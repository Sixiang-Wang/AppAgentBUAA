package com.example.myapplication.scripts

import android.annotation.SuppressLint
import android.content.Context
import android.view.accessibility.AccessibilityNodeInfo
import com.example.myapplication.myAccessibilityService;

public class Ut {
    companion object {
        @SuppressLint("ServiceCast")
        @JvmStatic
        fun text(inputStr: String) {
            val service = myAccessibilityService.getInstance()

            // 获取当前窗口中的可交互元素
            val rootNode = service.myActiveRootNode ?: run {
                println("rootInActiveWindow is null.")
                return
            }
            // 查找所有的 EditText
            val inputNodeClasses = setOf(
                "android.widget.EditText",
                "android.widget.AutoCompleteTextView",
                "com.google.android.material.textfield.TextInputEditText"
            )

            val inputNodes = findAllInputFields(rootNode)
                .filter {
                    val cls = it.className ?: return@filter false
                    cls in inputNodeClasses || cls.contains("EditText")
                }

            if (inputNodes.isNotEmpty()) {
                val inputField = inputNodes[0]
                inputField.performAction(AccessibilityNodeInfo.ACTION_FOCUS)  // 请求焦点
                inputField.performAction(AccessibilityNodeInfo.ACTION_CLICK)  // 点击输入框
                println("Clicked on input field.")

                // 等待输入法弹出
                Thread.sleep(500)
                println("Begin to text $inputStr")
                myAccessibilityService.getInstance().run {
                    inputMethod ?: error("No input method.")
                }.run {
                    currentInputConnection ?: error("No current input connection.")
                }.commitText(inputStr, 0x1, null)
                println("End to text $inputStr")
            } else {
                println("No EditText field found.")
            }
        }
        fun findAllInputFields(node: AccessibilityNodeInfo?): List<AccessibilityNodeInfo> {
            val result = mutableListOf<AccessibilityNodeInfo>()
            if (node == null) return result

            val className = node.className?.toString() ?: ""

            // 定义输入框的识别规则
            val isInputField = (
                    (className.contains("EditText") || className.contains("TextView"))
                            && node.isEditable
                    )

            if (isInputField) {
                result.add(node)
            }

            for (i in 0 until node.childCount) {
                result.addAll(findAllInputFields(node.getChild(i)))
            }

            return result
        }


    }

}
