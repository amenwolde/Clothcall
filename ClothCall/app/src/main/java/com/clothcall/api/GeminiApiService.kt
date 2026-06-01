package com.clothcall.api

import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.util.concurrent.TimeUnit

private const val TAG = "GroqAPI"
private const val BASE_URL = "https://api.groq.com/openai/v1/chat/completions"
private const val MODEL = "meta-llama/llama-4-scout-17b-16e-instruct"

private val SYSTEM_PROMPT = """
You are a private clothing condition assistant for ClothCall. Analyze this clothing image and report only what you observe. Use passive voice. Be location-specific for stains ('darker mark near the right cuff'). Use soft comparative language for fading ('this has drifted noticeably from a fresh shirt'). Never command the user. If uncertain, say so openly. End every report with: Do you still want to wear it?

Language rules:
- Stains: sharp, location-specific descriptions only
- Fading: soft, trend-based language; reference the caregiver by name when provided
- Uncertainty: admit it openly, do not speculate beyond the image
- Always end with exactly: Do you still want to wear it?
- Never use the phrases "you should", "you must", or "change your shirt"
- Always use passive voice throughout
""".trimIndent()

private val STAIN_ONLY_PROMPT = """
You are a private clothing condition assistant for ClothCall. Analyze this clothing image for visible stains, marks, discoloration, or damage only. Use passive voice. Be location-specific ('darker mark near the right cuff'). Admit uncertainty openly. Never command the user. Reference the caregiver by name when provided. If nothing concerning is visible, say so clearly. End every report with exactly: Do you still want to wear it?

Language rules:
- Stains: sharp, location-specific descriptions only
- Uncertainty: admit it openly, do not speculate beyond the image
- Always end with exactly: Do you still want to wear it?
- Never use the phrases "you should", "you must", or "change your shirt"
- Always use passive voice throughout
""".trimIndent()

class GeminiApiService {

    private val client = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(90, TimeUnit.SECONDS)
        .writeTimeout(30, TimeUnit.SECONDS)
        .build()

    suspend fun analyzeClothing(
        apiKey: String,
        base64Image: String,
        baselineBase64: String? = null,
        caregiverName: String?,
        fadeThreshold: Int?
    ): Result<String> = withContext(Dispatchers.IO) {
        runCatching {
            Log.d(TAG, "analyzeClothing — base64 len: ${base64Image.length}, hasBaseline: ${baselineBase64 != null}")
            val (userMsg, systemPrompt) = if (baselineBase64 != null) {
                userMessageWithTwoImages(baselineBase64, base64Image, caregiverName, fadeThreshold) to SYSTEM_PROMPT
            } else {
                userMessageWithImage(base64Image, caregiverName) to STAIN_ONLY_PROMPT
            }
            val messages = JSONArray().apply {
                put(systemMessage(systemPrompt))
                put(userMsg)
            }
            extractText(post(apiKey, buildBody(messages)))
        }.also { result ->
            result.onFailure { Log.e(TAG, "analyzeClothing failed", it) }
        }
    }

    suspend fun requestMoreDetail(
        apiKey: String,
        base64Image: String,
        baselineBase64: String? = null,
        firstResponseText: String,
        caregiverName: String?,
        fadeThreshold: Int?
    ): Result<String> = withContext(Dispatchers.IO) {
        runCatching {
            val followUp = buildString {
                append("Please provide more specific detail — focus on any areas that were only briefly mentioned.")
                if (caregiverName != null) append(" Remember, the trusted person is $caregiverName.")
            }
            val (firstUserMsg, systemPrompt) = if (baselineBase64 != null) {
                userMessageWithTwoImages(baselineBase64, base64Image, caregiverName, fadeThreshold) to SYSTEM_PROMPT
            } else {
                userMessageWithImage(base64Image, caregiverName) to STAIN_ONLY_PROMPT
            }
            val messages = JSONArray().apply {
                put(systemMessage(systemPrompt))
                put(firstUserMsg)
                put(assistantMessage(firstResponseText))
                put(userMessageText(followUp))
            }
            extractText(post(apiKey, buildBody(messages)))
        }.also { result ->
            result.onFailure { Log.e(TAG, "requestMoreDetail failed", it) }
        }
    }

    // ── helpers ──────────────────────────────────────────────────────────────

    private fun systemMessage(prompt: String) = JSONObject().apply {
        put("role", "system")
        put("content", prompt)
    }

    private fun imageUrlPart(base64: String) = JSONObject().apply {
        put("type", "image_url")
        put("image_url", JSONObject().apply {
            put("url", "data:image/jpeg;base64,$base64")
        })
    }

    private fun textPart(text: String) = JSONObject().apply {
        put("type", "text")
        put("text", text)
    }

    private fun userMessageWithImage(
        base64Image: String,
        caregiverName: String?
    ): JSONObject {
        val text = buildString {
            append("Please analyze this clothing item.")
            if (caregiverName != null) {
                append(" The trusted person for this check is called $caregiverName. Reference them by name in your response.")
            }
        }
        return JSONObject().apply {
            put("role", "user")
            put("content", JSONArray().apply {
                put(imageUrlPart(base64Image))
                put(textPart(text))
            })
        }
    }

    private fun userMessageWithTwoImages(
        baselineBase64: String,
        currentBase64: String,
        caregiverName: String?,
        fadeThreshold: Int?
    ): JSONObject {
        val text = buildString {
            append("The first image is the baseline reference for this garment. The second image is what is being worn today. Compare them for fading, stains, and condition changes.")
            if (caregiverName != null) {
                append(" The trusted person for this check is called $caregiverName. Reference them by name in your response.")
                if (fadeThreshold != null) {
                    append(" Their fade tolerance is $fadeThreshold%.")
                }
            }
        }
        return JSONObject().apply {
            put("role", "user")
            put("content", JSONArray().apply {
                put(imageUrlPart(baselineBase64))
                put(imageUrlPart(currentBase64))
                put(textPart(text))
            })
        }
    }

    private fun assistantMessage(text: String) = JSONObject().apply {
        put("role", "assistant")
        put("content", text)
    }

    private fun userMessageText(text: String) = JSONObject().apply {
        put("role", "user")
        put("content", JSONArray().apply {
            put(textPart(text))
        })
    }

    private fun buildBody(messages: JSONArray) = JSONObject().apply {
        put("model", MODEL)
        put("messages", messages)
        put("max_tokens", 1024)
    }

    private fun post(apiKey: String, body: JSONObject): String {
        if (apiKey.isBlank()) throw IllegalArgumentException("API key is empty — enter it in settings")

        val request = Request.Builder()
            .url(BASE_URL)
            .addHeader("Authorization", "Bearer $apiKey")
            .addHeader("Content-Type", "application/json")
            .post(body.toString().toRequestBody("application/json".toMediaType()))
            .build()

        client.newCall(request).execute().use { response ->
            val raw = response.body?.string() ?: ""
            Log.d(TAG, "HTTP ${response.code} — body length ${raw.length}")
            if (!response.isSuccessful) {
                Log.e(TAG, "Error body: $raw")
                val errorMsg = runCatching {
                    JSONObject(raw).getJSONObject("error").getString("message")
                }.getOrNull()
                throw Exception(errorMsg ?: "HTTP ${response.code}: $raw")
            }
            return raw
        }
    }

    private fun extractText(rawJson: String): String {
        val json = JSONObject(rawJson)
        val choices = json.optJSONArray("choices")
        if (choices == null || choices.length() == 0) {
            throw Exception("Groq returned no choices")
        }
        val content = choices.getJSONObject(0)
            .optJSONObject("message")
            ?.optString("content")
        if (content.isNullOrBlank()) {
            val finishReason = choices.getJSONObject(0).optString("finish_reason")
            throw Exception("Groq response had empty content (finish_reason: $finishReason)")
        }
        return content
    }
}
