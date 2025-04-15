package com.example.myapplication.scripts;

import android.content.Context;
import android.media.AudioManager;
import android.speech.tts.TextToSpeech;
import android.util.Log;
import java.util.Locale;

public class TTSManager {

    private static TTSManager instance;
    private TextToSpeech textToSpeech;
    private boolean isReady = false;
    private AudioManager audioManager;
    private AudioManager.OnAudioFocusChangeListener focusChangeListener;

    private TTSManager(Context context) {
        // 获取 AudioManager 实例
        audioManager = (AudioManager) context.getSystemService(Context.AUDIO_SERVICE);

        // 初始化音频焦点监听
        focusChangeListener = focusChange -> {
            if (focusChange == AudioManager.AUDIOFOCUS_LOSS_TRANSIENT_CAN_DUCK || focusChange == AudioManager.AUDIOFOCUS_LOSS_TRANSIENT) {
                // 如果焦点临时丢失，可以调低音量
                Log.d("TTSManager", "Audio focus temporarily lost");
            } else if (focusChange == AudioManager.AUDIOFOCUS_GAIN) {
                // 恢复音频焦点
                Log.d("TTSManager", "Audio focus gained");
            } else if (focusChange == AudioManager.AUDIOFOCUS_LOSS) {
                // 丢失焦点后，停止TTS播放
                stop();
            }
        };

        // 初始化 TTS
        textToSpeech = new TextToSpeech(context.getApplicationContext(), status -> {
            if (status == TextToSpeech.SUCCESS) {
                int result = textToSpeech.setLanguage(Locale.CHINESE);
                isReady = !(result == TextToSpeech.LANG_MISSING_DATA || result == TextToSpeech.LANG_NOT_SUPPORTED);
            }
        });
    }

    public static synchronized TTSManager getInstance(Context context) {
        if (instance == null) {
            instance = new TTSManager(context);
        }
        return instance;
    }

    public void speak(String text) {
        if (isReady && text != null && !text.isEmpty()) {
            // 请求音频焦点
            int focusResult = audioManager.requestAudioFocus(focusChangeListener, AudioManager.STREAM_MUSIC, AudioManager.AUDIOFOCUS_GAIN);

            if (focusResult == AudioManager.AUDIOFOCUS_REQUEST_GRANTED) {
                // 请求成功，播放语音
                textToSpeech.speak(text, TextToSpeech.QUEUE_ADD, null, null);
            } else {
                Log.d("TTSManager", "Failed to gain audio focus");
            }
        }
    }

    public void stop() {
        if (textToSpeech != null) {
            textToSpeech.stop();
        }
        // 释放音频焦点
        audioManager.abandonAudioFocus(focusChangeListener);
    }

    public void shutdown() {
        if (textToSpeech != null) {
            textToSpeech.stop();
            textToSpeech.shutdown();
        }
        // 释放音频焦点
        audioManager.abandonAudioFocus(focusChangeListener);
    }

    public long estimateDurationByChars(String text) {
        if (text == null || text.isEmpty()) return 0;
        int numChars = text.length();
        return (long) (numChars * 0.25 * 1000); // 转换为毫秒
    }

}