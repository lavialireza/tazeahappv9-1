package com.example.bookapp.data

/**
 * عملیات مشترک دفتر معین‌البکاء برای Admin و Viewer.
 * این لایه فقط روی DAO تحویلی کار می‌کند؛ بنابراین هیچ‌گاه دیتابیس دو برنامه را مخلوط نمی‌کند.
 * انتشار، امضا، مدیریت کاربر و واردکردن بسته رسمی عمداً در این هسته وجود ندارند.
 */
object DirectorOfficeWorkspaceOperations {
    private suspend fun recordHistory(
        dao: DirectorOfficeWorkspaceDao,
        plan: DirectorPlanEntity,
        version: DirectorPlanVersionEntity,
        turnId: Long?,
        entityType: String,
        fieldName: String,
        changeType: String,
        summary: String,
        oldValue: String,
        newValue: String,
        restoredFromId: Long? = null
    ) {
        if (oldValue == newValue) return
        dao.insertChangeHistory(
            DirectorChangeHistoryEntity(
                planId = plan.id,
                versionId = version.id,
                turnId = turnId,
                entityType = entityType,
                fieldName = fieldName,
                changeType = changeType,
                summary = summary,
                oldValue = oldValue,
                newValue = newValue,
                changedAt = System.currentTimeMillis(),
                restoredFromId = restoredFromId
            )
        )
    }

    private fun mergedSourceSectionUids(segments: List<DirectorTextSegmentEntity>): String =
        segments.flatMap(DirectorOfficeHistorySnapshots::sourceSectionIds).distinct().joinToString("\u001F")

    private fun mergedSourceTitle(segments: List<DirectorTextSegmentEntity>): String =
        segments.map { it.sourceSectionTitle }.filter { it.isNotBlank() }.distinct().joinToString("، ")

    /**
     * ساخت مشترک دفتر بر پایه کتابخانه نقش‌ها و بخش‌ها. الگوریتم شماره‌محور و متن کامل
     * در Admin و Viewer دقیقاً یکی است؛ DAO تعیین می‌کند داده در کدام دیتابیس مستقل ذخیره شود.
     * MANUAL اطلاعات موجود را پاک نمی‌کند؛ SELECTOR فقط نوبت‌های فعلی را خالی می‌کند تا
     * کاربر آن‌ها را انتخاب کند.
     */
    suspend fun buildDraftFromLibrary(
        dao: DirectorOfficeWorkspaceDao,
        versionId: Long,
        method: String,
        roles: List<DirectorOfficeBuildRole>,
        sections: List<DirectorOfficeBuildSection>,
        transactionRunner: DirectorOfficeTransactionRunner = DirectDirectorOfficeTransactionRunner
    ): DirectorOfficeBuildResult {
        require(method in DirectorOfficeSharedContract.methodCodes) { "روش ساخت دفتر معتبر نیست." }
        val version = dao.version(versionId) ?: error("نسخه دفتر پیدا نشد.")
        val plan = dao.plan(version.planId) ?: error("دفتر پیدا نشد.")
        DirectorOfficeWorkspaceUseCases.requireDraft(plan, version)
        require(roles.map { it.uid }.distinct().size == roles.size) { "شناسه نقش‌ها تکراری است." }
        require(sections.all { section -> roles.any { it.uid == section.roleUid } }) { "بخشی به نقشی خارج از مجلس انتخاب‌شده متصل است." }

        val orderedRoles = roles
        val roleByUid = orderedRoles.associateBy { it.uid }
        val orderedSections = sections.filter { it.content.isNotBlank() }
        val byNumber = linkedMapOf<Int, Long>()
        val byRole = linkedMapOf<String, Long>()
        var insertedCount = 0

        data class NumberedEntry(
            val number: Int,
            val role: DirectorOfficeBuildRole,
            val section: DirectorOfficeBuildSection,
            val text: String
        )

        // تمام داده‌های قابل پردازش پیش از پاک‌سازی نسخه آماده می‌شوند.
        val numberedEntries = if (method == DirectorOfficeSharedContract.ROLE_NUMBERED) {
            orderedSections.flatMap { section ->
                val role = roleByUid[section.roleUid] ?: return@flatMap emptyList()
                DirectorOfficeWorkspaceUseCases.parseNumberedDialogues(section.content).map { (number, text) ->
                    NumberedEntry(number, role, section, text)
                }
            }
        } else emptyList()

        val fullTextRoleGroups = if (method == DirectorOfficeSharedContract.FULL_TEXT) {
            orderedRoles.mapNotNull { role ->
                val roleSections = orderedSections.filter { it.roleUid == role.uid }
                if (roleSections.isEmpty()) null else role to roleSections
            }
        } else emptyList()

        transactionRunner.run {
            val existingTurns = dao.turns(versionId)

            // اگر روش انتخابی در متن فعلی چیزی پیدا نکند، دفتر تازه به‌صورت پیش‌نویس خالی
            // ذخیره می‌شود؛ اما هنگام بازسازی، اطلاعات قبلی هرگز بی‌صدا پاک نمی‌شوند.
            if (method == DirectorOfficeSharedContract.ROLE_NUMBERED && numberedEntries.isEmpty() && existingTurns.isNotEmpty()) {
                error("در متن این مجلس نوبت شماره‌دار پیدا نشد؛ نوبت‌های قبلی حفظ شدند و تغییری در دفتر اعمال نشد.")
            }
            if (method == DirectorOfficeSharedContract.FULL_TEXT && fullTextRoleGroups.isEmpty() && existingTurns.isNotEmpty()) {
                error("متن قابل استفاده‌ای برای ساخت نسخه جدید پیدا نشد؛ نوبت‌های قبلی حفظ شدند.")
            }

            if (method != DirectorOfficeSharedContract.MANUAL) {
                existingTurns.forEach { dao.deleteTurn(it.id) }
            }

            when (method) {
                DirectorOfficeSharedContract.ROLE_NUMBERED -> {
                    if (numberedEntries.isNotEmpty()) {
                        val groups = numberedEntries.groupBy { it.number }.toSortedMap().entries.toList()
                        val turnRows = groups.mapIndexed { sequence, (number, numberEntries) ->
                            val distinctRoles = numberEntries.map { it.role }.distinctBy { it.uid }
                            DirectorTurnEntity(
                                versionId = versionId,
                                turnNumber = number,
                                sequenceIndex = sequence,
                                turnType = if (distinctRoles.size > 1) DirectorTurnEntity.TYPE_COLLECTIVE else DirectorTurnEntity.TYPE_NORMAL,
                                title = if (distinctRoles.size > 1) distinctRoles.joinToString("، ") { it.title } else distinctRoles.first().title
                            )
                        }
                        val turnIds = dao.insertTurns(turnRows)
                        val participantRows = mutableListOf<DirectorParticipantEntity>()
                        val segmentRows = mutableListOf<DirectorTextSegmentEntity>()
                        groups.forEachIndexed { index, (number, numberEntriesForTurn) ->
                            val turnId = turnIds[index]
                            byNumber[number] = turnId
                            numberEntriesForTurn.map { it.role }.distinctBy { it.uid }.forEachIndexed { roleIndex, role ->
                                participantRows += DirectorParticipantEntity(
                                    turnId = turnId, roleUid = role.uid, roleTitle = role.title,
                                    orderIndex = roleIndex, isLead = roleIndex == 0
                                )
                            }
                            numberEntriesForTurn.forEachIndexed { segmentIndex, entry ->
                                segmentRows += DirectorTextSegmentEntity(
                                    turnId = turnId, segmentIndex = segmentIndex, text = entry.text,
                                    sectionUid = entry.section.uid, sourceTaziehUid = plan.taziehUid,
                                    sourceRoleUid = entry.role.uid, sourceSectionTitle = entry.section.title,
                                    sourceSectionUids = entry.section.uid, sourceMethod = method, sourceContentHash = ContentUid.sha256(entry.section.content)
                                )
                            }
                        }
                        if (participantRows.isNotEmpty()) dao.insertParticipants(participantRows)
                        if (segmentRows.isNotEmpty()) dao.insertTextSegments(segmentRows)
                        insertedCount = groups.size
                    }
                }
                DirectorOfficeSharedContract.FULL_TEXT -> {
                    if (fullTextRoleGroups.isNotEmpty()) {
                        val turnRows = fullTextRoleGroups.mapIndexed { sequence, (role, _) ->
                            DirectorTurnEntity(
                                versionId = versionId, turnNumber = sequence + 1,
                                sequenceIndex = sequence, title = role.title
                            )
                        }
                        val turnIds = dao.insertTurns(turnRows)
                        val participantRows = mutableListOf<DirectorParticipantEntity>()
                        val segmentRows = mutableListOf<DirectorTextSegmentEntity>()
                        fullTextRoleGroups.forEachIndexed { index, (role, roleSections) ->
                            val turnId = turnIds[index]
                            byRole[role.uid] = turnId
                            participantRows += DirectorParticipantEntity(
                                turnId = turnId, roleUid = role.uid, roleTitle = role.title, isLead = true
                            )
                            roleSections.forEachIndexed { segmentIndex, section ->
                                segmentRows += DirectorTextSegmentEntity(
                                    turnId = turnId, segmentIndex = segmentIndex, text = section.content,
                                    sectionUid = section.uid, sourceTaziehUid = plan.taziehUid,
                                    sourceRoleUid = role.uid, sourceSectionTitle = section.title,
                                    sourceSectionUids = section.uid, sourceMethod = method, sourceContentHash = ContentUid.sha256(section.content)
                                )
                            }
                        }
                        if (participantRows.isNotEmpty()) dao.insertParticipants(participantRows)
                        if (segmentRows.isNotEmpty()) dao.insertTextSegments(segmentRows)
                        insertedCount = fullTextRoleGroups.size
                    }
                }
                DirectorOfficeSharedContract.MANUAL, DirectorOfficeSharedContract.SELECTOR -> Unit
            }
            dao.updatePlan(plan.copy(updatedAt = System.currentTimeMillis()))
        }
        return DirectorOfficeBuildResult(byNumber.toMap(), byRole.toMap(), insertedCount)
    }
    /** ساخت یک نوبت متصل به نقش و بخش کتابخانه؛ داده منبع فقط خوانده می‌شود. */
    suspend fun addRoleLinkedTurn(
        dao: DirectorOfficeWorkspaceDao,
        versionId: Long,
        turnNumber: Int,
        roleUid: String,
        roleTitle: String,
        text: String,
        sectionUid: String? = null,
        allowPublishedPlan: Boolean = false,
        transactionRunner: DirectorOfficeTransactionRunner = DirectDirectorOfficeTransactionRunner
    ) {
        require(turnNumber > 0 && roleUid.isNotBlank() && roleTitle.isNotBlank() && text.isNotBlank()) {
            "اطلاعات نقش یا نوبت معتبر نیست."
        }
        val version = dao.version(versionId) ?: error("نسخه دفتر پیدا نشد.")
        val plan = dao.plan(version.planId) ?: error("دفتر پیدا نشد.")
        require(version.status == DirectorPlanVersionEntity.STATUS_DRAFT) { "نسخه منتشرشده قابل ویرایش نیست." }
        require(plan.status != DirectorPlanEntity.STATUS_ARCHIVED) { "دفتر بایگانی‌شده قابل ویرایش نیست." }
        if (!allowPublishedPlan) {
            require(plan.status != DirectorPlanEntity.STATUS_PUBLISHED) { "برای تغییر دفتر منتشرشده ابتدا نسخه پیش‌نویس جدید ایجاد کنید." }
        }
        val nextIndex = DirectorOfficeWorkspaceUseCases.nextSequenceIndex(dao.turns(versionId))
        transactionRunner.run {
            val turnId = dao.insertTurn(DirectorTurnEntity(
                versionId = versionId, turnNumber = turnNumber, sequenceIndex = nextIndex, title = roleTitle.trim()
            ))
            dao.insertParticipant(DirectorParticipantEntity(
                turnId = turnId, roleUid = roleUid.trim(), roleTitle = roleTitle.trim(), isLead = true
            ))
            dao.insertTextSegment(DirectorTextSegmentEntity(
                turnId = turnId, segmentIndex = 0, text = text.trim(), sectionUid = sectionUid,
                sourceTaziehUid = plan.taziehUid, sourceRoleUid = roleUid.trim(),
                sourceSectionUids = sectionUid.orEmpty(),
                sourceMethod = if (sectionUid.isNullOrBlank()) DirectorOfficeSharedContract.MANUAL else "LIBRARY_ROLE_LINKED",
                sourceContentHash = if (sectionUid.isNullOrBlank()) "" else ContentUid.sha256(text.trim())
            ))
            dao.updatePlan(plan.copy(updatedAt = System.currentTimeMillis()))
        }
    }
    private suspend fun requireEditableTurn(dao: DirectorOfficeWorkspaceDao, turnId: Long): Pair<DirectorPlanEntity, DirectorPlanVersionEntity> {
        val turn = dao.turn(turnId) ?: error("نوبت دفتر پیدا نشد.")
        val version = dao.version(turn.versionId) ?: error("نسخه دفتر پیدا نشد.")
        val plan = dao.plan(version.planId) ?: error("دفتر پیدا نشد.")
        DirectorOfficeWorkspaceUseCases.requireDraft(plan, version)
        return plan to version
    }

    private suspend fun requireEditableCue(dao: DirectorOfficeWorkspaceDao, cueId: Long): Pair<DirectorPlanEntity, DirectorPlanVersionEntity> {
        val cue = dao.stageCue(cueId) ?: error("راهنمای صحنه پیدا نشد.")
        val version = dao.version(cue.versionId) ?: error("نسخه دفتر پیدا نشد.")
        val plan = dao.plan(version.planId) ?: error("دفتر پیدا نشد.")
        DirectorOfficeWorkspaceUseCases.requireDraft(plan, version)
        return plan to version
    }

    suspend fun updateTurn(dao: DirectorOfficeWorkspaceDao, value: DirectorTurnEntity) {
        require(value.turnNumber > 0 && value.title.isNotBlank()) { "شماره و عنوان نوبت معتبر نیست." }
        val current = dao.turn(value.id) ?: error("نوبت دفتر پیدا نشد.")
        val (plan, _) = requireEditableTurn(dao, current.id)
        require(value.versionId == current.versionId && value.sequenceIndex == current.sequenceIndex) {
            "شناسه نسخه و جایگاه نوبت از فرم ویرایش قابل تغییر نیست."
        }
        dao.updateTurn(value)
        val version = dao.version(value.versionId) ?: error("نسخه دفتر پیدا نشد.")
        if (current.turnNumber != value.turnNumber) {
            recordHistory(dao, plan, version, current.id, "TURN", "turnNumber", "UPDATE",
                "شماره نوبت از ${current.turnNumber} به ${value.turnNumber} تغییر کرد.", current.turnNumber.toString(), value.turnNumber.toString())
        }
        if (current.title != value.title) {
            recordHistory(dao, plan, version, current.id, "TURN", "title", "UPDATE",
                "عنوان نوبت از «${current.title}» به «${value.title}» تغییر کرد.", current.title, value.title)
        }
        dao.updatePlan(plan.copy(updatedAt = System.currentTimeMillis()))
    }

    suspend fun updateTextSegment(dao: DirectorOfficeWorkspaceDao, value: DirectorTextSegmentEntity) {
        val (plan, version) = requireEditableTurn(dao, value.turnId)
        val before = dao.textSegments(value.turnId).sortedBy { it.segmentIndex }
        require(before.any { it.id == value.id }) { "متن دیالوگ متعلق به این نوبت نیست." }
        require(value.text.isNotBlank()) { "متن دیالوگ خالی است." }
        val oldSnapshot = DirectorOfficeHistorySnapshots.encodeSegments(before)
        dao.updateTextSegment(value)
        val after = dao.textSegments(value.turnId).sortedBy { it.segmentIndex }
        recordHistory(dao, plan, version, value.turnId, "DIALOGUE", "segments", "UPDATE",
            "متن دیالوگ نوبت ${dao.turn(value.turnId)?.turnNumber ?: ""} ویرایش شد.",
            oldSnapshot, DirectorOfficeHistorySnapshots.encodeSegments(after))
        dao.updatePlan(plan.copy(updatedAt = System.currentTimeMillis()))
    }

    suspend fun addDialogueSegment(dao: DirectorOfficeWorkspaceDao, turnId: Long, text: String) {
        require(text.isNotBlank()) { "متن دیالوگ خالی است." }
        val (plan, version) = requireEditableTurn(dao, turnId)
        val before = dao.textSegments(turnId).sortedBy { it.segmentIndex }
        val nextIndex = (before.maxOfOrNull { it.segmentIndex } ?: -1) + 1
        dao.insertTextSegment(DirectorTextSegmentEntity(turnId = turnId, segmentIndex = nextIndex, text = text.trim()))
        val after = dao.textSegments(turnId).sortedBy { it.segmentIndex }
        recordHistory(dao, plan, version, turnId, "DIALOGUE", "segments", "ADD",
            "یک بخش دیالوگ به نوبت ${dao.turn(turnId)?.turnNumber ?: ""} اضافه شد.",
            DirectorOfficeHistorySnapshots.encodeSegments(before), DirectorOfficeHistorySnapshots.encodeSegments(after))
        dao.updatePlan(plan.copy(updatedAt = System.currentTimeMillis()))
    }

    /**
     * افزودن یک دیالوگ انتخاب‌شده از کتابخانه به نوبت؛ الگوریتم واحد برای Admin و Viewer.
     * شناسه گروه پیوستگی از کلید گزینه انتخاب‌گر می‌آید تا دیالوگ‌های متعلق به همان منبع
     * در صورت انتخاب مجدد قابل شناسایی باشند.
     */
    suspend fun addSelectorDialogue(
        dao: DirectorOfficeWorkspaceDao,
        planId: Long,
        entry: DirectorOfficeSelectorEntry,
        requestedTurnNumber: Int? = null,
        requestedVersionId: Long? = null
    ) {
        require(entry.key.isNotBlank() && entry.roleUid.isNotBlank() && entry.roleTitle.isNotBlank() &&
            entry.sectionUid.isNotBlank() && entry.text.isNotBlank()) {
            "گزینه انتخاب‌شده برای ساخت نوبت معتبر نیست."
        }
        val plan = dao.plan(planId) ?: error("دفتر پیدا نشد.")
        val version = if (requestedVersionId != null) {
            dao.version(requestedVersionId) ?: error("نسخه انتخاب‌شده پیدا نشد.")
        } else {
            dao.versionsForPlan(planId).firstOrNull { it.status == DirectorPlanVersionEntity.STATUS_DRAFT }
                ?: error("نسخه پیش‌نویس دفتر پیدا نشد.")
        }
        require(version.planId == planId) { "نسخه انتخابی متعلق به این دفتر نیست." }
        DirectorOfficeWorkspaceUseCases.requireDraft(plan, version)
        val currentTurns = dao.turns(version.id)
        val nextSequence = DirectorOfficeWorkspaceUseCases.nextSequenceIndex(currentTurns)
        val nextNumber = requestedTurnNumber ?: DirectorOfficeWorkspaceUseCases.nextTurnNumber(currentTurns)
        require(nextNumber > 0) { "شماره نوبت باید بزرگ‌تر از صفر باشد." }
        val title = entry.roleTitle.trim()
        val turnId = dao.insertTurn(DirectorTurnEntity(
            versionId = version.id,
            turnNumber = nextNumber,
            sequenceIndex = nextSequence,
            title = title,
            continuityGroupUid = entry.key
        ))
        dao.insertParticipant(DirectorParticipantEntity(
            turnId = turnId, roleUid = entry.roleUid.trim(), roleTitle = title, isLead = true
        ))
        dao.insertTextSegment(DirectorTextSegmentEntity(
            turnId = turnId, segmentIndex = 0, text = entry.text.trim(), sectionUid = entry.sectionUid,
            sourceTaziehUid = plan.taziehUid, sourceRoleUid = entry.roleUid.trim(),
            sourceSectionTitle = entry.sectionTitle, sourceSectionUids = entry.sectionUid,
            sourceMethod = DirectorOfficeSharedContract.SELECTOR,
            sourceContentHash = entry.sourceContentHash.ifBlank { ContentUid.sha256(entry.text.trim()) }
        ))
        dao.updatePlan(plan.copy(updatedAt = System.currentTimeMillis()))
    }

    suspend fun updateStageCue(dao: DirectorOfficeWorkspaceDao, value: DirectorStageCueEntity) {
        val (plan, version) = requireEditableCue(dao, value.id)
        require(value.versionId == version.id && value.title.isNotBlank() && value.instruction.isNotBlank()) {
            "اطلاعات راهنمای صحنه معتبر نیست."
        }
        require(value.turnId == null || dao.turn(value.turnId)?.versionId == version.id) {
            "راهنمای صحنه باید به نوبت همین نسخه متصل باشد."
        }
        dao.updateStageCue(value)
        dao.updatePlan(plan.copy(updatedAt = System.currentTimeMillis()))
    }

    suspend fun addStageCue(
        dao: DirectorOfficeWorkspaceDao,
        planId: Long,
        title: String,
        instruction: String,
        turnId: Long? = null,
        cueType: String = DirectorStageCueEntity.CUE_OTHER,
        blocksNextTurn: Boolean = cueType == DirectorStageCueEntity.CUE_IMMEDIATE,
        continuationRoleUid: String = ""
    ) {
        require(title.isNotBlank() && instruction.isNotBlank()) { "عنوان و متن راهنمای صحنه الزامی است." }
        val plan = dao.plan(planId) ?: error("دفتر پیدا نشد.")
        val version = if (turnId != null) {
            val turn = dao.turn(turnId) ?: error("نوبت دفتر پیدا نشد.")
            dao.version(turn.versionId) ?: error("نسخه دفتر پیدا نشد.")
        } else {
            dao.versionsForPlan(planId).firstOrNull { it.status == DirectorPlanVersionEntity.STATUS_DRAFT }
                ?: error("نسخه پیش‌نویس دفتر پیدا نشد.")
        }
        DirectorOfficeWorkspaceUseCases.requireDraft(plan, version)
        require(version.planId == planId && (turnId == null || dao.turn(turnId)?.versionId == version.id)) {
            "راهنمای صحنه باید به نوبت همین نسخه متصل باشد."
        }
        val order = (dao.stageCues(version.id).maxOfOrNull { it.orderIndex } ?: -1) + 1
        dao.insertStageCue(DirectorStageCueEntity(
            versionId = version.id, turnId = turnId, cueType = cueType, title = title.trim(),
            instruction = instruction.trim(), orderIndex = order, blocksNextTurn = blocksNextTurn,
            continuationRoleUid = continuationRoleUid
        ))
        dao.updatePlan(plan.copy(updatedAt = System.currentTimeMillis()))
    }

    suspend fun addTurn(
        dao: DirectorOfficeWorkspaceDao,
        planId: Long,
        turnNumber: Int,
        title: String,
        text: String
    ) {
        require(turnNumber > 0) { "شماره نوبت باید بزرگ‌تر از صفر باشد." }
        require(title.isNotBlank() && text.isNotBlank()) { "عنوان نقش و متن نوبت را وارد کنید." }
        val plan = dao.plan(planId) ?: error("دفتر پیدا نشد.")
        val version = dao.versionsForPlan(planId).firstOrNull { it.status == DirectorPlanVersionEntity.STATUS_DRAFT } ?: error("نسخه پیش‌نویس دفتر پیدا نشد.")
        DirectorOfficeWorkspaceUseCases.requireDraft(plan, version)
        val nextIndex = DirectorOfficeWorkspaceUseCases.nextSequenceIndex(dao.turns(version.id))
        val turnId = dao.insertTurn(DirectorTurnEntity(versionId = version.id, turnNumber = turnNumber, sequenceIndex = nextIndex, title = title.trim()))
        dao.insertParticipant(DirectorParticipantEntity(turnId = turnId, roleUid = "personal-role-" + java.util.UUID.randomUUID(), roleTitle = title.trim(), isLead = true))
        dao.insertTextSegment(DirectorTextSegmentEntity(turnId = turnId, segmentIndex = 0, text = text.trim()))
        dao.updatePlan(plan.copy(updatedAt = System.currentTimeMillis()))
    }


    /** متن یکپارچه ویرایشگر Admin را با حفظ شناسه اولین قطعه ذخیره می‌کند. */
    suspend fun replaceTurnText(dao: DirectorOfficeWorkspaceDao, turnId: Long, text: String) {
        val (plan, version) = requireEditableTurn(dao, turnId)
        val current = dao.textSegments(turnId).sortedBy { it.segmentIndex }
        val oldSnapshot = DirectorOfficeHistorySnapshots.encodeSegments(current)
        if (current.isEmpty()) {
            dao.insertTextSegment(DirectorTextSegmentEntity(turnId = turnId, segmentIndex = 0, text = text.trim()))
        } else {
            val first = current.first()
            val sourceUids = mergedSourceSectionUids(current)
            val sourceTitles = mergedSourceTitle(current)
            val sourceRoles = current.mapNotNull { it.sourceRoleUid }.distinct()
            val originalHash = if (current.size == 1) first.sourceContentHash else ""
            dao.updateTextSegment(first.copy(
                segmentIndex = 0,
                text = text.trim(),
                sourceTaziehUid = current.firstNotNullOfOrNull { it.sourceTaziehUid },
                sourceRoleUid = sourceRoles.singleOrNull(),
                sourceSectionTitle = sourceTitles,
                sourceSectionUids = sourceUids,
                sourceMethod = if (current.size == 1) first.sourceMethod else "MERGED_EDIT",
                sourceContentHash = originalHash
            ))
            current.drop(1).forEach { dao.deleteTextSegment(it.id) }
        }
        val after = dao.textSegments(turnId).sortedBy { it.segmentIndex }
        recordHistory(dao, plan, version, turnId, "DIALOGUE", "segments", "UPDATE",
            "متن کامل دیالوگ نوبت ${dao.turn(turnId)?.turnNumber ?: ""} ویرایش شد.",
            oldSnapshot, DirectorOfficeHistorySnapshots.encodeSegments(after))
        dao.updatePlan(plan.copy(updatedAt = System.currentTimeMillis()))
    }

    /** ترتیب نوبت‌ها را به شکل اتمیک از سوی فراخواننده جابه‌جا می‌کند؛ شاخص موقت منفی از برخورد unique جلوگیری می‌کند. */
    suspend fun reorderTurns(
        dao: DirectorOfficeWorkspaceDao,
        orderedTurns: List<DirectorTurnEntity>,
        transactionRunner: DirectorOfficeTransactionRunner = DirectDirectorOfficeTransactionRunner
    ) {
        require(orderedTurns.map { it.id }.distinct().size == orderedTurns.size) { "فهرست نوبت‌ها تکراری است." }
        val first = orderedTurns.firstOrNull() ?: return
        val version = dao.version(first.versionId) ?: error("نسخه دفتر پیدا نشد.")
        val plan = dao.plan(version.planId) ?: error("دفتر پیدا نشد.")
        DirectorOfficeWorkspaceUseCases.requireDraft(plan, version)
        require(orderedTurns.all { it.versionId == version.id }) { "ترتیب نوبت‌های چند نسخه قابل تغییر هم‌زمان نیست." }
        val current = dao.turns(version.id)
        require(orderedTurns.map { it.id }.toSet() == current.map { it.id }.toSet()) { "ترتیب باید شامل تمام نوبت‌های همان نسخه باشد." }
        transactionRunner.run {
            orderedTurns.forEachIndexed { index, turn -> dao.moveTurn(turn.id, -100000 - index) }
            orderedTurns.forEachIndexed { index, turn -> dao.moveTurn(turn.id, index) }
            dao.updatePlan(plan.copy(updatedAt = System.currentTimeMillis()))
        }
    }

    suspend fun addParticipant(dao: DirectorOfficeWorkspaceDao, turnId: Long, roleUid: String, roleTitle: String, isLead: Boolean = false) {
        require(roleUid.isNotBlank() && roleTitle.isNotBlank()) { "نقش معتبر نیست." }
        val (plan, version) = requireEditableTurn(dao, turnId)
        val current = dao.participants(turnId)
        require(current.none { it.roleUid == roleUid }) { "این نقش قبلاً به نوبت اضافه شده است." }
        val beforeSnapshot = DirectorOfficeHistorySnapshots.encodeParticipants(current)
        dao.insertParticipant(DirectorParticipantEntity(
            turnId = turnId, roleUid = roleUid, roleTitle = roleTitle.trim(),
            orderIndex = (current.maxOfOrNull { it.orderIndex } ?: -1) + 1,
            isLead = isLead || current.isEmpty()
        ))
        val after = dao.participants(turnId)
        val turn = dao.turn(turnId) ?: error("نوبت دفتر پیدا نشد.")
        if (current.isNotEmpty()) dao.updateTurn(turn.copy(turnType = DirectorTurnEntity.TYPE_COLLECTIVE))
        recordHistory(dao, plan, version, turnId, "ROLE", "participants", "ADD",
            "نقش «${roleTitle.trim()}» به نوبت اضافه شد.", beforeSnapshot, DirectorOfficeHistorySnapshots.encodeParticipants(after))
        dao.updatePlan(plan.copy(updatedAt = System.currentTimeMillis()))
    }

    suspend fun updateParticipant(dao: DirectorOfficeWorkspaceDao, value: DirectorParticipantEntity) {
        val (plan, version) = requireEditableTurn(dao, value.turnId)
        val current = dao.participants(value.turnId)
        val old = current.firstOrNull { it.id == value.id } ?: error("نقش این نوبت پیدا نشد.")
        require(value.turnId == old.turnId && value.roleUid.isNotBlank() && value.roleTitle.isNotBlank()) {
            "اطلاعات نقش معتبر نیست."
        }
        require(current.none { it.id != old.id && it.roleUid == value.roleUid }) {
            "این نقش قبلاً به نوبت اضافه شده است."
        }
        val beforeSnapshot = DirectorOfficeHistorySnapshots.encodeParticipants(current)
        dao.updateParticipant(value.copy(roleTitle = value.roleTitle.trim()))
        val after = dao.participants(value.turnId)
        recordHistory(
            dao, plan, version, value.turnId, "ROLE", "participants", "UPDATE",
            "اطلاعات نقش «${old.roleTitle}» اصلاح شد.", beforeSnapshot,
            DirectorOfficeHistorySnapshots.encodeParticipants(after)
        )
        dao.updatePlan(plan.copy(updatedAt = System.currentTimeMillis()))
    }

    suspend fun removeParticipant(dao: DirectorOfficeWorkspaceDao, participantId: Long, turnId: Long) {
        val (plan, version) = requireEditableTurn(dao, turnId)
        val current = dao.participants(turnId)
        val beforeSnapshot = DirectorOfficeHistorySnapshots.encodeParticipants(current)
        val target = current.firstOrNull { it.id == participantId } ?: error("نقش این نوبت پیدا نشد.")
        dao.deleteParticipant(target.id)
        val remaining = dao.participants(turnId)
        if (target.isLead && remaining.isNotEmpty()) {
            dao.updateParticipant(remaining.first().copy(isLead = true))
        }
        val after = dao.participants(turnId)
        val turn = dao.turn(turnId) ?: error("نوبت دفتر پیدا نشد.")
        dao.updateTurn(turn.copy(turnType = if (after.size > 1) DirectorTurnEntity.TYPE_COLLECTIVE else DirectorTurnEntity.TYPE_NORMAL))
        recordHistory(dao, plan, version, turnId, "ROLE", "participants", "REMOVE",
            "نقش «${target.roleTitle}» از نوبت حذف شد.", beforeSnapshot, DirectorOfficeHistorySnapshots.encodeParticipants(after))
        dao.updatePlan(plan.copy(updatedAt = System.currentTimeMillis()))
    }

    suspend fun setLeadParticipant(dao: DirectorOfficeWorkspaceDao, turnId: Long, participantId: Long) {
        val (plan, version) = requireEditableTurn(dao, turnId)
        val current = dao.participants(turnId)
        require(current.any { it.id == participantId }) { "نقش انتخاب‌شده متعلق به این نوبت نیست." }
        val beforeSnapshot = DirectorOfficeHistorySnapshots.encodeParticipants(current)
        current.forEach { participant ->
            if (participant.isLead != (participant.id == participantId)) {
                dao.updateParticipant(participant.copy(isLead = participant.id == participantId))
            }
        }
        val after = dao.participants(turnId)
        val oldLead = current.firstOrNull { it.isLead }?.roleTitle.orEmpty()
        val newLead = after.firstOrNull { it.isLead }?.roleTitle.orEmpty()
        recordHistory(dao, plan, version, turnId, "ROLE", "participants", "UPDATE",
            "نقش سرپرست از «$oldLead» به «$newLead» تغییر کرد.", beforeSnapshot, DirectorOfficeHistorySnapshots.encodeParticipants(after))
        dao.updatePlan(plan.copy(updatedAt = System.currentTimeMillis()))
    }

    suspend fun deleteTurn(dao: DirectorOfficeWorkspaceDao, turnId: Long) {
        val (plan, _) = requireEditableTurn(dao, turnId)
        dao.deleteTurn(turnId)
        dao.updatePlan(plan.copy(updatedAt = System.currentTimeMillis()))
    }

    suspend fun deleteTextSegment(dao: DirectorOfficeWorkspaceDao, segment: DirectorTextSegmentEntity) {
        val (plan, version) = requireEditableTurn(dao, segment.turnId)
        val before = dao.textSegments(segment.turnId).sortedBy { it.segmentIndex }
        require(before.any { it.id == segment.id }) { "متن دیالوگ متعلق به این نوبت نیست." }
        dao.deleteTextSegment(segment.id)
        val after = dao.textSegments(segment.turnId).sortedBy { it.segmentIndex }
        recordHistory(dao, plan, version, segment.turnId, "DIALOGUE", "segments", "REMOVE",
            "یک بخش دیالوگ از نوبت ${dao.turn(segment.turnId)?.turnNumber ?: ""} حذف شد.",
            DirectorOfficeHistorySnapshots.encodeSegments(before), DirectorOfficeHistorySnapshots.encodeSegments(after))
        dao.updatePlan(plan.copy(updatedAt = System.currentTimeMillis()))
    }

    /** Restore a previous text, turn-number/title or role snapshot; source-library rows are never touched. */
    suspend fun restoreChangeHistory(dao: DirectorOfficeWorkspaceDao, historyId: Long) {
        val entry = dao.changeHistoryEntry(historyId) ?: error("سابقه تغییر پیدا نشد.")
        val turnId = entry.turnId ?: error("این سابقه به نوبت مشخصی متصل نیست و قابل بازگردانی نیست.")
        val (plan, version) = requireEditableTurn(dao, turnId)
        require(entry.planId == plan.id && entry.versionId == version.id) { "سابقه متعلق به نسخه انتخاب‌شده نیست." }
        when (entry.entityType) {
            "DIALOGUE" -> {
                require(entry.fieldName == "segments") { "نوع سابقه دیالوگ پشتیبانی نمی‌شود." }
                require(entry.oldValue.isBlank() || entry.oldValue.startsWith("v1\n")) { "قالب سابقه متن قابل بازگردانی نیست." }
                val current = dao.textSegments(turnId).sortedBy { it.segmentIndex }
                val restored = DirectorOfficeHistorySnapshots.decodeSegments(entry.oldValue, turnId).map { segment ->
                    val fileId = segment.sourceFileId
                    if (fileId != null && dao.sourceFile(fileId) == null) segment.copy(sourceFileId = null) else segment
                }
                current.forEach { dao.deleteTextSegment(it.id) }
                if (restored.isNotEmpty()) dao.insertTextSegments(restored)
                val after = dao.textSegments(turnId).sortedBy { it.segmentIndex }
                recordHistory(dao, plan, version, turnId, "DIALOGUE", "segments", "RESTORE",
                    "نسخه قبلی متن نوبت ${dao.turn(turnId)?.turnNumber ?: ""} بازگردانی شد.",
                    DirectorOfficeHistorySnapshots.encodeSegments(current), DirectorOfficeHistorySnapshots.encodeSegments(after), historyId)
            }
            "TURN" -> {
                val current = dao.turn(turnId) ?: error("نوبت دیگر وجود ندارد.")
                val restored = when (entry.fieldName) {
                    "turnNumber" -> current.copy(turnNumber = entry.oldValue.toIntOrNull() ?: error("شماره قبلی معتبر نیست."))
                    "title" -> current.copy(title = entry.oldValue)
                    else -> error("فیلد نوبت قابل بازگردانی نیست.")
                }
                dao.updateTurn(restored)
                recordHistory(dao, plan, version, turnId, "TURN", entry.fieldName, "RESTORE",
                    "${entry.summary} (بازگردانی شد)", entry.newValue, entry.oldValue, historyId)
            }
            "ROLE" -> {
                require(entry.fieldName == "participants") { "نوع سابقه نقش پشتیبانی نمی‌شود." }
                require(entry.oldValue.isBlank() || entry.oldValue.startsWith("v1\n")) { "قالب سابقه نقش قابل بازگردانی نیست." }
                val current = dao.participants(turnId)
                val restored = DirectorOfficeHistorySnapshots.decodeParticipants(entry.oldValue, turnId)
                current.forEach { dao.deleteParticipant(it.id) }
                if (restored.isNotEmpty()) dao.insertParticipants(restored)
                val after = dao.participants(turnId)
                val turn = dao.turn(turnId) ?: error("نوبت دیگر وجود ندارد.")
                dao.updateTurn(turn.copy(turnType = if (after.size > 1) DirectorTurnEntity.TYPE_COLLECTIVE else DirectorTurnEntity.TYPE_NORMAL))
                recordHistory(dao, plan, version, turnId, "ROLE", "participants", "RESTORE",
                    "وضعیت قبلی نقش‌های نوبت بازگردانی شد.",
                    DirectorOfficeHistorySnapshots.encodeParticipants(current), DirectorOfficeHistorySnapshots.encodeParticipants(after), historyId)
            }
            else -> error("این نوع سابقه قابل بازگردانی نیست.")
        }
        dao.updatePlan(plan.copy(updatedAt = System.currentTimeMillis()))
    }

    suspend fun deleteStageCue(dao: DirectorOfficeWorkspaceDao, cueId: Long) {
        val (plan, _) = requireEditableCue(dao, cueId)
        dao.deleteStageCue(cueId)
        dao.updatePlan(plan.copy(updatedAt = System.currentTimeMillis()))
    }
}

