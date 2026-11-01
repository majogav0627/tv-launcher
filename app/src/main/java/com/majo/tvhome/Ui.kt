package com.majo.tvhome

import android.content.Context
import android.content.res.ColorStateList
import android.graphics.Outline
import android.graphics.Rect
import android.os.Build
import android.view.View
import android.view.ViewOutlineProvider
import android.view.ViewTreeObserver
import android.view.animation.PathInterpolator
import androidx.recyclerview.widget.RecyclerView

fun Context.dp(value: Int): Int = (value * resources.displayMetrics.density + 0.5f).toInt()
fun Context.dpf(value: Float): Float = value * resources.displayMetrics.density

fun Context.colorStateList(id: Int): ColorStateList =
    if (Build.VERSION.SDK_INT >= 23) getColorStateList(id)
    else @Suppress("DEPRECATION") resources.getColorStateList(id)

/** Recorta una vista con esquinas redondeadas (también proyecta la sombra con esa forma). */
class RoundedOutline(private val radius: Float) : ViewOutlineProvider() {
    override fun getOutline(view: View, outline: Outline) {
        outline.setRoundRect(0, 0, view.width, view.height, radius)
    }
}

/** IDs estables por app: permiten que RecyclerView conserve el foco al refrescar la lista. */
object StableIds {
    private val ids = HashMap<String, Long>()
    private var next = 1L
    fun of(key: String): Long = ids.getOrPut(key) { next++ }
}

/** Quita el resaltado gris que Android 8+ pinta por defecto sobre las vistas enfocadas. */
fun View.noDefaultHighlight() {
    if (Build.VERSION.SDK_INT >= 26) defaultFocusHighlightEnabled = false
}

/** Ejecuta [block] tras el próximo pase de layout de la vista. */
fun View.afterNextLayout(block: () -> Unit) {
    val observer = viewTreeObserver
    observer.addOnGlobalLayoutListener(object : ViewTreeObserver.OnGlobalLayoutListener {
        override fun onGlobalLayout() {
            if (observer.isAlive) observer.removeOnGlobalLayoutListener(this)
            else viewTreeObserver.removeOnGlobalLayoutListener(this)
            block()
        }
    })
    requestLayout()
}

object FocusAnim {
    // Curva suave tipo "ease-out" (rápida al principio, se asienta al final)
    private val ease = PathInterpolator(0.2f, 0.0f, 0.0f, 1.0f)

    /** Animación de foco de una tarjeta: escala, sombra, brillo y etiqueta. */
    fun card(
        root: View, card: View, sheen: View, label: View,
        focused: Boolean, scale: Float, animate: Boolean, forceLabel: Boolean = false
    ) {
        val ctx = card.context
        val s = if (focused) scale else 1f
        val z = if (focused) ctx.dpf(18f) else 0f
        val cardH = if (card.height > 0) card.height else card.layoutParams.height
        val labelShift = if (focused) cardH * (scale - 1f) / 2f else 0f
        val labelAlpha = if (focused || forceLabel) 1f else 0f
        // El elemento enfocado se dibuja por encima de sus vecinos
        root.translationZ = if (focused) 1f else 0f
        if (animate) {
            val d = if (focused) 220L else 160L
            card.animate().scaleX(s).scaleY(s).translationZ(z).setDuration(d).setInterpolator(ease).start()
            sheen.animate().alpha(if (focused) 1f else 0f).setDuration(d).setInterpolator(ease).start()
            label.animate().alpha(labelAlpha).translationY(labelShift).setDuration(d).setInterpolator(ease).start()
        } else {
            card.animate().cancel(); sheen.animate().cancel(); label.animate().cancel()
            card.scaleX = s; card.scaleY = s; card.translationZ = z
            sheen.alpha = if (focused) 1f else 0f
            label.alpha = labelAlpha; label.translationY = labelShift
        }
    }

    /** Animación sencilla de escala para botones y filas. */
    fun scale(view: View, focused: Boolean, scale: Float) {
        view.animate().scaleX(if (focused) scale else 1f).scaleY(if (focused) scale else 1f)
            .setDuration(if (focused) 200L else 150L).setInterpolator(ease).start()
    }
}

class HorizontalSpacing(private val gap: Int) : RecyclerView.ItemDecoration() {
    override fun getItemOffsets(outRect: Rect, view: View, parent: RecyclerView, state: RecyclerView.State) {
        val pos = parent.getChildAdapterPosition(view)
        outRect.set(0, 0, if (pos == state.itemCount - 1) 0 else gap, 0)
    }
}

class GridSpacing(private val span: Int, private val hGap: Int, private val vGap: Int) :
    RecyclerView.ItemDecoration() {
    override fun getItemOffsets(outRect: Rect, view: View, parent: RecyclerView, state: RecyclerView.State) {
        val pos = parent.getChildAdapterPosition(view)
        if (pos == RecyclerView.NO_POSITION) return
        val col = pos % span
        outRect.left = col * hGap / span
        outRect.right = hGap - (col + 1) * hGap / span
        outRect.bottom = vGap
    }
}
