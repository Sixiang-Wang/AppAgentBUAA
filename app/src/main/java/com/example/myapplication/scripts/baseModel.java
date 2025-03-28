package com.example.myapplication.scripts;

import java.util.List;
import java.util.Map;

abstract class baseModel {
    abstract Map.Entry<Boolean, String> getModelResponse(String prompt, List<String> images);
}
