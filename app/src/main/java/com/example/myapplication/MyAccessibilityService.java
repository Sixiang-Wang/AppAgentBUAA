package com.example.myapplication;

import static com.example.myapplication.activity.MainActivity.TAG;

import android.accessibilityservice.AccessibilityService;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.util.Log;
import android.view.accessibility.AccessibilityEvent;
import android.view.accessibility.AccessibilityNodeInfo;
import android.util.Xml;
import android.view.accessibility.AccessibilityWindowInfo;

import org.xmlpull.v1.XmlSerializer;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.List;

public class MyAccessibilityService extends AccessibilityService {
    private static MyAccessibilityService instance; // 让外部可以访问

    @Override
    public void onCreate() {
        super.onCreate();
        instance = this; // 保存实例，供外部调用
    }


    @Override
    public void onAccessibilityEvent(AccessibilityEvent event) {

    }

    @Override
    protected void onServiceConnected() {
        super.onServiceConnected();
        Log.d("MyAccessibilityService", "Accessibility Service Connected!");
    }

    @Override
    public void onInterrupt() {
        // 需要实现的方法，无需操作
    }

    public static MyAccessibilityService getInstance() {
        return instance;
    }

    public void saveUiHierarchy(String prefix, String save_dir) {
        if (instance == null) {
            Log.e("saveUiHierarchy", "Instance is null! AccessibilityService not started?");
            return;
        }

        List<AccessibilityWindowInfo> windows = getWindows();
        if (windows == null || windows.isEmpty()) {
            Log.e("saveUiHierarchy", "No windows found! Maybe no active UI?");
            return;
        }

        AccessibilityNodeInfo rootNode = null;
        String launcherPackage = getLauncherPackageName(); // 获取桌面启动器的包名
        for (AccessibilityWindowInfo window : windows) {
            if (window.getType() == AccessibilityWindowInfo.TYPE_APPLICATION) {
                rootNode = window.getRoot();
                if (rootNode != null) {
                    String packageName = rootNode.getPackageName() != null ? rootNode.getPackageName().toString() : "";
                    if (launcherPackage.equals(packageName) || packageName.isEmpty()) {
                        // 这是桌面窗口
                        break;
                    }
                }
            }
        }

        if (rootNode == null) {
            Log.e("saveUiHierarchy", "Root node is null! No suitable window found.");
            return;
        }

        Log.d("saveUiHierarchy", "Root node found! Proceeding to save UI hierarchy...");

        File saveDirectory = new File(save_dir);
        if (!saveDirectory.exists() && !saveDirectory.mkdirs()) {
            Log.e("saveUiHierarchy", "Failed to create directory: " + save_dir);
            return;
        }

        File saveFile = new File(save_dir, prefix + ".xml");
        try (FileOutputStream fos = new FileOutputStream(saveFile, false)) {
            XmlSerializer serializer = Xml.newSerializer();
            serializer.setOutput(fos, "UTF-8");
            serializer.startDocument("UTF-8", true);
            serializeNode(serializer, rootNode);
            serializer.endDocument();
            serializer.flush();
            Log.d("saveUiHierarchy", "Saved UI hierarchy to: " + saveFile.getAbsolutePath());
        } catch (IOException e) {
            Log.e("saveUiHierarchy", "Error saving UI hierarchy", e);
        }
    }





    private void serializeNode(XmlSerializer serializer, AccessibilityNodeInfo node) throws IOException {
        if (node == null) return;

        serializer.startTag("", "node");
        serializer.attribute("", "class", node.getClassName() != null ? node.getClassName().toString() : "");
        serializer.attribute("", "text", node.getText() != null ? node.getText().toString() : "");
        serializer.attribute("", "content-desc", node.getContentDescription() != null ? node.getContentDescription().toString() : "");
        serializer.attribute("", "clickable", String.valueOf(node.isClickable()));

        for (int i = 0; i < node.getChildCount(); i++) {
            serializeNode(serializer, node.getChild(i));
        }

        serializer.endTag("", "node");
    }

    private String getLauncherPackageName() {
        Intent intent = new Intent(Intent.ACTION_MAIN);
        intent.addCategory(Intent.CATEGORY_HOME);
        ResolveInfo resolveInfo = getPackageManager().resolveActivity(intent, PackageManager.MATCH_DEFAULT_ONLY);
        return resolveInfo.activityInfo.packageName;
    }

}
