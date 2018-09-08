package com.tcl.a2group.niceplayer.service;

import android.app.Service;
import android.content.Intent;
import android.media.MediaPlayer;
import android.os.Binder;
import android.os.IBinder;
import android.util.Log;

import com.tcl.a2group.niceplayer.entity.MusicAttribute;

import java.io.IOException;
import java.util.List;
import java.util.Random;
import java.util.Stack;

public class MusicService extends Service {
    private static final String TAG = "MusicService";

    //          列表索引相关常量
    // 列表首索引
    public static final int INDEX_START = 0;
    // 列表结束标志
    public static final int INDEX_END = -1;
    // 列表索引错误
    public static final int INDEX_ERROR = -2;
    //          模式相关常量
    // 顺序播放
    public static final int MODE_ORDER = 0;
    // 随机播放
    public static final int MODE_RANDOM = 1;
    // 单曲循环
    public static final int MODE_SINGLE = 2;
    // 列表循环
    public static final int MODE_LIST_REPEAT = 3;
    // 模式总数
    public static final int MODE_COUNT = 4;

    // 媒体播放器实例
    public MediaPlayer mediaPlayer;

    // 记录当前播放模式
    public int playMode = MODE_ORDER;
    // 当前播放的歌曲索引
    private int currIndex = -1;
    // 播放列表
    private List<MusicAttribute> musicAttributeList;
    // 自然播放完成监听者
    private OnPlayCompletedListener onPlayCompletedListener;
    // 随机播放历史歌曲索引栈
    private Stack<Integer> playSongIndexTrack = new Stack<>();

    // 构造函数
    public MusicService() {
        mediaPlayer = new MediaPlayer();
        mediaPlayer.setLooping(false);
        mediaPlayer.setOnCompletionListener(new MediaPlayer.OnCompletionListener() {
            @Override
            public void onCompletion(MediaPlayer mp) {
                Log.d(TAG, "onCompletion: music playing completion");
                int index = moveToNextSong();
                if(onPlayCompletedListener != null){
                    onPlayCompletedListener.onPlayCompleted(index);
                }
            }
        });
    }

    //  通过 Binder 来保持 Activity 和 Service 的通信
    public MyBinder binder = new MyBinder();
    public class MyBinder extends Binder {
        public MusicService getService() {
            return MusicService.this;
        }
    }

    // 切换到下一首
    public int skipToNextSong(){
        int index = getNextSongIndex();
        if(index == INDEX_END){
            index = 0;
        }
        playMusic(index);
        return index;
    }

    // 切换到上一首
    public int rollbackToPreviousSong(){
        int index = getPreviousSongIndex();
        playMusic(index);
        return index;
    }

    // 自动播放下一首
    public int moveToNextSong(){
        int index = getNextSongIndex();
        playMusic(index);
        return index;
    }

    public void moveToSongByIndex(int index){
        currIndex = index;
        playMusic(index);
    }

    private int getNextSongIndex(){
        int index = INDEX_ERROR;
        switch (playMode){
            //todo 用户主动切歌
            case MODE_LIST_REPEAT:{
                index = currIndex + 1;
                index = index >= musicAttributeList.size() ? index - musicAttributeList.size() : index;
                break;
            }
            case MODE_SINGLE:{
                index = currIndex;
                break;
            }
            case MODE_ORDER:{
                index = currIndex + 1;
                index = index >= musicAttributeList.size() ? INDEX_END : index;
                break;
            }
            case MODE_RANDOM:{
                Random random = new Random();
                index = random.nextInt(musicAttributeList.size());
                break;
            }
        }
        return index;
    }

    private int getPreviousSongIndex(){
        int index = currIndex - 1;
        index = index < 0 ? musicAttributeList.size() - 1 : index;
        return index;
    }

    public void playMusic(int index){
        if(index < INDEX_END) {
            // 播放index出错
            Log.d(TAG, "playMusic: wrong index");
        }else if(index == INDEX_END){
            // 顺序播放到达列表底部，结束播放
            currIndex = index;
            mediaPlayer.reset();
        }else{
            // 正常播放下一首
            currIndex = index;
            mediaPlayer.reset();
            try {
                Log.d(TAG, "playMusic: song path: " + musicAttributeList.get(index).getPath());
                mediaPlayer.setDataSource(musicAttributeList.get(index).getPath());
                mediaPlayer.prepare();
                mediaPlayer.start();

            } catch (IOException e) {
                e.printStackTrace();
            }
        }
    }

    public void initPlay(){
        if(musicAttributeList == null || musicAttributeList.isEmpty()){
            return;
        }
        playMusic(0);
    }

    public int getCurrentPosition(){
        if(mediaPlayer != null){
            return mediaPlayer.getCurrentPosition();
        }
        else return 0;
    }

    public void pause(){
        if(mediaPlayer != null && mediaPlayer.isPlaying()){
            mediaPlayer.pause();
        }
    }

    public void start(){
        if(mediaPlayer != null && !mediaPlayer.isPlaying()){
            mediaPlayer.start();
        }
    }

    public void seekTo(int position){
        if(mediaPlayer != null){
            mediaPlayer.seekTo(position);
        }
    }

    public boolean isPlaying() {
        return mediaPlayer != null && mediaPlayer.isPlaying();
    }

    public void setMusicAttributeList(List<MusicAttribute> musicAttributeList){
        this.musicAttributeList = musicAttributeList;
    }

    public void setOnPlayCompletedListener(OnPlayCompletedListener onPlayCompletedListener){
        this.onPlayCompletedListener = onPlayCompletedListener;
    }

    public void setVolume(int volume, int maxVolume){
        if(mediaPlayer != null){
            float scaleVolume = ((float)volume / maxVolume);
            mediaPlayer.setVolume(scaleVolume, scaleVolume);
        }
    }
    
    public void setPlayMode(int playMode){
        this.playMode = playMode;
    }

    public int getPlayingIndex(){
        return currIndex;
    }

    public void prepareForUnbind(){
        if (mediaPlayer != null) {
            mediaPlayer.stop();
            mediaPlayer.release();
        }
    }

    @Override
    public IBinder onBind(Intent intent) {
        return binder;
    }

    @Override
    public void onDestroy() {
        Log.d(TAG, "onDestroy: ");
        super.onDestroy();
    }

    /**
     * 自然播放完成回调接口，由PlayActivity实现
     */
    public interface OnPlayCompletedListener{
        /**
         * 
         * @param newPlayIndex 新播放的歌曲在列表中的index
         */
        public void onPlayCompleted(int newPlayIndex);
    }
}