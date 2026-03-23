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

    fun setFloatingButtonService(service: com.wpspasswordmanager.ui.FloatingButtonService) {
        this.floatingButtonService = service
        Log.d(TAG, "悬浮按钮服务已设置")
    }

    fun getService(): WpsAccessibilityService? {
        return accessibilityService
    }

    fun isServiceEnabled(context: Context): Boolean {
        val enabledServices = Settings.Secure.getString(
            context.contentResolver,
            Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES
        )
        val packageName = context.packageName
        return enabledServices?.contains("$packageName/${WpsAccessibilityService::class.java.name}") ?: false
    }

    fun openAccessibilitySettings(context: Context) {
        val intent = Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)
        context.startActivity(intent)
    }

    fun fillPassword(password: String) {
        accessibilityService?.fillPassword(password)
    }

    fun showFloatingButton() {
        floatingButtonService?.showFloatingButton()
    }

    fun hideFloatingButton() {
        floatingButtonService?.hideFloatingButton()
    }
}
