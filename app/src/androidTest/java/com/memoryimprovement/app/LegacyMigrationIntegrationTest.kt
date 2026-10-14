package com.memoryimprovement.app

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
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
}
