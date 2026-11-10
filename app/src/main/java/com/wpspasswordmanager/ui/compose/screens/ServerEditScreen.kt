package com.wpspasswordmanager.ui.compose.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.wpspasswordmanager.ui.compose.AppBar
import com.wpspasswordmanager.ui.compose.AppButton
import com.wpspasswordmanager.ui.compose.AppViewModel
import com.wpspasswordmanager.ui.compose.C
import com.wpspasswordmanager.ui.compose.T
import com.wpspasswordmanager.ui.compose.TestState
import com.wpspasswordmanager.ui.compose.UiState

/**
 * 设置页 · 服务器设置（独立页）
 * 与向导第 1 步共用 [ServerFormBody] / [ServerTestButton]，但外壳是「设置子页」：
 * 有返回箭头、没有「1 / 4」步骤条与「下一步」，保存后立即回设置页。
 */
@Composable
fun ServerEditScreen(vm: AppViewModel) {
    val s = vm.s
    val holder = rememberServerFormHolder(vm)

    Column(
        Modifier
            .fillMaxSize()
            .background(C.Bg)
            .statusBarsPadding()
            .imePadding()
    ) {
        Box(Modifier.fillMaxWidth().background(Color.White)) {
            AppBar("服务器设置", onBack = vm::closeServerEdit)
        }
        Box(Modifier.fillMaxWidth().height(1.dp).background(Color(0xFFEFEFF3)))

        Column(
            Modifier
                .weight(1f)
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
        ) {
            ServerFormBody(holder, s, vm, heading = false)
            Text(
                "改动会立即生效；若新服务端不认可当前登录令牌，保存后需要重新登录。",
                style = T.Micro.copy(fontSize = 11.sp, lineHeight = 17.sp, color = Color(0xFF9A9AA4)),
                modifier = Modifier.padding(start = 16.dp, end = 16.dp, bottom = 16.dp)
            )
        }

        Column(Modifier.fillMaxWidth().background(Color.White)) {
            Box(Modifier.fillMaxWidth().height(1.dp).background(Color(0xFFF2F2F6)))
            Column(
                Modifier
                    .fillMaxWidth()
                    .navigationBarsPadding()
                    .padding(start = 16.dp, end = 16.dp, top = 12.dp, bottom = 16.dp)
            ) {
                ServerTestButton(holder, s, vm)
                Spacer(Modifier.height(10.dp))
                ServerEditSaveButton(s, vm, holder)
            }
        }
    }
}

/** 保存按钮独立订阅本地文本，打字时整页不重组 */
@Composable
private fun ServerEditSaveButton(s: UiState, vm: AppViewModel, holder: ServerFormHolder) {
    val host by holder.host
    val port by holder.port
    val canSave = host.isNotBlank() && (port.toIntOrNull()?.let { it in 1..65535 } == true)
    AppButton(
        if (s.test is TestState.Testing) "校验中…" else "保存并返回",
        onClick = { vm.submitServerEdit(holder.host.value, holder.port.value) },
        modifier = Modifier.fillMaxWidth(),
        enabled = canSave && s.test !is TestState.Testing,
        fontSize = 15.sp
    )
}
