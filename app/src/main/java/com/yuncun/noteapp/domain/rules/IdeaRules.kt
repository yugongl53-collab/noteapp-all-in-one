package com.yuncun.noteapp.domain.rules

import java.time.Duration
import java.time.Instant

data class NormalizedIdeaInput(
    val content: String,
    val tags: List<String>
)

/** M2 灵感输入与回收站规则，供页面和持久化边界共用。 */
object IdeaRules {
    val retention: Duration = Duration.ofDays(30)

    /** 标签输入支持半角逗号、中文逗号和换行，规范化后保持首次出现顺序。 */
    fun normalize(content: String, tagsInput: String): NormalizedIdeaInput = NormalizedIdeaInput(
        content = TextRules.normalizeRequiredText(content, "灵感正文"),
        tags = TextRules.normalizeTags(tagsInput.split(',', '，', '\n'))
    )

    /** 到达删除时间后的 30×24 小时整点即视为过期。 */
    fun isExpired(deletedAt: Instant, now: Instant): Boolean =
        !now.isBefore(deletedAt.plus(retention))
}
