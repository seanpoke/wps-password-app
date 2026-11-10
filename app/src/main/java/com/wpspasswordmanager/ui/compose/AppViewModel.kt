package com.wpspasswordmanager.ui.compose

import android.app.Application
import android.content.Intent
import android.os.Build
import android.os.Environment
import android.provider.Settings
import android.view.accessibility.AccessibilityManager
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.google.gson.Gson
import com.wpspasswordmanager.DirectoryMigrationManager
import com.wpspasswordmanager.business.DocumentFactory
import com.wpspasswordmanager.business.WpsAppInfo
import com.wpspasswordmanager.business.WpsManager
import com.wpspasswordmanager.network.LoginResponse
import com.wpspasswordmanager.network.NetworkCallback
import com.wpspasswordmanager.network.NetworkManager
import com.wpspasswordmanager.storage.ConfigStorage
import com.wpspasswordmanager.storage.ServerConfig
import com.wpspasswordmanager.storage.UserInfo
import com.wpspasswordmanager.network.HeartbeatService
import com.wpspasswordmanager.utils.LogManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import java.io.File
import java.io.IOException
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale
import java.util.Date
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

/* ---------------- 路由与状态模型 ---------------- */

sealed interface Route {
    data object Boot : Route
    data class Wizard(val step: Int) : Route
    data object Main : Route
    data object Expired : Route
    data object Logs : Route
    data object Password : Route
    data object About : Route
    data object ServerEdit : Route
    data object ForcePassword : Route
    data object Advanced : Route
    data object Assistant : Route
}

enum class TokenState { Checking, Ok, None, Expired }

sealed interface TestState {
    data object Untested : TestState
    data object Testing : TestState
    data class Ok(val ms: Long) : TestState
    data class Fail(val message: String) : TestState
}

data class PermState(
    val accessibility: Boolean = false,
    val overlay: Boolean = false,
    val storage: Boolean = false,
    val wpsPkg: String? = null,
    val wpsLabel: String? = null
) {
    val ready: Boolean get() = accessibility && overlay && storage && !wpsPkg.isNullOrEmpty()
    val appPermsOk: Boolean get() = accessibility && overlay
}

data class DocItem(
    val file: File,
    val name: String,
    val fmt: String,
    val sizeText: String,
    val timeText: String,
    val isToday: Boolean
)

data class UpdateInfo(
    val latestVersion: String,
    val downloadUrl: String,
    val changelog: String,
    val force: Boolean
)

data class ForceChangeState(
    val account: String,
    val tempToken: String,
    val oldPassword: String,
    val newPassword: String = "",
    val confirmPassword: String = "",
    val error: String = "",
    val submitting: Boolean = false
)

/* ---------------- 第二期：文档链路状态 ---------------- */

data class CreateDocState(
    val name: String = "未命名文档",
    val fmt: String = "docx",
    val error: String = "",
    val creating: Boolean = false
)

data class FileExistsState(
    val fullName: String,
    val fmt: String,
    val mode: String = "overwrite",
    val rename: String = ""
)

data class OpenModeState(
    val doc: DocItem,
    val mode: String = "view"
)

data class AuthNode(
    val dn: String,
    val type: Int,
    val name: String,
    val account: String?,
    val hasAuth: Boolean,
    val children: List<AuthNode>,
    /** 服务端 id 化标识：type=0 -> sys_dept.id；type=1 -> sys_user.id。提交授权必须用它 */
    val id: Long = 0L
) {
    val isDept: Boolean get() = type == 0
}

/** 三期：自助改密页（整页，需填旧密码） */
data class PasswordFormState(
    val old: String = "",
    val neu: String = "",
    val confirm: String = "",
    val submitting: Boolean = false,
    val error: String = ""
) {
    val mismatch: Boolean get() = confirm.isNotEmpty() && confirm != neu
    val tooLong: Boolean get() = neu.length > 32
    val ok: Boolean
        get() = old.isNotEmpty() && neu.length in 8..32 &&
            neu.any { it.isUpperCase() } && neu.any { it.isLowerCase() } && neu.any { it.isDigit() } &&
            confirm == neu && neu != old
}

data class PermTreeState(
    val docName: String,
    val docId: String?,
    val loading: Boolean = true,
    val error: String = "",
    val roots: List<AuthNode> = emptyList(),
    val expanded: Set<String> = emptySet(),
    val selected: Set<String> = emptySet(),
    val saving: Boolean = false,
    val saveError: String = "",
    val savedAt: Long = 0L
)

data class UiState(
    // 路由
    val route: Route = Route.Boot,

    // 启动闸门
    val bootLocalOk: Boolean = false,
    val bootToken: TokenState = TokenState.Checking,
    val bootPerm: PermState = PermState(),
    val bootReady: Boolean = false,

    // 向导
    val host: String = "",
    val port: String = "8080",
    val http: Boolean = false,
    val test: TestState = TestState.Untested,
    val account: String = "",
    val password: String = "",
    val remember: Boolean = true,
    val loginError: String = "",
    val loggingIn: Boolean = false,
    val scanning: Boolean = false,
    val wpsApps: List<WpsAppInfo> = emptyList(),
    val selectedPkg: String? = null,

    // 主界面
    val tab: String = "overview",
    val name: String = "",
    val role: String? = null,
    val docs: List<DocItem> = emptyList(),
    val refreshing: Boolean = false,
    val perm: PermState = PermState(),
    val tokenOk: Boolean = true,

    // 文档长按菜单 / 删除确认 / 高级设置
    val docMenu: DocItem? = null,
    val deleteTarget: DocItem? = null,
    val cacheSize: String = "",
    val migrating: Boolean = false,

    // 令牌失效页
    val expiredForm: Boolean = false,
    val expiredError: String = "",
    val expiredBusy: Boolean = false,

    // 强制改密
    val forceChange: ForceChangeState? = null,

    // 弹层
    val toast: String? = null,
    val logoutConfirm: Boolean = false,
    val update: UpdateInfo? = null,
    val downloading: Int? = null,
    val downloadError: String? = null,

    // 三期：自助改密
    val passwordForm: PasswordFormState? = null,

    // 第二期：文档链路
    val createDoc: CreateDocState? = null,
    val fileExists: FileExistsState? = null,
    val openMode: OpenModeState? = null,
    val permTree: PermTreeState? = null
)

class AppViewModel(app: Application) : AndroidViewModel(app) {

    private val TAG = "AppViewModel"
    private val storage = ConfigStorage.getInstance(app)
    private val network = NetworkManager.getInstance(app)
    private val gson = Gson()

    private var _s = androidx.compose.runtime.mutableStateOf(UiState())
    val s: UiState get() = _s.value

    private fun set(block: (UiState) -> UiState) {
        _s.value = block(_s.value)
    }

    /* ---------------- 表单文本本地持有：只在提交点写回 UiState ---------------- */
    /**
     * 第二批性能改造：Compose 输入框把文本放在本地 remember 里，逐字输入不再产生
     * UiState.copy → 整棵界面重组；只在提交 / 跳转 / 测试连接时把最终文本同步进来，
     * 因此下面所有包装方法都先 set 一次再复用原有业务方法，业务读取路径完全不变。
     */

    /** 首次编辑时调用一次即可：清掉上一次的错误提示，不参与逐字输入 */
    fun markLoginEdited() {
        set {
            if (it.loginError.isEmpty() && it.expiredError.isEmpty()) it
            else it.copy(loginError = "", expiredError = "")
        }
    }

    /** 服务器地址一旦被改动，旧的「连接正常」结果必须作废（对齐原 setHost/setPort 语义） */
    fun markServerEdited() {
        set { if (it.test is TestState.Untested) it else it.copy(test = TestState.Untested) }
    }

    fun markCreateNameEdited() {
        set { st ->
            if (st.createDoc?.error.isNullOrEmpty()) st
            else st.copy(createDoc = st.createDoc?.copy(error = ""))
        }
    }

    fun markPasswordFormEdited() {
        set { st ->
            if (st.passwordForm?.error.isNullOrEmpty()) st
            else st.copy(passwordForm = st.passwordForm?.copy(error = ""))
        }
    }

    fun markForceChangeEdited() {
        set { st ->
            if (st.forceChange?.error.isNullOrEmpty()) st
            else st.copy(forceChange = st.forceChange?.copy(error = ""))
        }
    }

    fun submitLogin(account: String, password: String) {
        set { it.copy(account = account.trimStart(), password = password, loginError = "", expiredError = "") }
        login()
    }

    fun submitReLogin(account: String, password: String) {
        set { it.copy(account = account.trimStart(), password = password, expiredError = "") }
        reLogin()
    }

    fun submitServerTest(host: String, port: String) {
        set { it.copy(host = sanitizeHost(host), port = port.filter { c -> c.isDigit() }.take(5)) }
        testConnection()
    }

    fun submitWizardServerStep(host: String, port: String) {
        set { it.copy(host = sanitizeHost(host), port = port.filter { c -> c.isDigit() }.take(5)) }
        wizardNext()
    }

    fun submitServerEdit(host: String, port: String) {
        set { it.copy(host = sanitizeHost(host), port = port.filter { c -> c.isDigit() }.take(5)) }
        saveServerEdit()
    }

    fun submitCreate(name: String) {
        setCreateName(name)
        submitCreateDoc()
    }

    fun submitExistsRename(rename: String) {
        setExistsRename(rename)
        confirmExists()
    }

    fun submitPasswordForm(old: String, neu: String, confirm: String) {
        set { st -> st.copy(passwordForm = st.passwordForm?.copy(old = old, neu = neu, confirm = confirm, error = "")) }
        submitPassword()
    }

    fun submitForceChangeForm(newPassword: String, confirmPassword: String) {
        set { st -> st.copy(forceChange = st.forceChange?.copy(newPassword = newPassword, confirmPassword = confirmPassword, error = "")) }
        submitForceChange()
    }

    /** 返回键逻辑下沉：BackHandler 只需订阅一个布尔，不再订阅整个 UiState */
    fun backEnabled(): Boolean {
        val st = _s.value
        val wizardStep = (st.route as? Route.Wizard)?.step ?: 1
        val dialogOpen = st.logoutConfirm || st.update != null || st.downloading != null ||
            st.downloadError != null || st.fileExists != null || st.openMode != null || st.deleteTarget != null
        val sheetOpen = st.createDoc != null || st.permTree != null || st.docMenu != null
        return dialogOpen || sheetOpen || wizardStep > 1 ||
            st.route is Route.Logs || st.route is Route.Password || st.route is Route.About ||
            st.route is Route.ServerEdit || st.route is Route.ForcePassword || st.route is Route.Advanced ||
            st.route is Route.Assistant || (st.route is Route.Expired && st.expiredForm)
    }

    fun handleBack() {
        val st = _s.value
        val wizardStep = (st.route as? Route.Wizard)?.step ?: 1
        when {
            st.deleteTarget != null -> cancelDeleteDoc()
            st.docMenu != null -> closeDocMenu()
            st.logoutConfirm -> cancelLogout()
            st.downloading != null || st.downloadError != null -> cancelDownload()
            st.update != null -> dismissUpdate()
            st.fileExists != null -> dismissExists()
            st.openMode != null -> cancelOpenMode()
            st.createDoc != null -> closeCreateDoc()
            st.permTree != null -> closePermTree()
            wizardStep > 1 -> wizardBack()
            st.route is Route.Logs -> closeLogs()
            st.route is Route.Password -> closePasswordPage()
            st.route is Route.About -> closeAbout()
            st.route is Route.ServerEdit -> closeServerEdit()
            st.route is Route.ForcePassword -> abortForcePassword()
            st.route is Route.Advanced -> closeAdvanced()
            st.route is Route.Assistant -> closeAssistant()
            else -> cancelExpiredForm()
        }
    }

    private var toastJob: kotlinx.coroutines.Job? = null
    private var downloadCall: okhttp3.Call? = null

    init {
        set { it.copy(perm = readPerms()) }
        startHeartbeat()
        loadConfigIntoForm()
        decideBootRoute()
    }

    /* ---------------- 系统状态 ---------------- */

    fun onResumeRefresh() {
        if (debugScene != null) return
        val perms = readPerms()
        set { it.copy(perm = perms) }
        if (s.route is Route.Main) reloadDocs()
    }

    private fun startHeartbeat() {
        try {
            getApplication<Application>().startService(Intent(getApplication(), HeartbeatService::class.java))
        } catch (e: Exception) {
            LogManager.log(TAG, "启动心跳失败: ${e.message}", "WARN")
        }
    }

    private fun readPerms(): PermState {
        val ctx = getApplication<Application>()
        val accessibility = try {
            val am = ctx.getSystemService(android.content.Context.ACCESSIBILITY_SERVICE) as AccessibilityManager
            am.getEnabledAccessibilityServiceList(android.accessibilityservice.AccessibilityServiceInfo.FEEDBACK_GENERIC)
                .any { it.id.contains("WpsAccessibilityService") }
        } catch (e: Exception) {
            false
        }
        val overlay = try {
            Settings.canDrawOverlays(ctx)
        } catch (e: Exception) {
            false
        }
        val storageOk = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            Environment.isExternalStorageManager()
        } else true
        val pkg = storage.getTargetWpsPackage()
        val label = pkg?.let {
            try {
                val ai = ctx.packageManager.getApplicationInfo(it, 0)
                ctx.packageManager.getApplicationLabel(ai).toString()
            } catch (e: Exception) {
                null
            }
        }
        return PermState(accessibility, overlay, storageOk, if (label == null) null else pkg, label)
    }

    /* ---------------- 启动闸门 ---------------- */

    private fun loadConfigIntoForm() {
        val cfg = storage.getServerConfig()
        val user = storage.getUserInfo()
        set {
            it.copy(
                host = cfg?.ipAddress ?: "",
                port = cfg?.port?.takeIf { p -> p.isNotEmpty() } ?: "8080",
                http = storage.getAllowHttp(),
                account = cfg?.username?.takeIf { a -> a.isNotEmpty() } ?: (user?.account ?: ""),
                remember = storage.getRememberPassword(),
                password = if (storage.getRememberPassword()) storage.getPassword() ?: "" else ""
            )
        }
    }

    private fun decideBootRoute() {
        val cfg = storage.getServerConfig()
        val user = storage.getUserInfo()
        set {
            it.copy(
                bootLocalOk = cfg != null,
                bootToken = if (user == null) TokenState.None else TokenState.Checking,
                bootPerm = readPerms()
            )
        }

        if (cfg == null) {
            viewModelScope.launch {
                delay(900)
                if (s.route !is Route.Boot) return@launch
                set { it.copy(route = Route.Wizard(1), bootReady = true) }
            }
            return
        }
        if (user == null) {
            viewModelScope.launch {
                delay(700)
                if (s.route !is Route.Boot) return@launch
                set { it.copy(route = Route.Wizard(2), bootReady = true) }
            }
            return
        }

        // 已登录：静默续期，失败进「会话失效」
        viewModelScope.launch {
            val ok = try {
                val resp = call { network.refreshToken(user.token, it) }
                org.json.JSONObject(resp).optInt("status", 0) == 200
            } catch (e: Exception) {
                false
            }
            if (s.route !is Route.Boot) return@launch
            set {
                it.copy(
                    bootToken = if (ok) TokenState.Ok else TokenState.Expired,
                    tokenOk = ok,
                    name = user.name,
                    account = user.account,
                    role = user.role,
                    route = if (ok) Route.Main else Route.Expired,
                    bootReady = true
                )
            }
            if (ok) {
                startHeartbeat()
                reloadDocs()
                checkVersion()
            } else {
                set { it.copy(expiredForm = false) }
            }
        }
    }

    /* ---------------- 向导：服务器 ---------------- */

    fun setHost(v: String) = set { it.copy(host = sanitizeHost(v), test = TestState.Untested) }

    /**
     * 允许用户直接粘贴「https://10.8.5.55:8443/」：协议头与路径在输入时已剥离，
     * 若地址里还带端口，则在「测试连接 / 登录」提交点把端口拆到端口框（避免逐字输入被误拆）。
     */
    fun normalizeServerAddress() {
        val m = Regex("^(.+?):(\\d{1,5})$").find(s.host.trim()) ?: return
        val p = m.groupValues[2].toIntOrNull() ?: return
        if (p in 1..65535) set { it.copy(host = m.groupValues[1], port = m.groupValues[2]) }
    }
    fun setPort(v: String) = set { it.copy(port = v.filter { c -> c.isDigit() }.take(5), test = TestState.Untested) }
    fun setHttp(v: Boolean) = set { it.copy(http = v, test = TestState.Untested) }
    fun setAccount(v: String) = set { it.copy(account = v.trimStart(), loginError = "", expiredError = "") }
    fun setPassword(v: String) = set { it.copy(password = v, loginError = "", expiredError = "") }
    fun setRemember(v: Boolean) = set { it.copy(remember = v) }

    fun portValid(): Boolean = s.port.toIntOrNull()?.let { it in 1..65535 } == true

    fun testConnection() {
        normalizeServerAddress()
        if (s.host.isBlank() || !portValid()) return
        set { it.copy(test = TestState.Testing) }
        val started = System.currentTimeMillis()
        // 先落盘，NetworkManager 依配置构建 URL
        storage.saveServerConfig(ServerConfig(s.host.trim(), s.port, s.account))
        storage.saveAllowHttp(s.http)
        network.onServerConfigChanged()
        viewModelScope.launch {
            val result = try {
                val resp = call { network.checkVersion(currentVersion(), it) }
                val obj = org.json.JSONObject(resp)
                if (obj.optInt("status", 0) == 200) TestState.Ok(System.currentTimeMillis() - started)
                else TestState.Fail(obj.optString("message", "服务端返回异常"))
            } catch (e: Exception) {
                TestState.Fail(connectErrorText(e))
            }
            set { it.copy(test = result) }
        }
    }

    /* ---------------- 向导：登录 ---------------- */

    fun login() {
        normalizeServerAddress()
        val acc = s.account.trim()
        val pwd = s.password
        if (acc.isEmpty() || pwd.isEmpty() || s.loggingIn) return
        set { it.copy(loggingIn = true, loginError = "") }

        storage.saveServerConfig(ServerConfig(s.host.trim(), s.port, acc))
        storage.saveAllowHttp(s.http)
        network.onServerConfigChanged()

        viewModelScope.launch {
            try {
                val resp = call { network.login(acc, pwd, it) }
                val parsed = gson.fromJson(resp, LoginResponse::class.java)
                if (parsed.status != 200 || parsed.data == null) {
                    set { it.copy(loggingIn = false, loginError = parsed.message.ifEmpty { "登录失败" }) }
                    return@launch
                }
                val data = parsed.data
                if (data.needChangePwd == true) {
                    set {
                        it.copy(
                            loggingIn = false,
                            route = Route.ForcePassword,
                            forceChange = ForceChangeState(data.account, data.token, pwd)
                        )
                    }
                    return@launch
                }
                persistLogin(data, pwd)
            } catch (e: Exception) {
                set { it.copy(loggingIn = false, loginError = friendlyError(e)) }
            }
        }
    }

    private fun persistLogin(data: UserInfo, pwd: String) {
        storage.saveUserInfo(data)
        if (s.remember) {
            storage.saveRememberPassword(true)
            storage.savePassword(pwd)
        } else {
            storage.saveRememberPassword(false)
            storage.clearPassword()
        }
        LogManager.log(TAG, "登录成功: ${data.account}", "DEBUG")
        startHeartbeat()
        val perms = readPerms()
        val needSetup = !perms.ready
        set {
            it.copy(
                loggingIn = false,
                loginError = "",
                name = data.name,
                role = data.role,
                tokenOk = true,
                perm = perms,
                password = if (s.remember) it.password else "",
                route = if (needSetup) Route.Wizard(3) else Route.Main,
                tab = "overview"
            )
        }
        reloadDocs()
        if (s.route is Route.Main) checkVersion()
    }

    /* ---------------- 向导：权限 / 受管应用 ---------------- */

    fun refreshPerms() = set { it.copy(perm = readPerms()) }

    fun scanWps() {
        set { it.copy(scanning = true) }
        viewModelScope.launch {
            val apps = withContext(Dispatchers.IO) { WpsManager.scanInstalledWpsApps(getApplication()) }
            set {
                it.copy(
                    scanning = false,
                    wpsApps = apps,
                    selectedPkg = it.selectedPkg ?: apps.firstOrNull()?.packageName
                )
            }
        }
    }

    fun selectWps(pkg: String) {
        set { it.copy(selectedPkg = pkg) }
    }

    fun finishWizard() {
        val pkg = s.selectedPkg
        if (!pkg.isNullOrEmpty()) storage.saveTargetWpsPackage(pkg)
        set { it.copy(perm = readPerms(), route = Route.Main, tab = "overview") }
        reloadDocs()
    }

    /* ---------------- 向导步骤 ---------------- */

    fun wizardNext() {
        when (s.route) {
            is Route.Wizard -> {
                val step = (s.route as Route.Wizard).step
                when (step) {
                    1 -> if (s.test is TestState.Ok) goWizard(2)
                    3 -> if (s.perm.ready) {
                        scanWps()
                        goWizard(4)
                    }
                }
            }
            else -> Unit
        }
    }

    fun wizardBack() {
        val r = s.route as? Route.Wizard ?: return
        if (r.step <= 1) return
        goWizard(r.step - 1)
    }

    fun skipPerm() {
        scanWps()
        goWizard(4)
    }

    private fun goWizard(step: Int) {
        set { it.copy(route = Route.Wizard(step), perm = readPerms()) }
    }

    /* ---------------- 主界面 ---------------- */

    private var lastDocsReloadAt = 0L

    fun setTab(tab: String) {
        set { it.copy(tab = tab) }
        if (tab != "docs" && tab != "overview") return
        // 切页本身只做状态切换；磁盘扫描节流，避免紧接着的 docs 更新再引发一次全树重组
        val now = android.os.SystemClock.elapsedRealtime()
        if (s.docs.isNotEmpty() && now - lastDocsReloadAt < 1000L) return
        lastDocsReloadAt = now
        reloadDocs()
    }

    /* ---------------- 三期：运行日志页 ---------------- */

    fun openLogs() = set { it.copy(route = Route.Logs) }

    fun closeLogs() = set { it.copy(route = Route.Main, tab = "settings") }

    /* ---------------- 三期：自助改密页 ---------------- */

    /** 服务端约定：role == "user" 为本地账号，其余（admin 等）为 LDAP 域账号 */
    fun isLocalAccount(): Boolean = s.role?.equals("user", ignoreCase = true) == true

    fun openPasswordPage() {
        if (!isLocalAccount()) {
            showToast("域账号请到域账号服务网站修改密码")
            return
        }
        set { it.copy(route = Route.Password, passwordForm = PasswordFormState()) }
    }

    fun closePasswordPage() = set {
        it.copy(route = Route.Main, tab = "settings", passwordForm = null)
    }

    /* ---------------- 三期：关于与更新 ---------------- */

    fun openAbout() = set { it.copy(route = Route.About) }

    fun closeAbout() = set { it.copy(route = Route.Main, tab = "settings") }

    fun showLicenses() = showToast("第三方组件：AndroidX、Kotlin Coroutines、OkHttp、Gson")

    /* ---------------- 文档：长按菜单 / 删除 ---------------- */

    fun openDocMenu(doc: DocItem) = set { it.copy(docMenu = doc) }

    fun closeDocMenu() = set { it.copy(docMenu = null) }

    fun askDeleteDoc(doc: DocItem) = set { it.copy(docMenu = null, deleteTarget = doc) }

    fun cancelDeleteDoc() = set { it.copy(deleteTarget = null) }

    fun confirmDeleteDoc() {
        val doc = s.deleteTarget ?: return
        set { it.copy(deleteTarget = null) }
        viewModelScope.launch {
            val ok = withContext(Dispatchers.IO) { runCatching { doc.file.delete() }.getOrDefault(false) }
            if (ok) {
                LogManager.log(TAG, "删除文档: ${doc.file.absolutePath}", "INFO")
                showToast("已删除「${doc.name}」")
            } else {
                LogManager.log(TAG, "删除文档失败: ${doc.file.absolutePath}", "ERROR")
                showToast("删除失败，请检查存储权限")
            }
            reloadDocs()
        }
    }

    /* ---------------- 设置：受管应用 / 高级设置 ---------------- */

    fun saveWpsSelection() {
        val pkg = s.selectedPkg
        if (pkg.isNullOrEmpty()) return
        storage.saveTargetWpsPackage(pkg)
        set { it.copy(perm = readPerms()) }
        showToast("受管应用已更新")
    }

    fun openAdvanced() {
        set { it.copy(route = Route.Advanced) }
        loadCacheSize()
    }

    fun closeAdvanced() = set { it.copy(route = Route.Main, tab = "settings") }

    fun closeAssistant() = set { it.copy(route = Route.Main, tab = "docs") }

    /** 调试 / 走查：直接拉起悬浮气泡（真实入口由无障碍服务在 WPS 前台时触发） */
    fun debugShowBubble() {
        val ctx = getApplication<Application>()
        val intent = Intent(ctx, com.wpspasswordmanager.ui.FloatingButtonService::class.java)
            .putExtra("show_bubble", true)
        runCatching {
            androidx.core.content.ContextCompat.startForegroundService(ctx, intent)
        }.onFailure { showToast("启动悬浮服务失败：${it.message}") }
    }

    fun loadCacheSize() {
        viewModelScope.launch {
            val text = withContext(Dispatchers.IO) { formatSize(dirSize(cacheDirs())) }
            set { it.copy(cacheSize = text) }
        }
    }

    fun clearCache() {
        viewModelScope.launch {
            val freed = withContext(Dispatchers.IO) {
                val total = dirSize(cacheDirs())
                cacheDirs().forEach { dir -> runCatching { dir.listFiles()?.forEach { it.deleteRecursively() } } }
                total
            }
            loadCacheSize()
            showToast(if (freed > 0) "已清理 ${formatSize(freed)} 缓存" else "缓存已经是空的")
            LogManager.log(TAG, "清理缓存: ${formatSize(freed)}", "INFO")
        }
    }

    fun runMigration() {
        if (s.migrating) return
        set { it.copy(migrating = true) }
        val ctx = getApplication<Application>()
        DirectoryMigrationManager(ctx).executeMigration(object : DirectoryMigrationManager.Callback {
            override fun onSuccess() {
                set { it.copy(migrating = false) }
                showToast("目录迁移完成")
                reloadDocs()
            }

            override fun onError(errorCode: Int, message: String) {
                set { it.copy(migrating = false) }
                showToast("目录迁移失败：$message")
                LogManager.log(TAG, "目录迁移失败($errorCode): $message", "ERROR")
            }
        })
    }

    private fun cacheDirs(): List<File> {
        val ctx = getApplication<Application>()
        return listOfNotNull(ctx.cacheDir, ctx.externalCacheDir)
    }

    private fun dirSize(dirs: List<File>): Long = dirs.sumOf { root ->
        runCatching {
            root.walkTopDown().filter { it.isFile }.sumOf { it.length() }
        }.getOrDefault(0L)
    }

    private fun formatSize(bytes: Long): String = when {
        bytes >= 1024 * 1024 -> String.format(Locale.getDefault(), "%.1f MB", bytes / 1024.0 / 1024.0)
        bytes >= 1024 -> String.format(Locale.getDefault(), "%.0f KB", bytes / 1024.0)
        else -> "$bytes B"
    }

    /* ---------------- 服务器设置（独立页，复用向导 Step1 表单） ---------------- */

    fun openServerEdit() = set { it.copy(route = Route.ServerEdit, test = TestState.Untested) }

    fun closeServerEdit() = set { it.copy(route = Route.Main, tab = "settings") }

    fun saveServerEdit() {
        normalizeServerAddress()
        val st = s
        if (st.host.isBlank() || !portValid()) {
            showToast("请填写有效的服务器地址与端口")
            return
        }
        storage.saveServerConfig(ServerConfig(st.host.trim(), st.port, st.account))
        storage.saveAllowHttp(st.http)
        network.onServerConfigChanged()

        val user = storage.getUserInfo()
        if (user == null) {
            set { it.copy(route = Route.Main, tab = "settings") }
            showToast("服务器设置已保存")
            return
        }
        // 地址变了，当前 token 未必被新服务端认可：立即校验一次会话
        viewModelScope.launch {
            val ok = try {
                val resp = call { network.refreshToken(user.token, it) }
                org.json.JSONObject(resp).optInt("status", 0) == 200
            } catch (e: Exception) {
                false
            }
            if (ok) {
                set { it.copy(route = Route.Main, tab = "settings", tokenOk = true) }
                showToast("服务器设置已保存")
                reloadDocs()
            } else {
                set {
                    it.copy(
                        route = Route.Expired,
                        tab = "settings",
                        tokenOk = false,
                        bootToken = TokenState.Expired,
                        expiredForm = false,
                        expiredError = ""
                    )
                }
            }
        }
    }

    fun pwdOld(v: String) = set { st -> st.copy(passwordForm = st.passwordForm?.copy(old = v, error = "")) }
    fun pwdNew(v: String) = set { st -> st.copy(passwordForm = st.passwordForm?.copy(neu = v, error = "")) }
    fun pwdConfirm(v: String) = set { st -> st.copy(passwordForm = st.passwordForm?.copy(confirm = v, error = "")) }

    fun submitPassword() {
        val p = s.passwordForm ?: return
        if (p.submitting || !p.ok) return
        val user = storage.getUserInfo()
        val account = user?.account ?: s.account
        val token = user?.token
        set { it.copy(passwordForm = p.copy(submitting = true, error = "")) }
        viewModelScope.launch {
            try {
                val resp = call { network.changePassword(account, p.old, p.neu, token, it) }
                val obj = org.json.JSONObject(resp)
                if (obj.optInt("status", 0) == 200) {
                    if (storage.getRememberPassword()) storage.savePassword(p.neu)
                    set { it.copy(passwordForm = null, route = Route.Main, tab = "settings") }
                    showToast("新密码已保存，当前保持登录")
                } else {
                    set { it.copy(passwordForm = p.copy(submitting = false, error = obj.optString("message", "修改失败"))) }
                }
            } catch (e: Exception) {
                set { it.copy(passwordForm = p.copy(submitting = false, error = friendlyError(e))) }
            }
        }
    }

    fun refreshOverview() {
        set { it.copy(refreshing = true, perm = readPerms()) }
        viewModelScope.launch {
            delay(520)
            reloadDocs()
            set { it.copy(refreshing = false) }
            showToast("状态已刷新")
        }
    }

    fun reloadDocs() {
        viewModelScope.launch {
            val list = withContext(Dispatchers.IO) { scanDocs() }
            set { it.copy(docs = list) }
        }
    }

    private fun scanDocs(): List<DocItem> {
        val dir = File(
            Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOCUMENTS),
            "WpsManagement"
        )
        if (!dir.exists() || !dir.isDirectory) return emptyList()
        val files = dir.listFiles()?.filter {
            it.isFile && !it.name.startsWith(".") &&
                it.extension.lowercase() in setOf("docx", "doc", "xlsx", "xls", "pptx", "ppt", "pdf", "txt")
        } ?: return emptyList()
        return files.sortedByDescending { it.lastModified() }.take(50).map { f ->
            val today = isToday(f.lastModified())
            DocItem(
                file = f,
                name = f.name,
                fmt = f.extension.lowercase(),
                sizeText = humanSize(f.length()),
                timeText = timeText(f.lastModified(), today),
                isToday = today
            )
        }
    }

    private fun isToday(ts: Long): Boolean {
        val cal = Calendar.getInstance()
        val nowDay = cal.get(Calendar.DAY_OF_YEAR) to cal.get(Calendar.YEAR)
        cal.timeInMillis = ts
        return nowDay == (cal.get(Calendar.DAY_OF_YEAR) to cal.get(Calendar.YEAR))
    }

    private fun timeText(ts: Long, today: Boolean): String {
        val cal = Calendar.getInstance().apply { timeInMillis = ts }
        val hm = SimpleDateFormat("HH:mm", Locale.getDefault()).format(Date(ts))
        if (today) return "今天 $hm"
        val yesterday = Calendar.getInstance().apply { add(Calendar.DAY_OF_YEAR, -1) }
        if (cal.get(Calendar.YEAR) == yesterday.get(Calendar.YEAR) &&
            cal.get(Calendar.DAY_OF_YEAR) == yesterday.get(Calendar.DAY_OF_YEAR)
        ) return "昨天 $hm"
        return SimpleDateFormat("M 月 d 日", Locale.getDefault()).format(Date(ts))
    }

    private fun humanSize(bytes: Long): String = when {
        bytes >= 1024 * 1024 -> String.format(Locale.US, "%.1f MB", bytes / 1024f / 1024f)
        bytes >= 1024 -> "${bytes / 1024} KB"
        else -> "$bytes B"
    }

    /* ---------------- 新建文档 ---------------- */

    fun openCreateDoc() = set { it.copy(createDoc = CreateDocState()) }
    fun closeCreateDoc() = set { it.copy(createDoc = null) }
    fun setCreateName(v: String) = set { st -> st.copy(createDoc = st.createDoc?.copy(name = v, error = "")) }
    fun setCreateFmt(v: String) = set { st -> st.copy(createDoc = st.createDoc?.copy(fmt = v, error = "")) }
    fun pickExistsMode(mode: String) = set { st -> st.copy(fileExists = st.fileExists?.copy(mode = mode)) }
    fun setExistsRename(v: String) = set { st -> st.copy(fileExists = st.fileExists?.copy(rename = v)) }
    fun dismissExists() = set { it.copy(fileExists = null) }

    fun submitCreateDoc() {
        val d = s.createDoc ?: return
        if (d.creating) return
        val base = d.name.trim().removeSuffix(".${d.fmt}").trim()
        if (base.isEmpty()) {
            set { st -> st.copy(createDoc = d.copy(error = "请输入文档名称")) }
            return
        }
        if (base.any { it in "\\/:*?\"<>|" }) {
            set { st -> st.copy(createDoc = d.copy(error = "名称不能包含 \\ / : * ? \" < > |")) }
            return
        }
        val full = "$base.${d.fmt}"
        val target = File(DocumentFactory.docsDir(), full)
        if (target.exists() && target.length() > 0) {
            set { st -> st.copy(fileExists = FileExistsState(full, d.fmt, "overwrite", uniqueDocName(base, d.fmt))) }
            return
        }
        createDocNow(full, null)
    }

    private fun uniqueDocName(base: String, fmt: String): String {
        var i = 1
        while (true) {
            val candidate = "$base（$i）"
            if (!File(DocumentFactory.docsDir(), "$candidate.$fmt").exists()) return candidate
            i++
            if (i > 99) return "$base（${System.currentTimeMillis()}）"
        }
    }

    private fun createDocNow(fullName: String, overwriteName: String?) {
        val d = s.createDoc ?: return
        set { st -> st.copy(createDoc = d.copy(creating = true, error = ""), fileExists = null) }
        viewModelScope.launch {
            val ok = withContext(Dispatchers.IO) {
                try {
                    val dir = DocumentFactory.ensureDocsDir()
                    if (overwriteName != null) File(dir, overwriteName).delete()
                    DocumentFactory.createEmptyDocument(File(dir, fullName), fullName.substringAfterLast('.'))
                } catch (e: Exception) {
                    LogManager.log(TAG, "创建文档失败: ${e.message}", "ERROR")
                    false
                }
            }
            if (!ok) {
                set { st -> st.copy(createDoc = st.createDoc?.copy(creating = false, error = "创建失败，请检查文档操作权限")) }
                return@launch
            }
            set { it.copy(createDoc = null, fileExists = null) }
            reloadDocs()
            showToast("文档已创建")
            val created = File(DocumentFactory.docsDir(), fullName)
            openInWps(created)
        }
    }

    fun confirmExists() {
        val fe = s.fileExists ?: return
        if (fe.mode == "rename") {
            val nm = fe.rename.trim().removeSuffix(".${fe.fmt}").trim()
            if (nm.isEmpty()) {
                set { st -> st.copy(fileExists = fe.copy(rename = uniqueDocName(fe.fullName.substringBeforeLast('.'), fe.fmt))) }
                return
            }
            createDocNow("$nm.${fe.fmt}", null)
        } else {
            createDocNow(fe.fullName, fe.fullName)
        }
    }

    /* ---------------- 打开文档 ---------------- */

    fun openDocFlow(doc: DocItem) = set { it.copy(openMode = OpenModeState(doc)) }
    fun setOpenMode(mode: String) = set { st -> st.copy(openMode = st.openMode?.copy(mode = mode)) }
    fun cancelOpenMode() = set { it.copy(openMode = null) }

    fun confirmOpenMode() {
        val om = s.openMode ?: return
        set { it.copy(openMode = null) }
        viewModelScope.launch {
            val src = om.doc.file
            if (!src.exists()) {
                showToast("文件不存在或已被移动")
                reloadDocs()
                return@launch
            }
            val file = if (om.mode == "view") {
                withContext(Dispatchers.IO) { DocumentFactory.prepareViewCopy(getApplication(), src) }
            } else src
            if (file == null || !file.exists()) {
                showToast("准备查看副本失败")
                return@launch
            }
            openInWps(file)
        }
    }

    private fun openInWps(file: File) {
        val ctx = getApplication<Application>()
        try {
            // 方案A：恢复走 ProxyActivity 完整链路（token/权限校验 → 读文件尾部元数据 →
            // 初始化 FileMeta(uid/权限/密码) → 唤起 WPS），
            // 与历史版本一致；否则新建/编辑打开的文档永远没有 uid，
            // 保存后文件观察者也写不进尾部元数据，导致「文档权限/加密状态/自动填充」全部失效。
            com.wpspasswordmanager.monitor.WpsAccessibilityService.stableDocumentPath = file.absolutePath
            com.wpspasswordmanager.monitor.WpsAccessibilityService.currentDocumentPath = file.absolutePath
            com.wpspasswordmanager.ui.ProxyActivity.openFileWithWps(ctx, file)
            LogManager.log(TAG, "打开文档(经ProxyActivity): ${file.absolutePath}", "DEBUG")
        } catch (e: Exception) {
            LogManager.log(TAG, "打开文档失败: ${e.message}", "ERROR")
            showToast("无法启动 WPS 应用")
        }
    }

    /* ---------------- 文档权限树 ---------------- */

    fun openPermTree(doc: DocItem) {
        val ctx = getApplication<Application>()
        val uid = try {
            com.wpspasswordmanager.business.FileMetaManager.getInstance().getUidFromFile(ctx, doc.file.absolutePath)
        } catch (e: Exception) {
            null
        }
        set { it.copy(permTree = PermTreeState(docName = doc.name, docId = uid, loading = !uid.isNullOrEmpty())) }
        if (uid.isNullOrEmpty()) {
            set {
                it.copy(
                    permTree = it.permTree?.copy(
                        loading = false,
                        error = "该文档还没有权限标识，先用「编辑」打开一次，待密码生成后再来授权"
                    )
                )
            }
            return
        }
        loadPermTree(uid)
    }

    fun closePermTree() = set { it.copy(permTree = null) }

    fun retryPermTree() {
        val pt = s.permTree ?: return
        val docId = pt.docId
        if (docId.isNullOrEmpty()) {
            closePermTree()
            return
        }
        set { it.copy(permTree = pt.copy(loading = true, error = "", saveError = "")) }
        loadPermTree(docId)
    }

    private fun loadPermTree(docId: String) {
        viewModelScope.launch {
            try {
                val token = storage.getUserInfo()?.token
                val q = java.net.URLEncoder.encode(docId, "UTF-8")
                val resp = call { network.executeGetRequest("/doc/auth/tree?docId=$q", token, it) }
                val obj = org.json.JSONObject(resp)
                if (obj.optInt("status", 0) != 200) {
                    set { st -> st.copy(permTree = st.permTree?.copy(loading = false, error = obj.optString("message", "加载权限树失败"))) }
                    return@launch
                }
                val arr = obj.optJSONArray("data") ?: org.json.JSONArray()
                val roots = mutableListOf<AuthNode>()
                val selected = mutableSetOf<String>()
                val expanded = mutableSetOf<String>()
                for (i in 0 until arr.length()) {
                    roots += parseAuthNode(arr.getJSONObject(i), false, selected, expanded, 0)
                }
                set {
                    it.copy(
                        permTree = it.permTree?.copy(
                            loading = false,
                            error = "",
                            roots = roots,
                            selected = selected,
                            expanded = expanded,
                            saveError = ""
                        )
                    )
                }
            } catch (e: Exception) {
                set { st -> st.copy(permTree = st.permTree?.copy(loading = false, error = friendlyError(e))) }
            }
        }
    }

    fun togglePermNode(dn: String) {
        set { st ->
            val pt = st.permTree ?: return@set st
            val sel = pt.selected.toMutableSet()
            if (sel.contains(dn)) sel.remove(dn) else {
                sel.add(dn)
                // 选中部门时，子孙由部门代表，移除其显式勾选，避免重复授权
                findAuthNode(pt.roots, dn)?.let { node -> collectAuthDns(node).filter { it != dn }.forEach { sel.remove(it) } }
            }
            st.copy(permTree = pt.copy(selected = sel, saveError = ""))
        }
    }

    fun togglePermExpand(dn: String) = set { st ->
        val pt = st.permTree ?: return@set st
        val ex = pt.expanded.toMutableSet()
        if (!ex.remove(dn)) ex.add(dn)
        st.copy(permTree = pt.copy(expanded = ex))
    }

    /** 全部 dns（部门+用户） */
    fun allAuthDns(nodes: List<AuthNode>): List<String> = nodes.flatMap { listOf(it.dn) + allAuthDns(it.children) }

    fun permSelectAll() = set { st ->
        val pt = st.permTree ?: return@set st
        st.copy(permTree = pt.copy(selected = pt.roots.map { it.dn }.toSet(), saveError = ""))
    }

    fun permInvert() = set { st ->
        val pt = st.permTree ?: return@set st
        val roots = pt.roots.map { it.dn }.toSet()
        st.copy(permTree = pt.copy(selected = roots - pt.selected, saveError = ""))
    }

    fun permToggleExpandAll() = set { st ->
        val pt = st.permTree ?: return@set st
        val all = collectDeptDns(pt.roots).toSet()
        val expanded: Set<String> = if (pt.expanded.containsAll(all)) emptySet() else all
        st.copy(permTree = pt.copy(expanded = expanded))
    }

    fun savePermTree() {
        val pt = s.permTree ?: return
        if (pt.saving) return
        val docId = pt.docId ?: return
        set { st -> st.copy(permTree = pt.copy(saving = true, saveError = "")) }
        viewModelScope.launch {
            try {
                val token = storage.getUserInfo()?.token
                // 服务端已 id 化：只认 sys_dept.id / sys_user.id，传 DN 会被解析成空列表并清空原有授权
                val deptIds = mutableListOf<Long>()
                val userIds = mutableListOf<Long>()
                pt.selected.forEach { dn ->
                    val node = findAuthNode(pt.roots, dn) ?: return@forEach
                    if (node.id <= 0L) return@forEach
                    if (node.isDept) deptIds += node.id else userIds += node.id
                }
                val body = org.json.JSONObject().apply {
                    put("docId", docId)
                    put("deptIdList", org.json.JSONArray(deptIds))
                    put("userIdList", org.json.JSONArray(userIds))
                    put("isTemp", false)
                }.toString()
                val resp = call { network.executePostRequest("/doc/auth/update", body, token, it) }
                val obj = org.json.JSONObject(resp)
                if (obj.optInt("status", 0) == 200) {
                    set { st -> st.copy(permTree = st.permTree?.copy(saving = false, saveError = "", savedAt = System.currentTimeMillis())) }
                    showToast("权限已保存")
                    loadPermTree(docId)
                } else {
                    set {
                        st -> st.copy(permTree = st.permTree?.copy(
                            saving = false,
                            saveError = "上次失败：${obj.optString("message", "保存失败")}，选择已保留"
                        ))
                    }
                }
            } catch (e: Exception) {
                set {
                    st -> st.copy(permTree = st.permTree?.copy(
                        saving = false,
                        saveError = "上次失败：${friendlyError(e)}，选择已保留"
                    ))
                }
            }
        }
    }

    /* ---------------- 会话失效 ---------------- */

    fun onSessionExpired() {
        if (s.route is Route.Main || s.route is Route.Wizard) {
            set { it.copy(route = Route.Expired, expiredForm = false, tokenOk = false, bootToken = TokenState.Expired) }
        }
    }

    fun openExpired() {
        set { it.copy(route = Route.Expired, expiredForm = false, expiredError = "", tokenOk = false) }
    }

    fun goWizardStep(step: Int) {
        set { it.copy(route = Route.Wizard(step), expiredForm = false) }
        if (step == 4) scanWps()
    }

    fun openExpiredForm() {
        set {
            it.copy(
                expiredForm = true,
                expiredError = "",
                account = storage.getServerConfig()?.username?.takeIf { a -> a.isNotEmpty() }
                    ?: storage.getUserInfo()?.account ?: it.account,
                password = ""
            )
        }
    }

    fun cancelExpiredForm() = set { it.copy(expiredForm = false, expiredError = "", password = "") }

    fun reLogin() {
        val acc = s.account.trim()
        val pwd = s.password
        if (acc.isEmpty() || pwd.isEmpty() || s.expiredBusy) return
        set { it.copy(expiredBusy = true, expiredError = "") }
        viewModelScope.launch {
            try {
                val resp = call { network.login(acc, pwd, it) }
                val parsed = gson.fromJson(resp, LoginResponse::class.java)
                if (parsed.status == 200 && parsed.data != null) {
                    val data = parsed.data
                    if (data.needChangePwd == true) {
                        set {
                            it.copy(
                                expiredBusy = false,
                                route = Route.ForcePassword,
                                forceChange = ForceChangeState(data.account, data.token, pwd)
                            )
                        }
                    } else {
                        storage.saveUserInfo(data)
                        if (s.remember) {
                            storage.saveRememberPassword(true)
                            storage.savePassword(pwd)
                        }
                        startHeartbeat()
                        set {
                            it.copy(
                                expiredBusy = false,
                                expiredForm = false,
                                password = "",
                                name = data.name,
                                role = data.role,
                                tokenOk = true,
                                perm = readPerms(),
                                route = Route.Main,
                                tab = "overview"
                            )
                        }
                        reloadDocs()
                    }
                } else {
                    set { it.copy(expiredBusy = false, expiredError = parsed.message.ifEmpty { "登录失败" }) }
                }
            } catch (e: Exception) {
                set { it.copy(expiredBusy = false, expiredError = friendlyError(e)) }
            }
        }
    }

    fun gotoServerEdit() {
        set {
            it.copy(
                route = Route.Wizard(1),
                test = TestState.Untested,
                expiredForm = false,
                expiredError = ""
            )
        }
    }

    /* ---------------- 强制改密 ---------------- */

    fun forceChangeNew(v: String) = set { st -> st.copy(forceChange = st.forceChange?.copy(newPassword = v, error = "")) }
    fun forceChangeConfirm(v: String) = set { st -> st.copy(forceChange = st.forceChange?.copy(confirmPassword = v, error = "")) }
    /** 放弃改密：临时登录态作废，退回登录页（不允许停在半登录状态） */
    fun abortForcePassword() {
        set {
            it.copy(
                forceChange = null,
                password = "",
                loggingIn = false,
                expiredBusy = false,
                route = Route.Wizard(2)
            )
        }
        showToast("已取消改密，请用原密码重新登录")
    }

    fun dismissForceChange() = abortForcePassword()

    fun submitForceChange() {
        val fc = s.forceChange ?: return
        if (fc.submitting) return
        when {
            fc.newPassword.length < 8 -> set { it.copy(forceChange = fc.copy(error = "新密码至少 8 位")) }
            !(fc.newPassword.any { c -> c.isUpperCase() } &&
                fc.newPassword.any { c -> c.isLowerCase() } &&
                fc.newPassword.any { c -> c.isDigit() }) ->
                set { it.copy(forceChange = fc.copy(error = "需同时包含大写字母、小写字母和数字")) }
            fc.newPassword != fc.confirmPassword -> set { it.copy(forceChange = fc.copy(error = "两次输入不一致")) }
            else -> {
                set { it.copy(forceChange = fc.copy(submitting = true, error = "")) }
                viewModelScope.launch {
                    try {
                        val resp = call { network.changePassword(fc.account, fc.oldPassword, fc.newPassword, fc.tempToken, it) }
                        val obj = org.json.JSONObject(resp)
                        if (obj.optInt("status", 0) == 200) {
                            storage.clearUserInfo()
                            set {
                                it.copy(
                                    forceChange = null,
                                    password = "",
                                    name = "",
                                    role = null,
                                    tokenOk = false,
                                    docs = emptyList(),
                                    tab = "overview",
                                    route = Route.Wizard(2)
                                )
                            }
                            showToast("密码已更新，请用新密码登录")
                        } else {
                            set { it.copy(forceChange = fc.copy(submitting = false, error = obj.optString("message", "修改失败"))) }
                        }
                    } catch (e: Exception) {
                        set { it.copy(forceChange = fc.copy(submitting = false, error = friendlyError(e))) }
                    }
                }
            }
        }
    }

    /* ---------------- 退出登录 ---------------- */

    fun askLogout() = set { it.copy(logoutConfirm = true) }
    fun cancelLogout() = set { it.copy(logoutConfirm = false) }

    fun doLogout() {
        val user = storage.getUserInfo()
        set { it.copy(logoutConfirm = false) }
        val finish = {
            storage.clearUserInfo()
            set {
                it.copy(
                    route = Route.Wizard(2),
                    name = "",
                    role = null,
                    docs = emptyList(),
                    tab = "overview",
                    tokenOk = false,
                    password = if (storage.getRememberPassword()) it.password else ""
                )
            }
        }
        if (user == null) {
            finish()
            return
        }
        viewModelScope.launch {
            try {
                call { network.logout(user.token, it) }
            } catch (e: Exception) {
                LogManager.log(TAG, "登出请求失败: ${e.message}", "WARN")
            }
            finish()
        }
    }

    /* ---------------- 版本更新 ---------------- */

    fun checkVersion(manual: Boolean = false) {
        if (storage.getServerConfig() == null) {
            if (manual) showToast("请先配置服务器")
            return
        }
        viewModelScope.launch {
            try {
                val resp = call { network.checkVersion(currentVersion(), it) }
                val obj = org.json.JSONObject(resp)
                if (obj.optInt("status", 0) != 200) {
                    if (manual) showToast("版本检查失败")
                    return@launch
                }
                val data = obj.getJSONObject("data")
                val type = data.optString("updateType", "NONE")
                if (type == "NONE") {
                    if (manual) showToast("当前已是最新版本")
                    return@launch
                }
                fun String.norm() = if (this == "null") "" else this
                val info = UpdateInfo(
                    latestVersion = data.optString("latestVersion", "").norm(),
                    downloadUrl = data.optString("downloadUrl", "").norm(),
                    changelog = data.optString("changelog", "").norm(),
                    force = type == "FORCE"
                )
                set { it.copy(update = info) }
            } catch (e: Exception) {
                LogManager.log(TAG, "版本检查失败: ${e.message}", "DEBUG")
                if (manual) showToast("版本检查失败：${friendlyError(e)}")
            }
        }
    }

    fun dismissUpdate() = set { it.copy(update = null, downloadError = null) }

    fun startDownload() {
        val info = s.update ?: return
        if (s.downloading != null) return
        if (info.downloadUrl.isEmpty()) {
            showToast("未配置下载地址，请联系管理员")
            return
        }
        val full = if (info.downloadUrl.startsWith("http")) info.downloadUrl else {
            val base = network.getServerBaseUrl() ?: run {
                showToast("未配置服务器地址，无法下载")
                return
            }
            base.trimEnd('/') + "/" + info.downloadUrl.trimStart('/')
        }
        val dir = getApplication<Application>().getExternalFilesDir("update")
            ?: File(getApplication<Application>().filesDir, "update")
        val dest = File(dir, "update_${System.currentTimeMillis()}.apk")
        set { it.copy(downloading = 0, downloadError = null) }
        downloadCall = network.downloadApk(
            full,
            dest,
            onProgress = { p -> viewModelScope.launch { set { st -> st.copy(downloading = p) } } },
            onResult = { ok, msg ->
                viewModelScope.launch {
                    if (ok) {
                        set { st -> st.copy(downloading = 100) }
                        installApk(File(msg))
                        set { st -> st.copy(downloading = null, update = null) }
                    } else {
                        set { st -> st.copy(downloading = null, downloadError = msg) }
                    }
                }
            }
        )
    }

    fun cancelDownload() {
        downloadCall?.cancel()
        downloadCall = null
        set { it.copy(downloading = null, downloadError = null, update = null) }
    }

    private fun installApk(file: File) {
        val ctx = getApplication<Application>()
        try {
            val uri = androidx.core.content.FileProvider.getUriForFile(ctx, "${ctx.packageName}.fileprovider", file)
            val intent = Intent(Intent.ACTION_VIEW).apply {
                setDataAndType(uri, "application/vnd.android.package-archive")
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            ctx.startActivity(intent)
        } catch (e: Exception) {
            showToast("无法启动安装程序")
        }
    }

    /* ---------------- 调试场景（仅 debug 包；对应原型的 applyScene） ---------------- */

    private var debugScene: String? = null

    fun applyDebugScene(name: String) {
        if (!com.wpspasswordmanager.BuildConfig.DEBUG) return
        debugScene = name
        val demo = listOf(
            DocItem(File("/doc"), "2026 年 Q4 经营分析报告.docx", "docx", "2.4 MB", "今天 09:12", true),
            DocItem(File("/doc"), "研发中心预算表.xlsx", "xlsx", "812 KB", "昨天 17:40", false),
            DocItem(File("/doc"), "产品线汇报_终版.pptx", "pptx", "5.1 MB", "10 月 28 日", false)
        )
        when (name) {
            "overview" -> set {
                it.copy(
                    route = Route.Main,
                    tab = "overview",
                    name = "张鹏",
                    role = "admin",
                    account = "zhangpeng",
                    tokenOk = true,
                    perm = PermState(true, true, true, "cn.wps.moffice_pro", "WPS Office"),
                    docs = demo
                )
            }
            "blocked" -> set {
                it.copy(
                    route = Route.Main,
                    tab = "overview",
                    name = "张鹏",
                    role = "admin",
                    account = "zhangpeng",
                    tokenOk = true,
                    perm = PermState(false, false, false, null, null),
                    docs = demo
                )
            }
            "docs" -> set {
                it.copy(
                    route = Route.Main,
                    tab = "docs",
                    name = "张鹏",
                    role = "admin",
                    account = "zhangpeng",
                    tokenOk = true,
                    perm = PermState(true, true, true, "cn.wps.moffice_pro", "WPS Office"),
                    docs = demo
                )
            }
            "settings" -> set {
                it.copy(
                    route = Route.Main,
                    tab = "settings",
                    name = "张鹏",
                    role = "admin",
                    account = "zhangpeng",
                    host = it.host.ifEmpty { "10.20.1.8" },
                    port = it.port.ifEmpty { "8080" },
                    tokenOk = true,
                    perm = PermState(true, true, true, "cn.wps.moffice_pro", "WPS Office"),
                    docs = demo
                )
            }
            "logs" -> set {
                it.copy(
                    route = Route.Logs,
                    tab = "settings",
                    name = "张鹏",
                    role = "admin",
                    account = "zhangpeng",
                    tokenOk = true,
                    perm = PermState(true, true, true, "cn.wps.moffice_pro", "WPS Office")
                )
            }
            "password" -> set {
                it.copy(
                    route = Route.Password,
                    tab = "settings",
                    name = "张鹏",
                    role = "admin",
                    account = "zhangpeng",
                    tokenOk = true,
                    perm = PermState(true, true, true, "cn.wps.moffice_pro", "WPS Office"),
                    passwordForm = PasswordFormState()
                )
            }
            "about" -> set {
                it.copy(
                    route = Route.About,
                    tab = "settings",
                    name = "张鹏",
                    role = "admin",
                    account = "zhangpeng",
                    tokenOk = true,
                    perm = PermState(true, true, true, "cn.wps.moffice_pro", "WPS Office")
                )
            }
            "expired" -> set { it.copy(route = Route.Expired, expiredForm = false, tokenOk = false) }
            "wizard1" -> set {
                it.copy(
                    route = Route.Wizard(1),
                    host = "10.8.5.55",
                    port = "8443",
                    test = TestState.Ok(66)
                )
            }
            "wizard3" -> set { it.copy(route = Route.Wizard(3), perm = PermState(false, true, true, null, null)) }
            "forceChange" -> set {
                it.copy(
                    route = Route.ForcePassword,
                    account = "test1",
                    forceChange = ForceChangeState("test1", "temp-token", "Init@2026")
                )
            }
            "settingsLocal" -> set {
                it.copy(
                    route = Route.Main,
                    tab = "settings",
                    name = "测试用户1",
                    role = "user",
                    account = "test1",
                    host = "10.8.5.55",
                    port = "8443",
                    tokenOk = true,
                    perm = PermState(true, true, true, "cn.wps.moffice_pro", "WPS Office"),
                    docs = demo
                )
            }
            "update" -> set {
                it.copy(
                    route = Route.Main,
                    tab = "settings",
                    name = "张鹏",
                    role = "admin",
                    account = "zhangpeng",
                    tokenOk = true,
                    update = UpdateInfo(
                        "1.0.5", "", "· 无障碍服务被系统回收后可一键恢复\n· 权限面板支持按部门或姓名搜索\n· 优化大文件保存时的卡顿", false
                    )
                )
            }
            "updateForce" -> set {
                it.copy(
                    route = Route.Main,
                    tab = "settings",
                    name = "张鹏",
                    role = "admin",
                    account = "zhangpeng",
                    tokenOk = true,
                    update = UpdateInfo("1.0.5", "", "", true)
                )
            }
            "download" -> set { it.copy(route = Route.Main, tab = "settings", downloading = 46) }
            "downloadErr" -> set {
                it.copy(route = Route.Main, tab = "settings", downloading = 46, downloadError = "连接超时，已中断在 5.2 MB")
            }
            "bubble" -> {
                set {
                    it.copy(
                        route = Route.Main,
                        tab = "docs",
                        name = "张鹏",
                        role = "admin",
                        account = "zhangpeng",
                        tokenOk = true
                    )
                }
                debugShowBubble()
            }
            "assistant" -> set {
                it.copy(
                    route = Route.Assistant,
                    name = "张鹏",
                    role = "admin",
                    account = "zhangpeng",
                    tokenOk = true
                )
            }
            "serverEdit" -> set {
                it.copy(
                    route = Route.ServerEdit,
                    tab = "settings",
                    name = "张鹏",
                    role = "user",
                    account = "zhangpeng",
                    host = "10.8.5.55",
                    port = "8443",
                    tokenOk = true,
                    perm = PermState(true, true, true, "cn.wps.moffice_pro", "WPS Office")
                )
            }
            "createSheet" -> set {
                it.copy(
                    route = Route.Main, tab = "docs", name = "张鹏", role = "admin",
                    tokenOk = true, perm = PermState(true, true, true, "cn.wps.moffice_pro", "WPS Office"),
                    docs = demo, createDoc = CreateDocState()
                )
            }
            "exists" -> set {
                it.copy(
                    route = Route.Main, tab = "docs", name = "张鹏", tokenOk = true,
                    perm = PermState(true, true, true, "cn.wps.moffice_pro", "WPS Office"),
                    docs = demo,
                    createDoc = CreateDocState(name = "2026 年 Q4 经营分析报告", fmt = "docx"),
                    fileExists = FileExistsState("2026 年 Q4 经营分析报告.docx", "docx", "rename", "2026 年 Q4 经营分析报告（1）")
                )
            }
            "openMode" -> set {
                it.copy(
                    route = Route.Main, tab = "docs", name = "张鹏", tokenOk = true,
                    perm = PermState(true, true, true, "cn.wps.moffice_pro", "WPS Office"),
                    docs = demo, openMode = OpenModeState(demo[0])
                )
            }
            "permTree" -> set {
                it.copy(
                    route = Route.Main, tab = "docs", name = "张鹏", tokenOk = true,
                    perm = PermState(true, true, true, "cn.wps.moffice_pro", "WPS Office"),
                    docs = demo,
                    permTree = demoPermTree("2026 年 Q4 经营分析报告.docx")
                )
            }
        }
    }

    private fun demoPermTree(docName: String): PermTreeState {
        fun user(dn: String, name: String, account: String?, auth: Boolean) =
            AuthNode(dn, 1, name, account, auth, emptyList())
        fun dept(dn: String, name: String, auth: Boolean, children: List<AuthNode>) =
            AuthNode(dn, 0, name, null, auth, children)

        val zhanggl = user("cn=zhanggl", "张贵丽", "zhanggl", true)
        val zhoudan = user("cn=zhoudan", "周丹", null, true)
        val zhangpeng = user("cn=zhangpeng", "张鹏（我）", "zhangpeng", true)
        val wangzj = user("cn=wangzj", "王志杰", "wangzj", true)

        val xingzheng = dept("ou=行政部", "行政部", false, emptyList())
        val zhengfu = dept("ou=政府事务与资质部", "政府事务与资质部", true, listOf(zhanggl, zhoudan))
        val bigdata = dept("ou=大数据技术组", "大数据技术组", true, listOf(zhangpeng, wangzj))
        val sysdev = dept("ou=系统软件研发部", "系统软件研发部", true, listOf(bigdata))
        val yunbroad = dept("ou=云宽带系统研发组", "云宽带系统研发组", false, emptyList())
        val rnd = dept("ou=研发中心", "研发中心", true, listOf(sysdev, yunbroad))
        val gm = dept("ou=总经理办公室", "总经理办公室", false, listOf(xingzheng, zhengfu))
        val root = dept("ou=武汉绿网", "武汉绿网", false, listOf(gm, rnd))

        return PermTreeState(
            docName = docName,
            docId = "demo-doc-id",
            loading = false,
            roots = listOf(root),
            expanded = setOf(root.dn, gm.dn, rnd.dn),
            selected = setOf(zhengfu.dn, rnd.dn)
        )
    }

    /* ---------------- 杂项 ---------------- */

    fun showToast(text: String) {
        toastJob?.cancel()
        set { it.copy(toast = text) }
        toastJob = viewModelScope.launch {
            delay(2000)
            set { it.copy(toast = null) }
        }
    }

    fun clearToast() = set { it.copy(toast = null) }

    fun currentVersion(): String = try {
        getApplication<Application>().packageManager
            .getPackageInfo(getApplication<Application>().packageName, 0).versionName ?: "0.0.0"
    } catch (e: Exception) {
        "0.0.0"
    }

    fun currentBuild(): Int = try {
        val info = getApplication<Application>().packageManager
            .getPackageInfo(getApplication<Application>().packageName, 0)
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.P) {
            info.longVersionCode.toInt()
        } else {
            @Suppress("DEPRECATION")
            info.versionCode
        }
    } catch (e: Exception) {
        0
    }

    private fun connectErrorText(e: Exception): String {
        val m = e.message ?: ""
        return when {
            m.contains("timeout", true) -> "连接超时（30 秒），请确认网络与端口"
            m.contains("failed to connect", true) -> "无法建立连接，请确认 IP、端口与防火墙"
            m.contains("Unable to resolve host", true) -> "域名解析失败，请检查地址"
            m.contains("SSL", true) || m.contains("certificate", true) -> "证书校验失败；内网可打开下方 HTTP 调试"
            else -> "连接失败，请检查地址"
        }
    }

    private fun friendlyError(e: Exception): String {
        val msg = e.message ?: "网络异常"
        return when {
            msg.contains("401") -> "账号或密码不正确"
            msg.contains("timeout", true) -> "连接超时，请检查网络"
            msg.contains("Unable to resolve host", true) -> "无法解析服务器地址"
            msg.contains("failed to connect", true) -> "无法连接服务器"
            else -> msg
        }
    }

    private fun sanitizeHost(raw: String): String {
        var v = raw.trimStart()
        v = v.replace(Regex("^[a-zA-Z][a-zA-Z0-9+.-]*://"), "")
        return v.replace(Regex("/.*$"), "").trim()
    }

    private suspend fun call(block: (NetworkCallback) -> Unit): String =
        suspendCancellableCoroutine { cont ->
            var done = false
            block(object : NetworkCallback {
                override fun onSuccess(response: String) {
                    if (!done) {
                        done = true
                        if (cont.isActive) cont.resume(response)
                    }
                }

                override fun onError(error: String) {
                    if (!done) {
                        done = true
                        if (cont.isActive) cont.resumeWithException(IOException(error))
                    }
                }
            })
        }
}
