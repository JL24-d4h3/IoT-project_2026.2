package org.iot.project.ui.client.chat;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;
import androidx.lifecycle.Observer;
import androidx.lifecycle.SavedStateHandle;
import androidx.lifecycle.ViewModel;

import com.google.firebase.auth.FirebaseAuth;

import org.iot.project.core.ResultCallback;
import org.iot.project.core.ServiceLocator;
import org.iot.project.core.SessionManager;
import org.iot.project.core.UiState;
import org.iot.project.data.repository.ChatRepository;
import org.iot.project.models.Booking;
import org.iot.project.models.Conversation;
import org.iot.project.models.Cuenta;
import org.iot.project.models.Hotel;
import org.iot.project.models.Message;
import org.iot.project.models.Role;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

/** Conserva el borrador y la identidad del envío hasta su confirmación del servidor. */
public class ChatViewModel extends ViewModel {

    private static final String TEXTO = "chat_cliente_texto";
    private static final String MENSAJE_ID = "chat_cliente_mensaje_id";
    private static final String UID = "chat_cliente_uid";
    private static final String RESERVA = "chat_cliente_reserva";

    private final SavedStateHandle guardado;
    private final MutableLiveData<UiState<Conversation>> conversacion = new MutableLiveData<>();
    private final MutableLiveData<UiState<Conversation>> envio = new MutableLiveData<>();
    private final MutableLiveData<Boolean> puedeEscribir = new MutableLiveData<>(false);
    private final MutableLiveData<String> titulo = new MutableLiveData<>();
    private final MutableLiveData<String> errorLectura = new MutableLiveData<>();
    private final MutableLiveData<String> borrador;
    private final Observer<Cuenta> observadorSesion;
    private final Set<String> lecturasConfirmadas = new HashSet<>();
    private List<String> lecturasPendientes = new ArrayList<>();
    private ChatRepository.Suscripcion escucha;
    private Conversation ultima;
    private String bookingId;
    private String uid;
    private long contexto;
    private long generacionEscucha;
    private boolean iniciada;
    private boolean visible;
    private boolean enviando;
    private boolean leyendo;

    public ChatViewModel(@NonNull SavedStateHandle guardado) {
        this.guardado = guardado;
        borrador = guardado.getLiveData(TEXTO, "");
        observadorSesion = cuenta -> {
            if (bookingId != null && !identidadVigente()) {
                contexto++;
                cerrarEscucha();
                ultima = null;
                enviando = false;
                leyendo = false;
                guardado.set(TEXTO, "");
                guardado.set(MENSAJE_ID, null);
                envio.setValue(null);
                errorLectura.setValue(null);
                titulo.setValue(null);
                conversacion.setValue(UiState.error("La sesión cambió. Vuelve a entrar al chat desde tu reserva."));
            }
        };
        SessionManager.sesion().observeForever(observadorSesion);
    }

    public LiveData<UiState<Conversation>> getConversacion() { return conversacion; }
    public LiveData<UiState<Conversation>> getEnvio() { return envio; }
    public LiveData<Boolean> getPuedeEscribir() { return puedeEscribir; }
    public LiveData<String> getTitulo() { return titulo; }
    public LiveData<String> getErrorLectura() { return errorLectura; }
    public LiveData<String> getBorrador() { return borrador; }
    @Nullable public Conversation getUltimaConversacion() { return ultima; }
    public boolean estaEnviando() { return enviando; }
    public boolean tieneEnvioPendiente() { return guardado.get(MENSAJE_ID) != null; }

    public void cargar(@NonNull String id) {
        String actualUid = SessionManager.getUsuarioIdSeguro();
        if (id.equals(bookingId) && actualUid.equals(uid)) { return; }
        cerrarEscucha();
        contexto++;
        bookingId = id;
        uid = actualUid;
        if (!id.equals(guardado.get(RESERVA)) || !actualUid.equals(guardado.get(UID))) {
            guardado.set(TEXTO, "");
            guardado.set(MENSAJE_ID, null);
        }
        guardado.set(RESERVA, id);
        guardado.set(UID, actualUid);
        ultima = null;
        enviando = false;
        leyendo = false;
        lecturasConfirmadas.clear();
        lecturasPendientes.clear();
        errorLectura.setValue(null);
        envio.setValue(null);
        titulo.setValue(null);
        conversacion.setValue(UiState.loading());
        if (!identidadVigente()) {
            conversacion.setValue(UiState.error("Inicia sesión como cliente para abrir el chat de tu reserva."));
            return;
        }
        cargarTitulo(contexto);
        if (iniciada) { escuchar(); }
    }

    public void iniciar() {
        iniciada = true;
        if (bookingId != null && escucha == null) { escuchar(); }
    }

    public void detener() {
        iniciada = false;
        visible = false;
        cerrarEscucha();
    }

    public void setVisible(boolean valor) { visible = valor; }

    public void recargar() {
        cerrarEscucha();
        if (ultima == null) { conversacion.setValue(UiState.loading()); }
        if (iniciada) { escuchar(); }
    }

    private void escuchar() {
        if (!identidadVigente() || bookingId == null) {
            puedeEscribir.setValue(false);
            conversacion.setValue(UiState.error("La sesión del cliente ya no está disponible."));
            return;
        }
        final long solicitud = contexto;
        final long version = ++generacionEscucha;
        escucha = ServiceLocator.chats().escucharConversacionDe(bookingId,
                new ResultCallback<Conversation>() {
                    @Override public void onExito(@NonNull Conversation datos) {
                        if (!vigente(solicitud) || version != generacionEscucha || !iniciada) { return; }
                        ultima = datos;
                        puedeEscribir.setValue(true);
                        confirmarSiEntregado(datos);
                        conversacion.setValue(UiState.success(datos));
                    }
                    @Override public void onError(@NonNull String mensaje) {
                        if (!vigente(solicitud) || version != generacionEscucha || !iniciada) { return; }
                        puedeEscribir.setValue(false);
                        conversacion.setValue(UiState.error(mensaje));
                    }
                });
    }

    private void cerrarEscucha() {
        generacionEscucha++;
        if (escucha != null) {
            escucha.cerrar();
            escucha = null;
        }
        puedeEscribir.setValue(false);
    }

    private void cargarTitulo(long solicitud) {
        ServiceLocator.reservas().obtener(bookingId, new ResultCallback<Booking>() {
            @Override public void onExito(@NonNull Booking reserva) {
                if (!vigente(solicitud)) { return; }
                if (!reserva.getHotelNombre().trim().isEmpty()) {
                    titulo.setValue(reserva.getHotelNombre());
                } else {
                    ServiceLocator.hoteles().obtenerDeReserva(bookingId, new ResultCallback<Hotel>() {
                        @Override public void onExito(@NonNull Hotel hotel) {
                            if (vigente(solicitud)) { titulo.setValue(hotel.getNombre()); }
                        }
                        @Override public void onError(@NonNull String mensaje) {
                            // El encabezado conserva el título genérico; el chat no depende del catálogo.
                        }
                    });
                }
            }
            @Override public void onError(@NonNull String mensaje) {
                // El listener de la conversación informa cualquier problema de acceso.
            }
        });
    }

    public void actualizarBorrador(@NonNull String texto) {
        if (!tieneEnvioPendiente()) { guardado.set(TEXTO, texto); }
    }

    public void enviar() {
        if (!Boolean.TRUE.equals(puedeEscribir.getValue()) || enviando || !identidadVigente()) { return; }
        String texto = borrador.getValue();
        if (texto == null || texto.trim().isEmpty()) { return; }
        if (texto.trim().length() > 4000) {
            envio.setValue(UiState.error("El mensaje no puede tener más de 4000 caracteres."));
            return;
        }
        String existente = guardado.get(MENSAJE_ID);
        final String mensajeId = existente == null ? UUID.randomUUID().toString() : existente;
        guardado.set(MENSAJE_ID, mensajeId);
        guardado.set(TEXTO, texto.trim());
        final long solicitud = contexto;
        enviando = true;
        envio.setValue(UiState.loading());
        ServiceLocator.chats().enviar(bookingId, mensajeId, texto.trim(), new ResultCallback<Conversation>() {
            @Override public void onExito(@NonNull Conversation datos) {
                if (!vigente(solicitud) || !mensajeId.equals(guardado.get(MENSAJE_ID))) { return; }
                confirmarEnvio(datos);
            }
            @Override public void onError(@NonNull String mensaje) {
                if (!vigente(solicitud) || !mensajeId.equals(guardado.get(MENSAJE_ID))) { return; }
                enviando = false;
                // El UUID y el texto se conservan: un error de red puede ocurrir después del commit.
                envio.setValue(UiState.error(mensaje));
            }
        });
    }

    private void confirmarSiEntregado(Conversation datos) {
        String pendiente = guardado.get(MENSAJE_ID);
        String textoPendiente = guardado.get(TEXTO);
        if (pendiente == null) { return; }
        for (Message mensaje : datos.getMensajes()) {
            if (pendiente.equals(mensaje.getId()) && mensaje.esDe(Message.Autor.CLIENTE)
                    && mensaje.getTexto().equals(textoPendiente)) {
                confirmarEnvio(datos);
                return;
            }
        }
    }

    private void confirmarEnvio(Conversation datos) {
        enviando = false;
        guardado.set(MENSAJE_ID, null);
        guardado.set(TEXTO, "");
        envio.setValue(UiState.success(datos));
    }

    /** Solo recibe mensajes que el adaptador presentó con la pantalla visible. */
    public void marcarEntregados(@NonNull List<Message> entregados) {
        if (!visible || !iniciada || !Boolean.TRUE.equals(puedeEscribir.getValue()) || leyendo) { return; }
        List<String> ids = new ArrayList<>();
        for (Message mensaje : entregados) {
            if (!mensaje.esDe(Message.Autor.CLIENTE) && !mensaje.isLeido()
                    && !lecturasConfirmadas.contains(mensaje.getId())) {
                ids.add(mensaje.getId());
            }
        }
        lecturasPendientes = ids;
        if (ids.isEmpty() && errorLectura.getValue() != null) {
            errorLectura.setValue(null);
        }
        if (!ids.isEmpty() && errorLectura.getValue() == null) { marcarLectura(ids); }
    }

    public void reintentarLectura() {
        if (visible && iniciada && !leyendo && !lecturasPendientes.isEmpty()
                && Boolean.TRUE.equals(puedeEscribir.getValue())) {
            marcarLectura(new ArrayList<>(lecturasPendientes));
        }
    }

    private void marcarLectura(List<String> ids) {
        final long solicitud = contexto;
        leyendo = true;
        ServiceLocator.chats().marcarLeido(bookingId, Message.Autor.CLIENTE, ids,
                new ResultCallback<Conversation>() {
                    @Override public void onExito(@NonNull Conversation datos) {
                        if (!vigente(solicitud)) { return; }
                        leyendo = false;
                        lecturasConfirmadas.addAll(ids);
                        errorLectura.setValue(null);
                    }
                    @Override public void onError(@NonNull String mensaje) {
                        if (!vigente(solicitud)) { return; }
                        leyendo = false;
                        errorLectura.setValue(mensaje);
                    }
                });
    }

    private boolean identidadVigente() {
        return uid != null && !uid.isEmpty() && uid.equals(SessionManager.getUsuarioId())
                && SessionManager.getRolActivo() == Role.CLIENTE
                && FirebaseAuth.getInstance().getCurrentUser() != null
                && uid.equals(FirebaseAuth.getInstance().getCurrentUser().getUid());
    }

    private boolean vigente(long solicitud) { return solicitud == contexto && identidadVigente(); }

    public void limpiarEnvio() { if (!enviando) { envio.setValue(null); } }

    @Override protected void onCleared() {
        contexto++;
        cerrarEscucha();
        SessionManager.sesion().removeObserver(observadorSesion);
    }
}
