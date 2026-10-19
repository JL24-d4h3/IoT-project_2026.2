package org.iot.project.data.firebase;

import com.google.firebase.FirebaseNetworkException;
import com.google.firebase.FirebaseTooManyRequestsException;
import com.google.firebase.auth.FirebaseAuthException;
import com.google.firebase.firestore.FirebaseFirestoreException;

/** Mensajes sin credenciales, tokens ni detalles internos del SDK. */
public final class FirebaseErrores {

    private FirebaseErrores() {
    }

    public static String mensaje(Exception error) {
        if (error instanceof FirebaseNetworkException) {
            return "No pudimos conectar. Revisa tu conexión e inténtalo de nuevo.";
        }
        if (error instanceof FirebaseTooManyRequestsException) {
            return "Hubo demasiados intentos. Espera un momento e inténtalo de nuevo.";
        }
        if (error instanceof FirebaseAuthException) {
            switch (((FirebaseAuthException) error).getErrorCode()) {
                case "ERROR_USER_DISABLED":
                    return "Tu cuenta está deshabilitada. Contacta al administrador.";
                case "ERROR_EMAIL_ALREADY_IN_USE":
                    return "Ya existe una cuenta con ese correo. Inicia sesión o recupera tu contraseña.";
                case "ERROR_WEAK_PASSWORD":
                    return "La contraseña no cumple la política de seguridad. Usa una contraseña más segura.";
                case "ERROR_INVALID_EMAIL":
                    return "Escribe un correo válido.";
                case "ERROR_OPERATION_NOT_ALLOWED":
                    return "El acceso con correo y contraseña no está habilitado. Contacta al administrador.";
                case "ERROR_INVALID_API_KEY":
                case "ERROR_APP_NOT_AUTHORIZED":
                case "ERROR_PROJECT_NOT_FOUND":
                    return "No pudimos conectar esta aplicación con Firebase. Contacta al administrador.";
                default:
                    return "No pudimos iniciar sesión. Revisa el correo y la contraseña e inténtalo de nuevo.";
            }
        }
        if (error instanceof FirebaseFirestoreException) {
            switch (((FirebaseFirestoreException) error).getCode()) {
                case PERMISSION_DENIED:
                    return "No pudimos acceder a tu perfil. Contacta al administrador para revisar la configuración.";
                case UNAVAILABLE:
                case DEADLINE_EXCEEDED:
                    return "No pudimos verificar tu perfil. Revisa tu conexión e inténtalo de nuevo.";
                case UNAUTHENTICATED:
                    return "Tu sesión venció. Inicia sesión de nuevo.";
                default:
                    return "No pudimos cargar tu perfil. Inténtalo de nuevo.";
            }
        }
        if (error instanceof IllegalArgumentException && error.getMessage() != null) {
            return error.getMessage();
        }
        return "No pudimos completar la operación. Inténtalo de nuevo.";
    }
}
