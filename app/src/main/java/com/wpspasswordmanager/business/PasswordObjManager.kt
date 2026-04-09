package com.wpspasswordmanager.business

import android.util.Log
import com.wpspasswordmanager.monitor.FileSystemEventListener
import java.io.File
import java.util.concurrent.ConcurrentHashMap

object PasswordObjManager {
    // 使用 ConcurrentHashMap 保证线程安全
    // Key: 文件路径, Value: 文件加密状态
    private val stateMap = ConcurrentHashMap<String, FileCryptoState>()

    private const val TAG = "PasswordObjManager"

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
     * 获取原始密码
     */
    fun getCurrentPassword(filePath: String): String? {
        var state = stateMap[filePath]
        if (state != null) {
            return state.currentPassword}
        return null;
    }

    /**
     * 清理资源 (文件关闭时调用)
     */
    fun clearState(filePath: String) {
        stateMap.remove(filePath)
    }


    /**
     * 获取有效的报错密码
     */
    fun getWritePassword(filePath: String): String? {
        // 获取密码状态
        val pendingPasswordList = getState(filePath)
        var passwordToUse: String? = null

        // 存储到局部变量以避免smart cast问题
        val pendingPasswords = pendingPasswordList?.pendingPasswordList
        val currentPassword = pendingPasswordList?.currentPassword

        // 按照逻辑处理密码选择
        val hasPendingPassword = pendingPasswords != null && !pendingPasswords.isEmpty()
        val hasCurrentPassword = currentPassword != null && currentPassword.isNotEmpty()

        when {
            // 情况1：两者都不存在
            !hasPendingPassword && !hasCurrentPassword -> {
                Log.d(
                    TAG,
                    "[时间戳: ${System.currentTimeMillis()}] 无待定密码和当前密码，跳过密码写入操作"
                )
            }

            // 情况2：只有currentPassword存在
            !hasPendingPassword && hasCurrentPassword -> {
                Log.d(
                   TAG,
                    "[时间戳: ${System.currentTimeMillis()}] 无待定密码，使用当前密码执行写入操作"
                )
                passwordToUse = currentPassword
            }

            // 情况3：只有pendingPassword存在
            hasPendingPassword && !hasCurrentPassword -> {
                Log.d(TAG, "[时间戳: ${System.currentTimeMillis()}] 只有待定密码，需要校验权限")

                // 校验pendingPassword是否具备文件打开权限
                if (!filePath.startsWith("content://")) {
                    val file = File(filePath)
                    if (file.exists() && file.canRead() && pendingPasswords != null) {
                        var foundValidPassword = false

                        // 遍历所有待定密码
                        for (pendingPassword in pendingPasswords) {
                            Log.d(
                               TAG,
                                "[时间戳: ${System.currentTimeMillis()}] 验证待定密码是否可以打开文件: '$pendingPassword'"
                            )
                            val isPasswordValid =
                                OfficeEncryptUtils.verifyPassword(file, pendingPassword)
                            if (isPasswordValid) {
                                Log.d(
                                   TAG,
                                    "[时间戳: ${System.currentTimeMillis()}] 待定密码验证成功，可以打开文件"
                                )
                                passwordToUse = pendingPassword
                                foundValidPassword = true
                                break // 找到有效密码后停止遍历
                            } else {
                                Log.e(
                                   TAG,
                                    "[时间戳: ${System.currentTimeMillis()}] 待定密码验证失败: '$pendingPassword'"
                                )
                            }
                        }

                        // 若全部校验失败，跳过密码写入操作
                        if (!foundValidPassword) {
                            Log.e(
                               TAG,
                                "[时间戳: ${System.currentTimeMillis()}] 所有待定密码验证失败，无法打开文件，跳过密码写入"
                            )
                        }
                    } else {
                        Log.w(
                           TAG,
                            "[时间戳: ${System.currentTimeMillis()}] 文件不存在或不可读，无法验证密码"
                        )
                    }
                } else if (pendingPasswords != null) {
                    // 对于Content URI，使用第一个待定密码
                    Log.d(
                       TAG,
                        "[时间戳: ${System.currentTimeMillis()}] Content URI，使用第一个待定密码"
                    )
                    passwordToUse = pendingPasswords.firstOrNull()
                }
            }

            // 情况4：两者都存在
            hasPendingPassword && hasCurrentPassword -> {
                Log.d(
                   TAG,
                    "[时间戳: ${System.currentTimeMillis()}] 待定密码和当前密码都存在，优先校验待定密码"
                )

                // 校验pendingPassword是否具备文件打开权限
                if (!filePath.startsWith("content://")) {
                    val file = File(filePath)
                    if (file.exists() && file.canRead()) {
                        var foundValidPassword = false

                        // 遍历所有待定密码
                        if (pendingPasswords != null) {
                            for (pendingPassword in pendingPasswords) {
                                Log.d(
                                   TAG,
                                    "[时间戳: ${System.currentTimeMillis()}] 验证待定密码是否可以打开文件: '$pendingPassword'"
                                )
                                val isPasswordValid =
                                    OfficeEncryptUtils.verifyPassword(file, pendingPassword)
                                if (isPasswordValid) {
                                    Log.d(
                                       TAG,
                                        "[时间戳: ${System.currentTimeMillis()}] 待定密码验证成功，使用待定密码"
                                    )
                                    passwordToUse = pendingPassword
                                    foundValidPassword = true
                                    break // 找到有效密码后停止遍历
                                } else {
                                    Log.e(
                                       TAG,
                                        "[时间戳: ${System.currentTimeMillis()}] 待定密码验证失败: '$pendingPassword'"
                                    )
                                }
                            }
                        }

                        // 若全部校验失败，使用currentPassword
                        if (!foundValidPassword) {
                            Log.e(
                               TAG,
                                "[时间戳: ${System.currentTimeMillis()}] 所有待定密码验证失败，使用当前密码"
                            )
                            passwordToUse = currentPassword
                        }
                    } else {
                        Log.w(
                           TAG,
                            "[时间戳: ${System.currentTimeMillis()}] 文件不存在或不可读，无法验证密码，使用当前密码"
                        )
                        passwordToUse = currentPassword
                    }
                } else {
                    // 对于Content URI，优先使用待定密码
                    Log.d(
                       TAG,
                        "[时间戳: ${System.currentTimeMillis()}] Content URI，优先使用待定密码"
                    )
                    passwordToUse = pendingPasswords?.firstOrNull()
                }
            }
        }
        return passwordToUse
    }
}