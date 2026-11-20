package com.satvik.satvikx.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.CloudDone
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Stars
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.satvik.satvikx.ui.theme.AccentPink
import com.satvik.satvikx.ui.theme.ArcCyan
import com.satvik.satvikx.ui.theme.ArcCyanBright
import com.satvik.satvikx.ui.theme.SpotifyGreen
import com.satvik.satvikx.ui.theme.StarkBorder
import com.satvik.satvikx.ui.theme.StarkGold
import com.satvik.satvikx.ui.theme.SurfaceDark
import com.satvik.satvikx.ui.theme.SurfaceElevated
import com.satvik.satvikx.ui.theme.TextPrimary
import com.satvik.satvikx.ui.theme.TextSecondary
import com.satvik.satvikx.ui.theme.TextTertiary

/**
 * Premium "What's New in v1.0.19" Update Showcase Dialog.
 * Displays high-tech Spotify/J.A.R.V.I.S. styling with tactile haptics,
 * feature highlights, and interactive user feedback pills.
 */
@Composable
fun WhatsNewDialog(
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier
) {
    val haptic = LocalHapticFeedback.current
    var selectedFeedback by remember { mutableStateOf<String?>(null) }
    var feedbackSubmitted by remember { mutableStateOf(false) }

    val feedbackOptions = listOf(
        "⚡ Blazing Fast",
        "🎨 Love Portraits",
        "💎 Perfect Sync",
        "🚀 Smooth Navigation"
    )

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(
            usePlatformDefaultWidth = false,
            dismissOnBackPress = true,
            dismissOnClickOutside = true
        )
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth(0.92f)
                .clip(RoundedCornerShape(26.dp))
                .background(SurfaceDark)
                .border(
                    BorderStroke(
                        1.dp,
                        Brush.verticalGradient(
                            listOf(
                                ArcCyanBright.copy(alpha = 0.55f),
                                ArcCyan.copy(alpha = 0.2f),
                                Color.Transparent
                            )
                        )
                    ),
                    RoundedCornerShape(26.dp)
                )
                .padding(20.dp)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState()),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                // Top Row: Version Tag & Close Button
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Surface(
                        shape = RoundedCornerShape(20.dp),
                        color = ArcCyan.copy(alpha = 0.15f),
                        border = BorderStroke(0.7.dp, ArcCyanBright.copy(alpha = 0.5f))
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(7.dp)
                                    .clip(CircleShape)
                                    .background(SpotifyGreen)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = "v1.0.19 UPDATE",
                                style = MaterialTheme.typography.labelSmall.copy(
                                    fontFamily = FontFamily.Monospace,
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 10.sp,
                                    color = ArcCyanBright
                                )
                            )
                        }
                    }

                    IconButton(
                        onClick = {
                            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                            onDismiss()
                        },
                        modifier = Modifier.size(30.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Close,
                            contentDescription = "Close",
                            tint = TextTertiary,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                }

                Spacer(modifier = Modifier.height(10.dp))

                // Brand Logo & Headline
                SatvikXLogo(sizeDp = 46.dp, showGlow = true)
                Spacer(modifier = Modifier.height(8.dp))

                Text(
                    text = "What's New in SatvikX",
                    style = MaterialTheme.typography.titleLarge.copy(
                        fontWeight = FontWeight.ExtraBold,
                        color = TextPrimary,
                        fontSize = 21.sp
                    )
                )
                Spacer(modifier = Modifier.height(3.dp))
                Text(
                    text = "A refined, ultra-fast auditory experience",
                    style = MaterialTheme.typography.bodySmall.copy(
                        color = TextSecondary,
                        fontSize = 12.5.sp
                    )
                )

                Spacer(modifier = Modifier.height(16.dp))

                // Feature Highlights
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    WhatsNewFeatureItem(
                        icon = Icons.Default.Stars,
                        iconColor = ArcCyanBright,
                        badgeText = "VISUAL",
                        badgeColor = ArcCyan,
                        title = "Deezer Studio Portraits",
                        description = "Crystal-clear artist photography and a dedicated Spotify-style artist page with top tracks and albums."
                    )

                    WhatsNewFeatureItem(
                        icon = Icons.Default.Home,
                        iconColor = SpotifyGreen,
                        badgeText = "NAVIGATION",
                        badgeColor = SpotifyGreen,
                        title = "Instant Home Snap-Back",
                        description = "Tapping Home on the bottom bar immediately returns to your feed, even while browsing artist discography."
                    )

                    WhatsNewFeatureItem(
                        icon = Icons.Default.Favorite,
                        iconColor = AccentPink,
                        badgeText = "LIBRARY",
                        badgeColor = AccentPink,
                        title = "Unified Liked Songs",
                        description = "Merged into a single canonical playlist with automated deduplication and cloud synchronization."
                    )

                    WhatsNewFeatureItem(
                        icon = Icons.Default.CloudDone,
                        iconColor = StarkGold,
                        badgeText = "SYNC",
                        badgeColor = StarkGold,
                        title = "Real-Time Callsign Sync",
                        description = "Profile callsigns and avatar edits now synchronize immediately to your Supabase account across devices."
                    )
                }

                Spacer(modifier = Modifier.height(16.dp))

                // Interactive Feedback Section
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(14.dp))
                        .background(SurfaceElevated.copy(alpha = 0.5f))
                        .border(BorderStroke(0.6.dp, StarkBorder), RoundedCornerShape(14.dp))
                        .padding(12.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "HOW DOES THIS UPDATE FEEL?",
                            style = MaterialTheme.typography.labelSmall.copy(
                                fontFamily = FontFamily.Monospace,
                                fontWeight = FontWeight.Bold,
                                fontSize = 10.sp,
                                color = ArcCyanBright
                            )
                        )
                        if (feedbackSubmitted) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(
                                    imageVector = Icons.Default.Check,
                                    contentDescription = null,
                                    tint = SpotifyGreen,
                                    modifier = Modifier.size(13.dp)
                                )
                                Spacer(modifier = Modifier.width(3.dp))
                                Text(
                                    text = "Noted!",
                                    style = MaterialTheme.typography.labelSmall.copy(
                                        color = SpotifyGreen,
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 10.sp
                                    )
                                )
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(8.dp))

                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        feedbackOptions.forEach { option ->
                            val isSelected = selectedFeedback == option
                            Surface(
                                shape = RoundedCornerShape(18.dp),
                                color = if (isSelected) SpotifyGreen.copy(alpha = 0.2f) else Color(0xFF1E2430),
                                border = BorderStroke(
                                    0.8.dp,
                                    if (isSelected) SpotifyGreen else StarkBorder
                                ),
                                modifier = Modifier
                                    .clip(RoundedCornerShape(18.dp))
                                    .clickable {
                                        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                        selectedFeedback = option
                                        feedbackSubmitted = true
                                    }
                            ) {
                                Text(
                                    text = option,
                                    style = MaterialTheme.typography.bodySmall.copy(
                                        fontSize = 11.5.sp,
                                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                                        color = if (isSelected) SpotifyGreen else TextSecondary
                                    ),
                                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)
                                )
                            }
                        }
                    }

                    AnimatedVisibility(
                        visible = feedbackSubmitted,
                        enter = fadeIn(),
                        exit = fadeOut()
                    ) {
                        Text(
                            text = "✨ Thank you! Your feedback keeps SatvikX finely tuned.",
                            style = MaterialTheme.typography.bodySmall.copy(
                                fontSize = 11.sp,
                                color = TextTertiary
                            ),
                            modifier = Modifier.padding(top = 8.dp)
                        )
                    }
                }

                Spacer(modifier = Modifier.height(18.dp))

                // Primary CTA Button
                Button(
                    onClick = {
                        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                        onDismiss()
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(48.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = SpotifyGreen,
                        contentColor = Color(0xFF121212)
                    ),
                    shape = RoundedCornerShape(14.dp),
                    border = BorderStroke(0.8.dp, ArcCyanBright.copy(alpha = 0.4f))
                ) {
                    Text(
                        text = "EXPLORE SATVIKX 1.0.19",
                        style = MaterialTheme.typography.bodyMedium.copy(
                            fontWeight = FontWeight.ExtraBold,
                            fontSize = 13.5.sp,
                            letterSpacing = 0.8.sp
                        )
                    )
                }
            }
        }
    }
}

@Composable
private fun WhatsNewFeatureItem(
    icon: ImageVector,
    iconColor: Color,
    badgeText: String,
    badgeColor: Color,
    title: String,
    description: String,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(SurfaceElevated.copy(alpha = 0.6f))
            .border(BorderStroke(0.5.dp, StarkBorder), RoundedCornerShape(14.dp))
            .padding(12.dp),
        verticalAlignment = Alignment.Top
    ) {
        Box(
            modifier = Modifier
                .size(36.dp)
                .clip(CircleShape)
                .background(iconColor.copy(alpha = 0.12f)),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = iconColor,
                modifier = Modifier.size(18.dp)
            )
        }

        Spacer(modifier = Modifier.width(12.dp))

        Column(modifier = Modifier.weight(1f)) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.bodyMedium.copy(
                        fontWeight = FontWeight.Bold,
                        color = TextPrimary,
                        fontSize = 13.sp
                    )
                )
                Surface(
                    shape = RoundedCornerShape(4.dp),
                    color = badgeColor.copy(alpha = 0.15f)
                ) {
                    Text(
                        text = badgeText,
                        style = MaterialTheme.typography.labelSmall.copy(
                            fontFamily = FontFamily.Monospace,
                            fontWeight = FontWeight.Bold,
                            fontSize = 8.5.sp,
                            color = badgeColor
                        ),
                        modifier = Modifier.padding(horizontal = 4.dp, vertical = 1.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(2.dp))

            Text(
                text = description,
                style = MaterialTheme.typography.bodySmall.copy(
                    color = TextSecondary,
                    fontSize = 11.5.sp,
                    lineHeight = 16.sp
                )
            )
        }
    }
}

