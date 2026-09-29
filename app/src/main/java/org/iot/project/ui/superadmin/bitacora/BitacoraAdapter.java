package org.iot.project.ui.superadmin.bitacora;

import android.view.LayoutInflater;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.DiffUtil;
import androidx.recyclerview.widget.ListAdapter;
import androidx.recyclerview.widget.RecyclerView;

import org.iot.project.databinding.ItemBitacoraBinding;
import org.iot.project.models.LogEntry;

import java.time.format.DateTimeFormatter;

/**
 * Filas de la bitacora (RF-118 a RF-120).
 *
 * <p>Lo usa la portada del superadministrador para los ultimos movimientos y la
 * pantalla de Auditoria para la lista entera: es la misma fila en los dos
 * sitios, y tenerla dos veces obligaria a arreglar dos veces lo mismo.
 *
 * <p>Cada movimiento enseña cuando ocurrio, quien lo hizo y que paso. El
 * "cuando" va en formato corto —dia y hora— porque la lista se lee de arriba
 * abajo buscando lo reciente, no consultando un acta.
 */
public class BitacoraAdapter extends ListAdapter<LogEntry, BitacoraAdapter.Fila> {

    private static final DateTimeFormatter FORMATO =
            DateTimeFormatter.ofPattern("dd/MM · HH:mm");

    public BitacoraAdapter() {
        super(DIFF);
    }

    @NonNull
    @Override
    public Fila onCreateViewHolder(@NonNull ViewGroup padre, int tipo) {
        return new Fila(ItemBitacoraBinding.inflate(
                LayoutInflater.from(padre.getContext()), padre, false));
    }

    @Override
    public void onBindViewHolder(@NonNull Fila fila, int posicion) {
        LogEntry evento = getItem(posicion);
        fila.binding.bitacoraEvento.setText(evento.getEvento().getDisplayName());
        fila.binding.bitacoraCuando.setText(cuando(evento));
        fila.binding.bitacoraDetalle.setText(evento.getDetalle());
        fila.binding.bitacoraAutor.setText(evento.getUsuario());
    }

    /** Formato corto de la marca temporal. */
    @NonNull
    public static String cuando(@NonNull LogEntry evento) {
        return evento.getTimestamp().format(FORMATO);
    }

    static class Fila extends RecyclerView.ViewHolder {

        final ItemBitacoraBinding binding;

        Fila(@NonNull ItemBitacoraBinding binding) {
            super(binding.getRoot());
            this.binding = binding;
        }
    }

    private static final DiffUtil.ItemCallback<LogEntry> DIFF =
            new DiffUtil.ItemCallback<LogEntry>() {
                @Override
                public boolean areItemsTheSame(@NonNull LogEntry a, @NonNull LogEntry b) {
                    // Un movimiento no tiene identificador: lo que lo distingue
                    // es su marca temporal, que RC-040 obliga a que exista.
                    return a.getTimestamp().equals(b.getTimestamp())
                            && a.getEvento() == b.getEvento()
                            && a.getDetalle().equals(b.getDetalle());
                }

                @Override
                public boolean areContentsTheSame(@NonNull LogEntry a, @NonNull LogEntry b) {
                    return areItemsTheSame(a, b);
                }
            };
}
