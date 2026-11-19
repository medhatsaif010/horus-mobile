# Horus Sec Mobile

تطبيق أندرويد من Horus Sec: بيفحص الموبايل من نفسه، من غير كمبيوتر ولا Root.

- **فحص الموبايل:** برامج التجسس المعروفة (بالاسم وبالتوقيع الرقمي)، تطبيقات البنوك والمحافظ المصرية المزيفة، شكل أحصنة طروادة البنكية (SMS + Accessibility أو الظهور فوق التطبيقات)، التطبيقات المخفية، Device admin، قراية الإشعارات، التسطيب من برّه المتجر، Root، التحديث الأمني والثغرات المستغلة (CISA KEV)، شهادات أمان متضافة بإيد، Proxy، Private DNS، وقفل الشاشة.
- **رقم أمان** من 100، و"اتغيّر إيه من آخر فحص"، و"مين يقدر يسمعك ويشوفك".
- **فحص لينك:** من جوه التطبيق أو بزرار مشاركة من واتساب والرسايل.
- عربي وإنجليزي.
- للقراءة بس: كل تصليح بزرار بيفتح الإعداد الصح، والمستخدم هو اللي ينفّذ.

## البناء

كل push على `main` بيبني APK على GitHub Actions، وبينشره في Release اسمه `apk`:
`https://github.com/medhatsaif010/horus-mobile/releases/download/apk/HorusShield.apk`

لو البناء فشل، السجل بيتنشر في Release اسمه `build-log`.

التوقيع الحالي (`app/horus-test.keystore`) للتجربة بس وعام عن قصد. النشر على Google Play محتاج مفتاح خاص يتحط كـ GitHub secret.

قاعدة التهديدات بتتنزّل من `medhatsaif010/horus-intel` وبتتقبل بس لو توقيعها RSA-SHA256 مطابق للمفتاح العام اللي جوه `Intel.kt`.

Stalkerware indicators: Echap (CC BY 4.0). C2 / URLhaus / MalwareBazaar: abuse.ch (CC0). CISA KEV.
