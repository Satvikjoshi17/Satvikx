package com.satvik.satvikx.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.satvik.satvikx.data.local.entity.BlendEntity
import com.satvik.satvikx.data.local.entity.BlendTrackCrossRef
import kotlinx.coroutines.flow.Flow

@Dao
interface BlendDao {

    @Query("SELECT * FROM blends ORDER BY createdAt DESC")
    fun getAllBlends(): Flow<List<BlendEntity>>

    @Query("SELECT * FROM blends ORDER BY createdAt DESC")
    suspend fun getAllBlendsSync(): List<BlendEntity>

    @Query("SELECT * FROM blends WHERE blendId = :blendId")
    suspend fun getBlendById(blendId: String): BlendEntity?

    @Query("SELECT * FROM blend_tracks WHERE blendId = :blendId ORDER BY position ASC")
    suspend fun getBlendTracks(blendId: String): List<BlendTrackCrossRef>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertBlend(blend: BlendEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertBlendTracks(tracks: List<BlendTrackCrossRef>)

    @Query("DELETE FROM blends WHERE blendId = :blendId")
    suspend fun deleteBlend(blendId: String)

    @Query("DELETE FROM blend_tracks WHERE blendId = :blendId")
    suspend fun deleteBlendTracks(blendId: String)
}
