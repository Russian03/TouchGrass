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

El build de debug incluye una pantalla que vuelca los viewIds visibles, para descubrir los
nuevos cuando una actualización de Instagram rompa una regla.

## Saldo diario y desbloqueos

Cada mes el usuario tiene un **límite diario** de minutos que puede desbloquear. Las reglas están
en `core/budget/BudgetPolicy` (Kotlin puro) y el estado se guarda con `data/budget/BudgetRepository`.

- El día va de **8:00 a 8:00** (hora local). Cada día empieza con el límite completo y lo que no se
  gasta se pierde.
- Un desbloqueo se pide para una app y unos minutos, que se **restan enteros al pedirlo**. Dura
  esos minutos de reloj, aunque se salga de la app. Solo puede haber uno a la vez y el saldo es
  el mismo para todas las apps. La pantalla de pedir tiempo (`ui/unlock`) lista las apps con
  reglas usando el `name` y `blocks` de cada fichero; desde la pantalla de bloqueo se abre con
  la app ya elegida (extra `EXTRA_UNLOCK_PACKAGE` de `MainActivity`, que solo marca la app).
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

## Privacidad y seguridad

- No se pide permiso `INTERNET`.
- El servicio de accesibilidad se declara con `packageNames` restringido a las apps objetivo.
- Los snapshots descartan el texto del contenido (mensajes, captions) y solo guardan
  identificadores estructurales y, de las vistas que nombran las reglas, los textos que buscan
  (las pestañas de TikTok).
- `allowBackup=false` y las reglas de extracción excluyen todos los datos.
- Además de las apps objetivo, el servicio escucha al launcher, pero solo para saber que el
  usuario ha salido a Inicio o a Recientes. De su pantalla no se lee nada.
- Solo se exporta `MainActivity`, porque la necesita el lanzador. El servicio exige
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
