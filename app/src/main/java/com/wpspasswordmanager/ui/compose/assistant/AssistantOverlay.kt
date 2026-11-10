package com.wpspasswordmanager.ui.compose.assistant

import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
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
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.wpspasswordmanager.ui.compose.AppButton
import com.wpspasswordmanager.ui.compose.BtnStyle
import com.wpspasswordmanager.ui.compose.C
import com.wpspasswordmanager.ui.compose.I
import com.wpspasswordmanager.ui.compose.PermTreeState
import com.wpspasswordmanager.ui.compose.SvgIcon
import com.wpspasswordmanager.ui.compose.T
import com.wpspasswordmanager.ui.compose.pressable

/** 悬浮助手三态的纯数据描述（Service 持有，Compose 只负责画） */
data class AssistantUiState(
    val open: Boolean = false,
    val filling: Boolean = false,
    val filled: Boolean = false,
    val docName: String = "",
    val encrypted: Boolean = false,
    val canPerm: Boolean = false,
    val password: String = ""
)

private val ICON_CLOSE = listOf("M6 6 L18 18", "M18 6 L6 18")
private val ICON_SPARK = listOf(
    "M12 3 L13.9 7.6 L18.5 9.5 L13.9 11.4 L12 16 L10.1 11.4 L5.5 9.5 L10.1 7.6 Z",
    "M18.5 15 L19.4 17.1 L21.5 18 L19.4 18.9 L18.5 21 L17.6 18.9 L15.5 18 L17.6 17.1 Z"
)
private val ICON_COPY = listOf(
    "M9 9 H18 A1.5 1.5 0 0 1 19.5 10.5 V20 A1.5 1.5 0 0 1 18 21.5 H9 A1.5 1.5 0 0 1 7.5 20 V10.5 A1.5 1.5 0 0 1 9 9 Z",
    "M15 9 V6.5 A2.5 2.5 0 0 0 12.5 4 H6.5 A2.5 2.5 0 0 0 4 6.5 V12.5 A2.5 2.5 0 0 0 6.5 15 H9"
)
private val ICON_PEOPLE = listOf(
    "circle:9,8,3.2",
    "M3.5 19.5 a5.5 5.5 0 0 1 11 0",
    "circle:17.5,9.5,2.4",
    "M15.5 19.5 a4.6 4.6 0 0 1 6 0"
)
private val ICON_SHIELD = listOf(
    "M12 2.8 L19.2 5.8 L19.2 11.9 C19.2 16.5 16.1 19.6 12 21.2 C7.9 19.6 4.8 16.5 4.8 11.9 L4.8 5.8 Z",
    "circle:12,11,2.1",
    "M12 13.1 L12 15"
)

/**
 * 悬浮助手浮层（屏 20 / 21）
 * 三态：气泡 → 未填充面板 → 已填充面板。
 * 只做渲染，所有动作通过回调交给 FloatingButtonService 的真实业务方法。
 */
@Composable
fun AssistantOverlay(
    state: AssistantUiState,
    permTree: PermTreeState? = null,
    permActions: AssistantPermActions? = null,
    onOpen: () -> Unit,
    onClose: () -> Unit,
    onFill: () -> Unit,
    onCopy: () -> Unit,
    onPerm: () -> Unit,
    onDone: () -> Unit,
    onDrag: (Float, Float) -> Unit
) {
    when {
        !state.open -> Bubble(onOpen = onOpen, onDrag = onDrag)
        permTree != null && permActions != null -> AssistantPermTreePanel(permTree, permActions, onDrag = onDrag)
        state.filled -> FilledCard(state, onCopy = onCopy, onDone = onDone, onDrag = onDrag)
        else -> PanelCard(
            state,
            onClose = onClose,
            onFill = onFill,
            onCopy = onCopy,
            onPerm = onPerm,
            onDrag = onDrag
        )
    }
}

/** 拖动与点击分层：外层手势直接可拖（不再要求长按），内层单独负责点击打开 */
@Composable
private fun Bubble(onOpen: () -> Unit, onDrag: (Float, Float) -> Unit) {
    Box(
        Modifier
            .padding(6.dp)
            .size(54.dp)
            .clip(CircleShape)
            .background(Brush.linearGradient(listOf(Color(0xFF8B4BFF), C.Primary)))
            .pointerInput(Unit) {
                detectDragGestures { change, drag ->
                    change.consume()
                    onDrag(drag.x, drag.y)
                }
            },
        contentAlignment = Alignment.Center
    ) {
        Box(
            Modifier
                .fillMaxSize()
                .clip(CircleShape)
                .pressable(onClick = onOpen),
            contentAlignment = Alignment.Center
        ) {
            SvgIcon(ICON_SHIELD, size = 25.dp, tint = Color.White, strokeWidth = 1.7f)
        }
    }
}

/** 面板标题栏拖动Modifier：不覆盖下面的按钮与列表交互 */
private fun Modifier.panelDragHandle(onDrag: (Float, Float) -> Unit): Modifier = pointerInput(Unit) {
    detectDragGestures { change, drag ->
        change.consume()
        onDrag(drag.x, drag.y)
    }
}

@Composable
private fun PanelCard(
    state: AssistantUiState,
    onClose: () -> Unit,
    onFill: () -> Unit,
    onCopy: () -> Unit,
    onPerm: () -> Unit,
    onDrag: (Float, Float) -> Unit
) {
    Column(
        Modifier
            .padding(6.dp)
            .width(262.dp)
            .clip(RoundedCornerShape(18.dp))
            .background(Color.White)
            .padding(horizontal = 13.dp, vertical = 12.dp)
    ) {
        Row(
            Modifier.fillMaxWidth().panelDragHandle(onDrag),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                Modifier.size(30.dp).clip(RoundedCornerShape(9.dp)).background(C.PrimarySoft),
                contentAlignment = Alignment.Center
            ) {
                SvgIcon(I.doc, size = 15.dp, tint = C.Primary, strokeWidth = 1.9f)
            }
            Spacer(Modifier.width(9.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    state.docName.ifEmpty { "未识别到文档" },
                    style = T.Small.copy(fontSize = 12.5.sp, fontWeight = FontWeight.SemiBold, color = C.Ink),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    if (state.encrypted) "已加密" else "未加密",
                    style = T.Micro.copy(fontSize = 10.5.sp),
                    modifier = Modifier.padding(top = 1.dp)
                )
            }
            Box(
                Modifier.size(24.dp).clip(CircleShape).pressable(onClick = onClose),
                contentAlignment = Alignment.Center
            ) {
                SvgIcon(ICON_CLOSE, size = 13.dp, tint = Color(0xFFB4B4BE), strokeWidth = 2.2f)
            }
        }

        Spacer(Modifier.height(11.dp))
        Box(
            Modifier
                .fillMaxWidth()
                .height(42.dp)
                .clip(RoundedCornerShape(11.dp))
                .background(
                    if (state.filling) Brush.linearGradient(listOf(Color(0xFFC9B3FF), Color(0xFFC0A6FF)))
                    else Brush.linearGradient(listOf(Color(0xFF8B4BFF), C.Primary))
                )
                .pressable(enabled = !state.filling, onClick = onFill),
            contentAlignment = Alignment.Center
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (state.filling) {
                    CircularProgressIndicator(
                        Modifier.size(14.dp),
                        strokeWidth = 1.8.dp,
                        color = Color.White
                    )
                    Spacer(Modifier.width(7.dp))
                } else {
                    SvgIcon(ICON_SPARK, size = 15.dp, tint = Color.White, strokeWidth = 1.9f)
                    Spacer(Modifier.width(6.dp))
                }
                Text(
                    if (state.filling) "正在填充…" else "生成并填充密码",
                    style = T.Small.copy(fontSize = 12.5.sp, fontWeight = FontWeight.Bold, color = Color.White)
                )
            }
        }

        Spacer(Modifier.height(7.dp))
        GhostAction("复制密码", ICON_COPY, onCopy)
        Spacer(Modifier.height(7.dp))
        GhostAction("文档权限", ICON_PEOPLE, onPerm)
        Spacer(Modifier.height(8.dp))
        Text(
            "拖动气泡或标题栏可移动位置",
            style = T.Micro.copy(fontSize = 10.sp, color = C.Ink3),
            modifier = Modifier.fillMaxWidth()
        )
    }
}

@Composable
private fun GhostAction(label: String, icon: List<String>, onClick: () -> Unit) {
    Box(
        Modifier
            .fillMaxWidth()
            .height(38.dp)
            .clip(RoundedCornerShape(11.dp))
            .background(Color(0xFFF7F7FA))
            .pressable(onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            SvgIcon(icon, size = 14.dp, tint = C.Ink2, strokeWidth = 1.9f)
            Spacer(Modifier.width(6.dp))
            Text(
                label,
                style = T.Small.copy(fontSize = 12.sp, fontWeight = FontWeight.SemiBold, color = C.Ink2)
            )
        }
    }
}

@Composable
private fun FilledCard(
    state: AssistantUiState,
    onCopy: () -> Unit,
    onDone: () -> Unit,
    onDrag: (Float, Float) -> Unit
) {
    Column(
        Modifier
            .padding(6.dp)
            .width(262.dp)
            .clip(RoundedCornerShape(18.dp))
            .background(Color.White)
            .padding(horizontal = 13.dp, vertical = 12.dp)
    ) {
        Row(
            Modifier.fillMaxWidth().panelDragHandle(onDrag),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                Modifier.size(30.dp).clip(RoundedCornerShape(9.dp)).background(C.OkSoft),
                contentAlignment = Alignment.Center
            ) {
                SvgIcon(I.check, size = 16.dp, tint = C.Ok, strokeWidth = 2.4f)
            }
            Spacer(Modifier.width(9.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    "密码已生成并填入",
                    style = T.Small.copy(fontSize = 12.5.sp, fontWeight = FontWeight.SemiBold, color = C.Ink)
                )
                Text(
                    "同时写入文档元数据",
                    style = T.Micro.copy(fontSize = 10.5.sp),
                    modifier = Modifier.padding(top = 1.dp)
                )
            }
        }

        Spacer(Modifier.height(11.dp))
        Column(
            Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(11.dp))
                .background(Color(0xFFF7F7FA))
                .padding(horizontal = 10.dp, vertical = 12.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                state.password.ifEmpty { "—" },
                style = T.Body.copy(
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 0.8.sp,
                    fontFamily = FontFamily.Monospace,
                    color = C.Ink
                ),
                maxLines = 1
            )
            Spacer(Modifier.height(5.dp))
            Text(
                "${state.password.length} 位 · 含大小写/数字/符号 · 强度高",
                style = T.Micro.copy(fontSize = 10.sp, color = C.Ink3)
            )
        }

        Spacer(Modifier.height(9.dp))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            AppButton(
                "复制",
                onClick = onCopy,
                modifier = Modifier.weight(1f),
                style = BtnStyle.Soft,
                height = 38.dp,
                fontSize = 12.sp
            )
            AppButton(
                "完成",
                onClick = onDone,
                modifier = Modifier.weight(1f),
                style = BtnStyle.Ghost,
                height = 38.dp,
                fontSize = 12.sp
            )
        }
        Spacer(Modifier.height(8.dp))
        Text(
            "密码不会展示在通知栏",
            style = T.Micro.copy(fontSize = 10.sp, color = C.Ink3),
            modifier = Modifier.fillMaxWidth()
        )
    }
}
