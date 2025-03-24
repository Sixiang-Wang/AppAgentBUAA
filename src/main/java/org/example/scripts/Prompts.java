package org.example.scripts;
public class Prompts {
    public static final String TAP_DOC_TEMPLATE =
            "I will give you the screenshot of a mobile app before and after tapping the UI element labeled " +
                    "with the number <ui_element> on the screen. The numeric tag of each element is located at the center of the element. " +
                    "Tapping this UI element is a necessary part of proceeding with a larger task, which is to <task_desc>. Your task is to " +
                    "describe the functionality of the UI element concisely in one or two sentences. Notice that your description of the UI " +
                    "element should focus on the general function. For example, if the UI element is used to navigate to the chat window " +
                    "with John, your description should not include the name of the specific person. Just say: \"Tapping this area will " +
                    "navigate the user to the chat window\". Never include the numeric tag of the UI element in your description. You can use " +
                    "pronouns such as \"the UI element\" to refer to the element.";

    public static final String TEXT_DOC_TEMPLATE =
            "I will give you the screenshot of a mobile app before and after typing in the input area labeled " +
                    "with the number <ui_element> on the screen. The numeric tag of each element is located at the center of the element. " +
                    "Typing in this UI element is a necessary part of proceeding with a larger task, which is to <task_desc>. Your task is " +
                    "to describe the functionality of the UI element concisely in one or two sentences. For example, if the change of the " +
                    "screenshot shows that the user typed \"How are you?\" in the chat box, you do not need to mention the actual text. " +
                    "Just say: \"This input area is used for the user to type a message to send to the chat window.\" Never include the " +
                    "numeric tag of the UI element in your description. You can use pronouns such as \"the UI element\" to refer to the element.";

    public static final String LONG_PRESS_DOC_TEMPLATE =
            "I will give you the screenshot of a mobile app before and after long pressing the UI element labeled " +
                    "with the number <ui_element> on the screen. The numeric tag of each element is located at the center of the element. " +
                    "Long pressing this UI element is a necessary part of proceeding with a larger task, which is to <task_desc>. Your task " +
                    "is to describe the functionality of the UI element concisely in one or two sentences. For example, if long pressing " +
                    "the UI element redirects the user to the chat window with John, your description should not include the name of the " +
                    "specific person. Just say: \"Long pressing this area will redirect the user to the chat window\". Never include the " +
                    "numeric tag of the UI element in your description. You can use pronouns such as \"the UI element\" to refer to the element.";

    public static final String SWIPE_DOC_TEMPLATE =
            "I will give you the screenshot of a mobile app before and after swiping <swipe_dir> the UI element labeled " +
                    "with the number <ui_element> on the screen. The numeric tag of each element is located at the center of the element. " +
                    "Swiping this UI element is a necessary part of proceeding with a larger task, which is to <task_desc>. Your task is " +
                    "to describe the functionality of the UI element concisely in one or two sentences. For example, if swiping the UI " +
                    "element increases the contrast ratio of an image of a building, your description should be just like this: \"Swiping " +
                    "this area enables the user to tune a specific parameter of the image\". Never include the numeric tag of the UI element " +
                    "in your description. You can use pronouns such as \"the UI element\" to refer to the element.";

    public static final String REFINE_DOC_SUFFIX =
            "\nA documentation of this UI element generated from previous demos is shown below. Your " +
                    "generated description should be based on this previous doc and optimize it. Notice that it is possible that your " +
                    "understanding of the function of the UI element derived from the given screenshots conflicts with the previous doc, " +
                    "because the function of a UI element can be flexible. In this case, your generated description should combine both.\n" +
                    "Old documentation of this UI element: <old_doc>";

    public static final String TASK_TEMPLATE =
            "You are an agent that is trained to perform some basic tasks on a smartphone. You will be given a " +
                    "smartphone screenshot. The interactive UI elements on the screenshot are labeled with numeric tags starting from 1. The " +
                    "numeric tag of each interactive element is located in the center of the element.\n\n" +
                    "You can call the following functions to control the smartphone:\n\n" +
                    "1. tap(element: int)\n" +
                    "This function is used to tap an UI element shown on the smartphone screen.\n" +
                    "\"element\" is a numeric tag assigned to an UI element shown on the smartphone screen.\n" +
                    "A simple use case can be tap(5), which taps the UI element labeled with the number 5.\n\n" +
                    "2. text(text_input: str)\n" +
                    "This function is used to insert text input in an input field/box. text_input is the string you want to insert and must " +
                    "be wrapped with double quotation marks. A simple use case can be text(\"Hello, world!\"), which inserts the string " +
                    "\"Hello, world!\" into the input area on the smartphone screen. This function is usually callable when you see a keyboard " +
                    "showing in the lower half of the screen.\n\n" +
                    "3. long_press(element: int)\n" +
                    "This function is used to long press an UI element shown on the smartphone screen.\n" +
                    "\"element\" is a numeric tag assigned to an UI element shown on the smartphone screen.\n" +
                    "A simple use case can be long_press(5), which long presses the UI element labeled with the number 5.\n\n" +
                    "4. swipe(element: int, direction: str, dist: str)\n" +
                    "This function is used to swipe an UI element shown on the smartphone screen, usually a scroll view or a slide bar.\n" +
                    "\"element\" is a numeric tag assigned to an UI element shown on the smartphone screen. \"direction\" is a string that " +
                    "represents one of the four directions: up, down, left, right. \"direction\" must be wrapped with double quotation " +
                    "marks. \"dist\" determines the distance of the swipe and can be one of the three options: short, medium, long. You should " +
                    "choose the appropriate distance option according to your need.\n" +
                    "A simple use case can be swipe(21, \"up\", \"medium\"), which swipes up the UI element labeled with the number 21 for a " +
                    "medium distance.\n\n" +
                    "5. grid()\n" +
                    "You should call this function when you find the element you want to interact with is not labeled with a numeric tag and " +
                    "other elements with numeric tags cannot help with the task. The function will bring up a grid overlay to divide the " +
                    "smartphone screen into small areas and this will give you more freedom to choose any part of the screen to tap, long " +
                    "press, or swipe.\n<ui_document>\nThe task you need to complete is to <task_description>. Your past actions to proceed with this task are summarized as\n" +
                    "follows: <last_act>\nNow, given the documentation and the following labeled screenshot, you need to think and call the function needed to " +
                    "proceed with the task. Your output should include three parts in the given format:\nObservation: <Describe what you observe in the image>\n" +
                    "Thought: <To complete the given task, what is the next step I should do>\n" +
                    "Action: <The function call with the correct parameters to proceed with the task. If you believe the task is completed or " +
                    "there is nothing to be done, you should output FINISH. You cannot output anything else except a function call or FINISH in this field.>\n" +
                    "Summary: <Summarize your past actions along with your latest action in one or two sentences. Do not include the numeric " +
                    "tag in your summary>\nYou can only take one action at a time, so please directly call the function.";
}
