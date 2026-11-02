package com.jump.browser.data

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.BufferedReader
import java.io.InputStreamReader
import java.net.HttpURLConnection
import java.net.URL

/**
 * A minimal chat client for an OpenAI-compatible endpoint.
 *
 * The app ships no key and no baked-in provider: the user supplies a base URL,
 * a model and a key in Settings. That keeps the feature honest (no hidden
 * backend, no telemetry) and works with OpenAI, OpenRouter, a local server or
 * anything else that speaks the same `/chat/completions` shape.
 */
class AiClient(private val settings: ChatSettings) {

    data class Message(val role: String, val content: String)

    sealed interface Result {
        data class Ok(val text: String) : Result
        data class Error(val message: String) : Result
    }

    val isConfigured: Boolean
        get() = settings.baseUrl.isNotBlank() && settings.model.isNotBlank()

    /** Sends [prompt] with optional page context and returns the reply. */
    suspend fun ask(prompt: String, pageContext: String? = null): Result {
        if (!isConfigured) return Result.Error("Add an endpoint and model in Settings -> AI settings")
        val messages = JSONArray()
        settings.systemPrompt.takeIf { it.isNotBlank() }?.let {
            messages.put(JSONObject().put("role", "system").put("content", it))
        }
        val content = if (pageContext.isNullOrBlank()) {
            prompt
        } else {
            "Page content:\n$pageContext\n\nQuestion: $prompt"
        }
        messages.put(JSONObject().put("role", "user").put("content", content))

        val body = JSONObject()
            .put("model", settings.model)
            .put("messages", messages)
            .put("temperature", 0.4)
        val reply = post(body.toString())
        return reply
    }

    /**
     * One non-streaming request. Runs on IO; callers are on the main thread.
     *
     * The response shape is `{"choices":[{"message":{"content":"..."}}]}`.
     * Errors are surfaced with the provider's own message where possible,
     * because "401" alone is not actionable.
     */
    private suspend fun post(json: String): Result = withContext(Dispatchers.IO) {
        var connection: HttpURLConnection? = null
        try {
            val url = URL(settings.baseUrl.trimEnd('/') + "/chat/completions")
            connection = (url.openConnection() as HttpURLConnection).apply {
                requestMethod = "POST"
                connectTimeout = 20_000
                readTimeout = 60_000
                doOutput = true
                setRequestProperty("Content-Type", "application/json")
                if (settings.apiKey.isNotBlank()) {
                    setRequestProperty("Authorization", "Bearer ${settings.apiKey}")
                }
                setRequestProperty("Accept", "application/json")
            }
            connection.outputStream.use { it.write(json.toByteArray()) }
            val code = connection.responseCode
            val stream = if (code in 200..299) connection.inputStream else connection.errorStream
            val text = stream?.let { input ->
                BufferedReader(InputStreamReader(input, Charsets.UTF_8)).use { it.readText() }
            }.orEmpty()
            if (code !in 200..299) {
                val detail = runCatching {
                    JSONObject(text).optJSONObject("error")?.optString("message")
                }.getOrNull()
                return@withContext Result.Error(
                    if (!detail.isNullOrBlank()) "$code: $detail" else "Request failed ($code)",
                )
            }
            val parsed = runCatching { JSONObject(text) }.getOrNull()
                ?: return@withContext Result.Error("The response was not JSON")
            val content = parsed.optJSONArray("choices")
                ?.optJSONObject(0)
                ?.optJSONObject("message")
                ?.optString("content")
            if (content.isNullOrBlank()) {
                return@withContext Result.Error("The response had no message")
            }
            Result.Ok(content.trim())
        } catch (e: Exception) {
            Result.Error(e.message ?: "Network error")
        } finally {
            connection?.disconnect()
        }
    }
}

/** Settings for the assistant; stored with the rest of the app settings. */
data class ChatSettings(
    val baseUrl: String = "",
    val model: String = "",
    val apiKey: String = "",
    val systemPrompt: String = "",
) {
    companion object {
        private const val PREFIX = "ai_"

        fun fromJson(o: JSONObject) = ChatSettings(
            baseUrl = o.optString("${PREFIX}baseUrl", ""),
            model = o.optString("${PREFIX}model", ""),
            apiKey = o.optString("${PREFIX}apiKey", ""),
            systemPrompt = o.optString("${PREFIX}systemPrompt", ""),
        )

        fun writeTo(o: JSONObject, s: ChatSettings) {
            o.put("${PREFIX}baseUrl", s.baseUrl)
            o.put("${PREFIX}model", s.model)
            o.put("${PREFIX}apiKey", s.apiKey)
            o.put("${PREFIX}systemPrompt", s.systemPrompt)
        }
    }
}
