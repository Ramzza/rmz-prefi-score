package hu.rmz.prefiscore.data

import com.anthropic.client.okhttp.AnthropicOkHttpClient
import com.anthropic.core.JsonValue
import com.anthropic.errors.AnthropicIoException
import com.anthropic.errors.AnthropicServiceException
import com.anthropic.models.messages.Base64ImageSource
import com.anthropic.models.messages.ContentBlockParam
import com.anthropic.models.messages.ImageBlockParam
import com.anthropic.models.messages.JsonOutputFormat
import com.anthropic.models.messages.MessageCreateParams
import com.anthropic.models.messages.OutputConfig
import com.anthropic.models.messages.StopReason
import hu.rmz.prefiscore.model.JsonCodec
import hu.rmz.prefiscore.model.SheetScan
import java.time.Duration
import java.util.Base64

/** Kép → [SheetScan] átalakító. */
fun interface SheetScanner {
    fun scan(jpeg: ByteArray): SheetScan
}

class ScanException(message: String, cause: Throwable? = null) : Exception(message, cause)

/** Kézzel írt Prefi pontozólap felismerése a Claude API-val (vision + strukturált JSON kimenet). */
class ClaudeSheetScanner(
    private val apiKey: String,
    private val baseUrl: String? = null,
) : SheetScanner {

    override fun scan(jpeg: ByteArray): SheetScan {
        val client = AnthropicOkHttpClient.builder()
            .apiKey(apiKey)
            .timeout(Duration.ofMinutes(5))
            .apply { baseUrl?.let { baseUrl(it) } }
            .build()
        try {
            val response = client.messages().create(buildParams(jpeg))
            val stop = response.stopReason().orElse(null)
            if (stop == StopReason.REFUSAL) throw ScanException("A modell nem dolgozta fel a képet. Próbálj másik fotót.")
            if (stop == StopReason.MAX_TOKENS) throw ScanException("A válasz túl hosszú lett és megszakadt. Próbáld újra.")
            val text = response.content().mapNotNull { it.text().orElse(null)?.text() }.joinToString("")
            if (text.isBlank()) throw ScanException("Üres választ kaptunk. Próbáld újra.")
            return try {
                JsonCodec.json.decodeFromString(SheetScan.serializer(), text)
            } catch (e: Exception) {
                throw ScanException("A felismert adatok nem értelmezhetők.", e)
            }
        } catch (e: AnthropicServiceException) {
            throw ScanException(
                when (e.statusCode()) {
                    401 -> "Érvénytelen API kulcs. Ellenőrizd a Beállításokban."
                    403 -> "Az API kulcshoz nincs jogosultság ehhez a modellhez."
                    413 -> "A kép túl nagy."
                    429 -> "Túl sok kérés vagy elfogyott a keret. Próbáld később."
                    in 500..599 -> "A Claude API átmenetileg nem elérhető (${e.statusCode()}). Próbáld később."
                    else -> "API hiba (${e.statusCode()}): ${e.message}"
                },
                e,
            )
        } catch (e: AnthropicIoException) {
            throw ScanException("Hálózati hiba. Ellenőrizd az internetkapcsolatot.", e)
        } finally {
            client.close()
        }
    }

    fun buildParams(jpeg: ByteArray): MessageCreateParams {
        val image = ImageBlockParam.builder()
            .source(
                Base64ImageSource.builder()
                    .data(Base64.getEncoder().encodeToString(jpeg))
                    .mediaType(Base64ImageSource.MediaType.IMAGE_JPEG)
                    .build(),
            )
            .build()
        return MessageCreateParams.builder()
            .model(MODEL)
            .maxTokens(16000L)
            .system(SYSTEM_PROMPT)
            .outputConfig(
                OutputConfig.builder()
                    .effort(OutputConfig.Effort.HIGH)
                    .format(JsonOutputFormat.builder().schema(schema()).build())
                    .build(),
            )
            .addUserMessageOfContentBlockParams(
                listOf(
                    ContentBlockParam.ofImage(image),
                    ContentBlockParam.ofText("Írd át ezt a Prefi pontozólapot a megadott JSON formátumba."),
                ),
            )
            // Szerveroldali tartalék modell, ha a biztonsági szűrő elutasítaná a kérést.
            .putAdditionalHeader("anthropic-beta", "server-side-fallback-2026-07-01")
            .putAdditionalBodyProperty("fallbacks", JsonValue.from("default"))
            .build()
    }

    companion object {
        const val MODEL = "claude-opus-5-5"

        private fun obj(properties: Map<String, Any>): Map<String, Any> = mapOf(
            "type" to "object",
            "properties" to properties,
            "required" to properties.keys.toList(),
            "additionalProperties" to false,
        )

        private val intArray = mapOf("type" to "array", "items" to mapOf("type" to "integer"))

        private val SCHEMA: Map<String, Any> = obj(
            mapOf(
                "players" to mapOf(
                    "type" to "array",
                    "items" to obj(
                        mapOf(
                            "name" to mapOf("type" to "string"),
                            "upperValues" to intArray,
                            "rooms" to mapOf(
                                "type" to "array",
                                "items" to obj(mapOf("target" to mapOf("type" to "string"), "values" to intArray)),
                            ),
                        ),
                    ),
                ),
                "druzsTotal" to mapOf("type" to "integer"),
                "druzsCrossed" to mapOf("type" to "integer"),
                "confidence" to mapOf("type" to "string", "enum" to listOf("high", "medium", "low")),
                "notes" to mapOf("type" to "string"),
            ),
        )

        fun schema(): JsonOutputFormat.Schema {
            val b = JsonOutputFormat.Schema.builder()
            SCHEMA.forEach { (k, v) -> b.putAdditionalProperty(k, JsonValue.from(v)) }
            return b.build()
        }

        val SYSTEM_PROMPT = """
            You transcribe photographed, handwritten score sheets of the Hungarian card game Prefi (a Preferans variant) into JSON.

            Sheet layout:
            - There is one block per player, top to bottom. The player's name is on the left, followed by a vertical bar and a comma-separated, chronological list of the player's "fent" (upper) score values. The last value is the current one. Earlier values may be struck through or carry small ticks or carets; still transcribe every value in order.
            - Below that row, under a horizontal line, is the player's "pince" (cellar). It is split by vertical lines into rooms, one per other player. Each room is labelled with that player's name and a colon, followed by a chronological list of cumulative values that may wrap onto the next line (the last value is the current one). A room without numbers has an empty list.
            - The sheet may contain a row of small circles: these are "druzs" marks. Count all circles (druzsTotal) and how many of them are crossed or slashed through (druzsCrossed). If there are no circles, both are 0.

            Rules:
            - Transcribe exactly what is written. Never compute, correct, or invent values. All numbers are integers; a leading minus sign means a negative number.
            - When a digit has been overwritten, use the final (overwriting) digit.
            - Keep player names as written, capitalized, with Hungarian accents. A room's target must use the same spelling as that player's own block name, even if the room label is abbreviated or spelled differently.
            - List players in the order their blocks appear, and rooms in the order they appear within each cellar.
            - confidence: how legible the sheet was overall.
            - notes: in Hungarian, briefly list anything uncertain (ambiguous digits, unclear marks, guessed names). Use an empty string if nothing is uncertain.
        """.trimIndent()
    }
}
