package com.example.bookapp.ui.screens

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.room.withTransaction
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.example.bookapp.data.*
import kotlinx.coroutines.launch
import org.json.JSONObject

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GlossaryManagementScreen(db: AppDatabase, onBack: () -> Unit) {
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    var message by remember { mutableStateOf<String?>(null) }
    var busy by remember { mutableStateOf(false) }
    var headword by remember { mutableStateOf("") }
    var meaning by remember { mutableStateOf("") }
    var source by remember { mutableStateOf("") }
    var author by remember { mutableStateOf("") }
    var page by remember { mutableStateOf("") }
    var quote by remember { mutableStateOf("") }
    var spelling by remember { mutableStateOf("") }
    var partOfSpeech by remember { mutableStateOf("") }
    var pronunciation by remember { mutableStateOf("") }
    var etymology by remember { mutableStateOf("") }
    var notes by remember { mutableStateOf("") }
    var sourceUrl by remember { mutableStateOf("") }
    var taziehUid by remember { mutableStateOf("") }
    var versionUid by remember { mutableStateOf("") }
    var roleUid by remember { mutableStateOf("") }
    var sectionUid by remember { mutableStateOf("") }
    var terms by remember { mutableStateOf(emptyList<GlossaryTermEntity>()) }
    var selected by remember { mutableStateOf<GlossaryTermEntity?>(null) }
    var selectedMeanings by remember { mutableStateOf(emptyList<GlossaryMeaningEntity>()) }
    var selectedSpellings by remember { mutableStateOf(emptyList<GlossarySpellingEntity>()) }
    var editingTerm by remember { mutableStateOf<GlossaryTermEntity?>(null) }
    var editingMeaning by remember { mutableStateOf<GlossaryMeaningEntity?>(null) }
    var editingSpelling by remember { mutableStateOf<GlossarySpellingEntity?>(null) }
    var statusFilter by remember { mutableStateOf("ALL") }

    suspend fun reload() {
        terms = if (statusFilter == "ALL") db.glossaryDao().allTerms() else db.glossaryDao().termsByStatus(statusFilter)
        selected?.let { selectedMeanings = db.glossaryDao().meanings(it.id); selectedSpellings = db.glossaryDao().spellings(it.id) }
    }
    LaunchedEffect(statusFilter) { reload() }

    val importLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) scope.launch {
            busy = true
            message = null
            try {
                val bytes = context.contentResolver.openInputStream(uri)?.use { it.readBytes() }
                    ?: error("خواندن فایل امکان‌پذیر نیست.")
                val result = GlossaryJsonTransfer(context, db, db.glossaryDao()).importJson(bytes)
                reload()
                message = if (result.alreadyImported) {
                    "این فایل قبلاً وارد شده است؛ تغییری اعمال نشد."
                } else {
                    "ورود انجام شد: ${result.termsAdded} مدخل، ${result.meaningsAdded} معنی و ${result.spellingsAdded} صورت نوشتاری جدید. همه موارد واردشده در انتظار بررسی هستند."
                }
            } catch (e: Exception) {
                message = "ورود فایل انجام نشد: ${e.message ?: "خطای نامشخص"}"
            } finally { busy = false }
        }
    }
    val exportLauncher = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/json")) { uri ->
        if (uri != null) scope.launch {
            busy = true
            message = null
            try {
                val bytes = GlossaryJsonTransfer(context, db, db.glossaryDao()).exportVerifiedJson()
                context.contentResolver.openOutputStream(uri)?.use { it.write(bytes) }
                    ?: error("ذخیره فایل امکان‌پذیر نیست.")
                message = "خروجی JSON واژه‌های تأییدشده ذخیره شد."
            } catch (e: Exception) {
                message = "تهیه خروجی انجام نشد: ${e.message ?: "خطای نامشخص"}"
            } finally { busy = false }
        }
    }


    Scaffold(topBar = {
        TopAppBar(title = { Text("واژه‌نامه تخصصی تعزیه") }, navigationIcon = {
            IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "بازگشت") }
        })
    }) { padding ->
        LazyColumn(Modifier.fillMaxSize().padding(padding), contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            item {
                Text("ورود و خروجی فایل واژه‌نامه", style = MaterialTheme.typography.titleMedium)
                Text("فایل‌های تکمیلی به‌صورت افزایشی ادغام می‌شوند؛ اطلاعات قبلی حذف یا بازنویسی نمی‌شوند. فقط مدخل‌ها و معنی‌های تأییدشده در خروجی انتشار قرار می‌گیرند.", style = MaterialTheme.typography.bodySmall)
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(
                        enabled = !busy,
                        onClick = { importLauncher.launch(arrayOf("application/json", "text/json", "application/octet-stream")) },
                        modifier = Modifier.weight(1f)
                    ) { Text("ورود فایل JSON") }
                    OutlinedButton(
                        enabled = !busy,
                        onClick = { exportLauncher.launch("tazieh-glossary.json") },
                        modifier = Modifier.weight(1f)
                    ) { Text("خروجی انتشار") }
                }
            }
            item {
                Text("ثبت مدخل و معنی", style = MaterialTheme.typography.titleMedium)
                Text("هر مدخل می‌تواند چند معنی مستقل، صورت نوشتاری و شاهد مستند داشته باشد. اطلاعات تا زمان بررسی، پیشنهادی محسوب می‌شود.", style = MaterialTheme.typography.bodySmall)
                OutlinedTextField(headword, { headword = it }, label = { Text("واژه / سرمدخل") }, modifier = Modifier.fillMaxWidth(), singleLine = true)
                OutlinedTextField(spelling, { spelling = it }, label = { Text("صورت نوشتاری دیگر (اختیاری)") }, modifier = Modifier.fillMaxWidth(), singleLine = true)
                OutlinedTextField(partOfSpeech, { partOfSpeech = it }, label = { Text("نوع واژه (اسم، فعل، اصطلاح و…)") }, modifier = Modifier.fillMaxWidth())
                OutlinedTextField(pronunciation, { pronunciation = it }, label = { Text("تلفظ") }, modifier = Modifier.fillMaxWidth())
                OutlinedTextField(etymology, { etymology = it }, label = { Text("ریشه‌شناسی") }, modifier = Modifier.fillMaxWidth(), minLines = 2)
                OutlinedTextField(notes, { notes = it }, label = { Text("یادداشت مدخل (اختیاری)") }, modifier = Modifier.fillMaxWidth(), minLines = 2)
                OutlinedTextField(meaning, { meaning = it }, label = { Text("معنی") }, modifier = Modifier.fillMaxWidth(), minLines = 2)
                OutlinedTextField(source, { source = it }, label = { Text("منبع معنی / عنوان کتاب") }, modifier = Modifier.fillMaxWidth())
                OutlinedTextField(author, { author = it }, label = { Text("پدیدآورنده") }, modifier = Modifier.fillMaxWidth())
                OutlinedTextField(page, { page = it }, label = { Text("صفحه / جلد") }, modifier = Modifier.fillMaxWidth())
                OutlinedTextField(sourceUrl, { sourceUrl = it }, label = { Text("پیوند منبع (اختیاری)") }, modifier = Modifier.fillMaxWidth())
                Text("نشانی شاهد در مجموعه تعزیه (در صورت وجود)", style = MaterialTheme.typography.labelMedium)
                OutlinedTextField(taziehUid, { taziehUid = it }, label = { Text("شناسه تعزیه") }, modifier = Modifier.fillMaxWidth())
                OutlinedTextField(versionUid, { versionUid = it }, label = { Text("شناسه نسخه") }, modifier = Modifier.fillMaxWidth())
                OutlinedTextField(roleUid, { roleUid = it }, label = { Text("شناسه نقش") }, modifier = Modifier.fillMaxWidth())
                OutlinedTextField(sectionUid, { sectionUid = it }, label = { Text("شناسه بخش") }, modifier = Modifier.fillMaxWidth())
                OutlinedTextField(quote, { quote = it }, label = { Text("شاهد متنی یا کاربرد در تعزیه") }, modifier = Modifier.fillMaxWidth(), minLines = 2)
                Button(enabled = !busy && headword.isNotBlank() && meaning.isNotBlank(), onClick = {
                    scope.launch {
                        busy = true; message = null
                        try {
                            val repository = GlossaryRepository(db, db.glossaryDao())
                            val result = repository.addSuggestion(
                                GlossarySuggestionInput(
                                    headword = headword,
                                    meaning = meaning,
                                    spelling = spelling,
                                    partOfSpeech = partOfSpeech,
                                    pronunciation = pronunciation,
                                    etymology = etymology,
                                    notes = notes,
                                    sourceTitle = source,
                                    sourceAuthor = author,
                                    sourcePage = page,
                                    sourceUrl = sourceUrl,
                                    taziehUid = taziehUid,
                                    versionUid = versionUid,
                                    roleUid = roleUid,
                                    sectionUid = sectionUid,
                                    quote = quote
                                )
                            )
                            headword = ""; spelling = ""; meaning = ""; source = ""; author = ""; page = ""; quote = ""; partOfSpeech = ""; pronunciation = ""; etymology = ""; notes = ""; sourceUrl = ""; taziehUid = ""; versionUid = ""; roleUid = ""; sectionUid = ""
                            reload(); message = if (result.meaningCreated || result.termCreated || result.spellingCreated) "واژه و اطلاعات تکمیلی به‌صورت پیشنهادی ثبت شد؛ برای استفاده قطعی باید بررسی و تأیید شود." else "این معنی قبلاً ثبت شده بود؛ اطلاعات قبلی حفظ شد."
                        } catch (e: Exception) { message = "ثبت انجام نشد: ${e.message ?: "خطای نامشخص"}" }
                        finally { busy = false }
                    }
                }, modifier = Modifier.fillMaxWidth()) { Text(if (busy) "در حال ثبت…" else "ثبت واژه و معنی") }
                message?.let { Text(it, color = MaterialTheme.colorScheme.primary, style = MaterialTheme.typography.bodySmall) }
            }
            item {
                Text("مدخل‌های ثبت‌شده", style = MaterialTheme.typography.titleMedium)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FilterChip(selected = statusFilter == "ALL", onClick = { statusFilter = "ALL" }, label = { Text("همه") })
                    FilterChip(selected = statusFilter == GlossaryTermEntity.STATUS_PENDING, onClick = { statusFilter = GlossaryTermEntity.STATUS_PENDING }, label = { Text("در انتظار بررسی") })
                    FilterChip(selected = statusFilter == GlossaryTermEntity.STATUS_VERIFIED, onClick = { statusFilter = GlossaryTermEntity.STATUS_VERIFIED }, label = { Text("تأییدشده") })
                }
            }
            items(terms, key = { it.id }) { term ->
                OutlinedCard(Modifier.fillMaxWidth()) {
                    Column(Modifier.fillMaxWidth().padding(12.dp)) {
                        Text(term.headword, style = MaterialTheme.typography.titleMedium)
                        if (term.partOfSpeech.isNotBlank() || term.pronunciation.isNotBlank()) Text(listOf(term.partOfSpeech, term.pronunciation).filter { it.isNotBlank() }.joinToString(" • "), style = MaterialTheme.typography.bodySmall)
                        if (term.etymology.isNotBlank()) Text("ریشه: ${term.etymology}", style = MaterialTheme.typography.bodySmall)
                        Text("وضعیت: ${if (term.reviewStatus == GlossaryTermEntity.STATUS_VERIFIED) "تأییدشده" else if (term.reviewStatus == GlossaryTermEntity.STATUS_REJECTED) "ردشده" else "در انتظار بررسی"}", style = MaterialTheme.typography.labelSmall)
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            TextButton(onClick = { scope.launch { selected = term; selectedMeanings = db.glossaryDao().meanings(term.id); selectedSpellings = db.glossaryDao().spellings(term.id) } }) { Text(if (selected?.id == term.id) "بستن جزئیات" else "نمایش جزئیات") }
                            if (selected?.id == term.id) TextButton(onClick = { editingTerm = term }) { Text("ویرایش مدخل") }
                        }
                        if (selected?.id == term.id) {
                            if (term.notes.isNotBlank()) Text("یادداشت: ${term.notes}", style = MaterialTheme.typography.bodySmall)
                            Text("املای جایگزین", style = MaterialTheme.typography.labelLarge)
                            if (selectedSpellings.isEmpty()) Text("املای جایگزینی ثبت نشده است.", style = MaterialTheme.typography.bodySmall)
                            selectedSpellings.forEach { sp ->
                                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                    Text(sp.spelling)
                                    TextButton(onClick = { editingSpelling = sp }) { Text("ویرایش") }
                                }
                            }
                            selectedMeanings.forEach { m ->
                                Divider()
                                Text(m.meaning)
                                TextButton(onClick = { editingMeaning = m }) { Text("ویرایش معنی و اطلاعات آن") }
                                if (m.sourceTitle.isNotBlank()) Text("منبع: ${m.sourceTitle} ${m.sourceAuthor} ${m.sourcePage}", style = MaterialTheme.typography.bodySmall)
                                if (m.sourceUrl.isNotBlank()) Text("پیوند منبع: ${m.sourceUrl}", style = MaterialTheme.typography.bodySmall)
                                val anchors = listOf(m.usageTaziehUid?.let { "تعزیه: $it" }, m.usageVersionUid?.let { "نسخه: $it" }, m.usageRoleUid?.let { "نقش: $it" }, m.usageSectionUid?.let { "بخش: $it" }).filterNotNull()
                                if (anchors.isNotEmpty()) Text(anchors.joinToString(" | "), style = MaterialTheme.typography.bodySmall)
                                if (m.usageQuote.isNotBlank()) Text("شاهد: ${m.usageQuote}", style = MaterialTheme.typography.bodySmall)
                                Text("وضعیت معنی: ${if (m.reviewStatus == GlossaryTermEntity.STATUS_VERIFIED) "تأییدشده" else if (m.reviewStatus == GlossaryTermEntity.STATUS_REJECTED) "ردشده" else "در انتظار بررسی"}", style = MaterialTheme.typography.labelSmall)
                                Row {
                                    TextButton(onClick = { scope.launch { db.withTransaction { val now = System.currentTimeMillis(); db.glossaryDao().updateMeaning(m.copy(reviewStatus = GlossaryTermEntity.STATUS_VERIFIED, updatedAt = now)); db.glossaryDao().insertHistory(GlossaryHistoryEntity(termId = term.id, action = "VERIFY_MEANING", beforeJson = JSONObject().put("status", m.reviewStatus).toString(), afterJson = JSONObject().put("meaningUid", m.uid).put("status", "VERIFIED").toString(), changedAt = now)) }; selectedMeanings = db.glossaryDao().meanings(term.id) } }) { Text("تأیید معنی") }
                                    TextButton(onClick = { scope.launch { db.withTransaction { val now = System.currentTimeMillis(); db.glossaryDao().updateMeaning(m.copy(reviewStatus = GlossaryTermEntity.STATUS_REJECTED, updatedAt = now)); db.glossaryDao().insertHistory(GlossaryHistoryEntity(termId = term.id, action = "REJECT_MEANING", beforeJson = JSONObject().put("status", m.reviewStatus).toString(), afterJson = JSONObject().put("meaningUid", m.uid).put("status", "REJECTED").toString(), changedAt = now)) }; selectedMeanings = db.glossaryDao().meanings(term.id) } }) { Text("رد معنی") }
                                }
                            }
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                TextButton(onClick = { scope.launch { db.withTransaction { val before = JSONObject().put("status", term.reviewStatus).toString(); val now = System.currentTimeMillis(); db.glossaryDao().updateTerm(term.copy(reviewStatus = GlossaryTermEntity.STATUS_VERIFIED, updatedAt = now)); db.glossaryDao().insertHistory(GlossaryHistoryEntity(termId = term.id, action = "VERIFY_TERM", beforeJson = before, afterJson = "{\"status\":\"VERIFIED\"}", changedAt = now)) }; reload() } }) { Text("تأیید مدخل") }
                                TextButton(onClick = { scope.launch { db.withTransaction { val now = System.currentTimeMillis(); db.glossaryDao().updateTerm(term.copy(reviewStatus = GlossaryTermEntity.STATUS_REJECTED, updatedAt = now)); db.glossaryDao().insertHistory(GlossaryHistoryEntity(termId = term.id, action = "REJECT_TERM", beforeJson = "{}", afterJson = "{\"status\":\"REJECTED\"}", changedAt = now)) }; reload() } }) { Text("رد مدخل") }
                            }
                        }
                    }
                }
            }
            if (terms.isEmpty()) item { Text("هنوز مدخلی در این فهرست ثبت نشده است.") }
        }
    }

    editingTerm?.let { term ->
        GlossaryTermEditDialog(
            term = term,
            onDismiss = { editingTerm = null },
            onSave = { word, pos, pron, etym, note ->
                scope.launch {
                    busy = true
                    try {
                        GlossaryRepository(db, db.glossaryDao()).updateTermDetails(term.id, word, pos, pron, etym, note)
                        editingTerm = null
                        reload()
                        message = "اطلاعات مدخل ویرایش شد؛ شناسه و وضعیت تأیید آن حفظ شد."
                    } catch (e: Exception) {
                        message = "ویرایش انجام نشد: ${e.message ?: "خطای نامشخص"}"
                    } finally { busy = false }
                }
            }
        )
    }
    editingSpelling?.let { spellingRow ->
        GlossarySpellingEditDialog(
            spelling = spellingRow.spelling,
            onDismiss = { editingSpelling = null },
            onSave = { value ->
                scope.launch {
                    busy = true
                    try {
                        GlossaryRepository(db, db.glossaryDao()).updateSpellingDetails(spellingRow.id, value)
                        editingSpelling = null
                        reload()
                        message = "املای جایگزین ویرایش شد."
                    } catch (e: Exception) {
                        message = "ویرایش املای جایگزین انجام نشد: ${e.message ?: "خطای نامشخص"}"
                    } finally { busy = false }
                }
            }
        )
    }
    editingMeaning?.let { meaningRow ->
        GlossaryMeaningEditDialog(
            meaning = meaningRow,
            onDismiss = { editingMeaning = null },
            onSave = { value, src, srcAuthor, srcPage, srcUrl, evidence, taziehRef, versionRef, roleRef, sectionRef ->
                scope.launch {
                    busy = true
                    try {
                        GlossaryRepository(db, db.glossaryDao()).updateMeaningDetails(
                            meaningRow.id, value, src, srcAuthor, srcPage, srcUrl, evidence,
                            taziehRef, versionRef, roleRef, sectionRef
                        )
                        editingMeaning = null
                        reload()
                        message = "معنی و اطلاعات منبع ویرایش شد؛ وضعیت تأیید آن حفظ شد."
                    } catch (e: Exception) {
                        message = "ویرایش معنی انجام نشد: ${e.message ?: "خطای نامشخص"}"
                    } finally { busy = false }
                }
            }
        )
    }

}


@Composable
private fun GlossaryTermEditDialog(
    term: GlossaryTermEntity,
    onDismiss: () -> Unit,
    onSave: (String, String, String, String, String) -> Unit
) {
    var headword by remember(term.id) { mutableStateOf(term.headword) }
    var partOfSpeech by remember(term.id) { mutableStateOf(term.partOfSpeech) }
    var pronunciation by remember(term.id) { mutableStateOf(term.pronunciation) }
    var etymology by remember(term.id) { mutableStateOf(term.etymology) }
    var notes by remember(term.id) { mutableStateOf(term.notes) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("ویرایش مدخل واژه") },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(headword, { headword = it }, label = { Text("واژه / سرمدخل") }, modifier = Modifier.fillMaxWidth(), singleLine = true)
                OutlinedTextField(partOfSpeech, { partOfSpeech = it }, label = { Text("نوع واژه") }, modifier = Modifier.fillMaxWidth())
                OutlinedTextField(pronunciation, { pronunciation = it }, label = { Text("تلفظ") }, modifier = Modifier.fillMaxWidth())
                OutlinedTextField(etymology, { etymology = it }, label = { Text("ریشه‌شناسی") }, modifier = Modifier.fillMaxWidth())
                OutlinedTextField(notes, { notes = it }, label = { Text("یادداشت") }, modifier = Modifier.fillMaxWidth(), minLines = 2)
            }
        },
        confirmButton = { TextButton(enabled = headword.isNotBlank(), onClick = { onSave(headword, partOfSpeech, pronunciation, etymology, notes) }) { Text("ذخیره") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("انصراف") } }
    )
}

@Composable
private fun GlossarySpellingEditDialog(
    spelling: String,
    onDismiss: () -> Unit,
    onSave: (String) -> Unit
) {
    var value by remember(spelling) { mutableStateOf(spelling) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("ویرایش املای جایگزین") },
        text = { OutlinedTextField(value, { value = it }, label = { Text("املای جایگزین") }, modifier = Modifier.fillMaxWidth(), singleLine = true) },
        confirmButton = { TextButton(enabled = value.isNotBlank(), onClick = { onSave(value) }) { Text("ذخیره") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("انصراف") } }
    )
}

@Composable
private fun GlossaryMeaningEditDialog(
    meaning: GlossaryMeaningEntity,
    onDismiss: () -> Unit,
    onSave: (String, String, String, String, String, String, String, String, String, String) -> Unit
) {
    var value by remember(meaning.id) { mutableStateOf(meaning.meaning) }
    var source by remember(meaning.id) { mutableStateOf(meaning.sourceTitle) }
    var author by remember(meaning.id) { mutableStateOf(meaning.sourceAuthor) }
    var page by remember(meaning.id) { mutableStateOf(meaning.sourcePage) }
    var url by remember(meaning.id) { mutableStateOf(meaning.sourceUrl) }
    var quote by remember(meaning.id) { mutableStateOf(meaning.usageQuote) }
    var taziehUid by remember(meaning.id) { mutableStateOf(meaning.usageTaziehUid.orEmpty()) }
    var versionUid by remember(meaning.id) { mutableStateOf(meaning.usageVersionUid.orEmpty()) }
    var roleUid by remember(meaning.id) { mutableStateOf(meaning.usageRoleUid.orEmpty()) }
    var sectionUid by remember(meaning.id) { mutableStateOf(meaning.usageSectionUid.orEmpty()) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("ویرایش معنی و اطلاعات آن") },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(value, { value = it }, label = { Text("معنی") }, modifier = Modifier.fillMaxWidth(), minLines = 2)
                OutlinedTextField(source, { source = it }, label = { Text("عنوان منبع") }, modifier = Modifier.fillMaxWidth())
                OutlinedTextField(author, { author = it }, label = { Text("پدیدآورنده") }, modifier = Modifier.fillMaxWidth())
                OutlinedTextField(page, { page = it }, label = { Text("صفحه / جلد") }, modifier = Modifier.fillMaxWidth())
                OutlinedTextField(url, { url = it }, label = { Text("پیوند منبع") }, modifier = Modifier.fillMaxWidth())
                OutlinedTextField(taziehUid, { taziehUid = it }, label = { Text("شناسه تعزیه") }, modifier = Modifier.fillMaxWidth())
                OutlinedTextField(versionUid, { versionUid = it }, label = { Text("شناسه نسخه") }, modifier = Modifier.fillMaxWidth())
                OutlinedTextField(roleUid, { roleUid = it }, label = { Text("شناسه نقش") }, modifier = Modifier.fillMaxWidth())
                OutlinedTextField(sectionUid, { sectionUid = it }, label = { Text("شناسه بخش") }, modifier = Modifier.fillMaxWidth())
                OutlinedTextField(quote, { quote = it }, label = { Text("شاهد متنی") }, modifier = Modifier.fillMaxWidth(), minLines = 2)
            }
        },
        confirmButton = {
            TextButton(enabled = value.isNotBlank(), onClick = { onSave(value, source, author, page, url, quote, taziehUid, versionUid, roleUid, sectionUid) }) { Text("ذخیره") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("انصراف") } }
    )
}
