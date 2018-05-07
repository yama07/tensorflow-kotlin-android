package jp.yama07.tensorflowkotlin.util

import android.graphics.Bitmap
import android.graphics.Matrix
import android.os.Environment
import timber.log.Timber
import java.io.File
import java.io.FileOutputStream

object ImageUtils {
  fun convertYuv420ToArgb8888(
      yData: ByteArray,
      uData: ByteArray,
      vData: ByteArray,
      width: Int,
      height: Int,
      yRowStride: Int,
      uvRowStride: Int,
      uvPixelStride: Int,
      out: IntArray
  ) {
    var yp = 0
    for (j in 0 until height) {
      val pY = yRowStride * j
      val pUV = uvRowStride * (j shr 1)
      for (i in 0 until width) {
        val uvOffset = pUV + (i shr 1) * uvPixelStride

        out[yp++] = yuv2Rgb(
            0xff and yData[pY + i].toInt(),
            0xff and uData[uvOffset].toInt(),
            0xff and vData[uvOffset].toInt())
      }
    }
  }

  private const val MAX_CHANNEL_VALUE = 262143

  private fun yuv2Rgb(y_: Int, u_: Int, v_: Int): Int {
    val y = if (y_ - 16 < 0) 0 else y_ - 16
    val u = u_ - 128
    val v = v_ - 128

    val y1192 = 1192 * y
    var r = y1192 + 1634 * v
    var g = y1192 - 833 * v - 400 * u
    var b = y1192 + 2066 * u

    r = if (r > MAX_CHANNEL_VALUE) MAX_CHANNEL_VALUE else if (r < 0) 0 else r
    g = if (g > MAX_CHANNEL_VALUE) MAX_CHANNEL_VALUE else if (g < 0) 0 else g
    b = if (b > MAX_CHANNEL_VALUE) MAX_CHANNEL_VALUE else if (b < 0) 0 else b

    return -0x1000000 or (r shl 6 and 0xff0000) or (g shr 2 and 0xff00) or (b shr 10 and 0xff)
  }

  fun getTransformationMatrix(
      srcWidth: Int,
      srcHeight: Int,
      dstWidth: Int,
      dstHeight: Int,
      applyRotation: Int,
      maintainAspectRation: Boolean
  ): Matrix {
    val matrix = Matrix()

    if (applyRotation != 0) {
      if (applyRotation % 90 != 0) {
        Timber.w("Rotation of $applyRotation % 90 != 0")
      }
      matrix.postTranslate(-srcWidth / 2.0f, -srcHeight / 2.0f)
      matrix.postRotate(applyRotation.toFloat())
    }

    val transpose = (Math.abs(applyRotation) + 90) % 180 == 0

    val inWidth = if (transpose) srcHeight else srcWidth
    val inHeight = if (transpose) srcWidth else srcHeight

    if (inWidth != dstWidth || inHeight != dstHeight) {
      val scaleFactorX = dstWidth / inWidth.toFloat()
      val scaleFactorY = dstHeight / inHeight.toFloat()

      if (maintainAspectRation) {
        val scaleFactor = Math.max(scaleFactorX, scaleFactorY)
        matrix.postScale(scaleFactor, scaleFactor)
      } else {
        matrix.postScale(scaleFactorX, scaleFactorY)
      }
    }

    if (applyRotation != 0) {
      matrix.postTranslate(dstWidth / 2.0f, dstHeight / 2.0f)
    }

    return matrix
  }

  private fun saveBitmap(bitmap: Bitmap, filename: String) {
    val root = Environment.getExternalStorageDirectory().absolutePath + File.separator + "tensorflow"
    Timber.i("Saving ${bitmap.width}x${bitmap.width} bitmap to $root")
    val myDir = File(root)

    if (!myDir.mkdir()) {
      Timber.i("Make dir failed")
    }

    val file = File(myDir, filename)
    if (file.exists()) {
      file.delete()
    }
    try {
      FileOutputStream(file).use {
        bitmap.compress(Bitmap.CompressFormat.PNG, 99, it)
        it.flush()
      }
    } catch (e: Exception) {
      Timber.e(e, "Exception!")
    }
  }

  fun saveBitmap(bitmap: Bitmap) = saveBitmap(bitmap, "preview.png")
}