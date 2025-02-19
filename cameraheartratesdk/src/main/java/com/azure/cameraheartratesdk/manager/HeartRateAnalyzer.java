package com.azure.cameraheartratesdk.manager;

import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.ImageFormat;
import android.graphics.YuvImage;
import android.media.Image;
import android.util.Log;

import androidx.annotation.OptIn;
import androidx.camera.core.ExperimentalGetImage;
import androidx.camera.core.ImageAnalysis;
import androidx.camera.core.ImageProxy;

import com.azure.cameraheartratesdk.listener.CameraHeartRateListener;

import java.io.ByteArrayOutputStream;
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

class HeartRateAnalyzer implements ImageAnalysis.Analyzer {
    private long lastAnalyzedTimestamp = 0L;
    //上次得到的心率
    private int lastHeartRate = 0;
    //前项心率占比，平滑过渡心率计算结果
    private float heartRateSmooth = 0.5f;
    private final List<Long> frameTimestamps = new ArrayList<>();
    private final List<Double> redIntensity = new ArrayList<>();
    Set<CameraHeartRateListener> cameraHeartRateListenerSet = new HashSet<>();

    @OptIn(markerClass = ExperimentalGetImage.class)
    @Override
    public void analyze(ImageProxy image) {
        long currentTimestamp = System.currentTimeMillis();

        if (currentTimestamp - lastAnalyzedTimestamp >= 100) {
            //转化为RGB格式的Bitmap
            Bitmap bitmap = yuvToRgb(image.getImage());
            if (bitmap == null) {
                Log.e("HeartRateAnalyzer", "Bitmap creation failed");
                image.close();
                return;
            }

            //手指是否在相机上
            if (!isFingerCoveringCamera(bitmap)) {
                Log.d("HeartRateAnalyzer", "Finger not detected on camera");
                Set<CameraHeartRateListener> cameraHeartRateListenerSet1 = cloneHeartRateListenerSet();
                for (CameraHeartRateListener cameraHeartRateListener : cameraHeartRateListenerSet1){
                    cameraHeartRateListener.onHeartRate(0);
                    cameraHeartRateListener.onFingerDetected(false);
                }
                image.close();
                return;
            }

            double avgRedIntensity = calculateAverageRedIntensity(bitmap);
            redIntensity.add(avgRedIntensity);
            frameTimestamps.add(currentTimestamp);

            if (frameTimestamps.size() > 30) {
                int heartRate = calculateHeartRate(frameTimestamps, redIntensity);
                Set<CameraHeartRateListener> cameraHeartRateListenerSet1 = cloneHeartRateListenerSet();
                for (CameraHeartRateListener cameraHeartRateListener : cameraHeartRateListenerSet1){
                    cameraHeartRateListener.onHeartRate(heartRate);
                    cameraHeartRateListener.onFingerDetected(true);
                }
                frameTimestamps.clear();
                redIntensity.clear();
            }

            lastAnalyzedTimestamp = currentTimestamp;
        }

        image.close();
    }

    private double calculateAverageRedIntensity(Bitmap bitmap) {
        double redSum = 0.0;
        int pixelCount = 0;

        for (int y = 0; y < bitmap.getHeight(); y += 10) {
            for (int x = 0; x < bitmap.getWidth(); x += 10) {
                int pixel = bitmap.getPixel(x, y);
                int red = (pixel >> 16) & 0xFF;
                redSum += red;
                pixelCount++;
            }
        }

        return pixelCount > 0 ? redSum / pixelCount : 0.0;
    }

    private int calculateHeartRate(List<Long> timestamps, List<Double> intensities) {
        List<Double> smoothedIntensities = smoothData(intensities, 5);
        List<Integer> peaks = findPeaks(smoothedIntensities);
        if (peaks.size() < 2) return 0;

        List<Long> durations = new ArrayList<>();
        for (int i = 0; i < peaks.size() - 1; i++) {
            durations.add(timestamps.get(peaks.get(i + 1)) - timestamps.get(peaks.get(i)));
        }

        double averageDuration = 0;
        for (long duration : durations) {
            averageDuration += duration;
        }
        averageDuration /= durations.size();
        int heartRateNew = (int) (60000 / averageDuration);

        // 如果 lastHeartRate 不在正常范围内
        if (!isNormalRange(lastHeartRate)){
            // 如果 heartRateNew 在正常范围内，则更新并返回
            if (isNormalRange(heartRateNew)){
                lastHeartRate = heartRateNew;
                return heartRateNew;
            } else {
                // lastHeartRate和heartRateNew都不在正常范围，返回0
                return 0;
            }
        } else {
            // 如果 lastHeartRate 在正常范围内
            if (isNormalRange(heartRateNew)){
                // 平滑处理 heartRateNew，并返回
                heartRateNew = (int) (heartRateNew * (1 - heartRateSmooth) + lastHeartRate * heartRateSmooth);
                lastHeartRate = heartRateNew;
                return heartRateNew;
            } else {
                // 如果 heartRateNew 不在正常范围内，返回上一个正常的心率
                int hr = lastHeartRate;
                lastHeartRate = heartRateNew;
                return hr;
            }
        }

    }

    private boolean isNormalRange(int heartRate){
        return heartRate > 50 && heartRate < 200;
    }

    private List<Integer> findPeaks(List<Double> intensities) {
        List<Integer> peaks = new ArrayList<>();
        for (int i = 1; i < intensities.size() - 1; i++) {
            if (intensities.get(i) > intensities.get(i - 1) && intensities.get(i) > intensities.get(i + 1)) {
                peaks.add(i);
            }
        }
        return peaks;
    }

    private List<Double> smoothData(List<Double> intensities, int windowSize) {
        List<Double> smoothedIntensities = new ArrayList<>();
        for (int i = 0; i < intensities.size(); i++) {
            int start = Math.max(0, i - windowSize / 2);
            int end = Math.min(intensities.size(), i + windowSize / 2);
            List<Double> window = intensities.subList(start, end);
            double average = 0;
            for (double val : window) {
                average += val;
            }
            smoothedIntensities.add(average / window.size());
        }
        return smoothedIntensities;
    }

    public static Bitmap yuvToRgb(Image image) {
        if (image == null) return null;

        byte[] nv21 = yuv420ToNv21(image);  // 将 YUV420 数据转换为 NV21 格式
        YuvImage yuvImage = new YuvImage(nv21, ImageFormat.NV21, image.getWidth(), image.getHeight(), null);  // 创建一个 YuvImage 对象
        ByteArrayOutputStream out = new ByteArrayOutputStream();  // 用于输出数据流
        yuvImage.compressToJpeg(new android.graphics.Rect(0, 0, image.getWidth(), image.getHeight()), 100, out);  // 将 YUV 数据压缩为 JPEG 格式
        byte[] imageBytes = out.toByteArray();  // 获取 JPEG 格式的字节数据
        return BitmapFactory.decodeByteArray(imageBytes, 0, imageBytes.length);  // 将 JPEG 字节数组转换为 Bitmap
    }

    private static byte[] yuv420ToNv21(Image image) {
        ByteBuffer yBuffer = image.getPlanes()[0].getBuffer();  // Y 亮度数据
        ByteBuffer uBuffer = image.getPlanes()[1].getBuffer();  // U 色度数据
        ByteBuffer vBuffer = image.getPlanes()[2].getBuffer();  // V 色度数据

        int ySize = yBuffer.remaining();  // Y 数据的大小
        int uSize = uBuffer.remaining();  // U 数据的大小
        int vSize = vBuffer.remaining();  // V 数据的大小

        byte[] nv21 = new byte[ySize + uSize + vSize];  // 创建一个字节数组存储转换后的数据

        yBuffer.get(nv21, 0, ySize);  // 将 Y 数据复制到结果数组
        vBuffer.get(nv21, ySize, vSize);  // 将 V 数据复制到结果数组
        uBuffer.get(nv21, ySize + vSize, uSize);  // 将 U 数据复制到结果数组

        return nv21;  // 返回 NV21 格式的数据
    }


    private boolean isFingerCoveringCamera(Bitmap bitmap) {
        int regionWidth = bitmap.getWidth() / 4;
        int regionHeight = bitmap.getHeight() / 4;
        int startX = bitmap.getWidth() / 2 - regionWidth / 2;
        int startY = bitmap.getHeight() / 2 - regionHeight / 2;

        int redPixelCount = 0;
        int totalPixelCount = 0;

        for (int y = startY; y < startY + regionHeight; y++) {
            for (int x = startX; x < startX + regionWidth; x++) {
                int pixel = bitmap.getPixel(x, y);
                int red = (pixel >> 16) & 0xFF;
                int green = (pixel >> 8) & 0xFF;
                int blue = pixel & 0xFF;

                if (red > green && red > blue) {
                    redPixelCount++;
                }
                totalPixelCount++;
            }
        }

        double redRatio = (double) redPixelCount / totalPixelCount;
        Log.d("HeartRateAnalyzer", "Red pixel ratio: " + redRatio);
        return redRatio > 0.8; // Adjust this threshold as necessary
    }

    public void addHeartRateListener(CameraHeartRateListener cameraHeartRateListener){
        cameraHeartRateListenerSet.add(cameraHeartRateListener);
    }

    public void removeHeartRateListener(CameraHeartRateListener cameraHeartRateListener){
        cameraHeartRateListenerSet.remove(cameraHeartRateListener);
    }

    private Set<CameraHeartRateListener> cloneHeartRateListenerSet() {
        Set<CameraHeartRateListener> cameraHeartRateListenerSetNew = new HashSet<>();
        cameraHeartRateListenerSetNew.addAll(this.cameraHeartRateListenerSet);
        return cameraHeartRateListenerSetNew;
    }
}

