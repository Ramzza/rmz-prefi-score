package hu.rmz.prefiscore.model

import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject

object JsonCodec {
    val json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
        prettyPrint = true
        explicitNulls = false
    }

    fun encodeGame(game: Game): String = json.encodeToString(Game.serializer(), game)
    fun decodeGame(text: String): Game = json.decodeFromString(Game.serializer(), text)

    fun encodeBundle(games: List<Game>, now: Long): String =
        json.encodeToString(ExportBundle.serializer(), ExportBundle(exportedAt = now, games = games))

    /** Egy játék, játéklista vagy export csomag beolvasása. */
    fun decodeAny(text: String): List<Game> {
        val element = json.parseToJsonElement(text)
        return when {
            element is JsonArray -> json.decodeFromJsonElement(ListSerializer(Game.serializer()), element)
            element is JsonObject && "games" in element -> json.decodeFromJsonElement(ExportBundle.serializer(), element).games
            element is JsonObject && "players" in element -> listOf(json.decodeFromJsonElement(Game.serializer(), element))
            else -> throw IllegalArgumentException("Ismeretlen fájlformátum")
        }
    }
}
