package com.example.myapplication.scripts;
import static com.example.myapplication.activity.MainActivity.TAG;

import android.content.Context;
import android.util.Log;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileOutputStream;
import java.io.FileReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;

import org.json.JSONException;
import org.json.JSONObject;
import org.json.JSONTokener;
public class config {
    public static Map<String, String> loadConfig(Context context) {
        copyConfigToInternalStorage(context);
        Map<String, String> configs = new HashMap<>(System.getenv());
        // 读取 JSON 配置文件
        File configFile = new File(context.getFilesDir(), "config.json");
        if (configFile.exists()) {
            try (BufferedReader reader = new BufferedReader(new FileReader(configFile))) {
                // 读取 JSON 文件内容并转换为字符串
                StringBuilder jsonText = new StringBuilder();
                String line;
                while ((line = reader.readLine()) != null) {
                    jsonText.append(line);
                }

                // 解析 JSON
                JSONObject jsonObject = new JSONObject(new JSONTokener(jsonText.toString()));

                // 遍历 JSON 对象并转换为 Map<String, String>
                Iterator<String> keys = jsonObject.keys();
                while (keys.hasNext()) {
                    String key = keys.next();
                    configs.put(key, jsonObject.get(key).toString());  // 确保转换为字符串
                }
            } catch (IOException | JSONException e) {
                e.printStackTrace();
            }
        }
        return configs;
    }
    public static void copyConfigToInternalStorage(Context context) {
        File internalFile = new File(context.getFilesDir(), "config.json");
        // 检查文件是否已存在，避免重复复制
        if (internalFile.exists()) {
            Log.d(TAG, "config.json already exists in internal storage.");
            return;
        }

        try (InputStream inputStream = context.getAssets().open("config.json");
             OutputStream outputStream = new FileOutputStream(internalFile)) {

            byte[] buffer = new byte[1024];
            int length;
            while ((length = inputStream.read(buffer)) > 0) {
                outputStream.write(buffer, 0, length);
            }

            Log.d(TAG, "config.json copied successfully to internal storage.");
        } catch (IOException e) {
            Log.e(TAG, "Failed to copy config.json", e);
        }
    }
}
