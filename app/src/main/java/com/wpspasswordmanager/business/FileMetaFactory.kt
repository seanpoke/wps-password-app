package com.wpspasswordmanager.business

import android.util.Log
import java.io.File
import java.util.concurrent.ConcurrentHashMap

object FileMetaFactory {
    // 使用 ConcurrentHashMap 保证线程安全
    // Key: 文件路径, Value: 文件加密状态
    private val map = ConcurrentHashMap<String, FileMeta>()

    private const val TAG = "FileMetaFactory"

    /**
     * 文件打开时初始化
     */
    fun initFileState(filePath: String, oldPass: String?) {
        val fileMeta = FileMeta(
            filePath = filePath,
            uid = null,
            currentPassword = oldPass,
        )
        map[filePath] = fileMeta
        // 输出日志
        Log.d(
            TAG,
            "[时间戳: ${System.currentTimeMillis()}] initFileState - FileCryptoState: filePath='$filePath', currentPassword='${oldPass ?: "null"}', pendingPasswordList='${fileMeta.pendingPasswordList?.toList() ?: "null"}'"
        )
    }

    /**
     * 无障碍服务捕获到输入时更新
     */
    fun updatePendingPassword(filePath: String, newPassword: String) {
        Log.d(
            TAG,
            "updatePendingPassword密码入参 '$filePath'"
        )
        var fileMeta = map[filePath]
        if (fileMeta != null) {
            if (fileMeta.pendingPasswordList == null) {
                fileMeta.pendingPasswordList = OrderedSet()
            }
            fileMeta.pendingPasswordList?.add(newPassword)
            // 输出日志
            Log.d(
                TAG,
                "[时间戳: ${System.currentTimeMillis()}] updatePendingPassword - FileCryptoState: filePath='$filePath', currentPassword='${fileMeta.currentPassword ?: "null"}', pendingPasswordList='${fileMeta.pendingPasswordList?.toList()}'"
            )
        } else {
            // 如果状态不存在，自动初始化一个新的状态
            val pendingPasswordSet = OrderedSet<String>()
            pendingPasswordSet.add(newPassword)
            fileMeta = FileMeta(
                filePath = filePath,
                uid = null,
                currentPassword = null,
                pendingPasswordList = pendingPasswordSet
            )
            map[filePath] = fileMeta
            // 输出日志
            Log.d(
                TAG,
                "[时间戳: ${System.currentTimeMillis()}] updatePendingPassword - FileCryptoState not found, created new state: filePath='$filePath', pendingPasswordList='${fileMeta.pendingPasswordList?.toList()}'"
            )
        }
    }

    /**
     * 清理资源 (文件关闭时调用)
     */
    fun clearFile(filePath: String) {
        Log.d(
            TAG,
            "clearFile密码入参入参 '$filePath'"
        )
        map.remove(filePath)
    }

    /**
     * 清理资源 (文件关闭时调用)
     */
    fun getUid(filePath: String):String? {
        var fileMeta = map[filePath]
        if (fileMeta != null) {
            return fileMeta.uid
        }
        return null;
    }

    /**
     * 获取有效的密码
     */
    fun getWritePassword(filePath: String): String? {
        Log.d(
            TAG,
            "getWritePassword密码入参入参 '$filePath'"
        )
        val fileMeta = map[filePath]
        val pendingPasswords = fileMeta?.pendingPasswordList
        val currentPassword = fileMeta?.currentPassword

        // 如果pendPasswordList为空，返回currentPassword
        if (pendingPasswords == null || pendingPasswords.isEmpty()) {
            Log.d(
                TAG,
                "[时间戳: ${System.currentTimeMillis()}] 无待定密码，使用当前密码执行写入操作"
            )
            return currentPassword
        }

        // pendPasswordList不为空，遍历尝试打开文件
        if (!filePath.startsWith("content://")) {
            val file = File(filePath)
            if (file.exists() && file.canRead()) {
                for (pendingPassword in pendingPasswords) {
                    Log.d(
                        TAG,
                        "[时间戳: ${System.currentTimeMillis()}] 验证待定密码是否可以打开文件: '$pendingPassword'"
                    )
                    val isPasswordValid = OfficeEncryptUtils.verifyPassword(file, pendingPassword)
                    if (isPasswordValid) {
                        Log.d(
                            TAG,
                            "[时间戳: ${System.currentTimeMillis()}] 待定密码验证成功，使用该密码"
                        )
                        return pendingPassword
                    } else {
                        Log.e(
                            TAG,
                            "[时间戳: ${System.currentTimeMillis()}] 待定密码验证失败: '$pendingPassword'"
                        )
                    }
                }
            } else {
                Log.w(
                    TAG,
                    "[时间戳: ${System.currentTimeMillis()}] 文件不存在或不可读，无法验证密码"
                )
            }
        } else {
            // 对于Content URI，使用第一个待定密码
            Log.d(
                TAG,
                "[时间戳: ${System.currentTimeMillis()}] Content URI，使用第一个待定密码"
            )
            return pendingPasswords.firstOrNull()
        }

        // 所有密码都无效，返回currentPassword
        Log.e(
            TAG,
            "[时间戳: ${System.currentTimeMillis()}] 所有待定密码验证失败，使用当前密码"
        )
        return currentPassword
    }

    /**
     * 获取文件的当前密码
     */
    fun getCurrentPassword(filePath: String): String? {
        val fileMeta = map[filePath]
        return fileMeta?.currentPassword
    }
}