
package com.example.bookapp.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.platform.LocalContext
import com.example.bookapp.R
import com.example.bookapp.data.Prefs
import com.example.bookapp.ui.theme.colorSchemeFor

data class MultiRoleSectionContent(
    val id: Long,
    val title: String,
    val content: String
)

data class MultiRoleRoleContent(
    val roleTitle: String,
    val sections: List<MultiRoleSectionContent>
)

private val BTitrFont = FontFamily(
    Font(R.font.b_titr_bold, FontWeight.Bold)
)

@Composable
fun MultiRoleDialogueScreen(
    taziehTitle: String,
    roles: List<MultiRoleRoleContent>,
    darkMode: Boolean,
    onDarkModeChange: (Boolean) -> Unit,
    fontScale: Float,
    onFontScaleChange: (Float) -> Unit,
    themeChoice: String,
    onThemeChoiceChange: (String) -> Unit,
    fontChoice: String,
    onFontChoiceChange: (String) -> Unit,
    onBack: () -> Unit
) {
    val context = LocalContext.current

    var lineSpacing by remember {
        mutableFloatStateOf(Prefs.getLineSpacing(context))
    }

    var showOverflowMenu by remember { mutableStateOf(false) }
    var showSettings by remember { mutableStateOf(false) }
    var showFontMenu by remember { mutableStateOf(false) }

    val selectedFontFamily = when (fontChoice) {
        "b_titr" -> BTitrFont
        "serif" -> FontFamily.Serif
        "sans" -> FontFamily.SansSerif
        "cursive" -> FontFamily.Cursive
        else -> FontFamily.Default
    }

    MaterialTheme(colorScheme = colorSchemeFor(themeChoice, darkMode)) {
        Surface(
            modifier = Modifier.fillMaxSize(),
            color = MaterialTheme.colorScheme.background
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 12.dp)
            ) {
                // نوار بالای صفحه و منوی سه‌نقطه
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 5.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    IconButton(onClick = onBack) {
                        Icon(
                            Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "بازگشت"
                        )
                    }

                    Text(
                        text = "مکالمه چندنقشی",
                        modifier = Modifier.weight(1f),
                        style = MaterialTheme.typography.titleLarge,
                        color = MaterialTheme.colorScheme.primary
                    )

                    Box {
                        IconButton(
                            onClick = { showOverflowMenu = true }
                        ) {
                            Icon(
                                Icons.Filled.MoreVert,
                                contentDescription = "تنظیمات نمایش"
                            )
                        }

                        DropdownMenu(
                            expanded = showOverflowMenu,
                            onDismissRequest = {
                                showOverflowMenu = false
                            }
                        ) {
                            DropdownMenuItem(
                                text = { Text("تنظیمات نمایش") },
                                onClick = {
                                    showOverflowMenu = false
                                    showSettings = true
                                }
                            )
                        }
                    }
                }

                Text(
                    text = taziehTitle,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(start = 8.dp, bottom = 8.dp),
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onBackground
                )

                if (roles.isEmpty()) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(1f),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = "متنی برای نقش‌های انتخاب‌شده پیدا نشد.",
                            color = MaterialTheme.colorScheme.onBackground
                        )
                    }
                } else {
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

                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(1f),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        roles.forEachIndexed { index, role ->
                            key(index, role.roleTitle) {
                                Card(
                                    modifier = Modifier
                                        .weight(1f)
                                        .fillMaxWidth(),
                                    shape = RoundedCornerShape(14.dp),
                                    colors = CardDefaults.cardColors(
                                        containerColor =
                                            roleCardColors[index % roleCardColors.size]
                                    )
                                ) {
                                    Column(
                                        modifier = Modifier
                                            .fillMaxSize()
                                            .padding(
                                                horizontal = 10.dp,
                                                vertical = 6.dp
                                            )
                                    ) {
                                        Text(
                                            text = role.roleTitle,
                                            style = MaterialTheme.typography.titleSmall,
                                            color = roleTitleColors[
                                                index % roleTitleColors.size
                                            ],
                                            fontWeight = FontWeight.Bold
                                        )

                                        HorizontalDivider(
                                            modifier = Modifier.padding(vertical = 5.dp),
                                            color = MaterialTheme.colorScheme.outlineVariant
                                        )

                                        Box(
                                            modifier = Modifier
                                                .weight(1f)
                                                .fillMaxWidth()
                                                .verticalScroll(
                                                    rememberScrollState()
                                                )
                                        ) {
                                            Column {
                                                if (role.sections.isEmpty()) {
                                                    Text(
                                                        text = "برای این نقش بخشی ثبت نشده است.",
                                                        color = MaterialTheme.colorScheme.onSurface
                                                    )
                                                }

                                                role.sections.forEach { section ->
                                                    if (section.title.isNotBlank()) {
                                                        Text(
                                                            text = section.title,
                                                            style = MaterialTheme.typography.titleSmall,
                                                            color = MaterialTheme.colorScheme.secondary,
                                                            fontWeight = FontWeight.Bold
                                                        )

                                                        Spacer(
                                                            Modifier.height(5.dp)
                                                        )
                                                    }

                                                    Text(
                                                        text = section.content,
                                                        fontFamily = selectedFontFamily,
                                                        fontWeight = if (
                                                            fontChoice == "b_titr"
                                                        ) {
                                                            FontWeight.Bold
                                                        } else {
                                                            FontWeight.Normal
                                                        },
                                                        fontSize = (20f * fontScale).sp,
                                                        lineHeight = (
                                                            20f * fontScale * lineSpacing
                                                        ).sp,
                                                        color = MaterialTheme.colorScheme.onSurface
                                                    )

                                                    Spacer(
                                                        Modifier.height(16.dp)
                                                    )
                                                }
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }

        // پنجره تنظیمات نمایش
        if (showSettings) {
            AlertDialog(
                onDismissRequest = {
                    showSettings = false
                    showFontMenu = false
                },
                title = {
                    Text("تنظیمات نمایش متن")
                },
                text = {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(max = 480.dp)
                            .verticalScroll(rememberScrollState()),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text("حالت شب")
                            Switch(
                                checked = darkMode,
                                onCheckedChange = onDarkModeChange
                            )
                        }

                        HorizontalDivider()

                        Text("اندازه متن: ${"%.1f".format(fontScale)}")

                        Slider(
                            value = fontScale.coerceIn(0.8f, 1.6f),
                            onValueChange = onFontScaleChange,
                            valueRange = 0.8f..1.6f
                        )

                        HorizontalDivider()

                        Text("قلم متن")

                        Box {
                            OutlinedButton(
                                onClick = {
                                    showFontMenu = true
                                }
                            ) {
                                Text(
                                    when (fontChoice) {
                                        "b_titr" -> "B Titr"
                                        "serif" -> "سریف"
                                        "sans" -> "ساده"
                                        "cursive" -> "تحریری"
                                        else -> "پیش‌فرض"
                                    }
                                )
                            }

                            DropdownMenu(
                                expanded = showFontMenu,
                                onDismissRequest = {
                                    showFontMenu = false
                                }
                            ) {
                                listOf(
                                    "titr" to "پیش‌فرض",
                                    "b_titr" to "B Titr",
                                    "serif" to "سریف",
                                    "sans" to "ساده",
                                    "cursive" to "تحریری"
                                ).forEach { (value, label) ->
                                    DropdownMenuItem(
                                        text = { Text(label) },
                                        onClick = {
                                            onFontChoiceChange(value)
                                            showFontMenu = false
                                        }
                                    )
                                }
                            }
                        }

                        HorizontalDivider()

                        Text("فاصله خطوط")

                        Slider(
                            value = lineSpacing.coerceIn(1f, 2f),
                            onValueChange = {
                                lineSpacing = it
                                Prefs.setLineSpacing(context, it)
                            },
                            valueRange = 1f..2f
                        )

                        Text(
                            text = "رنگ برنامه",
                            style = MaterialTheme.typography.titleSmall
                        )

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceEvenly
                        ) {
                            TextButton(
                                onClick = {
                                    onThemeChoiceChange("default")
                                }
                            ) {
                                Text("طلایی")
                            }

                            TextButton(
                                onClick = {
                                    onThemeChoiceChange("green")
                                }
                            ) {
                                Text("سبز")
                            }

                            TextButton(
                                onClick = {
                                    onThemeChoiceChange("red")
                                }
                            ) {
                                Text("قرمز")
                            }
                        }
                    }
                },
                confirmButton = {
                    TextButton(
                        onClick = { showSettings = false }
                    ) {
                        Text("بستن")
                    }
                }
            )
        }
    }
}
