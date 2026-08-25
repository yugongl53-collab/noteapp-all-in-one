package com.yuncun.noteapp.ui.schedule

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.yuncun.noteapp.data.local.entity.AcademicTermEntity
import com.yuncun.noteapp.data.local.entity.CourseScheduleEntity
import com.yuncun.noteapp.data.local.entity.ScheduleTaskEntity
import com.yuncun.noteapp.data.repository.AcademicTermInput
import com.yuncun.noteapp.data.repository.CourseScheduleInput
import com.yuncun.noteapp.data.repository.ScheduleRepository
import com.yuncun.noteapp.data.repository.ScheduleSnapshot
import com.yuncun.noteapp.data.repository.ScheduleTaskInput
import com.yuncun.noteapp.domain.model.CourseRule
import com.yuncun.noteapp.domain.model.ScheduleInstance
import com.yuncun.noteapp.domain.model.ScheduleRule
import com.yuncun.noteapp.domain.model.TermPeriod
import com.yuncun.noteapp.domain.rules.AcademicCalendarRules
import com.yuncun.noteapp.domain.rules.EventStreamItem
import com.yuncun.noteapp.domain.rules.ScheduleConflictRules
import com.yuncun.noteapp.domain.rules.ScheduleExpansionRules
import com.yuncun.noteapp.domain.rules.ScheduleViewRules
import com.yuncun.noteapp.reminder.NoOpReminderCoordinator
import com.yuncun.noteapp.reminder.ReminderCoordinator
import com.yuncun.noteapp.reminder.ReminderSyncResult
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

enum class ScheduleViewMode { TIMETABLE, EVENT_STREAM }

data class ScheduleUiState(
    val isLoading: Boolean = true,
    val selectedWeek: LocalDate,
    val viewMode: ScheduleViewMode = ScheduleViewMode.TIMETABLE,
    val terms: List<AcademicTermEntity> = emptyList(),
    val tasks: List<ScheduleTaskEntity> = emptyList(),
    val courses: List<CourseScheduleEntity> = emptyList(),
    val instances: List<ScheduleInstance> = emptyList(),
    val eventStream: List<EventStreamItem> = emptyList(),
    val weekLabels: List<String> = emptyList(),
    val overlappingIds: Set<String> = emptySet(),
    val taskNameSuggestions: List<String> = emptyList(),
    val courseNameSuggestions: List<String> = emptyList(),
    val operationInProgress: Boolean = false,
    val overlapConfirmationRequired: Boolean = false,
    val feedback: String? = null,
    val actionCompletedVersion: Long = 0
)

private sealed interface PendingSave {
    data class Task(val id: String?, val input: ScheduleTaskInput) : PendingSave
    data class Course(val id: String?, val input: CourseScheduleInput) : PendingSave
}

/** M3 状态层统一展开两种视图，并让所有写操作成功后重新读取 Room。 */
class ScheduleViewModel(
    private val repository: ScheduleRepository,
    private val reminderCoordinator: ReminderCoordinator = NoOpReminderCoordinator,
    private val clock: () -> Instant = Instant::now,
    private val zoneId: ZoneId = ZoneId.systemDefault(),
    private val today: () -> LocalDate = { LocalDate.now(zoneId) }
) : ViewModel() {
    private var pendingSave: PendingSave? = null
    private val _uiState = MutableStateFlow(ScheduleUiState(selectedWeek = today()))
    val uiState: StateFlow<ScheduleUiState> = _uiState.asStateFlow()

    init {
        refresh()
    }

    fun refresh() {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true) }
            runCatching { repository.load() }
                .onSuccess { snapshot -> applySnapshot(snapshot) }
                .onFailure { error -> showFailure("加载日程失败", error) }
        }
    }

    fun selectView(mode: ScheduleViewMode) {
        // 事件流定义为“本周接下来”，切入时主动回到当前自然周。
        _uiState.update {
            val selectedWeek = if (mode == ScheduleViewMode.EVENT_STREAM) today() else it.selectedWeek
            derive(it.copy(viewMode = mode, selectedWeek = selectedWeek))
        }
    }

    fun previousWeek() = selectWeek(_uiState.value.selectedWeek.minusWeeks(1))
    fun nextWeek() = selectWeek(_uiState.value.selectedWeek.plusWeeks(1))
    fun currentWeek() = selectWeek(today())

    fun saveTerm(id: String?, input: AcademicTermInput) = runOperation("学期已保存") {
        repository.saveTerm(id, input, clock())
    }

    fun deleteTerm(id: String) = runOperation("学期已删除") { repository.deleteTerm(id) }

    fun saveTask(id: String?, input: ScheduleTaskInput) {
        val candidate = input.toRule(id ?: "__pending_task__")
        val snapshot = _uiState.value
        val conflictsTask = snapshot.tasks.filterNot { it.id == id }.any { existing ->
            ScheduleConflictRules.tasksConflict(candidate, existing.toRule())
        }
        val terms = snapshot.terms.associateBy { it.id }
        val conflictsCourse = snapshot.courses.any { course ->
            terms[course.termId]?.let { term ->
                ScheduleConflictRules.taskAndCourseConflict(candidate, course.toRule(), term.toPeriod())
            } == true
        }
        if (conflictsTask || conflictsCourse) {
            pendingSave = PendingSave.Task(id, input)
            _uiState.update { it.copy(overlapConfirmationRequired = true) }
        } else {
            persistTask(id, input)
        }
    }

    fun saveCourse(id: String?, input: CourseScheduleInput) {
        val candidate = input.toRule(id ?: "__pending_course__")
        val snapshot = _uiState.value
        val term = snapshot.terms.firstOrNull { it.id == input.termId }
        val conflictsCourse = snapshot.courses.filterNot { it.id == id }.any { existing ->
            ScheduleConflictRules.coursesConflict(candidate, existing.toRule())
        }
        val conflictsTask = term != null && snapshot.tasks.any { task ->
            ScheduleConflictRules.taskAndCourseConflict(task.toRule(), candidate, term.toPeriod())
        }
        if (conflictsCourse || conflictsTask) {
            pendingSave = PendingSave.Course(id, input)
            _uiState.update { it.copy(overlapConfirmationRequired = true) }
        } else {
            persistCourse(id, input)
        }
    }

    /** 用户确认后只执行先前已经完整校验过的待保存输入。 */
    fun confirmOverlapSave() {
        val pending = pendingSave ?: return
        pendingSave = null
        _uiState.update { it.copy(overlapConfirmationRequired = false) }
        when (pending) {
            is PendingSave.Task -> persistTask(pending.id, pending.input)
            is PendingSave.Course -> persistCourse(pending.id, pending.input)
        }
    }

    fun cancelOverlapSave() {
        pendingSave = null
        _uiState.update { it.copy(overlapConfirmationRequired = false) }
    }

    fun deleteTask(id: String) = runOperation("普通事件已删除") { repository.deleteTask(id) }
    fun deleteCourse(id: String) = runOperation("课程已删除") { repository.deleteCourse(id) }

    fun consumeFeedback() {
        _uiState.update { it.copy(feedback = null) }
    }

    private fun selectWeek(date: LocalDate) {
        _uiState.update { derive(it.copy(selectedWeek = AcademicCalendarRules.weekStart(date))) }
    }

    private fun persistTask(id: String?, input: ScheduleTaskInput) = runOperation(
        successMessage = "普通事件已保存",
        warnIfPermissionMissing = input.isEnabled && input.reminderEnabled
    ) {
        repository.saveTask(id, input, clock())
    }

    private fun persistCourse(id: String?, input: CourseScheduleInput) = runOperation(
        successMessage = "课程已保存",
        warnIfPermissionMissing = input.reminderEnabled
    ) {
        repository.saveCourse(id, input, clock())
    }

    private fun runOperation(
        successMessage: String,
        warnIfPermissionMissing: Boolean = false,
        action: suspend () -> Unit
    ) {
        if (_uiState.value.operationInProgress) return
        _uiState.update { it.copy(operationInProgress = true) }
        viewModelScope.launch {
            runCatching {
                action()
                val snapshot = repository.load()
                snapshot to reminderCoordinator.synchronize()
            }.onSuccess { (snapshot, reminderResult) ->
                applySnapshot(
                    snapshot,
                    reminderFeedback(successMessage, reminderResult, warnIfPermissionMissing),
                    completed = true
                )
            }.onFailure { error ->
                _uiState.update { it.copy(operationInProgress = false) }
                showFailure("操作失败", error)
            }
        }
    }

    private fun applySnapshot(snapshot: ScheduleSnapshot, feedback: String? = null, completed: Boolean = false) {
        _uiState.update { current ->
            derive(
                current.copy(
                    isLoading = false,
                    terms = snapshot.terms,
                    tasks = snapshot.tasks,
                    courses = snapshot.courses,
                    operationInProgress = false,
                    feedback = feedback,
                    actionCompletedVersion = current.actionCompletedVersion + if (completed) 1 else 0
                )
            )
        }
    }

    /** 每次数据、周次或视图变化都从同一份实体快照重新派生展示结果。 */
    private fun derive(state: ScheduleUiState): ScheduleUiState {
        val terms = state.terms.map { it.toPeriod() }
        val instances = ScheduleExpansionRules.expandWeek(
            weekDate = state.selectedWeek,
            schedules = state.tasks.map { it.toRule() },
            courses = state.courses.map { it.toRule() },
            terms = terms,
            zoneId = zoneId
        )
        return state.copy(
            selectedWeek = AcademicCalendarRules.weekStart(state.selectedWeek),
            instances = instances,
            eventStream = ScheduleViewRules.eventStream(instances, clock()),
            weekLabels = AcademicCalendarRules.labelsForWeek(state.selectedWeek, terms),
            overlappingIds = ScheduleViewRules.overlappingIds(instances),
            taskNameSuggestions = ScheduleViewRules.distinctRecentNames(state.tasks.map { it.title to it.updatedAt }),
            courseNameSuggestions = ScheduleViewRules.distinctRecentNames(state.courses.map { it.courseName to it.updatedAt })
        )
    }

    private fun showFailure(prefix: String, error: Throwable) {
        _uiState.update {
            it.copy(isLoading = false, feedback = "$prefix：${error.message ?: "未知错误"}")
        }
    }

    private fun reminderFeedback(
        successMessage: String,
        result: ReminderSyncResult,
        warnIfPermissionMissing: Boolean
    ): String = when {
        result.errorMessage != null -> "$successMessage，但提醒未生效：${result.errorMessage}"
        warnIfPermissionMissing && !result.permissions.isEffective ->
            "$successMessage；提醒已配置但未生效：缺少${result.permissions.missingReason()}"
        else -> successMessage
    }

    class Factory(
        private val repository: ScheduleRepository,
        private val reminderCoordinator: ReminderCoordinator = NoOpReminderCoordinator
    ) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            require(modelClass.isAssignableFrom(ScheduleViewModel::class.java)) { "不支持的 ViewModel 类型" }
            return ScheduleViewModel(repository, reminderCoordinator) as T
        }
    }
}

private fun ScheduleTaskEntity.toRule() = ScheduleRule(
    id, title, category, type, weekdays, effectiveFrom, date, startTime, endTime, isEnabled
)

private fun ScheduleTaskInput.toRule(id: String) = ScheduleRule(
    id, title, category, type, weekdays, effectiveFrom, date, startTime, endTime, isEnabled
)

private fun CourseScheduleEntity.toRule() = CourseRule(
    id, termId, courseName, location, weekdays, startTime, endTime, startWeek, endWeek
)

private fun CourseScheduleInput.toRule(id: String) = CourseRule(
    id, termId, courseName, location, weekdays, startTime, endTime, startWeek, endWeek
)
