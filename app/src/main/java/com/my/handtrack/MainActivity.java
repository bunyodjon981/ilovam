package com.my.handtrack;

import android.Manifest;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.graphics.Matrix;
import android.os.Bundle;
import android.os.SystemClock;
import android.view.Gravity;
import android.widget.FrameLayout;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.camera.core.CameraSelector;
import androidx.camera.core.ImageAnalysis;
import androidx.camera.core.ImageProxy;
import androidx.camera.core.Preview;
import androidx.camera.lifecycle.ProcessCameraProvider;
import androidx.camera.view.PreviewView;
import androidx.core.content.ContextCompat;

import com.google.mediapipe.framework.image.BitmapImageBuilder;
import com.google.mediapipe.framework.image.MPImage;
import com.google.mediapipe.tasks.components.containers.NormalizedLandmark;
import com.google.mediapipe.tasks.core.BaseOptions;
import com.google.mediapipe.tasks.vision.core.RunningMode;
import com.google.mediapipe.tasks.vision.handlandmarker.HandLandmarker;
import com.google.mediapipe.tasks.vision.handlandmarker.HandLandmarkerResult;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class MainActivity extends AppCompatActivity {

    // Model fayli: app/src/main/assets/hand_landmarker.task
    static final String MODEL = "hand_landmarker.task";

    PreviewView preview;
    HandOverlayView overlay;
    TextView status;
    HandLandmarker landmarker;
    final ExecutorService analysisExec = Executors.newSingleThreadExecutor();

    @Override
    protected void onCreate(Bundle b) {
        super.onCreate(b);
        FrameLayout root = new FrameLayout(this);
        preview = new PreviewView(this);
        preview.setScaleType(PreviewView.ScaleType.FILL_CENTER);
        overlay = new HandOverlayView(this);
        status = new TextView(this);
        status.setTextColor(0xFF00FF78);
        status.setTextSize(18);
        status.setPadding(24, 24, 24, 24);
        status.setText("Kamera ishga tushmoqda...");
        root.addView(preview, new FrameLayout.LayoutParams(-1, -1));
        root.addView(overlay, new FrameLayout.LayoutParams(-1, -1));
        root.addView(status, new FrameLayout.LayoutParams(-2, -2, Gravity.TOP));
        setContentView(root);

        if (checkSelfPermission(Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED) {
            start();
        } else {
            requestPermissions(new String[]{Manifest.permission.CAMERA}, 1);
        }
    }

    @Override
    public void onRequestPermissionsResult(int code, @NonNull String[] perms, @NonNull int[] res) {
        super.onRequestPermissionsResult(code, perms, res);
        if (code == 1 && res.length > 0 && res[0] == PackageManager.PERMISSION_GRANTED) {
            start();
        } else {
            status.setText("Kamera ruxsati berilmadi");
        }
    }

    void start() {
        createLandmarker();
        ProcessCameraProvider.getInstance(this).addListener(() -> {
            try {
                bind(ProcessCameraProvider.getInstance(this).get());
            } catch (Exception e) {
                status.setText("Kamera xatosi: " + e.getMessage());
            }
        }, ContextCompat.getMainExecutor(this));
    }

    void createLandmarker() {
        try {
            HandLandmarker.HandLandmarkerOptions opts = HandLandmarker.HandLandmarkerOptions.builder()
                    .setBaseOptions(BaseOptions.builder().setModelAssetPath(MODEL).build())
                    .setRunningMode(RunningMode.LIVE_STREAM)
                    .setNumHands(2)
                    .setMinHandDetectionConfidence(0.5f)
                    .setMinTrackingConfidence(0.5f)
                    .setResultListener((result, input) -> onHands(result, input))
                    .setErrorListener(e -> status.setText("Model xatosi: " + e.getMessage()))
                    .build();
            landmarker = HandLandmarker.createFromOptions(this, opts);
        } catch (Exception e) {
            status.setText("Model topilmadi. hand_landmarker.task faylini assets papkasiga qo'ying.\n" + e.getMessage());
        }
    }

    void bind(ProcessCameraProvider provider) {
        Preview p = new Preview.Builder().build();
        p.setSurfaceProvider(preview.getSurfaceProvider());

        ImageAnalysis ia = new ImageAnalysis.Builder()
                .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                .build();
        ia.setAnalyzer(analysisExec, this::analyze);

        provider.unbindAll();
        provider.bindToLifecycle(this, CameraSelector.DEFAULT_FRONT_CAMERA, p, ia);
    }

    // Har bir kadrni to'g'rilab (aylantirib) MediaPipe'ga yuboramiz
    void analyze(ImageProxy proxy) {
        try {
            if (landmarker == null) return;
            Bitmap bmp = proxy.toBitmap();
            Matrix m = new Matrix();
            m.postRotate(proxy.getImageInfo().getRotationDegrees());
            Bitmap upright = Bitmap.createBitmap(bmp, 0, 0, bmp.getWidth(), bmp.getHeight(), m, true);
            MPImage img = new BitmapImageBuilder(upright).build();
            landmarker.detectAsync(img, SystemClock.uptimeMillis());
        } catch (Exception e) {
            runOnUiThread(() -> status.setText("Tahlil xatosi: " + e.getMessage()));
        } finally {
            proxy.close();
        }
    }

    void onHands(HandLandmarkerResult result, MPImage img) {
        List<float[][]> hands = new ArrayList<float[][]>();
        for (List<NormalizedLandmark> lm : result.landmarks()) {
            float[][] pts = new float[lm.size()][2];
            for (int i = 0; i < lm.size(); i++) {
                pts[i][0] = lm.get(i).x();
                pts[i][1] = lm.get(i).y();
            }
            hands.add(pts);
        }
        final int w = img.getWidth(), h = img.getHeight();
        runOnUiThread(() -> {
            overlay.setHands(hands, w, h);
            status.setText(hands.isEmpty() ? "Qo'l topilmadi" : "Qo'l topildi: " + hands.size());
        });
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (landmarker != null) landmarker.close();
        analysisExec.shutdown();
    }
}
