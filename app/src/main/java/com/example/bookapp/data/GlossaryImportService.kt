package com.example.bookapp.data

import androidx.room.withTransaction
import org.json.JSONArray
import org.json.JSONObject
import java.util.UUID

/** Immutable preview row. Nothing is written to Room until apply() is explicitly called. */
data class GlossaryImportPreview(
    val packageUid: String,
    val terms: List<GlossaryImportTerm>,
    val errors: List<String>,
    val warnings: List<String>
) {
    val meaningCount: Int get() = terms.sumOf { it.meanings.size }
    val spellingCount: Int get() = terms.sumOf { it.spellings.size }
    val isValid: Boolean get() = errors.isEmpty() && terms.isNotEmpty()
}

data class GlossaryImportTerm(
    val headword: String,
    val partOfSpeech: String,
    val pronunciation: String,
    val etymology: String,
    val notes: String,
    val spellings: List<String>,
    val meanings: List<GlossaryImportMeaning>
)

data class GlossaryImportMeaning(
    val meaning: String,
    val sourceTitle: String,
    val sourceAuthor: String,
    val sourcePage: String,
    val sourceUrl: String,
    val usageTaziehUid: String?,
    val usageVersionUid: String?,
    val usageRoleUid: String?,
    val usageSectionUid: String,
    val usageQuote: String,
    val usageFieldTitle: String = "",
    val usageTaziehTitle: String = "",
    val usageRoleTitle: String = "",
    val usageSectionTitle: String = ""
)

data class GlossaryImportResult(val addedTerms: Int, val reusedTerms: Int, val addedMeanings: Int, val skippedMeanings: Int, val addedSpellings: Int, val skippedSpellings: Int)

/**
 * JSON package contract v1. All imported records are created as PENDING, never auto-approved.
 * Import is additive and transactional; a malformed package cannot partially update the glossary.
 */
class GlossaryImportService(private val db: AppDatabase) {
    fun preview(json: String): GlossaryImportPreview {
        val errors = mutableListOf<String>()
        val warnings = mutableListOf<String>()
        val root = runCatching { JSONObject(json) }.getOrElse {
            return GlossaryImportPreview("", emptyList(), listOf("ساختار JSON معتبر نیست: ${it.message}"), emptyList())
        }
        val version = root.optInt("schemaVersion", -1)
        if (version != 1) errors += "نسخه قالب پشتیبانی نمی‌شود؛ schemaVersion باید 1 باشد."
        val packageUid = root.optString("packageUid").trim()
        if (packageUid.isBlank()) errors += "شناسه بسته (packageUid) الزامی است."
        val termsArray = root.optJSONArray("terms")
        if (termsArray == null) errors += "آرایه terms در فایل وجود ندارد."
        val parsed = mutableListOf<GlossaryImportTerm>()
        val seenHeadwords = mutableSetOf<String>()
        if (termsArray != null) {
            for (i in 0 until termsArray.length()) {
                val item = termsArray.optJSONObject(i)
                if (item == null) { errors += "ردیف واژه ${i + 1} ساختار شیء ندارد."; continue }
                val headword = item.optString("headword").trim()
                val normalized = normalize(headword)
                if (normalized.isBlank()) { errors += "واژه ردیف ${i + 1} خالی است."; continue }
                if (!seenHeadwords.add(normalized)) errors += "واژه تکراری در همین بسته: $headword"
                val spellings = readStrings(item.optJSONArray("spellings"))
                    .map { it.trim() }.filter { it.isNotBlank() }.distinctBy(::normalize)
                val meaningsArray = item.optJSONArray("meanings")
                if (meaningsArray == null || meaningsArray.length() == 0) {
                    errors += "برای واژه «$headword» دست‌کم یک معنی لازم است."
                }
                val meanings = mutableListOf<GlossaryImportMeaning>()
                if (meaningsArray != null) {
                    for (j in 0 until meaningsArray.length()) {
                        val meaningObject = meaningsArray.optJSONObject(j)
                        if (meaningObject == null) { errors += "معنی ${j + 1} واژه «$headword» معتبر نیست."; continue }
                        val meaning = meaningObject.optString("meaning").trim()
                        val sourceTitle = meaningObject.optString("sourceTitle").trim()
                        val sourceUrl = meaningObject.optString("sourceUrl").trim()
                        val sectionUid = meaningObject.optString("usageSectionUid").trim()
                        val fieldTitle = meaningObject.optString("usageFieldTitle").trim()
                        val taziehTitle = meaningObject.optString("usageTaziehTitle").trim()
                        val roleTitle = meaningObject.optString("usageRoleTitle").trim()
                        val sectionTitle = meaningObject.optString("usageSectionTitle").trim()
                        val hasCompletePath = fieldTitle.isNotBlank() && taziehTitle.isNotBlank() && roleTitle.isNotBlank() && sectionTitle.isNotBlank()
                        if (meaning.isBlank()) errors += "متن معنی برای «$headword» خالی است."
                        if (sourceTitle.isBlank() && sourceUrl.isBlank()) errors += "برای معنی «$headword» باید منبع یا نشانی منبع ثبت شود."
                        if (sectionUid.isBlank() && !hasCompletePath) errors += "کاربرد معنی «$headword» باید شناسه بخش یا مسیر کامل عنوانی (رشته، مجلس، نقش و بخش) داشته باشد."
                        meanings += GlossaryImportMeaning(
                            meaning, sourceTitle, meaningObject.optString("sourceAuthor").trim(), meaningObject.optString("sourcePage").trim(), sourceUrl,
                            meaningObject.optString("usageTaziehUid").trim().ifBlank { null },
                            meaningObject.optString("usageVersionUid").trim().ifBlank { null },
                            meaningObject.optString("usageRoleUid").trim().ifBlank { null }, sectionUid,
                            meaningObject.optString("usageQuote").trim(), fieldTitle, taziehTitle, roleTitle, sectionTitle
                        )
                    }
                }
                parsed += GlossaryImportTerm(
                    headword, item.optString("partOfSpeech").trim(), item.optString("pronunciation").trim(),
                    item.optString("etymology").trim(), item.optString("notes").trim(), spellings, meanings
                )
            }
        }
        if (parsed.isEmpty()) errors += "هیچ واژه قابل ورودی در بسته پیدا نشد."
        if (parsed.any { it.meanings.isEmpty() }) warnings += "برخی واژه‌ها بدون معنی معتبر هستند و در صورت وجود خطا، کل بسته وارد نمی‌شود."
        return GlossaryImportPreview(packageUid, parsed, errors.distinct(), warnings)
    }

    suspend fun apply(preview: GlossaryImportPreview): GlossaryImportResult {
        require(preview.isValid) { "ابتدا خطاهای پیش‌نمایش را برطرف کنید." }
        return db.withTransaction {
            val dao = db.glossaryDao()
            require(!dao.hasImportPackage("IMPORT_PACKAGE:${preview.packageUid}")) {
                "این شناسه بسته قبلاً وارد شده است؛ برای بسته جدید packageUid تازه تعیین کنید."
            }
            var addedTerms = 0; var reusedTerms = 0; var addedMeanings = 0; var skippedMeanings = 0
            var addedSpellings = 0; var skippedSpellings = 0
            val now = System.currentTimeMillis()
            // Validate every contextual reference before writing anything. Room transaction
            // guarantees rollback as an additional safeguard if a later insert fails.
            preview.terms.forEach { incoming ->
                incoming.meanings.forEach { meaning ->
                    val section = resolveUsageSection(meaning)
                    val role = db.roleDao().getById(section.roleId)
                    val tazieh = db.taziehDao().getById(role.taziehId)
                    require(meaning.usageRoleUid.isNullOrBlank() || meaning.usageRoleUid == role.uid) {
                        "شناسه نقش با بخش «${section.title}» مطابقت ندارد."
                    }
                    require(meaning.usageVersionUid.isNullOrBlank() || meaning.usageVersionUid == role.versionUid) {
                        "شناسه نسخه با بخش «${section.title}» مطابقت ندارد."
                    }
                    require(meaning.usageTaziehUid.isNullOrBlank() || meaning.usageTaziehUid == tazieh?.uid) {
                        "شناسه مجلس با بخش «${section.title}» مطابقت ندارد."
                    }
                }
            }
            preview.terms.forEach { incoming ->
                val normalized = normalize(incoming.headword)
                val existing = dao.byNormalizedHeadword(normalized)
                val term = if (existing == null) {
                    val draft = GlossaryTermEntity(
                        uid = "term-${UUID.randomUUID()}", headword = incoming.headword, normalizedHeadword = normalized,
                        partOfSpeech = incoming.partOfSpeech, pronunciation = incoming.pronunciation,
                        etymology = incoming.etymology, notes = incoming.notes,
                        reviewStatus = GlossaryTermEntity.STATUS_PENDING, createdAt = now, updatedAt = now
                    )
                    val id = dao.insertTerm(draft); addedTerms++
                    draft.copy(id = id)
                } else {
                    reusedTerms++
                    dao.updateTerm(existing.copy(
                        partOfSpeech = existing.partOfSpeech.ifBlank { incoming.partOfSpeech },
                        pronunciation = existing.pronunciation.ifBlank { incoming.pronunciation },
                        etymology = existing.etymology.ifBlank { incoming.etymology },
                        notes = existing.notes.ifBlank { incoming.notes }, updatedAt = now
                    ))
                    existing
                }
                incoming.spellings.forEach { spelling ->
                    val key = normalize(spelling)
                    if (key == normalized || dao.spellingByNormalized(key) != null) skippedSpellings++
                    else { dao.insertSpelling(GlossarySpellingEntity(termId = term.id, spelling = spelling, normalizedSpelling = key)); addedSpellings++ }
                }
                incoming.meanings.forEach { meaning ->
                    val resolvedSection = resolveUsageSection(meaning)
                    val resolvedRole = db.roleDao().getById(resolvedSection.roleId)
                    val resolvedTazieh = db.taziehDao().getById(resolvedRole.taziehId)
                    val resolvedMeaning = meaning.copy(
                        usageSectionUid = resolvedSection.uid,
                        usageRoleUid = resolvedRole.uid,
                        usageVersionUid = resolvedRole.versionUid.ifBlank { null },
                        usageTaziehUid = resolvedTazieh?.uid
                    )
                    val duplicate = dao.duplicateMeaning(term.id, meaning.meaning, meaning.sourceTitle, meaning.sourceAuthor, meaning.sourcePage, resolvedSection.uid)
                    if (duplicate != null) {
                        skippedMeanings++
                    } else {
                        dao.insertMeaning(GlossaryMeaningEntity(
                            uid = "meaning-${UUID.randomUUID()}", termId = term.id, meaning = meaning.meaning,
                            sourceTitle = meaning.sourceTitle, sourceAuthor = meaning.sourceAuthor, sourcePage = meaning.sourcePage,
                            sourceUrl = meaning.sourceUrl, usageTaziehUid = resolvedMeaning.usageTaziehUid,
                            usageVersionUid = resolvedMeaning.usageVersionUid, usageRoleUid = resolvedMeaning.usageRoleUid,
                            usageSectionUid = resolvedMeaning.usageSectionUid, usageQuote = meaning.usageQuote,
                            reviewStatus = GlossaryTermEntity.STATUS_PENDING, createdAt = now, updatedAt = now
                        ))
                        addedMeanings++
                    }
                }
                dao.insertHistory(GlossaryHistoryEntity(
                    termId = term.id, action = "IMPORT_PACKAGE:${preview.packageUid}", beforeJson = "{}",
                    afterJson = JSONObject().put("headword", incoming.headword).put("meanings", incoming.meanings.size).toString(), changedAt = now
                ))
            }
            GlossaryImportResult(addedTerms, reusedTerms, addedMeanings, skippedMeanings, addedSpellings, skippedSpellings)
        }
    }


    /** Resolve either a stable section UID or an exact field/tazieh/role/section title path. */
    private suspend fun resolveUsageSection(meaning: GlossaryImportMeaning): SectionEntity {
        if (meaning.usageSectionUid.isNotBlank()) {
            return db.sectionDao().getByUid(meaning.usageSectionUid)
                ?: error("شناسه بخش پیدا نشد: ${meaning.usageSectionUid}")
        }
        val field = db.fieldDao().getAll().singleOrNull { it.title.trim() == meaning.usageFieldTitle }
            ?: error("رشته با عنوان «${meaning.usageFieldTitle}» پیدا نشد یا عنوان آن یکتا نیست.")
        val tazieh = db.taziehDao().getByField(field.id).singleOrNull { it.title.trim() == meaning.usageTaziehTitle }
            ?: error("مجلس «${meaning.usageTaziehTitle}» در رشته انتخاب‌شده پیدا نشد یا یکتا نیست.")
        val role = db.roleDao().getByTazieh(tazieh.id).singleOrNull { it.title.trim() == meaning.usageRoleTitle }
            ?: error("نقش «${meaning.usageRoleTitle}» در مجلس انتخاب‌شده پیدا نشد یا یکتا نیست.")
        return db.sectionDao().getByRole(role.id).singleOrNull { it.title.trim() == meaning.usageSectionTitle }
            ?: error("بخش «${meaning.usageSectionTitle}» در نقش انتخاب‌شده پیدا نشد یا یکتا نیست.")
    }

    private fun readStrings(array: JSONArray?): List<String> = if (array == null) emptyList() else
        (0 until array.length()).mapNotNull { array.optString(it).takeIf { value -> value != "null" } }

    private fun normalize(value: String): String = value.trim().replace('ي', 'ی').replace('ك', 'ک')
        .replace('ۀ', 'ه').replace('ة', 'ه').replace(Regex("[\\u064B-\\u065F\\u0670]"), "")
        .replace(Regex("\\s+"), " ")
}
