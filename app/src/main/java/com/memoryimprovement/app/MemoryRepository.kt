package com.memoryimprovement.app

import android.content.Context
import androidx.room.Room
import org.json.JSONArray
import java.util.UUID

/** Offline-first Room persistence, with one-time migration from the original preference store. */
class MemoryRepository(context: Context) {
    private val appContext = context.applicationContext
    private val database = Room.databaseBuilder(
        appContext,
        MemoryDatabase::class.java,
        "memory_improvement.db",
    ).addMigrations(MemoryDatabase.MIGRATION_1_2).build()
    private val dao = database.memoryDao()

    suspend fun loadItems(): List<MemoryItem> {
        val existing = dao.getItems()
        if (existing.isEmpty()) migrateLegacyItems()
        return dao.getItems().map { it.toModel() }
    }

    suspend fun saveItems(items: List<MemoryItem>) {
        dao.upsertItems(items.map { it.toEntity() })
    }

    suspend fun saveAttempt(attempt: RecallAttempt) {
        dao.insertAttempt(
            RecallAttemptEntity(
                itemId = attempt.itemId,
                mode = attempt.mode.name,
                correct = attempt.correct,
                score = attempt.score,
                latencyMs = attempt.latencyMs.coerceAtLeast(0L),
                reviewedAt = attempt.reviewedAt,
            ),
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

    suspend fun deletePalaceNode(id: String) {
        dao.deletePalaceNode(id)
    }

    private suspend fun migrateLegacyItems() {
        val raw = appContext.getSharedPreferences(PREFERENCES, Context.MODE_PRIVATE)
            .getString(LEGACY_ITEMS_KEY, null) ?: return
        val legacyItems = runCatching {
            val array = JSONArray(raw)
            buildList {
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
        }.getOrDefault(emptyList())
        if (legacyItems.isNotEmpty()) dao.upsertItems(legacyItems.map { it.toEntity() })
    }

    private fun MemoryItemEntity.toModel() = MemoryItem(
        id = id, prompt = prompt, answer = answer, palace = palace, locus = locus,
        createdAt = createdAt, nextReviewAt = nextReviewAt, lastScore = lastScore,
        reviewCount = reviewCount, palaceNodeId = palaceNodeId,
    )

    private fun MemoryItem.toEntity() = MemoryItemEntity(
        id = id, prompt = prompt, answer = answer, palace = palace, locus = locus,
        createdAt = createdAt, nextReviewAt = nextReviewAt, lastScore = lastScore,
        reviewCount = reviewCount, palaceNodeId = palaceNodeId,
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
        const val PREFERENCES = "memory_improvement_local"
        const val LEGACY_ITEMS_KEY = "items_json"
    }
}
