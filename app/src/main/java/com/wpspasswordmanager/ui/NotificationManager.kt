package com.wpspasswordmanager.ui

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.app.NotificationCompat
import com.wpspasswordmanager.R

class AppNotificationManager private constructor(private val context: Context) {

    companion object {
        private const val TAG = "NotificationManager"
        private const val CHANNEL_ID_OPERATION = "operation_channel"
        private const val CHANNEL_ID_ERROR = "error_channel"
        private const val CHANNEL_ID_INFO = "info_channel"
        private const val NOTIFICATION_ID_OPERATION = 1
        private const val NOTIFICATION_ID_ERROR = 2
        private const val NOTIFICATION_ID_INFO = 3

        private var instance: AppNotificationManager? = null

        fun getInstance(context: Context): AppNotificationManager {
            if (instance == null) {
                instance = AppNotificationManager(context.applicationContext)
            }
            return instance!!
        }
    }

    private val notificationManager: android.app.NotificationManager

    init {
        notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as android.app.NotificationManager
        createNotificationChannels()
    }

    private fun createNotificationChannels() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            // 操作通知频道
            val operationChannel = NotificationChannel(
                CHANNEL_ID_OPERATION,
                "操作通知",
                NotificationManager.IMPORTANCE_LOW
            )
            operationChannel.description = "显示应用操作状态"
            notificationManager.createNotificationChannel(operationChannel)

            // 错误通知频道
            val errorChannel = NotificationChannel(
                CHANNEL_ID_ERROR,
                "错误通知",
                NotificationManager.IMPORTANCE_HIGH
            )
            errorChannel.description = "显示应用错误信息"
            notificationManager.createNotificationChannel(errorChannel)

            // 信息通知频道
            val infoChannel = NotificationChannel(
                CHANNEL_ID_INFO,
                "信息通知",
                NotificationManager.IMPORTANCE_DEFAULT
            )
            infoChannel.description = "显示应用信息"
            notificationManager.createNotificationChannel(infoChannel)
        }
    }

    /**
     * 显示操作通知
     */
    fun showOperationNotification(title: String, content: String, autoCancel: Boolean = true) {
        val notification = NotificationCompat.Builder(context, CHANNEL_ID_OPERATION)
            .setContentTitle(title)
            .setContentText(content)
            .setSmallIcon(R.mipmap.ic_launcher)
            .setAutoCancel(autoCancel)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .build()

        notificationManager.notify(NOTIFICATION_ID_OPERATION, notification)
    }

    /**
     * 显示错误通知
     */
    fun showErrorNotification(title: String, content: String, autoCancel: Boolean = true) {
        val notification = NotificationCompat.Builder(context, CHANNEL_ID_ERROR)
            .setContentTitle(title)
            .setContentText(content)
            .setSmallIcon(R.mipmap.ic_launcher)
            .setAutoCancel(autoCancel)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .build()

        notificationManager.notify(NOTIFICATION_ID_ERROR, notification)
    }

    /**
     * 显示信息通知
     */
    fun showInfoNotification(title: String, content: String, autoCancel: Boolean = true) {
        val notification = NotificationCompat.Builder(context, CHANNEL_ID_INFO)
            .setContentTitle(title)
            .setContentText(content)
            .setSmallIcon(R.mipmap.ic_launcher)
            .setAutoCancel(autoCancel)
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .build()

        notificationManager.notify(NOTIFICATION_ID_INFO, notification)
    }

    /**
     * 显示带点击操作的通知
     */
    fun showActionNotification(title: String, content: String, intent: Intent, autoCancel: Boolean = true) {
        val pendingIntent = PendingIntent.getActivity(
            context,
            0,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val notification = NotificationCompat.Builder(context, CHANNEL_ID_INFO)
            .setContentTitle(title)
            .setContentText(content)
            .setSmallIcon(R.mipmap.ic_launcher)
            .setContentIntent(pendingIntent)
            .setAutoCancel(autoCancel)
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .build()

        notificationManager.notify(NOTIFICATION_ID_INFO, notification)
    }

    /**
     * 取消所有通知
     */
    fun cancelAllNotifications() {
        notificationManager.cancelAll()
    }

    /**
     * 取消指定通知
     */
    fun cancelNotification(notificationId: Int) {
        notificationManager.cancel(notificationId)
    }
}