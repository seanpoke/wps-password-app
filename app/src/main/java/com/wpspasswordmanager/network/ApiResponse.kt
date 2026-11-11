package com.wpspasswordmanager.network

import com.wpspasswordmanager.storage.UserInfo

// 登录响应数据类
data class LoginResponse(
    val message: String,
    val status: Int,
    val data: UserInfo
)
