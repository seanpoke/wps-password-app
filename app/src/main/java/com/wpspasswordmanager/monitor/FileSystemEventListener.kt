package com.wpspasswordmanager.monitor

import android.os.FileObserver
import android.util.Log
import com.wpspasswordmanager.business.PasswordStorage
import java.io.File

/**
 * 文件系统事件监听器
 * 按照核心流程文档要求：使用FileObserver监控目标文件的状态变化，当文件状态变为可写时触发密码写入
 * 实现原理：
 * - 监听WPS应用的文件操作事件
 * - 当WPS进入后台或进程结束时触发密码写入
 * - 结合ActivityManager监控应用状态
 * 优势：
 * - 与应用行为直接关联，时机更准确
 * - 资源消耗低，只在应用状态变化时触发
 */
class FileSystemEventListener(private val filePath: String, private val password: String, private val context: android.content.Context) {

    companion object {
        private const val TAG = "FileSystemEventListener"
        private const val DEBOUNCE_DELAY = 1000L // 防抖延迟时间，单位毫秒
        private const val LOG_TAIL_SIZE = 1024 // 日志打印的文件尾部大小，单位字节
    }

    private var fileObserver: FileObserver? = null
    private var isContentUri = false
    private var handler: android.os.Handler? = null
    private var debounceRunnable: Runnable? = null

    /**
     * 开始监听
     */
    fun startListening() {
        try {
            if (filePath.startsWith("content://")) {
                // 对于Content URI，使用应用状态监控
                isContentUri = true
                Log.d(TAG, "开始监控Content URI: $filePath")
                // 注册应用状态监听器
                registerAppStateListener()
            } else {
                // 对于普通文件路径，使用FileObserver
                val file = File(filePath)
                if (file.exists()) {
                    Log.d(TAG, "开始监听文件: $filePath")
                    fileObserver = object : FileObserver(filePath, CREATE or MODIFY or CLOSE_WRITE) {
                        override fun onEvent(event: Int, path: String?) {
                            handleFileEvent(event, path)
                        }
                    }
                    fileObserver?.startWatching()
                } else {
                    Log.e(TAG, "文件不存在，无法监听: $filePath")
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "开始监听失败", e)
        }
    }

    /**
     * 处理文件系统事件
     */
    private fun handleFileEvent(event: Int, path: String?) {
        try {
            Log.d(TAG, "文件系统事件: $event, 路径: $path")
            
            // 当文件关闭写入时触发密码写入
            if (event and FileObserver.CLOSE_WRITE != 0) {
                Log.d(TAG, "文件关闭写入，触发密码写入")
                
                // 防抖处理：取消之前的任务，只处理最后一次事件
                debounceRunnable?.let { handler?.removeCallbacks(it) }
                
                if (handler == null) {
                    handler = android.os.Handler(android.os.Looper.getMainLooper())
                }
                
                val runnable = Runnable {
                    try {
                        // 检查是否已经处理过此事件
                        if (isHandlingEvent) {
                            Log.d(TAG, "事件已在处理中，跳过")
                            return@Runnable
                        }
                        
                        isHandlingEvent = true
                        
                        // 写入密码
                        val success = PasswordStorage.getInstance().writePassword(context, filePath, password)
                        if (success) {
                            Log.d(TAG, "密码写入成功")
                            // 打印文件zip尾部最后1KB的内容
                            logFileTail()
                        } else {
                            Log.e(TAG, "密码写入失败")
                        }
                        
                        // 密码写入完成后，停止监听
                        stopListening()
                    } catch (e: Exception) {
                        Log.e(TAG, "处理文件系统事件失败", e)
                    } finally {
                        isHandlingEvent = false
                    }
                }
                
                debounceRunnable = runnable
                
                // 延迟执行，确保只处理最后一次事件
                handler?.postDelayed(runnable, DEBOUNCE_DELAY)
            }
        } catch (e: Exception) {
            Log.e(TAG, "处理文件系统事件失败", e)
        }
    }
    
    /**
     * 是否正在处理事件
     */
    private var isHandlingEvent = false
    
    /**
     * 打印文件zip尾部最后1KB的内容，只输出WPPM标记相关的内容
     */
    private fun logFileTail() {
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

    /**
     * 注册应用状态监听器
     */
    private fun registerAppStateListener() {
        try {
            Log.d(TAG, "注册应用状态监听器")
            // 实现应用状态监控逻辑
            val activityManager = context.getSystemService(android.content.Context.ACTIVITY_SERVICE) as android.app.ActivityManager
            
            // 立即尝试写入密码（对于Content URI）
            Log.d(TAG, "尝试直接写入密码到Content URI")
            val success = PasswordStorage.getInstance().writePassword(context, filePath, password)
            if (success) {
                Log.d(TAG, "密码写入成功")
            } else {
                Log.e(TAG, "密码写入失败，将在适当时机重试")
                // 设置延迟任务，稍后再次尝试
                android.os.Handler().postDelayed({ 
                    Log.d(TAG, "延迟重试写入密码")
                    val retrySuccess = PasswordStorage.getInstance().writePassword(context, filePath, password)
                    if (retrySuccess) {
                        Log.d(TAG, "重试写入密码成功")
                    } else {
                        Log.e(TAG, "重试写入密码失败")
                    }
                }, 3000) // 3秒后重试
                
                // 再次延迟重试
                android.os.Handler().postDelayed({ 
                    Log.d(TAG, "再次延迟重试写入密码")
                    val retrySuccess = PasswordStorage.getInstance().writePassword(context, filePath, password)
                    if (retrySuccess) {
                        Log.d(TAG, "再次重试写入密码成功")
                    } else {
                        Log.e(TAG, "再次重试写入密码失败")
                    }
                }, 6000) // 6秒后再次重试
            }
        } catch (e: Exception) {
            Log.e(TAG, "注册应用状态监听器失败", e)
        }
    }

    /**
     * 停止监听
     */
    fun stopListening() {
        try {
            Log.d(TAG, "停止监听: $filePath")
            if (!isContentUri) {
                fileObserver?.stopWatching()
                fileObserver = null
            }
            // 对于Content URI，不需要特殊处理
        } catch (e: Exception) {
            Log.e(TAG, "停止监听失败", e)
        }
    }
}