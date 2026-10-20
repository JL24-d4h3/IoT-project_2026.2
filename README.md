# Estadía — aplicación Android

Aplicación de reservas de hoteles con perfiles de cliente, administrador de
hotel, superadministrador y conductor. Proyecto Android en Java con Firebase
Authentication y Cloud Firestore.

## Configuración local

1. Abrir el proyecto en Android Studio y configurar el SDK solicitado por Gradle.
2. Descargar el archivo de configuración de la aplicación Android desde el
   proyecto Firebase correspondiente y guardarlo en `app/google-services.json`.
   Este archivo es local, está ignorado por Git y es necesario para compilar.
3. Sincronizar Gradle y ejecutar la aplicación. Usar un JDK compatible con la
   versión del Android Gradle Plugin, por ejemplo el incluido con Android Studio.

No subir claves, cuentas de servicio, archivos de firma ni configuraciones
personales. `tools/firebase/perfiles-ejemplo.json` contiene únicamente datos
ficticios para documentar la estructura de los perfiles.

## Código y verificación

- `app/src/main/`: código y recursos de la aplicación.
- `app/src/test/` y `app/src/androidTest/`: pruebas unitarias y de interfaz.
- `tools/`: utilidades de desarrollo y pruebas locales de Firebase.
- `docs/`: requisitos y documentación técnica.

Las pruebas Android con Firebase local se describen en
[`ANDROID_TESTS.md`](tools/firebase/rules-tests/ANDROID_TESTS.md).
Usar proyectos de demostración y emuladores para las pruebas de integración.
