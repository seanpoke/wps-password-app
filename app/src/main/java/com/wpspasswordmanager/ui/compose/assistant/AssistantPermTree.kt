package com.wpspasswordmanager.ui.compose.assistant

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectDragGestures
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.wpspasswordmanager.ui.compose.AppButton
import com.wpspasswordmanager.ui.compose.AppCheckbox
import com.wpspasswordmanager.ui.compose.AuthNode
import com.wpspasswordmanager.ui.compose.Badge
import com.wpspasswordmanager.ui.compose.BtnStyle
import com.wpspasswordmanager.ui.compose.C
import com.wpspasswordmanager.ui.compose.I
import com.wpspasswordmanager.ui.compose.IconBtn
import com.wpspasswordmanager.ui.compose.PermTreeState
import com.wpspasswordmanager.ui.compose.SvgIcon
import com.wpspasswordmanager.ui.compose.T
import com.wpspasswordmanager.ui.compose.pressable
import com.wpspasswordmanager.ui.compose.screens.PermRow
import com.wpspasswordmanager.ui.compose.screens.countSelectedDepts
import com.wpspasswordmanager.ui.compose.screens.countUsers
import com.wpspasswordmanager.ui.compose.screens.flatten
import com.wpspasswordmanager.ui.compose.screens.subtreeHasSelected

private val ICON_CLOSE = listOf("M6 6 L18 18", "M18 6 L6 18")

/** 行级展示数据：勾选/半选/禁用/人数提示在状态变化时一次性算好，滚动零计算 */
private data class PermLine(
    val node: AuthNode,
    val depth: Int,
    val checked: Boolean,
    val indeterminate: Boolean,
    val disabled: Boolean,
    val expanded: Boolean,
    val peopleHint: String?
)

private fun buildPermLines(state: PermTreeState): List<PermLine> {
    val rows = mutableListOf<PermRow>()
    flatten(state.roots, state.expanded, state.selected, false, 0, rows)
    return rows.map { row ->
        val node = row.node
        val checked = row.inherited || state.selected.contains(node.key)
        val indeterminate = !checked && subtreeHasSelected(node, state.selected)
        val peopleHint = when {
            row.inherited -> "含于上级"
            node.isDept -> "${countUsers(node.children, state.selected, checked)} 人"
            else -> null
        }
        PermLine(
            node = node,
            depth = row.depth,
            checked = checked,
            indeterminate = indeterminate,
            disabled = row.inherited,
            expanded = state.expanded.contains(node.key),
            peopleHint = peopleHint
        )
    }
}

/** 悬浮窗版权限树的动作集合（由 FloatingButtonService 提供，与 App 内 AppViewModel 行为一致） */
data class AssistantPermActions(
    val toggleNode: (String) -> Unit,
    val toggleExpand: (String) -> Unit,
    val selectAll: () -> Unit,
    val invert: () -> Unit,
    val expandAll: () -> Unit,
    val save: () -> Unit,
    val retry: () -> Unit,
    val close: () -> Unit
)

/**
 * 屏 16/17 的悬浮窗变体：参考原型的近全屏底部抽屉布局。
 * 宽 = 屏宽 92%，高 = 屏高 84%；视觉令牌（C/T/组件）与 App 内 PermTreeSheet 一致。
 * 行级勾选计算全部在 [buildPermLines] 预聚合，滚动时不做任何树遍历，保证丝滑。
 */
@Composable
fun AssistantPermTreePanel(
    state: PermTreeState,
    actions: AssistantPermActions,
    onDrag: (Float, Float) -> Unit = { _, _ -> }
) {
    val configuration = LocalConfiguration.current
    val panelWidth = (configuration.screenWidthDp * 0.92f).dp
    val panelHeight = (configuration.screenHeightDp * 0.84f).dp

    Column(
        Modifier
            .padding(6.dp)
            .width(panelWidth)
            .height(panelHeight)
            .clip(RoundedCornerShape(22.dp))
            .background(Color.White)
            .padding(horizontal = 16.dp, vertical = 14.dp)
    ) {
        // 标题行：文档权限 + 已选徽章（跟随统计）+ 关闭；同时作为拖动把手
        Row(
            Modifier
                .fillMaxWidth()
                .pointerInput(Unit) {
                    detectDragGestures { change, drag ->
                        change.consume()
                        onDrag(drag.x, drag.y)
                    }
                },
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                "文档权限",
                style = T.Body.copy(fontSize = 18.sp, fontWeight = FontWeight.Bold, color = C.Ink)
            )
            Spacer(Modifier.width(9.dp))
            val (depts, people) = remember(state.roots, state.selected) {
                countSelectedDepts(state.roots, state.selected) to
                    countUsers(state.roots, state.selected, false)
            }
            Badge(
                if (depts > 0) "已选 $depts 部门 · $people 人" else "已选 $people 人",
                Color(0xFFF1F1F5),
                C.Ink2,
                height = 20.dp,
                fontSize = 10.5.sp
            )
            Spacer(Modifier.weight(1f))
            IconBtn(ICON_CLOSE, onClick = actions.close, tint = C.Ink3, size = 30.dp, iconSize = 15.dp)
        }
        Spacer(Modifier.height(4.dp))
        Text(
            (state.docName.ifEmpty { "未识别到文档" }) + " · 修改后将同步至服务器",
            style = T.Micro.copy(fontSize = 11.5.sp),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )

        Spacer(Modifier.height(10.dp))

        when {
            state.loading -> {
                Column(
                    Modifier.fillMaxWidth().weight(1f),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center
                ) {
                    CircularProgressIndicator(Modifier.size(24.dp), strokeWidth = 2.dp, color = C.Primary)
                    Spacer(Modifier.height(12.dp))
                    Text("正在加载组织架构…", style = T.Small3.copy(fontSize = 12.5.sp))
                }
            }

            state.error.isNotEmpty() -> {
                Column(
                    Modifier.fillMaxWidth().weight(1f),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center
                ) {
                    Box(
                        Modifier.size(46.dp).clip(RoundedCornerShape(14.dp)).background(C.WarnSoft),
                        contentAlignment = Alignment.Center
                    ) {
                        SvgIcon(I.warn, size = 22.dp, tint = C.Warn, strokeWidth = 1.8f)
                    }
                    Spacer(Modifier.height(12.dp))
                    Text(
                        state.error,
                        style = T.Small3.copy(fontSize = 12.5.sp, lineHeight = 19.sp),
                        textAlign = androidx.compose.ui.text.style.TextAlign.Center
                    )
                    Spacer(Modifier.height(14.dp))
                    AppButton(
                        if (state.docId.isNullOrEmpty()) "知道了" else "重试",
                        onClick = { if (state.docId.isNullOrEmpty()) actions.close() else actions.retry() },
                        modifier = Modifier.width(150.dp),
                        height = 42.dp,
                        fontSize = 13.sp
                    )
                }
            }

            else -> {
                val lines = remember(state.roots, state.expanded, state.selected) { buildPermLines(state) }
                Column(Modifier.weight(1f)) {
                    Row(
                        Modifier.padding(horizontal = 2.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        PermChip("全选", actions.selectAll)
                        PermChip("反选", actions.invert)
                        PermChip("展开全部", actions.expandAll)
                    }
                    Spacer(Modifier.height(4.dp))
                    LazyColumn {
                        items(lines, key = { it.node.key }, contentType = { it.node.isDept }) { line ->
                            AssistantPermRow(line, actions)
                        }
                        item { Spacer(Modifier.height(10.dp)) }
                    }
                }
            }
        }

        if (state.saveError.isNotEmpty()) {
            Spacer(Modifier.height(8.dp))
            Row(
                Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(10.dp))
                    .background(C.ErrSoft)
                    .padding(horizontal = 11.dp, vertical = 9.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                SvgIcon(I.info, size = 13.dp, tint = C.Err, strokeWidth = 2f)
                Spacer(Modifier.width(7.dp))
                Text(
                    state.saveError,
                    style = T.Micro.copy(fontSize = 11.sp, lineHeight = 16.sp, color = Color(0xFF8E211C))
                )
            }
        }

        Spacer(Modifier.height(10.dp))
        AppButton(
            when {
                state.saving -> "保存中…"
                state.saveError.isNotEmpty() -> "重试保存"
                else -> "保存权限"
            },
            onClick = actions.save,
            modifier = Modifier.fillMaxWidth(),
            height = 44.dp,
            fontSize = 14.sp,
            enabled = !state.saving && !state.loading && state.error.isEmpty(),
            style = if (state.saveError.isNotEmpty()) BtnStyle.Danger else BtnStyle.Primary
        )
    }
}

@Composable
private fun PermChip(text: String, onClick: () -> Unit) {
    Box(
        Modifier
            .clip(RoundedCornerShape(999.dp))
            .border(1.dp, C.Line, RoundedCornerShape(999.dp))
            .pressable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 6.dp)
    ) {
        Text(text, style = T.Micro.copy(fontSize = 11.5.sp, color = C.Ink2, fontWeight = FontWeight.Medium))
    }
}

@Composable
private fun AssistantPermRow(line: PermLine, actions: AssistantPermActions) {
    val node = line.node
    val expanded = line.expanded

    Row(
        Modifier
            .fillMaxWidth()
            .pressable(enabled = !line.disabled) { actions.toggleNode(node.key) }
            .padding(start = 6.dp + (line.depth * 16).dp, end = 4.dp, top = 9.dp, bottom = 9.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        if (node.isDept && node.children.isNotEmpty()) {
            Box(
                Modifier.size(20.dp).pressable { actions.toggleExpand(node.key) },
                contentAlignment = Alignment.Center
            ) {
                SvgIcon(
                    I.chevronRight,
                    size = 13.dp,
                    tint = C.Ink3,
                    strokeWidth = 2.2f,
                    modifier = Modifier.rotate(if (expanded) 90f else 0f)
                )
            }
            Spacer(Modifier.width(4.dp))
        } else {
            Spacer(Modifier.width(24.dp))
        }

        AppCheckbox(
            checked = line.checked,
            indeterminate = line.indeterminate,
            disabled = line.disabled,
            onClick = { actions.toggleNode(node.key) }
        )
        Spacer(Modifier.width(9.dp))

        Text(
            node.name,
            style = T.Body.copy(
                fontSize = 14.sp,
                fontWeight = FontWeight.SemiBold,
                color = if (line.disabled) C.Ink3 else C.Ink
            ),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )

        if (!node.isDept) {
            Spacer(Modifier.width(7.dp))
            if (node.account == null) {
                Badge("无账号", Color(0xFFF1F1F5), C.Ink3, height = 18.dp, fontSize = 9.sp)
            }
        }

        Spacer(Modifier.weight(1f))

        when {
            line.disabled -> Text("含于上级", style = T.Micro.copy(fontSize = 10.sp, color = Color(0xFFB0B0BA)))
            node.isDept && line.peopleHint != null ->
                Text(line.peopleHint, style = T.Micro.copy(fontSize = 11.sp))
        }
    }
}
