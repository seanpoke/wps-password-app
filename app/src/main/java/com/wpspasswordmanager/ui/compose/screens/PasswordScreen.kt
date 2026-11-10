package com.wpspasswordmanager.ui.compose.screens

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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.wpspasswordmanager.ui.compose.AppBar
import com.wpspasswordmanager.ui.compose.AppButton
import com.wpspasswordmanager.ui.compose.AppField
import com.wpspasswordmanager.ui.compose.AppViewModel
import com.wpspasswordmanager.ui.compose.Badge
import com.wpspasswordmanager.ui.compose.BtnStyle
import com.wpspasswordmanager.ui.compose.C
import com.wpspasswordmanager.ui.compose.FieldError
import com.wpspasswordmanager.ui.compose.I
import com.wpspasswordmanager.ui.compose.IconBtn
import com.wpspasswordmanager.ui.compose.PasswordFormState
import com.wpspasswordmanager.ui.compose.SvgIcon
import com.wpspasswordmanager.ui.compose.T

/**
 * 屏 25 · 修改密码（自助改密整页）
 * 三项手填：当前密码 / 新密码 / 确认新密码；四项规则实时点亮；不满足时保存按钮置灰。
 */
@Composable
fun PasswordScreen(vm: AppViewModel, account: String) {
    val p: PasswordFormState = vm.s.passwordForm ?: PasswordFormState()
    val oldPwd = remember { mutableStateOf(p.old) }
    val newPwd = remember { mutableStateOf(p.neu) }
    val confirmPwd = remember { mutableStateOf(p.confirm) }
    val edited = remember { mutableStateOf(false) }

    Column(
        Modifier
            .fillMaxSize()
            .background(Color.White)
            .statusBarsPadding()
            .imePadding()
    ) {
        AppBar("修改密码", onBack = vm::closePasswordPage)

        Column(
            Modifier
                .weight(1f)
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(start = 16.dp, end = 16.dp, top = 20.dp)
        ) {
            Text(
                "设置新密码",
                style = T.H2.copy(fontSize = 20.sp, fontWeight = FontWeight.Bold, letterSpacing = (-0.4).sp)
            )
            Spacer(Modifier.height(7.dp))
            Text(
                buildAnnotatedString {
                    append("账号 ")
                    withStyle(SpanStyle(fontWeight = FontWeight.Bold, color = C.Ink2)) { append(account) }
                    append(" · 本地账号，密码由本系统管理")
                },
                style = T.Small.copy(fontSize = 12.5.sp, color = C.Ink3)
            )

            PasswordFields(p, oldPwd, newPwd, confirmPwd, edited, vm)
        }

        // 说明文案贴底（对应原型的 margin-top:auto）
        Row(Modifier.fillMaxWidth().padding(start = 16.dp, end = 16.dp, bottom = 14.dp)) {
            SvgIcon(I.info, size = 12.dp, tint = C.Ink3, strokeWidth = 2f, modifier = Modifier.padding(top = 2.dp))
            Spacer(Modifier.width(6.dp))
            Text(
                "改密不影响其他已登录设备，它们会在 token 过期后（最长 72 小时）被要求重新登录",
                style = T.Micro.copy(fontSize = 11.sp, lineHeight = 19.sp)
            )
        }

        Column(Modifier.fillMaxWidth().background(Color.White)) {
            Box(Modifier.fillMaxWidth().height(1.dp).background(Color(0xFFF2F2F6)))
            Box(
                Modifier
                    .fillMaxWidth()
                    .navigationBarsPadding()
                    .padding(start = 16.dp, end = 16.dp, top = 12.dp, bottom = 22.dp)
            ) {
                PasswordSubmitBar(p, oldPwd, newPwd, confirmPwd, vm)
            }
        }
    }
}

private fun onPwdChange(
    target: MutableState<String>,
    value: String,
    edited: MutableState<Boolean>,
    vm: AppViewModel
) {
    target.value = value
    if (!edited.value) {
        edited.value = true
        vm.markPasswordFormEdited()
    }
}

/** 三个密码框与规则 chip 只在打字时局部重组 */
@Composable
private fun PasswordFields(
    p: PasswordFormState,
    oldPwd: MutableState<String>,
    newPwd: MutableState<String>,
    confirmPwd: MutableState<String>,
    edited: MutableState<Boolean>,
    vm: AppViewModel
) {
    var showOld by remember { mutableStateOf(false) }
    var showNew by remember { mutableStateOf(false) }
    var showConfirm by remember { mutableStateOf(false) }
    val old by oldPwd
    val neu by newPwd
    val confirm by confirmPwd
    val tooLong = neu.length > 32
    val mismatch = confirm.isNotEmpty() && confirm != neu

    Spacer(Modifier.height(22.dp))
    FieldLabel("当前密码")
    AppField(
        value = old,
        onValueChange = { onPwdChange(oldPwd, it, edited, vm) },
        placeholder = "输入当前登录密码",
        password = !showOld,
        textStyle = T.Body.copy(fontSize = 13.5.sp),
        trailing = {
            IconBtn(
                if (showOld) I.eyeOff else I.eye,
                onClick = { showOld = !showOld },
                size = 26.dp,
                iconSize = 17.dp,
                tint = Color(0xFFA8A8B2),
                strokeWidth = 1.9f
            )
        }
    )

    Spacer(Modifier.height(16.dp))
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        FieldLabel("新密码", Modifier.weight(1f))
        Text("8–32 位", style = T.Micro.copy(fontSize = 11.sp))
    }
    AppField(
        value = neu,
        onValueChange = { onPwdChange(newPwd, it, edited, vm) },
        placeholder = "设置新密码",
        password = !showNew,
        error = tooLong,
        textStyle = T.Body.copy(fontSize = 13.5.sp),
        trailing = {
            IconBtn(
                if (showNew) I.eyeOff else I.eye,
                onClick = { showNew = !showNew },
                size = 26.dp,
                iconSize = 17.dp,
                tint = Color(0xFFA8A8B2),
                strokeWidth = 1.9f
            )
        }
    )
    Spacer(Modifier.height(9.dp))
    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        RuleChip("长度 ≥ 8", neu.length in 8..32)
        RuleChip("大写", neu.any { it.isUpperCase() })
        RuleChip("小写", neu.any { it.isLowerCase() })
        RuleChip("数字", neu.any { it.isDigit() })
    }
    if (tooLong) FieldError("密码最长 32 位")

    Spacer(Modifier.height(16.dp))
    FieldLabel("确认新密码")
    AppField(
        value = confirm,
        onValueChange = { onPwdChange(confirmPwd, it, edited, vm) },
        placeholder = "再次输入新密码",
        password = !showConfirm,
        error = mismatch,
        textStyle = T.Body.copy(fontSize = 13.5.sp),
        trailing = {
            IconBtn(
                if (showConfirm) I.eyeOff else I.eye,
                onClick = { showConfirm = !showConfirm },
                size = 26.dp,
                iconSize = 17.dp,
                tint = Color(0xFFA8A8B2),
                strokeWidth = 1.9f
            )
        }
    )
    if (mismatch) FieldError("两次输入不一致")
    if (p.error.isNotEmpty()) FieldError(p.error)
    Spacer(Modifier.height(8.dp))
}

@Composable
private fun PasswordSubmitBar(
    p: PasswordFormState,
    oldPwd: MutableState<String>,
    newPwd: MutableState<String>,
    confirmPwd: MutableState<String>,
    vm: AppViewModel
) {
    val old by oldPwd
    val neu by newPwd
    val confirm by confirmPwd
    val ok = old.isNotEmpty() && neu.length in 8..32 &&
        neu.any { it.isUpperCase() } && neu.any { it.isLowerCase() } && neu.any { it.isDigit() } &&
        confirm == neu && neu != old
    AppButton(
        if (p.submitting) "正在提交…" else "保存新密码",
        onClick = { vm.submitPasswordForm(oldPwd.value, newPwd.value, confirmPwd.value) },
        modifier = Modifier.fillMaxWidth(),
        enabled = ok && !p.submitting,
        style = BtnStyle.Primary,
        height = 46.dp,
        fontSize = 15.sp
    )
}

@Composable
internal fun FieldLabel(text: String, modifier: Modifier = Modifier) {
    Text(
        text,
        style = T.Small.copy(fontSize = 12.5.sp, fontWeight = FontWeight.SemiBold, color = C.Ink2),
        modifier = modifier.padding(bottom = 8.dp)
    )
}

@Composable
internal fun RuleChip(label: String, on: Boolean) {
    Badge(
        label,
        bg = if (on) C.OkSoft else Color(0xFFF1F1F5),
        fg = if (on) C.Ok else C.Ink3,
        height = 20.dp,
        fontSize = 10.sp
    )
}
