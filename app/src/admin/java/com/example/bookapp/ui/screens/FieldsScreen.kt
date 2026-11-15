
package com.example.bookapp.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.MenuBook
import androidx.compose.material.icons.filled.Category
import androidx.compose.material.icons.filled.ChevronLeft
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp

data class FieldCatalogItem(
    val id: Long,
    val title: String,
    val taziehCount: Int
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FieldsScreen(
    items: List<FieldCatalogItem>,
    onOpen: (FieldCatalogItem) -> Unit,
    onBack: () -> Unit
) {
    var query by remember { mutableStateOf("") }

    val ink = Color(0xFF263B5B)
    val muted = Color(0xFF647184)
    // رنگ کارت معرفی با کارت اصلی کتابخانه در صفحه قبل هماهنگ است.
     val headerColor = Color(0xFFF3E5C3)
    val softGreen = Color(0xFFE8F2E9)
    val softBlue = Color(0xFFE8EDF8)
    val gold = Color(0xFFF4EBD7)
    val paper = Color(0xFFF7F8F5)

    val filtered = remember(items, query) {
        val q = normalizePersianSearch(query)
        items.filter {
            q.isBlank() || normalizePersianSearch(it.title).contains(q)
        }.sortedBy {
            normalizePersianSearch(it.title)
        }
    }

    Scaffold(
        containerColor = paper,
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        "زمینه‌های تعزیه",
                        color = ink,
                        fontWeight = FontWeight.Bold
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "بازگشت",
                            tint = ink
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = paper
                )
            )
        }
    ) { padding ->

        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
        ) {

            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                shape = RoundedCornerShape(22.dp),
                colors = CardDefaults.cardColors(
                    containerColor = headerColor
                ),
                elevation = CardDefaults.cardElevation(
                    defaultElevation = 0.dp
                )
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(18.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Surface(
                        shape = RoundedCornerShape(15.dp),
                        color = Color.White
                    ) {
                        Icon(
                            Icons.Filled.Category,
                            contentDescription = null,
                            modifier = Modifier.padding(12.dp).size(28.dp),
                            tint = Color(0xFF315B45)
                        )
                    }

                    Spacer(Modifier.width(14.dp))

                    Column(
                        modifier = Modifier.weight(1f),
                        verticalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Text(
                            "زمینه‌های تعزیه",
                            color = ink,
                            fontWeight = FontWeight.Bold,
                            style = MaterialTheme.typography.titleMedium
                        )
                        Text(
                            "زمینه موردنظر را انتخاب کنید و به مجموعه تعزیه‌های آن بروید.",
                            color = muted,
                            style = MaterialTheme.typography.bodySmall
                        )
                    }
                }
            }

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 18.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    "فهرست زمینه‌ها",
                    color = ink,
                    fontWeight = FontWeight.Bold,
                    style = MaterialTheme.typography.titleMedium
                )

                Spacer(Modifier.weight(1f))

                Surface(
                    shape = RoundedCornerShape(20.dp),
                    color = gold
                ) {
                    Text(
                        "${filtered.size} زمینه",
                        modifier = Modifier.padding(
                            horizontal = 12.dp,
                            vertical = 6.dp
                        ),
                        color = Color(0xFF735A2A),
                        fontWeight = FontWeight.Bold,
                        style = MaterialTheme.typography.labelMedium
                    )
                }
            }

            OutlinedTextField(
                value = query,
                onValueChange = { query = it },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 6.dp),
                singleLine = true,
                shape = RoundedCornerShape(16.dp),
                leadingIcon = {
                    Icon(
                        Icons.Filled.Search,
                        contentDescription = null,
                        tint = ink
                    )
                },
                placeholder = {
                    Text("جستجوی زمینه تعزیه")
                },
                colors = OutlinedTextFieldDefaults.colors(
                 unfocusedContainerColor = softGreen,
                    focusedContainerColor = Color.White,
                    unfocusedBorderColor = Color.Transparent,
                    focusedBorderColor = Color(0xFF789A7D)
                )
            )

            if (filtered.isEmpty()) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(24.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Icon(
                            Icons.Filled.MenuBook,
                            contentDescription = null,
                            tint = muted,
                            modifier = Modifier.size(42.dp)
                        )
                        Text(
                            if (items.isEmpty())
                                "هنوز زمینه‌ای ثبت نشده است"
                            else
                                "زمینه‌ای با این نام پیدا نشد",
                            color = muted,
                            fontWeight = FontWeight.Medium
                        )
                    }
                }
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(
                        start = 16.dp,
                        end = 16.dp,
                        top = 8.dp,
                        bottom = 20.dp
                    ),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    items(filtered, key = { it.id }) { field ->
                        val cardColor = if (filtered.indexOf(field) % 2 == 0) softGreen else softBlue
                        val iconSurfaceColor = if (filtered.indexOf(field) % 2 == 0) Color(0xFFDCEBDD) else Color(0xFFDCE6F2)

                        Card(
                            onClick = { onOpen(field) },
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(18.dp),
                            colors = CardDefaults.cardColors(
                                containerColor = cardColor
                            ),
                            elevation = CardDefaults.cardElevation(
                                defaultElevation = 1.dp
                            )
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 15.dp, vertical = 14.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {

                                Surface(
                                    shape = RoundedCornerShape(13.dp),
                                    color = iconSurfaceColor
                                ) {
                                    Icon(
                                        Icons.Filled.MenuBook,
                                        contentDescription = null,
                                        modifier = Modifier
                                            .padding(10.dp)
                                            .size(23.dp),
                                        tint = Color(0xFF315B45)
                                    )
                                }

                                Spacer(Modifier.width(13.dp))

                                Column(
                                    modifier = Modifier.weight(1f),
                                    verticalArrangement = Arrangement.spacedBy(5.dp)
                                ) {
                                    Text(
                                        field.title,
                                        color = ink,
                                        fontWeight = FontWeight.Bold,
                                        style = MaterialTheme.typography.titleMedium
                                    )

                                    Text(
                                        "مجموعه تعزیه‌های این زمینه",
                                        color = muted,
                                        style = MaterialTheme.typography.bodySmall
                                    )
                                }

                                Surface(
                                    shape = RoundedCornerShape(12.dp),
                                    color = gold
                                ) {
                                    Text(
                                        "${field.taziehCount} تعزیه",
                                        modifier = Modifier.padding(
                                            horizontal = 10.dp,
                                            vertical = 7.dp
                                        ),
                                        color = Color(0xFF735A2A),
                                        fontWeight = FontWeight.Bold,
                                        style = MaterialTheme.typography.labelMedium
                                    )
                                }

                                Spacer(Modifier.width(5.dp))

                                Icon(
                                    Icons.Filled.ChevronLeft,
                                    contentDescription = "مشاهده",
                                    tint = ink
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}
