package com.lockin

import android.content.Context
import android.content.Intent
import android.graphics.drawable.Drawable

/**
 * The three apps that stay reachable while locked in. Each slot holds a package name chosen by
 * the user; the special value [DIALER] keeps the plain phone dialler in a slot.
 */
object AllowedApps {

    const val SLOTS = 3
    const val DIALER = "@dialer"

    private const val PREFS = "lockin_settings"
    private const val KEY = "allowed_app_"
    private val defaults = listOf("com.spotify.music", "com.whatsapp", DIALER)

    /** The chosen apps, in slot order, without blanks or duplicates. */
    fun slots(context: Context): List<String> {
        val sp = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val out = ArrayList<String>()
        for (index in 0 until SLOTS) {
            val value = sp.getString(KEY + index, defaults[index]) ?: ""
            if (value.isEmpty() || out.contains(value)) continue
            out.add(value)
        }
        return out
    }

    fun slot(context: Context, index: Int): String =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .getString(KEY + index, defaults.getOrElse(index) { "" }) ?: ""

    fun setSlot(context: Context, index: Int, packageName: String?) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
            .putString(KEY + index, packageName ?: "")
            .apply()
    }

    fun label(context: Context, value: String): String {
        if (value == DIALER) return "Phone"
        return runCatching {
            val pm = context.packageManager
            pm.getApplicationLabel(pm.getApplicationInfo(value, 0)).toString()
        }.getOrDefault(value)
    }

    fun icon(context: Context, value: String): Drawable? {
        if (value == DIALER) {
            return runCatching {
                context.packageManager.getApplicationIcon("com.android.dialer")
            }.getOrNull()
        }
        return runCatching { context.packageManager.getApplicationIcon(value) }.getOrNull()
    }

    /** Launch intent for the app, or null when it is no longer installed. */
    fun launchIntent(context: Context, value: String): Intent? {
        if (value == DIALER) {
            return Intent(Intent.ACTION_DIAL).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        return context.packageManager.getLaunchIntentForPackage(value)
    }
}
