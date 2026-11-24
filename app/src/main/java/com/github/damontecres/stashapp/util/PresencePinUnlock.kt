package com.github.damontecres.stashapp.util

import android.content.Context
import android.content.SharedPreferences
import android.util.Log
import androidx.preference.PreferenceManager
import com.github.damontecres.stashapp.R
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import java.util.concurrent.TimeUnit

/**
 * Skips the local app PIN when Home Assistant presence matches:
 * Sam home AND Meghan not home AND guest mode off.
 *
 * Reads existing HA entities (defaults: person.sam_davis, person.meghan,
 * input_boolean.guest_mode) — no hardcoded fragile hacks.
 */
object PresencePinUnlock {
    private const val TAG = "PresencePinUnlock"

    // Prefer LAN IP on Android TV — .local / mDNS is flaky.
    const val DEFAULT_HA_URL = "http://192.168.1.23:8123"
    const val DEFAULT_SAM_ENTITY = "person.sam_davis"
    const val DEFAULT_MEGHAN_ENTITY = "person.meghan"
    const val DEFAULT_GUEST_ENTITY = "input_boolean.guest_mode"

    private val httpClient: OkHttpClient by lazy {
        OkHttpClient
            .Builder()
            .connectTimeout(3, TimeUnit.SECONDS)
            .readTimeout(3, TimeUnit.SECONDS)
            .callTimeout(5, TimeUnit.SECONDS)
            .build()
    }

    data class Config(
        val enabled: Boolean,
        val haUrl: String,
        val haToken: String,
        val samEntity: String,
        val meghanEntity: String,
        val guestEntity: String,
    ) {
        val isConfigured: Boolean
            get() = enabled && haUrl.isNotBlank() && haToken.isNotBlank()
    }

    fun configFrom(prefs: SharedPreferences, context: Context): Config {
        fun key(res: Int) = context.getString(res)
        return Config(
            enabled = prefs.getBoolean(key(R.string.pref_key_presence_unlock_enabled), false),
            haUrl =
                prefs
                    .getString(key(R.string.pref_key_ha_url), DEFAULT_HA_URL)
                    ?.trim()
                    ?.trimEnd('/')
                    ?.ifBlank { DEFAULT_HA_URL } ?: DEFAULT_HA_URL,
            haToken = prefs.getString(key(R.string.pref_key_ha_token), "")?.trim().orEmpty(),
            samEntity =
                prefs
                    .getString(key(R.string.pref_key_ha_sam_entity), DEFAULT_SAM_ENTITY)
                    ?.trim()
                    ?.ifBlank { DEFAULT_SAM_ENTITY } ?: DEFAULT_SAM_ENTITY,
            meghanEntity =
                prefs
                    .getString(key(R.string.pref_key_ha_meghan_entity), DEFAULT_MEGHAN_ENTITY)
                    ?.trim()
                    ?.ifBlank { DEFAULT_MEGHAN_ENTITY } ?: DEFAULT_MEGHAN_ENTITY,
            guestEntity =
                prefs
                    .getString(key(R.string.pref_key_ha_guest_entity), DEFAULT_GUEST_ENTITY)
                    ?.trim()
                    ?.ifBlank { DEFAULT_GUEST_ENTITY } ?: DEFAULT_GUEST_ENTITY,
        )
    }

    fun configFrom(context: Context): Config =
        configFrom(PreferenceManager.getDefaultSharedPreferences(context), context)

    /**
     * Pure rule: Sam at home, Meghan not at home, guest mode off.
     * Person entities use state "home" when home; away may be "not_home" or a zone name.
     *
     * Fail closed: null/blank/unknown states never skip the PIN. Notably, treating a missing
     * Meghan state as "away" would incorrectly unlock when presence is unknown.
     */
    fun shouldSkipPin(
        samState: String?,
        meghanState: String?,
        guestState: String?,
    ): Boolean {
        val sam = samState?.trim().orEmpty()
        val meghan = meghanState?.trim().orEmpty()
        val guest = guestState?.trim().orEmpty()
        if (sam.isEmpty() || meghan.isEmpty() || guest.isEmpty()) {
            return false
        }
        val samHome = sam.equals("home", ignoreCase = true)
        val meghanAway = !meghan.equals("home", ignoreCase = true)
        val guestOff = guest.equals("off", ignoreCase = true)
        return samHome && meghanAway && guestOff
    }

    /**
     * Returns true when presence unlock is enabled/configured and HA says skip PIN.
     * On any error or incomplete config, returns false (PIN still required).
     */
    suspend fun shouldSkipPin(context: Context): Boolean =
        withContext(Dispatchers.IO) {
            // Prefer DataStore (Compose) over SharedPreferences-only reads.
            val config = AppPin.presenceConfig(context)
            if (!config.isConfigured) {
                Log.d(TAG, "Presence unlock not configured; PIN required")
                return@withContext false
            }
            try {
                val sam = fetchState(config, config.samEntity)
                val meghan = fetchState(config, config.meghanEntity)
                val guest = fetchState(config, config.guestEntity)
                val skip = shouldSkipPin(sam, meghan, guest)
                Log.i(
                    TAG,
                    "Presence check sam=$sam meghan=$meghan guest=$guest => skipPin=$skip",
                )
                skip
            } catch (e: Exception) {
                Log.w(TAG, "Presence check failed; keeping PIN", e)
                false
            }
        }

    private fun fetchState(
        config: Config,
        entityId: String,
    ): String {
        val url = "${config.haUrl}/api/states/$entityId"
        val request =
            Request
                .Builder()
                .url(url)
                .header("Authorization", "Bearer ${config.haToken}")
                .header("Content-Type", "application/json")
                .get()
                .build()
        httpClient.newCall(request).execute().use { response ->
            if (!response.isSuccessful) {
                throw IllegalStateException("HA $entityId HTTP ${response.code}")
            }
            val body = response.body?.string().orEmpty()
            return JSONObject(body).optString("state", "")
        }
    }
}
