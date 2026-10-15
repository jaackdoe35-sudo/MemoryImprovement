package com.memoryimprovement.app

import android.os.Bundle
import android.os.SystemClock
import android.net.Uri
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.launch
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import java.util.UUID

class MainActivity : ComponentActivity() {
    private var pendingExport: String = ""
    private var pendingImportConsumer: ((Result<String>) -> Unit)? = null
    private var pendingBackupImportConsumer: ((Result<String>) -> Unit)? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val createExportFile = registerForActivityResult(ActivityResultContracts.CreateDocument("text/tab-separated-values")) { uri: Uri? ->
            if (uri != null) {
                contentResolver.openOutputStream(uri)?.bufferedWriter()?.use { it.write(pendingExport) }
            }
        }
        val createLegacyBackup = registerForActivityResult(ActivityResultContracts.CreateDocument("application/json")) { uri: Uri? ->
            if (uri != null) {
                contentResolver.openOutputStream(uri)?.bufferedWriter()?.use { it.write(pendingExport) }
            }
        }
        val openImportFile = registerForActivityResult(ActivityResultContracts.OpenDocument()) { uri: Uri? ->
            if (uri != null) {
                runCatching {
                    contentResolver.openInputStream(uri)?.bufferedReader()?.use { it.readText() }
                        ?: throw IllegalStateException("Could not read the selected file.")
                }.onSuccess { content ->
                    pendingImportConsumer?.invoke(Result.success(content))
                }.onFailure { error ->
                    pendingImportConsumer?.invoke(Result.failure(error))
                }
            }
            pendingImportConsumer = null
        }
        val openBackupFile = registerForActivityResult(ActivityResultContracts.OpenDocument()) { uri: Uri? ->
            if (uri != null) {
                runCatching {
                    contentResolver.openInputStream(uri)?.bufferedReader()?.use { it.readText() }
                        ?: throw IllegalStateException("Could not read the selected backup file.")
                }.onSuccess { content ->
                    pendingBackupImportConsumer?.invoke(Result.success(content))
                }.onFailure { error ->
                    pendingBackupImportConsumer?.invoke(Result.failure(error))
                }
            }
            pendingBackupImportConsumer = null
        }
        val repository = MemoryRepository(applicationContext)
        lifecycleScope.launch {
            val initialMigrationResult = repository.loadItemsWithMigrationStatus()
            val initialItems = initialMigrationResult.items
            val initialNodes = repository.loadPalaceNodes()
            val initialAttempts = repository.loadAttempts()
            val initialCheckpoints = repository.loadRetentionCheckpoints()
            setContent {
                MaterialTheme(
                    colorScheme = lightColorScheme(
                        primary = Color(0xFF245B4A),
                        onPrimary = Color.White,
                        secondary = Color(0xFFB96F43),
                        background = Color(0xFFF6F4EF),
                        surface = Color(0xFFFFFEFA),
                        onSurface = Color(0xFF202820),
                        onSurfaceVariant = Color(0xFF626B63),
                        primaryContainer = Color(0xFFDCEBE2),
                        onPrimaryContainer = Color(0xFF173E31),
                    ),
                ) {
                    Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
                        MemoryApp(
                            initialItems = initialItems,
                            initialNodes = initialNodes,
                            initialAttempts = initialAttempts,
                            initialCheckpoints = initialCheckpoints,
                            initialMigrationResult = initialMigrationResult,
                            onSave = { updated -> lifecycleScope.launch { repository.saveItems(updated) } },
                            onExportMemories = { items ->
                                pendingExport = MemoryExportPlan.toTsv(items)
                                createExportFile.launch("memory-improvement-memories.tsv")
                            },
                            onExportLegacyData = { raw ->
                                pendingExport = raw
                                createLegacyBackup.launch("memory-improvement-legacy-backup.json")
                            },
                            onExportFullBackup = {
                                lifecycleScope.launch {
                                    runCatching { MemoryBackupPlan.encode(repository.createBackupSnapshot()) }
                                        .onSuccess { raw ->
                                            pendingExport = raw
                                            createLegacyBackup.launch("memory-improvement-backup.json")
                                        }
                                }
                            },
                            onChooseBackupFile = { consume ->
                                pendingBackupImportConsumer = consume
                                openBackupFile.launch(arrayOf("application/json", "application/octet-stream", "text/*"))
                            },
                            onRestoreBackup = { snapshot, onRestored ->
                                lifecycleScope.launch {
                                    runCatching { repository.restoreBackupSnapshot(snapshot) }
                                        .fold(
                                            onSuccess = { onRestored(Result.success(Unit)) },
                                            onFailure = { onRestored(Result.failure(it)) },
                                        )
                                }
                            },
                            onChooseImportFile = { consume ->
                                pendingImportConsumer = consume
                                openImportFile.launch(arrayOf("text/tab-separated-values", "text/*", "application/octet-stream"))
                            },
                            onDeleteMemory = { itemId, onDeleted ->
                                lifecycleScope.launch {
                                    repository.deleteMemory(itemId)
                                    onDeleted()
                                }
                            },
                            onAttempt = { attempt -> lifecycleScope.launch { repository.saveAttempt(attempt) } },
                            onCompleteRetention = { id, score, latencyMs, response, accuracy, onLoaded ->
                                lifecycleScope.launch {
                                    repository.completeRetentionCheckpoint(id, score, latencyMs, response, accuracy)
                                    onLoaded(repository.loadRetentionCheckpoints())
                                }
                            },
                            onCreateNode = { name, kind, parentId, onLoaded ->
                                lifecycleScope.launch {
                                    repository.createPalaceNode(name, kind, parentId)
                                    onLoaded(repository.loadPalaceNodes())
                                }
                            },
                            onDeleteNode = { nodeId, onLoaded ->
                                lifecycleScope.launch {
                                    repository.deletePalaceNode(nodeId)
                                    onLoaded(repository.loadPalaceNodes())
                                }
                            },
                            onRenameNode = { nodeId, name, onLoaded ->
                                lifecycleScope.launch {
                                    repository.renamePalaceNode(nodeId, name)
                                    onLoaded(repository.loadPalaceNodes())
                                }
                            },
                            onMoveNode = { nodeId, parentId, onLoaded ->
                                lifecycleScope.launch {
                                    repository.movePalaceNode(nodeId, parentId)
                                    onLoaded(repository.loadPalaceNodes())
                                }
                            },
                        )
                    }
                }
            }
        }
    }
}

private enum class AppTab(val label: String) {
    Today("Today"), Palace("Palace"), Recall("Recall"), Library("Library")
}

@OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
@Composable
private fun MemoryApp(
    initialItems: List<MemoryItem>,
    initialNodes: List<PalaceNode>,
    initialAttempts: List<RecallAttempt>,
    initialCheckpoints: List<RetentionCheckpoint>,
    initialMigrationResult: LegacyMigrationResult,
    onSave: (List<MemoryItem>) -> Unit,
    onExportMemories: (List<MemoryItem>) -> Unit,
    onExportLegacyData: (String) -> Unit,
    onExportFullBackup: () -> Unit,
    onChooseBackupFile: (((Result<String>) -> Unit) -> Unit),
    onRestoreBackup: (MemoryBackupSnapshot, (Result<Unit>) -> Unit) -> Unit,
    onChooseImportFile: (((Result<String>) -> Unit) -> Unit),
    onDeleteMemory: (String, () -> Unit) -> Unit,
    onAttempt: (RecallAttempt) -> Unit,
    onCompleteRetention: (String, Int, Long, String?, Int, (List<RetentionCheckpoint>) -> Unit) -> Unit,
    onCreateNode: (String, PalaceNodeKind, String?, (List<PalaceNode>) -> Unit) -> Unit,
    onDeleteNode: (String, (List<PalaceNode>) -> Unit) -> Unit,
    onRenameNode: (String, String, (List<PalaceNode>) -> Unit) -> Unit,
    onMoveNode: (String, String?, (List<PalaceNode>) -> Unit) -> Unit,
) {
    var memories by remember { mutableStateOf(initialItems) }
    var palaceNodes by remember { mutableStateOf(initialNodes) }
    var checkpoints by remember { mutableStateOf(initialCheckpoints) }
    var migrationResult by remember { mutableStateOf(initialMigrationResult) }
    var activeCheckpoint by remember { mutableStateOf<RetentionCheckpoint?>(null) }
    var activeSequence by remember { mutableStateOf<RecallSequence?>(null) }
    var tab by remember { mutableStateOf(AppTab.Today) }
    var adding by remember { mutableStateOf(false) }
    var bulkImportOpen by remember { mutableStateOf(false) }
    var bulkImportText by remember { mutableStateOf("") }
    var bulkImportError by remember { mutableStateOf<String?>(null) }
    var bulkImportStatus by remember { mutableStateOf<String?>(null) }
    var bulkImportPreview by remember { mutableStateOf<MemoryImportBatch?>(null) }
    var backupImportOpen by remember { mutableStateOf(false) }
    var backupImportSnapshot by remember { mutableStateOf<MemoryBackupSnapshot?>(null) }
    var backupImportError by remember { mutableStateOf<String?>(null) }
    var backupImportStatus by remember { mutableStateOf<String?>(null) }
    var pendingCueCandidate by remember { mutableStateOf<MemoryItem?>(null) }
    var pendingCueConflicts by remember { mutableStateOf<List<MemoryCueConflict>>(emptyList()) }
    var editingMemory by remember { mutableStateOf<MemoryItem?>(null) }
    var deletingMemory by remember { mutableStateOf<MemoryItem?>(null) }
    var active by remember { mutableStateOf<MemoryItem?>(null) }
    var revealed by remember { mutableStateOf(false) }
    var attempts by remember { mutableStateOf(initialAttempts.size) }
    var correct by remember { mutableStateOf(initialAttempts.count { it.correct }) }
    var attemptHistory by remember { mutableStateOf(initialAttempts) }
    fun recordAttempt(attempt: RecallAttempt) {
        attemptHistory = attemptHistory + attempt
        onAttempt(attempt)
    }
    var recallMode by remember { mutableStateOf(RecallMode.Cued) }

    fun save(updated: List<MemoryItem>) {
        memories = updated
        onSave(updated)
    }
    fun startRecall() {
        activeCheckpoint = null
        activeSequence = null
        active = memories.minByOrNull { it.nextReviewAt }
        revealed = false
        tab = AppTab.Recall
    }
    fun startRetention(checkpoint: RetentionCheckpoint) {
        activeCheckpoint = checkpoint
        activeSequence = null
        active = memories.firstOrNull { it.id == checkpoint.itemId }
        revealed = false
        tab = AppTab.Recall
    }

    Scaffold(
        topBar = {
            TopAppBar(title = {
                Column {
                    Text("MEMORY STUDIO", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleMedium)
                    Text("Learn it. Remember it. Recall it.", style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            })
        },
        bottomBar = {
            NavigationBar {
                AppTab.entries.forEach { destination ->
                    NavigationBarItem(
                        selected = tab == destination,
                        onClick = { tab = destination; if (destination == AppTab.Recall) startRecall() },
                        icon = { Text(when (destination) {
                            AppTab.Today -> "◎"; AppTab.Palace -> "⌂"
                            AppTab.Recall -> "↻"; AppTab.Library -> "▤"
                        }) },
                        label = { Text(destination.label) },
                    )
                }
            }
        },
    ) { padding ->
        Column(Modifier.fillMaxSize().padding(padding)) {
            when (tab) {
                AppTab.Today -> TodayScreen(
                    memories, attempts, correct, checkpoints, attemptHistory,
                    migrationResult = migrationResult,
                    onExportLegacyData = onExportLegacyData,
                    onExportFullBackup = onExportFullBackup,
                    onImportFullBackup = {
                        backupImportOpen = true
                        backupImportSnapshot = null
                        backupImportError = null
                        backupImportStatus = null
                    },
                    onAdd = { adding = true }, onRecall = ::startRecall, onStartRetention = ::startRetention,
                    onStartSequence = { sequence -> activeSequence = sequence; activeCheckpoint = null; revealed = false; tab = AppTab.Recall },
                    onStartPriority = { item -> activeCheckpoint = null; activeSequence = null; active = item; revealed = false; tab = AppTab.Recall },
                    onDiscriminationAttempt = { item, wasCorrect, latencyMs, response ->
                        attempts += 1
                        if (wasCorrect) correct += 1
                        recordAttempt(
                            RecallAttempt(
                                itemId = item.id,
                                mode = RecallMode.Discrimination,
                                correct = wasCorrect,
                                score = if (wasCorrect) 5 else 0,
                                latencyMs = latencyMs,
                                recalledResponse = response,
                            ),
                        )
                    },
                )
                AppTab.Palace -> PalaceScreen(
                    memories = memories,
                    nodes = palaceNodes,
                    onAddMemory = { adding = true },
                    onCreateNode = { name, kind, parentId, onLoaded ->
                        onCreateNode(name, kind, parentId) { refreshed ->
                            palaceNodes = refreshed
                            onLoaded(refreshed)
                        }
                    },
                    onDeleteNode = { nodeId, onLoaded ->
                        onDeleteNode(nodeId) { refreshed ->
                            palaceNodes = refreshed
                            onLoaded(refreshed)
                        }
                    },
                    onRenameNode = { nodeId, name, onLoaded ->
                        onRenameNode(nodeId, name) { refreshed ->
                            palaceNodes = refreshed
                            onLoaded(refreshed)
                        }
                    },
                    onMoveNode = { nodeId, parentId, onLoaded ->
                        onMoveNode(nodeId, parentId) { refreshed ->
                            palaceNodes = refreshed
                            onLoaded(refreshed)
                        }
                    },
                )
                AppTab.Recall -> if (activeCheckpoint != null) {
                    RetentionTestScreen(
                        item = active,
                        checkpoint = activeCheckpoint!!,
                        onComplete = { score, latencyMs, response, accuracy ->
                            val checkpointId = activeCheckpoint!!.id
                            onCompleteRetention(checkpointId, score, latencyMs, response, accuracy) { refreshed ->
                                checkpoints = refreshed
                                activeCheckpoint = null
                                tab = AppTab.Today
                            }
                        },
                    )
                } else if (activeSequence != null) {
                    SequenceRecallScreen(
                        sequence = activeSequence!!,
                        onComplete = { score, sequenceCorrect, latencyMs, response ->
                            val sequence = activeSequence ?: return@SequenceRecallScreen
                            val now = System.currentTimeMillis()
                            val updated = memories.map { memory ->
                                if (sequence.items.any { it.id == memory.id }) {
                                    val outcome = RecallScheduler.schedule(
                                        previousReviews = memory.reviewCount,
                                        score = score,
                                        now = now,
                                        consecutiveFailures = if (sequenceCorrect) 0 else 2,
                                    )
                                    memory.copy(nextReviewAt = outcome.nextReviewAt, lastScore = outcome.score, reviewCount = memory.reviewCount + 1)
                                } else memory
                            }
                            save(updated)
                            sequence.items.forEach { memory ->
                                recordAttempt(RecallAttempt(
                                    itemId = memory.id, mode = RecallMode.Sequence, correct = sequenceCorrect,
                                    score = score, latencyMs = latencyMs, recalledResponse = response,
                                ))
                            }
                            attempts += sequence.items.size
                            if (sequenceCorrect) correct += sequence.items.size
                            activeSequence = null
                            active = updated.filter { candidate -> sequence.items.none { it.id == candidate.id } }
                                .minByOrNull { it.nextReviewAt }
                            revealed = false
                            tab = AppTab.Today
                        },
                    )
                } else RecallScreen(
                    item = active,
                    revealed = revealed,
                    mode = recallMode,
                    onModeChange = { recallMode = it; revealed = false },
                    onReveal = { revealed = true },
                    onRate = { score, userCheckedCorrect, latencyMs, recalledResponse ->
                        val current = active ?: return@RecallScreen
                        val recentAttempts = attemptHistory
                            .asSequence()
                            .filter { it.itemId == current.id }
                            .sortedByDescending { it.reviewedAt }
                            .take(2)
                            .toList()
                        val consecutiveFailures = RecallFailurePlan.consecutiveFailuresAfter(
                            recentAttempts = recentAttempts,
                            currentCorrect = userCheckedCorrect,
                        )
                        val outcome = RecallScheduler.schedule(
                            previousReviews = current.reviewCount,
                            score = score,
                            consecutiveFailures = consecutiveFailures,
                        )
                        val updated = memories.map { memory ->
                            if (memory.id == current.id) memory.copy(
                                nextReviewAt = outcome.nextReviewAt,
                                lastScore = outcome.score,
                                reviewCount = memory.reviewCount + 1,
                            ) else memory
                        }
                        save(updated)
                        attempts += 1
                        if (userCheckedCorrect) correct += 1
                        recordAttempt(
                            RecallAttempt(
                                itemId = current.id,
                                mode = recallMode,
                                correct = userCheckedCorrect,
                                score = score,
                                latencyMs = latencyMs,
                                recalledResponse = recalledResponse,
                            ),
                        )
                        active = updated.filter { it.id != current.id }.minByOrNull { it.nextReviewAt }
                        revealed = false
                    },
                    onAdd = { adding = true },
                )
                AppTab.Library -> LibraryScreen(
                    memories = memories,
                    onAdd = { adding = true },
                    onEdit = { editingMemory = it },
                    onDelete = { deletingMemory = it },
                    onImport = { bulkImportOpen = true; bulkImportError = null },
                    onExport = { onExportMemories(memories) },
                )
            }
        }
    }

    deletingMemory?.let { item ->
        AlertDialog(
            onDismissRequest = { deletingMemory = null },
            title = { Text("Delete memory?") },
            text = { Text("This permanently deletes this memory and its saved recall attempts and retention checkpoints. This cannot be undone.") },
            confirmButton = {
                TextButton(onClick = {
                    onDeleteMemory(item.id) {
                        memories = memories.filterNot { it.id == item.id }
                        attemptHistory = attemptHistory.filterNot { it.itemId == item.id }
                        attempts = attemptHistory.size
                        correct = attemptHistory.count { it.correct }
                        checkpoints = checkpoints.filterNot { it.itemId == item.id }
                        if (active?.id == item.id) active = null
                        if (editingMemory?.id == item.id) editingMemory = null
                        deletingMemory = null
                    }
                }) { Text("Delete") }
            },
            dismissButton = {
                TextButton(onClick = { deletingMemory = null }) { Text("Cancel") }
            },
        )
    }

    if (backupImportOpen) {
        AlertDialog(
            onDismissRequest = {
                backupImportOpen = false
                backupImportSnapshot = null
                backupImportError = null
                backupImportStatus = null
            },
            title = { Text("Restore full backup") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("A full backup replaces all current memories, palace locations, recall attempts, and retention checkpoints. Export a backup of your current data first if you may need it.")
                    Button(onClick = {
                        onChooseBackupFile { result ->
                            result.onSuccess { raw ->
                                runCatching { MemoryBackupPlan.decode(raw) }
                                    .onSuccess {
                                        backupImportSnapshot = it
                                        backupImportError = null
                                        backupImportStatus = null
                                    }
                                    .onFailure {
                                        backupImportSnapshot = null
                                        backupImportError = it.message ?: "The selected file is not a valid backup."
                                    }
                            }.onFailure {
                                backupImportError = it.message ?: "Could not read the selected backup file."
                            }
                        }
                    }) { Text("Choose backup JSON") }
                    backupImportSnapshot?.let { snapshot ->
                        Text("Ready to replace data with ${snapshot.memories.size} memories, ${snapshot.palaceNodes.size} palace locations, ${snapshot.attempts.size} recall attempts, and ${snapshot.checkpoints.size} retention checkpoints.")
                    }
                    backupImportError?.let { Text(it, color = MaterialTheme.colorScheme.error) }
                    backupImportStatus?.let { Text(it, color = MaterialTheme.colorScheme.primary) }
                }
            },
            confirmButton = {
                TextButton(
                    enabled = backupImportSnapshot != null,
                    onClick = {
                        val snapshot = backupImportSnapshot ?: return@TextButton
                        onRestoreBackup(snapshot) { result ->
                            result.onSuccess {
                                memories = snapshot.memories
                                palaceNodes = snapshot.palaceNodes
                                attemptHistory = snapshot.attempts
                                attempts = snapshot.attempts.size
                                correct = snapshot.attempts.count { it.correct }
                                checkpoints = snapshot.checkpoints
                                active = null
                                activeCheckpoint = null
                                activeSequence = null
                                revealed = false
                                migrationResult = LegacyMigrationResult(items = snapshot.memories)
                                backupImportOpen = false
                                backupImportSnapshot = null
                                backupImportError = null
                                backupImportStatus = null
                                tab = AppTab.Today
                            }.onFailure {
                                backupImportError = it.message ?: "Restore failed. Existing data was not intentionally changed."
                            }
                        }
                    },
                ) { Text("Replace local data") }
            },
            dismissButton = {
                TextButton(onClick = {
                    backupImportOpen = false
                    backupImportSnapshot = null
                    backupImportError = null
                    backupImportStatus = null
                }) { Text("Cancel") }
            },
        )
    }

    if (bulkImportOpen) {
        AlertDialog(
            onDismissRequest = { bulkImportOpen = false; bulkImportError = null; bulkImportStatus = null; bulkImportPreview = null },
            title = { Text("Bulk import memories") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Paste tab-separated rows: prompt, answer, palace, locus, chunk, association, sequence order. The first two columns are required.")
                    OutlinedTextField(
                        value = bulkImportText,
                        onValueChange = { bulkImportText = it; bulkImportError = null; bulkImportStatus = null; bulkImportPreview = null },
                        label = { Text("TSV rows") },
                        modifier = Modifier.fillMaxWidth(),
                        minLines = 5,
                        maxLines = 10,
                    )
                    TextButton(onClick = {
                        onChooseImportFile { result ->
                            result.onSuccess { content ->
                                bulkImportText = content
                                bulkImportPreview = null
                                bulkImportError = null
                                bulkImportStatus = null
                            }.onFailure { error ->
                                bulkImportError = error.message?.takeIf { it.isNotBlank() }
                                    ?: "Could not read the selected file."
                                bulkImportPreview = null
                            }
                        }
                    }) { Text("Choose TSV file") }
                    bulkImportError?.let { Text(it, color = MaterialTheme.colorScheme.error) }
                    bulkImportStatus?.let { Text(it, color = MaterialTheme.colorScheme.primary) }
                    bulkImportPreview?.let { preview ->
                        Text("Preview: ${preview.rows.size} new memories; ${preview.duplicatesSkipped} duplicates will be skipped.")
                        preview.rows.take(3).forEach { row ->
                            Text("• ${row.prompt} → ${row.answer}", style = MaterialTheme.typography.bodySmall)
                        }
                        if (preview.rows.size > 3) {
                            Text("…and ${preview.rows.size - 3} more", style = MaterialTheme.typography.bodySmall)
                        }
                    }
                    Text("Duplicate prompt + answer pairs are skipped automatically.", style = MaterialTheme.typography.bodySmall)
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    val preview = bulkImportPreview
                    if (preview == null) {
                        runCatching { MemoryImportPlan.parseTsv(bulkImportText) }
                            .map { MemoryImportPlan.excludeDuplicates(it, memories) }
                            .onSuccess { batch ->
                                if (batch.rows.isEmpty()) {
                                    bulkImportError = "Every imported row duplicates an existing memory or another row."
                                } else {
                                    bulkImportPreview = batch
                                    bulkImportError = null
                                }
                            }
                            .onFailure { bulkImportError = it.message ?: "Could not parse import data." }
                    } else {
                        val now = System.currentTimeMillis()
                        val newItems = preview.rows.map { row ->
                            MemoryItem(
                                id = UUID.randomUUID().toString(),
                                prompt = row.prompt,
                                answer = row.answer,
                                palace = row.palace,
                                locus = row.locus,
                                chunkLabel = row.chunkLabel,
                                association = row.association,
                                sequenceOrder = row.sequenceOrder,
                                createdAt = now,
                                nextReviewAt = now,
                            )
                        }
                        save(memories + newItems)
                        bulkImportText = ""
                        bulkImportPreview = null
                        bulkImportError = null
                        if (preview.duplicatesSkipped > 0) {
                            bulkImportStatus = "Imported ${preview.rows.size}; skipped ${preview.duplicatesSkipped} duplicate row(s)."
                        } else {
                            bulkImportOpen = false
                            bulkImportStatus = null
                        }
                    }
                }) { Text(if (bulkImportPreview == null) "Preview import" else "Confirm import") }
            },
            dismissButton = {
                TextButton(onClick = { bulkImportOpen = false; bulkImportError = null }) { Text("Cancel") }
            },
        )
    }

    if (pendingCueCandidate != null) {
        val candidate = pendingCueCandidate!!
        AlertDialog(
            onDismissRequest = {
                pendingCueCandidate = null
                pendingCueConflicts = emptyList()
            },
            title = { Text("Review similar cues") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("This prompt matches or closely resembles existing cues. Similar wording can make retrieval ambiguous. Review the matches before deciding.")
                    pendingCueConflicts.forEach { conflict ->
                        Card(modifier = Modifier.fillMaxWidth()) {
                            Column(Modifier.padding(10.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                Text(conflict.existing.prompt, fontWeight = FontWeight.SemiBold)
                                Text("Answer: ${conflict.existing.answer}")
                                Text(
                                    if (conflict.exactMatch) "Exact normalized prompt match"
                                    else "Lexical overlap: ${"%.0f".format(conflict.similarity * 100)}%",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                        }
                    }
                    Text("This is a wording-based warning, not a semantic judgment. It may miss synonyms or flag legitimate related facts.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    if (editingMemory != null) save(memories.map { if (it.id == candidate.id) candidate else it })
                    else save(memories + candidate)
                    if (active?.id == candidate.id) { active = candidate; revealed = false }
                    adding = false
                    editingMemory = null
                    pendingCueCandidate = null
                    pendingCueConflicts = emptyList()
                }) { Text("Save anyway") }
            },
            dismissButton = {
                Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    TextButton(onClick = {
                        pendingCueCandidate = null
                        pendingCueConflicts = emptyList()
                    }) { Text("Edit cue") }
                    TextButton(onClick = {
                        pendingCueCandidate = null
                        pendingCueConflicts = emptyList()
                        adding = false
                        editingMemory = null
                    }) { Text("Discard") }
                }
            },
        )
    }

    if (adding || editingMemory != null) {
        val existing = editingMemory
        AddMemoryDialog(
            initialItem = existing,
            onDismiss = { adding = false; editingMemory = null },
            nodes = palaceNodes,
            onCreate = { prompt, answer, palace, locus, palaceNodeId, chunkLabel, association, sequenceOrder ->
                val selectedNode = palaceNodes.firstOrNull { it.id == palaceNodeId }
                val resolvedPalace = selectedNode?.let { node ->
                    generateSequence(node) { current -> palaceNodes.firstOrNull { it.id == current.parentId } }
                        .lastOrNull { it.kind == PalaceNodeKind.Palace }?.name
                } ?: palace.trim()
                val resolvedLocus = selectedNode?.name ?: locus.trim()
                val item = if (existing != null) {
                    existing.copy(
                        prompt = prompt.trim(), answer = answer.trim(), palace = resolvedPalace,
                        locus = resolvedLocus, palaceNodeId = palaceNodeId,
                        chunkLabel = chunkLabel.trim(), association = association.trim(), sequenceOrder = sequenceOrder,
                    )
                } else {
                    val now = System.currentTimeMillis()
                    MemoryItem(
                        id = UUID.randomUUID().toString(), prompt = prompt.trim(), answer = answer.trim(),
                        palace = resolvedPalace, locus = resolvedLocus, palaceNodeId = palaceNodeId,
                        chunkLabel = chunkLabel.trim(), association = association.trim(), sequenceOrder = sequenceOrder,
                        createdAt = now, nextReviewAt = now,
                    )
                }
                val conflicts = MemoryCueConfusionPlan.find(item, memories)
                if (conflicts.isNotEmpty()) {
                    pendingCueCandidate = item
                    pendingCueConflicts = conflicts
                } else {
                    if (existing != null) save(memories.map { if (it.id == existing.id) item else it })
                    else save(memories + item)
                    adding = false
                    editingMemory = null
                    if (active?.id == item.id) { active = item; revealed = false }
                }
            },
        )
    }
}

@Composable
private fun TodayScreen(
    memories: List<MemoryItem>, attempts: Int, correct: Int,
    checkpoints: List<RetentionCheckpoint>,
    attemptHistory: List<RecallAttempt>,
    migrationResult: LegacyMigrationResult,
    onExportLegacyData: (String) -> Unit,
    onExportFullBackup: () -> Unit,
    onImportFullBackup: () -> Unit,
    onAdd: () -> Unit, onRecall: () -> Unit,
    onStartRetention: (RetentionCheckpoint) -> Unit,
    onStartSequence: (RecallSequence) -> Unit,
    onStartPriority: (MemoryItem) -> Unit,
    onDiscriminationAttempt: (MemoryItem, Boolean, Long, String?) -> Unit,
) {
    var activeDrill by remember { mutableStateOf<InterferencePair?>(null) }
    var selectedAnswerId by remember { mutableStateOf<String?>(null) }
    var drillChecked by remember { mutableStateOf(false) }
    var drillStartedAt by remember { mutableStateOf(0L) }
    LazyColumn(Modifier.fillMaxSize().padding(horizontal = 18.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)) {
        item { Spacer(Modifier.height(4.dp)) }
        item {
            Card(modifier = Modifier.fillMaxWidth()) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Data safety", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleMedium)
                    Text("Full JSON backups include memories, palace locations, recall history, and retention checkpoints.")
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Button(onClick = onExportFullBackup, modifier = Modifier.weight(1f)) { Text("Export backup") }
                        TextButton(onClick = onImportFullBackup, modifier = Modifier.weight(1f)) { Text("Restore backup") }
                    }
                }
            }
        }
        if (migrationResult.errorMessage != null) {
            item {
                Card(
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer),
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text("Legacy data needs recovery", fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onErrorContainer)
                        Text(migrationResult.errorMessage, color = MaterialTheme.colorScheme.onErrorContainer)
                        Text("The original JSON is still preserved. Export a backup before editing or replacing it.",
                            color = MaterialTheme.colorScheme.onErrorContainer, style = MaterialTheme.typography.bodySmall)
                        migrationResult.preservedRawData?.let { raw ->
                            Button(onClick = { onExportLegacyData(raw) }) { Text("Export legacy JSON backup") }
                        }
                    }
                }
            }
        }
        item {
            Card(
                colors = CardDefaults.cardColors(containerColor = Color(0xFF202C4A)),
                shape = RoundedCornerShape(26.dp), modifier = Modifier.fillMaxWidth(),
            ) {
                Column(Modifier.padding(22.dp)) {
                    Text(if (memories.isEmpty()) "YOUR STARTING POINT" else "YOUR NEXT PRACTICE", color = Color(0xFFC9E8D5), style = MaterialTheme.typography.labelLarge)
                    Spacer(Modifier.height(10.dp))
                    Text(if (memories.isEmpty()) "You can train your memory." else "Recall first. Review second.",
                        color = Color.White, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
                    Spacer(Modifier.height(8.dp))
                    Text(if (memories.isEmpty()) "No experience needed. Start with one small fact and learn the method as you go." else "Try to retrieve the answer before revealing it. Effortful recall helps you find what you really know.", color = Color(0xFFE1EEE6))
                    Spacer(Modifier.height(18.dp))
                    Button(onClick = if (memories.isEmpty()) onAdd else onRecall) {
                        Text(if (memories.isEmpty()) "Add a first fact" else "Practise recall")
                    }
                }
            }
        }
        item {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Heading("Learn the essentials")
                Text("Three useful skills. Start with the first; you do not need to know any terminology.",
                    color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodyMedium)
                Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer),
                    shape = RoundedCornerShape(20.dp), modifier = Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Text("01  Active recall", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onPrimaryContainer)
                        Text("What it means: try to remember before looking at the answer. Example: close your notes and explain one idea in your own words.",
                            color = MaterialTheme.colorScheme.onPrimaryContainer)
                        Text("Why use it: it reveals what you can actually retrieve, not just what looks familiar.",
                            style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onPrimaryContainer)
                    }
                }
                Card(shape = RoundedCornerShape(20.dp), modifier = Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Text("02  Spaced practice", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                        Text("What it means: revisit information across separate sessions instead of cramming it all at once.")
                        Text("Try this: test yourself today, then return to the same fact tomorrow. Adjust the interval based on how well you remember.",
                            style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
                Card(shape = RoundedCornerShape(20.dp), modifier = Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Text("03  Distinctive associations", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                        Text("What it means: connect a fact to a concrete, unusual image or action that gives you a retrieval cue.")
                        Text("Example: to remember that a person is a baker, imagine them juggling enormous loaves in a bakery. The stranger and more specific cue may be easier to recall.",
                            style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
        }
        item {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                MetricCard("Stored", memories.size.toString(), "memory units", Modifier.weight(1f))
                MetricCard("Attempts", attempts.toString(), "all recorded", Modifier.weight(1f))
                MetricCard("Self-rated success", if (attempts == 0) "—" else "${(correct * 100) / attempts}%", "all recorded attempts", Modifier.weight(1f))
            }
        }
        item {
            Heading("Performance by recall mode")
            if (attemptHistory.isEmpty()) {
                Text("Results will appear after your first recall attempt.", color = MaterialTheme.colorScheme.onSurfaceVariant)
            } else {
                RecallAnalytics.byMode(attemptHistory).forEach { summary ->
                    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            Text(summary.mode.label(), fontWeight = FontWeight.SemiBold)
                            Text("${summary.accuracyPercent}% · ${summary.attempts} attempts")
                        }
                        Text("Mean retrieval time: ${summary.meanLatencyMs / 1000.0}s",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
                Text("Success is based on recorded scores/self-ratings, not independently verified correctness.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        item {
            Heading("Delayed-retention results")
            RetentionAnalytics.byDelay(checkpoints).forEach { summary ->
                Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text("${summary.delayDays}-day checkpoint", fontWeight = FontWeight.SemiBold)
                        Text("${summary.completed}/${summary.total} completed")
                    }
                    summary.meanSelfRating?.let { mean ->
                        Text("Mean self-rating: ${"%.1f".format(mean)}/5",
                            color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    summary.checkedAccuracyPercent?.let { percent ->
                        Text("User-checked accuracy: $percent% (${summary.checkedAccuracyCount} rated)",
                            color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    summary.meanLatencyMs?.let { latency ->
                        Text("Mean retrieval time: ${latency / 1000.0}s",
                            color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    if (summary.completed == 0) {
                        Text("No completed results yet.", color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
            Text("Self-ratings and user-checked accuracy are separate measures; neither is an automated semantic evaluation.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        item {
            Heading("Progress by memory")
            val progress = MemoryProgressPlan.summarize(memories, attemptHistory)
            if (progress.isEmpty()) {
                Text("Each memory appears here after at least two recorded recall attempts.",
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
            } else {
                Text("First-versus-latest comparisons use recorded attempt labels, not automatic grading of free-text responses.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
                progress.forEach { itemProgress ->
                    Card(shape = RoundedCornerShape(16.dp), modifier = Modifier.fillMaxWidth()) {
                        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            Text(itemProgress.item.prompt, fontWeight = FontWeight.SemiBold)
                            Text("${itemProgress.attemptCount} attempts · ${itemProgress.correctCount} marked correct")
                            val change = when {
                                !itemProgress.firstCorrect && itemProgress.latestCorrect -> "Improved: latest attempt marked correct"
                                itemProgress.firstCorrect && !itemProgress.latestCorrect -> "Regressed: latest attempt marked incorrect"
                                itemProgress.latestCorrect -> "Latest attempt marked correct"
                                else -> "Still marked incorrect on latest attempt"
                            }
                            Text(change, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            Text("Mean retrieval time: ${"%.1f".format(itemProgress.meanLatencyMs / 1000.0)} s",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                }
            }
        }
        item {
            Heading("Repeated errors")
            val repeatedErrors = RepeatedErrorPlan.find(memories, attemptHistory)
            if (repeatedErrors.isEmpty()) {
                Text("No memories have two or more attempts marked incorrect yet.",
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
            } else {
                Text("These items have repeated recorded failures. The list uses saved attempt labels, not automatic grading of free-text answers.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
                repeatedErrors.forEach { error ->
                    Card(shape = RoundedCornerShape(16.dp), modifier = Modifier.fillMaxWidth()) {
                        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(5.dp)) {
                            Text(error.item.prompt, fontWeight = FontWeight.SemiBold)
                            Text("${error.incorrectAttempts} recorded incorrect attempts",
                                color = MaterialTheme.colorScheme.error)
                            error.lastIncorrectResponse?.let {
                                Text("Latest recorded response: $it",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                            Button(onClick = { onStartPriority(error.item) }, modifier = Modifier.fillMaxWidth()) {
                                Text("Practise this memory")
                            }
                        }
                    }
                }
            }
        }
        item {
            Heading("Learning progress")
            val progress = MemoryProgressPlan.summarize(memories, attemptHistory)
            if (progress.isEmpty()) {
                Text("Progress comparisons appear after a memory has at least two recorded attempts.",
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
            } else {
                progress.forEach { summary ->
                    Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
                        Text(summary.item.prompt, fontWeight = FontWeight.SemiBold)
                        val trajectory = when {
                            !summary.firstCorrect && summary.latestCorrect -> "Improved: latest attempt marked correct"
                            summary.firstCorrect && !summary.latestCorrect -> "Declined: latest attempt marked incorrect"
                            summary.latestCorrect -> "Maintained: first and latest attempts marked correct"
                            else -> "Still struggling: first and latest attempts marked incorrect"
                        }
                        Text(trajectory, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Text("${summary.correctCount}/${summary.attemptCount} marked correct · mean retrieval time ${summary.meanLatencyMs / 1000.0}s",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Button(onClick = { onStartPriority(summary.item) }, modifier = Modifier.fillMaxWidth()) {
                            Text("Practise this memory")
                        }
                    }
                }
                Text("These trends compare saved first/latest correctness labels; they do not establish causation or independently grade answers.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        item {
            Heading("Recent recall history")
            val recentAttempts = attemptHistory.sortedByDescending { it.reviewedAt }.take(10)
            if (recentAttempts.isEmpty()) {
                Text("Completed recall attempts will appear here.",
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
            } else {
                val itemById = memories.associateBy { it.id }
                recentAttempts.forEach { attempt ->
                    Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
                        Text(itemById[attempt.itemId]?.prompt ?: "Memory no longer in library",
                            fontWeight = FontWeight.SemiBold)
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            Text("${attempt.mode.label()} · ${if (attempt.correct) "Marked correct" else "Marked incorrect"}",
                                color = if (attempt.correct) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error)
                            Text("${attempt.latencyMs / 1000.0}s",
                                color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        Text("Rating ${attempt.score}/5 · ${android.text.format.DateUtils.getRelativeTimeSpanString(attempt.reviewedAt, System.currentTimeMillis(), android.text.format.DateUtils.MINUTE_IN_MILLIS)}",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant)
                        attempt.recalledResponse?.takeIf { it.isNotBlank() }?.let { response ->
                            Text("Response: $response", style = MaterialTheme.typography.bodySmall)
                        }
                    }
                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                }
            }
            Text("History displays saved correctness labels and responses; it does not independently evaluate free-text answers.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        item {
            Heading("Priority practice")
            val priorities = WeakMemoryPlan.rank(memories, attemptHistory)
            if (priorities.isEmpty()) {
                Text("Add memory items to build a targeted practice queue.", color = MaterialTheme.colorScheme.onSurfaceVariant)
            } else {
                Text("Low previous ratings and overdue reviews rise to the top. Untested items are included until you have results.",
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
                priorities.forEach { priority ->
                    Card(shape = RoundedCornerShape(16.dp), modifier = Modifier.fillMaxWidth()) {
                        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(5.dp)) {
                            Text(priority.item.prompt, fontWeight = FontWeight.SemiBold)
                            Text(priority.reason, color = MaterialTheme.colorScheme.onSurfaceVariant,
                                style = MaterialTheme.typography.bodySmall)
                            Button(onClick = { onStartPriority(priority.item) }, modifier = Modifier.fillMaxWidth()) {
                                Text("Practise this memory")
                            }
                        }
                    }
                }
            }
        }
        item { Heading("The system") }
        item { PrincipleCard("01", "Encode", "Chunk facts and create distinctive associations.") }
        item { PrincipleCard("02", "Place", "Put facts into a navigable palace, route, and locus.") }
        item { PrincipleCard("03", "Retrieve", "Practise cues, free recall, and reverse connections.") }
        item { PrincipleCard("04", "Verify", "Measure delayed recall, retrieval speed, and interference.") }
        item {
            Heading("Ordered-sequence practice")
            val sequences = RecallSequencePlan.groups(memories)
            if (sequences.isEmpty()) {
                Text("Add at least two items with the same chunk label and different sequence positions to practise an ordered list.",
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
            } else {
                sequences.forEach { sequence ->
                    Card(shape = RoundedCornerShape(16.dp), modifier = Modifier.fillMaxWidth()) {
                        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            Text(sequence.label, fontWeight = FontWeight.SemiBold)
                            Text("${sequence.items.size} ordered items", color = MaterialTheme.colorScheme.onSurfaceVariant)
                            Button(onClick = { onStartSequence(sequence) }, modifier = Modifier.fillMaxWidth()) {
                                Text("Practise sequence")
                            }
                        }
                    }
                }
            }
        }
        item {
            Heading("Potential memory interference")
            val interference = MemoryInterferencePlan.find(memories)
            if (interference.isEmpty()) {
                Text(
                    if (memories.size < 2) "Add more memory items to check for overlapping wording."
                    else "No strong lexical overlaps found. This does not rule out conceptual confusion.",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            } else {
                Text("These items share important words. This is a wording-based warning, not proof that you confuse them.",
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
                interference.forEach { pair ->
                    Card(shape = RoundedCornerShape(16.dp), modifier = Modifier.fillMaxWidth()) {
                        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            Text("Shared terms: ${pair.sharedTerms.joinToString(", ")}",
                                color = MaterialTheme.colorScheme.primary, style = MaterialTheme.typography.labelMedium)
                            Text("A · ${pair.first.prompt}", fontWeight = FontWeight.SemiBold)
                            Text(pair.first.answer, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            Surface(modifier = Modifier.fillMaxWidth().height(1.dp), color = MaterialTheme.colorScheme.outlineVariant) {}
                            Text("B · ${pair.second.prompt}", fontWeight = FontWeight.SemiBold)
                            Text(pair.second.answer, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            Text("Discrimination cue: make each prompt specific and give the two facts distinct, vivid associations.",
                                style = MaterialTheme.typography.bodySmall)
                            Button(
                                onClick = {
                                    activeDrill = pair
                                    selectedAnswerId = null
                                    drillChecked = false
                                    drillStartedAt = SystemClock.elapsedRealtime()
                                },
                                modifier = Modifier.fillMaxWidth(),
                            ) { Text(if (activeDrill == pair) "Restart discrimination drill" else "Practise distinction") }
                            if (activeDrill == pair) {
                                Surface(modifier = Modifier.fillMaxWidth().height(1.dp), color = MaterialTheme.colorScheme.outlineVariant) {}
                                Text("Which answer belongs to this cue?", fontWeight = FontWeight.SemiBold)
                                Text(pair.first.prompt, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                TextButton(
                                    onClick = { if (!drillChecked) selectedAnswerId = pair.first.id },
                                    modifier = Modifier.fillMaxWidth(),
                                ) {
                                    Text((if (selectedAnswerId == pair.first.id) "✓ " else "") + pair.first.answer)
                                }
                                TextButton(
                                    onClick = { if (!drillChecked) selectedAnswerId = pair.second.id },
                                    modifier = Modifier.fillMaxWidth(),
                                ) {
                                    Text((if (selectedAnswerId == pair.second.id) "✓ " else "") + pair.second.answer)
                                }
                                if (!drillChecked) {
                                    Button(
                                        onClick = {
                                            val isCorrect = selectedAnswerId == pair.first.id
                                            onDiscriminationAttempt(
                                                pair.first,
                                                isCorrect,
                                                (SystemClock.elapsedRealtime() - drillStartedAt).coerceAtLeast(0L),
                                                if (selectedAnswerId == pair.first.id) pair.first.answer else pair.second.answer,
                                            )
                                            drillChecked = true
                                        },
                                        enabled = selectedAnswerId != null,
                                        modifier = Modifier.fillMaxWidth(),
                                    ) { Text("Check answer") }
                                } else {
                                    Text(
                                        if (selectedAnswerId == pair.first.id) "Correct. The cue matches this fact."
                                        else "Not quite. The correct answer is: ${pair.first.answer}",
                                        color = if (selectedAnswerId == pair.first.id) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error,
                                    )
                                    TextButton(onClick = { activeDrill = null; selectedAnswerId = null; drillChecked = false }) {
                                        Text("Finish drill")
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
        item {
            Heading("Retention checkpoints")
            val now = System.currentTimeMillis()
            val pending = checkpoints.filter { it.completedAt == null }.sortedBy { it.dueAt }
            val due = pending.filter { it.dueAt <= now }
            Text(
                if (pending.isEmpty()) "No pending checkpoints. Add memory items to create a retention schedule."
                else "${due.size} due · ${pending.size - due.size} upcoming",
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            if (pending.isEmpty()) {
                Spacer(Modifier.height(6.dp))
                Text("Checkpoints are planned for 24 hours, 7 days, and 30 days after each memory is created.",
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
            } else {
                pending.take(8).forEach { checkpoint ->
                    val item = memories.firstOrNull { it.id == checkpoint.itemId }
                    if (item != null) {
                        Card(shape = RoundedCornerShape(16.dp), modifier = Modifier.fillMaxWidth()) {
                            Column(Modifier.padding(14.dp)) {
                                Text("${checkpoint.delayDays}-day retention test", fontWeight = FontWeight.SemiBold)
                                Text(item.prompt, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                Text(
                                    if (checkpoint.dueAt <= now) "Due now"
                                    else "Due ${android.text.format.DateUtils.getRelativeTimeSpanString(checkpoint.dueAt, now, android.text.format.DateUtils.MINUTE_IN_MILLIS)}",
                                    style = MaterialTheme.typography.labelMedium,
                                )
                                Spacer(Modifier.height(6.dp))
                                Button(onClick = { onStartRetention(checkpoint) }, modifier = Modifier.fillMaxWidth()) {
                                    Text(if (checkpoint.dueAt <= now) "Start delayed test" else "Test early")
                                }
                            }
                        }
                    }
                }
            }
        }
        item {
            Heading("Retention results")
            val now = System.currentTimeMillis()
            val completed = checkpoints
                .filter { it.completedAt != null }
                .sortedByDescending { it.completedAt }
            if (completed.isEmpty()) {
                Text(
                    "Completed delayed tests will appear here. Each result keeps your written response, self-rating, and retrieval time.",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            } else {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf(1, 7, 30).forEach { delayDays ->
                        val group = completed.filter { it.delayDays == delayDays }
                        val scheduled = checkpoints.count { it.delayDays == delayDays }
                        val notTested = (scheduled - group.size).coerceAtLeast(0)
                        val rated = group.mapNotNull { it.score }
                        val timed = group.mapNotNull { it.latencyMs }
                        Card(shape = RoundedCornerShape(16.dp), modifier = Modifier.fillMaxWidth()) {
                            Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                Text("${delayDays}-day checkpoint", fontWeight = FontWeight.SemiBold)
                                Text("${group.size} completed · $notTested not tested")
                                if (rated.isNotEmpty()) {
                                    Text(
                                        "Mean self-rating: ${"%.1f".format(rated.average())} / 5",
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    )
                                }
                                val accuracyRatings = group.mapNotNull { it.accuracy }
                                if (accuracyRatings.isNotEmpty()) {
                                    val fullyCorrect = accuracyRatings.count { it == 2 }
                                    val partiallyCorrect = accuracyRatings.count { it == 1 }
                                    val incorrect = accuracyRatings.count { it == 0 }
                                    val accuracyPercent = fullyCorrect * 100 / accuracyRatings.size
                                    Text(
                                        "Fully correct: $fullyCorrect (${accuracyPercent}%) · Partial: $partiallyCorrect · Incorrect: $incorrect (${accuracyRatings.size} checked)",
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    )
                                }
                                if (timed.isNotEmpty()) {
                                    Text(
                                        "Mean retrieval time: ${"%.1f".format(timed.average() / 1000.0)} s",
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    )
                                }
                            }
                        }
                    }
                }
                Spacer(Modifier.height(4.dp))
                Text("Recent completed tests", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                completed.take(12).forEach { checkpoint ->
                    val memory = memories.firstOrNull { it.id == checkpoint.itemId }
                    Card(shape = RoundedCornerShape(16.dp), modifier = Modifier.fillMaxWidth()) {
                        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(5.dp)) {
                            Text("${checkpoint.delayDays}-day test", fontWeight = FontWeight.SemiBold)
                            if (memory != null) {
                                Text(memory.prompt)
                                Text("Stored answer: ${memory.answer}", color = MaterialTheme.colorScheme.onSurfaceVariant)
                            } else {
                                Text("The original memory item is no longer available.")
                            }
                            val accuracyLabel = when (checkpoint.accuracy) {
                                0 -> "Incorrect"
                                1 -> "Partially correct"
                                2 -> "Correct"
                                else -> "Not checked"
                            }
                            Text(
                                "Answer accuracy: $accuracyLabel · Self-rating: ${checkpoint.score?.let { "$it / 5" } ?: "not recorded"} · Retrieval time: ${checkpoint.latencyMs?.let { "${"%.1f".format(it / 1000.0)} s" } ?: "not recorded"}",
                                style = MaterialTheme.typography.bodySmall,
                            )
                            Text(
                                "Completed ${android.text.format.DateUtils.getRelativeTimeSpanString(checkpoint.completedAt!!, now, android.text.format.DateUtils.MINUTE_IN_MILLIS)}",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                            Text(
                                "Your response: ${checkpoint.recalledResponse?.takeIf { it.isNotBlank() } ?: "No written response"}",
                                style = MaterialTheme.typography.bodyMedium,
                            )
                            Text(
                                "Compare your response with the stored answer; correctness is not scored automatically.",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                }
            }
        }
        item {
            Heading("Recall method comparison")
            Text(
                "Descriptive history, not a controlled experiment. Correctness reflects saved attempt labels; ratings are self-reported.",
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                style = MaterialTheme.typography.bodySmall,
            )
            val summaries = RecallAnalytics.byMode(attemptHistory)
            if (summaries.isEmpty()) {
                Text("Complete recall sessions to compare methods.", color = MaterialTheme.colorScheme.onSurfaceVariant)
            } else {
                summaries.forEach { summary ->
                    Card(shape = RoundedCornerShape(16.dp), modifier = Modifier.fillMaxWidth()) {
                        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            Text(summary.mode.name.lowercase().replaceFirstChar { it.uppercase() },
                                fontWeight = FontWeight.SemiBold)
                            Text("${summary.attempts} attempts · ${summary.accuracyPercent}% marked correct (${summary.correct})")
                            Text("Mean retrieval time: ${"%.1f".format(summary.meanLatencyMs / 1000.0)} s")
                        }
                    }
                }
            }
        }
        item {
            Heading("Review queue")
            val due = memories.count { it.nextReviewAt <= System.currentTimeMillis() }
            Text(if (memories.isEmpty()) "Your first memory unit starts the review system."
                else "$due of ${memories.size} memory units are due now.",
                color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        item { Spacer(Modifier.height(12.dp)) }
    }
}

@Composable
private fun MetricCard(title: String, value: String, detail: String, modifier: Modifier = Modifier) {
    Card(modifier = modifier, shape = RoundedCornerShape(18.dp)) {
        Column(Modifier.padding(11.dp)) {
            Text(title, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(Modifier.height(7.dp))
            Text(value, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
            Text(detail, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun Heading(value: String) {
    Text(value, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
}

@Composable
private fun PrincipleCard(number: String, title: String, detail: String) {
    Card(shape = RoundedCornerShape(18.dp), modifier = Modifier.fillMaxWidth()) {
        Row(Modifier.padding(15.dp), horizontalArrangement = Arrangement.spacedBy(14.dp)) {
            Text(number, color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold)
            Column {
                Text(title, fontWeight = FontWeight.SemiBold)
                Text(detail, color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodyMedium)
            }
        }
    }
}

private data class PalaceNodeDepth(val node: PalaceNode, val depth: Int)

private fun flattenPalaceTree(nodes: List<PalaceNode>): List<PalaceNodeDepth> {
    val byParent = nodes.groupBy { it.parentId }
    val result = mutableListOf<PalaceNodeDepth>()
    val visited = mutableSetOf<String>()
    fun visit(parentId: String?, depth: Int) {
        byParent[parentId].orEmpty().forEach { node ->
            if (visited.add(node.id)) {
                result += PalaceNodeDepth(node, depth)
                visit(node.id, depth + 1)
            }
        }
    }
    visit(null, 0)
    return result
}

private fun allowedChildKinds(node: PalaceNode?): List<PalaceNodeKind> = when (node?.kind) {
    null -> listOf(PalaceNodeKind.Palace)
    PalaceNodeKind.Palace -> listOf(PalaceNodeKind.Route, PalaceNodeKind.Area, PalaceNodeKind.Locus)
    PalaceNodeKind.Route, PalaceNodeKind.Area -> listOf(PalaceNodeKind.Route, PalaceNodeKind.Area, PalaceNodeKind.Locus)
    PalaceNodeKind.Locus -> emptyList()
}

@Composable
private fun PalaceScreen(
    memories: List<MemoryItem>,
    nodes: List<PalaceNode>,
    onAddMemory: () -> Unit,
    onCreateNode: (String, PalaceNodeKind, String?, (List<PalaceNode>) -> Unit) -> Unit,
    onDeleteNode: (String, (List<PalaceNode>) -> Unit) -> Unit,
    onRenameNode: (String, String, (List<PalaceNode>) -> Unit) -> Unit,
    onMoveNode: (String, String?, (List<PalaceNode>) -> Unit) -> Unit,
) {
    var addParent by remember { mutableStateOf<PalaceNode?>(null) }
    var movingNode by remember { mutableStateOf<PalaceNode?>(null) }
    var renamingNode by remember { mutableStateOf<PalaceNode?>(null) }
    var deletingNode by remember { mutableStateOf<PalaceNode?>(null) }
    var showAddDialog by remember { mutableStateOf(false) }
    val flattened = remember(nodes) { flattenPalaceTree(nodes) }
    LazyColumn(
        Modifier.fillMaxSize().padding(18.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        item {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Heading("Memory palace")
                TextButton(onClick = { addParent = null; showAddDialog = true }) { Text("+ Palace") }
            }
            Text(
                "Build a navigable hierarchy: palace → route or area → locus. Each node has a stable ID.",
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        if (flattened.isEmpty()) {
            item {
                Card(shape = RoundedCornerShape(20.dp), modifier = Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(18.dp)) {
                        Text("Start with a palace", fontWeight = FontWeight.SemiBold)
                        Text("Create a familiar building, then add routes, areas, and distinct loci.",
                            color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Spacer(Modifier.height(10.dp))
                        Button(onClick = { addParent = null; showAddDialog = true }) { Text("Create first palace") }
                    }
                }
            }
        }
        items(flattened, key = { it.node.id }) { entry ->
            val node = entry.node
            val itemCount = memories.count { it.palaceNodeId == node.id }
            Card(
                shape = RoundedCornerShape(18.dp),
                modifier = Modifier.fillMaxWidth().padding(start = (entry.depth * 12).dp),
            ) {
                Column(Modifier.padding(14.dp)) {
                    Text(node.kind.label.uppercase(), color = MaterialTheme.colorScheme.primary,
                        style = MaterialTheme.typography.labelSmall)
                    Text(node.name, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                    Text(
                        if (itemCount == 1) "1 linked memory" else "$itemCount linked memories",
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        style = MaterialTheme.typography.bodySmall,
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        if (allowedChildKinds(node).isNotEmpty()) {
                            TextButton(onClick = { addParent = node; showAddDialog = true }) { Text("+ Add child location") }
                        }
                        TextButton(onClick = { renamingNode = node }) { Text("Rename") }
                        if (node.kind != PalaceNodeKind.Palace) {
                            TextButton(onClick = { movingNode = node }) { Text("Move") }
                        }
                        TextButton(onClick = { deletingNode = node }) { Text("Delete branch") }
                    }
                }
            }
        }
        item {
            Spacer(Modifier.height(4.dp))
            Button(onClick = onAddMemory, modifier = Modifier.fillMaxWidth()) {
                Text("Add memory item")
            }
        }
    }

    movingNode?.let { node ->
        MovePalaceNodeDialog(
            node = node,
            nodes = nodes,
            onDismiss = { movingNode = null },
            onMove = { parentId -> onMoveNode(node.id, parentId) { movingNode = null } },
        )
    }

    renamingNode?.let { node ->
        RenamePalaceNodeDialog(
            node = node,
            onDismiss = { renamingNode = null },
            onRename = { name -> onRenameNode(node.id, name) { renamingNode = null } },
        )
    }

    deletingNode?.let { node ->
        AlertDialog(
            onDismissRequest = { deletingNode = null },
            title = { Text("Delete ${node.kind.label.lowercase()} branch?") },
            text = {
                Text("This permanently removes this location and all child locations. Linked memories and their review history are kept, but their palace-location links are cleared.")
            },
            confirmButton = {
                Button(onClick = {
                    onDeleteNode(node.id) { deletingNode = null }
                }) { Text("Delete branch") }
            },
            dismissButton = {
                TextButton(onClick = { deletingNode = null }) { Text("Cancel") }
            },
        )
    }

    if (showAddDialog) {
        AddPalaceNodeDialog(
            parent = addParent,
            allowedKinds = allowedChildKinds(addParent),
            onDismiss = { showAddDialog = false; addParent = null },
            onCreate = { name, kind ->
                onCreateNode(name, kind, addParent?.id) {
                    showAddDialog = false
                    addParent = null
                }
            },
        )
    }
}

@Composable
private fun MovePalaceNodeDialog(
    node: PalaceNode,
    nodes: List<PalaceNode>,
    onDismiss: () -> Unit,
    onMove: (String?) -> Unit,
) {
    val currentParent = nodes.firstOrNull { it.id == node.parentId }
    val candidates = nodes.filter { candidate ->
        candidate.id != currentParent?.id && PalaceMovePlan.isValidParent(nodes, node.id, candidate.id)
    }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Move ${node.kind.label.lowercase()}") },
        text = {
            if (candidates.isEmpty()) {
                Text("There are no valid parent locations for this item.")
            } else {
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text("Choose a new parent location:")
                    candidates.forEach { candidate ->
                        TextButton(onClick = { onMove(candidate.id) }, modifier = Modifier.fillMaxWidth()) {
                            Text("${candidate.kind.label}: ${candidate.name}")
                        }
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}

@Composable
private fun RenamePalaceNodeDialog(
    node: PalaceNode,
    onDismiss: () -> Unit,
    onRename: (String) -> Unit,
) {
    var name by remember(node.id) { mutableStateOf(node.name) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Rename ${node.kind.label.lowercase()}") },
        text = {
            OutlinedTextField(
                value = name,
                onValueChange = { name = it },
                label = { Text("Name") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )
        },
        confirmButton = {
            Button(onClick = { onRename(name.trim()) }, enabled = name.trim().isNotEmpty()) { Text("Save name") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}

@Composable
private fun AddPalaceNodeDialog(
    parent: PalaceNode?,
    allowedKinds: List<PalaceNodeKind>,
    onDismiss: () -> Unit,
    onCreate: (String, PalaceNodeKind) -> Unit,
) {
    var name by remember(parent?.id) { mutableStateOf("") }
    var kind by remember(parent?.id, allowedKinds) { mutableStateOf(allowedKinds.firstOrNull() ?: PalaceNodeKind.Palace) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (parent == null) "Create palace" else "Add location") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                if (parent != null) {
                    Text("Parent: \${parent.name}", color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("Name") },
                    singleLine = true,
                )
                Text("Location type", style = MaterialTheme.typography.labelLarge)
                allowedKinds.forEach { option ->
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        androidx.compose.material3.RadioButton(
                            selected = kind == option,
                            onClick = { kind = option },
                        )
                        Text(option.label, modifier = Modifier.padding(top = 12.dp))
                    }
                }
            }
        },
        confirmButton = {
            Button(onClick = { onCreate(name.trim(), kind) }, enabled = name.isNotBlank() && allowedKinds.isNotEmpty()) {
                Text("Create")
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}

private fun RecallMode.label(): String = when (this) {
    RecallMode.Cued -> "Cued"
    RecallMode.Free -> "Free"
    RecallMode.Reverse -> "Reverse"
    RecallMode.Sequence -> "Sequence"
    RecallMode.Discrimination -> "Discrimination"
    RecallMode.Retention -> "Retention"
}

@Composable
private fun RetentionTestScreen(
    item: MemoryItem?,
    checkpoint: RetentionCheckpoint,
    onComplete: (Int, Long, String?, Int) -> Unit,
) {
    var response by remember(checkpoint.id) { mutableStateOf("") }
    var revealed by remember(checkpoint.id) { mutableStateOf(false) }
    var selectedAccuracy by remember(checkpoint.id) { mutableStateOf<Int?>(null) }
    var selectedEffort by remember(checkpoint.id) { mutableStateOf<Int?>(null) }
    val startedAt = remember(checkpoint.id) { SystemClock.elapsedRealtime() }
    Column(Modifier.fillMaxSize().padding(20.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
        Heading("${checkpoint.delayDays}-day retention test")
        Text(
            "Recall first, then reveal the stored fact. Record answer accuracy separately from how easy recall felt.",
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        if (item == null) {
            Text("The linked memory item could not be found.")
        } else {
            Card(shape = RoundedCornerShape(22.dp), modifier = Modifier.fillMaxWidth()) {
                Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text("FREE RECALL · NO CUE", color = MaterialTheme.colorScheme.primary,
                        style = MaterialTheme.typography.labelLarge)
                    Text("Write everything you can recall about this memory before checking the answer.",
                        style = MaterialTheme.typography.bodyLarge)
                    OutlinedTextField(
                        value = response,
                        onValueChange = { if (!revealed) response = it },
                        label = { Text("What do you remember?") },
                        modifier = Modifier.fillMaxWidth(),
                        minLines = 3,
                        enabled = !revealed,
                    )
                    if (revealed) {
                        Text("EXPECTED PROMPT", color = MaterialTheme.colorScheme.primary,
                            style = MaterialTheme.typography.labelMedium)
                        Text(item.prompt, fontWeight = FontWeight.SemiBold)
                        Text("EXPECTED ANSWER", color = MaterialTheme.colorScheme.primary,
                            style = MaterialTheme.typography.labelMedium)
                        Text(item.answer)
                    } else {
                        Button(onClick = { revealed = true }, modifier = Modifier.fillMaxWidth()) {
                            Text("Reveal expected fact")
                        }
                    }
                }
            }
            if (revealed) {
                Text("1. Compare your response with the stored answer", fontWeight = FontWeight.SemiBold)
                Text("This is a manual check, not AI grading. Choose the closest match.",
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
                listOf(0 to "Incorrect", 1 to "Partially correct", 2 to "Correct")
                    .forEach { (value, label) ->
                        TextButton(onClick = { selectedAccuracy = value }, modifier = Modifier.fillMaxWidth()) {
                            Text(if (selectedAccuracy == value) "✓ $label" else label)
                        }
                    }
                Spacer(Modifier.height(4.dp))
                Text("2. How did retrieval feel?", fontWeight = FontWeight.SemiBold)
                listOf(0 to "Forgot", 2 to "Difficult", 3 to "Partial", 4 to "Mostly recalled", 5 to "Fully recalled")
                    .forEach { (score, label) ->
                        TextButton(onClick = { selectedEffort = score }, modifier = Modifier.fillMaxWidth()) {
                            Text(if (selectedEffort == score) "✓ $label" else label)
                        }
                    }
                Button(
                    onClick = {
                        onComplete(
                            selectedEffort!!,
                            SystemClock.elapsedRealtime() - startedAt,
                            response.trim().takeIf { it.isNotEmpty() },
                            selectedAccuracy!!,
                        )
                    },
                    enabled = selectedAccuracy != null && selectedEffort != null,
                    modifier = Modifier.fillMaxWidth(),
                ) { Text("Save retention result") }
            }
        }
    }
}

@Composable
private fun SequenceRecallScreen(
    sequence: RecallSequence,
    onComplete: (Int, Boolean, Long, String?) -> Unit,
) {
    var response by remember(sequence.label, sequence.items.map { it.id }) { mutableStateOf("") }
    var revealed by remember(sequence.label, sequence.items.map { it.id }) { mutableStateOf(false) }
    var selectedScore by remember(sequence.label, sequence.items.map { it.id }) { mutableStateOf<Int?>(null) }
    var sequenceCorrect by remember(sequence.label, sequence.items.map { it.id }) { mutableStateOf<Boolean?>(null) }
    val startedAt = remember(sequence.label, sequence.items.map { it.id }) { SystemClock.elapsedRealtime() }
    Column(Modifier.fillMaxSize().padding(20.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
        Heading("Ordered recall")
        Text("Sequence: ${sequence.label}", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
        Text("Recall the items in order without looking. Include the sequence numbers if useful, then reveal and compare.",
            color = MaterialTheme.colorScheme.onSurfaceVariant)
        Card(shape = RoundedCornerShape(22.dp), modifier = Modifier.fillMaxWidth()) {
            Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                if (!revealed) {
                    OutlinedTextField(
                        value = response,
                        onValueChange = { response = it },
                        label = { Text("Write the sequence from memory") },
                        modifier = Modifier.fillMaxWidth(),
                        minLines = 5,
                    )
                    Button(onClick = { revealed = true }, modifier = Modifier.fillMaxWidth()) { Text("Reveal expected order") }
                } else {
                    Text("EXPECTED ORDER", color = MaterialTheme.colorScheme.primary, style = MaterialTheme.typography.labelLarge)
                    sequence.items.forEachIndexed { index, item ->
                        Text("${item.sequenceOrder ?: index + 1}. ${item.prompt} — ${item.answer}",
                            style = MaterialTheme.typography.bodyLarge)
                        if (item.association.isNotBlank()) {
                            Text("Association: ${item.association}", style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                }
            }
        }
        if (revealed) {
            Text("After comparing, mark correctness separately from retrieval effort.", fontWeight = FontWeight.SemiBold)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                TextButton(onClick = { sequenceCorrect = false }, modifier = Modifier.weight(1f)) {
                    Text(if (sequenceCorrect == false) "✓ Incorrect" else "Incorrect")
                }
                TextButton(onClick = { sequenceCorrect = true }, modifier = Modifier.weight(1f)) {
                    Text(if (sequenceCorrect == true) "✓ Correct" else "Correct")
                }
            }
            Text("How much effort did retrieval require?", fontWeight = FontWeight.SemiBold)
            listOf(0 to "Again", 2 to "Hard", 3 to "Got it", 5 to "Easy")
                .forEach { (score, label) ->
                    TextButton(onClick = { selectedScore = score }, modifier = Modifier.fillMaxWidth()) {
                        Text(if (selectedScore == score) "✓ $label" else label)
                    }
                }
            Button(
                onClick = { onComplete(selectedScore!!, sequenceCorrect == true, SystemClock.elapsedRealtime() - startedAt, response.trim().takeIf { it.isNotEmpty() }) },
                enabled = selectedScore != null && sequenceCorrect != null,
                modifier = Modifier.fillMaxWidth(),
            ) { Text("Save sequence result") }
        }
    }
}

@Composable
private fun RecallScreen(
    item: MemoryItem?,
    revealed: Boolean,
    mode: RecallMode,
    onModeChange: (RecallMode) -> Unit,
    onReveal: () -> Unit,
    onRate: (Int, Boolean, Long, String?) -> Unit,
    onAdd: () -> Unit,
) {
    var recalledResponse by remember(item?.id, mode) { mutableStateOf("") }
    var userCheckedCorrect by remember(item?.id, mode) { mutableStateOf<Boolean?>(null) }
    val recallStartedAt = remember(item?.id, mode) { SystemClock.elapsedRealtime() }
    Column(Modifier.fillMaxSize().padding(20.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
        Heading("Retrieval practice")
        Text("Choose a retrieval direction. Rate the effort honestly; difficulty is useful data.",
            color = MaterialTheme.colorScheme.onSurfaceVariant)
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.fillMaxWidth()) {
            listOf(RecallMode.Cued, RecallMode.Free, RecallMode.Reverse).forEach { option ->
                TextButton(onClick = { onModeChange(option) }) {
                    Text(if (mode == option) "• ${option.label()}" else option.label())
                }
            }
        }
        if (item == null) {
            Card(shape = RoundedCornerShape(24.dp), modifier = Modifier.fillMaxWidth()) {
                Column(Modifier.padding(20.dp)) {
                    Text("Nothing to practise yet", fontWeight = FontWeight.Bold)
                    Spacer(Modifier.height(8.dp))
                    Text("Add a prompt and answer to start a recall session.")
                    Spacer(Modifier.height(14.dp))
                    Button(onClick = onAdd) { Text("Add memory") }
                }
            }
        } else {
            Card(shape = RoundedCornerShape(26.dp), modifier = Modifier.fillMaxWidth()) {
                Column(Modifier.padding(22.dp)) {
                    if (mode != RecallMode.Free) {
                        Text(if (mode == RecallMode.Reverse) "REVERSE CUE · ANSWER" else "RECALL PROMPT",
                            color = MaterialTheme.colorScheme.primary, style = MaterialTheme.typography.labelLarge)
                        Spacer(Modifier.height(16.dp))
                        Text(if (mode == RecallMode.Reverse) item.answer else item.prompt,
                            style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
                    } else {
                        Text("FREE RECALL · NO CUE", color = MaterialTheme.colorScheme.primary,
                            style = MaterialTheme.typography.labelLarge)
                        Spacer(Modifier.height(16.dp))
                        Text("Retrieve the fact from memory without seeing its prompt.",
                            style = MaterialTheme.typography.bodyLarge)
                    }
                    if (item.palace.isNotBlank()) {
                        Spacer(Modifier.height(10.dp))
                        Text("Place: ${item.palace} · ${item.locus.ifBlank { "locus not set" }}",
                            style = MaterialTheme.typography.labelMedium)
                    }
                    if (revealed) {
                        Spacer(Modifier.height(20.dp))
                        Text(if (mode == RecallMode.Reverse) "EXPECTED PROMPT" else "ANSWER",
                            color = MaterialTheme.colorScheme.primary, style = MaterialTheme.typography.labelLarge)
                        Spacer(Modifier.height(6.dp))
                        if (mode == RecallMode.Reverse) {
                            Text(item.prompt, style = MaterialTheme.typography.bodyLarge)
                        } else if (mode == RecallMode.Free) {
                            Text("PROMPT", color = MaterialTheme.colorScheme.primary,
                                style = MaterialTheme.typography.labelMedium)
                            Text(item.prompt, style = MaterialTheme.typography.bodyLarge)
                            Spacer(Modifier.height(8.dp))
                            Text("ANSWER", color = MaterialTheme.colorScheme.primary,
                                style = MaterialTheme.typography.labelMedium)
                            Text(item.answer, style = MaterialTheme.typography.bodyLarge)
                        } else {
                            Text(item.answer, style = MaterialTheme.typography.bodyLarge)
                        }
                    } else {
                        OutlinedTextField(
                            value = recalledResponse,
                            onValueChange = { recalledResponse = it },
                            label = {
                                Text(
                                    when (mode) {
                                        RecallMode.Free -> "What do you remember?"
                                        RecallMode.Reverse -> "What is the original prompt?"
                                        else -> "Your recalled answer"
                                    },
                                )
                            },
                            modifier = Modifier.fillMaxWidth(),
                            minLines = if (mode == RecallMode.Free) 3 else 1,
                        )
                        Spacer(Modifier.height(20.dp))
                        Button(onClick = onReveal) { Text("Reveal answer") }
                    }
                }
            }
            if (revealed) {
                Text("Check your answer against the revealed answer.", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                Text("Your correctness check and your effort rating are recorded separately. This is self-checked, not automatic grading.", color = MaterialTheme.colorScheme.onSurfaceVariant)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                    TextButton(onClick = { userCheckedCorrect = false }, modifier = Modifier.weight(1f)) {
                        Text(if (userCheckedCorrect == false) "✓ Incorrect" else "Incorrect")
                    }
                    TextButton(onClick = { userCheckedCorrect = true }, modifier = Modifier.weight(1f)) {
                        Text(if (userCheckedCorrect == true) "✓ Correct" else "Correct")
                    }
                }
                Text("How much effort did retrieval require?", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                listOf(0 to "Again", 2 to "Hard", 3 to "Got it", 5 to "Easy").forEach { (score, label) ->
                    TextButton(
                        enabled = userCheckedCorrect != null,
                        onClick = { onRate(score, userCheckedCorrect == true, SystemClock.elapsedRealtime() - recallStartedAt, recalledResponse.trim().takeIf { it.isNotEmpty() }) },
                        modifier = Modifier.fillMaxWidth(),
                    ) { Text(label) }
                }
            }
        }
    }
}

@Composable
private fun LibraryScreen(memories: List<MemoryItem>, onAdd: () -> Unit, onEdit: (MemoryItem) -> Unit, onDelete: (MemoryItem) -> Unit, onImport: () -> Unit, onExport: () -> Unit) {
    var searchQuery by remember { mutableStateOf("") }
    var dueOnly by remember { mutableStateOf(false) }
    val now = System.currentTimeMillis()
    val filteredMemories = remember(memories, searchQuery, dueOnly, now) {
        MemoryLibraryPlan.filterAndSort(memories, searchQuery, dueOnly, now)
    }
    Column(Modifier.fillMaxSize().padding(horizontal = 18.dp)) {
        Spacer(Modifier.height(8.dp))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Heading("Memory library")
            Row {
                TextButton(onClick = onExport, enabled = memories.isNotEmpty()) { Text("Export TSV") }
                TextButton(onClick = onImport) { Text("Import TSV") }
                TextButton(onClick = onAdd) { Text("+ Add") }
            }
        }
        if (memories.isEmpty()) {
            Text("Your saved facts stay on this device. Add one to begin.",
                color = MaterialTheme.colorScheme.onSurfaceVariant)
        } else {
            OutlinedTextField(
                value = searchQuery,
                onValueChange = { searchQuery = it },
                modifier = Modifier.fillMaxWidth(),
                label = { Text("Search memories") },
                singleLine = true,
            )
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text("${filteredMemories.size} memories", style = MaterialTheme.typography.labelMedium)
                TextButton(onClick = { dueOnly = !dueOnly }) {
                    Text(if (dueOnly) "Show all" else "Due reviews only")
                }
            }
            if (filteredMemories.isEmpty()) {
                Text("No memories match this search.", color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            LazyColumn(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                items(filteredMemories, key = { it.id }) { item ->
                    Card(shape = RoundedCornerShape(18.dp), modifier = Modifier.fillMaxWidth()) {
                        Column(Modifier.padding(16.dp)) {
                            Text(item.prompt, fontWeight = FontWeight.SemiBold)
                            Spacer(Modifier.height(4.dp))
                            Text(item.answer, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            if (item.chunkLabel.isNotBlank()) {
                                Text("Chunk: ${item.chunkLabel}", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary)
                            }
                            if (item.association.isNotBlank()) {
                                Text("Association: ${item.association}", style = MaterialTheme.typography.bodySmall)
                            }
                            item.sequenceOrder?.let { order -> Text("Sequence position: $order", style = MaterialTheme.typography.labelSmall) }
                            if (item.palace.isNotBlank()) {
                                Spacer(Modifier.height(8.dp))
                                Text("${item.palace} / ${item.locus}", style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.primary)
                            }
                            Text("Reviews: ${item.reviewCount}", style = MaterialTheme.typography.labelSmall)
                            Row {
                                TextButton(onClick = { onEdit(item) }) { Text("Edit memory") }
                                TextButton(onClick = { onDelete(item) }) { Text("Delete") }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun AddMemoryDialog(
    nodes: List<PalaceNode>,
    initialItem: MemoryItem? = null,
    onDismiss: () -> Unit,
    onCreate: (String, String, String, String, String?, String, String, Int?) -> Unit,
) {
    var prompt by remember(initialItem?.id) { mutableStateOf(initialItem?.prompt.orEmpty()) }
    var answer by remember(initialItem?.id) { mutableStateOf(initialItem?.answer.orEmpty()) }
    var palace by remember(initialItem?.id) { mutableStateOf(initialItem?.palace.orEmpty()) }
    var locus by remember(initialItem?.id) { mutableStateOf(initialItem?.locus.orEmpty()) }
    var selectedNodeId by remember(initialItem?.id) { mutableStateOf(initialItem?.palaceNodeId) }
    var chunkLabel by remember(initialItem?.id) { mutableStateOf(initialItem?.chunkLabel.orEmpty()) }
    var association by remember(initialItem?.id) { mutableStateOf(initialItem?.association.orEmpty()) }
    var sequenceOrderText by remember(initialItem?.id) { mutableStateOf(initialItem?.sequenceOrder?.toString().orEmpty()) }
    var nodeMenuExpanded by remember { mutableStateOf(false) }
    val locusNodes = nodes.filter { it.kind == PalaceNodeKind.Locus }
    val selectedNodeName = locusNodes.firstOrNull { it.id == selectedNodeId }?.let { node ->
        val parentNames = generateSequence(node) { current -> nodes.firstOrNull { it.id == current.parentId } }
            .toList().asReversed().joinToString(" / ") { it.name }
        parentNames
    } ?: "No locus selected"
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (initialItem == null) "Add a memory unit" else "Edit memory unit") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(prompt, { prompt = it }, label = { Text("Prompt or cue") })
                OutlinedTextField(answer, { answer = it }, label = { Text("Answer or fact") })
                Text("Encode for fast, durable recall", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
                OutlinedTextField(chunkLabel, { chunkLabel = it }, label = { Text("Chunk / topic (optional)") }, singleLine = true)
                OutlinedTextField(association, { association = it }, label = { Text("Vivid image or action association (optional)") }, minLines = 2)
                OutlinedTextField(sequenceOrderText, { value -> sequenceOrderText = value.filter(Char::isDigit).take(4) }, label = { Text("Sequence position (optional, e.g. 1)") }, singleLine = true)
                Text("Group related facts under one chunk. Use a concrete, unusual image or action. Number items only when order matters.", color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodySmall)
                val encodingCheck = EncodingQualityPlan.evaluate(prompt, answer, chunkLabel, association)
                Text(
                    "Local encoding checklist: ${encodingCheck.score}/${encodingCheck.maxScore}",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                )
                Text(
                    "This is a completeness checklist, not a prediction of how well you will remember the fact.",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    style = MaterialTheme.typography.bodySmall,
                )
                encodingCheck.suggestions.forEach { suggestion ->
                    Text("• $suggestion", color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodySmall)
                }
                OutlinedTextField(palace, { palace = it }, label = { Text("Palace / building (optional)") }, singleLine = true)
                OutlinedTextField(locus, { locus = it }, label = { Text("Locus / location (optional)") }, singleLine = true)
                if (locusNodes.isNotEmpty()) {
                    Text("Link to a palace locus", style = MaterialTheme.typography.labelMedium)
                    Box {
                        TextButton(onClick = { nodeMenuExpanded = true }) { Text(selectedNodeName) }
                        DropdownMenu(
                            expanded = nodeMenuExpanded,
                            onDismissRequest = { nodeMenuExpanded = false },
                        ) {
                            locusNodes.forEach { node ->
                                val path = generateSequence(node) { current ->
                                    nodes.firstOrNull { it.id == current.parentId }
                                }.toList().asReversed().joinToString(" / ") { it.name }
                                DropdownMenuItem(
                                    text = { Text(path) },
                                    onClick = {
                                        selectedNodeId = node.id
                                        nodeMenuExpanded = false
                                    },
                                )
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            Button(onClick = { onCreate(prompt, answer, palace, locus, selectedNodeId, chunkLabel, association, sequenceOrderText.toIntOrNull()?.takeIf { it > 0 }) },
                enabled = prompt.isNotBlank() && answer.isNotBlank()) { Text(if (initialItem == null) "Save locally" else "Save changes") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}
