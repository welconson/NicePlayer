package com.tcl.a2group.niceplayer;

import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;
import android.util.Log;

import java.util.Timer;
import java.util.TimerTask;

public class SplashActivity extends Activity {
    private final static String TAG = "SplashActivity";
    private final static long SPLASH_TIME = 3000;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_splash);
        if(getActionBar() != null){
            getActionBar().hide();
        }
        splash();
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
    }

    private void leapToPlayActivity(){
        Intent intent = new Intent(this, PlayActivity.class);
        Log.d(TAG, "leapToPlayActivity: leaping");
        this.startActivity(intent);
    }
}
