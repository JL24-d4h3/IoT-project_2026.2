# Web y API de taxistas

Servidor Spring Boot con vistas Thymeleaf para registrar taxistas y administrar sus cuentas. La misma aplicacion expone la API REST que consume Android y persiste taxistas, servicios y valoraciones en MySQL.

## Requisitos

- JDK 17 y Maven 3.9 en `PATH`.
- MySQL 8 para ejecutar la aplicacion.

Comprobar `java -version` y `mvn -version`: ambos deben usar Java 17. Ejecutar los siguientes pasos desde la carpeta `web/`.

## Configuracion local

Copiar la plantilla en Linux:

```bash
cp .env.example .env
```

En PowerShell:

```powershell
Copy-Item .env.example .env
```

Completar `DB_URL`, `DB_USER`, `DB_PASSWORD`, `ADMIN_PASSWORD` y `JWT_SECRET`. Usar credenciales propias; `JWT_SECRET` debe tener al menos 32 bytes aleatorios. No hay contrasenas ni claves de acceso predeterminadas. `.env` se lee como propiedades Java, sin ejecutarse y sin comillas de shell. Tambien se pueden proporcionar las variables al proceso Java directamente.

Ejemplo de URL para MySQL instalado en el mismo equipo:

```properties
DB_URL=jdbc:mysql://localhost:3306/taxidb?useSSL=false&allowPublicKeyRetrieval=true&serverTimezone=America/Lima&characterEncoding=UTF-8
```

El usuario del panel es `superadmin`; su contrasena se obtiene de `ADMIN_PASSWORD`. No subir `.env`, credenciales de servicio, claves privadas o fotos al repositorio.

## Base de datos

El archivo `database/schema.sql` contiene solo el DDL de `taxidb` y sus tres tablas. Para una instalacion nueva, entrar al cliente MySQL con una cuenta administrativa y cargar el esquema:

```text
mysql -u ADMINISTRADOR -p
```

Dentro del cliente, desde la carpeta `web/`:

```sql
SOURCE database/schema.sql;
```

Crear por separado un usuario de aplicacion y completar `DB_USER` y `DB_PASSWORD`. La aplicacion necesita permisos de lectura y escritura sobre `taxidb`; no necesita ejecutar como administrador SQL. `DB_DDL_AUTO=none` evita cambios automaticos de esquema. Se puede usar `validate` para comprobarlo al arrancar.

## Compilar, probar y ejecutar

En Linux y PowerShell:

```text
mvn --batch-mode --no-transfer-progress verify
mvn spring-boot:run
```

Las pruebas existentes usan repositorios y verificadores simulados; no requieren cuentas Firebase o una base MySQL real. `verify` construye el JAR sin arrancar la aplicacion.

Tambien se puede ejecutar el artefacto empaquetado:

```text
java -Duser.timezone=America/Lima -jar target/gestion-taxistas-1.0.0.jar
```

La web local esta en `http://localhost:8080/registro`, el panel en `/login` y Swagger en `/swagger-ui.html`. Para Android, configurar la URL base con el host accesible desde el dispositivo y una barra final, sin anadir `/api/`. En produccion utilizar HTTPS y configurar el proxy del servidor.

## Fotos y datos de demostracion

`APP_ALMACENAMIENTO=local` es el valor predeterminado: guarda imagenes en `uploads/` y usa `APP_URL_BASE` para sus URLs. Para Cloudinary, establecer `APP_ALMACENAMIENTO=cloudinary` y completar sus tres variables en el entorno privado. Con almacenamiento local se necesita un disco persistente para conservar las fotos al desplegar.

Las cuentas ficticias solo se insertan con el perfil `demo`, sin `prod`, con `DEMO_TAXISTA_PASSWORD` definido y si no hay taxistas. Para habilitarlas expresamente, configurar `SPRING_PROFILES_ACTIVE=demo` y una contrasena propia. Si falta esa contrasena, no se insertan datos. El perfil `prod` desactiva siempre este inicializador.

## Autenticacion y API

- El panel web usa sesion y CSRF con el usuario `superadmin`.
- El conductor inicia sesion en `POST /api/auth/login`; el servidor exige cuenta activa y habilitada, y devuelve su JWT.
- La administracion REST en `/api/admin/taxistas` utiliza un ID token Firebase verificado y una lista de UID autorizados en el servidor.

Para esta ultima funcion, configurar `FIREBASE_PROJECT_ID`, `FIREBASE_SUPERADMIN_UIDS` y Application Default Credentials mediante `GOOGLE_APPLICATION_CREDENTIALS`, fuera del repositorio. La web y la API del conductor pueden arrancar sin estas variables; la administracion REST responde `503` hasta configurarlas. El archivo Android `google-services.json` no es una credencial del servidor.

Las rutas principales son `/api/taxistas`, `/api/servicios` y `/api/admin/taxistas`. El flujo del servicio mantiene los estados `SOLICITADO`, `ASIGNADO`, `EN_CAMINO`, `EN_TRASLADO` y `FINALIZADO`, con cierre mediante QR. La consulta de servicios y ubicaciones, la solicitud de taxi y las valoraciones mantienen el acceso publico del contrato actual.
