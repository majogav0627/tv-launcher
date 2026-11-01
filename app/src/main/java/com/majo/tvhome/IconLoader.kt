package com.majo.tvhome

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.drawable.Drawable
import android.os.Handler
import android.os.Looper
import android.os.Process
import android.util.DisplayMetrics
import android.util.LruCache
import java.util.concurrent.Executors

/** Imagen ya preparada para una tarjeta: banner a tamaño de tarjeta, o icono + color de fondo. */
class CardArt(val bitmap: Bitmap, val isBanner: Boolean, val color: Int)

/**
 * Carga banners/iconos en segundo plano y los guarda en una caché en memoria con límite de tamaño,
 * para no bloquear la interfaz ni gastar RAM de más.
 */
object IconLoader {
    private val executor = Executors.newFixedThreadPool(2) { r ->
        Thread({
            Process.setThreadPriority(Process.THREAD_PRIORITY_BACKGROUND)
            r.run()
        }, "iconos").apply { isDaemon = true }
    }
    private val main = Handler(Looper.getMainLooper())
    private val pending = HashMap<String, MutableList<(CardArt) -> Unit>>()

    // Como mucho 1/8 de la memoria de la app, y nunca más de 24 MB
    private val cache: LruCache<String, CardArt> by lazy {
        val maxKb = (Runtime.getRuntime().maxMemory() / 1024 / 8).toInt().coerceAtMost(24 * 1024)
        object : LruCache<String, CardArt>(maxKb) {
            override fun sizeOf(key: String, value: CardArt) = value.bitmap.byteCount / 1024
        }
    }

    private fun keyOf(app: AppEntry, w: Int) = "${app.key}|$w"

    fun cached(app: AppEntry, w: Int): CardArt? = cache.get(keyOf(app, w))

    /** Debe llamarse desde el hilo principal. */
    fun load(ctx: Context, app: AppEntry, w: Int, h: Int, callback: (CardArt) -> Unit) {
        val key = keyOf(app, w)
        cache.get(key)?.let { callback(it); return }
        pending[key]?.let { it.add(callback); return }
        pending[key] = mutableListOf(callback)
        val appCtx = ctx.applicationContext
        executor.execute {
            val art = try {
                render(appCtx, app, w, h)
            } catch (_: Throwable) {
                null
            }
            main.post {
                val callbacks = pending.remove(key) ?: return@post
                if (art != null) {
                    cache.put(key, art)
                    callbacks.forEach { it(art) }
                }
            }
        }
    }

    /** Olvida las imágenes de un paquete (por ejemplo, tras actualizarse). */
    fun invalidate(packageName: String) {
        for (k in cache.snapshot().keys) {
            if (k.startsWith("$packageName|")) cache.remove(k)
        }
    }

    fun trim(level: Int) {
        if (level >= 80 /* TRIM_MEMORY_COMPLETE */) cache.evictAll()
        else if (level >= 40 /* TRIM_MEMORY_BACKGROUND */) cache.trimToSize(cache.maxSize() / 2)
    }

    private fun render(ctx: Context, app: AppEntry, w: Int, h: Int): CardArt {
        val pm = ctx.packageManager
        val banner: Drawable? = try {
            app.activityInfo.loadBanner(pm)
        } catch (_: Throwable) {
            null
        }
        if (banner != null) {
            val bmp = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
            drawCenterCrop(banner, Canvas(bmp), w, h)
            return CardArt(bmp, true, Color.BLACK)
        }
        val icon = loadBestIcon(ctx, app)
        val size = (h * 0.56f).toInt().coerceAtLeast(48)
        val bmp = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
        icon.setBounds(0, 0, size, size)
        icon.draw(Canvas(bmp))
        return CardArt(bmp, false, dominantColor(bmp))
    }

    private fun loadBestIcon(ctx: Context, app: AppEntry): Drawable {
        val ai = app.activityInfo
        val pm = ctx.packageManager
        try {
            val res = pm.getResourcesForApplication(ai.applicationInfo)
            val id = ai.iconResource
            if (id != 0) {
                res.getDrawableForDensity(id, DisplayMetrics.DENSITY_XXHIGH, null)?.let { return it }
            }
        } catch (_: Throwable) {
        }
        return ai.loadIcon(pm)
    }

    private fun drawCenterCrop(d: Drawable, c: Canvas, w: Int, h: Int) {
        val iw = d.intrinsicWidth
        val ih = d.intrinsicHeight
        if (iw <= 0 || ih <= 0) {
            d.setBounds(0, 0, w, h)
        } else {
            val scale = maxOf(w.toFloat() / iw, h.toFloat() / ih)
            val dw = (iw * scale).toInt()
            val dh = (ih * scale).toInt()
            val left = (w - dw) / 2
            val top = (h - dh) / 2
            d.setBounds(left, top, left + dw, top + dh)
        }
        d.draw(c)
    }

    /** Color de fondo derivado del icono: favorece los tonos vivos y lo deja oscuro y elegante. */
    private fun dominantColor(src: Bitmap): Int {
        val small = Bitmap.createScaledBitmap(src, 24, 24, true)
        val hsv = FloatArray(3)
        var r = 0.0; var g = 0.0; var b = 0.0; var total = 0.0
        for (y in 0 until small.height) for (x in 0 until small.width) {
            val p = small.getPixel(x, y)
            if (Color.alpha(p) < 160) continue
            Color.colorToHSV(p, hsv)
            val weight = 0.15 + hsv[1] * hsv[2] * 2.0
            r += Color.red(p) * weight; g += Color.green(p) * weight; b += Color.blue(p) * weight
            total += weight
        }
        if (small != src) small.recycle()
        if (total <= 0.0) return Color.rgb(44, 44, 50)
        Color.colorToHSV(Color.rgb((r / total).toInt(), (g / total).toInt(), (b / total).toInt()), hsv)
        hsv[1] = hsv[1].coerceAtMost(0.7f)
        hsv[2] = hsv[2].coerceIn(0.28f, 0.52f)
        return Color.HSVToColor(hsv)
    }

    fun shade(color: Int, factor: Float): Int {
        val hsv = FloatArray(3)
        Color.colorToHSV(color, hsv)
        hsv[2] = (hsv[2] * factor).coerceIn(0f, 1f)
        return Color.HSVToColor(hsv)
    }
}
