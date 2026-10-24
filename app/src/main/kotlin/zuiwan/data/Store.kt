package zuiwan.data

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.floatPreferencesKey
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import zuiwan.model.AccentChoice
import zuiwan.model.EqPreset
import zuiwan.model.QualityMode
import zuiwan.model.SessionData
import zuiwan.model.ThemeChoice
import zuiwan.model.Track
import zuiwan.model.defaultpresets

private val Context.zuiwanStore: DataStore<Preferences> by preferencesDataStore(name = "zuiwan_prefs")

@Serializable
data class QueueSnapshot(
    val tracks: List<Track> = emptyList(),
    val index: Int = 0,
    val positionMs: Long = 0L,
    val title: String? = null,
    val shuffle: Boolean = false,
    val repeatMode: Int = 0,
)

@Serializable
data class UserSettings(
    val theme: ThemeChoice = ThemeChoice.DARK,
    val accent: AccentChoice = AccentChoice.DYNAMIC,
    val glassIntensity: Float = 0.78f,
    val quality: QualityMode = QualityMode.HIGH,
    val normalize: Boolean = true,
    val skipSilence: Boolean = false,
    val crossfadeSec: Float = 0f,
    val speed: Float = 1.0f,
    val pitch: Float = 1.0f,
    val eqEnabled: Boolean = false,
    val eqPresetId: String = "flat",
    val customPreset: EqPreset? = null,
    val persistQueue: Boolean = true,
    val autoplay: Boolean = true,
    val pauseHistory: Boolean = false,
    val hideExplicit: Boolean = false,
    val romanizeLyrics: Boolean = true,
    val lyricsScale: Float = 1.0f,
    val regionCode: String = "US",
    val languageCode: String = "en",
    val scrobbleEnabled: Boolean = false,
    val lastfmSession: String = "",
    val lastfmUsername: String = "",
)

class PrefStore(private val context: Context) {
    private val json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
    }

    private object Keys {
        val THEME = stringPreferencesKey("theme")
        val ACCENT = stringPreferencesKey("accent")
        val GLASS = floatPreferencesKey("glass_intensity")
        val QUALITY = stringPreferencesKey("quality")
        val NORMALIZE = booleanPreferencesKey("normalize")
        val SKIP_SILENCE = booleanPreferencesKey("skip_silence")
        val CROSSFADE = floatPreferencesKey("crossfade_sec")
        val SPEED = floatPreferencesKey("speed")
        val PITCH = floatPreferencesKey("pitch")
        val EQ_ENABLED = booleanPreferencesKey("eq_enabled")
        val EQ_PRESET = stringPreferencesKey("eq_preset")
        val EQ_CUSTOM = stringPreferencesKey("eq_custom")
        val PERSIST_QUEUE = booleanPreferencesKey("persist_queue")
        val AUTOPLAY = booleanPreferencesKey("autoplay")
        val PAUSE_HISTORY = booleanPreferencesKey("pause_history")
        val HIDE_EXPLICIT = booleanPreferencesKey("hide_explicit")
        val ROMANIZE = booleanPreferencesKey("romanize_lyrics")
        val LYRICS_SCALE = floatPreferencesKey("lyrics_scale")
        val REGION = stringPreferencesKey("region_code")
        val LANGUAGE = stringPreferencesKey("language_code")
        val SESSION = stringPreferencesKey("yt_session")
        val QUEUE = stringPreferencesKey("saved_queue")
        val SCROBBLE = booleanPreferencesKey("scrobble_enabled")
        val LASTFM_SESSION = stringPreferencesKey("lastfm_session")
        val LASTFM_USER = stringPreferencesKey("lastfm_user")
        val CACHE_LIMIT_MB = intPreferencesKey("cache_limit_mb")
        val UPDATED_AT = longPreferencesKey("updated_at")
    }

    val settings: Flow<UserSettings> =
        context.zuiwanStore.data.map { prefs ->
            UserSettings(
                theme = runCatching { ThemeChoice.valueOf(prefs[Keys.THEME] ?: "") }.getOrDefault(ThemeChoice.DARK),
                accent = runCatching { AccentChoice.valueOf(prefs[Keys.ACCENT] ?: "") }.getOrDefault(AccentChoice.DYNAMIC),
                glassIntensity = prefs[Keys.GLASS] ?: 0.78f,
                quality = runCatching { QualityMode.valueOf(prefs[Keys.QUALITY] ?: "") }.getOrDefault(QualityMode.HIGH),
                normalize = prefs[Keys.NORMALIZE] ?: true,
                skipSilence = prefs[Keys.SKIP_SILENCE] ?: false,
                crossfadeSec = prefs[Keys.CROSSFADE] ?: 0f,
                speed = prefs[Keys.SPEED] ?: 1.0f,
                pitch = prefs[Keys.PITCH] ?: 1.0f,
                eqEnabled = prefs[Keys.EQ_ENABLED] ?: false,
                eqPresetId = prefs[Keys.EQ_PRESET] ?: "flat",
                customPreset = prefs[Keys.EQ_CUSTOM]?.let {
                    runCatching { json.decodeFromString<EqPreset>(it) }.getOrNull()
                },
                persistQueue = prefs[Keys.PERSIST_QUEUE] ?: true,
                autoplay = prefs[Keys.AUTOPLAY] ?: true,
                pauseHistory = prefs[Keys.PAUSE_HISTORY] ?: false,
                hideExplicit = prefs[Keys.HIDE_EXPLICIT] ?: false,
                romanizeLyrics = prefs[Keys.ROMANIZE] ?: true,
                lyricsScale = prefs[Keys.LYRICS_SCALE] ?: 1.0f,
                regionCode = prefs[Keys.REGION] ?: "US",
                languageCode = prefs[Keys.LANGUAGE] ?: "en",
                scrobbleEnabled = prefs[Keys.SCROBBLE] ?: false,
                lastfmSession = prefs[Keys.LASTFM_SESSION] ?: "",
                lastfmUsername = prefs[Keys.LASTFM_USER] ?: "",
            )
        }

    val session: Flow<SessionData> =
        context.zuiwanStore.data.map { prefs ->
            prefs[Keys.SESSION]?.let {
                runCatching { json.decodeFromString<SessionData>(it) }.getOrNull()
            } ?: SessionData()
        }

    suspend fun currentsettings(): UserSettings = settings.first()

    suspend fun currentsession(): SessionData = session.first()

    suspend fun settheme(choice: ThemeChoice) {
        context.zuiwanStore.edit { it[Keys.THEME] = choice.name }
    }

    suspend fun setaccent(choice: AccentChoice) {
        context.zuiwanStore.edit { it[Keys.ACCENT] = choice.name }
    }

    suspend fun setglass(value: Float) {
        context.zuiwanStore.edit { it[Keys.GLASS] = value.coerceIn(0.4f, 1.0f) }
    }

    suspend fun setquality(mode: QualityMode) {
        context.zuiwanStore.edit { it[Keys.QUALITY] = mode.name }
    }

    suspend fun setnormalize(enabled: Boolean) {
        context.zuiwanStore.edit { it[Keys.NORMALIZE] = enabled }
    }

    suspend fun setskipsilence(enabled: Boolean) {
        context.zuiwanStore.edit { it[Keys.SKIP_SILENCE] = enabled }
    }

    suspend fun setcrossfade(seconds: Float) {
        context.zuiwanStore.edit { it[Keys.CROSSFADE] = seconds.coerceIn(0f, 12f) }
    }

    suspend fun setspeed(speed: Float) {
        context.zuiwanStore.edit { it[Keys.SPEED] = speed.coerceIn(0.5f, 2.0f) }
    }

    suspend fun setpitch(pitch: Float) {
        context.zuiwanStore.edit { it[Keys.PITCH] = pitch.coerceIn(0.5f, 2.0f) }
    }

    suspend fun seteqenabled(enabled: Boolean) {
        context.zuiwanStore.edit { it[Keys.EQ_ENABLED] = enabled }
    }

    suspend fun seteqpreset(presetId: String, custom: EqPreset? = null) {
        context.zuiwanStore.edit {
            it[Keys.EQ_PRESET] = presetId
            if (custom != null) {
                it[Keys.EQ_CUSTOM] = json.encodeToString(custom)
            }
        }
    }

    suspend fun setpersistqueue(enabled: Boolean) {
        context.zuiwanStore.edit { it[Keys.PERSIST_QUEUE] = enabled }
    }

    suspend fun setautoplay(enabled: Boolean) {
        context.zuiwanStore.edit { it[Keys.AUTOPLAY] = enabled }
    }

    suspend fun setpausehistory(enabled: Boolean) {
        context.zuiwanStore.edit { it[Keys.PAUSE_HISTORY] = enabled }
    }

    suspend fun sethideexplicit(enabled: Boolean) {
        context.zuiwanStore.edit { it[Keys.HIDE_EXPLICIT] = enabled }
    }

    suspend fun setromanize(enabled: Boolean) {
        context.zuiwanStore.edit { it[Keys.ROMANIZE] = enabled }
    }

    suspend fun setlyricsscale(scale: Float) {
        context.zuiwanStore.edit { it[Keys.LYRICS_SCALE] = scale.coerceIn(0.8f, 1.5f) }
    }

    suspend fun setlocale(region: String, language: String) {
        context.zuiwanStore.edit {
            it[Keys.REGION] = region.uppercase().ifBlank { "US" }
            it[Keys.LANGUAGE] = language.lowercase().ifBlank { "en" }
        }
    }

    suspend fun savesession(data: SessionData) {
        context.zuiwanStore.edit {
            it[Keys.SESSION] = json.encodeToString(data)
            it[Keys.UPDATED_AT] = System.currentTimeMillis()
        }
    }

    suspend fun clearsession() {
        context.zuiwanStore.edit { it.remove(Keys.SESSION) }
    }

    suspend fun setlastfm(enabled: Boolean, sessionKey: String, username: String) {
        context.zuiwanStore.edit {
            it[Keys.SCROBBLE] = enabled
            it[Keys.LASTFM_SESSION] = sessionKey
            it[Keys.LASTFM_USER] = username
        }
    }

    suspend fun queuesave(snapshot: QueueSnapshot) {
        context.zuiwanStore.edit {
            it[Keys.QUEUE] = json.encodeToString(snapshot)
        }
    }

    suspend fun queueload(): QueueSnapshot? {
        val prefs = context.zuiwanStore.data.first()
        val raw = prefs[Keys.QUEUE] ?: return null
        return runCatching { json.decodeFromString<QueueSnapshot>(raw) }.getOrNull()
    }

    fun activepreset(current: UserSettings): EqPreset {
        if (current.eqPresetId == "custom" && current.customPreset != null) {
            return current.customPreset
        }
        return defaultpresets().firstOrNull { it.id == current.eqPresetId } ?: defaultpresets().first()
    }
}
