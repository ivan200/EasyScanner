package com.ivan200.easyscanner.models

import android.graphics.PointF

/**
 * @author ivan200
 * @since 08.10.2022
 */
data class BarCode(
    val points: List<PointF>,
    val text: String,
    val data: List<Byte>
)
