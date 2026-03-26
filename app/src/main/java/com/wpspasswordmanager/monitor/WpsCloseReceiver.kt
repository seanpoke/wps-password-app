package com.wpspasswordmanager.monitor

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log

class WpsCloseReceiver : BroadcastReceiver() {

    companion object {
        private const val TAG = "WpsCloseReceiver"
    }

    override fun onReceive(context: Context, intent: Intent) {
        val action = intent.action
        Log.d(TAG, "收到广播: $action")

        // 处理 WPS 相关的关闭事件
        when (action) {
            Intent.ACTION_PACKAGE_REMOVED -> {
                val packageName = intent.dataString
                if (packageName?.contains("cn.wps.moffice") == true) {
                    Log.i(TAG, "WPS 被卸载，清理相关数据")
                    // 清理相关数据
                    handleWpsUninstall(context)
                }
            }
            Intent.ACTION_PACKAGE_FULLY_REMOVED -> {
                val packageName = intent.dataString
                if (packageName?.contains("cn.wps.moffice") == true) {
                    Log.i(TAG, "WPS 被完全卸载，清理相关数据")
                    // 清理相关数据
                    handleWpsUninstall(context)
                }
            }
            // 监听应用退出事件
            "android.intent.action.EXIT" -> {
                val packageName = intent.getStringExtra("packageName")
                if (packageName?.contains("cn.wps.moffice") == true) {
                    Log.i(TAG, "WPS 应用退出，触发元数据回写")
                    // 触发元数据回写
                    triggerMetadataWriteBack(context)
                }
            }
            // 监听文档关闭事件
            "com.wpspasswordmanager.action.DOCUMENT_CLOSED" -> {
                val documentPath = intent.getStringExtra("documentPath")
                Log.i(TAG, "WPS 文档关闭: $documentPath")
                // 触发元数据回写
                triggerMetadataWriteBack(context, documentPath)
            }
        }
    }

    private fun handleWpsUninstall(context: Context) {
        // 清理与 WPS 相关的数据
        Log.d(TAG, "清理 WPS 相关数据")
        // 这里可以添加清理逻辑，如删除缓存的密码等
    }

    private fun triggerMetadataWriteBack(context: Context, documentPath: String? = null) {
        Log.d(TAG, "触发元数据回写任务")
        
        // 启动后台服务进行元数据回写
        val serviceIntent = Intent(context, MetadataWriteService::class.java)
        if (documentPath != null) {
            serviceIntent.putExtra("documentPath", documentPath)
        }
        
        try {
            context.startService(serviceIntent)
            Log.d(TAG, "元数据回写服务已启动")
        } catch (e: Exception) {
            Log.e(TAG, "启动元数据回写服务失败", e)
        }
    }
}