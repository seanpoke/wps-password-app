package com.wpspasswordmanager.business

import android.content.Context
import android.util.Log
import java.util.concurrent.ConcurrentHashMap

class MemoryPasswordStorage private constructor() {

    companion object {
        private const val TAG = "MemoryPasswordStorage"
        private var instance: MemoryPasswordStorage? = null
        private var context: Context? = null

        fun getInstance(): MemoryPasswordStorage {
            if (instance == null) {
                instance = MemoryPasswordStorage()
            }
            return instance!!
        }

        fun init(context: Context) {
            this.context = context
        }
    }

    // 使用ConcurrentHashMap存储密码，确保线程安全
    private val passwordMap = ConcurrentHashMap<String, String>()

    /**
     * 存储密码到内存
     */
    fun storePasswordInMemory(key: String, password: String, fileUri: String? = null): Boolean {
        try {
            // 存储到内存
            passwordMap[key] = password
            Log.d(TAG, "密码已存储到内存: $key")

            return true
        } catch (e: Exception) {
            Log.e(TAG, "存储密码失败", e)
            return false
        }
    }

    /**
     * 从内存中获取密码
     */
    fun getPasswordFromMemory(key: String): String? {
        try {
            // 从内存中获取
            val password = passwordMap[key]
            if (password != null) {
                Log.d(TAG, "从内存中获取密码: $key")
                return password
            }

            return null
        } catch (e: Exception) {
            Log.e(TAG, "获取密码失败", e)
            return null
        }
    }

    /**
     * 从内存中移除密码
     */
    fun removePasswordFromMemory(key: String): Boolean {
        try {
            // 从内存中移除
            passwordMap.remove(key)
            Log.d(TAG, "从内存中移除密码: $key")

            return true
        } catch (e: Exception) {
            Log.e(TAG, "移除密码失败", e)
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
        val keys = mutableSetOf<String>()
        
        // 添加内存中的键
        keys.addAll(passwordMap.keys)
        
        return keys
    }

    /**
     * 检查内存中是否存在密码
     */
    fun containsPassword(key: String): Boolean {
        // 检查内存
        return passwordMap.containsKey(key)
    }
}
