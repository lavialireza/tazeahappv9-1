package com.example.bookapp.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update

@Dao
interface EncyclopediaDao {
    @Insert(onConflict = OnConflictStrategy.ABORT) suspend fun insertRecord(record: EncyclopediaRecordEntity): Long
    @Update suspend fun updateRecord(record: EncyclopediaRecordEntity): Int
    @Query("SELECT * FROM encyclopedia_records WHERE id = :id LIMIT 1") suspend fun record(id: Long): EncyclopediaRecordEntity?
    @Query("SELECT * FROM encyclopedia_records WHERE (:kind = 'ALL' OR kind = :kind) AND (:status = 'ALL' OR reviewStatus = :status) AND (:query = '' OR normalizedTitle LIKE '%' || :query || '%' OR aliases LIKE '%' || :query || '%' OR summary LIKE '%' || :query || '%' OR details LIKE '%' || :query || '%') ORDER BY updatedAt DESC, title COLLATE NOCASE") suspend fun search(kind: String, status: String, query: String): List<EncyclopediaRecordEntity>
    @Insert(onConflict = OnConflictStrategy.IGNORE) suspend fun insertLink(link: EncyclopediaLinkEntity): Long
    @Query("SELECT * FROM encyclopedia_links WHERE recordId = :recordId ORDER BY id") suspend fun links(recordId: Long): List<EncyclopediaLinkEntity>
    @Query("DELETE FROM encyclopedia_links WHERE recordId = :recordId") suspend fun deleteLinksForRecord(recordId: Long): Int
    @Query("DELETE FROM encyclopedia_links WHERE id = :id") suspend fun deleteLink(id: Long): Int
    @Insert suspend fun insertHistory(history: EncyclopediaHistoryEntity): Long
    @Query("SELECT * FROM encyclopedia_history WHERE recordId = :recordId ORDER BY changedAt DESC") suspend fun history(recordId: Long): List<EncyclopediaHistoryEntity>
    @Query("SELECT COUNT(*) FROM encyclopedia_records WHERE kind = :kind") suspend fun count(kind: String): Int
    @Query("SELECT * FROM encyclopedia_records WHERE reviewStatus = 'VERIFIED' ORDER BY kind, title COLLATE NOCASE") suspend fun allVerified(): List<EncyclopediaRecordEntity>
    @Query("SELECT * FROM encyclopedia_links ORDER BY recordId, id") suspend fun allLinks(): List<EncyclopediaLinkEntity>
}
