package com.example.bookapp.data

import androidx.room.*

@Dao
interface GlossaryDao {
    @Query("SELECT * FROM glossary_terms ORDER BY normalizedHeadword") suspend fun allTerms(): List<GlossaryTermEntity>
    @Query("SELECT * FROM glossary_spellings ORDER BY id") suspend fun allSpellings(): List<GlossarySpellingEntity>
    @Query("SELECT * FROM glossary_meanings ORDER BY id") suspend fun allMeanings(): List<GlossaryMeaningEntity>
    @Query("SELECT * FROM glossary_terms WHERE id = :id LIMIT 1") suspend fun term(id: Long): GlossaryTermEntity?
    @Query("SELECT * FROM glossary_terms WHERE normalizedHeadword = :normalized LIMIT 1") suspend fun byNormalizedHeadword(normalized: String): GlossaryTermEntity?
    @Insert(onConflict = OnConflictStrategy.ABORT) suspend fun insertTerm(term: GlossaryTermEntity): Long
    @Update suspend fun updateTerm(term: GlossaryTermEntity): Int
    @Query("SELECT * FROM glossary_meanings WHERE termId = :termId ORDER BY id") suspend fun meanings(termId: Long): List<GlossaryMeaningEntity>
    @Insert(onConflict = OnConflictStrategy.ABORT) suspend fun insertMeaning(meaning: GlossaryMeaningEntity): Long
    @Query("SELECT * FROM glossary_meanings WHERE termId = :termId AND meaning = :meaning AND sourceTitle = :sourceTitle AND sourceAuthor = :sourceAuthor AND sourcePage = :sourcePage LIMIT 1") suspend fun duplicateMeaning(termId: Long, meaning: String, sourceTitle: String, sourceAuthor: String, sourcePage: String): GlossaryMeaningEntity?
    @Update suspend fun updateMeaning(meaning: GlossaryMeaningEntity): Int
    @Query("SELECT * FROM glossary_spellings WHERE termId = :termId ORDER BY id") suspend fun spellings(termId: Long): List<GlossarySpellingEntity>
    @Insert(onConflict = OnConflictStrategy.ABORT) suspend fun insertSpelling(spelling: GlossarySpellingEntity): Long
    @Query("SELECT * FROM glossary_history WHERE termId = :termId ORDER BY changedAt DESC") suspend fun history(termId: Long): List<GlossaryHistoryEntity>
    @Insert suspend fun insertHistory(item: GlossaryHistoryEntity): Long
    @Query("SELECT * FROM glossary_terms WHERE reviewStatus = :status ORDER BY normalizedHeadword") suspend fun termsByStatus(status: String): List<GlossaryTermEntity>
}
