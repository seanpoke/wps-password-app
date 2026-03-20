package com.wpspasswordmanager.monitor

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.AccessibilityServiceInfo
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.text.InputType
import android.util.Log
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo

class WpsAccessibilityService : AccessibilityService() {

    companion object {
        private const val TAG = "WpsAccessibilityService"
        private val WPS_PACKAGES = arrayOf("cn.wps.moffice_eng", "cn.wps.moffice")
    }

    override fun onServiceConnected() {
        super.onServiceConnected()
        Log.d(TAG, "无障碍服务已连接")

        // 注册服务到管理器
        AccessibilityServiceManager.getInstance().setService(this)

        val info = AccessibilityServiceInfo().apply {
            eventTypes = AccessibilityEvent.TYPES_ALL_MASK
            feedbackType = AccessibilityServiceInfo.FEEDBACK_GENERIC
            flags = AccessibilityServiceInfo.FLAG_INCLUDE_NOT_IMPORTANT_VIEWS or
                    AccessibilityServiceInfo.FLAG_REQUEST_ENHANCED_WEB_ACCESSIBILITY or
                    AccessibilityServiceInfo.FLAG_REPORT_VIEW_IDS
            packageNames = WPS_PACKAGES
            notificationTimeout = 100
        }

        serviceInfo = info
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent) {
        val packageName = event.packageName?.toString()
        if (packageName in WPS_PACKAGES) {
            when (event.eventType) {
                AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED -> {
                    handleWindowStateChanged(event)
                }
                AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED -> {
                    handleWindowContentChanged(event)
                }
                AccessibilityEvent.TYPE_VIEW_FOCUSED -> {
                    handleViewFocused(event)
                }
            }
        }
    }

    override fun onInterrupt() {
        Log.d(TAG, "无障碍服务被中断")
    }

    private fun handleWindowStateChanged(event: AccessibilityEvent) {
        Log.d(TAG, "窗口状态改变: ${event.className}")
        val rootNode = rootInActiveWindow ?: return
        detectPasswordDialog(rootNode)
    }

    private fun handleWindowContentChanged(event: AccessibilityEvent) {
        Log.d(TAG, "窗口内容改变: ${event.className}")
        val rootNode = rootInActiveWindow ?: return
        detectPasswordDialog(rootNode)
    }

    private fun handleViewFocused(event: AccessibilityEvent) {
        Log.d(TAG, "视图获得焦点: ${event.className}")
        val rootNode = rootInActiveWindow ?: return
        detectPasswordDialog(rootNode)
    }

    private fun detectPasswordDialog(rootNode: AccessibilityNodeInfo) {
        // 查找密码输入框
        val passwordInputNodes = findPasswordInputNodes(rootNode)
        if (passwordInputNodes.isNotEmpty()) {
            Log.d(TAG, "找到密码输入框: ${passwordInputNodes.size}")
            // 启动悬浮按钮服务
            startFloatingButtonService()
        } else {
            // 隐藏悬浮按钮
            stopFloatingButtonService()
        }

        // 查找确认按钮
        val confirmButton = findConfirmButton(rootNode)
        if (confirmButton != null) {
            Log.d(TAG, "找到确认按钮")
        }
    }

    private fun startFloatingButtonService() {
        val intent = Intent(this, com.wpspasswordmanager.ui.FloatingButtonService::class.java)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            startForegroundService(intent)
        } else {
            startService(intent)
        }
        Log.d(TAG, "启动悬浮按钮服务")
    }

    private fun stopFloatingButtonService() {
        val intent = Intent(this, com.wpspasswordmanager.ui.FloatingButtonService::class.java)
        stopService(intent)
        Log.d(TAG, "停止悬浮按钮服务")
    }

    private fun findPasswordInputNodes(rootNode: AccessibilityNodeInfo): List<AccessibilityNodeInfo> {
        val result = mutableListOf<AccessibilityNodeInfo>()
        val queue = mutableListOf(rootNode)

        while (queue.isNotEmpty()) {
            val node = queue.removeAt(0)

            // 检查是否是密码输入框
            if (isPasswordInput(node)) {
                result.add(node)
            }

            // 遍历子节点
            for (i in 0 until node.childCount) {
                val child = node.getChild(i)
                if (child != null) {
                    queue.add(child)
                }
            }
        }

        return result
    }

    private fun isPasswordInput(node: AccessibilityNodeInfo): Boolean {
        // 检查节点是否是输入框且输入类型为密码
        if (node.className.toString().contains("EditText")) {
            val inputType = node.inputType
            return inputType and InputType.TYPE_TEXT_VARIATION_PASSWORD != 0 ||
                    inputType and InputType.TYPE_TEXT_VARIATION_VISIBLE_PASSWORD != 0 ||
                    inputType and InputType.TYPE_TEXT_VARIATION_WEB_PASSWORD != 0
        }
        return false
    }

    private fun findConfirmButton(rootNode: AccessibilityNodeInfo): AccessibilityNodeInfo? {
        val queue = mutableListOf(rootNode)

        while (queue.isNotEmpty()) {
            val node = queue.removeAt(0)

            // 检查是否是确认按钮
            if (isConfirmButton(node)) {
                return node
            }

            // 遍历子节点
            for (i in 0 until node.childCount) {
                val child = node.getChild(i)
                if (child != null) {
                    queue.add(child)
                }
            }
        }

        return null
    }

    private fun isConfirmButton(node: AccessibilityNodeInfo): Boolean {
        // 检查节点是否是按钮且文本包含确认相关内容
        if (node.className.toString().contains("Button")) {
            val text = node.text?.toString() ?: ""
            return text.contains("确定") || text.contains("确认") || text.contains("OK") || text.contains("Confirm")
        }
        return false
    }

    fun fillPassword(password: String) {
        val rootNode = rootInActiveWindow ?: return
        val passwordInputNodes = findPasswordInputNodes(rootNode)

        for (node in passwordInputNodes) {
            // 填充密码
            val arguments = android.os.Bundle()
            arguments.putCharSequence(AccessibilityNodeInfo.ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE, password)
            node.performAction(AccessibilityNodeInfo.ACTION_SET_TEXT, arguments)
            Log.d(TAG, "已填充密码到输入框")

            // 查找并点击确认按钮
            val confirmButton = findConfirmButton(rootNode)
            if (confirmButton != null) {
                confirmButton.performAction(AccessibilityNodeInfo.ACTION_CLICK)
                Log.d(TAG, "已点击确认按钮")
            }
        }
    }
}
