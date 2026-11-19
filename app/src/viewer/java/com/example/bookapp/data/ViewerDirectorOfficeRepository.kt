package com.example.bookapp.data

import android.content.Context
import androidx.room.withTransaction

/**
 * Adapter اختصاصی Viewer: فقط مجوزها و دیتابیس شخصی Viewer را مدیریت می‌کند.
 * تمام قواعد و عملیات عمومی دفتر به هسته مشترک منتقل شده‌اند.
 */
object ViewerDirectorOfficeRepository {
    private fun requireEdit(context: Context) {
        require(DirectorOfficeAccessRules.canEditPersonal(ViewerAccessPolicy.getEffectivePermissions(context))) {
            "مدیر اجازه ویرایش دفتر شخصی را برای این Viewer فعال نکرده است."
        }
    }

    private suspend fun inTransaction(db: ViewerDirectorOfficeDatabase, operation: suspend () -> Unit) {
        db.withTransaction { operation() }
    }

    suspend fun updateText(context: Context, db: ViewerDirectorOfficeDatabase, value: DirectorTextSegmentEntity) {
        requireEdit(context)
        DirectorOfficeWorkspaceOperations.updateText(db.directorDao(), value) { operation -> inTransaction(db, operation) }
    }

    suspend fun updateTurn(context: Context, db: ViewerDirectorOfficeDatabase, value: DirectorTurnEntity) {
        requireEdit(context)
        DirectorOfficeWorkspaceOperations.updateTurn(db.directorDao(), value) { operation -> inTransaction(db, operation) }
    }

    suspend fun addDialogueSegment(context: Context, db: ViewerDirectorOfficeDatabase, turnId: Long, text: String) {
        requireEdit(context)
        DirectorOfficeWorkspaceOperations.addDialogueSegment(db.directorDao(), turnId, text) { operation -> inTransaction(db, operation) }
    }

    suspend fun updateCue(context: Context, db: ViewerDirectorOfficeDatabase, value: DirectorStageCueEntity) {
        requireEdit(context)
        DirectorOfficeWorkspaceOperations.updateCue(db.directorDao(), value) { operation -> inTransaction(db, operation) }
    }

    suspend fun reorderTurns(context: Context, db: ViewerDirectorOfficeDatabase, orderedTurns: List<DirectorTurnEntity>) {
        requireEdit(context)
        DirectorOfficeWorkspaceOperations.reorderTurns(db.directorDao(), orderedTurns) { operation ->
            inTransaction(db, operation)
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
        DirectorOfficeWorkspaceOperations.addManualTurn(db.directorDao(), planId, turnNumber, title, text) { operation ->
            inTransaction(db, operation)
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
        val versionId = db.directorDao().versions(planId).firstOrNull()?.id
            ?: error("نسخه دفتر شخصی پیدا نشد.")
        DirectorOfficeWorkspaceOperations.addSelectorDialogue(
            db.directorDao(),
            versionId,
            DirectorOfficeSelectorEntry(
                key = entry.key,
                roleUid = entry.roleUid,
                roleTitle = entry.roleTitle,
                sectionUid = entry.sectionUid,
                text = entry.text
            )
        ) { operation -> inTransaction(db, operation) }
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
        val versionId = db.directorDao().versions(planId).firstOrNull()?.id
            ?: error("نسخه دفتر شخصی پیدا نشد.")
        DirectorOfficeWorkspaceOperations.addStageCue(db.directorDao(), versionId, title, instruction, turnId) { operation ->
            inTransaction(db, operation)
        }
    }
}
