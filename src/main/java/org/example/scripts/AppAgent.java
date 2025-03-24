package org.example.scripts;

import java.io.*;
import java.nio.file.*;
import java.util.*;
import java.util.regex.*;
import org.yaml.snakeyaml.Yaml;

public class AppAgent {
    private static Map<String, String> configs;

    public static void main(String[] args) throws IOException {
        if (args.length < 2) {
            System.err.println("Usage: java AppAgent --app APP_NAME --demo DEMO_NAME [--root_dir ROOT_DIR]");
            System.exit(1);
        }

        // 解析命令行参数
        String app = null, demo = null, rootDir = "./";
        for (int i = 0; i < args.length; i++) {
            if (args[i].equals("--app")) app = args[++i];
            else if (args[i].equals("--demo")) demo = args[++i];
            else if (args[i].equals("--root_dir")) rootDir = args[++i];
        }

        if (app == null || demo == null) {
            System.err.println("ERROR: --app and --demo are required parameters!");
            System.exit(1);
        }

        // 加载配置
        configs = loadConfig("./config.yaml");

        // 设置工作目录路径
        String workDir = Paths.get(rootDir, "apps", app).toString();
        String demoDir = Paths.get(workDir, "demos", demo).toString();
        String taskDir = Paths.get(demoDir, "xml").toString();
        String labeledSsDir = Paths.get(demoDir, "labeled_screenshots").toString();
        String recordPath = Paths.get(taskDir, "record.txt").toString();
        String taskDescPath = Paths.get(taskDir, "task_desc.txt").toString();
        String logPath = Paths.get(taskDir, "log_" + app + "_" + demo + ".txt").toString();
        String docsDir = Paths.get(workDir, "demo_docs").toString();

        // 确保文件存在
        if (!Files.exists(Paths.get(taskDir)) || !Files.exists(Paths.get(recordPath))) {
            System.err.println("ERROR: Missing required files in " + taskDir);
            System.exit(1);
        }
        Files.createDirectories(Paths.get(docsDir));

        System.out.println("Starting documentation generation for " + app + " demo " + demo);

        // 读取 task 描述
        String taskDesc = new String(Files.readAllBytes(Paths.get(taskDescPath)));

        // 读取操作记录
        List<String> lines = Files.readAllLines(Paths.get(recordPath));
        for (int i = 0; i < lines.size() - 1; i++) {
            String[] parts = lines.get(i).split(":::");
            if (parts.length < 2) continue;

            String action = parts[0];
            String resourceId = parts[1];
            String actionType = action.split("\\(")[0];
            String actionParam = action.replaceAll(".*\\((.*?)\\)", "$1");

            String prompt = "";
            switch (actionType) {
                case "tap":
                    prompt = Prompts.TAP_DOC_TEMPLATE.replace("<ui_element>", actionParam);
                    break;
                case "text":
                    String[] textParams = actionParam.split(":sep:");
                    prompt = Prompts.TEXT_DOC_TEMPLATE.replace("<ui_element>", textParams[0]);
                    break;
                case "long_press":
                    prompt = Prompts.LONG_PRESS_DOC_TEMPLATE.replace("<ui_element>", actionParam);
                    break;
                case "swipe":
                    String[] swipeParams = actionParam.split(":sep:");
                    String swipeDir = swipeParams[1];
                    if(swipeDir.equals("up") || swipeDir.equals("down")) {
                        actionType = "v_swipe";
                    }else{
                        actionType = "h_swipe";
                    }
                    prompt = Prompts.SWIPE_DOC_TEMPLATE.replace("<ui_element>", swipeParams[0])
                            .replace("<swipe_dir>", swipeDir);
                    break;
            }
            prompt = prompt.replace("<task_desc>", taskDesc);

            // 读取或创建文档
            String docPath = Paths.get(docsDir, resourceId + ".txt").toString();
            Map<String, String> docContent = new HashMap<>();
            if (Files.exists(Paths.get(docPath))) {
                docContent = loadDoc(docPath);
                if (!docContent.getOrDefault(actionType, "").isEmpty()) {
                    if (Boolean.parseBoolean(configs.get("DOC_REFINE"))) {
                        prompt += Prompts.REFINE_DOC_SUFFIX.replace("<old_doc>", docContent.get(actionType));
                        System.out.println("Refining existing doc for " + resourceId);
                    } else {
                        System.out.println("Skipping existing doc for " + resourceId);
                        continue;
                    }
                }
            } else {
                docContent.put("tap", "");
                docContent.put("text", "");
                docContent.put("v_swipe", "");
                docContent.put("h_swipe", "");
                docContent.put("long_press", "");
            }

            // 调用 LLM 生成文档（此处模拟）
            String response = simulateLLMResponse(prompt);
            docContent.put(actionType, response);
            Files.write(Paths.get(docPath), Collections.singleton(response));

            System.out.println("Documentation generated for " + resourceId);
        }

        System.out.println("Documentation generation completed.");
    }

    private static Map<String, String> loadConfig(String path) throws IOException {
        Yaml yaml = new Yaml();
        try (InputStream inputStream = new FileInputStream(path)) {
            return yaml.load(inputStream);
        }
    }

    private static Map<String, String> loadDoc(String path) throws IOException {
        List<String> lines = Files.readAllLines(Paths.get(path));
        Map<String, String> doc = new HashMap<>();
        for (String line : lines) {
            String[] parts = line.split(": ", 2);
            if (parts.length == 2) doc.put(parts[0], parts[1]);
        }
        return doc;
    }

    private static String simulateLLMResponse(String prompt) {
        return "Generated doc for: " + prompt;
    }
}
