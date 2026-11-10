package org.iot.project.data.repository;

import androidx.annotation.NonNull;

import org.iot.project.core.ResultCallback;
import org.iot.project.models.TaxistaAdministrado;

import java.util.List;

/** Administración de taxistas del sistema web, autorizada por el backend. */
public interface TaxistasAdminRepository {
    void listar(@NonNull ResultCallback<List<TaxistaAdministrado>> callback);

    void cambiarHabilitacion(@NonNull String taxistaId, boolean habilitado,
                            @NonNull ResultCallback<TaxistaAdministrado> callback);

    void cambiarCuenta(@NonNull String taxistaId, boolean activa,
                       @NonNull ResultCallback<TaxistaAdministrado> callback);
}
