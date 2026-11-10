package org.iot.project.ui.components;

import android.content.Context;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;
import android.os.Bundle;
import android.util.AttributeSet;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.View;
import android.widget.FrameLayout;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.widget.AppCompatTextView;
import androidx.core.content.ContextCompat;

import com.google.android.gms.maps.CameraUpdateFactory;
import com.google.android.gms.maps.GoogleMap;
import com.google.android.gms.maps.GoogleMapOptions;
import com.google.android.gms.maps.MapView;
import com.google.android.gms.maps.model.BitmapDescriptorFactory;
import com.google.android.gms.maps.model.LatLng;
import com.google.android.gms.maps.model.LatLngBounds;
import com.google.android.gms.maps.model.Marker;
import com.google.android.gms.maps.model.MarkerOptions;

import org.iot.project.R;

/** Mapa real del recojo y del último reporte del taxista; no calcula rutas ni tiempos de llegada. */
public class TrackingMapView extends FrameLayout {
    private static final String ESTADO_MAPA = "tracking_google_map";
    private static final double RADIO_TIERRA_M = 6371000d;

    private final AppCompatTextView aviso;
    @Nullable private MapView vistaMapa;
    @Nullable private GoogleMap mapa;
    @Nullable private Marker marcaRecojo;
    @Nullable private Marker marcaConductor;
    @Nullable private LatLng recojo;
    @Nullable private LatLng conductor;
    private boolean creada;
    private boolean destruida;
    private boolean mapaCargado;
    private boolean camaraInicial;

    private final Runnable comprobarCarga = () -> {
        if (!destruida && vistaMapa != null && !mapaCargado) {
            mostrarAviso(R.string.mapa_google_error_carga);
        }
    };

    public TrackingMapView(@NonNull Context context) { this(context, null); }

    public TrackingMapView(@NonNull Context context, @Nullable AttributeSet attrs) {
        this(context, attrs, 0);
    }

    public TrackingMapView(@NonNull Context context, @Nullable AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        aviso = new AppCompatTextView(context);
        aviso.setGravity(Gravity.CENTER);
        aviso.setTextSize(14f);
        aviso.setPadding(dp(16), dp(16), dp(16), dp(16));
        aviso.setTextColor(ContextCompat.getColor(context, R.color.colorTextSecondary));
        aviso.setBackgroundColor(ContextCompat.getColor(context, R.color.colorSurface));
        addView(aviso, new LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT));
        setContentDescription(context.getString(R.string.mapa_google_descripcion));
        mostrarAviso(R.string.mapa_google_cargando);
    }

    /** El fragmento dueño debe propagar su ciclo de vida a estos métodos. */
    public void onCreate(@Nullable Bundle estado) {
        if (creada || destruida) return;
        creada = true;
        // No se construye MapView cuando falta la clave: el SDK no llega a inicializarse.
        if (!tieneClaveConfigurada()) {
            mostrarAviso(R.string.mapa_google_sin_configurar);
            return;
        }
        try {
            vistaMapa = new MapView(getContext(), new GoogleMapOptions()
                    .mapType(GoogleMap.MAP_TYPE_NORMAL)
                    .mapToolbarEnabled(false).zoomControlsEnabled(true));
            addView(vistaMapa, 0, new LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT));
            vistaMapa.onCreate(estado == null ? null : estado.getBundle(ESTADO_MAPA));
            vistaMapa.getMapAsync(googleMap -> {
                if (destruida || vistaMapa == null) return;
                mapa = googleMap;
                googleMap.getUiSettings().setMyLocationButtonEnabled(false);
                googleMap.getUiSettings().setMapToolbarEnabled(false);
                // Ver al taxista no requiere acceder al GPS del teléfono del cliente.
                googleMap.setOnMapLoadedCallback(() -> {
                    if (destruida) return;
                    mapaCargado = true;
                    removeCallbacks(comprobarCarga);
                    aviso.setVisibility(View.GONE);
                });
                actualizarMarcadores();
                encuadrar(true);
            });
        } catch (RuntimeException error) {
            liberarMapa();
            mostrarAviso(R.string.mapa_google_no_disponible);
        }
    }

    public void onStart() {
        if (vistaMapa != null && !destruida) {
            vistaMapa.onStart();
            if (!mapaCargado) {
                removeCallbacks(comprobarCarga);
                postDelayed(comprobarCarga, 15_000L);
            }
        }
    }

    public void onResume() { if (vistaMapa != null && !destruida) vistaMapa.onResume(); }
    public void onPause() { if (vistaMapa != null && !destruida) vistaMapa.onPause(); }

    public void onStop() {
        removeCallbacks(comprobarCarga);
        if (vistaMapa != null && !destruida) vistaMapa.onStop();
    }

    public void onSaveInstanceState(@NonNull Bundle estado) {
        if (vistaMapa == null || destruida) return;
        Bundle mapaGuardado = new Bundle();
        vistaMapa.onSaveInstanceState(mapaGuardado);
        estado.putBundle(ESTADO_MAPA, mapaGuardado);
    }

    public void onLowMemory() { if (vistaMapa != null && !destruida) vistaMapa.onLowMemory(); }

    public void onDestroy() {
        if (destruida) return;
        destruida = true;
        removeCallbacks(comprobarCarga);
        liberarMapa();
    }

    public void setRecojo(double latitud, double longitud) {
        LatLng siguiente = coordenadasValidas(latitud, longitud) ? new LatLng(latitud, longitud) : null;
        if (iguales(recojo, siguiente)) return;
        recojo = siguiente;
        camaraInicial = false;
        actualizarMarcadores();
        encuadrar(true);
    }

    public void setConductor(double latitud, double longitud) {
        LatLng siguiente = coordenadasValidas(latitud, longitud) ? new LatLng(latitud, longitud) : null;
        if (iguales(conductor, siguiente)) return;
        conductor = siguiente;
        actualizarMarcadores();
        encuadrar(false);
    }

    public boolean hayConductor() { return conductor != null; }

    /** Distancia geográfica al recojo, nunca una ruta por calles ni una estimación de llegada. */
    public double getDistanciaM() {
        return conductor == null || recojo == null ? 0d
                : distanciaMetros(conductor.latitude, conductor.longitude, recojo.latitude, recojo.longitude);
    }

    public void limpiarConductor() {
        conductor = null;
        actualizarMarcadores();
    }

    @Override
    protected void onSizeChanged(int ancho, int alto, int anteriorAncho, int anteriorAlto) {
        super.onSizeChanged(ancho, alto, anteriorAncho, anteriorAlto);
        if (ancho > 0 && alto > 0 && !camaraInicial) encuadrar(true);
    }

    @Override
    public boolean dispatchTouchEvent(MotionEvent evento) {
        if (mapa != null && aviso.getVisibility() != View.VISIBLE && getParent() != null) {
            int accion = evento.getActionMasked();
            getParent().requestDisallowInterceptTouchEvent(accion != MotionEvent.ACTION_UP
                    && accion != MotionEvent.ACTION_CANCEL);
        }
        return super.dispatchTouchEvent(evento);
    }

    private void actualizarMarcadores() {
        if (mapa == null || destruida) return;
        if (recojo != null) {
            if (marcaRecojo == null) {
                marcaRecojo = mapa.addMarker(new MarkerOptions().position(recojo)
                        .title(getContext().getString(R.string.mapa_google_recojo))
                        .icon(BitmapDescriptorFactory.defaultMarker(BitmapDescriptorFactory.HUE_AZURE)));
            } else marcaRecojo.setPosition(recojo);
        } else if (marcaRecojo != null) {
            marcaRecojo.remove();
            marcaRecojo = null;
        }
        if (conductor != null) {
            if (marcaConductor == null) {
                marcaConductor = mapa.addMarker(new MarkerOptions().position(conductor)
                        .title(getContext().getString(R.string.mapa_google_taxista))
                        .icon(BitmapDescriptorFactory.defaultMarker(BitmapDescriptorFactory.HUE_ORANGE)));
            } else marcaConductor.setPosition(conductor);
        } else if (marcaConductor != null) {
            marcaConductor.remove();
            marcaConductor = null;
        }
    }

    private void encuadrar(boolean forzar) {
        if (mapa == null || destruida || (recojo == null && conductor == null)) return;
        if (getWidth() == 0 || getHeight() == 0) {
            post(() -> { if (!destruida && getWidth() > 0 && getHeight() > 0) encuadrar(forzar); });
            return;
        }
        if (!forzar && camaraInicial && conductor != null
                && mapa.getProjection().getVisibleRegion().latLngBounds.contains(conductor)) return;
        if (recojo != null && conductor != null
                && distanciaMetros(recojo.latitude, recojo.longitude, conductor.latitude, conductor.longitude) > 20d) {
            LatLngBounds limites = new LatLngBounds.Builder().include(recojo).include(conductor).build();
            // El tamaño explícito también funciona cuando MapView termina de inicializarse
            // antes de su primer layout dentro de la tarjeta inicialmente oculta.
            int margen = Math.min(dp(40), Math.min(getWidth(), getHeight()) / 4);
            mapa.moveCamera(CameraUpdateFactory.newLatLngBounds(limites, getWidth(), getHeight(), margen));
        } else {
            mapa.moveCamera(CameraUpdateFactory.newLatLngZoom(conductor != null ? conductor : recojo, 16f));
        }
        camaraInicial = true;
    }

    private boolean tieneClaveConfigurada() {
        try {
            ApplicationInfo info = getContext().getPackageManager().getApplicationInfo(
                    getContext().getPackageName(), PackageManager.GET_META_DATA);
            String clave = info.metaData == null ? null : info.metaData.getString("com.google.android.geo.API_KEY");
            return clave != null && !clave.trim().isEmpty() && !clave.contains("${")
                    && !clave.contains("REEMPLAZA");
        } catch (PackageManager.NameNotFoundException error) {
            return false;
        }
    }

    private void liberarMapa() {
        mapa = null;
        marcaRecojo = null;
        marcaConductor = null;
        if (vistaMapa != null) {
            try { vistaMapa.onDestroy(); } catch (RuntimeException ignorado) { }
            removeView(vistaMapa);
            vistaMapa = null;
        }
    }

    private void mostrarAviso(int recurso) {
        aviso.setText(recurso);
        aviso.setVisibility(View.VISIBLE);
    }

    private static boolean iguales(@Nullable LatLng primero, @Nullable LatLng segundo) {
        return primero == null ? segundo == null : primero.equals(segundo);
    }

    private static boolean coordenadasValidas(double latitud, double longitud) {
        return Double.isFinite(latitud) && Double.isFinite(longitud)
                && latitud >= -90d && latitud <= 90d && longitud >= -180d && longitud <= 180d;
    }

    static double distanciaMetros(double lat1, double lng1, double lat2, double lng2) {
        double dLat = Math.toRadians(lat2 - lat1);
        double dLng = Math.toRadians(lng2 - lng1);
        double a = Math.sin(dLat / 2d) * Math.sin(dLat / 2d)
                + Math.cos(Math.toRadians(lat1)) * Math.cos(Math.toRadians(lat2))
                * Math.sin(dLng / 2d) * Math.sin(dLng / 2d);
        a = Math.max(0d, Math.min(1d, a));
        return RADIO_TIERRA_M * 2d * Math.atan2(Math.sqrt(a), Math.sqrt(1d - a));
    }

    private int dp(int valor) { return Math.round(valor * getResources().getDisplayMetrics().density); }
}
