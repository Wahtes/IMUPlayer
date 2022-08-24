package com.demo.AudioFx.imu;

import android.util.Log;

import com.demo.AudioFx.BaseVisualizerView;

import org.jtransforms.fft.DoubleFFT_1D;

import java.util.Arrays;
import java.util.LinkedList;


public class IMUPeriodicityAlgorithm {
    private static LinkedList<double[]> linearAccData = new LinkedList<>();
    private static LinkedList<Long> linearAccDataTime = new LinkedList<>();
    private static final double LINEAR_ACC_THRESHOLD = 3.5;
    private static final double LINEAR_ACC_PERIOD_THRESHOLD = 20.0;
    private static int GYRO_DATA_SIZE = 512;
    private static int LINEAR_ACC_DATA_SIZE = 256;
//    private static int LINEAR_ACC_DATA_SIZE = 256;
    private static final int MAX_LINEAR_ACC_DATA_SIZE = 256;
//    private static final int MAX_LINEAR_ACC_DATA_SIZE = 256;
    private static final int MIN_LINEAR_ACC_DATA_SIZE = 128;
    private static int MIN_TIME_INTERVAL = 5000;  // 5s
    private static int LINEAR_ACC_SAMPLING_PERIOD_MILLISECOND = 50;
    private static long prevLinearAccUpdateTimestamp = 0;
    private static long prevGyroTriggerTimestamp = System.currentTimeMillis();
    private static long prevLinearAccTriggerTimestamp = System.currentTimeMillis();

    public static synchronized String updateLinearAccData(double x, double y, double z, long timestamp, BaseVisualizerView baseVisualizerView) {

//        linearAccCollectionContinuationCheck();   // 检查是否还有继续记录的必要

        prevLinearAccUpdateTimestamp = timestamp;
        // update sliding window
        while (linearAccData.size() >= LINEAR_ACC_DATA_SIZE) {
            linearAccData.removeFirst();
            linearAccDataTime.removeFirst();
        }
        double data[] = new double[3];
        data[0] = x;
        data[1] = y;
        data[2] = z;
        linearAccData.addLast(data);
        linearAccDataTime.addLast(timestamp);

        if (linearAccData.size() == LINEAR_ACC_DATA_SIZE) {
            return analyseFreq(baseVisualizerView);
        }
        return null;
    }

    public static synchronized String analyseFreq(BaseVisualizerView baseVisualizerView) {

        double[] accX = new double[LINEAR_ACC_DATA_SIZE];
        double[] accY = new double[LINEAR_ACC_DATA_SIZE];
        double[] accZ = new double[LINEAR_ACC_DATA_SIZE];
        int index = 0;
        // standardize raw linear acceleration data
        // unlike gyroscope data, linear acceleration does not have a fixed range
        // here we fit the 3 dimensions of raw data into the range [-1, 1], though the data may NOT fill the WHOLE RANGE
        // since we are only interested in periodicity here, this improves the consistency of our algorithm...
        // ... by disregarding the actual amplitude of raw acceleration data
//        double[] max = {Double.NEGATIVE_INFINITY, Double.NEGATIVE_INFINITY, Double.NEGATIVE_INFINITY};
//        double[] min = {Double.POSITIVE_INFINITY, Double.POSITIVE_INFINITY, Double.POSITIVE_INFINITY};
//        for (double[] datapoint: linearAccData) {
//            for (int dim = 0; dim < 3; dim++) {
//                max[dim] = Math.max(max[dim], datapoint[dim]);
//                min[dim] = Math.min(min[dim], datapoint[dim]);
//            }
//        }
//        for (double[] datapoint: linearAccData) {
//            accX[index] = datapoint[0] / Math.max(Math.abs(max[0]), Math.abs(min[0]));
//            accY[index] = datapoint[1] / Math.max(Math.abs(max[1]), Math.abs(min[1]));
//            accZ[index] = datapoint[2] / Math.max(Math.abs(max[2]), Math.abs(min[2]));
//            index++;
//        }
        for (double[] datapoint: linearAccData) {
            accX[index] = datapoint[0];
            accY[index] = datapoint[1];
            accZ[index] = datapoint[2];
            index++;
        }
        DoubleFFT_1D fft1D = new DoubleFFT_1D(LINEAR_ACC_DATA_SIZE);
        fft1D.realForward(accX);
        fft1D.realForward(accY);
        fft1D.realForward(accZ);
        double[] energy = new double[LINEAR_ACC_DATA_SIZE / 2];
        for (int i = 0; i < LINEAR_ACC_DATA_SIZE / 2; i++) {
            energy[i] = Math.sqrt(accX[i * 2] * accX[i * 2] + accX[i * 2 + 1] * accX[i * 2 + 1] +
                    accY[i * 2] * accY[i * 2] + accY[i * 2 + 1] * accY[i * 2 + 1] +
                    accZ[i * 2] * accZ[i * 2] + accZ[i * 2 + 1] * accZ[i * 2 + 1]);
        }

        // here we discards energy[0], which is NOT related to periodicity
        // it is associated with the MEAN VALUE of raw data
        double[] energySorted = new double[LINEAR_ACC_DATA_SIZE / 2 - 1];
        for (int i = 9; i < LINEAR_ACC_DATA_SIZE / 2; i++)
            energySorted[i - 9] = energy[i];
        Arrays.sort(energySorted);

        int max_energy_index = 0;
        for (int i = 0; i < LINEAR_ACC_DATA_SIZE / 2; i++)
            if (energy[i] == energySorted[LINEAR_ACC_DATA_SIZE / 2 - 2]) {
                max_energy_index = i;
//                linearAccPeriodSeconds = i * LINEAR_ACC_SAMPLING_PERIOD_MILLISECOND / 1e3;
//                Log.d("PeriodicityAlgorithm", "Linear Acceleration Period = " + Double.toString(linearAccPeriodSeconds) + "s");
                break;
            }
        Log.e("energy.length", energy.length + "");
        int[] fft = new int[128];
        for (int i = 0 ; i < 128; i++)
            fft[i] = (int) (energy[i] * 20);
        baseVisualizerView.updateGraph(fft);

        if (System.currentTimeMillis() - prevLinearAccTriggerTimestamp >= 500) {
            prevLinearAccTriggerTimestamp = System.currentTimeMillis();
            String arrayClip = "";
            for(int i = 0; i < 128; i++) {
                arrayClip += energy[i] + ", ";
            }
            Log.e("array0", arrayClip);
            arrayClip = "";
            for(int i = 0; i < 128; i++) {
                arrayClip += fft[i] + ", ";
            }
            Log.e("array1", arrayClip);
            int WINDOW_SIZE = 50;
            double[] highComponent = Arrays.copyOfRange(energy, energy.length - WINDOW_SIZE, energy.length);
            double predictedVolume = 0;


            for (int i = 0; i < WINDOW_SIZE; i++) {
                predictedVolume += highComponent[i];
            }
            return "" + predictedVolume;
        }
        return null;
    }

    public static synchronized void flushLinearAccData() {
        while(!linearAccData.isEmpty())
            linearAccData.removeFirst();
        while(!linearAccDataTime.isEmpty())
            linearAccDataTime.removeFirst();
    }
}
