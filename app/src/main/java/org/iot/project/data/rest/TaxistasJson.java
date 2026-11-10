package org.iot.project.data.rest;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

import org.iot.project.models.Driver;
import org.iot.project.models.TaxiService;
import org.iot.project.models.TaxiStatus;
import org.iot.project.models.Vehicle;

import java.math.BigDecimal;
import java.net.URI;

/** Traduce el contrato de gestion-taxistas sin depender de Android ni de Firebase. */
public final class TaxistasJson {
    private TaxistasJson() { }

    public static Driver conductor(JsonObject datos) {
        return conductor(datos, null);
    }

    /** Una base opcional permite resolver las fotos que el servidor devuelve como /uploads/.... */
    public static Driver conductor(JsonObject datos, String baseUrl) {
        exigirObjeto(datos);
        String nombres = texto(datos, "nombres");
        String apellidos = texto(datos, "apellidos");
        if (nombres == null) {
            nombres = requerido(datos, "nombreCompleto");
            apellidos = ""; // El resumen no separa nombres y apellidos.
        }
        Driver conductor = new Driver(id(datos, "id"), nombres, apellidos == null ? "" : apellidos)
                .withContacto(null, texto(datos, "telefono"), null)
                .withFoto(foto(texto(datos, "fotoUrl"), baseUrl));
        String tipo = texto(datos, "tipoDocumento");
        String documento = texto(datos, "documentoEnmascarado");
        if (tipo != null || documento != null) conductor.withDocumento(tipo, documento);
        conductor.setHabilitado("HABILITADO".equals(texto(datos, "estadoHabilitacion"))
                && booleano(datos, "cuentaActiva"));

        Double promedio = numeroOpcional(datos, "valoracionPromedio");
        if (promedio != null) {
            if (promedio < 0d || promedio > 5d) throw invalido("valoracionPromedio");
            // Cero significa que todavía no tiene valoraciones; no se inventa una nota de 1.
            if (promedio > 0d) conductor.setRating((float) (promedio * 2d));
        }
        Double total = numeroOpcional(datos, "totalValoraciones");
        if (total != null) {
            if (total < 0d || total > Integer.MAX_VALUE || total != Math.floor(total)) {
                throw invalido("totalValoraciones");
            }
            conductor.setNumServicios(total.intValue());
        }
        String placa = texto(datos, "placaVehiculo");
        if (placa != null) {
            conductor.withVehiculo(new Vehicle(placa)
                    .withFoto(foto(texto(datos, "fotoVehiculoUrl"), baseUrl)));
        }
        return conductor;
    }

    /** Conserva el vínculo con la reserva local al actualizar el estado remoto. */
    public static TaxiService servicio(JsonObject datos, TaxiService contexto) {
        exigirObjeto(datos);
        String apiId = id(datos, "id");
        if (contexto != null && contexto.getApiId() != null && !contexto.getApiId().isEmpty()
                && !apiId.equals(contexto.getApiId())) {
            throw new IllegalArgumentException("La respuesta pertenece a otro servicio de taxi.");
        }
        final TaxiStatus estado;
        try {
            estado = TaxiStatus.valueOf(requerido(datos, "estado"));
        } catch (IllegalArgumentException error) {
            throw invalido("estado");
        }
        String codigo = texto(datos, "codigoQr");
        if (codigo == null) codigo = contexto == null ? "" : contexto.getCodigo();
        TaxiService servicio = new TaxiService(contexto == null ? apiId : contexto.getId(),
                codigo, contexto == null ? "" : contexto.getBookingId(),
                contexto == null ? "" : contexto.getClienteId()).withApiId(apiId)
                .withRuta(ruta(datos, "direccionOrigen", contexto == null ? null : contexto.getOrigen()),
                        ruta(datos, "direccionDestino", contexto == null ? null : contexto.getDestino()));

        if (contexto != null) {
            servicio.withProgramacion(contexto.getFecha(), contexto.getHora(), contexto.isIdaYVuelta())
                    .withPasajeros(contexto.getNumPasajeros()).withPrecio(contexto.getPrecio());
        } else {
            Double tarifa = numeroOpcional(datos, "tarifa");
            if (tarifa != null) {
                if (tarifa < 0d) throw invalido("tarifa");
                servicio.withPrecio(tarifa);
            }
        }
        Double latitud = numeroOpcional(datos, "latitudOrigen");
        Double longitud = numeroOpcional(datos, "longitudOrigen");
        if (latitud != null || longitud != null) {
            if (latitud == null || longitud == null) throw invalido("punto de recojo");
            coordenadas(latitud, longitud);
            servicio.withRecojo(latitud, longitud);
        } else if (contexto != null && contexto.hasRecojo()) {
            coordenadas(contexto.getLatRecojo(), contexto.getLngRecojo());
            servicio.withRecojo(contexto.getLatRecojo(), contexto.getLngRecojo());
        }

        Driver anterior = contexto == null ? null : contexto.getDriver();
        Driver conductor = null;
        String conductorId = presente(datos, "taxistaId") ? id(datos, "taxistaId") : null;
        if (conductorId != null) {
            if (anterior != null && conductorId.equals(anterior.getId())) {
                conductor = anterior;
            } else {
                conductor = new Driver(conductorId, requerido(datos, "taxistaNombre"), "");
                String placa = texto(datos, "taxistaPlaca");
                if (placa != null) conductor.withVehiculo(new Vehicle(placa));
            }
        }
        if (estado != TaxiStatus.SOLICITADO && conductor == null) throw invalido("taxistaId");
        if (estado == TaxiStatus.SOLICITADO && conductor != null) throw invalido("estado/taxistaId");
        float rating = contexto == null ? 0f : contexto.getRatingCliente();
        servicio.withEstadoPersistido(estado, conductor, rating);
        // ServicioResponse no contiene posición del conductor. Solo se conserva un reporte real
        // del mismo conductor; nunca se usa el punto de recojo como si fuera su ubicación.
        if (contexto != null && conductor != null && anterior != null
                && conductor.getId().equals(anterior.getId())
                && contexto.getUltimaActualizacionUbicacion() != null) {
            coordenadas(contexto.getLatConductor(), contexto.getLngConductor());
            servicio.actualizarUbicacion(contexto.getLatConductor(), contexto.getLngConductor(),
                    contexto.getUltimaActualizacionUbicacion());
        }
        return servicio;
    }

    public static JsonObject solicitud(TaxiService servicio, String nombre, String telefono) {
        if (servicio == null) throw new IllegalArgumentException("No encontramos el traslado solicitado.");
        JsonObject datos = new JsonObject();
        datos.addProperty("clienteId", limitado(servicio.getClienteId(), "clienteId", 60));
        datos.addProperty("nombreCliente", limitado(nombre, "nombreCliente", 120));
        if (telefono != null && !telefono.trim().isEmpty()) {
            String valor = telefono.trim();
            if (!valor.matches("^[0-9+\\- ]{6,20}$")) throw invalido("telefonoCliente");
            datos.addProperty("telefonoCliente", valor);
        }
        datos.addProperty("direccionOrigen", limitado(servicio.getOrigen(), "direccionOrigen", 200));
        datos.addProperty("direccionDestino", limitado(servicio.getDestino(), "direccionDestino", 200));
        if (!servicio.hasRecojo()) {
            throw new IllegalArgumentException("El hotel no tiene coordenadas de recojo para solicitar el taxi.");
        }
        coordenadas(servicio.getLatRecojo(), servicio.getLngRecojo());
        datos.addProperty("latitudOrigen", servicio.getLatRecojo());
        datos.addProperty("longitudOrigen", servicio.getLngRecojo());
        double precio = servicio.getPrecio();
        if (!Double.isFinite(precio) || precio < 0d) throw invalido("tarifa");
        // El contrato REST acepta tarifa null o positiva. El beneficio gratuito sigue en la reserva.
        if (precio > 0d) datos.addProperty("tarifa", precio);
        return datos;
    }

    /** La API usa enteros de 1 a 5; medias estrellas Android se redondean al entero más próximo. */
    public static int puntuacion(float rating) {
        if (!Float.isFinite(rating) || rating < 1f || rating > 10f) throw invalido("puntuacion");
        return Math.max(1, Math.round(rating / 2f));
    }

    private static void exigirObjeto(JsonObject datos) {
        if (datos == null) throw new IllegalArgumentException("La API devolvió una respuesta vacía.");
    }

    private static boolean presente(JsonObject datos, String campo) {
        return datos.has(campo) && !datos.get(campo).isJsonNull();
    }

    private static String texto(JsonObject datos, String campo) {
        if (!presente(datos, campo)) return null;
        JsonElement valor = datos.get(campo);
        if (!valor.isJsonPrimitive() || !valor.getAsJsonPrimitive().isString()) throw invalido(campo);
        String texto = valor.getAsString().trim();
        return texto.isEmpty() ? null : texto;
    }

    private static String requerido(JsonObject datos, String campo) {
        String valor = texto(datos, campo);
        if (valor == null) throw invalido(campo);
        return valor;
    }

    private static String ruta(JsonObject datos, String campo, String anterior) {
        String valor = texto(datos, campo);
        if (valor == null) valor = anterior;
        return limitado(valor, campo, 200);
    }

    private static String id(JsonObject datos, String campo) {
        if (!presente(datos, campo) || !datos.get(campo).isJsonPrimitive()) throw invalido(campo);
        try {
            BigDecimal valor = new BigDecimal(datos.get(campo).getAsString());
            long numero = valor.longValueExact();
            if (numero <= 0) throw invalido(campo);
            return Long.toString(numero);
        } catch (ArithmeticException | NumberFormatException error) {
            throw invalido(campo);
        }
    }

    private static Double numeroOpcional(JsonObject datos, String campo) {
        if (!presente(datos, campo)) return null;
        JsonElement valor = datos.get(campo);
        if (!valor.isJsonPrimitive() || !valor.getAsJsonPrimitive().isNumber()) throw invalido(campo);
        double numero = valor.getAsDouble();
        if (!Double.isFinite(numero)) throw invalido(campo);
        return numero;
    }

    private static boolean booleano(JsonObject datos, String campo) {
        if (!presente(datos, campo)) return false;
        if (!datos.get(campo).isJsonPrimitive()
                || !datos.get(campo).getAsJsonPrimitive().isBoolean()) throw invalido(campo);
        return datos.get(campo).getAsBoolean();
    }

    private static String foto(String url, String baseUrl) {
        if (url == null || baseUrl == null || baseUrl.trim().isEmpty()) return url;
        try {
            return URI.create(baseUrl).resolve(url).toString();
        } catch (IllegalArgumentException error) {
            throw invalido("fotoUrl");
        }
    }

    private static String limitado(String valor, String campo, int longitudMaxima) {
        if (valor == null || valor.trim().isEmpty() || valor.trim().length() > longitudMaxima) {
            throw invalido(campo);
        }
        return valor.trim();
    }

    private static void coordenadas(double latitud, double longitud) {
        if (!Double.isFinite(latitud) || !Double.isFinite(longitud)
                || latitud < -90d || latitud > 90d || longitud < -180d || longitud > 180d) {
            throw invalido("coordenadas");
        }
    }

    private static IllegalArgumentException invalido(String campo) {
        return new IllegalArgumentException("La API de taxis tiene un dato inválido: " + campo + ".");
    }
}
