package com.example.bookapp.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.ui.Modifier
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Surface
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.PersonAdd
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.getValue
import androidx.compose.ui.unit.dp

/** اجزای رابط کاربری مشترک دفتر؛ هر دو Flavor باید از همین اجزا استفاده کنند. */
@Composable
fun DirectorOfficeSearchField(
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
    modifier: Modifier = Modifier,
    singleLine: Boolean = true,
    showSearchIcon: Boolean = true
) {
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        modifier = modifier.fillMaxWidth(),
        singleLine = singleLine,
        label = { Text(label) },
        leadingIcon = if (showSearchIcon) ({ Icon(Icons.Default.Search, contentDescription = null) }) else null,
        trailingIcon = {
            if (value.isNotBlank()) {
                IconButton(onClick = { onValueChange("") }) {
                    Icon(Icons.Default.Close, contentDescription = "پاک کردن جستجو")
                }
            }
        }
    )
}

@Composable
fun DirectorOfficeTurnInputFields(
    number: String,
    onNumberChange: (String) -> Unit,
    title: String? = null,
    onTitleChange: ((String) -> Unit)? = null,
    text: String,
    onTextChange: (String) -> Unit,
    numberLabel: String = "شماره نوبت",
    titleLabel: String = "عنوان نقش / نوبت",
    textLabel: String = "متن دیالوگ",
    textMinHeight: Int = 120,
    textMaxHeight: Int = 240
) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        OutlinedTextField(
            value = number,
            onValueChange = { onNumberChange(it.filter(Char::isDigit).take(6)) },
            label = { Text(numberLabel) },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true
        )
        if (title != null && onTitleChange != null) {
            OutlinedTextField(
                value = title,
                onValueChange = onTitleChange,
                label = { Text(titleLabel) },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true
            )
        }
        OutlinedTextField(
            value = text,
            onValueChange = onTextChange,
            label = { Text(textLabel) },
            modifier = Modifier.fillMaxWidth().heightIn(min = textMinHeight.dp, max = textMaxHeight.dp),
            minLines = 3
        )
    }
}


@Composable
fun DirectorOfficeDialogueTextField(
    value: String,
    onValueChange: (String) -> Unit,
    modifier: Modifier = Modifier,
    label: String = "متن دیالوگ",
    minHeight: Int = 140,
    maxHeight: Int = 320,
    minLines: Int = 4
) {
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        label = { Text(label) },
        modifier = modifier.fillMaxWidth().heightIn(min = minHeight.dp, max = maxHeight.dp),
        minLines = minLines
    )
}

@Composable
fun DirectorOfficeStageCueInputFields(
    title: String? = null,
    onTitleChange: ((String) -> Unit)? = null,
    instruction: String,
    onInstructionChange: (String) -> Unit,
    instructionLabel: String = "توضیحات صحنه",
    instructionMinHeight: Int = 90,
    instructionMaxHeight: Int? = 240
) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        if (title != null && onTitleChange != null) {
            OutlinedTextField(
                value = title,
                onValueChange = onTitleChange,
                label = { Text("عنوان راهنما") },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true
            )
        }
        OutlinedTextField(
            value = instruction,
            onValueChange = onInstructionChange,
            label = { Text(instructionLabel) },
            modifier = if (instructionMaxHeight == null) {
                Modifier.fillMaxWidth().heightIn(min = instructionMinHeight.dp)
            } else {
                Modifier.fillMaxWidth().heightIn(min = instructionMinHeight.dp, max = instructionMaxHeight.dp)
            },
            minLines = 2
        )
    }
}

/**
 * کارت واحد نوبت دفتر برای هر دو Flavor.
 * تفاوت سطح دسترسی از طریق callbackهای اختیاری و editable کنترل می‌شود؛
 * این کامپوننت هیچ DAO یا API اختصاصی Admin/Viewer را نمی‌شناسد.
 */
@Composable
fun DirectorOfficeTurnCard(
    turn: com.example.bookapp.data.DirectorTurnEntity,
    position: Int,
    totalTurns: Int,
    participants: List<com.example.bookapp.data.DirectorParticipantEntity>,
    segments: List<com.example.bookapp.data.DirectorTextSegmentEntity>,
    stageCues: List<com.example.bookapp.data.DirectorStageCueEntity>,
    editable: Boolean,
    modifier: Modifier = Modifier,
    containerColor: androidx.compose.ui.graphics.Color = androidx.compose.material3.MaterialTheme.colorScheme.surfaceVariant,
    contentColor: androidx.compose.ui.graphics.Color = androidx.compose.ui.graphics.Color.Unspecified,
    inlineEditorText: String? = null,
    onInlineEditorTextChange: ((String) -> Unit)? = null,
    onSaveInlineEditor: (() -> Unit)? = null,
    onSaveNumber: ((Int) -> Unit)? = null,
    onMoveUp: (() -> Unit)? = null,
    onMoveDown: (() -> Unit)? = null,
    onEditTurn: (() -> Unit)? = null,
    onAddDialogue: (() -> Unit)? = null,
    onManageParticipants: (() -> Unit)? = null,
    onDeleteTurn: (() -> Unit)? = null,
    onEditSegment: ((com.example.bookapp.data.DirectorTextSegmentEntity) -> Unit)? = null,
    onDeleteSegment: ((com.example.bookapp.data.DirectorTextSegmentEntity) -> Unit)? = null,
    onEditCue: ((com.example.bookapp.data.DirectorStageCueEntity) -> Unit)? = null,
    onDeleteCue: ((com.example.bookapp.data.DirectorStageCueEntity) -> Unit)? = null,
    onAddCue: (() -> Unit)? = null,
    collapseStageCues: Boolean = false,
    extraContent: (@Composable () -> Unit)? = null
) {
    var numberDraft by androidx.compose.runtime.remember(turn.id, turn.turnNumber) {
        androidx.compose.runtime.mutableStateOf(turn.turnNumber.toString())
    }
    var showInlineEditor by androidx.compose.runtime.remember(turn.id) {
        androidx.compose.runtime.mutableStateOf(false)
    }
    var showStageCues by androidx.compose.runtime.remember(turn.id, collapseStageCues) {
        androidx.compose.runtime.mutableStateOf(!collapseStageCues)
    }
    val roleLabel = participants.sortedBy { it.orderIndex }.joinToString("، ") { participant ->
        if (participant.isLead) "★ ${participant.roleTitle}" else participant.roleTitle
    }
    androidx.compose.material3.Card(
        modifier = modifier.fillMaxWidth(),
        colors = androidx.compose.material3.CardDefaults.cardColors(
            containerColor = containerColor,
            contentColor = contentColor
        )
    ) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(7.dp)
        ) {
            // شماره نوبت در سربرگ و کنار عنوان نقش قرار می‌گیرد؛ دیگر یک کادر بزرگ جداگانه نیست.
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalAlignment = androidx.compose.ui.Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = turn.title + if (turn.turnType == com.example.bookapp.data.DirectorTurnEntity.TYPE_COLLECTIVE) " • جمعی" else "",
                        style = androidx.compose.material3.MaterialTheme.typography.titleSmall,
                        fontWeight = androidx.compose.ui.text.font.FontWeight.SemiBold
                    )
                    if (roleLabel.isNotBlank()) {
                        Text(
                            "نقش‌ها: $roleLabel",
                            style = androidx.compose.material3.MaterialTheme.typography.labelSmall,
                            color = androidx.compose.material3.MaterialTheme.colorScheme.secondary
                        )
                    }
                }
                if (editable && onSaveNumber != null) {
                    androidx.compose.material3.OutlinedTextField(
                        value = numberDraft,
                        onValueChange = { numberDraft = it.filter(Char::isDigit).take(6) },
                        modifier = Modifier.width(82.dp).height(56.dp),
                        label = { Text("شماره", style = androidx.compose.material3.MaterialTheme.typography.labelSmall) },
                        textStyle = androidx.compose.material3.MaterialTheme.typography.bodySmall,
                        singleLine = true,
                        keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(
                            keyboardType = androidx.compose.ui.text.input.KeyboardType.Number
                        )
                    )
                    if (numberDraft != turn.turnNumber.toString()) {
                        androidx.compose.material3.TextButton(
                            onClick = {
                                numberDraft.toIntOrNull()?.takeIf { it > 0 }?.let(onSaveNumber)
                            },
                            contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 5.dp, vertical = 0.dp)
                        ) { Text("ثبت") }
                    }
                } else {
                    Text(
                        "نوبت ${toPersianOfficeDigits(turn.turnNumber.toString())}",
                        style = androidx.compose.material3.MaterialTheme.typography.labelMedium,
                        color = androidx.compose.material3.MaterialTheme.colorScheme.secondary
                    )
                }
            }

            if (totalTurns > 0) {
                Text(
                    "جایگاه اجرا ${toPersianOfficeDigits((position + 1).coerceAtLeast(1).toString())} از ${toPersianOfficeDigits(totalTurns.toString())}",
                    style = androidx.compose.material3.MaterialTheme.typography.labelSmall,
                    color = androidx.compose.material3.MaterialTheme.colorScheme.secondary
                )
            }

            if (editable && (onEditTurn != null || onAddDialogue != null || onManageParticipants != null || onDeleteTurn != null)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End,
                    verticalAlignment = androidx.compose.ui.Alignment.CenterVertically
                ) {
                    onEditTurn?.let { action ->
                        androidx.compose.material3.IconButton(onClick = action) {
                            androidx.compose.material3.Icon(androidx.compose.material.icons.Icons.Default.Edit, contentDescription = "ویرایش نوبت")
                        }
                    }
                    onAddDialogue?.let { action ->
                        androidx.compose.material3.IconButton(onClick = action) {
                            androidx.compose.material3.Icon(androidx.compose.material.icons.Icons.Default.Add, contentDescription = "افزودن دیالوگ")
                        }
                    }
                    onManageParticipants?.let { action ->
                        androidx.compose.material3.IconButton(onClick = action) {
                            androidx.compose.material3.Icon(androidx.compose.material.icons.Icons.Default.PersonAdd, contentDescription = "مدیریت نقش‌های نوبت")
                        }
                    }
                    onDeleteTurn?.let { action ->
                        androidx.compose.material3.IconButton(onClick = action) {
                            androidx.compose.material3.Icon(androidx.compose.material.icons.Icons.Default.Delete, contentDescription = "حذف نوبت")
                        }
                    }
                }
            }

            if (editable && (onMoveUp != null || onMoveDown != null)) {
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    onMoveUp?.let { action ->
                        androidx.compose.material3.TextButton(onClick = action, enabled = position > 0) { Text("↑ انتقال به بالا") }
                    }
                    onMoveDown?.let { action ->
                        androidx.compose.material3.TextButton(onClick = action, enabled = position in 0 until (totalTurns - 1)) { Text("↓ انتقال به پایین") }
                    }
                }
            }

            if (turn.notes.isNotBlank()) {
                Text(turn.notes, color = androidx.compose.material3.MaterialTheme.colorScheme.error, style = androidx.compose.material3.MaterialTheme.typography.bodySmall)
            }

            // متن دیالوگ در یک کادر با ارتفاع ثابت نمایش داده می‌شود؛ پیمایش فقط داخل همین کادر است.
            androidx.compose.material3.Surface(
                modifier = Modifier.fillMaxWidth().height(176.dp).verticalScroll(rememberScrollState()),
                shape = RoundedCornerShape(10.dp),
                color = androidx.compose.material3.MaterialTheme.colorScheme.surface,
                tonalElevation = 1.dp
            ) {
                Column(
                    modifier = Modifier.fillMaxWidth().padding(10.dp),
                    verticalArrangement = Arrangement.spacedBy(7.dp)
                ) {
                    Text(
                        "متن دیالوگ",
                        style = androidx.compose.material3.MaterialTheme.typography.labelMedium,
                        color = androidx.compose.material3.MaterialTheme.colorScheme.secondary
                    )
                    if (segments.isEmpty()) {
                        Text(
                            "برای این نوبت متنی ثبت نشده است.",
                            style = androidx.compose.material3.MaterialTheme.typography.bodySmall,
                            color = androidx.compose.material3.MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    segments.sortedBy { it.segmentIndex }.forEach { segment ->
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(4.dp),
                            verticalAlignment = androidx.compose.ui.Alignment.Top
                        ) {
                            Text(
                                segment.text,
                                modifier = Modifier.weight(1f),
                                style = androidx.compose.material3.MaterialTheme.typography.bodyMedium
                            )
                            if (editable) {
                                onEditSegment?.let { action ->
                                    androidx.compose.material3.IconButton(onClick = { action(segment) }) {
                                        androidx.compose.material3.Icon(androidx.compose.material.icons.Icons.Default.Edit, contentDescription = "ویرایش دیالوگ")
                                    }
                                }
                                onDeleteSegment?.let { action ->
                                    androidx.compose.material3.IconButton(onClick = { action(segment) }) {
                                        androidx.compose.material3.Icon(androidx.compose.material.icons.Icons.Default.Delete, contentDescription = "حذف دیالوگ")
                                    }
                                }
                            }
                        }
                    }
                }
            }

            // دکمه ویرایش فقط ویرایشگر را باز می‌کند؛ کادر ویرایش به‌صورت پیش‌فرض مخفی است.
            // دکمه راهنمای صحنه و دکمه ویرایش/ذخیره کوچک و در یک ردیف قرار دارند.
            if ((editable && inlineEditorText != null && onInlineEditorTextChange != null && onSaveInlineEditor != null) ||
                stageCues.isNotEmpty() || (editable && onAddCue != null)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = androidx.compose.ui.Alignment.CenterVertically
                ) {
                    if (editable && inlineEditorText != null && onInlineEditorTextChange != null && onSaveInlineEditor != null) {
                        androidx.compose.material3.TextButton(
                            onClick = {
                                if (showInlineEditor) {
                                    onSaveInlineEditor.invoke()
                                    showInlineEditor = false
                                } else {
                                    showInlineEditor = true
                                }
                            },
                            contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 8.dp, vertical = 0.dp)
                        ) {
                            Text(if (showInlineEditor) "ذخیره متن" else "ویرایش")
                        }
                        if (showInlineEditor) {
                            androidx.compose.material3.TextButton(
                                onClick = { showInlineEditor = false },
                                contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 6.dp, vertical = 0.dp)
                            ) { Text("انصراف") }
                        }
                    }
                    if (stageCues.isNotEmpty() || (editable && onAddCue != null)) {
                        androidx.compose.material3.TextButton(
                            onClick = { showStageCues = !showStageCues },
                            contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 8.dp, vertical = 0.dp)
                        ) {
                            Text("راهنمای صحنه (${toPersianOfficeDigits(stageCues.size.toString())}) ${if (showStageCues) "▲" else "▼"}")
                        }
                    }
                }
            }

            if (showInlineEditor && editable && inlineEditorText != null && onInlineEditorTextChange != null) {
                androidx.compose.material3.OutlinedTextField(
                    value = inlineEditorText,
                    onValueChange = onInlineEditorTextChange,
                    modifier = Modifier.fillMaxWidth().height(156.dp),
                    minLines = 4,
                    maxLines = 6,
                    label = { Text("متن دیالوگ") }
                )
            }

            if (showStageCues) {
                if (stageCues.isEmpty()) {
                    Text(
                        "برای این نوبت هنوز راهنمای صحنه‌ای ثبت نشده است.",
                        style = androidx.compose.material3.MaterialTheme.typography.bodySmall,
                        color = androidx.compose.material3.MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                stageCues.sortedBy { it.orderIndex }.forEach { cue ->
                    // راهنماها عمداً رنگ متفاوتی از متن دیالوگ دارند تا سریع تشخیص داده شوند.
                    androidx.compose.material3.Surface(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(10.dp),
                        color = androidx.compose.material3.MaterialTheme.colorScheme.tertiaryContainer
                    ) {
                        Column(
                            Modifier.fillMaxWidth().padding(10.dp),
                            verticalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            Text(
                                "${cue.title.ifBlank { cue.cueType }}${if (cue.blocksNextTurn) " • بلافاصله / توقف انتقال" else ""}",
                                style = androidx.compose.material3.MaterialTheme.typography.labelMedium,
                                color = androidx.compose.material3.MaterialTheme.colorScheme.onTertiaryContainer
                            )
                            Text(
                                cue.instruction,
                                style = androidx.compose.material3.MaterialTheme.typography.bodySmall,
                                color = androidx.compose.material3.MaterialTheme.colorScheme.onTertiaryContainer
                            )
                            if (editable) {
                                Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                                    onEditCue?.let { action ->
                                        androidx.compose.material3.TextButton(onClick = { action(cue) }) { Text("ویرایش راهنما") }
                                    }
                                    onDeleteCue?.let { action ->
                                        androidx.compose.material3.TextButton(onClick = { action(cue) }) { Text("حذف راهنما") }
                                    }
                                }
                            }
                        }
                    }
                }
                if (editable) {
                    onAddCue?.let { action ->
                        androidx.compose.material3.TextButton(onClick = action) { Text("افزودن راهنمای صحنه") }
                    }
                }
            }
            extraContent?.invoke()
        }
    }
}
private fun toPersianOfficeDigits(value: String): String = value.map { ch ->
    if (ch in '0'..'9') ('۰'.code + ch.code - '0'.code).toChar() else ch
}.joinToString("")

/** گزینه روش ساخت مشترک؛ توضیح و عنوان فقط در یک محل نگهداری می‌شوند. */
@Composable
fun DirectorOfficeBuildMethodOption(
    method: com.example.bookapp.data.DirectorOfficeBuildMethod,
    selected: Boolean,
    onClick: () -> Unit,
    orderLabel: String? = null,
    modifier: Modifier = Modifier,
    enabled: Boolean = true
) {
    Row(
        modifier = modifier.fillMaxWidth().clickable(enabled = enabled, onClick = onClick),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = androidx.compose.ui.Alignment.CenterVertically
    ) {
        androidx.compose.material3.RadioButton(selected = selected, onClick = onClick, enabled = enabled)
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text("${orderLabel.orEmpty()}${if (orderLabel.isNullOrBlank()) "" else " "}${method.label}", style = androidx.compose.material3.MaterialTheme.typography.bodyMedium)
            Text(method.description, style = androidx.compose.material3.MaterialTheme.typography.bodySmall, color = androidx.compose.material3.MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}
