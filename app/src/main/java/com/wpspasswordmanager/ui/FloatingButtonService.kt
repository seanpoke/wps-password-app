package com.wpspasswordmanager.ui

import android.app.Service
import android.content.Intent
import android.graphics.PixelFormat
import android.os.Build
import android.os.IBinder
import android.provider.Settings
import android.util.Log
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
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

        // 兜底看门狗：气泡显示期间定期确认屏幕上仍是【添加密码】/【修改密码】窗口
        private const val WATCHDOG_INTERVAL_MS = 500L
        private const val WATCHDOG_MISS_LIMIT = 6 // 加权计数阈值：明确「不是」每次 +2（约 3 秒），「无法判断」每次 +1（约 6 秒）
    }

    data class ScreenMetrics(val width: Int, val height: Int)
    data class Position(val x: Int, val y: Int)

    private lateinit var windowManager: WindowManager
    private lateinit var floatingView: View
    private var isFloatingButtonVisible = false

    // Compose 浮层状态
    private var overlayHost: com.wpspasswordmanager.ui.compose.assistant.ComposeOverlayHost? = null
    private var overlayOwner: com.wpspasswordmanager.ui.compose.assistant.ServiceOwner? = null
    private var overlayParams: WindowManager.LayoutParams? = null
    private val overlayState = androidx.compose.runtime.mutableStateOf(
        com.wpspasswordmanager.ui.compose.assistant.AssistantUiState()
    )

    /** 悬浮窗 Compose 权限树的状态；null = 未打开（显示常规三态面板） */
    private val permTreeState = androidx.compose.runtime.mutableStateOf<com.wpspasswordmanager.ui.compose.PermTreeState?>(null)

    private val mainHandler by lazy { android.os.Handler(android.os.Looper.getMainLooper()) }

    /**
     * 兜底看门狗：气泡可见期间定期向无障碍服务询问「当前是否仍处于添加/修改密码窗口」。
     * 无障碍事件可能丢失（例如关闭弹窗的瞬间 rootInActiveWindow 为空、或服务被系统短暂停止），
     * 只靠事件触发隐藏会残留气泡；这里用轮询兜底，加权计数达到 [WATCHDOG_MISS_LIMIT] 即隐藏。
     */
    /** 看门狗查询线程：isPasswordDialogOnScreen 的全树遍历是跨进程 binder 调用，
     *  必须与悬浮窗 UI（主线程）隔离，否则权限树等列表滑动会掉帧 */
    private val watchdogExecutor = java.util.concurrent.Executors.newSingleThreadExecutor()

    private val overlayWatchdog = object : Runnable {
        private var missCount = 0

        override fun run() {
            if (!isFloatingButtonVisible) {
                missCount = 0
                return
            }

            watchdogExecutor.execute {
                val onScreen = com.wpspasswordmanager.monitor.AccessibilityServiceManager.getInstance()
                    .isPasswordDialogOnScreen()
                mainHandler.post { handleWatchdogResult(onScreen) }
            }
        }

        private fun handleWatchdogResult(onScreen: Boolean?) {
            if (!isFloatingButtonVisible) {
                missCount = 0
                return
            }

            if (onScreen == true) {
                missCount = 0
            } else {
                // 明确「不是密码弹框」权重高（约 3 秒即隐藏）；「无法判断」权重低（约 6 秒），
                // 避免切换输入法等瞬时拿不到根节点时误伤。
                missCount += if (onScreen == false) 2 else 1
                if (missCount >= WATCHDOG_MISS_LIMIT) {
                    com.wpspasswordmanager.utils.LogManager.log(TAG, "兜底隐藏：连续未确认密码弹框（onScreen=$onScreen）", "DEBUG")
                    Log.d(TAG, "兜底隐藏：连续未确认密码弹框（onScreen=$onScreen）")
                    missCount = 0
                    hideFloatingButton()
                    return
                }
            }

            mainHandler.postDelayed(this, WATCHDOG_INTERVAL_MS)
        }
    }

    private fun startOverlayWatchdog() {
        mainHandler.removeCallbacks(overlayWatchdog)
        mainHandler.postDelayed(overlayWatchdog, WATCHDOG_INTERVAL_MS)
        Log.d(TAG, "兜底看门狗已启动")
    }

    private fun stopOverlayWatchdog() {
        mainHandler.removeCallbacks(overlayWatchdog)
    }

    /**
     * 服务刚被无障碍拉起时的首屏显示：确认当前仍在【添加密码】/【修改密码】窗口才显示气泡。
     * 无障碍侧刚拿到根节点时偶发为空，允许短间隔重试两次再放弃，避免把「无法判断」当成「已关闭」。
     */
    private fun showIfPasswordDialog(attempt: Int) {
        if (isFloatingButtonVisible) return

        val onScreen = com.wpspasswordmanager.monitor.AccessibilityServiceManager.getInstance()
            .isPasswordDialogOnScreen()
        com.wpspasswordmanager.utils.LogManager.log(
            TAG, "showIfPasswordDialog attempt=$attempt onScreen=$onScreen", "DEBUG"
        )

        when {
            onScreen == true -> {
                com.wpspasswordmanager.monitor.AccessibilityServiceManager.getInstance().clearPendingShowRequest()
                initFloatingButton()
                com.wpspasswordmanager.utils.LogManager.log(TAG, "服务就绪，当前处于密码窗口，立即显示气泡", "DEBUG")
                Log.d(TAG, "服务就绪，当前处于密码窗口，立即显示气泡")
            }

            onScreen == null && attempt < 7 -> {
                mainHandler.postDelayed({ showIfPasswordDialog(attempt + 1) }, 250L)
            }

            else -> {
                com.wpspasswordmanager.monitor.AccessibilityServiceManager.getInstance().clearPendingShowRequest()
                com.wpspasswordmanager.utils.LogManager.log(
                    TAG, "服务就绪但当前不在添加/修改密码窗口（onScreen=$onScreen），不显示气泡", "DEBUG"
                )
                Log.d(TAG, "服务就绪但当前不在添加/修改密码窗口（onScreen=$onScreen），不显示气泡")
            }
        }
    }

    /** 更新浮层状态，并在重组后重新测量窗口（WRAP_CONTENT 需要显式 updateViewLayout） */
    private fun updateOverlay(block: (com.wpspasswordmanager.ui.compose.assistant.AssistantUiState) -> com.wpspasswordmanager.ui.compose.assistant.AssistantUiState) {
        overlayState.value = block(overlayState.value)
        mainHandler.postDelayed({
            val params = overlayParams ?: return@postDelayed
            if (!::floatingView.isInitialized) return@postDelayed
            runCatching {
                constrainPositionToScreen(params, floatingView)
                windowManager.updateViewLayout(floatingView, params)
            }
        }, 60)
    }

    /** 长按气泡拖动：gravity 为 TOP|END，向右拖动 x 需要减小 */
    private fun dragOverlayBy(dx: Float, dy: Float) {
        val params = overlayParams ?: return
        if (!::floatingView.isInitialized) return
        params.x -= dx.toInt()
        params.y += dy.toInt()
        runCatching {
            constrainPositionToScreen(params, floatingView)
            windowManager.updateViewLayout(floatingView, params)
        }
    }

    /** 读取当前文档名 / 是否已加密 / 是否有写权限（决定「文档权限」入口） */
    private fun refreshOverlayDoc() {
        val path = WpsAccessibilityService.stableDocumentPath
        val name = if (path.isNullOrEmpty()) "" else java.io.File(path).name
        val meta = if (path.isNullOrEmpty()) null else runCatching {
            com.wpspasswordmanager.business.FileMetaFactory.getFileMeta(path)
        }.getOrNull()
        updateOverlay {
            it.copy(
                docName = name,
                encrypted = !meta?.uid.isNullOrEmpty(),
                canPerm = meta?.writeAuth == true
            )
        }
    }

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

        // 调试 / 走查用：由外部显式要求立即显示气泡（跳过依赖无障碍拿到文档路径的前置判断）
        if (intent?.getBooleanExtra("show_bubble", false) == true && !isFloatingButtonVisible) {
            runCatching { initFloatingButton() }
        }

        // 无障碍侧在【添加密码】/【修改密码】窗口出现时拉起本服务：服务就绪后立即判定并显示，
        // 避免要等下一个无障碍事件才出现气泡（用户观感是延迟数秒）
        if (intent?.getBooleanExtra("show_if_dialog", false) == true) {
            showIfPasswordDialog(0)
        }

        // 不自动初始化悬浮按钮，只在需要时通过showFloatingButton方法显示

        return START_STICKY
    }

    override fun onDestroy() {
        super.onDestroy()
        stopOverlayWatchdog()
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

            overlayOwner?.destroy()
            overlayOwner = null
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
        // Compose 浮层：气泡 / 未填充面板 / 已填充面板三态由 AssistantOverlay 负责画
        val host = com.wpspasswordmanager.ui.compose.assistant.ComposeOverlayHost(this)
        overlayHost = host
        overlayOwner = com.wpspasswordmanager.ui.compose.assistant.ServiceOwner().also { it.attach(host) }
        host.content = {
            com.wpspasswordmanager.ui.compose.assistant.AssistantOverlay(
                state = overlayState.value,
                permTree = permTreeState.value,
                permActions = permActions,
                onOpen = { updateOverlay { it.copy(open = true) } },
                onClose = { updateOverlay { it.copy(open = false) } },
                onFill = { generateAndFillPassword() },
                onCopy = { viewPassword() },
                onPerm = { openPermTreeSheet() },
                onDone = { updateOverlay { it.copy(open = false, filled = false, filling = false) } },
                onDrag = { dx, dy -> dragOverlayBy(dx, dy) }
            )
        }
        floatingView = host

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

        overlayParams = params
        // 文档信息与「文档权限」可见性
        refreshOverlayDoc()
        // 面板展开 / 收起会让窗口尺寸变化，需要通知 WindowManager 重新测量
        host.addOnLayoutChangeListener { view, left, top, right, bottom, oldLeft, oldTop, oldRight, oldBottom ->
            if (right - left != oldRight - oldLeft || bottom - top != oldBottom - oldTop) {
                runCatching {
                    constrainPositionToScreen(params, view)
                    windowManager.updateViewLayout(view, params)
                }
            }
        }

        try {
            windowManager.addView(floatingView, params)
            constrainPositionToScreen(params, floatingView)
            windowManager.updateViewLayout(floatingView, params)
            isFloatingButtonVisible = true
            Log.d(TAG, "悬浮按钮添加成功")
            startOverlayWatchdog()
        } catch (e: Exception) {
            Log.e(TAG, "添加悬浮按钮失败", e)
        }
    }

    private fun removeFloatingButton() {
        // 无论isFloatingButtonVisible的状态如何，都尝试移除悬浮按钮
        stopOverlayWatchdog()
        // 方案a：服务常驻，隐藏只是移除视图；重置浮层状态，避免下次显示时带着旧的面板展开状态
        overlayState.value = com.wpspasswordmanager.ui.compose.assistant.AssistantUiState()
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
        updateOverlay { it.copy(open = true, filling = true) }

        // 填充密码到WPS
        AccessibilityServiceManager.getInstance().fillPassword(password)

        // 无障碍填充是异步的，稍后切到「已填入」态并展示密码
        mainHandler.postDelayed({
            updateOverlay { it.copy(filling = false, filled = true, password = password) }
        }, 700)
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

            val documentPath = WpsAccessibilityService.stableDocumentPath
            if (!documentPath.isNullOrEmpty()) {
                val fileName = java.io.File(documentPath).name
                val prefix = "\$n_"
                if (fileName.startsWith(prefix)) {
                    Log.d(TAG, "文件名为\$n_开头，不显示悬浮按钮: $fileName")
                    return
                }
            }
            
            // 文件类型验证
            if (!WpsAccessibilityService.isValidFileType(documentPath)) {
                Log.w(TAG, "文件类型不允许，不显示悬浮按钮")
                return
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

    /* ---------------- 悬浮窗 Compose 权限树（与 App 内 PermTreeSheet 同一套接口与交互） ---------------- */

    private val permActions by lazy {
        com.wpspasswordmanager.ui.compose.assistant.AssistantPermActions(
            toggleNode = { key ->
                updatePerm { pt ->
                    val sel = pt.selected.toMutableSet()
                    if (sel.contains(key)) {
                        sel.remove(key)
                    } else {
                        sel.add(key)
                        // 选中部门时，子孙由部门代表，移除其显式勾选，避免重复授权
                        com.wpspasswordmanager.ui.compose.findAuthNode(pt.roots, key)?.let { node ->
                            com.wpspasswordmanager.ui.compose.collectAuthKeys(node)
                                .filter { it != key }
                                .forEach { sel.remove(it) }
                        }
                    }
                    com.wpspasswordmanager.utils.LogManager.log(
                        TAG,
                        "权限树-勾选: key=$key, 选中=${sel.contains(key)}, 已选总数=${sel.size}" +
                            sel.take(20).joinToString("|", prefix="[", postfix="]"),
                        "DEBUG"
                    )
                    pt.copy(selected = sel, saveError = "")
                }
            },
            toggleExpand = { key ->
                updatePerm { pt ->
                    val ex = pt.expanded.toMutableSet()
                    if (!ex.remove(key)) ex.add(key)
                    pt.copy(expanded = ex)
                }
            },
            selectAll = {
                updatePerm { it.copy(selected = it.roots.map { r -> r.key }.toSet(), saveError = "") }
            },
            invert = {
                updatePerm { it.copy(selected = it.roots.map { r -> r.key }.toSet() - it.selected, saveError = "") }
            },
            expandAll = {
                updatePerm { pt ->
                    val all = com.wpspasswordmanager.ui.compose.collectDeptKeys(pt.roots).toSet()
                    pt.copy(expanded = if (pt.expanded.containsAll(all)) emptySet() else all)
                }
            },
            save = { savePermTreeSheet() },
            retry = { permTreeState.value?.docId?.let { loadPermTreeSheet(it) } },
            close = {
                permTreeState.value = null
                // 回到常规面板：恢复兜底看门狗
                if (isFloatingButtonVisible) startOverlayWatchdog()
            }
        )
    }

    private fun updatePerm(
        block: (com.wpspasswordmanager.ui.compose.PermTreeState) -> com.wpspasswordmanager.ui.compose.PermTreeState
    ) {
        val cur = permTreeState.value ?: return
        permTreeState.value = block(cur)
    }

    private fun openPermTreeSheet() {
        val path = com.wpspasswordmanager.monitor.WpsAccessibilityService.stableDocumentPath
        val meta = path?.let { com.wpspasswordmanager.business.FileMetaFactory.getFileMeta(it) }
        val uid = meta?.uid
        if (path.isNullOrEmpty() || uid.isNullOrEmpty()) {
            Toast.makeText(this, "未找到文档元数据，无法管理权限", Toast.LENGTH_SHORT).show()
            return
        }
        if (!isFloatingButtonVisible) {
            initFloatingButton()
        }
        // 权限树打开期间暂停兜底看门狗（其全树遍历虽已后台化，但暂停可完全排除干扰，关闭面板后恢复）
        stopOverlayWatchdog()
        updateOverlay { it.copy(open = true, filled = false, filling = false) }
        permTreeState.value = com.wpspasswordmanager.ui.compose.PermTreeState(
            docName = java.io.File(path).name,
            docId = uid,
            isTemp = meta?.isTempUid == true
        )
        com.wpspasswordmanager.utils.LogManager.log(TAG, "打开悬浮窗权限树: uid=$uid, isTemp=${meta.isTempUid}", "DEBUG")
        loadPermTreeSheet(uid)
    }

    private fun loadPermTreeSheet(docId: String) {
        // saving 一并复位：任何重载都不应让按钮停留在「保存中…」
        updatePerm { it.copy(loading = true, error = "", saving = false) }
        val token = ConfigStorage.getInstance(this).getUserInfo()?.token
        val q = java.net.URLEncoder.encode(docId, "UTF-8")
        com.wpspasswordmanager.utils.LogManager.log(
            TAG,
            "权限树-请求: GET /doc/auth/tree?docId=$q, token=${if (token.isNullOrEmpty()) "空" else "已获取"}",
            "DEBUG"
        )
        NetworkManager.getInstance(this).executeGetRequest("/doc/auth/tree?docId=$q", token, object : NetworkCallback {
            override fun onSuccess(response: String) {
                com.wpspasswordmanager.utils.LogManager.log(
                    TAG,
                    "权限树-响应: ${response.take(2000)}",
                    "DEBUG"
                )
                val obj = runCatching { org.json.JSONObject(response) }.getOrDefault(org.json.JSONObject())
                mainHandler.post {
                    // 快速切换文档时丢弃过期回调，避免 A 文档的树覆盖 B 文档
                    if (permTreeState.value?.docId != docId) return@post
                    if (obj.optInt("status", 0) != 200) {
                        com.wpspasswordmanager.utils.LogManager.log(
                            TAG,
                            "权限树-失败: status=${obj.optInt("status", 0)}, message=${obj.optString("message")}",
                            "DEBUG"
                        )
                        updatePerm { it.copy(loading = false, error = obj.optString("message", "加载权限树失败")) }
                        return@post
                    }
                    val arr = obj.optJSONArray("data") ?: org.json.JSONArray()
                    val roots = mutableListOf<com.wpspasswordmanager.ui.compose.AuthNode>()
                    val selected = mutableSetOf<String>()
                    val expanded = mutableSetOf<String>()
                    for (i in 0 until arr.length()) {
                        roots += com.wpspasswordmanager.ui.compose.parseAuthNode(
                            arr.getJSONObject(i), false, selected, expanded, 0
                        )
                    }
                    com.wpspasswordmanager.utils.LogManager.log(
                        TAG,
                        "权限树-解析完成: 顶级节点=${roots.size}, 数据长度=${arr.length()}, " +
                            "顶级Key=${roots.joinToString("|") { it.key }}, " +
                            "回显已选=${selected.size}${selected.take(20).joinToString("|", prefix="[", postfix="]")}",
                        "DEBUG"
                    )
                    updatePerm {
                        it.copy(loading = false, error = "", roots = roots, selected = selected, expanded = expanded, saveError = "")
                    }
                }
            }

            override fun onError(error: String) {
                com.wpspasswordmanager.utils.LogManager.log(
                    TAG,
                    "权限树-网络异常: $error",
                    "DEBUG"
                )
                mainHandler.post {
                    if (permTreeState.value?.docId != docId) return@post
                    updatePerm { it.copy(loading = false, error = error.ifEmpty { "网络异常，请稍后重试" }) }
                }
            }
        })
    }

    private fun savePermTreeSheet() {
        val pt = permTreeState.value ?: return
        if (pt.saving) return
        val docId = pt.docId ?: return
        if (pt.demo) {
            Toast.makeText(this, "演示数据，不会提交到服务器", Toast.LENGTH_SHORT).show()
            return
        }
        updatePerm { it.copy(saving = true, saveError = "") }

        // 服务端已 id 化：只认 sys_dept.id / sys_user.id；选中集合以 type:id 为键，dn 仅用于日志
        val deptIds = mutableListOf<Long>()
        val userIds = mutableListOf<Long>()
        val deptDns = mutableListOf<String>()
        val accountDns = mutableListOf<String>()
        var lostKeys = 0
        var missingId = 0
        pt.selected.forEach { key ->
            val node = com.wpspasswordmanager.ui.compose.findAuthNode(pt.roots, key)
            if (node == null) {
                lostKeys++
                return@forEach
            }
            if (node.id <= 0L) {
                missingId++
                return@forEach
            }
            if (node.isDept) {
                deptIds += node.id
                deptDns += node.dn ?: "-"
            } else {
                userIds += node.id
                accountDns += node.dn ?: "-"
            }
        }
        val body = org.json.JSONObject().apply {
            put("docId", docId)
            put("deptIdList", org.json.JSONArray(deptIds))
            put("userIdList", org.json.JSONArray(userIds))
            put("isTemp", pt.isTemp)
        }.toString()
        com.wpspasswordmanager.utils.LogManager.log(
            TAG,
            "权限保存-请求: POST /doc/auth/update, 勾选总数=${pt.selected.size}, " +
                "部门ID=${deptIds.size}$deptIds, 用户ID=${userIds.size}$userIds, " +
                "树中未找到=$lostKeys, 缺id=$missingId, body=${body.take(2000)}",
            "DEBUG"
        )
        com.wpspasswordmanager.utils.LogManager.log(
            TAG,
            "权限保存-DN: 部门=${deptDns.take(20).joinToString("|", prefix="[", postfix="]")}, " +
                "用户=${accountDns.take(20).joinToString("|", prefix="[", postfix="]")}",
            "DEBUG"
        )

        val token = ConfigStorage.getInstance(this).getUserInfo()?.token
        com.wpspasswordmanager.utils.LogManager.log(
            TAG,
            "权限保存-token: ${if (token.isNullOrEmpty()) "空" else "已获取"}",
            "DEBUG"
        )
        NetworkManager.getInstance(this).executePostRequest("/doc/auth/update", body, token, object : NetworkCallback {
            override fun onSuccess(response: String) {
                com.wpspasswordmanager.utils.LogManager.log(
                    TAG,
                    "权限保存-响应: ${response.take(2000)}",
                    "DEBUG"
                )
                val obj = runCatching { org.json.JSONObject(response) }.getOrDefault(org.json.JSONObject())
                mainHandler.post {
                    if (permTreeState.value?.docId != docId) return@post
                    if (obj.optInt("status", 0) == 200) {
                        Toast.makeText(this@FloatingButtonService, "权限已保存", Toast.LENGTH_SHORT).show()
                        // 必须先落 saving=false，否则 reload 期间按钮会永久停在「保存中…」
                        updatePerm { it.copy(saving = false, saveError = "", savedAt = System.currentTimeMillis()) }
                        loadPermTreeSheet(docId)
                    } else {
                        val msg = obj.optString("message", "保存失败")
                        com.wpspasswordmanager.utils.LogManager.log(
                            TAG,
                            "权限保存-失败: status=${obj.optInt("status", 0)}, message=$msg",
                            "DEBUG"
                        )
                        updatePerm { it.copy(saving = false, saveError = "上次失败：$msg，选择已保留") }
                    }
                }
            }

            override fun onError(error: String) {
                com.wpspasswordmanager.utils.LogManager.log(
                    TAG,
                    "权限保存-网络异常: $error",
                    "DEBUG"
                )
                mainHandler.post {
                    if (permTreeState.value?.docId != docId) return@post
                    updatePerm { it.copy(saving = false, saveError = "上次失败：${error.ifEmpty { "网络异常" }}，选择已保留") }
                }
            }
        })
    }
}
