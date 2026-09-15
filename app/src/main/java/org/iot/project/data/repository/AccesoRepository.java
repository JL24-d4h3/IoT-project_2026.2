package org.iot.project.data.repository;

import androidx.annotation.NonNull;

import org.iot.project.core.ResultCallback;
import org.iot.project.models.Cuenta;
import org.iot.project.models.User;

import java.util.List;

/**
 * Acceso a la aplicacion: identificar una cuenta, crear una y recuperar la
 * contrasena.
 *
 * <p>Ningun metodo comprueba una contrasena, y no es un olvido. Guardar
 * contrasenas obligaria a inventar un almacen de secretos en una entrega que es
 * solo front end, justo lo que RC-042 y RT-038 prohiben. Lo que si es real es
 * lo que viene despues: identificar el rol y dar acceso unicamente a lo que ese
 * rol puede hacer (RF-004).
 */
public interface AccesoRepository {

    /**
     * Busca la cuenta de un correo, sea de un usuario o de un conductor.
     *
     * <p>Falla si el correo no corresponde a ninguna cuenta y tambien si la
     * cuenta existe pero esta deshabilitada (RF-009): en ese caso el mensaje
     * explica por que, en vez de decir que no existe.
     */
    void cuentaPorEmail(@NonNull String email, @NonNull ResultCallback<Cuenta> callback);

    /**
     * Una cuenta de ejemplo por rol, para entrar sin escribir un correo.
     *
     * <p>La pantalla de acceso no puede leer los correos de los datos simulados
     * por su cuenta: los simulados no se tocan desde las pantallas (§49, reglas
     * 33 a 35). Los pide aqui como pediria cualquier otro dato.
     *
     * <p>Elegir una de estas cuentas no salta ninguna comprobacion: rellena el
     * formulario y lo envia, de modo que una cuenta deshabilitada sigue sin
     * entrar aunque se elija desde aqui (RF-009).
     */
    void cuentasDeDemostracion(@NonNull ResultCallback<List<Cuenta>> callback);

    /**
     * Alta de un cliente desde la aplicacion movil (RF-001).
     *
     * <p>El cliente queda habilitado al terminar el registro, sin intervencion
     * de nadie (RF-002). El borrador no lleva contrasena: lo que se escriba en
     * ese campo se valida en la pantalla y no llega hasta aqui.
     */
    void registrar(@NonNull User borrador, @NonNull ResultCallback<Cuenta> callback);

    /**
     * Recuperacion de contrasena.
     *
     * <p>Devuelve el correo al que se habria enviado el enlace. No informa de si
     * ese correo tiene cuenta: la respuesta es la misma para todos, para que
     * este formulario no sirva para averiguar quien esta registrado.
     */
    void recuperarContrasena(@NonNull String email, @NonNull ResultCallback<String> callback);
}
