package com.example.myapplication.scripts;


import static com.example.myapplication.activity.MainActivity.TAG;

import android.util.Log;

public class printUtils {
    public static void printWithColor(String text, String color) {
        switch (color.toLowerCase()) {
            case "red":
                Log.e(TAG, text);
                break;
            case "yellow":
                Log.w(TAG, text);
                break;
            case "blue":
                Log.i(TAG, text);
                break;
            default:
                Log.d(TAG, text);
        }
    }
}
