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
import android.os.ParcelUuid;
import android.util.SparseArray;
import android.widget.EditText;
import android.widget.TextView;

import com.demo.AudioFx.imu.IMUPeriodicityAlgorithm;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.reflect.TypeToken;

import java.io.File;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;

public class MainActivity extends Activity
{
	private static final String TAG = "MainActivity";

	private static final float VISUALIZER_HEIGHT_DIP = 200f;
	public static String DATA_SAVE_FOLDER;
	private File currentFile;


	private MediaPlayer mMediaPlayer;

	BaseVisualizerView mBaseVisualizerView;
	private TextView energyTextView;
	private TextView volumeTextView;
	private TextView statusTextView;
	private TextView buttonStart;
	private TextView buttonStop;
	AudioManager mAudioManager;
	boolean isRecording = false;
	private int id_count = 0;

	private SensorManager sensorManager;

	private final SensorEventListener listener = new SensorEventListener() {
		@Override
		public void onSensorChanged(SensorEvent event) {
			if (isRecording) {
				saveSingleIMUData(event);
			}

			if (event.sensor.getType() == Sensor.TYPE_ACCELEROMETER) {
				float accx = event.values[0];
				float accy = event.values[1];
				float accz = event.values[2];
//				Log.e("onSensorChanged", "x:" + accx + " y:" + accy + " z:" + accz);
				String volume = IMUPeriodicityAlgorithm.updateLinearAccData(accx, accy, accz, event.timestamp, mBaseVisualizerView);
				if (volume != null) {
					runOnUiThread(() -> {
						energyTextView.setText(volume);
						volumeTextView.setText(mAudioManager.getStreamVolume(AudioManager.STREAM_MUSIC) + "");
					});
				}
			}
		}
		@Override
		public void onAccuracyChanged(Sensor sensor, int accuracy) {}
	};

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
		statusTextView = findViewById(R.id.textView_status);
		buttonStart = findViewById(R.id.btn_start);
		buttonStop = findViewById(R.id.btn_stop);

		mMediaPlayer.setOnCompletionListener(mp -> {
			// TODO Auto-generated method stub
//				mVisualizer.setEnabled(false);
		});

		mMediaPlayer.start();
		mMediaPlayer.setLooping(true);

		initSensor();

		buttonStart.setOnClickListener(v -> {
			startRecording();
		});
		buttonStop.setOnClickListener(v -> {
			stopRecording();
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

	private void startRecording() {
		if (!isRecording) {
			currentFile = nextFile();
			isRecording = true;
			statusTextView.setText("正在录制");
			buttonStart.setEnabled(false);
			buttonStop.setEnabled(true);
			// TODO 开始录音
		}
	}

	private void stopRecording() {
		if (isRecording) {
			isRecording = false;
			statusTextView.setText("未在录制");
			buttonStart.setEnabled(true);
			buttonStop.setEnabled(false);
			// TODO 结束录音
		}
	}

	private void initSensor() {
		sensorManager = (SensorManager)getSystemService(Context.SENSOR_SERVICE);
//		Sensor sensor = sensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER);
//		sensorManager.registerListener(listener, sensor, SensorManager.SENSOR_DELAY_FASTEST);

		sensorManager.registerListener(listener, sensorManager.getDefaultSensor(Sensor.TYPE_GYROSCOPE), SensorManager.SENSOR_DELAY_FASTEST);
		sensorManager.registerListener(listener, sensorManager.getDefaultSensor(Sensor.TYPE_LINEAR_ACCELERATION), SensorManager.SENSOR_DELAY_FASTEST);
		sensorManager.registerListener(listener, sensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER), SensorManager.SENSOR_DELAY_FASTEST);
		sensorManager.registerListener(listener, sensorManager.getDefaultSensor(Sensor.TYPE_MAGNETIC_FIELD), SensorManager.SENSOR_DELAY_FASTEST);
	}

	private void saveSingleIMUData(SensorEvent event) {
		String record = String.format("%d\t%d\t%f\t%f\t%f",
				event.timestamp,
				event.sensor.getType(),
				event.values[0],
				event.values[1],
				event.values[2]);
		FileUtils.writeStringToFile(record, currentFile, true);
	}

	private void saveMetaData() {
		String tag = ((EditText) findViewById(R.id.tagTextView)).getText().toString();
		Map<String, String> metaData = new HashMap<>();
		metaData.put("ConfirmVolume", tag);
		metaData.put("SystemVolume", mAudioManager.getStreamVolume(AudioManager.STREAM_MUSIC) + "");
		String pathname = currentFile.getAbsolutePath() + ".meta";
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

	public File nextFile() {
		String dateTime = new SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(new Date());
		String filename = "IMU_" + dateTime + "_" + id_count++ + ".txt";
		return new File(DATA_SAVE_FOLDER + filename);
//		return String.format("IMU_%d_%04d.txt", new Date().getTime(), id_count++);
	}
}