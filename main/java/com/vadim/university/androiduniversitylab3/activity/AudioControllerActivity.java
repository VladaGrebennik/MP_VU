package com.vadim.university.androiduniversitylab3.activity;

import android.content.Intent;
import android.os.Bundle;
import android.os.Handler;
import android.support.annotation.Nullable;
import android.support.v4.content.LocalBroadcastManager;
import android.support.v7.app.AppCompatActivity;
import android.util.Log;
import android.view.MenuItem;
import android.view.View;
import android.widget.ImageButton;
import android.widget.SeekBar;
import android.widget.TextView;

import com.vadim.university.androiduniversitylab3.service.MediaPlayerService;
import com.vadim.university.androiduniversitylab3.R;
import com.vadim.university.androiduniversitylab3.util.TimeUtils;

public class AudioControllerActivity extends AppCompatActivity
        implements SeekBar.OnSeekBarChangeListener {

    private LocalBroadcastManager localBroadcastManager;

    private ImageButton btnPlay;
    private ImageButton btnNext;
    private ImageButton btnPrevious;
    private ImageButton btnRepeat;
    private ImageButton btnShuffle;

    private SeekBar audioProgressBar;
    private TextView audioTitleLabel;
    private TextView audioCurrentDurationLabel;
    private TextView audioTotalDurationLabel;

    private MediaPlayerService.LocalBinder mediaPlayerServiceBinder;
    private Handler mHandler = new Handler();
    private int currentAudioDuration;
    private boolean isAudioPlaying = true;

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_audio_controller);

        localBroadcastManager = LocalBroadcastManager.getInstance(this);
        mediaPlayerServiceBinder =
                (MediaPlayerService.LocalBinder)getIntent().getExtras()
                        .getBundle("bundle").getBinder("binderMediaService");
        mapViews();

        // Listeners
        audioProgressBar.setOnSeekBarChangeListener(this); // Important

        audioProgressBar.setProgress(0);
        audioProgressBar.setMax(100);

        updateProgressBar();

        mediaPlayerServiceBinder.setListener(new MediaPlayerService.BoundServiceListener() {
            @Override
            public void setAudioTitle(String title) {
                audioTitleLabel.setText(title);
            }

            @Override
            public void setCycleStatus(boolean isCycle) {
                if(isCycle) {
                    btnRepeat.setImageResource(R.drawable.btn_repeat_focused);
                } else {
                    btnRepeat.setImageResource(R.drawable.btn_repeat);
                }
            }

            @Override
            public void setShuffleStatus(boolean isShuffle) {
                if(isShuffle) {
                    btnShuffle.setImageResource(R.drawable.btn_shuffle_focused);
                } else {
                    btnShuffle.setImageResource(R.drawable.btn_shuffle);
                }
            }

            @Override
            public void setCurrentDuration(int duration) {
                currentAudioDuration = duration;
            }

            @Override
            public void setPlayingStatus(boolean isPlaying) {
                if(isPlaying) {
                    btnPlay.setImageResource(R.drawable.btn_pause);
                } else {
                    btnPlay.setImageResource(R.drawable.btn_play);
                }
                isAudioPlaying = isPlaying;
            }
        });

        localBroadcastManager.sendBroadcast(new Intent(MediaPlayerService.ACTION_PLAY));

        registerButtonListeners();
    }

    private void registerButtonListeners() {
        btnPlay.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                if(isAudioPlaying) {
                    localBroadcastManager.sendBroadcast(new Intent(MediaPlayerService.ACTION_PAUSE));
                } else {
                    localBroadcastManager.sendBroadcast(new Intent(MediaPlayerService.ACTION_RESUME));
                }
            }
        });
        btnNext.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                localBroadcastManager.sendBroadcast(new Intent(MediaPlayerService.ACTION_NEXT));
            }
        });
        btnPrevious.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                localBroadcastManager.sendBroadcast(new Intent(MediaPlayerService.ACTION_PREVIOUS));
            }
        });
        btnRepeat.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                localBroadcastManager.sendBroadcast(new Intent(MediaPlayerService.ACTION_CYCLE));
            }
        });
        btnShuffle.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                localBroadcastManager.sendBroadcast(new Intent(MediaPlayerService.ACTION_SHUFFLE));
            }
        });
    }

    public void updateProgressBar() {
        mHandler.postDelayed(mUpdateTimeTask, 100);
    }

    private Runnable mUpdateTimeTask = new Runnable() {
        public void run() {
            int currentDuration = mediaPlayerServiceBinder.getCurrentPosition();

            if(currentDuration > 0) {
                audioTotalDurationLabel.setText("" + TimeUtils.milliSecondsToTimer(currentAudioDuration));
                // Displaying time completed playing
                audioCurrentDurationLabel.setText("" + TimeUtils.milliSecondsToTimer(currentDuration));

                // Updating progress bar
                int progress = TimeUtils.getProgressPercentage(currentDuration, currentAudioDuration);
                //Log.d("Progress", ""+progress);
                audioProgressBar.setProgress(progress);
                Log.d("DEBUG", currentDuration + "     " + currentAudioDuration + "        " + progress);
            }

            // Running this thread after 100 milliseconds
            mHandler.postDelayed(this, 100);
        }
    };

    @Override
    public void onProgressChanged(SeekBar seekBar, int progress, boolean fromTouch) {
    }

    @Override
    public void onStartTrackingTouch(SeekBar seekBar) {
        mHandler.removeCallbacks(mUpdateTimeTask);
    }

    @Override
    public void onStopTrackingTouch(SeekBar seekBar) {
        mHandler.removeCallbacks(mUpdateTimeTask);
        int currentPosition =  TimeUtils.progressToTimer(seekBar.getProgress(), currentAudioDuration);

        mediaPlayerServiceBinder.setSeekPosition(currentPosition);

        updateProgressBar();
    }

    private void mapViews() {
        btnPlay = (ImageButton) findViewById(R.id.btnPlay);
        btnNext = (ImageButton) findViewById(R.id.btnNext);
        btnPrevious = (ImageButton) findViewById(R.id.btnPrevious);
        btnRepeat = (ImageButton) findViewById(R.id.btnRepeat);
        btnShuffle = (ImageButton) findViewById(R.id.btnShuffle);
        audioProgressBar = (SeekBar) findViewById(R.id.songProgressBar);
        audioTitleLabel = (TextView) findViewById(R.id.songTitle);
        audioCurrentDurationLabel = (TextView) findViewById(R.id.songCurrentDurationLabel);
        audioTotalDurationLabel = (TextView) findViewById(R.id.songTotalDurationLabel);
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        switch (item.getItemId()){
            case android.R.id.home:
                super.onBackPressed();
                break;
        }
        return true;
    }
}
