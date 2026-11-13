package com.example.bookapp.ui

import android.content.Context
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.FileOpen
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.navigation.NavHostController
import com.example.bookapp.data.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.File
import java.security.MessageDigest
import java.util.UUID
import java.util.zip.ZipInputStream

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DirectorOfficeScreen(db: AppDatabase, onBack: () -> Unit) {
    val context = LocalContext.current
    val dao = remember { db.directorDao() }
    val scope = rememberCoroutineScope()
    var plans by remember { mutableStateOf(emptyList<DirectorPlanEntity>()) }
    var selectedPlan by remember { mutableStateOf<DirectorPlanEntity?>(null) }
    var selectedVersion by remember { mutableStateOf<DirectorPlanVersionEntity?>(null) }
    var sources by remember { mutableStateOf(emptyList<DirectorSourceFileEntity>()) }
    var turns by remember { mutableStateOf(emptyList<DirectorTurnEntity>()) }
    var previewText by remember { mutableStateOf("") }
    var message by remember { mutableStateOf<String?>(null) }
    var busy by remember { mutableStateOf(false) }
    var planTitle by remember { mutableStateOf("") }
    var taziehUid by remember { mutableStateOf("") }
    var roleTitle by remember { mutableStateOf("") }
    var turnNumber by remember { mutableStateOf("1") }
    var turnText by remember { mutableStateOf("") }
    var refresh by remember { mutableIntStateOf(0) }
    var cueInstruction by remember { mutableStateOf("") }
    var cueTurnId by remember { mutableStateOf<Long?>(null) }
    var stageCues by remember { mutableStateOf(emptyList<DirectorStageCueEntity>()) }

    fun reload() {
        scope.launch {
            plans = dao.plans()
            selectedPlan?.let { current ->
                selectedPlan = dao.plan(current.id)
                sources = dao.sourceFiles(current.id)
                val versions = dao.versions(current.id)
                selectedVersion = selectedVersion?.let { old -> versions.firstOrNull { it.id == old.id } } ?: versions.firstOrNull()
                selectedVersion?.let {
                    turns = dao.turns(it.id)
                    stageCues = dao.stageCues(it.id)
                }
            }
        }
    }
    LaunchedEffect(refresh) { reload() }

    val importLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenMultipleDocuments()) { uris ->
        if (uris.isNotEmpty() && selectedPlan != null && selectedVersion != null) {
            scope.launch {
                busy = true
                try {
                    val importedSources = mutableListOf<Pair<Long, DirectorSourceFileEntity>>()
                    uris.forEach { uri ->
                        val imported = withContext(Dispatchers.IO) { importDirectorSource(context, uri, selectedPlan!!.id) }
                        val sourceId = dao.insertSourceFile(imported)
                        importedSources += sourceId to imported
                    }
                    val parsedFiles = importedSources.mapNotNull { (sourceId, source) ->
                        val role = source.originalName.substringBeforeLast('.', source.originalName).replace('_', ' ').trim()
                        val parsed = parseNumberedDirectorTurns(source.extractedText)
                        if (parsed.isEmpty()) null else Triple(sourceId, role.ifBlank { "نقش بدون نام" }, parsed)
                    }
                    var createdTurns = 0
                    var collectiveTurns = 0
                    var mismatchedTurns = 0
                    if (parsedFiles.isNotEmpty()) {
                        val grouped = linkedMapOf<Int, MutableList<Triple<Long, String, String>>>()
                        parsedFiles.forEach { (sourceId, role, parsed) ->
                            parsed.forEach { (number, text) -> grouped.getOrPut(number) { mutableListOf() }.add(Triple(sourceId, role, text)) }
                        }
                        var nextIndex = (dao.turns(selectedVersion!!.id).maxOfOrNull { it.sequenceIndex } ?: 0) + 1
                        grouped.toSortedMap().forEach { (number, entries) ->
                            val distinctRoles = entries.map { it.second }.distinct()
                            val isCollective = distinctRoles.size > 1
                            val distinctTexts = entries.map { it.third.trim().replace(Regex("\\s+"), " ") }.distinct()
                            val note = if (distinctTexts.size > 1) "اختلاف متن برای شماره مشترک؛ نیازمند بازبینی مدیر" else ""
                            if (distinctTexts.size > 1) mismatchedTurns++
                            if (isCollective) collectiveTurns++
                            val turnId = dao.insertTurn(DirectorTurnEntity(
                                versionId = selectedVersion!!.id,
                                turnNumber = number,
                                sequenceIndex = nextIndex++,
                                turnType = if (isCollective) DirectorTurnEntity.TYPE_COLLECTIVE else DirectorTurnEntity.TYPE_NORMAL,
                                title = if (isCollective) "نوبت جمعی: ${distinctRoles.joinToString("، ")}" else distinctRoles.firstOrNull().orEmpty(),
                                notes = note
                            ))
                            entries.distinctBy { it.second }.forEachIndexed { index, entry ->
                                dao.insertParticipant(DirectorParticipantEntity(turnId = turnId, roleUid = "source-${entry.first}", roleTitle = entry.second, orderIndex = index, isLead = index == 0))
                            }
                            entries.forEachIndexed { index, entry ->
                                dao.insertTextSegment(DirectorTextSegmentEntity(turnId = turnId, segmentIndex = index, text = entry.third, sourceFileId = entry.first))
                            }
                            createdTurns++
                        }
                    }
                    previewText = importedSources.firstOrNull()?.second?.extractedText.orEmpty()
                    message = if (parsedFiles.isEmpty()) {
                        "${importedSources.size} فایل اصلی ذخیره شد؛ از فایل‌های متنی قابل استخراج، نوبت شماره‌دار پیدا نشد. PDF در این نسخه فقط ذخیره می‌شود."
                    } else {
                        "$createdTurns نوبت ساخته شد؛ $collectiveTurns نوبت جمعی شناسایی شد. ${if (mismatchedTurns > 0) "$mismatchedTurns شماره با اختلاف متن نیازمند بازبینی است." else "اختلاف متن شناسایی نشد."}"
                    }
                    reload()
                } catch (e: Exception) {
                    message = "ورود فایل انجام نشد: ${e.message ?: "خطای نامشخص"}"
                } finally { busy = false }
            }
        }
    }


    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("دفتر معین البکا") },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "بازگشت") } },
                actions = { IconButton(onClick = { refresh++ }) { Icon(Icons.Default.Refresh, contentDescription = "بروزرسانی") } }
            )
        }
    ) { padding ->
        LazyColumn(Modifier.fillMaxSize().padding(padding).padding(14.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            item {
                Text("ساخت و آماده‌سازی دفتر معین البکا برای هر تعزیه", style = MaterialTheme.typography.bodyMedium)
                Text("فایل منبع بدون تغییر نگهداری می‌شود؛ اطلاعات دفتر مستقل از متن رسمی کتابخانه است.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            item {
                Card {
                    Column(Modifier.fillMaxWidth().padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text("ایجاد دفتر جدید", fontWeight = FontWeight.Bold)
                        OutlinedTextField(planTitle, { planTitle = it }, label = { Text("عنوان دفتر / مجلس") }, modifier = Modifier.fillMaxWidth(), singleLine = true)
                        OutlinedTextField(taziehUid, { taziehUid = it }, label = { Text("شناسه تعزیه (UID) موجود در کتابخانه") }, modifier = Modifier.fillMaxWidth(), singleLine = true)
                        Button(enabled = planTitle.isNotBlank() && taziehUid.isNotBlank() && !busy, onClick = {
                            scope.launch {
                                busy = true
                                try {
                                    val now = System.currentTimeMillis()
                                    val id = dao.insertPlan(DirectorPlanEntity(uid = UUID.randomUUID().toString(), taziehUid = taziehUid.trim(), title = planTitle.trim(), createdAt = now, updatedAt = now))
                                    val versionId = dao.insertVersion(DirectorPlanVersionEntity(planId = id, versionNumber = 1, title = "نسخه اول", createdAt = now))
                                    selectedPlan = dao.plan(id)
                                    selectedVersion = dao.version(versionId)
                                    planTitle = ""
                                    message = "دفتر جدید ایجاد شد."
                                    reload()
                                } catch (e: Exception) { message = "ایجاد دفتر ناموفق بود: ${e.message}" }
                                finally { busy = false }
                            }
                        }) { Icon(Icons.Default.Add, null); Spacer(Modifier.width(6.dp)); Text("ایجاد دفتر") }
                    }
                }
            }
            item { Text("دفترهای موجود", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold) }
            if (plans.isEmpty()) item { Text("هنوز دفتری ایجاد نشده است.", color = MaterialTheme.colorScheme.onSurfaceVariant) }
            items(plans, key = { it.id }) { plan ->
                Card(Modifier.fillMaxWidth().clickable {
                    scope.launch {
                        selectedPlan = plan
                        val versions = dao.versions(plan.id)
                        selectedVersion = versions.firstOrNull()
                        sources = dao.sourceFiles(plan.id)
                        turns = selectedVersion?.let { dao.turns(it.id) } ?: emptyList()
                    }
                }, colors = CardDefaults.cardColors(containerColor = if (selectedPlan?.id == plan.id) MaterialTheme.colorScheme.secondaryContainer else MaterialTheme.colorScheme.surfaceVariant)) {
                    Column(Modifier.padding(12.dp)) {
                        Text(plan.title, fontWeight = FontWeight.Bold)
                        Text("شناسه تعزیه: ${plan.taziehUid}", style = MaterialTheme.typography.bodySmall)
                        Text("وضعیت: ${if (plan.status == DirectorPlanEntity.STATUS_PUBLISHED) "منتشرشده" else "پیش‌نویس"}", style = MaterialTheme.typography.labelSmall)
                    }
                }
            }
            if (selectedPlan != null) {
                item {
                    Divider()
                    Text("مدیریت: ${selectedPlan!!.title}", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                    Text("نسخه فعال: ${selectedVersion?.title ?: "ندارد"}", style = MaterialTheme.typography.bodySmall)
                    Button(onClick = { importLauncher.launch(arrayOf("text/plain", "application/pdf", "application/vnd.openxmlformats-officedocument.wordprocessingml.document", "application/octet-stream")) }, enabled = !busy) {
                        Icon(Icons.Default.FileOpen, null); Spacer(Modifier.width(6.dp)); Text("بارگذاری یک یا چند فایل نقش (PDF / TXT / DOCX)")
                    }
                }
                item {
                    Text("فایل‌های منبع (${sources.size})", fontWeight = FontWeight.Bold)
                    sources.forEach { source ->
                        ListItem(
                            headlineContent = { Text(source.originalName) },
                            supportingContent = { Text(if (source.extractedText.isBlank()) "ذخیره‌شده؛ متن استخراج نشده" else "متن استخراج‌شده: ${source.extractedText.length} نویسه") },
                            leadingContent = { Icon(Icons.Default.Description, null) },
                            modifier = Modifier.clickable { previewText = source.extractedText }
                        )
                    }
                }
                item {
                    Text("پیش‌نمایش و متن ورودی", fontWeight = FontWeight.Bold)
                    OutlinedTextField(previewText, { previewText = it }, label = { Text("متن استخراج‌شده / متن قابل اصلاح") }, modifier = Modifier.fillMaxWidth().heightIn(min = 140.dp), minLines = 5)
                    Text("PDF اسکن‌شده یا تصویری در این مرحله فقط به‌عنوان منبع نگهداری می‌شود؛ OCR و استخراج متن PDF در مرحله بعد اضافه خواهد شد.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                item {
                    Button(
                        enabled = selectedVersion != null && roleTitle.isNotBlank() && previewText.isNotBlank() && !busy,
                        onClick = {
                            val parsed = parseNumberedDirectorTurns(previewText)
                            if (parsed.isEmpty()) {
                                message = "شماره‌گذاری قابل تشخیصی پیدا نشد. قالب پیشنهادی: نوبت ۱ یا ۱- متن نوبت"
                            } else {
                                scope.launch {
                                    busy = true
                                    try {
                                        val version = selectedVersion!!
                                        var nextIndex = (dao.turns(version.id).maxOfOrNull { it.sequenceIndex } ?: 0) + 1
                                        parsed.forEach { part ->
                                            val turnId = dao.insertTurn(DirectorTurnEntity(versionId = version.id, turnNumber = part.first, sequenceIndex = nextIndex++, title = roleTitle.trim()))
                                            dao.insertParticipant(DirectorParticipantEntity(turnId = turnId, roleUid = "manual-${UUID.randomUUID()}", roleTitle = roleTitle.trim(), isLead = true))
                                            dao.insertTextSegment(DirectorTextSegmentEntity(turnId = turnId, segmentIndex = 0, text = part.second))
                                        }
                                        message = "${parsed.size} نوبت شماره‌گذاری‌شده به‌صورت پیش‌نویس اضافه شد؛ ترتیب و متن را بازبینی کنید."
                                        reload()
                                    } catch (e: Exception) { message = "تبدیل متن ناموفق بود: ${e.message}" }
                                    finally { busy = false }
                                }
                            }
                        }
                    ) { Text("تشخیص نوبت‌های شماره‌دار از متن") }
                }
                item {
                    Text("ساخت نوبت دستی از متن", fontWeight = FontWeight.Bold)
                    OutlinedTextField(roleTitle, { roleTitle = it }, label = { Text("نام نقش / شبیه‌خوان") }, modifier = Modifier.fillMaxWidth(), singleLine = true)
                    OutlinedTextField(turnNumber, { turnNumber = it.filter(Char::isDigit).take(6) }, label = { Text("شماره نوبت") }, modifier = Modifier.fillMaxWidth(), singleLine = true)
                    OutlinedTextField(turnText, { turnText = it }, label = { Text("متن این نوبت") }, modifier = Modifier.fillMaxWidth().heightIn(min = 120.dp), minLines = 4)
                    Button(enabled = selectedVersion != null && roleTitle.isNotBlank() && turnText.isNotBlank() && !busy, onClick = {
                        scope.launch {
                            busy = true
                            try {
                                val version = selectedVersion!!
                                val nextIndex = (dao.turns(version.id).maxOfOrNull { it.sequenceIndex } ?: 0) + 1
                                val number = turnNumber.toIntOrNull() ?: nextIndex
                                val turnId = dao.insertTurn(DirectorTurnEntity(versionId = version.id, turnNumber = number, sequenceIndex = nextIndex, title = roleTitle.trim()))
                                dao.insertParticipant(DirectorParticipantEntity(turnId = turnId, roleUid = "manual-${UUID.randomUUID()}", roleTitle = roleTitle.trim(), isLead = true))
                                dao.insertTextSegment(DirectorTextSegmentEntity(turnId = turnId, segmentIndex = 0, text = turnText.trim()))
                                turnText = ""
                                message = "نوبت $number به دفتر اضافه شد."
                                reload()
                            } catch (e: Exception) { message = "ثبت نوبت ناموفق بود: ${e.message}" }
                            finally { busy = false }
                        }
                    }) { Icon(Icons.Default.Add, null); Spacer(Modifier.width(6.dp)); Text("افزودن نوبت") }
                }
                item {
                    Divider()
                    Text("دستورهای صحنه", fontWeight = FontWeight.Bold)
                    Text("برای ثبت دستور، یک نوبت را انتخاب کنید. دستور «بلافاصله» مانع عبور خودکار به نوبت بعدی می‌شود.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    var expanded by remember { mutableStateOf(false) }
                    val selectedCueTurn = turns.firstOrNull { it.id == cueTurnId } ?: turns.firstOrNull()
                    ExposedDropdownMenuBox(expanded = expanded, onExpandedChange = { expanded = !expanded }) {
                        OutlinedTextField(
                            value = selectedCueTurn?.let { "نوبت ${it.turnNumber} — ${it.title}" } ?: "ابتدا نوبت بسازید",
                            onValueChange = {}, readOnly = true, label = { Text("نوبت مرتبط") },
                            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded) },
                            modifier = Modifier.menuAnchor().fillMaxWidth()
                        )
                        ExposedDropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
                            turns.forEach { turn ->
                                DropdownMenuItem(text = { Text("نوبت ${turn.turnNumber} — ${turn.title}") }, onClick = { cueTurnId = turn.id; expanded = false })
                            }
                        }
                    }
                    OutlinedTextField(cueInstruction, { cueInstruction = it }, label = { Text("شرح دستور صحنه") }, modifier = Modifier.fillMaxWidth(), minLines = 2)
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Button(enabled = selectedVersion != null && selectedCueTurn != null && cueInstruction.isNotBlank() && !busy, onClick = {
                            scope.launch {
                                try {
                                    val order = dao.stageCues(selectedVersion!!.id).maxOfOrNull { it.orderIndex }?.plus(1) ?: 0
                                    dao.insertStageCue(DirectorStageCueEntity(versionId = selectedVersion!!.id, turnId = selectedCueTurn!!.id, cueType = DirectorStageCueEntity.CUE_OTHER, title = "دستور صحنه", instruction = cueInstruction.trim(), orderIndex = order))
                                    cueInstruction = ""
                                    message = "دستور صحنه ثبت شد."
                                    reload()
                                } catch (e: Exception) { message = "ثبت دستور ناموفق بود: ${e.message}" }
                            }
                        }) { Text("ثبت دستور") }
                        OutlinedButton(enabled = selectedVersion != null && selectedCueTurn != null && !busy, onClick = {
                            scope.launch {
                                try {
                                    val order = dao.stageCues(selectedVersion!!.id).maxOfOrNull { it.orderIndex }?.plus(1) ?: 0
                                    val role = dao.participants(selectedCueTurn!!.id).firstOrNull()?.roleUid.orEmpty()
                                    dao.insertStageCue(DirectorStageCueEntity(versionId = selectedVersion!!.id, turnId = selectedCueTurn.id, cueType = DirectorStageCueEntity.CUE_IMMEDIATE, title = "بلافاصله", instruction = cueInstruction.ifBlank { "ادامه اجرا با همان نقش؛ به نوبت بعدی منتقل نشود." }, orderIndex = order, blocksNextTurn = true, continuationRoleUid = role))
                                    cueInstruction = ""
                                    message = "دستور بلافاصله ثبت شد و عبور به نوبت بعدی را مسدود می‌کند."
                                    reload()
                                } catch (e: Exception) { message = "ثبت دستور بلافاصله ناموفق بود: ${e.message}" }
                            }
                        }) { Text("بلافاصله") }
                    }
                    stageCues.forEach { cue ->
                        ListItem(
                            headlineContent = { Text(cue.title.ifBlank { cue.cueType }) },
                            supportingContent = { Text(cue.instruction + if (cue.blocksNextTurn) " • ادامه همان نقش" else "") },
                            trailingContent = { TextButton(onClick = { scope.launch { dao.deleteStageCue(cue.id); reload() } }) { Text("حذف") } }
                        )
                    }
                }
                item {
                    Text("نوبت‌های ثبت‌شده (${turns.size})", fontWeight = FontWeight.Bold)
                }
                items(turns, key = { it.id }) { turn ->
                    val segmentState = remember(turn.id) { mutableStateOf("") }
                    Card(Modifier.fillMaxWidth()) {
                        Column(Modifier.padding(12.dp)) {
                            Text("نوبت ${turn.turnNumber}  •  ترتیب ${turn.sequenceIndex}", fontWeight = FontWeight.Bold)
                            Text(turn.title + if (turn.turnType == DirectorTurnEntity.TYPE_COLLECTIVE) " • جمعی" else "")
                            if (turn.notes.isNotBlank()) Text(turn.notes, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
                            LaunchedEffect(turn.id, refresh) { segmentState.value = dao.textSegments(turn.id).joinToString("\n") { it.text } }
                            Text(segmentState.value, style = MaterialTheme.typography.bodySmall, maxLines = 4)
                        }
                    }
                }
            }
            if (busy) item { LinearProgressIndicator(Modifier.fillMaxWidth()) }
            message?.let { msg -> item { Text(msg, color = MaterialTheme.colorScheme.primary) } }
        }
    }
}

private suspend fun importDirectorSource(context: Context, uri: Uri, planId: Long): DirectorSourceFileEntity {
    val bytes = withContext(Dispatchers.IO) { context.contentResolver.openInputStream(uri)?.use { it.readBytes() } ?: error("فایل قابل خواندن نیست") }
    require(bytes.isNotEmpty()) { "فایل خالی است" }
    require(bytes.size <= 35 * 1024 * 1024) { "حجم فایل بیش از ۳۵ مگابایت است" }
    val name = queryDisplayName(context, uri) ?: "director-source-${System.currentTimeMillis()}"
    val mime = context.contentResolver.getType(uri) ?: "application/octet-stream"
    val ext = name.substringAfterLast('.', "").lowercase()
    val dir = File(context.filesDir, "director_sources/$planId").apply { mkdirs() }
    val target = File(dir, "${UUID.randomUUID()}-${name.replace(Regex("[^A-Za-z0-9._-]"), "_")}")
    target.writeBytes(bytes)
    val extracted = when (ext) {
        "txt", "text" -> bytes.toString(Charsets.UTF_8).removePrefix("\uFEFF")
        "docx" -> extractDocxText(bytes)
        else -> ""
    }
    return DirectorSourceFileEntity(planId = planId, originalName = name, mimeType = mime, localPath = target.absolutePath, sha256 = sha256(bytes), importMethod = when (ext) { "txt", "text" -> "TEXT"; "docx" -> "DOCX_XML"; "pdf" -> "PDF_SOURCE_ONLY"; else -> "SOURCE_ONLY" }, extractedText = extracted, createdAt = System.currentTimeMillis())
}

private fun queryDisplayName(context: Context, uri: Uri): String? = runCatching {
    context.contentResolver.query(uri, arrayOf(android.provider.OpenableColumns.DISPLAY_NAME), null, null, null)?.use { c -> if (c.moveToFirst()) c.getString(0) else null }
}.getOrNull()

private fun sha256(bytes: ByteArray): String = MessageDigest.getInstance("SHA-256").digest(bytes).joinToString("") { "%02x".format(it) }

private fun extractDocxText(bytes: ByteArray): String {
    val documentXml = ZipInputStream(bytes.inputStream()).use { zip ->
        var result = ""
        while (true) {
            val entry = zip.nextEntry ?: break
            if (entry.name == "word/document.xml") {
                result = zip.readBytes().toString(Charsets.UTF_8)
                break
            }
        }
        result
    }
    if (documentXml.isBlank()) return ""
    return documentXml
        .replace(Regex("</w:p>"), "\n")
        .replace(Regex("<w:tab[^>]*/>"), "\t")
        .replace(Regex("<[^>]+>"), "")
        .replace("&amp;", "&").replace("&lt;", "<").replace("&gt;", ">")
        .replace("&quot;", "\"").replace("&apos;", "'")
        .trim()
}


/** Returns (turn number, turn body) pairs for common Persian numbered-heading formats. */
private fun parseNumberedDirectorTurns(source: String): List<Pair<Int, String>> {
    val heading = Regex("(?m)^\\s*(?:نوبت|شماره)\\s*[:：#-]?\\s*(\\d+)\\s*(?:[-–:：]\\s*(.*))?$|^\\s*(\\d+)\\s*[-.)：:]\\s*(.*)$")
    val lines = source.replace("\r\n", "\n").replace('\r', '\n').lines()
    val result = mutableListOf<Pair<Int, String>>()
    var currentNumber: Int? = null
    val body = StringBuilder()
    fun flush() {
        val number = currentNumber
        val text = body.toString().trim()
        if (number != null && text.isNotBlank()) result += number to text
        body.setLength(0)
    }
    for (line in lines) {
        val normalizedLine = line.map { ch ->
            when (ch) {
                in '۰'..'۹' -> ('0'.code + (ch.code - '۰'.code)).toChar()
                in '٠'..'٩' -> ('0'.code + (ch.code - '٠'.code)).toChar()
                else -> ch
            }
        }.joinToString("")
        val match = heading.matchEntire(normalizedLine)
        if (match != null) {
            flush()
            currentNumber = (match.groups[1]?.value ?: match.groups[3]?.value)?.toIntOrNull()
            val inline = match.groups[2]?.value ?: match.groups[4]?.value
            if (!inline.isNullOrBlank()) body.append(inline).append('\n')
        } else if (currentNumber != null) {
            body.append(line).append('\n')
        }
    }
    flush()
    return result
}
