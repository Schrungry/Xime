package com.kingzcheung.xime.rime

import androidx.test.platform.app.InstrumentationRegistry
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.io.File

/** 真实 JNI + librime + Lua 按键回归；夹具只模拟按键规则，不替代万象真机验收。 */
class RimeEditingKeyTest {
    private lateinit var engine: RimeEngine

    @Before
    fun setup() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val root = File(instrumentation.targetContext.cacheDir, "rime_key_rules_" + System.nanoTime())
        val user = File(root, "user").apply { mkdirs() }
        val shared = File(root, "shared").apply { mkdirs() }
        val assets = instrumentation.context.assets
        fun copyAssets(path: String, target: File) {
            val children = assets.list(path).orEmpty()
            if (children.isEmpty()) {
                target.parentFile?.mkdirs()
                assets.open(path).use { source -> target.outputStream().use { source.copyTo(it) } }
            } else {
                target.mkdirs()
                children.forEach { copyAssets(path + "/" + it, File(target, it)) }
            }
        }
        copyAssets("rime_key_rules", shared)
        engine = RimeEngine.getInstance()
        engine.initialize(user.absolutePath, shared.absolutePath)
        assertTrue("fixture deployment", engine.deploy())
        assertTrue("fixture session", engine.ensureSession(10_000))
        assertTrue(engine.switchSchema("key_rules"))
        engine.setOption("ascii_mode", false)
    }

    @After
    fun teardown() {
        if (::engine.isInitialized) engine.destroy()
    }

    @Test
    fun tonesRunSchemeRulesAtMiddleCaretAndKeepWholeInputCandidates() {
        for (tone in "7890") {
            assertTrue(engine.setInput("nihao"))
            val result = engine.processKeyAndGetResult(tone.code, 0, 2)
            assertTrue(result.processed)
            assertEquals("ni" + tone + "hao", result.inputText)
            assertEquals(3, result.caretPos)
            assertEquals("", result.committedText)
            assertEquals("first:ni" + tone + "hao", result.candidates.first().text)

            // The actual native caret is restored to the end for full-code candidates.
            assertEquals(result.inputText.length, engine.getComposition().caretPos)
        }
    }

    @Test
    fun toneReplacementUsesReturnedCaretRatherThanAssumingOneCharacterInserted() {
        assertTrue(engine.setInput("ni7hao"))
        val result = engine.processKeyAndGetResult('9'.code, 0, 3)
        assertEquals("ni9hao", result.inputText)
        assertEquals(3, result.caretPos)
        val continued = engine.processKeyAndGetResult('m'.code, 0, result.caretPos)
        assertEquals("ni9mhao", continued.inputText)
        assertEquals(4, continued.caretPos)
    }

    @Test
    fun digitSelectionIsDecidedByScheme() {
        assertTrue(engine.setInput("nihao"))
        val result = engine.processKeyAndGetResult('2'.code, 0)
        assertTrue(result.processed)
        assertEquals("second:nihao", result.committedText)
        assertEquals("", result.inputText)
    }

    @Test
    fun uppercaseMagicLettersStayInCompositionAndAcceptDigits() {
        // Covers explicit SEND_RIME uppercase (mask=0) and Shift taps (mask=1).
        for ((letter, mask) in "URN".flatMap { letter -> listOf(letter to 0, letter to 1) }) {
            engine.clearComposition()
            val start = engine.processKeyAndGetResult(letter.code, mask)
            assertTrue(start.processed)
            assertEquals(letter.toString(), start.inputText)
            assertEquals("", start.committedText)
            for (digit in "1234") {
                assertTrue(engine.processKeyAndGetResult(digit.code, 0).processed)
            }
            assertEquals(letter + "1234", engine.getInput())
        }
    }

    @Test
    fun uppercaseCanBeInsertedAtMiddleAndBackspaceRespectsCaretZero() {
        assertTrue(engine.setInput("nihao"))
        val inserted = engine.processKeyAndGetResult('U'.code, 1, 2)
        assertEquals("niUhao", inserted.inputText)
        assertEquals(3, inserted.caretPos)
        val removed = engine.processKeyAndGetResult(0xff08, 0, inserted.caretPos)
        assertEquals("nihao", removed.inputText)
        assertEquals(2, removed.caretPos)
        val atStart = engine.processKeyAndGetResult(0xff08, 0, 0)
        assertEquals("nihao", atStart.inputText)
        assertEquals(0, atStart.caretPos)
    }
}
