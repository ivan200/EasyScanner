package com.ivan200.easyscanner.analyzer

import android.graphics.ImageFormat
import android.graphics.PointF
import android.util.Size
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageProxy
import androidx.lifecycle.LiveData
import com.google.android.gms.tasks.Task
import com.google.mlkit.vision.barcode.BarcodeScanner
import com.google.mlkit.vision.barcode.BarcodeScannerOptions
import com.google.mlkit.vision.barcode.BarcodeScanning
import com.google.mlkit.vision.barcode.common.Barcode
import com.google.mlkit.vision.common.InputImage
import com.ivan200.easyscanner.ImageUtils
import com.ivan200.easyscanner.ScanResult
import com.ivan200.easyscanner.SingleLiveEvent

/**
 * Recognize QR-code using Google MLKit
 */
class QRcodeAnalyzerML: ImageAnalysis.Analyzer {

    var previewSize = Size(0, 0)

    private val _result = SingleLiveEvent<ScanResult>()
    val result: LiveData<ScanResult> = _result

    private val scanner: BarcodeScanner by lazy {
        val options = BarcodeScannerOptions.Builder()
            .setBarcodeFormats(
                Barcode.FORMAT_QR_CODE
            )
            .build()
        BarcodeScanning.getClient(options)
    }

    private var pendingTask: Task<out Any>? = null

    @androidx.camera.core.ExperimentalGetImage
    override fun analyze(imageProxy: ImageProxy) {
        val mediaImage = imageProxy.image
        if (mediaImage == null || pendingTask?.isComplete == false || imageProxy.format != ImageFormat.YUV_420_888) {
            imageProxy.close()
            return
        }
        ImageUtils.setSquareCropRect(imageProxy)

        val nv21ByteArray = ImageUtils.yuv420888ToNv21(imageProxy)
        val image = InputImage.fromByteArray(
            nv21ByteArray,
            imageProxy.cropRect.width(),
            imageProxy.cropRect.height(),
            imageProxy.imageInfo.rotationDegrees,
            InputImage.IMAGE_FORMAT_NV21
        )
        // val image = InputImage.fromMediaImage(mediaImage, imageProxy.imageInfo.rotationDegrees)

        pendingTask = scanner.process(image)
            .addOnSuccessListener { barcodes ->
                val scanResults = barcodes.map { it.rawValue ?: "" }
                val points = barcodes.mapNotNull { barcode: Barcode ->
                    barcode.cornerPoints?.map {
                        ImageUtils.translatePoint(imageProxy, previewSize, PointF(it))
                    }?.toTypedArray() ?: emptyArray()
                }
                _result.postValue(ScanResult(scanResults, points))
            }
            .addOnFailureListener {
                _result.postValue(ScanResult(error = it))
            }
            .addOnCompleteListener {
                imageProxy.close()
            }
    }
}

