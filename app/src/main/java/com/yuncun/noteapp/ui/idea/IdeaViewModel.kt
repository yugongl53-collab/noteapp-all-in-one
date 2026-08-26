package com.yuncun.noteapp.ui.idea

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.yuncun.noteapp.data.local.entity.IdeaEntity
import com.yuncun.noteapp.data.repository.IdeaRepository
import com.yuncun.noteapp.domain.rules.IdeaRules
import java.time.Duration
import java.time.Instant
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class IdeaUiState(
    val isLoading: Boolean = true,
    val activeIdeas: List<IdeaEntity> = emptyList(),
    val recycledIdeas: List<IdeaEntity> = emptyList(),
    val operationInProgress: Boolean = false,
    val feedback: String? = null,
    val saveCompletedVersion: Long = 0,
    val deleteCompletedVersion: Long = 0
)

data class IdeaDraftState(
    val id: String? = null,
    val content: String = "",
    val tagsInput: String = "",
    val contentError: String? = null,
    val isSaving: Boolean = false,
    val isDirty: Boolean = false
)

/** M2 灵感状态层：输入始终留在状态中，只有持久化成功后才清空或完成编辑。 */
class IdeaViewModel(
    private val repository: IdeaRepository,
    private val clock: () -> Instant = Instant::now
) : ViewModel() {
    private var expirationCleanupJob: Job? = null
    private val _uiState = MutableStateFlow(IdeaUiState())
    val uiState: StateFlow<IdeaUiState> = _uiState.asStateFlow()

    private val _editorDraft = MutableStateFlow(IdeaDraftState())
    val editorDraft: StateFlow<IdeaDraftState> = _editorDraft.asStateFlow()

    init {
        refresh()
    }

    fun refresh() {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true) }
            runCatching { repository.refresh(clock()) }
                .onSuccess { snapshot ->
                    scheduleExpirationCleanup(snapshot.recycledIdeas)
                    _uiState.update {
                        it.copy(
                            isLoading = false,
                            activeIdeas = snapshot.activeIdeas,
                            recycledIdeas = snapshot.recycledIdeas
                        )
                    }
                }
                .onFailure { error ->
                    _uiState.update {
                        it.copy(isLoading = false, feedback = failureMessage("加载失败", error))
                    }
                }
        }
    }

    /** 打开编辑页时复制当前持久化内容；新建入口使用空草稿。 */
    fun prepareEditor(id: String?) {
        if (id == null) {
            _editorDraft.value = IdeaDraftState()
            return
        }
        val idea = _uiState.value.activeIdeas.firstOrNull { it.id == id }
        if (idea == null) {
            _uiState.update { it.copy(feedback = "灵感不存在或已移入回收站") }
            return
        }
        _editorDraft.value = IdeaDraftState(
            id = idea.id,
            content = idea.content,
            tagsInput = idea.tags.joinToString("，")
        )
    }

    fun updateEditorContent(content: String) {
        _editorDraft.update { it.copy(content = content, contentError = null, isDirty = true) }
    }

    fun updateEditorTags(tagsInput: String) {
        _editorDraft.update { it.copy(tagsInput = tagsInput, isDirty = true) }
    }

    fun saveEditor() {
        val draft = _editorDraft.value
        val input = runCatching { IdeaRules.normalize(draft.content, draft.tagsInput) }
            .getOrElse { error ->
                _editorDraft.update { it.copy(contentError = error.message ?: "灵感正文不能为空") }
                return
            }
        _editorDraft.update { it.copy(isSaving = true, contentError = null) }
        viewModelScope.launch {
            val result = if (draft.id == null) {
                runCatching { repository.create(input.content, input.tags, clock()) }
            } else {
                runCatching { repository.update(draft.id, input.content, input.tags, clock()) }
            }
            result.onSuccess {
                _editorDraft.update { it.copy(isSaving = false, isDirty = false) }
                _uiState.update {
                    it.copy(
                        feedback = "灵感已保存",
                        saveCompletedVersion = it.saveCompletedVersion + 1
                    )
                }
                refreshKeepingFeedback()
            }.onFailure { error ->
                _editorDraft.update { it.copy(isSaving = false) }
                _uiState.update { it.copy(feedback = failureMessage("保存失败，请重试", error)) }
            }
        }
    }

    fun moveToTrash(id: String) = runListOperation("灵感已移入回收站") {
        repository.moveToTrash(id, clock())
        _uiState.update { it.copy(deleteCompletedVersion = it.deleteCompletedVersion + 1) }
    }

    fun restore(id: String) = runListOperation("灵感已恢复") { repository.restore(id) }

    fun permanentlyDelete(id: String) = runListOperation("灵感已永久删除") {
        repository.permanentlyDelete(id)
    }

    fun consumeFeedback() {
        _uiState.update { it.copy(feedback = null) }
    }

    private fun runListOperation(successMessage: String, action: suspend () -> Unit) {
        if (_uiState.value.operationInProgress) return
        _uiState.update { it.copy(operationInProgress = true) }
        viewModelScope.launch {
            runCatching { action() }
                .onSuccess { reloadAfterOperation(successMessage) }
                .onFailure { error ->
                    _uiState.update {
                        it.copy(
                            operationInProgress = false,
                            feedback = failureMessage("操作失败，请重试", error)
                        )
                    }
                }
        }
    }

    private suspend fun reloadAfterOperation(successMessage: String) {
        runCatching { repository.refresh(clock()) }
            .onSuccess { snapshot ->
                scheduleExpirationCleanup(snapshot.recycledIdeas)
                _uiState.update {
                    it.copy(
                        isLoading = false,
                        activeIdeas = snapshot.activeIdeas,
                        recycledIdeas = snapshot.recycledIdeas,
                        operationInProgress = false,
                        feedback = successMessage
                    )
                }
            }
            .onFailure { error ->
                _uiState.update {
                    it.copy(
                        isLoading = false,
                        operationInProgress = false,
                        feedback = failureMessage("刷新失败，请重试", error)
                    )
                }
            }
    }

    /** 编辑保存成功需要保留 Snackbar 文案，同时刷新列表排序。 */
    private suspend fun refreshKeepingFeedback() {
        val feedback = _uiState.value.feedback
        reloadAfterOperation(feedback ?: "灵感已保存")
    }

    private fun failureMessage(prefix: String, error: Throwable): String =
        "$prefix：${error.message ?: "未知错误"}"

    /** 进程持续运行时等待最近到期点并刷新；重启后仍由仓储首次读取兜底清理。 */
    private fun scheduleExpirationCleanup(recycledIdeas: List<IdeaEntity>) {
        expirationCleanupJob?.cancel()
        val nextExpiration = recycledIdeas.mapNotNull { it.deletedAt }
            .minOrNull()
            ?.plus(IdeaRules.retention)
            ?: return
        val waitMillis = Duration.between(clock(), nextExpiration).toMillis().coerceAtLeast(0L)
        expirationCleanupJob = viewModelScope.launch {
            delay(waitMillis)
            refresh()
        }
    }

    class Factory(private val repository: IdeaRepository) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            require(modelClass.isAssignableFrom(IdeaViewModel::class.java)) { "不支持的 ViewModel 类型" }
            return IdeaViewModel(repository) as T
        }
    }
}
