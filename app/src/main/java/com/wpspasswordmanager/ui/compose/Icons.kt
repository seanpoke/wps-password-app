package com.wpspasswordmanager.ui.compose

import android.graphics.Path
import android.graphics.RectF
import android.util.Log
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.asComposePath
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.core.graphics.PathParser

/**
 * 图标：复用原型 SVG 的 24×24 几何，但全部改写成**显式空格分隔**的安全写法
 * （Android 的 PathParser 对 “0 100 18” 这类紧凑弧标记会抛 RuntimeException）。
 * 圆用 `circle:cx,cy,r` 原语，避免手写弧。
 */
object I {
    val back = listOf("M15 18 L9 12 L15 6")
    val plus = listOf("M12 5 L12 19", "M5 12 L19 12")
    val folder = listOf(
        "M3 7.5 A2.5 2.5 0 0 1 5.5 5 L8.7 5 L10.5 7 L17.5 7 A2.5 2.5 0 0 1 20 9.5 " +
            "L20 16.5 A2.5 2.5 0 0 1 17.5 19 L5.5 19 A2.5 2.5 0 0 1 3 16.5 Z"
    )
    val info = listOf("circle:12,12,9", "M12 16 L12 12", "M12 8 L12 8.01")
    val warn = listOf("M12 3.5 L20 19 L4 19 Z", "M12 9.5 L12 13.5", "M12 16.5 L12 16.51")
    val check = listOf("M20 6 L9 17 L4 12")
    val chevronRight = listOf("M9 6 L15 12 L9 18")
    val shield = listOf(
        "M12 2.8 L19.2 5.8 L19.2 11.9 C19.2 16.5 16.1 19.6 12 21.2 C7.9 19.6 4.8 16.5 4.8 11.9 L4.8 5.8 Z",
        "M9 11.8 L11.2 14 L15.4 9.4"
    )
    val lock = listOf(
        "M6 10.5 L18 10.5 A2 2 0 0 1 20 12.5 L20 18.5 A2 2 0 0 1 18 20.5 " +
            "L6 20.5 A2 2 0 0 1 4 18.5 L4 12.5 A2 2 0 0 1 6 10.5 Z",
        "M8.5 10.5 L8.5 7.6 A3.5 3.5 0 0 1 15.5 7.6 L15.5 10.5"
    )
    val logout = listOf(
        "M9 21 L6 21 A2 2 0 0 1 4 19 L4 5 A2 2 0 0 1 6 3 L9 3",
        "M16 17 L21 12 L16 7",
        "M21 12 L9 12"
    )
    val refresh = listOf("M20 12 A8 8 0 1 1 17.7 6.4", "M20 4 L20 8 L16 8")
    val eye = listOf(
        "M2.5 12 C2.5 12 6 5.5 12 5.5 C18 5.5 21.5 12 21.5 12 C21.5 12 18 18.5 12 18.5 C6 18.5 2.5 12 2.5 12 Z",
        "circle:12,12,2.8"
    )
    val eyeOff = listOf(
        "M2.5 12 C2.5 12 6 5.5 12 5.5 C18 5.5 21.5 12 21.5 12 C21.5 12 18 18.5 12 18.5 C6 18.5 2.5 12 2.5 12 Z",
        "circle:12,12,2.8",
        "M4 4 L20 20"
    )
    val doc = listOf("M6 3 L13 3 L18 8 L18 21 L6 21 Z", "M13 3 L13 8 L18 8")
    val search = listOf("circle:11,11,6.5", "M16 16 L20.5 20.5")
    val wifi = listOf(
        "M5 12.5 A10 10 0 0 1 19 12.5",
        "M8.5 16 A5.2 5.2 0 0 1 15.5 16",
        "M12 19 L12 19.01"
    )
    val folderCheck = listOf(
        "M3 7.5 A2.5 2.5 0 0 1 5.5 5 L8.7 5 L10.5 7 L17.5 7 A2.5 2.5 0 0 1 20 9.5 " +
            "L20 16.5 A2.5 2.5 0 0 1 17.5 19 L5.5 19 A2.5 2.5 0 0 1 3 16.5 Z",
        "M8.8 13.6 L10.8 15.6 L14.8 11.2"
    )
    val display = listOf(
        "M5 4.5 L19 4.5 A1.5 1.5 0 0 1 20.5 6 L20.5 15 A1.5 1.5 0 0 1 19 16.5 " +
            "L5 16.5 A1.5 1.5 0 0 1 3.5 15 L3.5 6 A1.5 1.5 0 0 1 5 4.5 Z",
        "M8 20 L16 20",
        "M12 16.5 L12 20"
    )
    val key = listOf("circle:8.5,15.5,3.5", "M11 13 L20 4", "M17 7 L19.5 9.5", "M15 9 L17 11")
    val download = listOf("M12 3.5 L12 14.5", "M7.5 10.5 L12 15 L16.5 10.5", "M4.5 19.5 L19.5 19.5")
    val more = listOf("M12 5 L12 5.01", "M12 12 L12 12.01", "M12 19 L12 19.01")
    val pause = listOf("M9 6 L9 18", "M15 6 L15 18")
    val play = listOf("M8 5.5 L18 12 L8 18.5 Z")
    val linkOff = listOf("M4 4 L20 20", "M9.5 14.5 L14.5 9.5")
    val gear = listOf(
        "M4 7 L11 7", "M15 7 L20 7",
        "M4 12 L7 12", "M11 12 L20 12",
        "M4 17 L12 17", "M16 17 L20 17",
        "circle:13,7,2", "circle:9,12,2", "circle:14,17,2"
    )
}

private const val TAG = "SvgIcon"

private fun buildPath(d: String): Path? = runCatching {
    if (d.startsWith("circle:")) {
        val p = d.removePrefix("circle:").split(",")
        val cx = p[0].trim().toFloat()
        val cy = p[1].trim().toFloat()
        val r = p[2].trim().toFloat()
        Path().apply { addOval(RectF(cx - r, cy - r, cx + r, cy + r), Path.Direction.CW) }
    } else {
        PathParser.createPathFromPathData(d)
    }
}.onFailure {
    Log.e(TAG, "path 解析失败: $d · ${it.message}")
}.getOrNull()

@Composable
fun SvgIcon(
    paths: List<String>,
    size: Dp = 20.dp,
    tint: Color = C.Ink2,
    strokeWidth: Float = 2f,
    modifier: Modifier = Modifier
) {
    Canvas(modifier.size(size)) {
        val k = this.size.minDimension / 24f
        withTransform({ scale(k, k, Offset.Zero) }) {
            paths.forEach { d ->
                val p = buildPath(d) ?: return@forEach
                drawPath(
                    p.asComposePath(),
                    color = tint,
                    style = Stroke(width = strokeWidth, cap = StrokeCap.Round, join = StrokeJoin.Round)
                )
            }
        }
    }
}
