package com.wpspasswordmanager.ui

import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.widget.Button
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.wpspasswordmanager.R
import com.wpspasswordmanager.business.MemoryPasswordStorage
import com.wpspasswordmanager.business.PasswordGenerator
import com.wpspasswordmanager.business.PasswordStorage
import com.wpspasswordmanager.monitor.AccessibilityServiceManager

class MainActivity : AppCompatActivity() {
    private val OVERLAY_PERMISSION_REQUEST_CODE = 100

    private lateinit var accessibilityStatus: TextView
    private lateinit var overlayStatus: TextView
    private lateinit var enableAccessibilityButton: Button
    private lateinit var enableOverlayButton: Button
    private lateinit var generatePasswordButton: Button
    private lateinit var managePasswordsButton: Button
    private lateinit var settingsButton: Button

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        // 初始化 MemoryPasswordStorage
        MemoryPasswordStorage.init(this)

        // 初始化 UI 元素
        initUI()

        // 设置点击事件
        setupClickListeners()

        // 更新权限状态
        updatePermissionStatus()
    }

    private fun initUI() {
        accessibilityStatus = findViewById(R.id.accessibility_status)
        overlayStatus = findViewById(R.id.overlay_status)
        enableAccessibilityButton = findViewById(R.id.enable_accessibility_button)
        enableOverlayButton = findViewById(R.id.enable_overlay_button)
        generatePasswordButton = findViewById(R.id.generate_password_button)
        managePasswordsButton = findViewById(R.id.manage_passwords_button)
        settingsButton = findViewById(R.id.settings_button)
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
            val password = PasswordStorage.getInstance().getPassword(this, key)
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

    private fun requestOverlayPermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            if (!Settings.canDrawOverlays(this)) {
                val intent = Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION)
                startActivityForResult(intent, OVERLAY_PERMISSION_REQUEST_CODE)
            }
        }
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
