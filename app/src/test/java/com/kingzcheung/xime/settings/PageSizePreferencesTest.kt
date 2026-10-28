package com.kingzcheung.xime.settings

import android.content.Context
import android.content.SharedPreferences
import org.junit.Assert.assertEquals
import org.junit.Test
import org.mockito.kotlin.mock
import org.mockito.kotlin.times
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever

class PageSizePreferencesTest {
    private val prefs = mock<SharedPreferences>()
    private val editor = mock<SharedPreferences.Editor>()
    private val context = mock<Context> {
        on { getSharedPreferences("kime_settings", Context.MODE_PRIVATE) }.thenReturn(prefs)
    }

    @Test
    fun `读取页大小保留5和历史0并限制范围`() {
        val cases = listOf(0 to 0, 1 to 5, 4 to 5, 5 to 5, 6 to 6, 20 to 20, 50 to 50, 51 to 50)
        for ((stored, expected) in cases) {
            whenever(prefs.getInt("page_size", SettingsPreferences.DEFAULT_PAGE_SIZE)).thenReturn(stored)
            assertEquals(expected, SettingsPreferences.getPageSize(context))
        }
        assertEquals(20, SettingsPreferences.DEFAULT_PAGE_SIZE)
    }

    @Test
    fun `写入页大小保留5和历史0并限制范围`() {
        whenever(prefs.edit()).thenReturn(editor)
        val cases = listOf(0 to 0, 1 to 5, 4 to 5, 5 to 5, 6 to 6, 20 to 20, 50 to 50, 51 to 50)
        for ((input, expected) in cases) {
            whenever(editor.putInt("page_size", expected)).thenReturn(editor)
            SettingsPreferences.setPageSize(context, input)
        }
        verify(editor).putInt("page_size", 0)
        verify(editor, times(3)).putInt("page_size", 5)
        verify(editor).putInt("page_size", 6)
        verify(editor).putInt("page_size", 20)
        verify(editor, times(2)).putInt("page_size", 50)
    }
}
