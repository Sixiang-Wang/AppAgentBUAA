package com.example.myapplication.activity;



import static com.example.myapplication.scripts.config.loadConfig;

import android.Manifest;
import android.annotation.TargetApi;
import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.graphics.Rect;
import android.media.projection.MediaProjectionManager;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.os.Handler;
import android.os.Looper;
import android.os.SystemClock;
import android.provider.Settings;
import android.util.Log;
import android.view.MotionEvent;
import android.view.PixelCopy;
import android.view.View;
import android.view.ViewTreeObserver;
import android.view.Window;
import android.view.WindowManager;
import android.view.accessibility.AccessibilityManager;
import android.widget.Button;
import android.widget.ImageView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;

import com.example.myapplication.App;
import com.example.myapplication.MyAccessibilityService;
import com.example.myapplication.R;
import com.example.myapplication.constant.RequestCode;
import com.example.myapplication.constant.ServiceType;
import com.example.myapplication.databinding.ActivityMainBinding;
import com.example.myapplication.scripts.Android_Controller;
import com.example.myapplication.scripts.QwenModel;
import com.example.myapplication.scripts.utils;
import com.example.myapplication.services.MediaProjectionService;
import com.example.myapplication.util.MediaProjectionHelper;
import com.example.myapplication.util.NotificationHelper;
import com.example.myapplication.util.TaskPool;
import com.example.myapplication.util.ToastUtils;
import com.example.myapplication.util.WindowHelper;
import com.example.myapplication.view.ScreenshotView;

import java.io.File;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Map;
import java.util.Objects;

public class MainActivity extends AppCompatActivity {
    private ActivityMainBinding binding;
    public static String TAG = "MainActivity";
    private static final String ACCESSIBILITY_SERVICE_ID = "com.example.myapplication/.MyAccessibilityService";


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
                startLearn();
            } catch (InterruptedException e) {
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

    public  void startLearn() throws InterruptedException {
        Log.d(TAG, "MainActivity 创建完成");
        Log.d(TAG, "请求屏幕录制权限");
        final File root_dir = new File(App.getApp().getExternalFilesDir(null).getParent());
        String app = "com.zhihu.android";
        String task_desc="打开知乎点击故事";
        //请求读写权限
//        if ( Build.VERSION.SDK_INT>=Build.VERSION_CODES.R) {
//            if (!Environment.isExternalStorageManager()) {
//                Intent intent = new Intent(Settings.ACTION_MANAGE_ALL_FILES_ACCESS_PERMISSION);
//                this.startActivity(intent);
//                return;
//            }
//        }
        Log.d(TAG,this.getFilesDir().toString());
        //获取文件配置
        Map<String,String> configs = loadConfig(this);
        //建立模型
        if(configs.get("MODEL").equals("OpenAI")){
            Log.d(TAG,"openAI还没修改完");
            // 这个还没修改，暂时不用
        }else if(configs.get("MODEL").equals("Qwen")){
            QwenModel qwenModel = new QwenModel(
                    configs.get("DASHSCOPE_API_KEY"),
                    configs.get("QWEN_MODEL")
            );
        }else{
            utils.printWithColor("ERROR: Unsupported model type"+configs.get("MODEL"), "red");
            return ;
        }
        File work_dir = new File(root_dir,"apps");
        if (!work_dir.exists()) {
            work_dir.mkdirs();
        }
        work_dir = new File(work_dir,app);
        if (!work_dir.exists()) {
            work_dir.mkdirs();
        }
        File demo_dir = new File(work_dir,"demos");
        if(!demo_dir.exists()){
            demo_dir.mkdirs();
        }
        long demoTimestamp = System.currentTimeMillis() / 1000;
        Date date = new Date(demoTimestamp * 1000);  // 乘1000转换为毫秒级
        SimpleDateFormat dateFormat = new SimpleDateFormat("'self_explore_'yyyy-MM-dd_HH-mm-ss");
        String taskName = dateFormat.format(date);
        File task_dir = new File(demo_dir,taskName);
        task_dir.mkdirs();
        File docs_dir = new File(work_dir,"auto_docs");
        if(!docs_dir.exists()){
            docs_dir.mkdirs();
        }
        File explore_log_path = new File(task_dir,"log_explore_"+taskName+".txt");
        File reflect_log_path = new File(task_dir,"log_reflect_"+taskName+".txt");
        Android_Controller androidController = new Android_Controller(this);
        int width = androidController.get_device_size().x;
        int height = androidController.get_device_size().y;
        utils.printWithColor("Screen resolution of present device"+width+"x"+height,"yellow");
        utils.printWithColor("Please enter the description of the task you want me to complete in a few sentences:","blue");
        int round_count = 0;
        int doc_count = 0;
        String last_act = "None";
        Boolean task_complete = false;

        //回到桌面
        Intent intent = new Intent(Intent.ACTION_MAIN);
        intent.addCategory(Intent.CATEGORY_HOME);
        intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        startActivity(intent);

        while (round_count < Integer.parseInt(Objects.requireNonNull(configs.get("MAX_ROUNDS")))) {
           round_count++;
           utils.printWithColor("Round "+round_count,"yellow");
           String screenshot_before = androidController.get_screenshot(round_count+"_before.png",task_dir.getAbsolutePath());
           String xml_path = androidController.get_xml(round_count,task_dir);
           utils.printWithColor(screenshot_before,"yellow");
           utils.printWithColor(xml_path,"yellow");
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
