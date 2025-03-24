package org.example.scripts;

import java.io.*;
import java.util.*;
import org.w3c.dom.*;
import javax.xml.parsers.*;
import org.xml.sax.InputSource;

public class AndroidController {
    private String device;
    private String screenshotDir;
    private String xmlDir;
    private int width;
    private int height;
    private String backslash = "\\";

    public AndroidController(String device) {
        this.device = device;
        this.screenshotDir = Config.loadConfig().get("ANDROID_SCREENSHOT_DIR");
        this.xmlDir = Config.loadConfig().get("ANDROID_XML_DIR");
        this.width = getDeviceSize().get(0);
        this.height = getDeviceSize().get(1);
    }

    private List<Integer> getDeviceSize() {
        String adbCommand = "adb -s " + device + " shell wm size";
        String result = executeAdb(adbCommand);
        if (!result.equals("ERROR")) {
            String[] parts = result.split(": ")[1].split("x");
            return Arrays.asList(Integer.parseInt(parts[0]), Integer.parseInt(parts[1]));
        }
        return Arrays.asList(0, 0);
    }

    private String executeAdb(String adbCommand) {
        try {
            Process process = Runtime.getRuntime().exec(adbCommand);
            BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()));
            StringBuilder output = new StringBuilder();
            String line;
            while ((line = reader.readLine()) != null) {
                output.append(line).append("\n");
            }
            int exitCode = process.waitFor();
            if (exitCode == 0) {
                return output.toString().strip();
            }
            printWithColor("Command execution failed: " + adbCommand, "red");
            return "ERROR";
        } catch (IOException | InterruptedException e) {
            printWithColor("Command execution failed: " + adbCommand, "red");
            e.printStackTrace();
            return "ERROR";
        }
    }

    public String getScreenshot(String prefix, String saveDir) {
        String capCommand = "adb -s " + device + " shell screencap -p " +
                (screenshotDir + "\\" + prefix + ".png").replace(backslash, "/");
        String pullCommand = "adb -s " + device + " pull " +
                (screenshotDir + "\\" + prefix + ".png").replace(backslash, "/") +
                " " + (saveDir + "\\" + prefix + ".png").replace(backslash, "/");
        String result = executeAdb(capCommand);
        if (!result.equals("ERROR")) {
            result = executeAdb(pullCommand);
            if (!result.equals("ERROR")) {
                return saveDir + "\\" + prefix + ".png";
            }
            return result;
        }
        return result;
    }

    public String getXml(String prefix, String saveDir) {
        String dumpCommand = "adb -s " + device + " shell uiautomator dump " +
                (xmlDir + "\\" + prefix + ".xml").replace(backslash, "/");
        String pullCommand = "adb -s " + device + " pull " +
                (xmlDir + "\\" + prefix + ".xml").replace(backslash, "/") +
                " " + (saveDir + "\\" + prefix + ".xml").replace(backslash, "/");
        String result = executeAdb(dumpCommand);
        if (!result.equals("ERROR")) {
            result = executeAdb(pullCommand);
            if (!result.equals("ERROR")) {
                return saveDir + "\\" + prefix + ".xml";
            }
            return result;
        }
        return result;
    }

    public String back() {
        String adbCommand = "adb -s " + device + " shell input keyevent KEYCODE_BACK";
        return executeAdb(adbCommand);
    }

    public String tap(int x, int y) {
        String adbCommand = "adb -s " + device + " shell input tap " + x + " " + y;
        return executeAdb(adbCommand);
    }

    public String text(String inputStr) {
        inputStr = inputStr.replace(" ", "%s");
        inputStr = inputStr.replace("'", "");
        String adbCommand = "adb -s " + device + " shell input text " + inputStr;
        return executeAdb(adbCommand);
    }

    public String longPress(int x, int y, int duration) {
        String adbCommand = "adb -s " + device + " shell input swipe " + x + " " + y + " " + x + " " + y + " " + duration;
        return executeAdb(adbCommand);
    }

    public String swipe(int x, int y, String direction, String dist, boolean quick) {
        int unitDist = width / 10;
        if (dist.equals("long")) {
            unitDist *= 3;
        } else if (dist.equals("medium")) {
            unitDist *= 2;
        }
        int duration = quick ? 100 : 400;
        String offset = "";
        switch (direction) {
            case "up":
                offset = "0, " + (-2 * unitDist);
                break;
            case "down":
                offset = "0, " + (2 * unitDist);
                break;
            case "left":
                offset = (-unitDist) + ", 0";
                break;
            case "right":
                offset = unitDist + ", 0";
                break;
            default:
                return "ERROR";
        }
        String adbCommand = "adb -s " + device + " shell input swipe " + x + " " + y + " " + offset + " " + duration;
        return executeAdb(adbCommand);
    }

    public String swipePrecise(int[] start, int[] end, int duration) {
        String adbCommand = "adb -s " + device + " shell input swipe " +
                start[0] + " " + start[1] + " " + end[0] + " " + end[1] + " " + duration;
        return executeAdb(adbCommand);
    }

    public static class AndroidElement {
        public String uid;
        public int[] bbox;
        public String attrib;

        public AndroidElement(String uid, int[] bbox, String attrib) {
            this.uid = uid;
            this.bbox = bbox;
            this.attrib = attrib;
        }
    }

    private static String getIdFromElement(Element elem) {
        String[] bounds = elem.getAttribute("bounds").substring(1, elem.getAttribute("bounds").length() - 1).split("]\\[");

        String[] xy1 = bounds[0].split(",");
        String[] xy2 = bounds[1].split(",");

        int x1 = Integer.parseInt(xy1[0]);
        int y1 = Integer.parseInt(xy1[1]);
        int x2 = Integer.parseInt(xy2[0]);
        int y2 = Integer.parseInt(xy2[1]);

        int elemWidth = x2 - x1;
        int elemHeight = y2 - y1;
        String elemId = elem.getAttribute("resource-id").replace(":", ".").replace("/", "_");
        if (elemId.isEmpty()) {
            elemId = elem.getAttribute("class") + "_" + elemWidth + "_" + elemHeight;
        }

        if (elem.getAttribute("content-desc").length() < 20) {
            elemId += "_" + elem.getAttribute("content-desc").replace("/", "_").replace(" ", "").replace(":", "_");
        }

        return elemId;
    }

    public static void traverseTree(String xmlPath, List<AndroidElement> elemList, String attrib, boolean addIndex) {
        try {
            DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
            DocumentBuilder builder = factory.newDocumentBuilder();
            Document doc = builder.parse(new InputSource(new FileReader(xmlPath)));
            NodeList nodeList = doc.getElementsByTagName("*");

            for (int i = 0; i < nodeList.getLength(); i++) {
                Element elem = (Element) nodeList.item(i);
                if (elem.getAttribute(attrib).equals("true")) {
                    String parentPrefix = "";
                    Node parent = elem.getParentNode();
                    if (parent != null && parent.getNodeType() == Node.ELEMENT_NODE) {
                        parentPrefix = getIdFromElement((Element) parent);
                    }
                    String elemId = getIdFromElement(elem);
                    if (!parentPrefix.isEmpty()) {
                        elemId = parentPrefix + "_" + elemId;
                    }

                    if (addIndex) {
                        elemId += "_" + elem.getAttribute("index");
                    }

                    int[] bounds = new int[]{Integer.parseInt(elem.getAttribute("bounds").split(",")[0]),
                            Integer.parseInt(elem.getAttribute("bounds").split(",")[1]),
                            Integer.parseInt(elem.getAttribute("bounds").split(",")[2]),
                            Integer.parseInt(elem.getAttribute("bounds").split(",")[3])};

                    boolean close = false;
                    for (AndroidElement e : elemList) {
                        int[] bbox = e.bbox;
                        int dist = (int) Math.sqrt(Math.pow(bounds[0] - bbox[0], 2) + Math.pow(bounds[1] - bbox[1], 2));
                        if (dist <= Integer.parseInt(Config.loadConfig().get("MIN_DIST"))) {
                            close = true;
                            break;
                        }
                    }

                    if (!close) {
                        elemList.add(new AndroidElement(elemId, bounds, attrib));
                    }
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private static void printWithColor(String text, String color) {
        // This method needs implementation for printing with color (similar to Python's print_with_color)
        System.out.println(text);
    }
}

