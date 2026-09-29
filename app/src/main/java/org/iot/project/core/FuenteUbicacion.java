package org.iot.project.core;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import org.iot.project.models.Driver;
import org.iot.project.models.TaxiService;
import org.iot.project.models.Ubicacion;

/**
 * De donde salen las coordenadas del conductor (RF-098).
 *
 * <p>Hoy la unica implementacion es simulada: el conductor se acerca al punto
 * de recojo a saltos, como ya hacia el repositorio. La interfaz existe para que
 * el dia que se pida ubicacion real se escriba <em>otra</em> implementacion y no
 * haya que tocar ninguna pantalla.
 *
 * <p>Devuelve el dato de forma sincrona porque la ultima posicion conocida es
 * un dato que ya se tiene: una implementacion con GPS pediria actualizaciones y
 * guardaria la ultima, pero quien pregunta solo quiere saber donde esta.
 */
public interface FuenteUbicacion {

    /**
     * Donde esta el conductor ahora mismo.
     *
     * @param conductor el conductor, que siempre existe
     * @param enCurso   el servicio que tiene en curso, o {@code null} si esta libre
     */
    @NonNull
    Ubicacion posicion(@NonNull Driver conductor, @Nullable TaxiService enCurso);
}
