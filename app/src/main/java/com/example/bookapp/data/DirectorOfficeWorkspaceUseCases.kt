package com.example.bookapp.data

/**
 * منطق دامنه مشترک دفتر معین‌البکاء؛ بدون وابستگی به Room، Context یا Flavor.
 * Admin و Viewer باید برای پردازش متن و قواعد پایه نوبت از همین پیاده‌سازی استفاده کنند.
 */
object DirectorOfficeWorkspaceUseCases {
    /** تبدیل ارقام فارسی/عربی به لاتین؛ برای جستجو و تحلیل شماره نوبت. */
    fun normalizeNumber(value: String): String = value.map { ch ->
        when (ch) {
            in '۰'..'۹' -> ('0'.code + (ch.code - '۰'.code)).toChar()
            in '٠'..'٩' -> ('0'.code + (ch.code - '٠'.code)).toChar()
            else -> ch
        }
    }.joinToString("")

    /**
     * جداسازی دیالوگ‌ها با خطوط خالی؛ شکست خطوط داخل هر دیالوگ حفظ می‌شود.
     */
    fun splitDialogues(content: String): List<String> {
        if (content.isBlank()) return emptyList()
        return content.replace("\r\n", "\n").replace('\r', '\n')
            .split(Regex("\n[\t ]*\n+"))
            .map { it.trim() }
            .filter { it.isNotBlank() }
    }

    /**
     * استخراج دیالوگ‌های شماره‌دار از متن هر نقش. قالب‌های 1-، ۱)، دیالوگ ۳: و
     * شماره در ابتدای خط پشتیبانی می‌شوند؛ خطوط بعدی تا شماره بعدی به همان دیالوگ تعلق دارند.
     */
    fun parseNumberedDialogues(content: String): List<Pair<Int, String>> {
        if (content.isBlank()) return emptyList()
        val heading = Regex("^\\s*(?:(?:دیالوگ|نوبت|شماره)\\s*)?([0-9۰-۹٠-٩]{1,6})(?:\\s*[-–—ـ.)،:：]\\s*|\\s+|$)(.*)$")
        val result = mutableListOf<Pair<Int, String>>()
        var currentNumber: Int? = null
        val body = StringBuilder()
        fun flush() {
            val number = currentNumber
            val text = body.toString().trim()
            if (number != null && text.isNotBlank()) result += number to text
            body.setLength(0)
        }
        content.replace("\r\n", "\n").replace('\r', '\n').lines().forEach { line ->
            val match = heading.matchEntire(line)
            if (match != null) {
                flush()
                currentNumber = normalizeNumber(match.groupValues[1]).toIntOrNull()
                val inline = match.groupValues[2].trim()
                if (inline.isNotBlank()) body.append(inline).append('\n')
            } else if (currentNumber != null) {
                body.append(line).append('\n')
            }
        }
        flush()
        return result
    }

    /**
     * اگر چند دفتر برای یک مجلس ساخته شود، نام آن‌ها را قابل تشخیص نگه می‌دارد.
     * نام پایه در صورت تکرار با پسوند فارسی‌پسند (۲)، (۳) و ... یکتا می‌شود.
     */
    fun uniqueOfficeTitle(baseTitle: String, existingTitles: Iterable<String>): String {
        val base = baseTitle.trim().ifBlank { "دفتر معین‌البکاء" }
        fun normalizedTitle(value: String) = normalizeNumber(value.trim()).lowercase()
        val normalizedExisting = existingTitles.map(::normalizedTitle).toSet()
        if (normalizedTitle(base) !in normalizedExisting) return base
        var suffix = 2
        while (normalizedTitle("$base ($suffix)") in normalizedExisting) suffix++
        val localizedSuffix = suffix.toString().map { digit ->
            if (digit in '0'..'9') ('۰'.code + digit.code - '0'.code).toChar() else digit
        }.joinToString("")
        return "$base ($localizedSuffix)"
    }

    /** شاخص بعدی نوبت، با پشتیبانی از فهرست خالی. */
    fun nextSequenceIndex(turns: List<DirectorTurnEntity>): Int =
        (turns.maxOfOrNull { it.sequenceIndex } ?: -1) + 1

    /** شماره بعدی نوبت برای روش انتخاب‌گر. */
    fun nextTurnNumber(turns: List<DirectorTurnEntity>): Int =
        (turns.maxOfOrNull { it.turnNumber } ?: 0) + 1

    /** ترتیب نهایی را مستقل از DAO تعیین می‌کند. */
    fun normalizedOrder(turns: List<DirectorTurnEntity>): List<Pair<Long, Int>> =
        turns.mapIndexed { index, turn -> turn.id to index }

    /** قاعده پایه پیش‌نویس: هیچ عملیات شخصی نباید روی دفتر/نسخه منتشرشده انجام شود. */
    fun requireDraft(plan: DirectorPlanEntity, version: DirectorPlanVersionEntity) {
        require(plan.status != DirectorPlanEntity.STATUS_ARCHIVED &&
            version.status == DirectorPlanVersionEntity.STATUS_DRAFT) {
            "ویرایش فقط روی نسخه پیش‌نویس و دفتر فعال مجاز است. نسخه منتشرشده قابل تغییر نیست."
        }
    }
}
