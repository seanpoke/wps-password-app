package com.wpspasswordmanager.network

import android.content.Context
import android.util.Log
import com.wpspasswordmanager.business.EccEncryptor
import com.wpspasswordmanager.storage.ConfigStorage
import com.wpspasswordmanager.storage.ServerConfig
import okhttp3.*
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.IOException

class NetworkManager private constructor(context: Context) {
    private val TAG = "NetworkManager"
    private lateinit var okHttpClient: OkHttpClient
    private val configStorage: ConfigStorage

    companion object {
        @Volatile
        private var instance: NetworkManager? = null

        fun getInstance(context: Context): NetworkManager {
            return instance ?: synchronized(this) {
                instance ?: NetworkManager(context).also { instance = it }
            }
        }

        fun resetInstance() {
            instance = null
        }
    }

    init {
        configStorage = ConfigStorage.getInstance(context)
        refreshHttpClient()
    }

    private fun refreshHttpClient() {
        val config = configStorage.getServerConfig()
        okHttpClient = if (config != null) {
            try {
                val httpUrl = SSLUtils.parseUserAddress("${config.ipAddress}:${config.port}")
                SSLUtils.getUnsafeOkHttpClient(httpUrl)
            } catch (e: IllegalArgumentException) {
                SSLUtils.getUnsafeOkHttpClient("http://default".toHttpUrlOrNull()!!)
            }
        } else {
            SSLUtils.getUnsafeOkHttpClient("http://default".toHttpUrlOrNull()!!)
        }
    }

    fun onServerConfigChanged() {
        refreshHttpClient()
    }

    private fun getBaseUrl(): String? {
        val config = configStorage.getServerConfig()
        return if (config != null) {
            var ipAddress = config.ipAddress
            if (!ipAddress.startsWith("http://") && !ipAddress.startsWith("https://")) {
                ipAddress = "http://$ipAddress"
            }
            "$ipAddress:${config.port}"
        } else {
            null
        }
    }

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

    fun executeGetRequest(path: String, token: String? = null, callback: NetworkCallback) {
        val url = buildUrl(path)
        if (url == null) {
            callback.onError("Invalid URL")
            return
        }

        val requestBuilder = Request.Builder()
            .url(url)
            .get()

        token?.let {
            requestBuilder.addHeader("token", it)
        }

        val request = requestBuilder.build()

        okHttpClient.newCall(request).enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) {
                callback.onError(e.message ?: "Network error")
                callback.onComplete()
            }

            override fun onResponse(call: Call, response: Response) {
                if (response.isSuccessful) {
                    callback.onSuccess(response.body?.string() ?: "")
                } else {
                    callback.onError("HTTP ${response.code}: ${response.message}")
                }
                callback.onComplete()
            }
        })
    }

    fun executePostRequest(path: String, jsonBody: String, token: String? = null, callback: NetworkCallback) {
        Log.d(TAG, "执行POST请求: path=$path")
        val url = buildUrl(path)
        if (url == null) {
            Log.e(TAG, "构建URL失败")
            callback.onError("Invalid URL")
            return
        }
        Log.d(TAG, "请求URL: $url")

        val mediaType = "application/json".toMediaType()
        val requestBody = jsonBody.toRequestBody(mediaType)

        val requestBuilder = Request.Builder()
            .url(url)
            .post(requestBody)
            .addHeader("Content-Type", "application/json")

        token?.let {
            requestBuilder.addHeader("token", it)
        }

        val request = requestBuilder.build()
        Log.d(TAG, "请求准备完成，开始发送")

        okHttpClient.newCall(request).enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) {
                Log.e(TAG, "请求失败: ${e.message}")
                callback.onError(e.message ?: "Network error")
                callback.onComplete()
            }

            override fun onResponse(call: Call, response: Response) {
                Log.d(TAG, "请求响应: code=${response.code}, message=${response.message}")
                if (response.isSuccessful) {
                    val responseBody = response.body?.string() ?: ""
                    Log.d(TAG, "响应成功: $responseBody")
                    callback.onSuccess(responseBody)
                } else {
                    val errorMessage = "HTTP ${response.code}: ${response.message}"
                    Log.e(TAG, "响应失败: $errorMessage")
                    if (response.code == 401) {
                        callback.onError("401: Unauthorized")
                    } else {
                        callback.onError(errorMessage)
                    }
                }
                callback.onComplete()
            }
        })
    }

    fun login(account: String, password: String, callback: NetworkCallback) {
        Log.d(TAG, "执行登录请求: account=$account")
        val jsonBody = "{\"account\": \"$account\", \"password\": \"$password\"}"
        Log.d(TAG, "登录请求体: $jsonBody")
        executePostRequest("/account/login", jsonBody, null, callback)
    }

    fun refreshToken(token: String, callback: NetworkCallback) {
        executePostRequest("/account/refresh-token", "{}", token, callback)
    }

    fun logout(token: String, callback: NetworkCallback) {
        Log.d(TAG, "执行登出请求")
        executePostRequest("/account/logout", "{}", token, callback)
    }

    fun getDocumentOwner(docId: String, token: String?, fileName: String? = null, callback: NetworkCallback) {
        Log.d(TAG, "执行获取文档权限请求: docId=$docId, fileName=$fileName")
        val jsonBody = buildString {
            append("{")
            append("\"docId\": \"$docId\"")
            fileName?.let { append(", \"fileName\": \"$it\"") }
            append("}")
        }
        Log.d(TAG, "获取文档权限请求体: $jsonBody")
        executePostRequest("/doc/owner", jsonBody, token, callback)
    }

    fun getDocumentPassword(docId: String, encryPassword: String, token: String?, keyVersion: String = "default", isTemp: Boolean = false, callback: NetworkCallback) {
        Log.d(TAG, "执行获取文档密码请求: docId=$docId, keyVersion=$keyVersion, isTemp=$isTemp")
        val jsonBody = "{\"docId\": \"$docId\", \"encryPassword\": \"$encryPassword\", \"keyVersion\": \"$keyVersion\", \"isTemp\": $isTemp}"
        Log.d(TAG, "获取文档密码请求体: $jsonBody")
        executePostRequest("/doc/password", jsonBody, token, callback)
    }

    fun getLatestKey(callback: NetworkCallback) {
        Log.d(TAG, "执行获取最新密钥信息请求")
        executeGetRequest("/config/latest-key", null, callback)
    }

    fun executeApiRequest(method: String, path: String, body: String? = null, token: String? = null, callback: NetworkCallback) {
        when (method.toUpperCase()) {
            "GET" -> executeGetRequest(path, token, callback)
            "POST" -> if (body != null) executePostRequest(path, body, token, callback) else callback.onError("Body required for POST")
            else -> callback.onError("Unsupported method")
        }
    }

    fun reportSaveLog(docId: String, path: String, beforePassword: String? = null, afterPassword: String? = null, possiblePassword: List<String>? = null, platform: String = "android", token: String? = null, callback: NetworkCallback) {
        Log.d(TAG, "执行保存记录上报请求: docId=$docId, path=$path, platform=$platform")

        val keyVersion = configStorage.getKeyVersion()

        val encryptedBeforePassword = beforePassword?.let {
            EccEncryptor.encryptPassword(it)
        }
        val encryptedAfterPassword = afterPassword?.let {
            EccEncryptor.encryptPassword(it)
        }
        val encryptedPossiblePassword = possiblePassword?.mapNotNull {
            EccEncryptor.encryptPassword(it)
        }

        val jsonBody = buildString {
            append("{")
            append("\"docId\": \"$docId\",")
            append("\"path\": \"$path\",")
            append("\"keyVersion\": \"$keyVersion\",")
            append("\"platform\": \"$platform\"")
            encryptedBeforePassword?.let { append(", \"beforePassword\": \"$it\"") }
            encryptedAfterPassword?.let { append(", \"afterPassword\": \"$it\"") }
            encryptedPossiblePassword?.let { passwords ->
                if (passwords.isNotEmpty()) {
                    append(", \"possiblePassword\": [")
                    passwords.forEachIndexed { index, password ->
                        if (index > 0) append(", ")
                        append("\"$password\"")
                    }
                    append("]")
                }
            }
            append("}")
        }
        Log.d(TAG, "保存记录上报请求体: $jsonBody")
        executePostRequest("/doc/save/log", jsonBody, token, callback)
    }
}

interface NetworkCallback {
    fun onSuccess(response: String)
    fun onError(error: String)
    fun onComplete() {}
}