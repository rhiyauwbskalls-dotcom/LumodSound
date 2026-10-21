package com.lumodsound.app.data

data class ArtistUi(
    val url: String,
    val name: String,
    val thumb: String?,
    val subscribers: Long
)

/** Álbum, sencillo o EP. trackCount se conoce cuando ya se pidió su lista de canciones. */
data class AlbumUi(
    val url: String,
    val name: String,
    val artist: String,
    val artistUrl: String?,
    val thumb: String?,
    val trackCount: Int? = null,
    /** Línea de texto lista para mostrar bajo el nombre (p. ej. en el Inicio: "Álbum • Artista • 2024"). */
    val subtitle: String? = null
)

data class ArtistPage(
    val artist: ArtistUi,
    val hero: String?,
    val description: String?,
    val subscribers: Long,
    val popular: List<SongUi>,
    val releases: List<AlbumUi>,
    val popularFailed: Boolean = false,
    val releasesFailed: Boolean = false,
    /** Si vienen, ya están separados (datos reales de YouTube Music) y no hay que clasificarlos. */
    val albums: List<AlbumUi>? = null,
    val singles: List<AlbumUi>? = null
)

/** Datos de una canción para la pantalla de información. */
data class SongInfo(
    val title: String,
    val artist: String,
    val album: AlbumUi?,
    val albumName: String?,
    val year: String?,
    val durationSec: Long,
    val audioFormat: String?,
    val audioCodec: String?,
    val bitrateKbps: Int,
    val channel: String?,
    val views: Long,
    val videoId: String,
    val url: String
)
