package com.wpspasswordmanager.ui

import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.widget.Button
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.wpspasswordmanager.R
import com.wpspasswordmanager.business.PasswordGenerator
import com.wpspasswordmanager.business.PasswordStorage
import com.wpspasswordmanager.monitor.AccessibilityServiceManager

class MainActivity : AppCompatActivity() {
    private val OVERLAY_PERMISSION_REQUEST_CODE = 100

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        // 请求显示在其他应用之上的权限
        requestOverlayPermission()

        val enableAccessibilityButton = findViewById<Button>(R.id.enable_accessibility_button)
        enableAccessibilityButton.setOnClickListener {
            val intent = Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)
            startActivity(intent)
        }

        val generatePasswordButton = findViewById<Button>(R.id.generate_password_button)
        generatePasswordButton.setOnClickListener {
            // 生成12位随机密码
            val password = PasswordGenerator.getInstance().generatePassword()
            Toast.makeText(this, "生成的密码: $password", Toast.LENGTH_LONG).show()
            
            // 存储密码（示例：使用当前时间作为key）
            val key = "password_${System.currentTimeMillis()}"
            val stored = PasswordStorage.getInstance().storePassword(this, key, password)
            if (stored) {
                Toast.makeText(this, "密码已存储", Toast.LENGTH_SHORT).show()
            } else {
                Toast.makeText(this, "密码存储失败", Toast.LENGTH_SHORT).show()
            }
            
            // 填充密码到WPS
            AccessibilityServiceManager.getInstance().fillPassword(password)
        }

        val managePasswordsButton = findViewById<Button>(R.id.manage_passwords_button)
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
    }

    override fun onResume() {
        super.onResume()
        // 检查无障碍服务状态
        val isEnabled = AccessibilityServiceManager.getInstance().isServiceEnabled(this)
        val enableAccessibilityButton = findViewById<Button>(R.id.enable_accessibility_button)
        enableAccessibilityButton.text = if (isEnabled) "无障碍服务已启用" else "启用无障碍服务"
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
