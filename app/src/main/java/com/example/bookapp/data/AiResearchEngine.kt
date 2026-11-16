package com.example.bookapp.data

import java.util.Locale

/** ابزارهای هوشمند آفلاین و توضیح‌پذیر؛ هیچ متن مرجعی را تغییر نمی‌دهند. */
object AiResearchEngine {
    data class Finding(val title: String, val detail: String, val sample: String = "")
    data class SemanticHit(val row: SearchCorpusRow, val score: Int, val snippet: String)

    private val stopWords = setOf("و", "در", "از", "به", "که", "را", "با", "برای", "این", "آن", "بر", "تا", "اما", "یا", "چون", "اگر", "من", "تو", "او", "ما", "شما", "ایشان", "است", "هست", "بود", "شد", "شود", "می", "نمی", "های", "ها")

    fun normalize(value: String): String = normalizeSearchText(value)
        .replace(Regex("[^\\p{L}\\p{N}\\s]"), " ")
        .replace(Regex("\\s+"), " ").trim()

    fun analyzeStructure(text: String): List<Finding> {
        val lines = text.replace("\r", "\n").lines().map { it.trim() }.filter { it.isNotBlank() }
        if (lines.isEmpty()) return listOf(Finding("متنی برای بررسی وجود ندارد", "متن را وارد کنید؛ این ابزار فقط پیشنهاد می‌دهد."))
        val findings = mutableListOf<Finding>()
        val headingCandidates = lines.filter { line ->
            line.length in 3..90 && (line.startsWith("مجلس") || line.startsWith("بخش") || line.startsWith("صحنه") || line.startsWith("تعزیه") || (line.length < 55 && line == line.uppercase(Locale.ROOT)))
        }
        findings += Finding("ساختار احتمالی", "${lines.size} سطر غیرخالی شناسایی شد؛ ${headingCandidates.size} عنوان احتمالی پیدا شد.", headingCandidates.take(20).joinToString("\n"))
        val roleMarkers = lines.filter { Regex("^[\\p{L}ء-ی][\\p{L}ء-ی _‌]{1,35}[:：ـ-]").containsMatchIn(it) }
        findings += Finding("گوینده‌های احتمالی", "${roleMarkers.size} سطر دارای الگوی نام گوینده هستند. تشخیص قطعی نیست.", roleMarkers.take(25).joinToString("\n"))
        val duplicates = lines.groupingBy { normalize(it) }.eachCount().filter { it.value > 1 && it.key.length > 8 }
        findings += Finding("تکرارهای احتمالی", "${duplicates.size} عبارت تکراری شناسایی شد؛ ممکن است تکرار شعری عمدی باشد.", duplicates.entries.take(20).joinToString("\n") { "${it.value} بار: ${it.key}" })
        return findings
    }

    fun suggestSpeakers(text: String): List<Finding> {
        val lines = text.lines().map { it.trim() }.filter { it.isNotBlank() }
        val pattern = Regex("^([\\p{L}ء-ی][\\p{L}ء-ی _‌]{1,35})\\s*[:：ـ-]\\s*(.+)$")
        val grouped = lines.mapNotNull { line -> pattern.find(line)?.let { it.groupValues[1].trim() to it.groupValues[2].trim() } }
            .groupBy({ it.first }, { it.second })
        return grouped.entries.sortedByDescending { it.value.size }.map { (speaker, speeches) ->
            Finding(speaker, "${speeches.size} گفتار احتمالی", speeches.firstOrNull().orEmpty())
        }
    }

    fun glossaryCandidates(text: String, maxItems: Int = 30): List<Finding> {
        val tokens = normalize(text).split(' ').filter { it.length >= 4 && it !in stopWords && it.any(Char::isLetter) }
        return tokens.groupingBy { it }.eachCount().filter { it.value >= 2 }
            .entries.sortedWith(compareByDescending<Map.Entry<String, Int>> { it.value }.thenBy { it.key })
            .take(maxItems).map { Finding(it.key, "${it.value} بار در متن دیده شده؛ نامزد بررسی پژوهشگر، نه معنی تأییدشده.") }
    }

    fun semanticSearch(corpus: List<SearchCorpusRow>, query: String, limit: Int = 30): List<SemanticHit> {
        val terms = normalize(query).split(' ').filter { it.length >= 2 && it !in stopWords }.toSet()
        if (terms.isEmpty()) return emptyList()
        return corpus.mapNotNull { row ->
            val body = normalize(listOf(row.sectionTitle, row.content, row.roleTitle, row.taziehTitle, row.fieldTitle, row.footnotesText.orEmpty()).joinToString(" "))
            val words = body.split(' ').toSet()
            val matched = terms.count { term -> term in words || words.any { it.startsWith(term) || term.startsWith(it) && it.length >= 3 } }
            if (matched == 0) null else {
                val score = matched * 100 / terms.size + if (normalize(row.sectionTitle).contains(terms.first())) 20 else 0
                val index = terms.map { body.indexOf(it) }.filter { it >= 0 }.minOrNull() ?: 0
                val start = (index - 70).coerceAtLeast(0)
                SemanticHit(row, score, body.substring(start, (start + 220).coerceAtMost(body.length)))
            }
        }.sortedWith(compareByDescending<SemanticHit> { it.score }.thenBy { it.row.taziehTitle }.thenBy { it.row.roleTitle }).take(limit)
    }
}
