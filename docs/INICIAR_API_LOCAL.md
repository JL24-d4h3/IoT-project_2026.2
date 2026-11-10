# Iniciar la web y API local

El backend está en `web/`. Utiliza Java 17, Maven y MySQL. La web Thymeleaf y la API REST arrancan juntas en el puerto 8080.

1. Crear el esquema MySQL mediante `web/database/schema.sql`.
2. Copiar `web/.env.example` a `web/.env` y completar las variables privadas. Indicar el host y puerto reales de MySQL.
3. Seleccionar `APP_ALMACENAMIENTO=local` para fotos locales o configurar las tres variables Cloudinary.
4. Desde `web/`, ejecutar:

```bash
mvn spring-boot:run
```

Mantener la terminal abierta mientras se utilice el servidor. Consultar `http://localhost:8080/registro` y `http://localhost:8080/api/taxistas/disponibles`.

Android debug utiliza `http://10.0.2.2:8080/` desde el emulador oficial. Para un teléfono físico, configurar una dirección alcanzable o el reenvío USB descrito en [API_TAXISTAS_LOCAL.md](API_TAXISTAS_LOCAL.md). Para un servidor publicado, usar su dominio HTTPS en `taxistas.api.baseUrl` y reconstruir la app.

El perfil `prod` evita cargar datos de demostración. La administración de taxistas desde el superadministrador Android requiere Firebase Admin y `FIREBASE_SUPERADMIN_UIDS` en el backend.
