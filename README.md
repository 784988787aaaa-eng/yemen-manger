# Yemen Manager — مدير الملفات ومختبر الحزم المحلي المتقدم

**Yemen Manager** هو تطبيق أندرويد محليّ التشغيل (100% Local-First Offline) لإدارة الملفات بلوحتين مستقلتين، ومعالجة الأرشيفات بأمان، وتحرير النصوص والبيانات الثنائية (Hex) ومقارنة الملفات (Diff)، والفحص الساكن لحزم `APK` (`AndroidManifest.xml` الثنائي `AXML`، وجداول الموارد `resources.arsc`، وملفات `classes.dex`، والتوقيع)، مع إعادة بناء وتوقيع نسخ العمل، وإدارة التطبيقات المثبتة.

---

## 1. المبادئ الهندسية المطبقة
- **محلي بالكامل دون إنترنت (Local-First):** تعمل جميع الميزات الأساسية دون أي اتصال خارجي أو حسابات سحابية أو تتبع.
- **حماية الملف الأصلي افتراضيًا (Safe by Default):** عمليات التحرير النصي والثنائي وإعادة بناء الحزم تكتب إلى نسخ عمل مستقلة (`.ym_work` أو `_rebuilt_signed.apk`) وتتحقق من سلامة المخرجات قبل أي استبدال اختياري للأصل.
- **التعامل مع المدخلات كبيانات غير موثوقة:** فحص صارم ضد هجمات `Zip Slip` (تجاوز المسارات) و`Zip Bomb` (نسب التوسع المفرطة)، وتحليل ساكن لملفات `APK/DEX/AXML/ARSC` دون تنفيذ أي شيفرة.
- **واجهة ثنائية اللغة (العربية RTL والإنجليزية LTR):** دعم أصلي لاتجاهي `RTL` و`LTR` مع نظام ألوان تقني داكن (`#0B0F14` / `#151B23` / `#5B7CFA` / `#20C9A6`) ووضع فاتح عالي التباين.

---

## 2. بنية الحزم والوحدات داخل المستودع
- `com.example.core.model`: نماذج المجال، الأخطاء الموحدة (`DomainError`)، حالات العمليات (`OperationStatus`)، ومصفوفة القدرات (`CapabilityState`).
- `com.example.core.i18n`: حزمة الترجمة الفورية للعربية (`RTL`) والإنجليزية (`LTR`).
- `com.example.core.storage`: واجهة `StorageProvider`، ومزود الملفات المحلية (`LocalFileStorageProvider`)، ومزود `SafStorageProvider`، وباني بيانات AXML الثنائية اللازمة لإعادة بناء APK (`BinaryAxmlBuilder`).
- `com.example.core.tasks`: محرك العمليات غير المتزامنة (`TaskCenterEngine`) مع التقدم والإلغاء التعاوني.
- `com.example.data.local`: قاعدة بيانات `Room` (`YemenDatabase`) لحفظ المفضلة، وسجل المسارات، وسجل العمليات، ومسودات المحرر.
- `com.example.engines.editor`: محرك النصوص (`TextEditorEngine`) مع `BoundedUndoRedoStack`، ومحرك `HexEngine` بنافذة 256 بايت، ومحرك المقارنة (`DiffEngine`).
- `com.example.engines.archive`: محرك `ZipArchiveEngine` مع حماية `Zip Slip` و`Zip Bomb` وكشف الأرشيف المشفر.
- `com.example.engines.apk`: محرك `ApkLabEngine` لتحليل `AXML` و`ARSC` و`DEX` وإعادة البناء والتوقيع بـ `RSA-2048`.
- `com.example.engines.system`: محرك `InstalledAppsEngine`، وفاحص مزودات الصلاحيات (`PrivilegeProviderProber`)، وخادم الشبكة المحلية الاختياري (`LocalLanTransferServer`)، وسجل الإضافات المعزول (`HonestCapabilityRegistry`).
- `com.example.ui`: شاشات `Jetpack Compose` (`FileManagerScreen`, `EditorsAndDiffScreen`, `ArchiveAndApkLabScreen`, `AppsTasksAndSettingsScreen`) و`YemenManagerViewModel`.

---

## 3. تعليمات البناء والتشغيل والاختبار محليًا (Offline Build & Test)

### أ) تشغيل الاختبارات المحلية (Unit + Golden + Robolectric UI Tests)
```bash
gradle :app:testDebugUnitTest
```

### ب) بناء حزمة التثبيت (Debug APK)
```bash
gradle :app:assembleDebug
```
يتم إنتاج ملف `APK` القابل للتثبيت في المسار:
`app/build/outputs/apk/debug/app-debug.apk`

---

## 4. الوثائق المرجعية المرفقة في المستودع
- [`CHANGELOG.md`](./CHANGELOG.md) — سجل الإصدارات والتغييرات المرحلية (P0 – P11).
- [`docs/DECISIONS.md`](./docs/DECISIONS.md) — سجل القرارات المعمارية والتقنية (ADR).
- [`docs/DEPENDENCIES.md`](./docs/DEPENDENCIES.md) — قائمة التبعيات المحلية وتراخيصها ومبرراتها.
- [`docs/TEST_PLAN.md`](./docs/TEST_PLAN.md) — خطة الاختبار الشاملة ونتائج التشغيل الفعلية.
