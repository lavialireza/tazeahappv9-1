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
        currentSourceRoleUids: Set<String>? = null
    ): List<DirectorOfficeQualityIssue> {
        val issues = mutableListOf<DirectorOfficeQualityIssue>()
        val participantsByTurn = workspace.participantsByTurn
        val segmentsByTurn = workspace.textSegmentsByTurn

        if (workspace.turns.isEmpty()) {
            issues += DirectorOfficeQualityIssue(
                code = "EMPTY_OFFICE", severity = DirectorOfficeQualitySeverity.INFO,
                message = "این نسخه هنوز نوبتی ندارد."
            )
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
            if (participants.isEmpty()) {
                issues += DirectorOfficeQualityIssue(
                    "NO_ROLE", DirectorOfficeQualitySeverity.WARNING,
                    "برای نوبت ${turn.turnNumber} هیچ نقشی ثبت نشده است.", turn.id, turn.turnNumber
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
                    "متن دیالوگ نوبت ${turn.turnNumber} خالی است.", turn.id, turn.turnNumber
                )
            }
            segments.filter { it.text.isBlank() }.forEach {
                issues += DirectorOfficeQualityIssue(
                    "BLANK_SEGMENT", DirectorOfficeQualitySeverity.WARNING,
                    "یکی از قطعه‌های دیالوگ نوبت ${turn.turnNumber} خالی است.", turn.id, turn.turnNumber
                )
            }

            segments.forEach { segment ->
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

        // A number may repeat for different roles (collective scenes); only the same role/number pair is suspicious.
        val occurrences = workspace.turns.flatMap { turn ->
            participantsByTurn[turn.id].orEmpty().map { participant ->
                Triple(participant.roleUid, turn.turnNumber, turn)
            }
        }
        occurrences.groupBy { it.first to it.second }.values.forEach { duplicateGroup ->
            if (duplicateGroup.size > 1) {
                val first = duplicateGroup.first().third
                val roleTitle = participantsByTurn[first.id].orEmpty()
                    .firstOrNull { it.roleUid == duplicateGroup.first().first }?.roleTitle.orEmpty()
                issues += DirectorOfficeQualityIssue(
                    "DUPLICATE_ROLE_NUMBER", DirectorOfficeQualitySeverity.WARNING,
                    "شماره ${first.turnNumber} برای نقش «$roleTitle» در بیش از یک نوبت تکرار شده است.",
                    first.id, first.turnNumber
                )
            }
        }

        return issues.distinctBy { listOf(it.code, it.turnId, it.message) }
            .sortedWith(compareBy<DirectorOfficeQualityIssue> { it.severity.ordinal }.thenBy { it.turnNumber ?: Int.MAX_VALUE })
    }
}
