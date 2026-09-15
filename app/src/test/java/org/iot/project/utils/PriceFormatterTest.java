package org.iot.project.utils;

import static org.junit.Assert.assertEquals;

import org.junit.Test;

/**
 * El precio es informacion critica (§66) y se muestra en casi todas las
 * pantallas, asi que su formato se fija con pruebas en vez de confiar en el
 * locale del dispositivo.
 */
public class PriceFormatterTest {

    @Test
    public void usaSeparadorDeMilesConComa() {
        assertEquals("S/ 1,240", PriceFormatter.format(1240));
    }

    @Test
    public void formateaMilesYMillones() {
        assertEquals("S/ 12,500", PriceFormatter.format(12500));
        assertEquals("S/ 1,250,000", PriceFormatter.format(1250000));
    }

    @Test
    public void omiteDecimalesEnElFormatoDeCard() {
        assertEquals("S/ 280", PriceFormatter.format(280.00));
    }

    @Test
    public void redondeaElCentavoHaciaArriba() {
        assertEquals("S/ 281", PriceFormatter.format(280.5));
        assertEquals("S/ 280", PriceFormatter.format(280.4));
    }

    @Test
    public void conservaCentavosEnElDesgloseDePago() {
        assertEquals("S/ 1,030.50", PriceFormatter.formatConDecimales(1030.5));
        assertEquals("S/ 1,030.00", PriceFormatter.formatConDecimales(1030));
    }

    @Test
    public void componeElPrecioPorNoche() {
        assertEquals("S/ 280 por noche", PriceFormatter.formatPorNoche(280));
    }

    @Test
    public void componeElTotalConSuNumeroDeNoches() {
        assertEquals("S/ 1,120 · 4 noches", PriceFormatter.formatTotalConNoches(1120, 4));
    }

    @Test
    public void usaSingularCuandoEsUnaSolaNoche() {
        assertEquals("S/ 280 · 1 noche", PriceFormatter.formatTotalConNoches(280, 1));
    }

    @Test
    public void formateaCeroSinDecimales() {
        assertEquals("S/ 0", PriceFormatter.format(0));
    }
}
