package com.kingzcheung.xime.service

import android.view.KeyCharacterMap
import android.view.KeyEvent
import android.view.inputmethod.InputConnection
import org.junit.Assert.assertEquals
import org.junit.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.argumentCaptor
import org.mockito.kotlin.mock
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever

class HardwareKeyEventTest {

    @Test
    fun `capture retains original key event fields and replays a copy`() {
        val source = KeyEvent(
            101L,
            207L,
            KeyEvent.ACTION_DOWN,
            KeyEvent.KEYCODE_F1,
            2,
            KeyEvent.META_ALT_ON or KeyEvent.META_SHIFT_ON,
            42,
            15,
            KeyEvent.FLAG_FROM_SYSTEM,
            KeyCharacterMap.VIRTUAL_KEYBOARD,
        )
        val connection = mock<InputConnection>()
        whenever(connection.sendKeyEvent(any())).thenReturn(true)
        val snapshot = HardwareKeyEvent.capture(source)

        assertEquals(HardwareKeyHandlingResult.FORWARDED_TO_EDITOR, snapshot.replayTo(connection))
        val replay = argumentCaptor<KeyEvent>()
        verify(connection).sendKeyEvent(replay.capture())
        assertEquals(source.action, replay.firstValue.action)
        assertEquals(source.keyCode, replay.firstValue.keyCode)
        assertEquals(source.repeatCount, replay.firstValue.repeatCount)
        assertEquals(source.metaState, replay.firstValue.metaState)
        assertEquals(source.deviceId, replay.firstValue.deviceId)
        assertEquals(source.scanCode, replay.firstValue.scanCode)
        assertEquals(source.downTime, replay.firstValue.downTime)
        assertEquals(source.eventTime, replay.firstValue.eventTime)

    }

    @Test
    fun `replay returns unhandled without an input connection`() {
        val event = KeyEvent(KeyEvent.ACTION_UP, KeyEvent.KEYCODE_TAB)
        val snapshot = HardwareKeyEvent.capture(event)
        assertEquals(HardwareKeyHandlingResult.UNHANDLED, snapshot.replayTo(null))
    }

    @Test
    fun `replay returns unhandled when editor rejects the event`() {
        val connection = mock<InputConnection>()
        whenever(connection.sendKeyEvent(any())).thenReturn(false)
        val snapshot = HardwareKeyEvent.capture(KeyEvent(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_TAB))

        assertEquals(HardwareKeyHandlingResult.UNHANDLED, snapshot.replayTo(connection))
        verify(connection).sendKeyEvent(any())
    }

    @Test
    fun `hardware enter result distinguishes rime ime and editor handling`() {
        assertEquals(
            HardwareKeyHandlingResult.RIME_CONSUMED,
            classifyHardwareEnterResult(rimeConsumed = true, hasCompositionAfterRouting = true),
        )
        assertEquals(
            HardwareKeyHandlingResult.IME_CONSUMED,
            classifyHardwareEnterResult(rimeConsumed = false, hasCompositionAfterRouting = true),
        )
        assertEquals(
            HardwareKeyHandlingResult.FORWARDED_TO_EDITOR,
            classifyHardwareEnterResult(rimeConsumed = false, hasCompositionAfterRouting = false),
        )
    }
}
