package com.kingzcheung.xime.service

import android.view.KeyEvent
import org.junit.Assert.assertEquals
import org.junit.Test

class KeyCodeMapperTest {

    @Test
    fun `主键盘数字的Shift映射为符号`() {
        assertEquals("!", keyCodeToKey(KeyEvent.KEYCODE_1, true))
        assertEquals("@", keyCodeToKey(KeyEvent.KEYCODE_2, true))
        assertEquals("#", keyCodeToKey(KeyEvent.KEYCODE_3, true))
        assertEquals("\$", keyCodeToKey(KeyEvent.KEYCODE_4, true))
        assertEquals("%", keyCodeToKey(KeyEvent.KEYCODE_5, true))
        assertEquals("^", keyCodeToKey(KeyEvent.KEYCODE_6, true))
        assertEquals("&", keyCodeToKey(KeyEvent.KEYCODE_7, true))
        assertEquals("*", keyCodeToKey(KeyEvent.KEYCODE_8, true))
        assertEquals("(", keyCodeToKey(KeyEvent.KEYCODE_9, true))
        assertEquals(")", keyCodeToKey(KeyEvent.KEYCODE_0, true))
    }

    @Test
    fun `主键盘标点的Shift映射为符号`() {
        assertEquals("+", keyCodeToKey(KeyEvent.KEYCODE_EQUALS, true))
        assertEquals("_", keyCodeToKey(KeyEvent.KEYCODE_MINUS, true))
        assertEquals("?", keyCodeToKey(KeyEvent.KEYCODE_SLASH, true))
        assertEquals("*", keyCodeToKey(KeyEvent.KEYCODE_STAR, false))
        assertEquals("+", keyCodeToKey(KeyEvent.KEYCODE_PLUS, false))
    }

    @Test
    fun `未按Shift时仍返回基础字符`() {
        assertEquals("8", keyCodeToKey(KeyEvent.KEYCODE_8, false))
        assertEquals("=", keyCodeToKey(KeyEvent.KEYCODE_EQUALS, false))
    }
}
