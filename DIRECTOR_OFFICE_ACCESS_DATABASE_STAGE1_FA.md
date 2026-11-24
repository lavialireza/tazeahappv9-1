# مرحله اول: مدل دسترسی و جداسازی دیتابیس دفتر معین‌البکا

## فایل‌های افزوده/تغییریافته
- `app/src/main/java/com/example/bookapp/data/DirectorOfficeDatabase.kt` (جدید)
- `app/src/main/java/com/example/bookapp/data/ViewerAccessPolicy.kt` (افزودن مجوزهای دفتر)

## مدل ذخیره‌سازی Viewer
Viewer دو فایل مستقل دارد:
- `director_official.db`: دفتر رسمی منتشرشده؛ تنها DAO آن متدهای خواندن دارد و هیچ INSERT/UPDATE/DELETE در API رسمی وجود ندارد.
- `director_personal.db`: دفتر شخصی همان نصب Viewer؛ نوشتن فقط از مسیر `ViewerDirectorOfficeStore` و پس از بررسی مجوز انجام می‌شود.

این دو فایل جدول‌ها و شناسه‌های SQLite مستقل دارند. تغییر یا پاک‌کردن دفتر رسمی از طریق مسیر دفتر شخصی انجام نمی‌شود و برعکس.

## مجوزهای افزودهشده به مدیریت دسترسی Admin
- `director.official.view`
- `director.personal.create`
- `director.personal.edit`
- `director.build.roleNumbered`
- `director.build.fullText`
- `director.build.manual`
- `director.build.selector`

تمام مجوزهای دفتر به‌صورت پیش‌فرض خاموش‌اند. روش‌های ساخت، فرزند مجوز ساخت دفتر شخصی هستند؛ بنابراین روشن‌کردن روش به‌تنهایی بدون مجوز ساخت دفتر شخصی کافی نیست.

## محدودیت‌های قطعی Viewer
در `ViewerDirectorOfficeStore` هیچ API برای انتشار رسمی، کپی یا خروجی گرفتن تعریف نشده است. DAO دفتر رسمی فقط خواندنی است. ایجاد/ویرایش دفتر شخصی فقط در فایل شخصی انجام می‌شود و وضعیت آن به DRAFT محدود می‌ماند.

## نکته درباره مرحله بعد
این مرحله زیرساخت ذخیره‌سازی و مجوزها را آماده می‌کند. هنوز رابط کاربری دفتر در Viewer و کانال انتقال امن نسخه رسمی از Admin به فایل `director_official.db` متصل نشده است؛ بنابراین در این مرحله دفتر رسمی به‌صورت خودکار از Admin به Viewer منتقل نمی‌شود. این اتصال باید در مرحله بعد با بسته رسمی امضاشده و بدون قابلیت خروجی‌گیری برای کاربر انجام شود.

## سازگاری و آزمون
نسخه دیتابیس اصلی `AppDatabase` و Migrationهای قبلی دست‌نخورده مانده‌اند؛ داده‌های فعلی Admin حذف یا جابه‌جا نشده‌اند. در محیط فعلی Gradle Wrapper در پروژه موجود نیست و فرمان `gradle` نیز نصب نیست؛ بنابراین بیلد Android در این محیط اجرا/تأیید نشده است.
