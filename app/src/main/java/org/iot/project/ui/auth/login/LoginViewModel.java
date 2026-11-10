package org.iot.project.ui.auth.login;

import androidx.annotation.NonNull;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;
import androidx.lifecycle.SavedStateHandle;
import androidx.lifecycle.ViewModel;

import org.iot.project.core.ResultCallback;
import org.iot.project.core.ServiceLocator;
import org.iot.project.core.UiState;
import org.iot.project.data.repository.AccesoRepository;
import org.iot.project.models.Cuenta;

/** Un solo acceso en curso; contraseñas y tokens nunca forman parte del estado guardado. */
public class LoginViewModel extends ViewModel {
    interface ProveedorTaxista {
        void iniciarSesion(String correo, String contrasena, ResultCallback<Cuenta> callback);
        void cancelar();
    }

    private final MutableLiveData<UiState<Cuenta>> acceso = new MutableLiveData<>();
    private final SavedStateHandle estado;
    private final AccesoRepository firebase;
    private final ProveedorTaxista taxistas;
    private boolean esperandoGoogle;
    private long operacion;

    public LoginViewModel(@NonNull SavedStateHandle estado) {
        this(estado, ServiceLocator.acceso(), new ProveedorTaxista() {
            @Override public void iniciarSesion(String correo, String contrasena, ResultCallback<Cuenta> callback) {
                ServiceLocator.accesoTaxistaRest().iniciarSesion(correo, contrasena, callback);
            }
            @Override public void cancelar() { ServiceLocator.accesoTaxistaRest().cancelar(); }
        });
    }

    LoginViewModel(SavedStateHandle estado, AccesoRepository firebase, ProveedorTaxista taxistas) {
        this.estado = estado;
        this.firebase = firebase;
        this.taxistas = taxistas;
    }

    public LiveData<Boolean> getModoTaxista() { return estado.getLiveData("accesoTaxistaRest", false); }
    public boolean esModoTaxista() { return Boolean.TRUE.equals(estado.get("accesoTaxistaRest")); }

    public void seleccionarModoTaxista(boolean taxista) {
        if (!estaCargando() && taxista != esModoTaxista()) {
            limpiarAcceso();
            estado.set("accesoTaxistaRest", taxista);
        }
    }

    public LiveData<UiState<Cuenta>> getAcceso() { return acceso; }
    public boolean estaCargando() {
        UiState<Cuenta> actual = acceso.getValue();
        return actual != null && actual.isLoading();
    }

    public void entrar(@NonNull String correo, @NonNull String contrasena) {
        if (estaCargando()) return;
        esperandoGoogle = false;
        long solicitud = ++operacion;
        acceso.setValue(UiState.loading());
        if (esModoTaxista()) taxistas.iniciarSesion(correo, contrasena, resultado(solicitud));
        else firebase.iniciarSesion(correo, contrasena, resultado(solicitud));
    }

    /** Se llama únicamente después de pulsar el botón Google. No inicia peticiones en segundo plano. */
    public boolean comenzarGoogle() {
        if (esModoTaxista() || estaCargando()) return false;
        operacion++;
        esperandoGoogle = true;
        acceso.setValue(UiState.loading());
        return true;
    }

    public boolean estaEsperandoGoogle() { return esperandoGoogle; }

    public void completarGoogle(@NonNull String idToken) {
        if (!esperandoGoogle || !estaCargando() || esModoTaxista()) return;
        esperandoGoogle = false;
        firebase.iniciarSesionGoogle(idToken, resultado(operacion));
    }

    /** Cancelar el selector no cancela una autenticación que Firebase ya esté comprobando. */
    public void cancelarGoogle() {
        if (!esperandoGoogle) return;
        limpiarAcceso();
    }

    public void fallarGoogle(@NonNull String mensaje) {
        if (!esperandoGoogle) return;
        esperandoGoogle = false;
        operacion++;
        acceso.setValue(UiState.error(mensaje));
    }

    private ResultCallback<Cuenta> resultado(long solicitud) {
        return new ResultCallback<Cuenta>() {
            @Override public void onExito(@NonNull Cuenta cuenta) {
                if (solicitud == operacion) acceso.setValue(UiState.success(cuenta));
            }
            @Override public void onError(@NonNull String mensaje) {
                if (solicitud == operacion) acceso.setValue(UiState.error(mensaje));
            }
        };
    }

    public void limpiarAcceso() {
        operacion++;
        esperandoGoogle = false;
        acceso.setValue(null);
    }

    @Override protected void onCleared() {
        operacion++;
        esperandoGoogle = false;
        taxistas.cancelar();
        super.onCleared();
    }
}
