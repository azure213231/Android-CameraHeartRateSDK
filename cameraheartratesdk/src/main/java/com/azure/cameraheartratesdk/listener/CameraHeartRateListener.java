package com.azure.cameraheartratesdk.listener;

public interface CameraHeartRateListener {
    void onHeartRate(int heartRate);
    void onSDNN(int sdnn);
    void onRMSSD(int rmssd);
    void onFingerDetected(boolean isDetected);
    void onEffectiveValueRate(float hrEffectiveValueRate,float hrvEffectiveValueRate);
}
