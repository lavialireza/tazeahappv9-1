package com.example.bookapp.ui

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.example.bookapp.data.DirectorStageCueEntity

/**
 * فرم مشترک ویرایش مشخصات نوبت برای Admin و Viewer.
 * این Composable فقط UI را مدیریت می‌کند؛ کنترل مجوز و ذخیره‌سازی در فراخواننده باقی می‌ماند.
 */
@Composable
fun DirectorOfficeTurnDetailsDialog(
    title: String,
    turnNumber: String,
    onTurnNumberChange: (String) -> Unit,
    turnTitle: String,
    onTurnTitleChange: (String) -> Unit,
    onDismiss: () -> Unit,
    onConfirm: () -> Unit,
    confirmLabel: String = "ذخیره",
    dismissLabel: String = "انصراف",
    confirmEnabled: Boolean = turnNumber.toIntOrNull()?.let { it > 0 } == true && turnTitle.isNotBlank()
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            Column(
                Modifier.heightIn(max = 420.dp).verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                androidx.compose.material3.OutlinedTextField(
                    value = turnNumber,
                    onValueChange = { onTurnNumberChange(it.filter(Char::isDigit).take(6)) },
                    label = { Text("شماره نوبت") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true
                )
                androidx.compose.material3.OutlinedTextField(
                    value = turnTitle,
                    onValueChange = onTurnTitleChange,
                    label = { Text("عنوان نقش / نوبت") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true
                )
            }
        },
        confirmButton = { TextButton(onClick = onConfirm, enabled = confirmEnabled) { Text(confirmLabel) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text(dismissLabel) } }
    )
}

/**
 * فرم واحد افزودن/ویرایش قطعه دیالوگ.
 * callback ذخیره به فراخواننده سپرده شده تا مسیر دیتابیس و سیاست دسترسی Flavor حفظ شود.
 */
@Composable
fun DirectorOfficeDialogueEditorDialog(
    title: String,
    value: String,
    onValueChange: (String) -> Unit,
    onDismiss: () -> Unit,
    onConfirm: () -> Unit,
    confirmLabel: String = "ذخیره",
    dismissLabel: String = "انصراف",
    confirmEnabled: Boolean = value.isNotBlank(),
    fieldLabel: String = "متن دیالوگ"
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            Column(Modifier.heightIn(max = 480.dp).verticalScroll(rememberScrollState())) {
                DirectorOfficeDialogueTextField(
                    value = value,
                    onValueChange = onValueChange,
                    label = fieldLabel,
                    minHeight = 180,
                    maxHeight = 360,
                    minLines = 6
                )
            }
        },
        confirmButton = { TextButton(onClick = onConfirm, enabled = confirmEnabled) { Text(confirmLabel) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text(dismissLabel) } }
    )
}

/**
 * ویرایشگر مشترک راهنمای صحنه.
 * Admin می‌تواند فقط متن راهنما را نمایش دهد؛ Viewer عنوان و نوع راهنما را نیز ویرایش می‌کند.
 * تصمیم مجاز بودن عملیات و persistence همواره بیرون از این UI انجام می‌شود.
 */
@Composable
fun DirectorOfficeStageCueEditorDialog(
    title: String,
    instruction: String,
    onInstructionChange: (String) -> Unit,
    onDismiss: () -> Unit,
    onConfirm: () -> Unit,
    cueTitle: String? = null,
    onCueTitleChange: ((String) -> Unit)? = null,
    cueType: String? = null,
    onCueTypeChange: ((String) -> Unit)? = null,
    instructionLabel: String = "توضیحات صحنه",
    instructionMinHeight: Int = 120,
    instructionMaxHeight: Int = 240,
    confirmLabel: String = "ذخیره",
    dismissLabel: String = "انصراف",
    confirmEnabled: Boolean = instruction.isNotBlank()
) {
    val cueTypes = listOf(
        DirectorStageCueEntity.CUE_OTHER to "سایر",
        DirectorStageCueEntity.CUE_MOVE to "حرکت",
        DirectorStageCueEntity.CUE_MUSIC to "موسیقی",
        DirectorStageCueEntity.CUE_LIGHT to "نور",
        DirectorStageCueEntity.CUE_PAUSE to "مکث",
        DirectorStageCueEntity.CUE_IMMEDIATE to "بلافاصله"
    )
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            Column(
                Modifier.heightIn(max = 480.dp).verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                DirectorOfficeStageCueInputFields(
                    title = cueTitle,
                    onTitleChange = onCueTitleChange,
                    instruction = instruction,
                    onInstructionChange = onInstructionChange,
                    instructionLabel = instructionLabel,
                    instructionMinHeight = instructionMinHeight,
                    instructionMaxHeight = instructionMaxHeight
                )
                if (cueType != null && onCueTypeChange != null) {
                    Row(
                        Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        cueTypes.forEach { (key, label) ->
                            FilterChip(
                                selected = cueType == key,
                                onClick = { onCueTypeChange(key) },
                                label = { Text(label) }
                            )
                        }
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = onConfirm, enabled = confirmEnabled) { Text(confirmLabel) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text(dismissLabel) } }
    )
}
