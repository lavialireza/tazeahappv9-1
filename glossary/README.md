# ماژول مستقل واژه‌نامه تعزیه (`:glossary`)

این ماژول لایه داده و مدل‌های اصلی واژه‌نامه را نگهداری می‌کند و به‌صورت یک Android Library در هر دو flavor برنامه (Admin و Viewer) استفاده می‌شود.

## مرحله ۱ انجام‌شده
- انتقال `GlossaryEntities.kt` به این ماژول
- انتقال `GlossaryDao.kt` به این ماژول
- حفظ package فعلی `com.example.bookapp.data` برای جلوگیری از تغییر گسترده importها و ارجاع‌های موجود
- اتصال ماژول به `:app` از طریق Gradle
- حفظ مالکیت نمونه پایگاه داده، فایل `bookapp.db` و migrationها در ماژول برنامه

## مواردی که عمداً در این مرحله تغییر نکرده‌اند
- `AppDatabase.kt` و شماره نسخه/مهاجرت‌های Room
- صفحه‌های فعلی دیکشنری و مدیریت دیکشنری
- `GlossaryStore` و داده‌های SharedPreferences قدیمی
- موتور پیوند واژه به متن و ناوبری
- ساختار دفتر معین‌البکاء و سایر بخش‌های برنامه

این موارد باید در مراحل بعدی با مهاجرت داده و آزمون سازگاری یکپارچه شوند؛ حذف یا جایگزینی ناگهانی آن‌ها مجاز نیست.

## مراحل بعدی
1. افزودن لایه repository و سرویس نرمال‌سازی مشترک
2. پیاده‌سازی ورود اولیه و ادغام افزایشی فایل‌های JSON
3. ثبت سابقه import و جلوگیری از اعمال دوباره یک فایل
4. خروجی نسخه‌دار فقط از مدخل‌ها/معنی‌های تأییدشده
5. اتصال لمس واژه به مدخل دیکشنری در متن
6. بسته‌بندی snapshot دیکشنری در چهار خروجی Admin/Viewer آزمایشی و انتشار

## Stage 2 — shared normalization and repository

- `GlossaryNormalizer` provides the shared Persian/Arabic canonicalization used by the Admin management screen, the legacy local glossary store, and the offset-preserving text linker.
- `GlossaryRepository.addSuggestion` is the Admin-only transactional write path (`glossary/src/admin`). It creates a term only when its normalized headword does not exist, adds meanings without overwriting existing meanings, rejects conflicting spellings, and records each operation in glossary history. The Viewer flavor does not package this write repository.
- New terms and meanings remain `PENDING`; this stage does not publish or approve them.
- The repository is intentionally storage-focused. JSON package import/export, import fingerprints, and release snapshots are reserved for the next stage.
