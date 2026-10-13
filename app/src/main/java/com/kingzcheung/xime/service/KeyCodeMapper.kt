package com.kingzcheung.xime.service

import android.view.KeyEvent

/** 物理键码是否为左右 Shift（修饰键，不产生字符，单击语义由调用方定义）。 */
internal fun isShiftKeyCode(keyCode: Int): Boolean =
    keyCode == KeyEvent.KEYCODE_SHIFT_LEFT || keyCode == KeyEvent.KEYCODE_SHIFT_RIGHT

/** 物理键码 → 输入法按键名。 */
internal fun keyCodeToKey(keyCode: Int, isShifted: Boolean, isCapsLockOn: Boolean = false): String? {
    return when (keyCode) {
        KeyEvent.KEYCODE_A -> if (isShifted || isCapsLockOn) "A" else "a"
        KeyEvent.KEYCODE_B -> if (isShifted || isCapsLockOn) "B" else "b"
        KeyEvent.KEYCODE_C -> if (isShifted || isCapsLockOn) "C" else "c"
        KeyEvent.KEYCODE_D -> if (isShifted || isCapsLockOn) "D" else "d"
        KeyEvent.KEYCODE_E -> if (isShifted || isCapsLockOn) "E" else "e"
        KeyEvent.KEYCODE_F -> if (isShifted || isCapsLockOn) "F" else "f"
        KeyEvent.KEYCODE_G -> if (isShifted || isCapsLockOn) "G" else "g"
        KeyEvent.KEYCODE_H -> if (isShifted || isCapsLockOn) "H" else "h"
        KeyEvent.KEYCODE_I -> if (isShifted || isCapsLockOn) "I" else "i"
        KeyEvent.KEYCODE_J -> if (isShifted || isCapsLockOn) "J" else "j"
        KeyEvent.KEYCODE_K -> if (isShifted || isCapsLockOn) "K" else "k"
        KeyEvent.KEYCODE_L -> if (isShifted || isCapsLockOn) "L" else "l"
        KeyEvent.KEYCODE_M -> if (isShifted || isCapsLockOn) "M" else "m"
        KeyEvent.KEYCODE_N -> if (isShifted || isCapsLockOn) "N" else "n"
        KeyEvent.KEYCODE_O -> if (isShifted || isCapsLockOn) "O" else "o"
        KeyEvent.KEYCODE_P -> if (isShifted || isCapsLockOn) "P" else "p"
        KeyEvent.KEYCODE_Q -> if (isShifted || isCapsLockOn) "Q" else "q"
        KeyEvent.KEYCODE_R -> if (isShifted || isCapsLockOn) "R" else "r"
        KeyEvent.KEYCODE_S -> if (isShifted || isCapsLockOn) "S" else "s"
        KeyEvent.KEYCODE_T -> if (isShifted || isCapsLockOn) "T" else "t"
        KeyEvent.KEYCODE_U -> if (isShifted || isCapsLockOn) "U" else "u"
        KeyEvent.KEYCODE_V -> if (isShifted || isCapsLockOn) "V" else "v"
        KeyEvent.KEYCODE_W -> if (isShifted || isCapsLockOn) "W" else "w"
        KeyEvent.KEYCODE_X -> if (isShifted || isCapsLockOn) "X" else "x"
        KeyEvent.KEYCODE_Y -> if (isShifted || isCapsLockOn) "Y" else "y"
        KeyEvent.KEYCODE_Z -> if (isShifted || isCapsLockOn) "Z" else "z"
        KeyEvent.KEYCODE_SPACE -> "space"
        KeyEvent.KEYCODE_ENTER -> "enter"
        KeyEvent.KEYCODE_DEL -> "delete"
        KeyEvent.KEYCODE_0 -> if (isShifted) ")" else "0"
        KeyEvent.KEYCODE_1 -> if (isShifted) "!" else "1"
        KeyEvent.KEYCODE_2 -> if (isShifted) "@" else "2"
        KeyEvent.KEYCODE_3 -> if (isShifted) "#" else "3"
        KeyEvent.KEYCODE_4 -> if (isShifted) "\$" else "4"
        KeyEvent.KEYCODE_5 -> if (isShifted) "%" else "5"
        KeyEvent.KEYCODE_6 -> if (isShifted) "^" else "6"
        KeyEvent.KEYCODE_7 -> if (isShifted) "&" else "7"
        KeyEvent.KEYCODE_8 -> if (isShifted) "*" else "8"
        KeyEvent.KEYCODE_9 -> if (isShifted) "(" else "9"
        KeyEvent.KEYCODE_COMMA -> if (isShifted) "<" else ","
        KeyEvent.KEYCODE_PERIOD -> if (isShifted) ">" else "."
        KeyEvent.KEYCODE_MINUS -> if (isShifted) "_" else "-"
        KeyEvent.KEYCODE_EQUALS -> if (isShifted) "+" else "="
        KeyEvent.KEYCODE_SLASH -> if (isShifted) "?" else "/"
        KeyEvent.KEYCODE_BACKSLASH -> if (isShifted) "|" else "\\"
        KeyEvent.KEYCODE_SEMICOLON -> if (isShifted) ":" else ";"
        KeyEvent.KEYCODE_APOSTROPHE -> if (isShifted) "\"" else "'"
        KeyEvent.KEYCODE_LEFT_BRACKET -> if (isShifted) "{" else "["
        KeyEvent.KEYCODE_RIGHT_BRACKET -> if (isShifted) "}" else "]"
        KeyEvent.KEYCODE_GRAVE -> if (isShifted) "~" else "`"
        KeyEvent.KEYCODE_PLUS -> "+"
        KeyEvent.KEYCODE_STAR -> "*"
        KeyEvent.KEYCODE_NUMPAD_ADD -> "+"
        KeyEvent.KEYCODE_NUMPAD_MULTIPLY -> "*"
        KeyEvent.KEYCODE_TAB -> "\t"
        else -> null
    }
}
