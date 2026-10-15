package com.memoryimprovement.app

import androidx.room.ColumnInfo
import androidx.room.Dao
import androidx.room.Database
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
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
    val palaceNodeId: String? = null,
    @ColumnInfo(defaultValue = "''") val chunkLabel: String = "",
    @ColumnInfo(defaultValue = "''") val association: String = "",
    val sequenceOrder: Int? = null,
)

@Entity(
    tableName = "palace_nodes",
    foreignKeys = [
        ForeignKey(
            entity = PalaceNodeEntity::class,
            parentColumns = ["id"],
            childColumns = ["parentId"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [Index("parentId"), Index("kind")],
)
data class PalaceNodeEntity(
    @PrimaryKey val id: String,
    val name: String,
    /** PALACE, ROUTE, AREA, or LOCUS */
    val kind: String,
    val parentId: String?,
    val position: Int = 0,
    val createdAt: Long = System.currentTimeMillis(),
)

@Entity(
    tableName = "retention_checkpoints",
    indices = [Index("itemId"), Index("dueAt"), Index("completedAt")],
)
data class RetentionCheckpointEntity(
    @PrimaryKey val id: String,
    val itemId: String,
    val delayDays: Int,
    val dueAt: Long,
    val completedAt: Long?,
    val score: Int?,
    val latencyMs: Long?,
    val recalledResponse: String?,
    val accuracy: Int? = null,
)

@Entity(tableName = "recall_attempts")
data class RecallAttemptEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val itemId: String,
    val mode: String,
    val correct: Boolean,
    val score: Int,
    val latencyMs: Long,
    val recalledResponse: String?,
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

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertLegacyItemsIfAbsent(items: List<MemoryItemEntity>)

    @Query("DELETE FROM memory_items WHERE id = :itemId")
    suspend fun deleteItem(itemId: String)

    @Query("DELETE FROM retention_checkpoints WHERE itemId = :itemId")
    suspend fun deleteCheckpointsForItem(itemId: String)

    @Query("DELETE FROM recall_attempts WHERE itemId = :itemId")
    suspend fun deleteAttemptsForItem(itemId: String)

    @Insert
    suspend fun insertAttempt(attempt: RecallAttemptEntity)

    @Insert
    suspend fun insertAttempts(attempts: List<RecallAttemptEntity>)

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertRetentionCheckpoints(checkpoints: List<RetentionCheckpointEntity>)

    @Query("SELECT * FROM retention_checkpoints ORDER BY dueAt")
    suspend fun getRetentionCheckpoints(): List<RetentionCheckpointEntity>

    @Query("UPDATE retention_checkpoints SET completedAt = :completedAt, score = :score, latencyMs = :latencyMs, recalledResponse = :recalledResponse, accuracy = :accuracy WHERE id = :id AND completedAt IS NULL")
    suspend fun completeRetentionCheckpoint(
        id: String,
        completedAt: Long,
        score: Int,
        latencyMs: Long,
        recalledResponse: String?,
        accuracy: Int,
    ): Int

    @Query("SELECT * FROM recall_attempts ORDER BY reviewedAt DESC")
    fun observeAttempts(): Flow<List<RecallAttemptEntity>>

    @Query("SELECT * FROM recall_attempts ORDER BY reviewedAt DESC")
    suspend fun getAttempts(): List<RecallAttemptEntity>

    @Query("SELECT COUNT(*) FROM recall_attempts")
    fun observeAttemptCount(): Flow<Int>

    @Query("SELECT COUNT(*) FROM recall_attempts WHERE correct = 1")
    fun observeCorrectAttemptCount(): Flow<Int>

    @Query("SELECT * FROM palace_nodes ORDER BY position, name COLLATE NOCASE")
    suspend fun getPalaceNodes(): List<PalaceNodeEntity>

    @Query("SELECT * FROM palace_nodes WHERE id = :id LIMIT 1")
    suspend fun getPalaceNode(id: String): PalaceNodeEntity?

    @Query("SELECT * FROM palace_nodes WHERE parentId IS :parentId ORDER BY position, name COLLATE NOCASE")
    suspend fun getChildren(parentId: String?): List<PalaceNodeEntity>

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insertPalaceNode(node: PalaceNodeEntity)

    @Query("UPDATE memory_items SET palaceNodeId = NULL WHERE palaceNodeId IN (:nodeIds)")
    suspend fun clearMemoryPalaceNodeReferences(nodeIds: List<String>)

    @Query("UPDATE palace_nodes SET parentId = :parentId WHERE id = :id")
    suspend fun movePalaceNode(id: String, parentId: String?)

    @Query("UPDATE palace_nodes SET name = :name WHERE id = :id")
    suspend fun renamePalaceNode(id: String, name: String)

    @Query("DELETE FROM palace_nodes WHERE id = :id")
    suspend fun deletePalaceNode(id: String)

    @Query("DELETE FROM memory_items")
    suspend fun deleteAllItems()

    @Query("DELETE FROM recall_attempts")
    suspend fun deleteAllAttempts()

    @Query("DELETE FROM retention_checkpoints")
    suspend fun deleteAllRetentionCheckpoints()

    @Query("DELETE FROM palace_nodes")
    suspend fun deleteAllPalaceNodes()
}

@Database(
    entities = [MemoryItemEntity::class, RecallAttemptEntity::class, PalaceNodeEntity::class, RetentionCheckpointEntity::class],
    version = 6,
    exportSchema = false,
)
abstract class MemoryDatabase : RoomDatabase() {
    abstract fun memoryDao(): MemoryDao

    companion object {
        val MIGRATION_5_6 = object : Migration(5, 6) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE memory_items ADD COLUMN chunkLabel TEXT NOT NULL DEFAULT ''")
                db.execSQL("ALTER TABLE memory_items ADD COLUMN association TEXT NOT NULL DEFAULT ''")
                db.execSQL("ALTER TABLE memory_items ADD COLUMN sequenceOrder INTEGER")
            }
        }

        val MIGRATION_4_5 = object : Migration(4, 5) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE retention_checkpoints ADD COLUMN accuracy INTEGER")
            }
        }

        val MIGRATION_3_4 = object : Migration(3, 4) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("""CREATE TABLE IF NOT EXISTS retention_checkpoints (
                    id TEXT NOT NULL PRIMARY KEY,
                    itemId TEXT NOT NULL,
                    delayDays INTEGER NOT NULL,
                    dueAt INTEGER NOT NULL,
                    completedAt INTEGER,
                    score INTEGER,
                    latencyMs INTEGER,
                    recalledResponse TEXT
                )""")
                db.execSQL("CREATE INDEX IF NOT EXISTS index_retention_checkpoints_itemId ON retention_checkpoints(itemId)")
                db.execSQL("CREATE INDEX IF NOT EXISTS index_retention_checkpoints_dueAt ON retention_checkpoints(dueAt)")
                db.execSQL("CREATE INDEX IF NOT EXISTS index_retention_checkpoints_completedAt ON retention_checkpoints(completedAt)")
            }
        }

        val MIGRATION_2_3 = object : Migration(2, 3) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE recall_attempts ADD COLUMN recalledResponse TEXT")
            }
        }

        val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE memory_items ADD COLUMN palaceNodeId TEXT")
                db.execSQL(
                    """CREATE TABLE IF NOT EXISTS palace_nodes (
                        id TEXT NOT NULL PRIMARY KEY,
                        name TEXT NOT NULL,
                        kind TEXT NOT NULL,
                        parentId TEXT,
                        position INTEGER NOT NULL,
                        createdAt INTEGER NOT NULL,
                        FOREIGN KEY(parentId) REFERENCES palace_nodes(id) ON UPDATE NO ACTION ON DELETE CASCADE
                    )""",
                )
                db.execSQL("CREATE INDEX IF NOT EXISTS index_palace_nodes_parentId ON palace_nodes(parentId)")
                db.execSQL("CREATE INDEX IF NOT EXISTS index_palace_nodes_kind ON palace_nodes(kind)")
            }
        }
    }
}
