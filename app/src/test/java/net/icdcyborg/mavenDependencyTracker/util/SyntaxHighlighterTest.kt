package net.icdcyborg.mavenDependencyTracker.util

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.SpanStyle
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class SyntaxHighlighterTest {
    @Test
    fun `highlightPomXml should highlight comments`() {
        val xml =
            """
            <!-- comment -->
            <project>
                <artifactId>test</artifactId>
            </project>
            """.trimIndent()
        val annotatedString = highlightPomXml(xml)

        val expectedCommentStyle = SpanStyle(color = Color(0xFF006400))
        assertEquals(expectedCommentStyle, annotatedString.spanStyles[0].item)
        assertEquals(0, annotatedString.spanStyles[0].start)
        assertEquals(16, annotatedString.spanStyles[0].end)
    }

    @Test
    fun `highlightPomXml should highlight tags`() {
        val xml =
            """
            <project>
                <artifactId>test</artifactId>
            </project>
            """.trimIndent()
        val annotatedString = highlightPomXml(xml)

        val expectedTagStyle = SpanStyle(color = Color(0xFF800080))

        println(annotatedString.spanStyles)
        // <project>
        assertEquals(expectedTagStyle, annotatedString.spanStyles[0].item)
        assertEquals(0, annotatedString.spanStyles[0].start)
        assertEquals(9, annotatedString.spanStyles[0].end)

        // <artifactId>
        assertEquals(expectedTagStyle, annotatedString.spanStyles[1].item)
        assertEquals(14, annotatedString.spanStyles[1].start)
        assertEquals(26, annotatedString.spanStyles[1].end)

        // </artifactId>
        assertEquals(expectedTagStyle, annotatedString.spanStyles[2].item)
        assertEquals(30, annotatedString.spanStyles[2].start)
        assertEquals(43, annotatedString.spanStyles[2].end)

        // </project>
        assertEquals(expectedTagStyle, annotatedString.spanStyles[3].item)
        assertEquals(44, annotatedString.spanStyles[3].start)
        assertEquals(54, annotatedString.spanStyles[3].end)
    }

    @Test
    fun `highlightPomXml should highlight both comments and tags`() {
        val xml =
            """
            <!-- comment -->
            <project>
                <!-- inner comment -->
                <artifactId>test</artifactId>
            </project>
            """.trimIndent()
        val annotatedString = highlightPomXml(xml)

        val expectedCommentStyle = SpanStyle(color = Color(0xFF006400))
        val expectedTagStyle = SpanStyle(color = Color(0xFF800080))
        println(annotatedString.spanStyles)
        // <!-- comment -->
        assertEquals(expectedCommentStyle, annotatedString.spanStyles[0].item)
        assertEquals(0, annotatedString.spanStyles[0].start)
        assertEquals(16, annotatedString.spanStyles[0].end)

        // <project>
        assertEquals(expectedTagStyle, annotatedString.spanStyles[2].item)
        assertEquals(17, annotatedString.spanStyles[2].start)
        assertEquals(26, annotatedString.spanStyles[2].end)

        // <!-- inner comment -->
        assertEquals(expectedCommentStyle, annotatedString.spanStyles[1].item)
        assertEquals(31, annotatedString.spanStyles[1].start)
        assertEquals(53, annotatedString.spanStyles[1].end)

        // <artifactId>
        assertEquals(expectedTagStyle, annotatedString.spanStyles[3].item)
        assertEquals(58, annotatedString.spanStyles[3].start)
        assertEquals(70, annotatedString.spanStyles[3].end)

        // </artifactId>
        assertEquals(expectedTagStyle, annotatedString.spanStyles[4].item)
        assertEquals(74, annotatedString.spanStyles[4].start)
        assertEquals(87, annotatedString.spanStyles[4].end)

        // </project>
        assertEquals(expectedTagStyle, annotatedString.spanStyles[5].item)
        assertEquals(88, annotatedString.spanStyles[5].start)
        assertEquals(98, annotatedString.spanStyles[5].end)
    }

    @Test
    fun `highlightPomXml should handle empty string`() {
        val xml = ""
        val annotatedString = highlightPomXml(xml)
        assertEquals(0, annotatedString.spanStyles.size)
    }

    @Test
    fun `highlightPomXml should handle xml without comments or tags`() {
        val xml = "Plain text content"
        val annotatedString = highlightPomXml(xml)
        assertEquals(0, annotatedString.spanStyles.size)
    }
}
