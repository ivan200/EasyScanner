package com.ivan200.easyscanner.views

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.graphics.PorterDuff
import android.graphics.PorterDuffXfermode
import android.graphics.RectF
import android.util.AttributeSet
import android.view.View
import androidx.annotation.FloatRange
import com.ivan200.easyscanner.ImageUtils

/**
 *
 */
internal class BoxView : View {
    constructor(context: Context?) : super(context)
    constructor(context: Context?, attributeSet: AttributeSet?) : super(context, attributeSet)
    constructor(context: Context?, attributeSet: AttributeSet?, defStyleAttr: Int)
        : super(context, attributeSet, defStyleAttr)
    @Suppress("unused")
    constructor(context: Context?, attributeSet: AttributeSet?, defStyleAttr: Int, defStyleRes: Int)
        : super(context, attributeSet, defStyleAttr, defStyleRes)

    init {
        setLayerType(LAYER_TYPE_HARDWARE, null)
    }

    /**
     * @property spanSizePercents size of the span between the corners, in percent
     * @property lineWidth width of lines
     * @property boxCorners corner radius
     */
    companion object {
        @FloatRange(from = 0.0, to = 100.0)
        const val spanSizePercents = 60f
        const val lineWidth = 3f
        const val boxCorners = 8f
    }

    private val backgroundPaint: Paint = Paint().apply {
        color =

            Color.parseColor("#55000000")
    }

    private val boxPaint: Paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.WHITE
        style = Paint.Style.STROKE
        strokeWidth = context.resources.displayMetrics.density * lineWidth
        strokeCap = Paint.Cap.ROUND
    }

    private val transparentPaint: Paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        strokeWidth = boxPaint.strokeWidth
        xfermode = PorterDuffXfermode(PorterDuff.Mode.CLEAR)
    }

    private val boxCornerRadius: Float = context.resources.displayMetrics.density * boxCorners

    private var path = Path()

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val boxRect = ImageUtils.getBarcodeReticleBox(width, height)
        // Draws the dark background scrim and leaves the box area clear.
        canvas.drawRect(0f, 0f, width.toFloat(), height.toFloat(), backgroundPaint)
        // As the stroke is always centered, so erase twice with FILL and STROKE respectively to clear
        // all area that the box rect would occupy.
        transparentPaint.style = Paint.Style.FILL_AND_STROKE
        canvas.drawRoundRect(boxRect, boxCornerRadius, boxCornerRadius, transparentPaint)

        val lineShift = boxPaint.strokeWidth / 2f
        drawCornersPath(canvas, boxRect, boxCornerRadius, lineShift)
    }

    private fun drawCornersPath(
        canvas: Canvas,
        rect: RectF,
        cornerRadius: Float,
        shift: Float
    ) {
        if (spanSizePercents == 0f) {
            canvas.drawRoundRect(rect, boxCornerRadius, boxCornerRadius, boxPaint)
            return
        }
        val left: Float = rect.left - shift
        val top: Float = rect.top - shift
        val right: Float = rect.right + shift
        val bottom: Float = rect.bottom + shift

        val span = rect.width() * (spanSizePercents / 100f)   //the size of the gap between the corners, in pixels
        val cornerSize = (rect.width() - span) / 2

        path = Path().apply {
            //Top right
            moveTo(right - cornerSize, top)
            lineTo(right - cornerRadius, top)
            if (cornerRadius > 0) {
                quadTo(right, top, right, top + cornerRadius)
            }
            lineTo(right, top + cornerSize)

            //Bottom right
            moveTo(right, bottom - cornerSize)
            lineTo(right, bottom - cornerRadius)
            if (cornerRadius > 0) {
                quadTo(right, bottom, right - cornerRadius, bottom)
            }
            lineTo(right - cornerSize, bottom)

            //Bottom left
            moveTo(left + cornerSize, bottom)
            lineTo(left + cornerRadius, bottom)
            if (cornerRadius > 0) {
                quadTo(left, bottom, left, bottom - cornerRadius)
            }
            lineTo(left, bottom - cornerSize)

            //Top left
            moveTo(left, top + cornerSize)
            lineTo(left, top + cornerRadius)
            if (cornerRadius > 0) {
                quadTo(left, top, left + cornerRadius, top)
            }
            lineTo(left + cornerSize, top)
        }

        canvas.drawPath(path, boxPaint)
    }
}