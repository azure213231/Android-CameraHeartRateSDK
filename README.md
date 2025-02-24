# Android-CameraHeartRateSDK

## **相机PPG心率监测SDK**

本项目通过相机实时监测返回心率值，release版本中的aar即可导入使用，喜欢本项目请为此项目添加⭐⭐⭐⭐⭐

### **初始化**

在监测心率页面创建时进行初始化

```
CameraHeartRateManager.getInstance().init();
```



### **反初始化**

在监测心率页面销毁时进行反初始化

```
CameraHeartRateManager.getInstance().init();
```



### **添加**相机**心率监听器**

在manager初始化后进行调用，其中CameraHeartRateListener属于SDK中的类

```
CameraHeartRateManager.getInstance().addCameraHeartRateListener(CameraHeartRateListener listener)
```

onHeartRate回调返回值为心率，单位为 次数/分钟

onFingerDetected返回值为手指是否放置的状态，isDetected为true表示放置，false为未放置

```
 private CameraHeartRateListener cameraHeartRateListener = new CameraHeartRateListener() {
    @Override
    public void onHeartRate(int heartRate) {
      //返回值为心率，单位为 次数/分钟
    }

    @Override
    public void onFingerDetected(boolean isDetected) {
      //返回值为手指是否放置的状态，isDetected为true表示放置，false为未放置
    }
};
```



### 移除相机心率监听器

在不需要监听器时进行移除，释放资源。

```
CameraHeartRateManager.getInstance().removeCameraHeartRateListener(CameraHeartRateListener listener)
```



### 根据图像分析心率

将相机的实时image传输进入，根据提供的image进行分析心率。结果会在相机心率监听器中返回。

Android中的ImageProxy类为androidx.camera.core.ImageProxy

```
CameraHeartRateManager.getInstance().analyzeImage(ImageProxy image)
```
