package com.example.myapplication.activity;

import static com.example.myapplication.scripts.config.loadConfig;

import android.Manifest;
import android.accessibilityservice.AccessibilityService;
import android.content.ActivityNotFoundException;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.provider.Settings;
import android.speech.RecognitionListener;
import android.speech.RecognizerIntent;
import android.speech.SpeechRecognizer;
import android.text.Editable;
import android.text.TextUtils;
import android.text.TextWatcher;
import android.util.Log;
import android.view.View;
import android.widget.EditText;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

import com.example.myapplication.App;
import com.example.myapplication.R;
import com.example.myapplication.constant.ServiceType;
import com.example.myapplication.databinding.ActivityMainBinding;
import com.example.myapplication.myAccessibilityService;
import com.example.myapplication.scripts.HighlightManager;
import com.example.myapplication.scripts.TTSManager;
import com.example.myapplication.scripts.androidController;
import com.example.myapplication.scripts.androidElement;
import com.example.myapplication.scripts.qwenModel;
import com.example.myapplication.scripts.responseParser;
import com.example.myapplication.scripts.printUtils;
import com.example.myapplication.services.MediaProjectionService;
import com.example.myapplication.util.MediaProjectionHelper;
import com.example.myapplication.util.NotificationHelper;
import com.example.myapplication.util.ToastUtils;
import com.example.myapplication.util.WindowHelper;
import com.example.myapplication.view.PieChartView;

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
import java.util.Locale;
import java.util.Map;
import java.util.Objects;

public class MainActivity extends AppCompatActivity {
    private ActivityMainBinding binding;
    public static String TAG = "MainActivity";
    private static final int REQ_CODE_VOICE_APP = 1001;
    private static final int REQ_CODE_VOICE_TASK = 1002;
    String app = "";
    String task_desc = "";


    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        binding = ActivityMainBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());
        NotificationHelper.check(this);
        initView();
        TTSManager.getInstance(this);
        HighlightManager.Maincontext=this;

    }

    private void initView() {
        if (getSupportActionBar() != null) {
            getSupportActionBar().hide();
        }
        binding.btnStart.setOnClickListener(v -> {
            MediaProjectionHelper.start(this);

        });
        binding.btnStartAccessibility.setOnClickListener(v -> checkAccessibilityService());

        // 监听 App 输入框
        binding.etApp.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {}

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
                app = s.toString(); // 动态更新 app 变量
            }

            @Override
            public void afterTextChanged(Editable s) {}
        });

        // 监听 Task 描述输入框
        binding.etTaskDesc.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {}

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
                task_desc = s.toString(); // 动态更新 task_desc 变量
            }

            @Override
            public void afterTextChanged(Editable s) {}
        });
        PieChartView pieChartView = binding.pieChart;
        pieChartView.setOnSliceClickListener(index -> {
            switch (index) {
                case 0:
                    binding.mainButton.animate()
                            .alpha(0f)
                            .setDuration(300)
                            .withEndAction(() -> {
                                binding.mainButton.setVisibility(View.GONE);
                                // 让 ScrollView 先透明
                                binding.quanXian.setAlpha(0f);
                                binding.quanXian.setVisibility(View.VISIBLE);
                                // 然后渐变出现
                                binding.quanXian.animate()
                                        .alpha(1f)
                                        .setDuration(300);
                            });
                    break;
                case 1:
                    if(app.isEmpty()){
                        ToastUtils.shortCall("app为空！请输入app名称");
                        TTSManager.getInstance(this).speak("app为空！请输入app名称!");
                    }else if(task_desc.isEmpty()){
                        ToastUtils.shortCall("任务描述为空！请输入任务描述！");
                        TTSManager.getInstance(this).speak("任务描述为空！请输入任务描述！");
                    }else if(!MediaProjectionHelper.mStarted){
                        ToastUtils.shortCall("前台截屏权限未授予！请授予前台截屏权限！");
                        TTSManager.getInstance(this).speak("前台截屏权限未授予！请授予前台截屏权限！\"");
                    }else if (!isAccessibilityServiceEnabled(this, myAccessibilityService.class)){
                        ToastUtils.shortCall("无障碍权限未授予！请授予无障碍权限！");
                        TTSManager.getInstance(this).speak("无障碍权限未授予！请授予无障碍权限！");
                    }
                    else {
                        selfExplorer selfExplorer = new selfExplorer(this, app, task_desc);
                        new Thread(() -> {
                            try {
                                selfExplorer.startSelfExplorer();
                            } catch (InterruptedException | JSONException | IOException e) {
                                throw new RuntimeException(e);
                            }
                        }).start();
                    }
                    break;
                case 2:
                    if(app.isEmpty()){
                        ToastUtils.shortCall("app为空！请输入app名称");
                        TTSManager.getInstance(this).speak("app为空！请输入app名称!");
                    }else if(task_desc.isEmpty()){
                        ToastUtils.shortCall("任务描述为空！请输入任务描述！");
                        TTSManager.getInstance(this).speak("任务描述为空！请输入任务描述！");
                    }else if(!MediaProjectionHelper.mStarted){
                        ToastUtils.shortCall("前台截屏权限未授予！请授予前台截屏权限！");
                        TTSManager.getInstance(this).speak("前台截屏权限未授予！请授予前台截屏权限！");
                    }else if (!isAccessibilityServiceEnabled(this, myAccessibilityService.class)){
                        ToastUtils.shortCall("无障碍权限未授予！请授予无障碍权限！");
                        TTSManager.getInstance(this).speak("无障碍权限未授予！请授予无障碍权限！");
                    }
                    else {
                        taskExecutor taskExecutor = new taskExecutor(this, app, task_desc);
                        new Thread(() -> {
                            try {
                                taskExecutor.startTaskExecutor();
                            } catch (InterruptedException | IOException | JSONException e) {
                                throw new RuntimeException(e);
                            }
                        }).start();
                    }
                    break;
            }
        });

        binding.btnAiGuide.setOnClickListener(v ->{
            if(app.isEmpty()){
                ToastUtils.shortCall("app为空！请输入app名称");
                TTSManager.getInstance(this).speak("app为空！请输入app名称!");
            }else if(task_desc.isEmpty()){
                ToastUtils.shortCall("任务描述为空！请输入任务描述！");
                TTSManager.getInstance(this).speak("任务描述为空！请输入任务描述！");
            }else if(!MediaProjectionHelper.mStarted){
                ToastUtils.shortCall("前台截屏权限未授予！请授予前台截屏权限！");
                TTSManager.getInstance(this).speak("前台截屏权限未授予！请授予前台截屏权限！");
            }else if (!isAccessibilityServiceEnabled(this, myAccessibilityService.class)){
                ToastUtils.shortCall("无障碍权限未授予！请授予无障碍权限！");
                TTSManager.getInstance(this).speak("无障碍权限未授予！请授予无障碍权限！");
            }else{
                aiGuide aiGuide = new aiGuide(this,app,task_desc);
                myAccessibilityService.getInstance().aiGuide=aiGuide;
                new Thread(() -> {
                    try {
                        aiGuide.startAiGuide();
                    } catch (Exception e) {
                        throw new RuntimeException(e);
                    }
                }).start();
            }
        });
        binding.btnStartOverlay.setOnClickListener(v -> {
            if(hasOverlayPermission(this)){
                requestOverlayPermission(this);
            }
        });

        binding.backButton.setOnClickListener(v -> {
            binding.quanXian.animate()
                    .alpha(0f)
                    .setDuration(300)
                    .withEndAction(() -> {
                        binding.quanXian.setVisibility(View.GONE);
                        // 让 ScrollView 先透明
                        binding.mainButton.setAlpha(0f);
                        binding.mainButton.setVisibility(View.VISIBLE);
                        // 然后渐变出现
                        binding.mainButton.animate()
                                .alpha(1f)
                                .setDuration(300);
                    });
        });







    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, @Nullable Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        MediaProjectionHelper.onStartResult(requestCode, resultCode, data);

    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
    }

    public void checkAccessibilityService(){
        // 检查无障碍服务是否已启用
        if (!isAccessibilityServiceEnabled(this, myAccessibilityService.class)) {
            // 如果未授权，则跳转到无障碍设置页面
            Intent intent = new Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS);
            startActivity(intent);
        }
    }

    private boolean isAccessibilityServiceEnabled(Context context, Class<? extends AccessibilityService> service) {
        ComponentName expectedComponentName = new ComponentName(context, service);

        try {
            String enabledServicesSetting = Settings.Secure.getString(
                    context.getContentResolver(),
                    Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES
            );
            if (enabledServicesSetting == null) {
                return false;
            }
            TextUtils.SimpleStringSplitter colonSplitter = new TextUtils.SimpleStringSplitter(':');
            colonSplitter.setString(enabledServicesSetting);
            while (colonSplitter.hasNext()) {
                String enabledService = colonSplitter.next();
                ComponentName enabledComponent = ComponentName.unflattenFromString(enabledService);
                if (enabledComponent != null && enabledComponent.equals(expectedComponentName)) {
                    return true;
                }
            }
        } catch (Exception e) {
            Log.e("AccessibilityCheck", "检查无障碍权限时出错", e);
        }
        return false;
    }




    public boolean hasOverlayPermission(Context context) {
        return Settings.canDrawOverlays(context);
    }

    public void requestOverlayPermission(Context context) {
        if (!Settings.canDrawOverlays(context)) {
            Toast.makeText(context, "需要悬浮窗权限，请开启！", Toast.LENGTH_SHORT).show();
            Intent intent = new Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                    Uri.parse("package:" + context.getPackageName()));
            context.startActivity(intent);
        }else{
            Toast.makeText(context, "悬浮窗已经开启了！", Toast.LENGTH_SHORT).show();
        }
    }

    public void onBackPressed() {
        if (binding.backButton.getVisibility() != View.GONE) {
            // 如果按钮是可见的，模拟点击
            binding.backButton.performClick();
            // 模拟点击自定义返回按钮
        }
    }



}
