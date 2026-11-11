package com.wpspasswordmanager.ui.compose

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * 设计令牌：与 design/prototype.html 的 :root 变量一一对应。
 * 原型按 360×760 设计，1 CSS px = 1dp。
 */
object C {
    val Primary = Color(0xFF6200EE)
    val PrimaryMid = Color(0xFF7A1FFF)
    val PrimaryDark = Color(0xFF3700B3)
    val PrimarySoft = Color(0xFFEDE7F6)

    val Ok = Color(0xFF2E7D32)
    val OkSoft = Color(0xFFE8F4E9)
    val Warn = Color(0xFFB26A00)
    val WarnDeep = Color(0xFF8A5300)
    val WarnSub = Color(0xFFA07A47)
    val WarnSoft = Color(0xFFFFF3E0)
    val WarnLine = Color(0xFFF4DFC0)
    val Err = Color(0xFFC62828)
    val ErrSoft = Color(0xFFFDECEA)
    val Info = Color(0xFF1565C0)


    val Ink = Color(0xFF1B1B1F)
    val Ink2 = Color(0xFF4A4A52)
    val Ink3 = Color(0xFF8A8A94)
    val Line = Color(0xFFE3E3E8)
    val LineSoft = Color(0xFFEFEFF3)
    val Divider = Color(0xFFF4F4F8)
    val Bg = Color(0xFFF6F6F9)
    val Field = Color(0xFFDEDEE6)

    val Placeholder = Color(0xFFA8A8B2)

    val PrimaryGradient = listOf(Color(0xFF8B4BFF), Primary)
    val PrimaryBtnGradient = listOf(PrimaryMid, Primary)

}

object T {
    val H1 = TextStyle(fontSize = 21.sp, lineHeight = 28.sp, fontWeight = FontWeight.Bold, letterSpacing = (-0.4).sp, color = C.Ink)
    val H2 = TextStyle(fontSize = 20.sp, lineHeight = 27.sp, fontWeight = FontWeight.Bold, letterSpacing = (-0.35).sp, color = C.Ink)
    val Title = TextStyle(fontSize = 16.sp, lineHeight = 22.sp, fontWeight = FontWeight.Bold, letterSpacing = (-0.2).sp, color = C.Ink)
    val Body = TextStyle(fontSize = 14.sp, lineHeight = 21.sp, color = C.Ink)
    val Body2 = TextStyle(fontSize = 13.5.sp, lineHeight = 20.sp, color = C.Ink)
    val Small = TextStyle(fontSize = 12.5.sp, lineHeight = 19.sp, color = C.Ink2)
    val Small3 = TextStyle(fontSize = 12.sp, lineHeight = 18.sp, color = C.Ink3)
    val Tiny = TextStyle(fontSize = 11.sp, lineHeight = 17.sp, color = C.Ink3)
    val Micro = TextStyle(fontSize = 10.5.sp, lineHeight = 16.sp, color = C.Ink3)
    val Label = TextStyle(fontSize = 11.sp, lineHeight = 16.sp, fontWeight = FontWeight.Medium, color = C.Ink3)
    val Section = TextStyle(fontSize = 13.sp, lineHeight = 18.sp, fontWeight = FontWeight.Bold, color = C.Ink)
    val StatValue = TextStyle(fontSize = 17.sp, lineHeight = 22.sp, fontWeight = FontWeight.Bold, color = C.Ink)
}

fun Modifier.card(
    radius: Dp = 14.dp,
    bg: Color = Color.White,
    line: Color = C.LineSoft,
    elevation: Dp = 1.dp
): Modifier {
    val shape = RoundedCornerShape(radius)
    return this
        .shadow(elevation, shape, clip = false)
        .clip(shape)
        .background(bg)
        .border(1.dp, line, shape)
}

/** 点击态（用默认波纹，不改变布局与尺寸） */
fun Modifier.pressable(enabled: Boolean = true, onClick: () -> Unit): Modifier =
    this.clickable(enabled = enabled, onClick = onClick)
