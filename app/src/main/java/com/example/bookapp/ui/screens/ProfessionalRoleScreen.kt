
package com.example.bookapp.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.School
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.PhotoLibrary
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp

data class ProfessionalRoleItem(
    val id: Long,
    val title: String,
    val sectionCount: Int,
    val firstVerse: String,
    val isMine: Boolean
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProfessionalRoleScreen(
    taziehTitle: String,
    items: List<ProfessionalRoleItem>,
    onOpen: (ProfessionalRoleItem) -> Unit,
    onSetMine: (ProfessionalRoleItem) -> Unit,
    onCompare: (ProfessionalRoleItem) -> Unit,
    onOpenGallery: () -> Unit = {},
    showGallery: Boolean = true,
    onBack: () -> Unit,
    readOnly: Boolean = false,
    showCompare: Boolean = true,
    canSelectMine: Boolean = true
) {
    var query by remember { mutableStateOf("") }

    val ink = Color(0xFF263B5B)
    val muted = Color(0xFF647184)
    val lightGreen = Color(0xFFE8F2E9)
    val greenDark = Color(0xFF315B45)
    val paper = Color(0xFFF7F8F5)

    val filtered = remember(items, query) {
        val normalizedQuery = normalizePersianSearch(query)
        items.filter {
            normalizedQuery.isBlank() ||
                normalizePersianSearch(it.title).contains(normalizedQuery)
        }
    }

    Scaffold(
        containerColor = paper,
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = "نقش‌های تعزیه",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = ink
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

            // Search
            OutlinedTextField(
                value = query,
                onValueChange = { query = it },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp, vertical = 5.dp),
                singleLine = true,
                shape = RoundedCornerShape(14.dp),
                leadingIcon = {
                    Icon(
                        Icons.Filled.Search,
                        contentDescription = null,
                        tint = greenDark
                    )
                },
                placeholder = {
                    Text("جستجو در میان نقش‌ها")
                },
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = greenDark,
                    unfocusedBorderColor = Color(0xFFD8E1D9),
                    focusedContainerColor = Color.White,
                    unfocusedContainerColor = Color.White
                )
            )

            // Tazieh information and gallery
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp, vertical = 3.dp),
                shape = RoundedCornerShape(14.dp),
                colors = CardDefaults.cardColors(
                    containerColor = lightGreen
                ),
                elevation = CardDefaults.cardElevation(
                    defaultElevation = 1.dp
                )
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 11.dp, vertical = 7.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {

                    Column(
                        modifier = Modifier.weight(1f)
                    ) {
                        Text(
                            text = taziehTitle,
                            color = ink,
                            fontWeight = FontWeight.Bold,
                            style = MaterialTheme.typography.titleSmall,
                            maxLines = 1
                        )

                        Spacer(Modifier.height(3.dp))

                        Row(
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "${filtered.size} نقش",
                                color = greenDark,
                                fontWeight = FontWeight.Bold,
                                style = MaterialTheme.typography.labelMedium
                            )

                            Spacer(Modifier.width(9.dp))

                            Text(
                                text = "نقش من با علامت مشخص می‌شود.",
                                color = muted,
                                style = MaterialTheme.typography.labelSmall
                            )
                        }
                    }

                    if (showGallery) {
                        Surface(
                            onClick = onOpenGallery,
                            shape = RoundedCornerShape(11.dp),
                            color = Color.White
                        ) {
                            Row(
                                modifier = Modifier.padding(
                                    horizontal = 9.dp,
                                    vertical = 7.dp
                                ),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(
                                    Icons.Filled.PhotoLibrary,
                                    contentDescription = if (readOnly)
                                        "مشاهده گالری تصاویر"
                                    else
                                        "مدیریت گالری تصاویر",
                                    tint = ink,
                                    modifier = Modifier.size(19.dp)
                                )

                                Spacer(Modifier.width(5.dp))

                                Text(
                                    text = "گالری",
                                    color = ink,
                                    fontWeight = FontWeight.Bold,
                                    style = MaterialTheme.typography.labelMedium
                                )
                            }
                        }
                    }
                }
            }

            // Roles list
            if (filtered.isEmpty()) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f)
                        .padding(24.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Icon(
                            Icons.Filled.Search,
                            contentDescription = null,
                            tint = muted,
                            modifier = Modifier.size(38.dp)
                        )

                        Spacer(Modifier.height(10.dp))

                        Text(
                            text = if (items.isEmpty())
                                "هنوز نقشی برای این تعزیه ثبت نشده است."
                            else
                                "نقشی با این عنوان پیدا نشد.",
                            color = muted,
                            style = MaterialTheme.typography.bodyMedium
                        )
                    }
                }
            } else {
                LazyColumn(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f),
                    contentPadding = PaddingValues(
                        start = 12.dp,
                        end = 12.dp,
                        top = 5.dp,
                        bottom = 18.dp
                    ),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    itemsIndexed(
                        items = filtered,
                        key = { _, role -> role.id }
                    ) { index, role ->

                        Card(
                            onClick = {
                                onOpen(role)
                            },
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(16.dp),
                            colors = CardDefaults.cardColors(
                                containerColor = if (index % 2 == 0) {
                                    Color.White
                                } else {
                                    Color(0xFFE8F2E9)
                                }
                            ),
                            elevation = CardDefaults.cardElevation(
                                defaultElevation = 1.dp
                            )
                        ) {
                            Column(
                                modifier = Modifier.padding(12.dp)
                            ) {

                                // Role title
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(
                                        text = role.title,
                                        modifier = Modifier.weight(1f),
                                        style = MaterialTheme.typography.titleMedium,
                                        fontWeight = FontWeight.Bold,
                                        color = ink
                                    )

                                    if (role.isMine) {
                                        Surface(
                                            shape = RoundedCornerShape(10.dp),
                                            color = lightGreen
                                        ) {
                                            Row(
                                                modifier = Modifier.padding(
                                                    horizontal = 9.dp,
                                                    vertical = 5.dp
                                                ),
                                                verticalAlignment = Alignment.CenterVertically
                                            ) {
                                                Icon(
                                                    Icons.Filled.CheckCircle,
                                                    contentDescription = "نقش من",
                                                    tint = greenDark,
                                                    modifier = Modifier.size(17.dp)
                                                )

                                                Spacer(Modifier.width(4.dp))

                                                Text(
                                                    "نقش من",
                                                    color = greenDark,
                                                    style = MaterialTheme.typography.labelSmall,
                                                    fontWeight = FontWeight.Bold
                                                )
                                            }
                                        }
                                    }
                                }

                                Spacer(Modifier.height(6.dp))

                                Text(
                                    text = "${role.sectionCount} بخش",
                                    color = muted,
                                    style = MaterialTheme.typography.bodySmall
                                )

                                if (role.firstVerse.isNotBlank()) {
                                    Spacer(Modifier.height(7.dp))

                                    Surface(
                                        shape = RoundedCornerShape(9.dp),
                                        color = Color(0xFFF5F6F4)
                                    ) {
                                        Text(
                                            text = role.firstVerse,
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .padding(9.dp),
                                            maxLines = 2,
                                            color = muted,
                                            style = MaterialTheme.typography.bodySmall
                                        )
                                    }
                                }

                                // Actions
                                if (canSelectMine || showCompare) {
                                    Spacer(Modifier.height(9.dp))

                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                                    ) {
                                        if (canSelectMine) {
                                            OutlinedButton(
                                                onClick = {
                                                    onSetMine(role)
                                                },
                                                shape = RoundedCornerShape(11.dp),
                                                colors = ButtonDefaults.outlinedButtonColors(
                                                    contentColor = if (role.isMine)
                                                        Color(0xFF9B3D3D)
                                                    else
                                                        greenDark
                                                ),
                                                modifier = Modifier.weight(1f)
                                            ) {
                                                Icon(
                                                    Icons.Filled.School,
                                                    contentDescription = null,
                                                    modifier = Modifier.size(18.dp)
                                                )

                                                Spacer(Modifier.width(5.dp))

                                                Text(
                                                    if (role.isMine)
                                                        "حذف از نقش من"
                                                    else
                                                        "انتخاب نقش من"
                                                )
                                            }
                                        }

                                        if (showCompare) {
                                            OutlinedButton(
                                                onClick = {
                                                    onCompare(role)
                                                },
                                                shape = RoundedCornerShape(11.dp),
                                                colors = ButtonDefaults.outlinedButtonColors(
                                                    contentColor = ink
                                                ),
                                                modifier = Modifier.weight(1f)
                                            ) {
                                                Text("نمایش چند نقش")
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
}
