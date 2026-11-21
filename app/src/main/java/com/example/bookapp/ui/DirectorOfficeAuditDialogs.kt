package com.example.bookapp.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.bookapp.data.DirectorChangeHistoryEntity
import com.example.bookapp.data.DirectorOfficeHistorySnapshots
import com.example.bookapp.data.DirectorOfficeQualityIssue
import com.example.bookapp.data.DirectorOfficeQualitySeverity
import com.example.bookapp.data.DirectorTurnEntity
import java.text.DateFormat
import java.util.Date

@Composable
fun DirectorOfficeQualityDialog(
    issues: List<DirectorOfficeQualityIssue>,
    onDismiss: () -> Unit,
    onSelectIssue: (DirectorOfficeQualityIssue) -> Unit
) {
    val errors = issues.count { it.severity == DirectorOfficeQualitySeverity.ERROR }
    val warnings = issues.count { it.severity == DirectorOfficeQualitySeverity.WARNING }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("گزارش کنترل صحت و کامل‌بودن دفتر") },
        text = {
            Column(
                modifier = Modifier
                    .heightIn(max = (LocalConfiguration.current.screenHeightDp * 0.62f).dp)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text("خطا: $errors   •   هشدار: $warnings   •   اطلاعات: ${issues.size - errors - warnings}")
                if (errors > 0 || warnings > 0) {
                    Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer)) {
                        Text(
                            "دفتر هنوز کامل یا بدون اشکال نیست. همه موارد شناسایی‌شده در ادامه فهرست شده‌اند؛ تا رفع خطاها و هشدارهای کامل‌بودن، دفتر تأیید نمی‌شود.",
                            modifier = Modifier.padding(12.dp),
                            color = MaterialTheme.colorScheme.onErrorContainer
                        )
                    }
                }
                if (issues.isEmpty()) {
                    Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer)) {
                        Text(
                            "هیچ‌یک از موارد قابل‌کنترل در این نسخه پیدا نشد. این نتیجه فقط بر اساس اطلاعات موجود در دفتر و فهرست نقش‌های قابل‌دسترسی است.",
                            modifier = Modifier.padding(12.dp),
                            color = MaterialTheme.colorScheme.onPrimaryContainer
                        )
                    }
                } else {
                    issues.forEach { issue ->
                        val tint = when (issue.severity) {
                            DirectorOfficeQualitySeverity.ERROR -> MaterialTheme.colorScheme.error
                            DirectorOfficeQualitySeverity.WARNING -> MaterialTheme.colorScheme.tertiary
                            DirectorOfficeQualitySeverity.INFO -> MaterialTheme.colorScheme.primary
                        }
                        Card(
                            modifier = Modifier.fillMaxWidth(),
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
                        ) {
                            Column(Modifier.padding(10.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                Text(
                                    when (issue.severity) {
                                        DirectorOfficeQualitySeverity.ERROR -> "خطا"
                                        DirectorOfficeQualitySeverity.WARNING -> "هشدار"
                                        DirectorOfficeQualitySeverity.INFO -> "اطلاعات"
                                    },
                                    color = tint,
                                    fontWeight = FontWeight.Bold
                                )
                                Text(issue.message, style = MaterialTheme.typography.bodyMedium)
                                if (issue.turnId != null) {
                                    TextButton(onClick = { onSelectIssue(issue) }) { Text("رفتن به نوبت ${issue.turnNumber ?: "موردنظر"}") }
                                }
                            }
                        }
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("بستن") } }
    )
}

@Composable
fun DirectorOfficeChangeHistoryDialog(
    turn: DirectorTurnEntity,
    entries: List<DirectorChangeHistoryEntity>,
    canRestore: Boolean,
    restoring: Boolean,
    onDismiss: () -> Unit,
    onRestore: (DirectorChangeHistoryEntity) -> Unit
) {
    var restoreCandidate by remember(entries) { mutableStateOf<DirectorChangeHistoryEntity?>(null) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("سابقه تغییرات نوبت ${turn.turnNumber}") },
        text = {
            Column(
                modifier = Modifier
                    .heightIn(max = (LocalConfiguration.current.screenHeightDp * 0.66f).dp)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                if (entries.isEmpty()) {
                    Text("برای این نوبت هنوز تغییری پس از ساخت دفتر ثبت نشده است.")
                }
                entries.forEach { entry ->
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
                    ) {
                        Column(Modifier.padding(10.dp), verticalArrangement = Arrangement.spacedBy(5.dp)) {
                            Text(entry.summary, fontWeight = FontWeight.SemiBold)
                            Text(
                                "${historyTypeLabel(entry)} • ${DateFormat.getDateTimeInstance(DateFormat.SHORT, DateFormat.SHORT).format(Date(entry.changedAt))}",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Text("قبل از تغییر", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.secondary)
                            HistoryValueBox(historyPreview(entry, entry.oldValue, turn.id))
                            Text("بعد از تغییر", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.secondary)
                            HistoryValueBox(historyPreview(entry, entry.newValue, turn.id))
                            if (canRestore && entry.turnId != null && entry.entityType in setOf("DIALOGUE", "ROLE", "TURN")) {
                                OutlinedButton(
                                    enabled = !restoring,
                                    onClick = { restoreCandidate = entry }
                                ) { Text("بازگردانی وضعیت قبل") }
                            }
                        }
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("بستن") } }
    )
    restoreCandidate?.let { candidate ->
        AlertDialog(
            onDismissRequest = { restoreCandidate = null },
            title = { Text("بازگردانی تغییر") },
            text = { Text("وضعیت قبل از این تغییر برای نوبت ${turn.turnNumber} جایگزین وضعیت فعلی شود؟ یک رویداد جدید نیز در تاریخچه ثبت خواهد شد.") },
            confirmButton = {
                TextButton(enabled = !restoring, onClick = {
                    onRestore(candidate)
                    restoreCandidate = null
                }) { Text(if (restoring) "در حال بازگردانی…" else "بازگردانی") }
            },
            dismissButton = { TextButton(onClick = { restoreCandidate = null }) { Text("انصراف") } }
        )
    }
}

@Composable
private fun HistoryValueBox(value: String) {
    Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
        Text(
            value.ifBlank { "— خالی —" },
            modifier = Modifier.fillMaxWidth().padding(8.dp),
            style = MaterialTheme.typography.bodySmall
        )
    }
}

private fun historyTypeLabel(entry: DirectorChangeHistoryEntity): String = when (entry.entityType) {
    "DIALOGUE" -> "متن دیالوگ"
    "ROLE" -> "نقش‌ها"
    "TURN" -> if (entry.fieldName == "turnNumber") "شماره نوبت" else "عنوان نوبت"
    else -> "تغییر دفتر"
} + when (entry.changeType) {
    "ADD" -> " • افزودن"
    "REMOVE" -> " • حذف"
    "RESTORE" -> " • بازگردانی"
    else -> " • ویرایش"
}

private fun historyPreview(entry: DirectorChangeHistoryEntity, value: String, turnId: Long): String = when (entry.entityType) {
    "DIALOGUE" -> DirectorOfficeHistorySnapshots.decodeSegments(value, turnId)
        .joinToString("\n") { it.text }.ifBlank { "متنی ثبت نشده بود." }
    "ROLE" -> DirectorOfficeHistorySnapshots.decodeParticipants(value, turnId)
        .sortedBy { it.orderIndex }
        .joinToString("، ") { (if (it.isLead) "★ " else "") + it.roleTitle }
        .ifBlank { "نقشی ثبت نشده بود." }
    "TURN" -> value
    else -> value
}
