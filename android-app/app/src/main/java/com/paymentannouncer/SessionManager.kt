package com.paymentannouncer

import android.content.Context

object SessionManager {
    private const val PREFS = "payment_announcer_prefs"
    private const val KEY_TOKEN = "driver_token"
    private const val KEY_LANG = "voice_lang"
    private const val KEY_SOUND_MODE = "sound_mode"

    fun saveToken(context: Context, token: String) {
        prefs(context).edit().putString(KEY_TOKEN, token).apply()
    }

    fun getToken(context: Context): String? = prefs(context).getString(KEY_TOKEN, null)

    fun bearer(context: Context): String = "Bearer ${getToken(context)}"

    fun clear(context: Context) {
        prefs(context).edit().remove(KEY_TOKEN).apply()
    }

    fun isLoggedIn(context: Context): Boolean = getToken(context) != null

    fun saveLanguage(context: Context, lang: String) {
        prefs(context).edit().putString(KEY_LANG, lang).apply()
    }

    fun getLanguage(context: Context): String = prefs(context).getString(KEY_LANG, "en") ?: "en"

    // "voice" = speak the name aloud, "beep" = notification sound only, "silent" = no sound
    fun saveSoundMode(context: Context, mode: String) {
        prefs(context).edit().putString(KEY_SOUND_MODE, mode).apply()
    }

    fun getSoundMode(context: Context): String = prefs(context).getString(KEY_SOUND_MODE, "voice") ?: "voice"

    private fun prefs(context: Context) =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
}
