package com.memoryimprovement.app

import org.junit.Assert.assertEquals
import org.junit.Test

class RecallSchedulerTest {
    @Test
    fun failedRecallSchedulesNextDay() {
        val now = 1_000_000L
        val result = RecallScheduler.schedule(previousReviews = 3, score = 0, now = now)
        assertEquals(1, result.intervalDays)
        assertEquals(now + 24L * 60L * 60L * 1000L, result.nextReviewAt)
    }

    @Test
    fun easyRecallUsesBaselineLongerInterval() {
        val result = RecallScheduler.schedule(previousReviews = 0, score = 5, now = 0L)
        assertEquals(2, result.intervalDays)
        assertEquals(5, result.score)
    }

    @Test
    fun retentionCheckpointsAreScheduledAtOneSevenAndThirtyDays() {
        val encodedAt = 123_456L
        val checkpoints = RetentionCheckpointPlan.dueTimes("fact-1", encodedAt)
        assertEquals(listOf(1, 7, 30), checkpoints.map { it.delayDays })
        assertEquals(listOf("fact-1-1", "fact-1-7", "fact-1-30"), checkpoints.map { it.id })
        assertEquals(
            listOf(1L, 7L, 30L).map { encodedAt + it * 24L * 60L * 60L * 1000L },
            checkpoints.map { it.dueAt },
        )
    }

    @Test
    fun retentionCheckpointsKeepTheSameItemIdentity() {
        val checkpoints = RetentionCheckpointPlan.dueTimes("stable-item-id", 0L)
        assertEquals(setOf("stable-item-id"), checkpoints.map { it.itemId }.toSet())
    }

    @Test
    fun orderedSequenceGroupsOnlyItemsWithSharedChunkAndSequencePositions() {
        val items = listOf(
            MemoryItem(id = "b", prompt = "Second", answer = "B", chunkLabel = "Protocol", sequenceOrder = 2),
            MemoryItem(id = "x", prompt = "Unsequenced", answer = "X", chunkLabel = "Protocol"),
            MemoryItem(id = "a", prompt = "First", answer = "A", chunkLabel = "Protocol", sequenceOrder = 1),
            MemoryItem(id = "c", prompt = "Other", answer = "C", chunkLabel = "Different", sequenceOrder = 1),
        )
        val groups = RecallSequencePlan.groups(items)
        assertEquals(1, groups.size)
        assertEquals("Protocol", groups.single().label)
        assertEquals(listOf("a", "b"), groups.single().items.map { it.id })
    }

    @Test
    fun singleNumberedItemDoesNotCreateSequence() {
        val groups = RecallSequencePlan.groups(
            listOf(MemoryItem(id = "a", prompt = "Only", answer = "A", chunkLabel = "Topic", sequenceOrder = 1)),
        )
        assertEquals(0, groups.size)
    }

    @Test
    fun interferencePlanFindsItemsWithStrongSharedTerms() {
        val items = listOf(
            MemoryItem(id = "a", prompt = "What does access control protect?", answer = "Access control protects restricted resources"),
            MemoryItem(id = "b", prompt = "What does access control protect?", answer = "Access control protects approved resources"),
            MemoryItem(id = "c", prompt = "How does photosynthesis work?", answer = "Plants convert sunlight into chemical energy"),
        )
        val pairs = MemoryInterferencePlan.find(items)
        assertEquals(1, pairs.size)
        assertEquals(setOf("access", "control", "protect", "resources"), pairs.single().sharedTerms.toSet())
        assertEquals("a", pairs.single().first.id)
        assertEquals("b", pairs.single().second.id)
    }

    @Test
    fun interferencePlanIgnoresSingleSharedWord() {
        val items = listOf(
            MemoryItem(id = "a", prompt = "Explain network routing", answer = "Packets follow a path"),
            MemoryItem(id = "b", prompt = "Explain network security", answer = "Traffic is filtered"),
        )
        assertEquals(0, MemoryInterferencePlan.find(items).size)
    }

    @Test
    fun weakMemoryPlanPrioritizesLowScoresAndOverdueItems() {
        val now = 100L * 24L * 60L * 60L * 1000L
        val strong = MemoryItem(id = "strong", prompt = "Strong", answer = "A", lastScore = 5, reviewCount = 4, nextReviewAt = now + 5L * 24L * 60L * 60L * 1000L)
        val weak = MemoryItem(id = "weak", prompt = "Weak", answer = "B", lastScore = 1, reviewCount = 2, nextReviewAt = now - 2L * 24L * 60L * 60L * 1000L)
        val untested = MemoryItem(id = "new", prompt = "New", answer = "C", reviewCount = 0, nextReviewAt = now)
        val ranked = WeakMemoryPlan.rank(listOf(strong, weak, untested), now)
        assertEquals("weak", ranked.first().item.id)
        assertEquals("Low last self-rating", ranked.first().reason)
        assertEquals("strong", ranked.last().item.id)
    }

    @Test
    fun weakMemoryPlanRespectsLimit() {
        val items = (1..6).map { MemoryItem(id = "$it", prompt = "P$it", answer = "A$it") }
        assertEquals(3, WeakMemoryPlan.rank(items, limit = 3).size)
    }

    @Test
    fun performanceSummaryUsesOnlyAttemptsForEachMode() {
        val attempts = listOf(
            RecallAttempt(itemId = "a", mode = RecallMode.Cued, correct = true, score = 4, latencyMs = 1000L),
            RecallAttempt(itemId = "b", mode = RecallMode.Cued, correct = false, score = 1, latencyMs = 3000L),
            RecallAttempt(itemId = "c", mode = RecallMode.Free, correct = true, score = 5, latencyMs = 2000L),
        )
        assertEquals(2, attempts.count { it.mode == RecallMode.Cued })
        assertEquals(1, attempts.count { it.mode == RecallMode.Free })
        assertEquals(1, attempts.filter { it.mode == RecallMode.Cued }.count { it.correct })
    }

    @Test
    fun scoreIsClampedToSupportedRange() {
        assertEquals(5, RecallScheduler.schedule(0, 9, 0L).score)
        assertEquals(0, RecallScheduler.schedule(0, -3, 0L).score)
    }
}
