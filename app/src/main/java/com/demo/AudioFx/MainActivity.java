package com.demo.AudioFx;

import android.app.Activity;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.le.ScanResult;
import android.content.Context;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;
import android.media.AudioManager;
import android.media.MediaPlayer;
import android.os.Bundle;
import android.os.Handler;
import android.os.ParcelUuid;
import android.util.SparseArray;
import android.widget.EditText;
import android.widget.TextView;

import com.demo.AudioFx.imu.IMUPeriodicityAlgorithm;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.reflect.TypeToken;

import java.io.File;
import java.util.Date;
import java.util.HashMap;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collector;

public class MainActivity extends Activity
{
	private static final String TAG = "MainActivity";

	private static final float VISUALIZER_HEIGHT_DIP = 200f;
	public static String DATA_SAVE_FOLDER;
	private String currentFilename = "";


	private MediaPlayer mMediaPlayer;

	BaseVisualizerView mBaseVisualizerView;
	private TextView energyTextView;
	private TextView volumeTextView;
	AudioManager mAudioManager;
	boolean isRecording = false;

	public void onCreate(Bundle savedInstanceState)
	{
		super.onCreate(savedInstanceState);
		setContentView(R.layout.activity_main);

		setVolumeControlStream(AudioManager.STREAM_MUSIC);
		mAudioManager = (AudioManager) getSystemService(Context.AUDIO_SERVICE);

		mMediaPlayer = MediaPlayer.create(this, R.raw.z8806c);

		mBaseVisualizerView = findViewById(R.id.visualizerView);
		energyTextView = findViewById(R.id.energyTextView);
		volumeTextView = findViewById(R.id.volumeTextView);

		mMediaPlayer.setOnCompletionListener(mp -> {
			// TODO Auto-generated method stub
//				mVisualizer.setEnabled(false);
		});

		mMediaPlayer.start();
		mMediaPlayer.setLooping(true);

		initSensor();

		findViewById(R.id.btn_start).setOnClickListener(v -> {
			currentFilename = nextFilename();
			isRecording = true;
			// TODO 开始录音
		});
		findViewById(R.id.btn_stop).setOnClickListener(v -> {
			isRecording = false;
			// TODO 结束录音
		});
		findViewById(R.id.btn_submit).setOnClickListener(v -> {
			// 写入当前音量
			saveMetaData();
		});

		DATA_SAVE_FOLDER = getExternalMediaDirs()[0].getAbsolutePath() + "/Data/IMU/";
	}


	@Override
	protected void onPause()
	{
		// TODO Auto-generated method stub
		super.onPause();
		if (isFinishing() && mMediaPlayer != null) {
			mMediaPlayer.release();
			mMediaPlayer = null;
		}
	}

	private SensorManager sensorManager;

	private SensorEventListener listener = new SensorEventListener() {
		@Override
		public void onSensorChanged(SensorEvent event) {
			float accx = event.values[0];
			float accy = event.values[1];
			float accz = event.values[2];
//			Log.e("onSensorChanged", "x:" + accx + " y:" + accy + " z:" + accz);
			if (isRecording)
				saveSingleIMUData(accx, accy, accz);

			String volume = IMUPeriodicityAlgorithm.updateLinearAccData(accx, accy, accz, event.timestamp, mBaseVisualizerView);
			if (volume != null) {
				runOnUiThread(() -> {
					energyTextView.setText(volume);
					volumeTextView.setText(mAudioManager.getStreamVolume(AudioManager.STREAM_MUSIC) + "");
				});
			}
		}
		@Override
		public void onAccuracyChanged(Sensor sensor, int accuracy) {}
	};

	private void initSensor() {
		sensorManager = (SensorManager)getSystemService(Context.SENSOR_SERVICE);
		Sensor sensor = sensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER);
		sensorManager.registerListener(listener, sensor, SensorManager.SENSOR_DELAY_FASTEST);
	}

	private void saveSingleIMUData(float x, float y, float z) {
		String record = String.format("%f\t%f\t%f\t%d", x, y, z, System.currentTimeMillis());
		String pathname = DATA_SAVE_FOLDER + currentFilename;
		FileUtils.writeStringToFile(record, new File(pathname), true);
	}

	private void saveMetaData() {
		String tag = ((EditText) findViewById(R.id.tagTextView)).getText().toString();
		Map<String, String> metaData = new HashMap<>();
		metaData.put("ConfirmVolume", tag);
		metaData.put("SystemVolume", mAudioManager.getStreamVolume(AudioManager.STREAM_MUSIC) + "");
		String pathname = DATA_SAVE_FOLDER + currentFilename + ".meta";
		Gson gson = new GsonBuilder().disableHtmlEscaping()
				.registerTypeAdapter(Bundle.class, GsonUtils.bundleSerializer)
				.registerTypeAdapter(ScanResult.class, GsonUtils.scanResultSerializer)
				.registerTypeAdapter(new TypeToken<SparseArray<byte[]>>(){}.getType(), new GsonUtils.SparseArraySerializer<byte[]>())
				.registerTypeAdapter(BluetoothDevice.class, GsonUtils.bluetoothDeviceSerializer)
				.registerTypeAdapter(ParcelUuid.class, GsonUtils.parcelUuidSerializer)
				.create();

		String result = gson.toJson(metaData);
		FileUtils.writeStringToFile(result, new File(pathname), false);
	}

	private int id_count = 0;
	public String nextFilename() {
		return String.format("IMU_%d_%04d.txt", new Date().getTime(), id_count++);
	}
}