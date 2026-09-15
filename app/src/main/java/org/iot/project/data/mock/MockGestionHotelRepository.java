package org.iot.project.data.mock;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import org.iot.project.core.MockConfig;
import org.iot.project.core.ResultCallback;
import org.iot.project.core.ServiceLocator;
import org.iot.project.core.SessionManager;
import org.iot.project.data.repository.GestionHotelRepository;
import org.iot.project.models.Booking;
import org.iot.project.models.BookingStatus;
import org.iot.project.models.CargosDeReserva;
import org.iot.project.models.ClienteDeHotel;
import org.iot.project.models.Hotel;
import org.iot.project.models.HotelService;
import org.iot.project.models.IngresoPorServicio;
import org.iot.project.models.LogEntry;
import org.iot.project.models.NearbyPlace;
import org.iot.project.models.Periodicidad;
import org.iot.project.models.PeriodoDeVentas;
import org.iot.project.models.ReservaDeHotel;
import org.iot.project.models.ResumenHotel;
import org.iot.project.models.Room;
import org.iot.project.models.Service;
import org.iot.project.models.User;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Implementacion simulada de la gestion del hotel (§42 a §45).
 *
 * <p>Las reglas que la pantalla no puede garantizar viven aqui: el minimo de
 * cuatro fotografias (RF-013), los datos obligatorios de una habitacion
 * (RF-014 a RF-018), que el servicio salga del catalogo y no se lo invente el
 * administrador (regla 7) y que nadie gestione un hotel que no es el suyo
 * (RF-023).
 *
 * <p>Los cambios se aplican sobre los objetos de {@link MockData}, que son los
 * mismos que consulta el cliente. No hay copia: retirar una habitacion aqui la
 * retira de verdad de la busqueda, que es lo que hace creible el mock.
 */
public class MockGestionHotelRepository extends MockRepository
        implements GestionHotelRepository {

    @Override
    public void resumen(@NonNull String hotelId, @NonNull ResultCallback<ResumenHotel> callback) {
        entregarDato(callback, () -> {
            // exigirHotel ya comprueba RF-023: si el hotel no es el suyo, esto
            // lanza y el callback recibe el error en vez de un resumen ajeno.
            Hotel hotel = exigirHotel(hotelId);

            // El modo VACIO vacia las listas, no el hotel: el hotel existe, lo
            // que no hay es nada dentro. Sin esta rama el tablero enseñaria las
            // estadias reales y el estado "sin huespedes" de la portada seria
            // imposible de ver, que es justo lo que ese modo sirve para
            // comprobar.
            if (MockConfig.getModo() == MockConfig.Modo.VACIO) {
                return new ResumenHotel(hotel,
                        Collections.<ReservaDeHotel>emptyList(),
                        Collections.<IngresoPorServicio>emptyList());
            }

            return new ResumenHotel(hotel, estadiasEnCurso(hotel), calcularIngresos(hotelId));
        }, "No pudimos cargar el resumen de tu hotel.");
    }

    @Override
    public void reservas(@NonNull String hotelId,
                         @NonNull ResultCallback<List<ReservaDeHotel>> callback) {
        entregarLista(callback, () -> {
            Hotel hotel = exigirHotel(hotelId);
            List<ReservaDeHotel> reservas = new ArrayList<>();
            for (Booking reserva : MockData.RESERVAS) {
                if (reserva.getHotelId().equals(hotel.getId())) {
                    reservas.add(MockData.detallar(reserva));
                }
            }
            // La mas reciente primero, por fecha de entrada y no por la de
            // creacion: lo que el administrador mira es quien llega ahora.
            reservas.sort(Comparator.comparing(
                    (ReservaDeHotel reserva) -> reserva.getReserva().getFechaEntrada()).reversed());
            return reservas;
        });
    }

    @Override
    public void reserva(@NonNull String hotelId, @NonNull String bookingId,
                        @NonNull ResultCallback<ReservaDeHotel> callback) {
        entregarDato(callback, () -> {
            Hotel hotel = exigirHotel(hotelId);
            Booking reserva = MockData.reserva(bookingId);
            if (reserva == null || !reserva.getHotelId().equals(hotelId)) {
                // El mismo mensaje para "no existe" y para "es de otro hotel":
                // distinguirlos dejaria averiguar, probando identificadores,
                // que reservas tiene la competencia.
                throw new IllegalArgumentException("No encontramos esta reserva en tu hotel.");
            }
            return MockData.detallar(reserva);
        }, "No pudimos cargar esta reserva.");
    }

    @Override
    public void clientes(@NonNull String hotelId,
                         @NonNull ResultCallback<List<ClienteDeHotel>> callback) {
        entregarLista(callback, () -> {
            Hotel hotel = exigirHotel(hotelId);

            // Se agrupa primero y se resume despues, en dos pasos: resumir
            // mientras se recorre obligaria a buscar en cada vuelta si el
            // cliente ya estaba, que es justo lo que hace el mapa.
            Map<String, List<Booking>> porCliente = new LinkedHashMap<>();
            for (Booking reserva : MockData.RESERVAS) {
                if (!reserva.getHotelId().equals(hotel.getId())
                        || !cuentaComoEstancia(reserva)) {
                    continue;
                }
                List<Booking> suyas = porCliente.get(reserva.getClienteId());
                if (suyas == null) {
                    suyas = new ArrayList<>();
                    porCliente.put(reserva.getClienteId(), suyas);
                }
                suyas.add(reserva);
            }

            List<ClienteDeHotel> clientes = new ArrayList<>();
            for (Map.Entry<String, List<Booking>> entrada : porCliente.entrySet()) {
                User cliente = MockData.usuario(entrada.getKey());
                if (cliente == null) {
                    // Una reserva cuyo cliente ya no existe se salta: la fila se
                    // identifica por un nombre, y sin él no hay fila que pintar.
                    continue;
                }
                clientes.add(resumir(cliente, entrada.getValue()));
            }

            // Primero quien está alojado ahora, que es a quien hay que atender
            // si llama recepción; después, el que estuvo más recientemente.
            clientes.sort(Comparator
                    .comparingInt((ClienteDeHotel cliente) -> cliente.estaEnCurso() ? 0 : 1)
                    .thenComparing(Comparator.comparing(
                            ClienteDeHotel::getUltimaEstancia).reversed()));
            return clientes;
        });
    }

    /**
     * Si una reserva cuenta como estancia para la ficha del cliente.
     *
     * <p>Solo las que llegaron a ocupar una habitación. Una cancelada nunca fue
     * una estancia; una pendiente o confirmada a futuro todavía no lo es.
     */
    private static boolean cuentaComoEstancia(@NonNull Booking reserva) {
        return reserva.getEstado() == BookingStatus.ACTIVA
                || reserva.getEstado() == BookingStatus.FINALIZADA;
    }

    /** Resume todas las estancias de un cliente en este hotel. */
    @NonNull
    private static ClienteDeHotel resumir(@NonNull User cliente,
                                          @NonNull List<Booking> estancias) {
        double total = 0d;
        LocalDate ultima = null;
        boolean enCurso = false;

        for (Booking estancia : estancias) {
            total += estancia.getTotal();
            if (ultima == null || estancia.getFechaEntrada().isAfter(ultima)) {
                ultima = estancia.getFechaEntrada();
            }
            enCurso = enCurso || estancia.getEstado() == BookingStatus.ACTIVA;
        }

        // ultima no puede quedar nula: la lista solo llega aquí con al menos una
        // estancia, y todas tienen fecha de entrada.
        return new ClienteDeHotel(cliente, estancias.size(), total,
                ultima != null ? ultima : MockData.HOY, enCurso);
    }

    @Override
    public void cargos(@NonNull String hotelId,
                       @NonNull ResultCallback<List<CargosDeReserva>> callback) {
        entregarLista(callback, () -> {
            Hotel hotel = exigirHotel(hotelId);
            List<CargosDeReserva> agrupados = new ArrayList<>();
            for (Booking reserva : MockData.RESERVAS) {
                if (!reserva.getHotelId().equals(hotel.getId())
                        || reserva.getCargos().isEmpty()) {
                    continue;
                }
                agrupados.add(new CargosDeReserva(MockData.detallar(reserva),
                        reserva.getCargos()));
            }

            // La estadia mas reciente primero, por la misma razon que en la
            // lista de reservas: lo que el administrador acaba de cobrar es lo
            // que viene a mirar.
            agrupados.sort(Comparator.comparing(
                    (CargosDeReserva grupo) -> grupo.getEstadia().getReserva().getFechaEntrada())
                    .reversed());
            return agrupados;
        });
    }

    @Override
    public void habitaciones(@NonNull String hotelId,
                             @NonNull ResultCallback<List<Room>> callback) {
        entregarLista(callback, () -> {
            Hotel hotel = exigirHotel(hotelId);
            List<Room> habitaciones = new ArrayList<>(hotel.getHabitaciones());
            // Las disponibles primero y, dentro de cada grupo, de barata a
            // cara: lo que el administrador revisa a diario es lo que esta a
            // la venta y a que precio.
            habitaciones.sort(Comparator
                    .comparing(Room::isDisponible).reversed()
                    .thenComparing(Room::getPrecioNoche));
            return habitaciones;
        });
    }

    @Override
    public void guardarHabitacion(@NonNull Room borrador,
                                  @NonNull ResultCallback<Room> callback) {
        ejecutar(callback, () -> {
            Hotel hotel = exigirHotel(borrador.getHotelId());
            validarHabitacion(borrador);

            // Sin identificador es una habitacion nueva: quien la registra no
            // tiene por que saber con que nombre la guarda el hotel.
            Room existente = borrador.getId() == null
                    ? null : habitacion(hotel, borrador.getId());
            if (existente == null) {
                Room nueva = new Room(idHabitacionNueva(hotel), hotel.getId(),
                        borrador.getTipo(), borrador.getPrecioNoche());
                copiarDatos(nueva, borrador);
                hotel.addHabitacion(nueva);
                registrar("Registró la habitación " + nueva.getNumero()
                        + " del " + hotel.getNombre() + ".");
                return nueva;
            }
            copiarDatos(existente, borrador);
            registrar("Actualizó la habitación " + existente.getNumero()
                    + " del " + hotel.getNombre() + ".");
            return existente;
        });
    }

    @Override
    public void cambiarDisponibilidad(@NonNull String hotelId, @NonNull String roomId,
                                      boolean disponible, @NonNull ResultCallback<Room> callback) {
        ejecutar(callback, () -> {
            Hotel hotel = exigirHotel(hotelId);
            Room habitacion = habitacion(hotel, roomId);
            if (habitacion == null) {
                throw new IllegalArgumentException("No encontramos esa habitación.");
            }
            habitacion.setDisponible(disponible);
            registrar((disponible ? "Volvió a poner a la venta la habitación "
                    : "Retiró de la venta la habitación ") + habitacion.getNumero()
                    + " del " + hotel.getNombre() + ".");
            return habitacion;
        });
    }

    @Override
    public void asignarServicio(@NonNull HotelService asignacion,
                                @NonNull ResultCallback<Hotel> callback) {
        ejecutar(callback, () -> {
            Hotel hotel = exigirHotel(asignacion.getHotelId());
            Service catalogo = ServiceLocator.hoteles().servicio(asignacion.getServiceId());
            if (catalogo == null) {
                // Regla 7: el administrador elige del catalogo, no escribe un
                // nombre. Un servicio que no esta en el catalogo es un dato
                // inventado, y se rechaza en vez de aceptarlo a ciegas.
                throw new IllegalArgumentException(
                        "Ese servicio no está en el catálogo de la aplicación.");
            }
            validarServicio(asignacion);
            hotel.addServicio(asignacion);
            registrar("Configuró el servicio " + catalogo.getName() + " del "
                    + hotel.getNombre() + (asignacion.isIncluded()
                    ? " como incluido." : " con un costo adicional."));
            return hotel;
        });
    }

    @Override
    public void quitarServicio(@NonNull String hotelId, @NonNull String serviceId,
                               @NonNull ResultCallback<Hotel> callback) {
        ejecutar(callback, () -> {
            Hotel hotel = exigirHotel(hotelId);
            if (!hotel.quitarServicio(serviceId)) {
                throw new IllegalArgumentException(
                        "Ese servicio no estaba configurado en el hotel.");
            }
            Service catalogo = ServiceLocator.hoteles().servicio(serviceId);
            registrar("Quitó el servicio "
                    + (catalogo != null ? catalogo.getName() : serviceId)
                    + " del " + hotel.getNombre() + ".");
            return hotel;
        });
    }

    @Override
    public void actualizarDatos(@NonNull String hotelId, @NonNull String nombre,
                                @NonNull String descripcion, @NonNull String direccion,
                                double latitud, double longitud,
                                @NonNull ResultCallback<Hotel> callback) {
        ejecutar(callback, () -> {
            Hotel hotel = exigirHotel(hotelId);
            if (nombre.trim().isEmpty()) {
                throw new IllegalArgumentException("El hotel necesita un nombre.");
            }
            if (direccion.trim().isEmpty()) {
                throw new IllegalArgumentException("Indica la dirección del hotel.");
            }
            exigirCoordenada(latitud, longitud);

            hotel.setNombre(nombre.trim());
            hotel.setDescripcion(descripcion.trim());
            hotel.setDireccion(direccion.trim());
            hotel.setUbicacion(latitud, longitud);
            registrar("Actualizó los datos del " + hotel.getNombre() + ".");
            return hotel;
        });
    }

    @Override
    public void agregarFoto(@NonNull String hotelId, @NonNull String url,
                            @NonNull ResultCallback<Hotel> callback) {
        ejecutar(callback, () -> {
            Hotel hotel = exigirHotel(hotelId);
            if (url.trim().isEmpty()) {
                throw new IllegalArgumentException("La dirección de la fotografía está vacía.");
            }
            hotel.addFoto(url.trim());
            registrar("Agregó una fotografía al " + hotel.getNombre() + ".");
            return hotel;
        });
    }

    @Override
    public void agregarLugarCercano(@NonNull String hotelId, @NonNull NearbyPlace lugar,
                                    @NonNull ResultCallback<Hotel> callback) {
        ejecutar(callback, () -> {
            Hotel hotel = exigirHotel(hotelId);
            if (lugar.getNombre().trim().isEmpty()) {
                throw new IllegalArgumentException("Indica el nombre del lugar.");
            }
            // Sin distancia no se puede ordenar ni decir si esta cerca, y el
            // dato es la razon de ser de la lista. Cero no vale como "no lo se":
            // significaria que el lugar esta dentro del hotel.
            if (lugar.getDistanciaKm() <= 0d) {
                throw new IllegalArgumentException("La distancia debe ser mayor que cero.");
            }
            hotel.addLugarCercano(lugar);
            registrar("Registró el lugar cercano " + lugar.getNombre().trim()
                    + " del " + hotel.getNombre() + ".");
            return hotel;
        });
    }

    @Override
    public void quitarFoto(@NonNull String hotelId, @NonNull String url,
                           @NonNull ResultCallback<Hotel> callback) {
        ejecutar(callback, () -> {
            Hotel hotel = exigirHotel(hotelId);
            if (!hotel.getFotos().contains(url)) {
                throw new IllegalArgumentException("Esa fotografía no está en el hotel.");
            }
            // RF-013: el minimo se comprueba antes de quitar. Un hotel en el
            // limite no puede quedarse en tres ni un momento, asi que para
            // cambiar una foto hay que agregar la nueva primero y quitar
            // despues la vieja. Es el orden que exige la regla, no una
            // limitacion de la pantalla.
            if (hotel.getFotos().size() - 1 < Hotel.MIN_FOTOS) {
                throw new IllegalStateException(
                        "El hotel necesita al menos " + Hotel.MIN_FOTOS
                                + " fotografías. Agrega otra antes de quitar esta.");
            }
            hotel.quitarFoto(url);
            registrar("Quitó una fotografía del " + hotel.getNombre() + ".");
            return hotel;
        });
    }

    @Override
    public void ventas(@NonNull String hotelId, @NonNull Periodicidad periodicidad,
                       @NonNull ResultCallback<List<PeriodoDeVentas>> callback) {
        entregarLista(callback, () -> {
            exigirHotel(hotelId);
            return PeriodoDeVentas.agrupar(periodicidad, generanIngreso(hotelId));
        });
    }

    /**
     * Las reservas del hotel que cuentan como venta.
     *
     * <p>Que reserva entra lo decide {@link #generaIngreso}, el mismo criterio
     * que el reporte de servicios, y no es una coincidencia: el desglose por
     * servicios tiene que sumar dentro del total de ventas, y con dos criterios
     * distintos el administrador acabaria viendo un desglose mayor que el total
     * del que forma parte.
     */
    @NonNull
    private static List<Booking> generanIngreso(@NonNull String hotelId) {
        List<Booking> reservas = new ArrayList<>();
        for (Booking reserva : MockData.RESERVAS) {
            if (reserva.getHotelId().equals(hotelId) && generaIngreso(reserva)) {
                reservas.add(reserva);
            }
        }
        return reservas;
    }

    @Override
    public void ingresosPorServicios(@NonNull String hotelId,
                                     @NonNull ResultCallback<List<IngresoPorServicio>> callback) {
        entregarLista(callback, () -> {
            exigirHotel(hotelId);
            return calcularIngresos(hotelId);
        });
    }

    /**
     * Arma el reporte de servicios adicionales de un hotel (RF-060, RF-061).
     *
     * <p>Lo comparten la portada y la pantalla de reportes. Se calcula una sola
     * vez y en un solo sitio para que el total de la portada y el desglose del
     * reporte no puedan discrepar.
     */
    @NonNull
    private static List<IngresoPorServicio> calcularIngresos(String hotelId) {
        // Se acumula por servicio conservando el orden de aparicion; el
        // orden final lo fija RF-061.
        Map<String, int[]> veces = new LinkedHashMap<>();
        Map<String, Double> montos = new LinkedHashMap<>();

        for (Booking reserva : MockData.RESERVAS) {
            if (!reserva.getHotelId().equals(hotelId) || !generaIngreso(reserva)) {
                continue;
            }
            for (HotelService servicio : reserva.getServiciosAdicionales()) {
                String id = servicio.getServiceId();
                int[] contador = veces.get(id);
                if (contador == null) {
                    veces.put(id, new int[]{1});
                    montos.put(id, servicio.getPrice());
                } else {
                    contador[0]++;
                    montos.put(id, montos.get(id) + servicio.getPrice());
                }
            }
        }

        List<IngresoPorServicio> reporte = new ArrayList<>();
        for (Map.Entry<String, int[]> entrada : veces.entrySet()) {
            Service catalogo = ServiceLocator.hoteles().servicio(entrada.getKey());
            reporte.add(new IngresoPorServicio(
                    entrada.getKey(),
                    catalogo != null ? catalogo.getName() : entrada.getKey(),
                    entrada.getValue()[0],
                    montos.get(entrada.getKey())));
        }

        // RF-061: de menor a mayor monto. Es el orden que pide el enunciado
        // y el contrario del que uno elegiria para mirar un ranking, asi
        // que se deja dicho para que no parezca un descuido.
        reporte.sort(Comparator.comparingDouble(IngresoPorServicio::getMontoTotal));
        return reporte;
    }

    /**
     * Las estadias en curso del hotel, con cliente y habitacion resueltos.
     *
     * <p>El cruce se hace aqui porque es aqui donde estan los tres conjuntos a
     * la vez. El nombre del cliente puede faltar si su cuenta se dio de baja;
     * en ese caso se dice que no esta disponible en vez de inventar un nombre,
     * porque la fila sigue siendo cierta —hay alguien alojado— aunque no
     * sepamos quien.
     */
    @NonNull
    private static List<ReservaDeHotel> estadiasEnCurso(Hotel hotel) {
        List<ReservaDeHotel> estadias = new ArrayList<>();
        for (Booking reserva : MockData.RESERVAS) {
            if (!reserva.getHotelId().equals(hotel.getId())
                    || reserva.getEstado() != BookingStatus.ACTIVA) {
                continue;
            }
            estadias.add(MockData.detallar(reserva));
        }

        // La que sale antes va primero: es el orden en que hay que atenderlas.
        estadias.sort(Comparator.comparing(estadia -> estadia.getReserva().getFechaSalida()));
        return estadias;
    }

    // ------------------------------------------------------------------
    //  Reglas de negocio
    // ------------------------------------------------------------------

    /**
     * Regla 20 (§18), RF-020: incluido y con precio son excluyentes.
     *
     * <p>La pantalla ya lo impide —solo pide el precio cuando el servicio se
     * cobra— pero la regla es del negocio y no del formulario. Aqui se comprueba
     * porque es lo unico que impide que un hotel acabe con un servicio marcado
     * como incluido y con precio a la vez: eso no es un detalle de estilo, se
     * traduce en un cobro al huesped por algo que su reserva dice que va
     * incluido.
     *
     * <p>Lo mismo al reves: un adicional sin precio es un servicio que nadie
     * puede cobrar, y en el reporte de ingresos apareceria como una fila de
     * cero sin explicacion.
     */
    private void validarServicio(HotelService asignacion) {
        if (asignacion.isIncluded()) {
            if (asignacion.getPrice() > 0d) {
                throw new IllegalArgumentException(
                        "Un servicio incluido no se cobra: quita el precio o márcalo como "
                                + "adicional.");
            }
            return;
        }
        if (asignacion.getPrice() <= 0d) {
            throw new IllegalArgumentException(
                    "Indica cuánto se cobra por este servicio adicional.");
        }
    }

    /** RF-014 a RF-018: sin tipo, capacidad ni area, la habitacion no se puede ofrecer. */
    private void validarHabitacion(Room habitacion) {
        if (habitacion.getTipo() == null || habitacion.getTipo().trim().isEmpty()) {
            throw new IllegalArgumentException("Indica el tipo de habitación.");
        }
        if (habitacion.getNumero() == null || habitacion.getNumero().trim().isEmpty()) {
            // El numero no lo pide ningun RF con nombre propio, pero es la
            // etiqueta con la que la habitacion aparece en la lista y en la
            // bitacora: sin el, la fila se queda sin titulo y el registro dice
            // "Registro la habitacion null". Es obligatorio por uso, no por
            // capricho.
            throw new IllegalArgumentException("Indica el número o nombre de la habitación.");
        }
        if (habitacion.getCapacidadAdultos() < 1) {
            throw new IllegalArgumentException("Una habitación aloja al menos un adulto.");
        }
        if (habitacion.getCapacidadNinos() < 0) {
            throw new IllegalArgumentException("La capacidad de niños no puede ser negativa.");
        }
        if (habitacion.getAreaM2() <= 0d) {
            throw new IllegalArgumentException("Indica el área de la habitación en m².");
        }
        if (habitacion.getPrecioNoche() <= 0d) {
            throw new IllegalArgumentException("El precio por noche debe ser mayor que cero.");
        }
    }

    private void exigirCoordenada(double latitud, double longitud) {
        if (latitud < -90d || latitud > 90d || longitud < -180d || longitud > 180d) {
            throw new IllegalArgumentException("Las coordenadas del hotel no son válidas.");
        }
        if (latitud == 0d && longitud == 0d) {
            // Null Island: casi siempre es un formulario que se dejo en blanco.
            // El taxi calcula distancias con esto (RF-099), asi que un cero
            // aqui no es inocente.
            throw new IllegalArgumentException("Indica la ubicación del hotel en el mapa.");
        }
    }

    /**
     * Si la reserva genera ingreso.
     *
     * <p>Una cancelada no facturo nada y una pendiente todavia no se pago
     * (RF-045: el pago es lo que confirma). Contarlas inflaria el reporte con
     * dinero que nadie pago, que es la peor clase de error en un reporte de
     * ingresos.
     */
    private static boolean generaIngreso(Booking reserva) {
        BookingStatus estado = reserva.getEstado();
        return estado == BookingStatus.CONFIRMADA
                || estado == BookingStatus.ACTIVA
                || estado == BookingStatus.FINALIZADA;
    }

    /** RF-023: se comprueba en cada operacion, no una vez al entrar. */
    private Hotel exigirHotel(String hotelId) {
        if (!SessionManager.puedeGestionar(hotelId)) {
            throw new IllegalStateException("Ese hotel no es el tuyo.");
        }
        Hotel hotel = MockData.hotel(hotelId);
        if (hotel == null) {
            throw new IllegalArgumentException("No encontramos ese hotel.");
        }
        return hotel;
    }

    // ------------------------------------------------------------------
    //  Auxiliares
    // ------------------------------------------------------------------

    @Nullable
    private static Room habitacion(Hotel hotel, String roomId) {
        for (Room habitacion : hotel.getHabitaciones()) {
            if (habitacion.getId().equals(roomId)) {
                return habitacion;
            }
        }
        return null;
    }

    /** Copia los campos editables. El identificador y el hotel no se tocan. */
    private static void copiarDatos(Room destino, Room origen) {
        destino.setTipo(origen.getTipo().trim());
        destino.setPrecioNoche(origen.getPrecioNoche());
        destino.withCapacidad(origen.getCapacidadAdultos(), origen.getCapacidadNinos())
                .withArea(origen.getAreaM2())
                .withUbicacion(origen.getPiso(), origen.getNumero());
    }

    /** Correlativo por hotel: "H1-R4", "H1-R5"... */
    private static String idHabitacionNueva(Hotel hotel) {
        return hotel.getId() + "-R" + (hotel.getHabitaciones().size() + 1);
    }

    private void registrar(String detalle) {
        User usuario = MockData.usuario(SessionManager.getUsuarioIdSeguro());
        MockData.BITACORA.add(new LogEntry(
                LocalDateTime.now(),
                usuario != null ? usuario.getNombreCompleto() : "Administrador",
                LogEntry.Evento.ACCION_ADMINISTRATIVA,
                detalle));
    }
}
