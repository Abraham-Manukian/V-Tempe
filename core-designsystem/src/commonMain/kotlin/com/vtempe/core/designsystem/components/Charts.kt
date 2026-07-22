package com.vtempe.core.designsystem.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import kotlin.math.min

@Composable
fun BarChart(
    data: List<Int>,
    modifier: Modifier = Modifier,
    barColor: Color? = null,
    barWidth: Dp = 16.dp,
    spacing: Dp = 12.dp,
) {
    val resolvedBarColor = barColor ?: MaterialTheme.colorScheme.primary
    val maxVal = (data.maxOrNull() ?: 0).coerceAtLeast(1)
    Canvas(modifier = modifier.fillMaxWidth().height(120.dp)) {
        val widthPx = size.width
        val heightPx = size.height
        val barW = barWidth.toPx()
        val gap = spacing.toPx()
        val totalBarsWidth = data.size * barW + (data.size - 1) * gap
        var x = (widthPx - totalBarsWidth) / 2f
        data.forEach { value ->
            val h = (value.toFloat() / maxVal.toFloat()) * heightPx
            drawRect(
                color = resolvedBarColor,
                topLeft = Offset(x, heightPx - h),
                size = androidx.compose.ui.geometry.Size(barW, h)
            )
            x += barW + gap
        }
    }
}

@Composable
fun RingChart(
    values: List<Float>,
    colors: List<Color>,
    modifier: Modifier = Modifier,
    strokeWidth: Dp = 20.dp,
    /** Degrees of empty space between segments — 0 for a solid ring, a few degrees for a
     *  "separated slices" look. */
    gapDegrees: Float = 4f,
) {
    val total = values.sum().coerceAtLeast(0.0001f)
    val sweepAngles = values.map { v -> (v / total) * 360f }
    val fallbackColor = MaterialTheme.colorScheme.primary
    val trackColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f)
    Canvas(modifier = modifier.fillMaxWidth().height(160.dp)) {
        // drawArc's default bounding box is the full canvas rect — on a wide, short canvas
        // (fillMaxWidth() against a fixed 160dp height) that stretched the "ring" into a
        // flattened oval instead of a circle. Inscribe a proper square instead, sized by the
        // smaller dimension and centered.
        val strokePx = strokeWidth.toPx()
        val diameter = min(size.width, size.height) - strokePx
        val topLeft = Offset((size.width - diameter) / 2f, (size.height - diameter) / 2f)
        val arcSize = Size(diameter, diameter)
        val stroke = Stroke(width = strokePx, cap = StrokeCap.Round)

        // Faint full-circle track behind the segments, so the chart still reads as "a ring"
        // even when one value is 0 (e.g. no fat logged yet) instead of just vanishing.
        drawArc(
            color = trackColor,
            startAngle = 0f,
            sweepAngle = 360f,
            useCenter = false,
            topLeft = topLeft,
            size = arcSize,
            style = stroke
        )

        var start = -90f
        sweepAngles.forEachIndexed { i, sweep ->
            if (sweep > 0f) {
                val gap = if (sweepAngles.count { it > 0f } > 1) gapDegrees else 0f
                drawArc(
                    color = colors.getOrNull(i) ?: fallbackColor,
                    startAngle = start + gap / 2f,
                    sweepAngle = (sweep - gap).coerceAtLeast(0f),
                    useCenter = false,
                    topLeft = topLeft,
                    size = arcSize,
                    style = stroke
                )
            }
            start += sweep
        }
    }
}

@Composable
fun LineChart(
    values: List<Float>,
    modifier: Modifier = Modifier,
    lineColor: Color? = null,
    strokeWidth: Dp = 3.dp,
) {
    if (values.isEmpty()) return
    val color = lineColor ?: MaterialTheme.colorScheme.secondary
    Canvas(modifier = modifier.fillMaxWidth().height(160.dp)) {
        val w = size.width
        val h = size.height
        val min = values.minOrNull() ?: 0f
        val max = values.maxOrNull() ?: 1f
        val range = (max - min).takeIf { it > 0f } ?: 1f
        val stepX = w / (values.size - 1).coerceAtLeast(1)
        val path = Path()
        values.forEachIndexed { i, v ->
            val x = stepX * i
            val y = h - ((v - min) / range) * h
            if (i == 0) path.moveTo(x, y) else path.lineTo(x, y)
        }
        drawPath(
            path = path,
            color = color,
            style = Stroke(width = strokeWidth.toPx())
        )
    }
}

