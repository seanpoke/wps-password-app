package com.wpspasswordmanager.utils

import android.content.Context
import android.util.Log
import java.io.*
import java.text.SimpleDateFormat
import java.util.*
import java.util.concurrent.ConcurrentLinkedQueue
import java.util.concurrent.Executors
import java.util.concurrent.ScheduledExecutorService
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean

object LogManager {

    private const val LOG_DIR = "wps_password_logs"
    private const val LOG_FILE_NAME = "app_log.txt"
    private const val MAX_LOG_SIZE = 1024 * 1024 // 1MB
    private const val MAX_LOG_DAYS = 7 // 保留7天日志
    private const val BATCH_WRITE_SIZE = 50 // 批量写入大小
    private const val FLUSH_INTERVAL_MS = 100 // 刷新间隔(毫秒)
    private const val MAX_QUEUE_SIZE = 100000 // 队列最大容量

    private val logQueue = ConcurrentLinkedQueue<LogEntry>()
    private val executor: ScheduledExecutorService = Executors.newSingleThreadScheduledExecutor { r ->
        val thread = Thread(r, "LogManager-Writer")
        thread.isDaemon = true
        thread.priority = Thread.MIN_PRIORITY
        thread
    }
    private val isRunning = AtomicBoolean(false)
    private val flushLock = Any()

    data class LogEntry(
        val timestamp: String,
        val level: String,
        val tag: String,
        val message: String
    )

    init {
        startLogWriter()
    }

    private fun startLogWriter() {
        if (isRunning.compareAndSet(false, true)) {
            executor.scheduleAtFixedRate({
                flushLogs()
            }, 0, FLUSH_INTERVAL_MS.toLong(), TimeUnit.MILLISECONDS)
        }
    }

    fun log(tag: String, message: String, level: String = "INFO") {
        val timestamp = SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSS", Locale.getDefault()).format(Date())
        val logEntry = LogEntry(timestamp, level.toUpperCase(), tag, message)
        
        printToLogcat(tag, message, level)
        
        if (logQueue.size >= MAX_QUEUE_SIZE) {
            logQueue.poll()
        }
        logQueue.offer(logEntry)
    }

    private fun printToLogcat(tag: String, message: String, level: String) {
        when (level.toUpperCase()) {
            "VERBOSE" -> Log.v(tag, message)
            "DEBUG" -> Log.d(tag, message)
            "INFO" -> Log.i(tag, message)
            "WARN" -> Log.w(tag, message)
            "ERROR" -> Log.e(tag, message)
            else -> Log.i(tag, message)
        }
    }

    fun logVerbose(tag: String, message: String) = log(tag, message, "VERBOSE")
    fun logDebug(tag: String, message: String) = log(tag, message, "DEBUG")
    fun logInfo(tag: String, message: String) = log(tag, message, "INFO")
    fun logWarn(tag: String, message: String) = log(tag, message, "WARN")
    fun logError(tag: String, message: String) = log(tag, message, "ERROR")

    private fun flushLogs() {
        if (logQueue.isEmpty()) return
        
        synchronized(flushLock) {
            if (logQueue.isEmpty()) return
            
            val batch = mutableListOf<LogEntry>()
            var count = 0
            while (count < BATCH_WRITE_SIZE && logQueue.isNotEmpty()) {
                val entry = logQueue.poll()
                entry?.let { batch.add(it) }
                count++
            }
            
            if (batch.isNotEmpty()) {
                writeLogsToFile(batch)
            }
        }
    }

    private fun writeLogsToFile(entries: List<LogEntry>) {
        try {
            val file = getLogFile(ContextProvider.context)
            FileWriter(file, true).use { writer ->
                for (entry in entries) {
                    val logLine = "[${entry.timestamp}] [${entry.level}] [${entry.tag}] ${entry.message}\n"
                    writer.write(logLine)
                }
                writer.flush()
            }
            
            checkLogSize(file)
        } catch (e: IOException) {
            entries.forEach { logQueue.offer(it) }
        }
    }

    private fun getLogFile(context: Context): File {
        val logDir = File(context.filesDir, LOG_DIR)
        if (!logDir.exists()) {
            logDir.mkdirs()
        }
        return File(logDir, LOG_FILE_NAME)
    }

    private fun checkLogSize(file: File) {
        if (file.length() > MAX_LOG_SIZE) {
            try {
                val header = "[${SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSS", Locale.getDefault()).format(Date())}] [INFO] [LogManager] Log file cleared due to size limit\n"
                FileWriter(file, false).use { writer ->
                    writer.write(header)
                    writer.flush()
                }
            } catch (e: IOException) {
                e.printStackTrace()
            }
        }
    }

    fun getLogs(): List<String> {
        val logs = mutableListOf<String>()
        try {
            val file = getLogFile(ContextProvider.context)
            if (!file.exists()) return logs
            
            BufferedReader(FileReader(file)).use { reader ->
                var line: String?
                while (reader.readLine().also { line = it } != null) {
                    logs.add(line!!)
                }
            }
        } catch (e: IOException) {
            e.printStackTrace()
        }
        return logs
    }

    fun clearLogs() {
        synchronized(flushLock) {
            logQueue.clear()
            try {
                val file = getLogFile(ContextProvider.context)
                if (file.exists()) {
                    file.delete()
                    file.createNewFile()
                }
            } catch (e: IOException) {
                e.printStackTrace()
            }
        }
    }

    fun shutdown() {
        if (isRunning.compareAndSet(true, false)) {
            flushLogs()
            executor.shutdown()
            try {
                if (!executor.awaitTermination(1, TimeUnit.SECONDS)) {
                    executor.shutdownNow()
                }
            } catch (e: InterruptedException) {
                executor.shutdownNow()
                Thread.currentThread().interrupt()
            }
        }
    }

    object ContextProvider {
        lateinit var context: Context
        
        fun initialize(context: Context) {
            this.context = context.applicationContext
        }
    }
}