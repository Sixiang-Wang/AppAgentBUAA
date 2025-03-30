package com.example.myapplication.scripts;

import static com.example.myapplication.scripts.config.loadConfig;

import android.accessibilityservice.InputMethod;
import android.accessibilityservice.InputMethod.AccessibilityInputConnection;
import android.view.accessibility.AccessibilityWindowInfo;
import android.accessibilityservice.AccessibilityService;
import android.accessibilityservice.GestureDescription;
import android.app.Activity;
import android.content.Context;
import android.graphics.BitmapFactory;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.Point;
import android.graphics.Rect;
import android.os.Build;
import android.os.Bundle;
import android.util.Log;
import android.view.WindowMetrics;

import androidx.annotation.RequiresApi;
import com.example.myapplication.myAccessibilityService;
import com.example.myapplication.services.MediaProjectionService;
import com.example.myapplication.util.TaskPool;
import com.example.myapplication.util.ToastUtils;
import static com.example.myapplication.activity.MainActivity.TAG;

import org.json.JSONException;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.NamedNodeMap;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CountDownLatch;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import android.app.UiAutomation;
import android.view.accessibility.AccessibilityNodeInfo;
import android.view.accessibility.AccessibilityWindowInfo;

public class androidController {
    Context context;
    public static int width;
    public static int height;
    String screenshot_dir;
    String xml_dir;
    String backslash;

    double MIN_DIST;
    public androidController(Context context){
        this.context=context;
        width=this.get_device_size().x;
        height=this.get_device_size().y;
        Map<String,String> configs = loadConfig(this.context,"config.json");
        this.screenshot_dir = configs.get("ANDROID_SCREENSHOT_DIR");
        this.xml_dir = configs.get("ANDROID_XML_DIR");
        this.backslash="\\";
        this.MIN_DIST = Double.parseDouble(configs.get("MIN_DIST"));
    }
    public Point get_device_size(){
        WindowMetrics metrics = ((Activity) context).getWindowManager().getCurrentWindowMetrics();
        return new Point(metrics.getBounds().width(),metrics.getBounds().height());
    }

    //prefix 应该是形如1_before.png的东西
    public String get_screenshot(String prefix, String save_dir) {

        MediaProjectionService.screenshot(prefix, save_dir);

        return save_dir + "/" + prefix;

    }

    public String get_xml(int round_count, File task_dir){
        if(myAccessibilityService.getInstance()!=null){
            ToastUtils.longCall("保存开始");
            String round_count_string = String.valueOf(round_count);
            myAccessibilityService.getInstance().saveUiHierarchy(round_count_string, task_dir.getAbsolutePath());
            return task_dir.getAbsolutePath()+"/"+round_count_string+".xml";
        }else{
            ToastUtils.longCall("无障碍未启动！");
            return null;
        }
    }

    public void traverseTree(String xmlPath, List<androidElement> elemList, String attrib, boolean addIndex) {
        try {
            File inputFile = new File(xmlPath);
            DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
            DocumentBuilder builder = factory.newDocumentBuilder();
            Document doc = builder.parse(inputFile);
            doc.getDocumentElement().normalize();

            List<Element> path = new ArrayList<>();
            parseElement(doc.getDocumentElement(), path, elemList, attrib, addIndex);

        } catch (Exception e) {
            e.printStackTrace();
        }
    }
    private void parseElement(Element elem, List<Element> path, List<androidElement> elemList, String attrib, boolean addIndex) {
        path.add(elem);

        if (elem.hasAttribute(attrib) && elem.getAttribute(attrib).equals("true")) {
            String parentPrefix = "";
            if (path.size() > 1) {
                parentPrefix = getIdFromElement(path.get(path.size() - 2));
            }

            String boundsStr = elem.getAttribute("bounds");
            int[] bbox = parseBounds(boundsStr);
            int centerX = (bbox[0] + bbox[2]) / 2;
            int centerY = (bbox[1] + bbox[3]) / 2;

            String elemId = getIdFromElement(elem);
            if (!parentPrefix.isEmpty()) {
                elemId = parentPrefix + "_" + elemId;
            }
            if (addIndex) {
                elemId += "_" + elem.getAttribute("index");
            }

            boolean close = false;
            for (androidElement e : elemList) {
                int[] bbox2 = e.bbox;
                int centerX2 = (bbox2[0] + bbox2[2]) / 2;
                int centerY2 = (bbox2[1] + bbox2[3]) / 2;
                double dist = Math.sqrt(Math.pow(centerX - centerX2, 2) + Math.pow(centerY - centerY2, 2));

                if (dist <= MIN_DIST) {
                    close = true;
                    break;
                }
            }

            if (!close) {
                Map<String, String> attributes = new HashMap<>();
                NamedNodeMap attrMap = elem.getAttributes();
                for (int i = 0; i < attrMap.getLength(); i++) {
                    Node node = attrMap.item(i);
                    attributes.put(node.getNodeName(), node.getNodeValue());
                }
                elemList.add(new androidElement(elemId, bbox, attrib));
            }
        }

        NodeList children = elem.getChildNodes();
        for (int i = 0; i < children.getLength(); i++) {
            Node node = children.item(i);
            if (node.getNodeType() == Node.ELEMENT_NODE) {
                parseElement((Element) node, path, elemList, attrib, addIndex);
            }
        }
        path.remove(path.size() - 1);
    }

    private int[] parseBounds(String bounds) {
        String[] parts = bounds.split("]\\["); // 直接拆分
        parts[0] = parts[0].replace("[", ""); // 去掉第一个 "["
        parts[1] = parts[1].replace("]", ""); // 去掉第二个 "]"
        String[] p1 = parts[0].split(",");
        String[] p2 = parts[1].split(",");

        return new int[]{Integer.parseInt(p1[0]), Integer.parseInt(p1[1]), Integer.parseInt(p2[0]), Integer.parseInt(p2[1])};
    }

    private String getIdFromElement(Element elem) {
        String boundsStr = elem.getAttribute("bounds");
        int[] bbox = parseBounds(boundsStr);
        int elemW = bbox[2] - bbox[0];
        int elemH = bbox[3] - bbox[1];
        String elemId;
        if (elem.hasAttribute("resource-id") && !elem.getAttribute("resource-id").isEmpty()) {
            elemId = elem.getAttribute("resource-id").replace(":", ".").replace("/", "_");
        } else {
            elemId = elem.getAttribute("class") + "_" + elemW + "_" + elemH;
        }

        if (elem.hasAttribute("content-desc") && !elem.getAttribute("content-desc").isEmpty() &&
                elem.getAttribute("content-desc").length() < 20) {
            String contentDesc = elem.getAttribute("content-desc")
                    .replace("/", "_")
                    .replace(" ", "")
                    .replace(":", "_");
            elemId += "_" + contentDesc;
        }
        return elemId;
    }
    public void drawBoundingBoxes(String img_path,
                                           String output_path,
                                           List<androidElement> elemList,
                                           boolean recordMode,
                                           boolean darkMode) {
        Bitmap originalBitmap = BitmapFactory.decodeFile(img_path);
        Bitmap mutableBitmap = originalBitmap.copy(Bitmap.Config.ARGB_8888, true);
        Canvas canvas = new Canvas(mutableBitmap);
        Paint textPaint = new Paint();
        textPaint.setTextSize(40);
        textPaint.setAntiAlias(true);
        Paint backgroundPaint = new Paint();
        backgroundPaint.setStyle(Paint.Style.FILL);
        int count = 1;

        for (androidElement elem : elemList) {
            try {
                int left = elem.bbox[0];
                int top = elem.bbox[1];
                int right = elem.bbox[2];
                int bottom = elem.bbox[3];
                // 选择边界框颜色
                if (recordMode) {
                    if ("clickable".equals(elem.attrib)) {
                        backgroundPaint.setColor(Color.RED);
                    } else if ("focusable".equals(elem.attrib)) {
                        backgroundPaint.setColor(Color.BLUE);
                    } else {
                        backgroundPaint.setColor(Color.GREEN);
                    }
                } else {
                    backgroundPaint.setColor(Color.BLACK);
                }

                // 选择文本颜色
                textPaint.setColor(darkMode ? Color.BLACK : Color.WHITE);

                // 计算文本背景的矩形区域
                String label = String.valueOf(count);
                Rect textBounds = new Rect();
                textPaint.getTextBounds(label, 0, label.length(), textBounds);

                int textX = (left + right) / 2 + 10;
                int textY = (top + bottom) / 2 + 10;
                int padding = 10;

                // 绘制文本背景
                canvas.drawRect(textX - padding,
                        textY - textBounds.height() - padding,
                        textX + textBounds.width() + padding,
                        textY + padding, backgroundPaint);

                // 绘制文本
                canvas.drawText(label, textX, textY, textPaint);
            } catch (Exception e) {
                System.out.println("ERROR: Exception occurred while labeling the image\n" + e.getMessage());
            }
            count++;
        }

        try {
            File file = new File(output_path);
            FileOutputStream out = new FileOutputStream(file);
            mutableBitmap.compress(Bitmap.CompressFormat.PNG, 100, out);
            out.flush();
            out.close();
        } catch (IOException e) {
            e.printStackTrace();
        }

    }

    @RequiresApi(api = Build.VERSION_CODES.N)
    public void tap(int x, int y) {
        if (myAccessibilityService.getInstance() == null) {
            Log.e(TAG, "无障碍服务实例为空，无法执行点击！");
            return;
        }

        Log.d(TAG, "开始执行点击: (" + x + ", " + y + ")");

        Path path = new Path();
        path.moveTo(x, y);
        GestureDescription.StrokeDescription stroke = new GestureDescription.StrokeDescription(path, 100, 300);
        GestureDescription.Builder builder = new GestureDescription.Builder();
        builder.addStroke(stroke);
        boolean success = myAccessibilityService.getInstance().dispatchGesture(
                builder.build(),
                new AccessibilityService.GestureResultCallback() {
                    @Override
                    public void onCompleted(GestureDescription gestureDescription) {
                        Log.d(TAG, "点击成功");
                    }
                    @Override
                    public void onCancelled(GestureDescription gestureDescription) {
                        Log.e(TAG, "点击被取消！");
                    }
                },
                null
        );

        Log.d(TAG, "dispatchGesture 返回值: " + success);
    }

    public void text(String inputText){
        Ut.text(inputText);
    }

//    public void text(String inputText) {
//        if (myAccessibilityService.getInstance() == null) {
//            Log.e(TAG, "无障碍服务实例为空，无法执行输入！");
//            return;
//        }
//        InputMethod inputMethod = new InputMethod(myAccessibilityService.getInstance());;
//        if (inputMethod == null) {
//            Log.e(TAG, "输入法实例为空，无法执行输入！");
//            return;
//        }
//        AccessibilityInputConnection connection = inputMethod.getCurrentInputConnection();
//        if (connection == null) {
//            Log.e(TAG, "当前输入连接为空，无法执行输入！");
//            return;
//        }
//        connection.commitText(inputText, 1, null);  // 0表示光标位置
//    }























}
