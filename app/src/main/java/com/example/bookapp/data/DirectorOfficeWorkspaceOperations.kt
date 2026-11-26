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

    private suspend fun sharedTurnText(dao: DirectorOfficeWorkspaceDao, turnId: Long): String =
        dao.textSegments(turnId).sortedWith(compareBy<DirectorTextSegmentEntity> { it.segmentIndex }.thenBy { it.id })
            .joinToString("\\n") { it.text }


    /**
     * ساخت مشترک دفتر بر پایه کتابخانه نقش‌ها و بخش‌ها. الگوریتم شماره‌محور و متن کامل
     * در Admin و Viewer دقیقاً یکی است؛ DAO تعیین می‌کند داده در کدام دیتابیس مستقل ذخیره شود.
     * MANUAL اطلاعات موجود را پاک نمی‌کند؛ SELECTOR بخش‌های اولویت‌دار معین‌البکاء را
     * با استخراج شماره‌های موجود می‌سازد و متن‌های بدون شماره را برای انتخاب دستی باقی می‌گذارد.
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
        require(roles.isNotEmpty() || method == DirectorOfficeSharedContract.MANUAL) {
            "برای ساخت دفتر، دست‌کم یک نقش معتبر انتخاب کنید."
        }
        require(roles.all { it.uid.isNotBlank() && it.title.isNotBlank() }) {
            "شناسه و عنوان تمام نقش‌های انتخاب‌شده باید معتبر باشد."
        }
        require(roles.map { it.uid }.distinct().size == roles.size) { "شناسه نقش‌ها تکراری است." }
        require(sections.all { it.uid.isNotBlank() && it.roleUid.isNotBlank() }) {
            "شناسه بخش یا ارتباط آن با نقش ناقص است."
        }
        require(sections.all { it.content.isNotBlank() }) {
            "یکی از بخش‌های انتخاب‌شده متن ندارد؛ بخش خالی را حذف یا متن منبع را اصلاح کنید."
        }
        require(sections.map { it.uid }.distinct().size == sections.size) {
            "یک بخش از کتابخانه بیش از یک بار به ساخت دفتر معرفی شده است."
        }
        require(sections.all { section -> roles.any { it.uid == section.roleUid } }) {
            "بخشی به نقشی خارج از مجلس انتخاب‌شده متصل است."
        }

        val orderedRoles = roles
        val roleByUid = orderedRoles.associateBy { it.uid }
        val orderedSections = sections.filter { it.content.isNotBlank() }
        // هش متن کامل هر بخش فقط یک‌بار محاسبه می‌شود. قبلاً برای تک‌تک دیالوگ‌های
        // یک بخش، کل متن بلند همان بخش دوباره join و SHA-256 می‌شد و ساخت را کند می‌کرد.
        val sectionHashByUid = if (method in setOf(DirectorOfficeSharedContract.ROLE_NUMBERED, DirectorOfficeSharedContract.SELECTOR)) {
            orderedSections.associate { it.uid to ContentUid.sha256(it.content) }
        } else emptyMap()
        val byNumber = linkedMapOf<Int, Long>()
        val byRole = linkedMapOf<String, Long>()
        val buildWarnings = mutableListOf<String>()
        var insertedCount = 0

        data class NumberedEntry(
            val number: Int,
            val role: DirectorOfficeBuildRole,
            val section: DirectorOfficeBuildSection,
            val text: String
        )

        // تمام داده‌های قابل پردازش پیش از پاک‌سازی نسخه آماده می‌شوند.
        val numberedEntries = if (method in setOf(DirectorOfficeSharedContract.ROLE_NUMBERED, DirectorOfficeSharedContract.SELECTOR)) {
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

        if (method in setOf(DirectorOfficeSharedContract.ROLE_NUMBERED, DirectorOfficeSharedContract.SELECTOR) && numberedEntries.isNotEmpty()) {
            require(numberedEntries.all { it.number > 0 }) {
                "شماره نوبت باید بزرگ‌تر از صفر باشد؛ هیچ تغییری در دفتر ذخیره نشد."
            }
            require(numberedEntries.all { it.text.isNotBlank() }) {
                "برای یکی از شماره‌های نوبت، متن وارد نشده است؛ هیچ تغییری در دفتر ذخیره نشد."
            }
            // شماره در یک نقش باید یکتا باشد؛ تکرار بین بخش‌های مختلف همان نقش نیز خطاست.
            val duplicatesInsideRole = numberedEntries
                .groupBy { it.role.uid to it.number }
                .filterValues { it.size > 1 }
            if (duplicatesInsideRole.isNotEmpty()) {
                val details = duplicatesInsideRole.entries.joinToString("؛ ") { (key, entries) ->
                    val roleTitle = entries.first().role.title
                    "نقش «$roleTitle»، شماره ${key.second} (${entries.size} بار)"
                }
                error("شماره نوبت در یک نقش تکرار شده است: $details. ابتدا متن منبع را اصلاح کنید؛ هیچ تغییری در دفتر ذخیره نشد.")
            }

            // اختلاف متن بین نقش‌های هم‌شماره مانع ساخت نیست: هر نقش به‌صورت
            // نوبت مستقل با همان شماره ذخیره می‌شود و هشدار پایدار برای اصلاح ثبت می‌گردد.
            val mismatchedTurns = numberedEntries.groupBy { it.number }
                .filterValues { entries -> entries.map { DirectorOfficeWorkspaceUseCases.normalizeDialogueText(it.text) }.distinct().size > 1 }
            mismatchedTurns.toSortedMap().forEach { (number, entries) ->
                val rolesText = entries.map { it.role.title }.distinct().joinToString("، ")
                buildWarnings += "هشدار شماره ${DirectorOfficeWorkspaceUseCases.formatPersianNumber(number)}: متن این شماره در نقش‌های $rolesText یکسان نیست؛ نوبت‌ها جداگانه ثبت شده‌اند و باید در دفتر بررسی و اصلاح شوند."
            }

            val sortedNumbers = numberedEntries.map { it.number }.distinct().sorted()
            val missing = if (sortedNumbers.isEmpty()) emptyList() else
                (1 until sortedNumbers.last()).filterNot { it in sortedNumbers }
            if (missing.isNotEmpty()) {
                buildWarnings += "شماره‌های جاافتاده در توالی نوبت‌ها: ${missing.joinToString("، ") { DirectorOfficeWorkspaceUseCases.formatPersianNumber(it) }}. این مورد هشدار است و مانع ساخت دفتر نمی‌شود."
            }
        }

        transactionRunner.run {
            val existingTurns = dao.turns(versionId)

            // اگر روش انتخابی در متن فعلی چیزی پیدا نکند، دفتر تازه به‌صورت پیش‌نویس خالی
            // ذخیره می‌شود؛ اما هنگام بازسازی، اطلاعات قبلی هرگز بی‌صدا پاک نمی‌شوند.
            if (method in setOf(DirectorOfficeSharedContract.ROLE_NUMBERED, DirectorOfficeSharedContract.SELECTOR) && numberedEntries.isEmpty() && existingTurns.isNotEmpty()) {
                error("در متن این مجلس نوبت شماره‌دار پیدا نشد؛ نوبت‌های قبلی حفظ شدند و تغییری در دفتر اعمال نشد.")
            }
            if (method == DirectorOfficeSharedContract.FULL_TEXT && fullTextRoleGroups.isEmpty() && existingTurns.isNotEmpty()) {
                error("متن قابل استفاده‌ای برای ساخت نسخه جدید پیدا نشد؛ نوبت‌های قبلی حفظ شدند.")
            }

            if (method != DirectorOfficeSharedContract.MANUAL) {
                existingTurns.forEach { dao.deleteTurn(it.id) }
            }

            when (method) {
                DirectorOfficeSharedContract.ROLE_NUMBERED, DirectorOfficeSharedContract.SELECTOR -> {
                    if (numberedEntries.isNotEmpty()) {
                        data class PreparedNumberedTurn(
                            val number: Int,
                            val entries: List<NumberedEntry>,
                            val isCollective: Boolean,
                            val warning: String? = null
                        )

                        val preparedTurns = numberedEntries.groupBy { it.number }.toSortedMap().flatMap { (number, entries) ->
                            val distinctTexts = entries.map { DirectorOfficeWorkspaceUseCases.normalizeDialogueText(it.text) }.distinct()
                            if (distinctTexts.size == 1) {
                                listOf(PreparedNumberedTurn(number, entries, entries.map { it.role.uid }.distinct().size > 1))
                            } else {
                                // متن‌های متفاوت با شماره یکسان را در یک کارت ادغام نمی‌کنیم.
                                // برای هر نقش یک ردیف مستقل با همان شماره می‌سازیم تا متن‌ها حفظ شوند.
                                entries.map { entry ->
                                    PreparedNumberedTurn(
                                        number = number,
                                        entries = listOf(entry),
                                        isCollective = false,
                                        warning = "هشدار: متن شماره ${DirectorOfficeWorkspaceUseCases.formatPersianNumber(number)} با متن همین شماره در نقش‌های دیگر یکسان نیست؛ نیازمند بررسی و اصلاح کارگردان است."
                                    )
                                }
                            }
                        }

                        val turnRows = preparedTurns.mapIndexed { sequence, prepared ->
                            val distinctRoles = prepared.entries.map { it.role }.distinctBy { it.uid }
                            DirectorTurnEntity(
                                versionId = versionId,
                                turnNumber = prepared.number,
                                sequenceIndex = sequence,
                                turnType = if (prepared.isCollective) DirectorTurnEntity.TYPE_COLLECTIVE else DirectorTurnEntity.TYPE_NORMAL,
                                title = if (prepared.isCollective) distinctRoles.joinToString("، ") { it.title } else distinctRoles.first().title,
                                notes = prepared.warning.orEmpty()
                            )
                        }
                        val turnIds = dao.insertTurns(turnRows)
                        val participantRows = mutableListOf<DirectorParticipantEntity>()
                        val segmentRows = mutableListOf<DirectorTextSegmentEntity>()
                        preparedTurns.forEachIndexed { index, prepared ->
                            val turnId = turnIds[index]
                            if (prepared.number !in byNumber) byNumber[prepared.number] = turnId
                            prepared.entries.map { it.role }.distinctBy { it.uid }.forEachIndexed { roleIndex, role ->
                                participantRows += DirectorParticipantEntity(
                                    turnId = turnId, roleUid = role.uid, roleTitle = role.title,
                                    orderIndex = roleIndex, isLead = roleIndex == 0
                                )
                            }
                            // در نوبت جمعی متن یک‌بار ذخیره می‌شود؛ در تعارض هر متن در نوبت مستقل خودش می‌ماند.
                            val firstEntry = prepared.entries.first()
                            val sourceSections = prepared.entries.map { it.section }.distinctBy { it.uid }
                            val sourceRoles = prepared.entries.map { it.role }.distinctBy { it.uid }
                            segmentRows += DirectorTextSegmentEntity(
                                turnId = turnId,
                                segmentIndex = 0,
                                text = firstEntry.text.trim(),
                                sectionUid = sourceSections.singleOrNull()?.uid,
                                sourceTaziehUid = plan.taziehUid,
                                sourceRoleUid = sourceRoles.singleOrNull()?.uid,
                                sourceSectionTitle = sourceSections.map { it.title }.filter { it.isNotBlank() }.distinct().joinToString("، "),
                                sourceSectionUids = sourceSections.joinToString("\u001F") { it.uid },
                                sourceMethod = method,
                                sourceContentHash = if (sourceSections.size == 1) {
                                    sectionHashByUid[sourceSections.single().uid].orEmpty()
                                } else ContentUid.sha256(sourceSections.joinToString("\n") { it.content })
                            )
                        }
                        if (participantRows.isNotEmpty()) dao.insertParticipants(participantRows)
                        if (segmentRows.isNotEmpty()) dao.insertTextSegments(segmentRows)
                        insertedCount = preparedTurns.size
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
                DirectorOfficeSharedContract.MANUAL -> Unit
            }
            // به‌روزرسانی صرفاً زمانی دفتر برای ساخت اولیه لازم نیست؛ حذف آن اجازه می‌دهد
            // مجوز create بدون اعطای ناخواسته مجوز edit برای تکمیل اولیه استفاده شود.
        }
        return DirectorOfficeBuildResult(byNumber.toMap(), byRole.toMap(), insertedCount, buildWarnings.toList())
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
        transactionRunner: DirectorOfficeTransactionRunner = DirectDirectorOfficeTransactionRunner
    ) {
        require(turnNumber > 0 && roleUid.isNotBlank() && roleTitle.isNotBlank() && text.isNotBlank()) {
            "اطلاعات نقش یا نوبت معتبر نیست."
        }
        val version = dao.version(versionId) ?: error("نسخه دفتر پیدا نشد.")
        val plan = dao.plan(version.planId) ?: error("دفتر پیدا نشد.")
        require(version.status == DirectorPlanVersionEntity.STATUS_DRAFT) { "نسخه منتشرشده قابل ویرایش نیست." }
        require(plan.status != DirectorPlanEntity.STATUS_ARCHIVED) { "دفتر بایگانی‌شده قابل ویرایش نیست." }
        transactionRunner.run {
            val existingTurns = dao.turns(versionId)
            val sameNumberTurns = existingTurns.filter { it.turnNumber == turnNumber }
            val normalizedIncoming = DirectorOfficeWorkspaceUseCases.normalizeDialogueText(text)
            val matchingTurn = sameNumberTurns.firstOrNull { turn ->
                DirectorOfficeWorkspaceUseCases.normalizeDialogueText(sharedTurnText(dao, turn.id)) == normalizedIncoming
            }
            require(sameNumberTurns.none { turn -> dao.participants(turn.id).any { it.roleUid == roleUid.trim() } }) {
                "این نقش قبلاً برای شماره ${DirectorOfficeWorkspaceUseCases.formatPersianNumber(turnNumber)} ثبت شده است."
            }
            if (matchingTurn != null) {
                addParticipant(dao, matchingTurn.id, roleUid.trim(), roleTitle.trim(), false)
                return@run
            }
            val nextIndex = DirectorOfficeWorkspaceUseCases.nextSequenceIndex(existingTurns)
            val conflictNote = if (sameNumberTurns.isNotEmpty())
                "هشدار: متن این نوبت با متن شماره ${DirectorOfficeWorkspaceUseCases.formatPersianNumber(turnNumber)} در نقش‌های دیگر یکسان نیست؛ این متن جداگانه حفظ شده و باید توسط کارگردان بررسی و اصلاح شود."
            else ""
            val turnId = dao.insertTurn(DirectorTurnEntity(
                versionId = versionId, turnNumber = turnNumber, sequenceIndex = nextIndex,
                title = roleTitle.trim(), notes = conflictNote
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
        val sameNumberTurns = dao.turns(current.versionId).filter { it.id != current.id && it.turnNumber == value.turnNumber }
        val currentRoleUids = dao.participants(current.id).map { it.roleUid }.toSet()
        var repeatedRole: DirectorParticipantEntity? = null
        for (otherTurn in sameNumberTurns) {
            repeatedRole = dao.participants(otherTurn.id).firstOrNull { it.roleUid in currentRoleUids }
            if (repeatedRole != null) break
        }
        // امکان ثبت موقت شماره اصلاحی حتی در صورت برخورد با شماره‌ای دیگر لازم است؛
        // معین‌البکاء می‌تواند نوبت‌های متعارض را یکی‌یکی اصلاح کند. کنترل کیفیت
        // همچنان تکرار همان نقش/شماره را به‌عنوان خطای قابل پیگیری گزارش می‌کند.
        val currentText = DirectorOfficeWorkspaceUseCases.normalizeDialogueText(sharedTurnText(dao, current.id))
        val hasTextConflict = sameNumberTurns.any { other ->
            DirectorOfficeWorkspaceUseCases.normalizeDialogueText(sharedTurnText(dao, other.id)) != currentText
        }
        val warning = when {
            repeatedRole != null -> "هشدار: شماره ${DirectorOfficeWorkspaceUseCases.formatPersianNumber(value.turnNumber)} برای نقش «${repeatedRole?.roleTitle.orEmpty()}» نیز ثبت شده است؛ شماره‌ها را بازبینی و اصلاح کنید."
            hasTextConflict -> "هشدار: متن این نوبت با متن شماره ${DirectorOfficeWorkspaceUseCases.formatPersianNumber(value.turnNumber)} در نقش‌های دیگر یکسان نیست؛ این متن جداگانه حفظ شده و باید توسط کارگردان بررسی و اصلاح شود."
            else -> value.notes
        }
        dao.updateTurn(value.copy(notes = warning))
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
        require((entry.sourceTextStartOffset == null && entry.sourceTextEndOffset == null) ||
            (entry.sourceTextStartOffset != null && entry.sourceTextEndOffset != null &&
                entry.sourceTextStartOffset >= 0 && entry.sourceTextEndOffset > entry.sourceTextStartOffset)) {
            "بازه انتخاب‌شده از متن معتبر نیست."
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
        val sameNumberTurns = currentTurns.filter { it.turnNumber == nextNumber }
        val normalizedIncoming = DirectorOfficeWorkspaceUseCases.normalizeDialogueText(entry.text)
        val matchingTurn = sameNumberTurns.firstOrNull { turn ->
            DirectorOfficeWorkspaceUseCases.normalizeDialogueText(sharedTurnText(dao, turn.id)) == normalizedIncoming
        }
        require(sameNumberTurns.none { turn -> dao.participants(turn.id).any { it.roleUid == entry.roleUid.trim() } }) {
            "این نقش قبلاً برای شماره ${DirectorOfficeWorkspaceUseCases.formatPersianNumber(nextNumber)} ثبت شده است."
        }
        if (matchingTurn != null) {
            addParticipant(dao, matchingTurn.id, entry.roleUid.trim(), entry.roleTitle.trim(), false)
            // متن نوبت جمعی فقط یک‌بار ذخیره می‌شود. برای انتخاب بازه‌ای، قطعه خالی
            // صرفاً منشأ و offset نقش دوم را نگه می‌دارد؛ برای ورودی قدیمیِ کامل، پیوندها ادغام می‌شوند.
            val existingSegments = dao.textSegments(matchingTurn.id)
            if (entry.sourceTextStartOffset != null && entry.sourceTextEndOffset != null) {
                dao.insertTextSegment(DirectorTextSegmentEntity(
                    turnId = matchingTurn.id,
                    segmentIndex = (existingSegments.maxOfOrNull { it.segmentIndex } ?: -1) + 1,
                    text = "",
                    sectionUid = entry.sectionUid,
                    sourceTaziehUid = plan.taziehUid,
                    sourceRoleUid = entry.roleUid.trim(),
                    sourceSectionTitle = entry.sectionTitle,
                    sourceSectionUids = entry.sectionUid,
                    sourceMethod = DirectorOfficeSharedContract.SELECTOR,
                    sourceContentHash = entry.sourceContentHash,
                    sourcePageStart = entry.sourceTextStartOffset,
                    sourcePageEnd = entry.sourceTextEndOffset
                ))
            } else {
                existingSegments.firstOrNull()?.let { first ->
                    val linkedSources = (first.sourceSectionUids.ifBlank { first.sectionUid.orEmpty() }
                        .split('\u001F') + entry.sectionUid).filter { it.isNotBlank() }.distinct().joinToString("\u001F")
                    dao.updateTextSegment(first.copy(sourceSectionUids = linkedSources))
                }
            }
            return
        }
        val title = entry.roleTitle.trim()
        val conflictNote = if (sameNumberTurns.isNotEmpty())
            "هشدار: متن این نوبت با متن شماره ${DirectorOfficeWorkspaceUseCases.formatPersianNumber(nextNumber)} در نقش‌های دیگر یکسان نیست؛ این متن جداگانه حفظ شده و باید توسط کارگردان بررسی و اصلاح شود."
        else ""
        val turnId = dao.insertTurn(DirectorTurnEntity(
            versionId = version.id,
            turnNumber = nextNumber,
            sequenceIndex = nextSequence,
            title = title,
            notes = conflictNote,
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
            sourceContentHash = entry.sourceContentHash.ifBlank { ContentUid.sha256(entry.text.trim()) },
            sourcePageStart = entry.sourceTextStartOffset,
            sourcePageEnd = entry.sourceTextEndOffset
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
        val existingTurns = dao.turns(version.id)
        val sameNumberTurns = existingTurns.filter { it.turnNumber == turnNumber }
        val normalizedIncoming = DirectorOfficeWorkspaceUseCases.normalizeDialogueText(text)
        val matchingTurn = sameNumberTurns.firstOrNull { turn ->
            DirectorOfficeWorkspaceUseCases.normalizeDialogueText(sharedTurnText(dao, turn.id)) == normalizedIncoming
        }
        require(sameNumberTurns.none { turn -> dao.participants(turn.id).any {
            DirectorOfficeWorkspaceUseCases.normalizeDialogueText(it.roleTitle) ==
                DirectorOfficeWorkspaceUseCases.normalizeDialogueText(title)
        } }) { "این نقش قبلاً برای شماره $turnNumber ثبت شده است." }
        if (matchingTurn != null) {
            addParticipant(
                dao, matchingTurn.id,
                "personal-role-" + java.util.UUID.randomUUID(), title.trim(), false
            )
            return
        }
        val nextIndex = DirectorOfficeWorkspaceUseCases.nextSequenceIndex(existingTurns)
        val conflictNote = if (sameNumberTurns.isNotEmpty())
            "هشدار: متن این نوبت با متن شماره ${DirectorOfficeWorkspaceUseCases.formatPersianNumber(turnNumber)} در نقش‌های دیگر یکسان نیست؛ این متن جداگانه حفظ شده و باید توسط کارگردان بررسی و اصلاح شود."
        else ""
        val turnId = dao.insertTurn(DirectorTurnEntity(versionId = version.id, turnNumber = turnNumber, sequenceIndex = nextIndex, title = title.trim(), notes = conflictNote))
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
            val originalHash = if (current.size == 1 || first.sourceMethod == DirectorOfficeSharedContract.SELECTOR)
                first.sourceContentHash else ""
            dao.updateTextSegment(first.copy(
                segmentIndex = 0,
                text = text.trim(),
                sourceTaziehUid = current.firstNotNullOfOrNull { it.sourceTaziehUid },
                sourceRoleUid = sourceRoles.singleOrNull(),
                sourceSectionTitle = sourceTitles,
                sourceSectionUids = sourceUids,
                sourceMethod = if (current.all { it.sourceMethod == DirectorOfficeSharedContract.SELECTOR })
                    DirectorOfficeSharedContract.SELECTOR
                else if (current.size == 1) first.sourceMethod else "MERGED_EDIT",
                sourceContentHash = originalHash
            ))
            current.drop(1)
                .filterNot { it.text.isBlank() && it.sourceMethod == DirectorOfficeSharedContract.SELECTOR && it.sourcePageStart != null && it.sourcePageEnd != null }
                .forEach { dao.deleteTextSegment(it.id) }
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


    /**
     * درج واقعی یک نوبت در شماره میانی: همه شماره‌های >= شماره مقصد یک واحد افزایش
     * می‌یابند و نوبت تازه در همان جایگاه توالی قرار می‌گیرد. شناسه‌ها و پیوندهای نوبت‌های
     * موجود دست‌نخورده می‌مانند.
     */
    suspend fun insertTurnAtNumber(
        dao: DirectorOfficeWorkspaceDao,
        planId: Long,
        versionId: Long,
        turnNumber: Int,
        roleUid: String,
        roleTitle: String,
        text: String,
        selectorEntry: DirectorOfficeSelectorEntry? = null,
        transactionRunner: DirectorOfficeTransactionRunner = DirectDirectorOfficeTransactionRunner
    ) {
        require(turnNumber > 0 && roleUid.isNotBlank() && roleTitle.isNotBlank() && text.isNotBlank()) {
            "شماره، نقش و متن نوبت باید معتبر باشند."
        }
        val plan = dao.plan(planId) ?: error("دفتر پیدا نشد.")
        val version = dao.version(versionId) ?: error("نسخه دفتر پیدا نشد.")
        require(version.planId == planId) { "نسخه انتخاب‌شده متعلق به این دفتر نیست." }
        DirectorOfficeWorkspaceUseCases.requireDraft(plan, version)
        val current = dao.turns(versionId).sortedWith(compareBy<DirectorTurnEntity> { it.sequenceIndex }.thenBy { it.id })
        // در درج میانی، نوبت موجودِ همین شماره نیز به شماره بعد منتقل می‌شود؛
        // بنابراین وجود همان نقش در شماره مقصد نباید مانع درج شود.
        require(selectorEntry == null || (
            selectorEntry.roleUid == roleUid && selectorEntry.roleTitle == roleTitle &&
                selectorEntry.text.trim() == text.trim()
        )) { "اطلاعات متن انتخاب‌شده با نقش یا متن نوبت هماهنگ نیست." }

        transactionRunner.run {
            val insertionIndex = current.indexOfFirst { it.turnNumber >= turnNumber }
                .let { if (it < 0) current.size else it }

            // ابتدا شماره‌ها به‌روزرسانی می‌شوند؛ شماره در دیتابیس یکتا نیست و
            // بنابراین این تغییر هیچ تعارضی با نوبت‌های جمعی/هم‌شماره ایجاد نمی‌کند.
            current.filter { it.turnNumber >= turnNumber }
                .sortedByDescending { it.turnNumber }
                .forEach { dao.updateTurn(it.copy(turnNumber = it.turnNumber + 1)) }

            val maxSequence = current.maxOfOrNull { it.sequenceIndex } ?: -1
            val newTurnId = dao.insertTurn(DirectorTurnEntity(
                versionId = versionId,
                turnNumber = turnNumber,
                sequenceIndex = maxSequence + 1,
                title = roleTitle.trim(),
                continuityGroupUid = selectorEntry?.key.orEmpty()
            ))
            dao.insertParticipant(DirectorParticipantEntity(
                turnId = newTurnId,
                roleUid = roleUid.trim(),
                roleTitle = roleTitle.trim(),
                isLead = true
            ))
            if (selectorEntry != null) {
                dao.insertTextSegment(DirectorTextSegmentEntity(
                    turnId = newTurnId, segmentIndex = 0, text = text.trim(),
                    sectionUid = selectorEntry.sectionUid,
                    sourceTaziehUid = plan.taziehUid,
                    sourceRoleUid = selectorEntry.roleUid,
                    sourceSectionTitle = selectorEntry.sectionTitle,
                    sourceSectionUids = selectorEntry.sectionUid,
                    sourceMethod = DirectorOfficeSharedContract.SELECTOR,
                    sourceContentHash = selectorEntry.sourceContentHash,
                    sourcePageStart = selectorEntry.sourceTextStartOffset,
                    sourcePageEnd = selectorEntry.sourceTextEndOffset
                ))
            } else {
                dao.insertTextSegment(DirectorTextSegmentEntity(
                    turnId = newTurnId, segmentIndex = 0, text = text.trim(),
                    sectionUid = null
                ))
            }

            val ordered = current.toMutableList().apply {
                add(insertionIndex, DirectorTurnEntity(
                    id = newTurnId, versionId = versionId, turnNumber = turnNumber,
                    sequenceIndex = maxSequence + 1, title = roleTitle.trim()
                ))
            }
            ordered.forEachIndexed { index, turn -> dao.moveTurn(turn.id, -200000 - index) }
            ordered.forEachIndexed { index, turn -> dao.moveTurn(turn.id, index) }
            dao.updatePlan(plan.copy(updatedAt = System.currentTimeMillis()))
        }
    }

    suspend fun addParticipant(dao: DirectorOfficeWorkspaceDao, turnId: Long, roleUid: String, roleTitle: String, isLead: Boolean = false) {
        require(roleUid.isNotBlank() && roleTitle.isNotBlank()) { "نقش معتبر نیست." }
        val (plan, version) = requireEditableTurn(dao, turnId)
        val current = dao.participants(turnId)
        require(current.none { it.roleUid == roleUid }) { "این نقش قبلاً به نوبت اضافه شده است." }
        val beforeSnapshot = DirectorOfficeHistorySnapshots.encodeParticipants(current)
        if (isLead) {
            current.filter { it.isLead }.forEach { dao.updateParticipant(it.copy(isLead = false)) }
        }
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
        if (value.isLead) {
            current.filter { it.id != value.id && it.isLead }
                .forEach { dao.updateParticipant(it.copy(isLead = false)) }
        } else if (old.isLead && current.size > 1 && current.none { it.id != value.id && it.isLead }) {
            val replacement = current.first { it.id != value.id }
            dao.updateParticipant(replacement.copy(isLead = true))
        }
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

