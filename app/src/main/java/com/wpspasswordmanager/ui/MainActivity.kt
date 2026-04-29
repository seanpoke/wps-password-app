package com.wpspasswordmanager.ui

import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.widget.Button
import android.widget.CheckBox
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.ListView
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.wpspasswordmanager.R
import com.wpspasswordmanager.business.PasswordGenerator
import com.wpspasswordmanager.business.FileMetaManager
import com.wpspasswordmanager.business.WpsAppInfo
import com.wpspasswordmanager.business.WpsManager
import com.wpspasswordmanager.monitor.AccessibilityServiceManager
import com.wpspasswordmanager.network.NetworkCallback
import com.wpspasswordmanager.network.NetworkManager
import com.wpspasswordmanager.network.HeartbeatService
import com.wpspasswordmanager.network.LoginResponse
import com.wpspasswordmanager.network.ErrorResponse
import com.wpspasswordmanager.storage.ConfigStorage
import com.wpspasswordmanager.storage.ServerConfig
import com.wpspasswordmanager.storage.UserInfo
import com.google.gson.Gson
import com.wpspasswordmanager.utils.LogManager

class MainActivity : AppCompatActivity() {
    private val OVERLAY_PERMISSION_REQUEST_CODE = 100
    private val QUERY_ALL_PACKAGES_REQUEST_CODE = 101
    private val TAG = "MainActivity"

    private lateinit var accessibilityStatus: TextView
    private lateinit var overlayStatus: TextView
    private lateinit var enableAccessibilityButton: Button
    private lateinit var enableOverlayButton: Button

    // 配置管理UI元素
    private lateinit var ipAddressInput: EditText
    private lateinit var portInput: EditText
    private lateinit var usernameInput: EditText
    private lateinit var passwordInput: EditText
    private lateinit var rememberPasswordCheckbox: CheckBox
    private lateinit var loginButton: Button
    private lateinit var userInfoTextView: TextView

    // 错误提示文本框
    private lateinit var ipAddressError: TextView
    private lateinit var portError: TextView
    private lateinit var usernameError: TextView
    private lateinit var passwordError: TextView

    // WPS 应用选择相关 UI
    private lateinit var wpsScanningLayout: LinearLayout
    private lateinit var wpsEmptyLayout: LinearLayout
    private lateinit var wpsAppList: ListView
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

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

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

        // 检查并请求 QUERY_ALL_PACKAGES 权限（Android 11+）
        checkAndRequestQueryAllPackagesPermission()

        // 初始化会话过期广播接收器
        sessionExpiredReceiver = object : android.content.BroadcastReceiver() {
            override fun onReceive(context: android.content.Context, intent: android.content.Intent) {
                LogManager.log(TAG, "收到会话过期广播", "DEBUG")
                handle401Error()
            }
        }

        // 检查是否已登录，如果已登录则启动心跳服务
        val userInfo = configStorage.getUserInfo()
        if (userInfo != null) {
            // 立即检查token有效性
            networkManager.refreshToken(userInfo.token, object : NetworkCallback {
                override fun onSuccess(response: String) {
                    // Token有效，保持登录状态
                    runOnUiThread {
                        isLoggedIn = true
                        disableConfigInputs()
                        updateLoginButton()
                        userInfoTextView.text = "你好，${userInfo.name}"
                        userInfoTextView.visibility = TextView.VISIBLE
                    }
                }

                override fun onError(error: String) {
                // Token无效（401）或网络错误，清理登录状态
                runOnUiThread {
                    handle401Error()
                }
            }
            })
        }
    }

    private fun initUI() {
        accessibilityStatus = findViewById(R.id.accessibility_status)
        overlayStatus = findViewById(R.id.overlay_status)
        enableAccessibilityButton = findViewById(R.id.enable_accessibility_button)
        enableOverlayButton = findViewById(R.id.enable_overlay_button)

        // 初始化配置管理UI元素
        ipAddressInput = findViewById(R.id.ip_address_input)
        portInput = findViewById(R.id.port_input)
        usernameInput = findViewById(R.id.username_input)
        passwordInput = findViewById(R.id.password_input)
        rememberPasswordCheckbox = findViewById(R.id.remember_password_checkbox)
        loginButton = findViewById(R.id.login_button)
        userInfoTextView = findViewById(R.id.user_info_text_view)

        // 初始化错误提示文本框
        ipAddressError = findViewById(R.id.ip_address_error)
        portError = findViewById(R.id.port_error)
        usernameError = findViewById(R.id.username_error)
        passwordError = findViewById(R.id.password_error)

        // 初始化 WPS 应用选择相关 UI
        wpsScanningLayout = findViewById(R.id.wps_scanning_layout)
        wpsEmptyLayout = findViewById(R.id.wps_empty_layout)
        wpsAppList = findViewById(R.id.wps_app_list)
        wpsSelectedInfo = findViewById(R.id.wps_selected_info)
        scanWpsButton = findViewById(R.id.scan_wps_button)
        installWpsButton = findViewById(R.id.install_wps_button)

        // 初始化标题点击事件
        val appTitle = findViewById<TextView>(R.id.app_title)
        appTitle.setOnClickListener {
            handleTitleClick()
        }
    }

    private fun handleTitleClick() {
        titleClickCount++
        
        // 重置计时器
        titleClickTimer?.removeCallbacksAndMessages(null)
        titleClickTimer = android.os.Handler()
        titleClickTimer?.postDelayed({
            titleClickCount = 0
        }, 1000) // 1秒内点击3次
        
        // 连续点击3次，打开日志页面
        if (titleClickCount == 3) {
            val intent = Intent(this, LogActivity::class.java)
            startActivity(intent)
            titleClickCount = 0
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

        loginButton.setOnClickListener {
            if (isLoggedIn) {
                handleLogout()
            } else {
                handleLogin()
            }
        }

        scanWpsButton.setOnClickListener {
            scanWpsApps()
        }

        installWpsButton.setOnClickListener {
            openWpsInMarket()
        }
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
            // 保存配置信息
            saveConfig(ipAddress, port, username, password, rememberPassword)

            // 执行登录请求
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
    }

    // 执行登录请求
    private fun performLogin(username: String, password: String) {
        LogManager.log(TAG, "开始执行登录请求: Username=$username", "DEBUG")
        // 执行真实的网络请求（异步）
        networkManager.login(username, password, object : NetworkCallback {
            override fun onSuccess(response: String) {
                LogManager.log(TAG, "登录请求成功，响应: $response", "DEBUG")
                // 在主线程更新UI
                runOnUiThread {
                    processLoginResponse(response)
                }
            }

            override fun onError(error: String) {
                LogManager.log(TAG, "登录请求失败: $error", "ERROR")
                // 在主线程更新UI
                runOnUiThread {
                    if (error.contains("401")) {
                        handle401Error()
                    } else {
                        Toast.makeText(this@MainActivity, "登录失败：$error", Toast.LENGTH_SHORT).show()
                    }
                }
            }
        })
    }

    // 处理登录响应
    private fun processLoginResponse(response: String) {
        LogManager.log(TAG, "开始处理登录响应", "DEBUG")
        val gson = Gson()
        try {
            // 尝试解析为成功响应
            val loginResponse = gson.fromJson(response, LoginResponse::class.java)
            LogManager.log(TAG, "解析登录响应成功: status=${loginResponse.status}, message=${loginResponse.message}", "DEBUG")

            if (loginResponse.status == 200) {
                LogManager.log(TAG, "登录成功: account=${loginResponse.data.account}, name=${loginResponse.data.name}", "DEBUG")
                // 保存用户信息
                configStorage.saveUserInfo(loginResponse.data)

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
        // 获取当前用户信息用于日志记录和登出请求
        val userInfo = configStorage.getUserInfo()
        val username = userInfo?.account ?: "未知用户"
        val token = userInfo?.token

        LogManager.log(TAG, "开始处理注销: username=$username, token=$token", "DEBUG")

        // 调用登出接口（如果有token）
        if (token != null) {
            networkManager.logout(token, object : NetworkCallback {
                override fun onSuccess(response: String) {
                    LogManager.log(TAG, "登出接口调用成功: $response", "DEBUG")
                }

                override fun onError(error: String) {
                    LogManager.log(TAG, "登出接口调用失败: $error", "ERROR")
                    // 登出请求失败不影响界面正常跳转
                }
            })
        }

        // 清理用户信息和密码缓存（保留服务器配置）
        configStorage.clearUserInfo()

        // 更新登录状态
        isLoggedIn = false

        // 启用配置管理页面的所有输入框
        enableConfigInputs()

        // 变更注销按钮为登录按钮
        updateLoginButton()

        // 隐藏用户信息
        userInfoTextView.visibility = TextView.GONE

        // 重新加载配置信息
        loadSavedConfig()

        // 记录注销日志
        logLogoutSuccess(username)

        // 显示注销成功提示
        Toast.makeText(this, "注销成功", Toast.LENGTH_SHORT).show()

        // 停止心跳服务
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
        
        // 显示居中较小的提示弹窗
        val builder = android.app.AlertDialog.Builder(this)
        builder.setTitle("登录过期")
        builder.setMessage("您的登录已过期，请重新登录")
        builder.setPositiveButton("确定") { dialog, which ->
            dialog.dismiss()
        }
        val dialog = builder.create()
        dialog.show()
        
        // 设置弹窗大小
        val window = dialog.window
        window?.setLayout(600, 400) // 设置弹窗宽度为600px，高度为400px
        window?.setGravity(android.view.Gravity.CENTER) // 设置弹窗居中
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
        
        // 再次检查用户信息状态
        val userInfo = configStorage.getUserInfo()
        if (userInfo != null && isLoggedIn) {
            // 检查token有效性
            networkManager.refreshToken(userInfo.token, object : NetworkCallback {
                override fun onSuccess(response: String) {
                    // Token有效，保持登录状态
                }

                override fun onError(error: String) {
                    // Token无效（401）或网络错误，清理登录状态
                    runOnUiThread {
                        handle401Error()
                    }
                }
            })
        }
    }

    override fun onStop() {
        super.onStop()
        // 注销会话过期广播接收器
        unregisterReceiver(sessionExpiredReceiver)
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == OVERLAY_PERMISSION_REQUEST_CODE) {
            // 更新权限状态
            updatePermissionStatus()

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                if (Settings.canDrawOverlays(this)) {
                    Toast.makeText(this, "已获得显示在其他应用之上的权限", Toast.LENGTH_SHORT).show()
                } else {
                    Toast.makeText(this, "未获得显示在其他应用之上的权限，悬浮按钮功能将无法使用", Toast.LENGTH_LONG).show()
                }
            }
        }
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        
        if (requestCode == QUERY_ALL_PACKAGES_REQUEST_CODE) {
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
        wpsAppList.visibility = ListView.GONE
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
                } else {
                    LogManager.log(TAG, "找到 WPS 应用，显示列表", "DEBUG")
                    wpsAppList.visibility = ListView.VISIBLE
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
        val builder = android.app.AlertDialog.Builder(this)
        builder.setTitle("读取设备应用列表权限")
        builder.setMessage("为了扫描WPS应用，需要授予\"读取设备应用列表\"权限。\n\n请点击确定前往应用信息页面开启权限。")
        builder.setPositiveButton("确定") { _, _ ->
            val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS)
            intent.data = android.net.Uri.parse("package:$packageName")
            startActivity(intent)
        }
        builder.setNegativeButton("取消", null)
        builder.show()
    }

    private fun updateWpsAppList() {
        LogManager.log(TAG, "更新 WPS 应用列表", "DEBUG")
        
        val selectedPackage = configStorage.getTargetWpsPackage()
        LogManager.log(TAG, "已保存的目标包名: $selectedPackage", "DEBUG")
        
        val adapter = WpsAppAdapter(
            this,
            wpsApps,
            selectedPackage,
            ::onWpsAppSelected
        )
        
        wpsAppList.adapter = adapter
        
        if (selectedPackage != null) {
            val selectedApp = wpsApps.find { it.packageName == selectedPackage }
            if (selectedApp != null) {
                LogManager.log(TAG, "找到已选择的应用: ${selectedApp.label}", "DEBUG")
                wpsSelectedInfo.text = "已选择: ${selectedApp.label}"
                wpsSelectedInfo.visibility = TextView.VISIBLE
            } else {
                LogManager.log(TAG, "已保存的包名不在当前扫描结果中", "WARN")
                wpsSelectedInfo.text = "请选择默认 WPS 应用"
                wpsSelectedInfo.visibility = TextView.VISIBLE
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
}