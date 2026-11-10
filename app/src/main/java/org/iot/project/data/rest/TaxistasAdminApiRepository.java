package org.iot.project.data.rest;

import androidx.annotation.NonNull;

import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.gson.JsonObject;

import org.iot.project.core.ResultCallback;
import org.iot.project.core.SessionManager;
import org.iot.project.data.repository.TaxistasAdminRepository;
import org.iot.project.models.Cuenta;
import org.iot.project.models.Driver;
import org.iot.project.models.Role;
import org.iot.project.models.TaxistaAdministrado;

import java.util.List;

/** Gestión REST de taxistas con el ID token Firebase de la sesión de superadministrador. */
public final class TaxistasAdminApiRepository implements TaxistasAdminRepository {
    private static final String RUTA = "/api/admin/taxistas";
    private static final String SESION_CAMBIO = "Inicia sesión como superadministrador para gestionar los taxistas.";
    private static final String TOKEN_NO_DISPONIBLE = "No pudimos verificar tu sesión de superadministrador. Revisa la conexión e inténtalo de nuevo.";

    @Override
    public void listar(@NonNull ResultCallback<List<TaxistaAdministrado>> callback) {
        conAcceso(token -> {
            List<TaxistaAdministrado> datos = TaxistasAdminJson.listar(
                    TaxistasApi.getInstancia().lista(RUTA, token));
            for (TaxistaAdministrado dato : datos) resolverFotos(dato);
            return datos;
        }, callback);
    }

    @Override
    public void cambiarHabilitacion(@NonNull String id, boolean habilitado,
                                    @NonNull ResultCallback<TaxistaAdministrado> callback) {
        if (!idValido(id, callback)) return;
        conAcceso(token -> {
            JsonObject respuesta = TaxistasApi.getInstancia().objeto("PUT", RUTA + "/" + id + "/habilitacion",
                    TaxistasAdminJson.cuerpoHabilitacion(habilitado), token);
            return resolverFotos(TaxistasAdminJson.confirmarHabilitacion(respuesta, id, habilitado));
        }, callback);
    }

    @Override
    public void cambiarCuenta(@NonNull String id, boolean activa,
                              @NonNull ResultCallback<TaxistaAdministrado> callback) {
        if (!idValido(id, callback)) return;
        conAcceso(token -> {
            JsonObject respuesta = TaxistasApi.getInstancia().objeto("PUT", RUTA + "/" + id + "/cuenta",
                    TaxistasAdminJson.cuerpoCuenta(activa), token);
            return resolverFotos(TaxistasAdminJson.confirmarCuenta(respuesta, id, activa));
        }, callback);
    }

    private static boolean idValido(String id, ResultCallback<?> callback) {
        try {
            TaxistasAdminJson.validarId(id);
            return true;
        } catch (IllegalArgumentException error) {
            callback.onError("Selecciona un taxista con un identificador válido del sistema web.");
            return false;
        }
    }

    private <T> void conAcceso(Operacion<T> trabajo, ResultCallback<T> callback) {
        final Acceso acceso;
        try {
            FirebaseUser usuario = FirebaseAuth.getInstance().getCurrentUser();
            acceso = new Acceso(usuario);
        } catch (RuntimeException error) {
            callback.onError(SESION_CAMBIO);
            return;
        }
        if (!comprobar(acceso, callback)) return;
        try {
            acceso.usuario.getIdToken(false).addOnCompleteListener(resultado -> {
                if (!comprobar(acceso, callback)) return;
                if (!resultado.isSuccessful() || resultado.getResult() == null) {
                    callback.onError(TOKEN_NO_DISPONIBLE);
                    return;
                }
                String token = resultado.getResult().getToken();
                if (token == null || token.isBlank() || token.length() > 16384
                        || token.contains("\r") || token.contains("\n")) {
                    callback.onError(TOKEN_NO_DISPONIBLE);
                    return;
                }
                TaxistasApi.ejecutar(() -> {
                    exigir(acceso);
                    T dato = trabajo.ejecutar(token);
                    exigir(acceso);
                    return dato;
                }, new ResultCallback<T>() {
                    @Override public void onExito(@NonNull T dato) {
                        if (comprobar(acceso, callback)) callback.onExito(dato);
                    }
                    @Override public void onError(@NonNull String mensaje) {
                        if (comprobar(acceso, callback)) callback.onError(mensaje);
                    }
                });
            });
        } catch (RuntimeException error) {
            callback.onError(vigente(acceso) ? TOKEN_NO_DISPONIBLE : SESION_CAMBIO);
        }
    }

    private static boolean vigente(Acceso acceso) {
        FirebaseUser usuario = FirebaseAuth.getInstance().getCurrentUser();
        Cuenta cuenta = SessionManager.getCuenta();
        return acceso.uid != null && usuario != null && cuenta != null
                && acceso.uid.equals(usuario.getUid()) && acceso.uid.equals(cuenta.getIdentidadId())
                && cuenta.getRol() == Role.SUPERADMIN;
    }

    private static void exigir(Acceso acceso) {
        if (!vigente(acceso)) throw new IllegalArgumentException(SESION_CAMBIO);
    }

    private static boolean comprobar(Acceso acceso, ResultCallback<?> callback) {
        if (vigente(acceso)) return true;
        callback.onError(SESION_CAMBIO);
        return false;
    }

    private static TaxistaAdministrado resolverFotos(TaxistaAdministrado dato) {
        Driver perfil = dato.getPerfil();
        perfil.withFoto(TaxistasApi.resolverUrl(perfil.getFotoUrl()));
        if (perfil.getVehiculo() != null) {
            perfil.getVehiculo().withFoto(TaxistasApi.resolverUrl(perfil.getVehiculo().getFotoUrl()));
        }
        return dato;
    }

    private interface Operacion<T> { T ejecutar(String token) throws Exception; }

    private static final class Acceso {
        final FirebaseUser usuario;
        final String uid;
        Acceso(FirebaseUser usuario) {
            this.usuario = usuario;
            this.uid = usuario == null ? null : usuario.getUid();
        }
    }
}
