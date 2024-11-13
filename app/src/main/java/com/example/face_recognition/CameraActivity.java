package com.example.face_recognition;

import android.Manifest;
import android.app.PendingIntent;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.net.Uri;
import android.os.Bundle;
import android.provider.MediaStore;
import android.telephony.SmsManager;
import android.telephony.SubscriptionInfo;
import android.telephony.SubscriptionManager;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

import com.google.mlkit.vision.common.InputImage;
import com.google.mlkit.vision.face.Face;
import com.google.mlkit.vision.face.FaceDetection;
import com.google.mlkit.vision.face.FaceDetector;
import com.google.mlkit.vision.face.FaceDetectorOptions;
import com.google.mlkit.vision.face.FaceLandmark;

import java.util.List;

public class CameraActivity extends AppCompatActivity {

    private static final int CAMERA_REQUEST_CODE = 100;


    private ImageView imageView;
    private Bitmap bitmap;

    // Lanzador para manejar el resultado de la cámara
    private final ActivityResultLauncher<Intent> takePictureLauncher = registerForActivityResult(
            new ActivityResultContracts.StartActivityForResult(),
            result -> {
                if (result.getResultCode() == RESULT_OK && result.getData() != null) {
                    Bundle extras = result.getData().getExtras();
                    bitmap = (Bitmap) extras.get("data");
                    imageView.setImageBitmap(bitmap); // Mostrar la imagen capturada en el ImageView

                    // Detectar rostros una vez que la imagen es capturada
                    detectFaces(bitmap);
                }
            }
    );

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_camera);

        // Asignamos el ImageView donde se mostrará la imagen
        imageView = findViewById(R.id.imageView);

        // Configuramos el botón para tomar la foto
        Button takePictureButton = findViewById(R.id.btnTakePicture);
        takePictureButton.setOnClickListener(v -> {
            if (checkCameraPermission()) {
                dispatchTakePictureIntent();
            } else {
                requestCameraPermission();
            }
        });
    }

    // Método para comprobar si se tiene permiso para usar la cámara
    private boolean checkCameraPermission() {
        return ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED;
    }

    // Método para solicitar permiso de cámara
    private void requestCameraPermission() {
        ActivityCompat.requestPermissions(this, new String[]{Manifest.permission.CAMERA}, CAMERA_REQUEST_CODE);
    }

    // Método para manejar la respuesta de permisos
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

    // Método para lanzar la cámara
    private void dispatchTakePictureIntent() {
        Intent takePictureIntent = new Intent(MediaStore.ACTION_IMAGE_CAPTURE);
        if (takePictureIntent.resolveActivity(getPackageManager()) != null) {
            takePictureLauncher.launch(takePictureIntent); // Usamos el nuevo launcher
        }
    }

    // Método para detectar rostros en el bitmap usando ML Kit
    private void detectFaces(Bitmap bitmap) {
        // Convertir el Bitmap a un InputImage
        InputImage image = InputImage.fromBitmap(bitmap, 0);

        // Configurar el detector de rostros con las opciones deseadas
        FaceDetectorOptions options = new FaceDetectorOptions.Builder()
                .setPerformanceMode(FaceDetectorOptions.PERFORMANCE_MODE_FAST)
                .setLandmarkMode(FaceDetectorOptions.LANDMARK_MODE_ALL)
                .setClassificationMode(FaceDetectorOptions.CLASSIFICATION_MODE_ALL)
                .build();

        // Obtener el detector de rostros de ML Kit
        FaceDetector detector = FaceDetection.getClient(options);

        // Procesar la imagen
        detector.process(image)
                .addOnSuccessListener(faces -> {
                    // Manejar el éxito de la detección de rostros
                    handleFaces(faces);
                })
                .addOnFailureListener(e -> {
                    // Manejar el fallo en la detección
                    Toast.makeText(this, "Error detectando rostros", Toast.LENGTH_SHORT).show();
                });
    }

    // Método para manejar los rostros detectados
    private void handleFaces(List<Face> faces) {
        if (faces.isEmpty()) {
            Toast.makeText(this, "No se detectaron rostros", Toast.LENGTH_SHORT).show();
        } else {
            for (Face face : faces) {
                boolean actionTaken = false;

                // Si ambos ojos están cerrados, abrir WhatsApp
                if (face.getLeftEyeOpenProbability() != null && face.getLeftEyeOpenProbability() < 0.5 &&
                        face.getRightEyeOpenProbability() != null && face.getRightEyeOpenProbability() < 0.5) {
                    openWhatsApp();
                    actionTaken = true;
                }

                // Si el ojo izquierdo está cerrado (y ambos ojos no están cerrados), enviar un mensaje
                else if (!actionTaken && face.getLeftEyeOpenProbability() != null && face.getLeftEyeOpenProbability() < 0.5) {
                    sendMessage("3118617814", "El ojo izquierdo está cerrado.");
                    actionTaken = true;
                }

                // Si el ojo derecho está cerrado, abrir la configuración
                else if (!actionTaken && face.getRightEyeOpenProbability() != null && face.getRightEyeOpenProbability() < 0.5) {
                    openSettings();
                    actionTaken = true;
                }

                // Si la boca está abierta, abrir la aplicación de llamadas
                else if (!actionTaken) {
                    List<FaceLandmark> landmarks = face.getAllLandmarks();
                    boolean mouthOpen = false;
                    for (FaceLandmark landmark : landmarks) {
                        if (landmark.getLandmarkType() == FaceLandmark.MOUTH_BOTTOM) {
                            mouthOpen = true; // Detectar si la boca está abierta
                            break;
                        }
                    }
                    if (mouthOpen) {
                        openDialer();
                        actionTaken = true;
                    }
                }

                // Si el usuario está sonriendo (y no se han cumplido otras condiciones), llamar al 123
                if (!actionTaken && face.getSmilingProbability() != null && face.getSmilingProbability() > 0.5) {
                    makeCall();
                    actionTaken = true;
                }

                // Si la cabeza está inclinada, abrir el navegador
                if (!actionTaken && Math.abs(face.getHeadEulerAngleY()) > 20) {
                    openBrowser();
                    actionTaken = true;
                }

                // Si ninguna de las condiciones anteriores se cumple, muestra un mensaje
                if (!actionTaken) {
                    Toast.makeText(this, "No está realizando ninguna acción", Toast.LENGTH_SHORT).show();
                }
                break; // Procesar solo el primer rostro detectado
            }
        }
    }

    // Método para enviar un mensaje de texto
    private void sendMessage(String phoneNumber, String message) {
        // Verificar si el dispositivo tiene el servicio de mensajería
        if (getApplicationContext().getPackageManager().hasSystemFeature(PackageManager.FEATURE_TELEPHONY_MESSAGING)) {
            try {
                // Obtener el SubscriptionManager
                SubscriptionManager subscriptionManager = (SubscriptionManager) getSystemService(SubscriptionManager.class);

                // Obtener la lista de suscripciones activas
                if (ActivityCompat.checkSelfPermission(this, Manifest.permission.READ_PHONE_STATE) != PackageManager.PERMISSION_GRANTED) {
                    // TODO: Consider calling
                    //    ActivityCompat#requestPermissions
                    // here to request the missing permissions, and then overriding
                    //   public void onRequestPermissionsResult(int requestCode, String[] permissions,
                    //                                          int[] grantResults)
                    // to handle the case where the user grants the permission. See the documentation
                    // for ActivityCompat#requestPermissions for more details.
                    return;
                }
                List<SubscriptionInfo> subscriptionInfoList = subscriptionManager.getActiveSubscriptionInfoList();
                if (subscriptionInfoList != null && !subscriptionInfoList.isEmpty()) {
                    // Obtener el ID de la primera suscripción activa
                    int subscriptionId = subscriptionInfoList.get(0).getSubscriptionId();

                    // Crear una instancia de SmsManager
                    SmsManager smsManager = SmsManager.getDefault();

                    // Enviar el mensaje de texto
                    PendingIntent sentIntent = PendingIntent.getBroadcast(this, 0, new Intent("SMS_SENT"), PendingIntent.FLAG_IMMUTABLE);
                    PendingIntent deliveredIntent = PendingIntent.getBroadcast(this, 0, new Intent("SMS_DELIVERED"), PendingIntent.FLAG_IMMUTABLE);
                    smsManager.sendTextMessage(phoneNumber, null, message, sentIntent, deliveredIntent);
                    Toast.makeText(this, "Mensaje enviado", Toast.LENGTH_SHORT).show();
                } else {
                    Toast.makeText(this, "No hay suscripciones activas.", Toast.LENGTH_SHORT).show();
                }
            } catch (Exception e) {
                Toast.makeText(this, "Error al enviar el mensaje: " + e.getMessage(), Toast.LENGTH_SHORT).show();
            }
        } else {
            Toast.makeText(this, "El dispositivo no tiene servicio de mensajería.", Toast.LENGTH_SHORT).show();
        }
    }

    // Método para abrir WhatsApp
    private void openWhatsApp() {
        Intent intent = new Intent(Intent.ACTION_VIEW);
        intent.setData(Uri.parse("https://wa.me/"));
        startActivity(intent);
    }

    // Método para abrir la aplicación de llamadas
    private void openDialer() {
        Intent intent = new Intent(Intent.ACTION_DIAL);
        intent.setData(Uri.parse("tel:3118617814"));
        startActivity(intent);
    }

    // Método para abrir la configuración de Android
    private void openSettings() {
        Intent intent = new Intent(android.provider.Settings.ACTION_SETTINGS);
        startActivity(intent);
    }

    // Método para realizar una llamada
    private void makeCall() {
        Intent intent = new Intent(Intent.ACTION_CALL);
        intent.setData(Uri.parse("tel:123"));

        // Verificar si el permiso de llamada está concedido
        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.CALL_PHONE) != PackageManager.PERMISSION_GRANTED) {
            // Si el permiso no está concedido, solicitarlo
            ActivityCompat.requestPermissions(this, new String[]{Manifest.permission.CALL_PHONE}, 1);
            return;
        }

        // Iniciar la llamada
        startActivity(intent);
    }

    // Método para abrir el navegador web
    private void openBrowser() {
        Intent browserIntent = new Intent(Intent.ACTION_VIEW, Uri.parse("https://www.google.com"));
        startActivity(browserIntent);
    }
}
