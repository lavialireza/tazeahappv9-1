package com.example.bookapp.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Forum
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.OutlinedTextField
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp

 data class DialogueSummary(val id: Long, val title: String, val turnsCount: Int)

@OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
@Composable
fun DialoguesScreen(
    taziehTitle: String,
    dialogues: List<DialogueSummary>,
    onOpenDialogue: (DialogueSummary) -> Unit,
    onEditDialogue: (DialogueSummary, String) -> Unit,
    onDeleteDialogue: (DialogueSummary) -> Unit,
    onCreateNew: () -> Unit,
    onAddVersion: (DialogueSummary) -> Unit = {},
    onBack: () -> Unit,
    readOnly: Boolean = false,
    canCreate: Boolean = !readOnly,
    canEdit: Boolean = !readOnly,
    canDelete: Boolean = !readOnly
) {
    var editTarget by remember { mutableStateOf<DialogueSummary?>(null) }
    var editTitle by remember { mutableStateOf("") }
    var deleteTarget by remember { mutableStateOf<DialogueSummary?>(null) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("گفتگوها: $taziehTitle") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "بازگشت")
                    }
                }
            )
        },
        floatingActionButton = {
            if (canCreate) {
                ExtendedFloatingActionButton(
                    text = { Text("گفتگوی جدید") },
                    icon = { Icon(Icons.Filled.Add, contentDescription = null) },
                    onClick = onCreateNew
                )
            }
        }
    ) { padding ->
        if (dialogues.isEmpty()) {
            Box(
                Modifier.fillMaxSize().padding(padding).padding(24.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    if (canCreate) {
                        "هنوز گفتگویی نساخته‌اید.\nاز دکمه «گفتگوی جدید» برای ساخت آن استفاده کنید."
                    } else {
                        "هنوز گفتگویی برای نمایش در این بخش وجود ندارد."
                    },
                    textAlign = TextAlign.Center
                )
            }
        } else {
            Column(
                modifier = Modifier.fillMaxSize().padding(padding)
            ) {
                Text(
                    "برای نمایش هر گفتگو، روی کارت آن یا آیکن چشم بزنید.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
                )
                LazyColumn(
                    Modifier.weight(1f).fillMaxWidth(),
                    contentPadding = PaddingValues(12.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                items(dialogues, key = { it.id }) { dialogue ->
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(14.dp),
                        onClick = { onOpenDialogue(dialogue) }
                    ) {
                        Row(
                            Modifier.padding(14.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(Icons.Filled.Forum, contentDescription = null)
                            Spacer(Modifier.width(10.dp))
                            Column(Modifier.weight(1f)) {
                                Text(dialogue.title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                                Text(
                                    "${dialogue.turnsCount} نوبت",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            if (canEdit) {
                                IconButton(onClick = { onAddVersion(dialogue) }) {
                                    Icon(
                                        Icons.Filled.Add,
                                        contentDescription = "افزودن نسخه یا متن به گفتگو"
                                    )
                                }
                            }
                            IconButton(onClick = { onOpenDialogue(dialogue) }) {
                                Icon(
                                    Icons.Filled.Visibility,
                                    contentDescription = "نمایش گفتگو"
                                )
                            }
                            if (canEdit) {
                                IconButton(onClick = {
                                    editTarget = dialogue
                                    editTitle = dialogue.title
                                }) {
                                    Icon(Icons.Filled.Edit, contentDescription = "ویرایش نام")
                                }
                            }
                            if (canDelete) {
                                IconButton(onClick = { deleteTarget = dialogue }) {
                                    Icon(Icons.Filled.Delete, contentDescription = "حذف گفتگو")
                                }
                            }
                        }
                    }
                }
                }
            }
        }
    }

    editTarget?.let { target ->
        AlertDialog(
            onDismissRequest = { editTarget = null },
            title = { Text("ویرایش نام گفتگو") },
            text = {
                OutlinedTextField(
                    value = editTitle,
                    onValueChange = { editTitle = it },
                    label = { Text("نام گفتگو") },
                    singleLine = true
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    val value = editTitle.trim()
                    if (value.isNotBlank() && canEdit) {
                        onEditDialogue(target, value)
                        editTarget = null
                    }
                }) { Text("ذخیره") }
            },
            dismissButton = {
                TextButton(onClick = { editTarget = null }) { Text("انصراف") }
            }
        )
    }

    deleteTarget?.let { target ->
        AlertDialog(
            onDismissRequest = { deleteTarget = null },
            title = { Text("حذف گفتگو") },
            text = {
                Text("آیا گفتگو «${target.title}» حذف شود؟ متن‌های اصلی تعزیه حذف نخواهند شد.")
            },
            confirmButton = {
                TextButton(onClick = {
                    if (canDelete) onDeleteDialogue(target)
                    deleteTarget = null
                }) { Text("حذف") }
            },
            dismissButton = {
                TextButton(onClick = { deleteTarget = null }) { Text("انصراف") }
            }
        )
    }
}
