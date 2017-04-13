package com.devlin_n.magic_player;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.ActivityInfo;
import android.graphics.PixelFormat;
import android.media.AudioManager;
import android.net.ConnectivityManager;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.util.AttributeSet;
import android.util.Log;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.MotionEvent;
import android.view.SurfaceHolder;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewTreeObserver;
import android.view.WindowManager;
import android.widget.FrameLayout;
import android.widget.ProgressBar;
import android.widget.RelativeLayout;
import android.widget.Toast;

import java.io.IOException;
import java.util.List;

import tv.danmaku.ijk.media.player.IMediaPlayer;
import tv.danmaku.ijk.media.player.IjkMediaPlayer;

/**
 * 播放器
 * Created by Devlin_n on 2017/4/7.
 */

public class IjkVideoView extends FrameLayout implements SurfaceHolder.Callback, IjkMediaController.MediaPlayerControlInterface,
        IMediaPlayer.OnErrorListener, IMediaPlayer.OnCompletionListener, IMediaPlayer.OnInfoListener,
        IMediaPlayer.OnBufferingUpdateListener, IMediaPlayer.OnPreparedListener, IMediaPlayer.OnVideoSizeChangedListener {

    private static final String TAG = IjkVideoView.class.getSimpleName();
    private IjkMediaPlayer mMediaPlayer;
    private BaseMediaController mMediaController;
    private View videoView;
    private boolean isPlayed;
    private MySurfaceView surfaceView;
    private RelativeLayout surfaceContainer;
    private StatusView statusView;
    private int bufferPercentage;
    private ProgressBar bufferProgress;
    private WindowManager wm;
    private WindowManager.LayoutParams wmParams;
    private static boolean isOpenFloatWindow = false;
    private int originalWidth, originalHeight;
    private boolean isFullScreen;
    private View floatView;
    private String mCurrentUrl;
    private boolean isCallPause;
    private NetChangeReceiver netChangeReceiver;

    public static final int VOD = 1;
    public static final int LIVE = 2;
    public static final int AD = 3;
    private List<VideoModel> mVideoModels;
    private int mCurrentVideoPosition = 0;
    private int mCurrentPosition;
    private String mCurrentTitle = "";
    private static boolean IS_PLAY_ON_MOBILE_NETWORK = false;

    public IjkVideoView(@NonNull Context context) {
        super(context);
        init();
    }


    public IjkVideoView(@NonNull Context context, @Nullable AttributeSet attrs) {
        super(context, attrs);
        init();
    }

    private void init() {
        if (isOpenFloatWindow) {
            MyWindowManager.cleanManager();
            isOpenFloatWindow = false;
        }
        videoView = LayoutInflater.from(getContext()).inflate(R.layout.layout_video_view, this);
        bufferProgress = (ProgressBar) videoView.findViewById(R.id.buffering);
        surfaceContainer = (RelativeLayout) videoView.findViewById(R.id.surface_container);
        getViewTreeObserver().addOnGlobalLayoutListener(new ViewTreeObserver.OnGlobalLayoutListener() {
            @Override
            public void onGlobalLayout() {
                originalWidth = getWidth();
                originalHeight = getHeight();
                if (originalWidth != -1 && originalHeight != -1) {
                    getViewTreeObserver().removeOnGlobalLayoutListener(this);
                }
            }
        });
        registerNetReceiver();
    }

    private void openVideo() {
        if (NetUtil.getNetworkType(getContext()) == 4 && !IS_PLAY_ON_MOBILE_NETWORK) {
            if (statusView == null) {
                statusView = new StatusView(getContext());
            }
            statusView.setMessage(getResources().getString(R.string.wifi_tip));
            statusView.setButtonTextAndAction(getResources().getString(R.string.continue_play), new OnClickListener() {
                @Override
                public void onClick(View v) {
                    IS_PLAY_ON_MOBILE_NETWORK = true;
                    removeView(statusView);
                    openVideo();
                }
            });
            addView(statusView);
            return;
        }
        if (mCurrentUrl == null || mCurrentUrl.trim().equals("")) return;
        AudioManager am = (AudioManager) getContext().getSystemService(Context.AUDIO_SERVICE);
        am.requestAudioFocus(null, AudioManager.STREAM_MUSIC, AudioManager.AUDIOFOCUS_GAIN);
        try {
            if (mMediaPlayer != null) mMediaPlayer.release();
            mMediaPlayer = new IjkMediaPlayer();
            mMediaPlayer.setDataSource(mCurrentUrl);
            mMediaPlayer.setScreenOnWhilePlaying(true);
            mMediaPlayer.setAudioStreamType(AudioManager.STREAM_MUSIC);
            mMediaPlayer.setOnErrorListener(this);
            mMediaPlayer.setOnCompletionListener(this);
            mMediaPlayer.setOnInfoListener(this);
            mMediaPlayer.setOnBufferingUpdateListener(this);
            mMediaPlayer.setOnPreparedListener(this);
            mMediaPlayer.setOnVideoSizeChangedListener(this);
            mMediaPlayer.prepareAsync();
        } catch (IOException e) {
            e.printStackTrace();
        }

        bufferProgress.setVisibility(VISIBLE);
        addSurfaceView();
    }

    private void addSurfaceView() {
        surfaceContainer.removeAllViews();
        surfaceView = new MySurfaceView(getContext());
        surfaceView.setLayoutParams(new RelativeLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.MATCH_PARENT));
        SurfaceHolder surfaceHolder = surfaceView.getHolder();
        surfaceHolder.addCallback(this);
        surfaceHolder.setFormat(PixelFormat.RGBA_8888);
        surfaceContainer.addView(surfaceView);
    }

    @Override
    public void surfaceCreated(SurfaceHolder holder) {
        if (mMediaController != null) mMediaController.hide();
    }

    @Override
    public void surfaceChanged(SurfaceHolder holder, int format, int width, int height) {
        if (mMediaPlayer != null) {
            mMediaPlayer.setDisplay(holder);
        }
    }

    @Override
    public void surfaceDestroyed(SurfaceHolder holder) {

    }

    public void setUrl(String url) {
        this.mCurrentUrl = url;
        openVideo();
    }


    public void setVideos(List<VideoModel> videoModels) {
        this.mVideoModels = videoModels;
        playNext();
    }

    public void setTitle(String title) {
        if (title != null) {
            this.mCurrentTitle = title;
        }
    }

    private void playNext() {
        if (mCurrentVideoPosition >= mVideoModels.size()) return;
        VideoModel videoModel = mVideoModels.get(mCurrentVideoPosition);
        if (videoModel != null) {
            mCurrentUrl = videoModel.url;
            mCurrentTitle = videoModel.title;
            openVideo();
            setMediaController(videoModel.type);
        }
    }

    @Override
    public void start() {
        if (isOpenFloatWindow || mMediaPlayer == null) return;
        if (!mMediaPlayer.isPlaying()) {
            mMediaPlayer.start();
            Log.d(TAG, "start: called");
        }
    }

    @Override
    public void pause() {
        if (isOpenFloatWindow || mMediaPlayer == null) return;
        if (mMediaPlayer.isPlaying()) {
            mMediaPlayer.pause();
            Log.d(TAG, "pause: called");
        }
        isCallPause = true;
    }

    public void resume() {
        if (isOpenFloatWindow || mMediaPlayer == null) return;
        if (isPlayed && !mMediaPlayer.isPlaying()) {
            Log.d(TAG, "resume: called");
            mMediaPlayer.start();
        }
    }

    public void stopPlayback() {
        if (mMediaPlayer != null) {
            mMediaPlayer.stop();
            mMediaPlayer.release();
            mMediaPlayer = null;
            AudioManager am = (AudioManager) getContext().getSystemService(Context.AUDIO_SERVICE);
            am.abandonAudioFocus(null);
        }
    }

    public void release() {
        if (isOpenFloatWindow) return;
        if (mMediaPlayer != null) {
            mMediaPlayer.reset();
            mMediaPlayer.release();
            mMediaPlayer = null;
            AudioManager am = (AudioManager) getContext().getSystemService(Context.AUDIO_SERVICE);
            am.abandonAudioFocus(null);
        }

        if (mMediaController != null) mMediaController.destroy();
        unregisterNetReceiver();
    }

    @Override
    public int getDuration() {
        if (mMediaPlayer != null) {
            return (int) mMediaPlayer.getDuration();
        }
        return 0;
    }

    @Override
    public int getCurrentPosition() {
        if (mMediaPlayer != null) {
            mCurrentPosition = (int) mMediaPlayer.getCurrentPosition();
            return mCurrentPosition;
        }
        return 0;
    }

    @Override
    public void seekTo(int pos) {
        mMediaPlayer.seekTo(pos);
    }

    @Override
    public boolean isPlaying() {
        return mMediaPlayer != null && mMediaPlayer.isPlaying();
    }

    @Override
    public int getBufferPercentage() {
        if (mMediaPlayer != null) {
            return bufferPercentage;
        }
        return 0;
    }

    @Override
    public void startFloatScreen() {
        if (isOpenFloatWindow) return;
        initWindow();
        floatView = LayoutInflater.from(getContext()).inflate(R.layout.layout_float_window, null);
        MyWindowManager.floatViews.add(floatView);
        MyWindowManager.ijkMediaPlayer = mMediaPlayer;
        final MySurfaceView surfaceView = (MySurfaceView) floatView.findViewById(R.id.float_sv);
        SurfaceHolder surfaceHolder = surfaceView.getHolder();
        surfaceHolder.addCallback(this);
        surfaceHolder.setFormat(PixelFormat.RGBA_8888);
        floatView.findViewById(R.id.btn_close).setOnClickListener(new OnClickListener() {
            @Override
            public void onClick(View v) {
                wm.removeView(floatView);
                mMediaPlayer.release();
                isOpenFloatWindow = false;
            }
        });
        floatView.setOnTouchListener(new OnTouchListener() {
            private int floatX;
            private int floatY;

            @Override
            public boolean onTouch(View v, MotionEvent event) {
                final int X = (int) event.getRawX();
                final int Y = (int) event.getRawY();

                switch (event.getAction() & MotionEvent.ACTION_MASK) {
                    case MotionEvent.ACTION_DOWN:
                        floatX = (int) event.getX();
                        floatY = (int) (event.getY() + WindowUtil.getStatusBarHeight(getContext()));
                        break;
                    case MotionEvent.ACTION_UP:
                    case MotionEvent.ACTION_MOVE:
                        wmParams.gravity = Gravity.START | Gravity.TOP;
                        wmParams.x = X - floatX;
                        wmParams.y = Y - floatY;
                        wm.updateViewLayout(floatView, wmParams);
                        break;
                }
                return false;
            }
        });
        wm.addView(floatView, wmParams);
        WindowUtil.getAppCompActivity(getContext()).finish();
        isOpenFloatWindow = true;
    }

    private void initWindow() {
        wm = MyWindowManager.getWindowManager(getContext());
        wmParams = new WindowManager.LayoutParams();
        wmParams.type = WindowManager.LayoutParams.TYPE_SYSTEM_ALERT; // 设置window type
        // 设置图片格式，效果为背景透明
        wmParams.format = PixelFormat.TRANSPARENT;
        wmParams.flags = WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE | WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL;
        wmParams.gravity = Gravity.END | Gravity.BOTTOM; // 调整悬浮窗口至左上角
        // 设置悬浮窗口长宽数据
        int width = WindowUtil.getScreenWidth(getContext()) / 2;
        wmParams.width = width;
        wmParams.height = width * 9 / 16;
    }

    @Override
    public void startFullScreen() {
        ViewGroup.LayoutParams layoutParams = this.getLayoutParams();
        WindowUtil.hideSupportActionBar(getContext(), true, true);
        layoutParams.width = WindowUtil.getScreenHeight(getContext());
        layoutParams.height = WindowUtil.getScreenWidth(getContext());
        this.setLayoutParams(layoutParams);
        isFullScreen = true;
    }

    @Override
    public void stopFullScreen() {
        ViewGroup.LayoutParams layoutParams = this.getLayoutParams();
        WindowUtil.showSupportActionBar(getContext(), true, true);
        layoutParams.width = originalWidth;
        layoutParams.height = originalHeight;
        this.setLayoutParams(layoutParams);
        isFullScreen = false;
    }

    @Override
    public boolean isFullScreen() {
        return isFullScreen;
    }

    @Override
    public String getTitle() {
        return mCurrentTitle;
    }

    public void setMediaController(int type) {
        removeView(mMediaController);
        switch (type) {
            case VOD: {
                IjkMediaController ijkMediaController = new IjkMediaController(getContext());
                ijkMediaController.setMediaPlayer(this);
                mMediaController = ijkMediaController;
                break;
            }
            case LIVE: {
                IjkMediaController ijkMediaController = new IjkMediaController(getContext());
                ijkMediaController.setMediaPlayer(this);
                ijkMediaController.setLive(true);
                mMediaController = ijkMediaController;
                break;
            }
            case AD:
                break;
            default:
                break;
        }
    }

    public void setMediaController(BaseMediaController mediaController) {
        if (mediaController != null) {
            mediaController.setMediaPlayer(this);
            mMediaController = mediaController;
        }
    }

    @Override
    public boolean onError(IMediaPlayer iMediaPlayer, int i, int i1) {
        bufferProgress.setVisibility(GONE);
        if (statusView == null) {
            statusView = new StatusView(getContext());
        }
        statusView.setMessage(getResources().getString(R.string.error_message));
        statusView.setButtonTextAndAction(getResources().getString(R.string.retry), new OnClickListener() {
            @Override
            public void onClick(View v) {
                removeView(statusView);
                openVideo();
            }
        });
        addView(statusView);
        return true;
    }

    @Override
    public void onCompletion(IMediaPlayer iMediaPlayer) {
        if (mMediaController != null) mMediaController.reset();
        mCurrentVideoPosition++;
        if (mVideoModels != null && mVideoModels.size() > 1)
            playNext();
    }

    @Override
    public boolean onInfo(IMediaPlayer iMediaPlayer, int what, int extra) {
        if (what == IMediaPlayer.MEDIA_INFO_BUFFERING_START) {
            if (mMediaController != null) mMediaController.hide();
            bufferProgress.setVisibility(VISIBLE);
        } else if (what == IMediaPlayer.MEDIA_INFO_BUFFERING_END) {
            bufferProgress.setVisibility(GONE);
        }
        return false;
    }

    @Override
    public void onBufferingUpdate(IMediaPlayer iMediaPlayer, int i) {
        bufferPercentage = i;
    }

    @Override
    public void onPrepared(IMediaPlayer iMediaPlayer) {
        bufferProgress.setVisibility(GONE);
        if (mMediaController != null) addView(mMediaController);
        if (isCallPause) mMediaPlayer.pause();
        isPlayed = true;
    }

    @Override
    public void onVideoSizeChanged(IMediaPlayer iMediaPlayer, int i, int i1, int i2, int i3) {
        int videoWidth = iMediaPlayer.getVideoWidth();
        int videoHeight = iMediaPlayer.getVideoHeight();
        if (videoWidth != 0 && videoHeight != 0) {
            surfaceView.setVideoSize(videoWidth, videoHeight);
            requestLayout();
        }
    }

    public boolean backFromFullScreen() {
        if (mMediaController.lockBack()) return true;
        if (isFullScreen) {
            stopFullScreen();
            WindowUtil.getAppCompActivity(getContext()).setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_PORTRAIT);
            mMediaController.updateFullScreen();
            return true;
        }
        return false;
    }

    public class NetChangeReceiver extends BroadcastReceiver {
        @Override
        public void onReceive(Context context, Intent intent) {
            if (NetUtil.getNetworkType(context) == 3) {// 网络是WIFI
                Toast.makeText(context, "WIFI", Toast.LENGTH_SHORT).show();
            } else if (NetUtil.getNetworkType(context) == 2
                    || NetUtil.getNetworkType(context) == 4) {// 网络是手机网络或者是以太网
                // TODO 更新状态是暂停状态
                Toast.makeText(context, "手机网络或者是以太网", Toast.LENGTH_SHORT).show();
            } else if (NetUtil.getNetworkType(context) == 1) {// 网络链接断开
                Toast.makeText(context, "网络链接断开", Toast.LENGTH_SHORT).show();
            } else {
                Toast.makeText(context, "网络已断开", Toast.LENGTH_SHORT).show();
            }
        }
    }

    /**
     * 注册网络监听器
     */
    private void registerNetReceiver() {
        if (netChangeReceiver == null) {
            IntentFilter filter = new IntentFilter(
                    ConnectivityManager.CONNECTIVITY_ACTION);
            netChangeReceiver = new NetChangeReceiver();
            WindowUtil.getAppCompActivity(getContext()).registerReceiver(netChangeReceiver, filter);
        }
    }

    /**
     * 销毁网络监听器
     */
    private void unregisterNetReceiver() {
        if (netChangeReceiver != null) {
            WindowUtil.getAppCompActivity(getContext()).unregisterReceiver(netChangeReceiver);
            netChangeReceiver = null;
        }
    }
}