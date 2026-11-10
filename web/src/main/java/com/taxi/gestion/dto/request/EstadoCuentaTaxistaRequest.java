package com.taxi.gestion.dto.request;

import jakarta.validation.constraints.NotNull;

public record EstadoCuentaTaxistaRequest(
        @NotNull(message = "Indica si la cuenta queda activa") Boolean activa) {}
