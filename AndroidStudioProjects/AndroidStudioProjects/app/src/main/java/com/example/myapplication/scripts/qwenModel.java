package com.example.myapplication.scripts;

import static com.example.myapplication.activity.MainActivity.TAG;

import android.util.Log;

import java.util.*;
import java.util.concurrent.CountDownLatch;

import com.alibaba.dashscope.aigc.multimodalconversation.MultiModalConversation;
import com.alibaba.dashscope.aigc.multimodalconversation.MultiModalConversationParam;
import com.alibaba.dashscope.aigc.multimodalconversation.MultiModalConversationResult;
import com.alibaba.dashscope.common.MultiModalMessage;
import com.alibaba.dashscope.common.Role;

import java.util.concurrent.Executors;



//大模型之一
public class qwenModel extends baseModel {
    //模型的apiKey
    private final String apiKey;
    private final String model;
    public qwenModel(String apiKey, String model) {
        this.apiKey = apiKey;
        this.model = model;
    }

    //与模型进行通话，发送内容为prompt和一张image
    //此方法使用了同步锁，意味着在主线程里调用该方法，主线程会等待该方法完成后才进行下一步。
    @Override
    public Map.Entry<Boolean, String> getModelResponse(String prompt, List<String> images) {
        CountDownLatch latch = new CountDownLatch(1); // 创建一个同步锁
        final Map.Entry<Boolean, String>[] resultHolder = new Map.Entry[1]; // 存储结果
        Executors.newSingleThreadExecutor().execute(() -> {
            try {
                String image = "file://" + images.get(0);
                MultiModalMessage multiModalMessage = MultiModalMessage.builder()
                        .role(Role.USER.getValue())
                        .content(List.of(
                                Collections.singletonMap("image", image),
                                Collections.singletonMap("text", prompt))).build();
                MultiModalConversationParam multiModalConversationParam = MultiModalConversationParam.builder()
                        .model(model)
                        .apiKey(apiKey)
                        .message(multiModalMessage)
                        .build();
                MultiModalConversation multiModalConversation = new MultiModalConversation();
                Log.d(TAG, "开始 call");

                MultiModalConversationResult result = multiModalConversation.call(multiModalConversationParam);

                String msg = (String) result.getOutput().getChoices().get(0).getMessage().getContent().get(0).get("text");
                Log.d(TAG, "Qwen 回答结果: " + result);
                resultHolder[0] = new AbstractMap.SimpleEntry<>(true, msg);
            } catch (Exception e) {
                Log.e(TAG, "请求失败", e);
                resultHolder[0] = new AbstractMap.SimpleEntry<>(false, "请求失败: " + e.getMessage());
            } finally {
                latch.countDown(); // 释放锁，主线程继续执行
            }
        });
        try {
            latch.await(); // 主线程等待，直到子线程完成
        } catch (InterruptedException e) {
            Log.e(TAG, "等待线程被中断", e);
            return new AbstractMap.SimpleEntry<>(false, "线程被中断");
        }
        return resultHolder[0]; // 返回结果
    }


}


