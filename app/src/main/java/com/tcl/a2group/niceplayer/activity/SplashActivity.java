package com.tcl.a2group.niceplayer.activity;

import android.Manifest;
import android.app.Activity;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.media.MediaScannerConnection;
import android.net.Uri;
import android.os.Bundle;
import android.os.Environment;
import android.support.annotation.NonNull;
import android.util.Log;
import android.widget.Toast;

import com.tcl.a2group.niceplayer.R;

import java.io.File;
import java.util.Timer;
import java.util.TimerTask;

public class SplashActivity extends Activity {
    private final static String TAG = "SplashActivity";
    private final static long SPLASH_TIME = 3000;

    private boolean isScanFinished = false;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_splash);
        if(getActionBar() != null){
            getActionBar().hide();
        }
        if(checkSelfPermission(Manifest.permission.READ_EXTERNAL_STORAGE) != PackageManager.PERMISSION_GRANTED){
            requestPermissions(new String[]{Manifest.permission.READ_EXTERNAL_STORAGE}, 0);
            Log.d(TAG, "onCreate: request external read permission");
        }else {
            splash();
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        if(requestCode == 0 && permissions.length > 0){
            if(grantResults[0] == PackageManager.PERMISSION_GRANTED){
                Toast.makeText(this, "read permission granted", Toast.LENGTH_SHORT).show();
                splash();
            }
            else finish();
        }
    }

    private void splash(){
        Timer timer = new Timer();
        TimerTask timerTask = new TimerTask() {
            @Override
            public void run() {
                leapToPlayActivity();
                SplashActivity.this.finish();
            }
        };
        timer.schedule(timerTask, SPLASH_TIME);
        File musicDir = new File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_MUSIC).getPath());
        final File[] toBeScannedMusicFiles = musicDir.listFiles();
        if(toBeScannedMusicFiles != null) {
            String[] toBeScannedMusicPath = new String[toBeScannedMusicFiles.length];
            for(int i = 0; i < toBeScannedMusicFiles.length; i++){
                toBeScannedMusicPath[i] = toBeScannedMusicFiles[i].getAbsolutePath();
            }
            MediaScannerConnection.scanFile(SplashActivity.this,
                    toBeScannedMusicPath, null, new MediaScannerConnection.OnScanCompletedListener() {
                private int scanSum = 0;
                @Override
                public void onScanCompleted(String path, Uri uri) {
                    scanSum++;
                    Log.d(TAG, "onScanCompleted: path: " + path + ", scan summery: " + scanSum);
                    //todo 完成扫描才能跳转，未完成扫描则延长splash时间
                }
            });
        }
    }


    private void leapToPlayActivity(){
        Intent intent = new Intent(this, PlayActivity.class);
        Log.d(TAG, "leapToPlayActivity: leaping");
        this.startActivity(intent);
    }
}
