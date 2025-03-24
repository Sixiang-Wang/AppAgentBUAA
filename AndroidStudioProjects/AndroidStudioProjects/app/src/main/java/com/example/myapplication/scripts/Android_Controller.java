package com.example.myapplication.scripts;

import static com.example.myapplication.scripts.config.loadConfig;

import android.app.Activity;
import android.content.Context;
import android.graphics.Point;
import android.view.WindowMetrics;

import com.example.myapplication.MyAccessibilityService;
import com.example.myapplication.services.MediaProjectionService;
import com.example.myapplication.util.TaskPool;
import com.example.myapplication.util.ToastUtils;

import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.NamedNodeMap;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

import java.io.File;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;


public class Android_Controller{
    Context context;
    int width;
    int height;
    String screenshot_dir;
    String xml_dir;
    String backslash;

    double MIN_DIST;
    public Android_Controller(Context context){
        this.context=context;
        this.width=this.get_device_size().x;
        this.height=this.get_device_size().y;
        Map<String,String> configs = loadConfig(this.context);
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
    public String get_screenshot(String prefix,String save_dir){
        TaskPool.CACHE.execute(() -> {
            try {
                Thread.sleep(200L);
                TaskPool.MAIN.post(() -> MediaProjectionService.screenshot(prefix, save_dir));
                Thread.sleep(200L);
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
        });
        return save_dir+"/"+prefix;
    }

    public String get_xml(int round_count, File task_dir){
        if(MyAccessibilityService.getInstance()!=null){
            ToastUtils.longCall("保存开始");
            String round_count_string = String.valueOf(round_count);
            new Thread(() -> {
                try {
                    Thread.sleep(5000); // 先等待 5 秒,防止你来不及换到其他应用
                } catch (InterruptedException e) {
                    e.printStackTrace();
                }
                MyAccessibilityService.getInstance().saveUiHierarchy(round_count_string, task_dir.getAbsolutePath());
                ToastUtils.longCall("保存完成！");
            }).start();
            return task_dir.getAbsolutePath()+"/"+round_count_string+".xml";
        }else{
            ToastUtils.longCall("无障碍未启动！");
            return null;
        }
    }

    public void traverseTree(String xmlPath, List<Android_Element> elemList, String attrib, boolean addIndex) {
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
    private void parseElement(Element elem, List<Element> path, List<Android_Element> elemList, String attrib, boolean addIndex) {
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
            for (Android_Element e : elemList) {
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

                elemList.add(new Android_Element(elemId, bbox, attributes));
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
        String[] parts = bounds.replace("[", "").replace("]", "").split("\\]\\[");
        String[] p1 = parts[0].split(",");
        String[] p2 = parts[1].split(",");
        return new int[]{Integer.parseInt(p1[0]), Integer.parseInt(p1[1]), Integer.parseInt(p2[0]), Integer.parseInt(p2[1])};
    }

    private static String getIdFromElement(Element elem) {
        return elem.getAttribute("class").replace(".", "_").toLowerCase();
    }








}
