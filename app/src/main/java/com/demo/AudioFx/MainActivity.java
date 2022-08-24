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
import android.os.Handler;
import android.widget.TextView;

import com.demo.AudioFx.imu.IMUPeriodicityAlgorithm;

public class MainActivity extends Activity
{
	private static final String TAG = "MainActivity";

	private static final float VISUALIZER_HEIGHT_DIP = 200f;

	private MediaPlayer mMediaPlayer;

	BaseVisualizerView mBaseVisualizerView;
	private TextView energyTextView;

	public void onCreate(Bundle savedInstanceState)
	{
		super.onCreate(savedInstanceState);
		setContentView(R.layout.activity_main);

		setVolumeControlStream(AudioManager.STREAM_MUSIC);

		mMediaPlayer = MediaPlayer.create(this, R.raw.z8806c);

		mBaseVisualizerView = findViewById(R.id.visualizerView);
		energyTextView = findViewById(R.id.energyTextView);

		mMediaPlayer.setOnCompletionListener(mp -> {
			// TODO Auto-generated method stub
//				mVisualizer.setEnabled(false);
		});

		mMediaPlayer.start();
		mMediaPlayer.setLooping(true);

		initSensor();

		findViewById(R.id.btn_start).setOnClickListener(v -> {});
		findViewById(R.id.btn_stop).setOnClickListener(v -> {});
		findViewById(R.id.btn_submit).setOnClickListener(v -> {});
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
	private Handler sensorHandler;

	private SensorEventListener listener;
	private void initSensor() {
		sensorManager = (SensorManager)getSystemService(Context.SENSOR_SERVICE);
		Sensor sensor = sensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER);
		listener = new SensorEventListener() {
			@Override
			public void onSensorChanged(SensorEvent event) {
				float accx = event.values[0];
				float accy = event.values[1];
				float accz = event.values[2];
//			Log.e("onSensorChanged", "x:" + accx + " y:" + accy + " z:" + accz);
				String volume = IMUPeriodicityAlgorithm.updateLinearAccData(accx, accy, accz, event.timestamp, mBaseVisualizerView);
				if (volume != null) {
					runOnUiThread(() -> energyTextView.setText(volume));
				}
			}
			@Override
			public void onAccuracyChanged(Sensor sensor, int accuracy) {
			}
		};

		sensorManager.registerListener(listener, sensor, SensorManager.SENSOR_DELAY_FASTEST);
//		try{
//			sensorFOS = new FileOutputStream(sensorData);
//		} catch (FileNotFoundException e){
//			e.printStackTrace();
//		}
	}
}