package com.example.bookapp.data

/**
 * قرارداد مشترک دفتر معین‌البکاء بین Flavorهای Admin و Viewer.
 * کد روش‌ها و کلید مجوزها فقط در این فایل تعریف می‌شوند تا دو APK از یک قرارداد استفاده کنند.
 */
data class DirectorOfficeBuildMethod(
    val code: String,
    val label: String,
    val permission: String,
    val requiresLibrarySource: Boolean,
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
            "تشخیص نوبت‌ها بر اساس شماره دیالوگ؛ متن‌های هم‌شماره و یکسان به‌صورت جمعی ثبت می‌شوند و متن‌های متفاوت جداگانه با هشدار برای بازبینی حفظ می‌شوند؛ تکرار شماره در یک نقش همچنان نیازمند اصلاح است."
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
            SELECTOR, "انتخاب معین‌البکاء و اولویت‌بندی بخش‌ها", "director.personal.method.selector", true,
            "نقش‌ها و بخش‌های واقعی مجلس نمایش داده می‌شوند؛ برای هر نقش اولویت بخش‌ها تعیین می‌شود و امکان کنار گذاشتن بخش یا کل نقش وجود دارد. نوبت‌های شماره‌دار از بخش‌های انتخاب‌شده استخراج می‌شوند."
        )
    )

    val methodCodes: Set<String> = buildMethods.mapTo(linkedSetOf()) { it.code }

    fun method(code: String): DirectorOfficeBuildMethod? = buildMethods.firstOrNull { it.code == code }

    fun canCreatePersonal(permissions: Map<String, Boolean>, code: String): Boolean =
        permissions["director.personal.create"] == true &&
            method(code)?.let { permissions[it.permission] == true } == true
}
/** ورودی استاندارد و مستقل از نوع دیتابیس برای ساخت نوبت‌ها از کتابخانه مجلس. */
data class DirectorOfficeBuildRole(
    val uid: String,
    val title: String,
    val orderIndex: Int = 0
)

data class DirectorOfficeBuildSection(
    val uid: String,
    val roleUid: String,
    val content: String,
    val orderIndex: Int = 0,
    /** عنوان بخش در کتابخانه؛ برای ثبت دقیق منشأ متن در دفتر معین‌البکاء. */
    val title: String = ""
)

/** نتیجه ساخت مشترک؛ برای اتصال راهنماهای صحنه به شناسه‌های تازه استفاده می‌شود. */
data class DirectorOfficeBuildResult(
    val turnIdsByNumber: Map<Int, Long>,
    val turnIdsByRole: Map<String, Long>,
    val insertedTurnCount: Int,
    /** هشدارهای غیرمسدودکننده؛ مانند شماره‌های جاافتاده در متن نقش‌ها. */
    val warnings: List<String> = emptyList()
)


/** یک گزینه دیالوگ برای روش انتخاب‌گر؛ مستقل از DAO و Flavor. */
data class DirectorOfficeSelectorEntry(
    val key: String,
    val roleUid: String,
    val roleTitle: String,
    val sectionUid: String,
    val sectionTitle: String,
    val text: String,
    /** هش متن کامل بخش اصلی برای ردیابی منبع؛ متن کتابخانه هرگز تغییر نمی‌کند. */
    val sourceContentHash: String = "",
    /** بازه انتخاب‌شده در متن کامل بخش؛ انتهای بازه exclusive است. */
    val sourceTextStartOffset: Int? = null,
    val sourceTextEndOffset: Int? = null
)
