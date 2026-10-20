package org.iot.project.data.firebase;

import org.iot.project.models.Booking;
import org.iot.project.models.BookingStatus;
import org.iot.project.models.Charge;
import org.iot.project.models.Hotel;
import org.iot.project.models.HotelService;
import org.iot.project.models.Room;
import org.iot.project.models.Review;

import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** Documento de reserva y copia de los datos contratados, independiente del catálogo actual. */
public final class BookingFirebase {
    public static final int MAX_NOCHES = 90;

    private BookingFirebase() { }

    public static void exigirId(String id) {
        if (id == null || id.trim().isEmpty() || !id.equals(id.trim())
                || id.contains("/") || id.length() > 200 || id.equals(".") || id.equals("..")) {
            throw new IllegalArgumentException("El identificador de la reserva, hotel o habitación no es válido.");
        }
    }

    public static void validarPeriodo(LocalDate entrada, LocalDate salida, boolean nueva) {
        if (entrada == null || salida == null || !salida.isAfter(entrada)) {
            throw new IllegalArgumentException("La salida debe ser posterior a la entrada.");
        }
        if (ChronoUnit.DAYS.between(entrada, salida) > MAX_NOCHES) {
            throw new IllegalArgumentException("Puedes reservar hasta 90 noches por reserva.");
        }
        if (nueva && entrada.isBefore(LocalDate.now())) {
            throw new IllegalArgumentException("La fecha de entrada no puede estar en el pasado.");
        }
    }

    /** Importes, capacidad y datos de alojamiento siempre proceden del hotel leído en la transacción. */
    public static Booking nueva(Booking borrador, Hotel hotel, Room habitacion, String uid) {
        exigirId(borrador.getId());
        validarPeriodo(borrador.getFechaEntrada(), borrador.getFechaSalida(), true);
        if (!hotel.isPublicado() || !habitacion.isDisponible()) {
            throw new IllegalArgumentException("Este alojamiento ya no está disponible para reservar.");
        }
        if (!uid.equals(borrador.getClienteId()) || !hotel.getId().equals(borrador.getHotelId())
                || !habitacion.getId().equals(borrador.getRoomId())
                || !hotel.getId().equals(habitacion.getHotelId())) {
            throw new IllegalArgumentException("Los datos de la reserva no corresponden a tu cuenta o al hotel.");
        }
        if (borrador.getNumHuespedes() < 1 || borrador.getNumHuespedes() > habitacion.getCapacidadTotal()) {
            throw new IllegalArgumentException("La cantidad de huéspedes supera la capacidad de la habitación.");
        }
        if (!Double.isFinite(borrador.getPrecioNoche())
                || Math.abs(borrador.getPrecioNoche() - habitacion.getPrecioNoche()) > 0.001) {
            throw new IllegalArgumentException("El precio de la habitación cambió. Vuelve a abrir el formulario para ver el nuevo total.");
        }
        Booking reserva = new Booking(borrador.getId(), "RES-" + borrador.getId(), hotel.getId(),
                habitacion.getId(), uid, borrador.getFechaEntrada(), borrador.getFechaSalida(),
                borrador.getNumHuespedes(), habitacion.getPrecioNoche(), BookingStatus.PENDIENTE)
                .withAlojamiento(hotel.getNombre(), hotel.getCiudad(), hotel.getDistrito(),
                        hotel.getFotos().isEmpty() ? "" : hotel.getFotos().get(0),
                        habitacion.getTipo(), habitacion.getNumero());
        Set<String> elegidos = new HashSet<>();
        for (HotelService solicitado : borrador.getServiciosAdicionales()) {
            if (!elegidos.add(solicitado.getServiceId())) {
                throw new IllegalArgumentException("Un servicio adicional no puede repetirse.");
            }
            HotelService vigente = null;
            for (HotelService servicio : hotel.getServicios()) {
                if (servicio.getServiceId().equals(solicitado.getServiceId())) vigente = servicio;
            }
            if (vigente == null || vigente.isIncluded()) {
                throw new IllegalArgumentException("Un servicio seleccionado ya no está disponible como adicional.");
            }
            if (!Double.isFinite(solicitado.getPrice()) || Math.abs(solicitado.getPrice() - vigente.getPrice()) > 0.001) {
                throw new IllegalArgumentException("El precio de un servicio cambió. Vuelve a abrir el formulario para revisar el total.");
            }
            reserva.addServicioAdicional(HotelService.adicional(hotel.getId(), vigente.getServiceId(), vigente.getPrice()));
        }
        return reserva;
    }

    public static Map<String, Object> documento(Booking reserva) {
        Map<String, Object> datos = new HashMap<>();
        datos.put("codigo", reserva.getCodigo());
        datos.put("hotelId", reserva.getHotelId());
        datos.put("roomId", reserva.getRoomId());
        datos.put("clienteId", reserva.getClienteId());
        datos.put("fechaEntrada", reserva.getFechaEntrada().toString());
        datos.put("fechaSalida", reserva.getFechaSalida().toString());
        datos.put("numHuespedes", reserva.getNumHuespedes());
        datos.put("precioNoche", reserva.getPrecioNoche());
        datos.put("estado", reserva.getEstado().name());
        datos.put("hotelNombre", reserva.getHotelNombre());
        datos.put("hotelCiudad", reserva.getHotelCiudad());
        datos.put("hotelDistrito", reserva.getHotelDistrito());
        datos.put("hotelFotoUrl", reserva.getHotelFotoUrl());
        datos.put("habitacionTipo", reserva.getHabitacionTipo());
        datos.put("habitacionNumero", reserva.getHabitacionNumero());
        List<Map<String, Object>> servicios = new ArrayList<>();
        for (HotelService servicio : reserva.getServiciosAdicionales()) servicios.add(HotelFirebase.servicio(servicio));
        datos.put("serviciosAdicionales", servicios);
        datos.put("cargos", cargos(reserva));
        datos.put("cobrado", reserva.isCobrado());
        if (reserva.getValoracion() != null) datos.put("valoracion", valoracion(reserva.getValoracion()));
        return datos;
    }

    public static List<Map<String, Object>> cargos(Booking reserva) {
        List<Map<String, Object>> cargos = new ArrayList<>();
        for (Charge cargo : reserva.getCargos()) {
            Map<String, Object> datos = new HashMap<>();
            datos.put("monto", cargo.getMonto());
            datos.put("motivo", cargo.getMotivo());
            datos.put("observacion", cargo.getObservacion());
            cargos.add(datos);
        }
        return cargos;
    }

    public static Map<String, Object> valoracion(Review valoracion) {
        Map<String, Object> datos = new HashMap<>();
        datos.put("id", valoracion.getId());
        datos.put("entidadId", valoracion.getEntidadId());
        datos.put("autorNombre", valoracion.getAutorNombre());
        datos.put("autorFotoUrl", valoracion.getAutorFotoUrl() == null ? "" : valoracion.getAutorFotoUrl());
        datos.put("rating", valoracion.getRating());
        datos.put("comentario", valoracion.getComentario());
        datos.put("fecha", valoracion.getFecha().toString());
        return datos;
    }

    public static Booking desde(String id, Map<String, Object> datos) {
        exigirId(id);
        if (datos == null) throw new IllegalArgumentException("No encontramos los datos de esta reserva.");
        String hotelId = texto(datos, "hotelId", true);
        String roomId = texto(datos, "roomId", true);
        String clienteId = texto(datos, "clienteId", true);
        exigirId(hotelId);
        exigirId(roomId);
        exigirId(clienteId);
        final LocalDate entrada;
        final LocalDate salida;
        final BookingStatus estado;
        try {
            entrada = LocalDate.parse(texto(datos, "fechaEntrada", true));
            salida = LocalDate.parse(texto(datos, "fechaSalida", true));
            estado = BookingStatus.valueOf(texto(datos, "estado", true));
        } catch (DateTimeParseException | IllegalArgumentException error) {
            throw new IllegalArgumentException("La reserva tiene fechas o estado inválidos.");
        }
        validarPeriodo(entrada, salida, false);
        double huespedes = numero(datos, "numHuespedes");
        double precio = numero(datos, "precioNoche");
        if (huespedes < 1 || huespedes > 40 || huespedes != Math.floor(huespedes) || precio <= 0) {
            throw new IllegalArgumentException("La reserva tiene huéspedes o precio inválidos.");
        }
        Booking reserva = new Booking(id, texto(datos, "codigo", true), hotelId, roomId, clienteId,
                entrada, salida, (int) huespedes, precio, estado)
                .withAlojamiento(texto(datos, "hotelNombre", false), texto(datos, "hotelCiudad", false),
                        texto(datos, "hotelDistrito", false), texto(datos, "hotelFotoUrl", false),
                        texto(datos, "habitacionTipo", false), texto(datos, "habitacionNumero", false));
        Set<String> servicios = new HashSet<>();
        for (Object valor : lista(datos, "serviciosAdicionales")) {
            Map<String, Object> servicio = mapa(valor);
            String serviceId = texto(servicio, "serviceId", true);
            double monto = numero(servicio, "price");
            if (!servicios.add(serviceId) || !Boolean.FALSE.equals(servicio.get("included")) || monto <= 0) {
                throw new IllegalArgumentException("La reserva tiene un servicio adicional inválido.");
            }
            reserva.addServicioAdicional(HotelService.adicional(hotelId, serviceId, monto));
        }
        for (Object valor : lista(datos, "cargos")) {
            Map<String, Object> cargo = mapa(valor);
            reserva.addCargo(new Charge(numero(cargo, "monto"), texto(cargo, "motivo", true),
                    texto(cargo, "observacion", true)));
        }
        Object cobrado = datos.get("cobrado");
        if (cobrado != null && !(cobrado instanceof Boolean)) {
            throw new IllegalArgumentException("La reserva tiene un indicador de cobro inválido.");
        }
        if (Boolean.TRUE.equals(cobrado)) reserva.marcarCobrado();
        if (datos.get("valoracion") != null) {
            Map<String, Object> valoracion = mapa(datos.get("valoracion"));
            double rating = numero(valoracion, "rating");
            if (rating < Review.RATING_MIN || rating > Review.RATING_MAX
                    || !hotelId.equals(texto(valoracion, "entidadId", true))) {
                throw new IllegalArgumentException("La reserva tiene una valoración inválida.");
            }
            try {
                reserva.setValoracion(new Review(texto(valoracion, "id", true), hotelId,
                        texto(valoracion, "autorNombre", true), (float) rating,
                        texto(valoracion, "comentario", true), LocalDate.parse(texto(valoracion, "fecha", true)))
                        .withAutorFoto(texto(valoracion, "autorFotoUrl", false)));
            } catch (DateTimeParseException error) {
                throw new IllegalArgumentException("La reserva tiene una fecha de valoración inválida.");
            }
        }
        return reserva;
    }

    /** Reconocer el reintento del mismo formulario sin crear otra reserva. */
    public static boolean mismoPedido(Booking existente, Booking pedido) {
        if (!existente.getClienteId().equals(pedido.getClienteId())
                || !existente.getHotelId().equals(pedido.getHotelId())
                || !existente.getRoomId().equals(pedido.getRoomId())
                || !existente.getFechaEntrada().equals(pedido.getFechaEntrada())
                || !existente.getFechaSalida().equals(pedido.getFechaSalida())
                || existente.getNumHuespedes() != pedido.getNumHuespedes()) return false;
        Set<String> existentes = new HashSet<>();
        Set<String> pedidos = new HashSet<>();
        for (HotelService servicio : existente.getServiciosAdicionales()) existentes.add(servicio.getServiceId());
        for (HotelService servicio : pedido.getServiciosAdicionales()) pedidos.add(servicio.getServiceId());
        return existentes.equals(pedidos) && pedidos.size() == pedido.getServiciosAdicionales().size();
    }

    private static String texto(Map<String, Object> datos, String campo, boolean obligatorio) {
        Object valor = datos.get(campo);
        if (valor == null && !obligatorio) return "";
        if (!(valor instanceof String) || (obligatorio && ((String) valor).trim().isEmpty())) {
            throw new IllegalArgumentException("La reserva tiene un campo inválido: " + campo + ".");
        }
        return ((String) valor).trim();
    }

    private static double numero(Map<String, Object> datos, String campo) {
        Object valor = datos.get(campo);
        if (!(valor instanceof Number) || !Double.isFinite(((Number) valor).doubleValue())) {
            throw new IllegalArgumentException("La reserva tiene un importe o cantidad inválido: " + campo + ".");
        }
        return ((Number) valor).doubleValue();
    }

    private static List<?> lista(Map<String, Object> datos, String campo) {
        Object valor = datos.get(campo);
        if (valor == null) return Collections.emptyList();
        if (!(valor instanceof List<?>)) throw new IllegalArgumentException("La reserva tiene datos inválidos: " + campo + ".");
        return (List<?>) valor;
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> mapa(Object valor) {
        if (!(valor instanceof Map<?, ?>)) throw new IllegalArgumentException("La reserva tiene datos adicionales inválidos.");
        return (Map<String, Object>) valor;
    }
}
