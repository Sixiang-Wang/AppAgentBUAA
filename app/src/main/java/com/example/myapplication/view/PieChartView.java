package com.example.myapplication.view;
import android.annotation.SuppressLint;
import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.RectF;
import android.graphics.Typeface; // 引入 Typeface
import android.util.AttributeSet;
import android.util.Log; // 用于错误日志
import android.view.MotionEvent;
import android.view.View;

public class PieChartView extends View {
    private Paint paint; // 用于绘制扇形
    private Paint textPaint; // 新增：用于绘制文本
    private RectF rectF;
    private float[] angles = {120f, 120f, 120f}; // 三个部分的角度
    // 确保标签数组与角度数组对应
    private String[] labels = {"设置权限", "学习", "执行"};
    private OnSliceClickListener listener;
    private float separationAngle = 10f; // 每个扇形之间的间隔角度
    private int selectedSliceIndex = -1; // 用于记录被点击的扇形索引

    public PieChartView(Context context) {
        super(context);
        init();
    }

    public PieChartView(Context context, AttributeSet attrs) {
        super(context, attrs);
        init();
    }

    private void init() {
        // 扇形画笔
        paint = new Paint();
        paint.setAntiAlias(true);

        // 初始化文本画笔
        textPaint = new Paint();
        textPaint.setAntiAlias(true);
        textPaint.setColor(Color.BLACK); // 设置文字颜色，可以根据需要调整
        textPaint.setTextSize(60f); // 设置文字大小，根据实际效果调整
        textPaint.setTextAlign(Paint.Align.CENTER); // 设置文本对齐方式为居中

        textPaint.setTypeface(Typeface.DEFAULT_BOLD); // 使用默认粗体

    }

    @Override
    protected void onSizeChanged(int w, int h, int oldw, int oldh) {
        super.onSizeChanged(w, h, oldw, oldh);

        // 计算圆形区域的大小，使其居中并填充整个视图
        int size = Math.min(w, h);
        // 稍微缩小一点，给文字和阴影留出空间
        float padding = size * 0.05f; // 留出 5% 的边距
        int chartSize = (int) (size - 2 * padding);

        int left = (w - chartSize) / 2;
        int top = (h - chartSize) / 2;
        int right = left + chartSize;
        int bottom = top + chartSize;

        rectF = new RectF(left, top, right, bottom);
    }


    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);

        if (rectF == null) return; // 确保 rectF 已初始化

        // 计算圆心和半径
        float centerX = rectF.centerX();
        float centerY = rectF.centerY();
        float radius = rectF.width() / 2;

        // 计算总的间隔角度
        float totalSeparationAngle = separationAngle * angles.length;

        // 计算每个扇形的实际角度
        float totalAngle = 360f;
        float totalOriginalAngle = 0f;
        for (float angle : angles) {
            totalOriginalAngle += angle;
        }

        // 确保每个部分的角度按比例缩小
        float scaleFactor = (totalAngle - totalSeparationAngle) / totalOriginalAngle;
        float[] adjustedAngles = new float[angles.length];  // 定义并初始化adjustedAngles数组

        // 计算每个扇形的调整后的角度
        for (int i = 0; i < angles.length; i++) {
            adjustedAngles[i] = angles[i] * scaleFactor;
        }

        // 绘制饼状图
        float startAngle = -25; // 初始角度保持不变
        int[] colors = {
                Color.parseColor("#FFB6C1"), // 粉色
                Color.parseColor("#FFFACD"), // 黄色
                Color.parseColor("#FFA07A") // 橙红色
        }; // 每一部分的颜色

        for (int i = 0; i < adjustedAngles.length; i++) {
            // 为选中的扇形添加更强的阴影效果
            if (i == selectedSliceIndex) {
                paint.setShadowLayer(20, 10, 10, Color.BLACK);
            } else {
                paint.clearShadowLayer(); // 清除阴影，比设置为透明更好
            }
            paint.setColor(colors[i % colors.length]); // 使用模运算防止数组越界

            // 绘制扇形
            RectF currentRectF = rectF; // 默认使用原始大小
            float currentStartAngle = startAngle;
            float currentSweepAngle = adjustedAngles[i];

            // 在选中扇形时稍微让它向外移动一点（效果比直接增大角度好）
            if (i == selectedSliceIndex) {
                float midAngleRad = (float) Math.toRadians(startAngle + adjustedAngles[i] / 2.0f);
                float offset = radius * 0.05f; // 向外移动半径的5%
                float dx = (float) (offset * Math.cos(midAngleRad));
                float dy = (float) (offset * Math.sin(midAngleRad));
                currentRectF = new RectF(rectF.left + dx, rectF.top + dy, rectF.right + dx, rectF.bottom + dy);
                // 注意：如果需要阴影跟随移动，可能需要更复杂的绘制技巧，或者接受阴影仍在原位
            }

            canvas.drawArc(currentRectF, currentStartAngle, currentSweepAngle, true, paint);


            // --- 绘制文本 ---
            if (labels != null && i < labels.length && labels[i] != null) {
                // 计算文本位置：在扇区角平分线上，距离圆心约 60%-70% 半径处
                float textAngle = startAngle + adjustedAngles[i] / 2.0f; // 扇区中间的角度
                float textRadius = radius * 0.65f; // 文本距离圆心的距离
                // 将角度转换为弧度
                double textAngleRad = Math.toRadians(textAngle);
                // 计算文本中心点的坐标 (注意：画布坐标系y轴向下)
                float textX = centerX + (float) (textRadius * Math.cos(textAngleRad));
                float textY = centerY + (float) (textRadius * Math.sin(textAngleRad));

                // 如果扇区被选中且移动了，文本也应该跟着移动
                if (i == selectedSliceIndex) {
                    float midAngleRad = (float) Math.toRadians(startAngle + adjustedAngles[i] / 2.0f);
                    float offset = radius * 0.05f; // 与扇区移动相同的偏移量
                    textX += (float) (offset * Math.cos(midAngleRad));
                    textY += (float) (offset * Math.sin(midAngleRad));
                }


                // 绘制文本，注意 drawText 的 y 坐标是基线位置，需要微调使文本垂直居中
                // 获取文本的高度信息
                Paint.FontMetrics fm = textPaint.getFontMetrics();
                float textHeight = fm.descent - fm.ascent;
                float verticalOffset = textHeight / 2 - fm.descent; // 计算垂直方向的偏移量
                canvas.drawText(labels[i], textX, textY + verticalOffset, textPaint);
            }

            // 绘制完扇形和文本后更新起始角度
            startAngle += adjustedAngles[i] + separationAngle;
        }
        // 绘制完成后清除画笔的阴影，以防影响其他地方的绘制（如果此画笔复用的话）
        paint.clearShadowLayer();
    }


    @SuppressLint("ClickableViewAccessibility")
    @Override
    public boolean onTouchEvent(MotionEvent event) {
        if (event.getAction() == MotionEvent.ACTION_DOWN) {
            if (rectF == null) return false; // 防止 rectF 未初始化

            float x = event.getX();
            float y = event.getY();
            float centerX = rectF.centerX();
            float centerY = rectF.centerY();
            float dx = x - centerX;
            float dy = y - centerY;

            // 计算点击点到圆心的距离
            float distanceFromCenter = (float) Math.sqrt(dx * dx + dy * dy);
            float radius = rectF.width() / 2;

            // 如果点击在圆外，或者点击在被选中扇区移动后的空白区域（近似判断）
            if (distanceFromCenter > radius * 1.05f) { // 稍微放大判断半径以覆盖移动后的区域
                if (selectedSliceIndex != -1) { // 只有在之前有选中项时才需要取消
                    selectedSliceIndex = -1;
                    invalidate(); // 重绘以取消高亮
                }
                return false; // 让事件继续传递或由父视图处理
            }
            // 如果精确点击在圆外但在近似范围内，也视为取消
            if (distanceFromCenter > radius && selectedSliceIndex != -1) {
                // 点击发生在圆外但在选中扇区可能移动到的区域，也取消选中
                selectedSliceIndex = -1;
                invalidate();
                return true; // 消费掉事件，因为我们处理了取消选中的逻辑
            }
            // 如果点击在圆内，继续判断扇区

            // --- 扇区命中判断 ---
            // 1. 计算点击角度 (修正：考虑 y 轴向下)
            double angleRad = Math.atan2(dy, dx);
            double angleDeg = Math.toDegrees(angleRad);
            // 2. 将角度标准化到 0-360 或 -180-180 范围，并使其与 onDraw 中的 startAngle (-25度开始) 对齐
            //    我们将 onDraw 的 startAngle 视为 0 度参考点进行旋转匹配
            double normalizedAngle = (angleDeg - (-25) + 360) % 360; // 将-25度视为新的0度起点


            // 计算调整后的角度 (这部分逻辑可以移到 onSizeChanged 或只计算一次)
            float totalSeparationAngle = separationAngle * angles.length;
            float totalOriginalAngle = 0f;
            for (float angleValue : angles) {
                totalOriginalAngle += angleValue;
            }
            float scaleFactor = (360f - totalSeparationAngle) / totalOriginalAngle;
            float[] adjustedAngles = new float[angles.length];
            float currentCumulativeAngle = 0f; // 记录相对于新起点(即-25度位置)的累积角度

            int newSelectedSliceIndex = -1;
            for (int i = 0; i < angles.length; i++) {
                adjustedAngles[i] = angles[i] * scaleFactor;
                float sliceStart = currentCumulativeAngle;
                float sliceEnd = currentCumulativeAngle + adjustedAngles[i];

                // 判断 normalizedAngle 是否落在 [sliceStart, sliceEnd) 区间内
                if (normalizedAngle >= sliceStart && normalizedAngle < sliceEnd) {
                    newSelectedSliceIndex = i;
                    break; // 找到对应的扇区
                }
                // 更新累积角度，加上扇区角度和间隔角度
                currentCumulativeAngle += adjustedAngles[i] + separationAngle;
            }

            // 如果点击了有效扇区或者点击了空白处（newSelectedSliceIndex 保持 -1）
            if (newSelectedSliceIndex != selectedSliceIndex) {
                selectedSliceIndex = newSelectedSliceIndex;
                // 触发事件，传递当前的选中扇区索引
                if (listener != null && selectedSliceIndex != -1) {
                    listener.onSliceClick(selectedSliceIndex); // 触发事件，执行具体的回调（例如Toast）
                }
                invalidate();
            } else {
                // 如果点击的是同一个扇区，也触发事件
                if (listener != null && selectedSliceIndex != -1) {
                    listener.onSliceClick(selectedSliceIndex); // 触发事件，执行具体的回调（例如Toast）
                }
            }

            return true; // 返回 true 表示消费了 ACTION_DOWN 事件
        }
        return super.onTouchEvent(event);
    }

    public void setOnSliceClickListener(OnSliceClickListener listener) {
        this.listener = listener;
    }

    public interface OnSliceClickListener {
        void onSliceClick(int index);
    }

}