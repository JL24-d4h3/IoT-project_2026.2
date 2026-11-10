package org.iot.project.test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import android.content.Context;
import android.graphics.Typeface;
import android.view.ContextThemeWrapper;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;

import org.iot.project.R;
import org.iot.project.models.Driver;
import org.iot.project.models.TaxiService;
import org.iot.project.models.TaxiStatus;
import org.iot.project.ui.components.TaxiStatusView;
import org.junit.Test;
import org.junit.runner.RunWith;

/** Los tres roles leen el mismo estado sin acciones que modifiquen el viaje. */
@RunWith(AndroidJUnit4.class)
public final class TaxiEstadosAndroidTest {
    @Test public void cincoEstadosCompartenOrdenYPasoActualEnLosTresRoles() {
        InstrumentationRegistry.getInstrumentation().runOnMainSync(() -> {
            Context contexto = new ContextThemeWrapper(
                    InstrumentationRegistry.getInstrumentation().getTargetContext(), R.style.Theme_Project);
            TaxiStatusView vista = new TaxiStatusView(contexto);
            for (TaxiStatus estado : TaxiStatus.values()) {
                TaxiService taxi = servicio(estado);
                vista.bind(taxi);
                comprobarEstado(vista, estado, contexto);
                vista.bindParaHotel(taxi);
                comprobarEstado(vista, estado, contexto);
                vista.bindParaConductor(taxi);
                comprobarEstado(vista, estado, contexto);
            }
        });
    }

    @Test public void recepcionExplicaPasajeroYDestinoSinInstruccionesParaAvanzar() {
        InstrumentationRegistry.getInstrumentation().runOnMainSync(() -> {
            Context contexto = new ContextThemeWrapper(
                    InstrumentationRegistry.getInstrumentation().getTargetContext(), R.style.Theme_Project);
            TaxiStatusView vista = new TaxiStatusView(contexto);
            TaxiService taxi = servicio(TaxiStatus.EN_TRASLADO);
            vista.bindParaHotel(taxi);
            TextView nota = vista.findViewById(R.id.status_note);
            assertEquals(contexto.getString(R.string.taxi_hotel_en_traslado, "Aeropuerto"),
                    nota.getText().toString());
            assertTrue(nota.getText().toString().contains("Aeropuerto"));
            assertFalse(vista.isClickable());
            taxi.withEstadoPersistido(TaxiStatus.FINALIZADO, taxi.getDriver(), 0);
            vista.bindParaHotel(taxi);
            assertEquals(contexto.getString(R.string.taxi_hotel_finalizado), nota.getText().toString());
        });
    }

    private static void comprobarEstado(TaxiStatusView vista, TaxiStatus estado, Context contexto) {
        TextView insignia = vista.findViewById(R.id.status_badge);
        assertEquals(estado.getDisplayName(), insignia.getText().toString());
        assertFalse(((TextView) vista.findViewById(R.id.status_note)).getText().toString().isBlank());
        LinearLayout pasos = vista.findViewById(R.id.status_timeline);
        assertEquals(5, pasos.getChildCount());
        int actuales = 0;
        for (int i = 0; i < pasos.getChildCount(); i++) {
            TextView etiqueta = pasos.getChildAt(i).findViewById(R.id.step_label);
            assertEquals(TaxiStatus.values()[i].getDisplayName(), etiqueta.getText().toString());
            boolean actual = pasos.getChildAt(i).getContentDescription().toString()
                    .contains(contexto.getString(R.string.taxi_paso_actual));
            if (actual) actuales++;
            assertEquals(i == estado.ordinal(), actual);
            assertEquals(i == estado.ordinal() ? Typeface.BOLD : Typeface.NORMAL,
                    etiqueta.getTypeface() == null ? Typeface.NORMAL : etiqueta.getTypeface().getStyle());
        }
        assertEquals(1, actuales);
    }

    private static TaxiService servicio(TaxiStatus estado) {
        return new TaxiService("taxi-1", "", "reserva-1", "cliente-1")
                .withApiId("25").withRuta("Hotel", "Aeropuerto")
                .withEstadoPersistido(estado, estado == TaxiStatus.SOLICITADO
                        ? null : new Driver("73", "Julio", "Soto"), 0);
    }
}
