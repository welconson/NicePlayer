package com.tcl.a2group.niceplayer;

import android.app.Service;
import android.content.Intent;
import android.media.MediaPlayer;
import android.os.Binder;
import android.os.Environment;
import android.os.IBinder;
import android.util.Log;

import java.io.File;
import java.io.FilenameFilter;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Stack;

public class MusicService extends Service {
    private static final String TAG = "MusicService";
    private static final int INDEX_END = -1;
    private static final int INDEX_ERROR = -2;

    public MediaPlayer mediaPlayer;
    public boolean tag = false;

    //顺序播放
    public static final int LINE = 0;
    //随机播放
    public static final int RADOM = 1;
    //单曲循环
    public static final int SINGLE_RECICLE = 2;
    //列表循环
    public static final int MENU_RECICLE = 3;
    public static final int PLAY_MODE_SUM = 4;
    //播放下一首
    public static final int OPER_NEXT = 1;
    //播放上一首
    public static final int OPER_PREVIOUS=-1;
    //保存当前播放模式
    public int playMode = LINE;
    //用于显示播放列表的数据源
    private List<Map<String,Object>> musicList=new ArrayList<>();
    //当前播放的歌曲索引
    private int currIndex=-1;
//    //获取歌曲列表
//    public List<Map<String,Object>> refeshMusicList(String musicUrl){
//        File musicDir=new File(musicUrl);
//        if(musicDir!=null&&musicDir.isDirectory()){
//            File[] musicFile=musicDir.listFiles(new MusicFilter());
//            if(musicFile!=null){
//                musicList=new ArrayList<Map<String,Object>>();
//                for(int i=0;i<musicFile.length;i++){
//                    File currMusic=musicFile[i];
//                    //获取当前目录的名称和绝对路径
//                    String abPath=currMusic.getAbsolutePath();
//                    String musicName=currMusic.getName();
//                    Map<String,Object> currMusicMap=new HashMap<>();
//                    currMusicMap.put("musicName", musicName);
//                    currMusicMap.put("musicAbPath", abPath);
//                    musicList.add(currMusicMap);
//                }
//
//            }else{
//                musicList = new ArrayList<Map<String,Object>>();
//            }
//        }else{
//            musicList = new ArrayList<Map<String,Object>>();
//        }
//        return musicList;
//    }

    private List<PlayActivity.MusicAttribute> musicAttributeList;
    private OnPlayCompletedListener onPlayCompletedListener;
    private Stack<Integer> playSongIndexTrack = new Stack<>();

//    //播放音乐
//    public void playMusic(int musicPo) throws IllegalArgumentException, SecurityException, IllegalStateException, IOException{
//        Map<String,Object> currMusic=musicList.get(musicPo);
//        Log.i("i",(String)currMusic.get("musicAbPath"));
//        String musicUrl=(String)currMusic.get("musicAbPath");
//        mediaPlayer.reset();
//        mediaPlayer.setDataSource(musicUrl);
//        mediaPlayer.prepare();
//        mediaPlayer.start();
//        currIndex=musicPo;
//    }

//    //按照播放模式播放音乐
//    public void playNew(int operCode) throws IllegalArgumentException, SecurityException, IllegalStateException, IOException{
//        File externalMusicDir = getExternalFilesDir(Environment.DIRECTORY_MUSIC);
//        if(externalMusicDir != null)
//            musicList = refeshMusicList(externalMusicDir.getAbsolutePath());
//        else return;
//        for(Map<String,Object> m:musicList){
//            Log.i("i",m.get("musicName").toString());
//        }
//        if(musicList.size()>0){
//            Log.i("i",""+ playMode);
//            switch(playMode){
//                case MENU_RECICLE:
//                    int newIndex=0;
//                    switch(operCode){
//                        case OPER_NEXT:
//                            newIndex=currIndex+1;
//                            if(newIndex>=musicList.size()){
//                                newIndex=0;
//                            }
//                            break;
//                        case OPER_PREVIOUS:
//                            newIndex=currIndex-1;
//                            if(newIndex<0){
//                                newIndex=musicList.size()-1;
//                            }
//                            break;
//                    }
//                    playMusic(newIndex);
//                    break;
//                case SINGLE_RECICLE:
//                    Log.i("88  ",currIndex+"");
//                    playMusic(currIndex);
//                    break;
//                case RADOM:
//                    Random rd=new Random();
//                    int randomIndex=rd.nextInt(musicList.size());
//                    playMusic(randomIndex);
//                    break;
//                case LINE:
//                    newIndex=0;
//                    switch(operCode){
//                        case OPER_NEXT:
//                            newIndex=currIndex+1;
//                            if(newIndex>=musicList.size()){
//                                newIndex=0;
//                            }
//                            break;
//                        case OPER_PREVIOUS:
//                            newIndex=currIndex-1;
//                            if(newIndex<0){
//                                newIndex=musicList.size()-1;
//                            }
//                            break;
//                    }
//                    playMusic(newIndex);
//                    break;
//
//            }
//        }
//    }

    //构造函数
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

/*        try {
            mediaPlayer.setDataSource("/storage/emulated/0/$MuMu共享文件夹/music/music.mp3");
            mediaPlayer.prepare();
            mediaPlayer.setLooping(true);
            currIndex = 0;
        } catch (Exception e) {
            e.printStackTrace();
        }*/
    }

    //  通过 Binder 来保持 Activity 和 Service 的通信
    public MyBinder binder = new MyBinder();
    public class MyBinder extends Binder {
        MusicService getService() {
            return MusicService.this;
        }
    }
//    //播放/暂停
//    public void playOrPause() {
//        if (mediaPlayer.isPlaying()) {
//            mediaPlayer.pause();
//        } else {
//            mediaPlayer.start();
//        }
//    }
//    //停止
//    public void stop() {
//        if (mediaPlayer != null) {
//            mediaPlayer.stop();
//            try {
//                mediaPlayer.reset();
//                mediaPlayer.setDataSource("/data/music.mp3");
//                mediaPlayer.prepare();
//                mediaPlayer.seekTo(0);
//            } catch (Exception e) {
//                e.printStackTrace();
//            }
//        }
//    }

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

    private int getNextSongIndex(){
        int index = INDEX_ERROR;
        switch (playMode){
            //todo 用户主动切歌
            case MENU_RECICLE:{
                index = currIndex + 1;
                index = index >= musicAttributeList.size() ? index - musicAttributeList.size() : index;
                break;
            }
            case SINGLE_RECICLE:{
                index = currIndex;
                break;
            }
            case LINE:{
                index = currIndex + 1;
                index = index >= musicAttributeList.size() ? INDEX_END : index;
                break;
            }
            case RADOM:{
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
            mediaPlayer.reset();
        }else{
            // 正常播放下一首
            currIndex = index;
            mediaPlayer.reset();
            try {
                Log.d(TAG, "playMusic: song path: " + musicAttributeList.get(index).path);
                mediaPlayer.setDataSource(musicAttributeList.get(index).path);
                mediaPlayer.prepare();
                mediaPlayer.start();

            } catch (IOException e) {
                e.printStackTrace();
            }
        }
    }

    public void initPlay(){
//        File externalMusicDir = getExternalFilesDir(Environment.DIRECTORY_MUSIC);
//        if(externalMusicDir != null)
//            musicList = refeshMusicList(externalMusicDir.getAbsolutePath());
//        else return;
//        if(musicList.isEmpty())
//            return;

        if(musicAttributeList == null || musicAttributeList.isEmpty()){
            return;
        }
        playMusic(0);
    }

    public int getDuration(){
        if(mediaPlayer != null){
            return mediaPlayer.getDuration();
        }
        else return 0;
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

    public boolean isPlaying(){
        if(mediaPlayer != null){
            return mediaPlayer.isPlaying();
        }
        return false;
    }

    public void setMusicAttributeList(List<PlayActivity.MusicAttribute> musicAttributeList){
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

    @Override
    public boolean onUnbind(Intent intent) {
        return super.onUnbind(intent);
    }

    @Override
    public IBinder onBind(Intent intent) {
        return binder;
    }

    public interface OnPlayCompletedListener{
        public void onPlayCompleted(int newPlayIndex);
    }
}

//内部类
class MusicFilter implements FilenameFilter {
    public boolean accept(File dir, String name) {
        return (name.endsWith(".mp3") || name.endsWith(".MP3"));//返回当前目录所有以.mp3结尾的文件
    }
}