package com.example.onviftest.utiliy;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.util.AttributeSet;
import android.view.View;

public class CustomRectangleView extends View {

    private float x1, y1, x2, y2; // 假设这四个变量存储了坐标值
    private Paint paint;

    public CustomRectangleView(Context context) {
        super(context);
        init();
    }

    public CustomRectangleView(Context context, AttributeSet attrs) {
        super(context, attrs);
        init();
    }

    public CustomRectangleView(Context context, AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        init();
    }

    private void init() {
        paint = new Paint();
        paint.setColor(Color.GREEN); // 设置边框颜色为绿色
        paint.setStrokeWidth(2f); // 设置边框宽度
        paint.setStyle(Paint.Style.STROKE); // 只绘制边框，不填充内部
    }

    public void setCoordinates(float x1, float y1, float x2, float y2) {
        this.x1 = x1;
        this.y1 = y1;
        this.x2 = x2;
        this.y2 = y2;
        invalidate(); // 刷新视图，触发重绘
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);

        // 确保(x1,y1)为左上角，(x2,y2)为右下角
        if (x1 > x2) {
            float temp = x1;
            x1 = x2;
            x2 = temp;
        }
        if (y1 > y2) {
            float temp = y1;
            y1 = y2;
            y2 = temp;
        }

        canvas.drawRect(x1, y1, x2, y2, paint); // 绘制矩形框
    }
}
