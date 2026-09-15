package org.iot.project.models;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

/**
 * Reglas 5 a 8 y 18 a 20 del sistema de servicios: el precio pertenece a la
 * relacion hotel-servicio, no al servicio, y un incluido no lleva precio.
 */
public class HotelServiceTest {

    @Test
    public void unServicioIncluidoNoTienePrecio() {
        HotelService piscina = HotelService.incluido("H1", "PISCINA");

        assertTrue(piscina.isIncluded());
        assertFalse(piscina.isAdicional());
        assertEquals(0d, piscina.getPrice(), 0.001);
    }

    @Test
    public void unServicioAdicionalConservaSuPrecio() {
        HotelService lavanderia = HotelService.adicional("H1", "LAVANDERIA", 20);

        assertTrue(lavanderia.isAdicional());
        assertEquals(20d, lavanderia.getPrice(), 0.001);
    }

    @Test(expected = IllegalArgumentException.class)
    public void unServicioAdicionalSinPrecioEsInvalido() {
        HotelService.adicional("H1", "LAVANDERIA", 0);
    }

    @Test
    public void clasificarComoIncluidoLimpiaElPrecioAnterior() {
        HotelService servicio = HotelService.adicional("H1", "PISCINA", 25);
        servicio.setIncluded(true);

        assertTrue(servicio.isIncluded());
        assertEquals(0d, servicio.getPrice(), 0.001);
    }

    @Test(expected = IllegalStateException.class)
    public void noSePuedeAsignarPrecioAUnServicioIncluido() {
        HotelService.incluido("H1", "WIFI").setPrice(10);
    }

    @Test
    public void elMismoServicioPuedeSerIncluidoEnUnHotelYAdicionalEnOtro() {
        // Regla 19: "Piscina" incluida en el Hotel A y con costo en el Hotel B.
        HotelService enHotelA = HotelService.incluido("H1", "PISCINA");
        HotelService enHotelB = HotelService.adicional("H2", "PISCINA", 25);

        assertEquals("PISCINA", enHotelA.getServiceId());
        assertEquals("PISCINA", enHotelB.getServiceId());
        assertTrue(enHotelA.isIncluded());
        assertTrue(enHotelB.isAdicional());
        assertEquals(25d, enHotelB.getPrice(), 0.001);
    }

    @Test
    public void unHotelNoDuplicaUnServicioYaAsociado() {
        Hotel hotel = new Hotel("H1", "Hotel Miraflores", "Miraflores", "Lima");

        hotel.addServicio(HotelService.incluido("H1", "WIFI"));
        hotel.addServicio(HotelService.incluido("H1", "PISCINA"));
        hotel.addServicio(HotelService.adicional("H1", "WIFI", 15));

        assertEquals(2, hotel.getServicios().size());
        HotelService wifi = hotel.getServicios().stream()
                .filter(s -> s.getServiceId().equals("WIFI"))
                .findFirst()
                .orElseThrow(AssertionError::new);

        // La segunda asociacion reemplaza a la primera en vez de duplicarla.
        assertTrue(wifi.isAdicional());
        assertEquals(15d, wifi.getPrice(), 0.001);
    }
}
