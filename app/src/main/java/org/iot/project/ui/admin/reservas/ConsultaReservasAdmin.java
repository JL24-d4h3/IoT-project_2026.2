package org.iot.project.ui.admin.reservas;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import org.iot.project.models.ReservaDeHotel;
import org.iot.project.models.User;

import java.text.Normalizer;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Búsqueda de las reservas del hotel por nombre del cliente, DNI (o cualquier
 * documento) y código de reserva.
 *
 * <p>Filtra en memoria sobre lo que la lista ya trae, sin consultas nuevas.
 * Ignora mayúsculas y tildes, y cada palabra escrita debe aparecer en algún
 * dato de la reserva: "perez juan" encuentra a "Juan Pérez".
 */
final class ConsultaReservasAdmin {

    private ConsultaReservasAdmin() {
    }

    @NonNull
    static List<ReservaDeHotel> aplicar(@NonNull List<ReservaDeHotel> reservas,
                                        @Nullable String busqueda) {
        String consulta = normalizar(busqueda);
        if (consulta.isEmpty()) return reservas;
        String[] palabras = consulta.split(" ");
        List<ReservaDeHotel> visibles = new ArrayList<>();
        for (ReservaDeHotel detalle : reservas) {
            String datos = datosBuscables(detalle);
            boolean todas = true;
            for (String palabra : palabras) {
                if (!datos.contains(palabra)) {
                    todas = false;
                    break;
                }
            }
            if (todas) visibles.add(detalle);
        }
        return visibles;
    }

    private static String datosBuscables(ReservaDeHotel detalle) {
        User cliente = detalle.getCliente();
        StringBuilder datos = new StringBuilder();
        if (cliente != null) {
            datos.append(normalizar(cliente.getNombreCompleto())).append(' ')
                    .append(normalizar(cliente.getNumeroDocumento())).append(' ');
        }
        datos.append(normalizar(detalle.getReserva().getCodigo())).append(' ')
                .append(normalizar(detalle.getReserva().getCodigoCorto()));
        return datos.toString();
    }

    static String normalizar(@Nullable String texto) {
        if (texto == null) return "";
        return Normalizer.normalize(texto, Normalizer.Form.NFD)
                .replaceAll("\\p{M}+", "").toLowerCase(Locale.ROOT)
                .trim().replaceAll("\\s+", " ");
    }
}
