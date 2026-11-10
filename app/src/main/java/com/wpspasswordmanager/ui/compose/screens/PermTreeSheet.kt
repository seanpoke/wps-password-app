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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.wpspasswordmanager.ui.compose.AppButton
import com.wpspasswordmanager.ui.compose.AppCheckbox
import com.wpspasswordmanager.ui.compose.AppViewModel
import com.wpspasswordmanager.ui.compose.AuthNode
import com.wpspasswordmanager.ui.compose.Badge
import com.wpspasswordmanager.ui.compose.BottomSheet
import com.wpspasswordmanager.ui.compose.BtnStyle
import com.wpspasswordmanager.ui.compose.C
import com.wpspasswordmanager.ui.compose.I
import com.wpspasswordmanager.ui.compose.IconBtn
import com.wpspasswordmanager.ui.compose.PermTreeState
import com.wpspasswordmanager.ui.compose.SvgIcon
import com.wpspasswordmanager.ui.compose.T
import com.wpspasswordmanager.ui.compose.pressable

internal data class PermRow(val node: AuthNode, val depth: Int, val inherited: Boolean)

/** 行渲染所需的全部派生值：滚动时不再做任何树遍历 */
internal data class PermLine(
    val row: PermRow,
    val checked: Boolean,
    val indeterminate: Boolean,
    val disabled: Boolean,
    val expanded: Boolean,
    val peopleHint: String?
)

internal fun flatten(
    nodes: List<AuthNode>,
    expanded: Set<String>,
    selected: Set<String>,
    inherited: Boolean,
    depth: Int,
    out: MutableList<PermRow>
) {
    nodes.forEach { n ->
        out += PermRow(n, depth, inherited)
        val childInherited = inherited || selected.contains(n.dn)
        if (n.isDept && expanded.contains(n.dn) && n.children.isNotEmpty()) {
            flatten(n.children, expanded, selected, childInherited, depth + 1, out)
        }
    }
}

internal fun subtreeHasSelected(node: AuthNode, selected: Set<String>): Boolean =
    selected.contains(node.dn) || node.children.any { subtreeHasSelected(it, selected) }

internal fun countUsers(nodes: List<AuthNode>, selected: Set<String>, inherited: Boolean): Int {
    var n = 0
    nodes.forEach { node ->
        val covered = inherited || selected.contains(node.dn)
        if (!node.isDept && covered) n += 1
        n += countUsers(node.children, selected, covered)
    }
    return n
}

internal fun countSelectedDepts(nodes: List<AuthNode>, selected: Set<String>): Int =
    nodes.count { it.isDept && selected.contains(it.dn) } +
        nodes.sumOf { countSelectedDepts(it.children, selected) }

/** 屏 16/17 · 文档权限树 */
@Composable
fun PermTreeSheet(state: PermTreeState, vm: AppViewModel) {
    BottomSheet(onDismiss = vm::closePermTree, heightFraction = 0.88f) {
        Row(
            Modifier.padding(start = 18.dp, end = 10.dp, top = 6.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(Modifier.weight(1f)) {
                Text("文档权限", style = T.Body.copy(fontSize = 17.sp, fontWeight = FontWeight.Bold))
                Spacer(Modifier.height(6.dp))
                Text(
                    state.docName,
                    style = T.Small3.copy(fontSize = 12.sp),
                    maxLines = 1
                )
            }
            IconBtn(listOf("M6 6 L18 18", "M18 6 L6 18"), onClick = vm::closePermTree, tint = C.Ink3, size = 32.dp, iconSize = 16.dp)
        }

        when {
            state.loading -> {
                Column(
                    Modifier
                        .fillMaxWidth()
                        .weight(1f),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center
                ) {
                    CircularProgressIndicator(Modifier.size(22.dp), strokeWidth = 2.dp, color = C.Primary)
                    Spacer(Modifier.height(12.dp))
                    Text("正在加载组织架构…", style = T.Micro.copy(fontSize = 11.5.sp))
                }
            }
            state.error.isNotEmpty() -> {
                Column(
                    Modifier
                        .fillMaxWidth()
                        .weight(1f)
                        .padding(horizontal = 24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center
                ) {
                    Box(
                        Modifier
                            .size(46.dp)
                            .clip(RoundedCornerShape(14.dp))
                            .background(C.WarnSoft),
                        contentAlignment = Alignment.Center
                    ) {
                        SvgIcon(I.warn, size = 22.dp, tint = C.Warn, strokeWidth = 1.8f)
                    }
                    Spacer(Modifier.height(12.dp))
                    Text(state.error, style = T.Small3.copy(lineHeight = 19.sp), textAlign = androidx.compose.ui.text.style.TextAlign.Center)
                    Spacer(Modifier.height(14.dp))
                    AppButton(
                        if (state.docId.isNullOrEmpty()) "知道了" else "重试",
                        onClick = { if (state.docId.isNullOrEmpty()) vm.closePermTree() else vm.retryPermTree() },
                        modifier = Modifier.width(140.dp),
                        height = 40.dp,
                        fontSize = 13.sp
                    )
                }
            }
            else -> {
                // 勾选态 / 半选 / 人数全部在这里一次性算完，滚动时每行只做绘制
                val lines = remember(state.roots, state.expanded, state.selected) {
                    val rows = buildList { flatten(state.roots, state.expanded, state.selected, false, 0, this) }
                    rows.map { row ->
                        val node = row.node
                        val checked = row.inherited || state.selected.contains(node.dn)
                        PermLine(
                            row = row,
                            checked = checked,
                            indeterminate = !checked && subtreeHasSelected(node, state.selected),
                            disabled = row.inherited,
                            expanded = state.expanded.contains(node.dn),
                            peopleHint = if (node.isDept) {
                                "${countUsers(node.children, state.selected, checked)} 人"
                            } else null
                        )
                    }
                }
                Column(Modifier.weight(1f)) {
                    Row(
                        Modifier.padding(horizontal = 18.dp, vertical = 10.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        ToolChip("全选", vm::permSelectAll)
                        ToolChip("反选", vm::permInvert)
                        ToolChip("展开全部", vm::permToggleExpandAll)
                    }
                    LazyColumn(Modifier.fillMaxSize()) {
                        items(
                            lines,
                            key = { it.row.node.dn },
                            contentType = { it.row.node.isDept }
                        ) { line -> PermRowItem(line, vm) }
                        item { Spacer(Modifier.height(8.dp)) }
                    }
                }
            }
        }

        if (state.saveError.isNotEmpty()) {
            Row(
                Modifier
                    .padding(horizontal = 18.dp)
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(10.dp))
                    .background(C.ErrSoft)
                    .padding(horizontal = 11.dp, vertical = 9.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                SvgIcon(I.info, size = 13.dp, tint = C.Err, strokeWidth = 2f)
                Spacer(Modifier.width(7.dp))
                Text(state.saveError, style = T.Micro.copy(color = Color(0xFF8E211C), lineHeight = 16.sp))
            }
        }

        Row(
            Modifier.padding(start = 18.dp, end = 18.dp, top = 12.dp, bottom = 14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // 整棵树递归统计只在勾选/数据变化时算一次，避免每次重组重复遍历
            val (depts, people) = remember(state.roots, state.selected) {
                countSelectedDepts(state.roots, state.selected) to
                    countUsers(state.roots, state.selected, false)
            }
            Text(
                if (depts > 0) "已选 $depts 部门 · $people 人" else "已选 $people 人",
                style = T.Micro.copy(fontSize = 11.5.sp)
            )
            Spacer(Modifier.weight(1f))
            AppButton(
                when {
                    state.saving -> "保存中…"
                    state.saveError.isNotEmpty() -> "重试保存"
                    else -> "保存权限"
                },
                onClick = vm::savePermTree,
                modifier = Modifier.width(132.dp),
                height = 42.dp,
                fontSize = 13.5.sp,
                enabled = !state.saving && !state.loading && state.error.isEmpty(),
                style = if (state.saveError.isNotEmpty()) BtnStyle.Danger else BtnStyle.Primary
            )
        }
    }
}

@Composable
private fun ToolChip(text: String, onClick: () -> Unit) {
    Box(
        Modifier
            .clip(RoundedCornerShape(999.dp))
            .border(1.dp, C.Line, RoundedCornerShape(999.dp))
            .pressable(onClick = onClick)
            .padding(horizontal = 10.dp, vertical = 5.dp)
    ) {
        Text(text, style = T.Micro.copy(fontSize = 11.sp, color = C.Ink2, fontWeight = FontWeight.Medium))
    }
}

@Composable
private fun PermRowItem(line: PermLine, vm: AppViewModel) {
    val row = line.row
    val node = row.node
    val checked = line.checked
    val indeterminate = line.indeterminate
    val disabled = line.disabled
    val expanded = line.expanded

    Row(
        Modifier
            .fillMaxWidth()
            .pressable(enabled = !disabled) { vm.togglePermNode(node.dn) }
            .padding(start = 14.dp + (row.depth * 14).dp, end = 16.dp, top = 8.dp, bottom = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        if (node.isDept && node.children.isNotEmpty()) {
            Box(
                Modifier
                    .size(18.dp)
                    .pressable { vm.togglePermExpand(node.dn) },
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
            Spacer(Modifier.width(22.dp))
        }

        AppCheckbox(
            checked = checked,
            indeterminate = indeterminate,
            disabled = disabled,
            onClick = { vm.togglePermNode(node.dn) }
        )
        Spacer(Modifier.width(8.dp))

        Text(
            node.name,
            style = T.Small.copy(
                fontSize = 12.5.sp,
                fontWeight = FontWeight.SemiBold,
                color = if (disabled) C.Ink3 else C.Ink
            ),
            maxLines = 1
        )

        if (!node.isDept) {
            Spacer(Modifier.width(6.dp))
            if (node.account == null) {
                Badge("无账号", Color(0xFFF1F1F5), C.Ink3, height = 18.dp, fontSize = 9.sp)
            }
        }

        Spacer(Modifier.weight(1f))

        when {
            disabled -> Text("含于上级", style = T.Micro.copy(fontSize = 9.5.sp, color = Color(0xFFB0B0BA)))
            node.isDept -> line.peopleHint?.let {
                Text(it, style = T.Micro.copy(fontSize = 10.5.sp))
            }
        }
    }
}
