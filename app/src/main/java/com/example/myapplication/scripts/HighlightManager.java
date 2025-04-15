package com.example.myapplication.scripts;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.PixelFormat;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.View;
import android.view.WindowManager;
import android.os.Handler;
import android.os.Looper;

public class HighlightManager {

    public static Context Maincontext=null;
    private static View highlightView = null;

    private static WindowManager.LayoutParams currentParams = null;

    public interface OnHighlightClickListener {
        void onClick();
    }
    private static OnHighlightClickListener clickListener;

    public static void setOnHighlightClickListener(OnHighlightClickListener listener) {
        clickListener = listener;
    }

    // 显示高亮，参数是 bbox 数组
    public static void showHighlight(Context context, int[] bbox) {
        if (bbox == null || bbox.length != 4) return;

        if (Looper.myLooper() != Looper.getMainLooper()) {
            new Handler(Looper.getMainLooper()).post(() -> showHighlight(context, bbox));
            return;
        }

        int x1 = bbox[0];
        int y1 = bbox[1]-getStatusBarHeight(Maincontext);
        int x2 = bbox[2];
        int y2 = bbox[3]-getStatusBarHeight(Maincontext);

        if (highlightView != null) {
            clearHighlight(context);
        }

        WindowManager windowManager = (WindowManager) context.getSystemService(Context.WINDOW_SERVICE);

        highlightView = new HighlightView(context);

        WindowManager.LayoutParams params = new WindowManager.LayoutParams(
                x2 - x1,
                y2 - y1,
                WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY, // 悬浮窗
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE ,
                PixelFormat.TRANSLUCENT
        );

        params.gravity = Gravity.TOP | Gravity.START;
        params.x = x1;
        params.y = y1;

        currentParams = params; // 保存参数引用，便于后续切换
        windowManager.addView(highlightView, params);
    }

    // 清除高亮
    public static void clearHighlight(Context context) {
        if (highlightView != null) {
            if (Looper.myLooper() != Looper.getMainLooper()) {
                new Handler(Looper.getMainLooper()).post(() -> clearHighlight(context));
                return;
            }
            WindowManager windowManager = (WindowManager) context.getSystemService(Context.WINDOW_SERVICE);
            windowManager.removeView(highlightView);
            highlightView = null;
        }
    }

    // 允许点击穿透
    public static void allowTouchThrough(Context context) {
        if (highlightView != null && currentParams != null) {
            if (Looper.myLooper() != Looper.getMainLooper()) {
                new Handler(Looper.getMainLooper()).post(() -> allowTouchThrough(context));
                return;
            }
            WindowManager windowManager = (WindowManager) context.getSystemService(Context.WINDOW_SERVICE);
            currentParams.flags = WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE | WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL;
            windowManager.updateViewLayout(highlightView, currentParams);
        }
    }

    // 禁止点击穿透（恢复遮挡行为）
    public static void blockTouch(Context context) {
        if (highlightView != null && currentParams != null) {
            if (Looper.myLooper() != Looper.getMainLooper()) {
                new Handler(Looper.getMainLooper()).post(() -> blockTouch(context));
                return;
            }
            WindowManager windowManager = (WindowManager) context.getSystemService(Context.WINDOW_SERVICE);
            currentParams.flags = WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE;
            windowManager.updateViewLayout(highlightView, currentParams);
        }
    }




    // 内部类：绘制高亮方框
    private static class HighlightView extends View {
        private Paint borderPaint;

        public HighlightView(Context context) {
            super(context);

            setBackgroundColor(Color.TRANSPARENT);
            setClickable(false);
            setFocusable(false);
            setFocusableInTouchMode(false);
            // 边框画笔
            borderPaint = new Paint();
            borderPaint.setColor(Color.parseColor("#FF3030")); // 鲜红色
            borderPaint.setStyle(Paint.Style.STROKE);
            borderPaint.setStrokeWidth(16); // 加粗线条（原来是10）
            borderPaint.setAntiAlias(true); // 抗锯齿，让线条更平滑
        }

        @Override
        protected void onDraw(Canvas canvas) {
            super.onDraw(canvas);
            canvas.drawRect(0, 0, getWidth(), getHeight(), borderPaint);
        }

        @Override
        public boolean dispatchTouchEvent(MotionEvent event) {
            if (event.getAction() == MotionEvent.ACTION_UP && clickListener != null) {
                clickListener.onClick();
            }
            return false; // 关键！返回 false 继续传递
        }

    }

    public static int getStatusBarHeight(Context context) {
        int result = 0;
        int resourceId = context.getResources().getIdentifier("status_bar_height", "dimen", "android");
        if (resourceId > 0) {
            result = context.getResources().getDimensionPixelSize(resourceId);
        }
        return result;
    }
}
