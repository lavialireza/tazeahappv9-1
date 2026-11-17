package com.example.bookapp.data

/** قواعد مشترک دسترسی دفتر؛ مستقل از نمایش دکمه‌ها و قابل آزمون واحد. */
object DirectorOfficeAccessRules {
    private val methodKeys = mapOf(
        "ROLE_NUMBERED" to "director.personal.method.number",
        "FULL_TEXT" to "director.personal.method.fulltext",
        "MANUAL" to "director.personal.method.manual",
        "SELECTOR" to "director.personal.method.selector"
    )

    fun canViewOfficial(permissions: Map<String, Boolean>): Boolean =
        permissions["director.official.view"] == true

    fun canCreatePersonal(permissions: Map<String, Boolean>, method: String): Boolean =
        permissions["director.personal.create"] == true && methodKeys[method]?.let { permissions[it] == true } == true

    fun canEditPersonal(permissions: Map<String, Boolean>): Boolean =
        permissions["director.personal.edit"] == true

    /** انتشار، کپی و خروجی برای Viewer در هر شرایطی ممنوع است. */
    fun canPublishOfficial(@Suppress("UNUSED_PARAMETER") permissions: Map<String, Boolean>): Boolean = false
    fun canCopyOrExport(@Suppress("UNUSED_PARAMETER") permissions: Map<String, Boolean>): Boolean = false
}
