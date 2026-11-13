package com.example.bookapp.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update

/** Data access for the director workspace. All records are independent from existing rehearsal/dialogue tables. */
@Dao
interface DirectorDao {
    @Query("SELECT * FROM director_plans ORDER BY updatedAt DESC, id DESC") suspend fun plans(): List<DirectorPlanEntity>
    @Query("SELECT * FROM director_plans WHERE id=:id LIMIT 1") suspend fun plan(id: Long): DirectorPlanEntity?
    @Query("SELECT * FROM director_plans WHERE taziehUid=:taziehUid ORDER BY updatedAt DESC") suspend fun plansForTazieh(taziehUid: String): List<DirectorPlanEntity>
    @Insert(onConflict = OnConflictStrategy.ABORT) suspend fun insertPlan(value: DirectorPlanEntity): Long
    @Update suspend fun updatePlan(value: DirectorPlanEntity)
    @Query("DELETE FROM director_plans WHERE id=:id") suspend fun deletePlan(id: Long)

    @Query("SELECT * FROM director_plan_versions WHERE planId=:planId ORDER BY versionNumber DESC") suspend fun versions(planId: Long): List<DirectorPlanVersionEntity>
    @Query("SELECT * FROM director_plan_versions WHERE id=:id LIMIT 1") suspend fun version(id: Long): DirectorPlanVersionEntity?
    @Insert(onConflict = OnConflictStrategy.ABORT) suspend fun insertVersion(value: DirectorPlanVersionEntity): Long
    @Update suspend fun updateVersion(value: DirectorPlanVersionEntity)
    @Query("DELETE FROM director_plan_versions WHERE id=:id") suspend fun deleteVersion(id: Long)

    @Query("SELECT * FROM director_source_files WHERE planId=:planId ORDER BY createdAt DESC") suspend fun sourceFiles(planId: Long): List<DirectorSourceFileEntity>
    @Insert suspend fun insertSourceFile(value: DirectorSourceFileEntity): Long
    @Update suspend fun updateSourceFile(value: DirectorSourceFileEntity)
    @Query("DELETE FROM director_source_files WHERE id=:id") suspend fun deleteSourceFile(id: Long)

    @Query("SELECT * FROM director_turns WHERE versionId=:versionId ORDER BY sequenceIndex, id") suspend fun turns(versionId: Long): List<DirectorTurnEntity>
    @Query("SELECT * FROM director_turns WHERE id=:id LIMIT 1") suspend fun turn(id: Long): DirectorTurnEntity?
    @Insert suspend fun insertTurn(value: DirectorTurnEntity): Long
    @Insert suspend fun insertTurns(values: List<DirectorTurnEntity>): List<Long>
    @Update suspend fun updateTurn(value: DirectorTurnEntity)
    @Query("DELETE FROM director_turns WHERE id=:id") suspend fun deleteTurn(id: Long)
    @Query("UPDATE director_turns SET sequenceIndex=:sequenceIndex WHERE id=:turnId") suspend fun moveTurn(turnId: Long, sequenceIndex: Int)

    @Query("SELECT * FROM director_turn_participants WHERE turnId=:turnId ORDER BY orderIndex, id") suspend fun participants(turnId: Long): List<DirectorParticipantEntity>
    @Insert(onConflict = OnConflictStrategy.ABORT) suspend fun insertParticipant(value: DirectorParticipantEntity): Long
    @Insert(onConflict = OnConflictStrategy.ABORT) suspend fun insertParticipants(values: List<DirectorParticipantEntity>): List<Long>
    @Query("DELETE FROM director_turn_participants WHERE id=:id") suspend fun deleteParticipant(id: Long)

    @Query("SELECT * FROM director_text_segments WHERE turnId=:turnId ORDER BY segmentIndex, id") suspend fun textSegments(turnId: Long): List<DirectorTextSegmentEntity>
    @Insert suspend fun insertTextSegment(value: DirectorTextSegmentEntity): Long
    @Insert suspend fun insertTextSegments(values: List<DirectorTextSegmentEntity>): List<Long>
    @Update suspend fun updateTextSegment(value: DirectorTextSegmentEntity)
    @Query("DELETE FROM director_text_segments WHERE id=:id") suspend fun deleteTextSegment(id: Long)

    @Query("SELECT * FROM director_stage_cues WHERE versionId=:versionId ORDER BY orderIndex, id") suspend fun stageCues(versionId: Long): List<DirectorStageCueEntity>
    @Query("SELECT * FROM director_stage_cues WHERE turnId=:turnId ORDER BY orderIndex, id") suspend fun cuesForTurn(turnId: Long): List<DirectorStageCueEntity>
    @Insert suspend fun insertStageCue(value: DirectorStageCueEntity): Long
    @Update suspend fun updateStageCue(value: DirectorStageCueEntity)
    @Query("DELETE FROM director_stage_cues WHERE id=:id") suspend fun deleteStageCue(id: Long)

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
