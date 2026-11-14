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
import androidx.compose.material.icons.filled.Delete
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
import androidx.room.withTransaction
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
    var planSearch by remember { mutableStateOf("") }
    var hasMorePlans by remember { mutableStateOf(false) }
    var loadingMorePlans by remember { mutableStateOf(false) }
    var selectedPlan by remember { mutableStateOf<DirectorPlanEntity?>(null) }
    var selectedVersion by remember { mutableStateOf<DirectorPlanVersionEntity?>(null) }
    var planVersions by remember { mutableStateOf(emptyList<DirectorPlanVersionEntity>()) }
    var sources by remember { mutableStateOf(emptyList<DirectorSourceFileEntity>()) }
    var turns by remember { mutableStateOf(emptyList<DirectorTurnEntity>()) }
    var previewText by remember { mutableStateOf("") }
    var message by remember { mutableStateOf<String?>(null) }
    var busy by remember { mutableStateOf(false) }
    var planTitle by remember { mutableStateOf("") }
    var taziehOptions by remember { mutableStateOf(emptyList<TaziehEntity>()) }
    var fieldOptions by remember { mutableStateOf(emptyList<FieldEntity>()) }
    var selectedTazieh by remember { mutableStateOf<TaziehEntity?>(null) }
    var taziehMenuExpanded by remember { mutableStateOf(false) }
    var roleOptions by remember { mutableStateOf(emptyList<RoleEntity>()) }
    var selectedRole by remember { mutableStateOf<RoleEntity?>(null) }
    var roleMenuExpanded by remember { mutableStateOf(false) }
    var deletePlanCandidate by remember { mutableStateOf<DirectorPlanEntity?>(null) }
    var turnNumber by remember { mutableStateOf("1") }
    var turnText by remember { mutableStateOf("") }
    var refresh by remember { mutableIntStateOf(0) }
    var cueInstruction by remember { mutableStateOf("") }
    var cueTurnId by remember { mutableStateOf<Long?>(null) }
    var stageCues by remember { mutableStateOf(emptyList<DirectorStageCueEntity>()) }

    suspend fun loadPlans(reset: Boolean) {
        val offset = if (reset) 0 else plans.size
        val page = dao.plansPage(planSearch.trim(), 50, offset)
        plans = if (reset) page else plans + page
        hasMorePlans = page.size == 50
    }

    fun reload() {
        scope.launch {
            loadPlans(reset = true)
            selectedPlan?.let { current ->
                selectedPlan = dao.plan(current.id)
                val targetTazieh = db.taziehDao().getByUid(current.taziehUid)
                roleOptions = targetTazieh?.let { db.roleDao().getByTazieh(it.id) }.orEmpty()
                selectedRole = selectedRole?.let { old -> roleOptions.firstOrNull { it.uid == old.uid } } ?: roleOptions.firstOrNull()
                sources = dao.sourceFiles(current.id)
                val versions = dao.versions(current.id)
                planVersions = versions
                selectedVersion = selectedVersion?.let { old -> versions.firstOrNull { it.id == old.id } } ?: versions.firstOrNull()
                selectedVersion?.let {
                    turns = dao.turns(it.id)
                    stageCues = dao.stageCues(it.id)
                }
            }
        }
    }
    LaunchedEffect(Unit) {
        try {
            taziehOptions = db.taziehDao().getAll()
            fieldOptions = db.fieldDao().getAll()
        } catch (e: Exception) { message = "دریافت فهرست زمینه‌ها و تعزیه‌ها ناموفق بود: ${e.message}" }
    }
    LaunchedEffect(refresh) { reload() }
    LaunchedEffect(planSearch) { loadPlans(reset = true) }

    val importLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenMultipleDocuments()) { uris ->
        if (uris.isNotEmpty() && selectedPlan != null && selectedVersion != null) {
            scope.launch {
                busy = true
                try {
                    val importedSources = mutableListOf<Pair<Long, DirectorSourceFileEntity>>()
                    uris.forEach { uri ->
                        val imported = withContext(Dispatchers.IO) { importDirectorSource(context, uri, selectedPlan!!.id) }
                        val alreadyStored = dao.sourceFiles(selectedPlan!!.id).any { it.sha256 == imported.sha256 }
                        if (alreadyStored) {
                            File(imported.localPath).delete()
                        } else {
                            val sourceId = dao.insertSourceFile(imported)
                            importedSources += sourceId to imported
                        }
                    }
                    val targetTazieh = db.taziehDao().getByUid(selectedPlan!!.taziehUid)
                    val targetTaziehTitle = targetTazieh?.title.orEmpty()
                    val targetFieldTitle = targetTazieh?.let { target ->
                        db.fieldDao().getAll().firstOrNull { it.id == target.fieldId }?.title
                    }.orEmpty()
                    val targetRoles = targetTazieh?.let { db.roleDao().getByTazieh(it.id) }.orEmpty()
                    val parsedEntries = importedSources.flatMap { (sourceId, source) ->
                        parseDirectorSourceTurns(sourceId, source, targetFieldTitle, targetTaziehTitle, targetRoles)
                    }
                    var createdTurns = 0
                    var collectiveTurns = 0
                    var mismatchedTurns = 0
                    var nextSequence = (dao.turns(selectedVersion!!.id).maxOfOrNull { it.sequenceIndex } ?: 0) + 1
                    var nextUnnumberedTurn = (dao.turns(selectedVersion!!.id).maxOfOrNull { it.turnNumber } ?: 0) + 1

                    suspend fun saveTurn(number: Int, entries: List<DirectorSourceTurn>) {
                        if (entries.isEmpty()) return
                        val distinctRoles = entries.map { it.roleTitle }.distinctBy { ContentUid.normalize(it) }
                        val isCollective = distinctRoles.size > 1
                        val distinctTexts = entries.map { it.text.trim().replace(Regex("\\s+"), " ") }.distinct()
                        val note = if (distinctTexts.size > 1) "اختلاف متن برای شماره مشترک؛ نیازمند بازبینی مدیر" else ""
                        if (distinctTexts.size > 1) mismatchedTurns++
                        if (isCollective) collectiveTurns++
                        val turnId = dao.insertTurn(DirectorTurnEntity(
                            versionId = selectedVersion!!.id,
                            turnNumber = number,
                            sequenceIndex = nextSequence++,
                            turnType = if (isCollective) DirectorTurnEntity.TYPE_COLLECTIVE else DirectorTurnEntity.TYPE_NORMAL,
                            title = if (isCollective) "نوبت جمعی: ${distinctRoles.joinToString("، ")}" else distinctRoles.firstOrNull().orEmpty(),
                            notes = note
                        ))
                        distinctRoles.forEachIndexed { index, roleName ->
                            val linkedRole = resolveDirectorRole(db, selectedPlan!!.taziehUid, roleName)
                            dao.insertParticipant(DirectorParticipantEntity(
                                turnId = turnId,
                                roleUid = linkedRole?.uid ?: "unmatched-${ContentUid.sha256("${selectedPlan!!.taziehUid}|${ContentUid.normalize(roleName)}")}",
                                roleTitle = linkedRole?.title ?: roleName,
                                orderIndex = index,
                                isLead = index == 0
                            ))
                        }
                        entries.forEachIndexed { index, entry ->
                            dao.insertTextSegment(DirectorTextSegmentEntity(
                                turnId = turnId, segmentIndex = index, text = entry.text, sourceFileId = entry.sourceId
                            ))
                        }
                        createdTurns++
                    }

                    // Numbered files from separate roles are joined by their shared turn number.
                    parsedEntries.filter { it.turnNumber != null }
                        .groupBy { it.turnNumber!! }.toSortedMap()
                        .forEach { (number, entries) -> saveTurn(number, entries) }

                    // Full-structure DOCX and unnumbered role files retain source/file order.
                    parsedEntries.filter { it.turnNumber == null }.forEach { entry ->
                        saveTurn(nextUnnumberedTurn++, listOf(entry))
                    }
                    previewText = importedSources.firstOrNull()?.second?.extractedText.orEmpty()
                    message = if (parsedEntries.isEmpty()) {
                        "${importedSources.size} فایل منبع ذخیره شد؛ متن ساختاریافته/پاراگرافی و نوبت‌های شماره‌دار بررسی شد. PDF اسکن‌شده و تصویر به OCR نیاز دارند."
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
                        Text("انتخاب تعزیه از کتابخانه", style = MaterialTheme.typography.labelLarge)
                        Box {
                            OutlinedButton(
                                onClick = { taziehMenuExpanded = true },
                                modifier = Modifier.fillMaxWidth(),
                                enabled = taziehOptions.isNotEmpty()
                            ) {
                                Text(selectedTazieh?.title ?: if (taziehOptions.isEmpty()) "تعزیه‌ای در کتابخانه پیدا نشد" else "برای انتخاب تعزیه لمس کنید")
                            }
                            DropdownMenu(
                                expanded = taziehMenuExpanded,
                                onDismissRequest = { taziehMenuExpanded = false },
                                modifier = Modifier.fillMaxWidth(0.92f)
                            ) {
                                taziehOptions.forEach { item ->
                                    DropdownMenuItem(
                                        text = { Text("${fieldOptions.firstOrNull { it.id == item.fieldId }?.title ?: "زمینه نامشخص"}  ←  ${item.title}") },
                                        onClick = {
                                            selectedTazieh = item
                                            if (planTitle.isBlank()) planTitle = item.title
                                            taziehMenuExpanded = false
                                        }
                                    )
                                }
                            }
                        }
                        if (selectedTazieh != null) {
                            Text("زمینه: ${fieldOptions.firstOrNull { it.id == selectedTazieh!!.fieldId }?.title ?: "نامشخص"} | مجلس: ${selectedTazieh!!.title}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        OutlinedTextField(planTitle, { planTitle = it }, label = { Text("عنوان دفتر / مجلس") }, modifier = Modifier.fillMaxWidth(), singleLine = true)
                        Button(enabled = planTitle.isNotBlank() && selectedTazieh != null && !busy, onClick = {
                            scope.launch {
                                busy = true
                                try {
                                    val existingPlan = dao.plansForTazieh(selectedTazieh!!.uid).firstOrNull()
                                    if (existingPlan != null) {
                                        selectedPlan = existingPlan
                                        val target = db.taziehDao().getByUid(existingPlan.taziehUid)
                                        roleOptions = target?.let { db.roleDao().getByTazieh(it.id) }.orEmpty()
                                        selectedRole = roleOptions.firstOrNull()
                                        planVersions = dao.versions(existingPlan.id)
                                        selectedVersion = planVersions.firstOrNull()
                                        sources = dao.sourceFiles(existingPlan.id)
                                        turns = selectedVersion?.let { dao.turns(it.id) } ?: emptyList()
                                        message = "برای این مجلس دفتر اصلی از قبل وجود دارد؛ همان دفتر باز شد. نسخه‌های آن را مدیریت کنید."
                                    } else {
                                        val now = System.currentTimeMillis()
                                        val id = dao.insertPlan(DirectorPlanEntity(uid = UUID.randomUUID().toString(), taziehUid = selectedTazieh!!.uid, title = planTitle.trim(), createdAt = now, updatedAt = now))
                                        val versionId = dao.insertVersion(DirectorPlanVersionEntity(planId = id, versionNumber = 1, title = "نسخه اول", createdAt = now))
                                        selectedPlan = dao.plan(id)
                                        val target = db.taziehDao().getByUid(selectedTazieh!!.uid)
                                        roleOptions = target?.let { db.roleDao().getByTazieh(it.id) }.orEmpty()
                                        selectedRole = roleOptions.firstOrNull()
                                        selectedVersion = dao.version(versionId)
                                        planTitle = ""
                                        selectedTazieh = null
                                        message = "دفتر جدید ایجاد شد."
                                    }
                                    reload()
                                } catch (e: Exception) { message = "ایجاد دفتر ناموفق بود: ${e.message}" }
                                finally { busy = false }
                            }
                        }) { Icon(Icons.Default.Add, null); Spacer(Modifier.width(6.dp)); Text("ایجاد دفتر") }
                    }
                }
            }
            item {
                Text("دفترهای موجود", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                OutlinedTextField(planSearch, { planSearch = it }, modifier = Modifier.fillMaxWidth(), label = { Text("جست‌وجوی دفتر بر اساس نام مجلس") }, singleLine = true)
                Text("فهرست صفحه‌بندی‌شده؛ هر بار حداکثر ۵۰ دفتر بارگذاری می‌شود.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            if (plans.isEmpty()) item { Text(if (planSearch.isBlank()) "هنوز دفتری ایجاد نشده است." else "دفتر منطبق با جست‌وجو پیدا نشد.", color = MaterialTheme.colorScheme.onSurfaceVariant) }
            items(plans, key = { it.id }) { plan ->
                Card(Modifier.fillMaxWidth().clickable {
                    scope.launch {
                        selectedPlan = plan
                        val target = db.taziehDao().getByUid(plan.taziehUid)
                        roleOptions = target?.let { db.roleDao().getByTazieh(it.id) }.orEmpty()
                        selectedRole = roleOptions.firstOrNull()
                        val versions = dao.versions(plan.id)
                        planVersions = versions
                        selectedVersion = versions.firstOrNull()
                        sources = dao.sourceFiles(plan.id)
                        turns = selectedVersion?.let { dao.turns(it.id) } ?: emptyList()
                    }
                }, colors = CardDefaults.cardColors(containerColor = if (selectedPlan?.id == plan.id) MaterialTheme.colorScheme.secondaryContainer else MaterialTheme.colorScheme.surfaceVariant)) {
                    Row(Modifier.fillMaxWidth().padding(12.dp), horizontalArrangement = Arrangement.SpaceBetween) {
                        Column(Modifier.weight(1f)) {
                            Text(plan.title, fontWeight = FontWeight.Bold)
                            val tazieh = taziehOptions.firstOrNull { it.uid == plan.taziehUid }
                            val fieldTitle = tazieh?.let { t -> fieldOptions.firstOrNull { it.id == t.fieldId }?.title }.orEmpty()
                            Text("${fieldTitle.ifBlank { "زمینه نامشخص" }}  ←  ${tazieh?.title ?: plan.taziehUid}", style = MaterialTheme.typography.bodySmall)
                            Text("وضعیت: ${if (plan.status == DirectorPlanEntity.STATUS_PUBLISHED) "منتشرشده" else "پیش‌نویس"}", style = MaterialTheme.typography.labelSmall)
                        }
                        IconButton(onClick = { deletePlanCandidate = plan }) {
                            Icon(Icons.Default.Delete, contentDescription = "حذف دفتر")
                        }
                    }
                }
            }
            if (hasMorePlans) item {
                OutlinedButton(enabled = !loadingMorePlans, onClick = {
                    scope.launch {
                        loadingMorePlans = true
                        try { loadPlans(reset = false) } finally { loadingMorePlans = false }
                    }
                }, modifier = Modifier.fillMaxWidth()) { Text(if (loadingMorePlans) "در حال بارگذاری…" else "نمایش ۵۰ دفتر بعدی") }
            }
            if (selectedPlan != null) {
                item {
                    Divider()
                    Text("مدیریت: ${selectedPlan!!.title}", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                    Text("نسخه انتخابی: ${selectedVersion?.title ?: "ندارد"} | وضعیت: ${selectedVersion?.status ?: "—"}", style = MaterialTheme.typography.bodySmall)
                    Text("نقش‌های ثبت‌شده در همین مجلس (${roleOptions.size})", fontWeight = FontWeight.SemiBold)
                    if (roleOptions.isEmpty()) Text("برای این مجلس هنوز نقشی در کتابخانه ثبت نشده است.", color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
                    else Text(roleOptions.joinToString("، ") { it.title }, style = MaterialTheme.typography.bodySmall)
                    if (planVersions.isNotEmpty()) {
                        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            planVersions.forEach { version ->
                                TextButton(onClick = {
                                    selectedVersion = version
                                    scope.launch {
                                        turns = dao.turns(version.id)
                                        stageCues = dao.stageCues(version.id)
                                    }
                                }) { Text("${version.versionNumber}: ${version.title}") }
                            }
                        }
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedButton(enabled = selectedPlan != null && selectedVersion != null && !busy, onClick = {
                            scope.launch {
                                busy = true
                                try {
                                    val plan = selectedPlan!!
                                    val sourceVersion = selectedVersion!!
                                    val now = System.currentTimeMillis()
                                    val newNumber = (dao.versions(plan.id).maxOfOrNull { it.versionNumber } ?: 0) + 1
                                    val newVersionId = db.withTransaction {
                                        val id = dao.insertVersion(DirectorPlanVersionEntity(
                                            planId = plan.id, versionNumber = newNumber,
                                            title = "نسخه $newNumber", status = DirectorPlanVersionEntity.STATUS_DRAFT,
                                            changeNotes = "رونوشت از نسخه ${sourceVersion.versionNumber}", createdAt = now
                                        ))
                                        val turnMap = mutableMapOf<Long, Long>()
                                        dao.turns(sourceVersion.id).forEach { oldTurn ->
                                            val newTurnId = dao.insertTurn(oldTurn.copy(id = 0, versionId = id))
                                            turnMap[oldTurn.id] = newTurnId
                                            dao.participants(oldTurn.id).forEach { participant ->
                                                dao.insertParticipant(participant.copy(id = 0, turnId = newTurnId))
                                            }
                                            dao.textSegments(oldTurn.id).forEach { segment ->
                                                dao.insertTextSegment(segment.copy(id = 0, turnId = newTurnId))
                                            }
                                        }
                                        dao.stageCues(sourceVersion.id).forEach { cue ->
                                            dao.insertStageCue(cue.copy(id = 0, versionId = id, turnId = cue.turnId?.let { turnMap[it] }))
                                        }
                                        id
                                    }
                                    planVersions = dao.versions(plan.id)
                                    selectedVersion = dao.version(newVersionId)
                                    turns = dao.turns(newVersionId)
                                    stageCues = dao.stageCues(newVersionId)
                                    message = "نسخه پیش‌نویس جدید با کپی کامل ترتیب، نقش‌ها، متن‌ها و دستورهای صحنه ساخته شد."
                                } catch (e: Exception) { message = "ساخت نسخه جدید ناموفق بود: ${e.message}" }
                                finally { busy = false }
                            }
                        }) { Text("ساخت نسخه جدید از نسخه انتخابی") }
                        Button(enabled = selectedPlan != null && selectedVersion != null && selectedVersion!!.status != DirectorPlanVersionEntity.STATUS_PUBLISHED && !busy, onClick = {
                            scope.launch {
                                busy = true
                                try {
                                    val plan = selectedPlan!!
                                    val version = selectedVersion!!
                                    db.withTransaction {
                                        dao.versions(plan.id).filter { it.status == DirectorPlanVersionEntity.STATUS_PUBLISHED }.forEach {
                                            dao.updateVersion(it.copy(status = DirectorPlanVersionEntity.STATUS_SUPERSEDED))
                                        }
                                        dao.updateVersion(version.copy(status = DirectorPlanVersionEntity.STATUS_PUBLISHED, publishedAt = System.currentTimeMillis()))
                                        dao.updatePlan(plan.copy(status = DirectorPlanEntity.STATUS_PUBLISHED, updatedAt = System.currentTimeMillis()))
                                    }
                                    selectedPlan = dao.plan(plan.id)
                                    planVersions = dao.versions(plan.id)
                                    selectedVersion = dao.version(version.id)
                                    message = "نسخه ${version.versionNumber} منتشر شد؛ نسخه منتشرشده قبلی به حالت جایگزین‌شده رفت."
                                    reload()
                                } catch (e: Exception) { message = "انتشار نسخه ناموفق بود: ${e.message}" }
                                finally { busy = false }
                            }
                        }) { Text("انتشار نسخه انتخابی") }
                    }
                    Text("سه روش ورود: ۱) فایل کامل مجلس با نقش و ترتیب دیالوگ‌ها؛ ۲) فایل جدا برای هر نقش بدون شماره (نام فایل باید با نام نقش کتابخانه مطابق باشد)؛ ۳) فایل جدا برای هر نقش با شماره‌های مشترک اجرا. در روش سوم شماره یکسان، نوبت جمعی می‌سازد.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Button(onClick = { importLauncher.launch(arrayOf("text/plain", "application/json", "application/pdf", "application/vnd.openxmlformats-officedocument.wordprocessingml.document", "application/octet-stream", "image/*")) }, enabled = !busy) {
                        Icon(Icons.Default.FileOpen, null); Spacer(Modifier.width(6.dp)); Text("ورود فایل کامل یا فایل‌های جداگانه نقش‌ها")
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
                    Text("PDF متنی استخراج می‌شود؛ PDF اسکن‌شده و تصویر تا زمان نصب مدل OCR فارسی/عربی فقط به‌عنوان منبع نگهداری می‌شوند.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                item {
                    Button(
                        enabled = selectedVersion != null && selectedRole != null && previewText.isNotBlank() && !busy,
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
                                            val linkedRole = selectedRole!!
                                            val turnId = dao.insertTurn(DirectorTurnEntity(versionId = version.id, turnNumber = part.first, sequenceIndex = nextIndex++, title = linkedRole.title))
                                            dao.insertParticipant(DirectorParticipantEntity(turnId = turnId, roleUid = linkedRole.uid, roleTitle = linkedRole.title, isLead = true))
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
                    ExposedDropdownMenuBox(expanded = roleMenuExpanded, onExpandedChange = { roleMenuExpanded = !roleMenuExpanded }) {
                        OutlinedTextField(
                            value = selectedRole?.title ?: "ابتدا نقش مجلس را انتخاب کنید",
                            onValueChange = {}, readOnly = true, label = { Text("اتصال به نقش موجود همین مجلس") },
                            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = roleMenuExpanded) },
                            modifier = Modifier.menuAnchor().fillMaxWidth(), enabled = roleOptions.isNotEmpty()
                        )
                        ExposedDropdownMenu(expanded = roleMenuExpanded, onDismissRequest = { roleMenuExpanded = false }) {
                            roleOptions.forEach { role ->
                                DropdownMenuItem(text = { Text(role.title) }, onClick = { selectedRole = role; roleMenuExpanded = false })
                            }
                        }
                    }
                    OutlinedTextField(turnNumber, { turnNumber = it.filter(Char::isDigit).take(6) }, label = { Text("شماره نوبت") }, modifier = Modifier.fillMaxWidth(), singleLine = true)
                    OutlinedTextField(turnText, { turnText = it }, label = { Text("متن این نوبت") }, modifier = Modifier.fillMaxWidth().heightIn(min = 120.dp), minLines = 4)
                    Button(enabled = selectedVersion != null && selectedRole != null && turnText.isNotBlank() && !busy, onClick = {
                        scope.launch {
                            busy = true
                            try {
                                val version = selectedVersion!!
                                val nextIndex = (dao.turns(version.id).maxOfOrNull { it.sequenceIndex } ?: 0) + 1
                                val number = turnNumber.toIntOrNull() ?: nextIndex
                                val linkedRole = selectedRole!!
                                val turnId = dao.insertTurn(DirectorTurnEntity(versionId = version.id, turnNumber = number, sequenceIndex = nextIndex, title = linkedRole.title))
                                dao.insertParticipant(DirectorParticipantEntity(turnId = turnId, roleUid = linkedRole.uid, roleTitle = linkedRole.title, isLead = true))
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
                            val participantsState = remember(turn.id) { mutableStateOf(emptyList<DirectorParticipantEntity>()) }
                            LaunchedEffect(turn.id, refresh) {
                                segmentState.value = dao.textSegments(turn.id).joinToString("\n") { it.text }
                                participantsState.value = dao.participants(turn.id)
                            }
                            Text(segmentState.value, style = MaterialTheme.typography.bodySmall, maxLines = 4)
                            participantsState.value.filter { it.roleUid.startsWith("unmatched-") }.forEach { participant ->
                                var mappingExpanded by remember(participant.id) { mutableStateOf(false) }
                                ExposedDropdownMenuBox(expanded = mappingExpanded, onExpandedChange = { mappingExpanded = !mappingExpanded }) {
                                    OutlinedTextField(
                                        value = "اتصال «${participant.roleTitle}» به نقش کتابخانه",
                                        onValueChange = {}, readOnly = true,
                                        trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(mappingExpanded) },
                                        modifier = Modifier.menuAnchor().fillMaxWidth(), enabled = roleOptions.isNotEmpty()
                                    )
                                    ExposedDropdownMenu(expanded = mappingExpanded, onDismissRequest = { mappingExpanded = false }) {
                                        roleOptions.forEach { role ->
                                            DropdownMenuItem(text = { Text(role.title) }, onClick = {
                                                scope.launch {
                                                    try {
                                                        dao.updateParticipant(participant.copy(roleUid = role.uid, roleTitle = role.title))
                                                        participantsState.value = dao.participants(turn.id)
                                                        message = "نقش «${participant.roleTitle}» به «${role.title}» متصل شد."
                                                    } catch (e: Exception) { message = "اتصال نقش انجام نشد: ${e.message}" }
                                                }
                                                mappingExpanded = false
                                            })
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
            if (busy) item { LinearProgressIndicator(Modifier.fillMaxWidth()) }
            message?.let { msg -> item { Text(msg, color = MaterialTheme.colorScheme.primary) } }
        }
    }
    deletePlanCandidate?.let { candidate ->
        AlertDialog(
            onDismissRequest = { deletePlanCandidate = null },
            title = { Text("حذف دفتر معین‌البکا") },
            text = { Text("دفتر «${candidate.title}» از فهرست فعال خارج می‌شود. فایل‌های منبع و متن اصلی مجلس حذف نمی‌شوند. ادامه می‌دهید؟") },
            confirmButton = {
                TextButton(onClick = {
                    scope.launch {
                        try {
                            dao.softDeletePlan(candidate.id, System.currentTimeMillis())
                            if (selectedPlan?.id == candidate.id) {
                                selectedPlan = null; selectedVersion = null; planVersions = emptyList()
                                sources = emptyList(); turns = emptyList(); roleOptions = emptyList(); selectedRole = null
                            }
                            message = "دفتر حذف شد؛ اطلاعات آن به‌صورت نرم نگهداری شده و متن اصلی مجلس دست‌نخورده است."
                            deletePlanCandidate = null
                            reload()
                        } catch (e: Exception) { message = "حذف دفتر انجام نشد: ${e.message}"; deletePlanCandidate = null }
                    }
                }) { Text("حذف دفتر") }
            },
            dismissButton = { TextButton(onClick = { deletePlanCandidate = null }) { Text("انصراف") } }
        )
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
        "txt", "text", "json" -> bytes.toString(Charsets.UTF_8).removePrefix("\uFEFF")
        "docx" -> wordToCompatibleJson(bytes)
        "pdf" -> runCatching { extractPdfText(context, bytes).text }.getOrElse { "" }
        else -> ""
    }
    return DirectorSourceFileEntity(planId = planId, originalName = name, mimeType = mime, localPath = target.absolutePath, sha256 = sha256(bytes), importMethod = when (ext) { "txt", "text", "json" -> "TEXT"; "docx" -> "DOCX_XML"; "pdf" -> "PDF_TEXT_OR_SCAN"; "jpg", "jpeg", "png" -> "IMAGE_OCR_PENDING"; else -> "SOURCE_ONLY" }, extractedText = extracted, createdAt = System.currentTimeMillis())
}

private data class DirectorSourceTurn(
    val sourceId: Long,
    val turnNumber: Int?,
    val roleTitle: String,
    val text: String
)

/** Supports (1) one structured DOCX for all roles, (2) unnumbered role files,
 * and (3) separate role files with shared numbered turns. */
private fun parseDirectorSourceTurns(
    sourceId: Long,
    source: DirectorSourceFileEntity,
    targetFieldTitle: String,
    targetTaziehTitle: String,
    knownRoles: List<RoleEntity>
): List<DirectorSourceTurn> {
    val raw = source.extractedText.trim()
    if (raw.isBlank()) return emptyList()

    if (raw.startsWith("[")) {
        return runCatching {
            val root = org.json.JSONArray(raw)
            val out = mutableListOf<DirectorSourceTurn>()
            val totalTaziehs = (0 until root.length()).sumOf { index ->
                root.optJSONObject(index)?.optJSONArray("taziehs")?.length() ?: 0
            }
            for (fi in 0 until root.length()) {
                val field = root.getJSONObject(fi)
                val fieldTitle = ContentUid.normalize(field.optString("title"))
                val fieldMatches = fieldTitle.equals(ContentUid.normalize(targetFieldTitle), true) ||
                    (root.length() == 1 && fieldTitle == ContentUid.normalize("بدون زمینه"))
                if (!fieldMatches) continue
                val taziehs = field.optJSONArray("taziehs") ?: continue
                for (ti in 0 until taziehs.length()) {
                    val tazieh = taziehs.getJSONObject(ti)
                    val majlisTitle = ContentUid.normalize(tazieh.optString("title"))
                    val majlisMatches = majlisTitle.equals(ContentUid.normalize(targetTaziehTitle), true) ||
                        (totalTaziehs == 1 && majlisTitle == ContentUid.normalize("بدون تعزیه"))
                    if (!majlisMatches) continue
                    val roles = tazieh.optJSONArray("roles") ?: continue
                    for (ri in 0 until roles.length()) {
                        val role = roles.getJSONObject(ri)
                        val sections = role.optJSONArray("sections") ?: continue
                        for (si in 0 until sections.length()) {
                            val section = sections.getJSONObject(si)
                            val body = section.optString("content").trim()
                            if (body.isNotBlank()) out += DirectorSourceTurn(
                                sourceId = sourceId, turnNumber = null,
                                roleTitle = role.optString("title", "نقش نامشخص"),
                                text = body
                            )
                        }
                    }
                }
            }
            out
        }.getOrDefault(emptyList())
    }

    val combinedEntries = parseRoleLabeledCombinedText(sourceId, raw, knownRoles)
    if (combinedEntries.isNotEmpty()) return combinedEntries

    val fileRole = source.originalName.substringBeforeLast('.', source.originalName)
        .replace('_', ' ').replace('-', ' ').trim().ifBlank { "نقش بدون نام" }
    val numbered = parseNumberedDirectorTurns(raw)
    if (numbered.isNotEmpty()) return numbered.map { DirectorSourceTurn(sourceId, it.first, fileRole, it.second) }

    // Unnumbered role documents are split into paragraph blocks and kept in the
    // order in which the user selected the files. A separate execution-list file
    // still needs explicit review before publication.
    return raw.replace("\r\n", "\n").replace('\r', '\n')
        .split(Regex("\\n\\s*\\n+"))
        .map { it.trim() }
        .filter { it.isNotBlank() }
        .map { DirectorSourceTurn(sourceId, null, fileRole, it) }
}

/** Parses a single combined TXT file whose dialogue blocks start with an existing role title.
 * Accepted headers: "نوبت ۱۲ - امام حسین: ..." and "امام حسین: ..." or a role title alone. */
private fun parseRoleLabeledCombinedText(
    sourceId: Long,
    raw: String,
    knownRoles: List<RoleEntity>
): List<DirectorSourceTurn> {
    if (knownRoles.isEmpty()) return emptyList()
    val roleByNormalizedTitle = knownRoles.associateBy { ContentUid.normalize(it.title) }
    val header = Regex("^\\s*(?:(?:نوبت|شماره)\\s*([0-9۰-۹٠-٩]+)\\s*[-–:：]\\s*)?(.+?)\\s*[:：]\\s*(.*)$")
    val result = mutableListOf<DirectorSourceTurn>()
    var currentRole: String? = null
    var currentNumber: Int? = null
    val body = StringBuilder()
    fun flush() {
        val role = currentRole
        val text = body.toString().trim()
        if (role != null && text.isNotBlank()) result += DirectorSourceTurn(sourceId, currentNumber, role, text)
        body.setLength(0)
    }
    raw.replace("\r\n", "\n").replace('\r', '\n').lines().forEach { line ->
        val trimmed = line.trim()
        if (trimmed.isBlank()) {
            if (currentRole != null) body.append('\n')
            return@forEach
        }
        val match = header.matchEntire(trimmed)
        val matchedRole = match?.groups?.get(2)?.value?.let { roleByNormalizedTitle[ContentUid.normalize(it)] }
            ?: roleByNormalizedTitle[ContentUid.normalize(trimmed)]
        if (matchedRole != null) {
            flush()
            currentRole = matchedRole.title
            currentNumber = match?.groups?.get(1)?.value?.let(::normalizeDirectorNumber)?.toIntOrNull()
            val inline = match?.groups?.get(3)?.value.orEmpty().trim()
            if (inline.isNotBlank()) body.append(inline).append('\n')
        } else if (currentRole != null) {
            body.append(line).append('\n')
        }
    }
    flush()
    return result
}

private fun normalizeDirectorNumber(value: String): String = value.map { ch ->
    when (ch) {
        in '۰'..'۹' -> ('0'.code + (ch.code - '۰'.code)).toChar()
        in '٠'..'٩' -> ('0'.code + (ch.code - '٠'.code)).toChar()
        else -> ch
    }
}.joinToString("")

private suspend fun resolveDirectorRole(db: AppDatabase, taziehUid: String, roleTitle: String): RoleEntity? {
    val tazieh = db.taziehDao().getByUid(taziehUid) ?: return null
    return db.roleDao().getByTazieh(tazieh.id).firstOrNull {
        ContentUid.normalize(it.title).equals(ContentUid.normalize(roleTitle), ignoreCase = true)
    }
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
