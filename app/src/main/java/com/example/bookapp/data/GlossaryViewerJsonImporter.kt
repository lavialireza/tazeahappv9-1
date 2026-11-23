package com.example.bookapp.data

import android.content.Context
import androidx.room.withTransaction
import org.json.JSONArray
import org.json.JSONObject
import java.security.MessageDigest
import java.util.UUID

/** Read-only publication intake for Viewer. Imported rows are accepted only from
 * the Admin export envelope, verified by its SHA-256 checksum, and appended. */
class GlossaryViewerJsonImporter(
    private val context: Context,
    private val database: AppDatabase,
    private val dao: GlossaryDao
) {
    data class ImportResult(
        val alreadyImported: Boolean,
        val termsAdded: Int,
        val meaningsAdded: Int,
        val spellingsAdded: Int,
        val releaseId: String
    )

    suspend fun import(bytes: ByteArray): ImportResult {
        require(bytes.isNotEmpty()) { "فایل خالی است." }
        require(bytes.size <= MAX_FILE_BYTES) { "حجم فایل بیش از ۱۰ مگابایت است." }
        val root = JSONObject(bytes.toString(Charsets.UTF_8))
        require(root.optString("format") == FORMAT) { "قالب فایل واژه‌نامه معتبر نیست." }
        require(root.optInt("schemaVersion", -1) == SCHEMA_VERSION) { "نسخه ساختار فایل پشتیبانی نمی‌شود." }
        val releaseId = root.optString("releaseId").trim()
        require(releaseId.isNotBlank()) { "شناسه انتشار در فایل وجود ندارد." }
        val generatedAt = root.optLong("generatedAt", -1L)
        require(generatedAt > 0L) { "تاریخ انتشار فایل معتبر نیست." }
        val entries = root.optJSONArray("terms") ?: error("فهرست واژه‌ها در فایل وجود ندارد.")
        val expectedHash = root.optString("sha256").trim().lowercase()
        require(expectedHash.matches(Regex("[0-9a-f]{64}"))) { "شناسه صحت فایل معتبر نیست." }
        val unsigned = JSONObject(root.toString()).apply { remove("sha256") }
        val actualHash = sha256(unsigned.toString().toByteArray(Charsets.UTF_8))
        require(actualHash == expectedHash) { "صحت فایل تأیید نشد؛ فایل ممکن است ناقص یا دست‌کاری شده باشد." }

        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val hashes = (prefs.getStringSet(KEY_HASHES, emptySet()) ?: emptySet()).toMutableSet()
        val hash = sha256(bytes)
        if (hashes.contains(hash) || prefs.getString(KEY_RELEASE, null) == releaseId) {
            return ImportResult(true, 0, 0, 0, releaseId)
        }
        val lastGeneratedAt = prefs.getLong(KEY_GENERATED_AT, 0L)
        require(generatedAt >= lastGeneratedAt) { "این فایل قدیمی‌تر از آخرین انتشار نصب‌شده است." }

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
                            uid = item.optString("uid").ifBlank { "viewer-term-${UUID.randomUUID()}" },
                            headword = headword,
                            normalizedHeadword = normalized,
                            partOfSpeech = item.optString("partOfSpeech"),
                            pronunciation = item.optString("pronunciation"),
                            etymology = item.optString("etymology"),
                            notes = item.optString("notes"),
                            reviewStatus = GlossaryTermEntity.STATUS_VERIFIED,
                            createdAt = now,
                            updatedAt = now
                        )
                    )
                    term = dao.term(id)
                    termsAdded++
                }
                val target = term ?: continue
                // A matching headword is now present in an Admin-verified release.
                // Preserve its local text/metadata; only promote its publication status.
                if (target.reviewStatus != GlossaryTermEntity.STATUS_VERIFIED) {
                    dao.updateTerm(target.copy(reviewStatus = GlossaryTermEntity.STATUS_VERIFIED, updatedAt = System.currentTimeMillis()))
                }

                val spellings = item.optJSONArray("spellings") ?: JSONArray()
                val existingSpellings = dao.allSpellings().associateBy { it.normalizedSpelling }.toMutableMap()
                for (si in 0 until spellings.length()) {
                    val spelling = spellings.optString(si).trim()
                    val normalizedSpelling = GlossaryNormalizer.normalize(spelling)
                    if (spelling.isBlank() || normalizedSpelling.isBlank() || normalizedSpelling == normalized) continue
                    val owner = existingSpellings[normalizedSpelling]
                    val headwordOwner = dao.byNormalizedHeadword(normalizedSpelling)
                    if (owner == null && (headwordOwner == null || headwordOwner.id == target.id)) {
                        val spellingId = dao.insertSpelling(GlossarySpellingEntity(termId = target.id, spelling = spelling, normalizedSpelling = normalizedSpelling))
                        existingSpellings[normalizedSpelling] = GlossarySpellingEntity(id = spellingId, termId = target.id, spelling = spelling, normalizedSpelling = normalizedSpelling)
                        spellingsAdded++
                    }
                }

                val meanings = item.optJSONArray("meanings") ?: JSONArray()
                for (mi in 0 until meanings.length()) {
                    val meaningItem = meanings.optJSONObject(mi) ?: continue
                    val meaning = meaningItem.optString("meaning").trim()
                    if (meaning.isBlank()) continue
                    val sourceTitle = meaningItem.optString("sourceTitle").trim()
                    val sourceAuthor = meaningItem.optString("sourceAuthor").trim()
                    val sourcePage = meaningItem.optString("sourcePage").trim()
                    if (dao.duplicateMeaning(target.id, meaning, sourceTitle, sourceAuthor, sourcePage) != null) continue
                    val now = System.currentTimeMillis()
                    dao.insertMeaning(
                        GlossaryMeaningEntity(
                            uid = meaningItem.optString("uid").ifBlank { "viewer-meaning-${UUID.randomUUID()}" },
                            termId = target.id,
                            meaning = meaning,
                            sourceTitle = sourceTitle,
                            sourceAuthor = sourceAuthor,
                            sourcePage = sourcePage,
                            sourceUrl = meaningItem.optString("sourceUrl"),
                            usageTaziehUid = meaningItem.optString("taziehUid").takeIf { it.isNotBlank() },
                            usageVersionUid = meaningItem.optString("versionUid").takeIf { it.isNotBlank() },
                            usageRoleUid = meaningItem.optString("roleUid").takeIf { it.isNotBlank() },
                            usageSectionUid = meaningItem.optString("sectionUid").takeIf { it.isNotBlank() },
                            usageQuote = meaningItem.optString("quote"),
                            reviewStatus = GlossaryTermEntity.STATUS_VERIFIED,
                            createdAt = now,
                            updatedAt = now
                        )
                    )
                    meaningsAdded++
                }
            }
        }
        hashes.add(hash)
        prefs.edit()
            .putStringSet(KEY_HASHES, hashes)
            .putString(KEY_RELEASE, releaseId)
            .putLong(KEY_GENERATED_AT, generatedAt)
            .apply()
        return ImportResult(false, termsAdded, meaningsAdded, spellingsAdded, releaseId)
    }

    private fun sha256(bytes: ByteArray): String =
        MessageDigest.getInstance("SHA-256").digest(bytes).joinToString("") { "%02x".format(it) }

    companion object {
        private const val FORMAT = "tazieh-glossary"
        private const val SCHEMA_VERSION = 1
        private const val PREFS = "viewer_glossary_publications"
        private const val KEY_HASHES = "imported_sha256"
        private const val KEY_RELEASE = "latest_release_id"
        private const val KEY_GENERATED_AT = "latest_generated_at"
        private const val MAX_FILE_BYTES = 10 * 1024 * 1024
    }
}
