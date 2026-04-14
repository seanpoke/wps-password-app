package com.wpspasswordmanager.storage

import android.content.Context
import android.content.SharedPreferences
import com.google.gson.Gson

class ConfigStorage private constructor(context: Context) {
    private val sharedPreferences: SharedPreferences
    private val gson = Gson()

    companion object {
        private const val PREF_NAME = "config_storage"
        private const val KEY_CONFIG = "server_config"
        private const val KEY_USER_INFO = "user_info"
        private const val KEY_REMEMBER_PASSWORD = "remember_password"
        private const val KEY_PASSWORD = "password"

        @Volatile
        private var instance: ConfigStorage? = null

        fun getInstance(context: Context): ConfigStorage {
            return instance ?: synchronized(this) {
                instance ?: ConfigStorage(context).also { instance = it }
            }
        }
    }

    init {
        sharedPreferences = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
    }

    // 存储服务器配置
    fun saveServerConfig(config: ServerConfig) {
        val editor = sharedPreferences.edit()
        editor.putString(KEY_CONFIG, gson.toJson(config))
        editor.apply()
    }

    // 获取服务器配置
    fun getServerConfig(): ServerConfig? {
        val configJson = sharedPreferences.getString(KEY_CONFIG, null)
        return if (configJson != null) {
            gson.fromJson(configJson, ServerConfig::class.java)
        } else {
            null
        }
    }

    // 存储用户信息
    fun saveUserInfo(userInfo: UserInfo) {
        val editor = sharedPreferences.edit()
        editor.putString(KEY_USER_INFO, gson.toJson(userInfo))
        editor.apply()
    }

    // 获取用户信息
    fun getUserInfo(): UserInfo? {
        val userInfoJson = sharedPreferences.getString(KEY_USER_INFO, null)
        return if (userInfoJson != null) {
            gson.fromJson(userInfoJson, UserInfo::class.java)
        } else {
            null
        }
    }

    // 存储密码
    fun savePassword(password: String) {
        val editor = sharedPreferences.edit()
        editor.putString(KEY_PASSWORD, password)
        editor.apply()
    }

    // 获取密码
    fun getPassword(): String? {
        return sharedPreferences.getString(KEY_PASSWORD, null)
    }

    // 存储记住密码状态
    fun saveRememberPassword(remember: Boolean) {
        val editor = sharedPreferences.edit()
        editor.putBoolean(KEY_REMEMBER_PASSWORD, remember)
        editor.apply()
    }

    // 获取记住密码状态
    fun getRememberPassword(): Boolean {
        return sharedPreferences.getBoolean(KEY_REMEMBER_PASSWORD, false)
    }

    // 清除所有缓存
    fun clearAll() {
        val editor = sharedPreferences.edit()
        editor.clear()
        editor.apply()
    }

    // 清除密码缓存
    fun clearPassword() {
        val editor = sharedPreferences.edit()
        editor.remove(KEY_PASSWORD)
        editor.apply()
    }
}

// 服务器配置数据类
data class ServerConfig(
    val ipAddress: String,
    val port: String,
    val username: String
)

// 用户信息数据类
data class UserInfo(
    val userId: String,
    val username: String,
    val token: String
)