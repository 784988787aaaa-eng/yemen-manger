package com.example.core.tasks

import com.example.core.model.AppResult
import com.example.core.model.DomainError
import com.example.core.model.OperationStatus
import com.example.core.model.OperationTask
import com.example.core.model.OperationType
import com.example.data.local.OperationLogEntity
import com.example.data.local.YemenManagerDao
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.util.concurrent.ConcurrentHashMap

class TaskCenterEngine(
    private val dao: YemenManagerDao?,
    private val scope: CoroutineScope
) {
    private val _tasks = MutableStateFlow<List<OperationTask>>(emptyList())
    val tasks: StateFlow<List<OperationTask>> = _tasks.asStateFlow()

    private val runningJobs = ConcurrentHashMap<String, Job>()

    fun enqueueAndRun(
        type: OperationType,
        titleAr: String,
        titleEn: String,
        sources: List<String>,
        destination: String?,
        recoverabilityNote: String = "الأصل محفوظ ولا يُعدل تلقائيًا",
        block: suspend (updateProgress: (Float?, Long, Long?) -> Unit, isCancelled: () -> Boolean) -> AppResult<List<String>>
    ): String {
        val initialTask = OperationTask(
            operationType = type,
            titleAr = titleAr,
            titleEn = titleEn,
            sourceReferences = sources,
            destinationReference = destination,
            status = OperationStatus.QUEUED,
            recoverabilityNote = recoverabilityNote
        )
        val taskId = initialTask.operationId
        _tasks.update { listOf(initialTask) + it }

        val job = scope.launch(Dispatchers.IO) {
            updateTask(taskId) { it.copy(status = OperationStatus.RUNNING, startedAt = System.currentTimeMillis()) }
            try {
                val result = block(
                    { fraction, processed, total ->
                        updateTask(taskId) { current ->
                            current.copy(
                                progressFraction = fraction,
                                processedBytes = processed,
                                totalBytes = total
                            )
                        }
                    },
                    { runningJobs[taskId]?.isActive == false || currentStatus(taskId) == OperationStatus.CANCELLING }
                )

                val finishedTime = System.currentTimeMillis()
                when (result) {
                    is AppResult.Success -> {
                        val finalStatus = if (result.warnings.isEmpty()) {
                            OperationStatus.SUCCEEDED
                        } else {
                            OperationStatus.SUCCEEDED_WITH_WARNINGS
                        }
                        updateTask(taskId) {
                            it.copy(
                                status = finalStatus,
                                progressFraction = 1.0f,
                                finishedAt = finishedTime,
                                warnings = result.warnings,
                                outputReferences = result.data
                            )
                        }
                        persistLog(taskId, type, finalStatus, titleAr, null, result.warnings.size)
                    }
                    is AppResult.Failure -> {
                        val finalStatus = if (result.error is DomainError.Cancelled) {
                            OperationStatus.CANCELLED
                        } else {
                            OperationStatus.FAILED
                        }
                        updateTask(taskId) {
                            it.copy(
                                status = finalStatus,
                                finishedAt = finishedTime,
                                errorCode = result.error.code,
                                errorMessage = result.error.messageAr,
                                outputReferences = result.partialOutputs
                            )
                        }
                        persistLog(taskId, type, finalStatus, "${result.error.code}: ${result.error.messageAr}", result.error.code, 0)
                    }
                }
            } catch (ce: CancellationException) {
                val finishedTime = System.currentTimeMillis()
                updateTask(taskId) {
                    it.copy(
                        status = OperationStatus.CANCELLED,
                        finishedAt = finishedTime,
                        errorCode = "ERR_CANCELLED",
                        errorMessage = "تم إلغاء العملية بشكل تعاوني"
                    )
                }
                persistLog(taskId, type, OperationStatus.CANCELLED, "Cancelled: $titleAr", "ERR_CANCELLED", 0)
            } catch (e: Exception) {
                val finishedTime = System.currentTimeMillis()
                updateTask(taskId) {
                    it.copy(
                        status = OperationStatus.FAILED,
                        finishedAt = finishedTime,
                        errorCode = "ERR_UNEXPECTED",
                        errorMessage = e.message ?: "خطأ غير متوقع أثناء التنفيذ"
                    )
                }
                persistLog(taskId, type, OperationStatus.FAILED, "Failed: $titleAr", "ERR_UNEXPECTED", 0)
            } finally {
                runningJobs.remove(taskId)
            }
        }
        runningJobs[taskId] = job
        return taskId
    }

    fun cancelTask(operationId: String) {
        updateTask(operationId) {
            if (it.status == OperationStatus.RUNNING || it.status == OperationStatus.QUEUED) {
                it.copy(status = OperationStatus.CANCELLING)
            } else it
        }
        runningJobs[operationId]?.cancel()
    }

    fun clearFinishedTasks() {
        _tasks.update { list ->
            list.filter {
                it.status == OperationStatus.RUNNING ||
                    it.status == OperationStatus.QUEUED ||
                    it.status == OperationStatus.CANCELLING
            }
        }
    }

    private fun currentStatus(operationId: String): OperationStatus? {
        return _tasks.value.firstOrNull { it.operationId == operationId }?.status
    }

    private fun updateTask(operationId: String, transform: (OperationTask) -> OperationTask) {
        _tasks.update { list ->
            list.map { if (it.operationId == operationId) transform(it) else it }
        }
    }

    private suspend fun persistLog(
        id: String,
        type: OperationType,
        status: OperationStatus,
        summary: String,
        errorCode: String?,
        warningCount: Int
    ) {
        // NFR-SEC-02: Never log secrets or sensitive file contents
        dao?.upsertOperationLog(
            OperationLogEntity(
                operationId = id,
                operationType = type.name,
                status = status.name,
                summary = summary.take(200),
                errorCode = errorCode,
                warningCount = warningCount
            )
        )
    }
}
