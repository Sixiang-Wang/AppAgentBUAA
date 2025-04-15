package com.example.myapplication;


import static com.example.myapplication.activity.MainActivity.TAG;
import static com.example.myapplication.scripts.androidController.height;
import static com.example.myapplication.scripts.androidController.width;

import com.example.myapplication.activity.aiGuide;
import android.accessibilityservice.AccessibilityService;
import android.accessibilityservice.AccessibilityServiceInfo;
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


import org.json.JSONArray;
import org.json.JSONObject;
import org.xmlpull.v1.XmlSerializer;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStreamWriter;
import java.io.StringWriter;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;


public class myAccessibilityService extends AccessibilityService {
    private static final String[] NAF_EXCLUDED_CLASSES = new String[] {
            android.widget.GridView.class.getName(), android.widget.GridLayout.class.getName(),
            android.widget.ListView.class.getName(), android.widget.TableLayout.class.getName()
    };

    private static myAccessibilityService instance; // 让外部可以访问

    public AccessibilityNodeInfo myActiveRootNode = null;

    public JSONObject jsonObject = null;

    public aiGuide aiGuide;

    @Override
    public void onCreate() {
        super.onCreate();
        instance = this; // 保存实例，供外部调用
    }

    private void configServiceInfo() {
        AccessibilityServiceInfo serviceInfo = new AccessibilityServiceInfo();
        serviceInfo.feedbackType = AccessibilityServiceInfo.FEEDBACK_GENERIC;
        serviceInfo.flags = AccessibilityServiceInfo.DEFAULT |
                AccessibilityServiceInfo.FLAG_INPUT_METHOD_EDITOR | AccessibilityServiceInfo.FLAG_RETRIEVE_INTERACTIVE_WINDOWS;
        serviceInfo.flags &= ~AccessibilityServiceInfo.FLAG_INCLUDE_NOT_IMPORTANT_VIEWS;
        // 将配置好的 serviceInfo 设置到无障碍服务中
        setServiceInfo(serviceInfo);
    }


    @Override
    public void onAccessibilityEvent(AccessibilityEvent event) {
        if (event.getEventType() == AccessibilityEvent.TYPE_VIEW_CLICKED) {
            Log.d("Accessibility", "检测到用户点击事件");
            if (aiGuide != null) {
                aiGuide.notifyUserClicked();
            }
        }
    }

    @Override
    protected void onServiceConnected() {
        super.onServiceConnected();
        configServiceInfo();
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
        for (AccessibilityWindowInfo window : windows) {
            Log.i(TAG,window.getTitle()+"_"+window.getRoot().getPackageName());
        }


        AccessibilityNodeInfo rootNode = null;
        String launcherPackage = getLauncherPackageName(); // 获取桌面启动器的包名
        boolean foregroundAppFound = false; // 标志变量，用于判断是否找到前台应用窗口
        int minChildNodes = 1; // 最小有效子节点数量

        for (AccessibilityWindowInfo window : windows) {
            // 首先，获取前台应用的根节点
            if (window.getType() == AccessibilityWindowInfo.TYPE_APPLICATION) {
                rootNode = window.getRoot();

                if (rootNode != null && rootNode.getChildCount() >= minChildNodes && rootNode.isVisibleToUser()) {
                    String packageName = rootNode.getPackageName() != null ? rootNode.getPackageName().toString() : "";
                    if (!packageName.isEmpty() && !packageName.equals(launcherPackage)) {
                        // 这是前台应用窗口，且子节点足够多
                        foregroundAppFound = true;
                        break; // 找到前台应用窗口后跳出循环
                    }
                }
            }
        }

// 如果没有找到前台应用，则获取桌面窗口
        if (!foregroundAppFound) {
            for (AccessibilityWindowInfo window : windows) {
                if (window.getType() == AccessibilityWindowInfo.TYPE_APPLICATION) {
                    rootNode = window.getRoot();

                    if (rootNode != null && rootNode.getChildCount() >= minChildNodes && rootNode.isVisibleToUser()) {
                        String packageName = rootNode.getPackageName() != null ? rootNode.getPackageName().toString() : "";
                        if (launcherPackage.equals(packageName) || packageName.isEmpty()) {
                            // 这是桌面窗口，且子节点足够多
                            break;
                        }
                    }
                }
            }
        }

        if (rootNode == null) {
            Log.e(TAG, "Root node is null! No suitable window found.");
            return;
        }

        myActiveRootNode = rootNode;
        Log.d(TAG, "我们找到的Root node是"+rootNode.getPackageName()+"_"+rootNode.getWindow().getTitle()+" Proceeding to save UI hierarchy...");

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

        boolean s = node.isImportantForAccessibility();
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
        serializer.attribute("", "scrollable",String.valueOf(node.isScrollable()));
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

    public String getLauncherPackageName() {
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

    public void simulateBackKey() {
        performGlobalAction(GLOBAL_ACTION_BACK);
    }






    public NodeInfoWrapper getCompressedNodeTree() {
        if (instance == null) {
            Log.e(TAG, "Instance is null! AccessibilityService not started?");
            return null;
        }
        List<AccessibilityWindowInfo> windows = getWindows();
        if (windows == null || windows.isEmpty()) {
            Log.e(TAG, "No windows found! Maybe no active UI?");
            return null;
        }

        for (AccessibilityWindowInfo window : windows) {
            Log.i(TAG,window.getTitle()+"_"+window.getRoot().getPackageName());
        }

        AccessibilityNodeInfo rootNode = null;
        String launcherPackage = getLauncherPackageName(); // 获取桌面启动器的包名
        boolean foregroundAppFound = false; // 标志变量，用于判断是否找到前台应用窗口
        int minChildNodes = 1; // 最小有效子节点数量

        for (AccessibilityWindowInfo window : windows) {
            // 首先，获取前台应用的根节点
            if (window.getType() == AccessibilityWindowInfo.TYPE_APPLICATION) {
                rootNode = window.getRoot();

                if (rootNode != null && rootNode.getChildCount() >= minChildNodes && rootNode.isVisibleToUser()) {
                    String packageName = rootNode.getPackageName() != null ? rootNode.getPackageName().toString() : "";
                    if (!packageName.isEmpty() && !packageName.equals(launcherPackage)) {
                        // 这是前台应用窗口，且子节点足够多
                        foregroundAppFound = true;
                        break; // 找到前台应用窗口后跳出循环
                    }
                }
            }
        }

// 如果没有找到前台应用，则获取桌面窗口
        if (!foregroundAppFound) {
            for (AccessibilityWindowInfo window : windows) {
                if (window.getType() == AccessibilityWindowInfo.TYPE_APPLICATION) {
                    rootNode = window.getRoot();

                    if (rootNode != null && rootNode.getChildCount() >= minChildNodes && rootNode.isVisibleToUser()) {
                        String packageName = rootNode.getPackageName() != null ? rootNode.getPackageName().toString() : "";
                        if (launcherPackage.equals(packageName) || packageName.isEmpty()) {
                            // 这是桌面窗口，且子节点足够多
                            break;
                        }
                    }
                }
            }
        }

        if (rootNode == null) {
            Log.e(TAG, "Root node is null! No suitable window found.");
            return null;
        }

        myActiveRootNode = rootNode;
        Log.d(TAG, "我们找到的Root node是"+rootNode.getPackageName()+"_"+rootNode.getWindow().getTitle()+" Proceeding to save UI hierarchy...");

        return copyImportantNode(rootNode);
    }
    private static NodeInfoWrapper copyImportantNode(AccessibilityNodeInfo node) {
        if (node == null) return null;

        float score = getInstance().scoreNode(node,getInstance().jsonObject);


        if (!isNodeImportant(node)) {
            // 自身不重要，但可能有重要的子节点
            List<NodeInfoWrapper> importantChildren = new ArrayList<>();
            for (int i = 0; i < node.getChildCount(); i++) {
                AccessibilityNodeInfo child = node.getChild(i);
                NodeInfoWrapper compressedChild = copyImportantNode(child);
                if (compressedChild != null) {
                    importantChildren.add(compressedChild);
                }
            }

            if (!importantChildren.isEmpty()) {
                return new NodeInfoWrapper(node, importantChildren, score);
            } else {
                return null;
            }
        } else {
            // 重要节点，连带重要子节点一起保留
            List<NodeInfoWrapper> importantChildren = new ArrayList<>();
            for (int i = 0; i < node.getChildCount(); i++) {
                AccessibilityNodeInfo child = node.getChild(i);
                NodeInfoWrapper compressedChild = copyImportantNode(child);
                if (compressedChild != null) {
                    importantChildren.add(compressedChild);
                }
            }
            return new NodeInfoWrapper(node, importantChildren, score);
        }
    }

    private static boolean isNodeImportant(AccessibilityNodeInfo node) {
        if (node == null) return false;

        if (!node.isVisibleToUser()) {
            return false;
        }

        if (node.getText() != null && node.getText().length() > 0) {
            return true;
        }

        if (node.getContentDescription() != null && node.getContentDescription().length() > 0) {
            return true;
        }

        if (node.isClickable() || node.isFocusable() || node.isEditable() || node.isScrollable() || node.isLongClickable()) {
            return true;
        }

        return false;
    }

    public static class NodeInfoWrapper {
        public AccessibilityNodeInfo node;

        public float score;
        public List<NodeInfoWrapper> children;

        public NodeInfoWrapper(AccessibilityNodeInfo node, List<NodeInfoWrapper> children, float score) {
            this.node = node;
            this.children = children;
            this.score=score;
        }
    }
    public void exportToXml(NodeInfoWrapper root, String saveDir, String prefix) {
        if (root == null) return;

        try {
            StringWriter writer = new StringWriter();
            XmlSerializer serializer = Xml.newSerializer();
            serializer.setOutput(writer);
            serializer.startDocument("UTF-8", true);

            writeNode(serializer, root, 0);

            serializer.endDocument();
            String xmlContent = writer.toString();

            // 写入文件
            if (saveDir != null && !saveDir.isEmpty()) {
                File file = new File(saveDir,prefix+".xml");
                if (!file.getParentFile().exists()) {
                    file.getParentFile().mkdirs(); // 创建父目录
                }
                FileOutputStream fos = new FileOutputStream(file);
                OutputStreamWriter osw = new OutputStreamWriter(fos, "UTF-8");
                osw.write(xmlContent);
                osw.flush();
                osw.close();
                fos.close();
            }

        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private static void writeNode(XmlSerializer serializer, NodeInfoWrapper nodeWrapper,int index) throws Exception {
        if (nodeWrapper == null || nodeWrapper.node == null) return;

        AccessibilityNodeInfo node = nodeWrapper.node;

        serializer.startTag("", "node");
        serializer.attribute("", "index", Integer.toString(index));
        serializer.attribute("", "text", node.getText() != null ? node.getText().toString() : "");
        serializer.attribute("", "resource-id", node.getViewIdResourceName() != null ? node.getViewIdResourceName() : "");
        serializer.attribute("", "class", node.getClassName() != null ? node.getClassName().toString() : "");
        serializer.attribute("", "package", node.getPackageName()!=null ? node.getPackageName().toString() : "");
        serializer.attribute("", "content-desc", node.getContentDescription() != null ? node.getContentDescription().toString() : "");
        serializer.attribute("", "checked", String.valueOf(node.isChecked()));
        serializer.attribute("", "enabled", String.valueOf(node.isEnabled()));
        serializer.attribute("", "focused", String.valueOf(node.isFocused()));
        serializer.attribute("", "scrollable",String.valueOf(node.isScrollable()));
        serializer.attribute("", "long-clickable", String.valueOf(node.isLongClickable()));
        serializer.attribute("", "password", String.valueOf(node.isPassword()));
        serializer.attribute("", "selected", String.valueOf(node.isSelected()));
        serializer.attribute("", "clickable", String.valueOf(node.isClickable()));
        serializer.attribute("", "focusable", String.valueOf(node.isFocusable()));
        serializer.attribute("", "longClickable", String.valueOf(node.isLongClickable()));
        serializer.attribute("", "visibleToUser", String.valueOf(node.isVisibleToUser()));
        serializer.attribute("","score", String.valueOf(nodeWrapper.score));


        Rect bounds = new Rect();
        node.getBoundsInScreen(bounds);
        serializer.attribute("", "bounds", getVisibleBoundsInScreen(node,new Rect(0, 0, width, height),false).toShortString());
        // 递归处理子节点
        List<NodeInfoWrapper> children = nodeWrapper.children;
        if (children != null) {
            for(int i = 0 ;i<children.size();i++){
                writeNode(serializer,children.get(i),i);
            }
        }

        serializer.endTag("", "node");
    }

    public float scoreNode(AccessibilityNodeInfo node, JSONObject configJson) {
        if (node == null || configJson == null) return 0f;

        // ===== 1. Extract node properties =====
        String text = node.getText() != null ? node.getText().toString() : "";
        String desc = node.getContentDescription() != null ? node.getContentDescription().toString() : "";
        String resourceId = node.getViewIdResourceName() != null ? node.getViewIdResourceName() : "";
        String className = node.getClassName() != null ? node.getClassName().toString() : "";

        float score = 0f;

        // ===== 2. Load keyword weights from JSON =====
        JSONObject weights = configJson.optJSONObject("weights");
        score += scoreKeywords(configJson.optJSONObject("critical"), text, desc, resourceId, 1.0f, weights);
        score += scoreKeywords(configJson.optJSONObject("important"), text, desc, resourceId, 0.9f, weights);
        score += scoreKeywords(configJson.optJSONObject("general"), text, desc, resourceId, 0.7f, weights);

        // ===== 3. Widget class name scoring =====
        JSONArray widgetScores = configJson.optJSONArray("widgetScores");
        if (widgetScores != null) {
            for (int i = 0; i < widgetScores.length(); i++) {
                JSONObject widgetObj = widgetScores.optJSONObject(i);
                if (widgetObj == null) continue;
                JSONArray classNames = widgetObj.optJSONArray("classNames");
                float widgetScore = (float) widgetObj.optDouble("score", 0.0);
                if (classNames != null) {
                    for (int j = 0; j < classNames.length(); j++) {
                        String cls = classNames.optString(j, "");
                        if (!cls.isEmpty() && className.contains(cls)) {
                            score += widgetScore;
                            break;
                        }
                    }
                }
            }
        }

        // ===== 4. Apply clickable and visibility weights =====
        if (weights != null) {
            if (node.isClickable()) {
                float clickableWeight = (float) weights.optDouble("clickable", 1.0);
                score *= clickableWeight;
            }
            if (node.isVisibleToUser()) {
                float visibleWeight = (float) weights.optDouble("visible", 1.0);
                score *= visibleWeight;
            }
        }

        return score;
    }

    private float scoreKeywords(JSONObject keywords, String text, String desc, String resourceId, float matchFactor, JSONObject weights) {
        float score = 0f;
        if (keywords == null) return score;

        // 从 weights 中读取匹配权重（如没有就使用默认值）
        float exactWeight = weights != null ? (float) weights.optDouble("exactMatch", 1.0) : 1.0f;
        float caseInsensitiveWeight = weights != null ? (float) weights.optDouble("caseInsensitiveMatch", 0.9) : 0.9f;
        float partialWeight = weights != null ? (float) weights.optDouble("partialMatch", matchFactor) : matchFactor;

        Iterator<String> keys = keywords.keys();
        while (keys.hasNext()) {
            String keyword = keys.next();
            double weight = keywords.optDouble(keyword, 0.0);

            if (text.equals(keyword) || desc.equals(keyword)) {
                score += weight * exactWeight;
            } else if (text.equalsIgnoreCase(keyword) || desc.equalsIgnoreCase(keyword)) {
                score += weight * caseInsensitiveWeight;
            } else if (text.contains(keyword) || desc.contains(keyword) || resourceId.contains(keyword)) {
                score += weight * partialWeight;
            }
        }

        return score;
    }






}
