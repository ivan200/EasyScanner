package com.ivan200.easyscanner

import android.Manifest
import android.annotation.SuppressLint
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.hardware.display.DisplayManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.util.DisplayMetrics
import android.util.Patterns
import android.util.Size
import android.view.*
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.arch.core.util.Function
import androidx.camera.core.*
import androidx.camera.core.ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.core.content.ContextCompat
import androidx.core.content.ContextCompat.getSystemService
import androidx.lifecycle.Observer
import com.ivan200.easyscanner.analyzer.QRcodeAnalyzerML
import com.ivan200.easyscanner.databinding.ActivityMainBinding
import com.ivan200.easyscanner.permission.PermissionData
import com.ivan200.easyscanner.permission.PermissionsDelegate
import java.util.concurrent.Executors
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min

class MainActivity : AppCompatActivity() {

    /** Blocking camera operations are performed using this executor */
    private var cameraExecutor = Executors.newSingleThreadExecutor()

    private lateinit var binding: ActivityMainBinding

    private var displayId: Int = -1
    private var analysis: ImageAnalysis? = null
    private var imageCapture: ImageCapture? = null
    private var analyzer: QRcodeAnalyzerML? = null
    private var scanObserver: Observer<ScanResult>? = null
    private var cameraProvider: ProcessCameraProvider? = null
    private var preview: Preview? = null
    private var cameraSelector: CameraSelector? = null
    private var camera: Camera? = null

    private var resetOnResume = false

    private lateinit var delegate: PermissionsDelegate

    private val displayManager by lazy { getSystemService(Context.DISPLAY_SERVICE) as DisplayManager }
    private val displayListener = object : DisplayManager.DisplayListener {
        override fun onDisplayAdded(displayId: Int) = Unit
        override fun onDisplayRemoved(displayId: Int) = Unit
        override fun onDisplayChanged(displayId: Int) = binding.viewFinder.let { view ->
            val rotation = view.displayCompat.rotation
            if (displayId == this@MainActivity.displayId) {
                analysis?.targetRotation = rotation
                imageCapture?.targetRotation = rotation
            }
        }
    }

    @SuppressLint("CheckResult")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        val view = binding.root
        setContentView(view)
        resetUi()
        binding.copy.setOnClickListener {
            (getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager)
                ?.setPrimaryClip(ClipData.newPlainText(binding.result.text, binding.result.text))
            resetUi()
            rebindCamera()
        }
        binding.open.setOnClickListener {
            var url = binding.result.text.toString()
            if (!arrayOf("https://", "http://", "rtsp://").any { url.lowercase().startsWith(it) }) {
                url = "https://$url"
            }

            try {
                val i = Intent(Intent.ACTION_VIEW)
                i.data = Uri.parse(url)
                startActivity(i)
                resetOnResume = true
            } catch (ex: Exception) {
                ex.printStackTrace()
            }
        }

        val cameraPermission = PermissionData(
            Manifest.permission.CAMERA,
            R.string.permission_camera_rationale,
            R.string.permission_camera_rationale_goto_settings
        )
        delegate = PermissionsDelegate(
            activity = this,
            savedInstanceState = savedInstanceState,
            allPermissions = arrayListOf(cameraPermission),
            onPermissionGranted = {
                binding.viewFinder.post {
                    displayManager.registerDisplayListener(displayListener, null)
                    initCamera()
                }
            },
            onPermissionRejected = {
                Toast.makeText(this, getString(R.string.toast_permission_rejected), Toast.LENGTH_SHORT).show()
            }
        )

        binding.viewFinder.addOnLayoutChangeListener { v, left, top, right, bottom, oldLeft, oldTop, oldRight, oldBottom ->
            analyzer?.outputTransform = binding.viewFinder.outputTransform
        }
        if (isCameraAvailable()) {
            delegate.requestPermissions()
        }
    }

    private fun isCameraAvailable(): Boolean {
        return packageManager.hasSystemFeature(PackageManager.FEATURE_CAMERA_ANY)
    }

    /** Returns true if the device has an available back camera. False otherwise */
    private fun hasBackCamera(): Boolean {
        return cameraProvider?.hasCamera(CameraSelector.DEFAULT_BACK_CAMERA) ?: false
    }

    /** Returns true if the device has an available front camera. False otherwise */
    private fun hasFrontCamera(): Boolean {
        return cameraProvider?.hasCamera(CameraSelector.DEFAULT_FRONT_CAMERA) ?: false
    }

    private fun initCamera() {
        val cameraProviderFuture = ProcessCameraProvider.getInstance(this)
        cameraProviderFuture.addListener({
            try {
                cameraProvider = cameraProviderFuture.get()
                onCameraProviderInitialized()
            } catch (e: Exception) {
                processCameraException(e)
            }
        }, ContextCompat.getMainExecutor(this))
    }

    private fun processCameraException(ex: Exception) {
        val iEx = when (ex) {
            is CameraUnavailableException -> ex
            is InitializationException -> ex.cause as? CameraUnavailableException
            else -> null
        }
        if (iEx != null) {
            val errorRes = when (iEx.reason) {
                CameraUnavailableException.CAMERA_UNKNOWN_ERROR -> R.string.camera_error_0
                CameraUnavailableException.CAMERA_DISABLED -> R.string.camera_error_1
                CameraUnavailableException.CAMERA_DISCONNECTED -> R.string.camera_error_2
                CameraUnavailableException.CAMERA_ERROR -> R.string.camera_error_3
                CameraUnavailableException.CAMERA_IN_USE -> R.string.camera_error_4
                CameraUnavailableException.CAMERA_MAX_IN_USE -> R.string.camera_error_5
                CameraUnavailableException.CAMERA_UNAVAILABLE_DO_NOT_DISTURB -> R.string.camera_error_6
                else -> R.string.camera_error_0
            }
            binding.result.visibility = View.VISIBLE
            binding.result.text = getString(errorRes)
        } else {
            // ExecutionException, InterruptedException, IllegalArgumentException
            ex.printStackTrace()
            binding.result.visibility = View.VISIBLE
            binding.result.text = ex.message
        }
    }

    private fun onCameraProviderInitialized() {
        cameraSelector = when {
            hasBackCamera() -> CameraSelector.DEFAULT_BACK_CAMERA
            hasFrontCamera() -> CameraSelector.DEFAULT_FRONT_CAMERA
            else -> throw IllegalStateException("Back and front camera are unavailable")
        }

        val previewSize = getPreviewSize()
        val screenAspectRatio = aspectRatio(previewSize.width, previewSize.height)
        val rotation = binding.viewFinder.displayCompat.rotation

        preview = Preview.Builder().apply {
            setTargetAspectRatio(screenAspectRatio)
            setTargetRotation(rotation)
        }.build().apply {
            // Attach the viewfinder's surface provider to preview use case
            setSurfaceProvider(binding.viewFinder.surfaceProvider)
        }

        analyzer = QRcodeAnalyzerML()
        analysis = ImageAnalysis.Builder().apply {
            setBackpressureStrategy(STRATEGY_KEEP_ONLY_LATEST)
            setTargetAspectRatio(screenAspectRatio)
            setTargetRotation(rotation)
        }.build().apply {
            setAnalyzer(cameraExecutor, analyzer!!)
        }

        imageCapture = ImageCapture.Builder()
            .setCaptureMode(ImageCapture.CAPTURE_MODE_MINIMIZE_LATENCY)
            .setTargetAspectRatio(screenAspectRatio)
            .setTargetRotation(rotation)
            .build()

        rebindCamera()

        // add pinch to zoom
        val scaleGestureDetector = ScaleGestureDetector(
            this,
            object : ScaleGestureDetector.SimpleOnScaleGestureListener() {
                override fun onScale(detector: ScaleGestureDetector): Boolean {
                    if (camera != null) {
                        val scale = camera!!.cameraInfo.zoomState.value!!.zoomRatio * detector.scaleFactor
                        camera!!.cameraControl.setZoomRatio(scale)
                    }
                    return true
                }
            }
        )

        // add focus on click
        val clickGestureDetector = Function<MotionEvent, Unit> { event ->
            if (event.pointerCount == 1 &&
                event.action == MotionEvent.ACTION_UP &&
                event.eventTime - event.downTime < 200 &&
                camera != null
            ) {
                val point = binding.viewFinder.meteringPointFactory.createPoint(event.x, event.y)
                val action = FocusMeteringAction.Builder(point).build()
                binding.focusView.anim(event.x, event.y)
                val future = camera!!.cameraControl.startFocusAndMetering(action)
                future.addListener({ binding.focusView.hide() }, ContextCompat.getMainExecutor(this))
            }
        }
        binding.viewFinder.setOnTouchListener { view, event ->
            view.performClick()
            clickGestureDetector.apply(event)
            scaleGestureDetector.onTouchEvent(event)
            return@setOnTouchListener true
        }
    }

    private fun showResult(it: ScanResult) {
        when (it) {
            is ScanResult.Error -> {
                binding.result.text = it.error.message
            }
            is ScanResult.Success -> {
                val text = it.barcodes.joinToString("\n") { it.text }
                if (text.isNotEmpty()) {
                    binding.result.visibility = View.VISIBLE
                    binding.result.text = text
                    binding.copy.visibility = View.VISIBLE

                    if (Patterns.WEB_URL.matcher(text).matches()) {
                        binding.open.visibility = View.VISIBLE
                    }
                    pauseFrame()
                } else {
                    binding.result.visibility = View.INVISIBLE
                }
                if (it.barcodes.isNotEmpty()) {
                    binding.pointsView.show(it.barcodes.map { it.points })
                }
            }
        }
    }

    private fun pauseFrame() {
        scanObserver?.apply { analyzer?.result?.removeObserver(this) }
        cameraProvider?.unbindAll()
    }

    private fun getPreviewSize(): Size {
        val size = Size(binding.viewFinder.measuredWidth, binding.viewFinder.measuredHeight)
        return if (size.height <= 0 || size.width <= 0) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                (getSystemService(Context.WINDOW_SERVICE) as WindowManager).currentWindowMetrics.bounds.let {
                    Size(it.width(), it.height())
                }
            } else {
                @Suppress("DEPRECATION")
                val metrics = DisplayMetrics().also {
                    binding.viewFinder.display.getRealMetrics(it)
                }
                Size(metrics.widthPixels, metrics.heightPixels)
            }
        } else size
    }

    private fun resetUi() {
        binding.result.text = null
        binding.copy.visibility = View.INVISIBLE
        binding.open.visibility = View.GONE
        binding.result.visibility = View.INVISIBLE
        binding.pointsView.hide()
    }

    private fun rebindCamera() {
        cameraProvider?.let {
            // Must unbind the use-cases before rebinding them
            it.unbindAll()
            camera = it.bindToLifecycle(
                this,
                cameraSelector!!,
                preview!!,
                imageCapture,
                analysis!!
            )
            analyzer?.result?.observe(this, Observer(this::showResult).apply { scanObserver = this })
        }
    }

    override fun onStart() {
        super.onStart()
        lockOrientation(binding.viewFinder)
        hideSystemUI()
    }

    override fun onStop() {
        unlockOrientation()
        super.onStop()
    }

    override fun onResume() {
        super.onResume()
        if (resetOnResume) {
            resetUi()
            rebindCamera()
            resetOnResume = false
        }
    }

    /**
     *  [ImageAnalysis.Builder] requires enum value of [AspectRatio].
     *  Currently it has values of 4:3 & 16:9.
     *
     *  Detecting the most suitable ratio for dimensions provided in @params by counting absolute
     *  of preview ratio to one of the provided values.
     *
     *  @param width - preview width
     *  @param height - preview height
     *  @return suitable aspect ratio
     */
    private fun aspectRatio(width: Int, height: Int): Int {
        val previewRatio = max(width, height).toDouble() / min(width, height)
        if (abs(previewRatio - RATIO_4_3_VALUE) <= abs(previewRatio - RATIO_16_9_VALUE)) {
            return AspectRatio.RATIO_4_3
        }
        return AspectRatio.RATIO_16_9
    }

    override fun onSaveInstanceState(outState: Bundle) {
        delegate.saveInstanceState(outState)
        super.onSaveInstanceState(outState)
    }

    override fun onDestroy() {
        cameraExecutor.shutdown()
        displayManager.unregisterDisplayListener(displayListener)
        super.onDestroy()
    }

    companion object {
        private const val RATIO_4_3_VALUE = 4.0 / 3.0
        private const val RATIO_16_9_VALUE = 16.0 / 9.0
    }
}
