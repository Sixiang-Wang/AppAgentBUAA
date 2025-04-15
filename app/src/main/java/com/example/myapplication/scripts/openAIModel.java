package com.example.myapplication.scripts;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.util.AbstractMap;
import java.util.Base64;
import java.util.List;
import java.util.Map;

import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;

public class openAIModel extends baseModel {
    private final String baseUrl;
    private final String apiKey;
    private final String model;
    private final double temperature;
    private final int maxTokens;
    private final OkHttpClient client = new OkHttpClient();

    public openAIModel(String baseUrl, String apiKey, String model, double temperature, int maxTokens) {
        this.baseUrl = baseUrl;
        this.apiKey = apiKey;
        this.model = model;
        this.temperature = temperature;
        this.maxTokens = maxTokens;
    }

    public Map.Entry<Boolean, String> getModelResponse_tasksplit(String prompt) {
        return null;
    }

    public Map.Entry<Boolean, String> getModelResponse_keyword(String prompt){
        return null;
    }
    public Map.Entry<Boolean, String> getModelResponse_keyword_2(String prompt){
        return null;
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