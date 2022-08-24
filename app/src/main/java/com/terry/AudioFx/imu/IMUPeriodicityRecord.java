package com.terry.AudioFx.imu;

public class IMUPeriodicityRecord {
    private long timestamp;
    private double period;
    private int type;
    public final static int GYRO = 0, LINEAR_ACCELERATION = 1;

    public IMUPeriodicityRecord(long timestamp, double period, int type) {
        if (type != GYRO && type != LINEAR_ACCELERATION)
            throw new IllegalArgumentException();
        this.timestamp = timestamp;
        this.period = period;
        this.type = type;
    }

    public long getTimestamp() {
        return timestamp;
    }

    public double getPeriod() {
        return period;
    }

    public int getType() {
        return type;
    }
}
