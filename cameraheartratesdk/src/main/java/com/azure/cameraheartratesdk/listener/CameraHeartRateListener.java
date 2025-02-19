package com.azure.cameraheartratesdk.listener;

public interface CameraHeartRateListener {
    void onHeartRate(int heartRate);
    void onFingerDetected(boolean isDetected);
}
