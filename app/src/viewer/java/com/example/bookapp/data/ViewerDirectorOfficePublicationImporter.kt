package com.example.bookapp.data

import android.content.Context
import android.net.Uri
import android.util.Base64
import androidx.room.withTransaction
import org.json.JSONObject
import java.security.KeyFactory
import java.security.MessageDigest
import java.security.Signature
import java.security.spec.X509EncodedKeySpec

/** فقط Flavor Viewer این importer را دارد؛ فایل‌های فاقد امضای Admin پذیرفته نمی‌شوند. */
object ViewerDirectorOfficePublicationImporter {
    private const val SCHEMA = "tazieh-director-office"
    private const val SCHEMA_VERSION = 1
    private const val MAX_PACKAGE_BYTES = 50 * 1024 * 1024
    private const val MAX_VERSIONS = 100
    private const val MAX_TURNS = 100_000
    private const val MAX_TEXT_CHARS = 20_000_000

    private fun verify(payloadText: String, signatureText: String): Boolean {
        val encoded = com.example.bookapp.BuildConfig.POLICY_PUBLIC_KEY
        if (encoded.isBlank()) return false
        return runCatching {
            val keyBytes = Base64.decode(encoded, Base64.NO_WRAP)
            val publicKey = KeyFactory.getInstance("RSA").generatePublic(X509EncodedKeySpec(keyBytes))
            val verifier = Signature.getInstance("SHA256withRSA")
            verifier.initVerify(publicKey)
            verifier.update(payloadText.toByteArray(Charsets.UTF_8))
            verifier.verify(Base64.decode(signatureText, Base64.DEFAULT))
        }.getOrDefault(false)
    }

    suspend fun import(context: Context, uri: Uri): Result<String> = runCatching {
        check(ViewerAccessPolicy.hasPermission(context, "director.official.view")) {
            "دسترسی دریافت و مشاهده دفتر رسمی توسط مدیر فعال نشده است."
        }
        val input = context.contentResolver.openInputStream(uri) ?: error("فایل انتخاب‌شده قابل خواندن نیست.")
        val bytes = input.use { stream ->
            val out = java.io.ByteArrayOutputStream()
            val buffer = ByteArray(8192)
            var total = 0
            while (true) {
                val count = stream.read(buffer)
                if (count < 0) break
                total += count
                require(total <= MAX_PACKAGE_BYTES) { "حجم بسته رسمی بیش از حد مجاز است." }
                out.write(buffer, 0, count)
            }
            out.toByteArray()
        }
        val envelope = JSONObject(bytes.toString(Charsets.UTF_8))
        require(envelope.optString("schema") == SCHEMA && envelope.optInt("schemaVersion", -1) == SCHEMA_VERSION) {
            "ساختار فایل دفتر رسمی پشتیبانی نمی‌شود."
        }
        val payloadText = envelope.getString("payload")
        require(verify(payloadText, envelope.optString("signature"))) {
            "امضای بسته معتبر نیست؛ فقط فایل صادرشده از نسخه معتبر Admin پذیرفته می‌شود."
        }
        val payload = JSONObject(payloadText)
        require(payload.optString("schema") == SCHEMA && payload.optInt("schemaVersion", -1) == SCHEMA_VERSION) {
            "نسخه اطلاعات دفتر رسمی معتبر نیست."
        }
        val planJson = payload.getJSONObject("plan")
        val planUid = planJson.getString("uid").trim()
        require(planUid.isNotBlank() && planUid.length <= 200) { "شناسه دفتر معتبر نیست." }
        val updatedAt = planJson.getLong("updatedAt")
        val versionsJson = payload.getJSONArray("versions")
        require(versionsJson.length() in 1..MAX_VERSIONS) { "تعداد نسخه‌های بسته معتبر نیست." }

        val planDigest = MessageDigest.getInstance("SHA-256").digest(payloadText.toByteArray(Charsets.UTF_8))
            .joinToString("") { "%02x".format(it) }
        val prefs = context.getSharedPreferences("director_official_imports", Context.MODE_PRIVATE)
        val previousDigest = prefs.getString("digest_$planUid", null)
        if (previousDigest == planDigest) return@runCatching "این نسخه رسمی قبلاً دریافت شده است؛ اطلاعات تغییری نکرده است."

        val db = OfficialDirectorOfficeDatabase.get(context)
        val dao = db.importDao()
        db.withTransaction {
            val existing = dao.existingPlan(planUid)
            if (existing != null) {
                require(updatedAt >= existing.updatedAt) { "این فایل از نسخه رسمی موجود قدیمی‌تر است و برای جلوگیری از بازگشت نسخه پذیرفته نشد." }
                dao.deletePlan(planUid) // حذف آبشاری فقط در دیتابیس رسمی؛ دیتابیس شخصی مستقل می‌ماند.
            }
            val planId = dao.insertPlan(
                DirectorPlanEntity(
                    uid = planUid,
                    taziehUid = planJson.optString("taziehUid"),
                    title = planJson.getString("title"),
                    status = DirectorPlanEntity.STATUS_PUBLISHED,
                    description = planJson.optString("description"),
                    createdAt = planJson.getLong("createdAt"),
                    updatedAt = updatedAt,
                    isDeleted = false,
                    deletedAt = null
                )
            )
            var totalTurns = 0
            var totalTextChars = 0L
            for (vi in 0 until versionsJson.length()) {
                val versionJson = versionsJson.getJSONObject(vi)
                val versionId = dao.insertVersion(
                    DirectorPlanVersionEntity(
                        planId = planId,
                        versionNumber = versionJson.getInt("versionNumber"),
                        title = versionJson.optString("title"),
                        status = DirectorPlanVersionEntity.STATUS_PUBLISHED,
                        changeNotes = versionJson.optString("changeNotes"),
                        createdAt = versionJson.getLong("createdAt"),
                        publishedAt = if (versionJson.isNull("publishedAt")) null else versionJson.optLong("publishedAt")
                    )
                )
                val turnJsonArray = versionJson.getJSONArray("turns")
                require(totalTurns + turnJsonArray.length() <= MAX_TURNS) { "تعداد نوبت‌های بسته بیش از حد مجاز است." }
                val turnIdsBySequence = mutableMapOf<Int, Long>()
                for (ti in 0 until turnJsonArray.length()) {
                    val turnJson = turnJsonArray.getJSONObject(ti)
                    val sequence = turnJson.getInt("sequenceIndex")
                    require(sequence >= 0 && sequence !in turnIdsBySequence) { "ترتیب نوبت‌ها در بسته تکراری یا نامعتبر است." }
                    val turnId = dao.insertTurn(
                        DirectorTurnEntity(
                            versionId = versionId,
                            turnNumber = turnJson.getInt("turnNumber"),
                            sequenceIndex = sequence,
                            turnType = turnJson.optString("turnType", DirectorTurnEntity.TYPE_NORMAL),
                            title = turnJson.optString("title"),
                            continuityGroupUid = turnJson.optString("continuityGroupUid"),
                            isEnabled = turnJson.optBoolean("isEnabled", true),
                            notes = turnJson.optString("notes")
                        )
                    )
                    turnIdsBySequence[sequence] = turnId
                    val participants = turnJson.getJSONArray("participants")
                    for (pi in 0 until participants.length()) {
                        val item = participants.getJSONObject(pi)
                        dao.insertParticipant(DirectorParticipantEntity(
                            turnId = turnId,
                            roleUid = item.getString("roleUid"),
                            roleTitle = item.getString("roleTitle"),
                            orderIndex = item.optInt("orderIndex"),
                            isLead = item.optBoolean("isLead")
                        ))
                    }
                    val segments = turnJson.getJSONArray("segments")
                    for (si in 0 until segments.length()) {
                        val item = segments.getJSONObject(si)
                        val text = item.optString("text")
                        totalTextChars += text.length
                        require(totalTextChars <= MAX_TEXT_CHARS) { "حجم متن بسته رسمی بیش از حد مجاز است." }
                        dao.insertTextSegment(DirectorTextSegmentEntity(
                            turnId = turnId,
                            segmentIndex = item.getInt("segmentIndex"),
                            text = text,
                            sectionUid = if (item.isNull("sectionUid")) null else item.optString("sectionUid"),
                            sourceFileId = null,
                            sourcePageStart = if (item.isNull("sourcePageStart")) null else item.optInt("sourcePageStart"),
                            sourcePageEnd = if (item.isNull("sourcePageEnd")) null else item.optInt("sourcePageEnd")
                        ))
                    }
                    totalTurns++
                }
                val cues = versionJson.getJSONArray("cues")
                for (ci in 0 until cues.length()) {
                    val item = cues.getJSONObject(ci)
                    val turnSequence = if (item.isNull("turnSequenceIndex")) null else item.optInt("turnSequenceIndex")
                    val turnId = turnSequence?.let { turnIdsBySequence[it] ?: error("راهنمای صحنه به نوبت ناموجود متصل شده است.") }
                    dao.insertStageCue(DirectorStageCueEntity(
                        versionId = versionId,
                        turnId = turnId,
                        cueType = item.optString("cueType", DirectorStageCueEntity.CUE_OTHER),
                        title = item.optString("title"),
                        instruction = item.getString("instruction"),
                        orderIndex = item.optInt("orderIndex"),
                        blocksNextTurn = item.optBoolean("blocksNextTurn"),
                        continuationRoleUid = item.optString("continuationRoleUid")
                    ))
                }
            }
        }
        check(prefs.edit().putString("digest_$planUid", planDigest).commit()) { "ثبت سابقه دریافت بسته انجام نشد." }
        "دفتر رسمی «${planJson.optString("title") }» با موفقیت و با تأیید امضای دیجیتال دریافت شد. تعداد نسخه‌های دریافت‌شده: ${versionsJson.length()}"
    }
}
