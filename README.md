# Reservas de hoteles y servicios de taxi

Aplicación Android en Java con Firebase para hoteles, habitaciones, reservas, chat y perfiles. La carpeta [web/](web/) contiene la página de registro y administración de taxistas y su API REST, construidas con Spring Boot, Thymeleaf y MySQL.

## Estructura

- `app/`: aplicación Android y pruebas.
- `gradle/`, `gradlew*` y archivos Gradle: construcción de Android.
- `web/`: fuentes Java, vistas web, pruebas Maven y esquema MySQL sin datos.
- `tools/`: herramientas de desarrollo y pruebas con Firebase Emulator.
- `docs/`: configuración técnica y contratos de integración.

## Preparar Android

1. Abrir la raíz en Android Studio e instalar el SDK solicitado por Gradle. La app requiere Android 14 o posterior.
2. Registrar `org.iot.project` en tu proyecto Firebase y colocar tu archivo privado `google-services.json` en `app/`.
3. Copiar `local.properties.example` a `local.properties` y configurar el SDK, la URL de tu API y Maps.
4. Habilitar los proveedores Firebase necesarios. Para Google, registrar las huellas del certificado y descargar la configuración OAuth actualizada; consultar [ACCESO_GOOGLE.md](docs/ACCESO_GOOGLE.md).
5. Sincronizar Gradle y ejecutar la app.

En PowerShell:

```powershell
.\gradlew.bat :app:assembleDebug
.\gradlew.bat :app:testDebugUnitTest
```

La URL se configura mediante `taxistas.api.baseUrl`, la variable `TAXISTAS_API_BASE_URL` o `-PtaxistasApiBaseUrl`. Usar la raíz HTTPS, por ejemplo `https://api.example.com/`, sin añadir `/api/`. El transporte rechaza redirecciones; configurar la dirección final. Debug admite el servidor local del emulador y release exige HTTPS.

## Preparar la web y API

Seguir [web/README.md](web/README.md). La web y la API son el mismo servidor Java 17, con MySQL y variables privadas para el panel, JWT y el almacenamiento de fotos.

Los taxistas se registran en la web y acceden desde Android con la opción **Taxista**. Sus cuentas SQL y JWT son independientes de las cuentas Firebase. La administración REST desde el superadministrador Android requiere configurar Firebase Admin y los UID autorizados en el servidor.

El contrato vigente está en [COMPATIBILIDAD_API.md](docs/COMPATIBILIDAD_API.md).

## Configuración privada

Firebase, `.env`, `local.properties`, `secrets.properties`, claves SSH, archivos de firma, fotos subidas y resultados de compilación se mantienen fuera de Git. Las plantillas `.example` contienen campos sin credenciales. Las claves del servidor permanecen en el backend.

Las pruebas Firebase usan el proyecto ficticio `demo-iot-hoteles` y credenciales de prueba sin acceso a proyectos reales. Para una demostración pública, usar datos ficticios: la API conserva consultas y operaciones de pasajeros sin autenticación; su autorización debe completarse antes de trabajar con viajes y datos personales reales.
