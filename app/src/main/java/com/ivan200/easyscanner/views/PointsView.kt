package com.ivan200.easyscanner.views

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.CornerPathEffect
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
        const val lineWidth = 2f
        const val cornerRadius = 10f
    }

    private val linePaint: Paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#adc1e0")
        style = Paint.Style.STROKE
        strokeWidth = context.resources.displayMetrics.density * lineWidth
        strokeCap = Paint.Cap.ROUND
        pathEffect = CornerPathEffect(context.resources.displayMetrics.density * cornerRadius)
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
                drawBarcode(path, expandBarcode(barcodePoints, 1.2f))
            }
            canvas.drawPath(path, linePaint)
        }
    }

    private fun drawBarcode(path: Path, barcodePoints: List<PointF>, cornerSize: Float = 0.25f) {
        for (i in barcodePoints.indices) {
            val prev = barcodePoints[(i - 1 + barcodePoints.size) % barcodePoints.size]
            val curr = barcodePoints[i]
            val next = barcodePoints[(i + 1) % barcodePoints.size]

            val v1 = PointF(prev.x - curr.x, prev.y - curr.y)
            val v2 = PointF(next.x - curr.x, next.y - curr.y)

            val a = PointF(curr.x + v1.x * cornerSize, curr.y + v1.y * cornerSize)
            val b = PointF(curr.x + v2.x * cornerSize, curr.y + v2.y * cornerSize)

            path.moveTo(a.x, a.y)
            path.lineTo(curr.x, curr.y)
            path.lineTo(b.x, b.y)
        }
    }

    fun expandBarcode(points: List<PointF>, fraction: Float): List<PointF> {
        if (points.isEmpty()) return emptyList()
        val center = when (points.size) {
            4 -> findDiagIntersection(points[0], points[2], points[1], points[3]) ?: getCentroid(points)
            else -> getCentroid(points)
        }
        return points.map { PointF(center.x + (it.x - center.x) * fraction, center.y + (it.y - center.y) * fraction) }
    }

    private fun getCentroid(points: List<PointF>) = PointF(
        points.sumOf { it.x.toDouble() }.toFloat() / points.size,
        points.sumOf { it.y.toDouble() }.toFloat() / points.size
    )

    private fun findDiagIntersection(a: PointF, c: PointF, b: PointF, d: PointF): PointF? {
        val v1 = PointF(c.x - a.x,c.y - a.y)
        val v2 = PointF(d.x - b.x,d.y - b.y)
        val v = PointF(b.x - a.x, b.y - a.y)
        val det = v1.x * v2.y - v1.y * v2.x
        if (det == 0f) return null
        val t = (v.x * v2.y - v.y * v2.x) / det
        return PointF(a.x + t * v1.x, a.y + t * v1.y)
    }
}
