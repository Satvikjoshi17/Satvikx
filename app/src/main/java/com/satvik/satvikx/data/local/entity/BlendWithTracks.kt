package com.satvik.satvikx.data.local.entity

/**
 * Data class representing a track attributed to either User A, User B, or both.
 */
data class AttributedTrack(
    val track: TrackEntity,
    val broughtBy: String, // "creator", "partner", "both"
    val broughtByName: String // e.g. "Satvik", "Sarah", "Both"
)

/**
 * Compound object holding a BlendEntity and its fully resolved attributed tracks.
 */
data class BlendWithTracks(
    val blend: BlendEntity,
    val attributedTracks: List<AttributedTrack>
)
