package com.ivan200.easyscanner

import android.graphics.PointF
import android.graphics.RectF

sealed class ScanResult {
    class Success(val barcodes: List<BarCode>) : ScanResult()
    class Error(val error: Throwable) : ScanResult()
}

data class BarCode(
    val points: List<PointF>,
    val bounds: RectF,
    val text: String,
    val data: List<Byte>
)
