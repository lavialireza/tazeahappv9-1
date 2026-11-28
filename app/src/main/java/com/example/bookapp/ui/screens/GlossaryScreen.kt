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
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Delete
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
import java.text.Collator
import java.util.Locale

private fun normalizeGlossaryEntry(value: String): String = value.trim()
    .replace('ي', 'ی').replace('ك', 'ک').replace('ۀ', 'ه').replace('ة', 'ه')
    .replace(Regex("[\\u064B-\\u065F\\u0670]"), "")
    .replace(Regex("\\s+"), " ")

/**
 * صفحه واحد واژه‌نامه تعزیه برای Admin و Viewer.
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
    val allMeaningSources by db.glossaryDao().observeMeaningSources().collectAsState(initial = emptyList())
    val allWitnesses by db.glossaryDao().observeWitnesses().collectAsState(initial = emptyList())
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
    var pendingExcelBytes by remember { mutableStateOf<ByteArray?>(null) }
    var editingTerm by remember { mutableStateOf<GlossaryTermEntity?>(null) }
    var editingMeaning by remember { mutableStateOf<GlossaryMeaningEntity?>(null) }
    var deletingTerm by remember { mutableStateOf<GlossaryTermEntity?>(null) }
    var deletingMeaning by remember { mutableStateOf<GlossaryMeaningEntity?>(null) }
    var usageLabels by remember { mutableStateOf<Map<String, String>>(emptyMap()) }
    var deletingTermHistoryCount by remember { mutableStateOf(0) }

    LaunchedEffect(terms, selectedTermId) {
        if (selectedTermId != null && terms.none { it.id == selectedTermId }) selectedTermId = null
    }

    LaunchedEffect(allMeanings) {
        val labels = mutableMapOf<String, String>()
        allMeanings.forEach { item ->
            val sectionUid = item.usageSectionUid
            if (!sectionUid.isNullOrBlank()) {
                val section = runCatching { db.sectionDao().getByUid(sectionUid) }.getOrNull()
                if (section != null) {
                    val role = runCatching { db.roleDao().getById(section.roleId) }.getOrNull()
                    val tazieh = role?.let { runCatching { db.taziehDao().getById(it.taziehId) }.getOrNull() }
                    val field = tazieh?.let { runCatching { db.fieldDao().getAll().firstOrNull { f -> f.id == it.fieldId } }.getOrNull() }
                    labels[item.uid] = listOf(
                        tazieh?.title?.takeIf { it.isNotBlank() }?.let { "مجلس: $it" },
                        role?.title?.takeIf { it.isNotBlank() }?.let { "نقش: $it" },
                        section.title.takeIf { it.isNotBlank() }?.let { "بخش: $it" }
                    ).filterNotNull().joinToString("، ")
                }
            }
        }
        usageLabels = labels
    }

    val importLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null && isAdmin) {
            scope.launch {
                busy = true
                try {
                    val bytes = context.contentResolver.openInputStream(uri)?.use { it.readBytes() }
                        ?: error("خواندن فایل انتخاب‌شده ممکن نشد.")
                    val displayName = uri.lastPathSegment?.substringAfterLast('/') ?: "glossary.json"
                    importPreview = GlossaryImportService(db).previewFile(bytes, displayName)
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

    val excelImportLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null && isAdmin) {
            scope.launch {
                busy = true
                try {
                    val json = context.contentResolver.openInputStream(uri)?.use { GlossaryExcelService.previewJson(it) }
                        ?: error("خواندن فایل Excel ممکن نشد.")
                    importPreview = GlossaryImportService(db).preview(json)
                    message = if (importPreview?.isValid == true) "پیش‌نمایش Excel آماده است؛ هنوز هیچ داده‌ای وارد نشده." else "فایل Excel خوانده شد؛ خطاها را در پیش‌نمایش بررسی کنید."
                } catch (e: Exception) {
                    message = "خواندن فایل Excel ناموفق بود: ${e.message ?: "خطای نامشخص"}"
                } finally { busy = false }
            }
        }
    }

    val excelExportLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet")
    ) { uri ->
        val bytes = pendingExcelBytes
        pendingExcelBytes = null
        if (uri != null && bytes != null && isAdmin) {
            scope.launch {
                busy = true
                try {
                    context.contentResolver.openOutputStream(uri)?.use { it.write(bytes) }
                        ?: error("ذخیره فایل Excel ممکن نشد.")
                    message = "فایل Excel واژه‌نامه ذخیره شد. هر ردیف نماینده یک معنی و کاربرد مستقل است."
                } catch (e: Exception) {
                    message = "ساخت فایل Excel ناموفق بود: ${e.message ?: "خطای نامشخص"}"
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
        }.sortedWith { left, right ->
            val persianCollator = Collator.getInstance(Locale("fa", "IR")).apply {
                strength = Collator.PRIMARY
                decomposition = Collator.CANONICAL_DECOMPOSITION
            }
            persianCollator.compare(left.headword.trim(), right.headword.trim())
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("واژه‌نامه تعزیه") },
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
            var showPackageTools by remember { mutableStateOf(false) }
            if (isAdmin) {
                Card(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
                ) {
                    Column(Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 4.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth().clickable { showPackageTools = !showPackageTools }.padding(vertical = 8.dp),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text("مدیریت بسته‌ها", fontWeight = FontWeight.SemiBold)
                            Text(if (showPackageTools) "بستن ▲" else "نمایش عملیات ▼", color = MaterialTheme.colorScheme.primary)
                        }
                        if (showPackageTools) {
                            Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    OutlinedButton(
                        onClick = { importLauncher.launch(arrayOf("application/json", "text/json", "text/plain", "application/zip", "application/x-zip-compressed", "*/*")) },
                        enabled = !busy,
                        modifier = Modifier.fillMaxWidth()
                    ) { Text(if (busy) "در حال بررسی بسته…" else "ورود بسته واژه‌ها (JSON یا ZIP برنامه جانبی)") }
                    OutlinedButton(
                        onClick = { excelImportLauncher.launch(arrayOf("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet", "application/octet-stream", "*/*")) },
                        enabled = !busy,
                        modifier = Modifier.fillMaxWidth()
                    ) { Text("ورود فایل Excel (.xlsx)") }
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
                    OutlinedButton(
                        onClick = {
                            scope.launch {
                                busy = true
                                try {
                                    pendingExcelBytes = GlossaryExcelService.export(db)
                                    excelExportLauncher.launch("glossary-${System.currentTimeMillis()}.xlsx")
                                } catch (e: Exception) {
                                    message = "تهیه خروجی Excel ممکن نشد: ${e.message ?: "خطای نامشخص"}"
                                } finally { busy = false }
                            }
                        },
                        enabled = !busy,
                        modifier = Modifier.fillMaxWidth()
                    ) { Text("خروجی Excel از واژه‌نامه") }
                    Text("در فایل Excel هر ردیف یک معنی و کاربرد مستقل است. ورود Excel مانند بسته ورودی، ابتدا پیش‌نمایش می‌شود و موارد واردشده در انتظار بررسی مدیر خواهند بود.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        }
                    }
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
                            else MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.72f)
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
                                            "DELETED" -> "حذف‌شده (بایگانی)"
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
                                            if (sourceLine.isNotBlank()) Text("منبع اصلی: $sourceLine", style = MaterialTheme.typography.bodySmall)
                                            if (meaning.sourceUrl.isNotBlank()) Text("نشانی منبع: ${meaning.sourceUrl}", style = MaterialTheme.typography.bodySmall)
                                            val extraSources = allMeaningSources.filter { it.meaningId == meaning.id }
                                            extraSources.forEach { src ->
                                                val label = listOf(src.title, src.author, src.publisher, src.year, src.page).filter { it.isNotBlank() }.joinToString("، ")
                                                Text("منبع مستقل: $label", style = MaterialTheme.typography.bodySmall)
                                                if (src.url.isNotBlank()) Text(src.url, style = MaterialTheme.typography.bodySmall)
                                                val sourceDetails = listOf(
                                                    src.language.takeIf { it.isNotBlank() }?.let { "زبان: $it" },
                                                    src.volume.takeIf { it.isNotBlank() }?.let { "جلد: $it" },
                                                    src.bibliographicInfo.takeIf { it.isNotBlank() }?.let { "مشخصات: $it" },
                                                    src.originalText.takeIf { it.isNotBlank() }?.let { "متن اصلی: $it" },
                                                    src.translation.takeIf { it.isNotBlank() }?.let { "ترجمه: $it" },
                                                    src.commentary.takeIf { it.isNotBlank() }?.let { "توضیحات: $it" }
                                                ).filterNotNull()
                                                sourceDetails.forEach { detail -> Text(detail, style = MaterialTheme.typography.bodySmall) }
                                                val sourceLocation = listOf(src.usageFieldTitle, src.usageTaziehTitle, src.usageRoleTitle, src.usageSectionTitle).filter { it.isNotBlank() }.joinToString(" ← ")
                                                if (sourceLocation.isNotBlank()) Text("محل کاربرد این منبع: $sourceLocation", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.primary)
                                            }
                                            if (meaning.authorOpinion.isNotBlank()) Text("نظر مؤلف: ${meaning.authorOpinion}", style = MaterialTheme.typography.bodySmall)
                                            if (meaning.usageQuote.isNotBlank()) Text("شاهد کاربرد: ${meaning.usageQuote}", style = MaterialTheme.typography.bodySmall)
                                            allWitnesses.filter { it.meaningId == meaning.id }.forEach { witness ->
                                                Text("شاهد مستقل: ${witness.text}", style = MaterialTheme.typography.bodySmall)
                                                val witnessSource = listOf(witness.sourceTitle, witness.sourcePage).filter { it.isNotBlank() }.joinToString("، ")
                                                if (witnessSource.isNotBlank()) Text("مأخذ شاهد: $witnessSource", style = MaterialTheme.typography.bodySmall)
                                                val witnessLocation = listOf(witness.usageFieldTitle, witness.usageTaziehTitle, witness.usageRoleTitle, witness.usageSectionTitle).filter { it.isNotBlank() }.joinToString(" ← ")
                                                if (witnessLocation.isNotBlank()) Text("محل کاربرد این شاهد: $witnessLocation", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.primary)
                                            }
                                            val usageLabel = usageLabels[meaning.uid].orEmpty()
                                            if (usageLabel.isNotBlank()) Text("محل کاربرد: $usageLabel", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                            if (isAdmin) {
                                                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                                    TextButton(onClick = { editingMeaning = meaning }) { Icon(Icons.Filled.Edit, contentDescription = null); Text("ویرایش کاربرد") }
                                                    TextButton(onClick = { deletingMeaning = meaning }) { Icon(Icons.Filled.Delete, contentDescription = null); Text("حذف کاربرد") }
                                                }
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
                                        TextButton(onClick = { editingTerm = term }) { Icon(Icons.Filled.Edit, contentDescription = null); Text("ویرایش واژه") }
                                        if (term.reviewStatus == "DELETED") TextButton(onClick = {
                                            scope.launch {
                                                db.withTransaction {
                                                    val now = System.currentTimeMillis()
                                                    db.glossaryDao().updateTerm(term.copy(reviewStatus = GlossaryTermEntity.STATUS_PENDING, updatedAt = now))
                                                    db.glossaryDao().insertHistory(GlossaryHistoryEntity(termId = term.id, action = "RESTORE_DELETED_TERM",
                                                        beforeJson = "{\"status\":\"DELETED\"}", afterJson = "{\"status\":\"PENDING\"}", changedAt = now))
                                                }
                                                message = "واژه به وضعیت بررسی بازگردانده شد."
                                            }
                                        }) { Text("بازیابی") }
                                        TextButton(onClick = {
                                            scope.launch {
                                                deletingTermHistoryCount = db.glossaryDao().history(term.id).size
                                                deletingTerm = term
                                            }
                                        }) { Icon(Icons.Filled.Delete, contentDescription = null); Text("حذف واژه") }
                                    }
                                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                        if (term.reviewStatus != GlossaryTermEntity.STATUS_VERIFIED && term.reviewStatus != "DELETED") Button(onClick = {
                                            scope.launch {
                                                db.withTransaction {
                                                    val now = System.currentTimeMillis()
                                                    db.glossaryDao().updateTerm(term.copy(reviewStatus = GlossaryTermEntity.STATUS_VERIFIED, updatedAt = now))
                                                    db.glossaryDao().insertHistory(GlossaryHistoryEntity(termId = term.id, action = "VERIFY_TERM", beforeJson = JSONObject().put("status", term.reviewStatus).toString(), afterJson = "{\"status\":\"VERIFIED\"}", changedAt = now))
                                                }
                                                
                                            }
                                        }, modifier = Modifier.weight(1f)) { Text("تأیید مدخل") }
                                        if (term.reviewStatus != "DELETED") TextButton(onClick = {
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
            title = { Text("پیش‌نمایش بسته واژه‌نامه") },
            text = {
                Column(Modifier.heightIn(max = 420.dp).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("شناسه بسته: ${preview.packageUid}")
                    Text("واژه‌ها: ${preview.terms.size}  |  معنی‌ها: ${preview.meaningCount}  |  صورت‌های نوشتاری: ${preview.spellingCount}")
                    Text(
                        if (preview.preapproved) "این بسته با تأیید نهایی LexText صادر شده است؛ فقط موارد تأییدشده مبدأ در برنامه اصلی نیز تأییدشده ثبت می‌شوند."
                        else "موارد این بسته با وضعیت «در انتظار بررسی» ثبت می‌شوند و به‌صورت خودکار تأیید نخواهند شد.",
                        style = MaterialTheme.typography.bodySmall
                    )
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
                                require(dao.duplicateMeaning(term.id, meaning.trim(), source.trim(), author.trim(), page.trim(), sourceUrl.trim(), section.uid) == null) { "این معنی با همین مشخصات منبع قبلاً ثبت شده است." }
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

    editingTerm?.let { original ->
        var headword by remember(original.id) { mutableStateOf(original.headword) }
        var partOfSpeech by remember(original.id) { mutableStateOf(original.partOfSpeech) }
        var pronunciation by remember(original.id) { mutableStateOf(original.pronunciation) }
        var etymology by remember(original.id) { mutableStateOf(original.etymology) }
        var notes by remember(original.id) { mutableStateOf(original.notes) }
        AlertDialog(
            onDismissRequest = { if (!busy) editingTerm = null },
            title = { Text("ویرایش واژه") },
            text = {
                Column(Modifier.heightIn(max = 420.dp).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(headword, { headword = it }, label = { Text("عنوان واژه") }, singleLine = true)
                    OutlinedTextField(partOfSpeech, { partOfSpeech = it }, label = { Text("نوع واژه") }, singleLine = true)
                    OutlinedTextField(pronunciation, { pronunciation = it }, label = { Text("تلفظ") }, singleLine = true)
                    OutlinedTextField(etymology, { etymology = it }, label = { Text("ریشه‌شناسی") }, minLines = 2)
                    OutlinedTextField(notes, { notes = it }, label = { Text("یادداشت") }, minLines = 2)
                }
            },
            confirmButton = {
                TextButton(enabled = !busy && headword.isNotBlank(), onClick = {
                    scope.launch {
                        busy = true
                        try {
                            val normalized = normalizeGlossaryEntry(headword)
                            db.withTransaction {
                                val dao = db.glossaryDao()
                                val collision = dao.byNormalizedHeadword(normalized)
                                require(collision == null || collision.id == original.id) { "واژه دیگری با همین عنوان وجود دارد." }
                                val changed = original.copy(headword = headword.trim(), normalizedHeadword = normalized,
                                    partOfSpeech = partOfSpeech.trim(), pronunciation = pronunciation.trim(),
                                    etymology = etymology.trim(), notes = notes.trim(), updatedAt = System.currentTimeMillis())
                                dao.updateTerm(changed)
                                dao.insertHistory(GlossaryHistoryEntity(termId = original.id, action = "EDIT_TERM",
                                    beforeJson = JSONObject().put("headword", original.headword).toString(),
                                    afterJson = JSONObject().put("headword", changed.headword).toString(), changedAt = System.currentTimeMillis()))
                            }
                            editingTerm = null
                            message = "واژه ویرایش شد."
                        } catch (e: Exception) { message = "ویرایش انجام نشد: ${e.message ?: "خطای نامشخص"}" }
                        finally { busy = false }
                    }
                }) { Text("ذخیره") }
            },
            dismissButton = { TextButton(onClick = { editingTerm = null }) { Text("انصراف") } }
        )
    }

    editingMeaning?.let { original ->
        var meaningText by remember(original.id) { mutableStateOf(original.meaning) }
        var sourceTitle by remember(original.id) { mutableStateOf(original.sourceTitle) }
        var sourceAuthor by remember(original.id) { mutableStateOf(original.sourceAuthor) }
        var sourcePage by remember(original.id) { mutableStateOf(original.sourcePage) }
        var sourceUrl by remember(original.id) { mutableStateOf(original.sourceUrl) }
        var quote by remember(original.id) { mutableStateOf(original.usageQuote) }
        var taziehOptions by remember(original.id) { mutableStateOf(emptyList<TaziehEntity>()) }
        var roleOptions by remember(original.id) { mutableStateOf(emptyList<RoleEntity>()) }
        var sectionOptions by remember(original.id) { mutableStateOf(emptyList<SectionEntity>()) }
        var selectedTazieh by remember(original.id) { mutableStateOf<TaziehEntity?>(null) }
        var selectedRole by remember(original.id) { mutableStateOf<RoleEntity?>(null) }
        var selectedSection by remember(original.id) { mutableStateOf<SectionEntity?>(null) }
        var taziehExpanded by remember(original.id) { mutableStateOf(false) }
        var roleExpanded by remember(original.id) { mutableStateOf(false) }
        var sectionExpanded by remember(original.id) { mutableStateOf(false) }
        LaunchedEffect(original.uid) {
            taziehOptions = db.taziehDao().getAll()
            val section = original.usageSectionUid?.let { db.sectionDao().getByUid(it) }
            val role = section?.let { db.roleDao().getById(it.roleId) }
            val tazieh = role?.let { db.taziehDao().getById(it.taziehId) }
            selectedTazieh = tazieh
            selectedRole = role
            selectedSection = section
            if (tazieh != null) roleOptions = db.roleDao().getByTazieh(tazieh.id)
            if (role != null) sectionOptions = db.sectionDao().getByRole(role.id)
        }
        AlertDialog(
            onDismissRequest = { if (!busy) editingMeaning = null },
            title = { Text("ویرایش معنی و کاربرد") },
            text = {
                Column(Modifier.heightIn(max = 460.dp).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(meaningText, { meaningText = it }, label = { Text("معنی") }, minLines = 2)
                    OutlinedTextField(sourceTitle, { sourceTitle = it }, label = { Text("منبع") }, singleLine = true)
                    OutlinedTextField(sourceAuthor, { sourceAuthor = it }, label = { Text("پدیدآورنده منبع") }, singleLine = true)
                    OutlinedTextField(sourcePage, { sourcePage = it }, label = { Text("صفحه / جلد") }, singleLine = true)
                    OutlinedTextField(sourceUrl, { sourceUrl = it }, label = { Text("نشانی منبع") }, singleLine = true)
                    OutlinedTextField(quote, { quote = it }, label = { Text("شاهد کاربرد") }, minLines = 2)
                    Text("تغییر محل کاربرد", style = MaterialTheme.typography.titleSmall)
                    Box {
                        OutlinedButton(onClick = { taziehExpanded = true }, modifier = Modifier.fillMaxWidth()) { Text(selectedTazieh?.title ?: "انتخاب مجلس") }
                        DropdownMenu(expanded = taziehExpanded, onDismissRequest = { taziehExpanded = false }) {
                            taziehOptions.forEach { option -> DropdownMenuItem(text = { Text(option.title) }, onClick = {
                                selectedTazieh = option; selectedRole = null; selectedSection = null
                                roleOptions = emptyList(); sectionOptions = emptyList(); taziehExpanded = false
                                scope.launch { roleOptions = db.roleDao().getByTazieh(option.id) }
                            }) }
                        }
                    }
                    Box {
                        OutlinedButton(onClick = { if (selectedTazieh != null) roleExpanded = true }, enabled = selectedTazieh != null, modifier = Modifier.fillMaxWidth()) { Text(selectedRole?.title ?: "انتخاب نقش") }
                        DropdownMenu(expanded = roleExpanded, onDismissRequest = { roleExpanded = false }) {
                            roleOptions.forEach { option -> DropdownMenuItem(text = { Text(option.title) }, onClick = {
                                selectedRole = option; selectedSection = null; sectionOptions = emptyList(); roleExpanded = false
                                scope.launch { sectionOptions = db.sectionDao().getByRole(option.id) }
                            }) }
                        }
                    }
                    Box {
                        OutlinedButton(onClick = { if (selectedRole != null) sectionExpanded = true }, enabled = selectedRole != null, modifier = Modifier.fillMaxWidth()) { Text(selectedSection?.title ?: "انتخاب بخش") }
                        DropdownMenu(expanded = sectionExpanded, onDismissRequest = { sectionExpanded = false }) {
                            sectionOptions.forEach { option -> DropdownMenuItem(text = { Text(option.title.ifBlank { "بخش ${option.orderIndex + 1}" }) }, onClick = { selectedSection = option; sectionExpanded = false }) }
                        }
                    }
                    Text("محل کاربرد فعلی: ${usageLabels[original.uid].orEmpty()}", style = MaterialTheme.typography.bodySmall)
                }
            },
            confirmButton = {
                TextButton(enabled = !busy && meaningText.isNotBlank() && (sourceTitle.isNotBlank() || sourceUrl.isNotBlank()) && selectedTazieh != null && selectedRole != null && selectedSection != null, onClick = {
                    scope.launch {
                        busy = true
                        try {
                            db.withTransaction {
                                val chosenTazieh = selectedTazieh ?: error("مجلس انتخاب نشده است.")
                                val chosenRole = selectedRole ?: error("نقش انتخاب نشده است.")
                                val chosenSection = selectedSection ?: error("بخش انتخاب نشده است.")
                                require(chosenRole.taziehId == chosenTazieh.id && chosenSection.roleId == chosenRole.id) { "ارتباط مجلس، نقش و بخش معتبر نیست." }
                                val changed = original.copy(meaning = meaningText.trim(), sourceTitle = sourceTitle.trim(),
                                    sourceAuthor = sourceAuthor.trim(), sourcePage = sourcePage.trim(), sourceUrl = sourceUrl.trim(),
                                    usageTaziehUid = chosenTazieh.uid, usageVersionUid = chosenRole.versionUid.ifBlank { null },
                                    usageRoleUid = chosenRole.uid, usageSectionUid = chosenSection.uid,
                                    usageQuote = quote.trim(), updatedAt = System.currentTimeMillis())
                                db.glossaryDao().updateMeaning(changed)
                                db.glossaryDao().insertHistory(GlossaryHistoryEntity(termId = original.termId, action = "EDIT_MEANING_USAGE",
                                    beforeJson = JSONObject().put("meaning", original.meaning).put("source", original.sourceTitle).put("usageSectionUid", original.usageSectionUid).toString(),
                                    afterJson = JSONObject().put("meaning", changed.meaning).put("source", changed.sourceTitle).put("usageSectionUid", changed.usageSectionUid).toString(), changedAt = System.currentTimeMillis()))
                            }
                            editingMeaning = null
                            message = "معنی و اطلاعات کاربرد ویرایش شد."
                        } catch (e: Exception) { message = "ویرایش انجام نشد: ${e.message ?: "خطای نامشخص"}" }
                        finally { busy = false }
                    }
                }) { Text("ذخیره") }
            },
            dismissButton = { TextButton(onClick = { editingMeaning = null }) { Text("انصراف") } }
        )
    }

    deletingMeaning?.let { item ->
        AlertDialog(
            onDismissRequest = { if (!busy) deletingMeaning = null },
            title = { Text("حذف کاربرد") },
            text = { Text("فقط همین معنی و کاربرد مستقل حذف می‌شود. سایر معنی‌ها، کاربردهای دیگر و تاریخچه مدخل دست‌نخورده می‌مانند. این کار پس از تأیید نهایی انجام می‌شود.") },
            confirmButton = {
                TextButton(enabled = !busy, onClick = {
                    scope.launch {
                        busy = true
                        try {
                            db.withTransaction {
                                db.glossaryDao().insertHistory(GlossaryHistoryEntity(termId = item.termId, action = "DELETE_MEANING",
                                    beforeJson = JSONObject().put("meaningUid", item.uid).put("meaning", item.meaning)
                                        .put("sourceTitle", item.sourceTitle).put("sourceAuthor", item.sourceAuthor)
                                        .put("sourcePage", item.sourcePage).put("sourceUrl", item.sourceUrl)
                                        .put("usageTaziehUid", item.usageTaziehUid).put("usageVersionUid", item.usageVersionUid)
                                        .put("usageRoleUid", item.usageRoleUid).put("usageSectionUid", item.usageSectionUid)
                                        .put("usageQuote", item.usageQuote).put("reviewStatus", item.reviewStatus).toString(),
                                    afterJson = "{}", changedAt = System.currentTimeMillis()))
                                db.glossaryDao().deleteMeaning(item)
                            }
                            deletingMeaning = null
                            message = "کاربرد انتخاب‌شده حذف شد؛ سایر کاربردها باقی ماندند."
                        } catch (e: Exception) { message = "حذف انجام نشد: ${e.message ?: "خطای نامشخص"}" }
                        finally { busy = false }
                    }
                }) { Text("تأیید حذف") }
            },
            dismissButton = { TextButton(onClick = { deletingMeaning = null }) { Text("انصراف") } }
        )
    }

    deletingTerm?.let { item ->
        val linkedMeanings = allMeanings.count { it.termId == item.id }
        val historyCount = deletingTermHistoryCount
        AlertDialog(
            onDismissRequest = { if (!busy) deletingTerm = null },
            title = { Text("حذف کنترل‌شده واژه") },
            text = {
                Text("این واژه $linkedMeanings معنی/کاربرد و $historyCount سابقه ثبت‌شده دارد. برای جلوگیری از نابودی ارتباط‌ها و تاریخچه، حذف به‌صورت بایگانی نرم انجام می‌شود: واژه از نمایش عمومی خارج می‌شود، اما معنی‌ها، کاربردها، صورت‌های نوشتاری و سوابق آن حفظ می‌شوند. در صورت نیاز مدیر می‌تواند آن را دوباره فعال کند.")
            },
            confirmButton = {
                TextButton(enabled = !busy && item.reviewStatus != "DELETED", onClick = {
                    scope.launch {
                        busy = true
                        try {
                            db.withTransaction {
                                val now = System.currentTimeMillis()
                                db.glossaryDao().updateTerm(item.copy(reviewStatus = "DELETED", updatedAt = now))
                                db.glossaryDao().insertHistory(GlossaryHistoryEntity(termId = item.id, action = "SOFT_DELETE_TERM",
                                    beforeJson = JSONObject().put("status", item.reviewStatus).toString(),
                                    afterJson = JSONObject().put("status", "DELETED").put("linkedMeaningsPreserved", linkedMeanings).toString(), changedAt = now))
                            }
                            deletingTerm = null
                            message = "واژه بایگانی شد؛ همه کاربردها و سوابق حفظ شدند."
                        } catch (e: Exception) { message = "بایگانی انجام نشد: ${e.message ?: "خطای نامشخص"}" }
                        finally { busy = false }
                    }
                }) { Text(if (item.reviewStatus == "DELETED") "قبلاً بایگانی شده" else "تأیید بایگانی واژه") }
            },
            dismissButton = { TextButton(onClick = { deletingTerm = null }) { Text("بستن") } }
        )
    }

}
