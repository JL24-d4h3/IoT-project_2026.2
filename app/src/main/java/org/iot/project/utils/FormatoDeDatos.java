package org.iot.project.utils;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import org.iot.project.core.ServiceLocator;
import org.iot.project.models.Service;
import org.iot.project.models.User;

/**
 * Como se escriben los datos en las pantallas.
 *
 * <p>Existe porque son varias pantallas y las mismas cosas se escriben en mas
 * de una: el documento de una persona, el nombre de un servicio a partir de su
 * identificador. Repetidas en cada pantalla, la primera que se corrigiera
 * dejaria a las otras diciendolo distinto.
 *
 * <p>Vive en {@code utils} y no dentro de un rol porque los perfiles de dos
 * roles distintos —el administrador de hotel y el superadministrador— enseñan
 * los mismos datos de la misma manera: en el paquete de uno de ellos, el otro
 * tendria que copiarlo o depender de la interfaz del primero.
 *
 * <p>Lo que se puede resolver desde el modelo se resuelve desde el modelo
 * —{@code Role.getDisplayName()}, {@code PriceFormatter}, {@code DateFormatter},
 * {@code plurals.xml}— y aqui solo queda lo que de verdad es una decision de
 * presentacion. Por eso no hay aqui ningun plural escrito a mano: los plurales
 * de la aplicacion viven en {@code res/values/plurals.xml}, que es donde el
 * idioma los puede conjugar bien.
 */
public final class FormatoDeDatos {

    private FormatoDeDatos() {
    }

    /**
     * "DNI 40918273", o solo el número si el tipo no está registrado.
     *
     * <p>Se omite el tipo antes que enseñar un "null 40918273", que es lo que
     * sale cuando el dato falta y se concatena sin mirar.
     *
     * <p>Devuelve {@code null} cuando no hay a quién describir —una reserva cuyo
     * cliente ya no existe— para que quien llama pueda enseñar su propio texto
     * de relleno. Es a propósito que no lo invente aquí: el relleno depende de
     * la pantalla, y "Sin registrar" en el perfil propio no significa lo mismo
     * que el cliente de una reserva que ya no está.
     */
    @Nullable
    public static CharSequence documento(@Nullable User usuario) {
        if (usuario == null) {
            return null;
        }
        String numero = usuario.getNumeroDocumento();
        if (numero == null || numero.isEmpty()) {
            return null;
        }
        String tipo = usuario.getTipoDocumento();
        return tipo == null || tipo.isEmpty() ? numero : tipo + " " + numero;
    }

    /**
     * Nombre del servicio a partir de su identificador.
     *
     * <p>Los servicios adicionales de una reserva se guardan por identificador
     * (regla 8: el nombre vive en el catálogo, no en la reserva), así que
     * cualquier pantalla que los liste tiene que resolverlo. Cuando el
     * identificador no está en el catálogo se devuelve {@code porDefecto} y no
     * una cadena vacía: un hueco en una lista de cobros se lee como un fallo de
     * la aplicación, y quien llama sabe qué decir en su lugar.
     */
    @NonNull
    public static String nombreServicio(@Nullable String serviceId, @NonNull String porDefecto) {
        Service servicio = serviceId == null ? null : ServiceLocator.hoteles().servicio(serviceId);
        return servicio != null ? servicio.getName() : porDefecto;
    }
}
