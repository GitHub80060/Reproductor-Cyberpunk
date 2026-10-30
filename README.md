# NEON//DECK

Reproductor de música para Android con estética cyberpunk (amarillo, cian y rojo neón, esquinas cortadas, líneas de escaneo y efecto glitch).

## Qué hace

- Lee la música de **una sola carpeta que tú eliges** (MP3, FLAC, M4A, OGG, WAV…). No pide permiso para ver el resto del teléfono.
- Sigue sonando con la pantalla apagada y con otras apps abiertas, con notificación y controles en la pantalla de bloqueo.
- Reproducción sin cortes entre canciones, aleatorio, repetir lista / repetir una, buscador.
- **Pantalla AUDIO** para que suene mejor:
  - Ecualizador de varias bandas con presets (PLANO, NEON, GRAVES, VOCES, AGUDOS).
  - Refuerzo de graves.
  - Volumen extra.
  - Todo se guarda para la próxima vez.
- Pausa sola si desconectas los audífonos.
- **Acceso limitado (v1.2):** al abrir la app por primera vez te obliga a elegir la carpeta donde está tu música. Solo se guarda permiso de **lectura** sobre esa carpeta (y sus subcarpetas); no hay permisos de almacenamiento ni de internet. Si cambias de carpeta, el permiso de la anterior se suelta. En LISTA, `CAMBIAR CARPETA` abre el selector otra vez y hay botones por subcarpeta y `TODO`.
- **Metadatos seguros:** título, artista y álbum se leen de las etiquetas del MP3 (si no tiene, se usa el nombre del archivo) y se tratan siempre como **texto plano**: se quitan caracteres de control e invisibles, se limita el largo y nunca se interpretan como HTML, rutas ni comandos.
- **Perfil:** sube tu foto de avatar (se recorta cuadrada y se guarda solo dentro de la app, no se sube a internet), escribe un alias y mira cuántas pistas y carpetas tienes. El avatar también aparece arriba a la derecha.
- **Colores:** 15 temas (VOLT, ROSA NEÓN, ÁCIDO, ÁRTICO, SOLAR, VIOLETA, CARMESÍ, ZAFIRO, ESMERALDA, OCÉANO, ATARDECER, FUCSIA, MENTA, ÁMBAR, MONOCROMO) que cambian el fondo y los acentos de toda la app. Se eligen en PERFIL y se recuerdan.

> Las barras animadas de la pantalla DECK son solo de adorno: se mueven mientras suena música, pero no leen el audio real.

## Dónde poner la música

Elige una carpeta (por ejemplo `Música` o `Música/YTDescargador`) la primera vez que abras la app. Android 11 o más nuevo no deja elegir la raíz del almacenamiento ni la carpeta `Download` completa: elige una carpeta dentro. Si agregas música nueva, pulsa el botón de recargar (flechas circulares) en la pestaña LISTA.

## Cómo obtener el APK (igual que la app anterior)

1. En GitHub crea un repositorio nuevo llamado `NeonDeck` (puede ser privado).
2. Sube **todo el contenido** de esta carpeta: `app`, `licencias`, `build.gradle.kts`, `settings.gradle.kts`, `gradle.properties`, `.gitignore` y `README.md`.
3. Pon el archivo de compilación (es el paso que falla al arrastrar, porque `.github` es una carpeta oculta):
   - Si en `.github/workflows/` ya existe `blank.yml` (la plantilla vacía de GitHub, que solo escribe "Hello, world" y NO compila nada), ábrelo, pulsa el lápiz, borra todo y pega el contenido de `workflow-para-copiar.txt`. Pulsa **Commit changes**.
   - Si no existe, usa **Add file → Create new file**, nombre `.github/workflows/build.yml`, pega el mismo contenido y pulsa **Commit changes**.
4. Entra a **Actions → Compilar APK** y, si no arrancó sola, pulsa **Run workflow**. Tarda unos 5 a 10 minutos.
5. Cuando tenga la palomita verde (en el workflow llamado **Compilar APK**, no en "CI"), abre esa ejecución, baja a **Artifacts** y descarga **NeonDeck-apk**. Dentro del zip está `app-debug.apk`.
6. Pásalo al celular e instálalo aceptando "fuentes desconocidas".

Al abrir la app por primera vez te pedirá elegir la carpeta de música y, en Android 13 o más nuevo, permiso para mostrar notificaciones.

## Créditos

- Fuentes Orbitron y Rajdhani, licencia SIL Open Font License (ver carpeta `licencias`).
- Motor de reproducción: AndroidX Media3 (ExoPlayer).
- El diseño está inspirado en el estilo visual del género cyberpunk. No usa logos ni nombres del juego.
