# اصلاح خطای Gradle در وابستگی PDFBox (Admin-only)

## خطای مشاهده‌شده

در فایل `app/build.gradle.kts` خط 315، فراخوانی زیر در Kotlin DSL به‌عنوان دسترسی‌گذار شناخته نمی‌شد:

```kotlin
adminImplementation("com.tom-roush:pdfbox-android:2.0.27.0")
```

## اصلاح انجام‌شده

فراخوانی به API عمومی افزودن وابستگی در Gradle تغییر کرد:

```kotlin
add("adminImplementation", "com.tom-roush:pdfbox-android:2.0.27.0")
```

این وابستگی همچنان به پیکربندی `adminImplementation` محدود است و به `implementation` عمومی تغییر داده نشده؛ بنابراین عمداً برای Viewer اضافه نمی‌شود.

## فایل‌های تغییرکرده

- فقط `app/build.gradle.kts`

## موارد حفظ‌شده

- نام و شناسهٔ Flavorهای Admin و Viewer
- تنظیمات signing و کلیدهای Policy
- مسیرهای مستقل assets هر Flavor
- تنظیمات build type و سایر وابستگی‌ها

## محدودیت اعتبارسنجی

در محیط آماده‌سازی این بسته، فرمان `gradle` نصب نیست و پروژه نیز `gradlew` و فایل‌های `gradle/wrapper` را در ZIP ندارد. بنابراین اجرای Gradle و تأیید واقعی بیلد `AdminQa` و `ViewerQa` در این محیط ممکن نبود. اصلاح متنی و وجود تنها یک محل تعریف وابستگی بررسی شد؛ نتیجهٔ بیلد باید در Android Studio یا CI تأیید شود.

## دستورهای آزمون پس از دریافت

از ریشهٔ پروژه اجرا کنید:

```bash
gradle :app:assembleAdminQa :app:assembleViewerQa
```

اگر این دو Variant در پروژه فعال باشند، هر دو باید بدون خطای پیکربندی ساخته شوند. سپس APK Viewer را از نظر نبود کلاس‌های `com.tom_roush.pdfbox` بررسی کنید.
