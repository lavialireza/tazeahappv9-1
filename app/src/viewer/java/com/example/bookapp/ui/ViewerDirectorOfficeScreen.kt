package com.example.bookapp.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.Lock
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
                if (!showingOfficial && canCreate && store.canBuildWith("MANUAL")) {
                    Button(onClick = { showCreate = true }, modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 10.dp)) {
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
        text = { OutlinedTextField(value = newTitle, onValueChange = { newTitle = it }, label = { Text("عنوان دفتر") }, singleLine = true) },
        confirmButton = { TextButton(onClick = {
            val title = newTitle.trim()
            if (title.isNotBlank()) scope.launch {
                runCatching {
                    val now = System.currentTimeMillis()
                    val plan = DirectorPlanEntity(uid = UUID.randomUUID().toString(), taziehUid = taziehUid.orEmpty(), title = title, status = DirectorPlanEntity.STATUS_DRAFT, createdAt = now, updatedAt = now)
                    val planId = store.createPersonalPlan(plan)
                    store.createPersonalVersion(DirectorPlanVersionEntity(planId = planId, versionNumber = 1, title = "نسخه اول", status = DirectorPlanVersionEntity.STATUS_DRAFT, createdAt = now))
                }.onFailure { message = it.message ?: "ساخت دفتر انجام نشد." }
                showCreate = false
                reloadLists()
            }
        }) { Text("ایجاد") } },
        dismissButton = { TextButton(onClick = { showCreate = false }) { Text("انصراف") } }
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

@Composable
private fun EmptyAccess(text: String) {
    Box(Modifier.fillMaxWidth().padding(24.dp), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Icon(Icons.Filled.Lock, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
            Text(text, style = MaterialTheme.typography.bodyMedium)
        }
    }
}
