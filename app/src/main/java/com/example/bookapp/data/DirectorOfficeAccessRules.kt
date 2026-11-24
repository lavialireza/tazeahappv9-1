package com.example.bookapp.data

/** قواعد مشترک دسترسی دفتر؛ مستقل از نمایش دکمه‌ها و قابل آزمون واحد. */
object DirectorOfficeAccessRules {
    fun canViewPersonal(permissions: Map<String, Boolean>): Boolean =
        permissions["director.personal.view"] == true

    fun canViewOfficial(permissions: Map<String, Boolean>): Boolean =
        permissions["director.official.view"] == true

    fun canCreatePersonal(permissions: Map<String, Boolean>, method: String): Boolean =
        permissions["director.personal.view"] == true &&
            DirectorOfficeSharedContract.canCreatePersonal(permissions, method)

    fun canEditPersonal(permissions: Map<String, Boolean>): Boolean =
        permissions["director.personal.view"] == true && permissions["director.personal.edit"] == true

    fun canDeletePersonal(permissions: Map<String, Boolean>): Boolean =
        permissions["director.personal.view"] == true && permissions["director.personal.delete"] == true

    /** انتشار، کپی و خروجی برای Viewer در هر شرایطی ممنوع است. */
    fun canPublishOfficial(@Suppress("UNUSED_PARAMETER") permissions: Map<String, Boolean>): Boolean = false
    fun canCopyOrExport(@Suppress("UNUSED_PARAMETER") permissions: Map<String, Boolean>): Boolean = false
}
