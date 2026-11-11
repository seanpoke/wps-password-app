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
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.wpspasswordmanager.ui.compose.AppBar
import com.wpspasswordmanager.ui.compose.AppButton
import com.wpspasswordmanager.ui.compose.AppCheckbox
import com.wpspasswordmanager.ui.compose.AppField
import com.wpspasswordmanager.ui.compose.AppSwitch
import com.wpspasswordmanager.ui.compose.AppViewModel
import com.wpspasswordmanager.ui.compose.Badge
import com.wpspasswordmanager.ui.compose.BtnStyle
import com.wpspasswordmanager.ui.compose.C
import com.wpspasswordmanager.ui.compose.Dot
import com.wpspasswordmanager.ui.compose.FieldError
import com.wpspasswordmanager.ui.compose.I
import com.wpspasswordmanager.ui.compose.IconBtn
import com.wpspasswordmanager.ui.compose.SvgIcon
import com.wpspasswordmanager.ui.compose.T
import com.wpspasswordmanager.ui.compose.TestState
import com.wpspasswordmanager.ui.compose.UiState
import com.wpspasswordmanager.ui.compose.card
import com.wpspasswordmanager.ui.compose.pressable

private val STEP_TITLES = listOf("初始化设置", "登录", "开启权限", "选择应用")

@Composable
fun WizardScreen(
    s: UiState,
    step: Int,
    vm: AppViewModel,
    onFixPermission: (String) -> Unit,
    onRescan: () -> Unit
) {
    Column(
        Modifier
            .fillMaxSize()
            .background(Color.White)
            .statusBarsPadding()
            .imePadding()
    ) {
        AppBar(
            title = STEP_TITLES.getOrElse(step - 1) { "" },
            onBack = if (step > 1) vm::wizardBack else null,
            trailing = {
                Text("$step / 4", style = T.Tiny.copy(fontSize = 11.5.sp), modifier = Modifier.padding(end = 10.dp))
            }
        )
        StepsBar(step)
        Box(Modifier.weight(1f)) {
            when (step) {
                1 -> ServerStep(s, vm)
                2 -> LoginStep(s, vm)
                3 -> PermStep(s, vm, onFixPermission)
                else -> WpsStep(s, vm, onRescan)
            }
        }
    }
}

@Composable
private fun StepsBar(step: Int) {
    Row(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 10.dp),
        horizontalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        repeat(4) { i ->
            val idx = i + 1
            Box(
                Modifier
                    .weight(1f)
                    .height(3.dp)
                    .clip(RoundedCornerShape(2.dp))
                    .background(if (idx <= step) C.Primary else Color(0xFFEFEFF3))
            )
        }
    }
}

/* ---------------- 1 服务器 ---------------- */

@Composable
private fun ServerStep(s: UiState, vm: AppViewModel) {
    val holder = rememberServerFormHolder(vm)
    Column(Modifier.fillMaxSize()) {
        Column(
            Modifier
                .weight(1f)
                .verticalScroll(rememberScrollState())
        ) {
            ServerFormBody(holder, s, vm, heading = true)
        }

        FootColumn {
            ServerTestButton(holder, s, vm)
            Spacer(Modifier.height(10.dp))
            AppButton(
                "下一步",
                onClick = { vm.submitWizardServerStep(holder.host.value, holder.port.value) },
                modifier = Modifier.fillMaxWidth(),
                enabled = s.test is TestState.Ok
            )
        }
    }
}

/* ---------------- 2 登录 ---------------- */

private fun onLoginTextChange(
    target: MutableState<String>,
    value: String,
    edited: MutableState<Boolean>,
    vm: AppViewModel
) {
    target.value = value
    if (!edited.value) {
        edited.value = true
        vm.markLoginEdited()
    }
}

@Composable
private fun LoginStep(s: UiState, vm: AppViewModel) {
    val account = remember { mutableStateOf(s.account) }
    val password = remember { mutableStateOf(s.password) }
    val edited = remember { mutableStateOf(false) }
    Column(Modifier.fillMaxSize()) {
        Column(
            Modifier
                .weight(1f)
                .verticalScroll(rememberScrollState())
        ) {
            Column(Modifier.padding(horizontal = 16.dp)) {
                LoginFields(s, vm, account, password, edited)
            }
        }
        FootColumn {
            LoginSubmitButton(s, vm, account, password)
            Spacer(Modifier.height(12.dp))
            Text(
                "登录即表示同意《内部系统使用规范》",
                style = T.Tiny.copy(fontSize = 11.sp),
                modifier = Modifier.fillMaxWidth(),
                textAlign = androidx.compose.ui.text.style.TextAlign.Center
            )
        }
    }
}

/** 账号 / 密码逐字输入只重组这个子组合，步骤条与页面外壳保持不动 */
@Composable
private fun LoginFields(
    s: UiState,
    vm: AppViewModel,
    account: MutableState<String>,
    password: MutableState<String>,
    edited: MutableState<Boolean>
) {
    var pwdVisible by remember { mutableStateOf(false) }
    val acc by account
    val pwd by password
    Spacer(Modifier.height(16.dp))
    Text("登录企业账号", style = T.H1.copy(fontSize = 22.sp))
    Spacer(Modifier.height(9.dp))
    Text("使用 OA 域账号登录，密码错误 5 次将锁定 10 分钟。", style = T.Small3.copy(lineHeight = 20.sp))

    Spacer(Modifier.height(26.dp))
    Text("账号", style = T.Label)
    Spacer(Modifier.height(6.dp))
    AppField(
        value = acc,
        onValueChange = { onLoginTextChange(account, it, edited, vm) },
        placeholder = "请输入域账号",
        keyboard = KeyboardType.Text
    )

    Spacer(Modifier.height(18.dp))
    Text("密码", style = T.Label)
    Spacer(Modifier.height(6.dp))
    AppField(
        value = pwd,
        onValueChange = { onLoginTextChange(password, it, edited, vm) },
        placeholder = "登录密码",
        password = !pwdVisible,
        error = s.loginError.isNotEmpty(),
        trailing = {
            IconBtn(
                if (pwdVisible) I.eye else I.eyeOff,
                onClick = { pwdVisible = !pwdVisible },
                size = 26.dp,
                iconSize = 17.dp,
                tint = C.Placeholder
            )
        }
    )
    if (s.loginError.isNotEmpty()) FieldError(s.loginError)

    Spacer(Modifier.height(18.dp))
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.pressable { vm.setRemember(!s.remember) }
    ) {
        AppCheckbox(checked = s.remember, onClick = { vm.setRemember(!s.remember) })
        Spacer(Modifier.width(9.dp))
        Text("记住密码", style = T.Small.copy(color = C.Ink2))
    }
}

@Composable
private fun LoginSubmitButton(
    s: UiState,
    vm: AppViewModel,
    account: MutableState<String>,
    password: MutableState<String>
) {
    val acc by account
    val pwd by password
    val can = acc.trim().isNotEmpty() && pwd.isNotEmpty() && !s.loggingIn
    AppButton(
        if (s.loggingIn) "登录中…" else "登录",
        onClick = { vm.submitLogin(acc, pwd) },
        modifier = Modifier.fillMaxWidth(),
        enabled = can,
        leading = if (s.loggingIn) {
            { CircularProgressIndicator(Modifier.size(15.dp), strokeWidth = 1.8.dp, color = Color.White) }
        } else null
    )
}

/* ---------------- 3 权限 ---------------- */

@Composable
private fun PermStep(s: UiState, vm: AppViewModel, onFixPermission: (String) -> Unit) {
    val items = listOf(
        Triple("文件访问", "可读取 WpsManagement 目录内的文档元数据", s.perm.storage),
        Triple("无障碍服务", "识别 WPS 密码弹窗并自动填充", s.perm.accessibility),
        Triple("悬浮窗", "在 WPS 内显示密码助手入口", s.perm.overlay)
    )
    val ready = items.count { it.third }
    val all = ready == items.size

    Column(Modifier.fillMaxSize()) {
        Column(
            Modifier
                .weight(1f)
                .verticalScroll(rememberScrollState())
        ) {
            Column(Modifier.padding(horizontal = 16.dp)) {
                Spacer(Modifier.height(12.dp))
                Text(
                    if (all) "权限已就绪" else "还差 ${items.size - ready} 项权限",
                    style = T.H1.copy(fontSize = 20.sp)
                )
                Spacer(Modifier.height(7.dp))
                Text("这些权限仅用于识别 WPS 打开动作与展示悬浮按钮", style = T.Small3)

                Spacer(Modifier.height(16.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        Modifier
                            .weight(1f)
                            .height(5.dp)
                            .clip(RoundedCornerShape(3.dp))
                            .background(Color(0xFFEFEFF3))
                    ) {
                        Box(
                            Modifier
                                .fillMaxWidth(ready / items.size.toFloat())
                                .height(5.dp)
                                .clip(RoundedCornerShape(3.dp))
                                .background(C.Primary)
                        )
                    }
                    Spacer(Modifier.width(7.dp))
                    Text("$ready/${items.size}", style = T.Micro.copy(fontWeight = FontWeight.SemiBold))
                }

                Spacer(Modifier.height(18.dp))
                items.forEachIndexed { idx, (title, desc, on) ->
                    if (idx > 0) Spacer(Modifier.height(10.dp))
                    PermissionCard(title, desc, on) {
                        onFixPermission(
                            when (title) {
                                "文件访问" -> "storage"
                                "无障碍服务" -> "accessibility"
                                else -> "overlay"
                            }
                        )
                    }
                }

                Spacer(Modifier.height(14.dp))
                Text(
                    "返回本页会自动刷新；若状态未更新，点此重新检测",
                    style = T.Small.copy(fontSize = 11.5.sp, color = C.Primary),
                    modifier = Modifier
                        .fillMaxWidth()
                        .pressable { vm.refreshPerms() },
                    textAlign = androidx.compose.ui.text.style.TextAlign.Center
                )
                Spacer(Modifier.height(16.dp))
            }
        }
        FootColumn {
            AppButton("下一步", onClick = vm::wizardNext, modifier = Modifier.fillMaxWidth(), enabled = all)
            Spacer(Modifier.height(11.dp))
            Text(
                "稍后设置（悬浮助手将不可用）",
                style = T.Small.copy(fontSize = 11.5.sp, color = C.Ink3),
                modifier = Modifier
                    .fillMaxWidth()
                    .pressable { vm.skipPerm() },
                textAlign = androidx.compose.ui.text.style.TextAlign.Center
            )
        }
    }
}

@Composable
private fun PermissionCard(title: String, desc: String, on: Boolean, onFix: () -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .card(line = if (on) C.LineSoft else C.WarnLine)
            .padding(14.dp)
    ) {
        Box(
            Modifier
                .size(36.dp)
                .clip(RoundedCornerShape(10.dp))
                .background(if (on) C.OkSoft else C.WarnSoft),
            contentAlignment = Alignment.Center
        ) {
            if (on) {
                SvgIcon(I.check, size = 19.dp, tint = C.Ok, strokeWidth = 2.2f)
            } else if (title == "悬浮窗") {
                SvgIcon(I.display, size = 19.dp, tint = C.Warn, strokeWidth = 1.8f)
            } else {
                SvgIcon(I.warn, size = 19.dp, tint = C.Warn, strokeWidth = 1.8f)
            }
        }
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(title, style = T.Body2.copy(fontSize = 13.5.sp, fontWeight = FontWeight.SemiBold))
                Spacer(Modifier.width(8.dp))
                if (on) Badge("已开启", C.OkSoft, C.Ok) else Badge("未开启", C.WarnSoft, C.Warn)
            }
            Spacer(Modifier.height(4.dp))
            Text(desc, style = T.Micro.copy(lineHeight = 17.sp))
            if (!on) {
                Spacer(Modifier.height(9.dp))
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.pressable(onClick = onFix)
                ) {
                    Text("去开启", style = T.Small.copy(fontSize = 12.sp, fontWeight = FontWeight.SemiBold, color = C.Primary))
                    Spacer(Modifier.width(3.dp))
                    SvgIcon(I.chevronRight, size = 13.dp, tint = C.Primary, strokeWidth = 2.2f)
                }
            }
        }
    }
}

/* ---------------- 4 受管应用 ---------------- */

@Composable
private fun WpsStep(s: UiState, vm: AppViewModel, onRescan: () -> Unit) {
    Column(Modifier.fillMaxSize()) {
        Column(
            Modifier
                .weight(1f)
                .verticalScroll(rememberScrollState())
        ) {
            Column(Modifier.padding(horizontal = 16.dp)) {
                Spacer(Modifier.height(12.dp))
                Text("受管应用", style = T.H1.copy(fontSize = 20.sp))
                Spacer(Modifier.height(7.dp))
                Text("勾选后，仅在这些应用打开文档时触发密码助手", style = T.Small3)

                Spacer(Modifier.height(18.dp))
                Column(
                    Modifier
                        .fillMaxWidth()
                        .card()
                ) {
                    if (s.scanning) {
                        Row(
                            Modifier.padding(16.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            CircularProgressIndicator(Modifier.size(16.dp), strokeWidth = 1.8.dp, color = C.Primary)
                            Spacer(Modifier.width(10.dp))
                            Text("正在扫描已安装应用…", style = T.Small)
                        }
                    } else if (s.wpsApps.isEmpty()) {
                        Column(Modifier.padding(18.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                            Text("未检测到 WPS 应用", style = T.Small.copy(fontWeight = FontWeight.SemiBold))
                            Spacer(Modifier.height(6.dp))
                            Text("请先安装 WPS Office 后重新扫描", style = T.Micro)
                        }
                    } else {
                        s.wpsApps.forEachIndexed { i, app ->
                            Row(
                                Modifier
                                    .fillMaxWidth()
                                    .pressable { vm.selectWps(app.packageName) }
                                    .padding(horizontal = 14.dp, vertical = 15.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Box(
                                    Modifier
                                        .size(40.dp)
                                        .clip(RoundedCornerShape(11.dp))
                                        .background(Color(0xFFFCEBEA)),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text(
                                        "WPS",
                                        style = T.Micro.copy(fontSize = 10.sp, fontWeight = FontWeight.ExtraBold, color = Color(0xFFB3402F))
                                    )
                                }
                                Spacer(Modifier.width(12.dp))
                                Column(Modifier.weight(1f)) {
                                    Text(app.label.ifEmpty { "WPS Office" }, style = T.Body2.copy(fontWeight = FontWeight.SemiBold))
                                    Text(
                                        "${app.packageName} · ${app.versionName}",
                                        style = T.Micro,
                                        modifier = Modifier.padding(top = 2.dp)
                                    )
                                    Spacer(Modifier.height(6.dp))
                                    Badge("已安装", C.PrimarySoft, C.PrimaryDark)
                                }
                                AppCheckbox(
                                    checked = s.selectedPkg == app.packageName,
                                    onClick = { vm.selectWps(app.packageName) }
                                )
                            }
                            if (i != s.wpsApps.lastIndex) {
                                Box(Modifier.fillMaxWidth().height(1.dp).background(C.Divider))
                            }
                        }
                    }
                }

                Spacer(Modifier.height(14.dp))
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .pressable(onClick = onRescan)
                        .padding(vertical = 6.dp)
                ) {
                    SvgIcon(I.refresh, size = 14.dp, tint = C.Ink3, strokeWidth = 2f)
                    Spacer(Modifier.width(7.dp))
                    Text("重新扫描已安装应用", style = T.Small.copy(fontSize = 12.sp, color = C.Ink3))
                }
                Spacer(Modifier.height(16.dp))
            }
        }
        FootColumn {
            AppButton(
                "完成",
                onClick = vm::finishWizard,
                modifier = Modifier.fillMaxWidth(),
                enabled = !s.selectedPkg.isNullOrEmpty()
            )
        }
    }
}

@Composable
private fun FootColumn(content: @Composable androidx.compose.foundation.layout.ColumnScope.() -> Unit) {
    Column(
        Modifier
            .fillMaxWidth()
            .background(
                Brush.verticalGradient(listOf(Color.White.copy(alpha = 0f), Color.White))
            )
            .navigationBarsPadding()
            .padding(start = 16.dp, end = 16.dp, top = 14.dp, bottom = 16.dp),
        content = content
    )
}
