# نقشه وضع موجود کد و اتصال آن به معماری جدید

این سند بر اساس فایل‌های ZIP بررسی‌شده تهیه شده است؛ بررسی ایستا است و به معنی اجرای build یا آزمون روی دستگاه نیست.

## 1. نقاط موجود قابل استفاده مجدد

| حوزه | محل فعلی | تصمیم معماری |
|---|---|---|
| مدل محتوای پایه | `app/src/main/java/com/example/bookapp/data/Entities.kt` | حفظ و اتصال تدریجی به مدل نسخه‌بندی |
| پایگاه داده | `.../data/AppDatabase.kt` | حفظ Room و migrationهای موجود؛ توسعه افزایشی |
| شناسه پایدار | `.../data/ContentUid.kt` | مبنای UID برای import، sync و ارجاع |
| جستجو | `.../data/SearchDao.kt`, `AdvancedSearch.kt`, `SectionFts.kt` | نگهداری و گسترش به corpus چندنسخه‌ای |
| واژه‌نامه | `.../data/GlossaryStore.kt`, `GlossaryScreen.kt` | مهاجرت تدریجی از preference ساده به موجودیت‌های مستند |
| ورود Word | `app/src/admin/java/com/example/bookapp/data/WordContentImport.kt` | تبدیل به pipeline سند/استخراج/بازبینی |
| انتقال محتوا | `.../data/ContentTransferHelper.kt` | بررسی و اتصال به بسته محتوایی نسخه‌دار |
| کنترل محتوای Viewer | `.../data/ViewerContentWriteGuard.kt`, `ViewerAccessPolicy.kt`, `ViewerPermissionEngine.kt` | حفظ به‌عنوان مرز امنیتی و تقویت آزمون رفتاری |
| همگام‌سازی | `.../data/SyncServerHelper.kt`, `OfflineSyncQueue.kt`, `SyncMetaStore.kt`, `TombstoneStore.kt` | حفظ و تکمیل عملیات نسخه‌دار و تعارض‌یابی |
| پشتیبان | `.../data/BackupHelper.kt` | ارتقا به backup manifest و آزمون restore |
| انتشار | `.github/workflows/release.yml`, `.github/workflows/main.yml` | حفظ کانال رسمی و QA مستقل |
| بروزرسانی | `.../data/UpdateHelper.kt` | حفظ کنترل‌های فعلی و آزمون امضای نصب/آپدیت |
| مدیریت دسترسی | `ViewerAccessTransfer.kt`, `ViewerAccessSigner.kt`, receiverها | بازطراحی امن با حفظ سازگاری و بدون تغییر شتاب‌زده |

## 2. نقشه فعلی داده

مدل فعلی شامل `FieldEntity`, `TaziehEntity`, `RoleEntity`, `SectionEntity`, `FootnoteEntity`, `DialogueEntity`, `DialogueTurnEntity`, `TaziehImageEntity` و موجودیت‌های شخصی از جمله bookmark، tag، recent، reading history و my roles است.

`AppDatabase` نسخه 10 است و migrationهای موجود در فایل شامل مسیرهای 5→6، 6→7، 7→8، 8→9 و 9→10 هستند. UIDهای قابل حمل برای چند موجودیت اضافه شده‌اند. جدول‌های موجود باید حفظ شوند؛ مفهوم `TaziehVersion` و `SourceDocument` در مرحله بعد به‌صورت افزایشی افزوده می‌شود.

## 3. وضعیت ورودی و خروجی محتوا

- ورود Word در کد Admin وجود دارد؛ باید کیفیت ساختاردهی و پیوند به صفحه/منبع ارزیابی شود.
- محتوا در assets مجزای Admin و Viewer قرار دارد.
- خروجی داخلی و بسته Viewer باید از هم تفکیک شوند؛ قالب خارجی نیز به نمونه واقعی مقصد نیاز دارد.
- داده فعلی داخل assets نمونه/آزمایشی است و نباید با مجموعه اصلی Word/PDF کاربر اشتباه گرفته شود.

## 4. ترتیب عملی توسعه از روی کد موجود

1. ساخت آزمون‌های محافظتی برای import، UID، export، sync، backup و Viewer write guard.
2. ایجاد interfaceهای Repository و UseCase اطراف مسیرهای موجود، بدون انتقال انبوه فایل‌ها.
3. افزودن مدل آرشیو `SourceDocument` و `TaziehVersion` با migration افزایشی.
4. تبدیل Word import به job قابل پیگیری با وضعیت‌های استخراج و بازبینی.
5. پیوند قطعات متن به سند/صفحه و حفظ نسخه اصلی.
6. ارتقای واژه‌نامه و جستجو پس از تثبیت منشأ و نسخه‌بندی.

## 5. موانع فنی ثبت‌شده

- ZIP فعلی فاقد `gradlew` و پوشه `gradle/wrapper` است؛ اجرای build تکرارپذیر از همین ZIP بدون تهیه Wrapper/محیط Gradle ممکن نیست.
- مسیر schema در Gradle تعیین شده اما `app/schemas` در ZIP موجود نیست؛ پیش از migration بعدی باید export schema فعال و فایل‌های schema ثبت شوند.
- این محیط تا این لحظه build اندروید یا تست instrumented اجرا نکرده است؛ بنابراین هیچ ادعای موفقیت build مطرح نیست.
- ریسک‌های امنیتی گزارش‌شده در ممیزی policy/receiver باید قبل از هر تغییر در همان مسیرها با طراحی سازگار بررسی شوند.

## 6. محدوده این مرحله

در Stage 0 فقط اسناد معماری و نقشه کد به کپی مستقل پروژه اضافه شده‌اند. فایل‌های Kotlin، Gradle، Manifest، workflow، شناسه برنامه، کلیدها و داده‌ها تغییر نکرده‌اند.
