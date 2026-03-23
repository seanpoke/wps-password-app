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
import com.wpspasswordmanager.business.MemoryPasswordStorage
import com.wpspasswordmanager.business.PasswordStorage

class WpsAccessibilityService : AccessibilityService() {

    companion object {
        private const val TAG = "WpsAccessibilityService"
        private val WPS_PACKAGES = arrayOf("cn.wps.moffice_eng", "cn.wps.moffice")
        private var lastPassword: String? = null
        private var currentDocumentPath: String? = null
        private var stableDocumentPath: String? = null // 稳定的文档路径
        private var isFillingPassword = false // 防止自动填充无限循环的标志
        private var hasClickedShowPassword = false // 防止重复点击显示密码选项的标志
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
            Log.d(TAG, "收到事件: ${event.eventType}, 包名: $packageName, 类名: ${event.className}")
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
                AccessibilityEvent.TYPE_VIEW_CLICKED -> {
                    Log.d(TAG, "收到点击事件: ${event.source?.className}, 文本: ${event.source?.text}")
                    handleViewClicked(event)
                }
                AccessibilityEvent.TYPE_VIEW_TEXT_CHANGED -> {
                    Log.d(TAG, "收到文本变化事件: ${event.source?.className}, 文本: ${event.text}")
                    handleViewTextChanged(event)
                }
                AccessibilityEvent.TYPE_VIEW_SELECTED -> {
                    Log.d(TAG, "收到视图选择事件: ${event.source?.className}")
                }
                AccessibilityEvent.TYPE_VIEW_ACCESSIBILITY_FOCUSED -> {
                    Log.d(TAG, "收到视图无障碍焦点事件: ${event.source?.className}")
                }
            }
        }
    }
    
    private fun handleViewTextChanged(event: AccessibilityEvent) {
        // 处理文本变化事件，保存密码输入框的内容
        // 防止自动填充时的无限循环
        if (isFillingPassword) {
            Log.d(TAG, "正在填充密码中，跳过文本变化处理")
            return
        }
        
        val source = event.source
        if (source != null && isPasswordInput(source)) {
            // 尝试获取实际密码值
            var password = ""
            
            // 方法1: 尝试从 AccessibilityEvent 获取文本
            if (event.text != null && event.text.size > 0) {
                password = event.text.joinToString("")
            }
            
            // 方法2: 尝试从 source node 获取文本
            if (password.isEmpty()) {
                val nodeText = source.text?.toString() ?: ""
                if (!nodeText.isEmpty()) {
                    password = nodeText
                }
            }
            
            if (password.isNotEmpty()) {
                Log.d(TAG, "密码输入框文本变化: '$password'，长度: ${password.length}")
                Log.i(TAG, "明文密码: '$password'")
                // 存储密码到内存
                if (stableDocumentPath != null) {
                    Log.d(TAG, "使用稳定文档路径: $stableDocumentPath")
                    val stored = MemoryPasswordStorage.getInstance().storePasswordInMemory(stableDocumentPath!!, password)
                    if (stored) {
                        Log.i(TAG, "已成功存储用户输入的密码到内存: $stableDocumentPath")
                        Log.i(TAG, "存储的明文密码: '$password'")
                    } else {
                        Log.e(TAG, "存储用户输入的密码到内存失败: $stableDocumentPath")
                    }
                } else {
                    Log.e(TAG, "稳定文档路径为空，无法存储密码")
                }
            }
        }
    }
    
    private fun handleViewClicked(event: AccessibilityEvent) {
        // 处理确认按钮点击事件
        val source = event.source
        if (source != null) {
            Log.d(TAG, "检测到点击事件，源节点类型: ${source.className}")
            Log.d(TAG, "源节点文本: ${source.text}")
            Log.d(TAG, "源节点内容描述: ${source.contentDescription}")
            Log.d(TAG, "源节点ID: ${source.viewIdResourceName}")
            
            // 检查是否是确认按钮
            val isConfirm = isConfirmButton(source)
            Log.d(TAG, "是否是确认按钮: $isConfirm")
            
            if (isConfirm) {
                Log.i(TAG, "用户点击确认按钮，时间: ${System.currentTimeMillis()}")
                showOperationNotification("操作处理", "正在处理确认操作...")
                
                // 当用户点击确认按钮时，直接从内存中获取之前保存的密码
                if (stableDocumentPath != null) {
                    val password = MemoryPasswordStorage.getInstance().getPasswordFromMemory(stableDocumentPath!!)
                    if (password != null && password.isNotEmpty()) {
                        Log.i(TAG, "从内存中获取到密码，长度: ${password.length}")
                        Log.i(TAG, "获取的明文密码: '$password'")
                        showOperationNotification("操作处理", "密码已成功缓存到内存")
                    } else {
                        Log.e(TAG, "内存中未找到密码")
                        showOperationNotification("操作失败", "未找到密码，请重新输入")
                    }
                } else {
                    Log.e(TAG, "稳定文档路径为空，无法获取密码")
                    showOperationNotification("操作失败", "文档路径为空，无法处理")
                }
            } else if (isSaveButton(source)) {
                Log.i(TAG, "用户点击保存按钮，时间: ${System.currentTimeMillis()}")
                showOperationNotification("文件保存", "正在保存文件...")
                // 执行文件保存操作
                val saveSuccess = simulateFileSave()
                
                // 只有文件保存成功后，才执行密码写入元数据操作
                if (saveSuccess) {
                    Log.i(TAG, "文件保存成功，开始执行密码写入元数据操作")
                    showOperationNotification("密码存储", "正在存储密码到元数据...")
                    writePasswordToMetadata()
                    showOperationNotification("操作完成", "密码已成功存储到元数据")
                } else {
                    Log.e(TAG, "文件保存失败，取消密码写入元数据操作")
                    showOperationNotification("操作失败", "文件保存失败，请重试")
                }
            } else {
                Log.d(TAG, "点击的不是确认按钮或保存按钮")
            }
        } else {
            Log.d(TAG, "点击事件的源节点为null")
        }
    }
    
    private fun simulateFileSave(): Boolean {
        Log.i(TAG, "开始执行文件保存操作，时间: ${System.currentTimeMillis()}")
        val startTime = System.currentTimeMillis()
        try {
            // 模拟文件保存操作
            Thread.sleep(500) // 模拟保存耗时
            val endTime = System.currentTimeMillis()
            val duration = endTime - startTime
            Log.i(TAG, "文件保存操作成功，耗时: ${duration}ms，时间: $endTime")
            return true
        } catch (e: Exception) {
            Log.e(TAG, "文件保存操作失败", e)
            return false
        }
    }

    override fun onInterrupt() {
        Log.d(TAG, "无障碍服务被中断")
    }
    
    /**
     * 显示操作通知
     */
    private fun showOperationNotification(title: String, content: String) {
        try {
            // 只记录日志，不启动悬浮按钮服务
            // 避免在不需要时启动服务导致按钮显示
            Log.d(TAG, "操作通知: $title - $content")
        } catch (e: Exception) {
            Log.e(TAG, "显示操作通知失败", e)
        }
    }

    private fun handleWindowStateChanged(event: AccessibilityEvent) {
        Log.d(TAG, "窗口状态改变: ${event.className}")
        val rootNode = rootInActiveWindow ?: return
        detectPasswordDialog(rootNode)
        detectDocumentPath(rootNode)
    }

    private fun handleWindowContentChanged(event: AccessibilityEvent) {
        Log.d(TAG, "窗口内容改变: ${event.className}")
        val rootNode = rootInActiveWindow ?: return
        detectPasswordDialog(rootNode)
        detectDocumentPath(rootNode)
    }

    private fun handleViewFocused(event: AccessibilityEvent) {
        Log.d(TAG, "视图获得焦点: ${event.className}")
        val rootNode = rootInActiveWindow ?: return
        detectPasswordDialog(rootNode)
        detectDocumentPath(rootNode)
    }

    private fun detectPasswordDialog(rootNode: AccessibilityNodeInfo) {
        // 查找密码输入框
        val passwordInputNodes = findPasswordInputNodes(rootNode)
        // 查找确认按钮
        val confirmButton = findConfirmButton(rootNode)
        
        // 只有当同时找到密码输入框和确认按钮时，才认为是添加密码或修改密码的弹框
        val isPasswordDialog = passwordInputNodes.isNotEmpty() && confirmButton != null
        
        if (isPasswordDialog) {
            Log.d(TAG, "找到密码输入框: ${passwordInputNodes.size}，找到确认按钮: ${confirmButton != null}，判断为密码弹框")
            
            // 尝试找到并点击【显示密码】选项
            findAndClickShowPasswordOption(rootNode)
            
            // 启动悬浮按钮服务
            startFloatingButtonService()
            // 显示悬浮按钮
            AccessibilityServiceManager.getInstance().showFloatingButton()
            
            // 尝试自动填充密码
            autoFillPassword(rootNode)
        } else {
            // 隐藏悬浮按钮
            AccessibilityServiceManager.getInstance().hideFloatingButton()
            stopFloatingButtonService()
            // 当没有找到密码输入框时，说明文档可能已经关闭
            // 先将内存中的密码写入到安全存储
            writePasswordToMetadata()
            // 检查是否是文档进程完全关闭的情况
            // 通过判断是否还能检测到文档路径来确定
            val currentPath = findDocumentPathFromNodes(rootNode)
            if (currentPath == null && stableDocumentPath != null) {
                // 文档进程完全关闭，从内存中彻底清除密码
                Log.i(TAG, "文档进程完全关闭，从内存中彻底清除密码: $stableDocumentPath")
                val removed = MemoryPasswordStorage.getInstance().removePasswordFromMemory(stableDocumentPath!!)
                if (removed) {
                    Log.i(TAG, "已从内存中彻底清除密码: $stableDocumentPath")
                } else {
                    Log.e(TAG, "从内存中清除密码失败: $stableDocumentPath")
                }
                // 重置稳定文档路径和标志
                stableDocumentPath = null
                currentDocumentPath = null
                hasClickedShowPassword = false // 重置显示密码点击标志
                Log.i(TAG, "文档进程生命周期结束，已重置文档路径和显示密码标志")
            } else {
                // 文档进程仍在运行，保留密码在内存中
                Log.d(TAG, "文档进程仍在运行，密码继续保留在内存中")
            }
        }

        // 查找保存按钮
        val saveButton = findSaveButton(rootNode)
        if (saveButton != null) {
            Log.d(TAG, "找到保存按钮")
        }
    }
    
    /**
     * 查找并点击【显示密码】选项
     */
    private fun findAndClickShowPasswordOption(rootNode: AccessibilityNodeInfo) {
        // 如果已经点击过【显示密码】选项，不再重复点击
        if (hasClickedShowPassword) {
            Log.d(TAG, "已经点击过【显示密码】选项，不再重复点击")
            return
        }
        
        try {
            val queue = mutableListOf(rootNode)
            
            while (queue.isNotEmpty()) {
                val node = queue.removeAt(0)
                
                // 检查是否是【显示密码】选项的容器
                val text = node.text?.toString() ?: ""
                val contentDescription = node.contentDescription?.toString() ?: ""
                
                val hasShowPasswordText = text.contains("显示密码") || text.contains("show password") ||
                                         text.contains("Show Password") || contentDescription.contains("显示密码") ||
                                         contentDescription.contains("show password") || contentDescription.contains("Show Password")
                
                // 检查是否是复选框或开关
                val isCheckboxOrSwitch = node.className?.toString()?.contains("CheckBox") ?: false ||
                                        node.className?.toString()?.contains("Switch") ?: false ||
                                        node.className?.toString()?.contains("Toggle") ?: false
                
                if (hasShowPasswordText || isCheckboxOrSwitch) {
                    Log.d(TAG, "找到可能的【显示密码】选项: $text")
                    Log.d(TAG, "选项类名: ${node.className}")
                    Log.d(TAG, "是否可点击: ${node.isClickable}")
                    Log.d(TAG, "是否可聚焦: ${node.isFocusable}")
                    
                    // 尝试直接点击
                    if (node.isClickable) {
                        val success = node.performAction(AccessibilityNodeInfo.ACTION_CLICK)
                        if (success) {
                            Log.i(TAG, "成功点击【显示密码】选项")
                            hasClickedShowPassword = true
                            return
                        } else {
                            Log.e(TAG, "直接点击【显示密码】选项失败")
                        }
                    }
                    
                    // 尝试点击子节点
                    for (i in 0 until node.childCount) {
                        val child = node.getChild(i)
                        if (child != null && child.isClickable) {
                            Log.d(TAG, "尝试点击子节点: ${child.className}")
                            val success = child.performAction(AccessibilityNodeInfo.ACTION_CLICK)
                            if (success) {
                                Log.i(TAG, "成功点击【显示密码】选项的子节点")
                                hasClickedShowPassword = true
                                return
                            } else {
                                Log.e(TAG, "点击【显示密码】选项的子节点失败")
                            }
                        }
                    }
                    
                    // 尝试点击父节点
                    val parent = node.parent
                    if (parent != null && parent.isClickable) {
                        Log.d(TAG, "尝试点击父节点: ${parent.className}")
                        val success = parent.performAction(AccessibilityNodeInfo.ACTION_CLICK)
                        if (success) {
                            Log.i(TAG, "成功点击【显示密码】选项的父节点")
                            hasClickedShowPassword = true
                            return
                        } else {
                            Log.e(TAG, "点击【显示密码】选项的父节点失败")
                        }
                    }
                }
                
                // 遍历子节点
                for (i in 0 until node.childCount) {
                    val child = node.getChild(i)
                    if (child != null) {
                        queue.add(child)
                    }
                }
            }
            
            Log.d(TAG, "未找到【显示密码】选项")
        } catch (e: Exception) {
            Log.e(TAG, "查找并点击【显示密码】选项失败", e)
        }
    }
    
    private fun findSaveButton(rootNode: AccessibilityNodeInfo): AccessibilityNodeInfo? {
        val queue = mutableListOf(rootNode)

        while (queue.isNotEmpty()) {
            val node = queue.removeAt(0)

            // 检查是否是保存按钮
            if (isSaveButton(node)) {
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
    
    private fun autoFillPassword(rootNode: AccessibilityNodeInfo) {
        try {
            // 防止无限循环填充
            if (isFillingPassword) {
                Log.d(TAG, "正在填充密码中，跳过自动填充")
                return
            }
            
            // 检测文档路径
            detectDocumentPath(rootNode)
            
            // 不再自动填充密码，只在用户点击悬浮按钮时填充
            // 这样可以避免强制填充密码的行为
            Log.d(TAG, "跳过自动填充密码，等待用户手动操作")
        } catch (e: Exception) {
            Log.e(TAG, "自动填充密码失败", e)
            isFillingPassword = false
        }
    }

    private fun detectDocumentPath(rootNode: AccessibilityNodeInfo) {
        // 尝试从窗口标题或其他元素中提取文档路径
        // 这里可以根据实际WPS界面结构进行调整
        var detectedPath: String? = null
        
        // 方法1: 从根节点获取文本
        val windowTitle = rootNode.text?.toString() ?: ""
        if (windowTitle.isNotEmpty()) {
            detectedPath = windowTitle
            Log.d(TAG, "从根节点检测到文档路径: $detectedPath")
        }
        
        // 方法2: 遍历所有节点，寻找可能的文档路径
        if (detectedPath == null) {
            detectedPath = findDocumentPathFromNodes(rootNode)
            if (detectedPath != null) {
                Log.d(TAG, "从子节点检测到文档路径: $detectedPath")
            }
        }
        
        // 方法3: 从包名和类名中推断
        if (detectedPath == null) {
            val className = rootNode.className?.toString() ?: ""
            if (className.isNotEmpty()) {
                detectedPath = "wps_" + className.hashCode() + "_" + System.currentTimeMillis()
                Log.d(TAG, "从类名推断文档路径: $detectedPath")
            }
        }
        
        // 方法4: 使用时间戳作为临时标识符
        if (detectedPath == null) {
            detectedPath = "temp_" + System.currentTimeMillis()
            Log.d(TAG, "使用临时标识符作为文档路径: $detectedPath")
        }
        
        // 保持文档路径稳定，只在第一次设置或检测到新的有效路径时更新
        if (stableDocumentPath == null) {
            stableDocumentPath = detectedPath
            Log.d(TAG, "设置稳定文档路径: $stableDocumentPath")
        }
        
        currentDocumentPath = stableDocumentPath
        Log.d(TAG, "最终确定的文档路径: $currentDocumentPath")
    }
    
    private fun findDocumentPathFromNodes(node: AccessibilityNodeInfo): String? {
        val queue = mutableListOf(node)
        
        while (queue.isNotEmpty()) {
            val currentNode = queue.removeAt(0)
            
            // 检查节点文本是否可能是文档路径
            val text = currentNode.text?.toString() ?: ""
            if (text.isNotEmpty() && (text.contains(".doc") || text.contains(".docx") || text.contains(".xls") || text.contains(".xlsx") || text.contains(".ppt") || text.contains(".pptx"))) {
                return text
            }
            
            // 检查节点内容描述
            val contentDescription = currentNode.contentDescription?.toString() ?: ""
            if (contentDescription.isNotEmpty() && (contentDescription.contains(".doc") || contentDescription.contains(".docx") || contentDescription.contains(".xls") || contentDescription.contains(".xlsx") || contentDescription.contains(".ppt") || contentDescription.contains(".pptx"))) {
                return contentDescription
            }
            
            // 遍历子节点
            for (i in 0 until currentNode.childCount) {
                val child = currentNode.getChild(i)
                if (child != null) {
                    queue.add(child)
                }
            }
        }
        
        return null
    }

    private fun startFloatingButtonService() {
        try {
            // 检查是否真的需要启动服务
            // 只有在检测到密码弹框时才启动
            val rootNode = rootInActiveWindow ?: return
            val passwordInputNodes = findPasswordInputNodes(rootNode)
            val confirmButton = findConfirmButton(rootNode)
            val isPasswordDialog = passwordInputNodes.isNotEmpty() && confirmButton != null
            
            if (isPasswordDialog) {
                val intent = Intent(this, com.wpspasswordmanager.ui.FloatingButtonService::class.java)
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    startForegroundService(intent)
                } else {
                    startService(intent)
                }
                Log.d(TAG, "启动悬浮按钮服务")
            } else {
                Log.d(TAG, "不是密码弹框，不启动悬浮按钮服务")
            }
        } catch (e: Exception) {
            Log.e(TAG, "启动悬浮按钮服务失败", e)
        }
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
        val className = node.className?.toString() ?: ""
        val inputType = node.inputType
        val text = node.text?.toString() ?: ""
        val contentDescription = node.contentDescription?.toString() ?: ""
        
        // 更灵活地识别输入框
        val isInput = className.contains("EditText") || className.contains("Input") || 
                     className.contains("Text") || className.contains("editText") ||
                     className.contains("input") || className.contains("text") ||
                     className.contains("android.widget.EditText") || className.contains("androidx.appcompat.widget.AppCompatEditText") ||
                     className.contains("View") || className.contains("view") || // 增加对View类的支持，因为有些输入框可能使用View实现
                     className.contains("TextView") || className.contains("textView") // 增加对TextView类的支持，因为有些输入框可能使用TextView实现
        
        // 检查输入类型是否为密码
        val isPasswordType = inputType and InputType.TYPE_TEXT_VARIATION_PASSWORD != 0 ||
                            inputType and InputType.TYPE_TEXT_VARIATION_VISIBLE_PASSWORD != 0 ||
                            inputType and InputType.TYPE_TEXT_VARIATION_WEB_PASSWORD != 0
        
        // 检查节点文本或内容描述是否包含密码相关词汇
        val hasPasswordText = text.contains("密码") || text.contains("password") ||
                            text.contains("PASSWORD") || text.contains("Pass") || text.contains("pass") ||
                            contentDescription.contains("密码") || contentDescription.contains("password") ||
                            contentDescription.contains("PASSWORD") || contentDescription.contains("Pass") || contentDescription.contains("pass") ||
                            contentDescription.contains("输入密码") || contentDescription.contains("enter password") ||
                            contentDescription.contains("确认密码") || contentDescription.contains("confirm password") ||
                            contentDescription.contains("OPEN PERMISSION") || contentDescription.contains("Open Permission") ||
                            contentDescription.contains("打开权限") || contentDescription.contains("open permission") ||
                            contentDescription.contains("修改权限") || contentDescription.contains("modify permission")
        
        // 检查是否是可编辑的输入框
        val isEditable = node.isEditable
        
        // 满足以下条件之一即为密码输入框
        return (isInput && (isPasswordType || hasPasswordText) && isEditable)
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
        val className = node.className?.toString() ?: ""
        val text = node.text?.toString() ?: ""
        val contentDescription = node.contentDescription?.toString() ?: ""
        
        // 更灵活地识别按钮，不只是检查类名是否包含Button
        val isButton = className.contains("Button") || className.contains("button") || 
                      className.contains("android.widget.Button") || className.contains("androidx.appcompat.widget.AppCompatButton") ||
                      className.contains("View") || className.contains("view") || // 增加对View类的支持，因为有些按钮可能使用View实现
                      className.contains("TextView") || className.contains("textView") // 增加对TextView类的支持，因为有些按钮可能使用TextView实现
        
        // 检查文本或内容描述是否包含确认相关词汇
        val hasConfirmText = text.contains("确定") || text.contains("确认") || 
                            text.contains("OK") || text.contains("Confirm") ||
                            text.contains("ok") || text.contains("confirm") ||
                            contentDescription.contains("确定") || contentDescription.contains("确认") ||
                            contentDescription.contains("OK") || contentDescription.contains("Confirm") ||
                            contentDescription.contains("ok") || contentDescription.contains("confirm")
        
        return isButton && hasConfirmText
    }
    
    private fun isSaveButton(node: AccessibilityNodeInfo): Boolean {
        // 检查节点是否是按钮且文本包含保存相关内容
        if (node.className.toString().contains("Button")) {
            val text = node.text?.toString() ?: ""
            return text.contains("保存") || text.contains("Save")
        }
        return false
    }

    fun fillPassword(password: String) {
        try {
            // 防止无限循环填充
            if (isFillingPassword) {
                Log.d(TAG, "正在填充密码中，跳过填充操作")
                showOperationNotification("密码填充", "正在填充密码中，请稍候")
                return
            }
            
            val rootNode = rootInActiveWindow ?: run {
                Log.e(TAG, "根节点为空，无法执行密码填充")
                showOperationNotification("密码填充", "操作失败：无法访问界面元素")
                return
            }
            val passwordInputNodes = findPasswordInputNodes(rootNode)

            Log.i(TAG, "开始执行密码填充操作，时间: ${System.currentTimeMillis()}")
            Log.i(TAG, "填充的明文密码: '$password'")
            showOperationNotification("密码填充", "正在填充密码...")
            
            if (passwordInputNodes.isNotEmpty()) {
                var fillSuccess = false
                isFillingPassword = true
                try {
                    for (node in passwordInputNodes) {
                        try {
                            // 填充密码
                            val arguments = android.os.Bundle()
                            arguments.putCharSequence(AccessibilityNodeInfo.ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE, password)
                            val success = node.performAction(AccessibilityNodeInfo.ACTION_SET_TEXT, arguments)
                            if (success) {
                                Log.i(TAG, "密码填充成功")
                                Log.i(TAG, "明文密码填充成功: '$password'")
                                fillSuccess = true
                            } else {
                                Log.e(TAG, "密码填充失败")
                            }
                        } catch (e: Exception) {
                            Log.e(TAG, "填充密码时发生异常", e)
                        }
                    }
                    
                    if (fillSuccess) {
                        showOperationNotification("密码填充", "密码填充成功，弹窗保持打开状态")
                        // 存储密码到内存
                        lastPassword = password
                        if (stableDocumentPath != null) {
                            val stored = MemoryPasswordStorage.getInstance().storePasswordInMemory(stableDocumentPath!!, password)
                            if (stored) {
                                Log.i(TAG, "已存储密码到内存: $stableDocumentPath")
                                Log.i(TAG, "存储的明文密码: '$password'")
                            } else {
                                Log.e(TAG, "存储密码到内存失败: $stableDocumentPath")
                                showOperationNotification("密码存储", "密码已填充但未保存到内存，请手动确认")
                            }
                        } else {
                            Log.e(TAG, "稳定文档路径为空，无法存储密码到内存")
                            showOperationNotification("密码存储", "密码已填充但未保存到内存，请手动确认")
                        }
                    } else {
                        Log.e(TAG, "所有密码输入框填充失败")
                        showOperationNotification("密码填充", "密码填充失败，请重试")
                    }
                    
                    // 确保弹窗保持打开状态，不自动点击确认按钮
                    Log.i(TAG, "密码填充完成，弹窗保持打开状态，等待用户手动点击确认按钮")
                } finally {
                    isFillingPassword = false
                }
            } else {
                Log.e(TAG, "未找到密码输入框")
                showOperationNotification("密码填充", "未找到密码输入框，请检查界面")
            }
        } catch (e: Exception) {
            Log.e(TAG, "密码填充操作发生异常", e)
            showOperationNotification("密码填充", "操作失败，请重试")
            isFillingPassword = false
        } finally {
            Log.i(TAG, "密码填充操作完成，时间: ${System.currentTimeMillis()}")
        }
    }

    private fun writePasswordToMetadata() {
        try {
            Log.d(TAG, "开始执行密码写入元数据操作，时间: ${System.currentTimeMillis()}")
            // 从内存中获取密码
            if (stableDocumentPath != null) {
                val password = MemoryPasswordStorage.getInstance().getPasswordFromMemory(stableDocumentPath!!)
                if (password != null) {
                    Log.d(TAG, "获取到密码，开始写入元数据，文件路径: $stableDocumentPath")
                    Log.i(TAG, "写入的明文密码: '$password'")
                    try {
                        // 写入到安全存储
                        val success = PasswordStorage.getInstance().storePassword(this, stableDocumentPath!!, password)
                        if (success) {
                            Log.d(TAG, "密码已成功写入到安全存储: $stableDocumentPath，操作完成时间: ${System.currentTimeMillis()}")
                            Log.i(TAG, "明文密码已成功写入到安全存储: '$password'")
                        } else {
                            Log.e(TAG, "密码写入安全存储失败: $stableDocumentPath，操作完成时间: ${System.currentTimeMillis()}")
                            showOperationNotification("密码存储", "密码写入元数据失败，请重试")
                        }
                    } catch (e: Exception) {
                        Log.e(TAG, "密码写入安全存储时发生异常", e)
                        showOperationNotification("密码存储", "密码写入元数据时发生错误，请重试")
                    }
                    
                    // 不再从内存中移除密码，实现文档进程生命周期绑定
                    // 只有当文档进程完全关闭时，才从内存中彻底清除密码
                    Log.d(TAG, "密码已写入安全存储，但仍保留在内存中（文档进程生命周期绑定）: $stableDocumentPath")
                } else {
                    Log.d(TAG, "内存中没有找到密码: $stableDocumentPath")
                    showOperationNotification("密码存储", "内存中没有找到密码")
                }
            } else {
                Log.d(TAG, "稳定文档路径为空，无法写入密码")
                showOperationNotification("密码存储", "文档路径为空，无法写入密码")
            }
        } catch (e: Exception) {
            Log.e(TAG, "写入密码到元数据失败，文件路径: $stableDocumentPath", e)
            showOperationNotification("密码存储", "操作失败，请重试")
        } finally {
            Log.d(TAG, "密码写入元数据操作完成，时间: ${System.currentTimeMillis()}")
        }
    }
}
