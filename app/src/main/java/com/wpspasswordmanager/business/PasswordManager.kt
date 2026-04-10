package com.wpspasswordmanager.business

import android.content.Context
import android.net.Uri
import android.util.Log
import java.io.File
import java.io.InputStream

class PasswordManager private constructor() {

    companion object {
        private const val TAG = "PasswordManager"

        private var instance: PasswordManager? = null

        fun getInstance(): PasswordManager {
            if (instance == null) {
                instance = PasswordManager()
            }
            return instance!!
        }
    }

    /**
     * 从文件元数据读取密码
     */
    fun getPasswordFromFile(context: Context, key: String): String? {
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
}
