package com.memoryimprovement.app

data class MemoryItem(
    val id: String,
    val prompt: String,
    val answer: String,
    val palace: String = "",
    val locus: String = "",
    val createdAt: Long = System.currentTimeMillis(),
    val nextReviewAt: Long = createdAt,
    val lastScore: Int? = null,
    val reviewCount: Int = 0,
)

enum class RecallMode { Cued, Free, Reverse }

data class RecallAttempt(
    val itemId: String,
    val mode: RecallMode,
    val correct: Boolean,
    val score: Int,
    val latencyMs: Long,
    val reviewedAt: Long = System.currentTimeMillis(),
)

data class RecallOutcome(val nextReviewAt: Long, val intervalDays: Int, val score: Int)

/** Transparent baseline; evaluate against measured delayed recall before claiming it is optimal. */
object RecallScheduler {
    fun schedule(previousReviews: Int, score: Int, now: Long = System.currentTimeMillis()): RecallOutcome {
        val normalized = score.coerceIn(0, 5)
        val interval = when {
            normalized <= 1 -> 1
            normalized == 2 -> 2
            normalized == 3 -> listOf(1, 3, 7, 14)[previousReviews.coerceIn(0, 3)]
            else -> listOf(2, 5, 10, 21, 30)[previousReviews.coerceIn(0, 4)]
        }
        return RecallOutcome(now + interval * 24L * 60L * 60L * 1000L, interval, normalized)
    }
}
