# Oriket Desktop — Android

تطبيق أندرويد أصلي مستقل باسم **Oriket Desktop**، وليس تطبيق ويب أو PWA. يقدم واجهة سطح مكتب مبسطة على الهاتف.

## وظائف النسخة الأولى
- واجهة سطح مكتب عربية مع أيقونات وشريط مهام.
- فتح المواقع في متصفح أندرويد المثبت.
- رسام يعمل باللمس ويحفظ PNG في صور الهاتف.
- اختيار ملفات PDF والملفات الأخرى عبر منتقي ملفات أندرويد وفتحها بالتطبيقات المناسبة.
- فتح إعدادات النظام والحاسبة إذا كانت مثبتة.

## الحصول على APK
كل دفع إلى فرع `main` أو تشغيل يدوي لسير عمل **Build Orkeit Desktop APK** من تبويب Actions يبني APK تجريبيًا. افتح نتيجة التشغيل الناجحة ثم قسم **Artifacts** ونزّل `orkeit-desktop-debug-apk`. هذا APK للتجربة؛ قبل النشر العام يلزم توقيع إصدار Release بمفتاح آمن.

## البناء
يتطلب JDK 17 وAndroid SDK وGradle 8.9:
```bash
gradle assembleDebug
```
الناتج: `app/build/outputs/apk/debug/app-debug.apk`

## حدود مهمة
هذا تطبيق أندرويد أصلي لكنه **ليس نظام Windows كاملًا أو محاكيًا**. لا يشغّل ملفات Windows `.exe` ولا يضم Chrome المكتبي. المتصفح يفتح المواقع في متصفح الجهاز، والملفات تُفتح عبر التطبيقات المناسبة المثبتة على أندرويد. تشغيل برامج Windows الحقيقية يحتاج طبقة توافق/محاكاة متخصصة أو جهاز Windows بعيدًا.

المستودع مستقل ولا يعتمد على Firebase أو تطبيق Oriket لزيارات الدفاع المدني.


## Google Play release

The project now targets Android 16 / API 36 and has a separate `play` flavor. To create a signed Google Play App Bundle, configure the four upload-key secrets described in [GOOGLE_PLAY_RELEASE.md](GOOGLE_PLAY_RELEASE.md), then run **Build Google Play App Bundle** from GitHub Actions. The output is an `.aab` artifact; this workflow does not publish to Play Console automatically.

The Play flavor removes `REQUEST_INSTALL_PACKAGES` and disables GitHub APK self-updates so Google Play remains the update channel. Publish the privacy policy at a public URL before submitting; see `docs/privacy-policy.html` and enable GitHub Pages with the GitHub Actions source.

The direct-download APK and Google Play build are separate distribution variants. Because their signing certificates can differ, do not assume the Play version can update an already installed debug-signed APK without reinstalling.
