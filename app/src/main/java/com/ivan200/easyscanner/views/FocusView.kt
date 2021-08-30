package com.ivan200.easyscanner.views

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.util.AttributeSet
import android.view.View
import android.view.animation.Animation
import android.view.animation.Animation.AnimationListener
import android.view.animation.AnimationSet
import android.view.animation.ScaleAnimation
import android.view.animation.TranslateAnimation

class FocusView : View {
    constructor(context: Context?) : super(context)
    constructor(context: Context?, attributeSet: AttributeSet?) : super(context, attributeSet)
    constructor(context: Context?, attributeSet: AttributeSet?, defStyleAttr: Int)
        : super(context, attributeSet, defStyleAttr)
    @Suppress("unused")
    constructor(context: Context?, attributeSet: AttributeSet?, defStyleAttr: Int, defStyleRes: Int)
        : super(context, attributeSet, defStyleAttr, defStyleRes)

    private var animated = false
    private var paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.WHITE
        style = Paint.Style.STROKE
        strokeWidth = strokeWidth
    }
    private val strokeWidth = 3.0f
    private var animation: AnimationSet? = null
    private var fromX = 0f
    private var fromY = 0f
    private var toX = 1.0f
    private var toY = 1.0f
    private val animationDuration = 300L

    fun anim(xValue: Float, yValue: Float) {
        this.visibility = VISIBLE
        animated = true
        clearAnimation()
        if (animation != null) {
            animation!!.setAnimationListener(null)
            animation = null
        }
        invalidate()
        val scaleAnimation = ScaleAnimation(fromX, toX, fromY, toY).apply {
            duration = animationDuration
        }
        val translateAnimation = TranslateAnimation(
            TranslateAnimation.ABSOLUTE, xValue - width * fromX / 2,
            TranslateAnimation.ABSOLUTE, xValue - width * toX / 2,
            TranslateAnimation.ABSOLUTE, yValue - height * fromY / 2,
            TranslateAnimation.ABSOLUTE, yValue - height * toY / 2
        ).apply {
            duration = animationDuration
        }

        animation = AnimationSet(false).apply {
            addAnimation(scaleAnimation)
            addAnimation(translateAnimation)
            repeatCount = 0
            fillAfter = true
            isFillEnabled = true
            fillBefore = true
            setAnimationListener(object : AnimationListener {
                override fun onAnimationEnd(animation: Animation) {
                    animated = false
                }

                override fun onAnimationRepeat(animation: Animation) {}
                override fun onAnimationStart(animation: Animation) {}
            })
        }
        startAnimation(animation)
    }

    fun hide() {
        clearAnimation()
        this.visibility = INVISIBLE
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        if (animated) {
            canvas.drawCircle(
                (width / 2).toFloat(),
                (height / 2).toFloat(),
                width.toFloat() / 2.0f - strokeWidth / 2.0f - 1,
                paint
            )
        }
    }
}