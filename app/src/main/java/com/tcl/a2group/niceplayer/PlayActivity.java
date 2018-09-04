package com.tcl.a2group.niceplayer;

import android.Manifest;
import android.app.Service;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.ServiceConnection;
import android.content.pm.PackageManager;
import android.database.Cursor;
import android.os.Bundle;
import android.os.Environment;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.os.Message;
import android.provider.MediaStore;
import android.support.annotation.NonNull;
import android.support.v7.app.AppCompatActivity;
import android.support.v7.widget.Toolbar;
import android.util.Log;
import android.view.KeyEvent;
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

    private final static int MILLIS_PER_SECOND = 1000;
    private final static int TIME_ADJUST_UNIT = 10;
    private final static int ADJUST_UNIT_MILLIS = 100;


    private Toolbar toolbar;
//    private AlbumViewPager viewPager;
    private RelativeLayout lrcViewContainer;
    private TextView musicPlayedDuration, musicDuration, musicName, musicArtist;
    private ImageView musicCover;
    private SeekBar playSeekBar, volumeSeekBar;
    private boolean isPlayingBeforeAdjust = false;
    private boolean isAdjusting = false;
    private List<MusicAttribute> musicAttributeList;

    private MusicService musicService;
    private SimpleDateFormat durationFormat = new SimpleDateFormat("mm:ss");
    private ServiceConnection connection = new ServiceConnection() {
        @Override
        public void onServiceConnected(ComponentName name, IBinder service) {
            musicService = ((MusicService.MyBinder) (service)).getService();
            Log.d("musicService", musicService + "");
            musicService.setMusicAttributeList(musicAttributeList);
            musicService.initPlay();
            handler.sendEmptyMessage(MSG_UPDATE_SEEK_BAR);
            Message message = handler.obtainMessage();
            message.arg1 = 0;
            message.what = MSG_UPDATE_SONG_INFO;
            handler.sendMessage(message);
            musicService.setOnPlayCompletedListener(PlayActivity.this);
        }

        @Override
        public void onServiceDisconnected(ComponentName name) {
            musicService = null;
        }
    };
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
                    musicService.seekTo(musicService.getCurrentPosition() + MILLIS_PER_SECOND * TIME_ADJUST_UNIT);
                    musicService.pause();
                    playSeekBar.setProgress(musicService.getCurrentPosition());
                    musicPlayedDuration.setText(durationFormat.format(musicService.getCurrentPosition()));
                    handler.sendEmptyMessageDelayed(MSG_FORWARD, ADJUST_UNIT_MILLIS);
                    break;
                }
                case MSG_BACKWARD:{
                    musicService.seekTo(musicService.getCurrentPosition() - MILLIS_PER_SECOND * TIME_ADJUST_UNIT);
                    musicService.pause();
                    playSeekBar.setProgress(musicService.getCurrentPosition());
                    musicPlayedDuration.setText(durationFormat.format(musicService.getCurrentPosition()));
                    handler.sendEmptyMessageDelayed(MSG_BACKWARD, ADJUST_UNIT_MILLIS);
                    break;
                }
            }
        }
    };

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

        playSeekBar.setFocusable(false);
        volumeSeekBar.setFocusable(false);

        //todo 主动请求系统扫描新的音频文件
        if(checkSelfPermission(Manifest.permission.READ_EXTERNAL_STORAGE) != PackageManager.PERMISSION_GRANTED){
            requestPermissions(new String[]{Manifest.permission.READ_EXTERNAL_STORAGE}, 0);
            Log.d(TAG, "onCreate: request external read permission");
        }else {
            musicAttributeList = getMusicList();
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        if(requestCode == 0 && permissions.length > 0){
            if(grantResults[0] == PackageManager.PERMISSION_GRANTED){
                Toast.makeText(this, "read permission granted", Toast.LENGTH_SHORT).show();
                musicAttributeList = getMusicList();
                //todo 请求权限后续处理，传递音乐列表给播放服务
            }
        }
    }

    @Override
    public boolean onKeyDown(int keyCode, KeyEvent event) {
        Log.d(TAG, "onKeyDown: down keyCount: " + event.getRepeatCount());
        keyCount = event.getRepeatCount();
        if(keyCount > REPEAT_COUNT_THRESHOLD){
            onLongPress(keyCode);
        }
        return super.onKeyDown(keyCode, event);
    }

    @Override
    public boolean onKeyUp(int keyCode, KeyEvent event) {
        Log.d(TAG, "onKeyUp: up");
        if(keyCount <= REPEAT_COUNT_THRESHOLD){
            onShortPress(keyCode);
            keyCount = 0;
        }else {
            handler.removeMessages(MSG_FORWARD);
            handler.removeMessages(MSG_BACKWARD);
            if(isPlayingBeforeAdjust){
                Log.d(TAG, "onKeyUp: isPlayingBeforeAdjust true resume");
                musicService.start();
                handler.sendEmptyMessage(MSG_UPDATE_SEEK_BAR);
            }
            isAdjusting = false;
        }
        return true;
    }

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
                Log.d(TAG, "onLongPress: =====> right long press");
                Log.d(TAG, "onLongPress: right");
                break;
            }
            case KeyEvent.KEYCODE_DPAD_CENTER:{
                Log.d(TAG, "onLongPress: center");
                break;
            }
        }
    }

    private void onShortPress(int keyCode){
        Log.d(TAG, "onShortPress: short press");
        switch (keyCode){
            case KeyEvent.KEYCODE_DPAD_LEFT:{
                int index = musicService.skipToNextSong();
                updateSongInfoUI(index);
                Log.d(TAG, "onShortPress: left");
                break;
            }
            case KeyEvent.KEYCODE_DPAD_RIGHT:{
                int index = musicService.rollbackToPreviousSong();
                updateSongInfoUI(index);
                Log.d(TAG, "onShortPress: right");
                break;
            }
            case KeyEvent.KEYCODE_DPAD_UP:{
                Log.d(TAG, "onShortPress: up");
                break;
            }
            case KeyEvent.KEYCODE_DPAD_DOWN:{
                Log.d(TAG, "onShortPress: down");
                break;
            }
            case KeyEvent.KEYCODE_DPAD_CENTER:{
                Log.d(TAG, "onShortPress: center");
                if(musicService.isPlaying()){
                    musicService.pause();
                }else {
                    musicService.start();
                }
                handler.sendEmptyMessage(MSG_UPDATE_SEEK_BAR);
                break;
            }
        }
    }

    private void playForward(){
        if(isAdjusting)
            return;
        isAdjusting = true;
        isPlayingBeforeAdjust = musicService.isPlaying();
        if (isPlayingBeforeAdjust) {
            Log.d(TAG, "playForward: =====> paused");
            musicService.pause();
        }
        handler.sendEmptyMessage(MSG_FORWARD);
    }

    private void playBackward(){
        if(isAdjusting)
            return;
        isAdjusting = true;
        isPlayingBeforeAdjust = musicService.isPlaying();
        if(isPlayingBeforeAdjust) {
            musicService.pause();
        }
        handler.sendEmptyMessage(MSG_BACKWARD);
    }

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

    public void updateSongInfoUI(int songIndex){
        playSeekBar.setProgress(0);
        MusicAttribute musicAttribute = musicAttributeList.get(songIndex);
        playSeekBar.setMax(musicAttribute.duration);
        musicPlayedDuration.setText(durationFormat.format(0));
        musicDuration.setText(durationFormat.format(musicAttribute.duration));
        musicCover.setImageDrawable(getDrawable(R.drawable.default_music_cover));
        musicName.setText(musicAttribute.name);
        musicArtist.setText(musicAttribute.artist);
    }

    @Override
    public void onPlayCompleted(int newPlayIndex) {
        Message message = handler.obtainMessage();
        message.what = MSG_UPDATE_SONG_INFO;
        message.arg1 = newPlayIndex;
        handler.sendMessage(message);
    }

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
