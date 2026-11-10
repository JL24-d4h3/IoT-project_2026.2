package com.taxi.gestion.domain.enums;

import java.util.Collections;
import java.util.EnumMap;
import java.util.EnumSet;
import java.util.Map;
import java.util.Set;

/**
 * Ciclo de vida de un servicio de taxi.
 *
 * <pre>
 * SOLICITADO -> ASIGNADO -> EN_CAMINO -> EN_TRASLADO -> FINALIZADO
 * </pre>
 *
 * El grafo de transiciones vive aqui para que la regla sea unica y no se
 * duplique entre el servicio y los controladores.
 */
public enum EstadoServicio {

    SOLICITADO("Solicitado"),
    ASIGNADO("Asignado"),
    EN_CAMINO("En camino al punto de recojo"),
    EN_TRASLADO("En traslado"),
    FINALIZADO("Finalizado");

    private static final Map<EstadoServicio, Set<EstadoServicio>> TRANSICIONES =
            new EnumMap<>(EstadoServicio.class);

    static {
        TRANSICIONES.put(SOLICITADO, EnumSet.of(ASIGNADO));
        TRANSICIONES.put(ASIGNADO, EnumSet.of(EN_CAMINO));
        TRANSICIONES.put(EN_CAMINO, EnumSet.of(EN_TRASLADO));
        TRANSICIONES.put(EN_TRASLADO, EnumSet.of(FINALIZADO));
        TRANSICIONES.put(FINALIZADO, EnumSet.noneOf(EstadoServicio.class));
    }

    private final String etiqueta;

    EstadoServicio(String etiqueta) {
        this.etiqueta = etiqueta;
    }

    public String getEtiqueta() {
        return etiqueta;
    }

    /** Estados a los que se puede pasar desde este. */
    public Set<EstadoServicio> siguientesPosibles() {
        return Collections.unmodifiableSet(
                TRANSICIONES.getOrDefault(this, EnumSet.noneOf(EstadoServicio.class)));
    }

    /** true si la transicion this -> destino respeta el flujo definido. */
    public boolean permiteTransicionA(EstadoServicio destino) {
        return destino != null && siguientesPosibles().contains(destino);
    }

    public boolean esTerminal() {
        return siguientesPosibles().isEmpty();
    }
}
