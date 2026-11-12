package dev.hailab.deeppilot.core.references

import dev.hailab.deeppilot.core.protocol.FileReference
import dev.hailab.deeppilot.core.protocol.SessionReference

/**
 * The `@` word under the caret (PROTOCOL.md, composer references). Indices follow
 * the editor's UTF-16 selection. [query] is what goes to `c2s.session.references`:
 * the text after `@`, without the opening quote of `@"…`.
 */
data class ReferenceQuery(val query: String, val start: Int, val end: Int) {
    /** `@path`, quoted when it holds whitespace. A directory keeps the caret on it so typing goes on. */
    fun complete(file: FileReference, draft: String): ReferenceTextCompletion? {
        if (file.path.isEmpty() || file.path.any { it == '\n' || it == '"' }) return null
        val directory = file.kind == "directory"
        val path = if (directory && !file.path.endsWith("/")) file.path + "/" else file.path
        val quoted = path.any { it.isWhitespace() }
        if (directory) {
            // Left open (`@"dir/`) so the next keystroke keeps completing inside it.
            return insert(draft, if (quoted) "@\"$path" else "@$path", trailingSpace = false)
        }
        return insert(draft, if (quoted) "@\"$path\"" else "@$path", trailingSpace = true)
    }

    /** The host's canonical mention, unchanged; the host rewrites it for the model. */
    fun complete(session: SessionReference, draft: String): ReferenceTextCompletion? {
        if (session.mention.isEmpty() || session.mention.contains('\n')) return null
        return insert(draft, session.mention, trailingSpace = true)
    }

    private fun insert(draft: String, text: String, trailingSpace: Boolean): ReferenceTextCompletion? {
        if (start !in 0..draft.length || end !in start..draft.length) return null
        val hasSpace = draft.getOrNull(end) == ' '
        val insertion = text + if (trailingSpace && !hasSpace) " " else ""
        val caret = start + insertion.length + if (trailingSpace && hasSpace) 1 else 0
        return ReferenceTextCompletion(draft.replaceRange(start, end, insertion), caret)
    }

    companion object {
        const val MAX_QUERY_LENGTH = 1024

        /**
         * Null unless the caret sits in a word that starts with `@`: mail addresses,
         * finished `@[label](…)` mentions, composing text and selections never trigger.
         */
        fun detect(draft: String, selectionStart: Int, selectionEnd: Int = selectionStart, composing: Boolean = false): ReferenceQuery? {
            if (composing || selectionStart != selectionEnd || selectionStart !in 0..draft.length) return null
            val lineStart = draft.lastIndexOf('\n', selectionStart - 1) + 1
            quoted(draft, lineStart, selectionStart)?.let { return it }
            val start = draft.take(selectionStart).indexOfLast { it.isWhitespace() } + 1
            if (selectionStart <= start || draft[start] != '@') return null
            var end = selectionStart
            while (end < draft.length && !draft[end].isWhitespace()) end++
            val query = draft.substring(start + 1, selectionStart)
            if (query.startsWith("[") || query.startsWith("\"") || query.length > MAX_QUERY_LENGTH) return null
            return ReferenceQuery(query, start, end)
        }

        /** An open `@"…` on the caret's line; it may hold spaces. */
        private fun quoted(draft: String, lineStart: Int, caret: Int): ReferenceQuery? {
            val open = draft.lastIndexOf("@\"", caret - 2)
            if (open < lineStart || (open > 0 && !draft[open - 1].isWhitespace())) return null
            val query = draft.substring(open + 2, caret)
            if (query.contains('"') || query.length > MAX_QUERY_LENGTH) return null
            var end = caret
            while (end < draft.length && draft[end] != '"' && draft[end] != '\n') end++
            if (end < draft.length && draft[end] == '"') end++ else end = caret
            return ReferenceQuery(query, open, end)
        }
    }
}

data class ReferenceTextCompletion(val text: String, val caret: Int)
