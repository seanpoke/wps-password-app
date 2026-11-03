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
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.wpspasswordmanager.ui.compose.AppBar
import com.wpspasswordmanager.ui.compose.AppViewModel
import com.wpspasswordmanager.ui.compose.C
import com.wpspasswordmanager.ui.compose.I
import com.wpspasswordmanager.ui.compose.SvgIcon
import com.wpspasswordmanager.ui.compose.T
import com.wpspasswordmanager.ui.compose.card
import com.wpspasswordmanager.ui.compose.pressable

private val LOGO_ICON = listOf(
    "M12 2.8 L19.2 5.8 L19.2 11.9 C19.2 16.5 16.1 19.6 12 21.2 C7.9 19.6 4.8 16.5 4.8 11.9 L4.8 5.8 Z",
    "circle:12,11,2.1",
    "M12 13.1 L12 15"
)

/** 屏 29 · 关于与更新 */
@Composable
fun AboutScreen(vm: AppViewModel) {
    val version = vm.currentVersion()
    val build = vm.currentBuild()

    Column(
        Modifier
            .fillMaxSize()
            .background(C.Bg)
            .statusBarsPadding()
    ) {
        Box(Modifier.fillMaxWidth().background(Color.White)) {
            AppBar("关于", onBack = vm::closeAbout)
        }
        Box(Modifier.fillMaxWidth().height(1.dp).background(Color(0xFFEFEFF3)))

        Column(
            Modifier
                .weight(1f)
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .navigationBarsPadding()
        ) {
            Column(Modifier.fillMaxWidth().padding(top = 34.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                Box(
                    Modifier
                        .size(66.dp)
                        .shadow(18.dp, RoundedCornerShape(20.dp), clip = false)
                        .clip(RoundedCornerShape(20.dp))
                        .background(Brush.linearGradient(listOf(Color(0xFF8B4BFF), C.Primary))),
                    contentAlignment = Alignment.Center
                ) {
                    SvgIcon(LOGO_ICON, size = 32.dp, tint = Color.White, strokeWidth = 1.6f)
                }
                Spacer(Modifier.height(16.dp))
                Text("文档密码管家", style = T.Body.copy(fontSize = 17.sp, fontWeight = FontWeight.Bold))
                Spacer(Modifier.height(6.dp))
                Text("版本 v$version (build $build)", style = T.Small.copy(fontSize = 12.sp, color = C.Ink3))
            }

            Column(
                Modifier
                    .fillMaxWidth()
                    .padding(start = 16.dp, end = 16.dp, top = 22.dp)
                    .card()
            ) {
                AboutRow("检查更新", "当前 v$version") { vm.checkVersion(manual = true) }
                Box(Modifier.fillMaxWidth().padding(start = 14.dp).height(1.dp).background(C.Divider))
                AboutRow("开源许可", "第三方组件") { vm.showLicenses() }
            }

            Text(
                "仅限内部网络使用。文档密码不落盘明文，均经服务端加密存储。",
                style = T.Micro.copy(fontSize = 11.sp, lineHeight = 20.sp, color = Color(0xFFB4B4BE)),
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth().padding(start = 34.dp, end = 34.dp, top = 20.dp, bottom = 30.dp)
            )
        }
    }
}

@Composable
private fun AboutRow(title: String, sub: String, onClick: () -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .pressable(onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 13.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Column(Modifier.weight(1f)) {
            Text(title, style = T.Small.copy(fontSize = 13.5.sp, fontWeight = FontWeight.SemiBold, color = C.Ink))
            Spacer(Modifier.height(1.dp))
            Text(sub, style = T.Micro.copy(fontSize = 11.sp))
        }
        SvgIcon(I.chevronRight, size = 15.dp, tint = Color(0xFFC0C0CA), strokeWidth = 2.2f)
    }
}
