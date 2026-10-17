# Arquitectura

## Visión general

```
 Instagram / TikTok (apps oficiales)
        │  eventos de accesibilidad (cambio de ventana, scroll, contenido)
        ▼
 TouchGrassAccessibilityService ──► ScreenSnapshot (solo viewIds + contentDescription)
        │                                   │
        │                                   ▼
        │                           RuleEngine (Kotlin puro)
        │                                   │  Decision
        ▼                                   ▼
 Acciones: GLOBAL_ACTION_BACK · overlay "Touch grass" · descontar tiempo
        │
        ▼
 DataStore (ajustes + uso diario) ──► UI Compose (panel, ajustes, onboarding)
```

## Módulo y paquetes

Hay un único módulo `app`, con paquete raíz `io.github.russian03.touchgrass`.

| Paquete    | Responsabilidad                                                        | Depende de Android |
|------------|------------------------------------------------------------------------|--------------------|
| `core`     | Modelo de reglas, `RuleEngine`, saldo diario (`BudgetPolicy`)          | No                 |
| `data`     | DataStore, carga de reglas desde `assets/rules/*.json`                 | Sí                 |
| `service`  | `AccessibilityService`: traduce eventos a snapshots y ejecuta acciones | Sí                 |
| `overlay`  | Overlay de bloqueo (`TYPE_ACCESSIBILITY_OVERLAY`)                      | Sí                 |
| `ui`       | Compose + ViewModels (MVVM), inyección con Hilt                        | Sí                 |

La lógica de decisión está en `core` y no depende de Android, así que se testea con JUnit
sin emulador.

## Reglas de detección

Las apps cambian sus identificadores internos con las actualizaciones, así que las reglas no
están escritas en el código: van en ficheros JSON versionados, uno por app
(`assets/rules/instagram.json`, `assets/rules/tiktok.json`). Cada regla indica:

- la **app objetivo** (`com.instagram.android`, `com.zhiliaoapp.musically`),
- un **matcher** (viewId, contentDescription o combinación),
- una **acción** (`BLOCK`, `LIMIT`, `WARN`).

En TikTok casi todos los identificadores están ofuscados (`oph`, `opi`…) y cambian con cada
versión, y las pestañas de arriba comparten el de Android `android:id/text1`. Por eso una
condición puede pedir también un **texto** (`"text": ["Para ti", "For You"]`), y la acción
`click` puede elegir la vista por su texto. El snapshot solo guarda el texto de las vistas que
nombra alguna regla, y solo si es uno de los que busca; cualquier otro texto se descarta.
Los identificadores de Android (`android:id/…`) se guardan con su prefijo; los de la app, sin él.

En el build de debug, el servicio vuelca a logcat los viewIds visibles cada vez que cambian
(`DUMP pkg=… ids=…`), para descubrir los nuevos cuando una actualización de Instagram o TikTok
rompa una regla. En release no existe: R8 quita esos logs.

## Estadísticas

`core/stats/StatsPolicy` (Kotlin puro) apunta y resume; `data/stats/StatsRepository` las guarda en
otro DataStore multiproceso (`stats.json`), separado del saldo para que un fallo al guardarlas no
afecte nunca al bloqueo.

- Por **día lógico** (de 8:00 a 8:00, como el saldo): cada desbloqueo con su hora, app, minutos,
  minutos devueltos, motivo y nota; y, por app, cuántas veces saltó el bloqueo y cuántas se volvió
  atrás. Los desbloqueos los apunta la app; los bloqueos y las salidas, el servicio.
- Los días cuentan desde `since`, el primer día con estadísticas (lo fija el servicio al
  arrancar). Un día sin datos es un día sin pedir tiempo. Se guardan 24 meses.
- El resumen del mes (`StatsPolicy.month`) y la comparación con los meses anteriores (media
  diaria, porque el primer mes y el actual están a medias) se calculan al vuelo. El resumen lleva
  también la cara de cada día del mes (`calendar`), cuántos días pasados hubo de cada cara
  (`faces`) y los bloqueos de cada día (`blocksByDay`), para el calendario y el gráfico de línea.
- **Rachas:** `currentStreak` son los días seguidos de poco uso que llegan hasta hoy (hoy cuenta
  mientras siga siendo de poco uso) y `bestStreak`, la más larga desde `since`. Se ven en «Mis
  logros» de Inicio y en los recuadros de las estadísticas.
- Ya no hay aviso del resumen del mes ni tarjeta en Inicio. Al arrancar, el servicio cancela la
  alarma y borra el canal de avisos que dejaron las versiones anteriores.

### Caras de los días

Cada día pasado tiene una cara (`core/stats/DayFace`) según la parte del límite gastada y la racha
de días de poco uso (hasta el 25 % del límite, sin agotarlo):

| Cara | Cuándo |
|------|--------|
| `Mythic`, `Legend`, `Cool` | Poco uso con racha de 14, 7 o 3 días o más |
| `Free` | Sin pedir tiempo |
| `Happy`, `Calm`, `Neutral`, `Tight` | Hasta el 25 %, 50 %, 75 % o más sin agotarlo |
| `Sad` | Saldo agotado (cara triste, en gris) |
| `Disabled` | Se quitó la protección ese día: no cuenta lo usado y rompe la racha (ojos en cruz, en rojo) |

«Mi semana» enseña la semana anterior y la actual con esas caras (`ui/design/DayPill`). Los días
cuentan desde `since`, que fija el servicio la primera vez que se pone en marcha: antes de activar
la protección por primera vez no hay días que contar (`Untracked`, en gris con un guion), así que
tardar en dar el permiso no deja ningún día con cara mala.

### Primer acceso del día

La primera vez que se abre la app cada día salen, en un pop-up, las caras de los días que aún no
se han enseñado (desde el último enseñado hasta ayer, sin salir de «Mi semana»;
`StatsPolicy.unclaimedDays`). Debajo va una frase según la cara de ayer, o una de «sin trampas» si
alguno de esos días se quitó la protección (si son varios, se nombra el último). Al cerrarlo se
guarda `lastDayClaimed`. Los días anteriores a `since` (los del guion) nunca se enseñan: el primer
pop-up sale el día después de activar la protección por primera vez, con ese primer día. Si la protección sigue quitada, además de «Volver» sale «Activar
protección», que lleva al apartado de la protección de Ajustes.

## Estado de la protección

`service/AccessibilityStatus` distingue tres estados (`ProtectionState`):

- **`Running`**: activada en Accesibilidad y el sistema tiene el servicio vinculado.
- **`Off`**: el usuario la ha desactivado en Accesibilidad. Es lo único que cuenta como trampa.
- **`Stopped`**: sigue activada en el ajuste, pero el servicio no está vinculado. Pasa al cerrar
  el sistema el proceso `:blocker` (HyperOS al limpiar memoria), al reiniciar el móvil o al
  actualizar la app. Android lo vuelve a vincular solo en unos segundos si el sistema le deja
  arrancar (en un Xiaomi con «Autoinicio», medido: unos 4 s tras matar el proceso, sin abrir la
  app). **Nunca** cuenta como trampa.

Cómo se apunta un día sin protección (`DayStats.protectionOff`):

- Al desactivarla, Android llama a `onUnbind` del servicio. Solo se apunta si ya no está activada
  en el ajuste; si lo sigue estando, es un corte y no se apunta nada.
- Al abrir Inicio con el estado `Off` (por si `onUnbind` no llegó a guardarse).
- Mientras sigue quitada, cuentan todos los días desde `protectionOffSince`; al volver a
  activarla, el servicio los marca (`StatsPolicy.markProtectionOn`).

En la interfaz (`ui/common/ProtectionCard.kt`):

- `rememberProtectionState()` se actualiza solo: al volver a la app, al cambiar el ajuste de
  Accesibilidad (un `ContentObserver`), al vincularse o desvincularse el servicio (Android 13+) y,
  mientras no funciona, cada 2 s. Al volver la conexión todo se arregla sin salir de la pantalla.
- `rememberShownProtection()` aplica un margen de 60 s (`service/ProtectionGrace`): un corte
  recién empezado se enseña como si funcionara («Reconectando…» en Ajustes). Si dura más, «Mi día»
  sale con el personaje preocupado; con `Off`, con los ojos en cruz y en tinta.
- El pop-up del primer acceso solo ofrece activar la protección con `Off`.

## Consentimiento del servicio

Google Play exige un aviso destacado antes de pedir el servicio de accesibilidad. En los primeros
pasos, y en cualquier «Activar» de la app si aún no se ha aceptado, sale el aviso
(`ui/common/ProtectionConsent.kt`): qué mira, qué nunca lee y que nada sale del móvil, con
«Acepto» y «No, gracias». Se guarda `Profile.accessibilityConsent`; quien ya tenía la protección
funcionando de una versión anterior lo da por aceptado. La política de privacidad va dentro de la
app (`PrivacyPolicyDialog`) y es la misma que [`PRIVACIDAD.md`](PRIVACIDAD.md).

## Saldo diario y desbloqueos

Cada mes el usuario tiene un **límite diario** de minutos que puede desbloquear. Las reglas están
en `core/budget/BudgetPolicy` (Kotlin puro) y el estado se guarda con `data/budget/BudgetRepository`.

- El día va de **8:00 a 8:00** (hora local). Cada día empieza con el límite completo y lo que no se
  gasta se pierde.
- Un desbloqueo se pide para una app y unos minutos, que se **restan enteros al pedirlo**. Dura
  esos minutos de reloj, aunque se salga de la app. Solo puede haber uno a la vez y el saldo es
  el mismo para todas las apps. La pantalla de pedir tiempo (`ui/unlock`) lista las apps con
  reglas usando el `name` y `blocks` de cada fichero. El enlace de la pantalla de bloqueo abre
  Inicio (extra `EXTRA_OPEN_HOME` de `MainActivity`), desde donde se pide tiempo.
- Un desbloqueo se puede **terminar antes de tiempo**. Se devuelve lo que le quedaba, redondeado
  hacia abajo a múltiplos de 5 (13 → 10, 4 → 0), y solo el mismo día lógico en que se pidió.
- Mientras dura, el servicio sigue la navegación pero no pone la pantalla de bloqueo, y deja
  programada otra evaluación para el momento exacto en que caduca.
- **Bajar** el límite se aplica al momento. **Subirlo** queda pendiente hasta el día 1 del mes
  siguiente.

### Dos procesos

El servicio de accesibilidad corre en el proceso `:blocker` y la interfaz en el principal. El
estado se comparte con un **DataStore multiproceso** (`MultiProcessDataStoreFactory`, JSON en
`filesDir/datastore/budget.json`). Los cambios hechos desde la app llegan al servicio al momento.

### Antitrampas

- La duración de un desbloqueo se mide con el reloj **monotónico** (`elapsedRealtime`), que no se
  puede cambiar a mano. Tras un reinicio (lo indica `BOOT_COUNT`) se usa el reloj de pared, pero
  nunca para alargar la sesión.
- Todo lo que depende de la fecha (renovar el saldo, aplicar el límite del mes siguiente, pedir un
  desbloqueo) exige **fecha, hora y zona horaria automáticas**. Sin ellas, el estado se congela.
- Si algo falla, se bloquea. Un fichero corrupto o ilegible vuelve al estado por defecto, que no
  tiene límite y no desbloquea nada.

## Perfil

`data/profile/ProfileRepository` guarda en `profile.json` (DataStore normal, solo lo usa la app)
el nombre opcional con el que Inicio saluda. Se pide en los primeros pasos y se cambia en Ajustes;
se limpia antes de guardarlo (sin asteriscos, máximo 20 caracteres) y no influye en el bloqueo.

## Interfaz

Compose con Material 3 Expressive y la paleta de siempre: crema de fondo, tinta y lima
(`ui/theme/Color.kt`). Las piezas comunes están en `ui/design` y `ui/common`:

- **`LimeHeader`**: el banner lima de arriba, con las dos esquinas de abajo en sentidos opuestos.
  Inicio, Ajustes y Estadísticas lo comparten (`SharedTransitionLayout` en `AppNavHost` y
  `LocalLimeHeaderScope`): al ir de una a otra cambia de tamaño y los textos se funden.
- **`SectionTitle` / `MixedTitle`**: títulos con el grosor mezclado («Mi \*día\*»).
- **`BeigeCard`**: las tarjetas beige de Inicio, Ajustes y los primeros pasos.
- **`ChunkyPillButton`**: los botones con relieve (lima el principal, crema el secundario, gris
  apagado).
- **`Sprout`**: el logo como personaje, con caras (`SproutMood`), parpadeo, respiración y saltito.
  Lo usan los primeros pasos y «Mi día» sin protección.
- **`Tile`**: los recuadros de color de «Mis logros» y de las estadísticas.
- **`AnimatedFlame`**: el fuego de la racha, dibujado en código, que parpadea.
- **`staggerIn`**: la entrada en cascada de cada pantalla. **`fadingEdges`**: difuminado de los
  textos que se deslizan.

Ajustes tiene los apartados Tu nombre, Límite diario, Protección (estado, hora manual y consejo
de Xiaomi) y Privacidad. Al llegar desde «Ir a Ajustes» (en «Mi día» o en el pop-up del día),
baja hasta la protección y su tarjeta parpadea despacio mientras no funcione. «Volver» solo
navega con la pantalla activa (`dropUnlessResumed`), para que pulsar varias veces no deje la
navegación vacía.

## Privacidad y seguridad

- No se pide permiso `INTERNET` ni ningún permiso en tiempo de ejecución: solo el servicio de
  accesibilidad, que el usuario activa a mano tras aceptar el aviso.
- El servicio de accesibilidad se declara con `packageNames` restringido a las apps objetivo.
- Los snapshots descartan el texto del contenido (mensajes, captions) y solo guardan
  identificadores estructurales y, de las vistas que nombran las reglas, los textos que buscan
  (las pestañas de TikTok).
- `allowBackup=false` y las reglas de extracción excluyen todos los datos.
- Lo que se guarda (nombre, límite, desbloqueos con motivo, bloqueos y días sin protección) está
  en `filesDir/datastore`, privado de la app. La política de privacidad lo explica.
- Además de las apps objetivo, el servicio escucha al launcher, pero solo para saber que el
  usuario ha salido a Inicio o a Recientes. De su pantalla no se lee nada.
- Solo se exporta `MainActivity`, porque la necesita el lanzador. Su único extra solo abre
  Inicio (desde la pantalla de bloqueo): no cambia nada. El servicio exige
  `BIND_ACCESSIBILITY_SERVICE`. Las librerías no añaden permisos, y el único receiver
  exportado (`ProfileInstallReceiver` de AndroidX) exige `DUMP`, que solo tiene adb.
- En release: R8 con minify y shrinkResources, y sin logs `v`, `d` ni `i`, para que logcat no
  revele cuándo se usan las apps bloqueadas.
- La APK de release se firma con una clave propia. En local se lee de `keystore.properties` y
  en CI de los secretos de GitHub. La keystore nunca entra al repo (ver README, *Publicar una
  versión*).
- El tiempo de uso se mide con `SystemClock.elapsedRealtime()` y se exige la hora automática,
  para que no se pueda hacer trampa cambiando la hora (ver *Antitrampas*).

## Limitaciones conocidas

- **Solo Android.** iOS no permite este tipo de servicio.
- En Android 13 o superior, si la app se instala fuera de Play Store, el usuario debe activar
  "Permitir ajustes restringidos" antes de poder activar la accesibilidad.
- Google Play exige un aviso destacado (*prominent disclosure*) que explique el uso de la API
  de accesibilidad.
