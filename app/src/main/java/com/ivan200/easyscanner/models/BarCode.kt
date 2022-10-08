package com.ivan200.easyscanner.models

import android.graphics.PointF
import android.graphics.RectF

/**
 * @author ivan200
 * @since 08.10.2022
 */
data class BarCode(
    val points: List<PointF>,
    val bounds: RectF,
    val text: String,
    val data: List<Byte>
)
