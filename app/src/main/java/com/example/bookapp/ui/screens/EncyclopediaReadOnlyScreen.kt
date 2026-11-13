package com.example.bookapp.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.example.bookapp.data.AppDatabase
import com.example.bookapp.data.EncyclopediaRecordEntity
import com.example.bookapp.data.EncyclopediaLinkEntity

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EncyclopediaReadOnlyScreen(db: AppDatabase, onBack: () -> Unit) {
    var query by remember { mutableStateOf("") }
    var records by remember { mutableStateOf(emptyList<EncyclopediaRecordEntity>()) }
    var selectedKind by remember { mutableStateOf("ALL") }
    var links by remember { mutableStateOf(emptyMap<Long, List<EncyclopediaLinkEntity>>()) }
    LaunchedEffect(Unit) {
        records = db.encyclopediaDao().allVerified()
        val loadedLinks = mutableMapOf<Long, List<EncyclopediaLinkEntity>>()
        for (record in records) loadedLinks[record.id] = db.encyclopediaDao().links(record.id)
        links = loadedLinks
    }
    val filtered = records.filter { row ->
        (selectedKind == "ALL" || row.kind == selectedKind) &&
            (query.isBlank() || listOf(row.title, row.aliases, row.summary, row.details).any { it.contains(query.trim(), ignoreCase = true) })
    }
    Scaffold(topBar = { TopAppBar(title = { Text("دانشنامه تعزیه") }, navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, null) } }) }) { padding ->
        Column(Modifier.fillMaxSize().padding(padding).padding(12.dp)) {
            OutlinedTextField(query, { query = it }, Modifier.fillMaxWidth(), label = { Text("جستجو در دانشنامه") }, singleLine = true)
            Spacer(Modifier.height(8.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                listOf("ALL" to "همه", "PERSON" to "اشخاص", "EVENT" to "وقایع", "PLACE" to "مکان‌ها", "SOURCE" to "منابع").forEach { (key, label) ->
                    FilterChip(selected = selectedKind == key, onClick = { selectedKind = key }, label = { Text(label) })
                }
            }
            Text("فقط مدخل‌های تأییدشده نمایش داده می‌شوند.", style = MaterialTheme.typography.bodySmall)
            LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                items(filtered, key = { it.uid }) { item ->
                    ElevatedCard(Modifier.fillMaxWidth()) {
                        Column(Modifier.padding(12.dp)) {
                            Text(item.title, style = MaterialTheme.typography.titleMedium)
                            if (item.aliases.isNotBlank()) Text("نام‌های دیگر: ${item.aliases}", style = MaterialTheme.typography.bodySmall)
                            if (item.summary.isNotBlank()) Text(item.summary)
                            if (item.details.isNotBlank()) Text(item.details, style = MaterialTheme.typography.bodySmall)
                            if (item.sourceCitation.isNotBlank()) Text("منبع: ${item.sourceCitation}", style = MaterialTheme.typography.labelSmall)
                            val related = links[item.id].orEmpty()
                            if (related.isNotEmpty()) Text("پیوندهای آرشیوی: ${related.size}", style = MaterialTheme.typography.labelSmall)
                        }
                    }
                }
            }
        }
    }
}
