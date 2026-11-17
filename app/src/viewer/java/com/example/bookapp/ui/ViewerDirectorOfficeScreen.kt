package com.example.bookapp.ui

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
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.FileOpen
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.bookapp.data.*
import kotlinx.coroutines.launch
import java.util.UUID

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ViewerDirectorOfficeScreen(
    taziehUid: String? = null,
    taziehTitle: String = "",
    onBack: () -> Unit
) {
    val context = LocalContext.current
    val store = remember { ViewerDirectorOfficeStore(context) }
    val scope = rememberCoroutineScope()
    val canOfficial = ViewerPermissionEngine.can(context, "director.official.view")
    val canCreate = ViewerPermissionEngine.can(context, "director.personal.create")
    val canEdit = ViewerPermissionEngine.can(context, "director.personal.edit")
    var tab by remember { mutableIntStateOf(0) }
    var officialPlans by remember { mutableStateOf(emptyList<DirectorPlanEntity>()) }
    var personalPlans by remember { mutableStateOf(emptyList<DirectorPlanEntity>()) }
    var selectedPlan by remember { mutableStateOf<DirectorPlanEntity?>(null) }
    var selectedVersion by remember { mutableStateOf<DirectorPlanVersionEntity?>(null) }
    var versions by remember { mutableStateOf(emptyList<DirectorPlanVersionEntity>()) }
    var turns by remember { mutableStateOf(emptyList<DirectorTurnEntity>()) }
    var turnTexts by remember { mutableStateOf<Map<Long, String>>(emptyMap()) }
    var cues by remember { mutableStateOf(emptyList<DirectorStageCueEntity>()) }
    var loading by remember { mutableStateOf(false) }
    var message by remember { mutableStateOf<String?>(null) }
    var showCreate by remember { mutableStateOf(false) }
    var selectedBuildMode by remember { mutableStateOf("MANUAL") }
    var selectorCandidates by remember { mutableStateOf(emptyList<Triple<String, String, String>>()) }
    var selectorSelected by remember { mutableStateOf<Set<Int>>(emptySet()) }
    var selectorVersionId by remember { mutableStateOf<Long?>(null) }
    var showSelectorPicker by remember { mutableStateOf(false) }
    var newTitle by remember { mutableStateOf(taziehTitle.takeIf { it.isNotBlank() }?.let { "دفتر شخصی $it" } ?: "دفتر شخصی من") }
    var showAddTurn by remember { mutableStateOf(false) }
    var newTurnNumber by remember { mutableStateOf("1") }
    var newTurnTitle by remember { mutableStateOf("") }
    var newTurnText by remember { mutableStateOf("") }

    fun reloadLists() {
        scope.launch {
            loading = true
            runCatching {
                officialPlans = if (canOfficial) store.officialPlans() else emptyList()
                personalPlans = if (canCreate || canEdit) store.personalPlans(taziehUid) else emptyList()
            }.onFailure { message = it.message ?: "خطا در خواندن دفترها" }
            loading = false
        }
    }
    val officialImportLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) scope.launch {
            loading = true
            runCatching { ViewerDirectorOfficePublicationImporter.import(context, uri).getOrThrow() }
                .onSuccess { message = it; selectedPlan = null; selectedVersion = null; reloadLists() }
                .onFailure { message = it.message ?: "دریافت بسته رسمی انجام نشد." }
            loading = false
        }
    }

    LaunchedEffect(tab, taziehUid) { reloadLists() }

    fun openPlan(plan: DirectorPlanEntity, official: Boolean) {
        selectedPlan = plan
        selectedVersion = null
        turns = emptyList()
        turnTexts = emptyMap()
        cues = emptyList()
        scope.launch {
            loading = true
            runCatching {
                versions = if (official) store.officialVersions(plan.uid) else store.personalVersions(plan.id)
            }.onFailure { message = it.message ?: "خطا در خواندن نسخه‌ها" }
            loading = false
        }
    }

    fun openVersion(version: DirectorPlanVersionEntity, official: Boolean) {
        selectedVersion = version
        scope.launch {
            loading = true
            runCatching {
                val loadedTurns = if (official) store.officialTurns(version.id) else store.personalTurns(version.id)
                turns = loadedTurns
                turnTexts = loadedTurns.associate { turn ->
                    turn.id to (if (official) store.officialTextSegments(turn.id) else store.personalTextSegments(turn.id))
                        .joinToString("\n\n") { it.text }
                }
                cues = if (official) store.officialStageCues(version.id) else store.personalStageCues(version.id)
            }.onFailure { message = it.message ?: "خطا در خواندن نوبت‌ها" }
            loading = false
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("دفتر معین‌البکا", fontWeight = FontWeight.Bold) },
                navigationIcon = { IconButton(onClick = { if (selectedVersion != null) selectedVersion = null else if (selectedPlan != null) selectedPlan = null else onBack() }) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "بازگشت") } },
                actions = { IconButton(onClick = { reloadLists() }) { Icon(Icons.Filled.Description, contentDescription = "بازخوانی") } }
            )
        }
    ) { padding ->
        Column(Modifier.fillMaxSize().padding(padding)) {
            if (selectedPlan == null && (canOfficial || canCreate || canEdit)) {
                TabRow(selectedTabIndex = tab.coerceIn(0, if (canOfficial && (canCreate || canEdit)) 1 else 0)) {
                    if (canOfficial) Tab(selected = tab == 0, onClick = { tab = 0 }, text = { Text("دفتر رسمی") })
                    if (canCreate || canEdit) Tab(selected = tab == if (canOfficial) 1 else 0, onClick = { tab = if (canOfficial) 1 else 0 }, text = { Text("دفتر شخصی من") })
                }
            }
            if (!canOfficial && !canCreate && !canEdit) {
                EmptyAccess("مدیر دسترسی دفتر معین‌البکا را برای این کاربر فعال نکرده است.")
            } else if (loading) {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
            } else if (selectedPlan == null) {
                val showingOfficial = canOfficial && tab == 0
                val list = if (showingOfficial) officialPlans else personalPlans
                if (showingOfficial) {
                    OutlinedButton(
                        onClick = { officialImportLauncher.launch(arrayOf("*/*")) },
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp)
                    ) {
                        Icon(Icons.Filled.FileOpen, contentDescription = null)
                        Spacer(Modifier.width(8.dp))
                        Text("دریافت / بروزرسانی دفتر رسمی از فایل مدیر")
                    }
                }
                if (!showingOfficial && canCreate && listOf("ROLE_NUMBERED", "FULL_TEXT", "MANUAL", "SELECTOR").any { store.canBuildWith(it) }) {
                    Button(onClick = { selectedBuildMode = listOf("ROLE_NUMBERED", "FULL_TEXT", "MANUAL", "SELECTOR").firstOrNull { store.canBuildWith(it) } ?: "MANUAL"; showCreate = true }, modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 10.dp)) {
                        Icon(Icons.Filled.Add, contentDescription = null)
                        Spacer(Modifier.width(8.dp)); Text("ساخت دفتر شخصی")
                    }
                }
                if (list.isEmpty()) {
                    EmptyAccess(if (showingOfficial) "هنوز نسخه رسمی معین‌البکا به این دستگاه منتقل نشده است." else "هنوز دفتر شخصی برای این مجلس ساخته نشده است.")
                } else {
                    LazyColumn(contentPadding = PaddingValues(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        items(list, key = { it.id }) { plan ->
                            Card(Modifier.fillMaxWidth().clickable { openPlan(plan, showingOfficial) }) {
                                Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                    Text(plan.title, fontWeight = FontWeight.Bold)
                                    if (plan.description.isNotBlank()) Text(plan.description, style = MaterialTheme.typography.bodySmall)
                                    Text(if (showingOfficial) "نسخه رسمی • فقط خواندنی" else "دفتر شخصی • مستقل از نسخه رسمی", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary)
                                }
                            }
                        }
                    }
                }
            } else if (selectedVersion == null) {
                Column(Modifier.fillMaxSize().padding(12.dp)) {
                    Text(selectedPlan?.title.orEmpty(), style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                    Text(if (canOfficial && tab == 0) "نسخه‌های منتشرشده" else "نسخه‌های دفتر شخصی")
                    if (versions.isEmpty()) EmptyAccess("برای این دفتر نسخه‌ای ثبت نشده است.")
                    else LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        items(versions, key = { it.id }) { version ->
                            Card(Modifier.fillMaxWidth().clickable { openVersion(version, canOfficial && tab == 0) }) {
                                Column(Modifier.padding(12.dp)) {
                                    Text(version.title.ifBlank { "نسخه ${version.versionNumber}" }, fontWeight = FontWeight.Bold)
                                    Text("نسخه ${version.versionNumber} • ${if (version.status == DirectorPlanVersionEntity.STATUS_PUBLISHED) "منتشرشده" else "پیش‌نویس"}", style = MaterialTheme.typography.bodySmall)
                                }
                            }
                        }
                    }
                }
            } else {
                val officialMode = canOfficial && tab == 0
                Column(Modifier.fillMaxSize()) {
                    Row(Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text(selectedPlan?.title.orEmpty(), fontWeight = FontWeight.Bold)
                            Text("${turns.size} نوبت • ${if (officialMode) "فقط خواندنی" else "دفتر شخصی"}", style = MaterialTheme.typography.labelSmall)
                        }
                        if (!officialMode && (canEdit || canCreate)) TextButton(onClick = { showAddTurn = true }) { Icon(Icons.Filled.Add, null); Text("افزودن نوبت") }
                    }
                    cues.filter { it.turnId == null }.forEach { cue ->
                        Card(Modifier.fillMaxWidth().padding(horizontal = 12.dp), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer)) {
                            Column(Modifier.padding(10.dp)) {
                                Text(cue.title.ifBlank { "راهنمای کلی صحنه" }, fontWeight = FontWeight.Bold)
                                Text(cue.instruction, style = MaterialTheme.typography.bodySmall)
                            }
                        }
                    }
                    if (turns.isEmpty()) EmptyAccess("در این نسخه هنوز نوبتی ثبت نشده است.")
                    else LazyColumn(contentPadding = PaddingValues(12.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        items(turns, key = { it.id }) { turn ->
                            Card(Modifier.fillMaxWidth()) {
                                Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(7.dp)) {
                                    Text("نوبت ${turn.turnNumber}  •  جایگاه اجرا ${turn.sequenceIndex + 1}", fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
                                    if (turn.title.isNotBlank()) Text(turn.title, fontWeight = FontWeight.SemiBold)
                                    Text(turnTexts[turn.id].orEmpty().ifBlank { "متنی برای این نوبت ثبت نشده است." }, style = MaterialTheme.typography.bodyMedium)
                                    cues.filter { it.turnId == turn.id }.forEach { cue ->
                                        Surface(color = MaterialTheme.colorScheme.secondaryContainer, shape = MaterialTheme.shapes.small) {
                                            Column(Modifier.fillMaxWidth().padding(9.dp)) {
                                                Text(cue.title.ifBlank { "راهنمای صحنه" }, style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold)
                                                Text(cue.instruction, style = MaterialTheme.typography.bodySmall)
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
            message?.let { Text(it, modifier = Modifier.padding(12.dp), color = MaterialTheme.colorScheme.error) }
        }
    }

    if (showCreate) AlertDialog(
        onDismissRequest = { showCreate = false },
        title = { Text("ساخت دفتر شخصی") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(value = newTitle, onValueChange = { newTitle = it }, label = { Text("عنوان دفتر") }, singleLine = true)
                Text("روش ساخت دفتر شخصی", fontWeight = FontWeight.Bold)
                listOf(
                    "ROLE_NUMBERED" to "شماره‌محور: دیالوگ‌های شماره‌دار هر نقش در نوبت‌های هم‌شماره جمع می‌شوند.",
                    "FULL_TEXT" to "متن کامل: متن هر نقش به‌صورت یک نوبت مستقل وارد می‌شود.",
                    "MANUAL" to "دستی: دفتر خالی ساخته می‌شود و نوبت‌ها را خودتان می‌افزایید.",
                    "SELECTOR" to "انتخاب‌گر: دیالوگ‌ها را از متن نقش‌ها انتخاب می‌کنید."
                ).filter { store.canBuildWith(it.first) }.forEach { (mode, label) ->
                    Row(Modifier.fillMaxWidth().clickable { selectedBuildMode = mode }, verticalAlignment = Alignment.CenterVertically) {
                        RadioButton(selected = selectedBuildMode == mode, onClick = { selectedBuildMode = mode })
                        Text(label, modifier = Modifier.weight(1f), style = MaterialTheme.typography.bodySmall)
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = {
            val title = newTitle.trim()
            if (title.isNotBlank() && store.canBuildWith(selectedBuildMode)) scope.launch {
                runCatching {
                    val sourceRoles: List<RoleEntity>
                    val sourceSections: List<SectionEntity>
                    if (selectedBuildMode == "MANUAL") {
                        sourceRoles = emptyList()
                        sourceSections = emptyList()
                    } else {
                        val db = AppDatabase.getInstance(context)
                        val tazieh = db.taziehDao().getByUid(taziehUid.orEmpty()) ?: error("مجلس مرتبط پیدا نشد؛ ابتدا مجلس را از کتابخانه باز کنید.")
                        sourceRoles = db.roleDao().getByTazieh(tazieh.id)
                        sourceSections = db.sectionDao().getByRoles(sourceRoles.map { it.id }).filter { it.content.isNotBlank() }
                            .sortedWith(compareBy<SectionEntity> { section -> sourceRoles.indexOfFirst { it.id == section.roleId } }.thenBy { it.orderIndex }.thenBy { it.id })
                        require(sourceSections.isNotEmpty()) { "برای این مجلس متن نقش قابل استفاده‌ای وجود ندارد." }
                    }
                    val now = System.currentTimeMillis()
                    val plan = DirectorPlanEntity(uid = UUID.randomUUID().toString(), taziehUid = taziehUid.orEmpty(), title = title, status = DirectorPlanEntity.STATUS_DRAFT, createdAt = now, updatedAt = now)
                    val planId = store.createPersonalPlan(plan)
                    val versionId = store.createPersonalVersion(DirectorPlanVersionEntity(planId = planId, versionNumber = 1, title = "نسخه اول", status = DirectorPlanVersionEntity.STATUS_DRAFT, createdAt = now, changeNotes = "روش تکمیل دفتر: $selectedBuildMode"))
                    if (selectedBuildMode != "MANUAL") {
                        val roles = sourceRoles
                        val sections = sourceSections
                        when (selectedBuildMode) {
                            "ROLE_NUMBERED" -> {
                                val parsed = sections.flatMap { section ->
                                    val role = roles.firstOrNull { it.id == section.roleId } ?: return@flatMap emptyList()
                                    parseViewerNumberedDialogues(section.content).map { Triple(it.first, role, section.uid to it.second) }
                                }
                                require(parsed.isNotEmpty()) { "شماره دیالوگی در متن نقش‌ها پیدا نشد؛ نمونه: ۱- متن دیالوگ" }
                                parsed.groupBy { it.first }.toSortedMap().entries.forEachIndexed { index, entry ->
                                    val grouped = entry.value
                                    val roleNames = grouped.map { it.second }.distinctBy { it.uid }
                                    store.createPersonalTurnWithText(
                                        DirectorTurnEntity(versionId = versionId, turnNumber = entry.key, sequenceIndex = index,
                                            turnType = if (roleNames.size > 1) DirectorTurnEntity.TYPE_COLLECTIVE else DirectorTurnEntity.TYPE_NORMAL,
                                            title = roleNames.joinToString("، ") { it.title }),
                                        grouped.joinToString("\n\n") { it.third.second },
                                        roleNames.mapIndexed { participantIndex, role -> DirectorParticipantEntity(turnId = 0, roleUid = role.uid, roleTitle = role.title, orderIndex = participantIndex, isLead = participantIndex == 0) }
                                    )
                                }
                            }
                            "FULL_TEXT" -> {
                                roles.forEachIndexed { index, role ->
                                    val roleSections = sections.filter { it.roleId == role.id }
                                    if (roleSections.isNotEmpty()) store.createPersonalTurnWithText(
                                        DirectorTurnEntity(versionId = versionId, turnNumber = index + 1, sequenceIndex = index, title = role.title),
                                        roleSections.joinToString("\n\n") { it.content },
                                        listOf(DirectorParticipantEntity(turnId = 0, roleUid = role.uid, roleTitle = role.title, isLead = true))
                                    )
                                }
                            }
                            "SELECTOR" -> {
                                val selected = sections.flatMap { section ->
                                    val role = roles.firstOrNull { it.id == section.roleId } ?: return@flatMap emptyList()
                                    splitViewerDialogues(section.content).map { Triple(role.uid, "${role.title} • ${section.title}", it) }
                                }
                                require(selected.isNotEmpty()) { "متن قابل انتخابی در نقش‌های این مجلس وجود ندارد." }
                                selectorCandidates = selected
                                selectorSelected = emptySet()
                                selectorVersionId = versionId
                                showSelectorPicker = true
                            }
                        }
                    }
                }.onSuccess { message = "دفتر شخصی با روش انتخابی ساخته شد." }
                    .onFailure { message = it.message ?: "ساخت دفتر انجام نشد." }
                showCreate = false
                reloadLists()
            }
        }) { Text("ایجاد") } },
        dismissButton = { TextButton(onClick = { showCreate = false }) { Text("انصراف") } }
    )

    if (showSelectorPicker) AlertDialog(
        onDismissRequest = { showSelectorPicker = false },
        title = { Text("انتخاب دیالوگ‌های دفتر شخصی") },
        text = {
            Column(Modifier.fillMaxWidth().heightIn(max = 460.dp)) {
                Text("فقط مواردی که تیک می‌زنید به‌عنوان نوبت وارد دفتر شخصی می‌شوند.", style = MaterialTheme.typography.bodySmall)
                LazyColumn {
                    items(selectorCandidates.size) { index ->
                        Row(Modifier.fillMaxWidth().clickable {
                            selectorSelected = if (index in selectorSelected) selectorSelected - index else selectorSelected + index
                        }, verticalAlignment = Alignment.CenterVertically) {
                            Checkbox(checked = index in selectorSelected, onCheckedChange = { checked ->
                                selectorSelected = if (checked) selectorSelected + index else selectorSelected - index
                            })
                            Column(Modifier.weight(1f)) {
                                Text(selectorCandidates[index].second, fontWeight = FontWeight.SemiBold)
                                Text(selectorCandidates[index].third, maxLines = 3, style = MaterialTheme.typography.bodySmall)
                            }
                        }
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = {
            val versionId = selectorVersionId
            if (versionId != null && selectorSelected.isNotEmpty()) scope.launch {
                runCatching {
                    selectorSelected.sorted().forEachIndexed { index, candidateIndex ->
                        val candidate = selectorCandidates[candidateIndex]
                        store.createPersonalTurnWithText(
                            DirectorTurnEntity(versionId = versionId, turnNumber = index + 1, sequenceIndex = index, title = candidate.second.substringBefore(" • ")),
                            candidate.third,
                            listOf(DirectorParticipantEntity(turnId = 0, roleUid = candidate.first, roleTitle = candidate.second.substringBefore(" • "), isLead = true))
                        )
                    }
                }.onFailure { message = it.message ?: "ثبت انتخاب‌ها انجام نشد." }
                    .onSuccess { message = "${selectorSelected.size} دیالوگ انتخاب‌شده به دفتر شخصی افزوده شد." }
                showSelectorPicker = false
                reloadLists()
            }
        }, enabled = selectorSelected.isNotEmpty()) { Text("افزودن موارد انتخاب‌شده") } },
        dismissButton = { TextButton(onClick = { showSelectorPicker = false }) { Text("انصراف") } }
    )

    if (showAddTurn) AlertDialog(
        onDismissRequest = { showAddTurn = false },
        title = { Text("افزودن نوبت شخصی") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(value = newTurnNumber, onValueChange = { newTurnNumber = it.filter(Char::isDigit) }, label = { Text("شماره نوبت") }, singleLine = true)
                OutlinedTextField(value = newTurnTitle, onValueChange = { newTurnTitle = it }, label = { Text("عنوان / نقش") }, singleLine = true)
                OutlinedTextField(value = newTurnText, onValueChange = { newTurnText = it }, label = { Text("متن دیالوگ") }, minLines = 4)
            }
        },
        confirmButton = { TextButton(onClick = {
            val version = selectedVersion
            val number = newTurnNumber.toIntOrNull()
            if (version != null && number != null) scope.launch {
                runCatching {
                    store.createPersonalTurnWithText(DirectorTurnEntity(versionId = version.id, turnNumber = number, sequenceIndex = turns.size, title = newTurnTitle.trim()), newTurnText)
                    val loaded = store.personalTurns(version.id)
                    turns = loaded
                    turnTexts = loaded.associate { it.id to store.personalTextSegments(it.id).joinToString("\n\n") { seg -> seg.text } }
                }.onFailure { message = it.message ?: "ذخیره نوبت انجام نشد." }
                newTurnNumber = (number + 1).toString(); newTurnTitle = ""; newTurnText = ""; showAddTurn = false
            }
        }) { Text("ذخیره نوبت") } },
        dismissButton = { TextButton(onClick = { showAddTurn = false }) { Text("انصراف") } }
    )
}


private fun splitViewerDialogues(content: String): List<String> = content.replace("\r\n", "\n").replace('\r', '\n')
    .split(Regex("\n[\t ]*\n+" )).map { it.trim() }.filter { it.isNotBlank() }

private fun parseViewerNumberedDialogues(content: String): List<Pair<Int, String>> {
    val heading = Regex("^\\s*(?:(?:دیالوگ|نوبت|شماره)\\s*)?([0-9۰-۹٠-٩]{1,6})(?:\\s*[-–—ـ.)،:：]\\s*|\\s+|$)(.*)$")
    val result = mutableListOf<Pair<Int, String>>()
    var number: Int? = null
    val body = StringBuilder()
    fun flush() { val n = number; val text = body.toString().trim(); if (n != null && text.isNotBlank()) result += n to text; body.setLength(0) }
    content.replace("\r\n", "\n").replace('\r', '\n').lines().forEach { line ->
        val match = heading.matchEntire(line)
        if (match != null) {
            flush()
            val digits = match.groupValues[1].map { ch -> when (ch) { in '۰'..'۹' -> ch - '۰' + '0'.code; in '٠'..'٩' -> ch - '٠' + '0'.code; else -> ch.code }.toChar() }.joinToString("")
            number = digits.toIntOrNull()
            if (match.groupValues[2].isNotBlank()) body.append(match.groupValues[2].trim()).append('\n')
        } else if (number != null) body.append(line).append('\n')
    }
    flush()
    return result
}

@Composable
private fun EmptyAccess(text: String) {
    Box(Modifier.fillMaxWidth().padding(24.dp), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Icon(Icons.Filled.Lock, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
            Text(text, style = MaterialTheme.typography.bodyMedium)
        }
    }
}
