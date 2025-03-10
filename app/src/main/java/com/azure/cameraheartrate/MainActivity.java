package com.azure.cameraheartrate;

import android.os.Bundle;
import android.util.Log;
import android.widget.TextView;

import androidx.activity.EdgeToEdge;
import androidx.annotation.OptIn;
import androidx.appcompat.app.AppCompatActivity;
import androidx.camera.core.Camera;
import androidx.camera.core.CameraSelector;
import androidx.camera.core.ExperimentalGetImage;
import androidx.camera.core.ImageAnalysis;
import androidx.camera.core.Preview;
import androidx.camera.lifecycle.ProcessCameraProvider;
import androidx.camera.view.PreviewView;
import androidx.core.content.ContextCompat;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.lifecycle.LifecycleOwner;

import com.azure.cameraheartratesdk.listener.CameraHeartRateListener;
import com.azure.cameraheartratesdk.manager.CameraHeartRateManager;
import com.google.common.util.concurrent.ListenableFuture;

import java.util.concurrent.ExecutionException;

public class MainActivity extends AppCompatActivity {

    private PreviewView previewView;
    private TextView fingerDetectedTextView;
    private TextView effectiveValueTextView;
    private TextView heartRateTextView;
    private TextView sdnnTextView;
    private TextView rmssdTextView;

    private CameraHeartRateListener cameraHeartRateListener = new CameraHeartRateListener() {
        @Override
        public void onHeartRate(int heartRate) {
            String text = "heartRate: " + heartRate;
            heartRateTextView.setText(text);
        }

        @Override
        public void onSDNN(int sdnn) {
            String text = "sdnn: " + sdnn;
            sdnnTextView.setText(text);
        }

        @Override
        public void onRMSSD(int rmssd) {
            String text = "rmssd: " + rmssd;
            rmssdTextView.setText(text);
        }

        @Override
        public void onFingerDetected(boolean fingerDetected) {
            String text = "fingerDetected: " + fingerDetected;
            fingerDetectedTextView.setText(text);
        }

        @Override
        public void onEffectiveValueRate(float hrEffective, float hrvEffective) {
            String text = "hrEffective: " + hrEffective + ", hrvEffective: " + hrvEffective;
            effectiveValueTextView.setText(text);
        }
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        EdgeToEdge.enable(this);
        setContentView(R.layout.activity_main);
        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main), (v, insets) -> {
            Insets systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars());
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom);
            return insets;
        });

        previewView = findViewById(R.id.previewView);
        fingerDetectedTextView  = findViewById(R.id.finger_detected);
        effectiveValueTextView  = findViewById(R.id.effective_value);
        heartRateTextView  = findViewById(R.id.heart_rate);
        sdnnTextView  = findViewById(R.id.sdnn);
        rmssdTextView  = findViewById(R.id.rmssd);
        CameraHeartRateManager.getInstance().init();
        CameraHeartRateManager.getInstance().addCameraHeartRateListener(cameraHeartRateListener);


        startCamera();
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();

        CameraHeartRateManager.getInstance().removeCameraHeartRateListener(cameraHeartRateListener);
        CameraHeartRateManager.getInstance().unInit();
    }

    @OptIn(markerClass = ExperimentalGetImage.class)
    private void startCamera() {
        // Request permissions (Camera, etc.)
//        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.CAMERA)
//                != PackageManager.PERMISSION_GRANTED) {
//            ActivityCompat.requestPermissions(this, new String[]{Manifest.permission.CAMERA}, 100);
//            return;
//        }

        // Get the camera provider
        ListenableFuture<ProcessCameraProvider> cameraProviderFuture = ProcessCameraProvider.getInstance(this);

        // Add a listener to execute when the camera provider is available
        cameraProviderFuture.addListener(() -> {
            try {
                // Get the camera provider from the future
                ProcessCameraProvider cameraProvider = cameraProviderFuture.get();

                // Build camera selector
                CameraSelector cameraSelector = new CameraSelector.Builder()
                        .requireLensFacing(CameraSelector.LENS_FACING_BACK) // Front/Back camera
                        .build();

                // Create Preview use case
                Preview preview = new Preview.Builder().build();

                // Create ImageAnalysis use case for frame-by-frame analysis
                ImageAnalysis imageAnalysis = new ImageAnalysis.Builder()
                        .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                        .build();

                // Set an analyzer for the image analysis use case
                imageAnalysis.setAnalyzer(ContextCompat.getMainExecutor(this), image -> {
                    CameraHeartRateManager.getInstance().analyzeImage(image);
                });

                // Bind the camera use cases (preview and analysis) to the lifecycle
                cameraProvider.bindToLifecycle(this, cameraSelector, preview, imageAnalysis);

                // Bind the camera lifecycle to the current activity
                Camera camera = cameraProvider.bindToLifecycle((LifecycleOwner) this, cameraSelector, preview, imageAnalysis);

                // Enable the torch (flashlight)
                camera.getCameraControl().enableTorch(true);

                // Attach preview to the UI
                preview.setSurfaceProvider(previewView.getSurfaceProvider());

            } catch (ExecutionException | InterruptedException e) {
                e.printStackTrace();
            }
        }, ContextCompat.getMainExecutor(this));

    }

}