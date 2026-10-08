# TouchGrass 🌱

Una app Android para usar Instagram sin caer en el *doomscrolling*.
Más adelante también TikTok y YouTube.

TouchGrass **no sustituye** a la app oficial. Funciona *por encima* de ella mediante un
servicio de accesibilidad que detecta las zonas adictivas (Reels, Explorar, scroll infinito)
y las bloquea o les pone un límite de tiempo diario. Así puedes seguir usando los mensajes,
las publicaciones de tus amigos y las historias.

> Estado: **en reescritura**. La versión antigua basada en WebView está archivada en el tag
> [`legacy-v0`](../../tree/legacy-v0).

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
```

## Flujo de trabajo

Trabajamos por **bloques**. Cada bloque vive en una rama `bloque-N/<nombre>`, se revisa
y se integra en `main` con `git merge --no-ff`. Cada integración lleva un tag de versión.
El plan de bloques está en [docs/ROADMAP.md](docs/ROADMAP.md).

Commits en formato [Conventional Commits](https://www.conventionalcommits.org/es/):
`feat:`, `fix:`, `chore:`, `docs:`, `refactor:`, `test:`, `ci:`.

Cada push y PR a `main` pasa por CI (GitHub Actions): ktlint, lint, tests y build.

## Documentación

- [Arquitectura](docs/ARCHITECTURE.md)
- [Roadmap por bloques](docs/ROADMAP.md)
- [Decisiones de arquitectura (ADR)](docs/adr/)
