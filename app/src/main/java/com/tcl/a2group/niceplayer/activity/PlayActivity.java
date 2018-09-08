package com.tcl.a2group.niceplayer.activity;

import android.app.Service;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.ServiceConnection;
import android.database.Cursor;
import android.media.AudioManager;
import android.os.Bundle;
import android.os.Environment;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.os.Message;
import android.provider.MediaStore;
import android.support.v4.app.Fragment;
import android.support.v7.app.AppCompatActivity;
import android.util.Log;
import android.view.KeyEvent;
import android.view.View;
import android.widget.ImageView;
import android.widget.RelativeLayout;
import android.widget.SeekBar;
import android.widget.TextView;
import android.widget.Toast;

import com.tcl.a2group.niceplayer.service.MusicService;
import com.tcl.a2group.niceplayer.R;
import com.tcl.a2group.niceplayer.entity.MusicAttribute;
import com.tcl.a2group.niceplayer.fragment.PlayListFragment;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.List;

/**
 * 播放界面Activity
 * 管理播放界面的用户交互，协调后台的播放Service，承载列表Fragment的显示
 */
public class PlayActivity extends AppCompatActivity implements MusicService.OnPlayCompletedListener {
    private final static String TAG = "PlayActivity";
    private final static int REPEAT_COUNT_THRESHOLD = 10;
    // 消息常量，对应Handler处理的消息
    private final static int MSG_FORWARD = 10;
    private final static int MSG_BACKWARD = 11;
    private final static int MSG_UPDATE_SEEK_BAR = 1;
    private final static int MSG_UPDATE_SONG_INFO = 2;
    private final static int MSG_RAISE_VOLUME = 3;
    private final static int MSG_LOWER_VOLUME = 4;

    // 每秒的毫秒数
    private final static int MILLIS_PER_SECOND = 1000;
    // 播放进度条每次调整的单位(/s)
    private final static int PLAY_BAR_ADJUST_UNIT = 1;
    // Handler消息的间隔毫秒数
    private final static int MSG_INTERVAL_MILLIS = 10;
    // 音量最大值
    private final static int VOLUME_MAX = 100;
    // 音量中值
    private final static int VOLUME_MIDDLE = 50;
    // 音量调整单位
    private final static int VOLUME_ADJUST_UNIT = 1;


    public static Context context;
    // private AlbumViewPager viewPager;
    private RelativeLayout lrcViewContainer;
    private TextView musicPlayedDuration, musicDuration, musicName, musicArtist;
    private ImageView musicCover, playButton, playModeButton;
    private SeekBar playSeekBar, volumeSeekBar;
    // 记录快进或快退前的播放状态
    private boolean isPlayingBeforeAdjust = false;
    // 是否正在快进或快退
    private boolean isAdjustingPlayedSeekBar = false;
    // 是否正在长按调整音量
    private boolean isAdjustingVolume = false;
    // 音乐信息列表
    private List<MusicAttribute> musicAttributeList;
    // 音量管理实例，获取系统音量
    private AudioManager audioManager;
    // 记录当前的播放模式
    private int playMode;
    // 当前播放的歌曲index
    private int playingIndex = MusicService.INDEX_END;
    // 播放列表是否已显示
    private boolean isPlayListShowed;
    // 播放列表中焦点选中的index
    private int focusedIndex;
    // 显示播放列表的Fragment
    private PlayListFragment playListFragment;
    // 是否已经按下返回键
    private boolean isBackPressed;
    // 返回键按下的时间
    private long backPressedTime;
    // 按键次数统计，用于在触发onKeyUp回调时，判断是否为长按操作
    private int keyCount;

    // 后台播放服务
    private MusicService musicService;

    private SimpleDateFormat durationFormat = new SimpleDateFormat("mm:ss");

    // 绑定服务的连接类，实现连接建立、断开时的回调
    private ServiceConnection connection = new ServiceConnection() {
        @Override
        public void onServiceConnected(ComponentName name, IBinder service) {
            // 获取后台服务实例，自动播放第一首歌
            musicService = ((MusicService.MyBinder) (service)).getService();
            musicService.setMusicAttributeList(musicAttributeList);
            musicService.setPlayMode(playMode);
            initPlay();
            // 设置后台服务自然播放结束的事件监听接口
            musicService.setOnPlayCompletedListener(PlayActivity.this);
            // 根据当前播放音量设置当前的音量显示
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
        }

        @Override
        public void onServiceDisconnected(ComponentName name) {
            musicService = null;
        }
    };

    // 重写Handler的消息处理方法，主要用于处理长按操作，更新UI
    // 大多数消息发送者本身运行在UI线程
    private Handler handler = new Handler(Looper.getMainLooper()){
        @Override
        public void handleMessage(Message msg) {
            switch (msg.what) {
                // 更新播放进度条的消息处理，每次刷新进度条后，会发送下一次更新的延时消息
                case MSG_UPDATE_SEEK_BAR:{
                    playSeekBar.setProgress(musicService.getCurrentPosition());
                    musicPlayedDuration.setText(durationFormat.format(musicService.getCurrentPosition()));
                    handler.sendEmptyMessageDelayed(MSG_UPDATE_SEEK_BAR, MILLIS_PER_SECOND);
                    break;
                }
                // 更新歌曲信息的消息处理，更换播放歌曲时调用，传入新播放歌曲在列表的index
                case MSG_UPDATE_SONG_INFO:{
                    updateSongInfoUI(msg.arg1);
                    break;
                }
                // 快进消息处理，处理完成同样发送下一次更新的延时消息
                case MSG_FORWARD: {
                    int newPosition = musicService.getCurrentPosition() + MILLIS_PER_SECOND * PLAY_BAR_ADJUST_UNIT;
                    if(newPosition < playSeekBar.getMax()) {
                        musicService.seekTo(newPosition);
                        playSeekBar.setProgress(musicService.getCurrentPosition());
                        musicPlayedDuration.setText(durationFormat.format(musicService.getCurrentPosition()));
                        handler.sendEmptyMessageDelayed(MSG_FORWARD, MSG_INTERVAL_MILLIS);
                    }
                    break;
                }
                // 快退消息处理，同上
                case MSG_BACKWARD:{
                    int newPosition = musicService.getCurrentPosition() - MILLIS_PER_SECOND * PLAY_BAR_ADJUST_UNIT;
                    if (newPosition > 0) {
                        musicService.seekTo(newPosition);
                        playSeekBar.setProgress(musicService.getCurrentPosition());
                        musicPlayedDuration.setText(durationFormat.format(musicService.getCurrentPosition()));
                        handler.sendEmptyMessageDelayed(MSG_BACKWARD, MSG_INTERVAL_MILLIS);
                    }
                    break;
                }
                // 音量加长按消息处理，同上
                case MSG_RAISE_VOLUME:{
                    volumeSeekBar.setProgress(volumeSeekBar.getProgress() + VOLUME_ADJUST_UNIT);
                    musicService.setVolume(volumeSeekBar.getProgress(), VOLUME_MAX);
                    handler.sendEmptyMessageDelayed(MSG_RAISE_VOLUME, MSG_INTERVAL_MILLIS);
                    break;
                }
                // 音量减长按消息处理，同上
                case MSG_LOWER_VOLUME:{
                    volumeSeekBar.setProgress(volumeSeekBar.getProgress() - VOLUME_ADJUST_UNIT);
                    musicService.setVolume(volumeSeekBar.getProgress(), VOLUME_MAX);
                    handler.sendEmptyMessageDelayed(MSG_LOWER_VOLUME, MSG_INTERVAL_MILLIS);
                    break;

                }
            }
        }
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_play);
        bindService(new Intent(this, MusicService.class), connection, Service.BIND_AUTO_CREATE);

        Log.d(TAG, "onCreate: external music dir: " + getExternalFilesDir(Environment.DIRECTORY_MUSIC));

        context = this;
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

        playSeekBar.setFocusable(false);
        volumeSeekBar.setFocusable(false);

        playMode = MusicService.MODE_ORDER;
        setPlayModeDrawable(playMode);

        audioManager = (AudioManager)getSystemService(Context.AUDIO_SERVICE);
        if(audioManager != null){
            int max = audioManager.getStreamMaxVolume(AudioManager.STREAM_MUSIC);
            audioManager.getStreamVolume(AudioManager.STREAM_MUSIC);
        }

        musicAttributeList = getMusicList();

        isPlayListShowed = false;
        isBackPressed = false;
    }

    // 按键按下时的处理，按键按下次数统计，达到阈值则触发长按响应
    @Override
    public boolean onKeyDown(int keyCode, KeyEvent event) {
//        Log.d(TAG, "onKeyDown: down keyCount: " + event.getRepeatCount());
        keyCount = event.getRepeatCount();

        // 焦点在播放列表时，拦截按键事件
        if(isPlayListShowed){
            return super.onKeyDown(keyCode, event);
        }
        if(keyCount > REPEAT_COUNT_THRESHOLD){
            onLongPress(keyCode);
        }
        return super.onKeyDown(keyCode, event);
    }

    // 按键松开时的处理，判断是否需要取消长按处理
    @Override
    public boolean onKeyUp(int keyCode, KeyEvent event) {
//        Log.d(TAG, "onKeyUp: up");

        // 定义退出键时的行为
        if(keyCode == KeyEvent.KEYCODE_BACK){
            if (!isBackPressed) {
                isBackPressed = true;
                Toast.makeText(PlayActivity.this, "再按一次退出", Toast.LENGTH_SHORT).show();
                backPressedTime = System.currentTimeMillis();
                return false;
            }else {
                long secondBackPressedTime = System.currentTimeMillis();
                if(secondBackPressedTime - backPressedTime < 3000){
                    handler.removeMessages(MSG_UPDATE_SEEK_BAR);
                    musicService.prepareForUnbind();
                    unbindService(connection);
                    finish();
                }else {
                    Toast.makeText(PlayActivity.this, "再按一次退出", Toast.LENGTH_SHORT).show();
                    backPressedTime = secondBackPressedTime;
                }
            }
        }

        // 任意按键时间都会取消已经记录的后退按键事件
        isBackPressed = false;

        // 按键事件，焦点进入播放列表
        if(!isPlayListShowed && keyCode == KeyEvent.KEYCODE_A){
            isPlayListShowed = true;
            focusedIndex = playingIndex;
            Log.d(TAG, "onKeyUp: focus enter play list");
            playListFragment = new PlayListFragment();
            getSupportFragmentManager().beginTransaction()
                    .add(R.id.play_list_fragment, playListFragment, "play_list_fragment")
                    .commit();
            return false;
        }

        // 焦点在播放列表时，拦截按键事件
        if(isPlayListShowed){
            switch (keyCode){
                case KeyEvent.KEYCODE_DPAD_UP:{
                    if(focusedIndex != MusicService.INDEX_START){
                        focusedIndex = focusedIndex - 1;
//                        playListManager.setFocusOnIndex(focusedIndex);
                    }
                    Log.d(TAG, "onKeyUp: UP, focus index " + focusedIndex);
                    break;
                }
                case KeyEvent.KEYCODE_DPAD_DOWN:{
                    if(focusedIndex < musicAttributeList.size() - 1){
                        focusedIndex = focusedIndex + 1;
//                        playListManager.setFocusOnIndex(focusedIndex);
                    }
                    Log.d(TAG, "onKeyUp: DOWN, focus index " + focusedIndex);
                    break;
                }
                case KeyEvent.KEYCODE_DPAD_CENTER:{
                    playingIndex = playListFragment.getFocusItemIndex();
                    musicService.moveToSongByIndex(playingIndex);
                    updateSongInfoUI(playingIndex);
                    playButton.setImageDrawable(getDrawable(R.drawable.play_rdi_btn_pause));
                    break;
                }
                case KeyEvent.KEYCODE_A:{
                    isPlayListShowed = false;
                    Fragment fragment = getSupportFragmentManager().findFragmentByTag("play_list_fragment");
                    if (fragment != null) {
                        getSupportFragmentManager()
                                .beginTransaction()
                                .remove(fragment)
                                .commit();
                    }
                    break;
                }
            }
            return false;
        }

        //
        if(keyCount <= REPEAT_COUNT_THRESHOLD){
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
            isAdjustingPlayedSeekBar = false;
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
                    isAdjustingPlayedSeekBar = true;
                    handler.sendEmptyMessage(MSG_RAISE_VOLUME);
                }
                break;
            }
            case KeyEvent.KEYCODE_DPAD_DOWN:{
                if(!isAdjustingVolume){
                    isAdjustingPlayedSeekBar = true;
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
                playMode = ++playMode % MusicService.MODE_COUNT;
                musicService.setPlayMode(playMode);
                setPlayModeDrawable(playMode);
                break;
            }
        }
    }

    // 快进处理
    private void playForward(){
        if(isAdjustingPlayedSeekBar)
            return;
        isAdjustingPlayedSeekBar = true;
        isPlayingBeforeAdjust = musicService.isPlaying();
        if (isPlayingBeforeAdjust) {
            musicService.pause();
        }
        handler.sendEmptyMessage(MSG_FORWARD);
    }

    // 快退处理
    private void playBackward(){
        if(isAdjustingPlayedSeekBar)
            return;
        isAdjustingPlayedSeekBar = true;
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
                musicAttribute.setPath(cursor.getString(cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.DATA)));
                Log.d(TAG, "getMusicList: music path: " + musicAttribute.getPath());
                musicAttribute.setName(cursor.getString(cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.DISPLAY_NAME))); // 歌曲名
                musicAttribute.setAlbum(cursor.getString(cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.ALBUM))); // 专辑
                musicAttribute.setArtist(cursor.getString(cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.ARTIST))); // 作者
                musicAttribute.setSize(cursor.getLong(cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.SIZE)));// 大小
                musicAttribute.setDuration(cursor.getInt(cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.DURATION)));// 时长
                musicAttribute.setMusicId(cursor.getInt(cursor.getColumnIndexOrThrow(MediaStore.Audio.Media._ID)));// 歌曲的id
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
        playingIndex = MusicService.INDEX_START;
        Message message = handler.obtainMessage();
        message.arg1 = playingIndex;
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
        if(playingIndex == MusicService.INDEX_END){
            playingIndex = MusicService.INDEX_START;
            Message message = handler.obtainMessage();
            message.arg1 = playingIndex;
            message.what = MSG_UPDATE_SONG_INFO;
            handler.sendMessage(message);
        }
        else {
            musicService.start();
            playButton.setImageDrawable(getDrawable(R.drawable.play_rdi_btn_pause));
        }
    }

    // 播放下一首，同时更新UI
    private void skipToNextSong(){
        playingIndex = musicService.skipToNextSong();
        updateSongInfoUI(playingIndex);
        playButton.setImageDrawable(getDrawable(R.drawable.play_rdi_btn_pause));
    }

    // 播放上一首，同时更新UI
    private void rollbackToPreviousSong(){
        playingIndex = musicService.rollbackToPreviousSong();
        updateSongInfoUI(playingIndex);
        playButton.setImageDrawable(getDrawable(R.drawable.play_rdi_btn_pause));
    }

    // 设置播放模式，同时更新UI
    private void setPlayModeDrawable(int playMode){
        switch (playMode){
            case MusicService.MODE_ORDER:{
                playModeButton.setImageDrawable(getDrawable(R.drawable.ic_playlist_play_black));
                break;
            }
            case MusicService.MODE_RANDOM:{
                playModeButton.setImageDrawable(getDrawable(R.drawable.ic_shuffle_black));
                break;
            }
            case MusicService.MODE_SINGLE:{
                playModeButton.setImageDrawable(getDrawable(R.drawable.play_icn_one_prs));
                break;
            }
            case MusicService.MODE_LIST_REPEAT:{
                playModeButton.setImageDrawable(getDrawable(R.drawable.play_icn_loop_prs));
                break;
            }
        }
    }

    // 更新UI上的播放歌曲信息
    public void updateSongInfoUI(int songIndex){
        handler.removeMessages(MSG_UPDATE_SEEK_BAR);
        playSeekBar.setProgress(0);
        if (songIndex == MusicService.INDEX_END) {
            handler.removeMessages(MSG_UPDATE_SEEK_BAR);
            playSeekBar.setProgress(0);
            playButton.setImageDrawable(getDrawable(R.drawable.play_rdi_btn_play));
            musicPlayedDuration.setText(durationFormat.format(0));
        }else {
            MusicAttribute musicAttribute = musicAttributeList.get(songIndex);
            playSeekBar.setProgress(0);
            playSeekBar.setMax(musicAttribute.getDuration());
            musicPlayedDuration.setText(durationFormat.format(0));
            musicDuration.setText(durationFormat.format(musicAttribute.getDuration()));
            musicCover.setImageDrawable(getDrawable(R.drawable.default_music_cover));
            musicName.setText(musicAttribute.getName());
            musicArtist.setText(musicAttribute.getArtist());
            handler.sendEmptyMessage(MSG_UPDATE_SEEK_BAR);
        }
    }

    @Override
    public void onPlayCompleted(int newPlayIndex) {
        Message message = handler.obtainMessage();
        message.what = MSG_UPDATE_SONG_INFO;
        message.arg1 = newPlayIndex;
        handler.sendMessage(message);
    }

    @Override
    protected void onResume() {
        Log.d(TAG, "onResume: ");
        if(musicService != null) {
            Message message = handler.obtainMessage();
            message.arg1 = musicService.getPlayingIndex();
            if (message.arg1 != MusicService.INDEX_END) {
                handler.sendEmptyMessage(MSG_UPDATE_SEEK_BAR);
            }
            message.what = MSG_UPDATE_SONG_INFO;
            handler.sendMessage(message);
        }
        super.onResume();
    }

    public List<MusicAttribute> getMusicAttributeList() {
        return musicAttributeList;
    }
}
