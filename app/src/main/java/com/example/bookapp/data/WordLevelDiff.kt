package com.example.bookapp.data

/** مقایسه واژه‌ای با حفظ متن خام هر نسخه؛ برای متن‌های خیلی بزرگ به‌صورت امن به اختلاف خطی برمی‌گردد. */
enum class WordChange { SAME, ADDED, REMOVED }
data class WordDiffToken(val value: String, val change: WordChange, val leftIndex: Int?, val rightIndex: Int?)
object TaziehWordDiff {
    fun compare(left: String, right: String, maxTokens: Int = 1800): List<WordDiffToken> {
        val a = Regex("\\s+|[^\\s]+", RegexOption.MULTILINE).findAll(left).map { it.value }.toList()
        val b = Regex("\\s+|[^\\s]+", RegexOption.MULTILINE).findAll(right).map { it.value }.toList()
        if (a.size.toLong() * b.size.toLong() > maxTokens.toLong() * maxTokens) {
            return TaziehTextDiff.compare(left, right).lines.map { line ->
                WordDiffToken(line.text, when (line.kind) { DiffKind.SAME -> WordChange.SAME; DiffKind.ADDED -> WordChange.ADDED; DiffKind.REMOVED -> WordChange.REMOVED }, line.lineA, line.lineB)
            }
        }
        val dp = Array(a.size + 1) { IntArray(b.size + 1) }
        fun norm(s: String) = normalizeSearchText(s).trim()
        for (i in a.size - 1 downTo 0) for (j in b.size - 1 downTo 0) {
            dp[i][j] = if (norm(a[i]) == norm(b[j])) 1 + dp[i + 1][j + 1] else maxOf(dp[i + 1][j], dp[i][j + 1])
        }
        val out = mutableListOf<WordDiffToken>(); var i = 0; var j = 0
        while (i < a.size && j < b.size) {
            if (norm(a[i]) == norm(b[j])) { out += WordDiffToken(a[i], WordChange.SAME, i, j); i++; j++ }
            else if (dp[i + 1][j] >= dp[i][j + 1]) { out += WordDiffToken(a[i], WordChange.REMOVED, i, null); i++ }
            else { out += WordDiffToken(b[j], WordChange.ADDED, null, j); j++ }
        }
        while (i < a.size) { out += WordDiffToken(a[i], WordChange.REMOVED, i++, null) }
        while (j < b.size) { out += WordDiffToken(b[j], WordChange.ADDED, null, j++) }
        return out
    }
}
