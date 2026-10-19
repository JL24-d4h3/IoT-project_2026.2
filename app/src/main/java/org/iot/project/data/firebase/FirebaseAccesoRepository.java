package org.iot.project.data.firebase;

import androidx.annotation.NonNull;

import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseAuthException;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.firestore.DocumentReference;
import com.google.firebase.firestore.FieldValue;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.Source;

import org.iot.project.core.ResultCallback;
import org.iot.project.data.repository.AccesoRepository;
import org.iot.project.models.Cuenta;
import org.iot.project.models.Role;
import org.iot.project.models.User;

import java.util.HashMap;
import java.util.Locale;
import java.util.Map;

/** Credenciales en Auth y perfil/rol en usuarios/{uid}; nunca consulta mocks. */
public final class FirebaseAccesoRepository implements AccesoRepository {

    // Los callbacks de una operacion anterior no pueden restaurar otra sesion.
    private int operacion;
    private boolean autenticando;

    @Override
    public void iniciarSesion(@NonNull String email, @NonNull String contrasena,
                              @NonNull ResultCallback<Cuenta> callback) {
        String correo = normalizar(email);
        if (correo.isEmpty() || contrasena.isEmpty()) {
            callback.onError("Escribe tu correo y contraseña para continuar.");
            return;
        }
        int solicitud = comenzar(callback);
        if (solicitud < 0) {
            return;
        }
        auth().setLanguageCode("es");
        auth().signInWithEmailAndPassword(correo, contrasena)
                .addOnSuccessListener(resultado -> {
                    FirebaseUser usuario = resultado.getUser();
                    if (comprobar(solicitud, usuario, callback)) {
                        cargarPerfil(solicitud, usuario, callback);
                    }
                })
                .addOnFailureListener(error -> fallar(solicitud, error, callback));
    }

    @Override
    public void registrar(@NonNull User borrador, @NonNull String contrasena,
                          @NonNull ResultCallback<Cuenta> callback) {
        final Map<String, Object> perfil;
        String correo = normalizar(borrador.getEmail());
        try {
            if (correo.isEmpty() || contrasena.length() < 8) {
                throw new IllegalArgumentException("Completa el correo y una contraseña de al menos ocho caracteres.");
            }
            perfil = PerfilFirebase.nuevoCliente(borrador, correo);
        } catch (IllegalArgumentException error) {
            callback.onError(FirebaseErrores.mensaje(error));
            return;
        }
        int solicitud = comenzar(callback);
        if (solicitud < 0) {
            return;
        }
        auth().setLanguageCode("es");
        auth().createUserWithEmailAndPassword(correo, contrasena)
                .addOnSuccessListener(resultado -> {
                    FirebaseUser usuario = resultado.getUser();
                    if (!comprobar(solicitud, usuario, callback)) {
                        return;
                    }
                    perfil.put("email", usuario.getEmail());
                    perfil.put("creadoEn", FieldValue.serverTimestamp());
                    perfil.put("actualizadoEn", FieldValue.serverTimestamp());
                    db().collection("usuarios").document(usuario.getUid()).set(perfil)
                            .addOnSuccessListener(ignorado -> {
                                if (!comprobar(solicitud, usuario, callback)) {
                                    return;
                                }
                                Cuenta cuenta = PerfilFirebase.cuenta(usuario.getUid(), usuario.getEmail(), perfil);
                                // La pantalla confirma el alta y vuelve al formulario de acceso.
                                autenticando = false;
                                cerrarSesion();
                                callback.onExito(cuenta);
                            })
                            .addOnFailureListener(error -> {
                                if (!comprobar(solicitud, usuario, callback)) {
                                    return;
                                }
                                autenticando = false;
                                cerrarSesion();
                                callback.onError("La cuenta se creó, pero no pudimos guardar su perfil. "
                                        + "Intenta iniciar sesión para completar la configuración. "
                                        + FirebaseErrores.mensaje(error));
                            });
                })
                .addOnFailureListener(error -> fallar(solicitud, error, callback));
    }

    @Override
    public void restaurarSesion(@NonNull ResultCallback<Cuenta> callback) {
        FirebaseUser usuario = auth().getCurrentUser();
        if (usuario == null) {
            callback.onError("Inicia sesión para continuar.");
            return;
        }
        int solicitud = comenzar(callback);
        if (solicitud < 0) {
            return;
        }
        // reload detecta cuentas deshabilitadas o eliminadas en Authentication.
        usuario.reload().addOnSuccessListener(ignorado -> {
            if (comprobar(solicitud, usuario, callback)) {
                cargarPerfil(solicitud, usuario, callback);
            }
        }).addOnFailureListener(error -> fallar(solicitud, error, callback));
    }

    @Override
    public void recuperarContrasena(@NonNull String email,
                                   @NonNull ResultCallback<String> callback) {
        String correo = normalizar(email);
        if (correo.isEmpty()) {
            callback.onError("Escribe tu correo.");
            return;
        }
        auth().setLanguageCode("es");
        auth().sendPasswordResetEmail(correo)
                .addOnSuccessListener(ignorado -> callback.onExito(correo))
                .addOnFailureListener(error -> {
                    // Mismo resultado cuando la cuenta no existe: evita enumerar correos.
                    if (error instanceof FirebaseAuthException
                            && "ERROR_USER_NOT_FOUND".equals(((FirebaseAuthException) error).getErrorCode())) {
                        callback.onExito(correo);
                    } else {
                        callback.onError(FirebaseErrores.mensaje(error));
                    }
                });
    }

    @Override
    public void cerrarSesion() {
        operacion++;
        auth().signOut();
    }

    private void cargarPerfil(int solicitud, FirebaseUser usuario, ResultCallback<Cuenta> callback) {
        DocumentReference referencia = db().collection("usuarios").document(usuario.getUid());
        referencia.get(Source.SERVER).addOnSuccessListener(documento -> {
            if (!comprobar(solicitud, usuario, callback)) {
                return;
            }
            if (documento.exists()) {
                entregarPerfil(solicitud, usuario, documento.getData(), callback);
                return;
            }
            // Usuarios creados previamente en Auth estrenan perfil CLIENTE.
            // Los privilegios se provisionan en consola/backend, nunca por correo en la app.
            Map<String, Object> inicial = perfilInicial(usuario);
            db().runTransaction(transaccion -> {
                com.google.firebase.firestore.DocumentSnapshot actual = transaccion.get(referencia);
                if (actual.exists()) {
                    return actual.getData();
                }
                transaccion.set(referencia, inicial);
                return inicial;
            }).addOnSuccessListener(datos -> entregarPerfil(solicitud, usuario, datos, callback))
                    .addOnFailureListener(error -> fallar(solicitud, error, callback));
        }).addOnFailureListener(error -> fallar(solicitud, error, callback));
    }

    private void entregarPerfil(int solicitud, FirebaseUser usuario, Map<String, Object> datos,
                               ResultCallback<Cuenta> callback) {
        if (!comprobar(solicitud, usuario, callback)) {
            return;
        }
        final Cuenta cuenta;
        try {
            cuenta = PerfilFirebase.cuenta(usuario.getUid(), usuario.getEmail(), datos);
        } catch (IllegalArgumentException error) {
            fallar(solicitud, error, callback);
            return;
        }
        autenticando = false;
        callback.onExito(cuenta);
    }

    private void fallar(int solicitud, Exception error, ResultCallback<Cuenta> callback) {
        if (solicitud != operacion) {
            cancelar(callback);
            return;
        }
        autenticando = false;
        cerrarSesion();
        callback.onError(FirebaseErrores.mensaje(error));
    }

    private int comenzar(ResultCallback<Cuenta> callback) {
        if (autenticando) {
            callback.onError("Espera a que termine la solicitud de acceso anterior.");
            return -1;
        }
        autenticando = true;
        return ++operacion;
    }

    private boolean comprobar(int solicitud, FirebaseUser usuario, ResultCallback<Cuenta> callback) {
        if (solicitud != operacion) {
            cancelar(callback);
            return false;
        }
        FirebaseUser actual = auth().getCurrentUser();
        if (usuario == null || actual == null || !actual.getUid().equals(usuario.getUid())) {
            fallar(solicitud, new IllegalArgumentException("La sesión cambió. Inicia sesión de nuevo."), callback);
            return false;
        }
        return true;
    }

    private void cancelar(ResultCallback<Cuenta> callback) {
        autenticando = false;
        auth().signOut();
        callback.onError("La solicitud fue cancelada. Inténtalo de nuevo.");
    }

    private static Map<String, Object> perfilInicial(FirebaseUser usuario) {
        Map<String, Object> datos = new HashMap<>();
        String nombre = usuario.getDisplayName();
        String email = usuario.getEmail() == null ? "" : usuario.getEmail();
        if (nombre == null || nombre.trim().isEmpty()) {
            nombre = email.contains("@") ? email.substring(0, email.indexOf('@')) : "Usuario";
        }
        datos.put("nombres", nombre.trim());
        datos.put("apellidos", "");
        datos.put("email", email);
        datos.put("telefono", "");
        datos.put("direccion", "");
        datos.put("fotoUrl", "");
        datos.put("rol", Role.CLIENTE.name());
        datos.put("activo", true);
        datos.put("creadoEn", FieldValue.serverTimestamp());
        datos.put("actualizadoEn", FieldValue.serverTimestamp());
        return datos;
    }

    private static String normalizar(String email) {
        return email == null ? "" : email.trim().toLowerCase(Locale.ROOT);
    }

    private static FirebaseAuth auth() {
        return FirebaseAuth.getInstance();
    }

    private static FirebaseFirestore db() {
        return FirebaseFirestore.getInstance();
    }
}
