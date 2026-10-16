package com.memoryimprovement.app

import android.net.Uri
import android.os.Bundle
import android.os.SystemClock
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.LibraryBooks
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.FileDownload
import androidx.compose.material.icons.outlined.FileUpload
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material.icons.outlined.Place
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.lifecycleScope
import com.memoryimprovement.app.theme.ContextualHelpIcon
import com.memoryimprovement.app.theme.Spacing
import com.memoryimprovement.app.theme.pressScaleEffect
import kotlinx.coroutines.launch
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
                MemoryImprovementTheme {
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

@OptIn(ExperimentalMaterial3Api::class)
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
            NavigationBar(containerColor = MaterialTheme.colorScheme.surface) {
                AppTab.entries.forEach { destination ->
                    NavigationBarItem(
                        selected = tab == destination,
                        onClick = { tab = destination; if (destination == AppTab.Recall) startRecall() },
                        icon = {
                            Icon(
                                imageVector = when (destination) {
                                    AppTab.Today -> Icons.Outlined.Home
                                    AppTab.Palace -> Icons.Outlined.Place
                                    AppTab.Recall -> Icons.Outlined.Refresh
                                    AppTab.Library -> Icons.AutoMirrored.Outlined.LibraryBooks
                                },
                                contentDescription = destination.label,
                            )
                        },
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
                }, modifier = Modifier.pressScaleEffect()) { Text("Delete") }
            },
            dismissButton = {
                TextButton(onClick = { deletingMemory = null }, modifier = Modifier.pressScaleEffect()) { Text("Cancel") }
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
                Column(verticalArrangement = Arrangement.spacedBy(Spacing.small)) {
                    Text("A full backup replaces all current memories, palace locations, recall attempts, and retention checkpoints.")
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
                    }, modifier = Modifier.pressScaleEffect()) { Text("Choose backup JSON") }
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
                    modifier = Modifier.pressScaleEffect(),
                ) { Text("Replace local data") }
            },
            dismissButton = {
                TextButton(onClick = {
                    backupImportOpen = false
                    backupImportSnapshot = null
                    backupImportError = null
                    backupImportStatus = null
                }, modifier = Modifier.pressScaleEffect()) { Text("Cancel") }
            },
        )
    }

    if (bulkImportOpen) {
        AlertDialog(
            onDismissRequest = { bulkImportOpen = false; bulkImportError = null; bulkImportStatus = null; bulkImportPreview = null },
            title = { Text("Bulk import memories") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(Spacing.small)) {
                    Text("Paste tab-separated rows: prompt, answer, palace, locus, chunk, association, sequence order.")
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
                    }, modifier = Modifier.pressScaleEffect()) { Text("Choose TSV file") }
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
                }, modifier = Modifier.pressScaleEffect()) { Text(if (bulkImportPreview == null) "Preview import" else "Confirm import") }
            },
            dismissButton = {
                TextButton(onClick = { bulkImportOpen = false; bulkImportError = null }, modifier = Modifier.pressScaleEffect()) { Text("Cancel") }
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
                Column(verticalArrangement = Arrangement.spacedBy(Spacing.small)) {
                    Text("This prompt matches or closely resembles existing cues.")
                    pendingCueConflicts.forEach { conflict ->
                        Card(
                            shape = RoundedCornerShape(Spacing.cardCornerRadius),
                            border = BorderStroke(Spacing.outlineWidth, MaterialTheme.colorScheme.outlineVariant),
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                            elevation = CardDefaults.cardElevation(defaultElevation = Spacing.none),
                            modifier = Modifier.fillMaxWidth().pressScaleEffect(),
                        ) {
                            Column(Modifier.padding(Spacing.normal), verticalArrangement = Arrangement.spacedBy(Spacing.extraSmall)) {
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
                }, modifier = Modifier.pressScaleEffect()) { Text("Save anyway") }
            },
            dismissButton = {
                Row(horizontalArrangement = Arrangement.spacedBy(Spacing.extraSmall)) {
                    TextButton(onClick = {
                        pendingCueCandidate = null
                        pendingCueConflicts = emptyList()
                    }, modifier = Modifier.pressScaleEffect()) { Text("Edit cue") }
                    TextButton(onClick = {
                        pendingCueCandidate = null
                        pendingCueConflicts = emptyList()
                        adding = false
                        editingMemory = null
                    }, modifier = Modifier.pressScaleEffect()) { Text("Discard") }
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
    val accuracyPercent = if (attempts == 0) 0 else (correct * 100) / attempts

    LazyColumn(
        Modifier.fillMaxSize().padding(horizontal = Spacing.normal),
        verticalArrangement = Arrangement.spacedBy(Spacing.medium),
    ) {
        item { Spacer(Modifier.height(Spacing.extraSmall)) }
        item {
            Card(
                shape = RoundedCornerShape(Spacing.cardCornerRadius),
                border = BorderStroke(Spacing.outlineWidth, MaterialTheme.colorScheme.outlineVariant),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                elevation = CardDefaults.cardElevation(defaultElevation = Spacing.none),
                modifier = Modifier.fillMaxWidth().pressScaleEffect(),
            ) {
                Column(Modifier.padding(Spacing.normal), verticalArrangement = Arrangement.spacedBy(Spacing.small)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text("Data safety", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleMedium)
                        ContextualHelpIcon(
                            title = "Data Backup & Safety",
                            description = "Full JSON backups export all your memories, palace structures, recall attempt logs, and retention checkpoints to a portable JSON file.",
                        )
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(Spacing.small)) {
                        Button(onClick = onExportFullBackup, modifier = Modifier.weight(1f).pressScaleEffect()) {
                            Icon(Icons.Outlined.FileDownload, contentDescription = null, modifier = Modifier.size(Spacing.iconSizeSmall))
                            Spacer(Modifier.size(Spacing.extraSmall))
                            Text("Export backup")
                        }
                        TextButton(onClick = onImportFullBackup, modifier = Modifier.weight(1f).pressScaleEffect()) {
                            Icon(Icons.Outlined.FileUpload, contentDescription = null, modifier = Modifier.size(Spacing.iconSizeSmall))
                            Spacer(Modifier.size(Spacing.extraSmall))
                            Text("Restore backup")
                        }
                    }
                }
            }
        }
        if (migrationResult.errorMessage != null) {
            item {
                Card(
                    shape = RoundedCornerShape(Spacing.cardCornerRadius),
                    border = BorderStroke(Spacing.outlineWidth, MaterialTheme.colorScheme.error),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer),
                    elevation = CardDefaults.cardElevation(defaultElevation = Spacing.none),
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Column(Modifier.padding(Spacing.normal), verticalArrangement = Arrangement.spacedBy(Spacing.small)) {
                        Text("Legacy data needs recovery", fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onErrorContainer)
                        Text(migrationResult.errorMessage, color = MaterialTheme.colorScheme.onErrorContainer)
                        migrationResult.preservedRawData?.let { raw ->
                            Button(onClick = { onExportLegacyData(raw) }, modifier = Modifier.pressScaleEffect()) { Text("Export legacy JSON backup") }
                        }
                    }
                }
            }
        }
        item {
            Card(
                shape = RoundedCornerShape(Spacing.extraLarge),
                border = BorderStroke(Spacing.outlineWidth, MaterialTheme.colorScheme.primaryContainer),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer),
                elevation = CardDefaults.cardElevation(defaultElevation = Spacing.none),
                modifier = Modifier.fillMaxWidth().pressScaleEffect(),
            ) {
                Column(Modifier.padding(Spacing.extraLarge)) {
                    Text(
                        if (memories.isEmpty()) "YOUR STARTING POINT" else "YOUR NEXT PRACTICE",
                        color = MaterialTheme.colorScheme.onPrimaryContainer,
                        style = MaterialTheme.typography.labelLarge,
                    )
                    Spacer(Modifier.height(Spacing.small))
                    Text(
                        if (memories.isEmpty()) "You can train your memory." else "Recall first. Review second.",
                        color = MaterialTheme.colorScheme.onPrimaryContainer,
                        style = MaterialTheme.typography.headlineSmall,
                        fontWeight = FontWeight.Bold,
                    )
                    Spacer(Modifier.height(Spacing.normal))
                    Button(onClick = if (memories.isEmpty()) onAdd else onRecall, modifier = Modifier.pressScaleEffect()) {
                        Text(if (memories.isEmpty()) "Add a first fact" else "Practise recall")
                    }
                }
            }
        }
        item {
            Row(horizontalArrangement = Arrangement.spacedBy(Spacing.small), modifier = Modifier.fillMaxWidth()) {
                MetricCard("Stored", memories.size.toString(), "memory units", Modifier.weight(1f))
                MetricCard("Attempts", attempts.toString(), "all recorded", Modifier.weight(1f))
                ProgressRingMetricCard(
                    title = "Accuracy",
                    percentage = accuracyPercent,
                    detail = if (attempts == 0) "no attempts" else "$correct / $attempts correct",
                    modifier = Modifier.weight(1f),
                )
            }
        }
        item {
            Column(verticalArrangement = Arrangement.spacedBy(Spacing.small)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Heading("Learn the essentials")
                    ContextualHelpIcon(
                        title = "Essential Learning Principles",
                        description = "1. Active Recall: Attempt retrieval before revealing answers.\n2. Spaced Practice: Revisit items across separate sessions.\n3. Distinctive Associations: Connect facts with vivid, unusual mental images.",
                    )
                }
                Card(
                    shape = RoundedCornerShape(Spacing.cardCornerRadius),
                    border = BorderStroke(Spacing.outlineWidth, MaterialTheme.colorScheme.outlineVariant),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer),
                    elevation = CardDefaults.cardElevation(defaultElevation = Spacing.none),
                    modifier = Modifier.fillMaxWidth().pressScaleEffect(),
                ) {
                    Column(Modifier.padding(Spacing.normal), verticalArrangement = Arrangement.spacedBy(Spacing.extraSmall)) {
                        Text("01  Active recall", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onPrimaryContainer)
                        Text("Try to remember before looking at the answer.", color = MaterialTheme.colorScheme.onPrimaryContainer)
                    }
                }
                Card(
                    shape = RoundedCornerShape(Spacing.cardCornerRadius),
                    border = BorderStroke(Spacing.outlineWidth, MaterialTheme.colorScheme.outlineVariant),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    elevation = CardDefaults.cardElevation(defaultElevation = Spacing.none),
                    modifier = Modifier.fillMaxWidth().pressScaleEffect(),
                ) {
                    Column(Modifier.padding(Spacing.normal), verticalArrangement = Arrangement.spacedBy(Spacing.extraSmall)) {
                        Text("02  Spaced practice", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                        Text("Revisit information across separate sessions over time.")
                    }
                }
                Card(
                    shape = RoundedCornerShape(Spacing.cardCornerRadius),
                    border = BorderStroke(Spacing.outlineWidth, MaterialTheme.colorScheme.outlineVariant),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    elevation = CardDefaults.cardElevation(defaultElevation = Spacing.none),
                    modifier = Modifier.fillMaxWidth().pressScaleEffect(),
                ) {
                    Column(Modifier.padding(Spacing.normal), verticalArrangement = Arrangement.spacedBy(Spacing.extraSmall)) {
                        Text("03  Distinctive associations", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                        Text("Connect a fact to a concrete, unusual mental picture.")
                    }
                }
            }
        }
        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Heading("Your practice results")
                ContextualHelpIcon(
                    title = "Practice Results & Analytics",
                    description = "Analytics summarize recall accuracy and mean latency broken down by recall mode (Cued, Free, Reverse, Sequence, Discrimination).",
                )
            }
            if (attemptHistory.isEmpty()) {
                Text("Results will appear after your first recall attempt.", color = MaterialTheme.colorScheme.onSurfaceVariant)
            } else {
                RecallAnalytics.byMode(attemptHistory).forEach { summary ->
                    Column(verticalArrangement = Arrangement.spacedBy(Spacing.extraSmall)) {
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            Text(summary.mode.label(), fontWeight = FontWeight.SemiBold)
                            Text("${summary.accuracyPercent}% · ${summary.attempts} attempts")
                        }
                        Text("Mean retrieval time: ${summary.meanLatencyMs / 1000.0}s",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
        }
        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Heading("Delayed-retention results")
                ContextualHelpIcon(
                    title = "Delayed Retention Checkpoints",
                    description = "Delayed retention checkpoints measure long-term retrieval strength at 1-day, 7-day, and 30-day intervals.",
                )
            }
            RetentionAnalytics.byDelay(checkpoints).forEach { summary ->
                Column(verticalArrangement = Arrangement.spacedBy(Spacing.micro)) {
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text("${summary.delayDays}-day checkpoint", fontWeight = FontWeight.SemiBold)
                        Text("${summary.completed}/${summary.total} completed")
                    }
                    summary.meanSelfRating?.let { mean ->
                        Text("Mean self-rating: ${"%.1f".format(mean)}/5", color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
        }
        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Heading("Priority practice")
                ContextualHelpIcon(
                    title = "Priority Queue Logic",
                    description = "Priority queue ranks items needing review based on low previous self-ratings, overdue review dates, and repeated incorrect attempts.",
                )
            }
            val priorities = WeakMemoryPlan.rank(memories, attemptHistory)
            if (priorities.isEmpty()) {
                Text("Add memory items to build a targeted practice queue.", color = MaterialTheme.colorScheme.onSurfaceVariant)
            } else {
                priorities.forEach { priority ->
                    Card(
                        shape = RoundedCornerShape(Spacing.cardCornerRadius),
                        border = BorderStroke(Spacing.outlineWidth, MaterialTheme.colorScheme.outlineVariant),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                        elevation = CardDefaults.cardElevation(defaultElevation = Spacing.none),
                        modifier = Modifier.fillMaxWidth().pressScaleEffect(),
                    ) {
                        Column(Modifier.padding(Spacing.normal), verticalArrangement = Arrangement.spacedBy(Spacing.extraSmall)) {
                            Text(priority.item.prompt, fontWeight = FontWeight.SemiBold)
                            Text(priority.reason, color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodySmall)
                            Button(onClick = { onStartPriority(priority.item) }, modifier = Modifier.fillMaxWidth().pressScaleEffect()) {
                                Text("Practise this memory")
                            }
                        }
                    }
                }
            }
        }
        item { Spacer(Modifier.height(Spacing.medium)) }
    }
}

@Composable
private fun MetricCard(title: String, value: String, detail: String, modifier: Modifier = Modifier) {
    Card(
        modifier = modifier.pressScaleEffect(),
        shape = RoundedCornerShape(Spacing.cardCornerRadius),
        border = BorderStroke(Spacing.outlineWidth, MaterialTheme.colorScheme.outlineVariant),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = Spacing.none),
    ) {
        Column(Modifier.padding(Spacing.medium)) {
            Text(title, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(Modifier.height(Spacing.small))
            Text(value, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
            Text(detail, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun ProgressRingMetricCard(title: String, percentage: Int, detail: String, modifier: Modifier = Modifier) {
    Card(
        modifier = modifier.pressScaleEffect(),
        shape = RoundedCornerShape(Spacing.cardCornerRadius),
        border = BorderStroke(Spacing.outlineWidth, MaterialTheme.colorScheme.outlineVariant),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = Spacing.none),
    ) {
        Column(
            Modifier.padding(Spacing.medium),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(title, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(Modifier.height(Spacing.small))
            Box(contentAlignment = Alignment.Center) {
                CircularProgressIndicator(
                    progress = { percentage / 100f },
                    modifier = Modifier.size(Spacing.progressRingSize),
                    color = MaterialTheme.colorScheme.primary,
                    strokeWidth = Spacing.progressRingStrokeWidth,
                    trackColor = MaterialTheme.colorScheme.surfaceVariant,
                )
                Text("$percentage%", style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold)
            }
            Spacer(Modifier.height(Spacing.extraSmall))
            Text(detail, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun Heading(value: String) {
    Text(value, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
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
        Modifier.fillMaxSize().padding(Spacing.normal),
        verticalArrangement = Arrangement.spacedBy(Spacing.medium),
    ) {
        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Heading("Memory palace")
                Row(verticalAlignment = Alignment.CenterVertically) {
                    ContextualHelpIcon(
                        title = "Memory Palace Technique",
                        description = "A memory palace uses a familiar physical location as mental addresses. Place each fact at a specific spot along a route and mentally walk through to retrieve it.",
                    )
                    TextButton(onClick = { addParent = null; showAddDialog = true }, modifier = Modifier.pressScaleEffect()) { Text("+ Palace") }
                }
            }
        }
        if (flattened.isEmpty()) {
            item {
                Card(
                    shape = RoundedCornerShape(Spacing.cardCornerRadius),
                    border = BorderStroke(Spacing.outlineWidth, MaterialTheme.colorScheme.outlineVariant),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    elevation = CardDefaults.cardElevation(defaultElevation = Spacing.none),
                    modifier = Modifier.fillMaxWidth().pressScaleEffect(),
                ) {
                    Column(Modifier.padding(Spacing.normal)) {
                        Text("Start with a palace", fontWeight = FontWeight.SemiBold)
                        Spacer(Modifier.height(Spacing.small))
                        Button(onClick = { addParent = null; showAddDialog = true }, modifier = Modifier.pressScaleEffect()) { Text("Create first palace") }
                    }
                }
            }
        }
        items(flattened, key = { it.node.id }) { entry ->
            val node = entry.node
            val itemCount = memories.count { it.palaceNodeId == node.id }
            Card(
                shape = RoundedCornerShape(Spacing.cardCornerRadius),
                border = BorderStroke(Spacing.outlineWidth, MaterialTheme.colorScheme.outlineVariant),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                elevation = CardDefaults.cardElevation(defaultElevation = Spacing.none),
                modifier = Modifier.fillMaxWidth().padding(start = (entry.depth * 12).dp).pressScaleEffect(),
            ) {
                Column(Modifier.padding(Spacing.normal)) {
                    Text(node.kind.label.uppercase(), color = MaterialTheme.colorScheme.primary, style = MaterialTheme.typography.labelSmall)
                    Text(node.name, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                    Text(
                        if (itemCount == 1) "1 linked memory" else "$itemCount linked memories",
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        style = MaterialTheme.typography.bodySmall,
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(Spacing.small)) {
                        if (allowedChildKinds(node).isNotEmpty()) {
                            TextButton(onClick = { addParent = node; showAddDialog = true }, modifier = Modifier.pressScaleEffect()) { Text("+ Add location") }
                        }
                        TextButton(onClick = { renamingNode = node }, modifier = Modifier.pressScaleEffect()) { Text("Rename") }
                        if (node.kind != PalaceNodeKind.Palace) {
                            TextButton(onClick = { movingNode = node }, modifier = Modifier.pressScaleEffect()) { Text("Move") }
                        }
                        TextButton(onClick = { deletingNode = node }, modifier = Modifier.pressScaleEffect()) { Text("Delete") }
                    }
                }
            }
        }
        item {
            Spacer(Modifier.height(Spacing.extraSmall))
            Button(onClick = onAddMemory, modifier = Modifier.fillMaxWidth().pressScaleEffect()) {
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
            text = { Text("This permanently removes this location and all child locations.") },
            confirmButton = {
                Button(onClick = { onDeleteNode(node.id) { deletingNode = null } }, modifier = Modifier.pressScaleEffect()) { Text("Delete branch") }
            },
            dismissButton = {
                TextButton(onClick = { deletingNode = null }, modifier = Modifier.pressScaleEffect()) { Text("Cancel") }
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
                Column(verticalArrangement = Arrangement.spacedBy(Spacing.extraSmall)) {
                    Text("Choose a new parent location:")
                    candidates.forEach { candidate ->
                        TextButton(onClick = { onMove(candidate.id) }, modifier = Modifier.fillMaxWidth().pressScaleEffect()) {
                            Text("${candidate.kind.label}: ${candidate.name}")
                        }
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss, modifier = Modifier.pressScaleEffect()) { Text("Cancel") } },
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
            Button(onClick = { onRename(name.trim()) }, enabled = name.trim().isNotEmpty(), modifier = Modifier.pressScaleEffect()) { Text("Save name") }
        },
        dismissButton = { TextButton(onClick = onDismiss, modifier = Modifier.pressScaleEffect()) { Text("Cancel") } },
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
            Column(verticalArrangement = Arrangement.spacedBy(Spacing.small)) {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("Name") },
                    singleLine = true,
                )
                Text("Location type", style = MaterialTheme.typography.labelLarge)
                allowedKinds.forEach { option ->
                    Row(horizontalArrangement = Arrangement.spacedBy(Spacing.small)) {
                        RadioButton(
                            selected = kind == option,
                            onClick = { kind = option },
                        )
                        Text(option.label, modifier = Modifier.padding(top = Spacing.medium))
                    }
                }
            }
        },
        confirmButton = {
            Button(onClick = { onCreate(name.trim(), kind) }, enabled = name.isNotBlank() && allowedKinds.isNotEmpty(), modifier = Modifier.pressScaleEffect()) {
                Text("Create")
            }
        },
        dismissButton = { TextButton(onClick = onDismiss, modifier = Modifier.pressScaleEffect()) { Text("Cancel") } },
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

    Column(Modifier.fillMaxSize().padding(Spacing.large), verticalArrangement = Arrangement.spacedBy(Spacing.medium)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Heading("${checkpoint.delayDays}-day retention test")
            ContextualHelpIcon(
                title = "Retention Test Guide",
                description = "First write what you remember without cues, reveal the answer, and record both accuracy and retrieval effort.",
            )
        }
        if (item == null) {
            Text("The linked memory item could not be found.")
        } else {
            Card(
                shape = RoundedCornerShape(Spacing.cardCornerRadius),
                border = BorderStroke(Spacing.outlineWidth, MaterialTheme.colorScheme.outlineVariant),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                elevation = CardDefaults.cardElevation(defaultElevation = Spacing.none),
                modifier = Modifier.fillMaxWidth().pressScaleEffect(),
            ) {
                Column(Modifier.padding(Spacing.normal), verticalArrangement = Arrangement.spacedBy(Spacing.medium)) {
                    Text("FREE RECALL · NO CUE", color = MaterialTheme.colorScheme.primary, style = MaterialTheme.typography.labelLarge)
                    OutlinedTextField(
                        value = response,
                        onValueChange = { if (!revealed) response = it },
                        label = { Text("What do you remember?") },
                        modifier = Modifier.fillMaxWidth(),
                        minLines = 3,
                        enabled = !revealed,
                    )
                    if (revealed) {
                        Text("EXPECTED PROMPT", color = MaterialTheme.colorScheme.primary, style = MaterialTheme.typography.labelMedium)
                        Text(item.prompt, fontWeight = FontWeight.SemiBold)
                        Text("EXPECTED ANSWER", color = MaterialTheme.colorScheme.primary, style = MaterialTheme.typography.labelMedium)
                        Text(item.answer)
                    } else {
                        Button(onClick = { revealed = true }, modifier = Modifier.fillMaxWidth().pressScaleEffect()) {
                            Text("Reveal expected fact")
                        }
                    }
                }
            }
            if (revealed) {
                Text("1. Compare response", fontWeight = FontWeight.SemiBold)
                listOf(0 to "Incorrect", 1 to "Partially correct", 2 to "Correct").forEach { (value, label) ->
                    TextButton(onClick = { selectedAccuracy = value }, modifier = Modifier.fillMaxWidth().pressScaleEffect()) {
                        Text(if (selectedAccuracy == value) "✓ $label" else label)
                    }
                }
                Spacer(Modifier.height(Spacing.extraSmall))
                Text("2. Retrieval effort", fontWeight = FontWeight.SemiBold)
                listOf(0 to "Forgot", 2 to "Difficult", 3 to "Partial", 4 to "Mostly recalled", 5 to "Fully recalled").forEach { (score, label) ->
                    TextButton(onClick = { selectedEffort = score }, modifier = Modifier.fillMaxWidth().pressScaleEffect()) {
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
                    modifier = Modifier.fillMaxWidth().pressScaleEffect(),
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

    Column(Modifier.fillMaxSize().padding(Spacing.large), verticalArrangement = Arrangement.spacedBy(Spacing.medium)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Heading("Ordered recall")
            ContextualHelpIcon(
                title = "Ordered Sequence Practice",
                description = "Practise recalling items in explicit numbered order.",
            )
        }
        Text("Sequence: ${sequence.label}", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
        Card(
            shape = RoundedCornerShape(Spacing.cardCornerRadius),
            border = BorderStroke(Spacing.outlineWidth, MaterialTheme.colorScheme.outlineVariant),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            elevation = CardDefaults.cardElevation(defaultElevation = Spacing.none),
            modifier = Modifier.fillMaxWidth().pressScaleEffect(),
        ) {
            Column(Modifier.padding(Spacing.normal), verticalArrangement = Arrangement.spacedBy(Spacing.medium)) {
                if (!revealed) {
                    OutlinedTextField(
                        value = response,
                        onValueChange = { response = it },
                        label = { Text("Write sequence from memory") },
                        modifier = Modifier.fillMaxWidth(),
                        minLines = 4,
                    )
                    Button(onClick = { revealed = true }, modifier = Modifier.fillMaxWidth().pressScaleEffect()) { Text("Reveal expected order") }
                } else {
                    Text("EXPECTED ORDER", color = MaterialTheme.colorScheme.primary, style = MaterialTheme.typography.labelLarge)
                    sequence.items.forEachIndexed { index, item ->
                        Text("${item.sequenceOrder ?: index + 1}. ${item.prompt} — ${item.answer}", style = MaterialTheme.typography.bodyLarge)
                    }
                }
            }
        }
        if (revealed) {
            Row(horizontalArrangement = Arrangement.spacedBy(Spacing.small), modifier = Modifier.fillMaxWidth()) {
                TextButton(onClick = { sequenceCorrect = false }, modifier = Modifier.weight(1f).pressScaleEffect()) {
                    Text(if (sequenceCorrect == false) "✓ Incorrect" else "Incorrect")
                }
                TextButton(onClick = { sequenceCorrect = true }, modifier = Modifier.weight(1f).pressScaleEffect()) {
                    Text(if (sequenceCorrect == true) "✓ Correct" else "Correct")
                }
            }
            listOf(0 to "Again", 2 to "Hard", 3 to "Got it", 5 to "Easy").forEach { (score, label) ->
                TextButton(onClick = { selectedScore = score }, modifier = Modifier.fillMaxWidth().pressScaleEffect()) {
                    Text(if (selectedScore == score) "✓ $label" else label)
                }
            }
            Button(
                onClick = { onComplete(selectedScore!!, sequenceCorrect == true, SystemClock.elapsedRealtime() - startedAt, response.trim().takeIf { it.isNotEmpty() }) },
                enabled = selectedScore != null && sequenceCorrect != null,
                modifier = Modifier.fillMaxWidth().pressScaleEffect(),
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

    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(Spacing.large),
        verticalArrangement = Arrangement.spacedBy(Spacing.normal),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Heading("Retrieval practice")
            ContextualHelpIcon(
                title = "Retrieval Practice Modes",
                description = "Cued: Question → Answer.\nFree: Retrieve fact without seeing cue.\nReverse: Answer → Question.",
            )
        }
        Row(horizontalArrangement = Arrangement.spacedBy(Spacing.extraSmall), modifier = Modifier.fillMaxWidth()) {
            listOf(RecallMode.Cued, RecallMode.Free, RecallMode.Reverse).forEach { option ->
                if (mode == option) {
                    Button(onClick = { onModeChange(option) }, modifier = Modifier.weight(1f).pressScaleEffect()) {
                        Text(option.label())
                    }
                } else {
                    TextButton(onClick = { onModeChange(option) }, modifier = Modifier.weight(1f).pressScaleEffect()) {
                        Text(option.label())
                    }
                }
            }
        }
        if (item == null) {
            Card(
                shape = RoundedCornerShape(Spacing.cardCornerRadius),
                border = BorderStroke(Spacing.outlineWidth, MaterialTheme.colorScheme.outlineVariant),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                elevation = CardDefaults.cardElevation(defaultElevation = Spacing.none),
                modifier = Modifier.fillMaxWidth().pressScaleEffect(),
            ) {
                Column(Modifier.padding(Spacing.normal)) {
                    Text("Nothing to practise yet", fontWeight = FontWeight.Bold)
                    Spacer(Modifier.height(Spacing.small))
                    Button(onClick = onAdd, modifier = Modifier.pressScaleEffect()) { Text("Add memory") }
                }
            }
        } else {
            Card(
                shape = RoundedCornerShape(Spacing.cardCornerRadius),
                border = BorderStroke(Spacing.outlineWidth, MaterialTheme.colorScheme.outlineVariant),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                elevation = CardDefaults.cardElevation(defaultElevation = Spacing.none),
                modifier = Modifier.fillMaxWidth().pressScaleEffect(),
            ) {
                Column(Modifier.padding(Spacing.normal)) {
                    if (mode != RecallMode.Free) {
                        Text(if (mode == RecallMode.Reverse) "REVERSE CUE · ANSWER" else "RECALL PROMPT",
                            color = MaterialTheme.colorScheme.primary, style = MaterialTheme.typography.labelLarge)
                        Spacer(Modifier.height(Spacing.medium))
                        Text(if (mode == RecallMode.Reverse) item.answer else item.prompt,
                            style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
                    } else {
                        Text("FREE RECALL · NO CUE", color = MaterialTheme.colorScheme.primary,
                            style = MaterialTheme.typography.labelLarge)
                        Spacer(Modifier.height(Spacing.medium))
                        Text("Retrieve fact from memory.", style = MaterialTheme.typography.bodyLarge)
                    }
                    if (revealed) {
                        Spacer(Modifier.height(Spacing.normal))
                        Text(if (mode == RecallMode.Reverse) "EXPECTED PROMPT" else "ANSWER",
                            color = MaterialTheme.colorScheme.primary, style = MaterialTheme.typography.labelLarge)
                        Spacer(Modifier.height(Spacing.small))
                        Text(if (mode == RecallMode.Reverse) item.prompt else item.answer, style = MaterialTheme.typography.bodyLarge)
                    } else {
                        OutlinedTextField(
                            value = recalledResponse,
                            onValueChange = { recalledResponse = it },
                            label = { Text("Recalled answer") },
                            modifier = Modifier.fillMaxWidth(),
                        )
                        Spacer(Modifier.height(Spacing.normal))
                        Button(onClick = onReveal, modifier = Modifier.pressScaleEffect()) { Text("Reveal answer") }
                    }
                }
            }
            if (revealed) {
                Text("Compare & Rate", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                Row(horizontalArrangement = Arrangement.spacedBy(Spacing.small), modifier = Modifier.fillMaxWidth()) {
                    TextButton(onClick = { userCheckedCorrect = false }, modifier = Modifier.weight(1f).pressScaleEffect()) {
                        Text(if (userCheckedCorrect == false) "✓ Incorrect" else "Incorrect")
                    }
                    TextButton(onClick = { userCheckedCorrect = true }, modifier = Modifier.weight(1f).pressScaleEffect()) {
                        Text(if (userCheckedCorrect == true) "✓ Correct" else "Correct")
                    }
                }
                listOf(0 to "Again", 2 to "Hard", 3 to "Got it", 5 to "Easy").forEach { (score, label) ->
                    TextButton(
                        enabled = userCheckedCorrect != null,
                        onClick = { onRate(score, userCheckedCorrect == true, SystemClock.elapsedRealtime() - recallStartedAt, recalledResponse.trim().takeIf { it.isNotEmpty() }) },
                        modifier = Modifier.fillMaxWidth().pressScaleEffect(),
                    ) { Text(label) }
                }
            }
        }
    }
}

@Composable
private fun LibraryScreen(
    memories: List<MemoryItem>,
    onAdd: () -> Unit,
    onEdit: (MemoryItem) -> Unit,
    onDelete: (MemoryItem) -> Unit,
    onImport: () -> Unit,
    onExport: () -> Unit,
) {
    var searchQuery by remember { mutableStateOf("") }
    var dueOnly by remember { mutableStateOf(false) }
    val now = System.currentTimeMillis()
    val filteredMemories = remember(memories, searchQuery, dueOnly, now) {
        MemoryLibraryPlan.filterAndSort(memories, searchQuery, dueOnly, now)
    }

    Column(Modifier.fillMaxSize().padding(horizontal = Spacing.normal)) {
        Spacer(Modifier.height(Spacing.small))
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Heading("Memory library")
            Row(verticalAlignment = Alignment.CenterVertically) {
                ContextualHelpIcon(
                    title = "Memory Library",
                    description = "Browse, search, edit, import, and export all study items.",
                )
                IconButton(onClick = onExport, enabled = memories.isNotEmpty(), modifier = Modifier.pressScaleEffect()) {
                    Icon(Icons.Outlined.FileDownload, contentDescription = "Export")
                }
                IconButton(onClick = onImport, modifier = Modifier.pressScaleEffect()) {
                    Icon(Icons.Outlined.FileUpload, contentDescription = "Import")
                }
                IconButton(onClick = onAdd, modifier = Modifier.pressScaleEffect()) {
                    Icon(Icons.Outlined.Add, contentDescription = "Add Memory")
                }
            }
        }
        if (memories.isEmpty()) {
            Card(
                shape = RoundedCornerShape(Spacing.cardCornerRadius),
                border = BorderStroke(Spacing.outlineWidth, MaterialTheme.colorScheme.outlineVariant),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                elevation = CardDefaults.cardElevation(defaultElevation = Spacing.none),
                modifier = Modifier.fillMaxWidth().pressScaleEffect(),
            ) {
                Column(Modifier.padding(Spacing.normal), verticalArrangement = Arrangement.spacedBy(Spacing.small)) {
                    Text("Your library is empty", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                    Button(onClick = onAdd, modifier = Modifier.fillMaxWidth().pressScaleEffect()) { Text("Create first memory") }
                }
            }
        } else {
            OutlinedTextField(
                value = searchQuery,
                onValueChange = { searchQuery = it },
                modifier = Modifier.fillMaxWidth(),
                label = { Text("Search memories") },
                leadingIcon = { Icon(Icons.Outlined.Search, contentDescription = null) },
                singleLine = true,
            )
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                Text("${filteredMemories.size} memories", style = MaterialTheme.typography.labelMedium)
                TextButton(onClick = { dueOnly = !dueOnly }, modifier = Modifier.pressScaleEffect()) {
                    Text(if (dueOnly) "Show all" else "Due reviews only")
                }
            }
            LazyColumn(verticalArrangement = Arrangement.spacedBy(Spacing.small)) {
                items(filteredMemories, key = { it.id }) { item ->
                    Card(
                        shape = RoundedCornerShape(Spacing.cardCornerRadius),
                        border = BorderStroke(Spacing.outlineWidth, MaterialTheme.colorScheme.outlineVariant),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                        elevation = CardDefaults.cardElevation(defaultElevation = Spacing.none),
                        modifier = Modifier.fillMaxWidth().pressScaleEffect(),
                    ) {
                        Column(Modifier.padding(Spacing.normal)) {
                            Text(item.prompt, fontWeight = FontWeight.SemiBold)
                            Spacer(Modifier.height(Spacing.extraSmall))
                            Text(item.answer, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            Row(horizontalArrangement = Arrangement.End, modifier = Modifier.fillMaxWidth()) {
                                IconButton(onClick = { onEdit(item) }, modifier = Modifier.pressScaleEffect()) {
                                    Icon(Icons.Outlined.Edit, contentDescription = "Edit")
                                }
                                IconButton(onClick = { onDelete(item) }, modifier = Modifier.pressScaleEffect()) {
                                    Icon(Icons.Outlined.Delete, contentDescription = "Delete")
                                }
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
    var setupStep by remember(initialItem?.id) { mutableStateOf(0) }
    val locusNodes = nodes.filter { it.kind == PalaceNodeKind.Locus }
    val selectedNodeName = locusNodes.firstOrNull { it.id == selectedNodeId }?.let { node ->
        generateSequence(node) { current -> nodes.firstOrNull { it.id == current.parentId } }
            .toList().asReversed().joinToString(" / ") { it.name }
    } ?: "No locus selected"

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (initialItem == null) "Create a memory · ${setupStep + 1} of 3" else "Edit memory · ${setupStep + 1} of 3") },
        text = {
            Column(
                modifier = Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(Spacing.small),
            ) {
                if (setupStep == 0) {
                    OutlinedTextField(prompt, { prompt = it }, label = { Text("Question or cue") }, modifier = Modifier.fillMaxWidth())
                    OutlinedTextField(answer, { answer = it }, label = { Text("Answer") }, modifier = Modifier.fillMaxWidth())
                }
                if (setupStep == 1) {
                    OutlinedTextField(chunkLabel, { chunkLabel = it }, label = { Text("Topic (optional)") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                    OutlinedTextField(association, { association = it }, label = { Text("Unusual image/action (optional)") }, minLines = 2, modifier = Modifier.fillMaxWidth())
                    val encodingCheck = EncodingQualityPlan.evaluate(prompt, answer, chunkLabel, association)
                    Text("Quality score: ${encodingCheck.score}/${encodingCheck.maxScore}", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
                }
                if (setupStep == 2) {
                    OutlinedTextField(sequenceOrderText, { value -> sequenceOrderText = value.filter(Char::isDigit).take(4) }, label = { Text("Order number (optional)") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                    OutlinedTextField(palace, { palace = it }, label = { Text("Building (optional)") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                    OutlinedTextField(locus, { locus = it }, label = { Text("Spot in building (optional)") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                    if (locusNodes.isNotEmpty()) {
                        Box {
                            TextButton(onClick = { nodeMenuExpanded = true }, modifier = Modifier.pressScaleEffect()) { Text(selectedNodeName) }
                            DropdownMenu(expanded = nodeMenuExpanded, onDismissRequest = { nodeMenuExpanded = false }) {
                                locusNodes.forEach { node ->
                                    val path = generateSequence(node) { current -> nodes.firstOrNull { it.id == current.parentId } }
                                        .toList().asReversed().joinToString(" / ") { it.name }
                                    DropdownMenuItem(text = { Text(path) }, onClick = { selectedNodeId = node.id; nodeMenuExpanded = false })
                                }
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    if (setupStep < 2) setupStep += 1
                    else onCreate(prompt, answer, palace, locus, selectedNodeId, chunkLabel, association, sequenceOrderText.toIntOrNull()?.takeIf { it > 0 })
                },
                enabled = if (setupStep == 0) prompt.isNotBlank() && answer.isNotBlank() else true,
                modifier = Modifier.pressScaleEffect(),
            ) { Text(if (setupStep < 2) "Continue" else if (initialItem == null) "Save fact" else "Save changes") }
        },
        dismissButton = { TextButton(onClick = onDismiss, modifier = Modifier.pressScaleEffect()) { Text("Cancel") } },
    )
}
