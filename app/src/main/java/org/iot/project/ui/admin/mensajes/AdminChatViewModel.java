package org.iot.project.ui.admin.mensajes;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;
import androidx.lifecycle.Observer;
import androidx.lifecycle.SavedStateHandle;
import androidx.lifecycle.ViewModel;

import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;

import org.iot.project.core.ResultCallback;
import org.iot.project.core.ServiceLocator;
import org.iot.project.core.SessionManager;
import org.iot.project.core.UiState;
import org.iot.project.data.repository.ChatRepository;
import org.iot.project.models.ConversacionDeHotel;
import org.iot.project.models.Conversation;
import org.iot.project.models.Cuenta;
import org.iot.project.models.Message;
import org.iot.project.models.Role;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;

/** Chat del hotel: escucha visible, borrador recuperable y envíos idempotentes. */
public class AdminChatViewModel extends ViewModel {
    private static final String UID = "admin_chat_uid";
    private static final String HOTEL = "admin_chat_hotel";
    private static final String BOOKING = "admin_chat_booking";
    private static final String BORRADOR = "admin_chat_borrador";
    private static final String MENSAJE_ID = "admin_chat_mensaje_id";
    private static final String TEXTO_ENVIADO = "admin_chat_texto_enviado";

    private final SavedStateHandle guardado;
    private final MutableLiveData<UiState<ConversacionDeHotel>> conversacion =
            new MutableLiveData<>();
    private final MutableLiveData<UiState<Conversation>> envio = new MutableLiveData<>();
    private final MutableLiveData<String> errorLectura = new MutableLiveData<>();
    private final Set<String> lecturasEnCurso = new HashSet<>();
    private final Set<String> lecturasConfirmadas = new HashSet<>();
    private final Set<String> lecturasFallidas = new HashSet<>();
    private final Observer<Cuenta> sesion = cuenta -> revisarSesion();
    @Nullable private ChatRepository.Suscripcion suscripcion;
    @Nullable private String uid;
    @Nullable private String hotelId;
    @Nullable private String bookingId;
    private long generacionEscucha;
    private long generacionCuenta;
    private boolean visible;

    public AdminChatViewModel(SavedStateHandle guardado) {
        this.guardado = guardado;
        uid = guardado.get(UID);
        hotelId = guardado.get(HOTEL);
        bookingId = guardado.get(BOOKING);
        SessionManager.sesion().observeForever(sesion);
    }

    public LiveData<UiState<ConversacionDeHotel>> getConversacion() { return conversacion; }
    public LiveData<UiState<Conversation>> getEnvio() { return envio; }
    public LiveData<String> getErrorLectura() { return errorLectura; }
    public LiveData<String> getBorrador() { return guardado.getLiveData(BORRADOR, ""); }

    public void cargar(@NonNull String reservaId) {
        revisarSesion();
        if (!reservaId.equals(bookingId)) {
            cerrarEscucha();
            limpiarCuenta();
            bookingId = reservaId;
            guardado.set(BOOKING, reservaId);
            conversacion.setValue(UiState.loading());
        }
        conectar();
    }

    public void iniciarSeguimiento() {
        visible = true;
        revisarSesion();
        conectar();
    }

    public void detenerSeguimiento() {
        visible = false;
        cerrarEscucha();
    }

    public void recargar() {
        cerrarEscucha();
        conversacion.setValue(UiState.loading());
        conectar();
    }

    private void revisarSesion() {
        String actualUid = SessionManager.getRolActivo() == Role.ADMIN_HOTEL
                ? SessionManager.getUsuarioId() : null;
        String actualHotel = SessionManager.getHotelAdministrado();
        if (Objects.equals(uid, actualUid) && Objects.equals(hotelId, actualHotel)) return;
        cerrarEscucha();
        limpiarCuenta();
        uid = actualUid;
        hotelId = actualHotel;
        guardado.set(UID, uid);
        guardado.set(HOTEL, hotelId);
        conversacion.setValue(UiState.loading());
        conectar();
    }

    private void limpiarCuenta() {
        generacionCuenta++;
        guardado.set(BORRADOR, "");
        guardado.remove(MENSAJE_ID);
        guardado.remove(TEXTO_ENVIADO);
        envio.setValue(null);
        errorLectura.setValue(null);
        lecturasEnCurso.clear();
        lecturasConfirmadas.clear();
        lecturasFallidas.clear();
    }

    private void conectar() {
        if (!visible || suscripcion != null || bookingId == null) return;
        if (uid == null || hotelId == null || !sesionVigente(uid, hotelId)) {
            conversacion.setValue(UiState.error("No tienes un hotel asignado para abrir este chat."));
            return;
        }
        final String cuenta = uid;
        final String hotel = hotelId;
        final String reserva = bookingId;
        final long version = ++generacionEscucha;
        conversacion.setValue(UiState.loading());
        suscripcion = ServiceLocator.chats().escucharConversacionDeHotel(reserva,
                new ResultCallback<ConversacionDeHotel>() {
                    @Override public void onExito(@NonNull ConversacionDeHotel datos) {
                        if (!escuchaVigente(cuenta, hotel, reserva, version)) return;
                        if (!hotel.equals(datos.getConversacion().getHotelId())
                                || !reserva.equals(datos.getBookingId())
                                || !datos.getEstadia().getReserva().permiteChat()) {
                            conversacion.setValue(UiState.error(
                                    "El chat se cierra cuando termina la estadía."));
                            cerrarEscucha();
                            return;
                        }
                        conversacion.setValue(UiState.success(datos));
                        confirmarDesdeHistorial(datos.getConversacion());
                    }
                    @Override public void onError(@NonNull String mensaje) {
                        if (escuchaVigente(cuenta, hotel, reserva, version)) {
                            conversacion.setValue(UiState.error(mensaje));
                        }
                    }
                });
    }

    public void actualizarBorrador(@NonNull String texto) {
        if (tieneEnvioPendiente()) return;
        if (!texto.equals(guardado.<String>get(BORRADOR))) guardado.set(BORRADOR, texto);
    }

    public boolean tieneEnvioPendiente() { return guardado.get(MENSAJE_ID) != null; }

    public void responder(@NonNull String texto) {
        UiState<ConversacionDeHotel> actual = conversacion.getValue();
        UiState<Conversation> anterior = envio.getValue();
        if (!visible || uid == null || hotelId == null || bookingId == null
                || !sesionVigente(uid, hotelId) || actual == null || !actual.isSuccess()
                || (anterior != null && anterior.isLoading())) return;
        String mensajeId = guardado.get(MENSAJE_ID);
        String pendiente = guardado.get(TEXTO_ENVIADO);
        String limpio = mensajeId != null && pendiente != null ? pendiente : texto.trim();
        if (limpio.isEmpty()) return;
        if (limpio.length() > 4000) {
            envio.setValue(UiState.error("Escribe como máximo 4000 caracteres por mensaje."));
            return;
        }
        if (mensajeId == null) {
            actualizarBorrador(texto);
            mensajeId = UUID.randomUUID().toString();
            guardado.set(MENSAJE_ID, mensajeId);
            guardado.set(TEXTO_ENVIADO, limpio);
        }
        final String cuenta = uid;
        final String hotel = hotelId;
        final String reserva = bookingId;
        final String idSolicitud = mensajeId;
        final long version = generacionCuenta;
        envio.setValue(UiState.loading());
        ServiceLocator.chats().responder(reserva, mensajeId, limpio,
                new ResultCallback<Conversation>() {
                    @Override public void onExito(@NonNull Conversation datos) {
                        if (cuentaVigente(cuenta, hotel, reserva, version)) {
                            confirmarEnvio(idSolicitud, datos);
                        }
                    }
                    @Override public void onError(@NonNull String mensaje) {
                        if (cuentaVigente(cuenta, hotel, reserva, version)
                                && idSolicitud.equals(guardado.<String>get(MENSAJE_ID))) {
                            envio.setValue(UiState.error(mensaje));
                        }
                    }
                });
    }

    private void confirmarDesdeHistorial(Conversation datos) {
        String pendienteId = guardado.get(MENSAJE_ID);
        String pendienteTexto = guardado.get(TEXTO_ENVIADO);
        if (pendienteId == null || pendienteTexto == null) return;
        for (Message mensaje : datos.getMensajes()) {
            if (pendienteId.equals(mensaje.getId()) && mensaje.esDe(Message.Autor.HOTEL)
                    && pendienteTexto.equals(mensaje.getTexto())) {
                confirmarEnvio(pendienteId, datos);
                return;
            }
        }
    }

    private void confirmarEnvio(String idSolicitud, Conversation datos) {
        // Un callback tardío de un mensaje ya confirmado no borra el borrador siguiente.
        if (!idSolicitud.equals(guardado.<String>get(MENSAJE_ID))) return;
        guardado.remove(MENSAJE_ID);
        guardado.remove(TEXTO_ENVIADO);
        guardado.set(BORRADOR, "");
        envio.setValue(UiState.success(datos));
    }

    /** Solo recibe los mensajes que el RecyclerView ya presentó en pantalla. */
    public void marcarVisibles(@NonNull List<Message> mensajes) {
        UiState<ConversacionDeHotel> estado = conversacion.getValue();
        if (!visible || uid == null || hotelId == null || bookingId == null
                || !sesionVigente(uid, hotelId) || estado == null || !estado.isSuccess()) return;
        List<String> ids = new ArrayList<>();
        for (Message mensaje : mensajes) {
            if (mensaje.esDe(Message.Autor.CLIENTE) && !mensaje.isLeido()
                    && !lecturasEnCurso.contains(mensaje.getId())
                    && !lecturasConfirmadas.contains(mensaje.getId())
                    && !lecturasFallidas.contains(mensaje.getId())) ids.add(mensaje.getId());
        }
        if (ids.isEmpty()) return;
        final String cuenta = uid;
        final String hotel = hotelId;
        final String reserva = bookingId;
        final long version = generacionCuenta;
        lecturasEnCurso.addAll(ids);
        ServiceLocator.chats().marcarLeido(reserva, Message.Autor.HOTEL, ids,
                new ResultCallback<Conversation>() {
                    @Override public void onExito(@NonNull Conversation datos) {
                        if (!cuentaVigente(cuenta, hotel, reserva, version)) return;
                        lecturasEnCurso.removeAll(ids);
                        lecturasConfirmadas.addAll(ids);
                    }
                    @Override public void onError(@NonNull String mensaje) {
                        if (!cuentaVigente(cuenta, hotel, reserva, version)) return;
                        lecturasEnCurso.removeAll(ids);
                        lecturasFallidas.addAll(ids);
                        errorLectura.setValue(mensaje);
                    }
                });
    }

    public void reintentarLectura() {
        lecturasFallidas.clear();
        errorLectura.setValue(null);
    }

    public void limpiarErrorLectura() { errorLectura.setValue(null); }
    public void limpiarEnvio() { envio.setValue(null); }

    private boolean escuchaVigente(String cuenta, String hotel, String reserva, long version) {
        return visible && version == generacionEscucha && reserva.equals(bookingId)
                && sesionVigente(cuenta, hotel);
    }

    private boolean cuentaVigente(String cuenta, String hotel, String reserva, long version) {
        return version == generacionCuenta && reserva.equals(bookingId)
                && sesionVigente(cuenta, hotel);
    }

    private boolean sesionVigente(String cuenta, String hotel) {
        FirebaseUser usuario = FirebaseAuth.getInstance().getCurrentUser();
        return usuario != null && cuenta.equals(usuario.getUid())
                && cuenta.equals(SessionManager.getUsuarioId())
                && hotel.equals(SessionManager.getHotelAdministrado())
                && SessionManager.getRolActivo() == Role.ADMIN_HOTEL;
    }

    private void cerrarEscucha() {
        generacionEscucha++;
        if (suscripcion != null) {
            suscripcion.cerrar();
            suscripcion = null;
        }
    }

    @Override protected void onCleared() {
        cerrarEscucha();
        SessionManager.sesion().removeObserver(sesion);
    }
}
