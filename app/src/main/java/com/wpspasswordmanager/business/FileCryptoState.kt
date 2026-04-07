package com.wpspasswordmanager.business

data class FileCryptoState(
    // --- 基础信息 ---
    val filePath: String,            // 文件绝对路径 (作为唯一标识)
    
    // --- 密码状态 ---
    var currentPassword: String?,    // 旧密码：当前已确认生效的密码
    var pendingPassword: String? = null, // 待定密码：无障碍服务捕获到的新密码
)