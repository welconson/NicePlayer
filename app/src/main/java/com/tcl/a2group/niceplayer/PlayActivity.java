package com.tcl.a2group.niceplayer;

import android.Manifest;
import android.app.Service;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.ServiceConnection;
import android.content.pm.PackageManager;
import android.database.Cursor;
import android.media.AudioManager;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.os.Message;
import android.provider.MediaStore;
import android.support.annotation.NonNull;
import android.support.v7.app.AppCompatActivity;
import android.support.v7.widget.LinearLayoutManager;
import android.support.v7.widget.RecyclerView;
import android.support.v7.widget.Toolbar;
import android.util.Log;
import android.view.KeyEvent;
import android.view.View;
import android.widget.ImageView;
import android.widget.RelativeLayout;
import android.widget.SeekBar;
import android.widget.TextView;
import android.widget.Toast;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.List;

public class PlayActivity extends AppCompatActivity implements MusicService.OnPlayCompletedListener{
    public static Context context;
    private final static String TAG = "PlayActivity";
    private final static int REPEAT_COUNT_THRESHOLD = 10;
    private final static int MSG_FORWARD = 10;
    private final static int MSG_BACKWARD = 11;
    private final static int MSG_UPDATE_SEEK_BAR = 1;
    private final static int MSG_UPDATE_SONG_INFO = 2;
    private final static int MSG_RAISE_VOLUME = 3;
    private final static int MSG_LOWER_VOLUME = 4;

    private final static int MILLIS_PER_SECOND = 1000;
    private final static int TIME_ADJUST_UNIT = 1;
    private final static int ADJUST_UNIT_MILLIS = 10;
    private final static int VOLUME_MAX = 100;
    private final static int VOLUME_MIDDLE = 50;
    private final static int VOLUME_ADJUST_UNIT = 1;


    private Toolbar toolbar;
//    private AlbumViewPager viewPager;
    private RelativeLayout lrcViewContainer;
    private TextView musicPlayedDuration, musicDuration, musicName, musicArtist;
    private ImageView musicCover, playButton, playModeButton;
    private SeekBar playSeekBar, volumeSeekBar;
    private RecyclerView playListView;
    private boolean isPlayingBeforeAdjust = false;
    private boolean isAdjustingPlayer = false;
    private boolean isAdjustingVolume = false;
    private List<MusicAttribute> musicAttributeList;
    private AudioManager audioManager;
    private int playMode;

    private MusicService musicService;
    private SimpleDateFormat durationFormat = new SimpleDateFormat("mm:ss");

    // 绑定服务的连接类
    private ServiceConnection connection = new ServiceConnection() {
        @Override
        public void onServiceConnected(ComponentName name, IBinder service) {
            musicService = ((MusicService.MyBinder) (service)).getService();
            musicService.setMusicAttributeList(musicAttributeList);
            musicService.setPlayMode(playMode);
            initPlay();
            musicService.setOnPlayCompletedListener(PlayActivity.this);
            volumeSeekBar.setMax(VOLUME_MAX);
            //todo volumeSeekBar的显示控制
            volumeSeekBar.setVisibility(View.VISIBLE);
            if(audioManager != null){
                int currentVolume = audioManager.getStreamVolume(AudioManager.STREAM_MUSIC) * VOLUME_MAX
                        / audioManager.getStreamMaxVolume(AudioManager.STREAM_MUSIC);
                volumeSeekBar.setProgress(currentVolume);
                musicService.setVolume(currentVolume, VOLUME_MAX);
            }else {
                volumeSeekBar.setProgress(VOLUME_MIDDLE);
                musicService.setVolume(VOLUME_MIDDLE, VOLUME_MAX);
            }
//            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
//                playListView.setDefaultFocusHighlightEnabled(true);
//            }
//            Log.d(TAG, "onServiceConnected: recycler view has" + (playListView.requestFocus() ? "" : " not") + " got focused");
        }

        @Override
        public void onServiceDisconnected(ComponentName name) {
            musicService = null;
        }
    };

    // 主要用于处理长按操作，更新UI
    // 大多数消息发送者本身运行在UI线程
    private Handler handler = new Handler(Looper.getMainLooper()){
        @Override
        public void handleMessage(Message msg) {
            switch (msg.what) {
                case MSG_UPDATE_SEEK_BAR:{
                    playSeekBar.setProgress(musicService.getCurrentPosition());
                    musicPlayedDuration.setText(durationFormat.format(musicService.getCurrentPosition()));
                    handler.sendEmptyMessageDelayed(MSG_UPDATE_SEEK_BAR, MILLIS_PER_SECOND);
                    break;
                }
                case MSG_UPDATE_SONG_INFO:{
                    updateSongInfoUI(msg.arg1);
                    break;
                }
                case MSG_FORWARD: {
                    Log.d(TAG, "handleMessage: =====> msg forward");
                    int newPosition = musicService.getCurrentPosition() + MILLIS_PER_SECOND * TIME_ADJUST_UNIT;
                    if(newPosition < playSeekBar.getMax()) {
                        musicService.seekTo(newPosition);
                        playSeekBar.setProgress(musicService.getCurrentPosition());
                        musicPlayedDuration.setText(durationFormat.format(musicService.getCurrentPosition()));
                        handler.sendEmptyMessageDelayed(MSG_FORWARD, ADJUST_UNIT_MILLIS);
                    }
                    break;
                }
                case MSG_BACKWARD:{
                    int newPosition = musicService.getCurrentPosition() - MILLIS_PER_SECOND * TIME_ADJUST_UNIT;
                    if (newPosition > 0) {
                        musicService.seekTo(newPosition);
                        playSeekBar.setProgress(musicService.getCurrentPosition());
                        musicPlayedDuration.setText(durationFormat.format(musicService.getCurrentPosition()));
                        handler.sendEmptyMessageDelayed(MSG_BACKWARD, ADJUST_UNIT_MILLIS);
                    }
                    break;
                }
                case MSG_RAISE_VOLUME:{
                    volumeSeekBar.setProgress(volumeSeekBar.getProgress() + VOLUME_ADJUST_UNIT);
                    musicService.setVolume(volumeSeekBar.getProgress(), VOLUME_MAX);
                    handler.sendEmptyMessageDelayed(MSG_RAISE_VOLUME, ADJUST_UNIT_MILLIS);
                    break;
                }
                case MSG_LOWER_VOLUME:{
                    volumeSeekBar.setProgress(volumeSeekBar.getProgress() - VOLUME_ADJUST_UNIT);
                    musicService.setVolume(volumeSeekBar.getProgress(), VOLUME_MAX);
                    handler.sendEmptyMessageDelayed(MSG_LOWER_VOLUME, ADJUST_UNIT_MILLIS);
                    break;

                }
            }
        }
    };

    // 按键次数统计，用于在触发onKeyUp回调时，判断是否为长按操作
    private int keyCount;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        context = this;
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_play);
        bindService(new Intent(this, MusicService.class), connection, Service.BIND_AUTO_CREATE);

        Log.d(TAG, "onCreate: external music dir: " + getExternalFilesDir(Environment.DIRECTORY_MUSIC));

//        viewPager = findViewById(R.id.view_pager);
        lrcViewContainer = findViewById(R.id.lrcViewContainer);
        musicPlayedDuration = findViewById(R.id.music_duration_played);
        musicDuration = findViewById(R.id.music_duration);
        playSeekBar = findViewById(R.id.play_seek);
        volumeSeekBar = findViewById(R.id.volume_seek);
        musicArtist = findViewById(R.id.music_artist);
        musicName = findViewById(R.id.music_name);
        musicCover = findViewById(R.id.music_cover);
        playButton = findViewById(R.id.playing_play);
        playModeButton = findViewById(R.id.playing_mode);
        playListView = findViewById(R.id.play_list_view);

        playSeekBar.setFocusable(false);
        volumeSeekBar.setFocusable(false);

        playMode = MusicService.LINE;
        setPlayModeDrawable(playMode);

        audioManager = (AudioManager)getSystemService(Context.AUDIO_SERVICE);
        if(audioManager != null){
            int max = audioManager.getStreamMaxVolume(AudioManager.STREAM_MUSIC);
            audioManager.getStreamVolume(AudioManager.STREAM_MUSIC);
        }

        musicAttributeList = getMusicList();
        playListView.setLayoutManager(new LinearLayoutManager(this));
        playListView.setAdapter(new PlayListAdapter(musicAttributeList, this));
    }

    // 按键按下时的处理，按键按下次数统计，达到阈值则触发长按响应
    @Override
    public boolean onKeyDown(int keyCode, KeyEvent event) {
        Log.d(TAG, "onKeyDown: down keyCount: " + event.getRepeatCount());
        keyCount = event.getRepeatCount();
        if(keyCount > REPEAT_COUNT_THRESHOLD){
            onLongPress(keyCode);
        }
        return super.onKeyDown(keyCode, event);
    }

    // 按键松开时的处理，判断是否需要取消长按处理
    @Override
    public boolean onKeyUp(int keyCode, KeyEvent event) {
        Log.d(TAG, "onKeyUp: up");
        /*if(playListView.isFocused()){
            // 列表获取焦点时的按键响应
            Log.d(TAG, "onKeyUp: playListView is focused");
            if(keyCode == KeyEvent.KEYCODE_DPAD_LEFT){
                playListView.clearFocus();
                playListView.setFocusable(false);
            }
        }
        else */if(keyCount <= REPEAT_COUNT_THRESHOLD){
            onShortPress(keyCode);
            keyCount = 0;
        }else {
            handler.removeMessages(MSG_FORWARD);
            handler.removeMessages(MSG_BACKWARD);
            handler.removeMessages(MSG_LOWER_VOLUME);
            handler.removeMessages(MSG_RAISE_VOLUME);
            if(isPlayingBeforeAdjust){
                Log.d(TAG, "onKeyUp: isPlayingBeforeAdjust true resume");
                musicService.start();
                handler.sendEmptyMessage(MSG_UPDATE_SEEK_BAR);
            }
            isAdjustingPlayer = false;
        }
        return true;
    }

    // 长按处理
    private void onLongPress(int keyCode){
        Log.d(TAG, "onLongPress: long press");
        switch (keyCode){
            case KeyEvent.KEYCODE_DPAD_LEFT:{
                playBackward();
                Log.d(TAG, "onLongPress: left");
                break;
            }
            case KeyEvent.KEYCODE_DPAD_RIGHT:{
                playForward();
                Log.d(TAG, "onLongPress: right");
                break;
            }
            case KeyEvent.KEYCODE_DPAD_UP:{
                if(!isAdjustingVolume){
                    isAdjustingPlayer = true;
                    handler.sendEmptyMessage(MSG_RAISE_VOLUME);
                }
                break;
            }
            case KeyEvent.KEYCODE_DPAD_DOWN:{
                if(!isAdjustingVolume){
                    isAdjustingPlayer = true;
                    handler.sendEmptyMessage(MSG_LOWER_VOLUME);
                }
                break;
            }
            case KeyEvent.KEYCODE_DPAD_CENTER:{
                Log.d(TAG, "onLongPress: center");
                break;
            }
        }
    }

    // 短按处理
    private void onShortPress(int keyCode){
        Log.d(TAG, "onShortPress: short press");
        switch (keyCode){
            case KeyEvent.KEYCODE_DPAD_LEFT:{
                rollbackToPreviousSong();
                Log.d(TAG, "onShortPress: left");
                break;
            }
            case KeyEvent.KEYCODE_DPAD_RIGHT:{
                skipToNextSong();
                Log.d(TAG, "onShortPress: right");
                break;
            }
            case KeyEvent.KEYCODE_DPAD_UP:{
                Log.d(TAG, "onShortPress: up");
                volumeSeekBar.setProgress(volumeSeekBar.getProgress() + VOLUME_ADJUST_UNIT);
                musicService.setVolume(volumeSeekBar.getProgress(), VOLUME_MAX);
                break;
            }
            case KeyEvent.KEYCODE_DPAD_DOWN:{
                Log.d(TAG, "onShortPress: down");
                volumeSeekBar.setProgress(volumeSeekBar.getProgress() - VOLUME_ADJUST_UNIT);
                musicService.setVolume(volumeSeekBar.getProgress(), VOLUME_MAX);
                break;
            }
            case KeyEvent.KEYCODE_DPAD_CENTER:{
                Log.d(TAG, "onShortPress: center");
                if(musicService.isPlaying()){
                    pause();
                }else {
                    start();
                }
                handler.sendEmptyMessage(MSG_UPDATE_SEEK_BAR);
                break;
            }
            case KeyEvent.KEYCODE_TAB:{
                Log.d(TAG, "onLongPress: tab");
                playMode = ++playMode % MusicService.PLAY_MODE_SUM;
                musicService.setPlayMode(playMode);
                setPlayModeDrawable(playMode);
                break;
            }
        }
    }

    // 快进处理
    private void playForward(){
        if(isAdjustingPlayer)
            return;
        isAdjustingPlayer = true;
        isPlayingBeforeAdjust = musicService.isPlaying();
        if (isPlayingBeforeAdjust) {
            musicService.pause();
        }
        handler.sendEmptyMessage(MSG_FORWARD);
    }

    // 快退处理
    private void playBackward(){
        if(isAdjustingPlayer)
            return;
        isAdjustingPlayer = true;
        isPlayingBeforeAdjust = musicService.isPlaying();
        if(isPlayingBeforeAdjust) {
            musicService.pause();
        }
        handler.sendEmptyMessage(MSG_BACKWARD);
    }

    // 获取歌曲列表
    private List<MusicAttribute> getMusicList(){
        Cursor cursor = getContentResolver().query(MediaStore.Audio.Media.EXTERNAL_CONTENT_URI, null, null, null, MediaStore.Audio.Media.DEFAULT_SORT_ORDER);
        List<MusicAttribute> musicAttributes = new ArrayList<>();
        if (cursor != null) {
            while (cursor.moveToNext()){
                MusicAttribute musicAttribute = new MusicAttribute();
                musicAttribute.path = cursor.getString(cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.DATA));
                Log.d(TAG, "getMusicList: music path: " + musicAttribute.path);
                musicAttribute.name = cursor.getString(cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.DISPLAY_NAME)); // 歌曲名
                musicAttribute.album = cursor.getString(cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.ALBUM)); // 专辑
                musicAttribute.artist = cursor.getString(cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.ARTIST)); // 作者
                musicAttribute.size = cursor.getLong(cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.SIZE));// 大小
                musicAttribute.duration = cursor.getInt(cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.DURATION));// 时长
                musicAttribute.musicId = cursor.getInt(cursor.getColumnIndexOrThrow(MediaStore.Audio.Media._ID));// 歌曲的id
//                int albumId = cursor.getInt(cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.ALBUM_ID));
                musicAttributes.add(musicAttribute);
            }
            cursor.close();
        }
        return musicAttributes;
    }

    // 初始播放
    private void initPlay(){
        musicService.initPlay();
        playButton.setImageDrawable(getDrawable(R.drawable.play_rdi_btn_pause));
        handler.sendEmptyMessage(MSG_UPDATE_SEEK_BAR);
        Message message = handler.obtainMessage();
        message.arg1 = 0;
        message.what = MSG_UPDATE_SONG_INFO;
        handler.sendMessage(message);
    }

    // 暂停播放，同时更新UI
    private void pause(){
        musicService.pause();
        playButton.setImageDrawable(getDrawable(R.drawable.play_rdi_btn_play));
    }

    // 开始播放，同时更新UI
    private void start(){
        musicService.start();
        playButton.setImageDrawable(getDrawable(R.drawable.play_rdi_btn_pause));
    }

    // 播放下一首，同时更新UI
    private void skipToNextSong(){
        int index = musicService.skipToNextSong();
        updateSongInfoUI(index);
        playButton.setImageDrawable(getDrawable(R.drawable.play_rdi_btn_pause));
    }

    // 播放上一首，同时更新UI
    private void rollbackToPreviousSong(){
        int index = musicService.rollbackToPreviousSong();
        updateSongInfoUI(index);
        playButton.setImageDrawable(getDrawable(R.drawable.play_rdi_btn_pause));
    }

    // 设置播放模式，同时更新UI
    private void setPlayModeDrawable(int playMode){
        switch (playMode){
            case MusicService.LINE:{
                playModeButton.setImageDrawable(getDrawable(R.drawable.ic_playlist_play_black));
                break;
            }
            case MusicService.RADOM:{
                playModeButton.setImageDrawable(getDrawable(R.drawable.ic_shuffle_black));
                break;
            }
            case MusicService.SINGLE_RECICLE:{
                playModeButton.setImageDrawable(getDrawable(R.drawable.play_icn_one_prs));
                break;
            }
            case MusicService.MENU_RECICLE:{
                playModeButton.setImageDrawable(getDrawable(R.drawable.play_icn_loop_prs));
                break;
            }
        }
    }

    // 更新UI上的播放歌曲信息
    public void updateSongInfoUI(int songIndex){
        handler.removeMessages(MSG_UPDATE_SEEK_BAR);
        playSeekBar.setProgress(0);
        MusicAttribute musicAttribute = musicAttributeList.get(songIndex);
        playSeekBar.setMax(musicAttribute.duration);
        musicPlayedDuration.setText(durationFormat.format(0));
        musicDuration.setText(durationFormat.format(musicAttribute.duration));
        musicCover.setImageDrawable(getDrawable(R.drawable.default_music_cover));
        musicName.setText(musicAttribute.name);
        musicArtist.setText(musicAttribute.artist);
        handler.sendEmptyMessage(MSG_UPDATE_SEEK_BAR);
    }

    @Override
    public void onPlayCompleted(int newPlayIndex) {
        Message message = handler.obtainMessage();
        message.what = MSG_UPDATE_SONG_INFO;
        message.arg1 = newPlayIndex;
        handler.sendMessage(message);
    }

    // 歌曲信息
    public class MusicAttribute{
        String path;
        String name;
        String album;
        String artist;
        long size;
        int duration;
        int musicId;
    }
}
