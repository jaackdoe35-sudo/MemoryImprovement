package com.memoryimprovement.app

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

}
