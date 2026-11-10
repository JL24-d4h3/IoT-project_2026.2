# QR de cierre del traslado

El backend web crea un token al registrar el servicio: `QR-` seguido de los primeros 12 caracteres hexadecimales de un UUID aleatorio sin guiones, en mayúsculas. La implementación actual está en `ServicioTaxiService.generarCodigoQr()` del backend. No es el identificador numérico del viaje ni contiene nombre, teléfono o ubicación del cliente.

El token llega al cliente en `codigoQr` al crear el servicio. Android lo conserva junto al vínculo de la reserva porque las consultas posteriores no vuelven a devolverlo. La pantalla del cliente utiliza ZXing para convertir ese texto en la imagen QR; al decodificar la imagen se recupera exactamente el mismo texto.

## Uso por el conductor

1. Abre la validación de cierre del traslado en curso y elige escanear el QR mostrado por el cliente.
2. Android solicita el permiso de cámara cuando lo necesita. Si lo rechazas, puedes escribir el código manualmente.
3. Centra el QR del cliente en el lector. Cancelar la cámara no finaliza el servicio ni cambia su estado.
4. El contenido escaneado debe ser exclusivamente un token con formato `QR-` y 12 caracteres hexadecimales. Se normalizan espacios exteriores y mayúsculas. No se extraen códigos de enlaces, JSON ni otros textos; se rechazan controles y caracteres ajenos al formato.
5. Revisa el código leído y pulsa **Cerrar servicio**. El escaneo no envía la finalización automáticamente. El cierre solo se confirma cuando `PUT /api/servicios/{id}/finalizar` lo acepta. El backend comprueba la sesión JWT, el conductor asignado, el estado del viaje y la coincidencia del token. Reconocer un formato válido no demuestra que pertenezca al servicio actual.

No compartas capturas del QR fuera del traslado: el token permite solicitar su cierre al conductor autorizado. El lector utiliza la cámara únicamente durante el escaneo; no necesita el permiso de ubicación para interpretar el código.
