package com.wpspasswordmanager.business

import android.content.Context
import android.net.Uri
import android.util.Log
import java.io.File
import java.io.InputStream

class PasswordStorage private constructor() {

    companion object {
        private const val TAG = "PasswordStorage"
        private const val WPS_PASSWORD_METADATA_KEY = "wpsPassword"

        private var instance: PasswordStorage? = null

        fun getInstance(): PasswordStorage {
            if (instance == null) {
                instance = PasswordStorage()
            }
            return instance!!
        }
    }

    /**
     * 从文件元数据读取密码
     */
    fun getPassword(context: Context, key: String): String? {
        try {
            Log.d(TAG, "开始读取密码，文件路径: $key")
            
            // 检查是否是content URI
            if (key.startsWith("content://")) {
                val uri = Uri.parse(key)
                return readPasswordFromContentUri(context, uri)
            } else {
                // 处理普通文件路径
                val file = File(key)
                if (file.exists()) {
                    return readPasswordFromFile(context, file)
                } else {
                    Log.e(TAG, "文件不存在，无法读取密码")
                    return null
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "读取密码失败", e)
            return null
        }
    }

    /**
     * 检查密码是否存在
     */
    fun hasPassword(context: Context, key: String): Boolean {
        return getPassword(context, key) != null
    }

    /**
     * 写入密码
     * 按照核心流程文档要求：
     * 1. 对于本地文件，直接写入密码
     * 2. 对于Content URI，尝试使用ParcelFileDescriptor直接操作
     */
    fun writePassword(context: Context, key: String, password: String): Boolean {
        try {
            Log.d(TAG, "[时间戳: ${System.currentTimeMillis()}] 开始写入密码，文件路径: $key")
            Log.d(TAG, "[时间戳: ${System.currentTimeMillis()}] 密码: '$password'，长度: ${password.length}")
            
            // 检查密码是否变化
            val currentPassword = getPassword(context, key)
            Log.d(TAG, "[时间戳: ${System.currentTimeMillis()}] 当前文件中的密码: '${currentPassword ?: "无"}'")
            if (currentPassword == password) {
                Log.d(TAG, "[时间戳: ${System.currentTimeMillis()}] 密码未变化，跳过写入操作: $key")
                return true
            }
            
            // 检查是否是content URI
            if (key.startsWith("content://")) {
                val uri = Uri.parse(key)
                Log.d(TAG, "[时间戳: ${System.currentTimeMillis()}] 写入密码到Content URI: $uri")
                val result = writePasswordToContentUri(context, uri, password)
                Log.d(TAG, "[时间戳: ${System.currentTimeMillis()}] 写入密码到Content URI结果: $result")
                return result
            } else {
                // 处理普通文件路径
                val file = File(key)
                if (file.exists() && file.canWrite()) {
                    Log.d(TAG, "[时间戳: ${System.currentTimeMillis()}] 文件存在，大小: ${file.length()} bytes, 可写: ${file.canWrite()}")
                    // 直接写入密码到本地文件
                    val result = writePasswordToFile(context, file, password)
                    Log.d(TAG, "[时间戳: ${System.currentTimeMillis()}] 写入密码到本地文件结果: $result")
                    return result
                } else {
                    Log.e(TAG, "[时间戳: ${System.currentTimeMillis()}] 文件不存在或不可写，无法写入密码")
                    return false
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "[时间戳: ${System.currentTimeMillis()}] 写入密码失败", e)
            return false
        }
    }

    /**
     * 从Content URI写入密码
     */
    private fun writePasswordToContentUri(context: Context, uri: Uri, password: String): Boolean {
        try {
            Log.d(TAG, "尝试从Content URI写入密码: $uri")
            
            // 按照核心流程文档要求：采用方案5（ParcelFileDescriptor直接操作）
            Log.d(TAG, "使用ParcelFileDescriptor直接操作写入密码")
            val parcelResult = ZipExtraFieldManager.getInstance().writePasswordWithParcelFileDescriptor(context, uri, password)
            if (parcelResult) {
                Log.d(TAG, "使用ParcelFileDescriptor直接操作写入密码成功")
                return true
            }
            
            Log.e(TAG, "ParcelFileDescriptor直接操作失败")
            return false
        } catch (e: Exception) {
            Log.e(TAG, "从Content URI写入密码失败", e)
            return false
        }
    }
    
    /**
     * 使用临时文件从Content URI写入密码
     */
    private fun writePasswordToContentUriWithTempFile(context: Context, uri: Uri, password: String): Boolean {
        try {
            Log.d(TAG, "尝试使用临时文件从Content URI写入密码")
            
            // 创建临时文件
            val tempFile = File.createTempFile("temp", ".docx")
            tempFile.deleteOnExit()
            
            // 复制内容到临时文件
            context.contentResolver.openInputStream(uri)?.use { inputStream ->
                tempFile.outputStream().use { outputStream ->
                    inputStream.copyTo(outputStream)
                }
            }
            
            // 写入密码到临时文件
            val success = writePasswordToFile(context, tempFile, password)
            
            if (success) {
                // 写回原文件
                context.contentResolver.openOutputStream(uri)?.use { outputStream ->
                    tempFile.inputStream().use { inputStream ->
                        inputStream.copyTo(outputStream)
                    }
                }
            }
            
            return success
        } catch (e: Exception) {
            Log.e(TAG, "使用临时文件写入密码失败", e)
            return false
        }
    }

    /**
     * 写入密码到文件
     */
    private fun writePasswordToFile(context: Context, file: File, password: String): Boolean {
        try {
            Log.d(TAG, "尝试写入密码到文件: ${file.absolutePath}")
            
            // 尝试使用ZIP Extra Field
            val zipResult = ZipExtraFieldManager.getInstance().writePassword(file, password)
            if (zipResult) {
                Log.d(TAG, "使用ZIP Extra Field写入密码成功")
                return true
            }
            
            // 备用方案：使用文件属性
            Log.w(TAG, "ZIP Extra Field写入失败，尝试使用文件属性")
            return FileManager.getInstance().writePasswordToFileComment(context, file.absolutePath, password)
        } catch (e: Exception) {
            Log.e(TAG, "写入密码失败", e)
            return false
        }
    }

    /**
     * 从Content URI读取密码
     */
    private fun readPasswordFromContentUri(context: Context, uri: Uri): String? {
        try {
            Log.d(TAG, "尝试从Content URI读取密码: $uri")
            
            // 尝试使用不同的方法打开输入流
            try {
                context.contentResolver.openInputStream(uri)?.use { inputStream ->
                    // 检查输入流是否为空
                    val available = inputStream.available()
                    if (available == 0) {
                        Log.w(TAG, "Content URI输入流为空，可能是权限问题或文件未准备好")
                        // 尝试使用临时文件方法
                        return readPasswordFromContentUriWithTempFile(context, uri)
                    }
                    return readPasswordFromInputStream(inputStream)
                }
            } catch (securityException: SecurityException) {
                Log.e(TAG, "权限被拒绝，尝试使用其他方法")
                // 尝试使用临时文件方法
                return readPasswordFromContentUriWithTempFile(context, uri)
            } catch (e: Exception) {
                Log.e(TAG, "打开Content URI输入流失败", e)
                return null
            }
            
            Log.e(TAG, "无法打开Content URI输入流")
            return null
        } catch (e: Exception) {
            Log.e(TAG, "从Content URI读取密码失败", e)
            return null
        }
    }
    
    /**
     * 使用临时文件从Content URI读取密码
     */
    private fun readPasswordFromContentUriWithTempFile(context: Context, uri: Uri): String? {
        try {
            Log.d(TAG, "尝试使用临时文件从Content URI读取密码")
            
            // 创建临时文件
            val tempFile = File.createTempFile("temp", ".docx")
            tempFile.deleteOnExit()
            
            // 复制内容到临时文件
            context.contentResolver.openInputStream(uri)?.use { inputStream ->
                tempFile.outputStream().use { outputStream ->
                    inputStream.copyTo(outputStream)
                }
            }
            
            // 从临时文件读取密码
            return readPasswordFromFile(context, tempFile)
        } catch (e: Exception) {
            Log.e(TAG, "使用临时文件读取密码失败", e)
            return null
        }
    }

    /**
     * 从文件读取密码
     * 只使用ZIP Extra Field方式读取，按照读数据.md文档要求
     */
    private fun readPasswordFromFile(context: Context, file: File): String? {
        try {
            Log.d(TAG, "尝试从文件读取密码: ${file.absolutePath}")
            
            // 从ZIP Extra Field读取密码（按照读数据.md文档要求）
            val zipPassword = ZipExtraFieldManager.getInstance().readPassword(file)
            if (zipPassword != null) {
                Log.d(TAG, "从ZIP Extra Field读取密码成功")
                return zipPassword
            }
            
            Log.d(TAG, "ZIP Extra Field未找到密码")
            return null
        } catch (e: Exception) {
            Log.e(TAG, "从文件读取密码失败", e)
            return null
        }
    }

    /**
     * 从输入流读取密码（直接流读取模式）
     * 按照读数据.md文档要求：从输入流中读取ZIP Extra Field中的密码
     */
    private fun readPasswordFromInputStream(inputStream: InputStream): String? {
        try {
            Log.d(TAG, "尝试从输入流读取密码")
            
            // 检查输入流是否为空
            val available = inputStream.available()
            if (available == 0) {
                Log.w(TAG, "输入流为空，无法读取密码")
                return null
            }
            
            // 从ZIP Extra Field读取密码（直接流读取模式，按照读数据.md文档要求）
            val zipPassword = ZipExtraFieldManager.getInstance().readPasswordFromInputStream(inputStream)
            if (zipPassword != null) {
                Log.d(TAG, "从ZIP Extra Field读取密码成功")
                return zipPassword
            }
            
            Log.d(TAG, "ZIP Extra Field未找到密码")
            return null
        } catch (e: Exception) {
            Log.e(TAG, "从输入流读取密码失败", e)
            return null
        }
    }



    /**
     * 获取文件扩展名
     */
    private fun getFileExtension(fileName: String): String {
        val lastDotIndex = fileName.lastIndexOf('.')
        return if (lastDotIndex > 0) {
            fileName.substring(lastDotIndex + 1).lowercase()
        } else {
            ""
        }
    }
}
