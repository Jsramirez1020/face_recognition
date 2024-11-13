package com.example.face_recognition;

import android.Manifest;
import android.app.PendingIntent;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.net.Uri;
import android.os.Bundle;
import android.provider.MediaStore;
import android.speech.RecognitionListener;
import android.speech.RecognizerIntent;
import android.speech.SpeechRecognizer;
import android.telephony.SmsManager;
import android.telephony.SubscriptionInfo;
import android.telephony.SubscriptionManager;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.Nullable;
import androidx.annotation.OptIn;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;
import androidx.media3.common.util.Log;
import androidx.media3.common.util.UnstableApi;

import com.google.mlkit.vision.common.InputImage;
import com.google.mlkit.vision.face.Face;
import com.google.mlkit.vision.face.FaceDetection;
import com.google.mlkit.vision.face.FaceDetector;
import com.google.mlkit.vision.face.FaceDetectorOptions;
import com.google.mlkit.vision.face.FaceLandmark;

import java.util.ArrayList;
import java.util.List;

public class CameraActivity extends AppCompatActivity {

    private static final int CAMERA_REQUEST_CODE = 100;
    private static final int AUDIO_REQUEST_CODE = 200;
    private static final int CAMERA_FACING_FRONT = 1;
    private static final int CAMERA_FACING_BACK = 0;

    private SpeechRecognizer speechRecognizer;
    private ImageView imageView;
    private Bitmap bitmap;
    private boolean isFrontCamera = true;

    private final ActivityResultLauncher<Intent> takePictureLauncher = registerForActivityResult(
            new ActivityResultContracts.StartActivityForResult(),
            result -> {
                if (result.getResultCode() == RESULT_OK && result.getData() != null) {
                    Bundle extras = result.getData().getExtras();
                    bitmap = (Bitmap) extras.get("data");
                    imageView.setImageBitmap(bitmap);
                    detectFaces(bitmap);
                }
            }
    );

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_camera);
        imageView = findViewById(R.id.imageView);
        Button takePictureButton = findViewById(R.id.btnTakePicture);
        takePictureButton.setOnClickListener(v -> {
            if (checkCameraPermission()) {
                dispatchTakePictureIntent();
            } else {
                requestCameraPermission();
            }
        });
        startSpeechRecognition();
    }

    private void startSpeechRecognition() {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(this, new String[]{Manifest.permission.RECORD_AUDIO}, AUDIO_REQUEST_CODE);
        } else {
            setupSpeechRecognizer();
        }
    }

    private void setupSpeechRecognizer() {
        speechRecognizer = SpeechRecognizer.createSpeechRecognizer(this);
        Intent intent = new Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH);
        intent.putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM);
        intent.putExtra(RecognizerIntent.EXTRA_LANGUAGE, "es-ES");

        speechRecognizer.setRecognitionListener(new RecognitionListener() {
            @Override
            public void onResults(Bundle results) {
                ArrayList<String> matches = results.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION);
                if (matches != null && matches.contains("foto")) {
                    findViewById(R.id.btnTakePicture).performClick();
                }else if (matches != null && matches.contains("hola")) {
                    switchCamera();
                }
            }

            @Override public void onReadyForSpeech(Bundle params) {}
            @Override public void onBeginningOfSpeech() {}
            @Override public void onRmsChanged(float rmsdB) {}
            @Override public void onBufferReceived(byte[] buffer) {}
            @Override public void onEndOfSpeech() { startSpeechRecognition(); }
            @Override public void onError(int error) { startSpeechRecognition(); }
            @Override public void onPartialResults(Bundle partialResults) {}
            @Override public void onEvent(int eventType, Bundle params) {}
        });

        speechRecognizer.startListening(intent);
    }

    private boolean checkCameraPermission() {
        return ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED;
    }

    private void requestCameraPermission() {
        ActivityCompat.requestPermissions(this, new String[]{Manifest.permission.CAMERA}, CAMERA_REQUEST_CODE);
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == CAMERA_REQUEST_CODE) {
            if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                dispatchTakePictureIntent();
            } else {
                Toast.makeText(this, "Permiso de cámara denegado", Toast.LENGTH_SHORT).show();
            }
        }
    }

    @OptIn(markerClass = UnstableApi.class)
    private void dispatchTakePictureIntent() {
        Intent takePictureIntent = new Intent(MediaStore.ACTION_IMAGE_CAPTURE);
        int cameraFacing = isFrontCamera ? CAMERA_FACING_FRONT : CAMERA_FACING_BACK;
        takePictureIntent.putExtra("android.intent.extras.CAMERA_FACING", cameraFacing);

        if (takePictureIntent.resolveActivity(getPackageManager()) != null) {
            try { takePictureLauncher.launch(takePictureIntent);
            } catch (Exception e) {
                Log.e("TakePictureError", "Error launching camera", e); }
        } else {
            Log.e("TakePictureError", "No camera app available");
        }
    }

    private void switchCamera() {
        isFrontCamera = !isFrontCamera;
        Toast.makeText(this, "Cambiando cámara", Toast.LENGTH_SHORT).show();
    }

    private void detectFaces(Bitmap bitmap) {
        InputImage image = InputImage.fromBitmap(bitmap, 0);
        FaceDetectorOptions options = new FaceDetectorOptions.Builder()
                .setPerformanceMode(FaceDetectorOptions.PERFORMANCE_MODE_FAST)
                .setLandmarkMode(FaceDetectorOptions.LANDMARK_MODE_ALL)
                .setClassificationMode(FaceDetectorOptions.CLASSIFICATION_MODE_ALL)
                .build();

        FaceDetector detector = FaceDetection.getClient(options);

        detector.process(image)
                .addOnSuccessListener(this::handleFaces)
                .addOnFailureListener(e -> Toast.makeText(this, "Error detectando rostros", Toast.LENGTH_SHORT).show());
    }

    private void handleFaces(List<Face> faces) {
        if (faces.isEmpty()) {
            Toast.makeText(this, "No se detectaron rostros", Toast.LENGTH_SHORT).show();
        } else {
            for (Face face : faces) {
                boolean actionTaken = false;

                if (face.getLeftEyeOpenProbability() != null && face.getLeftEyeOpenProbability() < 0.4 &&
                        face.getRightEyeOpenProbability() != null && face.getRightEyeOpenProbability() < 0.4) {
                    openWhatsApp();
                    actionTaken = true;
                } else if (!actionTaken && face.getLeftEyeOpenProbability() != null && face.getLeftEyeOpenProbability() < 0.4) {
                    sendMessage("3118617814", "El ojo izquierdo está cerrado.");
                    actionTaken = true;
                } else if (!actionTaken && face.getRightEyeOpenProbability() != null && face.getRightEyeOpenProbability() < 0.4) {
                    openSettings();
                    actionTaken = true;
                } else if (!actionTaken) {
                    List<FaceLandmark> landmarks = face.getAllLandmarks();
                    boolean mouthOpen = landmarks.stream().anyMatch(landmark -> landmark.getLandmarkType() == FaceLandmark.MOUTH_BOTTOM);
                }

                if (!actionTaken && face.getSmilingProbability() != null && face.getSmilingProbability() > 0.7) {
                    makeCall();
                    actionTaken = true;
                }
                if (!actionTaken && Math.abs(face.getHeadEulerAngleY()) > 40) {
                    openBrowser();
                    actionTaken = true;
                }
                if (!actionTaken) {
                    Toast.makeText(this, "No está realizando ninguna acción", Toast.LENGTH_SHORT).show();
                }
                break;
            }
        }
    }

    private void sendMessage(String phoneNumber, String message) {
        try {
            SmsManager smsManager = SmsManager.getDefault();
            smsManager.sendTextMessage(phoneNumber, null, message, null, null);
            Toast.makeText(this, "Mensaje enviado", Toast.LENGTH_SHORT).show();
        } catch (Exception e) {
            Toast.makeText(this, "Error al enviar el mensaje", Toast.LENGTH_SHORT).show();
        }
    }

    private void openWhatsApp() {
        Intent intent = new Intent(Intent.ACTION_VIEW);
        intent.setData(Uri.parse("https://wa.me/"));
        startActivity(intent);
    }

    private void openSettings() {
        Intent intent = new Intent(android.provider.Settings.ACTION_SETTINGS);
        startActivity(intent);
    }

    private void makeCall() {
        Intent intent = new Intent(Intent.ACTION_CALL);
        intent.setData(Uri.parse("tel:123"));
        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.CALL_PHONE) != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(this, new String[]{Manifest.permission.CALL_PHONE}, 1);
            return;
        }
        startActivity(intent);
    }

    private void openBrowser() {
        Intent browserIntent = new Intent(Intent.ACTION_VIEW, Uri.parse("https://www.google.com"));
        startActivity(browserIntent);
    }
}
