package com.example.myapplication.scripts;

import static com.example.myapplication.scripts.config.loadConfig;

import android.accessibilityservice.InputMethod;
import android.accessibilityservice.InputMethod.AccessibilityInputConnection;
import android.graphics.Typeface;
import android.util.Xml;
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

import androidx.test.platform.app.InstrumentationRegistry;
import androidx.test.uiautomator.UiDevice;

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
import org.xmlpull.v1.XmlSerializer;

import java.io.BufferedWriter;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.FileWriter;
import java.io.IOException;
import java.util.AbstractMap;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import android.app.UiAutomation;
import android.view.accessibility.AccessibilityNodeInfo;
import android.view.accessibility.AccessibilityWindowInfo;

public class androidController {
    Context context;
    public static int width=1080;
    public static int height=2000;
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

    public String get_xml_2(String prefix, File task_dir){
        if(myAccessibilityService.getInstance()!=null){
            ToastUtils.shortCall("保存开始");
            String round_count_string = String.valueOf(prefix);

            myAccessibilityService.NodeInfoWrapper root = myAccessibilityService.getInstance().getCompressedNodeTree();
            myAccessibilityService.getInstance().exportToXml(root,task_dir.getAbsolutePath(),round_count_string);

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
                float score = Float.parseFloat(elem.getAttribute("score"));
                elemList.add(new androidElement(elemId, bbox, attrib,score,attributes));
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
//    public void drawBoundingBoxes(String img_path,
//                                  String output_path,
//                                  List<androidElement> elemList,
//                                  boolean recordMode,
//                                  boolean darkMode) {
//        Bitmap originalBitmap = BitmapFactory.decodeFile(img_path);
//        Bitmap mutableBitmap = originalBitmap.copy(Bitmap.Config.ARGB_8888, true);
//        Canvas canvas = new Canvas(mutableBitmap);
//        Paint textPaint = new Paint();
//        textPaint.setTextSize(60);  // 增大文字的大小
//        textPaint.setAntiAlias(true);
//
//        // 为了增强对比度，给文字设置阴影
//        textPaint.setShadowLayer(10f, 5f, 5f, Color.BLACK);  // 阴影效果，黑色阴影使文字更清晰
//
//        Paint borderPaint = new Paint();
//        borderPaint.setStyle(Paint.Style.STROKE);
//        borderPaint.setStrokeWidth(5);  // 设置边框的线宽
//        int count = 1;
//
//        for (androidElement elem : elemList) {
//            try {
//                int left = elem.bbox[0];
//                int top = elem.bbox[1];
//                int right = elem.bbox[2];
//                int bottom = elem.bbox[3];
//
//                // 选择边框颜色
//                if (recordMode) {
//                    if ("clickable".equals(elem.attrib)) {
//                        borderPaint.setColor(Color.RED);
//                    } else if ("focusable".equals(elem.attrib)) {
//                        borderPaint.setColor(Color.BLUE);
//                    } else {
//                        borderPaint.setColor(Color.GREEN);
//                    }
//                } else {
//                    borderPaint.setColor(Color.BLACK);  // 默认为黑色
//                }
//
//                // 绘制边框
//                canvas.drawRect(left, top, right, bottom, borderPaint);
//
//                // 选择文本颜色，使用高对比度颜色
//                textPaint.setColor(Color.YELLOW);  // 使用黄色高对比度文字
//
//                // 计算文本位置：将文本放在边界框的正中央
//                String label = String.valueOf(count);
//                float textWidth = textPaint.measureText(label);  // 计算文字宽度
//                float textX = (left + right) / 2 - textWidth / 2;  // 水平居中
//                float textY = (top + bottom) / 2 + (textPaint.getTextSize() / 2);  // 垂直居中
//
//                // 绘制数字
//                canvas.drawText(label, textX, textY, textPaint);
//            } catch (Exception e) {
//                System.out.println("ERROR: Exception occurred while labeling the image\n" + e.getMessage());
//            }
//            count++;
//        }
//
//        try {
//            File file = new File(output_path);
//            FileOutputStream out = new FileOutputStream(file);
//            mutableBitmap.compress(Bitmap.CompressFormat.PNG, 100, out);
//            out.flush();
//            out.close();
//        } catch (IOException e) {
//            e.printStackTrace();
//        }
//    }
    public void drawBoundingBoxes(String img_path,
                                  String output_path,
                                  List<androidElement> elemList,
                                  boolean recordMode,
                                  boolean darkMode) {
        Bitmap originalBitmap = BitmapFactory.decodeFile(img_path);
        Bitmap mutableBitmap = originalBitmap.copy(Bitmap.Config.ARGB_8888, true);
        Canvas canvas = new Canvas(mutableBitmap);
        Paint textPaint = new Paint();
        textPaint.setTextSize(60);
        textPaint.setFakeBoldText(true);
        textPaint.setAntiAlias(true);
        textPaint.setTypeface(Typeface.DEFAULT_BOLD);
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

                int textX = (left + right) / 2 ;
                int textY = (top + bottom) / 2 ;
                int padding = 15;

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

    public void tap(int x, int y) {
        if (myAccessibilityService.getInstance() == null) {
            Log.e(TAG, "无障碍服务实例为空，无法执行点击！");
            return;
        }
        Log.d(TAG, "开始执行点击: (" + x + ", " + y + ")");
        Path path = new Path();
        path.moveTo(x, y);
        GestureDescription.StrokeDescription stroke = new GestureDescription.StrokeDescription(path, 0, 100);
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
    public void longPress(int x, int y) {
        if (myAccessibilityService.getInstance() == null) {
            Log.e(TAG, "无障碍服务实例为空，无法执行点击！");
            return;
        }
        Log.d(TAG, "开始执行点击: (" + x + ", " + y + ")");
        Path path = new Path();
        path.moveTo(x, y);
        GestureDescription.StrokeDescription stroke = new GestureDescription.StrokeDescription(path, 0, 1000);
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


    public void swipe(int x, int y, String direction, String dist, boolean quick) {
        Log.d(TAG, "Begin to swipe " + x + " " + y + " " + direction + " " + dist + " " + quick);

        int width = androidController.width;
        int unitDist = (width / 10) * (dist.equals("long") ? 3 : dist.equals("medium") ? 2 : 1);

        int offsetX = 0, offsetY = 0;
        switch (direction) {
            case "up":
                offsetX = 0;
                offsetY = -2 * unitDist;
                break;
            case "down":
                offsetX = 0;
                offsetY = 2 * unitDist;
                break;
            case "left":
                offsetX = -unitDist;
                offsetY = 0;
                break;
            case "right":
                offsetX = unitDist;
                offsetY = 0;
                break;
            default:
                throw new IllegalArgumentException("Invalid direction " + direction);
        }

        long duration = quick ? 100 : 400;

        Path path = new Path();
        path.moveTo(x, y);
        path.rLineTo(offsetX, offsetY);

        GestureDescription.StrokeDescription stroke = new GestureDescription.StrokeDescription(path, 0, duration);
        GestureDescription gesture = new GestureDescription.Builder().addStroke(stroke).build();

        if (myAccessibilityService.getInstance() != null) {
            myAccessibilityService.getInstance().dispatchGesture(gesture, null, null);
        }

        Log.d(TAG, "End to swipe " + x + " " + y + " " + direction + " " + dist + " " + quick);
    }

    public void swipePrecise(int startX, int startY, int endX, int endY, long duration) {
        Log.d(TAG, "Begin precise swipe from (" + startX + ", " + startY + ") to (" + endX + ", " + endY + ") with duration " + duration);

        Path path = new Path();
        path.moveTo(startX, startY);
        path.lineTo(endX, endY);

        GestureDescription.StrokeDescription stroke = new GestureDescription.StrokeDescription(path, 0, duration);
        GestureDescription gesture = new GestureDescription.Builder().addStroke(stroke).build();

        if (myAccessibilityService.getInstance() != null) {
            myAccessibilityService.getInstance().dispatchGesture(gesture, null, null);
        }

        Log.d(TAG, "End precise swipe from (" + startX + ", " + startY + ") to (" + endX + ", " + endY + ")");
    }






    public void back(){
        myAccessibilityService.getInstance().simulateBackKey();
    }

    public Point drawGrid(String imgPath, String outputPath) {
        // 读取图片
        Bitmap bitmap = BitmapFactory.decodeFile(imgPath);
        if (bitmap == null) {
            Log.e("GridDrawer", "无法读取图像: " + imgPath);
            return new Point(-1, -1);
        }

        int height = bitmap.getHeight();
        int width = bitmap.getWidth();
        int unitHeight = getUnitLen(height);
        int unitWidth = getUnitLen(width);
        if (unitHeight < 0) unitHeight = 120;
        if (unitWidth < 0) unitWidth = 120;

        Bitmap mutableBitmap = bitmap.copy(Bitmap.Config.ARGB_8888, true);
        Canvas canvas = new Canvas(mutableBitmap);
        Paint paint = new Paint();
        paint.setColor(Color.rgb(255, 116, 113)); // 颜色 (R, G, B)
        paint.setStyle(Paint.Style.STROKE);
        paint.setStrokeWidth(Math.max(1, unitWidth / 50));

        Paint textPaint = new Paint();
        textPaint.setColor(Color.BLACK);
        textPaint.setTextSize(unitWidth / 10f);
        textPaint.setAntiAlias(true);

        int rows = height / unitHeight;
        int cols = width / unitWidth;

        for (int i = 0; i < rows; i++) {
            for (int j = 0; j < cols; j++) {
                int label = i * cols + j + 1;
                int left = j * unitWidth;
                int top = i * unitHeight;
                int right = (j + 1) * unitWidth;
                int bottom = (i + 1) * unitHeight;

                // 画矩形
                canvas.drawRect(left, top, right, bottom, paint);

                // 画编号（黑色阴影）
                canvas.drawText(String.valueOf(label), left + unitWidth * 0.05f + 3, top + unitHeight * 0.3f + 3, textPaint);
                // 画编号（原色）
                textPaint.setColor(Color.rgb(255, 116, 113));
                canvas.drawText(String.valueOf(label), left + unitWidth * 0.05f, top + unitHeight * 0.3f, textPaint);
                textPaint.setColor(Color.BLACK); // 复原颜色
            }
        }

        File file = new File(outputPath);
        try (FileOutputStream out = new FileOutputStream(file)) {
            bitmap.compress(Bitmap.CompressFormat.PNG, 100, out);
            out.flush();
            Log.d("GridDrawer", "图片保存成功: " + outputPath);
        } catch (IOException e) {
            Log.e("GridDrawer", "图片保存失败: " + e.getMessage());
        }

        return new Point(rows, cols); // 返回 rows 和 cols
    }


    private int getUnitLen(int n) {
        for (int i = 1; i <= n; i++) {
            if (n % i == 0 && i >= 120 && i <= 180) {
                return i;
            }
        }
        return -1;
    }


    public ArrayList<Map.Entry<String, Boolean>> parseSteps(String taskDescription) {
        // 创建一个列表来存储步骤
        ArrayList<Map.Entry<String, Boolean>> steps = new ArrayList<>();

        // 正则表达式来匹配 "Step X: <action>"
        String regex = "(Step \\d+: .+)";
        Pattern pattern = Pattern.compile(regex);
        Matcher matcher = pattern.matcher(taskDescription);

        // 查找并提取步骤
        while (matcher.find()) {
            // 提取步骤描述并去除多余空格，然后添加到列表中，Boolean 默认为 false
            steps.add(new AbstractMap.SimpleEntry<>(matcher.group(1).trim(), false));
        }

        return steps;
    }


    public static String task_and_status(Map.Entry<String, Boolean> task_status){
        if(task_status.getValue()){
            return task_status.getKey()+"(Completed)";
        }else{
            return task_status.getKey()+"(Not Completed)";
        }
    }

    public static String tasks_and_status(ArrayList<Map.Entry<String, Boolean>> tasks){
        StringBuilder sb=new StringBuilder();
        for(Map.Entry<String, Boolean> task:tasks){
            sb.append(task_and_status(task));
            sb.append("\n");
        }
        return sb.toString();
    }

    public static String completed_tasks_and_status(ArrayList<Map.Entry<String, Boolean>> tasks,int current_task_number){
        if(current_task_number==1){
            return "None";
        }else{
            StringBuilder sb=new StringBuilder();
            for(int i=1;i<=current_task_number-1;i++){
                sb.append(task_and_status(tasks.get(i-1)));
                sb.append("\n");
            }
            return sb.toString();
        }
    }

    public static void appendToFile(File filePath, String prompt) {
        BufferedWriter writer = null;
        try {
            // 如果文件不存在，创建文件
            if (!filePath.exists()) {
                filePath.createNewFile();
            }

            // 创建FileWriter对象，传入true表示追加模式
            writer = new BufferedWriter(new FileWriter(filePath, true));
            // 将prompt内容写入文件
            writer.write(prompt);
            // 换行
            writer.newLine();
        } catch (IOException e) {
            e.printStackTrace();
        } finally {
            // 关闭writer
            try {
                if (writer != null) {
                    writer.close();
                }
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
    }



























}
