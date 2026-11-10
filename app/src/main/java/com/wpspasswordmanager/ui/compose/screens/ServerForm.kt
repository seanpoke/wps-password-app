package com.wpspasswordmanager.ui.compose.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.wpspasswordmanager.ui.compose.AppButton
import com.wpspasswordmanager.ui.compose.AppField
import com.wpspasswordmanager.ui.compose.AppSwitch
import com.wpspasswordmanager.ui.compose.AppViewModel
import com.wpspasswordmanager.ui.compose.BtnStyle
import com.wpspasswordmanager.ui.compose.C
import com.wpspasswordmanager.ui.compose.FieldError
import com.wpspasswordmanager.ui.compose.I
import com.wpspasswordmanager.ui.compose.SvgIcon
import com.wpspasswordmanager.ui.compose.T
import com.wpspasswordmanager.ui.compose.TestState
import com.wpspasswordmanager.ui.compose.UiState
import com.wpspasswordmanager.ui.compose.card

/**
 * 服务器地址表单的本地文本容器。
 * 逐字输入只改这两个 MutableState；只有解引用它们的子组合会重组，
 * 步骤条 / 页面外壳 / 按钮以外的整棵界面不再跟着每敲一个字符重建。
 */
class ServerFormHolder(host0: String, port0: String) {
    val host: MutableState<String> = mutableStateOf(host0)
    val port: MutableState<String> = mutableStateOf(port0)
    var edited: Boolean = false

    /** 首次编辑时让旧的连接测试结果作废，只触发一次全局状态更新 */
    fun markEdited(vm: AppViewModel) {
        if (!edited) {
            edited = true
            vm.markServerEdited()
        }
    }
}

@Composable
fun rememberServerFormHolder(vm: AppViewModel): ServerFormHolder =
    remember { ServerFormHolder(vm.s.host, vm.s.port) }

/**
 * 服务器地址表单：向导第 1 步与设置页「服务器设置」共用同一份 UI 与校验，
 * 只有外壳（步骤条 / 独立页 / 底部按钮）不同。
 */
@Composable
fun ServerFormBody(
    holder: ServerFormHolder,
    s: UiState,
    vm: AppViewModel,
    heading: Boolean = true
) {
    Column(Modifier.padding(start = 16.dp, end = 16.dp, top = if (heading) 16.dp else 20.dp)) {
        if (heading) {
            Text("连接企业服务器", style = T.H1.copy(fontSize = 22.sp))
            Spacer(Modifier.height(24.dp))
        }

        Row(verticalAlignment = Alignment.Bottom) {
            Text("服务器地址", style = T.Label, modifier = Modifier.weight(1f))
            Text(
                "端口",
                style = T.Label,
                modifier = Modifier.width(96.dp),
                textAlign = TextAlign.Center
            )
        }
        Spacer(Modifier.height(8.dp))
        ServerAddressFields(holder, s, vm)

        Spacer(Modifier.height(8.dp))
        when (val t = s.test) {
            is TestState.Ok -> {
                Row(verticalAlignment = Alignment.Top) {
                    SvgIcon(I.check, size = 12.dp, tint = C.Ok, strokeWidth = 2.6f)
                    Spacer(Modifier.width(5.dp))
                    Text("连接正常 · ${t.ms}ms", style = T.Micro.copy(color = C.Ok))
                }
            }
            is TestState.Fail -> FieldError("连不上 ${s.host}:${s.port} · ${t.message}")
            else -> Text("只填域名或 IP 与端口；协议默认 HTTPS，无需填写", style = T.Micro)
        }

        if (s.http) {
            Spacer(Modifier.height(10.dp))
            Row(
                Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(10.dp))
                    .background(C.WarnSoft)
                    .border(1.dp, C.WarnLine, RoundedCornerShape(10.dp))
                    .padding(horizontal = 11.dp, vertical = 9.dp)
            ) {
                SvgIcon(I.warn, size = 13.dp, tint = Color(0xFF8A5300), strokeWidth = 2f)
                Spacer(Modifier.width(7.dp))
                Text(
                    "明文传输，仅限本地调试；正式环境请关闭",
                    style = T.Micro.copy(color = C.WarnDeep, lineHeight = 17.sp)
                )
            }
        }

        Spacer(Modifier.height(16.dp))
        Row(
            Modifier
                .fillMaxWidth()
                .card(bg = Color(0xFFFAFAFD), line = C.LineSoft)
                .padding(12.dp)
        ) {
            SvgIcon(I.info, size = 16.dp, tint = C.Info, strokeWidth = 1.8f)
            Spacer(Modifier.width(10.dp))
            Text(
                "以 ${if (s.http) "http://" else "https://"} 连接${if (s.http) "（明文，仅调试）" else "并校验证书"}；仅传输账号与文档元数据，不上传本地文件。",
                style = T.Micro.copy(color = Color(0xFF55555F), lineHeight = 17.sp)
            )
        }

        Spacer(Modifier.height(10.dp))
        Row(
            Modifier
                .fillMaxWidth()
                .card()
                .padding(horizontal = 12.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            SvgIcon(I.folderCheck, size = 15.dp, tint = C.Ink3, strokeWidth = 1.9f)
            Spacer(Modifier.width(10.dp))
            Column(Modifier.weight(1f)) {
                Text("开发调试：允许 HTTP", style = T.Small.copy(fontWeight = FontWeight.SemiBold, color = C.Ink2))
                Text("本地联调专用，开启后以 http:// 连接", style = T.Micro, modifier = Modifier.padding(top = 1.dp))
            }
            AppSwitch(s.http) { vm.setHttp(!s.http) }
        }
        Spacer(Modifier.height(20.dp))
    }
}

/** 打字只重组这一行输入框 */
@Composable
private fun ServerAddressFields(holder: ServerFormHolder, s: UiState, vm: AppViewModel) {
    val host by holder.host
    val port by holder.port
    Row {
        AppField(
            value = host,
            onValueChange = {
                holder.host.value = it
                holder.markEdited(vm)
            },
            placeholder = "10.20.1.8",
            modifier = Modifier.weight(1f),
            error = s.test is TestState.Fail,
            ok = s.test is TestState.Ok,
            prefix = if (s.http) "http://" else "https://",
            keyboard = KeyboardType.Uri,
            trailing = {
                when (s.test) {
                    is TestState.Ok -> SvgIcon(I.check, size = 16.dp, tint = C.Ok, strokeWidth = 2.4f)
                    is TestState.Fail -> SvgIcon(I.info, size = 16.dp, tint = C.Err, strokeWidth = 2.2f)
                    else -> Unit
                }
            }
        )
        Spacer(Modifier.width(10.dp))
        AppField(
            value = port,
            onValueChange = {
                holder.port.value = it.filter { c -> c.isDigit() }.take(5)
                holder.markEdited(vm)
            },
            placeholder = "8080",
            modifier = Modifier.width(96.dp),
            keyboard = KeyboardType.Number,
            center = true
        )
    }
}

/** 「测试连接」按钮：三态文案 + loading，向导与独立页共用 */
@Composable
fun ServerTestButton(
    holder: ServerFormHolder,
    s: UiState,
    vm: AppViewModel,
    modifier: Modifier = Modifier
) {
    val host by holder.host
    val port by holder.port
    val canTest = host.isNotBlank() && (port.toIntOrNull()?.let { it in 1..65535 } == true)
    val loading = s.test is TestState.Testing
    val label = when (s.test) {
        is TestState.Ok -> "已连接 · 重新测试"
        is TestState.Fail -> "重试连接"
        is TestState.Testing -> "正在连接…"
        else -> "测试连接"
    }
    val color = when (s.test) {
        is TestState.Ok -> Color(0xFF1B5E20)
        is TestState.Fail -> C.Err
        else -> C.Ink2
    }
    val border: Color? = when (s.test) {
        is TestState.Ok -> Color(0xFFA9D9AE)
        is TestState.Fail -> Color(0xFFEFC9C5)
        else -> null
    }
    AppButton(
        text = label,
        onClick = { vm.submitServerTest(holder.host.value, holder.port.value) },
        modifier = modifier.fillMaxWidth(),
        enabled = canTest && !loading,
        style = BtnStyle.Ghost,
        height = 40.dp,
        fontSize = 13.5.sp,
        textColor = color,
        borderColor = border,
        leading = if (loading) {
            { CircularProgressIndicator(Modifier.size(14.dp), strokeWidth = 1.8.dp, color = C.Ink3) }
        } else null
    )
}
