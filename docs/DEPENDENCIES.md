# سجل التبعيات والتراخيص — `docs/DEPENDENCIES.md`

وفقًا لقواعد البناء دون إنترنت (`OFFLINE_BUILD_NOTES_AR.md` والقسم 6 من `SRS`)، يعتمد مشروع **Yemen Manager** حصريًا على المكتبات المتوفرة مسبقًا في المخزون المحلي (`gradle/libs.versions.toml`)، مع تعطيل جميع مكتبات الشبكة والسحابة غير المطلوبة.

---

## 1. التبعيات المفعّلة في البناء (`app/build.gradle.kts`)

| المكتبة (Group:Artifact) | الإصدار | الترخيص | التوفر المحلي | مبرر الاستخدام في Yemen Manager |
| :--- | :--- | :--- | :--- | :--- |
| `androidx.core:core-ktx` | `1.18.0` | Apache-2.0 | متوفر محليًا | امتدادات Kotlin الأساسية لنظام Android. |
| `androidx.activity:activity-compose` | `1.10.1` | Apache-2.0 | متوفر محليًا | ربط `MainActivity` بواجهة `Jetpack Compose` و`enableEdgeToEdge()` وعقود `SAF`. |
| `androidx.compose:compose-bom` | `2024.09.00` | Apache-2.0 | متوفر محليًا | إدارة إصدارات `Compose UI` و`Material 3` و`Material Icons`. |
| `androidx.lifecycle:lifecycle-*-compose` | `2.8.7` | Apache-2.0 | متوفر محليًا | إدارة حالة الواجهة (`ViewModel` و`collectAsStateWithLifecycle`). |
| `androidx.room:room-runtime` / `room-ktx` / `room-compiler` | `2.7.0` | Apache-2.0 | متوفر محليًا | التخزين المحلي للمفضلة، وسجل المسارات، وسجل العمليات، ومسودات المحرر. |
| `org.jetbrains.kotlinx:kotlinx-coroutines-android` / `core` | `1.10.2` | Apache-2.0 | متوفر محليًا | تشغيل عمليات الملفات والأرشفة والتحليل في الخلفية مع دعم الإلغاء التعاوني. |
| `junit:junit` | `4.13.2` | EPL-1.0 | متوفر محليًا | اختبارات الوحدات والاختبارات المرجعية (`Golden Tests`) للمحركات. |
| `org.robolectric:robolectric` | `4.16.1` | MIT / Apache-2.0 | متوفر محليًا | اختبارات واجهة المستخدم والتدفقات محليًا على JVM دون الحاجة لمحاكي خارجي. |
| `androidx.compose.ui:ui-test-junit4` | BOM | Apache-2.0 | متوفر محليًا | التحقق الآلي من شاشات `Compose` وتبديل اللغات `RTL/LTR`. |

---

## 2. التبعيات المعطّلة عمدًا (لتحقيق Local-First وتقليص حجم APK)
تم تعطيل التبعيات التالية في `app/build.gradle.kts` لأنها غير لازمة لنواة التطبيق المحلية:
- `com.google.firebase:*` (`firebase-bom`, `firebase-ai`, `firebase-firestore`, `firebase-auth`, `firebase-appcheck`) — التطبيق محلي بالكامل بلا حسابات أو سحابة.
- `com.squareup.retrofit2:*` و`com.squareup.okhttp3:*` و`com.squareup.moshi:*` — لا توجد اتصالات HTTP خارجية.

---

## 3. التبعيات الخارجية غير المتوفرة في الكاش المحلي (المعزولة والموثقة)
| المكتبة المقترحة في SRS | الحالة في الكاش المحلي | البديل المنفذ محليًا في الإصدار 1.0 |
| :--- | :--- | :--- |
| `jadx-core` | غير مخزنة محليًا | محلل جداول `DEX` الثنائي الأصلي في `ApkLabEngine` + إعلان صريح في مصفوفة القدرات. |
| `smali` / `baksmali` (`dexlib2`) | غير مخزنة محليًا | استخراج الفئات والدوال والسلاسل النصية مباشرة من بنية `classes.dex`. |
| `apktool` | غير مخزنة محليًا | محلل ومولّد `AXML` و`ARSC` الثنائي في `ApkLabEngine` و`WorkspaceSeeder`. |
