package org.iot.project.models;

import androidx.annotation.NonNull;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Una pagina de resultados.
 *
 * <p>Existe porque una lista sola no puede decir la verdad sobre si quedan mas
 * resultados. Deducirlo de que la pagina venga llena falla justo en el unico
 * caso que importa: cuando el total es multiplo del tamano de pagina, la ultima
 * pagina llega completa y la pantalla ofreceria "ver mas" para no traer nada.
 * Quien sabe si hay mas es quien cuenta los resultados, es decir el repositorio.
 *
 * @param <T> tipo de los elementos de la pagina
 */
public final class Pagina<T> {

    private final List<T> items;
    private final int numero;
    private final boolean hayMas;

    public Pagina(@NonNull List<T> items, int numero, boolean hayMas) {
        // Copia y no envoltorio: quien construye la pagina suele seguir
        // trabajando sobre la lista completa, y una pagina no puede cambiar
        // despues de entregada.
        this.items = Collections.unmodifiableList(new ArrayList<>(items));
        this.numero = numero;
        this.hayMas = hayMas;
    }

    /** Pagina vacia: la primera de una busqueda sin resultados. */
    @NonNull
    public static <T> Pagina<T> vacia() {
        return new Pagina<>(Collections.emptyList(), 0, false);
    }

    @NonNull
    public List<T> getItems() {
        return items;
    }

    /** Numero de pagina, empezando en cero. */
    public int getNumero() {
        return numero;
    }

    public boolean isHayMas() {
        return hayMas;
    }

    public boolean isEmpty() {
        return items.isEmpty();
    }

    public int size() {
        return items.size();
    }
}
