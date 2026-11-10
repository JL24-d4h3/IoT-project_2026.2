# Inicio de sesión con Google

En el acceso de **Cliente / Admin**, el botón **Continuar con Google** abre el selector oficial de cuentas mediante Credential Manager. El ID token seleccionado se intercambia con Firebase Authentication; después se valida el perfil de `usuarios/{uid}` contra el servidor. El token de Google no se guarda en preferencias, argumentos de navegación ni estado de la pantalla.

## Configurar Firebase

El archivo local actual no contiene un cliente OAuth web. Hasta completar estos pasos, el botón informa que el acceso Google no está configurado; el acceso con correo y contraseña sigue disponible.

1. En Firebase Console, abre **Authentication → Sign-in method**, habilita **Google**, selecciona el correo de soporte y guarda.
2. En **Configuración del proyecto → Tus apps**, selecciona la app Android `org.iot.project` y añade sus certificados **SHA-1** y **SHA-256**. Para obtener los de desarrollo desde la carpeta Android:

   ```powershell
   $env:JAVA_HOME = 'C:\Program Files\Android\Android Studio\jbr'
   .\gradlew.bat :app:signingReport
   ```

   Para una compilación publicada, registra también el certificado con el que realmente se firma esa aplicación, incluido el certificado de firma de Google Play si corresponde.
3. Descarga el `google-services.json` actualizado y colócalo en `app/google-services.json`. Este archivo ya está excluido de Git. No copies claves, contraseñas ni secretos OAuth a Java o XML.
4. Sincroniza Gradle y vuelve a compilar/instalar. El plugin genera `default_web_client_id`; se utiliza el cliente OAuth de tipo **Web**, no el ID OAuth de Android. No se necesita un secreto de cliente dentro de la app.
5. Pulsa **Continuar con Google** y elige una cuenta. Cancelar el selector vuelve al formulario; no crea una sesión. Para probar el selector real, el dispositivo o emulador necesita los servicios y una cuenta Google disponibles.

Estos pasos siguen la [integración oficial de Google con Firebase](https://firebase.google.com/docs/auth/android/google-signin) y el [flujo de botón de Credential Manager](https://developer.android.com/identity/sign-in/credential-manager-siwg-implementation).

## Perfiles y roles

- Una cuenta Google sin perfil estrena un perfil **CLIENTE**, con el nombre y la foto disponibles de Google. No se asigna un hotel ni un rol administrativo a partir de su correo.
- Si el mismo UID ya tiene perfil, se conserva su rol y asignación. Un administrador de hotel o superadministrador puede usar Google cuando ese proveedor corresponde a su cuenta Firebase existente.
- Las cuentas deshabilitadas o con perfil inactivo siguen bloqueadas. Un perfil existente nunca se reemplaza por uno activo nuevo.
- Si Firebase informa un conflicto con otra forma de acceso, utiliza el acceso original. La app no fusiona cuentas ni sustituye perfiles basándose solo en un correo coincidente.
- Cerrar sesión también limpia el estado de Credential Manager para la cuenta Google correspondiente. El siguiente acceso vuelve a abrirse por una acción explícita.

## Por qué el taxista tiene otro acceso

Clientes, administradores de hotel y superadministradores usan **Firebase Authentication**. Los taxistas registrados en la página web usan **`POST /api/auth/login`** del backend Spring Boot, su base SQL y un **JWT propio**. La sesión Firebase no sustituye ese JWT y una cuenta Google no autoriza automáticamente a conducir.

Por eso se conserva la opción **Taxista**: sus credenciales se envían exclusivamente al sistema web y sus permisos dependen de la aprobación y cuenta activa allí. El mismo correo en ambos sistemas no significa que sea la misma cuenta.

Para ofrecer Google también a taxistas sería necesario que el backend verificara esa identidad y emitiera su JWT tras vincularla con un taxista aprobado. Esa vinculación no forma parte del contrato REST actual.
