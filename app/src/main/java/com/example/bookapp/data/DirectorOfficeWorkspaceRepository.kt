package com.example.bookapp.data

import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope

/**
 * تصویر یکپارچه فضای کاری دفتر؛ مستقل از Flavor و نوع پایگاه داده.
 * Admin و Viewer داده‌های خود را از Repository هم‌قرارداد می‌خوانند، اما DAO جداگانه تزریق می‌کنند.
 */
data class DirectorOfficeWorkspaceState(
    val plan: DirectorPlanEntity,
    val versions: List<DirectorPlanVersionEntity>,
    val selectedVersion: DirectorPlanVersionEntity?,
    val turns: List<DirectorTurnEntity>,
    val participantsByTurn: Map<Long, List<DirectorParticipantEntity>>,
    val textSegmentsByTurn: Map<Long, List<DirectorTextSegmentEntity>>,
    val stageCues: List<DirectorStageCueEntity>
) {
    val isReadOnly: Boolean
        get() = selectedVersion?.status != DirectorPlanVersionEntity.STATUS_DRAFT ||
            plan.status == DirectorPlanEntity.STATUS_ARCHIVED

    val searchableTextByTurn: Map<Long, String>
        get() = turns.associate { turn ->
            val participants = participantsByTurn[turn.id].orEmpty().joinToString(" ") { it.roleTitle }
            val text = textSegmentsByTurn[turn.id].orEmpty().joinToString(" ") { it.text }
            turn.id to listOf(turn.turnNumber.toString(), turn.title, participants, text).joinToString(" ")
        }
}

/** قرارداد یگانه بارگذاری و عملیات عمومی دفتر. API انتشار و سیاست دسترسی در آن وجود ندارد. */
interface DirectorOfficeWorkspaceRepository {
    suspend fun loadWorkspace(planId: Long, versionId: Long? = null): DirectorOfficeWorkspaceState
    suspend fun changeHistory(planId: Long, turnId: Long? = null): List<DirectorChangeHistoryEntity>
    suspend fun restoreChangeHistory(historyId: Long)
    suspend fun buildDraftFromLibrary(
        versionId: Long,
        method: String,
        roles: List<DirectorOfficeBuildRole>,
        sections: List<DirectorOfficeBuildSection>
    ): DirectorOfficeBuildResult
    suspend fun addTurn(planId: Long, turnNumber: Int, title: String, text: String)
    suspend fun updateTurn(value: DirectorTurnEntity)
    suspend fun replaceTurnText(turnId: Long, text: String)
    suspend fun updateTextSegment(value: DirectorTextSegmentEntity)
    suspend fun addDialogueSegment(turnId: Long, text: String)
    suspend fun addStageCue(
        planId: Long, title: String, instruction: String, turnId: Long? = null,
        cueType: String = DirectorStageCueEntity.CUE_OTHER,
        blocksNextTurn: Boolean = cueType == DirectorStageCueEntity.CUE_IMMEDIATE,
        continuationRoleUid: String = ""
    )
    suspend fun updateStageCue(value: DirectorStageCueEntity)
    suspend fun deleteStageCue(cueId: Long)
    suspend fun addParticipant(turnId: Long, roleUid: String, roleTitle: String, isLead: Boolean = false)
    suspend fun updateParticipant(value: DirectorParticipantEntity)
    suspend fun removeParticipant(participantId: Long, turnId: Long)
    suspend fun setLeadParticipant(turnId: Long, participantId: Long)
    suspend fun deleteTurn(turnId: Long)
    suspend fun deleteTextSegment(segment: DirectorTextSegmentEntity)
    suspend fun reorderTurns(orderedTurns: List<DirectorTurnEntity>)
    suspend fun addSelectorDialogue(
        planId: Long, entry: DirectorOfficeSelectorEntry, turnNumber: Int? = null, versionId: Long? = null
    )
    suspend fun addRoleLinkedTurn(
        versionId: Long, turnNumber: Int, roleUid: String, roleTitle: String,
        text: String, sectionUid: String? = null
    )
}

/**
 * پیاده‌سازی واحد Repository بر پایه قرارداد DAO. با تزریق DAO و مرز تراکنش، همین کلاس
 * برای دیتابیس Admin و دیتابیس شخصی Viewer استفاده می‌شود؛ هیچ اتصال سراسری به دیتابیس ندارد.
 */
class DaoDirectorOfficeWorkspaceRepository(
    private val dao: DirectorOfficeWorkspaceDao,
    private val transactionRunner: DirectorOfficeTransactionRunner = DirectDirectorOfficeTransactionRunner
) : DirectorOfficeWorkspaceRepository {

    override suspend fun changeHistory(planId: Long, turnId: Long?): List<DirectorChangeHistoryEntity> =
        dao.changeHistory(planId, turnId)

    override suspend fun restoreChangeHistory(historyId: Long) = transactionRunner.run {
        DirectorOfficeWorkspaceOperations.restoreChangeHistory(dao, historyId)
    }

    override suspend fun loadWorkspace(planId: Long, versionId: Long?): DirectorOfficeWorkspaceState = coroutineScope {
        // ابتدا مشخصات دفتر و نسخه‌ها هم‌زمان خوانده می‌شوند.
        val planRequest = async { dao.plan(planId) }
        val versionsRequest = async { dao.versionsForPlan(planId) }
        val plan = planRequest.await() ?: error("دفتر پیدا نشد.")
        val versions = versionsRequest.await().sortedByDescending { it.versionNumber }
        val selected = if (versionId != null) {
            versions.firstOrNull { it.id == versionId }
                ?: error("نسخه انتخاب‌شده متعلق به این دفتر نیست یا دیگر وجود ندارد.")
        } else {
            versions.firstOrNull { it.status == DirectorPlanVersionEntity.STATUS_DRAFT }
                ?: versions.firstOrNull()
        }

        val turnsRequest = selected?.let { selectedVersion -> async { dao.turns(selectedVersion.id) } }
        val cuesRequest = selected?.let { selectedVersion -> async { dao.stageCues(selectedVersion.id) } }
        val turns = turnsRequest?.await().orEmpty()
            .sortedWith(compareBy<DirectorTurnEntity> { it.sequenceIndex }.thenBy { it.id })
        val turnIds = turns.map { it.id }

        // ارتباط‌های همه نوبت‌ها با دو درخواست گروهی و به‌طور هم‌زمان بارگذاری می‌شوند.
        val participantsRequest = if (turnIds.isEmpty()) null else async { dao.participantsForTurns(turnIds) }
        val segmentsRequest = if (turnIds.isEmpty()) null else async { dao.textSegmentsForTurns(turnIds) }
        val participants = participantsRequest?.await().orEmpty()
        val segments = segmentsRequest?.await().orEmpty()
        val cues = cuesRequest?.await().orEmpty()
            .sortedWith(compareBy<DirectorStageCueEntity> { it.orderIndex }.thenBy { it.id })

        DirectorOfficeWorkspaceState(
            plan = plan,
            versions = versions,
            selectedVersion = selected,
            turns = turns,
            participantsByTurn = participants.groupBy { it.turnId }
                .mapValues { (_, rows) -> rows.sortedWith(compareBy<DirectorParticipantEntity> { it.orderIndex }.thenBy { it.id }) },
            textSegmentsByTurn = segments.groupBy { it.turnId }
                .mapValues { (_, rows) -> rows.sortedWith(compareBy<DirectorTextSegmentEntity> { it.segmentIndex }.thenBy { it.id }) },
            stageCues = cues
        )
    }

    override suspend fun buildDraftFromLibrary(
        versionId: Long, method: String,
        roles: List<DirectorOfficeBuildRole>, sections: List<DirectorOfficeBuildSection>
    ): DirectorOfficeBuildResult = DirectorOfficeWorkspaceOperations.buildDraftFromLibrary(
        dao, versionId, method, roles, sections, transactionRunner
    )

    override suspend fun addTurn(planId: Long, turnNumber: Int, title: String, text: String) =
        transactionRunner.run { DirectorOfficeWorkspaceOperations.addTurn(dao, planId, turnNumber, title, text) }

    override suspend fun updateTurn(value: DirectorTurnEntity) = transactionRunner.run {
        DirectorOfficeWorkspaceOperations.updateTurn(dao, value)
    }

    override suspend fun replaceTurnText(turnId: Long, text: String) = transactionRunner.run {
        DirectorOfficeWorkspaceOperations.replaceTurnText(dao, turnId, text)
    }

    override suspend fun updateTextSegment(value: DirectorTextSegmentEntity) = transactionRunner.run {
        DirectorOfficeWorkspaceOperations.updateTextSegment(dao, value)
    }

    override suspend fun addDialogueSegment(turnId: Long, text: String) = transactionRunner.run {
        DirectorOfficeWorkspaceOperations.addDialogueSegment(dao, turnId, text)
    }

    override suspend fun addStageCue(
        planId: Long, title: String, instruction: String, turnId: Long?, cueType: String,
        blocksNextTurn: Boolean, continuationRoleUid: String
    ) = transactionRunner.run {
        DirectorOfficeWorkspaceOperations.addStageCue(
            dao, planId, title, instruction, turnId, cueType, blocksNextTurn, continuationRoleUid
        )
    }

    override suspend fun updateStageCue(value: DirectorStageCueEntity) = transactionRunner.run {
        DirectorOfficeWorkspaceOperations.updateStageCue(dao, value)
    }

    override suspend fun deleteStageCue(cueId: Long) = transactionRunner.run {
        DirectorOfficeWorkspaceOperations.deleteStageCue(dao, cueId)
    }

    override suspend fun addParticipant(turnId: Long, roleUid: String, roleTitle: String, isLead: Boolean) = transactionRunner.run {
        DirectorOfficeWorkspaceOperations.addParticipant(dao, turnId, roleUid, roleTitle, isLead)
    }

    override suspend fun updateParticipant(value: DirectorParticipantEntity) = transactionRunner.run {
        DirectorOfficeWorkspaceOperations.updateParticipant(dao, value)
    }

    override suspend fun removeParticipant(participantId: Long, turnId: Long) = transactionRunner.run {
        DirectorOfficeWorkspaceOperations.removeParticipant(dao, participantId, turnId)
    }

    override suspend fun setLeadParticipant(turnId: Long, participantId: Long) = transactionRunner.run {
        DirectorOfficeWorkspaceOperations.setLeadParticipant(dao, turnId, participantId)
    }

    override suspend fun deleteTurn(turnId: Long) = transactionRunner.run {
        DirectorOfficeWorkspaceOperations.deleteTurn(dao, turnId)
    }

    override suspend fun deleteTextSegment(segment: DirectorTextSegmentEntity) = transactionRunner.run {
        DirectorOfficeWorkspaceOperations.deleteTextSegment(dao, segment)
    }

    override suspend fun reorderTurns(orderedTurns: List<DirectorTurnEntity>) =
        DirectorOfficeWorkspaceOperations.reorderTurns(dao, orderedTurns, transactionRunner)

    override suspend fun addSelectorDialogue(
        planId: Long, entry: DirectorOfficeSelectorEntry, turnNumber: Int?, versionId: Long?
    ) = transactionRunner.run {
        DirectorOfficeWorkspaceOperations.addSelectorDialogue(dao, planId, entry, turnNumber, versionId)
    }

    override suspend fun addRoleLinkedTurn(
        versionId: Long, turnNumber: Int, roleUid: String, roleTitle: String,
        text: String, sectionUid: String?
    ) = DirectorOfficeWorkspaceOperations.addRoleLinkedTurn(
        dao, versionId, turnNumber, roleUid, roleTitle, text, sectionUid,
        transactionRunner
    )
}

/**
 * جستجوی یگانه نوبت‌ها برای تمام Flavorها.
 * ورودی searchableTextByTurn باید از snapshot مشترک ساخته شده باشد؛ در نتیجه شماره، عنوان،
 * نام نقش‌ها و متن دیالوگ با قواعد یکسان در Admin، Viewer رسمی و Viewer شخصی جستجو می‌شوند.
 */
object DirectorOfficeWorkspaceSearch {
    fun filterTurns(
        turns: List<DirectorTurnEntity>,
        searchableTextByTurn: Map<Long, String>,
        query: String
    ): List<DirectorTurnEntity> {
        val normalizedQuery = ContentUid.normalize(
            DirectorOfficeWorkspaceUseCases.normalizeNumber(query.trim())
        )
        if (normalizedQuery.isBlank()) return turns

        return turns.filter { turn ->
            val number = ContentUid.normalize(
                DirectorOfficeWorkspaceUseCases.normalizeNumber(turn.turnNumber.toString())
            )
            val corpus = ContentUid.normalize(
                DirectorOfficeWorkspaceUseCases.normalizeNumber(searchableTextByTurn[turn.id].orEmpty())
            )
            number.contains(normalizedQuery, ignoreCase = true) ||
                corpus.contains(normalizedQuery, ignoreCase = true)
        }
    }
}
