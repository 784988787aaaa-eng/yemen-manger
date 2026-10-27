package com.example.engines.system

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.os.PowerManager
import android.os.VibrationEffect
import android.os.Vibrator
import android.provider.Settings
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import java.io.File

data class SystemPermissionItem(
    val id: String,
    val titleAr: String,
    val titleEn: String,
    val descriptionAr: String,
    val descriptionEn: String,
    val isGranted: Boolean,
    val isCritical: Boolean,
    val actionLabelAr: String,
    val actionLabelEn: String
)

data class SystemPermissionsSnapshot(
    val allFilesAccessGranted: Boolean,
    val batteryOptimizationIgnored: Boolean,
    val canInstallPackages: Boolean,
    val notificationsGranted: Boolean,
    val queryAllPackagesGranted: Boolean,
    val wakeLockDeclared: Boolean,
    val items: List<SystemPermissionItem>
) {
    val grantedCount: Int get() = items.count { it.isGranted }
    val totalCount: Int get() = items.size
    val allCriticalGranted: Boolean get() = allFilesAccessGranted && batteryOptimizationIgnored && canInstallPackages
}

object PermissionsAndSystemIntentsEngine {

    fun checkPermissionsSnapshot(context: Context): SystemPermissionsSnapshot {
        val allFilesGranted = try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                Environment.isExternalStorageManager()
            } else {
                val read = ContextCompat.checkSelfPermission(context, Manifest.permission.READ_EXTERNAL_STORAGE) == PackageManager.PERMISSION_GRANTED
                val write = ContextCompat.checkSelfPermission(context, Manifest.permission.WRITE_EXTERNAL_STORAGE) == PackageManager.PERMISSION_GRANTED
                read && write
            }
        } catch (_: Throwable) {
            false
        }

        val pm = try {
            context.getSystemService(Context.POWER_SERVICE) as? PowerManager
        } catch (_: Throwable) {
            null
        }
        val batteryIgnored = try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                pm?.isIgnoringBatteryOptimizations(context.packageName) ?: false
            } else {
                true
            }
        } catch (_: Throwable) {
            false
        }

        val canInstall = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            try {
                context.packageManager.canRequestPackageInstalls()
            } catch (_: Throwable) {
                false
            }
        } else {
            true
        }

        val notificationsGranted = try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED
            } else {
                true
            }
        } catch (_: Throwable) {
            false
        }

        val wakeLockGranted = try {
            ContextCompat.checkSelfPermission(context, Manifest.permission.WAKE_LOCK) == PackageManager.PERMISSION_GRANTED
        } catch (_: Throwable) {
            true
        }

        val items = listOf(
            SystemPermissionItem(
                id = "all_files_access",
                titleAr = "إذن الوصول الكامل لجميع الملفات (All Files Access)",
                titleEn = "All Files Storage Access (MANAGE_EXTERNAL_STORAGE)",
                descriptionAr = "ضروري لقراءة وكتابة المجلدات والملفات والأرشيفات عبر ذاكرة الجهاز المشتركة (/storage/emulated/0) بسرعة كاملة.",
                descriptionEn = "Required to browse, copy, move, and manage files across shared storage (/storage/emulated/0) at full speed.",
                isGranted = allFilesGranted,
                isCritical = true,
                actionLabelAr = if (allFilesGranted) "ممنوح ✓" else "منح الإذن الآن",
                actionLabelEn = if (allFilesGranted) "Granted ✓" else "Grant Access"
            ),
            SystemPermissionItem(
                id = "battery_optimization",
                titleAr = "استثناء تحسين البطارية (Ignore Battery Optimizations)",
                titleEn = "Ignore Battery Optimizations (Unrestricted Background)",
                descriptionAr = "يمنع النظام من إيقاف عمليات ضغط الأرشيفات أو بناء وتوقيع ملفات APK أو خادم النقل المحلي عند انطفاء الشاشة.",
                descriptionEn = "Prevents Android Doze from interrupting long archive extractions, APK 4K optimization, signing, or LAN transfers.",
                isGranted = batteryIgnored,
                isCritical = true,
                actionLabelAr = if (batteryIgnored) "مستثنى ✓" else "إلغاء قيود البطارية",
                actionLabelEn = if (batteryIgnored) "Unrestricted ✓" else "Disable Optimization"
            ),
            SystemPermissionItem(
                id = "install_unknown_apps",
                titleAr = "تثبيت حزم التطبيقات (Install Unknown Apps)",
                titleEn = "Install APK Packages (REQUEST_INSTALL_PACKAGES)",
                descriptionAr = "يتيح تثبيت ملفات APK التي قمت بتوقيعها أو استنساخها أو استخراجها مباشرة من داخل Yemen Manager.",
                descriptionEn = "Allows direct installation of signed, optimized, or cloned APKs directly from Yemen Manager.",
                isGranted = canInstall,
                isCritical = true,
                actionLabelAr = if (canInstall) "مفعّل ✓" else "تفعيل التثبيت",
                actionLabelEn = if (canInstall) "Enabled ✓" else "Allow Install"
            ),
            SystemPermissionItem(
                id = "post_notifications",
                titleAr = "إشعارات العمليات والمهام (POST_NOTIFICATIONS)",
                titleEn = "Task Progress Notifications (POST_NOTIFICATIONS)",
                descriptionAr = "لعرض حالة تقدم عمليات النسخ والضغط وتوقيع الحزم في الخلفية.",
                descriptionEn = "Displays completion and progress alerts for long file and APK operations.",
                isGranted = notificationsGranted,
                isCritical = false,
                actionLabelAr = if (notificationsGranted) "مفعّل ✓" else "تفعيل الإشعارات",
                actionLabelEn = if (notificationsGranted) "Granted ✓" else "Allow Alerts"
            ),
            SystemPermissionItem(
                id = "query_all_packages",
                titleAr = "فحص جميع التطبيقات المثبتة (QUERY_ALL_PACKAGES)",
                titleEn = "Inspect All Installed Packages (QUERY_ALL_PACKAGES)",
                descriptionAr = "مفعّل في البيان لقراءة تطبيقات المستخدم وتطبيقات النظام واستخراج ملفات Base/Split APK.",
                descriptionEn = "Declared in manifest to inspect User & System apps and extract Base/Split APKs.",
                isGranted = true,
                isCritical = false,
                actionLabelAr = "مفعّل ✓",
                actionLabelEn = "Active ✓"
            ),
            SystemPermissionItem(
                id = "wake_lock",
                titleAr = "قفل اليقظة للمعالج أثناء المهام الثقيلة (WAKE_LOCK)",
                titleEn = "CPU WakeLock for Heavy File Operations",
                descriptionAr = "يحافظ على سرعة المعالج القصوى أثناء حساب البصمات الرقمية SHA-256 وضغط الملفات الكبيرة.",
                descriptionEn = "Keeps CPU active during cryptographic hashing, ZIP compression, and APK signing.",
                isGranted = wakeLockGranted,
                isCritical = false,
                actionLabelAr = "مفعّل ✓",
                actionLabelEn = "Active ✓"
            )
        )

        return SystemPermissionsSnapshot(
            allFilesAccessGranted = allFilesGranted,
            batteryOptimizationIgnored = batteryIgnored,
            canInstallPackages = canInstall,
            notificationsGranted = notificationsGranted,
            queryAllPackagesGranted = true,
            wakeLockDeclared = wakeLockGranted,
            items = items
        )
    }

    fun launchAllFilesAccessSettings(context: Context): Boolean {
        return try {
            val intent = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                Intent(
                    Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION,
                    Uri.parse("package:${context.packageName}")
                )
            } else {
                Intent(
                    Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                    Uri.parse("package:${context.packageName}")
                )
            }.apply { addFlags(Intent.FLAG_ACTIVITY_NEW_TASK) }
            context.startActivity(intent)
            true
        } catch (_: Exception) {
            try {
                val fallback = Intent(Settings.ACTION_MANAGE_ALL_FILES_ACCESS_PERMISSION).apply {
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
                context.startActivity(fallback)
                true
            } catch (_: Exception) {
                launchAppDetailsSettings(context)
            }
        }
    }

    @SuppressLint("BatteryLife")
    fun launchIgnoreBatteryOptimizations(context: Context): Boolean {
        return try {
            val intent = Intent(
                Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS,
                Uri.parse("package:${context.packageName}")
            ).apply { addFlags(Intent.FLAG_ACTIVITY_NEW_TASK) }
            context.startActivity(intent)
            true
        } catch (_: Exception) {
            try {
                val fallback = Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS).apply {
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
                context.startActivity(fallback)
                true
            } catch (_: Exception) {
                launchAppDetailsSettings(context)
            }
        }
    }

    fun launchInstallUnknownAppsSettings(context: Context): Boolean {
        return try {
            val intent = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                Intent(
                    Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES,
                    Uri.parse("package:${context.packageName}")
                )
            } else {
                Intent(Settings.ACTION_SECURITY_SETTINGS)
            }.apply { addFlags(Intent.FLAG_ACTIVITY_NEW_TASK) }
            context.startActivity(intent)
            true
        } catch (_: Exception) {
            launchAppDetailsSettings(context)
        }
    }

    fun launchAppDetailsSettings(context: Context): Boolean {
        return try {
            val intent = Intent(
                Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                Uri.parse("package:${context.packageName}")
            ).apply { addFlags(Intent.FLAG_ACTIVITY_NEW_TASK) }
            context.startActivity(intent)
            true
        } catch (_: Exception) {
            false
        }
    }

    /**
     * Launches the Android system package installer for a local APK file via FileProvider.
     * If REQUEST_INSTALL_PACKAGES is not yet granted, redirects to the permission screen first.
     */
    fun installApkViaFileProvider(context: Context, apkPath: String): Pair<Boolean, String> {
        val file = File(apkPath)
        if (!file.exists()) {
            return false to "APK file not found: $apkPath"
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val canInstall = try {
                context.packageManager.canRequestPackageInstalls()
            } catch (_: Exception) {
                true
            }
            if (!canInstall) {
                launchInstallUnknownAppsSettings(context)
                return false to "يرجى منح إذن تثبيت التطبيقات (Install Unknown Apps) أولاً ثم إعادة المحاولة."
            }
        }

        return try {
            val authority = "${context.packageName}.fileprovider"
            val contentUri = FileProvider.getUriForFile(context, authority, file)
            val installIntent = Intent(Intent.ACTION_VIEW).apply {
                setDataAndType(contentUri, "application/vnd.android.package-archive")
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(installIntent)
            true to "تم فتح مثبت النظام لحزمة: ${file.name}"
        } catch (e: Exception) {
            false to "جاهز للتثبيت (${file.name}) — ${e.message ?: "System installer invoked"}"
        }
    }

    /**
     * Shares any local file via Android Share Sheet using FileProvider.
     */
    fun shareFileViaFileProvider(context: Context, filePath: String): Boolean {
        val file = File(filePath)
        if (!file.exists() || file.isDirectory) return false
        return try {
            val authority = "${context.packageName}.fileprovider"
            val contentUri = FileProvider.getUriForFile(context, authority, file)
            val shareIntent = Intent(Intent.ACTION_SEND).apply {
                type = "*/*"
                putExtra(Intent.EXTRA_STREAM, contentUri)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            val chooser = Intent.createChooser(shareIntent, file.name).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(chooser)
            true
        } catch (_: Exception) {
            false
        }
    }

    /**
     * Executes a heavy block while holding a bounded Partial WakeLock so CPU does not sleep mid-operation.
     */
    inline fun <T> withCpuWakeLock(context: Context, tag: String = "YemenManager:TaskWakeLock", block: () -> T): T {
        val pm = context.getSystemService(Context.POWER_SERVICE) as? PowerManager
        val wakeLock = try {
            pm?.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, tag)?.apply {
                setReferenceCounted(false)
                acquire(5 * 60 * 1000L) // Max 5 minutes safety timeout
            }
        } catch (_: Exception) {
            null
        }
        return try {
            block()
        } finally {
            try {
                if (wakeLock?.isHeld == true) {
                    wakeLock.release()
                }
            } catch (_: Exception) {}
        }
    }

    /**
     * Gentle tactile feedback when long-pressing or triggering toolbar actions.
     */
    fun triggerTickHaptic(context: Context) {
        try {
            val vibrator = context.getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator ?: return
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                vibrator.vibrate(VibrationEffect.createOneShot(18L, VibrationEffect.DEFAULT_AMPLITUDE))
            } else {
                @Suppress("DEPRECATION")
                vibrator.vibrate(15L)
            }
        } catch (_: Exception) {}
    }
}
