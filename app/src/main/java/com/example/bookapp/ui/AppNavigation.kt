package com.example.bookapp.ui

import android.content.Intent
import com.example.bookapp.BuildConfig
import com.example.bookapp.data.ViewerPermissionEngine

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import android.app.Activity
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.clickable
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Surface
import androidx.compose.material3.ListItem
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.navigation.NavHostController
import androidx.navigation.NavBackStackEntry
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.example.bookapp.data.AppDatabase
import com.example.bookapp.data.FieldEntity
import com.example.bookapp.data.NoteEntity
import com.example.bookapp.data.Prefs
import com.example.bookapp.data.SearchResult
import com.example.bookapp.data.SectionEntity
import com.example.bookapp.data.syncLocalContentFiles
import com.example.bookapp.data.syncRemoteContent
import com.example.bookapp.data.ViewerAccessPolicy
import com.example.bookapp.ui.screens.*
import kotlinx.coroutines.launch

private const val ROUTE_SPLASH = "splash"
private const val ROUTE_ONBOARDING = "onboarding"
private const val ROUTE_LOGIN = "login"
private const val ROUTE_MAIN_MENU = "main_menu"
private const val ROUTE_LIBRARY_HOME = "library_home"
private const val ROUTE_ACTIVITY_CENTER = "activity_center"
private const val ROUTE_RESEARCH_CENTER = "research_center"
private const val ROUTE_EXECUTION_CENTER = "execution_center"
private const val ROUTE_MANAGEMENT_CENTER = "management_center"
private const val ROUTE_TOOLS_CENTER = "tools_center"
private const val ROUTE_VIEWER_CONTENT = "viewer_content"
private const val ROUTE_SEARCH = "search"
private const val ROUTE_BOOKMARKS = "bookmarks"
private const val ROUTE_NOTES = "notes"
private const val ROUTE_MY_ROLE = "my_role"
private const val ROUTE_ALL_IMAGES = "all_images"
private const val ROUTE_REHEARSAL_HOME = "rehearsal_home"
private const val ROUTE_REHEARSAL_SETUP = "rehearsal_setup/{mode}"
private const val ROUTE_REHEARSAL = "rehearsal/{roleId}/{roleTitle}"
private const val ROUTE_REHEARSAL_MULTI = "rehearsal_multi/{taziehId}/{ownRoleId}/{ownRoleTitle}"
private const val ROUTE_ABOUT = "about"
private const val ROUTE_SETTINGS = "settings"
private const val ROUTE_VERSION = "version"
private const val ROUTE_CHANGELOG = "changelog"
private const val ROUTE_GLOSSARY = "glossary"
private const val ROUTE_ENCYCLOPEDIA = "encyclopedia"
private const val ROUTE_TAZIEH_CORRECTIONS = "tazieh_corrections"
private const val ROUTE_MUHARRAM_CALENDAR = "muharram_calendar"
private const val ROUTE_AI_RESEARCH = "ai_research"
private const val ROUTE_CONTENT_MANAGEMENT = "content_management"
private const val ROUTE_VIEWER_ACCESS = "viewer_access"
private const val ROUTE_SPECIAL_USERS = "special_users"
private const val ROUTE_ACCESS_AUDIT = "access_audit"
private const val ROUTE_CONTENT_EDITOR = "content_editor"
private const val ROUTE_FIELDS = "fields"
private const val ROUTE_TAZIEHS = "taziehs/{fieldId}/{fieldTitle}"
private const val ROUTE_ROLES = "roles/{taziehId}/{taziehTitle}"
private const val ROUTE_SECTIONS = "sections/{roleId}/{roleTitle}"
private const val ROUTE_TAZIEH_INDEX = "tazieh_index/{taziehId}/{taziehTitle}"
private const val ROUTE_TAZIEH_GALLERY = "tazieh_gallery/{taziehId}"
private const val ROUTE_TEXT = "text/{sectionId}"
private const val ROUTE_TEXT_PAGER = "text_pager/{roleId}/{startIndex}"
private const val ROUTE_COMPARE = "compare/{taziehId}"

private fun concreteNavigationRoute(entry: NavBackStackEntry?): String? {
    entry ?: return null
    var route = entry.destination.route ?: return null
    val args = entry.arguments ?: return route
    args.keySet().forEach { key ->
        val value = args.get(key)?.toString() ?: return@forEach
        route = route.replace("{$key}", android.net.Uri.encode(value))
    }
    return route
}

@Composable
fun AppNavigation(
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
    shortcutTarget: String?,
    deepLinkSectionId: Long? = null
) {
    val context = LocalContext.current
    val db = remember { AppDatabase.getInstance(context) }
    val publicViewer = BuildConfig.PUBLIC_VIEWER
    var viewerPermissions by remember(publicViewer) { mutableStateOf(if (publicViewer) ViewerAccessPolicy.getEffectivePermissions(context) else ViewerAccessPolicy.permissionLabels.keys.associateWith { true }) }
    val navController: NavHostController = rememberNavController()
    fun featureEnabled(key: String): Boolean {
        if (!publicViewer) return true
        return ViewerPermissionEngine.can(context, key)
    }
    fun navigateIfAllowed(key: String, route: String) {
        if (featureEnabled(key)) navController.navigate(route)
        else android.widget.Toast.makeText(context, "این قابلیت در سیاست دسترسی فعلی فعال نیست.", android.widget.Toast.LENGTH_SHORT).show()
    }
    // کنترل مجوز خروجی در لحظه اجرا؛ مخفی‌کردن دکمه به‌تنهایی کنترل امنیتی کافی نیست.
    fun exportAllowed(vararg keys: String): Boolean =
        !publicViewer || ViewerPermissionEngine.canAll(context, *keys)
    fun notifyExportDenied() {
        android.widget.Toast.makeText(context, "مدیر دسترسی خروجی را برای این کاربر فعال نکرده است.", android.widget.Toast.LENGTH_LONG).show()
    }

    LaunchedEffect(Unit) {
        // محتوای همراه APK برای هر دو build بارگذاری می‌شود؛ این مسیر فقط assets
        // داخلی برنامه را می‌خواند و در نسخه عمومی هیچ ابزار ورود/ویرایش در اختیار کاربر نیست.
        val isReturningUser = Prefs.getProcessedContentFiles(context).isNotEmpty()
        val syncResult = runCatching { syncLocalContentFiles(context, db) }
        val newFilesCount = syncResult.getOrElse { error ->
            android.util.Log.e("TaziehContent", "خطا در بارگذاری محتوای همراه برنامه", error)
            0
        }
        if (!publicViewer && isReturningUser && newFilesCount > 0) {
            com.example.bookapp.data.showNewContentNotification(context, newFilesCount)
        }
    }

    // مقصد بعد از ورود: اگر از طریق لینک اشتراک‌گذاری یک بخش خاص باز شده باشد
    // اولویت با آن است؛ وگرنه اگر از میان‌بر آیکون باز شده باشد، به همان مقصد می‌رویم
    fun postLoginRoute(): String = when {
        deepLinkSectionId != null -> "text/$deepLinkSectionId"
        shortcutTarget == "search" && featureEnabled("search") -> ROUTE_SEARCH
        shortcutTarget == "notes" && featureEnabled("notes") -> ROUTE_NOTES
        shortcutTarget == "bookmarks" && featureEnabled("bookmarks") -> ROUTE_BOOKMARKS
        else -> ROUTE_MAIN_MENU
    }

    // انیمیشن سریع و سبک بین صفحات (نه کند/سنگین)، تا هم نرم باشد و هم سرعت استفاده از برنامه افت نکند
    val navAnimDuration = 220
    val currentEntry by navController.currentBackStackEntryAsState()
    val navigationHistory = remember { mutableStateListOf<String>() }
    var historyIndex by remember { mutableIntStateOf(-1) }
    var lastEntryId by remember { mutableStateOf<String?>(null) }
    var lastPreviousEntryId by remember { mutableStateOf<String?>(null) }
    var skipHistoryTracking by remember { mutableStateOf(false) }
    var pendingHistoryIndex by remember { mutableIntStateOf(-1) }
    LaunchedEffect(currentEntry?.id) {
        val entry = currentEntry
        val entryId = entry?.id
        if (entry != null && entryId != null) {
            if (skipHistoryTracking) {
                if (pendingHistoryIndex >= 0) historyIndex = pendingHistoryIndex
                skipHistoryTracking = false
                pendingHistoryIndex = -1
            } else if (entryId == lastPreviousEntryId) {
                historyIndex = (historyIndex - 1).coerceAtLeast(0)
            } else {
                val route = concreteNavigationRoute(entry)
                if (route != null && (navigationHistory.getOrNull(historyIndex) != route || entryId != lastEntryId)) {
                    while (navigationHistory.size > historyIndex + 1) navigationHistory.removeAt(navigationHistory.lastIndex)
                    navigationHistory.add(route)
                    historyIndex = navigationHistory.lastIndex
                }
            }
            lastPreviousEntryId = navController.previousBackStackEntry?.id
            lastEntryId = entryId
        }
    }
    val currentRoute = currentEntry?.destination?.route
    val showGlobalNavigation = currentRoute != null && currentRoute !in setOf(ROUTE_SPLASH, ROUTE_ONBOARDING, ROUTE_LOGIN, ROUTE_MAIN_MENU)

    Column(Modifier.fillMaxSize()) {
        if (showGlobalNavigation) {
            Surface(color = androidx.compose.material3.MaterialTheme.colorScheme.surface, tonalElevation = 2.dp) {
                Row(Modifier.fillMaxWidth().height(44.dp).padding(horizontal = 8.dp), horizontalArrangement = Arrangement.SpaceBetween) {
                    IconButton(onClick = {
                        val mainIndex = navigationHistory.indexOfLast { it == ROUTE_MAIN_MENU }
                        val returnedHome = navController.popBackStack(ROUTE_MAIN_MENU, false)
                        if (returnedHome && mainIndex >= 0) {
                            skipHistoryTracking = true
                            pendingHistoryIndex = mainIndex
                        } else if (!returnedHome) {
                            navigationHistory.clear()
                            navigationHistory.add(ROUTE_MAIN_MENU)
                            historyIndex = 0
                            skipHistoryTracking = true
                            pendingHistoryIndex = 0
                            navController.navigate(ROUTE_MAIN_MENU) { launchSingleTop = true }
                        }
                    }) { Icon(Icons.Filled.Home, contentDescription = "خانه") }
                    Row {
                        IconButton(onClick = {
                            if (historyIndex > 0 && navController.previousBackStackEntry != null) {
                                skipHistoryTracking = true
                                pendingHistoryIndex = historyIndex - 1
                                navController.popBackStack()
                            }
                        }, enabled = historyIndex > 0 && navController.previousBackStackEntry != null) {
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "صفحه قبلی")
                        }
                        IconButton(onClick = {
                            val targetIndex = historyIndex + 1
                            val target = navigationHistory.getOrNull(targetIndex)
                            if (target != null) {
                                skipHistoryTracking = true
                                pendingHistoryIndex = targetIndex
                                navController.navigate(target) { launchSingleTop = true }
                            }
                        }, enabled = historyIndex >= 0 && historyIndex < navigationHistory.lastIndex) {
                            Icon(Icons.AutoMirrored.Filled.ArrowForward, contentDescription = "صفحه بعدی")
                        }
                    }
                }
            }
        }
        NavHost(
        modifier = Modifier.weight(1f),
        navController = navController,
        startDestination = ROUTE_SPLASH,
        enterTransition = {
            fadeIn(tween(navAnimDuration)) + slideInHorizontally(tween(navAnimDuration)) { it / 6 }
        },
        exitTransition = {
            fadeOut(tween(navAnimDuration)) + slideOutHorizontally(tween(navAnimDuration)) { -it / 6 }
        },
        popEnterTransition = {
            fadeIn(tween(navAnimDuration)) + slideInHorizontally(tween(navAnimDuration)) { -it / 6 }
        },
        popExitTransition = {
            fadeOut(tween(navAnimDuration)) + slideOutHorizontally(tween(navAnimDuration)) { it / 6 }
        }
    ) {

        composable(ROUTE_SPLASH) {
            SplashScreen(onFinished = {
                val next = if (Prefs.isOnboardingShown(context)) ROUTE_LOGIN else ROUTE_ONBOARDING
                navController.navigate(next) {
                    popUpTo(ROUTE_SPLASH) { inclusive = true }
                }
            })
        }

        composable(ROUTE_ONBOARDING) {
            OnboardingScreen(onFinished = {
                Prefs.setOnboardingShown(context)
                navController.navigate(ROUTE_LOGIN) {
                    popUpTo(ROUTE_ONBOARDING) { inclusive = true }
                }
            })
        }

        composable(ROUTE_LOGIN) {
            LoginScreen(onLoginSuccess = {
                navController.navigate(postLoginRoute()) {
                    popUpTo(ROUTE_LOGIN) { inclusive = true }
                }
            })
        }

        composable(ROUTE_MAIN_MENU) {
            MainMenuScreen(
    onOpenLibrary = { navController.navigate(ROUTE_LIBRARY_HOME) },
    onOpenActivityCenter = { navController.navigate(ROUTE_ACTIVITY_CENTER) },
    onOpenNotes = { navigateIfAllowed("notes", ROUTE_NOTES) },
    onOpenSearch = { navigateIfAllowed("search", ROUTE_SEARCH) },
    onOpenBookmarks = { navigateIfAllowed("bookmarks", ROUTE_BOOKMARKS) },
    onOpenSettings = { navController.navigate(ROUTE_SETTINGS) },
    onOpenAbout = { navController.navigate(ROUTE_ABOUT) },
    onExit = { (context as? Activity)?.finish() }
        )
        }

        if (featureEnabled("read.view")) composable(ROUTE_LIBRARY_HOME) {
            var randomVerse by remember { mutableStateOf<SearchResult?>(null) }
            var recentItems by remember { mutableStateOf(listOf<SearchResult>()) }
            LaunchedEffect(Unit) {
                randomVerse = db.searchDao().getRandomSection()
                val ids = Prefs.getRecent(context)
                if (ids.isNotEmpty()) {
                    val fetched = db.searchDao().getByIds(ids)
                    val byId = fetched.associateBy { it.sectionId }
                    recentItems = ids.mapNotNull { byId[it] }
                }
            }
            LibraryHomeScreen(
                randomVerse = randomVerse,
                recentItems = recentItems,
                onOpenTaziehList = { navController.navigate(ROUTE_FIELDS) },
                onOpenSearch = { navigateIfAllowed("search", ROUTE_SEARCH) },
                onOpenMyRole = { navigateIfAllowed("myRole", ROUTE_MY_ROLE) },
                onOpenRehearsal = { navigateIfAllowed("training.rehearse", ROUTE_REHEARSAL_HOME) },
                onOpenGlossary = { navigateIfAllowed("dictionary.view", ROUTE_GLOSSARY) },
                onOpenGallery = { navigateIfAllowed("gallery.view", ROUTE_ALL_IMAGES) },
                onOpenCompareGuide = { navController.navigate(ROUTE_FIELDS) },
                onItemClick = { result -> navController.navigate("text/${result.sectionId}") },
                showSearch = featureEnabled("search.basic"),
                showMyRole = featureEnabled("myRole.view"),
                showRehearsal = featureEnabled("training.rehearse"),
                showGlossary = featureEnabled("dictionary.view"),
                showGallery = featureEnabled("gallery.view"),
                showCompare = featureEnabled("compare.view"),
                onOpenDirectorOffice = if (!publicViewer) ({ navController.navigate("director_office") }) else null,
                onBack = { navController.popBackStack() }
            )
        }

        composable(ROUTE_ACTIVITY_CENTER) {
            ActivityCenterScreen(
                isAdmin = !publicViewer,
                canRead = featureEnabled("read.view"),
                canSearch = featureEnabled("search.basic"),
                canMyRole = featureEnabled("myRole.view"),
                canDictionary = featureEnabled("dictionary.view"),
                canEncyclopedia = featureEnabled("encyclopedia.view"),
                canCorrections = featureEnabled("taziehCorrections.view"),
                canAiResearch = featureEnabled("ai"),
                canCalendar = featureEnabled("calendar.view"),
                onOpenLibrary = { navController.navigate(ROUTE_FIELDS) },
                onOpenSearch = { navigateIfAllowed("search.basic", ROUTE_SEARCH) },
                onOpenMyRole = { navigateIfAllowed("myRole.view", ROUTE_MY_ROLE) },
                onOpenGlossary = { navigateIfAllowed("dictionary.view", ROUTE_GLOSSARY) },
                onOpenEncyclopedia = { navigateIfAllowed("encyclopedia.view", ROUTE_ENCYCLOPEDIA) },
                onOpenCorrections = { navigateIfAllowed("taziehCorrections.view", ROUTE_TAZIEH_CORRECTIONS) },
                onOpenAiResearch = { navigateIfAllowed("ai", ROUTE_AI_RESEARCH) },
                onOpenCalendar = { navigateIfAllowed("calendar.view", ROUTE_MUHARRAM_CALENDAR) },
                onOpenVersionComparison = { if (!publicViewer) navController.navigate("version_comparison") },
                onOpenEncyclopediaManagement = { if (!publicViewer) navController.navigate("encyclopedia_management") },
                onOpenPerformanceManagement = { if (!publicViewer) navController.navigate("performance_management") },
                onOpenContentManagement = { if (!publicViewer) navController.navigate(ROUTE_CONTENT_MANAGEMENT) },
                onOpenViewerAccess = { if (!publicViewer) navController.navigate(ROUTE_VIEWER_ACCESS) },
                onOpenSpecialUsers = { if (!publicViewer) navController.navigate(ROUTE_SPECIAL_USERS) },
                onOpenAccessAudit = { if (!publicViewer) navController.navigate(ROUTE_ACCESS_AUDIT) },
                onOpenSettings = { navController.navigate(ROUTE_SETTINGS) },
                onOpenMultiRoleDialogue = {
                    if (featureEnabled("read.view")) navController.navigate(ROUTE_FIELDS)
                },
                onBack = { navController.popBackStack() }
            )
        }

        composable(ROUTE_TOOLS_CENTER) {
            ToolsCenterScreen(
                onOpenCalendar = { navigateIfAllowed("calendar.view", ROUTE_MUHARRAM_CALENDAR) },
                onOpenVersion = { navController.navigate(ROUTE_VERSION) },
                onOpenChangelog = { navController.navigate(ROUTE_CHANGELOG) },
                onBack = { navController.popBackStack() }
            )
        }

        composable(ROUTE_RESEARCH_CENTER) {
            ResearchCenterScreen(
                showVersionComparison = !publicViewer,
                showAi = featureEnabled("ai"),
                showGlossary = featureEnabled("dictionary.view"),
                showEncyclopedia = featureEnabled("encyclopedia.view"),
                showCorrections = featureEnabled("taziehCorrections.view"),
                onOpenAi = { navigateIfAllowed("ai", ROUTE_AI_RESEARCH) },
                onOpenVersionComparison = { if (!publicViewer) navController.navigate("version_comparison") },
                onOpenGlossary = { navigateIfAllowed("dictionary.view", ROUTE_GLOSSARY) },
                onOpenEncyclopedia = { navigateIfAllowed("encyclopedia.view", ROUTE_ENCYCLOPEDIA) },
                onOpenCorrections = { navigateIfAllowed("taziehCorrections.view", ROUTE_TAZIEH_CORRECTIONS) },
                onBack = { navController.popBackStack() }
            )
        }

        composable(ROUTE_EXECUTION_CENTER) {
            ExecutionCenterScreen(
                showAdminActions = !publicViewer,
                onOpenGroups = { if (!publicViewer) navController.navigate("performance_groups") },
                onOpenExecutionManagement = { if (!publicViewer) navController.navigate("performance_execution") },
                onOpenDirectorOffice = { if (!publicViewer) navController.navigate("director_office") },
                onBack = { navController.popBackStack() }
            )
        }

        if (!publicViewer) composable(ROUTE_MANAGEMENT_CENTER) {
            ManagementCenterScreen(
                onOpenContent = { navController.navigate(ROUTE_CONTENT_MANAGEMENT) },
                onOpenAccess = { navController.navigate(ROUTE_VIEWER_ACCESS) },
                onOpenSpecialUsers = { navController.navigate(ROUTE_SPECIAL_USERS) },
                onOpenAudit = { navController.navigate(ROUTE_ACCESS_AUDIT) },
                onBack = { navController.popBackStack() }
            )
        }

        if (publicViewer) composable(ROUTE_VIEWER_CONTENT) {
            ViewerContentScreen(
                db = db,
                onOpenSection = { sectionId -> navController.navigate("text/$sectionId") },
                onBack = { navController.popBackStack() }
            )
        }

        if (featureEnabled("encyclopedia.view")) composable(ROUTE_ENCYCLOPEDIA) {
            EncyclopediaReadOnlyScreen(db = db, onBack = { navController.popBackStack() })
        }

        if (featureEnabled("search.basic")) composable(ROUTE_SEARCH) {
            var fields by remember { mutableStateOf(listOf<com.example.bookapp.data.FieldEntity>()) }
            var allTaziehs by remember { mutableStateOf(listOf<com.example.bookapp.data.TaziehEntity>()) }
            var allRoles by remember { mutableStateOf(listOf<com.example.bookapp.data.RoleEntity>()) }
            var allSections by remember { mutableStateOf(listOf<SectionEntity>()) }
            var searchCorpus by remember { mutableStateOf(emptyList<com.example.bookapp.data.SearchCorpusRow>()) }
            var bookmarkedIds by remember { mutableStateOf(Prefs.getBookmarks(context)) }
            LaunchedEffect(Unit) {
                fields = db.fieldDao().getAll()
                allTaziehs = db.taziehDao().getAll()
                allRoles = allTaziehs.flatMap { db.roleDao().getByTazieh(it.id) }
                allSections = db.sectionDao().getAll()
                searchCorpus = db.searchDao().getSearchCorpus()
            }
            SearchScreen(
                fields = fields,
                allTaziehs = allTaziehs,
                allRoles = allRoles,
                allSections = allSections,
                onSearch = { query, options ->
                    com.example.bookapp.data.AdvancedSearchEngine.search(searchCorpus, query, options)
                },
                onResultClick = { result -> navController.navigate("text/${result.sectionId}") },
                onSearchDialogues = { query -> db.searchDao().searchDialogues(query) },
                onSearchGlossary = { query ->
                    val normalized = com.example.bookapp.data.normalizeSearchText(query)
                    val queryTokens = normalized.split(' ').filter { it.isNotBlank() }.distinct()
                    fun matches(value: String): Boolean {
                        val candidate = com.example.bookapp.data.normalizeSearchText(value)
                        return queryTokens.isNotEmpty() && queryTokens.all { candidate.contains(it) }
                    }
                    val glossary = db.glossaryDao()
                    val spellingsByTerm = glossary.allSpellings().groupBy { it.termId }
                    val meaningsByTerm = glossary.allMeanings().groupBy { it.termId }
                    glossary.allTerms().asSequence().filter { term ->
                        term.reviewStatus != com.example.bookapp.data.GlossaryTermEntity.STATUS_REJECTED &&
                            (!publicViewer || term.reviewStatus == com.example.bookapp.data.GlossaryTermEntity.STATUS_VERIFIED)
                    }.mapNotNull { term ->
                        val matchingSpelling = spellingsByTerm[term.id].orEmpty().firstOrNull { matches(it.spelling) }
                        val meanings = meaningsByTerm[term.id].orEmpty().filter { meaning ->
                            meaning.reviewStatus != com.example.bookapp.data.GlossaryTermEntity.STATUS_REJECTED &&
                                (!publicViewer || meaning.reviewStatus == com.example.bookapp.data.GlossaryTermEntity.STATUS_VERIFIED)
                        }
                        val matchingMeaning = meanings.firstOrNull { matches(it.meaning) || matches(it.sourceTitle) }
                        val headMatch = matches(term.headword) || matches(term.normalizedHeadword)
                        if (headMatch || matchingSpelling != null || matchingMeaning != null) {
                            com.example.bookapp.ui.screens.GlossarySearchHit(term.id, term.headword, matchingMeaning?.meaning ?: meanings.firstOrNull()?.meaning.orEmpty(), matchingMeaning?.sourceTitle ?: meanings.firstOrNull()?.sourceTitle.orEmpty())
                        } else null
                    }.take(100).toList()
                },
                onDialogueResultClick = { d ->
                    if (featureEnabled("dialogues.view")) {
                        navController.navigate("dialogue_reader/${d.dialogueId}")
                    }
                },
                isBookmarked = { id -> id in bookmarkedIds },
                showBookmarks = featureEnabled("bookmarks.view"),
                advancedEnabled = featureEnabled("advancedSearch.filters"),
                onToggleBookmark = { id ->
                    if (featureEnabled("bookmarks.add")) {
                        Prefs.toggleBookmark(context, id)
                        bookmarkedIds = Prefs.getBookmarks(context)
                    } else {
                        android.widget.Toast.makeText(context, "دسترسی به علاقه‌مندی‌ها فعال نیست.", android.widget.Toast.LENGTH_SHORT).show()
                    }
                },
                onBack = { navController.popBackStack() }
            )
        }

        if (featureEnabled("bookmarks.view")) composable(ROUTE_BOOKMARKS) {
            var items by remember { mutableStateOf(listOf<SearchResult>()) }
            LaunchedEffect(Unit) {
                val ids = Prefs.getBookmarks(context).toList()
                items = if (ids.isEmpty()) emptyList() else db.searchDao().getByIds(ids)
            }
            BookmarksScreen(
                items = items,
                onItemClick = { result -> navController.navigate("text/${result.sectionId}") },
                onRemove = { result ->
                    if (featureEnabled("bookmarks.delete")) {
                        Prefs.toggleBookmark(context, result.sectionId)
                        items = items.filterNot { it.sectionId == result.sectionId }
                    }
                },
                canDelete = featureEnabled("bookmarks.delete"),
                onBack = { navController.popBackStack() }
            )
        }

        if (featureEnabled("notes.view")) composable(ROUTE_NOTES) {
            var notes by remember { mutableStateOf(listOf<NoteEntity>()) }
            var linkedNoteInfo by remember { mutableStateOf(emptyMap<Long, NoteLinkInfo>()) }
            val scope = androidx.compose.runtime.rememberCoroutineScope()
            val visibleEntry by navController.currentBackStackEntryAsState()

            suspend fun reload() {
                val loadedNotes = db.noteDao().getAll()
                notes = loadedNotes
                val links = mutableMapOf<Long, NoteLinkInfo>()
                loadedNotes.forEach { note ->
                    val sectionUid = note.sectionUid?.takeIf { it.isNotBlank() } ?: return@forEach
                    val section = db.sectionDao().getByUid(sectionUid) ?: return@forEach
                    val role = try { db.roleDao().getById(section.roleId) } catch (_: Exception) { null } ?: return@forEach
                    val tazieh = db.taziehDao().getById(role.taziehId)
                    val label = listOfNotNull(tazieh?.title, role.title, section.title).joinToString(" ← ")
                    links[note.id] = NoteLinkInfo(
                        label = label,
                        sectionId = section.id,
                        roleId = role.id,
                        roleTitle = role.title
                    )
                }
                linkedNoteInfo = links
            }
            LaunchedEffect(visibleEntry?.destination?.route) {
                if (visibleEntry?.destination?.route == ROUTE_NOTES) reload()
            }

            NotesScreen(
                notes = notes,
                onAddNote = { title, content ->
                    val note = NoteEntity(title = title, content = content)
                    scope.launch {
                        db.noteDao().insert(note)
                        reload()
                    }
                },
                onUpdateNote = { note, title, content ->
                    scope.launch {
                        db.noteDao().update(note.copy(title = title, content = content))
                        reload()
                    }
                },
                onDeleteNote = { id ->
                    scope.launch {
                        db.noteDao().delete(id)
                        reload()
                    }
                },
                onBack = { navController.popBackStack() },
                canAdd = featureEnabled("notes.add"),
                canEdit = featureEnabled("notes.edit"),
                canDelete = featureEnabled("notes.delete"),
                linkedNoteInfo = if (featureEnabled("training.rehearse")) linkedNoteInfo else emptyMap(),
                onOpenLinkedNote = { note ->
                    linkedNoteInfo[note.id]?.let { link ->
                        navController.currentBackStackEntry?.savedStateHandle?.set(
                            "rehearsal_selected_section_ids",
                            longArrayOf(link.sectionId)
                        )
                        navController.navigate("rehearsal/${link.roleId}/${android.net.Uri.encode(link.roleTitle)}")
                    }
                }
            )
        }

        if (featureEnabled("myRole.view")) composable(ROUTE_MY_ROLE) {
            var items by remember { mutableStateOf(listOf<MyRoleItem>()) }
            val scope = androidx.compose.runtime.rememberCoroutineScope()

            suspend fun reloadMyRoles() {
                val saved = Prefs.getAllMyRoles(context)
                items = saved.mapNotNull { (taziehId, roleId) ->
                    val tazieh = db.taziehDao().getById(taziehId) ?: return@mapNotNull null
                    val role = try { db.roleDao().getById(roleId) } catch (e: Exception) { null } ?: return@mapNotNull null
                    MyRoleItem(
                        taziehId = taziehId,
                        taziehTitle = tazieh.title,
                        roleId = roleId,
                        roleTitle = role.title
                    )
                }
            }
            LaunchedEffect(Unit) { reloadMyRoles() }

            MyRoleScreen(
                items = items,
                onRead = { item ->
                    navController.navigate("sections/${item.roleId}/${item.roleTitle}")
                },
                onExportPdf = { item ->
                    if (!exportAllowed("pdf", "pdf.create", "pdf.save", "myRole", "myRole.pdf")) notifyExportDenied()
                    else scope.launch {
                        val sections = db.sectionDao().getByRole(item.roleId)
                        com.example.bookapp.data.exportRoleToPdf(context, item.roleTitle, sections)
                    }
                },
                onRemove = { item ->
                    Prefs.clearMyRole(context, item.taziehId)
                    scope.launch { reloadMyRoles() }
                },
                onBack = { navController.popBackStack() },
                readOnly = publicViewer,
                showPdf = featureEnabled("myRole.pdf") && featureEnabled("myRole") && featureEnabled("pdf") && featureEnabled("pdf.create") && featureEnabled("pdf.save"),
                canRead = featureEnabled("myRole.view"),
                canRemove = featureEnabled("myRole.remove")
            )
        }

        if (featureEnabled("gallery.view")) composable(ROUTE_ALL_IMAGES) {
            var images by remember { mutableStateOf(listOf<GalleryImageItem>()) }
            LaunchedEffect(Unit) {
                val taziehs = db.taziehDao().getAll()
                images = taziehs.flatMap { tazieh ->
                    db.taziehImageDao().getByTazieh(tazieh.id).map { img ->
                        GalleryImageItem(img.id, img.filePath, img.caption, tazieh.title)
                    }
                }
            }
            AllImagesGalleryScreen(
                images = images,
                onBack = { navController.popBackStack() }
            )
        }

        if (featureEnabled("training.rehearse")) composable(ROUTE_REHEARSAL_HOME) {
            RehearsalModeScreen(
                onIndividual = { navController.navigate("rehearsal_setup/individual") },
                onMultiRole = { navController.navigate("rehearsal_setup/multi") },
                onBack = { navController.popBackStack() }
            )
        }

        if (featureEnabled("training.rehearse")) composable(ROUTE_REHEARSAL_SETUP) { backStackEntry ->
            val isMultiRoleMode = backStackEntry.arguments?.getString("mode") == "multi"
            var rehearsalFields by remember { mutableStateOf(listOf<FieldEntity>()) }
            var rehearsalTaziehs by remember { mutableStateOf(listOf<RehearsalTaziehOption>()) }
            var selectedRehearsalFieldId by remember { mutableStateOf<Long?>(null) }
            var selectedRehearsalTaziehId by remember { mutableStateOf<Long?>(null) }
            var rehearsalRoles by remember { mutableStateOf(listOf<RehearsalRoleOption>()) }
            var rehearsalRolesLoading by remember { mutableStateOf(false) }

            LaunchedEffect(Unit) {
                rehearsalFields = db.fieldDao().getAll()
                rehearsalTaziehs = db.taziehDao().getAll().map { tazieh ->
                    RehearsalTaziehOption(
                        id = tazieh.id,
                        fieldId = tazieh.fieldId,
                        title = tazieh.title
                    )
                }
            }

            LaunchedEffect(selectedRehearsalTaziehId) {
                val taziehId = selectedRehearsalTaziehId
                if (taziehId == null) {
                    rehearsalRoles = emptyList()
                    rehearsalRolesLoading = false
                } else {
                    rehearsalRolesLoading = true
                    rehearsalRoles = emptyList()
                    val roleEntities = db.roleDao().getByTazieh(taziehId)
                    val loadedRoleOptions = mutableListOf<RehearsalRoleOption>()
                    for (role in roleEntities) {
                        val roleSections = db.sectionDao().getByRole(role.id)
                        loadedRoleOptions.add(RehearsalRoleOption(role, roleSections))
                    }
                    rehearsalRoles = loadedRoleOptions
                    rehearsalRolesLoading = false
                }
            }

            RehearsalSetupScreen(
                fields = rehearsalFields,
                taziehs = rehearsalTaziehs,
                selectedFieldId = selectedRehearsalFieldId,
                selectedTaziehId = selectedRehearsalTaziehId,
                roles = rehearsalRoles,
                rolesLoading = rehearsalRolesLoading,
                isMultiRoleMode = isMultiRoleMode,
                onFieldSelected = { fieldId ->
                    selectedRehearsalFieldId = fieldId
                    selectedRehearsalTaziehId = null
                },
                onTaziehSelected = { selectedRehearsalTaziehId = it },
                onBack = { navController.popBackStack() },
                onStart = { taziehId, primaryRoleId, primaryRoleTitle, sectionIds, multiRole ->
                    navController.currentBackStackEntry?.savedStateHandle?.set(
                        "rehearsal_selected_section_ids",
                        sectionIds.toLongArray()
                    )
                    if (multiRole) {
                        navController.navigate(
                            "rehearsal_multi/$taziehId/$primaryRoleId/${android.net.Uri.encode(primaryRoleTitle)}"
                        )
                    } else {
                        navController.navigate(
                            "rehearsal/$primaryRoleId/${android.net.Uri.encode(primaryRoleTitle)}"
                        )
                    }
                }
            )
        }

        if (featureEnabled("training.rehearse")) composable(ROUTE_REHEARSAL) { backStackEntry ->
            val roleId = backStackEntry.arguments?.getString("roleId")?.toLongOrNull() ?: 0L
            val roleTitle = backStackEntry.arguments?.getString("roleTitle") ?: ""
            val selectedIds = navController.previousBackStackEntry?.savedStateHandle?.get<LongArray>("rehearsal_selected_section_ids")?.toSet()
            var sections by remember { mutableStateOf(listOf<SectionEntity>()) }
            LaunchedEffect(roleId, selectedIds) {
                val all = db.sectionDao().getByRole(roleId)
                sections = if (selectedIds.isNullOrEmpty()) all else all.filter { it.id in selectedIds }
            }
            val rehearsalScope = androidx.compose.runtime.rememberCoroutineScope()
            RehearsalScreen(
                roleTitle = roleTitle,
                sections = sections,
                onBack = { navController.popBackStack() },
                canPlayAudio = featureEnabled("audio.play"),
                canAddPracticeNote = featureEnabled("notes.view") && featureEnabled("notes.add"),
                onAddPracticeNote = { section, title, content ->
                    rehearsalScope.launch {
                        try {
                            db.noteDao().insert(
                                NoteEntity(title = title, content = content, sectionUid = section.uid)
                            )
                            android.widget.Toast.makeText(context, "یادداشت در دفتر یادداشت ذخیره شد.", android.widget.Toast.LENGTH_SHORT).show()
                        } catch (_: Exception) {
                            android.widget.Toast.makeText(context, "ذخیره یادداشت انجام نشد.", android.widget.Toast.LENGTH_SHORT).show()
                        }
                    }
                }
            )
        }

        if (featureEnabled("training.rehearse")) composable(ROUTE_REHEARSAL_MULTI) { backStackEntry ->
            val taziehId = backStackEntry.arguments?.getString("taziehId")?.toLongOrNull() ?: 0L
            val ownRoleId = backStackEntry.arguments?.getString("ownRoleId")?.toLongOrNull() ?: 0L
            val ownRoleTitle = backStackEntry.arguments?.getString("ownRoleTitle") ?: ""
            val selectedIds = navController.previousBackStackEntry?.savedStateHandle?.get<LongArray>("rehearsal_selected_section_ids")?.toList().orEmpty()
            var taziehTitle by remember { mutableStateOf("") }
            var orderingDescription by remember { mutableStateOf("ترتیب انتخاب‌شده توسط شما؛ ترتیب اصلی مستند نیست") }
            var turns by remember { mutableStateOf(listOf<RehearsalTurnItem>()) }
            LaunchedEffect(taziehId, ownRoleId, selectedIds) {
                taziehTitle = db.taziehDao().getById(taziehId)?.title.orEmpty()
                val selectedSections = mutableListOf<SectionEntity>()
                for (sectionId in selectedIds) {
                    try {
                        selectedSections.add(db.sectionDao().getById(sectionId))
                    } catch (_: Exception) {
                        // بخش حذف‌شده یا در دسترس‌نبودن آن از فهرست تمرین کنار گذاشته می‌شود.
                    }
                }
                val roleTitles = mutableMapOf<Long, String>()
                for (roleId in selectedSections.map { it.roleId }.distinct()) {
                    roleTitles[roleId] = db.roleDao().getById(roleId).title
                }
                val selectedSet = selectedSections.map { it.id }.toSet()
                val dialogues = db.dialogueDao().getByTazieh(taziehId)
                val dialogueCandidates = mutableListOf<Pair<List<com.example.bookapp.data.DialogueTurnEntity>, Map<Long, com.example.bookapp.data.DialogueTurnMetadataEntity>>>()
                for (dialogue in dialogues) {
                    val dialogueTurns = db.dialogueTurnDao().getByDialogue(dialogue.id)
                    val matched = dialogueTurns.filter { it.sectionId in selectedSet }
                    val metadata = db.dialogueTurnMetadataDao().getByDialogue(dialogue.id).associateBy { it.turnId }
                    dialogueCandidates.add(Pair(matched, metadata))
                }
                val candidates = dialogueCandidates.maxByOrNull { candidate ->
                    candidate.first.map { it.sectionId }.distinct().size
                }

                val byId = selectedSections.associateBy { it.id }
                val candidateMatchedIds = candidates?.first?.map { it.sectionId }?.toSet().orEmpty()
                val hasCompleteDialogueMatch = selectedSet.isNotEmpty() && candidateMatchedIds.containsAll(selectedSet)

                val orderedSections: List<SectionEntity> = if (candidates != null && hasCompleteDialogueMatch) {
                    val matchedTurns = candidates.first
                    val metadata = candidates.second
                    val allSourceVerified = matchedTurns.isNotEmpty() && matchedTurns.all { turn ->
                        val item = metadata[turn.id]
                        item != null && item.sourceSequenceIndex != null &&
                            (item.sourceSequenceStatus == com.example.bookapp.data.DialogueTurnMetadataEntity.SOURCE_DOCUMENTED ||
                             item.sourceSequenceStatus == com.example.bookapp.data.DialogueTurnMetadataEntity.SOURCE_DIRECTOR_CONFIRMED)
                    }
                    val orderedMatchedTurns = when {
                        allSourceVerified -> {
                            orderingDescription = "ترتیب مستند منبع"
                            matchedTurns.sortedBy { metadata[it.id]?.sourceSequenceIndex }
                        }
                        matchedTurns.isNotEmpty() && matchedTurns.all { metadata.containsKey(it.id) } -> {
                            orderingDescription = "ترتیب تمرین تنظیم‌شده؛ لزوماً ترتیب اصلی اجرا نیست"
                            matchedTurns.sortedWith(
                                compareBy<com.example.bookapp.data.DialogueTurnEntity> {
                                    metadata[it.id]?.rehearsalOrderIndex ?: Int.MAX_VALUE
                                }.thenBy { it.orderIndex }
                            )
                        }
                        else -> {
                            orderingDescription = "ترتیب ثبت‌شده در گفت‌وگو؛ ترتیب تاریخی/منبعی تأیید نشده"
                            matchedTurns.sortedBy { it.orderIndex }
                        }
                    }
                    // تکرار یک بخش در چند نوبت مکالمه حفظ می‌شود؛ آن را با distinct حذف نمی‌کنیم.
                    orderedMatchedTurns.mapNotNull { byId[it.sectionId] }
                } else {
                    orderingDescription = "ترتیب انتخاب‌شده توسط شما؛ ترتیب اصلی مستند نیست"
                    selectedIds.mapNotNull { byId[it] }
                }

                turns = orderedSections.map { section ->
                    RehearsalTurnItem(
                        section = section,
                        roleTitle = roleTitles[section.roleId].orEmpty(),
                        isOwnRole = section.roleId == ownRoleId,
                        orderLabel = orderingDescription
                    )
                }

            }
            val rehearsalScope = androidx.compose.runtime.rememberCoroutineScope()
            MultiRoleRehearsalScreen(
                taziehTitle = taziehTitle,
                ownRoleId = ownRoleId,
                ownRoleTitle = ownRoleTitle,
                turns = turns,
                onBack = { navController.popBackStack() },
                canPlayAudio = featureEnabled("audio.play"),
                canAddPracticeNote = featureEnabled("notes.view") && featureEnabled("notes.add"),
                onAddPracticeNote = { section, title, content ->
                    rehearsalScope.launch {
                        try {
                            db.noteDao().insert(
                                NoteEntity(title = title, content = content, sectionUid = section.uid)
                            )
                            android.widget.Toast.makeText(context, "یادداشت در دفتر یادداشت ذخیره شد.", android.widget.Toast.LENGTH_SHORT).show()
                        } catch (_: Exception) {
                            android.widget.Toast.makeText(context, "ذخیره یادداشت انجام نشد.", android.widget.Toast.LENGTH_SHORT).show()
                        }
                    }
                }
            )
        }

        composable(ROUTE_ABOUT) {
            var fieldsCount by remember { mutableIntStateOf(0) }
            var taziehsCount by remember { mutableIntStateOf(0) }
            var rolesCount by remember { mutableIntStateOf(0) }
            var sectionsCount by remember { mutableIntStateOf(0) }
            LaunchedEffect(Unit) {
                fieldsCount = db.searchDao().countFields()
                taziehsCount = db.searchDao().countTaziehs()
                rolesCount = db.searchDao().countRoles()
                sectionsCount = db.searchDao().countSections()
            }
            AboutScreen(
                fieldsCount = fieldsCount,
                taziehsCount = taziehsCount,
                rolesCount = rolesCount,
                sectionsCount = sectionsCount,
                readCount = Prefs.getReadSectionsCount(context),
                streakDays = Prefs.getStreakDays(context),
                activeDaysLast14 = Prefs.getActiveDaysLast(context, 14),
                showAppIntro = featureEnabled("appIntro"),
                onBack = { navController.popBackStack() }
            )
        }

        composable(ROUTE_SETTINGS) {
            SettingsScreen(
                darkMode = darkMode,
                onDarkModeChange = onDarkModeChange,
                autoDarkMode = autoDarkMode,
                onAutoDarkModeChange = onAutoDarkModeChange,
                fontScale = fontScale,
                onFontScaleChange = onFontScaleChange,
                fontChoice = fontChoice,
                onFontChoiceChange = onFontChoiceChange,
                themeChoice = themeChoice,
                onThemeChoiceChange = onThemeChoiceChange,
                keepScreenOn = keepScreenOn,
                onKeepScreenOnChange = onKeepScreenOnChange,
                showContentSync = !publicViewer,
                showViewerAccessImport = publicViewer,
                onImportViewerAccess = { uri ->
                    val result = runCatching { context.contentResolver.openInputStream(uri) ?: error("فایل خوانده نشد.") }
                        .fold(
                            onSuccess = { input -> com.example.bookapp.data.ViewerAccessTransfer.importPolicy(context, input) },
                            onFailure = { Result.failure(it) }
                        )
                    if (result.isSuccess && publicViewer) viewerPermissions = ViewerAccessPolicy.getEffectivePermissions(context)
                    result
                },
                onSyncContent = { syncRemoteContent(db, com.example.bookapp.data.Prefs.getContentSourceUrl(context)) },
                onCheckAppUpdate = {
                    val installed = com.example.bookapp.data.UpdateHelper.getInstalledVersion(context)
                    val result = com.example.bookapp.data.UpdateHelper.checkForUpdate(installed.buildNumber)
                    result
                },
                showUpdateManifestTools = !publicViewer,
                showSyncServerSettings = !publicViewer,
                showDatabaseDiagnostics = !publicViewer,
                syncServerUrl = com.example.bookapp.data.SyncServerHelper.getServerUrl(context),
                onSaveSyncServerUrl = { com.example.bookapp.data.SyncServerHelper.setServerUrl(context, it) },
                onSyncServer = { com.example.bookapp.data.SyncServerHelper.syncContent(context, db) },
                db = db,
                onBack = { navController.popBackStack() }
            )
        }

        composable(ROUTE_VERSION) { VersionScreen(onBack = { navController.popBackStack() }) }

        composable(ROUTE_CHANGELOG) {
            ChangelogScreen(
                entries = listOf(
                    ChangelogEntry("مرحله دهم — دستیار هوشمند", listOf(
                        "تحلیل آفلاین ساختار متن، عنوان‌های احتمالی و تکرارها",
                        "پیشنهاد گوینده بر اساس الگوی نام و دونقطه؛ نیازمند بازبینی انسانی",
                        "استخراج واژه‌های پرتکرار برای بررسی در واژه‌نامه",
                        "جستجوی معنایی/تقریبی آفلاین در متن آرشیو",
                        "تبدیل گفتار به متن با سرویس تشخیص گفتار نصب‌شده روی دستگاه",
                        "کنترل جداگانه دسترسی هوش مصنوعی برای هر کاربر توسط مدیر"
                    )),
                    ChangelogEntry("جدید", listOf(
                        "پشتیبان اطلاعات شخصی و فعالیت‌های کاربر با رمزگذاری اختیاری، کنترل سلامت SQLite و اعتبارسنجی فایل پیش از بازیابی",
                        "گزارش اشکال محتوا از داخل هر متن",
                        "تصحیح رنگ تم طلایی در حالت روشن",
                        "امکان روشن/خاموش‌کردن قفل صفحه از تنظیمات",
                        "پس‌زمینه متفاوت برای نقش دوم در مقایسه و گفتگو"
                    )),
                    ChangelogEntry("نسخه‌های قبلی", listOf(
                        "گالری تصاویر (اختصاصی هر تعزیه + گالری عمومی در منوی اصلی)",
                        "صدای واقعی برای بخش‌ها",
                        "گفتگوهای چندنقشی (مثل شمر و عباس)",
                        "فهرست تعزیه با ترتیب قابل ویرایش",
                        "حالت تمرین و نقش من",
                        "پاورقی برای واژه‌ها و توضیحات"
                    ))
                ),
                onBack = { navController.popBackStack() }
            )
        }

        if (featureEnabled("dictionary.view")) composable(ROUTE_GLOSSARY) {
            GlossaryScreen(
                onBack = { navController.popBackStack() },
                canAdd = featureEnabled("dictionary.add"),
                canEdit = featureEnabled("dictionary.edit"),
                canDelete = featureEnabled("dictionary.delete")
            )
        }

        if (featureEnabled("taziehCorrections.view")) composable(ROUTE_TAZIEH_CORRECTIONS) {
            TaziehCorrectionsScreen(
                onBack = { navController.popBackStack() },
                canAdd = featureEnabled("taziehCorrections.add"),
                canEdit = featureEnabled("taziehCorrections.edit"),
                canDelete = featureEnabled("taziehCorrections.delete"),
                canApply = featureEnabled("taziehCorrections.apply")
            )
        }

        if (featureEnabled("ai")) composable(ROUTE_AI_RESEARCH) {
            var aiCorpus by remember { mutableStateOf(emptyList<com.example.bookapp.data.SearchCorpusRow>()) }
            LaunchedEffect(Unit) { aiCorpus = db.searchDao().getSearchCorpus() }
            AiResearchScreen(
                corpus = aiCorpus,
                canAnalyze = featureEnabled("ai.analysis"),
                canSpeaker = featureEnabled("ai.speaker"),
                canGlossary = featureEnabled("ai.glossary"),
                canSemanticSearch = featureEnabled("ai.semanticSearch"),
                canTranscribe = featureEnabled("ai.transcription"),
                onOpenSection = { sectionId -> navController.navigate("text/$sectionId") },
                onBack = { navController.popBackStack() }
            )
        }

        if (featureEnabled("calendar.view")) composable(ROUTE_MUHARRAM_CALENDAR) {
            var suggestions by remember { mutableStateOf(listOf<MuharramTaziehSuggestion>()) }
            val countdowns = remember { com.example.bookapp.data.computeMuharramCountdowns() }
            val scope = androidx.compose.runtime.rememberCoroutineScope()
            LaunchedEffect(Unit) {
                if (countdowns != null) {
                    val allTaziehs = db.taziehDao().getAll()
                    val results = mutableListOf<MuharramTaziehSuggestion>()
                    com.example.bookapp.data.MUHARRAM_EVENTS.forEach { event ->
                        allTaziehs.filter { it.title.contains(event.matchKeyword) }.forEach { t ->
                            results.add(MuharramTaziehSuggestion(event.title, t.id, t.title))
                        }
                    }
                    suggestions = results
                }
            }
            MuharramCalendarScreen(
                countdowns = countdowns,
                suggestions = suggestions,
                showSuggestions = featureEnabled("calendar.suggestions"),
                onOpenTazieh = { taziehId ->
                    scope.launch {
                        val tazieh = db.taziehDao().getById(taziehId)
                        if (tazieh != null) navController.navigate("roles/$taziehId/${tazieh.title}")
                    }
                },
                onBack = { navController.popBackStack() }
            )
        }

        // مسیرهای مخصوص Admin (مدیریت محتوا، ویرایشگر، ورود JSON/Word، مدیریت
        // دسترسی، کاربران ویژه، لاگ دسترسی) در فایل جدا و مختص هر flavor است:
        // app/src/admin/java/.../AdminNavGraph.kt (پیاده‌سازی واقعی) و
        // app/src/viewer/java/.../AdminNavGraph.kt (بدون کد، خالی). به همین
        // دلیل کد این صفحات هرگز در خروجی Viewer کامپایل نمی‌شود.
        adminOnlyRoutes(navController = navController, db = db, context = context)

        dialogueFeatureRoutes(
            navController = navController,
            db = db,
            context = context,
            publicViewer = publicViewer,
            featureEnabled = { key -> featureEnabled(key) }
        )



        if (featureEnabled("read.view")) composable(ROUTE_FIELDS) {
            var fields by remember { mutableStateOf(emptyList<FieldCatalogItem>()) }
            LaunchedEffect(Unit) {
                val allFields = db.fieldDao().getAll()
                fields = allFields.map { field ->
                    FieldCatalogItem(
                        id = field.id,
                        title = field.title,
                        taziehCount = db.taziehDao().getByField(field.id).size
                    )
                }
            }
            FieldsScreen(
                items = fields,
                onOpen = { field -> navController.navigate("taziehs/${field.id}/${field.title}") },
                onBack = { navController.popBackStack() }
            )
        }

        if (featureEnabled("read.view")) composable(ROUTE_TAZIEHS) { backStackEntry ->
            val fieldId = backStackEntry.arguments?.getString("fieldId")?.toLongOrNull() ?: 0L
            var catalog by remember { mutableStateOf(emptyList<TaziehCatalogItem>()) }
            LaunchedEffect(fieldId) {
                val taziehs = db.taziehDao().getByField(fieldId)
                val fieldTitle = db.fieldDao().getAll().firstOrNull { it.id == fieldId }?.title ?: "زمینه"
                catalog = taziehs.map { t ->
                    val roles = db.roleDao().getByTazieh(t.id)
                    val hasAudio = roles.any { r -> db.sectionDao().getByRole(r.id).any { !it.audioUrl.isNullOrBlank() } }
                    TaziehCatalogItem(t.id, fieldId, fieldTitle, t.title, t.author, roles.size, hasAudio)
                }
            }
            TaziehCatalogScreen(
                items = catalog,
                initialFieldId = fieldId,
                onOpen = { item -> navController.navigate("roles/${item.id}/${java.net.URLEncoder.encode(item.title, "UTF-8")}") },
                onOpenGallery = { item ->
                    if (featureEnabled("gallery.view")) navController.navigate("tazieh_gallery/${item.id}")
                },
                onOpenDialogues = { item ->
                    if (featureEnabled("dialogues.view")) {
                        navController.navigate("dialogues/${item.id}/${android.net.Uri.encode(item.title)}")
                    }
                },
                showGallery = featureEnabled("gallery.view"),
                onBack = { navController.popBackStack() }
            )
        }

        if (featureEnabled("read.view")) composable(ROUTE_ROLES) { backStackEntry ->
            val taziehId = backStackEntry.arguments?.getString("taziehId")?.toLongOrNull() ?: 0L
            val taziehTitle = java.net.URLDecoder.decode(backStackEntry.arguments?.getString("taziehTitle") ?: "", "UTF-8")
            var roles by remember { mutableStateOf(listOf<com.example.bookapp.data.RoleEntity>()) }
            var myRoleId by remember { mutableStateOf<Long?>(null) }
            var roleItems by remember { mutableStateOf(listOf<ProfessionalRoleItem>()) }
            val scope = androidx.compose.runtime.rememberCoroutineScope()

            suspend fun reloadRoles() {
                roles = db.roleDao().getByTazieh(taziehId)
                myRoleId = Prefs.getMyRole(context, taziehId)
                roleItems = roles.map { role ->
                    val sections = db.sectionDao().getByRole(role.id)
                    ProfessionalRoleItem(
                        id = role.id,
                        title = role.title,
                        sectionCount = sections.size,
                        firstVerse = sections.firstOrNull()?.content?.lineSequence()?.firstOrNull { it.isNotBlank() }?.trim() ?: "",
                        isMine = role.id == myRoleId
                    )
                }
            }
            LaunchedEffect(taziehId) { reloadRoles() }

            ProfessionalRoleScreen(
                taziehTitle = taziehTitle,
                items = roleItems,
                onOpen = { item -> navController.navigate("sections/${item.id}/${item.title}") },
                onSetMine = { item ->
    if (featureEnabled("myRole.select")) {
        if (Prefs.getMyRole(context, taziehId) == item.id) {
            // اگر همین نقش قبلاً انتخاب شده، انتخاب را لغو کن
            Prefs.clearMyRole(context, taziehId)
        } else {
            // در غیر این صورت نقش جدید را ثبت کن
            Prefs.setMyRole(context, taziehId, item.id)
        }

        myRoleId = Prefs.getMyRole(context, taziehId)
        scope.launch {
            reloadRoles()
        }
    }
},
                onCompare = { navController.navigate("compare/$taziehId") },
                onOpenGallery = { if (featureEnabled("gallery.view")) navController.navigate("tazieh_gallery/$taziehId") },
                showGallery = featureEnabled("gallery.view"),
                onBack = { navController.popBackStack() },
                readOnly = publicViewer,
                showCompare = featureEnabled("compare.view"),
                canSelectMine = featureEnabled("myRole.select")
            )
        }

        if (featureEnabled("read.view")) composable(ROUTE_TAZIEH_INDEX) { backStackEntry ->
            val taziehId = backStackEntry.arguments?.getString("taziehId")?.toLongOrNull() ?: 0L
            val taziehTitle = backStackEntry.arguments?.getString("taziehTitle") ?: ""
            var indexItems by remember { mutableStateOf(listOf<TaziehIndexItem>()) }
            var taziehAuthor by remember { mutableStateOf<String?>(null) }
            var taziehAuthorEmail by remember { mutableStateOf<String?>(null) }
            val scope = androidx.compose.runtime.rememberCoroutineScope()

            suspend fun reloadIndex() {
                val roles = db.roleDao().getByTazieh(taziehId)
                indexItems = roles.map { role ->
                    val firstSection = db.sectionDao().getByRole(role.id).firstOrNull()
                    val firstVerse = firstSection?.content
                        ?.lineSequence()
                        ?.firstOrNull { it.isNotBlank() }
                        ?.trim() ?: ""
                    TaziehIndexItem(roleId = role.id, roleTitle = role.title, firstVerse = firstVerse)
                }
                val tazieh = db.taziehDao().getById(taziehId)
                taziehAuthor = tazieh?.author
                taziehAuthorEmail = tazieh?.authorEmail
            }
            LaunchedEffect(taziehId) { reloadIndex() }

            TaziehIndexScreen(
                taziehTitle = taziehTitle,
                author = taziehAuthor,
                authorEmail = taziehAuthorEmail,
                items = indexItems,
                onItemClick = { item -> navController.navigate("text_pager/${item.roleId}/0") },
                onExportPdf = {
                    if (!exportAllowed("pdf", "pdf.create", "pdf.save")) notifyExportDenied()
                    else scope.launch {
                        val roles = db.roleDao().getByTazieh(taziehId)
                        val rolesWithSections = roles.map { role ->
                            role.title to db.sectionDao().getByRole(role.id)
                        }
                        com.example.bookapp.data.exportTaziehToPdf(context, taziehTitle, rolesWithSections)
                    }
                },
                onOpenGallery = { if (featureEnabled("gallery.view")) navController.navigate("tazieh_gallery/$taziehId") },
                showGallery = featureEnabled("gallery.view"),
                showPdf = featureEnabled("pdf") && featureEnabled("pdf.create") && featureEnabled("pdf.save"),
                onRename = { item, newTitle ->
                    scope.launch {
                        db.roleDao().updateTitle(item.roleId, newTitle)
                        reloadIndex()
                    }
                },
                onMove = { index, direction ->
                    scope.launch {
                        val sorted = sortTaziehIndexItems(indexItems)
                        val targetIndex = index + direction
                        if (targetIndex in sorted.indices) {
                            val roleA = db.roleDao().getById(sorted[index].roleId)
                            val roleB = db.roleDao().getById(sorted[targetIndex].roleId)
                            db.roleDao().updateOrderIndex(roleA.id, roleB.orderIndex)
                            db.roleDao().updateOrderIndex(roleB.id, roleA.orderIndex)
                            reloadIndex()
                        }
                    }
                },
                onBack = { navController.popBackStack() }
            )
        }

        if (featureEnabled("gallery.view")) composable(ROUTE_TAZIEH_GALLERY) { backStackEntry ->
            val taziehId = backStackEntry.arguments?.getString("taziehId")?.toLongOrNull() ?: 0L
            var taziehTitle by remember { mutableStateOf("تعزیه") }
            var images by remember { mutableStateOf(listOf<TaziehImageItem>()) }
            var galleryError by remember { mutableStateOf<String?>(null) }
            val scope = androidx.compose.runtime.rememberCoroutineScope()

            suspend fun reloadImages() {
                images = db.taziehImageDao().getByTazieh(taziehId).map {
                    TaziehImageItem(it.id, it.filePath, it.caption)
                }
            }
            LaunchedEffect(taziehId) {
                taziehTitle = db.taziehDao().getById(taziehId)?.title.orEmpty().ifBlank { "تعزیه" }
                reloadImages()
            }

            TaziehGalleryScreen(
                taziehTitle = taziehTitle,
                images = images,
                onAddImage = { uri ->
                    scope.launch {
                        galleryError = null
                        runCatching {
                            check(db.taziehDao().getById(taziehId) != null) { "تعزیه انتخاب‌شده پیدا نشد." }
                            val path = com.example.bookapp.data.copyImageToAppStorage(context, uri)
                                ?: error("تصویر از گالری گوشی خوانده نشد یا در حافظه برنامه ذخیره نشد.")
                            val id = db.taziehImageDao().insert(
                                com.example.bookapp.data.TaziehImageEntity(taziehId = taziehId, filePath = path)
                            )
                            check(id > 0L) { "ثبت تصویر در پایگاه داده انجام نشد." }
                            reloadImages()
                        }.onFailure { e ->
                            galleryError = e.message ?: "افزودن تصویر ناموفق بود."
                            android.util.Log.e("TaziehGallery", "افزودن تصویر ناموفق بود", e)
                        }
                    }
                },
                onDeleteImage = { image ->
                    if (featureEnabled("gallery.delete")) scope.launch {
                        db.taziehImageDao().delete(image.id)
                        com.example.bookapp.data.deleteImageFromAppStorage(image.filePath)
                        reloadImages()
                    }
                },
                onUpdateCaption = { image, caption ->
                    if (featureEnabled("gallery.edit")) scope.launch {
                        db.taziehImageDao().updateCaption(image.id, caption)
                        reloadImages()
                    }
                },
                readOnly = publicViewer,
                canAddImage = featureEnabled("gallery.add"),
                canDeleteImage = featureEnabled("gallery.delete"),
                canEditCaption = featureEnabled("gallery.edit"),
                canLargePreview = featureEnabled("gallery.largePreview"),
                errorMessage = galleryError,
                onBack = { navController.popBackStack() }
            )
        }

        if (featureEnabled("compare.view")) composable(ROUTE_COMPARE) { backStackEntry ->
            val taziehId = backStackEntry.arguments?.getString("taziehId")?.toLongOrNull() ?: 0L
            var taziehTitle by remember { mutableStateOf("") }
            var roles by remember { mutableStateOf(listOf<com.example.bookapp.data.RoleEntity>()) }
            var compareSections by remember { mutableStateOf(listOf<CompareSectionItem>()) }
            var roleSections by remember { mutableStateOf(emptyMap<Long, List<SectionEntity>>()) }
            LaunchedEffect(taziehId) {
                val tazieh = db.taziehDao().getById(taziehId)
                taziehTitle = tazieh?.title.orEmpty()
                roles = db.roleDao().getByTazieh(taziehId)
                val loadedSections = roles.associate { role -> role.id to db.sectionDao().getByRole(role.id) }
                roleSections = loadedSections
                compareSections = roles.flatMap { role ->
                    loadedSections[role.id].orEmpty().map { section -> CompareSectionItem(section, role.title) }
                }
            }
            CompareScreen(
                taziehTitle = taziehTitle.ifBlank { "تعزیه" },
                roles = roles,
                sections = compareSections,
                roleSections = roleSections,
                onBack = { navController.popBackStack() }
            )
        }

        if (featureEnabled("read.view")) composable(ROUTE_SECTIONS) { backStackEntry ->
            val roleId = backStackEntry.arguments?.getString("roleId")?.toLongOrNull() ?: 0L
            val roleTitle = backStackEntry.arguments?.getString("roleTitle") ?: ""
            var items by remember { mutableStateOf(listOf<ListItemData>()) }
            val scope = androidx.compose.runtime.rememberCoroutineScope()
            LaunchedEffect(roleId) {
                items = db.sectionDao().getByRole(roleId).map { ListItemData(it.id, it.title) }
            }
            var taziehIdForCompare by remember { mutableStateOf<Long?>(null) }
            LaunchedEffect(roleId) {
                taziehIdForCompare = db.roleDao().getById(roleId).taziehId
            }
            GenericListScreen(
                screenTitle = roleTitle,
                items = items,
                onItemClick = { clicked ->
                    val index = items.indexOfFirst { it.id == clicked.id }.coerceAtLeast(0)
                    navController.navigate("text_pager/$roleId/$index")
                },
                onBack = { navController.popBackStack() },
                // فعال‌سازی رنگ‌بندی اختصاصی کارت‌های بخش‌ها
                colorfulCards = true,
               topBarAction = if (featureEnabled("compare.view")) {
    {
        TextButton(
            onClick = {
                taziehIdForCompare?.let {
                    navController.navigate("compare/$it")
                }
            },
            enabled = taziehIdForCompare != null
        ) {
            Text("نمایش چند نقش")
        }
    }
} else null,
                floatingAction = if (featureEnabled("pdf") && featureEnabled("pdf.create") && featureEnabled("pdf.save")) {
                    {
                        androidx.compose.material3.ExtendedFloatingActionButton(
                            text = { androidx.compose.material3.Text("خروجی PDF") },
                            icon = { androidx.compose.material3.Icon(Icons.Filled.Share, contentDescription = null) },
                            onClick = {
                                if (!exportAllowed("pdf", "pdf.create", "pdf.save")) notifyExportDenied()
                                else scope.launch {
                                    val fullSections = db.sectionDao().getByRole(roleId)
                                    com.example.bookapp.data.exportRoleToPdf(context, roleTitle, fullSections)
                                }
                            }
                        )
                    }
                } else null
            )
        }

        if (featureEnabled("read.view")) composable(ROUTE_TEXT_PAGER) { backStackEntry ->
            val roleId = backStackEntry.arguments?.getString("roleId")?.toLongOrNull() ?: 0L
            val startIndex = backStackEntry.arguments?.getString("startIndex")?.toIntOrNull() ?: 0
            var sections by remember { mutableStateOf(listOf<SectionEntity>()) }
            var bookmarkVersion by remember { mutableIntStateOf(0) }
            var breadcrumb by remember { mutableStateOf(Triple<String?, String?, String?>(null, null, null)) }
            val scope = androidx.compose.runtime.rememberCoroutineScope()
            LaunchedEffect(roleId) {
                sections = db.sectionDao().getByRole(roleId)
                val role = db.roleDao().getById(roleId)
                val tazieh = db.taziehDao().getById(role.taziehId)
                val fieldEntity = tazieh?.let { t -> db.fieldDao().getAll().find { it.id == t.fieldId } }
                breadcrumb = Triple(fieldEntity?.title, tazieh?.title, role.title)
            }
            if (sections.isNotEmpty()) {
                TextPagerScreen(
                    sections = sections,
                    startIndex = startIndex,
                    isBookmarked = { id -> bookmarkVersion.let { Prefs.isBookmarked(context, id) } },
                    onToggleBookmark = { id ->
                        Prefs.toggleBookmark(context, id)
                        bookmarkVersion++
                    },
                    onPageShown = { id ->
                        Prefs.addRecent(context, id)
                        Prefs.markSectionRead(context, id)
                    },
                    onOpenSearch = { navController.navigate(ROUTE_SEARCH) },
                    onOpenSettings = { navController.navigate(ROUTE_SETTINGS) },
                    onAttachAudio = { sectionId, uri ->
                        scope.launch {
                            val path = com.example.bookapp.data.copyAudioToAppStorage(context, uri)
                            if (path != null) {
                                db.sectionDao().updateAudioUrl(sectionId, path)
                                sections = db.sectionDao().getByRole(roleId)
                            }
                        }
                    },
                    onRemoveAudio = { sectionId ->
                        scope.launch {
                            sections.find { it.id == sectionId }?.audioUrl?.let {
                                com.example.bookapp.data.deleteAudioFromAppStorage(it)
                            }
                            db.sectionDao().updateAudioUrl(sectionId, null)
                            sections = db.sectionDao().getByRole(roleId)
                        }
                    },
                    footnotesForSection = { id -> db.footnoteDao().getBySection(id) },
                    onAddFootnote = { id, term, explanation ->
                        if (!featureEnabled("footnotes.add")) {
                            Result.failure(IllegalStateException("قابلیت پاورقی در سیاست دسترسی فعلی فعال نیست."))
                        } else runCatching {
                            val cleanTerm = term.trim()
                            val cleanExplanation = explanation.trim()
                            require(cleanTerm.isNotEmpty()) { "واژه یا عبارت خالی است" }
                            require(cleanExplanation.isNotEmpty()) { "توضیح پاورقی خالی است" }
                            val uid = com.example.bookapp.data.ContentUid.new()
                            val insertedId = db.footnoteDao().insert(com.example.bookapp.data.FootnoteEntity(
                                sectionId = id, term = cleanTerm, explanation = cleanExplanation, uid = uid
                            ))
                            require(insertedId > 0L) { "شناسه پاورقی ایجاد نشد" }
                            require(db.footnoteDao().getByUid(uid) != null) { "پاورقی در پایگاه داده تأیید نشد" }
                        }
                    },
                    onEditFootnote = { id, fn, term, explanation -> if (featureEnabled("footnotes.edit")) scope.launch {
                        db.footnoteDao().update(fn.copy(sectionId = id, term = term.trim(), explanation = explanation.trim()))
                    } },
                    onDeleteFootnote = { _, fn -> if (featureEnabled("footnotes.delete")) scope.launch { db.footnoteDao().delete(fn.id) } },
                    canViewFootnotes = featureEnabled("footnotes.view"),
                    canAddFootnote = featureEnabled("footnotes.add"),
                    canEditFootnote = featureEnabled("footnotes.edit"),
                    canDeleteFootnote = featureEnabled("footnotes.delete"),
                    canAddFootnoteToDictionary = featureEnabled("footnoteSync.dictionary") && featureEnabled("dictionary.add"),
                    canBookmark = featureEnabled("bookmarks.add"),
                    canBookmarkDelete = featureEnabled("bookmarks.delete"),
                    canAudio = featureEnabled("audio.play"),
                    canTts = featureEnabled("tts.play"),
                    canCopy = featureEnabled("copy.text"),
                    canShare = featureEnabled("share.content"),
                    fieldTitle = breadcrumb.first,
                    taziehTitle = breadcrumb.second,
                    roleTitle = breadcrumb.third,
                    onBack = { navController.popBackStack() }
                )
            }
        }

        if (featureEnabled("read.view")) composable(ROUTE_TEXT) { backStackEntry ->
            val sectionId = backStackEntry.arguments?.getString("sectionId")?.toLongOrNull() ?: 0L
            var title by remember { mutableStateOf("") }
            var content by remember { mutableStateOf("") }
            var bookmarked by remember { mutableStateOf(Prefs.isBookmarked(context, sectionId)) }
            var relatedSections by remember { mutableStateOf(listOf<com.example.bookapp.data.SearchResult>()) }
            var sectionAudioUrl by remember { mutableStateOf<String?>(null) }
            var footnotes by remember { mutableStateOf(listOf<com.example.bookapp.data.FootnoteEntity>()) }
            var glossaryOccurrences by remember { mutableStateOf(emptyList<com.example.bookapp.data.GlossaryOccurrence>()) }
            var footnoteError by remember { mutableStateOf<String?>(null) }
            var siblingSections by remember { mutableStateOf(listOf<com.example.bookapp.data.SectionEntity>()) }
            var siblingIndex by remember { mutableStateOf(-1) }
            var breadcrumb by remember { mutableStateOf(Triple<String?, String?, String?>(null, null, null)) }
            val scope = androidx.compose.runtime.rememberCoroutineScope()

            suspend fun reloadFootnotes() {
                footnotes = db.footnoteDao().getBySection(sectionId)
            }

            LaunchedEffect(sectionId) {
                val section = db.sectionDao().getById(sectionId)
                title = section.title
                content = section.content
                bookmarked = Prefs.isBookmarked(context, sectionId)
                sectionAudioUrl = section.audioUrl
                Prefs.addRecent(context, sectionId)
                Prefs.markSectionRead(context, sectionId)
                relatedSections = db.searchDao().getRelatedByTitle(section.title, sectionId)
                reloadFootnotes()
                runCatching {
                    glossaryOccurrences = com.example.bookapp.data.GlossaryTextLinker.link(
                        section.content,
                        db.glossaryDao().allTerms(),
                        db.glossaryDao().allSpellings(),
                        db.glossaryDao().allMeanings()
                    )
                }.onFailure { android.util.Log.w("TaziehGlossary", "پیوند خودکار واژه‌نامه در این بخش در دسترس نیست", it) }
                siblingSections = db.sectionDao().getByRole(section.roleId)
                siblingIndex = siblingSections.indexOfFirst { it.id == sectionId }
                val role = db.roleDao().getById(section.roleId)
                val tazieh = db.taziehDao().getById(role.taziehId)
                val fieldEntity = tazieh?.let { t -> db.fieldDao().getAll().find { it.id == t.fieldId } }
                breadcrumb = Triple(fieldEntity?.title, tazieh?.title, role.title)
            }
            TextScreen(
                title = title,
                content = content,
                isBookmarked = bookmarked,
                onToggleBookmark = {
                    if (bookmarked && featureEnabled("bookmarks.delete")) bookmarked = Prefs.toggleBookmark(context, sectionId)
                    else if (!bookmarked && featureEnabled("bookmarks.add")) bookmarked = Prefs.toggleBookmark(context, sectionId)
                },
                canBookmark = featureEnabled("bookmarks.add"),
                canAudio = featureEnabled("audio.play"),
                canTts = featureEnabled("tts.play"),
                canCopy = featureEnabled("copy.text"),
                canShare = featureEnabled("share.content"),
                canNavigate = featureEnabled("read.navigate"),
                sectionId = sectionId,
                audioUrl = sectionAudioUrl,
                relatedSections = relatedSections,
                onRelatedClick = { related -> navController.navigate("text/${related.sectionId}") },
                footnotes = if (featureEnabled("footnotes")) footnotes else emptyList(),
                glossaryOccurrences = if (featureEnabled("dictionary.view")) glossaryOccurrences else emptyList(),
                canViewFootnotes = featureEnabled("footnotes.view"),
                canAddFootnote = featureEnabled("footnotes.add"),
                canEditFootnote = featureEnabled("footnotes.edit"),
                canDeleteFootnote = featureEnabled("footnotes.delete"),
                canAddFootnoteToDictionary = featureEnabled("footnoteSync.dictionary") && featureEnabled("dictionary.add"),
                saveError = footnoteError,
                onAddFootnote = { term, explanation ->
                    if (!featureEnabled("footnotes.add")) {
                        Result.failure(IllegalStateException("قابلیت پاورقی در سیاست دسترسی فعلی فعال نیست."))
                    } else {
                        runCatching {
                            val section = db.sectionDao().getById(sectionId)
                            val cleanTerm = term.trim()
                            val cleanExplanation = explanation.trim()
                            require(cleanTerm.isNotEmpty()) { "واژه یا عبارت خالی است" }
                            require(cleanExplanation.isNotEmpty()) { "توضیح پاورقی خالی است" }
                            val normalizeFootnoteTerm: (String) -> String = { value ->
                                value.trim().replace('ي', 'ی').replace('ك', 'ک').replace('ۀ', 'ه').replace('ة', 'ه').replace(Regex("\\s+"), " ")
                            }
                            val duplicate = db.footnoteDao().getBySection(section.id).any {
                                normalizeFootnoteTerm(it.term) == normalizeFootnoteTerm(cleanTerm)
                            }
                            require(!duplicate) { "این واژه قبلاً در پاورقی همین بخش ثبت شده است." }
                            val uid = com.example.bookapp.data.ContentUid.new()
                            val insertedId = db.footnoteDao().insert(
                                com.example.bookapp.data.FootnoteEntity(
                                    sectionId = section.id,
                                    term = cleanTerm,
                                    explanation = cleanExplanation,
                                    uid = uid
                                )
                            )
                            require(insertedId > 0L) { "شناسه پاورقی ایجاد نشد" }
                            val saved = db.footnoteDao().getByUid(uid)
                            require(saved != null && saved.id == insertedId && saved.sectionId == section.id) {
                                "پاورقی در پایگاه داده تأیید نشد"
                            }
                            reloadFootnotes()
                            footnoteError = null
                        }.onFailure {
                            footnoteError = "ذخیره پاورقی انجام نشد: ${it.message ?: "خطای نامشخص"}"
                        }
                    }
                },
                onEditFootnote = { fn, term, explanation -> if (featureEnabled("footnotes.edit")) scope.launch {
                    footnoteError = null
                    runCatching {
                        val cleanTerm = term.trim()
                        val cleanExplanation = explanation.trim()
                        require(cleanTerm.isNotEmpty()) { "واژه یا عبارت خالی است" }
                        require(cleanExplanation.isNotEmpty()) { "توضیح پاورقی خالی است" }
                        val norm: (String) -> String = { value -> value.trim().replace('ي', 'ی').replace('ك', 'ک').replace('ۀ', 'ه').replace('ة', 'ه').replace(Regex("\\s+"), " ") }
                        val duplicate = db.footnoteDao().getBySection(sectionId).any { it.id != fn.id && norm(it.term) == norm(cleanTerm) }
                        require(!duplicate) { "این واژه قبلاً در پاورقی همین بخش ثبت شده است." }
                        db.footnoteDao().update(fn.copy(term = cleanTerm, explanation = cleanExplanation))
                        reloadFootnotes()
                    }.onFailure { footnoteError = "ویرایش پاورقی انجام نشد: ${it.message ?: "خطای نامشخص"}" }
                } },
                onDeleteFootnote = { fn -> if (featureEnabled("footnotes.delete")) scope.launch {
                    footnoteError = null
                    runCatching {
                        db.footnoteDao().delete(fn.id)
                        reloadFootnotes()
                    }.onFailure { footnoteError = "حذف پاورقی انجام نشد: ${it.message ?: "خطای نامشخص"}" }
                } },
                onOpenSearch = { navigateIfAllowed("search", ROUTE_SEARCH) },
                onOpenSettings = { navController.navigate(ROUTE_SETTINGS) },
                fieldTitle = breadcrumb.first,
                taziehTitle = breadcrumb.second,
                roleTitle = breadcrumb.third,
                darkMode = darkMode,
                onDarkModeChange = onDarkModeChange,
                fontScale = fontScale,
                onFontScaleChange = onFontScaleChange,
                fontChoice = fontChoice,
                onFontChoiceChange = onFontChoiceChange,
                hasPrevSection = siblingIndex > 0,
                hasNextSection = siblingIndex in 0 until siblingSections.size - 1,
                onPrevSection = {
                    if (siblingIndex > 0) navController.navigate("text/${siblingSections[siblingIndex - 1].id}")
                },
                onNextSection = {
                    if (siblingIndex in 0 until siblingSections.size - 1) navController.navigate("text/${siblingSections[siblingIndex + 1].id}")
                },
                onAttachAudio = { uri ->
                    scope.launch {
                        val path = com.example.bookapp.data.copyAudioToAppStorage(context, uri)
                        if (path != null) {
                            db.sectionDao().updateAudioUrl(sectionId, path)
                            sectionAudioUrl = path
                        }
                    }
                },
                onRemoveAudio = {
                    scope.launch {
                        sectionAudioUrl?.let { com.example.bookapp.data.deleteAudioFromAppStorage(it) }
                        db.sectionDao().updateAudioUrl(sectionId, null)
                        sectionAudioUrl = null
                    }
                },
                onBack = { navController.popBackStack() }
            )
        }
        }
    }
}
