package com.example.onviftest.utiliy;

import android.content.Context;
import android.util.DisplayMetrics;
import android.view.GestureDetector;
import android.view.MotionEvent;
import android.view.View;
import android.view.WindowManager;

import android.view.ScaleGestureDetector;

public class OnSwipeTouchListener implements View.OnTouchListener {
    private float downX;
    private float downY;
    private float upX;
    private float upY;
    private static final int MIN_SWIPE_DISTANCE = 150;
    private boolean isSwiping = false;
    private GestureDetector gestureDetector;
    private ScaleGestureDetector scaleGestureDetector;
    private int screenWidth;
    private int screenHeight;
    private boolean isScaling = false;
    private boolean ignoreNextUp = false;
    private float scaleFactorTotal = 1.0f;

    // 定义一个枚举类来表示滑动方向
    public enum SwipeDirection {
        LEFT, RIGHT, UP, DOWN, NONE
    }

    public OnSwipeTouchListener(Context context) {
        gestureDetector = new GestureDetector(context, new GestureListener());
        scaleGestureDetector = new ScaleGestureDetector(context, new ScaleListener());

        // 获取屏幕尺寸
        WindowManager windowManager = (WindowManager) context.getSystemService(Context.WINDOW_SERVICE);
        DisplayMetrics displayMetrics = new DisplayMetrics();
        windowManager.getDefaultDisplay().getMetrics(displayMetrics);
        screenWidth = displayMetrics.widthPixels;
        screenHeight = displayMetrics.heightPixels;
    }

    @Override
    public boolean onTouch(View v, MotionEvent event) {
        scaleGestureDetector.onTouchEvent(event);

        // 如果正在进行缩放操作，则忽略单指滑动事件
        if (isScaling) {
            return true;
        }

        gestureDetector.onTouchEvent(event);

        switch (event.getAction()) {
            case MotionEvent.ACTION_DOWN:
                downX = event.getX();
                downY = event.getY();
                isSwiping = false;
                ignoreNextUp = false; // 重置标志位
                return true;
            case MotionEvent.ACTION_MOVE:
                float moveX = event.getX();
                float moveY = event.getY();
                float deltaX = downX - moveX;
                float deltaY = downY - moveY;

                if (Math.abs(deltaX) > MIN_SWIPE_DISTANCE || Math.abs(deltaY) > MIN_SWIPE_DISTANCE) {
                    if (Math.abs(deltaX) > Math.abs(deltaY)) {
                        // 水平滑动
                        if (deltaX > 0) {
                            // 向左滑动
                            if (!isSwiping) {
                                isSwiping = true;
                                onSwipeLeftStart();
                            }
                            onSwipeLeft(moveX / screenWidth);
                        } else {
                            // 向右滑动
                            if (!isSwiping) {
                                isSwiping = true;
                                onSwipeRightStart();
                            }
                            onSwipeRight(moveX / screenWidth);
                        }
                    } else {
                        // 垂直滑动
                        if (deltaY > 0) {
                            // 向上滑动
                            if (!isSwiping) {
                                isSwiping = true;
                                onSwipeUpStart();
                            }
                            onSwipeUp(moveY / screenHeight);
                        } else {
                            // 向下滑动
                            if (!isSwiping) {
                                isSwiping = true;
                                onSwipeDownStart();
                            }
                            onSwipeDown(moveY / screenHeight);
                        }
                    }
                }
                return true;
            case MotionEvent.ACTION_UP:
                if (!isScaling && !ignoreNextUp) { // 如果不在缩放过程中，处理滑动结束事件
                    upX = event.getX();
                    upY = event.getY();
                    isSwiping = false;

                    // 计算滑动比例
                    float proportionX = (downX - upX) / screenWidth;
                    float proportionY = (downY - upY) / screenHeight;

                    // 确定滑动方向
                    SwipeDirection direction = getSwipeDirection(downX, downY, upX, upY);

                    // 传递滑动方向和比例到 onSwipeEnd 方法
                    onSwipeEnd(direction, proportionX, proportionY);
                }
                return true;
        }
        return false;
    }

    // 确定滑动方向的方法
    private SwipeDirection getSwipeDirection(float downX, float downY, float upX, float upY) {
        float deltaX = downX - upX;
        float deltaY = downY - upY;
        if (Math.abs(deltaX) > Math.abs(deltaY)) {
            if (Math.abs(deltaX) > MIN_SWIPE_DISTANCE) {
                return deltaX > 0 ? SwipeDirection.LEFT : SwipeDirection.RIGHT;
            }
        } else {
            if (Math.abs(deltaY) > MIN_SWIPE_DISTANCE) {
                return deltaY > 0 ? SwipeDirection.UP : SwipeDirection.DOWN;
            }
        }
        return SwipeDirection.NONE;
    }

    // ScaleGestureDetector的监听器
    private class ScaleListener extends ScaleGestureDetector.SimpleOnScaleGestureListener {
        @Override
        public boolean onScale(ScaleGestureDetector detector) {
            float scaleFactor = detector.getScaleFactor();
            scaleFactorTotal *= scaleFactor; // 记录总的缩放倍数
            if (scaleFactor > 1) {
                onPinchOut(scaleFactor);
            } else {
                onPinchIn(scaleFactor);
            }
            return true;
        }

        @Override
        public boolean onScaleBegin(ScaleGestureDetector detector) {
            isScaling = true;
            scaleFactorTotal = 1.0f; // 重置总的缩放倍数
            return true;
        }

        @Override
        public void onScaleEnd(ScaleGestureDetector detector) {
            isScaling = false;
            ignoreNextUp = true; // 标志下一次UP事件应被忽略
            onPinchEnd(scaleFactorTotal); // 传递总的缩放倍数
        }
    }

    public void onSwipeLeftStart() {
        // 重写此方法以处理左滑开始事件
    }

    public void onSwipeLeft(float proportion) {
        // 重写此方法以处理持续左滑事件
        // proportion 是相对于屏幕宽度的比例
    }

    public void onSwipeRightStart() {
        // 重写此方法以处理右滑开始事件
    }

    public void onSwipeRight(float proportion) {
        // 重写此方法以处理持续右滑事件
        // proportion 是相对于屏幕宽度的比例
    }

    public void onSwipeUpStart() {
        // 重写此方法以处理上滑开始事件
    }

    public void onSwipeUp(float proportion) {
        // 重写此方法以处理持续上滑事件
        // proportion 是相对于屏幕高度的比例
    }

    public void onSwipeDownStart() {
        // 重写此方法以处理下滑开始事件
    }

    public void onSwipeDown(float proportion) {
        // 重写此方法以处理持续下滑事件
        // proportion 是相对于屏幕高度的比例
    }

    public void onSwipeEnd(SwipeDirection direction, float proportionX, float proportionY) {
        // 重写此方法以处理滑动结束事件
        // direction 表示滑动方向
        // proportionX 是相对于屏幕宽度的滑动距离比例
        // proportionY 是相对于屏幕高度的滑动距离比例
    }

    public void onDoubleTap(MotionEvent e) {
        // 重写此方法以处理双击事件
    }

    public void onPinchIn(float scaleFactor) {
        // 重写此方法以处理缩小事件
    }

    public void onPinchOut(float scaleFactor) {
        // 重写此方法以处理放大事件
    }

    public void onPinchEnd(float totalScaleFactor) {
        // 重写此方法以处理缩放结束事件
        // totalScaleFactor 是总的缩放倍数
    }

    private class GestureListener extends GestureDetector.SimpleOnGestureListener {
        @Override
        public boolean onDoubleTap(MotionEvent e) {
            OnSwipeTouchListener.this.onDoubleTap(e);
            return true;
        }
    }
}
