# Contrato Android y API de taxistas

Android utiliza `app/src/main/java/org/iot/project/data/rest/`. El servidor correspondiente está en `web/src/main/java/com/taxi/gestion/`.

| Función | Contrato |
|---|---|
| Acceso del taxista | `POST /api/auth/login`, cuenta habilitada y activa; devuelve JWT |
| Crear solicitud | `POST /api/servicios`; devuelve identificador y QR generado por el servidor |
| Consultar viaje | `GET /api/servicios/{id}` y `GET /api/servicios/{id}/estado` |
| Ofertas | `GET /api/servicios/pendientes` |
| Aceptar | `PUT /api/servicios/{id}/aceptar`, JWT del taxista |
| Avanzar estado | `PUT /api/servicios/{id}/estado`, taxista asignado |
| Finalizar | `PUT /api/servicios/{id}/finalizar`, taxista asignado y QR |
| GPS | `PUT /api/taxistas/{id}/ubicacion`, identidad coincidente con el token |
| Historial | `GET /api/servicios/taxista/{id}`, identidad coincidente |
| Administrar taxistas | `/api/admin/taxistas`, token Firebase y UID autorizado en el servidor |

Estados: `SOLICITADO -> ASIGNADO -> EN_CAMINO -> EN_TRASLADO -> FINALIZADO`. El taxista acepta, activa los estados intermedios y escanea el QR para finalizar.

La reserva y el hotel se mantienen en Firebase. Android conserva el `apiId` y el QR que devuelve la creación. El servidor consulta viajes por identificador; no existe un endpoint por reserva. Consultar o sondear no crea solicitudes automáticamente; los resultados de envío inciertos se muestran para su resolución.

El conductor concede ubicación antes de consultar ofertas y reporta GPS mediante un servicio foreground. El escáner solicita permiso de cámara. Mapas, notificaciones y Google requieren su configuración Android privada.

Firebase y SQL utilizan flujos de acceso separados. Un fallo Firebase no reenvía automáticamente sus credenciales a la API de taxistas. La URL se inyecta por Gradle; las claves Cloudinary, MySQL, JWT y Firebase Admin pertenecen exclusivamente al servidor.
