package com.example.core.model

import java.util.UUID

/**
 * SRS Section 14: Typed domain errors. Never expose raw stack traces to the user.
 */
sealed class DomainError(val code: String, val messageAr: String, val messageEn: String) {
    class PermissionDenied(detail: String) : DomainError(
        "ERR_PERMISSION_DENIED",
        "تم رفض الصلاحية للوصول إلى المورد: $detail",
        "Permission denied while accessing resource: $detail"
    )
    class UnsupportedFormat(detail: String) : DomainError(
        "ERR_UNSUPPORTED_FORMAT",
        "تنسيق غير مدعوم أو مشفر: $detail",
        "Unsupported or encrypted format: $detail"
    )
    class CorruptInput(detail: String) : DomainError(
        "ERR_CORRUPT_INPUT",
        "مدخلات تالفة أو غير صالحة بنيويًا: $detail",
        "Corrupt or structurally invalid input: $detail"
    )
    class DestinationExists(detail: String) : DomainError(
        "ERR_DESTINATION_EXISTS",
        "الوجهة موجودة مسبقًا (حماية عدم الكتابة فوق الأصل): $detail",
        "Destination already exists (overwrite protection): $detail"
    )
    class InsufficientSpace(detail: String) : DomainError(
        "ERR_INSUFFICIENT_SPACE",
        "المساحة المتاحة غير كافية لإتمام العملية: $detail",
        "Insufficient storage space to complete operation: $detail"
    )
    class Cancelled(detail: String = "") : DomainError(
        "ERR_CANCELLED",
        "تم إلغاء العملية بطلب المستخدم $detail",
        "Operation cancelled by user $detail"
    )
    class ValidationFailed(detail: String) : DomainError(
        "ERR_VALIDATION_FAILED",
        "فشل التحقق الأمني أو البنيوي: $detail",
        "Security or structural validation failed: $detail"
    )
    class EngineUnavailable(detail: String) : DomainError(
        "ERR_ENGINE_UNAVAILABLE",
        "المحرك المطلوب غير متاح في الوضع الحالي: $detail",
        "Requested engine is unavailable in current mode: $detail"
    )
    class NotFound(detail: String) : DomainError(
        "ERR_NOT_FOUND",
        "العنصر المطلوب غير موجود: $detail",
        "Requested item not found: $detail"
    )
    class IoFailure(detail: String) : DomainError(
        "ERR_IO_FAILURE",
        "تعذر إتمام عملية القراءة/الكتابة: $detail",
        "I/O operation failed: $detail"
    )

    fun localizedMessage(isArabic: Boolean): String = if (isArabic) messageAr else messageEn
}

sealed class AppResult<out T> {
    data class Success<T>(val data: T, val warnings: List<String> = emptyList()) : AppResult<T>()
    data class Failure(val error: DomainError, val partialOutputs: List<String> = emptyList()) : AppResult<Nothing>()

    val isSuccess: Boolean get() = this is Success
}

/**
 * SRS Section 14: Standard operation lifecycle states.
 * Partial failure is NEVER silently promoted to SUCCEEDED.
 */
enum class OperationStatus {
    QUEUED,
    RUNNING,
    CANCELLING,
    SUCCEEDED,
    SUCCEEDED_WITH_WARNINGS,
    FAILED,
    CANCELLED
}

enum class OperationType {
    COPY,
    MOVE,
    RENAME,
    DELETE,
    TRASH,
    CREATE_FOLDER,
    HASH_COMPUTE,
    ARCHIVE_EXTRACT,
    ARCHIVE_CREATE,
    TEXT_SAVE,
    HEX_PATCH,
    APK_ANALYZE,
    APK_REBUILD_SIGN,
    APP_APK_EXTRACT
}

data class OperationTask(
    val operationId: String = UUID.randomUUID().toString(),
    val operationType: OperationType,
    val titleAr: String,
    val titleEn: String,
    val sourceReferences: List<String>,
    val destinationReference: String?,
    val status: OperationStatus = OperationStatus.QUEUED,
    val progressFraction: Float? = null,
    val processedBytes: Long = 0L,
    val totalBytes: Long? = null,
    val startedAt: Long = System.currentTimeMillis(),
    val finishedAt: Long? = null,
    val warnings: List<String> = emptyList(),
    val errorCode: String? = null,
    val errorMessage: String? = null,
    val outputReferences: List<String> = emptyList(),
    val recoverabilityNote: String = ""
)

/**
 * SRS Section 2: Honest capability status classification.
 */
enum class CapabilityState(val labelAr: String, val labelEn: String) {
    COMPLETED("مكتملة ومختبرة", "Completed & Tested"),
    EXPERIMENTAL("تجريبية (محدودة النطاق)", "Experimental (Bounded)"),
    NOT_SUPPORTED("غير مدعومة بدون مكتبة خارجية", "Not Supported Offline"),
    PLANNED("مؤجلة لإصدار لاحق", "Planned for Future Release")
}

data class CapabilityEntry(
    val id: String,
    val featureAr: String,
    val featureEn: String,
    val srsRef: String,
    val state: CapabilityState,
    val notesAr: String,
    val notesEn: String
)

enum class StorageProviderType {
    LOCAL_FS,
    SAF_URI
}

data class ProviderCapabilities(
    val supportsStreamingRead: Boolean,
    val supportsAtomicTempWrite: Boolean,
    val supportsDirectRename: Boolean,
    val supportsRandomAccess: Boolean,
    val supportsTrashBin: Boolean
)

data class FileEntry(
    val uriOrPath: String,
    val name: String,
    val isDirectory: Boolean,
    val sizeBytes: Long,
    val lastModified: Long,
    val extension: String,
    val mimeType: String,
    val canRead: Boolean,
    val canWrite: Boolean,
    val providerType: StorageProviderType = StorageProviderType.LOCAL_FS
)

data class FileHashReport(
    val uriOrPath: String,
    val sizeBytes: Long,
    val md5: String,
    val sha1: String,
    val sha256: String,
    val crc32Hex: String
)

enum class SortField {
    NAME, SIZE, DATE, EXTENSION
}

data class SortConfig(
    val field: SortField = SortField.NAME,
    val ascending: Boolean = true,
    val directoriesFirst: Boolean = true
)
