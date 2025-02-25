package com.azure.cameraheartratesdk.listener;

public interface CameraHeartRateListener {
    /**
     * onHeartRate回调返回值为心率，单位为 次数/分钟
     */
    void onHeartRate(int heartRate);
    /**
     * onSDNN回调返回值为sdnn
    */
    void onSDNN(int sdnn);
    /**
     * onRMSSD回调返回值为rmssd
     * */
    void onRMSSD(int rmssd);
    /**
     * 返回值为手指是否放置的状态，isDetected为true表示放置，false为未放置
     * */
    void onFingerDetected(boolean isDetected);
    /**
     * 分别为心率\hrv有效值比例
     * */
    void onEffectiveValueRate(float hrEffectiveValueRate,float hrvEffectiveValueRate);
}
