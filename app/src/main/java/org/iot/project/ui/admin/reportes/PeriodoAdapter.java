package org.iot.project.ui.admin.reportes;

import android.view.LayoutInflater;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.DiffUtil;
import androidx.recyclerview.widget.ListAdapter;
import androidx.recyclerview.widget.RecyclerView;

import org.iot.project.R;
import org.iot.project.databinding.ItemPeriodoReporteBinding;
import org.iot.project.models.PeriodoDeVentas;
import org.iot.project.utils.DateFormatter;
import org.iot.project.utils.PriceFormatter;

/**
 * Filas del reporte de reservas y ventas (RF-055 a RF-058).
 *
 * <p>Cada fila es un periodo ya sumado por el repositorio: aqui no se agrupa
 * nada, solo se le pone etiqueta a la fecha por la que se agrupo.
 */
public class PeriodoAdapter extends ListAdapter<PeriodoDeVentas, PeriodoAdapter.Fila> {

    private static final DiffUtil.ItemCallback<PeriodoDeVentas> COMPARADOR =
            new DiffUtil.ItemCallback<PeriodoDeVentas>() {
                @Override
                public boolean areItemsTheSame(@NonNull PeriodoDeVentas anterior,
                                               @NonNull PeriodoDeVentas nueva) {
                    // Un periodo es su fecha, con la granularidad que se pidio:
                    // el mismo 1 de septiembre es una fila distinta en el reporte
                    // diario y en el mensual, y sin comparar tambien la
                    // periodicidad se tomarian por la misma.
                    return anterior.getPeriodicidad() == nueva.getPeriodicidad()
                            && anterior.getInicio().equals(nueva.getInicio());
                }

                @Override
                public boolean areContentsTheSame(@NonNull PeriodoDeVentas anterior,
                                                  @NonNull PeriodoDeVentas nueva) {
                    // Se compara campo a campo y no por la lista entera: cada
                    // consulta arma PeriodoDeVentas nuevos, asi que comparar por
                    // identidad diria siempre que cambio todo.
                    return anterior.getReservas() == nueva.getReservas()
                            && anterior.getNoches() == nueva.getNoches()
                            && anterior.getMonto() == nueva.getMonto();
                }
            };

    public PeriodoAdapter() {
        super(COMPARADOR);
    }

    @NonNull
    @Override
    public Fila onCreateViewHolder(@NonNull ViewGroup padre, int tipoVista) {
        return new Fila(ItemPeriodoReporteBinding.inflate(
                LayoutInflater.from(padre.getContext()), padre, false));
    }

    @Override
    public void onBindViewHolder(@NonNull Fila titular, int posicion) {
        titular.bind(getItem(posicion));
    }

    /** Una fila del reporte. */
    static class Fila extends RecyclerView.ViewHolder {

        private final ItemPeriodoReporteBinding binding;

        Fila(@NonNull ItemPeriodoReporteBinding binding) {
            super(binding.getRoot());
            this.binding = binding;
        }

        void bind(@NonNull PeriodoDeVentas periodo) {
            binding.periodoEtiqueta.setText(etiqueta(periodo));
            binding.periodoMonto.setText(PriceFormatter.format(periodo.getMonto()));
            binding.periodoDetalle.setText(detalle(periodo));
        }

        /**
         * "lun 20 set", "septiembre 2026" o "2026", segun la granularidad.
         *
         * <p>La fila sabe como rotularse porque trae consigo con que se agrupo:
         * el mismo 1 de septiembre es un dia en un reporte y un mes entero en
         * otro, y la fecha sola no lo dice.
         */
        @NonNull
        private CharSequence etiqueta(@NonNull PeriodoDeVentas periodo) {
            switch (periodo.getPeriodicidad()) {
                case DIA:
                    return DateFormatter.fechaConDia(periodo.getInicio());
                case ANIO:
                    return String.valueOf(periodo.getInicio().getYear());
                case MES:
                default:
                    return DateFormatter.mesDe(periodo.getInicio());
            }
        }

        /** "3 reservas · 9 noches", con las dos cifras en singular o plural. */
        @NonNull
        private CharSequence detalle(@NonNull PeriodoDeVentas periodo) {
            String reservas = itemView.getContext().getResources().getQuantityString(
                    R.plurals.admin_reportes_reservas,
                    periodo.getReservas(), periodo.getReservas());
            String noches = itemView.getContext().getResources().getQuantityString(
                    R.plurals.admin_reportes_noches,
                    (int) periodo.getNoches(), periodo.getNoches());
            return itemView.getContext().getString(
                    R.string.admin_reportes_linea, reservas, noches);
        }
    }
}
