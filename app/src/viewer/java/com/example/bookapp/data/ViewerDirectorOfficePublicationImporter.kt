package com.example.bookapp.data

import android.content.Context
import android.net.Uri
import androidx.room.withTransaction
import org.json.JSONObject
import java.io.InputStream
import java.util.UUID
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** واردکردن بسته رسمی فقط در پایگاه داده رسمی Viewer؛ دیتابیس شخصی اصلاً باز نمی‌شود. */
object ViewerDirectorOfficePublicationImporter {
    private const val MAX_BYTES = 50L * 1024L * 1024L

    suspend fun import(context: Context, uri: Uri, officialDb: ViewerDirectorOfficeDatabase): String {
        require(DirectorOfficeAccessRules.canViewOfficial(ViewerAccessPolicy.getEffectivePermissions(context))) { "مجوز مشاهده و دریافت دفتر رسمی فعال نیست." }
        val input = context.contentResolver.openInputStream(uri) ?: error("فایل انتخاب‌شده باز نشد.")
        return import(context, input, officialDb)
    }

    suspend fun import(context: Context, input: InputStream, officialDb: ViewerDirectorOfficeDatabase): String {
        require(DirectorOfficeAccessRules.canViewOfficial(ViewerAccessPolicy.getEffectivePermissions(context))) { "مجوز مشاهده و دریافت دفتر رسمی فعال نیست." }
        val envelope = withContext(Dispatchers.IO) {
            val bytes = input.use { stream ->
                val out = java.io.ByteArrayOutputStream()
                val buffer = ByteArray(8192)
                var total = 0L
                while (true) {
                    val count = stream.read(buffer)
                    if (count < 0) break
                    total += count
                    require(total <= MAX_BYTES) { "حجم بسته بیش از ۵۰ مگابایت است." }
                    out.write(buffer, 0, count)
                }
                out.toByteArray()
            }
            JSONObject(bytes.toString(Charsets.UTF_8))
        }
        require(envelope.optString("schema") == DirectorOfficePublicationContract.SCHEMA && envelope.optInt("schemaVersion") == DirectorOfficePublicationContract.SCHEMA_VERSION) {
            "ساختار بسته دفتر پشتیبانی نمی‌شود."
        }
        val payload = envelope.getJSONObject("payload")
        require(payload.optString("schema") == DirectorOfficePublicationContract.SCHEMA && payload.optInt("schemaVersion") == DirectorOfficePublicationContract.SCHEMA_VERSION) {
            "شناسه ساختار محتوای بسته معتبر نیست."
        }
        require(ViewerAccessTransfer.verify(payload.toString(), envelope.optString("signature"))) {
            "امضای دیجیتال بسته معتبر نیست؛ فایل فقط باید از Admin اصلی دریافت شود."
        }
        val planJson = payload.getJSONObject("plan")
        val versionJson = payload.getJSONObject("version")
        val planUid = planJson.optString("uid").trim()
        require(planUid.isNotBlank()) { "شناسه دفتر در بسته خالی است." }
        require(versionJson.optInt("versionNumber") > 0) { "شماره نسخه منتشرشده معتبر نیست." }
        val turnsJson = payload.getJSONArray("turns")
        require(turnsJson.length() <= 20000) { "تعداد نوبت‌های بسته از حد مجاز بیشتر است." }

        val dao = officialDb.directorDao()
        val existingPlan = dao.planByUid(planUid)
        if (existingPlan != null) {
            val installedVersion = dao.versions(existingPlan.id).filter { it.status == DirectorPlanVersionEntity.STATUS_PUBLISHED }.maxOfOrNull { it.versionNumber } ?: 0
            require(versionJson.getInt("versionNumber") > installedVersion) {
                "نسخه دریافتی قدیمی یا تکراری است؛ نسخه نصب‌شده $installedVersion و نسخه فایل ${versionJson.getInt("versionNumber")} است."
            }
        }
        val writable = officialDb.openHelper.writableDatabase
        // قفل دیتابیس رسمی فقط در مسیر واردکردن بسته‌ای که امضایش تأیید شده باز می‌شود.
        ViewerDirectorOfficeDatabase.unlockOfficialForVerifiedImport(writable)
        try {
        officialDb.withTransaction {
            // فقط همان دفتر رسمی جایگزین می‌شود؛ director_personal.db جداست و لمس نمی‌شود.
            dao.replaceImportedOfficialPlan(planUid)
            val now = System.currentTimeMillis()
            val planId = dao.insertPlan(DirectorPlanEntity(
                uid = planUid,
                taziehUid = planJson.optString("taziehUid"),
                title = planJson.optString("title"),
                status = DirectorPlanEntity.STATUS_PUBLISHED,
                description = planJson.optString("description"),
                createdAt = planJson.optLong("createdAt", now),
                updatedAt = now
            ))
            val publishedAt = versionJson.optLong("publishedAt", now)
            val versionId = dao.insertVersion(DirectorPlanVersionEntity(
                planId = planId,
                versionNumber = versionJson.getInt("versionNumber"),
                title = versionJson.optString("title"),
                status = DirectorPlanVersionEntity.STATUS_PUBLISHED,
                changeNotes = versionJson.optString("changeNotes"),
                createdAt = publishedAt,
                publishedAt = publishedAt
            ))
            val turnIds = mutableListOf<Long>()
            for (i in 0 until turnsJson.length()) {
                val item = turnsJson.getJSONObject(i)
                val turnId = dao.insertTurn(DirectorTurnEntity(
                    versionId = versionId,
                    turnNumber = item.optInt("turnNumber"),
                    sequenceIndex = item.optInt("sequenceIndex", i),
                    turnType = item.optString("turnType", DirectorTurnEntity.TYPE_NORMAL),
                    title = item.optString("title"),
                    continuityGroupUid = item.optString("continuityGroupUid"),
                    isEnabled = item.optBoolean("isEnabled", true),
                    notes = item.optString("notes")
                ))
                turnIds += turnId
                val participants = item.optJSONArray("participants")
                if (participants != null) for (j in 0 until participants.length()) {
                    val person = participants.getJSONObject(j)
                    dao.insertParticipant(DirectorParticipantEntity(
                        turnId = turnId, roleUid = person.optString("roleUid"),
                        roleTitle = person.optString("roleTitle"), orderIndex = person.optInt("orderIndex"),
                        isLead = person.optBoolean("isLead")
                    ))
                }
                val segments = item.optJSONArray("segments")
                if (segments != null) for (j in 0 until segments.length()) {
                    val segment = segments.getJSONObject(j)
                    val section = if (segment.isNull("sectionUid")) null else segment.optString("sectionUid")
                    dao.insertTextSegment(DirectorTextSegmentEntity(
                        turnId = turnId, segmentIndex = segment.optInt("segmentIndex", j),
                        text = segment.optString("text"), sectionUid = section
                    ))
                }
            }
            val cues = payload.optJSONArray("stageCues")
            if (cues != null) for (i in 0 until cues.length()) {
                val cue = cues.getJSONObject(i)
                val index = cue.optInt("turnIndex", -1)
                dao.insertStageCue(DirectorStageCueEntity(
                    versionId = versionId,
                    turnId = turnIds.getOrNull(index),
                    cueType = cue.optString("cueType", DirectorStageCueEntity.CUE_OTHER),
                    title = cue.optString("title"), instruction = cue.optString("instruction"),
                    orderIndex = cue.optInt("orderIndex", i),
                    blocksNextTurn = cue.optBoolean("blocksNextTurn"),
                    continuationRoleUid = cue.optString("continuationRoleUid")
                ))
            }
        }
        } finally {
            ViewerDirectorOfficeDatabase.lockOfficialAfterImport(writable)
        }
        return "دفتر رسمی «${planJson.optString("title")}" +
            "»، نسخه ${versionJson.optInt("versionNumber")} با موفقیت و پس از اعتبارسنجی امضا وارد شد. دفتر شخصی بدون تغییر باقی ماند."
    }

    data class SelectorEntry(
        val key: String,
        val roleUid: String,
        val roleTitle: String,
        val sectionUid: String,
        val sectionTitle: String,
        val text: String
    )

    /** ساخت دفتر شخصی دستی بدون وابستگی به دریافت/مشاهده دفتر رسمی. */
    suspend fun createBlankPersonal(
        context: Context,
        personalDb: ViewerDirectorOfficeDatabase,
        taziehUid: String? = null,
        title: String = "دفتر شخصی جدید"
    ): Long {
        val permissions = ViewerAccessPolicy.getEffectivePermissions(context)
        require(DirectorOfficeAccessRules.canCreatePersonal(permissions, "MANUAL")) {
            "مدیر ساخت دفتر شخصی با روش دستی را برای این Viewer مجاز نکرده است."
        }
        val now = System.currentTimeMillis()
        val dao = personalDb.directorDao()
        return personalDb.withTransaction {
            val planId = dao.insertPlan(DirectorPlanEntity(
                uid = "personal-${UUID.randomUUID()}",
                taziehUid = taziehUid.orEmpty(),
                title = title.trim().ifBlank { "دفتر شخصی جدید" },
                status = DirectorPlanEntity.STATUS_DRAFT,
                description = "دفتر مستقل این Viewer؛ روش ساخت: MANUAL؛ انتشار رسمی و خروجی‌گیری ندارد.",
                createdAt = now,
                updatedAt = now
            ))
            dao.insertVersion(DirectorPlanVersionEntity(
                planId = planId,
                versionNumber = 1,
                title = "نسخه شخصی اول",
                status = DirectorPlanVersionEntity.STATUS_DRAFT,
                changeNotes = "دفتر شخصی؛ روش ساخت: MANUAL؛ ایجاد مستقل توسط کاربر",
                createdAt = now
            ))
            planId
        }
    }

    /** ساخت دفتر شخصی با روش واقعی انتخاب‌شده از محتوای محلی همان مجلس. */
    suspend fun createPersonalFromOfficial(
        context: Context,
        contentDb: AppDatabase,
        officialDb: ViewerDirectorOfficeDatabase,
        personalDb: ViewerDirectorOfficeDatabase,
        officialPlan: DirectorPlanEntity,
        method: String
    ): Long {
        val permissions = ViewerAccessPolicy.getEffectivePermissions(context)
        require(DirectorOfficeAccessRules.canViewOfficial(permissions)) { "برای ساخت دفتر شخصی، مشاهده محتوای رسمی باید مجاز باشد." }
        require(DirectorOfficeAccessRules.canCreatePersonal(permissions, method)) { "مدیر ساخت دفتر شخصی با این روش را برای Viewer مجاز نکرده است." }
        require(officialPlan.status == DirectorPlanEntity.STATUS_PUBLISHED) { "فقط از دفتر رسمی منتشرشده می‌توان دفتر شخصی ساخت." }

        val sourceDao = officialDb.directorDao()
        val sourceVersion = sourceDao.versions(officialPlan.id).firstOrNull { it.status == DirectorPlanVersionEntity.STATUS_PUBLISHED }
            ?: error("نسخه رسمی منتشرشده پیدا نشد.")
        val tazieh = if (method == "MANUAL") null else {
            contentDb.taziehDao().getByUid(officialPlan.taziehUid)
                ?: error("محتوای مجلس مرتبط در کتابخانه آفلاین Viewer پیدا نشد.")
        }
        val currentContentVersion = tazieh?.let { local ->
            contentDb.archiveDao().currentVersion(local.uid) ?: contentDb.archiveDao().versionsForTazieh(local.uid).firstOrNull()
        }
        val roles = when {
            tazieh == null -> emptyList()
            currentContentVersion != null -> contentDb.roleDao().getByVersionUid(currentContentVersion.uid)
            else -> contentDb.roleDao().getByTazieh(tazieh.id).filter { it.versionUid.isBlank() }
                .sortedWith(compareBy<com.example.bookapp.data.RoleEntity> { it.orderIndex }.thenBy { it.id })
        }
        val sections = if (roles.isEmpty()) emptyList() else contentDb.sectionDao().getByRoles(roles.map { it.id })
            .filter { it.content.isNotBlank() }
            .sortedWith(compareBy<com.example.bookapp.data.SectionEntity> { section -> roles.firstOrNull { it.id == section.roleId }?.orderIndex ?: Int.MAX_VALUE }
                .thenBy { it.orderIndex }.thenBy { it.id })
        if (method == "SELECTOR") require(sections.isNotEmpty()) { "برای روش انتخاب‌گر، متن محلی نقش‌های مجلس در Viewer پیدا نشد." }
        val now = System.currentTimeMillis()
        val targetDao = personalDb.directorDao()
        return personalDb.withTransaction {
            val newPlanId = targetDao.insertPlan(DirectorPlanEntity(
                uid = "personal-${UUID.randomUUID()}", taziehUid = officialPlan.taziehUid,
                title = "دفتر شخصی — ${officialPlan.title}", status = DirectorPlanEntity.STATUS_DRAFT,
                description = "دفتر مستقل این Viewer؛ روش ساخت: $method؛ انتشار رسمی و خروجی‌گیری ندارد.",
                createdAt = now, updatedAt = now
            ))
            val newVersionId = targetDao.insertVersion(DirectorPlanVersionEntity(
                planId = newPlanId, versionNumber = 1, title = "نسخه شخصی اول",
                status = DirectorPlanVersionEntity.STATUS_DRAFT,
                changeNotes = "دفتر شخصی؛ روش ساخت: $method؛ منشأ: ${officialPlan.uid}",
                createdAt = now
            ))
            val newTurnsByNumber = mutableMapOf<Int, Long>()
            val newTurnsByRole = mutableMapOf<String, Long>()

            when (method) {
                "ROLE_NUMBERED" -> {
                    data class NumberedEntry(val number: Int, val role: com.example.bookapp.data.RoleEntity, val section: com.example.bookapp.data.SectionEntity, val text: String)
                    val entries = sections.flatMap { section ->
                        val role = roles.firstOrNull { it.id == section.roleId } ?: return@flatMap emptyList()
                        DirectorOfficeWorkspaceUseCases.parseNumberedDialogues(section.content).map { (number, text) -> NumberedEntry(number, role, section, text) }
                    }
                    require(entries.isNotEmpty()) { "در متن محلی نقش‌های این مجلس شماره دیالوگ پیدا نشد؛ روش شماره‌محور قابل اجرا نیست." }
                    var sequence = 0
                    entries.groupBy { it.number }.toSortedMap().forEach { (number, numberEntries) ->
                        val distinctRoles = numberEntries.map { it.role }.distinctBy { it.uid }
                        val turnId = targetDao.insertTurn(DirectorTurnEntity(
                            versionId = newVersionId, turnNumber = number, sequenceIndex = sequence++,
                            turnType = if (distinctRoles.size > 1) DirectorTurnEntity.TYPE_COLLECTIVE else DirectorTurnEntity.TYPE_NORMAL,
                            title = if (distinctRoles.size > 1) distinctRoles.joinToString("، ") { it.title } else distinctRoles.first().title
                        ))
                        newTurnsByNumber[number] = turnId
                        distinctRoles.forEachIndexed { index, role ->
                            targetDao.insertParticipant(DirectorParticipantEntity(turnId = turnId, roleUid = role.uid, roleTitle = role.title, orderIndex = index, isLead = index == 0))
                        }
                        numberEntries.forEachIndexed { index, entry ->
                            targetDao.insertTextSegment(DirectorTextSegmentEntity(turnId = turnId, segmentIndex = index, text = entry.text, sectionUid = entry.section.uid))
                        }
                    }
                }
                "FULL_TEXT" -> {
                    var sequence = 0
                    roles.forEach { role ->
                        val roleSections = sections.filter { it.roleId == role.id }
                        if (roleSections.isNotEmpty()) {
                            val turnId = targetDao.insertTurn(DirectorTurnEntity(
                                versionId = newVersionId, turnNumber = sequence + 1, sequenceIndex = sequence++, title = role.title
                            ))
                            newTurnsByRole[role.uid] = turnId
                            targetDao.insertParticipant(DirectorParticipantEntity(turnId = turnId, roleUid = role.uid, roleTitle = role.title, isLead = true))
                            roleSections.forEachIndexed { index, section ->
                                targetDao.insertTextSegment(DirectorTextSegmentEntity(turnId = turnId, segmentIndex = index, text = section.content, sectionUid = section.uid))
                            }
                        }
                    }
                    require(newTurnsByRole.isNotEmpty()) { "متن کاملی برای نقش‌های این مجلس در کتابخانه Viewer پیدا نشد." }
                }
                "MANUAL", "SELECTOR" -> {
                    // این دو روش عمداً با دفتر خالی شروع می‌شوند؛ نوبت‌ها بعداً در Viewer
                    // به‌صورت دستی یا از فهرست انتخاب‌گر افزوده می‌شوند.
                }
            }

            // راهنماهای صحنه رسمی به دفتر شخصی منتقل می‌شوند، اما به شناسه‌های تازه
            // متصل می‌گردند؛ هیچ رکوردی در پایگاه داده رسمی تغییر نمی‌کند.
            val sourceTurns = sourceDao.turns(sourceVersion.id)
            val sourceTurnById = sourceTurns.associateBy { it.id }
            sourceDao.stageCues(sourceVersion.id).forEach { cue ->
                val targetTurnId = when (method) {
                    "ROLE_NUMBERED" -> cue.turnId?.let { sourceTurnById[it]?.turnNumber }?.let { newTurnsByNumber[it] }
                    "FULL_TEXT" -> cue.turnId?.let { sourceTurnById[it]?.title }?.let { title -> roles.firstOrNull { it.title == title }?.uid }?.let { newTurnsByRole[it] }
                    else -> null
                }
                targetDao.insertStageCue(cue.copy(id = 0, versionId = newVersionId, turnId = targetTurnId))
            }
            newPlanId
        }
    }

    suspend fun selectorEntries(contentDb: AppDatabase, taziehUid: String): List<SelectorEntry> = withContext(Dispatchers.IO) {
        val tazieh = contentDb.taziehDao().getByUid(taziehUid) ?: return@withContext emptyList()
        val version = contentDb.archiveDao().currentVersion(tazieh.uid)
            ?: contentDb.archiveDao().versionsForTazieh(tazieh.uid).firstOrNull()
        val roles = if (version != null) contentDb.roleDao().getByVersionUid(version.uid)
        else contentDb.roleDao().getByTazieh(tazieh.id).filter { it.versionUid.isBlank() }
            .sortedWith(compareBy<com.example.bookapp.data.RoleEntity> { it.orderIndex }.thenBy { it.id })
        if (roles.isEmpty()) return@withContext emptyList()
        val sections = contentDb.sectionDao().getByRoles(roles.map { it.id })
            .filter { it.content.isNotBlank() }
            .sortedWith(compareBy<com.example.bookapp.data.SectionEntity> { section -> roles.firstOrNull { it.id == section.roleId }?.orderIndex ?: Int.MAX_VALUE }
                .thenBy { it.orderIndex }.thenBy { it.id })
        sections.flatMap { section ->
            val role = roles.firstOrNull { it.id == section.roleId } ?: return@flatMap emptyList()
            DirectorOfficeWorkspaceUseCases.splitDialogues(section.content)
                .mapIndexed { index, text -> SelectorEntry("${section.uid}:$index", role.uid, role.title, section.uid, section.title, text) }
        }
    }



}
