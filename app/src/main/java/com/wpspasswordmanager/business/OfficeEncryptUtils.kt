package com.wpspasswordmanager.business

import android.util.Log
import org.apache.poi.poifs.filesystem.POIFSFileSystem
import org.apache.poi.poifs.crypt.EncryptionInfo
import java.io.FileInputStream
import java.io.File

object OfficeEncryptUtils {

    private const val TAG = "OfficeEncryptUtils"
    
    /**
     * 验证文件密码是否正确
     * @param file 文件对象
     * @param password 待验证的密码
     * @return 密码是否正确
     */
    fun verifyPassword(file: File, password: String): Boolean {
        try {
            Log.d(TAG, "开始验证密码: ${file.absolutePath}")
            val fis = FileInputStream(file)
            POIFSFileSystem(fis).use { fs ->
                // 尝试获取EncryptionInfo
                val info = EncryptionInfo(fs)
                val decryptor = info.decryptor
                
                // 验证密码
                val isCorrect = decryptor.verifyPassword(password)
                Log.d(TAG, "密码验证结果: $isCorrect")
                return isCorrect
            }
        } catch (e: Exception) {
            Log.d(TAG, "密码验证失败: ${e.message}")
            e.printStackTrace()
            // 密码错误或文件损坏
            return false
        }
    }

    /**
     * 检查文件是否加密
     * @param file 文件对象
     * @return 文件是否加密
     */
    fun isFileEncrypted(file: File): Boolean {
        try {
            Log.d(TAG, "开始检查文件是否加密: ${file.absolutePath}")
            val fis = FileInputStream(file)
            POIFSFileSystem(fis).use { fs ->
                // 尝试获取EncryptionInfo
                EncryptionInfo(fs)
                // 如果成功获取EncryptionInfo，说明文件是加密的
                Log.d(TAG, "文件已加密")
                return true
            }
        } catch (e: Exception) {
            Log.d(TAG, "文件未加密: ${e.message}")
            // 如果获取EncryptionInfo失败，说明文件未加密
            return false
        }
    }
}