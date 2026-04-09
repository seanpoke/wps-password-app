package com.wpspasswordmanager.ui

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.os.FileObserver
import android.provider.DocumentsContract
import android.util.Log
import androidx.appcompat.app.AppCompatActivity
import com.wpspasswordmanager.R
import com.wpspasswordmanager.business.OfficeEncryptUtils
import com.wpspasswordmanager.business.PasswordObjManager
import com.wpspasswordmanager.business.PasswordStorage
import com.wpspasswordmanager.business.ZipExtraFieldManager
import com.wpspasswordmanager.monitor.WpsAccessibilityService
import java.io.File
import java.io.FileOutputStream

class ProxyActivity : AppCompatActivity() {

    companion object {
        private const val TAG = "ProxyActivity"
        private const val WPS_MANAGEMENT_DIR = "WpsManagement"
    }

    // FileObserver 实例
    private var fileObserver: FileObserver? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // 初始化文件观察者
        initFileObserver()

        // 处理传入的 Intent
        handleIntent(intent)
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
                    Log.d(TAG, "文件删除，清理缓存: $fullPath")
                }

                MOVED_FROM -> {
                    Log.d(TAG, "文件重命名(原文件)，清理缓存: $fullPath")
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

    // 用于跟踪正在处理的文件，避免循环处理
    private val processingFiles = mutableSetOf<String>()

    /**
     * 处理文件写入完成事件
     */
    private fun handleFileCloseWrite(filePath: String) {
        // 避免循环处理同一个文件
        if (processingFiles.contains(filePath)) {
            Log.d(TAG, "文件正在处理中，跳过: $filePath")
            return
        }

        Log.d(TAG, "监听到文件写入完成事件: $filePath")

        // 标记文件正在处理
        processingFiles.add(filePath)
        Log.d(TAG, "标记文件为正在处理: $filePath, 处理中文件数量: ${processingFiles.size}")

        try {
            // 检查文件状态
            val file = File(filePath)
            Log.d(
                TAG,
                "文件状态 - 存在: ${file.exists()}, 可写: ${file.canWrite()}, 大小: ${file.length()} 字节"
            )

            var password = PasswordObjManager.getWritePassword(filePath)
            Log.d(TAG, "PA执行getWritePassword结果: $password")

            if (password != null) {
                Log.d(TAG, "准备将密码写入文件: $password")
                // 检查是否真的需要写入密码（避免无限循环）
                val currentPassword = PasswordStorage.getInstance().getPassword(this, filePath)
                if (currentPassword == null || currentPassword != password) {
                    // 使用ZipExtraFieldManager将密码回写到文件尾部
                    try {
                        if (file.exists() && file.canWrite()) {
                            Log.d(TAG, "开始写入密码到文件")
                            // 使用ZipExtraFieldManager将密码写入文件
                            val success = ZipExtraFieldManager.getInstance()
                                .writePassword(this, filePath, password)
                            if (success) {
                                Log.d(TAG, "成功将密码写入文件: $filePath")
                            } else {
                                Log.e(TAG, "密码写入失败: $filePath")
                            }
                        } else {
                            Log.e(TAG, "文件不存在或不可写: $filePath")
                        }
                    } catch (e: Exception) {
                        Log.e(TAG, "处理文件写入完成事件失败", e)
                    }
                } else {
                    Log.d(TAG, "密码未变化，跳过写入操作: $filePath")
                }
            } else {
                Log.d(TAG, "所有缓存中均未找到文件密码: $filePath")
            }
        } finally {
            // 移除处理标记
            processingFiles.remove(filePath)
            Log.d(TAG, "移除文件处理标记: $filePath, 处理中文件数量: ${processingFiles.size}")
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        // 停止文件观察者
        fileObserver?.stopWatching()
    }

    private fun handleIntent(intent: Intent?) {
        if (intent == null || intent.action != Intent.ACTION_VIEW) {
            Log.e(TAG, "无效的 Intent")
            finish()
            return
        }

        val uri = intent.data
        if (uri == null) {
            Log.e(TAG, "Intent 中没有 URI")
            finish()
            return
        }

        try {
            // 获取文件名
            val fileName = getFileName(uri)
            Log.d(TAG, "文件名: $fileName")
            Log.d(TAG, "文件 URI: $uri")

            // 策略分流
            if (isEncryptedFile(fileName)) {
                // 情况 B：文件已加密
                handleEncryptedFile(uri, fileName)
            } else {
                // 情况 A：文件未加密，直接转发给 WPS
                forwardToWps(null, uri)
            }
        } catch (e: Exception) {
            Log.e(TAG, "处理 Intent 失败", e)
            // 即使失败也转发给 WPS
            forwardToWps(null, uri)
        }
    }

    private fun getFileName(uri: Uri): String {
        var fileName = ""
        try {
            val cursor = contentResolver.query(uri, null, null, null, null)
            cursor?.use {
                if (it.moveToFirst()) {
                    // 使用标准的OpenableColumns.DISPLAY_NAME列名
                    val nameIndex = it.getColumnIndex(android.provider.OpenableColumns.DISPLAY_NAME)
                    if (nameIndex != -1) {
                        fileName = it.getString(nameIndex)
                    }
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "获取文件名失败", e)
        }

        // 如果ContentResolver无法获取文件名，尝试从URI路径中解析
        if (fileName.isEmpty()) {
            fileName = getFileNameFromUri(uri)
        }

        return fileName
    }

    /**
     * 从URI路径中解析文件名
     */
    private fun getFileNameFromUri(uri: Uri): String {
        val path = uri.path
        if (!path.isNullOrEmpty()) {
            val lastSlash = path.lastIndexOf('/')
            if (lastSlash != -1 && lastSlash < path.length - 1) {
                return path.substring(lastSlash + 1)
            }
        }
        return ""
    }

    private fun isEncryptedFile(fileName: String): Boolean {
        // 这里可以根据文件类型或其他特征判断是否加密
        // 暂时简单返回 true，实际应用中需要根据具体情况判断
        return true
    }

    private fun handleEncryptedFile(uri: Uri, fileName: String) {
        // 使用完整的URI字符串作为密码存储的键，确保唯一性
        val fileIdentifier = uri.toString()
        Log.d(TAG, "文件标识: $fileIdentifier")
        Log.d(TAG, "传入的文件名: '$fileName'")
        Log.d(TAG, "文件名长度: ${fileName.length}")

        // 实现完整的文件处理流程
        val localFile = processExternalContentUri(uri, fileName)
        if (localFile != null) {
            val localFilePath = localFile.absolutePath
            Log.d(TAG, "文件处理完成，本地路径: $localFilePath")
            // 保存本地文件路径到SharedPreferences
            saveFileUriToPreferences(localFilePath)
        } else {
            Log.e(TAG, "文件处理失败")
            // 保存原始URI作为备选
            saveFileUriToPreferences(fileIdentifier)
        }

        // 无论是否找到密码，都转发给 WPS
        forwardToWps(localFile, uri)
    }

    /**
     * 处理外部传入的ContentURI，实现完整的文件处理流程
     */
    private fun processExternalContentUri(uri: Uri, originalFileName: String): File? {
        try {
            // 1. 获取WpsManagement目录
            val documentsDir =
                android.os.Environment.getExternalStoragePublicDirectory(android.os.Environment.DIRECTORY_DOCUMENTS)
            val wpsManagementDir = File(documentsDir, "WpsManagement")

            // 确保WpsManagement目录存在
            if (!wpsManagementDir.exists()) {
                wpsManagementDir.mkdirs()
                Log.d(TAG, "创建 WpsManagement 目录: ${wpsManagementDir.absolutePath}")
            }

            // 2. 文件存在性检查与拷贝
            val targetFile = File(wpsManagementDir, originalFileName)
            if (targetFile.exists() && targetFile.length() > 0) {
                Log.d(TAG, "文件已存在，直接使用本地副本: ${targetFile.absolutePath}")
                // 立即读取密码并存储
                readAndStorePassword(targetFile.absolutePath, originalFileName)
                return targetFile
            }

            // 3. 从原始ContentURI拷贝文件到目标路径
            if (copyFileFromContentUri(uri, targetFile)) {
                Log.d(TAG, "文件拷贝成功: ${targetFile.absolutePath}")
                // 立即读取密码并存储到缓存，供后续FileObserver使用
                readAndStorePassword(targetFile.absolutePath, originalFileName)
                // 不在这里写入密码，完全依赖FileObserver监听文件更新
                return targetFile
            } else {
                Log.e(TAG, "文件拷贝失败")
                return null
            }
        } catch (e: Exception) {
            Log.e(TAG, "处理ContentURI失败", e)
            return null
        }
    }

    /**
     * 读取密码并存储到缓存
     */
    private fun readAndStorePassword(filePath: String, fileName: String) {
        Log.d(TAG, "开始读取密码并存储到缓存，文件路径: $filePath")
        try {
            val file = File(filePath)
            Log.d(TAG, "文件存在: ${file.exists()}")
            Log.d(TAG, "文件可读: ${file.canRead()}")
            Log.d(TAG, "文件大小: ${file.length()} 字节")

            val password = PasswordStorage.getInstance().getPassword(this, filePath)
            if (password != null) {
                Log.d(TAG, "从本地文件读取到密码: $password")
                // 存储密码到PasswordHolder，供无障碍服务使用
                com.wpspasswordmanager.business.PasswordHolder.storePassword(password, fileName)
                Log.d(TAG, "密码已存储到PasswordHolder")
            } else {
                Log.d(TAG, "本地文件中未找到密码")
            }
        } catch (e: Exception) {
            Log.e(TAG, "读取本地文件密码失败", e)
        }
    }


    /**
     * 从ContentURI拷贝文件到目标路径，实现校验机制确保文件完整性
     */
    private fun copyFileFromContentUri(uri: Uri, targetFile: File): Boolean {
        try {
            val inputStream = contentResolver.openInputStream(uri)
            inputStream?.use { input ->
                FileOutputStream(targetFile).use { output ->
                    val buffer = ByteArray(4096)
                    var bytesRead: Int
                    var totalBytes = 0L

                    while (input.read(buffer).also { bytesRead = it } != -1) {
                        output.write(buffer, 0, bytesRead)
                        totalBytes += bytesRead
                    }

                    // 验证文件大小
                    if (targetFile.length() == totalBytes) {
                        Log.d(TAG, "文件拷贝完成，大小: $totalBytes 字节")
                        return true
                    } else {
                        Log.e(
                            TAG,
                            "文件拷贝不完整，期望大小: $totalBytes, 实际大小: ${targetFile.length()}"
                        )
                        // 删除不完整的文件
                        targetFile.delete()
                        return false
                    }
                }
            }
            return false
        } catch (e: Exception) {
            Log.e(TAG, "拷贝文件失败", e)
            // 清理失败的文件
            targetFile.delete()
            return false
        }
    }


    /**
     * 保存文件URI到WpsAccessibilityService
     */
    private fun saveFileUriToPreferences(uri: String) {
        try {
            // 直接设置到WpsAccessibilityService的静态变量
            WpsAccessibilityService.currentFileUri = uri
            WpsAccessibilityService.stableDocumentPath = uri
            WpsAccessibilityService.currentDocumentPath = uri
            Log.d(TAG, "保存文件URI到WpsAccessibilityService: $uri")
        } catch (e: Exception) {
            Log.e(TAG, "保存文件URI失败", e)
        }
    }

    private fun forwardToWps(localFile: File?, originalUri: Uri) {
        try {
            if (localFile != null) {
                // 使用FileProvider获取可共享的URI
                val shareUri = getShareableUriFromFile(this, localFile)
                Log.d(TAG, "插件转换后唤起WPS的ContentURI: $shareUri")
                val wpsIntent = Intent(Intent.ACTION_VIEW)
                wpsIntent.setDataAndType(
                    shareUri,
                    "application/vnd.openxmlformats-officedocument.wordprocessingml.document"
                )

                // 添加读写权限
                wpsIntent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                wpsIntent.addFlags(Intent.FLAG_GRANT_WRITE_URI_PERMISSION)
                wpsIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)

                // 添加WPS特定参数，尝试阻止创建副本
                wpsIntent.putExtra("OpenMode", "Normal") // 正常打开模式
                wpsIntent.putExtra("NeedCreateTemp", false) // 不需要创建临时文件
                wpsIntent.putExtra("ReadOnly", false) // 可读写模式

                // 尝试启动WPS
                if (wpsIntent.resolveActivity(packageManager) != null) {
                    // 显式授予权限给WPS包
                    val wpsPackages = arrayOf("cn.wps.moffice_eng", "cn.wps.moffice")
                    for (pkg in wpsPackages) {
                        try {
                            grantUriPermission(
                                pkg,
                                shareUri,
                                Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION
                            )
                        } catch (e: Exception) {
                            Log.e(TAG, "授予权限给 $pkg 失败", e)
                        }
                    }

                    startActivity(wpsIntent)
                    Log.d(TAG, "通过FileProvider启动WPS成功，文件: ${localFile.absolutePath}")
                    Log.d(TAG, "已授予WPS应用读写权限")
                    Log.d(TAG, "已添加WPS特定参数，尝试阻止创建副本")
                } else {
                    // 如果直接启动失败，尝试通过文件选择器
                    Log.d(TAG, "直接启动WPS失败，尝试使用文件选择器")
                    val chooserIntent = Intent.createChooser(wpsIntent, "选择应用打开文件")
                    if (chooserIntent.resolveActivity(packageManager) != null) {
                        chooserIntent.flags =
                            Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION
                        startActivity(chooserIntent)
                        Log.d(TAG, "使用文件选择器启动成功")
                    } else {
                        Log.e(TAG, "没有应用可以打开此文件")
                        showErrorNotification("错误", "没有应用可以打开此文件")
                    }
                }
            } else {
                // 如果本地文件不存在，尝试直接使用原始URI
                Log.d(TAG, "本地文件不存在，尝试使用原始URI")
                Log.d(TAG, "插件转换后唤起WPS的ContentURI: $originalUri")
                val wpsIntent = Intent(Intent.ACTION_VIEW)
                wpsIntent.data = originalUri
                wpsIntent.flags =
                    Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK

                if (wpsIntent.resolveActivity(packageManager) != null) {
                    startActivity(wpsIntent)
                    Log.d(TAG, "使用原始URI启动WPS成功")
                } else {
                    Log.e(TAG, "没有应用可以打开此文件")
                    showErrorNotification("错误", "没有应用可以打开此文件")
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "启动 WPS 失败", e)
            showErrorNotification("启动失败", "无法启动WPS应用")
        } finally {
            // 完成后销毁自身
            finish()
        }
    }

    /**
     * 通过FileProvider获取可共享的URI
     */
    private fun getShareableUriFromFile(context: Context, file: File): Uri {
        return androidx.core.content.FileProvider.getUriForFile(
            context,
            "com.wpspasswordmanager.fileprovider",
            file
        )
    }

    /**
     * 显示错误通知
     */
    private fun showErrorNotification(title: String, message: String) {
        try {
            val notificationManager =
                getSystemService(Context.NOTIFICATION_SERVICE) as android.app.NotificationManager
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
                val channel = android.app.NotificationChannel(
                    "error_channel",
                    "错误通知",
                    android.app.NotificationManager.IMPORTANCE_HIGH
                )
                notificationManager.createNotificationChannel(channel)
            }
            val notification = androidx.core.app.NotificationCompat.Builder(this, "error_channel")
                .setSmallIcon(R.mipmap.ic_launcher)
                .setContentTitle(title)
                .setContentText(message)
                .setPriority(androidx.core.app.NotificationCompat.PRIORITY_HIGH)
                .build()
            notificationManager.notify(1, notification)
        } catch (e: Exception) {
            Log.e(TAG, "显示通知失败", e)
        }
    }
}