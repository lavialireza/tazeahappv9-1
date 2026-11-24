package com.example.bookapp.data

import java.net.URLDecoder
import java.net.URLEncoder

/** Stable, versioned snapshots used by the local audit trail and its restore operation. */
object DirectorOfficeHistorySnapshots {
    private fun enc(value: String?): String = URLEncoder.encode(value.orEmpty(), "UTF-8")
    private fun dec(value: String): String = URLDecoder.decode(value, "UTF-8")

    fun encodeSegments(values: List<DirectorTextSegmentEntity>): String = buildString {
        append("v1\n")
        values.sortedWith(compareBy<DirectorTextSegmentEntity> { it.segmentIndex }.thenBy { it.id })
            .forEach { segment ->
                append(segment.segmentIndex).append('\t')
                append(enc(segment.text)).append('\t')
                append(enc(segment.sectionUid)).append('\t')
                append(segment.sourceFileId?.toString().orEmpty()).append('\t')
                append(segment.sourcePageStart?.toString().orEmpty()).append('\t')
                append(segment.sourcePageEnd?.toString().orEmpty()).append('\t')
                append(enc(segment.sourceTaziehUid)).append('\t')
                append(enc(segment.sourceRoleUid)).append('\t')
                append(enc(segment.sourceSectionTitle)).append('\t')
                append(enc(segment.sourceSectionUids)).append('\t')
                append(enc(segment.sourceMethod)).append('\t')
                append(enc(segment.sourceContentHash)).append('\n')
            }
    }

    fun decodeSegments(snapshot: String, turnId: Long): List<DirectorTextSegmentEntity> {
        if (snapshot.isBlank()) return emptyList()
        val lines = snapshot.removePrefix("v1\n").lineSequence().filter { it.isNotBlank() }
        return lines.mapNotNull { line ->
            val parts = line.split('\t')
            if (parts.size < 12) return@mapNotNull null
            DirectorTextSegmentEntity(
                id = 0,
                turnId = turnId,
                segmentIndex = parts[0].toIntOrNull() ?: 0,
                text = dec(parts[1]),
                sectionUid = dec(parts[2]).ifBlank { null },
                sourceFileId = parts[3].toLongOrNull(),
                sourcePageStart = parts[4].toIntOrNull(),
                sourcePageEnd = parts[5].toIntOrNull(),
                sourceTaziehUid = dec(parts[6]).ifBlank { null },
                sourceRoleUid = dec(parts[7]).ifBlank { null },
                sourceSectionTitle = dec(parts[8]),
                sourceSectionUids = dec(parts[9]),
                sourceMethod = dec(parts[10]).ifBlank { "LEGACY" },
                sourceContentHash = dec(parts[11])
            )
        }.toList()
    }

    fun encodeParticipants(values: List<DirectorParticipantEntity>): String = buildString {
        append("v1\n")
        values.sortedWith(compareBy<DirectorParticipantEntity> { it.orderIndex }.thenBy { it.id })
            .forEach { participant ->
                append(participant.orderIndex).append('\t')
                append(if (participant.isLead) "1" else "0").append('\t')
                append(enc(participant.roleUid)).append('\t')
                append(enc(participant.roleTitle)).append('\n')
            }
    }

    fun decodeParticipants(snapshot: String, turnId: Long): List<DirectorParticipantEntity> {
        if (snapshot.isBlank()) return emptyList()
        return snapshot.removePrefix("v1\n").lineSequence().filter { it.isNotBlank() }.mapNotNull { line ->
            val parts = line.split('\t')
            if (parts.size < 4) return@mapNotNull null
            DirectorParticipantEntity(
                id = 0,
                turnId = turnId,
                orderIndex = parts[0].toIntOrNull() ?: 0,
                isLead = parts[1] == "1",
                roleUid = dec(parts[2]),
                roleTitle = dec(parts[3])
            )
        }.toList()
    }

    fun sourceSectionIds(value: DirectorTextSegmentEntity): List<String> =
        (value.sourceSectionUids.ifBlank { value.sectionUid.orEmpty() })
            .split('\u001F').map { it.trim() }.filter { it.isNotBlank() }.distinct()
}

enum class DirectorOfficeQualitySeverity { ERROR, WARNING, INFO }

data class DirectorOfficeQualityIssue(
    val code: String,
    val severity: DirectorOfficeQualitySeverity,
    val message: String,
    val turnId: Long? = null,
    val turnNumber: Int? = null
)

/** Local deterministic checks. It never changes a turn or source-library record. */
object DirectorOfficeQualityChecker {
    fun check(
        workspace: DirectorOfficeWorkspaceState,
        currentSourceSections: Map<String, String?> = emptyMap(),
        currentSourceRoleUids: Set<String>? = null,
        expectedSourceRoles: Map<String, String>? = null
    ): List<DirectorOfficeQualityIssue> {
        val issues = mutableListOf<DirectorOfficeQualityIssue>()
        val participantsByTurn = workspace.participantsByTurn
        val segmentsByTurn = workspace.textSegmentsByTurn

        workspace.selectedVersion?.let { version ->
            if (version.planId != workspace.plan.id) {
                issues += DirectorOfficeQualityIssue(
                    "VERSION_PLAN_MISMATCH", DirectorOfficeQualitySeverity.ERROR,
                    "نسخه انتخاب‌شده به دفتر فعلی تعلق ندارد."
                )
            }
        }

        if (workspace.turns.isEmpty()) {
            issues += DirectorOfficeQualityIssue(
                code = "EMPTY_OFFICE", severity = DirectorOfficeQualitySeverity.WARNING,
                message = "این نسخه هنوز هیچ نوبتی ندارد و دفتر کامل نیست."
            )
        }

        // Complete-office validation is based on the full role roster of the selected majlis,
        // not merely on whether the turns that happen to exist look internally valid.
        if (expectedSourceRoles == null || expectedSourceRoles.isEmpty()) {
            issues += DirectorOfficeQualityIssue(
                code = "SOURCE_ROLE_ROSTER_UNAVAILABLE",
                severity = DirectorOfficeQualitySeverity.WARNING,
                message = "فهرست نقش‌های مجلس اصلی برای مقایسه در دسترس نیست؛ کامل‌بودن کل دفتر قابل تأیید نیست."
            )
        } else {
            val registeredRoleUids = workspace.participantsByTurn.values.flatten()
                .map { it.roleUid }.filter { it.isNotBlank() && !it.startsWith("personal-role-") }.toSet()
            expectedSourceRoles.forEach { (roleUid, roleTitle) ->
                if (roleUid !in registeredRoleUids) {
                    issues += DirectorOfficeQualityIssue(
                        code = "MISSING_SOURCE_ROLE",
                        severity = DirectorOfficeQualitySeverity.WARNING,
                        message = "نقش «$roleTitle» هنوز در هیچ نوبتی از دفتر وارد نشده است.",
                        turnId = null,
                        turnNumber = null
                    )
                }
            }
        }

        workspace.turns.forEach { turn ->
            val participants = participantsByTurn[turn.id].orEmpty()
            val segments = segmentsByTurn[turn.id].orEmpty()
            if (turn.turnNumber <= 0) {
                issues += DirectorOfficeQualityIssue(
                    "INVALID_NUMBER", DirectorOfficeQualitySeverity.ERROR,
                    "شماره نوبت باید بزرگ‌تر از صفر باشد.", turn.id, turn.turnNumber
                )
            }
            if (turn.turnType !in setOf(DirectorTurnEntity.TYPE_NORMAL, DirectorTurnEntity.TYPE_COLLECTIVE)) {
                issues += DirectorOfficeQualityIssue(
                    "INVALID_TURN_TYPE", DirectorOfficeQualitySeverity.ERROR,
                    "نوع نوبت ${turn.turnNumber} معتبر نیست.", turn.id, turn.turnNumber
                )
            }
            if (participants.isEmpty()) {
                issues += DirectorOfficeQualityIssue(
                    "NO_ROLE", DirectorOfficeQualitySeverity.WARNING,
                    "برای نوبت ${turn.turnNumber} هیچ نقشی ثبت نشده است.", turn.id, turn.turnNumber
                )
            }
            if (participants.any { it.roleUid.isBlank() || it.roleTitle.isBlank() }) {
                issues += DirectorOfficeQualityIssue(
                    "INVALID_PARTICIPANT", DirectorOfficeQualitySeverity.ERROR,
                    "نوبت ${turn.turnNumber} دارای نقش با شناسه یا عنوان ناقص است.", turn.id, turn.turnNumber
                )
            }
            if (participants.map { it.roleUid }.distinct().size != participants.size) {
                issues += DirectorOfficeQualityIssue(
                    "DUPLICATE_PARTICIPANT", DirectorOfficeQualitySeverity.ERROR,
                    "یک نقش بیش از یک بار به نوبت ${turn.turnNumber} متصل شده است.", turn.id, turn.turnNumber
                )
            }
            if (participants.isNotEmpty() && participants.count { it.isLead } != 1) {
                issues += DirectorOfficeQualityIssue(
                    "INVALID_LEAD_ROLE", DirectorOfficeQualitySeverity.ERROR,
                    "نوبت ${turn.turnNumber} باید دقیقاً یک نقش اصلی داشته باشد.", turn.id, turn.turnNumber
                )
            }
            if (turn.turnType == DirectorTurnEntity.TYPE_COLLECTIVE && participants.size < 2) {
                issues += DirectorOfficeQualityIssue(
                    "COLLECTIVE_WITHOUT_PARTICIPANTS", DirectorOfficeQualitySeverity.ERROR,
                    "نوبت ${turn.turnNumber} جمعی علامت‌گذاری شده اما کمتر از دو نقش دارد.", turn.id, turn.turnNumber
                )
            }
            if (participants.size > 1 && turn.turnType != DirectorTurnEntity.TYPE_COLLECTIVE) {
                issues += DirectorOfficeQualityIssue(
                    "MULTI_ROLE_NOT_COLLECTIVE", DirectorOfficeQualitySeverity.ERROR,
                    "نوبت ${turn.turnNumber} چند شرکت‌کننده دارد اما به‌عنوان نوبت جمعی ثبت نشده است.", turn.id, turn.turnNumber
                )
            }
            currentSourceRoleUids?.let { libraryRoleUids ->
                participants.filter {
                    it.roleUid.isNotBlank() &&
                        !it.roleUid.startsWith("personal-role-") &&
                        it.roleUid !in libraryRoleUids
                }.forEach { participant ->
                    issues += DirectorOfficeQualityIssue(
                        "LIBRARY_ROLE_MISSING", DirectorOfficeQualitySeverity.WARNING,
                        "نقش «${participant.roleTitle}» در فهرست فعلی نقش‌های مجلس پیدا نشد یا هنوز به نقش کتابخانه متصل نشده است.",
                        turn.id, turn.turnNumber
                    )
                }
            }
            if (segments.none { it.text.isNotBlank() }) {
                issues += DirectorOfficeQualityIssue(
                    "NO_TEXT", DirectorOfficeQualitySeverity.ERROR,
                    "متن نوبت ${turn.turnNumber} خالی است یا فقط از فاصله و نویسه‌های خالی تشکیل شده است.",
                    turn.id, turn.turnNumber
                )
            }
            segments.filter { it.text.isBlank() }.forEach {
                issues += DirectorOfficeQualityIssue(
                    "BLANK_SEGMENT", DirectorOfficeQualitySeverity.ERROR,
                    "یک قطعه از متن نوبت ${turn.turnNumber} خالی یا فقط شامل فاصله است.",
                    turn.id, turn.turnNumber
                )
            }
            if (segments.any { it.segmentIndex < 0 }) {
                issues += DirectorOfficeQualityIssue(
                    "INVALID_SEGMENT_INDEX", DirectorOfficeQualitySeverity.ERROR,
                    "شماره ترتیب یکی از قطعه‌های متن نوبت ${turn.turnNumber} منفی است.", turn.id, turn.turnNumber
                )
            }
            if (segments.map { it.segmentIndex }.distinct().size != segments.size) {
                issues += DirectorOfficeQualityIssue(
                    "DUPLICATE_SEGMENT_INDEX", DirectorOfficeQualitySeverity.ERROR,
                    "شماره ترتیب قطعه‌های متن نوبت ${turn.turnNumber} تکراری است.", turn.id, turn.turnNumber
                )
            }
            val orderedSegmentIndexes = segments.map { it.segmentIndex }.distinct().sorted()
            if (orderedSegmentIndexes.isNotEmpty()) {
                val expectedIndexes = (0..orderedSegmentIndexes.last()).toSet()
                val missingIndexes = expectedIndexes - orderedSegmentIndexes.toSet()
                if (missingIndexes.isNotEmpty()) {
                    issues += DirectorOfficeQualityIssue(
                        "MISSING_TEXT_SEGMENT", DirectorOfficeQualitySeverity.ERROR,
                        "در متن نوبت ${turn.turnNumber} قطعه یا قطعه‌هایی از ترتیب متن جا افتاده‌اند (شماره قطعه: ${missingIndexes.sorted().joinToString("، ")}).",
                        turn.id, turn.turnNumber
                    )
                }
            }

            segments.forEach { segment ->
                if (!segment.sourceTaziehUid.isNullOrBlank() &&
                    segment.sourceTaziehUid != workspace.plan.taziehUid) {
                    issues += DirectorOfficeQualityIssue(
                        "SOURCE_TAZIEH_MISMATCH", DirectorOfficeQualitySeverity.ERROR,
                        "منبع متن نوبت ${turn.turnNumber} متعلق به مجلس دیگری است.", turn.id, turn.turnNumber
                    )
                }
                segment.sourceRoleUid?.takeIf { it.isNotBlank() }?.let { sourceRoleUid ->
                    if (participants.none { it.roleUid == sourceRoleUid }) {
                        issues += DirectorOfficeQualityIssue(
                            "SOURCE_ROLE_MISMATCH", DirectorOfficeQualitySeverity.WARNING,
                            "نقش منبع یکی از متن‌های نوبت ${turn.turnNumber} در نقش‌های همین نوبت وجود ندارد.",
                            turn.id, turn.turnNumber
                        )
                    }
                }
                val sourceIds = DirectorOfficeHistorySnapshots.sourceSectionIds(segment)
                if (sourceIds.isNotEmpty() && currentSourceSections.isNotEmpty()) {
                    val sourceTexts = sourceIds.map { currentSourceSections[it] }
                    if (sourceTexts.any { it == null }) {
                        issues += DirectorOfficeQualityIssue(
                            "SOURCE_MISSING", DirectorOfficeQualitySeverity.WARNING,
                            "حداقل یکی از بخش‌های منبع نوبت ${turn.turnNumber} دیگر در کتابخانه پیدا نمی‌شود.",
                            turn.id, turn.turnNumber
                        )
                    } else if (segment.sourceContentHash.isNotBlank()) {
                        val currentHash = ContentUid.sha256(sourceTexts.filterNotNull().joinToString("\n"))
                        if (currentHash != segment.sourceContentHash) {
                            issues += DirectorOfficeQualityIssue(
                                "SOURCE_CHANGED", DirectorOfficeQualitySeverity.INFO,
                                "متن منبع نوبت ${turn.turnNumber} پس از ساخت دفتر در کتابخانه تغییر کرده است؛ متن دفتر دست‌نخورده باقی مانده.",
                                turn.id, turn.turnNumber
                            )
                        }
                    }
                }
            }
        }

        // A shared/collective number must be represented by one turn row with multiple participants,
        // never by several independent turn rows carrying the same number.
        workspace.turns.groupBy { it.turnNumber }.filterKeys { it > 0 }.values
            .filter { it.size > 1 }.forEach { duplicateRows ->
                val bodies = duplicateRows.map { duplicate ->
                    segmentsByTurn[duplicate.id].orEmpty().sortedBy { it.segmentIndex }
                        .joinToString("\n") { it.text }
                        .let(DirectorOfficeWorkspaceUseCases::normalizeDialogueText)
                }
                duplicateRows.forEach { duplicate ->
                    issues += DirectorOfficeQualityIssue(
                        "DUPLICATE_TURN_NUMBER", DirectorOfficeQualitySeverity.ERROR,
                        "شماره ${duplicate.turnNumber} در چند نوبت مستقل ثبت شده است؛ نوبت‌های جمعی باید یک نوبت مشترک با چند نقش باشند.",
                        duplicate.id, duplicate.turnNumber
                    )
                }
                if (bodies.filter { it.isNotBlank() }.distinct().size > 1) {
                    duplicateRows.forEach { duplicate ->
                        issues += DirectorOfficeQualityIssue(
                            "COLLECTIVE_TEXT_MISMATCH", DirectorOfficeQualitySeverity.ERROR,
                            "متن‌های نوبت‌های هم‌شماره ${duplicate.turnNumber} یکسان نیستند؛ این تعارض باید توسط مدیر بررسی شود.",
                            duplicate.id, duplicate.turnNumber
                        )
                    }
                }
            }

        // Gaps in sequence numbers are warnings; they do not invalidate the office.
        val numberedTurns = workspace.turns.map { it.turnNumber }.filter { it > 0 }.distinct().sorted()
        if (numberedTurns.isNotEmpty()) {
            val gaps = (1 until numberedTurns.last()).filterNot { it in numberedTurns }
            if (gaps.isNotEmpty()) {
                issues += DirectorOfficeQualityIssue(
                    "MISSING_TURN_NUMBERS", DirectorOfficeQualitySeverity.WARNING,
                    "شماره‌های نوبت جاافتاده‌اند: ${gaps.joinToString("، ") { DirectorOfficeWorkspaceUseCases.formatPersianNumber(it) }}؛ این مورد هشدار است و مانع ادامه کار نیست."
                )
            }
        }

        val turnIdsInVersion = workspace.turns.map { it.id }.toSet()
        workspace.stageCues.forEach { cue ->
            if (workspace.selectedVersion?.id != cue.versionId) {
                issues += DirectorOfficeQualityIssue(
                    "CUE_VERSION_MISMATCH", DirectorOfficeQualitySeverity.ERROR,
                    "راهنمای صحنه «${cue.title}» به نسخه انتخاب‌شده تعلق ندارد."
                )
            }
            if (cue.title.isBlank() || cue.instruction.isBlank()) {
                issues += DirectorOfficeQualityIssue(
                    "INVALID_STAGE_CUE", DirectorOfficeQualitySeverity.ERROR,
                    "یکی از راهنماهای صحنه عنوان یا توضیح ندارد."
                )
            }
            if (cue.turnId != null && cue.turnId !in turnIdsInVersion) {
                issues += DirectorOfficeQualityIssue(
                    "CUE_TURN_MISMATCH", DirectorOfficeQualitySeverity.ERROR,
                    "راهنمای صحنه «${cue.title}» به نوبتی خارج از نسخه متصل شده است."
                )
            }
            if (cue.orderIndex < 0) {
                issues += DirectorOfficeQualityIssue(
                    "INVALID_CUE_ORDER", DirectorOfficeQualitySeverity.ERROR,
                    "ترتیب یکی از راهنماهای صحنه معتبر نیست."
                )
            }
        }
        if (workspace.stageCues.groupBy { it.orderIndex }.any { it.key >= 0 && it.value.size > 1 }) {
            issues += DirectorOfficeQualityIssue(
                "DUPLICATE_CUE_ORDER", DirectorOfficeQualitySeverity.WARNING,
                "ترتیب نمایش برخی راهنماهای صحنه تکراری است."
            )
        }

        // The same turn number can legitimately exist for different roles in a collective scene.
        // It is a duplicate only when the same role/number pair occurs in multiple turns.
        val occurrences = workspace.turns.flatMap { turn ->
            participantsByTurn[turn.id].orEmpty().map { participant ->
                Triple(participant.roleUid, turn, participant.roleTitle)
            }
        }
        occurrences.groupBy { it.first to it.second.turnNumber }.values.forEach { duplicateGroup ->
            if (duplicateGroup.size > 1) {
                duplicateGroup.forEach { occurrence ->
                    issues += DirectorOfficeQualityIssue(
                        "DUPLICATE_ROLE_NUMBER", DirectorOfficeQualitySeverity.ERROR,
                        "شماره ${occurrence.second.turnNumber} برای نقش «${occurrence.third}» در چند نوبت ثبت شده است.",
                        occurrence.second.id, occurrence.second.turnNumber
                    )
                }
            }
        }

        // Also flag exact repeated dialogue text for the same role, even if the turn numbers differ.
        // Normalize whitespace only; do not remove punctuation or Persian characters.
        val repeatedTexts = workspace.turns.flatMap { turn ->
            val roleRows = participantsByTurn[turn.id].orEmpty()
            val body = segmentsByTurn[turn.id].orEmpty()
                .sortedBy { it.segmentIndex }.joinToString(" ") { it.text.trim() }
                .replace(Regex("\\s+"), " ").trim()
            if (body.isBlank()) emptyList() else roleRows.map { Triple(it.roleUid, body, turn) to it.roleTitle }
        }.groupBy { it.first.first to it.first.second }
        repeatedTexts.values.filter { it.size > 1 }.forEach { duplicates ->
            duplicates.forEach { (entry, roleTitle) ->
                issues += DirectorOfficeQualityIssue(
                    "DUPLICATE_ROLE_TEXT", DirectorOfficeQualitySeverity.WARNING,
                    "متن یکسان برای نقش «$roleTitle» در بیش از یک نوبت تکرار شده است؛ تکراری‌بودن آن را بررسی کنید.",
                    entry.third.id, entry.third.turnNumber
                )
            }
        }

        return issues.distinctBy { listOf(it.code, it.turnId, it.message) }
            .sortedWith(compareBy<DirectorOfficeQualityIssue> { it.severity.ordinal }.thenBy { it.turnNumber ?: Int.MAX_VALUE })
    }
}
