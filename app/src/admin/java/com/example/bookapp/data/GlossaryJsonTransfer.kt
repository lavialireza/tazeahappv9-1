package com.example.bookapp.data

import android.content.Context
import androidx.room.RoomDatabase
import androidx.room.withTransaction
import org.json.JSONArray
import org.json.JSONObject
import java.security.MessageDigest
import java.util.UUID

/**
 * Offline JSON interchange for the Admin glossary.
 * Imports are additive: existing headwords/meanings are never overwritten.
 * Imported material always enters review as PENDING.
 */
class GlossaryJsonTransfer(
    private val context: Context,
    private val database: RoomDatabase,
    private val dao: GlossaryDao
) {
    data class ImportResult(
        val alreadyImported: Boolean,
        val termsAdded: Int,
        val meaningsAdded: Int,
        val spellingsAdded: Int
    )

    suspend fun importJson(bytes: ByteArray): ImportResult {
        require(bytes.isNotEmpty()) { "فایل خالی است." }
        require(bytes.size <= MAX_FILE_BYTES) { "حجم فایل بیش از حد مجاز است." }
        val hash = sha256(bytes)
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        if (prefs.getStringSet(KEY_HASHES, emptySet())?.contains(hash) == true) {
            return ImportResult(true, 0, 0, 0)
        }

        val root = JSONObject(bytes.toString(Charsets.UTF_8))
        require(root.optInt("schemaVersion", -1) == SCHEMA_VERSION) {
            "نسخه ساختار فایل پشتیبانی نمی‌شود."
        }
        require(root.optString("format") == FORMAT) { "قالب فایل واژه‌نامه معتبر نیست." }
        val entries = root.optJSONArray("terms") ?: JSONArray()
        var termsAdded = 0
        var meaningsAdded = 0
        var spellingsAdded = 0

        database.withTransaction {
            for (i in 0 until entries.length()) {
                val item = entries.optJSONObject(i) ?: continue
                val headword = item.optString("headword").trim()
                val normalized = GlossaryNormalizer.normalize(headword)
                if (headword.isBlank() || normalized.isBlank()) continue

                var term = dao.byNormalizedHeadword(normalized)
                if (term == null) {
                    val now = System.currentTimeMillis()
                    val id = dao.insertTerm(
                        GlossaryTermEntity(
                            uid = item.optString("uid").ifBlank { "term-${UUID.randomUUID()}" },
                            headword = headword,
                            normalizedHeadword = normalized,
                            partOfSpeech = item.optString("partOfSpeech"),
                            pronunciation = item.optString("pronunciation"),
                            etymology = item.optString("etymology"),
                            notes = item.optString("notes"),
                            reviewStatus = GlossaryTermEntity.STATUS_PENDING,
                            createdAt = now,
                            updatedAt = now
                        )
                    )
                    term = dao.term(id)
                    termsAdded++
                }
                val target = term ?: continue

                val spellings = item.optJSONArray("spellings") ?: JSONArray()
                for (si in 0 until spellings.length()) {
                    val spelling = spellings.optString(si).trim()
                    val normSpelling = GlossaryNormalizer.normalize(spelling)
                    if (spelling.isBlank() || normSpelling.isBlank() || normSpelling == normalized) continue
                    val allSpellings = dao.allSpellings()
                    val spellingOwner = allSpellings.firstOrNull { it.normalizedSpelling == normSpelling }
                    val headwordOwner = dao.byNormalizedHeadword(normSpelling)
                    if ((spellingOwner == null || spellingOwner.termId == target.id) &&
                        (headwordOwner == null || headwordOwner.id == target.id) &&
                        spellingOwner == null
                    ) {
                        dao.insertSpelling(
                            GlossarySpellingEntity(
                                termId = target.id,
                                spelling = spelling,
                                normalizedSpelling = normSpelling
                            )
                        )
                        spellingsAdded++
                    }
                }

                val meanings = item.optJSONArray("meanings") ?: JSONArray()
                for (mi in 0 until meanings.length()) {
                    val m = meanings.optJSONObject(mi) ?: continue
                    val meaning = m.optString("meaning").trim()
                    if (meaning.isBlank()) continue
                    val sourceTitle = m.optString("sourceTitle").trim()
                    val sourceAuthor = m.optString("sourceAuthor").trim()
                    val sourcePage = m.optString("sourcePage").trim()
                    if (dao.duplicateMeaning(target.id, meaning, sourceTitle, sourceAuthor, sourcePage) != null) continue
                    val now = System.currentTimeMillis()
                    dao.insertMeaning(
                        GlossaryMeaningEntity(
                            uid = m.optString("uid").ifBlank { "meaning-${UUID.randomUUID()}" },
                            termId = target.id,
                            meaning = meaning,
                            sourceTitle = sourceTitle,
                            sourceAuthor = sourceAuthor,
                            sourcePage = sourcePage,
                            sourceUrl = m.optString("sourceUrl"),
                            usageTaziehUid = m.optString("taziehUid").takeIf { it.isNotBlank() },
                            usageVersionUid = m.optString("versionUid").takeIf { it.isNotBlank() },
                            usageRoleUid = m.optString("roleUid").takeIf { it.isNotBlank() },
                            usageSectionUid = m.optString("sectionUid").takeIf { it.isNotBlank() },
                            usageQuote = m.optString("quote"),
                            reviewStatus = GlossaryTermEntity.STATUS_PENDING,
                            createdAt = now,
                            updatedAt = now
                        )
                    )
                    meaningsAdded++
                }
                dao.insertHistory(
                    GlossaryHistoryEntity(
                        termId = target.id,
                        action = "JSON_IMPORT",
                        beforeJson = "{}",
                        afterJson = JSONObject()
                            .put("sourceReleaseId", root.optString("releaseId"))
                            .put("importHash", hash)
                            .toString(),
                        changedAt = System.currentTimeMillis()
                    )
                )
            }
        }

        val hashes = (prefs.getStringSet(KEY_HASHES, emptySet()) ?: emptySet()).toMutableSet()
        hashes.add(hash)
        prefs.edit().putStringSet(KEY_HASHES, hashes).apply()
        return ImportResult(false, termsAdded, meaningsAdded, spellingsAdded)
    }

    suspend fun exportVerifiedJson(): ByteArray {
        val terms = dao.termsByStatus(GlossaryTermEntity.STATUS_VERIFIED)
        val outputTerms = JSONArray()
        for (term in terms) {
            val meanings = JSONArray()
            dao.meanings(term.id)
                .filter { it.reviewStatus == GlossaryTermEntity.STATUS_VERIFIED }
                .forEach { m ->
                    meanings.put(
                        JSONObject()
                            .put("uid", m.uid)
                            .put("meaning", m.meaning)
                            .put("sourceTitle", m.sourceTitle)
                            .put("sourceAuthor", m.sourceAuthor)
                            .put("sourcePage", m.sourcePage)
                            .put("sourceUrl", m.sourceUrl)
                            .put("taziehUid", m.usageTaziehUid ?: "")
                            .put("versionUid", m.usageVersionUid ?: "")
                            .put("roleUid", m.usageRoleUid ?: "")
                            .put("sectionUid", m.usageSectionUid ?: "")
                            .put("quote", m.usageQuote)
                    )
                }
            val spellings = JSONArray()
            dao.spellings(term.id).forEach { spellings.put(it.spelling) }
            outputTerms.put(
                JSONObject()
                    .put("uid", term.uid)
                    .put("headword", term.headword)
                    .put("partOfSpeech", term.partOfSpeech)
                    .put("pronunciation", term.pronunciation)
                    .put("etymology", term.etymology)
                    .put("notes", term.notes)
                    .put("spellings", spellings)
                    .put("meanings", meanings)
            )
        }
        val releaseId = "glossary-${System.currentTimeMillis()}"
        val payload = JSONObject()
            .put("format", FORMAT)
            .put("schemaVersion", SCHEMA_VERSION)
            .put("releaseId", releaseId)
            .put("generatedAt", System.currentTimeMillis())
            .put("terms", outputTerms)
        val canonical = payload.toString()
        payload.put("sha256", sha256(canonical.toByteArray(Charsets.UTF_8)))
        return payload.toString(2).toByteArray(Charsets.UTF_8)
    }

    private fun sha256(bytes: ByteArray): String =
        MessageDigest.getInstance("SHA-256").digest(bytes).joinToString("") { "%02x".format(it) }

    companion object {
        const val FORMAT = "tazieh-glossary"
        const val SCHEMA_VERSION = 1
        private const val PREFS = "glossary_json_imports"
        private const val KEY_HASHES = "imported_sha256"
        private const val MAX_FILE_BYTES = 10 * 1024 * 1024
    }
}
