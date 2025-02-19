package com.azure.cameraheartratesdk.manager;

import androidx.camera.core.ImageProxy;

import com.azure.cameraheartratesdk.listener.CameraHeartRateListener;

public class CameraHeartRateManager {

    private static CameraHeartRateManager instance;

    private HeartRateAnalyzer heartRateAnalyzer;

    private CameraHeartRateManager() {
    }

    public static CameraHeartRateManager getInstance() {
        if (instance == null) {
            synchronized (CameraHeartRateManager.class) {
                if (instance == null) {
                    instance = new CameraHeartRateManager();
                }
            }
        }
        return instance;
    }

    /**
     * 初始化
     * */
    public void init(){
        heartRateAnalyzer = new HeartRateAnalyzer();
    }

    /**
     * 反初始化
     * */
    public void unInit(){
        heartRateAnalyzer = null;
    }

    /**
     * 添加相机心率监听器
     * */
    public void addCameraHeartRateListener(CameraHeartRateListener listener){
        if (heartRateAnalyzer != null){
            heartRateAnalyzer.addHeartRateListener(listener);
        }
    }

    /**
     * 移除相机心率监听器
     * */
    public void removeCameraHeartRateListener(CameraHeartRateListener listener){
        if (heartRateAnalyzer != null){
            heartRateAnalyzer.removeHeartRateListener(listener);
        }
    }

    /**
     * 根据图像分析心率
     * */
    public void analyzeImage(ImageProxy image){
        if (heartRateAnalyzer != null){
            heartRateAnalyzer.analyze(image);
        }
    }
}
