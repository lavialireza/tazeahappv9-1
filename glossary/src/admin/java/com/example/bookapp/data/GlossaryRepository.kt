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
    val notes: String = "",
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
                pronunciation = input.pronunciation.trim(), etymology = input.etymology.trim(), notes = input.notes.trim(),
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

    /** Edit a headword and its descriptive fields without changing its identity or review state. */
    suspend fun updateTermDetails(
        termId: Long,
        headword: String,
        partOfSpeech: String,
        pronunciation: String,
        etymology: String,
        notes: String
    ) = database.withTransaction {
        val current = dao.term(termId) ?: error("مدخل موردنظر پیدا نشد.")
        val cleanHeadword = headword.trim()
        val normalized = GlossaryNormalizer.normalize(cleanHeadword)
        require(normalized.isNotBlank()) { "سرمدخل معتبر نیست." }
        val owner = dao.byNormalizedHeadword(normalized)
        require(owner == null || owner.id == termId) { "این واژه قبلاً به‌عنوان سرمدخل ثبت شده است." }
        val spellingOwner = dao.allSpellings().firstOrNull { it.normalizedSpelling == normalized }
        require(spellingOwner == null) {
            "این واژه قبلاً به‌عنوان املای جایگزین ثبت شده است؛ ابتدا املای جایگزین را اصلاح کنید."
        }
        val now = System.currentTimeMillis()
        val before = JSONObject()
            .put("headword", current.headword)
            .put("partOfSpeech", current.partOfSpeech)
            .put("pronunciation", current.pronunciation)
            .put("etymology", current.etymology)
            .put("notes", current.notes)
        val updated = current.copy(
            headword = cleanHeadword,
            normalizedHeadword = normalized,
            partOfSpeech = partOfSpeech.trim(),
            pronunciation = pronunciation.trim(),
            etymology = etymology.trim(),
            notes = notes.trim(),
            updatedAt = now
        )
        dao.updateTerm(updated)
        dao.insertHistory(
            GlossaryHistoryEntity(
                termId = termId,
                action = "EDIT_TERM",
                beforeJson = before.toString(),
                afterJson = JSONObject()
                    .put("headword", updated.headword)
                    .put("partOfSpeech", updated.partOfSpeech)
                    .put("pronunciation", updated.pronunciation)
                    .put("etymology", updated.etymology)
                    .put("notes", updated.notes)
                    .toString(),
                changedAt = now
            )
        )
    }

    /** Edit an alternate spelling; uniqueness is checked across all headwords and spellings. */
    suspend fun updateSpellingDetails(spellingId: Long, spelling: String) = database.withTransaction {
        val current = dao.spelling(spellingId) ?: error("املای جایگزین پیدا نشد.")
        val clean = spelling.trim()
        val normalized = GlossaryNormalizer.normalize(clean)
        require(normalized.isNotBlank()) { "املای جایگزین معتبر نیست." }
        val ownerTerm = dao.term(current.termId) ?: error("مدخل واژه پیدا نشد.")
        require(normalized != ownerTerm.normalizedHeadword) { "املای جایگزین نمی‌تواند با سرمدخل یکسان باشد." }
        val spellingOwner = dao.allSpellings().firstOrNull {
            it.id != spellingId && it.normalizedSpelling == normalized
        }
        require(spellingOwner == null || spellingOwner.termId == current.termId) {
            "این صورت نوشتاری قبلاً به مدخل دیگری اختصاص یافته است."
        }
        val headwordOwner = dao.byNormalizedHeadword(normalized)
        require(headwordOwner == null || headwordOwner.id == current.termId) {
            "این صورت نوشتاری، سرمدخلِ مدخل دیگری است."
        }
        val now = System.currentTimeMillis()
        dao.updateSpelling(current.copy(spelling = clean, normalizedSpelling = normalized))
        dao.insertHistory(
            GlossaryHistoryEntity(
                termId = current.termId,
                action = "EDIT_SPELLING",
                beforeJson = JSONObject().put("spelling", current.spelling).toString(),
                afterJson = JSONObject().put("spelling", clean).toString(),
                changedAt = now
            )
        )
    }

    /** Edit one meaning and its bibliographic/evidence fields while preserving status and UID. */
    suspend fun updateMeaningDetails(
        meaningId: Long,
        meaning: String,
        sourceTitle: String,
        sourceAuthor: String,
        sourcePage: String,
        sourceUrl: String,
        quote: String,
        taziehUid: String,
        versionUid: String,
        roleUid: String,
        sectionUid: String
    ) = database.withTransaction {
        val current = dao.meaning(meaningId) ?: error("معنی موردنظر پیدا نشد.")
        val cleanMeaning = meaning.trim()
        require(cleanMeaning.isNotBlank()) { "متن معنی نمی‌تواند خالی باشد." }
        val now = System.currentTimeMillis()
        val before = JSONObject()
            .put("meaning", current.meaning)
            .put("sourceTitle", current.sourceTitle)
            .put("sourceAuthor", current.sourceAuthor)
            .put("sourcePage", current.sourcePage)
            .put("sourceUrl", current.sourceUrl)
            .put("taziehUid", current.usageTaziehUid ?: "")
            .put("versionUid", current.usageVersionUid ?: "")
            .put("roleUid", current.usageRoleUid ?: "")
            .put("sectionUid", current.usageSectionUid ?: "")
            .put("quote", current.usageQuote)
        val updated = current.copy(
            meaning = cleanMeaning,
            sourceTitle = sourceTitle.trim(),
            sourceAuthor = sourceAuthor.trim(),
            sourcePage = sourcePage.trim(),
            sourceUrl = sourceUrl.trim(),
            usageTaziehUid = taziehUid.trim().ifBlank { null },
            usageVersionUid = versionUid.trim().ifBlank { null },
            usageRoleUid = roleUid.trim().ifBlank { null },
            usageSectionUid = sectionUid.trim().ifBlank { null },
            usageQuote = quote.trim(),
            updatedAt = now
        )
        dao.updateMeaning(updated)
        dao.insertHistory(
            GlossaryHistoryEntity(
                termId = current.termId,
                action = "EDIT_MEANING",
                beforeJson = before.toString(),
                afterJson = JSONObject()
                    .put("meaning", updated.meaning)
                    .put("sourceTitle", updated.sourceTitle)
                    .put("sourceAuthor", updated.sourceAuthor)
                    .put("sourcePage", updated.sourcePage)
                    .put("sourceUrl", updated.sourceUrl)
                    .put("taziehUid", updated.usageTaziehUid ?: "")
                    .put("versionUid", updated.usageVersionUid ?: "")
                    .put("roleUid", updated.usageRoleUid ?: "")
                    .put("sectionUid", updated.usageSectionUid ?: "")
                    .put("quote", updated.usageQuote)
                    .toString(),
                changedAt = now
            )
        )
    }

}
