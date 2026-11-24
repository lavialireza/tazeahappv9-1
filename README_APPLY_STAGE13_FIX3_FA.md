# راهنمای اعمال Patch مرحله سیزدهم – Fix 3

این بسته فقط فایل‌های مرتبط با قراردادهای DAO، کنترل صحت، تاریخچه، دیتابیس و صفحه مشترک دفتر را در مسیر اصلی پروژه قرار می‌دهد.

## روش اعمال
1. ابتدا از پروژه فعلی یک کپی پشتیبان بگیرید.
2. فایل ZIP را روی ریشه پروژه GitHub/Android Studio استخراج کنید و گزینه جایگزینی فایل‌های هم‌نام را بپذیرید.
3. فقط یک یا دو فایل را انتخابی جایگزین نکنید؛ تمام پوشه‌ها و فایل‌های داخل Patch باید روی پروژه اعمال شوند.
4. در Git اجرا کنید:
   `git status --short`
   سپس همه فایل‌های تغییرکرده و جدید را Commit و Push کنید.
5. کنترل کنید که SHA اجرای جدید از SHA قبلی بیشتر باشد.

## علت خطای لاگ قبلی
در لاگ 121561943056، وظیفه `:app:kspAdminQaKotlin` از مرحله عبور کرده بود. شکست واقعی در `:app:compileAdminQaKotlin` رخ داده بود. خطاها نشان می‌دادند که `DirectorDao.kt` و `DirectorOfficeMainPageDao.kt`/`DirectorOfficeWorkspaceDao.kt` از دو نسل متفاوت کد بوده‌اند و فایل `DirectorOfficeAuditHelpers.kt` و تابع `uniqueOfficeTitle` نیز در Commit بیلدشده در دسترس نبوده‌اند.

## کنترل‌های لازم بعد از استخراج
باید این عبارات در فایل‌های مشخص‌شده پیدا شوند:
- `interface DirectorOfficeMainPageDao : DirectorOfficeWorkspaceDao`
- `suspend fun textSegmentsForTurns`
- `suspend fun insertChangeHistory`
- `object DirectorOfficeHistorySnapshots`
- `data class DirectorOfficeQualityIssue`
- `enum class DirectorOfficeQualitySeverity`
- `fun uniqueOfficeTitle`
- `DirectorChangeHistoryEntity::class`
- `version = 2`
- `MIGRATION_1_2`

این Patch بیلد کامل Gradle را اجرا نمی‌کند؛ نتیجه قطعی فقط پس از اجرای Workflow مشخص می‌شود.
