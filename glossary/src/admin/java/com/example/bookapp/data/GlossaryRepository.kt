package com.example.bookapp.data

import androidx.room.RoomDatabase
import androidx.room.withTransaction
import org.json.JSONObject
import java.util.UUID

/** Input for a new term/meaning proposal. Existing approved data is never overwritten. */
data class GlossarySuggestionInput(
    val headword: String,
    val meaning: String,
    val spelling: String = "",
    val partOfSpeech: String = "",
    val pronunciation: String = "",
    val etymology: String = "",
    val sourceTitle: String = "",
    val sourceAuthor: String = "",
    val sourcePage: String = "",
    val sourceUrl: String = "",
    val taziehUid: String? = null,
    val versionUid: String? = null,
    val roleUid: String? = null,
    val sectionUid: String? = null,
    val quote: String = ""
)

data class GlossarySuggestionResult(val termId: Long, val termCreated: Boolean, val meaningCreated: Boolean, val spellingCreated: Boolean)

/** Shared transactional write path for glossary data in Admin and import workflows. */
class GlossaryRepository(private val database: RoomDatabase, private val dao: GlossaryDao) {
    suspend fun addSuggestion(input: GlossarySuggestionInput): GlossarySuggestionResult = database.withTransaction {
        val headword = input.headword.trim()
        val meaning = input.meaning.trim()
        val normalized = GlossaryNormalizer.normalize(headword)
        require(normalized.isNotBlank()) { "سرمدخل معتبر نیست." }
        require(meaning.isNotBlank()) { "معنی نمی‌تواند خالی باشد." }

        val existing = dao.byNormalizedHeadword(normalized)
        val now = System.currentTimeMillis()
        val termId: Long
        val termCreated: Boolean
        if (existing == null) {
            val draft = GlossaryTermEntity(
                uid = "term-${UUID.randomUUID()}", headword = headword,
                normalizedHeadword = normalized, partOfSpeech = input.partOfSpeech.trim(),
                pronunciation = input.pronunciation.trim(), etymology = input.etymology.trim(),
                createdAt = now, updatedAt = now
            )
            termId = dao.insertTerm(draft)
            termCreated = true
        } else {
            termId = existing.id
            termCreated = false
        }

        var spellingCreated = false
        val spelling = input.spelling.trim()
        if (spelling.isNotBlank()) {
            val normalizedSpelling = GlossaryNormalizer.normalize(spelling)
            require(normalizedSpelling != normalized) { "صورت نوشتاری با خود سرمدخل یکسان است." }
            // A normalized spelling is globally unique. Never reassign one silently.
            val spellingOwner = dao.allSpellings().firstOrNull { it.normalizedSpelling == normalizedSpelling }
            require(spellingOwner == null || spellingOwner.termId == termId) {
                "این صورت نوشتاری قبلاً به مدخل دیگری اختصاص یافته است."
            }
            val headwordOwner = dao.allTerms().firstOrNull { it.normalizedHeadword == normalizedSpelling }
            require(headwordOwner == null || headwordOwner.id == termId) {
                "این صورت نوشتاری، سرمدخلِ مدخل دیگری است."
            }
            if (spellingOwner == null) {
                dao.insertSpelling(GlossarySpellingEntity(termId = termId, spelling = spelling, normalizedSpelling = normalizedSpelling))
                spellingCreated = true
            }
        }

        val duplicate = dao.duplicateMeaning(termId, meaning, input.sourceTitle.trim(), input.sourceAuthor.trim(), input.sourcePage.trim())
        val meaningCreated = duplicate == null
        if (meaningCreated) {
            val row = GlossaryMeaningEntity(
                uid = "meaning-${UUID.randomUUID()}", termId = termId, meaning = meaning,
                sourceTitle = input.sourceTitle.trim(), sourceAuthor = input.sourceAuthor.trim(),
                sourcePage = input.sourcePage.trim(), sourceUrl = input.sourceUrl.trim(),
                usageTaziehUid = input.taziehUid?.trim()?.ifBlank { null },
                usageVersionUid = input.versionUid?.trim()?.ifBlank { null },
                usageRoleUid = input.roleUid?.trim()?.ifBlank { null },
                usageSectionUid = input.sectionUid?.trim()?.ifBlank { null },
                usageQuote = input.quote.trim(), createdAt = now, updatedAt = now
            )
            dao.insertMeaning(row)
        }

        dao.insertHistory(GlossaryHistoryEntity(
            termId = termId,
            action = when {
                termCreated && meaningCreated -> "CREATE_TERM_AND_MEANING"
                meaningCreated -> "ADD_MEANING"
                spellingCreated -> "ADD_SPELLING"
                else -> "DUPLICATE_SUGGESTION_IGNORED"
            },
            beforeJson = "{}",
            afterJson = JSONObject()
                .put("headword", headword)
                .put("normalizedHeadword", normalized)
                .put("meaningAdded", meaningCreated)
                .put("spellingAdded", spellingCreated)
                .toString(),
            changedAt = now
        ))
        GlossarySuggestionResult(termId, termCreated, meaningCreated, spellingCreated)
    }
}
