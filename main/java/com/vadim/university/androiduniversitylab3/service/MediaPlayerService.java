package com.vadim.university.androiduniversitylab3.service;

import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.media.AudioManager;
import android.media.MediaPlayer;
import android.media.session.MediaSessionManager;
import android.os.Binder;
import android.os.IBinder;
import android.os.RemoteException;
import android.support.v4.content.LocalBroadcastManager;
import android.support.v4.media.MediaMetadataCompat;
import android.support.v4.media.session.MediaControllerCompat;
import android.support.v4.media.session.MediaSessionCompat;
import android.support.v7.app.NotificationCompat;
import android.telephony.PhoneStateListener;
import android.telephony.TelephonyManager;

import com.vadim.university.androiduniversitylab3.R;
import com.vadim.university.androiduniversitylab3.util.StorageUtil;
import com.vadim.university.androiduniversitylab3.model.Audio;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Random;

public class MediaPlayerService extends Service implements MediaPlayer.OnCompletionListener,
        MediaPlayer.OnPreparedListener, AudioManager.OnAudioFocusChangeListener {

    public static final String ACTION_PLAY = "com.vadim.university.androiduniversitylab3.ACTION_PLAY";
    public static final String ACTION_RESUME = "com.vadim.university.androiduniversitylab3.ACTION_RESUME";
    public static final String ACTION_PAUSE = "com.vadim.university.androiduniversitylab3.ACTION_PAUSE";
    public static final String ACTION_PREVIOUS = "com.vadim.university.androiduniversitylab3.ACTION_PREVIOUS";
    public static final String ACTION_NEXT = "com.vadim.university.androiduniversitylab3.ACTION_NEXT";
    public static final String ACTION_SHUFFLE = "com.vadim.university.androiduniversitylab3.ACTION_SHUFFLE";
    public static final String ACTION_CYCLE = "com.vadim.university.androiduniversitylab3.ACTION_CYCLE";
    public static final String ACTION_STOP = "com.vadim.university.androiduniversitylab3.ACTION_STOP";
    public static final String ACTION_GET_CURRENT_POSITION = "com.vadim.university.androiduniversitylab3.ACTION_GET_CURRENT_POSITION";
    public static final String ACTION_SEEK = "com.vadim.university.androiduniversitylab3.ACTION_SEEK";

    /*
     * Allows interaction with media controllers, volume keys, media buttons, and transport controls.
     * An app creates an instance of MediaSession when it wants to publish media playback information or handle media keys.
     * */
    private MediaSessionManager mediaSessionManager;
    private MediaSessionCompat mediaSession;
    private MediaControllerCompat.TransportControls transportControls;

    private static final int NOTIFICATION_ID = 101;
    private MediaPlayer mediaPlayer;

    private int resumePosition; // milliseconds
    private int resumeIndex;

    private final IBinder iBinder = new LocalBinder();
    private static BoundServiceListener playerServiceListener;

    private AudioManager audioManager;

    private boolean ongoingCall = false;
    private PhoneStateListener phoneStateListener;
    private TelephonyManager telephonyManager;

    private ArrayList<Audio> audioList;
    private Audio activeAudio;
    private int audioIndex = -1;

    private boolean isFirstTime = true;
    private boolean isShuffle = false;
    private boolean isCycle = false;

    public enum PlaybackStatus {
        PLAYING,
        PAUSED
    }

    public interface BoundServiceListener {
        void setAudioTitle(String title);
        void setCycleStatus(boolean isCycle);
        void setShuffleStatus(boolean isShuffle);
        void setPlayingStatus(boolean isPlaying);
        void setCurrentDuration(int duration);
    }

    public class LocalBinder extends Binder {
        public void setListener(BoundServiceListener listener) {
            playerServiceListener = listener;
        }

        public int getCurrentPosition() {
            return mediaPlayer == null ? -1 : mediaPlayer.getCurrentPosition();
        }

        public void setSeekPosition(int position) {
            mediaPlayer.seekTo(position);
        }
    }

    @Override
    public void onCompletion(MediaPlayer mp) {
        if (!isCycle) {
            if (isShuffle) {
                Random rand = new Random();
                audioIndex = rand.nextInt(audioList.size());

            } else {
                if (audioIndex < (audioList.size() - 1)) {
                    audioIndex++;
                } else {
                    audioIndex = 0;
                }
            }
            activeAudio = audioList.get(audioIndex);
        }
        updateServiceListenerState();

        refreshMedia();
    }

    @Override
    public void onPrepared(MediaPlayer mp) {
        playMedia();
    }

    @Override
    public IBinder onBind(Intent intent) {
        return iBinder;
    }

    @Override
    public void onCreate() {
        super.onCreate();
        callStateListener();
        registerBroadcastReceiver();
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        try {
            StorageUtil storage = new StorageUtil(getApplicationContext());
            audioList = storage.loadAudio();
            audioIndex = storage.loadAudioIndex();
            activeAudio = audioList.get(audioIndex);
        } catch (NullPointerException e) {
            stopSelf();
        }

        if (requestAudioFocus() == false) {
            stopSelf();
        }

        if (mediaSessionManager == null) {
            try {
                initMediaSession();
                initMediaPlayer();
            } catch (RemoteException e) {
                e.printStackTrace();
                stopSelf();
            }
            buildNotification(PlaybackStatus.PLAYING);
        }

        handleIncomingActions(intent);
        return super.onStartCommand(intent, flags, startId);
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        if (mediaPlayer != null) {
            stopMedia();
            mediaPlayer.release();
        }
        removeAudioFocus();

        if (phoneStateListener != null) {
            telephonyManager.listen(phoneStateListener, PhoneStateListener.LISTEN_NONE);
        }

        removeNotification();

        LocalBroadcastManager.getInstance(this).unregisterReceiver(localBroadcastReceiver);

        new StorageUtil(getApplicationContext()).clearCachedAudioPlaylist();
    }

    @Override
    public void onAudioFocusChange(int focusState) {
        switch (focusState) {
            case AudioManager.AUDIOFOCUS_GAIN:
                if (mediaPlayer == null) refreshMediaPlayer();
                else if (!mediaPlayer.isPlaying()) mediaPlayer.start();
                mediaPlayer.setVolume(1.0f, 1.0f);
                break;
            case AudioManager.AUDIOFOCUS_LOSS:
                if (mediaPlayer.isPlaying()) mediaPlayer.stop();
                mediaPlayer.release();
                mediaPlayer = null;
                break;
            case AudioManager.AUDIOFOCUS_LOSS_TRANSIENT:
                if (mediaPlayer.isPlaying()) mediaPlayer.pause();
                break;
            case AudioManager.AUDIOFOCUS_LOSS_TRANSIENT_CAN_DUCK:
                if (mediaPlayer.isPlaying()) mediaPlayer.setVolume(0.1f, 0.1f);
                break;
        }
    }


    private boolean requestAudioFocus() {
        audioManager = (AudioManager) getSystemService(Context.AUDIO_SERVICE);
        int result = audioManager.requestAudioFocus(this, AudioManager.STREAM_MUSIC, AudioManager.AUDIOFOCUS_GAIN);
        if (result == AudioManager.AUDIOFOCUS_REQUEST_GRANTED) {
            return true;
        }

        return false;
    }

    private boolean removeAudioFocus() {
        return AudioManager.AUDIOFOCUS_REQUEST_GRANTED ==
                audioManager.abandonAudioFocus(this);
    }

    private void initMediaPlayer() {
        mediaPlayer = new MediaPlayer();

        mediaPlayer.setOnCompletionListener(this);
        mediaPlayer.setOnPreparedListener(this);
    }

    private void refreshMediaPlayer() {
        mediaPlayer.reset();
        mediaPlayer.setAudioStreamType(AudioManager.STREAM_MUSIC);

        try {
            // Set the data source to the mediaFile location
            mediaPlayer.setDataSource(activeAudio.getData());
        } catch (IOException e) {
            e.printStackTrace();
            stopSelf();
        }
        mediaPlayer.prepareAsync();
    }

    private void playMedia() {
        if (!mediaPlayer.isPlaying()) {

            mediaPlayer.start();
        }
        playerServiceListener.setPlayingStatus(mediaPlayer.isPlaying());
    }

    private void playMedia(int position) {
        if(position != audioIndex || isFirstTime) {
            if (position != -1 && position < audioList.size()) {
                activeAudio = audioList.get(position);
                audioIndex = position;
            } else {
                stopSelf();
            }
            refreshMedia();
        }
        playerServiceListener.setPlayingStatus(mediaPlayer.isPlaying());
    }

    private void stopMedia() {

        if (mediaPlayer == null) return;
        if (mediaPlayer.isPlaying()) {
            mediaPlayer.stop();
        }
        playerServiceListener.setPlayingStatus(mediaPlayer.isPlaying());
    }

    private void pauseMedia() {
        if (mediaPlayer.isPlaying()) {
            mediaPlayer.pause();
            resumePosition = mediaPlayer.getCurrentPosition();
            resumeIndex = audioIndex;
        }
        playerServiceListener.setPlayingStatus(mediaPlayer.isPlaying());
    }

    private void resumeMedia() {
        if (!mediaPlayer.isPlaying()) {
            if(resumeIndex != audioIndex) {
                activeAudio = audioList.get(audioIndex);
                refreshMedia();
            } else {
                mediaPlayer.seekTo(resumePosition);
                mediaPlayer.start();
            }
        }
        playerServiceListener.setPlayingStatus(mediaPlayer.isPlaying());
    }

    private void skipToNext() {
        if (!isCycle) {
            if (isShuffle) {
                audioIndex = new Random().nextInt(audioList.size());
            } else if (audioIndex == audioList.size() - 1) {
                audioIndex = 0;
            } else {
                audioIndex++;
            }
        }
        activeAudio = audioList.get(audioIndex);
        if (mediaPlayer.isPlaying()) {
            refreshMedia();
        }
    }

    private void skipToPrevious() {
        if (!isCycle) {
            if (isShuffle) {
                audioIndex = new Random().nextInt(audioList.size());
            } else if (audioIndex == 0) {
                audioIndex = audioList.size() - 1;
            } else {
                audioIndex--;
            }
        }
        activeAudio = audioList.get(audioIndex);
        if (mediaPlayer.isPlaying()) {
            refreshMedia();
        }
    }

    private void refreshMedia() {
        new StorageUtil(getApplicationContext()).storeAudioIndex(audioIndex);
        stopMedia();
        //reset mediaPlayer
        mediaPlayer.reset();
        refreshMediaPlayer();
        updateMetaData();
        buildNotification(PlaybackStatus.PLAYING);
    }

    private void updateServiceListenerState() {
        playerServiceListener.setAudioTitle(activeAudio.getTitle());
        playerServiceListener.setCycleStatus(isCycle);
        playerServiceListener.setShuffleStatus(isShuffle);
        playerServiceListener.setCurrentDuration(activeAudio.getDuration());
    }

    private void callStateListener() {
        telephonyManager = (TelephonyManager) getSystemService(Context.TELEPHONY_SERVICE);
        phoneStateListener = new PhoneStateListener() {
            @Override
            public void onCallStateChanged(int state, String incomingNumber) {
                switch (state) {
                    case TelephonyManager.CALL_STATE_OFFHOOK:
                    case TelephonyManager.CALL_STATE_RINGING:
                        if (mediaPlayer != null) {
                            pauseMedia();
                            ongoingCall = true;
                        }
                        break;
                    case TelephonyManager.CALL_STATE_IDLE:
                        if (mediaPlayer != null) {
                            if (ongoingCall) {
                                ongoingCall = false;
                                resumeMedia();
                            }
                        }
                        break;
                }
            }
        };
        telephonyManager.listen(phoneStateListener,
                PhoneStateListener.LISTEN_CALL_STATE);
    }


    private BroadcastReceiver localBroadcastReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            switch (intent.getAction()) {
                case ACTION_GET_CURRENT_POSITION:
                    break;
                case ACTION_SEEK:
                    break;
                case ACTION_PLAY:
                    playMedia(new StorageUtil(getApplicationContext()).loadAudioIndex());
                    break;
                case ACTION_PREVIOUS:
                    skipToPrevious();
                    break;
                case ACTION_NEXT:
                    skipToNext();
                    break;
                case ACTION_PAUSE:
                    pauseMedia();
                    break;
                case ACTION_SHUFFLE:
                    isShuffle = !isShuffle;
                    break;
                case ACTION_CYCLE:
                    isCycle = !isCycle;
                    break;
                case ACTION_RESUME:
                    resumeMedia();
                    break;
                case ACTION_STOP:
                    stopSelf();
                    break;
            }
            updateServiceListenerState();
            isFirstTime = false;
        }
    };

    private void registerBroadcastReceiver() {
        IntentFilter filter = new IntentFilter();
        filter.addAction(ACTION_PREVIOUS);
        filter.addAction(ACTION_PLAY);
        filter.addAction(ACTION_NEXT);
        filter.addAction(ACTION_PAUSE);
        filter.addAction(ACTION_SHUFFLE);
        filter.addAction(ACTION_CYCLE);
        filter.addAction(ACTION_RESUME);
        filter.addAction(ACTION_STOP);
        filter.addAction(ACTION_GET_CURRENT_POSITION);
        filter.addAction(ACTION_SEEK);
        LocalBroadcastManager.getInstance(this).registerReceiver(localBroadcastReceiver, filter);
    }


    private void initMediaSession() throws RemoteException {
        if (mediaSessionManager != null) return;

        mediaSessionManager = (MediaSessionManager) getSystemService(Context.MEDIA_SESSION_SERVICE);
        mediaSession = new MediaSessionCompat(getApplicationContext(), "AudioPlayer");
        transportControls = mediaSession.getController().getTransportControls();
        mediaSession.setActive(true);
        mediaSession.setFlags(MediaSessionCompat.FLAG_HANDLES_TRANSPORT_CONTROLS);

        updateMetaData();

        mediaSession.setCallback(new MediaSessionCompat.Callback() {
            @Override
            public void onPlay() {
                super.onPlay();
                resumeMedia();
                buildNotification(PlaybackStatus.PLAYING);
                updateServiceListenerState();
            }

            @Override
            public void onPause() {
                super.onPause();
                pauseMedia();
                buildNotification(PlaybackStatus.PAUSED);
                updateServiceListenerState();
            }

            @Override
            public void onSkipToNext() {
                super.onSkipToNext();
                skipToNext();
                updateMetaData();
                buildNotification(PlaybackStatus.PLAYING);
                updateServiceListenerState();
            }

            @Override
            public void onSkipToPrevious() {
                super.onSkipToPrevious();
                skipToPrevious();
                updateMetaData();
                buildNotification(PlaybackStatus.PLAYING);
                updateServiceListenerState();
            }

            @Override
            public void onStop() {
                super.onStop();
                removeNotification();
                //Stop the service
                stopSelf();
                updateServiceListenerState();
            }

            @Override
            public void onSeekTo(long position) {
                super.onSeekTo(position);
            }
        });
    }

    private void updateMetaData() {
        Bitmap albumArt = BitmapFactory.decodeResource(getResources(),
                R.drawable.ic_android);
        mediaSession.setMetadata(new MediaMetadataCompat.Builder()
                .putBitmap(MediaMetadataCompat.METADATA_KEY_ALBUM_ART, albumArt)
                .putString(MediaMetadataCompat.METADATA_KEY_ARTIST, activeAudio.getArtist())
                .putString(MediaMetadataCompat.METADATA_KEY_ALBUM, activeAudio.getAlbum())
                .putString(MediaMetadataCompat.METADATA_KEY_TITLE, activeAudio.getTitle())
                .build());
    }

    private void buildNotification(PlaybackStatus playbackStatus) {
        int notificationAction = android.R.drawable.ic_media_pause;
        PendingIntent play_pauseAction = null;

        if (playbackStatus == PlaybackStatus.PLAYING) {
            notificationAction = android.R.drawable.ic_media_pause;
            play_pauseAction = playbackAction(1);
        } else if (playbackStatus == PlaybackStatus.PAUSED) {
            notificationAction = android.R.drawable.ic_media_play;
            play_pauseAction = playbackAction(0);
        }

        Bitmap largeIcon = BitmapFactory.decodeResource(getResources(),
                R.drawable.ic_android);

        NotificationCompat.Builder notificationBuilder = (NotificationCompat.Builder) new NotificationCompat.Builder(this)
                .setShowWhen(false)
                .setStyle(new NotificationCompat.MediaStyle()
                        .setMediaSession(mediaSession.getSessionToken())
                        .setShowActionsInCompactView(0, 1, 2))
                .setColor(getResources().getColor(R.color.colorPrimary))
                .setLargeIcon(largeIcon)
                .setSmallIcon(android.R.drawable.stat_sys_headset)
                .setContentText(activeAudio.getArtist())
                .setContentTitle(activeAudio.getAlbum())
                .setContentInfo(activeAudio.getTitle())
                .addAction(android.R.drawable.ic_media_previous, "previous", playbackAction(3))
                .addAction(notificationAction, "pause", play_pauseAction)
                .addAction(android.R.drawable.ic_media_next, "next", playbackAction(2));

        ((NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE)).notify(NOTIFICATION_ID, notificationBuilder.build());
    }

    private void removeNotification() {
        NotificationManager notificationManager = (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);
        notificationManager.cancel(NOTIFICATION_ID);
    }

    private PendingIntent playbackAction(int actionNumber) {
        Intent playbackAction = new Intent(this, MediaPlayerService.class);
        switch (actionNumber) {
            case 0:
                playbackAction.setAction(ACTION_RESUME);
                return PendingIntent.getService(this, actionNumber, playbackAction, 0);
            case 1:
                playbackAction.setAction(ACTION_PAUSE);
                return PendingIntent.getService(this, actionNumber, playbackAction, 0);
            case 2:
                playbackAction.setAction(ACTION_NEXT);
                return PendingIntent.getService(this, actionNumber, playbackAction, 0);
            case 3:
                playbackAction.setAction(ACTION_PREVIOUS);
                return PendingIntent.getService(this, actionNumber, playbackAction, 0);
            default:
                break;
        }
        return null;
    }

    private void handleIncomingActions(Intent playbackAction) {
        if (playbackAction == null || playbackAction.getAction() == null) return;

        String actionString = playbackAction.getAction();
        if (actionString.equalsIgnoreCase(ACTION_RESUME)) {
            transportControls.play();
        } else if (actionString.equalsIgnoreCase(ACTION_PAUSE)) {
            transportControls.pause();
        } else if (actionString.equalsIgnoreCase(ACTION_NEXT)) {
            transportControls.skipToNext();
        } else if (actionString.equalsIgnoreCase(ACTION_PREVIOUS)) {
            transportControls.skipToPrevious();
        } else if (actionString.equalsIgnoreCase(ACTION_STOP)) {
            transportControls.stop();
        }
    }
}
