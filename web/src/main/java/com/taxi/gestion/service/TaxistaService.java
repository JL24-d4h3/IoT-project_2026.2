package com.taxi.gestion.service;

import com.taxi.gestion.domain.ServicioTaxi;
import com.taxi.gestion.domain.Taxista;
import com.taxi.gestion.domain.Valoracion;
import com.taxi.gestion.domain.enums.Disponibilidad;
import com.taxi.gestion.domain.enums.EstadoHabilitacion;
import com.taxi.gestion.domain.enums.EstadoServicio;
import com.taxi.gestion.dto.request.ActualizarTaxistaRequest;
import com.taxi.gestion.dto.request.RegistroTaxistaRequest;
import com.taxi.gestion.dto.request.UbicacionRequest;
import com.taxi.gestion.dto.request.ValoracionRequest;
import com.taxi.gestion.dto.response.ValoracionResponse;
import com.taxi.gestion.exception.ConflictoEstadoException;
import com.taxi.gestion.exception.CredencialesInvalidasException;
import com.taxi.gestion.exception.CuentaNoOperativaException;
import com.taxi.gestion.exception.RecursoDuplicadoException;
import com.taxi.gestion.exception.RecursoNoEncontradoException;
import com.taxi.gestion.exception.ReglaNegocioException;
import com.taxi.gestion.repository.ServicioTaxiRepository;
import com.taxi.gestion.repository.TaxistaRepository;
import com.taxi.gestion.repository.ValoracionRepository;
import com.taxi.gestion.storage.AlmacenamientoFotos;
import com.taxi.gestion.storage.ValidadorImagen;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.Period;
import java.util.List;

/** Reglas de negocio sobre taxistas: registro, habilitacion, ubicacion y reputacion. */
@Service
@RequiredArgsConstructor
@Slf4j
public class TaxistaService {

    private static final int EDAD_MINIMA = 18;

    private final TaxistaRepository taxistaRepository;
    private final ServicioTaxiRepository servicioRepository;
    private final ValoracionRepository valoracionRepository;
    private final PasswordEncoder passwordEncoder;
    private final AlmacenamientoFotos almacenamientoFotos;
    private final ValidadorImagen validadorImagen;

    private static final String CARPETA_FOTOS_TAXISTA = "taxistas";
    private static final String CARPETA_FOTOS_VEHICULO = "vehiculos";

    // ------------------------------------------------------------------
    // Registro
    // ------------------------------------------------------------------

    /**
     * Autoregistro del taxista. Queda PENDIENTE hasta que el Superadmin lo
     * habilite; la cuenta nace activa para que pueda iniciar sesion y
     * completar sus datos.
     */
    @Transactional
    public Taxista registrar(RegistroTaxistaRequest req) {
        validarUnicidad(req);
        validarEdad(req.getFechaNacimiento());

        // Las fotos son obligatorias y se suben solo cuando todo lo demas ya es valido,
        // para no dejar archivos huerfanos si el registro se rechaza por un duplicado.
        validadorImagen.validar(req.getFotoPerfil(), "La foto del taxista");
        validadorImagen.validar(req.getFotoVehiculo(), "La foto del vehiculo");
        String fotoUrl = almacenamientoFotos.guardar(req.getFotoPerfil(), CARPETA_FOTOS_TAXISTA);
        String fotoVehiculoUrl = almacenamientoFotos.guardar(req.getFotoVehiculo(), CARPETA_FOTOS_VEHICULO);

        Taxista taxista = Taxista.builder()
                .nombres(req.getNombres().trim())
                .apellidos(req.getApellidos().trim())
                .tipoDocumento(req.getTipoDocumento())
                .numeroDocumento(req.getNumeroDocumento().trim().toUpperCase())
                .fechaNacimiento(req.getFechaNacimiento())
                .correo(req.getCorreo().trim().toLowerCase())
                .telefono(req.getTelefono().trim())
                // BCrypt: la contrasena en claro no se persiste ni se registra en logs
                .passwordHash(passwordEncoder.encode(req.getPassword()))
                .domicilio(req.getDomicilio().trim())
                .fotoUrl(fotoUrl)
                .placaVehiculo(req.getPlacaVehiculo().trim().toUpperCase())
                .fotoVehiculoUrl(fotoVehiculoUrl)
                .estadoHabilitacion(EstadoHabilitacion.PENDIENTE)
                .cuentaActiva(Boolean.TRUE)
                .disponibilidad(Disponibilidad.DISPONIBLE)
                .build();

        Taxista guardado = taxistaRepository.save(taxista);
        log.info("Taxista registrado id={} placa={}", guardado.getId(), guardado.getPlacaVehiculo());
        return guardado;
    }

    private void validarUnicidad(RegistroTaxistaRequest req) {
        if (taxistaRepository.existsByCorreoIgnoreCase(req.getCorreo().trim())) {
            throw new RecursoDuplicadoException("Ya hay una cuenta registrada con ese correo");
        }
        if (taxistaRepository.existsByTipoDocumentoAndNumeroDocumento(
                req.getTipoDocumento(), req.getNumeroDocumento().trim().toUpperCase())) {
            throw new RecursoDuplicadoException("Ese documento de identidad ya esta registrado");
        }
        if (taxistaRepository.existsByPlacaVehiculoIgnoreCase(req.getPlacaVehiculo().trim())) {
            throw new RecursoDuplicadoException("Esa placa ya esta asociada a otro taxista");
        }
    }

    private void validarEdad(LocalDate nacimiento) {
        if (Period.between(nacimiento, LocalDate.now()).getYears() < EDAD_MINIMA) {
            throw new ReglaNegocioException("El taxista debe tener al menos "
                    + EDAD_MINIMA + " anios");
        }
    }

    // ------------------------------------------------------------------
    // Consultas
    // ------------------------------------------------------------------

    @Transactional(readOnly = true)
    public Taxista obtener(Long id) {
        return taxistaRepository.findById(id)
                .orElseThrow(() -> RecursoNoEncontradoException.taxista(id));
    }

    /**
     * Valida correo y contrasena para el login de la app movil.
     * Solo un taxista HABILITADO y con la cuenta activa puede iniciar sesion:
     * la aprobacion del Superadmin es justamente lo que habilita la integracion.
     */
    @Transactional(readOnly = true)
    public Taxista autenticar(String correo, String password) {
        Taxista taxista = taxistaRepository.findByCorreoIgnoreCase(correo.trim().toLowerCase())
                .orElseThrow(() -> new CredencialesInvalidasException(
                        "Correo o contrasena incorrectos"));

        if (!passwordEncoder.matches(password, taxista.getPasswordHash())) {
            throw new CredencialesInvalidasException("Correo o contrasena incorrectos");
        }
        if (taxista.getEstadoHabilitacion() != EstadoHabilitacion.HABILITADO) {
            throw new CuentaNoOperativaException("Tu cuenta todavia esta en revision. "
                    + "Te avisaremos por correo cuando quede habilitada.");
        }
        if (!Boolean.TRUE.equals(taxista.getCuentaActiva())) {
            throw new CuentaNoOperativaException("Tu cuenta esta desactivada. "
                    + "Comunicate con soporte para reactivarla.");
        }
        return taxista;
    }

    @Transactional(readOnly = true)
    public List<Taxista> listarTodos() {
        return taxistaRepository.findAllByOrderByFechaRegistroDesc();
    }

    /** Habilitados + cuenta activa + DISPONIBLE. */
    @Transactional(readOnly = true)
    public List<Taxista> listarDisponibles() {
        return taxistaRepository.buscarOperativosPorDisponibilidad(
                EstadoHabilitacion.HABILITADO, Disponibilidad.DISPONIBLE);
    }

    // ------------------------------------------------------------------
    // Acciones del Superadmin
    // ------------------------------------------------------------------

    /** Habilita o devuelve a PENDIENTE (rechazo) a un taxista. */
    @Transactional
    public Taxista cambiarHabilitacion(Long id, EstadoHabilitacion nuevoEstado) {
        Taxista taxista = taxistaRepository.findByIdParaActualizar(id)
                .orElseThrow(() -> RecursoNoEncontradoException.taxista(id));
        if (taxista.getEstadoHabilitacion() == nuevoEstado) {
            return taxista;
        }
        if (nuevoEstado == EstadoHabilitacion.PENDIENTE && tieneServicioEnCurso(id)) {
            throw new ConflictoEstadoException(
                    "No se puede retirar la habilitacion: el taxista tiene un servicio en curso");
        }
        taxista.setEstadoHabilitacion(nuevoEstado);
        log.info("Habilitacion del taxista {} cambiada a {}", id, nuevoEstado);
        return taxista;
    }

    /** Activa o desactiva la cuenta, sin tocar la habilitacion. */
    @Transactional
    public Taxista cambiarEstadoCuenta(Long id, boolean activa) {
        Taxista taxista = taxistaRepository.findByIdParaActualizar(id)
                .orElseThrow(() -> RecursoNoEncontradoException.taxista(id));
        boolean enCurso = tieneServicioEnCurso(id);
        if (!activa && enCurso) {
            throw new ConflictoEstadoException(
                    "No se puede desactivar la cuenta: el taxista tiene un servicio en curso");
        }
        taxista.setCuentaActiva(activa);
        // La reactivacion debe permitir volver a recibir ofertas cuando no existe viaje.
        taxista.setDisponibilidad(activa && !enCurso
                ? Disponibilidad.DISPONIBLE : Disponibilidad.OCUPADO);
        log.info("Cuenta del taxista {} {}", id, activa ? "activada" : "desactivada");
        return taxista;
    }

    private boolean tieneServicioEnCurso(Long taxistaId) {
        return servicioRepository.existsByTaxistaIdAndEstadoIn(taxistaId,
                List.of(EstadoServicio.ASIGNADO, EstadoServicio.EN_CAMINO,
                        EstadoServicio.EN_TRASLADO));
    }

    // ------------------------------------------------------------------
    // Operacion desde la app movil
    // ------------------------------------------------------------------

    /** Reporte periodico de posicion durante un servicio activo. */
    @Transactional
    public Taxista actualizarUbicacion(Long id, UbicacionRequest req) {
        Taxista taxista = obtener(id);
        if (!taxista.estaOperativo()) {
            throw new ConflictoEstadoException(
                    "El taxista no esta habilitado o su cuenta esta desactivada");
        }
        taxista.setLatitud(req.getLatitud());
        taxista.setLongitud(req.getLongitud());
        taxista.setUbicacionActualizadaEn(LocalDateTime.now());
        return taxista;
    }

    /** Actualizacion de los datos propios del taxista. */
    @Transactional
    public Taxista actualizarDatos(Long id, ActualizarTaxistaRequest req) {
        Taxista taxista = obtener(id);

        String nuevoCorreo = req.getCorreo().trim().toLowerCase();
        if (!nuevoCorreo.equalsIgnoreCase(taxista.getCorreo())
                && taxistaRepository.existsByCorreoIgnoreCase(nuevoCorreo)) {
            throw new RecursoDuplicadoException("Ese correo ya esta en uso por otra cuenta");
        }
        String nuevaPlaca = req.getPlacaVehiculo().trim().toUpperCase();
        if (!nuevaPlaca.equalsIgnoreCase(taxista.getPlacaVehiculo())
                && taxistaRepository.existsByPlacaVehiculoIgnoreCase(nuevaPlaca)) {
            throw new RecursoDuplicadoException("Esa placa ya esta asociada a otro taxista");
        }

        taxista.setNombres(req.getNombres().trim());
        taxista.setApellidos(req.getApellidos().trim());
        taxista.setCorreo(nuevoCorreo);
        taxista.setTelefono(req.getTelefono().trim());
        taxista.setDomicilio(req.getDomicilio().trim());
        taxista.setPlacaVehiculo(nuevaPlaca);

        if (req.getNuevaPassword() != null && !req.getNuevaPassword().isBlank()) {
            taxista.setPasswordHash(passwordEncoder.encode(req.getNuevaPassword()));
        }
        return taxista;
    }

    /** Reemplaza la foto del taxista, la del vehiculo, o ambas, subiendo los archivos nuevos. */
    @Transactional
    public Taxista actualizarFotos(Long id, MultipartFile fotoPerfil, MultipartFile fotoVehiculo) {
        boolean hayPerfil = fotoPerfil != null && !fotoPerfil.isEmpty();
        boolean hayVehiculo = fotoVehiculo != null && !fotoVehiculo.isEmpty();
        if (!hayPerfil && !hayVehiculo) {
            throw new ReglaNegocioException("Envia fotoPerfil, fotoVehiculo o ambas");
        }
        Taxista taxista = obtener(id);
        if (hayPerfil) {
            validadorImagen.validar(fotoPerfil, "La foto del taxista");
        }
        if (hayVehiculo) {
            validadorImagen.validar(fotoVehiculo, "La foto del vehiculo");
        }
        if (hayPerfil) {
            taxista.setFotoUrl(almacenamientoFotos.guardar(fotoPerfil, CARPETA_FOTOS_TAXISTA));
        }
        if (hayVehiculo) {
            taxista.setFotoVehiculoUrl(almacenamientoFotos.guardar(fotoVehiculo, CARPETA_FOTOS_VEHICULO));
        }
        return taxista;
    }

    // ------------------------------------------------------------------
    // Valoraciones
    // ------------------------------------------------------------------

    /** Registra la calificacion del cliente y recalcula el promedio del taxista. */
    @Transactional
    public ValoracionResponse registrarValoracion(Long taxistaId, ValoracionRequest req) {
        Taxista taxista = taxistaRepository.findByIdParaActualizar(taxistaId)
                .orElseThrow(() -> RecursoNoEncontradoException.taxista(taxistaId));

        ServicioTaxi servicio = null;
        if (req.getServicioId() != null) {
            servicio = servicioRepository.findById(req.getServicioId())
                    .orElseThrow(() -> RecursoNoEncontradoException.servicio(req.getServicioId()));

            if (servicio.getTaxista() == null
                    || !servicio.getTaxista().getId().equals(taxistaId)) {
                throw new ReglaNegocioException(
                        "El servicio " + servicio.getId() + " no fue atendido por ese taxista");
            }
            if (servicio.getEstado() != EstadoServicio.FINALIZADO) {
                throw new ConflictoEstadoException(
                        "Solo se puede valorar un servicio FINALIZADO");
            }
            if (valoracionRepository.existsByServicioId(servicio.getId())) {
                throw new ConflictoEstadoException("Ese servicio ya fue valorado");
            }
        }

        Valoracion valoracion = valoracionRepository.save(Valoracion.builder()
                .taxista(taxista)
                .servicio(servicio)
                .puntuacion(req.getPuntuacion())
                .comentario(vacioANull(req.getComentario()))
                .build());

        taxista.agregarValoracion(req.getPuntuacion());

        return new ValoracionResponse(
                valoracion.getId(),
                taxista.getId(),
                req.getPuntuacion(),
                taxista.getValoracionPromedio(),
                taxista.getTotalValoraciones());
    }

    private String vacioANull(String valor) {
        return (valor == null || valor.isBlank()) ? null : valor.trim();
    }
}
