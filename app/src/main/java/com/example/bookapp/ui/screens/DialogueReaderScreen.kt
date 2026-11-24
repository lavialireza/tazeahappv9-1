
package com.example.bookapp.ui.screens

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import kotlinx.coroutines.yield
import kotlin.math.roundToInt

data class DialogueTurnDisplay(
    val turnId: Long,
    val sectionId: Long,
    val roleTitle: String,
    val sectionTitle: String,
    val content: String,
    val roleId: Long = 0L,
    val versionLabel: String = "",
    val sourceSequenceIndex: Int? = null,
    val sourceSequenceStatus: String = "UNVERIFIED",
    val rehearsalOrderIndex: Int = 0,
    val expectsResponse: Boolean = false,
    val replyToTurnId: Long? = null,
    val expectedResponderRoleId: Long? = null,
    val expectedResponderRoleTitle: String? = null,
    val sequenceEvidence: String = "",
    val directorNote: String = "",
    val stageCues: List<com.example.bookapp.data.DialogueStageCueEntity> = emptyList()
)

private data class RoleStudyGroup(
    val roleId: Long,
    val roleTitle: String,
    val versionLabel: String,
    val numberedTurns: List<Pair<Int, DialogueTurnDisplay>>
)

private data class RoleSearchMatch(
    val turnId: Long,
    val start: Int,
    val end: Int
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DialogueReaderScreen(
    dialogueTitle: String,
    turns: List<DialogueTurnDisplay>,
    roleOrder: List<Long> = emptyList(),
    onMoveTurn: (index: Int, direction: Int) -> Unit,
    onDeleteTurn: (DialogueTurnDisplay) -> Unit,
    onAddTurn: () -> Unit = {},
    onExportPdf: () -> Unit = {},
    onBack: () -> Unit,
    readOnly: Boolean = false,
    showPdf: Boolean = true
) {
    var showOrderEditor by remember { mutableStateOf(false) }

    val queryByRole = remember {
        mutableStateMapOf<Long, String>()
    }

    val activeMatchByRole = remember {
        mutableStateMapOf<Long, Int>()
    }

    var expandedRoleId by remember {
        mutableStateOf<Long?>(null)
    }

    val orderedTurns = remember(turns) {
        turns.sortedWith(
            compareBy<DialogueTurnDisplay> { it.rehearsalOrderIndex }
                .thenBy { it.turnId }
        )
    }

    val roleGroups = remember(orderedTurns, roleOrder) {
        val turnsByRole = orderedTurns
            .filter { it.roleId > 0L }
            .groupBy { it.roleId }

        val idsInOrder = buildList {
            roleOrder.forEach { id ->
                if (id in turnsByRole && id !in this) {
                    add(id)
                }
            }

            turnsByRole.keys.forEach { id ->
                if (id !in this) {
                    add(id)
                }
            }
        }

        idsInOrder.mapNotNull { roleId ->
            val roleTurns = turnsByRole[roleId].orEmpty()

            if (roleTurns.isEmpty()) {
                null
            } else {
                RoleStudyGroup(
                    roleId = roleId,
                    roleTitle = roleTurns.first().roleTitle,
                    versionLabel = roleTurns.first().versionLabel,
                    numberedTurns = roleTurns.map { turn ->
                        (
                            orderedTurns.indexOfFirst {
                                it.turnId == turn.turnId
                            } + 1
                        ) to turn
                    }
                )
            }
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(dialogueTitle, maxLines = 1)
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "بازگشت"
                        )
                    }
                },
                actions = {
                    if (!readOnly) {
                        TextButton(
                            onClick = {
                                showOrderEditor = true
                            }
                        ) {
                            Text("ترتیب نوبت‌ها")
                        }

                        IconButton(onClick = onAddTurn) {
                            Icon(
                                Icons.Default.Add,
                                contentDescription = "افزودن متن"
                            )
                        }
                    }

                    if (showPdf) {
                        IconButton(onClick = onExportPdf) {
                            Icon(
                                Icons.Default.Share,
                                contentDescription = "خروجی PDF"
                            )
                        }
                    }
                }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .imePadding()
                .padding(
                    horizontal = 12.dp,
                    vertical = 8.dp
                )
        ) {
            Text(
                text = "مطالعهٔ نقش‌ها",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(
                    horizontal = 4.dp,
                    vertical = 6.dp
                )
            )

            if (roleGroups.isEmpty()) {
                Text(
                    text = "برای این گفتگو متنی برای مطالعه ثبت نشده است.",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(16.dp)
                )
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(vertical = 8.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    items(
                        items = roleGroups,
                        key = { it.roleId }
                    ) { group ->

                        val roleIndex = roleGroups.indexOfFirst {
                            it.roleId == group.roleId
                        }

                        val query = queryByRole[group.roleId].orEmpty()

                        val activeMatch =
                            activeMatchByRole[group.roleId] ?: 0

                        RoleStudyCard(
                            group = group,
                            colorIndex = roleIndex,
                            query = query,
                            activeMatchIndex = activeMatch,
                            expanded = expandedRoleId == group.roleId,
                            onQueryChange = { value ->
                                queryByRole[group.roleId] = value
                                activeMatchByRole[group.roleId] = 0
                            },
                            onNavigateMatch = { direction, matchCount ->
                                if (matchCount > 0) {
                                    val current = (
                                        activeMatchByRole[group.roleId] ?: 0
                                    ).coerceIn(0, matchCount - 1)

                                    activeMatchByRole[group.roleId] =
                                        (current + direction).mod(matchCount)
                                }
                            },
                            onExpand = {
                                expandedRoleId = group.roleId
                            },
                            onCollapse = {
                                expandedRoleId = null
                            }
                        )
                    }
                }
            }
        }
    }

    if (showOrderEditor && !readOnly) {
        TurnOrderEditorDialog(
            turns = orderedTurns,
            onMove = onMoveTurn,
            onDelete = onDeleteTurn,
            onDismiss = {
                showOrderEditor = false
            }
        )
    }
}

@Composable
private fun RoleStudyCard(
    group: RoleStudyGroup,
    colorIndex: Int,
    query: String,
    activeMatchIndex: Int,
    expanded: Boolean,
    onQueryChange: (String) -> Unit,
    onNavigateMatch: (direction: Int, matchCount: Int) -> Unit,
    onExpand: () -> Unit,
    onCollapse: () -> Unit
) {
    val cardColors = listOf(
        MaterialTheme.colorScheme.primaryContainer,
        MaterialTheme.colorScheme.secondaryContainer,
        MaterialTheme.colorScheme.tertiaryContainer,
        MaterialTheme.colorScheme.surfaceVariant
    )

    val titleColors = listOf(
        MaterialTheme.colorScheme.onPrimaryContainer,
        MaterialTheme.colorScheme.onSecondaryContainer,
        MaterialTheme.colorScheme.onTertiaryContainer,
        MaterialTheme.colorScheme.onSurfaceVariant
    )

    val cardColor = cardColors[colorIndex % cardColors.size]
    val titleColor = titleColors[colorIndex % titleColors.size]

    if (!expanded) {
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(18.dp),
            colors = CardDefaults.cardColors(
                containerColor = cardColor
            )
        ) {
            RoleStudyCardBody(
                group = group,
                titleColor = titleColor,
                query = query,
                activeMatchIndex = activeMatchIndex,
                fullscreen = false,
                onQueryChange = onQueryChange,
                onNavigateMatch = onNavigateMatch,
                onExpand = onExpand
            )
        }
    }

    if (expanded) {
        Dialog(
            onDismissRequest = onCollapse,
            properties = DialogProperties(
                usePlatformDefaultWidth = false
            )
        ) {
            Surface(
                modifier = Modifier.fillMaxSize(),
                shape = RoundedCornerShape(0.dp),
                color = cardColor
            ) {
                RoleStudyCardBody(
                    group = group,
                    titleColor = titleColor,
                    query = query,
                    activeMatchIndex = activeMatchIndex,
                    fullscreen = true,
                    onQueryChange = onQueryChange,
                    onNavigateMatch = onNavigateMatch,
                    onExpand = onCollapse
                )
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun RoleStudyCardBody(
    group: RoleStudyGroup,
    titleColor: Color,
    query: String,
    activeMatchIndex: Int,
    fullscreen: Boolean,
    onQueryChange: (String) -> Unit,
    onNavigateMatch: (direction: Int, matchCount: Int) -> Unit,
    onExpand: () -> Unit
) {
    val matches = remember(group.numberedTurns, query) {
        findRoleSearchMatches(
            numberedTurns = group.numberedTurns,
            query = query
        )
    }

    val safeActiveIndex = if (matches.isEmpty()) {
        0
    } else {
        activeMatchIndex.coerceIn(0, matches.lastIndex)
    }

    val currentMatch = matches.getOrNull(safeActiveIndex)
    val turnListState = rememberLazyListState()

    val density = LocalDensity.current
    val keyboardVisible = WindowInsets.ime.getBottom(density) > 0

    LaunchedEffect(
        currentMatch?.turnId,
        currentMatch?.start,
        currentMatch?.end,
        safeActiveIndex,
        group.numberedTurns
    ) {
        val targetIndex = currentMatch?.let { match ->
            group.numberedTurns.indexOfFirst {
                it.second.turnId == match.turnId
            }
        } ?: -1

        if (targetIndex >= 0) {
            turnListState.animateScrollToItem(
                index = targetIndex,
                scrollOffset = 0
            )
        }
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .then(
                if (fullscreen) Modifier.fillMaxSize()
                else Modifier
            )
            .imePadding()
            .padding(
                if (fullscreen) 16.dp else 12.dp
            ),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(
                modifier = Modifier.weight(1f)
            ) {
                Text(
                    text = group.roleTitle,
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                    color = titleColor
                )

                Text(
                    text = buildString {
                        if (group.versionLabel.isNotBlank()) {
                            append("منبع: ${group.versionLabel} · ")
                        }

                        append("${group.numberedTurns.size} بخش انتخاب‌شده")
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            TextButton(onClick = onExpand) {
                Text(
                    if (fullscreen) "بازگشت به کارت"
                    else "تمام‌صفحه"
                )
            }
        }

        OutlinedTextField(
            value = query,
            onValueChange = onQueryChange,
            modifier = Modifier.fillMaxWidth(),
            singleLine = true,
            label = {
                Text("جستجو در متن نقش ${group.roleTitle}")
            }
        )

        if (query.isNotBlank()) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = if (matches.isEmpty()) {
                        "نتیجه‌ای پیدا نشد."
                    } else {
                        "نتیجه ${safeActiveIndex + 1} از ${matches.size}"
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                if (matches.isNotEmpty()) {
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        TextButton(
                            onClick = {
                                onNavigateMatch(-1, matches.size)
                            }
                        ) {
                            Text("قبلی")
                        }

                        TextButton(
                            onClick = {
                                onNavigateMatch(1, matches.size)
                            }
                        ) {
                            Text("بعدی")
                        }
                    }
                }
            }
        }

        LazyColumn(
            state = turnListState,
            modifier = if (fullscreen) {
                Modifier
                    .weight(1f)
                    .fillMaxWidth()
            } else {
                Modifier
                    .fillMaxWidth()
                    .heightIn(
                        max = if (keyboardVisible) 180.dp else 480.dp
                    )
            },
            contentPadding = PaddingValues(bottom = 8.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            items(
                items = group.numberedTurns,
                key = { it.second.turnId }
            ) { (sequenceNumber, turn) ->

                val turnMatches = remember(matches, turn.turnId) {
                    matches.filter {
                        it.turnId == turn.turnId
                    }
                }

                val activeForTurn = currentMatch?.takeIf {
                    it.turnId == turn.turnId
                }

                val textScrollState = rememberScrollState()

                var textLayoutResult by remember(turn.turnId) {
                    mutableStateOf<TextLayoutResult?>(null)
                }

                LaunchedEffect(
                    activeForTurn?.turnId,
                    activeForTurn?.start,
                    activeForTurn?.end,
                    textLayoutResult
                ) {
                    val match = activeForTurn
                    val layout = textLayoutResult

                    if (match != null && layout != null) {
                        yield()

                        val safeOffset = match.start.coerceIn(
                            0,
                            turn.content.length
                        )

                        val lineIndex = layout.getLineForOffset(
                            safeOffset
                        )

                        val lineTop = layout.getLineTop(lineIndex)

                        val targetScroll = with(density) {
                            (
                                lineTop - 24.dp.toPx()
                            ).coerceAtLeast(0f).roundToInt()
                        }

                        textScrollState.animateScrollTo(targetScroll)
                    } else if (match == null) {
                        textScrollState.animateScrollTo(0)
                    }
                }

                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .then(
                            if (fullscreen) {
                                Modifier.fillParentMaxHeight()
                            } else {
                                Modifier
                            }
                        ),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surface
                    ),
                    shape = RoundedCornerShape(14.dp)
                ) {
                    Column(
                        modifier = if (fullscreen) {
                            Modifier
                                .fillMaxSize()
                                .padding(12.dp)
                        } else {
                            Modifier.padding(12.dp)
                        },
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Text(
                            text = "نوبت $sequenceNumber · ${turn.sectionTitle}",
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Bold
                        )

                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .then(
                                    if (fullscreen) {
                                        Modifier.weight(1f)
                                    } else {
                                        Modifier.height(220.dp)
                                    }
                                )
                                .verticalScroll(textScrollState)
                        ) {
                            Text(
                                text = buildHighlightedText(
                                    text = turn.content,
                                    matches = turnMatches,
                                    activeMatch = activeForTurn,
                                    normalHighlight = MaterialTheme.colorScheme.tertiaryContainer,
                                    activeHighlight = MaterialTheme.colorScheme.primaryContainer,
                                    activeTextColor = MaterialTheme.colorScheme.onPrimaryContainer
                                ),
                                onTextLayout = { layoutResult ->
                                    textLayoutResult = layoutResult
                                },
                                style = MaterialTheme.typography.bodyLarge
                            )
                        }
                    }
                }
            }
        }
    }
}

private fun findRoleSearchMatches(
    numberedTurns: List<Pair<Int, DialogueTurnDisplay>>,
    query: String
): List<RoleSearchMatch> {
    val needle = normalizeDialogueSearchText(query.trim())

    if (needle.isBlank()) {
        return emptyList()
    }

    val results = mutableListOf<RoleSearchMatch>()

    numberedTurns.forEach { (_, turn) ->
        val normalizedContent = normalizeDialogueSearchText(turn.content)
        var startAt = 0

        while (startAt <= normalizedContent.length - needle.length) {
            val index = normalizedContent.indexOf(
                string = needle,
                startIndex = startAt
            )

            if (index < 0) {
                break
            }

            results += RoleSearchMatch(
                turnId = turn.turnId,
                start = index,
                end = index + needle.length
            )

            startAt = index + needle.length.coerceAtLeast(1)
        }
    }

    return results
}

private fun normalizeDialogueSearchText(
    value: String
): String = buildString(value.length) {
    value.forEach { character ->
        append(
            when (character) {
                'ي', 'ى' -> 'ی'
                'ك' -> 'ک'
                else -> character.lowercaseChar()
            }
        )
    }
}

private fun buildHighlightedText(
    text: String,
    matches: List<RoleSearchMatch>,
    activeMatch: RoleSearchMatch?,
    normalHighlight: Color,
    activeHighlight: Color,
    activeTextColor: Color
): AnnotatedString = buildAnnotatedString {
    var cursor = 0

    matches.sortedBy { it.start }.forEach { match ->
        if (
            match.start < cursor ||
            match.start !in text.indices ||
            match.end > text.length
        ) {
            return@forEach
        }

        append(text.substring(cursor, match.start))

        val isActive = activeMatch?.let {
            it.start == match.start && it.end == match.end
        } == true

        withStyle(
            SpanStyle(
                background = if (isActive) {
                    activeHighlight
                } else {
                    normalHighlight
                },
                color = if (isActive) {
                    activeTextColor
                } else {
                    Color.Unspecified
                },
                fontWeight = if (isActive) {
                    FontWeight.Bold
                } else {
                    FontWeight.Normal
                }
            )
        ) {
            append(text.substring(match.start, match.end))
        }

        cursor = match.end
    }

    if (cursor < text.length) {
        append(text.substring(cursor))
    }
}

@Composable
private fun TurnOrderEditorDialog(
    turns: List<DialogueTurnDisplay>,
    onMove: (index: Int, direction: Int) -> Unit,
    onDelete: (DialogueTurnDisplay) -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text("تنظیم ترتیب متن‌ها")
        },
        text = {
            Column(
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text(
                    "ترتیب زیر، ترتیب نمایش و مطالعهٔ نوبت‌هاست و متن اصلی آرشیو را تغییر نمی‌دهد.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                LazyColumn(
                    modifier = Modifier.heightIn(max = 520.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    items(
                        items = turns,
                        key = { it.turnId }
                    ) { turn ->

                        val index = turns.indexOfFirst {
                            it.turnId == turn.turnId
                        }

                        Card(
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(8.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column(
                                    modifier = Modifier.weight(1f)
                                ) {
                                    Text(
                                        "${index + 1}. ${turn.roleTitle}",
                                        fontWeight = FontWeight.Bold
                                    )

                                    Text(
                                        turn.sectionTitle,
                                        style = MaterialTheme.typography.bodySmall
                                    )
                                }

                                Column(
                                    horizontalAlignment = Alignment.CenterHorizontally
                                ) {
                                    TextButton(
                                        enabled = index > 0,
                                        onClick = {
                                            onMove(index, -1)
                                        }
                                    ) {
                                        Text("بالا")
                                    }

                                    TextButton(
                                        enabled = index < turns.lastIndex,
                                        onClick = {
                                            onMove(index, 1)
                                        }
                                    ) {
                                        Text("پایین")
                                    }
                                }

                                TextButton(
                                    onClick = {
                                        onDelete(turn)
                                    }
                                ) {
                                    Text("حذف")
                                }
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            Button(onClick = onDismiss) {
                Text("پایان")
            }
        }
    )
}
