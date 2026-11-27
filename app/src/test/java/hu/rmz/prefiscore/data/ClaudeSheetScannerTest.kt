package hu.rmz.prefiscore.data

import hu.rmz.prefiscore.model.JsonCodec
import hu.rmz.prefiscore.model.SheetFixtures
import hu.rmz.prefiscore.model.SheetScan
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Before
import org.junit.Test

/** A Claude API hívás teljes útja (SDK kérésépítés, HTTP, válasz-feldolgozás) egy helyi mock szerverrel. */
class ClaudeSheetScannerTest {
    private lateinit var server: MockWebServer

    @Before fun setUp() {
        server = MockWebServer().apply { start() }
    }

    @After fun tearDown() = server.shutdown()

    private fun messageJson(text: String, stopReason: String = "end_turn"): String = buildJsonObject {
        put("id", "msg_test")
        put("type", "message")
        put("role", "assistant")
        put("model", "claude-opus-5-5")
        put("content", buildJsonArray { add(buildJsonObject { put("type", "text"); put("text", text) }) })
        put("stop_reason", stopReason)
        put("stop_sequence", null as String?)
        put("usage", buildJsonObject { put("input_tokens", 1500); put("output_tokens", 400) })
    }.toString()

    private fun scanner() = ClaudeSheetScanner("sk-ant-api03-test", server.url("/").toString().trimEnd('/'))

    @Test fun parsesStructuredResponseAndSendsExpectedRequest() {
        val payload = JsonCodec.json.encodeToString(SheetScan.serializer(), SheetFixtures.photo)
        server.enqueue(MockResponse().setHeader("Content-Type", "application/json").setBody(messageJson(payload)))

        val jpeg = byteArrayOf(-1, -40, -1, -32, 1, 2, 3)
        val result = scanner().scan(jpeg)
        assertEquals(SheetFixtures.photo, result)

        val req = server.takeRequest()
        assertEquals("/v1/messages", req.path)
        val auth = req.getHeader("x-api-key") ?: req.getHeader("Authorization").orEmpty()
        assertTrue("API kulcs a kérésben", auth.endsWith("sk-ant-api03-test"))
        assertTrue(req.getHeader("anthropic-beta")!!.contains("server-side-fallback-2026-07-01"))
        val body = JsonCodec.json.parseToJsonElement(req.body.readUtf8()).jsonObject
        assertEquals("claude-opus-5-5", body["model"]!!.jsonPrimitive.content)
        assertEquals("default", body["fallbacks"]!!.jsonPrimitive.content)
        val output = body["output_config"]!!.jsonObject
        assertEquals("high", output["effort"]!!.jsonPrimitive.content)
        val format = output["format"]!!.jsonObject
        assertEquals("json_schema", format["type"]!!.jsonPrimitive.content)
        val schema = format["schema"]!!.jsonObject
        assertEquals(JsonPrimitive(false), schema["additionalProperties"])
        assertTrue("players" in schema["properties"]!!.jsonObject)
        val content = body["messages"]!!.jsonArray[0].jsonObject["content"]!!.jsonArray
        val image = content[0].jsonObject
        assertEquals("image", image["type"]!!.jsonPrimitive.content)
        val source = image["source"] as JsonObject
        assertEquals("image/jpeg", source["media_type"]!!.jsonPrimitive.content)
        assertEquals(java.util.Base64.getEncoder().encodeToString(jpeg), source["data"]!!.jsonPrimitive.content)
        assertTrue(body["system"]!!.jsonPrimitive.content.contains("druzs"))
    }

    @Test fun mapsErrors() {
        server.enqueue(
            MockResponse().setResponseCode(401).setHeader("Content-Type", "application/json")
                .setBody("""{"type":"error","error":{"type":"authentication_error","message":"invalid x-api-key"}}"""),
        )
        try {
            scanner().scan(byteArrayOf(1))
            fail("kivételt vártunk")
        } catch (e: ScanException) {
            assertTrue(e.message!!.contains("API kulcs"))
        }
        server.enqueue(MockResponse().setHeader("Content-Type", "application/json").setBody(messageJson("", "refusal")))
        try {
            scanner().scan(byteArrayOf(1))
            fail("kivételt vártunk")
        } catch (e: ScanException) {
            assertTrue(e.message!!.contains("nem dolgozta fel"))
        }
        server.enqueue(MockResponse().setHeader("Content-Type", "application/json").setBody(messageJson("nem json")))
        try {
            scanner().scan(byteArrayOf(1))
            fail("kivételt vártunk")
        } catch (e: ScanException) {
            assertTrue(e.message!!.contains("nem értelmezhetők"))
        }
    }
}
