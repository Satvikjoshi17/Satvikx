package com.satvik.satvikx.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path

/**
 * An equilateral play triangle rendered on Canvas with optical centering.
 *
 * Mathematically nudged to the right by ~8% of width so that its visual center of mass
 * perfectly aligns with the geometric center of any enclosing circle or button.
 */
@Composable
fun OpticallyCenteredPlayIcon(
    modifier: Modifier = Modifier,
    tint: Color = Color.Black
) {
    Canvas(modifier = modifier) {
        val w = size.width
        val h = size.height

        // Symmetrical play triangle
        val triHeight = minOf(w, h) * 0.75f
        val triWidth = triHeight * 0.866025f // Equilateral ratio

        val top = (h - triHeight) / 2f
        val bottom = (h + triHeight) / 2f
        val centerY = h / 2f

        // Optical offset to compensate for centroid of mass:
        val opticalOffset = triWidth * 0.08f
        val left = ((w - triWidth) / 2f) + opticalOffset
        val right = left + triWidth

        val path = Path().apply {
            moveTo(left, top)
            lineTo(right, centerY)
            lineTo(left, bottom)
            close()
        }
        drawPath(path, color = tint)
    }
}
