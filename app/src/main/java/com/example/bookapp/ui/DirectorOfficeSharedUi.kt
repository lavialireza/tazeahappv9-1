package com.example.bookapp.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.ui.Modifier
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
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
