package org.iot.project.models;

/**
 * Tarjeta registrada por el cliente. El cobro es simulado (RF-039, RF-050,
 * RT-010), por eso solo se guardan los ultimos cuatro digitos y nunca el
 * numero completo ni el CVV (RC-011, RT-038).
 */
public class Card {

    private final String id;
    private final String marca;
    private final String ultimos4;
    private String titular;
    private String expiracion;
    private boolean recurrente;

    public Card(String id, String marca, String ultimos4, String titular, String expiracion) {
        this.id = id;
        this.marca = marca;
        this.ultimos4 = ultimos4;
        this.titular = titular;
        this.expiracion = expiracion;
    }

    public String getId() {
        return id;
    }

    public String getMarca() {
        return marca;
    }

    public String getUltimos4() {
        return ultimos4;
    }

    public String getTitular() {
        return titular;
    }

    public void setTitular(String titular) {
        this.titular = titular;
    }

    public String getExpiracion() {
        return expiracion;
    }

    public void setExpiracion(String expiracion) {
        this.expiracion = expiracion;
    }

    public boolean isRecurrente() {
        return recurrente;
    }

    public void setRecurrente(boolean recurrente) {
        this.recurrente = recurrente;
    }

    /** "Visa ****4242" — como se nombra la tarjeta en la lista de metodos de pago. */
    public String getDisplayName() {
        return marca + " ****" + ultimos4;
    }
}
