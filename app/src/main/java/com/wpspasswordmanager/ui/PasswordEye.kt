package com.wpspasswordmanager.ui

import android.content.Context
import android.text.InputType
import android.util.TypedValue
import android.view.MotionEvent
import android.widget.EditText
import androidx.core.content.ContextCompat
import com.wpspasswordmanager.R

/**
 * 密码输入框「小眼睛」切换：在 EditText 尾部显示眼睛图标，点击切换明文/密文。
 * 适用于所有密码输入框（登录、强制改密、自助改密等）。
 */
object PasswordEye {

    fun attach(editText: EditText) {
        var visible = false
        val context = editText.context

        // 记录原字体，inputType 切换会重置为等宽字体，需恢复
        val originalTypeface = editText.typeface

        fun updateIcon() {
            val res = if (visible) R.drawable.ic_eye_open else R.drawable.ic_eye_closed
            val icon = ContextCompat.getDrawable(context, res)
            editText.setCompoundDrawablesWithIntrinsicBounds(null, null, icon, null)
        }

        // 图标贴近右缘：小 padding + 图标与文字间距
        editText.setPadding(editText.paddingLeft, editText.paddingTop, dp(context, 4f).toInt(), editText.paddingBottom)
        editText.compoundDrawablePadding = dp(context, 8f).toInt()
        updateIcon()

        editText.setOnTouchListener { v, event ->
            if (event.action == MotionEvent.ACTION_UP) {
                val icon = editText.compoundDrawables[2]
                // 命中区域 = compoundPaddingRight（padding + 图标宽 + 间距）整条竖带
                if (icon != null && event.x >= editText.width - editText.compoundPaddingRight) {
                    visible = !visible
                    editText.inputType = if (visible) {
                        InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_VISIBLE_PASSWORD
                    } else {
                        InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_PASSWORD
                    }
                    editText.typeface = originalTypeface
                    editText.setSelection(editText.text.length)
                    updateIcon()
                    v.performClick()
                    true
                } else {
                    false
                }
            } else {
                false
            }
        }
    }

    private fun dp(context: Context, value: Float): Float =
        TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, value, context.resources.displayMetrics)
}
