package org.iot.project.ui.superadmin.conductores;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;

import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;

import org.iot.project.core.ResultCallback;
import org.iot.project.core.UiState;
import org.iot.project.data.repository.TaxistasAdminRepository;
import org.iot.project.models.Cuenta;
import org.iot.project.models.Driver;
import org.iot.project.models.Role;
import org.iot.project.models.TaxistaAdministrado;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

/** Lógica de estado Android con dependencias en memoria, sin Activity, Firebase ni HTTP. */
@RunWith(AndroidJUnit4.class)
public final class SuperadminConductoresViewModelAndroidTest {
    private FakeRepositorio repositorio;
    private FakeSesion sesion;
    private SuperadminConductoresViewModel viewModel;

    @Before public void preparar() {
        principal(() -> {
            repositorio = new FakeRepositorio();
            sesion = new FakeSesion(cuenta("sa-1", Role.SUPERADMIN));
            viewModel = new SuperadminConductoresViewModel(repositorio, sesion);
        });
    }

    @After public void liberar() { principal(() -> viewModel.onCleared()); }

    @Test public void aprobacionDeCuentaInactivaNoReactivaSuAcceso() {
        principal(() -> {
            cargar(taxista("1", false, false));
            viewModel.habilitar("1", true);
            assertEquals(1, repositorio.cambios.size());
            assertTrue(repositorio.ultimoCambio().habilitacion);
            assertFalse(lista().get(0).isHabilitado());
            assertFalse(lista().get(0).isCuentaActiva());
            repositorio.ultimoCambio().callback.onExito(taxista("1", true, false));
            assertTrue(lista().get(0).isHabilitado());
            assertFalse(lista().get(0).isCuentaActiva());
            assertFalse(lista().get(0).getPerfil().isHabilitado());
            assertEquals(SuperadminConductoresViewModel.Accion.HABILITACION,
                    viewModel.getCambio().getValue().requireData().accion);

            viewModel.filtrar(true);
            assertTrue(viewModel.getContenido().getValue().isEmpty());
        });
    }

    @Test public void activarCuentaNoApruebaAlTaxista() {
        principal(() -> {
            cargar(taxista("1", false, false));
            viewModel.cambiarCuenta("1", true);
            assertFalse(repositorio.ultimoCambio().habilitacion);
            repositorio.ultimoCambio().callback.onExito(taxista("1", false, true));
            assertFalse(lista().get(0).isHabilitado());
            assertTrue(lista().get(0).isCuentaActiva());
            assertEquals(SuperadminConductoresViewModel.Accion.CUENTA,
                    viewModel.getCambio().getValue().requireData().accion);
            viewModel.filtrar(true);
            assertEquals(1, lista().size());
        });
    }

    @Test public void dobleToqueYAccionParalelaSobreLaMismaFilaNoDuplicanElEnvio() {
        principal(() -> {
            cargar(taxista("1", false, true));
            viewModel.habilitar("1", true);
            viewModel.habilitar("1", true);
            viewModel.cambiarCuenta("1", false);
            assertEquals(1, repositorio.cambios.size());
            assertTrue(viewModel.getPendientes().getValue().contains("1"));
            assertFalse(viewModel.puedeCambiar("1"));
            assertFalse(lista().get(0).isHabilitado());
            repositorio.ultimoCambio().callback.onExito(taxista("1", true, true));
            assertTrue(viewModel.getPendientes().getValue().isEmpty());
            assertTrue(viewModel.puedeCambiar("1"));
        });
    }

    @Test public void lecturaAnteriorNoSobrescribeUnaEscrituraConfirmada() {
        principal(() -> {
            cargar(taxista("1", false, true));
            viewModel.reintentar();
            ResultCallback<List<TaxistaAdministrado>> lecturaAntigua = repositorio.ultimaLectura();
            viewModel.habilitar("1", true);
            repositorio.ultimoCambio().callback.onExito(taxista("1", true, true));
            lecturaAntigua.onExito(Collections.singletonList(taxista("1", false, true)));
            assertTrue(lista().get(0).isHabilitado());
            assertTrue(viewModel.getPendientes().getValue().isEmpty());
        });
    }

    @Test public void escrituraInciertaNoCambiaElEstadoNiSeRepiteAlVolver() {
        principal(() -> {
            cargar(taxista("1", true, true));
            viewModel.cambiarCuenta("1", false);
            repositorio.ultimoCambio().callback.onError("Se perdió la respuesta del servidor.");
            assertTrue(lista().get(0).isCuentaActiva());
            assertTrue(viewModel.getCambio().getValue().isError());
            assertTrue(viewModel.getPendientes().getValue().isEmpty());
            viewModel.detener();
            viewModel.iniciar();
            assertEquals(1, repositorio.cambios.size());
            assertEquals(2, repositorio.lecturas.size());
            repositorio.ultimaLectura().onExito(Collections.singletonList(taxista("1", true, false)));
            assertFalse(lista().get(0).isCuentaActiva());
        });
    }

    @Test public void logoutYRegresoAlMismoUidDescartanLaEscrituraDeLaSesionAnterior() {
        principal(() -> {
            cargar(taxista("1", false, true));
            viewModel.habilitar("1", true);
            Pendiente cambioAnterior = repositorio.ultimoCambio();
            sesion.cambiar(null);
            assertNull(viewModel.getContenido().getValue().getData());
            assertTrue(viewModel.getPendientes().getValue().isEmpty());
            sesion.cambiar(cuenta("sa-1", Role.SUPERADMIN));
            assertTrue(viewModel.getContenido().getValue().isLoading());
            cambioAnterior.callback.onExito(taxista("1", true, true));
            assertTrue(viewModel.getContenido().getValue().isLoading());
            assertNull(viewModel.getCambio().getValue());
            repositorio.ultimaLectura().onExito(Collections.singletonList(taxista("2", false, true)));
            assertEquals("2", lista().get(0).getId());
        });
    }

    @Test public void rolIncorrectoNoConsultaNiMutaYNoPublicaUnVacioAlFiltrar() {
        principal(() -> {
            sesion.cambiar(cuenta("cliente", Role.CLIENTE));
            viewModel.iniciar();
            viewModel.filtrar(true);
            viewModel.habilitar("1", true);
            viewModel.cambiarCuenta("1", false);
            assertTrue(viewModel.getContenido().getValue().isError());
            assertTrue(repositorio.lecturas.isEmpty());
            assertTrue(repositorio.cambios.isEmpty());
        });
    }

    @Test public void callbackDeLecturaTrasSalirNoPublicaDatos() {
        principal(() -> {
            viewModel.iniciar();
            ResultCallback<List<TaxistaAdministrado>> anterior = repositorio.ultimaLectura();
            UiState<List<TaxistaAdministrado>> carga = viewModel.getContenido().getValue();
            viewModel.detener();
            anterior.onExito(Collections.singletonList(taxista("1", true, true)));
            assertSame(carga, viewModel.getContenido().getValue());
            viewModel.iniciar();
            anterior.onError("La consulta antigua terminó tarde.");
            assertTrue(viewModel.getContenido().getValue().isLoading());
            repositorio.ultimaLectura().onExito(Collections.singletonList(taxista("2", false, true)));
            assertEquals("2", lista().get(0).getId());
        });
    }

    @Test public void erroresDeLecturaConservanElDirectorioYConfirmacionesAjenaSeRechazan() {
        principal(() -> {
            cargar(taxista("1", true, true));
            viewModel.reintentar();
            repositorio.ultimaLectura().onError("Sin conexión.");
            assertEquals("Sin conexión.", viewModel.getAviso().getValue());
            assertEquals("1", lista().get(0).getId());
            viewModel.cambiarCuenta("1", false);
            repositorio.ultimoCambio().callback.onExito(taxista("otra", true, false));
            assertTrue(viewModel.getCambio().getValue().isError());
            assertEquals("1", lista().get(0).getId());
            assertTrue(lista().get(0).isCuentaActiva());
        });
    }

    private void cargar(TaxistaAdministrado... taxistas) {
        viewModel.iniciar();
        repositorio.ultimaLectura().onExito(Arrays.asList(taxistas));
    }

    private List<TaxistaAdministrado> lista() {
        UiState<List<TaxistaAdministrado>> estado = viewModel.getContenido().getValue();
        assertNotNull(estado);
        assertTrue(estado.isSuccess());
        return estado.requireData();
    }

    private static TaxistaAdministrado taxista(String id, boolean aprobado, boolean activa) {
        Driver perfil = new Driver(id, "Ana", "Pérez").withContacto("ana@example.test", "999888777", null);
        perfil.setHabilitado(aprobado && activa);
        return new TaxistaAdministrado(perfil, aprobado, activa);
    }

    private static Cuenta cuenta(String uid, Role rol) {
        return new Cuenta(rol, uid, "Cuenta de prueba", "cuenta@example.test");
    }

    private static void principal(Runnable accion) {
        InstrumentationRegistry.getInstrumentation().runOnMainSync(accion);
    }

    private static final class FakeSesion implements SuperadminConductoresViewModel.SesionAdmin {
        final MutableLiveData<Cuenta> actual;
        FakeSesion(Cuenta cuenta) { actual = new MutableLiveData<>(cuenta); }
        void cambiar(Cuenta cuenta) { actual.setValue(cuenta); }
        @Override public LiveData<Cuenta> cambios() { return actual; }
        @Override public String uid() { return actual.getValue() == null ? null : actual.getValue().getIdentidadId(); }
        @Override public boolean esSuperadmin() {
            return actual.getValue() != null && actual.getValue().getRol() == Role.SUPERADMIN;
        }
    }

    private static final class Pendiente {
        final String id;
        final boolean valor;
        final boolean habilitacion;
        final ResultCallback<TaxistaAdministrado> callback;
        Pendiente(String id, boolean valor, boolean habilitacion, ResultCallback<TaxistaAdministrado> callback) {
            this.id = id;
            this.valor = valor;
            this.habilitacion = habilitacion;
            this.callback = callback;
        }
    }

    private static final class FakeRepositorio implements TaxistasAdminRepository {
        final List<ResultCallback<List<TaxistaAdministrado>>> lecturas = new ArrayList<>();
        final List<Pendiente> cambios = new ArrayList<>();
        ResultCallback<List<TaxistaAdministrado>> ultimaLectura() { return lecturas.get(lecturas.size() - 1); }
        Pendiente ultimoCambio() { return cambios.get(cambios.size() - 1); }
        @Override public void listar(ResultCallback<List<TaxistaAdministrado>> callback) { lecturas.add(callback); }
        @Override public void cambiarHabilitacion(String id, boolean valor, ResultCallback<TaxistaAdministrado> callback) {
            cambios.add(new Pendiente(id, valor, true, callback));
        }
        @Override public void cambiarCuenta(String id, boolean valor, ResultCallback<TaxistaAdministrado> callback) {
            cambios.add(new Pendiente(id, valor, false, callback));
        }
    }
}
