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
        db.directorDao().updateTextSegment(value)
    }

    suspend fun updateCue(context: Context, db: ViewerDirectorOfficeDatabase, value: DirectorStageCueEntity) {
        requireEdit(context)
        db.directorDao().updateStageCue(value)
    }

    suspend fun reorderTurns(context: Context, db: ViewerDirectorOfficeDatabase, orderedTurns: List<DirectorTurnEntity>) {
        requireEdit(context)
        val dao = db.directorDao()
        db.withTransaction {
            // شاخص موقت منفی از برخورد با unique(versionId, sequenceIndex) جلوگیری می‌کند.
            orderedTurns.forEachIndexed { index, turn -> dao.moveTurn(turn.id, -100000 - index) }
            orderedTurns.forEachIndexed { index, turn -> dao.moveTurn(turn.id, index) }
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
        val nextIndex = dao.turns(version.id).maxOfOrNull { it.sequenceIndex }?.plus(1) ?: 0
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
        val turns = dao.turns(version.id)
        val nextIndex = turns.maxOfOrNull { it.sequenceIndex }?.plus(1) ?: 0
        val nextNumber = (turns.maxOfOrNull { it.turnNumber } ?: 0) + 1
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
        val nextOrder = dao.stageCues(version.id).maxOfOrNull { it.orderIndex }?.plus(1) ?: 0
        dao.insertStageCue(DirectorStageCueEntity(
            versionId = version.id, turnId = turnId, title = title.trim(), instruction = instruction.trim(), orderIndex = nextOrder
        ))
    }
}
