package com.azure.cameraheartratesdk.listener;

public interface CameraHeartRateListener {
    void onHeartRate(int heartRate);
    void onSDNN(int heartRate);
    void onRMSSD(int heartRate);
    void onFingerDetected(boolean isDetected);
}
