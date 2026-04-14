package com.wpspasswordmanager.network

import android.content.Context
import com.wpspasswordmanager.storage.ConfigStorage
import com.wpspasswordmanager.storage.ServerConfig
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.IOException

class NetworkManager private constructor(context: Context) {
    private val okHttpClient = OkHttpClient()
    private val configStorage = ConfigStorage.getInstance(context)

    companion object {
        @Volatile
        private var instance: NetworkManager? = null

        fun getInstance(context: Context): NetworkManager {
            return instance ?: synchronized(this) {
                instance ?: NetworkManager(context).also { instance = it }
            }
        }
    }

    // 获取基础URL
    private fun getBaseUrl(): String? {
        val config = configStorage.getServerConfig()
        return if (config != null) {
            var ipAddress = config.ipAddress
            // 确保IP地址包含协议前缀
            if (!ipAddress.startsWith("http://") && !ipAddress.startsWith("https://")) {
                ipAddress = "http://$ipAddress"
            }
            "$ipAddress:${config.port}"
        } else {
            null
        }
    }

    // 构建完整URL
    private fun buildUrl(path: String): String? {
        val baseUrl = getBaseUrl()
        return if (baseUrl != null) {
            if (path.startsWith("/")) {
                "$baseUrl$path"
            } else {
                "$baseUrl/$path"
            }
        } else {
            null
        }
    }

    // 执行GET请求
    fun executeGetRequest(path: String, token: String? = null): String? {
        val url = buildUrl(path) ?: return null

        val requestBuilder = Request.Builder()
            .url(url)
            .get()

        // 添加认证token
        token?.let {
            requestBuilder.addHeader("Authorization", "Bearer $it")
        }

        val request = requestBuilder.build()

        try {
            val response = okHttpClient.newCall(request).execute()
            if (response.isSuccessful) {
                return response.body?.string()
            }
        } catch (e: IOException) {
            e.printStackTrace()
        }

        return null
    }

    // 执行POST请求
    fun executePostRequest(path: String, jsonBody: String, token: String? = null): String? {
        val url = buildUrl(path) ?: return null

        val mediaType = "application/json".toMediaType()
        val requestBody = jsonBody.toRequestBody(mediaType)

        val requestBuilder = Request.Builder()
            .url(url)
            .post(requestBody)
            .addHeader("Content-Type", "application/json")

        // 添加认证token
        token?.let {
            requestBuilder.addHeader("Authorization", "Bearer $it")
        }

        val request = requestBuilder.build()

        try {
            val response = okHttpClient.newCall(request).execute()
            if (response.isSuccessful) {
                return response.body?.string()
            }
        } catch (e: IOException) {
            e.printStackTrace()
        }

        return null
    }

    // 执行登录请求
    fun login(username: String, password: String): String? {
        val jsonBody = "{\"username\": \"$username\", \"password\": \"$password\"}"
        return executePostRequest("/api/login", jsonBody)
    }

    // 执行其他API请求
    fun executeApiRequest(method: String, path: String, body: String? = null, token: String? = null): String? {
        return when (method.toUpperCase()) {
            "GET" -> executeGetRequest(path, token)
            "POST" -> if (body != null) executePostRequest(path, body, token) else null
            else -> null
        }
    }
}