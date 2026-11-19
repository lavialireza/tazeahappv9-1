package com.example.bookapp.data

/**
 * قرارداد داده‌ای مشترک برای عملیات عمومی فضای کاری دفتر.
 * DAOهای Admin و Viewer این قرارداد را پیاده می‌کنند، اما هر Flavor به دیتابیس خودش متصل است.
 * این قرارداد عمداً هیچ API برای انتشار، امضا، کپی یا خروجی ندارد.
 */
interface DirectorOfficeWorkspaceDao {
    suspend fun plan(id: Long): DirectorPlanEntity?
    suspend fun version(id: Long): DirectorPlanVersionEntity?
    suspend fun turn(id: Long): DirectorTurnEntity?
    suspend fun turns(versionId: Long): List<DirectorTurnEntity>
    suspend fun updatePlan(value: DirectorPlanEntity)
    suspend fun insertTurn(value: DirectorTurnEntity): Long
    suspend fun moveTurn(turnId: Long, sequenceIndex: Int)
    suspend fun insertParticipant(value: DirectorParticipantEntity): Long
    suspend fun insertTextSegment(value: DirectorTextSegmentEntity): Long
    suspend fun updateTextSegment(value: DirectorTextSegmentEntity)
    suspend fun insertStageCue(value: DirectorStageCueEntity): Long
    suspend fun updateStageCue(value: DirectorStageCueEntity)
}
