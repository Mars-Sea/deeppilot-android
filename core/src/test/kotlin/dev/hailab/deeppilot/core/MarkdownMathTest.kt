package dev.hailab.deeppilot.core

import dev.hailab.deeppilot.core.markdown.DisplayMath
import dev.hailab.deeppilot.core.markdown.InlinePiece.Math
import dev.hailab.deeppilot.core.markdown.InlinePiece.Text
import dev.hailab.deeppilot.core.markdown.MarkdownMath
import dev.hailab.deeppilot.core.markdown.MathSegment
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Test

/** Mirrors the iOS MarkdownMathTests so both apps typeset the same text. */
class MarkdownMathTest {
    private fun p(i: Int) = MarkdownMath.placeholder(i)

    @Test
    fun inlineDelimitersAreRecognised() {
        val cases = listOf(
            "\$x\$" to listOf(Math("x")),
            "\\(x\\)" to listOf(Math("x")),
            "\\[x\\]" to listOf(Math("x")),
            "\$\$x\$\$" to listOf(Math("x")),
            "\$E = mc^2\$" to listOf(Math("E = mc^2")),
            "\\(\\frac{1}{2}\\)" to listOf(Math("\\frac{1}{2}")),
            "a \$x\$ b" to listOf(Text("a "), Math("x"), Text(" b")),
            "\$a\$ 和 \$b\$" to listOf(Math("a"), Text(" 和 "), Math("b")),
            "\$α ≤ β\$" to listOf(Math("α ≤ β")),
        )
        for ((source, expected) in cases) assertEquals(source, expected, MarkdownMath.splitInline(source))
    }

    @Test
    fun proseUnterminatedEscapedAndCodeStayLiteral() {
        val prose = "当 α ≤ β 时，∑ᵢ xᵢ 收敛"
        assertEquals(listOf(Text(prose)), MarkdownMath.splitInline(prose))
        for (source in listOf("\\(x", "\$x", "\\[x", "\$\$x", "a \$ b", "\$\$", "\$", "\\\$x\\\$", "`\$x\$`", "`\\(x\\)`", "价格 \$100 和 \$200")) {
            assertFalse(source, MarkdownMath.containsInlineMath(source))
        }
        assertEquals(listOf(Math("x"), Text(" 之后 `\$y\$`")), MarkdownMath.splitInline("\\(x\\) 之后 `\$y\$`"))
    }

    @Test
    fun displayBlocks() {
        assertEquals(DisplayMath("x^2", 1), MarkdownMath.displayMath(listOf("\$\$x^2\$\$"), 0))
        assertEquals(DisplayMath("\\frac{1}{2}", 3), MarkdownMath.displayMath(listOf("\$\$", "\\frac{1}{2}", "\$\$"), 0))
        assertEquals(DisplayMath("a + b", 3), MarkdownMath.displayMath(listOf("\\[", "a + b", "\\]"), 0))
        assertNull(MarkdownMath.displayMath(listOf("\$\$", "x"), 0))
        assertNull(MarkdownMath.displayMath(listOf("\\[", "x"), 0))
        assertNull(MarkdownMath.displayMath(listOf("\$\$x\$\$ therefore y"), 0))
    }

    @Test
    fun prepareSwapsFormulasForPlaceholders() {
        val prepared = MarkdownMath.prepare("前文 \$a_1\$ 和 **b**\n\n\$\$\n\\sum_i x_i\n\$\$\n后文")
        assertEquals("前文 ${p(0)} 和 **b**\n\n\n${p(1)}\n\n后文", prepared.markdown)
        assertEquals(listOf(MathSegment("a_1", false), MathSegment("\\sum_i x_i", true)), prepared.math)
    }

    @Test
    fun codeFencesAreUntouchedButMathFencesAreTypeset() {
        val code = "```python\nprice = \"\$x\$\"\n```"
        assertEquals(code, MarkdownMath.prepare(code).markdown)
        val fenced = MarkdownMath.prepare("```math\n\\sqrt{2}\n```")
        assertEquals(listOf(MathSegment("\\sqrt{2}", true)), fenced.math)
        // Mid-stream: the closing fence has not arrived, so nothing is eaten.
        assertEquals("\$\$x", MarkdownMath.prepare("\$\$x").markdown)
        assertEquals("```math\n\\sqrt{2}", MarkdownMath.prepare("```math\n\\sqrt{2}").markdown)
    }
}
