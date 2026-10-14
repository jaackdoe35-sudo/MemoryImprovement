package com.memoryimprovement.app

import android.os.Bundle
import android.os.SystemClock
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.launch
import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.OutlinedTextField
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
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import java.util.UUID

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val repository = MemoryRepository(applicationContext)
        lifecycleScope.launch {
            val initialItems = repository.loadItems()
            setContent {
                MaterialTheme {
                    Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
                        MemoryApp(
                            initialItems = initialItems,
                            onSave = { updated -> lifecycleScope.launch { repository.saveItems(updated) } },
                            onAttempt = { attempt -> lifecycleScope.launch { repository.saveAttempt(attempt) } },
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
    onSave: (List<MemoryItem>) -> Unit,
    onAttempt: (RecallAttempt) -> Unit,
) {
    var memories by remember { mutableStateOf(initialItems) }
    var tab by remember { mutableStateOf(AppTab.Today) }
    var adding by remember { mutableStateOf(false) }
    var active by remember { mutableStateOf<MemoryItem?>(null) }
    var revealed by remember { mutableStateOf(false) }
    var attempts by remember { mutableStateOf(0) }
    var correct by remember { mutableStateOf(0) }

    fun save(updated: List<MemoryItem>) {
        memories = updated
        onSave(updated)
    }
    fun startRecall() {
        active = memories.minByOrNull { it.nextReviewAt }
        revealed = false
        tab = AppTab.Recall
    }

    Scaffold(
        topBar = {
            TopAppBar(title = {
                Column {
                    Text("MemoryImprovement", fontWeight = FontWeight.Bold)
                    Text("Build memory you can retrieve", style = MaterialTheme.typography.labelMedium,
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
                AppTab.Today -> TodayScreen(memories, attempts, correct, { adding = true }, ::startRecall)
                AppTab.Palace -> PalaceScreen(memories, { adding = true })
                AppTab.Recall -> RecallScreen(
                    active, revealed, { revealed = true },
                    { score, latencyMs ->
                        val current = active ?: return@RecallScreen
                        val outcome = RecallScheduler.schedule(current.reviewCount, score)
                        val updated = memories.map { memory ->
                            if (memory.id == current.id) memory.copy(
                                nextReviewAt = outcome.nextReviewAt,
                                lastScore = outcome.score,
                                reviewCount = memory.reviewCount + 1,
                            ) else memory
                        }
                        save(updated)
                        attempts += 1
                        if (score >= 3) correct += 1
                        onAttempt(
                            RecallAttempt(
                                itemId = current.id,
                                mode = RecallMode.Cued,
                                correct = score >= 3,
                                score = score,
                                latencyMs = latencyMs,
                            ),
                        )
                        active = updated.filter { it.id != current.id }.minByOrNull { it.nextReviewAt }
                        revealed = false
                    },
                    { adding = true },
                )
                AppTab.Library -> LibraryScreen(memories, { adding = true })
            }
        }
    }

    if (adding) {
        AddMemoryDialog(
            onDismiss = { adding = false },
            onCreate = { prompt, answer, palace, locus ->
                val now = System.currentTimeMillis()
                val item = MemoryItem(
                    id = UUID.randomUUID().toString(), prompt = prompt.trim(), answer = answer.trim(),
                    palace = palace.trim(), locus = locus.trim(), createdAt = now, nextReviewAt = now,
                )
                save(memories + item)
                adding = false
                if (tab == AppTab.Recall) { active = item; revealed = false }
            },
        )
    }
}

@Composable
private fun TodayScreen(
    memories: List<MemoryItem>, attempts: Int, correct: Int,
    onAdd: () -> Unit, onRecall: () -> Unit,
) {
    LazyColumn(Modifier.fillMaxSize().padding(horizontal = 18.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)) {
        item { Spacer(Modifier.height(4.dp)) }
        item {
            Card(
                colors = CardDefaults.cardColors(containerColor = Color(0xFF202C4A)),
                shape = RoundedCornerShape(26.dp), modifier = Modifier.fillMaxWidth(),
            ) {
                Column(Modifier.padding(22.dp)) {
                    Text("YOUR NEXT REP", color = Color(0xFFB9C8FF), style = MaterialTheme.typography.labelLarge)
                    Spacer(Modifier.height(10.dp))
                    Text(if (memories.isEmpty()) "Turn information into recall." else "Recall before you review.",
                        color = Color.White, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
                    Spacer(Modifier.height(8.dp))
                    Text("Encode it. Place it. Retrieve it later.", color = Color(0xFFD9E0F7))
                    Spacer(Modifier.height(18.dp))
                    Button(onClick = if (memories.isEmpty()) onAdd else onRecall) {
                        Text(if (memories.isEmpty()) "Add your first memory" else "Start recall")
                    }
                }
            }
        }
        item {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                MetricCard("Stored", memories.size.toString(), "memory units", Modifier.weight(1f))
                MetricCard("Recalled", attempts.toString(), "this session", Modifier.weight(1f))
                MetricCard("Accuracy", if (attempts == 0) "—" else "${(correct * 100) / attempts}%", "session", Modifier.weight(1f))
            }
        }
        item { Heading("The system") }
        item { PrincipleCard("01", "Encode", "Chunk facts and create distinctive associations.") }
        item { PrincipleCard("02", "Place", "Put facts into a navigable palace, route, and locus.") }
        item { PrincipleCard("03", "Retrieve", "Practise cues, free recall, and reverse connections.") }
        item { PrincipleCard("04", "Verify", "Measure delayed recall, retrieval speed, and interference.") }
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
) {
    var addParent by remember { mutableStateOf<PalaceNode?>(null) }
    val flattened = remember(nodes) { flattenPalaceTree(nodes) }
    LazyColumn(
        Modifier.fillMaxSize().padding(18.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        item {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Heading("Memory palace")
                TextButton(onClick = { addParent = null }) { Text("+ Palace") }
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
                        Button(onClick = { addParent = null }) { Text("Create first palace") }
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
                    if (allowedChildKinds(node).isNotEmpty()) {
                        TextButton(onClick = { addParent = node }) { Text("+ Add child location") }
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

    if (addParent != null || nodes.isEmpty()) {
        AddPalaceNodeDialog(
            parent = addParent,
            allowedKinds = allowedChildKinds(addParent),
            onDismiss = { addParent = null },
            onCreate = { name, kind ->
                onCreateNode(name, kind, addParent?.id) { refreshed ->
                    addParent = null
                }
            },
        )
    }
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

@Composable
private fun RecallScreen(
    item: MemoryItem?, revealed: Boolean, onReveal: () -> Unit,
    onRate: (Int, Long) -> Unit, onAdd: () -> Unit,
) {
    val recallStartedAt = remember(item?.id) { SystemClock.elapsedRealtime() }
    Column(Modifier.fillMaxSize().padding(20.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
        Heading("Retrieval practice")
        Text("Try to retrieve the answer before revealing it. Rate the effort honestly; difficulty is useful data.",
            color = MaterialTheme.colorScheme.onSurfaceVariant)
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
                    Text("RECALL PROMPT", color = MaterialTheme.colorScheme.primary, style = MaterialTheme.typography.labelLarge)
                    Spacer(Modifier.height(16.dp))
                    Text(item.prompt, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
                    if (item.palace.isNotBlank()) {
                        Spacer(Modifier.height(10.dp))
                        Text("Place: ${item.palace} · ${item.locus.ifBlank { "locus not set" }}",
                            style = MaterialTheme.typography.labelMedium)
                    }
                    if (revealed) {
                        Spacer(Modifier.height(20.dp))
                        Text("ANSWER", color = MaterialTheme.colorScheme.primary, style = MaterialTheme.typography.labelLarge)
                        Spacer(Modifier.height(6.dp))
                        Text(item.answer, style = MaterialTheme.typography.bodyLarge)
                    } else {
                        Spacer(Modifier.height(20.dp))
                        Button(onClick = onReveal) { Text("Reveal answer") }
                    }
                }
            }
            if (revealed) {
                Text("How well did you retrieve it?", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                listOf(0 to "Again", 2 to "Hard", 3 to "Got it", 5 to "Easy").forEach { (score, label) ->
                    TextButton(
                        onClick = { onRate(score, SystemClock.elapsedRealtime() - recallStartedAt) },
                        modifier = Modifier.fillMaxWidth(),
                    ) { Text(label) }
                }
            }
        }
    }
}

@Composable
private fun LibraryScreen(memories: List<MemoryItem>, onAdd: () -> Unit) {
    Column(Modifier.fillMaxSize().padding(horizontal = 18.dp)) {
        Spacer(Modifier.height(8.dp))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Heading("Memory library")
            TextButton(onClick = onAdd) { Text("+ Add") }
        }
        if (memories.isEmpty()) {
            Text("Your saved facts stay on this device. Add one to begin.",
                color = MaterialTheme.colorScheme.onSurfaceVariant)
        } else {
            LazyColumn(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                items(memories, key = { it.id }) { item ->
                    Card(shape = RoundedCornerShape(18.dp), modifier = Modifier.fillMaxWidth()) {
                        Column(Modifier.padding(16.dp)) {
                            Text(item.prompt, fontWeight = FontWeight.SemiBold)
                            Spacer(Modifier.height(4.dp))
                            Text(item.answer, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            if (item.palace.isNotBlank()) {
                                Spacer(Modifier.height(8.dp))
                                Text("${item.palace} / ${item.locus}", style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.primary)
                            }
                            Text("Reviews: ${item.reviewCount}", style = MaterialTheme.typography.labelSmall)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun AddMemoryDialog(
    onDismiss: () -> Unit,
    onCreate: (String, String, String, String) -> Unit,
) {
    var prompt by remember { mutableStateOf("") }
    var answer by remember { mutableStateOf("") }
    var palace by remember { mutableStateOf("") }
    var locus by remember { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Add a memory unit") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(prompt, { prompt = it }, label = { Text("Prompt or cue") })
                OutlinedTextField(answer, { answer = it }, label = { Text("Answer or fact") })
                OutlinedTextField(palace, { palace = it }, label = { Text("Palace / building (optional)") }, singleLine = true)
                OutlinedTextField(locus, { locus = it }, label = { Text("Locus / location (optional)") }, singleLine = true)
            }
        },
        confirmButton = {
            Button(onClick = { onCreate(prompt, answer, palace, locus) },
                enabled = prompt.isNotBlank() && answer.isNotBlank()) { Text("Save locally") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}
