# دفتر معین‌البکاء — مرحله دوم: جداسازی دسترسی کتابخانه از صفحه مشترک

## هدف

این مرحله فقط مسئولیت دسترسی دفتر معین‌البکاء به اطلاعات کتابخانه را از صفحه Compose مشترک جدا می‌کند. هیچ صفحه یا بخش مستقلی برای دفتر رسمی و شخصی اضافه نشده است؛ ساختار فعلی Admin و Viewer و مسیر مشترک آن‌ها حفظ شده است.

## تغییرات

- قرارداد مشترک `DirectorOfficeLibraryRepository` برای خواندن اطلاعات مجلس، زمینه، نقش و بخش اضافه شد.
- پیاده‌سازی Room با نام `RoomDirectorOfficeLibraryRepository` ایجاد شد.
- منطق انتخاب نسخه جاری نقش‌ها و حل نقش بر اساس عنوان از فایل صفحه به Repository منتقل شد.
- منطق واردکردن متن موجود مجلس به نسخه دفتر نیز از فایل صفحه به Repository منتقل شد؛ الگوریتم و ترتیب انتخاب دیالوگ‌ها در این مرحله بازطراحی نشده است.
- `DirectorOfficeSharedMainScreen` دیگر `AppDatabase` دریافت نمی‌کند و در آن فراخوانی مستقیمی به DAOهای کتابخانه وجود ندارد.
- هر دو Wrapper مربوط به Admin و Viewer همان Repository مشترک را با دیتابیس محتوای همان Flavor تزریق می‌کنند.
- قرارداد، صفحه مشترک و Wrapperها تغییر کرده‌اند؛ هیچ فایل مربوط به سایر قابلیت‌های برنامه در این مرحله تغییر نکرده است.

## موارد حفظ‌شده

- ظاهر و رفتار صفحه مشترک عمداً بازطراحی نشده است.
- دسترسی‌های Admin و Viewer و DAO دفتر آن‌ها تغییر نکرده است.
- دیتابیس دفتر، ساختار جداول و Migrationها تغییر نکرده‌اند.
- دفتر رسمی/شخصی به دو صفحه یا دو بخش مجزا تقسیم نشده است.
- منطق واردکردن متن کتابخانه و ثبت نوبت‌ها از نظر رفتاری در این مرحله صرفاً جابه‌جایی مسئولیت بوده است.

## فایل‌های تغییرکرده

1. `app/src/main/java/com/example/bookapp/data/DirectorOfficeLibraryRepository.kt` — فایل جدید
2. `app/src/main/java/com/example/bookapp/ui/DirectorOfficeSharedMainScreen.kt`
3. `app/src/admin/java/com/example/bookapp/ui/DirectorOfficeScreen.kt`
4. `app/src/viewer/java/com/example/bookapp/ui/ViewerDirectorOfficeScreen.kt`

## اعتبارسنجی

- ارجاعات باقی‌مانده به `libraryDb` و `AppDatabase` در `DirectorOfficeSharedMainScreen.kt` بررسی شد و موردی باقی نمانده است.
- همه محل‌های فراخوانی صفحه مشترک بررسی شدند و هر دو Wrapper، Repository را تزریق می‌کنند.
- ساختار ZIP و سلامت فایل‌های فشرده بررسی می‌شود.
- Gradle/Android SDK در محیط تولید این بسته در دسترس نبود؛ بنابراین این مرحله هنوز Build یا تست اجرایی موفق اعلام نمی‌شود. پیش از ادغام نهایی باید هر چهار Variant در GitHub Actions ساخته شوند.
