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
        Log.d("PasswordStateManager", "[时间戳: ${System.currentTimeMillis()}] initFileState - FileCryptoState: filePath='$filePath', currentPassword='${oldPass ?: "null"}', pendingPasswordList='${state.pendingPasswordList?.toList() ?: "null"}'")
    }

    /**
     * 无障碍服务捕获到输入时更新
     */
    fun updatePendingPassword(filePath: String, newPassword: String) {
        var state = stateMap[filePath]
        if (state != null) {
            if (state.pendingPasswordList == null) {
                state.pendingPasswordList = OrderedSet()
            }
            state.pendingPasswordList?.add(newPassword)
            // 输出日志
            Log.d("PasswordStateManager", "[时间戳: ${System.currentTimeMillis()}] updatePendingPassword - FileCryptoState: filePath='$filePath', currentPassword='${state.currentPassword ?: "null"}', pendingPasswordList='${state.pendingPasswordList?.toList()}'")
        } else {
            // 如果状态不存在，自动初始化一个新的状态
            val pendingPasswordSet = OrderedSet<String>()
            pendingPasswordSet.add(newPassword)
            state = FileCryptoState(
                filePath = filePath,
                currentPassword = null,
                pendingPasswordList = pendingPasswordSet
            )
            stateMap[filePath] = state
            // 输出日志
            Log.d("PasswordStateManager", "[时间戳: ${System.currentTimeMillis()}] updatePendingPassword - FileCryptoState not found, created new state: filePath='$filePath', pendingPasswordList='${state.pendingPasswordList?.toList()}'")
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