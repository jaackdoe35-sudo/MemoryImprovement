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

enum class RecallMode { Cued, Free, Reverse, Sequence, Discrimination }

data class RecallSequence(val label: String, val items: List<MemoryItem>)

data class InterferencePair(
    val first: MemoryItem,
    val second: MemoryItem,
    val sharedTerms: List<String>,
    val overlap: Double,
)

/** Lexical heuristic only; this does not detect synonym or conceptual similarity. */
object MemoryInterferencePlan {
    private val ignored = setOf("about", "after", "also", "been", "being", "between", "could", "does", "each", "from", "have", "into", "more", "most", "only", "other", "over", "same", "some", "such", "than", "that", "their", "them", "then", "there", "these", "they", "this", "those", "through", "under", "very", "what", "when", "where", "which", "while", "with", "would", "your", "will", "must", "should", "using", "used")

    private fun terms(item: MemoryItem): Set<String> =
        (item.prompt + " " + item.answer).lowercase()
            .split(Regex("[^a-z0-9]+"))
            .filter { it.length >= 4 && it !in ignored }
            .toSet()

    fun find(memories: List<MemoryItem>, limit: Int = 8): List<InterferencePair> {
        val candidates = memories.map { it to terms(it) }.filter { it.second.size >= 2 }
        val pairs = mutableListOf<InterferencePair>()
        for (i in candidates.indices) {
            for (j in i + 1 until candidates.size) {
                val (first, a) = candidates[i]
                val (second, b) = candidates[j]
                val shared = a.intersect(b).sorted()
                val overlap = shared.size.toDouble() / minOf(a.size, b.size).coerceAtLeast(1)
                if (shared.size >= 2 && overlap >= 0.5) {
                    pairs += InterferencePair(first, second, shared, overlap)
                }
            }
        }
        return pairs.sortedWith(compareByDescending<InterferencePair> { it.overlap }
            .thenByDescending { it.sharedTerms.size }
            .thenBy { it.first.prompt.lowercase() })
            .take(limit)
    }
}

object RecallSequencePlan {
    /** Groups only explicitly numbered items; each group must contain at least two distinct facts. */
    fun groups(memories: List<MemoryItem>): List<RecallSequence> = memories
        .filter { it.chunkLabel.isNotBlank() && (it.sequenceOrder ?: 0) > 0 }
        .groupBy { it.chunkLabel.trim().lowercase() }
        .mapNotNull { (key, items) ->
            val ordered = items.sortedWith(compareBy<MemoryItem> { it.sequenceOrder }.thenBy { it.createdAt }.thenBy { it.id })
            if (ordered.size < 2) null else RecallSequence(
                label = ordered.first().chunkLabel.trim().ifBlank { key },
                items = ordered,
            )
        }
        .sortedBy { it.label.lowercase() }
}

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
