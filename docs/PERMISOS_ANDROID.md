# Permisos de notificaciones y cámara

La app guarda el historial y el último estado conocido de ambos permisos en preferencias privadas de la instalación. Cerrar la app, reiniciar el proceso o cambiar de cuenta no vuelve a abrir los diálogos automáticamente.

Android conserva la autorización real. La aplicación consulta ese permiso al reanudarse y antes de utilizar la cámara o mostrar un aviso, y actualiza su registro si el usuario lo cambió en Ajustes. Una concesión anterior no evita detectar una revocación, un permiso temporal caducado o el restablecimiento automático del sistema.

- **Notificaciones:** se solicita una vez al ingresar como cliente o administrador de hotel. El rechazo no bloquea el chat. En el centro de notificaciones, el botón de la cabecera permite abrir los ajustes de Android y cambiar la elección. La creación del canal de chat sigue conservando sus ajustes de sonido y visibilidad. El historial anterior `chat_solicitado` se migra para no repetir el aviso después de actualizar la app.
- **Cámara:** se solicita al tocar **Escanear QR**, después de comprobar la sesión y el servicio en traslado. Si ya se rechazó, el botón **Abrir ajustes de cámara** permite cambiar la decisión sin reiterar el diálogo. La transcripción manual del código continúa disponible. Volver de Ajustes actualiza el estado; el escáner no se abre automáticamente.

Las preferencias no contienen cuentas, tokens ni códigos QR. Tampoco se copian con las copias de seguridad o transferencias a otro dispositivo: esa nueva instalación comprueba sus propios permisos. Borrar los datos o desinstalar la app elimina este historial local.

Documentación del sistema: [permisos de ejecución en Android](https://developer.android.com/training/permissions/requesting).
