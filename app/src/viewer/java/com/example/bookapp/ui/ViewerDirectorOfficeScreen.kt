package com.example.bookapp.ui

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.ArrowDownward
import androidx.compose.material.icons.filled.ArrowUpward
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.FileOpen
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.platform.LocalContext
import com.example.bookapp.data.AppDatabase
import com.example.bookapp.data.DirectorPlanEntity
import com.example.bookapp.data.DirectorPlanVersionEntity
import com.example.bookapp.data.DirectorStageCueEntity
import com.example.bookapp.data.DirectorTextSegmentEntity
import com.example.bookapp.data.DirectorTurnEntity
import com.example.bookapp.data.ViewerAccessPolicy
import com.example.bookapp.data.ViewerDirectorOfficeDatabase
import com.example.bookapp.data.ViewerDirectorOfficePublicationImporter
import com.example.bookapp.data.ViewerDirectorOfficeRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

private data class ViewerBuildMethod(val code: String, val label: String, val permission: String)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ViewerDirectorOfficeScreen(contentDb: AppDatabase, onBack: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val officialDb = remember { ViewerDirectorOfficeDatabase.official(context) }
    val personalDb = remember { ViewerDirectorOfficeDatabase.personal(context) }
    val officialDao = remember { officialDb.directorDao() }
    val personalDao = remember { personalDb.directorDao() }

    val canOfficial = remember { ViewerAccessPolicy.hasPermission(context, "director.official.view") }
    val canCreate = remember { ViewerAccessPolicy.hasPermission(context, "director.personal.create") && canOfficial }
    val canEdit = remember { ViewerAccessPolicy.hasPermission(context, "director.personal.edit") }
    val methods = remember {
        listOf(
            ViewerBuildMethod("ROLE_NUMBERED", "شماره‌محور", "director.personal.method.number"),
            ViewerBuildMethod("FULL_TEXT", "متن کامل", "director.personal.method.fulltext"),
            ViewerBuildMethod("MANUAL", "دستی", "director.personal.method.manual"),
            ViewerBuildMethod("SELECTOR", "انتخاب‌گر", "director.personal.method.selector")
        ).filter { ViewerAccessPolicy.hasPermission(context, it.permission) }
    }

    var tab by remember { mutableIntStateOf(0) }
    var refresh by remember { mutableIntStateOf(0) }
    var busy by remember { mutableStateOf(false) }
    var message by remember { mutableStateOf<String?>(null) }
    var officialPlans by remember { mutableStateOf(emptyList<DirectorPlanEntity>()) }
    var personalPlans by remember { mutableStateOf(emptyList<DirectorPlanEntity>()) }
    var selectedOfficial by remember { mutableStateOf<DirectorPlanEntity?>(null) }
    var selectedPersonal by remember { mutableStateOf<DirectorPlanEntity?>(null) }
    var officialTurns by remember { mutableStateOf(emptyList<DirectorTurnEntity>()) }
    var personalTurns by remember { mutableStateOf(emptyList<DirectorTurnEntity>()) }
    var officialCues by remember { mutableStateOf(emptyList<DirectorStageCueEntity>()) }
    var selectedOfficialSourceMode by remember { mutableStateOf<String?>(null) }
    var selectedOfficialVersionLabel by remember { mutableStateOf("") }
    var personalCues by remember { mutableStateOf(emptyList<DirectorStageCueEntity>()) }
    var officialSegments by remember { mutableStateOf<Map<Long, List<DirectorTextSegmentEntity>>>(emptyMap()) }
    var personalSegments by remember { mutableStateOf<Map<Long, List<DirectorTextSegmentEntity>>>(emptyMap()) }
    var selectedPersonalMethod by remember { mutableStateOf("") }
    var selectorEntries by remember { mutableStateOf(emptyList<ViewerDirectorOfficePublicationImporter.SelectorEntry>()) }
    var manualTitle by remember { mutableStateOf("") }
    var manualText by remember { mutableStateOf("") }
    var manualNumber by remember { mutableStateOf("1") }
    var cueTitle by remember { mutableStateOf("") }
    var cueInstruction by remember { mutableStateOf("") }
    var search by remember { mutableStateOf("") }
    var showMethodDialog by remember { mutableStateOf(false) }
    var editingSegment by remember { mutableStateOf<DirectorTextSegmentEntity?>(null) }
    var editingSegmentText by remember { mutableStateOf("") }
    var editingCue by remember { mutableStateOf<DirectorStageCueEntity?>(null) }
    var editingCueTitle by remember { mutableStateOf("") }
    var editingCueText by remember { mutableStateOf("") }

    suspend fun reload() {
        officialPlans = if (canOfficial) officialDao.plansPage("", 500, 0).filter { it.status == DirectorPlanEntity.STATUS_PUBLISHED } else emptyList()
        personalPlans = personalDao.plansPage("", 500, 0).filter { it.uid.startsWith("personal-") }
        selectedOfficial = selectedOfficial?.let { old -> officialPlans.firstOrNull { it.uid == old.uid } }
        selectedPersonal = selectedPersonal?.let { old -> personalPlans.firstOrNull { it.uid == old.uid } }
        val officialPlan = selectedOfficial
        if (officialPlan != null) {
            val version = officialDao.versions(officialPlan.id).firstOrNull { it.status == DirectorPlanVersionEntity.STATUS_PUBLISHED }
            selectedOfficialSourceMode = version?.changeNotes?.substringAfter("روش تکمیل دفتر: ", "")?.takeIf { it in setOf("ROLE_NUMBERED", "FULL_TEXT", "MANUAL", "SELECTOR") }
            selectedOfficialVersionLabel = version?.let { "نسخه ${it.versionNumber} — ${it.title}" }.orEmpty()
            officialTurns = version?.let { officialDao.turns(it.id) }.orEmpty()
            officialCues = version?.let { officialDao.stageCues(it.id) }.orEmpty()
            val officialTextRows = if (officialTurns.isEmpty()) emptyList() else officialDao.textSegmentsForTurns(officialTurns.map { it.id })
            officialSegments = officialTextRows.groupBy { it.turnId }
        } else {
            officialTurns = emptyList(); officialCues = emptyList(); officialSegments = emptyMap(); selectedOfficialSourceMode = null; selectedOfficialVersionLabel = ""
        }
        val personalPlan = selectedPersonal
        if (personalPlan != null) {
            val version = personalDao.versions(personalPlan.id).firstOrNull()
            personalTurns = version?.let { personalDao.turns(it.id) }.orEmpty()
            personalCues = version?.let { personalDao.stageCues(it.id) }.orEmpty()
            val personalTextRows = if (personalTurns.isEmpty()) emptyList() else personalDao.textSegmentsForTurns(personalTurns.map { it.id })
            personalSegments = personalTextRows.groupBy { it.turnId }
            selectedPersonalMethod = version?.changeNotes?.substringAfter("روش ساخت: ", "")?.substringBefore("؛").orEmpty()
            selectorEntries = if (selectedPersonalMethod == "SELECTOR") ViewerDirectorOfficePublicationImporter.selectorEntries(contentDb, personalPlan.taziehUid) else emptyList()
        } else {
            personalTurns = emptyList(); personalCues = emptyList(); personalSegments = emptyMap(); selectedPersonalMethod = ""; selectorEntries = emptyList()
        }
    }

    val importLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) scope.launch {
            busy = true
            try {
                context.contentResolver.takePersistableUriPermission(uri, android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION)
            } catch (_: Exception) { }
            try {
                message = ViewerDirectorOfficePublicationImporter.import(context, uri, officialDb)
                refresh++
            } catch (e: Exception) {
                message = "واردکردن دفتر رسمی انجام نشد: ${e.message ?: "خطای نامشخص"}"
            } finally { busy = false }
        }
    }

    LaunchedEffect(refresh, canOfficial) {
        try { reload() } catch (e: Exception) { message = "بارگذاری دفتر ناموفق بود: ${e.message}" }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("دفتر معین‌البکا") },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "بازگشت") } }
            )
        }
    ) { padding ->
        Column(Modifier.fillMaxSize().padding(padding)) {
            TabRow(selectedTabIndex = tab) {
                Tab(selected = tab == 0, onClick = { tab = 0 }, text = { Text("دفتر رسمی") })
                Tab(selected = tab == 1, onClick = { tab = 1 }, text = { Text("دفتر شخصی") })
            }
            if (tab == 0) {
                if (!canOfficial) {
                    Text("مدیر دسترسی مشاهده دفتر رسمی را برای این Viewer فعال نکرده است.", Modifier.padding(16.dp), color = MaterialTheme.colorScheme.error)
                } else {
                    Row(Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedButton(onClick = { importLauncher.launch(arrayOf("application/json", "text/*", "*/*")) }, modifier = Modifier.weight(1f), enabled = !busy) {
                            Icon(Icons.Default.FileOpen, null); Text("دریافت بسته رسمی")
                        }
                    }
                    Text("فقط نسخه منتشرشده و امضاشده Admin نمایش داده می‌شود. این بخش امکان ویرایش، کپی، خروجی یا انتشار ندارد.", Modifier.padding(horizontal = 14.dp), style = MaterialTheme.typography.bodySmall)
                    LazyColumn(Modifier.weight(1f).fillMaxWidth().padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        if (officialPlans.isEmpty()) item { Text("هنوز بسته رسمی از Admin دریافت نشده است.") }
                        items(officialPlans, key = { it.uid }) { plan ->
                            Card(Modifier.fillMaxWidth().clickable {
                                selectedOfficial = plan
                                scope.launch { reload() }
                            }, colors = CardDefaults.cardColors(containerColor = if (selectedOfficial?.uid == plan.uid) MaterialTheme.colorScheme.secondaryContainer else MaterialTheme.colorScheme.surfaceVariant)) {
                                Column(Modifier.padding(12.dp)) {
                                    Text(plan.title, style = MaterialTheme.typography.titleMedium)
                                    Text("شناسه مجلس: ${plan.taziehUid}", style = MaterialTheme.typography.bodySmall)
                                    Text("وضعیت: منتشرشده توسط مدیر", style = MaterialTheme.typography.labelSmall)
                                }
                            }
                        }
                        if (selectedOfficial != null) {
                            item {
                                Text(selectedOfficialVersionLabel, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.secondary)
                                Text("نوبت‌های دفتر رسمی", style = MaterialTheme.typography.titleMedium)
                                OutlinedTextField(value = search, onValueChange = { search = it }, modifier = Modifier.fillMaxWidth(), label = { Text("جستجو در شماره، نقش و متن نوبت") }, leadingIcon = { Icon(Icons.Default.Search, null) }, singleLine = true)
                            }
                            val filtered = officialTurns.filter { turn ->
                                val corpus = (officialSegments[turn.id].orEmpty().joinToString(" ") { it.text } + " " + turn.title + " " + turn.turnNumber)
                                search.isBlank() || corpus.contains(search, ignoreCase = true)
                            }
                            items(filtered, key = { it.id }) { turn ->
                                Card(Modifier.fillMaxWidth()) {
                                    Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                        Text("نوبت ${turn.turnNumber} — ${turn.title}", style = MaterialTheme.typography.titleSmall)
                                        officialSegments[turn.id].orEmpty().forEach { segment -> Text(segment.text) }
                                        val cues = officialCues.filter { it.turnId == turn.id }
                                        cues.forEach { cue ->
                                            Text("راهنمای صحنه: ${cue.title.ifBlank { cue.cueType }} — ${cue.instruction}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.tertiary)
                                        }
                                    }
                                }
                            }
                            val globalCues = officialCues.filter { it.turnId == null }
                            if (globalCues.isNotEmpty()) item {
                                Text("راهنماهای عمومی صحنه", style = MaterialTheme.typography.titleSmall)
                                globalCues.forEach { cue -> Text("${cue.title}: ${cue.instruction}") }
                            }
                            item {
                                OutlinedButton(
                                    onClick = { showMethodDialog = true },
                                    enabled = canCreate && methods.isNotEmpty() && !busy,
                                    modifier = Modifier.fillMaxWidth()
                                ) { Text("ساخت دفتر شخصی از این مجلس") }
                                if (!canCreate || methods.isEmpty()) Text("مدیر اجازه ساخت دفتر شخصی یا هیچ‌یک از روش‌های ساخت را فعال نکرده است.", color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
                            }
                        }
                    }
                }
            } else {
                Column(Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp)) {
                    Text("دفتر شخصی فقط در همین دستگاه و در پایگاه داده مستقل ذخیره می‌شود. ویرایش آن هیچ اثری بر دفتر رسمی ندارد.", style = MaterialTheme.typography.bodySmall)
                    if (!canEdit) Text("مجوز ویرایش دفتر شخصی فعال نیست؛ دفترهای موجود فقط قابل مشاهده‌اند.", color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
                    if (!canOfficial && ViewerAccessPolicy.hasPermission(context, "director.personal.create")) Text("برای ساخت دفتر شخصی از محتوای مجلس، مدیر باید مشاهده دفتر رسمی را نیز فعال کند.", color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
                }
                LazyColumn(Modifier.weight(1f).fillMaxWidth().padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    if (personalPlans.isEmpty()) item { Text("هنوز دفتر شخصی ساخته نشده است. برای ساخت، ابتدا یک دفتر رسمی را دریافت و انتخاب کنید.") }
                    items(personalPlans, key = { it.uid }) { plan ->
                        Card(Modifier.fillMaxWidth().clickable {
                            selectedPersonal = plan
                            scope.launch { reload() }
                        }, colors = CardDefaults.cardColors(containerColor = if (selectedPersonal?.uid == plan.uid) MaterialTheme.colorScheme.secondaryContainer else MaterialTheme.colorScheme.surfaceVariant)) {
                            Column(Modifier.padding(12.dp)) {
                                Text(plan.title, style = MaterialTheme.typography.titleMedium)
                                Text("دفتر شخصی مستقل • روش: ${plan.description.substringAfter("روش ساخت: ", "نامشخص").substringBefore("؛")} • غیرقابل انتشار رسمی", style = MaterialTheme.typography.bodySmall)
                            }
                        }
                    }
                    if (selectedPersonal != null) {
                        if (selectedPersonalMethod == "MANUAL" && canEdit && ViewerAccessPolicy.hasPermission(context, "director.personal.method.manual")) item {
                            Text("افزودن نوبت به روش دستی", style = MaterialTheme.typography.titleMedium)
                            OutlinedTextField(manualNumber, { manualNumber = it.filter(Char::isDigit).take(6) }, label = { Text("شماره نوبت") }, modifier = Modifier.fillMaxWidth(), singleLine = true)
                            OutlinedTextField(manualTitle, { manualTitle = it }, label = { Text("عنوان نقش / نوبت") }, modifier = Modifier.fillMaxWidth(), singleLine = true)
                            OutlinedTextField(manualText, { manualText = it }, label = { Text("متن دیالوگ") }, modifier = Modifier.fillMaxWidth().heightIn(min = 120.dp), minLines = 3)
                            Button(onClick = {
                                scope.launch {
                                    try {
                                        ViewerDirectorOfficeRepository.addManualTurn(context, personalDb, selectedPersonal!!.id, manualNumber.toIntOrNull() ?: (personalTurns.size + 1), manualTitle, manualText)
                                        manualNumber = ((manualNumber.toIntOrNull() ?: personalTurns.size + 1) + 1).toString()
                                        manualTitle = ""; manualText = ""; refresh++
                                        message = "نوبت دستی به دفتر شخصی اضافه شد."
                                    } catch (e: Exception) { message = "افزودن نوبت دستی ناموفق بود: ${e.message}" }
                                }
                            }, enabled = manualTitle.isNotBlank() && manualText.isNotBlank()) { Text("افزودن نوبت") }
                        }
                        if (selectedPersonalMethod == "SELECTOR" && canEdit && ViewerAccessPolicy.hasPermission(context, "director.personal.method.selector")) item {
                            Text("انتخاب دیالوگ از متن مجلس", style = MaterialTheme.typography.titleMedium)
                            val alreadyUsed = personalTurns.map { it.continuityGroupUid }.filter { it.isNotBlank() }.toSet()
                            val availableEntries = selectorEntries.filterNot { it.key in alreadyUsed }
                            Text("دیالوگ‌های قابل انتخاب: ${availableEntries.size}", style = MaterialTheme.typography.bodySmall)
                            if (availableEntries.isEmpty()) Text("دیالوگ انتخاب‌نشده‌ای باقی نمانده است.", style = MaterialTheme.typography.bodySmall)
                            availableEntries.forEach { entry ->
                                Card(Modifier.fillMaxWidth().clickable(enabled = !busy) {
                                    scope.launch {
                                        try {
                                            ViewerDirectorOfficeRepository.addSelectorDialogue(context, personalDb, selectedPersonal!!.id, entry)
                                            refresh++
                                            message = "دیالوگ «${entry.roleTitle}» به‌عنوان نوبت جدید افزوده شد."
                                        } catch (e: Exception) { message = "افزودن دیالوگ انتخابی ناموفق بود: ${e.message}" }
                                    }
                                }) {
                                    Column(Modifier.padding(10.dp)) {
                                        Text("${entry.roleTitle} • ${entry.sectionTitle}", style = MaterialTheme.typography.titleSmall)
                                        Text(entry.text, style = MaterialTheme.typography.bodySmall)
                                        Text("انتخاب و افزودن به دفتر شخصی", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary)
                                    }
                                }
                            }
                        }
                        if (canEdit) item {
                            Text("افزودن راهنمای صحنه شخصی", style = MaterialTheme.typography.titleSmall)
                            OutlinedTextField(cueTitle, { cueTitle = it }, label = { Text("عنوان راهنما") }, modifier = Modifier.fillMaxWidth(), singleLine = true)
                            OutlinedTextField(cueInstruction, { cueInstruction = it }, label = { Text("توضیحات صحنه") }, modifier = Modifier.fillMaxWidth().heightIn(min = 90.dp), minLines = 2)
                            OutlinedButton(onClick = {
                                scope.launch {
                                    try {
                                        ViewerDirectorOfficeRepository.addStageCue(context, personalDb, selectedPersonal!!.id, cueTitle, cueInstruction)
                                        cueTitle = ""; cueInstruction = ""; refresh++
                                        message = "راهنمای صحنه شخصی ذخیره شد."
                                    } catch (e: Exception) { message = "افزودن راهنمای صحنه ناموفق بود: ${e.message}" }
                                }
                            }, enabled = cueTitle.isNotBlank() && cueInstruction.isNotBlank()) { Text("ذخیره راهنمای صحنه") }
                        }
                        item {
                            Text("نوبت‌های دفتر شخصی", style = MaterialTheme.typography.titleMedium)
                            OutlinedTextField(value = search, onValueChange = { search = it }, modifier = Modifier.fillMaxWidth(), label = { Text("جستجو در نوبت‌ها") }, singleLine = true)
                        }
                        val visible = personalTurns.filter { turn ->
                            val corpus = personalSegments[turn.id].orEmpty().joinToString(" ") { it.text } + " " + turn.title + " " + turn.turnNumber
                            search.isBlank() || corpus.contains(search, ignoreCase = true)
                        }
                        items(visible, key = { it.id }) { turn ->
                            Card(Modifier.fillMaxWidth()) {
                                Column(Modifier.padding(10.dp), verticalArrangement = Arrangement.spacedBy(5.dp)) {
                                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                        Text("نوبت ${turn.turnNumber} — ${turn.title}", style = MaterialTheme.typography.titleSmall, modifier = Modifier.weight(1f))
                                        if (canEdit) {
                                            IconButton(onClick = {
                                                scope.launch {
                                                    val current = personalTurns
                                                    val index = current.indexOfFirst { it.id == turn.id }
                                                    if (index > 0) {
                                                        val reordered = current.toMutableList().apply { add(index - 1, removeAt(index)) }
                                                        try {
                                                            ViewerDirectorOfficeRepository.reorderTurns(context, personalDb, reordered)
                                                            refresh++
                                                        } catch (e: Exception) { message = "تغییر ترتیب انجام نشد: ${e.message}" }
                                                    }
                                                }
                                            }, enabled = personalTurns.indexOfFirst { it.id == turn.id } > 0) { Icon(Icons.Default.ArrowUpward, "بالا") }
                                            IconButton(onClick = {
                                                scope.launch {
                                                    val current = personalTurns
                                                    val index = current.indexOfFirst { it.id == turn.id }
                                                    if (index >= 0 && index < current.lastIndex) {
                                                        val reordered = current.toMutableList().apply { add(index + 1, removeAt(index)) }
                                                        try {
                                                            ViewerDirectorOfficeRepository.reorderTurns(context, personalDb, reordered)
                                                            refresh++
                                                        } catch (e: Exception) { message = "تغییر ترتیب انجام نشد: ${e.message}" }
                                                    }
                                                }
                                            }, enabled = personalTurns.indexOfFirst { it.id == turn.id } in 0 until personalTurns.lastIndex) { Icon(Icons.Default.ArrowDownward, "پایین") }
                                        }
                                    }
                                    personalSegments[turn.id].orEmpty().forEach { segment ->
                                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                            Text(segment.text, modifier = Modifier.weight(1f))
                                            if (canEdit) IconButton(onClick = { editingSegment = segment; editingSegmentText = segment.text }) { Icon(Icons.Default.Edit, "ویرایش دیالوگ") }
                                        }
                                    }
                                    personalCues.filter { it.turnId == turn.id }.forEach { cue ->
                                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                            Text("${cue.title}: ${cue.instruction}", modifier = Modifier.weight(1f), style = MaterialTheme.typography.bodySmall)
                                            if (canEdit) IconButton(onClick = { editingCue = cue; editingCueTitle = cue.title; editingCueText = cue.instruction }) { Icon(Icons.Default.Edit, "ویرایش راهنمای صحنه") }
                                        }
                                    }
                                }
                            }
                        }
                        val globalPersonalCues = personalCues.filter { it.turnId == null }
                        if (globalPersonalCues.isNotEmpty()) item {
                            Text("راهنماهای عمومی صحنه", style = MaterialTheme.typography.titleSmall)
                            globalPersonalCues.forEach { cue ->
                                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                    Text("${cue.title}: ${cue.instruction}", modifier = Modifier.weight(1f))
                                    if (canEdit) IconButton(onClick = { editingCue = cue; editingCueTitle = cue.title; editingCueText = cue.instruction }) { Icon(Icons.Default.Edit, "ویرایش") }
                                }
                            }
                        }
                        item { Text("این دفتر شخصی امکان انتشار، کپی یا خروجی گرفتن ندارد.", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.error) }
                    }
                }
            }
            message?.let { Text(it, Modifier.fillMaxWidth().padding(12.dp), color = MaterialTheme.colorScheme.primary) }
            if (busy) Text("در حال پردازش…", Modifier.padding(12.dp))
        }
    }

    if (showMethodDialog && selectedOfficial != null) {
        AlertDialog(
            onDismissRequest = { showMethodDialog = false },
            title = { Text("روش ساخت دفتر شخصی") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    val sourceMode = selectedOfficialSourceMode
                    if (sourceMode != null) {
                        Text("روش محتوای منتشرشده: $sourceMode", style = MaterialTheme.typography.bodySmall)
                        if (methods.any { it.code != sourceMode }) {
                            Text("روش‌های متفاوت فقط به دلیل فعال‌سازی صریح همان روش توسط Admin در این فهرست آمده‌اند.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.tertiary)
                        }
                    }
                    methods.forEach { method ->
                        TextButton(onClick = {
                            val source = selectedOfficial!!
                            showMethodDialog = false
                            scope.launch {
                                busy = true
                                try {
                                    val id = withContext(Dispatchers.IO) {
                                        ViewerDirectorOfficePublicationImporter.createPersonalFromOfficial(context, contentDb, officialDb, personalDb, source, method.code)
                                    }
                                    selectedPersonal = personalDao.plan(id)
                                    tab = 1
                                    manualTitle = ""; manualText = ""; manualNumber = "1"
                                    refresh++
                                    message = "دفتر شخصی با روش «${method.label}» ساخته شد. این دفتر مستقل و غیرقابل انتشار رسمی است."
                                } catch (e: Exception) { message = "ساخت دفتر شخصی ناموفق بود: ${e.message}" }
                                finally { busy = false }
                            }
                        }, modifier = Modifier.fillMaxWidth()) { Text("ساخت با روش ${method.label}") }
                    }
                }
            },
            confirmButton = { TextButton(onClick = { showMethodDialog = false }) { Text("بستن") } }
        )
    }

    editingSegment?.let { segment ->
        AlertDialog(
            onDismissRequest = { editingSegment = null },
            title = { Text("ویرایش دیالوگ شخصی") },
            text = { OutlinedTextField(editingSegmentText, { editingSegmentText = it }, modifier = Modifier.fillMaxWidth().heightIn(min = 140.dp), minLines = 4) },
            confirmButton = {
                TextButton(onClick = {
                    scope.launch {
                        try {
                            require(canEdit && ViewerAccessPolicy.hasPermission(context, "director.personal.edit")) { "مجوز ویرایش فعال نیست." }
                            ViewerDirectorOfficeRepository.updateText(context, personalDb, segment.copy(text = editingSegmentText))
                            editingSegment = null; refresh++
                        } catch (e: Exception) { message = "ذخیره دیالوگ انجام نشد: ${e.message}" }
                    }
                }) { Text("ذخیره") }
            },
            dismissButton = { TextButton(onClick = { editingSegment = null }) { Text("انصراف") } }
        )
    }
    editingCue?.let { cue ->
        AlertDialog(
            onDismissRequest = { editingCue = null },
            title = { Text("ویرایش راهنمای صحنه شخصی") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(editingCueTitle, { editingCueTitle = it }, label = { Text("عنوان راهنما") }, modifier = Modifier.fillMaxWidth(), singleLine = true)
                    OutlinedTextField(editingCueText, { editingCueText = it }, label = { Text("توضیحات صحنه") }, modifier = Modifier.fillMaxWidth().heightIn(min = 120.dp), minLines = 3)
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    scope.launch {
                        try {
                            require(canEdit && ViewerAccessPolicy.hasPermission(context, "director.personal.edit")) { "مجوز ویرایش فعال نیست." }
                            ViewerDirectorOfficeRepository.updateCue(context, personalDb, cue.copy(title = editingCueTitle, instruction = editingCueText))
                            editingCue = null; refresh++
                        } catch (e: Exception) { message = "ذخیره راهنمای صحنه انجام نشد: ${e.message}" }
                    }
                }) { Text("ذخیره") }
            },
            dismissButton = { TextButton(onClick = { editingCue = null }) { Text("انصراف") } }
        )
    }
}

