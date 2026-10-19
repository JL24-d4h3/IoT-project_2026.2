package org.iot.project.data.firebase;

import org.iot.project.models.Hotel;
import org.iot.project.models.HotelService;
import org.iot.project.models.NearbyPlace;
import org.iot.project.models.Room;

import java.net.URI;
import java.net.URISyntaxException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** Mapeo y validacion del contenido del hotel, independientes del SDK Firebase. */
public final class HotelFirebase {
    public static final int MAX_FOTOS = 12;
    public static final int MAX_FOTOS_HABITACION = 4;
    public static final int MAX_HABITACIONES = 10;
    public static final int MAX_SERVICIOS = 12;
    public static final int MAX_LUGARES = 10;

    private HotelFirebase() {
    }

    /** El alta no confia en la publicacion o la asignacion del borrador. */
    public static Map<String, Object> nuevo(Hotel borrador, String creadorUid) {
        exigirId(creadorUid, "No hay una identidad válida para registrar el hotel.");
        if (borrador == null) throw new IllegalArgumentException("Completa los datos del hotel.");
        validarCoordenadas(borrador.getLatitud(), borrador.getLongitud());
        Map<String, Object> datos = new HashMap<>();
        datos.put("nombre", obligatorio(borrador.getNombre(), 120, "Indica el nombre del hotel."));
        datos.put("ciudad", obligatorio(borrador.getCiudad(), 120, "Indica la ciudad del hotel."));
        datos.put("distrito", obligatorio(borrador.getDistrito(), 120, "Indica el distrito del hotel."));
        datos.put("direccion", limitado(borrador.getDireccion(), 240, "La dirección"));
        datos.put("latitud", borrador.getLatitud());
        datos.put("longitud", borrador.getLongitud());
        datos.put("publicado", false);
        datos.put("administradorId", null);
        datos.put("creadoPor", creadorUid);
        return datos;
    }

    /** Solo campos del formulario general; nunca asignacion, ciudad ni permisos. */
    public static Map<String, Object> actualizacionDatos(String nombre, String descripcion,
                                                        String direccion, double latitud,
                                                        double longitud) {
        validarCoordenadas(latitud, longitud);
        Map<String, Object> datos = new HashMap<>();
        datos.put("nombre", obligatorio(nombre, 120, "El hotel necesita un nombre."));
        datos.put("descripcion", limitado(descripcion, 5000, "La descripción"));
        datos.put("direccion", obligatorio(direccion, 240, "Indica la dirección del hotel."));
        datos.put("latitud", latitud);
        datos.put("longitud", longitud);
        return datos;
    }

    public static List<String> fotosHotel(List<String> fotos) {
        return fotosValidas(fotos, MAX_FOTOS, "El hotel");
    }

    /** Representacion completa; las identidades proceden del repositorio. */
    public static Map<String, Object> habitacion(Room habitacion, String hotelId, String roomId) {
        exigirId(hotelId, "Selecciona un hotel válido.");
        exigirId(roomId, "La habitación necesita un identificador válido.");
        if (habitacion == null || !hotelId.equals(habitacion.getHotelId())) {
            throw new IllegalArgumentException("La habitación no pertenece a este hotel.");
        }
        validarHabitacion(habitacion);
        Map<String, Object> datos = new HashMap<>();
        datos.put("id", roomId);
        datos.put("hotelId", hotelId);
        datos.put("tipo", obligatorio(habitacion.getTipo(), 80, "Indica el tipo de habitación."));
        datos.put("numero", obligatorio(habitacion.getNumero(), 80,
                "Indica el número o nombre de la habitación."));
        datos.put("piso", habitacion.getPiso());
        datos.put("capacidadAdultos", habitacion.getCapacidadAdultos());
        datos.put("capacidadNinos", habitacion.getCapacidadNinos());
        datos.put("areaM2", habitacion.getAreaM2());
        datos.put("precioNoche", habitacion.getPrecioNoche());
        datos.put("disponible", habitacion.isDisponible());
        datos.put("fotos", fotosValidas(habitacion.getFotos(), MAX_FOTOS_HABITACION, "La habitación"));
        return datos;
    }

    public static Map<String, Object> servicio(HotelService servicio) {
        if (servicio == null) throw new IllegalArgumentException("Selecciona un servicio.");
        exigirId(servicio.getServiceId(), "Selecciona un servicio válido.");
        double precio = servicio.getPrice();
        if (!Double.isFinite(precio) || precio < 0 || precio > 1000000
                || (servicio.isIncluded() ? precio != 0 : precio <= 0)) {
            throw new IllegalArgumentException(
                    "Un servicio incluido tiene precio cero; un adicional debe costar más de cero y hasta 1000000.");
        }
        Map<String, Object> datos = new HashMap<>();
        datos.put("serviceId", servicio.getServiceId());
        datos.put("included", servicio.isIncluded());
        datos.put("price", precio);
        return datos;
    }

    public static Map<String, Object> lugar(NearbyPlace lugar) {
        if (lugar == null) throw new IllegalArgumentException("Completa los datos del lugar.");
        if (!Double.isFinite(lugar.getDistanciaKm()) || lugar.getDistanciaKm() <= 0
                || lugar.getDistanciaKm() > 1000) {
            throw new IllegalArgumentException("La distancia debe ser mayor que cero y hasta 1000 km.");
        }
        Map<String, Object> datos = new HashMap<>();
        datos.put("nombre", obligatorio(lugar.getNombre(), 120, "Indica el nombre del lugar."));
        datos.put("tipo", obligatorio(lugar.getTipo(), 120, "Indica el tipo de lugar."));
        datos.put("distanciaKm", lugar.getDistanciaKm());
        return datos;
    }

    /** Opcionales ausentes se leen vacios para hoteles ya registrados. */
    public static Hotel desde(String id, Map<String, Object> datos) {
        exigirId(id, "No encontramos un identificador de hotel válido.");
        if (datos == null) throw new IllegalArgumentException("No encontramos los datos del hotel.");
        Hotel hotel = new Hotel(id,
                obligatorio(texto(datos, "nombre"), 120, "El hotel no tiene nombre."),
                obligatorio(texto(datos, "distrito"), 120, "El hotel no tiene distrito."),
                obligatorio(texto(datos, "ciudad"), 120, "El hotel no tiene ciudad."));
        hotel.setDireccion(limitado(texto(datos, "direccion"), 240, "La dirección"));
        hotel.setDescripcion(limitado(texto(datos, "descripcion"), 5000, "La descripción"));
        double latitud = numero(datos, "latitud", false);
        double longitud = numero(datos, "longitud", false);
        validarCoordenadas(latitud, longitud);
        hotel.setUbicacion(latitud, longitud);
        hotel.setPublicado(booleano(datos, "publicado"));
        String administradorId = texto(datos, "administradorId");
        if (!administradorId.isEmpty()) {
            exigirId(administradorId, "El administrador asignado al hotel no es válido.");
            hotel.setAdministradorId(administradorId);
        }
        double rating = numero(datos, "rating", true);
        if (rating < 0 || rating > 10 || (rating > 0 && rating < 1)) {
            throw new IllegalArgumentException("La calificación del hotel no es válida.");
        }
        if (rating > 0) hotel.setRating((float) rating);
        hotel.setNumReviews(entero(datos, "numReviews", 0, Integer.MAX_VALUE, true));
        hotel.setPrecioDesde(noNegativo(datos, "precioDesde"));
        hotel.setMontoMinimoTaxi(noNegativo(datos, "montoMinimoTaxi"));
        for (String foto : fotosDesde(datos, "fotos", MAX_FOTOS, "El hotel")) hotel.addFoto(foto);

        Set<String> roomIds = new HashSet<>();
        for (Object valor : lista(datos, "habitaciones", MAX_HABITACIONES)) {
            Map<String, Object> mapa = mapa(valor, "La habitación");
            String roomId = texto(mapa, "id");
            exigirId(roomId, "La habitación no tiene un identificador válido.");
            if (!roomIds.add(roomId) || !id.equals(texto(mapa, "hotelId"))) {
                throw new IllegalArgumentException("Las habitaciones tienen identidades duplicadas o de otro hotel.");
            }
            Room habitacion = new Room(roomId, id, texto(mapa, "tipo"), numero(mapa, "precioNoche", false));
            habitacion.withCapacidad(entero(mapa, "capacidadAdultos", 1, 20, false),
                    entero(mapa, "capacidadNinos", 0, 20, false))
                    .withArea(numero(mapa, "areaM2", false))
                    .withUbicacion(entero(mapa, "piso", -10, 200, false), texto(mapa, "numero"));
            habitacion.setDisponible(booleano(mapa, "disponible"));
            for (String foto : fotosDesde(mapa, "fotos", MAX_FOTOS_HABITACION, "La habitación")) {
                habitacion.addFoto(foto);
            }
            habitacion(habitacion, id, roomId);
            hotel.addHabitacion(habitacion);
        }
        Set<String> servicios = new HashSet<>();
        for (Object valor : lista(datos, "servicios", MAX_SERVICIOS)) {
            Map<String, Object> mapa = mapa(valor, "El servicio");
            String serviceId = texto(mapa, "serviceId");
            if (!servicios.add(serviceId)) throw new IllegalArgumentException("El hotel tiene un servicio duplicado.");
            boolean incluido = booleano(mapa, "included");
            double precio = numero(mapa, "price", false);
            if (incluido && precio != 0) throw new IllegalArgumentException("Un servicio incluido no puede tener un cargo.");
            HotelService servicio = new HotelService(id, serviceId, incluido, precio);
            servicio(servicio);
            hotel.addServicio(servicio);
        }
        for (Object valor : lista(datos, "lugaresCercanos", MAX_LUGARES)) {
            Map<String, Object> mapa = mapa(valor, "El lugar cercano");
            NearbyPlace lugar = new NearbyPlace(texto(mapa, "nombre"), texto(mapa, "tipo"),
                    numero(mapa, "distanciaKm", false));
            lugar(lugar);
            hotel.addLugarCercano(lugar);
        }
        return hotel;
    }

    private static void validarHabitacion(Room habitacion) {
        if (habitacion.getCapacidadAdultos() < 1 || habitacion.getCapacidadAdultos() > 20
                || habitacion.getCapacidadNinos() < 0 || habitacion.getCapacidadNinos() > 20) {
            throw new IllegalArgumentException("La habitación admite de 1 a 20 adultos y de 0 a 20 niños.");
        }
        if (habitacion.getPiso() < -10 || habitacion.getPiso() > 200) {
            throw new IllegalArgumentException("El piso debe estar entre -10 y 200.");
        }
        if (!Double.isFinite(habitacion.getAreaM2()) || habitacion.getAreaM2() <= 0
                || habitacion.getAreaM2() > 10000) {
            throw new IllegalArgumentException("El área debe ser mayor que cero y hasta 10000 m².");
        }
        if (!Double.isFinite(habitacion.getPrecioNoche()) || habitacion.getPrecioNoche() <= 0
                || habitacion.getPrecioNoche() > 1000000) {
            throw new IllegalArgumentException("El precio por noche debe ser mayor que cero y hasta 1000000.");
        }
    }

    private static List<String> fotosDesde(Map<String, Object> datos, String campo, int maximo,
                                          String sujeto) {
        List<String> fotos = new ArrayList<>();
        for (Object valor : lista(datos, campo, maximo)) {
            if (!(valor instanceof String)) throw new IllegalArgumentException("Una fotografía no tiene una URL válida.");
            fotos.add((String) valor);
        }
        return fotosValidas(fotos, maximo, sujeto);
    }

    private static List<String> fotosValidas(List<String> fotos, int maximo, String sujeto) {
        if (fotos == null || fotos.size() > maximo) {
            throw new IllegalArgumentException(sujeto + " admite hasta " + maximo + " fotografías.");
        }
        List<String> resultado = new ArrayList<>();
        Set<String> unicas = new HashSet<>();
        for (String valor : fotos) {
            String url = limpio(valor);
            try {
                URI uri = new URI(url);
                if (url.length() > 2048 || !("http".equals(uri.getScheme()) || "https".equals(uri.getScheme()))
                        || uri.getHost() == null || uri.getHost().isEmpty()) {
                    throw new IllegalArgumentException("Usa una URL http o https válida, de hasta 2048 caracteres.");
                }
            } catch (URISyntaxException error) {
                throw new IllegalArgumentException("Usa una URL http o https válida, de hasta 2048 caracteres.");
            }
            if (!unicas.add(url)) throw new IllegalArgumentException("Las fotografías no pueden repetirse.");
            resultado.add(url);
        }
        return resultado;
    }

    private static List<?> lista(Map<String, Object> datos, String campo, int maximo) {
        Object valor = datos.get(campo);
        if (valor == null) return Collections.emptyList();
        if (!(valor instanceof List<?>) || ((List<?>) valor).size() > maximo) {
            throw new IllegalArgumentException("El campo " + campo + " admite hasta " + maximo + " elementos.");
        }
        return (List<?>) valor;
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> mapa(Object valor, String sujeto) {
        if (!(valor instanceof Map<?, ?>)) throw new IllegalArgumentException(sujeto + " no tiene datos válidos.");
        return (Map<String, Object>) valor;
    }

    private static boolean booleano(Map<String, Object> datos, String campo) {
        Object valor = datos.get(campo);
        if (!(valor instanceof Boolean)) throw new IllegalArgumentException("El campo " + campo + " no es válido.");
        return (Boolean) valor;
    }

    private static int entero(Map<String, Object> datos, String campo, int minimo, int maximo,
                              boolean opcional) {
        double valor = numero(datos, campo, opcional);
        if (valor != Math.floor(valor) || valor < minimo || valor > maximo) {
            throw new IllegalArgumentException("El campo " + campo + " debe estar entre " + minimo + " y " + maximo + ".");
        }
        return (int) valor;
    }

    private static double noNegativo(Map<String, Object> datos, String campo) {
        double valor = numero(datos, campo, true);
        if (valor < 0) throw new IllegalArgumentException("El hotel tiene un monto inválido: " + campo + ".");
        return valor;
    }

    private static double numero(Map<String, Object> datos, String campo, boolean opcional) {
        Object valor = datos.get(campo);
        if (valor == null && opcional) return 0;
        if (!(valor instanceof Number) || !Double.isFinite(((Number) valor).doubleValue())) {
            throw new IllegalArgumentException("El hotel tiene un campo numérico inválido: " + campo + ".");
        }
        return ((Number) valor).doubleValue();
    }

    private static void validarCoordenadas(double latitud, double longitud) {
        if (!Double.isFinite(latitud) || !Double.isFinite(longitud)
                || latitud < -90 || latitud > 90 || longitud < -180 || longitud > 180
                || (latitud == 0 && longitud == 0)) {
            throw new IllegalArgumentException("Indica coordenadas válidas para el hotel.");
        }
    }

    private static String texto(Map<String, Object> datos, String campo) {
        Object valor = datos.get(campo);
        if (valor == null) return "";
        if (!(valor instanceof String)) throw new IllegalArgumentException("El campo " + campo + " no es válido.");
        return ((String) valor).trim();
    }

    private static String obligatorio(String valor, int maximo, String mensaje) {
        String resultado = limitado(valor, maximo, "El texto");
        if (resultado.isEmpty()) throw new IllegalArgumentException(mensaje);
        return resultado;
    }

    private static String limitado(String valor, int maximo, String campo) {
        String resultado = limpio(valor);
        if (resultado.length() > maximo) {
            throw new IllegalArgumentException(campo + " admite hasta " + maximo + " caracteres.");
        }
        return resultado;
    }

    private static String limpio(String valor) {
        return valor == null ? "" : valor.trim();
    }

    private static void exigirId(String id, String mensaje) {
        if (id == null || id.trim().isEmpty() || id.contains("/") || id.length() > 200) {
            throw new IllegalArgumentException(mensaje);
        }
    }
}
