package org.iot.project.ui.superadmin.conductores;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import android.content.Context;
import android.view.ContextThemeWrapper;
import android.view.LayoutInflater;
import android.view.View;

import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;

import org.iot.project.R;
import org.iot.project.databinding.FragmentSuperadminConductoresBinding;
import org.iot.project.databinding.ItemConductorSaBinding;
import org.iot.project.models.Driver;
import org.iot.project.models.TaxistaAdministrado;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.util.ArrayList;
import java.util.List;

/** Infla las vistas reales con su tema; no abre actividades ni inicializa repositorios. */
@RunWith(AndroidJUnit4.class)
public final class SuperadminConductoresLayoutAndroidTest {
    @Test public void filaDistingueLasCuatroCombinacionesDeAprobacionYCuenta() {
        InstrumentationRegistry.getInstrumentation().runOnMainSync(() -> {
            Context contexto = contexto();
            ItemConductorSaBinding binding = ItemConductorSaBinding.inflate(LayoutInflater.from(contexto));
            List<String> acciones = new ArrayList<>();
            ConductorSaAdapter adaptador = new ConductorSaAdapter((id, nombre, accion, valor) ->
                    acciones.add(id + ":" + accion.name() + ":" + valor));
            ConductorSaAdapter.Fila fila = adaptador.new Fila(binding);

            for (boolean aprobado : new boolean[] { false, true }) {
                for (boolean activa : new boolean[] { false, true }) {
                    acciones.clear();
                    fila.bind(new ConductorSaAdapter.FilaDatos(taxista(aprobado, activa), false));
                    assertEquals(contexto.getString(aprobado ? R.string.sa_taxistas_aprobado
                            : R.string.sa_taxistas_sin_aprobar), binding.saConductorInsigniaEstado.getText().toString());
                    assertEquals(contexto.getString(activa ? R.string.sa_taxistas_cuenta_activa
                            : R.string.sa_taxistas_cuenta_inactiva), binding.saConductorInsigniaCuenta.getText().toString());
                    assertEquals(contexto.getString(aprobado ? R.string.sa_taxistas_retirar
                            : R.string.sa_taxistas_aprobar), binding.saConductorAccion.getText().toString());
                    assertEquals(contexto.getString(activa ? R.string.sa_taxistas_desactivar
                            : R.string.sa_taxistas_activar), binding.saConductorCuentaAccion.getText().toString());
                    assertTrue(binding.saConductorAccion.isEnabled());
                    assertTrue(binding.saConductorCuentaAccion.isEnabled());
                    assertEquals(View.VISIBLE, binding.saConductorDocumento.getVisibility());
                    assertTrue(binding.saConductorDocumento.getText().toString().contains("****1234"));

                    binding.saConductorAccion.performClick();
                    binding.saConductorCuentaAccion.performClick();
                    assertEquals(2, acciones.size());
                    assertEquals("7:HABILITACION:" + !aprobado, acciones.get(0));
                    assertEquals("7:CUENTA:" + !activa, acciones.get(1));
                    if (aprobado && activa) assertEquals(View.GONE, binding.saConductorNota.getVisibility());
                    else {
                        assertEquals(View.VISIBLE, binding.saConductorNota.getVisibility());
                        assertEquals(contexto.getString(!activa ? R.string.sa_taxistas_cuenta_inactiva_nota
                                : R.string.sa_taxistas_sin_aprobar_nota), binding.saConductorNota.getText().toString());
                    }
                }
            }
        });
    }

    @Test public void guardandoBloqueaAmbasAccionesYLaPantallaSeInflaConElTemaReal() {
        InstrumentationRegistry.getInstrumentation().runOnMainSync(() -> {
            Context contexto = contexto();
            LayoutInflater inflador = LayoutInflater.from(contexto);
            ItemConductorSaBinding filaBinding = ItemConductorSaBinding.inflate(inflador);
            ConductorSaAdapter adaptador = new ConductorSaAdapter((id, nombre, accion, valor) -> { });
            ConductorSaAdapter.Fila fila = adaptador.new Fila(filaBinding);
            fila.bind(new ConductorSaAdapter.FilaDatos(taxista(true, false), true));
            assertFalse(filaBinding.saConductorAccion.isEnabled());
            assertFalse(filaBinding.saConductorCuentaAccion.isEnabled());
            assertEquals(View.VISIBLE, filaBinding.saConductorNota.getVisibility());
            assertEquals(contexto.getString(R.string.sa_taxistas_guardando), filaBinding.saConductorNota.getText().toString());

            // La misma fila debe salir del estado de guardado al reutilizarse.
            fila.bind(new ConductorSaAdapter.FilaDatos(taxista(false, true), false));
            assertTrue(filaBinding.saConductorAccion.isEnabled());
            assertTrue(filaBinding.saConductorCuentaAccion.isEnabled());

            FragmentSuperadminConductoresBinding pantalla = FragmentSuperadminConductoresBinding.inflate(inflador);
            assertNotNull(pantalla.saConductoresLista);
            assertNotNull(pantalla.saConductoresFiltros);
            assertNotNull(pantalla.saConductoresAviso);
            assertEquals(contexto.getString(R.string.sa_taxistas_actualizar),
                    pantalla.saConductoresActualizar.getText().toString());
            assertEquals(contexto.getString(R.string.sa_taxistas_filtro_sin_aprobar),
                    pantalla.saFiltroSoloPendientes.getText().toString());
        });
    }

    private static Context contexto() {
        return new ContextThemeWrapper(InstrumentationRegistry.getInstrumentation().getTargetContext(), R.style.Theme_Project);
    }

    private static TaxistaAdministrado taxista(boolean aprobado, boolean activa) {
        Driver perfil = new Driver("7", "Ana", "Pérez")
                .withContacto("ana@example.test", "999888777", null)
                .withDocumento("DNI", "****1234");
        // El perfil operativo conserva su condición efectiva, distinta de la aprobación administrativa.
        perfil.setHabilitado(aprobado && activa);
        return new TaxistaAdministrado(perfil, aprobado, activa);
    }
}
