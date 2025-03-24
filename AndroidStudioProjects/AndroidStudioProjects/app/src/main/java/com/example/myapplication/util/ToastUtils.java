package com.example.myapplication.util;


import android.widget.Toast;

import com.example.myapplication.App;

public class ToastUtils {
    private static Toast mToast;

    public static void shortCall(String text) {
        cancel();
        mToast = Toast.makeText(App.getApp(), text, Toast.LENGTH_SHORT);
        mToast.show();
    }

    public static void longCall(String text) {
        cancel();
        mToast = Toast.makeText(App.getApp(), text, Toast.LENGTH_LONG);
        mToast.show();
    }

    private static void cancel() {
        if (mToast != null) {
            mToast.cancel();
        }
    }
}
