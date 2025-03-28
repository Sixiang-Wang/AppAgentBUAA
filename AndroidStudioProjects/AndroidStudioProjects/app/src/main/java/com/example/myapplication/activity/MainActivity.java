package com.example.myapplication.activity;

import static com.example.myapplication.scripts.config.loadConfig;

import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.provider.Settings;
import android.util.Log;

import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;

import com.example.myapplication.App;
import com.example.myapplication.R;
import com.example.myapplication.constant.ServiceType;
import com.example.myapplication.databinding.ActivityMainBinding;
import com.example.myapplication.scripts.androidController;
import com.example.myapplication.scripts.androidElement;
import com.example.myapplication.scripts.qwenModel;
import com.example.myapplication.scripts.responseParser;
import com.example.myapplication.scripts.printUtils;
import com.example.myapplication.services.MediaProjectionService;
import com.example.myapplication.util.MediaProjectionHelper;
import com.example.myapplication.util.NotificationHelper;
import com.example.myapplication.util.WindowHelper;

import org.json.JSONException;
import org.json.JSONObject;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.Objects;

public class MainActivity extends AppCompatActivity {
    private ActivityMainBinding binding;
    public static String TAG = "MainActivity";
    private static final String ACCESSIBILITY_SERVICE_ID = "com.example.myapplication/.MyAccessibilityService";

    //以下几个变量是为了避免“在lambda表达式里不得使用变量”的问题，此问题导致我只能把startLearn里的变量提升为类变量。
    String last_act = "None";
    Boolean task_complete = false;
    int round_count = 0;


    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        binding = ActivityMainBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());
        NotificationHelper.check(this);
        initView();
    }

    private void initView() {
        binding.btnStart.setOnClickListener(v -> {
            MediaProjectionHelper.start(this);
        });
        binding.btnStop.setOnClickListener(v -> {
            MediaProjectionHelper.stop();
        });
        binding.btnShowScreenshot.setOnClickListener(v -> {
            if (WindowHelper.checkOverlay(this)) {
                WindowHelper.showScreenshotView();
            }
        });
        binding.btnHideScreenshot.setOnClickListener(v -> {
            if (WindowHelper.checkOverlay(this)) {
                WindowHelper.hideScreenshotView();
            }
        });
        binding.rgServiceType.check(R.id.rb_screenshot);
        binding.rgServiceType.setOnCheckedChangeListener((group, checkedId) -> {
            if (checkedId == R.id.rb_screenshot) {
                MediaProjectionService.serviceType = ServiceType.SCREENSHOT;
            }
        });
        binding.btnStartJiaoben.setOnClickListener(v -> {
            try {
                startSelfExplorer();
            } catch (InterruptedException | IOException | JSONException e) {
                throw new RuntimeException(e);
            }
        });
        binding.btnStartAccessibility.setOnClickListener(v -> {
            checkAccessibilityService();
        });
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, @Nullable Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        MediaProjectionHelper.onStartResult(requestCode, resultCode, data);
    }

    public  void startSelfExplorer() throws InterruptedException, IOException, JSONException {

        //初始化类变量。
        last_act = "None";
        task_complete=false;
        round_count=0;

        //开发中测试用变量，将来必须让用户输入
        String app = "com.zhihu.android";
        String task_desc="打开知乎点击故事";
        //获取文件配置
        Map<String,String> configs = loadConfig(this,"config.json");

        //建立通信模型，这里很屎因为我还没把openAiModel给弄懂。
        qwenModel model = new qwenModel(
                configs.get("DASHSCOPE_API_KEY"),
                configs.get("QWEN_MODEL")
        );
        if(configs.get("MODEL").equals("OpenAI")){
            // 这个还没修改，暂时不用
            Log.d(TAG,"openAI还没修改完");
        }else if(configs.get("MODEL").equals("Qwen")){
            model = new qwenModel(
                    configs.get("DASHSCOPE_API_KEY"),
                    configs.get("QWEN_MODEL")
            );
        }else{
            printUtils.printWithColor("ERROR: Unsupported model type"+configs.get("MODEL"), "red");
            return ;
        }

        //文件路径，该测试所用到的文件路径在这里定义。File定义下给出了一个例子方便理解
        Log.d(TAG, "MainActivity 创建完成");
        final File root_dir = new File(App.getApp().getExternalFilesDir(null).getParent());
        // /storage/emulated/0/Android/data/com.example.myapplication
        Log.d(TAG,this.getFilesDir().toString());//
        File work_dir = new File(root_dir,"apps");
        // /storage/emulated/0/Android/data/com.example.myapplication/apps
        if (!work_dir.exists()) {
            work_dir.mkdirs();
        }
        work_dir = new File(work_dir,app);
        // /storage/emulated/0/Android/data/com.example.myapplication/apps/com.zhihu.android
        if (!work_dir.exists()) {
            work_dir.mkdirs();
        }
        File demo_dir = new File(work_dir,"demos");
        // /storage/emulated/0/Android/data/com.example.myapplication/apps/com.zhihu.android/demos
        if(!demo_dir.exists()){
            demo_dir.mkdirs();
        }
        long demoTimestamp = System.currentTimeMillis() / 1000;
        Date date = new Date(demoTimestamp * 1000);  // 乘1000转换为毫秒级
        SimpleDateFormat dateFormat = new SimpleDateFormat("'self_explore_'yyyy-MM-dd_HH-mm-ss");
        String taskName = dateFormat.format(date);
        File task_dir = new File(demo_dir,taskName);
        // /storage/emulated/0/Android/data/com.example.myapplication/apps/com.zhihu.android/demos/self_explore_'yyyy-MM-dd_HH-mm-ss
        task_dir.mkdirs();
        File docs_dir = new File(work_dir,"auto_docs");
        // /storage/emulated/0/Android/data/com.example.myapplication/apps/com.zhihu.android/auto_docs
        if(!docs_dir.exists()){
            docs_dir.mkdirs();
        }
        File explore_log_path = new File(task_dir,"log_explore_"+taskName+".txt");
        File reflect_log_path = new File(task_dir,"log_reflect_"+taskName+".txt");

        
        //创建方法类
        androidController androidController = new androidController(this);
        int width = androidController.get_device_size().x;
        int height = androidController.get_device_size().y;
        printUtils.printWithColor("Screen resolution of present device"+width+"x"+height,"yellow");
        printUtils.printWithColor("Please enter the description of the task you want me to complete in a few sentences:","blue");
        int doc_count = 0;



        List<String> useless_list = new ArrayList<>();


        //回到桌面
        Intent intent = new Intent(Intent.ACTION_MAIN);
        intent.addCategory(Intent.CATEGORY_HOME);
        intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        startActivity(intent);
        Thread.sleep(1000);


        //开始循环和模型进行通信
        while (round_count < Integer.parseInt(Objects.requireNonNull(configs.get("MAX_ROUNDS")))) {
           round_count++;
           printUtils.printWithColor("Round "+round_count,"yellow");
           String screenshot_before = androidController.get_screenshot(round_count+"_before.png",task_dir.getAbsolutePath());
           printUtils.printWithColor(screenshot_before,"yellow");
           String output_path = task_dir.getAbsolutePath()+"/"+ round_count +"_before_labeled.png";
           Boolean dark_mode = Boolean.valueOf(configs.get("DARK_MODE"));

           //同样是为了避免lambda表达式里不能使用非常量
           qwenModel finalModel = model;

           androidController.get_xml(round_count,task_dir, xml_path->{
               // 这里是 XML 文件保存完成后的回调,之后所有逻辑都在这里面完成。,
               printUtils.printWithColor("XML 文件路径：" + xml_path, "yellow");
               List<androidElement> clickable_list = new ArrayList<>();
               List<androidElement> focusable_list = new ArrayList<>();

               //分析得到的xml文件，将clickable为true的AndroidElement（这是自己创建的类不是Android官方给的类）放进clickablelist里，focusable同理。
               androidController.traverseTree(xml_path,clickable_list,"clickable",true);
               androidController.traverseTree(xml_path,focusable_list,"focusable",true);

               //下面会把clickable_list和focusable_list里的AndroidElement放进elem_list里，优先放clickable为true的，然后放focusable为true并且不会靠已存在的AndroidElement太近的。
               List<androidElement> elem_list = new ArrayList<>();
               for(androidElement elem:clickable_list){
                   if(useless_list.contains(elem.uid)){
                       continue;
                   }
                   elem_list.add(elem);
               }
               for(androidElement elem:focusable_list){
                   if(useless_list.contains(elem.uid)){
                       continue;
                   }
                   int[] bbox = elem.bbox;
                   int[] center ={(bbox[0] + bbox[2]) / 2, (bbox[1] + bbox[3]) / 2};
                   boolean close=false;
                   for(androidElement e:clickable_list){
                       bbox = e.bbox;
                       int[] center_ ={(bbox[0] + bbox[2]) / 2, (bbox[1] + bbox[3]) / 2};
                       double dist = Math.sqrt(Math.pow(Math.abs(center[0] - center_[0]), 2) +
                               Math.pow(Math.abs(center[1] - center_[1]), 2));
                       if(dist<=Double.parseDouble(configs.get("MIN_DIST"))){
                           close=true;
                           break;
                       }
                   }
                   if(!close){
                       elem_list.add(elem);
                   }
               }

               //将之前截的图，根据elem_list来绘制，得到一个标记了可点击点的屏幕截图
               androidController.drawBoundingBoxes(screenshot_before,output_path,elem_list,false,dark_mode);

               //将标记后的屏幕截图连通一个prompt发给Qwen模型，得到它的回复。
               Map<String,String> prompts = loadConfig(this,"prompts.json");
               String prompt = prompts.get("self_explore_task_template").replace("<task_description>",task_desc);
               prompt = prompt.replace("<last_act>", last_act);
               printUtils.printWithColor("Thinking about what to do in the next step...", "yellow");
               Map.Entry<Boolean,String> status_rsp = finalModel.getModelResponse(prompt, Collections.singletonList(output_path));

               if(status_rsp.getKey()){
                   //把此次回复的信息存进jason文件里。
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
                   String act_name = res.get(0);
                   last_act = res.get(res.size() - 1);
                   res.remove(res.size() - 1);
                   printUtils.printWithColor("开始分析回复得到信息","yellow");
                   if(act_name.equals("FINISH")){
                       task_complete =true;
                   }
                   if(act_name.equals("tap")){
                        printUtils.printWithColor("开始点击！","yellow");
                        int area = Integer.parseInt(res.get(1));
                        int[] bbox = elem_list.get(area-1).bbox;
                        int x = (bbox[0]+bbox[2])/2;
                        int y = (bbox[1]+bbox[3])/2;
                        androidController.tap(x,y);
                   }
                   if(act_name.equals("text")){
                       printUtils.printWithColor("开始输入文字！","yellow");
                       String input_str = res.get(1);
                       androidController.text(input_str);


                   }



               }else{
                   printUtils.printWithColor(status_rsp.getValue(),"yellow");
               }
           });

        }
    }

    public void checkAccessibilityService(){
        // 检查无障碍服务是否已启用
        if (!isAccessibilityServiceEnabled(this, ACCESSIBILITY_SERVICE_ID)) {
            // 如果未授权，则跳转到无障碍设置页面
            Intent intent = new Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS);
            startActivity(intent);
        }
    }

    private boolean isAccessibilityServiceEnabled(Context context, String serviceId) {
        try {
            String enabledServices = Settings.Secure.getString(
                    context.getContentResolver(),
                    Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES
            );
            return enabledServices != null && enabledServices.contains(serviceId);
        } catch (Exception e) {
            Log.e("AccessibilityCheck", "检查无障碍权限时出错", e);
            return false;
        }
    }


}
