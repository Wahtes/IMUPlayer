package com.demo.AudioFx;

import android.app.Activity;
import android.content.Context;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;
import android.media.AudioManager;
import android.media.MediaPlayer;
import android.os.Bundle;
import android.widget.EditText;
import android.widget.TextView;

import com.demo.AudioFx.data.SingleIMUData;
import com.demo.AudioFx.imu.IMUPeriodicityAlgorithm;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;

import java.io.File;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

public class MainActivity extends Activity
{
	private static final String TAG = "MainActivity";

	private static final float VISUALIZER_HEIGHT_DIP = 200f;
	public static String DATA_SAVE_FOLDER;
	private File currentIMUFile;
	private File currentAudioFile;
	private File currentMetaFile;

	Gson gson = new GsonBuilder().disableHtmlEscaping()
			.registerTypeAdapter(Bundle.class, GsonUtils.bundleSerializer)
			.create();

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

	private AudioCollector audioCollector;
	private List<SingleIMUData> recorded_IMU_data = new ArrayList<>();
	private long startTimestamp = 0;
	private long stopTimestamp = 0;

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

		audioCollector = new AudioCollector(mAudioManager);

		mMediaPlayer.setOnCompletionListener(mp -> {
			// TODO Auto-generated method stub
//				mVisualizer.setEnabled(false);
		});

//		mMediaPlayer.start();
//		mMediaPlayer.setLooping(true);

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
			nextFile();
			if (audioCollector.startRecording(currentAudioFile)) {
				// 开始录音
				startTimestamp = System.currentTimeMillis();
				isRecording = true;
				statusTextView.setText("正在录制");
				buttonStart.setEnabled(false);
				buttonStop.setEnabled(true);
				mMediaPlayer.seekTo(0);
				mMediaPlayer.start();
			}
		}
	}

	private void stopRecording() {
		if (isRecording) {
			// 结束录音
			audioCollector.stopRecording();
			stopTimestamp = System.currentTimeMillis();
			isRecording = false;
			mMediaPlayer.pause();

			// 存储数据到文件
			StringBuilder stringBuilder = new StringBuilder();
			for (SingleIMUData data : recorded_IMU_data) {
				stringBuilder.append(String.format("%d\t%d\t%f\t%f\t%f",
						data.getTimestamp(),
						data.getType(),
						data.getValues().get(0),
						data.getValues().get(1),
						data.getValues().get(2)
				));
				stringBuilder.append("\n");
			}
			FileUtils.writeStringToFile(stringBuilder.toString(), currentIMUFile);


			statusTextView.setText("未在录制");
			buttonStart.setEnabled(true);
			buttonStop.setEnabled(false);
		}
	}

	private void initSensor() {
		sensorManager = (SensorManager)getSystemService(Context.SENSOR_SERVICE);
		sensorManager.registerListener(listener, sensorManager.getDefaultSensor(Sensor.TYPE_GYROSCOPE), SensorManager.SENSOR_DELAY_FASTEST);
		sensorManager.registerListener(listener, sensorManager.getDefaultSensor(Sensor.TYPE_LINEAR_ACCELERATION), SensorManager.SENSOR_DELAY_FASTEST);
		sensorManager.registerListener(listener, sensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER), SensorManager.SENSOR_DELAY_FASTEST);
		sensorManager.registerListener(listener, sensorManager.getDefaultSensor(Sensor.TYPE_MAGNETIC_FIELD), SensorManager.SENSOR_DELAY_FASTEST);
	}

	private void saveSingleIMUData(SensorEvent event) {
		SingleIMUData singleIMUData = new SingleIMUData(
				Arrays.asList(event.values[0], event.values[1], event.values[2]),
				event.sensor.getName(),
				event.sensor.getType(),
				event.timestamp
		);
		recorded_IMU_data.add(singleIMUData);
	}

	private void saveMetaData() {
		String tag = ((EditText) findViewById(R.id.tagTextView)).getText().toString();
		Map<String, Object> metaData = new HashMap<>();
		metaData.put("ConfirmVolume", tag);
		metaData.put("SystemVolume", mAudioManager.getStreamVolume(AudioManager.STREAM_MUSIC));
		metaData.put("startTimestamp", startTimestamp);
		metaData.put("stopTimestamp", stopTimestamp);


		String result = gson.toJson(metaData);
		FileUtils.writeStringToFile(result, currentMetaFile, false);
	}

	public void nextFile() {
		String dateTime = new SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(new Date());
		String prefix = DATA_SAVE_FOLDER + dateTime + "_" + id_count;
		currentIMUFile = new File(prefix + "_IMU.txt");
		currentAudioFile = new File(prefix + "_Audio.mp3");
		currentMetaFile = new File(prefix + "_meta.json");
		id_count++;
//		return String.format("IMU_%d_%04d.txt", new Date().getTime(), id_count++);
	}
}