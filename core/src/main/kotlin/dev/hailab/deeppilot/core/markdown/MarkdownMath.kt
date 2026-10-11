package dev.hailab.deeppilot.core.markdown

/** One run of inline Markdown: literal markup, or a TeX expression typeset in the sentence. */
sealed interface InlinePiece {
    data class Text(val text: String) : InlinePiece
    data class Math(val tex: String) : InlinePiece
}

/** A display-math block found at a line index. */
data class DisplayMath(val tex: String, val lineCount: Int)

data class MathSegment(val tex: String, val display: Boolean)

/**
 * Markdown with every formula swapped for a placeholder, plus the formulas.
 * The Markdown renderer never sees TeX, so `_`, `*` and `\\` inside a formula
 * cannot turn into emphasis or escapes; the page swaps KaTeX output back in.
 */
data class PreparedMarkdown(val markdown: String, val math: List<MathSegment>)

/**
 * Finds TeX math in assistant Markdown. Ported from the iOS MarkdownMathScanner
 * so a reply reads the same on both phones and in the Host Web UI.
 *
 * Delimiters: `$…$`, `\(…\)`, `\[…\]`, `$$…$$` and a ```` ```math ```` fence.
 * Scanning is escape- and code-aware, CJK between dollar signs is prose
 * (prices), and every unterminated delimiter stays literal: a message that
 * is still streaming must never drop or reframe what it has received so far.
 */
object MarkdownMath {
    /** Placeholder brackets from the Private Use Area; Markdown leaves them alone. */
    const val OPEN = ''
    const val CLOSE = ''

    fun placeholder(index: Int) = "$OPEN$index$CLOSE"

    fun prepare(source: String): PreparedMarkdown {
        if (!mayContainMath(source)) return PreparedMarkdown(source, emptyList())
        val lines = source.split("\n")
        val math = mutableListOf<MathSegment>()
        val out = mutableListOf<String>()
        var index = 0
        while (index < lines.size) {
            val line = lines[index]
            val fence = fenceOf(line)
            if (fence != null) {
                val end = closingFence(lines, index, fence)
                if (fence.info == "math" && end != null) {
                    val tex = lines.subList(index + 1, end).joinToString("\n").trim()
                    if (tex.isNotEmpty()) {
                        out += displayBlock(math, tex)
                        index = end + 1
                        continue
                    }
                }
                // Ordinary (or still-streaming) code: copied verbatim, never scanned.
                val last = end ?: (lines.size - 1)
                out += lines.subList(index, last + 1)
                index = last + 1
                continue
            }
            val display = displayMath(lines, index)
            if (display != null) {
                out += displayBlock(math, display.tex)
                index += display.lineCount
                continue
            }
            out += splitInline(line).joinToString("") { piece ->
                when (piece) {
                    is InlinePiece.Text -> piece.text
                    is InlinePiece.Math -> {
                        math += MathSegment(piece.tex, display = false)
                        placeholder(math.size - 1)
                    }
                }
            }
            index += 1
        }
        return PreparedMarkdown(out.joinToString("\n"), math)
    }

    /** A display placeholder is its own paragraph, so it interrupts the text around it. */
    private fun displayBlock(math: MutableList<MathSegment>, tex: String): List<String> {
        math += MathSegment(tex, display = true)
        return listOf("", placeholder(math.size - 1), "")
    }

    fun mayContainMath(source: String) = source.contains('$') || source.contains('\\') || source.contains("math")

    fun containsInlineMath(source: String) = splitInline(source).any { it is InlinePiece.Math }

    // ---------- inline ----------

    fun splitInline(source: String): List<InlinePiece> {
        if (!source.contains('$') && !source.contains('\\')) return listOf(InlinePiece.Text(source))
        val chars = source
        val pieces = mutableListOf<InlinePiece>()
        var textStart = 0
        var index = 0

        fun flushText(end: Int) {
            if (end > textStart) pieces += InlinePiece.Text(chars.substring(textStart, end))
        }

        while (index < chars.length) {
            val c = chars[index]
            if (c == '`') {
                index = endOfCodeSpan(chars, index) ?: (index + 1)
                continue
            }
            if (c == '\\' && index + 1 < chars.length && !isEscaped(chars, index)) {
                val closer = when (chars[index + 1]) {
                    '(' -> ')'
                    '[' -> ']'
                    else -> null
                }
                val found = closer?.let { scanBackslashMath(chars, index, it) }
                if (found != null) {
                    flushText(index)
                    pieces += InlinePiece.Math(found.first)
                    index = found.second
                    textStart = index
                    continue
                }
                index += 2
                continue
            }
            if (c == '$' && !isEscaped(chars, index)) {
                val found = scanDollarMath(chars, index)
                if (found != null) {
                    flushText(index)
                    pieces += InlinePiece.Math(found.first)
                    index = found.second
                    textStart = index
                    continue
                }
                index += 1
                continue
            }
            index += 1
        }
        flushText(chars.length)
        return pieces.ifEmpty { listOf(InlinePiece.Text(source)) }
    }

    private fun isEscaped(chars: String, index: Int): Boolean {
        var count = 0
        var cursor = index - 1
        while (cursor >= 0 && chars[cursor] == '\\') {
            count++
            cursor--
        }
        return count % 2 == 1
    }

    private fun endOfCodeSpan(chars: String, from: Int): Int? {
        var fence = 0
        var cursor = from
        while (cursor < chars.length && chars[cursor] == '`') {
            fence++
            cursor++
        }
        while (cursor < chars.length) {
            if (chars[cursor] != '`') {
                cursor++
                continue
            }
            var run = 0
            while (cursor < chars.length && chars[cursor] == '`') {
                run++
                cursor++
            }
            if (run == fence) return cursor
        }
        return null
    }

    /** `\(…\)` or `\[…\]` on one line; [index] points at the backslash. */
    private fun scanBackslashMath(chars: String, index: Int, closer: Char): Pair<String, Int>? {
        var cursor = index + 2
        val content = StringBuilder()
        while (cursor < chars.length) {
            val c = chars[cursor]
            if (c == '\n') return null
            if (c == '\\' && cursor + 1 < chars.length && !isEscaped(chars, cursor)) {
                if (chars[cursor + 1] == closer) {
                    val tex = content.toString().trim(' ', '\t')
                    return if (tex.isEmpty()) null else tex to cursor + 2
                }
                content.append(c).append(chars[cursor + 1])
                cursor += 2
                continue
            }
            content.append(c)
            cursor++
        }
        return null
    }

    /** `$…$` or `$$…$$` on one line; [index] points at the dollar. */
    private fun scanDollarMath(chars: String, index: Int): Pair<String, Int>? {
        val doubled = index + 1 < chars.length && chars[index + 1] == '$'
        val start = index + if (doubled) 2 else 1
        if (start >= chars.length || chars[start] == '\n') return null
        var cursor = start
        while (cursor < chars.length) {
            val c = chars[cursor]
            if (c == '\n') return null
            if (c == '$' && !isEscaped(chars, cursor)) {
                if (doubled && (cursor + 1 >= chars.length || chars[cursor + 1] != '$')) {
                    cursor++
                    continue
                }
                val tex = chars.substring(start, cursor).trim(' ', '\t')
                // Empty is not a formula, and CJK between two dollars is prose (prices, ranges).
                if (tex.isEmpty() || containsIdeograph(tex)) return null
                return tex to cursor + if (doubled) 2 else 1
            }
            cursor++
        }
        return null
    }

    private fun containsIdeograph(text: String): Boolean {
        var i = 0
        while (i < text.length) {
            val cp = text.codePointAt(i)
            if (cp in 0x3040..0x30FF || cp in 0x3400..0x4DBF || cp in 0x4E00..0x9FFF ||
                cp in 0xAC00..0xD7AF || cp in 0xF900..0xFAFF || cp in 0x20000..0x2FA1F
            ) return true
            i += Character.charCount(cp)
        }
        return false
    }

    // ---------- blocks ----------

    /** Recognises `$$…$$` and `\[…\]` blocks starting at [index]; null when unterminated or mixed with prose. */
    fun displayMath(lines: List<String>, index: Int): DisplayMath? {
        if (index >= lines.size) return null
        val trimmed = lines[index].trim(' ', '\t')
        return when {
            trimmed.startsWith("$$") -> scanDisplayBlock(lines, index, "$$", "$$")
            trimmed.startsWith("\\[") -> scanDisplayBlock(lines, index, "\\[", "\\]")
            else -> null
        }
    }

    private fun scanDisplayBlock(lines: List<String>, index: Int, marker: String, closer: String): DisplayMath? {
        val body = lines[index].trim(' ', '\t').substring(marker.length)
        val sameLine = body.indexOf(closer)
        if (sameLine >= 0) {
            // A block owns its whole line; a formula followed by prose is inline math instead.
            if (body.substring(sameLine + closer.length).isNotBlank()) return null
            val tex = body.substring(0, sameLine).trim()
            return if (tex.isEmpty()) null else DisplayMath(tex, 1)
        }
        val collected = mutableListOf<String>()
        if (body.isNotEmpty()) collected += body
        var cursor = index + 1
        while (cursor < lines.size) {
            val line = lines[cursor]
            val lineTrimmed = line.trim(' ', '\t')
            val at = lineTrimmed.lastIndexOf(closer)
            if (at >= 0 && lineTrimmed.substring(at + closer.length).isBlank()) {
                val head = lineTrimmed.substring(0, at)
                if (head.isNotEmpty()) collected += head
                val tex = collected.joinToString("\n").trim()
                return if (tex.isEmpty()) null else DisplayMath(tex, cursor - index + 1)
            }
            collected += line
            cursor++
        }
        return null
    }

    private data class Fence(val char: Char, val length: Int, val info: String)

    private fun fenceOf(line: String): Fence? {
        val indent = line.length - line.trimStart(' ').length
        if (indent > 3) return null
        val rest = line.substring(indent)
        val char = rest.firstOrNull()?.takeIf { it == '`' || it == '~' } ?: return null
        val length = rest.takeWhile { it == char }.length
        if (length < 3) return null
        val info = rest.substring(length).trim()
        if (char == '`' && info.contains('`')) return null
        return Fence(char, length, info.substringBefore(' ').lowercase())
    }

    private fun closingFence(lines: List<String>, open: Int, fence: Fence): Int? {
        for (i in open + 1 until lines.size) {
            val trimmed = lines[i].trim()
            if (trimmed.length >= fence.length && trimmed.all { it == fence.char }) return i
        }
        return null
    }
}
