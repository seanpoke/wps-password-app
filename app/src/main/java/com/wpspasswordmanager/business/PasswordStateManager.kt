package com.wpspasswordmanager.business

import android.util.Log
import java.util.concurrent.ConcurrentHashMap

object PasswordStateManager {
    // 使用 ConcurrentHashMap 保证线程安全
    // Key: 文件路径, Value: 文件加密状态
    private val stateMap = ConcurrentHashMap<String, FileCryptoState>()

    /**
     * 文件打开时初始化
     */
    fun initFileState(filePath: String, oldPass: String?) {
        val state = FileCryptoState(
            filePath = filePath,
            currentPassword = oldPass,
        )
        stateMap[filePath] = state
        // 输出日志
        Log.d("PasswordStateManager", "[时间戳: ${System.currentTimeMillis()}] initFileState - FileCryptoState: filePath='$filePath', currentPassword='${oldPass ?: "null"}', pendingPassword='${state.pendingPassword ?: "null"}'")
    }

    /**
     * 无障碍服务捕获到输入时更新
     */
    fun updatePendingPassword(filePath: String, newPassword: String) {
        val state = stateMap[filePath]
        if (state != null) {
            state.pendingPassword = newPassword
            // 输出日志
            Log.d("PasswordStateManager", "[时间戳: ${System.currentTimeMillis()}] updatePendingPassword - FileCryptoState: filePath='$filePath', currentPassword='${state.currentPassword ?: "null"}', pendingPassword='$newPassword'")
        } else {
            // 输出日志
            Log.d("PasswordStateManager", "[时间戳: ${System.currentTimeMillis()}] updatePendingPassword - FileCryptoState not found for filePath: '$filePath'")
        }
    }

    /**
     * 获取状态 (用于保存时处理)
     */
    fun getState(filePath: String): FileCryptoState? {
        return stateMap[filePath]
    }

    /**
     * 清理资源 (文件关闭时调用)
     */
    fun clearState(filePath: String) {
        stateMap.remove(filePath)
    }
}