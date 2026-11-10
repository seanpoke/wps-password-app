package com.wpspasswordmanager.ui.compose.screens

import androidx.compose.foundation.background
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.wpspasswordmanager.ui.compose.AppBar
import com.wpspasswordmanager.ui.compose.AppViewModel
import com.wpspasswordmanager.ui.compose.Badge
import com.wpspasswordmanager.ui.compose.C
import com.wpspasswordmanager.ui.compose.I
import com.wpspasswordmanager.ui.compose.SvgIcon
import com.wpspasswordmanager.ui.compose.T
import com.wpspasswordmanager.ui.compose.card
import com.wpspasswordmanager.ui.compose.pressable

/**
 * 设置 · 高级设置（仅管理员）
 * 目录迁移 / 清理缓存。两项都直接作用在本机，不涉及服务端。
 */
@Composable
fun AdvancedScreen(vm: AppViewModel) {
    val s = vm.s
    Column(
        Modifier
            .fillMaxSize()
            .background(C.Bg)
            .statusBarsPadding()
    ) {
        Box(Modifier.fillMaxWidth().background(Color.White)) {
            AppBar("高级设置", onBack = vm::closeAdvanced)
        }
        Box(Modifier.fillMaxWidth().height(1.dp).background(Color(0xFFEFEFF3)))

        Column(
            Modifier
                .weight(1f)
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .navigationBarsPadding()
                .padding(start = 16.dp, end = 16.dp, top = 14.dp)
        ) {
            Badge("存储", C.PrimarySoft, C.PrimaryDark, height = 18.dp, fontSize = 9.5.sp, modifier = Modifier.padding(start = 2.dp, bottom = 8.dp))
            Column(Modifier.fillMaxWidth().card()) {
                AdvancedRow(
                    title = if (s.migrating) "正在迁移…" else "迁移旧目录文档",
                    sub = "把旧版本遗留的文档合并到 Documents/WpsManagement",
                    icon = I.folder,
                    onClick = { if (!s.migrating) vm.runMigration() }
                )
                Box(Modifier.fillMaxWidth().padding(start = 58.dp).height(1.dp).background(C.Divider))
                AdvancedRow(
                    title = "清理缓存",
                    sub = if (s.cacheSize.isEmpty()) "正在统计…" else "当前占用 ${s.cacheSize}",
                    icon = I.refresh,
                    onClick = vm::clearCache
                )
            }
        }
    }
}

@Composable
private fun AdvancedRow(title: String, sub: String, icon: List<String>, onClick: () -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .pressable(onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            Modifier.size(32.dp).clip(RoundedCornerShape(10.dp)).background(C.PrimarySoft),
            contentAlignment = Alignment.Center
        ) {
            SvgIcon(icon, size = 16.dp, tint = C.Primary, strokeWidth = 1.9f)
        }
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(title, style = T.Small.copy(fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = C.Ink))
            Text(sub, style = T.Micro.copy(fontSize = 11.sp), modifier = Modifier.padding(top = 1.dp))
        }
        SvgIcon(I.chevronRight, size = 15.dp, tint = Color(0xFFC0C0CA), strokeWidth = 2.2f)
    }
}
