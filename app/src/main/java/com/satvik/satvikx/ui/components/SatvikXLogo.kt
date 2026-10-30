
package com.satvik.satvikx.ui.components

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.satvik.satvikx.R
import com.satvik.satvikx.ui.theme.ArcCyan
import com.satvik.satvikx.ui.theme.ArcCyanBright
import com.satvik.satvikx.ui.theme.PrimaryNeon
import com.satvik.satvikx.ui.theme.StarkCarbon
import com.satvik.satvikx.ui.theme.StarkGold
import com.satvik.satvikx.ui.theme.SurfaceDark

/**
 * High-tech Stark Holographic Logo component for SatvikX.
 * Renders the official app logo with an optional subtle Arc Reactor energy pulse.
 * Supports vector drawables or custom user-provided image files placed in res/drawable/ic_satvikx_logo.
 */
@Composable
fun SatvikXLogo(
    modifier: Modifier = Modifier,
    sizeDp: Dp = 40.dp,
    showGlow: Boolean = true
) {
    val infiniteTransition = rememberInfiniteTransition(label = "LogoPulse")
    val pulseAlpha by infiniteTransition.animateFloat(
        initialValue = 0.4f,
        targetValue = 0.95f,
        animationSpec = infiniteRepeatable(
            animation = tween(1800, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "AlphaPulse"
    )

    Box(
        modifier = modifier.size(sizeDp),
        contentAlignment = Alignment.Center
    ) {
        if (showGlow) {
            // Ambient Arc Glow Ring
            Box(
                modifier = Modifier
                    .size(sizeDp)
                    .clip(CircleShape)
                    .background(
                        Brush.radialGradient(
                            listOf(
                                ArcCyanBright.copy(alpha = 0.35f * pulseAlpha),
                                Color.Transparent
                            )
                        )
                    )
            )
        }

        Surface(
            shape = CircleShape,
            color = SurfaceDark,
            border = BorderStroke(
                width = 1.dp,
                brush = Brush.sweepGradient(
                    listOf(
                        ArcCyanBright.copy(alpha = pulseAlpha),
                        PrimaryNeon.copy(alpha = 0.6f),
                        StarkGold.copy(alpha = 0.4f),
                        ArcCyanBright.copy(alpha = pulseAlpha)
                    )
                )
            ),
            modifier = Modifier.size(sizeDp * 0.92f)
        ) {
            Box(
                modifier = Modifier.padding(sizeDp * 0.12f),
                contentAlignment = Alignment.Center
            ) {
                Image(
                    painter = painterResource(id = R.drawable.ic_satvikx_logo),
                    contentDescription = "SatvikX Logo",
                    modifier = Modifier.size(sizeDp * 0.68f)
                )
            }
        }
    }
}

/**
 * SatvikX Header Branding Banner.
 * Displays the holographic logo alongside the stylized SATVIKX typography and Stark HUD status.
 */
@Composable
fun SatvikXBrandHeader(
    modifier: Modifier = Modifier,
    logoSize: Dp = 38.dp,
    showSubtitle: Boolean = true
) {
    Row(
        modifier = modifier,
        verticalAlignment = Alignment.CenterVertically
    ) {
        SatvikXLogo(sizeDp = logoSize)

        Spacer(modifier = Modifier.width(10.dp))

        Column {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = "SATVIKX",
                    style = MaterialTheme.typography.titleLarge.copy(
                        fontFamily = FontFamily.Monospace,
                        fontWeight = FontWeight.ExtraBold,
                        letterSpacing = 1.2.sp,
                        color = Color.White
                    )
                )
                Spacer(modifier = Modifier.width(6.dp))
                Surface(
                    shape = RoundedCornerShape(4.dp),
                    color = ArcCyan.copy(alpha = 0.15f),
                    border = BorderStroke(0.5.dp, ArcCyanBright)
                ) {
                    Text(
                        text = "PRO",
                        style = MaterialTheme.typography.labelSmall.copy(
                            fontFamily = FontFamily.Monospace,
                            fontWeight = FontWeight.Bold,
                            fontSize = 8.sp,
                            color = ArcCyanBright
                        ),
                        modifier = Modifier.padding(horizontal = 4.dp, vertical = 1.dp)
                    )
                }
            }

            if (showSubtitle) {
                Text(
                    text = "MARK LXXXV • AUDIO CORE",
                    style = MaterialTheme.typography.labelSmall.copy(
                        fontFamily = FontFamily.Monospace,
                        fontWeight = FontWeight.SemiBold,
                        fontSize = 9.sp,
                        letterSpacing = 0.6.sp,
                        color = ArcCyan.copy(alpha = 0.75f)
                    )
                )
            }
        }
    }
}
