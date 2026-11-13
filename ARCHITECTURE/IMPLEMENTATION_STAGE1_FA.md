# گزارش پیاده‌سازی مرحله ۱ معماری جدید Tazieh Studio

## دامنه این مرحله
پیاده‌سازی نخستین زیرساخت آرشیو نسخه‌دار در Android/Room؛ بدون تغییر شناسه بسته‌ها، flavorها، signing، workflow انتشار، سرور بروزرسانی یا سیاست دسترسی Viewer.

## فایل‌های افزوده‌شده
- `app/src/main/java/com/example/bookapp/data/archive/ArchiveEntities.kt`: مدل نسخه تعزیه و فراداده سند منبع.
- `app/src/main/java/com/example/bookapp/data/archive/ArchiveDao.kt`: عملیات خواندن/ثبت نسخه و سند و انتخاب نسخه جاری.
- `app/src/main/java/com/example/bookapp/data/archive/RoomArchiveRepository.kt`: پیاده‌سازی Repository با تراکنش و اعتبارسنجی ورودی.
- `app/src/main/java/com/example/bookapp/domain/model/ArchiveModels.kt`: مدل‌های مستقل از Android.
- `app/src/main/java/com/example/bookapp/domain/repository/ArchiveRepository.kt`: قرارداد Repository.
- `app/src/main/java/com/example/bookapp/domain/usecase/CreateTaziehVersion.kt` و `RegisterSourceDocument.kt`: موارد کاربرد دامنه.

## فایل‌های تغییریافته
- `data/Entities.kt`: افزودن پیوند `versionUid` به نقش‌ها.
- `data/Dao.kt`: به‌روزرسانی نقش همراه با شناسه نسخه.
- `data/AppDatabase.kt`: افزودن دو جدول و DAO و مهاجرت Room از نسخه ۱۰ به ۱۱؛ داده‌های قدیمی به یک نسخه اولیه با وضعیت `LEGACY_UNREVIEWED` پیوند می‌خورند.
- `data/AppDatabase.kt`: ورود محتوای جدید، در صورت نبود نسخه، یک ظرف نسخه اولیه ایجاد می‌کند و نقش‌ها را به آن متصل می‌کند.
- `ContentMergeTest.kt`: آزمون ایجاد نسخه اولیه و پیوند نقش.

## رفتار مهاجرت
- هیچ جدول قبلی حذف یا بازنویسی نمی‌شود.
- برای هر تعزیه موجود یک نسخه شماره ۱ ایجاد می‌شود.
- نقش‌های موجود به نسخه اولیه همان تعزیه متصل می‌شوند.
- اسناد منبع فعلاً به‌صورت فراداده ثبت می‌شوند؛ فایل Word/PDF در این مرحله کپی یا OCR نمی‌شود.
- داده‌های قبلی به‌صورت خودکار «تأییدشده» علامت نمی‌خورند.

## موارد عمداً انجام‌نشده
- هنوز نسخه‌برداری عمیق و مستقل از نقش‌ها، بخش‌ها، گفتارها و پاورقی‌ها هنگام ساخت نسخه جدید انجام نشده است؛ بنابراین نسخه جدید فعلاً ظرف آرشیوی/فراداده‌ای است و نباید آن را snapshot کامل متن تلقی کرد؛ فعال‌سازی نسخه و جابه‌جایی نقش‌ها تا زمان پیاده‌سازی snapshot مستقل عمداً در API عمومی قرار نگرفته است.
- انتخاب نسخه در رابط کاربری، ورود Word/PDF، OCR، نگهداری امن فایل و تطبیق متن با سند اصلی در مراحل بعدی انجام می‌شود.
- سیاست امضای Viewer و Receiver، که در ممیزی قبلی مسئله داشتند، در این مرحله دستکاری نشده‌اند.

## آزمون و محدودیت
یک آزمون Room/Robolectric به مجموعه تست اضافه شد. اجرای Gradle در محیط فعلی ممکن نبود: ZIP فاقد `gradlew` و `gradle/wrapper` است و Gradle نصب‌شده در محیط توسعه در دسترس نیست. بنابراین این مرحله هنوز build یا test عملیِ موفق اعلام نمی‌شود. پس از فراهم شدن Gradle Wrapper معتبر و SDK، باید `testAdminDebugUnitTest`, `assembleAdminQa` و `assembleViewerQa` اجرا شوند.

## بازگشت
این مرحله فقط در کپی مستقل پروژه انجام شده است. برای بازگشت، از کپی مرحله قبل استفاده شود؛ مخزن اصلی و کلیدهای انتشار تغییر نکرده‌اند.
