# خطة الاختبار وتقرير التحقق — `docs/TEST_PLAN.md`

تغطي هذه الخطة متطلبات القسم 11 من وثيقة `SRS` ومعايير القبول في `IMPLEMENTATION_ROADMAP_AR.md`.

---

## 1. مصفوفة الاختبارات الآلية المنفذة

### أ) اختبارات الوحدات والمحركات والاختبارات المرجعية (`ExampleUnitTest.kt`)
1. **`file operations preserve source and enforce overwrite protection`** (`FR-FM-002`, `FR-FM-006`, `FR-FM-009`):
   - يتحقق من إنشاء الملفات، والنسخ المتدفق بين اللوحتين، وبقاء الملف المصدر سليمًا بعد النسخ، ورفض الكتابة فوق ملف موجود مسبقًا (`DomainError.DestinationExists`)، وحساب البصمات `MD5` و`SHA-1` و`SHA-256` و`CRC32`.
2. **`text editor undo redo bounds and atomic working copy save`** (`FR-ED-001`, `FR-ED-002`, `FR-ED-004`):
   - يتحقق من التزام `BoundedUndoRedoStack` بالحد الأقصى للخطوات، وصحة البحث والاستبدال، وكتابة التعديلات في نسخة عمل `.ym_work` دون إتلاف الملف الأصلي.
3. **`hex engine reads windowed pages searches bytes and commits working copy`** (`FR-ED-003`, `FR-ED-004`, `NFR-MEM-01`):
   - يتحقق من قراءة نافذة 256 بايت فقط (16 صفًا × 16 بايت)، والبحث عن تسلسل بايتات `59 4D`، وحفظ التعديلات الثنائية في ملف عمل مستقل.
4. **`diff engine computes line and binary differences`** (`FR-ED-005`):
   - يتحقق من حساب الأسطر المضافة والمحذوفة والمتطابقة (`LCS`) وحساب الفروق الثنائية وأول إزاحة اختلاف.
5. **`zip archive engine blocks zip slip traversal and extracts valid archives`** (`FR-AR-001`..`FR-AR-005`, `NFR-SEC-01`):
   - يتحقق من حظر مسارات الخروج الخبيثة (`../escaped.txt` و`/abs/path.txt`) ومنع كتابة أي ملف خارج مجلد الوجهة، بالإضافة إلى نجاح إنشاء واستخراج أرشيف `ZIP` سليم.
6. **`apk lab golden test analyzes axml arsc dex and rebuilds signed working apk`** (`FR-APK-001`..`FR-APK-009`, `FR-DEX-001`..`FR-DEX-002`):
   - اختبار مرجعي (`Golden Test`) ينشئ حزمة `APK` قياسية تحتوي على `AndroidManifest.xml` ثنائي (`AXML`) و`resources.arsc` و`classes.dex`، ويتحقق من استخراج اسم الحزمة والإصدار والتحقق من `Adler32` واستخراج الفئات والدوال، ثم يعيد بناء الحزمة وتوقيعها بـ `RSA-2048` ويتحقق من صلاحية الحزمة الناتجة.
7. **`lan server is stopped by default requires token and extension registry blocks root`** (`FR-NET-003`..`FR-NET-005`, `FR-EXT-002`..`FR-EXT-004`):
   - يتحقق من أن خادم `LAN` متوقف افتراضيًا، ويرفض الطلبات غير المصرح بها (`HTTP 401`)، ويقبل الطلبات المزودة برمز الجلسة (`HTTP 200`)، ويغلق المنفذ فور الإيقاف، كما يتحقق من رفض الإضافات التي تطلب صلاحيات `root`.

### ب) اختبارات واجهة المستخدم وتدفقات Robolectric (`ExampleRobolectricTest.kt`)
1. **`app name matches Yemen Manager and dual pane file manager renders`** (`P1`, `P2`):
   - يتحقق من تطابق اسم التطبيق `Yemen Manager` وظهور مدير الملفات ثنائي اللوحة (`file_pane_A`).
2. **`navigation across editors archives apk lab tasks and settings works with RTL and LTR toggle`** (`NFR-I18N-01`, `P1`..`P11`):
   - يختبر التبديل الفوري بين العربية (`RTL`) والإنجليزية (`LTR`) والتنقل الفعلي عبر جميع الشاشات السبع (`Files`, `Editors & Diff`, `Archive Lab`, `APK Lab`, `Installed Apps`, `Task Center`, `Settings & Capabilities`).

---

## 2. أمر التشغيل القياسي
```bash
gradle :app:testDebugUnitTest
```
