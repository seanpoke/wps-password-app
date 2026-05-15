package com.wpspasswordmanager.ui

import android.app.Service
import android.content.Intent
import android.graphics.PixelFormat
import android.os.Build
import android.os.IBinder
import android.provider.Settings
import android.util.Log
import android.view.Gravity
import android.view.LayoutInflater
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import android.widget.Button
import android.widget.Toast
import com.wpspasswordmanager.R
import com.wpspasswordmanager.business.FileMetaFactory
import com.wpspasswordmanager.business.FileMetaManager
import com.wpspasswordmanager.business.PasswordGenerator
import com.wpspasswordmanager.monitor.AccessibilityServiceManager
import com.wpspasswordmanager.monitor.WpsAccessibilityService
import com.wpspasswordmanager.network.NetworkCallback
import com.wpspasswordmanager.network.NetworkManager
import com.wpspasswordmanager.storage.ConfigStorage

class FloatingButtonService : Service() {

    companion object {
        private const val TAG = "FloatingButtonService"
        private const val DEFAULT_MARGIN_RATIO = 0.05
        private const val VERTICAL_POSITION_RATIO = 0.18
        private const val MIN_MARGIN_DP = 16
    }

    data class ScreenMetrics(val width: Int, val height: Int)
    data class Position(val x: Int, val y: Int)

    private lateinit var windowManager: WindowManager
    private lateinit var floatingView: View
    private var isFloatingButtonVisible = false

    override fun onBind(intent: Intent?): IBinder? {
        return null
    }

    override fun onCreate() {
        super.onCreate()
        Log.d(TAG, "悬浮按钮服务创建")
        // 注册服务到管理器
        AccessibilityServiceManager.getInstance().setFloatingButtonService(this)
        // 启动前台服务
        startForegroundService()
        // 不自动初始化悬浮按钮，只在需要时通过showFloatingButton方法显示
    }

    private fun startForegroundService() {
        try {
            // 使用AppNotificationManager创建通知，确保通知频道已创建
            val notification = androidx.core.app.NotificationCompat.Builder(this, "operation_channel")
                .setContentTitle("文档密码管理")
                .setContentText("悬浮按钮服务正在运行")
                .setSmallIcon(R.mipmap.ic_launcher)
                .setPriority(androidx.core.app.NotificationCompat.PRIORITY_LOW)
                .build()

            // 确保通知频道已创建
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
                val notificationManager = getSystemService(android.app.NotificationManager::class.java)
                val channel = android.app.NotificationChannel(
                    "operation_channel",
                    "操作通知",
                    android.app.NotificationManager.IMPORTANCE_LOW
                )
                channel.description = "显示应用操作状态"
                notificationManager.createNotificationChannel(channel)
            }

            startForeground(1, notification)
            Log.d(TAG, "前台服务启动成功")
        } catch (e: Exception) {
            Log.e(TAG, "启动前台服务失败", e)
            // 即使失败也要继续运行服务
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        Log.d(TAG, "悬浮按钮服务启动")

        // 确保前台服务已启动
        startForegroundService()

        // 不自动初始化悬浮按钮，只在需要时通过showFloatingButton方法显示

        return START_STICKY
    }

    override fun onDestroy() {
        super.onDestroy()
        Log.d(TAG, "悬浮按钮服务销毁")
        // 强制移除悬浮按钮，无论isFloatingButtonVisible的状态如何
        try {
            if (::windowManager.isInitialized && ::floatingView.isInitialized) {
                // 尝试移除视图，即使可能已经被移除
                try {
                    windowManager.removeView(floatingView)
                    isFloatingButtonVisible = false
                    Log.d(TAG, "悬浮按钮移除成功")
                } catch (e: IllegalArgumentException) {
                    // 视图可能已经被移除，这是正常的
                    Log.d(TAG, "悬浮按钮视图已不存在，无需移除")
                    isFloatingButtonVisible = false
                } catch (e: Exception) {
                    Log.e(TAG, "移除悬浮按钮失败", e)
                    isFloatingButtonVisible = false
                }
            } else {
                Log.d(TAG, "悬浮按钮未初始化，无需移除")
                isFloatingButtonVisible = false
            }

            // 移除权限面板
            removePermissionPanel()
        } catch (e: Exception) {
            Log.e(TAG, "移除悬浮按钮时发生异常", e)
            isFloatingButtonVisible = false
        } finally {
            // 清除AccessibilityServiceManager中的引用，确保下次启动服务时能够正确初始化
            AccessibilityServiceManager.getInstance().setFloatingButtonService(null)
            Log.d(TAG, "已清除AccessibilityServiceManager中的悬浮按钮服务引用")
        }
    }

    private fun getScreenMetrics(): ScreenMetrics {
        val displayMetrics = resources.displayMetrics
        val screenWidth = displayMetrics.widthPixels
        val screenHeight = displayMetrics.heightPixels
        return ScreenMetrics(screenWidth, screenHeight)
    }

    private fun calculateOptimalPosition(screenMetrics: ScreenMetrics): Position {
        val density = resources.displayMetrics.density
        val minMargin = (MIN_MARGIN_DP * density).toInt()

        val x = minMargin
        val y = (screenMetrics.height * VERTICAL_POSITION_RATIO).toInt()

        return Position(x, y)
    }

    private fun constrainPositionToScreen(params: WindowManager.LayoutParams, view: View) {
        val screenMetrics = getScreenMetrics()
        val density = resources.displayMetrics.density
        val minMargin = (MIN_MARGIN_DP * density).toInt()

        val viewWidth = view.width
        val viewHeight = view.height

        val maxX = screenMetrics.width - viewWidth - minMargin
        val maxY = screenMetrics.height - viewHeight - minMargin

        params.x = params.x.coerceIn(minMargin, maxX.coerceAtLeast(minMargin))
        params.y = params.y.coerceIn(minMargin, maxY.coerceAtLeast(minMargin))
    }

    private fun initFloatingButton() {
        // 检查权限
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            if (!Settings.canDrawOverlays(this)) {
                Log.e(TAG, "没有显示在其他应用之上的权限")
                showPermissionNotification()
                return
            }
        }

        windowManager = getSystemService(WINDOW_SERVICE) as WindowManager
        val inflater = getSystemService(LAYOUT_INFLATER_SERVICE) as LayoutInflater
        floatingView = inflater.inflate(R.layout.floating_button, null)

        val params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
            } else {
                WindowManager.LayoutParams.TYPE_PHONE
            },
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
            WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL or
            WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON or
            WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
            PixelFormat.TRANSLUCENT
        )

        params.gravity = Gravity.TOP or Gravity.END
        val screenMetrics = getScreenMetrics()
        val initialPosition = calculateOptimalPosition(screenMetrics)
        params.x = initialPosition.x
        params.y = initialPosition.y

        val generatePasswordButton = floatingView.findViewById<Button>(R.id.generate_password_button)
        generatePasswordButton.setOnClickListener {
            generateAndFillPassword()
        }

        val viewPasswordButton = floatingView.findViewById<Button>(R.id.view_password_button)
        viewPasswordButton.setOnClickListener {
            viewPassword()
        }

        val documentPermissionButton = floatingView.findViewById<Button>(R.id.document_permission_button)
        documentPermissionButton.setOnClickListener {
            showDocumentPermissionDialog()
        }

        // 控制【显示文档权限】按钮的显示逻辑
        val documentPath = com.wpspasswordmanager.monitor.WpsAccessibilityService.stableDocumentPath
        if (documentPath != null && documentPath.isNotEmpty()) {
            val fileMeta = com.wpspasswordmanager.business.FileMetaFactory.getFileMeta(documentPath)
            if (fileMeta?.writeAuth != true) {
                documentPermissionButton.visibility = android.view.View.GONE
            } else {
                documentPermissionButton.visibility = android.view.View.VISIBLE
            }
        } else {
            documentPermissionButton.visibility = android.view.View.GONE
        }

        // 添加触摸事件，实现悬浮按钮的拖动
        floatingView.setOnTouchListener { v, event ->
            when (event.action) {
                MotionEvent.ACTION_DOWN -> {
                    // 开始拖动
                }
                MotionEvent.ACTION_MOVE -> {
                    // 更新位置
                    try {
                        params.x = event.rawX.toInt() - floatingView.width / 2
                        params.y = event.rawY.toInt() - floatingView.height / 2
                        constrainPositionToScreen(params, floatingView)
                        windowManager.updateViewLayout(floatingView, params)
                    } catch (e: Exception) {
                        Log.e(TAG, "更新悬浮按钮位置失败", e)
                    }
                }
                MotionEvent.ACTION_UP -> {
                    // 结束拖动
                }
            }
            true
        }

        try {
            windowManager.addView(floatingView, params)
            constrainPositionToScreen(params, floatingView)
            windowManager.updateViewLayout(floatingView, params)
            isFloatingButtonVisible = true
            Log.d(TAG, "悬浮按钮添加成功")
        } catch (e: Exception) {
            Log.e(TAG, "添加悬浮按钮失败", e)
        }
    }

    private fun removeFloatingButton() {
        // 无论isFloatingButtonVisible的状态如何，都尝试移除悬浮按钮
        try {
            if (::windowManager.isInitialized && ::floatingView.isInitialized) {
                // 尝试移除视图，即使可能已经被移除
                try {
                    windowManager.removeView(floatingView)
                    Log.d(TAG, "悬浮按钮移除成功")
                } catch (e: IllegalArgumentException) {
                    // 视图可能已经被移除，这是正常的
                    Log.d(TAG, "悬浮按钮视图已不存在，无需移除")
                } catch (e: Exception) {
                    Log.e(TAG, "移除悬浮按钮失败", e)
                }
                isFloatingButtonVisible = false
            } else {
                Log.d(TAG, "悬浮按钮未初始化，无需移除")
                isFloatingButtonVisible = false
            }
        } catch (e: Exception) {
            Log.e(TAG, "移除悬浮按钮时发生异常", e)
            isFloatingButtonVisible = false
        }
    }

    private fun generateAndFillPassword() {
        // 生成密码
        val password = PasswordGenerator.getInstance().generatePassword()
        Log.d(TAG, "生成密码: $password")

        // 填充密码到WPS
        AccessibilityServiceManager.getInstance().fillPassword(password)
    }

    private fun viewPassword() {
        // 获取稳定文档路径（存储密码时使用的路径）
        val documentPath = WpsAccessibilityService.stableDocumentPath
        if (documentPath.isNullOrEmpty()) {
            showOperationNotification("复制密码", "未找到文档路径")
            Toast.makeText(this, "未找到文档路径", Toast.LENGTH_SHORT).show()
            Log.e(TAG, "未找到文档路径")
            return
        }

        // 优先使用临时密码
        var password = WpsAccessibilityService.getTempPassword()
        if (password == null || password.isEmpty()) {
            // 如果FileMeta中也没有密码，从文档末尾读取密码（使用ProxyActivity）
            val uid = FileMetaFactory.getFileMeta(documentPath)?.uid ?: FileMetaFactory.createUid()
            password = ProxyActivity.readPasswordFromFile(this, documentPath, uid)
            if (password != null) {
                Log.d(TAG, "从文档末尾成功读取密码")
            }
        }

        if (password != null && password.isNotEmpty()) {
            // 将密码复制到剪贴板
            copyToClipboard(password)
            // 显示密码通知
            showOperationNotification("复制密码", "密码已复制到剪贴板")
            // 显示Toast提示
            Toast.makeText(this, "密码已复制到剪贴板", Toast.LENGTH_LONG).show()
            Log.d(TAG, "获取密码成功: $password")
        } else {
            showOperationNotification("复制密码", "未找到存储的密码")
            Toast.makeText(this, "未找到存储的密码", Toast.LENGTH_SHORT).show()
            Log.e(TAG, "未找到存储的密码，文件路径: $documentPath")
        }
    }

    /**
     * 将文本复制到剪贴板
     */
    private fun copyToClipboard(text: String) {
        try {
            val clipboard = getSystemService(CLIPBOARD_SERVICE) as android.content.ClipboardManager
            val clip = android.content.ClipData.newPlainText("密码", text)
            clipboard.setPrimaryClip(clip)
            Log.d(TAG, "密码已成功复制到剪贴板")
        } catch (e: Exception) {
            Log.e(TAG, "复制到剪贴板失败", e)
            Toast.makeText(this, "复制到剪贴板失败", Toast.LENGTH_SHORT).show()
        }
    }

    fun showFloatingButton() {
        try {
            // 再次检查权限
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                if (!Settings.canDrawOverlays(this)) {
                    Log.e(TAG, "没有显示在其他应用之上的权限，无法显示悬浮按钮")
                    showPermissionNotification()
                    return
                }
            }

            if (!isFloatingButtonVisible) {
                Log.d(TAG, "开始初始化悬浮按钮")
                initFloatingButton()
            } else {
                Log.d(TAG, "悬浮按钮已经可见，无需重复显示")
                // 确保按钮仍然存在
                try {
                    if (::windowManager.isInitialized && ::floatingView.isInitialized) {
                        Log.d(TAG, "悬浮按钮已确认显示")
                    } else {
                        Log.d(TAG, "悬浮按钮引用丢失，重新初始化")
                        isFloatingButtonVisible = false
                        initFloatingButton()
                    }
                } catch (e: Exception) {
                    Log.e(TAG, "检查悬浮按钮状态失败", e)
                    isFloatingButtonVisible = false
                    initFloatingButton()
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "显示悬浮按钮时发生异常", e)
            isFloatingButtonVisible = false
            // 尝试重新初始化
            try {
                initFloatingButton()
            } catch (retryEx: Exception) {
                Log.e(TAG, "重新初始化悬浮按钮失败", retryEx)
            }
        }
    }

    fun hideFloatingButton() {
        try {
            // 无论isFloatingButtonVisible的状态如何，都尝试移除悬浮按钮
            // 确保即使状态标志不正确，也能实际隐藏按钮
            removeFloatingButton()
        } catch (e: Exception) {
            Log.e(TAG, "隐藏悬浮按钮时发生异常", e)
            isFloatingButtonVisible = false
        }
    }

    private fun showPermissionNotification() {
        val intent = Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION)
        AppNotificationManager.getInstance(this).showActionNotification(
            "需要权限",
            "请开启显示在其他应用之上的权限，以使用悬浮按钮功能",
            intent
        )
        Log.d(TAG, "显示权限通知")
    }

    /**
     * 显示操作状态通知
     */
    fun showOperationNotification(title: String, content: String, autoCancel: Boolean = true) {
        AppNotificationManager.getInstance(this).showOperationNotification(title, content, autoCancel)
        Log.d(TAG, "显示操作通知: $title - $content")
    }

    private fun showDocumentPermissionDialog() {
        // 显示加载提示
        Toast.makeText(this, "加载文档权限数据...", Toast.LENGTH_SHORT).show()

        val networkManager = NetworkManager.getInstance(this)
        val userInfo = ConfigStorage.getInstance(this).getUserInfo()
        val token = userInfo?.token

        // 获取文档路径
        val documentPath = WpsAccessibilityService.stableDocumentPath
        if (documentPath.isNullOrEmpty()) {
            Toast.makeText(this, "未找到文档路径", Toast.LENGTH_SHORT).show()
            return
        }

        // 获取FileMeta对象
        val fileMeta = FileMetaFactory.getFileMeta(documentPath)
        if (fileMeta == null) {
            Toast.makeText(this, "未找到文档元数据", Toast.LENGTH_SHORT).show()
            return
        }

        // 获取docId参数
        val docId = fileMeta.uid
        if (docId.isNullOrEmpty()) {
            Toast.makeText(this, "未找到文档唯一标识", Toast.LENGTH_SHORT).show()
            return
        }

        // 使用异步方式获取数据，添加docId参数
        networkManager.executeGetRequest("/doc/auth/tree?docId=$docId", token, object : NetworkCallback {
            override fun onSuccess(response: String) {
                // 解析LdapItem数据
                val ldapItems = parseLdapItems(response)

                // 在主线程中显示对话框
                runOnUiThread {
                    showPermissionTreeDialog(ldapItems)
                }
            }

            override fun onError(error: String) {
                // 如果响应为null，使用模拟数据
                val mockResponse = getMockResponse()
                val ldapItems = parseLdapItems(mockResponse)

                // 在主线程中显示对话框
                runOnUiThread {
                    Toast.makeText(this@FloatingButtonService, "网络错误，使用模拟数据", Toast.LENGTH_SHORT).show()
                    showPermissionTreeDialog(ldapItems)
                }
            }

            override fun onComplete() {}
        })
    }

    private fun parseLdapItems(response: String): List<LdapItem> {
        try {
            val gson = com.google.gson.Gson()
            val responseData = gson.fromJson(response, ResponseData::class.java)
            return responseData.data ?: emptyList()
        } catch (e: Exception) {
            e.printStackTrace()
            return emptyList()
        }
    }

    // 响应数据类
    data class ResponseData(
        val message: String,
        val status: Int,
        val data: List<LdapItem>
    )

    private var permissionPanelView: android.view.View? = null
    private var isPermissionPanelExpanded = false

    private var treeAdapter: TreeAdapter? = null
    private var rootNodes: List<TreeNode> = emptyList()

    private fun showPermissionTreeDialog(ldapItems: List<LdapItem>) {
        // 在WPS应用界面内显示文档权限模块
        if (permissionPanelView == null) {
            // 解析LdapItem为TreeNode
            rootNodes = parseLdapItemsToTreeNodes(ldapItems)
            // 初始化时计算所有父节点的半勾选状态
            initializeParentAuthState(rootNodes)
            // 创建权限面板视图
            createPermissionPanel()
        } else {
            // 切换面板展开/收起状态
            togglePermissionPanel()
        }
    }

    private fun initializeParentAuthState(nodes: List<TreeNode>) {
        for (node in nodes) {
            if (node.children.isNotEmpty()) {
                initializeParentAuthState(node.children)
                
                val (checkedLeafCount, totalLeafCount) = countLeafNodes(node)
                
                if (totalLeafCount == 0) {
                    continue
                }
                
                if (checkedLeafCount == 0) {
                    node.hasAuth = false
                    node.isIndeterminate = false
                } else if (checkedLeafCount == totalLeafCount) {
                    node.hasAuth = true
                    node.isIndeterminate = false
                } else {
                    node.hasAuth = false
                    node.isIndeterminate = true
                }
            }
        }
    }

    private fun parseLdapItemsToTreeNodes(ldapItems: List<LdapItem>): List<TreeNode> {
        val nodes = mutableListOf<TreeNode>()
        for (item in ldapItems) {
            val node = TreeNode(
                dn = item.dn,
                name = item.name,
                account = item.account,
                type = item.type,
                hasAuth = item.hasAuth,
                level = 0
            )
            // 递归添加子部门和员工
            addChildren(node, item)
            nodes.add(node)
        }
        return nodes
    }

    private fun addChildren(parent: TreeNode, ldapItem: LdapItem) {
        // 添加子部门
        for (dept in ldapItem.deptList ?: emptyList()) {
            val deptNode = TreeNode(
                dn = dept.dn,
                name = dept.name,
                account = dept.account,
                type = dept.type,
                hasAuth = dept.hasAuth,
                level = parent.level + 1,
                parent = parent
            )
            parent.children.add(deptNode)
            addChildren(deptNode, dept)
        }

        // 添加员工
        for (employee in ldapItem.employList ?: emptyList()) {
            val employeeNode = TreeNode(
                dn = employee.dn,
                name = employee.name,
                account = employee.account,
                type = employee.type,
                hasAuth = employee.hasAuth,
                level = parent.level + 1,
                parent = parent
            )
            parent.children.add(employeeNode)
        }
    }

    private fun flattenTree(nodes: List<TreeNode>): List<TreeNode> {
        val result = mutableListOf<TreeNode>()
        for (node in nodes) {
            result.add(node)
            // 如果是部门(type=0)、已展开、且有子节点，则递归添加子节点
            if (node.type == 0 && node.isExpanded && node.children.isNotEmpty()) {
                result.addAll(flattenTree(node.children))
            }
        }
        return result
    }

    private fun createPermissionPanel() {
        try {
            // 检查权限
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                if (!Settings.canDrawOverlays(this)) {
                    Log.e(TAG, "没有显示在其他应用之上的权限")
                    showPermissionNotification()
                    return
                }
            }

            val inflater = getSystemService(LAYOUT_INFLATER_SERVICE) as LayoutInflater
            permissionPanelView = inflater.inflate(R.layout.permission_panel, null)

            val params = WindowManager.LayoutParams(
                WindowManager.LayoutParams.MATCH_PARENT,
                WindowManager.LayoutParams.WRAP_CONTENT,
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
                } else {
                    WindowManager.LayoutParams.TYPE_PHONE
                },
                WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL or
                WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
                PixelFormat.TRANSLUCENT
            )

            // 设置面板位置在屏幕底部
            params.gravity = Gravity.BOTTOM
            params.x = 0
            params.y = 0

            // 获取面板中的视图
            val panelHeader = permissionPanelView?.findViewById<android.widget.LinearLayout>(R.id.panel_header)
            val panelContent = permissionPanelView?.findViewById<android.widget.LinearLayout>(R.id.panel_content)
            val treeContainer = permissionPanelView?.findViewById<android.widget.LinearLayout>(R.id.tree_container)
            val btnClose = permissionPanelView?.findViewById<android.widget.Button>(R.id.btn_close)
            val btnSelectAll = permissionPanelView?.findViewById<android.widget.Button>(R.id.btn_select_all)
            val btnDeselectAll = permissionPanelView?.findViewById<android.widget.Button>(R.id.btn_deselect_all)
            val btnSave = permissionPanelView?.findViewById<android.widget.Button>(R.id.btn_save)
            val btnCancel = permissionPanelView?.findViewById<android.widget.Button>(R.id.btn_cancel)
            val etSearch = permissionPanelView?.findViewById<android.widget.EditText>(R.id.et_search)
            val btnSearch = permissionPanelView?.findViewById<android.widget.Button>(R.id.btn_search)
            val btnClear = permissionPanelView?.findViewById<android.widget.Button>(R.id.btn_clear)
            val tvError = permissionPanelView?.findViewById<android.widget.TextView>(R.id.tv_error)

            // 替换ScrollView和LinearLayout为RecyclerView
            treeContainer?.removeAllViews()
            val recyclerView = androidx.recyclerview.widget.RecyclerView(this)
            recyclerView.layoutManager = androidx.recyclerview.widget.LinearLayoutManager(this)
            // 初始扁平化列表
            val initialList = flattenTree(rootNodes).toMutableList()
            treeAdapter = TreeAdapter(
                nodes = initialList,
                onItemClicked = { node ->
                    if (node.type == 0 && node.children.isNotEmpty()) {
                        // 切换展开状态
                        node.isExpanded = !node.isExpanded
                        // 重新计算扁平化列表并通知适配器更新
                        val newList = flattenTree(rootNodes).toMutableList()
                        treeAdapter?.nodes?.clear()
                        treeAdapter?.nodes?.addAll(newList)
                        treeAdapter?.notifyDataSetChanged()
                    }
                },
                onAuthStateChanged = { node, hasAuth ->
                    node.hasAuth = hasAuth
                    node.isIndeterminate = false
                    
                    if (node.type == 0) {
                        node.updateChildrenAuthState(hasAuth)
                    }
                    
                    updateParentAuthState(node.parent)
                    
                    val newList = flattenTree(rootNodes).toMutableList()
                    treeAdapter?.nodes?.clear()
                    treeAdapter?.nodes?.addAll(newList)
                    treeAdapter?.notifyDataSetChanged()
                }
            )
            recyclerView.adapter = treeAdapter

            treeContainer?.addView(recyclerView, android.widget.LinearLayout.LayoutParams(
                android.widget.LinearLayout.LayoutParams.MATCH_PARENT,
                android.widget.LinearLayout.LayoutParams.MATCH_PARENT
            ))

            // 面板头部点击事件（展开/收起）
            panelHeader?.setOnClickListener {
                togglePermissionPanel()
            }

            // 关闭按钮点击事件
            btnClose?.setOnClickListener {
                removePermissionPanel()
            }

            // 全选按钮点击事件
            btnSelectAll?.setOnClickListener {
                selectAllNodes(rootNodes, true)
                val newList = flattenTree(rootNodes).toMutableList()
                treeAdapter?.nodes?.clear()
                treeAdapter?.nodes?.addAll(newList)
                treeAdapter?.notifyDataSetChanged()
            }

            // 反选按钮点击事件
            btnDeselectAll?.setOnClickListener {
                selectAllNodes(rootNodes, false)
                val newList = flattenTree(rootNodes).toMutableList()
                treeAdapter?.nodes?.clear()
                treeAdapter?.nodes?.addAll(newList)
                treeAdapter?.notifyDataSetChanged()
            }

            // 保存按钮点击事件
            btnSave?.setOnClickListener {
                // 处理保存逻辑
                val selectedItems = getSelectedNodes(rootNodes)
                
                // 提取选中的账号和部门DN
                val accountDnList = mutableListOf<String>()
                val deptDnList = mutableListOf<String>()
                
                for (node in selectedItems) {
                    if (node.type == 1) { // 员工
                        accountDnList.add(node.dn)
                    } else if (node.type == 0) { // 部门
                        deptDnList.add(node.dn)
                    }
                }
                
                // 获取文档路径
                val documentPath = WpsAccessibilityService.stableDocumentPath
                if (documentPath.isNullOrEmpty()) {
                    Toast.makeText(this, "未找到文档路径", Toast.LENGTH_SHORT).show()
                    return@setOnClickListener
                }
                
                // 获取FileMeta对象
                val fileMeta = FileMetaFactory.getFileMeta(documentPath)
                if (fileMeta == null) {
                    Toast.makeText(this, "未找到文档元数据", Toast.LENGTH_SHORT).show()
                    return@setOnClickListener
                }
                
                // 获取docId参数
                val docId = fileMeta.uid
                if (docId.isNullOrEmpty()) {
                    Toast.makeText(this, "未找到文档唯一标识", Toast.LENGTH_SHORT).show()
                    return@setOnClickListener
                }
                
                // 获取isTemp参数（根据uid是否已注册）
                val isTemp = fileMeta.isTempUid
                android.util.Log.d(TAG, "权限更新 - docId=$docId, isTemp=$isTemp, accountDnList=$accountDnList, deptDnList=$deptDnList")
                
                // 构建请求体
                val jsonBody = "{\"docId\": \"$docId\", \"accountDnList\": [${accountDnList.joinToString { "\"$it\"" }}], \"deptDnList\": [${deptDnList.joinToString { "\"$it\"" }}], \"isTemp\": $isTemp}"
                
                // 获取token
                val userInfo = ConfigStorage.getInstance(this).getUserInfo()
                val token = userInfo?.token
                
                // 发送请求
                val networkManager = NetworkManager.getInstance(this)
                networkManager.executePostRequest("/doc/auth/update", jsonBody, token, object : NetworkCallback {
                    override fun onSuccess(response: String) {
                        runOnUiThread {
                            try {
                                val json = org.json.JSONObject(response)
                                val status = json.getInt("status")
                                if (status == 200) {
                                    tvError?.visibility = android.view.View.GONE
                                    Toast.makeText(this@FloatingButtonService, "权限更新成功", Toast.LENGTH_SHORT).show()
                                    removePermissionPanel()
                                } else {
                                    val message = json.optString("message", "权限更新失败")
                                    tvError?.text = message
                                    tvError?.visibility = android.view.View.VISIBLE
                                }
                            } catch (e: Exception) {
                                tvError?.text = "权限更新失败: 解析响应异常"
                                tvError?.visibility = android.view.View.VISIBLE
                            }
                        }
                    }
                    
                    override fun onError(error: String) {
                        runOnUiThread {
                            tvError?.text = "权限更新失败: $error"
                            tvError?.visibility = android.view.View.VISIBLE
                        }
                    }

                    override fun onComplete() {}
                })
            }

            // 取消按钮点击事件
            btnCancel?.setOnClickListener {
                removePermissionPanel()
            }

            btnSearch?.setOnClickListener {
                val searchText = etSearch?.text?.toString() ?: ""
                android.util.Log.d(TAG, "搜索按钮点击，搜索文本: $searchText")
                performSearch(searchText)
            }

            etSearch?.setOnEditorActionListener { v, actionId, event ->
                if (actionId == android.view.inputmethod.EditorInfo.IME_ACTION_SEARCH) {
                    val searchText = etSearch.text.toString()
                    android.util.Log.d(TAG, "搜索框回车，搜索文本: $searchText")
                    performSearch(searchText)
                    true
                } else {
                    false
                }
            }

            // 清理按钮点击事件
            btnClear?.setOnClickListener {
                // 清除搜索框内容
                etSearch?.text?.clear()
                // 清除搜索结果，返回默认列表
                val initialList = flattenTree(rootNodes).toMutableList()
                treeAdapter?.nodes?.clear()
                treeAdapter?.nodes?.addAll(initialList)
                treeAdapter?.notifyDataSetChanged()
                // 给予用户视觉反馈
                Toast.makeText(this, "已清理搜索内容", Toast.LENGTH_SHORT).show()
            }

            // 添加面板到窗口
            windowManager.addView(permissionPanelView, params)
            isPermissionPanelExpanded = true
            Log.d(TAG, "文档权限面板添加成功")
        } catch (e: Exception) {
            Log.e(TAG, "创建文档权限面板失败", e)
        }
    }

    private fun selectAllNodes(nodes: List<TreeNode>, select: Boolean) {
        for (node in nodes) {
            node.hasAuth = select
            if (node.children.isNotEmpty()) {
                selectAllNodes(node.children, select)
            }
        }
    }

    private fun getSelectedNodes(nodes: List<TreeNode>): List<TreeNode> {
        val selected = mutableListOf<TreeNode>()
        for (node in nodes) {
            if (node.hasAuth) {
                selected.add(node)
                // 如果是部门类型且已被勾选，跳过子节点处理
                if (node.type == 0) {
                    continue
                }
            }
            if (node.children.isNotEmpty()) {
                selected.addAll(getSelectedNodes(node.children))
            }
        }
        return selected
    }

    private fun performSearch(searchText: String) {
        Log.d(TAG, "开始搜索: $searchText")
        
        if (searchText.isEmpty()) {
            Log.d(TAG, "搜索文本为空，显示完整树")
            resetAllExpanded(rootNodes)
            val initialList = flattenTree(rootNodes).toMutableList()
            treeAdapter?.isSearchMode = false
            treeAdapter?.nodes?.clear()
            treeAdapter?.nodes?.addAll(initialList)
            treeAdapter?.notifyDataSetChanged()
            return
        }

        treeAdapter?.isSearchMode = true
        
        val matchingNodes = mutableListOf<TreeNode>()
        findMatchingNodes(rootNodes, searchText, matchingNodes)
        Log.d(TAG, "找到匹配节点: ${matchingNodes.size}个")
        matchingNodes.forEach { Log.d(TAG, "匹配节点: ${it.name}") }
        
        if (matchingNodes.isEmpty()) {
            Log.d(TAG, "未找到匹配节点")
            treeAdapter?.nodes?.clear()
            treeAdapter?.notifyDataSetChanged()
            return
        }

        val nodesToShowDn = mutableSetOf<String>()
        matchingNodes.forEach { node ->
            var current: TreeNode? = node
            while (current != null) {
                nodesToShowDn.add(current.dn)
                if (current.type == 0) {
                    current.isExpanded = true
                }
                current = current.parent
            }
        }
        Log.d(TAG, "需要显示的节点DN: ${nodesToShowDn.size}个")

        val pathNodes = mutableListOf<TreeNode>()
        collectPathNodes(rootNodes, nodesToShowDn, pathNodes)
        
        Log.d(TAG, "收集到的路径节点: ${pathNodes.size}个")
        pathNodes.forEach { Log.d(TAG, "路径节点: ${it.name} (层级: ${it.level})") }

        treeAdapter?.nodes?.clear()
        treeAdapter?.nodes?.addAll(pathNodes)
        treeAdapter?.notifyDataSetChanged()
        Log.d(TAG, "搜索完成")
    }

    private fun findMatchingNodes(nodes: List<TreeNode>, searchText: String, result: MutableList<TreeNode>) {
        for (node in nodes) {
            if (node.name.contains(searchText, ignoreCase = true)) {
                result.add(node)
            }
            if (node.children.isNotEmpty()) {
                findMatchingNodes(node.children, searchText, result)
            }
        }
    }

    private fun collectPathNodes(nodes: List<TreeNode>, nodesToShowDn: Set<String>, result: MutableList<TreeNode>) {
        for (node in nodes) {
            if (nodesToShowDn.contains(node.dn)) {
                result.add(node)
                if (node.children.isNotEmpty()) {
                    collectPathNodes(node.children, nodesToShowDn, result)
                }
            }
        }
    }

    private fun resetAllExpanded(nodes: List<TreeNode>) {
        for (node in nodes) {
            node.isExpanded = false
            if (node.children.isNotEmpty()) {
                resetAllExpanded(node.children)
            }
        }
    }

    private fun updateParentAuthState(parent: TreeNode?) {
        if (parent == null) {
            return
        }
        
        val (checkedLeafCount, totalLeafCount) = countLeafNodes(parent)
        
        if (totalLeafCount == 0) {
            return
        }
        
        if (checkedLeafCount == 0) {
            parent.hasAuth = false
            parent.isIndeterminate = false
        } else if (checkedLeafCount == totalLeafCount) {
            parent.hasAuth = true
            parent.isIndeterminate = false
        } else {
            parent.hasAuth = false
            parent.isIndeterminate = true
        }
        
        updateParentAuthState(parent.parent)
    }

    private fun countLeafNodes(node: TreeNode): Pair<Int, Int> {
        if (node.type == 1 || node.children.isEmpty()) {
            // 叶子节点（员工或无子部门）
            return if (node.hasAuth) Pair(1, 1) else Pair(0, 1)
        }
        
        var checkedCount = 0
        var totalCount = 0
        
        for (child in node.children) {
            val (childChecked, childTotal) = countLeafNodes(child)
            checkedCount += childChecked
            totalCount += childTotal
        }
        
        return Pair(checkedCount, totalCount)
    }

    private fun filterNodes(nodes: List<TreeNode>, searchText: String): List<TreeNode> {
        val result = mutableListOf<TreeNode>()
        for (node in nodes) {
            if (node.name.contains(searchText, ignoreCase = true)) {
                result.add(node)
                if (node.type == 0 && node.children.isNotEmpty()) {
                    result.addAll(filterNodes(node.children, searchText))
                }
            } else if (node.type == 0 && node.children.isNotEmpty()) {
                val filteredChildren = filterNodes(node.children, searchText)
                if (filteredChildren.isNotEmpty()) {
                    result.add(node)
                    result.addAll(filteredChildren)
                }
            }
        }
        return result
    }

    private fun togglePermissionPanel() {
        permissionPanelView?.let {
            val panelContent = it.findViewById<android.widget.LinearLayout>(R.id.panel_content)
            if (isPermissionPanelExpanded) {
                // 收起面板
                panelContent.visibility = android.view.View.GONE
                isPermissionPanelExpanded = false
            } else {
                // 展开面板
                panelContent.visibility = android.view.View.VISIBLE
                isPermissionPanelExpanded = true
            }
        }
    }

    private fun removePermissionPanel() {
        try {
            if (permissionPanelView != null && ::windowManager.isInitialized) {
                windowManager.removeView(permissionPanelView)
                permissionPanelView = null
                isPermissionPanelExpanded = false
                Log.d(TAG, "文档权限面板移除成功")
            }
        } catch (e: Exception) {
            Log.e(TAG, "移除文档权限面板失败", e)
        }
    }



    // LdapItem数据类
    data class LdapItem(
        val type: Int, // 节点类型 0 部门 1员工
        val name: String, // 节点名称
        val dn: String, // LDAP完整路径
        val account: String?, // 账号名（用户专属）
        val hasAuth: Boolean, // 是否有权限
        val deptList: List<LdapItem>?, // 子部门列表
        val employList: List<LdapItem>? // 子员工列表
    )

    // 获取模拟响应数据
    private fun getMockResponse(): String {
        return """
        {
          "message": "操作成功",
          "status": 200,
          "data": [
            {
              "dn": "OU=武汉绿网,OU=绿色网络,DC=greenet,DC=com,DC=cn",
              "type": 0,
              "name": "武汉绿网",
              "account": null,
              "hasAuth": false,
              "deptList": [
                {
                  "dn": "OU=总经理办公室,OU=武汉绿网,OU=绿色网络,DC=greenet,DC=com,DC=cn",
                  "type": 0,
                  "name": "总经理办公室",
                  "account": null,
                  "hasAuth": false,
                  "deptList": [
                    {
                      "dn": "OU=行政部,OU=总经理办公室,OU=武汉绿网,OU=绿色网络,DC=greenet,DC=com,DC=cn",
                      "type": 0,
                      "name": "行政部",
                      "account": null,
                      "hasAuth": false,
                      "deptList": null,
                      "employList": null
                    },
                    {
                      "dn": "OU=政府事务与资质部,OU=总经理办公室,OU=武汉绿网,OU=绿色网络,DC=greenet,DC=com,DC=cn",
                      "type": 0,
                      "name": "政府事务与资质部",
                      "account": null,
                      "hasAuth": true,
                      "deptList": null,
                      "employList": [
                        {
                          "dn": "CN=张贵丽,OU=政府事务与资质部,OU=总经理办公室,OU=武汉绿网,OU=绿色网络,DC=greenet,DC=com,DC=cn",
                          "type": 1,
                          "name": "张贵丽",
                          "account": "zhanggl",
                          "deptList": null,
                          "employList": null,
                          "hasAuth": false
                        },
                        {
                          "dn": "CN=周丹,OU=政府事务与资质部,OU=总经理办公室,OU=武汉绿网,OU=绿色网络,DC=greenet,DC=com,DC=cn",
                          "type": 1,
                          "name": "周丹",
                          "account": "zhoudan",
                          "deptList": null,
                          "employList": null,
                          "hasAuth": false
                        }
                      ]
                    }
                  ],
                  "employList": [
                    {
                      "dn": "CN=孟春霞,OU=总经理办公室,OU=武汉绿网,OU=绿色网络,DC=greenet,DC=com,DC=cn",
                      "type": 1,
                      "name": "孟春霞",
                      "account": "mengcx",
                      "deptList": null,
                      "employList": null,
                      "hasAuth": false
                    }
                  ]
                },
                {
                  "dn": "OU=研发中心,OU=武汉绿网,OU=绿色网络,DC=greenet,DC=com,DC=cn",
                  "type": 0,
                  "name": "研发中心",
                  "account": null,
                  "hasAuth": false,
                  "deptList": [
                    {
                      "dn": "OU=系统软件研发部,OU=研发中心,OU=武汉绿网,OU=绿色网络,DC=greenet,DC=com,DC=cn",
                      "type": 0,
                      "name": "系统软件研发部",
                      "account": null,
                      "hasAuth": false,
                      "deptList": [
                        {
                          "dn": "OU=大数据技术组,OU=系统软件研发部,OU=研发中心,OU=武汉绿网,OU=绿色网络,DC=greenet,DC=com,DC=cn",
                          "type": 0,
                          "name": "大数据技术组",
                          "account": null,
                          "hasAuth": false,
                          "deptList": null,
                          "employList": [
                            {
                              "dn": "CN=张鹏,OU=大数据技术组,OU=系统软件研发部,OU=研发中心,OU=武汉绿网,OU=绿色网络,DC=greenet,DC=com,DC=cn",
                              "type": 1,
                              "name": "张鹏",
                              "account": "zhangpeng",
                              "deptList": null,
                              "employList": null,
                              "hasAuth": true
                            },
                            {
                              "dn": "CN=王志杰,OU=大数据技术组,OU=系统软件研发部,OU=研发中心,OU=武汉绿网,OU=绿色网络,DC=greenet,DC=com,DC=cn",
                              "type": 1,
                              "name": "王志杰",
                              "account": "wangzj",
                              "deptList": null,
                              "employList": null,
                              "hasAuth": false
                            }
                          ]
                        },
                        {
                          "dn": "OU=云宽带系统研发组,OU=系统软件研发部,OU=研发中心,OU=武汉绿网,OU=绿色网络,DC=greenet,DC=com,DC=cn",
                          "type": 0,
                          "name": "云宽带系统研发组",
                          "account": null,
                          "hasAuth": false,
                          "deptList": null,
                          "employList": null
                        }
                      ],
                      "employList": [
                        {
                          "dn": "CN=孙昌燕,OU=系统软件研发部,OU=研发中心,OU=武汉绿网,OU=绿色网络,DC=greenet,DC=com,DC=cn",
                          "type": 1,
                          "name": "孙昌燕",
                          "account": "suncy",
                          "deptList": null,
                          "employList": null,
                          "hasAuth": true
                        }
                      ]
                    }
                  ],
                  "employList": [
                    {
                      "dn": "CN=牛晨光,OU=研发中心,OU=武汉绿网,OU=绿色网络,DC=greenet,DC=com,DC=cn",
                      "type": 1,
                      "name": "牛晨光",
                      "account": "niucg",
                      "deptList": null,
                      "employList": null,
                      "hasAuth": false
                    }
                  ]
                }
              ],
              "employList": null
            }
          ]
        }
        """.trimIndent()
    }

    private fun runOnUiThread(action: () -> Unit) {
        android.os.Handler(android.os.Looper.getMainLooper()).post(action)
    }

    private fun showToast(message: String) {
        Toast.makeText(this@FloatingButtonService, message, Toast.LENGTH_LONG).show()
    }
}