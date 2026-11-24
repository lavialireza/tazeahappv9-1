# چک‌لیست امنیتی دو مخزن

- [ ] هیچ `*.jks` یا `*.keystore` در Git وجود ندارد.
- [ ] `RELEASE_KEYSTORE_BASE64` فقط در GitHub Secrets هر دو مخزن ثبت شده است.
- [ ] Passwordهای Keystore فقط در Secrets هستند.
- [ ] `UPDATES_REPO_TOKEN` فقط دسترسی لازم به Update Repository دارد.
- [ ] `ADMIN_POLICY_PRIVATE_KEY` در فایل‌های پروژه commit نشده است.
- [ ] Workflowهای قدیمی با `gh release create` و `GITHUB_RUN_NUMBER` برای Debug حذف شده‌اند.
- [ ] Debug workflow فقط `contents: read` دارد.
- [ ] Release واقعی دستی اجرا می‌شود.
- [ ] Release workflow هم‌زمانی انتشار را قفل می‌کند.
- [ ] نسخه آفلاین Keystore اصلی خارج از GitHub نگهداری می‌شود.

## بررسی سریع قبل از Push

```bash
git status --short
git ls-files | grep -Ei '\.(jks|keystore|pem|p12|key)$' || true
git grep -nEi 'password|private.?key|BEGIN (RSA |EC |OPENSSH )?PRIVATE KEY' -- ':!*.md' ':!*.txt' || true
```

وجود نام Secret در Workflow طبیعی است؛ وجود مقدار واقعی Secret در سورس طبیعی و مجاز نیست.
