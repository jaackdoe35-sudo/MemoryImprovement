package com.memoryimprovement.app

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "recall_prompts",
    foreignKeys = [
        ForeignKey(
            entity = MemoryChunk::class,
            parentColumns = ["id"],
            childColumns = ["chunkId"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [Index("chunkId")],
)
data class RecallPrompt(
    @PrimaryKey val id: String,
    val chunkId: String?,
    val prompt: String,
    val answer: String,
    val type: String = "CUED",
    val createdAt: Long = System.currentTimeMillis(),
)
