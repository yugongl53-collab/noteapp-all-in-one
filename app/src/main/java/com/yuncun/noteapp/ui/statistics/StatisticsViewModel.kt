package com.yuncun.noteapp.ui.statistics

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.yuncun.noteapp.data.local.entity.TimeRecordEntity
import com.yuncun.noteapp.data.repository.TimeRecordRepository
import com.yuncun.noteapp.domain.model.EventCategory
import com.yuncun.noteapp.domain.rules.TimeRecordRules
import com.yuncun.noteapp.domain.rules.TimeRecordSnapshot
import com.yuncun.noteapp.domain.rules.TimeStatistics
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.ZoneId
import java.time.format.DateTimeParseException
import java.time.temporal.TemporalAdjusters
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

enum class StatisticsPeriod { DAY, WEEK }
enum class StatisticsRanking { CATEGORY, TITLE }

data class TimeRecordDraftState(
    val id: String? = null,
    val title: String = "",
    val category: EventCategory = EventCategory.WORK,
    val startDate: String = "",
    val startTime: String = "",
    val endDate: String = "",
    val endTime: String = "",
    val error: String? = null,
    val isSaving: Boolean = false,
    val isOpen: Boolean = false
)

data class StatisticsUiState(
    val isLoading: Boolean = true,
    val loadError: String? = null,
    val records: List<TimeRecordEntity> = emptyList(),
    val visibleRecords: List<TimeRecordEntity> = emptyList(),
    val nameSuggestions: List<String> = emptyList(),
    val period: StatisticsPeriod = StatisticsPeriod.DAY,
    val ranking: StatisticsRanking = StatisticsRanking.CATEGORY,
    val selectedDate: LocalDate,
    val statistics: TimeStatistics? = null,
    val operationInProgress: Boolean = false,
    val feedback: String? = null,
    val actionCompletedVersion: Long = 0
)

/** M6 状态层以当前设备时区重算日周统计，并在写入失败时保留完整表单输入。 */
class StatisticsViewModel(
    private val repository: TimeRecordRepository,
    private val clock: () -> Instant = Instant::now,
    private val zoneId: () -> ZoneId = ZoneId::systemDefault
) : ViewModel() {
    private val initialDate = clock().atZone(zoneId()).toLocalDate()
    private val _uiState = MutableStateFlow(StatisticsUiState(selectedDate = initialDate))
    val uiState: StateFlow<StatisticsUiState> = _uiState.asStateFlow()

    private val _draft = MutableStateFlow(TimeRecordDraftState())
    val draft: StateFlow<TimeRecordDraftState> = _draft.asStateFlow()

    init {
        refresh()
    }

    fun refresh() {
        _uiState.update { it.copy(isLoading = true, loadError = null) }
        viewModelScope.launch {
            runCatching { repository.load() }
                .onSuccess { records -> applyRecords(records) }
                .onFailure { error ->
                    _uiState.update {
                        it.copy(
                            isLoading = false,
                            loadError = "统计读取失败：${error.message ?: "未知错误"}"
                        )
                    }
                }
        }
    }

    fun selectPeriod(period: StatisticsPeriod) {
        _uiState.update { recalculate(it.copy(period = period), it.records) }
    }

    fun selectRanking(ranking: StatisticsRanking) {
        _uiState.update { it.copy(ranking = ranking) }
    }

    fun previousPeriod() = movePeriod(-1)
    fun nextPeriod() = movePeriod(1)

    fun currentPeriod() {
        val today = clock().atZone(zoneId()).toLocalDate()
        _uiState.update { recalculate(it.copy(selectedDate = today), it.records) }
    }

    /** 新建记录默认落在当前统计日；今天使用当前分钟，历史日期使用 09:00。 */
    fun prepareNewRecord() {
        val state = _uiState.value
        val now = clock().atZone(zoneId()).withSecond(0).withNano(0)
        val startDateTime = if (state.selectedDate == now.toLocalDate()) {
            now.toLocalDateTime()
        } else {
            state.selectedDate.atTime(9, 0)
        }
        val endDateTime = startDateTime.plusHours(1)
        _draft.value = TimeRecordDraftState(
            startDate = startDateTime.toLocalDate().toString(),
            startTime = startDateTime.toLocalTime().toString(),
            endDate = endDateTime.toLocalDate().toString(),
            endTime = endDateTime.toLocalTime().toString(),
            isOpen = true
        )
    }

    fun prepareEditRecord(id: String) {
        val record = _uiState.value.records.firstOrNull { it.id == id }
        if (record == null) {
            _uiState.update { it.copy(feedback = "时间记录不存在") }
            return
        }
        val start = record.startAt.atZone(zoneId())
        val end = record.endAt.atZone(zoneId())
        _draft.value = TimeRecordDraftState(
            id = record.id,
            title = record.title,
            category = record.category,
            startDate = start.toLocalDate().toString(),
            startTime = start.toLocalTime().withSecond(0).withNano(0).toString(),
            endDate = end.toLocalDate().toString(),
            endTime = end.toLocalTime().withSecond(0).withNano(0).toString(),
            isOpen = true
        )
    }

    fun updateTitle(value: String) = _draft.update { it.copy(title = value, error = null) }
    fun updateCategory(value: EventCategory) = _draft.update { it.copy(category = value, error = null) }
    fun updateStartDate(value: String) = _draft.update { it.copy(startDate = value, error = null) }
    fun updateStartTime(value: String) = _draft.update { it.copy(startTime = value, error = null) }
    fun updateEndDate(value: String) = _draft.update { it.copy(endDate = value, error = null) }
    fun updateEndTime(value: String) = _draft.update { it.copy(endTime = value, error = null) }

    fun dismissEditor() {
        if (!_draft.value.isSaving) _draft.value = TimeRecordDraftState()
    }

    fun saveDraft() {
        val draft = _draft.value
        val range = runCatching {
            resolveInstant(draft.startDate, draft.startTime) to resolveInstant(draft.endDate, draft.endTime)
        }.getOrElse { error ->
            _draft.update { it.copy(error = error.message ?: "请输入有效的日期和时间") }
            return
        }
        _draft.update { it.copy(isSaving = true, error = null) }
        viewModelScope.launch {
            runCatching {
                repository.save(draft.id, draft.title, draft.category, range.first, range.second, clock())
                repository.load()
            }.onSuccess { records ->
                _draft.value = TimeRecordDraftState()
                applyRecords(records, "时间记录已保存", completed = true)
            }.onFailure { error ->
                _draft.update {
                    it.copy(isSaving = false, error = error.message ?: "保存失败，请重试")
                }
            }
        }
    }

    fun deleteRecord(id: String) = runOperation("时间记录已永久删除") {
        repository.delete(id)
    }

    fun consumeFeedback() {
        _uiState.update { it.copy(feedback = null) }
    }

    private fun movePeriod(direction: Long) {
        val days = if (_uiState.value.period == StatisticsPeriod.DAY) direction else direction * 7
        _uiState.update { recalculate(it.copy(selectedDate = it.selectedDate.plusDays(days)), it.records) }
    }

    private fun runOperation(successMessage: String, action: suspend () -> Unit) {
        if (_uiState.value.operationInProgress) return
        _uiState.update { it.copy(operationInProgress = true) }
        viewModelScope.launch {
            runCatching {
                action()
                repository.load()
            }.onSuccess { records -> applyRecords(records, successMessage, completed = true) }
                .onFailure { error ->
                    _uiState.update {
                        it.copy(
                            operationInProgress = false,
                            feedback = "操作失败：${error.message ?: "未知错误"}"
                        )
                    }
                }
        }
    }

    /** 每次 Room 快照变化都同时重建快捷名称、明细和统计，删除不会留下缓存。 */
    private fun applyRecords(records: List<TimeRecordEntity>, feedback: String? = null, completed: Boolean = false) {
        val suggestions = records.sortedByDescending { it.updatedAt }
            .map { it.title.trim() }
            .filter { it.isNotEmpty() }
            .distinct()
        _uiState.update { current ->
            recalculate(
                current.copy(
                    isLoading = false,
                    loadError = null,
                    records = records,
                    nameSuggestions = suggestions,
                    operationInProgress = false,
                    feedback = feedback,
                    actionCompletedVersion = current.actionCompletedVersion + if (completed) 1 else 0
                ),
                records
            )
        }
    }

    private fun recalculate(state: StatisticsUiState, records: List<TimeRecordEntity>): StatisticsUiState {
        val (startDate, endDate) = dateRange(state.period, state.selectedDate)
        val zone = zoneId()
        val startAt = startDate.atStartOfDay(zone).toInstant()
        val endAt = endDate.plusDays(1).atStartOfDay(zone).toInstant()
        val visibleRecords = records.filter { it.startAt < endAt && it.endAt > startAt }
        val statistics = TimeRecordRules.calculateStatistics(
            records.map(TimeRecordEntity::toSnapshot),
            startDate,
            endDate,
            zone
        )
        return state.copy(statistics = statistics, visibleRecords = visibleRecords)
    }

    private fun dateRange(period: StatisticsPeriod, selectedDate: LocalDate): Pair<LocalDate, LocalDate> =
        if (period == StatisticsPeriod.DAY) {
            selectedDate to selectedDate
        } else {
            val monday = selectedDate.with(TemporalAdjusters.previousOrSame(java.time.DayOfWeek.MONDAY))
            monday to monday.plusDays(6)
        }

    /** 夏令时跳过的当地时间没有有效偏移，必须提示用户修改而不是静默挪动。 */
    private fun resolveInstant(dateInput: String, timeInput: String): Instant {
        val localDateTime = try {
            LocalDateTime.of(LocalDate.parse(dateInput.trim()), LocalTime.parse(timeInput.trim()))
        } catch (_: DateTimeParseException) {
            throw IllegalArgumentException("日期或时间格式无效，请使用 YYYY-MM-DD 和 HH:mm")
        }
        val zone = zoneId()
        val offsets = zone.rules.getValidOffsets(localDateTime)
        require(offsets.isNotEmpty()) { "该当地时间不存在（夏令时切换），请修改时间" }
        return localDateTime.atOffset(offsets.first()).toInstant()
    }

    class Factory(private val repository: TimeRecordRepository) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            require(modelClass.isAssignableFrom(StatisticsViewModel::class.java)) { "不支持的 ViewModel 类型" }
            return StatisticsViewModel(repository) as T
        }
    }
}

private fun TimeRecordEntity.toSnapshot() = TimeRecordSnapshot(id, title, category, startAt, endAt)
