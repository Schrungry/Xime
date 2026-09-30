package com.kingzcheung.xime.settings

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.ByteArrayInputStream

/**
 * 用户词库导出/导入中**不依赖引擎**的部分：建议文件名、导入前文本校验、限量读取。
 * 真正的 Export/Import 走 JNI（librime UserDictManager），由真机端到端验证覆盖。
 */
class UserDictIoManagerTest {

    // ---- 导出建议文件名 ----

    @Test
    fun `exportFileName appends txt extension`() {
        assertEquals("wubi86.txt", UserDictIoManager.exportFileName("wubi86"))
        assertEquals("pinyin_simp.txt", UserDictIoManager.exportFileName("pinyin_simp"))
    }

    // ---- 导入前校验：通过 ----

    @Test
    fun `validateImportText accepts exported code table`() {
        val text = listOf(
            "# Rime user dictionary export",
            "#@/db_name\twubi86.userdb",
            "#@/db_type\tuserdb",
            "劝学\tclip\t1",
            "焉\tghg\t4",
        ).joinToString("\n")

        assertNull(UserDictIoManager.validateImportText(text))
    }

    @Test
    fun `validateImportText accepts two column code table`() {
        // 没有频率列也能导入：librime 的 parser 把缺失的 commits 当 0
        val text = "中国\tzhong guo\n人民\trm\n"

        assertNull(UserDictIoManager.validateImportText(text))
    }

    @Test
    fun `validateImportText accepts scheme dict yaml data section`() {
        // 方案 .dict.yaml 的数据段就是同一格式，头部行没有制表符 ⇒ 忽略即可，不该拦
        val text = listOf(
            "---",
            "name: wubi86",
            "version: \"1.0\"",
            "sort: by_weight",
            "columns:",
            "  - text",
            "  - code",
            "...",
            "工\ta\t0",
            "戈\ta\t0",
        ).joinToString("\n")

        assertNull(UserDictIoManager.validateImportText(text))
    }

    @Test
    fun `validateImportText ignores comment and blank lines`() {
        val text = "# 只有注释和空行，加一条词条\n\n   \n#@/db_type\tuserdb\n啊\taa\t1\n"

        assertNull(UserDictIoManager.validateImportText(text))
    }

    // ---- 导入前校验：拦截 ----

    @Test
    fun `validateImportText rejects blank text`() {
        assertNotNull(UserDictIoManager.validateImportText(""))
        assertNotNull(UserDictIoManager.validateImportText("   \n\t\n"))
    }

    @Test
    fun `validateImportText rejects binary content`() {
        // zip / 图片等二进制文件解码后必含 U+0000
        val text = "PK\u0003\u0004\u0000\u0000wubi86"

        val error = UserDictIoManager.validateImportText(text)

        assertNotNull(error)
        assertTrue(error!!.contains("不是文本文件"))
    }

    @Test
    fun `validateImportText rejects text without any code table row`() {
        val text = listOf(
            "# 只是一个说明文件",
            "name: wubi86",
            "这是一段没有制表符的散文。",
        ).joinToString("\n")

        val error = UserDictIoManager.validateImportText(text)

        assertNotNull(error)
        assertTrue(error!!.contains("词<制表符>码"))
    }

    @Test
    fun `validateImportText rejects row with empty columns`() {
        // 只有一列（缺少码）或空列都不算词条
        val text = "词\t\n\t码\n孤零零的一列\n"

        assertNotNull(UserDictIoManager.validateImportText(text))
    }

    // ---- 限量读取 ----

    @Test
    fun `readAllBytesLimited returns whole stream when under limit`() {
        val source = ByteArray(100) { it.toByte() }

        val bytes = UserDictIoManager.readAllBytesLimited(ByteArrayInputStream(source), 1024)

        assertEquals(100, bytes.size)
        assertTrue(source.contentEquals(bytes))
    }

    @Test
    fun `readAllBytesLimited returns empty for empty stream`() {
        assertEquals(0, UserDictIoManager.readAllBytesLimited(ByteArrayInputStream(ByteArray(0)), 1024).size)
    }

    @Test
    fun `readAllBytesLimited stops one byte past limit`() {
        val source = ByteArray(512 * 1024) { 65 }

        val bytes = UserDictIoManager.readAllBytesLimited(ByteArrayInputStream(source), 1024)

        // 只多读 1 字节 ⇒ 调用方用 size > limit 判超限，不会把大文件整个读进内存
        assertEquals(1025, bytes.size)
    }

    @Test
    fun `readAllBytesLimited handles limit boundary exactly`() {
        val source = ByteArray(1024) { 66 }

        val bytes = UserDictIoManager.readAllBytesLimited(ByteArrayInputStream(source), 1024)

        // 正好等于上限：不算超限
        assertEquals(1024, bytes.size)
    }
}