package com.wpspasswordmanager

import android.app.Application
import android.os.FileObserver
import android.util.Log
import com.wpspasswordmanager.business.FileMetaFactory
import com.wpspasswordmanager.business.FileMetaManager
import java.io.File

class WpsPasswordManagerApplication : Application() {

    companion object {
        private const val TAG = "WpsPasswordManagerApplication"
        private const val WPS_MANAGEMENT_DIR = "WpsManagement"
        private const val DEFAULT_DEBOUNCE_DELAY = 1000L // 默认防抖延迟时间，单位毫秒
        private const val MIN_DEBOUNCE_DELAY = 500L // 最小防抖延迟时间，单位毫秒
        private const val MAX_DEBOUNCE_DELAY = 2000L // 最大防抖延迟时间，单位毫秒
        private const val LOG_TAIL_SIZE = 1024 // 日志打印的文件尾部大小，单位字节
        private const val PLUGIN_OPERATION_TIMEOUT = 1000L // 插件操作超时时间，单位毫秒
        lateinit var instance: WpsPasswordManagerApplication
            private set
    }

    // 防抖延迟时间，单位毫秒
    private var debounceDelay: Long = DEFAULT_DEBOUNCE_DELAY

    // FileObserver 实例
    private var fileObserver: FileObserver? = null

    // Handler 和防抖任务
    private var handler: android.os.Handler? = null
    private var debounceRunnable: Runnable? = null
    private var isHandlingEvent = false

    // 插件操作时间戳
    private val pluginOperationTimestamp = java.util.concurrent.atomic.AtomicLong(0)

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
                    Log.d(TAG, "监听到文件写入完成事件: $fullPath")
                    if (isPluginOperation()) {
                        Log.d(TAG, "跳过由插件引起的CLOSE_WRITE事件: $fullPath")
                    } else {
                        handleFileCloseWrite(fullPath)
                        Log.d(TAG, "文件删除: $fullPath")
                    }
                }

                DELETE -> {
                    if (isPluginOperation()) {
                        Log.d(TAG, "跳过由插件引起的DELETE事件: $fullPath")
                    } else {
                        FileMetaFactory.clearFile(fullPath)
                        Log.d(TAG, "文件删除: $fullPath")
                    }
                }

                MOVED_FROM -> {
                    if (isPluginOperation()) {
                        Log.d(TAG, "跳过由插件引起的文件重命名事件(原文件): $fullPath")
                    } else {
                        FileMetaFactory.clearFile(fullPath)
                        Log.d(TAG, "文件重命名(原文件): $fullPath")
                    }
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
                        if (!isPluginOperation()) {
                            handleFileCloseWrite(fullPath)
                        } else {
                            Log.d(TAG, "跳过由插件引起的MOVED_TO事件: $fullPath")
                        }
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

                val fileMeta = FileMetaFactory.getFileMeta(filePath)
                if (fileMeta == null) {
                    Log.d(TAG, "未找到文件相关元数据: $filePath")
                    return@Runnable
                }

                // 写入密码到文件
                FileMetaManager.getInstance().writeMetaDataToFile(file, fileMeta)

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
            "[时间戳: ${System.currentTimeMillis()}] 延迟 ${debounceDelay}ms 执行密码写入: $filePath"
        )
        handler?.postDelayed(runnable, debounceDelay)
    }

    /**
     * 设置插件操作标志
     */
    fun setPluginOperation(operating: Boolean) {
        if (operating) {
            pluginOperationTimestamp.set(System.currentTimeMillis())
            Log.d(TAG, "设置插件操作标志，时间戳: ${pluginOperationTimestamp.get()}")
        }
    }

    /**
     * 检查是否为插件操作
     * @return true if the event was caused by a plugin operation within the timeout period
     */
    fun isPluginOperation(): Boolean {
        val timestamp = pluginOperationTimestamp.get()
        val currentTime = System.currentTimeMillis()
        val isPluginOp = currentTime - timestamp <= PLUGIN_OPERATION_TIMEOUT
        if (isPluginOp) {
            Log.d(TAG, "检测到插件操作，时间戳差: ${currentTime - timestamp}ms")
        }
        return isPluginOp
    }


    /**
     * 设置防抖延迟时间
     * @param delayMs 延迟时间，单位毫秒，范围：500ms-2000ms
     */
    fun setDebounceDelay(delayMs: Long) {
        debounceDelay = when {
            delayMs < MIN_DEBOUNCE_DELAY -> {
                Log.w(TAG, "防抖延迟时间小于最小值，设置为最小值: ${MIN_DEBOUNCE_DELAY}ms")
                MIN_DEBOUNCE_DELAY
            }

            delayMs > MAX_DEBOUNCE_DELAY -> {
                Log.w(TAG, "防抖延迟时间大于最大值，设置为最大值: ${MAX_DEBOUNCE_DELAY}ms")
                MAX_DEBOUNCE_DELAY
            }

            else -> {
                Log.d(TAG, "设置防抖延迟时间: ${delayMs}ms")
                delayMs
            }
        }
    }

    /**
     * 获取当前防抖延迟时间
     * @return 当前防抖延迟时间，单位毫秒
     */
    fun getDebounceDelay(): Long {
        return debounceDelay
    }
}
