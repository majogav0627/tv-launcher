package com.majo.tvhome

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.ActivityInfo
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import android.content.pm.ResolveInfo
import android.os.Build
import java.text.Collator
import java.util.Locale

/** Una app que se puede abrir. La clave es el nombre de paquete (una entrada por app). */
class AppEntry(
    val key: String,
    val label: String,
    val activityInfo: ActivityInfo,
    val isLeanback: Boolean
) {
    val packageName: String get() = activityInfo.packageName

    fun launchIntent(): Intent =
        Intent(Intent.ACTION_MAIN)
            .addCategory(if (isLeanback) Intent.CATEGORY_LEANBACK_LAUNCHER else Intent.CATEGORY_LAUNCHER)
            .setComponent(ComponentName(activityInfo.packageName, activityInfo.name))
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED)

    fun sameAs(other: AppEntry) = key == other.key && label == other.label &&
        activityInfo.name == other.activityInfo.name && isLeanback == other.isLeanback
}

object AppRepository {

    /** Lista apps de TV (LEANBACK_LAUNCHER) y de móvil (LAUNCHER), sin duplicados y sin este launcher. */
    fun load(ctx: Context): List<AppEntry> {
        val pm = ctx.packageManager
        val result = LinkedHashMap<String, AppEntry>()

        fun add(category: String, leanback: Boolean) {
            val intent = Intent(Intent.ACTION_MAIN).addCategory(category)
            for (ri in query(pm, intent)) {
                val ai = ri.activityInfo ?: continue
                val pkg = ai.packageName
                if (pkg == ctx.packageName || result.containsKey(pkg)) continue
                val label = try {
                    ri.loadLabel(pm).toString().trim()
                } catch (_: Exception) {
                    ""
                }.ifEmpty { pkg }
                result[pkg] = AppEntry(pkg, label, ai, leanback)
            }
        }
        // Primero las de TV: si una app tiene ambas, se usa su entrada de TV
        add(Intent.CATEGORY_LEANBACK_LAUNCHER, true)
        add(Intent.CATEGORY_LAUNCHER, false)

        val collator = Collator.getInstance(Locale.forLanguageTag("es-ES")).apply {
            strength = Collator.PRIMARY
        }
        return result.values.sortedWith { a, b -> collator.compare(a.label, b.label) }
    }

    private fun query(pm: PackageManager, intent: Intent): List<ResolveInfo> =
        if (Build.VERSION.SDK_INT >= 33) {
            pm.queryIntentActivities(intent, PackageManager.ResolveInfoFlags.of(0L))
        } else {
            @Suppress("DEPRECATION")
            pm.queryIntentActivities(intent, 0)
        }

    private val preferredFavorites = listOf(
        "com.google.android.youtube.tv",
        "com.netflix.ninja",
        "com.amazon.amazonvideo.livingroom",
        "com.disney.disneyplus",
        "com.hbo.hbonow",
        "com.wbd.stream",
        "com.spotify.tv.android",
        "com.google.android.youtube.tvmusic",
        "com.plexapp.android",
        "org.xbmc.kodi"
    )

    /** Favoritas iniciales la primera vez: apps populares instaladas y, si faltan, otras apps de TV. */
    fun defaultFavorites(apps: List<AppEntry>): List<String> {
        val byKey = apps.associateBy { it.key }
        val picks = preferredFavorites.filter { byKey.containsKey(it) }.take(6).toMutableList()
        if (picks.size < 4) {
            for (app in apps) {
                if (picks.size >= 5) break
                val isSystem = (app.activityInfo.applicationInfo.flags and ApplicationInfo.FLAG_SYSTEM) != 0
                if (app.isLeanback && !isSystem && app.key !in picks) picks.add(app.key)
            }
        }
        return picks
    }
}
