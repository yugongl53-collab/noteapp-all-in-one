package com.yuncun.noteapp.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.FocusState
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEvent
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp

/**
 * 校验与解析结果数据模型。
 */
data class SegmentInputResult(
    val newText: String,
    val autoJump: Boolean = false,
    val accepted: Boolean = true
)

/**
 * 时间与日期分段输入规则：
 * - 小时（0~23）：首位若为 3~9 则判定为单数字并自动跳格；输入两位满位时自动跳格；拦截 > 23。
 * - 分钟（0~59）：首位拦截 > 5；输入两位满位；拦截 > 59。
 * - 月份（1~12）：首位若为 2~9 则判定为单数字并自动跳格；输入两位满位时自动跳格；拦截 00 与 > 12。
 * - 日期（1~31）：结合月份上限拦截越界日期。
 */
object SegmentedDateTimeRules {

    fun parseTimeString(value: String): Pair<String, String> {
        val trimmed = value.trim()
        if (trimmed.isEmpty()) return "" to ""
        val parts = trimmed.split(":")
        val h = parts.getOrNull(0)?.trim().orEmpty()
        val m = parts.getOrNull(1)?.trim().orEmpty()
        return h to m
    }

    fun formatTimeString(hour: String, minute: String, autoPad: Boolean = false): String {
        val h = if (autoPad && hour.isNotEmpty() && hour.length == 1) hour.padStart(2, '0') else hour.trim()
        val m = if (autoPad && minute.isNotEmpty() && minute.length == 1) minute.padStart(2, '0') else minute.trim()
        return if (h.isEmpty() && m.isEmpty()) "" else "$h:$m"
    }

    fun validateHourInput(current: String, input: String): SegmentInputResult {
        val digits = input.filter { it.isDigit() }
        if (digits.isEmpty()) return SegmentInputResult(newText = "", autoJump = false, accepted = true)
        if (digits.length == 1) {
            val d = digits[0]
            return if (d in '3'..'9') {
                // 首位逻辑跳格：3~9 自动跳到分钟
                SegmentInputResult(newText = digits, autoJump = true, accepted = true)
            } else {
                // 0~2 等待第二位
                SegmentInputResult(newText = digits, autoJump = false, accepted = true)
            }
        }
        val firstTwo = digits.take(2)
        val hourInt = firstTwo.toIntOrNull()
        return if (hourInt != null && hourInt in 0..23) {
            // 满位自动跳格
            SegmentInputResult(newText = firstTwo, autoJump = true, accepted = true)
        } else {
            // 拦截非法小时 (> 23)
            SegmentInputResult(newText = current, autoJump = false, accepted = false)
        }
    }

    fun validateMinuteInput(current: String, input: String): SegmentInputResult {
        val digits = input.filter { it.isDigit() }
        if (digits.isEmpty()) return SegmentInputResult(newText = "", autoJump = false, accepted = true)
        if (digits.length == 1) {
            val d = digits[0]
            return if (d in '0'..'5') {
                SegmentInputResult(newText = digits, autoJump = false, accepted = true)
            } else {
                // 分钟首位大于 5 予以拦截
                SegmentInputResult(newText = current, autoJump = false, accepted = false)
            }
        }
        val firstTwo = digits.take(2)
        val minInt = firstTwo.toIntOrNull()
        return if (minInt != null && minInt in 0..59) {
            SegmentInputResult(newText = firstTwo, autoJump = false, accepted = true)
        } else {
            SegmentInputResult(newText = current, autoJump = false, accepted = false)
        }
    }

    fun parseDateString(value: String): Triple<String, String, String> {
        val trimmed = value.trim()
        if (trimmed.isEmpty()) return Triple("", "", "")
        val parts = trimmed.split("-")
        return when {
            parts.size >= 3 -> Triple(parts[0].trim(), parts[1].trim(), parts[2].trim())
            parts.size == 2 -> Triple("", parts[0].trim(), parts[1].trim())
            else -> Triple(trimmed, "", "")
        }
    }

    fun formatDateString(
        year: String,
        month: String,
        day: String,
        autoPad: Boolean = false,
        includeYear: Boolean = true
    ): String {
        val y = year.trim()
        val m = if (autoPad && month.isNotEmpty() && month.length == 1) month.padStart(2, '0') else month.trim()
        val d = if (autoPad && day.isNotEmpty() && day.length == 1) day.padStart(2, '0') else day.trim()
        if (!includeYear) {
            return if (m.isEmpty() && d.isEmpty()) "" else "$m-$d"
        }
        if (y.isEmpty() && m.isEmpty() && d.isEmpty()) return ""
        return "$y-$m-$d"
    }

    fun validateYearInput(current: String, input: String): SegmentInputResult {
        val digits = input.filter { it.isDigit() }
        return if (digits.length <= 4) {
            SegmentInputResult(newText = digits, autoJump = digits.length == 4, accepted = true)
        } else {
            val take4 = digits.take(4)
            SegmentInputResult(newText = take4, autoJump = true, accepted = true)
        }
    }

    fun validateMonthInput(current: String, input: String): SegmentInputResult {
        val digits = input.filter { it.isDigit() }
        if (digits.isEmpty()) return SegmentInputResult(newText = "", autoJump = false, accepted = true)
        if (digits.length == 1) {
            val d = digits[0]
            return if (d in '2'..'9') {
                // 首位逻辑跳格：2~9 判定为单数字月，自动跳转至日
                SegmentInputResult(newText = digits, autoJump = true, accepted = true)
            } else {
                // 0~1 等待第二位
                SegmentInputResult(newText = digits, autoJump = false, accepted = true)
            }
        }
        val firstTwo = digits.take(2)
        val monthInt = firstTwo.toIntOrNull()
        return if (monthInt != null && monthInt in 1..12) {
            // 满位自动跳格
            SegmentInputResult(newText = firstTwo, autoJump = true, accepted = true)
        } else {
            // 拦截非法月份 (00 或 13~99)
            SegmentInputResult(newText = current, autoJump = false, accepted = false)
        }
    }

    fun getMaxDaysInMonth(year: Int?, month: Int?): Int {
        if (month == null || month !in 1..12) return 31
        return when (month) {
            2 -> if (year != null && ((year % 4 == 0 && year % 100 != 0) || (year % 400 == 0))) 29 else 28
            4, 6, 9, 11 -> 30
            else -> 31
        }
    }

    fun validateDayInput(current: String, input: String, year: Int? = null, month: Int? = null): SegmentInputResult {
        val digits = input.filter { it.isDigit() }
        if (digits.isEmpty()) return SegmentInputResult(newText = "", autoJump = false, accepted = true)
        val maxDays = getMaxDaysInMonth(year, month)
        if (digits.length == 1) {
            val d = digits[0]
            return if (d in '4'..'9') {
                // 单数字日 4~9
                if (d.digitToInt() <= maxDays) {
                    SegmentInputResult(newText = digits, autoJump = false, accepted = true)
                } else {
                    SegmentInputResult(newText = current, autoJump = false, accepted = false)
                }
            } else {
                SegmentInputResult(newText = digits, autoJump = false, accepted = true)
            }
        }
        val firstTwo = digits.take(2)
        val dayInt = firstTwo.toIntOrNull()
        return if (dayInt != null && dayInt in 1..maxDays) {
            SegmentInputResult(newText = firstTwo, autoJump = false, accepted = true)
        } else {
            SegmentInputResult(newText = current, autoJump = false, accepted = false)
        }
    }
}

/**
 * 智能时间分段输入器（HH:mm）：
 * 支持时、分独立分段、首位逻辑跳格（3~9）、满位跳格、失焦自动补零及退格回退。
 */
@Composable
fun SegmentedTimeField(
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
    modifier: Modifier = Modifier,
    isError: Boolean = false,
    enabled: Boolean = true
) {
    var hourTfv by remember { mutableStateOf(TextFieldValue("")) }
    var minuteTfv by remember { mutableStateOf(TextFieldValue("")) }
    var isHourFocused by remember { mutableStateOf(false) }
    var isMinuteFocused by remember { mutableStateOf(false) }

    val hourFocusRequester = remember { FocusRequester() }
    val minuteFocusRequester = remember { FocusRequester() }

    // 外部传入值变化时同步内部输入框
    LaunchedEffect(value) {
        val (h, m) = SegmentedDateTimeRules.parseTimeString(value)
        if (h != hourTfv.text) {
            hourTfv = TextFieldValue(h, selection = TextRange(h.length))
        }
        if (m != minuteTfv.text) {
            minuteTfv = TextFieldValue(m, selection = TextRange(m.length))
        }
    }

    val hasFocus = isHourFocused || isMinuteFocused

    Column(modifier = modifier) {
        Surface(
            shape = RoundedCornerShape(8.dp),
            border = BorderStroke(
                width = if (hasFocus) 2.dp else 1.dp,
                color = when {
                    isError -> MaterialTheme.colorScheme.error
                    hasFocus -> MaterialTheme.colorScheme.primary
                    else -> MaterialTheme.colorScheme.outline
                }
            ),
            color = MaterialTheme.colorScheme.surface,
            modifier = Modifier
                .fillMaxWidth()
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                    enabled = enabled
                ) {
                    if (!hasFocus) hourFocusRequester.requestFocus()
                }
        ) {
            Column(
                modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)
            ) {
                Text(
                    text = label,
                    style = MaterialTheme.typography.labelSmall,
                    color = when {
                        isError -> MaterialTheme.colorScheme.error
                        hasFocus -> MaterialTheme.colorScheme.primary
                        else -> MaterialTheme.colorScheme.onSurfaceVariant
                    }
                )
                Spacer(modifier = Modifier.height(2.dp))
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.Center,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(32.dp)
                ) {
                    // 小时输入
                    SegmentCell(
                        value = hourTfv,
                        onValueChange = { newTfv ->
                            val res = SegmentedDateTimeRules.validateHourInput(hourTfv.text, newTfv.text)
                            if (res.accepted) {
                                hourTfv = TextFieldValue(res.newText, selection = TextRange(res.newText.length))
                                onValueChange(
                                    SegmentedDateTimeRules.formatTimeString(res.newText, minuteTfv.text, autoPad = false)
                                )
                                if (res.autoJump) {
                                    minuteFocusRequester.requestFocus()
                                }
                            }
                        },
                        placeholder = "HH",
                        focusRequester = hourFocusRequester,
                        onFocusChanged = { focusState ->
                            isHourFocused = focusState.isFocused
                            if (!focusState.isFocused && hourTfv.text.isNotEmpty()) {
                                val padded = hourTfv.text.padStart(2, '0')
                                if (padded != hourTfv.text) {
                                    hourTfv = TextFieldValue(padded, selection = TextRange(padded.length))
                                }
                                onValueChange(
                                    SegmentedDateTimeRules.formatTimeString(hourTfv.text, minuteTfv.text, autoPad = true)
                                )
                            }
                        },
                        modifier = Modifier.width(36.dp),
                        imeAction = ImeAction.Next,
                        enabled = enabled
                    )

                    Text(
                        text = ":",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(horizontal = 4.dp)
                    )

                    // 分钟输入
                    SegmentCell(
                        value = minuteTfv,
                        onValueChange = { newTfv ->
                            val res = SegmentedDateTimeRules.validateMinuteInput(minuteTfv.text, newTfv.text)
                            if (res.accepted) {
                                minuteTfv = TextFieldValue(res.newText, selection = TextRange(res.newText.length))
                                onValueChange(
                                    SegmentedDateTimeRules.formatTimeString(hourTfv.text, res.newText, autoPad = false)
                                )
                            }
                        },
                        placeholder = "mm",
                        focusRequester = minuteFocusRequester,
                        onFocusChanged = { focusState ->
                            isMinuteFocused = focusState.isFocused
                            if (!focusState.isFocused && minuteTfv.text.isNotEmpty()) {
                                val padded = minuteTfv.text.padStart(2, '0')
                                if (padded != minuteTfv.text) {
                                    minuteTfv = TextFieldValue(padded, selection = TextRange(padded.length))
                                }
                                onValueChange(
                                    SegmentedDateTimeRules.formatTimeString(hourTfv.text, minuteTfv.text, autoPad = true)
                                )
                            }
                        },
                        onPreviewKeyEvent = { keyEvent ->
                            // 空内容退格回退到小时
                            if (keyEvent.type == KeyEventType.KeyDown && keyEvent.key == Key.Backspace) {
                                if (minuteTfv.text.isEmpty() || (minuteTfv.selection.collapsed && minuteTfv.selection.start == 0)) {
                                    hourFocusRequester.requestFocus()
                                    hourTfv = hourTfv.copy(selection = TextRange(hourTfv.text.length))
                                    true
                                } else false
                            } else false
                        },
                        modifier = Modifier.width(36.dp),
                        imeAction = ImeAction.Done,
                        enabled = enabled
                    )
                }
            }
        }
    }
}

/**
 * 智能日期分段输入器（YYYY-MM-DD / MM-DD）：
 * 支持年、月、日分段、首位逻辑跳格（2~9）、满位跳格、失焦自动补零及退格回退。
 */
@Composable
fun SegmentedDateField(
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
    modifier: Modifier = Modifier,
    includeYear: Boolean = true,
    isError: Boolean = false,
    enabled: Boolean = true
) {
    var yearTfv by remember { mutableStateOf(TextFieldValue("")) }
    var monthTfv by remember { mutableStateOf(TextFieldValue("")) }
    var dayTfv by remember { mutableStateOf(TextFieldValue("")) }

    var isYearFocused by remember { mutableStateOf(false) }
    var isMonthFocused by remember { mutableStateOf(false) }
    var isDayFocused by remember { mutableStateOf(false) }

    val yearFocusRequester = remember { FocusRequester() }
    val monthFocusRequester = remember { FocusRequester() }
    val dayFocusRequester = remember { FocusRequester() }

    LaunchedEffect(value) {
        val (y, m, d) = SegmentedDateTimeRules.parseDateString(value)
        if (includeYear && y != yearTfv.text) {
            yearTfv = TextFieldValue(y, selection = TextRange(y.length))
        }
        if (m != monthTfv.text) {
            monthTfv = TextFieldValue(m, selection = TextRange(m.length))
        }
        if (d != dayTfv.text) {
            dayTfv = TextFieldValue(d, selection = TextRange(d.length))
        }
    }

    val hasFocus = isYearFocused || isMonthFocused || isDayFocused

    Column(modifier = modifier) {
        Surface(
            shape = RoundedCornerShape(8.dp),
            border = BorderStroke(
                width = if (hasFocus) 2.dp else 1.dp,
                color = when {
                    isError -> MaterialTheme.colorScheme.error
                    hasFocus -> MaterialTheme.colorScheme.primary
                    else -> MaterialTheme.colorScheme.outline
                }
            ),
            color = MaterialTheme.colorScheme.surface,
            modifier = Modifier
                .fillMaxWidth()
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                    enabled = enabled
                ) {
                    if (!hasFocus) {
                        if (includeYear) yearFocusRequester.requestFocus()
                        else monthFocusRequester.requestFocus()
                    }
                }
        ) {
            Column(
                modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)
            ) {
                Text(
                    text = label,
                    style = MaterialTheme.typography.labelSmall,
                    color = when {
                        isError -> MaterialTheme.colorScheme.error
                        hasFocus -> MaterialTheme.colorScheme.primary
                        else -> MaterialTheme.colorScheme.onSurfaceVariant
                    }
                )
                Spacer(modifier = Modifier.height(2.dp))
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.Center,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(32.dp)
                ) {
                    if (includeYear) {
                        // 年份输入
                        SegmentCell(
                            value = yearTfv,
                            onValueChange = { newTfv ->
                                val res = SegmentedDateTimeRules.validateYearInput(yearTfv.text, newTfv.text)
                                if (res.accepted) {
                                    yearTfv = TextFieldValue(res.newText, selection = TextRange(res.newText.length))
                                    onValueChange(
                                        SegmentedDateTimeRules.formatDateString(
                                            res.newText,
                                            monthTfv.text,
                                            dayTfv.text,
                                            autoPad = false,
                                            includeYear = true
                                        )
                                    )
                                    if (res.autoJump) {
                                        monthFocusRequester.requestFocus()
                                    }
                                }
                            },
                            placeholder = "YYYY",
                            focusRequester = yearFocusRequester,
                            onFocusChanged = { focusState ->
                                isYearFocused = focusState.isFocused
                                if (!focusState.isFocused && yearTfv.text.isNotEmpty()) {
                                    onValueChange(
                                        SegmentedDateTimeRules.formatDateString(
                                            yearTfv.text,
                                            monthTfv.text,
                                            dayTfv.text,
                                            autoPad = true,
                                            includeYear = true
                                        )
                                    )
                                }
                            },
                            modifier = Modifier.width(46.dp),
                            imeAction = ImeAction.Next,
                            enabled = enabled
                        )

                        Text(
                            text = "-",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(horizontal = 2.dp)
                        )
                    }

                    // 月份输入
                    SegmentCell(
                        value = monthTfv,
                        onValueChange = { newTfv ->
                            val res = SegmentedDateTimeRules.validateMonthInput(monthTfv.text, newTfv.text)
                            if (res.accepted) {
                                monthTfv = TextFieldValue(res.newText, selection = TextRange(res.newText.length))
                                onValueChange(
                                    SegmentedDateTimeRules.formatDateString(
                                        yearTfv.text,
                                        res.newText,
                                        dayTfv.text,
                                        autoPad = false,
                                        includeYear = includeYear
                                    )
                                )
                                if (res.autoJump) {
                                    dayFocusRequester.requestFocus()
                                }
                            }
                        },
                        placeholder = "MM",
                        focusRequester = monthFocusRequester,
                        onFocusChanged = { focusState ->
                            isMonthFocused = focusState.isFocused
                            if (!focusState.isFocused && monthTfv.text.isNotEmpty()) {
                                val padded = if (monthTfv.text.length == 1) monthTfv.text.padStart(2, '0') else monthTfv.text
                                if (padded != monthTfv.text) {
                                    monthTfv = TextFieldValue(padded, selection = TextRange(padded.length))
                                }
                                onValueChange(
                                    SegmentedDateTimeRules.formatDateString(
                                        yearTfv.text,
                                        monthTfv.text,
                                        dayTfv.text,
                                        autoPad = true,
                                        includeYear = includeYear
                                    )
                                )
                            }
                        },
                        onPreviewKeyEvent = { keyEvent ->
                            // 空内容退格回退到年
                            if (includeYear && keyEvent.type == KeyEventType.KeyDown && keyEvent.key == Key.Backspace) {
                                if (monthTfv.text.isEmpty() || (monthTfv.selection.collapsed && monthTfv.selection.start == 0)) {
                                    yearFocusRequester.requestFocus()
                                    yearTfv = yearTfv.copy(selection = TextRange(yearTfv.text.length))
                                    true
                                } else false
                            } else false
                        },
                        modifier = Modifier.width(36.dp),
                        imeAction = ImeAction.Next,
                        enabled = enabled
                    )

                    Text(
                        text = "-",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(horizontal = 2.dp)
                    )

                    // 日期输入
                    SegmentCell(
                        value = dayTfv,
                        onValueChange = { newTfv ->
                            val yearVal = yearTfv.text.toIntOrNull()
                            val monthVal = monthTfv.text.toIntOrNull()
                            val res = SegmentedDateTimeRules.validateDayInput(
                                dayTfv.text,
                                newTfv.text,
                                year = yearVal,
                                month = monthVal
                            )
                            if (res.accepted) {
                                dayTfv = TextFieldValue(res.newText, selection = TextRange(res.newText.length))
                                onValueChange(
                                    SegmentedDateTimeRules.formatDateString(
                                        yearTfv.text,
                                        monthTfv.text,
                                        res.newText,
                                        autoPad = false,
                                        includeYear = includeYear
                                    )
                                )
                            }
                        },
                        placeholder = "DD",
                        focusRequester = dayFocusRequester,
                        onFocusChanged = { focusState ->
                            isDayFocused = focusState.isFocused
                            if (!focusState.isFocused && dayTfv.text.isNotEmpty()) {
                                val padded = if (dayTfv.text.length == 1) dayTfv.text.padStart(2, '0') else dayTfv.text
                                if (padded != dayTfv.text) {
                                    dayTfv = TextFieldValue(padded, selection = TextRange(padded.length))
                                }
                                onValueChange(
                                    SegmentedDateTimeRules.formatDateString(
                                        yearTfv.text,
                                        monthTfv.text,
                                        dayTfv.text,
                                        autoPad = true,
                                        includeYear = includeYear
                                    )
                                )
                            }
                        },
                        onPreviewKeyEvent = { keyEvent ->
                            // 空内容退格回退到月
                            if (keyEvent.type == KeyEventType.KeyDown && keyEvent.key == Key.Backspace) {
                                if (dayTfv.text.isEmpty() || (dayTfv.selection.collapsed && dayTfv.selection.start == 0)) {
                                    monthFocusRequester.requestFocus()
                                    monthTfv = monthTfv.copy(selection = TextRange(monthTfv.text.length))
                                    true
                                } else false
                            } else false
                        },
                        modifier = Modifier.width(36.dp),
                        imeAction = ImeAction.Done,
                        enabled = enabled
                    )
                }
            }
        }
    }
}

@Composable
private fun SegmentCell(
    value: TextFieldValue,
    onValueChange: (TextFieldValue) -> Unit,
    placeholder: String,
    focusRequester: FocusRequester,
    onFocusChanged: (FocusState) -> Unit,
    modifier: Modifier = Modifier,
    onPreviewKeyEvent: ((KeyEvent) -> Boolean)? = null,
    imeAction: ImeAction = ImeAction.Next,
    enabled: Boolean = true
) {
    Box(
        modifier = modifier,
        contentAlignment = Alignment.Center
    ) {
        if (value.text.isEmpty()) {
            Text(
                text = placeholder,
                style = MaterialTheme.typography.bodyMedium.copy(textAlign = TextAlign.Center),
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.38f),
                modifier = Modifier.fillMaxWidth()
            )
        }
        BasicTextField(
            value = value,
            onValueChange = onValueChange,
            textStyle = MaterialTheme.typography.bodyMedium.copy(
                textAlign = TextAlign.Center,
                color = MaterialTheme.colorScheme.onSurface
            ),
            cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
            keyboardOptions = KeyboardOptions(
                keyboardType = KeyboardType.Number,
                imeAction = imeAction
            ),
            singleLine = true,
            maxLines = 1,
            enabled = enabled,
            modifier = Modifier
                .fillMaxWidth()
                .focusRequester(focusRequester)
                .onFocusChanged(onFocusChanged)
                .then(
                    if (onPreviewKeyEvent != null) Modifier.onPreviewKeyEvent(onPreviewKeyEvent)
                    else Modifier
                )
        )
    }
}
