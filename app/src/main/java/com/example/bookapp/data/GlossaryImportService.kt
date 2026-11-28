package com.example.bookapp.data

import androidx.room.withTransaction
import org.json.JSONArray
import org.json.JSONObject
import java.util.UUID
import java.util.zip.ZipInputStream
import java.io.ByteArrayInputStream
import java.security.MessageDigest

/** Immutable preview row. Nothing is written to Room until apply() is explicitly called. */
data class GlossaryImportPreview(
    val packageUid: String,
    val terms: List<GlossaryImportTerm>,
    val errors: List<String>,
    val warnings: List<String>,
    val preapproved: Boolean = false
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
    /** Accepts the native LexText ZIP exports (dictionary v2.3 and target vocabulary v1)
     * as well as the app's own JSON contract v1. ZIP contents are read in-memory only.
     */
    fun previewFile(bytes: ByteArray, fileName: String): GlossaryImportPreview {
        val lower = fileName.lowercase()
        val zipSignature = bytes.size >= 4 && bytes[0] == 0x50.toByte() && bytes[1] == 0x4b.toByte()
        if (!lower.endsWith(".zip") && !zipSignature) {
            return preview(runCatching { bytes.toString(Charsets.UTF_8) }.getOrDefault(""))
        }
        val files = linkedMapOf<String, ByteArray>()
        return try {
            ZipInputStream(ByteArrayInputStream(bytes)).use { zip ->
                while (true) {
                    val entry = zip.nextEntry ?: break
                    if (!entry.isDirectory && !entry.name.startsWith("/") && !entry.name.contains("..")) {
                        val leaf = entry.name.substringAfterLast('/')
                        if (leaf in setOf("manifest.json", "entries.json", "sources.json", "annotations.json", "target_vocabulary.json")) {
                            files[leaf] = zip.readBytes()
                        }
                    }
                    zip.closeEntry()
                }
            }
            val manifestBytes = files["manifest.json"] ?: error("فایل manifest.json در بسته پیدا نشد.")
            val manifest = JSONObject(manifestBytes.toString(Charsets.UTF_8))
            val contract = manifest.optString("contract")
            val sourceRows = JSONArray(files["sources.json"]?.toString(Charsets.UTF_8) ?: "[]")
            val sourceById = mutableMapOf<String, JSONObject>()
            for (i in 0 until sourceRows.length()) {
                val row = sourceRows.optJSONObject(i) ?: continue
                sourceById[row.optString("id")] = row
            }
            val sourceTerms = when (contract) {
                "lextext-taaziye-dictionary-v2" -> JSONArray(files["entries.json"]?.toString(Charsets.UTF_8) ?: error("entries.json در بسته وجود ندارد."))
                "lextext-target-vocabulary-v1" -> JSONArray(files["target_vocabulary.json"]?.toString(Charsets.UTF_8) ?: error("target_vocabulary.json در بسته وجود ندارد."))
                else -> error("قرارداد بسته LexText پشتیبانی نمی‌شود: ${contract.ifBlank { "نامشخص" }}")
            }
            val approvedOnly = manifest.optBoolean("approved_only", false)
            val convertedTerms = JSONArray()
            for (i in 0 until sourceTerms.length()) {
                val row = sourceTerms.optJSONObject(i) ?: continue
                val word = row.optString("term").trim()
                if (word.isBlank()) continue
                val extraMetadata = mutableListOf<String>()
                row.optString("type").takeIf { it.isNotBlank() && it != "null" }?.let { extraMetadata += "نوع مدخل در LexText: $it" }
                listOf("frequency" to "فراوانی", "difficulty" to "دشواری", "confidence" to "اطمینان", "target_score" to "امتیاز واژه هدف", "target_status" to "وضعیت واژه هدف", "term_status" to "وضعیت واژه در LexText").forEach { (key, label) ->
                    val value = row.optString(key).takeIf { it.isNotBlank() && it != "null" && it != "0" }
                    if (value != null) extraMetadata += "$label: $value"
                }
                val meanings = JSONArray()
                val senses = row.optJSONArray("meanings")
                if (senses != null) {
                    for (j in 0 until senses.length()) {
                        val sense = senses.optJSONObject(j) ?: continue
                        if (approvedOnly && !sense.optBoolean("is_verified", false)) continue
                        val text = sequenceOf("meaning", "translation", "definition").map { sense.optString(it).trim() }.firstOrNull { it.isNotBlank() } ?: ""
                        if (text.isBlank()) continue
                        val source = sourceById[sense.optString("source_id")]
                        if (source != null) {
                            val details = listOfNotNull(
                                source.optString("publisher").takeIf { it.isNotBlank() && it != "null" },
                                source.optString("year").takeIf { it.isNotBlank() && it != "null" }?.let { "سال $it" },
                                source.optString("source_type").takeIf { it.isNotBlank() && it != "null" }?.let { "نوع $it" },
                                source.optString("note").takeIf { it.isNotBlank() && it != "null" },
                                source.optString("reliability").takeIf { it.isNotBlank() && it != "null" }?.let { "اعتبار $it" }
                            )
                            if (details.isNotEmpty()) extraMetadata += "جزئیات منبع «${source.optString("name") }»: ${details.joinToString("؛ ")}"
                        }
                        val occurrences = row.optJSONArray("occurrences")
                        val quote = if (occurrences != null && occurrences.length() > 0) {
                            val first = occurrences.optJSONObject(0)
                            first?.optString("sentence")?.trim().orEmpty()
                        } else ""
                        meanings.put(JSONObject().apply {
                            put("meaning", text)
                            put("sourceTitle", source?.optString("name").orEmpty())
                            put("sourceAuthor", source?.optString("author").orEmpty())
                            put("sourcePage", "")
                            put("sourceUrl", source?.optString("url").orEmpty())
                            // LexText occurrences are document/page contexts, not app section UIDs.
                            // Preserve the sentence as a quotation, without fabricating a section link.
                            put("usageQuote", quote)
                        })
                    }
                }
                if (meanings.length() == 0) {
                    val fallback = row.optString("meaning").trim()
                    if (fallback.isNotBlank()) meanings.put(JSONObject().put("meaning", fallback).put("usageQuote", ""))
                }
                if (meanings.length() == 0) continue
                val notes = buildString {
                    val existing = row.optString("notes").trim()
                    if (existing.isNotBlank()) append(existing)
                    extraMetadata.forEach { item -> if (isNotEmpty()) append("\n"); append(item) }
                    val reason = row.optString("reason").trim()
                    if (reason.isNotBlank()) { if (isNotEmpty()) append("\n"); append("دلیل انتخاب در LexText: ").append(reason) }
                    val occ = row.optJSONArray("occurrences")
                    if (occ != null && occ.length() > 0) {
                        val refs = (0 until minOf(occ.length(), 10)).mapNotNull { k ->
                            occ.optJSONObject(k)?.let { o ->
                                val page = o.optString("page_number").takeIf { it.isNotBlank() && it != "null" }
                                val sentence = o.optString("sentence").trim()
                                listOfNotNull(page?.let { "صفحه $it" }, sentence.takeIf { it.isNotBlank() }).joinToString(": ").takeIf { it.isNotBlank() }
                            }
                        }
                        if (refs.isNotEmpty()) { if (isNotEmpty()) append("\n"); append("بافت‌های متنی استخراج‌شده از LexText (بدون اتصال به بخش برنامه):\n"); append(refs.joinToString("\n")) }
                    }
                }
                convertedTerms.put(JSONObject().apply {
                    put("headword", word)
                    put("partOfSpeech", "")
                    put("pronunciation", "")
                    put("etymology", "")
                    put("notes", notes)
                    put("spellings", JSONArray())
                    put("meanings", meanings)
                })
            }
            require(convertedTerms.length() > 0) { "بسته LexText واژه و معنی قابل‌ورودی ندارد." }
            val digest = MessageDigest.getInstance("SHA-256").digest(bytes).joinToString("") { "%02x".format(it) }
            val normalized = JSONObject().apply {
                put("schemaVersion", 1)
                put("packageUid", "lextext-${digest.take(32)}")
                put("packageTitle", "ورودی از ${manifest.optString("application", "LexText")}")
                put("description", "قرارداد مبدأ: $contract | نسخه: ${manifest.optString("schema_version")}")
                put("terms", convertedTerms)
            }
            val result = preview(normalized.toString())
            if (result.isValid) result.copy(
                warnings = (result.warnings + listOf(
                    "این بسته مستقیماً از خروجی LexText تبدیل شد.",
                    "بافت‌های صفحه‌ای حفظ شده‌اند؛ چون خروجی مبدأ شناسه بخش تعزیه ندارد، به بخش مشخصی از برنامه متصل نشده‌اند.",
                    if (approvedOnly) "این بسته با approved_only=true صادر شده است؛ فقط واژه‌های تأییدشده و معنی‌های is_verified=true وارد می‌شوند." else "این بسته همه وضعیت‌های مبدأ را دارد؛ رکوردهای آن در برنامه اصلی در انتظار بررسی می‌مانند."
                )).distinct(),
                preapproved = approvedOnly
            ) else result
        } catch (e: Exception) {
            GlossaryImportPreview("", emptyList(), listOf("خواندن بسته LexText ناموفق بود: ${e.message ?: "ساختار نامعتبر"}"), emptyList())
        }
    }

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
                        // LexText exports can contain approved lexical meanings and bibliographic
                        // sources without a ta'zieh section mapping. Keep those records; never
                        // invent a section UID. The local app can attach an exact usage later.
                        if (sectionUid.isNotBlank() && !hasCompletePath && meaningObject.optString("usageRoleUid").isBlank() && meaningObject.optString("usageTaziehUid").isBlank()) {
                            warnings += "برای کاربرد «$headword» فقط شناسه بخش ارائه شده است؛ اعتبارسنجی در زمان ورود انجام می‌شود."
                        }
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

    suspend fun apply(preview: GlossaryImportPreview): GlossaryImportResult = applyInternal(preview, published = preview.preapproved)

    /** Used only for JSON packages shipped inside the APK by the project build. */
    suspend fun applyPublished(preview: GlossaryImportPreview): GlossaryImportResult = applyInternal(preview, published = true)

    private suspend fun applyInternal(preview: GlossaryImportPreview, published: Boolean): GlossaryImportResult {
        require(preview.isValid) { "ابتدا خطاهای پیش‌نمایش را برطرف کنید." }
        return db.withTransaction {
            val dao = db.glossaryDao()
            val packageAction = if (published) "PUBLISH_PACKAGE:${preview.packageUid}" else "IMPORT_PACKAGE:${preview.packageUid}"
            require(!dao.hasImportPackage(packageAction)) {
                "این شناسه بسته قبلاً وارد شده است؛ برای بسته جدید packageUid تازه تعیین کنید."
            }
            var addedTerms = 0; var reusedTerms = 0; var addedMeanings = 0; var skippedMeanings = 0
            var addedSpellings = 0; var skippedSpellings = 0
            val now = System.currentTimeMillis()
            // Validate every contextual reference before writing anything. Room transaction
            // guarantees rollback as an additional safeguard if a later insert fails.
            preview.terms.forEach { incoming ->
                incoming.meanings.forEach { meaning ->
                    if (hasUsageReference(meaning)) {
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
            }
            preview.terms.forEach { incoming ->
                val normalized = normalize(incoming.headword)
                val existing = dao.byNormalizedHeadword(normalized)
                val term = if (existing == null) {
                    val draft = GlossaryTermEntity(
                        uid = "term-${UUID.randomUUID()}", headword = incoming.headword, normalizedHeadword = normalized,
                        partOfSpeech = incoming.partOfSpeech, pronunciation = incoming.pronunciation,
                        etymology = incoming.etymology, notes = incoming.notes,
                        reviewStatus = if (published) GlossaryTermEntity.STATUS_VERIFIED else GlossaryTermEntity.STATUS_PENDING,
                        createdAt = now, updatedAt = now
                    )
                    val id = dao.insertTerm(draft); addedTerms++
                    draft.copy(id = id)
                } else {
                    reusedTerms++
                    val updated = existing.copy(
                        partOfSpeech = existing.partOfSpeech.ifBlank { incoming.partOfSpeech },
                        pronunciation = existing.pronunciation.ifBlank { incoming.pronunciation },
                        etymology = existing.etymology.ifBlank { incoming.etymology },
                        notes = listOf(existing.notes.trim(), incoming.notes.trim()).filter { it.isNotBlank() }.distinct().joinToString("\n"),
                        reviewStatus = if (published) GlossaryTermEntity.STATUS_VERIFIED else existing.reviewStatus,
                        updatedAt = now
                    )
                    dao.updateTerm(updated)
                    updated
                }
                incoming.spellings.forEach { spelling ->
                    val key = normalize(spelling)
                    if (key == normalized || dao.spellingByNormalized(key) != null) skippedSpellings++
                    else { dao.insertSpelling(GlossarySpellingEntity(termId = term.id, spelling = spelling, normalizedSpelling = key)); addedSpellings++ }
                }
                incoming.meanings.forEach { meaning ->
                    val resolvedSection = if (hasUsageReference(meaning)) resolveUsageSection(meaning) else null
                    val resolvedRole = resolvedSection?.let { db.roleDao().getById(it.roleId) }
                    val resolvedTazieh = resolvedRole?.let { db.taziehDao().getById(it.taziehId) }
                    val resolvedMeaning = meaning.copy(
                        usageSectionUid = resolvedSection?.uid.orEmpty(),
                        usageRoleUid = resolvedRole?.uid ?: meaning.usageRoleUid,
                        usageVersionUid = resolvedRole?.versionUid?.ifBlank { null } ?: meaning.usageVersionUid,
                        usageTaziehUid = resolvedTazieh?.uid ?: meaning.usageTaziehUid
                    )
                    val duplicate = dao.duplicateMeaning(term.id, meaning.meaning, meaning.sourceTitle, meaning.sourceAuthor, meaning.sourcePage, meaning.sourceUrl, resolvedSection?.uid.orEmpty())
                    if (duplicate != null) {
                        if (published && duplicate.reviewStatus != GlossaryTermEntity.STATUS_VERIFIED) {
                            dao.updateMeaning(duplicate.copy(reviewStatus = GlossaryTermEntity.STATUS_VERIFIED, updatedAt = now))
                        }
                        skippedMeanings++
                    } else {
                        dao.insertMeaning(GlossaryMeaningEntity(
                            uid = "meaning-${UUID.randomUUID()}", termId = term.id, meaning = meaning.meaning,
                            sourceTitle = meaning.sourceTitle, sourceAuthor = meaning.sourceAuthor, sourcePage = meaning.sourcePage,
                            sourceUrl = meaning.sourceUrl, usageTaziehUid = resolvedMeaning.usageTaziehUid,
                            usageVersionUid = resolvedMeaning.usageVersionUid, usageRoleUid = resolvedMeaning.usageRoleUid,
                            usageSectionUid = resolvedMeaning.usageSectionUid, usageQuote = meaning.usageQuote,
                            reviewStatus = if (published) GlossaryTermEntity.STATUS_VERIFIED else GlossaryTermEntity.STATUS_PENDING,
                            createdAt = now, updatedAt = now
                        ))
                        addedMeanings++
                    }
                }
                dao.insertHistory(GlossaryHistoryEntity(
                    termId = term.id, action = packageAction, beforeJson = "{}",
                    afterJson = JSONObject().put("headword", incoming.headword).put("meanings", incoming.meanings.size).toString(), changedAt = now
                ))
            }
            GlossaryImportResult(addedTerms, reusedTerms, addedMeanings, skippedMeanings, addedSpellings, skippedSpellings)
        }
    }


    private fun hasUsageReference(meaning: GlossaryImportMeaning): Boolean =
        meaning.usageSectionUid.isNotBlank() || (!meaning.usageFieldTitle.isBlank() && !meaning.usageTaziehTitle.isBlank() && !meaning.usageRoleTitle.isBlank() && !meaning.usageSectionTitle.isBlank())

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


/**
 * Loads additive, pre-approved glossary packages bundled by the repository build.
 * Put each new package JSON in app/src/main/assets/glossary/packages/.
 * Content assets are synchronized first so contextual section references can resolve.
 */
suspend fun syncLocalGlossaryFiles(context: android.content.Context, db: AppDatabase): Int {
    val names = context.assets.list("glossary/packages")
        ?.filter { it.endsWith(".json", ignoreCase = true) }
        ?.sorted() ?: emptyList()
    if (names.isEmpty()) return 0

    val service = GlossaryImportService(db)
    val pending = mutableListOf<GlossaryImportPreview>()
    val seenPackageUids = mutableSetOf<String>()
    for (name in names) {
        val text = context.assets.open("glossary/packages/$name")
            .bufferedReader(Charsets.UTF_8).use { it.readText() }
        val preview = service.preview(text)
        require(preview.isValid) {
            "بسته فرهنگ اصطلاحات «$name» معتبر نیست: ${preview.errors.take(4).joinToString("؛ ")}"
        }
        require(seenPackageUids.add(preview.packageUid)) {
            "شناسه بسته «${preview.packageUid}» در بیش از یک فایل تکرار شده است. برای هر فایل packageUid مستقل تعیین کنید."
        }
        if (!db.glossaryDao().hasImportPackage("PUBLISH_PACKAGE:${preview.packageUid}")) {
            pending += preview
        }
    }

    // Each package has its own Room transaction and stable packageUid. Existing packages
    // are skipped on later launches; newly added files are appended without deleting old data.
    pending.forEach { service.applyPublished(it) }
    return pending.size
}


/** Creates a portable additive package containing only manager-approved terms and meanings. */
suspend fun exportVerifiedGlossaryJson(db: AppDatabase, packageUid: String): String {
    require(packageUid.isNotBlank()) { "شناسه بسته خروجی خالی است." }
    val dao = db.glossaryDao()
    val fieldsById = db.fieldDao().getAll().associateBy { it.id }
    val termsArray = JSONArray()
    dao.termsByStatus(GlossaryTermEntity.STATUS_VERIFIED).forEach termLoop@{ term ->
        val meaningsArray = JSONArray()
        dao.meanings(term.id).filter { it.reviewStatus == GlossaryTermEntity.STATUS_VERIFIED }.forEach meaningLoop@{ meaning ->
            val section = meaning.usageSectionUid?.takeIf { it.isNotBlank() }?.let { db.sectionDao().getByUid(it) }
            val role = section?.let { db.roleDao().getById(it.roleId) }
            val tazieh = role?.let { db.taziehDao().getById(it.taziehId) }
            val field = tazieh?.let { fieldsById[it.fieldId] }
            meaningsArray.put(JSONObject().apply {
                put("meaning", meaning.meaning)
                put("sourceTitle", meaning.sourceTitle)
                put("sourceAuthor", meaning.sourceAuthor)
                put("sourcePage", meaning.sourcePage)
                put("sourceUrl", meaning.sourceUrl)
                if (tazieh != null) put("usageTaziehUid", tazieh.uid)
                if (role != null) put("usageVersionUid", role.versionUid)
                if (role != null) put("usageRoleUid", role.uid)
                if (section != null) put("usageSectionUid", section.uid)
                if (field != null) put("usageFieldTitle", field.title)
                if (tazieh != null) put("usageTaziehTitle", tazieh.title)
                if (role != null) put("usageRoleTitle", role.title)
                if (section != null) put("usageSectionTitle", section.title)
                put("usageQuote", meaning.usageQuote)
            })
        }
        if (meaningsArray.length() == 0) return@termLoop
        val spellingArray = JSONArray()
        dao.spellings(term.id).forEach { spellingArray.put(it.spelling) }
        termsArray.put(JSONObject().apply {
            put("headword", term.headword)
            put("partOfSpeech", term.partOfSpeech)
            put("pronunciation", term.pronunciation)
            put("etymology", term.etymology)
            put("notes", term.notes)
            put("spellings", spellingArray)
            put("meanings", meaningsArray)
        })
    }
    require(termsArray.length() > 0) { "هیچ واژه و معنی تأییدشده و متصل به بخش معتبر برای انتشار وجود ندارد." }
    return JSONObject().apply {
        put("schemaVersion", 1)
        put("packageUid", packageUid)
        put("packageTitle", "بسته منتشرشده فرهنگ اصطلاحات")
        put("description", "فقط واژه‌ها و معنی‌های تأییدشده؛ بسته افزایشی")
        put("terms", termsArray)
    }.toString(2)
}
