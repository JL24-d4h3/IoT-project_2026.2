package org.iot.project;

import android.os.Bundle;
import android.view.Menu;
import android.view.View;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.lifecycle.Lifecycle;
import androidx.lifecycle.ViewModelProvider;
import androidx.navigation.NavController;
import androidx.navigation.NavDestination;
import androidx.navigation.NavOptions;
import androidx.navigation.fragment.NavHostFragment;
import androidx.navigation.ui.NavigationUI;

import org.iot.project.core.Roles;
import org.iot.project.core.AvisosClienteViewModel;
import org.iot.project.core.SessionManager;
import org.iot.project.databinding.ActivityMainBinding;
import org.iot.project.models.Cuenta;
import org.iot.project.utils.InsetUtils;

import java.util.Collections;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;
import java.util.Objects;

/**
 * Unica Activity de la aplicacion: aloja el grafo de navegacion y la barra
 * inferior. Cada pantalla es un Fragment.
 *
 * <p>Los cuatro roles (cliente, administrador de hotel, conductor, superadmin)
 * comparten esta Activity; lo que cambia es el grafo que se carga, de modo que
 * la barra inferior muestra las secciones propias de cada rol.
 *
 * <p>El cambio de grafo lo decide la sesion: esta Activity observa
 * {@link SessionManager#sesion()} y, cuando aparece o desaparece una cuenta,
 * sustituye el grafo y la barra. Ni las pantallas de acceso ni las de rol
 * navegan a ninguna parte por su cuenta para entrar o salir; solo cambian la
 * sesion. Asi el momento en que se cambia de interfaz esta en un solo sitio.
 */
public class MainActivity extends AppCompatActivity {

    /**
     * Destinos que ocupan la pantalla completa y por tanto esconden la barra
     * inferior: reserva, pago, chat, seguimiento del taxi.
     *
     * <p>Se llena a medida que se revisan las pantallas. Entra ahora el chat del
     * administrador, que apila su barra de escritura sobre la de navegacion: dos
     * barras pegadas al borde inferior se leen como una sola mal dibujada. Las
     * del cliente —reserva, pago, chat— siguen sin revisar y por eso no estan.
     */
    private static final Set<Integer> PANTALLAS_COMPLETAS = Collections.unmodifiableSet(
            new HashSet<>(Arrays.asList(R.id.adminChatFragment, R.id.superadminAltaHotelFragment)));

    /**
     * Una barra con una sola seccion no navega a ningun sitio: solo ocupa
     * pantalla. Mientras un rol tenga menos secciones que esto, su barra no se
     * muestra, y aparece sola cuando su grafo crezca.
     */
    private static final int SECCIONES_MINIMAS_PARA_BARRA = 2;

    private ActivityMainBinding binding;
    private NavController navController;
    private AvisosClienteViewModel avisosCliente;
    private String identidadAplicada;
    private String hotelAplicado;
    private static final String CLAVE_UID = "sesionUid";
    private static final String CLAVE_HOTEL = "sesionHotelId";

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        // Tras morir el proceso no restaurar pantallas protegidas antes de Auth.
        String uidGuardado = savedInstanceState == null ? null : savedInstanceState.getString(CLAVE_UID);
        boolean estadoPublico = uidGuardado == null;
        boolean mismaSesion = SessionManager.haySesion()
                && Objects.equals(uidGuardado, SessionManager.getIdentidadId());
        Bundle estadoSeguro = estadoPublico || mismaSesion ? savedInstanceState : null;
        super.onCreate(estadoSeguro);
        identidadAplicada = estadoSeguro == null ? null : estadoSeguro.getString(CLAVE_UID);
        hotelAplicado = estadoSeguro == null ? null : estadoSeguro.getString(CLAVE_HOTEL);
        binding = ActivityMainBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());
        avisosCliente = new ViewModelProvider(this).get(AvisosClienteViewModel.class);

        NavHostFragment host = (NavHostFragment) getSupportFragmentManager()
                .findFragmentById(R.id.nav_host);
        if (host == null) {
            throw new IllegalStateException("Falta el NavHostFragment en activity_main.xml");
        }
        navController = host.getNavController();

        // Registra la sincronizacion inversa: al cambiar de destino, marca el
        // item correspondiente de la barra inferior.
        NavigationUI.setupWithNavController(binding.bottomNav, navController);

        // Se reemplaza el listener de seleccion para conservar el estado de cada
        // pestana (setRestoreState / setSaveState) y no perder el scroll ni el
        // formulario al cambiar de seccion y volver.
        binding.bottomNav.setOnItemSelectedListener(item -> {
            NavOptions opciones = new NavOptions.Builder()
                    .setLaunchSingleTop(true)
                    .setRestoreState(true)
                    .setPopUpTo(navController.getGraph().getStartDestinationId(), false, true)
                    .build();
            try {
                navController.navigate(item.getItemId(), null, opciones);
                return true;
            } catch (IllegalArgumentException e) {
                // El destino no existe en este grafo: el item no debe navegar.
                return false;
            }
        });

        // Volver a tocar la pestana ya activa regresa a su raiz.
        binding.bottomNav.setOnItemReselectedListener(item ->
                navController.popBackStack(item.getItemId(), false));

        navController.addOnDestinationChangedListener(this::alCambiarDestino);

        // La barra de gestos no debe tapar la navegacion inferior.
        InsetUtils.applyBottomPadding(binding.bottomNav);

        // Al registrarlo se recibe el valor actual, si lo hay: tras una rotacion
        // la sesion sigue viva y la interfaz se vuelve a poner sola.
        SessionManager.inicializar();
        SessionManager.sesion().observe(this, this::aplicarSesion);
        SessionManager.restaurando().observe(this, cargando -> {
            boolean visible = Boolean.TRUE.equals(cargando);
            binding.sessionLoading.setVisibility(visible ? View.VISIBLE : View.GONE);
            binding.navHost.setVisibility(visible ? View.INVISIBLE : View.VISIBLE);
        });
        SessionManager.aviso().observe(this, mensaje -> {
            if (mensaje != null) {
                Toast.makeText(this, mensaje, Toast.LENGTH_LONG).show();
                SessionManager.limpiarAviso();
            }
        });
    }

    @Override
    protected void onStart() {
        super.onStart();
        if (avisosCliente != null) avisosCliente.iniciar();
    }

    @Override
    protected void onStop() {
        if (avisosCliente != null) avisosCliente.detener();
        super.onStop();
    }

    @Override
    protected void onSaveInstanceState(@NonNull Bundle outState) {
        super.onSaveInstanceState(outState);
        outState.putString(CLAVE_UID, identidadAplicada);
        outState.putString(CLAVE_HOTEL, hotelAplicado);
    }

    /**
     * Cambia la interfaz entera cuando cambia la sesion.
     *
     * <p>El grafo se sustituye solo si de verdad es otro. Volver a poner el
     * mismo grafo reiniciaria la pila de navegacion del rol a su primera
     * pantalla, y eso ocurriria en cada rotacion de pantalla.
     */
    private void aplicarSesion(@Nullable Cuenta cuenta) {
        Roles roles = cuenta == null ? null : Roles.de(cuenta.getRol());
        int grafo = roles == null ? R.navigation.nav_auth : roles.getGrafo();

        // Antes de cambiar de grafo: al hacerlo se avisa del destino nuevo, y
        // quien decide si hay barra mira que el menu tenga secciones.
        aplicarMenu(roles);

        String identidad = cuenta == null ? null : cuenta.getIdentidadId();
        String hotelId = cuenta == null ? null : cuenta.getHotelId();
        boolean cambioDeCuenta = !Objects.equals(identidadAplicada, identidad);
        boolean cambioDeHotel = !Objects.equals(hotelAplicado, hotelId);
        identidadAplicada = identidad;
        hotelAplicado = hotelId;
        if (navController.getGraph().getId() != grafo || cambioDeCuenta || cambioDeHotel) {
            navController.setGraph(grafo);
        } else {
            actualizarBarra(navController.getCurrentDestination());
        }
        if (getLifecycle().getCurrentState().isAtLeast(Lifecycle.State.STARTED)) {
            avisosCliente.iniciar();
        }
    }

    /**
     * Pone en la barra las secciones del rol, o ninguna si no hay sesion.
     *
     * <p>La barra empieza sin menu —el layout no declara ninguno— porque las
     * secciones son del rol y no de la Activity: mientras no se sepa quien
     * entro, no hay nada que ofrecer.
     */
    private void aplicarMenu(@Nullable Roles roles) {
        Menu menu = binding.bottomNav.getMenu();
        menu.clear();
        if (roles != null) {
            binding.bottomNav.inflateMenu(roles.getMenu());
        }
    }

    private void alCambiarDestino(@NonNull NavController controller,
                                  @NonNull NavDestination destino,
                                  @Nullable Bundle argumentos) {
        actualizarBarra(destino);
    }

    private void actualizarBarra(@Nullable NavDestination destino) {
        boolean visible = destino != null
                && SessionManager.haySesion()
                && binding.bottomNav.getMenu().size() >= SECCIONES_MINIMAS_PARA_BARRA
                && !PANTALLAS_COMPLETAS.contains(destino.getId());
        binding.bottomNav.setVisibility(visible ? View.VISIBLE : View.GONE);
        binding.navDivider.setVisibility(visible ? View.VISIBLE : View.GONE);
    }

    /** Expuesto para que las pantallas puedan navegar sin depender del Fragment. */
    @NonNull
    public NavController getNavController() {
        return navController;
    }
}
