package com.wpspasswordmanager.business

import android.util.Log
import org.apache.poi.poifs.filesystem.POIFSFileSystem
import org.apache.poi.poifs.crypt.EncryptionInfo
import java.io.FileInputStream
import java.io.File
import java.security.MessageDigest
import kotlin.math.min

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
}