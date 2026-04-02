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
import com.wpspasswordmanager.business.PasswordGenerator
import com.wpspasswordmanager.business.PasswordStorage
import com.wpspasswordmanager.monitor.AccessibilityServiceManager
import com.wpspasswordmanager.monitor.WpsAccessibilityService

class FloatingButtonService : Service() {

    companion object {
        private const val TAG = "FloatingButtonService"
    }

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
            // 使用AppNotificationManager中已创建的通知频道
            val notification = androidx.core.app.NotificationCompat.Builder(this, "operation_channel")
                .setContentTitle("WPS密码管理器")
                .setContentText("悬浮按钮服务正在运行")
                .setSmallIcon(R.mipmap.ic_launcher)
                .setPriority(androidx.core.app.NotificationCompat.PRIORITY_LOW)
                .build()
            
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
        } catch (e: Exception) {
            Log.e(TAG, "移除悬浮按钮时发生异常", e)
            isFloatingButtonVisible = false
        } finally {
            // 清除AccessibilityServiceManager中的引用，确保下次启动服务时能够正确初始化
            AccessibilityServiceManager.getInstance().setFloatingButtonService(null)
            Log.d(TAG, "已清除AccessibilityServiceManager中的悬浮按钮服务引用")
        }
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
        params.x = 0
        params.y = 100

        val generatePasswordButton = floatingView.findViewById<Button>(R.id.generate_password_button)
        generatePasswordButton.setOnClickListener {
            generateAndFillPassword()
        }

        val viewPasswordButton = floatingView.findViewById<Button>(R.id.view_password_button)
        viewPasswordButton.setOnClickListener {
            viewPassword()
        }

        // 添加触摸事件，实现悬浮按钮的拖动
        floatingView.setOnTouchListener {
            v, event ->
            when (event.action) {
                MotionEvent.ACTION_DOWN -> {
                    // 开始拖动
                }
                MotionEvent.ACTION_MOVE -> {
                    // 更新位置
                    try {
                        params.x = event.rawX.toInt() - floatingView.width / 2
                        params.y = event.rawY.toInt() - floatingView.height / 2
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
            showOperationNotification("查看密码", "未找到文档路径")
            Toast.makeText(this, "未找到文档路径", Toast.LENGTH_SHORT).show()
            Log.e(TAG, "未找到文档路径")
            return
        }

        // 从文件扩展属性读取密码
        val password = PasswordStorage.getInstance().getPassword(this, documentPath)
        if (password != null && password.isNotEmpty()) {
            // 将密码复制到剪贴板
            copyToClipboard(password)
            // 显示密码通知
            showOperationNotification("查看密码", "密码已复制到剪贴板")
            // 显示Toast提示
            Toast.makeText(this, "密码已复制到剪贴板", Toast.LENGTH_LONG).show()
            Log.d(TAG, "从文件扩展属性读取密码成功: $password")
        } else {
            showOperationNotification("查看密码", "未找到存储的密码")
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
}
