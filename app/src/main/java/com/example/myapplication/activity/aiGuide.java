package com.example.myapplication.activity;

import static com.example.myapplication.activity.MainActivity.TAG;
import static com.example.myapplication.activity.taskExecutor.convertAndroidElementToJson;
import static com.example.myapplication.scripts.androidController.appendToFile;
import static com.example.myapplication.scripts.config.loadConfig;

import android.content.Context;
import android.content.Intent;
import android.graphics.Point;
import android.util.Log;

import com.example.myapplication.App;
import com.example.myapplication.myAccessibilityService;
import com.example.myapplication.scripts.HighlightManager;
import com.example.myapplication.scripts.TTSManager;
import com.example.myapplication.scripts.androidController;
import com.example.myapplication.scripts.androidElement;
import com.example.myapplication.scripts.baseModel;
import com.example.myapplication.scripts.printUtils;
import com.example.myapplication.scripts.qwenModel;
import com.example.myapplication.scripts.responseParser;
import com.fasterxml.jackson.databind.ObjectMapper;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.CountDownLatch;

public class aiGuide {
    Context context;
    int width;
    int height;
    String app;
    String task_desc;

    private CountDownLatch latch;

    //这个东西的目标是，不去做具体的实施，而是每次只给建议（语音+红标）
    public aiGuide(Context context,String app,String task_desc) {
        this.app=app;
        this.task_desc=task_desc;
        this.context = context;
    }

    public void startAiGuide() throws Exception {
        //获取文件配置
        Map<String, String> configs = loadConfig(this.context, "config.json");
        Map<String, String> prompts = loadConfig(this.context,"prompts.json");

        //建立通信模型
        baseModel model = null;
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
        final File root_dir = new File(App.getApp().getExternalFilesDir(null).getParent());
        // /storage/emulated/0/Android/data/com.example.myapplication
        Log.d(TAG, this.context.getFilesDir().toString());//
        File propmt_dir = new File(root_dir,"record3.txt");
        File app_dir = new File(new File(root_dir, "apps"), app);
        File work_dir = new File(root_dir,"tasks");
        if (!work_dir.exists()) {
            work_dir.mkdirs();
        }

        long taskTimestamp = System.currentTimeMillis() / 1000;
        Date date = new Date(taskTimestamp * 1000);  // 乘1000转换为毫秒级
        SimpleDateFormat dateFormat = new SimpleDateFormat("'task_"+app+"_'yyyy-MM-dd_HH-mm-ss");
        String dir_name = dateFormat.format(date);
        File task_dir = new File(work_dir, dir_name);
        task_dir.mkdirs();
        File log_path = new File(task_dir,"log_"+app+"_"+ dir_name +".txt");

        androidController controller = new androidController(this.context);
        width = controller.get_device_size().x;
        height = controller.get_device_size().y;
        printUtils.printWithColor("Screen resolution of present device" + width + "x" + height, "yellow");
        printUtils.printWithColor("Please enter the description of the task you want me to complete in a few sentences:", "blue");

        int round_count=0;
        String last_act = "None";
        Boolean task_complete = false;

        //回到桌面
        Intent intent = new Intent(Intent.ACTION_MAIN);
        intent.addCategory(Intent.CATEGORY_HOME);
        intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        this.context.startActivity(intent);
        TTSManager.getInstance(context).speak("任务执行开始！");
        Thread.sleep(1000);

        String prompt= prompts.get("keyword_json_template").replace("<task_description>",task_desc);
        Map.Entry<Boolean, String> status_rsp_key = model.getModelResponse_keyword_2(prompt);
        if(status_rsp_key.getKey()){
            String json=status_rsp_key.getValue().replace("```json","").replace("```","");
            printUtils.printWithColor("json如下：\n"+json,"blue");
            JSONObject jsonObject = new JSONObject(json);
            myAccessibilityService.getInstance().jsonObject=jsonObject;
        }else{
            printUtils.printWithColor("获取关键词json出错！","red");
            return ;
        }

        while (round_count < Integer.parseInt(Objects.requireNonNull(configs.get("MAX_ROUNDS")))) {
            Thread.sleep(2000);
            round_count+=1;
            printUtils.printWithColor("Round " + round_count, "yellow");

            String screenshot_path = controller.get_screenshot(dir_name+"_"+round_count+".png", task_dir.getAbsolutePath());
            printUtils.printWithColor(screenshot_path, "yellow");
            Thread.sleep(2000);

            String xml_path = controller.get_xml_2(dir_name+"_"+round_count, task_dir);
            Thread.sleep(2000);
            //现在已经形成了xml文件，并且已经被打分了。

            List<androidElement> clickable_list = new ArrayList<>();
            List<androidElement> focusable_list = new ArrayList<>();
            //分析得到的xml文件，将clickable为true的AndroidElement（这是自己创建的类不是Android官方给的类）放进clickablelist里，focusable同理。

            controller.traverseTree(xml_path, clickable_list, "clickable", true);
            controller.traverseTree(xml_path, focusable_list, "focusable", true);

            List<androidElement> elem_list = new ArrayList<>();
            elem_list.addAll(clickable_list);
            for (androidElement elem : focusable_list) {
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

            int remain_num=15;
            keepTopNElements(elem_list,remain_num);
            controller.drawBoundingBoxes(screenshot_path,task_dir.getAbsolutePath()+"/"+dir_name+"_"+round_count+"_labeled.png",elem_list,false,Boolean.valueOf(configs.get("DARK_MODE")));;
            Thread.sleep(1000);
            File image = new File(task_dir,dir_name+"_"+round_count+"_labeled.png");

            String node_info = "当前屏幕的控件信息如下，你只能从它们中选择你要操作的UI元素：\n";
            node_info = node_info + convertAndroidElementToJson(elem_list);
            node_info = node_info+"\n";
            prompt = prompts.get("aiGuide_template").replace("<ui_document>",node_info);
            prompt = prompt.replace("<task_description>",task_desc);
            prompt = prompt.replace("<last_act>",last_act);

            appendToFile(propmt_dir,prompt);

            printUtils.printWithColor("Thinking about what to do in the next step...","yellow");

            Map.Entry<Boolean, String> status_rsp = model.getModelResponse(prompt, Collections.singletonList(image.getAbsolutePath()));

            if(status_rsp.getKey()){
                //把此次回复的信息存进json文件里。
                FileWriter logfile = new FileWriter(log_path, true); // 以追加模式打开文件
                JSONObject logItem = new JSONObject();
                logItem.put("step", round_count);
                logItem.put("prompt", prompt);
                logItem.put("image", dir_name+"_"+round_count + "_labeled.png");
                logItem.put("response", status_rsp.getValue());
                logfile.write(logItem + "\n"); // 写入JSON字符串并加上换行符
                logfile.close(); // 关闭文件
                ArrayList<String> res;
                res = responseParser.parseExploreRsp_Chinese(status_rsp.getValue());
                String act_name=res.get(0);
                if(act_name.equals("FINISH")){
                    task_complete=true;
                    String result = res.get(res.size()-1);
                    TTSManager.getInstance(context).speak(result);
                    TTSManager.getInstance(context).speak("任务已经结束！");
                    break;
                }
                if(act_name.equals("ERROR")){
                    break;
                }

                last_act = res.get(res.size()-1);
                res.remove(res.size() - 1);
                if(act_name.equals("tap")){
                    int area = Integer.parseInt(res.get(1));
                    int[] bbox = elem_list.get(area - 1).bbox;
                    String observation = res.get(2);
                    String doc = "红框框住"+res.get(3).substring(5);

                    TTSManager.getInstance(context).speak(observation);
                    TTSManager.getInstance(context).speak(doc);

                    HighlightManager.showHighlight(myAccessibilityService.getInstance(), bbox);
                    Thread.sleep(TTSManager.getInstance(context).estimateDurationByChars(observation+doc));
                    printUtils.printWithColor("sleep结束！","red");
                    HighlightManager.allowTouchThrough(myAccessibilityService.getInstance());

                    HighlightManager.setOnHighlightClickListener(() -> {
                        Log.d("Highlight", "用户点击了高亮控件");
                        notifyUserClicked(); // 调用你封装的通知方法，唤醒等待的逻辑
                    });
                    TTSManager.getInstance(context).speak("您现在应该点击被红框框住的元素");
                    waitForUserClick();
                    HighlightManager.clearHighlight(myAccessibilityService.getInstance());
                    Thread.sleep(500);

                    int x = (bbox[0] + bbox[2]) / 2;
                    int y = (bbox[1] + bbox[3]) / 2;
                    controller.tap(x,y);

                    TTSManager.getInstance(context).speak("您已经成功点击了被红框框住的元素！接下来我们继续分析吧！");


                }else if(act_name.equals("text")){
                    printUtils.printWithColor("开始输入文字！", "yellow");
                    String input_str = res.get(1);
                    String observation = res.get(2);
                    String doc = "红框框住"+res.get(3).substring(5);
                    TTSManager.getInstance(context).speak(observation);
                    TTSManager.getInstance(context).speak(doc);
                    TTSManager.getInstance(context).speak("我帮您输入了"+input_str);
                    controller.text(input_str);
                    Thread.sleep(1500);
                    TTSManager.getInstance(context).speak("我帮您成功输入了文字，接下来我们继续分析吧！");

                }else if(act_name.equals("long_press")){
                    printUtils.printWithColor("开始长按！", "yellow");
                    int area = Integer.parseInt(res.get(1));
                    int[] bbox = elem_list.get(area - 1).bbox;
                    String observation = res.get(2);
                    String doc = "红框框住"+res.get(3).substring(5);
                    TTSManager.getInstance(context).speak(observation);
                    TTSManager.getInstance(context).speak(doc);

                    HighlightManager.showHighlight(myAccessibilityService.getInstance(), bbox);
                    Thread.sleep(TTSManager.getInstance(context).estimateDurationByChars(observation+doc));
                    printUtils.printWithColor("sleep结束！","red");
                    HighlightManager.allowTouchThrough(myAccessibilityService.getInstance());

                    HighlightManager.setOnHighlightClickListener(() -> {
                        Log.d("Highlight", "用户长按了高亮控件");
                        notifyUserClicked(); // 调用你封装的通知方法，唤醒等待的逻辑
                    });
                    TTSManager.getInstance(context).speak("您现在应该长按被红框框住的元素");
                    waitForUserClick();
                    HighlightManager.clearHighlight(myAccessibilityService.getInstance());
                    Thread.sleep(1500);

                    int x = (bbox[0] + bbox[2]) / 2;
                    int y = (bbox[1] + bbox[3]) / 2;
                    controller.longPress(x,y);

                    TTSManager.getInstance(context).speak("您已经成功长按了被红框框住的元素！接下来我们继续分析吧！");


                } else if(act_name.equals("swipe")){
                    printUtils.printWithColor("开始滑动！", "yellow");
                    int area = Integer.parseInt(res.get(1));
                    int[] bbox = elem_list.get(area - 1).bbox;
                    int x = (bbox[0] + bbox[2]) / 2;
                    int y = (bbox[1] + bbox[3]) / 2;
                    String swipe_dir = res.get(2);
                    String dist = res.get(3);
                    String observation = res.get(5);
                    String doc = "红框框住"+res.get(6).substring(5);

                    TTSManager.getInstance(context).speak(observation);
                    TTSManager.getInstance(context).speak(doc);

                    HighlightManager.showHighlight(myAccessibilityService.getInstance(), bbox);
                    Thread.sleep(TTSManager.getInstance(context).estimateDurationByChars(observation+doc));

                    printUtils.printWithColor("sleep结束！","red");
                    HighlightManager.allowTouchThrough(myAccessibilityService.getInstance());

                    HighlightManager.setOnHighlightClickListener(() -> {
                        Log.d("Highlight", "用户滑动了高亮控件");
                        notifyUserClicked(); // 调用你封装的通知方法，唤醒等待的逻辑
                    });

                    TTSManager.getInstance(context).speak("您现在应该以"+dist+"距离"+"向"+swipe_dir+"滑动被红框框住的元素");

                    waitForUserClick();
                    HighlightManager.clearHighlight(myAccessibilityService.getInstance());
                    Thread.sleep(1500);


                    controller.swipe(x,y,swipe_dir,dist,false);

                    TTSManager.getInstance(context).speak("您已经成功滑动了被红框框住的元素！接下来我们继续分析吧！");

                }
                Thread.sleep(Integer.parseInt(configs.get("REQUEST_INTERVAL")));
            }else{
                printUtils.printWithColor(status_rsp.getValue(),"red");
                break;
            }


        }

        if(task_complete){
            printUtils.printWithColor("Task completed successfully","yellow");
        }else if(round_count == Integer.parseInt(Objects.requireNonNull(configs.get("MAX_ROUNDS")))){
            printUtils.printWithColor("Task finished due to reaching max rounds","yellow");
        }else{
            printUtils.printWithColor("Task finished unexpectedly","red");
        }

    }

    public void keepTopNElements(List<androidElement> elemList, int n) {
        // 先按 score 排序
        sortByScore(elemList);

        // 检查是否有足够的元素，避免越界
        if (n < elemList.size()) {
            elemList.subList(n, elemList.size()).clear(); // 删除从第n个元素开始的所有元素
        }

    }

    public void sortByScore(List<androidElement> elemList) {
        Collections.sort(elemList, new Comparator<androidElement>() {
            @Override
            public int compare(androidElement e1, androidElement e2) {
                // 将较大的 score 排在前面，使用 float 的 compareTo 方法
                return Float.compare(e2.score, e1.score);
            }
        });
    }

    public static String convertAndroidElementToJson(List<androidElement> elem_list) throws JSONException {
        JSONArray jsonArray = new JSONArray();  // 用于存放所有元素的 JSON 对象
        // 遍历 elem_list，将每个 androidElement 转换为 JSON 对象
        int index = 1;  // 从 1 开始计数
        for (androidElement elem : elem_list) {
            JSONObject jsonObject = new JSONObject();

            // 将 index 和 score 添加到 JSON 对象中
            jsonObject.put("index", index);
            jsonObject.put("score", elem.score);

            // 提取 attributes 中的 "text", "class" 和 "content-desc"
            JSONObject filteredAttributes = new JSONObject();
            if (elem.attributes != null) {
                // 为每个需要的键提供值，即使为空
                filteredAttributes.put("text", elem.attributes.getOrDefault("text", ""));
                filteredAttributes.put("class", elem.attributes.getOrDefault("class", ""));
                filteredAttributes.put("content-desc", elem.attributes.getOrDefault("content-desc", ""));
            }
            // 将过滤后的 attributes 添加到 JSON 对象中
            jsonObject.put("attributes", filteredAttributes);
            // 将该元素的 JSON 对象加入 JSON 数组
            jsonArray.put(jsonObject);
            // 增加 index
            index++;
        }
        // 返回最终的 JSON 字符串
        return jsonArray.toString(4);
    }

    public void waitForUserClick() {
        latch = new CountDownLatch(1);
        Log.d("aiGuide", "等待用户点击控件...");
        try {
            latch.await(); // 阻塞当前线程，直到 countDown()
        } catch (InterruptedException e) {
            e.printStackTrace();
        }
        Log.d("aiGuide", "用户点击完成，继续执行");
    }


    public void notifyUserClicked() {
        if (latch != null) {
            latch.countDown();
        }
    }


}

