package net.icdcyborg.mavenDependencyTracker.util

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import kotlin.text.RegexOption

/**
 * POM XML文字列をシンタックスハイライトします。
 *
 * @param xmlString ハイライトするPOM XML文字列。
 * @return シンタックスハイライトされたAnnotatedString。
 */
fun highlightPomXml(xmlString: String): AnnotatedString =
    buildAnnotatedString {
        append(xmlString)

        // コメントの正規表現: <!--.*?-->
        val commentRegex = "<!--.*?-->".toRegex(RegexOption.DOT_MATCHES_ALL)
        commentRegex.findAll(xmlString).forEach {
            addStyle(SpanStyle(color = Color(0xFF006400)), it.range.first, it.range.last + 1) // 暗めの緑色
        }

        // タグの正規表現: <[^!>]*>
        val tagRegex = "<[^!>]*>".toRegex()
        tagRegex.findAll(xmlString).forEach {
            addStyle(SpanStyle(color = Color(0xFF800080)), it.range.first, it.range.last + 1) // 暗めの紫色
        }
    }
