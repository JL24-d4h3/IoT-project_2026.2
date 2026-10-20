package org.iot.project.ui.superadmin.usuarios;

import android.content.res.ColorStateList;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.DrawableRes;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.content.ContextCompat;
import androidx.recyclerview.widget.DiffUtil;
import androidx.recyclerview.widget.ListAdapter;
import androidx.recyclerview.widget.RecyclerView;

import org.iot.project.R;
import org.iot.project.databinding.ItemCuentaSaBinding;
import org.iot.project.models.User;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Filas de la lista de cuentas (RF-005).
 *
 * <p>El boton de cada fila se ata aqui y no en el Fragment: es una accion sobre
 * la fila, y quien sabe de que cuenta se trata es la fila.
 *
 * <p>El rol se rotula con {@link org.iot.project.models.Role#getDisplayName()} y
 * no con una cadena propia: el rol ya se llama a si mismo en un solo sitio, y
 * una segunda lista de nombres se quedaria atras el dia que cambie uno.
 *
 * <p><b>La lista guarda filas resueltas y no cuentas.</b> El por que esta en
 * {@link Cuenta}.
 */
public class CuentaSaAdapter extends ListAdapter<CuentaSaAdapter.Cuenta, CuentaSaAdapter.Fila> {

    /**
     * Lo que la pantalla hace cuando se pulsa el boton de una cuenta.
     *
     * <p>Van el identificador y el nombre, y no la cuenta entera, porque la fila
     * no tiene la cuenta: el nombre es lo que necesita el aviso, y el
     * identificador es lo unico que hace falta para encontrar a la cuenta de
     * verdad.
     */
    public interface AlAccionar {
        void accionar(@NonNull String usuarioId, @NonNull String nombre, boolean activo);
    }

    /**
     * Una fila: lo que se pinta, copiado al publicar la lista.
     *
     * <p>No es un {@link User} porque el comparador de la lista no puede mirar
     * al modelo. Los modelos son mutables y el repositorio entrega sus propias
     * instancias, asi que al desactivar una cuenta la lista anterior y la nueva
     * apuntan al mismo objeto: comparar sus campos seria comparar al objeto
     * consigo mismo, saldria "iguales" y la fila se quedaria en ACTIVA con el
     * aviso de que esa persona ya no puede entrar.
     *
     * <p>Copiando los valores al publicar, la lista vieja conserva lo que
     * enseñaba y el cambio se ve en el sitio, sin recargar la pantalla.
     */
    static final class Cuenta {

        private final String id;
        private final String nombre;
        private final String email;
        private final String rol;
        private final boolean activa;
        private final boolean desactivable;
        private final boolean pendiente;

        Cuenta(@NonNull User usuario, boolean pendiente) {
            this.id = usuario.getId();
            this.nombre = (texto(usuario.getNombres()) + " " + texto(usuario.getApellidos())).trim();
            this.email = usuario.getEmail();
            this.rol = usuario.getRol().getDisplayName();
            this.activa = usuario.isActivo();
            this.desactivable = usuario.esDesactivable();
            this.pendiente = pendiente;
        }

        Cuenta(@NonNull Cuenta cuenta, boolean pendiente) {
            id = cuenta.id;
            nombre = cuenta.nombre;
            email = cuenta.email;
            rol = cuenta.rol;
            activa = cuenta.activa;
            desactivable = cuenta.desactivable;
            this.pendiente = pendiente;
        }
    }

    private final AlAccionar alAccionar;
    private Set<String> pendientes = Collections.emptySet();
    private List<Cuenta> cuentas = Collections.emptyList();

    public CuentaSaAdapter(@NonNull AlAccionar alAccionar) {
        super(DIFF);
        this.alAccionar = alAccionar;
    }

    /**
     * Publica una lista de cuentas.
     *
     * <p>La conversion a filas se hace aqui y no en la pantalla: la pantalla
     * sigue hablando de {@link User}, que es lo que le entrega el ViewModel, y
     * quien sabe que hace falta para pintar una fila es la fila.
     */
    public void mostrar(@NonNull List<User> cuentas) {
        List<Cuenta> filas = new ArrayList<>(cuentas.size());
        for (User usuario : cuentas) {
            filas.add(new Cuenta(usuario, pendientes.contains(usuario.getId())));
        }
        this.cuentas = filas;
        submitList(filas);
    }

    public void mostrarPendientes(@NonNull Set<String> pendientes) {
        this.pendientes = new HashSet<>(pendientes);
        List<Cuenta> filas = new ArrayList<>(cuentas.size());
        for (Cuenta cuenta : cuentas) {
            filas.add(new Cuenta(cuenta, pendientes.contains(cuenta.id)));
        }
        cuentas = filas;
        submitList(filas);
    }

    @NonNull
    @Override
    public Fila onCreateViewHolder(@NonNull ViewGroup padre, int tipo) {
        return new Fila(ItemCuentaSaBinding.inflate(
                LayoutInflater.from(padre.getContext()), padre, false));
    }

    @Override
    public void onBindViewHolder(@NonNull Fila fila, int posicion) {
        fila.bind(getItem(posicion));
    }

    class Fila extends RecyclerView.ViewHolder {

        private final ItemCuentaSaBinding binding;

        Fila(@NonNull ItemCuentaSaBinding binding) {
            super(binding.getRoot());
            this.binding = binding;
        }

        void bind(@NonNull Cuenta cuenta) {
            String nombre = cuenta.nombre.isEmpty()
                    ? itemView.getContext().getString(R.string.sa_usuarios_firebase_sin_nombre)
                    : cuenta.nombre;
            binding.saCuentaNombre.setText(nombre);
            binding.saCuentaEmail.setText(texto(cuenta.email).isEmpty()
                    ? itemView.getContext().getString(R.string.sa_usuarios_firebase_sin_correo)
                    : cuenta.email);
            binding.saCuentaRol.setText(cuenta.rol);

            binding.saCuentaEstado.setText(cuenta.activa
                    ? R.string.sa_cuenta_activa : R.string.sa_cuenta_desactivada);
            pintarEstado(cuenta.activa);

            // Un superadministrador no se desactiva (RF-006): no se le ofrece el
            // boton, en vez de ofrecerlo y rechazarlo despues. Sin nadie que
            // pueda administrar la plataforma, nadie podria volver a activar a
            // los demas.
            binding.saCuentaAccion.setVisibility(cuenta.desactivable ? View.VISIBLE : View.GONE);
            binding.saCuentaAccion.setOnClickListener(null);
            binding.saCuentaAccion.setEnabled(!cuenta.pendiente);
            if (!cuenta.desactivable) {
                return;
            }
            binding.saCuentaAccion.setText(cuenta.pendiente
                    ? R.string.sa_usuarios_firebase_guardando
                    : cuenta.activa ? R.string.sa_desactivar : R.string.sa_activar);
            binding.saCuentaAccion.setContentDescription(itemView.getContext().getString(
                    cuenta.pendiente ? R.string.sa_usuarios_firebase_guardando_cuenta
                            : cuenta.activa ? R.string.sa_usuarios_firebase_desactivar_cuenta
                            : R.string.sa_usuarios_firebase_activar_cuenta, nombre));
            binding.saCuentaAccion.setOnClickListener(v -> {
                if (!pendientes.contains(cuenta.id)) {
                    alAccionar.accionar(cuenta.id, nombre, !cuenta.activa);
                }
            });
        }

        private void pintarEstado(boolean activa) {
            fondo(activa ? R.drawable.bg_badge_success : R.drawable.bg_badge_error);
            binding.saCuentaEstado.setTextColor(ColorStateList.valueOf(ContextCompat.getColor(
                    itemView.getContext(),
                    activa ? R.color.colorOnSuccessContainer : R.color.colorOnErrorContainer)));
        }

        private void fondo(@DrawableRes int fondo) {
            binding.saCuentaEstado.setBackground(
                    ContextCompat.getDrawable(itemView.getContext(), fondo));
        }
    }

    private static final DiffUtil.ItemCallback<Cuenta> DIFF =
            new DiffUtil.ItemCallback<Cuenta>() {
                @Override
                public boolean areItemsTheSame(@NonNull Cuenta a, @NonNull Cuenta b) {
                    return a.id.equals(b.id);
                }

                @Override
                public boolean areContentsTheSame(@NonNull Cuenta a, @NonNull Cuenta b) {
                    return a.activa == b.activa
                            && a.desactivable == b.desactivable
                            && a.pendiente == b.pendiente
                            && a.nombre.equals(b.nombre)
                            && mismoTexto(a.email, b.email)
                            && mismoTexto(a.rol, b.rol);
                }
            };

    private static boolean mismoTexto(@Nullable String a, @Nullable String b) {
        return a == null ? b == null : a.equals(b);
    }

    @NonNull private static String texto(@Nullable String texto) {
        return texto == null ? "" : texto.trim();
    }
}
