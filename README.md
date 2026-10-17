# TouchGrass 🌱

Una app Android para usar Instagram y TikTok sin caer en el *doomscrolling*.
Más adelante también YouTube.

TouchGrass **no sustituye** a la app oficial. Funciona *por encima* de ella mediante un
servicio de accesibilidad que detecta cuándo entras en el scroll (los Reels de Instagram,
Para ti y Comunidad de TikTok) y pone una pantalla de bloqueo encima. Si quieres verlos, pides unos minutos de tu saldo diario. Así puedes seguir
usando los mensajes, las publicaciones de tus amigos y las historias.

> **[Descargar la última versión](../../releases/latest)**. Bloquea los Reels de Instagram
> y las pestañas Para ti y Comunidad de TikTok. La versión antigua, basada en WebView, está archivada en el tag
> [`legacy-v0`](../../tree/legacy-v0).

## Compatibilidad

Funciona en **cualquier móvil Android 8.0 (API 26) o superior**, de cualquier marca.
El bloqueo usa el servicio de accesibilidad estándar de Android y las reglas dependen de las
apps de Instagram y TikTok, no del modelo de móvil.

Algunas capas de fabricante cierran apps en segundo plano de forma agresiva y pueden
detener la protección un momento. Android la vuelve a poner en marcha sola en unos segundos y
esos cortes no cuentan como haberla quitado. Si un corte dura, la app lo avisa en *Mi día*.
Más abajo se explica qué hacer en cada caso.

## Instalación

TouchGrass no está en ninguna tienda: se instala desde las
[Releases de GitHub](../../releases/latest).

1. **Descargar la APK.** Desde el móvil, abre la [última Release](../../releases/latest) y
   descarga `touchgrass-vX.Y.Z.apk` (en *Assets*). Ábrela desde Descargas y pulsa
   *Instalar*. Android pedirá permitir la instalación de apps de origen desconocido para el
   navegador o la app de archivos.
2. **Permitir ajustes restringidos** (Android 13 o superior). Android bloquea la
   accesibilidad a las apps instaladas fuera de una tienda. Ve a
   *Ajustes → Aplicaciones → TouchGrass → ⋮ → Permitir ajustes restringidos*.
   Si no aparece la opción, intenta activar primero la protección (paso 4); al rechazarlo,
   la opción se desbloquea.
3. **Primeros pasos.** La primera vez que abres TouchGrass te pide tu nombre (opcional), te
   explica cómo funciona y te pide tu límite de minutos al día. Antes de activar la protección
   te enseña qué lee el servicio de accesibilidad y qué no, y te pide que lo aceptes.
4. **Activar la protección.** En el último paso, pulsa *Activar en Accesibilidad* y activa
   TouchGrass en la lista de servicios. Vuelve a la app: debe mostrar *Protección activada*.

### Xiaomi, Redmi y POCO (HyperOS / MIUI)

HyperOS y MIUI matan el proceso de la app con mucha facilidad, por ejemplo al limpiar
memoria. Android vuelve a arrancar el servicio en unos segundos solo si el sistema le deja.
Para que le deje:

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

### Comprobar la descarga (opcional)

Cada Release incluye `touchgrass-vX.Y.Z.apk.sha256`. En un ordenador, con los dos ficheros
en la misma carpeta:

```bash
sha256sum -c touchgrass-vX.Y.Z.apk.sha256    # debe decir OK
```

Todas las versiones van firmadas con la misma clave. La huella SHA-256 del certificado es:

```
4d9cde9b128b248b103de0124f4ba33e56604af95620c538ef45f0e8af8df652
```

Se puede comprobar con `apksigner verify --print-certs touchgrass-vX.Y.Z.apk` (viene con el
Android SDK).

### Si aparece «TouchGrass se ha detenido»

El sistema ha cerrado el servicio aunque el ajuste siga activado y no ha vuelto en un minuto.
Tu día no se ve afectado: solo cuenta como trampa desactivarlo tú en Accesibilidad. Comprueba
el *Autoinicio* y el ahorro de batería (ver arriba). Si sigue sin volver, en *Ajustes →
Protección* pulsa *Reactivar en Accesibilidad*, **desactiva** TouchGrass y **vuelve a
activarlo**.

## Uso

- **Inicio** te saluda por tu nombre y muestra:
  - **Mi día:** los minutos que quedan hoy y cuándo se renueva el saldo (a las 8:00). Si la
    protección está quitada, el personaje sale con los ojos en cruz y un botón te lleva a
    arreglarlo en Ajustes.
  - **Mi semana:** la semana anterior y la actual, con una cara por día según lo que usaste. Las
    rachas de días con poco uso dan caras especiales. Si agotaste el saldo, el día sale triste y
    en gris; si quitaste la protección, con los ojos en cruz y en rojo.
- **Primer acceso del día.** La primera vez que abres la app cada día, salen las caras de los
  días que aún no habías visto y una frase sobre ayer. Si quitaste la protección, te lo
  recuerda y, si sigue quitada, te ofrece activarla.
- **Pedir tiempo**, en Inicio, abre una pantalla en la que eliges la app y después los minutos
  (atajos de 5 a 60 o − / +), nunca más de los que quedan hoy. El saldo es el mismo para
  todas las apps y solo hay un desbloqueo a la vez. Los minutos se restan al pedirlos y corren
  aunque salgas de la app. Al acabar vuelve el bloqueo. Si terminas antes desde Inicio, se
  devuelve lo que sobra en múltiplos de 5 minutos.
- **Pantalla de bloqueo.** Primero aparece *Volver atrás*, que te saca del scroll. Un segundo
  después, encima y más discreto, sale el enlace *Pedir tiempo de uso*, que abre la pantalla de
  pedir tiempo con esa app ya elegida.
- **Qué se bloquea:** en Instagram, los Reels: la pestaña y el visor, salvo el reel que abres
  desde un perfil, un chat, Inicio o Guardados. En TikTok, las pestañas **Para ti** y
  **Comunidad**; *Volver atrás* te lleva a Mensajes. Siguiendo, Amigos, Buscar, los perfiles
  y los chats quedan libres.
- **Motivo.** Antes de pedir tiempo eliges para qué: Baño, Comer, Bus, Pasatiempo, Dormir u
  Otro (con un texto corto).
- **Estadísticas** (icono de las barras, junto a Ajustes): por mes, los minutos desbloqueados y
  la media al día, cuántas veces pediste tiempo, cuántas saltó el bloqueo y cuántas volviste
  atrás, los días sin pedir tiempo y sin agotar el saldo, el reparto por app y por motivo, la
  comparación con los meses anteriores y el detalle de cada día.
- **Resumen del mes.** El día 1 a las 8:00 llega un aviso (si permites las notificaciones) y en
  Inicio sale una tarjeta con el resumen del mes anterior hasta que lo abres.
- **Ajustes** (icono de la rueda) permite cambiar tu nombre y el límite diario. Bajarlo se
  aplica al momento; subirlo, a partir del mes siguiente, y ese cambio pendiente se puede
  cancelar. También muestra el estado de la protección y la política de privacidad.
- Con la fecha, la hora o la zona horaria en manual, el saldo no se renueva y la app avisa
  para activar la hora automática.

## Actualizaciones

TouchGrass **no se actualiza sola**. No tiene permiso de Internet, a propósito, así que no
puede comprobar si hay versiones nuevas.

- **Para actualizar:** descarga la APK de la nueva Release e instálala encima de la que
  tienes, sin desinstalar nada. Como todas las versiones van firmadas con la misma clave,
  Android la acepta como actualización y conservas el límite y el saldo.
- **Para enterarte de las versiones nuevas**, cualquiera de estas dos:
  - En GitHub, en este repo: *Watch → Custom → Releases*. Te llega un aviso (y un correo)
    con cada versión.
  - Con [Obtainium](https://github.com/ImranR98/Obtainium), una app libre para Android:
    añade la URL de este repo y te avisa, o actualiza, cuando sale una Release nueva.

Si Android dice que la app no se ha instalado o que hay un conflicto con un paquete
existente, la APK no está firmada con la misma clave. No la instales y descárgala solo de
las Releases de este repo.

## Principios

- **Privacidad total:** la app no pide permiso de Internet y ningún dato sale del móvil,
  tampoco las estadísticas ni los motivos.
- **Mínima intrusión:** el servicio solo observa las apps objetivo y solo lee los
  identificadores de vista necesarios y, en TikTok, el nombre de las pestañas de arriba
  («Para ti», «Siguiendo»…). Nunca lee el contenido de mensajes.
- **Difícil de saltar:** subir el límite se aplica al mes siguiente, los desbloqueos se miden
  con un reloj monotónico y, sin fecha y hora automáticas, el saldo no se renueva ni se
  desbloquea nada. Cambiar la hora del móvil no sirve.

## Compilar

Para desarrollar o para instalar desde el código en lugar de las Releases.

Requisitos: JDK 17 o superior y Android SDK 37 (o Android Studio, que trae ambos).
Versión mínima de Android: 8.0 (API 26).

```bash
./gradlew assembleDebug        # APK de debug
./gradlew installDebug         # la instala en el móvil conectado por adb
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
- [Política de privacidad](docs/PRIVACIDAD.md)
- [Decisiones de arquitectura (ADR)](docs/adr/)
