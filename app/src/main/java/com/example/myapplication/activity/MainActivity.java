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
            selfExplorer selfExplorer = new selfExplorer(this);
            new Thread(() -> {
                try {
                    selfExplorer.startSelfExplorer();
                } catch (InterruptedException | JSONException | IOException e) {
                    throw new RuntimeException(e);
                }
            }).start();
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
