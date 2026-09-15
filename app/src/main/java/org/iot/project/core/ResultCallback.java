package org.iot.project.core;

import androidx.annotation.NonNull;

/**
 * Respuesta asincrona de un repositorio.
 *
 * <p>El mensaje de error llega ya redactado para el usuario: nunca "HTTP 500"
 * ni el nombre de una excepcion (§53). Traducir el fallo a lenguaje humano es
 * responsabilidad de la capa de datos, no de la pantalla.
 *
 * @param <T> tipo del dato que devuelve la operacion
 */
public interface ResultCallback<T> {

    void onExito(@NonNull T datos);

    void onError(@NonNull String mensaje);
}
