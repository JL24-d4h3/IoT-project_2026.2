package org.iot.project.ui.client.bookings;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import android.content.Context;
import android.view.ContextThemeWrapper;
import android.view.LayoutInflater;
import android.view.View;

import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;

import org.iot.project.R;
import org.iot.project.databinding.FragmentBookingsBinding;
import org.junit.Test;
import org.junit.runner.RunWith;

/** Los controles reales se inflan sin actividad, repositorios ni llamadas a Firebase. */
@RunWith(AndroidJUnit4.class)
public final class BookingsListadoLayoutAndroidTest {
    @Test public void ventanaReducidaPorTecladoConservaEspacioParaReservas() {
        InstrumentationRegistry.getInstrumentation().runOnMainSync(() -> {
            Context contexto = new ContextThemeWrapper(
                    InstrumentationRegistry.getInstrumentation().getTargetContext(), R.style.Theme_Project);
            FragmentBookingsBinding vista = FragmentBookingsBinding.inflate(LayoutInflater.from(contexto));
            vista.reservasLista.setVisibility(View.VISIBLE);
            int lado = Math.round(360 * contexto.getResources().getDisplayMetrics().density);
            int minimo = Math.round(48 * contexto.getResources().getDisplayMetrics().density);
            vista.getRoot().measure(View.MeasureSpec.makeMeasureSpec(lado, View.MeasureSpec.EXACTLY),
                    View.MeasureSpec.makeMeasureSpec(lado, View.MeasureSpec.EXACTLY));
            vista.getRoot().layout(0, 0, lado, lado);

            assertEquals(View.VISIBLE, vista.reservasControles.getVisibility());
            assertTrue("Debe conservarse al menos una zona de interacción de 48 dp para la lista",
                    vista.reservasLista.getHeight() >= minimo);
            assertTrue(vista.reservasLista.getBottom() <= lado);
            assertTrue(vista.reservasLista.getMeasuredHeight() > 0);
            assertEquals(vista.reservasOrden.getParent(), vista.reservasSentido.getParent());
        });
    }

    @Test public void listadoEmpiezaPorSalidaDescendenteYTodosLosEstados() {
        InstrumentationRegistry.getInstrumentation().runOnMainSync(() -> {
            Context contexto = new ContextThemeWrapper(
                    InstrumentationRegistry.getInstrumentation().getTargetContext(), R.style.Theme_Project);
            FragmentBookingsBinding vista = FragmentBookingsBinding.inflate(LayoutInflater.from(contexto));

            assertNotNull(vista.reservasLista);
            assertEquals("", vista.reservasBuscar.getText().toString());
            assertEquals(R.id.reservas_orden_salida, vista.reservasOrden.getCheckedChipId());
            assertEquals(R.id.reservas_sentido_descendente, vista.reservasSentido.getCheckedChipId());
            assertEquals(R.id.reservas_estado_todos, vista.reservasEstados.getCheckedChipId());
            assertEquals(6, vista.reservasEstados.getChildCount());
            assertTrue(vista.reservasOrden.isSingleSelection());
            assertTrue(vista.reservasSentido.isSingleSelection());
            assertTrue(vista.reservasEstados.isSingleSelection());
            assertTrue(vista.reservasEstados.isSelectionRequired());
        });
    }

    @Test public void filtrosIndependientesSiguenDisponiblesSinResultados() {
        InstrumentationRegistry.getInstrumentation().runOnMainSync(() -> {
            Context contexto = new ContextThemeWrapper(
                    InstrumentationRegistry.getInstrumentation().getTargetContext(), R.style.Theme_Project);
            FragmentBookingsBinding vista = FragmentBookingsBinding.inflate(LayoutInflater.from(contexto));

            vista.reservasOrden.check(R.id.reservas_orden_nombre);
            vista.reservasSentido.check(R.id.reservas_sentido_ascendente);
            vista.reservasEstados.check(R.id.reservas_estado_cancelada);
            vista.reservasLista.setVisibility(View.GONE);
            vista.reservasVacio.setVisibility(View.VISIBLE);
            assertEquals(View.VISIBLE, vista.reservasControles.getVisibility());
            assertEquals(R.id.reservas_orden_nombre, vista.reservasOrden.getCheckedChipId());
            assertEquals(R.id.reservas_sentido_ascendente, vista.reservasSentido.getCheckedChipId());
            assertEquals(R.id.reservas_estado_cancelada, vista.reservasEstados.getCheckedChipId());

            vista.reservasEstadoCancelada.performClick();
            assertEquals(R.id.reservas_estado_cancelada, vista.reservasEstados.getCheckedChipId());
        });
    }
}
