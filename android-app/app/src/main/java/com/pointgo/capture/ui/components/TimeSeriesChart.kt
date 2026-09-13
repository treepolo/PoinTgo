package com.pointgo.capture.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.dp
import kotlin.math.abs
import kotlin.math.max

@Composable
fun TimeSeriesChart(
    values: List<Float>,
    threshold: Float? = null,
    modifier: Modifier = Modifier,
) {
    val shape = RoundedCornerShape(16.dp)
    // Resolve theme colors in the composable scope; Canvas draw lambdas are not
    // composable and therefore cannot read MaterialTheme directly.
    val surfaceVariant = MaterialTheme.colorScheme.surfaceVariant
    val primaryColor = MaterialTheme.colorScheme.primary
    val errorColor = MaterialTheme.colorScheme.error
    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(220.dp)
            .clip(shape)
            .background(surfaceVariant),
    ) {
        Canvas(Modifier.fillMaxWidth().height(220.dp)) {
            val left = 28f
            val right = size.width - 12f
            val top = 16f
            val bottom = size.height - 24f
            val centerY = (top + bottom) / 2f
            val usableWidth = (right - left).coerceAtLeast(1f)
            val usableHeight = (bottom - top).coerceAtLeast(1f)
            val maxMagnitude = max(
                1f,
                values.maxOfOrNull { abs(it) } ?: 1f,
            )
            val gridColor = Color.Gray.copy(alpha = 0.22f)
            repeat(4) { row ->
                val y = top + usableHeight * row / 3f
                drawLine(gridColor, Offset(left, y), Offset(right, y), strokeWidth = 1f)
            }
            drawLine(
                gridColor,
                Offset(left, centerY),
                Offset(right, centerY),
                strokeWidth = 1.5f,
            )
            if (values.size > 1) {
                val path = Path()
                values.forEachIndexed { index, value ->
                    val x = left + usableWidth * index / (values.lastIndex.toFloat())
                    val y = centerY - (value / maxMagnitude) * (usableHeight / 2f)
                    if (index == 0) path.moveTo(x, y) else path.lineTo(x, y)
                }
                drawPath(
                    path = path,
                    color = primaryColor,
                    style = Stroke(width = 3f, cap = StrokeCap.Round),
                )
            }
            threshold?.takeIf { it > 0f }?.let { limit ->
                val y = centerY - (limit / maxMagnitude).coerceIn(-1f, 1f) * (usableHeight / 2f)
                drawLine(
                    color = errorColor,
                    start = Offset(left, y),
                    end = Offset(right, y),
                    strokeWidth = 2f,
                    cap = StrokeCap.Round,
                )
            }
        }
    }
}
