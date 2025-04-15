package com.example.myapplication.activity;

import static com.example.myapplication.activity.MainActivity.TAG;
import static com.example.myapplication.scripts.config.loadConfig;

import android.content.Context;
import android.util.Log;

import com.example.myapplication.App;
import com.example.myapplication.scripts.printUtils;
import com.example.myapplication.scripts.qwenModel;
import com.google.gson.Gson;

import org.json.JSONException;
import org.json.JSONObject;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.io.*;
import java.nio.file.*;
import java.util.*;
import java.util.regex.*;
import java.util.stream.Collectors;

public class DocGen {
    Context context;

    public DocGen(Context context){
        this.context=context;
    }

    public  void startDocGen() throws InterruptedException, IOException, JSONException {

        //开发中测试用变量，将来必须让用户输入
        String app = "com.zhihu.android";
        String demoName = "demo";
        String task_desc = "打开知乎点击故事";
        //获取文件配置
        Map<String, String> configs = loadConfig(this.context, "config.json");

        //建立通信模型，这里很屎因为我还没把openAiModel给弄懂。
        qwenModel model = new qwenModel(
                configs.get("DASHSCOPE_API_KEY"),
                configs.get("QWEN_MODEL")
        );
        if (configs.get("MODEL").equals("OpenAI")) {
            // 这个还没修改，暂时不用
            Log.d(TAG, "openAI还没修改完");
        } else if (configs.get("MODEL").equals("Qwen")) {
            model = new qwenModel(
                    configs.get("DASHSCOPE_API_KEY"),
                    configs.get("QWEN_MODEL")
            );
        } else {
            printUtils.printWithColor("ERROR: Unsupported model type" + configs.get("MODEL"), "red");
            return;
        }


        Log.d(TAG, "MainActivity 创建完成");
        final File rootDir = new File(App.getApp().getExternalFilesDir(null).getParent());
        // /storage/emulated/0/Android/data/com.example.myapplication
        Log.d(TAG,this.context.getFilesDir().toString());//
        File workDir = new File(rootDir,"apps");
        // /storage/emulated/0/Android/data/com.example.myapplication/apps
        if (!workDir.exists()) {
            workDir.mkdirs();
        }
        File demoDir = new File(workDir,"demos");



        File taskDir = new File(demoDir, demoName);
        File xmlDir = new File(taskDir, "xml");
        File labeledSsDir = new File(taskDir, "labeled_screenshots");
        File recordPath = new File(taskDir, "record.txt");
        File taskDescPath = new File(taskDir, "task_desc.txt");


        if (!taskDir.exists() || !xmlDir.exists() || !labeledSsDir.exists() ||
                !recordPath.exists() || !taskDescPath.exists()) {
            throw new RuntimeException("fuck everyone!in DocGen line77");
        }

        File logPath = new File(taskDir, "log_" + app + "_" + demoName + ".txt");

        File docsDir = new File(workDir, "demo_docs");
        if (!docsDir.exists()) {
            docsDir.mkdir();
        }

        System.out.println("Starting to generate documentations for the app " + app + " based on the demo " + demoName);
        int docCount = 0;

        List<String> lines = new ArrayList<>();
        int step = 0;

        try (BufferedReader infile = new BufferedReader(new FileReader(recordPath))) {
            lines = infile.lines().collect(Collectors.toList());
            step = lines.size() - 1;
        } catch (IOException e) {
            e.printStackTrace();
        }

        for (int i = 1; i <= step; i++) {
            String imgBefore = labeledSsDir + "/" + demoName + "_" + i + ".png";
            String imgAfter = labeledSsDir + "/" + demoName + "_" + (i + 1) + ".png";
            String rec = lines.get(i - 1).trim();

            String[] parts = rec.split(":::");
            String action = parts[0];
            String resourceId = parts[1];

            String actionType = action.split("\\(")[0];
            Matcher matcher = Pattern.compile("\\((.*?)\\)").matcher(action);
            String actionParam = "";

            if (matcher.find()) {
                actionParam = matcher.group(1);
            }

            Map<String,String> prompts = loadConfig(this.context,"prompts.json");
            String promptTemplate = "";
            String prompt = "";

            if (actionType.equals("tap")) {
                promptTemplate = prompts.get("tap_doc_template");
                prompt = promptTemplate.replace("<ui_element>", actionParam);
            } else if (actionType.equals("text")) {
                String[] inputParts = actionParam.split(":sep:");
                String inputArea = inputParts[0];
                promptTemplate = prompts.get("text_doc_template");
                prompt = promptTemplate.replace("<ui_element>", inputArea);
            } else if (actionType.equals("long_press")) {
                promptTemplate = prompts.get("long_press_doc_template");
                prompt = promptTemplate.replace("<ui_element>", actionParam);
            } else if (actionType.equals("swipe")) {
                String[] swipeParts = actionParam.split(":sep:");
                String swipeArea = swipeParts[0];
                String swipeDir = swipeParts[1];

                if (swipeDir.equals("up") || swipeDir.equals("down")) {
                    actionType = "v_swipe";
                } else if (swipeDir.equals("left") || swipeDir.equals("right")) {
                    actionType = "h_swipe";
                }

                promptTemplate = prompts.get("swipe_doc_template");
                prompt = promptTemplate.replace("<swipe_dir>", swipeDir).replace("<ui_element>", swipeArea);
            } else {
                break;
            }

            // 读取任务描述
            String taskDesc = new String(Files.readAllBytes(taskDescPath.toPath()), StandardCharsets.UTF_8);
            prompt = prompt.replace("<task_desc>", taskDesc);

            // 生成文档名称和路径
            String docName = resourceId + ".txt";
            File docPath = new File(docsDir, docName);

            File docFile = docPath;
            Map<String, String> docContent = new HashMap<>();

            if (docFile.exists()) {
                try {
                    String docContentStr = new String(Files.readAllBytes(docFile.toPath()), StandardCharsets.UTF_8);
                    Gson gson = new Gson();
                    docContent = gson.fromJson(docContentStr, Map.class);

                    if (docContent.containsKey(actionType) && !docContent.get(actionType).isEmpty()) {
                        if (configs.containsKey("DOC_REFINE") && Boolean.parseBoolean(configs.get("DOC_REFINE"))) {
                            String suffix = prompts.get("refine_doc_suffix").replace("<old_doc>", docContent.get(actionType));
                            prompt += suffix;
                            printUtils.printWithColor("Documentation for the element " + resourceId + " already exists. The doc will be "
                                    + "refined based on the latest demo.", "yellow");
                        } else {
                            printUtils.printWithColor("Documentation for the element " + resourceId + " already exists. Turn on DOC_REFINE "
                                    + "in the config file if needed.", "yellow");
                            continue;
                        }
                    }
                } catch (IOException e) {
                    e.printStackTrace();
                }
            } else {
                docContent.put("tap", "");
                docContent.put("text", "");
                docContent.put("v_swipe", "");
                docContent.put("h_swipe", "");
                docContent.put("long_press", "");
            }

            printUtils.printWithColor("Waiting for GPT-4V to generate documentation for the element " + resourceId, "yellow");
            Map.Entry<Boolean, String> statusRsp = model.getModelResponse(prompt, Arrays.asList(imgBefore, imgAfter));



            if (statusRsp.getKey()) {
                // 更新文档内容
                docContent.put(actionType, statusRsp.getValue());

                // 写入日志文件
                try (FileWriter logFile = new FileWriter(logPath, true)) {
                    Map<String, Object> logItem = new HashMap<>();
                    logItem.put("step", i);
                    logItem.put("prompt", prompt);
                    logItem.put("image_before", demoName + "_" + i + ".png");
                    logItem.put("image_after", demoName + "_" + (i + 1) + ".png");
                    logItem.put("response", statusRsp.getValue());

                    logFile.write(new JSONObject(logItem).toString() + "\n");
                } catch (IOException e) {
                    e.printStackTrace();
                }

                // 写入或更新文档文件
                try {
                    if (docPath.exists()) {
                        // 如果文件已经存在，则读取内容并更新
                        String docContentStr = new String(Files.readAllBytes(docPath.toPath()));
                        JSONObject docJson = new JSONObject(docContentStr);

                        // 更新文档内容
                        docJson.put(actionType, statusRsp.getValue());

                        try (FileWriter docFileTemp = new FileWriter(docPath)) {
                            docFileTemp.write(docJson.toString());
                        }
                    } else {
                        // 如果文件不存在，则创建新文档
                        JSONObject docJson = new JSONObject(docContent);
                        try (FileWriter docFileTemp = new FileWriter(docPath)) {
                            docFileTemp.write(docJson.toString());
                        }
                    }
                } catch (IOException e) {
                    e.printStackTrace();
                }

                docCount++;
                printUtils.printWithColor("Documentation generated and saved to " + docPath, "yellow");
            } else {
                printUtils.printWithColor(statusRsp.getValue(), "red");
            }


            // 延迟下一次请求
            Thread.sleep(Integer.parseInt(Objects.requireNonNull(configs.get("REQUEST_INTERVAL"))));


        }
        printUtils.printWithColor("Documentation generation phase completed. "+docCount+" docs generated.", "yellow");

    }


}
