package com.wpspasswordmanager.business

import android.graphics.drawable.Drawable

data class WpsAppInfo(
    val label: String,
    val packageName: String,
    val versionName: String,
    val versionCode: Int,
    val icon: Drawable,
    val installPath: String
)