package com.example.data.local

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.example.data.model.AiProvider
import com.example.data.model.AppSettings
import com.example.data.model.AudioCompressPreset
import com.example.data.model.AudioSaveBehavior
import com.example.data.model.AutoCorrectRule
import com.example.data.model.DailyUsage
import com.example.data.model.GeminiModelOption
import com.example.data.model.RetentionOption
import com.example.data.model.ThemeMode
import com.example.data.model.VideoCompressPreset
import com.squareup.moshi.Moshi
import com.squareup.moshi.Types
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.UUID

private val Context.transcribeDataStore: DataStore<Preferences> by preferencesDataStore(
    name = "transcribe_preferences"
)

class PreferencesManager(private val context: Context) {

    private val moshi: Moshi = Moshi.Builder()
        .addLast(KotlinJsonAdapterFactory())
        .build()

    private val rulesListType = Types.newParameterizedType(List::class.java, AutoCorrectRule::class.java)
    private val rulesAdapter = moshi.adapter<List<AutoCorrectRule>>(rulesListType)

    private object Keys {
        val THEME_MODE = stringPreferencesKey("theme_mode")
        val IS_DARK_THEME = booleanPreferencesKey("is_dark_theme")
        val AI_PROVIDER = stringPreferencesKey("ai_provider")
        val GEMINI_MODEL_OPTION = stringPreferencesKey("gemini_model_option")
        val GEMINI_CUSTOM_MODEL_ID = stringPreferencesKey("gemini_custom_model_id")
        val OPENROUTER_MODEL_ID = stringPreferencesKey("openrouter_model_id")
        val GEMINI_API_KEY = stringPreferencesKey("gemini_api_key")
        val GROQ_API_KEY = stringPreferencesKey("groq_api_key")
        val OPENROUTER_API_KEY = stringPreferencesKey("openrouter_api_key")
        val OPENAI_API_KEY = stringPreferencesKey("openai_api_key")
        val DEFAULT_RETENTION = stringPreferencesKey("default_retention")
        val AUDIO_SAVE_BEHAVIOR = stringPreferencesKey("audio_save_behavior")
        val DEFAULT_VIDEO_PRESET = stringPreferencesKey("default_video_preset")
        val DEFAULT_FIX_HDR = booleanPreferencesKey("default_fix_hdr")
        val DEFAULT_AUDIO_PRESET = stringPreferencesKey("default_audio_preset")
        val AUTO_CORRECT_RULES_JSON = stringPreferencesKey("auto_correct_rules_json")
        val USAGE_DATE = stringPreferencesKey("usage_date")
        val USAGE_REQUESTS = intPreferencesKey("usage_requests")
        val USAGE_TOKENS = longPreferencesKey("usage_tokens")
        val REMOTE_CONFIG_JSON = stringPreferencesKey("remote_config_json")
        val DISMISSED_UPDATE_VERSION = stringPreferencesKey("dismissed_update_version")
    }

    private fun todayDateKey(): String {
        val formatter = SimpleDateFormat("yyyy-MM-dd", Locale.US)
        return formatter.format(Date())
    }

    val settingsFlow: Flow<AppSettings> = context.transcribeDataStore.data.map { prefs ->
        val rulesJson = prefs[Keys.AUTO_CORRECT_RULES_JSON]
        val rules = if (!rulesJson.isNullOrBlank()) {
            runCatching { rulesAdapter.fromJson(rulesJson) }.getOrNull().orEmpty()
        } else {
            emptyList()
        }

        val resolvedThemeMode = when {
            prefs[Keys.THEME_MODE] != null -> ThemeMode.fromKey(prefs[Keys.THEME_MODE])
            prefs[Keys.IS_DARK_THEME] == false -> ThemeMode.LIGHT
            else -> ThemeMode.SYSTEM
        }

        AppSettings(
            themeMode = resolvedThemeMode,
            provider = AiProvider.fromKey(prefs[Keys.AI_PROVIDER]),
            geminiModelOption = GeminiModelOption.fromKey(prefs[Keys.GEMINI_MODEL_OPTION]),
            geminiCustomModelId = prefs[Keys.GEMINI_CUSTOM_MODEL_ID] ?: "",
            openRouterModelId = prefs[Keys.OPENROUTER_MODEL_ID] ?: "meta-llama/llama-3.3-70b-instruct:free",
            geminiApiKey = prefs[Keys.GEMINI_API_KEY] ?: "",
            groqApiKey = prefs[Keys.GROQ_API_KEY] ?: "",
            openRouterApiKey = prefs[Keys.OPENROUTER_API_KEY] ?: "",
            openAiApiKey = prefs[Keys.OPENAI_API_KEY] ?: "",
            defaultRetention = RetentionOption.fromKey(prefs[Keys.DEFAULT_RETENTION]),
            audioSaveBehavior = AudioSaveBehavior.fromKey(prefs[Keys.AUDIO_SAVE_BEHAVIOR]),
            defaultVideoPreset = VideoCompressPreset.fromKey(prefs[Keys.DEFAULT_VIDEO_PRESET]),
            defaultFixHdr = prefs[Keys.DEFAULT_FIX_HDR] ?: true,
            defaultAudioPreset = AudioCompressPreset.fromKey(prefs[Keys.DEFAULT_AUDIO_PRESET]),
            autoCorrectRules = rules
        )
    }

    val dailyUsageFlow: Flow<DailyUsage> = context.transcribeDataStore.data.map { prefs ->
        val today = todayDateKey()
        val storedDate = prefs[Keys.USAGE_DATE] ?: ""
        if (storedDate != today) {
            DailyUsage(dateKey = today, requestsToday = 0, tokensToday = 0L)
        } else {
            DailyUsage(
                dateKey = storedDate,
                requestsToday = prefs[Keys.USAGE_REQUESTS] ?: 0,
                tokensToday = prefs[Keys.USAGE_TOKENS] ?: 0L
            )
        }
    }

    suspend fun setThemeMode(mode: ThemeMode) {
        context.transcribeDataStore.edit { prefs ->
            prefs[Keys.THEME_MODE] = mode.name
        }
    }

    suspend fun setProvider(provider: AiProvider) {
        context.transcribeDataStore.edit { prefs ->
            prefs[Keys.AI_PROVIDER] = provider.name
        }
    }

    suspend fun setGeminiModelOption(option: GeminiModelOption) {
        context.transcribeDataStore.edit { prefs ->
            prefs[Keys.GEMINI_MODEL_OPTION] = option.name
        }
    }

    suspend fun setGeminiCustomModelId(modelId: String) {
        context.transcribeDataStore.edit { prefs ->
            prefs[Keys.GEMINI_CUSTOM_MODEL_ID] = modelId.trim()
        }
    }

    suspend fun setOpenRouterModelId(modelId: String) {
        context.transcribeDataStore.edit { prefs ->
            prefs[Keys.OPENROUTER_MODEL_ID] = modelId.trim()
        }
    }

    suspend fun saveApiKey(provider: AiProvider, apiKey: String) {
        context.transcribeDataStore.edit { prefs ->
            val trimmed = apiKey.trim()
            when (provider) {
                AiProvider.GEMINI -> prefs[Keys.GEMINI_API_KEY] = trimmed
                AiProvider.GROQ -> prefs[Keys.GROQ_API_KEY] = trimmed
                AiProvider.OPENROUTER -> prefs[Keys.OPENROUTER_API_KEY] = trimmed
                AiProvider.OPENAI -> prefs[Keys.OPENAI_API_KEY] = trimmed
            }
        }
    }

    suspend fun removeApiKey(provider: AiProvider) {
        saveApiKey(provider, "")
    }

    suspend fun setDefaultRetention(option: RetentionOption) {
        context.transcribeDataStore.edit { prefs ->
            prefs[Keys.DEFAULT_RETENTION] = option.name
        }
    }

    suspend fun setAudioSaveBehavior(behavior: AudioSaveBehavior) {
        context.transcribeDataStore.edit { prefs ->
            prefs[Keys.AUDIO_SAVE_BEHAVIOR] = behavior.name
        }
    }

    suspend fun setDefaultVideoPreset(preset: VideoCompressPreset) {
        context.transcribeDataStore.edit { prefs ->
            prefs[Keys.DEFAULT_VIDEO_PRESET] = preset.name
        }
    }

    suspend fun setDefaultFixHdr(enabled: Boolean) {
        context.transcribeDataStore.edit { prefs ->
            prefs[Keys.DEFAULT_FIX_HDR] = enabled
        }
    }

    suspend fun setDefaultAudioPreset(preset: AudioCompressPreset) {
        context.transcribeDataStore.edit { prefs ->
            prefs[Keys.DEFAULT_AUDIO_PRESET] = preset.name
        }
    }

    suspend fun addAutoCorrectRule(wrong: String, correct: String) {
        val cleanWrong = wrong.trim()
        val cleanCorrect = correct.trim()
        if (cleanWrong.isEmpty() || cleanCorrect.isEmpty()) return

        context.transcribeDataStore.edit { prefs ->
            val current = prefs[Keys.AUTO_CORRECT_RULES_JSON]?.let {
                runCatching { rulesAdapter.fromJson(it) }.getOrNull()
            }.orEmpty().toMutableList()
            current.removeAll { it.wrong.equals(cleanWrong, ignoreCase = true) }
            current.add(0, AutoCorrectRule(UUID.randomUUID().toString(), cleanWrong, cleanCorrect))
            prefs[Keys.AUTO_CORRECT_RULES_JSON] = rulesAdapter.toJson(current)
        }
    }

    suspend fun removeAutoCorrectRule(ruleId: String) {
        context.transcribeDataStore.edit { prefs ->
            val current = prefs[Keys.AUTO_CORRECT_RULES_JSON]?.let {
                runCatching { rulesAdapter.fromJson(it) }.getOrNull()
            }.orEmpty().filterNot { it.id == ruleId }
            prefs[Keys.AUTO_CORRECT_RULES_JSON] = rulesAdapter.toJson(current)
        }
    }

    suspend fun mergeAutoCorrectRules(imported: List<AutoCorrectRule>) {
        if (imported.isEmpty()) return
        context.transcribeDataStore.edit { prefs ->
            val current = prefs[Keys.AUTO_CORRECT_RULES_JSON]?.let {
                runCatching { rulesAdapter.fromJson(it) }.getOrNull()
            }.orEmpty().toMutableList()
            imported.forEach { rule ->
                if (rule.wrong.isNotBlank() && rule.correct.isNotBlank()) {
                    current.removeAll { it.wrong.equals(rule.wrong, ignoreCase = true) }
                    current.add(rule.copy(id = rule.id.ifBlank { UUID.randomUUID().toString() }))
                }
            }
            prefs[Keys.AUTO_CORRECT_RULES_JSON] = rulesAdapter.toJson(current)
        }
    }

    suspend fun recordUsage(tokensUsed: Long) {
        val today = todayDateKey()
        context.transcribeDataStore.edit { prefs ->
            val storedDate = prefs[Keys.USAGE_DATE] ?: ""
            if (storedDate != today) {
                prefs[Keys.USAGE_DATE] = today
                prefs[Keys.USAGE_REQUESTS] = 1
                prefs[Keys.USAGE_TOKENS] = tokensUsed.coerceAtLeast(0L)
            } else {
                prefs[Keys.USAGE_REQUESTS] = (prefs[Keys.USAGE_REQUESTS] ?: 0) + 1
                prefs[Keys.USAGE_TOKENS] = (prefs[Keys.USAGE_TOKENS] ?: 0L) + tokensUsed.coerceAtLeast(0L)
            }
        }
    }

    suspend fun resetDailyUsage() {
        val today = todayDateKey()
        context.transcribeDataStore.edit { prefs ->
            prefs[Keys.USAGE_DATE] = today
            prefs[Keys.USAGE_REQUESTS] = 0
            prefs[Keys.USAGE_TOKENS] = 0L
        }
    }

    suspend fun ensureDailyResetOnLaunch() {
        val today = todayDateKey()
        context.transcribeDataStore.edit { prefs ->
            val storedDate = prefs[Keys.USAGE_DATE] ?: ""
            if (storedDate != today) {
                prefs[Keys.USAGE_DATE] = today
                prefs[Keys.USAGE_REQUESTS] = 0
                prefs[Keys.USAGE_TOKENS] = 0L
            }
        }
    }

    val cachedRemoteConfigJsonFlow: Flow<String?> = context.transcribeDataStore.data.map { prefs ->
        prefs[Keys.REMOTE_CONFIG_JSON]
    }

    val dismissedUpdateVersionFlow: Flow<String?> = context.transcribeDataStore.data.map { prefs ->
        prefs[Keys.DISMISSED_UPDATE_VERSION]
    }

    suspend fun saveCachedRemoteConfigJson(rawJson: String) {
        context.transcribeDataStore.edit { prefs ->
            prefs[Keys.REMOTE_CONFIG_JSON] = rawJson
        }
    }

    suspend fun setDismissedUpdateVersion(version: String) {
        context.transcribeDataStore.edit { prefs ->
            prefs[Keys.DISMISSED_UPDATE_VERSION] = version.trim()
        }
    }
}
