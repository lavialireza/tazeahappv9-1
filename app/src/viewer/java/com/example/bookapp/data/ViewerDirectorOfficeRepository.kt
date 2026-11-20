package com.example.bookapp.data

import android.content.Context
import androidx.room.withTransaction
import java.util.UUID
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** تمام عملیات تغییردهنده دفتر شخصی از این لایه عبور می‌کنند و مجوز در لحظه اجرا بررسی می‌شود. */
object ViewerDirectorOfficeRepository {

    /** ساخت دفتر شخصی مستقیماً از کتابخانه محلی همین Viewer؛ هیچ بسته‌ای از Admin دریافت نمی‌شود. */
    suspend fun createPersonalFromLibrary(
        context: Context,
        contentDb: AppDatabase,
        personalDb: ViewerDirectorOfficeDatabase,
        taziehUid: String?,
        method: String
    ): Long {
        val permissions = ViewerAccessPolicy.getEffectivePermissions(context)
        require(DirectorOfficeAccessRules.canCreatePersonal(permissions, method)) {
            "مدیر ساخت دفتر شخصی با این روش را برای این Viewer مجاز نکرده است."
        }
        require(method in DirectorOfficeSharedContract.methodCodes) { "روش ساخت دفتر معتبر نیست." }

        val sourceTazieh = if (method == DirectorOfficeSharedContract.MANUAL) {
            taziehUid?.takeIf { it.isNotBlank() }?.let { contentDb.taziehDao().getByUid(it) }
        } else {
            require(!taziehUid.isNullOrBlank()) { "ابتدا مجلس موردنظر را از کتابخانه انتخاب کنید." }
            contentDb.taziehDao().getByUid(taziehUid) ?: error("مجلس انتخاب‌شده در کتابخانه محلی پیدا نشد.")
        }
        val sourceVersion = sourceTazieh?.let { local ->
            contentDb.archiveDao().currentVersion(local.uid)
                ?: contentDb.archiveDao().versionsForTazieh(local.uid).firstOrNull()
        }
        val roles = when {
            sourceTazieh == null -> emptyList()
            sourceVersion != null -> contentDb.roleDao().getByVersionUid(sourceVersion.uid)
            else -> contentDb.roleDao().getByTazieh(sourceTazieh.id).filter { it.versionUid.isBlank() }
                .sortedWith(compareBy<RoleEntity> { it.orderIndex }.thenBy { it.id })
        }
        val sections = if (roles.isEmpty()) emptyList() else {
            val rolesById = roles.associateBy { it.id }
            contentDb.sectionDao().getByRoles(roles.map { it.id })
                .filter { it.content.isNotBlank() && rolesById.containsKey(it.roleId) }
                .sortedWith(compareBy<SectionEntity> { section -> rolesById[section.roleId]?.orderIndex ?: Int.MAX_VALUE }
                    .thenBy { it.orderIndex }.thenBy { it.id })
        }
        if (method == DirectorOfficeSharedContract.SELECTOR) {
            require(sections.isNotEmpty()) { "برای روش انتخاب‌گر، متن نقش‌های مجلس در کتابخانه محلی پیدا نشد." }
        }

        val now = System.currentTimeMillis()
        val dao = personalDb.directorDao()
        return personalDb.withTransaction {
            val title = sourceTazieh?.title?.let { "دفتر شخصی — $it" } ?: "دفتر شخصی جدید"
            val planId = dao.insertPlan(DirectorPlanEntity(
                uid = "personal-${UUID.randomUUID()}", taziehUid = sourceTazieh?.uid.orEmpty(),
                title = title, status = DirectorPlanEntity.STATUS_DRAFT,
                description = "دفتر مستقل این Viewer؛ روش ساخت: $method؛ منبع: کتابخانه محلی همین دستگاه؛ بدون انتقال دفتر از Admin.",
                createdAt = now, updatedAt = now
            ))
            val versionId = dao.insertVersion(DirectorPlanVersionEntity(
                planId = planId, versionNumber = 1, title = "نسخه شخصی اول",
                status = DirectorPlanVersionEntity.STATUS_DRAFT,
                changeNotes = "دفتر شخصی؛ روش ساخت: $method؛ منبع: کتابخانه محلی همین دستگاه",
                createdAt = now
            ))
            val roleInputs = roles.map { role ->
                DirectorOfficeBuildRole(uid = role.uid, title = role.title, orderIndex = role.orderIndex)
            }
            val roleUidById = roles.associate { it.id to it.uid }
            val sectionInputs = sections.mapNotNull { section ->
                val roleUid = roleUidById[section.roleId] ?: return@mapNotNull null
                DirectorOfficeBuildSection(
                    uid = section.uid, roleUid = roleUid, content = section.content, orderIndex = section.orderIndex
                )
            }
            DirectorOfficeWorkspaceOperations.buildDraftFromLibrary(
                dao = dao, versionId = versionId, method = method,
                roles = roleInputs, sections = sectionInputs
            )
            planId
        }
    }

    /** فهرست انتخاب‌گر از متن محلی همان مجلس ساخته می‌شود، نه از فایل دفتر رسمی. */
    suspend fun selectorEntries(contentDb: AppDatabase, taziehUid: String): List<DirectorOfficeSelectorEntry> =
        withContext(Dispatchers.IO) {
            val tazieh = contentDb.taziehDao().getByUid(taziehUid) ?: return@withContext emptyList()
            val version = contentDb.archiveDao().currentVersion(tazieh.uid)
                ?: contentDb.archiveDao().versionsForTazieh(tazieh.uid).firstOrNull()
            val roles = if (version != null) contentDb.roleDao().getByVersionUid(version.uid)
            else contentDb.roleDao().getByTazieh(tazieh.id).filter { it.versionUid.isBlank() }
                .sortedWith(compareBy<RoleEntity> { it.orderIndex }.thenBy { it.id })
            if (roles.isEmpty()) return@withContext emptyList()
            val roleById = roles.associateBy { it.id }
            val sections = contentDb.sectionDao().getByRoles(roles.map { it.id })
                .filter { it.content.isNotBlank() && roleById.containsKey(it.roleId) }
                .sortedWith(compareBy<SectionEntity> { section -> roleById[section.roleId]?.orderIndex ?: Int.MAX_VALUE }
                    .thenBy { it.orderIndex }.thenBy { it.id })
            sections.flatMap { section ->
                val role = roleById[section.roleId] ?: return@flatMap emptyList()
                DirectorOfficeWorkspaceUseCases.splitDialogues(section.content).mapIndexed { index, text ->
                    DirectorOfficeSelectorEntry("${section.uid}:$index", role.uid, role.title, section.uid, section.title, text)
                }
            }
        }

    private fun requireEdit(context: Context) {
        require(DirectorOfficeAccessRules.canEditPersonal(ViewerAccessPolicy.getEffectivePermissions(context))) {
            "مدیر اجازه ویرایش دفتر شخصی را برای این Viewer فعال نکرده است."
        }
    }

    suspend fun deletePlan(context: Context, db: ViewerDirectorOfficeDatabase, planId: Long) {
        requireEdit(context)
        val dao = db.directorDao()
        val plan = dao.plan(planId) ?: error("دفتر شخصی پیدا نشد.")
        require(plan.uid.startsWith("personal-")) { "فقط دفتر شخصی همین Viewer قابل حذف است." }
        val version = dao.versions(planId).firstOrNull() ?: error("نسخه دفتر پیدا نشد.")
        DirectorOfficeWorkspaceUseCases.requireDraft(plan, version)
        dao.softDeletePlan(planId, System.currentTimeMillis())
    }

    suspend fun addTurn(context: Context, db: ViewerDirectorOfficeDatabase, planId: Long, turnNumber: Int, title: String, text: String) {
        requireEdit(context)
        val dao = db.directorDao()
        db.withTransaction { DirectorOfficeWorkspaceOperations.addTurn(dao, planId, turnNumber, title, text) }
    }

    suspend fun addParticipant(context: Context, db: ViewerDirectorOfficeDatabase, turnId: Long, roleUid: String, roleTitle: String, isLead: Boolean = false) {
        requireEdit(context)
        val dao = db.directorDao()
        db.withTransaction { DirectorOfficeWorkspaceOperations.addParticipant(dao, turnId, roleUid, roleTitle, isLead) }
    }

    suspend fun setLeadParticipant(context: Context, db: ViewerDirectorOfficeDatabase, participantId: Long, turnId: Long) {
        requireEdit(context)
        val dao = db.directorDao()
        db.withTransaction { DirectorOfficeWorkspaceOperations.setLeadParticipant(dao, turnId, participantId) }
    }

    suspend fun removeParticipant(context: Context, db: ViewerDirectorOfficeDatabase, participantId: Long, turnId: Long) {
        requireEdit(context)
        val dao = db.directorDao()
        db.withTransaction { DirectorOfficeWorkspaceOperations.removeParticipant(dao, participantId, turnId) }
    }

    suspend fun deleteTurn(context: Context, db: ViewerDirectorOfficeDatabase, turnId: Long) {
        requireEdit(context)
        val dao = db.directorDao()
        db.withTransaction { DirectorOfficeWorkspaceOperations.deleteTurn(dao, turnId) }
    }

    suspend fun deleteTextSegment(context: Context, db: ViewerDirectorOfficeDatabase, segment: DirectorTextSegmentEntity) {
        requireEdit(context)
        val dao = db.directorDao()
        db.withTransaction { DirectorOfficeWorkspaceOperations.deleteTextSegment(dao, segment) }
    }

    suspend fun deleteStageCue(context: Context, db: ViewerDirectorOfficeDatabase, cueId: Long) {
        requireEdit(context)
        val dao = db.directorDao()
        db.withTransaction { DirectorOfficeWorkspaceOperations.deleteStageCue(dao, cueId) }
    }

    suspend fun updateText(context: Context, db: ViewerDirectorOfficeDatabase, value: DirectorTextSegmentEntity) {
        requireEdit(context)
        db.withTransaction { DirectorOfficeWorkspaceOperations.updateTextSegment(db.directorDao(), value) }
    }

    suspend fun updateTurn(context: Context, db: ViewerDirectorOfficeDatabase, value: DirectorTurnEntity) {
        requireEdit(context)
        db.withTransaction { DirectorOfficeWorkspaceOperations.updateTurn(db.directorDao(), value) }
    }

    suspend fun addDialogueSegment(context: Context, db: ViewerDirectorOfficeDatabase, turnId: Long, text: String) {
        requireEdit(context)
        db.withTransaction { DirectorOfficeWorkspaceOperations.addDialogueSegment(db.directorDao(), turnId, text) }
    }

    suspend fun updateCue(context: Context, db: ViewerDirectorOfficeDatabase, value: DirectorStageCueEntity) {
        requireEdit(context)
        db.withTransaction { DirectorOfficeWorkspaceOperations.updateStageCue(db.directorDao(), value) }
    }

    suspend fun reorderTurns(context: Context, db: ViewerDirectorOfficeDatabase, orderedTurns: List<DirectorTurnEntity>) {
        requireEdit(context)
        db.withTransaction {
            DirectorOfficeWorkspaceOperations.reorderTurns(db.directorDao(), orderedTurns)
        }
    }

    suspend fun addManualTurn(
        context: Context,
        db: ViewerDirectorOfficeDatabase,
        planId: Long,
        turnNumber: Int,
        title: String,
        text: String
    ) {
        requireEdit(context)
        require(ViewerAccessPolicy.hasPermission(context, "director.personal.method.manual")) {
            "روش دستی برای این Viewer مجاز نیست."
        }
        require(title.isNotBlank() && text.isNotBlank()) { "عنوان نقش و متن نوبت را وارد کنید." }
        val dao = db.directorDao()
        val version = dao.versions(planId).firstOrNull() ?: error("نسخه دفتر شخصی پیدا نشد.")
        val plan = dao.plan(planId) ?: error("دفتر شخصی پیدا نشد.")
        DirectorOfficeWorkspaceUseCases.requireDraft(plan, version)
        val existingTurns = dao.turns(version.id)
        val nextIndex = DirectorOfficeWorkspaceUseCases.nextSequenceIndex(existingTurns)
        db.withTransaction {
            val turnId = dao.insertTurn(DirectorTurnEntity(
                versionId = version.id, turnNumber = turnNumber, sequenceIndex = nextIndex, title = title.trim()
            ))
            dao.insertParticipant(DirectorParticipantEntity(
                turnId = turnId, roleUid = "personal-manual-${UUID.randomUUID()}", roleTitle = title.trim(), isLead = true
            ))
            dao.insertTextSegment(DirectorTextSegmentEntity(turnId = turnId, segmentIndex = 0, text = text.trim()))
            dao.updatePlan(dao.plan(planId)?.copy(updatedAt = System.currentTimeMillis()) ?: error("دفتر شخصی پیدا نشد."))
        }
    }

    suspend fun addSelectorDialogue(
        context: Context,
        db: ViewerDirectorOfficeDatabase,
        planId: Long,
        entry: DirectorOfficeSelectorEntry
    ) {
        requireEdit(context)
        require(ViewerAccessPolicy.hasPermission(context, "director.personal.method.selector")) {
            "روش انتخاب‌گر برای این Viewer مجاز نیست."
        }
        val dao = db.directorDao()
        val version = dao.versions(planId).firstOrNull() ?: error("نسخه دفتر شخصی پیدا نشد.")
        val plan = dao.plan(planId) ?: error("دفتر شخصی پیدا نشد.")
        DirectorOfficeWorkspaceUseCases.requireDraft(plan, version)
        val turns = dao.turns(version.id)
        val nextIndex = DirectorOfficeWorkspaceUseCases.nextSequenceIndex(turns)
        val nextNumber = DirectorOfficeWorkspaceUseCases.nextTurnNumber(turns)
        db.withTransaction {
            val turnId = dao.insertTurn(DirectorTurnEntity(
                versionId = version.id, turnNumber = nextNumber, sequenceIndex = nextIndex,
                title = entry.roleTitle, continuityGroupUid = entry.key
            ))
            dao.insertParticipant(DirectorParticipantEntity(
                turnId = turnId, roleUid = entry.roleUid, roleTitle = entry.roleTitle, isLead = true
            ))
            dao.insertTextSegment(DirectorTextSegmentEntity(
                turnId = turnId, segmentIndex = 0, text = entry.text, sectionUid = entry.sectionUid
            ))
            dao.updatePlan(dao.plan(planId)?.copy(updatedAt = System.currentTimeMillis()) ?: error("دفتر شخصی پیدا نشد."))
        }
    }

    suspend fun addStageCue(
        context: Context,
        db: ViewerDirectorOfficeDatabase,
        planId: Long,
        title: String,
        instruction: String,
        turnId: Long? = null,
        cueType: String = DirectorStageCueEntity.CUE_OTHER,
        blocksNextTurn: Boolean = cueType == DirectorStageCueEntity.CUE_IMMEDIATE
    ) {
        requireEdit(context)
        db.withTransaction { DirectorOfficeWorkspaceOperations.addStageCue(db.directorDao(), planId, title, instruction, turnId, cueType, blocksNextTurn) }
    }
}
