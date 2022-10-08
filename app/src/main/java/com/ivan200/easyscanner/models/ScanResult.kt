package com.ivan200.easyscanner.models

/**
 * @author ivan200
 * @since 08.10.2022
 */
sealed class ScanResult {
    class Success(val barcodes: List<BarCode>) : ScanResult()
    class Error(val error: Throwable) : ScanResult()
}
