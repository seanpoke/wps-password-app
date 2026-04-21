package com.wpspasswordmanager.network

import android.app.Service
import android.content.Intent
import android.os.Binder
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import com.wpspasswordmanager.storage.ConfigStorage
import java.util.*

class HeartbeatService : Service() {
    private val binder = LocalBinder()
    private val handler = Handler(Looper.getMainLooper())
    private val heartbeatInterval = 30 * 60 * 1000L // 30分钟
    private var heartbeatRunnable: Runnable? = null
    private lateinit var networkManager: NetworkManager
    private lateinit var configStorage: ConfigStorage

    inner class LocalBinder : Binder() {
        fun getService(): HeartbeatService = this@HeartbeatService
    }

    override fun onCreate() {
        super.onCreate()
        networkManager = NetworkManager.getInstance(this)
        configStorage = ConfigStorage.getInstance(this)
    }

    override fun onBind(intent: Intent): IBinder {
        return binder
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        startHeartbeat()
        return START_STICKY
    }

    override fun onDestroy() {
        stopHeartbeat()
        super.onDestroy()
    }

    fun startHeartbeat() {
        stopHeartbeat() // 确保不会重复启动

        heartbeatRunnable = Runnable {
            sendHeartbeat()
            handler.postDelayed(heartbeatRunnable!!, heartbeatInterval)
        }

        handler.post(heartbeatRunnable!!)
    }

    fun stopHeartbeat() {
        heartbeatRunnable?.let {
            handler.removeCallbacks(it)
        }
        heartbeatRunnable = null
    }

    private fun sendHeartbeat() {
        val userInfo = configStorage.getUserInfo()
        if (userInfo != null) {
            // 调用后端的心跳接口（异步）
            networkManager.refreshToken(userInfo.token, object : NetworkCallback {
                override fun onSuccess(response: String) {
                    try {
                        val gson = com.google.gson.Gson()
                        val loginResponse = gson.fromJson(response, LoginResponse::class.java)

                        if (loginResponse.status == 200) {
                            // 保存更新后的用户信息
                            configStorage.saveUserInfo(loginResponse.data)
                            println("[Heartbeat] Success at ${Date()}")
                        } else {
                            println("[Heartbeat] Failed: ${loginResponse.message}")
                            // 停止心跳服务
                            stopHeartbeat()
                        }
                    } catch (e: Exception) {
                        println("[Heartbeat] Error: ${e.message}")
                        // 停止心跳服务
                        stopHeartbeat()
                    }
                }

                override fun onError(error: String) {
                    println("[Heartbeat] Network error: $error")
                    // 清理用户信息
                    configStorage.clearUserInfo()
                    // 停止心跳服务
                    stopHeartbeat()
                    // 发送广播通知MainActivity更新UI
                    val intent = Intent("com.wpspasswordmanager.ACTION_SESSION_EXPIRED")
                    sendBroadcast(intent)
                }
            })
        }
    }
}