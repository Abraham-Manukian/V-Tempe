package com.vtempe.ui.screens

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/** Google's four-colour "G" mark, drawn as a ring (no bitmap asset needed) — used on the
 *  "Continue with Google" button so it reads as Google at a glance instead of a bare label. */
@Composable
fun GoogleLogo(size: Dp = 18.dp, modifier: Modifier = Modifier) {
    Canvas(modifier = modifier.size(size)) {
        val strokeWidth = this.size.minDimension * 0.24f
        val radius = (this.size.minDimension - strokeWidth) / 2f
        val topLeft = Offset(center.x - radius, center.y - radius)
        val arcSize = Size(radius * 2, radius * 2)
        val stroke = Stroke(width = strokeWidth, cap = StrokeCap.Butt)

        drawArc(Color(0xFF4285F4), startAngle = -50f, sweepAngle = 100f, useCenter = false, topLeft = topLeft, size = arcSize, style = stroke)
        drawArc(Color(0xFF34A853), startAngle = 52f, sweepAngle = 88f, useCenter = false, topLeft = topLeft, size = arcSize, style = stroke)
        drawArc(Color(0xFFFBBC05), startAngle = 142f, sweepAngle = 76f, useCenter = false, topLeft = topLeft, size = arcSize, style = stroke)
        drawArc(Color(0xFFEA4335), startAngle = 220f, sweepAngle = 88f, useCenter = false, topLeft = topLeft, size = arcSize, style = stroke)

        // The "G" opens to the right — a short blue bar closes the ring's gap into a flag shape.
        drawRect(
            Color(0xFF4285F4),
            topLeft = Offset(center.x - strokeWidth * 0.1f, center.y - strokeWidth / 2f),
            size = Size(radius + strokeWidth * 0.1f, strokeWidth)
        )
    }
}
