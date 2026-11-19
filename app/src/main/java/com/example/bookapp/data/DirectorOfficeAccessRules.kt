package com.example.bookapp.data

/** قواعد مشترک دسترسی دفتر؛ مستقل از نمایش دکمه‌ها و قابل آزمون واحد. */
object DirectorOfficeAccessRules {

    fun canViewOfficial(permissions: Map<String, Boolean>): Boolean =
        permissions["director.official.view"] == true

    fun canCreatePersonal(permissions: Map<String, Boolean>, method: String): Boolean =
        DirectorOfficeSharedContract.canCreatePersonal(permissions, method)

    fun canEditPersonal(permissions: Map<String, Boolean>): Boolean =
        permissions["director.personal.edit"] == true

    /** انتشار، کپی و خروجی برای Viewer در هر شرایطی ممنوع است. */
    fun canPublishOfficial(@Suppress("UNUSED_PARAMETER") permissions: Map<String, Boolean>): Boolean = false
    fun canCopyOrExport(@Suppress("UNUSED_PARAMETER") permissions: Map<String, Boolean>): Boolean = false
}
