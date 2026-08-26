package com.yuncun.noteapp.ui.pomodoro

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.yuncun.noteapp.data.local.entity.EventPoolItemEntity
import com.yuncun.noteapp.data.repository.EventPoolRepository
import com.yuncun.noteapp.domain.model.AppSettings
import com.yuncun.noteapp.domain.model.EventCategory
import com.yuncun.noteapp.domain.model.EventPoolCandidate
import com.yuncun.noteapp.domain.model.PomodoroSession
import com.yuncun.noteapp.domain.model.PomodoroState
import com.yuncun.noteapp.domain.rules.EventPoolRules
import com.yuncun.noteapp.domain.rules.PomodoroRules
import com.yuncun.noteapp.pomodoro.PomodoroCoordinator
import java.time.Instant
import kotlin.random.Random
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class PomodoroUiState(
    val isLoading: Boolean = true,
    val poolItems: List<EventPoolItemEntity> = emptyList(),
    val poolNameSuggestions: List<String> = emptyList(),
    val selectedCandidate: EventPoolCandidate? = null,
    val drawVersion: Long = 0,
    val settings: AppSettings = AppSettings(),
    val session: PomodoroSession? = null,
    val remainingSeconds: Long = 0,
    val operationInProgress: Boolean = false,
    val feedback: String? = null,
    val actionCompletedVersion: Long = 0
)

/** M5 状态层连接 Room 事件池与 DataStore 番茄钟，并以注入时钟计算展示倒计时。 */
class PomodoroViewModel(
    private val poolRepository: EventPoolRepository,
    private val coordinator: PomodoroCoordinator,
    private val clock: () -> Instant = Instant::now,
    private val nextWeightUnit: (Long) -> Long = { Random.nextLong(it) }
) : ViewModel() {
    private val _uiState = MutableStateFlow(PomodoroUiState())
    val uiState: StateFlow<PomodoroUiState> = _uiState.asStateFlow()
    private var tickerJob: Job? = null

    init {
        refreshPool()
        viewModelScope.launch {
            coordinator.settings.collect { settings -> _uiState.update { it.copy(settings = settings) } }
        }
        viewModelScope.launch {
            coordinator.session.collect(::applySession)
        }
        viewModelScope.launch {
            runCatching { coordinator.synchronize() }
                .onFailure { showFailure("恢复番茄钟失败", it) }
        }
    }

    fun refreshPool() {
        viewModelScope.launch {
            runCatching { poolRepository.load() }
                .onSuccess(::applyPool)
                .onFailure { showFailure("加载事件池失败", it) }
        }
    }

    fun savePoolItem(id: String?, title: String, category: EventCategory, weight: Int, isEnabled: Boolean) =
        runPoolOperation("事件池项目已保存") {
            poolRepository.save(id, title, category, isEnabled, weight, clock())
        }

    fun setPoolItemEnabled(id: String, enabled: Boolean) = runPoolOperation(
        if (enabled) "事件池项目已启用" else "事件池项目已停用"
    ) { poolRepository.setEnabled(id, enabled, clock()) }

    fun deletePoolItem(id: String) = runPoolOperation("事件池项目已删除") {
        poolRepository.delete(id)
        if (_uiState.value.selectedCandidate?.id == id) {
            _uiState.update { it.copy(selectedCandidate = null) }
        }
    }

    fun draw() {
        val candidates = _uiState.value.poolItems.map(EventPoolItemEntity::toCandidate)
        val selected = EventPoolRules.draw(candidates, nextWeightUnit)
        _uiState.update {
            it.copy(
                selectedCandidate = selected,
                drawVersion = it.drawVersion + if (selected == null) 0 else 1,
                feedback = if (selected == null) "没有启用项目，请先添加或启用一项" else null
            )
        }
    }

    fun startPomodoro(title: String?, focusMinutes: Int, restMinutes: Int) =
        runPomodoroOperation("专注计时已开始") { coordinator.start(title, focusMinutes, restMinutes) }

    fun pause() = runPomodoroOperation("番茄钟已暂停", coordinator::pause)
    fun resume() = runPomodoroOperation("番茄钟已继续", coordinator::resume)
    fun reset() = runPomodoroOperation("当前阶段已重置", coordinator::reset)
    fun finishEarly() = runPomodoroOperation("当前阶段已提前结束", coordinator::finishEarly)
    fun startRest() = runPomodoroOperation("休息计时已开始", coordinator::startRest)
    fun clearSession() = runPomodoroOperation("番茄钟已结束", coordinator::clear)

    fun consumeFeedback() {
        _uiState.update { it.copy(feedback = null) }
    }

    private fun runPoolOperation(successMessage: String, action: suspend () -> Unit) {
        if (_uiState.value.operationInProgress) return
        _uiState.update { it.copy(operationInProgress = true) }
        viewModelScope.launch {
            runCatching {
                action()
                poolRepository.load()
            }.onSuccess { items -> applyPool(items, successMessage, completed = true) }
                .onFailure { error -> finishFailure("事件池操作失败", error) }
        }
    }

    private fun runPomodoroOperation(successMessage: String, action: suspend () -> Unit) {
        if (_uiState.value.operationInProgress) return
        _uiState.update { it.copy(operationInProgress = true) }
        viewModelScope.launch {
            runCatching { action() }
                .onSuccess {
                    _uiState.update { state ->
                        state.copy(
                            operationInProgress = false,
                            feedback = successMessage,
                            actionCompletedVersion = state.actionCompletedVersion + 1
                        )
                    }
                }
                .onFailure { error -> finishFailure("番茄钟操作失败", error) }
        }
    }

    private fun applyPool(items: List<EventPoolItemEntity>, feedback: String? = null, completed: Boolean = false) {
        _uiState.update { state ->
            state.copy(
                isLoading = false,
                poolItems = items,
                poolNameSuggestions = items.map { it.title.trim() }.filter { it.isNotEmpty() }.distinct(),
                operationInProgress = false,
                feedback = feedback,
                actionCompletedVersion = state.actionCompletedVersion + if (completed) 1 else 0
            )
        }
    }

    private fun applySession(session: PomodoroSession?) {
        tickerJob?.cancel()
        val remaining = session?.let { PomodoroRules.remainingSeconds(it, clock()) } ?: 0
        _uiState.update { it.copy(session = session, remainingSeconds = remaining) }
        if (session?.state == PomodoroState.RUNNING) startTicker(session)
    }

    private fun startTicker(session: PomodoroSession) {
        tickerJob = viewModelScope.launch {
            while (true) {
                val remaining = PomodoroRules.remainingSeconds(session, clock())
                _uiState.update { it.copy(remainingSeconds = remaining) }
                if (remaining == 0L) {
                    coordinator.handleAlarm(session.id, requireNotNull(session.targetEndAt))
                    break
                }
                delay(1_000)
            }
        }
    }

    private fun finishFailure(prefix: String, error: Throwable) {
        _uiState.update { it.copy(operationInProgress = false) }
        showFailure(prefix, error)
    }

    private fun showFailure(prefix: String, error: Throwable) {
        _uiState.update {
            it.copy(isLoading = false, feedback = "$prefix：${error.message ?: "未知错误"}")
        }
    }

    class Factory(
        private val poolRepository: EventPoolRepository,
        private val coordinator: PomodoroCoordinator
    ) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            require(modelClass.isAssignableFrom(PomodoroViewModel::class.java)) { "不支持的 ViewModel 类型" }
            return PomodoroViewModel(poolRepository, coordinator) as T
        }
    }
}

private fun EventPoolItemEntity.toCandidate() = EventPoolCandidate(id, title, category, isEnabled, weight)
