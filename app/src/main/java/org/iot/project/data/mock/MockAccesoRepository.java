package org.iot.project.data.mock;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import org.iot.project.core.ResultCallback;
import org.iot.project.data.repository.AccesoRepository;
import org.iot.project.models.Cuenta;
import org.iot.project.models.Driver;
import org.iot.project.models.Role;
import org.iot.project.models.User;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Implementacion simulada del acceso.
 *
 * <p>Busca sobre los mismos datos que ve el resto de la aplicacion, asi que
 * entrar con una cuenta registrada desde la pantalla de registro da acceso a
 * esa identidad de verdad y no a una copia.
 */
public class MockAccesoRepository extends MockRepository implements AccesoRepository {

    @Override
    public void cuentaPorEmail(@NonNull String email, @NonNull ResultCallback<Cuenta> callback) {
        ejecutar(callback, () -> {
            String buscado = normalizar(email);
            if (buscado.isEmpty()) {
                throw new IllegalArgumentException("Escribe tu correo para continuar.");
            }

            for (User usuario : MockData.USUARIOS) {
                if (buscado.equals(normalizar(usuario.getEmail()))) {
                    exigirActivo(usuario);
                    return new Cuenta(usuario.getRol(), usuario.getId(),
                            usuario.getNombreCompleto(), usuario.getEmail());
                }
            }
            for (Driver conductor : MockData.CONDUCTORES) {
                if (buscado.equals(normalizar(conductor.getEmail()))) {
                    exigirHabilitado(conductor);
                    return new Cuenta(Role.CONDUCTOR, conductor.getId(),
                            conductor.getNombreCompleto(), conductor.getEmail());
                }
            }

            throw new IllegalArgumentException(
                    "No encontramos una cuenta con ese correo. Revísalo o crea una cuenta nueva.");
        });
    }

    /**
     * Alta de un cliente (RF-001).
     *
     * <p>El identificador se arma con el tamaNo de la lista, que solo crece:
     * dentro de una misma sesion dos altas nunca comparten numero.
     */
    @Override
    public void registrar(@NonNull User borrador, @NonNull ResultCallback<Cuenta> callback) {
        ejecutar(callback, () -> {
            String nombres = exigirTexto(borrador.getNombres(), "Escribe tu nombre.");
            String apellidos = exigirTexto(borrador.getApellidos(), "Escribe tus apellidos.");
            String email = normalizar(borrador.getEmail());
            if (email.isEmpty()) {
                throw new IllegalArgumentException("Escribe tu correo.");
            }
            if (!email.contains("@") || email.startsWith("@") || email.endsWith("@")) {
                throw new IllegalArgumentException("Ese correo no parece válido.");
            }
            if (yaRegistrado(email)) {
                throw new IllegalArgumentException(
                        "Ya existe una cuenta con ese correo. Inicia sesión en lugar de registrarte.");
            }

            User nuevo = new User("U" + (MockData.USUARIOS.size() + 1),
                    nombres, apellidos, Role.CLIENTE);
            nuevo.setEmail(email);
            nuevo.setTelefono(textoONulo(borrador.getTelefono()));
            // RF-002: el cliente queda habilitado al completar el registro, sin
            // que nadie tenga que aprobarlo.
            nuevo.setActivo(true);
            MockData.USUARIOS.add(nuevo);

            return new Cuenta(nuevo.getRol(), nuevo.getId(),
                    nuevo.getNombreCompleto(), nuevo.getEmail());
        });
    }

    @Override
    public void recuperarContrasena(@NonNull String email,
                                    @NonNull ResultCallback<String> callback) {
        ejecutar(callback, () -> {
            String destino = email == null ? "" : email.trim();
            if (destino.isEmpty()) {
                throw new IllegalArgumentException("Escribe tu correo.");
            }
            if (!destino.contains("@")) {
                throw new IllegalArgumentException("Ese correo no parece válido.");
            }
            // A proposito no se comprueba si la cuenta existe: si el formulario
            // respondiera distinto segun el caso, cualquiera podria usarlo para
            // averiguar quien tiene cuenta.
            return destino;
        });
    }

    /**
     * Una cuenta de ejemplo por rol.
     *
     * <p>Va por {@link #ejecutar} y no por {@code entregarLista} a proposito: en
     * modo {@link org.iot.project.core.MockConfig.Modo#VACIO} los atajos
     * desaparecerian, y ese modo sirve para ver listas vacias, no para dejar la
     * aplicacion sin forma de entrar.
     *
     * <p>Se elige la primera cuenta activa de cada rol. Si un rol no tiene
     * ninguna, simplemente no aparece: es preferible a ofrecer un atajo que
     * sabemos que va a fallar.
     */
    @Override
    public void cuentasDeDemostracion(@NonNull ResultCallback<List<Cuenta>> callback) {
        ejecutar(callback, () -> {
            List<Cuenta> cuentas = new ArrayList<>();
            for (Role rol : Role.values()) {
                Cuenta ejemplo = primeraDeRol(rol);
                if (ejemplo != null) {
                    cuentas.add(ejemplo);
                }
            }
            return cuentas;
        });
    }

    @Nullable
    private Cuenta primeraDeRol(Role rol) {
        if (rol == Role.CONDUCTOR) {
            for (Driver conductor : MockData.CONDUCTORES) {
                if (conductor.isHabilitado()) {
                    return new Cuenta(rol, conductor.getId(),
                            conductor.getNombreCompleto(), conductor.getEmail());
                }
            }
            return null;
        }
        for (User usuario : MockData.USUARIOS) {
            if (usuario.getRol() == rol && usuario.isActivo()) {
                return new Cuenta(rol, usuario.getId(),
                        usuario.getNombreCompleto(), usuario.getEmail());
            }
        }
        return null;
    }

    // ------------------------------------------------------------------

    /** RF-009: una cuenta deshabilitada no accede a las funcionalidades protegidas. */
    private void exigirActivo(User usuario) {
        if (!usuario.isActivo()) {
            throw new IllegalStateException(
                    "Tu cuenta está deshabilitada. Escríbenos si crees que es un error.");
        }
    }

    /** RF-077, RT-014: el conductor espera la aprobacion del Superadmin. */
    private void exigirHabilitado(Driver conductor) {
        if (!conductor.isHabilitado()) {
            throw new IllegalStateException(
                    "Tu cuenta de conductor todavía no está habilitada. "
                            + "Un administrador tiene que aprobarla antes de que puedas entrar.");
        }
    }

    /** Un correo puede pertenecer a un usuario o a un conductor: hay que mirar los dos. */
    private boolean yaRegistrado(String email) {
        for (User usuario : MockData.USUARIOS) {
            if (email.equals(normalizar(usuario.getEmail()))) {
                return true;
            }
        }
        for (Driver conductor : MockData.CONDUCTORES) {
            if (email.equals(normalizar(conductor.getEmail()))) {
                return true;
            }
        }
        return false;
    }

    private static String exigirTexto(String valor, String mensajeSiFalta) {
        String limpio = valor == null ? "" : valor.trim();
        if (limpio.isEmpty()) {
            throw new IllegalArgumentException(mensajeSiFalta);
        }
        return limpio;
    }

    private static String textoONulo(String valor) {
        String limpio = valor == null ? "" : valor.trim();
        return limpio.isEmpty() ? null : limpio;
    }

    /** Los correos se comparan sin distinguir mayusculas ni espacios sobrantes. */
    private static String normalizar(String email) {
        return email == null ? "" : email.trim().toLowerCase(Locale.ROOT);
    }
}
