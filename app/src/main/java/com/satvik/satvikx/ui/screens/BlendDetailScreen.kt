package com.satvik.satvikx.ui.screens

import android.content.Intent
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.PlayCircle
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Shuffle
import androidx.compose.material.icons.filled.Stars
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil3.compose.AsyncImage
import com.satvik.satvikx.data.local.entity.AttributedTrack
import com.satvik.satvikx.ui.theme.ArcCyan
import com.satvik.satvikx.ui.theme.ArcCyanBright
import com.satvik.satvikx.ui.theme.PrimaryNeon
import com.satvik.satvikx.ui.theme.StarkCarbon
import com.satvik.satvikx.ui.theme.StarkGold
import com.satvik.satvikx.ui.theme.SurfaceDark
import com.satvik.satvikx.ui.theme.SurfaceElevated
import com.satvik.satvikx.ui.theme.SurfaceVariantDark
import com.satvik.satvikx.ui.theme.TextPrimary
import com.satvik.satvikx.ui.theme.TextSecondary
import com.satvik.satvikx.ui.viewmodel.BlendViewModel

@Composable
fun BlendDetailScreen(
    blendId: String,
    onBack: () -> Unit,
    viewModel: BlendViewModel = hiltViewModel()
) {
    val context = LocalContext.current
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    LaunchedEffect(blendId) {
        viewModel.loadBlend(blendId)
    }

    BackHandler {
        onBack()
    }

    val blendWithTracks = uiState.activeBlend

    if (uiState.isGenerating || blendWithTracks == null) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(SurfaceDark),
            contentAlignment = Alignment.Center
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                CircularProgressIndicator(color = ArcCyanBright, modifier = Modifier.size(36.dp), strokeWidth = 2.5.dp)
                Spacer(modifier = Modifier.height(14.dp))
                Text(
                    text = "SYNTHESIZING TASTE FUSION...",
                    style = MaterialTheme.typography.labelSmall.copy(
                        fontFamily = FontFamily.Monospace,
                        fontWeight = FontWeight.Bold,
                        color = ArcCyan
                    )
                )
            }
        }
        return
    }

    val blend = blendWithTracks.blend
    var showMenu by remember { mutableStateOf(false) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(SurfaceDark)
            .statusBarsPadding()
    ) {
        // Navigation Top Bar
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 8.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            IconButton(onClick = onBack) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                    contentDescription = "Back",
                    tint = TextPrimary
                )
            }

            Text(
                text = "STARK BLEND ARCHIVE",
                style = MaterialTheme.typography.labelSmall.copy(
                    fontFamily = FontFamily.Monospace,
                    fontWeight = FontWeight.ExtraBold,
                    letterSpacing = 1.sp,
                    color = ArcCyan
                )
            )

            Box {
                IconButton(onClick = { showMenu = true }) {
                    Icon(
                        imageVector = Icons.Default.MoreVert,
                        contentDescription = "Options",
                        tint = TextSecondary
                    )
                }

                DropdownMenu(
                    expanded = showMenu,
                    onDismissRequest = { showMenu = false }
                ) {
                    DropdownMenuItem(
                        text = { Text("Share Match Card", fontFamily = FontFamily.Monospace) },
                        leadingIcon = { Icon(Icons.Default.Share, contentDescription = null, tint = ArcCyanBright) },
                        onClick = {
                            showMenu = false
                            val shareIntent = Intent().apply {
                                action = Intent.ACTION_SEND
                                putExtra(
                                    Intent.EXTRA_TEXT,
                                    "✨ Check out our ${blend.matchScore}% Music Taste Match on Stark Blend!\n\n${blend.title}\nShared Anthem: ${blend.sharedAnthemTitle} by ${blend.sharedAnthemArtist}"
                                )
                                type = "text/plain"
                            }
                            context.startActivity(Intent.createChooser(shareIntent, "Share Blend Card"))
                        }
                    )
                    DropdownMenuItem(
                        text = { Text("Delete Blend", fontFamily = FontFamily.Monospace, color = Color(0xFFFF5252)) },
                        leadingIcon = { Icon(Icons.Default.Delete, contentDescription = null, tint = Color(0xFFFF5252)) },
                        onClick = {
                            showMenu = false
                            viewModel.deleteBlend(blend.blendId)
                            Toast.makeText(context, "Deleted ${blend.title}", Toast.LENGTH_SHORT).show()
                            onBack()
                        }
                    )
                }
            }
        }

        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(bottom = 90.dp)
        ) {
            // Header: Arc Reactor Holographic Match Gauge
            item {
                HolographicMatchHeader(blend = blend, tracksCount = blendWithTracks.attributedTracks.size)
            }

            // Shared Anthem Card ("The Song That Unites You")
            if (blend.sharedAnthemTitle.isNotBlank()) {
                item {
                    SharedAnthemCard(
                        blend = blend,
                        onClickPlay = {
                            val anthemTrack = blendWithTracks.attributedTracks.firstOrNull { it.broughtBy == "both" }?.track
                            if (anthemTrack != null) {
                                viewModel.playTrackInBlend(anthemTrack, blendWithTracks.attributedTracks.map { it.track })
                            }
                        }
                    )
                }
            }

            // Action Buttons (Play Blend / Shuffle)
            item {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 10.dp),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Button(
                        onClick = { viewModel.playBlend(blendWithTracks, shuffle = false) },
                        modifier = Modifier.weight(1f),
                        colors = ButtonDefaults.buttonColors(containerColor = ArcCyanBright, contentColor = StarkCarbon),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Icon(imageVector = Icons.Default.PlayArrow, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(text = "PLAY BLEND", fontWeight = FontWeight.Bold, fontFamily = FontFamily.Monospace)
                    }

                    OutlinedButton(
                        onClick = { viewModel.playBlend(blendWithTracks, shuffle = true) },
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(12.dp),
                        border = BorderStroke(1.dp, ArcCyanBright)
                    ) {
                        Icon(imageVector = Icons.Default.Shuffle, contentDescription = null, modifier = Modifier.size(16.dp), tint = ArcCyanBright)
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(text = "SHUFFLE", color = ArcCyanBright, fontWeight = FontWeight.Bold, fontFamily = FontFamily.Monospace)
                    }
                }
            }

            // Divider & Section Title
            item {
                HorizontalDivider(
                    color = SurfaceVariantDark,
                    thickness = 1.dp,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
                )

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 4.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "ATTRIBUTED TRACKS",
                        style = MaterialTheme.typography.labelSmall.copy(
                            fontFamily = FontFamily.Monospace,
                            fontWeight = FontWeight.ExtraBold,
                            color = TextSecondary
                        )
                    )
                    Text(
                        text = "${blendWithTracks.attributedTracks.size} TRACKS",
                        style = MaterialTheme.typography.labelSmall.copy(
                            fontFamily = FontFamily.Monospace,
                            color = StarkGold
                        )
                    )
                }
            }

            // Attributed Track Items
            itemsIndexed(
                items = blendWithTracks.attributedTracks,
                key = { idx, item -> "${item.track.id}_$idx" }
            ) { index, item ->
                AttributedTrackRow(
                    index = index + 1,
                    attributedTrack = item,
                    onClick = {
                        viewModel.playTrackInBlend(item.track, blendWithTracks.attributedTracks.map { it.track })
                    }
                )
            }
        }
    }
}

@Composable
private fun HolographicMatchHeader(
    blend: com.satvik.satvikx.data.local.entity.BlendEntity,
    tracksCount: Int
) {
    val animatedProgress = remember { Animatable(0f) }

    LaunchedEffect(blend.matchScore) {
        animatedProgress.animateTo(
            targetValue = blend.matchScore / 100f,
            animationSpec = tween(durationMillis = 1000, easing = FastOutSlowInEasing)
        )
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 12.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        // Holographic Arc Reactor Progress Circle
        Box(
            modifier = Modifier.size(130.dp),
            contentAlignment = Alignment.Center
        ) {
            androidx.compose.foundation.Canvas(modifier = Modifier.size(120.dp)) {
                // Background Track
                drawArc(
                    color = SurfaceVariantDark,
                    startAngle = -90f,
                    sweepAngle = 360f,
                    useCenter = false,
                    style = Stroke(width = 8.dp.toPx(), cap = StrokeCap.Round)
                )
                // Animated Match Arc
                drawArc(
                    brush = Brush.sweepGradient(listOf(ArcCyanBright, PrimaryNeon, StarkGold, ArcCyanBright)),
                    startAngle = -90f,
                    sweepAngle = 360f * animatedProgress.value,
                    useCenter = false,
                    style = Stroke(width = 8.dp.toPx(), cap = StrokeCap.Round)
                )
            }

            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(
                    text = "${(animatedProgress.value * 100).toInt()}%",
                    style = MaterialTheme.typography.displaySmall.copy(
                        fontFamily = FontFamily.Monospace,
                        fontWeight = FontWeight.ExtraBold,
                        color = ArcCyanBright
                    )
                )
                Text(
                    text = "MATCH",
                    style = MaterialTheme.typography.labelSmall.copy(
                        fontFamily = FontFamily.Monospace,
                        fontSize = 10.sp,
                        letterSpacing = 1.sp,
                        color = TextSecondary
                    )
                )
            }
        }

        Spacer(modifier = Modifier.height(12.dp))

        // Blend Title
        Text(
            text = blend.title,
            style = MaterialTheme.typography.titleLarge.copy(
                fontWeight = FontWeight.Bold,
                color = TextPrimary
            ),
            textAlign = androidx.compose.ui.text.style.TextAlign.Center
        )

        Spacer(modifier = Modifier.height(4.dp))

        // Dual Participant Chips with Energy Beam
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Center
        ) {
            Surface(
                shape = RoundedCornerShape(12.dp),
                color = ArcCyan.copy(alpha = 0.15f),
                border = BorderStroke(1.dp, ArcCyanBright)
            ) {
                Text(
                    text = blend.creatorName.uppercase(),
                    style = MaterialTheme.typography.labelSmall.copy(
                        fontFamily = FontFamily.Monospace,
                        fontWeight = FontWeight.Bold,
                        color = ArcCyanBright
                    ),
                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                )
            }

            Text(
                text = " • FUSION • ",
                style = MaterialTheme.typography.labelSmall.copy(
                    fontFamily = FontFamily.Monospace,
                    fontSize = 10.sp,
                    color = PrimaryNeon
                ),
                modifier = Modifier.padding(horizontal = 4.dp)
            )

            Surface(
                shape = RoundedCornerShape(12.dp),
                color = StarkGold.copy(alpha = 0.15f),
                border = BorderStroke(1.dp, StarkGold)
            ) {
                Text(
                    text = blend.partnerName.uppercase(),
                    style = MaterialTheme.typography.labelSmall.copy(
                        fontFamily = FontFamily.Monospace,
                        fontWeight = FontWeight.Bold,
                        color = StarkGold
                    ),
                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }

        Spacer(modifier = Modifier.height(6.dp))

        Text(
            text = "TOP SHARED GENRE • ${blend.topSharedGenre.uppercase()}",
            style = MaterialTheme.typography.labelSmall.copy(
                fontFamily = FontFamily.Monospace,
                fontSize = 11.sp,
                letterSpacing = 0.8.sp,
                color = TextSecondary
            )
        )
    }
}

@Composable
private fun SharedAnthemCard(
    blend: com.satvik.satvikx.data.local.entity.BlendEntity,
    onClickPlay: () -> Unit
) {
    Surface(
        shape = RoundedCornerShape(14.dp),
        color = SurfaceElevated,
        border = BorderStroke(1.dp, PrimaryNeon.copy(alpha = 0.4f)),
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 6.dp)
            .clickable { onClickPlay() }
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Surface(
                shape = RoundedCornerShape(8.dp),
                color = SurfaceVariantDark,
                modifier = Modifier.size(46.dp)
            ) {
                if (blend.sharedAnthemThumbnail.isNotBlank()) {
                    AsyncImage(
                        model = blend.sharedAnthemThumbnail,
                        contentDescription = blend.sharedAnthemTitle,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxSize()
                    )
                } else {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            imageVector = Icons.Default.Stars,
                            contentDescription = null,
                            tint = PrimaryNeon,
                            modifier = Modifier.size(24.dp)
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.width(12.dp))

            Column(modifier = Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .size(6.dp)
                            .clip(CircleShape)
                            .background(PrimaryNeon)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = "THE SONG THAT UNITES YOU",
                        style = MaterialTheme.typography.labelSmall.copy(
                            fontFamily = FontFamily.Monospace,
                            fontWeight = FontWeight.ExtraBold,
                            fontSize = 9.sp,
                            letterSpacing = 0.6.sp,
                            color = PrimaryNeon
                        )
                    )
                }
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = blend.sharedAnthemTitle,
                    style = MaterialTheme.typography.bodyMedium.copy(
                        fontWeight = FontWeight.Bold,
                        color = TextPrimary
                    ),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = blend.sharedAnthemArtist,
                    style = MaterialTheme.typography.bodySmall.copy(fontSize = 11.sp),
                    color = TextSecondary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }

            IconButton(onClick = onClickPlay) {
                Icon(
                    imageVector = Icons.Default.PlayCircle,
                    contentDescription = "Play Shared Anthem",
                    tint = PrimaryNeon,
                    modifier = Modifier.size(32.dp)
                )
            }
        }
    }
}

@Composable
private fun AttributedTrackRow(
    index: Int,
    attributedTrack: AttributedTrack,
    onClick: () -> Unit
) {
    val track = attributedTrack.track

    Surface(
        color = Color.Transparent,
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onClick() }
            .padding(horizontal = 16.dp, vertical = 6.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "$index",
                style = MaterialTheme.typography.labelSmall.copy(
                    fontFamily = FontFamily.Monospace,
                    color = TextSecondary.copy(alpha = 0.6f)
                ),
                modifier = Modifier.width(26.dp)
            )

            Surface(
                shape = RoundedCornerShape(8.dp),
                color = SurfaceVariantDark,
                modifier = Modifier.size(44.dp)
            ) {
                if (track.thumbnailUrl.isNotBlank()) {
                    AsyncImage(
                        model = track.thumbnailUrl,
                        contentDescription = track.title,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxSize()
                    )
                }
            }

            Spacer(modifier = Modifier.width(12.dp))

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = track.title,
                    style = MaterialTheme.typography.bodyMedium.copy(
                        fontWeight = FontWeight.Bold,
                        color = TextPrimary
                    ),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Spacer(modifier = Modifier.height(2.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = track.artist,
                        style = MaterialTheme.typography.bodySmall.copy(fontSize = 11.sp),
                        color = TextSecondary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f, fill = false)
                    )

                    Spacer(modifier = Modifier.width(8.dp))

                    // Attribution Pill Badge
                    val (badgeText, badgeColor) = when (attributedTrack.broughtBy) {
                        "creator" -> Pair("You", ArcCyanBright)
                        "both" -> Pair("Both ★", PrimaryNeon)
                        else -> Pair(attributedTrack.broughtByName, StarkGold)
                    }

                    Surface(
                        shape = RoundedCornerShape(6.dp),
                        color = badgeColor.copy(alpha = 0.12f),
                        border = BorderStroke(1.dp, badgeColor.copy(alpha = 0.5f))
                    ) {
                        Text(
                            text = badgeText,
                            style = MaterialTheme.typography.labelSmall.copy(
                                fontFamily = FontFamily.Monospace,
                                fontSize = 9.sp,
                                fontWeight = FontWeight.Bold,
                                color = badgeColor
                            ),
                            modifier = Modifier.padding(horizontal = 5.dp, vertical = 1.5.dp),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.width(8.dp))

            val durationMinutes = track.durationSeconds / 60
            val durationRemainingSec = track.durationSeconds % 60
            Text(
                text = String.format("%d:%02d", durationMinutes, durationRemainingSec),
                style = MaterialTheme.typography.labelSmall.copy(
                    fontFamily = FontFamily.Monospace,
                    fontSize = 11.sp,
                    color = TextSecondary
                )
            )
        }
    }
}
