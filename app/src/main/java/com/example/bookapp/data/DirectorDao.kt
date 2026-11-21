package com.example.bookapp.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update

/** Data access for the director workspace. All records are independent from existing rehearsal/dialogue tables. */
@Dao
interface DirectorDao : DirectorOfficeMainPageDao {
    @Query("SELECT * FROM director_plans WHERE isDeleted = 0 AND (:query = '' OR title LIKE '%' || :query || '%') ORDER BY updatedAt DESC, id DESC LIMIT :limit OFFSET :offset") override suspend fun plansPage(query: String, limit: Int, offset: Int): List<DirectorPlanEntity>
    @Query("SELECT * FROM director_plans WHERE id=:id LIMIT 1") override suspend fun plan(id: Long): DirectorPlanEntity?
    @Query("SELECT * FROM director_plans WHERE taziehUid=:taziehUid AND isDeleted = 0 ORDER BY updatedAt DESC") override suspend fun plansForTazieh(taziehUid: String): List<DirectorPlanEntity>
    @Insert(onConflict = OnConflictStrategy.ABORT) override suspend fun insertPlan(value: DirectorPlanEntity): Long
    @Update override suspend fun updatePlan(value: DirectorPlanEntity)
    @Query("UPDATE director_plans SET isDeleted=1, deletedAt=:deletedAt, updatedAt=:deletedAt WHERE id=:id AND isDeleted=0") override suspend fun softDeletePlan(id: Long, deletedAt: Long)

    @Query("SELECT * FROM director_plan_versions WHERE planId=:planId ORDER BY versionNumber DESC") override suspend fun versions(planId: Long): List<DirectorPlanVersionEntity>
    @Query("SELECT * FROM director_plan_versions WHERE planId=:planId ORDER BY versionNumber DESC") override suspend fun versionsForPlan(planId: Long): List<DirectorPlanVersionEntity>
    @Query("SELECT * FROM director_plan_versions WHERE id=:id LIMIT 1") override suspend fun version(id: Long): DirectorPlanVersionEntity?
    @Insert(onConflict = OnConflictStrategy.ABORT) override suspend fun insertVersion(value: DirectorPlanVersionEntity): Long
    @Update override suspend fun updateVersion(value: DirectorPlanVersionEntity)
    @Query("DELETE FROM director_plan_versions WHERE id=:id") suspend fun deleteVersion(id: Long)

    @Query("SELECT * FROM director_source_files WHERE planId=:planId ORDER BY createdAt DESC") override suspend fun sourceFiles(planId: Long): List<DirectorSourceFileEntity>
    @Insert override suspend fun insertSourceFile(value: DirectorSourceFileEntity): Long
    @Update suspend fun updateSourceFile(value: DirectorSourceFileEntity)
    @Query("DELETE FROM director_source_files WHERE id=:id") suspend fun deleteSourceFile(id: Long)

    @Query("SELECT * FROM director_turns WHERE versionId=:versionId ORDER BY sequenceIndex, id") override suspend fun turns(versionId: Long): List<DirectorTurnEntity>
    @Query("SELECT * FROM director_turns WHERE id=:id LIMIT 1") override suspend fun turn(id: Long): DirectorTurnEntity?
    @Insert override suspend fun insertTurn(value: DirectorTurnEntity): Long
    @Insert override suspend fun insertTurns(values: List<DirectorTurnEntity>): List<Long>
    @Update override suspend fun updateTurn(value: DirectorTurnEntity)
    @Query("DELETE FROM director_turns WHERE id=:id") override suspend fun deleteTurn(id: Long)
    @Query("UPDATE director_turns SET sequenceIndex=:sequenceIndex WHERE id=:turnId") override suspend fun moveTurn(turnId: Long, sequenceIndex: Int)

    @Query("SELECT * FROM director_turn_participants WHERE turnId=:turnId ORDER BY orderIndex, id") override suspend fun participants(turnId: Long): List<DirectorParticipantEntity>
    @Query("SELECT * FROM director_turn_participants WHERE turnId IN (:turnIds) ORDER BY turnId, orderIndex, id") override suspend fun participantsForTurns(turnIds: List<Long>): List<DirectorParticipantEntity>
    @Insert(onConflict = OnConflictStrategy.ABORT) override suspend fun insertParticipant(value: DirectorParticipantEntity): Long
    @Insert(onConflict = OnConflictStrategy.ABORT) override suspend fun insertParticipants(values: List<DirectorParticipantEntity>): List<Long>
    @Update override suspend fun updateParticipant(value: DirectorParticipantEntity)
    @Query("DELETE FROM director_turn_participants WHERE id=:id") override suspend fun deleteParticipant(id: Long)

    @Query("SELECT * FROM director_text_segments WHERE turnId=:turnId ORDER BY segmentIndex, id") override suspend fun textSegments(turnId: Long): List<DirectorTextSegmentEntity>
    @Query("SELECT * FROM director_text_segments WHERE turnId IN (:turnIds) ORDER BY turnId, segmentIndex, id") override suspend fun textSegmentsForTurns(turnIds: List<Long>): List<DirectorTextSegmentEntity>
    @Insert override suspend fun insertTextSegment(value: DirectorTextSegmentEntity): Long
    @Insert override suspend fun insertTextSegments(values: List<DirectorTextSegmentEntity>): List<Long>
    @Update override suspend fun updateTextSegment(value: DirectorTextSegmentEntity)
    @Query("DELETE FROM director_text_segments WHERE id=:id") override suspend fun deleteTextSegment(id: Long)

    @Query("SELECT * FROM director_stage_cues WHERE versionId=:versionId ORDER BY orderIndex, id") override suspend fun stageCues(versionId: Long): List<DirectorStageCueEntity>
    @Query("SELECT * FROM director_stage_cues WHERE id=:id LIMIT 1") override suspend fun stageCue(id: Long): DirectorStageCueEntity?
    @Query("SELECT * FROM director_stage_cues WHERE turnId=:turnId ORDER BY orderIndex, id") suspend fun cuesForTurn(turnId: Long): List<DirectorStageCueEntity>
    @Insert override suspend fun insertStageCue(value: DirectorStageCueEntity): Long
    @Update override suspend fun updateStageCue(value: DirectorStageCueEntity)
    @Query("DELETE FROM director_stage_cues WHERE id=:id") override suspend fun deleteStageCue(id: Long)

    @Query("SELECT * FROM director_sessions WHERE planId=:planId ORDER BY startedAt DESC, id DESC") suspend fun sessions(planId: Long): List<DirectorSessionEntity>
    @Query("SELECT * FROM director_sessions WHERE id=:id LIMIT 1") suspend fun session(id: Long): DirectorSessionEntity?
    @Insert suspend fun insertSession(value: DirectorSessionEntity): Long
    @Update suspend fun updateSession(value: DirectorSessionEntity)
    @Query("DELETE FROM director_sessions WHERE id=:id") suspend fun deleteSession(id: Long)

    @Query("SELECT * FROM director_turn_progress WHERE sessionId=:sessionId ORDER BY id") suspend fun progress(sessionId: Long): List<DirectorTurnProgressEntity>
    @Query("SELECT * FROM director_turn_progress WHERE sessionId=:sessionId AND turnId=:turnId LIMIT 1") suspend fun turnProgress(sessionId: Long, turnId: Long): DirectorTurnProgressEntity?
    @Insert(onConflict = OnConflictStrategy.REPLACE) suspend fun upsertTurnProgress(value: DirectorTurnProgressEntity): Long
    @Update suspend fun updateTurnProgress(value: DirectorTurnProgressEntity)
    @Query("DELETE FROM director_turn_progress WHERE sessionId=:sessionId AND turnId=:turnId") suspend fun clearTurnProgress(sessionId: Long, turnId: Long)
}
