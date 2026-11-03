package com.wpspasswordmanager.ui.compose.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.wpspasswordmanager.ui.compose.AppButton
import com.wpspasswordmanager.ui.compose.BtnStyle
import com.wpspasswordmanager.ui.compose.C
import com.wpspasswordmanager.ui.compose.DialogCard
import com.wpspasswordmanager.ui.compose.I
import com.wpspasswordmanager.ui.compose.IconBtn
import com.wpspasswordmanager.ui.compose.SvgIcon
import com.wpspasswordmanager.ui.compose.T
import com.wpspasswordmanager.ui.compose.UpdateInfo
import com.wpspasswordmanager.ui.compose.pressable

/* ---------------- 弹窗内的小件 ---------------- */

@Composable
private fun DialogIcon(bg: Color, fg: Color, icon: List<String>) {
    Box(
        Modifier
            .size(46.dp)
            .clip(RoundedCornerShape(14.dp))
            .background(bg),
        contentAlignment = Alignment.Center
    ) {
        SvgIcon(icon, size = 22.dp, tint = fg, strokeWidth = 1.9f)
    }
}

@Composable
private fun DialogTitle(title: String, body: String? = null) {
    Text(
        title,
        style = T.Body.copy(fontSize = 16.sp, fontWeight = FontWeight.Bold),
        textAlign = TextAlign.Center,
        modifier = Modifier.fillMaxWidth()
    )
    if (body != null) {
        Spacer(Modifier.height(7.dp))
        Text(
            body,
            style = T.Small.copy(fontSize = 12.5.sp, color = C.Ink3, lineHeight = 19.sp),
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth()
        )
    }
}

@Composable
private fun RadioDot(selected: Boolean) {
    Box(
        Modifier
            .size(18.dp)
            .clip(CircleShape)
            .background(if (selected) C.Primary else Color.White)
            .border(1.5.dp, if (selected) C.Primary else Color(0xFFC9C9D2), CircleShape),
        contentAlignment = Alignment.Center
    ) {
        if (selected) Box(Modifier.size(6.dp).clip(CircleShape).background(Color.White))
    }
}

/* ---------------- 退出登录确认 ---------------- */

@Composable
fun LogoutDialog(onCancel: () -> Unit, onConfirm: () -> Unit) {
    DialogCard {
        DialogTitle("退出当前账号？", "退出后悬浮助手立即停止工作，本地已生成的密码仍会保留在文档中。")
        Spacer(Modifier.height(16.dp))
        AppButton(
            "退出登录",
            onClick = onConfirm,
            modifier = Modifier.fillMaxWidth(),
            style = BtnStyle.Ghost,
            height = 44.dp,
            fontSize = 14.sp,
            textColor = C.Err,
            borderColor = Color(0xFFEFC9C5)
        )
        Spacer(Modifier.height(8.dp))
        AppButton(
            "取消",
            onClick = onCancel,
            modifier = Modifier.fillMaxWidth(),
            style = BtnStyle.Ghost,
            height = 42.dp,
            fontSize = 13.5.sp
        )
    }
}

/* ---------------- 通用二次确认 ---------------- */

@Composable
fun ConfirmDialog(
    title: String,
    body: String,
    confirmText: String,
    onCancel: () -> Unit,
    onConfirm: () -> Unit,
    danger: Boolean = false
) {
    DialogCard {
        DialogTitle(title, body)
        Spacer(Modifier.height(16.dp))
        AppButton(
            confirmText,
            onClick = onConfirm,
            modifier = Modifier.fillMaxWidth(),
            style = BtnStyle.Ghost,
            height = 44.dp,
            fontSize = 14.sp,
            textColor = if (danger) C.Err else C.Primary,
            borderColor = if (danger) Color(0xFFEFC9C5) else null
        )
        Spacer(Modifier.height(8.dp))
        AppButton(
            "取消",
            onClick = onCancel,
            modifier = Modifier.fillMaxWidth(),
            style = BtnStyle.Ghost,
            height = 42.dp,
            fontSize = 13.5.sp
        )
    }
}

/* ---------------- 打开方式（屏 18） ---------------- */

@Composable
fun OpenModeDialog(
    fileName: String,
    mode: String,
    onPick: (String) -> Unit,
    onCancel: () -> Unit,
    onConfirm: () -> Unit
) {
    DialogCard(width = 302.dp) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("以什么方式打开？", style = T.Body.copy(fontSize = 15.sp, fontWeight = FontWeight.Bold))
            Spacer(Modifier.weight(1f))
            IconBtn(
                listOf("M6 6 L18 18", "M18 6 L6 18"),
                onClick = onCancel,
                tint = C.Ink3,
                size = 30.dp,
                iconSize = 15.dp
            )
        }
        Spacer(Modifier.height(6.dp))
        Text(fileName, style = T.Micro.copy(fontSize = 11.5.sp), maxLines = 1)
        Spacer(Modifier.height(14.dp))
        ModeCard(
            title = "可编辑",
            desc = "在 WPS 中修改，保存后写回文档",
            selected = mode == "edit",
            onClick = { onPick("edit") }
        )
        Spacer(Modifier.height(8.dp))
        ModeCard(
            title = "只读查看",
            desc = "复制一份到缓存，任何改动都不会保存",
            selected = mode == "view",
            onClick = { onPick("view") }
        )
        Spacer(Modifier.height(16.dp))
        Row {
            AppButton(
                "取消",
                onClick = onCancel,
                modifier = Modifier.width(88.dp),
                style = BtnStyle.Ghost,
                height = 42.dp,
                fontSize = 13.5.sp
            )
            Spacer(Modifier.width(8.dp))
            AppButton("打开", onClick = onConfirm, modifier = Modifier.weight(1f), height = 42.dp, fontSize = 13.5.sp)
        }
    }
}

@Composable
private fun ModeCard(title: String, desc: String, selected: Boolean, onClick: () -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(if (selected) Color(0xFFFBF8FF) else Color.White)
            .border(1.5.dp, if (selected) C.Primary else Color(0xFFE5E5EC), RoundedCornerShape(12.dp))
            .pressable(onClick = onClick)
            .padding(12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            Modifier
                .size(32.dp)
                .clip(RoundedCornerShape(9.dp))
                .background(if (selected) C.Primary else Color(0xFFF1F1F5)),
            contentAlignment = Alignment.Center
        ) {
            SvgIcon(
                I.doc,
                size = 16.dp,
                tint = if (selected) Color.White else Color(0xFF6B6B76),
                strokeWidth = 1.9f
            )
        }
        Spacer(Modifier.width(11.dp))
        Column(Modifier.weight(1f)) {
            Text(
                title,
                style = T.Small.copy(fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = C.Ink)
            )
            Spacer(Modifier.height(2.dp))
            Text(desc, style = T.Micro.copy(fontSize = 11.sp, lineHeight = 16.sp))
        }
        RadioDot(selected)
    }
}

/* ---------------- 版本更新（屏 21-24） ---------------- */

@Composable
fun UpdateDialog(
    info: UpdateInfo,
    currentVersion: String,
    onLater: () -> Unit,
    onUpdate: () -> Unit,
    onExit: () -> Unit
) {
    DialogCard(width = 318.dp) {
        if (info.force) {
            Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
                DialogIcon(C.ErrSoft, C.Err, I.warn)
                Spacer(Modifier.height(13.dp))
                DialogTitle(
                    "此版本已停止支持",
                    "当前 $currentVersion 低于最低支持版本 ${info.latestVersion}。更新后才能继续使用，历史文档不受影响。"
                )
            }
            Spacer(Modifier.height(14.dp))
            WarnBox("升级前请保存正在编辑的文档，退出后未保存内容会丢失")
            Spacer(Modifier.height(16.dp))
            AppButton("立即更新", onClick = onUpdate, modifier = Modifier.fillMaxWidth(), height = 44.dp, fontSize = 14.sp)
            Spacer(Modifier.height(8.dp))
            AppButton(
                "退出应用",
                onClick = onExit,
                modifier = Modifier.fillMaxWidth(),
                style = BtnStyle.Ghost,
                height = 42.dp,
                fontSize = 13.5.sp
            )
        } else {
            Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
                DialogIcon(C.PrimarySoft, C.Primary, I.download)
                Spacer(Modifier.height(13.dp))
                DialogTitle("发现新版本 ${info.latestVersion}", "当前 $currentVersion")
            }
            if (info.changelog.isNotEmpty()) {
                Spacer(Modifier.height(14.dp))
                Column(
                    Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(11.dp))
                        .background(Color(0xFFF7F7FA))
                        .padding(horizontal = 12.dp, vertical = 11.dp)
                ) {
                    Text(
                        "更新内容",
                        style = T.Micro.copy(
                            fontSize = 10.5.sp,
                            fontWeight = FontWeight.Bold,
                            color = C.Ink3,
                            letterSpacing = 0.4.sp
                        )
                    )
                    Spacer(Modifier.height(5.dp))
                    Text(
                        info.changelog,
                        style = T.Micro.copy(fontSize = 12.sp, color = C.Ink2, lineHeight = 21.sp)
                    )
                }
            }
            Spacer(Modifier.height(16.dp))
            Row(Modifier.fillMaxWidth()) {
                AppButton(
                    "暂不更新",
                    onClick = onLater,
                    modifier = Modifier.width(96.dp),
                    style = BtnStyle.Ghost,
                    height = 42.dp,
                    fontSize = 13.5.sp
                )
                Spacer(Modifier.width(8.dp))
                AppButton("立即更新", onClick = onUpdate, modifier = Modifier.weight(1f), height = 42.dp, fontSize = 13.5.sp)
            }
        }
    }
}

/* ---------------- 下载进度 / 下载失败 ---------------- */

@Composable
fun DownloadDialog(pct: Int, error: String?, onCancel: () -> Unit, onRetry: () -> Unit) {
    DialogCard(width = 318.dp) {
        if (error != null) {
            Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
                DialogIcon(C.ErrSoft, C.Err, I.warn)
                Spacer(Modifier.height(13.dp))
                DialogTitle("下载未完成", "$error\n可重试，或稍后再从「设置 → 关于」更新。")
            }
            Spacer(Modifier.height(14.dp))
            Box(
                Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(11.dp))
                    .background(Color(0xFFFEF7F6))
                    .padding(horizontal = 12.dp, vertical = 10.dp)
            ) {
                Text(
                    "已下载的临时文件会保留，重试将从断点继续",
                    style = T.Micro.copy(fontSize = 11.sp, color = Color(0xFF8E211C), lineHeight = 16.sp)
                )
            }
            Spacer(Modifier.height(16.dp))
            Row(Modifier.fillMaxWidth()) {
                AppButton(
                    "取消",
                    onClick = onCancel,
                    modifier = Modifier.width(96.dp),
                    style = BtnStyle.Ghost,
                    height = 42.dp,
                    fontSize = 13.5.sp
                )
                Spacer(Modifier.width(8.dp))
                AppButton("重试", onClick = onRetry, modifier = Modifier.weight(1f), height = 42.dp, fontSize = 13.5.sp)
            }
        } else {
            Text("正在下载更新包", style = T.Body.copy(fontSize = 15.sp, fontWeight = FontWeight.Bold))
            Spacer(Modifier.height(18.dp))
            Box(
                Modifier
                    .fillMaxWidth()
                    .height(6.dp)
                    .clip(RoundedCornerShape(3.dp))
                    .background(Color(0xFFEDE7F6))
            ) {
                Box(
                    Modifier
                        .fillMaxWidth(pct.coerceIn(0, 100) / 100f)
                        .height(6.dp)
                        .clip(RoundedCornerShape(3.dp))
                        .background(Brush.horizontalGradient(listOf(Color(0xFF8B4BFF), C.Primary)))
                )
            }
            Spacer(Modifier.height(8.dp))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text("${pct.coerceIn(0, 100)}%", style = T.Micro.copy(fontSize = 11.sp))
                Text(
                    if (pct >= 100) "下载完成，正在校验…" else "请保持网络连接",
                    style = T.Micro.copy(fontSize = 11.sp)
                )
            }
            Spacer(Modifier.height(16.dp))
            Text(
                "下载完成后会自动打开安装程序，需要允许「安装未知应用」",
                style = T.Micro.copy(fontSize = 11.5.sp, lineHeight = 18.sp, color = C.Ink3)
            )
            Spacer(Modifier.height(16.dp))
            AppButton(
                "取消下载",
                onClick = onCancel,
                modifier = Modifier.fillMaxWidth(),
                style = BtnStyle.Ghost,
                height = 42.dp,
                fontSize = 13.5.sp
            )
        }
    }
}

@Composable
private fun WarnBox(text: String) {
    Box(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(11.dp))
            .background(Color(0xFFFEF7F6))
            .border(1.dp, Color(0xFFF7D9D6), RoundedCornerShape(11.dp))
            .padding(horizontal = 12.dp, vertical = 11.dp)
    ) {
        Text(text, style = T.Micro.copy(fontSize = 11.sp, color = Color(0xFF8E211C), lineHeight = 18.sp))
    }
}
