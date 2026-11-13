
package com.example.bookapp.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.MenuBook
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

data class ListItemData(
    val id: Long,
    val title: String,
    val subtitle: String? = null
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GenericListScreen(
    screenTitle: String,
    items: List<ListItemData>,
    onItemClick: (ListItemData) -> Unit,
    onBack: (() -> Unit)? = null,
    floatingAction: (@Composable () -> Unit)? = null,
    selectedIds: Set<Long> = emptySet(),
    onToggleSelect: ((ListItemData) -> Unit)? = null,
    topBarAction: (@Composable () -> Unit)? = null,
    snackbarHostState: SnackbarHostState? = null,
    colorfulCards: Boolean = false
) {
    Scaffold(
        containerColor = Color(0xFFF2F7EF),
        snackbarHost = {
            snackbarHostState?.let { SnackbarHost(it) }
        },
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = screenTitle,
                        color = Color(0xFF344C3A),
                        style = MaterialTheme.typography.titleLarge
                    )
                },
                navigationIcon = {
                    if (onBack != null) {
                        IconButton(onClick = onBack) {
                            Icon(
                                Icons.AutoMirrored.Filled.ArrowBack,
                                contentDescription = "بازگشت",
                                tint = Color(0xFF55765B)
                            )
                        }
                    }
                },
                actions = {
                    topBarAction?.invoke()
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = Color(0xFFF2F7EF)
                )
            )
        },
        floatingActionButton = {
            floatingAction?.invoke()
        }
    ) { padding ->

        if (items.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "موردی برای نمایش وجود ندارد",
                    color = Color(0xFF788579),
                    style = MaterialTheme.typography.bodyLarge
                )
            }
        } else {
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding),
                contentPadding = PaddingValues(
                    horizontal = 16.dp,
                    vertical = 14.dp
                ),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                itemsIndexed(
                    items = items,
                    key = { _, item -> item.id }
                ) { index, item ->
                    ListCard(
                        item = item,
                        index = index,
                        selected = item.id in selectedIds,
                        colorful = colorfulCards,
                        onClick = {
                            if (onToggleSelect != null) {
                                onToggleSelect(item)
                            } else {
                                onItemClick(item)
                            }
                        }
                    )
                }
            }
        }
    }
}

@Composable
private fun ListCard(
    item: ListItemData,
    index: Int,
    selected: Boolean = false,
    colorful: Boolean = false,
    onClick: () -> Unit
) {
    val cardBackground = when {
        selected -> Color(0xFFE0EEDB)
        !colorful -> Color(0xFFFFFEFA)
        else -> when (index % 4) {
            0 -> Color(0xFFE5F0DF)
            1 -> Color(0xFFE3EDF8)
            2 -> Color(0xFFF5EEDC)
            else -> Color(0xFFF5E5E8)
        }
    }

    val cardBorder = when {
        selected -> Color(0xFF8EAD86)
        !colorful -> Color(0xFFDCE8D8)
        else -> when (index % 4) {
            0 -> Color(0xFFBDD3B5)
            1 -> Color(0xFFB9CDE2)
            2 -> Color(0xFFE1D2AC)
            else -> Color(0xFFE0C3CA)
        }
    }

    val iconBackground = when {
        selected -> Color(0xFFCCDFC5)
        !colorful -> Color(0xFFE5EFE0)
        else -> when (index % 4) {
            0 -> Color(0xFFD0E3C7)
            1 -> Color(0xFFCFDEEF)
            2 -> Color(0xFFECE1C4)
            else -> Color(0xFFECD4DB)
        }
    }

    val iconTint = Color(0xFF55765B)

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onClick() },
        shape = RoundedCornerShape(18.dp),
        elevation = CardDefaults.cardElevation(
            defaultElevation = 2.dp
        ),
        border = BorderStroke(1.dp, cardBorder),
        colors = CardDefaults.cardColors(
            containerColor = cardBackground
        )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(
                    horizontal = 16.dp,
                    vertical = 15.dp
                ),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(46.dp)
                    .clip(RoundedCornerShape(14.dp))
                    .background(iconBackground),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    Icons.AutoMirrored.Filled.MenuBook,
                    contentDescription = null,
                    tint = iconTint
                )
            }

            Spacer(Modifier.width(14.dp))

            Column(
                modifier = Modifier.weight(1f)
            ) {
                Text(
                    text = item.title,
                    style = MaterialTheme.typography.titleMedium,
                    color = Color(0xFF344C3A)
                )

                if (!item.subtitle.isNullOrBlank()) {
                    Spacer(Modifier.height(4.dp))

                    Text(
                        text = item.subtitle,
                        style = MaterialTheme.typography.bodySmall,
                        color = Color(0xFF788579)
                    )
                }
            }

            Icon(
                Icons.AutoMirrored.Filled.ArrowBack,
                contentDescription = null,
                tint = Color(0xFF829681)
            )
        }
    }
}
