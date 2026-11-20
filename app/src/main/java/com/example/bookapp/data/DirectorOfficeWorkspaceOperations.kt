package com.example.bookapp.data

/**
 * عملیات مشترک دفتر معین‌البکاء برای Admin و Viewer.
 * این لایه فقط روی DAO تحویلی کار می‌کند؛ بنابراین هیچ‌گاه دیتابیس دو برنامه را مخلوط نمی‌کند.
 * انتشار، امضا، مدیریت کاربر و واردکردن بسته رسمی عمداً در این هسته وجود ندارند.
 */
object DirectorOfficeWorkspaceOperations {
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

        // ترتیب ورودی از DAO کتابخانه می‌آید؛ همان ترتیب مرتب‌شده در هر دو Flavor حفظ می‌شود.
        val orderedRoles = roles
        val roleByUid = orderedRoles.associateBy { it.uid }
        val orderedSections = sections.filter { it.content.isNotBlank() }
        val byNumber = linkedMapOf<Int, Long>()
        val byRole = linkedMapOf<String, Long>()
        var insertedCount = 0

        transactionRunner.run {
            if (method != DirectorOfficeSharedContract.MANUAL) {
                dao.turns(versionId).forEach { dao.deleteTurn(it.id) }
            }

            when (method) {
                DirectorOfficeSharedContract.ROLE_NUMBERED -> {
                    data class NumberedEntry(
                        val number: Int,
                        val role: DirectorOfficeBuildRole,
                        val section: DirectorOfficeBuildSection,
                        val text: String
                    )
                    val entries = orderedSections.flatMap { section ->
                        val role = roleByUid[section.roleUid] ?: return@flatMap emptyList()
                        DirectorOfficeWorkspaceUseCases.parseNumberedDialogues(section.content).map { (number, text) ->
                            NumberedEntry(number, role, section, text)
                        }
                    }
                    require(entries.isNotEmpty()) {
                        "در متن نقش‌ها شماره دیالوگ پیدا نشد. شماره هر دیالوگ را در ابتدای خط، مانند «۱- متن دیالوگ»، وارد کنید."
                    }
                    entries.groupBy { it.number }.toSortedMap().forEach { (number, numberEntries) ->
                        val distinctRoles = numberEntries.map { it.role }.distinctBy { it.uid }
                        val title = if (distinctRoles.size > 1) distinctRoles.joinToString("، ") { it.title }
                            else distinctRoles.first().title
                        val turnId = dao.insertTurn(DirectorTurnEntity(
                            versionId = versionId, turnNumber = number, sequenceIndex = insertedCount,
                            turnType = if (distinctRoles.size > 1) DirectorTurnEntity.TYPE_COLLECTIVE else DirectorTurnEntity.TYPE_NORMAL,
                            title = title
                        ))
                        byNumber[number] = turnId
                        distinctRoles.forEachIndexed { index, role ->
                            dao.insertParticipant(DirectorParticipantEntity(
                                turnId = turnId, roleUid = role.uid, roleTitle = role.title,
                                orderIndex = index, isLead = index == 0
                            ))
                        }
                        numberEntries.forEachIndexed { index, entry ->
                            dao.insertTextSegment(DirectorTextSegmentEntity(
                                turnId = turnId, segmentIndex = index, text = entry.text, sectionUid = entry.section.uid
                            ))
                        }
                        insertedCount++
                    }
                }
                DirectorOfficeSharedContract.FULL_TEXT -> {
                    orderedRoles.forEach { role ->
                        val roleSections = orderedSections.filter { it.roleUid == role.uid }
                        if (roleSections.isNotEmpty()) {
                            val turnId = dao.insertTurn(DirectorTurnEntity(
                                versionId = versionId, turnNumber = insertedCount + 1,
                                sequenceIndex = insertedCount, title = role.title
                            ))
                            byRole[role.uid] = turnId
                            dao.insertParticipant(DirectorParticipantEntity(
                                turnId = turnId, roleUid = role.uid, roleTitle = role.title, isLead = true
                            ))
                            roleSections.forEachIndexed { index, section ->
                                dao.insertTextSegment(DirectorTextSegmentEntity(
                                    turnId = turnId, segmentIndex = index, text = section.content, sectionUid = section.uid
                                ))
                            }
                            insertedCount++
                        }
                    }
                    require(byRole.isNotEmpty()) { "متن کاملی برای نقش‌های این مجلس پیدا نشد." }
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
                turnId = turnId, segmentIndex = 0, text = text.trim(), sectionUid = sectionUid
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
        dao.updatePlan(plan.copy(updatedAt = System.currentTimeMillis()))
    }

    suspend fun updateTextSegment(dao: DirectorOfficeWorkspaceDao, value: DirectorTextSegmentEntity) {
        val (plan, _) = requireEditableTurn(dao, value.turnId)
        require(dao.textSegments(value.turnId).any { it.id == value.id }) { "متن دیالوگ متعلق به این نوبت نیست." }
        require(value.text.isNotBlank()) { "متن دیالوگ خالی است." }
        dao.updateTextSegment(value)
        dao.updatePlan(plan.copy(updatedAt = System.currentTimeMillis()))
    }

    suspend fun addDialogueSegment(dao: DirectorOfficeWorkspaceDao, turnId: Long, text: String) {
        require(text.isNotBlank()) { "متن دیالوگ خالی است." }
        val (plan, _) = requireEditableTurn(dao, turnId)
        val nextIndex = (dao.textSegments(turnId).maxOfOrNull { it.segmentIndex } ?: -1) + 1
        dao.insertTextSegment(DirectorTextSegmentEntity(turnId = turnId, segmentIndex = nextIndex, text = text.trim()))
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
        blocksNextTurn: Boolean = cueType == DirectorStageCueEntity.CUE_IMMEDIATE
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
            instruction = instruction.trim(), orderIndex = order, blocksNextTurn = blocksNextTurn
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
        val (plan, _) = requireEditableTurn(dao, turnId)
        val current = dao.textSegments(turnId).sortedBy { it.segmentIndex }
        if (current.isEmpty()) {
            dao.insertTextSegment(DirectorTextSegmentEntity(turnId = turnId, segmentIndex = 0, text = text.trim()))
        } else {
            dao.updateTextSegment(current.first().copy(segmentIndex = 0, text = text.trim()))
            current.drop(1).forEach { dao.deleteTextSegment(it.id) }
        }
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
        val (plan, _) = requireEditableTurn(dao, turnId)
        val current = dao.participants(turnId)
        require(current.none { it.roleUid == roleUid }) { "این نقش قبلاً به نوبت اضافه شده است." }
        dao.insertParticipant(DirectorParticipantEntity(
            turnId = turnId, roleUid = roleUid, roleTitle = roleTitle.trim(),
            orderIndex = (current.maxOfOrNull { it.orderIndex } ?: -1) + 1,
            isLead = isLead || current.isEmpty()
        ))
        val turn = dao.turn(turnId) ?: error("نوبت دفتر پیدا نشد.")
        if (current.isNotEmpty()) dao.updateTurn(turn.copy(turnType = DirectorTurnEntity.TYPE_COLLECTIVE))
        dao.updatePlan(plan.copy(updatedAt = System.currentTimeMillis()))
    }

    suspend fun removeParticipant(dao: DirectorOfficeWorkspaceDao, participantId: Long, turnId: Long) {
        val (plan, _) = requireEditableTurn(dao, turnId)
        val current = dao.participants(turnId)
        val target = current.firstOrNull { it.id == participantId } ?: error("نقش این نوبت پیدا نشد.")
        dao.deleteParticipant(target.id)
        val remaining = dao.participants(turnId)
        if (target.isLead && remaining.isNotEmpty()) {
            dao.updateParticipant(remaining.first().copy(isLead = true))
        }
        val turn = dao.turn(turnId) ?: error("نوبت دفتر پیدا نشد.")
        dao.updateTurn(turn.copy(turnType = if (remaining.size > 1) DirectorTurnEntity.TYPE_COLLECTIVE else DirectorTurnEntity.TYPE_NORMAL))
        dao.updatePlan(plan.copy(updatedAt = System.currentTimeMillis()))
    }

    suspend fun setLeadParticipant(dao: DirectorOfficeWorkspaceDao, turnId: Long, participantId: Long) {
        val (plan, _) = requireEditableTurn(dao, turnId)
        val current = dao.participants(turnId)
        require(current.any { it.id == participantId }) { "نقش انتخاب‌شده متعلق به این نوبت نیست." }
        current.forEach { participant ->
            if (participant.isLead != (participant.id == participantId)) {
                dao.updateParticipant(participant.copy(isLead = participant.id == participantId))
            }
        }
        dao.updatePlan(plan.copy(updatedAt = System.currentTimeMillis()))
    }

    suspend fun deleteTurn(dao: DirectorOfficeWorkspaceDao, turnId: Long) {
        val (plan, _) = requireEditableTurn(dao, turnId)
        dao.deleteTurn(turnId)
        dao.updatePlan(plan.copy(updatedAt = System.currentTimeMillis()))
    }

    suspend fun deleteTextSegment(dao: DirectorOfficeWorkspaceDao, segment: DirectorTextSegmentEntity) {
        val (plan, _) = requireEditableTurn(dao, segment.turnId)
        require(dao.textSegments(segment.turnId).any { it.id == segment.id }) { "متن دیالوگ متعلق به این نوبت نیست." }
        dao.deleteTextSegment(segment.id)
        dao.updatePlan(plan.copy(updatedAt = System.currentTimeMillis()))
    }

    suspend fun deleteStageCue(dao: DirectorOfficeWorkspaceDao, cueId: Long) {
        val (plan, _) = requireEditableCue(dao, cueId)
        dao.deleteStageCue(cueId)
        dao.updatePlan(plan.copy(updatedAt = System.currentTimeMillis()))
    }
}

