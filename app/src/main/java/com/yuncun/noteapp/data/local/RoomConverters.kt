package com.yuncun.noteapp.data.local

import androidx.room.TypeConverter
import com.yuncun.noteapp.domain.model.EventCategory
import com.yuncun.noteapp.domain.model.ScheduleType
import com.yuncun.noteapp.domain.model.TermSeason
import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

/** Room 类型转换器统一保存稳定标识、ISO 日期和绝对毫秒时间戳。 */
class RoomConverters {
    @TypeConverter
    fun instantToEpochMilli(value: Instant?): Long? = value?.toEpochMilli()

    @TypeConverter
    fun epochMilliToInstant(value: Long?): Instant? = value?.let(Instant::ofEpochMilli)

    @TypeConverter
    fun localDateToIso(value: LocalDate?): String? = value?.toString()

    @TypeConverter
    fun isoToLocalDate(value: String?): LocalDate? = value?.let(LocalDate::parse)

    @TypeConverter
    fun localTimeToMinuteOfDay(value: LocalTime?): Int? = value?.toSecondOfDay()?.div(60)

    @TypeConverter
    fun minuteOfDayToLocalTime(value: Int?): LocalTime? = value?.let { LocalTime.ofSecondOfDay(it * 60L) }

    @TypeConverter
    fun eventCategoryToId(value: EventCategory?): String? = value?.stableId

    @TypeConverter
    fun idToEventCategory(value: String?): EventCategory? = value?.let(EventCategory::fromStableId)

    @TypeConverter
    fun termSeasonToId(value: TermSeason?): String? = value?.stableId

    @TypeConverter
    fun idToTermSeason(value: String?): TermSeason? = value?.let(TermSeason::fromStableId)

    @TypeConverter
    fun scheduleTypeToId(value: ScheduleType?): String? = value?.stableId

    @TypeConverter
    fun idToScheduleType(value: String?): ScheduleType? = value?.let(ScheduleType::fromStableId)

    @TypeConverter
    fun weekdaysToIds(value: Set<DayOfWeek>?): String? =
        value?.map(DayOfWeek::getValue)?.sorted()?.joinToString(",")

    @TypeConverter
    fun idsToWeekdays(value: String?): Set<DayOfWeek>? = value?.let { encoded ->
        if (encoded.isBlank()) emptySet() else encoded.split(',').map { DayOfWeek.of(it.toInt()) }.toSet()
    }

    @TypeConverter
    fun tagsToJson(value: List<String>?): String? = value?.let(Json::encodeToString)

    @TypeConverter
    fun jsonToTags(value: String?): List<String>? = value?.let(Json::decodeFromString)
}
