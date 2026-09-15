package org.iot.project.ui.components;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.drawable.Drawable;
import android.util.AttributeSet;
import android.view.View;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.content.res.AppCompatResources;
import androidx.core.content.ContextCompat;

import org.iot.project.R;
import org.iot.project.models.TaxiService;

/**
 * Seguimiento del taxi sobre un plano esquematico (RF-099, §37).
 *
 * <p>No es un mapa real y no lo aparenta: no hay teselas, ni nombres de calle,
 * ni servicio de mapas de por medio — la aplicacion es un front end sin
 * backend, y un mapa de verdad en este punto solo podria dibujar una cuadricula
 * gris. Lo que si es real es <em>la distancia</em>: sale de las coordenadas que
 * el conductor reporta (RF-098), y por eso el hueco entre los dos marcadores se
 * encoge de verdad conforme avanza, en vez de ser una animacion de adorno.
 *
 * <p>Lo que el plano responde es la unica pregunta que el cliente se hace
 * mientras espera: cuanto falta. Por eso la escala se satura a
 * {@link #ALCANCE_M}: mas alla de ese radio, todos los «lejos» se parecen y
 * afinar mas no informa de nada.
 *
 * <p>Guarda coordenadas, no pixeles. La posicion en pantalla se calcula en
 * {@link #onDraw} porque el ancho de la vista no se conoce hasta que se mide:
 * resolverlo al recibir los datos dejaria los marcadores amontonados en la
 * esquina cada vez que el servicio llega antes que el primer trazado.
 */
public class TrackingMapView extends View {

    /**
     * Radio que representa el plano entero. Un traslado urbano al aeropuerto se
     * decide en este orden de magnitud; con un alcance mayor, los ultimos
     * cientos de metros —los unicos en los que el cliente mira la pantalla— se
     * quedarian pegados al centro.
     */
    private static final double ALCANCE_M = 1500d;

    /** Radio terrestre medio, para la distancia entre dos coordenadas. */
    private static final double RADIO_TIERRA_M = 6371000d;

    /** Ni pegado ni fuera: con el marcador encima del otro no se ve que hay dos. */
    private static final float FRACCION_MINIMA = 0.14f;

    /** Lado de la cuadricula del plano. */
    private static final float PASO_CUADRICULA_DP = 28f;

    private final Paint pincelCuadricula = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint pincelRuta = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint pincelMarca = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint pincelAro = new Paint(Paint.ANTI_ALIAS_FLAG);

    private final Path camino = new Path();

    private final Drawable iconoTaxi;
    private final float radioMarca;
    private final float pasoCuadricula;

    private final int colorRecojo;
    private final int colorConductor;

    private boolean hayRecojo;
    private double latRecojo;
    private double lngRecojo;

    private boolean hayConductor;
    private double latConductor;
    private double lngConductor;

    /** Distancia real entre conductor y recojo, en metros. */
    private double distanciaM;

    public TrackingMapView(@NonNull Context context) {
        this(context, null);
    }

    public TrackingMapView(@NonNull Context context, @Nullable AttributeSet attrs) {
        this(context, attrs, 0);
    }

    public TrackingMapView(@NonNull Context context, @Nullable AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);

        pincelCuadricula.setStyle(Paint.Style.STROKE);
        pincelCuadricula.setStrokeWidth(dp(1f));
        pincelCuadricula.setColor(ContextCompat.getColor(context, R.color.colorBorder));

        pincelRuta.setStyle(Paint.Style.STROKE);
        pincelRuta.setStrokeWidth(dp(3f));
        pincelRuta.setStrokeCap(Paint.Cap.ROUND);
        pincelRuta.setColor(ContextCompat.getColor(context, R.color.colorPrimary));

        pincelMarca.setStyle(Paint.Style.FILL);

        pincelAro.setStyle(Paint.Style.STROKE);
        pincelAro.setStrokeWidth(dp(2f));
        pincelAro.setColor(ContextCompat.getColor(context, R.color.colorSurface));

        radioMarca = dp(16f);
        pasoCuadricula = dp(PASO_CUADRICULA_DP);

        colorRecojo = ContextCompat.getColor(context, R.color.colorPrimary);
        colorConductor = ContextCompat.getColor(context, R.color.colorAccent);

        iconoTaxi = AppCompatResources.getDrawable(context, R.drawable.ic_taxi);
        if (iconoTaxi != null) {
            iconoTaxi.setTint(ContextCompat.getColor(context, R.color.colorOnAccentContainer));
        }

        // El plano se describe con el texto que lo acompaña —la distancia—, no
        // con lo que dibuja: sin esto, un lector de pantalla anunciaria una
        // vista vacia y el cliente no tendria forma de saber donde esta el taxi.
        setContentDescription(context.getString(R.string.mapa_descripcion));
    }

    /**
     * Situa el punto de recojo.
     *
     * <p>Es el unico punto fijo del plano y por eso no se guarda su posicion en
     * pantalla: el plano se lee siempre igual y lo unico que cambia es por donde
     * llega el conductor.
     */
    public void setRecojo(double latitud, double longitud) {
        this.latRecojo = latitud;
        this.lngRecojo = longitud;
        this.hayRecojo = true;
        invalidate();
    }

    /**
     * Situa al conductor.
     *
     * <p>Sin coordenadas —todavia no las reporto— no se dibuja nada suyo:
     * inventarle una posicion seria pintar un dato falso sobre el que el cliente
     * podria decidir esperar.
     */
    public void setConductor(double latitud, double longitud) {
        this.latConductor = latitud;
        this.lngConductor = longitud;
        this.hayConductor = tieneCoordenadas(latitud, longitud) && hayRecojo;
        if (hayConductor) {
            distanciaM = distanciaMetros(latConductor, lngConductor, latRecojo, lngRecojo);
        }
        invalidate();
    }

    /** true si hay una posicion de conductor que dibujar. */
    public boolean hayConductor() {
        return hayConductor;
    }

    /** Distancia en metros entre el conductor y el punto de recojo. */
    public double getDistanciaM() {
        return distanciaM;
    }

    /** Deja el plano sin conductor: el viaje ya no se sigue desde aqui. */
    public void limpiarConductor() {
        hayConductor = false;
        invalidate();
    }

    /** Distancia en metros entre dos coordenadas (haversine). */
    static double distanciaMetros(double lat1, double lng1, double lat2, double lng2) {
        double dLat = Math.toRadians(lat2 - lat1);
        double dLng = Math.toRadians(lng2 - lng1);
        double a = Math.sin(dLat / 2) * Math.sin(dLat / 2)
                + Math.cos(Math.toRadians(lat1)) * Math.cos(Math.toRadians(lat2))
                * Math.sin(dLng / 2) * Math.sin(dLng / 2);
        return RADIO_TIERRA_M * 2 * Math.atan2(Math.sqrt(a), Math.sqrt(1 - a));
    }

    private static boolean tieneCoordenadas(double lat, double lng) {
        return lat != 0d || lng != 0d;
    }

    @Override
    protected void onDraw(@NonNull Canvas lienzo) {
        super.onDraw(lienzo);

        float ancho = getWidth();
        float alto = getHeight();
        if (ancho == 0f || alto == 0f || !hayRecojo) {
            return;
        }

        dibujarCuadricula(lienzo, ancho, alto);

        float recojoX = ancho * 0.32f;
        float recojoY = alto * 0.62f;

        if (hayConductor) {
            float fraccion = (float) Math.min(1d, distanciaM / ALCANCE_M);
            fraccion = Math.max(FRACCION_MINIMA, fraccion);

            // Se acerca desde el lado del que de verdad viene: el rumbo real
            // entre las dos coordenadas decide el angulo, no un adorno fijo.
            // La longitud se corrige por el coseno de la latitud porque un
            // grado de longitud no mide lo mismo en Lima que en el ecuador.
            double rumbo = Math.atan2(latRecojo - latConductor,
                    (lngRecojo - lngConductor) * Math.cos(Math.toRadians(latConductor)));

            // El radio util descuenta el propio marcador para que a distancia
            // maxima siga entrando entero dentro del plano.
            float radioUtil = Math.min(ancho, alto) / 2f - radioMarca - dp(6f);
            float conductorX = recojoX + (float) (Math.cos(rumbo) * radioUtil * fraccion);
            float conductorY = recojoY - (float) (Math.sin(rumbo) * radioUtil * fraccion);

            dibujarRuta(lienzo, conductorX, conductorY, recojoX, recojoY);
            dibujarRecojo(lienzo, recojoX, recojoY);
            dibujarConductor(lienzo, conductorX, conductorY);
        } else {
            dibujarRecojo(lienzo, recojoX, recojoY);
        }
    }

    /**
     * Calles de mentira.
     *
     * <p>Estan para que el plano se lea como un plano y no como un rectangulo
     * de color: sin ellas, dos puntos flotando en un gris no dicen «mapa».
     */
    private void dibujarCuadricula(@NonNull Canvas lienzo, float ancho, float alto) {
        for (float x = pasoCuadricula; x < ancho; x += pasoCuadricula) {
            lienzo.drawLine(x, 0f, x, alto, pincelCuadricula);
        }
        for (float y = pasoCuadricula; y < alto; y += pasoCuadricula) {
            lienzo.drawLine(0f, y, ancho, y, pincelCuadricula);
        }
    }

    private void dibujarRuta(@NonNull Canvas lienzo, float desdeX, float desdeY,
                             float hastaX, float hastaY) {
        camino.reset();
        camino.moveTo(desdeX, desdeY);
        camino.lineTo(hastaX, hastaY);
        lienzo.drawPath(camino, pincelRuta);
    }

    private void dibujarRecojo(@NonNull Canvas lienzo, float x, float y) {
        float radio = dp(9f);
        pincelMarca.setColor(colorRecojo);
        lienzo.drawCircle(x, y, radio, pincelMarca);
        lienzo.drawCircle(x, y, radio, pincelAro);

        // Punto blanco en el centro: distingue el punto de recojo del marcador
        // del conductor sin depender solo del color.
        pincelMarca.setColor(Color.WHITE);
        lienzo.drawCircle(x, y, radio * 0.35f, pincelMarca);
    }

    private void dibujarConductor(@NonNull Canvas lienzo, float x, float y) {
        pincelMarca.setColor(colorConductor);
        lienzo.drawCircle(x, y, radioMarca, pincelMarca);
        lienzo.drawCircle(x, y, radioMarca, pincelAro);

        if (iconoTaxi != null) {
            float lado = radioMarca * 1.1f;
            iconoTaxi.setBounds(Math.round(x - lado / 2f), Math.round(y - lado / 2f),
                    Math.round(x + lado / 2f), Math.round(y + lado / 2f));
            iconoTaxi.draw(lienzo);
        }
    }

    private float dp(float valor) {
        return valor * getResources().getDisplayMetrics().density;
    }
}
