# اصلاح امنیتی و پایداری انتشار

- رفع خطای `a release with the same tag name already exists`.
- استفاده از `GITHUB_RUN_ID` برای Tag یکتا.
- انتشار idempotent در rerun همان workflow با `gh release upload --clobber`.
- جلوگیری از انتشار هم‌زمان با GitHub Actions concurrency.
- تعیین `--target $GITHUB_SHA` هنگام ایجاد Release.
- حذف کلید خصوصی Debug از مخزن و حذف رمز hard-coded آن از Gradle.
- استفاده از debug keystore استاندارد Gradle برای Buildهای Debug.
- کلیدهای Release همچنان فقط از GitHub Secrets خوانده می‌شوند.

## نکته مهم
`ADMIN_POLICY_PRIVATE_KEY` در معماری فعلی برای امضای Policy در Admin استفاده می‌شود و در BuildConfig قرار می‌گیرد؛ بنابراین برای امنیت در برابر استخراج APK، این کلید باید در یک سرویس امضای خارج از APK قرار گیرد. این مورد در این Patch تغییر داده نشده تا منطق فعلی Policy/Viewer خراب نشود.
