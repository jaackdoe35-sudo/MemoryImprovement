package com.memoryimprovement.app

import androidx.room.Dao
import androidx.room.Database
import androidx.room.Entity
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.RoomDatabase
import kotlinx.coroutines.flow.Flow

@Entity(tableName = "memory_items")
data class MemoryItemEntity(
    @PrimaryKey val id: String,
    val prompt: String,
    val answer: String,
    val palace: String,
    val locus: String,
    val createdAt: Long,
    val nextReviewAt: Long,
    val lastScore: Int?,
    val reviewCount: Int,
)

@Entity(tableName = "recall_attempts")
data class RecallAttemptEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val itemId: String,
    val mode: String,
    val correct: Boolean,
    val score: Int,
    val latencyMs: Long,
    val reviewedAt: Long,
)

@Dao
interface MemoryDao {
    @Query("SELECT * FROM memory_items ORDER BY createdAt DESC")
    fun observeItems(): Flow<List<MemoryItemEntity>>

    @Query("SELECT * FROM memory_items ORDER BY createdAt DESC")
    suspend fun getItems(): List<MemoryItemEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertItems(items: List<MemoryItemEntity>)

    @Query("DELETE FROM memory_items WHERE id = :itemId")
    suspend fun deleteItem(itemId: String)

    @Insert
    suspend fun insertAttempt(attempt: RecallAttemptEntity)

    @Query("SELECT * FROM recall_attempts ORDER BY reviewedAt DESC")
    fun observeAttempts(): Flow<List<RecallAttemptEntity>>

    @Query("SELECT COUNT(*) FROM recall_attempts")
    fun observeAttemptCount(): Flow<Int>

    @Query("SELECT COUNT(*) FROM recall_attempts WHERE correct = 1")
    fun observeCorrectAttemptCount(): Flow<Int>
}

@Database(
    entities = [MemoryItemEntity::class, RecallAttemptEntity::class],
    version = 1,
    exportSchema = false,
)
abstract class MemoryDatabase : RoomDatabase() {
    abstract fun memoryDao(): MemoryDao
}
