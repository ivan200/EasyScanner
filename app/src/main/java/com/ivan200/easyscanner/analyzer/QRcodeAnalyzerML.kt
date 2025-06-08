package com.ivan200.easyscanner.analyzer

import android.graphics.Bitmap
import android.graphics.ImageFormat
import android.graphics.Matrix
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
    val previewSize = Point(0, 0)

    fun updatePreviewSize(width: Int, height: Int) {
        previewSize.x = width
        previewSize.y = height
    }

    private val _result = SingleLiveEvent<ScanResult>()
    val result: LiveData<ScanResult> = _result

    var paused = false

    fun reset() {
        (_result.value as? ScanResult.Success)?.analyzedBitmap?.recycle()
        paused = false
    }

    private val scanner: BarcodeScanner by lazy {
        val options = BarcodeScannerOptions.Builder()
            .setBarcodeFormats(
                Barcode.FORMAT_ALL_FORMATS
            )
            .build()
        BarcodeScanning.getClient(options)
    }

    private var pendingTask: Task<out Any>? = null

    @androidx.camera.core.ExperimentalGetImage
    override fun analyze(imageProxy: ImageProxy) {
        val mediaImage = imageProxy.image
        if (paused || mediaImage == null || pendingTask?.isComplete == false || imageProxy.format != ImageFormat.YUV_420_888) {
            imageProxy.close()
            return
        }

        val transformFromPreviewToProxy = getTransform(imageProxy, false)
        val previewReticle = BoxView.getBarcodeReticleBox(previewSize.x, previewSize.y)
        val proxyReticle = previewReticle.transform(transformFromPreviewToProxy)
        val transformFromProxyToPreview = getTransform(imageProxy, true)

        val image = InputImage.fromMediaImage(mediaImage, imageProxy.imageInfo.rotationDegrees)
        pendingTask = scanner.process(image)
            .addOnSuccessListener { barcodes ->
                val codes = mutableListOf<BarCode>()
                for (barcode in barcodes) {
                    if (barcode.boundingBox == null || proxyReticle.contains(barcode.boundingBox!!.toRectF())) {
                        codes.add(
                            BarCode(
                                points = barcode.cornerPoints?.transform(transformFromProxyToPreview) ?: emptyList(),
                                text = barcode.rawValue.orEmpty(),
                                data = barcode.rawBytes?.toList().orEmpty()
                            )
                        )
                    }
                }

                if (codes.isNotEmpty()) {
                    paused = true
                    _result.postValue(ScanResult.Success(codes, rotateBitmap(imageProxy.toBitmap(), imageProxy.imageInfo.rotationDegrees)))
                }
            }
            .addOnFailureListener {
                paused = true
                _result.postValue(ScanResult.Error(it))
            }
            .addOnCompleteListener {
                imageProxy.close()
            }
    }

    fun rotateBitmap(source: Bitmap, degrees: Int): Bitmap {
        if(degrees == 0) return source
        val matrix = Matrix().apply { postRotate(degrees.toFloat()) }
        return Bitmap.createBitmap(source, 0, 0, source.width, source.height, matrix, true).also { source.recycle() }
    }


    /**
     * get transfomation to map imageproxy coordinates to preview coordiates
     *
     * https://developer.android.com/reference/androidx/camera/view/transform/CoordinateTransform
     */
    @TransformExperimental
    fun getTransform(imageProxy: ImageProxy, toPreview: Boolean): CoordinateTransform? {
        // Dkn why i need to setCropRect, but without i get:
        // java.lang.IllegalStateException: The source transform cannot be inverted
        //   at androidx.core.util.Preconditions.checkState(Preconditions.java:169)
        //   at androidx.camera.view.transform.CoordinateTransform.<init>(CoordinateTransform.java:96)
        imageProxy.setCropRect(Rect(0, 0, imageProxy.width, imageProxy.height))

        val source = ImageProxyTransformFactory().apply {
            isUsingRotationDegrees = true
        }.getOutputTransform(imageProxy)
        return outputTransform?.let { if(toPreview) CoordinateTransform(source, it) else  CoordinateTransform(it, source) }
    }

    @TransformExperimental
    fun RectF.transform(transform: CoordinateTransform?): RectF = RectF(this).also { transform?.mapRect(it) }

    @TransformExperimental
    fun Array<Point>.transform(transform: CoordinateTransform?): List<PointF> = map {
        it.toPointF().apply { transform?.mapPoint(this) }
    }
}
