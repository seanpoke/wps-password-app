package com.wpspasswordmanager.business

import android.content.Context
import android.net.Uri
import android.util.Log
import java.io.File
import java.io.InputStream

class FileMetaManager private constructor() {

    companion object {
        private const val TAG = "FileMetaManager"

        private var instance: FileMetaManager? = null

        fun getInstance(): FileMetaManager {
            if (instance == null) {
                instance = FileMetaManager()
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
                Log.d(TAG, "从文件file对象读取密码成功")
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
            val zipPassword =
                ZipExtraFieldManager.getInstance().readPasswordFromInputStream(inputStream)
            if (zipPassword != null) {
                Log.d(TAG, "从文件流InputStream读取密码成功")
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
     * 从文件中读取keyVersion
     * 只使用ZIP Extra Field方式读取，按照读数据.md文档要求
     */
    fun getKeyVersionFromFile(context: Context, filePath: String): String? {
        try {
            Log.d(TAG, "开始获取文件keyVersion，文件路径: $filePath")
            val file = File(filePath)

            // 检查文件是否存在
            if (!file.exists()) {
                Log.e(TAG, "文件不存在: $filePath")
                return null
            }

            // 检查文件是否可读
            if (!file.canRead()) {
                Log.e(TAG, "文件不可读: $filePath")
                return null
            }

            // 直接从文件读取keyVersion
            return readKeyVersionFromFile(context, file)
        } catch (e: Exception) {
            Log.e(TAG, "获取文件keyVersion失败", e)
            return null
        }
    }

    /**
     * 从文件读取keyVersion
     * 只使用ZIP Extra Field方式读取，按照读数据.md文档要求
     */
    private fun readKeyVersionFromFile(context: Context, file: File): String? {
        try {
            Log.d(TAG, "尝试从文件读取keyVersion: ${file.absolutePath}")

            // 从ZIP Extra Field读取keyVersion（按照读数据.md文档要求）
            val zipKeyVersion =
                ZipExtraFieldManager.getInstance().readKeyVersionFromInputStream(file.inputStream())
            if (zipKeyVersion != null) {
                Log.d(TAG, "从ZIP Extra Field读取keyVersion成功")
                return zipKeyVersion
            }

            Log.d(TAG, "ZIP Extra Field未找到keyVersion")
            return null
        } catch (e: Exception) {
            Log.e(TAG, "从文件读取keyVersion失败", e)
            return null
        }
    }

    /**
     * 从文件中读取uid
     * 只使用ZIP Extra Field方式读取，按照读数据.md文档要求
     */
    fun getUidFromFile(context: Context, filePath: String): String? {
        try {
            Log.d(TAG, "开始获取文件uid，文件路径: $filePath")
            val file = File(filePath)

            // 检查文件是否存在
            if (!file.exists()) {
                Log.e(TAG, "文件不存在: $filePath")
                return null
            }

            // 检查文件是否可读
            if (!file.canRead()) {
                Log.e(TAG, "文件不可读: $filePath")
                return null
            }

            // 直接从文件读取uid
            return readUidFromFile(context, file)
        } catch (e: Exception) {
            Log.e(TAG, "获取文件uid失败", e)
            return null
        }
    }

    /**
     * 从文件读取uid
     * 只使用ZIP Extra Field方式读取，按照读数据.md文档要求
     */
    private fun readUidFromFile(context: Context, file: File): String? {
        try {
            Log.d(TAG, "尝试从文件读取uid: ${file.absolutePath}")

            // 从ZIP Extra Field读取uid（按照读数据.md文档要求）
            val zipUid =
                ZipExtraFieldManager.getInstance().readUidFromInputStream(file.inputStream())
            if (zipUid != null) {
                Log.d(TAG, "从ZIP Extra Field读取uid成功")
                return zipUid
            }

            Log.d(TAG, "ZIP Extra Field未找到uid")
            return null
        } catch (e: Exception) {
            Log.e(TAG, "从文件读取uid失败", e)
            return null
        }
    }


    /**
     * 将密码写入文件
     */
    fun writeMetaDataToFile(file: File, fileMeta: FileMeta) {
        val filePath = fileMeta.filePath
        val uid = fileMeta.uid
        val password = FileMetaFactory.getWritePassword(filePath)
        // 写入元数据时使用全局存储的keyVersion
        val keyVersion = getGlobalKeyVersion()
        try {
            if (!file.exists() || !file.canWrite()) {
                Log.e(TAG, "文件不存在或不可写: $filePath")
                return
            }

            Log.d(TAG, "开始写入元数据到文件")
            val success = ZipExtraFieldManager.getInstance()
                .appendMetaDataToFileEnd(filePath, uid, password, keyVersion)
            if (success) {
                Log.d(TAG, "写入元数据到文件成功: $filePath, keyVersion=$keyVersion")
                logFileTail(filePath)
            } else {
                Log.e(TAG, "写入元数据到文件失败: $filePath")
            }
        } catch (e: Exception) {
            Log.e(TAG, "写入元数据到文件失败异常", e)
        }
    }
    
    /**
     * 获取全局存储的keyVersion
     */
    private fun getGlobalKeyVersion(): String {
        return try {
            val context = com.wpspasswordmanager.WpsPasswordManagerApplication.instance
            com.wpspasswordmanager.storage.ConfigStorage.getInstance(context).getKeyVersion()
        } catch (e: Exception) {
            Log.e(TAG, "获取全局keyVersion失败，使用默认值: ${e.message}")
            "default"
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
                val startPos = if (fileLength > 1024) fileLength - 1024 else 0
                val buffer = ByteArray(1024)

                file.inputStream().use { inputStream ->
                    inputStream.skip(startPos)
                    val bytesRead = inputStream.read(buffer)
                    if (bytesRead > 0) {
                        // 查找WPPM标记
                        val wppmSignature = "WPPM"
                        val wppmBytes = wppmSignature.toByteArray()
                        val bufferContent = buffer.sliceArray(0 until bytesRead)

                        // 查找所有WPPM标记的位置
                        val wppmPositions = mutableListOf<Int>()
                        for (i in 0 until bufferContent.size - wppmBytes.size + 1) {
                            var match = true
                            for (j in wppmBytes.indices) {
                                if (bufferContent[i + j] != wppmBytes[j]) {
                                    match = false
                                    break
                                }
                            }
                            if (match) {
                                wppmPositions.add(i)
                            }
                        }

                        if (wppmPositions.isNotEmpty()) {
                            Log.d(TAG, "找到 ${wppmPositions.size} 个WPPM标记")
                            
                            // 只打印WPPM标记及其后续内容
                            for (pos in wppmPositions) {
                                // 从WPPM标记开始，取后面的内容（最多200字节）
                                val endPos = minOf(pos + 200, bufferContent.size)
                                val wppmContent = bufferContent.sliceArray(pos until endPos)
                                
                                // 将内容转换为十六进制字符串，避免乱码
                                val hexString = wppmContent.joinToString(" ") { "%02X".format(it) }
                                // 将内容转换为字符字符串，非可打印字符用.代替
                                val charString = wppmContent.joinToString("") { if (it in 32..126) it.toChar().toString() else "." }
                                Log.d(TAG, "WPPM标记位置: $pos, 内容（十六进制）: $hexString, 内容（字符）: $charString")
                            }
                        }
                    }
                }
            } else {
                Log.e(TAG, "文件不存在或不可读: $filePath")
            }
        } catch (e: Exception) {
            Log.e(TAG, "打印文件尾部失败", e)
        }
    }
}
