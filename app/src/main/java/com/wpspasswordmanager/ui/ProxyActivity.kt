package com.wpspasswordmanager.ui

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Bundle

import android.util.Log
import androidx.appcompat.app.AppCompatActivity
import com.wpspasswordmanager.R
import com.wpspasswordmanager.WpsPasswordManagerApplication
import com.wpspasswordmanager.business.FileMetaHolder
import com.wpspasswordmanager.business.FileMetaManager
import com.wpspasswordmanager.monitor.WpsAccessibilityService
import com.wpspasswordmanager.network.NetworkManager
import com.wpspasswordmanager.storage.ConfigStorage
import org.json.JSONObject
import java.io.File
import java.io.FileOutputStream

class ProxyActivity : AppCompatActivity() {

    companion object {
        private const val TAG = "ProxyActivity"
        private const val WPS_MANAGEMENT_DIR = "WpsManagement"
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // 处理传入的 Intent
        handleIntent(intent)
    }

    override fun onDestroy() {
        super.onDestroy()
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
            handleFileUri(uri, fileName)
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

    private fun handleFileUri(uri: Uri, fileName: String) {
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
                // 同时读取类型为2的uid并存储到缓存
                readAndStoreUid(targetFile.absolutePath, originalFileName)
                // 初始化FileMeta对象并获取权限信息
                initFileMetaWithPermissions(targetFile.absolutePath)
                return targetFile
            }

            // 3. 从原始ContentURI拷贝文件到目标路径
            if (copyFileFromContentUri(uri, targetFile)) {
                Log.d(TAG, "文件拷贝成功: ${targetFile.absolutePath}")
                // 立即读取密码并存储到缓存，供后续FileObserver使用
                readAndStorePassword(targetFile.absolutePath, originalFileName)
                // 同时读取类型为2的uid并存储到缓存
                readAndStoreUid(targetFile.absolutePath, originalFileName)
                // 初始化FileMeta对象并获取权限信息
                initFileMetaWithPermissions(targetFile.absolutePath)
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

            val password = FileMetaManager.getInstance().getPasswordFromFile(this, filePath)
            if (password != null) {
                Log.d(TAG, "从本地文件读取到密码: $password")
                // 存储密码到PasswordHolder，供无障碍服务使用
                FileMetaHolder.storePassword(password, fileName)
                Log.d(TAG, "密码已存储到PasswordHolder")
            } else {
                Log.d(TAG, "本地文件中未找到密码")
            }
        } catch (e: Exception) {
            Log.e(TAG, "读取本地文件密码失败", e)
        }
    }

    private fun readAndStoreUid(filePath: String, fileName: String) {
        Log.d(TAG, "开始读取uid并存储到缓存，文件路径: $filePath")
        try {
            val file = File(filePath)
            Log.d(TAG, "文件存在: ${file.exists()}")
            Log.d(TAG, "文件可读: ${file.canRead()}")
            Log.d(TAG, "文件大小: ${file.length()} 字节")

            val uid = FileMetaManager.getInstance().getUidFromFile(this, filePath)
            if (uid != null) {
                Log.d(TAG, "从本地文件读取到uid: $uid")
                // 存储uid到PasswordHolder，供无障碍服务使用
                FileMetaHolder.storeUid(uid, fileName)
                Log.d(TAG, "uid已存储到PasswordHolder")
            } else {
                Log.d(TAG, "本地文件中未找到uid")
            }
        } catch (e: Exception) {
            Log.e(TAG, "读取本地文件uid失败", e)
        }
    }

    /**
     * 初始化FileMeta对象并获取权限信息
     */
    private fun initFileMetaWithPermissions(filePath: String) {
        Log.d(TAG, "开始初始化FileMeta对象并获取权限信息，文件路径: $filePath")
        val password = FileMetaHolder.cachedPassword
        val uid = FileMetaHolder.cachedUid
        val finalUid = uid ?: com.wpspasswordmanager.business.FileMetaFactory.createUid()
        try {

            // 从ConfigStorage获取token
            val token = getTokenFromStorage()
            Log.d(TAG, "获取到token: ${if (token.isNullOrEmpty()) "空" else "已获取"}")
            
            // 尝试获取权限信息
            Log.d(TAG, "开始获取文档权限信息")
            
            // 使用NetworkManager获取文档权限信息
            NetworkManager.getInstance(this).getDocumentOwner(
                docId = finalUid,
                token = token,
                callback = object : com.wpspasswordmanager.network.NetworkCallback {
                    override fun onSuccess(response: String) {
                        Log.d(TAG, "获取文档权限响应: $response")
                        try {
                            val json = JSONObject(response)
                            if (json.getInt("status") == 200) {
                                val data = json.getJSONObject("data")
                                val ownerAccount = data.optString("ownerAccount")
                                val ownerName = data.optString("ownerName")
                                val readAuth = data.optBoolean("readAuth", false)
                                val writeAuth = data.optBoolean("writeAuth", false)
                                
                                // 存储到FileMetaFactory
                                com.wpspasswordmanager.business.FileMetaFactory.initFileMetaWithPermissions(
                                    filePath = filePath,
                                    oldPass = password,
                                    uid = finalUid,
                                    ownerAccount = ownerAccount,
                                    ownerName = ownerName,
                                    readAuth = readAuth,
                                    writeAuth = writeAuth
                                )
                                
                                Log.d(TAG, "FileMeta对象初始化成功，权限信息: readAuth=$readAuth, writeAuth=$writeAuth, ownerAccount=$ownerAccount, ownerName=$ownerName")
                            } else {
                                // 响应状态码不是200，使用默认权限
                                initFileMetaWithDefaultPermissions(filePath, password, finalUid)
                            }
                        } catch (e: Exception) {
                            Log.e(TAG, "解析权限响应失败", e)
                            // 解析失败时使用默认权限
                            initFileMetaWithDefaultPermissions(filePath, password, finalUid)
                        }
                    }
                    
                    override fun onError(error: String) {
                        Log.e(TAG, "获取文档权限失败: $error")
                        // 网络请求失败时使用默认权限
                        initFileMetaWithDefaultPermissions(filePath, password, finalUid)
                    }
                }
            )
        } catch (e: Exception) {
            Log.e(TAG, "初始化FileMeta对象失败", e)
            initFileMetaWithDefaultPermissions(filePath, password, finalUid)
        }
    }
    
    /**
     * 从ConfigStorage获取token
     */
    private fun getTokenFromStorage(): String? {
        try {
            val userInfo = ConfigStorage.getInstance(this).getUserInfo()
            return userInfo?.token
        } catch (e: Exception) {
            Log.e(TAG, "获取token失败", e)
            return null
        }
    }
    
    /**
     * 使用默认权限初始化FileMeta对象
     */
    private fun initFileMetaWithDefaultPermissions(filePath: String, password: String?, uid: String) {
        Log.d(TAG, "使用默认权限初始化FileMeta对象")
        com.wpspasswordmanager.business.FileMetaFactory.initFileMetaWithPermissions(
            filePath = filePath,
            oldPass = password,
            uid = uid,
            ownerAccount = null,
            ownerName = null,
            readAuth = false,
            writeAuth = false
        )
        Log.d(TAG, "FileMeta对象初始化成功，使用默认权限设置")
    }


    /**
     * 从ContentURI拷贝文件到目标路径，实现校验机制确保文件完整性
     */
    private fun copyFileFromContentUri(uri: Uri, targetFile: File): Boolean {
        try {
            // 设置插件操作标志，避免触发文件事件监听器
            WpsPasswordManagerApplication.instance.setPluginOperation(true)
            
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