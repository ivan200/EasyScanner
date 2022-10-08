package com.ivan200.easyscanner.views

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.graphics.PointF
import android.util.AttributeSet
import android.view.View

internal class PointsView : View {
    constructor(context: Context?) : super(context)
    constructor(context: Context?, attributeSet: AttributeSet?) : super(context, attributeSet)
    constructor(context: Context?, attributeSet: AttributeSet?, defStyleAttr: Int)
        : super(context, attributeSet, defStyleAttr)

    @Suppress("unused")
    constructor(context: Context?, attributeSet: AttributeSet?, defStyleAttr: Int, defStyleRes: Int)
        : super(context, attributeSet, defStyleAttr, defStyleRes)

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
            canvas.drawPath(path, linePaint)
        }
    }
}