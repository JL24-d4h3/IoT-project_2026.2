package org.iot.project.data;

import org.iot.project.R;
import org.iot.project.models.Service;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

/** Tipos de servicio compartidos; su inclusión y precio pertenecen a cada hotel. */
public final class CatalogoServicios {

    private CatalogoServicios() { }

    /** Las identidades también están permitidas explícitamente en firestore.rules. */
    public static List<Service> crear() {
        return Collections.unmodifiableList(Arrays.asList(
                new Service("WIFI", "Wi-Fi de alta velocidad",
                        "Conexión inalámbrica en todas las áreas del hotel", R.drawable.ic_service_wifi),
                new Service("DESAYUNO", "Desayuno buffet",
                        "Desayuno completo servido en el hotel", R.drawable.ic_service_restaurant),
                new Service("RESTAURANTE", "Restaurante",
                        "Cocina local e internacional a la carta", R.drawable.ic_service_restaurant),
                new Service("BAR", "Bar y lounge",
                        "Coctelería y aperitivos por la tarde", R.drawable.ic_service_restaurant),
                new Service("PISCINA", "Piscina",
                        "Piscina temperada con zona de descanso", R.drawable.ic_service_pool),
                new Service("GIMNASIO", "Gimnasio",
                        "Equipo cardiovascular y de fuerza", R.drawable.ic_service_fitness),
                new Service("SPA", "Spa y sauna",
                        "Circuito de agua y masajes con cita previa", R.drawable.ic_service_fitness),
                new Service("ESTACIONAMIENTO", "Estacionamiento",
                        "Cochera cubierta dentro del establecimiento", R.drawable.ic_service_parking),
                new Service("AIRE_ACONDICIONADO", "Aire acondicionado",
                        "Climatización individual por habitación", R.drawable.ic_service_ac),
                new Service("LAVANDERIA", "Lavandería",
                        "Servicio de lavado y planchado por encargo", R.drawable.ic_service_laundry),
                new Service("TRASLADO_AEROPUERTO", "Traslado al aeropuerto",
                        "Recojo y traslado coordinado con recepción", R.drawable.ic_service_shuttle),
                new Service("MASCOTAS", "Admite mascotas",
                        "Hasta dos mascotas pequeñas por habitación", R.drawable.ic_service_pets),
                new Service("SALA_REUNIONES", "Sala de reuniones",
                        "Espacio equipado para eventos de trabajo", R.drawable.ic_service_business),
                new Service("RECEPCION_24H", "Recepción 24 horas",
                        "Atención permanente durante toda la estadía", R.drawable.ic_service_reception)));
    }
}
