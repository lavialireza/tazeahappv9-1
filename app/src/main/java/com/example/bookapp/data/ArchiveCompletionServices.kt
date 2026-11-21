package com.example.bookapp.data

import androidx.room.withTransaction
import com.example.bookapp.data.archive.TaziehVersionEntity
import org.json.JSONArray
import org.json.JSONObject
import java.security.MessageDigest

/** نتیجه مقایسه دو نسخه مستقل؛ متن مرجع هیچ‌گاه در این سرویس تغییر نمی‌کند. */
data class VersionSectionDiff(
    val roleTitle: String,
    val sectionTitle: String,
    val leftSectionUid: String?,
    val rightSectionUid: String?,
    val diff: TextDiffSummary
)
data class VersionComparisonReport(
    val leftVersionUid: String,
    val rightVersionUid: String,
    val matchedSections: Int,
    val addedSections: Int,
    val removedSections: Int,
    val sectionDiffs: List<VersionSectionDiff>
)

class VersionComparisonService(private val db: AppDatabase) {
    suspend fun compare(leftUid: String, rightUid: String): VersionComparisonReport {
        require(leftUid.isNotBlank() && rightUid.isNotBlank() && leftUid != rightUid) { "دو نسخه مستقل و متفاوت انتخاب کنید." }
        val left = db.archiveDao().versionByUid(leftUid) ?: error("نسخه اول پیدا نشد.")
        val right = db.archiveDao().versionByUid(rightUid) ?: error("نسخه دوم پیدا نشد.")
        require(left.taziehUid == right.taziehUid) { "فقط نسخه‌های متعلق به یک تعزیه قابل مقایسه هستند." }
        val leftRows = loadVersionRows(left)
        val rightRows = loadVersionRows(right)
        val keyOf = { role: String, section: String -> normalizeSearchText(role) + "\u0000" + normalizeSearchText(section) }
        val l = leftRows.groupBy { keyOf(it.first, it.second.title) }
        val r = rightRows.groupBy { keyOf(it.first, it.second.title) }
        val keys = (l.keys + r.keys).toSortedSet()
        val output = mutableListOf<VersionSectionDiff>()
        var matched = 0; var added = 0; var removed = 0
        keys.forEach { key ->
            val leftGroup = l[key].orEmpty()
            val rightGroup = r[key].orEmpty()
            val role = leftGroup.firstOrNull()?.first ?: rightGroup.first().first
            val title = leftGroup.firstOrNull()?.second?.title ?: rightGroup.first().second.title
            val pairs = maxOf(leftGroup.size, rightGroup.size)
            for (index in 0 until pairs) {
                val a = leftGroup.getOrNull(index)?.second
                val b = rightGroup.getOrNull(index)?.second
                when {
                    a != null && b != null -> { matched++; output += VersionSectionDiff(role, title, a.uid, b.uid, TaziehTextDiff.compare(a.content, b.content)) }
                    a != null -> { removed++; output += VersionSectionDiff(role, title, a.uid, null, TextDiffSummary(0, 0, 1, listOf(DiffLine(DiffKind.REMOVED, a.content, 1, null)))) }
                    b != null -> { added++; output += VersionSectionDiff(role, title, null, b.uid, TextDiffSummary(0, 1, 0, listOf(DiffLine(DiffKind.ADDED, b.content, null, 1)))) }
                }
            }
        }
        return VersionComparisonReport(leftUid, rightUid, matched, added, removed, output)
    }

    private suspend fun loadVersionRows(version: TaziehVersionEntity): List<Pair<String, SectionEntity>> {
        val result = mutableListOf<Pair<String, SectionEntity>>()
        db.roleDao().getByVersionUid(version.uid).forEach { role ->
            db.sectionDao().getByRole(role.id).forEach { section -> result += role.title to section }
        }
        return result
    }
}

/** پیوندهای واژه‌نامه فقط به‌صورت annotation ارائه می‌شوند؛ متن ورودی هرگز ویرایش نمی‌شود. */
data class GlossaryOccurrence(val start: Int, val endExclusive: Int, val termUid: String, val headword: String, val meaningCount: Int, val verifiedMeanings: List<String> = emptyList(), val sources: List<String> = emptyList())
object GlossaryTextLinker {
    fun link(text: String, terms: List<GlossaryTermEntity>, spellings: List<GlossarySpellingEntity>, meanings: List<GlossaryMeaningEntity>): List<GlossaryOccurrence> {
        val approved = terms.filter { it.reviewStatus == GlossaryTermEntity.STATUS_VERIFIED }
        val byId = approved.associateBy { it.id }
        val variants = (approved.map { Triple(it.id, it.headword, it.uid) } + spellings.filter { it.termId in byId }.map { Triple(it.termId, it.spelling, byId.getValue(it.termId).uid) })
            .filter { it.second.isNotBlank() }.distinctBy { it.first to normalizeSearchText(it.second) }
            .sortedByDescending { normalizeSearchText(it.second).length }
        val normalized = normalizeWithOffsets(text)
        val found = mutableListOf<GlossaryOccurrence>()
        val occupied = mutableListOf<IntRange>()
        for ((id, variant, uid) in variants) {
            val needle = normalizeSearchText(variant)
            if (needle.isBlank()) continue
            var from = 0
            while (from <= normalized.first.length - needle.length) {
                val at = normalized.first.indexOf(needle, from)
                if (at < 0) break
                val afterIndex = at + needle.length
                val leftBoundaryOk = at == 0 || !normalized.first[at - 1].isLetterOrDigit()
                val rightBoundaryOk = afterIndex >= normalized.first.length || !normalized.first[afterIndex].isLetterOrDigit()
                if (!leftBoundaryOk || !rightBoundaryOk) { from = at + 1; continue }
                val start = normalized.second[at]
                val end = normalized.second[(at + needle.length - 1).coerceAtMost(normalized.second.lastIndex)] + 1
                val range = start..(end - 1)
                if (occupied.none { it.first <= range.last && range.first <= it.last }) {
                    occupied += range
                    val approvedMeanings = meanings.filter { it.termId == id && it.reviewStatus == GlossaryTermEntity.STATUS_VERIFIED }
                    found += GlossaryOccurrence(start, end, uid, byId.getValue(id).headword, approvedMeanings.size,
                        approvedMeanings.map { it.meaning }, approvedMeanings.mapNotNull { it.sourceTitle.takeIf(String::isNotBlank) }.distinct())
                }
                from = at + needle.length
            }
        }
        return found.sortedBy { it.start }
    }

    private fun normalizeWithOffsets(source: String): Pair<String, List<Int>> {
        val out = StringBuilder(); val offsets = mutableListOf<Int>()
        source.forEachIndexed { index, raw ->
            val c = when (raw) { 'ي', 'ى' -> 'ی'; 'ك' -> 'ک'; 'ۀ', 'ة' -> 'ه'; '\u0640', '\u200c' -> ' '; else -> raw }
            if (c in '\u064B'..'\u065F' || c == '\u0670') return@forEachIndexed
            if (c.isWhitespace()) {
                if (out.isNotEmpty() && out.last() != ' ') { out.append(' '); offsets += index }
            } else { out.append(c.lowercaseChar()); offsets += index }
        }
        if (out.isNotEmpty() && out.last() == ' ') { out.setLength(out.length - 1); offsets.removeAt(offsets.lastIndex) }
        return out.toString() to offsets
    }
}

/** گراف ساده و قابل خروجی از پیوندهای دانشنامه؛ یال‌ها از داده تأییدشده ساخته می‌شوند. */
data class KnowledgeGraphNode(val id: String, val label: String, val kind: String)
data class KnowledgeGraphEdge(val fromId: String, val toId: String, val relation: String, val contextUid: String)
data class KnowledgeGraph(val nodes: List<KnowledgeGraphNode>, val edges: List<KnowledgeGraphEdge>)
object EncyclopediaGraphBuilder {
    fun build(records: List<EncyclopediaRecordEntity>, links: List<EncyclopediaLinkEntity>): KnowledgeGraph {
        val approved = records.filter { it.reviewStatus == EncyclopediaRecordEntity.VERIFIED }
        val nodes = approved.map { KnowledgeGraphNode(it.uid, it.title, it.kind) }
        val edges = links.flatMap { link ->
            val record = approved.firstOrNull { it.id == link.recordId } ?: return@flatMap emptyList()
            listOf(
                "TAZIEH" to link.taziehUid,
                "VERSION" to link.versionUid,
                "ROLE" to link.roleUid,
                "SECTION" to link.sectionUid
            ).filter { it.second.isNotBlank() }.map { (kind, uid) ->
                val contextId = "context:$kind:$uid"
                KnowledgeGraphEdge(record.uid, contextId, link.relationType, uid)
            }
        }
        val contextNodes = edges.map { it.toId }.distinct().map { id ->
            val kind = id.removePrefix("context:").substringBefore(':')
            val label = id.substringAfterLast(':')
            KnowledgeGraphNode(id, label, "ARCHIVE_$kind")
        }
        return KnowledgeGraph((nodes + contextNodes).distinctBy { it.id }, edges.distinct())
    }
}

/** ساخت Snapshot ثابت اجرای زنده با متن و هش؛ تغییر بعدی آرشیو Snapshot قبلی را عوض نمی‌کند. */
data class PerformanceSnapshot(val performanceUid: String, val versionUid: String, val createdAt: Long, val json: String, val sha256: String)
class PerformanceSnapshotService(private val db: AppDatabase) {
    suspend fun create(performanceUid: String): PerformanceSnapshot = db.withTransaction {
        val performance = db.performanceDao().allPerformances().firstOrNull { it.uid == performanceUid } ?: error("برنامه اجرا پیدا نشد.")
        require(performance.versionUid.isNotBlank()) { "برنامه اجرا به نسخه مشخصی از تعزیه متصل نیست." }
        val version = db.archiveDao().versionByUid(performance.versionUid) ?: error("نسخه مرجع اجرا پیدا نشد.")
        val assignments = db.performanceDao().assignments(performance.id)
        val root = JSONObject().put("snapshotVersion", 1).put("performanceUid", performance.uid)
            .put("performanceTitle", performance.title).put("taziehUid", version.taziehUid)
            .put("versionUid", version.uid).put("versionNumber", version.versionNumber)
            .put("versionTitle", version.title).put("createdAt", System.currentTimeMillis())
        require(assignments.isNotEmpty()) { "برای این اجرا هنوز نقشی تخصیص داده نشده است." }
        val roleArray = JSONArray()
        assignments.forEach { assignment ->
            val role = db.roleDao().getByUid(assignment.roleUid) ?: error("نقش تخصیص‌یافته «${assignment.roleTitle}» پیدا نشد.")
            require(role.versionUid == version.uid) { "نقش «${role.title}» متعلق به نسخه دیگری است؛ پیش از ساخت Snapshot تخصیص‌ها را اصلاح کنید." }
            val sections = JSONArray()
            db.sectionDao().getByRole(role.id).forEach { section ->
                val footnotes = JSONArray().apply {
                    db.footnoteDao().getBySection(section.id).forEach { note ->
                        put(JSONObject().put("uid", note.uid).put("term", note.term).put("explanation", note.explanation))
                    }
                }
                sections.put(JSONObject().put("sectionUid", section.uid).put("order", section.orderIndex)
                    .put("title", section.title).put("content", section.content).put("footnotes", footnotes))
            }
            val member = db.performanceDao().member(assignment.memberId) ?: error("عضو تخصیص‌یافته پیدا نشد.")
            roleArray.put(JSONObject().put("roleUid", role.uid).put("roleTitle", role.title)
                .put("memberUid", member.uid).put("memberName", member.name)
                .put("isAlternate", assignment.isAlternate).put("sections", sections))
        }
        root.put("assignedRoles", roleArray)
        root.put("directorNotes", JSONArray().apply {
            db.performanceDao().notes(performance.id).forEach { note ->
                put(JSONObject().put("sectionUid", note.sectionUid).put("type", note.noteType).put("body", note.body).put("updatedAt", note.updatedAt))
            }
        })
        val json = root.toString(2)
        PerformanceSnapshot(performance.uid, version.uid, root.getLong("createdAt"), json, sha256(json))
    }

    private fun sha256(text: String) = MessageDigest.getInstance("SHA-256").digest(text.toByteArray(Charsets.UTF_8)).joinToString("") { "%02x".format(it) }
}

/** خروجی ساختارمند از فهرست انتخاب‌ها؛ برای جلوگیری از واردکردن دستی UID در فرم‌های بعدی. */
data class ArchiveVersionOption(val uid: String, val title: String, val versionNumber: Int, val isCurrent: Boolean)
suspend fun AppDatabase.versionOptions(taziehUid: String): List<ArchiveVersionOption> = archiveDao().versionsForTazieh(taziehUid).map {
    ArchiveVersionOption(it.uid, it.title, it.versionNumber, it.isCurrent)
}

/** اعتبارسنجی انتخاب ۲ تا ۴ نسخه و ساخت زوج‌های مقایسه با نسخه مبنا. */
object MultiVersionComparisonPlan {
    fun pairs(versionUids: List<String>): List<Pair<String, String>> {
        require(versionUids.size in 2..4) { "برای مقایسه باید بین ۲ تا ۴ نسخه انتخاب کنید." }
        require(versionUids.all { it.isNotBlank() }) { "شناسه نسخه انتخاب‌شده معتبر نیست." }
        require(versionUids.distinct().size == versionUids.size) { "یک نسخه را بیش از یک بار انتخاب نکنید." }
        val baseline = versionUids.first()
        return versionUids.drop(1).map { baseline to it }
    }
}
