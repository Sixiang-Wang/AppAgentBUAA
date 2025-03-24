package org.example.scripts;

import java.io.*;
import java.util.*;
import org.yaml.snakeyaml.DumperOptions;
import org.yaml.snakeyaml.Yaml;

public class Config {

    private static Map<String, String> configs = null;
    private static String configPath = "../config.yaml";
    // 静态方法加载配置文件
    public static Map<String, String> loadConfig() {
        if (configs == null) {
            configs = new HashMap<>(System.getenv());  // 加载环境变量

            try {
                // 读取 YAML 配置文件
                FileInputStream fileInputStream = new FileInputStream(configPath);
                Yaml yaml = new Yaml();
                Map<String, Object> yamlData = yaml.load(fileInputStream);

                // 将 YAML 数据添加到 configs 中
                if (yamlData != null) {
                    for (Map.Entry<String, Object> entry : yamlData.entrySet()) {
                        configs.put(entry.getKey(), entry.getValue().toString());
                    }
                }
            } catch (IOException e) {
                System.out.println("Error loading configuration file: " + e.getMessage());
            }
        }
        return configs;
    }

    // 获取特定的配置项
    public static String get(String key) {
        if (configs == null) {
            loadConfig();  // 默认加载配置文件
        }
        return configs.getOrDefault(key, "");  // 返回空字符串如果没有找到
    }

    // 测试方法
    public static void main(String[] args) {
        // 测试加载配置
        Map<String, String> configs = loadConfig();
        System.out.println(configs);

        // 示例：获取特定的配置项
        String screenshotDir = get("ANDROID_SCREENSHOT_DIR");
        String xmlDir = get("ANDROID_XML_DIR");

        System.out.println("Screenshot Directory: " + screenshotDir);
        System.out.println("XML Directory: " + xmlDir);
    }
}
