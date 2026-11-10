# Administración de taxistas desde Android

El superadministrador usa **Conductores** para consultar las cuentas del sistema web, revisar sus datos y aprobarlas. El listado y las acciones se ejecutan en el backend Spring Boot; no se crean copias de los taxistas web en Firestore.

## Endpoints

| Operación | Método y ruta | JSON enviado |
| --- | --- | --- |
| Listar taxistas, incluidos pendientes e inactivos | `GET /api/admin/taxistas` | Sin cuerpo |
| Aprobar o retirar la aprobación | `PUT /api/admin/taxistas/{id}/habilitacion` | `{"habilitado": true}` o `{"habilitado": false}` |
| Activar o desactivar una cuenta web | `PUT /api/admin/taxistas/{id}/cuenta` | `{"activa": true}` o `{"activa": false}` |

El listado devuelve un array y cada escritura devuelve el taxista confirmado. Incluye el ID numérico web, nombre, correo, teléfono, fotos, documento enmascarado, vehículo, `estadoHabilitacion` y `cuentaActiva`. Nunca incluye contraseñas ni hashes. Los códigos principales son `400` para un cuerpo inválido, `401` para un token ausente o inválido, `403` para un usuario sin autorización administrativa, `404` si el taxista no existe, `409` si tiene un viaje que impide el cambio y `503` si no se puede validar el acceso administrativo.

**Aprobación** y **cuenta activa** son independientes: aprobar cambia `PENDIENTE` a `HABILITADO`; desactivar cambia `cuentaActiva` sin retirar la aprobación. Para operar e iniciar sesión como taxista deben cumplirse ambas condiciones. El filtro **Por aprobar** usa únicamente la aprobación. El backend impide retirar la habilitación o desactivar la cuenta durante un servicio `ASIGNADO`, `EN_CAMINO` o `EN_TRASLADO`.

## Configuración del backend

Android envía automáticamente el ID token de su sesión Firebase en `Authorization: Bearer …`. No utiliza la contraseña del panel web ni el JWT del taxista para administrar cuentas. El servidor verifica el token y comprueba que su UID esté en la lista de superadministradores autorizados.

Configura estas variables en el proceso que inicia **gestion-taxistas** (terminal o configuración de ejecución del IDE):

```powershell
$env:FIREBASE_PROJECT_ID = "ID_DE_TU_PROYECTO_FIREBASE"
$env:FIREBASE_SUPERADMIN_UIDS = "UID_DEL_SUPERADMINISTRADOR"
$env:GOOGLE_APPLICATION_CREDENTIALS = "C:\credenciales\firebase-admin.json"
```

- El proyecto debe coincidir con el que utiliza Android.
- Copia el **UID** de la cuenta superadministradora desde Firebase Authentication. Para autorizar varias cuentas, separa sus UID con comas. No uses correos ni IDs numéricos de taxista.
- La cuenta debe seguir teniendo el perfil Android `rol: SUPERADMIN` y `activo: true` para acceder a su panel. La lista de UID del backend es la autorización del servidor.
- `GOOGLE_APPLICATION_CREDENTIALS` apunta a las credenciales del servidor para Firebase Admin, guardadas fuera de los repositorios. También se admiten Application Default Credentials del entorno. `google-services.json` pertenece al cliente Android y no sirve como credencial del servidor.
- Reinicia el backend después de configurar las variables. La web y la API del taxista pueden arrancar sin esta configuración, pero las operaciones administrativas permanecen bloqueadas hasta configurarla.

Las credenciales y la verificación del ID token siguen la [configuración oficial de Firebase Admin](https://firebase.google.com/docs/admin/setup) y la [verificación oficial de tokens](https://firebase.google.com/docs/auth/admin/verify-id-tokens). No es necesario modificar reglas de Firestore para esta integración.

La URL y el puerto son los mismos que usa la API de taxis. En el emulador, el valor debug predeterminado es `http://10.0.2.2:8080/`; consulta [API_TAXISTAS_LOCAL.md](API_TAXISTAS_LOCAL.md) para un teléfono físico.

## Comprobar el flujo

1. Inicia el backend configurado y abre Android con el superadministrador autorizado.
2. En **Conductores**, pulsa **Actualizar** y abre el filtro **Por aprobar**. Deben aparecer los registros pendientes de la base web.
3. Pulsa **Aprobar**. La fila cambia solo después de la confirmación del backend. Si la cuenta estaba activa, el taxista ya puede iniciar sesión en Android.
4. En **Todos**, desactiva la cuenta de un taxista sin viaje en curso. La aprobación permanece y el acceso operativo queda bloqueado. Actívala de nuevo para restablecer el acceso.
5. Durante un viaje, intenta desactivar o retirar la aprobación: debe aparecer el conflicto del servidor y conservarse el estado confirmado de la fila.
6. Vuelve a la portada: el resumen consulta los taxistas aprobados y pendientes en el mismo backend. Si el backend no responde, los módulos Firebase siguen disponibles y las cifras de taxistas aparecen sin confirmar.

Las escrituras no se repiten automáticamente después de un error de conexión. Si el resultado es incierto, actualiza el listado antes de decidir repetir la acción.
