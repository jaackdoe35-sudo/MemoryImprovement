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
    fun scoreIsClampedToSupportedRange() {
        assertEquals(5, RecallScheduler.schedule(0, 9, 0L).score)
        assertEquals(0, RecallScheduler.schedule(0, -3, 0L).score)
    }
}
