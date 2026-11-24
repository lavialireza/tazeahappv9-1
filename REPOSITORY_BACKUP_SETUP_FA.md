# راه‌اندازی مخزن اصلی و مخزن پشتیبان مستقل

این پروژه برای دو مخزن مستقل طراحی شده است:

- مخزن اصلی: محل توسعه و انتشار معمول
- مخزن پشتیبان: کپی مستقل که در صورت از دسترس خارج شدن مخزن اصلی، بدون وابستگی به آن می‌تواند APK Release تولید کند.

## اصل امنیتی

**هیچ فایل keystore، password، token یا private key نباید داخل Git commit شود.**

هر دو مخزن باید Secretهای یکسانِ مربوط به کلید Release را در تنظیمات GitHub خود داشته باشند. خود فایل کلید داخل هیچ‌کدام از مخزن‌ها قرار نمی‌گیرد.

## Secretهای لازم در هر دو مخزن

### امضای APK Release

- `RELEASE_KEYSTORE_BASE64`
- `RELEASE_STORE_PASSWORD`
- `RELEASE_KEY_ALIAS`
- `RELEASE_KEY_PASSWORD`

این چهار مقدار باید به همان Release Keystore اصلی مربوط باشند تا APK مخزن پشتیبان بتواند روی نسخه نصب‌شده قبلی update شود.

### در صورت استفاده از کلید جداگانه Viewer

- `RELEASE_VIEWER_KEYSTORE_BASE64`
- `RELEASE_VIEWER_STORE_PASSWORD`
- `RELEASE_VIEWER_KEY_ALIAS`
- `RELEASE_VIEWER_KEY_PASSWORD`

اگر این Secretها تنظیم نشوند، Viewer طبق رفتار سازگار با نسخه قبلی با کلید Release اصلی امضا می‌شود.

### امضای Policy فعلی Admin

- `ADMIN_POLICY_PRIVATE_KEY`

**هشدار:** این کلید در معماری فعلی هنگام Build به Admin APK وارد می‌شود. بنابراین Secret بودن آن در GitHub جلوی مشاهده آن در مخزن را می‌گیرد، اما جلوی استخراج آن از APK Admin را نمی‌گیرد. برای امنیت بسیار بالا باید در آینده امضای Policy به سرویس/کیف خارج از APK منتقل شود.

## Variables لازم در هر دو مخزن

- `POLICY_PUBLIC_KEY`
- `UPDATES_REPO`
- `UPDATE_BASE_URL_ADMIN`
- `UPDATE_BASE_URL_VIEWER`

`UPDATES_REPO` می‌تواند همان مخزن انتشار فایل‌های update باشد و در هر دو مخزن یکسان تنظیم شود.

## توکن انتشار Update Repository

در هر دو مخزن:

- `UPDATES_REPO_TOKEN`

از Fine-grained Personal Access Token استفاده کنید که فقط روی مخزن Update Repository دسترسی `Contents: Read and write` داشته باشد. برای سورس برنامه دسترسی اضافه ندهید.

## نتیجه

ساختار نهایی:

```text
Main Repository
  ├─ Source code
  ├─ Workflows
  └─ GitHub Secrets
       └─ Release signing key

Backup Repository
  ├─ Source code (independent copy)
  ├─ Same Workflows
  └─ GitHub Secrets
       └─ Same Release signing key

Update Repository
  ├─ admin/*.apk
  ├─ admin/update.json
  ├─ user/*.apk
  └─ user/update.json
```

اگر Main Repository از دسترس خارج شود، Backup Repository می‌تواند Workflow `Release and Publish Update` را به صورت دستی اجرا کند و APKهای Release را با همان کلید امضا کند.

## اجرای Release

در هر دو مخزن:

`Actions` → `Release and Publish Update` → `Run workflow`

Release واقعی فقط با اجرای دستی Workflow انجام می‌شود.

## جلوگیری از خطای Release تکراری

Workflow انتشار Debug دیگر GitHub Release نمی‌سازد و فقط Artifact ایجاد می‌کند.

بنابراین Tagهای قدیمی `apk-build-N` باعث شکست Buildهای Debug نمی‌شوند.

انتشار واقعی نیز فقط از Workflow مخصوص Release انجام می‌شود و با `concurrency` از انتشار هم‌زمان جلوگیری می‌شود.

## Backup آفلاین کلید

حداقل یک نسخه رمزگذاری‌شده از Release Keystore را خارج از GitHub نگهداری کنید. اگر Keystore اصلی از بین برود، APK جدید نمی‌تواند با همان امضا تولید شود و update روی نسخه‌های نصب‌شده قبلی ممکن است غیرممکن شود.
