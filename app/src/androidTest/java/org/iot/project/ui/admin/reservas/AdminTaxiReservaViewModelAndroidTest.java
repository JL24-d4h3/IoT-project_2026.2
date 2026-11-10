package org.iot.project.ui.admin.reservas;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;

import androidx.annotation.NonNull;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;

import org.iot.project.core.ResultCallback;
import org.iot.project.core.UiState;
import org.iot.project.data.repository.TaxiRepository;
import org.iot.project.models.Cuenta;
import org.iot.project.models.Driver;
import org.iot.project.models.OfertaDeTaxi;
import org.iot.project.models.Role;
import org.iot.project.models.TaxiService;
import org.iot.project.models.TaxiStatus;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

/** Estado Android con fuentes inyectadas: sin Activity, Firebase, GPS ni red. */
@RunWith(AndroidJUnit4.class)
public final class AdminTaxiReservaViewModelAndroidTest {
    private FakeTaxiRepository repositorio;
    private FakeSesion sesion;
    private AdminTaxiReservaViewModel viewModel;

    @Before public void preparar() {
        enPrincipal(() -> {
            repositorio = new FakeTaxiRepository();
            sesion = new FakeSesion(admin("admin-1", "hotel-1"));
            viewModel = new AdminTaxiReservaViewModel(repositorio, sesion);
        });
    }

    @After public void liberar() {
        enPrincipal(() -> { if (viewModel != null) viewModel.onCleared(); });
    }

    @Test public void cincoEstadosConservanCoordenadasRealesYFinalizadoNoEsActivo() {
        enPrincipal(() -> {
            iniciar("reserva-1");
            LocalDateTime ultimoReporte = LocalDateTime.of(2026, 10, 26, 10, 5);
            double latitud = -12.091;
            double longitud = -77.031;
            for (TaxiStatus estado : TaxiStatus.values()) {
                if (!estado.isFinished()) {
                    latitud -= 0.001;
                    longitud -= 0.002;
                    ultimoReporte = ultimoReporte.plusMinutes(1);
                }
                TaxiService recibido = taxi("taxi-1", "reserva-1", "501", estado);
                recibido.actualizarUbicacion(latitud, longitud, ultimoReporte);
                repositorio.ultima().exito(Collections.singletonList(recibido));
                AdminTaxiReservaViewModel.Estado pantalla = datos();
                assertEquals(1, pantalla.confirmados.size());
                assertTrue(pantalla.sinConfirmar.isEmpty());
                assertNotNull(pantalla.seleccionado);
                assertEquals(estado, pantalla.seleccionado.getEstado());
                assertEquals(latitud, pantalla.seleccionado.getLatConductor(), 0d);
                assertEquals(longitud, pantalla.seleccionado.getLngConductor(), 0d);
                assertEquals(ultimoReporte, pantalla.seleccionado.getUltimaActualizacionUbicacion());
                assertEquals("", pantalla.seleccionado.getCodigo());
            }
            assertTrue(datos().seleccionado.getEstado().isFinished());
            assertFalse(datos().seleccionado.getEstado().isActive());
            assertEquals(1, repositorio.escuchas.size());
        });
    }

    @Test public void solicitudesSinApiIdNoSeConfundenConTrasladosConfirmados() {
        enPrincipal(() -> {
            iniciar("reserva-1");
            TaxiService confirmado = taxi("taxi-confirmado", "reserva-1", "502", TaxiStatus.SOLICITADO);
            TaxiService incierto = taxi("taxi-incierto", "reserva-1", null, TaxiStatus.SOLICITADO)
                    .withEnvioApi("ENVIADO_SIN_CONFIRMAR");
            TaxiService rechazado = taxi("taxi-rechazado", "reserva-1", null, TaxiStatus.SOLICITADO)
                    .withEnvioApi("RECHAZADO");
            repositorio.ultima().exito(Arrays.asList(incierto, confirmado, rechazado));
            assertEquals(1, datos().confirmados.size());
            assertEquals(2, datos().sinConfirmar.size());
            assertEquals("taxi-confirmado", datos().seleccionado.getId());

            repositorio.ultima().exito(Arrays.asList(incierto, rechazado));
            assertTrue(datos().confirmados.isEmpty());
            assertEquals(2, datos().sinConfirmar.size());
            assertNull(datos().seleccionado);
        });
    }

    @Test public void listaVaciaYServiciosDeOtraReservaNoInventanUnTaxi() {
        enPrincipal(() -> {
            iniciar("reserva-1");
            repositorio.ultima().exito(Collections.emptyList());
            assertTrue(viewModel.getContenido().getValue().isEmpty());
            repositorio.ultima().exito(Collections.singletonList(
                    taxi("ajeno", "otra-reserva", "503", TaxiStatus.EN_CAMINO)));
            assertTrue(viewModel.getContenido().getValue().isEmpty());
        });
    }

    @Test public void callbacksTardiosSeIgnoranTrasDetenerYTrasReanudar() {
        enPrincipal(() -> {
            iniciar("reserva-1");
            Escucha antigua = repositorio.ultima();
            antigua.exito(Collections.singletonList(taxi("taxi-1", "reserva-1", "504", TaxiStatus.ASIGNADO)));
            UiState<AdminTaxiReservaViewModel.Estado> antes = viewModel.getContenido().getValue();
            viewModel.detenerSeguimiento();
            assertTrue(antigua.cerrada);
            antigua.error("Respuesta tardía");
            antigua.exito(Collections.emptyList());
            assertSame(antes, viewModel.getContenido().getValue());

            viewModel.iniciarSeguimiento();
            assertEquals(2, repositorio.escuchas.size());
            repositorio.ultima().exito(Collections.singletonList(
                    taxi("taxi-actual", "reserva-1", "505", TaxiStatus.EN_TRASLADO)));
            antigua.exito(Collections.singletonList(taxi("viejo", "reserva-1", "506", TaxiStatus.SOLICITADO)));
            antigua.error("Error de la consulta anterior");
            assertEquals("taxi-actual", datos().seleccionado.getId());
            assertNull(datos().aviso);
        });
    }

    @Test public void cambiarHotelOCuentaBorraDatosYCierraLaConsultaAnterior() {
        enPrincipal(() -> {
            iniciar("reserva-1");
            Escucha hotelAnterior = repositorio.ultima();
            hotelAnterior.exito(Collections.singletonList(taxi("privado-1", "reserva-1", "507", TaxiStatus.EN_CAMINO)));
            sesion.cambiar(admin("admin-1", "hotel-2"));
            assertTrue(hotelAnterior.cerrada);
            assertTrue(viewModel.getContenido().getValue().isLoading());
            assertEquals("hotel-2", repositorio.ultima().hotelId);
            hotelAnterior.exito(Collections.singletonList(taxi("privado-1", "reserva-1", "507", TaxiStatus.FINALIZADO)));
            assertTrue(viewModel.getContenido().getValue().isLoading());

            Escucha cuentaAnterior = repositorio.ultima();
            cuentaAnterior.exito(Collections.singletonList(taxi("privado-2", "reserva-1", "508", TaxiStatus.ASIGNADO)));
            sesion.cambiar(admin("admin-2", "hotel-2"));
            assertTrue(cuentaAnterior.cerrada);
            assertTrue(viewModel.getContenido().getValue().isLoading());
            cuentaAnterior.error("Error de la cuenta anterior");
            cuentaAnterior.exito(Collections.emptyList());
            assertTrue(viewModel.getContenido().getValue().isLoading());
            repositorio.ultima().exito(Collections.singletonList(taxi("actual", "reserva-1", "509", TaxiStatus.SOLICITADO)));
            assertEquals("actual", datos().seleccionado.getId());

            sesion.cambiar(null);
            assertTrue(viewModel.getContenido().getValue().isError());
            assertNull(viewModel.getContenido().getValue().getData());
            assertTrue(repositorio.ultima().cerrada);
        });
    }

    @Test public void cambiarReservaDescartaElResultadoDeLaReservaAnterior() {
        enPrincipal(() -> {
            iniciar("reserva-1");
            Escucha antigua = repositorio.ultima();
            antigua.exito(Collections.singletonList(taxi("viejo", "reserva-1", "510", TaxiStatus.EN_CAMINO)));
            viewModel.cargar("reserva-2");
            assertTrue(antigua.cerrada);
            assertEquals("reserva-2", repositorio.ultima().bookingId);
            assertTrue(viewModel.getContenido().getValue().isLoading());
            antigua.exito(Collections.singletonList(taxi("viejo", "reserva-1", "510", TaxiStatus.FINALIZADO)));
            assertTrue(viewModel.getContenido().getValue().isLoading());
            repositorio.ultima().exito(Collections.singletonList(taxi("nuevo", "reserva-2", "511", TaxiStatus.ASIGNADO)));
            assertEquals("nuevo", datos().seleccionado.getId());
        });
    }

    @Test public void rolEquivocadoOSinHotelNoConsultaElRepositorio() {
        enPrincipal(() -> {
            sesion.cambiar(new Cuenta(Role.CLIENTE, "cliente", "Cliente", "cliente@example.test"));
            iniciar("reserva-1");
            assertTrue(repositorio.escuchas.isEmpty());
            assertTrue(viewModel.getContenido().getValue().isError());
            sesion.cambiar(admin("admin-sin-hotel", null));
            viewModel.iniciarSeguimiento();
            assertTrue(repositorio.escuchas.isEmpty());
            assertTrue(viewModel.getContenido().getValue().isError());
        });
    }

    @Test public void errorDeRedMantieneUltimoPuntoYSeLimpiaTrasRecuperarse() {
        enPrincipal(() -> {
            iniciar("reserva-1");
            TaxiService recibido = taxi("taxi-1", "reserva-1", "512", TaxiStatus.EN_TRASLADO);
            LocalDateTime reporte = LocalDateTime.of(2026, 10, 26, 8, 30);
            recibido.actualizarUbicacion(-12.125, -77.085, reporte);
            repositorio.ultima().exito(Collections.singletonList(recibido));
            repositorio.ultima().error("No hay conexión con el sistema de taxis.");
            assertEquals("No hay conexión con el sistema de taxis.", datos().aviso);
            assertEquals(TaxiStatus.EN_TRASLADO, datos().seleccionado.getEstado());
            assertEquals(-12.125, datos().seleccionado.getLatConductor(), 0d);
            assertEquals(-77.085, datos().seleccionado.getLngConductor(), 0d);
            assertEquals(reporte, datos().seleccionado.getUltimaActualizacionUbicacion());

            repositorio.ultima().exito(Collections.singletonList(recibido));
            assertNull(datos().aviso);
            assertEquals(reporte, datos().seleccionado.getUltimaActualizacionUbicacion());
        });
    }

    @Test public void seleccionManualConservaUnFinalizadoAunqueExistaOtroActivo() {
        enPrincipal(() -> {
            iniciar("reserva-1");
            TaxiService finalizado = taxi("historial", "reserva-1", "513", TaxiStatus.FINALIZADO);
            TaxiService activo = taxi("actual", "reserva-1", "514", TaxiStatus.EN_TRASLADO);
            repositorio.ultima().exito(Arrays.asList(finalizado, activo));
            assertEquals("actual", datos().seleccionado.getId());
            viewModel.seleccionar("historial");
            repositorio.ultima().exito(Arrays.asList(finalizado, activo));
            assertEquals("historial", datos().seleccionado.getId());
            assertTrue(datos().seleccionado.getEstado().isFinished());
            assertFalse(datos().seleccionado.getEstado().isActive());
        });
    }

    private void iniciar(String bookingId) {
        viewModel.cargar(bookingId);
        viewModel.iniciarSeguimiento();
    }

    private AdminTaxiReservaViewModel.Estado datos() {
        UiState<AdminTaxiReservaViewModel.Estado> estado = viewModel.getContenido().getValue();
        assertNotNull(estado);
        assertTrue(estado.isSuccess());
        return estado.requireData();
    }

    private static Cuenta admin(String uid, String hotelId) {
        return new Cuenta(Role.ADMIN_HOTEL, uid, "Administrador", "admin@example.test", hotelId);
    }

    private static TaxiService taxi(String id, String bookingId, String apiId, TaxiStatus estado) {
        return new TaxiService(id, "", bookingId, "cliente-1")
                .withApiId(apiId).withRuta("Hotel", "Aeropuerto")
                .withProgramacion(LocalDate.of(2026, 10, 26), LocalTime.of(10, 0), false)
                .withRecojo(-12.09654, -77.03611)
                .withEstadoPersistido(estado, estado == TaxiStatus.SOLICITADO
                        ? null : new Driver("conductor-1", "Ana", "Pérez"), 0f);
    }

    private static void enPrincipal(Runnable accion) {
        InstrumentationRegistry.getInstrumentation().runOnMainSync(accion);
    }

    private static final class FakeSesion implements AdminTaxiReservaViewModel.SesionHotel {
        private final MutableLiveData<Cuenta> cuenta;
        FakeSesion(Cuenta inicial) { cuenta = new MutableLiveData<>(inicial); }
        void cambiar(Cuenta nueva) { cuenta.setValue(nueva); }
        @Override public LiveData<Cuenta> cambios() { return cuenta; }
        @Override public String uid() {
            return cuenta.getValue() == null ? null : cuenta.getValue().getIdentidadId();
        }
        @Override public String hotel() {
            return cuenta.getValue() == null ? null : cuenta.getValue().getHotelId();
        }
        @Override public boolean esAdministrador() {
            return cuenta.getValue() != null && cuenta.getValue().getRol() == Role.ADMIN_HOTEL;
        }
    }

    private static final class Escucha {
        final String hotelId;
        final String bookingId;
        final ResultCallback<List<TaxiService>> callback;
        boolean cerrada;
        Escucha(String hotelId, String bookingId, ResultCallback<List<TaxiService>> callback) {
            this.hotelId = hotelId;
            this.bookingId = bookingId;
            this.callback = callback;
        }
        // Se permite entregar aun después de cerrar para comprobar la guarda del ViewModel.
        void exito(List<TaxiService> datos) { callback.onExito(datos); }
        void error(String mensaje) { callback.onError(mensaje); }
    }

    private static final class FakeTaxiRepository implements TaxiRepository {
        final List<Escucha> escuchas = new ArrayList<>();
        Escucha ultima() { return escuchas.get(escuchas.size() - 1); }

        @Override public Suscripcion escucharServiciosDeReserva(@NonNull String hotelId,
                @NonNull String bookingId, @NonNull ResultCallback<List<TaxiService>> callback) {
            Escucha escucha = new Escucha(hotelId, bookingId, callback);
            escuchas.add(escucha);
            return () -> escucha.cerrada = true;
        }

        private AssertionError ajeno() { return new AssertionError("El seguimiento del hotel solo debe escuchar su reserva."); }
        @Override public void servicioActivo(String id, ResultCallback<TaxiService> callback) { throw ajeno(); }
        @Override public void historial(String id, ResultCallback<List<TaxiService>> callback) { throw ajeno(); }
        @Override public void solicitar(TaxiService solicitud, ResultCallback<TaxiService> callback) { throw ajeno(); }
        @Override public void obtener(String id, ResultCallback<TaxiService> callback) { throw ajeno(); }
        @Override public void calificar(String id, float rating, ResultCallback<TaxiService> callback) { throw ajeno(); }
        @Override public void perfilDe(String id, ResultCallback<Driver> callback) { throw ajeno(); }
        @Override public void disponibles(String id, ResultCallback<List<OfertaDeTaxi>> callback) { throw ajeno(); }
        @Override public void serviciosEnCursoDe(String id, ResultCallback<List<TaxiService>> callback) { throw ajeno(); }
        @Override public void aceptar(String id, String conductor, ResultCallback<TaxiService> callback) { throw ajeno(); }
        @Override public void avanzarComoConductor(String id, String conductor, ResultCallback<TaxiService> callback) { throw ajeno(); }
        @Override public void validarCodigo(String id, String conductor, String codigo, ResultCallback<TaxiService> callback) { throw ajeno(); }
    }
}
