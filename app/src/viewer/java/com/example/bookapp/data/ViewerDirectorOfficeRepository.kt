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

    suspend fun updateText(context: Context, db: ViewerDirectorOfficeDatabase, value: DirectorTextSegmentEntity) {
        requireEdit(context)
        val dao = db.directorDao()
        val turn = dao.turn(value.turnId) ?: error("نوبت دفتر شخصی پیدا نشد.")
        val version = dao.version(turn.versionId) ?: error("نسخه دفتر شخصی پیدا نشد.")
        val plan = dao.plan(version.planId) ?: error("دفتر شخصی پیدا نشد.")
        DirectorOfficeWorkspaceUseCases.requireDraft(plan, version)
        dao.updateTextSegment(value)
    }

    suspend fun updateTurn(context: Context, db: ViewerDirectorOfficeDatabase, value: DirectorTurnEntity) {
        requireEdit(context)
        require(value.turnNumber > 0) { "شماره نوبت باید بزرگ‌تر از صفر باشد." }
        require(value.title.isNotBlank()) { "عنوان نقش یا نوبت خالی است." }
        val dao = db.directorDao()
        val current = dao.turn(value.id) ?: error("نوبت دفتر شخصی پیدا نشد.")
        val version = dao.version(current.versionId) ?: error("نسخه دفتر شخصی پیدا نشد.")
        val plan = dao.plan(version.planId) ?: error("دفتر شخصی پیدا نشد.")
        DirectorOfficeWorkspaceUseCases.requireDraft(plan, version)
        require(value.versionId == current.versionId && value.sequenceIndex == current.sequenceIndex) { "شناسه نسخه و جایگاه نوبت قابل تغییر از این فرم نیست." }
        dao.updateTurn(value)
        dao.updatePlan(plan.copy(updatedAt = System.currentTimeMillis()))
    }

    suspend fun addDialogueSegment(context: Context, db: ViewerDirectorOfficeDatabase, turnId: Long, text: String) {
        requireEdit(context)
        require(text.isNotBlank()) { "متن دیالوگ خالی است." }
        val dao = db.directorDao()
        val turn = dao.turn(turnId) ?: error("نوبت دفتر شخصی پیدا نشد.")
        val version = dao.version(turn.versionId) ?: error("نسخه دفتر شخصی پیدا نشد.")
        val plan = dao.plan(version.planId) ?: error("دفتر شخصی پیدا نشد.")
        DirectorOfficeWorkspaceUseCases.requireDraft(plan, version)
        val nextIndex = dao.textSegments(turnId).maxOfOrNull { it.segmentIndex }?.plus(1) ?: 0
        dao.insertTextSegment(DirectorTextSegmentEntity(turnId = turnId, segmentIndex = nextIndex, text = text.trim()))
        dao.updatePlan(plan.copy(updatedAt = System.currentTimeMillis()))
    }

    suspend fun updateCue(context: Context, db: ViewerDirectorOfficeDatabase, value: DirectorStageCueEntity) {
        requireEdit(context)
        val dao = db.directorDao()
        val version = dao.version(value.versionId) ?: error("نسخه دفتر شخصی پیدا نشد.")
        val plan = dao.plan(version.planId) ?: error("دفتر شخصی پیدا نشد.")
        DirectorOfficeWorkspaceUseCases.requireDraft(plan, version)
        dao.updateStageCue(value)
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
        turnId: Long? = null
    ) {
        requireEdit(context)
        require(title.isNotBlank() && instruction.isNotBlank()) { "عنوان و متن راهنمای صحنه را وارد کنید." }
        val dao = db.directorDao()
        val version = dao.versions(planId).firstOrNull() ?: error("نسخه دفتر شخصی پیدا نشد.")
        val plan = dao.plan(planId) ?: error("دفتر شخصی پیدا نشد.")
        DirectorOfficeWorkspaceUseCases.requireDraft(plan, version)
        require(turnId == null || dao.turn(turnId)?.versionId == version.id) { "راهنمای صحنه باید به نوبت همین نسخه متصل باشد." }
        val nextOrder = dao.stageCues(version.id).maxOfOrNull { it.orderIndex }?.plus(1) ?: 0
        dao.insertStageCue(DirectorStageCueEntity(
            versionId = version.id, turnId = turnId, title = title.trim(), instruction = instruction.trim(), orderIndex = nextOrder
        ))
    }
}
