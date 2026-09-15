package org.iot.project.core;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

/**
 * Estado de una pantalla: carga, contenido, vacio o error.
 *
 * <p>Existe para que ningun Fragment tenga que improvisar sus propios flags
 * booleanos, y para que los cuatro estados que exige §50 (loading, empty,
 * error, success) se puedan renderizar siempre con los mismos componentes.
 *
 * @param <T> tipo del dato que produce la pantalla cuando hay contenido.
 */
public final class UiState<T> {

    public enum Status {
        LOADING,
        SUCCESS,
        EMPTY,
        ERROR
    }

    private final Status status;
    private final T data;
    private final String message;

    private UiState(Status status, @Nullable T data, @Nullable String message) {
        this.status = status;
        this.data = data;
        this.message = message;
    }

    public static <T> UiState<T> loading() {
        return new UiState<>(Status.LOADING, null, null);
    }

    public static <T> UiState<T> success(@NonNull T data) {
        return new UiState<>(Status.SUCCESS, data, null);
    }

    public static <T> UiState<T> empty() {
        return new UiState<>(Status.EMPTY, null, null);
    }

    public static <T> UiState<T> error(@NonNull String message) {
        return new UiState<>(Status.ERROR, null, message);
    }

    @NonNull
    public Status getStatus() {
        return status;
    }

    @Nullable
    public T getData() {
        return data;
    }

    /**
     * Solo tiene sentido cuando el estado es ERROR. El texto ya viene
     * orientado al usuario, nunca es un mensaje tecnico (§53).
     */
    @Nullable
    public String getMessage() {
        return message;
    }

    public boolean isLoading() {
        return status == Status.LOADING;
    }

    public boolean isSuccess() {
        return status == Status.SUCCESS;
    }

    public boolean isEmpty() {
        return status == Status.EMPTY;
    }

    public boolean isError() {
        return status == Status.ERROR;
    }

    /**
     * Devuelve el dato asumiendo que la pantalla ya comprobo que hay contenido.
     *
     * @throws IllegalStateException si se llama en un estado sin datos.
     */
    @NonNull
    public T requireData() {
        if (data == null) {
            throw new IllegalStateException("UiState en estado " + status + " no tiene datos");
        }
        return data;
    }
}
