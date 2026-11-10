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

    /** 是否已有一次「带 show_if_dialog 启动服务」的请求在途，用于去重（无障碍事件很密集，不去重会连发几十次） */
    @Volatile
    private var showRequestPending = false

    fun setService(service: WpsAccessibilityService) {
        this.accessibilityService = service
        Log.d(TAG, "无障碍服务已设置")
    }

    fun setFloatingButtonService(service: com.wpspasswordmanager.ui.FloatingButtonService?) {
        this.floatingButtonService = service
        if (service != null) {
            // 服务就绪后由它自行处理在途的显示请求
            Log.d(TAG, "悬浮按钮服务已设置")
        } else {
            // 服务停止（隐藏/销毁）后允许下一轮重新发起启动请求
            showRequestPending = false
            Log.d(TAG, "悬浮按钮服务引用已清除")
        }
    }

    /** 服务侧最终放弃显示（如判定已不在密码窗口）时调用，解除去重占位 */
    fun clearPendingShowRequest() {
        showRequestPending = false
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

    /**
     * 悬浮窗兜底：当前是否仍处于【添加密码】/【修改密码】窗口。
     * null 表示无障碍服务不可用或暂时无法判断。
     */
    fun isPasswordDialogOnScreen(): Boolean? = accessibilityService?.isPasswordDialogOnScreen()

    fun showFloatingButton() {
        if (floatingButtonService != null) {
            showRequestPending = false
            com.wpspasswordmanager.utils.LogManager.log(TAG, "showFloatingButton：服务已就绪，直接显示气泡", "DEBUG")
            floatingButtonService?.showFloatingButton()
        } else if (showRequestPending) {
            // 已有一次启动请求在途，服务就绪后会自行判定并显示，这里不再重复 startForegroundService
        } else {
            showRequestPending = true
            com.wpspasswordmanager.utils.LogManager.log(TAG, "showFloatingButton：服务未就绪，带 show_if_dialog 启动服务", "DEBUG")
            Log.d(TAG, "悬浮按钮服务未初始化，尝试启动服务")
            // 尝试启动悬浮按钮服务
            val context = accessibilityService?.applicationContext
            if (context != null) {
                val intent = Intent(context, com.wpspasswordmanager.ui.FloatingButtonService::class.java)
                // 服务刚被拉起时无法立即显示，让服务就绪后自行确认「仍在添加/修改密码窗口」并显示，
                // 否则要等下一个无障碍事件，气泡会延迟数秒才出现
                intent.putExtra("show_if_dialog", true)
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
        }
    }
}
