package com.wpspasswordmanager.ui

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.provider.DocumentsContract
import android.util.Log
import androidx.appcompat.app.AppCompatActivity
import com.wpspasswordmanager.R
import com.wpspasswordmanager.business.PasswordStorage
import java.io.File
import java.io.FileOutputStream

class ProxyActivity : AppCompatActivity() {

    companion object {
        private const val TAG = "ProxyActivity"
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        // 处理传入的 Intent
        handleIntent(intent)
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
                    val nameIndex = it.getColumnIndex("display_name")
                    if (nameIndex != -1) {
                        fileName = it.getString(nameIndex)
                    }
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "获取文件名失败", e)
        }
        return fileName
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
        
        // 优先尝试直接从原始URI读取密码，避免不必要的文件拷贝
        try {
            Log.d(TAG, "尝试直接从原始URI读取密码")
            val password = PasswordStorage.getInstance().getPassword(this, fileIdentifier)
            if (password != null) {
                Log.d(TAG, "从原始URI读取到密码: $password")
                // 存储密码到PasswordHolder，供无障碍服务使用
                com.wpspasswordmanager.business.PasswordHolder.storePassword(password, fileName)
                // 同时存储到MemoryPasswordStorage作为备份
                com.wpspasswordmanager.business.MemoryPasswordStorage.getInstance().storePasswordInMemory(fileIdentifier, password, fileIdentifier)
                // 保存原始URI到SharedPreferences
                saveFileUriToPreferences(fileIdentifier)
                // 直接使用原始URI转发给WPS
                forwardToWps(null, uri)
                return
            } else {
                Log.d(TAG, "原始URI中未找到密码")
            }
        } catch (e: Exception) {
            Log.e(TAG, "从原始URI读取密码失败，尝试使用本地文件", e)
        }
        
        // 如果直接读取失败，再复制文件到应用私有目录
        val localFile = copyFileToLocalCache(uri, fileName)
        if (localFile != null) {
            val localFilePath = localFile.absolutePath
            Log.d(TAG, "文件成功复制到本地: $localFilePath")
            // 保存本地文件路径到SharedPreferences
            saveFileUriToPreferences(localFilePath)
            
            // 尝试从本地文件读取密码
            try {
                val password = PasswordStorage.getInstance().getPassword(this, localFilePath)
                if (password != null) {
                    Log.d(TAG, "从本地文件读取到密码: $password")
                    // 存储密码到PasswordHolder，供无障碍服务使用
                    com.wpspasswordmanager.business.PasswordHolder.storePassword(password, fileName)
                    // 同时存储到MemoryPasswordStorage作为备份
                    com.wpspasswordmanager.business.MemoryPasswordStorage.getInstance().storePasswordInMemory(localFilePath, password, localFilePath)
                } else {
                    Log.d(TAG, "本地文件中未找到密码")
                }
            } catch (e: Exception) {
                Log.e(TAG, "读取本地文件密码失败", e)
            }
        } else {
            Log.e(TAG, "文件复制到本地失败")
            // 保存原始URI作为备选
            saveFileUriToPreferences(fileIdentifier)
        }
        
        // 无论是否找到密码，都转发给 WPS
        forwardToWps(localFile, uri)
    }
    
    /**
     * 复制文件到应用私有缓存目录
     */
    private fun copyFileToLocalCache(uri: Uri, fileName: String): File? {
        try {
            // 直接使用固定的文件名，避免文件名为空的问题
            val tempDir = cacheDir
            val localFile = File(tempDir, "temp_doc_${System.currentTimeMillis()}.docx")
            
            // 确保目录存在
            if (!tempDir.exists()) {
                tempDir.mkdirs()
            }
            
            // 从ContentResolver读取文件内容并写入本地文件
            val inputStream = contentResolver.openInputStream(uri)
            inputStream?.use { input ->
                FileOutputStream(localFile).use { output ->
                    val buffer = ByteArray(4096)
                    var bytesRead: Int
                    while (input.read(buffer).also { bytesRead = it } != -1) {
                        output.write(buffer, 0, bytesRead)
                    }
                }
            }
            
            if (localFile.exists() && localFile.length() > 0) {
                return localFile
            } else {
                return null
            }
        } catch (e: Exception) {
            Log.e(TAG, "文件复制失败", e)
            return null
        }
    }

    /**
     * 保存文件URI到SharedPreferences
     */
    private fun saveFileUriToPreferences(uri: String) {
        try {
            val prefs = getSharedPreferences("WpsPasswordManagerPrefs", Context.MODE_PRIVATE)
            prefs.edit().putString("current_file_uri", uri).apply()
            Log.d(TAG, "保存文件URI到SharedPreferences: $uri")
        } catch (e: Exception) {
            Log.e(TAG, "保存文件URI失败", e)
        }
    }

    private fun forwardToWps(localFile: File?, originalUri: Uri) {
        try {
            if (localFile != null) {
                // 使用FileProvider获取可共享的URI
                val shareUri = getShareableUriFromFile(this, localFile)
                val wpsIntent = Intent(Intent.ACTION_VIEW)
                wpsIntent.setDataAndType(shareUri, "application/vnd.openxmlformats-officedocument.wordprocessingml.document")
                wpsIntent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                wpsIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                
                // 尝试启动WPS
                if (wpsIntent.resolveActivity(packageManager) != null) {
                    startActivity(wpsIntent)
                    Log.d(TAG, "通过FileProvider启动WPS成功，文件: ${localFile.absolutePath}")
                } else {
                    // 如果直接启动失败，尝试通过文件选择器
                    Log.d(TAG, "直接启动WPS失败，尝试使用文件选择器")
                    val chooserIntent = Intent.createChooser(wpsIntent, "选择应用打开文件")
                    if (chooserIntent.resolveActivity(packageManager) != null) {
                        chooserIntent.flags = Intent.FLAG_GRANT_READ_URI_PERMISSION
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
                val wpsIntent = Intent(Intent.ACTION_VIEW)
                wpsIntent.data = originalUri
                wpsIntent.flags = Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK
                
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
     * 从Content URI创建临时文件
     */
    private fun createTempFileFromContentUri(uri: Uri): File? {
        try {
            // 直接使用固定的文件名，避免文件名为空的问题
            val tempDir = cacheDir
            val tempFile = File(tempDir, "temp_doc_${System.currentTimeMillis()}.docx")
            
            // 确保目录存在
            if (!tempDir.exists()) {
                tempDir.mkdirs()
            }
            
            // 从ContentResolver读取文件内容并写入临时文件
            val inputStream = contentResolver.openInputStream(uri)
            inputStream?.use { input ->
                FileOutputStream(tempFile).use { output ->
                    val buffer = ByteArray(4096)
                    var bytesRead: Int
                    while (input.read(buffer).also { bytesRead = it } != -1) {
                        output.write(buffer, 0, bytesRead)
                    }
                }
            }
            
            if (tempFile.exists() && tempFile.length() > 0) {
                Log.d(TAG, "创建临时文件成功: ${tempFile.absolutePath}")
                return tempFile
            } else {
                Log.e(TAG, "创建临时文件失败，文件不存在或为空")
                return null
            }
        } catch (e: Exception) {
            Log.e(TAG, "创建临时文件失败", e)
            return null
        }
    }

    /**
     * 显示错误通知
     */
    private fun showErrorNotification(title: String, message: String) {
        try {
            val notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as android.app.NotificationManager
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
                val channel = android.app.NotificationChannel("error_channel", "错误通知", android.app.NotificationManager.IMPORTANCE_HIGH)
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