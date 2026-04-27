package com.wpspasswordmanager.utils

import android.content.Context
import android.os.Environment
import java.io.*
import java.text.SimpleDateFormat
import java.util.*

object LogManager {

    private const val LOG_DIR = "wps_password_logs"
    private const val LOG_FILE_NAME = "app_log.txt"
    private const val MAX_LOG_SIZE = 1024 * 1024 // 1MB
    private const val MAX_LOG_DAYS = 7 // 保留7天日志

    // 获取日志文件路径
    private fun getLogFile(context: Context): File {
        val logDir = File(context.filesDir, LOG_DIR)
        if (!logDir.exists()) {
            logDir.mkdirs()
        }
        return File(logDir, LOG_FILE_NAME)
    }

    // 写入日志
    fun log(tag: String, message: String, level: String = "INFO") {
        val timestamp = SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSS", Locale.getDefault()).format(Date())
        val logEntry = "[$timestamp] [$level] [$tag] $message"
        
        try {
            val file = getLogFile(ContextProvider.context)
            val writer = FileWriter(file, true)
            writer.appendLine(logEntry)
            writer.flush()
            writer.close()
            
            // 检查日志文件大小，超过限制则清理
            checkLogSize(file)
            // 清理过期日志
            cleanExpiredLogs()
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    // 检查日志文件大小
    private fun checkLogSize(file: File) {
        if (file.length() > MAX_LOG_SIZE) {
            // 清空日志文件
            try {
                val writer = FileWriter(file, false)
                writer.write("[${SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSS", Locale.getDefault()).format(Date())}] [INFO] [LogManager] Log file cleared due to size limit\n")
                writer.flush()
                writer.close()
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    // 清理过期日志
    private fun cleanExpiredLogs() {
        val file = getLogFile(ContextProvider.context)
        if (!file.exists()) return
        
        try {
            val reader = BufferedReader(FileReader(file))
            val lines = mutableListOf<String>()
            val cutoffTime = System.currentTimeMillis() - (MAX_LOG_DAYS * 24 * 60 * 60 * 1000)
            
            var line: String?
            while (reader.readLine().also { line = it } != null) {
                val timestampStr = line?.substringAfter("[")?.substringBefore("]")
                if (timestampStr != null) {
                    try {
                        val timestamp = SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSS", Locale.getDefault()).parse(timestampStr)?.time
                        if (timestamp != null && timestamp > cutoffTime) {
                            lines.add(line!!)
                        }
                    } catch (e: Exception) {
                        // 解析时间失败，保留该日志
                        lines.add(line!!)
                    }
                } else {
                    lines.add(line!!)
                }
            }
            reader.close()
            
            // 重写日志文件
            val writer = FileWriter(file, false)
            for (logLine in lines) {
                writer.appendLine(logLine)
            }
            writer.flush()
            writer.close()
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    // 获取所有日志
    fun getLogs(): List<String> {
        val logs = mutableListOf<String>()
        try {
            val file = getLogFile(ContextProvider.context)
            if (!file.exists()) return logs
            
            val reader = BufferedReader(FileReader(file))
            var line: String?
            while (reader.readLine().also { line = it } != null) {
                logs.add(line!!)
            }
            reader.close()
        } catch (e: Exception) {
            e.printStackTrace()
        }
        return logs // 最新的日志在后面
    }

    // 清除所有日志
    fun clearLogs() {
        try {
            val file = getLogFile(ContextProvider.context)
            if (file.exists()) {
                file.delete()
                file.createNewFile()
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    // 上下文提供者，用于获取应用上下文
    object ContextProvider {
        lateinit var context: Context
        
        fun initialize(context: Context) {
            this.context = context.applicationContext
        }
    }
}