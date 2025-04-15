package com.example.myapplication.scripts;

import java.util.List;
import java.util.Map;

public abstract class baseModel {
    public abstract Map.Entry<Boolean, String> getModelResponse(String prompt, List<String> images);
    public abstract Map.Entry<Boolean, String> getModelResponse_tasksplit(String prompt);

    public abstract Map.Entry<Boolean, String> getModelResponse_keyword(String prompt);

    public abstract Map.Entry<Boolean, String> getModelResponse_keyword_2(String prompt);
}
