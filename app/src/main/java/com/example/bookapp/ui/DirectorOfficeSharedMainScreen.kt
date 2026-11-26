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
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.unit.dp
import com.example.bookapp.data.*
import com.example.bookapp.ui.theme.FontChoiceLabels
import com.example.bookapp.ui.theme.colorSchemeFor
import com.example.bookapp.ui.theme.typographyFor
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.delay
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
    /** متن کامل و تغییرناپذیر بخش منبع؛ انتخاب‌ها با offset به همین متن متصل‌اند. */
    val text: String
)

private data class DirectorSelectorRangeItem(
    val source: DirectorSelectorItem,
    val start: Int,
    val end: Int,
    val text: String
)

private data class DirectorSelectorRangeSelection(
    val source: DirectorSelectorItem,
    val start: Int,
    val end: Int
)

/** بخش‌های مصرف‌نشده متن هر نقش را با حفظ offset دقیق متن اصلی برمی‌گرداند. */
private fun availableDirectorSelectorRanges(
    workspace: DirectorOfficeWorkspaceState?,
    items: List<DirectorSelectorItem>,
    roleUid: String?
): List<DirectorSelectorRangeItem> {
    if (roleUid.isNullOrBlank()) return emptyList()
    val usedSegments = workspace?.textSegmentsByTurn?.values.orEmpty().flatten()
    return items.asSequence().filter { it.role.uid == roleUid }.flatMap { item ->
        val ranges = mutableListOf<IntRange>()
        val related = usedSegments.filter { segment ->
            segment.sourceMethod == DirectorOfficeSharedContract.SELECTOR &&
                (segment.sectionUid == item.section.uid ||
                    item.section.uid in segment.sourceSectionUids.split('\u001F'))
        }
        var legacySearchFrom = 0
        related.forEach { segment ->
            val start = segment.sourcePageStart
            val end = segment.sourcePageEnd
            if (start != null && end != null && start >= 0 && end > start && end <= item.text.length) {
                ranges += start until end
            } else {
                // سازگاری با نوبت‌های انتخاب‌گری که در نسخه‌های قبلی به‌صورت یک دیالوگ کامل ذخیره شده‌اند.
                val found = item.text.indexOf(segment.text, legacySearchFrom)
                if (found >= 0 && segment.text.isNotBlank()) {
                    ranges += found until (found + segment.text.length)
                    legacySearchFrom = found + segment.text.length
                }
            }
        }
        val merged = ranges.sortedBy { it.first }.fold(mutableListOf<IntRange>()) { acc, range ->
            val last = acc.lastOrNull()
            if (last == null || range.first > last.last + 1) acc += range
            else acc[acc.lastIndex] = last.first..maxOf(last.last, range.last)
            acc
        }
        val result = mutableListOf<DirectorSelectorRangeItem>()
        var cursor = 0
        merged.forEach { used ->
            if (cursor < used.first) {
                val endExclusive = used.first.coerceAtMost(item.text.length)
                if (endExclusive > cursor) result += DirectorSelectorRangeItem(item, cursor, endExclusive, item.text.substring(cursor, endExclusive))
            }
            cursor = maxOf(cursor, used.last + 1)
        }
        if (cursor < item.text.length) result += DirectorSelectorRangeItem(item, cursor, item.text.length, item.text.substring(cursor))
        result.asSequence().filter { it.text.isNotBlank() }
    }.toList()
}


private data class BuiltOfficeScreenData(
    val roles: List<RoleEntity>,
    val selectedRole: RoleEntity?,
    val selectorRole: RoleEntity?,
    val selectorTurnNumber: String,
    val selectorItems: List<DirectorSelectorItem>,
    val selectedDialogueKeys: Set<String>,
    val workspace: DirectorOfficeWorkspaceState,
    val generatedTurnCount: Int,
    val warnings: List<String> = emptyList()
)
internal suspend fun directorVersionHasText(dao: DirectorOfficeMainPageDao, versionId: Long): Boolean {
    val turnIds = dao.turns(versionId).map { it.id }
    return turnIds.isNotEmpty() && dao.textSegmentsForTurns(turnIds).any { it.text.isNotBlank() }
}

internal fun buildDirectorVersionPreview(workspace: DirectorOfficeWorkspaceState): String {
    return workspace.turns.joinToString("\n\n") { turn ->
        val roleTitles = workspace.participantsByTurn[turn.id].orEmpty().joinToString("، ") { it.roleTitle }
        val turnText = workspace.textSegmentsByTurn[turn.id].orEmpty().joinToString("\n") { it.text }
        buildString {
            append("نوبت ")
            append(turn.turnNumber)
            append(" — ")
            append(roleTitles.ifBlank { turn.title })
            append("\n")
            append(turnText)
        }
    }
}

/** نسخه سازگار برای نقاطی که فقط شناسه نسخه در اختیار دارند؛ روابط را گروهی می‌خواند. */
internal suspend fun buildDirectorVersionPreview(
    dao: DirectorOfficeMainPageDao,
    versionId: Long
): String {
    val turns = dao.turns(versionId)
    if (turns.isEmpty()) return ""
    val turnIds = turns.map { it.id }
    val participantsByTurn = dao.participantsForTurns(turnIds).groupBy { it.turnId }
    val segmentsByTurn = dao.textSegmentsForTurns(turnIds).groupBy { it.turnId }
    return turns.joinToString("\n\n") { turn ->
        val roleTitles = participantsByTurn[turn.id].orEmpty().joinToString("، ") { it.roleTitle }
        val turnText = segmentsByTurn[turn.id].orEmpty().joinToString("\n") { it.text }
        buildString {
            append("نوبت "); append(turn.turnNumber); append(" — ")
            append(roleTitles.ifBlank { turn.title }); append("\n"); append(turnText)
        }
    }
}

/**
 * نسخه بدون پرس‌وجوی دیتابیس از تطبیق دیالوگ‌های استفاده‌شده با Snapshot بارگذاری‌شده.
 * برای صفحه مشترک، این مسیر مانع خواندن مجدد turns/segments بعد از ساخت یا بازکردن دفتر می‌شود.
 */
internal fun usedDirectorDialogueKeys(
    workspace: DirectorOfficeWorkspaceState,
    items: List<DirectorSelectorItem>
): Set<String> {
    val used = mutableSetOf<String>()
    val segments = workspace.turns.flatMap { turn -> workspace.textSegmentsByTurn[turn.id].orEmpty() }
    for (segment in segments) {
        val sourceUids = (segment.sourceSectionUids.ifBlank { segment.sectionUid.orEmpty() })
            .split('\u001F').filter { it.isNotBlank() }.toSet()
        if (sourceUids.isEmpty()) continue
        sourceUids.forEach { sectionUid ->
            val match = items.firstOrNull { item ->
                item.key !in used && item.section.uid == sectionUid && item.text == segment.text
            }
            if (match != null) used += match.key
        }
    }
    return used
}

/**
 * Matches saved section-backed segments to source dialogue items. A shared collective
 * segment may point to several source sections, so one stored segment marks each matching
 * role-section entry as already used without changing the source text.
 */
internal suspend fun usedDirectorDialogueKeys(
    dao: DirectorOfficeMainPageDao,
    versionId: Long,
    items: List<DirectorSelectorItem>
): Set<String> {
    val used = mutableSetOf<String>()
    val turns = dao.turns(versionId)
    if (turns.isEmpty()) return used
    val segments = dao.textSegmentsForTurns(turns.map { it.id })
    for (segment in segments) {
        val sourceUids = (segment.sourceSectionUids.ifBlank { segment.sectionUid.orEmpty() })
            .split('\u001F').filter { it.isNotBlank() }.toSet()
        if (sourceUids.isEmpty()) continue
        sourceUids.forEach { sectionUid ->
            val match = items.firstOrNull { item ->
                item.key !in used && item.section.uid == sectionUid && item.text == segment.text
            }
            if (match != null) used += match.key
        }
    }
    return used
}


private enum class DirectorOfficeScreenStage {
    SAVED_OFFICES, CREATE_OFFICE, SELECTED_OFFICE
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DirectorOfficeSharedMainScreen(
    libraryRepository: DirectorOfficeLibraryRepository,
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
    val canViewPersonal = canAccess("director.personal.view")
    val canCreatePersonal = canAccess("director.personal.create")
    val canEditPersonal = canAccess("director.personal.edit")
    val canDeletePersonal = canAccess("director.personal.delete")
    val canImportSources = canAccess("director.source.import") &&
        (canCreatePersonal || canEditPersonal) && sourceImporter != null
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
    var screenStage by remember { mutableStateOf(DirectorOfficeScreenStage.SAVED_OFFICES) }
    var taziehOptions by remember { mutableStateOf(emptyList<TaziehEntity>()) }
    var fieldOptions by remember { mutableStateOf(emptyList<FieldEntity>()) }
    var selectedTazieh by remember { mutableStateOf<TaziehEntity?>(null) }
    var taziehMenuExpanded by remember { mutableStateOf(false) }
    var roleOptions by remember { mutableStateOf(emptyList<RoleEntity>()) }
    var buildSectionOptions by remember { mutableStateOf(emptyList<SectionEntity>()) }
    var selectorSectionPriorities by remember { mutableStateOf<Map<String, Int>>(emptyMap()) }
    var excludedBuildRoleUids by remember { mutableStateOf<Set<String>>(emptySet()) }
    var expandedPrioritySectionUid by remember { mutableStateOf<String?>(null) }
    var selectedRole by remember { mutableStateOf<RoleEntity?>(null) }
    var roleMenuExpanded by remember { mutableStateOf(false) }
    var deletePlanCandidate by remember { mutableStateOf<DirectorPlanEntity?>(null) }
    var showAddTurnDialog by remember { mutableStateOf(false) }
    var insertTurnMode by remember { mutableStateOf(false) }
    var deleteTurnCandidate by remember { mutableStateOf<DirectorTurnEntity?>(null) }
    var editingTurnCandidate by remember { mutableStateOf<DirectorTurnEntity?>(null) }
    var editingTurnRole by remember { mutableStateOf<RoleEntity?>(null) }
    var editingTurnNumber by remember { mutableStateOf("") }
    var editingTurnText by remember { mutableStateOf("") }
    var editingTurnRoleMenuExpanded by remember { mutableStateOf(false) }
    var turnNumber by remember { mutableStateOf("1") }
    var turnText by remember { mutableStateOf("") }
    var selectorRangeEditor by remember { mutableStateOf<DirectorSelectorRangeItem?>(null) }
    var selectorRangeTextValue by remember { mutableStateOf(TextFieldValue("")) }
    var selectorRangeSelection by remember { mutableStateOf<DirectorSelectorRangeSelection?>(null) }
    var refresh by remember { mutableIntStateOf(0) }
    var stageCues by remember { mutableStateOf(emptyList<DirectorStageCueEntity>()) }
    var showBuildModeDialog by remember { mutableStateOf(false) }
    var showVersionManagement by remember { mutableStateOf(false) }
    var showVersionsDialog by remember { mutableStateOf(false) }
    var selectedBuildMode by remember { mutableStateOf(DirectorOfficeSharedContract.ROLE_NUMBERED) }
    val canBuildSelectedMethod = canBuildMethod(selectedBuildMode)
    val canBuildAnyMethod = isAdmin || DirectorOfficeSharedContract.methodCodes.any { canBuildMethod(it) }
    val canShowVersionManagement = isAdmin || canBuildAnyMethod || canEditPersonal || canImportSources
    var selectorItems by remember { mutableStateOf(emptyList<DirectorSelectorItem>()) }
    var selectorRole by remember { mutableStateOf<RoleEntity?>(null) }
    var selectorRoleMenu by remember { mutableStateOf(false) }
    var selectorTurnNumber by remember { mutableStateOf("1") }
    var selectedDialogueKeys by remember { mutableStateOf<Set<String>>(emptySet()) }
    var turnSearch by remember { mutableStateOf("") }
    var showTurnSearchField by remember { mutableStateOf(false) }
    var selectedSearchResultIndex by remember { mutableIntStateOf(0) }
    var turnSearchCorpus by remember { mutableStateOf<Map<Long, String>>(emptyMap()) }
    var showQualityDialog by remember { mutableStateOf(false) }
    var qualityIssues by remember { mutableStateOf(emptyList<DirectorOfficeQualityIssue>()) }
    var qualityChecking by remember { mutableStateOf(false) }
    var historyDialogTurn by remember { mutableStateOf<DirectorTurnEntity?>(null) }
    var historyEntries by remember { mutableStateOf(emptyList<DirectorChangeHistoryEntity>()) }
    var historyRestoring by remember { mutableStateOf(false) }
    var showOfficeSettingsDialog by remember { mutableStateOf(false) }
    var officeLineSpacing by remember { mutableFloatStateOf(Prefs.getLineSpacing(context)) }
    val turnListState = rememberLazyListState()
    val officeTypography = remember(fontChoice, fontScale, officeLineSpacing) {
        val base = typographyFor(fontChoice, fontScale)
        base.copy(
            bodyLarge = base.bodyLarge.copy(lineHeight = base.bodyLarge.fontSize * officeLineSpacing.coerceIn(1.1f, 2.6f)),
            bodyMedium = base.bodyMedium.copy(lineHeight = base.bodyMedium.fontSize * officeLineSpacing.coerceIn(1.1f, 2.6f)),
            bodySmall = base.bodySmall.copy(lineHeight = base.bodySmall.fontSize * officeLineSpacing.coerceIn(1.1f, 2.6f))
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
            // Search is hosted in the shared top bar; the workspace contributes its title and optional global-cue row.
            val firstTurnItemIndex = (
                turnListState.layoutInfo.totalItemsCount - visibleTurns.size - trailingItems - sharedGlobalCueItem
            ).coerceAtLeast(0)
            val safeResultIndex = selectedSearchResultIndex.coerceIn(0, visibleTurns.lastIndex)
            runCatching { turnListState.animateScrollToItem(firstTurnItemIndex + safeResultIndex) }
        }
    }

    suspend fun loadPlans(reset: Boolean) {
        if (!canViewPersonal) {
            plans = emptyList()
            hasMorePlans = false
            selectedPlan = null
            selectedVersion = null
            planVersions = emptyList()
            workspaceState = null
            turns = emptyList()
            stageCues = emptyList()
            sources = emptyList()
            previewText = ""
            message = "مجوز مشاهده دفتر شخصی برای این Viewer فعال نیست."
            return
        }
        val offset = if (reset) 0 else plans.size
        val (loadedPage, canLoadMore) = withContext(Dispatchers.IO) {
            if (initialTaziehId != null) {
                val currentTazieh = libraryRepository.taziehById(initialTaziehId)
                val rows = currentTazieh?.let { dao.plansForTazieh(it.uid) }.orEmpty()
                    .filter { planSearch.isBlank() || it.title.contains(planSearch.trim(), ignoreCase = true) }
                rows to false
            } else {
                val rows = dao.plansPage(planSearch.trim(), 50, offset)
                rows to (rows.size == 50)
            }
        }
        plans = if (reset) loadedPage else plans + loadedPage
        hasMorePlans = canLoadMore
    }

    fun applyWorkspaceToScreen(loaded: DirectorOfficeWorkspaceState) {
        workspaceState = loaded
        selectedPlan = loaded.plan
        planVersions = loaded.versions
        selectedVersion = loaded.selectedVersion
        turns = loaded.turns
        stageCues = loaded.stageCues
        previewText = buildDirectorVersionPreview(loaded)
    }

    /** بارگذاری روابط دفتر با پرس‌وجوهای گروهی و خارج از رشته اصلی رابط کاربری. */
    suspend fun loadWorkspaceIntoScreen(planId: Long, preferredVersionId: Long? = selectedVersion?.id) {
        val loaded = withContext(Dispatchers.IO) {
            workspaceRepository.loadWorkspace(planId, preferredVersionId)
        }
        applyWorkspaceToScreen(loaded)
    }

    fun excludedRolesFromVersion(version: DirectorPlanVersionEntity?): Set<String> {
        val notes = version?.changeNotes.orEmpty()
        val marker = "نقش‌های کنارگذاشته‌شده:"
        if (!notes.contains(marker)) return emptySet()
        return notes.substringAfter(marker).substringBefore("|")
            .split(",").map { it.trim() }.filter { it.isNotBlank() }.toSet()
    }

    /** ساخت نسخه اول دفتر با روش انتخاب‌شده؛ منطق چهار روش در یک مسیر اجرا می‌شود. */
    suspend fun buildNewOfficeWithMethod(
        plan: DirectorPlanEntity,
        version: DirectorPlanVersionEntity,
        method: String,
        selectedSectionPriorities: Map<String, Int?> = emptyMap(),
        excludedRoleUids: Set<String> = emptySet()
    ): String {
        // خواندن کتابخانه، ساخت نوبت‌ها و تهیه Snapshot خارج از رشته اصلی رابط انجام می‌شود.
        val result = withContext(Dispatchers.IO) {
            val tazieh = libraryRepository.taziehByUid(plan.taziehUid)
                ?: throw IllegalStateException("مجلس مرتبط با این دفتر در کتابخانه پیدا نشد.")
            val allRoles = libraryRepository.rolesFor(tazieh)
            // انتخاب و کنارگذاری نقش‌ها در هر چهار روش ساخت اعمال می‌شود.
            val roles = allRoles.filterNot { it.uid in excludedRoleUids }
            require(roles.isNotEmpty()) { "حداقل یک نقش باید برای ورود به دفتر انتخاب شود." }
            val roleById = roles.associateBy { it.id }
            val allSections = libraryRepository.sectionsForRoles(allRoles.map { it.id })
                .filter { it.content.isNotBlank() }
            // اولویت‌بندی و کنارگذاری بخش‌ها نیز مستقل از روش ساخت است.
            // مقدار مثبت یعنی ورود به دفتر؛ مقدار منفی یعنی کنارگذاری؛ صفر/تهی یعنی تعیین‌تکلیف‌نشده.
            val selected = allSections.filter { section ->
                roleById.containsKey(section.roleId) && (selectedSectionPriorities[section.uid] ?: 0) > 0
            }.sortedWith(
                compareBy<SectionEntity> { section -> roleById[section.roleId]?.orderIndex ?: Int.MAX_VALUE }
                    .thenBy { section -> selectedSectionPriorities[section.uid] ?: Int.MAX_VALUE }
                    .thenBy { it.orderIndex }.thenBy { it.id }
            )
            val rolesWithoutSections = roles.filter { role -> selected.none { it.roleId == role.id } }
            require(rolesWithoutSections.isEmpty()) {
                "برای نقش‌های ${rolesWithoutSections.joinToString("، ") { it.title }} هیچ بخشی برای ورود انتخاب نشده است. برای کنار گذاشتن کامل، خود نقش را غیرفعال کنید."
            }
            val sections = selected
            val roleInputs = roles.map { DirectorOfficeBuildRole(uid = it.uid, title = it.title, orderIndex = it.orderIndex) }
            val roleUidById = roles.associate { it.id to it.uid }
            val sectionInputs = sections.mapNotNull { section ->
                val roleUid = roleUidById[section.roleId] ?: return@mapNotNull null
                DirectorOfficeBuildSection(uid = section.uid, roleUid = roleUid, content = section.content, orderIndex = section.orderIndex, title = section.title)
            }

            val buildResult = workspaceRepository.buildDraftFromLibrary(version.id, method, roleInputs, sectionInputs)
            val sectionRules = selectedSectionPriorities.entries.sortedBy { it.key }
                .joinToString(",") { (uid, priority) -> "$uid=${priority ?: 0}" }
            val excludedRoles = excludedRoleUids.sorted().joinToString(",")
            val buildConfigurationNote = "روش تکمیل دفتر: $method | اولویت‌بندی بخش‌ها: $sectionRules | نقش‌های کنارگذاشته‌شده: $excludedRoles"
            dao.updateVersion(version.copy(changeNotes = buildConfigurationNote))


            // متن بخش‌های اولویت‌دار برای انتخاب‌گر تکمیلی نگه داشته می‌شود؛
            // بخش‌های کنارگذاشته‌شده در این فهرست حضور ندارند.
            val selectorItems = if (method == DirectorOfficeSharedContract.SELECTOR) {
                sections.mapNotNull { section ->
                    val role = roleById[section.roleId] ?: return@mapNotNull null
                    DirectorSelectorItem(section.uid, section, role, section.content)
                }
            } else emptyList()
            // فقط یک Snapshot نهایی گرفته می‌شود و همان داده برای پیش‌نمایش و انتخاب‌گر استفاده می‌شود.
            val workspace = workspaceRepository.loadWorkspace(plan.id, version.id)
            val selectedKeys = if (method == DirectorOfficeSharedContract.SELECTOR) {
                usedDirectorDialogueKeys(workspace, selectorItems)
            } else emptySet()
            val nextTurnNumber = (workspace.turns.maxOfOrNull { it.turnNumber } ?: 0) + 1
            BuiltOfficeScreenData(
                roles = roles,
                selectedRole = roles.firstOrNull(),
                selectorRole = roles.firstOrNull(),
                selectorTurnNumber = nextTurnNumber.toString(),
                selectorItems = selectorItems,
                selectedDialogueKeys = selectedKeys,
                workspace = workspace,
                generatedTurnCount = buildResult.insertedTurnCount,
                warnings = buildResult.warnings
            )
        }

        selectedBuildMode = method
        roleOptions = result.roles
        selectedRole = result.selectedRole
        selectorRole = result.selectorRole
        selectorTurnNumber = result.selectorTurnNumber
        selectorItems = result.selectorItems
        selectedDialogueKeys = result.selectedDialogueKeys
        applyWorkspaceToScreen(result.workspace)
        val resultMessage = when (method) {
            DirectorOfficeSharedContract.ROLE_NUMBERED -> if (result.generatedTurnCount > 0)
                "${result.generatedTurnCount} نوبت شماره‌دار از متن نقش‌ها استخراج شد."
            else "شماره دیالوگی در متن شناسایی نشد؛ دفتر خالی ذخیره شد و می‌توانید نوبت‌ها را دستی اضافه کنید."
            DirectorOfficeSharedContract.FULL_TEXT -> if (result.generatedTurnCount > 0)
                "متن کامل ${result.generatedTurnCount} نقش به ترتیب وارد دفتر شد."
            else "برای نقش‌های این مجلس متن قابل انتقال پیدا نشد؛ دفتر خالی ذخیره شد و می‌توانید نوبت‌ها را بعداً اضافه کنید."
            DirectorOfficeSharedContract.MANUAL -> "دفتر خام با نقش‌ها و بخش‌های انتخاب‌شده ساخته شد؛ اکنون نوبت‌ها را به‌صورت دستی اضافه کنید."
            else -> if (result.generatedTurnCount > 0)
                "${result.generatedTurnCount} نوبت از بخش‌های انتخاب‌شده و بر اساس شماره‌های متن ساخته شد. بخش‌های بدون شماره همچنان از انتخاب‌گر دستی قابل تکمیل‌اند."
            else "دفتر و انتخاب‌های نقش‌ها و بخش‌ها ذخیره شد؛ بخش‌های انتخاب‌شده را می‌توانید با انتخاب‌گر به نوبت تبدیل کنید."
        }
        val completeMessage = if (result.warnings.isEmpty()) resultMessage
            else resultMessage + "\n" + result.warnings.joinToString("\n")
        message = completeMessage
        return completeMessage
    }


    fun reload() {
        scope.launch {
            loadPlans(reset = true)
            selectedPlan?.let { current ->
                val refreshedRoles = withContext(Dispatchers.IO) {
                    val targetTazieh = libraryRepository.taziehByUid(current.taziehUid)
                    targetTazieh?.let { libraryRepository.rolesFor(it) }.orEmpty()
                }
                sources = withContext(Dispatchers.IO) { dao.sourceFiles(current.id) }
                loadWorkspaceIntoScreen(current.id, selectedVersion?.id)
                val currentVersion = selectedVersion
                roleOptions = refreshedRoles.filterNot { it.uid in excludedRolesFromVersion(currentVersion) }
                selectedRole = selectedRole?.let { old -> roleOptions.firstOrNull { it.uid == old.uid } } ?: roleOptions.firstOrNull()
                selectedBuildMode = currentVersion?.changeNotes?.substringAfter("روش تکمیل دفتر: ", "")?.substringBefore("|")
                    ?.substringAfter("روش تکمیل دفتر: ", "")
                    ?.takeIf { it in DirectorOfficeSharedContract.methodCodes }
                    ?: DirectorOfficeSharedContract.ROLE_NUMBERED
            }
        }
    }
    LaunchedEffect(initialTaziehId) {
        try {
            val (loadedTaziehs, loadedFields) = withContext(Dispatchers.IO) {
                val rows = if (initialTaziehId == null) libraryRepository.allTaziehs()
                    else listOfNotNull(libraryRepository.taziehById(initialTaziehId))
                rows to libraryRepository.allFields()
            }
            taziehOptions = loadedTaziehs
            fieldOptions = loadedFields
        } catch (e: Exception) { message = "دریافت فهرست زمینه‌ها و تعزیه‌ها ناموفق بود: ${e.message}" }
    }
    LaunchedEffect(initialTaziehId, taziehOptions) {
        if (initialTaziehId != null && taziehOptions.isNotEmpty()) {
            val chosen = taziehOptions.firstOrNull { it.id == initialTaziehId }
            if (chosen != null) {
                selectedTazieh = chosen
                planTitle = chosen.title
                val loadedBuildSource = withContext(Dispatchers.IO) {
                    val loadedRoles = libraryRepository.rolesFor(chosen)
                    val loadedSections = libraryRepository.sectionsForRoles(loadedRoles.map { it.id })
                        .filter { it.content.isNotBlank() }
                        .sortedWith(compareBy<SectionEntity> { section -> loadedRoles.firstOrNull { it.id == section.roleId }?.orderIndex ?: Int.MAX_VALUE }
                            .thenBy { it.orderIndex }.thenBy { it.id })
                    loadedRoles to loadedSections
                }
                roleOptions = loadedBuildSource.first
                buildSectionOptions = loadedBuildSource.second
                selectorSectionPriorities = loadedBuildSource.second.groupBy { it.roleId }.values.flatMap { roleSections ->
                    roleSections.map { section -> section.uid to if (roleSections.size == 1) 1 else 0 }
                }.toMap()
                excludedBuildRoleUids = emptySet()
                selectedRole = roleOptions.firstOrNull()
                val existingPlan = if (canViewPersonal) {
                    withContext(Dispatchers.IO) { dao.plansForTazieh(chosen.uid).firstOrNull() }
                } else null
                screenStage = if (existingPlan == null) {
                    if (canCreatePersonal) DirectorOfficeScreenStage.CREATE_OFFICE else DirectorOfficeScreenStage.SAVED_OFFICES
                } else {
                    DirectorOfficeScreenStage.SAVED_OFFICES
                }
                if (existingPlan == null && !canCreatePersonal) {
                    message = "برای ساخت دفتر این مجلس، مجوز ساخت دفتر در سیاست دسترسی فعال نیست."
                }
            }
        }
    }
    LaunchedEffect(refresh) { if (refresh > 0) reload() }
    LaunchedEffect(planSearch, canViewPersonal) {
        delay(250)
        loadPlans(reset = true)
    }
    LaunchedEffect(selectedVersion?.id, selectedBuildMode, roleOptions, selectedPlan?.id, workspaceState?.selectedVersion?.id, selectorSectionPriorities) {
        val version = selectedVersion
        val plan = selectedPlan
        val roles = roleOptions
        if (version != null && plan != null && selectedBuildMode == DirectorOfficeSharedContract.SELECTOR && roles.isNotEmpty()) {
            try {
                val selectorData = withContext(Dispatchers.IO) {
                    val sections = libraryRepository.sectionsForRoles(roles.map { it.id })
                        .filter { it.content.isNotBlank() && (selectorSectionPriorities.isEmpty() || (selectorSectionPriorities[it.uid] ?: -1) > 0) }
                        .sortedWith(compareBy<SectionEntity> { section -> roles.firstOrNull { it.id == section.roleId }?.orderIndex ?: Int.MAX_VALUE }.thenBy { it.orderIndex }.thenBy { it.id })
                    val items = sections.mapNotNull { section ->
                        val role = roles.firstOrNull { it.id == section.roleId } ?: return@mapNotNull null
                        DirectorSelectorItem(section.uid, section, role, section.content)
                    }
                    val currentSnapshot = workspaceState?.takeIf { it.selectedVersion?.id == version.id }
                    val alreadyUsed = currentSnapshot?.let { usedDirectorDialogueKeys(it, items) }
                        ?: usedDirectorDialogueKeys(dao, version.id, items)
                    val nextNumber = (currentSnapshot?.turns?.maxOfOrNull { it.turnNumber }
                        ?: dao.turns(version.id).maxOfOrNull { it.turnNumber } ?: 0) + 1
                    Triple(items, alreadyUsed, nextNumber)
                }
                selectorItems = selectorData.first
                selectedDialogueKeys = selectorData.second
                selectorRole = selectorRole?.let { old -> roles.firstOrNull { it.uid == old.uid } } ?: roles.firstOrNull()
                selectorTurnNumber = selectorData.third.toString()
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
                    val targetTazieh = libraryRepository.taziehByUid(selectedPlan!!.taziehUid)
                    val targetTaziehTitle = targetTazieh?.title.orEmpty()
                    val targetFieldTitle = targetTazieh?.let { target ->
                        libraryRepository.fieldTitle(target.fieldId)
                    }.orEmpty()
                    val targetRoles = targetTazieh?.let { libraryRepository.rolesFor(it) }.orEmpty()
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
                        val linkedRolesByName = distinctRoles.associate { roleName ->
                            ContentUid.normalize(roleName) to libraryRepository.resolveRole(selectedPlan!!.taziehUid, roleName)
                        }
                        distinctRoles.forEachIndexed { index, roleName ->
                            val linkedRole = linkedRolesByName[ContentUid.normalize(roleName)]
                            dao.insertParticipant(DirectorParticipantEntity(
                                turnId = turnId,
                                roleUid = linkedRole?.uid ?: "unmatched-${ContentUid.sha256("${selectedPlan!!.taziehUid}|${ContentUid.normalize(roleName)}")}",
                                roleTitle = linkedRole?.title ?: roleName,
                                orderIndex = index,
                                isLead = index == 0
                            ))
                        }
                        entries.forEachIndexed { index, entry ->
                            val linkedRole = linkedRolesByName[ContentUid.normalize(entry.roleTitle)]
                            dao.insertTextSegment(DirectorTextSegmentEntity(
                                turnId = turnId, segmentIndex = index, text = entry.text, sourceFileId = entry.sourceId,
                                sourceTaziehUid = selectedPlan?.taziehUid,
                                sourceRoleUid = linkedRole?.uid,
                                sourceMethod = "SOURCE_FILE",
                                sourceContentHash = ContentUid.sha256(entry.text)
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
            IconButton(
                enabled = selectedVersion?.status == DirectorPlanVersionEntity.STATUS_DRAFT &&
                    (isAdmin || canCreatePersonal || canEditPersonal) && roleOptions.isNotEmpty() && !busy,
                onClick = {
                    selectedRole = selectedRole?.let { old -> roleOptions.firstOrNull { it.uid == old.uid } } ?: roleOptions.firstOrNull()
                    turnNumber = ((turns.maxOfOrNull { it.turnNumber } ?: 0) + 1).toString()
                    turnText = ""
                    selectorRangeEditor = null
                    selectorRangeSelection = null
                    selectorRangeTextValue = TextFieldValue("")
                    insertTurnMode = false
                    showAddTurnDialog = true
                }
            ) {
                Icon(Icons.Default.Add, contentDescription = "افزودن نوبت جدید")
            }
            IconButton(onClick = {
                if (showTurnSearchField) {
                    showTurnSearchField = false
                    turnSearch = ""
                    selectedSearchResultIndex = 0
                } else {
                    showTurnSearchField = true
                }
            }) {
                Icon(
                    if (showTurnSearchField) Icons.Default.Close else Icons.Default.Search,
                    contentDescription = if (showTurnSearchField) "بستن جستجو" else "جستجو در دفتر"
                )
            }
            IconButton(onClick = { refresh++ }) {
                Icon(Icons.Default.Refresh, contentDescription = "بروزرسانی")
            }
        },
        topBarContent = {
            if (showTurnSearchField) {
                Column(
                    modifier = Modifier.fillMaxWidth().padding(start = 12.dp, end = 12.dp, bottom = 8.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(4.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        DirectorOfficeSearchField(
                            value = turnSearch,
                            onValueChange = { turnSearch = it },
                            label = "جستجو در شماره، نقش و متن دیالوگ",
                            modifier = Modifier.weight(1f),
                            showSearchIcon = true
                        )
                        IconButton(onClick = {
                            showTurnSearchField = false
                            turnSearch = ""
                            selectedSearchResultIndex = 0
                        }) {
                            Icon(Icons.Default.Close, contentDescription = "بستن جستجو")
                        }
                    }
                    if (turnSearch.isNotBlank()) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(4.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                if (visibleTurns.isEmpty()) "۰ نتیجه" else "${toPersianOfficeDigits((selectedSearchResultIndex + 1).coerceAtMost(visibleTurns.size).toString())} از ${toPersianOfficeDigits(visibleTurns.size.toString())}",
                                modifier = Modifier.weight(1f),
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.secondary
                            )
                            TextButton(
                                enabled = visibleTurns.isNotEmpty() && selectedSearchResultIndex > 0,
                                onClick = { selectedSearchResultIndex = (selectedSearchResultIndex - 1).coerceAtLeast(0) }
                            ) { Text("نتیجه قبلی") }
                            TextButton(
                                enabled = visibleTurns.isNotEmpty() && selectedSearchResultIndex < visibleTurns.lastIndex,
                                onClick = { selectedSearchResultIndex = (selectedSearchResultIndex + 1).coerceAtMost(visibleTurns.lastIndex) }
                            ) { Text("نتیجه بعدی") }
                        }
                    }
                }
            }
        },
        listState = turnListState
    ) {
            when (screenStage) {
                DirectorOfficeScreenStage.CREATE_OFFICE -> {
                    item {
                        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                            TextButton(onClick = {
                                showVersionManagement = false
                                showVersionsDialog = false
                                screenStage = DirectorOfficeScreenStage.SAVED_OFFICES
                            }) {
                                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = null)
                                Spacer(Modifier.width(4.dp))
                                Text("بازگشت به دفاتر ثبت‌شده")
                            }
                        }
                    }
                    item {
                        Card {
                            Column(Modifier.fillMaxWidth().padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                                Text("ساخت دفتر معین‌البکاء", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                                Text("ابتدا مجلس را انتخاب کنید، سپس روش ایجاد دفتر را مشخص کنید.", style = MaterialTheme.typography.bodyMedium)
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
                                        taziehOptions.forEach { option ->
                                            DropdownMenuItem(
                                                text = { Text("${fieldOptions.firstOrNull { it.id == option.fieldId }?.title ?: "زمینه نامشخص"}  ←  ${option.title}") },
                                                onClick = {
                                                    selectedTazieh = option
                                                    if (planTitle.isBlank()) planTitle = option.title
                                                    roleOptions = emptyList()
                                                    buildSectionOptions = emptyList()
                                                    selectorSectionPriorities = emptyMap()
                                                    excludedBuildRoleUids = emptySet()
                                                    selectedRole = null
                                                    selectorRole = null
                                                    taziehMenuExpanded = false
                                                    scope.launch {
                                                        val loaded = withContext(Dispatchers.IO) {
                                                            val loadedRoles = libraryRepository.rolesFor(option)
                                                            val loadedSections = libraryRepository.sectionsForRoles(loadedRoles.map { it.id })
                                                                .filter { it.content.isNotBlank() }
                                                                .sortedWith(compareBy<SectionEntity> { section -> loadedRoles.firstOrNull { it.id == section.roleId }?.orderIndex ?: Int.MAX_VALUE }
                                                                    .thenBy { it.orderIndex }.thenBy { it.id })
                                                            loadedRoles to loadedSections
                                                        }
                                                        roleOptions = loaded.first
                                                        buildSectionOptions = loaded.second
                                                        selectorSectionPriorities = loaded.second.groupBy { it.roleId }.values.flatMap { roleSections ->
                                                            roleSections.mapIndexed { index, section -> section.uid to if (roleSections.size == 1) 1 else 0 }
                                                        }.toMap()
                                                        selectedRole = loaded.first.firstOrNull()
                                                        selectorRole = loaded.first.firstOrNull()
                                                    }
                                                }
                                            )
                                        }
                                    }
                                }
                                selectedTazieh?.let { target ->
                                    Text(
                                        "زمینه: ${fieldOptions.firstOrNull { it.id == target.fieldId }?.title ?: "نامشخص"} | مجلس: ${target.title}",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                                OutlinedTextField(
                                    value = planTitle,
                                    onValueChange = { planTitle = it },
                                    label = { Text("عنوان دفتر") },
                                    modifier = Modifier.fillMaxWidth(),
                                    singleLine = true,
                                    enabled = canCreatePersonal
                                )
                                Divider()
                                Text("روش ایجاد دفتر را انتخاب کنید", fontWeight = FontWeight.Bold)
                                listOf(
                                    DirectorOfficeSharedContract.ROLE_NUMBERED,
                                    DirectorOfficeSharedContract.MANUAL,
                                    DirectorOfficeSharedContract.FULL_TEXT,
                                    DirectorOfficeSharedContract.SELECTOR
                                ).forEachIndexed { index, methodCode ->
                                    val method = DirectorOfficeSharedContract.method(methodCode) ?: return@forEachIndexed
                                    val orderLabel = listOf("۱.", "۲.", "۳.", "۴.").getOrElse(index) { "${index + 1}." }
                                    val displayTitle = when (method.code) {
                                        DirectorOfficeSharedContract.ROLE_NUMBERED -> "تشخیص نوبت‌های شماره‌دار از متن"
                                        DirectorOfficeSharedContract.MANUAL -> "ساخت دستی نوبت‌ها"
                                        DirectorOfficeSharedContract.FULL_TEXT -> "ساخت از متن کامل"
                                        else -> "ساخت با انتخاب معین‌البکاء و اولویت‌بندی بخش‌ها"
                                    }
                                    Row(
                                        modifier = Modifier.fillMaxWidth().clickable(enabled = canBuildMethod(method.code)) { selectedBuildMode = method.code },
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                                    ) {
                                        RadioButton(
                                            selected = selectedBuildMode == method.code,
                                            onClick = { selectedBuildMode = method.code },
                                            enabled = canBuildMethod(method.code)
                                        )
                                        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                                            Text("$orderLabel $displayTitle", fontWeight = FontWeight.SemiBold)
                                            Text(method.description, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                        }
                                    }
                                }
                                Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer)) {
                                    Column(Modifier.fillMaxWidth().padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                        when (selectedBuildMode) {
                                            DirectorOfficeSharedContract.ROLE_NUMBERED -> {
                                                Text("تشخیص نوبت‌های شماره‌دار از متن", fontWeight = FontWeight.Bold)
                                                Text("برنامه فقط بخش‌های انتخاب‌شده از نقش‌های فعال همین مجلس را بررسی می‌کند و دیالوگ‌های دارای شماره را به نوبت تبدیل می‌کند. نوبت‌های هم‌شماره با متن یکسان به‌صورت جمعی ثبت می‌شوند؛ اگر متن‌ها متفاوت باشند، جداگانه ذخیره و برای اصلاح در دفتر هشدار داده می‌شوند.", style = MaterialTheme.typography.bodySmall)
                                            }
                                            DirectorOfficeSharedContract.FULL_TEXT -> {
                                                Text("ساخت از متن کامل", fontWeight = FontWeight.Bold)
                                                Text("متن کامل بخش‌های انتخاب‌شده از نقش‌های فعال همین مجلس، با رعایت اولویت هر نقش، در دفتر ثبت می‌شود.", style = MaterialTheme.typography.bodySmall)
                                            }
                                            DirectorOfficeSharedContract.MANUAL -> {
                                                Text("ساخت دفتر خام به روش دستی", fontWeight = FontWeight.Bold)
                                                Text("در این روش دفتر خام ساخته می‌شود؛ نقش‌ها و بخش‌های انتخاب‌شده به‌عنوان محدوده همین دفتر ثبت می‌شوند و نقش‌های کنارگذاشته‌شده در فهرست نقش‌های قابل استفاده نخواهند بود. نوبت‌ها را پس از ساخت به‌صورت دستی اضافه می‌کنید.", style = MaterialTheme.typography.bodySmall)
                                            }
                                            else -> {
                                                Text("ساخت با انتخاب معین‌البکاء و اولویت‌بندی بخش‌ها", fontWeight = FontWeight.Bold)
                                                Text("تمام نقش‌ها و بخش‌های واقعی مجلس نمایش داده می‌شوند. برای هر نقش، ترتیب بخش‌ها را تعیین کنید یا یک بخش را از دفتر کنار بگذارید. می‌توانید یک یا چند نقش را نیز به‌طور کامل خارج کنید. نوبت‌های شماره‌دار از بخش‌های انتخاب‌شده استخراج می‌شوند و متن اصلی کتابخانه تغییر نمی‌کند.", style = MaterialTheme.typography.bodySmall)
                                            }
                                        }
                                    }
                                }
                                if (selectedTazieh != null) {
                                    Divider()
                                    Text("تعیین وضعیت نقش‌ها و اولویت بخش‌ها", fontWeight = FontWeight.Bold)
                                    Text("این انتخاب‌ها در هر چهار روش ساخت دفتر اعمال می‌شوند.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.primary)
                                    Text("برای کنار گذاشتن کامل یک نقش، تیک کنار نام آن را بردارید. برای هر نقش فعال، تمام بخش‌ها باید اولویت بگیرند یا «وارد دفتر نشود» شوند.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                    roleOptions.forEach { role ->
                                        val roleSections = buildSectionOptions.filter { it.roleId == role.id }
                                        val roleExcluded = role.uid in excludedBuildRoleUids
                                        Card(
                                            colors = CardDefaults.cardColors(containerColor = if (roleExcluded) MaterialTheme.colorScheme.surfaceVariant else MaterialTheme.colorScheme.surface),
                                            modifier = Modifier.fillMaxWidth()
                                        ) {
                                            Column(Modifier.fillMaxWidth().padding(10.dp), verticalArrangement = Arrangement.spacedBy(7.dp)) {
                                                Row(verticalAlignment = Alignment.CenterVertically) {
                                                    Checkbox(
                                                        checked = !roleExcluded,
                                                        onCheckedChange = { checked ->
                                                            excludedBuildRoleUids = if (checked) excludedBuildRoleUids - role.uid else excludedBuildRoleUids + role.uid
                                                        }
                                                    )
                                                    Column(Modifier.weight(1f)) {
                                                        Text(role.title, fontWeight = FontWeight.SemiBold)
                                                        Text(if (roleExcluded) "این نقش به‌طور کامل وارد دفتر نمی‌شود" else "${roleSections.size} بخش شناسایی شده", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                                    }
                                                }
                                                if (!roleExcluded) {
                                                    roleSections.forEach { section ->
                                                        val currentPriority = selectorSectionPriorities[section.uid] ?: 0
                                                        val usedPriorities = roleSections.filter { it.uid != section.uid }
                                                            .mapNotNull { selectorSectionPriorities[it.uid] }
                                                            .filter { it > 0 }.toSet()
                                                        val menuOpen = expandedPrioritySectionUid == section.uid
                                                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                                            Column(Modifier.weight(1f)) {
                                                                Text(section.title.ifBlank { "بخش بدون عنوان" }, fontWeight = FontWeight.Medium)
                                                                Text("${section.content.length} نویسه", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                                            }
                                                            Box {
                                                                OutlinedButton(onClick = { expandedPrioritySectionUid = if (menuOpen) null else section.uid }) {
                                                                    Text(when {
                                                                        currentPriority == -1 -> "وارد دفتر نشود"
                                                                        currentPriority > 0 -> "اولویت $currentPriority"
                                                                        else -> "تعیین اولویت"
                                                                    })
                                                                }
                                                                DropdownMenu(expanded = menuOpen, onDismissRequest = { expandedPrioritySectionUid = null }) {
                                                                    (1..roleSections.size).filter { it !in usedPriorities || it == currentPriority }.forEach { priority ->
                                                                        DropdownMenuItem(
                                                                            text = { Text("اولویت $priority") },
                                                                            onClick = {
                                                                                selectorSectionPriorities = selectorSectionPriorities + (section.uid to priority)
                                                                                expandedPrioritySectionUid = null
                                                                            }
                                                                        )
                                                                    }
                                                                    DropdownMenuItem(
                                                                        text = { Text("وارد دفتر نشود") },
                                                                        onClick = {
                                                                            selectorSectionPriorities = selectorSectionPriorities + (section.uid to -1)
                                                                            expandedPrioritySectionUid = null
                                                                        }
                                                                    )
                                                                }
                                                            }
                                                        }
                                                    }
                                                }
                                            }
                                        }
                                    }
                                    val incompleteRoles = roleOptions.filter { role ->
                                        role.uid !in excludedBuildRoleUids && buildSectionOptions.filter { it.roleId == role.id }.let { roleSections ->
                                            roleSections.isEmpty() || roleSections.any { (selectorSectionPriorities[it.uid] ?: 0) == 0 } || roleSections.none { (selectorSectionPriorities[it.uid] ?: 0) > 0 }
                                        }
                                    }
                                    Text(
                                        if (incompleteRoles.isEmpty()) "همه نقش‌های انتخاب‌شده و بخش‌های آن‌ها تعیین تکلیف شده‌اند."
                                        else "نیازمند تکمیل: ${incompleteRoles.joinToString("، ") { it.title }}",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = if (incompleteRoles.isEmpty()) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error
                                    )
                                }
                                Button(
                                    modifier = Modifier.fillMaxWidth(),
                                    enabled = canCreatePersonal && canBuildMethod(selectedBuildMode) && selectedTazieh != null && planTitle.isNotBlank() && !busy && (roleOptions.isNotEmpty() && roleOptions.any { it.uid !in excludedBuildRoleUids } && roleOptions.filter { it.uid !in excludedBuildRoleUids }.all { role ->
                                        val rs = buildSectionOptions.filter { it.roleId == role.id }
                                        rs.isNotEmpty() && rs.all { (selectorSectionPriorities[it.uid] ?: 0) != 0 } && rs.any { (selectorSectionPriorities[it.uid] ?: 0) > 0 }
                                    }),
                                    onClick = {
                                        scope.launch {
                                            busy = true
                                            var createdPlanId: Long? = null
                                            try {
                                                // برای هر مجلس چند دفتر مستقل مجاز است. عنوان تکراری به‌صورت خودکار شماره می‌گیرد
                                                // تا دفتر دوم و سوم در فهرست به‌روشنی از یکدیگر قابل تشخیص باشند.
                                                val target = selectedTazieh ?: error("مجلس انتخاب نشده است.")
                                                val now = System.currentTimeMillis()
                                                val baseTitle = planTitle.trim().ifBlank { target.title }
                                                val existingTitles = withContext(Dispatchers.IO) {
                                                    dao.plansForTazieh(target.uid).filterNot { it.isDeleted }.map { it.title }
                                                }
                                                val finalTitle = DirectorOfficeWorkspaceUseCases.uniqueOfficeTitle(baseTitle, existingTitles)
                                                val planUid = UUID.randomUUID().toString()
                                                val (newPlanId, newVersionId) = transactionRunner.run {
                                                    val planId = dao.insertPlan(
                                                        DirectorPlanEntity(
                                                            uid = planUid,
                                                            taziehUid = target.uid,
                                                            title = finalTitle,
                                                            createdAt = now,
                                                            updatedAt = now
                                                        )
                                                    )
                                                    val versionId = dao.insertVersion(
                                                        DirectorPlanVersionEntity(
                                                            planId = planId, versionNumber = 1, title = "نسخه اول",
                                                            createdAt = now, changeNotes = "روش تکمیل دفتر: $selectedBuildMode"
                                                        )
                                                    )
                                                    planId to versionId
                                                }
                                                createdPlanId = newPlanId
                                                // از دو پرس‌وجوی اضافی plan()/version() استفاده نمی‌کنیم؛ شناسه‌ها
                                                // در همین تراکنش ساخته شده‌اند و مقادیر این دو شیء معلوم است.
                                                val newPlan = DirectorPlanEntity(
                                                    id = newPlanId, uid = planUid, taziehUid = target.uid, title = finalTitle,
                                                    createdAt = now, updatedAt = now
                                                )
                                                val newVersion = DirectorPlanVersionEntity(
                                                    id = newVersionId, planId = newPlanId, versionNumber = 1, title = "نسخه اول",
                                                    createdAt = now, changeNotes = "روش تکمیل دفتر: $selectedBuildMode"
                                                )
                                                val buildSummary = buildNewOfficeWithMethod(
                                                    plan = newPlan,
                                                    version = newVersion,
                                                    method = selectedBuildMode,
                                                    selectedSectionPriorities = selectorSectionPriorities.mapValues { (_, value) -> value.takeIf { it > 0 } },
                                                    excludedRoleUids = excludedBuildRoleUids
                                                )
                                                // buildNewOfficeWithMethod نقش‌ها و Snapshot نهایی را قبلاً در IO بارگذاری و اعمال کرده است؛
                                                // از خواندن دوباره همان داده‌ها و اجرای مجدد پرس‌وجوهای سنگین جلوگیری می‌کنیم.
                                                selectedPlan = newPlan
                                                selectedVersion = newVersion
                                                planVersions = listOf(newVersion)
                                                sources = emptyList()
                                                plans = listOf(newPlan) + plans.filterNot { it.id == newPlanId }
                                                showVersionManagement = false
                                                showVersionsDialog = false
                                                screenStage = DirectorOfficeScreenStage.SELECTED_OFFICE
                                                turnNumber = "1"
                                                turnText = ""
                                                message = "دفتر «${newPlan.title}» با روش انتخاب‌شده ساخته شد. $buildSummary"
                                            } catch (e: Exception) {
                                                createdPlanId?.let { runCatching { dao.softDeletePlan(it, System.currentTimeMillis()) } }
                                                message = "ساخت دفتر ناموفق بود: ${e.message ?: "خطای نامشخص"}"
                                            } finally { busy = false }
                                        }
                                    }
                                ) {
                                    if (busy) CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp)
                                    else Icon(Icons.Default.Add, contentDescription = null)
                                    Spacer(Modifier.width(8.dp))
                                    Text("ساخت دفتر و ذخیره")
                                }
                            }
                        }
                    }
                }
                DirectorOfficeScreenStage.SAVED_OFFICES -> {
                    item {
                        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            Text("دفتر معین‌البکاء", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                            Button(
                                modifier = Modifier.fillMaxWidth(),
                                enabled = canCreatePersonal && !busy,
                                onClick = {
                                    selectedTazieh = null
                                    planTitle = ""
                                    buildSectionOptions = emptyList()
                                    selectorSectionPriorities = emptyMap()
                                    excludedBuildRoleUids = emptySet()
                                    expandedPrioritySectionUid = null
                                    planSearch = ""
                                    selectedRole = null
                                    roleOptions = emptyList()
                                    turnNumber = "1"
                                    turnText = ""
                                    selectedBuildMode = DirectorOfficeSharedContract.ROLE_NUMBERED
                                    message = null
                                    screenStage = DirectorOfficeScreenStage.CREATE_OFFICE
                                }
                            ) {
                                Icon(Icons.Default.Add, contentDescription = null)
                                Spacer(Modifier.width(6.dp))
                                Text("افزودن دفتر جدید")
                            }
                            Text("دفترهای ثبت‌شده", fontWeight = FontWeight.SemiBold)
                        }
                    }
                    item {
                        OutlinedTextField(
                            value = planSearch, onValueChange = { planSearch = it },
                            modifier = Modifier.fillMaxWidth(),
                            label = { Text("جست‌وجوی دفتر بر اساس نام مجلس") }, singleLine = true
                        )
                    }
                    if (plans.isEmpty()) item {
                        Text(if (planSearch.isBlank()) "هنوز دفتری ثبت نشده است. برای شروع «افزودن دفتر جدید» را انتخاب کنید." else "دفتر منطبق با جست‌وجو پیدا نشد.", color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
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
                                    busy = true
                                    try {
                                        selectorSectionPriorities = emptyMap()
                                        excludedBuildRoleUids = emptySet()
                                        buildSectionOptions = emptyList()
                                        message = null
                                        val prepared = withContext(Dispatchers.IO) {
                                            coroutineScope {
                                                // خواندن مجلس، Snapshot دفتر و فایل‌های منبع مستقل‌اند؛ هم‌زمان اجرا شوند.
                                                val targetRequest = async { libraryRepository.taziehByUid(plan.taziehUid) }
                                                val workspaceRequest = async { workspaceRepository.loadWorkspace(plan.id) }
                                                val sourceRequest = async { if (isAdmin) dao.sourceFiles(plan.id) else emptyList() }
                                                val target = targetRequest.await()
                                                val rolesRequest = target?.let { selectedTazieh ->
                                                    async { libraryRepository.rolesFor(selectedTazieh) }
                                                }
                                                val roles = rolesRequest?.await().orEmpty()
                                                Triple(target, roles, workspaceRequest.await() to sourceRequest.await())
                                            }
                                        }
                                        val targetTazieh = prepared.first
                                        sources = prepared.third.second
                                        var loadedWorkspace = prepared.third.first
                                        val chosenVersion = loadedWorkspace.selectedVersion
                                        roleOptions = prepared.second.filterNot { it.uid in excludedRolesFromVersion(chosenVersion) }
                                        selectedRole = roleOptions.firstOrNull()
                                        selectedBuildMode = chosenVersion?.changeNotes?.substringAfter("روش تکمیل دفتر: ", "")?.substringBefore("|")
                                            ?.substringAfter("روش تکمیل دفتر: ", "")
                                            ?.takeIf { code -> code in DirectorOfficeSharedContract.methodCodes }
                                            ?: DirectorOfficeSharedContract.ROLE_NUMBERED

                                        // روش انتخاب معین‌البکاء با تنظیمات ذخیره‌شده ساخته می‌شود؛ از بازسازی خودکار متن‌های کنارگذاشته‌شده جلوگیری کن.
                                        val hasStoredDialogue = loadedWorkspace.textSegmentsByTurn.values.any { rows -> rows.any { it.text.isNotBlank() } }
                                        if (chosenVersion != null && selectedBuildMode !in setOf(DirectorOfficeSharedContract.SELECTOR, DirectorOfficeSharedContract.MANUAL) &&
                                            !chosenVersion.changeNotes.contains("اولویت‌بندی بخش‌ها:") &&
                                            (isAdmin || canCreatePersonal || canEditPersonal) && !hasStoredDialogue && targetTazieh != null) {
                                            val imported = withContext(Dispatchers.IO) {
                                                libraryRepository.importExistingText(dao, transactionRunner, chosenVersion.id, targetTazieh.id, selectedBuildMode)
                                            }
                                            if (imported.sectionCount > 0) {
                                                loadedWorkspace = withContext(Dispatchers.IO) {
                                                    workspaceRepository.loadWorkspace(plan.id, chosenVersion.id)
                                                }
                                                message = "${imported.sectionCount} بخش از متن‌های داخلی همین مجلس در ${imported.turnCount} نوبت به دفتر منتقل شد."
                                            }
                                        }
                                        applyWorkspaceToScreen(loadedWorkspace)
                                        showVersionManagement = false
                                        showVersionsDialog = false
                                        screenStage = DirectorOfficeScreenStage.SELECTED_OFFICE
                                    } catch (e: Exception) {
                                        message = "بازکردن دفتر ناموفق بود: ${e.message}"
                                    } finally { busy = false }
                                }
                            },
                            trailingContent = {
                                if (isAdmin || canDeletePersonal) IconButton(onClick = { deletePlanCandidate = plan }) {
                                    Icon(Icons.Default.Delete, contentDescription = "حذف دفتر")
                                }
                            }
                        )
                    }
                    if (hasMorePlans) item {
                        OutlinedButton(
                            enabled = !loadingMorePlans,
                            onClick = {
                                scope.launch {
                                    loadingMorePlans = true
                                    try { loadPlans(reset = false) } finally { loadingMorePlans = false }
                                }
                            },
                            modifier = Modifier.fillMaxWidth()
                        ) { Text(if (loadingMorePlans) "در حال بارگذاری…" else "نمایش ۵۰ دفتر بعدی") }
                    }
                }
                DirectorOfficeScreenStage.SELECTED_OFFICE -> {
                    item {
                        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            TextButton(onClick = {
                                showVersionManagement = false
                                showVersionsDialog = false
                                screenStage = DirectorOfficeScreenStage.SAVED_OFFICES
                            }) {
                                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = null)
                                Spacer(Modifier.width(4.dp))
                                Text("بازگشت به فهرست دفاتر")
                            }
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(selectedPlan?.title ?: "دفتر معین‌البکاء", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
                                if (isAdmin || canDeletePersonal) {
                                    TextButton(onClick = { selectedPlan?.let { deletePlanCandidate = it } }) {
                                        Icon(Icons.Default.Delete, contentDescription = null)
                                        Spacer(Modifier.width(4.dp))
                                        Text("حذف دفتر")
                                    }
                                }
                            }
                        }
                    }
                }
            }
            if (screenStage == DirectorOfficeScreenStage.SELECTED_OFFICE && selectedPlan != null) {
                item {
                    Card(Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer)) {
                        Column(Modifier.fillMaxWidth().padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                                Column(Modifier.weight(1f)) {
                                    Text("دفتر معین‌البکاء", fontWeight = FontWeight.Bold)
                                    Text(if (selectedVersion?.status == DirectorPlanVersionEntity.STATUS_PUBLISHED) "نسخه منتشرشده — فقط خواندنی" else "در حال تکمیل · پیش‌نویس", style = MaterialTheme.typography.bodySmall)
                                    val savedMethodCode = selectedVersion?.changeNotes
                                        ?.substringAfter("روش تکمیل دفتر: ", "")
                                        ?.substringBefore("|")
                                        ?.trim()
                                    val savedMethodLabel = savedMethodCode?.let { DirectorOfficeSharedContract.method(it)?.label }
                                    Text(
                                        "روش ساخت دفتر: ${savedMethodLabel ?: "روش ساخت ثبت نشده"}",
                                        style = MaterialTheme.typography.labelMedium,
                                        color = MaterialTheme.colorScheme.primary
                                    )
                                }
                                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                    Text("${turns.size} نوبت", fontWeight = FontWeight.SemiBold)
                                    if (isAdmin || canDeletePersonal) {
                                        TextButton(
                                            enabled = !busy,
                                            onClick = { selectedPlan?.let { deletePlanCandidate = it } }
                                        ) {
                                            Icon(Icons.Default.Delete, contentDescription = "حذف دفتر")
                                            Spacer(Modifier.width(4.dp))
                                            Text("حذف دفتر")
                                        }
                                    }
                                }
                            }
                            Text("فهرست نوبت‌ها", fontWeight = FontWeight.SemiBold)
                            OutlinedButton(
                                modifier = Modifier.fillMaxWidth(),
                                enabled = selectedVersion?.status == DirectorPlanVersionEntity.STATUS_DRAFT && !busy,
                                onClick = {
                                    scope.launch {
                                        qualityChecking = true
                                        try {
                                            val snapshot = workspaceState ?: error("اطلاعات دفتر برای کنترل بارگذاری نشده است.")
                                            val sourceIds = snapshot.textSegmentsByTurn.values.flatten()
                                                .flatMap { DirectorOfficeHistorySnapshots.sourceSectionIds(it) }.distinct()
                                            val qualityContext = withContext(Dispatchers.IO) {
                                                val sourceContent = sourceIds.associateWith { null as String? }.toMutableMap()
                                                sourceIds.chunked(400).forEach { batch ->
                                                    libraryRepository.sectionsByUids(batch).forEach { section -> sourceContent[section.uid] = section.content }
                                                }
                                                val tazieh = libraryRepository.taziehByUid(snapshot.plan.taziehUid)
                                                val sourceRoles = tazieh?.let { libraryRepository.rolesFor(it) }
                                                sourceContent.toMap() to (sourceRoles?.map { it.uid }?.toSet() to sourceRoles?.associate { it.uid to it.title })
                                            }
                                            qualityIssues = DirectorOfficeQualityChecker.check(
                                                workspace = snapshot,
                                                currentSourceSections = qualityContext.first,
                                                currentSourceRoleUids = qualityContext.second.first,
                                                expectedSourceRoles = qualityContext.second.second
                                            )
                                            showQualityDialog = true
                                            message = if (qualityIssues.isEmpty())  "کنترل‌های دفتر بدون مورد گزارش‌شده پایان یافت؛ اطلاعات فعلی از نظر قواعد بررسی‌شده معتبر است." else "کنترل پایان یافت؛ ${qualityIssues.size} مورد نیازمند بررسی در گزارش نمایش داده می‌شود."
                                        } catch (e: Exception) { message = "کنترل صحت دفتر انجام نشد: ${e.message}" }
                                        finally { qualityChecking = false }
                                    }
                                }
                            ) { Text("کنترل صحت دفتر / پایان تکمیل") }
                            OutlinedButton(
                                modifier = Modifier.fillMaxWidth(),
                                enabled = selectedPlan != null && selectedVersion?.status == DirectorPlanVersionEntity.STATUS_DRAFT && !busy,
                                onClick = {
                                    scope.launch {
                                        try {
                                            dao.updatePlan(selectedPlan!!.copy(updatedAt = System.currentTimeMillis()))
                                            loadWorkspaceIntoScreen(selectedPlan!!.id, selectedVersion!!.id)
                                            message = "تغییرات دفتر ذخیره شد. نوبت‌هایی که جداگانه ذخیره شده‌اند در دفتر ثبت هستند."
                                        } catch (e: Exception) { message = "ذخیره تغییرات ناموفق بود: ${e.message}" }
                                    }
                                }
                            ) { Text("ذخیره تغییرات") }
                        }
                    }
                }
                item {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Divider()
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                                Text("نسخه فعال", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                Text(
                                    selectedVersion?.let { version ->
                                        "نسخه ${version.versionNumber} • ${version.title} • ${if (version.status == DirectorPlanVersionEntity.STATUS_PUBLISHED) "منتشرشده" else "پیش‌نویس"}"
                                    } ?: "نسخه‌ای برای این دفتر ثبت نشده است",
                                    style = MaterialTheme.typography.bodyMedium,
                                    fontWeight = FontWeight.SemiBold
                                )
                            }
                            if (planVersions.size > 1) {
                                TextButton(onClick = { showVersionsDialog = true }) {
                                    Text("انتخاب نسخه (${planVersions.size})")
                                }
                            }
                        }
                        Text("نقش‌های ثبت‌شده در همین مجلس (${roleOptions.size})", fontWeight = FontWeight.SemiBold)
                        if (roleOptions.isEmpty()) Text(
                            "برای این مجلس هنوز نقشی در کتابخانه ثبت نشده است.",
                            color = MaterialTheme.colorScheme.error,
                            style = MaterialTheme.typography.bodySmall
                        ) else Text(roleOptions.joinToString("، ") { it.title }, style = MaterialTheme.typography.bodySmall)
                        Card(
                            Modifier.fillMaxWidth(),
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer)
                        ) {
                            Column(
                                Modifier.fillMaxWidth().heightIn(min = 96.dp, max = 220.dp)
                                    .verticalScroll(rememberScrollState()).padding(12.dp)
                            ) {
                                Text("متن کامل دفتر", fontWeight = FontWeight.Bold)
                                Spacer(Modifier.height(4.dp))
                                Text(
                                    previewText.ifBlank { "پس از تکمیل دفتر، متن تمام نقش‌ها به ترتیب نوبت در همین کادر نمایش داده می‌شود." },
                                    style = MaterialTheme.typography.bodyMedium
                                )
                            }
                        }
                    }
                }
                if (canShowVersionManagement) item {
                    Card(
                        Modifier.fillMaxWidth(),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
                    ) {
                        Column(Modifier.fillMaxWidth()) {
                            Row(
                                modifier = Modifier.fillMaxWidth().clickable { showVersionManagement = !showVersionManagement }.padding(12.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                                    Text("مدیریت نسخه و منابع", fontWeight = FontWeight.Bold)
                                    Text(
                                        "بازسازی روش ساخت، ایجاد نسخه و مدیریت فایل‌های منبع",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                                Text(if (showVersionManagement) "بستن" else "نمایش")
                            }
                            if (showVersionManagement) {
                                Divider()
                                Column(
                                    Modifier.fillMaxWidth().padding(12.dp),
                                    verticalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    if (selectedVersion != null && selectedVersion?.status != DirectorPlanVersionEntity.STATUS_PUBLISHED &&
                                        (isAdmin || canCreatePersonal || canEditPersonal) && canBuildAnyMethod
                                    ) {
                                        OutlinedButton(
                                            modifier = Modifier.fillMaxWidth(),
                                            enabled = !busy,
                                            onClick = {
                                                selectedBuildMode = selectedVersion?.changeNotes?.substringAfter("روش تکمیل دفتر: ", "")?.substringBefore("|")
                                                    ?.takeIf { it in DirectorOfficeSharedContract.methodCodes && canBuildMethod(it) }
                                                    ?: DirectorOfficeSharedContract.methodCodes.firstOrNull { canBuildMethod(it) }
                                                    ?: DirectorOfficeSharedContract.ROLE_NUMBERED
                                                showVersionManagement = true
                                                showBuildModeDialog = true
                                            }
                                        ) { Text("انتخاب روش ساخت / بازسازی این نسخه") }
                                    }
                                    if ((isAdmin || canEditPersonal) && selectedPlan != null && selectedVersion != null) {
                                        OutlinedButton(
                                            modifier = Modifier.fillMaxWidth(),
                                            enabled = !busy,
                                            onClick = {
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
                                                                changeNotes = sourceVersion.changeNotes.substringAfter("روش تکمیل دفتر: ", "").substringBefore("|")
                                                                    .takeIf { it in DirectorOfficeSharedContract.methodCodes }
                                                                    ?.let { "روش تکمیل دفتر: $it|رونوشت از نسخه ${sourceVersion.versionNumber}" }
                                                                    ?: "رونوشت از نسخه ${sourceVersion.versionNumber}", createdAt = now
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
                                            }
                                        ) { Text("ساخت نسخه جدید از نسخه انتخابی") }
                                    }
                                    if (turns.isEmpty() && selectedVersion != null && selectedPlan != null &&
                                        canBuildSelectedMethod && (isAdmin || canCreatePersonal || canEditPersonal)
                                    ) {
                                        OutlinedButton(
                                            modifier = Modifier.fillMaxWidth(),
                                            enabled = !busy,
                                            onClick = {
                                                scope.launch {
                                                    busy = true
                                                    try {
                                                        val plan = selectedPlan!!
                                                        val version = selectedVersion!!
                                                        val importResult = withContext(Dispatchers.IO) {
                                                            val tazieh = libraryRepository.taziehByUid(plan.taziehUid)
                                                                ?: return@withContext null
                                                            val result = libraryRepository.importExistingText(
                                                                dao, transactionRunner, version.id, tazieh.id, selectedBuildMode
                                                            )
                                                            val workspace = workspaceRepository.loadWorkspace(plan.id, version.id)
                                                            result to workspace
                                                        }
                                                        if (importResult == null) {
                                                            message = "مجلس مرتبط با این دفتر در کتابخانه پیدا نشد."
                                                        } else {
                                                            applyWorkspaceToScreen(importResult.second)
                                                            val result = importResult.first
                                                            message = if (result.sectionCount > 0) {
                                                                "${result.sectionCount} بخش از متن‌های داخلی ${result.roleCount} نقش در ${result.turnCount} نوبت به دفتر منتقل شد."
                                                            } else {
                                                                "دیالوگ شماره‌داری برای انتقال پیدا نشد؛ متن کامل نقش‌ها وارد نشد. شماره‌گذاری متن منبع را بررسی کنید."
                                                            }
                                                            refresh++
                                                        }
                                                    } catch (e: Exception) {
                                                        message = "انتقال متن‌های داخلی ناموفق بود: ${e.message}"
                                                    } finally { busy = false }
                                                }
                                            }
                                        ) { Text("تکمیل دفتر از متن‌های موجود برنامه") }
                                    }
                                    if (canImportSources) {
                                        Button(
                                            modifier = Modifier.fillMaxWidth(),
                                            onClick = { importLauncher.launch(arrayOf("text/plain", "application/json", "application/pdf", "application/vnd.openxmlformats-officedocument.wordprocessingml.document", "application/octet-stream", "image/*")) },
                                            enabled = !busy
                                        ) {
                                            Icon(Icons.Default.FileOpen, null); Spacer(Modifier.width(6.dp)); Text("ورود فایل کامل یا فایل‌های جداگانه نقش‌ها")
                                        }
                                    }
                                    if (sources.isNotEmpty()) {
                                        Divider()
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
                                    if (isAdmin || canCreatePersonal || canEditPersonal || canImportSources) {
                                        Divider()
                                        Text("پیش‌نمایش و متن ورودی", fontWeight = FontWeight.Bold)
                                        OutlinedTextField(
                                            value = previewText,
                                            onValueChange = { previewText = it },
                                            label = { Text("متن استخراج‌شده / متن قابل اصلاح") },
                                            modifier = Modifier.fillMaxWidth().heightIn(min = 120.dp, max = 220.dp),
                                            minLines = 3,
                                            enabled = isAdmin || canCreatePersonal || canEditPersonal || canImportSources
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
                if (showVersionManagement && selectedBuildMode == DirectorOfficeSharedContract.ROLE_NUMBERED) item {
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
                if (showVersionManagement && selectedBuildMode == DirectorOfficeSharedContract.SELECTOR && canBuildMethod(DirectorOfficeSharedContract.SELECTOR) && selectedVersion != null) {
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
                        val availableDialogues = availableDirectorSelectorRanges(workspaceState, selectorItems, selectorRole?.uid)
                        Text("بخش‌های باقی‌مانده متن: ${availableDialogues.size}", style = MaterialTheme.typography.labelMedium)
                        if (availableDialogues.isEmpty()) {
                            Text("متن انتخاب‌نشده‌ای برای این نقش باقی نمانده است.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        Column(Modifier.fillMaxWidth().heightIn(max = 340.dp).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            availableDialogues.forEach { range ->
                                Card(Modifier.fillMaxWidth().clickable(enabled = !busy) {
                                    selectedRole = range.source.role
                                    turnNumber = ((turns.maxOfOrNull { it.turnNumber } ?: 0) + 1).toString()
                                    turnText = ""
                                    selectorRangeEditor = range
                                    selectorRangeTextValue = TextFieldValue(range.text, selection = TextRange(0, 0))
                                    selectorRangeSelection = null
                                    insertTurnMode = false
                                    showAddTurnDialog = true
                                }) {
                                    Column(Modifier.fillMaxWidth().padding(10.dp)) {
                                        Text("${range.source.role.title} • ${range.source.section.title}", fontWeight = FontWeight.SemiBold)
                                        Text(range.text, style = MaterialTheme.typography.bodySmall)
                                        Text("انتخاب ابتدا و انتها و ثبت به‌عنوان نوبت", color = MaterialTheme.colorScheme.primary, style = MaterialTheme.typography.labelMedium)
                                    }
                                }
                            }
                        }
                    }
                }
                if (workspaceState != null) item(key = "director-office-quality-check") {
                    OutlinedButton(
                        modifier = Modifier.fillMaxWidth(),
                        enabled = !qualityChecking && !busy,
                        onClick = {
                            val snapshot = workspaceState ?: return@OutlinedButton
                            scope.launch {
                                qualityChecking = true
                                try {
                                    val sourceIds = snapshot.textSegmentsByTurn.values.flatten()
                                        .flatMap { DirectorOfficeHistorySnapshots.sourceSectionIds(it) }
                                        .distinct()
                                    val qualityContext = withContext(Dispatchers.IO) {
                                        val result = sourceIds.associateWith { null as String? }.toMutableMap()
                                        sourceIds.chunked(400).forEach { batch ->
                                            libraryRepository.sectionsByUids(batch).forEach { section ->
                                                result[section.uid] = section.content
                                            }
                                        }
                                        val tazieh = libraryRepository.taziehByUid(snapshot.plan.taziehUid)
                                        val sourceRoles = tazieh?.let { libraryRepository.rolesFor(it) }
                                        val roleUids = sourceRoles?.map(RoleEntity::uid)?.toSet()
                                        result.toMap() to (roleUids to sourceRoles?.associate { it.uid to it.title })
                                    }
                                    qualityIssues = DirectorOfficeQualityChecker.check(
                                        workspace = snapshot,
                                        currentSourceSections = qualityContext.first,
                                        currentSourceRoleUids = qualityContext.second.first,
                                        expectedSourceRoles = qualityContext.second.second
                                    )
                                    showQualityDialog = true
                                } catch (e: Exception) {
                                    message = "کنترل صحت دفتر انجام نشد: ${e.message}"
                                } finally {
                                    qualityChecking = false
                                }
                            }
                        }
                    ) {
                        if (qualityChecking) CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp)
                        else Icon(Icons.Default.Search, contentDescription = null)
                        Spacer(Modifier.width(8.dp))
                        Text("کنترل صحت و کامل‌بودن دفتر")
                    }
                }
                directorOfficeSharedWorkspaceItems(
                    workspace = workspaceState,
                    query = turnSearch,
                    onQueryChange = { turnSearch = it },
                    // فیلد جستجوی نوبت‌ها در نوار بالای صفحه و کنار تنظیمات باز می‌شود.
                    showSearchField = false,
                    searchLabel = "جستجو در شماره، نقش و متن دیالوگ",
                    listTitle = "نوبت‌های ثبت‌شده",
                    emptyMessage = "برای این نسخه هنوز نوبتی ثبت نشده است.",
                    lineSpacing = officeLineSpacing,
                    searchResultIndex = selectedSearchResultIndex,
                    onPreviousSearchResult = { selectedSearchResultIndex = (selectedSearchResultIndex - 1).coerceAtLeast(0) },
                    onNextSearchResult = { selectedSearchResultIndex = (selectedSearchResultIndex + 1).coerceAtMost((visibleTurns.size - 1).coerceAtLeast(0)) },
                    turnActions = { turn, turnPosition, totalTurns ->
                        val segmentsState = remember(turn.id) { mutableStateOf(emptyList<DirectorTextSegmentEntity>()) }
                        val participantsState = remember(turn.id) { mutableStateOf(emptyList<DirectorParticipantEntity>()) }
                        var showTurnCueDialog by remember(turn.id) { mutableStateOf(false) }
                        var turnCueDraft by remember(turn.id) { mutableStateOf("") }
                        var turnCueType by remember(turn.id) { mutableStateOf(DirectorStageCueEntity.CUE_OTHER) }
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
                            participantsState.value = workspaceState?.participantsByTurn?.get(turn.id)
                                ?: dao.participants(turn.id)
                        }

                        DirectorOfficeSharedTurnActions(
                            onShowHistory = {
                                val planId = selectedPlan?.id
                                if (planId != null) {
                                    scope.launch {
                                        try {
                                            historyEntries = workspaceRepository.changeHistory(planId, turn.id)
                                            historyDialogTurn = turn
                                        } catch (e: Exception) {
                                            message = "خواندن سابقه نوبت ناموفق بود: ${e.message}"
                                        }
                                    }
                                }
                            },
                            editable = workspaceState?.isReadOnly != true && (isAdmin || canEditPersonal),
                            onEditTurn = if (workspaceState?.isReadOnly != true && selectedVersion?.status == DirectorPlanVersionEntity.STATUS_DRAFT && (isAdmin || canEditPersonal)) ({
                                editingTurnCandidate = turn
                                editingTurnRole = participantsState.value.firstOrNull()?.let { participant ->
                                    roleOptions.firstOrNull { it.uid == participant.roleUid }
                                } ?: roleOptions.firstOrNull()
                                editingTurnNumber = turn.turnNumber.toString()
                                editingTurnText = segmentsState.value.sortedBy { it.segmentIndex }.joinToString("\n") { it.text }
                                editingTurnRoleMenuExpanded = false
                            }) else null,
                            participants = participantsState.value,
                            segments = segmentsState.value,
                            stageCues = turnStageCues,
                            collapseStageCues = true,
                            containerColor = turnPalette.containerColor,
                            contentColor = turnPalette.contentColor,
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
                            onDeleteTurn = if (workspaceState?.isReadOnly != true && selectedVersion?.status == DirectorPlanVersionEntity.STATUS_DRAFT && (isAdmin || canDeletePersonal)) {
                                ({ deleteTurnCandidate = turn })
                            } else null,
                            onEditCue = if (isAdmin || canEditPersonal) ({ cue ->
                                editingTurnCueId = cue.id
                                turnCueDraft = cue.instruction
                                turnCueType = cue.cueType
                                showTurnCueDialog = true
                            }) else null,
                            onDeleteCue = if (isAdmin || canDeletePersonal) ({ cue ->
                                scope.launch {
                                    try {
                                        workspaceRepository.deleteStageCue(cue.id)
                                        loadWorkspaceIntoScreen(selectedPlan?.id ?: error("دفتر انتخاب‌شده پیدا نشد."), turn.versionId)
                                        message = "راهنمای صحنه از این نوبت حذف شد؛ متن دیالوگ بدون تغییر ماند."
                                    } catch (e: Exception) {
                                        message = "حذف راهنمای صحنه انجام نشد: ${e.message}"
                                    }
                                }
                            }) else null,
                            onAddCue = if (isAdmin || canCreatePersonal || canEditPersonal) ({
                                editingTurnCueId = null
                                turnCueDraft = ""
                                turnCueType = DirectorStageCueEntity.CUE_OTHER
                                showTurnCueDialog = true
                            }) else null,
                            onEditSegment = if (isAdmin || canEditPersonal) ({ segment ->
                                editingSegmentItem = segment
                                editingSegmentDraft = segment.text
                            }) else null,
                            onDeleteSegment = if (isAdmin || canDeletePersonal) ({ segment -> deletingSegmentItem = segment }) else null,
                            extraContent = null,
                            afterCardContent = {
                                if (turn.notes.startsWith("هشدار:")) {
                                    Card(
                                        modifier = Modifier.fillMaxWidth(),
                                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.tertiaryContainer)
                                    ) {
                                        Text(
                                            turn.notes,
                                            modifier = Modifier.fillMaxWidth().padding(10.dp),
                                            color = MaterialTheme.colorScheme.onTertiaryContainer,
                                            style = MaterialTheme.typography.bodySmall
                                        )
                                    }
                                }
                                if (showTurnCueDialog) {
                                    DirectorOfficeStageCueEditorDialog(
                                        title = if (editingTurnCueId == null) "ثبت راهنمای صحنه — نوبت ${turn.turnNumber}" else "ویرایش راهنمای صحنه — نوبت ${turn.turnNumber}",
                                        instruction = turnCueDraft,
                                        onInstructionChange = { turnCueDraft = it },
                                        cueType = turnCueType,
                                        onCueTypeChange = { turnCueType = it },
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
                                                    val isImmediate = turnCueType == DirectorStageCueEntity.CUE_IMMEDIATE
                                                    val continuationRoleUid = participantsState.value.firstOrNull()?.roleUid.orEmpty()
                                                    val cueTitle = if (isImmediate) "بلافاصله" else "راهنمای صحنه"
                                                    if (existingCue != null) {
                                                        workspaceRepository.updateStageCue(existingCue.copy(
                                                            title = cueTitle,
                                                            instruction = turnCueDraft.trim(),
                                                            cueType = turnCueType,
                                                            blocksNextTurn = isImmediate,
                                                            continuationRoleUid = if (isImmediate) continuationRoleUid else ""
                                                        ))
                                                        message = "راهنمای صحنه این نوبت ویرایش شد."
                                                    } else {
                                                        workspaceRepository.addStageCue(
                                                            planId = selectedPlan!!.id,
                                                            title = cueTitle,
                                                            instruction = turnCueDraft.trim(),
                                                            turnId = turn.id,
                                                            cueType = turnCueType,
                                                            blocksNextTurn = isImmediate,
                                                            continuationRoleUid = if (isImmediate) continuationRoleUid else ""
                                                        )
                                                        message = if (isImmediate) "راهنمای بلافاصله ثبت شد و عبور به نوبت بعدی را متوقف می‌کند."
                                                            else "راهنمای صحنه به نوبت ${turn.turnNumber} متصل شد."
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
                item(key = "director-office-add-turn-bottom") {
                    Button(
                        modifier = Modifier.fillMaxWidth(),
                        enabled = selectedVersion?.status == DirectorPlanVersionEntity.STATUS_DRAFT &&
                            (isAdmin || canCreatePersonal || canEditPersonal) && roleOptions.isNotEmpty() && !busy,
                        onClick = {
                            selectedRole = selectedRole?.let { old -> roleOptions.firstOrNull { it.uid == old.uid } } ?: roleOptions.firstOrNull()
                            turnNumber = ((turns.maxOfOrNull { it.turnNumber } ?: 0) + 1).toString()
                            turnText = ""
                            selectorRangeEditor = null
                            selectorRangeSelection = null
                            selectorRangeTextValue = TextFieldValue("")
                            insertTurnMode = false
                            showAddTurnDialog = true
                        }
                    ) {
                        Icon(Icons.Default.Add, contentDescription = null)
                        Spacer(Modifier.width(6.dp))
                        Text("افزودن نوبت جدید")
                    }
                    Text(
                        "نوبت جدید به انتهای فهرست افزوده می‌شود.",
                        modifier = Modifier.fillMaxWidth(),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
            if (busy) item { LinearProgressIndicator(Modifier.fillMaxWidth()) }
            message?.let { msg -> item { Text(msg, color = MaterialTheme.colorScheme.primary) } }
    }
    if (showVersionsDialog) {
        AlertDialog(
            onDismissRequest = { showVersionsDialog = false },
            title = { Text("انتخاب نسخه دفتر") },
            text = {
                Column(
                    modifier = Modifier
                        .heightIn(max = (LocalConfiguration.current.screenHeightDp * 0.62f).dp)
                        .verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    planVersions.sortedByDescending { it.versionNumber }.forEach { version ->
                        val isSelected = selectedVersion?.id == version.id
                        OutlinedButton(
                            modifier = Modifier.fillMaxWidth(),
                            onClick = {
                                showVersionsDialog = false
                                showVersionManagement = false
                                selectedVersion = version
                                version.changeNotes.substringAfter("روش تکمیل دفتر: ", "").substringBefore("|")
                                    .takeIf { it in DirectorOfficeSharedContract.methodCodes }
                                    ?.let { selectedBuildMode = it }
                                scope.launch { loadWorkspaceIntoScreen(selectedPlan!!.id, version.id) }
                            }
                        ) {
                            Column(Modifier.fillMaxWidth()) {
                                Text(
                                    "نسخه ${version.versionNumber}: ${version.title}",
                                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal
                                )
                                Text(
                                    if (version.status == DirectorPlanVersionEntity.STATUS_PUBLISHED) "منتشرشده" else "پیش‌نویس",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }
                }
            },
            confirmButton = { TextButton(onClick = { showVersionsDialog = false }) { Text("بستن") } }
        )
    }
    if (showBuildModeDialog) {
        AlertDialog(
            onDismissRequest = { showBuildModeDialog = false },
            title = { Text("روش تکمیل دفتر معین‌البکا") },
            text = {
                Column(
                    modifier = Modifier
                        .heightIn(max = (LocalConfiguration.current.screenHeightDp * 0.62f).dp)
                        .verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
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
                            val tazieh = libraryRepository.taziehByUid(plan.taziehUid)
                            if (tazieh == null) throw IllegalStateException("مجلس مرتبط پیدا نشد")
                            val roles = libraryRepository.rolesFor(tazieh)
                            val sections = libraryRepository.sectionsForRoles(roles.map { it.id })
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
                                    orderIndex = section.orderIndex,
                                    title = section.title
                                )
                            }
                            val buildResult = transactionRunner.run {
                                val built = DaoDirectorOfficeWorkspaceRepository(dao).buildDraftFromLibrary(
                                    versionId = version.id,
                                    method = selectedBuildMode,
                                    roles = roleInputs,
                                    sections = sectionInputs
                                )
                                dao.updateVersion(version.copy(changeNotes = "روش تکمیل دفتر: $selectedBuildMode"))
                                built
                            }
                            loadWorkspaceIntoScreen(plan.id, version.id)
                            selectedRole = roles.firstOrNull()
                            selectorRole = roles.firstOrNull()
                            selectorTurnNumber = ((turns.maxOfOrNull { it.turnNumber } ?: 0) + 1).toString()
                            selectedDialogueKeys = emptySet()
                            selectorItems = sections.mapNotNull { section ->
                                val role = roles.firstOrNull { it.id == section.roleId } ?: return@mapNotNull null
                                DirectorSelectorItem(section.uid, section, role, section.content)
                            }
                            if (selectedBuildMode == DirectorOfficeSharedContract.SELECTOR) {
                                selectedDialogueKeys = usedDirectorDialogueKeys(dao, version.id, selectorItems)
                            }
                            val buildMessage = when (selectedBuildMode) {
                                DirectorOfficeSharedContract.ROLE_NUMBERED -> "نوبت‌ها بر اساس شماره‌های داخل متن نقش‌ها ساخته شدند؛ ${turns.size} شماره دیالوگ شناسایی شد و متن همان دیالوگ‌ها ثبت شد."
                                DirectorOfficeSharedContract.FULL_TEXT -> "متن کامل ${turns.size} نقش به‌ترتیب در دفتر قرار گرفت."
                                DirectorOfficeSharedContract.MANUAL -> "حالت دستی فعال شد؛ متن اصلی مجلس تغییری نکرد."
                                else -> "حالت انتخاب با معین‌البکا فعال شد؛ دیالوگ‌های انتخاب‌شده دیگر در فهرست باقی‌مانده نمایش داده نمی‌شوند."
                            }
                            message = if (buildResult.warnings.isEmpty()) buildMessage
                                else buildMessage + "\n" + buildResult.warnings.joinToString("\n")
                            showBuildModeDialog = false
                            showVersionManagement = true
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
            onResetSettings = {
                onDarkModeChange(false)
                onAutoDarkModeChange(false)
                onFontScaleChange(1.0f)
                officeLineSpacing = 1.4f
                Prefs.setLineSpacing(context, 1.4f)
                onFontChoiceChange("titr")
                onThemeChoiceChange("default")
                onKeepScreenOnChange(true)
            },
            onDismiss = { showOfficeSettingsDialog = false }
        )
    }
    if (showQualityDialog) {
        DirectorOfficeQualityDialog(
            issues = qualityIssues,
            onDismiss = { showQualityDialog = false },
            onSelectIssue = { issue ->
                showQualityDialog = false
                issue.turnNumber?.let { turnSearch = it.toString(); showTurnSearchField = true }
            }
        )
    }
    historyDialogTurn?.let { historyTurn ->
        DirectorOfficeChangeHistoryDialog(
            turn = historyTurn,
            entries = historyEntries,
            canRestore = (isAdmin || canEditPersonal) && workspaceState?.isReadOnly != true,
            restoring = historyRestoring,
            onDismiss = { if (!historyRestoring) historyDialogTurn = null },
            onRestore = { historyEntry ->
                val planId = selectedPlan?.id
                if (planId != null) scope.launch {
                    historyRestoring = true
                    try {
                        workspaceRepository.restoreChangeHistory(historyEntry.id)
                        loadWorkspaceIntoScreen(planId, historyTurn.versionId)
                        historyEntries = workspaceRepository.changeHistory(planId, historyTurn.id)
                        message = "وضعیت قبلی نوبت بازگردانی شد و این بازگردانی نیز در سابقه ثبت شد."
                    } catch (e: Exception) {
                        message = "بازگردانی سابقه انجام نشد: ${e.message}"
                    } finally {
                        historyRestoring = false
                    }
                }
            }
        )
    }
    editingTurnCandidate?.let { candidate ->
        AlertDialog(
            onDismissRequest = { if (!busy) editingTurnCandidate = null },
            title = { Text("ویرایش نوبت ${candidate.turnNumber}") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    ExposedDropdownMenuBox(
                        expanded = editingTurnRoleMenuExpanded,
                        onExpandedChange = { editingTurnRoleMenuExpanded = !editingTurnRoleMenuExpanded }
                    ) {
                        OutlinedTextField(
                            value = editingTurnRole?.title.orEmpty(),
                            onValueChange = {},
                            readOnly = true,
                            label = { Text("نقش نوبت") },
                            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(editingTurnRoleMenuExpanded) },
                            modifier = Modifier.menuAnchor().fillMaxWidth(),
                            enabled = roleOptions.isNotEmpty()
                        )
                        ExposedDropdownMenu(
                            expanded = editingTurnRoleMenuExpanded,
                            onDismissRequest = { editingTurnRoleMenuExpanded = false }
                        ) {
                            roleOptions.forEach { role ->
                                DropdownMenuItem(
                                    text = { Text(role.title) },
                                    onClick = { editingTurnRole = role; editingTurnRoleMenuExpanded = false }
                                )
                            }
                        }
                    }
                    OutlinedTextField(
                        value = editingTurnNumber,
                        onValueChange = { editingTurnNumber = it.filter(Char::isDigit).take(6) },
                        label = { Text("شماره نوبت") },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true,
                        keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(
                            keyboardType = androidx.compose.ui.text.input.KeyboardType.Number
                        )
                    )
                    DirectorOfficeDialogueTextField(
                        value = editingTurnText,
                        onValueChange = { editingTurnText = it },
                        label = "متن نوبت",
                        minHeight = 150,
                        maxHeight = 280
                    )
                }
            },
            confirmButton = {
                TextButton(
                    enabled = !busy && editingTurnRole != null && (editingTurnNumber.toIntOrNull() ?: 0) > 0 && editingTurnText.isNotBlank(),
                    onClick = {
                        scope.launch {
                            busy = true
                            try {
                                val role = editingTurnRole ?: error("نقش نوبت انتخاب نشده است.")
                                val number = editingTurnNumber.toIntOrNull() ?: error("شماره نوبت معتبر نیست.")
                                val participant = dao.participants(candidate.id).firstOrNull()
                                if (participant == null) {
                                    workspaceRepository.addParticipant(candidate.id, role.uid, role.title, isLead = true)
                                } else {
                                    workspaceRepository.updateParticipant(participant.copy(roleUid = role.uid, roleTitle = role.title))
                                }
                                workspaceRepository.updateTurn(candidate.copy(turnNumber = number, title = role.title))
                                workspaceRepository.replaceTurnText(candidate.id, editingTurnText.trim())
                                loadWorkspaceIntoScreen(selectedPlan?.id ?: error("دفتر انتخاب‌شده پیدا نشد."), candidate.versionId)
                                editingTurnCandidate = null
                                message = "شماره، نقش و متن نوبت با هم ذخیره شدند؛ عنوان نوبت نیز با نقش جدید هماهنگ شد."
                            } catch (e: Exception) {
                                message = "ویرایش نوبت انجام نشد: ${e.message}"
                            } finally { busy = false }
                        }
                    }
                ) { Text(if (busy) "در حال ذخیره…" else "ذخیره تغییرات") }
            },
            dismissButton = {
                TextButton(enabled = !busy, onClick = { editingTurnCandidate = null }) { Text("انصراف") }
            }
        )
    }
    if (showAddTurnDialog) {
        AlertDialog(
            onDismissRequest = { if (!busy) showAddTurnDialog = false },
            title = { Text(if (insertTurnMode) "درج نوبت در میان نوبت‌ها" else "افزودن نوبت به ${selectedPlan?.title.orEmpty()}") },
            text = {
                Column(
                    modifier = Modifier.heightIn(max = 560.dp).verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Surface(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable(enabled = !busy) {
                                val nextMode = !insertTurnMode
                                insertTurnMode = nextMode
                                if (!nextMode) {
                                    turnNumber = ((turns.maxOfOrNull { it.turnNumber } ?: 0) + 1).toString()
                                }
                            },
                        shape = MaterialTheme.shapes.medium,
                        color = if (insertTurnMode) MaterialTheme.colorScheme.tertiaryContainer
                            else MaterialTheme.colorScheme.secondaryContainer,
                        contentColor = if (insertTurnMode) MaterialTheme.colorScheme.onTertiaryContainer
                            else MaterialTheme.colorScheme.onSecondaryContainer,
                        tonalElevation = 2.dp
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 13.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            Icon(
                                if (insertTurnMode) Icons.Default.PlayArrow else Icons.Default.Add,
                                contentDescription = null
                            )
                            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                                Text(
                                    if (insertTurnMode) "درج میان نوبت‌ها فعال است"
                                    else "درج نوبت در میان نوبت‌های ثبت‌شده",
                                    fontWeight = FontWeight.Bold
                                )
                                Text(
                                    if (insertTurnMode) "برای بازگشت به افزودن در انتهای دفتر، اینجا را لمس کنید."
                                    else "برای افزودن نوبت در شماره دلخواه و جابه‌جایی خودکار نوبت‌های بعدی، اینجا را لمس کنید.",
                                    style = MaterialTheme.typography.bodySmall
                                )
                            }
                            Switch(checked = insertTurnMode, onCheckedChange = null, enabled = !busy)
                        }
                    }
                    if (insertTurnMode) {
                        Text(
                            "حالت درج فعال است. شماره مقصد را وارد کنید؛ حتی اگر آن شماره قبلاً وجود داشته باشد، نوبت جدید در همان جایگاه قرار می‌گیرد و همه نوبت‌های آن شماره و شماره‌های بعدی یک واحد جابه‌جا می‌شوند.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    ExposedDropdownMenuBox(expanded = roleMenuExpanded, onExpandedChange = { roleMenuExpanded = !roleMenuExpanded }) {
                        OutlinedTextField(
                            value = selectedRole?.title ?: "انتخاب نقش",
                            onValueChange = {}, readOnly = true,
                            label = { Text("نقش نوبت") },
                            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(roleMenuExpanded) },
                            modifier = Modifier.menuAnchor().fillMaxWidth(),
                            enabled = roleOptions.isNotEmpty()
                        )
                        ExposedDropdownMenu(expanded = roleMenuExpanded, onDismissRequest = { roleMenuExpanded = false }) {
                            roleOptions.forEach { role ->
                                DropdownMenuItem(text = { Text(role.title) }, onClick = {
                                    selectedRole = role
                                    roleMenuExpanded = false
                                    selectorRangeEditor = null
                                    selectorRangeSelection = null
                                    selectorRangeTextValue = TextFieldValue("")
                                    turnText = ""
                                    if (!insertTurnMode) {
                                        turnNumber = ((turns.maxOfOrNull { it.turnNumber } ?: 0) + 1).toString()
                                    }
                                })
                            }
                        }
                    }
                    if (selectedBuildMode == DirectorOfficeSharedContract.SELECTOR) {
                        OutlinedTextField(
                            value = turnNumber,
                            onValueChange = { turnNumber = it.filter(Char::isDigit).take(6) },
                            label = { Text(if (insertTurnMode) "شماره محل درج" else "شماره نوبت (پیشنهادی و قابل ویرایش)") },
                            modifier = Modifier.fillMaxWidth(), singleLine = true
                        )
                        Text("متن‌های باقی‌مانده نقش ${selectedRole?.title.orEmpty()}", fontWeight = FontWeight.SemiBold)
                        Text("یک بخش را باز کنید، سپس ابتدا و انتهای قسمت موردنظر را از متن انتخاب کنید. قسمت‌های ثبت‌شده دیگر در انتخاب‌های بعدی نمایش داده نمی‌شوند.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        val availableRanges = availableDirectorSelectorRanges(workspaceState, selectorItems, selectedRole?.uid)
                        if (availableRanges.isEmpty()) {
                            Text("متن انتخاب‌نشده‌ای برای این نقش باقی نمانده است.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        } else {
                            Column(Modifier.fillMaxWidth().heightIn(max = 190.dp).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                availableRanges.forEachIndexed { index, range ->
                                    Card(
                                        modifier = Modifier.fillMaxWidth().clickable(enabled = !busy) {
                                            selectorRangeEditor = range
                                            selectorRangeTextValue = TextFieldValue(range.text, selection = TextRange(0, 0))
                                            selectorRangeSelection = null
                                        },
                                        colors = CardDefaults.cardColors(containerColor = if (selectorRangeEditor == range) MaterialTheme.colorScheme.secondaryContainer else MaterialTheme.colorScheme.surfaceVariant)
                                    ) {
                                        Column(Modifier.fillMaxWidth().padding(10.dp)) {
                                            Text("بخش ${index + 1} • ${range.source.section.title}", fontWeight = FontWeight.SemiBold)
                                            Text(range.text, style = MaterialTheme.typography.bodySmall, maxLines = 4)
                                            Text("برای انتخاب ابتدا و انتها لمس کنید", color = MaterialTheme.colorScheme.primary, style = MaterialTheme.typography.labelSmall)
                                        }
                                    }
                                }
                            }
                        }
                        selectorRangeEditor?.let { range ->
                            Text("متن منبع؛ برای انتخاب، روی متن نگه دارید و ابتدا و انتها را مشخص کنید:", style = MaterialTheme.typography.labelMedium)
                            OutlinedTextField(
                                value = selectorRangeTextValue,
                                onValueChange = { selectorRangeTextValue = it },
                                readOnly = true,
                                modifier = Modifier.fillMaxWidth().heightIn(min = 150.dp, max = 250.dp),
                                textStyle = MaterialTheme.typography.bodyMedium,
                                label = { Text(range.source.section.title) }
                            )
                            OutlinedButton(
                                modifier = Modifier.fillMaxWidth(),
                                enabled = selectorRangeTextValue.selection.min < selectorRangeTextValue.selection.max,
                                onClick = {
                                    val rawStart = selectorRangeTextValue.selection.min.coerceIn(0, range.text.length)
                                    val rawEnd = selectorRangeTextValue.selection.max.coerceIn(0, range.text.length)
                                    var start = rawStart
                                    var end = rawEnd
                                    while (start < end && range.text[start].isWhitespace()) start++
                                    while (end > start && range.text[end - 1].isWhitespace()) end--
                                    if (end > start) {
                                        turnText = range.text.substring(start, end)
                                        selectorRangeSelection = DirectorSelectorRangeSelection(
                                            source = range.source,
                                            start = range.start + start,
                                            end = range.start + end
                                        )
                                    }
                                }
                            ) { Text("ثبت قسمت انتخاب‌شده به‌عنوان متن نوبت") }
                        }
                        DirectorOfficeDialogueTextField(
                            value = turnText,
                            onValueChange = { turnText = it },
                            label = "متن نوبت انتخاب‌شده (قابل ویرایش)",
                            minHeight = 110,
                            maxHeight = 220
                        )
                    } else {
                        DirectorOfficeTurnInputFields(
                            number = turnNumber,
                            onNumberChange = { turnNumber = it },
                            text = turnText,
                            onTextChange = { turnText = it },
                            textLabel = "متن نوبت",
                            textMinHeight = 130,
                            textMaxHeight = 240
                        )
                    }
                }
            },
            confirmButton = {
                TextButton(
                    enabled = !busy && selectedRole != null && (turnNumber.toIntOrNull() ?: 0) > 0 && turnText.isNotBlank() &&
                        (selectedBuildMode != DirectorOfficeSharedContract.SELECTOR || selectorRangeSelection != null),
                    onClick = {
                        scope.launch {
                            busy = true
                            try {
                                val version = selectedVersion ?: error("نسخه پیش‌نویس پیدا نشد.")
                                val role = selectedRole ?: error("نقش انتخاب نشده است.")
                                val number = turnNumber.toInt()
                                val selectorEntry = if (selectedBuildMode == DirectorOfficeSharedContract.SELECTOR) {
                                    val selectedRange = selectorRangeSelection ?: error("ابتدا بخشی از متن نقش را انتخاب کنید.")
                                    val source = selectedRange.source
                                    require(source.role.uid == role.uid) { "نقش متن انتخاب‌شده با نقش نوبت یکسان نیست." }
                                    DirectorOfficeSelectorEntry(
                                        key = "${source.section.uid}:${selectedRange.start}:${selectedRange.end}",
                                        roleUid = role.uid,
                                        roleTitle = role.title,
                                        sectionUid = source.section.uid,
                                        sectionTitle = source.section.title,
                                        text = turnText.trim(),
                                        sourceContentHash = ContentUid.sha256(source.section.content),
                                        sourceTextStartOffset = selectedRange.start,
                                        sourceTextEndOffset = selectedRange.end
                                    )
                                } else null
                                if (insertTurnMode) {
                                    workspaceRepository.insertTurnAtNumber(
                                        planId = selectedPlan!!.id,
                                        versionId = version.id,
                                        turnNumber = number,
                                        roleUid = role.uid,
                                        roleTitle = role.title,
                                        text = turnText.trim(),
                                        selectorEntry = selectorEntry
                                    )
                                } else if (selectorEntry != null) {
                                    workspaceRepository.addSelectorDialogue(
                                        planId = selectedPlan!!.id,
                                        entry = selectorEntry,
                                        turnNumber = number,
                                        versionId = version.id
                                    )
                                } else {
                                    workspaceRepository.addRoleLinkedTurn(
                                        versionId = version.id,
                                        turnNumber = number,
                                        roleUid = role.uid,
                                        roleTitle = role.title,
                                        text = turnText.trim()
                                    )
                                }
                                loadWorkspaceIntoScreen(selectedPlan!!.id, version.id)
                                turnNumber = (number + 1).toString()
                                turnText = ""
                                selectorRangeEditor = null
                                selectorRangeSelection = null
                                selectorRangeTextValue = TextFieldValue("")
                                showAddTurnDialog = false
                                message = when {
                                    insertTurnMode -> "نوبت جدید در جایگاه $number درج شد؛ شماره نوبت‌های بعدی یک واحد افزایش یافت و ترتیب قبلی حفظ شد."
                                    selectedBuildMode == DirectorOfficeSharedContract.SELECTOR -> "قسمت انتخاب‌شده از نقش «${role.title}» در نوبت $number ثبت شد؛ این قسمت از انتخاب‌های بعدی کنار گذاشته شد."
                                    else -> "نوبت جدید به انتهای دفتر اضافه و ذخیره شد."
                                }
                                insertTurnMode = false
                            } catch (e: Exception) { message = "افزودن نوبت ناموفق بود: ${e.message}" }
                            finally { busy = false }
                        }
                    }
                ) { Text(if (busy) "در حال ذخیره…" else "ذخیره نوبت") }
            },
            dismissButton = { TextButton(enabled = !busy, onClick = { showAddTurnDialog = false }) { Text("انصراف") } }
        )
    }
    deleteTurnCandidate?.let { candidate ->
        AlertDialog(
            onDismissRequest = { deleteTurnCandidate = null },
            title = { Text("حذف نوبت") },
            text = { Text("نوبت ${candidate.turnNumber} از دفتر حذف شود؟ این کار متن‌ها و ارتباط‌های همین نوبت را نیز حذف می‌کند.") },
            confirmButton = {
                TextButton(onClick = {
                    scope.launch {
                        try {
                            workspaceRepository.deleteTurn(candidate.id)
                            loadWorkspaceIntoScreen(selectedPlan!!.id, candidate.versionId)
                            message = "نوبت ${candidate.turnNumber} حذف شد."
                        } catch (e: Exception) { message = "حذف نوبت ناموفق بود: ${e.message}" }
                        finally { deleteTurnCandidate = null }
                    }
                }) { Text("حذف") }
            },
            dismissButton = { TextButton(onClick = { deleteTurnCandidate = null }) { Text("انصراف") } }
        )
    }
    deletePlanCandidate?.let { candidate ->
        AlertDialog(
            onDismissRequest = { deletePlanCandidate = null },
            title = { Text("حذف دفتر معین‌البکا") },
            text = { Text("دفتر «${candidate.title}» از فهرست فعال خارج می‌شود. فایل‌های منبع و متن اصلی مجلس حذف نمی‌شوند. ادامه می‌دهید؟") },
            confirmButton = {
                TextButton(enabled = isAdmin || canDeletePersonal, onClick = {
                    scope.launch {
                        try {
                            dao.softDeletePlan(candidate.id, System.currentTimeMillis())
                            if (selectedPlan?.id == candidate.id) {
                                selectedPlan = null; selectedVersion = null; planVersions = emptyList()
                                sources = emptyList(); turns = emptyList(); roleOptions = emptyList(); selectedRole = null
                                workspaceState = null
                                screenStage = DirectorOfficeScreenStage.SAVED_OFFICES
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
    val onShowHistory: (() -> Unit)? = null,
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
    lineSpacing: Float = 1.4f,
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

    // کادر جستجو در بالای فهرست قرار می‌گیرد تا مانند نسخه قبلی همیشه در دسترس باشد.
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

    item(key = "director-shared-workspace-title") {
        Text(
            text = "$listTitle (${turns.size})" + if (query.isNotBlank()) " • نتایج: ${visibleTurns.size}" else "",
            modifier = Modifier.fillMaxWidth().padding(top = 8.dp, bottom = 2.dp),
            style = MaterialTheme.typography.titleMedium
        )
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
            lineSpacing = lineSpacing,
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
            onShowHistory = actions.onShowHistory,
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
            showSearchIcon = true
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
