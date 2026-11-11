package com.wpspasswordmanager.ui

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Bundle
import android.view.accessibility.AccessibilityManager

import com.wpspasswordmanager.utils.FileNameResolver
import com.wpspasswordmanager.utils.LogManager
import androidx.activity.compose.setContent
import androidx.appcompat.app.AppCompatActivity
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.wpspasswordmanager.ui.compose.AppButton
import com.wpspasswordmanager.ui.compose.AppField
import com.wpspasswordmanager.ui.compose.AppTheme
import com.wpspasswordmanager.ui.compose.BtnStyle
import com.wpspasswordmanager.ui.compose.C
import com.wpspasswordmanager.ui.compose.T
import com.wpspasswordmanager.ui.compose.screens.OpenModeDialog
import com.wpspasswordmanager.R
import com.wpspasswordmanager.WpsPasswordManagerApplication
import com.wpspasswordmanager.business.FileMetaFactory
import com.wpspasswordmanager.business.FileMetaManager
import com.wpspasswordmanager.monitor.WpsAccessibilityService
import com.wpspasswordmanager.network.NetworkCallback
import com.wpspasswordmanager.network.NetworkManager
import com.wpspasswordmanager.storage.ConfigStorage
import com.wpspasswordmanager.ui.compose.AppActivity
import org.json.JSONObject
import java.io.File
import java.io.FileOutputStream

class ProxyActivity : AppCompatActivity() {

    private val mainHandler = android.os.Handler(android.os.Looper.getMainLooper())

    companion object {
        private const val TAG = "ProxyActivity"

        /** App 内已经通过新版「打开方式」选过模式，不要再弹一次选择窗口 */
        private const val EXTRA_DIRECT_OPEN = "wps_direct_open"
        private const val EXTRA_DIRECT_PATH = "wps_direct_path"

        fun openFileWithWps(context: Context, file: File) {
            val intent = Intent(context, ProxyActivity::class.java)
            intent.action = Intent.ACTION_VIEW
            intent.data = androidx.core.content.FileProvider.getUriForFile(
                context,
                "com.wpspasswordmanager.fileprovider",
                file
            )
            intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            // 只读查看产生的 cacheView 副本不在 WpsManagement 目录，旧逻辑会误判成外部文件
            // 再弹一次「打开方式」。这里显式带上来源与真实路径，走直接打开链路。
            intent.putExtra(EXTRA_DIRECT_OPEN, true)
            intent.putExtra(EXTRA_DIRECT_PATH, file.absolutePath)
            context.startActivity(intent)
        }

        fun readPasswordFromFile(context: Context, filePath: String, uid: String, keyVersion: String? = null): String? {
            LogManager.log(TAG, "开始读取文档口令", "DEBUG")
            try {
                val localPassword = FileMetaManager.getInstance().getPasswordFromFile(context, filePath)
                if (localPassword != null) {
                    LogManager.log(TAG, "已读取本地加密口令", "DEBUG")
                    val token = getStaticTokenFromStorage(context)

                    // 如果未传入keyVersion，从文件读取或使用全局默认值
                    val finalKeyVersion = if (!keyVersion.isNullOrEmpty()) {
                        keyVersion
                    } else {
                        val fileKeyVersion = FileMetaManager.getInstance().getKeyVersionFromFile(context, filePath)
                        if (!fileKeyVersion.isNullOrEmpty()) {
                            fileKeyVersion
                        } else {
                            ConfigStorage.getInstance(context).getKeyVersion()
                        }
                    }
                    val latch = java.util.concurrent.CountDownLatch(1)
                    var resultPassword: String? = null

                    LogManager.log(TAG, "开始调用获取文档密码接口", "DEBUG")
                    NetworkManager.getInstance(context).getDocumentPassword(
                        docId = uid,
                        encryPassword = localPassword,
                        token = token,
                        keyVersion = finalKeyVersion,
                        callback = object : NetworkCallback {
                            override fun onSuccess(response: String) {
                                LogManager.log(TAG, "获取文档密码接口已响应", "DEBUG")
                                try {
                                    val json = org.json.JSONObject(response)
                                    if (json.getInt("status") == 200) {
                                        val data = json.getJSONObject("data")
                                        val documentPassword = data.optString("password")
                                        LogManager.log(TAG, "已获取文档口令（长度 ${documentPassword.length}）", "DEBUG")
                                        resultPassword = documentPassword
                                    } else {
                                        LogManager.log(TAG, "获取文档密码失败，响应状态码不是200", "ERROR")
                                        resultPassword = null
                                    }
                                } catch (e: Exception) {
                                    LogManager.log(TAG, "解析获取文档密码响应失败: ${e.message}", "ERROR")
                                    resultPassword = null
                                } finally {
                                    latch.countDown()
                                }
                            }

                            override fun onError(error: String) {
                                LogManager.log(TAG, "获取文档密码失败: $error", "ERROR")
                                resultPassword = null
                                latch.countDown()
                            }

                            override fun onComplete() {}
                        }
                    )

                    latch.await(10, java.util.concurrent.TimeUnit.SECONDS)
                    return resultPassword
                } else {
                    LogManager.log(TAG, "本地文件中未找到密码", "DEBUG")
                    return null
                }
            } catch (e: Exception) {
                LogManager.log(TAG, "读取本地文件密码失败: ${e.message}", "ERROR")
                return null
            }
        }

        private fun getStaticTokenFromStorage(context: Context): String? {
            try {
                val userInfo = ConfigStorage.getInstance(context).getUserInfo()
                return userInfo?.token
            } catch (e: Exception) {
                LogManager.log(TAG, "获取token失败: ${e.message}", "ERROR")
                return null
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // 检查token存在性与有效性
        if (!checkTokenValidity()) {
            // token不存在或已失效，跳转到登录页面（新版界面会自行进入登录向导/失效页）
            val loginIntent = Intent(this, AppActivity::class.java)
            loginIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK)
            startActivity(loginIntent)
            finish()
            return
        }

        // 检查应用权限状态
        if (!checkAppPermissions()) {
            // 权限不足，跳转到主页面
            val mainIntent = Intent(this, AppActivity::class.java)
            mainIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK)
            startActivity(mainIntent)
            finish()
            return
        }

        // 检查所有文件管理权限
        if (!checkManageStoragePermission()) {
            // 权限不足，跳转到主页面
            val mainIntent = Intent(this, AppActivity::class.java)
            mainIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK)
            startActivity(mainIntent)
            finish()
            return
        }

        // 检查是否选择了 WPS 应用
        if (!checkWpsAppSelected()) {
            // 未选择 WPS 应用，跳转到主页面
            val mainIntent = Intent(this, AppActivity::class.java)
            mainIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK)
            startActivity(mainIntent)
            finish()
            return
        }

        // 处理传入的 Intent
        handleIntent(intent)
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        // openFileWithWps 带 FLAG_ACTIVITY_CLEAR_TOP；复用实例时 extras 必须重新解析
        setIntent(intent)
        handleIntent(intent)
    }

    /**
     * 检查是否选择了 WPS 应用
     * @return true if WPS app is selected, false otherwise
     */
    private fun checkWpsAppSelected(): Boolean {
        val configStorage = ConfigStorage.getInstance(this)
        val selectedPackage = configStorage.getTargetWpsPackage()
        
        if (selectedPackage.isNullOrEmpty()) {
            LogManager.log(TAG, "未选择 WPS 应用", "DEBUG")
            return false
        }
        
        try {
            packageManager.getPackageInfo(selectedPackage, 0)
            LogManager.log(TAG, "已选择 WPS 应用: $selectedPackage", "DEBUG")
            return true
        } catch (e: PackageManager.NameNotFoundException) {
            LogManager.log(TAG, "选择的 WPS 应用 $selectedPackage 已卸载", "WARN")
            configStorage.clearTargetWpsPackage()
            return false
        }
    }

    /**
     * 检查应用权限状态
     * @return true if all required permissions are granted, false otherwise
     */
    private fun checkAppPermissions(): Boolean {
        // 检查无障碍服务权限
        val isAccessibilityServiceEnabled = isAccessibilityServiceEnabled()
        LogManager.log(TAG, "无障碍服务状态: $isAccessibilityServiceEnabled", "DEBUG")

        // 检查悬浮窗权限
        val isOverlayPermissionGranted = isOverlayPermissionGranted()
        LogManager.log(TAG, "悬浮窗权限状态: $isOverlayPermissionGranted", "DEBUG")

        // 若任意一项权限未启用，则返回false
        return isAccessibilityServiceEnabled && isOverlayPermissionGranted
    }

    /**
     * 检查所有文件管理权限
     * @return true if MANAGE_EXTERNAL_STORAGE permission is granted, false otherwise
     */
    private fun checkManageStoragePermission(): Boolean {
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.R) {
            val hasPermission = android.os.Environment.isExternalStorageManager()
            LogManager.log(TAG, "MANAGE_EXTERNAL_STORAGE 权限状态: $hasPermission", "DEBUG")
            return hasPermission
        }
        return true
    }

    /**
     * 检查无障碍服务是否启用
     * @return true if accessibility service is enabled, false otherwise
     */
    private fun isAccessibilityServiceEnabled(): Boolean {
        try {
            val accessibilityManager = getSystemService(Context.ACCESSIBILITY_SERVICE) as AccessibilityManager
            val enabledServices = accessibilityManager.getEnabledAccessibilityServiceList(android.accessibilityservice.AccessibilityServiceInfo.FEEDBACK_GENERIC)
            
            for (service in enabledServices) {
                if (service.id.contains("WpsAccessibilityService")) {
                    return true
                }
            }
            return false
        } catch (e: Exception) {
            LogManager.log(TAG, "检查无障碍服务状态失败: ${e.message}", "ERROR")
            return false
        }
    }

    /**
     * 检查悬浮窗权限是否授予
     * @return true if overlay permission is granted, false otherwise
     */
    private fun isOverlayPermissionGranted(): Boolean {
        try {
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.M) {
                // Android 6.0及以上需要动态申请权限
                return android.provider.Settings.canDrawOverlays(this)
            } else {
                // Android 6.0以下默认授予
                return true
            }
        } catch (e: Exception) {
            LogManager.log(TAG, "检查悬浮窗权限失败: ${e.message}", "ERROR")
            return false
        }
    }

    /**
     * 检查token存在性与有效性
     * @return true if token is valid, false otherwise
     */
    private fun checkTokenValidity(): Boolean {
        try {
            // 检查本地存储中是否存在token
            val token = getTokenFromStorage()
            if (token.isNullOrEmpty()) {
                LogManager.log(TAG, "Token不存在", "DEBUG")
                return false
            }

            // 尝试刷新token以验证其有效性
            val latch = java.util.concurrent.CountDownLatch(1)
            var isTokenValid = false

            NetworkManager.getInstance(this).refreshToken(token, object : NetworkCallback {
                override fun onSuccess(response: String) {
                    try {
                        val json = JSONObject(response)
                        if (json.getInt("status") == 200) {
                            // token刷新成功，更新本地存储中的token
                            val data = json.getJSONObject("data")
                            val newToken = data.optString("token")
                            if (!newToken.isNullOrEmpty()) {
                                val userInfo = ConfigStorage.getInstance(this@ProxyActivity).getUserInfo()
                                if (userInfo != null) {
                                    LogManager.log(TAG, "Token刷新成功", "DEBUG")
                                    isTokenValid = true
                                } else {
                                    LogManager.log(TAG, "用户信息不存在", "ERROR")
                                    isTokenValid = false
                                }
                            } else {
                                LogManager.log(TAG, "Token刷新成功但返回的token为空", "ERROR")
                                isTokenValid = false
                            }
                        } else {
                            LogManager.log(TAG, "Token刷新失败，响应状态码不是200", "ERROR")
                            isTokenValid = false
                        }
                    } catch (e: Exception) {
                        LogManager.log(TAG, "解析token刷新响应失败: ${e.message}", "ERROR")
                        isTokenValid = false
                    } finally {
                        latch.countDown()
                    }
                }

                override fun onError(error: String) {
                    LogManager.log(TAG, "Token刷新失败: $error", "ERROR")
                    isTokenValid = false
                    latch.countDown()
                }

                override fun onComplete() {}
            })

            // 等待网络请求完成，最多等待5秒
            latch.await(5, java.util.concurrent.TimeUnit.SECONDS)
            return isTokenValid
        } catch (e: Exception) {
            LogManager.log(TAG, "检查token有效性失败: ${e.message}", "ERROR")
            return false
        }
    }

    override fun onDestroy() {
        super.onDestroy()
    }

    private fun handleIntent(intent: Intent?) {
        if (intent == null || intent.action != Intent.ACTION_VIEW) {
            LogManager.log(TAG, "无效的 Intent", "ERROR")
            finish()
            return
        }

        val uri = intent.data
        if (uri == null) {
            LogManager.log(TAG, "Intent 中没有 URI", "ERROR")
            finish()
            return
        }

        try {
            val (fileName, isInWpsManagement) = getFileName(uri)
            LogManager.log(TAG, "文件名: $fileName", "DEBUG")
            LogManager.log(TAG, "文件 URI: $uri", "DEBUG")
            LogManager.log(TAG, "文件是否在WpsManagement目录中: $isInWpsManagement", "DEBUG")
            handleFileUri(
                uri,
                fileName,
                isInWpsManagement,
                intent.getBooleanExtra(EXTRA_DIRECT_OPEN, false),
                intent.getStringExtra(EXTRA_DIRECT_PATH)
            )
        } catch (e: Exception) {
            LogManager.log(TAG, "处理 Intent 失败: ${e.message}", "ERROR")
            forwardToWps(null, uri)
        }
    }

    private fun getFileName(uri: Uri): Pair<String, Boolean> {
        var fileName: String
        var isInWpsManagement = false
        
        try {
            val documentsDir = android.os.Environment.getExternalStoragePublicDirectory(android.os.Environment.DIRECTORY_DOCUMENTS)
            val wpsManagementDir = File(documentsDir, "WpsManagement")
            val wpsManagementPath = wpsManagementDir.absolutePath

            val resolvedResult = FileNameResolver.resolveFileName(this, uri)
            
            if (resolvedResult.success) {
                fileName = resolvedResult.fileName
                LogManager.log(TAG, "文件名解析成功，来源: ${resolvedResult.source}", "DEBUG")
                
                val isHashName = FileNameResolver.isHashFileName(fileName)
                if (isHashName) {
                    LogManager.log(TAG, "检测到Hash文件名: $fileName，可能需要进一步处理", "WARN")
                }
            } else {
                fileName = getFileNameFromUri(uri)
                LogManager.log(TAG, "使用URI路径作为后备文件名: $fileName", "DEBUG")
            }

            isInWpsManagement = isFileFromWpsManagement(uri, wpsManagementPath)
            LogManager.log(TAG, "文件是否来自WpsManagement目录: $isInWpsManagement", "DEBUG")
        } catch (e: Exception) {
            LogManager.log(TAG, "获取文件名失败: ${e.message}", "ERROR")
            fileName = getFileNameFromUri(uri)
        }

        return Pair(fileName, isInWpsManagement)
    }

    private fun isFileFromWpsManagement(uri: Uri, wpsManagementPath: String): Boolean {
        var isInWpsManagement = false
        
        val cursor = contentResolver.query(uri, null, null, null, null)
        cursor?.use {
            if (it.moveToFirst()) {
                val pathIndex = it.getColumnIndex("_data")
                if (pathIndex != -1) {
                    val filePath = it.getString(pathIndex)
                    if (!filePath.isNullOrEmpty()) {
                        isInWpsManagement = filePath.contains(wpsManagementPath, ignoreCase = true)
                        LogManager.log(TAG, "通过_data列检测: 文件路径=$filePath, 是否在WpsManagement=$isInWpsManagement", "DEBUG")
                    }
                }
            }
        }

        if (!isInWpsManagement) {
            val uriPath = uri.path
            if (!uriPath.isNullOrEmpty()) {
                isInWpsManagement = uriPath.contains("WpsManagement", ignoreCase = true)
                LogManager.log(TAG, "通过URI路径检测: URI路径=$uriPath, 是否在WpsManagement=$isInWpsManagement", "DEBUG")
            }
        }

        return isInWpsManagement
    }

    private fun checkFileInWpsManagement(fileName: String, wpsManagementDir: File): Boolean {
        if (fileName.isEmpty()) {
            return false
        }
        
        if (!wpsManagementDir.exists()) return false
        
        val targetFile = File(wpsManagementDir, fileName)
        return targetFile.exists() && targetFile.length() > 0
    }

    /**
     * 从URI路径中解析文件名
     */
    private fun getFileNameFromUri(uri: Uri): String {
        val path = uri.path
        if (!path.isNullOrEmpty()) {
            val lastSlash = path.lastIndexOf('/')
            if (lastSlash != -1 && lastSlash < path.length - 1) {
                return path.substring(lastSlash + 1)
            }
        }
        return ""
    }

    private fun handleFileUri(
        uri: Uri,
        fileName: String,
        isInWpsManagement: Boolean,
        directOpen: Boolean = false,
        directPath: String? = null
    ) {
        // App 内发起（已选过只读/可编辑，或新建文档后直接打开）：直接用真实文件走完整链路
        if (directOpen && !directPath.isNullOrEmpty() && File(directPath).exists()) {
            LogManager.log(TAG, "App 内已选打开方式，直接处理: $directPath", "DEBUG")
            continueWithFile(File(directPath), uri)
            return
        }

        if (isInWpsManagement) {
            LogManager.log(TAG, "文件是副本文件，直接执行打开流程", "DEBUG")
            continueWithFile(File(wpsDir(), fileName), uri)
        } else {
            LogManager.log(TAG, "文件不在当前插件目录，显示【打开方式】弹窗", "DEBUG")
            showOpenModeDialog(uri, fileName)
        }
    }

    private fun showOpenModeDialog(uri: Uri, fileName: String) {
        // 透明 Activity 里直接用 Compose 渲染遮罩 + 新版卡片，与 App 内「打开方式」完全一致
        setContent {
            var visible by remember { mutableStateOf(true) }
            var busy by remember { mutableStateOf(false) }
            var mode by remember { mutableStateOf("view") }
            if (visible) {
                AppTheme {
                    Box(
                        Modifier
                            .fillMaxSize()
                            .background(Color(0x73110C1E)),
                        contentAlignment = Alignment.Center
                    ) {
                        if (busy) {
                            PreparingCard()
                        } else {
                            OpenModeDialog(
                                fileName = fileName,
                                mode = mode,
                                onPick = { mode = it },
                                onCancel = { finish() },
                                onConfirm = {
                                    if (mode == "view") {
                                        LogManager.log(TAG, "用户选择【只读查看】模式", "DEBUG")
                                        busy = true
                                        openFileInViewMode(uri, fileName)
                                    } else {
                                        LogManager.log(TAG, "用户选择【可编辑】模式", "DEBUG")
                                        // 收起遮罩，避免后续导入步骤的窗口叠在半透明背景上
                                        visible = false
                                        handleEditMode(uri, fileName)
                                    }
                                }
                            )
                        }
                    }
                }
            }
        }
    }

    /** 准备只读副本时的进度卡片，替代旧的 AlertDialog loading */
    @Composable
    private fun PreparingCard() {
        Column(
            Modifier
                .width(220.dp)
                .clip(RoundedCornerShape(16.dp))
                .background(Color.White)
                .padding(20.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            CircularProgressIndicator(Modifier.size(26.dp), strokeWidth = 2.dp, color = C.Primary)
            Spacer(Modifier.height(12.dp))
            Text("正在准备文件…", style = T.Small.copy(fontSize = 13.sp, color = C.Ink2))
        }
    }

    /* ---------------- 「可编辑」导入链路：全部改用 Compose 弹窗 ---------------- */

    @Composable
    private fun ProxyScrim(content: @Composable () -> Unit) {
        Box(
            Modifier
                .fillMaxSize()
                .background(Color(0x73110C1E)),
            contentAlignment = Alignment.Center
        ) { content() }
    }

    private fun showBusyCard() {
        // 延后一帧替换内容，避免在当前 Compose 手势回调里直接重建 Composition
        mainHandler.post {
            if (!isFinishing && !isDestroyed) {
                setContent { AppTheme { ProxyScrim { PreparingCard() } } }
            }
        }
    }

    private fun wpsDir(): File = File(
        android.os.Environment.getExternalStoragePublicDirectory(android.os.Environment.DIRECTORY_DOCUMENTS),
        "WpsManagement"
    )

    private fun toast(text: String) {
        android.widget.Toast.makeText(this, text, android.widget.Toast.LENGTH_SHORT).show()
    }

    /** 复制 / 覆盖完成后的统一收尾：初始化元数据并唤起 WPS */
    private fun continueWithFile(targetFile: File, uri: Uri) {
        // processFile 内部会同步等待网络回调（最长 10s），放到后台避免卡住进度卡片 / ANR
        Thread {
            processFile(targetFile) { localFile ->
                runOnUiThread {
                    if (localFile != null) {
                        saveFileUriToPreferences(localFile.absolutePath)
                    } else {
                        saveFileUriToPreferences(uri.toString())
                    }
                    forwardToWps(localFile, uri)
                }
            }
        }.start()
    }

    private fun openExistingCopy(uri: Uri, fileName: String) {
        showBusyCard()
        continueWithFile(File(wpsDir(), fileName), uri)
    }

    private fun overwriteAndOpen(uri: Uri, fileName: String) {
        showBusyCard()
        val targetFile = File(wpsDir(), fileName)
        Thread {
            try {
                if (targetFile.exists()) targetFile.delete()
                val ok = copyFileFromContentUri(uri, targetFile)
                runOnUiThread {
                    if (ok) {
                        toast("文件覆盖成功")
                        continueWithFile(targetFile, uri)
                    } else {
                        toast("文件覆盖失败")
                        forwardToWps(null, uri)
                    }
                }
            } catch (e: Exception) {
                LogManager.log(TAG, "文件覆盖时发生错误: ${e.message}", "ERROR")
                runOnUiThread { toast("文件操作失败"); forwardToWps(null, uri) }
            }
        }.start()
    }

    private fun saveAsAndOpen(uri: Uri, newFileName: String) {
        val targetFile = File(wpsDir(), newFileName)
        if (targetFile.exists() && targetFile.length() > 0) {
            showFileExistsDialog(uri, newFileName)
            return
        }
        showBusyCard()
        Thread {
            try {
                val ok = copyFileFromContentUri(uri, targetFile)
                runOnUiThread {
                    if (ok) {
                        toast("文件保存成功")
                        continueWithFile(targetFile, uri)
                    } else {
                        toast("文件保存失败")
                        forwardToWps(null, uri)
                    }
                }
            } catch (e: Exception) {
                LogManager.log(TAG, "文件保存时发生错误: ${e.message}", "ERROR")
                runOnUiThread { toast("文件操作失败"); forwardToWps(null, uri) }
            }
        }.start()
    }

    /** 拆成「名称 + 扩展名」：扩展名固定，不参与编辑 */
    private fun splitFileName(fileName: String): Pair<String, String> {
        val ext = FileNameResolver.getFileExtension(fileName)
        val base = if (ext.isEmpty()) fileName else fileName.substring(0, fileName.length - ext.length - 1)
        return base to ext
    }

    /** 用户可能连扩展名一起粘进来，剥掉固定后缀，避免出现 a.docx.docx */
    private fun stripFixedExtension(input: String, ext: String): String {
        val text = input.trim()
        return if (ext.isNotEmpty() && text.endsWith(".$ext", ignoreCase = true)) {
            text.dropLast(ext.length + 1).trim()
        } else {
            text
        }
    }

    private fun joinFileName(base: String, ext: String): String =
        if (ext.isEmpty()) base else "$base.$ext"

    /** 「文档已存在」：Compose 版，替代旧 AlertDialog + dialog_file_exists.xml */
    private fun showFileExistsDialog(uri: Uri, fileName: String) {
        val (baseName, extension) = splitFileName(fileName)
        mainHandler.post {
            if (!isFinishing && !isDestroyed) {
                setContent {
                    AppTheme {
                        ProxyScrim {
                            ProxyFileExistsCard(
                                baseName = baseName,
                                extension = extension,
                                onOpenCopy = { openExistingCopy(uri, fileName) },
                                onOverwrite = { overwriteAndOpen(uri, fileName) },
                                onRename = { name -> saveAsAndOpen(uri, joinFileName(name, extension)) },
                                onCancel = { finish() }
                            )
                        }
                    }
                }
            }
        }
    }

    /** 「文件保存」：Compose 版，替代旧 AlertDialog */
    private fun showSaveFileDialog(uri: Uri, currentFileName: String, isHashName: Boolean) {
        val (baseName, extension) = splitFileName(currentFileName)
        LogManager.log(TAG, "文件保存弹窗: $currentFileName, hashName=$isHashName", "DEBUG")
        mainHandler.post {
            if (!isFinishing && !isDestroyed) {
                setContent {
                    AppTheme {
                        ProxyScrim {
                            ProxySaveCard(
                                baseName = baseName,
                                extension = extension,
                                onCancel = { finish() },
                                onSave = { name -> saveAsAndOpen(uri, joinFileName(name, extension)) }
                            )
                        }
                    }
                }
            }
        }
    }

    @Composable
    private fun ProxyFileExistsCard(
        baseName: String,
        extension: String,
        onOpenCopy: () -> Unit,
        onOverwrite: () -> Unit,
        onRename: (String) -> Unit,
        onCancel: () -> Unit
    ) {
        var name by remember(baseName, extension) { mutableStateOf(baseName) }
        var error by remember(baseName, extension) { mutableStateOf("") }
        Column(
            Modifier
                .width(308.dp)
                .clip(RoundedCornerShape(18.dp))
                .background(Color.White)
                .padding(18.dp)
        ) {
            Text("文档已存在", style = T.Body.copy(fontSize = 16.sp, fontWeight = FontWeight.Bold))
            Spacer(Modifier.height(6.dp))
            Text(
                "/Documents/WpsManagement 中已有同名文件，请选择处理方式",
                style = T.Micro.copy(fontSize = 11.5.sp, lineHeight = 17.sp)
            )
            Spacer(Modifier.height(16.dp))
            AppButton(
                "打开已有副本",
                onClick = onOpenCopy,
                modifier = Modifier.fillMaxWidth(),
                height = 42.dp,
                fontSize = 14.sp
            )
            Spacer(Modifier.height(8.dp))
            AppButton(
                "覆盖并重新导入",
                onClick = onOverwrite,
                modifier = Modifier.fillMaxWidth(),
                style = BtnStyle.Ghost,
                height = 42.dp,
                fontSize = 14.sp
            )
            Spacer(Modifier.height(16.dp))
            Text("或重命名后保存", style = T.Small.copy(fontSize = 12.5.sp, fontWeight = FontWeight.SemiBold))
            Spacer(Modifier.height(6.dp))
            NameWithFixedExtensionField(
                name = name,
                extension = extension,
                onNameChange = { name = it; error = "" },
                placeholder = "新文件名",
                error = error.isNotEmpty()
            )
            if (error.isNotEmpty()) {
                Spacer(Modifier.height(4.dp))
                Text(error, style = T.Micro.copy(fontSize = 11.sp, color = C.Err))
            }
            Spacer(Modifier.height(12.dp))
            Row {
                AppButton(
                    "取消",
                    onClick = onCancel,
                    modifier = Modifier.weight(1f),
                    style = BtnStyle.Ghost,
                    height = 42.dp,
                    fontSize = 13.5.sp
                )
                Spacer(Modifier.width(8.dp))
                AppButton(
                    "重命名保存",
                    onClick = {
                        val n = stripFixedExtension(name, extension)
                        when {
                            n.isEmpty() -> error = "请输入文件名"
                            File(wpsDir(), joinFileName(n, extension)).exists() -> error = "文件已存在，请输入其他名称"
                            else -> onRename(n)
                        }
                    },
                    modifier = Modifier.weight(1f),
                    height = 42.dp,
                    fontSize = 13.5.sp
                )
            }
        }
    }

    @Composable
    private fun ProxySaveCard(
        baseName: String,
        extension: String,
        onSave: (String) -> Unit,
        onCancel: () -> Unit
    ) {
        var name by remember(baseName, extension) { mutableStateOf(baseName) }
        var error by remember(baseName, extension) { mutableStateOf("") }
        Column(
            Modifier
                .width(308.dp)
                .clip(RoundedCornerShape(18.dp))
                .background(Color.White)
                .padding(18.dp)
        ) {
            Text("文件保存", style = T.Body.copy(fontSize = 16.sp, fontWeight = FontWeight.Bold))
            Spacer(Modifier.height(6.dp))
            Text(
                "当前文件将保存到 /Documents/WpsManagement 目录",
                style = T.Micro.copy(fontSize = 11.5.sp, lineHeight = 17.sp)
            )
            Spacer(Modifier.height(14.dp))
            NameWithFixedExtensionField(
                name = name,
                extension = extension,
                onNameChange = { name = it; error = "" },
                placeholder = "请输入文件名称",
                error = error.isNotEmpty()
            )
            if (error.isNotEmpty()) {
                Spacer(Modifier.height(4.dp))
                Text(error, style = T.Micro.copy(fontSize = 11.sp, color = C.Err))
            }
            Spacer(Modifier.height(16.dp))
            Row {
                AppButton(
                    "取消",
                    onClick = onCancel,
                    modifier = Modifier.weight(1f),
                    style = BtnStyle.Ghost,
                    height = 42.dp,
                    fontSize = 13.5.sp
                )
                Spacer(Modifier.width(8.dp))
                AppButton(
                    "保存",
                    onClick = {
                        val n = stripFixedExtension(name, extension)
                        if (n.isEmpty()) error = "请输入文件名称" else onSave(n)
                    },
                    modifier = Modifier.weight(1f),
                    height = 42.dp,
                    fontSize = 13.5.sp
                )
            }
        }
    }

    /** 名称输入框 + 右侧固定扩展名：类型不可编辑 */
    @Composable
    private fun NameWithFixedExtensionField(
        name: String,
        extension: String,
        onNameChange: (String) -> Unit,
        placeholder: String,
        error: Boolean
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            AppField(
                value = name,
                onValueChange = onNameChange,
                placeholder = placeholder,
                modifier = Modifier.weight(1f),
                error = error
            )
            if (extension.isNotEmpty()) {
                Spacer(Modifier.width(8.dp))
                Text(
                    ".$extension",
                    style = T.Small.copy(
                        fontSize = 13.sp,
                        color = C.Ink3,
                        fontWeight = FontWeight.SemiBold
                    )
                )
            }
        }
    }

    private fun handleEditMode(uri: Uri, fileName: String) {
        val documentsDir = android.os.Environment.getExternalStoragePublicDirectory(android.os.Environment.DIRECTORY_DOCUMENTS)
        val wpsManagementDir = File(documentsDir, "WpsManagement")
        val targetFile = File(wpsManagementDir, fileName)

        if (targetFile.exists() && targetFile.length() > 0) {
            LogManager.log(TAG, "插件目录中存在同名文件，显示【文档已存在】弹窗", "DEBUG")
            showFileExistsDialog(uri, fileName)
        } else {
            LogManager.log(TAG, "插件目录中不存在同名文件，显示【文档保存】弹窗", "DEBUG")
            val isHashName = FileNameResolver.isHashFileName(fileName)
            showSaveFileDialog(uri, fileName, isHashName)
        }
    }

    private fun openFileInViewMode(uri: Uri, fileName: String) {
        val cacheDir = File(getExternalFilesDir(null), "cacheView")

        if (!cacheDir.exists()) {
            cacheDir.mkdirs()
            LogManager.log(TAG, "创建cacheView目录: ${cacheDir.absolutePath}", "DEBUG")
        }

        val targetFileName = generateViewModeFileName(cacheDir, fileName)
        val targetFile = File(cacheDir, targetFileName)

        // 进度由 showOpenModeDialog 的 Compose 卡片呈现，不再弹旧 AlertDialog
        Thread {
            try {
                val copySuccess = copyFileFromContentUri(uri, targetFile)
                runOnUiThread {
                    if (copySuccess) {
                        LogManager.log(TAG, "查看模式文件拷贝成功: ${targetFile.absolutePath}", "DEBUG")
                        continueWithFile(targetFile, uri)
                    } else {
                        android.widget.Toast.makeText(this, "文件准备失败", android.widget.Toast.LENGTH_SHORT).show()
                        forwardToWps(null, uri)
                    }
                }
            } catch (e: Exception) {
                runOnUiThread {
                    android.widget.Toast.makeText(this, "文件操作失败", android.widget.Toast.LENGTH_SHORT).show()
                    LogManager.log(TAG, "查看模式文件操作失败: ${e.message}", "ERROR")
                    forwardToWps(null, uri)
                }
            }
        }.start()
    }

    private fun generateViewModeFileName(cacheDir: File, originalFileName: String): String {
        val prefix = "\$n_"
        val baseFileName = prefix + originalFileName
        
        val existingFile = File(cacheDir, baseFileName)
        if (!existingFile.exists()) {
            LogManager.log(TAG, "cache目录中不存在同名文件，使用新文件名: $baseFileName", "DEBUG")
            return baseFileName
        }

        if (existingFile.delete()) {
            LogManager.log(TAG, "成功删除cache目录中的旧文件: ${existingFile.absolutePath}", "DEBUG")
            return baseFileName
        } else {
            val timestamp = System.currentTimeMillis()
            val extensionIndex = originalFileName.lastIndexOf('.')
            val nameWithoutExtension = if (extensionIndex > 0) {
                originalFileName.substring(0, extensionIndex)
            } else {
                originalFileName
            }
            val extension = if (extensionIndex > 0) {
                originalFileName.substring(extensionIndex)
            } else {
                ""
            }
            
            val dateFormat = java.text.SimpleDateFormat("HHmmssSSS", java.util.Locale.getDefault())
            val timeStr = dateFormat.format(java.util.Date(timestamp))
            val newFileName = "$prefix$nameWithoutExtension" + "_$timeStr$extension"
            LogManager.log(TAG, "删除旧文件失败，使用带时间戳的文件名: $newFileName", "DEBUG")
            return newFileName
        }
    }

    /** 已停用：旧 AlertDialog 实现，仅保留作回退参考，业务流程不再进入 */
    private fun processFile(file: File, callback: (File?) -> Unit) {
        try {
            val existingUid = readUidFromFile(file.absolutePath)
            val isNewUid = existingUid == null
            val uid = existingUid ?: FileMetaFactory.createUid()
            val keyVersion = readKeyVersionFromFile(file.absolutePath)
            val entryPassword = readPassword(file.absolutePath)
            
            LogManager.log(TAG, "文件处理元数据 - uid: $uid, isNewUid: $isNewUid, keyVersion: ${keyVersion ?: "null"}, entryPassword: ${if (entryPassword.isNullOrEmpty()) "null" else "已获取"}", "DEBUG")
            
            val latch = java.util.concurrent.CountDownLatch(1)
            
            if (!isNewUid) {
                processExistingUidFile(file.absolutePath, uid, keyVersion, entryPassword, latch)
            } else {
                processNewUidFile(file.absolutePath, uid, keyVersion, entryPassword, latch)
            }
            
            latch.await(10, java.util.concurrent.TimeUnit.SECONDS)
            callback(file)
        } catch (e: Exception) {
            LogManager.log(TAG, "处理文件失败: ${e.message}", "ERROR")
            callback(null)
        }
    }
    
    /**
     * 处理已有uid的文件（非新文件）
     * 调用/doc/owner接口获取DocInfo，进行双重条件判断后初始化FileMeta
     */
    private fun processExistingUidFile(
        filePath: String,
        uid: String,
        keyVersion: String?,
        entryPassword: String?,
        latch: java.util.concurrent.CountDownLatch
    ) {
        LogManager.log(TAG, "处理已有uid的文件，开始调用/doc/owner接口", "DEBUG")
        
        val token = getTokenFromStorage()
        val fileName = File(filePath).name
        
        NetworkManager.getInstance(this).getDocumentOwner(
            docId = uid,
            token = token,
            fileName = fileName,
            callback = object : NetworkCallback {
                override fun onSuccess(response: String) {
                    LogManager.log(TAG, "获取文档权限接口已响应", "DEBUG")
                    try {
                        val json = JSONObject(response)
                        if (json.getInt("status") == 200) {
                            val data = json.getJSONObject("data")
                            val ownerAccount = data.optString("ownerAccount")
                            val ownerName = data.optString("ownerName")
                            val readAuth = data.optBoolean("readAuth", false)
                            val writeAuth = data.optBoolean("writeAuth", false)
                            
                            // 双重条件判断
                            // a) 当前用户是否拥有权限（读权限或写权限任意一个为true）
                            // b) entryPassword和keyVersion是否均不为空
                            val hasPermission = readAuth || writeAuth
                            val hasValidCredentials = !entryPassword.isNullOrEmpty() && !keyVersion.isNullOrEmpty()
                            
                            LogManager.log(TAG, "权限判断结果 - hasPermission: $hasPermission, hasValidCredentials: $hasValidCredentials", "DEBUG")
                            
                            if (hasPermission && hasValidCredentials) {
                                // 条件均满足：调用/doc/password接口获取真实密码
                                fetchRealPasswordAndInitFileMeta(
                                    filePath = filePath,
                                    uid = uid,
                                    keyVersion = keyVersion!!,
                                    entryPassword = entryPassword!!,
                                    isTemp = false,
                                    ownerAccount = ownerAccount,
                                    ownerName = ownerName,
                                    readAuth = readAuth,
                                    writeAuth = writeAuth,
                                    latch = latch
                                )
                            } else {
                                // 条件不满足：仅利用DocInfo初始化FileMeta，密码字段为null
                                LogManager.log(TAG, "条件不满足，使用DocInfo初始化FileMeta（密码为null）", "DEBUG")
                                FileMetaFactory.initFileMetaWithPermissions(
                                    filePath = filePath,
                                    oldPass = null,
                                    uid = uid,
                                    ownerAccount = ownerAccount,
                                    ownerName = ownerName,
                                    readAuth = readAuth,
                                    writeAuth = writeAuth,
                                    keyVersion = keyVersion
                                )
                                latch.countDown()
                            }
                        } else {
                            LogManager.log(TAG, "获取文档权限失败，响应状态码不是200", "ERROR")
                            initFileMetaWithDefaultPermissions(filePath, null, uid, keyVersion)
                            latch.countDown()
                        }
                    } catch (e: Exception) {
                        LogManager.log(TAG, "解析权限响应失败: ${e.message}", "ERROR")
                        initFileMetaWithDefaultPermissions(filePath, null, uid, keyVersion)
                        latch.countDown()
                    }
                }
                
                override fun onError(error: String) {
                    LogManager.log(TAG, "获取文档权限失败: $error", "ERROR")
                    initFileMetaWithDefaultPermissions(filePath, null, uid, keyVersion)
                    latch.countDown()
                }
                
                override fun onComplete() {}
            }
        )
    }
    
    /**
     * 处理新uid的文件（新文件）
     * 根据entryPassword和keyVersion是否有效决定是否调用/doc/password接口
     */
    private fun processNewUidFile(
        filePath: String,
        uid: String,
        keyVersion: String?,
        entryPassword: String?,
        latch: java.util.concurrent.CountDownLatch
    ) {
        LogManager.log(TAG, "处理新uid的文件", "DEBUG")
        
        // 验证entryPassword和keyVersion是否均不为空
        val hasValidCredentials = !entryPassword.isNullOrEmpty() && !keyVersion.isNullOrEmpty()
        
        if (hasValidCredentials) {
            // 条件满足：调用/doc/password接口，isTemp=true
            LogManager.log(TAG, "entryPassword和keyVersion均有效，调用/doc/password接口获取真实密码", "DEBUG")
            fetchRealPasswordAndInitFileMeta(
                filePath = filePath,
                uid = uid,
                keyVersion = keyVersion!!,
                entryPassword = entryPassword!!,
                isTemp = true,
                ownerAccount = null,
                ownerName = null,
                readAuth = true,
                writeAuth = true,
                latch = latch
            )
        } else {
            LogManager.log(TAG, "entryPassword或keyVersion为空，使用临时uid初始化FileMeta", "DEBUG")
            FileMetaFactory.initFileMetaWithTempUid(
                filePath = filePath,
                oldPass = null,
                uid = uid,
                keyVersion = keyVersion
            )
            latch.countDown()
        }
    }
    
    /**
     * 调用/doc/password接口获取真实密码，并使用完整数据初始化FileMeta对象
     */
    private fun fetchRealPasswordAndInitFileMeta(
        filePath: String,
        uid: String,
        keyVersion: String,
        entryPassword: String,
        isTemp: Boolean,
        ownerAccount: String?,
        ownerName: String?,
        readAuth: Boolean,
        writeAuth: Boolean,
        latch: java.util.concurrent.CountDownLatch
    ) {
        LogManager.log(TAG, "调用/doc/password接口，isTemp: $isTemp", "DEBUG")
        
        val token = getTokenFromStorage()
        
        NetworkManager.getInstance(this).getDocumentPassword(
            docId = uid,
            encryPassword = entryPassword,
            token = token,
            keyVersion = keyVersion,
            isTemp = isTemp,
            callback = object : NetworkCallback {
                override fun onSuccess(response: String) {
                    LogManager.log(TAG, "获取文档密码接口已响应", "DEBUG")
                    try {
                        val json = org.json.JSONObject(response)
                        if (json.getInt("status") == 200) {
                            val data = json.getJSONObject("data")
                            val realPassword = data.optString("password")
                            LogManager.log(TAG, "从接口获取到真实密码", "DEBUG")
                            
                            FileMetaFactory.initFileMetaWithPermissions(
                                filePath = filePath,
                                oldPass = realPassword,
                                uid = uid,
                                ownerAccount = ownerAccount,
                                ownerName = ownerName,
                                readAuth = readAuth,
                                writeAuth = writeAuth,
                                keyVersion = keyVersion
                            )
                        } else {
                            LogManager.log(TAG, "获取文档密码失败，响应状态码不是200", "ERROR")
                            FileMetaFactory.initFileMetaWithPermissions(
                                filePath = filePath,
                                oldPass = null,
                                uid = uid,
                                ownerAccount = ownerAccount,
                                ownerName = ownerName,
                                readAuth = readAuth,
                                writeAuth = writeAuth,
                                keyVersion = keyVersion
                            )
                        }
                    } catch (e: Exception) {
                        LogManager.log(TAG, "解析获取文档密码响应失败: ${e.message}", "ERROR")
                        FileMetaFactory.initFileMetaWithPermissions(
                            filePath = filePath,
                            oldPass = null,
                            uid = uid,
                            ownerAccount = ownerAccount,
                            ownerName = ownerName,
                            readAuth = readAuth,
                            writeAuth = writeAuth,
                            keyVersion = keyVersion
                        )
                    } finally {
                        latch.countDown()
                    }
                }
                
                override fun onError(error: String) {
                    LogManager.log(TAG, "获取文档密码失败: $error", "ERROR")
                    FileMetaFactory.initFileMetaWithPermissions(
                        filePath = filePath,
                        oldPass = null,
                        uid = uid,
                        ownerAccount = ownerAccount,
                        ownerName = ownerName,
                        readAuth = readAuth,
                        writeAuth = writeAuth,
                        keyVersion = keyVersion
                    )
                    latch.countDown()
                }
                
                override fun onComplete() {}
            }
        )
    }
    
    /**
     * 读取文件中的keyVersion
     */
    private fun readKeyVersionFromFile(filePath: String): String? {
        LogManager.log(TAG, "开始读取keyVersion，文件路径: $filePath", "DEBUG")
        try {
            val keyVersion = FileMetaManager.getInstance().getKeyVersionFromFile(this, filePath)
            if (keyVersion != null) {
                LogManager.log(TAG, "从本地文件读取到keyVersion: $keyVersion", "DEBUG")
            } else {
                LogManager.log(TAG, "本地文件中未找到keyVersion", "DEBUG")
            }
            return keyVersion
        } catch (e: Exception) {
            LogManager.log(TAG, "读取本地文件keyVersion失败: ${e.message}", "ERROR")
            return null
        }
    }

    /**
     * 读取文件中的加密密码（仅读取，不解析）
     */
    private fun readPassword(filePath: String): String? {
        LogManager.log(TAG, "开始读取加密密码，文件路径: $filePath", "DEBUG")
        return try {
            val file = File(filePath)
            LogManager.log(TAG, "文件存在: ${file.exists()}", "DEBUG")
            LogManager.log(TAG, "文件可读: ${file.canRead()}", "DEBUG")
            LogManager.log(TAG, "文件大小: ${file.length()} 字节", "DEBUG")

            val encryptedPassword = FileMetaManager.getInstance().getPasswordFromFile(this, filePath)
            if (encryptedPassword != null) {
                LogManager.log(TAG, "从本地文件读取到加密密码", "DEBUG")
            } else {
                LogManager.log(TAG, "本地文件中未找到加密密码", "DEBUG")
            }
            encryptedPassword
        } catch (e: Exception) {
            LogManager.log(TAG, "读取本地文件加密密码失败: ${e.message}", "ERROR")
            null
        }
    }

    private fun readUidFromFile(filePath: String): String? {
        LogManager.log(TAG, "开始读取uid并存储到缓存，文件路径: $filePath", "DEBUG")
        try {
            val file = File(filePath)
            LogManager.log(TAG, "文件存在: ${file.exists()}", "DEBUG")
            LogManager.log(TAG, "文件可读: ${file.canRead()}", "DEBUG")
            LogManager.log(TAG, "文件大小: ${file.length()} 字节", "DEBUG")

            val uid = FileMetaManager.getInstance().getUidFromFile(this, filePath)
            if (uid != null) {
                LogManager.log(TAG, "从本地文件读取到uid: $uid", "DEBUG")
            } else {
                LogManager.log(TAG, "本地文件中未找到uid", "DEBUG")
            }
            return uid
        } catch (e: Exception) {
            LogManager.log(TAG, "读取本地文件uid失败: ${e.message}", "ERROR")
            return null
        }
    }

    /**
     * 从ConfigStorage获取token
     */
    private fun getTokenFromStorage(): String? {
        try {
            val userInfo = ConfigStorage.getInstance(this).getUserInfo()
            return userInfo?.token
        } catch (e: Exception) {
            LogManager.log(TAG, "获取token失败: ${e.message}", "ERROR")
            return null
        }
    }

    /**
     * 使用默认权限初始化FileMeta对象（权限均为false）
     */
    private fun initFileMetaWithDefaultPermissions(
        filePath: String,
        password: String?,
        uid: String,
        keyVersion: String? = null
    ) {
        LogManager.log(TAG, "使用默认权限初始化FileMeta对象", "DEBUG")
        FileMetaFactory.initFileMetaWithPermissions(
            filePath = filePath,
            oldPass = password,
            uid = uid,
            ownerAccount = null,
            ownerName = null,
            readAuth = false,
            writeAuth = false,
            keyVersion = keyVersion
        )
        LogManager.log(TAG, "FileMeta对象初始化成功，使用默认权限设置", "DEBUG")
    }


    /**
     * 从ContentURI拷贝文件到目标路径，实现校验机制确保文件完整性
     */
    private fun copyFileFromContentUri(uri: Uri, targetFile: File): Boolean {
        try {
            // 设置插件操作标志，避免触发文件事件监听器
            WpsPasswordManagerApplication.instance.setPluginOperation(true)

            val inputStream = contentResolver.openInputStream(uri)
            inputStream?.use { input ->
                FileOutputStream(targetFile).use { output ->
                    val buffer = ByteArray(4096)
                    var bytesRead: Int
                    var totalBytes = 0L

                    while (input.read(buffer).also { bytesRead = it } != -1) {
                        output.write(buffer, 0, bytesRead)
                        totalBytes += bytesRead
                    }

                    // 验证文件大小
                    if (targetFile.length() == totalBytes) {
                        LogManager.log(TAG, "文件拷贝完成，大小: $totalBytes 字节", "DEBUG")
                        return true
                    } else {
                        LogManager.log(
                            TAG,
                            "文件拷贝不完整，期望大小: $totalBytes, 实际大小: ${targetFile.length()}",
                            "ERROR"
                        )
                        // 删除不完整的文件
                        targetFile.delete()
                        return false
                    }
                }
            }
            return false
        } catch (e: Exception) {
            LogManager.log(TAG, "拷贝文件失败: ${e.message}", "ERROR")
            // 清理失败的文件
            targetFile.delete()
            return false
        }
    }


    /**
     * 保存文件URI到WpsAccessibilityService
     */
    private fun saveFileUriToPreferences(uri: String) {
        try {
            // 直接设置到WpsAccessibilityService的静态变量
            WpsAccessibilityService.currentFileUri = uri
            WpsAccessibilityService.stableDocumentPath = uri
            WpsAccessibilityService.currentDocumentPath = uri
            LogManager.log(TAG, "保存文件URI到WpsAccessibilityService: $uri", "DEBUG")
        } catch (e: Exception) {
            LogManager.log(TAG, "保存文件URI失败: ${e.message}", "ERROR")
        }
    }

    private fun forwardToWps(localFile: File?, originalUri: Uri) {
        try {
            if (localFile != null) {
                val shareUri = getShareableUriFromFile(this, localFile)
                val mimeType = getMimeTypeFromFileExtension(localFile.name)
                LogManager.log(TAG, "插件转换后唤起WPS的ContentURI: $shareUri", "DEBUG")
                LogManager.log(TAG, "文件MIME类型: $mimeType", "DEBUG")
                val wpsIntent = Intent(Intent.ACTION_VIEW)
                wpsIntent.setDataAndType(
                    shareUri,
                    mimeType
                )

                wpsIntent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                wpsIntent.addFlags(Intent.FLAG_GRANT_WRITE_URI_PERMISSION)
                wpsIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)

                wpsIntent.putExtra("OpenMode", "Normal")
                wpsIntent.putExtra("NeedCreateTemp", false)
                wpsIntent.putExtra("ReadOnly", false)

                val wpsPackage = findWpsPackage()
                if (wpsPackage != null) {
                    wpsIntent.setPackage(wpsPackage)
                    grantUriPermission(
                        wpsPackage,
                        shareUri,
                        Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION
                    )

                    startActivity(wpsIntent)
                    LogManager.log(TAG, "通过FileProvider启动WPS成功，包名: $wpsPackage，文件: ${localFile.absolutePath}", "DEBUG")
                    LogManager.log(TAG, "已授予WPS应用读写权限", "DEBUG")
                    LogManager.log(TAG, "已添加WPS特定参数，尝试阻止创建副本", "DEBUG")
                } else {
                    LogManager.log(TAG, "未找到WPS应用，尝试使用文件选择器", "DEBUG")
                    val chooserIntent = Intent.createChooser(wpsIntent, "选择应用打开文件")
                    if (chooserIntent.resolveActivity(packageManager) != null) {
                        chooserIntent.flags =
                            Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION
                        startActivity(chooserIntent)
                        LogManager.log(TAG, "使用文件选择器启动成功", "DEBUG")
                    } else {
                        LogManager.log(TAG, "没有应用可以打开此文件", "ERROR")
                        showErrorNotification("错误", "没有应用可以打开此文件")
                    }
                }
            } else {
                LogManager.log(TAG, "本地文件不存在，尝试使用原始URI", "DEBUG")
                LogManager.log(TAG, "插件转换后唤起WPS的ContentURI: $originalUri", "DEBUG")
                val wpsIntent = Intent(Intent.ACTION_VIEW)
                wpsIntent.data = originalUri
                wpsIntent.flags =
                    Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK

                val wpsPackage = findWpsPackage()
                if (wpsPackage != null) {
                    wpsIntent.setPackage(wpsPackage)
                    startActivity(wpsIntent)
                    LogManager.log(TAG, "使用原始URI启动WPS成功，包名: $wpsPackage", "DEBUG")
                } else {
                    LogManager.log(TAG, "没有应用可以打开此文件", "ERROR")
                    showErrorNotification("错误", "没有应用可以打开此文件")
                }
            }
        } catch (e: Exception) {
            LogManager.log(TAG, "启动 WPS 失败: ${e.message}", "ERROR")
            showErrorNotification("启动失败", "无法启动WPS应用")
        } finally {
            finish()
        }
    }

    private fun getMimeTypeFromFileExtension(fileName: String): String {
        val extension = fileName.substringAfterLast('.', "").lowercase()
        return when (extension) {
            "docx" -> "application/vnd.openxmlformats-officedocument.wordprocessingml.document"
            "xlsx" -> "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"
            "pptx" -> "application/vnd.openxmlformats-officedocument.presentationml.presentation"
            else -> "application/octet-stream"
        }
    }

    private fun findWpsPackage(): String? {
        val configStorage = ConfigStorage.getInstance(this)
        
        val selectedPackage = configStorage.getTargetWpsPackage()
        if (selectedPackage != null) {
            try {
                packageManager.getPackageInfo(selectedPackage, 0)
                LogManager.log(TAG, "使用用户选择的WPS包: $selectedPackage", "DEBUG")
                return selectedPackage
            } catch (e: PackageManager.NameNotFoundException) {
                LogManager.log(TAG, "用户选择的WPS包 $selectedPackage 已卸载，尝试自动查找", "WARN")
                configStorage.clearTargetWpsPackage()
            }
        }
        
        LogManager.log(TAG, "未设置用户选择的WPS包，尝试自动查找", "DEBUG")
        val wpsPackages = arrayOf("cn.wps.moffice_eng", "cn.wps.moffice", "cn.wps.wpsoffice")
        for (pkg in wpsPackages) {
            try {
                packageManager.getPackageInfo(pkg, 0)
                LogManager.log(TAG, "自动找到WPS应用，包名: $pkg", "DEBUG")
                configStorage.saveTargetWpsPackage(pkg)
                return pkg
            } catch (e: PackageManager.NameNotFoundException) {
                LogManager.log(TAG, "WPS包 $pkg 不存在", "DEBUG")
            }
        }
        return null
    }

    /**
     * 通过FileProvider获取可共享的URI
     */
    private fun getShareableUriFromFile(context: Context, file: File): Uri {
        return androidx.core.content.FileProvider.getUriForFile(
            context,
            "com.wpspasswordmanager.fileprovider",
            file
        )
    }

    /**
     * 显示错误通知
     */
    private fun showErrorNotification(title: String, message: String) {
        try {
            val notificationManager =
                getSystemService(Context.NOTIFICATION_SERVICE) as android.app.NotificationManager
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
                val channel = android.app.NotificationChannel(
                    "error_channel",
                    "错误通知",
                    android.app.NotificationManager.IMPORTANCE_HIGH
                )
                notificationManager.createNotificationChannel(channel)
            }
            val notification = androidx.core.app.NotificationCompat.Builder(this, "error_channel")
                .setSmallIcon(R.mipmap.ic_launcher)
                .setContentTitle(title)
                .setContentText(message)
                .setPriority(androidx.core.app.NotificationCompat.PRIORITY_HIGH)
                .build()
            notificationManager.notify(1, notification)
        } catch (e: Exception) {
            LogManager.log(TAG, "显示通知失败: ${e.message}", "ERROR")
        }
    }

}
