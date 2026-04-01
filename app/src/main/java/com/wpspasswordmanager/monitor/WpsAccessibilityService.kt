package com.wpspasswordmanager.monitor

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.AccessibilityServiceInfo
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.text.InputType
import android.util.Log
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import com.wpspasswordmanager.business.MemoryPasswordStorage
import com.wpspasswordmanager.business.PasswordStorage
import com.wpspasswordmanager.monitor.FileSystemEventListener

class WpsAccessibilityService : AccessibilityService() {

    companion object {
        private const val TAG = "WpsAccessibilityService"
        private val WPS_PACKAGES = arrayOf("cn.wps.moffice_eng", "cn.wps.moffice")
        private var lastPassword: String? = null
        var currentDocumentPath: String? = null
        var stableDocumentPath: String? = null // 稳定的文档路径
        var currentFileUri: String? = null // 当前文件的URI
        private var isFillingPassword = false // 防止自动填充无限循环的标志
        private var hasClickedShowPassword = false // 防止重复点击显示密码选项的标志
        private var hasClickedGeneratePassword = false // 标记是否点击了生成密码按钮
        private var isFloatingButtonServiceStarted = false // 标记悬浮按钮服务是否已启动
        private var isDocumentOpened = false // 标记文档是否已经成功打开
    }
    
    // 文件系统事件监听器实例
    private var fileSystemEventListener: FileSystemEventListener? = null

    override fun onServiceConnected() {
        super.onServiceConnected()
        Log.d(TAG, "无障碍服务已连接")

        // 初始化 MemoryPasswordStorage
        MemoryPasswordStorage.init(this)

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

    /**
     * 保存当前文件URI
     */
    fun saveCurrentFileUri(uri: String) {
        try {
            currentFileUri = uri
            stableDocumentPath = uri
            currentDocumentPath = uri
            Log.d(TAG, "保存文件URI: $uri")
        } catch (e: Exception) {
            Log.e(TAG, "保存文件URI失败", e)
        }
    }

    /**
     * 清除当前文件URI
     */
    fun clearCurrentFileUri() {
        try {
            currentFileUri = null
            Log.d(TAG, "已清除文件URI")
        } catch (e: Exception) {
            Log.e(TAG, "清除文件URI失败", e)
        }
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent) {
        try {
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
        } catch (e: Exception) {
            Log.e(TAG, "处理无障碍事件失败", e)
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
            
            // 方法3: 尝试从 source node 的 content description 获取文本
            if (password.isEmpty()) {
                val contentDescription = source.contentDescription?.toString() ?: ""
                if (!contentDescription.isEmpty()) {
                    password = contentDescription
                }
            }
            
            if (password.isNotEmpty()) {
                Log.d(TAG, "密码输入框文本变化: '$password'，长度: ${password.length}")
                // 存储密码到内存
                if (currentFileUri != null) {
                    Log.d(TAG, "使用文件URI: $currentFileUri")
                    val stored = MemoryPasswordStorage.getInstance().storePasswordInMemory(currentFileUri!!, password, currentFileUri)
                    if (stored) {
                        Log.i(TAG, "已成功存储用户输入的密码到内存: $currentFileUri")
                    } else {
                        Log.e(TAG, "存储用户输入的密码到内存失败: $currentFileUri")
                    }
                } else if (stableDocumentPath != null) {
                    Log.d(TAG, "使用稳定文档路径: $stableDocumentPath")
                    val stored = MemoryPasswordStorage.getInstance().storePasswordInMemory(stableDocumentPath!!, password)
                    if (stored) {
                        Log.i(TAG, "已成功存储用户输入的密码到内存: $stableDocumentPath")
                    } else {
                        Log.e(TAG, "存储用户输入的密码到内存失败: $stableDocumentPath")
                    }
                } else {
                    // 即使稳定文档路径为空，也先存储密码到内存，等文档路径确定后再处理
                    Log.e(TAG, "稳定文档路径为空，先存储密码到临时存储")
                    val stored = MemoryPasswordStorage.getInstance().storePasswordInMemory("temp", password)
                    if (stored) {
                        Log.i(TAG, "已成功存储用户输入的密码到临时内存")
                    } else {
                        Log.e(TAG, "存储用户输入的密码到临时内存失败")
                    }
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
            
            // 检查是否是生成密码按钮
            val isGeneratePassword = isGeneratePasswordButton(source)
            if (isGeneratePassword) {
                Log.i(TAG, "用户点击生成密码按钮，时间: ${System.currentTimeMillis()}")
                hasClickedGeneratePassword = true
                showOperationNotification("操作处理", "正在生成密码...")
                return
            }
            
            // 检查是否是确认按钮
            val isConfirm = isConfirmButton(source)
            Log.d(TAG, "是否是确认按钮: $isConfirm")
            
            if (isConfirm) {
                Log.i(TAG, "用户点击确认按钮，时间: ${System.currentTimeMillis()}")
                showOperationNotification("操作处理", "正在处理确认操作...")
                
                // 尝试获取密码，先从文件URI，再从稳定文档路径，最后从临时存储
                var password: String? = null
                if (currentFileUri != null) {
                    password = MemoryPasswordStorage.getInstance().getPasswordFromMemory(currentFileUri!!)
                }
                if (password == null && stableDocumentPath != null) {
                    password = MemoryPasswordStorage.getInstance().getPasswordFromMemory(stableDocumentPath!!)
                }
                if (password == null) {
                    // 尝试从临时存储获取密码
                    password = MemoryPasswordStorage.getInstance().getPasswordFromMemory("temp")
                }
                
                if (password != null && password.isNotEmpty()) {
                    Log.i(TAG, "从内存中获取到密码，长度: ${password.length}")
                    showOperationNotification("操作处理", "密码已成功缓存到内存")
                    
                    // 启动文件系统事件监听器，由FileObserver处理密码写入
                    startFileSystemEventListener(password)
                } else {
                    Log.e(TAG, "内存中未找到密码")
                    showOperationNotification("操作失败", "未找到密码，请重新输入")
                }
            } else {
                Log.d(TAG, "点击的不是确认按钮")
            }
        } else {
            Log.d(TAG, "点击事件的源节点为null")
        }
    }
    
    /**
     * 检查是否是生成密码按钮
     */
    private fun isGeneratePasswordButton(node: AccessibilityNodeInfo): Boolean {
        val text = node.text?.toString() ?: ""
        val contentDescription = node.contentDescription?.toString() ?: ""
        
        return text.contains("生成密码") || text.contains("generate password") || 
               text.contains("Generate Password") || contentDescription.contains("生成密码") ||
               contentDescription.contains("generate password") || contentDescription.contains("Generate Password")
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
            Log.d(TAG, "操作通知: $title - $content")
            // 使用通知管理器显示通知
            com.wpspasswordmanager.ui.AppNotificationManager.getInstance(this).showOperationNotification(title, content)
        } catch (e: Exception) {
            Log.e(TAG, "显示操作通知失败", e)
        }
    }

    private fun handleWindowStateChanged(event: AccessibilityEvent) {
        Log.d(TAG, "窗口状态改变: ${event.className}")
        val rootNode = rootInActiveWindow ?: return
        detectPasswordDialog(rootNode)
        detectDocumentPath(rootNode)
        
        // 检测是否回退到文件列表页
        val className = event.className?.toString() ?: ""
        Log.d(TAG, "当前窗口类名: $className")
        
        // 增强检测：多种可能的文件列表页类名
        val isFileListScreen = className.contains("HomeRootActivity") || 
                               className.contains("FileManagerActivity") ||
                               className.contains("DocumentListActivity") ||
                               className.contains("MainActivity")
        
        if (isFileListScreen) {
            Log.d(TAG, "检测到回退到文件列表页，尝试写入密码")
            // 重置状态
            isDocumentOpened = false
            hasClickedShowPassword = false
            
            // 停止文件系统事件监听器
            Log.d(TAG, "停止文件系统事件监听器")
            fileSystemEventListener?.stopListening()
            fileSystemEventListener = null
            
            // 清理相关缓存
            clearCurrentFileUri()
            stableDocumentPath = null
            currentDocumentPath = null
            // 清理内存中的密码
            MemoryPasswordStorage.getInstance().removePasswordFromMemory("temp")
            Log.d(TAG, "已清理密码相关缓存")
        }
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
            // 当没有找到密码输入框时，不立即认为文档进程完全关闭
            // 只有在确定文档进程真正关闭时才清除密码和重置文档路径
            // 避免在打开【密码加密】等其他窗口时误判
            Log.d(TAG, "未检测到密码输入框，保持当前状态")
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
            // 如果文档已经打开，不再尝试填充
            if (isDocumentOpened) {
                Log.d(TAG, "文档已经打开，跳过自动填充")
                return
            }
            
            // 防止无限循环填充
            if (isFillingPassword) {
                Log.d(TAG, "正在填充密码中，跳过自动填充")
                return
            }
            
            // 检测文档路径（减少日志输出）
            detectDocumentPath(rootNode, false)
            
            // 尝试从PasswordHolder中获取密码
            var password: String? = null
            
            // 优先从PasswordHolder中获取密码
            if (com.wpspasswordmanager.business.PasswordHolder.hasCachedPassword()) {
                Log.d(TAG, "尝试从PasswordHolder读取密码")
                password = com.wpspasswordmanager.business.PasswordHolder.cachedPassword
                if (password != null) {
                    Log.i(TAG, "从PasswordHolder读取密码成功")
                }
            }
            
            // 如果PasswordHolder中没有找到密码，尝试从内存中获取
            if (password == null && currentFileUri != null) {
                Log.d(TAG, "尝试从内存读取密码: $currentFileUri")
                password = MemoryPasswordStorage.getInstance().getPasswordFromMemory(currentFileUri!!)
                if (password != null) {
                    Log.i(TAG, "从内存读取密码成功: $currentFileUri")
                }
            }
            
            // 如果内存中没有找到密码，尝试使用稳定文档路径
            if (password == null && stableDocumentPath != null) {
                Log.d(TAG, "尝试从内存读取密码: $stableDocumentPath")
                password = MemoryPasswordStorage.getInstance().getPasswordFromMemory(stableDocumentPath!!)
                if (password != null) {
                    Log.i(TAG, "从内存读取密码成功: $stableDocumentPath")
                }
            }
            
            // 不再尝试从文件元数据中读取，避免权限问题
            
            // 如果找到密码，自动填充
            if (password != null && password.isNotEmpty()) {
                Log.i(TAG, "开始自动填充密码")
                
                val passwordInputNodes = findPasswordInputNodes(rootNode)
                if (passwordInputNodes.isNotEmpty()) {
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
                                    
                                    // 检查是否需要自动点击确认按钮
                                    // 只有在首次打开加密文件的场景中才自动提交
                                    // 【添加密码】窗口不允许自动关闭，必须由用户手动操作
                                    val shouldAutoSubmit = !hasClickedGeneratePassword
                                    
                                    Log.i(TAG, "是否自动提交: $shouldAutoSubmit, 场景类型: ${if (hasClickedGeneratePassword) "生成密码" else "首次打开"}")
                                    
                                    if (shouldAutoSubmit) {
                                        // 场景1：首次打开加密文件，自动点击确认按钮
                                        Log.i(TAG, "尝试自动点击确认按钮")
                                        val confirmButton = findConfirmButton(rootNode)
                                        if (confirmButton != null) {
                                            Log.i(TAG, "找到确认按钮，尝试点击")
                                            val clickSuccess = confirmButton.performAction(AccessibilityNodeInfo.ACTION_CLICK)
                                            if (clickSuccess) {
                                                Log.i(TAG, "成功点击确认按钮")
                                                isDocumentOpened = true // 标记文档已打开
                                                
                                                // 启动文件系统事件监听器
                                                startFileSystemEventListener(password)
                                            } else {
                                                Log.e(TAG, "点击确认按钮失败")
                                            }
                                        } else {
                                            Log.d(TAG, "未找到确认按钮")
                                        }
                                    } else {
                                        Log.i(TAG, "此场景不自动提交，保持窗口打开")
                                    }
                                    
                                    // 填充后清除PasswordHolder缓存
                                    com.wpspasswordmanager.business.PasswordHolder.clear()
                                    
                                    // 重置生成密码标志
                                    if (hasClickedGeneratePassword) {
                                        hasClickedGeneratePassword = false
                                    }
                                } else {
                                    Log.e(TAG, "密码填充失败")
                                }
                            } catch (e: Exception) {
                                Log.e(TAG, "填充密码时发生异常", e)
                            }
                        }
                    } finally {
                        isFillingPassword = false
                    }
                } else {
                    Log.e(TAG, "未找到密码输入框")
                }
            } else {
                Log.d(TAG, "未找到密码，等待用户手动输入")
            }
        } catch (e: Exception) {
            Log.e(TAG, "自动填充密码失败", e)
            isFillingPassword = false
        }
    }
    
    /**
     * 检查当前窗口是否是首次打开加密文件的窗口
     * 只有 OpenEditDecryptDialog 窗口才允许自动提交
     */
    private fun isOpenEditDecryptDialog(): Boolean {
        val rootNode = rootInActiveWindow
        if (rootNode != null) {
            // 检查根节点的类名
            val rootClassName = rootNode.className?.toString() ?: ""
            if (rootClassName.contains("OpenEditDecryptDialog")) {
                Log.d(TAG, "当前窗口类名: $rootClassName, 是否为OpenEditDecryptDialog: true")
                return true
            }
            
            // 如果根节点是FrameLayout，检查其子节点是否包含OpenEditDecryptDialog
            if (rootClassName.contains("FrameLayout")) {
                for (i in 0 until rootNode.childCount) {
                    val child = rootNode.getChild(i)
                    if (child != null) {
                        val childClassName = child.className?.toString() ?: ""
                        if (childClassName.contains("OpenEditDecryptDialog")) {
                            Log.d(TAG, "子节点类名: $childClassName, 是否为OpenEditDecryptDialog: true")
                            return true
                        }
                    }
                }
            }
            
            // 检查窗口标题或其他元素
            val windowTitle = rootNode.text?.toString() ?: ""
            if (windowTitle.contains("文档已加密") || windowTitle.contains("Document is encrypted")) {
                Log.d(TAG, "窗口标题: $windowTitle, 判断为加密文档窗口")
                return true
            }
            
            Log.d(TAG, "当前窗口类名: $rootClassName, 是否为OpenEditDecryptDialog: false")
        }
        return false
    }

    private fun detectDocumentPath(rootNode: AccessibilityNodeInfo, enableLogging: Boolean = true) {
        // 优先使用已存储的本地文件路径
        if (currentFileUri != null && !currentFileUri!!.startsWith("content://")) {
            if (enableLogging) {
                Log.d(TAG, "优先使用已存储的本地文件路径: $currentFileUri")
            }
            stableDocumentPath = currentFileUri
            currentDocumentPath = currentFileUri
            if (enableLogging) {
                Log.d(TAG, "最终确定的文档路径: $currentDocumentPath")
                // 检查本地文件状态
                val file = java.io.File(currentFileUri!!)
                Log.d(TAG, "调试：本地文件存在: ${file.exists()}")
                Log.d(TAG, "调试：文件路径: ${file.absolutePath}")
                Log.d(TAG, "调试：文件可写: ${file.canWrite()}")
                Log.d(TAG, "调试：文件可读: ${file.canRead()}")
            }
            if (enableLogging) {
                Log.d(TAG, "文档路径检测完成")
            }
            return
        }
        
        // 尝试从窗口标题或其他元素中提取文档路径
        // 这里可以根据实际WPS界面结构进行调整
        var detectedPath: String? = null
        
        if (enableLogging) {
            Log.d(TAG, "开始检测文档路径")
        }
        
        // 方法1: 尝试从WPS的标题栏获取文件路径（优先使用）
        detectedPath = findFilePathFromTitleBar(rootNode)
        if (detectedPath != null && enableLogging) {
            Log.d(TAG, "从标题栏检测到文档路径: $detectedPath")
        }
        
        // 方法2: 从根节点获取文本
        if (detectedPath == null) {
            val windowTitle = rootNode.text?.toString() ?: ""
            if (windowTitle.isNotEmpty()) {
                detectedPath = windowTitle
                if (enableLogging) {
                    Log.d(TAG, "从根节点检测到文档路径: $detectedPath")
                }
            }
        }
        
        // 方法3: 遍历所有节点，寻找可能的文档路径
        if (detectedPath == null) {
            detectedPath = findDocumentPathFromNodes(rootNode)
            if (detectedPath != null && enableLogging) {
                Log.d(TAG, "从子节点检测到文档路径: $detectedPath")
            }
        }
        
        // 方法4: 尝试从WPS特定的界面元素中获取文件路径
        if (detectedPath == null) {
            detectedPath = findWpsSpecificFilePath(rootNode)
            if (detectedPath != null && enableLogging) {
                Log.d(TAG, "从WPS特定元素检测到文档路径: $detectedPath")
            }
        }
        
        // 方法5: 尝试从WPS的文件信息区域获取文件路径
        if (detectedPath == null) {
            detectedPath = findFilePathFromFileInfoArea(rootNode)
            if (detectedPath != null && enableLogging) {
                Log.d(TAG, "从文件信息区域检测到文档路径: $detectedPath")
            }
        }
        
        // 方法6: 尝试从WPS的状态栏获取文件路径
        if (detectedPath == null) {
            detectedPath = findFilePathFromStatusBar(rootNode)
            if (detectedPath != null && enableLogging) {
                Log.d(TAG, "从状态栏检测到文档路径: $detectedPath")
            }
        }
        
        // 方法7: 尝试从WPS的文件名显示区域获取文件路径
        if (detectedPath == null) {
            detectedPath = findFileNameFromWps(rootNode)
            if (detectedPath != null && enableLogging) {
                Log.d(TAG, "从WPS文件名显示区域检测到文档路径: $detectedPath")
            }
        }
        
        // 方法8: 尝试从WPS的导航栏获取文件路径
        if (detectedPath == null) {
            detectedPath = findFilePathFromNavigationBar(rootNode)
            if (detectedPath != null && enableLogging) {
                Log.d(TAG, "从导航栏检测到文档路径: $detectedPath")
            }
        }
        
        // 方法9: 尝试从WPS的文件属性区域获取文件路径
        if (detectedPath == null) {
            detectedPath = findFilePathFromFileProperties(rootNode)
            if (detectedPath != null && enableLogging) {
                Log.d(TAG, "从文件属性区域检测到文档路径: $detectedPath")
            }
        }
        
        // 方法10: 尝试从WPS的通知或其他系统元素获取文件路径
        if (detectedPath == null) {
            detectedPath = findFilePathFromSystemElements(rootNode)
            if (detectedPath != null && enableLogging) {
                Log.d(TAG, "从系统元素检测到文档路径: $detectedPath")
            }
        }
        
        // 方法11: 从包名和类名中推断（仅作为最后的备选）
        if (detectedPath == null) {
            val className = rootNode.className?.toString() ?: ""
            if (className.isNotEmpty()) {
                detectedPath = "wps_" + className.hashCode() + "_" + System.currentTimeMillis()
                if (enableLogging) {
                    Log.d(TAG, "从类名推断文档路径: $detectedPath")
                }
            }
        }
        
        // 方法12: 使用时间戳作为临时标识符（仅作为最后的备选）
        if (detectedPath == null) {
            detectedPath = "temp_" + System.currentTimeMillis()
            if (enableLogging) {
                Log.d(TAG, "使用临时标识符作为文档路径: $detectedPath")
            }
        }
        
        // 检查是否是真实的文件路径
        val isRealFilePath = detectedPath?.contains(".doc") == true || 
                           detectedPath?.contains(".docx") == true || 
                           detectedPath?.contains(".xls") == true || 
                           detectedPath?.contains(".xlsx") == true || 
                           detectedPath?.contains(".ppt") == true || 
                           detectedPath?.contains(".pptx") == true ||
                           detectedPath?.contains("/storage/") == true ||
                           detectedPath?.contains("SD卡") == true ||
                           detectedPath?.contains("Internal storage") == true ||
                           detectedPath?.contains("file:/") == true
        
        if (enableLogging) {
            Log.d(TAG, "检测到的文档路径: $detectedPath")
            Log.d(TAG, "是否为真实文件路径: $isRealFilePath")
        }
        
        // 保持文档路径稳定，只在第一次设置或检测到新的有效路径时更新
        if (stableDocumentPath == null || (isRealFilePath && (!stableDocumentPath!!.contains(".doc") && !stableDocumentPath!!.contains(".xls") && !stableDocumentPath!!.contains(".ppt") && !stableDocumentPath!!.contains("/storage/")))) {
            stableDocumentPath = detectedPath
            if (enableLogging) {
                Log.d(TAG, "设置稳定文档路径: $stableDocumentPath")
            }
        }
        
        currentDocumentPath = stableDocumentPath
        if (enableLogging) {
            Log.d(TAG, "最终确定的文档路径: $currentDocumentPath")
        }
        
        // 调试：检查文件是否存在
        if (stableDocumentPath != null && enableLogging) {
            if (stableDocumentPath!!.startsWith("content://")) {
                // 对于Content URI，只记录URI信息，不尝试访问
                Log.d(TAG, "调试：Content URI: $stableDocumentPath")
                Log.d(TAG, "调试：Content URI 权限处理将在需要时进行")
                // 不再尝试检查Content URI的权限，避免权限错误
            } else {
                // 对于普通文件路径
                val file = java.io.File(stableDocumentPath!!)
                Log.d(TAG, "调试：文件存在: ${file.exists()}")
                Log.d(TAG, "调试：文件路径: ${file.absolutePath}")
                Log.d(TAG, "调试：文件可写: ${file.canWrite()}")
                Log.d(TAG, "调试：文件可读: ${file.canRead()}")
                
                // 尝试获取文件的父目录
                val parent = file.parent
                if (parent != null) {
                    val parentDir = java.io.File(parent)
                    Log.d(TAG, "调试：父目录存在: ${parentDir.exists()}")
                    Log.d(TAG, "调试：父目录可写: ${parentDir.canWrite()}")
                }
            }
        }
        
        if (enableLogging) {
            Log.d(TAG, "文档路径检测完成")
        }
    }
    
    /**
     * 从系统元素获取文件路径
     */
    private fun findFilePathFromSystemElements(rootNode: AccessibilityNodeInfo): String? {
        val queue = mutableListOf(rootNode)
        
        while (queue.isNotEmpty()) {
            val currentNode = queue.removeAt(0)
            
            // 检查节点文本是否可能是文件路径
            val text = currentNode.text?.toString() ?: ""
            val contentDescription = currentNode.contentDescription?.toString() ?: ""
            
            // 检查是否包含文件路径特征
            if (text.isNotEmpty() && (text.contains("/storage/") || text.contains("SD卡") || text.contains("Internal storage")) &&
                (text.contains(".doc") || text.contains(".docx") || text.contains(".xls") || 
                 text.contains(".xlsx") || text.contains(".ppt") || text.contains(".pptx"))) {
                Log.d(TAG, "从系统元素找到文件路径: $text")
                return text
            }
            
            if (contentDescription.isNotEmpty() && (contentDescription.contains("/storage/") || contentDescription.contains("SD卡") || contentDescription.contains("Internal storage")) &&
                (contentDescription.contains(".doc") || contentDescription.contains(".docx") || contentDescription.contains(".xls") || 
                 contentDescription.contains(".xlsx") || contentDescription.contains(".ppt") || contentDescription.contains(".pptx"))) {
                Log.d(TAG, "从系统元素内容描述找到文件路径: $contentDescription")
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
    
    /**
     * 从WPS的导航栏获取文件路径
     */
    private fun findFilePathFromNavigationBar(rootNode: AccessibilityNodeInfo): String? {
        val queue = mutableListOf(rootNode)
        
        while (queue.isNotEmpty()) {
            val currentNode = queue.removeAt(0)
            
            // 检查节点文本是否可能是文件路径
            val text = currentNode.text?.toString() ?: ""
            val contentDescription = currentNode.contentDescription?.toString() ?: ""
            val className = currentNode.className?.toString() ?: ""
            
            // 检查是否是导航栏相关的节点
            if (className.contains("Navigation") || className.contains("navigation") || 
                className.contains("Toolbar") || className.contains("toolbar") ||
                className.contains("ActionBar") || className.contains("action_bar")) {
                
                // 检查文本是否包含文件路径特征
                if (text.isNotEmpty() && (text.contains(".doc") || text.contains(".docx") || 
                    text.contains(".xls") || text.contains(".xlsx") || 
                    text.contains(".ppt") || text.contains(".pptx"))) {
                    Log.d(TAG, "从导航栏找到文件路径: $text")
                    return text
                }
                
                // 检查内容描述
                if (contentDescription.isNotEmpty() && (contentDescription.contains(".doc") || contentDescription.contains(".docx") || 
                    contentDescription.contains(".xls") || contentDescription.contains(".xlsx") || 
                    contentDescription.contains(".ppt") || contentDescription.contains(".pptx"))) {
                    Log.d(TAG, "从导航栏内容描述找到文件路径: $contentDescription")
                    return contentDescription
                }
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
    
    /**
     * 从WPS的文件属性区域获取文件路径
     */
    private fun findFilePathFromFileProperties(rootNode: AccessibilityNodeInfo): String? {
        val queue = mutableListOf(rootNode)
        
        while (queue.isNotEmpty()) {
            val currentNode = queue.removeAt(0)
            
            // 检查节点文本是否可能是文件路径
            val text = currentNode.text?.toString() ?: ""
            val contentDescription = currentNode.contentDescription?.toString() ?: ""
            
            // 检查是否包含文件路径特征
            if (text.isNotEmpty() && (text.contains("路径") || text.contains("Path") || text.contains("path")) &&
                (text.contains(".doc") || text.contains(".docx") || 
                 text.contains(".xls") || text.contains(".xlsx") || 
                 text.contains(".ppt") || text.contains(".pptx") ||
                 text.contains("/storage/") || text.contains("SD卡") ||
                 text.contains("Internal storage") || text.contains("file:/"))) {
                Log.d(TAG, "从文件属性区域找到文件路径: $text")
                return text
            }
            
            if (contentDescription.isNotEmpty() && (contentDescription.contains("路径") || contentDescription.contains("Path") || contentDescription.contains("path")) &&
                (contentDescription.contains(".doc") || contentDescription.contains(".docx") || 
                 contentDescription.contains(".xls") || contentDescription.contains(".xlsx") || 
                 contentDescription.contains(".ppt") || contentDescription.contains(".pptx") ||
                 contentDescription.contains("/storage/") || contentDescription.contains("SD卡") ||
                 contentDescription.contains("Internal storage") || contentDescription.contains("file:/"))) {
                Log.d(TAG, "从文件属性区域内容描述找到文件路径: $contentDescription")
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
    
    /**
     * 从WPS的文件名显示区域获取文件名
     */
    private fun findFileNameFromWps(rootNode: AccessibilityNodeInfo): String? {
        val queue = mutableListOf(rootNode)
        
        while (queue.isNotEmpty()) {
            val currentNode = queue.removeAt(0)
            
            // 检查节点文本是否可能是文件名
            val text = currentNode.text?.toString() ?: ""
            val contentDescription = currentNode.contentDescription?.toString() ?: ""
            val className = currentNode.className?.toString() ?: ""
            
            // 检查是否是文件名（包含常见的文档扩展名）
            if (text.isNotEmpty() && (text.contains(".doc") || text.contains(".docx") || 
                text.contains(".xls") || text.contains(".xlsx") || 
                text.contains(".ppt") || text.contains(".pptx"))) {
                Log.d(TAG, "从WPS找到文件名: $text")
                return text
            }
            
            if (contentDescription.isNotEmpty() && (contentDescription.contains(".doc") || contentDescription.contains(".docx") || 
                contentDescription.contains(".xls") || contentDescription.contains(".xlsx") || 
                contentDescription.contains(".ppt") || contentDescription.contains(".pptx"))) {
                Log.d(TAG, "从WPS内容描述找到文件名: $contentDescription")
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
    
    /**
     * 从WPS的状态栏获取文件路径
     */
    private fun findFilePathFromStatusBar(rootNode: AccessibilityNodeInfo): String? {
        val queue = mutableListOf(rootNode)
        
        while (queue.isNotEmpty()) {
            val currentNode = queue.removeAt(0)
            
            // 检查节点文本是否可能是文件路径
            val text = currentNode.text?.toString() ?: ""
            val contentDescription = currentNode.contentDescription?.toString() ?: ""
            val className = currentNode.className?.toString() ?: ""
            
            // 检查是否是状态栏相关的节点
            if (className.contains("Status") || className.contains("status") || 
                className.contains("Bar") || className.contains("bar")) {
                
                // 检查文本是否包含文件路径特征
                if (text.isNotEmpty() && (
                    text.contains(".doc") || text.contains(".docx") || 
                    text.contains(".xls") || text.contains(".xlsx") || 
                    text.contains(".ppt") || text.contains(".pptx")
                )) {
                    Log.d(TAG, "从状态栏找到文件路径: $text")
                    return text
                }
                
                // 检查内容描述
                if (contentDescription.isNotEmpty() && (
                    contentDescription.contains(".doc") || contentDescription.contains(".docx") || 
                    contentDescription.contains(".xls") || contentDescription.contains(".xlsx") || 
                    contentDescription.contains(".ppt") || contentDescription.contains(".pptx")
                )) {
                    Log.d(TAG, "从状态栏内容描述找到文件路径: $contentDescription")
                    return contentDescription
                }
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
    
    /**
     * 从WPS的文件信息区域获取文件路径
     */
    private fun findFilePathFromFileInfoArea(rootNode: AccessibilityNodeInfo): String? {
        val queue = mutableListOf(rootNode)
        
        while (queue.isNotEmpty()) {
            val currentNode = queue.removeAt(0)
            
            // 检查节点文本是否可能是文件路径
            val text = currentNode.text?.toString() ?: ""
            val contentDescription = currentNode.contentDescription?.toString() ?: ""
            
            // 检查是否包含文件路径特征
            if (text.isNotEmpty() && (
                text.contains("/storage/") || 
                text.contains("SD卡") || 
                text.contains("Internal storage") ||
                text.contains("file:/")
            ) && (
                text.contains(".doc") || 
                text.contains(".docx") || 
                text.contains(".xls") || 
                text.contains(".xlsx") || 
                text.contains(".ppt") || 
                text.contains(".pptx")
            )) {
                Log.d(TAG, "从文件信息区域找到文件路径: $text")
                return text
            }
            
            if (contentDescription.isNotEmpty() && (
                contentDescription.contains("/storage/") || 
                contentDescription.contains("SD卡") || 
                contentDescription.contains("Internal storage") ||
                contentDescription.contains("file:/")
            ) && (
                contentDescription.contains(".doc") || 
                contentDescription.contains(".docx") || 
                contentDescription.contains(".xls") || 
                contentDescription.contains(".xlsx") || 
                contentDescription.contains(".ppt") || 
                contentDescription.contains(".pptx")
            )) {
                Log.d(TAG, "从文件信息区域内容描述找到文件路径: $contentDescription")
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
    
    /**
     * 从WPS特定的界面元素中获取文件路径
     */
    private fun findWpsSpecificFilePath(rootNode: AccessibilityNodeInfo): String? {
        val queue = mutableListOf(rootNode)
        
        while (queue.isNotEmpty()) {
            val currentNode = queue.removeAt(0)
            
            // 检查节点是否是WPS特定的文件信息元素
            val text = currentNode.text?.toString() ?: ""
            val contentDescription = currentNode.contentDescription?.toString() ?: ""
            val className = currentNode.className?.toString() ?: ""
            val viewId = currentNode.viewIdResourceName ?: ""
            
            // 检查是否是WPS的标题栏或文件信息区域
            if (className.contains("Title") || className.contains("title") || 
                className.contains("Bar") || className.contains("bar") ||
                className.contains("File") || className.contains("file") ||
                className.contains("Info") || className.contains("info") ||
                className.contains("Toolbar") || className.contains("toolbar")) {
                
                // 检查文本是否包含文件路径或文件名
                if (text.isNotEmpty()) {
                    // 检查是否包含文件扩展名
                    if (text.contains(".doc") || text.contains(".docx") || 
                        text.contains(".xls") || text.contains(".xlsx") || 
                        text.contains(".ppt") || text.contains(".pptx")) {
                        Log.d(TAG, "从WPS特定元素找到文件路径: $text")
                        return text
                    }
                }
                
                // 检查内容描述
                if (contentDescription.isNotEmpty()) {
                    // 检查是否包含文件扩展名
                    if (contentDescription.contains(".doc") || contentDescription.contains(".docx") || 
                        contentDescription.contains(".xls") || contentDescription.contains(".xlsx") || 
                        contentDescription.contains(".ppt") || contentDescription.contains(".pptx")) {
                        Log.d(TAG, "从WPS特定元素内容描述找到文件路径: $contentDescription")
                        return contentDescription
                    }
                }
            }
            
            // 检查是否是文件路径相关的节点
            if (text.isNotEmpty() && (text.contains("/storage/") || text.contains("SD卡") || text.contains("Internal storage")) &&
                (text.contains(".doc") || text.contains(".docx") || text.contains(".xls") || 
                 text.contains(".xlsx") || text.contains(".ppt") || text.contains(".pptx"))) {
                Log.d(TAG, "从文件路径相关节点找到文件路径: $text")
                return text
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
    
    /**
     * 从WPS的标题栏获取文件路径
     */
    private fun findFilePathFromTitleBar(rootNode: AccessibilityNodeInfo): String? {
        val queue = mutableListOf(rootNode)
        
        Log.d(TAG, "开始从标题栏获取文件路径")
        
        while (queue.isNotEmpty()) {
            val currentNode = queue.removeAt(0)
            
            // 检查节点文本是否可能是文件路径
            val text = currentNode.text?.toString() ?: ""
            val contentDescription = currentNode.contentDescription?.toString() ?: ""
            val className = currentNode.className?.toString() ?: ""
            
//            Log.d(TAG, "检查节点: 类名=$className, 文本=$text, 内容描述=$contentDescription")
            
            // 查找包含文件路径特征的文本
            if (text.isNotEmpty()) {
                // 检查是否包含文件扩展名
                if (text.contains(".doc") || text.contains(".docx") || 
                    text.contains(".xls") || text.contains(".xlsx") || 
                    text.contains(".ppt") || text.contains(".pptx")) {
                    Log.d(TAG, "从标题栏找到文件路径: $text")
                    return text
                }
                
                // 检查是否包含存储路径
                if (text.contains("/storage/") || text.contains("SD卡") || 
                    text.contains("Internal storage")) {
                    Log.d(TAG, "从标题栏找到存储路径: $text")
                    return text
                }
            }
            
            if (contentDescription.isNotEmpty()) {
                // 检查是否包含文件扩展名
                if (contentDescription.contains(".doc") || contentDescription.contains(".docx") || 
                    contentDescription.contains(".xls") || contentDescription.contains(".xlsx") || 
                    contentDescription.contains(".ppt") || contentDescription.contains(".pptx")) {
                    Log.d(TAG, "从标题栏内容描述找到文件路径: $contentDescription")
                    return contentDescription
                }
                
                // 检查是否包含存储路径
                if (contentDescription.contains("/storage/") || contentDescription.contains("SD卡") || 
                    contentDescription.contains("Internal storage")) {
                    Log.d(TAG, "从标题栏内容描述找到存储路径: $contentDescription")
                    return contentDescription
                }
            }
            
            // 尝试从节点的其他属性中获取文件路径
            try {
                // 检查节点是否有与文件路径相关的属性
                val nodeInfo = currentNode
                if (nodeInfo != null) {
                    // 尝试获取节点的包名和类名，可能包含文件信息
                    val className = nodeInfo.className?.toString() ?: ""
                    if (className.contains("Title") || className.contains("title") || 
                        className.contains("bar") || className.contains("Bar")) {
                        // 对于标题栏节点，尝试获取其文本或子节点的文本
                        val titleText = nodeInfo.text?.toString() ?: ""
                        if (titleText.isNotEmpty()) {
                            Log.d(TAG, "标题栏节点文本: $titleText")
                            return titleText
                        }
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "获取节点属性失败", e)
            }
            
            // 遍历子节点
            for (i in 0 until currentNode.childCount) {
                val child = currentNode.getChild(i)
                if (child != null) {
                    queue.add(child)
                }
            }
        }
        
        Log.d(TAG, "从标题栏未找到文件路径")
        return null
    }
    
    private fun findDocumentPathFromNodes(node: AccessibilityNodeInfo): String? {
        val queue = mutableListOf(node)
        
        while (queue.isNotEmpty()) {
            val currentNode = queue.removeAt(0)
            
            // 检查节点文本是否可能是文档路径
            val text = currentNode.text?.toString() ?: ""
            if (text.isNotEmpty()) {
                // 检查文本是否包含文件路径特征
                if ((text.contains(".doc") || text.contains(".docx") || text.contains(".xls") || 
                     text.contains(".xlsx") || text.contains(".ppt") || text.contains(".pptx")) &&
                    (text.contains("/") || text.contains("\\") || text.contains("storage/") || 
                     text.contains("SD卡") || text.contains("Internal storage"))) {
                    return text
                }
                // 检查文本是否只是文件名（包含扩展名）
                if (text.contains(".doc") || text.contains(".docx") || 
                    text.contains(".xls") || text.contains(".xlsx") || 
                    text.contains(".ppt") || text.contains(".pptx")) {
                    return text
                }
            }
            
            // 检查节点内容描述
            val contentDescription = currentNode.contentDescription?.toString() ?: ""
            if (contentDescription.isNotEmpty()) {
                // 检查内容描述是否包含文件路径特征
                if ((contentDescription.contains(".doc") || contentDescription.contains(".docx") || 
                     contentDescription.contains(".xls") || contentDescription.contains(".xlsx") || 
                     contentDescription.contains(".ppt") || contentDescription.contains(".pptx")) &&
                    (contentDescription.contains("/") || contentDescription.contains("\\") || 
                     contentDescription.contains("storage/") || contentDescription.contains("SD卡") || 
                     contentDescription.contains("Internal storage"))) {
                    return contentDescription
                }
                // 检查内容描述是否只是文件名（包含扩展名）
                if (contentDescription.contains(".doc") || contentDescription.contains(".docx") || 
                    contentDescription.contains(".xls") || contentDescription.contains(".xlsx") || 
                    contentDescription.contains(".ppt") || contentDescription.contains(".pptx")) {
                    return contentDescription
                }
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
        // 移除悬浮按钮服务启动，避免崩溃
        Log.d(TAG, "悬浮按钮服务已禁用")
    }

    private fun stopFloatingButtonService() {
        // 移除悬浮按钮服务停止，避免崩溃
        Log.d(TAG, "悬浮按钮服务已禁用")
    }
    
    /**
     * 启动文件系统事件监听器
     */
    private fun startFileSystemEventListener(password: String) {
        try {
            var targetPath: String? = null
            if (currentFileUri != null) {
                targetPath = currentFileUri
            } else if (stableDocumentPath != null) {
                targetPath = stableDocumentPath
            }
            
            if (targetPath != null) {
                Log.d(TAG, "启动文件系统事件监听器: $targetPath")
                // 先停止之前可能存在的监听器
                fileSystemEventListener?.stopListening()
                // 创建并启动新的文件系统事件监听器
                fileSystemEventListener = FileSystemEventListener(targetPath, password, this)
                fileSystemEventListener?.startListening()
                showOperationNotification("操作成功", "已启动文件监听，将在文件保存后自动写入密码")
            } else {
                Log.e(TAG, "没有可用的文件路径")
                showOperationNotification("操作失败", "没有可用的文件路径")
            }
        } catch (e: Exception) {
            Log.e(TAG, "启动文件系统事件监听器失败", e)
            showOperationNotification("操作失败", "启动文件监听失败")
        }
    }

    /**
     * 查找密码输入框
     */
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

    /**
     * 检查是否是密码输入框
     */
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
        val viewId = node.viewIdResourceName ?: ""
        
        // 更灵活地识别按钮，不只是检查类名是否包含Button
        val isButton = className.contains("Button") || className.contains("button") || 
                      className.contains("android.widget.Button") || className.contains("androidx.appcompat.widget.AppCompatButton") ||
                      className.contains("View") || className.contains("view") || // 增加对View类的支持，因为有些按钮可能使用View实现
                      className.contains("TextView") || className.contains("textView") || // 增加对TextView类的支持，因为有些按钮可能使用TextView实现
                      className.contains("AppCompatButton") || className.contains("appcompat_button") // 增加对AppCompatButton的支持
        
        // 检查文本或内容描述是否包含确认相关词汇
        val hasConfirmText = text.contains("确定") || text.contains("确认") || 
                            text.contains("OK") || text.contains("Confirm") ||
                            text.contains("ok") || text.contains("confirm") ||
                            text.contains("确定") || text.contains("确认") ||
                            contentDescription.contains("确定") || contentDescription.contains("确认") ||
                            contentDescription.contains("OK") || contentDescription.contains("Confirm") ||
                            contentDescription.contains("ok") || contentDescription.contains("confirm")
        
        // 检查是否是特定的确认按钮ID
        val isConfirmId = viewId.contains("confirm") || viewId.contains("ok") || viewId.contains("button1")
        
        // 综合判断
        val result = (isButton && hasConfirmText) || isConfirmId
        if (result) {
            Log.d(TAG, "确认按钮识别成功: 类名=$className, 文本=$text, 内容描述=$contentDescription, ID=$viewId")
        }
        
        return result
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


}
