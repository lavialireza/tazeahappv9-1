package com.example.bookapp.data

import androidx.room.withTransaction
import com.example.bookapp.ui.screens.SectionPickerItem
import java.util.Locale

/** Shared persistence and ownership rules for Admin and Viewer dialogues. */
object DialogueRepository {

    private fun roleKey(title: String): String = title.trim()
        .replace('ي', 'ی')
        .replace('ك', 'ک')
        .replace("ـ", "")
        .lowercase(Locale.ROOT)
        .filterNot { it.isWhitespace() || it == '\u200c' }

    suspend fun loadSectionSources(
        db: AppDatabase,
        taziehId: Long
    ): List<SectionPickerItem> {
        val tazieh = db.taziehDao().getById(taziehId)
            ?: throw IllegalArgumentException("تعزیه انتخاب‌شده پیدا نشد.")
        val versions = db.archiveDao().versionsForTazieh(tazieh.uid)
            .sortedWith(compareByDescending<com.example.bookapp.data.archive.TaziehVersionEntity> { it.isCurrent }
                .thenByDescending { it.versionNumber })

        return versions.flatMap { version ->
            db.roleDao().getByVersionUid(version.uid)
                .filter { it.taziehId == taziehId }
                .flatMap { role ->
                    db.sectionDao().getByRole(role.id).map { section ->
                        SectionPickerItem(
                            sectionId = section.id,
                            roleTitle = role.title,
                            sectionTitle = section.title,
                            roleId = role.id,
                            versionUid = version.uid,
                            versionTitle = version.title,
                            versionNumber = version.versionNumber,
                            isCurrentVersion = version.isCurrent
                        )
                    }
                }
        }
    }

    suspend fun createDialogue(
        db: AppDatabase,
        taziehId: Long,
        title: String,
        orderedSections: List<SectionPickerItem>,
        ownerInstallationId: String
    ): Long = db.withTransaction {
        require(title.isNotBlank()) { "نام گفتگو را وارد کنید." }
        require(orderedSections.size >= 2) { "حداقل دو نوبت برای گفتگو لازم است." }
        require(orderedSections.map { it.sectionId }.distinct().size == orderedSections.size) {
            "یک بخش نمی‌تواند بیش از یک‌بار در توالی اولیه ثبت شود."
        }

        val tazieh = db.taziehDao().getById(taziehId)
            ?: throw IllegalArgumentException("تعزیه انتخاب‌شده پیدا نشد.")
        val validVersionUids = db.archiveDao().versionsForTazieh(tazieh.uid)
            .map { it.uid }.toSet()
        val verifiedItems = orderedSections.map { item ->
            require(item.versionUid.isNotBlank() && item.versionUid in validVersionUids) {
                "نسخه منبع یکی از نقش‌ها معتبر نیست یا متعلق به این تعزیه نیست."
            }
            val section = db.sectionDao().getById(item.sectionId)
            val role = db.roleDao().getById(section.roleId)
            require(section.roleId == item.roleId && role.id == item.roleId) {
                "بخش انتخاب‌شده با نقش ثبت‌شده آن مطابقت ندارد."
            }
            require(role.taziehId == taziehId) {
                "یکی از بخش‌ها متعلق به تعزیه دیگری است."
            }
            require(role.versionUid == item.versionUid) {
                "نسخه آرشیو یکی از نقش‌ها پس از انتخاب تغییر کرده است؛ فهرست را دوباره بارگذاری کنید."
            }
            item.copy(roleTitle = role.title, sectionTitle = section.title)
        }

        val roleGroups = verifiedItems.groupBy { roleKey(it.roleTitle) }
        require(roleGroups.size >= 2) {
            "گفتگو باید دست‌کم از دو نقش متفاوت تشکیل شود."
        }
        roleGroups.values.forEach { items ->
            require(items.map { it.roleId }.distinct().size == 1 &&
                items.map { it.versionUid }.distinct().size == 1) {
                "برای هر نقش فقط یک نسخه منبع انتخاب کنید."
            }
        }

        val dialogueId = db.dialogueDao().insert(
            DialogueEntity(
                taziehId = taziehId,
                title = title.trim(),
                // نسخه نخست فقط برای سازگاری با گفت‌وگوهای قدیمی نگهداری می‌شود؛
                // منبع واقعی هر نقش در جدول role selections ذخیره می‌شود.
                versionUid = verifiedItems.first().versionUid,
                ownerInstallationId = ownerInstallationId
            )
        )
        require(dialogueId > 0L) { "ذخیره گفتگو انجام نشد." }

        val firstByRole = linkedMapOf<String, SectionPickerItem>()
        verifiedItems.forEach { item ->
            val key = roleKey(item.roleTitle)
            if (!firstByRole.containsKey(key)) firstByRole[key] = item
        }
        firstByRole.values.forEachIndexed { displayOrder, item ->
            db.dialogueRoleSelectionDao().insert(
                DialogueRoleSelectionEntity(
                    dialogueId = dialogueId,
                    roleId = item.roleId,
                    versionUid = item.versionUid,
                    displayOrder = displayOrder
                )
            )
        }

        verifiedItems.forEachIndexed { index, item ->
            val turnId = db.dialogueTurnDao().insert(
                DialogueTurnEntity(
                    dialogueId = dialogueId,
                    sectionId = item.sectionId,
                    orderIndex = index
                )
            )
            require(turnId > 0L) { "ثبت یکی از نوبت‌های گفتگو انجام نشد." }
            db.dialogueTurnMetadataDao().upsert(
                DialogueTurnMetadataEntity(
                    turnId = turnId,
                    rehearsalOrderIndex = index
                )
            )
        }
        dialogueId
    }

    suspend fun loadAllowedSections(
        db: AppDatabase,
        dialogue: DialogueEntity
    ): List<SectionPickerItem> {
        val tazieh = db.taziehDao().getById(dialogue.taziehId)
            ?: return emptyList()
        val versions = db.archiveDao().versionsForTazieh(tazieh.uid).associateBy { it.uid }
        val selections = db.dialogueRoleSelectionDao().getByDialogue(dialogue.id)

        val roleSources = if (selections.isEmpty()) {
            db.roleDao().getByVersionUid(dialogue.versionUid)
                .filter { it.taziehId == dialogue.taziehId }
                .map { role -> role to dialogue.versionUid }
        } else {
            selections.mapNotNull { selection ->
                val role = runCatching { db.roleDao().getById(selection.roleId) }.getOrNull()
                    ?: return@mapNotNull null
                if (role.taziehId != dialogue.taziehId || role.versionUid != selection.versionUid) {
                    return@mapNotNull null
                }
                role to selection.versionUid
            }
        }

        return roleSources.flatMap { (role, versionUid) ->
            val version = versions[versionUid]
            db.sectionDao().getByRole(role.id).map { section ->
                SectionPickerItem(
                    sectionId = section.id,
                    roleTitle = role.title,
                    sectionTitle = section.title,
                    roleId = role.id,
                    versionUid = versionUid,
                    versionTitle = version?.title.orEmpty(),
                    versionNumber = version?.versionNumber,
                    isCurrentVersion = version?.isCurrent == true
                )
            }
        }
    }

    suspend fun requireAccessibleDialogue(
        db: AppDatabase,
        dialogueId: Long,
        isAdmin: Boolean,
        ownerInstallationId: String
    ): DialogueEntity {
        if (isAdmin) {
            return db.dialogueDao().getByIdOrNull(dialogueId)
                ?: throw IllegalArgumentException("گفتگوی موردنظر پیدا نشد.")
        }
        if (ownerInstallationId.isBlank()) {
            throw SecurityException("شناسه نصب برای بررسی مالکیت گفتگو معتبر نیست.")
        }
        return db.dialogueDao().getOwnedById(dialogueId, ownerInstallationId)
            ?: throw SecurityException("این گفتگو متعلق به این نصب نیست یا وجود ندارد.")
    }

    suspend fun loadDialogues(
        db: AppDatabase,
        taziehId: Long,
        isAdmin: Boolean,
        ownerInstallationId: String
    ): List<DialogueEntity> = when {
        isAdmin -> db.dialogueDao().getByTazieh(taziehId)
        ownerInstallationId.isBlank() -> emptyList()
        else -> db.dialogueDao().getOwnedByTazieh(taziehId, ownerInstallationId)
    }

    suspend fun renameDialogue(
        db: AppDatabase,
        dialogueId: Long,
        title: String,
        isAdmin: Boolean,
        ownerInstallationId: String
    ) = db.withTransaction {
        require(title.isNotBlank()) { "نام گفتگو نمی‌تواند خالی باشد." }
        requireAccessibleDialogue(db, dialogueId, isAdmin, ownerInstallationId)
        if (isAdmin) db.dialogueDao().updateTitle(dialogueId, title.trim())
        else require(db.dialogueDao().updateOwnedTitle(dialogueId, ownerInstallationId, title.trim()) == 1) {
            "اجازه ویرایش این گفتگو وجود ندارد."
        }
    }

    suspend fun deleteDialogue(
        db: AppDatabase,
        dialogueId: Long,
        isAdmin: Boolean,
        ownerInstallationId: String
    ) = db.withTransaction {
        requireAccessibleDialogue(db, dialogueId, isAdmin, ownerInstallationId)
        if (isAdmin) db.dialogueDao().delete(dialogueId)
        else require(db.dialogueDao().deleteOwned(dialogueId, ownerInstallationId) == 1) {
            "اجازه حذف این گفتگو وجود ندارد."
        }
    }
}
