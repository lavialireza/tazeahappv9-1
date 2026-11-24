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
        taziehId: Long
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
        taziehId: Long
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

        data class Entry(
            val section: SectionEntity,
            val role: RoleEntity,
            val turnNumber: Int,
            val order: Int,
            val verified: Boolean
        )

        val sectionById = sections.associateBy { it.id }
        val dialogues = archiveVersion?.let { db.dialogueDao().getByVersionUid(it.uid) }
            ?: db.dialogueDao().getByTazieh(taziehId).filter { it.versionUid.isBlank() }
        var bestEntries = emptyList<Entry>()
        var bestCoverage = -1
        for (dialogue in dialogues) {
            val dialogueTurns = db.dialogueTurnDao().getByDialogue(dialogue.id)
            val metadataByTurnId = db.dialogueTurnMetadataDao().getByDialogue(dialogue.id).associateBy { it.turnId }
            val candidate = mutableListOf<Entry>()
            for (sourceTurn in dialogueTurns) {
                val section = sectionById[sourceTurn.sectionId] ?: continue
                val role = roleById[section.roleId] ?: continue
                val metadata = metadataByTurnId[sourceTurn.id]
                val verified = metadata != null && metadata.sourceSequenceIndex != null &&
                    (metadata.sourceSequenceStatus == DialogueTurnMetadataEntity.SOURCE_DOCUMENTED ||
                        metadata.sourceSequenceStatus == DialogueTurnMetadataEntity.SOURCE_DIRECTOR_CONFIRMED)
                val number = if (verified) (metadata!!.sourceSequenceIndex!! + 1) else (sourceTurn.orderIndex + 1)
                candidate += Entry(section, role, number, sourceTurn.orderIndex, verified)
            }
            val coverage = candidate.map { it.section.id }.distinct().size
            if (coverage > bestCoverage) {
                bestCoverage = coverage
                bestEntries = candidate
            }
        }

        val usedDialogue = bestEntries.isNotEmpty()
        val entries = if (usedDialogue) {
            val represented = bestEntries.map { it.section.id }.toSet()
            val nextNumber = (bestEntries.maxOfOrNull { it.turnNumber } ?: 0) + 1
            val missing = sections.filter { it.id !in represented }.mapIndexed { index, section ->
                Entry(section, roleById.getValue(section.roleId), nextNumber + index,
                    Int.MAX_VALUE - sections.size + index, false)
            }
            bestEntries + missing
        } else sections.mapIndexed { index, section ->
            Entry(section, roleById.getValue(section.roleId), index + 1, index, false)
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
            var sequence = 0
            for ((number, group) in entries.groupBy { it.turnNumber }.toSortedMap()) {
                val distinctRoles = group.map { it.role }.distinctBy { it.uid }
                val turnId = dao.insertTurn(DirectorTurnEntity(
                    versionId = versionId, turnNumber = number, sequenceIndex = sequence++,
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
                group.forEachIndexed { index, entry ->
                    dao.insertTextSegment(DirectorTextSegmentEntity(
                        turnId = turnId, segmentIndex = index, text = entry.section.content,
                        sectionUid = entry.section.uid, sourceTaziehUid = tazieh.uid,
                        sourceRoleUid = entry.role.uid, sourceSectionTitle = entry.section.title,
                        sourceSectionUids = entry.section.uid, sourceMethod = "LIBRARY_IMPORT",
                        sourceContentHash = ContentUid.sha256(entry.section.content)
                    ))
                }
            }
        }
        return DirectorLibraryImportResult(roles.size, sections.size,
            entries.groupBy { it.turnNumber }.size, usedDialogue)
    }
}
