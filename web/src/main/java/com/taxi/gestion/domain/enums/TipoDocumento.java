package com.taxi.gestion.domain.enums;

/** Tipo de documento de identidad del taxista. */
public enum TipoDocumento {
    DNI("DNI"),
    PASAPORTE("Pasaporte"),
    CARNET_EXTRANJERIA("Carnet de extranjeria");

    private final String etiqueta;

    TipoDocumento(String etiqueta) {
        this.etiqueta = etiqueta;
    }

    public String getEtiqueta() {
        return etiqueta;
    }
}
