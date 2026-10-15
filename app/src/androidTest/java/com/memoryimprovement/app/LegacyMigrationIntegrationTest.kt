package com.memoryimprovement.app

import androidx.sqlite.db.SupportSQLiteDatabase
import androidx.sqlite.db.SupportSQLiteOpenHelper
import androidx.sqlite.db.framework.FrameworkSQLiteOpenHelperFactory
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.util.UUID

@RunWith(AndroidJUnit4::class)
class LegacyMigrationIntegrationTest {
    private val context get() = InstrumentationRegistry.getInstrumentation().targetContext
    private val preferences get() = context.getSharedPreferences("memory_improvement_local", 0)
    private lateinit var databaseName: String
    private lateinit var repository: MemoryRepository

    @Before
    fun setUp() {
        preferences.edit()
            .remove("items_json")
            .remove("legacy_migration_pending")
            .commit()
        databaseName = "migration-test-${UUID.randomUUID()}.db"
        repository = MemoryRepository(context, databaseName)
    }

    @After
    fun tearDown() {
        repository.close()
        context.deleteDatabase(databaseName)
        preferences.edit()
            .remove("items_json")
            .remove("legacy_migration_pending")
            .commit()
    }

    @Test
    fun migration5To6PreservesExistingMemoriesAndAddsEncodingColumns() {
        val legacyDatabaseName = "migration-5-to-6-${UUID.randomUUID()}.db"
        val callback = object : SupportSQLiteOpenHelper.Callback(5) {
            override fun onCreate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    """CREATE TABLE memory_items (
                        id TEXT NOT NULL PRIMARY KEY,
                        prompt TEXT NOT NULL,
                        answer TEXT NOT NULL,
                        palace TEXT NOT NULL,
                        locus TEXT NOT NULL,
                        createdAt INTEGER NOT NULL,
                        nextReviewAt INTEGER NOT NULL,
                        lastScore INTEGER,
                        reviewCount INTEGER NOT NULL,
                        palaceNodeId TEXT
                    )""",
                )
                db.execSQL(
                    """INSERT INTO memory_items
                        (id, prompt, answer, palace, locus, createdAt, nextReviewAt, lastScore, reviewCount, palaceNodeId)
                        VALUES ('legacy-memory', 'Original prompt', 'Original answer', 'Home', 'Desk', 123, 456, 4, 7, NULL)""",
                )
            }

            override fun onUpgrade(db: SupportSQLiteDatabase, oldVersion: Int, newVersion: Int) = Unit
        }
        val helper = FrameworkSQLiteOpenHelperFactory().create(
            SupportSQLiteOpenHelper.Configuration.builder(context)
                .name(legacyDatabaseName)
                .callback(callback)
                .build(),
        )

        try {
            val db = helper.writableDatabase
            MemoryDatabase.MIGRATION_5_6.migrate(db)

            val defaults = mutableMapOf<String, String?>()
            val notNull = mutableMapOf<String, Int>()
            db.query("PRAGMA table_info(memory_items)").use { cursor ->
                while (cursor.moveToNext()) {
                    val columnName = cursor.getString(cursor.getColumnIndexOrThrow("name"))
                    defaults[columnName] = if (cursor.isNull(cursor.getColumnIndexOrThrow("dflt_value"))) {
                        null
                    } else {
                        cursor.getString(cursor.getColumnIndexOrThrow("dflt_value"))
                    }
                    notNull[columnName] = cursor.getInt(cursor.getColumnIndexOrThrow("notnull"))
                }
            }
            assertEquals("''", defaults["chunkLabel"])
            assertEquals("''", defaults["association"])
            assertEquals(1, notNull["chunkLabel"])
            assertEquals(1, notNull["association"])
            assertNull(defaults["sequenceOrder"])
            assertEquals(0, notNull["sequenceOrder"])

            db.query(
                "SELECT id, prompt, answer, palace, locus, createdAt, nextReviewAt, lastScore, reviewCount, palaceNodeId, chunkLabel, association, sequenceOrder FROM memory_items WHERE id = 'legacy-memory'",
            ).use { cursor ->
                assertTrue("The pre-migration memory must remain present", cursor.moveToFirst())
                assertEquals("legacy-memory", cursor.getString(0))
                assertEquals("Original prompt", cursor.getString(1))
                assertEquals("Original answer", cursor.getString(2))
                assertEquals("Home", cursor.getString(3))
                assertEquals("Desk", cursor.getString(4))
                assertEquals(123L, cursor.getLong(5))
                assertEquals(456L, cursor.getLong(6))
                assertEquals(4, cursor.getInt(7))
                assertEquals(7, cursor.getInt(8))
                assertTrue(cursor.isNull(9))
                assertEquals("", cursor.getString(10))
                assertEquals("", cursor.getString(11))
                assertTrue("sequenceOrder should default to NULL for existing rows", cursor.isNull(12))
                assertFalse("Only the seeded memory should exist", cursor.moveToNext())
            }
        } finally {
            helper.close()
            context.deleteDatabase(legacyDatabaseName)
        }
    }

    @Test
    fun malformedLegacyJsonIsPreservedAndCanBeRetriedAfterCorrection() = runBlocking {
        val malformed = "[{\"id\":\"broken\""
        preferences.edit().putString("items_json", malformed).commit()

        val failed = repository.loadItemsWithMigrationStatus()

        assertNotNull(failed.errorMessage)
        assertEquals(malformed, failed.preservedRawData)
        assertEquals(malformed, preferences.getString("items_json", null))
        assertEquals(true, preferences.getBoolean("legacy_migration_pending", false))
        assertEquals(emptyList<MemoryItem>(), failed.items)

        val corrected = """[{"id":"legacy-1","prompt":"Capital of France?","answer":"Paris"}]"""
        preferences.edit().putString("items_json", corrected).commit()
        val retried = repository.loadItemsWithMigrationStatus()

        assertNull(retried.errorMessage)
        assertEquals("legacy-1", retried.items.single().id)
        assertEquals("Capital of France?", retried.items.single().prompt)
        assertEquals(false, preferences.getBoolean("legacy_migration_pending", true))
    }

    @Test
    fun retryImportsMissingRecordsWithoutOverwritingExistingMemory() = runBlocking {
        repository.saveItems(
            listOf(MemoryItem(id = "shared-id", prompt = "User-edited prompt", answer = "Kept answer")),
        )
        val legacy = """[
            {"id":"shared-id","prompt":"Old prompt","answer":"Old answer"},
            {"id":"legacy-new","prompt":"Imported prompt","answer":"Imported answer"}
        ]""".trimIndent()
        preferences.edit()
            .putString("items_json", legacy)
            .putBoolean("legacy_migration_pending", true)
            .commit()

        val result = repository.loadItemsWithMigrationStatus()
        val existing = result.items.single { it.id == "shared-id" }
        val imported = result.items.single { it.id == "legacy-new" }

        assertEquals("User-edited prompt", existing.prompt)
        assertEquals("Kept answer", existing.answer)
        assertEquals("Imported prompt", imported.prompt)
        assertEquals(2, result.items.size)
        assertEquals(false, preferences.getBoolean("legacy_migration_pending", true))
    }

    @Test
    fun deletingMemoryRemovesOnlyItsAttemptsAndRetentionCheckpoints() = runBlocking {
        val deleted = MemoryItem(id = "delete-me", prompt = "Delete?", answer = "Yes")
        val kept = MemoryItem(id = "keep-me", prompt = "Keep?", answer = "Yes")
        repository.saveItems(listOf(deleted, kept))
        repository.saveAttempt(
            RecallAttempt(
                itemId = deleted.id,
                mode = RecallMode.Free,
                correct = false,
                score = 1,
                latencyMs = 1200L,
                recalledResponse = "No",
                reviewedAt = 100L,
            ),
        )
        repository.saveAttempt(
            RecallAttempt(
                itemId = kept.id,
                mode = RecallMode.Cued,
                correct = true,
                score = 5,
                latencyMs = 800L,
                recalledResponse = "Yes",
                reviewedAt = 200L,
            ),
        )

        repository.deleteMemory(deleted.id)

        assertEquals(listOf(kept.id), repository.loadItems().map { it.id })
        assertTrue(repository.loadRetentionCheckpoints().none { it.itemId == deleted.id })
        assertEquals(listOf(kept.id), repository.loadAttempts().map { it.itemId })
        assertEquals(3, repository.loadRetentionCheckpoints().size)
    }

    @Test
    fun retentionCheckpointCompletionIsAtomicAndCannotBeRepeated() = runBlocking {
        val item = MemoryItem(id = "retention-item", prompt = "Capital?", answer = "Paris")
        repository.saveItems(listOf(item))
        val checkpointId = "${item.id}-1"

        val first = repository.completeRetentionCheckpoint(
            id = checkpointId,
            score = 9,
            latencyMs = -50L,
            recalledResponse = "  Paris  ",
            accuracy = 4,
            completedAt = 123456L,
        )
        val second = repository.completeRetentionCheckpoint(
            id = checkpointId,
            score = 5,
            latencyMs = 100L,
            recalledResponse = "Paris",
            accuracy = 2,
            completedAt = 123999L,
        )

        assertTrue(first)
        assertFalse(second)
        val checkpoint = repository.loadRetentionCheckpoints().single { it.id == checkpointId }
        assertEquals(123456L, checkpoint.completedAt)
        assertEquals(5, checkpoint.score)
        assertEquals(0L, checkpoint.latencyMs)
        assertEquals("Paris", checkpoint.recalledResponse)
        assertEquals(2, checkpoint.accuracy)

        val attempts = repository.loadAttempts()
        assertEquals(1, attempts.size)
        assertEquals(RecallMode.Retention, attempts.single().mode)
        assertTrue(attempts.single().correct)
        assertEquals(0L, attempts.single().latencyMs)
        assertEquals(123456L, attempts.single().reviewedAt)
    }
    @Test
    fun deletingPalaceBranchUnlinksMemoriesAndPreservesOtherBranches() = runBlocking {
        repository.createPalaceNode("Home", PalaceNodeKind.Palace, null)
        repository.createPalaceNode("Office", PalaceNodeKind.Palace, null)
        val roots = repository.loadPalaceNodes()
        val home = roots.single { it.name == "Home" }
        val office = roots.single { it.name == "Office" }

        repository.createPalaceNode("Hallway", PalaceNodeKind.Route, home.id)
        val hallway = repository.loadPalaceNodes().single { it.name == "Hallway" }
        repository.createPalaceNode("Desk", PalaceNodeKind.Locus, hallway.id)
        val desk = repository.loadPalaceNodes().single { it.name == "Desk" }

        repository.saveItems(
            listOf(
                MemoryItem(
                    id = "home-memory",
                    prompt = "Home prompt",
                    answer = "Home answer",
                    palaceNodeId = desk.id,
                ),
                MemoryItem(
                    id = "office-memory",
                    prompt = "Office prompt",
                    answer = "Office answer",
                    palaceNodeId = office.id,
                ),
            ),
        )

        repository.deletePalaceNode(home.id)

        val remainingNodes = repository.loadPalaceNodes()
        assertTrue(remainingNodes.none { it.id == home.id || it.id == hallway.id || it.id == desk.id })
        assertTrue(remainingNodes.any { it.id == office.id })
        val memories = repository.loadItems().associateBy { it.id }
        assertNull(memories.getValue("home-memory").palaceNodeId)
        assertEquals(office.id, memories.getValue("office-memory").palaceNodeId)
    }

    @Test
    fun palaceRenameMoveRejectsCyclesAndPreservesSequenceEncodingData() = runBlocking {
        repository.createPalaceNode("Home", PalaceNodeKind.Palace, null)
        repository.createPalaceNode("Office", PalaceNodeKind.Palace, null)
        val roots = repository.loadPalaceNodes()
        val home = roots.single { it.name == "Home" }
        val office = roots.single { it.name == "Office" }

        repository.createPalaceNode("Hallway", PalaceNodeKind.Route, home.id)
        val hallway = repository.loadPalaceNodes().single { it.name == "Hallway" }
        repository.createPalaceNode("Desk", PalaceNodeKind.Locus, hallway.id)
        val desk = repository.loadPalaceNodes().single { it.name == "Desk" }

        repository.renamePalaceNode(desk.id, "Study desk")
        repository.movePalaceNode(desk.id, office.id)

        val moved = repository.loadPalaceNodes().single { it.id == desk.id }
        assertEquals("Study desk", moved.name)
        assertEquals(office.id, moved.parentId)

        var cycleRejected = false
        try {
            repository.movePalaceNode(hallway.id, desk.id)
        } catch (_: IllegalArgumentException) {
            cycleRejected = true
        }
        assertTrue("Moving a route beneath its own descendant must be rejected", cycleRejected)
        assertEquals(home.id, repository.loadPalaceNodes().single { it.id == hallway.id }.parentId)

        val encodedMemory = MemoryItem(
            id = "sequence-memory",
            prompt = "Name the sequence",
            answer = "First, second, third",
            palaceNodeId = desk.id,
            chunkLabel = "Ordered steps",
            association = "A vivid image links each step",
            sequenceOrder = 2,
        )
        repository.saveItems(listOf(encodedMemory))

        val persisted = repository.loadItems().single { it.id == encodedMemory.id }
        assertEquals(desk.id, persisted.palaceNodeId)
        assertEquals("Ordered steps", persisted.chunkLabel)
        assertEquals("A vivid image links each step", persisted.association)
        assertEquals(2, persisted.sequenceOrder)
    }

    @Test
    fun persistedMemoriesSurviveEscapedTsvExportAndImportRoundTrip() = runBlocking {
        val sourceItems = listOf(
            MemoryItem(
                id = "round-trip-1",
                prompt = "Question\twith tab\nand newline",
                answer = "Line one\r\nLine two\\\\literal",
                palace = "Home\tA",
                locus = "Room\nB",
                createdAt = 100L,
                nextReviewAt = 200L,
                lastScore = 4,
                reviewCount = 3,
                chunkLabel = "Chunk\\\\C",
                association = "Association\rD",
                sequenceOrder = 2,
            ),
            MemoryItem(
                id = "round-trip-2",
                prompt = "Second question",
                answer = "Second answer",
                palace = "Office",
                locus = "Desk",
                createdAt = 300L,
                nextReviewAt = 400L,
                chunkLabel = "Protocol",
                association = "A clear, distinctive mental image",
                sequenceOrder = 3,
            ),
        )
        repository.saveItems(sourceItems)
        val persistedBeforeExport = repository.loadItems().associateBy { it.id }
        val exported = MemoryExportPlan.toTsv(persistedBeforeExport.values.sortedBy { it.id })
        val parsed = MemoryImportPlan.parseTsv(exported)

        assertEquals(2, parsed.size)
        val importedDatabaseName = "import-round-trip-${UUID.randomUUID()}.db"
        val importedRepository = MemoryRepository(context, importedDatabaseName)
        try {
            importedRepository.saveItems(
                parsed.mapIndexed { index, row ->
                    MemoryItem(
                        id = "imported-${index + 1}",
                        prompt = row.prompt,
                        answer = row.answer,
                        palace = row.palace,
                        locus = row.locus,
                        chunkLabel = row.chunkLabel,
                        association = row.association,
                        sequenceOrder = row.sequenceOrder,
                    )
                },
            )
            val restored = importedRepository.loadItems().associateBy { it.prompt }
            for (original in sourceItems) {
                val restoredItem = restored.getValue(original.prompt)
                assertEquals(original.answer, restoredItem.answer)
                assertEquals(original.palace, restoredItem.palace)
                assertEquals(original.locus, restoredItem.locus)
                assertEquals(original.chunkLabel, restoredItem.chunkLabel)
                assertEquals(original.association, restoredItem.association)
                assertEquals(original.sequenceOrder, restoredItem.sequenceOrder)
            }
        } finally {
            importedRepository.close()
            context.deleteDatabase(importedDatabaseName)
        }
    }

    @Test
    fun retentionCompletionUpdatesAndPersistsMemoryScheduleAndHistory() = runBlocking {
        val item = MemoryItem(
            id = "retention-lifecycle",
            prompt = "What is working memory?",
            answer = "The system that temporarily holds and manipulates information",
            createdAt = 1_000L,
            nextReviewAt = 1_000L,
            reviewCount = 2,
        )
        repository.saveItems(listOf(item))
        val checkpointId = "${item.id}-7"
        val completedAt = 10L * 24L * 60L * 60L * 1000L

        val completed = repository.completeRetentionCheckpoint(
            id = checkpointId,
            score = 1,
            latencyMs = 2_500L,
            recalledResponse = " Temporarily holds information ",
            accuracy = 1,
            completedAt = completedAt,
        )

        assertTrue(completed)
        val updated = repository.loadItems().single { it.id == item.id }
        assertEquals(completedAt + 24L * 60L * 60L * 1000L, updated.nextReviewAt)
        assertEquals(1, updated.lastScore)
        assertEquals(3, updated.reviewCount)

        val checkpoint = repository.loadRetentionCheckpoints().single { it.id == checkpointId }
        assertEquals(completedAt, checkpoint.completedAt)
        assertEquals(1, checkpoint.score)
        assertEquals(2_500L, checkpoint.latencyMs)
        assertEquals("Temporarily holds information", checkpoint.recalledResponse)
        assertEquals(1, checkpoint.accuracy)

        val attempt = repository.loadAttempts().single()
        assertEquals(item.id, attempt.itemId)
        assertEquals(RecallMode.Retention, attempt.mode)
        assertEquals(false, attempt.correct)
        assertEquals(1, attempt.score)
        assertEquals(2_500L, attempt.latencyMs)
        assertEquals("Temporarily holds information", attempt.recalledResponse)
        assertEquals(completedAt, attempt.reviewedAt)

        repository.close()
        repository = MemoryRepository(context, databaseName)

        val reloaded = repository.loadItems().single { it.id == item.id }
        assertEquals(updated.nextReviewAt, reloaded.nextReviewAt)
        assertEquals(updated.lastScore, reloaded.lastScore)
        assertEquals(updated.reviewCount, reloaded.reviewCount)
        assertEquals(completedAt, repository.loadRetentionCheckpoints().single { it.id == checkpointId }.completedAt)
        assertEquals(1, repository.loadAttempts().size)
    }

    @Test
    fun destructiveOperationsRemoveDependentHistoryAndPreserveUnrelatedMemories() = runBlocking {
        val branchMemory = MemoryItem(
            id = "branch-memory",
            prompt = "Where is the study desk?",
            answer = "At the end of the hallway",
            createdAt = 100L,
        )
        val deletedMemory = MemoryItem(
            id = "deleted-memory",
            prompt = "What should be removed?",
            answer = "The memory and its dependent history",
            createdAt = 200L,
        )
        repository.createPalaceNode("Home", PalaceNodeKind.Palace, null)
        val home = repository.loadPalaceNodes().single { it.name == "Home" }
        repository.createPalaceNode("Hallway", PalaceNodeKind.Route, home.id)
        val hallway = repository.loadPalaceNodes().single { it.name == "Hallway" }
        repository.createPalaceNode("Study desk", PalaceNodeKind.Locus, hallway.id)
        val locus = repository.loadPalaceNodes().single { it.name == "Study desk" }

        repository.saveItems(listOf(branchMemory.copy(palaceNodeId = locus.id), deletedMemory))
        repository.saveAttempt(
            RecallAttempt(
                itemId = deletedMemory.id,
                mode = RecallMode.Cued,
                correct = false,
                score = 1,
                latencyMs = 900L,
                recalledResponse = "An unrelated response",
                reviewedAt = 300L,
            ),
        )
        assertTrue(repository.loadRetentionCheckpoints().any { it.itemId == deletedMemory.id })
        assertTrue(repository.loadAttempts().any { it.itemId == deletedMemory.id })

        repository.deleteMemory(deletedMemory.id)

        assertFalse(repository.loadItems().any { it.id == deletedMemory.id })
        assertFalse(repository.loadRetentionCheckpoints().any { it.itemId == deletedMemory.id })
        assertFalse(repository.loadAttempts().any { it.itemId == deletedMemory.id })

        repository.deletePalaceNode(home.id)

        val remainingMemory = repository.loadItems().single { it.id == branchMemory.id }
        assertNull(remainingMemory.palaceNodeId)
        assertTrue(repository.loadPalaceNodes().isEmpty())
        assertEquals("Where is the study desk?", remainingMemory.prompt)
        assertEquals("At the end of the hallway", remainingMemory.answer)
    }

}
