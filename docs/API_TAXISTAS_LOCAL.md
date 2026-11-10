# API del sistema web de taxistas

La aplicación consume el backend Spring Boot de gestión de taxistas. En su
configuración actual escucha en el puerto **8080**. Hoteles, reservas y las
cuentas de cliente/administración siguen utilizando Firebase.

## Conexión en desarrollo

Para iniciar Spring Boot con el JDK y Maven disponibles en este equipo, sigue
[INICIAR_API_LOCAL.md](INICIAR_API_LOCAL.md). Incluye MySQL, el esquema y los comandos PowerShell.

1. Iniciar el backend web y comprobar `http://localhost:8080/swagger-ui.html`.
2. Abrir la variante **debug** de Android. En el emulador oficial, la URL por
   defecto es `http://10.0.2.2:8080/`: apunta al equipo anfitrión.
3. Para otra dirección, añadir a `local.properties` (archivo ignorado por Git):

   ```properties
   taxistas.api.baseUrl=http://10.0.2.2:8080/
   ```

   También se acepta `-PtaxistasApiBaseUrl=...` al compilar, o la variable
   `TAXISTAS_API_BASE_URL`. Volver a sincronizar/compilar después del cambio.

Para un celular USB, ejecutar `adb reverse tcp:8080 tcp:8080`, configurar
`taxistas.api.baseUrl=http://127.0.0.1:8080/` y recompilar. Para Wi-Fi, usar
la IP local del equipo y permitir el acceso a ese puerto en la red local.
`localhost` en el teléfono identifica al propio teléfono.

HTTP está habilitado en debug. Una compilación release necesita una URL HTTPS
explícita; no toma por defecto la dirección local de desarrollo.

## Acceso y operación

- Elegir **Cliente / Hotel** para las cuentas existentes de Firebase.
- Elegir **Taxista** para ingresar con correo y contraseña registrados en el
  sistema web. El taxista debe estar habilitado y tener cuenta activa allí.
- El token JWT del backend permanece en memoria y se elimina al cerrar sesión.
  Después de terminar el proceso de Android hay que volver a ingresar.
- El conductor consulta perfil, solicitudes y servicio en curso, acepta un
  servicio y avanza `ASIGNADO → EN_CAMINO → EN_TRASLADO`. Finaliza validando el
  código QR del cliente. La pantalla consulta mientras está visible.
- **Activar ubicación** pide permiso preciso y una posición GPS válida antes de
  mostrar solicitudes. Durante un servicio asignado, un servicio en primer plano
  captura GPS y envía posiciones nuevas al backend aproximadamente cada 10 segundos,
  también con la pantalla bloqueada. Se detiene al finalizar o cerrar sesión.
- El cliente solicita el taxi desde su reserva. Firestore conserva la propiedad,
  reserva, programación, pasajeros, beneficio, QR e ID del servicio web. Los
  estados y la ubicación reportada se consultan a la API; no se simula movimiento.
- El administrador consulta los taxis vinculados en **Reservas → Detalle → Taxi
  de la reserva**. Comparte los cinco estados y el mapa con el cliente, sin
  acciones para avanzar el servicio. La consulta valida su hotel y reserva en
  Firestore y solo lee estado, perfil y ubicación del sistema web.

## Rutas utilizadas

| Operación | Ruta |
| --- | --- |
| Login del taxista | `POST /api/auth/login` |
| Perfil | `GET /api/taxistas/{id}` |
| Ubicación reportada | `GET /api/taxistas/{id}/ubicacion` |
| Reportar ubicación del conductor | `PUT /api/taxistas/{id}/ubicacion` |
| Crear viaje | `POST /api/servicios` |
| Consultar viaje | `GET /api/servicios/{id}` |
| Estado del viaje desde el hotel | `GET /api/servicios/{id}/estado` |
| Solicitudes disponibles | `GET /api/servicios/pendientes` |
| Servicios del conductor | `GET /api/servicios/taxista/{id}` |
| Aceptar | `PUT /api/servicios/{id}/aceptar` |
| Avanzar | `PUT /api/servicios/{id}/estado` |
| Finalizar | `PUT /api/servicios/{id}/finalizar` |
| Valorar | `POST /api/taxistas/{id}/valoracion` |

Las escrituras del conductor y la consulta de su historial envían el JWT web
como Bearer; no se envía un token de Firebase a esa API.

## Límites del contrato actual

El superadministrador puede listar, aprobar y activar/desactivar taxistas desde
Android mediante `/api/admin/taxistas`. El backend necesita Firebase Admin y una
lista de UID autorizados: consulta [ADMIN_TAXISTAS.md](ADMIN_TAXISTAS.md) antes de
usar estas acciones. La API no expone historial de cliente ni la
programación/pasajeros al conductor. Android conserva esos datos
de la reserva y muestra información pendiente cuando el backend no la conoce.
Si falta permiso preciso o se apaga GPS, se pausa el seguimiento y se ocultan las
nuevas solicitudes. El viaje se conserva y sigue permitiendo su finalización.
Android puede terminar el proceso al retirar permisos: al ingresar de nuevo se
recupera el viaje desde el backend. Consulta [Mapas y ubicación](MAPAS_UBICACION.md)
para configurar la clave local de Google Maps y conocer el comportamiento completo.

La valoración Android de 1–10 se convierte a un entero de 1–5 para el backend.
La tarifa cero del beneficio se conserva en la reserva y se omite en la petición
web porque el contrato solo acepta una tarifa positiva o ausente.

La API entrega el código QR únicamente al crear el servicio. Si se pierde la
respuesta de creación, no permite recuperarlo ni buscar la solicitud por un ID
externo. Android registra el intento antes de enviarlo y evita repetir un POST
de resultado incierto, incluso después de reiniciar. Ese caso necesita
conciliación en el backend; las lecturas nunca crean un viaje adicional.
Si la conexión falla antes de empezar a escribir la petición (por ejemplo,
localhost apagado), se libera el intento para permitir un reintento manual.
El cliente verá una tarjeta **Solicitud sin confirmar** con el botón
**Reintentar solicitud**. El botón reutiliza el mismo registro; no aparece si
el resultado del envío es incierto. Las solicitudes sin confirmar no muestran
QR ni seguimiento de conductor.
