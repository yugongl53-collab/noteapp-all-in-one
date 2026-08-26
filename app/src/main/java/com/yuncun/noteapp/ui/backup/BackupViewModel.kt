package com.yuncun.noteapp.ui.backup

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.yuncun.noteapp.data.backup.BackupOperations
import com.yuncun.noteapp.data.backup.BackupSnapshot
import com.yuncun.noteapp.data.backup.BackupSummary
import com.yuncun.noteapp.data.backup.toSummary
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class BackupUiState(
    val isBusy: Boolean = false,
    val showExportWarning: Boolean = false,
    val pendingImport: BackupSummary? = null,
    val feedback: String? = null
)

/** M7 状态层把文件读取、预校验和最终替换分成两个明确阶段。 */
class BackupViewModel(private val operations: BackupOperations) : ViewModel() {
    private val _uiState = MutableStateFlow(BackupUiState())
    val uiState: StateFlow<BackupUiState> = _uiState.asStateFlow()
    private var pendingSnapshot: BackupSnapshot? = null

    fun requestExport() {
        _uiState.update { it.copy(showExportWarning = true) }
    }

    fun dismissExportWarning() {
        _uiState.update { it.copy(showExportWarning = false) }
    }

    fun confirmExportWarning(chooseTarget: () -> Unit) {
        _uiState.update { it.copy(showExportWarning = false) }
        chooseTarget()
    }

    fun export(fileName: String, write: suspend (String) -> Unit) {
        _uiState.update { it.copy(isBusy = true) }
        viewModelScope.launch {
            runCatching {
                val content = operations.exportJson()
                write(content)
            }.onSuccess {
                _uiState.update { it.copy(isBusy = false, feedback = "数据备份已成功导出") }
            }.onFailure { error ->
                showFailure("数据备份导出失败，请重试", error)
            }
        }
    }

    fun prepareImport(fileName: String, read: suspend () -> String) {
        pendingSnapshot = null
        _uiState.update { it.copy(isBusy = true, pendingImport = null) }
        viewModelScope.launch {
            runCatching { operations.prepareImport(read()) }
                .onSuccess { snapshot ->
                    pendingSnapshot = snapshot
                    _uiState.update { it.copy(isBusy = false, pendingImport = snapshot.toSummary(fileName)) }
                }
                .onFailure {
                    _uiState.update { it.copy(isBusy = false, feedback = "文件格式不符合要求或已损坏，未能读取数据") }
                }
        }
    }

    fun dismissImportConfirmation() {
        pendingSnapshot = null
        _uiState.update { it.copy(pendingImport = null) }
    }

    fun confirmImport(onImported: () -> Unit) {
        val snapshot = pendingSnapshot ?: return
        _uiState.update { it.copy(isBusy = true) }
        viewModelScope.launch {
            runCatching { operations.import(snapshot) }
                .onSuccess { result ->
                    pendingSnapshot = null
                    val cleanup = if (result.expiredIdeasRemoved > 0) {
                        "（已自动清理 ${result.expiredIdeasRemoved} 条已过期笔记）"
                    } else ""
                    val message = result.reminderError?.let { "数据已成功恢复并同步，但部分提醒未能同步生效：$it$cleanup" }
                        ?: "数据已成功恢复并同步$cleanup"
                    _uiState.update { it.copy(isBusy = false, pendingImport = null, feedback = message) }
                    onImported()
                }
                .onFailure { error -> showFailure("数据恢复失败，原有数据未受影响", error) }
        }
    }

    fun consumeFeedback() {
        _uiState.update { it.copy(feedback = null) }
    }

    private fun showFailure(prefix: String, error: Throwable) {
        _uiState.update { it.copy(isBusy = false, feedback = "$prefix：${error.message ?: "未知错误"}") }
    }

    class Factory(private val operations: BackupOperations) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T = BackupViewModel(operations) as T
    }
}
