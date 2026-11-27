package com.example.bookapp.data

import androidx.room.*
import kotlinx.coroutines.flow.Flow

@Dao
interface GlossaryDao {
    @Query("SELECT * FROM glossary_terms ORDER BY normalizedHeadword") suspend fun allTerms(): List<GlossaryTermEntity>
    @Query("SELECT * FROM glossary_terms ORDER BY normalizedHeadword") fun observeAllTerms(): Flow<List<GlossaryTermEntity>>
    @Query("SELECT * FROM glossary_spellings ORDER BY id") suspend fun allSpellings(): List<GlossarySpellingEntity>
    @Query("SELECT * FROM glossary_spellings ORDER BY id") fun observeAllSpellings(): Flow<List<GlossarySpellingEntity>>
    @Query("SELECT * FROM glossary_meanings ORDER BY id") suspend fun allMeanings(): List<GlossaryMeaningEntity>
    @Query("SELECT * FROM glossary_meanings ORDER BY id") fun observeAllMeanings(): Flow<List<GlossaryMeaningEntity>>
    @Query("SELECT * FROM glossary_terms WHERE id = :id LIMIT 1") suspend fun term(id: Long): GlossaryTermEntity?
    @Query("SELECT * FROM glossary_terms WHERE normalizedHeadword = :normalized LIMIT 1") suspend fun byNormalizedHeadword(normalized: String): GlossaryTermEntity?
    @Insert(onConflict = OnConflictStrategy.ABORT) suspend fun insertTerm(term: GlossaryTermEntity): Long
    @Update suspend fun updateTerm(term: GlossaryTermEntity): Int
    @Query("SELECT * FROM glossary_meanings WHERE termId = :termId ORDER BY id") suspend fun meanings(termId: Long): List<GlossaryMeaningEntity>
    @Insert(onConflict = OnConflictStrategy.ABORT) suspend fun insertMeaning(meaning: GlossaryMeaningEntity): Long
    @Query("SELECT * FROM glossary_meanings WHERE termId = :termId AND meaning = :meaning AND sourceTitle = :sourceTitle AND sourceAuthor = :sourceAuthor AND sourcePage = :sourcePage AND IFNULL(usageSectionUid, '') = IFNULL(:usageSectionUid, '') LIMIT 1") suspend fun duplicateMeaning(termId: Long, meaning: String, sourceTitle: String, sourceAuthor: String, sourcePage: String, usageSectionUid: String?): GlossaryMeaningEntity?
    @Update suspend fun updateMeaning(meaning: GlossaryMeaningEntity): Int
    @Query("SELECT * FROM glossary_spellings WHERE termId = :termId ORDER BY id") suspend fun spellings(termId: Long): List<GlossarySpellingEntity>
    @Query("SELECT * FROM glossary_spellings WHERE normalizedSpelling = :normalized LIMIT 1") suspend fun spellingByNormalized(normalized: String): GlossarySpellingEntity?
    @Insert(onConflict = OnConflictStrategy.ABORT) suspend fun insertSpelling(spelling: GlossarySpellingEntity): Long
    @Query("SELECT * FROM glossary_history WHERE termId = :termId ORDER BY changedAt DESC") suspend fun history(termId: Long): List<GlossaryHistoryEntity>
    @Insert suspend fun insertHistory(item: GlossaryHistoryEntity): Long
    @Query("SELECT EXISTS(SELECT 1 FROM glossary_history WHERE action = :action)") suspend fun hasImportPackage(action: String): Boolean
    @Query("SELECT * FROM glossary_terms WHERE reviewStatus = :status ORDER BY normalizedHeadword") suspend fun termsByStatus(status: String): List<GlossaryTermEntity>
}
