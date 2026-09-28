package com.verlintas.baic2.feature.chat

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class MarkdownParserTest {

    @Test
    fun splitsParagraphsBulletsAndCode() {
        val text = """
            这是一个 **Markdown** 测试：
            - 列表项一
            - 列表项二

            ```kotlin
            val nova = "streaming works"
            ```
        """.trimIndent()

        val blocks = parseMarkdownBlocks(text)

        assertEquals(4, blocks.size)
        val paragraph = blocks[0] as MarkdownBlock.Paragraph
        assertEquals(MarkdownBlock.Paragraph.Kind.NORMAL, paragraph.kind)
        assertTrue(paragraph.text.contains("Markdown"))

        val first = blocks[1] as MarkdownBlock.Paragraph
        assertEquals(MarkdownBlock.Paragraph.Kind.BULLET, first.kind)
        assertEquals("- 列表项一", first.text)

        val second = blocks[2] as MarkdownBlock.Paragraph
        assertEquals(MarkdownBlock.Paragraph.Kind.BULLET, second.kind)

        val code = blocks[3] as MarkdownBlock.Code
        assertEquals("kotlin", code.language)
        assertEquals("val nova = \"streaming works\"", code.code)
    }

    @Test
    fun consecutiveNormalLinesFormOneParagraph() {
        val blocks = parseMarkdownBlocks("line one\nline two\n\nline three")

        assertEquals(2, blocks.size)
        assertEquals("line one\nline two", (blocks[0] as MarkdownBlock.Paragraph).text)
        assertEquals("line three", (blocks[1] as MarkdownBlock.Paragraph).text)
    }

    @Test
    fun headingsAreClassified() {
        val blocks = parseMarkdownBlocks("# Title\n## Sub\n### Small")

        assertEquals(
            listOf(
                MarkdownBlock.Paragraph.Kind.HEADING1,
                MarkdownBlock.Paragraph.Kind.HEADING2,
                MarkdownBlock.Paragraph.Kind.HEADING3,
            ),
            blocks.map { (it as MarkdownBlock.Paragraph).kind },
        )
    }

    @Test
    fun unterminatedCodeBlockStillRenders() {
        val blocks = parseMarkdownBlocks("text\n```py\nprint(1)")

        assertEquals(2, blocks.size)
        val code = blocks[1] as MarkdownBlock.Code
        assertEquals("py", code.language)
        assertEquals("print(1)", code.code)
    }

    @Test
    fun numberedListsAreClassified() {
        val blocks = parseMarkdownBlocks("1. first\n2. second")

        assertEquals(
            listOf(
                MarkdownBlock.Paragraph.Kind.NUMBERED,
                MarkdownBlock.Paragraph.Kind.NUMBERED,
            ),
            blocks.map { (it as MarkdownBlock.Paragraph).kind },
        )
    }
}
