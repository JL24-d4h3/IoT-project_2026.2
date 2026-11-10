package org.iot.project.ui.superadmin.conductores;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.content.ContextCompat;
import androidx.recyclerview.widget.DiffUtil;
import androidx.recyclerview.widget.ListAdapter;
import androidx.recyclerview.widget.RecyclerView;

import org.iot.project.R;
import org.iot.project.databinding.ItemConductorSaBinding;
import org.iot.project.models.Driver;
import org.iot.project.models.TaxistaAdministrado;
import org.iot.project.models.Vehicle;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;

/** Dos estados y dos acciones explícitas, sin convertir un taxista web en una cuenta Firebase. */
public class ConductorSaAdapter extends ListAdapter<ConductorSaAdapter.FilaDatos, ConductorSaAdapter.Fila> {
    interface AlAccionar {
        void accionar(String id, String nombre, SuperadminConductoresViewModel.Accion accion, boolean valor);
    }

    static final class FilaDatos {
        final String id;
        final String nombre;
        final String contacto;
        final String documento;
        final String vehiculo;
        final boolean habilitado;
        final boolean cuentaActiva;
        final boolean guardando;

        FilaDatos(TaxistaAdministrado taxista, boolean guardando) {
            Driver perfil = taxista.getPerfil();
            id = taxista.getId();
            nombre = perfil.getNombreCompleto().trim();
            contacto = concatenar(perfil.getEmail(), perfil.getTelefono());
            // El DTO administrativo entrega el documento ya enmascarado.
            documento = concatenar(perfil.getTipoDocumento(), perfil.getNumeroDocumento());
            Vehicle auto = perfil.getVehiculo();
            vehiculo = auto == null ? "" : concatenar(auto.getDescripcion(), auto.getPlaca());
            habilitado = taxista.isHabilitado();
            cuentaActiva = taxista.isCuentaActiva();
            this.guardando = guardando;
        }
    }

    private final AlAccionar accion;
    private List<TaxistaAdministrado> actuales = Collections.emptyList();
    private Set<String> pendientes = Collections.emptySet();

    public ConductorSaAdapter(AlAccionar accion) { super(DIFF); this.accion = accion; }

    public void mostrar(List<TaxistaAdministrado> taxistas) {
        actuales = new ArrayList<>(taxistas);
        publicar();
    }

    public void mostrarPendientes(Set<String> ids) {
        pendientes = new HashSet<>(ids);
        publicar();
    }

    private void publicar() {
        List<FilaDatos> filas = new ArrayList<>();
        for (TaxistaAdministrado taxista : actuales) {
            filas.add(new FilaDatos(taxista, pendientes.contains(taxista.getId())));
        }
        submitList(filas);
    }

    @NonNull @Override public Fila onCreateViewHolder(@NonNull ViewGroup padre, int tipo) {
        return new Fila(ItemConductorSaBinding.inflate(LayoutInflater.from(padre.getContext()), padre, false));
    }

    @Override public void onBindViewHolder(@NonNull Fila fila, int posicion) { fila.bind(getItem(posicion)); }

    class Fila extends RecyclerView.ViewHolder {
        private final ItemConductorSaBinding binding;
        Fila(ItemConductorSaBinding binding) { super(binding.getRoot()); this.binding = binding; }

        void bind(FilaDatos datos) {
            binding.saConductorNombre.setText(datos.nombre.isEmpty()
                    ? itemView.getContext().getString(R.string.sa_taxistas_sin_nombre) : datos.nombre);
            binding.saConductorContacto.setText(datos.contacto);
            binding.saConductorContacto.setVisibility(datos.contacto.isEmpty() ? View.GONE : View.VISIBLE);
            binding.saConductorDocumento.setText(datos.documento);
            binding.saConductorDocumento.setVisibility(datos.documento.isEmpty() ? View.GONE : View.VISIBLE);
            binding.saConductorVehiculo.setText(datos.vehiculo.isEmpty()
                    ? itemView.getContext().getString(R.string.sa_conductor_sin_vehiculo) : datos.vehiculo);

            binding.saConductorInsigniaEstado.setText(datos.habilitado
                    ? R.string.sa_taxistas_aprobado : R.string.sa_taxistas_sin_aprobar);
            badge(binding.saConductorInsigniaEstado, datos.habilitado);
            binding.saConductorInsigniaCuenta.setText(datos.cuentaActiva
                    ? R.string.sa_taxistas_cuenta_activa : R.string.sa_taxistas_cuenta_inactiva);
            badge(binding.saConductorInsigniaCuenta, datos.cuentaActiva);
            binding.saConductorNota.setVisibility(datos.guardando || !datos.habilitado || !datos.cuentaActiva
                    ? View.VISIBLE : View.GONE);
            binding.saConductorNota.setText(datos.guardando ? R.string.sa_taxistas_guardando
                    : !datos.cuentaActiva ? R.string.sa_taxistas_cuenta_inactiva_nota
                    : R.string.sa_taxistas_sin_aprobar_nota);

            binding.saConductorAccion.setText(datos.habilitado
                    ? R.string.sa_taxistas_retirar : R.string.sa_taxistas_aprobar);
            binding.saConductorCuentaAccion.setText(datos.cuentaActiva
                    ? R.string.sa_taxistas_desactivar : R.string.sa_taxistas_activar);
            binding.saConductorAccion.setEnabled(!datos.guardando);
            binding.saConductorCuentaAccion.setEnabled(!datos.guardando);
            binding.saConductorAccion.setOnClickListener(v -> {
                if (!pendientes.contains(datos.id)) accion.accionar(datos.id, datos.nombre,
                        SuperadminConductoresViewModel.Accion.HABILITACION, !datos.habilitado);
            });
            binding.saConductorCuentaAccion.setOnClickListener(v -> {
                if (!pendientes.contains(datos.id)) accion.accionar(datos.id, datos.nombre,
                        SuperadminConductoresViewModel.Accion.CUENTA, !datos.cuentaActiva);
            });
        }

        private void badge(android.widget.TextView etiqueta, boolean positivo) {
            etiqueta.setBackgroundResource(positivo ? R.drawable.bg_badge_success : R.drawable.bg_badge_warning);
            etiqueta.setTextColor(ContextCompat.getColor(itemView.getContext(), positivo
                    ? R.color.colorOnSuccessContainer : R.color.colorOnWarningContainer));
        }
    }

    static String concatenar(@Nullable String primero, @Nullable String segundo) {
        String a = primero == null ? "" : primero.trim();
        String b = segundo == null ? "" : segundo.trim();
        return a.isEmpty() ? b : b.isEmpty() ? a : a + " · " + b;
    }

    private static final DiffUtil.ItemCallback<FilaDatos> DIFF = new DiffUtil.ItemCallback<FilaDatos>() {
        @Override public boolean areItemsTheSame(@NonNull FilaDatos a, @NonNull FilaDatos b) {
            return a.id.equals(b.id);
        }
        @Override public boolean areContentsTheSame(@NonNull FilaDatos a, @NonNull FilaDatos b) {
            return a.habilitado == b.habilitado && a.cuentaActiva == b.cuentaActiva
                    && a.guardando == b.guardando && Objects.equals(a.nombre, b.nombre)
                    && Objects.equals(a.contacto, b.contacto) && Objects.equals(a.documento, b.documento)
                    && Objects.equals(a.vehiculo, b.vehiculo);
        }
    };
}
