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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.ArrowDownward
import androidx.compose.material.icons.filled.ArrowUpward
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.FileOpen
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.PersonAdd
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
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
import androidx.compose.runtime.DisposableEffect
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
import androidx.compose.ui.platform.LocalView
import com.example.bookapp.data.AppDatabase
import com.example.bookapp.data.DirectorPlanEntity
import com.example.bookapp.data.DirectorOfficeSharedContract
import com.example.bookapp.data.DirectorPlanVersionEntity
import com.example.bookapp.data.DirectorStageCueEntity
import com.example.bookapp.data.DirectorTextSegmentEntity
import com.example.bookapp.data.DirectorTurnEntity
import com.example.bookapp.data.ViewerPermissionEngine
import com.example.bookapp.data.ViewerDirectorOfficeDatabase
import com.example.bookapp.data.ViewerDirectorOfficePublicationImporter
import com.example.bookapp.data.ViewerDirectorOfficeRepository
import com.example.bookapp.data.Prefs
import com.example.bookapp.data.DirectorParticipantEntity
import com.example.bookapp.ui.theme.colorSchemeFor
import com.example.bookapp.ui.theme.typographyFor
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ViewerDirectorOfficeScreen(
    contentDb: AppDatabase,
    taziehId: Long? = null,
    policyPermissions: Map<String, Boolean>,
    onBack: () -> Unit
) {
    val context = LocalContext.current
    val localView = LocalView.current
    val scope = rememberCoroutineScope()
    var darkMode by remember { mutableStateOf(Prefs.isDarkMode(context)) }
    var autoDarkMode by remember { mutableStateOf(Prefs.getAutoDarkMode(context)) }
    var fontScale by remember { mutableStateOf(Prefs.getFontScale(context)) }
    var lineSpacing by remember { mutableStateOf(Prefs.getLineSpacing(context)) }
    var fontChoice by remember { mutableStateOf(Prefs.getFontChoice(context)) }
    var themeChoice by remember { mutableStateOf(Prefs.getThemeChoice(context)) }
    var keepScreenOn by remember { mutableStateOf(Prefs.getKeepScreenOn(context)) }
    var showOfficeSettingsDialog by remember { mutableStateOf(false) }
    val baseOfficeTypography = remember(fontChoice, fontScale, lineSpacing) {
        val base = typographyFor(fontChoice, fontScale)
        base.copy(
            bodyLarge = base.bodyLarge.copy(lineHeight = base.bodyLarge.fontSize * lineSpacing),
            bodyMedium = base.bodyMedium.copy(lineHeight = base.bodyMedium.fontSize * lineSpacing),
            bodySmall = base.bodySmall.copy(lineHeight = base.bodySmall.fontSize * lineSpacing)
        )
    }
    DisposableEffect(localView, keepScreenOn) {
        localView.keepScreenOn = keepScreenOn
        onDispose { localView.keepScreenOn = false }
    }
    val officialDb = remember { ViewerDirectorOfficeDatabase.official(context) }
    val personalDb = remember { ViewerDirectorOfficeDatabase.personal(context) }
    val officialDao = remember { officialDb.directorDao() }
    val personalDao = remember { personalDb.directorDao() }

    val canOfficial = ViewerPermissionEngine.can(policyPermissions, "director.official.view")
    val canCreate = ViewerPermissionEngine.can(policyPermissions, "director.personal.create")
    val canEdit = ViewerPermissionEngine.can(policyPermissions, "director.personal.edit")
    val methods = DirectorOfficeSharedContract.buildMethods.filter {
        DirectorOfficeSharedContract.canCreatePersonal(policyPermissions, it.code)
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
    var officialParticipants by remember { mutableStateOf<Map<Long, List<com.example.bookapp.data.DirectorParticipantEntity>>>(emptyMap()) }
    var personalParticipants by remember { mutableStateOf<Map<Long, List<com.example.bookapp.data.DirectorParticipantEntity>>>(emptyMap()) }
    var selectedPersonalMethod by remember { mutableStateOf("") }
    var selectorEntries by remember { mutableStateOf(emptyList<ViewerDirectorOfficePublicationImporter.SelectorEntry>()) }
    var manualTitle by remember { mutableStateOf("") }
    var manualText by remember { mutableStateOf("") }
    var manualNumber by remember { mutableStateOf("1") }
    var cueTitle by remember { mutableStateOf("") }
    var cueInstruction by remember { mutableStateOf("") }
    var cueType by remember { mutableStateOf(DirectorStageCueEntity.CUE_OTHER) }
    var cueTurnId by remember { mutableStateOf<Long?>(null) }
    var cueTurnMenuExpanded by remember { mutableStateOf(false) }
    var editingCueType by remember { mutableStateOf(DirectorStageCueEntity.CUE_OTHER) }
    var search by remember { mutableStateOf("") }
    var showMethodDialog by remember { mutableStateOf(false) }
    var editingSegment by remember { mutableStateOf<DirectorTextSegmentEntity?>(null) }
    var editingSegmentText by remember { mutableStateOf("") }
    var editingCue by remember { mutableStateOf<DirectorStageCueEntity?>(null) }
    var editingCueTitle by remember { mutableStateOf("") }
    var editingCueText by remember { mutableStateOf("") }
    var editingTurn by remember { mutableStateOf<DirectorTurnEntity?>(null) }
    var editingTurnNumber by remember { mutableStateOf("") }
    var editingTurnTitle by remember { mutableStateOf("") }
    var addingDialogueToTurn by remember { mutableStateOf<DirectorTurnEntity?>(null) }
    var newDialogueText by remember { mutableStateOf("") }
    var managingParticipantsFor by remember { mutableStateOf<DirectorTurnEntity?>(null) }
    var deleteTurnCandidate by remember { mutableStateOf<DirectorTurnEntity?>(null) }
    var deleteSegmentCandidate by remember { mutableStateOf<DirectorTextSegmentEntity?>(null) }
    var deleteCueCandidate by remember { mutableStateOf<DirectorStageCueEntity?>(null) }
    var deletePlanCandidate by remember { mutableStateOf<DirectorPlanEntity?>(null) }
    var customParticipantTitle by remember { mutableStateOf("") }

    var selectedTaziehUid by remember(taziehId) { mutableStateOf<String?>(null) }
    var selectedTaziehResolved by remember(taziehId) { mutableStateOf(taziehId == null) }

    // Build searchable text once when loaded rows change, not on every keystroke/recomposition.
    val officialSearchIndex = remember(officialTurns, officialSegments, officialParticipants) {
        officialTurns.associate { turn ->
            val roles = officialParticipants[turn.id].orEmpty().joinToString(" ") { it.roleTitle }
            turn.id to (officialSegments[turn.id].orEmpty().joinToString(" ") { it.text } + " " + turn.title + " " + turn.turnNumber + " " + roles)
        }
    }
    val personalSearchIndex = remember(personalTurns, personalSegments, personalParticipants) {
        personalTurns.associate { turn ->
            val roles = personalParticipants[turn.id].orEmpty().joinToString(" ") { it.roleTitle }
            turn.id to (personalSegments[turn.id].orEmpty().joinToString(" ") { it.text } + " " + turn.title + " " + turn.turnNumber + " " + roles)
        }
    }
    val officialCuesByTurn = remember(officialCues) { officialCues.filter { it.turnId != null }.groupBy { it.turnId } }
    val personalCuesByTurn = remember(personalCues) { personalCues.filter { it.turnId != null }.groupBy { it.turnId } }

    suspend fun reload() {
        officialPlans = if (canOfficial) {
            officialDao.plansPage("", 500, 0)
                .filter { it.status == DirectorPlanEntity.STATUS_PUBLISHED }
                .filter { taziehId == null || (selectedTaziehUid != null && it.taziehUid == selectedTaziehUid) }
        } else emptyList()
        personalPlans = personalDao.plansPage("", 500, 0)
            .filter { it.uid.startsWith("personal-") }
            .filter { taziehId == null || (selectedTaziehUid != null && it.taziehUid == selectedTaziehUid) }
        selectedOfficial = selectedOfficial?.let { old -> officialPlans.firstOrNull { it.uid == old.uid } }
        selectedPersonal = selectedPersonal?.let { old -> personalPlans.firstOrNull { it.uid == old.uid } }
        val officialPlan = selectedOfficial
        if (officialPlan != null) {
            val version = officialDao.versions(officialPlan.id).firstOrNull { it.status == DirectorPlanVersionEntity.STATUS_PUBLISHED }
            selectedOfficialSourceMode = version?.changeNotes?.substringAfter("روش تکمیل دفتر: ", "")?.takeIf { it in DirectorOfficeSharedContract.methodCodes }
            selectedOfficialVersionLabel = version?.let { "نسخه ${it.versionNumber} — ${it.title}" }.orEmpty()
            officialTurns = version?.let { officialDao.turns(it.id) }.orEmpty()
            officialCues = version?.let { officialDao.stageCues(it.id) }.orEmpty()
            val officialTextRows = if (officialTurns.isEmpty()) emptyList() else officialDao.textSegmentsForTurns(officialTurns.map { it.id })
            officialSegments = officialTextRows.groupBy { it.turnId }
            officialParticipants = if (officialTurns.isEmpty()) emptyMap() else officialDao.participantsForTurns(officialTurns.map { it.id }).groupBy { it.turnId }
        } else {
            officialTurns = emptyList(); officialCues = emptyList(); officialSegments = emptyMap(); officialParticipants = emptyMap(); selectedOfficialSourceMode = null; selectedOfficialVersionLabel = ""
        }
        val personalPlan = selectedPersonal
        if (personalPlan != null) {
            val version = personalDao.versions(personalPlan.id).firstOrNull()
            personalTurns = version?.let { personalDao.turns(it.id) }.orEmpty()
            personalCues = version?.let { personalDao.stageCues(it.id) }.orEmpty()
            val personalTextRows = if (personalTurns.isEmpty()) emptyList() else personalDao.textSegmentsForTurns(personalTurns.map { it.id })
            personalSegments = personalTextRows.groupBy { it.turnId }
            personalParticipants = if (personalTurns.isEmpty()) emptyMap() else personalDao.participantsForTurns(personalTurns.map { it.id }).groupBy { it.turnId }
            selectedPersonalMethod = version?.changeNotes?.substringAfter("روش ساخت: ", "")?.substringBefore("؛").orEmpty()
            selectorEntries = ViewerDirectorOfficePublicationImporter.selectorEntries(contentDb, personalPlan.taziehUid)
        } else {
            personalTurns = emptyList(); personalCues = emptyList(); personalSegments = emptyMap(); personalParticipants = emptyMap(); selectedPersonalMethod = ""; selectorEntries = emptyList()
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

    LaunchedEffect(taziehId) {
        selectedTaziehResolved = taziehId == null
        selectedTaziehUid = null
        if (taziehId != null) {
            selectedTaziehUid = withContext(Dispatchers.IO) { contentDb.taziehDao().getById(taziehId)?.uid }
            selectedTaziehResolved = true
        }
    }

    LaunchedEffect(refresh, canOfficial, selectedTaziehResolved, selectedTaziehUid) {
        if (!selectedTaziehResolved) return@LaunchedEffect
        try { reload() } catch (e: Exception) { message = "بارگذاری دفتر ناموفق بود: ${e.message}" }
    }

    MaterialTheme(
        colorScheme = colorSchemeFor(themeChoice, darkMode),
        typography = baseOfficeTypography
    ) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("دفتر معین‌البکا") },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "بازگشت") } },
                actions = {
                    IconButton(onClick = { showOfficeSettingsDialog = true }) {
                        Icon(Icons.Default.Settings, contentDescription = "تنظیمات دفتر")
                    }
                }
            )
        }
    ) { padding ->
        Column(Modifier.fillMaxSize().padding(padding)) {
            if (taziehId != null) {
                Text(
                    "دفتر معین‌البکای همین مجلس",
                    modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp),
                    style = MaterialTheme.typography.titleSmall
                )
            }
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
                            Icon(Icons.Default.FileOpen, null); Text("دریافت / به‌روزرسانی دفتر رسمی")
                        }
                    }
                    Text("فقط نسخه منتشرشده و امضاشده Admin نمایش داده می‌شود. با واردکردن بسته جدیدتر، نسخه رسمی قبلی همین مجلس به‌صورت کامل جایگزین می‌شود؛ دفتر شخصی کاربر تغییر نمی‌کند. این بخش امکان ویرایش، کپی، خروجی یا انتشار ندارد.", Modifier.padding(horizontal = 14.dp), style = MaterialTheme.typography.bodySmall)
                    LazyColumn(Modifier.weight(1f).fillMaxWidth().padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        if (officialPlans.isEmpty()) item {
                            Text(
                                if (taziehId == null) "هنوز بسته رسمی از Admin دریافت نشده است."
                                else "برای این مجلس هنوز بسته رسمی منتشرشده و دریافت‌شده‌ای وجود ندارد. ابتدا بسته همین مجلس را از Admin دریافت کنید.",
                                modifier = Modifier.padding(vertical = 12.dp)
                            )
                        }
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
                                DirectorOfficeSearchField(value = search, onValueChange = { search = it }, label = "جستجو در شماره، نقش و متن نوبت")
                            }
                            val filtered = officialTurns.filter { turn ->
                                search.isBlank() || officialSearchIndex[turn.id].orEmpty().contains(search, ignoreCase = true)
                            }
                            items(filtered, key = { it.id }) { turn ->
                                Card(Modifier.fillMaxWidth()) {
                                    Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                        Text("نوبت ${turn.turnNumber} — ${turn.title}", style = MaterialTheme.typography.titleSmall)
                                        val officialRoleNames = officialParticipants[turn.id].orEmpty().sortedBy { it.orderIndex }.joinToString("، ") { it.roleTitle }
                                        if (officialRoleNames.isNotBlank()) Text("نقش‌ها: $officialRoleNames", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.secondary)
                                        officialSegments[turn.id].orEmpty().forEach { segment -> Text(segment.text) }
                                        val cues = officialCuesByTurn[turn.id].orEmpty()
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
                    if (!canCreate) {
                        Text("مدیر مجوز ساخت دفتر شخصی را برای این Viewer فعال نکرده است.", color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
                    } else if (methods.isEmpty()) {
                        Text("مجوز اصلی ساخت فعال است، اما مدیر هنوز هیچ روش ساختی را برای این Viewer فعال نکرده است.", color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
                    } else {
                        Text("روش‌های مجاز مدیر: ${methods.joinToString("، ") { it.label }}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.primary)
                        OutlinedButton(
                            onClick = { showMethodDialog = true },
                            enabled = !busy && selectedTaziehResolved && (taziehId == null || selectedTaziehUid != null),
                            modifier = Modifier.fillMaxWidth()
                        ) { Text("ساخت دفتر شخصی جدید") }
                    }
                    if (!canOfficial && methods.any { it.requiresOfficialSource }) {
                        Text("روش‌های شماره‌محور، متن کامل و انتخاب‌گر به دفتر رسمی دریافت‌شده و مجوز مشاهده آن نیاز دارند؛ روش دستی مستقل است.", color = MaterialTheme.colorScheme.tertiary, style = MaterialTheme.typography.bodySmall)
                    }
                    if (canCreate && methods.isEmpty()) Text("مدیر هنوز هیچ روش ساخت دفتری را فعال نکرده است.", color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
                }
                LazyColumn(Modifier.weight(1f).fillMaxWidth().padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    if (personalPlans.isEmpty()) item { Text("هنوز دفتر شخصی ساخته نشده است. از دکمه «ساخت دفتر شخصی جدید» استفاده کنید.") }
                    items(personalPlans, key = { it.uid }) { plan ->
                        Card(Modifier.fillMaxWidth().clickable {
                            selectedPersonal = plan
                            scope.launch { reload() }
                        }, colors = CardDefaults.cardColors(containerColor = if (selectedPersonal?.uid == plan.uid) MaterialTheme.colorScheme.secondaryContainer else MaterialTheme.colorScheme.surfaceVariant)) {
                            Row(Modifier.fillMaxWidth().padding(12.dp), verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
                                Column(Modifier.weight(1f)) {
                                    Text(plan.title, style = MaterialTheme.typography.titleMedium)
                                    Text("دفتر شخصی مستقل • روش: ${plan.description.substringAfter("روش ساخت: ", "نامشخص").substringBefore("؛")} • غیرقابل انتشار رسمی", style = MaterialTheme.typography.bodySmall)
                                }
                                if (canEdit) IconButton(onClick = { deletePlanCandidate = plan }) {
                                    Icon(Icons.Default.Delete, contentDescription = "حذف دفتر شخصی")
                                }
                            }
                        }
                    }
                    if (selectedPersonal != null) {
                        item {
                            Text(selectedPersonal!!.title, style = MaterialTheme.typography.titleMedium)
                            Text("${personalTurns.size} نوبت • ${personalTurns.sumOf { personalSegments[it.id].orEmpty().size }} بخش دیالوگ • ${personalCues.size} راهنمای صحنه", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.secondary)
                        }
                        if (canEdit) item {
                            Text("افزودن نوبت جدید به دفتر شخصی", style = MaterialTheme.typography.titleMedium)
                            DirectorOfficeTurnInputFields(
                                number = manualNumber,
                                onNumberChange = { manualNumber = it },
                                title = manualTitle,
                                onTitleChange = { manualTitle = it },
                                text = manualText,
                                onTextChange = { manualText = it }
                            )
                            Button(onClick = {
                                scope.launch {
                                    try {
                                        ViewerDirectorOfficeRepository.addTurn(context, personalDb, selectedPersonal!!.id, manualNumber.toIntOrNull() ?: (personalTurns.size + 1), manualTitle, manualText)
                                        manualNumber = ((manualNumber.toIntOrNull() ?: personalTurns.size + 1) + 1).toString()
                                        manualTitle = ""; manualText = ""; refresh++
                                        message = "نوبت جدید به دفتر شخصی اضافه شد."
                                    } catch (e: Exception) { message = "افزودن نوبت دستی ناموفق بود: ${e.message}" }
                                }
                            }, enabled = manualTitle.isNotBlank() && manualText.isNotBlank()) { Text("افزودن نوبت") }
                        }
                        if (selectedPersonalMethod == DirectorOfficeSharedContract.SELECTOR && canEdit && ViewerPermissionEngine.can(policyPermissions, "director.personal.method.selector")) item {
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
                            DirectorOfficeStageCueInputFields(
                                title = cueTitle,
                                onTitleChange = { cueTitle = it },
                                instruction = cueInstruction,
                                onInstructionChange = { cueInstruction = it }
                            )
                            androidx.compose.foundation.layout.Box {
                                OutlinedButton(onClick = { cueTurnMenuExpanded = true }) {
                                    Text(cueTurnId?.let { id -> personalTurns.firstOrNull { it.id == id }?.let { "نوبت ${it.turnNumber} — ${it.title}" } } ?: "راهنمای عمومی دفتر")
                                }
                                DropdownMenu(expanded = cueTurnMenuExpanded, onDismissRequest = { cueTurnMenuExpanded = false }) {
                                    DropdownMenuItem(text = { Text("راهنمای عمومی دفتر") }, onClick = { cueTurnId = null; cueTurnMenuExpanded = false })
                                    personalTurns.forEach { targetTurn ->
                                        DropdownMenuItem(text = { Text("نوبت ${targetTurn.turnNumber} — ${targetTurn.title}") }, onClick = { cueTurnId = targetTurn.id; cueTurnMenuExpanded = false })
                                    }
                                }
                            }
                            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                                listOf(
                                    DirectorStageCueEntity.CUE_OTHER to "سایر",
                                    DirectorStageCueEntity.CUE_MOVE to "حرکت",
                                    DirectorStageCueEntity.CUE_MUSIC to "موسیقی",
                                    DirectorStageCueEntity.CUE_LIGHT to "نور",
                                    DirectorStageCueEntity.CUE_PAUSE to "مکث",
                                    DirectorStageCueEntity.CUE_IMMEDIATE to "بلافاصله"
                                ).forEach { (key, label) ->
                                    FilterChip(selected = cueType == key, onClick = { cueType = key }, label = { Text(label) })
                                }
                            }
                            OutlinedButton(onClick = {
                                scope.launch {
                                    try {
                                        ViewerDirectorOfficeRepository.addStageCue(context, personalDb, selectedPersonal!!.id, cueTitle, cueInstruction, turnId = cueTurnId, cueType = cueType)
                                        cueTitle = ""; cueInstruction = ""; cueType = DirectorStageCueEntity.CUE_OTHER; cueTurnId = null; refresh++
                                        message = "راهنمای صحنه شخصی ذخیره شد."
                                    } catch (e: Exception) { message = "افزودن راهنمای صحنه ناموفق بود: ${e.message}" }
                                }
                            }, enabled = cueTitle.isNotBlank() && cueInstruction.isNotBlank()) { Text("ذخیره راهنمای صحنه") }
                        }
                        item {
                            Text("نوبت‌های دفتر شخصی", style = MaterialTheme.typography.titleMedium)
                            if (personalTurns.isEmpty()) Text("این دفتر هنوز نوبتی ندارد. از فرم افزودن نوبت جدید استفاده کنید.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.secondary)
                        }
                        item {
                            DirectorOfficeSearchField(
                                value = search,
                                onValueChange = { search = it },
                                label = "جستجو در نوبت‌ها",
                                showSearchIcon = false
                            )
                        }
                        val visible = personalTurns.filter { turn ->
                            search.isBlank() || personalSearchIndex[turn.id].orEmpty().contains(search, ignoreCase = true)
                        }
                        items(visible, key = { it.id }) { turn ->
                            Card(Modifier.fillMaxWidth()) {
                                Column(Modifier.padding(10.dp), verticalArrangement = Arrangement.spacedBy(5.dp)) {
                                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                        Text("نوبت ${turn.turnNumber} — ${turn.title}", style = MaterialTheme.typography.titleSmall, modifier = Modifier.weight(1f))
                                        if (canEdit) {
                                            IconButton(onClick = { editingTurn = turn; editingTurnNumber = turn.turnNumber.toString(); editingTurnTitle = turn.title }) { Icon(Icons.Default.Edit, "ویرایش عنوان و شماره نوبت") }
                                            IconButton(onClick = { addingDialogueToTurn = turn; newDialogueText = "" }) { Icon(Icons.Default.Add, "افزودن دیالوگ") }
                                            IconButton(onClick = { managingParticipantsFor = turn; customParticipantTitle = "" }) { Icon(Icons.Default.PersonAdd, "مدیریت نقش‌های نوبت") }
                                            IconButton(onClick = { deleteTurnCandidate = turn }) { Icon(Icons.Default.Delete, "حذف نوبت") }
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
                                    val roleNames = personalParticipants[turn.id].orEmpty().sortedBy { it.orderIndex }.joinToString("، ") { it.roleTitle }
                                    if (roleNames.isNotBlank()) Text("نقش‌ها: $roleNames", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.secondary)
                                    personalSegments[turn.id].orEmpty().forEach { segment ->
                                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                            Text(segment.text, modifier = Modifier.weight(1f))
                                            if (canEdit) IconButton(onClick = { editingSegment = segment; editingSegmentText = segment.text }) { Icon(Icons.Default.Edit, "ویرایش دیالوگ") }
                                            if (canEdit) IconButton(onClick = { deleteSegmentCandidate = segment }) { Icon(Icons.Default.Delete, "حذف دیالوگ") }
                                        }
                                    }
                                    personalCuesByTurn[turn.id].orEmpty().forEach { cue ->
                                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                            Text("${cue.title}: ${cue.instruction}", modifier = Modifier.weight(1f), style = MaterialTheme.typography.bodySmall)
                                            if (canEdit) IconButton(onClick = { editingCue = cue; editingCueTitle = cue.title; editingCueText = cue.instruction; editingCueType = cue.cueType }) { Icon(Icons.Default.Edit, "ویرایش راهنمای صحنه") }
                                            if (canEdit) IconButton(onClick = { deleteCueCandidate = cue }) { Icon(Icons.Default.Delete, "حذف راهنمای صحنه") }
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
                                    if (canEdit) IconButton(onClick = { editingCue = cue; editingCueTitle = cue.title; editingCueText = cue.instruction; editingCueType = cue.cueType }) { Icon(Icons.Default.Edit, "ویرایش") }
                                    if (canEdit) IconButton(onClick = { deleteCueCandidate = cue }) { Icon(Icons.Default.Delete, "حذف") }
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



    if (showOfficeSettingsDialog) {
        DirectorOfficeAppearanceSettingsDialog(
            darkMode = darkMode,
            onDarkModeChange = { darkMode = it; Prefs.setDarkMode(context, it) },
            autoDarkMode = autoDarkMode,
            onAutoDarkModeChange = { autoDarkMode = it; Prefs.setAutoDarkMode(context, it) },
            fontScale = fontScale,
            onFontScaleChange = { fontScale = it; Prefs.setFontScale(context, it) },
            lineSpacing = lineSpacing,
            onLineSpacingChange = { lineSpacing = it; Prefs.setLineSpacing(context, it) },
            fontChoice = fontChoice,
            onFontChoiceChange = { fontChoice = it; Prefs.setFontChoice(context, it) },
            themeChoice = themeChoice,
            onThemeChoiceChange = { themeChoice = it; Prefs.setThemeChoice(context, it) },
            keepScreenOn = keepScreenOn,
            onKeepScreenOnChange = { keepScreenOn = it; Prefs.setKeepScreenOn(context, it) },
            onDismiss = { showOfficeSettingsDialog = false }
        )
    }

    if (showMethodDialog) {
        AlertDialog(
            onDismissRequest = { showMethodDialog = false },
            title = { Text("روش ساخت دفتر شخصی") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    val sourceMode = selectedOfficialSourceMode
                    if (selectedOfficial != null && sourceMode != null) {
                        Text("روش محتوای منتشرشده: $sourceMode", style = MaterialTheme.typography.bodySmall)
                    }
                    if (selectedOfficial == null && methods.any { it.requiresOfficialSource }) {
                        Text("برای روش‌های مبتنی بر محتوای مجلس، ابتدا بسته رسمی را دریافت و یک دفتر را در زبانه دفتر رسمی انتخاب کنید. روش دستی بدون دفتر رسمی قابل ساخت است.", style = MaterialTheme.typography.bodySmall)
                    }
                    methods.forEach { method ->
                        TextButton(onClick = {
                            showMethodDialog = false
                            if (method.requiresOfficialSource && !canOfficial) {
                                message = "برای این روش، مدیر باید مجوز مشاهده دفتر رسمی را نیز فعال کند."
                                return@TextButton
                            }
                            if (method.requiresOfficialSource && selectedOfficial == null) {
                                tab = 0
                                message = "ابتدا یک دفتر رسمی را انتخاب کنید؛ سپس از دفتر شخصی روش ساخت را اجرا کنید."
                                return@TextButton
                            }
                            scope.launch {
                                busy = true
                                try {
                                    val id = withContext(Dispatchers.IO) {
                                        if (!method.requiresOfficialSource) {
                                            ViewerDirectorOfficePublicationImporter.createBlankPersonal(context, personalDb, selectedTaziehUid)
                                        } else {
                                            ViewerDirectorOfficePublicationImporter.createPersonalFromOfficial(context, contentDb, officialDb, personalDb, selectedOfficial!!, method.code)
                                        }
                                    }
                                    selectedPersonal = personalDao.plan(id)
                                    tab = 1
                                    manualTitle = ""; manualText = ""; manualNumber = "1"
                                    refresh++
                                    message = "دفتر شخصی با روش «${method.label}» ساخته شد. این دفتر مستقل و غیرقابل انتشار رسمی است."
                                } catch (e: Exception) { message = "ساخت دفتر شخصی ناموفق بود: ${e.message}" }
                                finally { busy = false }
                            }
                        }, modifier = Modifier.fillMaxWidth()) {
                            Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                Text("ساخت با روش ${method.label}")
                                Text(method.description, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        }
                    }
                }
            },
            confirmButton = { TextButton(onClick = { showMethodDialog = false }) { Text("بستن") } }
        )
    }

    deletePlanCandidate?.let { plan ->
        AlertDialog(
            onDismissRequest = { deletePlanCandidate = null },
            title = { Text("حذف دفتر شخصی") },
            text = { Text("دفتر «${plan.title}» از فهرست فعال این دستگاه خارج شود؟ دفتر رسمی و اطلاعات کتابخانه تغییر نمی‌کنند.") },
            confirmButton = { TextButton(onClick = {
                scope.launch {
                    try {
                        ViewerDirectorOfficeRepository.deletePlan(context, personalDb, plan.id)
                        if (selectedPersonal?.id == plan.id) selectedPersonal = null
                        deletePlanCandidate = null; refresh++; message = "دفتر شخصی از فهرست فعال خارج شد."
                    } catch (e: Exception) { message = "حذف دفتر انجام نشد: ${e.message}" }
                }
            }) { Text("حذف") } },
            dismissButton = { TextButton(onClick = { deletePlanCandidate = null }) { Text("انصراف") } }
        )
    }

    managingParticipantsFor?.let { turn ->
        val existing = personalParticipants[turn.id].orEmpty().sortedBy { it.orderIndex }
        val availableRoles = selectorEntries.distinctBy { it.roleUid }.sortedBy { it.roleTitle }
        AlertDialog(
            onDismissRequest = { managingParticipantsFor = null },
            title = { Text("مدیریت نقش‌های نوبت ${turn.turnNumber}") },
            text = {
                Column(Modifier.heightIn(max = 460.dp).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("نقش‌های ثبت‌شده در این نوبت")
                    existing.forEach { participant ->
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            Text(participant.roleTitle, modifier = Modifier.weight(1f))
                            TextButton(onClick = {
                                scope.launch {
                                    try {
                                        ViewerDirectorOfficeRepository.removeParticipant(context, personalDb, participant.id, turn.id)
                                        refresh++
                                    } catch (e: Exception) { message = "حذف نقش انجام نشد: ${e.message}" }
                                }
                            }) { Text("حذف") }
                        }
                    }
                    Text("افزودن نقش از کتابخانه مجلس")
                    availableRoles.filterNot { role -> existing.any { it.roleUid == role.roleUid } }.forEach { role ->
                        TextButton(onClick = {
                            scope.launch {
                                try {
                                    ViewerDirectorOfficeRepository.addParticipant(context, personalDb, turn.id, role.roleUid, role.roleTitle)
                                    refresh++
                                } catch (e: Exception) { message = "افزودن نقش انجام نشد: ${e.message}" }
                            }
                        }, modifier = Modifier.fillMaxWidth()) { Text(role.roleTitle) }
                    }
                    OutlinedTextField(
                        value = customParticipantTitle,
                        onValueChange = { customParticipantTitle = it },
                        label = { Text("نقش دیگر / عنوان دلخواه") },
                        modifier = Modifier.fillMaxWidth(), singleLine = true
                    )
                    TextButton(enabled = customParticipantTitle.isNotBlank(), onClick = {
                        val title = customParticipantTitle.trim()
                        scope.launch {
                            try {
                                ViewerDirectorOfficeRepository.addParticipant(context, personalDb, turn.id, "personal-role-${java.util.UUID.randomUUID()}", title)
                                customParticipantTitle = ""
                                refresh++
                            } catch (e: Exception) { message = "افزودن نقش انجام نشد: ${e.message}" }
                        }
                    }) { Text("افزودن نقش دلخواه") }
                }
            },
            confirmButton = { TextButton(onClick = { managingParticipantsFor = null }) { Text("پایان") } }
        )
    }

    deleteTurnCandidate?.let { turn ->
        AlertDialog(
            onDismissRequest = { deleteTurnCandidate = null },
            title = { Text("حذف نوبت") },
            text = { Text("نوبت ${turn.turnNumber} — ${turn.title} و دیالوگ‌ها و نقش‌های وابسته به آن حذف شوند؟") },
            confirmButton = { TextButton(onClick = {
                scope.launch {
                    try {
                        ViewerDirectorOfficeRepository.deleteTurn(context, personalDb, turn.id)
                        deleteTurnCandidate = null; refresh++; message = "نوبت حذف شد."
                    } catch (e: Exception) { message = "حذف نوبت انجام نشد: ${e.message}" }
                }
            }) { Text("حذف") } },
            dismissButton = { TextButton(onClick = { deleteTurnCandidate = null }) { Text("انصراف") } }
        )
    }

    deleteSegmentCandidate?.let { segment ->
        AlertDialog(
            onDismissRequest = { deleteSegmentCandidate = null },
            title = { Text("حذف دیالوگ") },
            text = { Text("این بخش از متن نوبت حذف شود؟") },
            confirmButton = { TextButton(onClick = {
                scope.launch {
                    try {
                        ViewerDirectorOfficeRepository.deleteTextSegment(context, personalDb, segment)
                        deleteSegmentCandidate = null; refresh++; message = "دیالوگ حذف شد."
                    } catch (e: Exception) { message = "حذف دیالوگ انجام نشد: ${e.message}" }
                }
            }) { Text("حذف") } },
            dismissButton = { TextButton(onClick = { deleteSegmentCandidate = null }) { Text("انصراف") } }
        )
    }

    deleteCueCandidate?.let { cue ->
        AlertDialog(
            onDismissRequest = { deleteCueCandidate = null },
            title = { Text("حذف راهنمای صحنه") },
            text = { Text("راهنمای «${cue.title}» حذف شود؟") },
            confirmButton = { TextButton(onClick = {
                scope.launch {
                    try {
                        ViewerDirectorOfficeRepository.deleteStageCue(context, personalDb, cue.id)
                        deleteCueCandidate = null; refresh++; message = "راهنمای صحنه حذف شد."
                    } catch (e: Exception) { message = "حذف راهنما انجام نشد: ${e.message}" }
                }
            }) { Text("حذف") } },
            dismissButton = { TextButton(onClick = { deleteCueCandidate = null }) { Text("انصراف") } }
        )
    }

    editingTurn?.let { turn ->
        AlertDialog(
            onDismissRequest = { editingTurn = null },
            title = { Text("ویرایش مشخصات نوبت") },
            text = {
                Column(Modifier.heightIn(max = 420.dp).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    OutlinedTextField(value = editingTurnNumber, onValueChange = { editingTurnNumber = it.filter(Char::isDigit).take(6) }, label = { Text("شماره نوبت") }, modifier = Modifier.fillMaxWidth(), singleLine = true)
                    OutlinedTextField(value = editingTurnTitle, onValueChange = { editingTurnTitle = it }, label = { Text("عنوان نقش / نوبت") }, modifier = Modifier.fillMaxWidth(), singleLine = true)
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    scope.launch {
                        try {
                            ViewerDirectorOfficeRepository.updateTurn(context, personalDb, turn.copy(turnNumber = editingTurnNumber.toIntOrNull() ?: 0, title = editingTurnTitle.trim()))
                            editingTurn = null; refresh++; message = "مشخصات نوبت ذخیره شد."
                        } catch (e: Exception) { message = "ویرایش نوبت انجام نشد: ${e.message}" }
                    }
                }, enabled = editingTurnNumber.toIntOrNull()?.let { it > 0 } == true && editingTurnTitle.isNotBlank()) { Text("ذخیره") }
            },
            dismissButton = { TextButton(onClick = { editingTurn = null }) { Text("انصراف") } }
        )
    }

    addingDialogueToTurn?.let { turn ->
        AlertDialog(
            onDismissRequest = { addingDialogueToTurn = null },
            title = { Text("افزودن دیالوگ به نوبت ${turn.turnNumber}") },
            text = {
                Column(Modifier.heightIn(max = 480.dp).verticalScroll(rememberScrollState())) {
                    DirectorOfficeDialogueTextField(value = newDialogueText, onValueChange = { newDialogueText = it }, minHeight = 180, maxHeight = 360, minLines = 6)
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    scope.launch {
                        try {
                            ViewerDirectorOfficeRepository.addDialogueSegment(context, personalDb, turn.id, newDialogueText)
                            addingDialogueToTurn = null; newDialogueText = ""; refresh++; message = "دیالوگ جدید به نوبت افزوده شد."
                        } catch (e: Exception) { message = "افزودن دیالوگ انجام نشد: ${e.message}" }
                    }
                }, enabled = newDialogueText.isNotBlank()) { Text("افزودن") }
            },
            dismissButton = { TextButton(onClick = { addingDialogueToTurn = null }) { Text("انصراف") } }
        )
    }

    editingSegment?.let { segment ->
        AlertDialog(
            onDismissRequest = { editingSegment = null },
            title = { Text("ویرایش دیالوگ شخصی") },
            text = { Column(Modifier.heightIn(max = 480.dp).verticalScroll(rememberScrollState())) { DirectorOfficeDialogueTextField(value = editingSegmentText, onValueChange = { editingSegmentText = it }, minHeight = 180, maxHeight = 360, minLines = 6) } },
            confirmButton = {
                TextButton(onClick = {
                    scope.launch {
                        try {
                            require(canEdit && ViewerPermissionEngine.can(policyPermissions, "director.personal.edit")) { "مجوز ویرایش فعال نیست." }
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
                DirectorOfficeStageCueInputFields(
                    title = editingCueTitle,
                    onTitleChange = { editingCueTitle = it },
                    instruction = editingCueText,
                    onInstructionChange = { editingCueText = it },
                    instructionMinHeight = 120
                )
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    listOf(
                        DirectorStageCueEntity.CUE_OTHER to "سایر",
                        DirectorStageCueEntity.CUE_MOVE to "حرکت",
                        DirectorStageCueEntity.CUE_MUSIC to "موسیقی",
                        DirectorStageCueEntity.CUE_LIGHT to "نور",
                        DirectorStageCueEntity.CUE_PAUSE to "مکث",
                        DirectorStageCueEntity.CUE_IMMEDIATE to "بلافاصله"
                    ).forEach { (key, label) ->
                        FilterChip(selected = editingCueType == key, onClick = { editingCueType = key }, label = { Text(label) })
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    scope.launch {
                        try {
                            require(canEdit && ViewerPermissionEngine.can(policyPermissions, "director.personal.edit")) { "مجوز ویرایش فعال نیست." }
                            ViewerDirectorOfficeRepository.updateCue(context, personalDb, cue.copy(title = editingCueTitle, instruction = editingCueText, cueType = editingCueType, blocksNextTurn = editingCueType == DirectorStageCueEntity.CUE_IMMEDIATE))
                            editingCue = null; refresh++
                        } catch (e: Exception) { message = "ذخیره راهنمای صحنه انجام نشد: ${e.message}" }
                    }
                }) { Text("ذخیره") }
            },
            dismissButton = { TextButton(onClick = { editingCue = null }) { Text("انصراف") } }
        )
    }
    }
}
