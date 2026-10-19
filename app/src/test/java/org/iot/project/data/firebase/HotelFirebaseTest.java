package org.iot.project.data.firebase;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;

import org.iot.project.models.Hotel;
import org.iot.project.models.Room;
import org.junit.Test;

import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;

/** Contrato del alta y lectura remotas sin SDK, red ni identidades simuladas. */
public class HotelFirebaseTest {

    @Test
    public void altaLimpiaTextosYConservaUbicacion() {
        Map<String, Object> datos = HotelFirebase.nuevo(borrador(), "uid-superadmin");

        assertEquals("Hotel Nuevo", datos.get("nombre"));
        assertEquals("Lima", datos.get("ciudad"));
        assertEquals("Miraflores", datos.get("distrito"));
        assertEquals("Av. Principal 123", datos.get("direccion"));
        assertEquals(-12.12, ((Number) datos.get("latitud")).doubleValue(), 0);
        assertEquals(-77.03, ((Number) datos.get("longitud")).doubleValue(), 0);
        assertEquals("uid-superadmin", datos.get("creadoPor"));
    }

    @Test
    public void altaNoConfiaEnIdPublicacionNiAdministradorDelBorrador() {
        Hotel hotel = borrador();
        hotel.setPublicado(true);
        hotel.setAdministradorId("uid-admin-inyectado");
        hotel.addFoto("https://example.com/foto.jpg");
        hotel.addHabitacion(new Room("R1", hotel.getId(), "Suite", 250));

        Map<String, Object> datos = HotelFirebase.nuevo(hotel, "uid-superadmin");

        assertEquals(Boolean.FALSE, datos.get("publicado"));
        assertTrue(datos.containsKey("administradorId"));
        assertNull(datos.get("administradorId"));
        assertEquals(new HashSet<>(Arrays.asList("nombre", "ciudad", "distrito", "direccion",
                "latitud", "longitud", "publicado", "administradorId", "creadoPor")),
                datos.keySet());
    }

    @Test
    public void altaExigeNombreCiudadYDistrito() {
        Hotel sinNombre = new Hotel("", " ", "Miraflores", "Lima");
        Hotel sinCiudad = new Hotel("", "Hotel", "Miraflores", " ");
        Hotel sinDistrito = new Hotel("", "Hotel", " ", "Lima");
        for (Hotel hotel : Arrays.asList(sinNombre, sinCiudad, sinDistrito)) {
            hotel.setUbicacion(-12.12, -77.03);
            assertThrows(IllegalArgumentException.class,
                    () -> HotelFirebase.nuevo(hotel, "uid-superadmin"));
        }
    }

    @Test
    public void direccionEsOpcional() {
        Hotel hotel = borrador();
        hotel.setDireccion(null);
        Map<String, Object> datos = HotelFirebase.nuevo(hotel, "uid-superadmin");

        assertEquals("", datos.get("direccion"));
        assertEquals("", HotelFirebase.desde("hotel-remoto", datos).getDireccion());
    }

    @Test
    public void creadorDebeSerUnaIdentidadDeDocumentoValida() {
        for (String uid : Arrays.asList("", " ", "usuarios/otra-cuenta")) {
            assertThrows(IllegalArgumentException.class,
                    () -> HotelFirebase.nuevo(borrador(), uid));
        }
        assertThrows(IllegalArgumentException.class,
                () -> HotelFirebase.nuevo(borrador(), null));
    }

    @Test
    public void coordenadasFueraDeRangoNoSePersisten() {
        double[][] coordenadas = {
                {90.01, -77}, {-90.01, -77}, {-12, 180.01}, {-12, -180.01},
                {0, 0}, {Double.NaN, -77}, {-12, Double.NaN},
                {Double.POSITIVE_INFINITY, -77}, {-12, Double.NEGATIVE_INFINITY}
        };
        for (double[] coordenada : coordenadas) {
            Hotel hotel = borrador();
            hotel.setUbicacion(coordenada[0], coordenada[1]);
            assertThrows(IllegalArgumentException.class,
                    () -> HotelFirebase.nuevo(hotel, "uid-superadmin"));
        }
    }

    @Test
    public void aceptaBordesGeograficosYUnSoloCero() {
        double[][] coordenadas = {{90, 180}, {-90, -180}, {0, -77}, {-12, 0}};
        for (double[] coordenada : coordenadas) {
            Hotel hotel = borrador();
            hotel.setUbicacion(coordenada[0], coordenada[1]);
            Hotel leido = HotelFirebase.desde("hotel-remoto",
                    HotelFirebase.nuevo(hotel, "uid-superadmin"));
            assertEquals(coordenada[0], leido.getLatitud(), 0);
            assertEquals(coordenada[1], leido.getLongitud(), 0);
        }
    }

    @Test
    public void lecturaUsaIdDelDocumentoYNoInventarioDeDemostracion() {
        Map<String, Object> datos = datos();
        datos.put("id", "H1");
        Hotel hotel = HotelFirebase.desde("id-documento", datos);

        assertEquals("id-documento", hotel.getId());
        assertEquals("Hotel Nuevo", hotel.getNombre());
        assertEquals("Lima", hotel.getCiudad());
        assertEquals("Miraflores", hotel.getDistrito());
        assertFalse(hotel.isPublicado());
        assertNull(hotel.getAdministradorId());
        assertTrue(hotel.getFotos().isEmpty());
        assertTrue(hotel.getHabitaciones().isEmpty());
        assertTrue(hotel.getServicios().isEmpty());
        assertEquals(0, hotel.getRating(), 0);
        assertEquals(0, hotel.getNumReviews());
    }

    @Test
    public void lecturaConservaAdministradorYPublicacionDelServidor() {
        Map<String, Object> datos = datos();
        datos.put("administradorId", "uid-admin-real");
        datos.put("publicado", true);
        Hotel hotel = HotelFirebase.desde("hotel-remoto", datos);

        assertEquals("uid-admin-real", hotel.getAdministradorId());
        assertTrue(hotel.isPublicado());
    }

    @Test
    public void lecturaRechazaIdInvalidoYDocumentoAusente() {
        for (String id : Arrays.asList("", " ", "hoteles/H1")) {
            assertThrows(IllegalArgumentException.class,
                    () -> HotelFirebase.desde(id, datos()));
        }
        assertThrows(IllegalArgumentException.class,
                () -> HotelFirebase.desde("hotel-remoto", null));
    }

    @Test
    public void lecturaExigeDatosBasicosConTiposValidos() {
        for (String campo : Arrays.asList("nombre", "ciudad", "distrito", "latitud", "longitud", "publicado")) {
            Map<String, Object> datos = datos();
            datos.remove(campo);
            assertThrows(IllegalArgumentException.class,
                    () -> HotelFirebase.desde("hotel-remoto", datos));
        }
        for (String campo : Arrays.asList("nombre", "ciudad", "distrito", "direccion", "administradorId")) {
            Map<String, Object> datos = datos();
            datos.put(campo, 123);
            assertThrows(IllegalArgumentException.class,
                    () -> HotelFirebase.desde("hotel-remoto", datos));
        }
    }

    @Test
    public void lecturaNoConvierteBooleanosNiNumerosDesdeStrings() {
        for (String campo : Arrays.asList("latitud", "longitud", "publicado")) {
            Map<String, Object> datos = datos();
            datos.put(campo, "false");
            assertThrows(IllegalArgumentException.class,
                    () -> HotelFirebase.desde("hotel-remoto", datos));
        }
    }

    @Test
    public void lecturaValidaCoordenadasSinConfiarEnQueLaAppLasCreo() {
        Map<String, Object> datos = datos();
        datos.put("latitud", Double.NaN);
        assertThrows(IllegalArgumentException.class,
                () -> HotelFirebase.desde("hotel-remoto", datos));
        datos.put("latitud", -12);
        datos.put("longitud", 181L);
        assertThrows(IllegalArgumentException.class,
                () -> HotelFirebase.desde("hotel-remoto", datos));
    }

    @Test
    public void lecturaNoAceptaUnaRutaComoAdministrador() {
        Map<String, Object> datos = datos();
        datos.put("administradorId", "usuarios/uid-admin");
        assertThrows(IllegalArgumentException.class,
                () -> HotelFirebase.desde("hotel-remoto", datos));
    }

    @Test
    public void lecturaDeOpcionalesPreservaCifrasYFotografias() {
        Map<String, Object> datos = datos();
        datos.put("descripcion", " Descripcion del hotel ");
        datos.put("rating", 9.2);
        datos.put("numReviews", 12L);
        datos.put("precioDesde", 250L);
        datos.put("montoMinimoTaxi", 400.5);
        datos.put("fotos", Arrays.asList("https://example.com/1.jpg", "https://example.com/2.jpg"));
        Hotel hotel = HotelFirebase.desde("hotel-remoto", datos);

        assertEquals("Descripcion del hotel", hotel.getDescripcion());
        assertEquals(9.2, hotel.getRating(), 0.001);
        assertEquals(12, hotel.getNumReviews());
        assertEquals(250, hotel.getPrecioDesde(), 0);
        assertEquals(400.5, hotel.getMontoMinimoTaxi(), 0);
        assertEquals(2, hotel.getFotos().size());
    }

    @Test
    public void lecturaRechazaCifrasOpcionalesInvalidas() {
        for (String campo : Arrays.asList("rating", "numReviews", "precioDesde", "montoMinimoTaxi")) {
            Map<String, Object> datos = datos();
            datos.put(campo, -1);
            assertThrows(IllegalArgumentException.class,
                    () -> HotelFirebase.desde("hotel-remoto", datos));
        }
        Map<String, Object> ratingInvalido = datos();
        ratingInvalido.put("rating", 0.5);
        assertThrows(IllegalArgumentException.class,
                () -> HotelFirebase.desde("hotel-remoto", ratingInvalido));
        Map<String, Object> reviewsFraccionarias = datos();
        reviewsFraccionarias.put("numReviews", 1.5);
        assertThrows(IllegalArgumentException.class,
                () -> HotelFirebase.desde("hotel-remoto", reviewsFraccionarias));
    }

    private static Hotel borrador() {
        Hotel hotel = new Hotel("id-no-confiable", " Hotel Nuevo ", " Miraflores ", " Lima ");
        hotel.setDireccion(" Av. Principal 123 ");
        hotel.setUbicacion(-12.12, -77.03);
        return hotel;
    }

    private static Map<String, Object> datos() {
        return new HashMap<>(HotelFirebase.nuevo(borrador(), "uid-superadmin"));
    }
}
