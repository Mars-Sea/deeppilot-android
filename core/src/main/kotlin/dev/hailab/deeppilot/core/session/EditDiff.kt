package dev.hailab.deeppilot.core.session

import dev.hailab.deeppilot.core.protocol.ToolEdit

/** One line of a tool edit as the conversation shows it. */
data class EditLine(val kind: Kind, val text: String) {
    enum class Kind { Removed, Added }
}

/** [hidden] counts the changed lines left out to keep the row short. */
data class EditPreview(val lines: List<EditLine>, val hidden: Int)

/**
 * The changed lines of a [ToolEdit] (PROTOCOL.md §4, `tool.edit`). Only the
 * fragment the tool was given is known, never the file, so this is a diff of
 * that fragment: lines shared at both ends are dropped, the rest is shown as
 * removed then added.
 */
object EditDiff {
    fun preview(edit: ToolEdit, maxLines: Int = 12): EditPreview? {
        if (edit.truncated) return null
        val old = if (edit.operation == "replace") edit.oldText?.lines().orEmpty() else emptyList()
        val new = edit.newText?.takeIf { it.isNotEmpty() }?.lines().orEmpty()
        var head = 0
        while (head < old.size && head < new.size && old[head] == new[head]) head++
        var tail = 0
        while (tail < old.size - head && tail < new.size - head && old[old.size - 1 - tail] == new[new.size - 1 - tail]) tail++
        val changed = old.subList(head, old.size - tail).map { EditLine(EditLine.Kind.Removed, it) } +
            new.subList(head, new.size - tail).map { EditLine(EditLine.Kind.Added, it) }
        if (changed.isEmpty()) return null
        return EditPreview(changed.take(maxLines), (changed.size - maxLines).coerceAtLeast(0))
    }
}
