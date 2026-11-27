package hu.rmz.prefiscore.data

import hu.rmz.prefiscore.model.Game
import hu.rmz.prefiscore.model.JsonCodec
import java.io.File
import java.util.concurrent.Executors

/** Játékonként egy JSON fájl az alkalmazás belső tárhelyén. */
class GameRepository(baseDir: File) {
    private val dir = File(baseDir, "games").apply { mkdirs() }
    private val writer = Executors.newSingleThreadExecutor()

    fun loadAll(): List<Game> = dir.listFiles { f -> f.extension == "json" }.orEmpty().mapNotNull { f ->
        runCatching { JsonCodec.decodeGame(f.readText()) }.getOrNull()
    }

    fun save(game: Game) {
        val text = JsonCodec.encodeGame(game)
        writer.execute {
            val tmp = File(dir, "${game.id}.json.tmp")
            tmp.writeText(text)
            tmp.renameTo(File(dir, "${game.id}.json"))
        }
    }

    fun delete(id: String) {
        writer.execute { File(dir, "$id.json").delete() }
    }
}
