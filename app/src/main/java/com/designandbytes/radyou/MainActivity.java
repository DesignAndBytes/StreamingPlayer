package com.designandbytes.radyou;

import android.content.BroadcastReceiver;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.ServiceConnection;
import android.media.AudioManager;
import android.os.Bundle;
import android.os.IBinder;
import android.support.annotation.NonNull;
import android.support.design.widget.BottomNavigationView;
import android.support.v4.content.LocalBroadcastManager;
import android.support.v7.app.AppCompatActivity;
import android.support.v7.app.AppCompatDelegate;
import android.util.Log;
import android.view.MenuItem;
import android.view.View;
import android.widget.ImageButton;
import android.widget.SeekBar;
import android.widget.TextView;

import butterknife.BindView;
import butterknife.ButterKnife;
import butterknife.OnClick;

public class MainActivity extends AppCompatActivity implements ServiceConnection{

    private final static String TAG = "MainActivity";
    private final static String KEY_BOOL_SVC_STATE = "ServiceState";

    private PlayerService mPlayerService;
    private boolean mServiceBounded = false;

    private TextView mTextMessage;

    @BindView(R.id.btn_play)
    protected ImageButton mBtnPlay;

    @BindView(R.id.seek_volume)
    protected SeekBar mSeekVolume;

    private BottomNavigationView.OnNavigationItemSelectedListener mOnNavigationItemSelectedListener
            = new BottomNavigationView.OnNavigationItemSelectedListener() {

        @Override
        public boolean onNavigationItemSelected(@NonNull MenuItem item) {
            switch (item.getItemId()) {
                case R.id.navigation_home:
                    mTextMessage.setText(R.string.title);
                    return true;
                case R.id.navigation_dashboard:
                    mTextMessage.setText(R.string.title_dashboard);
                    return true;
                case R.id.navigation_notifications:
                    mTextMessage.setText(R.string.title_notifications);
                    return true;
            }
            return false;
        }

    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        // Requerido para utilizar vector images
        AppCompatDelegate.setCompatVectorFromResourcesEnabled(true);

        mTextMessage = (TextView) findViewById(R.id.message);
        BottomNavigationView navigation = (BottomNavigationView) findViewById(R.id.navigation);
        navigation.setOnNavigationItemSelectedListener(mOnNavigationItemSelectedListener);

        ButterKnife.bind(this);

        setVolumeControlStream(AudioManager.STREAM_MUSIC);
        setupSeekVolume();
    }

    private void setupSeekVolume() {
        final AudioManager am = (AudioManager)getApplicationContext().getSystemService(AUDIO_SERVICE);
        mSeekVolume.setMax(am.getStreamMaxVolume(AudioManager.STREAM_MUSIC));
        mSeekVolume.setProgress(am.getStreamVolume(AudioManager.STREAM_MUSIC));

        mSeekVolume.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override
            public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                am.setStreamVolume(AudioManager.STREAM_MUSIC, progress, AudioManager.FLAG_PLAY_SOUND);
            }

            @Override
            public void onStartTrackingTouch(SeekBar seekBar) {

            }

            @Override
            public void onStopTrackingTouch(SeekBar seekBar) {

            }
        });
    }

    @Override
    public void onSaveInstanceState(Bundle savedInstanceState) {
        savedInstanceState.putBoolean(KEY_BOOL_SVC_STATE, mServiceBounded);
        super.onSaveInstanceState(savedInstanceState);
    }

    @Override
    public void onRestoreInstanceState(Bundle savedInstanceState) {
        super.onRestoreInstanceState(savedInstanceState);
        mServiceBounded = savedInstanceState.getBoolean(KEY_BOOL_SVC_STATE);
    }

    @OnClick(R.id.btn_play)
    void btnPlayClick(@SuppressWarnings("UnusedParameters") View v){

        if(mServiceBounded){

            if(!mPlayerService.isPlaying()){
                mPlayerService.playAudio();
                setButtonImageStop();
            } else {
                mPlayerService.stopAudio();
                setButtonImagePlay();
            }

        }

    }


    @Override
    protected void onStart() {
        super.onStart();
        // Bind to Service
        Intent intent = new Intent(this, PlayerService.class);
        bindService(intent, this, Context.BIND_AUTO_CREATE);

        // Registra un receiver local
        LocalBroadcastManager.getInstance(this).registerReceiver(
                mLocalReceiver, new IntentFilter("close-event"));
    }

    @Override
    protected void onStop() {
        super.onStop();

        // Unbind to Service
        if (mServiceBounded) {
            unbindService(this);
        }

        // Elimina el registro del receiver local
        LocalBroadcastManager.getInstance(this).unregisterReceiver(
                mLocalReceiver);
    }

    @Override
    public void onServiceConnected(ComponentName componentName, IBinder iBinder) {
        mPlayerService = ((PlayerService.PlayerBinder) iBinder).getService();
        mServiceBounded = true;

        if(mPlayerService.isPlaying()) {
            setButtonImageStop();
        } else{
           setButtonImagePlay();
        }

        Log.d(TAG, "onServiceConnected: ");
    }

    private void setButtonImagePlay() {
        mBtnPlay.setImageDrawable(getResources().getDrawable(R.drawable.ic_play_arrow_black_24dp));
    }

    private void setButtonImageStop(){
        mBtnPlay.setImageDrawable(getResources().getDrawable(R.drawable.ic_stop_black_24dp));
    }

    @Override
    public void onServiceDisconnected(ComponentName componentName) {
        mServiceBounded = false;

        setButtonImageStop();
        Log.d(TAG, "onServiceDisconnected: ");
    }

    private final BroadcastReceiver mLocalReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {

            if (intent == null || intent.getAction() == null) return;

            finish();
        }
    };
}
