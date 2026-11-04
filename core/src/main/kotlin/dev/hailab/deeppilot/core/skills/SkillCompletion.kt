package dev.hailab.deeppilot.core.skills

import dev.hailab.deeppilot.core.protocol.SkillSummary
import java.util.Locale

/** 下标遵循 Android 编辑器的 UTF-16 选区约定；不在完整路径、网址或组合输入中触发。 */
data class SkillQuery(val query: String, val start: Int, val end: Int) {
    fun complete(name: String, draft: String): SkillTextCompletion? {
        if (start !in 0..draft.length || end !in start..draft.length || name.isEmpty() || !name.all(::nameCharacter)) return null
        val hasSpace = draft.getOrNull(end) == ' '
        val insertion = "/" + name + if (hasSpace) "" else " "
        return SkillTextCompletion(draft.replaceRange(start, end, insertion), start + insertion.length + if (hasSpace) 1 else 0)
    }

    /** 与网页端一致：有序子序列匹配，前缀优先，保持主机的同分顺序。 */
    fun matches(skills: List<SkillSummary>): List<SkillSummary> {
        val needle = query.lowercase(Locale.ROOT)
        return skills.filter { skill ->
            var matched = 0
            for (c in skill.name.lowercase(Locale.ROOT)) if (matched < needle.length && needle[matched] == c) matched++
            matched == needle.length
        }.sortedBy { !it.name.lowercase(Locale.ROOT).startsWith(needle) }
    }

    companion object {
        fun detect(draft: String, selectionStart: Int, selectionEnd: Int = selectionStart, composing: Boolean = false): SkillQuery? {
            if (composing || selectionStart != selectionEnd || selectionStart !in 0..draft.length) return null
            val start = draft.take(selectionStart).indexOfLast { it.isWhitespace() } + 1
            val end = draft.indexOfFirstAfter(selectionStart) { it.isWhitespace() }
            if (selectionStart <= start || draft.getOrNull(start) != '/' || !draft.substring(start + 1, end).all(::nameCharacter)) return null
            return SkillQuery(draft.substring(start + 1, selectionStart), start, end)
        }

        private fun String.indexOfFirstAfter(start: Int, predicate: (Char) -> Boolean): Int {
            for (index in start until length) if (predicate(this[index])) return index
            return length
        }
    }
}

private fun nameCharacter(c: Char): Boolean = c in 'a'..'z' || c in 'A'..'Z' || c in '0'..'9' || c == '-' || c == '_'
data class SkillTextCompletion(val text: String, val caret: Int)
