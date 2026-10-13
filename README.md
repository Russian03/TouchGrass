# TouchGrass 🌱

Una app Android para usar Instagram sin caer en el *doomscrolling*.
Más adelante también TikTok y YouTube.

TouchGrass **no sustituye** a la app oficial. Funciona *por encima* de ella mediante un
servicio de accesibilidad que detecta las zonas adictivas (Reels, Explorar, scroll infinito)
y las bloquea o les pone un límite de tiempo diario. Así puedes seguir usando los mensajes,
las publicaciones de tus amigos y las historias.

> Estado: **en reescritura**. De momento bloquea los Reels de Instagram. La versión antigua
> basada en WebView está archivada en el tag [`legacy-v0`](../../tree/legacy-v0).

## Compatibilidad

Funciona en **cualquier móvil Android 8.0 (API 26) o superior**, de cualquier marca.
El bloqueo usa el servicio de accesibilidad estándar de Android y las reglas dependen de la
app de Instagram, no del modelo de móvil.

Algunas capas de fabricante cierran apps en segundo plano de forma agresiva y pueden
detener la protección. La app lo detecta y lo avisa en la pantalla de inicio
(*Protección detenida*). Más abajo se explica qué hacer en cada caso.

## Instalación

Todavía no hay versión publicada en ninguna tienda. Hay que compilar la APK
(ver [Compilar](#compilar)) e instalarla en el móvil:

1. **Instalar la APK**, de una de estas formas:
   - Con el móvil conectado por USB y la depuración USB activada:
     `./gradlew installDebug` (o `adb install app/build/outputs/apk/debug/app-debug.apk`).
   - Copiando `app-debug.apk` al móvil y abriéndola. Android pedirá permitir la
     instalación de apps de origen desconocido.
2. **Permitir ajustes restringidos** (Android 13 o superior). Android bloquea la
   accesibilidad a las apps instaladas fuera de una tienda. Ve a
   *Ajustes → Aplicaciones → TouchGrass → ⋮ → Permitir ajustes restringidos*.
   Si no aparece la opción, intenta activar primero el paso 3; al rechazarlo, la opción
   se desbloquea.
3. **Primeros pasos.** La primera vez que abres TouchGrass te explica cómo funciona, qué
   lee el servicio de accesibilidad y te pide tu límite de minutos al día.
4. **Activar la protección.** En el último paso, pulsa *Activar en Accesibilidad* y activa
   TouchGrass en la lista de servicios. Vuelve a la app: debe mostrar *Protección activada*.

## Uso

- **Inicio** muestra el saldo de hoy (minutos usados y los que quedan), cuándo se renueva
  (a las 8:00) y el estado de la protección.
- **Pedir tiempo**, también en Inicio, desbloquea los Reels unos minutos (atajos de 5 a 60 o
  − / +), nunca más de los que quedan hoy. Se restan al pedirlos y corren aunque salgas de
  Instagram. Al acabar vuelve el bloqueo. Si terminas antes, se devuelve lo que sobra en
  múltiplos de 5 minutos. Desde la pantalla de bloqueo, *Ir a TouchGrass* lleva aquí.
- **Ajustes** (icono de la rueda) permite cambiar el límite diario. Bajarlo se aplica al
  momento; subirlo, a partir del mes siguiente, y ese cambio pendiente se puede cancelar.
- Con la fecha, la hora o la zona horaria en manual, el saldo no se renueva y la app avisa
  para activar la hora automática.

### Xiaomi, Redmi y POCO (HyperOS / MIUI)

HyperOS y MIUI matan el proceso de la app con mucha facilidad, por ejemplo al limpiar
Recientes, y Android no vuelve a arrancar el servicio por sí solo. Para evitarlo:

- En *Ajustes → Aplicaciones → TouchGrass*:
  - Activa **Autoinicio**.
  - En **Ahorro de batería**, elige **Sin restricciones**.
- La propia app muestra este consejo en Xiaomi, con un botón que abre esos ajustes.
- TouchGrass no aparece en Recientes a propósito: así no se puede cerrar deslizando su
  tarjeta.

### Otros Android

En Android puro (Pixel, Motorola, etc.) normalmente no hace falta nada más. En otras
marcas con ahorro de batería agresivo (Samsung, Huawei, OnePlus, Oppo…) conviene
quitar a TouchGrass de las optimizaciones de batería si la protección se detiene.
Por ejemplo, en Samsung: *Ajustes → Batería → Límites de uso en segundo plano →
Aplicaciones que nunca entran en suspensión*.

Solo se ha probado en un Xiaomi con HyperOS. En otras marcas debería funcionar, pero
no está verificado.

### Si aparece «Protección detenida»

El sistema ha cerrado el servicio aunque el ajuste siga activado. Pulsa
*Reactivar en Accesibilidad*, **desactiva** TouchGrass y **vuelve a activarlo**.

## Principios

- **Privacidad total:** la app no pide permiso de Internet y ningún dato sale del móvil.
- **Mínima intrusión:** el servicio solo observa las apps objetivo y solo lee los
  identificadores de vista necesarios. Nunca lee el contenido de mensajes.
- **Difícil de saltar:** los cambios para relajar los límites se aplican al día siguiente
  y el tiempo se mide con un reloj monotónico, así que cambiar la hora del móvil no sirve.

## Compilar

Requisitos: JDK 17 o superior y Android SDK 37 (o Android Studio, que trae ambos).
Versión mínima de Android: 8.0 (API 26).

```bash
./gradlew assembleDebug        # APK de debug
./gradlew testDebugUnitTest    # tests unitarios
./gradlew lintDebug            # lint de Android
./gradlew ktlintCheck          # estilo de código (ktlintFormat lo corrige)
./gradlew assembleRelease      # APK de release (firmada si hay clave, ver abajo)
```

## Publicar una versión

Las APK de release van firmadas con una clave propia. Android solo deja actualizar la app
si la versión nueva está firmada con **la misma clave**, así que si se pierde no se pueden
publicar más actualizaciones. Guarda una copia de seguridad fuera del ordenador. La clave
nunca entra al repo: `.gitignore` excluye `*.jks` y `keystore.properties`.

**1. Crear la clave (una sola vez).** Pide dos contraseñas y unos datos que pueden ir en blanco:

```bash
mkdir -p ~/keys
keytool -genkeypair -keystore ~/keys/touchgrass.jks -alias touchgrass \
  -keyalg RSA -keysize 4096 -validity 10000 -storetype PKCS12
```

**2. Firmar en local.** Crea `keystore.properties` en la raíz del proyecto:

```properties
storeFile=/home/<usuario>/keys/touchgrass.jks
storePassword=<contraseña>
keyAlias=touchgrass
keyPassword=<contraseña>
```

Con él, `./gradlew assembleRelease` deja la APK firmada en
`app/build/outputs/apk/release/app-release.apk`. Sin él, la release sale sin firmar.

**3. Release automática en GitHub.** En *Settings → Secrets and variables → Actions* del
repo, crea estos secretos:

| Secreto                         | Valor                                        |
|---------------------------------|----------------------------------------------|
| `TOUCHGRASS_KEYSTORE_BASE64`    | salida de `base64 -w0 ~/keys/touchgrass.jks` |
| `TOUCHGRASS_KEYSTORE_PASSWORD`  | contraseña del keystore                      |
| `TOUCHGRASS_KEY_ALIAS`          | `touchgrass`                                 |
| `TOUCHGRASS_KEY_PASSWORD`       | contraseña de la clave                       |

Al subir un tag (`git push origin v1.0.0`), la CI compila y firma la APK, comprueba la firma
y crea una Release con `touchgrass-v1.0.0.apk` y su suma SHA-256.

La APK de release (`io.github.russian03.touchgrass`) y la de debug (`.debug`) son apps
distintas y pueden convivir en el mismo móvil, cada una con su propio servicio de
accesibilidad.

## Flujo de trabajo

Trabajamos por **bloques**. Cada bloque vive en una rama `bloque-N/<nombre>`, se revisa
en un Pull Request y se integra en `main` con *Squash and merge*. Cada integración lleva
un tag de versión.
El plan de bloques está en [docs/ROADMAP.md](docs/ROADMAP.md).

Commits en formato [Conventional Commits](https://www.conventionalcommits.org/es/):
`feat:`, `fix:`, `chore:`, `docs:`, `refactor:`, `test:`, `ci:`.

Cada push y PR a `main` pasa por CI (GitHub Actions): ktlint, lint, tests y build.

## Documentación

- [Arquitectura](docs/ARCHITECTURE.md)
- [Roadmap por bloques](docs/ROADMAP.md)
- [Decisiones de arquitectura (ADR)](docs/adr/)
