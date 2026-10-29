# NEON//DECK

Reproductor de música para Android con estética cyberpunk (amarillo, cian y rojo neón, esquinas cortadas, líneas de escaneo y efecto glitch).

## Qué hace

- Lee la música que tengas en el teléfono (MP3, FLAC, M4A, OGG, WAV…).
- Sigue sonando con la pantalla apagada y con otras apps abiertas, con notificación y controles en la pantalla de bloqueo.
- Reproducción sin cortes entre canciones, aleatorio, repetir lista / repetir una, buscador.
- **Pantalla AUDIO** para que suene mejor:
  - Ecualizador de varias bandas con presets (PLANO, NEON, GRAVES, VOCES, AGUDOS).
  - Refuerzo de graves.
  - Volumen extra.
  - Todo se guarda para la próxima vez.
- Pausa sola si desconectas los audífonos.
- **Elegir carpeta:** en LISTA, el botón `ELEGIR CARPETA` abre el selector de Android; la app muestra solo las canciones de esa carpeta (y sus subcarpetas). También hay botones rápidos por cada carpeta con música y `TODO` para volver a verlo todo. Se recuerda la última elección.
- **Perfil:** sube tu foto de avatar (se recorta cuadrada y se guarda solo dentro de la app, no se sube a internet), escribe un alias y mira cuántas pistas y carpetas tienes. El avatar también aparece arriba a la derecha.
- **Colores:** 6 temas neón (VOLT, ROSA NEÓN, ÁCIDO, ÁRTICO, SOLAR, VIOLETA) que cambian toda la app. Se eligen en PERFIL y se recuerdan.

> Las barras animadas de la pantalla DECK son solo de adorno: se mueven mientras suena música, pero no leen el audio real.

## Dónde poner la música y elegir carpeta

La app muestra los archivos de audio de tu teléfono. En la pestaña LISTA aparecen botones con cada carpeta que tenga música (por ejemplo `YTDESCARGADOR (12)`): pulsa uno para ver y reproducir solo esa carpeta, o `TODO` para ver todo. La carpeta elegida se recuerda. Si descargaste MP3 con la app YT Descargador, quedan en `Música/YTDescargador` y aparecen solos. Si agregas música nueva, pulsa el botón de recargar (flechas circulares) en la pestaña LISTA.

## Cómo obtener el APK (igual que la app anterior)

1. En GitHub crea un repositorio nuevo llamado `NeonDeck` (puede ser privado).
2. Sube **todo el contenido** de esta carpeta: `app`, `licencias`, `build.gradle.kts`, `settings.gradle.kts`, `gradle.properties`, `.gitignore` y `README.md`.
3. Pon el archivo de compilación (es el paso que falla al arrastrar, porque `.github` es una carpeta oculta):
   - Si en `.github/workflows/` ya existe `blank.yml` (la plantilla vacía de GitHub, que solo escribe "Hello, world" y NO compila nada), ábrelo, pulsa el lápiz, borra todo y pega el contenido de `workflow-para-copiar.txt`. Pulsa **Commit changes**.
   - Si no existe, usa **Add file → Create new file**, nombre `.github/workflows/build.yml`, pega el mismo contenido y pulsa **Commit changes**.
4. Entra a **Actions → Compilar APK** y, si no arrancó sola, pulsa **Run workflow**. Tarda unos 5 a 10 minutos.
5. Cuando tenga la palomita verde (en el workflow llamado **Compilar APK**, no en "CI"), abre esa ejecución, baja a **Artifacts** y descarga **NeonDeck-apk**. Dentro del zip está `app-debug.apk`.
6. Pásalo al celular e instálalo aceptando "fuentes desconocidas".

Al abrir la app por primera vez te pedirá permiso para leer tu música y, en Android 13 o más nuevo, para mostrar notificaciones.

## Créditos

- Fuentes Orbitron y Rajdhani, licencia SIL Open Font License (ver carpeta `licencias`).
- Motor de reproducción: AndroidX Media3 (ExoPlayer).
- El diseño está inspirado en el estilo visual del género cyberpunk. No usa logos ni nombres del juego.
