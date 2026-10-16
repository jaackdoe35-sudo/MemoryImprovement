package com.memoryimprovement.app

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "associations",
    foreignKeys = [
        ForeignKey(
            entity = RecallPrompt::class,
            parentColumns = ["id"],
            childColumns = ["promptId"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [Index("promptId"), Index("chunkId")],
)
data class Association(
    @PrimaryKey val id: String,
    val promptId: String?,
    val chunkId: String?,
    val visualCue: String,
    val actionCue: String,
    val createdAt: Long = System.currentTimeMillis(),
)
