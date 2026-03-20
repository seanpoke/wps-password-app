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
import com.wpspasswordmanager.R
import com.wpspasswordmanager.business.PasswordGenerator
import com.wpspasswordmanager.monitor.AccessibilityServiceManager

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
        initFloatingButton()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        Log.d(TAG, "悬浮按钮服务启动")
        
        // 启动前台服务
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val notificationManager = getSystemService(NOTIFICATION_SERVICE) as android.app.NotificationManager
            val channel = android.app.NotificationChannel(
                "floating_button_channel",
                "悬浮按钮服务",
                android.app.NotificationManager.IMPORTANCE_LOW
            )
            notificationManager.createNotificationChannel(channel)
            
            val notification = android.app.Notification.Builder(this, "floating_button_channel")
                .setContentTitle("WPS密码管理器")
                .setContentText("悬浮按钮服务正在运行")
                .setSmallIcon(R.mipmap.ic_launcher)
                .build()
            
            startForeground(1, notification)
        }
        
        return START_STICKY
    }

    override fun onDestroy() {
        super.onDestroy()
        Log.d(TAG, "悬浮按钮服务销毁")
        removeFloatingButton()
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
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,
            PixelFormat.TRANSLUCENT
        )

        params.gravity = Gravity.TOP or Gravity.END
        params.x = 0
        params.y = 100

        val generatePasswordButton = floatingView.findViewById<Button>(R.id.generate_password_button)
        generatePasswordButton.setOnClickListener {
            generateAndFillPassword()
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
                    params.x = event.rawX.toInt() - floatingView.width / 2
                    params.y = event.rawY.toInt() - floatingView.height / 2
                    windowManager.updateViewLayout(floatingView, params)
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
        if (isFloatingButtonVisible) {
            try {
                windowManager.removeView(floatingView)
                isFloatingButtonVisible = false
                Log.d(TAG, "悬浮按钮移除成功")
            } catch (e: Exception) {
                Log.e(TAG, "移除悬浮按钮失败", e)
            }
        }
    }

    private fun generateAndFillPassword() {
        // 生成密码
        val password = PasswordGenerator.getInstance().generatePassword()
        Log.d(TAG, "生成密码: $password")

        // 填充密码到WPS
        AccessibilityServiceManager.getInstance().fillPassword(password)
    }

    fun showFloatingButton() {
        if (!isFloatingButtonVisible) {
            // 再次检查权限
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                if (!Settings.canDrawOverlays(this)) {
                    Log.e(TAG, "没有显示在其他应用之上的权限，无法显示悬浮按钮")
                    return
                }
            }
            initFloatingButton()
        }
    }

    fun hideFloatingButton() {
        removeFloatingButton()
    }

    private fun showPermissionNotification() {
        val notificationManager = getSystemService(NOTIFICATION_SERVICE) as android.app.NotificationManager
        
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = android.app.NotificationChannel(
                "permission_channel",
                "权限通知",
                android.app.NotificationManager.IMPORTANCE_DEFAULT
            )
            notificationManager.createNotificationChannel(channel)
        }
        
        val intent = Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION)
        val pendingIntent = android.app.PendingIntent.getActivity(
            this,
            0,
            intent,
            android.app.PendingIntent.FLAG_IMMUTABLE
        )
        
        val notification = android.app.Notification.Builder(this)
            .setContentTitle("需要权限")
            .setContentText("请开启显示在其他应用之上的权限，以使用悬浮按钮功能")
            .setSmallIcon(R.mipmap.ic_launcher)
            .setContentIntent(pendingIntent)
            .setAutoCancel(true)
            .apply {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    setChannelId("permission_channel")
                }
            }
            .build()
        
        notificationManager.notify(1, notification)
        Log.d(TAG, "显示权限通知")
    }
}
