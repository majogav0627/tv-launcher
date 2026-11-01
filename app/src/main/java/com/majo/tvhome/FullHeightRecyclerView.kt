package com.majo.tvhome

import android.content.Context
import android.util.AttributeSet
import androidx.recyclerview.widget.RecyclerView

/**
 * RecyclerView que, dentro de un ScrollView, crece hasta mostrar TODOS sus elementos.
 *
 * El ScrollView mide a sus hijos con altura "sin límite" pero pasando como tamaño la altura de la
 * pantalla; RecyclerView lo interpreta como límite y solo crea las filas que caben en pantalla.
 * Pasando tamaño 0 se fuerza a crear la cuadrícula entera.
 */
class FullHeightRecyclerView @JvmOverloads constructor(
    context: Context, attrs: AttributeSet? = null
) : RecyclerView(context, attrs) {

    override fun onMeasure(widthSpec: Int, heightSpec: Int) {
        val h = if (MeasureSpec.getMode(heightSpec) == MeasureSpec.UNSPECIFIED) {
            MeasureSpec.makeMeasureSpec(0, MeasureSpec.UNSPECIFIED)
        } else heightSpec
        super.onMeasure(widthSpec, h)
    }
}
