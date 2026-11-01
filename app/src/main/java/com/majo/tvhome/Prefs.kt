package com.majo.tvhome

import android.content.Context

/** Ajustes guardados: favoritas (con su orden), apps ocultas y fondo. */
class Prefs(ctx: Context) {
    private val sp = ctx.applicationContext.getSharedPreferences("inicio", Context.MODE_PRIVATE)

    val favoritesInitialized: Boolean get() = sp.contains(KEY_FAVORITES)

    var favorites: List<String>
        get() = sp.getString(KEY_FAVORITES, null)
            ?.split('\n')?.filter { it.isNotBlank() } ?: emptyList()
        set(value) = sp.edit().putString(KEY_FAVORITES, value.distinct().joinToString("\n")).apply()

    var hidden: Set<String>
        get() = sp.getStringSet(KEY_HIDDEN, null)?.toSet() ?: emptySet()
        set(value) = sp.edit().putStringSet(KEY_HIDDEN, HashSet(value)).apply()

    /** "gradient:N" o "image" */
    var background: String
        get() = sp.getString(KEY_BACKGROUND, null) ?: "gradient:0"
        set(value) = sp.edit().putString(KEY_BACKGROUND, value).apply()

    fun reset() = sp.edit().clear().apply()

    companion object {
        private const val KEY_FAVORITES = "favorites"
        private const val KEY_HIDDEN = "hidden"
        private const val KEY_BACKGROUND = "background"
    }
}
