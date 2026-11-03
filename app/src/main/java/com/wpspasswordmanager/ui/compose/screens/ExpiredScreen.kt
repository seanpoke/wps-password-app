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
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
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
import com.wpspasswordmanager.ui.compose.AppViewModel
import com.wpspasswordmanager.ui.compose.BtnStyle
import com.wpspasswordmanager.ui.compose.C
import com.wpspasswordmanager.ui.compose.FieldError
import com.wpspasswordmanager.ui.compose.I
import com.wpspasswordmanager.ui.compose.IconBtn
import com.wpspasswordmanager.ui.compose.SvgIcon
import com.wpspasswordmanager.ui.compose.T
import com.wpspasswordmanager.ui.compose.UiState
import com.wpspasswordmanager.ui.compose.pressable

/** 屏 02 · 登录状态已失效（就地登录，不跳向导） */
@Composable
fun ExpiredScreen(s: UiState, vm: AppViewModel) {
    var pwdVisible by remember { mutableStateOf(false) }
    val can = s.account.trim().isNotEmpty() && s.password.isNotEmpty() && !s.expiredBusy

    Box(
        Modifier
            .fillMaxSize()
            .background(Color.White)
            .statusBarsPadding()
            .navigationBarsPadding()
            .imePadding()
    ) {
        Column(
            Modifier
                .align(Alignment.Center)
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 30.dp, vertical = 24.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Box(
                Modifier
                    .size(64.dp)
                    .clip(RoundedCornerShape(20.dp))
                    .background(C.ErrSoft),
                contentAlignment = Alignment.Center
            ) {
                SvgIcon(I.lock, size = 30.dp, tint = C.Err, strokeWidth = 1.7f)
            }
            Spacer(Modifier.height(18.dp))
            Text("登录状态已失效", style = T.Body.copy(fontSize = 17.sp, fontWeight = FontWeight.Bold))
            Spacer(Modifier.height(9.dp))
            Text(
                "服务器会话已过期\n重新登录后将继续进入概览",
                style = T.Small3.copy(lineHeight = 22.sp),
                textAlign = TextAlign.Center
            )

            if (s.expiredForm) {
                Spacer(Modifier.height(22.dp))
                Column(Modifier.fillMaxWidth()) {
                    Text("账号", style = T.Label)
                    Spacer(Modifier.height(6.dp))
                    AppField(
                        value = s.account,
                        onValueChange = vm::setAccount,
                        placeholder = "zhangpeng",
                        keyboard = KeyboardType.Text
                    )
                    Spacer(Modifier.height(14.dp))
                    Text("密码", style = T.Label)
                    Spacer(Modifier.height(6.dp))
                    AppField(
                        value = s.password,
                        onValueChange = vm::setPassword,
                        placeholder = "登录密码",
                        password = !pwdVisible,
                        error = s.expiredError.isNotEmpty(),
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
                    if (s.expiredError.isNotEmpty()) FieldError(s.expiredError)
                }
            }

            Spacer(Modifier.height(26.dp))
            Column(Modifier.fillMaxWidth()) {
                if (s.expiredForm) {
                    AppButton(
                        if (s.expiredBusy) "登录中…" else "登录",
                        onClick = vm::reLogin,
                        modifier = Modifier.fillMaxWidth(),
                        enabled = can,
                        leading = if (s.expiredBusy) {
                            { CircularProgressIndicator(Modifier.size(15.dp), strokeWidth = 1.8.dp, color = Color.White) }
                        } else null
                    )
                    Spacer(Modifier.height(10.dp))
                    AppButton(
                        "取消",
                        onClick = vm::cancelExpiredForm,
                        modifier = Modifier.fillMaxWidth(),
                        style = BtnStyle.Ghost
                    )
                } else {
                    AppButton("重新登录", onClick = vm::openExpiredForm, modifier = Modifier.fillMaxWidth())
                }
            }
            Spacer(Modifier.height(14.dp))
            Row(
                Modifier.pressable { vm.gotoServerEdit() },
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.Center
            ) {
                Text(
                    "切换服务器",
                    style = T.Small.copy(fontSize = 12.5.sp, fontWeight = FontWeight.SemiBold, color = C.Primary)
                )
            }
            Spacer(Modifier.height(24.dp))
        }
    }
}
