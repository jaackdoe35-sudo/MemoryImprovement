package com.memoryimprovement.app

import android.content.Context
import androidx.room.Room
import org.json.JSONArray

/** Offline-first Room persistence, with one-time migration from the original preference store. */
class MemoryRepository(context: Context) {
    private val appContext = context.applicationContext
    private val database = Room.databaseBuilder(
        appContext,
        MemoryDatabase::class.java,
        "memory_improvement.db",
    ).build()
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
        reviewCount = reviewCount,
    )

    private fun MemoryItem.toEntity() = MemoryItemEntity(
        id = id, prompt = prompt, answer = answer, palace = palace, locus = locus,
        createdAt = createdAt, nextReviewAt = nextReviewAt, lastScore = lastScore,
        reviewCount = reviewCount,
    )

    private companion object {
        const val PREFERENCES = "memory_improvement_local"
        const val LEGACY_ITEMS_KEY = "items_json"
    }
}
