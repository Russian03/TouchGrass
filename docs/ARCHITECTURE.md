# Arquitectura

## Visión general

```
 Instagram (app oficial)
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
| `core`     | Modelo de reglas, `RuleEngine`, cálculo de presupuesto diario          | No                 |
| `data`     | DataStore, carga de reglas desde `assets/rules/*.json`                 | Sí                 |
| `service`  | `AccessibilityService`: traduce eventos a snapshots y ejecuta acciones | Sí                 |
| `overlay`  | Overlay de bloqueo (`TYPE_ACCESSIBILITY_OVERLAY`)                      | Sí                 |
| `ui`       | Compose + ViewModels (MVVM), inyección con Hilt                        | Sí                 |

La lógica de decisión está en `core` y no depende de Android, así que se testea con JUnit
sin emulador.

## Reglas de detección

Instagram cambia sus identificadores internos con las actualizaciones, así que las reglas no
están escritas en el código: van en ficheros JSON versionados (`assets/rules/instagram.json`).
Cada regla indica:

- la **app objetivo** (`com.instagram.android`),
- un **matcher** (viewId, contentDescription o combinación),
- una **acción** (`BLOCK`, `LIMIT`, `WARN`).

El build de debug incluye una pantalla que vuelca los viewIds visibles, para descubrir los
nuevos cuando una actualización de Instagram rompa una regla.

## Privacidad y seguridad

- No se pide permiso `INTERNET`.
- El servicio de accesibilidad se declara con `packageNames` restringido a las apps objetivo.
- Los snapshots descartan el texto del contenido (mensajes, captions) y solo guardan
  identificadores estructurales.
- `allowBackup=false` y las reglas de extracción excluyen todos los datos.
- En release: R8 con minify y shrinkResources. La keystore nunca entra al repo.
- El tiempo de uso se mide con `SystemClock.elapsedRealtime()` y se detecta
  `ACTION_TIME_CHANGED`, para que no se pueda hacer trampa cambiando la hora.

## Limitaciones conocidas

- **Solo Android.** iOS no permite este tipo de servicio.
- En Android 13 o superior, si la app se instala fuera de Play Store, el usuario debe activar
  "Permitir ajustes restringidos" antes de poder activar la accesibilidad.
- Google Play exige un aviso destacado (*prominent disclosure*) que explique el uso de la API
  de accesibilidad.
