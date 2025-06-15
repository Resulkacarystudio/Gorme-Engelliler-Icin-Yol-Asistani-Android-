package com.example.yolasistani;

import android.Manifest;
import android.content.Context;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.graphics.RectF;
import android.hardware.camera2.*;
import android.os.Bundle;
import android.os.Handler;
import android.os.HandlerThread;
import android.view.Surface;
import android.view.TextureView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

import org.tensorflow.lite.DataType;
import org.tensorflow.lite.support.image.TensorImage;
import org.tensorflow.lite.task.vision.detector.Detection;
import org.tensorflow.lite.task.vision.detector.ObjectDetector;

import android.speech.tts.TextToSpeech;
import android.speech.tts.UtteranceProgressListener;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

public class MainActivity extends AppCompatActivity {

    private static final int REQUEST_CODE = 101;
    private static final int SPEAK_INTERVAL_MS = 3000;
    private static final int MODEL_INPUT_WIDTH = 300;
    private static final int MODEL_INPUT_HEIGHT = 300;

    private TextureView textureView;
    private OverlayView overlayView;
    private ObjectDetector objectDetector;
    private CameraDevice cameraDevice;
    private CameraManager cameraManager;
    private Handler cameraHandler;

    private TextToSpeech textToSpeech;
    private final List<String> speechQueue = new ArrayList<>();
    private boolean isSpeaking = false;
    private long lastAnalysisTime = 0;
    private static final int ANALYSIS_INTERVAL_MS = 2000;
    private long lastSpeakTime = 0;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        textureView = findViewById(R.id.textureView);
        overlayView = findViewById(R.id.overlay);

        HandlerThread handlerThread = new HandlerThread("CameraBackground");
        handlerThread.start();
        cameraHandler = new Handler(handlerThread.getLooper());

        cameraManager = (CameraManager) getSystemService(Context.CAMERA_SERVICE);

        textureView.setSurfaceTextureListener(new TextureView.SurfaceTextureListener() {
            @Override
            public void onSurfaceTextureAvailable(@NonNull android.graphics.SurfaceTexture surface, int width, int height) {
                openCamera();
            }

            @Override public void onSurfaceTextureSizeChanged(@NonNull android.graphics.SurfaceTexture surface, int width, int height) {}
            @Override public boolean onSurfaceTextureDestroyed(@NonNull android.graphics.SurfaceTexture surface) { return false; }
            @Override public void onSurfaceTextureUpdated(@NonNull android.graphics.SurfaceTexture surface) {
                analyzeImage();
            }
        });

        textToSpeech = new TextToSpeech(this, status -> {
            if (status == TextToSpeech.SUCCESS) {
                textToSpeech.setLanguage(new Locale("tr", "TR"));
                textToSpeech.setSpeechRate(1.3f);
            }
        });

        loadModel();
    }

    private void loadModel() {
        try {
            ObjectDetector.ObjectDetectorOptions options = ObjectDetector.ObjectDetectorOptions.builder()
                    .setMaxResults(10)
                    .setScoreThreshold(0.5f)
                    .build();
            objectDetector = ObjectDetector.createFromFileAndOptions(
                    this,
                    "lite-model_ssd_mobilenet_v1_1_metadata_2.tflite",
                    options);
        } catch (Exception e) {
            Toast.makeText(this, "Model yüklenemedi: " + e.getMessage(), Toast.LENGTH_LONG).show();
        }
    }

    private void analyzeImage() {
        long currentTime = System.currentTimeMillis();
        if (currentTime - lastAnalysisTime < ANALYSIS_INTERVAL_MS) return;
        lastAnalysisTime = currentTime;

        Bitmap fullBitmap = textureView.getBitmap();
        if (fullBitmap == null) return;

        Bitmap resizedBitmap = Bitmap.createScaledBitmap(fullBitmap, MODEL_INPUT_WIDTH, MODEL_INPUT_HEIGHT, true);
        TensorImage tensorImage = new TensorImage(DataType.UINT8);
        tensorImage.load(resizedBitmap);

        List<Detection> results = objectDetector.detect(tensorImage);
        List<OverlayView.DetectionResult> formattedResults = new ArrayList<>();

        for (Detection detection : results) {
            String englishLabel = detection.getCategories().isEmpty() ? "Bilinmeyen" : detection.getCategories().get(0).getLabel();
            String label = LABEL_TRANSLATIONS.getOrDefault(englishLabel.toLowerCase(), englishLabel);
            float score = detection.getCategories().isEmpty() ? 0f : detection.getCategories().get(0).getScore();
            RectF location = detection.getBoundingBox();

            float scaleX = (float) fullBitmap.getWidth() / MODEL_INPUT_WIDTH;
            float scaleY = (float) fullBitmap.getHeight() / MODEL_INPUT_HEIGHT;

            RectF scaledLocation = new RectF(
                    location.left * scaleX,
                    location.top * scaleY,
                    location.right * scaleX,
                    location.bottom * scaleY
            );

            formattedResults.add(new OverlayView.DetectionResult(scaledLocation, label, score));

            if (score > 0.5f && System.currentTimeMillis() - lastSpeakTime > SPEAK_INTERVAL_MS) {
                lastSpeakTime = System.currentTimeMillis();
                speechQueue.add("Önünüzde " + label + " var");
            }
        }

        runOnUiThread(() -> {
            int bitmapWidth = fullBitmap.getWidth();
            int bitmapHeight = fullBitmap.getHeight();
            overlayView.setResults(formattedResults, bitmapWidth, bitmapHeight);
            speakNext();
        });
    }

    private void speakNext() {
        if (isSpeaking || speechQueue.isEmpty()) return;
        String text = speechQueue.remove(0);
        isSpeaking = true;

        textToSpeech.speak(text, TextToSpeech.QUEUE_FLUSH, null, "utteranceId");
        textToSpeech.setOnUtteranceProgressListener(new UtteranceProgressListener() {
            @Override public void onStart(String utteranceId) {}
            @Override public void onDone(String utteranceId) {
                isSpeaking = false;
                speakNext();
            }
            @Override public void onError(String utteranceId) {
                isSpeaking = false;
            }
        });
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (textToSpeech != null) {
            textToSpeech.stop();
            textToSpeech.shutdown();
        }
    }

    private void openCamera() {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA) != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(this, new String[]{Manifest.permission.CAMERA}, REQUEST_CODE);
            return;
        }

        try {
            String cameraId = cameraManager.getCameraIdList()[0];
            cameraManager.openCamera(cameraId, new CameraDevice.StateCallback() {
                @Override
                public void onOpened(@NonNull CameraDevice camera) {
                    cameraDevice = camera;
                    Surface surface = new Surface(textureView.getSurfaceTexture());
                    try {
                        CaptureRequest.Builder builder = cameraDevice.createCaptureRequest(CameraDevice.TEMPLATE_PREVIEW);
                        builder.addTarget(surface);
                        cameraDevice.createCaptureSession(List.of(surface), new CameraCaptureSession.StateCallback() {
                            @Override
                            public void onConfigured(@NonNull CameraCaptureSession session) {
                                try {
                                    session.setRepeatingRequest(builder.build(), null, cameraHandler);
                                } catch (CameraAccessException e) {
                                    throw new RuntimeException(e);
                                }
                            }
                            @Override public void onConfigureFailed(@NonNull CameraCaptureSession session) {}
                        }, cameraHandler);
                    } catch (CameraAccessException e) {
                        throw new RuntimeException(e);
                    }
                }
                @Override public void onDisconnected(@NonNull CameraDevice camera) {}
                @Override public void onError(@NonNull CameraDevice camera, int error) {}
            }, cameraHandler);

        } catch (CameraAccessException e) {
            e.printStackTrace();
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        if (requestCode == REQUEST_CODE && grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
            openCamera();
        } else {
            Toast.makeText(this, "Kamera izni gerekli", Toast.LENGTH_SHORT).show();
        }
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
    }


    private static final Map<String, String> LABEL_TRANSLATIONS = new HashMap<>();
    static {
        LABEL_TRANSLATIONS.put("person", "kişi");
        LABEL_TRANSLATIONS.put("bicycle", "bisiklet");
        LABEL_TRANSLATIONS.put("car", "araba");
        LABEL_TRANSLATIONS.put("motorcycle", "motosiklet");
        LABEL_TRANSLATIONS.put("airplane", "uçak");
        LABEL_TRANSLATIONS.put("bus", "otobüs");
        LABEL_TRANSLATIONS.put("train", "tren");
        LABEL_TRANSLATIONS.put("truck", "kamyon");
        LABEL_TRANSLATIONS.put("boat", "tekne");
        LABEL_TRANSLATIONS.put("traffic light", "trafik ışığı");
        LABEL_TRANSLATIONS.put("fire hydrant", "yangın musluğu");
        LABEL_TRANSLATIONS.put("stop sign", "dur işareti");
        LABEL_TRANSLATIONS.put("parking meter", "parkmetre");
        LABEL_TRANSLATIONS.put("bench", "bank");
        LABEL_TRANSLATIONS.put("bird", "kuş");
        LABEL_TRANSLATIONS.put("cat", "kedi");
        LABEL_TRANSLATIONS.put("dog", "köpek");
        LABEL_TRANSLATIONS.put("horse", "at");
        LABEL_TRANSLATIONS.put("sheep", "koyun");
        LABEL_TRANSLATIONS.put("cow", "inek");
        LABEL_TRANSLATIONS.put("elephant", "fil");
        LABEL_TRANSLATIONS.put("bear", "ayı");
        LABEL_TRANSLATIONS.put("zebra", "zebra");
        LABEL_TRANSLATIONS.put("giraffe", "zürafa");
        LABEL_TRANSLATIONS.put("backpack", "sırt çantası");
        LABEL_TRANSLATIONS.put("umbrella", "şemsiye");
        LABEL_TRANSLATIONS.put("handbag", "el çantası");
        LABEL_TRANSLATIONS.put("tie", "kravat");
        LABEL_TRANSLATIONS.put("suitcase", "bavul");
        LABEL_TRANSLATIONS.put("frisbee", "frizbi");
        LABEL_TRANSLATIONS.put("skis", "kayak");
        LABEL_TRANSLATIONS.put("snowboard", "snowboard");
        LABEL_TRANSLATIONS.put("sports ball", "top");
        LABEL_TRANSLATIONS.put("kite", "uçurtma");
        LABEL_TRANSLATIONS.put("baseball bat", "beyzbol sopası");
        LABEL_TRANSLATIONS.put("baseball glove", "beyzbol eldiveni");
        LABEL_TRANSLATIONS.put("skateboard", "kaykay");
        LABEL_TRANSLATIONS.put("surfboard", "sörf tahtası");
        LABEL_TRANSLATIONS.put("tennis racket", "tenis raketi");
        LABEL_TRANSLATIONS.put("bottle", "şişe");
        LABEL_TRANSLATIONS.put("wine glass", "şarap bardağı");
        LABEL_TRANSLATIONS.put("cup", "kupa");
        LABEL_TRANSLATIONS.put("fork", "çatal");
        LABEL_TRANSLATIONS.put("knife", "bıçak");
        LABEL_TRANSLATIONS.put("spoon", "kaşık");
        LABEL_TRANSLATIONS.put("bowl", "kase");
        LABEL_TRANSLATIONS.put("banana", "muz");
        LABEL_TRANSLATIONS.put("apple", "elma");
        LABEL_TRANSLATIONS.put("sandwich", "sandviç");
        LABEL_TRANSLATIONS.put("orange", "portakal");
        LABEL_TRANSLATIONS.put("broccoli", "brokoli");
        LABEL_TRANSLATIONS.put("carrot", "havuç");
        LABEL_TRANSLATIONS.put("hot dog", "sosisli");
        LABEL_TRANSLATIONS.put("pizza", "pizza");
        LABEL_TRANSLATIONS.put("donut", "donut");
        LABEL_TRANSLATIONS.put("cake", "pasta");
        LABEL_TRANSLATIONS.put("chair", "sandalye");
        LABEL_TRANSLATIONS.put("couch", "kanepe");
        LABEL_TRANSLATIONS.put("potted plant", "saksı bitkisi");
        LABEL_TRANSLATIONS.put("bed", "yatak");
        LABEL_TRANSLATIONS.put("dining table", "yemek masası");
        LABEL_TRANSLATIONS.put("toilet", "nesne");
        LABEL_TRANSLATIONS.put("tv", "televizyon");
        LABEL_TRANSLATIONS.put("laptop", "dizüstü bilgisayar");
        LABEL_TRANSLATIONS.put("mouse", "fare");
        LABEL_TRANSLATIONS.put("remote", "kumanda");
        LABEL_TRANSLATIONS.put("keyboard", "klavye");
        LABEL_TRANSLATIONS.put("cell phone", "cep telefonu");
        LABEL_TRANSLATIONS.put("microwave", "mikrodalga");
        LABEL_TRANSLATIONS.put("oven", "fırın");
        LABEL_TRANSLATIONS.put("toaster", "ekmek kızartma makinesi");
        LABEL_TRANSLATIONS.put("sink", "lavabo");
        LABEL_TRANSLATIONS.put("refrigerator", "buzdolabı");
        LABEL_TRANSLATIONS.put("book", "kitap");
        LABEL_TRANSLATIONS.put("clock", "saat");
        LABEL_TRANSLATIONS.put("vase", "vazo");
        LABEL_TRANSLATIONS.put("scissors", "makas");
        LABEL_TRANSLATIONS.put("teddy bear", "oyuncak ayı");
        LABEL_TRANSLATIONS.put("hair drier", "saç kurutma makinesi");
        LABEL_TRANSLATIONS.put("toothbrush", "diş fırçası");
    }

}
