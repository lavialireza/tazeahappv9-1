# معماری هسته مشترک دفتر معین‌البکاء — وضعیت پیاده‌سازی

این سند فقط مربوط به معماری مشترک دفتر معین‌البکاء بین Admin و Viewer است و با مراحل آرشیو نسخه‌های تعزیه در `IMPLEMENTATION_STAGE*.md` اشتباه نشود.

## هدف قطعی

رابط و عملیات عمومی دفتر معین‌البکاء یک بار در `app/src/main` توسعه داده می‌شوند. Admin و Viewer از همان هسته استفاده می‌کنند، اما:

- Admin دیتابیس و اختیارات مدیریتی خودش را حفظ می‌کند.
- Viewer دیتابیس شخصی مستقل و دیتابیس رسمی فقط‌خواندنی مستقل دارد.
- مجوزهای Viewer از سیاست امضاشده Admin می‌آیند و در Adapter و عملیات حساس Viewer دوباره بررسی می‌شوند.
- انتشار، امضا، مدیریت کاربران و جایگزینی دفتر رسمی در سورس Flavor مربوط به Admin می‌ماند و نباید به هسته مشترک یا APK Viewer منتقل شود.

## وضعیت مبنا در فایل ارسالی کاربر

### مرحله ۱ — قرارداد و اجزای پایه مشترک (در فایل مبنا موجود بود)

- `DirectorOfficeSharedContract.kt`: چهار روش `ROLE_NUMBERED`, `FULL_TEXT`, `MANUAL`, `SELECTOR` و کلیدهای مجوز.
- `DirectorOfficeAccessRules.kt`: قواعد مشاهده دفتر رسمی، ساخت و ویرایش دفتر شخصی و ممنوعیت قطعی انتشار/کپی/خروجی در Viewer.
- `DirectorOfficeSharedUi.kt`: فیلد جستجو، فرم نوبت، فیلد متن دیالوگ و فرم راهنمای صحنه.

### مرحله ۲ — قرارداد داده و قواعد دامنه مشترک (در فایل مبنا موجود بود)

- `DirectorOfficeWorkspaceDao.kt`: قرارداد عملیات عمومی DAO که Admin و Viewer هرکدام با DAO دیتابیس خود پیاده می‌کنند.
- `DirectorOfficeWorkspaceUseCases.kt`: نرمال‌سازی ارقام، جداسازی و استخراج دیالوگ‌ها، تعیین ترتیب و کنترل پیش‌نویس.
- `ViewerDirectorOfficeRepository.kt`: Adapter اختصاصی Viewer که مجوز را بررسی می‌کند.
- `ViewerDirectorOfficeDatabase.kt` و `DirectorOfficeDatabase.kt`: پایگاه داده‌های مستقل باقی می‌مانند.

## مرحله ۳ — انتقال عملیات عمومی به هسته مشترک (در این تحویل)

### فایل‌های افزوده‌شده

- `app/src/main/java/com/example/bookapp/data/DirectorOfficeWorkspaceOperations.kt`
- `app/src/test/java/com/example/bookapp/data/DirectorOfficeWorkspaceOperationsTest.kt`

### فایل‌های تغییرکرده

- `app/src/main/java/com/example/bookapp/data/DirectorOfficeWorkspaceDao.kt`
- `app/src/main/java/com/example/bookapp/data/DirectorDao.kt`
- `app/src/viewer/java/com/example/bookapp/data/ViewerDirectorOfficeDao.kt`
- `app/src/viewer/java/com/example/bookapp/data/ViewerDirectorOfficeRepository.kt`
- `app/src/main/java/com/example/bookapp/ui/DirectorOfficeSharedUi.kt`
- `app/src/admin/java/com/example/bookapp/ui/DirectorOfficeScreen.kt`
- `app/src/viewer/java/com/example/bookapp/ui/ViewerDirectorOfficeScreen.kt`

### عملیات منتقل‌شده به هسته مشترک

- ویرایش متن دیالوگ
- ویرایش شماره و عنوان نوبت
- افزودن دیالوگ به نوبت
- ویرایش راهنمای صحنه
- جابه‌جایی اتمیک ترتیب نوبت‌ها با شاخص موقت برای جلوگیری از برخورد کلید یکتا
- افزودن نوبت دستی
- افزودن نوبت متصل به نقش موجود
- افزودن دیالوگ با روش انتخاب‌گر
- افزودن راهنمای صحنه

هسته مشترک فقط `DirectorOfficeWorkspaceDao` را می‌شناسد و به `Context`، دیتابیس Admin/Viewer یا flavor وابسته نیست. مرز تراکنش را هر Flavor از دیتابیس مستقل خودش فراهم می‌کند.

### اتصال Admin و Viewer

- Repository در Viewer اکنون فقط Adapter مجوز و تراکنش است و عملیات عمومی را به `DirectorOfficeWorkspaceOperations` می‌سپارد.
- برای Admin، ویرایش یک نسخه پیش‌نویس جدید حتی وقتی دفتر مادر قبلاً نسخه منتشرشده دارد مجاز است؛ خود نسخه منتشرشده همچنان قفل می‌ماند. در Viewer، کنترل سخت‌گیرانه پیش‌نویس دفتر شخصی حفظ شده است.
- عملیات ساخت نوبت دستی متصل به نقش، انتخاب دیالوگ، افزودن راهنمای صحنه، ویرایش شماره نوبت و جابه‌جایی نوبت در صفحه Admin نیز به عملیات مشترک متصل شده‌اند.
- انتخاب‌گر چهار روش ساخت دفتر اکنون از `DirectorOfficeMethodOption` مشترک استفاده می‌کند.
- جستجو و فرم‌های نوبت، دیالوگ و راهنمای صحنه که از قبل مشترک بودند، حفظ شده‌اند.

## استقلال و امنیت حفظ‌شده

- دیتابیس رسمی Viewer و دیتابیس شخصی Viewer همچنان جدا هستند.
- Adapter Viewer پیش از هر عملیات ویرایشی مجوز ویرایش و در روش‌های مربوط، مجوز روش ساخت را بررسی می‌کند.
- قواعد مشترک اجازه انتشار، کپی یا خروجی رسمی در Viewer نمی‌دهند.
- فایل‌های امضا، انتشار رسمی و مدیریت کاربران در مسیر `app/src/admin` باقی مانده‌اند.
- هیچ تغییر schema یا migration دیتابیس در این مرحله انجام نشده است.

## آزمون و محدودیت تأیید

- کد هسته عملیات، قرارداد DAO و Repository Viewer با Kotlin compiler و stubهای حداقلی Android/Room بررسی نوعی شدند.
- امضای فراخوانی‌های مشترک از سمت Admin با یک قطعه آزمون مستقل کامپایل شد.
- بررسی نحوی فایل‌های Compose انجام شد؛ به علت نبود وابستگی‌های Android/Compose در محیط، این بررسی معادل کامپایل کامل Compose نیست.
- Gradle و Gradle Wrapper در محیط حاضر موجود نیستند؛ بنابراین Build کامل APKهای Admin و Viewer و اجرای آزمون‌های JUnit/Room انجام نشده است.

## مرحله بعد — استخراج واقعی UI و State مشترک

این مرحله هنوز کامل نشده است. دو صفحه اصلی همچنان flavor-specific هستند و بخش‌های زیادی از منطق State و UI در آن‌ها تکرار یا جدا نگهداری می‌شود:

- `app/src/admin/java/com/example/bookapp/ui/DirectorOfficeScreen.kt`
- `app/src/viewer/java/com/example/bookapp/ui/ViewerDirectorOfficeScreen.kt`

مرحله بعد باید به‌صورت کنترل‌شده انجام شود:

1. استخراج مدل صفحه و State مشترک دفتر به ViewModel/StateHolder در `app/src/main`.
2. ساخت اجزای مشترک فهرست نوبت، کارت دیالوگ، کارت راهنمای صحنه، ویرایشگر و مرتب‌سازی.
3. تبدیل صفحه Admin و Viewer به Hostهای باریک که Repository/Policy خود را تزریق می‌کنند.
4. نگه‌داشتن قابلیت‌های مختص Admin مانند انتشار، مدیریت کاربران و ورود منابع در Host/ماژول Admin.
5. نگه‌داشتن دفتر رسمی فقط‌خواندنی و دیتابیس شخصی مجزا در Host مربوط به Viewer.
6. حذف کد تکراری فقط پس از برابری رفتاری و آزمون هر دو Flavor.

تا قبل از اتمام این مرحله، نباید ادعا شود که کل دو صفحه ۱۹۳۱ و ۶۴۰ خطی به یک صفحه مشترک تبدیل شده‌اند.
