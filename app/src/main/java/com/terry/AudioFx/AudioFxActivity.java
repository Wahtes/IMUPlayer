package com.terry.AudioFx;

import android.app.Activity;
import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Rect;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;
import android.media.AudioManager;
import android.media.MediaPlayer;
import android.media.MediaPlayer.OnCompletionListener;
import android.media.audiofx.Equalizer;
import android.media.audiofx.Visualizer;
import android.os.Bundle;
import android.os.Handler;
import android.util.Log;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.LinearLayout;
import android.widget.SeekBar;
import android.widget.TextView;
import android.widget.SeekBar.OnSeekBarChangeListener;

import com.terry.AudioFx.imu.IMUPeriodicityAlgorithm;

public class AudioFxActivity extends Activity
{

	@SuppressWarnings("unused")
	private static final String TAG = "AudioFxActivity";

	private static final float VISUALIZER_HEIGHT_DIP = 200f;

	private MediaPlayer mMediaPlayer;
//	private Visualizer mVisualizer;
	private Equalizer mEqualizer; // 均衡器

	private LinearLayout mLayout;
//	VisualizerView mVisualizerView;
	BaseVisualizerView mBaseVisualizerView;
	private TextView mStatusTextView;
	private TextView volumeTextView;

	public void onCreate(Bundle savedInstanceState)
	{
		super.onCreate(savedInstanceState);
		setVolumeControlStream(AudioManager.STREAM_MUSIC);

		mStatusTextView = new TextView(this);
		mLayout = new LinearLayout(this);
		mLayout.setOrientation(LinearLayout.VERTICAL);
		mLayout.addView(mStatusTextView);
		setContentView(mLayout);

		mMediaPlayer = MediaPlayer.create(this, R.raw.z8806c);
//		mMediaPlayer = new MediaPlayer();
		
		setupVisualizerFxAndUi();
		setupEqualizeFxAndUi();

//		mVisualizer.setEnabled(true);
		mMediaPlayer.setOnCompletionListener(new OnCompletionListener()
		{

			@Override
			public void onCompletion(MediaPlayer mp)
			{
				// TODO Auto-generated method stub
//				mVisualizer.setEnabled(false);
			
			}
		});

		mMediaPlayer.start();
		mMediaPlayer.setLooping(true);
		
		mStatusTextView.setText("播放中。。。");

		initSensor();
	}

	/**
	 * 通过mMediaPlayer返回的AudioSessionId创建一个优先级为0均衡器对象 并且通过频谱生成相应的UI和对应的事件
	 */
	private void setupEqualizeFxAndUi()
	{
		mEqualizer = new Equalizer(0, mMediaPlayer.getAudioSessionId());
		mEqualizer.setEnabled(true);// 启用均衡器
	}

	/**
	 * 生成一个VisualizerView对象，使音频频谱的波段能够反映到 VisualizerView上
	 */
	private void setupVisualizerFxAndUi()
	{

		mBaseVisualizerView = new BaseVisualizerView(this);
		
		mBaseVisualizerView.setLayoutParams(new ViewGroup.LayoutParams(
				ViewGroup.LayoutParams.FILL_PARENT,
				(int) (VISUALIZER_HEIGHT_DIP * getResources()
						.getDisplayMetrics().density)));
		mLayout.addView(mBaseVisualizerView);

		volumeTextView = new TextView(this);
		volumeTextView.setText("0");
		mLayout.addView(volumeTextView);

//		mVisualizer = new Visualizer(mMediaPlayer.getAudioSessionId());
////		mVisualizer = new Visualizer(0);
//		// 参数内必须是2的位数
//		mVisualizer.setCaptureSize(Visualizer.getCaptureSizeRange()[1]);

//		// 设置允许波形表示，并且捕获它
//		mBaseVisualizerView.setVisualizer(mVisualizer);
	}

	@Override
	protected void onPause()
	{
		// TODO Auto-generated method stub
		super.onPause();
		if (isFinishing() && mMediaPlayer != null)
		{
//			mVisualizer.release();
			mMediaPlayer.release();
			mEqualizer.release();
			mMediaPlayer = null;
		}
	}

	private SensorManager sensorManager;
	private Handler sensorHandler;


	private void initSensor() {
		sensorManager = (SensorManager)getSystemService(Context.SENSOR_SERVICE);
		Sensor sensor = sensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER);
		sensorManager.registerListener(listener, sensor, SensorManager.SENSOR_DELAY_FASTEST);
//		try{
//			sensorFOS = new FileOutputStream(sensorData);
//		} catch (FileNotFoundException e){
//			e.printStackTrace();
//		}
	}

	private SensorEventListener listener = new SensorEventListener() {
		@Override
		public void onSensorChanged(SensorEvent event) {
			float accx = event.values[0];
			float accy = event.values[1];
			float accz = event.values[2];
//			Log.e("onSensorChanged", "x:" + accx + " y:" + accy + " z:" + accz);
			String volume = IMUPeriodicityAlgorithm.updateLinearAccData(accx, accy, accz, event.timestamp, mBaseVisualizerView);
			if (volume != null) {
				runOnUiThread(() -> volumeTextView.setText(volume));
			}
		}

		@Override
		public void onAccuracyChanged(Sensor sensor, int accuracy) {

		}
	};

}