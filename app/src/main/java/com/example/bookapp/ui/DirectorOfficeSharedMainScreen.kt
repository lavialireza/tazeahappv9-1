package com.example.bookapp.ui

import android.content.Context
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.FileOpen
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.bookapp.data.*
import com.example.bookapp.ui.theme.FontChoiceLabels
import com.example.bookapp.ui.theme.colorSchemeFor
import com.example.bookapp.ui.theme.typographyFor
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.util.UUID
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import com.example.bookapp.data.DirectorOfficeWorkspaceState
import com.example.bookapp.data.DirectorParticipantEntity
import com.example.bookapp.data.DirectorStageCueEntity
import com.example.bookapp.data.DirectorTextSegmentEntity
import com.example.bookapp.data.DirectorTurnEntity
import com.example.bookapp.data.DirectorOfficeWorkspaceSearch



/** قرارداد تزریق ورود فایل؛ پیاده‌سازی فرمت‌ها در هر Flavor باقی می‌ماند. */
fun interface DirectorOfficeSourceImporter {
    suspend fun import(context: Context, uri: Uri, planId: Long): DirectorSourceFileEntity
}

internal data class DirectorSelectorItem(
    val key: String,
    val section: SectionEntity,
    val role: RoleEntity,
    val text: String
)


internal data class DirectorLibraryImportResult(
    val roleCount: Int,
    val sectionCount: Int,
    val turnCount: Int,
    val usedDialogueOrder: Boolean
)
/**
 * Copies the selected majlis' already-stored library text into an empty director version.
 * It never edits roles/sections or imports content from another majlis.
 */
private suspend fun loadCurrentTaziehRoles(
    db: AppDatabase,
    tazieh: TaziehEntity
): List<RoleEntity> {
    val current = db.archiveDao().currentVersion(tazieh.uid)
        ?: db.archiveDao().versionsForTazieh(tazieh.uid).firstOrNull()
    return if (current != null) db.roleDao().getByVersionUid(current.uid)
    else db.roleDao().getByTazieh(tazieh.id).filter { it.versionUid.isBlank() }
        .sortedWith(compareBy<RoleEntity> { it.orderIndex }.thenBy { it.id })
}

internal suspend fun directorVersionHasText(dao: DirectorOfficeMainPageDao, versionId: Long): Boolean {
    return dao.turns(versionId).any { turn ->
        dao.textSegments(turn.id).any { it.text.isNotBlank() }
    }
}

internal suspend fun importExistingTaziehText(
    db: AppDatabase,
    dao: DirectorOfficeMainPageDao,
    transactionRunner: DirectorOfficeTransactionRunner,
    versionId: Long,
    taziehId: Long
): DirectorLibraryImportResult {
    val tazieh = db.taziehDao().getById(taziehId)
        ?: return DirectorLibraryImportResult(0, 0, 0, false)
    val archiveVersion = db.archiveDao().currentVersion(tazieh.uid)
        ?: db.archiveDao().versionsForTazieh(tazieh.uid).firstOrNull()
    val roles = (archiveVersion?.let { db.roleDao().getByVersionUid(it.uid) }
        ?: db.roleDao().getByTazieh(taziehId).filter { it.versionUid.isBlank() })
        .sortedWith(compareBy<RoleEntity> { it.orderIndex }.thenBy { it.id })
    if (roles.isEmpty()) return DirectorLibraryImportResult(0, 0, 0, false)
    val roleById = roles.associateBy { it.id }
    val sections = db.sectionDao().getByRoles(roles.map { it.id })
        .filter { it.content.isNotBlank() }
        .sortedWith(compareBy<SectionEntity> { roleById[it.roleId]?.orderIndex ?: Int.MAX_VALUE }.thenBy { it.orderIndex }.thenBy { it.id })
    if (sections.isEmpty()) return DirectorLibraryImportResult(roles.size, 0, 0, false)

    data class Entry(
        val section: SectionEntity,
        val role: RoleEntity,
        val turnNumber: Int,
        val order: Int,
        val verified: Boolean
    )

    val sectionById = sections.associateBy { it.id }
    val dialogues = archiveVersion?.let { db.dialogueDao().getByVersionUid(it.uid) }
        ?: db.dialogueDao().getByTazieh(taziehId).filter { it.versionUid.isBlank() }
    var bestEntries = emptyList<Entry>()
    var bestCoverage = -1
    for (dialogue in dialogues) {
        val dialogueTurns = db.dialogueTurnDao().getByDialogue(dialogue.id)
        val candidate = mutableListOf<Entry>()
        for (sourceTurn in dialogueTurns) {
            val section = sectionById[sourceTurn.sectionId] ?: continue
            val role = roleById[section.roleId] ?: continue
            val metadata = db.dialogueTurnMetadataDao().getByTurn(sourceTurn.id)
            val verified = metadata != null && metadata.sourceSequenceIndex != null &&
                (metadata.sourceSequenceStatus == DialogueTurnMetadataEntity.SOURCE_DOCUMENTED ||
                 metadata.sourceSequenceStatus == DialogueTurnMetadataEntity.SOURCE_DIRECTOR_CONFIRMED)
            val number = if (verified) (metadata!!.sourceSequenceIndex!! + 1) else (sourceTurn.orderIndex + 1)
            candidate += Entry(section, role, number, sourceTurn.orderIndex, verified)
        }
        val coverage = candidate.map { it.section.id }.distinct().size
        if (coverage > bestCoverage) {
            bestCoverage = coverage
            bestEntries = candidate
        }
    }

    val usedDialogue = bestEntries.isNotEmpty()
    val entries = if (usedDialogue) {
        val represented = bestEntries.map { it.section.id }.toSet()
        val nextNumber = (bestEntries.maxOfOrNull { it.turnNumber } ?: 0) + 1
        val missing = sections.filter { it.id !in represented }.mapIndexed { index, section ->
            Entry(section, roleById.getValue(section.roleId), nextNumber + index, Int.MAX_VALUE - sections.size + index, false)
        }
        bestEntries + missing
    } else {
        sections.mapIndexed { index, section ->
            Entry(section, roleById.getValue(section.roleId), index + 1, index, false)
        }
    }

    transactionRunner.run {
        // Preserve any version that already contains text. Empty placeholder turns from earlier
        // failed imports are removed so they cannot hide the recovered library content.
        val existingTurns = dao.turns(versionId)
        val alreadyHasText = existingTurns.any { existing ->
            dao.textSegments(existing.id).any { it.text.isNotBlank() }
        }
        if (alreadyHasText) return@run
        existingTurns.forEach { existing ->
            dao.participants(existing.id).forEach { dao.deleteParticipant(it.id) }
            dao.textSegments(existing.id).forEach { dao.deleteTextSegment(it.id) }
            dao.deleteTurn(existing.id)
        }
        var sequence = 0
        val grouped = entries.groupBy { it.turnNumber }.toSortedMap()
        for ((number, group) in grouped) {
            val distinctRoles = group.map { it.role }.distinctBy { it.uid }
            val turnId = dao.insertTurn(
                DirectorTurnEntity(
                    versionId = versionId,
                    turnNumber = number,
                    sequenceIndex = sequence++,
                    turnType = if (distinctRoles.size > 1) DirectorTurnEntity.TYPE_COLLECTIVE else DirectorTurnEntity.TYPE_NORMAL,
                    title = if (distinctRoles.size > 1) distinctRoles.joinToString("، ") { it.title } else distinctRoles.first().title,
                    notes = if (group.any { !it.verified }) "ترتیب این نوبت از ترتیب داخلی بخش‌ها/گفت‌وگو گرفته شده و ترتیب تاریخی آن تأیید نشده است." else ""
                )
            )
            distinctRoles.forEachIndexed { index, role ->
                dao.insertParticipant(
                    DirectorParticipantEntity(
                        turnId = turnId,
                        roleUid = role.uid,
                        roleTitle = role.title,
                        orderIndex = index,
                        isLead = index == 0
                    )
                )
            }
            group.forEachIndexed { index, entry ->
                dao.insertTextSegment(
                    DirectorTextSegmentEntity(
                        turnId = turnId,
                        segmentIndex = index,
                        text = entry.section.content,
                        sectionUid = entry.section.uid
                    )
                )
            }
        }
    }
    return DirectorLibraryImportResult(roles.size, sections.size, entries.groupBy { it.turnNumber }.size, usedDialogue)
}


internal suspend fun buildDirectorVersionPreview(
    dao: DirectorOfficeMainPageDao,
    versionId: Long
): String {
    // DAO methods are suspend functions; fetch each relation in a normal suspend loop
    // instead of calling them from joinToString's non-suspending transform lambda.
    val turns = dao.turns(versionId)
    val previewBlocks = mutableListOf<String>()

    for (turn in turns) {
        val participants = dao.participants(turn.id)
        val roleTitles = participants.joinToString("، ") { it.roleTitle }

        val segments = dao.textSegments(turn.id)
        val turnText = segments.joinToString("\n") { it.text }

        previewBlocks += buildString {
            append("نوبت ")
            append(turn.turnNumber)
            append(" — ")
            append(roleTitles.ifBlank { turn.title })
            append("\n")
            append(turnText)
        }
    }

    return previewBlocks.joinToString("\n\n")
}

/**
 * Matches saved section-backed segments to source dialogue items one-to-one.
 * Repeated identical paragraphs in one section must remain independently selectable.
 */
internal suspend fun usedDirectorDialogueKeys(
    dao: DirectorOfficeMainPageDao,
    versionId: Long,
    items: List<DirectorSelectorItem>
): Set<String> {
    val used = mutableSetOf<String>()
    for (turn in dao.turns(versionId)) {
        for (segment in dao.textSegments(turn.id)) {
            val sectionUid = segment.sectionUid ?: continue
            val match = items.firstOrNull { item ->
                item.key !in used && item.section.uid == sectionUid && item.text == segment.text
            }
            if (match != null) used += match.key
        }
    }
    return used
}


@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DirectorOfficeSharedMainScreen(
    libraryDb: AppDatabase,
    officeDao: DirectorOfficeMainPageDao,
    transactionRunner: DirectorOfficeTransactionRunner,
    isAdmin: Boolean,
    policyPermissions: Map<String, Boolean> = emptyMap(),
    sourceImporter: DirectorOfficeSourceImporter? = null,
    initialTaziehId: Long? = null,
    darkMode: Boolean,
    onDarkModeChange: (Boolean) -> Unit,
    autoDarkMode: Boolean,
    onAutoDarkModeChange: (Boolean) -> Unit,
    fontScale: Float,
    onFontScaleChange: (Float) -> Unit,
    themeChoice: String,
    onThemeChoiceChange: (String) -> Unit,
    fontChoice: String,
    onFontChoiceChange: (String) -> Unit,
    keepScreenOn: Boolean,
    onKeepScreenOnChange: (Boolean) -> Unit,
    onBack: () -> Unit
) {
    val context = LocalContext.current
    val dao = remember(officeDao) { officeDao }
    val canAccess: (String) -> Boolean = { key -> isAdmin || ViewerPermissionEngine.can(policyPermissions, key) }
    val canCreatePersonal = canAccess("director.personal.create")
    val canEditPersonal = canAccess("director.personal.edit")
    val canImportSources = canAccess("director.source.import") && sourceImporter != null
    fun canBuildMethod(method: String): Boolean = isAdmin || when (method) {
        DirectorOfficeSharedContract.ROLE_NUMBERED -> canAccess("director.personal.method.number")
        DirectorOfficeSharedContract.FULL_TEXT -> canAccess("director.personal.method.fulltext")
        DirectorOfficeSharedContract.MANUAL -> canAccess("director.personal.method.manual")
        DirectorOfficeSharedContract.SELECTOR -> canAccess("director.personal.method.selector")
        else -> false
    }
    val workspaceRepository = remember(dao, transactionRunner) {
        DaoDirectorOfficeWorkspaceRepository(dao, transactionRunner)
    }
    var workspaceState by remember { mutableStateOf<DirectorOfficeWorkspaceState?>(null) }
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
    var showBuildModeDialog by remember { mutableStateOf(false) }
    var selectedBuildMode by remember { mutableStateOf(DirectorOfficeSharedContract.ROLE_NUMBERED) }
    val canBuildSelectedMethod = canBuildMethod(selectedBuildMode)
    var selectorItems by remember { mutableStateOf(emptyList<DirectorSelectorItem>()) }
    var selectorRole by remember { mutableStateOf<RoleEntity?>(null) }
    var selectorRoleMenu by remember { mutableStateOf(false) }
    var selectorTurnNumber by remember { mutableStateOf("1") }
    var selectedDialogueKeys by remember { mutableStateOf<Set<String>>(emptySet()) }
    var turnSearch by remember { mutableStateOf("") }
    var selectedSearchResultIndex by remember { mutableIntStateOf(0) }
    var turnSearchCorpus by remember { mutableStateOf<Map<Long, String>>(emptyMap()) }
    var showOfficeSettingsDialog by remember { mutableStateOf(false) }
    var officeLineSpacing by remember { mutableFloatStateOf(Prefs.getLineSpacing(context)) }
    val turnListState = rememberLazyListState()
    val officeTypography = remember(fontChoice, fontScale, officeLineSpacing) {
        val base = typographyFor(fontChoice, fontScale)
        base.copy(
            bodyLarge = base.bodyLarge.copy(lineHeight = base.bodyLarge.fontSize * officeLineSpacing),
            bodyMedium = base.bodyMedium.copy(lineHeight = base.bodyMedium.fontSize * officeLineSpacing),
            bodySmall = base.bodySmall.copy(lineHeight = base.bodySmall.fontSize * officeLineSpacing)
        )
    }
    val visibleTurns = remember(turns, turnSearch, turnSearchCorpus) {
        DirectorOfficeWorkspaceSearch.filterTurns(turns, turnSearchCorpus, turnSearch)
    }
    LaunchedEffect(workspaceState, refresh) {
        turnSearchCorpus = workspaceState?.searchableTextByTurn.orEmpty()
    }
    LaunchedEffect(turnSearch, visibleTurns.size) {
        selectedSearchResultIndex = 0
    }
    LaunchedEffect(turnSearch, selectedSearchResultIndex, visibleTurns.size, busy, message) {
        if (turnSearch.isNotBlank() && visibleTurns.isNotEmpty()) {
            withFrameNanos { }
            val trailingItems = (if (busy) 1 else 0) + (if (message != null) 1 else 0)
            val sharedGlobalCueItem = if (workspaceState?.stageCues?.any { it.turnId == null } == true) 1 else 0
            // The shared workspace contributes a title, a search row, and optionally one global-cue row.
            val firstTurnItemIndex = (
                turnListState.layoutInfo.totalItemsCount - visibleTurns.size - trailingItems - sharedGlobalCueItem
            ).coerceAtLeast(0)
            val safeResultIndex = selectedSearchResultIndex.coerceIn(0, visibleTurns.lastIndex)
            runCatching { turnListState.animateScrollToItem(firstTurnItemIndex + safeResultIndex) }
        }
    }

    suspend fun loadPlans(reset: Boolean) {
        if (initialTaziehId != null) {
            val currentTazieh = libraryDb.taziehDao().getById(initialTaziehId)
            val onlyPlan = currentTazieh?.let { dao.plansForTazieh(it.uid) }.orEmpty()
                .filter { planSearch.isBlank() || it.title.contains(planSearch.trim(), ignoreCase = true) }
            plans = onlyPlan
            hasMorePlans = false
            return
        }
        val offset = if (reset) 0 else plans.size
        val page = dao.plansPage(planSearch.trim(), 50, offset)
        plans = if (reset) page else plans + page
        hasMorePlans = page.size == 50
    }

    /** تنها مسیر بارگذاری snapshot فضای کاری مشترک؛ فهرست منابع و کتابخانه همچنان Admin-only است. */
    suspend fun loadWorkspaceIntoScreen(planId: Long, preferredVersionId: Long? = selectedVersion?.id) {
        val loaded = workspaceRepository.loadWorkspace(planId, preferredVersionId)
        workspaceState = loaded
        selectedPlan = loaded.plan
        planVersions = loaded.versions
        selectedVersion = loaded.selectedVersion
        turns = loaded.turns
        stageCues = loaded.stageCues
        previewText = loaded.selectedVersion?.let { buildDirectorVersionPreview(dao, it.id) }.orEmpty()
    }

    fun reload() {
        scope.launch {
            loadPlans(reset = true)
            selectedPlan?.let { current ->
                val targetTazieh = libraryDb.taziehDao().getByUid(current.taziehUid)
                roleOptions = targetTazieh?.let { loadCurrentTaziehRoles(libraryDb, it) }.orEmpty()
                selectedRole = selectedRole?.let { old -> roleOptions.firstOrNull { it.uid == old.uid } } ?: roleOptions.firstOrNull()
                sources = dao.sourceFiles(current.id)
                loadWorkspaceIntoScreen(current.id, selectedVersion?.id)
                val currentVersion = selectedVersion
                currentVersion?.changeNotes
                    ?.substringAfter("روش تکمیل دفتر: ", "")
                    ?.takeIf { it in DirectorOfficeSharedContract.methodCodes }
                    ?.let { selectedBuildMode = it }
            }
        }
    }
    LaunchedEffect(initialTaziehId) {
        try {
            val allTaziehs = libraryDb.taziehDao().getAll()
            taziehOptions = if (initialTaziehId == null) allTaziehs else allTaziehs.filter { it.id == initialTaziehId }
            fieldOptions = libraryDb.fieldDao().getAll()
        } catch (e: Exception) { message = "دریافت فهرست زمینه‌ها و تعزیه‌ها ناموفق بود: ${e.message}" }
    }
    LaunchedEffect(initialTaziehId, taziehOptions) {
        if (initialTaziehId != null && taziehOptions.isNotEmpty()) {
            val chosen = taziehOptions.firstOrNull { it.id == initialTaziehId }
            if (chosen != null && selectedPlan?.taziehUid != chosen.uid) {
                busy = true
                try {
                    selectedTazieh = chosen
                    planTitle = chosen.title
                    roleOptions = loadCurrentTaziehRoles(libraryDb, chosen)
                    selectedRole = roleOptions.firstOrNull()
                    var plan = dao.plansForTazieh(chosen.uid).firstOrNull()
                    if (plan == null && !canCreatePersonal) {
                        message = "برای ساخت دفتر شخصی این مجلس، مجوز ساخت دفتر در سیاست دسترسی فعال نیست."
                    } else if (plan == null) {
                        val now = System.currentTimeMillis()
                        val newPlanId = dao.insertPlan(
                            DirectorPlanEntity(
                                uid = UUID.randomUUID().toString(),
                                taziehUid = chosen.uid,
                                title = chosen.title,
                                createdAt = now,
                                updatedAt = now
                            )
                        )
                        val createdVersionId = dao.insertVersion(
                            DirectorPlanVersionEntity(
                                planId = newPlanId,
                                versionNumber = 1,
                                title = "نسخه اول",
                                changeNotes = "نسخه پایه دفتر؛ روش تکمیل پس از انتخاب معین‌البکا تعیین می‌شود",
                                createdAt = now
                            )
                        )
                        selectedVersion = dao.version(createdVersionId)
                        showBuildModeDialog = true
                        plan = dao.plan(newPlanId)
                    }
                    if (plan != null) {
                        selectedPlan = plan
                        val versions = dao.versions(plan.id)
                        if (versions.isEmpty()) {
                            if (isAdmin || canCreatePersonal || canEditPersonal) {
                                val now = System.currentTimeMillis()
                                dao.insertVersion(DirectorPlanVersionEntity(planId = plan.id, versionNumber = 1, title = "نسخه اول", createdAt = now))
                            } else {
                                message = "برای ایجاد نسخه دفتر، مجوز ساخت یا ویرایش در سیاست دسترسی فعال نیست."
                            }
                        }
                        planVersions = dao.versions(plan.id)
                        selectedVersion = planVersions.firstOrNull()
                        selectedVersion?.changeNotes?.substringAfter("روش تکمیل دفتر: ", "")?.takeIf { it in DirectorOfficeSharedContract.methodCodes }?.let { selectedBuildMode = it }
                        sources = dao.sourceFiles(plan.id)
                        val currentVersion = selectedVersion
                        if (currentVersion != null && (isAdmin || canCreatePersonal || canEditPersonal) && !showBuildModeDialog && !directorVersionHasText(dao, currentVersion.id)) {
                            val result = importExistingTaziehText(libraryDb, dao, transactionRunner, currentVersion.id, chosen.id)
                            turns = dao.turns(currentVersion.id)
                            message = if (result.sectionCount > 0) {
                                "${result.sectionCount} بخش متن از ${result.roleCount} نقش در ${result.turnCount} نوبت به دفتر منتقل شد. ${if (result.usedDialogueOrder) "ترتیب ثبت‌شده گفت‌وگوی داخلی مبنا قرار گرفت؛ ترتیب تاریخی فقط در موارد مستند تأیید است." else "ترتیب بر اساس ترتیب ثبت نقش‌ها و بخش‌ها تنظیم شد و نیازمند بازبینی است."}"
                            } else {
                                "دفتر این مجلس ایجاد/باز شد، اما در نقش‌های آن متن ذخیره‌شده‌ای برای انتقال پیدا نشد."
                            }
                        } else {
                            turns = currentVersion?.let { dao.turns(it.id) }.orEmpty()
                        }
                        if (currentVersion != null) previewText = buildDirectorVersionPreview(dao, currentVersion.id)
                        stageCues = currentVersion?.let { dao.stageCues(it.id) }.orEmpty()
                        loadWorkspaceIntoScreen(plan.id, currentVersion?.id)
                        loadPlans(reset = true)
                    }
                } catch (e: Exception) {
                    message = "آماده‌سازی دفتر همین مجلس ناموفق بود: ${e.message}"
                } finally {
                    busy = false
                }
            }
        }
    }
    LaunchedEffect(refresh) { reload() }
    LaunchedEffect(planSearch) { loadPlans(reset = true) }
    LaunchedEffect(selectedVersion?.id, selectedBuildMode, roleOptions, selectedPlan?.id) {
        val version = selectedVersion
        val plan = selectedPlan
        if (version != null && plan != null && selectedBuildMode == DirectorOfficeSharedContract.SELECTOR && roleOptions.isNotEmpty()) {
            try {
                val tazieh = libraryDb.taziehDao().getByUid(plan.taziehUid)
                if (tazieh != null) {
                    val roles = loadCurrentTaziehRoles(libraryDb, tazieh)
                    val sections = libraryDb.sectionDao().getByRoles(roles.map { it.id })
                        .filter { it.content.isNotBlank() }
                        .sortedWith(compareBy<SectionEntity> { section -> roles.firstOrNull { it.id == section.roleId }?.orderIndex ?: Int.MAX_VALUE }.thenBy { it.orderIndex }.thenBy { it.id })
                    selectorItems = sections.flatMap { section ->
                        val role = roles.firstOrNull { it.id == section.roleId } ?: return@flatMap emptyList()
                        DirectorOfficeWorkspaceUseCases.splitDialogues(section.content).mapIndexed { index, part -> DirectorSelectorItem("${section.uid}:$index", section, role, part) }
                    }
                    selectedDialogueKeys = usedDirectorDialogueKeys(dao, version.id, selectorItems)
                    selectorRole = selectorRole?.let { old -> roles.firstOrNull { it.uid == old.uid } } ?: roles.firstOrNull()
                    selectorTurnNumber = ((dao.turns(version.id).maxOfOrNull { it.turnNumber } ?: 0) + 1).toString()
                }
            } catch (e: Exception) { message = "بارگذاری متن‌های انتخابی ناموفق بود: ${e.message}" }
        }
    }

    val importLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenMultipleDocuments()) { uris ->
        if (uris.isNotEmpty()) {
            if (!canImportSources) {
                message = "مجوز ورود منبع برای این Viewer فعال نیست."
            } else if (selectedPlan == null || selectedVersion == null) {
                message = "ابتدا دفتر و نسخه آن را انتخاب یا ایجاد کنید؛ سپس فایل را وارد کنید."
            } else scope.launch {
                busy = true
                try {
                    val importedSources = mutableListOf<Pair<Long, DirectorSourceFileEntity>>()
                    var firstPreviewSource: DirectorSourceFileEntity? = null
                    val storedBeforeImport = dao.sourceFiles(selectedPlan!!.id)
                    uris.forEach { uri ->
                        val imported = withContext(Dispatchers.IO) { sourceImporter!!.import(context, uri, selectedPlan!!.id) }
                        val duplicate = storedBeforeImport.firstOrNull { it.sha256 == imported.sha256 }
                        if (duplicate != null) {
                            File(imported.localPath).delete()
                            if (firstPreviewSource == null) firstPreviewSource = duplicate
                        } else {
                            val sourceId = dao.insertSourceFile(imported)
                            val saved = imported.copy(id = sourceId)
                            importedSources += sourceId to saved
                            if (firstPreviewSource == null) firstPreviewSource = saved
                        }
                    }
                    val targetTazieh = libraryDb.taziehDao().getByUid(selectedPlan!!.taziehUid)
                    val targetTaziehTitle = targetTazieh?.title.orEmpty()
                    val targetFieldTitle = targetTazieh?.let { target ->
                        libraryDb.fieldDao().getAll().firstOrNull { it.id == target.fieldId }?.title
                    }.orEmpty()
                    val targetRoles = targetTazieh?.let { loadCurrentTaziehRoles(libraryDb, it) }.orEmpty()
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
                            val linkedRole = resolveDirectorRole(libraryDb, selectedPlan!!.taziehUid, roleName)
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
                    val parsedPreview = parsedEntries.joinToString("\n\n") { entry ->
                        buildString {
                            append("نقش: ").append(entry.roleTitle)
                            entry.turnNumber?.let { append(" | شماره نوبت: ").append(it) }
                            append("\n").append(entry.text)
                        }
                    }
                    previewText = parsedPreview.ifBlank { firstPreviewSource?.extractedText.orEmpty() }
                    if (firstPreviewSource != null && previewText.isBlank()) {
                        message = "فایل ذخیره شد، اما متن قابل استخراجی در آن پیدا نشد. فایل Word/PDF متنی یا TXT را بررسی کنید."
                    } else message = if (parsedEntries.isEmpty()) {
                        "${importedSources.size} فایل جدید ذخیره شد؛ متن ساختاریافته قابل تطبیق پیدا نشد. متن منبع در پیش‌نمایش قرار گرفت تا قالب و نام نقش بررسی شود."
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


    MaterialTheme(
        colorScheme = colorSchemeFor(themeChoice, darkMode),
        typography = officeTypography
    ) {
    DirectorOfficeSharedScreen(
        title = "دفتر معین البکا",
        onBack = onBack,
        actions = {
            IconButton(onClick = { showOfficeSettingsDialog = true }) {
                Icon(Icons.Default.Settings, contentDescription = "تنظیمات دفتر")
            }
            IconButton(onClick = { refresh++ }) {
                Icon(Icons.Default.Refresh, contentDescription = "بروزرسانی")
            }
        },
        listState = turnListState
    ) {
            item {
                Text("ساخت و آماده‌سازی دفتر معین البکا برای هر تعزیه", style = MaterialTheme.typography.bodyMedium)
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
                        OutlinedTextField(planTitle, { planTitle = it }, label = { Text("عنوان دفتر / مجلس") }, modifier = Modifier.fillMaxWidth(), singleLine = true, enabled = isAdmin || canCreatePersonal)
                        Button(enabled = canCreatePersonal && planTitle.isNotBlank() && selectedTazieh != null && !busy, onClick = {
                            scope.launch {
                                busy = true
                                try {
                                    val existingPlan = dao.plansForTazieh(selectedTazieh!!.uid).firstOrNull()
                                    if (existingPlan != null) {
                                        selectedPlan = existingPlan
                                        val target = libraryDb.taziehDao().getByUid(existingPlan.taziehUid)
                                        roleOptions = target?.let { loadCurrentTaziehRoles(libraryDb, it) }.orEmpty()
                                        selectedRole = roleOptions.firstOrNull()
                                        planVersions = dao.versions(existingPlan.id)
                                        selectedVersion = planVersions.firstOrNull()
                                        selectedVersion?.changeNotes?.substringAfter("روش تکمیل دفتر: ", "")?.takeIf { it in DirectorOfficeSharedContract.methodCodes }?.let { selectedBuildMode = it }
                                        sources = dao.sourceFiles(existingPlan.id)
                                        turns = selectedVersion?.let { dao.turns(it.id) } ?: emptyList()
                                        stageCues = selectedVersion?.let { dao.stageCues(it.id) }.orEmpty()
                                        val currentVersion = selectedVersion
                                        if (currentVersion != null && !directorVersionHasText(dao, currentVersion.id)) {
                                            val result = importExistingTaziehText(libraryDb, dao, transactionRunner, currentVersion.id, target?.id ?: selectedTazieh!!.id)
                                            turns = dao.turns(currentVersion.id)
                                            previewText = buildDirectorVersionPreview(dao, currentVersion.id)
                                            message = if (result.sectionCount > 0) "${result.sectionCount} بخش از متن‌های داخلی همین مجلس در ${result.turnCount} نوبت به دفتر منتقل شد." else "دفتر باز شد؛ متن ذخیره‌شده‌ای برای انتقال پیدا نشد."
                                        } else {
                                            message = "برای این مجلس دفتر اصلی از قبل وجود دارد؛ همان دفتر باز شد. نسخه‌های آن را مدیریت کنید."
                                        }
                                    } else {
                                        val now = System.currentTimeMillis()
                                        val id = dao.insertPlan(DirectorPlanEntity(uid = UUID.randomUUID().toString(), taziehUid = selectedTazieh!!.uid, title = planTitle.trim(), createdAt = now, updatedAt = now))
                                        val versionId = dao.insertVersion(DirectorPlanVersionEntity(planId = id, versionNumber = 1, title = "نسخه اول", createdAt = now))
                                        selectedPlan = dao.plan(id)
                                        val target = libraryDb.taziehDao().getByUid(selectedTazieh!!.uid)
                                        roleOptions = target?.let { loadCurrentTaziehRoles(libraryDb, it) }.orEmpty()
                                        selectedRole = roleOptions.firstOrNull()
                                        selectedVersion = dao.version(versionId)
                                        turns = emptyList()
                                        previewText = ""
                                        selectorRole = roleOptions.firstOrNull()
                                        planTitle = ""
                                        selectedTazieh = null
                                        showBuildModeDialog = true
                                        message = "دفتر ایجاد شد؛ روش تکمیل دفتر را انتخاب کنید."
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
                val tazieh = taziehOptions.firstOrNull { it.uid == plan.taziehUid }
                val fieldTitle = tazieh?.let { t -> fieldOptions.firstOrNull { it.id == t.fieldId }?.title }.orEmpty()
                DirectorOfficeSelectablePlanCard(
                    title = plan.title,
                    subtitle = "${fieldTitle.ifBlank { "زمینه نامشخص" }}  ←  ${tazieh?.title ?: plan.taziehUid}",
                    selected = selectedPlan?.id == plan.id,
                    titleBold = true,
                    onClick = {
                        scope.launch {
                            selectedPlan = plan
                            val target = libraryDb.taziehDao().getByUid(plan.taziehUid)
                            roleOptions = target?.let { loadCurrentTaziehRoles(libraryDb, it) }.orEmpty()
                            selectedRole = roleOptions.firstOrNull()
                            val versions = dao.versions(plan.id)
                            planVersions = versions
                            selectedVersion = versions.firstOrNull()
                            sources = dao.sourceFiles(plan.id)
                            turns = selectedVersion?.let { dao.turns(it.id) } ?: emptyList()
                            selectedVersion?.let {
                                stageCues = dao.stageCues(it.id)
                                previewText = buildDirectorVersionPreview(dao, it.id)
                            } ?: run { stageCues = emptyList() }
                            val currentVersion = selectedVersion
                            if (currentVersion != null && (isAdmin || canCreatePersonal || canEditPersonal) && !directorVersionHasText(dao, currentVersion.id)) {
                                val selectedTazieh = libraryDb.taziehDao().getByUid(plan.taziehUid)
                                if (selectedTazieh != null) {
                                    val result = importExistingTaziehText(libraryDb, dao, transactionRunner, currentVersion.id, selectedTazieh.id)
                                    turns = dao.turns(currentVersion.id)
                                    previewText = buildDirectorVersionPreview(dao, currentVersion.id)
                                    message = if (result.sectionCount > 0) "${result.sectionCount} بخش از متن‌های داخلی همین مجلس در ${result.turnCount} نوبت به دفتر منتقل شد." else "متن ذخیره‌شده‌ای برای انتقال پیدا نشد."
                                }
                            }
                            selectedVersion?.let { loadWorkspaceIntoScreen(plan.id, it.id) }
                        }
                    },
                    trailingContent = {
                        if (isAdmin || canEditPersonal) IconButton(onClick = { deletePlanCandidate = plan }) {
                            Icon(Icons.Default.Delete, contentDescription = "حذف دفتر")
                        }
                    }
                )
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
                    Text("نسخه انتخابی: ${selectedVersion?.title ?: "ندارد"}", style = MaterialTheme.typography.bodySmall)
                    Text("نقش‌های ثبت‌شده در همین مجلس (${roleOptions.size})", fontWeight = FontWeight.SemiBold)
                    if (roleOptions.isEmpty()) Text("برای این مجلس هنوز نقشی در کتابخانه ثبت نشده است.", color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
                    else Text(roleOptions.joinToString("، ") { it.title }, style = MaterialTheme.typography.bodySmall)
                    Card(
                        Modifier.fillMaxWidth(),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer)
                    ) {
                        Column(Modifier.fillMaxWidth().heightIn(min = 130.dp, max = 280.dp).verticalScroll(rememberScrollState()).padding(12.dp)) {
                            Text("متن کامل دفتر (نمایش پیوسته)", fontWeight = FontWeight.Bold)
                            Spacer(Modifier.height(6.dp))
                            Text(previewText.ifBlank { "پس از تکمیل دفتر، متن تمام نقش‌ها به ترتیب نوبت در همین کادر نمایش داده می‌شود." }, style = MaterialTheme.typography.bodyMedium)
                        }
                    }
                    if (planVersions.isNotEmpty()) {
                        Card(
                            Modifier.fillMaxWidth(),
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
                        ) {
                            Column(Modifier.fillMaxWidth().padding(vertical = 4.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                                planVersions.forEach { version ->
                                    TextButton(
                                        onClick = {
                                            selectedVersion = version
                                            version.changeNotes.substringAfter("روش تکمیل دفتر: ", "").takeIf { it in DirectorOfficeSharedContract.methodCodes }?.let { selectedBuildMode = it }
                                            scope.launch {
                                                loadWorkspaceIntoScreen(selectedPlan!!.id, version.id)
                                            }
                                        },
                                        modifier = Modifier.fillMaxWidth()
                                    ) { Text("نسخه ${version.versionNumber}: ${version.title}  •  ${if (version.status == DirectorPlanVersionEntity.STATUS_PUBLISHED) "منتشرشده" else "پیش‌نویس"}") }
                                }
                            }
                        }
                    }
                    Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedButton(
                            modifier = Modifier.fillMaxWidth(),
                            enabled = (isAdmin || canCreatePersonal || canEditPersonal) && canBuildSelectedMethod && selectedPlan != null && selectedVersion != null && selectedVersion!!.status != DirectorPlanVersionEntity.STATUS_PUBLISHED && !busy,
                            onClick = {
                                selectedBuildMode = selectedVersion?.changeNotes?.substringAfter("روش تکمیل دفتر: ", "")
                                    ?.takeIf { it in DirectorOfficeSharedContract.methodCodes } ?: DirectorOfficeSharedContract.ROLE_NUMBERED
                                showBuildModeDialog = true
                            }
                        ) { Text("انتخاب روش ساخت / بازسازی این نسخه") }
                        OutlinedButton(modifier = Modifier.fillMaxWidth(), enabled = canEditPersonal && selectedPlan != null && selectedVersion != null && !busy, onClick = {
                            scope.launch {
                                busy = true
                                try {
                                    val plan = selectedPlan!!
                                    val sourceVersion = selectedVersion!!
                                    val now = System.currentTimeMillis()
                                    val newNumber = (dao.versions(plan.id).maxOfOrNull { it.versionNumber } ?: 0) + 1
                                    val newVersionId = transactionRunner.run {
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
                                    previewText = buildDirectorVersionPreview(dao, newVersionId)
                                    message = "نسخه پیش‌نویس جدید با کپی کامل ترتیب، نقش‌ها، متن‌ها و راهنماهای صحنه ساخته شد."
                                } catch (e: Exception) { message = "ساخت نسخه جدید ناموفق بود: ${e.message}" }
                                finally { busy = false }
                            }
                        }) { Text("ساخت نسخه جدید از نسخه انتخابی", maxLines = 2) }
                        if (isAdmin) Button(modifier = Modifier.fillMaxWidth(), enabled = (isAdmin || canEditPersonal) && selectedPlan != null && selectedVersion != null && selectedVersion!!.status != DirectorPlanVersionEntity.STATUS_PUBLISHED && !busy, onClick = {
                            scope.launch {
                                busy = true
                                try {
                                    val plan = selectedPlan!!
                                    val version = selectedVersion!!
                                    transactionRunner.run {
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
                        }) { Text("انتشار نسخه انتخابی", maxLines = 2) }

                    }
                    Card(
                        Modifier.fillMaxWidth(),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.tertiaryContainer)
                    ) {
                        Column(
                            Modifier.fillMaxWidth().padding(12.dp),
                            verticalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            if (turns.isEmpty()) {
                                OutlinedButton(
                                    modifier = Modifier.fillMaxWidth(),
                                    enabled = canBuildSelectedMethod && (isAdmin || canCreatePersonal || canEditPersonal) && selectedPlan != null && selectedVersion != null && !busy,
                                    onClick = {
                                        scope.launch {
                                            busy = true
                                            try {
                                                val plan = selectedPlan!!
                                                val version = selectedVersion!!
                                                val tazieh = libraryDb.taziehDao().getByUid(plan.taziehUid)
                                                if (tazieh == null) {
                                                    message = "مجلس مرتبط با این دفتر در کتابخانه پیدا نشد."
                                                } else {
                                                    val result = importExistingTaziehText(libraryDb, dao, transactionRunner, version.id, tazieh.id)
                                                    turns = dao.turns(version.id)
                                                    previewText = buildDirectorVersionPreview(dao, version.id)
                                                    message = if (result.sectionCount > 0) {
                                                        "${result.sectionCount} بخش از متن‌های داخلی ${result.roleCount} نقش در ${result.turnCount} نوبت به دفتر منتقل شد."
                                                    } else {
                                                        "در نقش‌های این مجلس متن ذخیره‌شده‌ای برای انتقال پیدا نشد."
                                                    }
                                                    refresh++
                                                }
                                            } catch (e: Exception) {
                                                message = "انتقال متن‌های داخلی ناموفق بود: ${e.message}"
                                            } finally {
                                                busy = false
                                            }
                                        }
                                    }
                                ) { Text("تکمیل دفتر از متن‌های موجود برنامه") }
                            }
                            Button(
                                modifier = Modifier.fillMaxWidth(),
                                onClick = { importLauncher.launch(arrayOf("text/plain", "application/json", "application/pdf", "application/vnd.openxmlformats-officedocument.wordprocessingml.document", "application/octet-stream", "image/*")) },
                                enabled = canImportSources && !busy
                            ) {
                                Icon(Icons.Default.FileOpen, null); Spacer(Modifier.width(6.dp)); Text("ورود فایل کامل یا فایل‌های جداگانه نقش‌ها")
                            }
                        }
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
                    OutlinedTextField(
                        value = previewText,
                        onValueChange = { previewText = it },
                        label = { Text("متن استخراج‌شده / متن قابل اصلاح") },
                        // Keep the preview compact; long content scrolls inside the field.
                        modifier = Modifier.fillMaxWidth().heightIn(min = 120.dp, max = 220.dp),
                        minLines = 3,
                        enabled = isAdmin || canCreatePersonal || canEditPersonal || canImportSources
                    )
                }
                item {
                    Button(
                        enabled = canBuildMethod(DirectorOfficeSharedContract.ROLE_NUMBERED) && (isAdmin || canCreatePersonal || canEditPersonal) && selectedVersion != null && selectedRole != null && previewText.isNotBlank() && !busy,
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
                if (selectedBuildMode == DirectorOfficeSharedContract.SELECTOR && canBuildMethod(DirectorOfficeSharedContract.SELECTOR) && selectedVersion != null) {
                    item {
                        Divider()
                        Text("انتخاب دیالوگ با معین‌البکا", fontWeight = FontWeight.Bold)
                        Text("ابتدا نقش نوبت جاری را انتخاب کنید. دیالوگ‌هایی که قبلاً در همین نسخه ثبت شده‌اند دوباره نمایش داده نمی‌شوند.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        ExposedDropdownMenuBox(expanded = selectorRoleMenu, onExpandedChange = { selectorRoleMenu = !selectorRoleMenu }) {
                            OutlinedTextField(
                                value = selectorRole?.title ?: "انتخاب نقش",
                                onValueChange = {}, readOnly = true,
                                label = { Text("نقش نوبت جاری") },
                                trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(selectorRoleMenu) },
                                modifier = Modifier.menuAnchor().fillMaxWidth(), enabled = roleOptions.isNotEmpty()
                            )
                            ExposedDropdownMenu(expanded = selectorRoleMenu, onDismissRequest = { selectorRoleMenu = false }) {
                                roleOptions.forEach { role -> DropdownMenuItem(text = { Text(role.title) }, onClick = { selectorRole = role; selectorRoleMenu = false }) }
                            }
                        }
                        OutlinedTextField(selectorTurnNumber, { selectorTurnNumber = it.filter(Char::isDigit).take(6) }, label = { Text("شماره نوبت بعدی") }, modifier = Modifier.fillMaxWidth(), singleLine = true)
                        val availableDialogues = selectorItems.filter { it.role.uid == selectorRole?.uid && it.key !in selectedDialogueKeys }
                        Text("دیالوگ‌های باقی‌مانده: ${availableDialogues.size}", style = MaterialTheme.typography.labelMedium)
                        if (availableDialogues.isEmpty()) {
                            Text("برای این نقش دیالوگ انتخاب‌نشده‌ای باقی نمانده است یا متن نقش بارگذاری نشده است.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        Column(Modifier.fillMaxWidth().heightIn(max = 340.dp).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            availableDialogues.forEach { entry ->
                                Card(Modifier.fillMaxWidth().clickable(enabled = !busy) {
                                    scope.launch {
                                        busy = true
                                        try {
                                            val version = selectedVersion!!
                                            val number = selectorTurnNumber.toIntOrNull()
                                                ?: ((dao.turns(version.id).maxOfOrNull { it.turnNumber } ?: 0) + 1)
                                            workspaceRepository.addSelectorDialogue(
                                                planId = selectedPlan!!.id,
                                                entry = DirectorOfficeSelectorEntry(
                                                    key = entry.key,
                                                    roleUid = entry.role.uid,
                                                    roleTitle = entry.role.title,
                                                    sectionUid = entry.section.uid,
                                                    sectionTitle = entry.section.title,
                                                    text = entry.text
                                                ),
                                                turnNumber = number,
                                                versionId = version.id
                                            )
                                            selectedDialogueKeys = selectedDialogueKeys + entry.key
                                            selectorTurnNumber = (number + 1).toString()
                                            loadWorkspaceIntoScreen(selectedPlan!!.id, version.id)
                                            message = "دیالوگ نقش «${entry.role.title}» در نوبت $number ثبت شد."
                                        } catch (e: Exception) { message = "ثبت دیالوگ ناموفق بود: ${e.message}" }
                                        finally { busy = false }
                                    }
                                }) {
                                    Column(Modifier.fillMaxWidth().padding(10.dp)) {
                                        Text("${entry.role.title} • ${entry.section.title}", fontWeight = FontWeight.SemiBold)
                                        Text(entry.text, style = MaterialTheme.typography.bodySmall)
                                        Text("انتخاب و ثبت در دفتر", color = MaterialTheme.colorScheme.primary, style = MaterialTheme.typography.labelMedium)
                                    }
                                }
                            }
                        }
                    }
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
                    DirectorOfficeTurnInputFields(
                        number = turnNumber,
                        onNumberChange = { turnNumber = it },
                        text = turnText,
                        onTextChange = { turnText = it },
                        textLabel = "متن این نوبت",
                        textMinHeight = 120,
                        textMaxHeight = 220
                    )
                    Button(enabled = canBuildMethod(DirectorOfficeSharedContract.MANUAL) && (isAdmin || canCreatePersonal || canEditPersonal) && selectedVersion != null && selectedRole != null && turnText.isNotBlank() && !busy, onClick = {
                        scope.launch {
                            busy = true
                            try {
                                val version = selectedVersion!!
                                val nextIndex = (dao.turns(version.id).maxOfOrNull { it.sequenceIndex } ?: -1) + 1
                                val number = turnNumber.toIntOrNull() ?: (nextIndex + 1)
                                val linkedRole = selectedRole!!
                                workspaceRepository.addRoleLinkedTurn(
                                    versionId = version.id,
                                    turnNumber = number,
                                    roleUid = linkedRole.uid,
                                    roleTitle = linkedRole.title,
                                    text = turnText.trim(),
                                    allowPublishedPlan = true
                                )
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
                    Text("راهنماهای صحنه", fontWeight = FontWeight.Bold)
                    Text("برای ثبت راهنما، یک نوبت را انتخاب کنید. راهنمای «بلافاصله» مانع عبور خودکار به نوبت بعدی می‌شود.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
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
                    DirectorOfficeStageCueInputFields(
                        instruction = cueInstruction,
                        onInstructionChange = { cueInstruction = it },
                        instructionLabel = "شرح راهنمای صحنه",
                        instructionMinHeight = 0,
                        instructionMaxHeight = null
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Button(enabled = (isAdmin || canCreatePersonal || canEditPersonal) && selectedVersion != null && selectedCueTurn != null && cueInstruction.isNotBlank() && !busy, onClick = {
                            scope.launch {
                                try {
                                    workspaceRepository.addStageCue(
                                        planId = selectedPlan!!.id,
                                        title = "راهنمای صحنه",
                                        instruction = cueInstruction.trim(),
                                        turnId = selectedCueTurn!!.id,
                                        cueType = DirectorStageCueEntity.CUE_OTHER,
                                        blocksNextTurn = false
                                    )
                                    cueInstruction = ""
                                    message = "راهنمای صحنه ثبت شد."
                                    reload()
                                } catch (e: Exception) { message = "ثبت راهنما ناموفق بود: ${e.message}" }
                            }
                        }) { Text("ثبت راهنما") }
                        OutlinedButton(enabled = (isAdmin || canCreatePersonal || canEditPersonal) && selectedVersion != null && selectedCueTurn != null && !busy, onClick = {
                            scope.launch {
                                try {
                                    val role = dao.participants(selectedCueTurn!!.id).firstOrNull()?.roleUid.orEmpty()
                                    workspaceRepository.addStageCue(
                                        planId = selectedPlan!!.id,
                                        title = "بلافاصله",
                                        instruction = cueInstruction.ifBlank { "ادامه اجرا با همان نقش؛ به نوبت بعدی منتقل نشود." },
                                        turnId = selectedCueTurn.id,
                                        cueType = DirectorStageCueEntity.CUE_IMMEDIATE,
                                        blocksNextTurn = true,
                                        continuationRoleUid = role
                                    )
                                    cueInstruction = ""
                                    message = "راهنمای بلافاصله ثبت شد و عبور به نوبت بعدی را مسدود می‌کند."
                                    reload()
                                } catch (e: Exception) { message = "ثبت راهنمای بلافاصله ناموفق بود: ${e.message}" }
                            }
                        }) { Text("بلافاصله") }
                    }
                }
                directorOfficeSharedWorkspaceItems(
                    workspace = workspaceState,
                    query = turnSearch,
                    onQueryChange = { turnSearch = it },
                    showSearchField = true,
                    searchLabel = "جستجو در شماره، نقش و متن دیالوگ",
                    listTitle = "نوبت‌های ثبت‌شده",
                    emptyMessage = "برای این نسخه هنوز نوبتی ثبت نشده است.",
                    searchResultIndex = selectedSearchResultIndex,
                    onPreviousSearchResult = { selectedSearchResultIndex = (selectedSearchResultIndex - 1).coerceAtLeast(0) },
                    onNextSearchResult = { selectedSearchResultIndex = (selectedSearchResultIndex + 1).coerceAtMost((visibleTurns.size - 1).coerceAtLeast(0)) },
                    turnActions = { turn, turnPosition, totalTurns ->
                        val segmentState = remember(turn.id) { mutableStateOf("") }
                        val segmentsState = remember(turn.id) { mutableStateOf(emptyList<DirectorTextSegmentEntity>()) }
                        val participantsState = remember(turn.id) { mutableStateOf(emptyList<DirectorParticipantEntity>()) }
                        var savingDialogue by remember(turn.id) { mutableStateOf(false) }
                        var showTurnCueDialog by remember(turn.id) { mutableStateOf(false) }
                        var turnCueDraft by remember(turn.id) { mutableStateOf("") }
                        var editingTurnCueId by remember(turn.id) { mutableStateOf<Long?>(null) }
                        var editingSegmentItem by remember(turn.id) { mutableStateOf<DirectorTextSegmentEntity?>(null) }
                        var editingSegmentDraft by remember(turn.id) { mutableStateOf("") }
                        var deletingSegmentItem by remember(turn.id) { mutableStateOf<DirectorTextSegmentEntity?>(null) }
                        val turnStageCues = stageCues.filter { it.turnId == turn.id }
                        val turnCardIndex = turnPosition
                        val turnPalette = directorOfficeTurnPalette(turnCardIndex, darkMode)

                        LaunchedEffect(turn.id, refresh, workspaceState) {
                            segmentsState.value = workspaceState?.textSegmentsByTurn?.get(turn.id)
                                ?: dao.textSegments(turn.id)
                            segmentState.value = segmentsState.value.joinToString("\n") { it.text }
                            participantsState.value = workspaceState?.participantsByTurn?.get(turn.id)
                                ?: dao.participants(turn.id)
                        }

                        DirectorOfficeSharedTurnActions(
                            editable = workspaceState?.isReadOnly != true && (isAdmin || canEditPersonal),
                            participants = participantsState.value,
                            segments = segmentsState.value,
                            stageCues = turnStageCues,
                            collapseStageCues = true,
                            containerColor = turnPalette.containerColor,
                            contentColor = turnPalette.contentColor,
                            inlineEditorText = segmentState.value,
                            onInlineEditorTextChange = { segmentState.value = it },
                            onSaveInlineEditor = {
                                scope.launch {
                                    savingDialogue = true
                                    try {
                                        val textToSave = segmentState.value.trim()
                                        workspaceRepository.replaceTurnText(turn.id, textToSave)
                                        loadWorkspaceIntoScreen(selectedPlan?.id ?: error("دفتر انتخاب‌شده پیدا نشد."), turn.versionId)
                                        val savedSegments = workspaceState?.textSegmentsByTurn?.get(turn.id).orEmpty()
                                        segmentsState.value = savedSegments
                                        val savedText = savedSegments.joinToString("\n") { it.text }
                                        segmentState.value = savedText
                                        turnSearchCorpus = turnSearchCorpus + (turn.id to savedText)
                                        previewText = buildDirectorVersionPreview(dao, turn.versionId)
                                        message = "متن دیالوگ نوبت ${turn.turnNumber} ذخیره شد؛ متن اصلی کتابخانه تغییر نکرد."
                                    } catch (e: Exception) {
                                        message = "ذخیره متن نوبت انجام نشد: ${e.message}"
                                    } finally {
                                        savingDialogue = false
                                    }
                                }
                            },
                            onSaveNumber = { value ->
                                scope.launch {
                                    try {
                                        workspaceRepository.updateTurn(turn.copy(turnNumber = value))
                                        loadWorkspaceIntoScreen(selectedPlan?.id ?: error("دفتر انتخاب‌شده پیدا نشد."), turn.versionId)
                                        message = "شماره نوبت اصلاح شد؛ ترتیب نمایش حفظ شد."
                                    } catch (e: Exception) {
                                        message = "اصلاح شماره نوبت انجام نشد: ${e.message}"
                                    }
                                }
                            },
                            onMoveUp = if (turnPosition > 0) ({
                                scope.launch {
                                    try {
                                        val reordered = turns.toMutableList().apply { add(turnPosition - 1, removeAt(turnPosition)) }
                                        workspaceRepository.reorderTurns(reordered)
                                        loadWorkspaceIntoScreen(selectedPlan?.id ?: error("دفتر انتخاب‌شده پیدا نشد."), turn.versionId)
                                    } catch (e: Exception) {
                                        message = "تغییر ترتیب نوبت انجام نشد: ${e.message}"
                                    }
                                }
                            }) else null,
                            onMoveDown = if (turnPosition >= 0 && turnPosition < totalTurns - 1) ({
                                scope.launch {
                                    try {
                                        val reordered = turns.toMutableList().apply { add(turnPosition + 1, removeAt(turnPosition)) }
                                        workspaceRepository.reorderTurns(reordered)
                                        loadWorkspaceIntoScreen(selectedPlan?.id ?: error("دفتر انتخاب‌شده پیدا نشد."), turn.versionId)
                                    } catch (e: Exception) {
                                        message = "تغییر ترتیب نوبت انجام نشد: ${e.message}"
                                    }
                                }
                            }) else null,
                            onEditCue = { cue ->
                                editingTurnCueId = cue.id
                                turnCueDraft = cue.instruction
                                showTurnCueDialog = true
                            },
                            onDeleteCue = { cue ->
                                scope.launch {
                                    try {
                                        workspaceRepository.deleteStageCue(cue.id)
                                        loadWorkspaceIntoScreen(selectedPlan?.id ?: error("دفتر انتخاب‌شده پیدا نشد."), turn.versionId)
                                        message = "راهنمای صحنه از این نوبت حذف شد؛ متن دیالوگ بدون تغییر ماند."
                                    } catch (e: Exception) {
                                        message = "حذف راهنمای صحنه انجام نشد: ${e.message}"
                                    }
                                }
                            },
                            onAddCue = {
                                editingTurnCueId = null
                                turnCueDraft = ""
                                showTurnCueDialog = true
                            },
                            onEditSegment = { segment ->
                                editingSegmentItem = segment
                                editingSegmentDraft = segment.text
                            },
                            onDeleteSegment = { segment -> deletingSegmentItem = segment },
                            extraContent = {
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
                                                        } catch (e: Exception) {
                                                            message = "اتصال نقش انجام نشد: ${e.message}"
                                                        }
                                                    }
                                                    mappingExpanded = false
                                                })
                                            }
                                        }
                                    }
                                }
                            },
                            afterCardContent = {
                                if (savingDialogue) LinearProgressIndicator(Modifier.fillMaxWidth())
                                if (showTurnCueDialog) {
                                    DirectorOfficeStageCueEditorDialog(
                                        title = if (editingTurnCueId == null) "ثبت راهنمای صحنه — نوبت ${turn.turnNumber}" else "ویرایش راهنمای صحنه — نوبت ${turn.turnNumber}",
                                        instruction = turnCueDraft,
                                        onInstructionChange = { turnCueDraft = it },
                                        instructionLabel = "راهنمای مستقل کارگردان برای این نوبت",
                                        instructionMinHeight = 120,
                                        instructionMaxHeight = 240,
                                        confirmLabel = "ذخیره راهنما",
                                        confirmEnabled = (isAdmin || canEditPersonal || canCreatePersonal) && turnCueDraft.isNotBlank() && !busy,
                                        onDismiss = { showTurnCueDialog = false },
                                        onConfirm = {
                                            scope.launch {
                                                try {
                                                    val existingCue = editingTurnCueId?.let { id -> dao.stageCue(id) }
                                                    if (existingCue != null) {
                                                        workspaceRepository.updateStageCue(existingCue.copy(instruction = turnCueDraft.trim()))
                                                        message = "راهنمای صحنه این نوبت ویرایش شد."
                                                    } else {
                                                        workspaceRepository.addStageCue(
                                                            planId = selectedPlan!!.id,
                                                            title = "راهنمای صحنه",
                                                            instruction = turnCueDraft.trim(),
                                                            turnId = turn.id,
                                                            cueType = DirectorStageCueEntity.CUE_OTHER
                                                        )
                                                        message = "راهنمای صحنه به نوبت ${turn.turnNumber} متصل شد."
                                                    }
                                                    loadWorkspaceIntoScreen(selectedPlan?.id ?: error("دفتر انتخاب‌شده پیدا نشد."), turn.versionId)
                                                    showTurnCueDialog = false
                                                } catch (e: Exception) {
                                                    message = "ذخیره راهنمای صحنه انجام نشد: ${e.message}"
                                                }
                                            }
                                        }
                                    )
                                }
                                editingSegmentItem?.let { segment ->
                                    AlertDialog(
                                        onDismissRequest = { editingSegmentItem = null },
                                        title = { Text("ویرایش بخش دیالوگ") },
                                        text = {
                                            DirectorOfficeDialogueTextField(
                                                value = editingSegmentDraft,
                                                onValueChange = { editingSegmentDraft = it },
                                                label = "متن دیالوگ",
                                                minHeight = 140,
                                                maxHeight = 300
                                            )
                                        },
                                        confirmButton = {
                                            TextButton(enabled = (isAdmin || canEditPersonal || canCreatePersonal) && editingSegmentDraft.isNotBlank(), onClick = {
                                                scope.launch {
                                                    try {
                                                        workspaceRepository.updateTextSegment(segment.copy(text = editingSegmentDraft.trim()))
                                                        loadWorkspaceIntoScreen(selectedPlan?.id ?: error("دفتر انتخاب‌شده پیدا نشد."), turn.versionId)
                                                        editingSegmentItem = null
                                                        message = "بخش دیالوگ ذخیره شد."
                                                    } catch (e: Exception) {
                                                        message = "ویرایش دیالوگ ناموفق بود: ${e.message}"
                                                    }
                                                }
                                            }) { Text("ذخیره") }
                                        },
                                        dismissButton = { TextButton(onClick = { editingSegmentItem = null }) { Text("انصراف") } }
                                    )
                                }
                                deletingSegmentItem?.let { segment ->
                                    AlertDialog(
                                        onDismissRequest = { deletingSegmentItem = null },
                                        title = { Text("حذف بخش دیالوگ") },
                                        text = { Text("این بخش از متن نوبت حذف شود؟") },
                                        confirmButton = {
                                            TextButton(enabled = isAdmin || canEditPersonal, onClick = {
                                                scope.launch {
                                                    try {
                                                        workspaceRepository.deleteTextSegment(segment)
                                                        loadWorkspaceIntoScreen(selectedPlan?.id ?: error("دفتر انتخاب‌شده پیدا نشد."), turn.versionId)
                                                        deletingSegmentItem = null
                                                        message = "بخش دیالوگ حذف شد."
                                                    } catch (e: Exception) {
                                                        message = "حذف دیالوگ ناموفق بود: ${e.message}"
                                                    }
                                                }
                                            }) { Text("حذف") }
                                        },
                                        dismissButton = { TextButton(onClick = { deletingSegmentItem = null }) { Text("انصراف") } }
                                    )
                                }
                            }
                        )
                    },
                    afterWorkspace = { globalCues ->
                        androidx.compose.foundation.layout.Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            Text("راهنماهای عمومی صحنه", style = MaterialTheme.typography.titleSmall)
                            globalCues.forEach { cue ->
                                ListItem(
                                    headlineContent = { Text(cue.title.replace("دستور صحنه", "راهنمای صحنه").ifBlank { cue.cueType }) },
                                    supportingContent = { Text(cue.instruction + if (cue.blocksNextTurn) " • ادامه همان نقش" else "") },
                                    trailingContent = {
                                        if (isAdmin || canEditPersonal) TextButton(onClick = {
                                            scope.launch {
                                                try {
                                                    workspaceRepository.deleteStageCue(cue.id)
                                                    loadWorkspaceIntoScreen(selectedPlan?.id ?: error("دفتر انتخاب‌شده پیدا نشد."), selectedVersion?.id)
                                                    message = "راهنمای صحنه حذف شد."
                                                } catch (e: Exception) {
                                                    message = "حذف راهنما انجام نشد: ${e.message}"
                                                }
                                            }
                                        }) { Text("حذف") }
                                    }
                                )
                            }
                        }
                    }
                )
            }
            if (busy) item { LinearProgressIndicator(Modifier.fillMaxWidth()) }
            message?.let { msg -> item { Text(msg, color = MaterialTheme.colorScheme.primary) } }
    }
    if (showBuildModeDialog) {
        AlertDialog(
            onDismissRequest = { showBuildModeDialog = false },
            title = { Text("روش تکمیل دفتر معین‌البکا") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("معین‌البکا تعیین کند دفتر چگونه ساخته شود. با تأیید، فقط نوبت‌های همین نسخه دفتر بازسازی می‌شوند؛ نسخه‌های دیگر و متن اصلی کتابخانه دست‌نخورده می‌مانند:")
                    DirectorOfficeSharedContract.buildMethods.forEachIndexed { index, method ->
                        val orderLabel = listOf("۱)", "۲)", "۳)", "۴)").getOrElse(index) { "${index + 1})" }
                        DirectorOfficeBuildMethodOption(
                            method = method,
                            selected = selectedBuildMode == method.code,
                            onClick = { if (canBuildMethod(method.code)) selectedBuildMode = method.code },
                            orderLabel = orderLabel,
                            enabled = canBuildMethod(method.code)
                        )
                    }
                }
            },
            confirmButton = {
                TextButton(enabled = canBuildSelectedMethod && (isAdmin || canCreatePersonal || canEditPersonal), onClick = {
                    val version = selectedVersion
                    val plan = selectedPlan
                    if (version == null || plan == null) {
                        showBuildModeDialog = false
                        return@TextButton
                    }
                    scope.launch {
                        busy = true
                        try {
                            val tazieh = libraryDb.taziehDao().getByUid(plan.taziehUid)
                            if (tazieh == null) throw IllegalStateException("مجلس مرتبط پیدا نشد")
                            val roles = loadCurrentTaziehRoles(libraryDb, tazieh)
                            val sections = libraryDb.sectionDao().getByRoles(roles.map { it.id })
                                .filter { it.content.isNotBlank() }
                                .sortedWith(compareBy<SectionEntity> { section -> roles.firstOrNull { it.id == section.roleId }?.orderIndex ?: Int.MAX_VALUE }.thenBy { it.orderIndex }.thenBy { it.id })
                            val roleInputs = roles.map { role ->
                                DirectorOfficeBuildRole(uid = role.uid, title = role.title, orderIndex = role.orderIndex)
                            }
                            val roleUidById = roles.associate { it.id to it.uid }
                            val sectionInputs = sections.mapNotNull { section ->
                                val roleUid = roleUidById[section.roleId] ?: return@mapNotNull null
                                DirectorOfficeBuildSection(
                                    uid = section.uid,
                                    roleUid = roleUid,
                                    content = section.content,
                                    orderIndex = section.orderIndex
                                )
                            }
                            transactionRunner.run {
                                DaoDirectorOfficeWorkspaceRepository(dao).buildDraftFromLibrary(
                                    versionId = version.id,
                                    method = selectedBuildMode,
                                    roles = roleInputs,
                                    sections = sectionInputs
                                )
                                dao.updateVersion(version.copy(changeNotes = "روش تکمیل دفتر: $selectedBuildMode"))
                            }
                            loadWorkspaceIntoScreen(plan.id, version.id)
                            selectedRole = roles.firstOrNull()
                            selectorRole = roles.firstOrNull()
                            selectorTurnNumber = ((turns.maxOfOrNull { it.turnNumber } ?: 0) + 1).toString()
                            selectedDialogueKeys = emptySet()
                            selectorItems = sections.flatMap { section ->
                                val role = roles.firstOrNull { it.id == section.roleId } ?: return@flatMap emptyList()
                                DirectorOfficeWorkspaceUseCases.splitDialogues(section.content).mapIndexed { index, part -> DirectorSelectorItem("${section.uid}:$index", section, role, part) }
                            }
                            if (selectedBuildMode == DirectorOfficeSharedContract.SELECTOR) {
                                selectedDialogueKeys = usedDirectorDialogueKeys(dao, version.id, selectorItems)
                            }
                            message = when (selectedBuildMode) {
                                DirectorOfficeSharedContract.ROLE_NUMBERED -> "نوبت‌ها بر اساس شماره‌های داخل متن نقش‌ها ساخته شدند؛ ${turns.size} شماره دیالوگ شناسایی شد و متن همان دیالوگ‌ها ثبت شد."
                                DirectorOfficeSharedContract.FULL_TEXT -> "متن کامل ${turns.size} نقش به‌ترتیب در دفتر قرار گرفت."
                                DirectorOfficeSharedContract.MANUAL -> "حالت دستی فعال شد؛ متن اصلی مجلس تغییری نکرد."
                                else -> "حالت انتخاب با معین‌البکا فعال شد؛ دیالوگ‌های انتخاب‌شده دیگر در فهرست باقی‌مانده نمایش داده نمی‌شوند."
                            }
                            showBuildModeDialog = false
                        } catch (e: Exception) { message = "ساخت دفتر با روش انتخابی ناموفق بود: ${e.message}" }
                        finally { busy = false }
                    }
                }) { Text("ایجاد دفتر با این روش") }
            },
            dismissButton = { TextButton(onClick = { showBuildModeDialog = false }) { Text("بعداً") } }
        )
    }
    if (showOfficeSettingsDialog) {
        DirectorOfficeAppearanceSettingsDialog(
            darkMode = darkMode,
            onDarkModeChange = onDarkModeChange,
            autoDarkMode = autoDarkMode,
            onAutoDarkModeChange = onAutoDarkModeChange,
            fontScale = fontScale,
            onFontScaleChange = onFontScaleChange,
            lineSpacing = officeLineSpacing,
            onLineSpacingChange = { officeLineSpacing = it; Prefs.setLineSpacing(context, it) },
            fontChoice = fontChoice,
            onFontChoiceChange = onFontChoiceChange,
            themeChoice = themeChoice,
            onThemeChoiceChange = onThemeChoiceChange,
            keepScreenOn = keepScreenOn,
            onKeepScreenOnChange = onKeepScreenOnChange,
            onDismiss = { showOfficeSettingsDialog = false }
        )
    }
    deletePlanCandidate?.let { candidate ->
        AlertDialog(
            onDismissRequest = { deletePlanCandidate = null },
            title = { Text("حذف دفتر معین‌البکا") },
            text = { Text("دفتر «${candidate.title}» از فهرست فعال خارج می‌شود. فایل‌های منبع و متن اصلی مجلس حذف نمی‌شوند. ادامه می‌دهید؟") },
            confirmButton = {
                TextButton(enabled = isAdmin || canEditPersonal, onClick = {
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
                    root.length() == 1
                if (!fieldMatches) continue
                val taziehs = field.optJSONArray("taziehs") ?: continue
                for (ti in 0 until taziehs.length()) {
                    val tazieh = taziehs.getJSONObject(ti)
                    val majlisTitle = ContentUid.normalize(tazieh.optString("title"))
                    val majlisMatches = majlisTitle.equals(ContentUid.normalize(targetTaziehTitle), true) ||
                        totalTaziehs == 1
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
            currentNumber = match?.groups?.get(1)?.value?.let { DirectorOfficeWorkspaceUseCases.normalizeNumber(it) }?.toIntOrNull()
            val inline = match?.groups?.get(3)?.value.orEmpty().trim()
            if (inline.isNotBlank()) body.append(inline).append('\n')
        } else if (currentRole != null) {
            body.append(line).append('\n')
        }
    }
    flush()
    return result
}

private fun toPersianDirectorDigits(value: String): String = value.map { ch ->
    if (ch in '0'..'9') ('۰'.code + (ch.code - '0'.code)).toChar() else ch
}.joinToString("")

private suspend fun resolveDirectorRole(db: AppDatabase, taziehUid: String, roleTitle: String): RoleEntity? {
    val tazieh = db.taziehDao().getByUid(taziehUid) ?: return null
    return loadCurrentTaziehRoles(db, tazieh).firstOrNull {
        ContentUid.normalize(it.title).equals(ContentUid.normalize(roleTitle), ignoreCase = true)
    }
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


// --- عناصر رابط کاربری مشترک که پیش‌تر در DirectorOfficeSharedScreen.kt بودند ---

/**
 * تنها قاب اصلی صفحه دفتر معین‌البکاء در Admin و Viewer.
 * این کامپوننت مالک TopAppBar، ناحیه زبانه/عنوان تکمیلی و LazyColumn اصلی است؛
 * هیچ Flavorای Scaffold یا فهرست پیمایش مستقلی برای صفحه دفتر نمی‌سازد.
 * محتواهای اختصاصی هر Flavor فقط به‌صورت آیتم/رویداد به همین صفحه تزریق می‌شوند.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DirectorOfficeSharedScreen(
    title: String,
    onBack: () -> Unit,
    listState: LazyListState,
    actions: @Composable RowScope.() -> Unit = {},
    topBarContent: @Composable ColumnScope.() -> Unit = {},
    listModifier: Modifier = Modifier,
    content: LazyListScope.() -> Unit
) {
    Scaffold(
        topBar = {
            Column {
                TopAppBar(
                    title = { Text(title) },
                    navigationIcon = {
                        IconButton(onClick = onBack) {
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "بازگشت")
                        }
                    },
                    actions = actions
                )
                topBarContent()
            }
        }
    ) { scaffoldPadding ->
        LazyColumn(
            state = listState,
            modifier = listModifier.fillMaxSize().padding(scaffoldPadding),
            contentPadding = PaddingValues(14.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
            content = content
        )
    }
}


/** کارت انتخاب دفتر، مشترک بین Admin و Viewer. */
@Composable
fun DirectorOfficeSelectablePlanCard(
    title: String,
    subtitle: String? = null,
    supportingText: String? = null,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    titleBold: Boolean = false,
    trailingContent: (@Composable RowScope.() -> Unit)? = null
) {
    Card(
        modifier = modifier.fillMaxWidth().clickable(onClick = onClick),
        colors = CardDefaults.cardColors(
            containerColor = if (selected) {
                MaterialTheme.colorScheme.secondaryContainer
            } else {
                MaterialTheme.colorScheme.surfaceVariant
            }
        )
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = if (titleBold) FontWeight.Bold else FontWeight.Normal
                )
                subtitle?.takeIf { it.isNotBlank() }?.let {
                    Text(it, style = MaterialTheme.typography.bodySmall)
                }
                supportingText?.takeIf { it.isNotBlank() }?.let {
                    Text(it, style = MaterialTheme.typography.labelSmall)
                }
            }
            trailingContent?.let { content -> Row(content = content) }
        }
    }
}

/**
 * داده و فرمان‌های نمایشی هر کارت نوبت. این مدل عمداً هیچ DAO، دیتابیس یا Flavorای را نمی‌شناسد.
 * Admin و Viewer فقط callbackهای مجاز خودشان را به این مدل می‌دهند.
 */
data class DirectorOfficeSharedTurnActions(
    val editable: Boolean = false,
    val participants: List<DirectorParticipantEntity>? = null,
    val segments: List<DirectorTextSegmentEntity>? = null,
    val stageCues: List<DirectorStageCueEntity>? = null,
    val containerColor: Color? = null,
    val contentColor: Color? = null,
    val inlineEditorText: String? = null,
    val onInlineEditorTextChange: ((String) -> Unit)? = null,
    val onSaveInlineEditor: (() -> Unit)? = null,
    val onSaveNumber: ((Int) -> Unit)? = null,
    val onMoveUp: (() -> Unit)? = null,
    val onMoveDown: (() -> Unit)? = null,
    val onEditTurn: (() -> Unit)? = null,
    val onAddDialogue: (() -> Unit)? = null,
    val onManageParticipants: (() -> Unit)? = null,
    val onDeleteTurn: (() -> Unit)? = null,
    val onEditSegment: ((DirectorTextSegmentEntity) -> Unit)? = null,
    val onDeleteSegment: ((DirectorTextSegmentEntity) -> Unit)? = null,
    val onEditCue: ((DirectorStageCueEntity) -> Unit)? = null,
    val onDeleteCue: ((DirectorStageCueEntity) -> Unit)? = null,
    val onAddCue: (() -> Unit)? = null,
    val collapseStageCues: Boolean = true,
    val extraContent: (@Composable () -> Unit)? = null,
    val afterCardContent: (@Composable () -> Unit)? = null
)


/** رنگ‌بندی مشترک کارت نوبت؛ در هر دو Flavor دقیقاً از یک قاعده استفاده می‌شود. */
data class DirectorOfficeTurnPalette(
    val containerColor: Color,
    val contentColor: Color
)

@Composable
fun directorOfficeTurnPalette(position: Int, darkMode: Boolean): DirectorOfficeTurnPalette {
    val isAlternate = position % 2 != 0
    return if (darkMode) {
        if (isAlternate) DirectorOfficeTurnPalette(
            MaterialTheme.colorScheme.secondaryContainer,
            MaterialTheme.colorScheme.onSecondaryContainer
        ) else DirectorOfficeTurnPalette(
            MaterialTheme.colorScheme.surfaceVariant,
            MaterialTheme.colorScheme.onSurfaceVariant
        )
    } else {
        if (isAlternate) DirectorOfficeTurnPalette(
            Color(0xFFE8F5E9),
            Color(0xFF1B4332)
        ) else DirectorOfficeTurnPalette(
            MaterialTheme.colorScheme.surfaceVariant,
            MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

/**
 * فهرست اصلی دفتر معین‌البکاء؛ تنها محل رندر نوبت‌ها در Admin و Viewer.
 * بخش‌های قبل از نوبت‌ها (مانند انتخاب مجلس، انتخاب دفتر یا ورود/ساخت محتوا) را هر Flavor
 * به‌صورت آیتم‌های بالادستی وارد می‌کند؛ خود جستجو، تیتر نوبت‌ها، کارت‌ها و ترتیب آن‌ها مشترک است.
 */
fun LazyListScope.directorOfficeSharedWorkspaceItems(
    workspace: DirectorOfficeWorkspaceState?,
    query: String,
    onQueryChange: (String) -> Unit,
    showSearchField: Boolean = true,
    searchLabel: String = "جستجو در شماره، نقش و متن دیالوگ",
    listTitle: String = "نوبت‌های دفتر",
    emptyMessage: String = "برای این دفتر هنوز نوبتی ثبت نشده است.",
    searchResultIndex: Int? = null,
    onPreviousSearchResult: (() -> Unit)? = null,
    onNextSearchResult: (() -> Unit)? = null,
    turnActions: @Composable (turn: DirectorTurnEntity, position: Int, total: Int) -> DirectorOfficeSharedTurnActions,
    afterWorkspace: (@Composable (globalCues: List<DirectorStageCueEntity>) -> Unit)? = null
) {
    if (workspace == null) return

    val turns = workspace.turns
    val visibleTurns = DirectorOfficeWorkspaceSearch.filterTurns(
        turns = turns,
        searchableTextByTurn = workspace.searchableTextByTurn,
        query = query
    )
    val cuesByTurn = workspace.stageCues.filter { it.turnId != null }.groupBy { it.turnId }

    item(key = "director-shared-workspace-title") {
        Text(
            text = "$listTitle (${turns.size})" + if (query.isNotBlank()) " • نتایج: ${visibleTurns.size}" else "",
            modifier = Modifier.fillMaxWidth().padding(top = 8.dp, bottom = 2.dp),
            style = MaterialTheme.typography.titleMedium
        )
    }

    if (showSearchField) {
        item(key = "director-shared-workspace-search") {
            ColumnSearchContent(
                query = query,
                onQueryChange = onQueryChange,
                searchLabel = searchLabel,
                resultIndex = searchResultIndex,
                resultCount = visibleTurns.size,
                onPrevious = onPreviousSearchResult,
                onNext = onNextSearchResult
            )
        }
    }

    if (visibleTurns.isEmpty()) {
        item(key = "director-shared-workspace-empty") {
            Text(
                text = if (query.isBlank()) emptyMessage else "نتیجه‌ای برای عبارت جستجو‌شده پیدا نشد.",
                modifier = Modifier.fillMaxWidth().padding(vertical = 14.dp),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                style = MaterialTheme.typography.bodyMedium
            )
        }
    }

    items(visibleTurns, key = { "director-turn-${it.id}" }) { turn ->
        val position = turns.indexOfFirst { it.id == turn.id }
        val actions = turnActions(turn, position, turns.size)
        DirectorOfficeTurnCard(
            turn = turn,
            position = position,
            totalTurns = turns.size,
            participants = actions.participants ?: workspace.participantsByTurn[turn.id].orEmpty(),
            segments = actions.segments ?: workspace.textSegmentsByTurn[turn.id].orEmpty(),
            stageCues = actions.stageCues ?: cuesByTurn[turn.id].orEmpty(),
            editable = actions.editable,
            containerColor = actions.containerColor ?: MaterialTheme.colorScheme.surfaceVariant,
            contentColor = actions.contentColor ?: Color.Unspecified,
            inlineEditorText = actions.inlineEditorText,
            onInlineEditorTextChange = actions.onInlineEditorTextChange,
            onSaveInlineEditor = actions.onSaveInlineEditor,
            onSaveNumber = actions.onSaveNumber,
            onMoveUp = actions.onMoveUp,
            onMoveDown = actions.onMoveDown,
            onEditTurn = actions.onEditTurn,
            onAddDialogue = actions.onAddDialogue,
            onManageParticipants = actions.onManageParticipants,
            onDeleteTurn = actions.onDeleteTurn,
            onEditSegment = actions.onEditSegment,
            onDeleteSegment = actions.onDeleteSegment,
            onEditCue = actions.onEditCue,
            onDeleteCue = actions.onDeleteCue,
            onAddCue = actions.onAddCue,
            collapseStageCues = actions.collapseStageCues,
            extraContent = actions.extraContent
        )
        actions.afterCardContent?.invoke()
    }

    afterWorkspace?.let { renderer ->
        val globalCues = workspace.stageCues.filter { it.turnId == null }
        if (globalCues.isNotEmpty()) {
            item(key = "director-shared-workspace-global-cues") { renderer(globalCues) }
        }
    }
}

@Composable
private fun ColumnSearchContent(
    query: String,
    onQueryChange: (String) -> Unit,
    searchLabel: String,
    resultIndex: Int?,
    resultCount: Int,
    onPrevious: (() -> Unit)?,
    onNext: (() -> Unit)?
) {
    androidx.compose.foundation.layout.Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        DirectorOfficeSearchField(
            value = query,
            onValueChange = onQueryChange,
            label = searchLabel,
            showSearchIcon = false
        )
        if (query.isNotBlank() && resultIndex != null && onPrevious != null && onNext != null) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Text(
                    if (resultCount == 0) "۰ نتیجه" else "${toPersianOfficeDigits((resultIndex + 1).coerceAtMost(resultCount).toString())} از ${toPersianOfficeDigits(resultCount.toString())}",
                    modifier = Modifier.weight(1f),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.secondary
                )
                androidx.compose.material3.TextButton(
                    enabled = resultCount > 0 && resultIndex > 0,
                    onClick = onPrevious
                ) { Text("نتیجه قبلی") }
                androidx.compose.material3.TextButton(
                    enabled = resultCount > 0 && resultIndex < resultCount - 1,
                    onClick = onNext
                ) { Text("نتیجه بعدی") }
            }
        }
    }
}

private fun toPersianOfficeDigits(value: String): String = value.map { ch ->
    if (ch in '0'..'9') ('۰'.code + (ch.code - '0'.code)).toChar() else ch
}.joinToString("")
