package com.wpspasswordmanager.ui.compose.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.wpspasswordmanager.ui.compose.AppButton
import com.wpspasswordmanager.ui.compose.AppViewModel
import com.wpspasswordmanager.ui.compose.BtnStyle
import com.wpspasswordmanager.ui.compose.C
import com.wpspasswordmanager.ui.compose.T
import com.wpspasswordmanager.ui.compose.assistant.AssistantOverlay
import com.wpspasswordmanager.ui.compose.assistant.AssistantUiState

/**
 * 悬浮助手的走查 / 演示页（仅用于 `--es scene assistant`）。
 * 真实场景里浮层挂在 WindowManager 上盖在 WPS 之上，这里用一张模拟文档页做背景，
 * 便于逐态截图核对；顶部三个按钮切换 气泡 / 未填充 / 已填充。
 */
@Composable
fun AssistantPreviewScreen(vm: AppViewModel) {
    var state by remember {
        mutableStateOf(
            AssistantUiState(
                open = true,
                docName = "2026 年 Q4 经营分析报告.docx",
                encrypted = false,
                canPerm = true
            )
        )
    }

    Box(Modifier.fillMaxSize().background(Color(0xFF2B2B33))) {
        // 模拟 WPS 文档页
        Column(
            Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .padding(horizontal = 10.dp, vertical = 6.dp)
                .clip(RoundedCornerShape(6.dp))
                .background(Color.White)
        ) {
            Spacer(Modifier.height(38.dp))
            Row(
                Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("‹", style = T.Body.copy(fontSize = 17.sp, color = Color(0xFF3A3A44)))
                Spacer(Modifier.width(8.dp))
                Text(
                    "2026 年 Q4 经营分析报告.docx",
                    style = T.Small.copy(fontSize = 12.sp, fontWeight = FontWeight.SemiBold, color = Color(0xFF3A3A44)),
                    maxLines = 1,
                    modifier = Modifier.weight(1f)
                )
                Text("已保存", style = T.Micro.copy(fontSize = 11.sp, color = Color(0xFF9A9AA4)))
            }
            Box(Modifier.fillMaxWidth().height(1.dp).background(Color(0xFFEFEFF3)))
            Column(Modifier.padding(horizontal = 20.dp, vertical = 18.dp)) {
                Text(
                    "2026 年 Q4 经营分析报告",
                    style = T.Body.copy(fontSize = 15.sp, fontWeight = FontWeight.Bold, color = Color(0xFF2A2A33))
                )
                Spacer(Modifier.height(14.dp))
                repeat(9) { i ->
                    Box(
                        Modifier
                            .fillMaxWidth(if (i % 3 == 0) 0.86f else 0.62f)
                            .height(9.dp)
                            .clip(RoundedCornerShape(4.dp))
                            .background(Color(0xFFEDEDF3))
                    )
                    Spacer(Modifier.height(9.dp))
                }
            }
        }

        // 演示控制条（真实浮层没有）
        Row(
            Modifier
                .align(Alignment.TopCenter)
                .statusBarsPadding()
                .padding(top = 2.dp),
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            AppButton(
                "气泡",
                onClick = { state = state.copy(open = false, filled = false) },
                style = BtnStyle.Ghost,
                height = 32.dp,
                fontSize = 11.sp
            )
            AppButton(
                "未填充",
                onClick = { state = state.copy(open = true, filled = false) },
                style = BtnStyle.Ghost,
                height = 32.dp,
                fontSize = 11.sp
            )
            AppButton(
                "已填入",
                onClick = {
                    state = state.copy(open = true, filled = true, password = "Kp7#vR2mQx9!aE4t")
                },
                style = BtnStyle.Ghost,
                height = 32.dp,
                fontSize = 11.sp
            )
        }

        Box(Modifier.align(Alignment.BottomEnd).padding(end = 8.dp, bottom = 24.dp)) {
            AssistantOverlay(
                state = state,
                onOpen = { state = state.copy(open = true) },
                onClose = { state = state.copy(open = false) },
                onFill = { state = state.copy(filled = true, password = "Kp7#vR2mQx9!aE4t") },
                onCopy = { vm.showToast("密码已复制到剪贴板") },
                onPerm = { vm.showToast("打开文档权限面板") },
                onDone = { state = state.copy(open = false, filled = false) },
                onDrag = { _, _ -> }
            )
        }
    }
}
