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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.MutableState
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
import com.wpspasswordmanager.ui.compose.AppField
import com.wpspasswordmanager.ui.compose.AppViewModel
import com.wpspasswordmanager.ui.compose.BottomSheet
import com.wpspasswordmanager.ui.compose.BtnStyle
import com.wpspasswordmanager.ui.compose.C
import com.wpspasswordmanager.ui.compose.CreateDocState
import com.wpspasswordmanager.ui.compose.FieldError
import com.wpspasswordmanager.ui.compose.I
import com.wpspasswordmanager.ui.compose.IconBtn
import com.wpspasswordmanager.ui.compose.T
import com.wpspasswordmanager.ui.compose.pressable

/** 屏 20 · 新建文档抽屉 */
@Composable
fun CreateDocSheet(state: CreateDocState, vm: AppViewModel) {
    // 文本放在 MutableState 引用里：只有真正解引用它的 CreateNameField 会因打字重组，
    // 抽屉标题 / 格式卡片 / 底部按钮都保持稳定，键盘动画期间不再整层重测量。
    val nameState = remember { mutableStateOf(state.name) }
    val dirtyState = remember { mutableStateOf(false) }
    BottomSheet(onDismiss = vm::closeCreateDoc, dismissOnScrimClick = false) {
        Row(
            Modifier.padding(start = 18.dp, end = 10.dp, top = 6.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(Modifier.weight(1f)) {
                Text("新建文档", style = T.Body.copy(fontSize = 17.sp, fontWeight = FontWeight.Bold))
                Spacer(Modifier.height(6.dp))
                Text("保存到 Documents/WpsManagement 目录", style = T.Small3.copy(fontSize = 12.sp))
            }
            IconBtn(listOf("M6 6 L18 18", "M18 6 L6 18"), onClick = vm::closeCreateDoc, tint = C.Ink3, size = 32.dp, iconSize = 16.dp)
        }

        Column(Modifier.padding(start = 18.dp, end = 18.dp, top = 16.dp)) {
            Text("文件名", style = T.Label, color = C.Ink, modifier = Modifier.padding(bottom = 6.dp))
            CreateNameField(nameState, dirtyState, state, vm)

            Spacer(Modifier.height(18.dp))
            Text("格式", style = T.Label, color = C.Ink)
            Spacer(Modifier.height(8.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf(Triple("docx", "文档", "Word"), Triple("xlsx", "表格", "Excel"), Triple("pptx", "演示", "PPT"))
                    .forEach { (fmt, _, hint) ->
                        val on = state.fmt == fmt
                        Column(
                            Modifier
                                .weight(1f)
                                .clip(RoundedCornerShape(12.dp))
                                .background(if (on) C.PrimarySoft.copy(alpha = 0.5f) else Color.White)
                                .border(
                                    width = if (on) 1.5.dp else 1.dp,
                                    color = if (on) C.Primary else C.Line,
                                    shape = RoundedCornerShape(12.dp)
                                )
                                .pressable(enabled = !state.creating) { vm.setCreateFmt(fmt) }
                                .padding(vertical = 10.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Text(
                                ".$fmt",
                                style = T.Small.copy(
                                    fontSize = 12.5.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = if (on) C.PrimaryDark else C.Ink
                                )
                            )
                            Spacer(Modifier.height(2.dp))
                            Text(hint, style = T.Micro.copy(fontSize = 10.sp, color = C.Ink3))
                        }
                    }
            }
        }

        Row(Modifier.padding(start = 18.dp, end = 18.dp, top = 20.dp, bottom = 16.dp)) {
            AppButton(
                "取消",
                onClick = vm::closeCreateDoc,
                modifier = Modifier.width(96.dp),
                style = BtnStyle.Ghost,
                height = 46.dp,
                fontSize = 14.sp,
                enabled = !state.creating
            )
            Spacer(Modifier.width(9.dp))
            AppButton(
                if (state.creating) "创建中…" else "创建文档",
                onClick = { vm.submitCreate(nameState.value) },
                modifier = Modifier.weight(1f),
                height = 46.dp,
                fontSize = 14.sp,
                enabled = !state.creating,
                leading = if (state.creating) {
                    { CircularProgressIndicator(Modifier.height(15.dp).width(15.dp), strokeWidth = 1.8.dp, color = Color.White) }
                } else null
            )
        }
    }
}

/** 打字只重组这一行：父级抽屉 / 弹窗的其余内容完全不参与 */
@Composable
private fun CreateNameField(
    nameState: MutableState<String>,
    dirtyState: MutableState<Boolean>,
    state: CreateDocState,
    vm: AppViewModel
) {
    val name by nameState
    Row(verticalAlignment = Alignment.CenterVertically) {
        AppField(
            value = name,
            onValueChange = {
                nameState.value = it
                if (!dirtyState.value) {
                    dirtyState.value = true
                    vm.markCreateNameEdited()
                }
            },
            placeholder = "例如：2026 年 Q1 预算表",
            modifier = Modifier.weight(1f),
            highlighted = true,
            error = state.error.isNotEmpty(),
            enabled = !state.creating,
            textStyle = T.Body.copy(fontSize = 13.5.sp)
        )
        Spacer(Modifier.width(8.dp))
        Text(".${state.fmt}", style = T.Small.copy(fontSize = 12.sp, color = C.Ink3, fontWeight = FontWeight.SemiBold))
    }
    if (state.error.isNotEmpty()) FieldError(state.error)
}

/** 屏 19 · 同名文档已存在 */
@Composable
fun FileExistsDialog(
    state: com.wpspasswordmanager.ui.compose.FileExistsState,
    vm: AppViewModel
) {
    val renameState = remember(state.fullName) { mutableStateOf(state.rename) }
    // ViewModel 回填默认重命名时才同步；用户逐字输入不会写回 UiState
    LaunchedEffect(state.rename) { renameState.value = state.rename }
    com.wpspasswordmanager.ui.compose.DialogCard(width = 308.dp) {
        Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
            Box(
                Modifier
                    .width(46.dp)
                    .height(46.dp)
                    .clip(RoundedCornerShape(14.dp))
                    .background(C.WarnSoft),
                contentAlignment = Alignment.Center
            ) {
                com.wpspasswordmanager.ui.compose.SvgIcon(I.warn, size = 22.dp, tint = C.Warn, strokeWidth = 1.8f)
            }
            Spacer(Modifier.height(13.dp))
            Text(
                "同名文档已存在",
                style = T.Body.copy(fontSize = 17.sp, fontWeight = FontWeight.Bold),
                modifier = Modifier.fillMaxWidth(),
                textAlign = androidx.compose.ui.text.style.TextAlign.Center
            )
            Spacer(Modifier.height(7.dp))
            Text(
                "WpsManagement 中已有「${state.fullName}」，请选择处理方式。",
                style = T.Small3.copy(lineHeight = 20.sp),
                modifier = Modifier.fillMaxWidth(),
                textAlign = androidx.compose.ui.text.style.TextAlign.Center
            )
        }
        Spacer(Modifier.height(14.dp))
        val rename = state.mode == "rename"
        ModeRow("覆盖原文件", "不可恢复", !rename) { vm.pickExistsMode("overwrite") }
        Spacer(Modifier.height(7.dp))
        ModeRow("重命名保存", "", rename) { vm.pickExistsMode("rename") }
        if (rename) {
            Spacer(Modifier.height(8.dp))
            RenameField(renameState, state)
        }
        Spacer(Modifier.height(16.dp))
        Row {
            AppButton("取消", onClick = vm::dismissExists, modifier = Modifier.width(88.dp), style = BtnStyle.Ghost, height = 42.dp, fontSize = 13.5.sp)
            Spacer(Modifier.width(8.dp))
            AppButton(
                if (rename) "保存副本" else "覆盖并打开",
                onClick = { vm.submitExistsRename(renameState.value) },
                modifier = Modifier.weight(1f),
                height = 42.dp,
                fontSize = 13.5.sp
            )
        }
    }
}

@Composable
private fun RenameField(
    renameState: MutableState<String>,
    state: com.wpspasswordmanager.ui.compose.FileExistsState
) {
    val renameText by renameState
    Row(verticalAlignment = Alignment.CenterVertically) {
        AppField(
            value = renameText,
            onValueChange = { renameState.value = it },
            placeholder = "新文件名",
            modifier = Modifier.weight(1f),
            textStyle = T.Body.copy(fontSize = 13.sp)
        )
        Spacer(Modifier.width(6.dp))
        Text(".${state.fmt}", style = T.Small.copy(color = C.Ink3, fontWeight = FontWeight.SemiBold))
    }
}

@Composable
private fun ModeRow(title: String, badge: String, on: Boolean, onClick: () -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(11.dp))
            .background(if (on) C.PrimarySoft.copy(alpha = 0.55f) else Color.White)
            .border(if (on) 1.5.dp else 1.dp, if (on) C.Primary else C.Line, RoundedCornerShape(11.dp))
            .pressable(onClick = onClick)
            .padding(horizontal = 11.dp, vertical = 11.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        com.wpspasswordmanager.ui.compose.AppCheckbox(checked = on, onClick = onClick)
        Spacer(Modifier.width(9.dp))
        Text(title, style = T.Small.copy(fontSize = 13.sp, fontWeight = FontWeight.SemiBold))
        Spacer(Modifier.weight(1f))
        if (badge.isNotEmpty()) {
            com.wpspasswordmanager.ui.compose.Badge(badge, C.ErrSoft, C.Err, height = 19.dp, fontSize = 9.5.sp)
        }
    }
}
