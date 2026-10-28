package com.satvik.satvikx.data.local.entity

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index

/**
 * Cross-reference table associating tracks with a Stark Blend playlist
 * with track-level attribution metadata.
 */
@Entity(
    tableName = "blend_tracks",
    primaryKeys = ["blendId", "trackId"],
    foreignKeys = [
        ForeignKey(
            entity = BlendEntity::class,
            parentColumns = ["blendId"],
            childColumns = ["blendId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [
        Index("blendId"),
        Index("trackId")
    ]
)
data class BlendTrackCrossRef(
    val blendId: String,
    val trackId: String,
    val position: Int = 0,
    val broughtBy: String, // "creator", "partner", "both"
    val broughtByName: String // e.g. "Satvik", "Sarah", "Both"
)
