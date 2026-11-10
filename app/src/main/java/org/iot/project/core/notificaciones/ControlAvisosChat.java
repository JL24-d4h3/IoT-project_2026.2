package org.iot.project.core.notificaciones;

import org.iot.project.models.AppNotification;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/** Deduplicación de avisos de chat durante una sesión, independiente de Android. */
public final class ControlAvisosChat {

    private final Set<String> procesados = new HashSet<>();
    private boolean recibidaPrimeraCarga;

    /**
     * Devuelve únicamente mensajes nuevos que pueden convertirse en un aviso Android.
     * La primera carga siempre se absorbe sin avisos. El llamador también debe usar
     * {@code inicial=true} en todas las cargas de caché iniciales y en la primera
     * carga confirmada por el servidor, para no anunciar después el historial que
     * faltaba en caché. Un lote vacío es válido; uno nulo no establece esa base.
     *
     * Los IDs se consumen incluso si el mensaje ya está leído o su chat está visible.
     * Se debe llamar también cuando falte permiso de notificaciones y descartar los
     * candidatos en ese caso, evitando que se acumulen para mostrarse al concederlo.
     * No modifica la marca de lectura de la bandeja.
     */
    public List<AppNotification> recibir(List<AppNotification> lote, boolean inicial,
                                          String chatVisible) {
        if (lote == null) return Collections.emptyList();
        boolean absorber = inicial || !recibidaPrimeraCarga;
        recibidaPrimeraCarga = true;
        List<AppNotification> nuevas = new ArrayList<>();
        for (AppNotification aviso : lote) {
            if (aviso == null || !idValido(aviso.getId())
                    || !procesados.add(aviso.getId())) continue;
            if (absorber || aviso.getTipo() != AppNotification.Tipo.MENSAJE
                    || aviso.isLeida() || !idValido(aviso.getBookingId())
                    || aviso.getBookingId().equals(chatVisible)) continue;
            nuevas.add(aviso);
        }
        return nuevas;
    }

    /** Debe invocarse al cerrar o cambiar la cuenta autenticada. */
    public void reiniciar() {
        procesados.clear();
        recibidaPrimeraCarga = false;
    }

    private static boolean idValido(String id) {
        return id != null && !id.trim().isEmpty() && id.length() <= 300
                && !id.contains("/") && !id.equals(".") && !id.equals("..");
    }
}
