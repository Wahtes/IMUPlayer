package com.demo.AudioFx;

import android.content.Context;
import android.media.AudioManager;
import android.media.MediaRecorder;
import android.os.Build;
import android.util.Log;

import androidx.annotation.RequiresApi;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

public class AudioCollector {
    private static final String TAG = "AudioCollector";

    private MediaRecorder mMediaRecorder;
    private final AtomicBoolean isCollecting;
    private final AudioManager audioManager;

//    private final File dummyOutputFile;

    public AudioCollector(AudioManager audioManager) {
        isCollecting = new AtomicBoolean(false);
        this.audioManager = audioManager;

//        dummyOutputFile = new File(context.getExternalMediaDirs()[0].getAbsolutePath() + "/tmp/null");
//        FileUtils.makeDir(dummyOutputFile.getParent());
    }

    public void close() {
        stopRecording();
//        clearDummyOutputFile();
    }

    public boolean startRecording(File file) {
        if (isCollecting.compareAndSet(false, true)) {
            try {
                FileUtils.makeDir(file.getParent());
                mMediaRecorder = startNewMediaRecorder(MediaRecorder.AudioSource.MIC, file.getAbsolutePath());
                return true;
            } catch (Exception e) {
                isCollecting.set(false);
                return false;
            }
        } else {
            return false;
        }
    }

    public void stopRecording() {
        stopMediaRecorder(mMediaRecorder);
        isCollecting.set(false);
    }

//    public String getDummyOutputFilePath() {
//        // from Android 11 (SDK 30) on, cannot use "/dev/null"
//        return dummyOutputFile.getAbsolutePath();
//    }
//
//    public void clearDummyOutputFile() {
//        try {
//            FileUtils.deleteFile(dummyOutputFile, "");
//        } catch (Exception e) {
//            e.printStackTrace();
//        }
//    }

    private MediaRecorder startNewMediaRecorder(int audioSource, String outputFilePath) throws IOException {
        MediaRecorder mediaRecorder = new MediaRecorder();
        // may throw IllegalStateException due to lack of permission
        mediaRecorder.setAudioSource(audioSource);
        mediaRecorder.setAudioChannels(2);
        mediaRecorder.setAudioSamplingRate(44100);
        mediaRecorder.setAudioEncodingBitRate(16 * 44100);
        mediaRecorder.setOutputFormat(MediaRecorder.OutputFormat.MPEG_4);
        mediaRecorder.setAudioEncoder(MediaRecorder.AudioEncoder.AAC);
        mediaRecorder.setOutputFile(outputFilePath);
        mediaRecorder.prepare();
        mediaRecorder.start();
        return mediaRecorder;
    }

    private void stopMediaRecorder(MediaRecorder mediaRecorder) {
        if (mediaRecorder != null) {
            try {
                // may throw IllegalStateException because no valid audio data has been received
                mediaRecorder.stop();
            } catch (Exception e) {
                e.printStackTrace();
            }
            try {
                mediaRecorder.release();
            } catch (Exception e) {
                e.printStackTrace();
            }
        }
    }

}