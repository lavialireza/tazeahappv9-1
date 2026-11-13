package com.example.bookapp.ui.screens

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

@Composable
fun RehearsalNoteDialog(
    roleTitle: String,
    sectionTitle: String,
    onDismiss: () -> Unit,
    onSave: (title: String, content: String) -> Unit
) {
    var title by remember(roleTitle, sectionTitle) {
        mutableStateOf("یادداشت تمرین: $sectionTitle")
    }
    var content by remember(roleTitle, sectionTitle) { mutableStateOf("") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("یادداشت برای این بخش") },
        text = {
            Column {
                Text(
                    "نقش: $roleTitle  ·  بخش: $sectionTitle",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(Modifier.height(10.dp))
                OutlinedTextField(
                    value = title,
                    onValueChange = { title = it },
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text("عنوان یادداشت") },
                    singleLine = true
                )
                Spacer(Modifier.height(8.dp))
                OutlinedTextField(
                    value = content,
                    onValueChange = { content = it },
                    modifier = Modifier.fillMaxWidth().height(170.dp),
                    label = { Text("متن یادداشت") }
                )
            }
        },
        confirmButton = {
            TextButton(
                onClick = { onSave(title.ifBlank { "یادداشت تمرین: $sectionTitle" }, content) },
                enabled = title.isNotBlank() || content.isNotBlank()
            ) { Text("ذخیره در دفتر یادداشت") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("انصراف") }
        }
    )
}
