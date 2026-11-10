package com.taxi.gestion.dto.request;

import jakarta.validation.constraints.NotNull;

public record HabilitacionTaxistaRequest(
        @NotNull(message = "Indica si el taxista queda habilitado") Boolean habilitado) {}
