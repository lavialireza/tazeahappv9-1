package com.example.bookapp.data

/**
 * قرارداد مشترک دفتر معین‌البکاء بین Flavorهای Admin و Viewer.
 * کد روش‌ها و کلید مجوزها فقط در این فایل تعریف می‌شوند تا دو APK از یک قرارداد استفاده کنند.
 */
data class DirectorOfficeBuildMethod(
    val code: String,
    val label: String,
    val permission: String,
    val requiresOfficialSource: Boolean,
    val description: String
)

object DirectorOfficeSharedContract {
    const val ROLE_NUMBERED = "ROLE_NUMBERED"
    const val FULL_TEXT = "FULL_TEXT"
    const val MANUAL = "MANUAL"
    const val SELECTOR = "SELECTOR"

    val buildMethods: List<DirectorOfficeBuildMethod> = listOf(
        DirectorOfficeBuildMethod(
            ROLE_NUMBERED, "شماره‌محور", "director.personal.method.number", true,
            "انتخاب بر اساس شماره دیالوگ داخل متن هر نقش؛ دیالوگ‌های هم‌شماره در یک نوبت قرار می‌گیرند."
        ),
        DirectorOfficeBuildMethod(
            FULL_TEXT, "متن کامل", "director.personal.method.fulltext", true,
            "انتقال متن کامل به ترتیب نقش‌ها؛ متن هر نقش کامل و پشت سر هم ثبت می‌شود."
        ),
        DirectorOfficeBuildMethod(
            MANUAL, "دستی", "director.personal.method.manual", false,
            "حالت دستی؛ نوبت و متن را خودتان وارد و ویرایش می‌کنید."
        ),
        DirectorOfficeBuildMethod(
            SELECTOR, "انتخاب‌گر", "director.personal.method.selector", true,
            "انتخاب با معین‌البکا؛ نقش و سپس دیالوگ موردنظر را برای هر نوبت انتخاب می‌کنید."
        )
    )

    val methodCodes: Set<String> = buildMethods.mapTo(linkedSetOf()) { it.code }

    fun method(code: String): DirectorOfficeBuildMethod? = buildMethods.firstOrNull { it.code == code }

    fun canCreatePersonal(permissions: Map<String, Boolean>, code: String): Boolean =
        permissions["director.personal.create"] == true &&
            method(code)?.let { permissions[it.permission] == true } == true
}
