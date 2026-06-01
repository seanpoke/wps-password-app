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
import java.util.concurrent.atomic.AtomicInteger

object LogManager {

    private const val LOG_DIR = "wps_password_logs"
    private const val LOG_FILE_NAME = "app_log.txt"
    private const val MAX_LOG_SIZE = 1024 * 1024 // 1MB
    private const val MAX_LOG_DAYS = 7 // 保留7天日志
    private const val BATCH_WRITE_SIZE = 50 // 批量写入大小
    private const val FLUSH_INTERVAL_MS = 100 // 刷新间隔(毫秒)
    private const val MAX_QUEUE_SIZE = 100000 // 队列最大容量
    private const val MAX_CACHE_SIZE = 1000 // 内存缓存最大容量
    private const val MAX_ARCHIVE_COUNT = 5 // 最大归档文件数量
    private const val MAX_RETRY_COUNT = 3 // 最大重试次数

    // ThreadLocal 保证 SimpleDateFormat 线程安全
    private val dateFormatThreadLocal = object : ThreadLocal<SimpleDateFormat>() {
        override fun initialValue(): SimpleDateFormat {
            return SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSS", Locale.getDefault())
        }
    }

    private val logQueue = ConcurrentLinkedQueue<LogEntry>()
    private val logCacheQueue = ConcurrentLinkedQueue<LogEntry>() // 内存缓存队列，用于展示
    private val logVersion = AtomicInteger(0) // 日志版本号，每次日志变化时递增（原子操作）
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
        val message: String,
        val retryCount: Int = 0 // 重试次数，默认0
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
        val timestamp = dateFormatThreadLocal.get()?.format(Date()) ?: ""
        val logEntry = LogEntry(timestamp, level.toUpperCase(), tag, message)
        
        printToLogcat(tag, message, level)
        
        // 加入写入队列
        if (logQueue.size >= MAX_QUEUE_SIZE) {
            logQueue.poll()
        }
        logQueue.offer(logEntry)
        
        // 加入内存缓存队列（用于展示）
        if (logCacheQueue.size >= MAX_CACHE_SIZE) {
            logCacheQueue.poll() // 满了就移除最早的
        }
        logCacheQueue.offer(logEntry)
        
        logVersion.incrementAndGet() // 更新版本号（原子操作）
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
            // 写入失败时，只有重试次数未达到上限的日志才重新放回队列
            entries.forEach { entry ->
                if (entry.retryCount < MAX_RETRY_COUNT) {
                    // 重试次数+1后放回队列
                    logQueue.offer(entry.copy(retryCount = entry.retryCount + 1))
                } else {
                    // 超过最大重试次数，记录错误日志
                    printToLogcat("LogManager", "Log entry dropped after ${MAX_RETRY_COUNT} retries: [${entry.timestamp}] [${entry.level}] [${entry.tag}] ${entry.message}", "ERROR")
                }
            }
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
            rotateLogFile(file)
        }
    }
    
    /**
     * 执行日志文件轮转
     * 轮转策略：app_log.txt → app_log.1.txt → app_log.2.txt → ... → app_log.5.txt（最大5个归档）
     */
    private fun rotateLogFile(currentFile: File) {
        try {
            val logDir = currentFile.parentFile ?: return
            
            // 从最旧的归档开始，依次前移
            for (i in MAX_ARCHIVE_COUNT downTo 1) {
                val oldArchive = File(logDir, "${LOG_FILE_NAME}.${i}")
                if (i == MAX_ARCHIVE_COUNT) {
                    // 最旧的归档直接删除
                    oldArchive.delete()
                } else if (oldArchive.exists()) {
                    // 前移：app_log.1 → app_log.2
                    val newArchive = File(logDir, "${LOG_FILE_NAME}.${i + 1}")
                    oldArchive.renameTo(newArchive)
                }
            }
            
            // 将当前日志文件重命名为第一个归档
            val firstArchive = File(logDir, "${LOG_FILE_NAME}.1")
            if (currentFile.exists()) {
                currentFile.renameTo(firstArchive)
            }
            
            // 创建新的空日志文件
            currentFile.createNewFile()
            
            printToLogcat("LogManager", "Log file rotated, archive created: ${LOG_FILE_NAME}.1", "INFO")
        } catch (e: IOException) {
            printToLogcat("LogManager", "Error rotating log file: ${e.message}", "ERROR")
        }
    }

    fun getLogs(): List<String> {
        // 从内存缓存队列读取，不读文件
        return logCacheQueue.toList().map { entry ->
            "[${entry.timestamp}] [${entry.level}] [${entry.tag}] ${entry.message}"
        }
    }
    
    /**
     * 获取当前日志版本号
     */
    fun getLogVersion(): Int {
        return logVersion.get()
    }
    
    /**
     * 从指定位置开始获取新增日志（用于增量刷新）
     * @param fromIndex 起始位置
     * @return 从 fromIndex 之后的新日志
     */
    fun getNewLogs(fromIndex: Int): List<String> {
        val allLogs = logCacheQueue.toList()
        if (fromIndex < 0 || fromIndex >= allLogs.size) {
            return emptyList()
        }
        return allLogs.subList(fromIndex, allLogs.size).map { entry ->
            "[${entry.timestamp}] [${entry.level}] [${entry.tag}] ${entry.message}"
        }
    }
    
    /**
     * 获取筛选后的日志列表
     * @param level 日志级别筛选（null或"所有"表示不筛选）
     * @param keyword 关键词筛选（null或空表示不筛选）
     * @return 筛选后的日志列表
     */
    fun getFilteredLogs(level: String?, keyword: String?): List<String> {
        return logCacheQueue.filter { entry ->
            val matchesLevel = level.isNullOrEmpty() || level == "所有" || entry.level == level
            val matchesKeyword = keyword.isNullOrEmpty() || entry.message.toLowerCase().contains(keyword.toLowerCase()) ||
                    entry.tag.toLowerCase().contains(keyword.toLowerCase())
            matchesLevel && matchesKeyword
        }.map { entry ->
            "[${entry.timestamp}] [${entry.level}] [${entry.tag}] ${entry.message}"
        }
    }
    
    /**
     * 从文件读取历史日志（包括所有归档文件）
     * 按时间顺序返回：最早的归档文件 -> 最新的归档文件 -> 当前文件
     */
    fun getHistoricalLogs(): List<String> {
        val logs = mutableListOf<String>()
        try {
            val currentFile = getLogFile(ContextProvider.context)
            val logDir = currentFile.parentFile ?: return logs
            
            // 先读取归档文件（从最旧的开始）
            for (i in MAX_ARCHIVE_COUNT downTo 1) {
                val archiveFile = File(logDir, "${LOG_FILE_NAME}.${i}")
                if (archiveFile.exists()) {
                    BufferedReader(FileReader(archiveFile)).use { reader ->
                        var line: String?
                        while (reader.readLine().also { line = it } != null) {
                            line?.let { logs.add(it) }
                        }
                    }
                }
            }
            
            // 最后读取当前日志文件
            if (currentFile.exists()) {
                BufferedReader(FileReader(currentFile)).use { reader ->
                    var line: String?
                    while (reader.readLine().also { line = it } != null) {
                        line?.let { logs.add(it) }
                    }
                }
            }
        } catch (e: IOException) {
            printToLogcat("LogManager", "读取历史日志失败: ${e.message}", "ERROR")
        }
        return logs
    }

    fun clearLogs() {
        synchronized(flushLock) {
            logQueue.clear()
            logCacheQueue.clear() // 同时清空内存缓存
            logVersion.incrementAndGet() // 更新版本号（原子操作）
            try {
                val file = getLogFile(ContextProvider.context)
                if (file.exists()) {
                    file.delete()
                    file.createNewFile()
                }
                } catch (e: IOException) {
                    printToLogcat("LogManager", "Error clearing log file: ${e.message}", "ERROR")
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