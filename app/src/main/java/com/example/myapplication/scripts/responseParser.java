package com.example.myapplication.scripts;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;



public class responseParser {
    private responseParser(){

    }
    public static ArrayList<String> parseExploreRsp_Chinese(String rsp){
        try {
            String observation = extractValue(rsp, "观察：(.*?)$");
            String act = extractValue(rsp, "行动：(.*?)$");
            String doc = extractValue(rsp, "控件信息：(.*?)$");
            String lastAct = extractValue(rsp, "总结：(.*?)$");
            printUtils.printWithColor("观察：", "yellow");
            printUtils.printWithColor(observation, "yellow");
            printUtils.printWithColor("行动：", "yellow");
            printUtils.printWithColor(act, "yellow");
            printUtils.printWithColor("控件信息：", "yellow");
            printUtils.printWithColor(doc, "yellow");
            printUtils.printWithColor("总结：", "yellow");
            printUtils.printWithColor(lastAct, "yellow");
            if (act.contains("FINISH")) {
                return new ArrayList<>(Arrays.asList("FINISH", lastAct));
            }
            String actName = act.split("\\(")[0];
            if ("tap".equals(actName)) {
                int area = Integer.parseInt(extractValue(act, "tap\\((\\d+)\\)"));
                return new ArrayList<>(Arrays.asList(actName, String.valueOf(area),observation,doc,lastAct));
            } else if ("text".equals(actName)) {
                String inputStr = extractValue(act, "text\\(\"(.*?)\"\\)");
                return new ArrayList<>(Arrays.asList(actName, inputStr,observation,doc,lastAct));
            } else if ("long_press".equals(actName)) {
                int area = Integer.parseInt(extractValue(act, "long_press\\((\\d+)\\)"));
                return new ArrayList<>(Arrays.asList(actName, String.valueOf(area),observation,doc,lastAct));
            } else if ("swipe".equals(actName)) {
                String params = extractValue(act, "swipe\\((.*?)\\)");
                String[] parts = params.split(",");
                int area = Integer.parseInt(parts[0].trim());
                String swipeDir = parts[1].trim().replace("\"", "");
                String dist = parts[2].trim().replace("\"", "");
                return new ArrayList<>(Arrays.asList(actName, String.valueOf(area), swipeDir, dist, observation,doc,lastAct));
            } else {
                printUtils.printWithColor("ERROR: Undefined act " + actName + "!", "red");
                return new ArrayList<>(Collections.singletonList("ERROR"));
            }
        }catch (Exception e) {
            printUtils.printWithColor("ERROR: an exception occurs while parsing the model response: " + e.getMessage(), "red");
            printUtils.printWithColor(rsp, "red");
            return new ArrayList<>(Collections.singletonList("ERROR"));
        }
    }
    public static ArrayList<String> parseExploreRsp(String rsp) {
        try {
            String observation = extractValue(rsp, "Observation: (.*?)$");
            String think = extractValue(rsp, "Thought: (.*?)$");
            String think_chinese = extractValue(rsp,"Thought_Chinese: (.*?)$");
            String act = extractValue(rsp, "Action: (.*?)$");
            String lastAct = extractValue(rsp, "Summary: (.*?)$");
            printUtils.printWithColor("Observation:", "yellow");
            printUtils.printWithColor(observation, "magenta");
            printUtils.printWithColor("Thought:", "yellow");
            printUtils.printWithColor(think, "magenta");
            printUtils.printWithColor("Thought_Chinese:", "yellow");
            printUtils.printWithColor(think_chinese, "magenta");
            printUtils.printWithColor("Action:", "yellow");
            printUtils.printWithColor(act, "magenta");
            printUtils.printWithColor("Summary:", "yellow");
            printUtils.printWithColor(lastAct, "magenta");

            if (act.contains("FINISH")) {
                return new ArrayList<>(Arrays.asList("FINISH",think_chinese));
            }
            String actName = act.split("\\(")[0];
            if ("tap".equals(actName)) {
                int area = Integer.parseInt(extractValue(act, "tap\\((\\d+)\\)"));
                return new ArrayList<>(Arrays.asList(actName, String.valueOf(area), think_chinese,lastAct));
            } else if ("text".equals(actName)) {
                String inputStr = extractValue(act, "text\\(\"(.*?)\"\\)");
                return new ArrayList<>(Arrays.asList(actName, inputStr, think_chinese,lastAct));
            } else if ("long_press".equals(actName)) {
                int area = Integer.parseInt(extractValue(act, "long_press\\((\\d+)\\)"));
                return new ArrayList<>(Arrays.asList(actName, String.valueOf(area), think_chinese,lastAct));
            } else if ("swipe".equals(actName)) {
                String params = extractValue(act, "swipe\\((.*?)\\)");
                String[] parts = params.split(",");
                int area = Integer.parseInt(parts[0].trim());
                String swipeDir = parts[1].trim().replace("\"", "");
                String dist = parts[2].trim().replace("\"", "");
                return new ArrayList<>(Arrays.asList(actName, String.valueOf(area), swipeDir, dist, think_chinese, lastAct));
            } else if ("grid".equals(actName)) {
                return new ArrayList<>(Collections.singletonList(actName));
            } else {
                printUtils.printWithColor("ERROR: Undefined act " + actName + "!", "red");
                return new ArrayList<>(Collections.singletonList("ERROR"));
            }
        } catch (Exception e) {
            printUtils.printWithColor("ERROR: an exception occurs while parsing the model response: " + e.getMessage(), "red");
            printUtils.printWithColor(rsp, "red");
            return new ArrayList<>(Collections.singletonList("ERROR"));
        }
    }



    public static ArrayList<String> parseGridRsp(String rsp) {
        try {
            String observation = extractValue(rsp, "Observation: (.*?)$");
            String think = extractValue(rsp, "Thought: (.*?)$");
            String act = extractValue(rsp, "Action: (.*?)$");
            String lastAct = extractValue(rsp, "Summary: (.*?)$");

            printUtils.printWithColor("Observation:", "yellow");
            printUtils.printWithColor(observation, "magenta");
            printUtils.printWithColor("Thought:", "yellow");
            printUtils.printWithColor(think, "magenta");
            printUtils.printWithColor("Action:", "yellow");
            printUtils.printWithColor(act, "magenta");
            printUtils.printWithColor("Summary:", "yellow");
            printUtils.printWithColor(lastAct, "magenta");

            if (act.contains("FINISH")) {
                return  new ArrayList<>(Collections.singletonList("FINISH"));
            }

            String actName = act.split("\\(")[0];

            if ("tap".equals(actName)) {
                String[] params = extractValue(act, "tap\\((.*?)\\)").split(",");
                int area = Integer.parseInt(params[0].trim());
                String subarea = params[1].trim().replace("\"", "");
                return new ArrayList<>(Arrays.asList(actName + "_grid", String.valueOf(area), subarea, lastAct));
            } else if ("long_press".equals(actName)) {
                String[] params = extractValue(act, "long_press\\((.*?)\\)").split(",");
                int area = Integer.parseInt(params[0].trim());
                String subarea = params[1].trim().replace("\"", "");
                return new ArrayList<>((Arrays.asList(actName + "_grid", String.valueOf(area), subarea, lastAct)));
            } else if ("swipe".equals(actName)) {
                String[] params = extractValue(act, "swipe\\((.*?)\\)").split(",");
                int startArea = Integer.parseInt(params[0].trim());
                String startSubarea = params[1].trim().replace("\"", "");
                int endArea = Integer.parseInt(params[2].trim());
                String endSubarea = params[3].trim().replace("\"", "");
                return new ArrayList<>(Arrays.asList(actName + "_grid", String.valueOf(startArea), startSubarea, String.valueOf(endArea), endSubarea, lastAct));
            } else if ("grid".equals(actName)) {
                return new ArrayList<>(Collections.singletonList(actName));
            } else {
                printUtils.printWithColor("ERROR: Undefined act " + actName + "!", "red");
                return new ArrayList<>(Collections.singletonList("ERROR"));
            }
        } catch (Exception e) {
            printUtils.printWithColor("ERROR: an exception occurs while parsing the model response: " + e.getMessage(), "red");
            printUtils.printWithColor(rsp, "red");
            return new ArrayList<>(Collections.singletonList("ERROR"));
        }
    }

    public static ArrayList<String> parseReflectRsp(String rsp) {
        try {
            String decision = extractValue(rsp, "Decision: (.*?)$");
            String think = extractValue(rsp, "Thought: (.*?)$");

            printUtils.printWithColor("Decision:", "yellow");
            printUtils.printWithColor(decision, "magenta");
            printUtils.printWithColor("Thought:", "yellow");
            printUtils.printWithColor(think, "magenta");

            if ("INEFFECTIVE".equals(decision)) {
                return new ArrayList<>(Arrays.asList(decision, think));
            } else if ("BACK".equals(decision) || "CONTINUE".equals(decision) || "NOT_COMPLETELY_SUCCESS".equals(decision) || "COMPLETELY_SUCCESS".equals(decision)) {
                String doc = extractValue(rsp, "Documentation: (.*?)$");
                printUtils.printWithColor("Documentation:", "yellow");
                printUtils.printWithColor(doc, "magenta");
                return new ArrayList<>(Arrays.asList(decision, think, doc));
            } else {
                printUtils.printWithColor("ERROR: Undefined decision " + decision + "!", "red");
                return new ArrayList<>(Collections.singletonList("ERROR"));
            }
        } catch (Exception e) {
            printUtils.printWithColor("ERROR: an exception occurs while parsing the model response: " + e.getMessage(), "red");
            printUtils.printWithColor(rsp, "red");
            return new ArrayList<>(Collections.singletonList("ERROR"));
        }
    }

    private static String extractValue(String text, String pattern) throws Exception {
        Pattern p = Pattern.compile(pattern, Pattern.MULTILINE);
        Matcher m = p.matcher(text);
        if (m.find()) {
            return m.group(1);
        } else {
            throw new Exception("Pattern not found: " + pattern);
        }
    }
}
