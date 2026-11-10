package com.wpspasswordmanager.ui.compose.screens

import android.content.ClipData
import android.content.ClipboardManager
import android.content.ContentValues
import android.content.Context
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.wpspasswordmanager.ui.compose.AppBar
import com.wpspasswordmanager.ui.compose.AppButton
import com.wpspasswordmanager.ui.compose.AppViewModel
import com.wpspasswordmanager.ui.compose.BottomSheet
import com.wpspasswordmanager.ui.compose.BtnStyle
import com.wpspasswordmanager.ui.compose.C
import com.wpspasswordmanager.ui.compose.I
import com.wpspasswordmanager.ui.compose.IconBtn
import com.wpspasswordmanager.ui.compose.SvgIcon
import com.wpspasswordmanager.ui.compose.T
import com.wpspasswordmanager.ui.compose.card
import com.wpspasswordmanager.ui.compose.pressable
import com.wpspasswordmanager.utils.LogManager
import kotlinx.coroutines.delay
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * 屏 27 · 运行日志
 * 数据来自 LogManager 的内存快照（不读文件），1s 轮询并在版本变化时刷新列表；
 * 暂停只冻结列表渲染，新日志仍照常写入。
 */
@Composable
fun LogsScreen(vm: AppViewModel) {
    val ctx = LocalContext.current
    var query by remember { mutableStateOf("") }
    var level by remember { mutableStateOf("all") }
    var paused by remember { mutableStateOf(false) }
    var menuOpen by remember { mutableStateOf(false) }
    var entries by remember { mutableStateOf<List<LogManager.LogEntry>>(emptyList()) }

    LaunchedEffect(paused) {
        if (paused) return@LaunchedEffect
        // 只在日志真正新增时才更新列表状态：原来每秒无条件塞入新 List，整屏（含全部图标）
        // 每秒白重绘一次
        var lastSize = -1
        var lastTail: String? = null
        while (true) {
            val fresh = LogManager.getEntries()
            val tail = fresh.lastOrNull()?.let { "${it.timestamp}|${it.message}" }
            if (fresh.size != lastSize || tail != lastTail) {
                lastSize = fresh.size
                lastTail = tail
                entries = fresh
            }
            delay(1000)
        }
    }

    val visible by remember {
        derivedStateOf {
            entries.filter { e ->
                (if (level == "all") true else e.level == "ERROR") &&
                    (query.isBlank() ||
                        "${e.timestamp} ${e.level} ${e.tag} ${e.message}".contains(query.trim(), ignoreCase = true))
            }
        }
    }

    fun reload() {
        entries = LogManager.getEntries()
    }

    fun copyAll() {
        if (visible.isEmpty()) {
            vm.showToast("没有可复制的日志")
            return
        }
        val text = visible.joinToString("\n") { "[${it.timestamp}] [${it.level}] [${it.tag}] ${it.message}" }
        val cm = ctx.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        cm.setPrimaryClip(ClipData.newPlainText("wps-logs", text))
        vm.showToast("已复制 ${visible.size} 条日志到剪贴板")
    }

    fun exportFile() {
        if (entries.isEmpty()) {
            vm.showToast("没有可导出的日志")
            return
        }
        val name = "logs-" + SimpleDateFormat("yyyyMMdd", Locale.getDefault()).format(Date()) + ".txt"
        val text = entries.joinToString("\n") { "[${it.timestamp}] [${it.level}] [${it.tag}] ${it.message}" }
        val saved = writeLogFile(ctx, name, text)
        vm.showToast(if (saved != null) "已导出到 $saved" else "导出失败：存储不可写")
    }

    fun clearAll() {
        if (entries.isEmpty()) {
            vm.showToast("日志已经是空的")
            return
        }
        LogManager.clearLogs()
        reload()
        vm.showToast("日志已清空")
    }

    Box(Modifier.fillMaxSize().background(Color.White)) {
        Column(
            Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .imePadding()
        ) {
            AppBar(
                "运行日志",
                onBack = vm::closeLogs,
                trailing = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        IconBtn(I.download, onClick = { exportFile() }, size = 36.dp, iconSize = 18.dp, tint = C.Ink)
                        IconBtn(I.more, onClick = { menuOpen = true }, size = 36.dp, iconSize = 18.dp, tint = C.Ink, strokeWidth = 2.6f)
                    }
                }
            )

            // 搜索 + 级别筛选
            Row(
                Modifier
                    .fillMaxWidth()
                    .padding(start = 14.dp, end = 14.dp, top = 10.dp, bottom = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(7.dp)
            ) {
                Row(
                    Modifier
                        .weight(1f)
                        .height(34.dp)
                        .clip(RoundedCornerShape(9.dp))
                        .background(Color(0xFFF7F7FA))
                        .padding(horizontal = 10.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    SvgIcon(I.search, size = 13.dp, tint = Color(0xFF9A9AA4), strokeWidth = 2f)
                    Spacer(Modifier.width(7.dp))
                    BasicTextField(
                        value = query,
                        onValueChange = { query = it },
                        singleLine = true,
                        textStyle = T.Tiny.copy(fontSize = 11.5.sp, color = C.Ink),
                        cursorBrush = SolidColor(C.Primary),
                        decorationBox = { inner ->
                            Box(contentAlignment = Alignment.CenterStart) {
                                if (query.isEmpty()) {
                                    Text("搜索关键词", style = T.Tiny.copy(fontSize = 11.5.sp, color = C.Placeholder))
                                }
                                inner()
                            }
                        },
                        modifier = Modifier.weight(1f)
                    )
                }
                LevelChip("全部", "all", level) { level = it }
                LevelChip("ERROR", "err", level) { level = it }
            }

            // 状态行
            Row(
                Modifier
                    .fillMaxWidth()
                    .padding(start = 14.dp, end = 14.dp, bottom = 8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                val dot = when {
                    entries.isEmpty() -> Color(0xFFC0C0CA)
                    paused -> Color(0xFFC0C0CA)
                    else -> C.Ok
                }
                Box(Modifier.width(6.dp).height(6.dp).clip(RoundedCornerShape(3.dp)).background(dot))
                Spacer(Modifier.width(7.dp))
                Text(statusText(entries.size, visible.size, paused), style = T.Micro.copy(fontSize = 10.5.sp))
                Spacer(Modifier.weight(1f))
                Row(
                    Modifier
                        .clip(RoundedCornerShape(6.dp))
                        .pressable { paused = !paused }
                        .padding(horizontal = 4.dp, vertical = 2.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    SvgIcon(
                        if (paused) I.play else I.pause,
                        size = 11.dp,
                        tint = C.Primary,
                        strokeWidth = 2.6f
                    )
                    Spacer(Modifier.width(4.dp))
                    Text(
                        if (paused) "继续" else "暂停",
                        style = T.Micro.copy(fontSize = 10.5.sp, fontWeight = FontWeight.SemiBold, color = C.Primary)
                    )
                }
            }

            Box(Modifier.fillMaxWidth().height(1.dp).background(C.Divider))

            // 列表
            val listState = rememberLazyListState()
            LaunchedEffect(visible.size, paused) {
                if (!paused && visible.isNotEmpty()) listState.scrollToItem(visible.lastIndex)
            }
            LazyColumn(Modifier.weight(1f).fillMaxWidth(), state = listState) {
                if (visible.isEmpty()) {
                    item { EmptyLogs(hasAny = entries.isNotEmpty()) }
                } else {
                    itemsIndexed(visible, key = { idx, e -> "$idx|${e.timestamp}|${e.message}" }) { _, e -> LogRow(e) }
                }
            }

            // 底部操作
            Column(Modifier.fillMaxWidth().background(Color.White)) {
                Box(Modifier.fillMaxWidth().height(1.dp).background(Color(0xFFF0F0F5)))
                Row(
                    Modifier
                        .fillMaxWidth()
                        .navigationBarsPadding()
                        .padding(start = 14.dp, end = 14.dp, top = 9.dp, bottom = 12.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    AppButton(
                        "复制全部",
                        onClick = { copyAll() },
                        modifier = Modifier.weight(1f),
                        style = BtnStyle.Ghost,
                        height = 34.dp,
                        fontSize = 12.sp
                    )
                    AppButton(
                        "清空",
                        onClick = { clearAll() },
                        modifier = Modifier.weight(1f),
                        style = BtnStyle.Ghost,
                        height = 34.dp,
                        fontSize = 12.sp
                    )
                    AppButton(
                        "导出文件",
                        onClick = { exportFile() },
                        modifier = Modifier.weight(1f),
                        style = BtnStyle.Soft,
                        height = 34.dp,
                        fontSize = 12.sp
                    )
                }
            }
        }

        if (menuOpen) {
            BottomSheet(onDismiss = { menuOpen = false }) {
                Column(Modifier.fillMaxWidth().padding(start = 18.dp, end = 18.dp, top = 12.dp)) {
                    Text("更多操作", style = T.Title)
                    Spacer(Modifier.height(4.dp))
                    Text(
                        "日志会一直滚动到最新一条；暂停只停住列表，不影响新记录写入。",
                        style = T.Micro.copy(fontSize = 11.5.sp, lineHeight = 17.sp)
                    )
                    Spacer(Modifier.height(14.dp))
                    Column(Modifier.fillMaxWidth().card()) {
                        MenuRow(
                            if (paused) "继续实时刷新" else "暂停实时刷新",
                            if (paused) "当前已暂停" else "当前正在刷新"
                        ) { paused = !paused; menuOpen = false }
                        MenuDivider()
                        MenuRow("复制全部", "当前筛选结果 ${visible.size} 条") { menuOpen = false; copyAll() }
                        MenuDivider()
                        MenuRow("导出文件", exportHint()) { menuOpen = false; exportFile() }
                        MenuDivider()
                        MenuRow("清空日志", "只清本地记录，服务端不受影响") { menuOpen = false; clearAll() }
                    }
                    Spacer(Modifier.height(16.dp))
                    AppButton(
                        "取消",
                        onClick = { menuOpen = false },
                        modifier = Modifier.fillMaxWidth(),
                        style = BtnStyle.Ghost,
                        height = 44.dp,
                        fontSize = 14.sp
                    )
                    Spacer(Modifier.height(18.dp))
                }
            }
        }
    }
}

private fun exportHint(): String =
    "保存为 logs-" + SimpleDateFormat("yyyyMMdd", Locale.getDefault()).format(Date()) + ".txt"

/**
 * 导出到系统「下载」目录。Android 10+ 走 MediaStore，无需 MANAGE_EXTERNAL_STORAGE；
 * 低版本直接写公共目录。返回给用户看的位置描述，失败返回 null。
 */
private fun writeLogFile(ctx: Context, name: String, text: String): String? = runCatching {
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
        val values = ContentValues().apply {
            put(MediaStore.MediaColumns.DISPLAY_NAME, name)
            put(MediaStore.MediaColumns.MIME_TYPE, "text/plain")
            put(MediaStore.MediaColumns.RELATIVE_PATH, Environment.DIRECTORY_DOWNLOADS)
            put(MediaStore.MediaColumns.IS_PENDING, 1)
        }
        val uri = ctx.contentResolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, values)
            ?: return@runCatching null
        val out = ctx.contentResolver.openOutputStream(uri) ?: return@runCatching null
        out.use { it.write(text.toByteArray()) }
        values.clear()
        values.put(MediaStore.MediaColumns.IS_PENDING, 0)
        ctx.contentResolver.update(uri, values, null, null)
        "Download/$name"
    } else {
        val dir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
        if (!dir.exists()) dir.mkdirs()
        File(dir, name).writeText(text)
        "Download/$name"
    }
}.getOrNull()

private fun statusText(total: Int, shown: Int, paused: Boolean): String {
    if (total == 0) return "暂无日志"
    val tail = if (shown == total) "" else "（筛选 $shown 条）"
    return if (paused) "已暂停 · 共 $total 条$tail" else "实时刷新中 · 共 $total 条$tail"
}

@Composable
private fun LevelChip(label: String, key: String, current: String, onPick: (String) -> Unit) {
    val on = current == key
    Box(
        Modifier
            .height(26.dp)
            .clip(RoundedCornerShape(7.dp))
            .background(if (on) C.Primary else Color(0xFFF4F4F8))
            .pressable { onPick(key) }
            .padding(horizontal = 9.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            label,
            style = T.Micro.copy(
                fontSize = 10.5.sp,
                fontWeight = FontWeight.SemiBold,
                color = if (on) Color.White else C.Ink2
            )
        )
    }
}

@Composable
private fun LogRow(e: LogManager.LogEntry) {
    val (bg, fg) = when (e.level) {
        "ERROR" -> Color(0xFFFDECEA) to Color(0xFFB3261E)
        "WARN" -> Color(0xFFFFF3E0) to Color(0xFF8A5300)
        "INFO" -> Color(0xFFE8F0FB) to Color(0xFF1B5AA8)
        else -> Color(0xFFF1F1F5) to Color(0xFF6A6A75)
    }
    Row(
        Modifier
            .fillMaxWidth()
            .background(if (e.level == "ERROR") Color(0xFFFEF7F6) else Color.White)
            .padding(horizontal = 14.dp, vertical = 9.dp),
        horizontalArrangement = Arrangement.spacedBy(9.dp)
    ) {
        Text(
            clockOf(e.timestamp),
            style = T.Micro.copy(
                fontSize = 10.sp,
                color = Color(0xFFA0A0AA),
                fontFamily = FontFamily.Monospace
            )
        )
        Box(
            Modifier.height(16.dp).clip(RoundedCornerShape(4.dp)).background(bg).padding(horizontal = 4.dp),
            contentAlignment = Alignment.Center
        ) {
            Text(
                e.level,
                style = T.Micro.copy(
                    fontSize = 9.5.sp,
                    fontWeight = FontWeight.Bold,
                    color = fg,
                    fontFamily = FontFamily.Monospace,
                    lineHeight = 12.sp
                )
            )
        }
        Text(
            e.message,
            style = T.Micro.copy(
                fontSize = 10.5.sp,
                lineHeight = 16.sp,
                color = Color(0xFF2A2A33),
                fontFamily = FontFamily.Monospace
            ),
            modifier = Modifier.weight(1f)
        )
    }
    Box(Modifier.fillMaxWidth().height(1.dp).background(Color(0xFFF6F6FA)))
}

private fun clockOf(timestamp: String): String {
    val time = timestamp.substringAfter(' ', timestamp)
    return if (time.length >= 8) time.substring(0, 8) else time
}

@Composable
private fun EmptyLogs(hasAny: Boolean) {
    Column(
        Modifier.fillMaxWidth().padding(horizontal = 24.dp, vertical = 46.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        if (hasAny) {
            Text("没有匹配的日志", style = T.Small3.copy(fontSize = 12.sp, color = Color(0xFF9A9AA4)))
            Spacer(Modifier.height(4.dp))
            Text("换个关键词，或切回「全部」", style = T.Micro.copy(fontSize = 11.sp, color = Color(0xFFB4B4BE)))
        } else {
            Text("日志已清空", style = T.Small3.copy(fontSize = 12.sp, color = Color(0xFF9A9AA4)))
        }
    }
}

@Composable
private fun MenuRow(title: String, sub: String, onClick: () -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .pressable(onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(Modifier.weight(1f)) {
            Text(title, style = T.Small.copy(fontSize = 13.5.sp, fontWeight = FontWeight.SemiBold, color = C.Ink))
            Spacer(Modifier.height(2.dp))
            Text(sub, style = T.Micro.copy(fontSize = 11.5.sp, lineHeight = 16.sp))
        }
    }
}

@Composable
private fun MenuDivider() {
    Box(Modifier.fillMaxWidth().padding(start = 14.dp).height(1.dp).background(C.Divider))
}
