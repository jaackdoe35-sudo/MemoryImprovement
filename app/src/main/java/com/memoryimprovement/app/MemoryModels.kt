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
    val palaceNodeId: String? = null,
    val chunkLabel: String = "",
    val association: String = "",
    val sequenceOrder: Int? = null,
)

enum class PalaceNodeKind(val label: String) {
    Palace("Palace"), Route("Route"), Area("Area"), Locus("Locus")
}

data class PalaceNode(
    val id: String,
    val name: String,
    val kind: PalaceNodeKind,
    val parentId: String?,
    val position: Int = 0,
    val createdAt: Long = System.currentTimeMillis(),
)

enum class RecallMode { Cued, Free, Reverse }

data class RecallAttempt(
    val itemId: String,
    val mode: RecallMode,
    val correct: Boolean,
    val score: Int,
    val latencyMs: Long,
    val recalledResponse: String? = null,
    val reviewedAt: Long = System.currentTimeMillis(),
)

data class RecallOutcome(val nextReviewAt: Long, val intervalDays: Int, val score: Int)

data class RetentionCheckpoint(
    val id: String,
    val itemId: String,
    val delayDays: Int,
    val dueAt: Long,
    val completedAt: Long? = null,
    val score: Int? = null,
    val latencyMs: Long? = null,
    val recalledResponse: String? = null,
    /** User-checked answer accuracy: 0 incorrect, 1 partial, 2 correct; null for legacy results. */
    val accuracy: Int? = null,
)

object RetentionCheckpointPlan {
    val delaysDays = listOf(1, 7, 30)

    fun dueTimes(itemId: String, encodedAt: Long): List<RetentionCheckpoint> =
        delaysDays.map { days ->
            RetentionCheckpoint(
                id = "$itemId-$days",
                itemId = itemId,
                delayDays = days,
                dueAt = encodedAt + days * 24L * 60L * 60L * 1000L,
            )
        }
}

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
