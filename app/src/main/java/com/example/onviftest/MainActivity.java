package com.example.onviftest;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.AppCompatButton;
import androidx.core.view.GestureDetectorCompat;

import android.annotation.SuppressLint;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.ImageFormat;
import android.graphics.PixelFormat;
import android.graphics.SurfaceTexture;
import android.hardware.display.DisplayManager;
import android.hardware.display.VirtualDisplay;
import android.media.Image;
import android.media.ImageReader;
import android.media.projection.MediaProjection;
import android.media.projection.MediaProjectionManager;
import android.net.Uri;
import android.os.Bundle;
import android.os.Environment;
import android.os.Handler;
import android.os.HandlerThread;
import android.util.DisplayMetrics;
import android.util.Log;
import android.util.Pair;
import android.view.GestureDetector;
import android.view.MotionEvent;
import android.view.PixelCopy;
import android.view.Surface;
import android.view.SurfaceView;
import android.view.TextureView;
import android.view.View;
import android.widget.FrameLayout;
import android.widget.ImageButton;
import android.widget.LinearLayout;
import android.widget.Toast;

import org.opencv.android.OpenCVLoader;
import org.opencv.android.Utils;
import org.opencv.core.Mat;
import org.opencv.core.MatOfByte;
import org.opencv.core.Rect;
import org.opencv.core.Scalar;
import org.opencv.core.Size;
import org.opencv.imgproc.Imgproc;
import org.opencv.objdetect.FaceDetectorYN;
import org.opencv.videoio.VideoCapture;
import org.videolan.libvlc.LibVLC;
import org.videolan.libvlc.Media;
import org.videolan.libvlc.MediaPlayer;
import org.videolan.libvlc.interfaces.IVLCVout;
import org.videolan.libvlc.util.VLCVideoLayout;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.ByteBuffer;
import java.text.SimpleDateFormat;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.logging.Logger;

import be.teletask.onvif.OnvifManager;
import be.teletask.onvif.listeners.OnvifMediaProfilesListener;
import be.teletask.onvif.listeners.OnvifMediaStreamURIListener;
import be.teletask.onvif.listeners.OnvifResponseListener;
import be.teletask.onvif.listeners.OnvifServicesListener;
import be.teletask.onvif.models.OnvifDevice;
import be.teletask.onvif.models.OnvifMediaProfile;
import be.teletask.onvif.models.OnvifServices;
import be.teletask.onvif.responses.OnvifResponse;
import io.reactivex.rxjava3.core.Single;
import io.reactivex.rxjava3.core.SingleEmitter;

public class MainActivity extends AppCompatActivity {
    private static final boolean USE_TEXTURE_VIEW = false;
    private static final boolean ENABLE_SUBTITLES = true;
    private TextureView mVideoLayout = null;
    private LibVLC mLibVLC = null;
    private MediaPlayer mMediaPlayer = null;

    private boolean canMove = false;

    OnvifManager onvifManager = null;

    OnvifDevice device = null;

    OnvifMediaProfile mediaProfile = null;

    OnvifServices services = null;

    private MatOfByte modelBuffer;
    private MatOfByte configBuffer;

    private FaceDetectorYN faceDetector;
    private Mat rgba;
    private Mat bgr;
    private Mat bgrScaled;
    private Size inputSize = null;
    private float scale = 2.f;
    private static final Scalar BOX_COLOR = new Scalar(0, 255, 0);
    private static final String TAG = "OCVSample::Activity";
    private Mat faces;

    @SuppressLint("CheckResult")
    @Override
    protected void onCreate(Bundle savedInstanceState) /**/ {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        if (!initOpenCV()) {
            return;
        }

        mVideoLayout = findViewById(R.id.videoLayout);
        final ArrayList<String> args = new ArrayList<>();
        args.add("-vvv");

        mLibVLC = new LibVLC(getApplicationContext(), args);

        if (mMediaPlayer != null && mMediaPlayer.isPlaying()) {
            mMediaPlayer.stop();
            mMediaPlayer.release();
        }
        mMediaPlayer = new MediaPlayer(mLibVLC);
        mVideoLayout.setSurfaceTextureListener(new TextureView.SurfaceTextureListener() {
            @Override
            public void onSurfaceTextureAvailable(@NonNull SurfaceTexture surface, int width, int height) {
                mMediaPlayer.getVLCVout().setVideoSurface(mVideoLayout.getSurfaceTexture());
                mMediaPlayer.getVLCVout().setWindowSize(mVideoLayout.getWidth(), mVideoLayout.getHeight());
                mMediaPlayer.getVLCVout().attachViews();
                startTimer();

            }

            @Override
            public void onSurfaceTextureSizeChanged(@NonNull SurfaceTexture surface, int width, int height) {

            }

            @Override
            public boolean onSurfaceTextureDestroyed(@NonNull SurfaceTexture surface) {
                return false;
            }

            @Override
            public void onSurfaceTextureUpdated(@NonNull SurfaceTexture surface) {

            }
        });

        setUpButtonListener();
        setDownButtonListener();
        setLeftButtonListener();
        setRightButtonListener();
        setBigButtonListener();
        setSmalltButtonListener();
        setLayoutListener();
//        setScreenShotListener();

        onvifManager = new OnvifManager(new OnvifResponseListener() {
            public void onResponse(OnvifDevice onvifDevice, OnvifResponse onvifResponse) {
                Log.i("response", "PTZ response: " + onvifResponse.getXml());
            }

            public void onError(OnvifDevice onvifDevice, int errorCode, String errorMsg) {
                Log.i("error", "ErrorCode：" + errorCode + "Error Msg：" + errorMsg);
            }
        });

        //获取device
        device = new OnvifDevice("172.16.30.232", "admin", "admin");
        onvifManager.getServices(device, new OnvifServicesListener() {
            @Override
            public void onServicesReceived(OnvifDevice onvifDevice, OnvifServices services) {
                Log.i("device", onvifDevice.getHostName());
            }
        });

        //获取配置文件
        Single<OnvifMediaProfile> getOnvifMediaProfileSingle = Single.create(emitter -> {
            onvifManager.getMediaProfiles(device, new OnvifMediaProfilesListener() {
                @Override
                public void onMediaProfilesReceived(OnvifDevice device, List<OnvifMediaProfile> mediaProfiles) {
                    if (!emitter.isDisposed()) {
                        // 获取第一个媒体配置文件
                        OnvifMediaProfile profile = mediaProfiles.get(0);
                        emitter.onSuccess(profile);  // 将结果发送给订阅者
                    }
                }
            });
        });

        //获取service（暂时没用）
        Single<OnvifServices> getOnvifServicesSingle = Single.create(emitter -> {
            onvifManager.getServices(device, new OnvifServicesListener() {
                @Override
                public void onServicesReceived(OnvifDevice onvifDevice, OnvifServices services) {
                    if (!emitter.isDisposed()) {
                        emitter.onSuccess(services);
                    }
                }
            });
        });

        // 错误处理
        Single.zip(
                getOnvifMediaProfileSingle,
                getOnvifServicesSingle,
                (onvifMediaProfile, onvifServices) -> new Pair<>(onvifMediaProfile, onvifServices)
        ).subscribe(
                // 当两个操作都完成时，这里会接收到一个Pair<OnvifMediaProfile, OnvifServices>对象
                pair -> {
                    OnvifMediaProfile mediaProfile = pair.first;
                    OnvifServices services = pair.second;

                    // 使用mediaProfile和services
                    useMediaProfileAndServices(mediaProfile, services);
                },
                this::handleError
        );

        //        final View greenRectangle = findViewById(R.id.greenRectangle);
//
//        VLCVideoLayout frameLayout = findViewById(R.id.videoLayout);
//        // 设置点击事件监听器
//        greenRectangle.setOnClickListener(new View.OnClickListener() {
//            @Override
//            public void onClick(View v) {
//                // 获取视图在屏幕上的绝对位置
//                int[] location = new int[2];
//                greenRectangle.getLocationOnScreen(location);
//                float x = location[0] + greenRectangle.getWidth() / 2;
//                float y = location[1] + greenRectangle.getHeight() / 2;
//
//                int layoutWidth = frameLayout.getWidth();
//                int layoutHeight = frameLayout.getHeight();
//
//                float widthScale = (float) greenRectangle.getWidth() / layoutWidth;
//                float heightScale = (float) greenRectangle.getHeight() / layoutHeight;
//
//                // 显示绝对位置
//                Toast.makeText(MainActivity.this, "widthscale: " + widthScale + ", heightscale: " + heightScale, Toast.LENGTH_SHORT).show();
//                float centerX = layoutWidth / 2f;
//                float centerY = layoutHeight / 2f;
//
//                // 计算点击位置与中心点之间的差值以及百分比
//                float dxFromCenter = x - centerX;
//                float dyFromCenter = y - centerY;
//
//                // 计算百分比
//                float percentX = dxFromCenter / centerX;
//                float percentY = dyFromCenter / centerY;
//
//                onvifManager.getStatus(device, mediaProfile, (onvifDevice, profile, status) -> {
//                    double currentPan = status.getPan();
//                    double currentTilt = status.getTilt();
//                    Log.d("pan", String.valueOf(currentPan));
//                    Log.d("tilt", String.valueOf(currentTilt));
//                    Log.d("zoom", String.valueOf(status.getZoom()));
//                    //x-整屏幕0.3
//                    //y-整屏幕1.1
//                    double newPan = currentPan + (percentX * 0.15);
//                    double newTilt = currentTilt - (percentY * 0.5);
//                    if(isZoom) {
//                        onvifManager.sendOnvifRequest(device, new PTZAbsoluteMove(mediaProfile, currentPan, currentTilt, 0f));
//                        isZoom = false;
//                    } else {
//                        onvifManager.sendOnvifRequest(device, new PTZAbsoluteMove(mediaProfile, newPan, newTilt, 1-(heightScale + widthScale)));
//                        isZoom = true;
//                    }
//                });
//            }
//        });
    }

    private boolean isZoom = false;
    GestureDetectorCompat gestureDetector;

    List<float[]> clickRectangles = new ArrayList<>();

    //点击屏幕位置
    @SuppressLint("ClickableViewAccessibility")
    private void setLayoutListener() {
        TextureView frameLayout = findViewById(R.id.videoLayout);
        gestureDetector = new GestureDetectorCompat(this, new GestureDetector.SimpleOnGestureListener() {
            @Override
            public boolean onDoubleTap(@NonNull MotionEvent e) {
                doSomethingEverySecond(false);
                Log.d("click face", String.valueOf(clickRectangles.stream().count()));

                // 获取点击事件发生时的位置坐标
                float xClickPos = e.getX();
                float yClickPos = e.getY();

                // 获取FrameLayout的宽高及中心点坐标
                int layoutWidth = frameLayout.getWidth();
                int layoutHeight = frameLayout.getHeight();
                float centerX = layoutWidth / 2f;
                float centerY = layoutHeight / 2f;

                if (!isZoom) {
                    if ((long) clickRectangles.size() > 0) {
                        for (float[] rect : clickRectangles) {
                            if ((xClickPos >= rect[0] && xClickPos <= rect[2]) && (yClickPos >= rect[1] && yClickPos <= rect[3])) {
                                onvifManager.getStatus(device, mediaProfile, (onvifDevice, profile, status) -> {
                                    double currentPan = status.getPan();
                                    double currentTilt = status.getTilt();
                                    Log.d("pan", String.valueOf(currentPan));
                                    Log.d("tilt", String.valueOf(currentTilt));
                                    Log.d("zoom", String.valueOf(status.getZoom()));
                                    //x-整屏幕0.3
                                    //y-整屏幕1.1

                                    // 计算点击位置与中心点之间的差值以及百分比
                                    float dxFromCenter = ((rect[0] + rect[2]) / 2) - centerX;
                                    float dyFromCenter = ((rect[1] + rect[3]) / 2) - centerY;

                                    float widthScale = (rect[2] - rect[0]) / layoutWidth;
                                    float heightScale = (rect[3] - rect[1]) / layoutHeight;

                                    // 计算百分比
                                    float percentX = dxFromCenter / centerX;
                                    float percentY = dyFromCenter / centerY;

                                    Log.d("percentx",String.valueOf(percentX));
                                    Log.d("percenty",String.valueOf(percentY));

                                    double newPan = currentPan + (percentX * 0.18);
                                    double newTilt = currentTilt - (percentY * 0.64);

                                    onvifManager.sendOnvifRequest(device, new PTZAbsoluteMove(mediaProfile, newPan, newTilt, 1 - (heightScale + widthScale)));
                                    isZoom = true;
                                });
                            }
                        }
                    }
                }


                if (isZoom) {
                    onvifManager.getStatus(device, mediaProfile, (onvifDevice, profile, status) -> {
                        double currentPan = status.getPan();
                        double currentTilt = status.getTilt();
                        onvifManager.sendOnvifRequest(device, new PTZAbsoluteMove(mediaProfile, currentPan, currentTilt, 0f));
                        isZoom = false;
                    });
                }

                return false;
            }
        });

        frameLayout.setOnTouchListener(new View.OnTouchListener() {
            @Override
            public boolean onTouch(View v, MotionEvent event) {
                gestureDetector.onTouchEvent(event);
                return true;
            }
        });
    }

    //点击按钮事件
    @SuppressLint("ClickableViewAccessibility")
    private void setUpButtonListener() {
        ImageButton button = findViewById(R.id.up);
        button.setOnTouchListener(new View.OnTouchListener() {
            @Override
            public boolean onTouch(View v, MotionEvent event) {
                switch (event.getAction()) {
                    case MotionEvent.ACTION_DOWN:
                        cameraControl(1);
                        break;
                    case MotionEvent.ACTION_UP:
                        cameraControl(7);
                        break;
                    case MotionEvent.ACTION_CANCEL:
                        cameraControl(7);
                        break;
                }

                return false; // 返回false表示允许其他事件监听器继续处理这个事件
            }
        });
    }

    @SuppressLint("ClickableViewAccessibility")
    private void setDownButtonListener() {
        ImageButton button = findViewById(R.id.down);
        button.setOnTouchListener(new View.OnTouchListener() {
            @Override
            public boolean onTouch(View v, MotionEvent event) {
                switch (event.getAction()) {
                    case MotionEvent.ACTION_DOWN:
                        cameraControl(2);
                        break;
                    case MotionEvent.ACTION_UP:
                        cameraControl(7);
                        break;
                    case MotionEvent.ACTION_CANCEL:
                        cameraControl(7);
                        break;
                }

                return false; // 返回false表示允许其他事件监听器继续处理这个事件
            }
        });
    }

    @SuppressLint("ClickableViewAccessibility")
    private void setLeftButtonListener() {
        ImageButton button = findViewById(R.id.left);
        button.setOnTouchListener(new View.OnTouchListener() {
            @Override
            public boolean onTouch(View v, MotionEvent event) {
                switch (event.getAction()) {
                    case MotionEvent.ACTION_DOWN:
                        cameraControl(3);
                        break;
                    case MotionEvent.ACTION_UP:
                        cameraControl(7);
                        break;
                    case MotionEvent.ACTION_CANCEL:
                        cameraControl(7);
                        break;
                }

                return false; // 返回false表示允许其他事件监听器继续处理这个事件
            }
        });
    }

    @SuppressLint("ClickableViewAccessibility")
    private void setRightButtonListener() {
        ImageButton button = findViewById(R.id.right);
        button.setOnTouchListener(new View.OnTouchListener() {
            @Override
            public boolean onTouch(View v, MotionEvent event) {
                switch (event.getAction()) {
                    case MotionEvent.ACTION_DOWN:
                        cameraControl(4);
                        break;
                    case MotionEvent.ACTION_UP:
                        cameraControl(7);
                        break;
                    case MotionEvent.ACTION_CANCEL:
                        cameraControl(7);
                        break;
                }

                return false; // 返回false表示允许其他事件监听器继续处理这个事件
            }
        });
    }

    @SuppressLint("ClickableViewAccessibility")
    private void setBigButtonListener() {
        ImageButton button = findViewById(R.id.big);
        button.setOnTouchListener(new View.OnTouchListener() {
            @Override
            public boolean onTouch(View v, MotionEvent event) {
                switch (event.getAction()) {
                    case MotionEvent.ACTION_DOWN:
                        cameraControl(5);
                        break;
                    case MotionEvent.ACTION_UP:
                        cameraControl(7);
                        break;
                    case MotionEvent.ACTION_CANCEL:
                        cameraControl(7);
                        break;
                }

                return false; // 返回false表示允许其他事件监听器继续处理这个事件
            }
        });
    }

    @SuppressLint("ClickableViewAccessibility")
    private void setSmalltButtonListener() {
        ImageButton button = findViewById(R.id.small);
        button.setOnTouchListener(new View.OnTouchListener() {
            @Override
            public boolean onTouch(View v, MotionEvent event) {
                switch (event.getAction()) {
                    case MotionEvent.ACTION_DOWN:
                        cameraControl(6);
                        break;
                    case MotionEvent.ACTION_UP:
                        cameraControl(7);
                        break;
                    case MotionEvent.ACTION_CANCEL:
                        cameraControl(7);
                        break;
                }

                return false; // 返回false表示允许其他事件监听器继续处理这个事件
            }
        });
    }

    private void useMediaProfileAndServices(OnvifMediaProfile mediaProfile, OnvifServices services) {
        onvifManager.getMediaStreamURI(device, mediaProfile, new OnvifMediaStreamURIListener() {
            @Override
            public void onMediaStreamURIReceived(OnvifDevice device, OnvifMediaProfile profile, String uri) {
                setLayout(uri);
            }
        });

        onvifManager.getConfigurations(device, (onvifDevice, configurations) -> {

        });

        this.services = services;
        this.mediaProfile = mediaProfile;
        canMove = true;
    }

    private void handleError(Throwable error) {

    }

    public void button_stop(View view) {
        cameraControl(7);
    }

    public void button_abs(View view) {
        cameraControl(0);
    }

    private void cameraControl(int direction) {
        if (canMove) {
            switch (direction) {
                case 0:
                    onvifManager.sendOnvifRequest(device, new PTZAbsoluteMove(mediaProfile, 0f, -0.5f, 0f));
                    break;
                case 1:
                    onvifManager.sendOnvifRequest(device, new PTZContinuesMove(mediaProfile, 0f, 0.3f, 0f));
                    break;
                case 2:
                    onvifManager.sendOnvifRequest(device, new PTZContinuesMove(mediaProfile, 0f, -0.3f, 0f));
                    break;
                case 3:
                    onvifManager.sendOnvifRequest(device, new PTZContinuesMove(mediaProfile, -0.3f, 0f, 0f));
                    break;
                case 4:
                    onvifManager.sendOnvifRequest(device, new PTZContinuesMove(mediaProfile, 0.3f, 0f, 0f));
                    break;
                case 5:
                    onvifManager.sendOnvifRequest(device, new PTZContinuesMove(mediaProfile, 0f, 0f, 1f));
                    break;
                case 6:
                    onvifManager.sendOnvifRequest(device, new PTZContinuesMove(mediaProfile, 0f, 0f, -1f));
                    break;
                case 7:
                    onvifManager.sendOnvifRequest(device, new PTZStop(mediaProfile));
                    break;
            }
        }
    }

    //流媒体
    private void setLayout(String url) {
        try {
            Media media = new Media(mLibVLC, Uri.parse(url));
            media.addOption(":network-caching=100");
            mMediaPlayer.setMedia(media);

            mMediaPlayer.setEventListener(new MediaPlayer.EventListener() {
                @Override
                public void onEvent(MediaPlayer.Event event) {
                    switch (event.type) {
                        case MediaPlayer.Event.Buffering:
                            if (event.getBuffering() == 300) {
                                // 媒体加载完成
                            }
                            break;
                        case MediaPlayer.Event.EndReached:
                            // 媒体播放结束
                            break;
                        case MediaPlayer.Event.EncounteredError:
                            // 发生错误
                            Toast.makeText(getApplicationContext(), "流媒体播放失败，请重新刷新。", Toast.LENGTH_SHORT).show();
                            break;
                    }
                }
            });
        } catch (Exception e) {
            e.printStackTrace();
        }
        mMediaPlayer.play();
    }

    /*
     * OPENCV
     * */
    private boolean initOpenCV() {
        if (OpenCVLoader.initLocal()) {
            Log.i(TAG, "OpenCV loaded successfully");
        } else {
            Log.e(TAG, "OpenCV initialization failed!");
            (Toast.makeText(this, "OpenCV initialization failed!", Toast.LENGTH_LONG)).show();
            return false;
        }

        byte[] buffer;
        try {
            // load cascade file from application resources
            InputStream is = getResources().openRawResource(R.raw.face_detection_yunet_2023mar);

            int size = is.available();
            buffer = new byte[size];
            int bytesRead = is.read(buffer);
            is.close();
        } catch (IOException e) {
            e.printStackTrace();
            Log.e(TAG, "Failed to ONNX model from resources! Exception thrown: " + e);
            (Toast.makeText(this, "Failed to ONNX model from resources!", Toast.LENGTH_LONG)).show();
            return false;
        }

        modelBuffer = new MatOfByte(buffer);
        configBuffer = new MatOfByte();

        faceDetector = FaceDetectorYN.create("onnx", modelBuffer, configBuffer, new Size(320, 320));
        if (faceDetector == null) {
            Log.e(TAG, "Failed to create FaceDetectorYN!");
            (Toast.makeText(this, "Failed to create FaceDetectorYN!", Toast.LENGTH_LONG)).show();
            return false;
        } else {
            Log.i(TAG, "FaceDetectorYN initialized successfully!");
        }
        return true;
    }

    private void startTimer() {
        mHandler.postDelayed(mRunnable, 1000); // 首次延迟1秒后执行
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        mHandler.removeCallbacksAndMessages(null);
    }

    private Handler mHandler = new Handler();
    private Runnable mRunnable = new Runnable() {
        @Override
        public void run() {
            // 在这里放置每秒钟要执行的代码
            doSomethingEverySecond(true);

            // 重新调度任务，使其每秒钟执行一次
            mHandler.postDelayed(this, 1000); // 1000毫秒等于1秒
        }
    };

    private void doSomethingEverySecond(boolean isSecond) {
        Bitmap screenshot = mVideoLayout.getBitmap();
        if (screenshot != null) {
            Mat frame = new Mat();

            // 读取新的帧
            rgba = new Mat();
            bgr = new Mat();
            bgrScaled = new Mat();
            faces = new Mat();
            Utils.bitmapToMat(screenshot, frame);
            Mat resultMat = onCameraFrame(frame, isSecond);

            rgba.release();
            bgr.release();
            bgrScaled.release();
            faces.release();
            frame.release();

            // 释放资源
            frame.release();
        }
    }

    /*
     * 点击屏幕按钮
     * */
    private void setScreenShotListener(boolean isSecond) {
//        AppCompatButton mScreenshotButton = findViewById(R.id.screenshot);
//        mScreenshotButton.setOnClickListener(new View.OnClickListener() {
//            @Override
//            public void onClick(View v) {
//                Bitmap screenshot = mVideoLayout.getBitmap();
//                if (screenshot != null) {
//                    Mat frame = new Mat();
//
//                    // 读取新的帧
//                    rgba = new Mat();
//                    bgr = new Mat();
//                    bgrScaled = new Mat();
//                    faces = new Mat();
//                    Utils.bitmapToMat(screenshot, frame);
//                    Mat resultMat = onCameraFrame(frame, isSecond);
//
//                    rgba.release();
//                    bgr.release();
//                    bgrScaled.release();
//                    faces.release();
//                    frame.release();
//
//                    // 释放资源
//                    frame.release();
//                }
//            }
//        });
    }

    /*
     * opencv
     * */
    public Mat onCameraFrame(Mat inputFrame, boolean isSecond) {
        inputFrame.convertTo(rgba, Imgproc.COLOR_YUV2RGBA_NV21);

        if (inputSize == null) {
            inputSize = new Size(Math.round(rgba.cols() / scale), Math.round(rgba.rows() / scale));
            faceDetector.setInputSize(inputSize);
        }

        Imgproc.cvtColor(rgba, bgr, Imgproc.COLOR_RGBA2BGR);
        Imgproc.resize(bgr, bgrScaled, inputSize);

        if (faceDetector != null) {
            int status = faceDetector.detect(bgrScaled, faces);
            Log.d(TAG, "Detector returned status " + status);
            visualize(rgba, faces, isSecond);
        }

        return rgba;
    }


    public void visualize(Mat rgba, Mat faces, boolean isSecond) {
        int thickness = 2;
        float[] faceData = new float[faces.cols() * faces.channels()];

        List<float[]> rectanglesCoordinates = new ArrayList<>();

        Log.d(TAG, "Detected faces.count :" + faces.rows());
        for (int i = 0; i < faces.rows(); i++) {
            faces.get(i, 0, faceData);

            Float x1 = scale * faceData[0] - 50;
            Float y1 = scale * faceData[1] - 50;
            Float x2 = scale * faceData[0] + scale * faceData[2] + 50;
            Float y2 = scale * faceData[1] + scale * faceData[3] + 50;

            Log.d(TAG, "Detected face (" + (scale * faceData[0]) + ", " + (scale * faceData[1]) + ", " +
                    (scale * faceData[2]) + ", " + (scale * faceData[3]) + ")");

            rectanglesCoordinates.add(new float[]{x1, y1, x2, y2});
        }
        if (isSecond) {
            clickRectangles.clear();
            clickRectangles = rectanglesCoordinates;
        }
        FrameLayout container = findViewById(R.id.rectangles_container);
        container.removeAllViews();
        for (float[] coords : rectanglesCoordinates) {
            CustomRectangleView rectangleView = new CustomRectangleView(this);
            rectangleView.setCoordinates(coords[0], coords[1], coords[2], coords[3]);
            container.addView(rectangleView);
        }
    }
}