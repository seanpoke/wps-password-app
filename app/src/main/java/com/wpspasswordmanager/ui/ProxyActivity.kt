package com.wpspasswordmanager.ui

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Bundle
import android.view.accessibility.AccessibilityManager

import com.wpspasswordmanager.utils.LogManager
import androidx.appcompat.app.AppCompatActivity
import com.wpspasswordmanager.R
import com.wpspasswordmanager.WpsPasswordManagerApplication
import com.wpspasswordmanager.business.FileMetaFactory
import com.wpspasswordmanager.business.FileMetaManager
import com.wpspasswordmanager.monitor.WpsAccessibilityService
import com.wpspasswordmanager.network.NetworkCallback
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

        // 检查token存在性与有效性
        if (!checkTokenValidity()) {
            // token不存在或已失效，跳转到登录页面
            val loginIntent = Intent(this, MainActivity::class.java)
            loginIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK)
            startActivity(loginIntent)
            finish()
            return
        }

        // 检查应用权限状态
        if (!checkAppPermissions()) {
            // 权限不足，跳转到主页面
            val mainIntent = Intent(this, MainActivity::class.java)
            mainIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK)
            startActivity(mainIntent)
            finish()
            return
        }

        // 处理传入的 Intent
        handleIntent(intent)
    }

    /**
     * 检查应用权限状态
     * @return true if all required permissions are granted, false otherwise
     */
    private fun checkAppPermissions(): Boolean {
        // 检查无障碍服务权限
        val isAccessibilityServiceEnabled = isAccessibilityServiceEnabled()
        LogManager.log(TAG, "无障碍服务状态: $isAccessibilityServiceEnabled", "DEBUG")

        // 检查悬浮窗权限
        val isOverlayPermissionGranted = isOverlayPermissionGranted()
        LogManager.log(TAG, "悬浮窗权限状态: $isOverlayPermissionGranted", "DEBUG")

        // 若任意一项权限未启用，则返回false
        return isAccessibilityServiceEnabled && isOverlayPermissionGranted
    }

    /**
     * 检查无障碍服务是否启用
     * @return true if accessibility service is enabled, false otherwise
     */
    private fun isAccessibilityServiceEnabled(): Boolean {
        try {
            val accessibilityManager = getSystemService(Context.ACCESSIBILITY_SERVICE) as AccessibilityManager
            val enabledServices = accessibilityManager.getEnabledAccessibilityServiceList(android.accessibilityservice.AccessibilityServiceInfo.FEEDBACK_GENERIC)
            
            for (service in enabledServices) {
                if (service.id.contains("WpsAccessibilityService")) {
                    return true
                }
            }
            return false
        } catch (e: Exception) {
            LogManager.log(TAG, "检查无障碍服务状态失败: ${e.message}", "ERROR")
            return false
        }
    }

    /**
     * 检查悬浮窗权限是否授予
     * @return true if overlay permission is granted, false otherwise
     */
    private fun isOverlayPermissionGranted(): Boolean {
        try {
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.M) {
                // Android 6.0及以上需要动态申请权限
                return android.provider.Settings.canDrawOverlays(this)
            } else {
                // Android 6.0以下默认授予
                return true
            }
        } catch (e: Exception) {
            LogManager.log(TAG, "检查悬浮窗权限失败: ${e.message}", "ERROR")
            return false
        }
    }

    /**
     * 检查token存在性与有效性
     * @return true if token is valid, false otherwise
     */
    private fun checkTokenValidity(): Boolean {
        try {
            // 检查本地存储中是否存在token
            val token = getTokenFromStorage()
            if (token.isNullOrEmpty()) {
                LogManager.log(TAG, "Token不存在", "DEBUG")
                return false
            }

            // 尝试刷新token以验证其有效性
            val latch = java.util.concurrent.CountDownLatch(1)
            var isTokenValid = false

            NetworkManager.getInstance(this).refreshToken(token, object : NetworkCallback {
                override fun onSuccess(response: String) {
                    try {
                        val json = JSONObject(response)
                        if (json.getInt("status") == 200) {
                            // token刷新成功，更新本地存储中的token
                            val data = json.getJSONObject("data")
                            val newToken = data.optString("token")
                            if (!newToken.isNullOrEmpty()) {
                                val userInfo = ConfigStorage.getInstance(this@ProxyActivity).getUserInfo()
                                if (userInfo != null) {
                                    LogManager.log(TAG, "Token刷新成功", "DEBUG")
                                    isTokenValid = true
                                } else {
                                    LogManager.log(TAG, "用户信息不存在", "ERROR")
                                    isTokenValid = false
                                }
                            } else {
                                LogManager.log(TAG, "Token刷新成功但返回的token为空", "ERROR")
                                isTokenValid = false
                            }
                        } else {
                            LogManager.log(TAG, "Token刷新失败，响应状态码不是200", "ERROR")
                            isTokenValid = false
                        }
                    } catch (e: Exception) {
                        LogManager.log(TAG, "解析token刷新响应失败: ${e.message}", "ERROR")
                        isTokenValid = false
                    } finally {
                        latch.countDown()
                    }
                }

                override fun onError(error: String) {
                    LogManager.log(TAG, "Token刷新失败: $error", "ERROR")
                    isTokenValid = false
                    latch.countDown()
                }
            })

            // 等待网络请求完成，最多等待5秒
            latch.await(5, java.util.concurrent.TimeUnit.SECONDS)
            return isTokenValid
        } catch (e: Exception) {
            LogManager.log(TAG, "检查token有效性失败: ${e.message}", "ERROR")
            return false
        }
    }

    override fun onDestroy() {
        super.onDestroy()
    }

    private fun handleIntent(intent: Intent?) {
        if (intent == null || intent.action != Intent.ACTION_VIEW) {
            LogManager.log(TAG, "无效的 Intent", "ERROR")
            finish()
            return
        }

        val uri = intent.data
        if (uri == null) {
            LogManager.log(TAG, "Intent 中没有 URI", "ERROR")
            finish()
            return
        }

        try {
            // 获取文件名和文件是否存在于WpsManagement目录的判断结果
            val (fileName, isInWpsManagement) = getFileName(uri)
            LogManager.log(TAG, "文件名: $fileName", "DEBUG")
            LogManager.log(TAG, "文件 URI: $uri", "DEBUG")
            LogManager.log(TAG, "文件是否在WpsManagement目录中: $isInWpsManagement", "DEBUG")
            handleFileUri(uri, fileName, isInWpsManagement)
        } catch (e: Exception) {
            LogManager.log(TAG, "处理 Intent 失败: ${e.message}", "ERROR")
            // 即使失败也转发给 WPS
            forwardToWps(null, uri)
        }
    }

    private fun getFileName(uri: Uri): Pair<String, Boolean> {
        var fileName = ""
        var isInWpsManagement = false
        
        try {
            // 获取WpsManagement目录路径
            val documentsDir = 
                android.os.Environment.getExternalStoragePublicDirectory(android.os.Environment.DIRECTORY_DOCUMENTS)
            val wpsManagementDir = File(documentsDir, "WpsManagement")
            val wpsManagementPath = wpsManagementDir.absolutePath
            
            val cursor = contentResolver.query(uri, null, null, null, null)
            cursor?.use {
                if (it.moveToFirst()) {
                    // 使用标准的OpenableColumns.DISPLAY_NAME列名
                    val nameIndex = it.getColumnIndex(android.provider.OpenableColumns.DISPLAY_NAME)
                    if (nameIndex != -1) {
                        fileName = it.getString(nameIndex)
                    }
                    
                    // 检查文件是否在WpsManagement目录中
                    val pathIndex = it.getColumnIndex("_data")
                    if (pathIndex != -1) {
                        val filePath = it.getString(pathIndex)
                        if (!filePath.isNullOrEmpty()) {
                            isInWpsManagement = filePath.contains(wpsManagementPath, ignoreCase = true)
                        }
                    }
                }
            }
            
            // 如果通过ContentResolver无法判断，尝试从URI路径中判断
            if (!isInWpsManagement) {
                val uriPath = uri.path
                if (!uriPath.isNullOrEmpty()) {
                    // 检查URI路径是否包含WpsManagement目录
                    isInWpsManagement = uriPath.contains("WpsManagement", ignoreCase = true)
                }
            }
        } catch (e: Exception) {
            LogManager.log(TAG, "获取文件名失败: ${e.message}", "ERROR")
        }

        // 如果ContentResolver无法获取文件名，尝试从URI路径中解析
        if (fileName.isEmpty()) {
            fileName = getFileNameFromUri(uri)
        }

        return Pair(fileName, isInWpsManagement)
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

    private fun handleFileUri(uri: Uri, fileName: String, isInWpsManagement: Boolean) {
        // 使用完整的URI字符串作为密码存储的键，确保唯一性
        val fileIdentifier = uri.toString()
        LogManager.log(TAG, "文件标识: $fileIdentifier", "DEBUG")
        LogManager.log(TAG, "传入的文件名: '$fileName'", "DEBUG")
        LogManager.log(TAG, "文件名长度: ${fileName.length}", "DEBUG")
        LogManager.log(TAG, "文件是否在WpsManagement目录中: $isInWpsManagement", "DEBUG")

        // 实现完整的文件处理流程
        processExternalContentUri(uri, fileName, isInWpsManagement) { localFile ->
            if (localFile != null) {
                val localFilePath = localFile.absolutePath
                LogManager.log(TAG, "文件处理完成，本地路径: $localFilePath", "DEBUG")
                // 保存本地文件路径到SharedPreferences
                saveFileUriToPreferences(localFilePath)
            } else {
                LogManager.log(TAG, "文件处理失败", "ERROR")
                // 保存原始URI作为备选
                saveFileUriToPreferences(fileIdentifier)
            }

            // 无论是否找到密码，都转发给 WPS
            forwardToWps(localFile, uri)
        }
    }

    /**
     * 处理外部传入的ContentURI，实现完整的文件处理流程
     */
    private fun processExternalContentUri(uri: Uri, originalFileName: String, isInWpsManagement: Boolean, callback: (File?) -> Unit) {
        try {
            // 1. 获取WpsManagement目录
            val documentsDir = 
                android.os.Environment.getExternalStoragePublicDirectory(android.os.Environment.DIRECTORY_DOCUMENTS)
            val wpsManagementDir = File(documentsDir, "WpsManagement")

            // 确保WpsManagement目录存在
            if (!wpsManagementDir.exists()) {
                wpsManagementDir.mkdirs()
                LogManager.log(TAG, "创建 WpsManagement 目录: ${wpsManagementDir.absolutePath}", "DEBUG")
            }

            // 2. 文件存在性检查与拷贝
            val targetFile = File(wpsManagementDir, originalFileName)
            if (targetFile.exists() && targetFile.length() > 0) {
                // 根据传入的isInWpsManagement参数判断是否直接打开文件
                if (isInWpsManagement) {
                    // 文件存在且来源于WpsManagement目录，直接打开文件
                    LogManager.log(TAG, "文件存在且来源于WpsManagement目录，直接使用: ${targetFile.absolutePath}", "DEBUG")
                    processFile(targetFile, callback)
                } else {
                    // 文件存在但不是来源于WpsManagement目录，显示弹窗询问用户
                    val builder = android.app.AlertDialog.Builder(this, android.R.style.Theme_Material_Dialog_Alert)
                    builder.setTitle("文件已存在")
                    builder.setMessage("当前文件已在/Documents/WpsManagement目录存在副本，是否覆盖文档内容")
                    
                    // 设置按钮样式和间距
                    builder.setNegativeButton("打开副本文件") { dialog, which ->
                        dialog.dismiss()
                        // 不执行任何操作，使用本地已存在的文件
                        processFile(targetFile, callback)
                    }
                    builder.setPositiveButton("覆盖副本文件") { dialog, which ->
                        dialog.dismiss()
                        
                        // 显示加载状态
                        val loadingBuilder = android.app.AlertDialog.Builder(this)
                        loadingBuilder.setMessage("正在处理文件...")
                        loadingBuilder.setCancelable(false)
                        val loadingDialog = loadingBuilder.create()
                        loadingDialog.show()
                        
                        // 在后台线程中执行文件操作
                        Thread {
                            try {
                                // 执行文件移除操作
                                if (targetFile.delete()) {
                                    LogManager.log(TAG, "成功删除已存在的文件: ${targetFile.absolutePath}", "DEBUG")
                                    // 执行文件拷贝
                                    val copySuccess = copyFileFromContentUri(uri, targetFile)
                                    runOnUiThread {
                                        loadingDialog.dismiss()
                                        if (copySuccess) {
                                            // 显示操作成功提示
                                            android.widget.Toast.makeText(this, "文件覆盖成功", android.widget.Toast.LENGTH_SHORT).show()
                                            processFile(targetFile, callback)
                                        } else {
                                            // 显示操作失败提示
                                            android.widget.Toast.makeText(this, "文件拷贝失败", android.widget.Toast.LENGTH_SHORT).show()
                                            LogManager.log(TAG, "文件拷贝失败: ${targetFile.absolutePath}", "DEBUG")
                                            callback(null)
                                        }
                                    }
                                } else {
                                    runOnUiThread {
                                        loadingDialog.dismiss()
                                        // 显示删除失败提示
                                        android.widget.Toast.makeText(this, "删除文件失败", android.widget.Toast.LENGTH_SHORT).show()
                                        LogManager.log(TAG, "删除文件失败: ${targetFile.absolutePath}", "DEBUG")
                                        processFile(targetFile, callback)
                                    }
                                }
                            } catch (e: Exception) {
                                runOnUiThread {
                                    loadingDialog.dismiss()
                                    // 显示错误提示
                                    android.widget.Toast.makeText(this, "文件操作失败", android.widget.Toast.LENGTH_SHORT).show()
                                    LogManager.log(TAG, "删除文件时发生错误: ${e.message}", "ERROR")
                                    processFile(targetFile, callback)
                                }
                            }
                        }.start()
                    }
                    val dialog = builder.create()
                    dialog.show()
                    
                    // 设置弹窗大小，根据屏幕尺寸动态计算
                    val window = dialog.window
                    if (window != null) {
                        val displayMetrics = resources.displayMetrics
                        val screenWidth = displayMetrics.widthPixels
                        val screenHeight = displayMetrics.heightPixels
                        
                        // 计算弹窗大小，使用屏幕宽度的70%和高度的35%
                        val dialogWidth = (screenWidth * 0.7).toInt()
                        val dialogHeight = (screenHeight * 0.35).toInt()
                        
                        window.setLayout(dialogWidth, dialogHeight)
                        window.setGravity(android.view.Gravity.CENTER) // 设置弹窗居中
                        
                        // 设置弹窗背景和边框
                        window.setBackgroundDrawableResource(android.R.drawable.dialog_frame)
                        
                        // 设置按钮样式
                        val negativeButton = dialog.getButton(android.app.AlertDialog.BUTTON_NEGATIVE)
                        val positiveButton = dialog.getButton(android.app.AlertDialog.BUTTON_POSITIVE)
                        
                        if (negativeButton != null && positiveButton != null) {
                            // 设置按钮文字颜色
                            negativeButton.setTextColor(resources.getColor(android.R.color.holo_blue_dark))
                            positiveButton.setTextColor(resources.getColor(android.R.color.holo_blue_dark))
                            
                            // 设置按钮间距
                            val layoutParams = negativeButton.layoutParams as android.widget.LinearLayout.LayoutParams
                            layoutParams.weight = 1f
                            layoutParams.marginStart = 16
                            layoutParams.marginEnd = 16
                            negativeButton.layoutParams = layoutParams
                            positiveButton.layoutParams = layoutParams
                        }
                    }
                }
            } else {
                // 文件不存在于WpsManagement目录中
                // 当用户从非WpsManagement目录打开文件时，显示提示弹窗
                if (!isInWpsManagement) {
                    showFileSavedNotification(callback, uri, targetFile)
                } else {
                    // 文件不存在且来源于WpsManagement目录，直接执行拷贝
                    if (!copyFileFromContentUri(uri, targetFile)) {
                        LogManager.log(TAG, "文件拷贝失败: ${targetFile.absolutePath}", "DEBUG")
                        callback(null)
                    } else {
                        processFile(targetFile, callback)
                    }
                }
            }
        } catch (e: Exception) {
            LogManager.log(TAG, "处理ContentURI失败: ${e.message}", "ERROR")
            callback(null)
        }
    }

    /**
     * 处理文件，读取UID和密码，初始化FileMeta对象
     */
    private fun processFile(file: File, callback: (File?) -> Unit) {
        try {
            // 读取uid
            val uid = readUidFromFile(file.absolutePath)
                ?: FileMetaFactory.createUid()
            // 读取密码
            val password = readAndParsePassword(file.absolutePath, uid)
            // 初始化FileMeta对象并获取权限信息
            initFileMetaWithPermissions(file.absolutePath, password, uid)
            callback(file)
        } catch (e: Exception) {
            LogManager.log(TAG, "处理文件失败: ${e.message}", "ERROR")
            callback(null)
        }
    }

    /**
     * 显示文件已另存通知弹窗
     * 当用户从非WpsManagement目录打开文件，且WpsManagement目录中不存在同名文件时触发
     */
    private fun showFileSavedNotification(callback: (File?) -> Unit, uri: Uri, targetFile: File) {
        val builder = android.app.AlertDialog.Builder(this, android.R.style.Theme_Material_Dialog_Alert)
        builder.setTitle("提示")
        builder.setMessage("当前文件已另存，后续请移步到/Documents/WpsManagement目录中查看")
        
        // 设置按钮样式和间距
        builder.setPositiveButton("知道了") { dialog, which ->
            dialog.dismiss()
            
            // 显示加载状态
            val loadingBuilder = android.app.AlertDialog.Builder(this)
            loadingBuilder.setMessage("正在处理文件...")
            loadingBuilder.setCancelable(false)
            val loadingDialog = loadingBuilder.create()
            loadingDialog.show()
            
            // 在后台线程中执行文件操作
            Thread {
                try {
                    // 执行文件拷贝
                    val copySuccess = copyFileFromContentUri(uri, targetFile)
                    runOnUiThread {
                        loadingDialog.dismiss()
                        if (copySuccess) {
                            // 显示操作成功提示
                            android.widget.Toast.makeText(this, "文件保存成功", android.widget.Toast.LENGTH_SHORT).show()
                            processFile(targetFile, callback)
                        } else {
                            // 显示操作失败提示
                            android.widget.Toast.makeText(this, "文件保存失败", android.widget.Toast.LENGTH_SHORT).show()
                            LogManager.log(TAG, "文件拷贝失败: ${targetFile.absolutePath}", "DEBUG")
                            callback(null)
                        }
                    }
                } catch (e: Exception) {
                    runOnUiThread {
                        loadingDialog.dismiss()
                        // 显示错误提示
                        android.widget.Toast.makeText(this, "文件操作失败", android.widget.Toast.LENGTH_SHORT).show()
                        LogManager.log(TAG, "文件操作时发生错误: ${e.message}", "ERROR")
                        callback(null)
                    }
                }
            }.start()
        }
        
        val dialog = builder.create()
        dialog.show()
        
        // 设置弹窗大小，根据屏幕尺寸动态计算
        val window = dialog.window
        if (window != null) {
            val displayMetrics = resources.displayMetrics
            val screenWidth = displayMetrics.widthPixels
            val screenHeight = displayMetrics.heightPixels
            
            // 计算弹窗大小，使用屏幕宽度的70%和高度的35%
            val dialogWidth = (screenWidth * 0.7).toInt()
            val dialogHeight = (screenHeight * 0.35).toInt()
            
            window.setLayout(dialogWidth, dialogHeight)
            window.setGravity(android.view.Gravity.CENTER) // 设置弹窗居中
            
            // 设置弹窗背景和边框
            window.setBackgroundDrawableResource(android.R.drawable.dialog_frame)
            
            // 设置按钮样式
            val positiveButton = dialog.getButton(android.app.AlertDialog.BUTTON_POSITIVE)
            if (positiveButton != null) {
                // 设置按钮文字颜色
                positiveButton.setTextColor(resources.getColor(android.R.color.holo_blue_dark))
            }
        }
    }

    /**
     * 读取密码并存储到缓存
     */
    private fun readAndParsePassword(filePath: String, uid: String): String? {
        LogManager.log(TAG, "开始读取密码并存储到缓存，文件路径: $filePath", "DEBUG")
        try {
            val file = File(filePath)
            LogManager.log(TAG, "文件存在: ${file.exists()}", "DEBUG")
            LogManager.log(TAG, "文件可读: ${file.canRead()}", "DEBUG")
            LogManager.log(TAG, "文件大小: ${file.length()} 字节", "DEBUG")

            val localPassword = FileMetaManager.getInstance().getPasswordFromFile(this, filePath)
            if (localPassword != null) {
                LogManager.log(TAG, "从本地文件读取到密码: $localPassword", "DEBUG")
                // 从ConfigStorage获取token
                val token = getTokenFromStorage()
                LogManager.log(TAG, "获取到token: ${if (token.isNullOrEmpty()) "空" else "已获取"}", "DEBUG")

                // 使用CountDownLatch等待网络请求完成
                val latch = java.util.concurrent.CountDownLatch(1)
                var resultPassword: String? = null

                // 调用获取文档密码接口
                LogManager.log(TAG, "开始调用获取文档密码接口", "DEBUG")
                NetworkManager.getInstance(this).getDocumentPassword(
                    docId = uid,
                    encryPassword = localPassword, // 这里直接使用从文件读取的密码，实际应用中可能需要加密
                    token = token,
                    callback = object : com.wpspasswordmanager.network.NetworkCallback {
                        override fun onSuccess(response: String) {
                            LogManager.log(TAG, "获取文档密码响应: $response", "DEBUG")
                            try {
                                val json = org.json.JSONObject(response)
                                if (json.getInt("status") == 200) {
                                    val data = json.getJSONObject("data")
                                    val documentPassword = data.optString("password")
                                    LogManager.log(TAG, "从接口获取到文档密码: $documentPassword", "DEBUG")
                                    resultPassword = documentPassword
                                } else {
                                    LogManager.log(TAG, "获取文档密码失败，响应状态码不是200", "ERROR")
                                    resultPassword = null
                                }
                            } catch (e: Exception) {
                                LogManager.log(TAG, "解析获取文档密码响应失败: ${e.message}", "ERROR")
                                resultPassword = null
                            } finally {
                                latch.countDown()
                            }
                        }

                        override fun onError(error: String) {
                            LogManager.log(TAG, "获取文档密码失败: $error", "ERROR")
                            resultPassword = null
                            latch.countDown()
                        }
                    }
                )

                // 等待网络请求完成，最多等待10秒
                latch.await(10, java.util.concurrent.TimeUnit.SECONDS)
                return resultPassword
            } else {
                LogManager.log(TAG, "本地文件中未找到密码", "DEBUG")
                return null
            }
        } catch (e: Exception) {
            LogManager.log(TAG, "读取本地文件密码失败: ${e.message}", "ERROR")
            return null
        }
    }

    private fun readUidFromFile(filePath: String): String? {
        LogManager.log(TAG, "开始读取uid并存储到缓存，文件路径: $filePath", "DEBUG")
        try {
            val file = File(filePath)
            LogManager.log(TAG, "文件存在: ${file.exists()}", "DEBUG")
            LogManager.log(TAG, "文件可读: ${file.canRead()}", "DEBUG")
            LogManager.log(TAG, "文件大小: ${file.length()} 字节", "DEBUG")

            val uid = FileMetaManager.getInstance().getUidFromFile(this, filePath)
            if (uid != null) {
                LogManager.log(TAG, "从本地文件读取到uid: $uid", "DEBUG")
            } else {
                LogManager.log(TAG, "本地文件中未找到uid", "DEBUG")
            }
            return uid
        } catch (e: Exception) {
            LogManager.log(TAG, "读取本地文件uid失败: ${e.message}", "ERROR")
            return null
        }
    }

    /**
     * 初始化FileMeta对象并获取权限信息
     */
    private fun initFileMetaWithPermissions(filePath: String, password: String?, uid: String) {
        LogManager.log(TAG, "开始初始化FileMeta对象并获取权限信息，文件路径: $filePath", "DEBUG")

        try {
            // 从ConfigStorage获取token
            val token = getTokenFromStorage()
            LogManager.log(TAG, "获取到token: ${if (token.isNullOrEmpty()) "空" else "已获取"}", "DEBUG")

            // 尝试获取权限信息
            LogManager.log(TAG, "开始获取文档权限信息", "DEBUG")

            // 使用NetworkManager获取文档权限信息
            NetworkManager.getInstance(this).getDocumentOwner(
                docId = uid,
                token = token,
                callback = object : com.wpspasswordmanager.network.NetworkCallback {
                    override fun onSuccess(response: String) {
                        LogManager.log(TAG, "获取文档权限响应: $response", "DEBUG")
                        try {
                            val json = JSONObject(response)
                            if (json.getInt("status") == 200) {
                                val data = json.getJSONObject("data")
                                val ownerAccount = data.optString("ownerAccount")
                                val ownerName = data.optString("ownerName")
                                val readAuth = data.optBoolean("readAuth", false)
                                val writeAuth = data.optBoolean("writeAuth", false)

                                // 存储到FileMetaFactory
                                FileMetaFactory.initFileMetaWithPermissions(
                                    filePath = filePath,
                                    oldPass = password,
                                    uid = uid,
                                    ownerAccount = ownerAccount,
                                    ownerName = ownerName,
                                    readAuth = readAuth,
                                    writeAuth = writeAuth
                                )

                                LogManager.log(
                                    TAG,
                                    "FileMeta对象初始化成功，权限信息: readAuth=$readAuth, writeAuth=$writeAuth, ownerAccount=$ownerAccount, ownerName=$ownerName",
                                    "DEBUG"
                                )
                            } else {
                                // 响应状态码不是200，使用默认权限
                                initFileMetaWithDefaultPermissions(filePath, password, uid)
                            }
                        } catch (e: Exception) {
                            LogManager.log(TAG, "解析权限响应失败: ${e.message}", "ERROR")
                            // 解析失败时使用默认权限
                            initFileMetaWithDefaultPermissions(filePath, password, uid)
                        }
                    }

                    override fun onError(error: String) {
                        LogManager.log(TAG, "获取文档权限失败: $error", "ERROR")
                        // 网络请求失败时使用默认权限
                        initFileMetaWithDefaultPermissions(filePath, password, uid)
                    }
                }
            )
        } catch (e: Exception) {
            LogManager.log(TAG, "初始化FileMeta对象失败: ${e.message}", "ERROR")
            initFileMetaWithDefaultPermissions(filePath, password, uid)
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
            LogManager.log(TAG, "获取token失败: ${e.message}", "ERROR")
            return null
        }
    }

    /**
     * 使用默认权限初始化FileMeta对象
     */
    private fun initFileMetaWithDefaultPermissions(
        filePath: String,
        password: String?,
        uid: String
    ) {
        LogManager.log(TAG, "使用默认权限初始化FileMeta对象", "DEBUG")
        FileMetaFactory.initFileMetaWithPermissions(
            filePath = filePath,
            oldPass = password,
            uid = uid,
            ownerAccount = null,
            ownerName = null,
            readAuth = false,
            writeAuth = false
        )
        LogManager.log(TAG, "FileMeta对象初始化成功，使用默认权限设置", "DEBUG")
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
                        LogManager.log(TAG, "文件拷贝完成，大小: $totalBytes 字节", "DEBUG")
                        return true
                    } else {
                        LogManager.log(
                            TAG,
                            "文件拷贝不完整，期望大小: $totalBytes, 实际大小: ${targetFile.length()}",
                            "ERROR"
                        )
                        // 删除不完整的文件
                        targetFile.delete()
                        return false
                    }
                }
            }
            return false
        } catch (e: Exception) {
            LogManager.log(TAG, "拷贝文件失败: ${e.message}", "ERROR")
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
            LogManager.log(TAG, "保存文件URI到WpsAccessibilityService: $uri", "DEBUG")
        } catch (e: Exception) {
            LogManager.log(TAG, "保存文件URI失败: ${e.message}", "ERROR")
        }
    }

    private fun forwardToWps(localFile: File?, originalUri: Uri) {
        try {
            if (localFile != null) {
                val shareUri = getShareableUriFromFile(this, localFile)
                LogManager.log(TAG, "插件转换后唤起WPS的ContentURI: $shareUri", "DEBUG")
                val wpsIntent = Intent(Intent.ACTION_VIEW)
                wpsIntent.setDataAndType(
                    shareUri,
                    "application/vnd.openxmlformats-officedocument.wordprocessingml.document"
                )

                wpsIntent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                wpsIntent.addFlags(Intent.FLAG_GRANT_WRITE_URI_PERMISSION)
                wpsIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)

                wpsIntent.putExtra("OpenMode", "Normal")
                wpsIntent.putExtra("NeedCreateTemp", false)
                wpsIntent.putExtra("ReadOnly", false)

                val wpsPackage = findWpsPackage()
                if (wpsPackage != null) {
                    wpsIntent.setPackage(wpsPackage)
                    grantUriPermission(
                        wpsPackage,
                        shareUri,
                        Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION
                    )

                    startActivity(wpsIntent)
                    LogManager.log(TAG, "通过FileProvider启动WPS成功，包名: $wpsPackage，文件: ${localFile.absolutePath}", "DEBUG")
                    LogManager.log(TAG, "已授予WPS应用读写权限", "DEBUG")
                    LogManager.log(TAG, "已添加WPS特定参数，尝试阻止创建副本", "DEBUG")
                } else {
                    LogManager.log(TAG, "未找到WPS应用，尝试使用文件选择器", "DEBUG")
                    val chooserIntent = Intent.createChooser(wpsIntent, "选择应用打开文件")
                    if (chooserIntent.resolveActivity(packageManager) != null) {
                        chooserIntent.flags =
                            Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION
                        startActivity(chooserIntent)
                        LogManager.log(TAG, "使用文件选择器启动成功", "DEBUG")
                    } else {
                        LogManager.log(TAG, "没有应用可以打开此文件", "ERROR")
                        showErrorNotification("错误", "没有应用可以打开此文件")
                    }
                }
            } else {
                LogManager.log(TAG, "本地文件不存在，尝试使用原始URI", "DEBUG")
                LogManager.log(TAG, "插件转换后唤起WPS的ContentURI: $originalUri", "DEBUG")
                val wpsIntent = Intent(Intent.ACTION_VIEW)
                wpsIntent.data = originalUri
                wpsIntent.flags =
                    Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK

                val wpsPackage = findWpsPackage()
                if (wpsPackage != null) {
                    wpsIntent.setPackage(wpsPackage)
                    startActivity(wpsIntent)
                    LogManager.log(TAG, "使用原始URI启动WPS成功，包名: $wpsPackage", "DEBUG")
                } else {
                    LogManager.log(TAG, "没有应用可以打开此文件", "ERROR")
                    showErrorNotification("错误", "没有应用可以打开此文件")
                }
            }
        } catch (e: Exception) {
            LogManager.log(TAG, "启动 WPS 失败: ${e.message}", "ERROR")
            showErrorNotification("启动失败", "无法启动WPS应用")
        } finally {
            finish()
        }
    }

    private fun findWpsPackage(): String? {
        val configStorage = ConfigStorage.getInstance(this)
        
        val selectedPackage = configStorage.getTargetWpsPackage()
        if (selectedPackage != null) {
            try {
                packageManager.getPackageInfo(selectedPackage, 0)
                LogManager.log(TAG, "使用用户选择的WPS包: $selectedPackage", "DEBUG")
                return selectedPackage
            } catch (e: PackageManager.NameNotFoundException) {
                LogManager.log(TAG, "用户选择的WPS包 $selectedPackage 已卸载，尝试自动查找", "WARN")
                configStorage.clearTargetWpsPackage()
            }
        }
        
        LogManager.log(TAG, "未设置用户选择的WPS包，尝试自动查找", "DEBUG")
        val wpsPackages = arrayOf("cn.wps.moffice_eng", "cn.wps.moffice", "cn.wps.wpsoffice")
        for (pkg in wpsPackages) {
            try {
                packageManager.getPackageInfo(pkg, 0)
                LogManager.log(TAG, "自动找到WPS应用，包名: $pkg", "DEBUG")
                configStorage.saveTargetWpsPackage(pkg)
                return pkg
            } catch (e: PackageManager.NameNotFoundException) {
                LogManager.log(TAG, "WPS包 $pkg 不存在", "DEBUG")
            }
        }
        return null
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
            LogManager.log(TAG, "显示通知失败: ${e.message}", "ERROR")
        }
    }
}