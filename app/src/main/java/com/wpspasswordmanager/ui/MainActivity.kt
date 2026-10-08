package com.wpspasswordmanager.ui

import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.os.Environment
import android.provider.Settings
import android.widget.Button
import android.widget.CheckBox
import android.widget.EditText
import android.widget.LinearLayout
import androidx.recyclerview.widget.RecyclerView
import androidx.recyclerview.widget.LinearLayoutManager
import android.widget.Spinner
import android.widget.ArrayAdapter
import android.widget.TextView
import android.view.View
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.wpspasswordmanager.R
import com.wpspasswordmanager.business.WpsAppInfo
import com.wpspasswordmanager.business.WpsManager
import com.wpspasswordmanager.monitor.AccessibilityServiceManager
import com.wpspasswordmanager.monitor.WpsAccessibilityService
import com.wpspasswordmanager.network.NetworkCallback
import com.wpspasswordmanager.network.NetworkManager
import com.wpspasswordmanager.network.HeartbeatService
import com.wpspasswordmanager.network.LoginResponse
import com.wpspasswordmanager.network.ErrorResponse
import com.wpspasswordmanager.storage.ConfigStorage
import com.wpspasswordmanager.storage.ServerConfig
import com.google.gson.Gson
import com.wpspasswordmanager.utils.LogManager
import com.wpspasswordmanager.DirectoryMigrationManager
import com.wpspasswordmanager.WpsPasswordManagerApplication
import java.io.File
import java.io.FileOutputStream

class MainActivity : AppCompatActivity() {
    private val OVERLAY_PERMISSION_REQUEST_CODE = 100
    private val QUERY_ALL_PACKAGES_REQUEST_CODE = 101
    private val STORAGE_PERMISSION_REQUEST_CODE = 102
    private val SAF_REQUEST_CODE = 103
    private val MANAGE_STORAGE_REQUEST_CODE = 104
    private val TAG = "MainActivity"
    
    private var permissionTitleClickCount = 0
    private var permissionTitleFirstClickTime = 0L
    private var appTitleClickCount = 0
    private var appTitleFirstClickTime = 0L
    private var configTitleClickCount = 0
    private var configTitleFirstClickTime = 0L
    private val CLICK_TIME_WINDOW = 1000L
    private var migrationButtonVisible = false
    private lateinit var permissionStatusTitle: TextView

    private lateinit var accessibilityStatus: TextView
    private lateinit var overlayStatus: TextView
    private lateinit var manageStorageStatus: TextView
    private lateinit var enableAccessibilityButton: Button
    private lateinit var enableOverlayButton: Button
    private lateinit var manageStorageButton: Button
    private lateinit var migrationStatus: TextView
    private lateinit var migrationButton: Button

    // 配置管理UI元素
    private lateinit var ipAddressInput: EditText
    private lateinit var portInput: EditText
    private lateinit var usernameInput: EditText
    private lateinit var passwordInput: EditText
    private lateinit var rememberPasswordCheckbox: CheckBox
    private lateinit var loginButton: Button
    private lateinit var userInfoTextView: TextView
    private lateinit var changePasswordButton: Button

    // 错误提示文本框
    private lateinit var ipAddressError: TextView
    private lateinit var portError: TextView
    private lateinit var usernameError: TextView
    private lateinit var passwordError: TextView

    // WPS 应用选择相关 UI
    private lateinit var wpsScanningLayout: LinearLayout
    private lateinit var wpsEmptyLayout: LinearLayout
    private lateinit var wpsAppList: RecyclerView
    private lateinit var wpsSelectedInfo: TextView
    private lateinit var scanWpsButton: Button
    private lateinit var installWpsButton: Button

    // WPS 应用列表数据
    private var wpsApps: List<WpsAppInfo> = emptyList()

    // 存储和网络管理
    private lateinit var configStorage: ConfigStorage
    private lateinit var networkManager: NetworkManager

    // 登录状态管理
    private var isLoggedIn = false
    private lateinit var sessionExpiredReceiver: android.content.BroadcastReceiver

    // 标题点击计数和计时器
    private var titleClickCount = 0
    private var titleClickTimer: android.os.Handler? = null

    // 按钮防重复点击相关
    private var originalButtonText: String = ""
    private var originalButtonBackground: android.graphics.drawable.Drawable? = null
    private var isButtonLoading: Boolean = false
    private var buttonTimeoutTimer: android.os.Handler? = null
    private val TIMEOUT_DURATION = 15000 // 15秒超时

    // 强制改密时的临时token（needChangePwd流程使用，不落地登录态）
    private var pendingChangePwdToken: String? = null

    // 更新包下载任务（用于取消）
    private var updateDownloadCall: okhttp3.Call? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        // 清理cache目录
        clearCacheDirectory()

        // 初始化存储和网络管理
        configStorage = ConfigStorage.getInstance(this)
        networkManager = NetworkManager.getInstance(this)

        // 初始化心跳服务
        val heartbeatIntent = Intent(this, HeartbeatService::class.java)
        startService(heartbeatIntent)

        // 初始化 UI 元素
        initUI()

        // 设置点击事件
        setupClickListeners()

        // 更新权限状态
        updatePermissionStatus()

        // 加载已保存的配置
        loadSavedConfig()

        // 检查并请求存储权限（QUERY_ALL_PACKAGES 权限在存储权限回调中请求）
        checkAndRequestStoragePermission()

        // 初始化会话过期广播接收器
        sessionExpiredReceiver = object : android.content.BroadcastReceiver() {
            override fun onReceive(context: android.content.Context, intent: android.content.Intent) {
                LogManager.log(TAG, "收到会话过期广播", "DEBUG")
                handle401Error()
            }
        }

        // 启动版本检查（免token，未配置服务器时静默跳过）
        checkAppVersion()

        // 检查是否已登录，如果已登录则启动心跳服务
        val userInfo = configStorage.getUserInfo()
        if (userInfo != null) {
            disableButton()
            networkManager.refreshToken(userInfo.token, object : NetworkCallback {
                override fun onSuccess(response: String) {
                    runOnUiThread {
                        clearButtonTimeout()
                        isLoggedIn = true
                        disableConfigInputs()
                        updateLoginButton()
                        userInfoTextView.text = "你好，${userInfo.name}"
                        userInfoTextView.visibility = TextView.VISIBLE
                        enableButton()
                    }
                }

                override fun onError(error: String) {
                    runOnUiThread {
                        clearButtonTimeout()
                        enableButton()
                        handle401Error()
                    }
                }

                override fun onComplete() {}
            })
        }
    }

    private fun initUI() {
        accessibilityStatus = findViewById(R.id.accessibility_status)
        overlayStatus = findViewById(R.id.overlay_status)
        manageStorageStatus = findViewById(R.id.manage_storage_status)
        enableAccessibilityButton = findViewById(R.id.enable_accessibility_button)
        enableOverlayButton = findViewById(R.id.enable_overlay_button)
        manageStorageButton = findViewById(R.id.manage_storage_button)
        migrationStatus = findViewById(R.id.migration_status)
        migrationButton = findViewById(R.id.migration_button)
        permissionStatusTitle = findViewById(R.id.permission_status_title)
        
        migrationButton.visibility = View.GONE

        // 初始化配置管理UI元素
        ipAddressInput = findViewById(R.id.ip_address_input)
        portInput = findViewById(R.id.port_input)
        usernameInput = findViewById(R.id.username_input)
        passwordInput = findViewById(R.id.password_input)
        PasswordEye.attach(passwordInput)
        rememberPasswordCheckbox = findViewById(R.id.remember_password_checkbox)
        loginButton = findViewById(R.id.login_button)
        userInfoTextView = findViewById(R.id.user_info_text_view)
        changePasswordButton = findViewById(R.id.change_password_button)

        // 初始化错误提示文本框
        ipAddressError = findViewById(R.id.ip_address_error)
        portError = findViewById(R.id.port_error)
        usernameError = findViewById(R.id.username_error)
        passwordError = findViewById(R.id.password_error)

        // 初始化 WPS 应用选择相关 UI
        wpsScanningLayout = findViewById(R.id.wps_scanning_layout)
        wpsEmptyLayout = findViewById(R.id.wps_empty_layout)
        wpsAppList = findViewById(R.id.wps_app_list)
            wpsAppList.layoutManager = LinearLayoutManager(this)
        wpsSelectedInfo = findViewById(R.id.wps_selected_info)
        scanWpsButton = findViewById(R.id.scan_wps_button)
        installWpsButton = findViewById(R.id.install_wps_button)

        // 初始化标题点击事件
        val appTitle = findViewById<TextView>(R.id.app_title)
        appTitle.setOnClickListener {
            handleAppTitleClick()
        }

        // 初始化新建文档按钮
        val addDocumentButton = findViewById<Button>(R.id.fab_add_document)
        addDocumentButton.setOnClickListener {
            showCreateDocumentDialog()
        }

        // 初始化版本号
        initVersion()
    }

    private fun initVersion() {
        val versionTextView = findViewById<TextView>(R.id.version_text_view)
        try {
            val packageInfo = packageManager.getPackageInfo(packageName, 0)
            versionTextView.text = "版本 ${packageInfo.versionName}"
        } catch (e: Exception) {
            versionTextView.text = "版本未知"
        }
    }

    /**
     * 客户端版本检查（免token，接口文档v1 11.1）
     * 启动时上报 platform=android 与当前版本号，由服务端判定 updateType：
     * NONE 正常进入；OPTIONAL 弹可更新提示（允许跳过）；FORCE 弹强制更新弹窗（禁止跳过）。
     * 检查失败（未配置服务器/网络异常等）静默忽略，不阻塞进入。
     */
    private fun checkAppVersion() {
        val currentVersion = try {
            packageManager.getPackageInfo(packageName, 0).versionName ?: "0.0.0"
        } catch (e: Exception) {
            "0.0.0"
        }
        LogManager.log(TAG, "启动版本检查: current=$currentVersion", "DEBUG")
        networkManager.checkVersion(currentVersion, object : NetworkCallback {
            override fun onSuccess(response: String) {
                runOnUiThread {
                    try {
                        val json = org.json.JSONObject(response)
                        if (json.getInt("status") == 200) {
                            val data = json.getJSONObject("data")
                            val updateType = data.optString("updateType", "NONE")
                            val latestVersion = data.optString("latestVersion", "")
                            // 服务端字段为 null 时 optString 会返回字符串 "null"，统一归一为空串
                            fun String.normalize(): String = if (this == "null") "" else this
                            val downloadUrl = data.optString("downloadUrl", "").normalize()
                            val changelog = data.optString("changelog", "").normalize()
                            LogManager.log(TAG, "版本检查结果: updateType=$updateType, latest=$latestVersion", "DEBUG")
                            when (updateType) {
                                "FORCE" -> showForceUpdateDialog(latestVersion, downloadUrl, changelog)
                                "OPTIONAL" -> showOptionalUpdateDialog(latestVersion, downloadUrl, changelog)
                                else -> LogManager.log(TAG, "已是最新版本，正常进入", "DEBUG")
                            }
                        } else {
                            LogManager.log(TAG, "版本检查返回非200: ${json.optString("message")}", "DEBUG")
                        }
                    } catch (e: Exception) {
                        LogManager.log(TAG, "版本检查响应解析异常: ${e.message}", "ERROR")
                    }
                }
            }

            override fun onError(error: String) {
                LogManager.log(TAG, "版本检查失败（忽略）: $error", "DEBUG")
            }

            override fun onComplete() {}
        })
    }

    // 可更新提示（OPTIONAL）：允许跳过
    private fun showOptionalUpdateDialog(latestVersion: String, downloadUrl: String, changelog: String) {
        val message = buildString {
            append("最新版本：$latestVersion\n")
            if (changelog.isNotEmpty()) {
                append("\n更新内容：\n$changelog")
            }
        }
        val dialog = android.app.AlertDialog.Builder(this, R.style.Theme_WpsPasswordManager_LightDialog)
            .setTitle("发现新版本")
            .setMessage(message)
            .setCancelable(true)
            .setPositiveButton("立即更新") { d, _ ->
                d.dismiss()
                startUpdateDownload(downloadUrl)
            }
            .setNegativeButton("暂不更新") { d, _ -> d.dismiss() }
            .create()
        dialog.show()
        setupDialogButtons(dialog)
    }

    // 强制更新弹窗（FORCE）：模态不可绕过，点击「确认并退出」杀掉整个App进程
    private fun showForceUpdateDialog(latestVersion: String, downloadUrl: String, changelog: String) {
        val dialog = android.app.AlertDialog.Builder(this, R.style.Theme_WpsPasswordManager_LightDialog)
            .setTitle("强制更新")
            .setMessage("当前版本过低，请联系管理员获取最新版本$latestVersion")
            .setCancelable(false)
            .setPositiveButton("确认并退出") { _, _ ->
                LogManager.log(TAG, "强制更新：用户确认，退出应用", "DEBUG")
                finishAffinity()
                android.os.Process.killProcess(android.os.Process.myPid())
                kotlin.system.exitProcess(0)
            }
            .setNegativeButton("立即更新") { _, _ ->
                startUpdateDownload(downloadUrl)
            }
            .create()
        dialog.setCanceledOnTouchOutside(false)
        dialog.show()
        setupDialogButtons(dialog)
    }

    // 应用内下载更新包：url为空提示联系管理员；否则下载显示进度，完成后自动拉起安装
    private fun startUpdateDownload(downloadUrl: String) {
        if (downloadUrl.isEmpty()) {
            Toast.makeText(this, "未配置下载地址，请联系管理员", Toast.LENGTH_LONG).show()
            return
        }
        // 服务端可能返回相对路径（如 /downloads/xxx.apk），需拼上服务器 base URL 才能下载
        val fullUrl = if (downloadUrl.startsWith("http://") || downloadUrl.startsWith("https://")) {
            downloadUrl
        } else {
            val base = networkManager.getServerBaseUrl()
            if (base.isNullOrEmpty()) {
                Toast.makeText(this, "未配置服务器地址，无法下载更新", Toast.LENGTH_LONG).show()
                return
            }
            val trimmedBase = if (base.endsWith("/")) base.substring(0, base.length - 1) else base
            val trimmedPath = if (downloadUrl.startsWith("/")) downloadUrl else "/$downloadUrl"
            "$trimmedBase$trimmedPath"
        }
        LogManager.log(TAG, "开始下载更新包: $fullUrl", "DEBUG")

        val layout = android.widget.LinearLayout(this).apply {
            orientation = android.widget.LinearLayout.VERTICAL
            setPadding(60, 40, 60, 10)
        }
        val statusTv = android.widget.TextView(this).apply { text = "准备下载..." }
        val progressBar = android.widget.ProgressBar(this, null, android.R.attr.progressBarStyleHorizontal).apply {
            max = 100
            progress = 0
        }
        val percentTv = android.widget.TextView(this).apply { text = "0%" }
        layout.addView(statusTv)
        layout.addView(progressBar)
        layout.addView(percentTv)

        val dialog = android.app.AlertDialog.Builder(this, R.style.Theme_WpsPasswordManager_LightDialog)
            .setTitle("下载更新包")
            .setView(layout)
            .setCancelable(false)
            .setNegativeButton("取消") { d, _ ->
                updateDownloadCall?.cancel()
                d.dismiss()
            }
            .create()
        dialog.show()
        setupDialogButtons(dialog)

        val destDir = getExternalFilesDir("update") ?: java.io.File(filesDir, "update")
        val destFile = java.io.File(destDir, "update_${System.currentTimeMillis()}.apk")

        updateDownloadCall = networkManager.downloadApk(
            fullUrl,
            destFile,
            onProgress = { percent ->
                runOnUiThread {
                    progressBar.progress = percent
                    percentTv.text = "$percent%"
                    statusTv.text = "正在下载..."
                }
            },
            onResult = { success, message ->
                runOnUiThread {
                    if (success) {
                        LogManager.log(TAG, "更新包下载完成: $message", "DEBUG")
                        statusTv.text = "下载完成，正在打开安装程序..."
                        progressBar.progress = 100
                        percentTv.text = "100%"
                        dialog.dismiss()
                        installApk(java.io.File(message))
                    } else {
                        LogManager.log(TAG, "更新包下载失败: $message", "ERROR")
                        statusTv.text = "下载失败：$message"
                        percentTv.text = ""
                    }
                }
            }
        )
    }

    // 通过 FileProvider 自动拉起 APK 安装界面
    private fun installApk(file: java.io.File) {
        try {
            val uri = androidx.core.content.FileProvider.getUriForFile(
                this, "$packageName.fileprovider", file
            )
            val intent = android.content.Intent(android.content.Intent.ACTION_VIEW).apply {
                setDataAndType(uri, "application/vnd.android.package-archive")
                addFlags(
                    android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION
                        or android.content.Intent.FLAG_ACTIVITY_NEW_TASK
                )
            }
            startActivity(intent)
        } catch (e: Exception) {
            LogManager.log(TAG, "拉起安装失败: ${e.message}", "ERROR")
            Toast.makeText(this, "无法启动安装程序", Toast.LENGTH_LONG).show()
        }
    }



    private fun setupClickListeners() {
        enableAccessibilityButton.setOnClickListener {
            val intent = Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)
            startActivity(intent)
        }

        enableOverlayButton.setOnClickListener {
            requestOverlayPermission()
        }

        manageStorageButton.setOnClickListener {
            requestManageStoragePermission()
        }

        loginButton.setOnClickListener {
            if (!isButtonLoading) {
                if (isLoggedIn) {
                    handleLogout()
                } else {
                    handleLogin()
                }
            }
        }

        scanWpsButton.setOnClickListener {
            scanWpsApps()
        }

        installWpsButton.setOnClickListener {
            openWpsInMarket()
        }

        migrationButton.setOnClickListener {
            executeDirectoryMigration()
        }

        permissionStatusTitle.setOnClickListener {
            handlePermissionTitleClick()
        }

        val configManagementTitle = findViewById<TextView>(R.id.config_management_title)
        configManagementTitle.setOnClickListener {
            handleConfigTitleClick()
        }

        changePasswordButton.setOnClickListener {
            showSelfChangePasswordDialog()
        }
    }

    private fun disableButton() {
        if (!isButtonLoading) {
            isButtonLoading = true
            originalButtonText = loginButton.text.toString()
            originalButtonBackground = loginButton.background

            loginButton.isEnabled = false
            loginButton.text = "加载中..."
            loginButton.setBackgroundColor(resources.getColor(android.R.color.darker_gray))

            buttonTimeoutTimer?.removeCallbacksAndMessages(null)
            buttonTimeoutTimer = android.os.Handler()
            buttonTimeoutTimer?.postDelayed({
                enableButton()
                Toast.makeText(this, "请求超时，请重试", Toast.LENGTH_SHORT).show()
            }, TIMEOUT_DURATION.toLong())
        }
    }

    private fun enableButton() {
        isButtonLoading = false
        loginButton.isEnabled = true
        updateLoginButton()

        buttonTimeoutTimer?.removeCallbacksAndMessages(null)
    }

    private fun clearButtonTimeout() {
        buttonTimeoutTimer?.removeCallbacksAndMessages(null)
    }

    private fun handlePermissionTitleClick() {
        val currentTime = System.currentTimeMillis()
        
        if (migrationButtonVisible) {
            migrationButton.visibility = View.GONE
            migrationStatus.visibility = View.GONE
            migrationButtonVisible = false
            permissionTitleClickCount = 0
            permissionTitleFirstClickTime = 0L
            return
        }
        
        if (permissionTitleClickCount == 0) {
            permissionTitleFirstClickTime = currentTime
            permissionTitleClickCount = 1
        } else {
            if (currentTime - permissionTitleFirstClickTime <= CLICK_TIME_WINDOW) {
                permissionTitleClickCount++
                if (permissionTitleClickCount >= 3) {
                    migrationButton.visibility = View.VISIBLE
                    migrationStatus.visibility = View.VISIBLE
                    migrationButtonVisible = true
                    permissionTitleClickCount = 0
                    permissionTitleFirstClickTime = 0L
                }
            } else {
                permissionTitleClickCount = 1
                permissionTitleFirstClickTime = currentTime
            }
        }
    }

    private fun handleAppTitleClick() {
        val currentTime = System.currentTimeMillis()
        
        if (appTitleClickCount == 0) {
            appTitleFirstClickTime = currentTime
            appTitleClickCount = 1
        } else {
            if (currentTime - appTitleFirstClickTime <= CLICK_TIME_WINDOW) {
                appTitleClickCount++
                if (appTitleClickCount >= 3) {
                    val intent = Intent(this, LogActivity::class.java)
                    startActivity(intent)
                    appTitleClickCount = 0
                    appTitleFirstClickTime = 0L
                }
            } else {
                appTitleClickCount = 1
                appTitleFirstClickTime = currentTime
            }
        }
    }

    private fun handleConfigTitleClick() {
        val currentTime = System.currentTimeMillis()
        if (configTitleClickCount == 0) {
            configTitleFirstClickTime = currentTime
            configTitleClickCount = 1
        } else {
            if (currentTime - configTitleFirstClickTime <= CLICK_TIME_WINDOW) {
                configTitleClickCount++
                if (configTitleClickCount >= 3) {
                    val newAllow = !configStorage.getAllowHttp()
                    configStorage.saveAllowHttp(newAllow)
                    networkManager.onServerConfigChanged()
                    val msg = if (newAllow) {
                        "已开启HTTP支持（HTTP/HTTPS均可）"
                    } else {
                        "已关闭HTTP支持（仅HTTPS）"
                    }
                    Toast.makeText(this, msg, Toast.LENGTH_LONG).show()
                    LogManager.log(TAG, "配置管理标题三连点切换协议支持：$msg", "DEBUG")
                    configTitleClickCount = 0
                    configTitleFirstClickTime = 0L
                }
            } else {
                configTitleClickCount = 1
                configTitleFirstClickTime = currentTime
            }
        }
    }

    private fun executeDirectoryMigration() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            if (!Environment.isExternalStorageManager()) {
                requestManageStoragePermission()
                return
            }
        }
        
        if (DirectoryMigrationManager.hasMigrationExceptionRecord()) {
            showMigrationExceptionDialog()
            return
        }
        
        performMigration()
    }

    private fun showMigrationExceptionDialog() {
        val record = DirectoryMigrationManager.getMigrationExceptionRecord()
        val message = "上一次迁移异常，原文件可能已经丢失，请人工查看WpsManagement目录中的文件是否完整"
        
        LogManager.log(TAG, "显示迁移异常对话框: 步骤=${record?.failedStep}, 错误码=${record?.errorCode}", "WARN")
        
        val builder = android.app.AlertDialog.Builder(this, R.style.Theme_WpsPasswordManager_LightDialog)
        builder.setTitle("迁移异常提醒")
        builder.setMessage(message)
        
        builder.setPositiveButton("已确认完整") { dialog, which ->
            LogManager.log(TAG, "用户确认文件完整，清除异常记录并执行迁移", "INFO")
            DirectoryMigrationManager.clearMigrationExceptionRecord()
            dialog.dismiss()
            performMigration()
        }
        
        builder.setNegativeButton("取消") { dialog, which ->
            LogManager.log(TAG, "用户取消迁移操作", "INFO")
            dialog.dismiss()
        }
        
        val dialog = builder.create()
        dialog.setCancelable(false)
        dialog.show()
        setupDialogButtons(dialog)
        dialog.window?.setBackgroundDrawableResource(android.R.color.white)
    }

    private fun requestManageStoragePermission() {
        LogManager.log(TAG, "请求 MANAGE_EXTERNAL_STORAGE 权限", "DEBUG")
        val intent = Intent(Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION)
        intent.data = android.net.Uri.parse("package:$packageName")
        startActivityForResult(intent, MANAGE_STORAGE_REQUEST_CODE)
    }

    private fun requestDocumentsAccess() {
        LogManager.log(TAG, "请求 Documents 目录访问权限 (SAF) - 降级方案", "DEBUG")
        val intent = Intent(Intent.ACTION_OPEN_DOCUMENT_TREE)
        intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        intent.addFlags(Intent.FLAG_GRANT_WRITE_URI_PERMISSION)
        intent.addFlags(Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION)
        intent.addFlags(Intent.FLAG_GRANT_PREFIX_URI_PERMISSION)
        startActivityForResult(intent, SAF_REQUEST_CODE)
    }

    private fun performMigration() {
        migrationButton.isEnabled = false
        migrationButton.text = "执行中..."
        migrationStatus.text = "目录迁移: 正在执行..."
        migrationStatus.setTextColor(resources.getColor(android.R.color.holo_orange_dark))

        DirectoryMigrationManager.execute(this, object : DirectoryMigrationManager.Callback {
            override fun onSuccess() {
                runOnUiThread {
                    migrationButton.isEnabled = true
                    migrationButton.text = "执行目录迁移"
                    migrationStatus.text = "目录迁移: 已完成"
                    migrationStatus.setTextColor(resources.getColor(android.R.color.holo_green_dark))
                    Toast.makeText(this@MainActivity, "目录迁移成功", Toast.LENGTH_SHORT).show()
                    
                    // 目录迁移成功后重启文件观察者
                    WpsPasswordManagerApplication.instance.restartFileObserver()
                }
            }

            override fun onError(errorCode: Int, message: String) {
                runOnUiThread {
                    migrationButton.isEnabled = true
                    migrationButton.text = "执行目录迁移"
                    migrationStatus.text = "目录迁移: 失败 - $message"
                    migrationStatus.setTextColor(resources.getColor(android.R.color.holo_red_dark))
                    Toast.makeText(this@MainActivity, "目录迁移失败(错误码: $errorCode): $message", Toast.LENGTH_LONG).show()
                    
                    // 无论迁移成功与否，都重启文件观察者
                    WpsPasswordManagerApplication.instance.restartFileObserver()
                }
            }
        })
    }

    override fun onResume() {
        super.onResume()
        // 更新权限状态
        updatePermissionStatus()
    }

    private fun updatePermissionStatus() {
        // 检查无障碍服务状态
        val isAccessibilityEnabled = AccessibilityServiceManager.getInstance().isServiceEnabled(this)
        if (isAccessibilityEnabled) {
            accessibilityStatus.text = "无障碍服务: 已启用"
            accessibilityStatus.setTextColor(resources.getColor(android.R.color.holo_green_dark))
            enableAccessibilityButton.text = "已启用"
            enableAccessibilityButton.isEnabled = false
        } else {
            accessibilityStatus.text = "无障碍服务: 未启用"
            accessibilityStatus.setTextColor(resources.getColor(android.R.color.holo_red_dark))
            enableAccessibilityButton.text = "启用"
            enableAccessibilityButton.isEnabled = true
        }

        // 检查悬浮窗权限状态
        val isOverlayEnabled = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            Settings.canDrawOverlays(this)
        } else {
            true
        }
        if (isOverlayEnabled) {
            overlayStatus.text = "悬浮窗权限: 已启用"
            overlayStatus.setTextColor(resources.getColor(android.R.color.holo_green_dark))
            enableOverlayButton.text = "已启用"
            enableOverlayButton.isEnabled = false
        } else {
            overlayStatus.text = "悬浮窗权限: 未启用"
            overlayStatus.setTextColor(resources.getColor(android.R.color.holo_red_dark))
            enableOverlayButton.text = "启用"
            enableOverlayButton.isEnabled = true
        }

        // 检查所有文件管理权限状态
        val isManageStorageEnabled = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            Environment.isExternalStorageManager()
        } else {
            true
        }
        if (isManageStorageEnabled) {
            manageStorageStatus.text = "文档操作权限: 已授权"
            manageStorageStatus.setTextColor(resources.getColor(android.R.color.holo_green_dark))
            manageStorageButton.text = "已授权"
            manageStorageButton.isEnabled = false
        } else {
            manageStorageStatus.text = "文档操作权限: 未授权"
            manageStorageStatus.setTextColor(resources.getColor(android.R.color.holo_red_dark))
            manageStorageButton.text = "授予权限"
            manageStorageButton.isEnabled = true
        }
    }

    // 加载已保存的配置
    private fun loadSavedConfig() {
        // 读取服务器配置
        val serverConfig = configStorage.getServerConfig()
        if (serverConfig != null) {
            ipAddressInput.setText(serverConfig.ipAddress)
            portInput.setText(serverConfig.port)
            usernameInput.setText(serverConfig.username)
        }

        // 读取密码（如果记住密码）
        if (configStorage.getRememberPassword()) {
            val password = configStorage.getPassword()
            if (password != null) {
                passwordInput.setText(password)
                rememberPasswordCheckbox.isChecked = true
            }
        }
    }

    private fun requestOverlayPermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            if (!Settings.canDrawOverlays(this)) {
                val intent = Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION)
                startActivityForResult(intent, OVERLAY_PERMISSION_REQUEST_CODE)
            }
        }
    }

    // 处理登录逻辑
    private fun handleLogin() {
        LogManager.log(TAG, "开始处理登录", "DEBUG")
        // 清除之前的错误提示
        clearErrorMessages()

        // 获取输入值
        val ipAddress = ipAddressInput.text.toString().trim()
        val port = portInput.text.toString().trim()
        val username = usernameInput.text.toString().trim()
        val password = passwordInput.text.toString().trim()
        val rememberPassword = rememberPasswordCheckbox.isChecked

        LogManager.log(TAG, "登录参数: IP=$ipAddress, Port=$port, Username=$username, RememberPassword=$rememberPassword", "DEBUG")

        // 数据校验
        var isValid = true

        // 校验IP地址
        if (ipAddress.isEmpty()) {
            ipAddressError.text = "IP地址不能为空"
            ipAddressError.visibility = TextView.VISIBLE
            isValid = false
            LogManager.log(TAG, "IP地址为空", "DEBUG")
        } else if (ipAddress.startsWith("http://") && !configStorage.getAllowHttp()) {
            ipAddressError.text = "不支持HTTP协议，请使用HTTPS协议"
            ipAddressError.visibility = TextView.VISIBLE
            isValid = false
            LogManager.log(TAG, "IP地址使用HTTP协议", "DEBUG")
        }

        // 校验端口号
        if (port.isEmpty()) {
            portError.text = "端口号不能为空"
            portError.visibility = TextView.VISIBLE
            isValid = false
            LogManager.log(TAG, "端口号为空", "DEBUG")
        } else if (!port.matches("\\d+".toRegex()) || port.toInt() !in 1..65535) {
            portError.text = "请输入有效的端口号（1-65535）"
            portError.visibility = TextView.VISIBLE
            isValid = false
            LogManager.log(TAG, "端口号无效: $port", "DEBUG")
        }

        // 校验用户名
        if (username.isEmpty()) {
            usernameError.text = "用户名不能为空"
            usernameError.visibility = TextView.VISIBLE
            isValid = false
            LogManager.log(TAG, "用户名为空", "DEBUG")
        }

        // 校验密码
        if (password.isEmpty()) {
            passwordError.text = "密码不能为空"
            passwordError.visibility = TextView.VISIBLE
            isValid = false
            LogManager.log(TAG, "密码为空", "DEBUG")
        }

        if (isValid) {
            LogManager.log(TAG, "参数校验通过，准备保存配置并执行登录", "DEBUG")
            
            disableButton()

            saveConfig(ipAddress, port, username, password, rememberPassword)

            performLogin(username, password)
        } else {
            LogManager.log(TAG, "参数校验失败", "DEBUG")
        }
    }

    // 清除错误提示
    private fun clearErrorMessages() {
        ipAddressError.visibility = TextView.GONE
        portError.visibility = TextView.GONE
        usernameError.visibility = TextView.GONE
        passwordError.visibility = TextView.GONE
    }



    // 保存配置信息
    private fun saveConfig(ipAddress: String, port: String, username: String, password: String, rememberPassword: Boolean) {
        // 保存服务器配置
        val serverConfig = ServerConfig(ipAddress, port, username)
        configStorage.saveServerConfig(serverConfig)

        // 保存记住密码状态
        configStorage.saveRememberPassword(rememberPassword)

        // 保存密码（如果选择记住密码）
        if (rememberPassword) {
            configStorage.savePassword(password)
        } else {
            configStorage.clearPassword()
        }

        // 配置更新后刷新网络客户端
        networkManager.onServerConfigChanged()
    }

    // 执行登录请求
    private fun performLogin(username: String, password: String) {
        LogManager.log(TAG, "开始执行登录请求: Username=$username", "DEBUG")
        networkManager.login(username, password, object : NetworkCallback {
            override fun onSuccess(response: String) {
                LogManager.log(TAG, "登录请求成功，响应: $response", "DEBUG")
                runOnUiThread {
                    clearButtonTimeout()
                    processLoginResponse(response, password)
                    enableButton()
                }
            }

            override fun onError(error: String) {
                LogManager.log(TAG, "登录请求失败: $error", "ERROR")
                runOnUiThread {
                    clearButtonTimeout()
                    enableButton()
                    if (error.contains("401")) {
                        handle401Error()
                    } else {
                        Toast.makeText(this@MainActivity, "登录失败：$error", Toast.LENGTH_SHORT).show()
                    }
                }
            }

            override fun onComplete() {}
        })
    }

    // 处理登录响应
    private fun processLoginResponse(response: String, loginPassword: String? = null) {
        LogManager.log(TAG, "开始处理登录响应", "DEBUG")
        val gson = Gson()
        try {
            // 尝试解析为成功响应
            val loginResponse = gson.fromJson(response, LoginResponse::class.java)
            LogManager.log(TAG, "解析登录响应成功: status=${loginResponse.status}, message=${loginResponse.message}", "DEBUG")

            if (loginResponse.status == 200) {
                val loginData = loginResponse.data
                // 强制改密：needChangePwd=true 时不落地登录态，进入改密流程
                if (loginData.needChangePwd == true) {
                    LogManager.log(TAG, "登录响应要求强制修改密码，进入改密流程", "DEBUG")
                    clearButtonTimeout()
                    enableButton()
                    handleNeedChangePassword(loginData.token, loginData.account, loginPassword ?: "")
                    return
                }
                LogManager.log(TAG, "登录成功: account=${loginData.account}, name=${loginData.name}", "DEBUG")
                // 保存用户信息
                configStorage.saveUserInfo(loginData)

                // 更新登录状态
                isLoggedIn = true

                // 禁用配置管理页面的所有输入框
                disableConfigInputs()

                // 变更登录按钮为注销按钮
                updateLoginButton()

                // 显示用户信息
                userInfoTextView.text = "你好，${loginResponse.data.name}"
                userInfoTextView.visibility = TextView.VISIBLE

                // 记录登录日志
                logLoginSuccess(loginResponse.data.account)

                // 显示登录成功提示
                Toast.makeText(this, "登录成功", Toast.LENGTH_SHORT).show()

                // 启动心跳服务
                val heartbeatIntent = Intent(this, HeartbeatService::class.java)
                startService(heartbeatIntent)
            } else {
                // 处理错误状态
                LogManager.log(TAG, "登录失败: ${loginResponse.message}", "ERROR")
                Toast.makeText(this, loginResponse.message, Toast.LENGTH_SHORT).show()
            }
        } catch (e: Exception) {
            LogManager.log(TAG, "解析登录响应失败: ${e.message}", "ERROR")
            // 尝试解析为错误响应
            try {
                val errorResponse = gson.fromJson(response, ErrorResponse::class.java)
                LogManager.log(TAG, "解析错误响应成功: message=${errorResponse.message}", "DEBUG")
                Toast.makeText(this, errorResponse.message, Toast.LENGTH_SHORT).show()
            } catch (e: Exception) {
                LogManager.log(TAG, "解析错误响应失败: ${e.message}", "ERROR")
                // 解析失败
                Toast.makeText(this, "登录失败：响应格式错误", Toast.LENGTH_SHORT).show()
            }
        }
    }

    /**
     * 处理强制改密流程（needChangePwd=true）
     * 此时不落地任何登录态（不保存token/登录标记），仅用登录响应返回的临时token发起改密请求。
     * 改密成功后要求用户用新密码重新登录（下次正常流程，needChangePwd=false）。
     */
    private fun handleNeedChangePassword(tempToken: String, account: String, oldPassword: String) {
        pendingChangePwdToken = tempToken
        val context = this
        val layout = android.widget.LinearLayout(context).apply {
            orientation = android.widget.LinearLayout.VERTICAL
            setPadding(60, 40, 60, 10)
        }
        val newEt = android.widget.EditText(context).apply {
            hint = "新密码"
            inputType = android.text.InputType.TYPE_CLASS_TEXT or android.text.InputType.TYPE_TEXT_VARIATION_PASSWORD
        }
        val confirmEt = android.widget.EditText(context).apply {
            hint = "确认新密码"
            inputType = android.text.InputType.TYPE_CLASS_TEXT or android.text.InputType.TYPE_TEXT_VARIATION_PASSWORD
        }
        val errorTv = android.widget.TextView(context).apply {
            setTextColor(resources.getColor(android.R.color.holo_red_dark))
        }
        PasswordEye.attach(newEt)
        PasswordEye.attach(confirmEt)
        layout.addView(newEt)
        layout.addView(confirmEt)
        layout.addView(errorTv)

        val dialog = android.app.AlertDialog.Builder(context, R.style.Theme_WpsPasswordManager_LightDialog)
            .setTitle("首次登录需修改密码")
            .setView(layout)
            .setCancelable(false)
            .setPositiveButton("确定", null)
            .setNegativeButton("取消") { d, _ -> d.dismiss() }
            .create()

        dialog.setOnShowListener {
            dialog.getButton(android.app.AlertDialog.BUTTON_POSITIVE).setOnClickListener {
                val newP = newEt.text.toString().trim()
                val confirmP = confirmEt.text.toString().trim()
                if (newP.isEmpty() || confirmP.isEmpty()) {
                    errorTv.text = "请输入新密码并确认"
                    return@setOnClickListener
                }
                if (newP != confirmP) {
                    errorTv.text = "两次输入的新密码不一致"
                    return@setOnClickListener
                }
                if (!isValidNewPassword(newP)) {
                    errorTv.text = "新密码需至少8位，且包含大写字母、小写字母和数字"
                    return@setOnClickListener
                }
                // token优先用登录响应返回的临时token，为空则回退全局已存token
                val token = pendingChangePwdToken ?: configStorage.getUserInfo()?.token
                if (token.isNullOrEmpty()) {
                    errorTv.text = "登录态缺失，请重新登录"
                    return@setOnClickListener
                }
                networkManager.changePassword(account, oldPassword, newP, token, object : NetworkCallback {
                    override fun onSuccess(response: String) {
                        runOnUiThread {
                            try {
                                val json = org.json.JSONObject(response)
                                if (json.getInt("status") == 200) {
                                    Toast.makeText(context, "密码修改成功，请使用新密码重新登录", Toast.LENGTH_LONG).show()
                                    dialog.dismiss()
                                    // 重置密码框，要求用户用新密码重新登录（走正常流程）
                                    passwordInput.setText("")
                                    pendingChangePwdToken = null
                                } else {
                                    errorTv.text = json.optString("message", "密码修改失败")
                                }
                            } catch (e: Exception) {
                                errorTv.text = "密码修改失败：解析响应异常"
                            }
                        }
                    }

                    override fun onError(error: String) {
                        runOnUiThread {
                            errorTv.text = "密码修改失败：$error"
                        }
                    }

                    override fun onComplete() {}
                })
            }
        }
        dialog.show()
        setupDialogButtons(dialog)
    }

    // 新密码复杂度校验：至少8位且包含大写字母、小写字母和数字
    private fun isValidNewPassword(pwd: String): Boolean {
        return pwd.length >= 8 &&
            pwd.any { it.isUpperCase() } &&
            pwd.any { it.isLowerCase() } &&
            pwd.any { it.isDigit() }
    }

    /**
     * 自助修改密码（用户主动触发，登录/未登录均可）
     * 与强制改密场景（handleNeedChangePassword，needChangePwd=true）明确区分：
     * - 触发：点击「修改密码」按钮；强制改密：登录响应 needChangePwd=true 自动弹出
     * - 输入：此处需手填 旧密码/新密码/确认新密码 三项；强制改密仅填新密码两项，旧密码自动取登录密码
     * - 账号来源：已登录取已存用户信息；未登录取配置管理里填写的账号（要求 IP/端口/账号均已填写）
     * - token：已登录带已存token；未登录不传token（后端该接口无需token，按请求参数中的账号校验）
     * - 校验：三项均非空、两次新密码一致、新密码不得与旧密码相同
     * - 成功后：已登录则保持登录态；若开启记住密码则同步更新本地存储的密码
     */
    private fun showSelfChangePasswordDialog() {
        val userInfo = configStorage.getUserInfo()
        val account: String
        val token: String?
        if (userInfo != null) {
            account = userInfo.account
            token = userInfo.token
        } else {
            // 未登录：要求服务器地址、端口、账号均已填写
            val ip = ipAddressInput.text.toString().trim()
            val port = portInput.text.toString().trim()
            val acc = usernameInput.text.toString().trim()
            if (ip.isEmpty() || port.isEmpty() || acc.isEmpty()) {
                Toast.makeText(this, "请先填写服务器地址、端口和账号，再修改密码", Toast.LENGTH_LONG).show()
                return
            }
            if (!port.matches("\\d+".toRegex()) || port.toInt() !in 1..65535) {
                Toast.makeText(this, "请输入有效的端口号（1-65535）", Toast.LENGTH_LONG).show()
                return
            }
            // 未登录时当前输入可能尚未落盘，先保存配置，保证网络层能构建URL
            configStorage.saveServerConfig(ServerConfig(ip, port, acc))
            networkManager.onServerConfigChanged()
            account = acc
            token = null // 后端 /account/change-password 无需token，按请求参数中的账号校验
            LogManager.log(TAG, "自助修改密码：未登录状态，使用输入框配置 account=$acc", "DEBUG")
        }
        LogManager.log(TAG, "自助修改密码：打开弹窗", "DEBUG")
        val context = this
        val layout = android.widget.LinearLayout(context).apply {
            orientation = android.widget.LinearLayout.VERTICAL
            setPadding(60, 40, 60, 10)
        }
        val oldEt = android.widget.EditText(context).apply {
            hint = "旧密码"
            inputType = android.text.InputType.TYPE_CLASS_TEXT or android.text.InputType.TYPE_TEXT_VARIATION_PASSWORD
        }
        val newEt = android.widget.EditText(context).apply {
            hint = "新密码"
            inputType = android.text.InputType.TYPE_CLASS_TEXT or android.text.InputType.TYPE_TEXT_VARIATION_PASSWORD
        }
        val confirmEt = android.widget.EditText(context).apply {
            hint = "确认新密码"
            inputType = android.text.InputType.TYPE_CLASS_TEXT or android.text.InputType.TYPE_TEXT_VARIATION_PASSWORD
        }
        val errorTv = android.widget.TextView(context).apply {
            setTextColor(resources.getColor(android.R.color.holo_red_dark))
        }
        PasswordEye.attach(oldEt)
        PasswordEye.attach(newEt)
        PasswordEye.attach(confirmEt)
        layout.addView(oldEt)
        layout.addView(newEt)
        layout.addView(confirmEt)
        layout.addView(errorTv)

        val dialog = android.app.AlertDialog.Builder(context, R.style.Theme_WpsPasswordManager_LightDialog)
            .setTitle("修改密码")
            .setView(layout)
            .setCancelable(false)
            .setPositiveButton("确定", null)
            .setNegativeButton("取消") { d, _ -> d.dismiss() }
            .create()

        dialog.setOnShowListener {
            val positiveBtn = dialog.getButton(android.app.AlertDialog.BUTTON_POSITIVE)
            positiveBtn.setOnClickListener {
                val oldP = oldEt.text.toString().trim()
                val newP = newEt.text.toString().trim()
                val confirmP = confirmEt.text.toString().trim()
                if (oldP.isEmpty() || newP.isEmpty() || confirmP.isEmpty()) {
                    errorTv.text = "请填写旧密码、新密码和确认新密码"
                    return@setOnClickListener
                }
                if (newP != confirmP) {
                    errorTv.text = "两次输入的新密码不一致"
                    return@setOnClickListener
                }
                if (!isValidNewPassword(newP)) {
                    errorTv.text = "新密码需至少8位，且包含大写字母、小写字母和数字"
                    return@setOnClickListener
                }
                if (newP == oldP) {
                    errorTv.text = "新密码不能与旧密码相同"
                    return@setOnClickListener
                }
                positiveBtn.isEnabled = false
                LogManager.log(TAG, "自助修改密码：提交改密请求", "DEBUG")
                networkManager.changePassword(account, oldP, newP, token, object : NetworkCallback {
                    override fun onSuccess(response: String) {
                        runOnUiThread {
                            positiveBtn.isEnabled = true
                            try {
                                val json = org.json.JSONObject(response)
                                if (json.getInt("status") == 200) {
                                    LogManager.log(TAG, "自助修改密码成功", "DEBUG")
                                    // 开启记住密码时，同步更新本地存储的密码，避免下次登录用旧密码
                                    if (configStorage.getRememberPassword()) {
                                        configStorage.savePassword(newP)
                                    }
                                    Toast.makeText(context, "密码修改成功", Toast.LENGTH_LONG).show()
                                    dialog.dismiss()
                                } else {
                                    errorTv.text = json.optString("message", "密码修改失败")
                                }
                            } catch (e: Exception) {
                                errorTv.text = "密码修改失败：解析响应异常"
                            }
                        }
                    }

                    override fun onError(error: String) {
                        runOnUiThread {
                            positiveBtn.isEnabled = true
                            errorTv.text = "密码修改失败：$error"
                        }
                    }

                    override fun onComplete() {}
                })
            }
        }
        dialog.show()
        setupDialogButtons(dialog)
    }

    // 禁用配置管理页面的所有输入框
    private fun disableConfigInputs() {
        ipAddressInput.isEnabled = false
        ipAddressInput.isClickable = false
        ipAddressInput.isFocusable = false
        ipAddressInput.isFocusableInTouchMode = false

        portInput.isEnabled = false
        portInput.isClickable = false
        portInput.isFocusable = false
        portInput.isFocusableInTouchMode = false

        usernameInput.isEnabled = false
        usernameInput.isClickable = false
        usernameInput.isFocusable = false
        usernameInput.isFocusableInTouchMode = false

        passwordInput.isEnabled = false
        passwordInput.isClickable = false
        passwordInput.isFocusable = false
        passwordInput.isFocusableInTouchMode = false

        rememberPasswordCheckbox.isEnabled = false
    }

    // 更新登录/注销按钮状态
    private fun updateLoginButton() {
        if (isLoggedIn) {
            loginButton.text = "注销"
            loginButton.setBackgroundColor(resources.getColor(android.R.color.holo_red_dark))
        } else {
            loginButton.text = "登录"
            loginButton.setBackgroundColor(resources.getColor(android.R.color.holo_purple))
        }
    }

    // 记录登录成功日志
    private fun logLoginSuccess(account: String) {
        val timestamp = System.currentTimeMillis()
        val logMessage = "[${timestamp}] 登录成功 - 账号: $account"
        println(logMessage)
        // 在实际应用中，这里可以使用更专业的日志库，如Logcat或第三方日志库
    }

    // 处理注销逻辑
    private fun handleLogout() {
        val userInfo = configStorage.getUserInfo()
        val username = userInfo?.account ?: "未知用户"
        val token = userInfo?.token

        LogManager.log(TAG, "开始处理注销: username=$username, token=$token", "DEBUG")

        disableButton()

        if (token != null) {
            networkManager.logout(token, object : NetworkCallback {
                override fun onSuccess(response: String) {
                    LogManager.log(TAG, "登出接口调用成功: $response", "DEBUG")
                    runOnUiThread {
                        clearButtonTimeout()
                        completeLogout(username)
                    }
                }

                override fun onError(error: String) {
                    LogManager.log(TAG, "登出接口调用失败: $error", "ERROR")
                    runOnUiThread {
                        clearButtonTimeout()
                        completeLogout(username)
                    }
                }

                override fun onComplete() {}
            })
        } else {
            clearButtonTimeout()
            completeLogout(username)
        }
    }

    private fun completeLogout(username: String) {
        configStorage.clearUserInfo()
        isLoggedIn = false
        enableConfigInputs()
        updateLoginButton()
        enableButton()
        userInfoTextView.visibility = TextView.GONE
        loadSavedConfig()
        logLogoutSuccess(username)
        Toast.makeText(this, "注销成功", Toast.LENGTH_SHORT).show()

        val heartbeatIntent = Intent(this, HeartbeatService::class.java)
        stopService(heartbeatIntent)
    }

    // 启用配置管理页面的所有输入框
    private fun enableConfigInputs() {
        ipAddressInput.isEnabled = true
        ipAddressInput.isClickable = true
        ipAddressInput.isFocusable = true
        ipAddressInput.isFocusableInTouchMode = true

        portInput.isEnabled = true
        portInput.isClickable = true
        portInput.isFocusable = true
        portInput.isFocusableInTouchMode = true

        usernameInput.isEnabled = true
        usernameInput.isClickable = true
        usernameInput.isFocusable = true
        usernameInput.isFocusableInTouchMode = true

        passwordInput.isEnabled = true
        passwordInput.isClickable = true
        passwordInput.isFocusable = true
        passwordInput.isFocusableInTouchMode = true

        rememberPasswordCheckbox.isEnabled = true
    }

    // 记录注销成功日志
    private fun logLogoutSuccess(account: String) {
        val timestamp = System.currentTimeMillis()
        val logMessage = "[${timestamp}] 注销成功 - 账号: $account"
        println(logMessage)
        // 在实际应用中，这里可以使用更专业的日志库，如Logcat或第三方日志库
    }

    // 处理401错误
    private fun handle401Error() {
        LogManager.log(TAG, "处理401错误", "DEBUG")
        // 清理用户信息和状态
        configStorage.clearUserInfo()
        isLoggedIn = false
        enableConfigInputs()
        updateLoginButton()
        userInfoTextView.visibility = TextView.GONE

        // 显示自适应的提示弹窗
        val builder = android.app.AlertDialog.Builder(this, R.style.Theme_WpsPasswordManager_LightDialog)
        builder.setTitle("登录过期")
        builder.setMessage("您的登录已过期，请重新登录")
        builder.setPositiveButton("确定") { dialog, which ->
            dialog.dismiss()
        }
        val dialog = builder.create()
        dialog.show()
        
        setupDialogButtons(dialog)
        
        // 设置弹窗自适应大小
        val window = dialog.window
        if (window != null) {
            val displayMetrics = resources.displayMetrics
            val screenWidth = displayMetrics.widthPixels
            val screenHeight = displayMetrics.heightPixels
            
            // 计算弹窗宽度为屏幕宽度的70%-85%，最小280dp，最大500dp
            val minWidth = (280 * displayMetrics.density).toInt()
            val maxWidth = (500 * displayMetrics.density).toInt()
            val dialogWidth = Math.min(Math.max((screenWidth * 0.8).toInt(), minWidth), maxWidth)
            
            // 计算弹窗高度为屏幕高度的30%-40%，最小200dp，最大350dp
            val minHeight = (200 * displayMetrics.density).toInt()
            val maxHeight = (350 * displayMetrics.density).toInt()
            val dialogHeight = Math.min(Math.max((screenHeight * 0.35).toInt(), minHeight), maxHeight)
            
            window.setLayout(dialogWidth, dialogHeight)
            window.setGravity(android.view.Gravity.CENTER)
            window.setBackgroundDrawableResource(android.R.color.white)
            
            // 设置弹窗内容内边距
            val padding = (24 * displayMetrics.density).toInt()
            window.decorView.setPadding(padding, padding, padding, padding)
        }
    }

    override fun onStart() {
        super.onStart()
        // 注册会话过期广播接收器
        val filter = android.content.IntentFilter("com.wpspasswordmanager.ACTION_SESSION_EXPIRED")
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.TIRAMISU) {
            registerReceiver(sessionExpiredReceiver, filter, android.content.Context.RECEIVER_NOT_EXPORTED)
        } else {
            registerReceiver(sessionExpiredReceiver, filter)
        }
        
        // Token有效性检查已在 onCreate() 中完成，心跳保活由 HeartbeatService 负责
        // 无需在 onStart() 中重复检查，避免重复的 refresh-token 请求
    }

    override fun onStop() {
        super.onStop()
        // 注销会话过期广播接收器
        unregisterReceiver(sessionExpiredReceiver)
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)

        if (requestCode == OVERLAY_PERMISSION_REQUEST_CODE) {
            updatePermissionStatus()

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                if (Settings.canDrawOverlays(this)) {
                    Toast.makeText(this, "已获得显示在其他应用之上的权限", Toast.LENGTH_SHORT).show()
                } else {
                    Toast.makeText(this, "未获得显示在其他应用之上的权限，悬浮按钮功能将无法使用", Toast.LENGTH_LONG).show()
                }
            }
        } else if (requestCode == SAF_REQUEST_CODE) {
            LogManager.log(TAG, "处理 SAF 权限请求结果", "DEBUG")
            
            if (resultCode == RESULT_OK && data != null) {
                val uri = data.data
                if (uri != null) {
                    LogManager.log(TAG, "SAF 权限已授予: $uri", "DEBUG")
                    contentResolver.takePersistableUriPermission(
                        uri,
                        Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION
                    )
                    Toast.makeText(this, "已获得 Documents 目录访问权限", Toast.LENGTH_SHORT).show()
                    performMigration()
                }
            } else {
                LogManager.log(TAG, "SAF 权限被拒绝", "WARN")
                migrationStatus.text = "目录迁移: 权限被拒绝"
                migrationStatus.setTextColor(resources.getColor(android.R.color.holo_red_dark))
                Toast.makeText(this, "未获得 Documents 目录访问权限，无法执行目录迁移", Toast.LENGTH_LONG).show()
            }
        } else if (requestCode == MANAGE_STORAGE_REQUEST_CODE) {
            LogManager.log(TAG, "处理 MANAGE_EXTERNAL_STORAGE 权限请求结果", "DEBUG")
            
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                if (Environment.isExternalStorageManager()) {
                    LogManager.log(TAG, "MANAGE_EXTERNAL_STORAGE 权限已授予", "DEBUG")
                    Toast.makeText(this, "已获得所有文件访问权限", Toast.LENGTH_SHORT).show()
                } else {
                    LogManager.log(TAG, "MANAGE_EXTERNAL_STORAGE 权限被拒绝", "WARN")
                    Toast.makeText(this, "未获得所有文件访问权限", Toast.LENGTH_LONG).show()
                }
            }
            updatePermissionStatus()
        }
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        
        if (requestCode == STORAGE_PERMISSION_REQUEST_CODE) {
            LogManager.log(TAG, "处理存储权限请求结果", "DEBUG")
            
            val readGranted = grantResults.isNotEmpty() && 
                grantResults[0] == android.content.pm.PackageManager.PERMISSION_GRANTED
            val writeGranted = grantResults.size > 1 && 
                grantResults[1] == android.content.pm.PackageManager.PERMISSION_GRANTED
            
            if (readGranted && writeGranted) {
                LogManager.log(TAG, "存储权限已授予", "DEBUG")
                Toast.makeText(this, "已获得存储权限", Toast.LENGTH_SHORT).show()
            }
            
            // 存储权限请求完成后，再请求 QUERY_ALL_PACKAGES 权限
            checkAndRequestQueryAllPackagesPermission()
        } else if (requestCode == QUERY_ALL_PACKAGES_REQUEST_CODE) {
            LogManager.log(TAG, "处理 QUERY_ALL_PACKAGES 权限请求结果", "DEBUG")
            
            if (grantResults.isNotEmpty() && grantResults[0] == android.content.pm.PackageManager.PERMISSION_GRANTED) {
                LogManager.log(TAG, "QUERY_ALL_PACKAGES 权限已授予", "DEBUG")
                Toast.makeText(this, "已获得读取设备应用列表权限", Toast.LENGTH_SHORT).show()
                scanWpsApps()
            } else {
                LogManager.log(TAG, "QUERY_ALL_PACKAGES 权限被拒绝", "WARN")
                Toast.makeText(this, "未获得读取设备应用列表权限，将无法扫描WPS应用", Toast.LENGTH_LONG).show()
                // 仍尝试扫描（可能在 Android 11 之前版本或通过 <queries> 声明可以扫描部分应用）
                scanWpsApps()
            }
        }
    }

    private fun scanWpsApps() {
        LogManager.log(TAG, "========== MainActivity 开始扫描 WPS 应用 ==========", "DEBUG")
        LogManager.log(TAG, "线程: ${Thread.currentThread().name}", "DEBUG")
        
        wpsScanningLayout.visibility = LinearLayout.VISIBLE
        wpsEmptyLayout.visibility = LinearLayout.GONE
        wpsAppList.visibility = RecyclerView.GONE
        wpsSelectedInfo.visibility = TextView.GONE

        Thread {
            LogManager.log(TAG, "在后台线程执行扫描", "DEBUG")
            wpsApps = WpsManager.scanInstalledWpsApps(this@MainActivity)
            
            LogManager.log(TAG, "扫描完成，找到 ${wpsApps.size} 个 WPS 应用", "DEBUG")
            wpsApps.forEach { LogManager.log(TAG, "  - ${it.label} (${it.packageName})", "DEBUG") }
            
            runOnUiThread {
                LogManager.log(TAG, "回到主线程更新 UI", "DEBUG")
                wpsScanningLayout.visibility = LinearLayout.GONE
                
                if (wpsApps.isEmpty()) {
                    LogManager.log(TAG, "未找到 WPS 应用，显示空状态", "WARN")
                    
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                        val permissionStatus = checkQueryAllPackagesPermissionStatus()
                        LogManager.log(TAG, "QUERY_ALL_PACKAGES 权限状态: $permissionStatus", "DEBUG")
                        
                        if (permissionStatus != "granted") {
                            wpsSelectedInfo.text = "需要开启应用信息权限才能扫描WPS应用"
                            wpsSelectedInfo.visibility = TextView.VISIBLE
                            showPermissionDialog()
                        } else {
                            wpsSelectedInfo.text = "扫描结果为空，请检查是否已安装WPS或开启应用信息权限"
                            wpsSelectedInfo.visibility = TextView.VISIBLE
                            wpsEmptyLayout.visibility = LinearLayout.VISIBLE
                            showPermissionDialog()
                        }
                    } else {
                        wpsEmptyLayout.visibility = LinearLayout.VISIBLE
                        wpsSelectedInfo.text = "未选择默认 WPS 应用"
                        wpsSelectedInfo.visibility = TextView.VISIBLE
                    }
                    
                    updateWpsAppList()
                } else {
                    LogManager.log(TAG, "找到 WPS 应用，显示列表", "DEBUG")
                    wpsAppList.visibility = RecyclerView.VISIBLE
                    updateWpsAppList()
                }
            }
        }.start()
    }

    private fun checkQueryAllPackagesPermissionStatus(): String {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.R) {
            return "not_required"
        }
        
        val permission = android.Manifest.permission.QUERY_ALL_PACKAGES
        val result = checkSelfPermission(permission)
        
        return when (result) {
            android.content.pm.PackageManager.PERMISSION_GRANTED -> "granted"
            android.content.pm.PackageManager.PERMISSION_DENIED -> "denied"
            else -> "unknown_$result"
        }
    }

    private fun checkAndRequestStoragePermission() {
        LogManager.log(TAG, "检查存储权限", "DEBUG")

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            val readGranted = checkSelfPermission(android.Manifest.permission.READ_EXTERNAL_STORAGE) ==
                    android.content.pm.PackageManager.PERMISSION_GRANTED
            val writeGranted = checkSelfPermission(android.Manifest.permission.WRITE_EXTERNAL_STORAGE) ==
                    android.content.pm.PackageManager.PERMISSION_GRANTED

            if (!readGranted || !writeGranted) {
                LogManager.log(TAG, "存储权限未授予，请求权限", "DEBUG")
                requestPermissions(
                    arrayOf(
                        android.Manifest.permission.READ_EXTERNAL_STORAGE,
                        android.Manifest.permission.WRITE_EXTERNAL_STORAGE
                    ),
                    STORAGE_PERMISSION_REQUEST_CODE
                )
            } else {
                LogManager.log(TAG, "存储权限已授予", "DEBUG")
            }
        } else {
            LogManager.log(TAG, "Android 版本低于 6.0，存储权限自动授予", "DEBUG")
        }
    }

    private fun checkAndRequestQueryAllPackagesPermission() {
        LogManager.log(TAG, "检查 QUERY_ALL_PACKAGES 权限", "DEBUG")
        
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            LogManager.log(TAG, "Android 版本 >= 11，延迟请求 QUERY_ALL_PACKAGES 权限", "DEBUG")
            android.os.Handler().postDelayed({
                requestPermissions(
                    arrayOf(android.Manifest.permission.QUERY_ALL_PACKAGES),
                    QUERY_ALL_PACKAGES_REQUEST_CODE
                )
            }, 500)
        } else {
            LogManager.log(TAG, "Android 版本低于 11，无需请求权限，直接扫描", "DEBUG")
            scanWpsApps()
        }
    }

    private fun showPermissionDialog() {
        val builder = android.app.AlertDialog.Builder(this, R.style.Theme_WpsPasswordManager_LightDialog)
        builder.setTitle("读取设备应用列表权限")
        builder.setMessage("为了扫描WPS应用，需要授予\"读取设备应用列表\"权限。\n\n请点击确定前往应用信息页面开启权限。")
        builder.setPositiveButton("确定") { _, _ ->
            val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS)
            intent.data = android.net.Uri.parse("package:$packageName")
            startActivity(intent)
        }
        builder.setNegativeButton("取消", null)
        val dialog = builder.create()
        dialog.show()
        setupDialogButtons(dialog)
        dialog.window?.setBackgroundDrawableResource(android.R.color.white)
    }

    private fun updateWpsAppList() {
        LogManager.log(TAG, "更新 WPS 应用列表", "DEBUG")
        
        val selectedPackage = configStorage.getTargetWpsPackage()
        LogManager.log(TAG, "已保存的目标包名: $selectedPackage", "DEBUG")
        
        val adapter = WpsAppRecyclerAdapter(
            this,
            selectedPackage,
            ::onWpsAppSelected
        )
        adapter.submitList(wpsApps)
        
        wpsAppList.adapter = adapter
        
        if (selectedPackage != null) {
            val selectedApp = wpsApps.find { it.packageName == selectedPackage }
            if (selectedApp != null) {
                LogManager.log(TAG, "找到已选择的应用: ${selectedApp.label}", "DEBUG")
                wpsSelectedInfo.text = "已选择: ${selectedApp.label}"
                wpsSelectedInfo.visibility = TextView.VISIBLE
            } else {
                LogManager.log(TAG, "已保存的包名不在当前扫描结果中", "WARN")
                if (wpsApps.isEmpty()) {
                    wpsSelectedInfo.text = "已选择: $selectedPackage"
                    wpsSelectedInfo.visibility = TextView.VISIBLE
                } else {
                    wpsSelectedInfo.text = "请选择默认 WPS 应用"
                    wpsSelectedInfo.visibility = TextView.VISIBLE
                }
            }
        } else {
            LogManager.log(TAG, "未设置默认 WPS 应用", "DEBUG")
            wpsSelectedInfo.text = "请选择默认 WPS 应用"
            wpsSelectedInfo.visibility = TextView.VISIBLE
        }
    }

    private fun onWpsAppSelected(wpsApp: WpsAppInfo) {
        LogManager.log(TAG, "用户选择了 WPS 应用: ${wpsApp.label} (${wpsApp.packageName})", "DEBUG")
        configStorage.saveTargetWpsPackage(wpsApp.packageName)
        LogManager.log(TAG, "已保存到 SharedPreferences", "DEBUG")
        updateWpsAppList()
        Toast.makeText(this, "已设置 ${wpsApp.label} 为默认 WPS 应用", Toast.LENGTH_SHORT).show()
    }

    private fun openWpsInMarket() {
        try {
            val intent = Intent(Intent.ACTION_VIEW)
            intent.data = android.net.Uri.parse("market://details?id=cn.wps.moffice")
            startActivity(intent)
        } catch (e: Exception) {
            try {
                val intent = Intent(Intent.ACTION_VIEW)
                intent.data = android.net.Uri.parse("https://play.google.com/store/apps/details?id=cn.wps.moffice")
                startActivity(intent)
            } catch (ex: Exception) {
                Toast.makeText(this, "无法打开应用商店", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun checkTokenValidity(): Boolean {
        try {
            val userInfo = configStorage.getUserInfo()
            val token = userInfo?.token
            if (token.isNullOrEmpty()) {
                LogManager.log(TAG, "Token不存在", "DEBUG")
                return false
            }
            return true
        } catch (e: Exception) {
            LogManager.log(TAG, "检查Token失败: ${e.message}", "ERROR")
            return false
        }
    }

    private fun checkAppPermissions(): Boolean {
        val isAccessibilityServiceEnabled = isAccessibilityServiceEnabled()
        LogManager.log(TAG, "无障碍服务状态: $isAccessibilityServiceEnabled", "DEBUG")

        val isOverlayPermissionGranted = isOverlayPermissionGranted()
        LogManager.log(TAG, "悬浮窗权限状态: $isOverlayPermissionGranted", "DEBUG")

        return isAccessibilityServiceEnabled && isOverlayPermissionGranted
    }

    private fun checkManageStoragePermission(): Boolean {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            val hasPermission = Environment.isExternalStorageManager()
            LogManager.log(TAG, "MANAGE_EXTERNAL_STORAGE 权限状态: $hasPermission", "DEBUG")
            return hasPermission
        }
        return true
    }

    private fun isAccessibilityServiceEnabled(): Boolean {
        try {
            val accessibilityManager = getSystemService(android.content.Context.ACCESSIBILITY_SERVICE) as android.view.accessibility.AccessibilityManager
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

    private fun isOverlayPermissionGranted(): Boolean {
        try {
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.M) {
                return android.provider.Settings.canDrawOverlays(this)
            } else {
                return true
            }
        } catch (e: Exception) {
            LogManager.log(TAG, "检查悬浮窗权限失败: ${e.message}", "ERROR")
            return false
        }
    }

    private fun checkWpsAppSelected(): Boolean {
        val selectedPackage = configStorage.getTargetWpsPackage()
        
        if (selectedPackage.isNullOrEmpty()) {
            LogManager.log(TAG, "未选择 WPS 应用", "DEBUG")
            return false
        }
        
        try {
            packageManager.getPackageInfo(selectedPackage, 0)
            LogManager.log(TAG, "已选择 WPS 应用: $selectedPackage", "DEBUG")
            return true
        } catch (e: android.content.pm.PackageManager.NameNotFoundException) {
            LogManager.log(TAG, "选择的 WPS 应用 $selectedPackage 已卸载", "WARN")
            configStorage.clearTargetWpsPackage()
            return false
        }
    }

    private fun setupDialogButtons(dialog: android.app.AlertDialog) {
        val negativeButton = dialog.getButton(android.app.AlertDialog.BUTTON_NEGATIVE)
        val positiveButton = dialog.getButton(android.app.AlertDialog.BUTTON_POSITIVE)
        
        if (negativeButton != null) {
            negativeButton.setTextColor(resources.getColor(android.R.color.black))
            negativeButton.setBackgroundColor(resources.getColor(R.color.purple_500))
            val params = negativeButton.layoutParams as android.widget.LinearLayout.LayoutParams
            params.width = android.widget.LinearLayout.LayoutParams.WRAP_CONTENT
            params.height = android.widget.LinearLayout.LayoutParams.WRAP_CONTENT
            params.weight = 0f
            params.marginStart = 16
            params.marginEnd = 8
            negativeButton.layoutParams = params
        }
        
        if (positiveButton != null) {
            positiveButton.setTextColor(resources.getColor(android.R.color.black))
            positiveButton.setBackgroundColor(resources.getColor(R.color.purple_500))
            val params = positiveButton.layoutParams as android.widget.LinearLayout.LayoutParams
            params.width = android.widget.LinearLayout.LayoutParams.WRAP_CONTENT
            params.height = android.widget.LinearLayout.LayoutParams.WRAP_CONTENT
            params.weight = 0f
            params.marginStart = 8
            params.marginEnd = 16
            positiveButton.layoutParams = params
        }
    }

    private fun showCreateDocumentDialog() {
        val dialogBuilder = android.app.AlertDialog.Builder(this, R.style.Theme_WpsPasswordManager_LightDialog)
        dialogBuilder.setTitle("新建文档")

        val inputLayout = android.widget.LinearLayout(this)
        inputLayout.orientation = android.widget.LinearLayout.VERTICAL
        inputLayout.setPadding(48, 24, 48, 16)

        val titleLabel = android.widget.TextView(this)
        titleLabel.text = "请输入文档名称以及选择文档类型，新建文件将保存到/Documents/WpsManagement目录中"
        titleLabel.setTextColor(resources.getColor(android.R.color.black))
        titleLabel.setTextSize(android.util.TypedValue.COMPLEX_UNIT_SP, 14f)
        titleLabel.setPadding(0, 0, 0, 16)
        inputLayout.addView(titleLabel)

        val nameInput = android.widget.EditText(this)
        nameInput.hint = "请输入文档名称前缀"
        nameInput.maxLines = 1
        nameInput.inputType = android.text.InputType.TYPE_CLASS_TEXT
        nameInput.setSingleLine(true)
        val nameParams = android.widget.LinearLayout.LayoutParams(
            android.widget.LinearLayout.LayoutParams.MATCH_PARENT,
            android.widget.LinearLayout.LayoutParams.WRAP_CONTENT
        )
        nameParams.bottomMargin = 8
        nameInput.layoutParams = nameParams
        inputLayout.addView(nameInput)

        val errorText = android.widget.TextView(this)
        errorText.setTextSize(android.util.TypedValue.COMPLEX_UNIT_SP, 12f)
        errorText.setTextColor(resources.getColor(android.R.color.holo_red_light))
        errorText.visibility = android.view.View.GONE
        val errorParams = android.widget.LinearLayout.LayoutParams(
            android.widget.LinearLayout.LayoutParams.MATCH_PARENT,
            android.widget.LinearLayout.LayoutParams.WRAP_CONTENT
        )
        errorParams.bottomMargin = 12
        errorText.layoutParams = errorParams
        inputLayout.addView(errorText)

        val typeLabel = android.widget.TextView(this)
        typeLabel.text = "选择文档类型"
        typeLabel.setTextColor(resources.getColor(android.R.color.black))
        typeLabel.setTextSize(android.util.TypedValue.COMPLEX_UNIT_SP, 14f)
        typeLabel.setPadding(0, 0, 0, 8)
        inputLayout.addView(typeLabel)

        val typeSpinner = Spinner(this)
        val documentTypes = arrayOf("docx", "xlsx", "pptx")
        val adapter = ArrayAdapter(this, android.R.layout.simple_spinner_item, documentTypes)
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        typeSpinner.adapter = adapter
        val spinnerParams = android.widget.LinearLayout.LayoutParams(
            android.widget.LinearLayout.LayoutParams.MATCH_PARENT,
            android.widget.LinearLayout.LayoutParams.WRAP_CONTENT
        )
        spinnerParams.bottomMargin = 16
        typeSpinner.layoutParams = spinnerParams
        inputLayout.addView(typeSpinner)

        dialogBuilder.setView(inputLayout)

        dialogBuilder.setPositiveButton("创建") { _, _ -> }

        dialogBuilder.setNegativeButton("取消") { dialog, _ ->
            dialog.dismiss()
        }

        val dialog = dialogBuilder.create()

        dialog.setOnShowListener {
            val negativeButton = dialog.getButton(android.app.AlertDialog.BUTTON_NEGATIVE)
            val positiveButton = dialog.getButton(android.app.AlertDialog.BUTTON_POSITIVE)

            if (negativeButton != null) {
                negativeButton.setTextColor(resources.getColor(android.R.color.black))
                negativeButton.setBackgroundColor(resources.getColor(R.color.purple_500))
                val params = negativeButton.layoutParams as android.widget.LinearLayout.LayoutParams
                params.width = android.widget.LinearLayout.LayoutParams.WRAP_CONTENT
                params.height = android.widget.LinearLayout.LayoutParams.WRAP_CONTENT
                params.weight = 0f
                params.marginStart = 16
                params.marginEnd = 8
                negativeButton.layoutParams = params
            }

            if (positiveButton != null) {
                positiveButton.setTextColor(resources.getColor(android.R.color.black))
                positiveButton.setBackgroundColor(resources.getColor(R.color.purple_500))
                val params = positiveButton.layoutParams as android.widget.LinearLayout.LayoutParams
                params.width = android.widget.LinearLayout.LayoutParams.WRAP_CONTENT
                params.height = android.widget.LinearLayout.LayoutParams.WRAP_CONTENT
                params.weight = 0f
                params.marginStart = 8
                params.marginEnd = 16
                positiveButton.layoutParams = params

                positiveButton.setOnClickListener {
                    errorText.visibility = android.view.View.GONE

                    if (!checkTokenValidity()) {
                        errorText.text = "用户登录状态已失效，请重新登录"
                        errorText.visibility = android.view.View.VISIBLE
                        return@setOnClickListener
                    }

                    if (!checkAppPermissions()) {
                        errorText.text = "应用权限不足，请检查无障碍服务和悬浮窗权限"
                        errorText.visibility = android.view.View.VISIBLE
                        return@setOnClickListener
                    }

                    if (!checkWpsAppSelected()) {
                        errorText.text = "未选择 WPS 应用，请先选择"
                        errorText.visibility = android.view.View.VISIBLE
                        return@setOnClickListener
                    }

                    if (!checkManageStoragePermission()) {
                        errorText.text = "请先授予【文档操作权限】，否则无法创建文件"
                        errorText.visibility = android.view.View.VISIBLE
                        return@setOnClickListener
                    }

                    val namePrefix = nameInput.text.toString().trim()
                    if (namePrefix.isEmpty()) {
                        errorText.text = "请输入文档名称"
                        errorText.visibility = android.view.View.VISIBLE
                        return@setOnClickListener
                    }

                    val selectedType = typeSpinner.selectedItem.toString()
                    val fileName = "$namePrefix.$selectedType"

                    val documentsDir = android.os.Environment.getExternalStoragePublicDirectory(android.os.Environment.DIRECTORY_DOCUMENTS)
                    val wpsManagementDir = File(documentsDir, "WpsManagement")
                    
                    if (!wpsManagementDir.exists()) {
                        wpsManagementDir.mkdirs()
                    }

                    val targetFile = File(wpsManagementDir, fileName)

                    if (targetFile.exists() && targetFile.length() > 0) {
                        errorText.text = "文件已存在，请输入其他名称"
                        errorText.visibility = android.view.View.VISIBLE
                        return@setOnClickListener
                    }

                    dialog.dismiss()

                    val loadingBuilder = android.app.AlertDialog.Builder(this@MainActivity, R.style.Theme_WpsPasswordManager_LightDialog)
                    loadingBuilder.setMessage("正在创建文档...")
                    loadingBuilder.setCancelable(false)
                    val loadingDialog = loadingBuilder.create()
                    loadingDialog.show()

                    Thread {
                        try {
                            val created = createEmptyDocument(targetFile, selectedType)
                            runOnUiThread {
                                loadingDialog.dismiss()
                                if (created) {
                                    Toast.makeText(this@MainActivity, "文档创建成功", Toast.LENGTH_SHORT).show()
                                    ProxyActivity.openFileWithWps(this@MainActivity, targetFile)
                                } else {
                                    Toast.makeText(this@MainActivity, "文档创建失败", Toast.LENGTH_SHORT).show()
                                }
                            }
                        } catch (e: Exception) {
                            runOnUiThread {
                                loadingDialog.dismiss()
                                Toast.makeText(this@MainActivity, "创建文档时发生错误", Toast.LENGTH_SHORT).show()
                                LogManager.log(TAG, "创建文档失败: ${e.message}", "ERROR")
                            }
                        }
                    }.start()
                }
            }
        }

        val window = dialog.window
        if (window != null) {
            val displayMetrics = resources.displayMetrics
            val dialogWidth = (displayMetrics.widthPixels * 0.85).toInt()
            window.setLayout(dialogWidth, android.view.ViewGroup.LayoutParams.WRAP_CONTENT)
            window.setGravity(android.view.Gravity.CENTER)
            window.setBackgroundDrawableResource(android.R.color.white)
        }

        dialog.show()
    }

    private fun createEmptyDocument(targetFile: File, type: String): Boolean {
        try {
            if (!targetFile.parentFile?.exists()!!) {
                targetFile.parentFile?.mkdirs()
            }

            when (type.toLowerCase()) {
                "docx" -> {
                    return createEmptyDocx(targetFile)
                }
                "xlsx" -> {
                    return createEmptyXlsx(targetFile)
                }
                "pptx" -> {
                    return createEmptyPptx(targetFile)
                }
                else -> {
                    return false
                }
            }
        } catch (e: Exception) {
            LogManager.log(TAG, "创建空文档失败: ${e.message}", "ERROR")
            return false
        }
    }

    private fun createEmptyDocx(targetFile: File): Boolean {
        try {
            val zipOutputStream = java.util.zip.ZipOutputStream(FileOutputStream(targetFile))
            
            val contentTypes = """<?xml version="1.0" encoding="UTF-8" standalone="yes"?>
<Types xmlns="http://schemas.openxmlformats.org/package/2006/content-types">
  <Default Extension="rels" ContentType="application/vnd.openxmlformats-package.relationships+xml"/>
  <Default Extension="xml" ContentType="application/xml"/>
  <Override PartName="/word/document.xml" ContentType="application/vnd.openxmlformats-officedocument.wordprocessingml.document.main+xml"/>
  <Override PartName="/word/styles.xml" ContentType="application/vnd.openxmlformats-officedocument.wordprocessingml.styles+xml"/>
  <Override PartName="/word/settings.xml" ContentType="application/vnd.openxmlformats-officedocument.wordprocessingml.settings+xml"/>
  <Override PartName="/word/webSettings.xml" ContentType="application/vnd.openxmlformats-officedocument.wordprocessingml.webSettings+xml"/>
  <Override PartName="/word/theme/theme1.xml" ContentType="application/vnd.openxmlformats-officedocument.theme+xml"/>
  <Override PartName="/word/fontTable.xml" ContentType="application/vnd.openxmlformats-officedocument.wordprocessingml.fontTable+xml"/>
</Types>"""
            addToZip(zipOutputStream, "[Content_Types].xml", contentTypes.toByteArray())

            val relationships = """<?xml version="1.0" encoding="UTF-8" standalone="yes"?>
<Relationships xmlns="http://schemas.openxmlformats.org/package/2006/relationships">
  <Relationship Id="rId1" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/officeDocument" Target="word/document.xml"/>
</Relationships>"""
            addToZip(zipOutputStream, "_rels/.rels", relationships.toByteArray())

            val document = """<?xml version="1.0" encoding="UTF-8" standalone="yes"?>
<w:document xmlns:w="http://schemas.openxmlformats.org/wordprocessingml/2006/main">
  <w:body>
    <w:p>
      <w:r>
        <w:t></w:t>
      </w:r>
    </w:p>
  </w:body>
</w:document>"""
            addToZip(zipOutputStream, "word/document.xml", document.toByteArray())

            val wordRels = """<?xml version="1.0" encoding="UTF-8" standalone="yes"?>
<Relationships xmlns="http://schemas.openxmlformats.org/package/2006/relationships"/>"""
            addToZip(zipOutputStream, "word/_rels/document.xml.rels", wordRels.toByteArray())

            zipOutputStream.close()
            return true
        } catch (e: Exception) {
            LogManager.log(TAG, "创建空DOCX失败: ${e.message}", "ERROR")
            return false
        }
    }

    private fun createEmptyXlsx(targetFile: File): Boolean {
        try {
            val zipOutputStream = java.util.zip.ZipOutputStream(FileOutputStream(targetFile))
            
            val contentTypes = """<?xml version="1.0" encoding="UTF-8" standalone="yes"?>
<Types xmlns="http://schemas.openxmlformats.org/package/2006/content-types">
  <Default Extension="rels" ContentType="application/vnd.openxmlformats-package.relationships+xml"/>
  <Default Extension="xml" ContentType="application/xml"/>
  <Override PartName="/xl/workbook.xml" ContentType="application/vnd.openxmlformats-officedocument.spreadsheetml.sheet.main+xml"/>
  <Override PartName="/xl/worksheets/sheet1.xml" ContentType="application/vnd.openxmlformats-officedocument.spreadsheetml.worksheet+xml"/>
</Types>"""
            addToZip(zipOutputStream, "[Content_Types].xml", contentTypes.toByteArray())

            val relationships = """<?xml version="1.0" encoding="UTF-8" standalone="yes"?>
<Relationships xmlns="http://schemas.openxmlformats.org/package/2006/relationships">
  <Relationship Id="rId1" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/officeDocument" Target="xl/workbook.xml"/>
</Relationships>"""
            addToZip(zipOutputStream, "_rels/.rels", relationships.toByteArray())

            val workbook = """<?xml version="1.0" encoding="UTF-8" standalone="yes"?>
<workbook xmlns="http://schemas.openxmlformats.org/spreadsheetml/2006/main">
  <sheets>
    <sheet name="Sheet1" sheetId="1" r:id="rId1" xmlns:r="http://schemas.openxmlformats.org/officeDocument/2006/relationships"/>
  </sheets>
</workbook>"""
            addToZip(zipOutputStream, "xl/workbook.xml", workbook.toByteArray())

            val xlRels = """<?xml version="1.0" encoding="UTF-8" standalone="yes"?>
<Relationships xmlns="http://schemas.openxmlformats.org/package/2006/relationships">
  <Relationship Id="rId1" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/worksheet" Target="worksheets/sheet1.xml"/>
</Relationships>"""
            addToZip(zipOutputStream, "xl/_rels/workbook.xml.rels", xlRels.toByteArray())

            val worksheet = """<?xml version="1.0" encoding="UTF-8" standalone="yes"?>
<worksheet xmlns="http://schemas.openxmlformats.org/spreadsheetml/2006/main">
  <sheetData/>
</worksheet>"""
            addToZip(zipOutputStream, "xl/worksheets/sheet1.xml", worksheet.toByteArray())

            zipOutputStream.close()
            return true
        } catch (e: Exception) {
            LogManager.log(TAG, "创建空XLSX失败: ${e.message}", "ERROR")
            return false
        }
    }

    private fun createEmptyPptx(targetFile: File): Boolean {
        try {
            val zipOutputStream = java.util.zip.ZipOutputStream(FileOutputStream(targetFile))
            
            val contentTypes = """<?xml version="1.0" encoding="UTF-8" standalone="yes"?>
<Types xmlns="http://schemas.openxmlformats.org/package/2006/content-types">
  <Default Extension="rels" ContentType="application/vnd.openxmlformats-package.relationships+xml"/>
  <Default Extension="xml" ContentType="application/xml"/>
  <Override PartName="/ppt/presentation.xml" ContentType="application/vnd.openxmlformats-officedocument.presentationml.presentation.main+xml"/>
  <Override PartName="/ppt/slides/slide1.xml" ContentType="application/vnd.openxmlformats-officedocument.presentationml.slide+xml"/>
</Types>"""
            addToZip(zipOutputStream, "[Content_Types].xml", contentTypes.toByteArray())

            val relationships = """<?xml version="1.0" encoding="UTF-8" standalone="yes"?>
<Relationships xmlns="http://schemas.openxmlformats.org/package/2006/relationships">
  <Relationship Id="rId1" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/officeDocument" Target="ppt/presentation.xml"/>
</Relationships>"""
            addToZip(zipOutputStream, "_rels/.rels", relationships.toByteArray())

            val presentation = """<?xml version="1.0" encoding="UTF-8" standalone="yes"?>
<p:presentation xmlns:p="http://schemas.openxmlformats.org/presentationml/2006/main">
  <p:sldMasterIdLst>
    <p:sldMasterId id="256" r:id="rId1" xmlns:r="http://schemas.openxmlformats.org/officeDocument/2006/relationships"/>
  </p:sldMasterIdLst>
  <p:sldIdLst>
    <p:sldId id="256" r:id="rId2" xmlns:r="http://schemas.openxmlformats.org/officeDocument/2006/relationships"/>
  </p:sldIdLst>
</p:presentation>"""
            addToZip(zipOutputStream, "ppt/presentation.xml", presentation.toByteArray())

            val pptRels = """<?xml version="1.0" encoding="UTF-8" standalone="yes"?>
<Relationships xmlns="http://schemas.openxmlformats.org/package/2006/relationships">
  <Relationship Id="rId1" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/slideMaster" Target="slideMasters/slideMaster1.xml"/>
  <Relationship Id="rId2" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/slide" Target="slides/slide1.xml"/>
</Relationships>"""
            addToZip(zipOutputStream, "ppt/_rels/presentation.xml.rels", pptRels.toByteArray())

            val slideMaster = """<?xml version="1.0" encoding="UTF-8" standalone="yes"?>
<p:sldMaster xmlns:p="http://schemas.openxmlformats.org/presentationml/2006/main"/>"""
            addToZip(zipOutputStream, "ppt/slideMasters/slideMaster1.xml", slideMaster.toByteArray())

            val slide = """<?xml version="1.0" encoding="UTF-8" standalone="yes"?>
<p:sld xmlns:p="http://schemas.openxmlformats.org/presentationml/2006/main">
  <p:cSld>
    <p:spTree>
      <p:nvGrpSpPr>
        <p:cNvPr id="1" name=""/>
        <p:cNvGrpSpPr/>
        <p:nvPr/>
      </p:nvGrpSpPr>
      <p:grpSpPr/>
    </p:spTree>
  </p:cSld>
</p:sld>"""
            addToZip(zipOutputStream, "ppt/slides/slide1.xml", slide.toByteArray())

            zipOutputStream.close()
            return true
        } catch (e: Exception) {
            LogManager.log(TAG, "创建空PPTX失败: ${e.message}", "ERROR")
            return false
        }
    }

    private fun createEmptyPdf(targetFile: File): Boolean {
        try {
            val minimalPdf = "%PDF-1.4\n" +
                    "1 0 obj\n" +
                    "<< /Type /Catalog /Pages 2 0 R >>\n" +
                    "endobj\n" +
                    "2 0 obj\n" +
                    "<< /Type /Pages /Kids [3 0 R] /Count 1 >>\n" +
                    "endobj\n" +
                    "3 0 obj\n" +
                    "<< /Type /Page /Parent 2 0 R /MediaBox [0 0 612 792] >>\n" +
                    "endobj\n" +
                    "xref\n" +
                    "0 4\n" +
                    "0000000000 65535 f \n" +
                    "0000000009 00000 n \n" +
                    "0000000058 00000 n \n" +
                    "0000000115 00000 n \n" +
                    "trailer\n" +
                    "<< /Size 4 /Root 1 0 R >>\n" +
                    "startxref\n" +
                    "195\n" +
                    "%%EOF"
            FileOutputStream(targetFile).use { output ->
                output.write(minimalPdf.toByteArray())
            }
            return true
        } catch (e: Exception) {
            LogManager.log(TAG, "创建空PDF失败: ${e.message}", "ERROR")
            return false
        }
    }

    private fun createEmptyTextFile(targetFile: File): Boolean {
        try {
            FileOutputStream(targetFile).use { output ->
                output.write(ByteArray(0))
            }
            return true
        } catch (e: Exception) {
            LogManager.log(TAG, "创建空文本文件失败: ${e.message}", "ERROR")
            return false
        }
    }

    private fun addToZip(zipOutputStream: java.util.zip.ZipOutputStream, entryName: String, data: ByteArray) {
        val entry = java.util.zip.ZipEntry(entryName)
        zipOutputStream.putNextEntry(entry)
        zipOutputStream.write(data)
        zipOutputStream.closeEntry()
    }

    private fun openDocumentInWps(file: File) {
        try {
            WpsAccessibilityService.stableDocumentPath = file.absolutePath
            WpsAccessibilityService.currentDocumentPath = file.absolutePath
            LogManager.log(TAG, "保存文件路径到WpsAccessibilityService: ${file.absolutePath}", "DEBUG")

            val shareUri = androidx.core.content.FileProvider.getUriForFile(
                this,
                "com.wpspasswordmanager.fileprovider",
                file
            )

            val wpsIntent = Intent(Intent.ACTION_VIEW)
            wpsIntent.setDataAndType(shareUri, getMimeType(file.name))

            wpsIntent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            wpsIntent.addFlags(Intent.FLAG_GRANT_WRITE_URI_PERMISSION)
            wpsIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)

            val configStorage = ConfigStorage.getInstance(this)
            val wpsPackage = configStorage.getTargetWpsPackage()

            if (wpsPackage != null) {
                wpsIntent.setPackage(wpsPackage)
                grantUriPermission(
                    wpsPackage,
                    shareUri,
                    Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION
                )
            }

            startActivity(wpsIntent)
            LogManager.log(TAG, "通过FileProvider启动WPS成功，文件: ${file.absolutePath}", "DEBUG")
        } catch (e: Exception) {
            LogManager.log(TAG, "启动WPS失败: ${e.message}", "ERROR")
            Toast.makeText(this, "无法启动WPS应用", Toast.LENGTH_SHORT).show()
        }
    }

    private fun getMimeType(fileName: String): String {
        val extension = fileName.substringAfterLast('.', "").toLowerCase()
        return when (extension) {
            "docx" -> "application/vnd.openxmlformats-officedocument.wordprocessingml.document"
            "xlsx" -> "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"
            "pptx" -> "application/vnd.openxmlformats-officedocument.presentationml.presentation"
            else -> "application/octet-stream"
        }
    }

    private fun clearCacheDirectory() {
        Thread {
            try {
                val cacheDir = File(applicationContext.getExternalFilesDir(null), "cacheView")

                if (cacheDir.exists() && cacheDir.isDirectory) {
                    val files = cacheDir.listFiles()
                    if (files != null) {
                        for (file in files) {
                            if (file.isFile) {
                                if (file.delete()) {
                                    LogManager.log(TAG, "已删除cacheView目录文件: ${file.name}", "DEBUG")
                                } else {
                                    LogManager.log(TAG, "删除cacheView目录文件失败: ${file.name}", "WARN")
                                }
                            }
                        }
                    }
                }
            } catch (e: Exception) {
                LogManager.log(TAG, "清理cacheView目录失败: ${e.message}", "ERROR")
            }
        }.start()

        startCacheCleanupTimer()
    }

    private fun startCacheCleanupTimer() {
        val cleanupHandler = android.os.Handler(android.os.Looper.getMainLooper())
        val cleanupRunnable = object : Runnable {
            override fun run() {
                Thread {
                    try {
                        val cacheDir = File(applicationContext.getExternalFilesDir(null), "cacheView")

                        if (cacheDir.exists() && cacheDir.isDirectory) {
                            val files = cacheDir.listFiles()
                            if (files != null) {
                                for (file in files) {
                                    if (file.isFile) {
                                        if (file.delete()) {
                                            LogManager.log(TAG, "定时清理cacheView目录文件: ${file.name}", "DEBUG")
                                        }
                                    }
                                }
                            }
                        }
                    } catch (e: Exception) {
                        LogManager.log(TAG, "定时清理cacheView目录失败: ${e.message}", "ERROR")
                    }
                }.start()

                cleanupHandler.postDelayed(this, 10 * 60 * 1000)
            }
        }

        cleanupHandler.postDelayed(cleanupRunnable, 10 * 60 * 1000)
    }
}