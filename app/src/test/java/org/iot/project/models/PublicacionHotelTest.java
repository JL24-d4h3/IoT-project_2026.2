package org.iot.project.models;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

/**
 * RF-007, RF-013 y RF-014: un hotel no se ofrece al cliente hasta que esta
 * completo y alguien lo publica.
 */
public class PublicacionHotelTest {

    private static Hotel nuevo() {
        return new Hotel("HX", "Hotel de prueba", "Miraflores", "Lima");
    }

    /** Cuatro fotografias, que es el minimo de RF-013. */
    private static void conFotos(Hotel hotel) {
        for (int i = 0; i < Hotel.MIN_FOTOS; i++) {
            hotel.addFoto("https://ejemplo.test/foto" + i + ".jpg");
        }
    }

    private static void conHabitacion(Hotel hotel) {
        hotel.addHabitacion(new Room("HX-R1", "HX", "Doble", 240));
    }

    @Test
    public void unHotelNaceSinPublicar() {
        assertFalse(nuevo().isPublicado());
    }

    @Test
    public void unHotelNaceSinAdministrador() {
        assertNull(nuevo().getAdministradorId());
    }

    @Test
    public void sinFotografiasNoEsPublicable() {
        Hotel hotel = nuevo();
        conHabitacion(hotel);
        assertFalse(hotel.aptoParaPublicar());
    }

    @Test
    public void sinHabitacionesNoEsPublicable() {
        Hotel hotel = nuevo();
        conFotos(hotel);
        assertFalse(hotel.aptoParaPublicar());
    }

    @Test
    public void conFotosYHabitacionesEsPublicable() {
        Hotel hotel = nuevo();
        conFotos(hotel);
        conHabitacion(hotel);
        assertTrue(hotel.aptoParaPublicar());
    }

    /**
     * Retirar no tiene condiciones: un hotel publicado al que despues se le
     * quitan las fotos se puede retirar igual. Lo que no puede es volver a
     * publicarse sin arreglarlo, y eso lo impone {@code aptoParaPublicar}.
     */
    @Test
    public void unHotelIncompletoSePuedeRetirar() {
        Hotel hotel = nuevo();
        hotel.setPublicado(true);
        hotel.setPublicado(false);
        assertFalse(hotel.isPublicado());
    }

    @Test
    public void elAdministradorSePuedeAsignarYCambiar() {
        Hotel hotel = nuevo();
        hotel.setAdministradorId("U2");
        assertEquals("U2", hotel.getAdministradorId());
        hotel.setAdministradorId("U5");
        assertEquals("U5", hotel.getAdministradorId());
    }

    /**
     * El alta del superadministrador no pide precio, asi que un hotel recien
     * registrado vale cero hasta que su administrador carga habitaciones. El
     * "Desde S/ ..." que anuncia el catalogo es el de la mas barata: si se
     * anunciara el campo guardado, todo hotel nacido del alta saldria a S/ 0.
     */
    @Test
    public void unHotelDelAltaAnunciaElPrecioDeSuHabitacionMasBarata() {
        Hotel hotel = nuevo();
        hotel.addHabitacion(new Room("HX-R1", "HX", "Doble", 240));
        hotel.addHabitacion(new Room("HX-R2", "HX", "Simple", 180));

        assertEquals(180d, hotel.getPrecioDesde(), 0.01);
    }

    @Test
    public void sinHabitacionesAnunciaElPrecioGuardado() {
        Hotel hotel = nuevo();
        hotel.setPrecioDesde(150);

        assertEquals(150d, hotel.getPrecioDesde(), 0.01);
    }
}
