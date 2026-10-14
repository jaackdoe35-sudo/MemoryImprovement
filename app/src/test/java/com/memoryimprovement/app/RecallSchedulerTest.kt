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
    fun scoreIsClampedToSupportedRange() {
        assertEquals(5, RecallScheduler.schedule(0, 9, 0L).score)
        assertEquals(0, RecallScheduler.schedule(0, -3, 0L).score)
    }
}
