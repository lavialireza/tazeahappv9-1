# پیاده‌سازی دفتر معین‌البکا در Viewer

این تغییرات روی نسخه کامل Admin دریافت‌شده در `tazeahappv9-1-main(8).zip` اعمال شده‌اند. ساختار اصلی و فایل‌های مدیریتی Admin حفظ شده‌اند؛ فقط کنترل‌های دسترسی Admin و مسیر ارسال بسته رسمی اضافه شده‌اند.

## ۱) دفتر رسمی
- Admin فقط نسخه‌ای را صادر می‌کند که خود دفتر و نسخه انتخابی هر دو وضعیت `PUBLISHED` داشته باشند.
- بسته شامل شناسه ثابت مجلس و دفتر، شماره نسخه، نوبت‌ها، شرکت‌کنندگان، متن دیالوگ‌ها و راهنماهای صحنه است.
- بسته با RSA و کلید خصوصی مختص Admin امضا می‌شود. کلید خصوصی در source set ادمین باقی می‌ماند.
- Viewer امضا را با کلید عمومی بررسی می‌کند، فایل‌های بزرگ‌تر از ۵۰ مگابایت را نمی‌پذیرد و نسخه قدیمی/تکراری را رد می‌کند.
- واردکردن نسخه جدید فقط رکورد همان دفتر را در `director_official.db` جایگزین می‌کند.
- روی جداول دفتر رسمی Triggerهای SQLite نصب می‌شوند تا درج، ویرایش و حذف عادی رد شود. قفل فقط در مسیر Import پس از اعتبارسنجی امضا موقتاً باز و در `finally` دوباره بسته می‌شود.

## ۲) دفتر شخصی
- پایگاه داده شخصی جدا از دفتر رسمی و به‌صورت وابسته به شناسه نصب Viewer نام‌گذاری می‌شود.
- دفتر شخصی با شناسه `personal-UUID` و وضعیت `DRAFT` ایجاد می‌شود.
- روش‌های واقعی ساخت:
  - `ROLE_NUMBERED`: استخراج شماره دیالوگ‌ها از متن نقش‌ها و ادغام دیالوگ‌های هم‌شماره.
  - `FULL_TEXT`: ساخت یک نوبت برای هر نقش و انتقال کامل بخش‌های متنی آن.
  - `MANUAL`: ایجاد دفتر خالی و افزودن دستی نوبت/متن.
  - `SELECTOR`: فهرست دیالوگ‌های متن محلی مجلس و افزودن انتخابی آن‌ها.
- راهنماهای صحنه نسخه رسمی به نسخه شخصی کپی می‌شوند، اما شناسه‌های نوبت در مقصد دوباره نگاشت می‌شوند؛ در روش‌های دستی و انتخاب‌گر، راهنماها به‌صورت عمومی باقی می‌مانند.
- ویرایش متن، راهنما، افزودن نوبت و تغییر ترتیب فقط از Repository دارای بررسی مجوز انجام می‌شود.
- Triggerهای پایگاه داده شخصی، درج یا تغییر وضعیت هر دفتر/نسخه به `PUBLISHED` را رد می‌کنند.

## ۳) مجوزهای Admin
در بخش «مدیریت دسترسی Viewer» یک کارت مستقل «دسترسی دفتر معین‌البکا» برای پروفایل عمومی و هر Viewer خاص اضافه شده است:
- مشاهده دفتر رسمی
- ساخت دفتر شخصی
- شماره‌محور
- متن کامل
- دستی
- انتخاب‌گر
- ویرایش دفتر شخصی
- انتشار رسمی، کپی و خروجی به‌صورت ثابت و غیرفعال

مجوز روش‌های ساخت به‌صورت پیش‌فرض خاموش است. ساخت هر دفتر نیازمند مجوز کلی ساخت و مجوز صریح همان روش است. اگر Admin روش دیگری را فعال نکرده باشد، Viewer آن روش را در فهرست ساخت نمی‌بیند و عملیات داده نیز آن را رد می‌کند.

## ۴) استقلال و محدودیت‌ها
- `director_official.db` و `director_personal_<installation>.db` دو فایل مستقل هستند.
- Import دفتر رسمی هیچ فراخوانی به پایگاه داده شخصی ندارد.
- Viewer هیچ مسیر انتشار، کپی یا خروجی دفتر ندارد؛ پنجره Viewer نیز از قبل با `FLAG_SECURE` محافظت می‌شود.
- محتوای متنی برای روش‌های شماره‌محور، متن کامل و انتخاب‌گر از پایگاه داده محلی کتابخانه Viewer خوانده می‌شود؛ بنابراین استفاده از این روش‌ها آفلاین است، مشروط بر اینکه محتوای همان مجلس در Viewer موجود باشد.

## ۵) فایل‌های اصلی افزوده/تغییریافته
- `app/src/viewer/java/com/example/bookapp/data/ViewerDirectorOfficeDatabase.kt`
- `app/src/viewer/java/com/example/bookapp/data/ViewerDirectorOfficeDao.kt`
- `app/src/viewer/java/com/example/bookapp/data/ViewerDirectorOfficePublicationImporter.kt`
- `app/src/viewer/java/com/example/bookapp/data/ViewerDirectorOfficeRepository.kt`
- `app/src/viewer/java/com/example/bookapp/ui/ViewerDirectorOfficeScreen.kt`
- `app/src/viewer/java/com/example/bookapp/ui/ViewerDirectorOfficeRoutes.kt`
- `app/src/admin/java/com/example/bookapp/data/DirectorOfficePublicationSigner.kt`
- `app/src/admin/java/com/example/bookapp/ui/ViewerDirectorOfficeRoutes.kt` (stub برای حفظ جداسازی Flavor)
- `app/src/admin/java/com/example/bookapp/ui/screens/ViewerAccessManagementScreen.kt`
- `app/src/admin/java/com/example/bookapp/ui/DirectorOfficeScreen.kt`
- `app/src/main/java/com/example/bookapp/data/ViewerAccessPolicy.kt`
- `app/src/main/java/com/example/bookapp/data/DirectorOfficeAccessRules.kt`
- `app/src/main/java/com/example/bookapp/data/DirectorOfficePublicationContract.kt`
- `app/src/main/java/com/example/bookapp/ui/AppNavigation.kt`
- `app/src/main/java/com/example/bookapp/ui/screens/CenterScreens.kt`
- `app/src/main/java/com/example/bookapp/MainActivity.kt`

## وضعیت آزمون
آزمون‌های واحد قواعد دسترسی اضافه شده‌اند. ZIP منبع و بررسی ایستای ساختار انجام می‌شود؛ اجرای Gradle/Android build در محیط تحویل انجام نشده است، چون Gradle Wrapper در پروژه موجود نیست و Gradle/Android SDK نیز در محیط فعلی نصب نیستند. بنابراین موفقیت APK هنوز تأیید نشده است.
