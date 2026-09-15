package org.iot.project.models;

import androidx.annotation.NonNull;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Los cobros adicionales de una estadia, con la estadia ya resuelta (RF-054).
 *
 * <p>RF-054 pide poder asociar cada cobro con la reserva correspondiente. Esta
 * clase es esa asociacion hecha dato: el cobro no se guarda con el codigo de su
 * reserva escrito al lado —dos cosas que pueden separarse— sino colgando del
 * cruce que ya resolvio el repositorio.
 *
 * <p>Agrupa y no envuelve un cobro suelto porque el administrador lee los
 * cobros por estadia: al cerrar una cuenta lo que pregunta es cuanto lleva
 * gastado de mas esa habitacion, y esa suma no se puede leer de una lista de
 * filas sueltas sin ir sumando.
 *
 * <p>Guarda la {@link ReservaDeHotel} entera, y no solo el nombre del cliente,
 * porque la tarjeta tiene que poder abrir el detalle de la reserva con un
 * identificador real y enseñar a que habitacion corresponde.
 */
public final class CargosDeReserva {

    @NonNull
    private final ReservaDeHotel estadia;

    /**
     * Los cobros, en el orden en que se registraron.
     *
     * <p>No se reordenan. El orden de registro es el unico orden cronologico
     * que existe —un {@link Charge} no guarda su fecha, y RF-052 no la pide— y
     * ademas es el que el administrador espera: el ultimo cobro es el que acaba
     * de hacer.
     */
    @NonNull
    private final List<Charge> cargos;

    /** La suma de los montos, calculada una vez y no en cada pintado. */
    private final double total;

    public CargosDeReserva(@NonNull ReservaDeHotel estadia, @NonNull List<Charge> cargos) {
        this.estadia = estadia;
        this.cargos = Collections.unmodifiableList(new ArrayList<>(cargos));

        double suma = 0d;
        for (Charge cargo : this.cargos) {
            suma += cargo.getMonto();
        }
        this.total = suma;
    }

    @NonNull
    public ReservaDeHotel getEstadia() {
        return estadia;
    }

    @NonNull
    public List<Charge> getCargos() {
        return cargos;
    }

    /** Lo que suman los cobros de esta estadia. */
    public double getTotal() {
        return total;
    }
}
