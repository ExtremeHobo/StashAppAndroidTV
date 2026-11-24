package com.github.damontecres.stashapp.util

import android.content.Context
import androidx.core.content.edit
import androidx.preference.PreferenceManager
import com.github.damontecres.stashapp.R
import com.github.damontecres.stashapp.proto.PinPreferences
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking

/**
 * PIN / presence prefs live in Proto DataStore (Compose UI) and optionally SharedPreferences
 * (legacy leanback). Initial setup historically wrote PIN only to DataStore while
 * [com.github.damontecres.stashapp.RootActivity] read SharedPreferences — that skipped the PIN gate.
 *
 * Always prefer DataStore, then heal SharedPreferences so legacy readers stay consistent.
 */
object AppPin {
    fun pinPreferences(context: Context): PinPreferences =
        runBlocking {
            context.preferences.data.first().pinPreferences
        }

    fun hasPin(context: Context): Boolean = resolvePinCode(context).isNotNullOrBlank()

    fun resolvePinCode(context: Context): String {
        val proto = pinPreferences(context)
        val protoPin = proto.pin.trim()
        val shared =
            PreferenceManager
                .getDefaultSharedPreferences(context)
                .getString(context.getString(R.string.pref_key_pin_code), "")
                ?.trim()
                .orEmpty()
        val pin = protoPin.ifBlank { shared }
        if (pin.isBlank()) {
            return ""
        }
        // Heal whichever store is missing the PIN so both gates stay consistent.
        if (protoPin.isBlank()) {
            runBlocking {
                context.preferences.updateData {
                    it.updatePinPreferences { this.pin = pin }
                }
            }
        }
        if (shared != pin) {
            healSharedPreferences(context, proto.toBuilder().setPin(pin).build())
        }
        return pin
    }

    fun presenceConfig(context: Context): PresencePinUnlock.Config {
        val proto = pinPreferences(context)
        val config =
            PresencePinUnlock.Config(
                enabled = proto.presenceUnlockEnabled,
                haUrl =
                    proto.haUrl
                        .trim()
                        .trimEnd('/')
                        .ifBlank { PresencePinUnlock.DEFAULT_HA_URL },
                haToken = proto.haToken.trim(),
                samEntity = proto.haSamEntity.trim().ifBlank { PresencePinUnlock.DEFAULT_SAM_ENTITY },
                meghanEntity =
                    proto.haMeghanEntity.trim().ifBlank { PresencePinUnlock.DEFAULT_MEGHAN_ENTITY },
                guestEntity =
                    proto.haGuestEntity.trim().ifBlank { PresencePinUnlock.DEFAULT_GUEST_ENTITY },
            )
        // If DataStore has presence config but SharedPreferences does not (or differs), heal SP
        // so any SharedPreferences-only readers stay correct.
        healSharedPreferences(context, proto)
        return config
    }

    fun writePinToSharedPreferences(
        context: Context,
        pin: String,
        autoSubmit: Boolean? = null,
    ) {
        PreferenceManager.getDefaultSharedPreferences(context).edit {
            putString(context.getString(R.string.pref_key_pin_code), pin)
            if (autoSubmit != null) {
                putBoolean(context.getString(R.string.pref_key_pin_code_auto), autoSubmit)
            }
        }
    }

    private fun healSharedPreferences(
        context: Context,
        proto: PinPreferences,
    ) {
        PreferenceManager.getDefaultSharedPreferences(context).edit {
            putString(context.getString(R.string.pref_key_pin_code), proto.pin)
            putBoolean(
                context.getString(R.string.pref_key_pin_code_auto),
                proto.autoSubmit,
            )
            putBoolean(
                context.getString(R.string.pref_key_presence_unlock_enabled),
                proto.presenceUnlockEnabled,
            )
            putString(
                context.getString(R.string.pref_key_ha_url),
                proto.haUrl.ifBlank { PresencePinUnlock.DEFAULT_HA_URL },
            )
            putString(context.getString(R.string.pref_key_ha_token), proto.haToken)
            putString(
                context.getString(R.string.pref_key_ha_sam_entity),
                proto.haSamEntity.ifBlank { PresencePinUnlock.DEFAULT_SAM_ENTITY },
            )
            putString(
                context.getString(R.string.pref_key_ha_meghan_entity),
                proto.haMeghanEntity.ifBlank { PresencePinUnlock.DEFAULT_MEGHAN_ENTITY },
            )
            putString(
                context.getString(R.string.pref_key_ha_guest_entity),
                proto.haGuestEntity.ifBlank { PresencePinUnlock.DEFAULT_GUEST_ENTITY },
            )
        }
    }
}
