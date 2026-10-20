# Sonaris

🌐 [English](README.md) · **Español**

Un **cliente de YouTube Music para Android**, no oficial y de código abierto, hecho con Kotlin, Jetpack Compose (Material 3) y Media3.
Se centra en una apariencia limpia y moderna (tema oscuro por defecto, fondos con el color de la portada, navegación flotante), letras sincronizadas y algunos
extras de comodidad: Discord Rich Presence, scrobbling en Last.fm, traducción de letras y una cola inteligente.

> **Estado:** en fase temprana y con desarrollo activo. Es posible que algunas cosas fallen. Los issues y pull requests son bienvenidos.

---

## ⚠️ Aviso legal

- Sonaris **no está afiliado, respaldado ni patrocinado** por YouTube, Google, Spotify, Discord, Last.fm ni ningún otro
  servicio mencionado aquí. Todos los nombres y marcas pertenecen a sus respectivos dueños.
- La música y los metadatos provienen de YouTube Music a través de [NewPipeExtractor](https://github.com/TeamNewPipe/NewPipeExtractor),
  **no** mediante una API oficial. Esto podría ir en contra de los Términos de Servicio de YouTube y puede dejar de funcionar en cualquier momento.
- Sonaris no aloja, almacena ni distribuye contenido protegido por derechos de autor. Las descargas y la caché de audio están pensadas únicamente
  para uso personal y sin conexión. **Tú eres responsable** de cumplir la ley y los términos de los servicios que uses.
- El software se ofrece «tal cual», sin garantía de ningún tipo. Úsalo bajo tu propio riesgo.

## ⚠️ Funciones relacionadas con Spotify — léelo antes de activarlas

Sonaris puede usar **opcionalmente** las letras sincronizadas de Spotify y el **Canvas** de Spotify (los videos cortos en bucle) en el reproductor.

**Esta no es una integración oficial con Spotify.** Funciona a través de la API web *no oficial y no documentada* del reproductor web de Spotify,
autenticada con tu propia cookie de sesión `sp_dc`. Ten en cuenta lo siguiente:

- Es muy probable que vaya en contra de los **Términos de Uso de Spotify** (clientes no oficiales y acceso automatizado). Usarlo podría provocar
  límites de peticiones o restricciones en tu cuenta de Spotify. **Lo usas completamente bajo tu propio riesgo**, y los mantenedores y
  colaboradores no se hacen responsables de ninguna consecuencia.
- Está **desactivado por defecto**. No se envía nada a Spotify a menos que inicies sesión tú mismo en *Ajustes → Spotify*.
- Los endpoints son internos y cambian sin previo aviso, así que estas funciones pueden dejar de funcionar en cualquier momento.
- Para solicitar un token de acceso, la app necesita secretos que usa el reproductor web de Spotify. Algunos vienen incluidos en el código, y una
  lista actualizada se descarga de un repositorio de GitHub mantenido por la comunidad
  ([`xyloflake/spot-secrets-go`](https://github.com/xyloflake/spot-secrets-go)). Esa lista es de terceros y su
  disponibilidad no está garantizada.
- Si prefieres no usar nada de esto, simplemente no inicies sesión en Spotify dentro de la app. Todo lo demás sigue funcionando
  (las letras recurren a [LRCLIB](https://lrclib.net)).
- Si representas a Spotify o a un titular de derechos y tienes alguna inquietud sobre esta función, abre un issue y la tomaremos
  en serio.

## Características

**Exploración y reproducción**
- Pantalla de inicio con recomendaciones de YouTube Music, chips de ánimo/género y una cuadrícula de acceso rápido con tus canciones recientes
- Búsqueda de canciones y álbumes; páginas de artista y de álbum
- Reproducción en segundo plano con notificación multimedia y controles en la pantalla de bloqueo (Media3)
- Cola inteligente con radio infinita, radio de artista, reordenamiento por arrastre e historial
- Modo aleatorio, repetición, temporizador de sueño y crossfade

**Biblioteca y modo sin conexión**
- Favoritos y listas de reproducción guardados en tu dispositivo
- Caché de audio (de tamaño configurable) para que una canción repetida empiece al instante
- Descargas permanentes, además de una tarjeta «Caché» en la biblioteca con las canciones descargadas por completo
- Opcional: enviar tu historial de escucha a tu cuenta de YouTube Music para mejorar las recomendaciones (desactivado por defecto)

**Letras**
- Letras sincronizadas con el tiempo (LRCLIB), con toque para saltar a esa parte, mostradas a pantalla completa
- Traducción de letras, ya sea en el dispositivo (ML Kit) o con la clave de API de tu propio proveedor de IA
  (Claude, ChatGPT, Gemini, OpenRouter, Pollinations o un endpoint personalizado)

**Integraciones (todas opcionales)**
- Discord Rich Presence
- Scrobbling en Last.fm
- Letras sincronizadas y Canvas de Spotify — consulta la advertencia anterior

**Apariencia**
- Tema oscuro por defecto con un único color de acento (fácil de cambiar en `ui/Theme.kt`) y un modo Material You opcional
- Pantallas del reproductor, la cola y las letras teñidas con el color de la portada
- Mini reproductor y barra de navegación flotantes que se ocultan al desplazarse

## Capturas de pantalla

_Próximamente. ¡Se agradecen contribuciones con capturas de pantalla!_

## Requisitos

- Android 8.1 (API 27) o superior
- Para compilar: Android Studio Koala o superior, JDK 17

## Compilación

1. Clona el repositorio y abre la carpeta en Android Studio.
2. Deja que Gradle se sincronice (Android Studio descarga por sí mismo los archivos faltantes del wrapper de Gradle).
3. Ejecuta la configuración `app` en un dispositivo o emulador.

**Last.fm (opcional):** las claves de Last.fm no están en el código. Crea una cuenta de API gratis en
<https://www.last.fm/api/account/create>, copia `lastfm.properties.example` a `app/src/main/assets/lastfm.properties` y rellena
`LASTFM_API_KEY` y `LASTFM_SECRET`. Sin ellas la app funciona igual, pero el scrobbling de Last.fm no estará disponible.
Para la compilación con GitHub Actions, añade esos mismos dos nombres como secretos del repositorio (*Settings → Secrets and variables → Actions*).

**¿Sin computadora?** Sube el proyecto a GitHub. El workflow de `.github/workflows/build.yml` compila un APK de depuración:
*Actions → Build APK → Artifacts*.

Si la reproducción o la exploración dejan de funcionar de repente, probablemente YouTube cambió algo. Sube la versión de
`com.github.TeamNewPipe:NewPipeExtractor` en `app/build.gradle.kts` y vuelve a intentarlo.

## Privacidad y seguridad

- Sonaris **no incluye librerías de analíticas, anuncios ni reporte de fallos**, y no tiene servidor propio.
- La app se comunica directamente con los servicios que usas: YouTube/Google, LRCLIB y (solo si los activas) Last.fm, Discord,
  Spotify y el proveedor de IA que configures. La traducción en el dispositivo descarga modelos de idioma la primera vez que la usas.
- Las credenciales de Discord y Spotify se cifran en reposo con AES-256-GCM usando una clave guardada en el Android Keystore.
- Las cookies de tu sesión de YouTube Music se guardan en el almacenamiento privado de la app **sin cifrado adicional**, así que alguien con
  acceso root a tu teléfono podría leerlas. Cerrar sesión las elimina.
- Nunca compartas tus cookies, tokens o claves de API en issues ni capturas de pantalla.

## Consejos de batería

Las letras, las pantallas animadas y la reproducción en segundo plano consumen algo de batería. Si notas un consumo alto, abre
*Ajustes → Batería* dentro de la app para ir a los ajustes de batería de Android para Sonaris, y evita dejar la pantalla de letras
abierta durante periodos largos.

## Estado del proyecto y hoja de ruta

Se agradece ayuda con cualquiera de estas tareas:

- [ ] Añadir capturas de pantalla a este README
- [ ] Rediseñar las pantallas de Biblioteca y Búsqueda para que coincidan con el resto de la app
- [ ] Mover las cadenas de la interfaz en español escritas directamente en el código a `strings.xml` y añadir traducciones
- [ ] Firma de versiones y releases etiquetados
- [ ] Pruebas

## Cómo contribuir

1. Abre primero un issue para cambios grandes, así podemos conversarlo.
2. Haz un fork del repositorio, crea una rama y mantén cada pull request centrado en una sola cosa.
3. Asegúrate de que la app compila (`gradle assembleDebug`) antes de abrir el pull request.
4. No subas claves personales, cookies ni tokens.

## Licencia

Sonaris se publica bajo la **Licencia Pública General de GNU v3.0** (consulta el archivo `LICENSE`), lo cual es obligatorio porque
se basa en NewPipeExtractor (GPL-3.0).

## Agradecimientos

- [NewPipeExtractor](https://github.com/TeamNewPipe/NewPipeExtractor) — extracción de datos de YouTube
- [LRCLIB](https://lrclib.net) — letras sincronizadas
- [AndroidX Media3](https://developer.android.com/media/media3), [Jetpack Compose](https://developer.android.com/jetpack/compose),
  [Coil](https://coil-kt.github.io/coil/), [OkHttp](https://square.github.io/okhttp/), [ML Kit](https://developers.google.com/ml-kit)
- Inspirado en otros clientes de música de código abierto como Metrolist y SimpMusic
