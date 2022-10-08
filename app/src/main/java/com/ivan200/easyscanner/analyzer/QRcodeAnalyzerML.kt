package com.ivan200.easyscanner.analyzer

import android.graphics.ImageFormat
import android.graphics.Point
import android.graphics.PointF
import android.graphics.Rect
import android.graphics.RectF
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageProxy
import androidx.camera.view.TransformExperimental
import androidx.camera.view.transform.CoordinateTransform
import androidx.camera.view.transform.ImageProxyTransformFactory
import androidx.camera.view.transform.OutputTransform
import androidx.core.graphics.toPointF
import androidx.core.graphics.toRectF
import androidx.lifecycle.LiveData
import com.google.android.gms.tasks.Task
import com.google.mlkit.vision.barcode.BarcodeScanner
import com.google.mlkit.vision.barcode.BarcodeScannerOptions
import com.google.mlkit.vision.barcode.BarcodeScanning
import com.google.mlkit.vision.barcode.common.Barcode
import com.google.mlkit.vision.common.InputImage
import com.ivan200.easyscanner.SingleLiveEvent
import com.ivan200.easyscanner.models.BarCode
import com.ivan200.easyscanner.models.ScanResult
import com.ivan200.easyscanner.views.BoxView

/**
 * Recognize QR-code using Google MLKit
 */
class QRcodeAnalyzerML : ImageAnalysis.Analyzer {

    var outputTransform: OutputTransform? = null

    private val _result = SingleLiveEvent<ScanResult>()
    val result: LiveData<ScanResult> = _result

    private val scanner: BarcodeScanner by lazy {
        val options = BarcodeScannerOptions.Builder()
            .setBarcodeFormats(Barcode.FORMAT_QR_CODE)
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
        val transform = getTransform(imageProxy)

        val fullBox = imageProxy.run {
            if (setOf(0, 180).contains(imageInfo.rotationDegrees)) Rect(0, 0, width, height) else Rect(0, 0, height, width)
        }
        val previewBounds = fullBox.toRectF().transform(transform)
        val reticleBox = BoxView.getBarcodeReticleBox(previewBounds.width().toInt(), previewBounds.height().toInt())

        val image = InputImage.fromMediaImage(mediaImage, imageProxy.imageInfo.rotationDegrees)
        pendingTask = scanner.process(image)
            .addOnSuccessListener { barcodes ->
                val codes = mutableListOf<BarCode>()
                for (barcode in barcodes) {
                    val box = barcode.boundingBox?.toRectF()?.transform(transform)
                    if (box == null || reticleBox.contains(box)) {
                        codes.add(
                            BarCode(
                                points = barcode.cornerPoints?.transform(transform) ?: emptyList(),
                                bounds = box?.transform(transform) ?: RectF(),
                                text = barcode.rawValue.orEmpty(),
                                data = barcode.rawBytes?.toList().orEmpty()
                            )
                        )
                    }
                }

                if (codes.isNotEmpty()) {
                    _result.postValue(ScanResult.Success(codes))
                }
            }
            .addOnFailureListener {
                _result.postValue(ScanResult.Error(it))
            }
            .addOnCompleteListener {
                imageProxy.close()
            }
    }

    /**
     * get transfomation to map imageproxy coordinates to preview coordiates
     *
     * https://developer.android.com/reference/androidx/camera/view/transform/CoordinateTransform
     */
    @TransformExperimental
    fun getTransform(imageProxy: ImageProxy): CoordinateTransform? {
        // Dkn why i need to setCropRect, but without i get:
        // java.lang.IllegalStateException: The source transform cannot be inverted
        //   at androidx.core.util.Preconditions.checkState(Preconditions.java:169)
        //   at androidx.camera.view.transform.CoordinateTransform.<init>(CoordinateTransform.java:96)
        imageProxy.setCropRect(Rect(0, 0, imageProxy.width, imageProxy.height))

        val source = ImageProxyTransformFactory().apply {
            isUsingRotationDegrees = true
        }.getOutputTransform(imageProxy)
        return outputTransform?.let { CoordinateTransform(source, it) }
    }

    @TransformExperimental
    fun RectF.transform(transform: CoordinateTransform?): RectF = RectF(this).also { transform?.mapRect(it) }

    @TransformExperimental
    fun Array<Point>.transform(transform: CoordinateTransform?): List<PointF> = map {
        it.toPointF().apply { transform?.mapPoint(this) }
    }
}
