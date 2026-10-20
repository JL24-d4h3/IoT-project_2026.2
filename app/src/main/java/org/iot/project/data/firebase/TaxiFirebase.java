package org.iot.project.data.firebase;

import com.google.firebase.Timestamp;

import org.iot.project.models.Booking;
import org.iot.project.models.BookingStatus;
import org.iot.project.models.Driver;
import org.iot.project.models.Hotel;
import org.iot.project.models.TaxiService;
import org.iot.project.models.TaxiStatus;
import org.iot.project.models.Vehicle;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.format.DateTimeParseException;
import java.util.HashMap;
import java.util.Map;

/** Contrato de los traslados solicitados por la app; no utiliza datos de demostración. */
public final class TaxiFirebase {
    private TaxiFirebase() { }

    /** Copia el pedido para que un cambio de formulario no altere un reintento de transacción. */
    public static TaxiService pedido(TaxiService solicitud, String uid) {
        if (solicitud == null || !uid.equals(solicitud.getClienteId())) {
            throw new IllegalArgumentException("Elige una reserva de tu cuenta para pedir el traslado.");
        }
        BookingFirebase.exigirId(solicitud.getBookingId());
        if (solicitud.getFecha() == null || solicitud.getHora() == null) {
            throw new IllegalArgumentException("Elige la fecha y la hora del traslado.");
        }
        if (solicitud.getNumPasajeros() < 1 || solicitud.getNumPasajeros() > 6) {
            throw new IllegalArgumentException("El traslado admite entre 1 y 6 pasajeros.");
        }
        if (!Double.isFinite(solicitud.getPrecio())) {
            throw new IllegalArgumentException("Vuelve a elegir tu reserva para consultar la tarifa del traslado.");
        }
        return new TaxiService(solicitud.getBookingId(), "", solicitud.getBookingId(), uid)
                .withRuta(etiqueta(solicitud.getOrigen(), "punto de recojo"),
                        etiqueta(solicitud.getDestino(), "destino"))
                .withProgramacion(solicitud.getFecha(), solicitud.getHora().withSecond(0).withNano(0),
                        solicitud.isIdaYVuelta()).withPasajeros(solicitud.getNumPasajeros())
                .withPrecio(solicitud.getPrecio());
    }

    public static TaxiService nuevo(TaxiService pedido, Booking reserva, Hotel hotel, String uid) {
        if (!uid.equals(reserva.getClienteId()) || !reserva.getId().equals(pedido.getBookingId())
                || !hotel.getId().equals(reserva.getHotelId())) {
            throw new IllegalArgumentException("La reserva del traslado no pertenece a tu cuenta.");
        }
        if (reserva.getEstado() != BookingStatus.CONFIRMADA && reserva.getEstado() != BookingStatus.ACTIVA
                && reserva.getEstado() != BookingStatus.FINALIZADA) {
            throw new IllegalArgumentException("Confirma el pago de tu reserva antes de solicitar un taxi.");
        }
        if (LocalDateTime.of(pedido.getFecha(), pedido.getHora()).isBefore(LocalDateTime.now())) {
            throw new IllegalArgumentException("Elige una fecha y una hora de traslado futuras.");
        }
        if (pedido.getFecha().isBefore(reserva.getFechaEntrada())
                || pedido.getFecha().isAfter(reserva.getFechaSalida())) {
            throw new IllegalArgumentException("El traslado debe programarse entre la entrada y la salida de la reserva.");
        }
        if (pedido.getNumPasajeros() > reserva.getNumHuespedes()) {
            throw new IllegalArgumentException("Los pasajeros no pueden superar los huéspedes de tu reserva.");
        }
        coordenadas(hotel.getLatitud(), hotel.getLongitud());
        double precio = reserva.calificaParaTaxiGratuito(hotel.getMontoMinimoTaxi())
                ? 0d : TaxiService.TARIFA_AEROPUERTO;
        if (Math.abs(precio - pedido.getPrecio()) > 0.001) {
            throw new IllegalArgumentException("La tarifa del traslado cambió. Vuelve a elegir tu reserva para revisar el precio.");
        }
        // Un traslado por reserva. El ID también permite recuperar una escritura cuyo resultado se perdió.
        return new TaxiService(reserva.getId(), "TAX-" + reserva.getId(), reserva.getId(), uid)
                .withRuta(pedido.getOrigen(), pedido.getDestino())
                .withRecojo(hotel.getLatitud(), hotel.getLongitud())
                .withProgramacion(pedido.getFecha(), pedido.getHora(), pedido.isIdaYVuelta())
                .withPasajeros(pedido.getNumPasajeros()).withPrecio(precio);
    }

    public static boolean mismoPedido(TaxiService existente, TaxiService pedido) {
        return existente.getBookingId().equals(pedido.getBookingId())
                && existente.getClienteId().equals(pedido.getClienteId())
                && existente.getOrigen().equals(pedido.getOrigen())
                && existente.getDestino().equals(pedido.getDestino())
                && existente.getFecha().equals(pedido.getFecha())
                && existente.getHora().equals(pedido.getHora())
                && existente.getNumPasajeros() == pedido.getNumPasajeros()
                && existente.isIdaYVuelta() == pedido.isIdaYVuelta();
    }

    public static Map<String, Object> documento(TaxiService taxi, String hotelId) {
        Map<String, Object> datos = new HashMap<>();
        datos.put("codigo", taxi.getCodigo());
        datos.put("bookingId", taxi.getBookingId());
        datos.put("clienteId", taxi.getClienteId());
        datos.put("hotelId", hotelId);
        datos.put("origen", taxi.getOrigen());
        datos.put("destino", taxi.getDestino());
        datos.put("latRecojo", taxi.getLatRecojo());
        datos.put("lngRecojo", taxi.getLngRecojo());
        datos.put("fecha", taxi.getFecha().toString());
        datos.put("hora", taxi.getHora().toString());
        datos.put("numPasajeros", taxi.getNumPasajeros());
        datos.put("idaYVuelta", taxi.isIdaYVuelta());
        datos.put("precio", taxi.getPrecio());
        datos.put("estado", TaxiStatus.SOLICITADO.name());
        datos.put("ratingCliente", 0d);
        datos.put("conductor", null);
        datos.put("ubicacionConductor", null);
        return datos;
    }

    public static TaxiService desde(String id, Map<String, Object> datos) {
        BookingFirebase.exigirId(id);
        if (datos == null) throw new IllegalArgumentException("No encontramos este servicio de taxi.");
        String bookingId = texto(datos, "bookingId", true);
        String clienteId = texto(datos, "clienteId", true);
        BookingFirebase.exigirId(bookingId);
        BookingFirebase.exigirId(clienteId);
        final LocalDate fecha;
        final LocalTime hora;
        final TaxiStatus estado;
        try {
            fecha = LocalDate.parse(texto(datos, "fecha", true));
            hora = LocalTime.parse(texto(datos, "hora", true));
            estado = TaxiStatus.valueOf(texto(datos, "estado", true));
        } catch (DateTimeParseException | IllegalArgumentException error) {
            throw new IllegalArgumentException("El traslado tiene fecha, hora o estado inválidos.");
        }
        double pasajeros = numero(datos, "numPasajeros");
        double precio = numero(datos, "precio");
        double rating = datos.containsKey("ratingCliente") ? numero(datos, "ratingCliente") : 0d;
        if (pasajeros < 1 || pasajeros > 6 || pasajeros != Math.floor(pasajeros) || precio < 0
                || rating < 0 || rating > 10 || (rating > 0 && rating < 1)) {
            throw new IllegalArgumentException("El traslado tiene pasajeros, importe o valoración inválidos.");
        }
        double latitud = numero(datos, "latRecojo");
        double longitud = numero(datos, "lngRecojo");
        coordenadas(latitud, longitud);
        Object idaYVuelta = datos.get("idaYVuelta");
        if (!(idaYVuelta instanceof Boolean)) throw new IllegalArgumentException("El traslado no indica su modalidad.");
        Driver conductor = datos.get("conductor") == null ? null : conductor(mapa(datos.get("conductor")));
        TaxiService servicio = new TaxiService(id, texto(datos, "codigo", true), bookingId, clienteId)
                .withRuta(texto(datos, "origen", true), texto(datos, "destino", true))
                .withRecojo(latitud, longitud).withProgramacion(fecha, hora, (Boolean) idaYVuelta)
                .withPasajeros((int) pasajeros).withPrecio(precio)
                .withEstadoPersistido(estado, conductor, (float) rating);
        if (datos.get("ubicacionConductor") != null) {
            Map<String, Object> ubicacion = mapa(datos.get("ubicacionConductor"));
            double lat = numero(ubicacion, "latitud");
            double lng = numero(ubicacion, "longitud");
            coordenadas(lat, lng);
            Object momento = ubicacion.get("actualizadoEn");
            if (!(momento instanceof Timestamp)) throw new IllegalArgumentException("La ubicación del conductor no tiene una fecha válida.");
            servicio.actualizarUbicacion(lat, lng, LocalDateTime.ofInstant(
                    ((Timestamp) momento).toDate().toInstant(), ZoneId.systemDefault()));
        }
        return servicio;
    }

    private static Driver conductor(Map<String, Object> datos) {
        String id = texto(datos, "id", true);
        BookingFirebase.exigirId(id);
        Driver conductor = new Driver(id, texto(datos, "nombres", true), texto(datos, "apellidos", true))
                .withContacto(texto(datos, "email", false), texto(datos, "telefono", false), "")
                .withFoto(texto(datos, "fotoUrl", false));
        if (datos.get("rating") != null) {
            double rating = numero(datos, "rating");
            if (rating < 1 || rating > 10) throw new IllegalArgumentException("La nota del conductor no es válida.");
            conductor.setRating((float) rating);
        }
        if (datos.get("vehiculo") != null) {
            Map<String, Object> vehiculo = mapa(datos.get("vehiculo"));
            conductor.withVehiculo(new Vehicle(texto(vehiculo, "placa", true), texto(vehiculo, "marca", false),
                    texto(vehiculo, "modelo", false), texto(vehiculo, "color", false))
                    .withFoto(texto(vehiculo, "fotoUrl", false)));
        }
        return conductor;
    }

    private static String etiqueta(String valor, String campo) {
        if (valor == null || valor.trim().isEmpty() || valor.trim().length() > 300) {
            throw new IllegalArgumentException("Indica un " + campo + " de hasta 300 caracteres.");
        }
        return valor.trim();
    }

    private static void coordenadas(double latitud, double longitud) {
        if (!Double.isFinite(latitud) || !Double.isFinite(longitud) || latitud < -90 || latitud > 90
                || longitud < -180 || longitud > 180) {
            throw new IllegalArgumentException("No encontramos un punto de recojo válido para este hotel.");
        }
    }

    private static String texto(Map<String, Object> datos, String campo, boolean obligatorio) {
        Object valor = datos.get(campo);
        if (valor == null && !obligatorio) return "";
        if (!(valor instanceof String) || (obligatorio && ((String) valor).trim().isEmpty())) {
            throw new IllegalArgumentException("El traslado tiene un dato inválido: " + campo + ".");
        }
        return ((String) valor).trim();
    }

    private static double numero(Map<String, Object> datos, String campo) {
        Object valor = datos.get(campo);
        if (!(valor instanceof Number) || !Double.isFinite(((Number) valor).doubleValue())) {
            throw new IllegalArgumentException("El traslado tiene un número inválido: " + campo + ".");
        }
        return ((Number) valor).doubleValue();
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> mapa(Object valor) {
        if (!(valor instanceof Map)) throw new IllegalArgumentException("El traslado tiene datos incompletos.");
        return (Map<String, Object>) valor;
    }
}
