package com.example.myapplication.activity;

import static com.example.myapplication.activity.MainActivity.TAG;
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

public class taskExecutor {
    Context context;
    int width;
    int height;
    int cols;
    int rows;

    String app;
    String task_desc;

    public taskExecutor(Context context,String app,String task_desc) {
        this.app=app;
        this.task_desc=task_desc;
        this.context = context;
    }


    public void startTaskExecutor() throws InterruptedException, IOException, JSONException {
        int user_input=1;
        //获取文件配置
        Map<String, String> configs = loadConfig(this.context, "config.json");
        Map<String, String> prompts = loadConfig(this.context,"prompts.json");

        //建立通信模型，这里很屎因为我还没把openAiModel给弄懂。
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
        File propmt_dir = new File(root_dir,"record2.txt");
        File app_dir = new File(new File(root_dir, "apps"), app);
        File work_dir = new File(root_dir,"tasks");
        if (!work_dir.exists()) {
            work_dir.mkdirs();
        }
        File auto_docs_dir = new File(app_dir,"auto_docs");
        File demo_docs_dir = new File(app_dir,"demo_docs");

        long taskTimestamp = System.currentTimeMillis() / 1000;
        Date date = new Date(taskTimestamp * 1000);  // 乘1000转换为毫秒级
        SimpleDateFormat dateFormat = new SimpleDateFormat("'task_"+app+"_'yyyy-MM-dd_HH-mm-ss");
        String dir_name = dateFormat.format(date);
        File task_dir = new File(work_dir, dir_name);
        task_dir.mkdirs();
        File log_path = new File(task_dir,"log_"+app+"_"+ dir_name +".txt");
        Boolean no_doc=false;

        File docs_dir=null;
        if(!auto_docs_dir.exists() && !demo_docs_dir.exists()){
            printUtils.printWithColor("No documentations found for the app "+app+",start to proceed with no docs.","red");
            no_doc=true;
        }else if(auto_docs_dir.exists() && demo_docs_dir.exists()){
            printUtils.printWithColor("The app "+app+" has documentations generated from both autonomous exploration and human demonstration,demonstration. Which one do you want to use? Type 1 or 2.\n1. Autonomous exploration\n2. Human Demonstration","blue");
            if(user_input==1){
                docs_dir = auto_docs_dir;
            }else{
                docs_dir = demo_docs_dir;
            }
        }else if(auto_docs_dir.exists()){
            printUtils.printWithColor("Documentations generated from autonomous exploration were found for the app "+app+". The doc base is selected automatically.","yellow");
            docs_dir =auto_docs_dir;
        }else{
            printUtils.printWithColor("Documentations generated from human demonstration were found for the app "+app+". The doc base is selected automatically.","yellow");
            docs_dir =demo_docs_dir;
        }
        androidController controller = new androidController(this.context);
        width = controller.get_device_size().x;
        height = controller.get_device_size().y;
        printUtils.printWithColor("Screen resolution of present device" + width + "x" + height, "yellow");
        printUtils.printWithColor("Please enter the description of the task you want me to complete in a few sentences:", "blue");

        int round_count=0;
        String last_act = "None";
        Boolean task_complete = false;
        Boolean grid_on = false;
        rows=0;
        cols=0;

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
            if(grid_on){
                Point rows_cols=controller.drawGrid(screenshot_path,task_dir.getAbsolutePath()+"/"+dir_name+"_"+round_count+"_grid.png");
                rows=rows_cols.x;
                cols=rows_cols.y;
                File image = new File(task_dir,dir_name+"_"+round_count+"_grid.png");
                prompt=prompts.get("task_template_grid");
            }else{
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
                prompt = null;
                if(no_doc){
                    String node_info = "The UI element information corresponding to the numeric tags displayed on the screenshot is as follows, you can only choose which UI element you want from them :\n";
                    node_info = node_info + convertAndroidElementToJson(elem_list);
                    node_info = node_info+"\n";
                    prompt = prompts.get("task_template").replace("<ui_document>",node_info);
                }else{
                    String ui_doc="";
                    for(int i=0;i<elem_list.size();i++){
                        androidElement elem = elem_list.get(i);
                        File doc_path=new File(docs_dir,elem.uid+".txt");
                        if(!doc_path.exists()){
                            continue;
                        }
                        ui_doc = ui_doc + "Documentation of UI element labeled with the numeric tag '"+i+1+"':\n";
                        Map<String, Object> doc_content = new ObjectMapper().readValue(doc_path, Map.class);
                        if(doc_content.containsKey("tap")){
                            ui_doc = ui_doc + "This UI element is clickable. "+doc_content.get("tap")+"\n\n";
                        }
                        if(doc_content.containsKey("text")){
                            ui_doc = ui_doc + "This UI element can receive text input. The text input is used for the following purposes: "+doc_content.get("text")+"\n\n";
                        }
                        if(doc_content.containsKey("long_press")){
                            ui_doc = ui_doc + "This UI element is long clickable. "+doc_content.get("tap")+"\n\n";
                        }
                        if(doc_content.containsKey("v_swipe")){
                            ui_doc = ui_doc + "This element can be swiped directly without tapping. You can swipe vertically on this UI element. "+doc_content.get("v_swipe")+"\n\n";
                        }
                        if(doc_content.containsKey("h_swipe")){
                            ui_doc = ui_doc + "This element can be swiped directly without tapping. You can swipe horizontally on this UI element. "+doc_content.get("h_swipe")+"\n\n";
                        }
                    }
                    printUtils.printWithColor("Documentations retrieved for the current interface:\n"+ui_doc,"yellow");
                    ui_doc= "You also have access to the following documentations that describes the functionalities of UI " +
                            "            elements you can interact on the screen. These docs are crucial for you to determine the target of your " +
                            "            next action. You should always prioritize these documented elements for interaction:"+ui_doc;
                    prompt = prompts.get("task_template").replace("<ui_document>",ui_doc);
                }
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
                    ArrayList<String> res = null;
                    if(grid_on){
                        res = responseParser.parseGridRsp(status_rsp.getValue());
                    }else{
                        res = responseParser.parseExploreRsp(status_rsp.getValue());
                    }
                    String act_name=res.get(0);
                    if(act_name.equals("FINISH")){
                        task_complete=true;
                        String think_chinese = res.get(res.size()-1);
                        TTSManager.getInstance(context).speak(think_chinese);
                        break;
                    }
                    if(act_name.equals("ERROR")){
                        break;
                    }

                    last_act = res.get(res.size()-1);
                    res.remove(res.size() - 1);
                    String think_chinese = res.get(res.size()-1);
                    TTSManager.getInstance(context).speak(think_chinese);

                    if(act_name.equals("tap")){
                        printUtils.printWithColor("开始点击！", "yellow");
                        int area = Integer.parseInt(res.get(1));
                        int[] bbox = elem_list.get(area - 1).bbox;

                        HighlightManager.showHighlight(myAccessibilityService.getInstance(), bbox);
                        Thread.sleep(2000);
                        HighlightManager.clearHighlight(myAccessibilityService.getInstance());

                        int x = (bbox[0] + bbox[2]) / 2;
                        int y = (bbox[1] + bbox[3]) / 2;
                        controller.tap(x, y);
                        Thread.sleep(2000);
                    }else if(act_name.equals("text")){
                        printUtils.printWithColor("开始输入文字！", "yellow");
                        String input_str = res.get(1);
                        controller.text(input_str);
                        Thread.sleep(2000);
                    }else if(act_name.equals("long_press")){
                        printUtils.printWithColor("开始长按！", "yellow");
                        int area = Integer.parseInt(res.get(1));
                        int[] bbox = elem_list.get(area - 1).bbox;
                        int x = (bbox[0] + bbox[2]) / 2;
                        int y = (bbox[1] + bbox[3]) / 2;
                        controller.longPress(x,y);
                        Thread.sleep(2000);
                    }else if(act_name.equals("swipe")){
                        printUtils.printWithColor("开始滑动！", "yellow");
                        int area = Integer.parseInt(res.get(1));
                        int[] bbox = elem_list.get(area - 1).bbox;
                        int x = (bbox[0] + bbox[2]) / 2;
                        int y = (bbox[1] + bbox[3]) / 2;
                        String swipe_dir = res.get(2);
                        String dist = res.get(3);
                        controller.swipe(x,y,swipe_dir,dist,false);
                        Thread.sleep(2000);
                    }else if(act_name.equals("grid")){
                        grid_on=true;
                    }else if(act_name.equals("tap_grid") || act_name.equals("long_press_grid")){
                        int area = Integer.parseInt(res.get(1));
                        String subarea = res.get(2);
                        Point x_y=areaToXY(area,subarea);
                        int x = x_y.x;
                        int y = x_y.y;
                        if(act_name.equals("tap_grid")){
                            controller.tap(x,y);
                            Thread.sleep(2000);
                        }else{
                            controller.longPress(x,y);
                            Thread.sleep(2000);
                        }
                    }else if(act_name.equals("swipe_grid")){
                        int start_area= Integer.parseInt(res.get(1));
                        String start_subarea = res.get(2);
                        int end_area = Integer.parseInt(res.get(3));
                        String end_subarea = res.get(4);
                        Point start_x_start_y = areaToXY(start_area,start_subarea);
                        int start_x=start_x_start_y.x;
                        int start_y= start_x_start_y.y;
                        Point end_x_end_y = areaToXY(end_area,end_subarea);
                        int end_x= end_x_end_y.x;
                        int end_y= end_x_end_y.y;
                        controller.swipePrecise(start_x,start_y,end_x,end_y,400);
                        Thread.sleep(2000);
                    }
                    if(!act_name.equals("grid")){
                        grid_on=false;
                    }
                    Thread.sleep(Integer.parseInt(configs.get("REQUEST_INTERVAL")));
                }else{
                    printUtils.printWithColor(status_rsp.getValue(),"red");
                    break;
                }
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

    private Point areaToXY(int area, String subarea){
        area -= 1;  // 调整索引
        int row = area / cols;
        int col = area % cols;

        int unitWidth = width / cols;
        int unitHeight = height / rows;

        int x_0 = col * unitWidth;
        int y_0 = row * unitHeight;

        int x, y;
        switch (subarea) {
            case "top-left":
                x = x_0 + unitWidth / 4;
                y = y_0 + unitHeight / 4;
                break;
            case "top":
                x = x_0 + unitWidth / 2;
                y = y_0 + unitHeight / 4;
                break;
            case "top-right":
                x = x_0 + unitWidth * 3 / 4;
                y = y_0 + unitHeight / 4;
                break;
            case "left":
                x = x_0 + unitWidth / 4;
                y = y_0 + unitHeight / 2;
                break;
            case "right":
                x = x_0 + unitWidth * 3 / 4;
                y = y_0 + unitHeight / 2;
                break;
            case "bottom-left":
                x = x_0 + unitWidth / 4;
                y = y_0 + unitHeight * 3 / 4;
                break;
            case "bottom":
                x = x_0 + unitWidth / 2;
                y = y_0 + unitHeight * 3 / 4;
                break;
            case "bottom-right":
                x = x_0 + unitWidth * 3 / 4;
                y = y_0 + unitHeight * 3 / 4;
                break;
            default: // center
                x = x_0 + unitWidth / 2;
                y = y_0 + unitHeight / 2;
                break;
        }
        return new Point(x, y);
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

}
