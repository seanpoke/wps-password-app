package com.wpspasswordmanager.ui

import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.widget.Button
import android.widget.CheckBox
import android.widget.EditText
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.wpspasswordmanager.R
import com.wpspasswordmanager.business.PasswordGenerator
import com.wpspasswordmanager.business.FileMetaManager
import com.wpspasswordmanager.monitor.AccessibilityServiceManager
import com.wpspasswordmanager.network.NetworkManager
import com.wpspasswordmanager.storage.ConfigStorage
import com.wpspasswordmanager.storage.ServerConfig
import com.wpspasswordmanager.storage.UserInfo
import com.google.gson.Gson

class MainActivity : AppCompatActivity() {
    private val OVERLAY_PERMISSION_REQUEST_CODE = 100

    private lateinit var accessibilityStatus: TextView
    private lateinit var overlayStatus: TextView
    private lateinit var enableAccessibilityButton: Button
    private lateinit var enableOverlayButton: Button
    private lateinit var generatePasswordButton: Button
    private lateinit var managePasswordsButton: Button
    private lateinit var settingsButton: Button

    // 配置管理UI元素
    private lateinit var ipAddressInput: EditText
    private lateinit var portInput: EditText
    private lateinit var usernameInput: EditText
    private lateinit var passwordInput: EditText
    private lateinit var rememberPasswordCheckbox: CheckBox
    private lateinit var loginButton: Button

    // 错误提示文本框
    private lateinit var ipAddressError: TextView
    private lateinit var portError: TextView
    private lateinit var usernameError: TextView
    private lateinit var passwordError: TextView

    // 存储和网络管理
    private lateinit var configStorage: ConfigStorage
    private lateinit var networkManager: NetworkManager

    // 登录状态管理
    private var isLoggedIn = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        // 初始化存储和网络管理
        configStorage = ConfigStorage.getInstance(this)
        networkManager = NetworkManager.getInstance(this)

        // 初始化 UI 元素
        initUI()

        // 设置点击事件
        setupClickListeners()

        // 更新权限状态
        updatePermissionStatus()

        // 加载已保存的配置
        loadSavedConfig()
    }

    private fun initUI() {
        accessibilityStatus = findViewById(R.id.accessibility_status)
        overlayStatus = findViewById(R.id.overlay_status)
        enableAccessibilityButton = findViewById(R.id.enable_accessibility_button)
        enableOverlayButton = findViewById(R.id.enable_overlay_button)
        generatePasswordButton = findViewById(R.id.generate_password_button)
        managePasswordsButton = findViewById(R.id.manage_passwords_button)
        settingsButton = findViewById(R.id.settings_button)

        // 初始化配置管理UI元素
        ipAddressInput = findViewById(R.id.ip_address_input)
        portInput = findViewById(R.id.port_input)
        usernameInput = findViewById(R.id.username_input)
        passwordInput = findViewById(R.id.password_input)
        rememberPasswordCheckbox = findViewById(R.id.remember_password_checkbox)
        loginButton = findViewById(R.id.login_button)

        // 初始化错误提示文本框
        ipAddressError = findViewById(R.id.ip_address_error)
        portError = findViewById(R.id.port_error)
        usernameError = findViewById(R.id.username_error)
        passwordError = findViewById(R.id.password_error)
    }

    private fun setupClickListeners() {
        enableAccessibilityButton.setOnClickListener {
            val intent = Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)
            startActivity(intent)
        }

        enableOverlayButton.setOnClickListener {
            requestOverlayPermission()
        }

        generatePasswordButton.setOnClickListener {
            // 生成12位随机密码
            val password = PasswordGenerator.getInstance().generatePassword()
            Toast.makeText(this, "生成的密码: $password", Toast.LENGTH_LONG).show()
            
            // 填充密码到WPS
            AccessibilityServiceManager.getInstance().fillPassword(password)
        }

        managePasswordsButton.setOnClickListener {
            // 示例：读取存储的密码
            val key = "password_1234567890" // 示例key
            val password = FileMetaManager.getInstance().getPasswordFromFile(this, key)
            if (password != null) {
                Toast.makeText(this, "读取的密码: $password", Toast.LENGTH_LONG).show()
            } else {
                Toast.makeText(this, "未找到密码", Toast.LENGTH_SHORT).show()
            }
        }

        settingsButton.setOnClickListener {
            val intent = Intent(this, SettingsActivity::class.java)
            startActivity(intent)
        }

        loginButton.setOnClickListener {
            if (isLoggedIn) {
                handleLogout()
            } else {
                handleLogin()
            }
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
        // 清除之前的错误提示
        clearErrorMessages()

        // 获取输入值
        val ipAddress = ipAddressInput.text.toString().trim()
        val port = portInput.text.toString().trim()
        val username = usernameInput.text.toString().trim()
        val password = passwordInput.text.toString().trim()
        val rememberPassword = rememberPasswordCheckbox.isChecked

        // 数据校验
        var isValid = true

        // 校验IP地址
        if (ipAddress.isEmpty()) {
            ipAddressError.text = "IP地址不能为空"
            ipAddressError.visibility = TextView.VISIBLE
            isValid = false
        }

        // 校验端口号
        if (port.isEmpty()) {
            portError.text = "端口号不能为空"
            portError.visibility = TextView.VISIBLE
            isValid = false
        } else if (!port.matches("\\d+".toRegex()) || port.toInt() !in 1..65535) {
            portError.text = "请输入有效的端口号（1-65535）"
            portError.visibility = TextView.VISIBLE
            isValid = false
        }

        // 校验用户名
        if (username.isEmpty()) {
            usernameError.text = "用户名不能为空"
            usernameError.visibility = TextView.VISIBLE
            isValid = false
        }

        // 校验密码
        if (password.isEmpty()) {
            passwordError.text = "密码不能为空"
            passwordError.visibility = TextView.VISIBLE
            isValid = false
        }

        if (isValid) {
            // 保存配置信息
            saveConfig(ipAddress, port, username, password, rememberPassword)

            // 执行登录请求
            performLogin(username, password)
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
        // 在实际应用中，这里应该使用NetworkManager执行网络请求
        // 这里为了演示，我们模拟一个登录成功的响应
        val mockResponse = "{ \"username\": \"$username\", \"token\": \"mock_token_123\"}"
        
        // 解析登录响应
        val gson = Gson()
        val userInfo = gson.fromJson(mockResponse, UserInfo::class.java)
        
        // 保存用户信息
        configStorage.saveUserInfo(userInfo)
        
        // 更新登录状态
        isLoggedIn = true
        
        // 禁用配置管理页面的所有输入框
        disableConfigInputs()
        
        // 变更登录按钮为注销按钮
        updateLoginButton()
        
        // 记录登录日志
        logLoginSuccess(username)
        
        // 显示登录成功提示
        Toast.makeText(this, "登录成功", Toast.LENGTH_SHORT).show()
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
    private fun logLoginSuccess(username: String) {
        val timestamp = System.currentTimeMillis()
        val logMessage = "[${timestamp}] 登录成功 - 用户名: $username"
        println(logMessage)
        // 在实际应用中，这里可以使用更专业的日志库，如Logcat或第三方日志库
    }

    // 处理注销逻辑
    private fun handleLogout() {
        // 获取当前用户名用于日志记录
        val userInfo = configStorage.getUserInfo()
        val username = userInfo?.username ?: "未知用户"
        
        // 清理所有用户信息缓存
        configStorage.clearAll()
        
        // 更新登录状态
        isLoggedIn = false
        
        // 启用配置管理页面的所有输入框
        enableConfigInputs()
        
        // 变更注销按钮为登录按钮
        updateLoginButton()
        
        // 重新加载配置信息
        loadSavedConfig()
        
        // 记录注销日志
        logLogoutSuccess(username)
        
        // 显示注销成功提示
        Toast.makeText(this, "注销成功", Toast.LENGTH_SHORT).show()
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
    private fun logLogoutSuccess(username: String) {
        val timestamp = System.currentTimeMillis()
        val logMessage = "[${timestamp}] 注销成功 - 用户名: $username"
        println(logMessage)
        // 在实际应用中，这里可以使用更专业的日志库，如Logcat或第三方日志库
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
}
