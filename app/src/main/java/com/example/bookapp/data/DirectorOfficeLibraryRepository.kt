package com.example.bookapp.data

/** نتیجه واردکردن محتوای موجود مجلس به نسخه دفتر معین‌البکاء. */
data class DirectorLibraryImportResult(
    val roleCount: Int,
    val sectionCount: Int,
    val turnCount: Int,
    val usedDialogueOrder: Boolean
)

/**
 * مرز دسترسی دفتر معین‌البکاء به کتابخانه اصلی.
 * صفحه مشترک فقط این قرارداد را می‌شناسد و مستقیماً DAOهای کتابخانه را فراخوانی نمی‌کند.
 */
interface DirectorOfficeLibraryRepository {
    suspend fun taziehById(id: Long): TaziehEntity?
    suspend fun taziehByUid(uid: String): TaziehEntity?
    suspend fun allTaziehs(): List<TaziehEntity>
    suspend fun allFields(): List<FieldEntity>
    suspend fun fieldTitle(fieldId: Long): String?
    suspend fun rolesFor(tazieh: TaziehEntity): List<RoleEntity>
    suspend fun sectionsForRoles(roleIds: List<Long>): List<SectionEntity>
    suspend fun sectionsByUids(uids: List<String>): List<SectionEntity>
    suspend fun resolveRole(taziehUid: String, roleTitle: String): RoleEntity?
    suspend fun importExistingText(
        dao: DirectorOfficeMainPageDao,
        transactionRunner: DirectorOfficeTransactionRunner,
        versionId: Long,
        taziehId: Long,
        method: String
    ): DirectorLibraryImportResult
}

/** پیاده‌سازی Room؛ در Admin و Viewer از همان قرارداد و کتابخانه محتوای همان Flavor استفاده می‌شود. */
class RoomDirectorOfficeLibraryRepository(
    private val db: AppDatabase
) : DirectorOfficeLibraryRepository {
    override suspend fun taziehById(id: Long) = db.taziehDao().getById(id)
    override suspend fun taziehByUid(uid: String) = db.taziehDao().getByUid(uid)
    override suspend fun allTaziehs() = db.taziehDao().getAll()
    override suspend fun allFields() = db.fieldDao().getAll()
    override suspend fun fieldTitle(fieldId: Long) = db.fieldDao().getAll().firstOrNull { it.id == fieldId }?.title
    override suspend fun sectionsForRoles(roleIds: List<Long>) =
        if (roleIds.isEmpty()) emptyList() else db.sectionDao().getByRoles(roleIds)
    override suspend fun sectionsByUids(uids: List<String>) =
        if (uids.isEmpty()) emptyList() else db.sectionDao().getByUids(uids)

    override suspend fun rolesFor(tazieh: TaziehEntity): List<RoleEntity> {
        val current = db.archiveDao().currentVersion(tazieh.uid)
            ?: db.archiveDao().versionsForTazieh(tazieh.uid).firstOrNull()
        return if (current != null) db.roleDao().getByVersionUid(current.uid)
        else db.roleDao().getByTazieh(tazieh.id).filter { it.versionUid.isBlank() }
            .sortedWith(compareBy<RoleEntity> { it.orderIndex }.thenBy { it.id })
    }

    override suspend fun resolveRole(taziehUid: String, roleTitle: String): RoleEntity? {
        val tazieh = taziehByUid(taziehUid) ?: return null
        return rolesFor(tazieh).firstOrNull {
            ContentUid.normalize(it.title).equals(ContentUid.normalize(roleTitle), ignoreCase = true)
        }
    }

    override suspend fun importExistingText(
        dao: DirectorOfficeMainPageDao,
        transactionRunner: DirectorOfficeTransactionRunner,
        versionId: Long,
        taziehId: Long,
        method: String
    ): DirectorLibraryImportResult {
        val tazieh = taziehById(taziehId)
            ?: return DirectorLibraryImportResult(0, 0, 0, false)
        val archiveVersion = db.archiveDao().currentVersion(tazieh.uid)
            ?: db.archiveDao().versionsForTazieh(tazieh.uid).firstOrNull()
        val roles = (archiveVersion?.let { db.roleDao().getByVersionUid(it.uid) }
            ?: db.roleDao().getByTazieh(taziehId).filter { it.versionUid.isBlank() })
            .sortedWith(compareBy<RoleEntity> { it.orderIndex }.thenBy { it.id })
        if (roles.isEmpty()) return DirectorLibraryImportResult(0, 0, 0, false)

        val roleById = roles.associateBy { it.id }
        val sections = sectionsForRoles(roles.map { it.id })
            .filter { it.content.isNotBlank() }
            .sortedWith(compareBy<SectionEntity> { roleById[it.roleId]?.orderIndex ?: Int.MAX_VALUE }
                .thenBy { it.orderIndex }.thenBy { it.id })
        if (sections.isEmpty()) return DirectorLibraryImportResult(roles.size, 0, 0, false)

        if (method == DirectorOfficeSharedContract.FULL_TEXT) {
            val sectionsByRole = sections.groupBy { it.roleId }
            transactionRunner.run {
                val existingTurns = dao.turns(versionId)
                val ids = existingTurns.map { it.id }
                val oldSegments = if (ids.isEmpty()) emptyList() else dao.textSegmentsForTurns(ids)
                if (oldSegments.any { it.text.isNotBlank() }) return@run
                val oldParticipants = if (ids.isEmpty()) emptyList() else dao.participantsForTurns(ids)
                oldParticipants.forEach { dao.deleteParticipant(it.id) }
                oldSegments.forEach { dao.deleteTextSegment(it.id) }
                existingTurns.forEach { dao.deleteTurn(it.id) }
                roles.filter { sectionsByRole[it.id].orEmpty().isNotEmpty() }.forEachIndexed { roleIndex, role ->
                    val roleSections = sectionsByRole[role.id].orEmpty()
                    val turnId = dao.insertTurn(DirectorTurnEntity(
                        versionId = versionId, turnNumber = roleIndex + 1,
                        sequenceIndex = roleIndex, title = role.title
                    ))
                    dao.insertParticipant(DirectorParticipantEntity(
                        turnId = turnId, roleUid = role.uid, roleTitle = role.title,
                        orderIndex = 0, isLead = true
                    ))
                    roleSections.forEachIndexed { segmentIndex, section ->
                        dao.insertTextSegment(DirectorTextSegmentEntity(
                            turnId = turnId, segmentIndex = segmentIndex, text = section.content,
                            sectionUid = section.uid, sourceTaziehUid = tazieh.uid,
                            sourceRoleUid = role.uid, sourceSectionTitle = section.title,
                            sourceSectionUids = section.uid, sourceMethod = DirectorOfficeSharedContract.FULL_TEXT,
                            sourceContentHash = ContentUid.sha256(section.content)
                        ))
                    }
                }
            }
            val roleCount = sections.map { it.roleId }.distinct().size
            return DirectorLibraryImportResult(roles.size, sections.size, roleCount, false)
        }

        data class Entry(
            val section: SectionEntity,
            val role: RoleEntity,
            val turnNumber: Int,
            val order: Int,
            val text: String
        )

        val sectionById = sections.associateBy { it.id }
        val numberedBySection = sections.associate { section ->
            section.id to DirectorOfficeWorkspaceUseCases.parseNumberedDialogues(section.content)
        }
        // روش شماره‌محور مستقیماً شماره و متن هر دیالوگ را از منبع نقش استخراج می‌کند؛
        // برای این روش نیازی به پیمایش جدول‌های Dialogue و پرس‌وجوهای تکراری آن‌ها نیست.
        val dialogues = if (method == DirectorOfficeSharedContract.ROLE_NUMBERED) emptyList()
            else archiveVersion?.let { db.dialogueDao().getByVersionUid(it.uid) }
                ?: db.dialogueDao().getByTazieh(taziehId).filter { it.versionUid.isBlank() }

        var bestEntries = emptyList<Entry>()
        var bestCoverage = -1
        for (dialogue in dialogues) {
            val dialogueTurns = db.dialogueTurnDao().getByDialogue(dialogue.id)
            if (dialogueTurns.isEmpty()) continue
            val metadataByTurnId = db.dialogueTurnMetadataDao().getByDialogue(dialogue.id).associateBy { it.turnId }
            val turnsPerSection = dialogueTurns.groupingBy { it.sectionId }.eachCount()
            val candidate = mutableListOf<Entry>()
            for (sourceTurn in dialogueTurns) {
                val section = sectionById[sourceTurn.sectionId] ?: continue
                val role = roleById[section.roleId] ?: continue
                val metadata = metadataByTurnId[sourceTurn.id]
                val verified = metadata != null && metadata.sourceSequenceIndex != null &&
                    (metadata.sourceSequenceStatus == DialogueTurnMetadataEntity.SOURCE_DOCUMENTED ||
                        metadata.sourceSequenceStatus == DialogueTurnMetadataEntity.SOURCE_DIRECTOR_CONFIRMED)
                val number = if (verified) (metadata!!.sourceSequenceIndex!! + 1) else (sourceTurn.orderIndex + 1)
                val numberedParts = numberedBySection[section.id].orEmpty()
                val matchingParts = numberedParts.filter { it.first == number }
                val exactText = when {
                    matchingParts.size == 1 -> matchingParts.single().second
                    matchingParts.size > 1 -> ""
                    numberedParts.isNotEmpty() -> "" // متن شماره‌دار است اما شماره این نوبت در آن وجود ندارد.
                    turnsPerSection[section.id] == 1 -> section.content.trim()
                    else -> "" // چند نوبت به یک بخش متصل‌اند؛ متن کامل بخش نباید به‌جای یک دیالوگ وارد شود.
                }
                if (exactText.isNotBlank()) {
                    candidate += Entry(section, role, number, sourceTurn.orderIndex, exactText)
                }
            }
            val coverage = candidate.map { it.section.id }.distinct().size
            if (coverage > bestCoverage) {
                bestCoverage = coverage
                bestEntries = candidate
            }
        }

        // حتی اگر جدول Dialogue قدیمی یا ناقص باشد، متن‌های شماره‌دار مستقیماً از خود متن
        // هر نقش استخراج می‌شوند. هیچ‌گاه کل متن نقش به‌عنوان یک دیالوگ جایگزین نمی‌شود.
        val parsedNumberedEntries = sections.flatMap { section ->
            val role = roleById[section.roleId] ?: return@flatMap emptyList()
            numberedBySection[section.id].orEmpty().map { (number, text) ->
                Entry(section, role, number, section.orderIndex, text)
            }
        }
        // برای بخش‌های دارای شماره، فقط متن دقیق همان شماره وارد می‌شود. متن کامل بخش
        // فقط زمانی مجاز است که در ساختار قدیمی، یک بخش صریحاً به یک نوبت واحد متصل باشد.
        val legacySingleDialogueEntries = if (method == DirectorOfficeSharedContract.ROLE_NUMBERED) {
            emptyList()
        } else {
            bestEntries.filter { numberedBySection[it.section.id].isNullOrEmpty() }
        }
        val entries = (parsedNumberedEntries + legacySingleDialogueEntries)
            .sortedWith(compareBy<Entry> { it.turnNumber }.thenBy { it.order }
                .thenBy { roleById[it.section.roleId]?.orderIndex ?: Int.MAX_VALUE })
        val duplicateRoleNumbers = entries.groupBy { it.role.uid to it.turnNumber }
            .filterValues { it.size > 1 }
        require(duplicateRoleNumbers.isEmpty()) {
            val details = duplicateRoleNumbers.keys.sortedWith(compareBy<Pair<String, Int>> { it.first }.thenBy { it.second })
                .joinToString("، ") { (_, number) -> number.toString() }
            "شماره دیالوگ در یک نقش تکرار شده است ($details)؛ ورود اطلاعات متوقف شد."
        }

        if (entries.isEmpty()) {
            // بدون شماره یا مرجع قابل اتکا، حدس نمی‌زنیم و متن کامل نقش را وارد نمی‌کنیم.
            return DirectorLibraryImportResult(roles.size, 0, 0, false)
        }
        require(entries.all { it.turnNumber > 0 && it.text.isNotBlank() }) {
            "شماره یا متن یکی از دیالوگ‌ها ناقص است؛ ورود اطلاعات متوقف شد."
        }

        val conflictingNumbers = entries.groupBy { it.turnNumber }
            .filterValues { group ->
                group.map { DirectorOfficeWorkspaceUseCases.normalizeDialogueText(it.text) }.distinct().size > 1
            }
        require(conflictingNumbers.isEmpty()) {
            val details = conflictingNumbers.keys.sorted().joinToString("، ")
            "متن دیالوگ‌های شماره $details یکسان نیست؛ برای جلوگیری از ساخت نوبت اشتباه، ورود اطلاعات متوقف شد."
        }

        transactionRunner.run {
            val existingTurns = dao.turns(versionId)
            val existingTurnIds = existingTurns.map { it.id }
            val existingSegments = if (existingTurnIds.isEmpty()) emptyList() else dao.textSegmentsForTurns(existingTurnIds)
            if (existingSegments.any { it.text.isNotBlank() }) return@run
            val existingParticipants = if (existingTurnIds.isEmpty()) emptyList() else dao.participantsForTurns(existingTurnIds)
            existingParticipants.forEach { dao.deleteParticipant(it.id) }
            existingSegments.forEach { dao.deleteTextSegment(it.id) }
            existingTurns.forEach { dao.deleteTurn(it.id) }

            val groups = entries.groupBy { it.turnNumber }.toSortedMap()
            groups.entries.forEachIndexed { sequence, (number, group) ->
                val distinctRoles = group.map { it.role }.distinctBy { it.uid }
                val turnId = dao.insertTurn(DirectorTurnEntity(
                    versionId = versionId,
                    turnNumber = number,
                    sequenceIndex = sequence,
                    turnType = if (distinctRoles.size > 1) DirectorTurnEntity.TYPE_COLLECTIVE else DirectorTurnEntity.TYPE_NORMAL,
                    title = if (distinctRoles.size > 1) distinctRoles.joinToString("، ") { it.title } else distinctRoles.first().title,
                    notes = ""
                ))
                distinctRoles.forEachIndexed { index, role ->
                    dao.insertParticipant(DirectorParticipantEntity(
                        turnId = turnId, roleUid = role.uid, roleTitle = role.title,
                        orderIndex = index, isLead = index == 0
                    ))
                }
                val sourceSections = group.map { it.section }.distinctBy { it.uid }
                val sourceRoles = group.map { it.role }.distinctBy { it.uid }
                dao.insertTextSegment(DirectorTextSegmentEntity(
                    turnId = turnId,
                    segmentIndex = 0,
                    text = group.first().text,
                    sectionUid = sourceSections.singleOrNull()?.uid,
                    sourceTaziehUid = tazieh.uid,
                    sourceRoleUid = sourceRoles.singleOrNull()?.uid,
                    sourceSectionTitle = sourceSections.map { it.title }.filter { it.isNotBlank() }.distinct().joinToString("، "),
                    sourceSectionUids = sourceSections.joinToString("\u001F") { it.uid },
                    sourceMethod = "LIBRARY_IMPORT",
                    sourceContentHash = ContentUid.sha256(group.joinToString("\n") { it.text })
                ))
            }
        }
        return DirectorLibraryImportResult(
            roleCount = roles.size,
            sectionCount = entries.map { it.section.uid }.distinct().size,
            turnCount = entries.map { it.turnNumber }.distinct().size,
            usedDialogueOrder = bestEntries.isNotEmpty()
        )
    }

}
