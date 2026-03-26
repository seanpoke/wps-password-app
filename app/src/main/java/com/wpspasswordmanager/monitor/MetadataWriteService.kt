package com.wpspasswordmanager.monitor

import android.app.Service
import android.content.Intent
import android.os.IBinder
import android.util.Log
import com.wpspasswordmanager.ui.AppNotificationManager

class MetadataWriteService : Service() {

    companion object {
        private const val TAG = "MetadataWriteService"
    }

    override fun onBind(intent: Intent?): IBinder? {
        return null
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val documentPath = intent?.getStringExtra("documentPath")
        Log.d(TAG, "启动元数据服务，文档路径: $documentPath")

        // 显示通知
        AppNotificationManager.getInstance(this).showOperationNotification(
            "元数据服务",
            "元数据服务已启动"
        )

        // 在后台线程中执行操作
        Thread {
            try {
                if (documentPath != null) {
                    // 处理指定文档
                    Log.d(TAG, "处理文档: $documentPath")
                } else {
                    // 处理所有文档
                    Log.d(TAG, "处理所有文档")
                }
                // 显示完成通知
                AppNotificationManager.getInstance(this).showOperationNotification(
                    "元数据服务",
                    "元数据服务已完成"
                )
            } catch (e: Exception) {
                Log.e(TAG, "元数据服务失败", e)
                // 显示错误通知
                AppNotificationManager.getInstance(this).showErrorNotification(
                    "元数据服务失败",
                    "服务过程中发生错误"
                )
            } finally {
                // 完成后停止服务
                stopSelf(startId)
            }
        }.start()

        return START_NOT_STICKY
    }
}