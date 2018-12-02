package com.vadim.university.androiduniversitylab3.activity;

import android.Manifest;
import android.content.ComponentName;
import android.content.ContentResolver;
import android.content.Context;
import android.content.Intent;
import android.content.ServiceConnection;
import android.content.pm.PackageManager;
import android.database.Cursor;
import android.net.Uri;
import android.os.Bundle;
import android.os.IBinder;
import android.provider.MediaStore;
import android.support.annotation.Nullable;
import android.support.v4.content.LocalBroadcastManager;
import android.support.v7.app.AppCompatActivity;
import android.support.v7.widget.LinearLayoutManager;
import android.support.v7.widget.RecyclerView;
import android.view.View;

import com.vadim.university.androiduniversitylab3.adapter.AudioListAdapter;
import com.vadim.university.androiduniversitylab3.service.MediaPlayerService;
import com.vadim.university.androiduniversitylab3.R;
import com.vadim.university.androiduniversitylab3.util.StorageUtil;
import com.vadim.university.androiduniversitylab3.model.Audio;

import java.util.ArrayList;

public class AudioListActivity extends AppCompatActivity implements AudioListAdapter.OnItemClickListener {
    public final static int MY_PERMISSIONS_REQUEST_READ_EXTERNAL_STORAGE = 2056;
    private IBinder binderMediaService;
    static boolean serviceBound = false;

    private ArrayList<Audio> audioList;
    private StorageUtil storage;
    private AudioListAdapter adapter;
    private RecyclerView mRecyclerView;

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_audio_list);

        storage = new StorageUtil(getApplicationContext());
        if(!serviceBound) {
            loadAudio();
            startBindService();
        } else {
            audioList = storage.loadAudio();
        }

        mRecyclerView = (RecyclerView)findViewById(R.id.audio_recycler_view);
        mRecyclerView.setHasFixedSize(true);

        adapter = new AudioListAdapter(audioList, getApplicationContext());
        mRecyclerView.setAdapter(adapter);
        mRecyclerView.setLayoutManager(new LinearLayoutManager(getApplicationContext()));
        adapter.setClickListener(this);
    }

    @Override
    public void onSaveInstanceState(Bundle savedInstanceState) {
        savedInstanceState.putBoolean("ServiceState", serviceBound);

        super.onSaveInstanceState(savedInstanceState);
    }

    @Override
    public void onRestoreInstanceState(Bundle savedInstanceState) {
        super.onRestoreInstanceState(savedInstanceState);

        serviceBound = savedInstanceState.getBoolean("ServiceState");
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();

        if(isFinishing()) {
            if (serviceBound) {
                unbindService(serviceConnection);
                LocalBroadcastManager.getInstance(this)
                        .sendBroadcast(new Intent(MediaPlayerService.ACTION_STOP));
                serviceBound = false;
            }
        }
    }

    private ServiceConnection serviceConnection = new ServiceConnection() {
        @Override
        public void onServiceConnected(ComponentName name, IBinder service) {
            binderMediaService = service;
            serviceBound = true;
        }

        @Override
        public void onServiceDisconnected(ComponentName name) {
            serviceBound = false;
        }
    };

    private void startBindService() {
        storage.storeAudio(audioList);
        storage.storeAudioIndex(0);

        Intent playerServiceIntent = new Intent(this, MediaPlayerService.class);
        startService(playerServiceIntent);
        bindService(playerServiceIntent, serviceConnection, Context.BIND_AUTO_CREATE);
    }

    private void loadAudio() {
        if(!gainStorageAccessPermission()) {
            return;
        }
        ContentResolver contentResolver = getContentResolver();

        Uri uri = MediaStore.Audio.Media.EXTERNAL_CONTENT_URI;
        String selection = MediaStore.Audio.Media.IS_MUSIC + "!= 0";
        String sortOrder = MediaStore.Audio.Media.TITLE + " ASC";

        Cursor cursor = contentResolver.query(uri, null, selection, null, sortOrder);

        if (cursor != null && cursor.getCount() > 0) {
            audioList = new ArrayList<>();
            while (cursor.moveToNext()) {
                String data = cursor.getString(cursor.getColumnIndex(MediaStore.Audio.Media.DATA));
                String title = cursor.getString(cursor.getColumnIndex(MediaStore.Audio.Media.TITLE));
                String album = cursor.getString(cursor.getColumnIndex(MediaStore.Audio.Media.ALBUM));
                String artist = cursor.getString(cursor.getColumnIndex(MediaStore.Audio.Media.ARTIST));
                Integer intDuration = Integer.parseInt(cursor.getString(cursor.getColumnIndex(MediaStore.Audio.Media.DURATION)));

                // Save to audioList
                audioList.add(new Audio(data, title, album, artist, intDuration));
            }
        }
        cursor.close();
    }

    private boolean gainStorageAccessPermission() {
        if (checkSelfPermission(Manifest.permission.READ_EXTERNAL_STORAGE)
                != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(new String[]{Manifest.permission.READ_EXTERNAL_STORAGE},
                    MY_PERMISSIONS_REQUEST_READ_EXTERNAL_STORAGE);

            return false;
        }
        return true;
    }

    @Override
    public void onClick(View view, int position) {
        Intent intent = new Intent(AudioListActivity.this, AudioControllerActivity.class);
        Bundle bundle = new Bundle();
        bundle.putBinder("binderMediaService", binderMediaService);
        intent.putExtra("bundle", bundle);
        new StorageUtil(getApplicationContext()).storeAudioIndex(position);
        startActivityForResult(intent, 1);
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        adapter.setSelectedItem(storage.loadAudioIndex());
        mRecyclerView.getLayoutManager().scrollToPosition(storage.loadAudioIndex());
    }

    @Override
    public void onBackPressed() {
    }
}
