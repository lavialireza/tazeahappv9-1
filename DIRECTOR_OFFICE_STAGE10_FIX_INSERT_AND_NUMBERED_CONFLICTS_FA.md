# مرحله ۱۰ — اصلاح درج نوبت و تعارض شماره‌های همسان

## اصلاحات

1. در درج میانی، وجود نوبت قبلی با همان نقش و همان شماره مقصد دیگر مانع نیست؛ نوبت‌های موجود از شماره مقصد به بعد ابتدا یک واحد افزایش می‌یابند و سپس نوبت تازه در همان جایگاه درج می‌شود.
2. در Viewer، جابه‌جایی داخلی `sequenceIndex` و افزایش شماره‌های نوبت‌های بعدی به‌عنوان بخش داخلی عملیات ایجاد نوبت پذیرفته می‌شود؛ بنابراین برای درج نوبت، مجوز ویرایش جداگانه لازم نیست، ولی همچنان مجوز ایجاد لازم است.
3. در ورود/بازخوانی متن روش شماره‌محور، وجود چند متن متفاوت با شماره یکسان دیگر باعث توقف و شکست ورود نمی‌شود. هر متن مستقل با همان شماره ذخیره و هشدار پایدار ثبت می‌شود؛ متن‌های یکسان همچنان در یک نوبت جمعی ادغام می‌شوند.
4. هشدار شماره‌های همسان و اختلاف متن از سطح خطای مسدودکننده به هشدار قابل بازبینی تغییر کرد. تکرار همان نقش و همان شماره همچنان در کنترل کیفیت به‌عنوان خطا گزارش می‌شود.
5. ویرایش شماره اجازه می‌دهد مدیر شماره‌های متعارض را مرحله‌به‌مرحله اصلاح کند؛ تعارض احتمالی همچنان در کنترل کیفیت گزارش می‌شود.

## فایل‌های تغییرکرده

- `app/src/main/java/com/example/bookapp/data/DirectorOfficeWorkspaceOperations.kt`
- `app/src/main/java/com/example/bookapp/data/DirectorOfficeLibraryRepository.kt`
- `app/src/main/java/com/example/bookapp/data/DirectorOfficeAuditHelpers.kt`
- `app/src/viewer/java/com/example/bookapp/data/ViewerDirectorOfficeMainPageDao.kt`
- `app/src/test/java/com/example/bookapp/data/DirectorOfficeWorkspaceOperationsTest.kt`
- `app/src/test/java/com/example/bookapp/data/DirectorOfficeQualityCheckerTest.kt`

## اعتبارسنجی

ساختار ZIP بررسی می‌شود. اجرای Gradle/JUnit در این محیط در دسترس نیست؛ بنابراین موفقیت بیلد یا اجرای تست‌ها ادعا نمی‌شود.
