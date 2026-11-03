package com.wpspasswordmanager.ui.compose.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.wpspasswordmanager.ui.compose.C
import com.wpspasswordmanager.ui.compose.Dot
import com.wpspasswordmanager.ui.compose.I
import com.wpspasswordmanager.ui.compose.SvgIcon
import com.wpspasswordmanager.ui.compose.T
import com.wpspasswordmanager.ui.compose.TokenState
import com.wpspasswordmanager.ui.compose.UiState

/** 屏 01 · 启动闸门 */
@Composable
fun BootScreen(s: UiState) {
    Box(
        Modifier
            .fillMaxSize()
            .background(Brush.linearGradient(listOf(Color(0xFF8A3CFF), C.Primary, Color(0xFF4A00B0))))
    ) {
        Column(
            Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .navigationBarsPadding()
                .padding(horizontal = 30.dp)
        ) {
            Spacer(Modifier.height(120.dp))

            Box(
                Modifier
                    .size(74.dp)
                    .clip(RoundedCornerShape(24.dp))
                    .background(Color.White.copy(alpha = 0.16f))
                    .border(1.dp, Color.White.copy(alpha = 0.22f), RoundedCornerShape(24.dp)),
                contentAlignment = Alignment.Center
            ) {
                SvgIcon(I.shield, size = 36.dp, tint = Color.White, strokeWidth = 1.7f)
            }

            Spacer(Modifier.height(20.dp))
            Text("文档密码管家", style = T.H1.copy(fontSize = 23.sp, color = Color.White, letterSpacing = 0.4.sp))
            Spacer(Modifier.height(8.dp))
            Text(
                "企业文档 · 权限与密码统一管理",
                style = T.Small.copy(fontSize = 12.5.sp, color = Color.White.copy(alpha = 0.78f))
            )

            Spacer(Modifier.weight(1f))

            Column(
                Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(16.dp))
                    .background(Color.White.copy(alpha = 0.12f))
                    .border(1.dp, Color.White.copy(alpha = 0.16f), RoundedCornerShape(16.dp))
                    .padding(14.dp)
            ) {
                Text(
                    "正在准备运行环境",
                    style = T.Tiny.copy(color = Color.White.copy(alpha = 0.72f), fontWeight = FontWeight.Medium)
                )
                Spacer(Modifier.height(10.dp))
                BootRow("本地配置", if (s.bootLocalOk) "已就绪" else "未配置", s.bootLocalOk, false)
                Spacer(Modifier.height(9.dp))
                BootRow(
                    "会话令牌",
                    when (s.bootToken) {
                        TokenState.Checking -> "校验中…"
                        TokenState.Ok -> "已登录"
                        TokenState.None -> "未登录"
                        TokenState.Expired -> "已失效"
                    },
                    s.bootToken == TokenState.Ok,
                    s.bootToken == TokenState.Checking
                )
                Spacer(Modifier.height(9.dp))
                val permMissing = listOf(s.bootPerm.accessibility, s.bootPerm.overlay, s.bootPerm.storage, s.bootPerm.wpsPkg != null)
                    .count { !it }
                BootRow(
                    "设备权限",
                    if (permMissing == 0) "全部就绪" else "还差 $permMissing 项",
                    permMissing == 0,
                    false
                )
            }

            Spacer(Modifier.height(14.dp))
            Text(
                "v" + androidx.compose.ui.platform.LocalContext.current.packageManager
                    .getPackageInfo(androidx.compose.ui.platform.LocalContext.current.packageName, 0)
                    .versionName,
                style = T.Micro.copy(color = Color.White.copy(alpha = 0.55f)),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 18.dp),
                textAlign = androidx.compose.ui.text.style.TextAlign.Center
            )
        }
    }
}

@Composable
private fun BootRow(label: String, status: String, ok: Boolean, checking: Boolean) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        if (checking) {
            CircularProgressIndicator(
                modifier = Modifier.size(11.dp),
                strokeWidth = 1.6.dp,
                color = Color.White.copy(alpha = 0.85f)
            )
        } else {
            Dot(
                color = when {
                    ok -> Color(0xFF8BE79B)
                    else -> Color(0xFFFFD08A)
                },
                size = 7.dp
            )
        }
        Spacer(Modifier.width(8.dp))
        Text(label, style = T.Small.copy(fontSize = 12.sp, color = Color.White.copy(alpha = 0.94f)))
        Spacer(Modifier.weight(1f))
        Text(
            status,
            style = T.Micro.copy(
                fontSize = 11.sp,
                color = if (ok) Color.White.copy(alpha = 0.8f) else Color(0xFFFFE0B2)
            )
        )
    }
}
