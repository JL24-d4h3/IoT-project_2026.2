# Recuperación del proyecto 1TEL05

El proyecto vivía en `/tmp/1TEL05` y se perdió al apagar el PC: `/tmp` se limpia en
cada arranque (systemd-tmpfiles), así que no se perdió de la RAM, se borró del disco.

Este directorio es la reconstrucción. **No se recuperó ninguna copia de seguridad del
proyecto**: se reconstruyó reproduciendo, en orden, todas las operaciones que la sesión
de Claude Code había dejado registradas en `~/.claude/projects/-tmp-1TEL05/`.

## Qué hay

| | |
|---|---|
| Ficheros | 395 (incluido este documento) |
| Clases Java | 183 (176 de `main` + 7 de test, 27.514 líneas) |
| Layouts | 87 |
| Drawables | 61 |
| Documentos originales | 3 (`docs/`) |

La app es **Estadía**, el proyecto de 1TEL05 (reservas de alojamiento), en Java con
Views y ViewBinding sobre `org.iot.project`.

## Cómo se reconstruyó

Replay cronológico de `Write`, `Edit`, `MultiEdit` y de los comandos de shell que mutan
ficheros (`sed -i`, heredocs, scripts de Python, `rm`, `mv`). Solo se replican las
operaciones que **tuvieron éxito** en la sesión original: repetir una que falló
introduciría cambios que nunca existieron.

Además del replay, cuatro fuentes de rescate:

1. **Lecturas del transcript** — los `Read` guardan el contenido: los 3 documentos de
   `docs/`, idénticos byte a byte a como se leyeron.
2. **`cat` de la sesión** — el andamiaje que creó Android Studio antes de que empezara
   la sesión (no lo escribió Claude, así que no hay `Write` que lo reproduzca):
   `settings.gradle`, `gradle.properties`, `local.properties`,
   `res/drawable/ic_launcher_background.xml`, `res/mipmap-anydpi/ic_launcher.xml`,
   y dos ficheros de `.idea/`.
3. **El commit inicial de tu repositorio de GitHub** — la sesión se perdió, pero el
   commit `0b5eb37` ("Creación del proyecto - IoT 2026.2") sí sobrevivió. De ahí salió
   todo el andamiaje binario y de plantilla que no aparece en ningún registro de texto.
   Ver la sección siguiente.
4. **Regenerado** — 7 ficheros de plantilla que no aparecían en ningún registro. Ya no
   queda ninguno: los 7 se han sustituido por los originales auténticos del commit.

## Fiabilidad

Se contrastó el resultado contra las **166 lecturas** registradas en el transcript:
**150 coinciden exactamente**. Las 16 restantes están identificadas una a una y
ninguna indica pérdida de contenido:

- **6** son lecturas parciales (el `Read` llevaba `limit`): lo recuperado es *más*
  completo que la lectura con la que se compara.
- **5** son los 3 documentos de `docs/`, leídos al principio de la sesión. Se
  recuperaron después por otra vía y se verificaron **idénticos byte a byte**.
- **4** son ficheros de build (`build.gradle`, `app/build.gradle`,
  `AndroidManifest.xml`, `gradle/libs.versions.toml`) leídos *antes* de ser editados:
  la comparación es contra la versión previa a la edición.
- **1** es `MainActivity.kt`, borrado a propósito (ver abajo).

Además, el verificador del propio proyecto, `tools/verificar_recursos.py`, pasa:
*"Sin problemas: todas las referencias resuelven"*.

## El andamiaje auténtico (del commit inicial)

Estos ficheros no dejaron rastro en el transcript —varios son binarios, y el resto los
creó Android Studio, no Claude— pero **sí estaban en el commit inicial de tu repositorio**,
así que están recuperados tal cual, byte a byte:

| | |
|---|---|
| Wrapper de Gradle | `gradlew`, `gradlew.bat`, `gradle/wrapper/gradle-wrapper.jar` y `.properties` (Gradle 9.1.0) |
| Iconos raster | los 10 `res/mipmap-*/ic_launcher*.webp` |
| Plantilla | `res/drawable/ic_launcher_foreground.xml` (el robot de Android), `res/mipmap-anydpi/ic_launcher_round.xml`, `res/xml/backup_rules.xml`, `res/xml/data_extraction_rules.xml`, `app/proguard-rules.pro`, `.gitignore`, `app/.gitignore` |

Se contrastó además que el resto de ficheros que el commit y la reconstrucción tienen en
común coinciden: `settings.gradle`, `.gitignore` y
`res/drawable/ic_launcher_background.xml` son idénticos; `gradle.properties` y
`res/mipmap-anydpi/ic_launcher.xml` solo diferían en el salto de línea final y se ha
tomado la versión del commit.

**Del commit NO se han tomado** los ficheros que la sesión modificó o borró a propósito:
`MainActivity.kt`, `ui/theme/{Color,Theme,Type}.kt`, `ExampleUnitTest.kt`,
`ExampleInstrumentedTest.kt` (se eliminaron a las 05:00:38 al pasar el proyecto de Kotlin
a Java: reponerlos crearía una clase `MainActivity` duplicada), ni `AndroidManifest.xml`,
`build.gradle`, `app/build.gradle`, `colors.xml`, `strings.xml`, `themes.xml` ni
`libs.versions.toml`, que la sesión reescribió por completo.

`.idea/` está en el directorio pero no se sube: el commit original tampoco lo llevaba y son
ajustes locales del IDE.

## Lo que NO se pudo recuperar

Nada del código. Lo único que no está y es correcto que no esté son los ficheros que la
sesión borró a propósito (los `.kt` de plantilla, arriba).

## Para que compile

1. **Abre la carpeta `1TEL05-recuperado`, no `app/`.** Si abres `app/`, Android Studio
   intenta ejecutar la tarea `wrapper` sobre el módulo `:app` y falla con *"Task
   'wrapper' not found in project ':app'"*: la tarea `wrapper` solo existe en la raíz.
2. `File > Open` → elige la carpeta que contiene `settings.gradle` → *Sync*.

El wrapper es el original, así que no hay nada que reparar: **Gradle 9.1.0** con
**AGP 9.0.1**, y la distribución ya está descargada en `~/.gradle`.

Si el SDK no está donde estaba, ajusta `local.properties` (no se sube al repo, es tuyo).

## Aviso

En la memoria del proyecto quedaron anotadas decisiones de diseño que **nunca
aprovaste**: el nombre "Estadía" y la paleta teal `#0D5C63` + ámbar `#E9A23B`.
Están aplicadas en el código y en el tema. El detalle está en
`docs/memory/CONTEXTO_FRONTEND.md`.
