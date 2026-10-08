# ADR 0001: Servicio de accesibilidad sobre la app nativa en lugar de WebView

- Estado: aceptada
- Fecha: 2026-10-08

## Contexto

La primera versión (tag `legacy-v0`) cargaba instagram.com en un WebView y controlaba Reels
y DMs leyendo URLs. Tenía estos problemas:

- La web de Instagram ofrece peor experiencia que la app oficial (sin notificaciones y con
  funciones recortadas).
- Bastaba con abrir la app oficial para saltarse TouchGrass.
- Cualquier cambio en el HTML o en las rutas de la web rompía la lógica.

## Decisión

Usar un `AccessibilityService` restringido a `com.instagram.android`. El servicio detecta
pantallas y elementos por viewId o contentDescription y reacciona con acciones globales
(atrás) o con un overlay de accesibilidad.

## Consecuencias

- ✅ El usuario sigue usando la app oficial, y TouchGrass actúa precisamente donde ocurre el
  doomscrolling.
- ✅ No hace falta red ni login dentro de TouchGrass, lo que mejora mucho la privacidad.
- ⚠️ Hay que mantener las reglas de detección cuando Instagram se actualice. Por eso las
  reglas van en JSON y existe una pantalla de debug.
- ⚠️ Requiere un aviso destacado para Google Play y, en sideload, activar "ajustes
  restringidos" en Android 13 o superior.
- ❌ No es viable en iOS.
