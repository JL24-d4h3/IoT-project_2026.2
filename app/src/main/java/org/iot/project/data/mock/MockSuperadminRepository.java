package org.iot.project.data.mock;

import androidx.annotation.NonNull;

import org.iot.project.core.ResultCallback;
import org.iot.project.core.SessionManager;
import org.iot.project.data.repository.SuperadminRepository;
import org.iot.project.models.Driver;
import org.iot.project.models.Hotel;
import org.iot.project.models.LogEntry;
import org.iot.project.models.ResumenSuperadmin;
import org.iot.project.models.Role;
import org.iot.project.models.User;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * Implementacion simulada del panel del superadministrador (§47).
 *
 * <p>Escribe en la misma bitacora que el resto de la aplicacion, asi que todo lo
 * que se hace desde aqui queda al lado de lo que hicieron los demas roles: la
 * auditoria que pide RF-118 es una sola, no una por rol.
 */
public class MockSuperadminRepository extends MockRepository implements SuperadminRepository {

    /** Cuantos movimientos enseña la portada; el resto quedan en la bitacora. */
    private static final int ULTIMOS_EN_PORTADA = 3;

    @Override
    public void resumen(@NonNull ResultCallback<ResumenSuperadmin> callback) {
        entregarDato(callback, () -> {
            int usuariosActivos = 0;
            for (User usuario : MockData.USUARIOS) {
                if (usuario.isActivo()) {
                    usuariosActivos++;
                }
            }

            int conductoresHabilitados = 0;
            for (Driver conductor : MockData.CONDUCTORES) {
                if (conductor.isHabilitado()) {
                    conductoresHabilitados++;
                }
            }

            int hotelesPublicados = 0;
            int sinAdministrador = 0;
            for (Hotel hotel : MockData.HOTELES) {
                if (hotel.isPublicado()) {
                    hotelesPublicados++;
                }
                if (hotel.getAdministradorId() == null) {
                    sinAdministrador++;
                }
            }

            return new ResumenSuperadmin(usuariosActivos, MockData.USUARIOS.size(),
                    conductoresHabilitados, MockData.CONDUCTORES.size(),
                    hotelesPublicados, MockData.HOTELES.size(), sinAdministrador,
                    ultimos(ULTIMOS_EN_PORTADA));
        }, "No pudimos cargar el resumen de la plataforma.");
    }

    @Override
    public void usuarios(@NonNull ResultCallback<List<User>> callback) {
        entregarLista(callback, () -> {
            List<User> ordenados = new ArrayList<>(MockData.USUARIOS);
            // Por rol y luego por nombre: es como se busca una cuenta, y deja
            // los superadministradores —que no se pueden tocar— al final.
            ordenados.sort(Comparator
                    .comparingInt((User u) -> u.getRol().ordinal())
                    .thenComparing(User::getNombreCompleto));
            return ordenados;
        });
    }

    @Override
    public void cambiarActivo(@NonNull String usuarioId, boolean activo,
                              @NonNull ResultCallback<User> callback) {
        ejecutar(callback, () -> {
            User usuario = exigirUsuario(usuarioId);
            if (!activo && !usuario.esDesactivable()) {
                throw new IllegalStateException("Un superadministrador no se puede desactivar:"
                        + " la plataforma se quedaría sin quien la administre.");
            }
            if (usuario.isActivo() == activo) {
                return usuario;
            }
            usuario.setActivo(activo);
            registrar(activo ? LogEntry.Evento.ACTIVACION : LogEntry.Evento.DESACTIVACION,
                    usuario.getNombreCompleto() + (activo
                            ? " fue activado."
                            : " fue desactivado y ya no puede entrar."));
            return usuario;
        });
    }

    @Override
    public void conductores(@NonNull ResultCallback<List<Driver>> callback) {
        entregarLista(callback, () -> {
            List<Driver> ordenados = new ArrayList<>(MockData.CONDUCTORES);
            // Los pendientes primero (RF-077): son los unicos sobre los que hay
            // algo que decidir, y es lo que el superadministrador viene a hacer.
            ordenados.sort(Comparator
                    .comparing(Driver::isHabilitado)
                    .thenComparing(Driver::getNombreCompleto));
            return ordenados;
        });
    }

    @Override
    public void habilitarConductor(@NonNull String conductorId, boolean habilitado,
                                   @NonNull ResultCallback<Driver> callback) {
        ejecutar(callback, () -> {
            Driver conductor = MockData.conductor(conductorId);
            if (conductor == null) {
                throw new IllegalArgumentException("No encontramos ese conductor.");
            }
            if (conductor.isHabilitado() == habilitado) {
                return conductor;
            }
            conductor.setHabilitado(habilitado);
            registrar(LogEntry.Evento.APROBACION, conductor.getNombreCompleto()
                    + (habilitado ? " fue habilitado para prestar servicios."
                                  : " dejó de estar habilitado."));
            return conductor;
        });
    }

    @Override
    public void hoteles(@NonNull ResultCallback<List<Hotel>> callback) {
        entregarLista(callback, () -> {
            List<Hotel> ordenados = new ArrayList<>(MockData.HOTELES);
            // Sin publicar primero: son los que esperan una accion del
            // superadministrador; los demas ya funcionan solos.
            ordenados.sort(Comparator
                    .comparing(Hotel::isPublicado)
                    .thenComparing(Hotel::getNombre));
            return ordenados;
        });
    }

    @Override
    public void administradores(@NonNull ResultCallback<List<User>> callback) {
        entregarLista(callback, () -> {
            List<User> administradores = new ArrayList<>();
            for (User usuario : MockData.USUARIOS) {
                if (usuario.getRol() == Role.ADMIN_HOTEL && usuario.isActivo()) {
                    administradores.add(usuario);
                }
            }
            administradores.sort(Comparator.comparing(User::getNombreCompleto));
            return administradores;
        });
    }

    @Override
    public void registrarHotel(@NonNull Hotel borrador, @NonNull ResultCallback<Hotel> callback) {
        ejecutar(callback, () -> {
            String nombre = limpiar(borrador.getNombre());
            if (nombre.isEmpty()) {
                throw new IllegalArgumentException("Ponle un nombre al hotel.");
            }
            if (limpiar(borrador.getCiudad()).isEmpty()) {
                throw new IllegalArgumentException("Indica en qué ciudad está el hotel.");
            }
            if (limpiar(borrador.getDistrito()).isEmpty()) {
                throw new IllegalArgumentException("Indica el distrito del hotel.");
            }
            // Un hotel sin coordenadas no se puede enseñar en el plano de
            // seguimiento del taxi (RF-084), asi que no se admite a medias.
            if (borrador.getLatitud() == 0d && borrador.getLongitud() == 0d) {
                throw new IllegalArgumentException("Indica las coordenadas del hotel.");
            }

            Hotel hotel = new Hotel(MockData.idNuevo("H"), nombre,
                    limpiar(borrador.getDistrito()), limpiar(borrador.getCiudad()));
            hotel.setDireccion(borrador.getDireccion());
            hotel.setUbicacion(borrador.getLatitud(), borrador.getLongitud());
            // Nace sin publicar y sin administrador (RF-007, RF-008): los dos
            // pasos que faltan son justamente los que vienen despues.
            MockData.HOTELES.add(hotel);

            registrar(LogEntry.Evento.ACCION_ADMINISTRATIVA,
                    "Se registró el hotel " + hotel.getNombre()
                            + " en " + hotel.getCiudad() + ".");
            return hotel;
        });
    }

    @Override
    public void asignarAdministrador(@NonNull String hotelId, @NonNull String usuarioId,
                                     @NonNull ResultCallback<Hotel> callback) {
        ejecutar(callback, () -> {
            Hotel hotel = exigirHotel(hotelId);
            User administrador = exigirUsuario(usuarioId);
            if (administrador.getRol() != Role.ADMIN_HOTEL) {
                throw new IllegalArgumentException(
                        administrador.getNombreCompleto() + " no es administrador de hotel.");
            }
            if (!administrador.isActivo()) {
                throw new IllegalStateException(
                        administrador.getNombreCompleto() + " tiene la cuenta desactivada.");
            }

            hotel.setAdministradorId(administrador.getId());
            registrar(LogEntry.Evento.ACCION_ADMINISTRATIVA,
                    detalleDeAsignacion(hotel, administrador));
            return hotel;
        });
    }

    /**
     * El movimiento de la asignacion, con aviso si ese administrador ya llevaba
     * otro hotel.
     *
     * <p>El aviso va en el detalle y no en un error porque no lo es: el modelo
     * admite que alguien lleve dos, y quien decide si eso es un problema es el
     * superadministrador. Lo que no puede es no enterarse.
     */
    private static String detalleDeAsignacion(Hotel hotel, User administrador) {
        int otros = 0;
        for (Hotel otro : MockData.HOTELES) {
            if (otro != hotel && administrador.getId().equals(otro.getAdministradorId())) {
                otros++;
            }
        }
        String detalle = administrador.getNombreCompleto() + " quedó a cargo de "
                + hotel.getNombre() + ".";
        if (otros > 0) {
            detalle += " Ya administraba " + otros + " hotel" + (otros == 1 ? "." : "es.");
        }
        return detalle;
    }

    @Override
    public void bitacora(@NonNull ResultCallback<List<LogEntry>> callback) {
        entregarLista(callback, MockSuperadminRepository::ordenadosDeLaBitacora);
    }

    // ------------------------------------------------------------------
    //  Apoyo
    // ------------------------------------------------------------------

    private static List<LogEntry> ultimos(int cuantos) {
        List<LogEntry> eventos = ordenadosDeLaBitacora();
        return eventos.size() <= cuantos ? eventos : new ArrayList<>(eventos.subList(0, cuantos));
    }

    /** Una lista nueva con los mas recientes primero. */
    private static List<LogEntry> ordenadosDeLaBitacora() {
        List<LogEntry> eventos = new ArrayList<>(MockData.BITACORA);
        eventos.sort(Comparator.comparing(LogEntry::getTimestamp).reversed());
        return eventos;
    }

    private static User exigirUsuario(String usuarioId) {
        User usuario = MockData.usuario(usuarioId);
        if (usuario == null) {
            throw new IllegalArgumentException("No encontramos esa cuenta.");
        }
        return usuario;
    }

    private static Hotel exigirHotel(String hotelId) {
        Hotel hotel = MockData.hotel(hotelId);
        if (hotel == null) {
            throw new IllegalArgumentException("No encontramos ese hotel.");
        }
        return hotel;
    }

    /** Escribe un movimiento en la bitacora a nombre de quien lo hizo. */
    private static void registrar(LogEntry.Evento evento, String detalle) {
        User autor = MockData.usuario(SessionManager.getUsuarioIdSeguro());
        MockData.BITACORA.add(new LogEntry(LocalDateTime.now(),
                autor != null ? autor.getNombreCompleto() : "Superadministrador",
                evento, detalle));
    }

    /** Recorta espacios y trata el nulo como vacio. */
    private static String limpiar(String texto) {
        return texto == null ? "" : texto.trim();
    }
}
