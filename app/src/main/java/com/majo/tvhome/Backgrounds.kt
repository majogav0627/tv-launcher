package com.majo.tvhome

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.drawable.Drawable
import android.graphics.drawable.GradientDrawable
import android.graphics.drawable.LayerDrawable
import android.os.Handler
import android.os.Looper
import android.widget.ImageView
import java.io.File
import java.io.InputStream
import java.util.concurrent.Executors

/** Fondos: degradados incluidos o una imagen propia guardada en la memoria interna de la app. */
object Backgrounds {
    const val IMAGE = "image"

    class Preset(val name: String, val colors: IntArray)

    private fun c(v: Long) = v.toInt()

    val presets = listOf(
        Preset("Medianoche", intArrayOf(c(0xFF0A0F1F), c(0xFF1B2545))),
        Preset("Grafito", intArrayOf(c(0xFF0C0C0E), c(0xFF2A2B30))),
        Preset("Océano", intArrayOf(c(0xFF031019), c(0xFF0A3A4F))),
        Preset("Aurora", intArrayOf(c(0xFF0F0C29), c(0xFF2E2A5E), c(0xFF1F3B4D))),
        Preset("Bosque", intArrayOf(c(0xFF06130E), c(0xFF183A2C))),
        Preset("Vino", intArrayOf(c(0xFF14070D), c(0xFF3F1527))),
        Preset("Ámbar", intArrayOf(c(0xFF120C06), c(0xFF3D2A12)))
    )

    private val executor = Executors.newSingleThreadExecutor()
    private val main = Handler(Looper.getMainLooper())
    private var cachedKey: String? = null
    private var cachedBitmap: Bitmap? = null

    fun wallpaperFile(ctx: Context) = File(ctx.filesDir, "fondo_personal")

    fun presetDrawable(ctx: Context, index: Int): Drawable {
        val p = presets[index.coerceIn(0, presets.size - 1)]
        val base = GradientDrawable(GradientDrawable.Orientation.TL_BR, p.colors)
        // Un brillo muy sutil arriba a la izquierda para dar profundidad
        val glow = GradientDrawable().apply {
            gradientType = GradientDrawable.RADIAL_GRADIENT
            colors = intArrayOf(0x1CFFFFFF, 0x00FFFFFF)
            setGradientCenter(0.18f, 0.0f)
            gradientRadius = ctx.resources.displayMetrics.widthPixels * 0.75f
        }
        return LayerDrawable(arrayOf(base, glow))
    }

    /** Pone el fondo elegido en [view]. La imagen se decodifica en segundo plano y a tamaño de pantalla. */
    fun apply(ctx: Context, view: ImageView) {
        val choice = Prefs(ctx).background
        if (choice == IMAGE) {
            val f = wallpaperFile(ctx)
            if (f.isFile) {
                val key = "img:${f.lastModified()}:${f.length()}"
                if (view.tag == key) return
                val bmp = cachedBitmap
                if (key == cachedKey && bmp != null) {
                    view.setImageBitmap(bmp); view.tag = key
                    return
                }
                val dm = ctx.resources.displayMetrics
                executor.execute {
                    val decoded = decodeSampled(f, dm.widthPixels, dm.heightPixels)
                    main.post {
                        if (decoded != null) {
                            cachedKey = key; cachedBitmap = decoded
                            view.setImageBitmap(decoded); view.tag = key
                        } else {
                            setPreset(ctx, view, 0)
                        }
                    }
                }
                return
            }
        }
        setPreset(ctx, view, choice.removePrefix("gradient:").toIntOrNull() ?: 0)
    }

    private fun setPreset(ctx: Context, view: ImageView, index: Int) {
        val key = "grad:$index"
        if (view.tag == key) return
        view.setImageDrawable(presetDrawable(ctx, index))
        view.tag = key
    }

    /** Copia una imagen al almacenamiento interno de la app. Devuelve false si no es una imagen válida. */
    fun importImage(ctx: Context, input: InputStream): Boolean {
        val tmp = File(ctx.filesDir, "fondo_tmp")
        tmp.outputStream().use { out -> input.copyTo(out) }
        val opts = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeFile(tmp.path, opts)
        if (opts.outWidth <= 0 || opts.outHeight <= 0) {
            tmp.delete()
            return false
        }
        val dest = wallpaperFile(ctx)
        dest.delete()
        if (!tmp.renameTo(dest)) {
            tmp.copyTo(dest, overwrite = true); tmp.delete()
        }
        cachedKey = null; cachedBitmap = null
        return true
    }

    fun deleteImage(ctx: Context) {
        wallpaperFile(ctx).delete()
        cachedKey = null; cachedBitmap = null
    }

    private fun decodeSampled(f: File, reqW: Int, reqH: Int): Bitmap? = try {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeFile(f.path, bounds)
        var sample = 1
        while (bounds.outWidth / (sample * 2) >= reqW && bounds.outHeight / (sample * 2) >= reqH) sample *= 2
        BitmapFactory.decodeFile(f.path, BitmapFactory.Options().apply {
            inSampleSize = sample
            inPreferredConfig = Bitmap.Config.RGB_565 // la mitad de memoria; sin transparencia no se nota
        })
    } catch (_: Throwable) {
        null
    }
}
