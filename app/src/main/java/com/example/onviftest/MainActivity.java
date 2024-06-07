package com.example.onviftest;

import android.Manifest;
import android.annotation.SuppressLint;
import android.graphics.Bitmap;
import android.graphics.SurfaceTexture;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import android.util.Pair;
import android.view.MotionEvent;
import android.view.TextureView;
import android.view.View;
import android.view.WindowManager;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.FrameLayout;
import android.widget.ImageButton;
import android.widget.ProgressBar;
import android.widget.Spinner;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.view.GestureDetectorCompat;
import androidx.fragment.app.FragmentManager;
import androidx.fragment.app.FragmentTransaction;

import com.example.onviftest.fragment.SettingsFragment;
import com.example.onviftest.model.Camera;
import com.example.onviftest.model.Device;
import com.example.onviftest.ptz.PTZAbsoluteMove;
import com.example.onviftest.ptz.PTZContinuesMove;
import com.example.onviftest.ptz.PTZStop;
import com.example.onviftest.utiliy.ConfigManger;
import com.example.onviftest.utiliy.CustomRectangleView;
import com.example.onviftest.utiliy.OnSwipeTouchListener;
import com.example.onviftest.utiliy.PermissionCheckUtil;
import com.example.onviftest.utiliy.BitmapSaver;

import org.videolan.libvlc.LibVLC;
import org.videolan.libvlc.Media;
import org.videolan.libvlc.MediaPlayer;

import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.Timer;
import java.util.TimerTask;
import java.util.stream.Collectors;

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

public class MainActivity extends AppCompatActivity implements IOnBackPressed {
    private TextureView mVideoLayout = null;
    private LibVLC mLibVLC = null;
    private MediaPlayer mMediaPlayer = null;

    private boolean canMove = false;

    OnvifManager onvifManager = null;

    OnvifDevice device = null;

    OnvifMediaProfile mediaProfile = null;

    OnvifServices services = null;

    private static final String TAG = "OCVSample::Activity";

    private Timer mTimer;
    private ProgressBar progressBar;

    @SuppressLint("CheckResult")
    @Override
    protected void onCreate(Bundle savedInstanceState) /**/ {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        hideSystemUI();

        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        checkPermission();
        progressBar = findViewById(R.id.progressBar);

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

                setBackgroundTask();
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
        setFingerListener();
        setCombobox();

        initOnvif();
    }

    private boolean isZoom = false;
    List<float[]> clickRectangles = new ArrayList<>();

    /**
     * 初始化onvif
     */
    @SuppressLint("CheckResult")
    private void initOnvif() {
        onvifManager = new OnvifManager(new OnvifResponseListener() {
            public void onResponse(OnvifDevice onvifDevice, OnvifResponse onvifResponse) {
                Log.i("response", "PTZ response: " + onvifResponse.getXml());
            }

            public void onError(OnvifDevice onvifDevice, int errorCode, String errorMsg) {
                Log.i("error", "ErrorCode：" + errorCode + "Error Msg：" + errorMsg);
                runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        progressBar.setVisibility(View.VISIBLE);
                        Toast.makeText(MainActivity.this, errorMsg, Toast.LENGTH_SHORT).show();
                    }
                });
            }
        });

        setOnvifConfig();
        //获取device
        //得到172.16.13.53
//        Toast.makeText(this, ip, Toast.LENGTH_SHORT).show();

        device = new OnvifDevice(ip.isEmpty() ? "192.168.1.1" : ip, "admin", "admin");
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
                        runOnUiThread(new Runnable() {
                            @Override
                            public void run() {
                                progressBar.setVisibility(View.GONE);
                            }
                        });
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
                Pair::new
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
    }

    private String ip = "";
    private double x = 0.0;
    private double y = 0.0;

    private void setOnvifConfig() {
        Camera camera = ConfigManger.loadDeviceFromJson(this);
        int index = camera.getDeviceList().size() <= camera.getCurrentDevice() ? 0 : camera.getCurrentDevice();
        if (camera.getDeviceList().size() > 0) {
            Device device = camera.getDeviceList().get(index);
            ip = device.getIp();
            x = device.getX();
            y = device.getY();
        }
    }

    /**
     * 单指滑动
     */
    private void setFingerListener() {
        mVideoLayout.setOnTouchListener(new OnSwipeTouchListener(getApplicationContext()) {
            @Override
            public void onSwipeEnd(SwipeDirection direction, float proportionX, float proportionY) {
                try {
                    onvifManager.getStatus(device, mediaProfile, (onvifDevice, profile, status) -> {
                        double currentPan = status.getPan();
                        double currentTilt = status.getTilt();
                        double currentZoom = status.getZoom();

                        if (direction == SwipeDirection.LEFT || direction == SwipeDirection.RIGHT) {
                            onvifManager.sendOnvifRequest(device, new PTZAbsoluteMove(mediaProfile, currentPan + (proportionX / 2), currentTilt, currentZoom));
                        } else if (direction == SwipeDirection.UP || direction == SwipeDirection.DOWN) {
                            onvifManager.sendOnvifRequest(device, new PTZAbsoluteMove(mediaProfile, currentPan, currentTilt - (proportionY / 2), currentZoom));
                        }
                    });
                } catch (Exception e) {
                    Log.e("Swipe End", e.toString());
                }
            }

            //双指缩放
            @Override
            public void onPinchEnd(float totalScaleFactor) {
                try {
                    onvifManager.getStatus(device, mediaProfile, (onvifDevice, profile, status) -> {
                        double currentPan = status.getPan();
                        double currentTilt = status.getTilt();
                        double currentZoom = status.getZoom();
                        double reduce = currentZoom - (1 - totalScaleFactor);
                        double amplify = (totalScaleFactor - 1) < 1 ? currentZoom + ((totalScaleFactor - 1) / 2) : 1;

                        if (totalScaleFactor < 1) {//缩小
                            onvifManager.sendOnvifRequest(device, new PTZAbsoluteMove(mediaProfile, currentPan, currentTilt, reduce > 0 ? reduce : 0));
                        } else {//放大
                            onvifManager.sendOnvifRequest(device, new PTZAbsoluteMove(mediaProfile, currentPan, currentTilt, amplify));
                        }
                    });
                } catch (Exception e) {
                    Log.e("Swipe End", e.toString());
                }

//                Toast.makeText(getApplicationContext(), String.format("缩放 $%.2f，缩放：$%.2f", currentZoom, totalScaleFactor), Toast.LENGTH_SHORT).show();
            }

            @Override
            public void onDoubleTap(MotionEvent e) {
                doubleClick(e);
            }
        });
    }

    private Spinner spinner;
    private ArrayAdapter<String> adapter;
    private List<String> dataList;
    private Camera camera;

    /**
     * 设置摄像头Combobox
     */
    private void setCombobox() {
        camera = ConfigManger.loadDeviceFromJson(this);
        ArrayList<String> nameList = camera.getDeviceList().stream()
                .map(Device::getName)
                .collect(Collectors.toCollection(ArrayList::new));

        dataList = nameList.size() > 0 ? nameList : new ArrayList<>();
        spinner = findViewById(R.id.spinner);
        adapter = new ArrayAdapter<>(this, R.layout.spinner_item, dataList);
        adapter.setDropDownViewResource(R.layout.dropdown_spinner_item);
        spinner.setAdapter(adapter);
        int index = camera.getDeviceList().size() <= camera.getCurrentDevice() ? 0 : camera.getCurrentDevice();

        spinner.setSelection(index);

        spinner.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
                // 获取选中的项
                //String selectedItem = parent.getItemAtPosition(position).toString();
                camera.setCurrentDevice(position);
                ConfigManger.saveDeviceToJson(getApplicationContext(), camera);
                initOnvif();
            }

            @Override
            public void onNothingSelected(AdapterView<?> parent) {
                // 当没有选中任何项时的处理
            }
        });
    }

    //双击屏幕位置
    @SuppressLint("ClickableViewAccessibility")
    private void doubleClick(MotionEvent e) {
        doSomethingEverySecond(false);
        Log.d("click face", String.valueOf(clickRectangles.stream().count()));

        // 获取点击事件发生时的位置坐标
        float xClickPos = e.getX();
        float yClickPos = e.getY();

        // 获取FrameLayout的宽高及中心点坐标
        int layoutWidth = mVideoLayout.getWidth();
        int layoutHeight = mVideoLayout.getHeight();
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

                            Log.d("percentx", String.valueOf(percentX));
                            Log.d("percenty", String.valueOf(percentY));

                            //默认x0.18。y0.64
                            double newPan = currentPan + (percentX * x);
                            double newTilt = currentTilt - (percentY * y);
                            if (newTilt < -0.60) {
                                newTilt = -0.60;
                            }

                            Log.d("newPanx", String.valueOf(newPan));
                            Log.d("newPany", String.valueOf(newTilt));

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
    }

    /**
     * 点击按钮事件
     */
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
        Toast.makeText(getApplicationContext(), String.format("错误：%s", error.toString()), Toast.LENGTH_SHORT);
    }

    /**
     * 停止
     *
     * @param view
     */
    public void button_stop(View view) {
        cameraControl(7);
    }

    /**
     * 设置界面
     *
     * @param view
     */
    public void button_settings(View view) {
        FragmentManager fragmentManager = getSupportFragmentManager();
        FragmentTransaction fragmentTransaction = fragmentManager.beginTransaction();
        fragmentTransaction.add(R.id.fragment_container_view, SettingsFragment.newInstance());
        fragmentTransaction.addToBackStack(null);
        fragmentTransaction.commit();
    }

    /**
     * 复位
     *
     * @param view
     */
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

    @Override
    protected void onDestroy() {
        super.onDestroy();
        mTimer.cancel();
    }

    public void setBackgroundTask() {
        mTimer = new Timer();
        mTimer.scheduleAtFixedRate(new TimerTask() {
            @Override
            public void run() {
                // 执行你的任务
                doSomethingEverySecond(false);

                mHandler.post(new Runnable() {
                    @Override
                    public void run() {
                        // 在主线程执行需要的任务
                        doSomethingInMain();
                    }
                });
            }
        }, 0, 1000); // 初始延迟为0，之后每秒执行一次
    }


    private void doSomethingEverySecond(boolean isSecond) {
        Bitmap screenshot = mVideoLayout.getBitmap();
        boolean isSuccess = BitmapSaver.saveBitmapToFile(this, screenshot, "person", Bitmap.CompressFormat.JPEG);
        if (isSuccess) {
            reload();
        }
    }

    private Handler mHandler = new Handler(Looper.getMainLooper());

    private void doSomethingInMain() {
        clickRectangles = yolov8ncnn.getRectanglesCoordinates();
        FrameLayout container = findViewById(R.id.rectangles_container);
        container.removeAllViews();
        for (float[] coords : clickRectangles) {
            CustomRectangleView rectangleView = new CustomRectangleView(this);
            rectangleView.setCoordinates(coords[0], coords[1], coords[2], coords[3]);
            container.addView(rectangleView);
        }
    }


    private Yolov8Ncnn yolov8ncnn = new Yolov8Ncnn();

    private void reload() {
        boolean ret_init = yolov8ncnn.loadModel(getAssets(), 0, 0);
        Log.i(TAG, "loadModel result=" + ret_init);
        File file = new File(this.getExternalFilesDir(null), "person.jpeg");
        yolov8ncnn.checkImagePose(file.getAbsolutePath());
    }

    private void checkPermission() {
        //读写文件
        String WRITE_EXTERNAL_STORAGE = Manifest.permission.WRITE_EXTERNAL_STORAGE;
        PermissionCheckUtil permissionCheckUtil = PermissionCheckUtil.with(MainActivity.this);
        permissionCheckUtil.permissions(WRITE_EXTERNAL_STORAGE).check(1008, new PermissionCheckUtil.OnPermissionCheckListener() {
            @Override
            public void onPermissionGranted(int requestCode) {
                Log.i(TAG, "###onPermissionGranted###");
                if (requestCode == 1008) {
//                    reload();
                }
            }

            @Override
            public void onPermissionDenied(int requestCode, String[] deniedPermissions) {
                Log.i(TAG, "###onPermissionDenied###");
                if (requestCode == 1008) {
                }
            }

            @Override
            public void onNeverAsked(int requestCode, String[] deniedPermissions) {

            }
        });
    }

    /**
     * 关闭上下状态栏
     */
    private void hideSystemUI() {
        // 启用沉浸模式
        View decorView = getWindow().getDecorView();
        decorView.setSystemUiVisibility(
                View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY
                        | View.SYSTEM_UI_FLAG_LAYOUT_STABLE
                        | View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
                        | View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
                        | View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
                        | View.SYSTEM_UI_FLAG_FULLSCREEN);
    }

    @Override
    public void onBackPressedFromFragment() {
        getSupportFragmentManager().popBackStack();
        setCombobox();
    }
}