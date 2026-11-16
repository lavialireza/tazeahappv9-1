package com.example.bookapp.data

/** Line-oriented, deterministic diff. Uses LCS for normal texts and a safe fallback for very large inputs. */
enum class DiffKind { SAME, ADDED, REMOVED }
data class DiffLine(val kind: DiffKind, val text: String, val lineA: Int?, val lineB: Int?)
data class TextDiffSummary(val same: Int, val added: Int, val removed: Int, val lines: List<DiffLine>)

object TaziehTextDiff {
    fun compare(left: String, right: String): TextDiffSummary {
        val a = left.replace("\r\n", "\n").replace('\r', '\n').lines()
        val b = right.replace("\r\n", "\n").replace('\r', '\n').lines()
        if (a.size.toLong() * b.size.toLong() > 250_000L) {
            val same = normalize(left) == normalize(right)
            val lines = if (same) a.mapIndexed { i, s -> DiffLine(DiffKind.SAME, s, i + 1, i + 1) }
                else listOf(DiffLine(DiffKind.REMOVED, "متن نسخه اول (${a.size} سطر)", null, null), DiffLine(DiffKind.ADDED, "متن نسخه دوم (${b.size} سطر)", null, null))
            return TextDiffSummary(if (same) a.size else 0, if (same) 0 else 1, if (same) 0 else 1, lines)
        }
        val dp = Array(a.size + 1) { IntArray(b.size + 1) }
        for (i in a.size - 1 downTo 0) for (j in b.size - 1 downTo 0) {
            dp[i][j] = if (normalize(a[i]) == normalize(b[j])) 1 + dp[i + 1][j + 1] else maxOf(dp[i + 1][j], dp[i][j + 1])
        }
        val out = mutableListOf<DiffLine>(); var i = 0; var j = 0
        while (i < a.size && j < b.size) {
            if (normalize(a[i]) == normalize(b[j])) { out += DiffLine(DiffKind.SAME, a[i], i + 1, j + 1); i++; j++ }
            else if (dp[i + 1][j] >= dp[i][j + 1]) { out += DiffLine(DiffKind.REMOVED, a[i], i + 1, null); i++ }
            else { out += DiffLine(DiffKind.ADDED, b[j], null, j + 1); j++ }
        }
        while (i < a.size) { out += DiffLine(DiffKind.REMOVED, a[i], i + 1, null); i++ }
        while (j < b.size) { out += DiffLine(DiffKind.ADDED, b[j], null, j + 1); j++ }
        return TextDiffSummary(out.count { it.kind == DiffKind.SAME }, out.count { it.kind == DiffKind.ADDED }, out.count { it.kind == DiffKind.REMOVED }, out)
    }
    private fun normalize(s: String) = normalizeSearchText(s).replace(Regex("\\s+"), " ").trim()
}
