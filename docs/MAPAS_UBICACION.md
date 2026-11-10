# Google Maps y ubicación del taxista

El cliente ve Google Maps en **Taxi** y el administrador en **Reservas → Detalle → Taxi de la reserva**. Ambos consultan el punto de recojo y la última posición del conductor recibida de la API web, incluidos los desplazamientos durante `EN_CAMINO` y `EN_TRASLADO`. La pantalla indica la antigüedad del reporte. Antes del traslado, la distancia mostrada al cliente es geográfica, en línea recta al recojo; durante el traslado no se presenta esa distancia como progreso del viaje. No se calculan rutas por calles ni tiempos de llegada.

## Estados compartidos

| Estado | Qué lo activa |
| --- | --- |
| `SOLICITADO` | El backend confirma la solicitud del cliente. |
| `ASIGNADO` | Un taxista acepta el servicio. |
| `EN_CAMINO` | El taxista pulsa la acción para salir hacia el recojo. |
| `EN_TRASLADO` | El taxista confirma que el pasajero subió. |
| `FINALIZADO` | Al llegar al aeropuerto, el taxista escanea el QR del cliente y confirma el cierre; el backend valida el código. |

El cliente y el administrador consultan estos estados, sin botones para avanzar el viaje. Las pantallas visibles actualizan estado y ubicación mediante consultas periódicas, aproximadamente cada 5 segundos después de completar la consulta anterior. No necesitan permisos GPS: solo el conductor captura y envía su ubicación.

En el detalle de la reserva del administrador se consultan exclusivamente los traslados vinculados a esa reserva y al hotel que tiene asignado. La aplicación valida la asignación y el propietario en Firestore antes de consultar la API y antes de entregar la respuesta. La lectura del administrador no crea solicitudes ni modifica documentos del taxi. Si hay varios traslados vinculados, el selector permite elegirlos. Un registro pendiente sin ID confirmado del backend aparece por separado, sin presentarlo como un viaje operativo.

Después de finalizar, el detalle conserva la última posición disponible como **reporte histórico**, con su fecha y hora. El cliente puede volver a abrir el viaje desde el historial; si no tiene un viaje activo, ve el último finalizado. Este detalle no impide solicitar un traslado para otra reserva elegible. Si no hubo un reporte de ubicación, se informa y no se inventa una posición final. El backend actual conserva la última ubicación del conductor y no un recorrido completo por viaje.

## Configurar el mapa

1. En tu proyecto de Google Cloud habilita **Maps SDK for Android** y configura la facturación que exige Google Maps Platform.
2. Crea una clave para Android y restríngela al paquete `org.iot.project` y a los certificados SHA-1 con los que firmas la aplicación. Restringe además su uso a **Maps SDK for Android**. La firma de depuración y la de publicación pueden requerir entradas diferentes.
3. En la raíz del proyecto Android crea `secrets.properties`, al lado de `settings.gradle`. Añade `MAPS_API_KEY=` seguido de tu clave local. También se admite la propiedad `MAPS_API_KEY` en `local.properties` si no existe en `secrets.properties`.
4. Sincroniza Gradle y vuelve a instalar la aplicación. La clave se incorpora al manifiesto mediante el placeholder `MAPS_API_KEY` y la metadata `com.google.android.geo.API_KEY`.

`secrets.properties` y `local.properties` están ignorados por Git. No pegues la clave en archivos Java, XML ni documentación. `google-services.json` configura Firebase y no sustituye la clave del SDK de mapas. Una clave incluida en una aplicación Android puede extraerse del APK: las restricciones de paquete, firma y API son necesarias.

Si falta la clave, la aplicación muestra un aviso de configuración y no crea el componente de Google Maps. El resto del flujo del taxi continúa disponible. Si el mapa no carga con una clave configurada, revisa la conexión, las restricciones de la clave, la API habilitada y Google Play Services del dispositivo. El emulador debe incluir Google Play Services.

Documentación oficial: [configuración de Maps SDK for Android](https://developers.google.com/maps/documentation/android-sdk/config) y [restricciones de claves](https://developers.google.com/maps/api-security-best-practices).

## Dónde se guarda la ubicación

El teléfono del conductor obtiene coordenadas reales y las envía al backend de la página web mediante `PUT /api/taxistas/{id}/ubicacion`, autenticado con su JWT. El servidor guarda latitud, longitud y el momento del último reporte. El cliente consulta `GET /api/taxistas/{id}/ubicacion` a través del repositorio REST. El mapa no genera posiciones de demostración ni deduce la posición del conductor a partir de la del hotel.

La ubicación de recogida procede del hotel de la reserva. Los teléfonos del cliente y del administrador no necesitan permiso de ubicación para ver estos dos marcadores. El seguimiento GPS se solicita únicamente en la cuenta del conductor y utiliza los permisos de Android y un servicio en primer plano con aviso visible mientras esté activo.

## GPS automático durante un servicio

El conductor inicia el seguimiento desde su panel visible, después de conceder ubicación precisa y encender el GPS. `ServicioUbicacionTaxista` solicita posiciones aproximadamente cada 5 segundos y las envía al backend aproximadamente cada 10 segundos; Android, el dispositivo y la red pueden variar estos tiempos. Solo utiliza reportes con hasta 2 minutos de antigüedad y precisión de 100 metros o mejor.

Antes de enviar, consulta el estado real del servicio web y continúa únicamente durante `ASIGNADO`, `EN_CAMINO` o `EN_TRASLADO`. El seguimiento puede continuar con la pantalla apagada mediante el servicio en primer plano. Se detiene al finalizar, cerrar sesión, revocar el permiso o apagar el GPS. Para consultar ofertas estando disponible se puede enviar una fijación manual; eso no activa el seguimiento continuo.

El JWT permanece en memoria. No se reinicia el seguimiento automáticamente al encender el teléfono ni mediante un servicio persistente después de que Android termine el proceso. Tras volver a conceder el permiso, abre el panel: se consulta al backend para decidir si procede reanudar. Si Android terminó el proceso, puede ser necesario iniciar sesión de nuevo.

Si revocar el permiso termina el proceso, no se puede garantizar un aviso inmediato al servidor o al cliente. Por eso el cliente conserva la hora del último reporte y señala cuándo quedó desactualizado; un marcador visible no garantiza que el conductor siga reportando en ese instante.

## Comprobar manualmente

1. Inicia el backend web y configura su URL accesible para Android según [API_TAXISTAS_LOCAL.md](API_TAXISTAS_LOCAL.md). El `localhost` del emulador apunta al emulador; para la computadora anfitriona se usa `10.0.2.2`.
2. Ingresa como taxista desde el acceso del sistema web y habilita el seguimiento GPS cuando lo solicite Android. Mantén encendida la ubicación del dispositivo.
3. Solicita un traslado desde una reserva válida del cliente y acéptalo como conductor.
4. En el cliente y en el detalle de la reserva del administrador comprueba `SOLICITADO` antes de aceptar y `ASIGNADO` después. El marcador debe corresponder al último reporte real. Sin reporte todavía, la pantalla debe indicarlo y mostrar únicamente el punto de recojo.
5. Cambia la posición real del conductor, o envía una posición desde los controles de ubicación del emulador, y comprueba la actualización. Cambiar el mapa no altera coordenadas guardadas.
6. Activa `EN_CAMINO` al salir hacia el hotel y `EN_TRASLADO` cuando suba el pasajero. Comprueba los estados en ambas vistas: el mapa sigue visible y, durante el traslado, el cliente deja de ver la distancia al hotel como si fuera distancia pendiente.
7. Finaliza validando el QR real del cliente. Comprueba `FINALIZADO` en ambas vistas y que el seguimiento se detenga. El último reporte debe verse como histórico; también puede consultarse desde el historial del cliente.

No se debe usar una ubicación obtenida antes de conceder permiso como si fuera un reporte nuevo. Si se pierde conexión o GPS, el cliente debe conservar la fecha del último reporte recibido y mostrar su antigüedad.
