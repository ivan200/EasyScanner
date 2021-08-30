package com.ivan200.easyscanner

import android.graphics.PointF

data class ScanResult(
    val result: List<String> = emptyList(),
    val points: List<Array<PointF>> = emptyList(),
    val error: Throwable? = null
)