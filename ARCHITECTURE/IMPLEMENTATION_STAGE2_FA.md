# گزارش پیاده‌سازی مرحله ۲ معماری جدید Tazieh Studio

## هدف
جدا کردن قرارداد و مدل‌های دامنه آرشیو از Room و افزودن گردش‌کار کنترل‌شده برای بررسی اسناد و وضعیت نسخه‌ها. این مرحله بر پایه مرحله ۱ انجام شده و فقط در کپی مستقل پروژه است.

## تغییرات اصلی

### ۱. مرز مستقل Domain
- مدل‌های `ArchiveVersion` و `ArchiveSourceDocument` به لایه Domain افزوده شدند.
- قرارداد `ArchiveRepository` اکنون فقط مدل‌های Domain را دریافت و برمی‌گرداند؛ هیچ Entity از Room از مرز Domain عبور نمی‌کند.
- تبدیل Entity به Domain فقط در `RoomArchiveRepository` انجام می‌شود.
- وضعیت‌ها با enumهای `VersionState`, `ReviewState` و `SourceDocumentType` تعریف شدند.

### ۲. قواعد چرخه نسخه
- `LEGACY_UNREVIEWED` می‌تواند به `DRAFT` یا `REVIEWED` منتقل شود.
- `DRAFT` فقط می‌تواند در حالت پیش‌نویس بماند یا به `REVIEWED` برسد.
- `REVIEWED` می‌تواند منتشر شود یا برای اصلاح به `DRAFT` برگردد.
- `PUBLISHED` نهایی است و به وضعیت قبلی بازنمی‌گردد؛ اصلاح آن باید با ساخت نسخه جدید انجام شود.
- تغییر وضعیت نسخه و بررسی سند در تراکنش Room اجرا می‌شود؛ یادداشت قبلی با ارسال مقدار خالی ناخواسته پاک نمی‌شود.

### ۳. بررسی اسناد
- وضعیت سند از طریق `ReviewSourceDocument` قابل ثبت است: `PENDING_REVIEW`, `ACCEPTED`, `REJECTED`.
- یادداشت بررسی همراه وضعیت ذخیره می‌شود.
- ثبت سند همچنان هش SHA-256 معتبر و مسیر فایل را الزامی می‌داند.

### ۴. مدیریت خطا و لغو عملیات
- Repository عملیات را به `Result` تبدیل می‌کند.
- `CancellationException` کورکورانه بلعیده نمی‌شود و لغو coroutine به فراخواننده منتقل می‌شود.
- اعتبارسنجی نسخه و سند در Domain قرار گرفته تا مستقل از Android قابل آزمون باشد.

## فایل‌های افزوده‌شده
- `domain/usecase/ArchiveValidation.kt`
- `domain/usecase/UpdateArchiveReview.kt`
- `app/src/test/java/com/example/bookapp/domain/usecase/ArchiveValidationTest.kt`

## فایل‌های تغییرکرده
- `domain/model/ArchiveModels.kt`
- `domain/repository/ArchiveRepository.kt`
- `domain/usecase/CreateTaziehVersion.kt`
- `domain/usecase/RegisterSourceDocument.kt`
- `data/archive/RoomArchiveRepository.kt`
- `data/archive/ArchiveDao.kt`
- `app/src/test/java/com/example/bookapp/data/ArchiveRepositoryTest.kt`

## سازگاری و مواردی که عمداً تغییر نکردند
- شماره Room همچنان ۱۱ است؛ در این مرحله ستون یا جدول جدیدی اضافه نشده و migration جدید لازم نیست.
- مهاجرت ۱۰ به ۱۱ و backfill داده‌های قبلی بدون تغییر باقی مانده است.
- ساختار چهار گونه Admin/Viewer/QA، شناسه بسته‌ها، signing، workflow انتشار، URLهای بروزرسانی، قالب `.taz` و سیاست دسترسی Viewer تغییر نکردند.
- ایجاد نسخه جدید هنوز snapshot مستقل از نقش‌ها، بخش‌ها و گفتارها نیست؛ نسخه جدید در این مرحله ظرف نسخه/فراداده است. انتخاب یا فعال‌سازی نسخه در UI نیز عمداً اضافه نشده است تا محتوای نسخه‌ها به‌اشتباه مخلوط نشود.
- فایل‌های Word/PDF هنوز وارد یا OCR نمی‌شوند؛ این مرحله فقط ثبت فراداده سند و وضعیت بررسی را پوشش می‌دهد.

## آزمون و محدودیت
- کدهای مستقل Domain با Kotlin compiler کامپایل شدند.
- اجرای Gradle/Room/Robolectric در این محیط ممکن نبود؛ پروژه فاقد `gradlew` و `gradle/wrapper` است و Gradle در محیط نصب نیست.
- بنابراین این مرحله را build یا test کامل Android اعلام نمی‌کنیم. آزمون‌های Room/Robolectric افزوده/به‌روزشده‌اند اما باید در محیط Android/Gradle اجرا شوند.

## ادامه پیشنهادی
مرحله ۳ باید ساخت snapshot واقعی نسخه را پیاده‌سازی کند: نسخه جدید با کپی مستقل نقش‌ها، بخش‌ها، گفتارها، پاورقی‌ها و پیوندهای منبع؛ همراه تراکنش اتمیک و آزمون شمارش/یکپارچگی. تا تکمیل آن، هیچ API برای فعال‌سازی یا جایگزینی نسخه جاری ارائه نمی‌شود.
