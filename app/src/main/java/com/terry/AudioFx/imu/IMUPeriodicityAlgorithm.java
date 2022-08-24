package com.terry.AudioFx.imu;

//import org.greenrobot.eventbus.EventBus;
import android.util.Log;

import com.terry.AudioFx.BaseVisualizerView;

import org.jtransforms.fft.DoubleFFT_1D;

import java.util.Arrays;
import java.util.LinkedList;

//import contextlib.feature.event.GyroscopePeriodicityEndEvent;
//import contextlib.feature.event.GyroscopePeriodicityStartEvent;
//import contextlib.feature.event.LinearAccelerationPeriodicityEndEvent;
//import contextlib.feature.event.LinearAccelerationPeriodicityStartEvent;

public class IMUPeriodicityAlgorithm {

    private static LinkedList<double[]> gyroData = new LinkedList<>();
    private static LinkedList<Long> gyroDataTime = new LinkedList<>();
    private static LinkedList<double[]> linearAccData = new LinkedList<>();
    private static LinkedList<Long> linearAccDataTime = new LinkedList<>();
    private static final double GYRO_THRESHOLD = 5.0;
    private static final double LINEAR_ACC_THRESHOLD = 3.5;
    private static final double GYRO_PERIOD_THRESHOLD = 10.0;
    private static final double LINEAR_ACC_PERIOD_THRESHOLD = 20.0;
    private static int GYRO_DATA_SIZE = 512;
    private static int LINEAR_ACC_DATA_SIZE = 256;
//    private static int LINEAR_ACC_DATA_SIZE = 256;
    private static final int MAX_GYRO_DATA_SIZE = 512;
    private static final int MAX_LINEAR_ACC_DATA_SIZE = 256;
//    private static final int MAX_LINEAR_ACC_DATA_SIZE = 256;
    private static final int MIN_GYRO_DATA_SIZE = 128;
    private static final int MIN_LINEAR_ACC_DATA_SIZE = 128;
    private static int MIN_TIME_INTERVAL = 5000;  // 5s
    private static int GYRO_SAMPLING_PERIOD_MILLISECOND = 50;
    private static int LINEAR_ACC_SAMPLING_PERIOD_MILLISECOND = 50;
    private static long prevGyroUpdateTimestamp = 0;
    private static long prevLinearAccUpdateTimestamp = 0;
    private static long prevGyroTriggerTimestamp = System.currentTimeMillis();
    private static long prevLinearAccTriggerTimestamp = System.currentTimeMillis();
    private static double gyroPeriodSeconds = -1.0;
    private static double linearAccPeriodSeconds = -1.0;

    private static LinkedList<IMUPeriodicityRecord> gyroPeriodicityEventRecords = new LinkedList<>();
    private static LinkedList<IMUPeriodicityRecord> linearAccPeriodicityEventRecords = new LinkedList<>();



    private static synchronized void linearAccCollectionContinuationCheck() {
        long timestamp = System.currentTimeMillis();
        if (!linearAccPeriodicityEventRecords.isEmpty() && timestamp - linearAccPeriodicityEventRecords.getLast().getTimestamp() >= 20000) {
            // currently recording (size >= 2) but haven't received a periodicity event for at least 20 seconds, time to stop recording
            if (linearAccPeriodicityEventRecords.size() >= 3) {
                double[] periods = new double[linearAccPeriodicityEventRecords.size()];
                int index = 0;
                for (IMUPeriodicityRecord record: linearAccPeriodicityEventRecords) {
                    periods[index++] = record.getPeriod();
                }
                Arrays.sort(periods);
                double thirtiethPercentilePeriod = periods[(int) (0.3 * periods.length)];
//                EventBus.getDefault().post(
//                        new LinearAccelerationPeriodicityEndEvent(linearAccPeriodicityEventRecords.getLast().getTimestamp(), thirtiethPercentilePeriod)
//                );
                // flush all previous linear acceleration data
                while (!linearAccPeriodicityEventRecords.isEmpty())
                    linearAccPeriodicityEventRecords.removeFirst();
            }
        }
    }


    private static synchronized void recordLinearAccPeriodicity(double period) {
        long timestamp = System.currentTimeMillis();
        linearAccPeriodicityEventRecords.addLast(
                new IMUPeriodicityRecord(
                        timestamp,
                        period,
                        IMUPeriodicityRecord.LINEAR_ACCELERATION
                )
        );
        if (linearAccPeriodicityEventRecords.size() == 3) {
            // 3 events triggered in a span of 20 seconds, time to start recording
//            EventBus.getDefault().post(
//                    new LinearAccelerationPeriodicityStartEvent(
//                            linearAccPeriodicityEventRecords.get(0).getTimestamp(),
//                            linearAccPeriodicityEventRecords.get(1).getTimestamp(),
//                            linearAccPeriodicityEventRecords.get(2).getTimestamp()
//                    )
//            );
        }
    }



    public static synchronized String updateLinearAccData(double x, double y, double z, long timestamp, BaseVisualizerView baseVisualizerView) {
        // sampling rate: once every 50ms
//        if (prevLinearAccUpdateTimestamp == 0)
//            prevLinearAccUpdateTimestamp = timestamp;
//        if (timestamp - prevLinearAccUpdateTimestamp < LINEAR_ACC_SAMPLING_PERIOD_MILLISECOND * 1e6)
//            return false;

        linearAccCollectionContinuationCheck();

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
//        Log.e("linearAccData", (linearAccData.size() > 50 ? linearAccData.subList(0,50) : linearAccData).toString());
        // calculate periodicity every 10s
//        if (linearAccData.size() == LINEAR_ACC_DATA_SIZE && System.currentTimeMillis() - prevLinearAccTriggerTimestamp >= MIN_TIME_INTERVAL) {
////            Log.d("PeriodicityAlgorithm", "-------------------------------------");
////            Log.d("PeriodicityAlgorithm", "linear acceleration data recording length in milliseconds = " + Double.toString((linearAccDataTime.getLast() - linearAccDataTime.getFirst()) / 1e6));
//            return calcLinearAccPeriod();
//        }
//        if (linearAccData.size() == LINEAR_ACC_DATA_SIZE && System.currentTimeMillis() - prevLinearAccTriggerTimestamp >= 500) {
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
        Log.e("ywt", energy.length + "");
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

    public static synchronized boolean calcLinearAccPeriod() {
        prevLinearAccTriggerTimestamp = System.currentTimeMillis();
        double[] accX = new double[LINEAR_ACC_DATA_SIZE];
        double[] accY = new double[LINEAR_ACC_DATA_SIZE];
        double[] accZ = new double[LINEAR_ACC_DATA_SIZE];
        int index = 0;
        // standardize raw linear acceleration data
        // unlike gyroscope data, linear acceleration does not have a fixed range
        // here we fit the 3 dimensions of raw data into the range [-1, 1], though the data may NOT fill the WHOLE RANGE
        // since we are only interested in periodicity here, this improves the consistency of our algorithm...
        // ... by disregarding the actual amplitude of raw acceleration data
        double[] max = {Double.NEGATIVE_INFINITY, Double.NEGATIVE_INFINITY, Double.NEGATIVE_INFINITY};
        double[] min = {Double.POSITIVE_INFINITY, Double.POSITIVE_INFINITY, Double.POSITIVE_INFINITY};
        for (double[] datapoint: linearAccData) {
            for (int dim = 0; dim < 3; dim++) {
                max[dim] = Math.max(max[dim], datapoint[dim]);
                min[dim] = Math.min(min[dim], datapoint[dim]);
            }
        }
        for (double[] datapoint: linearAccData) {
            accX[index] = datapoint[0] / Math.max(Math.abs(max[0]), Math.abs(min[0]));
            accY[index] = datapoint[1] / Math.max(Math.abs(max[1]), Math.abs(min[1]));
            accZ[index] = datapoint[2] / Math.max(Math.abs(max[2]), Math.abs(min[2]));
            index++;
        }
        DoubleFFT_1D fft1D = new DoubleFFT_1D(LINEAR_ACC_DATA_SIZE);
        fft1D.realForward(accX);
        fft1D.realForward(accY);
        fft1D.realForward(accZ);
        double[] energy = new double[LINEAR_ACC_DATA_SIZE / 2];
        for (int i = 0; i < LINEAR_ACC_DATA_SIZE / 2; i++) {
            energy[i] = accX[i * 2] * accX[i * 2] + accX[i * 2 + 1] * accX[i * 2 + 1] +
                        accY[i * 2] * accY[i * 2] + accY[i * 2 + 1] * accY[i * 2 + 1] +
                        accZ[i * 2] * accZ[i * 2] + accZ[i * 2 + 1] * accZ[i * 2 + 1];
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

        // we assume that the period with maximum energy is no more than 4 times the minimum period
        // and that peaks have a effective width of 5
        double min_period = max_energy_index * LINEAR_ACC_SAMPLING_PERIOD_MILLISECOND / 1e3;
        for (int div = 2; div <= 4; div++) {
            int cur_index = max_energy_index / div;
            if (cur_index <= 400 / LINEAR_ACC_SAMPLING_PERIOD_MILLISECOND)  // these extremely short periods of less than 0.2s are most likely noises
                break;
            if (cur_index >= 3 && cur_index <= LINEAR_ACC_DATA_SIZE / 2 - 3) {  // we don't want energy[0], which is irrelevant to periodicity
                int l = cur_index - 2, r = cur_index + 2;
                for (int i = l; i <= r; i++) {
                    if (energy[i] > energy[cur_index])
                        cur_index = i;
                }
            }
//            Log.d("PeriodicityAlgorithm", "trying period " + Double.toString(cur_index * LINEAR_ACC_SAMPLING_PERIOD_MILLISECOND / 1e3) + "s, ratio = " + Double.toString(energy[max_energy_index] / energy[cur_index]));
            if (energy[max_energy_index] / energy[cur_index] < LINEAR_ACC_PERIOD_THRESHOLD)
                min_period = cur_index * LINEAR_ACC_SAMPLING_PERIOD_MILLISECOND / 1e3;
        }

        linearAccPeriodSeconds = min_period;
//        Log.d("PeriodicityAlgorithm", "Linear Acceleration Period = " + Double.toString(linearAccPeriodSeconds) + "s");
        double periodicityIndex = energySorted[LINEAR_ACC_DATA_SIZE / 2 - 2] / energySorted[LINEAR_ACC_DATA_SIZE / 2 - 1 - (int) (5.0 * (LINEAR_ACC_DATA_SIZE / 256.0))];
//        Log.d("PeriodicityAlgorithm", "Linear Acceleration Periodicity Index = " + Double.toString(periodicityIndex));
        if (periodicityIndex > LINEAR_ACC_THRESHOLD) {
            if (LINEAR_ACC_DATA_SIZE > MIN_LINEAR_ACC_DATA_SIZE)
                LINEAR_ACC_DATA_SIZE /= 2;
            recordLinearAccPeriodicity(min_period);
            return true;
        }
        else {
            if (LINEAR_ACC_DATA_SIZE < MAX_LINEAR_ACC_DATA_SIZE)
                LINEAR_ACC_DATA_SIZE *= 2;
            else
                if (Math.random() < 0.2)
                    LINEAR_ACC_DATA_SIZE /= 2;
            return false;
        }
    }

    public static synchronized void flushLinearAccData() {
        while(!linearAccData.isEmpty())
            linearAccData.removeFirst();
        while(!linearAccDataTime.isEmpty())
            linearAccDataTime.removeFirst();
    }


    public static synchronized double getLinearAccPeriodSeconds() {
        return linearAccPeriodSeconds;
    }




}
