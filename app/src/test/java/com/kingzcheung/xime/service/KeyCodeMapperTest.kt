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

    @Test
    fun `CapsLock只影响字母大小写`() {
        for (keyCode in KeyEvent.KEYCODE_A..KeyEvent.KEYCODE_Z) {
            val lower = ('a' + keyCode - KeyEvent.KEYCODE_A).toString()
            assertEquals(lower, keyCodeToKey(keyCode, false, false))
            assertEquals(lower.uppercase(), keyCodeToKey(keyCode, true, false))
            assertEquals(lower.uppercase(), keyCodeToKey(keyCode, false, true))
        }
    }

    @Test
    fun `CapsLock不会把数字和标点变成Shift符号`() {
        val keys = (KeyEvent.KEYCODE_0..KeyEvent.KEYCODE_9).toList() + listOf(
            KeyEvent.KEYCODE_COMMA, KeyEvent.KEYCODE_PERIOD, KeyEvent.KEYCODE_MINUS,
            KeyEvent.KEYCODE_EQUALS, KeyEvent.KEYCODE_SLASH, KeyEvent.KEYCODE_BACKSLASH,
            KeyEvent.KEYCODE_SEMICOLON, KeyEvent.KEYCODE_APOSTROPHE,
            KeyEvent.KEYCODE_LEFT_BRACKET, KeyEvent.KEYCODE_RIGHT_BRACKET, KeyEvent.KEYCODE_GRAVE,
            KeyEvent.KEYCODE_PLUS, KeyEvent.KEYCODE_STAR,
            KeyEvent.KEYCODE_NUMPAD_ADD, KeyEvent.KEYCODE_NUMPAD_MULTIPLY,
            KeyEvent.KEYCODE_SPACE, KeyEvent.KEYCODE_ENTER, KeyEvent.KEYCODE_TAB,
        )
        for (keyCode in keys) {
            for (shift in listOf(false, true)) {
                assertEquals(
                    "keyCode=$keyCode shift=$shift",
                    keyCodeToKey(keyCode, shift, false),
                    keyCodeToKey(keyCode, shift, true),
                )
            }
        }
    }
}
