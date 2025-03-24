package com.example.myapplication.scripts;

import static com.example.myapplication.activity.MainActivity.TAG;

import android.util.Log;

import okhttp3.*;
import org.json.*;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import com.alibaba.dashscope.aigc.multimodalconversation.MultiModalConversation;
import com.alibaba.dashscope.aigc.multimodalconversation.MultiModalConversationParam;
import com.alibaba.dashscope.aigc.multimodalconversation.MultiModalConversationResult;
import com.alibaba.dashscope.common.MultiModalMessage;
import com.alibaba.dashscope.common.Role;
import com.alibaba.dashscope.exception.NoApiKeyException;
import com.alibaba.dashscope.exception.UploadFileException;

abstract class BaseModel {
    public abstract Map.Entry<Boolean, String> getModelResponse(String prompt, List<String> images);
}

class OpenAIModel extends BaseModel {
    private final String baseUrl;
    private final String apiKey;
    private final String model;
    private final double temperature;
    private final int maxTokens;
    private final OkHttpClient client = new OkHttpClient();

    public OpenAIModel(String baseUrl, String apiKey, String model, double temperature, int maxTokens) {
        this.baseUrl = baseUrl;
        this.apiKey = apiKey;
        this.model = model;
        this.temperature = temperature;
        this.maxTokens = maxTokens;
    }

    @Override
    public Map.Entry<Boolean, String> getModelResponse(String prompt, List<String> images) {
        try {
            JSONArray content = new JSONArray();
            content.put(new JSONObject().put("type", "text").put("text", prompt));

            for (String imgPath : images) {
                String base64Img = encodeImage(imgPath);
                JSONObject imgObj = new JSONObject()
                        .put("type", "image_url")
                        .put("image_url", new JSONObject().put("url", "data:image/jpeg;base64," + base64Img));
                content.put(imgObj);
            }

            JSONObject payload = new JSONObject()
                    .put("model", model)
                    .put("messages", new JSONArray().put(new JSONObject()
                            .put("role", "user")
                            .put("content", content)))
                    .put("temperature", temperature)
                    .put("max_tokens", maxTokens);

            RequestBody body = RequestBody.create(payload.toString(), MediaType.get("application/json; charset=utf-8"));

            Request request = new Request.Builder()
                    .url(baseUrl)
                    .header("Authorization", "Bearer " + apiKey)
                    .header("Content-Type", "application/json")
                    .post(body)
                    .build();

            Response response = client.newCall(request).execute();
            String responseBody = response.body().string();
            JSONObject jsonResponse = new JSONObject(responseBody);

            if (!jsonResponse.has("error")) {
                return new AbstractMap.SimpleEntry<>(true, jsonResponse.getJSONArray("choices").getJSONObject(0).getJSONObject("message").getString("content"));
            } else {
                return new AbstractMap.SimpleEntry<>(false, jsonResponse.getJSONObject("error").getString("message"));
            }
        } catch (Exception e) {
            return new AbstractMap.SimpleEntry<>(false, e.getMessage());
        }
    }

    private String encodeImage(String imagePath) throws IOException {
        File file = new File(imagePath);
        byte[] bytes = Files.readAllBytes(file.toPath());
        return Base64.getEncoder().encodeToString(bytes);
    }
}

public class QwenModel extends BaseModel {
    private final String apiKey;
    private final String model;
    public QwenModel(String apiKey, String model) {
        this.apiKey = apiKey;
        this.model = model;
    }
    @Override
    public Map.Entry<Boolean, String> getModelResponse(String prompt, List<String> images) {
        try {
            String image = "file://" + images.get(0);
            MultiModalMessage multiModalMessage = MultiModalMessage.builder()
                    .role(Role.USER.getValue())
                    .content(Arrays.asList(
                            Collections.singletonMap("image", image),
                            Collections.singletonMap("text", prompt))).build();
            MultiModalConversationParam multiModalConversationParam = MultiModalConversationParam.builder()
                    .model(model)
                    .apiKey(apiKey)
                    .message(multiModalMessage)
                    .build();
            MultiModalConversation multiModalConversation = new MultiModalConversation();
            Log.d(TAG, "开始call");
            MultiModalConversationResult result = multiModalConversation.call(multiModalConversationParam);
            String msg = (String) result.getOutput().getChoices().get(0).getMessage().getContent().get(0).get("text");
            Log.d(TAG, "Qwen 回答结果  result:" + result);
            return new AbstractMap.SimpleEntry<>(true, msg);
        } catch (NoApiKeyException e) {
            throw new RuntimeException(e);
        } catch (UploadFileException e) {
            throw new RuntimeException(e);
        }
    }


}

class ResponseParser {
    public static List<String> parseExploreRsp(String rsp) {
        try {
            String observation = extractValue(rsp, "Observation: (.*?)$");
            String think = extractValue(rsp, "Thought: (.*?)$");
            String act = extractValue(rsp, "Action: (.*?)$");
            String lastAct = extractValue(rsp, "Summary: (.*?)$");

            utils.printWithColor("Observation:", "yellow");
            utils.printWithColor(observation, "magenta");
            utils.printWithColor("Thought:", "yellow");
            utils.printWithColor(think, "magenta");
            utils.printWithColor("Action:", "yellow");
            utils.printWithColor(act, "magenta");
            utils.printWithColor("Summary:", "yellow");
            utils.printWithColor(lastAct, "magenta");

            if (act.contains("FINISH")) {
                return Collections.singletonList("FINISH");
            }

            String actName = act.split("\\(")[0];

            if ("tap".equals(actName)) {
                int area = Integer.parseInt(extractValue(act, "tap\\((\\d+)\\)"));
                return Arrays.asList(actName, String.valueOf(area), lastAct);
            } else if ("text".equals(actName)) {
                String inputStr = extractValue(act, "text\\(\"(.*?)\"\\)");
                return Arrays.asList(actName, inputStr, lastAct);
            } else if ("long_press".equals(actName)) {
                int area = Integer.parseInt(extractValue(act, "long_press\\((\\d+)\\)"));
                return Arrays.asList(actName, String.valueOf(area), lastAct);
            } else if ("swipe".equals(actName)) {
                String params = extractValue(act, "swipe\\((.*?)\\)");
                String[] parts = params.split(",");
                int area = Integer.parseInt(parts[0].trim());
                String swipeDir = parts[1].trim().replace("\"", "");
                String dist = parts[2].trim().replace("\"", "");
                return Arrays.asList(actName, String.valueOf(area), swipeDir, dist, lastAct);
            } else if ("grid".equals(actName)) {
                return Collections.singletonList(actName);
            } else {
                utils.printWithColor("ERROR: Undefined act " + actName + "!", "red");
                return Collections.singletonList("ERROR");
            }
        } catch (Exception e) {
            utils.printWithColor("ERROR: an exception occurs while parsing the model response: " + e.getMessage(), "red");
            utils.printWithColor(rsp, "red");
            return Collections.singletonList("ERROR");
        }
    }

    public static List<String> parseGridRsp(String rsp) {
        try {
            String observation = extractValue(rsp, "Observation: (.*?)$");
            String think = extractValue(rsp, "Thought: (.*?)$");
            String act = extractValue(rsp, "Action: (.*?)$");
            String lastAct = extractValue(rsp, "Summary: (.*?)$");

            utils.printWithColor("Observation:", "yellow");
            utils.printWithColor(observation, "magenta");
            utils.printWithColor("Thought:", "yellow");
            utils.printWithColor(think, "magenta");
            utils.printWithColor("Action:", "yellow");
            utils.printWithColor(act, "magenta");
            utils.printWithColor("Summary:", "yellow");
            utils.printWithColor(lastAct, "magenta");

            if (act.contains("FINISH")) {
                return Collections.singletonList("FINISH");
            }

            String actName = act.split("\\(")[0];

            if ("tap".equals(actName)) {
                String[] params = extractValue(act, "tap\\((.*?)\\)").split(",");
                int area = Integer.parseInt(params[0].trim());
                String subarea = params[1].trim().replace("\"", "");
                return Arrays.asList(actName + "_grid", String.valueOf(area), subarea, lastAct);
            } else if ("long_press".equals(actName)) {
                String[] params = extractValue(act, "long_press\\((.*?)\\)").split(",");
                int area = Integer.parseInt(params[0].trim());
                String subarea = params[1].trim().replace("\"", "");
                return Arrays.asList(actName + "_grid", String.valueOf(area), subarea, lastAct);
            } else if ("swipe".equals(actName)) {
                String[] params = extractValue(act, "swipe\\((.*?)\\)").split(",");
                int startArea = Integer.parseInt(params[0].trim());
                String startSubarea = params[1].trim().replace("\"", "");
                int endArea = Integer.parseInt(params[2].trim());
                String endSubarea = params[3].trim().replace("\"", "");
                return Arrays.asList(actName + "_grid", String.valueOf(startArea), startSubarea, String.valueOf(endArea), endSubarea, lastAct);
            } else if ("grid".equals(actName)) {
                return Collections.singletonList(actName);
            } else {
                utils.printWithColor("ERROR: Undefined act " + actName + "!", "red");
                return Collections.singletonList("ERROR");
            }
        } catch (Exception e) {
            utils.printWithColor("ERROR: an exception occurs while parsing the model response: " + e.getMessage(), "red");
            utils.printWithColor(rsp, "red");
            return Collections.singletonList("ERROR");
        }
    }

    public static List<String> parseReflectRsp(String rsp) {
        try {
            String decision = extractValue(rsp, "Decision: (.*?)$");
            String think = extractValue(rsp, "Thought: (.*?)$");

            utils.printWithColor("Decision:", "yellow");
            utils.printWithColor(decision, "magenta");
            utils.printWithColor("Thought:", "yellow");
            utils.printWithColor(think, "magenta");

            if ("INEFFECTIVE".equals(decision)) {
                return Arrays.asList(decision, think);
            } else if ("BACK".equals(decision) || "CONTINUE".equals(decision) || "SUCCESS".equals(decision)) {
                String doc = extractValue(rsp, "Documentation: (.*?)$");
                utils.printWithColor("Documentation:", "yellow");
                utils.printWithColor(doc, "magenta");
                return Arrays.asList(decision, think, doc);
            } else {
                utils.printWithColor("ERROR: Undefined decision " + decision + "!", "red");
                return Collections.singletonList("ERROR");
            }
        } catch (Exception e) {
            utils.printWithColor("ERROR: an exception occurs while parsing the model response: " + e.getMessage(), "red");
            utils.printWithColor(rsp, "red");
            return Collections.singletonList("ERROR");
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



