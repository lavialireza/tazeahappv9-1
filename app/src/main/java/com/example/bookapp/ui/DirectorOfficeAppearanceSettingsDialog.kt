package com.example.bookapp.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.FilterChip
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.bookapp.ui.theme.FontChoiceLabels

/** تنظیمات ظاهری مشترک دفتر؛ ذخیره‌سازی و مالکیت ترجیحات را هر Flavor تأمین می‌کند. */
@Composable
fun DirectorOfficeAppearanceSettingsDialog(
    darkMode: Boolean,
    onDarkModeChange: (Boolean) -> Unit,
    fontScale: Float,
    onFontScaleChange: (Float) -> Unit,
    lineSpacing: Float,
    onLineSpacingChange: (Float) -> Unit,
    fontChoice: String,
    onFontChoiceChange: (String) -> Unit,
    themeChoice: String,
    onThemeChoiceChange: (String) -> Unit,
    keepScreenOn: Boolean,
    onKeepScreenOnChange: (Boolean) -> Unit,
    onResetSettings: () -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("تنظیمات دفتر معین‌البکا") },
        text = {
            Column(
                modifier = Modifier.fillMaxWidth().heightIn(max = 520.dp).verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                // گزینه روشن ماندن صفحه در ابتدای تنظیمات قرار دارد تا هنگام مطالعه سریع در دسترس باشد.
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text("روشن ماندن صفحه")
                        Text("هنگام کار با دفتر، صفحه خاموش نشود")
                    }
                    Switch(checked = keepScreenOn, onCheckedChange = onKeepScreenOnChange)
                }
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                    Text("حالت شب")
                    Switch(checked = darkMode, onCheckedChange = onDarkModeChange)
                }
                Text("اندازه نوشته: ${(fontScale * 100).toInt()}٪")
                Slider(value = fontScale.coerceIn(0.8f, 2f), onValueChange = onFontScaleChange, valueRange = 0.8f..2f, steps = 11)
                Text("فاصله خطوط: ${"%.1f".format(lineSpacing)}")
                Slider(
                    value = lineSpacing.coerceIn(1.1f, 2.6f),
                    onValueChange = onLineSpacingChange,
                    valueRange = 1.1f..2.6f,
                    steps = 14
                )
                Text("نوع قلم")
                FontChoiceLabels.forEach { (key, label) ->
                    Row(Modifier.fillMaxWidth().clickable { onFontChoiceChange(key) }, verticalAlignment = Alignment.CenterVertically) {
                        RadioButton(selected = fontChoice == key, onClick = { onFontChoiceChange(key) })
                        Text(label)
                    }
                }
                Text("رنگ‌بندی")
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    listOf("default" to "طلایی", "green" to "سبز", "red" to "قرمز").forEach { (key, label) ->
                        FilterChip(selected = themeChoice == key, onClick = { onThemeChoiceChange(key) }, label = { Text(label) })
                    }
                }
            }
        },
        confirmButton = {
            // دکمه بازگشت به‌صورت فشرده در فضای موجود قرار می‌گیرد تا دکمه بستن
            // در صفحه‌های باریک از محدوده پنجره تنظیمات بیرون نزند.
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                TextButton(
                    onClick = onResetSettings,
                    modifier = Modifier.weight(1f),
                    contentPadding = PaddingValues(horizontal = 4.dp, vertical = 0.dp)
                ) {
                    Text(
                        "بازگشت به تنظیمات قبلی",
                        fontSize = 11.sp,
                        maxLines = 1,
                        softWrap = false
                    )
                }
                TextButton(
                    onClick = onDismiss,
                    contentPadding = PaddingValues(horizontal = 6.dp, vertical = 0.dp)
                ) {
                    Text("بستن", fontSize = 12.sp, maxLines = 1, softWrap = false)
                }
            }
        }
    )
}
