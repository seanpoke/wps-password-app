package com.wpspasswordmanager.ui.compose.screens

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
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
import com.wpspasswordmanager.ui.compose.AppButton
import com.wpspasswordmanager.ui.compose.AppCheckbox
import com.wpspasswordmanager.ui.compose.AppField
import com.wpspasswordmanager.ui.compose.BottomSheet
import com.wpspasswordmanager.ui.compose.AppViewModel
import com.wpspasswordmanager.ui.compose.Badge
import com.wpspasswordmanager.ui.compose.BtnStyle
import com.wpspasswordmanager.ui.compose.C
import com.wpspasswordmanager.ui.compose.DashedDivider
import com.wpspasswordmanager.ui.compose.DocBadge
import com.wpspasswordmanager.ui.compose.DocItem
import com.wpspasswordmanager.ui.compose.Dot
import com.wpspasswordmanager.ui.compose.I
import com.wpspasswordmanager.ui.compose.IconBtn
import com.wpspasswordmanager.ui.compose.SvgIcon
import com.wpspasswordmanager.ui.compose.T
import com.wpspasswordmanager.ui.compose.UiState
import com.wpspasswordmanager.ui.compose.card
import com.wpspasswordmanager.ui.compose.pressable
import java.util.Calendar

/** 主界面壳：概览 / 文档 / 设置 + 底部 Tab */
@Composable
fun MainScreen(
    s: UiState,
    vm: AppViewModel,
    onFixPermission: (String) -> Unit,
    onCreateDoc: () -> Unit,
    onOpenDoc: () -> Unit,
    onOpenLogs: () -> Unit
) {
    // tab 单独订阅：主界面快照不含 tab，切页时不会让三个页签的内容失效
    val tab by remember { derivedStateOf { vm.s.tab } }
    // 稳定的点击回调：底部 tab 的 clickable 节点不会因数据刷新被替换而吞掉当前手势
    val onSelectTab = remember<(String) -> Unit> { { vm.setTab(it) } }
    Column(
        Modifier
            .fillMaxSize()
            .background(C.Bg)
    ) {
        // 状态栏区域：白底（概览/文档/设置的顶部都是白色）
        Box(
            Modifier
                .fillMaxWidth()
                .background(Color.White)
                .statusBarsPadding()
        )
        Box(Modifier.weight(1f)) {
            when (tab) {
                "overview" -> OverviewTab(s, vm, onFixPermission, onCreateDoc, onOpenDoc)
                "docs" -> DocsTab(s, vm, onCreateDoc)
                else -> SettingsTab(s, vm, onOpenLogs, onFixPermission)
            }
            if (tab == "docs") {
                NewDocFab(vm)
            }
        }
        TabBar(tab, onSelectTab)
    }
}

/** 屏 09 · 右下角悬浮「新建」。不接收 UiState，数据刷新时其 clickable 节点保持原样 */
@Composable
private fun BoxScope.NewDocFab(vm: AppViewModel) {
    Box(
        Modifier
            .align(Alignment.BottomEnd)
            .padding(end = 16.dp, bottom = 16.dp)
            .size(54.dp)
            .shadow(10.dp, RoundedCornerShape(18.dp), clip = false)
            .clip(RoundedCornerShape(18.dp))
            .background(Brush.linearGradient(C.PrimaryBtnGradient))
            .pressable { vm.openCreateDoc() },
        contentAlignment = Alignment.Center
    ) {
        SvgIcon(I.plus, size = 22.dp, tint = Color.White, strokeWidth = 2.4f)
    }
}


@Composable
private fun TabBar(current: String, onSelect: (String) -> Unit) {
    Column(Modifier.background(Color.White)) {
        Box(Modifier.fillMaxWidth().height(1.dp).background(Color(0xFFEDEDF2)))
        Row(
            Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .padding(start = 8.dp, end = 8.dp, top = 6.dp, bottom = 8.dp)
        ) {
            TabItem("概览", I.shield, current == "overview", Modifier.weight(1f)) { onSelect("overview") }
            TabItem("文档", I.doc, current == "docs", Modifier.weight(1f)) { onSelect("docs") }
            TabItem("设置", I.gear, current == "settings", Modifier.weight(1f)) { onSelect("settings") }
        }
    }
}

@Composable
private fun TabItem(label: String, icon: List<String>, on: Boolean, modifier: Modifier, onClick: () -> Unit) {
    Column(
        modifier = modifier
            .clip(RoundedCornerShape(10.dp))
            .pressable(onClick = onClick)
            .padding(vertical = 2.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        SvgIcon(icon, size = 21.dp, tint = if (on) C.Primary else Color(0xFF9A9AA4), strokeWidth = 1.9f)
        Spacer(Modifier.height(3.dp))
        Text(
            label,
            style = T.Micro.copy(
                fontSize = 10.5.sp,
                fontWeight = FontWeight.SemiBold,
                color = if (on) C.Primary else Color(0xFF9A9AA4)
            )
        )
    }
}

/* ---------------- 概览 ---------------- */

private data class Stat(
    val title: String,
    val ok: Boolean,
    val value: String,
    val sub: String,
    val highlight: Boolean = false,
    val onClick: () -> Unit
)

@Composable
private fun OverviewTab(
    s: UiState,
    vm: AppViewModel,
    onFixPermission: (String) -> Unit,
    onCreateDoc: () -> Unit,
    onOpenDoc: () -> Unit
) {
    val p = s.perm
    // 时段问候只在进入页面时计算一次，避免每次重组都 Calendar.getInstance()
    val greetingText = remember { greeting() }
    val appBad = (if (p.accessibility) 0 else 1) + (if (p.overlay) 0 else 1)
    val stats = listOf(
        Stat(
            "账号会话", s.tokenOk,
            if (s.tokenOk) "已登录" else "已失效",
            if (s.tokenOk) "心跳正常" else "需重新登录"
        ) { if (!s.tokenOk) vm.openExpired() },
        Stat(
            "应用权限", p.appPermsOk,
            if (p.appPermsOk) "已开启" else "$appBad 项未开",
            "无障碍 · 悬浮窗"
        ) { onFixPermission(if (p.accessibility) "overlay" else "accessibility") },
        Stat(
            "文档操作权限", p.storage,
            if (p.storage) "已授权" else "未授权",
            if (p.storage) "可写入文档目录" else "无法写入目录"
        ) { onFixPermission("storage") },
        Stat(
            "受管应用", p.wpsPkg != null,
            p.wpsLabel ?: "未选择",
            if (p.wpsPkg != null) "已选择" else "助手不会触发",
            highlight = true
        ) { vm.goWizardStep(4) }
    )
    val bad = stats.filter { !it.ok }
    val okLine = stats.filter { it.ok }.joinToString(" · ") { if (it.highlight) "受管应用 ${it.value}" else it.title }

    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
    ) {
        // 问候区
        Column(
            Modifier
                .fillMaxWidth()
                .background(Color.White)
                .padding(start = 16.dp, end = 16.dp, top = 10.dp, bottom = if (bad.isEmpty()) 14.dp else 12.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    Modifier
                        .size(42.dp)
                        .clip(RoundedCornerShape(14.dp))
                        .background(Brush.linearGradient(C.PrimaryGradient)),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        (s.name.ifEmpty { "?" }).take(1),
                        style = T.Body.copy(fontSize = 15.sp, fontWeight = FontWeight.Bold, color = Color.White)
                    )
                }
                Spacer(Modifier.width(11.dp))
                Column(Modifier.weight(1f)) {
                    Text(
                        "$greetingText，${s.name.ifEmpty { "用户" }}",
                        style = T.Body.copy(fontSize = 16.sp, fontWeight = FontWeight.Bold, letterSpacing = (-0.2).sp),
                        maxLines = 1
                    )
                    Spacer(Modifier.height(3.dp))
                    Text(
                        buildString {
                            if (!s.role.isNullOrEmpty()) append(roleText(s.role))
                            if (s.account.trim().isNotEmpty()) {
                                if (isNotEmpty()) append(" · ")
                                append(s.account.trim())
                            }
                            if (isEmpty()) append("企业账号")
                        },
                        style = T.Micro.copy(fontSize = 11.5.sp),
                        maxLines = 1
                    )
                }
                if (bad.isEmpty()) {
                    IconBtn(I.logout, onClick = vm::askLogout, background = C.Bg, tint = C.Ink2, size = 34.dp, iconSize = 17.dp)
                }
            }
        }

        // 28 屏：操作未就绪
        if (bad.isNotEmpty()) {
            Column(Modifier.padding(start = 16.dp, end = 16.dp, top = 10.dp)) {
                Column(
                    Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(14.dp))
                        .background(Brush.verticalGradient(listOf(Color(0xFFFFFDF8), Color(0xFFFFF6E8))))
                        .border(1.dp, C.WarnLine, RoundedCornerShape(14.dp))
                        .padding(horizontal = 13.dp, vertical = 12.dp)
                ) {
                    Row {
                        Box(
                            Modifier
                                .size(30.dp)
                                .clip(RoundedCornerShape(9.dp))
                                .background(Color(0xFFFDF0DA)),
                            contentAlignment = Alignment.Center
                        ) {
                            SvgIcon(I.warn, size = 16.dp, tint = Color(0xFF8A5300), strokeWidth = 2f)
                        }
                        Spacer(Modifier.width(10.dp))
                        Column(Modifier.weight(1f)) {
                            Text("暂不能新建或打开文档", style = T.Small.copy(fontSize = 13.sp, fontWeight = FontWeight.Bold, color = C.WarnDeep))
                            Text(
                                "4 项检查有 ${bad.size} 项未通过",
                                style = T.Micro.copy(fontSize = 11.sp, color = C.WarnSub),
                                modifier = Modifier.padding(top = 2.dp)
                            )
                        }
                    }
                    Spacer(Modifier.height(9.dp))
                    bad.forEach { b ->
                        Row(
                            Modifier
                                .fillMaxWidth()
                                .padding(vertical = 3.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Dot(C.Warn, 5.dp)
                            Spacer(Modifier.width(7.dp))
                            Text(b.title, style = T.Micro.copy(fontSize = 11.5.sp, fontWeight = FontWeight.SemiBold, color = Color(0xFF7A5B2E)))
                            Spacer(Modifier.width(5.dp))
                            Text(
                                when (b.title) {
                                    "账号会话" -> "需要重新登录"
                                    "应用权限" -> if (!p.accessibility && !p.overlay) "均未开启" else if (!p.accessibility) "无障碍未开启" else "悬浮窗未开启"
                                    "文档操作权限" -> "未授权"
                                    else -> "未选择"
                                },
                                style = T.Micro.copy(fontSize = 11.5.sp, color = C.WarnSub)
                            )
                            Spacer(Modifier.weight(1f))
                            Text(
                                "去处理",
                                style = T.Micro.copy(fontSize = 11.5.sp, fontWeight = FontWeight.Bold, color = C.Primary),
                                modifier = Modifier.pressable { b.onClick() }
                            )
                        }
                    }
                    if (okLine.isNotEmpty()) {
                        Row(
                            Modifier
                                .fillMaxWidth()
                                .padding(top = 4.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Dot(C.Ok, 5.dp)
                            Spacer(Modifier.width(7.dp))
                            Text("$okLine 已就绪", style = T.Micro.copy(fontSize = 11.sp, color = Color(0xFFA0A0AA)))
                        }
                    }
                    Spacer(Modifier.height(9.dp))
                    DashedDivider(Color(0xFFF0DFC6))
                    Spacer(Modifier.height(8.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        SvgIcon(I.info, size = 12.dp, tint = C.WarnSub, strokeWidth = 2f)
                        Spacer(Modifier.width(5.dp))
                        Text("每次开机都要重开？", style = T.Micro.copy(color = C.WarnSub))
                        Text(
                            "看原因",
                            style = T.Micro.copy(color = C.Primary, fontWeight = FontWeight.SemiBold),
                            modifier = Modifier
                                .padding(start = 3.dp)
                                .pressable { vm.showToast("无障碍服务是系统级开关，被回收或手动关闭后不会自启") }
                        )
                    }
                }
            }
        }

        // 运行状态
        Column(Modifier.padding(start = 16.dp, end = 16.dp, top = if (bad.isEmpty()) 14.dp else 12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("运行状态", style = T.Section)
                Spacer(Modifier.weight(1f))
                if (bad.isEmpty()) {
                    Badge("全部正常", C.OkSoft, C.Ok, height = 20.dp)
                } else {
                    Text("刚刚同步", style = T.Micro.copy(fontSize = 11.sp))
                }
            }
            Spacer(Modifier.height(10.dp))
            StatsGrid(stats)
        }

        // 主操作：独立组合，文档列表刷新时这两个按钮不会被重建
        MainActionsRow(onCreateDoc, onOpenDoc)

        // 最近文档
        Row(
            Modifier
                .fillMaxWidth()
                .padding(start = 16.dp, end = 16.dp, top = 14.dp, bottom = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text("最近文档", style = T.Section)
            Spacer(Modifier.weight(1f))
            Text(
                "全部 ${s.docs.size} 份",
                style = T.Small.copy(fontSize = 12.sp, fontWeight = FontWeight.SemiBold, color = C.Primary),
                modifier = Modifier.pressable { vm.setTab("docs") }
            )
        }
        Column(Modifier.padding(start = 16.dp, end = 16.dp)) {
            if (s.docs.isEmpty()) {
                Column(
                    Modifier
                        .fillMaxWidth()
                        .card()
                        .padding(horizontal = 18.dp, vertical = 26.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text("还没有文档", style = T.Small.copy(fontWeight = FontWeight.SemiBold, color = C.Ink2))
                    Spacer(Modifier.height(6.dp))
                    Text("点上方「新建文档」开始", style = T.Micro.copy(fontSize = 11.5.sp))
                }
            } else {
                s.docs.take(3).forEachIndexed { i, d ->
                    if (i > 0) Spacer(Modifier.height(8.dp))
                    DocRow(d, onClick = { vm.openDocFlow(d) }, onLongClick = { vm.openDocMenu(d) })
                }
            }
        }

        if (bad.isNotEmpty()) {
            Row(Modifier.padding(start = 16.dp, end = 16.dp, top = 9.dp, bottom = 4.dp)) {
                SvgIcon(I.info, size = 11.dp, tint = C.Ink3, strokeWidth = 2f)
                Spacer(Modifier.width(5.dp))
                Text(
                    "列表与详情可正常浏览；新建与打开需先补齐上方 ${bad.size} 项",
                    style = T.Micro.copy(lineHeight = 17.sp)
                )
            }
        }
        Spacer(Modifier.height(18.dp))
    }
}

@Composable
private fun MainActionsRow(onCreateDoc: () -> Unit, onOpenDoc: () -> Unit) {
    Row(Modifier.padding(start = 16.dp, end = 16.dp, top = 14.dp)) {
        AppButton(
            "新建文档",
            onClick = onCreateDoc,
            modifier = Modifier.weight(1.15f),
            height = 48.dp,
            fontSize = 13.5.sp,
            leading = { SvgIcon(I.plus, size = 16.dp, tint = Color.White, strokeWidth = 2.2f) }
        )
        Spacer(Modifier.width(9.dp))
        AppButton(
            "打开文档",
            onClick = onOpenDoc,
            modifier = Modifier.weight(1f),
            height = 48.dp,
            fontSize = 13.5.sp,
            style = BtnStyle.Ghost,
            leading = { SvgIcon(I.folder, size = 16.dp, tint = C.Ink2, strokeWidth = 1.9f) }
        )
    }
}

@Composable
private fun StatsGrid(stats: List<Stat>) {
    Column(verticalArrangement = Arrangement.spacedBy(9.dp)) {
        stats.chunked(2).forEach { row ->
            Row(horizontalArrangement = Arrangement.spacedBy(9.dp)) {
                row.forEach { st ->
                    Box(Modifier.weight(1f)) { StatCard(st) }
                }
                if (row.size == 1) Spacer(Modifier.weight(1f))
            }
        }
    }
}

@Composable
private fun StatCard(st: Stat) {
    Column(
        Modifier
            .fillMaxWidth()
            .card(radius = 13.dp, line = if (st.ok) C.LineSoft else C.WarnLine)
            .pressable { st.onClick() }
            .padding(horizontal = 13.dp, vertical = 12.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Dot(if (st.ok) (if (st.highlight) C.Primary else C.Ok) else C.Warn, 6.dp)
            Spacer(Modifier.width(6.dp))
            Text(st.title, style = T.Micro.copy(fontSize = 11.sp, color = if (st.ok) C.Ink3 else C.WarnDeep))
        }
        Spacer(Modifier.height(7.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                st.value,
                style = T.StatValue.copy(fontSize = 16.sp, color = if (st.ok) C.Ink else C.WarnDeep),
                maxLines = 1,
                modifier = Modifier.weight(1f, fill = false)
            )
            if (!st.ok) {
                Spacer(Modifier.width(6.dp))
                Text("去开启", style = T.Micro.copy(fontSize = 10.5.sp, fontWeight = FontWeight.Bold, color = C.Primary))
            }
        }
        Spacer(Modifier.height(3.dp))
        Text(st.sub, style = T.Micro.copy(color = if (st.ok) C.Ink3 else C.WarnSub), maxLines = 1)
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun DocRow(d: DocItem, onClick: () -> Unit, onLongClick: () -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .card()
            .combinedClickable(onClick = onClick, onLongClick = onLongClick)
            .padding(horizontal = 12.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        DocBadge(d.fmt)
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(d.name, style = T.Small.copy(fontSize = 12.5.sp, fontWeight = FontWeight.SemiBold), maxLines = 1)
            Spacer(Modifier.height(3.dp))
            Text("${d.timeText} · ${d.sizeText}", style = T.Micro)
        }
    }
}

private fun greeting(): String = when (Calendar.getInstance().get(Calendar.HOUR_OF_DAY)) {
    in 5..11 -> "上午好"
    in 12..17 -> "下午好"
    else -> "晚上好"
}

private fun roleText(role: String): String = when (role.lowercase()) {
    "admin" -> "管理员"
    "member" -> "普通成员"
    else -> role
}

/* ---------------- 文档（第一期：列表 + 搜索 + 空态） ---------------- */

@Composable
private fun DocsTab(s: UiState, vm: AppViewModel, onCreateDoc: () -> Unit) {
    var query by remember { mutableStateOf("") }
    // 过滤与分组结果按输入/数据缓存，避免每次重组（含搜索框每敲一字）重复遍历
    val list = remember(s.docs, query) {
        s.docs.filter { query.isBlank() || it.name.contains(query, ignoreCase = true) }
    }
    val today = remember(list) { list.filter { it.isToday } }
    val older = remember(list) { list.filter { !it.isToday } }
    Column(
        Modifier
            .fillMaxSize()
            .background(C.Bg)
    ) {
        Row(
            Modifier
                .fillMaxWidth()
                .background(Color.White)
                .padding(start = 16.dp, end = 10.dp, top = 8.dp, bottom = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text("我的文档", style = T.Title)
            Spacer(Modifier.weight(1f))
            IconBtn(I.refresh, onClick = { vm.reloadDocs() }, tint = C.Ink2, size = 34.dp, iconSize = 18.dp)
        }
        Column(Modifier.padding(horizontal = 16.dp, vertical = 10.dp)) {
            AppField(
                value = query,
                onValueChange = { query = it },
                placeholder = "搜索文件名",
                height = 40.dp,
                textStyle = T.Small.copy(fontSize = 12.5.sp)
            )
        }
        if (list.isEmpty()) {
            Column(
                Modifier
                    .fillMaxWidth()
                    .weight(1f)
                    .verticalScroll(rememberScrollState())
                    .padding(top = 56.dp, start = 26.dp, end = 26.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Box(
                    Modifier
                        .size(78.dp)
                        .clip(RoundedCornerShape(24.dp))
                        .background(Color.White)
                        .border(1.dp, Color(0xFFEDEDF3), RoundedCornerShape(24.dp)),
                    contentAlignment = Alignment.Center
                ) {
                    SvgIcon(I.doc, size = 34.dp, tint = Color(0xFFC4C4CE), strokeWidth = 1.5f)
                }
                Spacer(Modifier.height(20.dp))
                Text(if (query.isBlank()) "目录里还没有文档" else "没有匹配的文档", style = T.Body.copy(fontWeight = FontWeight.Bold))
                Spacer(Modifier.height(8.dp))
                Text(
                    if (query.isBlank()) "新建文档后会自动保存在这里" else "试着换一个关键词\n仅显示 WpsManagement 目录内的文件",
                    style = T.Small3, textAlign = TextAlign.Center
                )
                Spacer(Modifier.height(22.dp))
                AppButton(
                    if (query.isBlank()) "新建文档" else "清除搜索",
                    onClick = { if (query.isBlank()) onCreateDoc() else query = "" },
                    modifier = Modifier.width(150.dp),
                    style = BtnStyle.Ghost,
                    height = 40.dp,
                    fontSize = 13.sp
                )
                Spacer(Modifier.height(16.dp))
            }
        } else {
            // 用 LazyColumn + 稳定 key，文档多时不再全量组合/全量绘制
            LazyColumn(
                Modifier.weight(1f),
                contentPadding = PaddingValues(horizontal = 16.dp)
            ) {
                if (today.isNotEmpty()) {
                    item { SectionLabel("今天") }
                    items(today, key = { it.file.absolutePath }) { d ->
                        DocRow(
                            d,
                            onClick = { vm.openDocFlow(d) },
                            onLongClick = { vm.openDocMenu(d) }
                        )
                        Spacer(Modifier.height(8.dp))
                    }
                }
                if (older.isNotEmpty()) {
                    item {
                        Spacer(Modifier.height(8.dp))
                        SectionLabel("更早")
                    }
                    items(older, key = { it.file.absolutePath }) { d ->
                        DocRow(
                            d,
                            onClick = { vm.openDocFlow(d) },
                            onLongClick = { vm.openDocMenu(d) }
                        )
                        Spacer(Modifier.height(8.dp))
                    }
                }
                item {
                    Spacer(Modifier.height(8.dp))
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .card()
                            .padding(13.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        SvgIcon(I.folder, size = 16.dp, tint = C.Ink3, strokeWidth = 1.8f)
                        Spacer(Modifier.width(10.dp))
                        Text(
                            "存储位置 Documents/WpsManagement / ${s.docs.size} 项",
                            style = T.Micro.copy(fontSize = 11.5.sp)
                        )
                    }
                    Spacer(Modifier.height(16.dp))
                }
            }
        }
    }
}

@Composable
private fun SectionLabel(text: String) {
    Text(
        text,
        style = T.Micro.copy(fontSize = 10.5.sp, fontWeight = FontWeight.Bold),
        modifier = Modifier.padding(start = 2.dp, top = 6.dp, bottom = 8.dp)
    )
}

/* ---------------- 设置（第一期：账号 / 关于骨架） ---------------- */

@Composable
private fun SettingsTab(
    s: UiState,
    vm: AppViewModel,
    onOpenLogs: () -> Unit,
    onFixPermission: (String) -> Unit
) {
    var wpsPickOpen by remember { mutableStateOf(false) }
    // 版本号读取是 PackageManager IPC，切 tab / 重组时不要反复跨进程取
    val versionLine = remember { "v${vm.currentVersion()} · build ${vm.currentBuild()}" }
    Box(Modifier.fillMaxSize()) {
    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
    ) {
        Column(
            Modifier
                .fillMaxWidth()
                .background(Color.White)
                .padding(horizontal = 16.dp, vertical = 14.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    Modifier
                        .size(48.dp)
                        .clip(RoundedCornerShape(15.dp))
                        .background(Brush.linearGradient(C.PrimaryGradient)),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        (s.name.ifEmpty { "?" }).take(1),
                        style = T.Body.copy(fontSize = 17.sp, fontWeight = FontWeight.Bold, color = Color.White)
                    )
                }
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(s.name.ifEmpty { "未登录" }, style = T.Body.copy(fontSize = 15.sp, fontWeight = FontWeight.Bold))
                        if (!s.role.isNullOrEmpty()) {
                            Spacer(Modifier.width(7.dp))
                            Badge(roleText(s.role), C.PrimarySoft, C.PrimaryDark, height = 19.dp, fontSize = 9.5.sp)
                        }
                    }
                    Text(s.account, style = T.Micro.copy(fontSize = 11.5.sp), modifier = Modifier.padding(top = 2.dp))
                }
                Badge(if (s.tokenOk) "已登录" else "已失效", if (s.tokenOk) C.OkSoft else C.ErrSoft, if (s.tokenOk) C.Ok else C.Err)
            }
        }

        Column(Modifier.padding(start = 16.dp, end = 16.dp, top = 14.dp)) {
            Badge("账户与安全", C.PrimarySoft, C.PrimaryDark, height = 18.dp, fontSize = 9.5.sp, modifier = Modifier.padding(start = 2.dp, bottom = 8.dp))
            Column(Modifier.fillMaxWidth().card()) {
                // 只有本地账号（role == user）才有自助改密；LDAP 域账号密码由域站管理
                if (vm.isLocalAccount()) {
                    SettingRow("修改密码", "本地账号 · 改密后需重新登录", I.lock) { vm.openPasswordPage() }
                    Divider()
                }
                SettingRow("登录状态", if (s.tokenOk) "已登录，心跳正常" else "会话已失效", I.shield) {
                    if (!s.tokenOk) vm.openExpired() else vm.askLogout()
                }
            }

            Spacer(Modifier.height(16.dp))
            Badge("连接与设备", C.Bg, C.Ink3, height = 18.dp, fontSize = 9.5.sp, modifier = Modifier.padding(start = 2.dp, bottom = 8.dp))
            Column(Modifier.fillMaxWidth().card()) {
                SettingRow("服务器", "${s.host}:${s.port}", I.wifi) { vm.openServerEdit() }
                Divider()
                SettingRow(
                    "无障碍服务",
                    if (s.perm.appPermsOk) "无障碍与悬浮窗均已开启" else "需开启无障碍与悬浮窗才能填充",
                    I.shield
                ) {
                    when {
                        !s.perm.accessibility -> onFixPermission("accessibility")
                        !s.perm.overlay -> onFixPermission("overlay")
                        else -> vm.showToast("无障碍与悬浮窗均已开启")
                    }
                }
                Divider()
                SettingRow(
                    "WPS 应用",
                    s.perm.wpsLabel ?: "未选择受管应用",
                    I.folderCheck
                ) {
                    vm.scanWps()
                    wpsPickOpen = true
                }
            }

            Spacer(Modifier.height(16.dp))
            Badge("支持与关于", C.Bg, C.Ink3, height = 18.dp, fontSize = 9.5.sp, modifier = Modifier.padding(start = 2.dp, bottom = 8.dp))
            Column(Modifier.fillMaxWidth().card()) {
                SettingRow("运行日志", "查看填充、授权与心跳记录", I.doc) { onOpenLogs() }
                Divider()
                if (s.role?.equals("admin", true) == true) {
                    SettingRow("高级设置", "目录迁移 · HTTP 开关 · 清除缓存", I.gear) { vm.openAdvanced() }
                    Divider()
                }
                SettingRow("关于", versionLine, I.info) { vm.openAbout() }
            }

            Spacer(Modifier.height(16.dp))
            AppButton("退出登录", onClick = vm::askLogout, modifier = Modifier.fillMaxWidth(), style = BtnStyle.Ghost, textColor = C.Err, borderColor = Color(0xFFEFC9C5))
            Spacer(Modifier.height(24.dp))
        }
        }

        if (wpsPickOpen) {
            BottomSheet(onDismiss = { wpsPickOpen = false }) {
                Column(
                    Modifier
                        .fillMaxWidth()
                        .padding(start = 18.dp, end = 18.dp, top = 12.dp, bottom = 18.dp)
                ) {
                    Text("选择受管应用", style = T.Title)
                    Spacer(Modifier.height(4.dp))
                    Text(
                        if (s.scanning) "正在扫描已安装应用…" else "填充时只会启动所选应用",
                        style = T.Micro.copy(fontSize = 11.5.sp)
                    )
                    Spacer(Modifier.height(14.dp))
                    Column(Modifier.fillMaxWidth().card()) {
                        if (s.wpsApps.isEmpty()) {
                            Text(
                                if (s.scanning) "扫描中…" else "未检测到 WPS Office",
                                style = T.Small.copy(fontSize = 12.5.sp, color = C.Ink3),
                                modifier = Modifier.padding(14.dp)
                            )
                        }
                        s.wpsApps.forEachIndexed { i, app ->
                            Row(
                                Modifier
                                    .fillMaxWidth()
                                    .pressable { vm.selectWps(app.packageName) }
                                    .padding(horizontal = 14.dp, vertical = 11.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Box(
                                    Modifier.size(34.dp).clip(RoundedCornerShape(10.dp)).background(Color(0xFFF4F4F8)),
                                    contentAlignment = Alignment.Center
                                ) {
                                    SvgIcon(I.folderCheck, size = 16.dp, tint = C.Ink3, strokeWidth = 1.9f)
                                }
                                Spacer(Modifier.width(11.dp))
                                Column(Modifier.weight(1f)) {
                                    Text(
                                        app.label,
                                        style = T.Small.copy(fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = C.Ink)
                                    )
                                    Text(
                                        app.packageName,
                                        style = T.Micro.copy(fontSize = 11.sp),
                                        modifier = Modifier.padding(top = 1.dp)
                                    )
                                }
                                AppCheckbox(
                                    checked = s.selectedPkg == app.packageName,
                                    onClick = { vm.selectWps(app.packageName) }
                                )
                            }
                            if (i != s.wpsApps.lastIndex) Divider()
                        }
                    }
                    Spacer(Modifier.height(16.dp))
                    Row(Modifier.fillMaxWidth()) {
                        AppButton(
                            "取消",
                            onClick = { wpsPickOpen = false },
                            modifier = Modifier.weight(1f),
                            style = BtnStyle.Ghost,
                            height = 44.dp,
                            fontSize = 14.sp
                        )
                        Spacer(Modifier.width(10.dp))
                        AppButton(
                            "保存",
                            onClick = {
                                vm.saveWpsSelection()
                                wpsPickOpen = false
                            },
                            modifier = Modifier.weight(1f),
                            height = 44.dp,
                            fontSize = 14.sp
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun SettingRow(title: String, sub: String, icon: List<String>, onClick: () -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .pressable(onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            Modifier
                .size(32.dp)
                .clip(RoundedCornerShape(10.dp))
                .background(C.PrimarySoft),
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

@Composable
private fun Divider() {
    Box(Modifier.fillMaxWidth().padding(start = 58.dp).height(1.dp).background(C.Divider))
}

