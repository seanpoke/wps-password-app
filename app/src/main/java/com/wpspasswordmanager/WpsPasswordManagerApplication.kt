package com.wpspasswordmanager

import android.app.Application
import android.os.FileObserver
import com.wpspasswordmanager.utils.LogManager
import com.wpspasswordmanager.business.FileMetaFactory
import com.wpspasswordmanager.business.FileMetaManager
import com.wpspasswordmanager.network.NetworkManager
import com.wpspasswordmanager.storage.ConfigStorage
import java.io.File

class WpsPasswordManagerApplication : Application() {

    companion object {
        private const val TAG = "WpsPasswordManagerApplication"
        private const val WPS_MANAGEMENT_DIR = "WpsManagement"
        private const val DEFAULT_DEBOUNCE_DELAY = 1000L // 默认防抖延迟时间，单位毫秒
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

        // 初始化 LogManager 的上下文提供者
        LogManager.ContextProvider.initialize(this)
        // 记录应用启动日志
        LogManager.log("Application", "WPS Password Manager started", "INFO")

        // 获取最新密钥信息
        fetchLatestKeyInfo()

        // 初始化文件观察者
        initFileObserver()
    }

    /**
     * 获取最新密钥信息并持久化存储
     */
    private fun fetchLatestKeyInfo() {
        LogManager.log(TAG, "开始获取最新密钥信息", "DEBUG")
        NetworkManager.getInstance(this).getLatestKey(object : com.wpspasswordmanager.network.NetworkCallback {
            override fun onSuccess(response: String) {
                try {
                    val json = org.json.JSONObject(response)
                    if (json.getInt("status") == 200) {
                        val data = json.getJSONObject("data")
                        val keyVersion = data.optString("keyVersion", "default")
                        val publicKey = data.optString("publicKey", "")

                        LogManager.log(TAG, "获取最新密钥信息成功: keyVersion=$keyVersion, publicKey=${if (publicKey.isNotEmpty()) "已获取" else "空"}", "DEBUG")

                        // 持久化存储到本地
                        val configStorage = ConfigStorage.getInstance(this@WpsPasswordManagerApplication)
                        if (keyVersion.isNotEmpty()) {
                            configStorage.saveKeyVersion(keyVersion)
                            LogManager.log(TAG, "keyVersion已保存到本地存储", "DEBUG")
                        }
                        if (publicKey.isNotEmpty()) {
                            configStorage.savePublicKey(publicKey)
                            LogManager.log(TAG, "publicKey已保存到本地存储", "DEBUG")
                        }
                    } else {
                        LogManager.log(TAG, "获取最新密钥信息失败，响应状态码不是200", "ERROR")
                    }
                } catch (e: Exception) {
                    LogManager.log(TAG, "解析密钥信息响应失败: ${e.message}", "ERROR")
                }
            }

            override fun onError(error: String) {
                LogManager.log(TAG, "获取最新密钥信息失败: $error", "ERROR")
                // 网络请求失败时使用默认值，ConfigStorage已设置默认值
            }

            override fun onComplete() {}
        })
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
            LogManager.log(TAG, "创建 WpsManagement 目录: ${wpsManagementDir.absolutePath}", "DEBUG")
        }

        // 检查目录是否存在且可访问
        if (wpsManagementDir.exists() && wpsManagementDir.isDirectory && wpsManagementDir.canRead()) {
            LogManager.log(TAG, "WpsManagement 目录存在且可访问: ${wpsManagementDir.absolutePath}", "DEBUG")
            // 列出目录内容
            val files = wpsManagementDir.listFiles()
            if (files != null && files.isNotEmpty()) {
                LogManager.log(TAG, "WpsManagement 目录包含 ${files.size} 个文件/目录", "DEBUG")
                for (file in files) {
                    LogManager.log(TAG, "  - ${file.name} (${if (file.isDirectory) "目录" else "文件"})")
                }
            } else {
                LogManager.log(TAG, "WpsManagement 目录为空", "DEBUG")
            }
        } else {
            LogManager.log(TAG, "WpsManagement 目录不存在或不可访问: ${wpsManagementDir.absolutePath}", "ERROR")
        }

        // 使用简单文件观察者，只监控WpsManagement目录
        fileObserver = SimpleFileObserver(wpsManagementDir.absolutePath)
        fileObserver?.startWatching()
        LogManager.log(TAG, "文件观察者已启动，监听目录: ${wpsManagementDir.absolutePath}", "DEBUG")
    }

    override fun onTerminate() {
        super.onTerminate()
        // 停止文件观察者
        fileObserver?.stopWatching()
        LogManager.log(TAG, "文件观察者已停止", "DEBUG")
    }

    /**
     * 重启文件观察者，用于目录迁移后重新监听
     */
    fun restartFileObserver() {
        LogManager.log(TAG, "重启文件观察者", "DEBUG")
        
        // 停止当前的文件观察者
        fileObserver?.stopWatching()
        LogManager.log(TAG, "已停止旧的文件观察者", "DEBUG")
        
        // 重新初始化文件观察者
        initFileObserver()
        LogManager.log(TAG, "文件观察者已重启", "DEBUG")
    }

    /**
     * 简单文件观察者，只监控指定目录
     */
    private inner class SimpleFileObserver(path: String) : FileObserver(path, ALL_EVENTS) {
        private val rootPath = path

        override fun onEvent(event: Int, path: String?) {
            if (path == null) return

            if (isViewModeFile(path)) {
                LogManager.log(TAG, "忽略\$n_开头的文件事件: $path", "DEBUG")
                return
            }

            val fullPath = File(rootPath, path).absolutePath

            when (event and ALL_EVENTS) {
                CLOSE_WRITE -> {
                    LogManager.log(TAG, "监听到文件写入完成事件: $fullPath", "DEBUG")
                    if (isPluginOperation()) {
                        LogManager.log(TAG, "跳过由插件引起的CLOSE_WRITE事件: $fullPath", "DEBUG")
                    } else {
                        handleFileCloseWrite(fullPath)
                        LogManager.log(TAG, "文件删除: $fullPath", "DEBUG")
                    }
                }

                DELETE -> {
                    if (isPluginOperation()) {
                        LogManager.log(TAG, "跳过由插件引起的DELETE事件: $fullPath", "DEBUG")
                    } else {
                        FileMetaFactory.clearFile(fullPath)
                        LogManager.log(TAG, "文件删除: $fullPath", "DEBUG")
                    }
                }

                MOVED_FROM -> {
                    if (isPluginOperation()) {
                        LogManager.log(TAG, "跳过由插件引起的文件重命名事件(原文件): $fullPath", "DEBUG")
                    } else {
                        FileMetaFactory.clearFile(fullPath)
                        LogManager.log(TAG, "文件重命名(原文件): $fullPath", "DEBUG")
                    }
                }

                MOVED_TO -> {
                    LogManager.log(TAG, "监听到文件移动完成: $fullPath", "DEBUG")
                    if (fullPath.endsWith(".docx") || fullPath.endsWith(".doc") || fullPath.endsWith(
                            ".xlsx"
                        ) || fullPath.endsWith(".xls") || fullPath.endsWith(".pptx") || fullPath.endsWith(
                            ".ppt"
                        )
                    ) {
                        if (!isPluginOperation()) {
                            handleFileCloseWrite(fullPath)
                        } else {
                            LogManager.log(TAG, "跳过由插件引起的MOVED_TO事件: $fullPath", "DEBUG")
                        }
                    }
                }
            }
        }

        private fun isViewModeFile(fileName: String): Boolean {
            return fileName.startsWith("\$n_")
        }
    }

    /**
     * 处理文件写入完成事件
     * 使用异步防抖处理模式，参考 FileSystemEventListener 的实现
     */
    private fun handleFileCloseWrite(filePath: String) {
        LogManager.log(TAG, "[时间戳: ${System.currentTimeMillis()}] 监听到文件写入完成事件: $filePath", "DEBUG")

        // 防抖处理：取消之前的任务，只处理最后一次事件
        debounceRunnable?.let { handler?.removeCallbacks(it) }

        if (handler == null) {
            handler = android.os.Handler(android.os.Looper.getMainLooper())
        }

        val runnable = Runnable {
            try {
                // 检查是否已经处理过此事件
                if (isHandlingEvent) {
                    LogManager.log(
                    TAG,
                    "[时间戳: ${System.currentTimeMillis()}] 事件已在处理中，跳过: $filePath",
                    "DEBUG"
                )
                    return@Runnable
                }
                isHandlingEvent = true

                LogManager.log(
                    TAG,
                    "[时间戳: ${System.currentTimeMillis()}] 开始处理文件写入事件: $filePath",
                    "DEBUG"
                )

                // 检查文件状态
                val file = File(filePath)
                LogManager.log(
                    TAG,
                    "文件状态 - 存在: ${file.exists()}, 可写: ${file.canWrite()}, 大小: ${file.length()} 字节",
                    "DEBUG"
                )

                val fileMeta = FileMetaFactory.getFileMeta(filePath)
                if (fileMeta == null) {
                    LogManager.log(TAG, "未找到文件相关元数据: $filePath", "DEBUG")
                    return@Runnable
                }

                // 检查是否为临时uid，如果是则先注册到服务端
                if (fileMeta.isTempUid && fileMeta.uid != null) {
                    LogManager.log(TAG, "检测到临时uid，需要先注册到服务端: ${fileMeta.uid}", "DEBUG")
                    registerTempUidToServer(filePath, fileMeta.uid!!) { registered ->
                        try {
                            if (registered) {
                                LogManager.log(TAG, "临时uid注册成功，更新isTempUid为false", "DEBUG")
                                val updatedMeta = FileMetaFactory.getFileMeta(filePath)
                                updatedMeta?.isTempUid = false
                            } else {
                                LogManager.log(TAG, "临时uid注册失败（网络错误），isTempUid保持true，下次保存时重试", "WARN")
                            }
                            // 无论注册成功与否，都写入元数据到文件
                            FileMetaManager.getInstance().writeMetaDataToFile(file, fileMeta)
                            postProcessAfterWrite(filePath)
                        } catch (e: Exception) {
                            LogManager.log(TAG, "注册后写入元数据失败: ${e.message}", "ERROR")
                        }
                    }
                    return@Runnable // 等待异步注册完成
                } else {
                    // 非临时uid，直接写入元数据
                    LogManager.log(TAG, "非临时uid，直接写入元数据", "DEBUG")
                    FileMetaManager.getInstance().writeMetaDataToFile(file, fileMeta)
                }

            } catch (e: Exception) {
                LogManager.log(TAG, "处理文件写入事件失败: ${e.message}", "ERROR")
            } finally {
                isHandlingEvent = false
                LogManager.log(TAG, "文件写入事件处理完成: $filePath", "DEBUG")
            }
        }

        debounceRunnable = runnable

        // 延迟执行，确保只处理最后一次事件
        LogManager.log(
            TAG,
            "[时间戳: ${System.currentTimeMillis()}] 延迟 ${debounceDelay}ms 执行密码写入: $filePath",
            "DEBUG"
        )
        handler?.postDelayed(runnable, debounceDelay)
    }

    /**
     * 注册临时uid到服务端（带重试机制）
     * @param filePath 文件路径
     * @param uid 要注册的uid
     * @param callback 注册完成回调，true表示注册成功，false表示失败
     */
    private fun registerTempUidToServer(filePath: String, uid: String, callback: (Boolean) -> Unit) {
        performRegisterWithRetry(filePath, uid, 1, 3, callback)
    }

    /**
     * 执行带重试的注册操作
     * @param filePath 文件路径
     * @param uid 要注册的uid
     * @param retryCount 当前重试次数
     * @param maxRetries 最大重试次数
     * @param callback 注册完成回调
     */
    private fun performRegisterWithRetry(filePath: String, uid: String, retryCount: Int, maxRetries: Int, callback: (Boolean) -> Unit) {
        LogManager.log(TAG, "注册临时uid到服务端，第 $retryCount 次尝试: uid=$uid", "DEBUG")

        val configStorage = ConfigStorage.getInstance(this)
        val userInfo = configStorage.getUserInfo()
        val token = userInfo?.token
        val fileName = File(filePath).name

        NetworkManager.getInstance(this).getDocumentOwner(
            docId = uid,
            token = token,
            fileName = fileName,
            callback = object : com.wpspasswordmanager.network.NetworkCallback {
                override fun onSuccess(response: String) {
                    LogManager.log(TAG, "临时uid注册成功: $response", "DEBUG")
                    callback(true)
                }

                override fun onError(error: String) {
                    LogManager.log(TAG, "临时uid注册失败，第 $retryCount 次: $error", "ERROR")
                    if (retryCount < maxRetries) {
                        val delay = (1000L * Math.pow(2.0, (retryCount - 1).toDouble())).toLong()
                        LogManager.log(TAG, "等待 ${delay}ms 后进行第 ${retryCount + 1} 次重试", "DEBUG")
                        handler?.postDelayed({
                            performRegisterWithRetry(filePath, uid, retryCount + 1, maxRetries, callback)
                        }, delay)
                    } else {
                        LogManager.log(TAG, "临时uid注册失败，已重试 $maxRetries 次，放弃重试", "ERROR")
                        callback(false)
                    }
                }

                override fun onComplete() {}
            }
        )
    }

    /**
     * 文件写入后的后续处理（上报保存记录）
     */
    private fun postProcessAfterWrite(filePath: String) {
        try {
            val fileMeta = FileMetaFactory.getFileMeta(filePath)
            if (fileMeta != null && fileMeta.uid != null) {
                val configStorage = ConfigStorage.getInstance(this)
                val userInfo = configStorage.getUserInfo()
                val token = userInfo?.token

                val docId = fileMeta.uid
                val beforePassword = fileMeta.currentPassword
                val afterPassword = FileMetaFactory.getWritePassword(filePath)
                val possiblePassword = fileMeta.pendingPasswordList?.toList()

                if (beforePassword.isNullOrEmpty() && afterPassword.isNullOrEmpty() && possiblePassword.isNullOrEmpty()) {
                    LogManager.log(TAG, "beforePassword、afterPassword、possiblePassword都为空，无需上报保存记录", "DEBUG")
                    return
                }

                LogManager.log(TAG, "准备上报保存记录: docId=$docId, path=$filePath, beforePassword=$beforePassword, afterPassword=$afterPassword, possiblePassword=$possiblePassword", "DEBUG")

                NetworkManager.getInstance(this).reportSaveLog(
                    docId = docId!!,
                    path = filePath,
                    beforePassword = beforePassword,
                    afterPassword = afterPassword,
                    possiblePassword = possiblePassword,
                    platform = "android",
                    token = token,
                    callback = object : com.wpspasswordmanager.network.NetworkCallback {
                        override fun onSuccess(response: String) {
                            LogManager.log(TAG, "保存记录上报成功: $response", "DEBUG")
                        }

                        override fun onError(error: String) {
                            LogManager.log(TAG, "保存记录上报失败: $error", "ERROR")
                        }

                        override fun onComplete() {
                            // 无论上报成功还是失败，都更新FileMeta中的currentPassword为afterPassword的值
                            val updatedFileMeta = FileMetaFactory.getFileMeta(filePath)
                            if (updatedFileMeta != null && afterPassword != null) {
                                updatedFileMeta.currentPassword = afterPassword
                                LogManager.log(TAG, "上报完成后更新currentPassword: $afterPassword", "DEBUG")

                                // 清空pendingPasswordList
                                updatedFileMeta.pendingPasswordList?.clear()
                                LogManager.log(TAG, "上报完成后清空pendingPasswordList", "DEBUG")
                            }
                        }
                    }
                )
            } else {
                LogManager.log(TAG, "文件元数据不存在或无uid，跳过保存记录上报", "DEBUG")
            }
        } catch (e: Exception) {
            LogManager.log(TAG, "上报保存记录失败: ${e.message}", "ERROR")
        }
    }

    /**
     * 设置插件操作标志
     */
    fun setPluginOperation(operating: Boolean) {
        if (operating) {
            pluginOperationTimestamp.set(System.currentTimeMillis())
            LogManager.log(TAG, "设置插件操作标志，时间戳: ${pluginOperationTimestamp.get()}", "DEBUG")
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
            LogManager.log(TAG, "检测到插件操作，时间戳差: ${currentTime - timestamp}ms", "DEBUG")
        }
        return isPluginOp
    }
}
