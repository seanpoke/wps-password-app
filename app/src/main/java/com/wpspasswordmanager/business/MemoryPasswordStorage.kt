package com.wpspasswordmanager.business

import android.util.Log
import java.util.concurrent.ConcurrentHashMap

class MemoryPasswordStorage private constructor() {

    companion object {
        private const val TAG = "MemoryPasswordStorage"
        private var instance: MemoryPasswordStorage? = null

        fun getInstance(): MemoryPasswordStorage {
            if (instance == null) {
                instance = MemoryPasswordStorage()
            }
            return instance!!
        }
    }

    // 使用ConcurrentHashMap存储密码，确保线程安全
    private val passwordMap = ConcurrentHashMap<String, String>()

    /**
     * 存储密码到内存
     */
    fun storePasswordInMemory(key: String, password: String): Boolean {
        try {
            passwordMap[key] = password
            Log.d(TAG, "密码已存储到内存: $key")
            return true
        } catch (e: Exception) {
            Log.e(TAG, "存储密码到内存失败", e)
            return false
        }
    }

    /**
     * 从内存中获取密码
     */
    fun getPasswordFromMemory(key: String): String? {
        try {
            val password = passwordMap[key]
            Log.d(TAG, "从内存中获取密码: $key")
            return password
        } catch (e: Exception) {
            Log.e(TAG, "从内存中获取密码失败", e)
            return null
        }
    }

    /**
     * 从内存中移除密码
     */
    fun removePasswordFromMemory(key: String): Boolean {
        try {
            passwordMap.remove(key)
            Log.d(TAG, "从内存中移除密码: $key")
            return true
        } catch (e: Exception) {
            Log.e(TAG, "从内存中移除密码失败", e)
            return false
        }
    }

    /**
     * 清空所有内存中的密码
     */
    fun clearAllPasswords() {
        try {
            passwordMap.clear()
            Log.d(TAG, "已清空所有内存中的密码")
        } catch (e: Exception) {
            Log.e(TAG, "清空内存密码失败", e)
        }
    }

    /**
     * 获取所有存储的密码键
     */
    fun getAllKeys(): Set<String> {
        return passwordMap.keys
    }

    /**
     * 检查内存中是否存在密码
     */
    fun containsPassword(key: String): Boolean {
        return passwordMap.containsKey(key)
    }
}
