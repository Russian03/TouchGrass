# Roadmap por bloques

Cada bloque es una rama `bloque-N/<nombre>` que se integra en `main` tras revisión.

| Bloque | Rama                          | Contenido                                                                 | Tag    |
|--------|-------------------------------|---------------------------------------------------------------------------|--------|
| 0      | `bloque-0/limpieza`           | Archivar el código antiguo, higiene del repo, documentación               | v0.1.0 |
| 1      | `bloque-1/esqueleto`          | Gradle moderno, Compose, Hilt, DataStore, package nuevo, CI, R8           | v0.2.0 |
| 2      | `bloque-2/motor-accesibilidad`| RuleEngine + tests, AccessibilityService, bloquear Reels, pantalla debug  | v0.3.0 |
| 3      | `bloque-3/limites-y-overlay`  | Presupuesto diario antitrampas, overlay "Touch grass", más bloqueos       | v0.4.0 |
| 4      | `bloque-4/presupuesto-datos`  | Saldo diario (8:00→8:00), límite mensual, DataStore multiproceso, tests   | v0.5.0 |
| 5      | `bloque-5/arranque-primeros-pasos` | Pantalla de carga, primeros pasos con aviso destacado y límite, ajustes | v0.6.0 |
| 6      | `bloque-6/desbloqueo-temporal`| Pedir tiempo (temporizador), desbloqueo en el servicio, caducidad         | v0.7.0 |
| 7      | `bloque-7/endurecimiento`     | Revisión de seguridad, build release firmado, Release automática en CI    | v1.0.0 |
| 8      | `bloque-8/textos-y-pantalla-bloqueo` | Textos genéricos («scroll»), enlace discreto para pedir tiempo     | —      |
| 9      | `bloque-9/pedir-tiempo-por-app` | Pantalla para pedir tiempo: elegir app y minutos, saldo compartido      | —      |
| 10     | `bloque-10/tiktok`            | Reglas para TikTok (Para ti y Comunidad)                                  | v1.1.0 |
| 11     | `bloque-11/estadisticas`      | Estadísticas: motivo al pedir tiempo, resumen diario y mensual con aviso  | v1.2.0 |
| 12     | `bloque-12/diseno-v1`         | Primer diseño nuevo: tema lima, Montserrat, Material 3 Expressive; primeros pasos e Inicio | —      |
| 13     | `bloque-13/logo-y-semana`     | Logo y pantalla de carga animada, saludo con nombre, «Mi semana» con caras | —      |
| 14     | `bloque-14/logo-final`        | Logo definitivo y pantalla de carga con círculos que lo rellenan | —      |
| 15     | `bloque-15/reclamar-dia`      | Primer acceso del día: las caras de los días que faltaban, días sin protección | —      |
| 16     | `bloque-16/primeros-pasos`    | Primeros pasos nuevos, consentimiento de accesibilidad y política de privacidad | —      |
| 17     | `bloque-17/ajustes`           | Ajustes rediseñados, aviso de protección en «Mi día», los cortes del servicio no cuentan como trampa | —      |
| 18     | `bloque-18/cara-sin-proteccion` | En «Mi semana», los días sin protección con los ojos en cruz y en rojo, y los de antes de empezar en gris con un guion | —      |
| 19     | `bloque-19/estadisticas`      | Estadísticas rediseñadas (calendario de caras, rachas, recuadros), «Mis logros» en Inicio; fuera el aviso del mes | —      |
| 20     | `bloque-20/solo-vertical`     | La app se queda en vertical aunque el móvil tenga la rotación automática | —      |
| 21     | `bloque-21/bloqueo-y-pedir-tiempo` | Pantalla de bloqueo rediseñada (lima, logo en silueta que niega); pendiente: la de pedir tiempo | —      |
| —      | después                       | PIN opcional para pedir tiempo                                            | —      |
| —      | después                       | Bloquear los botones de volumen durante la pantalla de bloqueo            | —      |
| —      | después                       | Reglas para YouTube                                                       | —      |
| —      | después                       | Diseño: revisar el aspecto visual de la app                               | —      |

## Publicar en Google Play

Lo que ya está en la app:

- Aviso destacado del servicio de accesibilidad justo antes de pedir activarlo, con «Acepto» y
  «No, gracias». Cualquier «Activar» de la app vuelve a enseñarlo si no se ha aceptado.
- Política de privacidad dentro de la app (Ajustes y el aviso); el texto está en
  [`PRIVACIDAD.md`](PRIVACIDAD.md).
- Sin permisos en tiempo de ejecución: solo el servicio de accesibilidad.
- `isAccessibilityTool="false"` y sin permiso de Internet.

Lo que se hace en Play Console:

- [ ] Publicar la política en una URL pública (p. ej. GitHub Pages) y ponerla en la ficha.
- [ ] Declaración de la API de accesibilidad, con un vídeo corto del aviso, el consentimiento y el
  bloqueo funcionando.
- [ ] Seguridad de los datos: no se recogen ni se comparten datos (todo se queda en el móvil).
- [ ] Clasificación de contenido y público objetivo (13 años o más).
- [ ] Acceso a la app para la revisión: no hay cuenta; explicar cómo activar la protección.
- [ ] Nivel de API objetivo el que pida Play en ese momento.
