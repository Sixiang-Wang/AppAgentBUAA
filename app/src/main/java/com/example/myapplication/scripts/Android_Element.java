package com.example.myapplication.scripts;

import java.util.Map;

public class Android_Element {
    public String uid;      // 元素的唯一标识符
    public int[] bbox;      // 元素的边界框 (假设是 [x1, y1, x2, y2])
    public Map<String, String> attrib;  // 元素的属性

    // 构造方法
    public Android_Element(String uid, int[] bbox, Map<String, String> attrib) {
        this.uid = uid;
        this.bbox = bbox;
        this.attrib = attrib;
    }





}
