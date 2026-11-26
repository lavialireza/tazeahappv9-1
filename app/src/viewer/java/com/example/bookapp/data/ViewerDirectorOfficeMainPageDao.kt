package com.example.bookapp.data

/** اعمال سیاست دسترسی در لایه DAO؛ پنهان‌کردن دکمه به‌تنهایی مجوز نوشتن ایجاد نمی‌کند. */
class ViewerDirectorOfficeMainPageDao(
    private val delegate: ViewerDirectorOfficeDao,
    private val permissions: Map<String, Boolean>
) : DirectorOfficeMainPageDao by delegate {

    private fun requireAny(vararg keys: String) {
        require(keys.any { ViewerPermissionEngine.can(permissions, it) }) {
            "مجوز لازم برای این عملیات دفتر معین‌البکاء فعال نیست."
        }
    }

    private fun requireView() = requireAny("director.personal.view")
    private fun requireCreate() = requireAny("director.personal.create")
    private fun requireEdit() = requireAny("director.personal.edit")
    private fun requireDelete() = requireAny("director.personal.delete")

    private suspend fun requireWritableVersion(versionId: Long): DirectorPlanVersionEntity {
        val version = delegate.version(versionId) ?: error("نسخه دفتر پیدا نشد.")
        val plan = delegate.plan(version.planId) ?: error("دفتر والد نسخه پیدا نشد.")
        require(!plan.isDeleted && plan.status == DirectorPlanEntity.STATUS_DRAFT &&
            version.status == DirectorPlanVersionEntity.STATUS_DRAFT) {
            "فقط نسخه پیش‌نویس دفتر شخصی فعال همین Viewer قابل تغییر است."
        }
        return version
    }

    private suspend fun requireWritableTurn(turnId: Long): DirectorTurnEntity {
        val turn = delegate.turn(turnId) ?: error("نوبت دفتر پیدا نشد.")
        requireWritableVersion(turn.versionId)
        return turn
    }

    override suspend fun plansPage(query: String, limit: Int, offset: Int): List<DirectorPlanEntity> {
        requireView()
        return delegate.plansPage(query, limit, offset)
    }

    override suspend fun plansForTazieh(taziehUid: String): List<DirectorPlanEntity> {
        requireView()
        return delegate.plansForTazieh(taziehUid)
    }

    override suspend fun plan(id: Long): DirectorPlanEntity? { requireView(); return delegate.plan(id) }
    override suspend fun sourceFile(id: Long): DirectorSourceFileEntity? { requireView(); return delegate.sourceFile(id) }
    override suspend fun version(id: Long): DirectorPlanVersionEntity? { requireView(); return delegate.version(id) }
    override suspend fun versionsForPlan(planId: Long): List<DirectorPlanVersionEntity> { requireView(); return delegate.versionsForPlan(planId) }
    override suspend fun versions(planId: Long): List<DirectorPlanVersionEntity> { requireView(); return delegate.versions(planId) }
    override suspend fun sourceFiles(planId: Long): List<DirectorSourceFileEntity> { requireView(); return delegate.sourceFiles(planId) }
    override suspend fun turn(id: Long): DirectorTurnEntity? { requireView(); return delegate.turn(id) }
    override suspend fun turns(versionId: Long): List<DirectorTurnEntity> { requireView(); return delegate.turns(versionId) }
    override suspend fun participants(turnId: Long): List<DirectorParticipantEntity> { requireView(); return delegate.participants(turnId) }
    override suspend fun participantsForTurns(turnIds: List<Long>): List<DirectorParticipantEntity> { requireView(); return delegate.participantsForTurns(turnIds) }
    override suspend fun textSegments(turnId: Long): List<DirectorTextSegmentEntity> { requireView(); return delegate.textSegments(turnId) }
    override suspend fun textSegmentsForTurns(turnIds: List<Long>): List<DirectorTextSegmentEntity> { requireView(); return delegate.textSegmentsForTurns(turnIds) }
    override suspend fun stageCues(versionId: Long): List<DirectorStageCueEntity> { requireView(); return delegate.stageCues(versionId) }
    override suspend fun stageCue(id: Long): DirectorStageCueEntity? { requireView(); return delegate.stageCue(id) }
    override suspend fun changeHistory(planId: Long, turnId: Long?): List<DirectorChangeHistoryEntity> { requireView(); return delegate.changeHistory(planId, turnId) }
    override suspend fun changeHistoryEntry(id: Long): DirectorChangeHistoryEntity? { requireView(); return delegate.changeHistoryEntry(id) }

    override suspend fun insertPlan(value: DirectorPlanEntity): Long {
        requireCreate()
        require(value.uid.isNotBlank() && value.status == DirectorPlanEntity.STATUS_DRAFT && value.title.isNotBlank()) {
            "فقط دفتر شخصی پیش‌نویس با شناسه معتبر قابل ایجاد است."
        }
        return delegate.insertPlan(value)
    }

    override suspend fun insertVersion(value: DirectorPlanVersionEntity): Long {
        requireAny("director.personal.create", "director.personal.edit")
        val plan = delegate.plan(value.planId) ?: error("دفتر والد نسخه پیدا نشد.")
        require(!plan.isDeleted && plan.status == DirectorPlanEntity.STATUS_DRAFT &&
            value.status == DirectorPlanVersionEntity.STATUS_DRAFT && value.versionNumber > 0) {
            "نسخه شخصی باید به دفتر همین Viewer متصل و در وضعیت پیش‌نویس باشد."
        }
        return delegate.insertVersion(value)
    }

    override suspend fun updateVersion(value: DirectorPlanVersionEntity) {
        val current = delegate.version(value.id) ?: error("نسخه دفتر پیدا نشد.")
        requireWritableVersion(value.id)
        val initialBuildNoteOnly = ViewerPermissionEngine.can(permissions, "director.personal.create") &&
            current.versionNumber == 1 && current.status == DirectorPlanVersionEntity.STATUS_DRAFT &&
            value.copy(changeNotes = current.changeNotes) == current
        require(ViewerPermissionEngine.can(permissions, "director.personal.edit") || initialBuildNoteOnly) {
            "برای ویرایش نسخه دفتر، مجوز ویرایش لازم است."
        }
        require(value.planId == current.planId && value.versionNumber == current.versionNumber &&
            value.createdAt == current.createdAt && value.publishedAt == current.publishedAt &&
            value.status == current.status) {
            "شناسه، شماره، تاریخ و وضعیت نسخه از مسیر ویرایش شخصی قابل تغییر نیست."
        }
        delegate.updateVersion(value)
    }

    override suspend fun insertSourceFile(value: DirectorSourceFileEntity): Long {
        requireAny("director.source.import")
        requireAny("director.personal.create", "director.personal.edit")
        val plan = delegate.plan(value.planId) ?: error("دفتر مقصد فایل منبع پیدا نشد.")
        require(!plan.isDeleted && plan.status == DirectorPlanEntity.STATUS_DRAFT &&
            value.originalName.isNotBlank() && value.localPath.isNotBlank()) {
            "فایل منبع باید به دفتر شخصی معتبر همین Viewer متصل باشد."
        }
        return delegate.insertSourceFile(value)
    }

    override suspend fun updatePlan(value: DirectorPlanEntity) {
        val current = delegate.plan(value.id) ?: error("دفتر پیدا نشد.")
        val timestampOnly = value.copy(updatedAt = current.updatedAt) == current
        val createTimestampTouch = timestampOnly &&
            ViewerPermissionEngine.can(permissions, "director.personal.create") &&
            !current.isDeleted && current.status != DirectorPlanEntity.STATUS_ARCHIVED
        require(ViewerPermissionEngine.can(permissions, "director.personal.edit") || createTimestampTouch) {
            "برای ویرایش اطلاعات دفتر، مجوز ویرایش لازم است."
        }
        require(!current.isDeleted && current.status != DirectorPlanEntity.STATUS_ARCHIVED &&
            value.uid == current.uid && value.taziehUid == current.taziehUid &&
            value.createdAt == current.createdAt && value.isDeleted == current.isDeleted &&
            value.deletedAt == current.deletedAt && value.status == current.status &&
            current.uid.isNotBlank()) {
            "شناسه، مجلس، مالکیت و وضعیت دفتر از مسیر ویرایش شخصی قابل تغییر نیست."
        }
        delegate.updatePlan(value)
    }

    override suspend fun softDeletePlan(id: Long, deletedAt: Long) {
        requireDelete()
        val plan = delegate.plan(id) ?: error("دفتر پیدا نشد.")
        require(!plan.isDeleted && plan.status != DirectorPlanEntity.STATUS_ARCHIVED) {
            "فقط دفتر شخصی همین Viewer قابل حذف است."
        }
        delegate.softDeletePlan(id, deletedAt)
    }

    override suspend fun insertTurn(value: DirectorTurnEntity): Long {
        requireAny("director.personal.create", "director.personal.edit")
        requireWritableVersion(value.versionId)
        require(value.turnNumber > 0 && value.title.isNotBlank()) { "شماره و عنوان نوبت معتبر نیست." }
        return delegate.insertTurn(value)
    }

    override suspend fun insertTurns(values: List<DirectorTurnEntity>): List<Long> {
        requireAny("director.personal.create", "director.personal.edit")
        require(values.all { it.turnNumber > 0 && it.title.isNotBlank() }) {
            "شماره و عنوان تمام نوبت‌ها باید معتبر باشد."
        }
        val versionIds = values.map { it.versionId }.distinct()
        require(versionIds.size <= 1) { "ساخت گروهی نوبت‌ها باید فقط برای یک نسخه انجام شود." }
        versionIds.singleOrNull()?.let { versionId ->
            requireWritableVersion(versionId)
            // شماره نوبت شناسه سراسری گفتار است، نه کلید یکتا در نسخه.
            // متن‌های یکسان در عملیات مشترک ادغام می‌شوند؛ متن‌های متفاوت با همان
            // شماره باید جداگانه ذخیره شوند تا کارگردان در دفتر اصلاحشان کند.
        }
        return delegate.insertTurns(values)
    }

    override suspend fun updateTurn(value: DirectorTurnEntity) {
        val current = requireWritableTurn(value.id)
        require(value.versionId == current.versionId && value.sequenceIndex == current.sequenceIndex &&
            value.turnNumber > 0 && value.title.isNotBlank()) {
            "شناسه نسخه، جایگاه، شماره یا عنوان نوبت معتبر نیست."
        }
        // شماره تکراری بین نقش‌ها مجاز است؛ کنترل تکرار همان نقش در منطق مشترک انجام می‌شود.
        val collectiveMarkerOnly = ViewerPermissionEngine.can(permissions, "director.personal.create") &&
            value.copy(turnType = current.turnType) == current &&
            value.turnType == DirectorTurnEntity.TYPE_COLLECTIVE
        require(ViewerPermissionEngine.can(permissions, "director.personal.edit") || collectiveMarkerOnly) {
            "برای ویرایش نوبت دفتر، مجوز ویرایش لازم است."
        }
        delegate.updateTurn(value)
    }

    override suspend fun deleteTurn(id: Long) {
        requireDelete()
        requireWritableTurn(id)
        delegate.deleteTurn(id)
    }

    override suspend fun moveTurn(turnId: Long, sequenceIndex: Int) {
        requireEdit()
        requireWritableTurn(turnId)
        // شاخص منفی فقط برای جابه‌جایی موقت داخل تراکنش استفاده می‌شود تا
        // قید یکتای (versionId, sequenceIndex) هنگام درج میان نوبت‌ها برخورد نکند.
        delegate.moveTurn(turnId, sequenceIndex)
    }

    override suspend fun insertParticipant(value: DirectorParticipantEntity): Long {
        requireAny("director.personal.create", "director.personal.edit")
        requireWritableTurn(value.turnId)
        require(value.roleUid.isNotBlank() && value.roleTitle.isNotBlank()) { "شناسه و عنوان نقش معتبر نیست." }
        require(delegate.participants(value.turnId).none { it.roleUid == value.roleUid }) {
            "این نقش قبلاً به نوبت اضافه شده است."
        }
        return delegate.insertParticipant(value)
    }

    override suspend fun insertParticipants(values: List<DirectorParticipantEntity>): List<Long> {
        requireAny("director.personal.create", "director.personal.edit")
        require(values.all { it.roleUid.isNotBlank() && it.roleTitle.isNotBlank() }) {
            "شناسه و عنوان تمام نقش‌ها باید معتبر باشد."
        }
        val turns = values.map { it.turnId }.distinct().mapNotNull { delegate.turn(it) }
        require(turns.size == values.map { it.turnId }.distinct().size) { "یکی از نوبت‌های مقصد پیدا نشد." }
        val versionIds = turns.map { it.versionId }.distinct()
        require(versionIds.size <= 1) { "ثبت گروهی نقش‌ها باید فقط در یک نسخه انجام شود." }
        versionIds.singleOrNull()?.let { requireWritableVersion(it) }
        values.groupBy { it.turnId }.forEach { (turnId, rows) ->
            val existingRoles = delegate.participants(turnId).map { it.roleUid }.toSet()
            val incomingRoles = rows.map { it.roleUid }
            require(incomingRoles.distinct().size == incomingRoles.size &&
                incomingRoles.none { it in existingRoles }) {
                "یک نقش در این نوبت بیش از یک بار ثبت شده است."
            }
        }
        return delegate.insertParticipants(values)
    }

    override suspend fun updateParticipant(value: DirectorParticipantEntity) {
        requireEdit()
        requireWritableTurn(value.turnId)
        val current = delegate.participants(value.turnId).firstOrNull { it.id == value.id }
            ?: error("نقش متعلق به این نوبت پیدا نشد.")
        require(value.roleUid.isNotBlank() && value.roleTitle.isNotBlank() &&
            delegate.participants(value.turnId).none { it.id != value.id && it.roleUid == value.roleUid }) {
            "اطلاعات نقش معتبر نیست یا نقش تکراری است."
        }
        delegate.updateParticipant(value)
    }

    override suspend fun deleteParticipant(id: Long) {
        requireDelete()
        val participant = delegate.participantById(id) ?: error("نقش دفتر پیدا نشد.")
        requireWritableTurn(participant.turnId)
        delegate.deleteParticipant(id)
    }

    override suspend fun insertTextSegment(value: DirectorTextSegmentEntity): Long {
        requireAny("director.personal.create", "director.personal.edit")
        requireWritableTurn(value.turnId)
        require(value.text.isNotBlank() && value.segmentIndex >= 0) { "متن و ترتیب قطعه دیالوگ معتبر نیست." }
        require(delegate.textSegments(value.turnId).none { it.segmentIndex == value.segmentIndex }) {
            "شماره ترتیب این قطعه متن قبلاً استفاده شده است."
        }
        return delegate.insertTextSegment(value)
    }

    override suspend fun insertTextSegments(values: List<DirectorTextSegmentEntity>): List<Long> {
        requireAny("director.personal.create", "director.personal.edit")
        require(values.all { it.text.isNotBlank() && it.segmentIndex >= 0 }) {
            "متن و ترتیب تمام قطعه‌های دیالوگ باید معتبر باشد."
        }
        val turns = values.map { it.turnId }.distinct().mapNotNull { delegate.turn(it) }
        require(turns.size == values.map { it.turnId }.distinct().size) { "یکی از نوبت‌های مقصد پیدا نشد." }
        val versionIds = turns.map { it.versionId }.distinct()
        require(versionIds.size <= 1) { "ثبت گروهی متن باید فقط در یک نسخه انجام شود." }
        versionIds.singleOrNull()?.let { requireWritableVersion(it) }
        values.groupBy { it.turnId }.forEach { (turnId, rows) ->
            val existingIndexes = delegate.textSegments(turnId).map { it.segmentIndex }.toSet()
            val incomingIndexes = rows.map { it.segmentIndex }
            require(incomingIndexes.distinct().size == incomingIndexes.size &&
                incomingIndexes.none { it in existingIndexes }) {
                "شماره ترتیب قطعه‌های متن تکراری است."
            }
        }
        return delegate.insertTextSegments(values)
    }

    override suspend fun updateTextSegment(value: DirectorTextSegmentEntity) {
        requireEdit()
        requireWritableTurn(value.turnId)
        val current = delegate.textSegments(value.turnId).firstOrNull { it.id == value.id }
            ?: error("متن متعلق به این نوبت پیدا نشد.")
        require(value.text.isNotBlank() && value.segmentIndex >= 0 &&
            delegate.textSegments(value.turnId).none { it.id != value.id && it.segmentIndex == value.segmentIndex }) {
            "متن یا ترتیب قطعه معتبر نیست."
        }
        delegate.updateTextSegment(value)
    }

    override suspend fun deleteTextSegment(id: Long) {
        requireDelete()
        val segment = delegate.textSegmentById(id) ?: error("قطعه متن دفتر پیدا نشد.")
        requireWritableTurn(segment.turnId)
        delegate.deleteTextSegment(id)
    }

    override suspend fun insertStageCue(value: DirectorStageCueEntity): Long {
        requireAny("director.personal.create", "director.personal.edit")
        requireWritableVersion(value.versionId)
        require(value.title.isNotBlank() && value.instruction.isNotBlank() &&
            (value.turnId == null || delegate.turn(value.turnId)?.versionId == value.versionId)) {
            "راهنمای صحنه باید اطلاعات معتبر و ارتباط با نوبت همین نسخه داشته باشد."
        }
        return delegate.insertStageCue(value)
    }

    override suspend fun updateStageCue(value: DirectorStageCueEntity) {
        requireEdit()
        val current = delegate.stageCue(value.id) ?: error("راهنمای صحنه پیدا نشد.")
        requireWritableVersion(current.versionId)
        require(value.versionId == current.versionId && value.title.isNotBlank() && value.instruction.isNotBlank() &&
            (value.turnId == null || delegate.turn(value.turnId)?.versionId == value.versionId)) {
            "راهنمای صحنه باید به نسخه و نوبت معتبر متصل باشد."
        }
        delegate.updateStageCue(value)
    }

    override suspend fun deleteStageCue(id: Long) {
        requireDelete()
        val cue = delegate.stageCue(id) ?: error("راهنمای صحنه پیدا نشد.")
        requireWritableVersion(cue.versionId)
        delegate.deleteStageCue(id)
    }

    override suspend fun insertChangeHistory(value: DirectorChangeHistoryEntity): Long {
        requireAny("director.personal.create", "director.personal.edit")
        val version = delegate.version(value.versionId) ?: error("نسخه تاریخچه پیدا نشد.")
        val plan = delegate.plan(value.planId) ?: error("دفتر تاریخچه پیدا نشد.")
        require(version.planId == plan.id && version.status == DirectorPlanVersionEntity.STATUS_DRAFT &&
            !plan.isDeleted && plan.status == DirectorPlanEntity.STATUS_DRAFT &&
            (value.turnId == null || delegate.turn(value.turnId)?.versionId == version.id)) {
            "تاریخچه باید به دفتر، نسخه و نوبت معتبر همین Viewer متصل باشد."
        }
        return delegate.insertChangeHistory(value)
    }
}
