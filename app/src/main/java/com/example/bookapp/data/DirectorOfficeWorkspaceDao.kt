package com.example.bookapp.data

/**
 * قرارداد داده‌ای مشترک برای عملیات عمومی فضای کاری دفتر.
 * DAOهای Admin و Viewer این قرارداد را پیاده می‌کنند، اما هر Flavor به دیتابیس خودش متصل است.
 * این قرارداد عمداً هیچ API برای انتشار، امضا، کپی یا خروجی ندارد.
 */
interface DirectorOfficeWorkspaceDao {
    suspend fun plan(id: Long): DirectorPlanEntity?
    suspend fun version(id: Long): DirectorPlanVersionEntity?
    suspend fun versionsForPlan(planId: Long): List<DirectorPlanVersionEntity>
    suspend fun turn(id: Long): DirectorTurnEntity?
    suspend fun participants(turnId: Long): List<DirectorParticipantEntity>
    suspend fun participantsForTurns(turnIds: List<Long>): List<DirectorParticipantEntity>
    suspend fun insertParticipant(value: DirectorParticipantEntity): Long
    suspend fun insertParticipants(values: List<DirectorParticipantEntity>): List<Long> = values.map { insertParticipant(it) }
    suspend fun updateParticipant(value: DirectorParticipantEntity)
    suspend fun deleteParticipant(id: Long)
    suspend fun textSegments(turnId: Long): List<DirectorTextSegmentEntity>
    /** بارگذاری همه قطعه‌های چند نوبت با یک پرس‌وجو؛ پیاده‌سازی پیش‌فرض برای DAOهای آزمایشی. */
    suspend fun textSegmentsForTurns(turnIds: List<Long>): List<DirectorTextSegmentEntity> =
        if (turnIds.isEmpty()) emptyList() else turnIds.flatMap { textSegments(it) }
    suspend fun turns(versionId: Long): List<DirectorTurnEntity>
    suspend fun updatePlan(value: DirectorPlanEntity)
    suspend fun softDeletePlan(id: Long, deletedAt: Long)
    suspend fun insertTurn(value: DirectorTurnEntity): Long
    suspend fun insertTurns(values: List<DirectorTurnEntity>): List<Long> = values.map { insertTurn(it) }
    suspend fun updateTurn(value: DirectorTurnEntity)
    suspend fun deleteTurn(id: Long)
    suspend fun moveTurn(turnId: Long, sequenceIndex: Int)
    suspend fun insertTextSegment(value: DirectorTextSegmentEntity): Long
    suspend fun insertTextSegments(values: List<DirectorTextSegmentEntity>): List<Long> = values.map { insertTextSegment(it) }
    suspend fun updateTextSegment(value: DirectorTextSegmentEntity)
    suspend fun deleteTextSegment(id: Long)
    suspend fun stageCues(versionId: Long): List<DirectorStageCueEntity>
    suspend fun stageCue(id: Long): DirectorStageCueEntity?
    suspend fun deleteStageCue(id: Long)
    suspend fun insertStageCue(value: DirectorStageCueEntity): Long
    suspend fun updateStageCue(value: DirectorStageCueEntity)
}
