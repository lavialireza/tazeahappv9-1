# گزارش تغییرات مرحله سوم

# مرحله سوم معماری Tazieh Studio — شاخه‌سازی مستقل نسخه‌ها

## هدف
ایجاد نسخه جدید از یک نسخه موجود به‌صورت مستقل، اتمیک و قابل ردیابی؛ بدون تغییر متن نسخه مبدأ و بدون دست‌کاری اطلاعات شخصی کاربر.

## تغییرات پیاده‌سازی‌شده
1. افزودن `parentVersionUid` به `TaziehVersionEntity` برای ثبت رابطه نسخه فرزند و مبدأ.
2. افزایش Room schema از 11 به 12 و افزودن مهاجرت `MIGRATION_11_12` بدون حذف یا بازنویسی داده‌های قبلی.
3. افزودن `ForkTaziehVersion`، `VersionForkReport` و قرارداد `forkVersion` به Domain.
4. افزودن `ForkTaziehVersionUseCase` و اعتبارسنجی شناسه مبدأ، عنوان و طول فیلدها.
5. پیاده‌سازی شاخه‌سازی در `RoomArchiveRepository` درون `database.withTransaction`.
6. ساخت شناسه‌های جدید برای نقش‌ها، بخش‌ها، پاورقی‌ها، دیالوگ‌ها و نوبت‌های دیالوگ؛ کلیدهای اصلی Room نیز با درج جدید تولید می‌شوند.
7. انتقال ارتباط دیالوگ‌ها با بخش‌های نسخه جدید و ثبت `versionUid` برای دیالوگ‌های جدید.
8. کپی فراداده اسناد منبع به نسخه جدید با وضعیت «در انتظار بررسی»؛ فایل اصلی با همان مسیر و SHA-256 حفظ می‌شود و بایت‌های فایل تکثیر نمی‌شوند.
9. نشان‌گذاری نسخه فرزند به‌عنوان نسخه جاری در همان تراکنش.
10. عدم کپی نشانک‌ها، سابقه مطالعه، یادداشت‌های شخصی و سایر داده‌های کاربر.
11. تصاویر تعزیه در مدل فعلی دارایی سطح تعزیه هستند؛ عمداً تکثیر نمی‌شوند تا در گالری تصاویر تکراری ایجاد نشود. تعداد تصاویر مشترک در گزارش نتیجه مشخص می‌شود.

## رفتار تراکنش و بازگشت
تمام عملیات از خواندن نسخه مبدأ تا درج نسخه فرزند، نقش‌ها، بخش‌ها، پاورقی‌ها، دیالوگ‌ها، نوبت‌ها و فراداده اسناد، در یک `withTransaction` قرار دارد. هر خطا باعث rollback کامل می‌شود. ابتدا نسخه جاری قبلی از نشان جاری خارج می‌شود، اما این تغییر نیز داخل همان تراکنش است و در صورت شکست برمی‌گردد.

## سازگاری با داده‌های قدیمی
در مهاجرت 11 به 12:
- ستون `parentVersionUid` به جدول نسخه‌ها اضافه می‌شود.
- ستون `versionUid` به جدول دیالوگ‌ها اضافه می‌شود.
- ارتباط دیالوگ‌های قدیمی تا حد امکان از روی نوبت‌های متصل به بخش و نسخه نقش تعیین می‌شود.
- داده‌ای حذف نمی‌شود.
- برای دیالوگ‌های قدیمی که `versionUid` خالی دارند، شاخه‌سازی مالکیت را از روی بخش‌های متصل تشخیص می‌دهد.

## آزمون‌ها و محدودیت تأیید
- آزمون‌های واحد اعتبارسنجی شاخه‌سازی افزوده شده‌اند.
- بررسی ایستای مسیرهای Room، قرارداد Repository، نسخه مهاجرت و اتصال مدل‌ها انجام شده است.
- در محیط فعلی `gradle`، `kotlinc` و `gradlew` موجود نیستند؛ بنابراین Android Gradle Build، آزمون Instrumentation و اجرای واقعی مهاجرت Room هنوز تأیید نشده‌اند.
- قبل از انتشار باید Build کامل چهار variant، مهاجرت واقعی DB نسخه 11 به 12، شکست عمدی در میانه تراکنش و مقایسه تعداد/متن رکوردهای مبدأ و فرزند روی دستگاه آزمایش شود.

## فایل‌های تغییرکرده
- `app/src/main/java/com/example/bookapp/data/AppDatabase.kt`
- `app/src/main/java/com/example/bookapp/data/Entities.kt`
- `app/src/main/java/com/example/bookapp/data/Dao.kt`
- `app/src/main/java/com/example/bookapp/data/archive/ArchiveEntities.kt`
- `app/src/main/java/com/example/bookapp/data/archive/ArchiveDao.kt`
- `app/src/main/java/com/example/bookapp/data/archive/RoomArchiveRepository.kt`
- `app/src/main/java/com/example/bookapp/domain/model/ArchiveModels.kt`
- `app/src/main/java/com/example/bookapp/domain/repository/ArchiveRepository.kt`
- `app/src/main/java/com/example/bookapp/domain/usecase/ArchiveValidation.kt`
- `app/src/main/java/com/example/bookapp/domain/usecase/ForkTaziehVersion.kt`
- `app/src/test/java/com/example/bookapp/domain/usecase/ForkVersionValidationTest.kt`

## حفاظت از محدوده
شناسه‌های package، کلید امضای انتشار، Secrets، workflow انتشار، URLهای بروزرسانی، سیاست Viewer و فایل‌های محتوای اصلی تغییر داده نشده‌اند.
