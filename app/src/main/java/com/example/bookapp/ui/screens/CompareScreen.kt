
package com.example.bookapp.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.bookapp.data.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

data class CompareSectionItem(
    val section: SectionEntity,
    val roleTitle: String
)

private data class ConversationRoleText(
    val roleId: Long,
    val title: String,
    val text: String
)

private data class CompareRoleSearchMatch(
    val start: Int,
    val end: Int
)

private fun normalizeCompareSearchText(value: String): String {
    return buildString(value.length) {
        value.forEach { character ->
            append(
                when (character) {
                    'ي', 'ى' -> 'ی'
                    'ك' -> 'ک'
                    'ؤ' -> 'و'
                    'إ', 'أ', 'ٱ' -> 'ا'
                    else -> character
                }
            )
        }
    }.lowercase()
}

private fun findCompareRoleSearchMatches(
    text: String,
    query: String
): List<CompareRoleSearchMatch> {
    if (query.isBlank()) return emptyList()

    val normalizedText = normalizeCompareSearchText(text)
    val normalizedQuery = normalizeCompareSearchText(query)

    if (normalizedQuery.isBlank()) return emptyList()

    val results = mutableListOf<CompareRoleSearchMatch>()
    var startIndex = 0

    while (startIndex <= normalizedText.length - normalizedQuery.length) {
        val foundIndex = normalizedText.indexOf(
            normalizedQuery,
            startIndex
        )

        if (foundIndex < 0) break

        results.add(
            CompareRoleSearchMatch(
                start = foundIndex,
                end = foundIndex + normalizedQuery.length
            )
        )

        startIndex = foundIndex + normalizedQuery.length
    }

    return results
}

private fun buildCompareHighlightedText(
    text: String,
    matches: List<CompareRoleSearchMatch>,
    selectedIndex: Int
): AnnotatedString {
    return buildAnnotatedString {
        var currentPosition = 0

        matches.forEachIndexed { index, match ->
            if (
                match.start < currentPosition ||
                match.start >= text.length ||
                match.end > text.length
            ) {
                return@forEachIndexed
            }

            append(text.substring(currentPosition, match.start))

            withStyle(
                SpanStyle(
                    background = if (index == selectedIndex) {
                        androidx.compose.ui.graphics.Color(0xFFFFB74D)
                    } else {
                        androidx.compose.ui.graphics.Color(0xFFFFF176)
                    },
                    color = androidx.compose.ui.graphics.Color.Black,
                    fontWeight = FontWeight.Bold
                )
            ) {
                append(text.substring(match.start, match.end))
            }

            currentPosition = match.end
        }

        if (currentPosition < text.length) {
            append(text.substring(currentPosition))
        }
    }
}

@Composable
private fun CompareRoleMultiDropdown(
    roles: List<RoleEntity>,
    selectedRoles: List<RoleEntity>,
    onToggle: (RoleEntity) -> Unit
) {
    var expanded by remember { mutableStateOf(false) }

    Column(
        Modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Text(
            "انتخاب نقش‌ها",
            style = MaterialTheme.typography.titleMedium
        )

        Box {
            OutlinedButton(
                onClick = { expanded = true },
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(
                    text = if (selectedRoles.isEmpty()) {
                        "انتخاب نقش‌ها"
                    } else {
                        selectedRoles.joinToString("، ") { it.title }
                    },
                    modifier = Modifier.fillMaxWidth(),
                    maxLines = 3
                )
            }

            DropdownMenu(
                expanded = expanded,
                onDismissRequest = { expanded = false }
            ) {
                roles.forEach { role ->
                    val checked = selectedRoles.any {
                        it.id == role.id
                    }

                    DropdownMenuItem(
                        text = {
                            Row(
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Checkbox(
                                    checked = checked,
                                    onCheckedChange = null
                                )
                                Spacer(Modifier.width(8.dp))
                                Text(role.title)
                            }
                        },
                        onClick = {
                            onToggle(role)
                        }
                    )
                }
            }
        }

        Text(
            "تعداد نقش‌های انتخاب‌شده: ${selectedRoles.size}",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )

        Text(
            "ترتیب انتخاب نقش‌ها، ترتیب نمایش آن‌ها خواهد بود.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
private fun RoleStudyCard(
    item: ConversationRoleText,
    fontSize: Float,
    cardColor: androidx.compose.ui.graphics.Color,
    titleColor: androidx.compose.ui.graphics.Color,
    fullscreen: Boolean,
    onToggleFullscreen: () -> Unit
) {
    var searchQuery by remember(item.roleId) {
        mutableStateOf("")
    }

    var selectedMatchIndex by remember(item.roleId) {
        mutableIntStateOf(0)
    }

    val matches = remember(item.text, searchQuery) {
        findCompareRoleSearchMatches(item.text, searchQuery)
    }

    val scrollState = rememberScrollState()
    val scope = rememberCoroutineScope()

    var textLayoutResult by remember {
        mutableStateOf<TextLayoutResult?>(null)
    }

    val highlightedText = remember(
        item.text,
        matches,
        selectedMatchIndex
    ) {
        buildCompareHighlightedText(
            text = item.text,
            matches = matches,
            selectedIndex = selectedMatchIndex
        )
    }

    LaunchedEffect(
        selectedMatchIndex,
        matches,
        textLayoutResult
    ) {
        if (matches.isEmpty()) return@LaunchedEffect

        val selectedMatch = matches.getOrNull(
            selectedMatchIndex
        ) ?: return@LaunchedEffect

        val layout = textLayoutResult
            ?: return@LaunchedEffect

        val line = layout.getLineForOffset(
            selectedMatch.start.coerceIn(
                0,
                item.text.length
            )
        )

        val targetPosition = layout
            .getLineTop(line)
            .toInt()
            .coerceAtLeast(0)

        scrollState.animateScrollTo(targetPosition)
    }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .then(
                if (fullscreen) {
                    Modifier.fillMaxHeight()
                } else {
                    Modifier.height(320.dp)
                }
            ),
        colors = CardDefaults.cardColors(
            containerColor = cardColor
        )
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(
                    horizontal = 10.dp,
                    vertical = 8.dp
                )
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    item.title,
                    style = MaterialTheme.typography.titleMedium,
                    color = titleColor,
                    modifier = Modifier.weight(1f)
                )

                TextButton(
                    onClick = onToggleFullscreen
                ) {
                    Text(
                        if (fullscreen) "بازگشت" else "تمام‌صفحه"
                    )
                }
            }

            Divider(
                modifier = Modifier.padding(vertical = 5.dp),
                color = titleColor.copy(alpha = 0.25f)
            )

            OutlinedTextField(
                value = searchQuery,
                onValueChange = {
                    searchQuery = it
                    selectedMatchIndex = 0
                    scope.launch {
                        scrollState.scrollTo(0)
                    }
                },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                label = {
                    Text("جستجو در متن این نقش")
                },
                placeholder = {
                    Text("عبارت موردنظر را وارد کنید")
                },
                keyboardOptions = KeyboardOptions(
                    imeAction = ImeAction.Search
                )
            )

            if (searchQuery.isNotBlank()) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 2.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        if (matches.isEmpty()) {
                            "عبارتی پیدا نشد"
                        } else {
                            "${selectedMatchIndex + 1} از ${matches.size} نتیجه"
                        },
                        style = MaterialTheme.typography.bodySmall,
                        color = titleColor
                    )

                    Row(
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        TextButton(
                            enabled = matches.isNotEmpty(),
                            onClick = {
                                selectedMatchIndex =
                                    if (selectedMatchIndex <= 0) {
                                        matches.lastIndex
                                    } else {
                                        selectedMatchIndex - 1
                                    }
                            }
                        ) {
                            Text("قبلی")
                        }

                        TextButton(
                            enabled = matches.isNotEmpty(),
                            onClick = {
                                selectedMatchIndex =
                                    if (selectedMatchIndex >= matches.lastIndex) {
                                        0
                                    } else {
                                        selectedMatchIndex + 1
                                    }
                            }
                        ) {
                            Text("بعدی")
                        }
                    }
                }
            } else {
                Spacer(Modifier.height(5.dp))
            }

            Divider(
                color = titleColor.copy(alpha = 0.2f)
            )

            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .verticalScroll(scrollState)
                    .padding(
                        horizontal = 4.dp,
                        vertical = 8.dp
                    )
            ) {
                Text(
                    text = highlightedText.ifEmpty {
                        AnnotatedString(
                            "برای این نقش متنی ثبت نشده است."
                        )
                    },
                    fontSize = fontSize.sp,
                    lineHeight = (fontSize * 1.65f).sp,
                    onTextLayout = {
                        textLayoutResult = it
                    }
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CompareScreen(
    taziehTitle: String,
    roles: List<RoleEntity>,
    sections: List<CompareSectionItem>,
    roleSections: Map<Long, List<SectionEntity>>,
    onBack: () -> Unit
) {
    // sections برای سازگاری با AppNavigation فعلی حفظ شده است.

    var conversationRoles by remember(roles) {
        mutableStateOf(roles.take(2))
    }

    var conversationTexts by remember {
        mutableStateOf(emptyList<ConversationRoleText>())
    }

    var isPreparingConversation by remember {
        mutableStateOf(false)
    }

    var isShowingConversation by remember {
        mutableStateOf(false)
    }

    var fullscreenRoleId by remember {
        mutableStateOf<Long?>(null)
    }

    var conversationFontSize by remember {
        mutableFloatStateOf(14f)
    }

    LaunchedEffect(
        isShowingConversation,
        conversationRoles.map { it.id },
        roleSections
    ) {
        if (!isShowingConversation) {
            conversationTexts = emptyList()
            isPreparingConversation = false
            return@LaunchedEffect
        }

        isPreparingConversation = true

        try {
            val prepared = withContext(Dispatchers.Default) {
                conversationRoles.map { role ->
                    val text = roleSections[role.id]
                        .orEmpty()
                        .joinToString("\n\n") { section ->
                            "【${section.title}】\n${section.content}"
                        }

                    ConversationRoleText(
                        roleId = role.id,
                        title = role.title,
                        text = text
                    )
                }
            }

            conversationTexts = prepared
        } finally {
            isPreparingConversation = false
        }
    }

    val roleCardColors = listOf(
        MaterialTheme.colorScheme.primaryContainer,
        MaterialTheme.colorScheme.secondaryContainer,
        MaterialTheme.colorScheme.tertiaryContainer,
        MaterialTheme.colorScheme.surfaceVariant
    )

    val roleTitleColors = listOf(
        MaterialTheme.colorScheme.onPrimaryContainer,
        MaterialTheme.colorScheme.onSecondaryContainer,
        MaterialTheme.colorScheme.onTertiaryContainer,
        MaterialTheme.colorScheme.onSurfaceVariant
    )

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        if (fullscreenRoleId != null) {
                            "نمایش تمام‌صفحه نقش"
                        } else {
                            "نمایش هم‌زمان نقش‌ها — $taziehTitle"
                        }
                    )
                },
                navigationIcon = {
                    IconButton(
                        onClick = {
                            if (fullscreenRoleId != null) {
                                fullscreenRoleId = null
                            } else {
                                onBack()
                            }
                        }
                    ) {
                        Icon(
                            Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "بازگشت"
                        )
                    }
                }
            )
        }
    ) { padding ->

        Column(
            Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(16.dp)
        ) {
            if (!isShowingConversation) {
                Text(
                    "نمایش هم‌زمان مکالمه نقش‌ها",
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.padding(bottom = 8.dp)
                )

                CompareRoleMultiDropdown(
                    roles = roles,
                    selectedRoles = conversationRoles,
                    onToggle = { role ->
                        val exists = conversationRoles.any {
                            it.id == role.id
                        }

                        if (exists) {
                            conversationRoles =
                                conversationRoles.filterNot {
                                    it.id == role.id
                                }
                        } else {
                            // بدون محدودیت تعداد نقش
                            conversationRoles =
                                conversationRoles + role
                        }
                    }
                )

                Button(
                    onClick = {
                        isShowingConversation = true
                    },
                    enabled = conversationRoles.size >= 2,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 12.dp)
                ) {
                    Text("نمایش نقش‌های انتخاب‌شده")
                }

                if (roles.isEmpty()) {
                    Text(
                        "برای این تعزیه نقشی ثبت نشده است.",
                        color = MaterialTheme.colorScheme.error
                    )
                }

            } else if (isPreparingConversation) {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        CircularProgressIndicator()
                        Spacer(Modifier.height(12.dp))
                        Text("در حال آماده‌سازی متن نقش‌ها...")
                    }
                }

            } else {
                if (fullscreenRoleId == null) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        TextButton(
                            onClick = {
                                isShowingConversation = false
                            }
                        ) {
                            Text("تغییر انتخاب نقش‌ها")
                        }

                        Row(
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                "قلم",
                                style = MaterialTheme.typography.labelMedium
                            )

                            TextButton(
                                onClick = {
                                    conversationFontSize =
                                        (conversationFontSize - 1f)
                                            .coerceAtLeast(10f)
                                },
                                enabled = conversationFontSize > 10f
                            ) {
                                Text("−")
                            }

                            Text(
                                "${conversationFontSize.toInt()}",
                                style = MaterialTheme.typography.labelLarge
                            )

                            TextButton(
                                onClick = {
                                    conversationFontSize =
                                        (conversationFontSize + 1f)
                                            .coerceAtMost(22f)
                                },
                                enabled = conversationFontSize < 22f
                            ) {
                                Text("+")
                            }
                        }
                    }

                    Spacer(Modifier.height(6.dp))

                    LazyColumn(
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxWidth(),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        items(
                            items = conversationTexts,
                            key = { it.roleId }
                        ) { item ->

                            val index = conversationTexts.indexOfFirst {
                                it.roleId == item.roleId
                            }.coerceAtLeast(0)

                            RoleStudyCard(
                                item = item,
                                fontSize = conversationFontSize,
                                cardColor = roleCardColors[
                                    index % roleCardColors.size
                                ],
                                titleColor = roleTitleColors[
                                    index % roleTitleColors.size
                                ],
                                fullscreen = false,
                                onToggleFullscreen = {
                                    fullscreenRoleId = item.roleId
                                }
                            )
                        }
                    }

                } else {
                    val fullscreenIndex =
                        conversationTexts.indexOfFirst {
                            it.roleId == fullscreenRoleId
                        }

                    val fullscreenItem =
                        conversationTexts.getOrNull(fullscreenIndex)

                    if (fullscreenItem != null) {
                        RoleStudyCard(
                            item = fullscreenItem,
                            fontSize = conversationFontSize,
                            cardColor = roleCardColors[
                                fullscreenIndex % roleCardColors.size
                            ],
                            titleColor = roleTitleColors[
                                fullscreenIndex % roleTitleColors.size
                            ],
                            fullscreen = true,
                            onToggleFullscreen = {
                                fullscreenRoleId = null
                            }
                        )
                    }
                }
            }
        }
    }
}
