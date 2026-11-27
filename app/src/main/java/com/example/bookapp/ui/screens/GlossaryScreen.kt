package com.example.bookapp.ui.screens

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.platform.LocalContext
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.room.withTransaction
import com.example.bookapp.data.*
import kotlinx.coroutines.launch
import org.json.JSONObject
import java.util.UUID

private fun normalizeGlossaryEntry(value: String): String = value.trim()
    .replace('ي', 'ی').replace('ك', 'ک').replace('ۀ', 'ه').replace('ة', 'ه')
    .replace(Regex("[\\u064B-\\u065F\\u0670]"), "")
    .replace(Regex("\\s+"), " ")

/**
 * صفحه واحد فرهنگ اصطلاحات برای Admin و Viewer.
 * Viewer فقط مدخل‌ها و معنی‌های تأییدشده را می‌بیند؛ ورود و بررسی اطلاعات فقط در Admin فعال است.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GlossaryScreen(
    db: AppDatabase,
    onBack: () -> Unit,
    isAdmin: Boolean
) {
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    var importPreview by remember { mutableStateOf<GlossaryImportPreview?>(null) }
    // Room Flow keeps the screen synchronized with bundled JSON imports that may finish
    // after this screen has already opened. No manual reload/race with startup is needed.
    val allTerms by db.glossaryDao().observeAllTerms().collectAsState(initial = emptyList())
    val spellings by db.glossaryDao().observeAllSpellings().collectAsState(initial = emptyList())
    val allMeanings by db.glossaryDao().observeAllMeanings().collectAsState(initial = emptyList())
    val terms = if (isAdmin) allTerms else allTerms.filter { it.reviewStatus == GlossaryTermEntity.STATUS_VERIFIED }
    val meanings = if (isAdmin) allMeanings else allMeanings.filter { it.reviewStatus == GlossaryTermEntity.STATUS_VERIFIED }
    var query by remember { mutableStateOf("") }
    var selectedTermId by remember { mutableStateOf<Long?>(null) }
    var showEditor by remember { mutableStateOf(false) }
    var statusFilter by remember { mutableStateOf("ALL") }
    var message by remember { mutableStateOf<String?>(null) }
    var busy by remember { mutableStateOf(false) }
    var historyTermId by remember { mutableStateOf<Long?>(null) }
    var historyRows by remember { mutableStateOf(emptyList<GlossaryHistoryEntity>()) }
    var pendingExportJson by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(terms, selectedTermId) {
        if (selectedTermId != null && terms.none { it.id == selectedTermId }) selectedTermId = null
    }

    val importLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null && isAdmin) {
            scope.launch {
                busy = true
                try {
                    val json = context.contentResolver.openInputStream(uri)?.bufferedReader()?.use { it.readText() }
                        ?: error("خواندن فایل انتخاب‌شده ممکن نشد.")
                    importPreview = GlossaryImportService(db).preview(json)
                    message = if (importPreview?.isValid == true) "پیش‌نمایش آماده است؛ هنوز هیچ داده‌ای وارد نشده." else "فایل بررسی شد؛ خطاهای آن را در پیش‌نمایش ببینید."
                } catch (e: Exception) {
                    message = "خواندن بسته ناموفق بود: ${e.message ?: "خطای نامشخص"}"
                } finally { busy = false }
            }
        }
    }

    val exportLauncher = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/json")) { uri ->
        val json = pendingExportJson
        pendingExportJson = null
        if (uri != null && json != null && isAdmin) {
            scope.launch {
                busy = true
                try {
                    context.contentResolver.openOutputStream(uri)?.bufferedWriter(Charsets.UTF_8)?.use { it.write(json) }
                        ?: error("ذخیره فایل خروجی ممکن نشد.")
                    message = "بسته انتشار ذخیره شد؛ آن را در پوشه app/src/main/assets/glossary/packages مخزن قرار دهید."
                } catch (e: Exception) {
                    message = "ساخت فایل انتشار ناموفق بود: ${e.message ?: "خطای نامشخص"}"
                } finally { busy = false }
            }
        }
    }

    val visibleTerms = remember(terms, spellings, meanings, query, statusFilter, isAdmin) {
        val q = normalizeGlossaryEntry(query)
        terms.filter { term ->
            val termMeanings = meanings.filter { it.termId == term.id }
            val matchesQuery = q.isBlank() || normalizeGlossaryEntry(term.headword).contains(q) ||
                spellings.any { it.termId == term.id && normalizeGlossaryEntry(it.spelling).contains(q) } ||
                termMeanings.any { normalizeGlossaryEntry(it.meaning).contains(q) || normalizeGlossaryEntry(it.sourceTitle).contains(q) }
            val statusMatches = !isAdmin || when (statusFilter) {
                "PENDING" -> term.reviewStatus == GlossaryTermEntity.STATUS_PENDING
                "VERIFIED" -> term.reviewStatus == GlossaryTermEntity.STATUS_VERIFIED
                "REJECTED" -> term.reviewStatus == GlossaryTermEntity.STATUS_REJECTED
                else -> true
            }
            matchesQuery && statusMatches
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("فرهنگ اصطلاحات تعزیه") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "بازگشت")
                    }
                }
            )
        },
        floatingActionButton = {
            if (isAdmin) FloatingActionButton(onClick = { showEditor = true }) {
                Icon(Icons.Filled.Add, contentDescription = "ثبت واژه")
            }
        }
    ) { padding ->
        Column(Modifier.fillMaxSize().padding(padding)) {
            if (isAdmin) {
                Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    OutlinedButton(
                        onClick = { importLauncher.launch(arrayOf("application/json", "text/json", "text/plain")) },
                        enabled = !busy,
                        modifier = Modifier.fillMaxWidth()
                    ) { Text(if (busy) "در حال بررسی بسته…" else "ورود بسته واژه‌ها (JSON)") }
                    OutlinedButton(
                        onClick = {
                            scope.launch {
                                busy = true
                                try {
                                    val packageUid = "glossary-publish-${UUID.randomUUID()}"
                                    pendingExportJson = exportVerifiedGlossaryJson(db, packageUid)
                                    exportLauncher.launch("$packageUid.json")
                                } catch (e: Exception) {
                                    message = "تهیه بسته انتشار ممکن نشد: ${e.message ?: "خطای نامشخص"}"
                                } finally { busy = false }
                            }
                        },
                        enabled = !busy,
                        modifier = Modifier.fillMaxWidth()
                    ) { Text("تهیه فایل انتشار واژه‌های تأییدشده") }
                }
            }
            OutlinedTextField(
                value = query,
                onValueChange = { query = it },
                label = { Text("جست‌وجوی واژه، معنی یا صورت نوشتاری") },
                leadingIcon = { Icon(Icons.Filled.Search, contentDescription = null) },
                singleLine = true,
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp)
            )
            if (isAdmin) {
                Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FilterChip(selected = statusFilter == "ALL", onClick = { statusFilter = "ALL" }, label = { Text("همه") })
                    FilterChip(selected = statusFilter == "PENDING", onClick = { statusFilter = "PENDING" }, label = { Text("در انتظار") })
                    FilterChip(selected = statusFilter == "VERIFIED", onClick = { statusFilter = "VERIFIED" }, label = { Text("تأییدشده") })
                    FilterChip(selected = statusFilter == "REJECTED", onClick = { statusFilter = "REJECTED" }, label = { Text("ردشده") })
                }
            }
            if (message != null) {
                Text(message!!, modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp), color = MaterialTheme.colorScheme.primary, style = MaterialTheme.typography.bodySmall)
            }
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                itemsIndexed(visibleTerms, key = { _, term -> term.id }) { index, term ->
                    val termMeanings = meanings.filter { it.termId == term.id }
                    val expanded = selectedTermId == term.id
                    Card(
                        modifier = Modifier.fillMaxWidth().clickable { selectedTermId = if (expanded) null else term.id },
                        shape = RoundedCornerShape(14.dp),
                        colors = CardDefaults.cardColors(
                            containerColor = if (index % 2 == 0) MaterialTheme.colorScheme.surface
                            else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.55f)
                        )
                    ) {
                        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                Text(term.headword, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                                if (isAdmin) {
                                    Text(
                                        when (term.reviewStatus) {
                                            GlossaryTermEntity.STATUS_VERIFIED -> "تأییدشده"
                                            GlossaryTermEntity.STATUS_REJECTED -> "ردشده"
                                            else -> "در انتظار بررسی"
                                        },
                                        style = MaterialTheme.typography.labelSmall,
                                        color = if (term.reviewStatus == GlossaryTermEntity.STATUS_VERIFIED) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.secondary
                                    )
                                }
                            }
                            if (term.partOfSpeech.isNotBlank()) Text(term.partOfSpeech, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.secondary)
                            Text(termMeanings.firstOrNull()?.meaning ?: if (isAdmin) "هنوز معنی ثبت نشده است." else "", style = MaterialTheme.typography.bodyMedium)
                            if (expanded) {
                                HorizontalDivider()
                                if (term.pronunciation.isNotBlank()) Text("تلفظ: ${term.pronunciation}", style = MaterialTheme.typography.bodySmall)
                                if (term.etymology.isNotBlank()) Text("ریشه‌شناسی: ${term.etymology}", style = MaterialTheme.typography.bodySmall)
                                if (term.notes.isNotBlank()) Text("یادداشت: ${term.notes}", style = MaterialTheme.typography.bodySmall)
                                if (isAdmin) {
                                    TextButton(onClick = {
                                        scope.launch { historyRows = db.glossaryDao().history(term.id); historyTermId = term.id }
                                    }) { Text("مشاهده تاریخچه تغییرات") }
                                }
                                val variants = spellings.filter { it.termId == term.id }
                                if (variants.isNotEmpty()) Text("صورت‌های دیگر: ${variants.joinToString("، ") { it.spelling }}", style = MaterialTheme.typography.bodySmall)
                                termMeanings.forEach { meaning ->
                                    Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)) {
                                        Column(Modifier.fillMaxWidth().padding(10.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                            Text(meaning.meaning, style = MaterialTheme.typography.bodyMedium)
                                            val sourceLine = listOf(meaning.sourceTitle, meaning.sourceAuthor, meaning.sourcePage).filter { it.isNotBlank() }.joinToString("، ")
                                            if (sourceLine.isNotBlank()) Text("منبع: $sourceLine", style = MaterialTheme.typography.bodySmall)
                                            if (meaning.sourceUrl.isNotBlank()) Text("نشانی منبع: ${meaning.sourceUrl}", style = MaterialTheme.typography.bodySmall)
                                            if (meaning.usageQuote.isNotBlank()) Text("شاهد: ${meaning.usageQuote}", style = MaterialTheme.typography.bodySmall)
                                            if (isAdmin) {
                                                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                                    if (meaning.reviewStatus != GlossaryTermEntity.STATUS_VERIFIED) TextButton(onClick = {
                                                        scope.launch {
                                                            db.withTransaction {
                                                                val now = System.currentTimeMillis()
                                                                db.glossaryDao().updateMeaning(meaning.copy(reviewStatus = GlossaryTermEntity.STATUS_VERIFIED, updatedAt = now))
                                                                db.glossaryDao().insertHistory(GlossaryHistoryEntity(termId = term.id, action = "VERIFY_MEANING", beforeJson = JSONObject().put("status", meaning.reviewStatus).toString(), afterJson = JSONObject().put("meaningUid", meaning.uid).put("status", "VERIFIED").toString(), changedAt = now))
                                                            }
                                                            
                                                        }
                                                    }) { Text("تأیید معنی") }
                                                    TextButton(onClick = {
                                                        scope.launch {
                                                            db.withTransaction {
                                                                val now = System.currentTimeMillis()
                                                                val nextStatus = if (meaning.reviewStatus == GlossaryTermEntity.STATUS_REJECTED) GlossaryTermEntity.STATUS_PENDING else GlossaryTermEntity.STATUS_REJECTED
                                                                db.glossaryDao().updateMeaning(meaning.copy(reviewStatus = nextStatus, updatedAt = now))
                                                                db.glossaryDao().insertHistory(GlossaryHistoryEntity(termId = term.id, action = if (nextStatus == GlossaryTermEntity.STATUS_PENDING) "RESTORE_MEANING" else "REJECT_MEANING", beforeJson = JSONObject().put("status", meaning.reviewStatus).toString(), afterJson = JSONObject().put("meaningUid", meaning.uid).put("status", nextStatus).toString(), changedAt = now))
                                                            }
                                                            
                                                        }
                                                    }) { Text(if (meaning.reviewStatus == GlossaryTermEntity.STATUS_REJECTED) "بازگردانی به بررسی" else "رد معنی") }
                                                }
                                            }
                                        }
                                    }
                                }
                                if (isAdmin) {
                                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                        if (term.reviewStatus != GlossaryTermEntity.STATUS_VERIFIED) Button(onClick = {
                                            scope.launch {
                                                db.withTransaction {
                                                    val now = System.currentTimeMillis()
                                                    db.glossaryDao().updateTerm(term.copy(reviewStatus = GlossaryTermEntity.STATUS_VERIFIED, updatedAt = now))
                                                    db.glossaryDao().insertHistory(GlossaryHistoryEntity(termId = term.id, action = "VERIFY_TERM", beforeJson = JSONObject().put("status", term.reviewStatus).toString(), afterJson = "{\"status\":\"VERIFIED\"}", changedAt = now))
                                                }
                                                
                                            }
                                        }, modifier = Modifier.weight(1f)) { Text("تأیید مدخل") }
                                        TextButton(onClick = {
                                            scope.launch {
                                                db.withTransaction {
                                                    val now = System.currentTimeMillis()
                                                    val nextStatus = if (term.reviewStatus == GlossaryTermEntity.STATUS_REJECTED) GlossaryTermEntity.STATUS_PENDING else GlossaryTermEntity.STATUS_REJECTED
                                                    db.glossaryDao().updateTerm(term.copy(reviewStatus = nextStatus, updatedAt = now))
                                                    db.glossaryDao().insertHistory(GlossaryHistoryEntity(termId = term.id, action = if (nextStatus == GlossaryTermEntity.STATUS_PENDING) "RESTORE_TERM" else "REJECT_TERM", beforeJson = JSONObject().put("status", term.reviewStatus).toString(), afterJson = JSONObject().put("status", nextStatus).toString(), changedAt = now))
                                                }
                                                
                                            }
                                        }) { Text(if (term.reviewStatus == GlossaryTermEntity.STATUS_REJECTED) "بازگردانی به بررسی" else "رد مدخل") }
                                    }
                                }
                            }
                        }
                    }
                }
                if (visibleTerms.isEmpty()) {
                    item { Text(if (isAdmin) "واژه‌ای مطابق جست‌وجو پیدا نشد. برای ثبت واژه جدید از دکمه افزودن استفاده کنید." else "هنوز واژه تأییدشده‌ای برای نمایش وجود ندارد.", color = MaterialTheme.colorScheme.onSurfaceVariant) }
                }
            }
        }
    }

    historyTermId?.let { termId ->
        AlertDialog(
            onDismissRequest = { historyTermId = null },
            title = { Text("تاریخچه تغییرات مدخل") },
            text = {
                Column(Modifier.heightIn(max = 420.dp).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    if (historyRows.isEmpty()) Text("برای این مدخل سابقه‌ای ثبت نشده است.")
                    historyRows.forEach { item ->
                        Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)) {
                            Column(Modifier.fillMaxWidth().padding(10.dp), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                                Text(item.action, fontWeight = FontWeight.SemiBold)
                                Text(java.text.DateFormat.getDateTimeInstance().format(java.util.Date(item.changedAt)), style = MaterialTheme.typography.labelSmall)
                                if (item.beforeJson.isNotBlank() && item.beforeJson != "{}") Text("قبل: ${item.beforeJson}", style = MaterialTheme.typography.bodySmall)
                                if (item.afterJson.isNotBlank() && item.afterJson != "{}") Text("بعد: ${item.afterJson}", style = MaterialTheme.typography.bodySmall)
                            }
                        }
                    }
                }
            },
            confirmButton = { TextButton(onClick = { historyTermId = null }) { Text("بستن") } }
        )
    }

    importPreview?.let { preview ->
        AlertDialog(
            onDismissRequest = { if (!busy) importPreview = null },
            title = { Text("پیش‌نمایش بسته فرهنگ اصطلاحات") },
            text = {
                Column(Modifier.heightIn(max = 420.dp).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("شناسه بسته: ${preview.packageUid}")
                    Text("واژه‌ها: ${preview.terms.size}  |  معنی‌ها: ${preview.meaningCount}  |  صورت‌های نوشتاری: ${preview.spellingCount}")
                    Text("همه موارد جدید با وضعیت «در انتظار بررسی» ثبت می‌شوند و به‌صورت خودکار تأیید نخواهند شد.", style = MaterialTheme.typography.bodySmall)
                    preview.errors.forEach { Text("خطا: $it", color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall) }
                    preview.warnings.forEach { Text("هشدار: $it", color = MaterialTheme.colorScheme.tertiary, style = MaterialTheme.typography.bodySmall) }
                    preview.terms.take(12).forEach { term ->
                        Text("• ${term.headword} (${term.meanings.size} معنی)", style = MaterialTheme.typography.bodySmall)
                    }
                    if (preview.terms.size > 12) Text("و ${preview.terms.size - 12} واژه دیگر…", style = MaterialTheme.typography.bodySmall)
                }
            },
            confirmButton = {
                TextButton(enabled = preview.isValid && !busy, onClick = {
                    scope.launch {
                        busy = true
                        try {
                            val result = GlossaryImportService(db).apply(preview)
                            importPreview = null
                            
                            message = "ورود افزایشی انجام شد: ${result.addedTerms} واژه جدید، ${result.reusedTerms} واژه موجود، ${result.addedMeanings} معنی جدید؛ ${result.skippedMeanings} معنی تکراری و ${result.skippedSpellings} صورت نوشتاری تکراری رد شد."
                        } catch (e: Exception) {
                            message = "ورود بسته انجام نشد و هیچ تغییر ناقصی ذخیره نشد: ${e.message ?: "خطای نامشخص"}"
                        } finally { busy = false }
                    }
                }) { Text(if (busy) "در حال ورود…" else "تأیید و ورود بسته") }
            },
            dismissButton = { TextButton(enabled = !busy, onClick = { importPreview = null }) { Text("انصراف") } }
        )
    }

    if (showEditor && isAdmin) {
        var headword by remember { mutableStateOf("") }
        var meaning by remember { mutableStateOf("") }
        var spelling by remember { mutableStateOf("") }
        var partOfSpeech by remember { mutableStateOf("") }
        var pronunciation by remember { mutableStateOf("") }
        var etymology by remember { mutableStateOf("") }
        var source by remember { mutableStateOf("") }
        var author by remember { mutableStateOf("") }
        var page by remember { mutableStateOf("") }
        var sourceUrl by remember { mutableStateOf("") }
        var taziehOptions by remember { mutableStateOf(emptyList<TaziehEntity>()) }
        var roleOptions by remember { mutableStateOf(emptyList<RoleEntity>()) }
        var sectionOptions by remember { mutableStateOf(emptyList<SectionEntity>()) }
        var selectedTazieh by remember { mutableStateOf<TaziehEntity?>(null) }
        var selectedRole by remember { mutableStateOf<RoleEntity?>(null) }
        var selectedSection by remember { mutableStateOf<SectionEntity?>(null) }
        var taziehExpanded by remember { mutableStateOf(false) }
        var roleExpanded by remember { mutableStateOf(false) }
        var sectionExpanded by remember { mutableStateOf(false) }
        var quote by remember { mutableStateOf("") }
        var notes by remember { mutableStateOf("") }
        LaunchedEffect(Unit) {
            taziehOptions = db.taziehDao().getAll()
        }
        AlertDialog(
            onDismissRequest = { if (!busy) showEditor = false },
            title = { Text("ثبت واژه و معنی") },
            text = {
                Column(Modifier.heightIn(max = 560.dp).fillMaxWidth().verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(7.dp)) {
                    OutlinedTextField(headword, { headword = it }, label = { Text("واژه اصلی") }, singleLine = true)
                    OutlinedTextField(spelling, { spelling = it }, label = { Text("صورت نوشتاری دیگر (اختیاری)") }, singleLine = true)
                    OutlinedTextField(partOfSpeech, { partOfSpeech = it }, label = { Text("نوع واژه") }, singleLine = true)
                    OutlinedTextField(pronunciation, { pronunciation = it }, label = { Text("تلفظ") }, singleLine = true)
                    OutlinedTextField(etymology, { etymology = it }, label = { Text("ریشه‌شناسی") }, minLines = 2)
                    OutlinedTextField(meaning, { meaning = it }, label = { Text("معنی") }, minLines = 2)
                    OutlinedTextField(source, { source = it }, label = { Text("عنوان منبع (یا نشانی منبع الزامی است)") }, singleLine = true)
                    OutlinedTextField(author, { author = it }, label = { Text("پدیدآورنده") }, singleLine = true)
                    OutlinedTextField(page, { page = it }, label = { Text("صفحه / جلد") }, singleLine = true)
                    OutlinedTextField(sourceUrl, { sourceUrl = it }, label = { Text("نشانی اینترنتی منبع (اختیاری)") }, singleLine = true)
                    Text("تعیین محل دقیق کاربرد واژه", style = MaterialTheme.typography.titleSmall)
                    Box {
                        OutlinedButton(onClick = { taziehExpanded = true }, modifier = Modifier.fillMaxWidth()) {
                            Text(selectedTazieh?.title ?: "۱. انتخاب مجلس")
                        }
                        DropdownMenu(expanded = taziehExpanded, onDismissRequest = { taziehExpanded = false }) {
                            taziehOptions.forEach { item ->
                                DropdownMenuItem(text = { Text(item.title) }, onClick = {
                                    selectedTazieh = item
                                    selectedRole = null
                                    selectedSection = null
                                    roleOptions = emptyList()
                                    sectionOptions = emptyList()
                                    taziehExpanded = false
                                    scope.launch { roleOptions = db.roleDao().getByTazieh(item.id) }
                                })
                            }
                        }
                    }
                    Box {
                        OutlinedButton(onClick = { if (selectedTazieh != null) roleExpanded = true }, enabled = selectedTazieh != null, modifier = Modifier.fillMaxWidth()) {
                            Text(selectedRole?.title ?: "۲. انتخاب نقش")
                        }
                        DropdownMenu(expanded = roleExpanded, onDismissRequest = { roleExpanded = false }) {
                            roleOptions.forEach { item ->
                                DropdownMenuItem(text = { Text(item.title) }, onClick = {
                                    selectedRole = item
                                    selectedSection = null
                                    sectionOptions = emptyList()
                                    roleExpanded = false
                                    scope.launch { sectionOptions = db.sectionDao().getByRole(item.id) }
                                })
                            }
                        }
                    }
                    Box {
                        OutlinedButton(onClick = { if (selectedRole != null) sectionExpanded = true }, enabled = selectedRole != null, modifier = Modifier.fillMaxWidth()) {
                            Text(selectedSection?.title ?: "۳. انتخاب بخش دقیق")
                        }
                        DropdownMenu(expanded = sectionExpanded, onDismissRequest = { sectionExpanded = false }) {
                            sectionOptions.forEach { item ->
                                DropdownMenuItem(text = { Text(item.title.ifBlank { "بخش ${item.orderIndex + 1}" }) }, onClick = {
                                    selectedSection = item
                                    sectionExpanded = false
                                })
                            }
                        }
                    }
                    OutlinedTextField(quote, { quote = it }, label = { Text("شاهد کاربرد در متن (اختیاری)") }, minLines = 2)
                    OutlinedTextField(notes, { notes = it }, label = { Text("یادداشت تکمیلی مدخل (اختیاری)") }, minLines = 2)
                }
            },
            confirmButton = {
                TextButton(enabled = !busy && headword.isNotBlank() && meaning.isNotBlank() && selectedTazieh != null && selectedRole != null && selectedSection != null && (source.isNotBlank() || sourceUrl.isNotBlank()), onClick = {
                    scope.launch {
                        busy = true
                        message = null
                        try {
                            val normalized = normalizeGlossaryEntry(headword)
                            require(normalized.isNotBlank()) { "واژه معتبر نیست." }
                            require(source.isNotBlank() || sourceUrl.isNotBlank()) { "ثبت عنوان منبع یا نشانی منبع الزامی است." }
                            db.withTransaction {
                                val section = selectedSection ?: error("بخش دقیق انتخاب نشده است.")
                                val contextRole = selectedRole ?: error("نقش انتخاب نشده است.")
                                val contextTazieh = selectedTazieh ?: error("مجلس انتخاب نشده است.")
                                require(section.roleId == contextRole.id && contextRole.taziehId == contextTazieh.id) {
                                    "زنجیره مجلس، نقش و بخش تغییر کرده است؛ دوباره انتخاب کنید."
                                }
                                val dao = db.glossaryDao()
                                val existing = dao.byNormalizedHeadword(normalized)
                                val now = System.currentTimeMillis()
                                val term = existing ?: run {
                                    val draft = GlossaryTermEntity(
                                        uid = "term-${UUID.randomUUID()}", headword = headword.trim(), normalizedHeadword = normalized,
                                        partOfSpeech = partOfSpeech.trim(), pronunciation = pronunciation.trim(), etymology = etymology.trim(), notes = notes.trim(),
                                        createdAt = now, updatedAt = now
                                    )
                                    draft.copy(id = dao.insertTerm(draft))
                                }
                                if (existing != null && (partOfSpeech.isNotBlank() || pronunciation.isNotBlank() || etymology.isNotBlank() || notes.isNotBlank())) {
                                    dao.updateTerm(term.copy(
                                        partOfSpeech = partOfSpeech.ifBlank { term.partOfSpeech }.trim(),
                                        pronunciation = pronunciation.ifBlank { term.pronunciation }.trim(),
                                        etymology = etymology.ifBlank { term.etymology }.trim(),
                                        notes = notes.ifBlank { term.notes }.trim(), updatedAt = now
                                    ))
                                }
                                if (spelling.isNotBlank()) {
                                    val normalizedSpelling = normalizeGlossaryEntry(spelling)
                                    require(normalizedSpelling != normalized) { "صورت نوشتاری با واژه اصلی یکسان است." }
                                    if (dao.spellings(term.id).none { it.normalizedSpelling == normalizedSpelling }) {
                                        dao.insertSpelling(GlossarySpellingEntity(termId = term.id, spelling = spelling.trim(), normalizedSpelling = normalizedSpelling))
                                    }
                                }
                                require(dao.duplicateMeaning(term.id, meaning.trim(), source.trim(), author.trim(), page.trim(), section.uid) == null) { "این معنی با همین مشخصات منبع قبلاً ثبت شده است." }
                                val item = GlossaryMeaningEntity(
                                    uid = "meaning-${UUID.randomUUID()}", termId = term.id, meaning = meaning.trim(),
                                    sourceTitle = source.trim(), sourceAuthor = author.trim(), sourcePage = page.trim(), sourceUrl = sourceUrl.trim(),
                                    usageTaziehUid = contextTazieh.uid, usageVersionUid = contextRole.versionUid.ifBlank { null },
                                    usageRoleUid = contextRole.uid, usageSectionUid = section.uid, usageQuote = quote.trim(),
                                    createdAt = now, updatedAt = now
                                )
                                dao.insertMeaning(item)
                                dao.insertHistory(GlossaryHistoryEntity(
                                    termId = term.id, action = if (existing == null) "CREATE_TERM_AND_MEANING" else "ADD_MEANING",
                                    beforeJson = "{}", afterJson = JSONObject().put("headword", term.headword).put("meaning", item.meaning).put("source", item.sourceTitle).toString(), changedAt = now
                                ))
                            }
                            showEditor = false
                            
                            message = "اطلاعات به‌صورت پیشنهادی ثبت شد؛ پس از تأیید مدیر در نمایش عمومی قرار می‌گیرد."
                        } catch (e: Exception) {
                            message = "ثبت انجام نشد: ${e.message ?: "خطای نامشخص"}"
                        } finally { busy = false }
                    }
                }) { Text(if (busy) "در حال ثبت…" else "ثبت") }
            },
            dismissButton = { TextButton(onClick = { showEditor = false }) { Text("انصراف") } }
        )
    }
}
