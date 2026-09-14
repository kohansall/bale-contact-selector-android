# راهنمای انتشار نسخه

## قاعدهٔ نسخه

نسخه‌ها با قالب `MAJOR.MINOR.PATCH` و در دورهٔ آزمایشی با پسوند `-beta` منتشر می‌شوند. `versionCode` همیشه باید افزایش یابد و `versionName` باید با Tag بدون حرف `v` یکسان باشد.

## شاخه و Pull Request

1. شاخه‌ای با نام `feature/<موضوع>` یا `fix/<موضوع>` بسازید.
2. تغییر، تست و `CHANGELOG.md` را در همان شاخه کامل کنید.
3. Pull Request به `main` باز کنید.
4. فقط پس از سبز شدن CI ادغام کنید.

Fork برای توسعهٔ همین مخزن لازم نیست؛ Fork برای مشارکت‌کننده‌ای مناسب است که دسترسی نوشتن به مخزن ندارد.

## Secrets لازم

در Settings → Secrets and variables → Actions این چهار Secret را ثبت کنید:

- `ANDROID_SIGNING_KEYSTORE_BASE64`: محتوای Base64 کلید اصلی نسخه‌های قبلی
- `ANDROID_SIGNING_STORE_PASSWORD`: رمز Keystore
- `ANDROID_SIGNING_KEY_PASSWORD`: رمز کلید
- `ANDROID_SIGNING_KEY_ALIAS`: نام Alias

کلید اصلی، رمزها یا فایل `keystore` نباید Commit شوند.

## انتشار

1. نسخه و Release Notes را به‌روزرسانی و PR را Merge کنید.
2. Tag مطابق نسخه بسازید؛ برای مثال `v0.3.1-beta`.
3. Workflow انتشار APK را می‌سازد، گواهی امضا را بررسی می‌کند و APK و SHA-256 را در GitHub Release می‌گذارد.
4. APK منتشرشده را روی گوشی دارای نسخهٔ قبلی به‌صورت Update آزمایش کنید.

اثر انگشت SHA-256 گواهی سازگار فعلی:

```text
d121e435a2e9b3f0ef500ee8e15437a12439899de6683baa4d829643c295c90f
```
