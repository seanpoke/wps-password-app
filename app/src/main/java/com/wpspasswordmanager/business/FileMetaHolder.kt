package com.wpspasswordmanager.business

import android.util.Log

object FileMetaHolder {
    private const val TAG = "PasswordHolder"
    
    var cachedPassword: String? = null
    var cachedUid: String? = null
    var targetFileName: String? = null
    
    /**
     * 存储密码和文件名
     */
    fun storePassword(password: String, fileName: String) {
        cachedPassword = password
        targetFileName = fileName
        Log.d(TAG, "密码已存储: $password, 文件名: $fileName")
    }
    
    /**
     * 存储uid和文件名
     */
    fun storeUid(uid: String, fileName: String) {
        cachedUid = uid
        targetFileName = fileName
        Log.d(TAG, "uid已存储: $uid, 文件名: $fileName")
    }
    
    /**
     * 清除缓存
     */
    fun clear() {
        cachedPassword = null
        cachedUid = null
        targetFileName = null
        Log.d(TAG, "缓存已清除")
    }
    
    /**
     * 检查是否有缓存的密码
     */
    fun hasCachedPassword(): Boolean {
        return cachedPassword != null && cachedPassword!!.isNotEmpty()
    }
    
    /**
     * 检查是否有缓存的uid
     */
    fun hasCachedUid(): Boolean {
        return cachedUid != null && cachedUid!!.isNotEmpty()
    }
}
