package com.sonaris.app.data

const val FOR_YOU_ID = "foryou"

/** query == null -> kiosco "Tendencias musicales" de YouTube. */
data class ShelfSpec(val id: String, val title: String, val query: String?)

val HOME_SHELVES = listOf(
    ShelfSpec("trending", "Tendencias musicales", null),
    ShelfSpec("pop", "Pop para ti", "pop hits"),
    ShelfSpec("reggaeton", "Reggaetón", "reggaeton hits"),
    ShelfSpec("rock", "Rock", "rock hits"),
    ShelfSpec("hiphop", "Hip hop y rap", "hip hop rap hits"),
    ShelfSpec("electro", "Electrónica", "electronic dance hits"),
    ShelfSpec("lofi", "Para relajarte", "lofi chill beats"),
    ShelfSpec("romantic", "Baladas románticas", "baladas románticas"),
    ShelfSpec("90s", "Clásicos de los 90", "90s hits")
)

/** Chips de géneros: etiqueta -> consulta de búsqueda. */
val GENRE_CHIPS = listOf(
    "Pop" to "pop hits",
    "Reggaetón" to "reggaeton",
    "Rock" to "rock",
    "Hip hop" to "hip hop",
    "Electrónica" to "electronic music",
    "Lo-fi" to "lofi",
    "Baladas" to "baladas",
    "Latino" to "música latina"
)
