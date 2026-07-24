package com.vtempe.core.designsystem.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlin.math.min
import kotlin.math.roundToInt

@Composable
fun BarChart(
    data: List<Int>,
    modifier: Modifier = Modifier,
    barColor: Color? = null,
    barWidth: Dp = 22.dp,
    spacing: Dp = 14.dp,
    /** Optional short label per bar (e.g. day-of-week), drawn under the axis. */
    labels: List<String>? = null,
    /** Draws the numeric value above each non-zero bar so the chart is legible without a tap. */
    showValues: Boolean = true,
    /** When set, bars become tappable and this fires with the tapped bar's index. */
    onBarClick: ((Int) -> Unit)? = null,
    /** Index of the currently selected bar — drawn highlighted. */
    selectedIndex: Int? = null,
) {
    val resolvedBarColor = barColor ?: MaterialTheme.colorScheme.primary
    val trackColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.06f)
    val labelColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
    val selectedLabelColor = resolvedBarColor
    val valueColor = MaterialTheme.colorScheme.onSurface
    val maxVal = (data.maxOrNull() ?: 0).coerceAtLeast(1)
    val textMeasurer = rememberTextMeasurer()
    val valueStyle = TextStyle(fontSize = 11.sp, fontWeight = FontWeight.Bold, color = valueColor)
    val labelStyle = TextStyle(fontSize = 11.sp, fontWeight = FontWeight.Medium, color = labelColor)
    val selectedLabelStyle = TextStyle(fontSize = 11.sp, fontWeight = FontWeight.Bold, color = selectedLabelColor)

    val tapModifier = if (onBarClick != null) {
        Modifier.pointerInput(data.size) {
            detectTapGestures { offset ->
                val barW = barWidth.toPx()
                val gap = spacing.toPx()
                val total = data.size * barW + (data.size - 1).coerceAtLeast(0) * gap
                val startX = (size.width - total) / 2f
                val idx = ((offset.x - startX) / (barW + gap)).toInt()
                if (idx in data.indices) onBarClick(idx)
            }
        }
    } else Modifier

    Canvas(modifier = modifier.fillMaxWidth().height(150.dp).then(tapModifier)) {
        val widthPx = size.width
        val topPad = 22.dp.toPx()
        val bottomPad = if (labels != null) 20.dp.toPx() else 4.dp.toPx()
        val chartHeight = size.height - topPad - bottomPad
        val barW = barWidth.toPx()
        val gap = spacing.toPx()
        val totalBarsWidth = data.size * barW + (data.size - 1).coerceAtLeast(0) * gap
        var x = (widthPx - totalBarsWidth) / 2f
        val baselineY = topPad + chartHeight
        val radius = CornerRadius(min(barW / 2.5f, 10f), min(barW / 2.5f, 10f))

        // Baseline
        drawLine(
            color = trackColor,
            start = Offset(0f, baselineY),
            end = Offset(widthPx, baselineY),
            strokeWidth = 1.dp.toPx(),
        )

        data.forEachIndexed { i, value ->
            val isSelected = i == selectedIndex
            // A selected bar reads at full strength; otherwise the tallest bar is emphasised.
            val isStrong = isSelected || (selectedIndex == null && value == maxVal && value > 0)
            val h = ((value.toFloat() / maxVal.toFloat()) * chartHeight).coerceAtLeast(if (value > 0) 6f else 0f)
            val top = baselineY - h
            if (value > 0) {
                drawRoundRect(
                    brush = Brush.verticalGradient(
                        colors = if (isStrong) listOf(resolvedBarColor, resolvedBarColor.copy(alpha = 0.75f))
                        else listOf(resolvedBarColor.copy(alpha = 0.55f), resolvedBarColor.copy(alpha = 0.35f)),
                        startY = top,
                        endY = baselineY,
                    ),
                    topLeft = Offset(x, top),
                    size = Size(barW, h),
                    cornerRadius = radius,
                )
                // A dot above the selected bar marks the current selection, Apple-Health style.
                if (isSelected) {
                    drawCircle(color = resolvedBarColor, radius = 3.dp.toPx(), center = Offset(x + barW / 2f, top - 12.dp.toPx()))
                }
            } else {
                // Zero-value placeholder so the day still reads as "present" instead of vanishing.
                drawRoundRect(
                    color = trackColor,
                    topLeft = Offset(x, baselineY - 4.dp.toPx()),
                    size = Size(barW, 4.dp.toPx()),
                    cornerRadius = CornerRadius(2.dp.toPx(), 2.dp.toPx()),
                )
            }

            if (showValues && value > 0) {
                drawCenteredText(textMeasurer, value.toString(), valueStyle, centerX = x + barW / 2f, bottomY = top - 6.dp.toPx())
            }
            labels?.getOrNull(i)?.let { label ->
                drawCenteredText(textMeasurer, label, if (isSelected) selectedLabelStyle else labelStyle, centerX = x + barW / 2f, bottomY = size.height)
            }

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
    /** Draws the numeric value above the first and last point, so a trend reads without a tap. */
    showEndValues: Boolean = true,
) {
    if (values.isEmpty()) return
    val color = lineColor ?: MaterialTheme.colorScheme.secondary
    val textMeasurer = rememberTextMeasurer()
    val valueColor = MaterialTheme.colorScheme.onSurface
    val valueStyle = TextStyle(fontSize = 11.sp, fontWeight = FontWeight.Bold, color = valueColor)

    Canvas(modifier = modifier.fillMaxWidth().height(160.dp)) {
        val topPad = 24.dp.toPx()
        val bottomPad = 8.dp.toPx()
        val w = size.width
        val h = size.height - topPad - bottomPad
        val min = values.minOrNull() ?: 0f
        val max = values.maxOrNull() ?: 1f
        val range = (max - min).takeIf { it > 0f } ?: 1f
        val stepX = w / (values.size - 1).coerceAtLeast(1)

        fun yOf(v: Float) = topPad + h - ((v - min) / range) * h

        val path = Path()
        val points = values.mapIndexed { i, v -> Offset(stepX * i, yOf(v)) }
        points.forEachIndexed { i, p -> if (i == 0) path.moveTo(p.x, p.y) else path.lineTo(p.x, p.y) }

        // Soft fill under the line so the trend reads at a glance.
        val fillPath = Path().apply {
            addPath(path)
            lineTo(points.last().x, topPad + h)
            lineTo(points.first().x, topPad + h)
            close()
        }
        drawPath(
            path = fillPath,
            brush = Brush.verticalGradient(
                colors = listOf(color.copy(alpha = 0.22f), color.copy(alpha = 0f)),
                startY = topPad,
                endY = topPad + h,
            ),
        )

        drawPath(path = path, color = color, style = Stroke(width = strokeWidth.toPx(), cap = StrokeCap.Round))

        points.forEachIndexed { i, p ->
            val isEnd = i == 0 || i == points.lastIndex
            drawCircle(color = Color.White, radius = if (isEnd) 6.dp.toPx() else 4.dp.toPx(), center = p)
            drawCircle(
                color = color,
                radius = if (isEnd) 6.dp.toPx() else 4.dp.toPx(),
                center = p,
                style = Stroke(width = 2.dp.toPx()),
            )
            if (isEnd && showEndValues) {
                drawCenteredText(textMeasurer, values[i].roundToInt().toString(), valueStyle, centerX = p.x, bottomY = p.y - 10.dp.toPx())
            }
        }
    }
}

private fun DrawScope.drawCenteredText(
    textMeasurer: TextMeasurer,
    text: String,
    style: TextStyle,
    centerX: Float,
    bottomY: Float,
) {
    val layout = textMeasurer.measure(text, style)
    drawText(
        textLayoutResult = layout,
        topLeft = Offset(centerX - layout.size.width / 2f, bottomY - layout.size.height),
    )
}
