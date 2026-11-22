package com.example.data.remote

import android.util.Base64
import com.example.AppConfig
import com.example.data.model.AiProvider
import com.example.data.model.MagicContent
import com.example.data.model.TranscriptSegment
import com.example.data.model.WordTiming
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.MultipartBody
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.asRequestBody
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.util.concurrent.TimeUnit

class RateLimitException(message: String) : Exception(message)
class ApiKeyInvalidException(message: String) : Exception(message)
class AiServiceException(message: String) : Exception(message)

data class AiResult<T>(
    val data: T,
    val tokensUsed: Long
)

data class AiRequestConfig(
    val provider: AiProvider,
    val apiKey: String,
    val geminiModelId: String,
    val openRouterModelId: String
)

class AiService {

    private val client: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(180, TimeUnit.SECONDS)
        .writeTimeout(120, TimeUnit.SECONDS)
        .build()

    private val jsonMediaType = "application/json; charset=utf-8".toMediaType()

    companion object {
        private const val GEMINI_BASE = "https://generativelanguage.googleapis.com"
        private const val GROQ_WHISPER_MODEL = "whisper-large-v3-turbo"
        private const val GROQ_LLM_MODEL = "llama-3.3-70b-versatile"
        private const val GROQ_BASE = "https://api.groq.com/openai/v1"
        private const val OPENROUTER_BASE = "https://openrouter.ai/api/v1"
        private const val OPENAI_BASE = "https://api.openai.com/v1"
        private const val OPENAI_WHISPER_MODEL = "whisper-1"
        private const val OPENAI_LLM_MODEL = "gpt-4o-mini"
        private const val INLINE_AUDIO_LIMIT_BYTES = 10L * 1024L * 1024L // 10 MB
    }

    suspend fun transcribeAudio(
        config: AiRequestConfig,
        audioFile: File,
        mimeType: String,
        durationMs: Long
    ): AiResult<List<TranscriptSegment>> = withContext(Dispatchers.IO) {
        when (config.provider) {
            AiProvider.GEMINI -> transcribeWithGemini(
                apiKey = config.apiKey,
                modelId = config.geminiModelId,
                audioFile = audioFile,
                mimeType = mimeType,
                durationMs = durationMs
            )
            AiProvider.GROQ -> transcribeWithWhisperEndpoint(
                baseUrl = GROQ_BASE,
                model = GROQ_WHISPER_MODEL,
                provider = AiProvider.GROQ,
                apiKey = config.apiKey,
                audioFile = audioFile,
                mimeType = mimeType,
                durationMs = durationMs
            )
            AiProvider.OPENAI -> transcribeWithWhisperEndpoint(
                baseUrl = OPENAI_BASE,
                model = OPENAI_WHISPER_MODEL,
                provider = AiProvider.OPENAI,
                apiKey = config.apiKey,
                audioFile = audioFile,
                mimeType = mimeType,
                durationMs = durationMs
            )
            AiProvider.OPENROUTER -> throw AiServiceException(
                "OpenRouter is configured for Hinglish conversion & Magic text tools. Switch to Google Gemini, Groq, or OpenAI in Settings to transcribe audio."
            )
        }
    }

    suspend fun translateToEnglish(
        config: AiRequestConfig,
        segments: List<TranscriptSegment>
    ): AiResult<List<TranscriptSegment>> = withContext(Dispatchers.IO) {
        if (segments.isEmpty()) return@withContext AiResult(emptyList(), 0L)

        val translatedAll = mutableListOf<TranscriptSegment>()
        var totalTokens = 0L

        val batches = segments.chunked(45)
        for (batch in batches) {
            val payloadArray = JSONArray()
            batch.forEach { seg ->
                payloadArray.put(
                    JSONObject().apply {
                        put("id", seg.id)
                        put("text", seg.text)
                    }
                )
            }

            val prompt = """
You are an expert subtitle translator. Translate each item's "text" into clear, natural, modern English suitable for video captions.
Rules:
1. Preserve the EXACT same number of items and the EXACT "id" for each item. Never merge or split lines.
2. Keep meaning accurate, crisp, and natural for spoken video subtitles.
3. Return ONLY valid JSON matching: {"items":[{"id":1,"text":"..."}]}

Input:
$payloadArray
            """.trimIndent()

            val response = callTextJson(config, prompt)
            totalTokens += response.tokensUsed

            val translatedMap = parseHinglishMap(response.data)
            batch.forEach { original ->
                val newText = translatedMap[original.id]?.takeIf { it.isNotBlank() } ?: original.text
                translatedAll.add(
                    original.copy(
                        text = newText,
                        words = emptyList()
                    )
                )
            }
        }

        AiResult(translatedAll, totalTokens)
    }

    suspend fun smartSpellFixHinglish(
        config: AiRequestConfig,
        segments: List<TranscriptSegment>
    ): AiResult<List<TranscriptSegment>> = withContext(Dispatchers.IO) {
        if (segments.isEmpty()) return@withContext AiResult(emptyList(), 0L)

        val convertedAll = mutableListOf<TranscriptSegment>()
        var totalTokens = 0L

        val batches = segments.chunked(45)
        for (batch in batches) {
            val payloadArray = JSONArray()
            batch.forEach { seg ->
                payloadArray.put(
                    JSONObject().apply {
                        put("id", seg.id)
                        put("text", seg.text)
                    }
                )
            }

            val prompt = """
You are an expert Indian bilingual subtitle editor specializing in AI Spell-Fix & Smart Hinglish.
Convert each item's "text" into clean, natural Romanized Hinglish AND fix phonetic English/brand spelling mistakes written in Hindi or broken transliteration (for example: "वाटसेप" or "watsep" -> "WhatsApp", "यूट्यूब" -> "YouTube", "इंस्टाग्राम" -> "Instagram", "आईफोन" -> "iPhone", "कंप्यूटर" -> "computer").
Rules:
1. Preserve the EXACT same number of items and the EXACT "id" for each item. Never merge, split, or drop lines. Timestamps depend on exact 1:1 IDs.
2. Convert Devanagari Hindi (e.g., "आज हम सीखेंगे") into crisp, modern Hinglish (e.g., "Aaj hum seekhenge").
3. Fix all phonetic English words, app names, brand names, and technical terms to their proper standard English spelling.
4. Return ONLY valid JSON matching: {"items":[{"id":1,"text":"..."}]}

Input:
$payloadArray
            """.trimIndent()

            val response = callTextJson(config, prompt)
            totalTokens += response.tokensUsed

            val convertedMap = parseHinglishMap(response.data)
            batch.forEach { original ->
                val newText = convertedMap[original.id]?.takeIf { it.isNotBlank() } ?: original.text
                convertedAll.add(
                    original.copy(
                        text = newText,
                        words = emptyList()
                    )
                )
            }
        }

        AiResult(convertedAll, totalTokens)
    }

    suspend fun convertToHinglish(
        config: AiRequestConfig,
        segments: List<TranscriptSegment>
    ): AiResult<List<TranscriptSegment>> = smartSpellFixHinglish(config, segments)

    suspend fun generateMagicContent(
        config: AiRequestConfig,
        transcriptText: String
    ): AiResult<MagicContent> = withContext(Dispatchers.IO) {
        val clipped = transcriptText.take(12_000)
        val prompt = """
You are a viral short-form & YouTube content strategist. Based strictly on the following video/audio transcript, generate high-converting creator assets.

Return ONLY valid JSON with this exact schema:
{
  "viralHooks": ["hook 1", "hook 2", "hook 3", "hook 4"],
  "thumbnailTitles": ["2-4 Word Title 1", "2-4 Word Title 2", "2-4 Word Title 3", "2-4 Word Title 4", "2-4 Word Title 5"],
  "captions": "Ready-to-post engaging Instagram/YouTube description with clear spacing and call-to-action",
  "hashtags": ["#Tag1", "#Tag2", "#Tag3", "#Tag4", "#Tag5", "#Tag6", "#Tag7", "#Tag8"],
  "pinnedComment": "1-2 engaging questions to boost audience comments"
}

Constraints:
- viralHooks: 3 to 5 punchy, attention-grabbing opening lines.
- thumbnailTitles: EXACTLY 5 ultra-short punchy titles strictly 2 to 4 words each for Thumbnail / Reel Cover Text.
- captions: concise, engaging, ready-to-post Instagram/YouTube description.
- hashtags: 8 targeted, relevant hashtags starting with #.
- pinnedComment: 1 to 2 engaging questions to boost comments.

Transcript:
$clipped
        """.trimIndent()

        val response = callTextJson(config, prompt)
        val root = parseJsonObjectSafe(response.data)

        AiResult(
            data = MagicContent(
                viralHooks = root.optJSONArray("viralHooks").toStringList(),
                captions = root.optString("captions", "").trim(),
                hashtags = root.optJSONArray("hashtags").toStringList(),
                thumbnailTitles = root.optJSONArray("thumbnailTitles").toStringList().take(5),
                pinnedComment = root.optString("pinnedComment", "").trim()
            ),
            tokensUsed = response.tokensUsed
        )
    }

    private fun callTextJson(config: AiRequestConfig, prompt: String): AiResult<String> {
        return when (config.provider) {
            AiProvider.GEMINI -> callGeminiTextJson(config.apiKey, config.geminiModelId, prompt)
            AiProvider.GROQ -> callOpenAiCompatibleChatJson(
                url = "$GROQ_BASE/chat/completions",
                apiKey = config.apiKey,
                model = GROQ_LLM_MODEL,
                provider = AiProvider.GROQ,
                prompt = prompt,
                extraHeaders = emptyMap(),
                forceJsonMode = true
            )
            AiProvider.OPENAI -> callOpenAiCompatibleChatJson(
                url = "$OPENAI_BASE/chat/completions",
                apiKey = config.apiKey,
                model = OPENAI_LLM_MODEL,
                provider = AiProvider.OPENAI,
                prompt = prompt,
                extraHeaders = emptyMap(),
                forceJsonMode = true
            )
            AiProvider.OPENROUTER -> callOpenAiCompatibleChatJson(
                url = "$OPENROUTER_BASE/chat/completions",
                apiKey = config.apiKey,
                model = config.openRouterModelId,
                provider = AiProvider.OPENROUTER,
                prompt = prompt,
                extraHeaders = mapOf(
                    "HTTP-Referer" to "https://ordieo.app",
                    "X-Title" to AppConfig.APP_NAME
                ),
                forceJsonMode = false
            )
        }
    }

    private fun transcribeWithGemini(
        apiKey: String,
        modelId: String,
        audioFile: File,
        mimeType: String,
        durationMs: Long
    ): AiResult<List<TranscriptSegment>> {
        val prompt = """
Transcribe this audio accurately with precise timestamps.
Language rules (Auto: Hindi / English):
- Where Hindi is spoken, write it in natural Devanagari Hindi script.
- Where English is spoken, write it in English.
- If the speaker code-switches between Hindi and English, write Hindi parts in Devanagari and English words in English naturally.
- Break the transcript into natural subtitle segments of 2 to 5 seconds each.
- Audio total duration is approximately $durationMs milliseconds. Keep all startMs and endMs within 0..$durationMs and strictly increasing.

Return ONLY valid JSON matching this schema:
{
  "segments": [
    { "startMs": 0, "endMs": 2800, "text": "..." }
  ]
}
        """.trimIndent()

        val audioPart = if (audioFile.length() <= INLINE_AUDIO_LIMIT_BYTES) {
            val bytes = audioFile.readBytes()
            val b64 = Base64.encodeToString(bytes, Base64.NO_WRAP)
            JSONObject().apply {
                put(
                    "inline_data",
                    JSONObject().apply {
                        put("mime_type", mimeType)
                        put("data", b64)
                    }
                )
            }
        } else {
            val fileUri = uploadFileToGemini(apiKey, audioFile, mimeType)
            JSONObject().apply {
                put(
                    "file_data",
                    JSONObject().apply {
                        put("mime_type", mimeType)
                        put("file_uri", fileUri)
                    }
                )
            }
        }

        val bodyJson = JSONObject().apply {
            put(
                "contents",
                JSONArray().put(
                    JSONObject().apply {
                        put(
                            "parts",
                            JSONArray()
                                .put(audioPart)
                                .put(JSONObject().put("text", prompt))
                        )
                    }
                )
            )
            put(
                "generationConfig",
                JSONObject().apply {
                    put("responseMimeType", "application/json")
                    put("temperature", 0.1)
                }
            )
        }

        val cleanModel = modelId.removePrefix("models/").trim()
        val url = "$GEMINI_BASE/v1beta/models/$cleanModel:generateContent?key=$apiKey"
        val request = Request.Builder()
            .url(url)
            .post(bodyJson.toString().toRequestBody(jsonMediaType))
            .build()

        client.newCall(request).execute().use { resp ->
            val raw = resp.body?.string().orEmpty()
            handleHttpErrors(resp.code, raw, AiProvider.GEMINI, cleanModel)

            val json = JSONObject(raw)
            val tokens = extractGeminiTokens(json)
            val text = extractGeminiText(json)
            val parsed = parseSegmentsJson(text, durationMs)
            return AiResult(parsed, tokens)
        }
    }

    private fun uploadFileToGemini(apiKey: String, file: File, mimeType: String): String {
        val fileBytes = file.length()
        val initUrl = "$GEMINI_BASE/upload/v1beta/files?key=$apiKey"
        val metadata = JSONObject().put("file", JSONObject().put("display_name", file.name))

        val initReq = Request.Builder()
            .url(initUrl)
            .addHeader("X-Goog-Upload-Protocol", "resumable")
            .addHeader("X-Goog-Upload-Command", "start")
            .addHeader("X-Goog-Upload-Header-Content-Length", fileBytes.toString())
            .addHeader("X-Goog-Upload-Header-Content-Type", mimeType)
            .post(metadata.toString().toRequestBody(jsonMediaType))
            .build()

        val uploadUrl = client.newCall(initReq).execute().use { resp ->
            val raw = resp.body?.string().orEmpty()
            handleHttpErrors(resp.code, raw, AiProvider.GEMINI)
            resp.header("X-Goog-Upload-URL")
                ?: throw AiServiceException("Failed to start Gemini file upload.")
        }

        val uploadReq = Request.Builder()
            .url(uploadUrl)
            .addHeader("X-Goog-Upload-Offset", "0")
            .addHeader("X-Goog-Upload-Command", "upload, finalize")
            .post(file.asRequestBody(mimeType.toMediaType()))
            .build()

        val fileUri = client.newCall(uploadReq).execute().use { resp ->
            val raw = resp.body?.string().orEmpty()
            handleHttpErrors(resp.code, raw, AiProvider.GEMINI)
            val obj = JSONObject(raw)
            val fileObj = obj.optJSONObject("file") ?: obj
            fileObj.optString("uri").takeIf { it.isNotBlank() }
                ?: throw AiServiceException("Gemini Files API did not return a file URI.")
        }

        Thread.sleep(1500L)
        return fileUri
    }

    private fun transcribeWithWhisperEndpoint(
        baseUrl: String,
        model: String,
        provider: AiProvider,
        apiKey: String,
        audioFile: File,
        mimeType: String,
        durationMs: Long
    ): AiResult<List<TranscriptSegment>> {
        val multipart = MultipartBody.Builder()
            .setType(MultipartBody.FORM)
            .addFormDataPart(
                "file",
                audioFile.name,
                audioFile.asRequestBody(mimeType.toMediaType())
            )
            .addFormDataPart("model", model)
            .addFormDataPart("response_format", "verbose_json")
            .addFormDataPart("timestamp_granularities[]", "segment")
            .addFormDataPart("timestamp_granularities[]", "word")
            .build()

        val request = Request.Builder()
            .url("$baseUrl/audio/transcriptions")
            .addHeader("Authorization", "Bearer $apiKey")
            .post(multipart)
            .build()

        client.newCall(request).execute().use { resp ->
            val raw = resp.body?.string().orEmpty()
            handleHttpErrors(resp.code, raw, provider, model)

            val root = JSONObject(raw)
            val wordsList = mutableListOf<WordTiming>()
            val wordsJson = root.optJSONArray("words")
            if (wordsJson != null) {
                for (i in 0 until wordsJson.length()) {
                    val w = wordsJson.optJSONObject(i) ?: continue
                    val wordText = w.optString("word", "").trim()
                    if (wordText.isNotEmpty()) {
                        val s = (w.optDouble("start", 0.0) * 1000.0).toLong().coerceAtLeast(0L)
                        val e = (w.optDouble("end", 0.0) * 1000.0).toLong().coerceAtLeast(s + 80L)
                        wordsList.add(WordTiming(wordText, s, e))
                    }
                }
            }

            val segments = mutableListOf<TranscriptSegment>()
            val segsJson = root.optJSONArray("segments")
            if (segsJson != null && segsJson.length() > 0) {
                for (i in 0 until segsJson.length()) {
                    val sObj = segsJson.optJSONObject(i) ?: continue
                    val text = sObj.optString("text", "").trim()
                    if (text.isEmpty()) continue
                    val startMs = (sObj.optDouble("start", 0.0) * 1000.0).toLong().coerceAtLeast(0L)
                    val endMs = (sObj.optDouble("end", 0.0) * 1000.0).toLong().coerceAtLeast(startMs + 250L)
                    val matchedWords = wordsList.filter { w ->
                        w.startMs >= (startMs - 120L) && w.endMs <= (endMs + 180L)
                    }
                    segments.add(
                        TranscriptSegment(
                            id = segments.size + 1,
                            startMs = startMs,
                            endMs = endMs,
                            text = text,
                            words = matchedWords
                        )
                    )
                }
            } else {
                val fullText = root.optString("text", "").trim()
                if (fullText.isNotEmpty()) {
                    segments.add(
                        TranscriptSegment(
                            id = 1,
                            startMs = 0L,
                            endMs = durationMs.coerceAtLeast(2000L),
                            text = fullText,
                            words = wordsList
                        )
                    )
                }
            }

            val audioSeconds = root.optDouble("duration", durationMs / 1000.0).coerceAtLeast(1.0)
            val estTokens = (audioSeconds * 25.0).toLong()
            return AiResult(segments, estTokens)
        }
    }

    private fun callGeminiTextJson(apiKey: String, modelId: String, prompt: String): AiResult<String> {
        val bodyJson = JSONObject().apply {
            put(
                "contents",
                JSONArray().put(
                    JSONObject().apply {
                        put("parts", JSONArray().put(JSONObject().put("text", prompt)))
                    }
                )
            )
            put(
                "generationConfig",
                JSONObject().apply {
                    put("responseMimeType", "application/json")
                    put("temperature", 0.35)
                }
            )
        }

        val cleanModel = modelId.removePrefix("models/").trim()
        val url = "$GEMINI_BASE/v1beta/models/$cleanModel:generateContent?key=$apiKey"
        val request = Request.Builder()
            .url(url)
            .post(bodyJson.toString().toRequestBody(jsonMediaType))
            .build()

        client.newCall(request).execute().use { resp ->
            val raw = resp.body?.string().orEmpty()
            handleHttpErrors(resp.code, raw, AiProvider.GEMINI, cleanModel)
            val json = JSONObject(raw)
            val tokens = extractGeminiTokens(json)
            val text = extractGeminiText(json)
            return AiResult(text, tokens)
        }
    }

    private fun callOpenAiCompatibleChatJson(
        url: String,
        apiKey: String,
        model: String,
        provider: AiProvider,
        prompt: String,
        extraHeaders: Map<String, String>,
        forceJsonMode: Boolean
    ): AiResult<String> {
        val bodyJson = JSONObject().apply {
            put("model", model)
            put("temperature", 0.35)
            if (forceJsonMode) {
                put("response_format", JSONObject().put("type", "json_object"))
            }
            put(
                "messages",
                JSONArray()
                    .put(
                        JSONObject().apply {
                            put("role", "system")
                            put("content", "You are a precise JSON-only assistant. Return ONLY valid JSON without markdown code blocks.")
                        }
                    )
                    .put(
                        JSONObject().apply {
                            put("role", "user")
                            put("content", prompt)
                        }
                    )
            )
        }

        val reqBuilder = Request.Builder()
            .url(url)
            .addHeader("Authorization", "Bearer $apiKey")
            .post(bodyJson.toString().toRequestBody(jsonMediaType))

        extraHeaders.forEach { (k, v) -> reqBuilder.addHeader(k, v) }

        client.newCall(reqBuilder.build()).execute().use { resp ->
            val raw = resp.body?.string().orEmpty()
            handleHttpErrors(resp.code, raw, provider, model)
            val json = JSONObject(raw)
            val tokens = json.optJSONObject("usage")?.optLong("total_tokens", 0L) ?: 0L
            val content = json.optJSONArray("choices")
                ?.optJSONObject(0)
                ?.optJSONObject("message")
                ?.optString("content", "")
                .orEmpty()
            return AiResult(content, tokens)
        }
    }

    private fun handleHttpErrors(code: Int, rawBody: String, provider: AiProvider, modelId: String = "") {
        if (code in 200..299) return
        val parsedMsg = runCatching {
            val obj = JSONObject(rawBody)
            obj.optJSONObject("error")?.optString("message") ?: rawBody.take(180)
        }.getOrDefault(rawBody.take(180))

        when (code) {
            429 -> throw RateLimitException(
                "${provider.displayName} rate limit reached. Wait a moment or switch providers in Settings."
            )
            401, 403 -> throw ApiKeyInvalidException(
                "Invalid or unauthorized ${provider.displayName} API key. Update it in Settings."
            )
            404 -> throw AiServiceException(
                "Model \"$modelId\" not found (404). Select another model or set a Custom Model ID in Settings."
            )
            else -> throw AiServiceException("${provider.displayName} error ($code): $parsedMsg")
        }
    }

    private fun extractGeminiTokens(json: JSONObject): Long {
        val usage = json.optJSONObject("usageMetadata") ?: return 0L
        val total = usage.optLong("totalTokenCount", 0L)
        if (total > 0L) return total
        return usage.optLong("promptTokenCount", 0L) + usage.optLong("candidatesTokenCount", 0L)
    }

    private fun extractGeminiText(json: JSONObject): String {
        val candidates = json.optJSONArray("candidates") ?: return ""
        val first = candidates.optJSONObject(0) ?: return ""
        val parts = first.optJSONObject("content")?.optJSONArray("parts") ?: return ""
        val sb = StringBuilder()
        for (i in 0 until parts.length()) {
            sb.append(parts.optJSONObject(i)?.optString("text", "").orEmpty())
        }
        return sb.toString().trim()
    }

    private fun parseSegmentsJson(raw: String, fallbackDurationMs: Long): List<TranscriptSegment> {
        val root = parseJsonObjectSafe(raw)
        val arr = root.optJSONArray("segments")
        val segments = mutableListOf<TranscriptSegment>()
        if (arr != null) {
            for (i in 0 until arr.length()) {
                val obj = arr.optJSONObject(i) ?: continue
                val text = obj.optString("text", "").trim()
                if (text.isEmpty()) continue
                val startMs = obj.optLong("startMs", -1L).let {
                    if (it >= 0L) it else (obj.optDouble("start", 0.0) * 1000.0).toLong()
                }.coerceAtLeast(0L)
                val endMs = obj.optLong("endMs", -1L).let {
                    if (it >= 0L) it else (obj.optDouble("end", (startMs + 2500L) / 1000.0) * 1000.0).toLong()
                }.coerceAtLeast(startMs + 250L)
                segments.add(
                    TranscriptSegment(
                        id = segments.size + 1,
                        startMs = startMs,
                        endMs = endMs,
                        text = text
                    )
                )
            }
        }
        if (segments.isEmpty() && raw.isNotBlank()) {
            segments.add(
                TranscriptSegment(
                    id = 1,
                    startMs = 0L,
                    endMs = fallbackDurationMs.coerceAtLeast(3000L),
                    text = stripMarkdownFences(raw).take(1000)
                )
            )
        }
        return segments
    }

    private fun parseHinglishMap(raw: String): Map<Int, String> {
        val root = parseJsonObjectSafe(raw)
        val arr = root.optJSONArray("items") ?: root.optJSONArray("segments") ?: return emptyMap()
        val map = mutableMapOf<Int, String>()
        for (i in 0 until arr.length()) {
            val obj = arr.optJSONObject(i) ?: continue
            val id = obj.optInt("id", -1)
            val text = obj.optString("text", "").trim()
            if (id >= 0 && text.isNotEmpty()) {
                map[id] = text
            }
        }
        return map
    }

    private fun parseJsonObjectSafe(raw: String): JSONObject {
        val cleaned = stripMarkdownFences(raw)
        return runCatching { JSONObject(cleaned) }.getOrElse {
            val start = cleaned.indexOf('{')
            val end = cleaned.lastIndexOf('}')
            if (start in 0 until end) {
                runCatching { JSONObject(cleaned.substring(start, end + 1)) }.getOrDefault(JSONObject())
            } else {
                JSONObject()
            }
        }
    }

    private fun stripMarkdownFences(raw: String): String {
        var text = raw.trim()
        if (text.startsWith("```")) {
            text = text.substringAfter('\n', text)
        }
        if (text.endsWith("```")) {
            text = text.substringBeforeLast("```")
        }
        return text.trim()
    }

    private fun JSONArray?.toStringList(): List<String> {
        if (this == null) return emptyList()
        val list = mutableListOf<String>()
        for (i in 0 until length()) {
            val s = optString(i, "").trim()
            if (s.isNotEmpty()) list.add(s)
        }
        return list
    }
}
