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
import java.util.Collections;
import java.util.HashSet;
import java.util.Iterator;
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
    List<Long> RRList = new ArrayList<>();

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
                    RRList.clear();
                }
                image.close();
                return;
            }

            double avgRedIntensity = calculateAverageRedIntensity(bitmap);
            redIntensity.add(avgRedIntensity);
            frameTimestamps.add(currentTimestamp);

            if (frameTimestamps.size() > 30) {
                List<Long> newRRList = calculateRR(frameTimestamps, redIntensity);
                addRRElements(RRList, newRRList, 10);
                List<Long> filterRRByChange = filterRRByChange(RRList, 0.3);
                int heartRate = calculateHeartRate(filterRRByChange);
//                int rmssd = calculateRMSSD(filterRRByChange);
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

    private List<Long> calculateRR(List<Long> timestamps, List<Double> intensities){
        List<Double> smoothedIntensities = smoothData(intensities, 5);
        List<Integer> peaks = findPeaks(smoothedIntensities);
        if (peaks.size() < 2) return null;

        List<Long> durations = new ArrayList<>();
        for (int i = 0; i < peaks.size() - 1; i++) {
            durations.add(timestamps.get(peaks.get(i + 1)) - timestamps.get(peaks.get(i)));
        }

        return durations;
    }

    private int calculateHeartRate(List<Long> durations) {
        if (durations == null){
            return 0;
        }

        double averageDuration = 0;
        for (long duration : durations) {
            averageDuration += duration;
        }
        averageDuration /= durations.size();
        int heartRateNew = (int) (60 * 1000 / averageDuration);

        return heartRateNew;
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

    /**
     * 将 newElements 添加到 original 中，保持 original 的最大长度为 30
     * 如果相加长度超过30，则从 original 的头部移除最早的数据。
     *
     * @param original 原有数据列表
     * @param newElements 新添加的数据列表
     */
    public static void addRRElements(List<Long> original, List<Long> newElements, Integer capacity) {
        if (newElements != null){
            int totalSize = original.size() + newElements.size();

            // 如果总长度超过容量，则移除多余的数据（从最老的数据开始，即列表头部）
            if (totalSize > capacity) {
                int toRemove = totalSize - capacity;
                // 注意：每次移除首元素，因为每次移除后索引会自动更新
                for (int i = 0; i < toRemove; i++) {
                    if (!original.isEmpty()) {
                        original.remove(0);
                    } else {
                        // 如果 original 已经为空，则不需要移除
                        break;
                    }
                }
            }

            // 将所有 newElements 添加到 original 中
            original.addAll(newElements);
        }
    }

    /**
     * 过滤 R-R 间期数据，过滤条件为：
     * 1. 只保留 [0.3, 1.4] 秒内的数值
     * 2. 计算相邻间期变化率，超过 changeThreshold 的数据将被剔除
     *
     * @param rrIntervals 原始 R-R 间期数组
     * @param changeThreshold 变化率阈值，默认 0.2
     * @return 过滤后的 R-R 间期列表
     */
    public static List<Long> filterRRByChange(List<Long> rrIntervals, double changeThreshold) {
        if (rrIntervals == null){
            return null;
        }
        // 将输入数据复制到新的列表中，便于操作
        List<Long> filteredRR = new ArrayList<>(rrIntervals);

        // 设定合理范围
        double rrMin = 300, rrMax = 1400;
        // 过滤掉不在合理范围内的数据
        Iterator<Long> iter = filteredRR.iterator();
        while (iter.hasNext()) {
            double rr = iter.next();
            if (rr < rrMin || rr > rrMax) {
                iter.remove();
            }
        }

        // 循环过滤，直到没有数据被剔除
        while (true) {
            if (filteredRR.size() < 3) {
                break;  // 数据太少，不需要筛选
            }

            // 求中位数
            Long medianRR = median(filteredRR);

            // 计算相邻 R-R 之间的变化率
            List<Long> rrDiff = new ArrayList<>();
            // 先计算首元素与中位数的相对差
            Long firstDiff = Math.abs(filteredRR.get(0) - medianRR) / medianRR;
            rrDiff.add(firstDiff);

            // 对于后续元素，计算相邻元素的变化率
            for (int i = 1; i < filteredRR.size(); i++) {
                Long diff = Math.abs(filteredRR.get(i) - filteredRR.get(i - 1)) / filteredRR.get(i - 1);
                rrDiff.add(diff);
            }

            // 根据变化率阈值过滤数据
            List<Long> newFilteredRR = new ArrayList<>();
            for (int i = 0; i < rrDiff.size(); i++) {
                if (rrDiff.get(i) <= changeThreshold) {
                    newFilteredRR.add(filteredRR.get(i));
                }
            }

            // 如果数据量没有变化，则退出循环
            if (newFilteredRR.size() == filteredRR.size()) {
                break;
            } else {
                filteredRR = newFilteredRR;
            }
        }
        return filteredRR;
    }

    /**
     * 计算 List 中的中位数
     *
     * @param list 数据列表
     * @return 中位数
     */
    private static Long median(List<Long> list) {
        List<Long> temp = new ArrayList<>(list);
        Collections.sort(temp);
        int n = temp.size();
        if (n % 2 == 0) {
            return (long) ((temp.get(n / 2 - 1) + temp.get(n / 2)) / 2.0);
        } else {
            return temp.get(n / 2);
        }
    }

//    /**
//     * 计算 SDNN，即所有心跳间期（NN间期）的标准差
//     * @param rrIntervals 心跳间隔数组，单位：毫秒
//     * @return SDNN 值（毫秒）
//     */
//    public static double calculateSDNN(List<Long> rrIntervals) {
//        if (rrIntervals == null || rrIntervals.isEmpty()) {
//            return 0;
//        }
//
//        // 计算平均值
//        double sum = 0;
//        for (Long rr : rrIntervals) {
//            sum += rr;
//        }
//        double mean = sum / rrIntervals.size();
//
//        // 计算方差
//        double variance = 0;
//        for (Long rr : rrIntervals) {
//            variance += Math.pow(rr - mean, 2);
//        }
//        variance = variance / rrIntervals.size();
//
//        // 返回标准差
//        return Math.sqrt(variance);
//    }
//
//    /**
//     * 计算 RMSSD，即相邻心跳间隔差的均方根
//     * @param rrIntervals 心跳间隔数组，单位：毫秒
//     * @return RMSSD 值（毫秒）
//     */
//    public static double calculateRMSSD(List<Long> rrIntervals) {
//        if (rrIntervals == null || rrIntervals.size() < 2) {
//            return 0;
//        }
//
//        double sumSquaredDiffs = 0;
//        // 计算连续间隔差的平方和
//        for (int i = 1; i < rrIntervals.size(); i++) {
//            long diff = rrIntervals.get(i) - rrIntervals.get(i - 1);
//            sumSquaredDiffs += diff * diff;
//        }
//
//        // 计算均值，然后取平方根
//        double meanSquaredDiff = sumSquaredDiffs / (rrIntervals.size() - 1);
//        return Math.sqrt(meanSquaredDiff);
//    }
}

