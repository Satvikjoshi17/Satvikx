package com.satvik.satvikx.ui.screens

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
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
import androidx.compose.foundation.layout.fillMaxHeight
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
import androidx.compose.material.icons.automirrored.filled.PlaylistAdd
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Shuffle
import androidx.compose.material.icons.filled.Stars
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil3.compose.AsyncImage
import com.satvik.satvikx.data.local.entity.AttributedTrack
import com.satvik.satvikx.data.local.entity.BlendEntity
import com.satvik.satvikx.data.local.entity.TrackEntity
import com.satvik.satvikx.ui.components.OpticallyCenteredPlayIcon
import com.satvik.satvikx.ui.theme.SpotifyGreen
import com.satvik.satvikx.ui.viewmodel.BlendViewModel
import java.util.Locale

/**
 * Pure Spotify Mobile Blend Screen.
 * Features:
 * - Iconic overlapping dual-avatar circles (Venn diagram cover art).
 * - "X% Match" Spotify pill badge & Taste Story Card.
 * - Shared Anthem highlight with instant playback preview.
 * - Big circular Spotify Green Play button (optically centered) & Shuffle toggle.
 * - Clean Spotify track rows with contributor avatar attribution circles.
 */
@Composable
fun BlendDetailScreen(
    blendId: String,
    onBack: () -> Unit,
    viewModel: BlendViewModel = hiltViewModel()
) {
    val context = LocalContext.current
    val haptic = LocalHapticFeedback.current
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val playbackState by viewModel.playbackState.collectAsStateWithLifecycle()
    val currentTrackId = playbackState.currentTrack?.id
    val isPlaying = playbackState.isPlaying

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
                .background(Color(0xFF121212)),
            contentAlignment = Alignment.Center
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                CircularProgressIndicator(color = SpotifyGreen, modifier = Modifier.size(38.dp), strokeWidth = 3.dp)
                Spacer(modifier = Modifier.height(16.dp))
                Text(
                    text = "Blending tastes...",
                    style = MaterialTheme.typography.bodyMedium.copy(
                        fontWeight = FontWeight.SemiBold,
                        color = Color.White
                    )
                )
            }
        }
        return
    }

    val blend = blendWithTracks.blend
    var showMenu by remember { mutableStateOf(false) }

    // Dynamic top gradient derived from match score
    val headerGradientTop = when {
        blend.matchScore >= 90 -> Color(0xFF1E3A8A) // Vibrant deep blue
        blend.matchScore >= 80 -> Color(0xFF134E4A) // Deep teal
        else -> Color(0xFF3B1D6B) // Rich violet
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFF121212))
    ) {
        // Gradient backdrop at top
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(340.dp)
                .background(
                    Brush.verticalGradient(
                        colors = listOf(
                            headerGradientTop.copy(alpha = 0.85f),
                            headerGradientTop.copy(alpha = 0.35f),
                            Color(0xFF121212)
                        )
                    )
                )
        )

        Column(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding()
        ) {
            // Top Navigation Bar
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
                        tint = Color.White
                    )
                }

                Text(
                    text = "Blend",
                    style = MaterialTheme.typography.titleMedium.copy(
                        fontWeight = FontWeight.Bold,
                        fontSize = 16.sp
                    ),
                    color = Color.White
                )

                Box {
                    IconButton(onClick = { showMenu = true }) {
                        Icon(
                            imageVector = Icons.Default.MoreVert,
                            contentDescription = "Options",
                            tint = Color(0xFFA7A7A7)
                        )
                    }

                    DropdownMenu(
                        expanded = showMenu,
                        onDismissRequest = { showMenu = false },
                        modifier = Modifier.background(Color(0xFF282828))
                    ) {
                        DropdownMenuItem(
                            text = { Text("Save as Playlist", color = Color.White) },
                            leadingIcon = {
                                Icon(
                                    Icons.AutoMirrored.Filled.PlaylistAdd,
                                    contentDescription = null,
                                    tint = SpotifyGreen
                                )
                            },
                            onClick = {
                                showMenu = false
                                viewModel.saveBlendAsPlaylist(blendWithTracks) {
                                    Toast.makeText(context, "Saved '${blend.title}' to Playlists", Toast.LENGTH_SHORT).show()
                                }
                            }
                        )
                        DropdownMenuItem(
                            text = { Text("Share Match Story", color = Color.White) },
                            leadingIcon = {
                                Icon(
                                    Icons.Default.Share,
                                    contentDescription = null,
                                    tint = SpotifyGreen
                                )
                            },
                            onClick = {
                                showMenu = false
                                val shareIntent = Intent().apply {
                                    action = Intent.ACTION_SEND
                                    putExtra(
                                        Intent.EXTRA_TEXT,
                                        "✨ We have a ${blend.matchScore}% Music Taste Match on SatvikX Blend!\n\n${blend.title}\nTop Genre: ${blend.topSharedGenre}\nShared Anthem: ${blend.sharedAnthemTitle} by ${blend.sharedAnthemArtist}"
                                    )
                                    type = "text/plain"
                                }
                                context.startActivity(Intent.createChooser(shareIntent, "Share Blend"))
                            }
                        )
                        DropdownMenuItem(
                            text = { Text("Copy Match Summary", color = Color.White) },
                            leadingIcon = {
                                Icon(
                                    Icons.Default.ContentCopy,
                                    contentDescription = null,
                                    tint = Color(0xFFA7A7A7)
                                )
                            },
                            onClick = {
                                showMenu = false
                                val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                                val clip = ClipData.newPlainText(
                                    "SatvikX Blend Match",
                                    "${blend.title} • ${blend.matchScore}% Match • Top Genre: ${blend.topSharedGenre}"
                                )
                                clipboard.setPrimaryClip(clip)
                                Toast.makeText(context, "Blend match summary copied", Toast.LENGTH_SHORT).show()
                            }
                        )
                        DropdownMenuItem(
                            text = { Text("Delete Blend", color = Color(0xFFFF5252)) },
                            leadingIcon = {
                                Icon(
                                    Icons.Default.Delete,
                                    contentDescription = null,
                                    tint = Color(0xFFFF5252)
                                )
                            },
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
                contentPadding = PaddingValues(bottom = 100.dp)
            ) {
                // Spotify Blend Hero Cover Art (Overlapping Dual Circular Avatars)
                item {
                    SpotifyBlendHeroHeader(blend = blend, totalSongs = blendWithTracks.attributedTracks.size)
                }

                // Taste Story Card (Match % and Shared Anthem)
                item {
                    val anthemTrack = blendWithTracks.attributedTracks.firstOrNull { it.broughtBy == "both" }?.track
                        ?: blendWithTracks.attributedTracks.firstOrNull { it.track.title.equals(blend.sharedAnthemTitle, ignoreCase = true) }?.track
                        ?: blendWithTracks.attributedTracks.firstOrNull()?.track
                    val isAnthemPlaying = (anthemTrack?.id == currentTrackId) && isPlaying

                    SpotifyTasteStoryCard(
                        blend = blend,
                        isPlaying = isAnthemPlaying,
                        onPlayAnthem = {
                            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                            if (anthemTrack != null) {
                                viewModel.playTrackInBlend(anthemTrack, blendWithTracks.attributedTracks.map { it.track })
                            }
                        }
                    )
                }

                // Spotify Action Row (Download, Save, Share, Shuffle, Big Green Play Button)
                item {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        // Save to Playlists
                        IconButton(
                            onClick = {
                                viewModel.saveBlendAsPlaylist(blendWithTracks) {
                                    Toast.makeText(context, "Saved to your library", Toast.LENGTH_SHORT).show()
                                }
                            },
                            modifier = Modifier.size(36.dp)
                        ) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Filled.PlaylistAdd,
                                contentDescription = "Save to Library",
                                tint = Color(0xFFA7A7A7),
                                modifier = Modifier.size(24.dp)
                            )
                        }

                        Spacer(modifier = Modifier.width(12.dp))

                        // Share
                        IconButton(
                            onClick = {
                                val shareIntent = Intent().apply {
                                    action = Intent.ACTION_SEND
                                    putExtra(
                                        Intent.EXTRA_TEXT,
                                        "Check out our ${blend.matchScore}% Blend on SatvikX! ${blend.title}"
                                    )
                                    type = "text/plain"
                                }
                                context.startActivity(Intent.createChooser(shareIntent, "Share Blend"))
                            },
                            modifier = Modifier.size(36.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Share,
                                contentDescription = "Share",
                                tint = Color(0xFFA7A7A7),
                                modifier = Modifier.size(22.dp)
                            )
                        }

                        Spacer(modifier = Modifier.weight(1f))

                        // Shuffle Toggle Button
                        IconButton(
                            onClick = {
                                haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                viewModel.playBlend(blendWithTracks, shuffle = true)
                                Toast.makeText(context, "Shuffle playing Blend", Toast.LENGTH_SHORT).show()
                            },
                            modifier = Modifier.size(38.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Shuffle,
                                contentDescription = "Shuffle Blend",
                                tint = Color(0xFFA7A7A7),
                                modifier = Modifier.size(24.dp)
                            )
                        }

                        Spacer(modifier = Modifier.width(14.dp))

                        // Spotify 52dp Circular Green Play Button (optically centered)
                        Box(
                            modifier = Modifier
                                .size(52.dp)
                                .shadow(8.dp, CircleShape)
                                .clip(CircleShape)
                                .background(SpotifyGreen)
                                .clickable {
                                    haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                    viewModel.playBlend(blendWithTracks, shuffle = false)
                                },
                            contentAlignment = Alignment.Center
                        ) {
                            OpticallyCenteredPlayIcon(
                                modifier = Modifier.size(20.dp),
                                tint = Color.Black
                            )
                        }
                    }
                }

                // Tracklist Header
                item {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 6.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "TRACKS",
                            style = MaterialTheme.typography.labelSmall.copy(
                                fontWeight = FontWeight.Bold,
                                fontSize = 12.sp,
                                letterSpacing = 0.8.sp
                            ),
                            color = Color(0xFFA7A7A7)
                        )
                        Text(
                            text = "${blendWithTracks.attributedTracks.size} songs",
                            style = MaterialTheme.typography.bodySmall.copy(fontSize = 12.sp),
                            color = Color(0xFFA7A7A7)
                        )
                    }
                }

                // Attributed Tracks List
                itemsIndexed(
                    items = blendWithTracks.attributedTracks,
                    key = { idx, item -> "${item.track.id}_$idx" }
                ) { index, item ->
                    SpotifyBlendTrackRow(
                        index = index + 1,
                        attributedTrack = item,
                        creatorName = blend.creatorName,
                        partnerName = blend.partnerName,
                        isCurrentPlaying = (item.track.id == currentTrackId) && isPlaying,
                        onClick = {
                            haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                            viewModel.playTrackInBlend(item.track, blendWithTracks.attributedTracks.map { it.track })
                        }
                    )
                }
            }
        }
    }
}

/**
 * Authentic Spotify Blend Cover Art:
 * Overlapping dual circular member avatars with vibrant border rings, match pill badge, and title.
 */
@Composable
private fun SpotifyBlendHeroHeader(
    blend: BlendEntity,
    totalSongs: Int
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 24.dp, vertical = 12.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        // Overlapping dual-circle avatar hero
        Box(
            modifier = Modifier
                .size(width = 170.dp, height = 110.dp),
            contentAlignment = Alignment.Center
        ) {
            // Circle 1: Creator Avatar
            Surface(
                shape = CircleShape,
                color = Color(0xFF38BDF8),
                border = BorderStroke(3.dp, Color(0xFF121212)),
                modifier = Modifier
                    .size(86.dp)
                    .align(Alignment.CenterStart)
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Text(
                        text = blend.creatorName.take(1).uppercase(),
                        style = MaterialTheme.typography.headlineMedium.copy(
                            fontWeight = FontWeight.ExtraBold,
                            color = Color.Black
                        )
                    )
                }
            }

            // Circle 2: Partner / Artist Avatar
            Surface(
                shape = CircleShape,
                color = if (blend.isArtistBlend) Color(0xFFF59E0B) else SpotifyGreen,
                border = BorderStroke(3.dp, Color(0xFF121212)),
                modifier = Modifier
                    .size(86.dp)
                    .align(Alignment.CenterEnd)
            ) {
                if (blend.isArtistBlend && blend.sharedAnthemThumbnail.isNotBlank()) {
                    AsyncImage(
                        model = blend.sharedAnthemThumbnail,
                        contentDescription = blend.partnerName,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxSize()
                    )
                } else {
                    Box(contentAlignment = Alignment.Center) {
                        Text(
                            text = blend.partnerName.take(1).uppercase(),
                            style = MaterialTheme.typography.headlineMedium.copy(
                                fontWeight = FontWeight.ExtraBold,
                                color = Color.Black
                            )
                        )
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(14.dp))

        // Match Percentage Pill
        Surface(
            shape = RoundedCornerShape(16.dp),
            color = SpotifyGreen.copy(alpha = 0.18f),
            border = BorderStroke(1.dp, SpotifyGreen.copy(alpha = 0.45f))
        ) {
            Row(
                modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    imageVector = Icons.Default.AutoAwesome,
                    contentDescription = null,
                    tint = SpotifyGreen,
                    modifier = Modifier.size(13.dp)
                )
                Spacer(modifier = Modifier.width(5.dp))
                Text(
                    text = "${blend.matchScore}% TASTE MATCH",
                    style = MaterialTheme.typography.labelSmall.copy(
                        fontWeight = FontWeight.ExtraBold,
                        letterSpacing = 0.6.sp,
                        color = SpotifyGreen
                    )
                )
            }
        }

        Spacer(modifier = Modifier.height(10.dp))

        // Title
        Text(
            text = blend.title,
            style = MaterialTheme.typography.headlineSmall.copy(
                fontWeight = FontWeight.Bold,
                fontSize = 22.sp
            ),
            color = Color.White,
            textAlign = TextAlign.Center,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis
        )

        Spacer(modifier = Modifier.height(4.dp))

        // Meta Subtitle
        Text(
            text = "Made for ${blend.creatorName} + ${blend.partnerName} • $totalSongs songs",
            style = MaterialTheme.typography.bodySmall.copy(fontSize = 13.sp),
            color = Color(0xFFA7A7A7),
            textAlign = TextAlign.Center
        )
    }
}

/**
 * Spotify Taste Story Card:
 * Highlights the genre alignment and the Shared Anthem uniting both listeners.
 */
@Composable
private fun SpotifyTasteStoryCard(
    blend: BlendEntity,
    isPlaying: Boolean,
    onPlayAnthem: () -> Unit
) {
    Surface(
        shape = RoundedCornerShape(12.dp),
        color = Color(0xFF242424),
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 6.dp)
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = Icons.Default.Stars,
                    contentDescription = null,
                    tint = SpotifyGreen,
                    modifier = Modifier.size(16.dp)
                )
                Spacer(modifier = Modifier.width(6.dp))
                Text(
                    text = "TASTE STORY",
                    style = MaterialTheme.typography.labelSmall.copy(
                        fontWeight = FontWeight.Bold,
                        fontSize = 11.sp,
                        letterSpacing = 0.5.sp,
                        color = SpotifyGreen
                    )
                )
            }

            Spacer(modifier = Modifier.height(6.dp))

            Text(
                text = "You and ${blend.partnerName} have ${blend.matchScore}% taste alignment in ${blend.topSharedGenre}.",
                style = MaterialTheme.typography.bodyMedium.copy(fontSize = 13.5.sp),
                color = Color.White
            )

            if (blend.sharedAnthemTitle.isNotBlank()) {
                Spacer(modifier = Modifier.height(10.dp))

                Surface(
                    shape = RoundedCornerShape(8.dp),
                    color = Color(0xFF181818),
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { onPlayAnthem() }
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Surface(
                            shape = RoundedCornerShape(4.dp),
                            color = Color(0xFF2A2A2A),
                            modifier = Modifier.size(38.dp)
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
                                        imageVector = Icons.Default.MusicNote,
                                        contentDescription = null,
                                        tint = Color(0xFFA7A7A7),
                                        modifier = Modifier.size(18.dp)
                                    )
                                }
                            }
                        }

                        Spacer(modifier = Modifier.width(10.dp))

                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "The song that unites you",
                                style = MaterialTheme.typography.labelSmall.copy(
                                    fontSize = 10.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    color = SpotifyGreen
                                )
                            )
                            Text(
                                text = blend.sharedAnthemTitle,
                                style = MaterialTheme.typography.bodySmall.copy(
                                    fontWeight = FontWeight.Bold,
                                    color = if (isPlaying) SpotifyGreen else Color.White
                                ),
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            Text(
                                text = blend.sharedAnthemArtist,
                                style = MaterialTheme.typography.bodySmall.copy(fontSize = 11.sp),
                                color = Color(0xFFA7A7A7),
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }

                        Spacer(modifier = Modifier.width(8.dp))

                        Box(
                            modifier = Modifier
                                .size(32.dp)
                                .clip(CircleShape)
                                .background(if (isPlaying) SpotifyGreen else Color(0xFF2E2E2E)),
                            contentAlignment = Alignment.Center
                        ) {
                            if (isPlaying) {
                                MiniEqualizerBars(color = Color.Black)
                            } else {
                                OpticallyCenteredPlayIcon(
                                    modifier = Modifier.size(13.dp),
                                    tint = Color.White
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

/**
 * Pure Spotify Track Row with Contributor Avatar Indicator.
 */
@Composable
private fun SpotifyBlendTrackRow(
    index: Int,
    attributedTrack: AttributedTrack,
    creatorName: String,
    partnerName: String,
    isCurrentPlaying: Boolean,
    onClick: () -> Unit
) {
    val track = attributedTrack.track

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 7.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Track Index or Equalizer
        Box(
            modifier = Modifier.width(24.dp),
            contentAlignment = Alignment.CenterStart
        ) {
            if (isCurrentPlaying) {
                MiniEqualizerBars(color = SpotifyGreen)
            } else {
                Text(
                    text = "$index",
                    style = MaterialTheme.typography.bodyMedium.copy(
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Normal
                    ),
                    color = Color(0xFFA7A7A7)
                )
            }
        }

        Spacer(modifier = Modifier.width(6.dp))

        // Album Art
        Surface(
            shape = RoundedCornerShape(4.dp),
            color = Color(0xFF242424),
            modifier = Modifier.size(46.dp)
        ) {
            if (track.thumbnailUrl.isNotBlank()) {
                AsyncImage(
                    model = track.thumbnailUrl,
                    contentDescription = track.title,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize()
                )
            } else {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        imageVector = Icons.Default.MusicNote,
                        contentDescription = null,
                        tint = Color(0xFFA7A7A7),
                        modifier = Modifier.size(20.dp)
                    )
                }
            }
        }

        Spacer(modifier = Modifier.width(12.dp))

        // Title, Contributor Badge & Artist
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = track.title,
                style = MaterialTheme.typography.bodyMedium.copy(
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 14.5.sp
                ),
                color = if (isCurrentPlaying) SpotifyGreen else Color.White,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )

            Spacer(modifier = Modifier.height(3.dp))

            Row(verticalAlignment = Alignment.CenterVertically) {
                // Spotify Contributor Badge Avatar
                when (attributedTrack.broughtBy) {
                    "creator" -> {
                        Surface(
                            shape = CircleShape,
                            color = Color(0xFF38BDF8),
                            modifier = Modifier.size(16.dp)
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Text(
                                    text = creatorName.take(1).uppercase(),
                                    style = MaterialTheme.typography.labelSmall.copy(
                                        fontSize = 9.sp,
                                        fontWeight = FontWeight.ExtraBold,
                                        color = Color.Black
                                    )
                                )
                            }
                        }
                    }
                    "partner" -> {
                        Surface(
                            shape = CircleShape,
                            color = Color(0xFFEC4899),
                            modifier = Modifier.size(16.dp)
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Text(
                                    text = partnerName.take(1).uppercase(),
                                    style = MaterialTheme.typography.labelSmall.copy(
                                        fontSize = 9.sp,
                                        fontWeight = FontWeight.ExtraBold,
                                        color = Color.Black
                                    )
                                )
                            }
                        }
                    }
                    else -> {
                        Surface(
                            shape = CircleShape,
                            color = SpotifyGreen,
                            modifier = Modifier.size(16.dp)
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Text(
                                    text = "★",
                                    style = MaterialTheme.typography.labelSmall.copy(
                                        fontSize = 9.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = Color.Black
                                    )
                                )
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.width(6.dp))

                Text(
                    text = track.artist,
                    style = MaterialTheme.typography.bodySmall.copy(fontSize = 12.5.sp),
                    color = Color(0xFFA7A7A7),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }

        Spacer(modifier = Modifier.width(8.dp))

        // Duration
        val minutes = track.durationSeconds / 60
        val seconds = track.durationSeconds % 60
        Text(
            text = String.format(Locale.US, "%d:%02d", minutes, seconds),
            style = MaterialTheme.typography.bodySmall.copy(fontSize = 12.sp),
            color = Color(0xFFA7A7A7)
        )
    }
}

/**
 * Animated Spotify mini equalizer bars for currently playing song.
 */
@Composable
private fun MiniEqualizerBars(
    modifier: Modifier = Modifier,
    color: Color = SpotifyGreen
) {
    val transition = rememberInfiniteTransition(label = "equalizer")
    val bar1 by transition.animateFloat(
        initialValue = 0.25f,
        targetValue = 0.95f,
        animationSpec = infiniteRepeatable(tween(420, easing = FastOutSlowInEasing), RepeatMode.Reverse),
        label = "bar1"
    )
    val bar2 by transition.animateFloat(
        initialValue = 0.85f,
        targetValue = 0.35f,
        animationSpec = infiniteRepeatable(tween(360, easing = FastOutSlowInEasing), RepeatMode.Reverse),
        label = "bar2"
    )
    val bar3 by transition.animateFloat(
        initialValue = 0.40f,
        targetValue = 1.0f,
        animationSpec = infiniteRepeatable(tween(480, easing = FastOutSlowInEasing), RepeatMode.Reverse),
        label = "bar3"
    )

    Row(
        modifier = modifier.size(width = 14.dp, height = 14.dp),
        horizontalArrangement = Arrangement.spacedBy(1.5.dp),
        verticalAlignment = Alignment.Bottom
    ) {
        Box(
            modifier = Modifier
                .width(2.5.dp)
                .fillMaxHeight(bar1)
                .background(color, RoundedCornerShape(1.dp))
        )
        Box(
            modifier = Modifier
                .width(2.5.dp)
                .fillMaxHeight(bar2)
                .background(color, RoundedCornerShape(1.dp))
        )
        Box(
            modifier = Modifier
                .width(2.5.dp)
                .fillMaxHeight(bar3)
                .background(color, RoundedCornerShape(1.dp))
        )
    }
}
