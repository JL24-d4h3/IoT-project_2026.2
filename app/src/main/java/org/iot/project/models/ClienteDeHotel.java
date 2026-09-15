package org.iot.project.models;

import androidx.annotation.NonNull;

import java.time.LocalDate;

/**
 * Un cliente que se ha alojado en el hotel, con lo que ha dejado en él.
 *
 * <p>Es el cruce de todas las reservas de una misma persona en un hotel,
 * resumido: cuántas veces se alojó, cuánto gastó y cuándo fue la última. Lo hace
 * el repositorio, que es quien tiene a mano las reservas y los usuarios a la
 * vez; sumarlo en la pantalla obligaría al Fragment a recorrer el almacén de
 * reservas entero por cada fila (reglas 33-35).
 *
 * <p>Solo cuenta reservas que llegaron a ocupar una habitación —activas o
 * finalizadas—. Una reserva cancelada nunca fue una estancia, y una confirmada
 * a futuro todavía no lo es: quien reserva para dentro de un mes no es todavía
 * un huésped del hotel, y esa reserva ya se consulta en la pantalla de reservas.
 * Por eso {@code numEstancias} nunca es cero y {@code ultimaEstancia} nunca
 * falta: quien aparece en esta lista, se alojó.
 */
public final class ClienteDeHotel {

    @NonNull
    private final User cliente;

    /** Cuántas veces se ha alojado en este hotel. Siempre uno o más. */
    private final int numEstancias;

    /** Lo que suman esas estancias, con servicios y cargos incluidos. */
    private final double totalGastado;

    /** La fecha de entrada de la más reciente. */
    @NonNull
    private final LocalDate ultimaEstancia;

    /** Si ahora mismo tiene una habitación ocupada. */
    private final boolean enCurso;

    public ClienteDeHotel(@NonNull User cliente, int numEstancias, double totalGastado,
                          @NonNull LocalDate ultimaEstancia, boolean enCurso) {
        this.cliente = cliente;
        this.numEstancias = numEstancias;
        this.totalGastado = totalGastado;
        this.ultimaEstancia = ultimaEstancia;
        this.enCurso = enCurso;
    }

    @NonNull
    public User getCliente() {
        return cliente;
    }

    public int getNumEstancias() {
        return numEstancias;
    }

    public double getTotalGastado() {
        return totalGastado;
    }

    @NonNull
    public LocalDate getUltimaEstancia() {
        return ultimaEstancia;
    }

    public boolean estaEnCurso() {
        return enCurso;
    }
}
