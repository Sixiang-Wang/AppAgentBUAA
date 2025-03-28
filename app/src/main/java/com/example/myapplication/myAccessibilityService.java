package com.example.myapplication;

import static com.example.myapplication.activity.MainActivity.TAG;
import static com.example.myapplication.scripts.androidController.height;
import static com.example.myapplication.scripts.androidController.width;

import android.accessibilityservice.AccessibilityService;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.graphics.Rect;
import android.os.Build;
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

public class myAccessibilityService extends AccessibilityService {
    private static final String[] NAF_EXCLUDED_CLASSES = new String[] {
            android.widget.GridView.class.getName(), android.widget.GridLayout.class.getName(),
            android.widget.ListView.class.getName(), android.widget.TableLayout.class.getName()
    };

    private static myAccessibilityService instance; // 让外部可以访问

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

    public static myAccessibilityService getInstance() {
        return instance;
    }

    public void saveUiHierarchy(String prefix, String save_dir) {
        if (instance == null) {
            Log.e(TAG, "Instance is null! AccessibilityService not started?");
            return;
        }

        List<AccessibilityWindowInfo> windows = getWindows();
        if (windows == null || windows.isEmpty()) {
            Log.e(TAG, "No windows found! Maybe no active UI?");
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
            Log.e(TAG, "Root node is null! No suitable window found.");
            return;
        }

        Log.d(TAG, "Root node found! Proceeding to save UI hierarchy...");

        File saveDirectory = new File(save_dir);
        if (!saveDirectory.exists() && !saveDirectory.mkdirs()) {
            Log.e(TAG, "Failed to create directory: " + save_dir);
            return;
        }

        File saveFile = new File(save_dir, prefix + ".xml");
        try (FileOutputStream fos = new FileOutputStream(saveFile, false)) {
            XmlSerializer serializer = Xml.newSerializer();
            serializer.setOutput(fos, "UTF-8");
            serializer.startDocument("UTF-8", true);
            serializeNode(serializer, rootNode,0);
            serializer.endDocument();
            serializer.flush();
            Log.d(TAG, "Saved UI hierarchy to: " + saveFile.getAbsolutePath());
        } catch (IOException e) {
            Log.e(TAG, "Error saving UI hierarchy", e);
        }
    }





    private void serializeNode(XmlSerializer serializer, AccessibilityNodeInfo node,int index) throws IOException {
        if (node == null) return;


        if (!nafExcludedClass(node) && !nafCheck(node)) {
            return;
        }
        serializer.startTag("", "node");
        serializer.attribute("", "index", Integer.toString(index));
        serializer.attribute("", "text", node.getText() != null ? node.getText().toString() : "");
        serializer.attribute("", "resource-id", node.getViewIdResourceName() != null ? node.getViewIdResourceName() : "");
        serializer.attribute("", "class", node.getClassName() != null ? node.getClassName().toString() : "");
        serializer.attribute("", "package", node.getPackageName()!=null ? node.getPackageName().toString() : "");
        serializer.attribute("", "content-desc", node.getContentDescription() != null ? node.getContentDescription().toString() : "");
        serializer.attribute("", "checkable", String.valueOf(node.isCheckable()));
        serializer.attribute("", "checked", String.valueOf(node.isChecked()));
        serializer.attribute("", "clickable", String.valueOf(node.isClickable()));
        serializer.attribute("", "enabled", String.valueOf(node.isEnabled()));
        serializer.attribute("", "focusable", String.valueOf(node.isFocusable()));
        serializer.attribute("", "focused", String.valueOf(node.isFocused()));
        serializer.attribute("","scrollable",String.valueOf(node.isScrollable()));
        serializer.attribute("", "long-clickable", String.valueOf(node.isLongClickable()));
        serializer.attribute("", "password", String.valueOf(node.isPassword()));
        serializer.attribute("", "selected", String.valueOf(node.isSelected()));

        Rect bounds = new Rect();
        node.getBoundsInScreen(bounds);
        serializer.attribute("", "bounds", getVisibleBoundsInScreen(node,new Rect(0, 0, width, height),false).toShortString());
        // 序列化子节点
        for (int i = 0; i < node.getChildCount(); i++) {
            serializeNode(serializer, node.getChild(i),i);
        }
        serializer.endTag("", "node");
    }

    private String getLauncherPackageName() {
        Intent intent = new Intent(Intent.ACTION_MAIN);
        intent.addCategory(Intent.CATEGORY_HOME);
        ResolveInfo resolveInfo = getPackageManager().resolveActivity(intent, PackageManager.MATCH_DEFAULT_ONLY);
        return resolveInfo.activityInfo.packageName;
    }

    private static boolean nafExcludedClass(AccessibilityNodeInfo node) {
        String className = safeCharSeqToString(node.getClassName());
        for(String excludedClassName : NAF_EXCLUDED_CLASSES) {
            if(className.endsWith(excludedClassName))
                return true;
        }
        return false;
    }
    private static boolean nafCheck(AccessibilityNodeInfo node) {
        boolean isNaf = node.isClickable() && node.isEnabled()
                && safeCharSeqToString(node.getContentDescription()).isEmpty()
                && safeCharSeqToString(node.getText()).isEmpty();

        if (!isNaf)
            return true;

        // check children since sometimes the containing element is clickable
        // and NAF but a child's text or description is available. Will assume
        // such layout as fine.
        return childNafCheck(node);
    }

    private static boolean childNafCheck(AccessibilityNodeInfo node) {
        int childCount = node.getChildCount();
        for (int x = 0; x < childCount; x++) {
            AccessibilityNodeInfo childNode = node.getChild(x);
            if (childNode == null) {
                continue;
            }
            if (!safeCharSeqToString(childNode.getContentDescription()).isEmpty()
                    || !safeCharSeqToString(childNode.getText()).isEmpty()) {
                return true;
            }

            if (childNafCheck(childNode)) {
                return true;
            }
        }
        return false;
    }
    private static String safeCharSeqToString(CharSequence cs) {
        return cs == null ? "" : stripInvalidXMLChars(cs);
    }

    private static String stripInvalidXMLChars(CharSequence cs) {
        StringBuilder ret = new StringBuilder();
        char ch;
        for (int i = 0; i < cs.length(); i++) {
            ch = cs.charAt(i);
            // http://www.w3.org/TR/xml11/#charsets
            if ((ch >= 0x0 && ch <= 0x8)
                    || (ch >= 0xB && ch <= 0xC)
                    || (ch >= 0xE && ch <= 0x1F)
                    || (ch >= 0x7F && ch <= 0x84)
                    || (ch >= 0x86 && ch <= 0x9F)
                    || (ch >= 0xD800 && ch <= 0xDFFF)
                    || (ch >= 0xFDD0 && ch <= 0xFDDF)
                    || (ch >= 0xFFFE && ch <= 0xFFFF)) {
                ret.append(".");
            } else {
                ret.append(ch);
            }
        }
        return ret.toString();
    }
    static Rect getVisibleBoundsInScreen(AccessibilityNodeInfo node, Rect displayRect,
                                         boolean trimScrollableParent) {
        if (node == null) {
            return null;
        }
        // targeted node's bounds
        Rect nodeRect = new Rect();
        node.getBoundsInScreen(nodeRect);
        if (displayRect == null) {
            displayRect = new Rect();
        }
        // On platforms that give us access to the node's window
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            // Trim any portion of the bounds that are outside the window
            Rect bounds = new Rect();
            AccessibilityWindowInfo window = Api21Impl.getWindow(node);
            if (window != null) {
                Api21Impl.getBoundsInScreen(window, bounds);
            }
        }

        // Trim the bounds into any scrollable ancestor, if required.
        if (trimScrollableParent) {
            for (AccessibilityNodeInfo ancestor = node.getParent(); ancestor != null; ancestor =
                    ancestor.getParent()) {
                if (ancestor.isScrollable()) {
                    Rect ancestorRect = getVisibleBoundsInScreen(ancestor, displayRect, true);

                    break;
                }
            }
        }

        return nodeRect;
    }

    static class Api21Impl {
        private Api21Impl() {
        }

        //        @DoNotInline
        static void getBoundsInScreen(AccessibilityWindowInfo accessibilityWindowInfo,
                                      Rect outBounds) {
            accessibilityWindowInfo.getBoundsInScreen(outBounds);
        }

        //        @DoNotInline
        static AccessibilityWindowInfo getWindow(AccessibilityNodeInfo accessibilityNodeInfo) {
            return accessibilityNodeInfo.getWindow();
        }
    }







}
