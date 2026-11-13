package com.example.bookapp.ui.screens

import android.content.Context
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.snapshotFlow
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.bookapp.data.AudioPlayerHelper
import com.example.bookapp.data.SectionEntity
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collect

@Immutable
data class RehearsalTurnItem(
    val section: SectionEntity,
    val roleTitle: String,
    val isOwnRole: Boolean,
    val orderLabel: String = ""
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MultiRoleRehearsalScreen(
    taziehTitle: String,
    ownRoleId: Long,
    ownRoleTitle: String,
    turns: List<RehearsalTurnItem>,
    onBack: () -> Unit,
    canPlayAudio: Boolean = true,
    canAddPracticeNote: Boolean = true,
    onAddPracticeNote: (SectionEntity, String, String) -> Unit
) {
    val context = LocalContext.current
    val audioIconColor = Color(0xFFD84343)
    val noteIconColor = Color(0xFF2869B2)
    val prefs = remember(context) {
        context.getSharedPreferences("rehearsal_progress", Context.MODE_PRIVATE)
    }
    var playingAudioSectionId by remember { mutableStateOf<Long?>(null) }
    val audioPlayerHelper = remember(context) {
        AudioPlayerHelper(context) { status ->
            if (status == "done" || status == "error") playingAudioSectionId = null
        }
    }
    var noteTarget by remember { mutableStateOf<SectionEntity?>(null) }

    DisposableEffect(audioPlayerHelper, turns) {
        onDispose {
            audioPlayerHelper.stop()
            playingAudioSectionId = null
        }
    }

    val ownSectionIds = remember(turns) {
        turns.filter { it.isOwnRole }.map { it.section.id }.distinct()
    }
    val ownSectionKey = ownSectionIds.joinToString(",")
    val turnSequenceKey = turns.joinToString(",") { "${it.section.roleId}-${it.section.id}" }
    val scrollPrefsKey = "multi_${ownRoleId}_${turnSequenceKey.hashCode()}"
    val ownLinesBySection = remember(turns) {
        turns.filter { it.isOwnRole }.associate { turn ->
            turn.section.id to turn.section.content.lines().filter { it.isNotBlank() }
        }
    }

    val startLineBySection = remember(ownRoleId, ownSectionKey) {
        mutableStateMapOf<Long, Int>().apply {
            ownSectionIds.forEach { sectionId ->
                val count = ownLinesBySection[sectionId]?.size ?: 0
                put(
                    sectionId,
                    prefs.getInt("start_line_$sectionId", 1).coerceIn(1, count.coerceAtLeast(1))
                )
            }
        }
    }
    val initialRevealCounts = ownSectionIds.associateWith { sectionId ->
        val lineCount = ownLinesBySection[sectionId]?.size ?: 0
        ((startLineBySection[sectionId] ?: 1) - 1).coerceIn(0, lineCount)
    }
    val revealedBySection = remember(ownRoleId, ownSectionKey) {
        mutableStateMapOf<Long, Int>().apply { putAll(initialRevealCounts) }
    }

    var learnedIds by remember(ownRoleId) {
        mutableStateOf(
            prefs.getStringSet("learned_sections_$ownRoleId", emptySet()).orEmpty().toSet()
        )
    }
    var autoPlay by remember(ownRoleId, ownSectionKey) { mutableStateOf(false) }
    var speed by remember { mutableIntStateOf(3) }
    var startPickerSection by remember { mutableStateOf<SectionEntity?>(null) }
    var restoredScrollPosition by remember(scrollPrefsKey) { mutableStateOf(false) }

    val ownTotalLines = ownSectionIds.sumOf { ownLinesBySection[it]?.size ?: 0 }
    val revealedTotal = ownSectionIds.sumOf { sectionId ->
        (revealedBySection[sectionId] ?: 0).coerceIn(0, ownLinesBySection[sectionId]?.size ?: 0)
    }
    val listState = rememberLazyListState()
    var focusTurnIndex by remember(turnSequenceKey) {
        mutableIntStateOf(turns.indexOfFirst { it.isOwnRole }.coerceAtLeast(0))
    }
    var focusRequested by remember { mutableStateOf(false) }

    LaunchedEffect(turns.size, scrollPrefsKey) {
        if (turns.isNotEmpty() && !restoredScrollPosition) {
            val savedIndex = prefs.getInt("multi_scroll_index_$scrollPrefsKey", 0)
                .coerceIn(0, turns.size)
            val savedOffset = prefs.getInt("multi_scroll_offset_$scrollPrefsKey", 0).coerceAtLeast(0)
            listState.scrollToItem(savedIndex, savedOffset)
            restoredScrollPosition = true
        }
    }

    LaunchedEffect(listState, scrollPrefsKey, restoredScrollPosition) {
        if (!restoredScrollPosition) return@LaunchedEffect
        snapshotFlow { listState.firstVisibleItemIndex to listState.firstVisibleItemScrollOffset }
            .collect { (index, offset) ->
                prefs.edit()
                    .putInt("multi_scroll_index_$scrollPrefsKey", index)
                    .putInt("multi_scroll_offset_$scrollPrefsKey", offset)
                    .apply()
            }
    }

    fun nextHiddenOwnTurnIndex(): Int {
        return turns.indexOfFirst { turn ->
            if (!turn.isOwnRole) return@indexOfFirst false
            val lineCount = ownLinesBySection[turn.section.id]?.size ?: 0
            (revealedBySection[turn.section.id] ?: 0) < lineCount
        }
    }

    fun revealNextOwnLine() {
        val turnIndex = nextHiddenOwnTurnIndex()
        if (turnIndex < 0) {
            autoPlay = false
            return
        }
        val sectionId = turns[turnIndex].section.id
        val maxLines = ownLinesBySection[sectionId]?.size ?: 0
        val current = revealedBySection[sectionId] ?: 0
        if (current < maxLines) {
            revealedBySection[sectionId] = current + 1
            if (focusTurnIndex != turnIndex) {
                focusTurnIndex = turnIndex
                focusRequested = true
            }
        }
    }

    LaunchedEffect(autoPlay, speed, revealedTotal, ownSectionKey, turnSequenceKey) {
        if (!autoPlay) return@LaunchedEffect
        if (nextHiddenOwnTurnIndex() < 0) {
            autoPlay = false
            return@LaunchedEffect
        }
        delay(speed * 1000L)
        revealNextOwnLine()
    }

    LaunchedEffect(focusTurnIndex, revealedTotal, focusRequested, restoredScrollPosition) {
        if (!focusRequested || !restoredScrollPosition || turns.isEmpty()) return@LaunchedEffect
        listState.animateScrollToItem((focusTurnIndex + 1).coerceIn(0, turns.size))
        focusRequested = false
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("تمرین چندنقشی", fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "بازگشت")
                    }
                }
            )
        },
        bottomBar = {
            Surface(shadowElevation = 8.dp) {
                Column(
                    Modifier
                        .fillMaxWidth()
                        .navigationBarsPadding()
                        .padding(horizontal = 12.dp, vertical = 8.dp),
                    verticalArrangement = Arrangement.spacedBy(7.dp)
                ) {
                    if (ownTotalLines > 0) {
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            Text("آشکارشده: $revealedTotal از $ownTotalLines خط", style = MaterialTheme.typography.labelSmall)
                            Text("${(revealedTotal * 100 / ownTotalLines)}٪", style = MaterialTheme.typography.labelSmall)
                        }
                        LinearProgressIndicator(
                            progress = { revealedTotal.toFloat() / ownTotalLines.toFloat() },
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                    Row(
                        Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(5.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        FilterChip(
                            selected = autoPlay,
                            enabled = ownTotalLines > revealedTotal,
                            onClick = { autoPlay = !autoPlay },
                            label = { Text(if (autoPlay) "توقف" else "خودخوان") }
                        )
                        listOf(2, 3, 5).forEach { seconds ->
                            FilterChip(
                                selected = speed == seconds,
                                onClick = { speed = seconds },
                                label = { Text("${seconds}ث") }
                            )
                        }
                    }
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(7.dp)) {
                        OutlinedButton(
                            onClick = {
                                autoPlay = false
                                revealedBySection.clear()
                                revealedBySection.putAll(initialRevealCounts)
                                focusTurnIndex = turns.indexOfFirst { it.isOwnRole }.coerceAtLeast(0)
                                focusRequested = true
                            },
                            enabled = revealedTotal > initialRevealCounts.values.sum(),
                            modifier = Modifier.weight(1f),
                            contentPadding = PaddingValues(horizontal = 5.dp, vertical = 7.dp)
                        ) {
                            Icon(Icons.Filled.VisibilityOff, contentDescription = null)
                            Spacer(Modifier.width(4.dp))
                            Text("پنهان‌سازی")
                        }
                        Button(
                            onClick = { autoPlay = false; revealNextOwnLine() },
                            enabled = revealedTotal < ownTotalLines,
                            modifier = Modifier.weight(1f),
                            contentPadding = PaddingValues(horizontal = 5.dp, vertical = 7.dp)
                        ) {
                            Icon(Icons.Filled.Visibility, contentDescription = null)
                            Spacer(Modifier.width(4.dp))
                            Text("خط بعد")
                        }
                        OutlinedButton(
                            onClick = {
                                autoPlay = false
                                ownSectionIds.forEach { sectionId ->
                                    revealedBySection[sectionId] = ownLinesBySection[sectionId]?.size ?: 0
                                }
                                focusTurnIndex = turns.indexOfLast { it.isOwnRole }.coerceAtLeast(0)
                                focusRequested = true
                            },
                            enabled = revealedTotal < ownTotalLines,
                            modifier = Modifier.weight(1f),
                            contentPadding = PaddingValues(horizontal = 5.dp, vertical = 7.dp)
                        ) { Text("نمایش کامل") }
                    }
                }
            }
        }
    ) { padding ->
        if (turns.isEmpty()) {
            Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) {
                Text("برای این تمرین، نوبت یا بخشی انتخاب نشده است.")
            }
        } else {
            LazyColumn(
                state = listState,
                modifier = Modifier.fillMaxSize().padding(padding),
                contentPadding = PaddingValues(12.dp),
                verticalArrangement = Arrangement.spacedBy(9.dp)
            ) {
                item {
                    Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
                        Text(taziehTitle, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                        Text("نقش اصلی تمرین: $ownRoleTitle", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.primary)
                        turns.firstOrNull()?.orderLabel?.takeIf { it.isNotBlank() }?.let {
                            Text(it, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                }
                itemsIndexed(turns, key = { index, turn -> "${turn.section.id}-$index" }) { index, turn ->
                    val section = turn.section
                    val lines = remember(section.id, section.content) {
                        section.content.lines().filter { it.isNotBlank() }
                    }
                    val shownCount = if (turn.isOwnRole) {
                        (revealedBySection[section.id] ?: initialRevealCounts[section.id] ?: 0).coerceIn(0, lines.size)
                    } else {
                        lines.size
                    }
                    val learned = section.id.toString() in learnedIds
                    val cardScrollState = rememberScrollState()

                    LaunchedEffect(turn.isOwnRole, section.id, shownCount) {
                        if (turn.isOwnRole && shownCount > 0) {
                            withFrameNanos { }
                            cardScrollState.animateScrollTo(cardScrollState.maxValue)
                        }
                    }

                    Card(
                        modifier = Modifier.fillMaxWidth().height(260.dp),
                        shape = RoundedCornerShape(15.dp),
                        colors = CardDefaults.cardColors(
                            containerColor = if (turn.isOwnRole) MaterialTheme.colorScheme.primaryContainer
                            else MaterialTheme.colorScheme.surfaceVariant
                        )
                    ) {
                        Column(Modifier.fillMaxSize()) {
                            Column(
                                Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp),
                                verticalArrangement = Arrangement.spacedBy(2.dp)
                            ) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Column(Modifier.weight(1f)) {
                                        Text(turn.roleTitle, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                                        Text(section.title, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                    }
                                    if (canPlayAudio) {
                                        IconButton(
                                            onClick = {
                                                val audioUrl = section.audioUrl?.takeIf { it.isNotBlank() } ?: return@IconButton
                                                if (playingAudioSectionId == section.id) {
                                                    audioPlayerHelper.stop()
                                                    playingAudioSectionId = null
                                                } else {
                                                    audioPlayerHelper.stop()
                                                    playingAudioSectionId = section.id
                                                    audioPlayerHelper.play(audioUrl)
                                                }
                                            },
                                            enabled = !section.audioUrl.isNullOrBlank(),
                                            modifier = Modifier.size(36.dp)
                                        ) {
                                            Icon(
                                                if (playingAudioSectionId == section.id) Icons.Filled.Stop else Icons.Filled.PlayArrow,
                                                contentDescription = if (section.audioUrl.isNullOrBlank()) "صوت ثبت نشده" else if (playingAudioSectionId == section.id) "توقف صوت" else "پخش صوت",
                                                modifier = Modifier.size(19.dp),
                                                tint = audioIconColor.copy(
                                                    alpha = if (section.audioUrl.isNullOrBlank()) 0.45f else 1f
                                                )
                                            )
                                        }
                                    }
                                    if (canAddPracticeNote) {
                                        IconButton(
                                            onClick = { noteTarget = section },
                                            modifier = Modifier.size(36.dp)
                                        ) {
                                            Icon(
                                                Icons.Filled.Edit,
                                                contentDescription = "یادداشت برای این بخش",
                                                modifier = Modifier.size(18.dp),
                                                tint = noteIconColor
                                            )
                                        }
                                    }
                                    if (turn.isOwnRole) {
                                        AssistChip(onClick = {}, label = { Text("نقش اصلی") }, enabled = false)
                                    }
                                }
                                if (turn.isOwnRole) {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        TextButton(
                                            onClick = { startPickerSection = section },
                                            contentPadding = PaddingValues(horizontal = 2.dp, vertical = 0.dp)
                                        ) { Text("انتخاب محل شروع", style = MaterialTheme.typography.labelSmall) }
                                        Spacer(Modifier.weight(1f))
                                        TextButton(
                                            onClick = {
                                                val key = section.id.toString()
                                                val updated = if (key in learnedIds) learnedIds - key else learnedIds + key
                                                learnedIds = updated
                                                prefs.edit().putStringSet("learned_sections_$ownRoleId", updated).apply()
                                            },
                                            contentPadding = PaddingValues(horizontal = 2.dp, vertical = 0.dp)
                                        ) {
                                            Text(if (learned) "✓ تمرین شده" else "علامت‌گذاری تمرین‌شده", style = MaterialTheme.typography.labelSmall)
                                        }
                                    }
                                }
                            }
                            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                            Column(
                                Modifier
                                    .fillMaxWidth()
                                    .weight(1f)
                                    .verticalScroll(cardScrollState)
                                    .padding(horizontal = 12.dp, vertical = 9.dp),
                                verticalArrangement = Arrangement.spacedBy(7.dp)
                            ) {
                                if (turn.isOwnRole && shownCount == 0 && lines.isNotEmpty()) {
                                    Text(
                                        "متن این بخش پنهان است؛ با «خط بعد» یا «خودخوان» آن را آشکار کنید.",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                                if (turn.isOwnRole) {
                                    lines.take(shownCount).forEach { line ->
                                        Text(line, style = MaterialTheme.typography.bodyLarge)
                                    }
                                    if (shownCount < lines.size) {
                                        Text(
                                            "${lines.size - shownCount} خط پنهان",
                                            style = MaterialTheme.typography.labelSmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                } else {
                                    lines.forEach { line ->
                                        Text(line, style = MaterialTheme.typography.bodyLarge)
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    startPickerSection?.let { section ->
        val lines = remember(section.id, section.content) {
            section.content.lines().filter { it.isNotBlank() }
        }
        val selectedStartLine = startLineBySection[section.id] ?: 1
        AlertDialog(
            onDismissRequest = { startPickerSection = null },
            title = { Text("انتخاب محل شروع تمرین") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text(
                        "متن کامل را پیمایش کنید و روی سطری بزنید که تمرین این بخش باید از آن آغاز شود.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    if (lines.isEmpty()) {
                        Text("متنی برای این بخش ثبت نشده است.")
                    } else {
                        LazyColumn(Modifier.heightIn(max = 440.dp)) {
                            itemsIndexed(lines) { index, line ->
                                Surface(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clickable {
                                            val startLine = index + 1
                                            startLineBySection[section.id] = startLine
                                            prefs.edit().putInt("start_line_${section.id}", startLine).apply()
                                            revealedBySection[section.id] = index
                                            autoPlay = false
                                            focusTurnIndex = turns.indexOfFirst {
                                                it.isOwnRole && it.section.id == section.id
                                            }.coerceAtLeast(0)
                                            focusRequested = true
                                            startPickerSection = null
                                        },
                                    shape = RoundedCornerShape(9.dp),
                                    color = if (selectedStartLine == index + 1) {
                                        MaterialTheme.colorScheme.primaryContainer
                                    } else {
                                        MaterialTheme.colorScheme.surface
                                    }
                                ) {
                                    Text(
                                        line,
                                        modifier = Modifier.fillMaxWidth().padding(horizontal = 10.dp, vertical = 9.dp),
                                        style = MaterialTheme.typography.bodyMedium
                                    )
                                }
                                Spacer(Modifier.height(3.dp))
                            }
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        startLineBySection[section.id] = 1
                        prefs.edit().putInt("start_line_${section.id}", 1).apply()
                        revealedBySection[section.id] = 0
                        autoPlay = false
                        focusTurnIndex = turns.indexOfFirst {
                            it.isOwnRole && it.section.id == section.id
                        }.coerceAtLeast(0)
                        focusRequested = true
                        startPickerSection = null
                    }
                ) { Text("شروع از ابتدا") }
            },
            dismissButton = {
                TextButton(onClick = { startPickerSection = null }) { Text("بستن") }
            }
        )
    }

    noteTarget?.let { target ->
        val roleTitle = turns.firstOrNull { it.section.id == target.id }?.roleTitle.orEmpty()
        RehearsalNoteDialog(
            roleTitle = roleTitle,
            sectionTitle = target.title,
            onDismiss = { noteTarget = null },
            onSave = { title, content ->
                onAddPracticeNote(target, title, content)
                noteTarget = null
            }
        )
    }
}
