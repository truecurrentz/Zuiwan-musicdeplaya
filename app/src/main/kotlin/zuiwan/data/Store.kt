package zuiwan.data

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import zuiwan.model.AudioEffects
import zuiwan.model.EqProfile
import zuiwan.model.FontChoice
import zuiwan.model.QualityMode
import zuiwan.model.SessionData
import zuiwan.model.ShaderPreset
import zuiwan.model.ThemeChoice
import zuiwan.model.VisualMode

private val Context.zuiwanPrefs: DataStore<Preferences> by preferencesDataStore(name = "zuiwan_prefs")

@Serializable
data class AppSettings(
    val theme: ThemeChoice = ThemeChoice.DARK,
    val dynamicColors: Boolean = true,
    val blurEnabled: Boolean = true,
    val quality: QualityMode = QualityMode.HIGH,
    val normalizeAudio: Boolean = true,
    val skipSilence: Boolean = false,
    val crossfadeSec: Int = 0,
    val speed: Float = 1.0f,
    val pitch: Float = 1.0f,
    val persistentQueue: Boolean = true,
    val autoRadio: Boolean = false,
    val pauseHistory: Boolean = false,
    val romanizeLyrics: Boolean = true,
    val contentCountry: String = "US",
    val contentLanguage: String = "en",
    val checkUpdates: Boolean = true,
    val updateNotifications: Boolean = true,
    val nightlyUpdates: Boolean = false,
    val dismissedUpdateTag: String = "",
    val lastNotifiedTag: String = "",
    val customAccentHex: String = "",
    val wallpaperPath: String = "",
    val motionBgPath: String = "",
    val motionIsVideo: Boolean = false,
    val bannerPath: String = "",
    val playerBgPath: String = "",
    val blurRadius: Float = 64f,
    val glassOpacity: Float = 1.0f,
    val bgDim: Float = 0.56f,
    val fontChoice: FontChoice = FontChoice.SANS,
    val customFontPath: String = "",
    val customCss: String = "",
    val compactMode: Boolean = false,
    val fullscreenMode: Boolean = false,
    val syllableLyrics: Boolean = true,
    val visualMode: VisualMode = VisualMode.SHADER,
    val shaderPreset: ShaderPreset = ShaderPreset.AURORA,
    val playerVisualBg: Boolean = false,
    val enabledPlugins: Set<String> = emptySet(),
)

class PrefStore(private val context: Context) {
    private val json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
        coerceInputValues = true
    }

    private val settingsKey = stringPreferencesKey("settings_json")
    private val sessionKey = stringPreferencesKey("session_json")
    private val eqKey = stringPreferencesKey("eq_json")
    private val effectsKey = stringPreferencesKey("effects_json")

    val settings: Flow<AppSettings> =
        context.zuiwanPrefs.data.map { prefs ->
            prefs[settingsKey]?.let {
                runCatching { json.decodeFromString<AppSettings>(it) }.getOrNull()
            } ?: AppSettings()
        }

    val session: Flow<SessionData> =
        context.zuiwanPrefs.data.map { prefs ->
            prefs[sessionKey]?.let {
                runCatching { json.decodeFromString<SessionData>(it) }.getOrNull()
            } ?: SessionData()
        }

    val eq: Flow<EqProfile> =
        context.zuiwanPrefs.data.map { prefs ->
            prefs[eqKey]?.let {
                runCatching { json.decodeFromString<EqProfile>(it) }.getOrNull()
            } ?: EqProfile()
        }

    val effects: Flow<AudioEffects> =
        context.zuiwanPrefs.data.map { prefs ->
            prefs[effectsKey]?.let {
                runCatching { json.decodeFromString<AudioEffects>(it) }.getOrNull()
            } ?: AudioEffects()
        }

    suspend fun updatesettings(transform: (AppSettings) -> AppSettings) {
        context.zuiwanPrefs.edit { prefs ->
            val cur = prefs[settingsKey]?.let {
                runCatching { json.decodeFromString<AppSettings>(it) }.getOrNull()
            } ?: AppSettings()
            prefs[settingsKey] = json.encodeToString(transform(cur))
        }
    }

    suspend fun updatesession(transform: (SessionData) -> SessionData) {
        context.zuiwanPrefs.edit { prefs ->
            val cur = prefs[sessionKey]?.let {
                runCatching { json.decodeFromString<SessionData>(it) }.getOrNull()
            } ?: SessionData()
            prefs[sessionKey] = json.encodeToString(transform(cur))
        }
    }

    suspend fun saveeq(profile: EqProfile) {
        context.zuiwanPrefs.edit { prefs ->
            prefs[eqKey] = json.encodeToString(profile)
        }
    }

    suspend fun saveeffects(fx: AudioEffects) {
        context.zuiwanPrefs.edit { prefs ->
            prefs[effectsKey] = json.encodeToString(fx)
        }
    }
}
