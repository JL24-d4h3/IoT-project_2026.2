package org.iot.project.ui.common;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;

import androidx.annotation.DrawableRes;
import androidx.annotation.NonNull;
import androidx.core.content.ContextCompat;
import androidx.recyclerview.widget.DiffUtil;
import androidx.recyclerview.widget.ListAdapter;
import androidx.recyclerview.widget.RecyclerView;

import org.iot.project.R;
import org.iot.project.databinding.ItemNotificationBinding;
import org.iot.project.models.AppNotification;
import org.iot.project.utils.DateFormatter;

import java.time.LocalDateTime;

/**
 * Centro de notificaciones (§39, §41).
 *
 * <p>Cada tipo lleva su icono y su color porque son notificaciones de cosas muy
 * distintas —un pago aprobado y un taxi que llegó— y el color ayuda a separarlas
 * antes de leerlas. El punto de "sin leer" se apaga al abrir la pantalla, que es
 * cuando dejan de serlo.
 */
public class NotificationAdapter
        extends ListAdapter<AppNotification, NotificationAdapter.NotificationViewHolder> {

    private static final DiffUtil.ItemCallback<AppNotification> COMPARADOR =
            new DiffUtil.ItemCallback<AppNotification>() {
                @Override
                public boolean areItemsTheSame(@NonNull AppNotification anterior,
                                               @NonNull AppNotification nueva) {
                    return anterior.getId().equals(nueva.getId());
                }

                @Override
                public boolean areContentsTheSame(@NonNull AppNotification anterior,
                                                  @NonNull AppNotification nueva) {
                    return anterior.isLeida() == nueva.isLeida()
                            && anterior.getTitulo().equals(nueva.getTitulo());
                }
            };

    public NotificationAdapter() {
        super(COMPARADOR);
    }

    @NonNull
    @Override
    public NotificationViewHolder onCreateViewHolder(@NonNull ViewGroup padre, int viewType) {
        return new NotificationViewHolder(ItemNotificationBinding.inflate(
                LayoutInflater.from(padre.getContext()), padre, false));
    }

    @Override
    public void onBindViewHolder(@NonNull NotificationViewHolder holder, int posicion) {
        holder.bind(getItem(posicion));
    }

    static class NotificationViewHolder extends RecyclerView.ViewHolder {

        private final ItemNotificationBinding binding;

        NotificationViewHolder(@NonNull ItemNotificationBinding binding) {
            super(binding.getRoot());
            this.binding = binding;
        }

        void bind(@NonNull AppNotification notificacion) {
            binding.notificationTitle.setText(notificacion.getTitulo());
            binding.notificationMessage.setText(notificacion.getMensaje());
            binding.notificationTime.setText(DateFormatter.relativo(
                    notificacion.getTimestamp(), LocalDateTime.now()));

            binding.notificationIcon.setImageResource(iconoDe(notificacion.getTipo()));
            tintar(binding.notificationIcon, colorDe(notificacion.getTipo()));
            binding.notificationUnread.setVisibility(
                    notificacion.isLeida() ? View.GONE : View.VISIBLE);
        }

        private void tintar(@NonNull ImageView vista, int colorRes) {
            vista.setColorFilter(ContextCompat.getColor(vista.getContext(), colorRes));
        }

        @DrawableRes
        private int iconoDe(@NonNull AppNotification.Tipo tipo) {
            switch (tipo) {
                case PAGO:
                    return R.drawable.ic_credit_card;
                case CHECKOUT:
                    return R.drawable.ic_receipt;
                case CARGO_ADICIONAL:
                    return R.drawable.ic_info;
                case TAXI:
                    return R.drawable.ic_taxi;
                case MENSAJE:
                    return R.drawable.ic_chat;
                case RESERVA:
                default:
                    return R.drawable.ic_calendar;
            }
        }

        private int colorDe(@NonNull AppNotification.Tipo tipo) {
            switch (tipo) {
                case PAGO:
                    return R.color.colorSuccess;
                case CARGO_ADICIONAL:
                    return R.color.colorWarning;
                case TAXI:
                    return R.color.colorInfo;
                case RESERVA:
                case CHECKOUT:
                case MENSAJE:
                default:
                    return R.color.colorPrimary;
            }
        }
    }
}
