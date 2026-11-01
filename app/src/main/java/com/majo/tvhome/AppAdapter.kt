package com.majo.tvhome

import android.graphics.drawable.GradientDrawable
import android.view.KeyEvent
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView

private const val CARD_BASE = 0xFF1C1C1F.toInt()

/** Adaptador de tarjetas de apps; se usa tanto para la fila de favoritas como para la cuadrícula. */
class AppAdapter(
    private val cardW: Int,
    private val cardH: Int,
    private val focusScale: Float,
    private val listener: Listener
) : RecyclerView.Adapter<AppAdapter.VH>() {

    interface Listener {
        fun onAppClick(app: AppEntry)
        fun onAppMenu(app: AppEntry)
    }

    var items: List<AppEntry> = emptyList()
        private set

    /** App que se está reordenando (muestra su nombre con flechas). */
    var movingKey: String? = null

    init {
        setHasStableIds(true)
    }

    fun submit(list: List<AppEntry>) {
        items = list
        notifyDataSetChanged()
    }

    /** Cambia la lista sin refrescar; el llamador notifica el movimiento concreto. */
    fun replaceSilently(list: List<AppEntry>) {
        items = list
    }

    fun indexOf(key: String) = items.indexOfFirst { it.key == key }

    override fun getItemCount() = items.size

    override fun getItemId(position: Int) = StableIds.of(items[position].key)

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH {
        val v = LayoutInflater.from(parent.context).inflate(R.layout.item_app, parent, false)
        v.layoutParams = RecyclerView.LayoutParams(cardW, ViewGroup.LayoutParams.WRAP_CONTENT)
        v.noDefaultHighlight()
        val vh = VH(v)
        vh.card.layoutParams = vh.card.layoutParams.apply { width = cardW; height = cardH }
        vh.card.outlineProvider = RoundedOutline(parent.context.dpf(12f))
        vh.card.clipToOutline = true

        v.setOnClickListener { vh.app?.let(listener::onAppClick) }
        v.setOnLongClickListener { vh.app?.let(listener::onAppMenu); true }
        v.setOnKeyListener { _, keyCode, event ->
            if (keyCode == KeyEvent.KEYCODE_MENU) {
                if (event.action == KeyEvent.ACTION_UP) vh.app?.let(listener::onAppMenu)
                true
            } else false
        }
        v.setOnFocusChangeListener { _, hasFocus -> vh.applyFocus(hasFocus, true) }
        return vh
    }

    override fun onBindViewHolder(holder: VH, position: Int) {
        val app = items[position]
        holder.app = app
        holder.label.text = if (app.key == movingKey) "◀   ${app.label}   ▶" else app.label
        holder.itemView.contentDescription = app.label

        val cached = IconLoader.cached(app, cardW)
        if (cached != null) {
            holder.show(cached)
        } else {
            holder.showPlaceholder()
            IconLoader.load(holder.itemView.context, app, cardW, cardH) { art ->
                if (holder.app?.key == app.key) holder.show(art)
            }
        }
        holder.applyFocus(holder.itemView.isFocused, false)
    }

    inner class VH(v: View) : RecyclerView.ViewHolder(v) {
        val card: FrameLayout = v.findViewById(R.id.card)
        val image: ImageView = v.findViewById(R.id.image)
        val sheen: View = v.findViewById(R.id.sheen)
        val label: TextView = v.findViewById(R.id.label)
        var app: AppEntry? = null

        fun applyFocus(focused: Boolean, animate: Boolean) {
            val moving = app != null && app?.key == movingKey
            FocusAnim.card(itemView, card, sheen, label, focused, focusScale, animate, forceLabel = moving)
        }

        fun showPlaceholder() {
            image.setImageDrawable(null)
            card.setBackgroundColor(CARD_BASE)
        }

        fun show(art: CardArt) {
            if (art.isBanner) {
                card.setBackgroundColor(CARD_BASE)
                image.setPadding(0, 0, 0, 0)
                image.scaleType = ImageView.ScaleType.CENTER_CROP
            } else {
                card.background = GradientDrawable(
                    GradientDrawable.Orientation.TOP_BOTTOM,
                    intArrayOf(IconLoader.shade(art.color, 1.18f), IconLoader.shade(art.color, 0.72f))
                )
                val pad = (cardH * 0.22f).toInt()
                image.setPadding(pad, pad, pad, pad)
                image.scaleType = ImageView.ScaleType.FIT_CENTER
            }
            image.setImageBitmap(art.bitmap)
        }
    }
}
