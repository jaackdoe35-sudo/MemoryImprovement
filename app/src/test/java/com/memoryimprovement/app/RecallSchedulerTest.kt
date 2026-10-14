package com.memoryimprovement.app

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class RecallSchedulerTest {
    @Test
    fun palaceMovePlanAllowsValidMoveAndRejectsCyclesOrInvalidParents() {
        val nodes = listOf(
            PalaceNode(id = "palaceA", name = "Home", kind = PalaceNodeKind.Palace, parentId = null),
            PalaceNode(id = "routeA", name = "Hall", kind = PalaceNodeKind.Route, parentId = "palaceA"),
            PalaceNode(id = "areaA", name = "Kitchen", kind = PalaceNodeKind.Area, parentId = "routeA"),
            PalaceNode(id = "locusA", name = "Fridge", kind = PalaceNodeKind.Locus, parentId = "areaA"),
            PalaceNode(id = "palaceB", name = "Office", kind = PalaceNodeKind.Palace, parentId = null),
        )
        assertTrue(PalaceMovePlan.isValidParent(nodes, "routeA", "palaceB"))
        assertTrue(PalaceMovePlan.isValidParent(nodes, "locusA", "palaceB"))
        assertFalse(PalaceMovePlan.isValidParent(nodes, "routeA", "locusA"))
        assertFalse(PalaceMovePlan.isValidParent(nodes, "routeA", "routeA"))
        assertFalse(PalaceMovePlan.isValidParent(nodes, "palaceA", "palaceB"))
        assertFalse(PalaceMovePlan.isValidParent(nodes, "missing", "palaceB"))
    }

    @Test
    fun malformedLegacyJsonIsReportedAndOriginalDataPreserved() {
        val raw = """[{"id":"broken""""
        val result = LegacyMigrationPlan.decode(raw) { throw IllegalArgumentException("malformed JSON") }
        assertEquals(emptyList<MemoryItem>(), result.items)
        assertEquals(raw, result.preservedRawData)
        assertEquals(
            "Legacy memory data could not be imported: malformed JSON. The original data has been preserved.",
            result.errorMessage,
        )
    }

    @Test
    fun validLegacyMigrationResultContainsDecodedItemsWithoutError() {
        val item = MemoryItem(id = "legacy", prompt = "P", answer = "A")
        val result = LegacyMigrationPlan.decode("valid") { listOf(item) }
        assertEquals(listOf(item), result.items)
        assertEquals(null, result.errorMessage)
        assertEquals(null, result.preservedRawData)
    }

    @Test
    fun memoryDeletionRemovesOnlyMatchingItemFromInMemoryCollections() {
        val items = listOf(
            MemoryItem(id = "keep", prompt = "Keep", answer = "A"),
            MemoryItem(id = "delete", prompt = "Delete", answer = "B"),
        )
        assertEquals(listOf("keep"), items.filterNot { it.id == "delete" }.map { it.id })
    }

    @Test
    fun libraryPlanSearchesAndPrioritizesDueReviews() {
        val now = 100_000L
        val due = MemoryItem(id = "due", prompt = "Cell biology", answer = "Mitochondria", nextReviewAt = now - 1L, createdAt = 1L)
        val future = MemoryItem(id = "future", prompt = "Cell division", answer = "Mitosis", nextReviewAt = now + 1L, createdAt = 2L)
        val all = MemoryLibraryPlan.filterAndSort(listOf(future, due), "cell", dueOnly = false, now = now)
        assertEquals(listOf("due", "future"), all.map { it.id })
        val dueOnly = MemoryLibraryPlan.filterAndSort(listOf(future, due), "CELL", dueOnly = true, now = now)
        assertEquals(listOf("due"), dueOnly.map { it.id })
    }

    @Test
    fun bulkImportParsesRequiredAndOptionalColumns() {
        val rows = MemoryImportPlan.parseTsv("Cell question\tMitochondria\tBiology\tDesk\tCell\tPowerhouse\t2")
        assertEquals(1, rows.size)
        assertEquals("Cell question", rows.single().prompt)
        assertEquals("Mitochondria", rows.single().answer)
        assertEquals("Biology", rows.single().palace)
        assertEquals(2, rows.single().sequenceOrder)
    }

    @Test
    fun exportTsvRoundTripsThroughImportParser() {
        val item = MemoryItem(
            id = "export", prompt = "What is ATP?", answer = "Cell energy",
            palace = "Biology", locus = "Desk", chunkLabel = "Cell", association = "Battery",
            sequenceOrder = 3,
        )
        val row = MemoryImportPlan.parseTsv(MemoryExportPlan.toTsv(listOf(item))).single()
        assertEquals(item.prompt, row.prompt)
        assertEquals(item.answer, row.answer)
        assertEquals(item.palace, row.palace)
        assertEquals(item.locus, row.locus)
        assertEquals(item.chunkLabel, row.chunkLabel)
        assertEquals(item.association, row.association)
        assertEquals(item.sequenceOrder, row.sequenceOrder)
    }

    @Test
    fun exportImportPreservesTabsNewlinesCarriageReturnsAndBackslashes() {
        val item = MemoryItem(
            id = "escaped",
            prompt = "Question\twith tab\nand newline",
            answer = "Line one\r\nLine two\\literal",
            palace = "Palace\tA",
            locus = "Room\nB",
            chunkLabel = "Chunk\\C",
            association = "Association\rD",
            sequenceOrder = 4,
        )
        val row = MemoryImportPlan.parseTsv(MemoryExportPlan.toTsv(listOf(item))).single()
        assertEquals(item.prompt, row.prompt)
        assertEquals(item.answer, row.answer)
        assertEquals(item.palace, row.palace)
        assertEquals(item.locus, row.locus)
        assertEquals(item.chunkLabel, row.chunkLabel)
        assertEquals(item.association, row.association)
        assertEquals(item.sequenceOrder, row.sequenceOrder)
    }

    @Test
    fun legacyTsvWithoutEscapesStillParses() {
        val row = MemoryImportPlan.parseTsv("Question?\tAnswer\tPalace\tDesk").single()
        assertEquals("Question?", row.prompt)
        assertEquals("Answer", row.answer)
        assertEquals("Palace", row.palace)
        assertEquals("Desk", row.locus)
    }

    @Test
    fun legacyTsvPreservesLiteralBackslashSequences() {
        val row = MemoryImportPlan.parseTsv("Question\\tmark\tAnswer\\n").single()
        assertEquals("Question\\tmark", row.prompt)
        assertEquals("Answer\\n", row.answer)
    }

    @Test
    fun importPreviewCountsNewRowsAndDuplicatesBeforeSaving() {
        val existing = listOf(MemoryItem(id = "old", prompt = "Capital?", answer = "Lagos"))
        val parsed = MemoryImportPlan.parseTsv(
            "Capital?\tLagos\nLargest ocean?\tPacific\nHighest mountain?\tEverest"
        )
        val preview = MemoryImportPlan.excludeDuplicates(parsed, existing)
        assertEquals(2, preview.rows.size)
        assertEquals(1, preview.duplicatesSkipped)
        assertEquals("Largest ocean?", preview.rows.first().prompt)
    }

    @Test
    fun bulkImportSkipsExistingAndRepeatedPromptAnswerPairs() {
        val existing = listOf(MemoryItem(id = "existing", prompt = "Capital?", answer = "Lagos"))
        val imported = listOf(
            MemoryImportRow(prompt = " capital? ", answer = "LAGOS"),
            MemoryImportRow(prompt = "Capital?", answer = "Lagos"),
            MemoryImportRow(prompt = "Largest ocean?", answer = "Pacific"),
        )
        val result = MemoryImportPlan.excludeDuplicates(imported, existing)
        assertEquals(1, result.rows.size)
        assertEquals("Largest ocean?", result.rows.single().prompt)
        assertEquals(2, result.duplicatesSkipped)
    }

    @Test
    fun bulkImportRejectsMissingAnswer() {
        val error = runCatching { MemoryImportPlan.parseTsv("Prompt only\t") }.exceptionOrNull()
        assertEquals(true, error is IllegalArgumentException)
    }

    @Test
    fun recallAnalyticsSummarizesEachModeWithoutMixingGroups() {
        val attempts = listOf(
            RecallAttempt("a", RecallMode.Free, true, 4, 1000L),
            RecallAttempt("b", RecallMode.Free, false, 2, 3000L),
            RecallAttempt("c", RecallMode.Cued, true, 5, 2000L),
        )
        val summaries = RecallAnalytics.byMode(attempts)
        val free = summaries.single { it.mode == RecallMode.Free }
        val cued = summaries.single { it.mode == RecallMode.Cued }
        assertEquals(2, free.attempts)
        assertEquals(1, free.correct)
        assertEquals(50, free.accuracyPercent)
        assertEquals(2000L, free.meanLatencyMs)
        assertEquals(1, cued.attempts)
        assertEquals(100, cued.accuracyPercent)
    }

    @Test
    fun successfulCurrentRecallResetsPreviousFailureStreak() {
        val recent = listOf(
            RecallAttempt("a", RecallMode.Free, false, 0, 1000L, reviewedAt = 20L),
            RecallAttempt("a", RecallMode.Free, false, 0, 1000L, reviewedAt = 10L),
        )
        assertEquals(0, RecallFailurePlan.consecutiveFailuresAfter(recent, currentCorrect = true))
    }

    @Test
    fun failedCurrentRecallExtendsOnlyTheMostRecentFailureStreak() {
        val recent = listOf(
            RecallAttempt("a", RecallMode.Free, false, 0, 1000L, reviewedAt = 30L),
            RecallAttempt("a", RecallMode.Free, true, 5, 1000L, reviewedAt = 20L),
            RecallAttempt("a", RecallMode.Free, false, 0, 1000L, reviewedAt = 10L),
        )
        assertEquals(2, RecallFailurePlan.consecutiveFailuresAfter(recent, currentCorrect = false))
    }

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
    fun palaceDeletionPlanIncludesOnlyTheSelectedBranch() {
        val nodes = listOf(
            PalaceNode("home", "Home", PalaceNodeKind.Palace, null),
            PalaceNode("route", "Route", PalaceNodeKind.Route, "home"),
            PalaceNode("room", "Room", PalaceNodeKind.Area, "route"),
            PalaceNode("desk", "Desk", PalaceNodeKind.Locus, "room"),
            PalaceNode("other", "Other palace", PalaceNodeKind.Palace, null),
            PalaceNode("other-locus", "Other locus", PalaceNodeKind.Locus, "other"),
        )
        assertEquals(setOf("home", "route", "room", "desk"), PalaceHierarchyPlan.descendantIds(nodes, "home"))
        assertEquals(setOf("other", "other-locus"), PalaceHierarchyPlan.descendantIds(nodes, "other"))
    }

    @Test
    fun palaceDeletionPlanReturnsEmptyForMissingNode() {
        assertEquals(emptySet<String>(), PalaceHierarchyPlan.descendantIds(emptyList(), "missing"))
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
        assertEquals(setOf("access", "control", "protect", "protects", "resources"), pairs.single().sharedTerms.toSet())
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
    fun schedulerShortensIntervalAfterTwoConsecutiveFailures() {
        val now = 10_000L
        val outcome = RecallScheduler.schedule(
            previousReviews = 4,
            score = 5,
            now = now,
            consecutiveFailures = 2,
        )
        assertEquals(1, outcome.intervalDays)
        assertEquals(now + 24L * 60L * 60L * 1000L, outcome.nextReviewAt)
        assertEquals(5, outcome.score)
    }

    @Test
    fun schedulerKeepsNormalIntervalWhenFailuresAreNotConsecutive() {
        val outcome = RecallScheduler.schedule(
            previousReviews = 4,
            score = 5,
            now = 0L,
            consecutiveFailures = 1,
        )
        assertEquals(30, outcome.intervalDays)
    }

    @Test
    fun weakMemoryPlanRaisesItemsWithRepeatedIncorrectAttempts() {
        val now = 100L * 24L * 60L * 60L * 1000L
        val repeated = MemoryItem(id = "repeat", prompt = "Repeat", answer = "A", reviewCount = 3, nextReviewAt = now + 2L * 24L * 60L * 60L * 1000L)
        val routine = MemoryItem(id = "routine", prompt = "Routine", answer = "B", reviewCount = 3, nextReviewAt = now + 2L * 24L * 60L * 60L * 1000L)
        val attempts = listOf(
            RecallAttempt(itemId = "repeat", mode = RecallMode.Free, correct = false, score = 0, latencyMs = 1000L, reviewedAt = 1L),
            RecallAttempt(itemId = "repeat", mode = RecallMode.Free, correct = false, score = 0, latencyMs = 1200L, reviewedAt = 2L),
        )
        val ranked = WeakMemoryPlan.rank(listOf(routine, repeated), attempts, now)
        assertEquals("repeat", ranked.first().item.id)
        assertEquals("Repeated incorrect attempts (2)", ranked.first().reason)
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
    fun recallAnalyticsSummarizesAccuracyAndLatencyPerMode() {
        val attempts = listOf(
            RecallAttempt(itemId = "a", mode = RecallMode.Cued, correct = true, score = 5, latencyMs = 1000L),
            RecallAttempt(itemId = "b", mode = RecallMode.Cued, correct = false, score = 0, latencyMs = 3000L),
            RecallAttempt(itemId = "c", mode = RecallMode.Free, correct = true, score = 5, latencyMs = 2000L),
        )
        val summaries = RecallAnalytics.byMode(attempts)
        val cued = summaries.single { it.mode == RecallMode.Cued }
        assertEquals(2, cued.attempts)
        assertEquals(1, cued.correct)
        assertEquals(50, cued.accuracyPercent)
        assertEquals(2000L, cued.meanLatencyMs)
        assertEquals(1, summaries.single { it.mode == RecallMode.Free }.attempts)
    }

    @Test
    fun recallAnalyticsReturnsEmptyForNoAttempts() {
        assertEquals(emptyList<RecallModeSummary>(), RecallAnalytics.byMode(emptyList()))
    }

    @Test
    fun repeatedErrorPlanShowsOnlyMemoriesWithMultipleRecordedFailures() {
        val memories = listOf(
            MemoryItem(id = "a", prompt = "A", answer = "Alpha"),
            MemoryItem(id = "b", prompt = "B", answer = "Beta"),
        )
        val attempts = listOf(
            RecallAttempt("a", RecallMode.Free, false, 0, 1000L, "wrong one", reviewedAt = 10L),
            RecallAttempt("a", RecallMode.Cued, false, 0, 1200L, "wrong two", reviewedAt = 20L),
            RecallAttempt("b", RecallMode.Cued, false, 0, 1500L, "wrong once", reviewedAt = 15L),
        )
        val repeated = RepeatedErrorPlan.find(memories, attempts)
        assertEquals(1, repeated.size)
        assertEquals("a", repeated.single().item.id)
        assertEquals(2, repeated.single().incorrectAttempts)
        assertEquals("wrong two", repeated.single().lastIncorrectResponse)
    }

    @Test
    fun repeatedErrorPlanHonorsLimitAndIgnoresDeletedMemoryIds() {
        val memories = listOf(MemoryItem(id = "a", prompt = "A", answer = "Alpha"))
        val attempts = listOf(
            RecallAttempt("a", RecallMode.Free, false, 0, 1000L),
            RecallAttempt("a", RecallMode.Cued, false, 0, 1000L),
            RecallAttempt("deleted", RecallMode.Cued, false, 0, 1000L),
            RecallAttempt("deleted", RecallMode.Cued, false, 0, 1000L),
        )
        assertEquals(1, RepeatedErrorPlan.find(memories, attempts, limit = 1).size)
    }

    @Test
    fun retentionAnalyticsSeparatesSelfRatingFromUserCheckedAccuracy() {
        val checkpoints = listOf(
            RetentionCheckpoint("a-1", "a", 1, 10L, completedAt = 20L, score = 4, latencyMs = 1000L, accuracy = 2),
            RetentionCheckpoint("b-1", "b", 1, 10L, completedAt = 21L, score = 2, latencyMs = 3000L, accuracy = 0),
            RetentionCheckpoint("c-1", "c", 1, 10L, completedAt = 22L, score = 3, latencyMs = 2000L, accuracy = 1),
            RetentionCheckpoint("a-7", "a", 7, 20L),
        )
        val summaries = RetentionAnalytics.byDelay(checkpoints)
        val day1 = summaries.single { it.delayDays == 1 }
        assertEquals(3, day1.total)
        assertEquals(3, day1.completed)
        assertEquals(3.0, day1.meanSelfRating!!, 0.001)
        assertEquals(33, day1.checkedAccuracyPercent)
        assertEquals(1, day1.fullyCorrectCount)
        assertEquals(1, day1.partiallyCorrectCount)
        assertEquals(1, day1.incorrectCount)
        assertEquals(3, day1.checkedAccuracyCount)
        assertEquals(2000L, day1.meanLatencyMs)
        val day7 = summaries.single { it.delayDays == 7 }
        assertEquals(0, day7.completed)
        assertEquals(null, day7.meanSelfRating)
        assertEquals(null, day7.checkedAccuracyPercent)
        assertEquals(3, summaries.size)
    }

    @Test
    fun memoryProgressComparesFirstAndLatestAttemptAndOmitsSingleAttempts() {
        val memories = listOf(
            MemoryItem(id = "improved", prompt = "Improved", answer = "A"),
            MemoryItem(id = "single", prompt = "Single", answer = "B"),
        )
        val attempts = listOf(
            RecallAttempt("improved", RecallMode.Free, false, 0, 3000L, reviewedAt = 10L),
            RecallAttempt("improved", RecallMode.Free, true, 5, 1000L, reviewedAt = 20L),
            RecallAttempt("single", RecallMode.Cued, true, 5, 500L, reviewedAt = 15L),
            RecallAttempt("deleted", RecallMode.Cued, true, 5, 500L, reviewedAt = 30L),
        )
        val summary = MemoryProgressPlan.summarize(memories, attempts).single()
        assertEquals("improved", summary.item.id)
        assertEquals(2, summary.attemptCount)
        assertEquals(1, summary.correctCount)
        assertEquals(false, summary.firstCorrect)
        assertEquals(true, summary.latestCorrect)
        assertEquals(2000L, summary.meanLatencyMs)
    }

    @Test
    fun retentionReviewPlanUpdatesScheduleAndReviewCountFromEffortScore() {
        val original = MemoryItem(
            id = "retained", prompt = "P", answer = "A",
            createdAt = 1L, nextReviewAt = 2L, lastScore = 4, reviewCount = 2,
        )
        val completedAt = 10L * 24L * 60L * 60L * 1000L
        val updated = RetentionReviewPlan.apply(original, effortScore = 1, completedAt = completedAt)
        assertEquals(completedAt + 24L * 60L * 60L * 1000L, updated.nextReviewAt)
        assertEquals(1, updated.lastScore)
        assertEquals(3, updated.reviewCount)
        assertEquals(original.id, updated.id)
    }

    @Test
    fun retentionReviewPlanClampsEffortScore() {
        val item = MemoryItem(id = "a", prompt = "P", answer = "A")
        assertEquals(0, RetentionReviewPlan.apply(item, -10, 100L).lastScore)
        assertEquals(5, RetentionReviewPlan.apply(item, 99, 100L).lastScore)
    }

    @Test
    fun scoreIsClampedToSupportedRange() {
        assertEquals(5, RecallScheduler.schedule(0, 9, 0L).score)
        assertEquals(0, RecallScheduler.schedule(0, -3, 0L).score)
    }
}
