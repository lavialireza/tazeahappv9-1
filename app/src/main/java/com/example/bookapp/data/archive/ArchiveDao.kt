package com.example.bookapp.data.archive

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query

@Dao
interface ArchiveDao {
    @Query("SELECT * FROM tazieh_versions WHERE taziehUid = :taziehUid ORDER BY versionNumber DESC")
    suspend fun versionsForTazieh(taziehUid: String): List<TaziehVersionEntity>

    @Query("SELECT COALESCE(MAX(versionNumber), 0) FROM tazieh_versions WHERE taziehUid = :taziehUid")
    suspend fun maxVersionNumber(taziehUid: String): Int

    @Query("UPDATE tazieh_versions SET isCurrent = 0 WHERE taziehUid = :taziehUid")
    suspend fun clearCurrentVersion(taziehUid: String): Int

    @Query("UPDATE tazieh_versions SET isCurrent = 1 WHERE uid = :uid")
    suspend fun markCurrentVersion(uid: String): Int

    @Query("SELECT * FROM tazieh_versions WHERE uid = :uid LIMIT 1")
    suspend fun versionByUid(uid: String): TaziehVersionEntity?

    @Query("SELECT * FROM tazieh_versions WHERE taziehUid = :taziehUid AND isCurrent = 1 LIMIT 1")
    suspend fun currentVersion(taziehUid: String): TaziehVersionEntity?

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insertVersion(version: TaziehVersionEntity): Long

    @Query("UPDATE tazieh_versions SET status = :status, updatedAt = :updatedAt, changeNote = :changeNote WHERE uid = :uid")
    suspend fun updateVersionState(uid: String, status: String, updatedAt: Long, changeNote: String): Int

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insertSourceDocument(document: SourceDocumentEntity): Long

    @Query("SELECT * FROM source_documents WHERE versionUid = :versionUid ORDER BY importedAt")
    suspend fun documentsForVersion(versionUid: String): List<SourceDocumentEntity>

    @Query("SELECT * FROM source_documents WHERE uid = :uid LIMIT 1")
    suspend fun documentByUid(uid: String): SourceDocumentEntity?

    @Query("UPDATE source_documents SET reviewStatus = :status, notes = :notes WHERE uid = :uid")
    suspend fun updateDocumentReview(uid: String, status: String, notes: String): Int

}
