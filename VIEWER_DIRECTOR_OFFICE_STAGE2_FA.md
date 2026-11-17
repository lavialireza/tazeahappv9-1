# مرحله دوم: اتصال دفتر معین‌البکا در Viewer به دیتابیس‌های مستقل

## موارد انجام‌شده

- صفحه اختصاصی `ViewerDirectorOfficeScreen` فقط در source set نسخه Viewer اضافه شد؛ صفحه مدیریتی Admin وارد APK Viewer نمی‌شود.
- مسیرهای ورود به دفتر در کتابخانه، مرکز اجرا و صفحه نقش‌های مجلس بر اساس مجوزهای `director.official.view`، `director.personal.create` و `director.personal.edit` فعال می‌شوند.
- فهرست دفتر رسمی و نسخه‌های منتشرشده از `director_official.db` خوانده می‌شود. DAO رسمی فقط Query دارد و هیچ عملیات نوشتن در آن تعریف نشده است.
- فهرست دفترهای شخصی، نسخه‌ها، نوبت‌ها، متن دیالوگ‌ها و راهنماهای صحنه از `director_personal.db` خوانده می‌شود.
- ایجاد دفتر شخصی دستی و افزودن نوبت به‌همراه متن، فقط با مجوزهای لازم و در فضای شخصی انجام می‌شود.
- صفحه رسمی فقط‌خواندنی است. در Viewer هیچ گزینه انتشار رسمی، کپی یا خروجی گرفتن اضافه نشده است.
- دکمه ورود به دفتر در صفحه نقش‌های تعزیه برای Viewer نیز، در صورت داشتن مجوز، قابل مشاهده است.

## محدودیت باقی‌مانده

دیتابیس‌های Admin و Viewer به دلیل نصب جداگانه APKها به‌صورت خودکار مشترک نیستند. این مرحله صفحه Viewer را به دیتابیس‌های محلی خودش متصل می‌کند؛ بنابراین برای نمایش دفتر رسمی باید مرحله انتقال امن و امضاشده از Admin به `director_official.db` نیز اجرا شود. تا قبل از آن، اگر دیتابیس رسمی خالی باشد، صفحه پیام «هنوز نسخه رسمی ... منتقل نشده است» نشان می‌دهد.

روش‌های ساخت شماره‌محور، متن کامل و انتخاب‌گر در این مرحله به‌عنوان مجوزهای مستقل حفظ شده‌اند؛ رابط ساخت دستی در این صفحه فعال است و اتصال رابط ساخت سایر روش‌ها مرحله بعدی کار خواهد بود.

## فایل‌های تغییر یافته

- `app/src/main/java/com/example/bookapp/data/DirectorOfficeDatabase.kt`
- `app/src/main/java/com/example/bookapp/ui/AppNavigation.kt`
- `app/src/main/java/com/example/bookapp/ui/screens/CenterScreens.kt`
- `app/src/main/java/com/example/bookapp/ui/screens/ProfessionalRoleScreen.kt`
- `app/src/viewer/java/com/example/bookapp/ui/ViewerDirectorOfficeScreen.kt` (جدید)
- `app/src/viewer/java/com/example/bookapp/ui/ViewerDirectorOfficeRoutes.kt` (جدید)
- `app/src/admin/java/com/example/bookapp/ui/ViewerDirectorOfficeRoutes.kt` (تابع خالی برای حفظ جداسازی Flavor)
