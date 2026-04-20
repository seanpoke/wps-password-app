package com.wpspasswordmanager.monitor

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.AccessibilityServiceInfo
import android.content.Intent
import android.text.InputType
import android.util.Log
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import com.wpspasswordmanager.business.FileMetaFactory

class WpsAccessibilityService : AccessibilityService() {

    companion object {
        private const val TAG = "WpsAccessibilityService"
        private val WPS_PACKAGES = arrayOf("cn.wps.moffice_eng", "cn.wps.moffice")
        private var tempPassword: String? = null // 临时存储密码，用户确认前不写入MemoryPasswordStorage

        /**
         * 获取临时密码
         */
        fun getTempPassword(): String? {
            return tempPassword
        }

        var currentDocumentPath: String? = null
        var stableDocumentPath: String? = null // 稳定的文档路径
        var currentFileUri: String? = null // 当前文件的URI
        private var isFillingPassword = false // 防止自动填充无限循环的标志
        private var hasClickedShowPassword = false // 防止重复点击显示密码选项的标志
        private var hasClickedGeneratePassword = false // 标记是否点击了生成密码按钮
        private var isFloatingButtonServiceStarted = false // 标记悬浮按钮服务是否已启动
        private var isDocumentOpened = false // 标记文档是否已经成功打开
        private var currentDialogType: DialogType = DialogType.UNKNOWN // 当前对话框类型
        private var lastDialogType: DialogType = DialogType.UNKNOWN // 上次的对话框类型

        // 显示密码监测相关变量
        private var isMonitoringShowPassword = false // 是否正在监测显示密码勾选框
        private var showPasswordChecked = false // 显示密码是否已勾选
        private var showPasswordMonitorThread: Thread? = null // 监测线程

        // 对话框类型
        enum class DialogType {
            UNKNOWN,
            OPEN_ENCRYPTED_DOCUMENT, // 打开加密文档
            ADD_PASSWORD, // 添加密码
            MODIFY_PASSWORD // 修改密码
        }
    }

    override fun onServiceConnected() {
        super.onServiceConnected()
        Log.d(TAG, "无障碍服务已连接")

        // 注册服务到管理器
        AccessibilityServiceManager.getInstance().setService(this)

        // 初始化所有状态变量
        currentDialogType = DialogType.UNKNOWN
        hasClickedShowPassword = false
        showPasswordChecked = false
        isMonitoringShowPassword = false
        showPasswordMonitorThread = null
        isFillingPassword = false
        hasClickedGeneratePassword = false
        isFloatingButtonServiceStarted = false
        isDocumentOpened = false
        tempPassword = null
        currentFileUri = null
        stableDocumentPath = null
        currentDocumentPath = null

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
                Log.d(
                    TAG,
                    "收到事件: ${event.eventType}, 包名: $packageName, 类名: ${event.className}"
                )
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
                        Log.d(
                            TAG,
                            "收到点击事件: ${event.source?.className}, 文本: ${event.source?.text}"
                        )
                        handleViewClicked(event)
                    }

                    AccessibilityEvent.TYPE_VIEW_TEXT_CHANGED -> {
                        Log.d(
                            TAG,
                            "收到文本变化事件: ${event.source?.className}, 文本: ${event.text}"
                        )
                        handleViewTextChanged(event)
                    }

                    AccessibilityEvent.TYPE_VIEW_LONG_CLICKED -> {
                        Log.d(
                            TAG,
                            "收到长按事件: ${event.source?.className}, 文本: ${event.source?.text}"
                        )
                        handleViewClicked(event)
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
                Log.d(
                    TAG,
                    "[时间戳: ${System.currentTimeMillis()}] 密码输入框文本变化: '$password'，长度: ${password.length}"
                )
                Log.d(
                    TAG,
                    "[时间戳: ${System.currentTimeMillis()}] 变化前的tempPassword: '${tempPassword ?: "null"}'"
                )
                // 临时存储密码，用户确认前不写入MemoryPasswordStorage
                tempPassword = password
                Log.i(
                    TAG,
                    "[时间戳: ${System.currentTimeMillis()}] 已将用户输入的密码临时存储，等待用户确认: '$password'"
                )
                Log.d(
                    TAG,
                    "[时间戳: ${System.currentTimeMillis()}] 变化后的tempPassword: '${tempPassword ?: "null"}'"
                )
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

                // 优先使用临时存储的密码
                var password: String? = tempPassword

                if (password != null && password.isNotEmpty()) {
                    Log.i(
                        TAG,
                        "[时间戳: ${System.currentTimeMillis()}] 从临时存储中获取到密码: '$password'，长度: ${password.length}"
                    )
                    // 清除临时密码
                    tempPassword = null
                    Log.d(TAG, "[时间戳: ${System.currentTimeMillis()}] 已清除临时密码")
                } else {
                    Log.e(TAG, "[时间戳: ${System.currentTimeMillis()}] 临时存储中未找到密码")
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

    override fun onInterrupt() {
        Log.d(TAG, "无障碍服务被中断")
        // 停止显示密码监测
        stopShowPasswordMonitoring()
    }

    override fun onDestroy() {
        super.onDestroy()
        Log.d(TAG, "无障碍服务被销毁")
        // 停止显示密码监测
        stopShowPasswordMonitoring()
        // 清理其他状态
        currentDialogType = DialogType.UNKNOWN
        hasClickedShowPassword = false
        showPasswordChecked = false
        isFillingPassword = false
        hasClickedGeneratePassword = false
        isFloatingButtonServiceStarted = false
        isDocumentOpened = false
        // 清理临时密码
        tempPassword = null
        // 清理密码状态
        val filePath = currentFileUri ?: stableDocumentPath
        if (filePath != null) {
            FileMetaFactory.clearFile(filePath)
            Log.d(TAG, "[时间戳: ${System.currentTimeMillis()}] 已清理密码状态: $filePath")
        }
        // 清理文档路径
        currentFileUri = null
        stableDocumentPath = null
        currentDocumentPath = null
    }

    /**
     * 显示操作通知
     */
    private fun showOperationNotification(title: String, content: String) {
        try {
            Log.d(TAG, "操作通知: $title - $content")
            // 使用通知管理器显示通知
            com.wpspasswordmanager.ui.AppNotificationManager.getInstance(this)
                .showOperationNotification(title, content)
        } catch (e: Exception) {
            Log.e(TAG, "显示操作通知失败", e)
        }
    }


    private fun handleWindowStateChanged(event: AccessibilityEvent) {
        Log.d(TAG, "窗口状态改变: ${event.className}")
        val rootNode = rootInActiveWindow ?: return
        detectPasswordDialog(rootNode)
        detectDocumentPath(rootNode)

        // 检测是否从密码弹框切换到文档编辑界面
        val className = event.className?.toString() ?: ""
        Log.d(TAG, "当前窗口类名: $className")

        // 检查是否切换到文档编辑界面
        val isDocumentEditor = className.contains("Writer") || className.contains("writer") ||
                className.contains("Spreadsheets") || className.contains("spreadsheets") ||
                className.contains("Presentation") || className.contains("presentation")

        if (isDocumentEditor) {
            // 检查是否有临时密码未处理
            if (tempPassword != null && tempPassword!!.isNotEmpty()) {
                Log.i(
                    TAG,
                    "[时间戳: ${System.currentTimeMillis()}] 检测到从密码弹框切换到文档编辑界面，处理未确认的临时密码: '$tempPassword'"
                )

                val filePath = currentFileUri ?: stableDocumentPath
                if (filePath != null) {
                    FileMetaFactory.updatePendingPassword(filePath, tempPassword!!)
                    Log.d(
                        TAG,
                        "handleWindowStateChanged-已更新待定密码到密码状态管理器: $filePath"
                    )
                }

                // 清除临时密码
                tempPassword = null
                Log.d(TAG, "[时间戳: ${System.currentTimeMillis()}] 已清除临时密码")
            }
        }

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

            // 清理相关缓存
            clearCurrentFileUri()
            stableDocumentPath = null
            currentDocumentPath = null
            // 清理临时存储的密码
            tempPassword = null
            Log.d(TAG, "已清理密码相关缓存")
        }
    }

    private fun handleWindowContentChanged(event: AccessibilityEvent) {
        // 防止在密码填充过程中处理窗口内容改变事件，避免循环调用
        if (isFillingPassword) {
            Log.d(TAG, "正在填充密码中，跳过窗口内容改变事件处理")
            return
        }
        Log.d(TAG, "窗口内容改变: ${event.className}")
        val rootNode = rootInActiveWindow ?: return
        detectPasswordDialog(rootNode)
        detectDocumentPath(rootNode)
    }

    private fun handleViewFocused(event: AccessibilityEvent) {
        // 防止在密码填充过程中处理视图获得焦点事件，避免循环调用
        if (isFillingPassword) {
            Log.d(TAG, "正在填充密码中，跳过视图获得焦点事件处理")
            return
        }
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

        // 只有当同时找到密码输入框和确认按钮时，才认为是密码弹框
        val isPasswordDialog = passwordInputNodes.isNotEmpty() && confirmButton != null

        if (isPasswordDialog) {
            Log.d(
                TAG,
                "找到密码输入框: ${passwordInputNodes.size}，找到确认按钮: ${confirmButton != null}，判断为密码弹框"
            )

            // 检测对话框类型
            val previousDialogType = currentDialogType
            detectDialogType(rootNode)

            // 只有在对话框类型发生变化时才重置显示密码标志
            if (previousDialogType != currentDialogType) {
                showPasswordChecked = false
            }

            // 对于非修改密码对话框，尝试找到并点击【显示密码】选项
            if (currentDialogType != DialogType.MODIFY_PASSWORD) {
                findAndClickShowPasswordOption(rootNode)
            }

            // 对于修改密码对话框，启动显示密码监测
            if (currentDialogType == DialogType.MODIFY_PASSWORD) {
                // 只有当上次不是修改密码窗口时才启动监测，避免重复调用
                if (lastDialogType != DialogType.MODIFY_PASSWORD) {
                    startShowPasswordMonitoring()
                }
            } else {
                // 对于其他对话框类型，停止监测
                stopShowPasswordMonitoring()
            }

            // 更新上次的对话框类型
            lastDialogType = currentDialogType

            // 只有在【添加密码】或【修改密码】窗口时显示悬浮按钮
            if (currentDialogType == DialogType.ADD_PASSWORD || currentDialogType == DialogType.MODIFY_PASSWORD) {
                // 启动悬浮按钮服务
                startFloatingButtonService()
                // 显示悬浮按钮
                AccessibilityServiceManager.getInstance().showFloatingButton()
            } else {
                // 其他密码弹框（如打开加密文档）不显示悬浮按钮
                AccessibilityServiceManager.getInstance().hideFloatingButton()
                stopFloatingButtonService()
            }

            if (currentDialogType == DialogType.OPEN_ENCRYPTED_DOCUMENT) {
                // 尝试自动填充密码
                autoFillPassword(rootNode)
            }

        } else {
            // 隐藏悬浮按钮
            AccessibilityServiceManager.getInstance().hideFloatingButton()
            stopFloatingButtonService()
            // 停止显示密码监测
            stopShowPasswordMonitoring()
            // 重置上次的对话框类型
            lastDialogType = DialogType.UNKNOWN

            // 处理未确认的临时密码
            if (tempPassword != null && tempPassword!!.isNotEmpty()) {
                Log.i(
                    TAG,
                    "[时间戳: ${System.currentTimeMillis()}] 检测到密码弹框关闭，处理未确认的临时密码: '$tempPassword'"
                )
                // 更新待定密码到密码状态管理器
                val filePath = currentFileUri ?: stableDocumentPath
                if (filePath != null) {
                    FileMetaFactory.updatePendingPassword(filePath, tempPassword!!)
                    Log.d(
                        TAG,
                        "detectPasswordDialog-已更新待定密码到密码状态管理器: $filePath"
                    )
                }
            }

            // 重置对话框类型
            currentDialogType = DialogType.UNKNOWN
            // 重置显示密码标志，确保下次打开弹窗时重新勾选
            hasClickedShowPassword = false
            showPasswordChecked = false
            // 重置其他标志
            isDocumentOpened = false
            hasClickedGeneratePassword = false
            // 清除临时密码，避免下次打开弹窗时使用旧密码
            tempPassword = null
            // 当没有找到密码输入框时，不立即认为文档进程完全关闭
            // 只有在确定文档进程真正关闭时才清除密码和重置文档路径
            // 避免在打开【密码加密】等其他窗口时误判
            Log.d(TAG, "未检测到密码输入框，重置相关状态")
        }

        // 查找保存按钮
        val saveButton = findSaveButton(rootNode)
        if (saveButton != null) {
            Log.d(TAG, "找到保存按钮")
        }
    }

    /**
     * 检测对话框类型
     */
    private fun detectDialogType(rootNode: AccessibilityNodeInfo) {
        try {
            val queue = mutableListOf(rootNode)

            while (queue.isNotEmpty()) {
                val node = queue.removeAt(0)
                val text = node.text?.toString() ?: ""
                val contentDescription = node.contentDescription?.toString() ?: ""

                // 检测对话框标题或文本
                if (text.contains("文档已加密") || text.contains("Document is encrypted")) {
                    currentDialogType = DialogType.OPEN_ENCRYPTED_DOCUMENT
                    Log.d(TAG, "检测到对话框类型: 打开加密文档")
                    return
                } else if (text.contains("添加密码") || text.contains("Add Password") || text.contains(
                        "add password"
                    )
                ) {
                    currentDialogType = DialogType.ADD_PASSWORD
                    Log.d(TAG, "检测到对话框类型: 添加密码")
                    return
                } else if (text.contains("修改密码") || text.contains("Modify Password") || text.contains(
                        "modify password"
                    )
                ) {
                    currentDialogType = DialogType.MODIFY_PASSWORD
                    Log.d(TAG, "检测到对话框类型: 修改密码")
                    return
                }

                // 检查内容描述
                if (contentDescription.contains("文档已加密") || contentDescription.contains("Document is encrypted")) {
                    currentDialogType = DialogType.OPEN_ENCRYPTED_DOCUMENT
                    Log.d(TAG, "检测到对话框类型: 打开加密文档")
                    return
                } else if (contentDescription.contains("添加密码") || contentDescription.contains("Add Password") || contentDescription.contains(
                        "add password"
                    )
                ) {
                    currentDialogType = DialogType.ADD_PASSWORD
                    Log.d(TAG, "检测到对话框类型: 添加密码")
                    return
                } else if (contentDescription.contains("修改密码") || contentDescription.contains("Modify Password") || contentDescription.contains(
                        "modify password"
                    )
                ) {
                    currentDialogType = DialogType.MODIFY_PASSWORD
                    Log.d(TAG, "检测到对话框类型: 修改密码")
                    return
                }

                // 遍历子节点
                for (i in 0 until node.childCount) {
                    val child = node.getChild(i)
                    if (child != null) {
                        queue.add(child)
                    }
                }
            }

            // 默认类型
            currentDialogType = DialogType.UNKNOWN
            Log.d(TAG, "检测到对话框类型: 未知")
        } catch (e: Exception) {
            Log.e(TAG, "检测对话框类型失败", e)
            currentDialogType = DialogType.UNKNOWN
        }
    }

    /**
     * 查找并点击【显示密码】选项
     */
    private fun findAndClickShowPasswordOption(rootNode: AccessibilityNodeInfo) {
        try {
            val queue = mutableListOf(rootNode)
            var foundShowPasswordOption = false

            Log.d(
                TAG,
                "开始查找【显示密码】选项，根节点: ${rootNode.className}, 子节点数量: ${rootNode.childCount}"
            )

            while (queue.isNotEmpty()) {
                val node = queue.removeAt(0)

//                Log.d(TAG, "遍历节点: ${node.className}, 子节点数量: ${node.childCount}")

                // 检查是否是【显示密码】选项的容器
                val text = node.text?.toString() ?: ""
                val contentDescription = node.contentDescription?.toString() ?: ""

//                Log.d(TAG, "节点文本: '$text', 内容描述: '$contentDescription'")

                val hasShowPasswordText =
                    text.contains("显示密码") || text.contains("show password") ||
                            text.contains("Show Password") || contentDescription.contains("显示密码") ||
                            contentDescription.contains("show password") || contentDescription.contains(
                        "Show Password"
                    )

                // 检查是否是复选框或开关
                val isCheckboxOrSwitch =
                    node.className?.toString()?.contains("CheckBox") ?: false ||
                            node.className?.toString()?.contains("Switch") ?: false ||
                            node.className?.toString()?.contains("Toggle") ?: false

                // 检查是否是清理按钮，避免误点击
                val isClearButton =
                    text.contains("清理") || text.contains("clear") || text.contains("Clear") ||
                            contentDescription.contains("清理") || contentDescription.contains("clear") || contentDescription.contains(
                        "Clear"
                    )

                if ((hasShowPasswordText || isCheckboxOrSwitch) && !isClearButton) {
                    foundShowPasswordOption = true
                    Log.d(TAG, "找到可能的【显示密码】选项: $text")
                    Log.d(TAG, "选项类名: ${node.className}")
                    Log.d(TAG, "是否可点击: ${node.isClickable}")
                    Log.d(TAG, "是否可聚焦: ${node.isFocusable}")
                    Log.d(TAG, "当前对话框类型: $currentDialogType")
                    Log.d(TAG, "是否已经点击过: $hasClickedShowPassword")

                    // 对于修改密码对话框，持续监测并确保【显示密码】选项处于勾选状态
                    if (currentDialogType == DialogType.MODIFY_PASSWORD) {
                        // 只在showPasswordChecked为false时点击，避免重复操作
                        if (node.isClickable && !showPasswordChecked) {
                            Log.d(TAG, "修改密码对话框中【显示密码】选项可点击，尝试点击")
                            val success = node.performAction(AccessibilityNodeInfo.ACTION_CLICK)
                            if (success) {
                                Log.i(TAG, "成功点击【显示密码】选项")
                                showPasswordChecked = true
                            } else {
                                Log.e(TAG, "直接点击【显示密码】选项失败")
                            }
                        }
                    } else {
                        // 对于其他对话框类型，只在未点击过时点击
                        if (!hasClickedShowPassword && node.isClickable) {
                            val success = node.performAction(AccessibilityNodeInfo.ACTION_CLICK)
                            if (success) {
                                Log.i(TAG, "成功点击【显示密码】选项")
                                hasClickedShowPassword = true
                            } else {
                                Log.e(TAG, "直接点击【显示密码】选项失败")
                            }
                        }
                    }

                    // 尝试点击子节点
                    for (i in 0 until node.childCount) {
                        val child = node.getChild(i)
                        if (child != null && child.isClickable) {
                            Log.d(TAG, "尝试点击子节点: ${child.className}")
                            if (currentDialogType == DialogType.MODIFY_PASSWORD) {
                                if (!showPasswordChecked) {
                                    val success =
                                        child.performAction(AccessibilityNodeInfo.ACTION_CLICK)
                                    if (success) {
                                        Log.i(TAG, "成功点击【显示密码】选项的子节点")
                                        showPasswordChecked = true
                                    } else {
                                        Log.e(TAG, "点击【显示密码】选项的子节点失败")
                                    }
                                }
                            } else {
                                if (!hasClickedShowPassword) {
                                    val success =
                                        child.performAction(AccessibilityNodeInfo.ACTION_CLICK)
                                    if (success) {
                                        Log.i(TAG, "成功点击【显示密码】选项的子节点")
                                        hasClickedShowPassword = true
                                    } else {
                                        Log.e(TAG, "点击【显示密码】选项的子节点失败")
                                    }
                                }
                            }
                        }
                    }

                    // 尝试点击父节点
                    val parent = node.parent
                    if (parent != null && parent.isClickable) {
                        Log.d(TAG, "尝试点击父节点: ${parent.className}")
                        if (currentDialogType == DialogType.MODIFY_PASSWORD) {
                            if (!showPasswordChecked) {
                                val success =
                                    parent.performAction(AccessibilityNodeInfo.ACTION_CLICK)
                                if (success) {
                                    Log.i(TAG, "成功点击【显示密码】选项的父节点")
                                    showPasswordChecked = true
                                } else {
                                    Log.e(TAG, "点击【显示密码】选项的父节点失败")
                                }
                            }
                        } else {
                            if (!hasClickedShowPassword) {
                                val success =
                                    parent.performAction(AccessibilityNodeInfo.ACTION_CLICK)
                                if (success) {
                                    Log.i(TAG, "成功点击【显示密码】选项的父节点")
                                    hasClickedShowPassword = true
                                } else {
                                    Log.e(TAG, "点击【显示密码】选项的父节点失败")
                                }
                            }
                        }
                    }

                    // 找到【显示密码】选项后，不再继续搜索
                    if (currentDialogType == DialogType.MODIFY_PASSWORD && showPasswordChecked) {
                        Log.d(TAG, "已找到并勾选【显示密码】选项，停止搜索")
                        break
                    } else if (hasClickedShowPassword) {
                        Log.d(TAG, "已找到并勾选【显示密码】选项，停止搜索")
                        break
                    }
                }

                // 遍历子节点
                for (i in 0 until node.childCount) {
                    val child = node.getChild(i)
                    if (child != null) {
//                        Log.d(TAG, "添加子节点到队列: ${child.className}")
                        queue.add(child)
                    }
                }
            }

            if (!foundShowPasswordOption) {
                Log.d(TAG, "未找到【显示密码】选项")
            }
        } catch (e: Exception) {
            Log.e(TAG, "查找并点击【显示密码】选项失败", e)
        }
    }

    /**
     * 启动显示密码监测
     */
    private fun startShowPasswordMonitoring() {
        try {
            if (!isMonitoringShowPassword && currentDialogType == DialogType.MODIFY_PASSWORD) {
                Log.d(TAG, "开始监测显示密码勾选框")
                isMonitoringShowPassword = true

                // 创建并启动监测线程
                showPasswordMonitorThread = Thread {
                    try {
                        // 在线程循环外获取显示密码节点集合
                        val rootNode = rootInActiveWindow
                        val showPasswordNodes = if (rootNode != null) {
                            findMFShowPassword(rootNode).toMutableList()
                        } else {
                            mutableListOf()
                        }

                        Log.d(TAG, "初始化显示密码节点集合，大小: ${showPasswordNodes.size}")

                        while (isMonitoringShowPassword) {
                            Thread.sleep(200) // 每200ms检查一次

                            // 检查节点集合是否为空
                            if (showPasswordNodes.isEmpty()) {
                                Log.d(TAG, "显示密码节点集合为空，停止监测")
                                break
                            }

                            val currentRootNode = rootInActiveWindow
                            if (currentRootNode != null) {
                                // 重新检测对话框类型，确保仍然是修改密码弹窗
                                detectDialogType(currentRootNode)

                                if (currentDialogType == DialogType.MODIFY_PASSWORD) {
                                    // 遍历节点集合并执行点击操作
                                    val iterator = showPasswordNodes.iterator()
                                    while (iterator.hasNext()) {
                                        val node = iterator.next()
                                        val success = clickMFShowPassword(node)
                                        if (success) {
                                            Log.d(TAG, "成功点击显示密码节点，从集合中移除")
                                            iterator.remove()
                                        } else {
                                            Log.d(TAG, "点击显示密码节点失败，继续下一个")
                                        }
                                    }

                                    // 当所有显示密码节点都已处理完成，停止监测
                                    if (showPasswordNodes.isEmpty()) {
                                        Log.d(TAG, "所有显示密码节点都已处理完成，停止监测")
                                        break
                                    }
                                } else {
                                    // 当前窗口不是修改密码窗口，停止监测
                                    Log.d(TAG, "当前窗口不是修改密码窗口，停止监测")
                                    break
                                }
                            } else {
                                // 根节点为空，说明窗口已关闭
                                Log.d(TAG, "根节点为空，窗口已关闭，停止监测")
                                break
                            }
                        }
                    } catch (e: Exception) {
                        Log.e(TAG, "监测线程异常", e)
                    } finally {
                        isMonitoringShowPassword = false
                        Log.d(TAG, "显示密码监测线程结束")
                    }
                }

                showPasswordMonitorThread?.start()
                Log.d(TAG, "显示密码监测线程已启动")
            }
        } catch (e: Exception) {
            Log.e(TAG, "启动显示密码监测失败", e)
            isMonitoringShowPassword = false
        }
    }


    /**
     * 查找【显示密码】元素节点
     * @return Set<AccessibilityNodeInfo> 找到的显示密码节点集合
     */
    private fun findMFShowPassword(rootNode: AccessibilityNodeInfo): Set<AccessibilityNodeInfo> {
        val showPasswordNodes = mutableSetOf<AccessibilityNodeInfo>()

        try {
            val queue = mutableListOf(rootNode)

            Log.d(
                TAG,
                "开始查找【显示密码】节点，根节点: ${rootNode.className}, 子节点数量: ${rootNode.childCount}"
            )

            while (queue.isNotEmpty()) {
                val node = queue.removeAt(0)

                Log.d(TAG, "遍历节点: ${node.className}, 子节点数量: ${node.childCount}")

                // 检查是否是【显示密码】选项
                val text = node.text?.toString() ?: ""
                val contentDescription = node.contentDescription?.toString() ?: ""

                Log.d(TAG, "节点文本: '$text', 内容描述: '$contentDescription'")

                val hasShowPasswordText =
                    text.contains("显示密码") || text.contains("show password") ||
                            text.contains("Show Password") || contentDescription.contains("显示密码") ||
                            contentDescription.contains("show password") || contentDescription.contains(
                        "Show Password"
                    )

                if (hasShowPasswordText) {
                    Log.d(
                        TAG,
                        "找到【显示密码】文本节点: ${node.className}, 父节点: ${node.parent?.className}"
                    )
                    showPasswordNodes.add(node)
                }

                // 检查是否是复选框或开关
                val isCheckboxOrSwitch =
                    node.className?.toString()?.contains("CheckBox") ?: false ||
                            node.className?.toString()?.contains("Switch") ?: false ||
                            node.className?.toString()?.contains("Toggle") ?: false

                if (isCheckboxOrSwitch) {
                    Log.d(
                        TAG,
                        "找到复选框/开关节点: ${node.className}, 父节点: ${node.parent?.className}"
                    )
                }

                // 遍历子节点
                for (i in 0 until node.childCount) {
                    val child = node.getChild(i)
                    if (child != null) {
//                        Log.d(TAG, "添加子节点到队列: ${child.className}")
                        queue.add(child)
                    }
                }
            }

            Log.d(TAG, "查找完成，找到 ${showPasswordNodes.size} 个【显示密码】节点")
        } catch (e: Exception) {
            Log.e(TAG, "查找【显示密码】节点失败", e)
        }

        return showPasswordNodes
    }

    /**
     * 点击【显示密码】元素节点
     * @return Boolean 是否点击成功
     */
    private fun clickMFShowPassword(node: AccessibilityNodeInfo): Boolean {
        var success = false

        try {
            Log.d(TAG, "处理【显示密码】节点: ${node.className}")

            // 尝试找到关联的复选框
            val checkboxNode = findAssociatedCheckbox(node)
            if (checkboxNode != null) {
                Log.d(
                    TAG,
                    "找到关联的复选框: ${checkboxNode.className}, 可点击: ${checkboxNode.isClickable}, 当前状态: ${checkboxNode.isChecked}"
                )

                // 执行点击操作
                if (checkboxNode.isClickable) {
                    success = checkboxNode.performAction(AccessibilityNodeInfo.ACTION_CLICK)
                    if (success) {
                        Log.i(TAG, "成功点击【显示密码】复选框")
                    } else {
                        Log.e(TAG, "点击【显示密码】复选框失败")
                    }
                }
            } else {
                // 尝试直接点击节点
                if (node.isClickable) {
                    success = node.performAction(AccessibilityNodeInfo.ACTION_CLICK)
                    if (success) {
                        Log.i(TAG, "成功点击【显示密码】节点")
                    } else {
                        Log.e(TAG, "点击【显示密码】节点失败")
                    }
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "处理【显示密码】节点失败", e)
        }

        return success
    }

    /**
     * 查找与显示密码文本关联的复选框
     */
    private fun findAssociatedCheckbox(textNode: AccessibilityNodeInfo): AccessibilityNodeInfo? {
        // 检查兄弟节点
        val parent = textNode.parent
        if (parent != null) {
            for (i in 0 until parent.childCount) {
                val sibling = parent.getChild(i)
                if (sibling != null && sibling != textNode) {
                    val className = sibling.className?.toString() ?: ""
                    if (className.contains("CheckBox") || className.contains("Switch") || className.contains(
                            "Toggle"
                        )
                    ) {
                        return sibling
                    }
                }
            }
        }
        return null
    }

    /**
     * 停止显示密码监测
     */
    private fun stopShowPasswordMonitoring() {
        try {
            if (isMonitoringShowPassword) {
                Log.d(TAG, "停止显示密码监测")
                isMonitoringShowPassword = false

                // 等待监测线程结束
                showPasswordMonitorThread?.join(1000) // 最多等待1秒
                showPasswordMonitorThread = null

                // 不重置showPasswordChecked，保持当前状态
                Log.d(TAG, "显示密码监测已停止")
            }
        } catch (e: Exception) {
            Log.e(TAG, "停止显示密码监测失败", e)
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

            // 【添加密码】和【修改密码】弹窗不需要自动填充密码，只需要实现【显示密码】选项的自动勾选功能
            if (currentDialogType == DialogType.ADD_PASSWORD || currentDialogType == DialogType.MODIFY_PASSWORD) {
                Log.d(TAG, "添加密码或修改密码弹窗，跳过自动填充")
                return
            }

            // 检测文档路径（减少日志输出）
            detectDocumentPath(rootNode, false)

            // 从FileMeta中获取密码和uid
            var password: String? = null
            val filePath = currentFileUri ?: stableDocumentPath
            if (filePath != null) {
                val fileMeta = FileMetaFactory.getFileMeta(filePath)
                if (fileMeta != null) {
                    password = fileMeta.currentPassword
                    Log.i(TAG, "从FileMeta读取密码: $password")
                }
            }

            // 初始化密码状态
            // 如果找到密码，自动填充
            if (password != null && password.isNotEmpty()) {
                // 检查是否需要根据权限属性决定是否执行自动填充
                var shouldAutoFill = false
                if (currentDialogType == DialogType.OPEN_ENCRYPTED_DOCUMENT && filePath != null) {
                    val fileMeta = FileMetaFactory.getFileMeta(filePath)
                    if (fileMeta != null) {
                        // 根据readAuth和writeAuth权限属性决定是否执行自动填充
                        shouldAutoFill = fileMeta.readAuth || fileMeta.writeAuth
                        Log.i(TAG, "文件权限检查: readAuth=${fileMeta.readAuth}, writeAuth=${fileMeta.writeAuth}, 应自动填充=$shouldAutoFill")
                    } else {
                        Log.d(TAG, "未找到文件元数据，无法判断权限")
                    }
                }

                if (shouldAutoFill) {
                    Log.i(TAG, "开始自动填充密码")

                    val passwordInputNodes = findPasswordInputNodes(rootNode)
                    if (passwordInputNodes.isNotEmpty()) {
                        isFillingPassword = true
                        try {
                            // 对于其他窗口，填充所有密码输入框
                            for (node in passwordInputNodes) {
                                try {
                                    // 填充密码
                                    val arguments = android.os.Bundle()
                                    arguments.putCharSequence(
                                        AccessibilityNodeInfo.ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE,
                                        password
                                    )
                                    val success = node.performAction(
                                        AccessibilityNodeInfo.ACTION_SET_TEXT,
                                        arguments
                                    )
                                    if (success) {
                                        Log.i(TAG, "密码填充成功")

                                        // 检查是否需要自动点击确认按钮
                                        // 只有在首次打开加密文件的场景中才自动提交
                                        val shouldAutoSubmit =
                                            !hasClickedGeneratePassword && (currentDialogType == DialogType.OPEN_ENCRYPTED_DOCUMENT)

                                        Log.i(
                                            TAG,
                                            "是否自动提交: $shouldAutoSubmit, 场景类型: ${if (hasClickedGeneratePassword) "生成密码" else if (currentDialogType == DialogType.OPEN_ENCRYPTED_DOCUMENT) "首次打开" else "修改密码"}"
                                        )

                                        if (shouldAutoSubmit) {
                                            // 场景1：首次打开加密文件，自动点击确认按钮
                                            Log.i(TAG, "尝试自动点击确认按钮")
                                            val confirmButton = findConfirmButton(rootNode)
                                            if (confirmButton != null) {
                                                Log.i(TAG, "找到确认按钮，尝试点击")
                                                val clickSuccess =
                                                    confirmButton.performAction(AccessibilityNodeInfo.ACTION_CLICK)
                                                if (clickSuccess) {
                                                    Log.i(TAG, "autoFillPassword-成功点击确认按钮")
                                                    isDocumentOpened = true // 标记文档已打开
                                                } else {
                                                    Log.e(TAG, "点击确认按钮失败")
                                                }
                                            } else {
                                                Log.d(TAG, "未找到确认按钮")
                                            }
                                        } else {
                                            Log.i(TAG, "此场景不自动提交，保持窗口打开")
                                        }

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
                    Log.i(TAG, "权限不足，不执行自动填充，等待用户手动输入密码")
                }
            } else {
                Log.d(TAG, "未找到密码，等待用户手动输入")
            }
        } catch (e: Exception) {
            Log.e(TAG, "自动填充密码失败", e)
            isFillingPassword = false
        }
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
        if (stableDocumentPath == null || (isRealFilePath && (!stableDocumentPath!!.contains(".doc") && !stableDocumentPath!!.contains(
                ".xls"
            ) && !stableDocumentPath!!.contains(".ppt") && !stableDocumentPath!!.contains("/storage/")))
        ) {
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
            if (text.isNotEmpty() && (text.contains("/storage/") || text.contains("SD卡") || text.contains(
                    "Internal storage"
                )) &&
                (text.contains(".doc") || text.contains(".docx") || text.contains(".xls") ||
                        text.contains(".xlsx") || text.contains(".ppt") || text.contains(".pptx"))
            ) {
                Log.d(TAG, "从系统元素找到文件路径: $text")
                return text
            }

            if (contentDescription.isNotEmpty() && (contentDescription.contains("/storage/") || contentDescription.contains(
                    "SD卡"
                ) || contentDescription.contains("Internal storage")) &&
                (contentDescription.contains(".doc") || contentDescription.contains(".docx") || contentDescription.contains(
                    ".xls"
                ) ||
                        contentDescription.contains(".xlsx") || contentDescription.contains(".ppt") || contentDescription.contains(
                    ".pptx"
                ))
            ) {
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
                className.contains("ActionBar") || className.contains("action_bar")
            ) {

                // 检查文本是否包含文件路径特征
                if (text.isNotEmpty() && (text.contains(".doc") || text.contains(".docx") ||
                            text.contains(".xls") || text.contains(".xlsx") ||
                            text.contains(".ppt") || text.contains(".pptx"))
                ) {
                    Log.d(TAG, "从导航栏找到文件路径: $text")
                    return text
                }

                // 检查内容描述
                if (contentDescription.isNotEmpty() && (contentDescription.contains(".doc") || contentDescription.contains(
                        ".docx"
                    ) ||
                            contentDescription.contains(".xls") || contentDescription.contains(".xlsx") ||
                            contentDescription.contains(".ppt") || contentDescription.contains(".pptx"))
                ) {
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
            if (text.isNotEmpty() && (text.contains("路径") || text.contains("Path") || text.contains(
                    "path"
                )) &&
                (text.contains(".doc") || text.contains(".docx") ||
                        text.contains(".xls") || text.contains(".xlsx") ||
                        text.contains(".ppt") || text.contains(".pptx") ||
                        text.contains("/storage/") || text.contains("SD卡") ||
                        text.contains("Internal storage") || text.contains("file:/"))
            ) {
                Log.d(TAG, "从文件属性区域找到文件路径: $text")
                return text
            }

            if (contentDescription.isNotEmpty() && (contentDescription.contains("路径") || contentDescription.contains(
                    "Path"
                ) || contentDescription.contains("path")) &&
                (contentDescription.contains(".doc") || contentDescription.contains(".docx") ||
                        contentDescription.contains(".xls") || contentDescription.contains(".xlsx") ||
                        contentDescription.contains(".ppt") || contentDescription.contains(".pptx") ||
                        contentDescription.contains("/storage/") || contentDescription.contains("SD卡") ||
                        contentDescription.contains("Internal storage") || contentDescription.contains(
                    "file:/"
                ))
            ) {
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
                        text.contains(".ppt") || text.contains(".pptx"))
            ) {
                Log.d(TAG, "从WPS找到文件名: $text")
                return text
            }

            if (contentDescription.isNotEmpty() && (contentDescription.contains(".doc") || contentDescription.contains(
                    ".docx"
                ) ||
                        contentDescription.contains(".xls") || contentDescription.contains(".xlsx") ||
                        contentDescription.contains(".ppt") || contentDescription.contains(".pptx"))
            ) {
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
                className.contains("Bar") || className.contains("bar")
            ) {

                // 检查文本是否包含文件路径特征
                if (text.isNotEmpty() && (
                            text.contains(".doc") || text.contains(".docx") ||
                                    text.contains(".xls") || text.contains(".xlsx") ||
                                    text.contains(".ppt") || text.contains(".pptx")
                            )
                ) {
                    Log.d(TAG, "从状态栏找到文件路径: $text")
                    return text
                }

                // 检查内容描述
                if (contentDescription.isNotEmpty() && (
                            contentDescription.contains(".doc") || contentDescription.contains(".docx") ||
                                    contentDescription.contains(".xls") || contentDescription.contains(
                                ".xlsx"
                            ) ||
                                    contentDescription.contains(".ppt") || contentDescription.contains(
                                ".pptx"
                            )
                            )
                ) {
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
                        )
            ) {
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
                        )
            ) {
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
                className.contains("Toolbar") || className.contains("toolbar")
            ) {

                // 检查文本是否包含文件路径或文件名
                if (text.isNotEmpty()) {
                    // 检查是否包含文件扩展名
                    if (text.contains(".doc") || text.contains(".docx") ||
                        text.contains(".xls") || text.contains(".xlsx") ||
                        text.contains(".ppt") || text.contains(".pptx")
                    ) {
                        Log.d(TAG, "从WPS特定元素找到文件路径: $text")
                        return text
                    }
                }

                // 检查内容描述
                if (contentDescription.isNotEmpty()) {
                    // 检查是否包含文件扩展名
                    if (contentDescription.contains(".doc") || contentDescription.contains(".docx") ||
                        contentDescription.contains(".xls") || contentDescription.contains(".xlsx") ||
                        contentDescription.contains(".ppt") || contentDescription.contains(".pptx")
                    ) {
                        Log.d(TAG, "从WPS特定元素内容描述找到文件路径: $contentDescription")
                        return contentDescription
                    }
                }
            }

            // 检查是否是文件路径相关的节点
            if (text.isNotEmpty() && (text.contains("/storage/") || text.contains("SD卡") || text.contains(
                    "Internal storage"
                )) &&
                (text.contains(".doc") || text.contains(".docx") || text.contains(".xls") ||
                        text.contains(".xlsx") || text.contains(".ppt") || text.contains(".pptx"))
            ) {
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
                    text.contains(".ppt") || text.contains(".pptx")
                ) {
                    Log.d(TAG, "从标题栏找到文件路径: $text")
                    return text
                }

                // 检查是否包含存储路径
                if (text.contains("/storage/") || text.contains("SD卡") ||
                    text.contains("Internal storage")
                ) {
                    Log.d(TAG, "从标题栏找到存储路径: $text")
                    return text
                }
            }

            if (contentDescription.isNotEmpty()) {
                // 检查是否包含文件扩展名
                if (contentDescription.contains(".doc") || contentDescription.contains(".docx") ||
                    contentDescription.contains(".xls") || contentDescription.contains(".xlsx") ||
                    contentDescription.contains(".ppt") || contentDescription.contains(".pptx")
                ) {
                    Log.d(TAG, "从标题栏内容描述找到文件路径: $contentDescription")
                    return contentDescription
                }

                // 检查是否包含存储路径
                if (contentDescription.contains("/storage/") || contentDescription.contains("SD卡") ||
                    contentDescription.contains("Internal storage")
                ) {
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
                        className.contains("bar") || className.contains("Bar")
                    ) {
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
                            text.contains("SD卡") || text.contains("Internal storage"))
                ) {
                    return text
                }
                // 检查文本是否只是文件名（包含扩展名）
                if (text.contains(".doc") || text.contains(".docx") ||
                    text.contains(".xls") || text.contains(".xlsx") ||
                    text.contains(".ppt") || text.contains(".pptx")
                ) {
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
                            contentDescription.contains("Internal storage"))
                ) {
                    return contentDescription
                }
                // 检查内容描述是否只是文件名（包含扩展名）
                if (contentDescription.contains(".doc") || contentDescription.contains(".docx") ||
                    contentDescription.contains(".xls") || contentDescription.contains(".xlsx") ||
                    contentDescription.contains(".ppt") || contentDescription.contains(".pptx")
                ) {
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
        try {
            if (!isFloatingButtonServiceStarted) {
                val intent =
                    Intent(this, com.wpspasswordmanager.ui.FloatingButtonService::class.java)
                if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
                    startForegroundService(intent)
                } else {
                    startService(intent)
                }
                isFloatingButtonServiceStarted = true
                Log.d(TAG, "悬浮按钮服务启动成功")
            } else {
                Log.d(TAG, "悬浮按钮服务已经启动")
            }
        } catch (e: Exception) {
            Log.e(TAG, "启动悬浮按钮服务失败", e)
        }
    }

    private fun stopFloatingButtonService() {
        try {
            // 先隐藏悬浮按钮
            AccessibilityServiceManager.getInstance().hideFloatingButton()

            if (isFloatingButtonServiceStarted) {
                val intent =
                    Intent(this, com.wpspasswordmanager.ui.FloatingButtonService::class.java)
                stopService(intent)
                isFloatingButtonServiceStarted = false
                Log.d(TAG, "悬浮按钮服务停止成功")
            } else {
                Log.d(TAG, "悬浮按钮服务已经停止")
            }
        } catch (e: Exception) {
            Log.e(TAG, "停止悬浮按钮服务失败", e)
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
                contentDescription.contains("PASSWORD") || contentDescription.contains("Pass") || contentDescription.contains(
            "pass"
        ) ||
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
        val isConfirmId =
            viewId.contains("confirm") || viewId.contains("ok") || viewId.contains("button1")

        // 综合判断
        val result = (isButton && hasConfirmText) || isConfirmId
        if (result) {
            Log.d(
                TAG,
                "确认按钮识别成功: 类名=$className, 文本=$text, 内容描述=$contentDescription, ID=$viewId"
            )
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
            Log.i(TAG, "当前对话框类型: $currentDialogType")
            showOperationNotification("密码填充", "正在填充密码...")

            if (passwordInputNodes.isNotEmpty()) {
                var fillSuccess = false
                isFillingPassword = true
                try {
                    // 根据对话框类型决定填充策略
                    val nodesToFill =
                        if (currentDialogType == DialogType.ADD_PASSWORD || currentDialogType == DialogType.MODIFY_PASSWORD) {
                            // 对于添加密码窗口，只填充前两个输入框（假设是【打开权限】部分）
                            Log.i(TAG, "添加密码窗口，只填充前两个密码输入框")
                            passwordInputNodes.take(2)
                        } else {
                            // 对于其他窗口，填充所有密码输入框
                            passwordInputNodes
                        }

                    for (node in nodesToFill) {
                        try {
                            // 填充密码
                            val arguments = android.os.Bundle()
                            arguments.putCharSequence(
                                AccessibilityNodeInfo.ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE,
                                password
                            )
                            val success =
                                node.performAction(AccessibilityNodeInfo.ACTION_SET_TEXT, arguments)
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
                        // 临时存储密码
                        tempPassword = password
                        Log.i(TAG, "已将密码临时存储，等待用户确认: '$password'")
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
