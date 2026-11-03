package com.wpspasswordmanager.ui.compose

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

enum class BtnStyle { Primary, Ghost, Soft, Danger }

@Composable
fun AppButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    style: BtnStyle = BtnStyle.Primary,
    height: Dp = 46.dp,
    fontSize: TextUnit = 15.sp,
    textColor: Color? = null,
    borderColor: Color? = null,
    leading: (@Composable () -> Unit)? = null
) {
    val shape = RoundedCornerShape(12.dp)
    val fg = textColor ?: when (style) {
        BtnStyle.Primary, BtnStyle.Danger -> Color.White
        BtnStyle.Ghost -> C.Ink2
        BtnStyle.Soft -> C.PrimaryDark
    }
    val bgModifier = when (style) {
        BtnStyle.Primary -> Modifier.background(Brush.linearGradient(C.PrimaryBtnGradient), shape)
        BtnStyle.Danger -> Modifier.background(C.Err, shape)
        BtnStyle.Soft -> Modifier.background(C.PrimarySoft, shape)
        BtnStyle.Ghost -> Modifier.background(Color.White, shape)
    }
    Box(
        modifier = modifier
            .alpha(if (enabled) 1f else 0.42f)
            .then(if (style == BtnStyle.Primary && enabled) Modifier.shadow(8.dp, shape, clip = false) else Modifier)
            .clip(shape)
            .then(bgModifier)
            .then(
                if (style == BtnStyle.Ghost) {
                    Modifier.border(1.dp, borderColor ?: Color(0xFFDCDCE4), shape)
                } else if (borderColor != null) {
                    Modifier.border(1.dp, borderColor, shape)
                } else Modifier
            )
            .pressable(enabled, onClick)
            .height(height),
        contentAlignment = Alignment.Center
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.Center) {
            if (leading != null) {
                leading()
                Spacer(Modifier.width(7.dp))
            }
            Text(text, style = T.Body.copy(fontSize = fontSize, fontWeight = FontWeight.SemiBold, color = fg))
        }
    }
}

@Composable
fun IconBtn(
    paths: List<String>,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    size: Dp = 34.dp,
    iconSize: Dp = 19.dp,
    tint: Color = C.Ink2,
    background: Color? = null,
    strokeWidth: Float = 2f
) {
    Box(
        modifier = modifier
            .size(size)
            .clip(RoundedCornerShape(10.dp))
            .then(if (background != null) Modifier.background(background) else Modifier)
            .pressable(onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        SvgIcon(paths, size = iconSize, tint = tint, strokeWidth = strokeWidth)
    }
}

@Composable
fun AppField(
    value: String,
    onValueChange: (String) -> Unit,
    placeholder: String,
    modifier: Modifier = Modifier,
    height: Dp = 50.dp,
    error: Boolean = false,
    ok: Boolean = false,
    highlighted: Boolean = false,
    enabled: Boolean = true,
    password: Boolean = false,
    keyboard: KeyboardType = KeyboardType.Text,
    center: Boolean = false,
    prefix: String? = null,
    trailing: (@Composable () -> Unit)? = null,
    textStyle: TextStyle = T.Body,
    imeAction: androidx.compose.ui.text.input.ImeAction = androidx.compose.ui.text.input.ImeAction.Done,
    onImeAction: (() -> Unit)? = null
) {
    var focused by remember { mutableStateOf(false) }
    val active = focused || highlighted
    val borderColor = when {
        error -> C.Err
        ok -> C.Ok
        active -> C.Primary
        else -> C.Field
    }
    Row(
        modifier = modifier
            .heightIn(min = height)
            .clip(RoundedCornerShape(12.dp))
            .background(if (enabled) Color.White else Color(0xFFF7F7FA))
            .border(if (active || error) 1.5.dp else 1.dp, borderColor, RoundedCornerShape(12.dp))
            .padding(horizontal = 13.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        if (prefix != null) {
            Text(prefix, style = T.Body.copy(fontSize = 13.5.sp, color = C.Placeholder))
            Spacer(Modifier.width(3.dp))
        }
        BasicTextField(
            value = value,
            onValueChange = onValueChange,
            enabled = enabled,
            singleLine = true,
            textStyle = textStyle.copy(
                color = if (enabled) C.Ink else C.Ink3,
                textAlign = if (center) TextAlign.Center else TextAlign.Start
            ),
            cursorBrush = SolidColor(C.Primary),
            keyboardOptions = KeyboardOptions(keyboardType = keyboard, imeAction = imeAction),
            keyboardActions = KeyboardActions(onDone = { onImeAction?.invoke() }),
            visualTransformation = if (password) PasswordVisualTransformation() else VisualTransformation.None,
            decorationBox = { inner ->
                Box(contentAlignment = if (center) Alignment.Center else Alignment.CenterStart) {
                    if (value.isEmpty()) {
                        Text(
                            placeholder,
                            style = textStyle.copy(color = C.Placeholder),
                            textAlign = if (center) TextAlign.Center else TextAlign.Start,
                            maxLines = 1
                        )
                    }
                    inner()
                }
            },
            modifier = Modifier
                .weight(1f)
                .onFocusChanged { focused = it.isFocused }
        )
        if (trailing != null) {
            Spacer(Modifier.width(8.dp))
            trailing()
        }
    }
}

@Composable
fun FieldError(text: String, modifier: Modifier = Modifier) {
    Row(modifier = modifier.padding(top = 6.dp)) {
        SvgIcon(I.info, size = 12.dp, tint = C.Err, strokeWidth = 2.2f)
        Spacer(Modifier.width(5.dp))
        Text(text, style = T.Micro.copy(color = C.Err, lineHeight = 16.sp))
    }
}

@Composable
fun Badge(
    text: String,
    bg: Color,
    fg: Color,
    modifier: Modifier = Modifier,
    height: Dp = 21.dp,
    fontSize: TextUnit = 10.5.sp
) {
    Box(
        modifier = modifier
            .height(height)
            .clip(RoundedCornerShape(999.dp))
            .background(bg)
            .padding(horizontal = 8.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(text, style = T.Micro.copy(fontSize = fontSize, color = fg, fontWeight = FontWeight.SemiBold))
    }
}

@Composable
fun Dot(color: Color, size: Dp = 6.dp) {
    Box(Modifier.size(size).clip(CircleShape).background(color))
}

@Composable
fun AppCheckbox(
    checked: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    indeterminate: Boolean = false,
    disabled: Boolean = false
) {
    val box: Color = when {
        disabled -> Color(0xFFF1F1F5)
        indeterminate -> C.Primary
        checked -> C.Primary
        else -> Color.White
    }
    val border = when {
        disabled -> Color(0xFFDCDCE4)
        checked || indeterminate -> C.Primary
        else -> Color(0xFFC9C9D2)
    }
    Box(
        modifier = modifier
            .size(20.dp)
            .clip(RoundedCornerShape(6.dp))
            .background(box)
            .border(1.5.dp, border, RoundedCornerShape(6.dp))
            .pressable(!disabled, onClick),
        contentAlignment = Alignment.Center
    ) {
        if (indeterminate) {
            Box(Modifier.width(9.dp).height(2.dp).clip(CircleShape).background(Color.White))
        } else if (checked) {
            SvgIcon(I.check, size = 11.dp, tint = Color.White, strokeWidth = 3.4f)
        }
    }
}

@Composable
fun AppSwitch(on: Boolean, onToggle: () -> Unit) {
    Box(
        modifier = Modifier
            .size(width = 42.dp, height = 26.dp)
            .clip(RoundedCornerShape(999.dp))
            .background(if (on) C.Primary else Color(0xFFDCDCE4))
            .pressable(onClick = onToggle)
            .padding(3.dp),
        contentAlignment = if (on) Alignment.CenterEnd else Alignment.CenterStart
    ) {
        Box(Modifier.size(20.dp).clip(CircleShape).background(Color.White))
    }
}

@Composable
fun AppBar(
    title: String,
    onBack: (() -> Unit)? = null,
    trailing: (@Composable () -> Unit)? = null
) {
    Row(
        Modifier
            .fillMaxWidth()
            .height(52.dp)
            .padding(horizontal = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        if (onBack != null) {
            IconBtn(I.back, onClick = onBack, size = 36.dp, iconSize = 20.dp, tint = C.Ink)
        } else {
            Spacer(Modifier.width(8.dp))
        }
        Text(title, style = T.Title, modifier = Modifier.weight(1f))
        if (trailing != null) trailing()
    }
}

@Composable
fun DashedDivider(color: Color, modifier: Modifier = Modifier, dash: Dp = 4.dp, gap: Dp = 3.dp) {
    androidx.compose.foundation.Canvas(modifier.fillMaxWidth().height(1.dp)) {
        val d = dash.toPx()
        val g = gap.toPx()
        var x = 0f
        while (x < size.width) {
            drawLine(
                color = color,
                start = androidx.compose.ui.geometry.Offset(x, 0f),
                end = androidx.compose.ui.geometry.Offset((x + d).coerceAtMost(size.width), 0f),
                strokeWidth = size.height
            )
            x += d + g
        }
    }
}

@Composable
fun ToastBar(text: String) {
    Box(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = 24.dp),
        contentAlignment = Alignment.Center
    ) {
        Box(
            Modifier
                .clip(RoundedCornerShape(12.dp))
                .background(Color(0xF01B1B1F))
                .padding(horizontal = 14.dp, vertical = 10.dp)
        ) {
            Text(text, style = T.Small.copy(color = Color.White))
        }
    }
}

/** 文档类型角标 */
@Composable
fun DocBadge(fmt: String, size: Dp = 38.dp) {
    val (bg, fg, label) = when (fmt.lowercase()) {
        "docx", "doc" -> Triple(Color(0xFFE7F0FB), Color(0xFF1A4E8A), "DOC")
        "xlsx", "xls" -> Triple(Color(0xFFE6F4EA), Color(0xFF1D6B33), "XLS")
        "pptx", "ppt" -> Triple(Color(0xFFFDECE8), Color(0xFFB3402F), "PPT")
        "pdf" -> Triple(Color(0xFFFCEBEA), Color(0xFFB3402F), "PDF")
        else -> Triple(Color(0xFFF1F1F5), Color(0xFF6A6A75), "TXT")
    }
    Box(
        Modifier
            .size(size)
            .clip(RoundedCornerShape(10.dp))
            .background(bg),
        contentAlignment = Alignment.Center
    ) {
        Text(label, style = T.Micro.copy(fontSize = 9.sp, fontWeight = FontWeight.ExtraBold, color = fg, letterSpacing = 0.2.sp))
    }
}

/** 底部抽屉容器：遮罩 + 上滑面板（对应原型的 .sheet） */
@Composable
fun BottomSheet(
    onDismiss: () -> Unit = {},
    heightFraction: Float? = null,
    content: @Composable ColumnScope.() -> Unit
) {
    Box(Modifier.fillMaxSize()) {
        Box(
            Modifier
                .fillMaxSize()
                .background(Color(0x8C0F0A1E))
                .pressable { onDismiss() }
        )
        Column(
            Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .then(if (heightFraction != null) Modifier.fillMaxHeight(heightFraction) else Modifier)
                .clip(RoundedCornerShape(topStart = 20.dp, topEnd = 20.dp))
                .background(Color.White)
                .imePadding()
                .navigationBarsPadding()
        ) {
            Box(
                Modifier
                    .padding(top = 8.dp, bottom = 2.dp)
                    .align(Alignment.CenterHorizontally)
                    .width(38.dp)
                    .height(4.dp)
                    .clip(RoundedCornerShape(2.dp))
                    .background(Color(0xFFE0E0E8))
            )
            content()
        }
    }
}

@Composable
fun DialogCard(
    width: Dp = 302.dp,
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit
) {
    Column(
        modifier = modifier
            .width(width)
            .shadow(24.dp, RoundedCornerShape(20.dp), clip = false)
            .clip(RoundedCornerShape(20.dp))
            .background(Color.White)
            .padding(start = 18.dp, end = 18.dp, top = 22.dp, bottom = 16.dp),
        content = content
    )
}
