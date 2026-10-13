package com.kingzcheung.xime.service

import android.view.KeyEvent
import android.view.inputmethod.InputConnection

/**
 * 实体按键的原始事件快照。
 *
 * Android 传入的 KeyEvent 由系统管理；跨过 IME 的分流边界前复制一份，
 * 这样未被输入法消费时仍可把完整的 metaState、scanCode、时间戳和 unicode 回放给编辑器。
 */
internal class HardwareKeyEvent private constructor(
    val raw: KeyEvent,
) {
    companion object {
        fun capture(event: KeyEvent): HardwareKeyEvent =
            HardwareKeyEvent(KeyEvent(event))
    }

    fun replayTo(inputConnection: InputConnection?): HardwareKeyHandlingResult {
        val connection = inputConnection ?: return HardwareKeyHandlingResult.UNHANDLED
        return if (connection.sendKeyEvent(KeyEvent(raw))) {
            HardwareKeyHandlingResult.FORWARDED_TO_EDITOR
        } else {
            HardwareKeyHandlingResult.UNHANDLED
        }
    }
}

internal enum class HardwareKeyHandlingResult {
    /** Rime/输入方案已消费原始按键。 */
    RIME_CONSUMED,
    /** Xime 自己完成了编辑器动作或文本提交。 */
    IME_CONSUMED,
    /** 原始按键已经回放给目标编辑器。 */
    FORWARDED_TO_EDITOR,
    UNHANDLED,
}
