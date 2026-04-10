package com.wpspasswordmanager

import android.app.Application
import android.os.FileObserver
import android.util.Log
import com.wpspasswordmanager.business.FileMetaFactory
import java.io.File

class WpsPasswordManagerApplication : Application() {

    companion object {
        private const val TAG = "WpsPasswordManagerApplication"
        private const val WPS_MANAGEMENT_DIR = "WpsManagement"
        private const val DEBOUNCE_DELAY = 2000L // 防抖延迟时间，单位毫秒
        private const val LOG_TAIL_SIZE = 1024 // 日志打印的文件尾部大小，单位字节
        lateinit var instance: WpsPasswordManagerApplication
            private set
    }

    // FileObserver 实例
    private var fileObserver: FileObserver? = null

    // Handler 和防抖任务
    private var handler: android.os.Handler? = null
    private var debounceRunnable: Runnable? = null
    private var isHandlingEvent = false

    override fun onCreate() {
        super.onCreate()
        instance = this

        // 初始化文件观察者
        initFileObserver()
    }

    /**
     * 初始化文件观察者，只监听 WpsManagement 目录
     */
    private fun initFileObserver() {
        val documentsDir =
            android.os.Environment.getExternalStoragePublicDirectory(android.os.Environment.DIRECTORY_DOCUMENTS)
        val wpsManagementDir = File(documentsDir, WPS_MANAGEMENT_DIR)

        // 确保目录存在
        if (!wpsManagementDir.exists()) {
            wpsManagementDir.mkdirs()
            Log.d(TAG, "创建 WpsManagement 目录: ${wpsManagementDir.absolutePath}")
        }

        // 检查目录是否存在且可访问
        if (wpsManagementDir.exists() && wpsManagementDir.isDirectory && wpsManagementDir.canRead()) {
            Log.d(TAG, "WpsManagement 目录存在且可访问: ${wpsManagementDir.absolutePath}")
            // 列出目录内容
            val files = wpsManagementDir.listFiles()
            if (files != null && files.isNotEmpty()) {
                Log.d(TAG, "WpsManagement 目录包含 ${files.size} 个文件/目录")
                for (file in files) {
                    Log.d(TAG, "  - ${file.name} (${if (file.isDirectory) "目录" else "文件"})")
                }
            } else {
                Log.d(TAG, "WpsManagement 目录为空")
            }
        } else {
            Log.e(TAG, "WpsManagement 目录不存在或不可访问: ${wpsManagementDir.absolutePath}")
        }

        // 使用简单文件观察者，只监控WpsManagement目录
        fileObserver = SimpleFileObserver(wpsManagementDir.absolutePath)
        fileObserver?.startWatching()
        Log.d(TAG, "文件观察者已启动，监听目录: ${wpsManagementDir.absolutePath}")
    }

    override fun onTerminate() {
        super.onTerminate()
        // 停止文件观察者
        fileObserver?.stopWatching()
        Log.d(TAG, "文件观察者已停止")
    }

    /**
     * 简单文件观察者，只监控指定目录
     */
    private inner class SimpleFileObserver(path: String) : FileObserver(path, ALL_EVENTS) {
        private val rootPath = path

        override fun onEvent(event: Int, path: String?) {
            if (path == null) return

            val fullPath = File(rootPath, path).absolutePath

            when (event and ALL_EVENTS) {
                CLOSE_WRITE -> {
                    // 处理文件写入完成事件
                    handleFileCloseWrite(fullPath)
                }

                DELETE -> {
                    // 处理文件删除事件，清理缓存
                    FileMetaFactory.clearFile(fullPath)
                    Log.d(TAG, "文件删除: $fullPath")
                }

                MOVED_FROM -> {
                    // 处理文件重命名（原文件），清理缓存
                    FileMetaFactory.clearFile(fullPath)
                    Log.d(TAG, "文件重命名(原文件): $fullPath")
                }

                MOVED_TO -> {
                    // 处理文件重命名（新文件），可能是WPS的保存操作
                    Log.d(TAG, "监听到文件移动完成: $fullPath")
                    // 检查是否是我们监控的文件类型
                    if (fullPath.endsWith(".docx") || fullPath.endsWith(".doc") || fullPath.endsWith(
                            ".xlsx"
                        ) || fullPath.endsWith(".xls") || fullPath.endsWith(".pptx") || fullPath.endsWith(
                            ".ppt"
                        )
                    ) {
                        handleFileCloseWrite(fullPath)
                    }
                }
            }
        }
    }

    /**
     * 处理文件写入完成事件
     * 使用异步防抖处理模式，参考 FileSystemEventListener 的实现
     */
    private fun handleFileCloseWrite(filePath: String) {
        Log.d(TAG, "[时间戳: ${System.currentTimeMillis()}] 监听到文件写入完成事件: $filePath")

        // 防抖处理：取消之前的任务，只处理最后一次事件
        debounceRunnable?.let { handler?.removeCallbacks(it) }

        if (handler == null) {
            handler = android.os.Handler(android.os.Looper.getMainLooper())
        }

        val runnable = Runnable {
            try {
                // 检查是否已经处理过此事件
                if (isHandlingEvent) {
                    Log.d(
                        TAG,
                        "[时间戳: ${System.currentTimeMillis()}] 事件已在处理中，跳过: $filePath"
                    )
                    return@Runnable
                }
                isHandlingEvent = true

                Log.d(
                    TAG,
                    "[时间戳: ${System.currentTimeMillis()}] 开始处理文件写入事件: $filePath"
                )

                // 检查文件状态
                val file = File(filePath)
                Log.d(
                    TAG,
                    "文件状态 - 存在: ${file.exists()}, 可写: ${file.canWrite()}, 大小: ${file.length()} 字节"
                )

                var password = FileMetaFactory.getWritePassword(filePath)
                Log.d(TAG, "应用执行getWritePassword结果: $password")

                // 卫语句：如果密码为null，直接返回
                if (password == null) {
                    Log.d(TAG, "元数据中未找到文件密码: $filePath")
                    return@Runnable
                }

                Log.d(TAG, "准备将密码写入文件: $password")

                // 写入密码到文件
                writePasswordToFile(file, filePath, password)

            } catch (e: Exception) {
                Log.e(TAG, "处理文件写入事件失败", e)
            } finally {
                isHandlingEvent = false
                Log.d(TAG, "文件写入事件处理完成: $filePath")
            }
        }

        debounceRunnable = runnable

        // 延迟执行，确保只处理最后一次事件
        Log.d(
            TAG,
            "[时间戳: ${System.currentTimeMillis()}] 延迟 ${DEBOUNCE_DELAY}ms 执行密码写入: $filePath"
        )
        handler?.postDelayed(runnable, DEBOUNCE_DELAY)
    }

    /**
     * 将密码写入文件
     */
    private fun writePasswordToFile(file: File, filePath: String, password: String) {
        try {
            if (!file.exists() || !file.canWrite()) {
                Log.e(TAG, "文件不存在或不可写: $filePath")
                return
            }

            Log.d(TAG, "开始写入密码到文件")
            val success = com.wpspasswordmanager.business.ZipExtraFieldManager.getInstance()
                .writePassword(filePath, password)
            if (success) {
                Log.d(TAG, "成功将密码写入文件: $filePath")
                logFileTail(filePath)
            } else {
                Log.e(TAG, "密码写入失败: $filePath")
            }
        } catch (e: Exception) {
            Log.e(TAG, "处理文件写入完成事件失败", e)
        }
    }


    /**
     * 打印文件zip尾部最后1KB的内容，只输出WPPM标记相关的内容
     */
    private fun logFileTail(filePath: String?) {
        try {
            val file = File(filePath)
            if (file.exists() && file.canRead()) {
                val fileLength = file.length()
                val startPos = if (fileLength > LOG_TAIL_SIZE) fileLength - LOG_TAIL_SIZE else 0
                val buffer = ByteArray(LOG_TAIL_SIZE)

                file.inputStream().use { inputStream ->
                    inputStream.skip(startPos)
                    val bytesRead = inputStream.read(buffer)
                    if (bytesRead > 0) {
                        // 查找WPPM标记
                        val wppmSignature = "WPPM"
                        val wppmBytes = wppmSignature.toByteArray()
                        val bufferContent = buffer.sliceArray(0 until bytesRead)

                        // 从后向前搜索WPPM标记
                        var wppmIndex = -1
                        for (i in bytesRead - wppmBytes.size downTo 0) {
                            var match = true
                            for (j in wppmBytes.indices) {
                                if (buffer[i + j] != wppmBytes[j]) {
                                    match = false
                                    break
                                }
                            }
                            if (match) {
                                wppmIndex = i
                                break
                            }
                        }

                        if (wppmIndex != -1) {
                            // 只输出WPPM标记及其后续内容
                            val wppmContent = String(buffer, wppmIndex, bytesRead - wppmIndex)
                            Log.d(TAG, "文件尾部WPPM标记内容: $wppmContent")
                        } else {
                            Log.d(TAG, "文件尾部未找到WPPM标记")
                        }
                    }
                }
            } else {
                Log.e(TAG, "文件不存在或不可读，无法打印尾部内容")
            }
        } catch (e: Exception) {
            Log.e(TAG, "打印文件尾部内容失败", e)
        }
    }
}
