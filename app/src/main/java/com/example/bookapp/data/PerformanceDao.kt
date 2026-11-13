package com.example.bookapp.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Update

@Dao
interface PerformanceDao {
    @Query("SELECT * FROM performance_groups ORDER BY name COLLATE NOCASE") suspend fun groups(): List<PerformanceGroupEntity>
    @Query("SELECT * FROM performance_groups WHERE id=:id LIMIT 1") suspend fun group(id: Long): PerformanceGroupEntity?
    @Insert suspend fun insertGroup(value: PerformanceGroupEntity): Long
    @Update suspend fun updateGroup(value: PerformanceGroupEntity)
    @Query("DELETE FROM performance_groups WHERE id=:id") suspend fun deleteGroup(id: Long)

    @Query("SELECT * FROM group_members WHERE groupId=:groupId ORDER BY isActive DESC, name COLLATE NOCASE") suspend fun members(groupId: Long): List<GroupMemberEntity>
    @Query("SELECT * FROM group_members WHERE id=:id LIMIT 1") suspend fun member(id: Long): GroupMemberEntity?
    @Insert suspend fun insertMember(value: GroupMemberEntity): Long
    @Update suspend fun updateMember(value: GroupMemberEntity)
    @Query("DELETE FROM group_members WHERE id=:id") suspend fun deleteMember(id: Long)

    @Query("SELECT * FROM performances WHERE groupId=:groupId ORDER BY scheduledAt DESC, id DESC") suspend fun performances(groupId: Long): List<PerformanceEntity>
    @Query("SELECT * FROM performances ORDER BY scheduledAt DESC, id DESC") suspend fun allPerformances(): List<PerformanceEntity>
    @Query("SELECT * FROM performances WHERE id=:id LIMIT 1") suspend fun performance(id: Long): PerformanceEntity?
    @Insert suspend fun insertPerformance(value: PerformanceEntity): Long
    @Update suspend fun updatePerformance(value: PerformanceEntity)
    @Query("DELETE FROM performances WHERE id=:id") suspend fun deletePerformance(id: Long)

    @Query("SELECT * FROM performance_assignments WHERE performanceId=:performanceId ORDER BY roleTitle, isAlternate, orderIndex") suspend fun assignments(performanceId: Long): List<PerformanceAssignmentEntity>
    @Insert suspend fun insertAssignment(value: PerformanceAssignmentEntity): Long
    @Update suspend fun updateAssignment(value: PerformanceAssignmentEntity)
    @Query("DELETE FROM performance_assignments WHERE id=:id") suspend fun deleteAssignment(id: Long)

    @Query("SELECT * FROM performance_notes WHERE performanceId=:performanceId ORDER BY updatedAt DESC") suspend fun notes(performanceId: Long): List<PerformanceNoteEntity>
    @Insert suspend fun insertNote(value: PerformanceNoteEntity): Long
    @Update suspend fun updateNote(value: PerformanceNoteEntity)
    @Query("DELETE FROM performance_notes WHERE id=:id") suspend fun deleteNote(id: Long)
}
