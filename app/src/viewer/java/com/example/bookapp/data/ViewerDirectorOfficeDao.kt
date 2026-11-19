package com.example.bookapp.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update

/** سطح داده محدودشده Viewer؛ فاقد API انتشار و مدیریت نسخه رسمی. */
@Dao
interface ViewerDirectorOfficeDao : DirectorOfficeWorkspaceDao {
    @Query("SELECT * FROM director_plans WHERE isDeleted = 0 AND (:query = '' OR title LIKE '%' || :query || '%') ORDER BY updatedAt DESC, id DESC LIMIT :limit OFFSET :offset")
    suspend fun plansPage(query: String, limit: Int, offset: Int): List<DirectorPlanEntity>

    @Query("SELECT * FROM director_plans WHERE id=:id LIMIT 1")
    override suspend fun plan(id: Long): DirectorPlanEntity?

    @Query("SELECT * FROM director_plans WHERE uid=:uid LIMIT 1")
    suspend fun planByUid(uid: String): DirectorPlanEntity?

    /** فقط Importer امضای معتبر را بررسی کرده و این حذف را در تراکنش انجام می‌دهد. */
    @Query("DELETE FROM director_plans WHERE uid=:uid")
    suspend fun replaceImportedOfficialPlan(uid: String)

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insertPlan(value: DirectorPlanEntity): Long

    @Update
    override suspend fun updatePlan(value: DirectorPlanEntity)

    @Query("SELECT * FROM director_plan_versions WHERE planId=:planId ORDER BY versionNumber DESC")
    override suspend fun versions(planId: Long): List<DirectorPlanVersionEntity>

    @Query("SELECT * FROM director_plan_versions WHERE id=:id LIMIT 1")
    override suspend fun version(id: Long): DirectorPlanVersionEntity?

    @Query("SELECT * FROM director_turns WHERE id=:id LIMIT 1")
    override suspend fun turn(id: Long): DirectorTurnEntity?

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insertVersion(value: DirectorPlanVersionEntity): Long

    @Update
    override suspend fun updateTurn(value: DirectorTurnEntity)

    @Query("SELECT * FROM director_turns WHERE versionId=:versionId ORDER BY sequenceIndex, id")
    override suspend fun turns(versionId: Long): List<DirectorTurnEntity>

    @Insert(onConflict = OnConflictStrategy.ABORT)
    override suspend fun insertTurn(value: DirectorTurnEntity): Long

    @Query("UPDATE director_turns SET sequenceIndex=:sequenceIndex WHERE id=:turnId")
    override suspend fun moveTurn(turnId: Long, sequenceIndex: Int)

    @Query("SELECT * FROM director_turn_participants WHERE turnId=:turnId ORDER BY orderIndex, id")
    override suspend fun participants(turnId: Long): List<DirectorParticipantEntity>

    @Query("SELECT * FROM director_turn_participants WHERE turnId IN (:turnIds) ORDER BY turnId, orderIndex, id")
    override suspend fun participantsForTurns(turnIds: List<Long>): List<DirectorParticipantEntity>

    @Insert(onConflict = OnConflictStrategy.ABORT)
    override suspend fun insertParticipant(value: DirectorParticipantEntity): Long

    @Query("SELECT * FROM director_text_segments WHERE turnId=:turnId ORDER BY segmentIndex, id")
    override suspend fun textSegments(turnId: Long): List<DirectorTextSegmentEntity>

    @Query("SELECT * FROM director_text_segments WHERE turnId IN (:turnIds) ORDER BY turnId, segmentIndex, id")
    suspend fun textSegmentsForTurns(turnIds: List<Long>): List<DirectorTextSegmentEntity>

    @Insert(onConflict = OnConflictStrategy.ABORT)
    override suspend fun insertTextSegment(value: DirectorTextSegmentEntity): Long

    @Update
    override suspend fun updateTextSegment(value: DirectorTextSegmentEntity)

    @Query("SELECT * FROM director_stage_cues WHERE versionId=:versionId ORDER BY orderIndex, id")
    override suspend fun stageCues(versionId: Long): List<DirectorStageCueEntity>

    @Insert(onConflict = OnConflictStrategy.ABORT)
    override suspend fun insertStageCue(value: DirectorStageCueEntity): Long

    @Update
    override suspend fun updateStageCue(value: DirectorStageCueEntity)
}
