package com.satvik.satvikx.ui.components

import android.graphics.CornerPathEffect
import androidx.compose.foundation.Canvas
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Paint
import androidx.compose.ui.graphics.PaintingStyle
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.asAndroidPath
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.nativeCanvas

/**
 * An equilateral play triangle rendered on Canvas with optical centering.
 *
 * Mathematically centered at the triangle's visual centroid of mass (offset by ~15% of width)
 * so that its visual weight perfectly aligns with the geometric center of any enclosing circle.
 */
@Composable
fun OpticallyCenteredPlayIcon(
    modifier: Modifier = Modifier,
    tint: Color = Color.Black
) {
    Canvas(modifier = modifier) {
        val w = size.width
        val h = size.height

        // Symmetrical equilateral play triangle
        val triHeight = minOf(w, h) * 0.72f
        val triWidth = triHeight * 0.866025f // Equilateral ratio

        val top = (h - triHeight) / 2f
        val bottom = (h + triHeight) / 2f
        val centerY = h / 2f

        // Centroid of equilateral triangle is at 1/3 from base (left edge).
        // To place centroid exactly at w/2, left must be (w/2) - (triWidth/3).
        // Left_geometric is (w - triWidth)/2 = (w/2) - (triWidth/2).
        // Difference is triWidth/6 (~16.67%). We use 14.5% for visual perfection.
        val opticalOffset = triWidth * 0.145f
        val left = ((w - triWidth) / 2f) + opticalOffset
        val right = left + triWidth

        val path = Path().apply {
            moveTo(left, top)
            lineTo(right, centerY)
            lineTo(left, bottom)
            close()
        }

        drawIntoCanvas { canvas ->
            val nativePaint = android.graphics.Paint().apply {
                isAntiAlias = true
                style = android.graphics.Paint.Style.FILL
                color = android.graphics.Color.argb(
                    (tint.alpha * 255).toInt(),
                    (tint.red * 255).toInt(),
                    (tint.green * 255).toInt(),
                    (tint.blue * 255).toInt()
                )
                // Subtle corner rounding for Spotify-grade premium aesthetic
                pathEffect = CornerPathEffect(triHeight * 0.15f)
            }
            canvas.nativeCanvas.drawPath(path.asAndroidPath(), nativePaint)
        }
    }
}

