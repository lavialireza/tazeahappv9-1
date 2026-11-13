# رفع خطای 404 در بررسی به‌روزرسانی — بررسی پروژه ارسالی

علت پیدا شده: فایل `app/build.gradle.kts` آدرس‌های پیش‌فرض `https://example.com/tazieh/admin` و `https://example.com/tazieh/user` دارد. Workflow انتشار قبلی آدرس واقعی مخزن را فقط برای تولید `update.json` استفاده می‌کرد و آن را هنگام ساخت APK به Gradle نمی‌داد. بنابراین APK می‌توانست Manifest را از `example.com` بخواهد و 404 بگیرد.

## تغییر انجام‌شده
- مرحله ساخت Admin اکنون `UPDATE_BASE_URL_ADMIN` را با `-PUPDATE_SERVER_ADMIN_URLS` به Gradle می‌دهد.
- مرحله ساخت Viewer اکنون `UPDATE_BASE_URL_VIEWER` را با `-PUPDATE_SERVER_VIEWER_URLS` به Gradle می‌دهد.
- چون این مقادیر Gradle property هستند، بر تنظیمات نمونه `update-servers.properties` اولویت دارند.
- منطق SHA-256، اعتبارسنجی HTTPS، شناسه بسته‌ها و منع downgrade دست‌نخورده باقی مانده است.

## مقادیر GitHub Variables مورد انتظار
`UPDATE_BASE_URL_ADMIN=https://raw.githubusercontent.com/lavialireza/tazieh-updates/main/admin`

`UPDATE_BASE_URL_VIEWER=https://raw.githubusercontent.com/lavialireza/tazieh-updates/main/user`

پس از commit کردن تغییر، باید workflow با موفقیت اجرا شود و APKهای جدید ساخته‌شده را روی گوشی نصب کنید. APKهای قبلی داخل گوشی آدرس اشتباه را دارند و تغییر Workflow به‌تنهایی آن APKهای از قبل ساخته‌شده را اصلاح نمی‌کند.

## محدودیت بررسی
این اصلاح بر اساس بررسی کد و فایل ZIP انجام شده است؛ در این محیط build واقعی Android/Gradle و تست روی گوشی اجرا نشده است.
