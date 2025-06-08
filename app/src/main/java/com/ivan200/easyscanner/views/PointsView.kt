package com.ivan200.easyscanner.views

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.graphics.PointF
import android.util.AttributeSet
import android.view.View
import androidx.annotation.AttrRes
import androidx.annotation.StyleRes

class PointsView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    @AttrRes defStyleAttr: Int = 0,
    @StyleRes defStyleRes: Int = 0
) : View(context, attrs, defStyleAttr, defStyleRes) {

    companion object {
        const val lineWidth = 1f
    }

    private val linePaint: Paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.GREEN
        style = Paint.Style.STROKE
        strokeWidth = context.resources.displayMetrics.density * lineWidth
        strokeCap = Paint.Cap.ROUND
    }

    fun show(barcodes: List<List<PointF>>) {
        this.visibility = VISIBLE
        this.barcodes = barcodes
        invalidate()
    }

    fun hide() {
        if (this.barcodes.isNotEmpty()) {
            this.barcodes = emptyList()
            invalidate()
        }
    }

    private var barcodes: List<List<PointF>> = emptyList()
    private var path = Path()

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)

        if (barcodes.isNotEmpty()) {
            path.reset()
            for (barcodePoints in barcodes) {
                drawBarcode(path,barcodePoints)
            }
            canvas.drawPath(path, linePaint)
        }
    }

    private fun drawBarcode(path: Path,barcodePoints: List<PointF>){
        if (barcodePoints.size > 1) {
            for ((index, point) in barcodePoints.withIndex()) {
                if (index == 0) {
                    path.moveTo(point.x, point.y)
                } else {
                    path.lineTo(point.x, point.y)
                    if (index == barcodePoints.size - 1) {
                        val first = barcodePoints[0]
                        path.lineTo(first.x, first.y)
                    }
                }
            }
        }
    }
}
