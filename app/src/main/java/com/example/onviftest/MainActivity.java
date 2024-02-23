package com.example.onviftest;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.AppCompatButton;
import androidx.core.view.GestureDetectorCompat;

import android.annotation.SuppressLint;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.util.Log;
import android.util.Pair;
import android.view.GestureDetector;
import android.view.MotionEvent;
import android.view.View;
import android.widget.FrameLayout;
import android.widget.Toast;

import org.videolan.libvlc.LibVLC;
import org.videolan.libvlc.Media;
import org.videolan.libvlc.MediaPlayer;
import org.videolan.libvlc.util.VLCVideoLayout;

import java.time.Instant;
import java.util.ArrayList;
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
    private VLCVideoLayout mVideoLayout = null;
    private LibVLC mLibVLC = null;
    private MediaPlayer mMediaPlayer = null;

    private boolean canMove = false;

    OnvifManager onvifManager = null;

    OnvifDevice device = null;

    OnvifMediaProfile mediaProfile = null;

    OnvifServices services = null;

    @SuppressLint("CheckResult")
    @Override
    protected void onCreate(Bundle savedInstanceState) /**/ {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        mVideoLayout = findViewById(R.id.videoLayout);

        setUpButtonListener();
        setDownButtonListener();
        setLeftButtonListener();
        setRightButtonListener();
        setBigButtonListener();
        setSmalltButtonListener();
        setLayoutListener();

        onvifManager = new OnvifManager(new OnvifResponseListener() {
            public void onResponse(OnvifDevice onvifDevice, OnvifResponse onvifResponse) {
                Log.i("response", "PTZ response: " + onvifResponse.getXml());
            }

            public void onError(OnvifDevice onvifDevice, int errorCode, String errorMsg) {
                Log.i("error", "ErrorCode：" + errorCode + "Error Msg：" + errorMsg);
            }
        });

        //获取device
        device = new OnvifDevice("172.16.23.210", "admin", "admin");
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
    }

    private boolean isZoom = false;
    GestureDetectorCompat gestureDetector;

    //点击屏幕位置
    @SuppressLint("ClickableViewAccessibility")
    private void setLayoutListener() {
        VLCVideoLayout frameLayout = findViewById(R.id.videoLayout);
        gestureDetector = new GestureDetectorCompat(this, new GestureDetector.SimpleOnGestureListener() {
            @Override
            public boolean onDoubleTap(@NonNull MotionEvent e) {
                // 获取点击事件发生时的位置坐标
                float xClickPos = e.getX();
                float yClickPos = e.getY();

                // 获取FrameLayout的宽高及中心点坐标
                int layoutWidth = frameLayout.getWidth();
                int layoutHeight = frameLayout.getHeight();
                float centerX = layoutWidth / 2f;
                float centerY = layoutHeight / 2f;

                // 计算点击位置与中心点之间的差值以及百分比
                float dxFromCenter = xClickPos - centerX;
                float dyFromCenter = yClickPos - centerY;

                // 计算百分比
                float percentX = dxFromCenter / centerX;
                float percentY = dyFromCenter / centerY;

                onvifManager.getStatus(device, mediaProfile, (onvifDevice, profile, status) -> {
                    double currentPan = status.getPan();
                    double currentTilt = status.getTilt();
                    Log.d("pan", String.valueOf(currentPan));
                    Log.d("tilt", String.valueOf(currentTilt));
                    Log.d("zoom", String.valueOf(status.getZoom()));
                    //x-整屏幕0.3
                    //y-整屏幕1.1
                    double newPan = currentPan + (percentX * 0.15);
                    double newTilt = currentTilt - (percentY * 0.55);
                    if(isZoom) {
                        onvifManager.sendOnvifRequest(device, new PTZAbsoluteMove(mediaProfile, currentPan, currentTilt, 0f));
                        isZoom = false;
                    } else {
                        onvifManager.sendOnvifRequest(device, new PTZAbsoluteMove(mediaProfile, newPan, newTilt, 1f));
                        isZoom = true;
                    }
                });

                // 输出或处理百分比信息
                Log.d("Position", "Clicked position: X offset from center is " + percentX + "%, Y offset from center is " + percentY + "%");
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
        AppCompatButton button = findViewById(R.id.up);
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
        AppCompatButton button = findViewById(R.id.down);
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
        AppCompatButton button = findViewById(R.id.left);
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
        AppCompatButton button = findViewById(R.id.right);
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
        AppCompatButton button = findViewById(R.id.big);
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
        AppCompatButton button = findViewById(R.id.small);
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
                    onvifManager.sendOnvifRequest(device, new PTZAbsoluteMove(mediaProfile, 0.5f, 0f, 0f));
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
        final ArrayList<String> args = new ArrayList<>();
        args.add("-vvv");

        mLibVLC = new LibVLC(getApplicationContext(), args);


        if (mMediaPlayer != null && mMediaPlayer.isPlaying()) {
            mMediaPlayer.stop();
            mMediaPlayer.release();
        }
        mMediaPlayer = new MediaPlayer(mLibVLC);
        runOnUiThread(new Runnable() {

            @Override
            public void run() {
                mMediaPlayer.attachViews(mVideoLayout, null, ENABLE_SUBTITLES, USE_TEXTURE_VIEW);
            }
        });

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
}