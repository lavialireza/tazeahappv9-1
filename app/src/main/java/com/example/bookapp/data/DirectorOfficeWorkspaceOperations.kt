package com.example.bookapp.data

import java.util.UUID

/**
 * عملیات کاربردی مشترک دفتر معین‌البکاء.
 *
 * این لایه به Flavor، Context و نوع دیتابیس وابسته نیست؛ هر دو برنامه فقط DAO
 * و مرز تراکنش دیتابیس مستقل خود را به آن می‌دهند. کنترل مجوز Viewer در adapter
 * همان Flavor باقی می‌ماند و این کلاس هیچ API انتشار یا مدیریت کاربر ندارد.
 */
object DirectorOfficeWorkspaceOperations {

    private fun requireEditableDraft(
        plan: DirectorPlanEntity,
        version: DirectorPlanVersionEntity,
        allowPublishedPlan: Boolean
    ) {
        if (!allowPublishedPlan) {
            DirectorOfficeWorkspaceUseCases.requireDraft(plan, version)
        } else {
            require(!plan.isDeleted && plan.status != DirectorPlanEntity.STATUS_ARCHIVED &&
                version.status == DirectorPlanVersionEntity.STATUS_DRAFT) {
                "فقط نسخه پیش‌نویس یک دفتر فعال قابل ویرایش است."
            }
        }
    }

    suspend fun updateText(
        dao: DirectorOfficeWorkspaceDao,
        value: DirectorTextSegmentEntity,
        allowPublishedPlan: Boolean = false,
        transactionRunner: DirectorOfficeTransactionRunner = DirectDirectorOfficeTransactionRunner
    ) {
        val turn = dao.turn(value.turnId) ?: error("نوبت دفتر پیدا نشد.")
        val version = dao.version(turn.versionId) ?: error("نسخه دفتر پیدا نشد.")
        val plan = dao.plan(version.planId) ?: error("دفتر پیدا نشد.")
        requireEditableDraft(plan, version, allowPublishedPlan)
        transactionRunner.run {
            dao.updateTextSegment(value)
            dao.updatePlan(plan.copy(updatedAt = System.currentTimeMillis()))
        }
    }

    suspend fun updateTurn(
        dao: DirectorOfficeWorkspaceDao,
        value: DirectorTurnEntity,
        allowPublishedPlan: Boolean = false,
        transactionRunner: DirectorOfficeTransactionRunner = DirectDirectorOfficeTransactionRunner
    ) {
        require(value.turnNumber > 0) { "شماره نوبت باید بزرگ‌تر از صفر باشد." }
        require(value.title.isNotBlank()) { "عنوان نقش یا نوبت خالی است." }
        val current = dao.turn(value.id) ?: error("نوبت دفتر پیدا نشد.")
        val version = dao.version(current.versionId) ?: error("نسخه دفتر پیدا نشد.")
        val plan = dao.plan(version.planId) ?: error("دفتر پیدا نشد.")
        requireEditableDraft(plan, version, allowPublishedPlan)
        require(value.versionId == current.versionId && value.sequenceIndex == current.sequenceIndex) {
            "شناسه نسخه و جایگاه نوبت از این عملیات قابل تغییر نیست."
        }
        transactionRunner.run {
            dao.updateTurn(value)
            dao.updatePlan(plan.copy(updatedAt = System.currentTimeMillis()))
        }
    }

    suspend fun addDialogueSegment(
        dao: DirectorOfficeWorkspaceDao,
        turnId: Long,
        text: String,
        allowPublishedPlan: Boolean = false,
        transactionRunner: DirectorOfficeTransactionRunner = DirectDirectorOfficeTransactionRunner
    ) {
        require(text.isNotBlank()) { "متن دیالوگ خالی است." }
        val turn = dao.turn(turnId) ?: error("نوبت دفتر پیدا نشد.")
        val version = dao.version(turn.versionId) ?: error("نسخه دفتر پیدا نشد.")
        val plan = dao.plan(version.planId) ?: error("دفتر پیدا نشد.")
        requireEditableDraft(plan, version, allowPublishedPlan)
        val nextIndex = dao.textSegments(turnId).maxOfOrNull { it.segmentIndex }?.plus(1) ?: 0
        transactionRunner.run {
            dao.insertTextSegment(DirectorTextSegmentEntity(turnId = turnId, segmentIndex = nextIndex, text = text.trim()))
            dao.updatePlan(plan.copy(updatedAt = System.currentTimeMillis()))
        }
    }

    suspend fun updateCue(
        dao: DirectorOfficeWorkspaceDao,
        value: DirectorStageCueEntity,
        allowPublishedPlan: Boolean = false,
        transactionRunner: DirectorOfficeTransactionRunner = DirectDirectorOfficeTransactionRunner
    ) {
        require(value.instruction.isNotBlank()) { "متن راهنمای صحنه خالی است." }
        val version = dao.version(value.versionId) ?: error("نسخه دفتر پیدا نشد.")
        val plan = dao.plan(version.planId) ?: error("دفتر پیدا نشد.")
        requireEditableDraft(plan, version, allowPublishedPlan)
        if (value.turnId != null) {
            require(dao.turn(value.turnId)?.versionId == version.id) {
                "راهنمای صحنه باید به نوبت همین نسخه متصل باشد."
            }
        }
        transactionRunner.run {
            dao.updateStageCue(value)
            dao.updatePlan(plan.copy(updatedAt = System.currentTimeMillis()))
        }
    }

    suspend fun reorderTurns(
        dao: DirectorOfficeWorkspaceDao,
        orderedTurns: List<DirectorTurnEntity>,
        allowPublishedPlan: Boolean = false,
        transactionRunner: DirectorOfficeTransactionRunner = DirectDirectorOfficeTransactionRunner
    ) {
        require(orderedTurns.map { it.id }.distinct().size == orderedTurns.size) {
            "فهرست نوبت‌ها دارای شناسه تکراری است."
        }
        val first = orderedTurns.firstOrNull() ?: return
        val version = dao.version(first.versionId) ?: error("نسخه دفتر پیدا نشد.")
        val plan = dao.plan(version.planId) ?: error("دفتر پیدا نشد.")
        requireEditableDraft(plan, version, allowPublishedPlan)
        require(orderedTurns.all { it.versionId == version.id }) {
            "ترتیب نوبت‌های چند نسخه را نمی‌توان هم‌زمان تغییر داد."
        }
        require(orderedTurns.all { dao.turn(it.id)?.versionId == version.id }) {
            "یکی از نوبت‌ها متعلق به نسخه انتخاب‌شده نیست."
        }
        transactionRunner.run {
            val finalOrder = DirectorOfficeWorkspaceUseCases.normalizedOrder(orderedTurns)
            finalOrder.forEachIndexed { index, (turnId, _) -> dao.moveTurn(turnId, -100000 - index) }
            finalOrder.forEach { (turnId, index) -> dao.moveTurn(turnId, index) }
            dao.updatePlan(plan.copy(updatedAt = System.currentTimeMillis()))
        }
    }

    suspend fun addManualTurn(
        dao: DirectorOfficeWorkspaceDao,
        planId: Long,
        turnNumber: Int,
        title: String,
        text: String,
        transactionRunner: DirectorOfficeTransactionRunner = DirectDirectorOfficeTransactionRunner
    ) {
        require(turnNumber > 0) { "شماره نوبت باید بزرگ‌تر از صفر باشد." }
        require(title.isNotBlank() && text.isNotBlank()) { "عنوان نقش و متن نوبت را وارد کنید." }
        val plan = dao.plan(planId) ?: error("دفتر پیدا نشد.")
        val version = dao.versions(planId).firstOrNull() ?: error("نسخه پیش‌نویس دفتر پیدا نشد.")
        DirectorOfficeWorkspaceUseCases.requireDraft(plan, version)
        val nextIndex = DirectorOfficeWorkspaceUseCases.nextSequenceIndex(dao.turns(version.id))
        transactionRunner.run {
            val turnId = dao.insertTurn(DirectorTurnEntity(
                versionId = version.id, turnNumber = turnNumber, sequenceIndex = nextIndex, title = title.trim()
            ))
            dao.insertParticipant(DirectorParticipantEntity(
                turnId = turnId, roleUid = "personal-manual-${UUID.randomUUID()}", roleTitle = title.trim(), isLead = true
            ))
            dao.insertTextSegment(DirectorTextSegmentEntity(turnId = turnId, segmentIndex = 0, text = text.trim()))
            dao.updatePlan(plan.copy(updatedAt = System.currentTimeMillis()))
        }
    }

    suspend fun addRoleLinkedTurn(
        dao: DirectorOfficeWorkspaceDao,
        versionId: Long,
        turnNumber: Int,
        roleUid: String,
        roleTitle: String,
        text: String,
        sectionUid: String? = null,
        allowPublishedPlan: Boolean = false,
        transactionRunner: DirectorOfficeTransactionRunner = DirectDirectorOfficeTransactionRunner
    ) {
        require(turnNumber > 0) { "شماره نوبت باید بزرگ‌تر از صفر باشد." }
        require(roleUid.isNotBlank() && roleTitle.isNotBlank() && text.isNotBlank()) {
            "نقش و متن نوبت باید مشخص باشند."
        }
        val version = dao.version(versionId) ?: error("نسخه دفتر پیدا نشد.")
        val plan = dao.plan(version.planId) ?: error("دفتر پیدا نشد.")
        requireEditableDraft(plan, version, allowPublishedPlan)
        val nextIndex = DirectorOfficeWorkspaceUseCases.nextSequenceIndex(dao.turns(version.id))
        transactionRunner.run {
            val turnId = dao.insertTurn(DirectorTurnEntity(
                versionId = version.id, turnNumber = turnNumber, sequenceIndex = nextIndex, title = roleTitle.trim()
            ))
            dao.insertParticipant(DirectorParticipantEntity(
                turnId = turnId, roleUid = roleUid, roleTitle = roleTitle.trim(), isLead = true
            ))
            dao.insertTextSegment(DirectorTextSegmentEntity(
                turnId = turnId, segmentIndex = 0, text = text.trim(), sectionUid = sectionUid
            ))
            dao.updatePlan(plan.copy(updatedAt = System.currentTimeMillis()))
        }
    }

    suspend fun addSelectorDialogue(
        dao: DirectorOfficeWorkspaceDao,
        versionId: Long,
        entry: DirectorOfficeSelectorEntry,
        turnNumber: Int? = null,
        allowPublishedPlan: Boolean = false,
        transactionRunner: DirectorOfficeTransactionRunner = DirectDirectorOfficeTransactionRunner
    ) {
        val version = dao.version(versionId) ?: error("نسخه پیش‌نویس دفتر پیدا نشد.")
        val plan = dao.plan(version.planId) ?: error("دفتر پیدا نشد.")
        requireEditableDraft(plan, version, allowPublishedPlan)
        val turns = dao.turns(version.id)
        val nextIndex = DirectorOfficeWorkspaceUseCases.nextSequenceIndex(turns)
        val nextNumber = turnNumber ?: DirectorOfficeWorkspaceUseCases.nextTurnNumber(turns)
        require(nextNumber > 0) { "شماره نوبت باید بزرگ‌تر از صفر باشد." }
        transactionRunner.run {
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
            dao.updatePlan(plan.copy(updatedAt = System.currentTimeMillis()))
        }
    }

    suspend fun addStageCue(
        dao: DirectorOfficeWorkspaceDao,
        versionId: Long,
        title: String,
        instruction: String,
        turnId: Long? = null,
        allowPublishedPlan: Boolean = false,
        transactionRunner: DirectorOfficeTransactionRunner = DirectDirectorOfficeTransactionRunner
    ) {
        require(title.isNotBlank() && instruction.isNotBlank()) { "عنوان و متن راهنمای صحنه الزامی است." }
        val version = dao.version(versionId) ?: error("نسخه پیش‌نویس دفتر پیدا نشد.")
        val plan = dao.plan(version.planId) ?: error("دفتر پیدا نشد.")
        requireEditableDraft(plan, version, allowPublishedPlan)
        require(turnId == null || dao.turn(turnId)?.versionId == version.id) {
            "راهنمای صحنه باید به نوبت همین نسخه متصل باشد."
        }
        val nextOrder = dao.stageCues(version.id).maxOfOrNull { it.orderIndex }?.plus(1) ?: 0
        transactionRunner.run {
            dao.insertStageCue(DirectorStageCueEntity(
                versionId = version.id, turnId = turnId, title = title.trim(), instruction = instruction.trim(), orderIndex = nextOrder
            ))
            dao.updatePlan(plan.copy(updatedAt = System.currentTimeMillis()))
        }
    }

}

data class DirectorOfficeSelectorEntry(
    val key: String,
    val roleUid: String,
    val roleTitle: String,
    val sectionUid: String,
    val text: String
)
