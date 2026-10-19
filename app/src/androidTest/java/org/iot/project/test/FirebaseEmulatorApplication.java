package org.iot.project.test;

import android.app.Application;
import android.content.Context;

import com.google.firebase.FirebaseApp;
import com.google.firebase.FirebaseOptions;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.firestore.FirebaseFirestore;

/** Solo existe en el APK de pruebas; ningun SDK recibe opciones de produccion. */
public final class FirebaseEmulatorApplication extends Application {
    public static final String PROJECT_ID = "demo-iot-hoteles";
    public static final String HOST = "10.0.2.2";
    public static final int AUTH_PORT = 19099;
    public static final int FIRESTORE_PORT = 18080;
    public static final String API_KEY = "AIza00000000000000000000000000000000000";

    @Override
    protected void attachBaseContext(Context base) {
        super.attachBaseContext(base);
        FirebaseApp existente = null;
        for (FirebaseApp app : FirebaseApp.getApps(this)) {
            if (FirebaseApp.DEFAULT_APP_NAME.equals(app.getName())) existente = app;
        }
        if (existente != null) {
            throw new IllegalStateException("Firebase DEFAULT se inicializo antes del harness seguro.");
        }
        FirebaseOptions opciones = new FirebaseOptions.Builder()
                .setProjectId(PROJECT_ID)
                .setApiKey(API_KEY)
                .setApplicationId("1:1234567890:android:1234567890abcdef")
                .build();
        FirebaseApp.initializeApp(this, opciones);
        exigirProyectoDemo();
    }

    @Override
    public void onCreate() {
        super.onCreate();
        exigirProyectoDemo();
        // DEFAULT ya es demo antes de los proveedores. Los SDK necesitan que
        // Application termine de adjuntarse para acceder a SharedPreferences.
        // Se redirigen antes de crear Activity, SessionManager o repositorios.
        FirebaseAuth.getInstance().useEmulator(HOST, AUTH_PORT);
        FirebaseFirestore.getInstance().useEmulator(HOST, FIRESTORE_PORT);
    }

    public static void exigirProyectoDemo() {
        FirebaseOptions opciones = FirebaseApp.getInstance().getOptions();
        if (!PROJECT_ID.equals(opciones.getProjectId()) || !API_KEY.equals(opciones.getApiKey())) {
            throw new IllegalStateException("Prueba cancelada: Firebase DEFAULT no es el proyecto demo.");
        }
    }
}
