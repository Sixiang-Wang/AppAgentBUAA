package com.example.myapplication.scripts;

import java.util.Map;

public class androidElement {
    public String uid;      // 元素的唯一标识符
    public int[] bbox;      // 元素的边界框 (假设是 [x1, y1, x2, y2])
    public String attrib;  // 元素的属性

    public float score;

    public Map<String, String> attributes=null;

    // 构造方法
    public androidElement(String uid, int[] bbox, String attrib,float score,Map<String, String> attributes) {
        this.uid = uid;
        this.bbox = bbox;
        this.attrib = attrib;
        this.score=score;
        this.attributes=attributes;
    }





}
