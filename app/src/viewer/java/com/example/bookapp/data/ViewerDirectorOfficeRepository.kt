package com.example.bookapp.data

import android.content.Context
import androidx.room.withTransaction
import java.util.UUID

/** تمام عملیات تغییردهنده دفتر شخصی از این لایه عبور می‌کنند و مجوز در لحظه اجرا بررسی می‌شود. */
object ViewerDirectorOfficeRepository {
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
        val dao = db.directorDao()
        require(orderedTurns.map { it.id }.distinct().size == orderedTurns.size) { "فهرست نوبت‌ها تکراری است." }
        val first = orderedTurns.firstOrNull() ?: return
        val version = dao.version(first.versionId) ?: error("نسخه دفتر شخصی پیدا نشد.")
        val plan = dao.plan(version.planId) ?: error("دفتر شخصی پیدا نشد.")
        DirectorOfficeWorkspaceUseCases.requireDraft(plan, version)
        require(orderedTurns.all { it.versionId == version.id }) { "ترتیب نوبت‌های چند نسخه قابل تغییر هم‌زمان نیست." }
        require(orderedTurns.all { dao.turn(it.id)?.versionId == version.id }) { "نوبت متعلق به دفتر شخصی انتخاب‌شده نیست." }
        db.withTransaction {
            // شاخص موقت منفی از برخورد با unique(versionId, sequenceIndex) جلوگیری می‌کند.
            val finalOrder = DirectorOfficeWorkspaceUseCases.normalizedOrder(orderedTurns)
            finalOrder.forEachIndexed { index, (turnId, _) -> dao.moveTurn(turnId, -100000 - index) }
            finalOrder.forEach { (turnId, index) -> dao.moveTurn(turnId, index) }
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
        entry: ViewerDirectorOfficePublicationImporter.SelectorEntry
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
