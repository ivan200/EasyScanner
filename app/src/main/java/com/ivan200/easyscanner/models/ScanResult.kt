package com.ivan200.easyscanner.models

import android.graphics.Bitmap

/**
 * @author ivan200
 * @since 08.10.2022
 */
sealed interface ScanResult {
    class Success(
        val barcodes: List<BarCode>,
        val analyzedBitmap: Bitmap
    ) : ScanResult
    class Error(val error: Throwable) : ScanResult
}
