package com.example.myapplication.scripts

import com.example.myapplication.myAccessibilityService;

public class Ut {
    companion object {
        @JvmStatic
        fun text(inputStr: String) {
            println("Begin to text $inputStr")
            myAccessibilityService.getInstance().run {
                inputMethod ?: error("No input method.")
            }.run {
                currentInputConnection ?: error("No current input connection.")
            }.commitText(inputStr, 0x1, null)
            println("End to text $inputStr")
        }
    }
}
