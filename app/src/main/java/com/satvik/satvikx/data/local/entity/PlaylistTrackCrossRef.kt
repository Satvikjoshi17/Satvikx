package com.satvik.satvikx.data.local.entity

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index

/**
 * Cross-reference table establishing a many-to-many relationship
 * between PlaylistEntity and TrackEntity with ordering support.
 * Decoupled from TrackEntity cascade deletion so tracks are never
 * purged from playlists when played, updated, or added across multiple playlists.
 */
@Entity(
    tableName = "playlist_track_cross_ref",
    primaryKeys = ["playlistId", "trackId"],
    foreignKeys = [
        ForeignKey(
            entity = PlaylistEntity::class,
            parentColumns = ["playlistId"],
            childColumns = ["playlistId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [
        Index(value = ["playlistId"]),
        Index(value = ["trackId"])
    ]
)
data class PlaylistTrackCrossRef(
    val playlistId: Long,
    val trackId: String,
    val position: Int = 0,
    val addedAtTimestamp: Long = System.currentTimeMillis()
)
