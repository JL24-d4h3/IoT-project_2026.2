package org.iot.project.data.mock;

import android.os.Handler;
import android.os.Looper;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import org.iot.project.core.MockConfig;
import org.iot.project.core.ResultCallback;

import java.util.Collections;
import java.util.List;

/**
 * Base de los repositorios simulados.
 *
 * <p>Existe por dos motivos, y ninguno es ahorrar lineas:
 *
 * <ol>
 *   <li><b>Latencia.</b> Un backend real nunca responde al instante. Si los
 *       datos llegaran de forma sincrona, los estados de carga de §50 nunca se
 *       verian y no habria forma de comprobar que la interfaz los resuelve
 *       bien. Aqui toda respuesta se retrasa.</li>
 *   <li><b>Hilo.</b> La respuesta se entrega siempre en el hilo principal, que
 *       es lo que LiveData exige para {@code setValue}. Los repositorios
 *       concretos no tienen que acordarse de esto.</li>
 * </ol>
 *
 * <p>{@link MockConfig} se consulta <em>en el momento de responder</em>, no al
 * lanzar la peticion: si el modo cambia mientras la peticion esta en vuelo, se
 * respeta el modo nuevo.
 */
public abstract class MockRepository {

    private static final Handler HILO_PRINCIPAL =
            new Handler(Looper.getMainLooper());

    /** Fuente de un dato. Se ejecuta en el hilo principal. */
    public interface Proveedor<T> {
        @Nullable
        T obtener();
    }

    /**
     * Entrega una lista.
     *
     * <p>En modo {@link MockConfig.Modo#VACIO} devuelve una lista vacia, que es
     * lo que la pantalla necesita para mostrar su estado vacio.
     */
    protected <T> void entregarLista(@NonNull ResultCallback<List<T>> callback,
                                     @NonNull Proveedor<List<T>> proveedor) {
        programar(() -> {
            if (hayFallo()) {
                callback.onError(MockConfig.mensajeDeError());
                return;
            }
            if (MockConfig.getModo() == MockConfig.Modo.VACIO) {
                callback.onExito(Collections.emptyList());
                return;
            }
            List<T> resultado = proveedor.obtener();
            callback.onExito(resultado != null ? resultado : Collections.<T>emptyList());
        });
    }

    /**
     * Entrega un unico dato.
     *
     * <p>Un resultado nulo se informa como error y no como exito: una pantalla
     * de detalle sin datos no debe quedar en blanco, debe mostrar su estado de
     * error con la opcion de reintentar (§50).
     */
    protected <T> void entregarDato(@NonNull ResultCallback<T> callback,
                                    @NonNull Proveedor<T> proveedor,
                                    @NonNull String mensajeSiNoExiste) {
        programar(() -> {
            if (hayFallo()) {
                callback.onError(MockConfig.mensajeDeError());
                return;
            }
            T resultado = proveedor.obtener();
            if (resultado == null) {
                callback.onError(mensajeSiNoExiste);
                return;
            }
            callback.onExito(resultado);
        });
    }

    /**
     * Ejecuta una operacion que modifica datos o que comprueba una regla de
     * acceso.
     *
     * <p>El modo {@link MockConfig.Modo#VACIO} no la afecta: ese modo describe
     * consultas sin resultados, no un sistema que no puede operar. Da igual que
     * la operacion lea o escriba: si entrar a la aplicacion dependiera de ese
     * modo, con la lista vacia nadie podria ni iniciar sesion. Solo
     * {@link MockConfig.Modo#ERROR} y {@link MockConfig.Modo#SIN_CONEXION} la
     * hacen fallar.
     */
    protected <T> void ejecutar(@NonNull ResultCallback<T> callback,
                                @NonNull Proveedor<T> operacion) {
        programar(() -> {
            if (hayFallo()) {
                callback.onError(MockConfig.mensajeDeError());
                return;
            }
            try {
                T resultado = operacion.obtener();
                callback.onExito(resultado);
            } catch (IllegalStateException | IllegalArgumentException e) {
                // La regla de negocio se redacta para el usuario; el mensaje de
                // la excepcion es el que la explica (RF-032, RC-013, RF-111).
                callback.onError(e.getMessage() != null
                        ? e.getMessage()
                        : "No pudimos completar la operación.");
            }
        });
    }

    private boolean hayFallo() {
        MockConfig.Modo modo = MockConfig.getModo();
        return modo == MockConfig.Modo.ERROR || modo == MockConfig.Modo.SIN_CONEXION;
    }

    private void programar(@NonNull Runnable tarea) {
        HILO_PRINCIPAL.postDelayed(tarea, MockConfig.getLatenciaMs());
    }
}
