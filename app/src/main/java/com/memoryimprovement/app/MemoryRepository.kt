package com.memoryimprovement.app

import android.content.Context
import androidx.room.Room
import androidx.room.withTransaction
import org.json.JSONArray
import java.util.UUID

/** Offline-first Room persistence, with one-time migration from the original preference store. */
class MemoryRepository(
    context: Context,
    databaseName: String = DATABASE_NAME,
) : AutoCloseable {
    private val appContext = context.applicationContext
    private val database = Room.databaseBuilder(
        appContext,
        MemoryDatabase::class.java,
        databaseName,
    ).addMigrations(
        MemoryDatabase.MIGRATION_1_2,
        MemoryDatabase.MIGRATION_2_3,
        MemoryDatabase.MIGRATION_3_4,
        MemoryDatabase.MIGRATION_4_5,
        MemoryDatabase.MIGRATION_5_6,
        MemoryDatabase.MIGRATION_6_7,
    ).build()

    override fun close() {
        database.close()
    }
    private val dao = database.memoryDao()

    suspend fun loadItems(): List<MemoryItem> = loadItemsWithMigrationStatus().items

    /** Captures a consistent logical backup of all user study data. */
    suspend fun createBackupSnapshot(createdAt: Long = System.currentTimeMillis()): MemoryBackupSnapshot =
        database.withTransaction {
            MemoryBackupSnapshot(
                createdAt = createdAt,
                memories = dao.getItems().map { it.toModel() },
                palaceNodes = dao.getPalaceNodes().map { it.toModel() },
                attempts = dao.getAttempts().map { attempt ->
                    RecallAttempt(
                        itemId = attempt.itemId,
                        mode = runCatching { RecallMode.valueOf(attempt.mode) }.getOrDefault(RecallMode.Cued),
                        correct = attempt.correct,
                        score = attempt.score,
                        latencyMs = attempt.latencyMs,
                        recalledResponse = attempt.recalledResponse,
                        reviewedAt = attempt.reviewedAt,
                    )
                },
                checkpoints = dao.getRetentionCheckpoints().map { it.toModel() },
            )
        }

    /**
     * Replaces local study data atomically. The payload is validated before entering the transaction,
     * and palace parents are inserted before their descendants.
     */
    suspend fun restoreBackupSnapshot(snapshot: MemoryBackupSnapshot) {
        // Reuse the public codec validator so callers cannot bypass backup integrity checks.
        MemoryBackupPlan.decode(MemoryBackupPlan.encode(snapshot))
        database.withTransaction {
            dao.deleteAllAttempts()
            dao.deleteAllRetentionCheckpoints()
            dao.deleteAllItems()
            dao.deleteAllPalaceNodes()
            dao.upsertItems(snapshot.memories.map { it.toEntity() })

            val pending = snapshot.palaceNodes.toMutableList()
            val inserted = mutableSetOf<String>()
            while (pending.isNotEmpty()) {
                val ready = pending.filter { it.parentId == null || it.parentId in inserted }
                check(ready.isNotEmpty()) { "Backup contains an invalid palace hierarchy." }
                ready.forEach { node ->
                    dao.insertPalaceNode(
                        PalaceNodeEntity(
                            id = node.id,
                            name = node.name,
                            kind = node.kind.name,
                            parentId = node.parentId,
                            position = node.position,
                            createdAt = node.createdAt,
                        ),
                    )
                    inserted += node.id
                }
                pending.removeAll(ready.toSet())
            }
            dao.insertAttempts(
                snapshot.attempts.map { attempt ->
                    RecallAttemptEntity(
                        itemId = attempt.itemId,
                        mode = attempt.mode.name,
                        correct = attempt.correct,
                        score = attempt.score,
                        latencyMs = attempt.latencyMs,
                        recalledResponse = attempt.recalledResponse,
                        reviewedAt = attempt.reviewedAt,
                    )
                },
            )
            dao.insertRetentionCheckpoints(snapshot.checkpoints.map { it.toEntity() })
        }
        appContext.getSharedPreferences(PREFERENCES, Context.MODE_PRIVATE).edit()
            .remove(LEGACY_ITEMS_KEY)
            .putBoolean(LEGACY_MIGRATION_PENDING_KEY, false)
            .apply()
    }

    suspend fun loadItemsWithMigrationStatus(): LegacyMigrationResult {
        val preferences = appContext.getSharedPreferences(PREFERENCES, Context.MODE_PRIVATE)
        val existing = dao.getItems()
        val retryPending = preferences.getBoolean(LEGACY_MIGRATION_PENDING_KEY, false)
        var migrationResult = LegacyMigrationResult(items = emptyList())
        if (existing.isEmpty() || retryPending) {
            val raw = preferences.getString(LEGACY_ITEMS_KEY, null)
            if (raw != null) {
                migrationResult = LegacyMigrationPlan.decode(raw, ::parseLegacyItems)
                if (migrationResult.errorMessage != null) {
                    // Keep the original preference untouched and retry on the next launch.
                    preferences.edit().putBoolean(LEGACY_MIGRATION_PENDING_KEY, true).apply()
                } else {
                    dao.insertLegacyItemsIfAbsent(migrationResult.items.map { it.toEntity() })
                    preferences.edit().putBoolean(LEGACY_MIGRATION_PENDING_KEY, false).apply()
                }
            } else {
                preferences.edit().putBoolean(LEGACY_MIGRATION_PENDING_KEY, false).apply()
            }
        }
        val items = dao.getItems().map { it.toModel() }
        dao.insertRetentionCheckpoints(
            items.flatMap { item -> RetentionCheckpointPlan.dueTimes(item.id, item.createdAt) }
                .map { it.toEntity() },
        )
        return migrationResult.copy(items = items)
    }

    suspend fun saveItems(items: List<MemoryItem>) {
        dao.upsertItems(items.map { it.toEntity() })
        dao.insertRetentionCheckpoints(
            items.flatMap { item -> RetentionCheckpointPlan.dueTimes(item.id, item.createdAt) }
                .map { it.toEntity() },
        )
    }

    /** Deletes a memory and its associated review records atomically. */
    suspend fun deleteMemory(itemId: String) = database.withTransaction {
        dao.deleteCheckpointsForItem(itemId)
        dao.deleteAttemptsForItem(itemId)
        dao.deleteItem(itemId)
    }

    suspend fun loadRetentionCheckpoints(): List<RetentionCheckpoint> =
        dao.getRetentionCheckpoints().map { it.toModel() }

    suspend fun completeRetentionCheckpoint(
        id: String,
        score: Int,
        latencyMs: Long,
        recalledResponse: String?,
        accuracy: Int,
        completedAt: Long = System.currentTimeMillis(),
    ): Boolean = database.withTransaction {
        val checkpoint = dao.getRetentionCheckpoints().firstOrNull { it.id == id } ?: return@withTransaction false
        val itemEntity = dao.getItems().firstOrNull { it.id == checkpoint.itemId } ?: return@withTransaction false
        val normalizedScore = score.coerceIn(0, 5)
        val normalizedAccuracy = accuracy.coerceIn(0, 2)
        val changed = dao.completeRetentionCheckpoint(
            id = id,
            completedAt = completedAt,
            score = normalizedScore,
            latencyMs = latencyMs.coerceAtLeast(0L),
            recalledResponse = recalledResponse?.trim()?.takeIf { it.isNotEmpty() },
            accuracy = normalizedAccuracy,
        )
        if (changed != 1) return@withTransaction false

        val updatedItem = RetentionReviewPlan.apply(itemEntity.toModel(), normalizedScore, completedAt)
        dao.upsertItems(listOf(updatedItem.toEntity()))
        dao.insertAttempt(
            RecallAttemptEntity(
                itemId = checkpoint.itemId,
                mode = RecallMode.Retention.name,
                correct = normalizedAccuracy == 2,
                score = normalizedScore,
                latencyMs = latencyMs.coerceAtLeast(0L),
                recalledResponse = recalledResponse?.trim()?.takeIf { it.isNotEmpty() },
                reviewedAt = completedAt,
            ),
        )
        true
    }

    suspend fun saveAttempt(attempt: RecallAttempt) {
        dao.insertAttempt(
            RecallAttemptEntity(
                itemId = attempt.itemId,
                mode = attempt.mode.name,
                correct = attempt.correct,
                score = attempt.score,
                latencyMs = attempt.latencyMs.coerceAtLeast(0L),
                recalledResponse = attempt.recalledResponse,
                reviewedAt = attempt.reviewedAt,
            ),
        )
    }

    suspend fun loadAttempts(): List<RecallAttempt> =
        dao.getAttempts().map { attempt ->
            RecallAttempt(
                itemId = attempt.itemId,
                mode = runCatching { RecallMode.valueOf(attempt.mode) }.getOrDefault(RecallMode.Cued),
                correct = attempt.correct,
                score = attempt.score,
                latencyMs = attempt.latencyMs,
                recalledResponse = attempt.recalledResponse,
                reviewedAt = attempt.reviewedAt,
            )
        }

    suspend fun loadPalaceNodes(): List<PalaceNode> =
        dao.getPalaceNodes().map { it.toModel() }

    suspend fun createPalaceNode(name: String, kind: PalaceNodeKind, parentId: String?) {
        val normalizedName = name.trim()
        require(normalizedName.isNotEmpty()) { "Node name cannot be empty." }
        val parent = parentId?.let { dao.getPalaceNode(it) }
        require(parentId == null || parent != null) { "Parent node does not exist." }
        require(
            when (kind) {
                PalaceNodeKind.Palace -> parent == null
                PalaceNodeKind.Route, PalaceNodeKind.Area -> parent?.kind in
                    listOf(PalaceNodeKind.Palace.name, PalaceNodeKind.Route.name, PalaceNodeKind.Area.name)
                PalaceNodeKind.Locus -> parent?.kind in
                    listOf(PalaceNodeKind.Palace.name, PalaceNodeKind.Route.name, PalaceNodeKind.Area.name)
            },
        ) { "This node type cannot be placed under the selected parent." }
        dao.insertPalaceNode(
            PalaceNodeEntity(
                id = UUID.randomUUID().toString(),
                name = normalizedName,
                kind = kind.name,
                parentId = parentId,
            ),
        )
    }

    suspend fun movePalaceNode(id: String, newParentId: String?) = database.withTransaction {
        val nodes = dao.getPalaceNodes().map { it.toModel() }
        require(PalaceMovePlan.isValidParent(nodes, id, newParentId)) {
            "This location cannot be moved to the selected parent."
        }
        dao.movePalaceNode(id, newParentId)
    }

    suspend fun renamePalaceNode(id: String, name: String) {
        val normalizedName = name.trim()
        require(normalizedName.isNotEmpty()) { "Node name cannot be empty." }
        require(dao.getPalaceNode(id) != null) { "Palace location does not exist." }
        dao.renamePalaceNode(id, normalizedName)
    }

    /** Deletes a palace branch and atomically unlinks memories assigned anywhere in that branch. */
    suspend fun deletePalaceNode(id: String) = database.withTransaction {
        val nodes = dao.getPalaceNodes().map { it.toModel() }
        val nodeIds = PalaceHierarchyPlan.descendantIds(nodes, id)
        if (nodeIds.isNotEmpty()) {
            dao.clearMemoryPalaceNodeReferences(nodeIds.toList())
            dao.deletePalaceNode(id)
        }
    }

    private fun parseLegacyItems(raw: String): List<MemoryItem> {
        val array = JSONArray(raw)
        return buildList {
            for (index in 0 until array.length()) {
                val value = array.getJSONObject(index)
                add(
                    MemoryItem(
                        id = value.getString("id"),
                        prompt = value.getString("prompt"),
                        answer = value.getString("answer"),
                        palace = value.optString("palace"),
                        locus = value.optString("locus"),
                        createdAt = value.optLong("createdAt"),
                        nextReviewAt = value.optLong("nextReviewAt"),
                        lastScore = if (value.isNull("lastScore")) null else value.optInt("lastScore"),
                        reviewCount = value.optInt("reviewCount"),
                    ),
                )
            }
        }
    }

    private fun RetentionCheckpoint.toEntity() = RetentionCheckpointEntity(
        id = id,
        itemId = itemId,
        delayDays = delayDays,
        dueAt = dueAt,
        completedAt = completedAt,
        score = score,
        latencyMs = latencyMs,
        recalledResponse = recalledResponse,
        accuracy = accuracy,
    )

    private fun RetentionCheckpointEntity.toModel() = RetentionCheckpoint(
        id = id,
        itemId = itemId,
        delayDays = delayDays,
        dueAt = dueAt,
        completedAt = completedAt,
        score = score,
        latencyMs = latencyMs,
        recalledResponse = recalledResponse,
        accuracy = accuracy,
    )

    private fun MemoryItemEntity.toModel() = MemoryItem(
        id = id, prompt = prompt, answer = answer, palace = palace, locus = locus,
        createdAt = createdAt, nextReviewAt = nextReviewAt, lastScore = lastScore,
        reviewCount = reviewCount, palaceNodeId = palaceNodeId,
        chunkLabel = chunkLabel, association = association, sequenceOrder = sequenceOrder,
    )

    private fun MemoryItem.toEntity() = MemoryItemEntity(
        id = id, prompt = prompt, answer = answer, palace = palace, locus = locus,
        createdAt = createdAt, nextReviewAt = nextReviewAt, lastScore = lastScore,
        reviewCount = reviewCount, palaceNodeId = palaceNodeId,
        chunkLabel = chunkLabel, association = association, sequenceOrder = sequenceOrder,
    )

    private fun PalaceNodeEntity.toModel() = PalaceNode(
        id = id,
        name = name,
        kind = runCatching { PalaceNodeKind.valueOf(kind) }.getOrDefault(PalaceNodeKind.Locus),
        parentId = parentId,
        position = position,
        createdAt = createdAt,
    )

    private companion object {
        const val DATABASE_NAME = "memory_improvement.db"
        const val PREFERENCES = "memory_improvement_local"
        const val LEGACY_ITEMS_KEY = "items_json"
        const val LEGACY_MIGRATION_PENDING_KEY = "legacy_migration_pending"
    }
}
