package com.yuncun.noteapp.domain.rules

/** 集中处理用户文本，避免各页面产生不同的空白和去重语义。 */
object TextRules {
    fun normalizeRequiredText(value: String, fieldName: String = "文本"): String {
        val normalized = value.trim()
        require(normalized.isNotEmpty()) { "${fieldName}不能为空" }
        return normalized
    }

    fun normalizeOptionalText(value: String?): String? = value?.trim()?.takeIf { it.isNotEmpty() }

    fun normalizeTags(values: Iterable<String>): List<String> =
        values.map(String::trim).filter(String::isNotEmpty).distinct()
}
