package com.example.bookapp.ui

import android.content.Context
import android.net.Uri
import android.widget.Toast
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.navigation.NavGraphBuilder
import androidx.navigation.NavHostController
import androidx.navigation.compose.composable
import androidx.room.withTransaction
import com.example.bookapp.data.AppDatabase
import com.example.bookapp.data.DialogueRepository
import com.example.bookapp.data.DialogueRoleSelectionEntity
import com.example.bookapp.data.DialogueTurnEntity
import com.example.bookapp.data.DialogueTurnMetadataEntity
import com.example.bookapp.data.ViewerAccessPolicy
import com.example.bookapp.data.exportDialogueToPdf
import com.example.bookapp.ui.screens.DialogueBuilderScreen
import com.example.bookapp.ui.screens.DialogueReaderScreen
import com.example.bookapp.ui.screens.DialogueSummary
import com.example.bookapp.ui.screens.DialogueTurnDisplay
import com.example.bookapp.ui.screens.DialoguesScreen
import com.example.bookapp.ui.screens.SectionPickerItem
import kotlinx.coroutines.launch

private const val ROUTE_DIALOGUES_SHARED = "dialogues/{taziehId}/{taziehTitle}"
private const val ROUTE_DIALOGUE_BUILDER_SHARED = "dialogue_builder/{taziehId}/{taziehTitle}"
private const val ROUTE_DIALOGUE_READER_SHARED = "dialogue_reader/{dialogueId}"

/**
 * مسیرهای مشترک مطالعهٔ چندنقشی برای Admin و Viewer.
 *
 * Admin همهٔ گفت‌وگوها را مدیریت می‌کند. Viewer فقط گفت‌وگوهایی را می‌بیند
 * و تغییر می‌دهد که ownerInstallationId آن‌ها متعلق به همین نصب باشد.
 * امکانات مدیریت اجرای گروهی عمداً در این بخش قرار نگرفته‌اند.
 */
fun NavGraphBuilder.dialogueFeatureRoutes(
    navController: NavHostController,
    db: AppDatabase,
    context: Context,
    publicViewer: Boolean,
    featureEnabled: (String) -> Boolean
) {
    val isAdmin = !publicViewer
    val ownerId = if (publicViewer) ViewerAccessPolicy.installationId(context) else ""

    if (featureEnabled("dialogues.view")) {
        composable(ROUTE_DIALOGUES_SHARED) { entry ->
            val taziehId = entry.arguments?.getString("taziehId")?.toLongOrNull() ?: 0L
            val taziehTitle = entry.arguments?.getString("taziehTitle").orEmpty()
            var dialogues by remember(taziehId, ownerId) {
                mutableStateOf(emptyList<DialogueSummary>())
            }
            var loadError by remember(taziehId, ownerId) {
                mutableStateOf<String?>(null)
            }
            val scope = rememberCoroutineScope()

            suspend fun reload() {
                val rows = DialogueRepository.loadDialogues(
                    db = db,
                    taziehId = taziehId,
                    isAdmin = isAdmin,
                    ownerInstallationId = ownerId
                )
                dialogues = rows.map { row ->
                    DialogueSummary(
                        id = row.id,
                        title = row.title,
                        turnsCount = db.dialogueTurnDao().getByDialogue(row.id).size
                    )
                }
            }

            LaunchedEffect(taziehId, ownerId) {
                runCatching { reload() }.onFailure {
                    loadError = it.message ?: "بارگذاری گفتگوها انجام نشد."
                }
            }

            DialoguesScreen(
                taziehTitle = taziehTitle,
                dialogues = dialogues,
                onOpenDialogue = { dialogue ->
                    navController.navigate("dialogue_reader/${dialogue.id}")
                },
                onAddVersion = { dialogue ->
                    navController.navigate("dialogue_reader/${dialogue.id}")
                    navController.currentBackStackEntry?.savedStateHandle?.set(
                        "openAddTurnOnEnter",
                        true
                    )
                },
                onEditDialogue = { dialogue, newTitle ->
                    scope.launch {
                        runCatching {
                            if (!featureEnabled("dialogues.edit")) {
                                throw SecurityException("مجوز ویرایش گفتگو فعال نیست.")
                            }
                            DialogueRepository.renameDialogue(
                                db = db,
                                dialogueId = dialogue.id,
                                title = newTitle,
                                isAdmin = isAdmin,
                                ownerInstallationId = ownerId
                            )
                            reload()
                        }.onFailure {
                            Toast.makeText(
                                context,
                                it.message ?: "ویرایش گفتگو انجام نشد.",
                                Toast.LENGTH_LONG
                            ).show()
                        }
                    }
                },
                onDeleteDialogue = { dialogue ->
                    scope.launch {
                        runCatching {
                            if (!featureEnabled("dialogues.delete")) {
                                throw SecurityException("مجوز حذف گفتگو فعال نیست.")
                            }
                            DialogueRepository.deleteDialogue(
                                db = db,
                                dialogueId = dialogue.id,
                                isAdmin = isAdmin,
                                ownerInstallationId = ownerId
                            )
                            reload()
                        }.onFailure {
                            Toast.makeText(
                                context,
                                it.message ?: "حذف گفتگو انجام نشد.",
                                Toast.LENGTH_LONG
                            ).show()
                        }
                    }
                },
                onCreateNew = {
                    if (featureEnabled("dialogues.create")) {
                        if (publicViewer && ownerId.isBlank()) {
                            Toast.makeText(
                                context,
                                "شناسه نصب برای ساخت گفتگوی شخصی در دسترس نیست.",
                                Toast.LENGTH_LONG
                            ).show()
                        } else {
                            navController.navigate(
                                "dialogue_builder/$taziehId/${Uri.encode(taziehTitle)}"
                            )
                        }
                    }
                },
                onBack = { navController.popBackStack() },
                readOnly = false,
                canCreate = featureEnabled("dialogues.create"),
                canEdit = isAdmin || featureEnabled("dialogues.edit"),
                canDelete = isAdmin || featureEnabled("dialogues.delete")
            )

            loadError?.let { message ->
                AlertDialog(
                    onDismissRequest = { loadError = null },
                    title = { Text("خطا در بارگذاری گفتگوها") },
                    text = { Text(message) },
                    confirmButton = {
                        TextButton(
                            onClick = {
                                loadError = null
                                scope.launch {
                                    runCatching { reload() }.onFailure {
                                        loadError = it.message ?: "بارگذاری گفتگوها انجام نشد."
                                    }
                                }
                            }
                        ) {
                            Text("تلاش دوباره")
                        }
                    },
                    dismissButton = {
                        TextButton(onClick = { loadError = null }) {
                            Text("بستن")
                        }
                    }
                )
            }
        }
    }

    if (featureEnabled("dialogues.create")) {
        composable(ROUTE_DIALOGUE_BUILDER_SHARED) { entry ->
            val taziehId = entry.arguments?.getString("taziehId")?.toLongOrNull() ?: 0L
            val taziehTitle = entry.arguments?.getString("taziehTitle").orEmpty()
            var sections by remember(taziehId) {
                mutableStateOf(emptyList<SectionPickerItem>())
            }
            var loading by remember(taziehId) { mutableStateOf(true) }
            var error by remember(taziehId) { mutableStateOf<String?>(null) }
            val scope = rememberCoroutineScope()

            LaunchedEffect(taziehId) {
                loading = true
                error = null
                runCatching {
                    DialogueRepository.loadSectionSources(db, taziehId)
                }.onSuccess {
                    sections = it
                }.onFailure {
                    error = it.message ?: "بارگذاری نسخه‌ها و نقش‌های تعزیه انجام نشد."
                }
                loading = false
            }

            when {
                loading -> {
                    Text(
                        "در حال بارگذاری نسخه‌ها و نقش‌های تعزیه…",
                        modifier = Modifier.padding(20.dp)
                    )
                }

                error != null -> {
                    Column(Modifier.padding(20.dp)) {
                        Text(
                            error.orEmpty(),
                            color = MaterialTheme.colorScheme.error
                        )
                        TextButton(onClick = { navController.popBackStack() }) {
                            Text("بازگشت")
                        }
                    }
                }

                sections.isEmpty() -> {
                    Column(Modifier.padding(20.dp)) {
                        Text("برای این تعزیه نسخهٔ آرشیوی دارای بخش پیدا نشد.")
                        TextButton(onClick = { navController.popBackStack() }) {
                            Text("بازگشت")
                        }
                    }
                }

                else -> {
                    DialogueBuilderScreen(
                        allSections = sections,
                        onBack = { navController.popBackStack() },
                        onSave = { dialogueTitle: String, orderedSections: List<SectionPickerItem> ->
                            scope.launch {
                                runCatching {
                                    if (!featureEnabled("dialogues.create")) {
                                        throw SecurityException("مجوز ساخت گفتگو فعال نیست.")
                                    }
                                    if (publicViewer && ownerId.isBlank()) {
                                        throw SecurityException(
                                            "شناسه نصب برای ثبت گفتگوی شخصی در دسترس نیست."
                                        )
                                    }
                                    DialogueRepository.createDialogue(
                                        db = db,
                                        taziehId = taziehId,
                                        title = dialogueTitle,
                                        orderedSections = orderedSections,
                                        ownerInstallationId = ownerId
                                    )
                                }.onSuccess {
                                    navController.navigate(
                                        "dialogues/$taziehId/${Uri.encode(taziehTitle)}"
                                    ) {
                                        popUpTo(ROUTE_DIALOGUE_BUILDER_SHARED) {
                                            inclusive = true
                                        }
                                        launchSingleTop = true
                                    }
                                }.onFailure {
                                    Toast.makeText(
                                        context,
                                        it.message ?: "ذخیره گفتگو انجام نشد.",
                                        Toast.LENGTH_LONG
                                    ).show()
                                }
                            }
                        }
                    )
                }
            }
        }
    }

    if (featureEnabled("dialogues.view")) {
        composable(ROUTE_DIALOGUE_READER_SHARED) { entry ->
            val dialogueId = entry.arguments
                ?.getString("dialogueId")
                ?.toLongOrNull() ?: 0L

            var dialogueTitle by remember(dialogueId) {
                mutableStateOf("در حال بارگذاری گفتگو…")
            }
            var turns by remember(dialogueId) {
                mutableStateOf(emptyList<DialogueTurnDisplay>())
            }
            var allowedSections by remember(dialogueId) {
                mutableStateOf(emptyList<SectionPickerItem>())
            }
            var availableSourceSections by remember(dialogueId) {
                mutableStateOf(emptyList<SectionPickerItem>())
            }
            var roleOrder by remember(dialogueId) {
                mutableStateOf(emptyList<Long>())
            }
            var showAddTurn by remember(dialogueId) { mutableStateOf(false) }
            LaunchedEffect(dialogueId) {
                if (entry.savedStateHandle.remove<Boolean>("openAddTurnOnEnter") == true) {
                    showAddTurn = true
                }
            }
            var canEdit by remember(dialogueId) { mutableStateOf(false) }
            var loadError by remember(dialogueId) { mutableStateOf<String?>(null) }
            val scope = rememberCoroutineScope()

            suspend fun ensureTurnMetadata(
                turnId: Long,
                fallbackOrder: Int
            ): DialogueTurnMetadataEntity {
                val dao = db.dialogueTurnMetadataDao()
                return dao.getByTurn(turnId) ?: DialogueTurnMetadataEntity(
                    turnId = turnId,
                    rehearsalOrderIndex = fallbackOrder
                ).also { dao.upsert(it) }
            }

            suspend fun reloadTurns() {
                loadError = null
                val dialogue = DialogueRepository.requireAccessibleDialogue(
                    db = db,
                    dialogueId = dialogueId,
                    isAdmin = isAdmin,
                    ownerInstallationId = ownerId
                )
                dialogueTitle = dialogue.title
                canEdit = isAdmin || featureEnabled("dialogues.edit")

                val allowed = DialogueRepository.loadAllowedSections(db, dialogue)
                allowedSections = allowed
                availableSourceSections = DialogueRepository.loadSectionSources(
                    db, dialogue.taziehId
                )
                roleOrder = allowed.map { it.roleId }.distinct()
                val allowedBySectionId = allowed.associateBy { it.sectionId }

                val sourceTurns = db.dialogueTurnDao().getByDialogue(dialogueId)
                val metadataById = sourceTurns.mapIndexed { index, source ->
                    source.id to ensureTurnMetadata(source.id, index)
                }.toMap()

                val orderedTurns = sourceTurns.sortedWith(
                    compareBy<DialogueTurnEntity> {
                        metadataById[it.id]?.rehearsalOrderIndex ?: it.orderIndex
                    }.thenBy { it.orderIndex }.thenBy { it.id }
                )

                turns = orderedTurns.mapNotNull { source ->
                    val section = runCatching {
                        db.sectionDao().getById(source.sectionId)
                    }.getOrNull() ?: return@mapNotNull null
                    if (section.roleId !in roleOrder) return@mapNotNull null
                    val sourceInfo = allowedBySectionId[section.id]
                        ?: return@mapNotNull null
                    val role = runCatching {
                        db.roleDao().getById(section.roleId)
                    }.getOrNull() ?: return@mapNotNull null
                    DialogueTurnDisplay(
                        turnId = source.id,
                        sectionId = section.id,
                        roleTitle = role.title,
                        sectionTitle = section.title,
                        content = section.content,
                        roleId = role.id,
                        versionLabel = sourceInfo.versionLabel,
                        rehearsalOrderIndex = metadataById[source.id]
                            ?.rehearsalOrderIndex ?: source.orderIndex
                    )
                }
            }

            LaunchedEffect(dialogueId, ownerId) {
                runCatching { reloadTurns() }.onFailure {
                    loadError = it.message ?: "بارگذاری گفتگو انجام نشد."
                    dialogueTitle = "گفتگو در دسترس نیست"
                    turns = emptyList()
                    allowedSections = emptyList()
                    roleOrder = emptyList()
                }
            }

            DialogueReaderScreen(
                dialogueTitle = dialogueTitle,
                turns = turns,
                roleOrder = roleOrder,
                onBack = { navController.popBackStack() },
                readOnly = !canEdit,
                showPdf = featureEnabled("pdf") &&
                    featureEnabled("pdf.create") &&
                    featureEnabled("pdf.save"),
                onExportPdf = {
                    if (featureEnabled("pdf") &&
                        featureEnabled("pdf.create") &&
                        featureEnabled("pdf.save")
                    ) {
                        scope.launch {
                            runCatching {
                                exportDialogueToPdf(
                                    context = context,
                                    dialogueTitle = dialogueTitle,
                                    turns = turns.map {
                                        Triple(it.roleTitle, it.sectionTitle, it.content)
                                    }
                                )
                            }.onFailure {
                                Toast.makeText(
                                    context,
                                    it.message ?: "خروجی PDF انجام نشد.",
                                    Toast.LENGTH_LONG
                                ).show()
                            }
                        }
                    }
                },
                onMoveTurn = { index, direction ->
                    if (canEdit) {
                        scope.launch {
                            runCatching {
                                db.withTransaction {
                                    DialogueRepository.requireAccessibleDialogue(
                                        db, dialogueId, isAdmin, ownerId
                                    )
                                    val current = turns
                                    val targetIndex = index + direction
                                    require(index in current.indices &&
                                        targetIndex in current.indices
                                    ) {
                                        "موقعیت انتخاب‌شده برای جابه‌جایی معتبر نیست."
                                    }
                                    val reordered = current.toMutableList()
                                    val movedTurn = reordered.removeAt(index)
                                    reordered.add(targetIndex, movedTurn)
                                    val metadataDao = db.dialogueTurnMetadataDao()
                                    reordered.forEachIndexed { newOrder, turn ->
                                        ensureTurnMetadata(turn.turnId, newOrder)
                                        metadataDao.updateRehearsalOrder(
                                            turn.turnId,
                                            newOrder
                                        )
                                    }
                                }
                                reloadTurns()
                            }.onFailure {
                                Toast.makeText(
                                    context,
                                    it.message ?: "تغییر ترتیب انجام نشد.",
                                    Toast.LENGTH_LONG
                                ).show()
                            }
                        }
                    }
                },
                onDeleteTurn = { turn ->
                    if (canEdit) {
                        scope.launch {
                            runCatching {
                                db.withTransaction {
                                    DialogueRepository.requireAccessibleDialogue(
                                        db, dialogueId, isAdmin, ownerId
                                    )
                                    val existing = db.dialogueTurnDao()
                                        .getByDialogue(dialogueId)
                                    require(existing.any { it.id == turn.turnId }) {
                                        "این نوبت متعلق به همین گفتگو نیست."
                                    }

                                    val remaining = existing.filterNot {
                                        it.id == turn.turnId
                                    }
                                    require(remaining.size >= 2) {
                                        "گفتگو باید حداقل دو نوبت داشته باشد."
                                    }

                                    val remainingRoleIds = remaining.mapNotNull { item ->
                                        runCatching {
                                            db.sectionDao().getById(item.sectionId).roleId
                                        }.getOrNull()
                                    }.toSet()
                                    require(remainingRoleIds.size >= 2) {
                                        "حذف این نوبت باعث می‌شود گفتگو کمتر از دو نقش داشته باشد."
                                    }

                                    db.dialogueTurnDao().deleteTurn(turn.turnId)

                                    val orderedRemaining = remaining.map { item ->
                                        item to (
                                            db.dialogueTurnMetadataDao()
                                                .getByTurn(item.id)
                                                ?.rehearsalOrderIndex ?: item.orderIndex
                                            )
                                    }.sortedWith(
                                        compareBy<Pair<DialogueTurnEntity, Int>> { it.second }
                                            .thenBy { it.first.orderIndex }
                                            .thenBy { it.first.id }
                                    )
                                    orderedRemaining.forEachIndexed { index, pair ->
                                        val item = pair.first
                                        ensureTurnMetadata(item.id, index)
                                        db.dialogueTurnMetadataDao()
                                            .updateRehearsalOrder(item.id, index)
                                    }

                                    db.dialogueRoleSelectionDao()
                                        .getByDialogue(dialogueId)
                                        .filter { it.roleId !in remainingRoleIds }
                                        .forEach { selection ->
                                            db.dialogueRoleSelectionDao().deleteRole(
                                                dialogueId,
                                                selection.roleId
                                            )
                                        }
                                }
                                reloadTurns()
                            }.onFailure {
                                Toast.makeText(
                                    context,
                                    it.message ?: "حذف نوبت انجام نشد.",
                                    Toast.LENGTH_LONG
                                ).show()
                            }
                        }
                    }
                },
                onAddTurn = {
                    if (canEdit) showAddTurn = true
                }
            )

            if (showAddTurn && canEdit) {
                var chosen by remember(dialogueId) {
                    mutableStateOf<SectionPickerItem?>(null)
                }

                AlertDialog(
                    onDismissRequest = { showAddTurn = false },
                    title = { Text("افزودن متن به مطالعه") },
                    text = {
                        if (availableSourceSections.isEmpty()) {
                            Text("منبع آرشیوی برای افزودن وجود ندارد.")
                        } else {
                            Column {
                                Text(
                                    "می‌توانید متنی از نسخه‌ای دیگر هم انتخاب کنید؛ با افزودن آن، نسخهٔ جدید به منابع این گفتگو اضافه می‌شود.",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.padding(bottom = 8.dp)
                                )
                                LazyColumn(
                                    Modifier.heightIn(max = 420.dp)
                                ) {
                                    items(
                                        availableSourceSections,
                                        key = { it.sectionId }
                                    ) { item ->
                                    ListItem(
                                        headlineContent = { Text(item.sectionTitle) },
                                        supportingContent = {
                                            Text("${item.roleTitle} · ${item.versionLabel}")
                                        },
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .clickable { chosen = item }
                                    )
                                }
                                }
                            }
                        }
                    },
                    confirmButton = {
                        TextButton(
                            enabled = chosen != null,
                            onClick = {
                                val selected = chosen ?: return@TextButton
                                scope.launch {
                                    runCatching {
                                        db.withTransaction {
                                            val freshDialogue =
                                                DialogueRepository.requireAccessibleDialogue(
                                                    db,
                                                    dialogueId,
                                                    isAdmin,
                                                    ownerId
                                                )
                                            val allFreshSources =
                                                DialogueRepository.loadSectionSources(
                                                    db,
                                                    freshDialogue.taziehId
                                                )
                                            require(allFreshSources.any {
                                                it.sectionId == selected.sectionId &&
                                                    it.roleId == selected.roleId &&
                                                    it.versionUid == selected.versionUid
                                            }) {
                                                "این متن در منابع آرشیوی این تعزیه وجود ندارد."
                                            }

                                            val selectionDao = db.dialogueRoleSelectionDao()
                                            var selections = selectionDao.getByDialogue(dialogueId)

                                            // گفت‌وگوهای قدیمی ممکن است هنوز جدول انتخاب نقش نداشته باشند.
                                            // پیش از افزودن منبع جدید، انتخاب‌های legacy را ثبت می‌کنیم تا
                                            // افزودن نسخهٔ تازه باعث ناپدیدشدن متن‌های قبلی نشود.
                                            if (selections.isEmpty()) {
                                                val legacySources =
                                                    DialogueRepository.loadAllowedSections(
                                                        db,
                                                        freshDialogue
                                                    ).distinctBy { it.roleId }
                                                legacySources.forEachIndexed { index, source ->
                                                    selectionDao.insert(
                                                        DialogueRoleSelectionEntity(
                                                            dialogueId = dialogueId,
                                                            roleId = source.roleId,
                                                            versionUid = source.versionUid,
                                                            displayOrder = index
                                                        )
                                                    )
                                                }
                                                selections = selectionDao.getByDialogue(dialogueId)
                                            }

                                            val existingSelection = selectionDao
                                                .getByDialogueAndRole(
                                                    dialogueId,
                                                    selected.roleId
                                                )
                                            if (existingSelection == null) {
                                                selectionDao.insert(
                                                    DialogueRoleSelectionEntity(
                                                        dialogueId = dialogueId,
                                                        roleId = selected.roleId,
                                                        versionUid = selected.versionUid,
                                                        displayOrder = (selections.maxOfOrNull {
                                                            it.displayOrder
                                                        } ?: -1) + 1
                                                    )
                                                )
                                            } else {
                                                require(existingSelection.versionUid == selected.versionUid) {
                                                    "این نقش از همین نسخه قبلاً به گفتگو افزوده شده است."
                                                }
                                            }

                                            val existing = db.dialogueTurnDao()
                                                .getByDialogue(dialogueId)
                                            require(existing.none {
                                                it.sectionId == selected.sectionId
                                            }) {
                                                "این متن قبلاً به گفت‌وگو افزوده شده است."
                                            }

                                            val nextSourceOrder =
                                                (existing.maxOfOrNull { it.orderIndex } ?: -1) + 1
                                            val newTurnId = db.dialogueTurnDao().insert(
                                                DialogueTurnEntity(
                                                    dialogueId = dialogueId,
                                                    sectionId = selected.sectionId,
                                                    orderIndex = nextSourceOrder
                                                )
                                            )
                                            require(newTurnId > 0L) {
                                                "ثبت متن جدید انجام نشد."
                                            }

                                            val nextDisplayOrder =
                                                (turns.maxOfOrNull {
                                                    it.rehearsalOrderIndex
                                                } ?: -1) + 1
                                            db.dialogueTurnMetadataDao().upsert(
                                                DialogueTurnMetadataEntity(
                                                    turnId = newTurnId,
                                                    rehearsalOrderIndex = nextDisplayOrder
                                                )
                                            )
                                        }
                                        reloadTurns()
                                        showAddTurn = false
                                    }.onFailure {
                                        Toast.makeText(
                                            context,
                                            it.message ?: "افزودن متن انجام نشد.",
                                            Toast.LENGTH_LONG
                                        ).show()
                                    }
                                }
                            }
                        ) {
                            Text("افزودن")
                        }
                    },
                    dismissButton = {
                        TextButton(onClick = { showAddTurn = false }) {
                            Text("انصراف")
                        }
                    }
                )
            }

            loadError?.let { message ->
                AlertDialog(
                    onDismissRequest = { loadError = null },
                    title = { Text("دسترسی به گفتگو ممکن نیست") },
                    text = { Text(message) },
                    confirmButton = {
                        TextButton(
                            onClick = {
                                loadError = null
                                scope.launch {
                                    runCatching { reloadTurns() }.onFailure {
                                        loadError = it.message ?: "بارگذاری گفتگو انجام نشد."
                                    }
                                }
                            }
                        ) {
                            Text("تلاش دوباره")
                        }
                    },
                    dismissButton = {
                        TextButton(
                            onClick = {
                                loadError = null
                                navController.popBackStack()
                            }
                        ) {
                            Text("بازگشت")
                        }
                    }
                )
            }
        }
    }
}
