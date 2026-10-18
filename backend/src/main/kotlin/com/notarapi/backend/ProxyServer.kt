package com.notarapi.backend

import io.ktor.client.HttpClient
import io.ktor.client.engine.cio.CIO
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation as ClientNegotiation
import io.ktor.client.request.header
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.HttpStatusCode
import io.ktor.http.contentType
import io.ktor.serialization.kotlinx.json.json
import io.ktor.server.application.Application
import io.ktor.server.application.install
import io.ktor.server.auth.Authentication
import io.ktor.server.auth.authenticate
import io.ktor.server.auth.bearer
import io.ktor.server.engine.embeddedServer
import io.ktor.server.netty.Netty
import io.ktor.server.plugins.contentnegotiation.ContentNegotiation
import io.ktor.server.plugins.ratelimit.RateLimit
import io.ktor.server.plugins.ratelimit.RateLimitName
import io.ktor.server.plugins.statuspages.StatusPages
import io.ktor.server.request.receive
import io.ktor.server.response.respond
import io.ktor.server.routing.post
import io.ktor.server.routing.routing
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlin.time.Duration.Companion.minutes

/**
 * Backend proxy LLM (plan §9). Deploy sendiri:
 *   PROVIDER_API_KEY=sk-... APP_TOKENS=token-1,token-2 PORT=8080 ./gradlew :backend:run
 *
 * Kontrak: POST /v1/receipts/parse (alias /v1/receipt/extract), Bearer token aplikasi,
 * body {schemaVersion,locale,currency,ocrText<=8000,requestedFields} -> {content:"<JSON struk>"}.
 * Isi env: PROVIDER_API_KEY (wajib), APP_TOKENS (wajib), PROVIDER_URL/PROVIDER_MODEL (opsional).
 */
fun main() {
    val port = System.getenv("PORT")?.toIntOrNull() ?: 8080
    embeddedServer(Netty, port = port, module = Application::receiptProxy).start(wait = true)
}

fun Application.receiptProxy() {
    val providerKey = System.getenv("PROVIDER_API_KEY").orEmpty()
    val providerUrl = System.getenv("PROVIDER_URL").ifNullOrBlank("https://api.openai.com/v1/chat/completions")
    val providerModel = System.getenv("PROVIDER_MODEL").ifNullOrBlank("gpt-4o-mini")
    val appTokens = System.getenv("APP_TOKENS")?.split(",")?.map { it.trim() }
        ?.filter { it.isNotEmpty() }?.toSet() ?: emptySet()

    install(ContentNegotiation) { json(Json { ignoreUnknownKeys = true }) }
    install(StatusPages) {
        exception<Throwable> { call, _ ->
            call.respond(HttpStatusCode.InternalServerError, mapOf("error" to "internal_error"))
        }
    }
    install(Authentication) {
        bearer("app") {
            realm = "notarapi"
            authenticate { cred ->
                if (appTokens.isEmpty() || appTokens.contains(cred.token)) UserPrincipal("app") else null
            }
        }
    }
    install(RateLimit) {
        register(RateLimitName("global")) {
            rateLimiter(limit = 60, refillPeriod = 1.minutes)
        }
    }

    routing {
        authenticate("app") {
            post("/v1/receipts/parse") { handleParse(providerUrl, providerModel, providerKey) }
            post("/v1/receipt/extract") { handleParse(providerUrl, providerModel, providerKey) }
        }
    }
}

private suspend fun io.ktor.server.routing.RoutingContext.handleParse(url: String, model: String, providerKey: String) {
    val req = call.receive<ParseRequest>()
    require(req.schemaVersion == 1) { "schemaVersion harus 1" }
    require(req.ocrText.isNotBlank() && req.ocrText.length <= 8_000) { "ocrText 1..8000 karakter" }
    if (providerKey.isBlank()) {
        call.respond(HttpStatusCode.ServiceUnavailable, mapOf("error" to "provider_not_configured"))
        return
    }
    val content = callProvider(url, model, providerKey, buildPrompt(req.ocrText))
    call.respond(ParseResponse(content = content))
}

private fun String?.ifNullOrBlank(default: String): String = if (this.isNullOrBlank()) default else this

@Serializable
data class ParseRequest(
    val schemaVersion: Int = 1,
    val locale: String = "id-ID",
    val currency: String = "IDR",
    val ocrText: String,
    val requestedFields: List<String> = listOf("merchant", "date", "items", "totals"),
)

@Serializable
data class ParseResponse(val content: String)

private class UserPrincipal(val name: String) : io.ktor.server.auth.Principal

private fun buildPrompt(ocr: String): String = buildString {
    append("Kamu parser struk Indonesia. Output HANYA JSON valid tanpa markdown.\n")
    append("Aturan: merchantName; transactionDate ISO-8601; currency IDR; ")
    append("items[name,quantity,unitPriceMinor,totalMinor,confidence]; ")
    append("subtotalMinor; taxMinor; discountMinor; totalMinor (rupiah tanpa sen, 15000).\n")
    append("Teks OCR:\n")
    append(ocr)
}

private fun callProvider(url: String, model: String, key: String, prompt: String): String {
    val client = HttpClient(CIO) {
        install(ClientNegotiation) { kotlinx.serialization.json.Json { ignoreUnknownKeys = true } }
    }
    return kotlinx.coroutines.runBlocking {
        try {
            val resp = client.post(url) {
                header("Authorization", "Bearer $key")
                contentType(ContentType.Application.Json)
                setBody(buildJsonObject {
                    put("model", JsonPrimitive(model))
                    put("temperature", JsonPrimitive(0.1))
                    put("max_tokens", JsonPrimitive(1024))
                    put("response_format", buildJsonObject { put("type", JsonPrimitive("json_object")) })
                    put("messages", buildJsonArray {
                        add(buildJsonObject {
                            put("role", JsonPrimitive("system"))
                            put("content", JsonPrimitive("Kamu parser struk Indonesia. Output HANYA JSON valid."))
                        })
                        add(buildJsonObject {
                            put("role", JsonPrimitive("user"))
                            put("content", JsonPrimitive(prompt))
                        })
                    })
                })
            }
            val body = resp.bodyAsText()
            Json.parseToJsonElement(body).jsonObject["choices"]?.jsonArray?.firstOrNull()?.jsonObject
                ?.get("message")?.jsonObject?.get("content")?.jsonPrimitive?.content
                ?: throw RuntimeException("provider response tidak dikenal")
        } finally {
            client.close()
        }
    }
}
