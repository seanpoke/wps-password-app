package com.wpspasswordmanager.monitor

import android.content.Context
import android.content.Intent
import android.provider.Settings
import android.util.Log

class AccessibilityServiceManager private constructor() {

    companion object {
        private const val TAG = "AccessibilityServiceManager"
        private var instance: AccessibilityServiceManager? = null

        fun getInstance(): AccessibilityServiceManager {
            if (instance == null) {
                instance = AccessibilityServiceManager()
            }
            return instance!!
        }
    }

    private var accessibilityService: WpsAccessibilityService? = null
    private var floatingButtonService: com.wpspasswordmanager.ui.FloatingButtonService? = null

    fun setService(service: WpsAccessibilityService) {
        this.accessibilityService = service
        Log.d(TAG, "无障碍服务已设置")
    }

    fun setFloatingButtonService(service: com.wpspasswordmanager.ui.FloatingButtonService?) {
        this.floatingButtonService = service
        if (service != null) {
            Log.d(TAG, "悬浮按钮服务已设置")
        } else {
            Log.d(TAG, "悬浮按钮服务引用已清除")
        }
    }

    fun isServiceEnabled(context: Context): Boolean {
        val enabledServices = Settings.Secure.getString(
            context.contentResolver,
            Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES
        )
        val packageName = context.packageName
        return enabledServices?.contains("$packageName/${WpsAccessibilityService::class.java.name}") ?: false
    }

    fun fillPassword(password: String) {
        accessibilityService?.fillPassword(password)
    }

    fun showFloatingButton() {
        if (floatingButtonService != null) {
            floatingButtonService?.showFloatingButton()
        } else {
            Log.d(TAG, "悬浮按钮服务未初始化，尝试启动服务")
            // 尝试启动悬浮按钮服务
            val context = accessibilityService?.applicationContext
            if (context != null) {
                val intent = Intent(context, com.wpspasswordmanager.ui.FloatingButtonService::class.java)
                if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
                    context.startForegroundService(intent)
                } else {
                    context.startService(intent)
                }
                Log.d(TAG, "悬浮按钮服务已启动，等待初始化完成后显示")
            }
        }
    }

    fun hideFloatingButton() {
        if (floatingButtonService != null) {
            floatingButtonService?.hideFloatingButton()
        } else {
            Log.d(TAG, "悬浮按钮服务未初始化，无需隐藏")
        }
    }
}
