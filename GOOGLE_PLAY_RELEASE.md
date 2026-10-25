# متطلبات نشر Oriket Desktop على Google Play

## ما أُضيف إلى المستودع
- استهداف Android 16 / API 36، المطلوب للتطبيقات الجديدة وتحديثات التطبيقات على Google Play ابتداءً من 31 أغسطس 2026.
- نكهة بناء مستقلة باسم `play` لا تتضمن صلاحية تثبيت APK من خارج المتجر.
- نسخة Google Play لا تنفذ فحص تحديثات GitHub ولا تعرض تثبيت APK خارجيًا؛ تُدار التحديثات من Google Play.
- سير عمل يدوي لبناء Android App Bundle موقّع (AAB) عند توفير أسرار التوقيع.
- مسودة سياسة خصوصية وصفحة نشر GitHub Pages.

## إعداد مفتاح الرفع في GitHub
من المستودع: Settings → Secrets and variables → Actions → New repository secret. أضف:
- `ANDROID_KEYSTORE_BASE64`: محتوى ملف keystore بعد تحويله إلى Base64.
- `ANDROID_KEYSTORE_PASSWORD`: كلمة مرور keystore.
- `ANDROID_KEY_ALIAS`: اسم المفتاح.
- `ANDROID_KEY_PASSWORD`: كلمة مرور المفتاح.

احتفظ بنسخة احتياطية آمنة من المفتاح وكلمات المرور. لا ترفع ملف keystore أو كلمات المرور إلى GitHub ولا ترسلها في المحادثات. استخدم مفتاح رفع مخصصًا لـ Google Play؛ لا تفترض أنه يطابق توقيع APK التجريبي القديم.

بعد إضافة الأسرار، افتح Actions → Build Google Play App Bundle → Run workflow. سينتج ملف:
`app-play-release.aab` داخل artifact باسم `orkeit-desktop-google-play-aab`. سير العمل لا ينشر التطبيق تلقائيًا إلى المتجر؛ ارفع الملف إلى Play Console بعد المراجعة.

## سياسة الخصوصية
1. استبدل حقل بريد الدعم داخل `docs/privacy-policy.html` بعنوان دعم رسمي فعلي.
2. من Settings → Pages، اختر النشر من GitHub Actions.
3. شغّل سير عمل Deploy privacy policy to GitHub Pages.
4. افتح رابط الصفحة الناتج وتأكد أنه عام ويعمل، ثم أدخله في Play Console وفي صفحة المتجر.

## قائمة مراجعة Play Console
- إنشاء التطبيق في Play Console وتأكيد اسم الحزمة `com.orkeit.desktop`.
- تفعيل Play App Signing وحفظ مفتاح الرفع بأمان.
- رفع AAB موقّع واستخدام مسار اختبار داخلي قبل الإنتاج.
- إكمال App content وData safety والإقرار بدقة عن البيانات والأذونات والمكتبات المستخدمة.
- إضافة رابط سياسة الخصوصية داخل Play Console وإتاحته داخل التطبيق/الصفحة.
- إعداد تصنيف المحتوى، والجمهور المستهدف، والإعلانات (لا تحدد وجود إعلانات ما لم تضفها).
- تجهيز اسم ووصف التطبيق، وأيقونة المتجر 512×512، وصورة Feature graphic بقياس 1024×500، ولقطات شاشة حقيقية من التطبيق.
- إكمال بيانات التواصل والتحقق من هوية المطوّر وأي اختبار مغلق يطلبه Play Console لحسابك.
- اختبار نسخة Play على أجهزة فعلية، ومراجعة الأعطال، والأداء، وإمكانية الوصول، وسياسة المحتوى.
- عدم ادعاء أن التطبيق نظام Windows كامل أو أن مشغلاته Chrome/VLC الأصليان؛ وصفه كبيئة سطح مكتب مستوحاة من Windows على Android.

## تنبيه التوقيع والتحديث
APK التجريبي الحالي موقّع بمفتاح debug محفوظ في GitHub Actions، بينما نسخة Google Play تستخدم شهادة توقيع التطبيق التي يديرها Play App Signing. قد لا يمكن تثبيت نسخة Play فوق APK التجريبي مباشرة بسبب اختلاف التوقيع. لا تحذف APK التجريبي قبل حفظ الملفات المهمة؛ وإذا كان هذا التطبيق منشورًا بالفعل بمفتاح إنتاج، فيجب استخدام إعدادات التوقيع الأصلية الصحيحة بدلًا من البدء بمفتاح جديد.

## مراجع رسمية
- متطلبات Target API: https://developer.android.com/google/play/requirements/target-sdk
- Android App Bundle: https://developer.android.com/guide/app-bundle
- Play App Signing: https://support.google.com/googleplay/android-developer/answer/9842756
- سياسة الخصوصية ومحتوى التطبيق: https://support.google.com/googleplay/android-developer/answer/18258653
