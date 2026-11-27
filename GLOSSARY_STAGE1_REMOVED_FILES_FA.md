# فایل‌هایی که در مرحله اول باید حذف شوند

این دو فایل عمداً از سورس مرحله اول حذف شده‌اند؛ چون مسیر و منطق آن‌ها در صفحه واحد فرهنگ اصطلاحات ادغام یا کنار گذاشته شده است:

1. `app/src/admin/java/com/example/bookapp/ui/screens/GlossaryManagementScreen.kt`
   - صفحه مدیریتی جداگانه؛ امکانات بررسی و تأیید آن به `GlossaryScreen.kt` مشترک منتقل شده است.

2. `app/src/main/java/com/example/bookapp/data/GlossaryStore.kt`
   - ذخیره‌سازی قدیمی SharedPreferences؛ صفحه جدید مستقیماً از Room استفاده می‌کند.

فهرست ثابت `GLOSSARY_TERMS` نیز از فایل قدیمی `GlossaryScreen.kt` حذف شده است. واژه‌های آزمایشی این فهرست دیگر به‌صورت خودکار نمایش داده نمی‌شوند.
