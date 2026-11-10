package org.iot.project.ui.client.bookings;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import org.iot.project.models.Booking;
import org.iot.project.models.BookingStatus;

import java.text.Collator;
import java.text.Normalizer;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.function.Function;

/** Búsqueda y orden global sobre las reservas ya obtenidas del cliente. */
public final class ConsultaReservasCliente {
    public enum Orden { NOMBRE, FECHA_SALIDA }

    private ConsultaReservasCliente() { }

    @NonNull public static List<Booking> aplicar(@NonNull List<Booking> reservas,
            @NonNull String busqueda, @Nullable BookingStatus estado, @NonNull Orden orden,
            boolean ascendente, @NonNull Function<Booking, String> nombreHotel) {
        String consulta = normalizar(busqueda);
        List<Booking> visibles = new ArrayList<>();
        Map<Booking, String> nombres = new IdentityHashMap<>();
        for (Booking reserva : reservas) {
            if (estado != null && reserva.getEstado() != estado) continue;
            String nombre = nombreHotel.apply(reserva);
            nombres.put(reserva, nombre == null ? "" : nombre.trim());
            if (consulta.isEmpty() || normalizar(nombre).contains(consulta)
                    || normalizar(reserva.getCodigo()).contains(consulta)) visibles.add(reserva);
        }
        Collator espanol = Collator.getInstance(Locale.forLanguageTag("es-PE"));
        espanol.setStrength(Collator.PRIMARY);
        Comparator<Booking> principal = orden == Orden.NOMBRE
                ? Comparator.comparing(nombres::get, espanol)
                : Comparator.comparing(Booking::getFechaSalida);
        if (!ascendente) principal = principal.reversed();
        visibles.sort(principal.thenComparing(Booking::getId));
        return visibles;
    }

    private static String normalizar(@Nullable String texto) {
        if (texto == null) return "";
        return Normalizer.normalize(texto, Normalizer.Form.NFD)
                .replaceAll("\\p{M}+", "").toLowerCase(Locale.ROOT)
                .trim().replaceAll("\\s+", " ");
    }
}
