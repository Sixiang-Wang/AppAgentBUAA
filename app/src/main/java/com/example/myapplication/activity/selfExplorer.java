package com.example.myapplication.activity;

import static com.example.myapplication.scripts.config.loadConfig;

import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.provider.ContactsContract;
import android.util.Log;

import static com.example.myapplication.activity.MainActivity.TAG;

import com.example.myapplication.App;
import com.example.myapplication.scripts.androidController;
import com.example.myapplication.scripts.androidElement;
import com.example.myapplication.scripts.printUtils;
import com.example.myapplication.scripts.qwenModel;
import com.example.myapplication.scripts.responseParser;
import com.example.myapplication.util.TaskPool;
import com.google.gson.Gson;

import org.json.JSONException;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.CountDownLatch;

public class selfExplorer {
    Context context;

    public selfExplorer(Context context) {
        this.context = context;
    }

    public void startSelfExplorer() throws InterruptedException, IOException, JSONException {
        //以下几个变量是为了避免“在lambda表达式里不得使用变量”的问题，此问题导致我只能把startLearn里的变量提升为类变量。
        String last_act = "None";
        Boolean task_complete = false;
        int round_count = 0;

        //开发中测试用变量，将来必须让用户输入
        String app = "com.tencent.mm";
        String task_desc = "打开微信,给张喆宇发送你好,记得点进张喆宇聊天界面后要点击输入框再输入文字";
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
        //文件路径，该测试所用到的文件路径在这里定义。File定义下给出了一个例子方便理解
        Log.d(TAG, "MainActivity 创建完成");
        final File root_dir = new File(App.getApp().getExternalFilesDir(null).getParent());
        // /storage/emulated/0/Android/data/com.example.myapplication
        Log.d(TAG, this.context.getFilesDir().toString());//
        File work_dir = new File(root_dir, "apps");
        // /storage/emulated/0/Android/data/com.example.myapplication/apps
        if (!work_dir.exists()) {
            work_dir.mkdirs();
        }
        work_dir = new File(work_dir, app);
        // /storage/emulated/0/Android/data/com.example.myapplication/apps/com.zhihu.android
        if (!work_dir.exists()) {
            work_dir.mkdirs();
        }
        File demo_dir = new File(work_dir, "demos");
        // /storage/emulated/0/Android/data/com.example.myapplication/apps/com.zhihu.android/demos
        if (!demo_dir.exists()) {
            demo_dir.mkdirs();
        }
        long demoTimestamp = System.currentTimeMillis() / 1000;
        Date date = new Date(demoTimestamp * 1000);  // 乘1000转换为毫秒级
        SimpleDateFormat dateFormat = new SimpleDateFormat("'self_explore_'yyyy-MM-dd_HH-mm-ss");
        String taskName = dateFormat.format(date);
        File task_dir = new File(demo_dir, taskName);
        // /storage/emulated/0/Android/data/com.example.myapplication/apps/com.zhihu.android/demos/self_explore_'yyyy-MM-dd_HH-mm-ss
        task_dir.mkdirs();
        File docs_dir = new File(work_dir, "auto_docs");
        // /storage/emulated/0/Android/data/com.example.myapplication/apps/com.zhihu.android/auto_docs
        if (!docs_dir.exists()) {
            docs_dir.mkdirs();
        }
        File explore_log_path = new File(task_dir, "log_explore_" + taskName + ".txt");
        File reflect_log_path = new File(task_dir, "log_reflect_" + taskName + ".txt");

        //创建方法类
        androidController controller = new androidController(this.context);
        int width = controller.get_device_size().x;
        int height = controller.get_device_size().y;
        printUtils.printWithColor("Screen resolution of present device" + width + "x" + height, "yellow");
        printUtils.printWithColor("Please enter the description of the task you want me to complete in a few sentences:", "blue");
        int doc_count = 0;

        List<String> useless_list = new ArrayList<>();

        //回到桌面
        Intent intent = new Intent(Intent.ACTION_MAIN);
        intent.addCategory(Intent.CATEGORY_HOME);
        intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        this.context.startActivity(intent);
        Thread.sleep(1000);

        //开始循环和模型进行通信
        while (round_count < Integer.parseInt(Objects.requireNonNull(configs.get("MAX_ROUNDS")))) {
            round_count++;
            printUtils.printWithColor("Round " + round_count, "yellow");
            String screenshot_before = controller.get_screenshot(round_count + "_before.png", task_dir.getAbsolutePath());
            printUtils.printWithColor(screenshot_before, "yellow");

            Thread.sleep(1000);
            Boolean dark_mode = Boolean.valueOf(configs.get("DARK_MODE"));

            String xml_path = controller.get_xml(round_count, task_dir);
            Thread.sleep(1000);


            printUtils.printWithColor("XML 文件路径：" + xml_path, "yellow");
            List<androidElement> clickable_list = new ArrayList<>();
            List<androidElement> focusable_list = new ArrayList<>();

            //分析得到的xml文件，将clickable为true的AndroidElement（这是自己创建的类不是Android官方给的类）放进clickablelist里，focusable同理。
            controller.traverseTree(xml_path, clickable_list, "clickable", true);
            controller.traverseTree(xml_path, focusable_list, "focusable", true);

            //下面会把clickable_list和focusable_list里的AndroidElement放进elem_list里，优先放clickable为true的，然后放focusable为true并且不会靠已存在的AndroidElement太近的。
            List<androidElement> elem_list = new ArrayList<>();
            for (androidElement elem : clickable_list) {
                if (useless_list.contains(elem.uid)) {
                    continue;
                }
                elem_list.add(elem);
            }
            for (androidElement elem : focusable_list) {
                if (useless_list.contains(elem.uid)) {
                    continue;
                }
                int[] bbox = elem.bbox;
                int[] center = {(bbox[0] + bbox[2]) / 2, (bbox[1] + bbox[3]) / 2};
                boolean close = false;
                for (androidElement e : clickable_list) {
                    bbox = e.bbox;
                    int[] center_ = {(bbox[0] + bbox[2]) / 2, (bbox[1] + bbox[3]) / 2};
                    double dist = Math.sqrt(Math.pow(Math.abs(center[0] - center_[0]), 2) +
                            Math.pow(Math.abs(center[1] - center_[1]), 2));
                    if (dist <= Double.parseDouble(configs.get("MIN_DIST"))) {
                        close = true;
                        break;
                    }
                }
                if (!close) {
                    elem_list.add(elem);
                }
            }


            String  base64_img_before = task_dir.getAbsolutePath() + "/" + round_count + "_before_labeled.png";
            //将之前截的图，根据elem_list来绘制，得到一个标记了可点击点的屏幕截图
            controller.drawBoundingBoxes(screenshot_before, base64_img_before, elem_list, false, dark_mode);

            //将标记后的屏幕截图连通一个prompt发给Qwen模型，得到它的回复。
            Map<String, String> prompts = loadConfig(this.context, "prompts.json");
            String prompt = prompts.get("self_explore_task_template").replace("<task_description>", task_desc);
            prompt = prompt.replace("<last_act>", last_act);
            printUtils.printWithColor("Thinking about what to do in the next step...", "yellow");
            Map.Entry<Boolean, String> status_rsp = model.getModelResponse(prompt, Collections.singletonList(base64_img_before));

            //为了照顾python的变量随便用而不得不提取出来赋值
            String act_name="tap";
            String swipe_dir = "up";
            int area=1;

            if (status_rsp.getKey()) {
                //把此次回复的信息存进json文件里。
                FileWriter logfile = new FileWriter(explore_log_path, true); // 以追加模式打开文件
                JSONObject logItem = new JSONObject();
                logItem.put("step", round_count);
                logItem.put("prompt", prompt);
                logItem.put("image", round_count + "_before_labeled.png");
                logItem.put("response", status_rsp.getValue());
                logfile.write(logItem.toString() + "\n"); // 写入JSON字符串并加上换行符
                logfile.close(); // 关闭文件
                //分析大模型的回复，得到基本信息
                ArrayList<String> res = responseParser.parseExploreRsp(status_rsp.getValue());
                act_name = res.get(0);
                last_act = res.get(res.size() - 1);
                res.remove(res.size() - 1);
                printUtils.printWithColor("开始分析回复得到信息", "yellow");
                if (act_name.equals("FINISH")) {
                    task_complete = true;
                }
                else if (act_name.equals("tap")) {
                    printUtils.printWithColor("开始点击！", "yellow");
                    area = Integer.parseInt(res.get(1));
                    int[] bbox = elem_list.get(area - 1).bbox;
                    int x = (bbox[0] + bbox[2]) / 2;
                    int y = (bbox[1] + bbox[3]) / 2;
                    controller.tap(x, y);

                }
                else if (act_name.equals("text")) {
                    printUtils.printWithColor("开始输入文字！", "yellow");
                    String input_str = res.get(1);
                    controller.text(input_str);


                }
                else if(act_name.equals("long_press")){
                    printUtils.printWithColor("开始长按！", "yellow");
                    area = Integer.parseInt(res.get(1));
                    int[] bbox = elem_list.get(area - 1).bbox;
                    int x = (bbox[0] + bbox[2]) / 2;
                    int y = (bbox[1] + bbox[3]) / 2;
                    controller.longPress(x,y);

                }
                else if(act_name.equals("swipe")){
                    printUtils.printWithColor("开始滑动！", "yellow");
                    area = Integer.parseInt(res.get(1));
                    int[] bbox = elem_list.get(area - 1).bbox;
                    int x = (bbox[0] + bbox[2]) / 2;
                    int y = (bbox[1] + bbox[3]) / 2;
                    swipe_dir = res.get(2);
                    String dist = res.get(3);
                    controller.swipe(x,y,swipe_dir,dist,false);
                }else{
                    break;
                }
                Thread.sleep(Integer.parseInt(configs.get("REQUEST_INTERVAL")));
            } else {
                printUtils.printWithColor(status_rsp.getValue(), "red");
                break;
            }
            String screenshot_after = controller.get_screenshot(round_count + "_after.png",task_dir.getAbsolutePath());
            printUtils.printWithColor(screenshot_after, "yellow");

            String base64_img_after = task_dir.getAbsolutePath() + "/" + round_count + "_after_labeled.png";
            controller.drawBoundingBoxes(screenshot_after,base64_img_after,elem_list,false,dark_mode);

            if(act_name.equals("tap")){
                prompt = prompts.get("self_explore_reflect_template").replace("<action>", "tapping");
            }else if(act_name.equals("text")){
                continue;
            }else if(act_name.equals("long_press")){
                prompt = prompts.get("self_explore_reflect_template").replace("<action>", "long pressing");
            }else if(act_name.equals("swipe")){
                if(swipe_dir.equals("up") || swipe_dir.equals("down")){
                    act_name="v_swipe";
                }else if(swipe_dir.equals("left") || swipe_dir.equals("right")){
                    act_name="h_swipe";
                }
                prompt=prompts.get("self_explore_reflect_template").replace("<action>", "swiping");
            }else{
                printUtils.printWithColor("ERROR: Undefined act!","red");
                break;
            }
            prompt = prompt.replace("<ui_element>",String.valueOf(area));
            prompt = prompt.replace("<task_desc>",task_desc);
            prompt = prompt.replace("<last_act>",last_act);

            printUtils.printWithColor("Reflecting on my previous action...","yellow");
            status_rsp = model.getModelResponse(prompt, Arrays.asList(base64_img_before, base64_img_after));

            if (status_rsp.getKey()) {
                String resource_id = elem_list.get(area-1).uid;
                //把此次回复的信息存进json文件里。
                FileWriter logfile = new FileWriter(reflect_log_path, true); // 以追加模式打开文件
                JSONObject logItem = new JSONObject();
                logItem.put("step", round_count);
                logItem.put("prompt", prompt);
                logItem.put("image_before", round_count + "_before_labeled.png");
                logItem.put("image_after", round_count + "_after_labeled.png");
                logItem.put("response", status_rsp.getValue());
                logfile.write(logItem.toString() + "\n"); // 写入JSON字符串并加上换行符
                logfile.close(); // 关闭文件
                ArrayList<String> res = responseParser.parseReflectRsp(status_rsp.getValue());
                String decision = res.get(0);
                if(decision.equals("ERROR")){
                    break;
                }
                if(decision.equals("INEFFECTIVE")){
                    useless_list.add(resource_id);
                    last_act="None";
                } else if(decision.equals("BACK") || decision.equals("CONTINUE")||decision.equals("SUCCESS")) {

                    if(decision.equals("BACK") || decision.equals("CONTINUE")){
                        useless_list.add(resource_id);
                        last_act="None";
                        if(decision.equals("BACK")){
                            controller.back();
                        }
                    }

                    String doc = res.get(res.size()-1);
                    String doc_name = resource_id+".txt";
                    File doc_path = new File(docs_dir,doc_name);
                    Map<String,String> doc_content = null;
                    if(doc_path.exists()){
                        try{
                            // 读取文件内容
                            BufferedReader reader = new BufferedReader(new FileReader(doc_path));
                            StringBuilder fileContent = new StringBuilder();
                            String line;
                            while ((line = reader.readLine()) != null) {
                                fileContent.append(line);
                            }
                            reader.close();
                            // 使用 Gson 或类似的库解析内容为 Map
                            Gson gson = new Gson();
                            doc_content = gson.fromJson(fileContent.toString(), Map.class);
                            // 检查 doc_content 中是否包含 act_name 并且值不为空、非假
                            if (doc_content.containsKey(act_name) && doc_content.get(act_name) != null && !doc_content.get(act_name).toString().isEmpty()) {
                                System.out.println("Documentation for the element " + resource_id + " already exists.");
                                continue;  // 跳过当前文档的处理
                            }
                        } catch (IOException e) {
                            e.printStackTrace();
                        }
                    } else{
                        // 如果文件不存在，创建新的文档内容
                        doc_content = new HashMap<>();
                        doc_content.put("tap", "");
                        doc_content.put("text", "");
                        doc_content.put("v_swipe", "");
                        doc_content.put("h_swipe", "");
                        doc_content.put("long_press", "");
                    }
                    doc_content.put(act_name, doc);
                    try {
                        // 将更新后的文档内容写回文件
                        FileWriter writer = new FileWriter(doc_path);
                        Gson gson = new Gson();
                        String docContentJson = gson.toJson(doc_content);
                        writer.write(docContentJson);
                        writer.close();
                    } catch (IOException e) {
                        e.printStackTrace();
                    }
                    doc_count++;  // 增加文档计数
                    printUtils.printWithColor("Documentation generated and saved to " + doc_path.getAbsolutePath(),"yellow");
                }else{
                    printUtils.printWithColor("ERROR: Undefined decision! +"+decision,"red");
                    break;
                }
            }else{
                printUtils.printWithColor(status_rsp.getValue(),"red");
                break;
            }
            Thread.sleep(Integer.parseInt(configs.get("REQUEST_INTERVAL")));
        }
        if(task_complete){
            printUtils.printWithColor("Autonomous exploration completed successfully. "+doc_count+" docs generated.","yellow");
        }else if(round_count == Integer.parseInt(Objects.requireNonNull(configs.get("MAX_ROUNDS")))){
            printUtils.printWithColor("Autonomous exploration finished due to reaching max rounds. "+doc_count+" docs generated.","yellow");
        }else{
            printUtils.printWithColor("Autonomous exploration finished unexpectedly. "+doc_count+" docs generated.","red");
        }
    }
}
