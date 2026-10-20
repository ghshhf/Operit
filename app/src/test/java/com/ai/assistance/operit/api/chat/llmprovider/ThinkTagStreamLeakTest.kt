package com.ai.assistance.operit.api.chat.llmprovider

import com.ai.assistance.operit.util.AppLogger
import com.ai.assistance.operit.util.stream.plugins.StreamXmlPlugin
import com.ai.assistance.operit.util.stream.splitBy
import com.ai.assistance.operit.util.stream.stream
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * issue #1341：思考内容里的字面 think 标记破坏流式解析。
 *
 * 两条防线都要有回归见证：
 * 1. 框架发射的 think 标签必须落在行首（emitMetadataTag 补换行）——
 *    否则行内裸标签通不过 StreamXmlPlugin 的位置门槛，思考块根本不成立；
 * 2. 思考内容必须经过 sanitizeReasoningText 无害化——
 *    否则模型自己写出的字面结束标签会把思考块提前闭合，后半段思考漏进正文。
 */
class ThinkTagStreamLeakTest {

    private var previousSystemLogEnabled = true

    @Before
    fun disableAndroidSystemLogForJvmTests() {
        previousSystemLogEnabled = AppLogger.enableSystemLog
        AppLogger.enableSystemLog = false
    }

    @After
    fun restoreAndroidSystemLog() {
        AppLogger.enableSystemLog = previousSystemLogEnabled
    }

    // ---------- sanitizeReasoningText ----------

    @Test
    fun `literal think tags in reasoning are neutralized`() {
        val raw =
            "我先执行查询。" + CLOSE + "\n\n\n\n" + CLOSE + "\n\n" + OPEN + "我查了一下，没有直接命中"
        val safe = sanitizeReasoningText(raw)

        assertFalse("字面结束标签必须被破坏", safe.contains(CLOSE))
        assertFalse("字面开始标签必须被破坏", safe.contains(OPEN))
        assertTrue(safe.contains("<\u200B/think>"))
        assertTrue(safe.contains("<\u200Bthink>"))
        // 显示无损：去掉零宽空格后原文还原
        assertEquals(raw, safe.replace("\u200B", ""))
    }

    @Test
    fun `reasoning without angle brackets passes through unchanged`() {
        val raw = "普通思考内容，没有任何标签"
        assertEquals(raw, sanitizeReasoningText(raw))
    }

    @Test
    fun `empty reasoning is handled`() {
        assertEquals("", sanitizeReasoningText(""))
    }

    // ---------- 防线 1：框架标签必须落在行首 ----------

    @Test
    fun `bare start tag mid-line is not recognized as xml`() {
        // 位置门槛：不在行首、不跟结束标签/标点，`<` 不当标签起始
        val stream = "前文内容" + OPEN + "思考内容" + CLOSE
        val groups = runBlocking { splitGroups(stream) }
        assertTrue("行内裸开始标签不应被当作 XML", groups.none { it.isXml })
    }

    @Test
    fun `newline wrapped tags are recognized even mid-stream`() {
        // emitMetadataTag 的产物：标签前后补换行，前文不换行也能成立
        val stream = "前文内容" + "\n" + OPEN + "\n" + "思考内容" + "\n" + CLOSE + "\n" + "正文"
        val groups = runBlocking { splitGroups(stream) }

        val xmlGroups = groups.filter { it.isXml }
        assertEquals("只应有一个 think 块", 1, xmlGroups.size)
        assertTrue(xmlGroups[0].text.contains("思考内容"))
        assertTrue(xmlGroups[0].text.endsWith(CLOSE))
        assertTrue(
            "正文不被思考块吞掉",
            groups.filterNot { it.isXml }.joinToString("") { it.text }.contains("正文")
        )
    }

    // ---------- 防线 2：字面结束标签不得闭合思考块 ----------

    @Test
    fun `unsanitized literal close tag leaks the reasoning tail`() {
        // 修复前病灶见证：模型在思考里写出字面结束标签，解析器当场闭合
        val reasoning = "我先执行查询。" + CLOSE + "\n\n\n\n" + CLOSE + "我查了一下，没有直接命中"
        val stream = "\n" + OPEN + "\n" + reasoning + "\n" + CLOSE + "\n"
        val groups = runBlocking { splitGroups(stream) }

        val xmlText = groups.filter { it.isXml }.joinToString("") { it.text }
        assertFalse("未无害化时思考块被提前闭合", xmlText.contains("我查了一下，没有直接命中"))
    }

    @Test
    fun `sanitized reasoning keeps the block closed only by the framework tag`() {
        val reasoning =
            sanitizeReasoningText("我先执行查询。" + CLOSE + "\n\n\n\n" + CLOSE + "我查了一下，没有直接命中")
        val stream = "\n" + OPEN + "\n" + reasoning + "\n" + CLOSE + "\n正文开始"
        val groups = runBlocking { splitGroups(stream) }

        val xmlGroups = groups.filter { it.isXml }
        assertEquals("只应有一个 think 块", 1, xmlGroups.size)
        val xmlText = xmlGroups[0].text
        assertTrue(xmlText.contains("我先执行查询"))
        assertTrue("字面结束标签不再触发闭合", xmlText.contains("我查了一下，没有直接命中"))
        assertTrue("块由框架的结束标签闭合", xmlText.endsWith(CLOSE))
        assertTrue(
            "正文不被思考块吞掉",
            groups.filterNot { it.isXml }.joinToString("") { it.text }.contains("正文开始")
        )
    }

    // ---------- helpers ----------

    private data class Group(val isXml: Boolean, val text: String)

    /** 走真实 splitBy，忠实复现 UI 层的消费方式。 */
    private suspend fun splitGroups(input: String): List<Group> {
        val result = mutableListOf<Group>()
        input.stream()
            .splitBy(listOf(StreamXmlPlugin(includeTagsInOutput = true)))
            .collect { group ->
                val builder = StringBuilder()
                group.stream.collect { chunk -> builder.append(chunk) }
                result.add(Group(isXml = group.tag != null, text = builder.toString()))
            }
        return result
    }

    private companion object {
        // ASCII 片段拼装，避免任何编码意外
        val OPEN: String = "<" + "think" + ">"
        val CLOSE: String = "<" + "/" + "think" + ">"
    }
}
