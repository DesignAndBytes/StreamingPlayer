package com.designandbytes.radyou;


import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.media.AudioManager;
import android.media.MediaPlayer;
import android.os.Binder;
import android.os.IBinder;
import android.support.annotation.Nullable;
import android.support.v4.content.LocalBroadcastManager;
import android.support.v7.app.NotificationCompat;
import android.telephony.PhoneStateListener;
import android.telephony.TelephonyManager;
import android.util.Log;
import android.widget.Toast;

import static android.media.MediaPlayer.MEDIA_ERROR_IO;
import static android.media.MediaPlayer.MEDIA_ERROR_MALFORMED;
import static android.media.MediaPlayer.MEDIA_ERROR_TIMED_OUT;
import static android.media.MediaPlayer.MEDIA_ERROR_UNSUPPORTED;
import static android.support.v4.app.NotificationCompat.PRIORITY_MAX;

import java.io.IOException;

public class PlayerService extends Service implements MediaPlayer.OnPreparedListener,
        MediaPlayer.OnErrorListener, AudioManager.OnAudioFocusChangeListener {

    private final static String TAG = "PlayerService";
    private final IBinder mBinder = new PlayerBinder();

    private MediaPlayer mMediaPlayer;
    private String mDataSource;

    //Handle incoming phone calls
    private boolean ongoingCall = false;


    public PlayerService() {
    }

    @Override
    public void onCreate() {
        super.onCreate();

        // Inicialización de MediaPLayer
        mMediaPlayer = new MediaPlayer();
        mMediaPlayer.setOnPreparedListener(this);
        mMediaPlayer.setOnErrorListener(this);

        try {
            mMediaPlayer.setAudioStreamType(AudioManager.STREAM_MUSIC);
            mDataSource = getResources().getString(R.string.streaming_source_01);
            mMediaPlayer.setDataSource(mDataSource);

        } catch (IllegalStateException | IOException e) {
            e.printStackTrace();
            Log.e(TAG, "Error: PlayerService@onCreate: ", e);
        }

        Log.d(TAG, "onCreate: ");

        // Notificación permanente para controlar la reproducción y detener el servicio
        NotificationManager nm = (NotificationManager) getSystemService(NOTIFICATION_SERVICE);
        int notificationID = 1;

        Bitmap b = BitmapFactory.decodeResource(getResources(), R.drawable.ic_notif_radio);

        NotificationCompat.Builder mBuilder =
                (NotificationCompat.Builder) new NotificationCompat.Builder(this)
                        .setSmallIcon(R.drawable.ic_notif_radio)
                        .setLargeIcon(b)
                        .setContentTitle(getResources().getString(R.string.app_name))
                        .setContentText(getResources().getString(R.string.description))
                        .setOngoing(true) // Set Fixed Notification
                        .setAutoCancel(false)
                        .setPriority(PRIORITY_MAX)
                        .setStyle(new NotificationCompat.MediaStyle());

        Intent resultIntent = new Intent(this, PlayerService.class);

        PendingIntent pendingPLay = PendingIntent.getService(this, 0, resultIntent, PendingIntent.FLAG_UPDATE_CURRENT);
        mBuilder.setContentIntent(pendingPLay);

        // Botón Play para iniciar reproducción
        mBuilder.addAction(R.mipmap.ic_img_play,
                "Play",
                PendingIntent.getService(this, 1, resultIntent.setAction("ACTION_PLAY"), 0)
        );

        // Botón Stop para detener la reproducción
        mBuilder.addAction(R.mipmap.ic_img_stop,
                "Stop",
                PendingIntent.getService(this, 1, resultIntent.setAction("ACTION_STOP"), 0)
        );

        // Botón Exit para detener el servicio y quitar la notificacion permanente
        mBuilder.addAction(R.mipmap.ic_img_close,
                "Exit",
                PendingIntent.getService(this, 1, resultIntent.setAction("ACTION_CLOSE"), 0)
        );

        nm.notify(notificationID, mBuilder.build());

        callStateListener();
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        handleIntent(intent);
        return super.onStartCommand(intent, flags, startId);
    }

    /**
     * Procesa las llamadas al servicio y filtra segun el valor de intentAction.
     * (estas llamadas son realizadas desde los botones en la notificacion permanente
     * pero tambien podrian realizarse desde otra parte como la actividad o desde otro componente)
     *
     * @param intent
     */
    private void handleIntent(Intent intent) {
        if (intent == null || intent.getAction() == null)
            return;

        String action = intent.getAction();

        if (action.equalsIgnoreCase("ACTION_PLAY")) {
            playAudio();
        } else if (action.equalsIgnoreCase("ACTION_STOP")) {
            stopAudio();
        } else if (action.equalsIgnoreCase("ACTION_CLOSE")) {
            stopAudio();

            // Se envia una señal de "cerrar" al receiver local (de la actividad MainActivity)
            Intent i = new Intent("close-event");
            LocalBroadcastManager.getInstance(this).sendBroadcast(i);

            NotificationManager nm = (NotificationManager) getSystemService(NOTIFICATION_SERVICE);
            nm.cancel(1);
            this.stopSelf();
        }
    }

    @Override
    public void onDestroy() {
        super.onDestroy();

        if (mMediaPlayer != null) {
            mMediaPlayer.release();
            mMediaPlayer = null;
        }


        Log.d(TAG, "onDestroy: ");
    }

    @Override
    public IBinder onBind(Intent intent) {
        return mBinder;
    }

    public void playAudio() {
        if (mMediaPlayer != null) {
            if (!mMediaPlayer.isPlaying()) {
                mMediaPlayer.prepareAsync();
            }
        }
    }

    public void pauseAudio() {
        if (mMediaPlayer != null) {
            if (mMediaPlayer.isPlaying()) {
                mMediaPlayer.pause();
            }
        }
    }

    public void stopAudio() {
        if (mMediaPlayer != null) {
            if (mMediaPlayer.isPlaying()) {
                mMediaPlayer.stop();
            }
        }
    }

    public void setStreamingSource(String sourceUrl) {
        mDataSource = sourceUrl;
        mMediaPlayer.stop();
        mMediaPlayer.prepareAsync();
    }

    public final boolean isPlaying() {
        return mMediaPlayer != null && mMediaPlayer.isPlaying();
    }

    @Nullable
    public String getStreamingSource() {
        return mDataSource;
    }

    @Override
    public boolean onError(MediaPlayer mediaPlayer, int what, int extra) {
        String w, e;
        switch (what) {
            case MediaPlayer.MEDIA_ERROR_UNKNOWN:
                w = "MEDIA_ERROR_UNKNOWN";
                break;
            case MediaPlayer.MEDIA_ERROR_SERVER_DIED:
                w = "MEDIA_ERROR_SERVER_DIED";
                break;
            default:
                w = "Código: " + String.valueOf(what);
        }

        switch (extra) {
            case MEDIA_ERROR_IO:
                e = "MEDIA_ERROR_IO";
                break;
            case MEDIA_ERROR_MALFORMED:
                e = "MEDIA_ERROR_MALFORMED";
                break;
            case MEDIA_ERROR_UNSUPPORTED:
                e = "MEDIA_ERROR_UNSUPPORTED";
                break;
            case MEDIA_ERROR_TIMED_OUT:
                e = "MEDIA_ERROR_TIMED_OUT";
                break;
            case -2147483648: // MEDIA_ERROR_SYSTEM(-2147483648) - low - level system error.
                e = "MEDIA_ERROR_SYSTEM(-2147483648) - low - level system error";
                break;
            default:
                e = "Extra: " + String.valueOf(extra);

        }

        Log.d(TAG, "onError: " + w + " " + e);
        Toast.makeText(getApplication().getApplicationContext(), "MediaPlayer@onError:" + w + " " + e, Toast.LENGTH_SHORT).show();
        return true;

    }

    @Override
    public void onPrepared(MediaPlayer mediaPlayer) {
        mediaPlayer.start();
        Log.d(TAG, "onPrepared: ");
    }

    @Override
    public void onAudioFocusChange(int i) {

    }

    //Handle incoming phone calls
    private void callStateListener() {
        // Get the telephony manager
        TelephonyManager telephonyManager = (TelephonyManager) getSystemService(Context.TELEPHONY_SERVICE);
        //Starting listening for PhoneState changes
        PhoneStateListener phoneStateListener = new PhoneStateListener() {
            @Override
            public void onCallStateChanged(int state, String incomingNumber) {
                switch (state) {
                    //if at least one call exists or the phone is ringing
                    //pause the MediaPlayer
                    case TelephonyManager.CALL_STATE_OFFHOOK:
                    case TelephonyManager.CALL_STATE_RINGING:
                        if (mMediaPlayer != null) {
                            // TODO CORREGIR ... pauseMedia();
                            ongoingCall = true;
                        }
                        break;
                    case TelephonyManager.CALL_STATE_IDLE:
                        // Phone idle. Start playing.
                        if (mMediaPlayer != null) {
                            if (ongoingCall) {
                                ongoingCall = false;
                                // TODO CORREGIR ...resumeMedia();
                            }
                        }
                        break;
                }
            }
        };
        // Register the listener with the telephony manager
        // Listen for changes to the device call state.
        telephonyManager.listen(phoneStateListener,
                PhoneStateListener.LISTEN_CALL_STATE);
    }

    public class PlayerBinder extends Binder {

        PlayerService getService() {
            return PlayerService.this;
        }
    }

}
