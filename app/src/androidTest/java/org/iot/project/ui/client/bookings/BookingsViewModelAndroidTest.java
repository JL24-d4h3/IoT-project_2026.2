package org.iot.project.ui.client.bookings;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;

import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;
import androidx.lifecycle.SavedStateHandle;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;

import org.iot.project.core.ResultCallback;
import org.iot.project.core.UiState;
import org.iot.project.data.repository.BookingRepository;
import org.iot.project.models.Booking;
import org.iot.project.models.BookingStatus;
import org.iot.project.models.Charge;
import org.iot.project.models.Cuenta;
import org.iot.project.models.Payment;
import org.iot.project.models.Review;
import org.iot.project.models.Role;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/** Consulta de reservas con fuentes en memoria: sin Activity, Firebase ni red. */
@RunWith(AndroidJUnit4.class)
public final class BookingsViewModelAndroidTest {
    private FakeRepositorio repositorio;
    private FakeSesion sesion;
    private SavedStateHandle guardado;
    private BookingsViewModel viewModel;

    @Before public void preparar() {
        enPrincipal(() -> {
            repositorio = new FakeRepositorio();
            sesion = new FakeSesion(cliente("cliente-1"));
            guardado = new SavedStateHandle();
            viewModel = nuevo(guardado);
        });
    }

    @After public void liberar() {
        enPrincipal(() -> { if (viewModel != null) viewModel.onCleared(); });
    }

    @Test public void ordenInicialEsSalidaDescendenteEnTodosLosEstados() {
        enPrincipal(() -> {
            viewModel.cargar();
            repositorio.ultima().exito(Arrays.asList(
                    reserva("proxima", "Álamo", 8, BookingStatus.CONFIRMADA),
                    reserva("historial", "Sol", 12, BookingStatus.FINALIZADA),
                    reserva("activa", "Mar", 4, BookingStatus.ACTIVA)));
            assertEquals(ConsultaReservasCliente.Orden.FECHA_SALIDA, viewModel.getOrden());
            assertFalse(viewModel.isAscendente());
            assertEquals("", viewModel.getBusqueda());
            assertNull(viewModel.getEstadoFiltrado());
            assertEquals(Arrays.asList("historial", "proxima", "activa"), ids(datos()));
            assertTrue(viewModel.tieneReservas());
        });
    }

    @Test public void buscadorOrdenYEstadoSonLocalesSinConsultarDeNuevo() {
        enPrincipal(() -> {
            viewModel.cargar();
            repositorio.ultima().exito(Arrays.asList(
                    reserva("1", "Álamo", 4, BookingStatus.ACTIVA),
                    reserva("2", "Alamo Sur", 8, BookingStatus.ACTIVA),
                    reserva("3", "Álamo Norte", 12, BookingStatus.CANCELADA)));
            int consultas = repositorio.consultas.size();
            viewModel.buscar("  ALAMO  ");
            viewModel.filtrarEstado(BookingStatus.ACTIVA);
            viewModel.ordenarPor(ConsultaReservasCliente.Orden.NOMBRE);
            viewModel.cambiarSentido(true);
            assertEquals(Arrays.asList("1", "2"), ids(datos()));
            assertEquals(consultas, repositorio.consultas.size());
            viewModel.buscar("RES-2");
            assertEquals(Collections.singletonList("2"), ids(datos()));
            assertEquals(consultas, repositorio.consultas.size());
        });
    }

    @Test public void criteriosSeMantienenYSeAplicanAlRefrescarDatos() {
        enPrincipal(() -> {
            viewModel.cargar();
            repositorio.ultima().exito(Collections.singletonList(
                    reserva("1", "Hotel Mar", 4, BookingStatus.ACTIVA)));
            viewModel.buscar("mar");
            viewModel.filtrarEstado(BookingStatus.ACTIVA);
            viewModel.cambiarSentido(true);
            viewModel.refrescarEnSilencio();
            repositorio.ultima().exito(Arrays.asList(
                    reserva("4", "Hotel Mar Nuevo", 12, BookingStatus.ACTIVA),
                    reserva("2", "Hotel Mar", 8, BookingStatus.ACTIVA),
                    reserva("3", "Hotel Mar", 4, BookingStatus.CANCELADA),
                    reserva("5", "Hotel Sol", 4, BookingStatus.ACTIVA)));
            assertEquals(Arrays.asList("2", "4"), ids(datos()));
            assertEquals("mar", viewModel.getBusqueda());
            assertEquals(BookingStatus.ACTIVA, viewModel.getEstadoFiltrado());
            assertTrue(viewModel.isAscendente());
        });
    }

    @Test public void savedStateRestauraBusquedaOrdenSentidoYEstadoDeLaMismaCuenta() {
        enPrincipal(() -> {
            viewModel.cargar();
            repositorio.ultima().exito(Collections.singletonList(
                    reserva("1", "Mar", 4, BookingStatus.CONFIRMADA)));
            viewModel.buscar("Mar");
            viewModel.ordenarPor(ConsultaReservasCliente.Orden.NOMBRE);
            viewModel.cambiarSentido(true);
            viewModel.filtrarEstado(BookingStatus.CONFIRMADA);
            Map<String, Object> copia = new HashMap<>();
            for (String clave : guardado.keys()) copia.put(clave, guardado.get(clave));
            viewModel.onCleared();
            viewModel = nuevo(new SavedStateHandle(copia));
            assertEquals("Mar", viewModel.getBusqueda());
            assertEquals(ConsultaReservasCliente.Orden.NOMBRE, viewModel.getOrden());
            assertTrue(viewModel.isAscendente());
            assertEquals(BookingStatus.CONFIRMADA, viewModel.getEstadoFiltrado());
            viewModel.cargar();
            repositorio.ultima().exito(Arrays.asList(
                    reserva("1", "Mar", 4, BookingStatus.CONFIRMADA),
                    reserva("2", "Sol", 8, BookingStatus.CONFIRMADA)));
            assertEquals(Collections.singletonList("1"), ids(datos()));
        });
    }

    @Test public void cambiarCuentaDescartaDatosRespuestasYPreferenciasDeLaAnterior() {
        enPrincipal(() -> {
            viewModel.cargar();
            Consulta vieja = repositorio.ultima();
            vieja.exito(Collections.singletonList(reserva("privada", "Mar", 8, BookingStatus.ACTIVA)));
            viewModel.buscar("Mar");
            viewModel.ordenarPor(ConsultaReservasCliente.Orden.NOMBRE);
            viewModel.cambiarSentido(true);
            viewModel.filtrarEstado(BookingStatus.ACTIVA);
            sesion.cambiar(cliente("cliente-2"));
            viewModel.cargar();
            assertEquals("", viewModel.getBusqueda());
            assertEquals(ConsultaReservasCliente.Orden.FECHA_SALIDA, viewModel.getOrden());
            assertFalse(viewModel.isAscendente());
            assertNull(viewModel.getEstadoFiltrado());
            assertFalse(viewModel.tieneReservas());
            assertNull(viewModel.getReservas().getValue().getData());
            assertEquals("cliente-2", repositorio.ultima().clienteId);
            UiState<List<Booking>> antes = viewModel.getReservas().getValue();
            vieja.exito(Collections.singletonList(reserva("privada", "Mar", 8, BookingStatus.ACTIVA)));
            vieja.error("Error anterior");
            assertSame(antes, viewModel.getReservas().getValue());
            repositorio.ultima().exito(Arrays.asList(
                    reserva("propia", "Sol", 4, BookingStatus.CONFIRMADA, "cliente-2"),
                    reserva("ajena", "Mar", 8, BookingStatus.ACTIVA, "cliente-1")));
            assertEquals(Collections.singletonList("propia"), ids(datos()));
        });
    }

    @Test public void cerrarSesionYVolverAlMismoUidNoRecuperaCallbacksAntiguos() {
        enPrincipal(() -> {
            viewModel.cargar();
            Consulta antigua = repositorio.ultima();
            antigua.exito(Collections.singletonList(reserva("privada", "Mar", 4, BookingStatus.ACTIVA)));
            viewModel.buscar("Mar");
            sesion.cambiar(null);
            assertFalse(viewModel.tieneReservas());
            assertEquals("", viewModel.getBusqueda());
            assertNull(viewModel.getReservas().getValue().getData());
            sesion.cambiar(cliente("cliente-1"));
            viewModel.cargar();
            UiState<List<Booking>> antes = viewModel.getReservas().getValue();
            antigua.exito(Collections.singletonList(reserva("privada", "Mar", 4, BookingStatus.ACTIVA)));
            assertSame(antes, viewModel.getReservas().getValue());
            repositorio.ultima().exito(Collections.singletonList(reserva("actual", "Sol", 8, BookingStatus.CONFIRMADA)));
            assertEquals(Collections.singletonList("actual"), ids(datos()));
        });
    }

    @Test public void suspenderDescartaUnaConsultaPendienteYLaReanudacionCargaLaActual() {
        enPrincipal(() -> {
            viewModel.cargar();
            Consulta antigua = repositorio.ultima();
            viewModel.suspender();
            UiState<List<Booking>> antes = viewModel.getReservas().getValue();
            antigua.exito(Collections.singletonList(reserva("vieja", "Mar", 4, BookingStatus.ACTIVA)));
            antigua.error("Consulta fuera de pantalla");
            assertSame(antes, viewModel.getReservas().getValue());
            viewModel.refrescarEnSilencio();
            repositorio.ultima().exito(Collections.singletonList(reserva("actual", "Sol", 8, BookingStatus.CONFIRMADA)));
            assertEquals(Collections.singletonList("actual"), ids(datos()));
        });
    }

    @Test public void sinCoincidenciasSeDistingueDeUnaCuentaSinReservas() {
        enPrincipal(() -> {
            viewModel.cargar();
            repositorio.ultima().exito(Collections.singletonList(reserva("1", "Mar", 4, BookingStatus.ACTIVA)));
            viewModel.buscar("sin coincidencias");
            assertTrue(viewModel.getReservas().getValue().isEmpty());
            assertTrue(viewModel.tieneReservas());
            viewModel.limpiarFiltros();
            assertEquals(Collections.singletonList("1"), ids(datos()));
            viewModel.refrescarEnSilencio();
            repositorio.ultima().exito(Collections.emptyList());
            assertTrue(viewModel.getReservas().getValue().isEmpty());
            assertFalse(viewModel.tieneReservas());
        });
    }

    @Test public void cadaEstadoSeFiltraSinPerderElOrdenElegido() {
        enPrincipal(() -> {
            viewModel.cargar();
            List<Booking> reservas = new ArrayList<>();
            for (BookingStatus estado : BookingStatus.values()) {
                reservas.add(reserva(estado.name() + "-A", "Sol", 4, estado));
                reservas.add(reserva(estado.name() + "-B", "Mar", 8, estado));
            }
            repositorio.ultima().exito(reservas);
            viewModel.ordenarPor(ConsultaReservasCliente.Orden.NOMBRE);
            viewModel.cambiarSentido(true);
            for (BookingStatus estado : BookingStatus.values()) {
                viewModel.filtrarEstado(estado);
                assertEquals(Arrays.asList(estado.name() + "-B", estado.name() + "-A"), ids(datos()));
            }
            viewModel.filtrarEstado(null);
            assertEquals(10, datos().size());
            assertEquals(1, repositorio.consultas.size());
        });
    }

    @Test public void otroRolNoPuedeConsultarLasReservasDelCliente() {
        enPrincipal(() -> {
            sesion.cambiar(new Cuenta(Role.ADMIN_HOTEL, "admin", "Admin", "admin@example.test", "hotel-1"));
            viewModel.cargar();
            assertTrue(repositorio.consultas.isEmpty());
            assertTrue(viewModel.getReservas().getValue().isError());
            assertFalse(viewModel.tieneReservas());
        });
    }

    private BookingsViewModel nuevo(SavedStateHandle estado) {
        return new BookingsViewModel(estado, repositorio, sesion, Booking::getHotelNombre);
    }

    private List<Booking> datos() {
        UiState<List<Booking>> actual = viewModel.getReservas().getValue();
        assertNotNull(actual);
        assertTrue(actual.isSuccess());
        return actual.requireData();
    }

    private static List<String> ids(List<Booking> reservas) {
        List<String> ids = new ArrayList<>();
        for (Booking reserva : reservas) ids.add(reserva.getId());
        return ids;
    }

    private static Cuenta cliente(String uid) { return new Cuenta(Role.CLIENTE, uid, "Cliente", uid + "@example.test"); }

    private static Booking reserva(String id, String nombre, int salida, BookingStatus estado) {
        return reserva(id, nombre, salida, estado, "cliente-1");
    }

    private static Booking reserva(String id, String nombre, int salida, BookingStatus estado, String clienteId) {
        return new Booking(id, "RES-" + id, "hotel-" + id, "room-" + id, clienteId,
                LocalDate.of(2026, 11, 1), LocalDate.of(2026, 11, salida), 1, 100, estado)
                .withAlojamiento(nombre, "Lima", "Miraflores", "", "Doble", "101");
    }

    private static void enPrincipal(Runnable accion) { InstrumentationRegistry.getInstrumentation().runOnMainSync(accion); }

    private static final class FakeSesion implements BookingsViewModel.SesionCliente {
        final MutableLiveData<Cuenta> cuenta;
        FakeSesion(Cuenta actual) { cuenta = new MutableLiveData<>(actual); }
        void cambiar(Cuenta actual) { cuenta.setValue(actual); }
        @Override public LiveData<Cuenta> cambios() { return cuenta; }
        @Override public String uid() { return cuenta.getValue() == null ? null : cuenta.getValue().getIdentidadId(); }
        @Override public boolean esCliente() { return cuenta.getValue() != null && cuenta.getValue().getRol() == Role.CLIENTE; }
    }

    private static final class Consulta {
        final String clienteId;
        final ResultCallback<List<Booking>> callback;
        Consulta(String clienteId, ResultCallback<List<Booking>> callback) { this.clienteId = clienteId; this.callback = callback; }
        void exito(List<Booking> reservas) { callback.onExito(reservas); }
        void error(String mensaje) { callback.onError(mensaje); }
    }

    private static final class FakeRepositorio implements BookingRepository {
        final List<Consulta> consultas = new ArrayList<>();
        Consulta ultima() { return consultas.get(consultas.size() - 1); }
        @Override public void reservasDe(String clienteId, ResultCallback<List<Booking>> callback) { consultas.add(new Consulta(clienteId, callback)); }
        private AssertionError ajeno() { return new AssertionError("La lista solo debe consultar las reservas de su cliente."); }
        @Override public void reservasDeHotel(String hotelId, ResultCallback<List<Booking>> callback) { throw ajeno(); }
        @Override public void obtener(String id, ResultCallback<Booking> callback) { throw ajeno(); }
        @Override public void crear(Booking reserva, ResultCallback<Booking> callback) { throw ajeno(); }
        @Override public void cancelar(String id, ResultCallback<Booking> callback) { throw ajeno(); }
        @Override public void pagar(String id, Payment pago, ResultCallback<Booking> callback) { throw ajeno(); }
        @Override public void finalizar(String id, ResultCallback<Booking> callback) { throw ajeno(); }
        @Override public void cobrar(String id, ResultCallback<Booking> callback) { throw ajeno(); }
        @Override public void agregarCargo(String id, Charge cargo, ResultCallback<Booking> callback) { throw ajeno(); }
        @Override public void valorar(String id, float rating, String comentario, ResultCallback<Review> callback) { throw ajeno(); }
    }
}
