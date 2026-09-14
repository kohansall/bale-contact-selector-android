# امضای نسخه‌ها

هیچ فایل Keystore یا گذرواژه‌ای در این مخزن نگهداری نمی‌شود. برای اینکه نسخهٔ جدید روی نسخه‌های قبلی نصب شود، Workflow انتشار باید با همان کلید اصلی امضا شود.

Secrets موردنیاز:

- `ANDROID_SIGNING_KEYSTORE_BASE64`
- `ANDROID_SIGNING_STORE_PASSWORD`
- `ANDROID_SIGNING_KEY_PASSWORD`
- `ANDROID_SIGNING_KEY_ALIAS`

اثر انگشت SHA-256 گواهی سازگار:

```text
d121e435a2e9b3f0ef500ee8e15437a12439899de6683baa4d829643c295c90f
```

گم‌شدن یا تغییر کلید باعث می‌شود نسخهٔ بعدی روی APK قبلی نصب نشود.
