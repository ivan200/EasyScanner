package com.ivan200.easyscanner

import android.graphics.ImageFormat
import android.graphics.PointF
import android.graphics.Rect
import android.graphics.RectF
import android.util.Size
import androidx.camera.core.ImageProxy
import kotlin.math.min

object ImageUtils {

    fun setSquareCropRect(imageProxy: ImageProxy) {
        val box = getBarcodeReticleBox(imageProxy.width, imageProxy.height)
        imageProxy.setCropRect(Rect(box.left.toInt(), box.top.toInt(), box.right.toInt(), box.bottom.toInt()))
    }

    fun yuv420888ToNv21(image: ImageProxy): ByteArray {
        val pixelCount = image.cropRect.width() * image.cropRect.height()
        val pixelSizeBits = ImageFormat.getBitsPerPixel(ImageFormat.YUV_420_888)
        val outputBuffer = ByteArray(pixelCount * pixelSizeBits / 8)
        imageToByteBuffer(image, outputBuffer, pixelCount)
        return outputBuffer
    }

    private fun imageToByteBuffer(image: ImageProxy, outputBuffer: ByteArray, pixelCount: Int) {
        val imageCrop = image.cropRect
        val imagePlanes = image.planes

        imagePlanes.forEachIndexed { planeIndex, plane ->
            // How many values are read in input for each output value written
            // Only the Y plane has a value for every pixel, U and V have half the resolution i.e.
            //
            // Y Plane            U Plane    V Plane
            // ===============    =======    =======
            // Y Y Y Y Y Y Y Y    U U U U    V V V V
            // Y Y Y Y Y Y Y Y    U U U U    V V V V
            // Y Y Y Y Y Y Y Y    U U U U    V V V V
            // Y Y Y Y Y Y Y Y    U U U U    V V V V
            // Y Y Y Y Y Y Y Y
            // Y Y Y Y Y Y Y Y
            // Y Y Y Y Y Y Y Y
            val outputStride: Int

            // The index in the output buffer the next value will be written at
            // For Y it's zero, for U and V we start at the end of Y and interleave them i.e.
            //
            // First chunk        Second chunk
            // ===============    ===============
            // Y Y Y Y Y Y Y Y    V U V U V U V U
            // Y Y Y Y Y Y Y Y    V U V U V U V U
            // Y Y Y Y Y Y Y Y    V U V U V U V U
            // Y Y Y Y Y Y Y Y    V U V U V U V U
            // Y Y Y Y Y Y Y Y
            // Y Y Y Y Y Y Y Y
            // Y Y Y Y Y Y Y Y
            var outputOffset: Int

            when (planeIndex) {
                0 -> {
                    outputStride = 1
                    outputOffset = 0
                }
                1 -> {
                    outputStride = 2
                    // For NV21 format, U is in odd-numbered indices
                    outputOffset = pixelCount + 1
                }
                2 -> {
                    outputStride = 2
                    // For NV21 format, V is in even-numbered indices
                    outputOffset = pixelCount
                }
                else -> {
                    // Image contains more than 3 planes, something strange is going on
                    return@forEachIndexed
                }
            }

            val planeBuffer = plane.buffer
            val rowStride = plane.rowStride
            val pixelStride = plane.pixelStride

            // We have to divide the width and height by two if it's not the Y plane
            val planeCrop = if (planeIndex == 0) {
                imageCrop
            } else {
                Rect(
                    imageCrop.left / 2,
                    imageCrop.top / 2,
                    imageCrop.right / 2,
                    imageCrop.bottom / 2
                )
            }

            val planeWidth = planeCrop.width()
            val planeHeight = planeCrop.height()

            // Intermediate buffer used to store the bytes of each row
            val rowBuffer = ByteArray(plane.rowStride)

            // Size of each row in bytes
            val rowLength = if (pixelStride == 1 && outputStride == 1) {
                planeWidth
            } else {
                // Take into account that the stride may include data from pixels other than this
                // particular plane and row, and that could be between pixels and not after every
                // pixel:
                //
                // |---- Pixel stride ----|                    Row ends here --> |
                // | Pixel 1 | Other Data | Pixel 2 | Other Data | ... | Pixel N |
                //
                // We need to get (N-1) * (pixel stride bytes) per row + 1 byte for the last pixel
                (planeWidth - 1) * pixelStride + 1
            }

            for (row in 0 until planeHeight) {
                // Move buffer position to the beginning of this row
                planeBuffer.position(
                    (row + planeCrop.top) * rowStride + planeCrop.left * pixelStride
                )

                if (pixelStride == 1 && outputStride == 1) {
                    // When there is a single stride value for pixel and output, we can just copy
                    // the entire row in a single step
                    planeBuffer.get(outputBuffer, outputOffset, rowLength)
                    outputOffset += rowLength
                } else {
                    // When either pixel or output have a stride > 1 we must copy pixel by pixel
                    planeBuffer.get(rowBuffer, 0, rowLength)
                    for (col in 0 until planeWidth) {
                        outputBuffer[outputOffset] = rowBuffer[col * pixelStride]
                        outputOffset += outputStride
                    }
                }
            }
        }
    }

    @Suppress("UnnecessaryVariable")
    fun getBarcodeReticleBox(viewWidth: Int, viewHeight: Int): RectF {
        val overlayWidth = viewWidth.toFloat()
        val overlayHeight = viewHeight.toFloat()

        val minSize = min(viewWidth, viewHeight).toFloat()// * 70f / 100f
        val boxWidth = minSize * 70 / 100
        val boxHeight = minSize * 70 / 100

        val cx = overlayWidth / 2
        val cy = overlayHeight / 2
        return RectF(
            cx - (boxWidth / 2).toInt(),
            cy - (boxHeight / 2).toInt(),
            cx + (boxWidth / 2).toInt(),
            cy + (boxHeight / 2).toInt()
        )
    }

    fun translatePoint(imageProxy: ImageProxy, screenSize: Size, point: PointF): PointF {
        return translatePoint(
            imageProxy.width,
            imageProxy.height,
            imageProxy.imageInfo.rotationDegrees,
            imageProxy.cropRect,
            screenSize,
            point
        )
    }

    /**
     * Translate point from ImageProxy sizes to Preview sizes
     * means to resize from small analysis picture size to big view size
     */
    private fun translatePoint(
        imageWidth: Int,
        imageHeight: Int,
        rotation: Int,
        cropRect: Rect,
        viewSize: Size,
        point: PointF
    ): PointF {

        val xRatio = when (rotation) {
            0, 180 -> imageWidth / viewSize.width.toFloat()
            90, 270 -> imageHeight / viewSize.width.toFloat()
            else -> 1f
        }
        val yRatio = when (rotation) {
            0, 180 -> imageHeight / viewSize.height.toFloat()
            90, 270 -> imageWidth / viewSize.height.toFloat()
            else -> 1f
        }
        val xAdd = when (rotation) {
            0 -> cropRect.left
            90 -> cropRect.top
            180 -> imageWidth - cropRect.right
            270 -> imageHeight - cropRect.bottom
            else -> 0
        }
        val yAdd = when (rotation) {
            0 -> cropRect.top
            90 -> cropRect.left
            180 -> imageHeight - cropRect.bottom
            270 -> imageWidth - cropRect.right
            else -> 0
        }
        return PointF(
            (point.x + xAdd) / xRatio,
            (point.y + yAdd) / yRatio
        )
    }
}