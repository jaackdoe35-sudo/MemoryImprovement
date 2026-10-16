package com.memoryimprovement.app

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "documents")
data class Document(
    @PrimaryKey val id: String,
    val title: String,
    val fileUri: String,
    val fileType: String,
    val content: String,
    val addedAt: Long = System.currentTimeMillis(),
    val processed: Boolean = false,
)
