package jp.yama07.tensorflowkotlin.ui

import android.Manifest
import android.app.Activity
import android.content.Context
import android.content.pm.PackageManager
import android.hardware.camera2.CameraAccessException
import android.hardware.camera2.CameraCharacteristics
import android.hardware.camera2.CameraManager
import android.media.Image
import android.media.ImageReader
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.HandlerThread
import android.support.v4.app.ActivityCompat
import android.util.Size
import android.view.KeyEvent
import android.view.Surface
import android.view.WindowManager
import android.widget.Toast
import jp.yama07.tensorflowkotlin.R
import jp.yama07.tensorflowkotlin.util.ImageUtils
import jp.yama07.tensorflowkotlin.view.OverlayView
import kotlinx.android.synthetic.main.fragment_camera_connection.*
import timber.log.Timber

abstract class CameraActivity : Activity(), ImageReader.OnImageAvailableListener {
  companion object {
    private const val PERMISSIONS_REQUEST = 1
    private const val PERMISSION_CAMERA = Manifest.permission.CAMERA
    private const val PERMISSION_STORAGE = Manifest.permission.WRITE_EXTERNAL_STORAGE
  }

  protected var isDebug = false

  private var handler: Handler? = null
  private var handlerThread: HandlerThread? = null
  private var useCamera2API = true
  private var isProcessingFrame = false
  private val yuvBytes = Array<ByteArray?>(3, { null })
  private var rgbBytes: IntArray? = null
  private var yRowStride = 0

  protected var previewWidth = 0
  protected var previewHeight = 0

  private var postInferenceCallback: Runnable? = null
  private var imageConverter: Runnable? = null

  override fun onCreate(savedInstanceState: Bundle?) {
    Timber.d("onCreate ${this}")
    super.onCreate(savedInstanceState)
    window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
    setContentView(R.layout.activity_camera)

    if (hasPermission()) {
      setFragment()
    } else {
      requestPermission()
    }
  }

  fun getRgbBytes(): IntArray {
    imageConverter?.run()
    return rgbBytes!!
  }

  override fun onImageAvailable(reader: ImageReader?) {
    if (previewWidth == 0 || previewHeight == 0) return
    if (rgbBytes == null) {
      rgbBytes = IntArray(previewWidth * previewHeight, { 0 })
    }
    try {
      val image = reader?.acquireLatestImage()
      image ?: return

      if (isProcessingFrame) {
        image.close()
        return
      }
      isProcessingFrame = true
      val planes = image.planes
      fillBytes(planes, yuvBytes)
      yRowStride = planes[0].rowStride
      val uvRowStride = planes[1].rowStride
      val uvPixelStride = planes[1].pixelStride

      imageConverter = Runnable {
        ImageUtils.convertYuv420ToArgb8888(
            yuvBytes[0]!!,
            yuvBytes[1]!!,
            yuvBytes[2]!!,
            previewWidth,
            previewHeight,
            yRowStride,
            uvRowStride,
            uvPixelStride,
            rgbBytes!!)
      }
      postInferenceCallback = Runnable {
        image.close()
        isProcessingFrame = false
      }
      processImage()
    } catch (e: Exception) {
      Timber.e(e, "Exception!")
    }
  }

  @Synchronized
  override fun onStart() {
    Timber.d("onStart ${this}")
    super.onStart()
  }

  @Synchronized
  override fun onResume() {
    Timber.d("onResume ${this}")
    super.onResume()

    handlerThread = HandlerThread("inference").also { it.start() }
    handler = Handler(handlerThread?.looper)
  }

  @Synchronized
  override fun onPause() {
    Timber.d("onPause ${this}")
    if (!isFinishing) {
      Timber.d("Requesting finish")
      finish()
    }

    handlerThread?.quitSafely()
    try {
      handlerThread?.join()
      handlerThread = null
      handler = null
    } catch (e: InterruptedException) {
      Timber.e(e, "Exception!")
    }

    super.onPause()
  }

  @Synchronized
  override fun onStop() {
    Timber.d("onStop ${this}")
    super.onStop()
  }

  @Synchronized
  override fun onDestroy() {
    Timber.d("onDestroy ${this}")
    super.onDestroy()
  }

  @Synchronized
  protected fun runInBackground(r: Runnable) = handler?.post(r)

  override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
    if (requestCode == PERMISSIONS_REQUEST) {
      if (grantResults.isNotEmpty()
          && grantResults[0] == PackageManager.PERMISSION_GRANTED
          && grantResults[1] == PackageManager.PERMISSION_GRANTED) {
        setFragment()
      } else {
        requestPermission()
      }
    }
  }

  private fun hasPermission(): Boolean = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
    checkSelfPermission(PERMISSION_CAMERA) == PackageManager.PERMISSION_GRANTED &&
        checkSelfPermission(PERMISSION_STORAGE) == PackageManager.PERMISSION_GRANTED
  } else {
    true
  }

  private fun requestPermission() {
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
      if (shouldShowRequestPermissionRationale(PERMISSION_CAMERA) ||
          shouldShowRequestPermissionRationale(PERMISSION_STORAGE)) {
        Toast.makeText(this,
            "Camera and storage permission are required for this demo", Toast.LENGTH_LONG).show()
      }
    }
    ActivityCompat.requestPermissions(this,
        arrayOf(PERMISSION_CAMERA, PERMISSION_STORAGE), PERMISSIONS_REQUEST)
  }

  private fun isHardwareLevelSupported(characteristics: CameraCharacteristics, requiredLevel: Int): Boolean {
    val deviceLevel = characteristics.get(CameraCharacteristics.INFO_SUPPORTED_HARDWARE_LEVEL)
    if (deviceLevel == CameraCharacteristics.INFO_SUPPORTED_HARDWARE_LEVEL_LEGACY) {
      return requiredLevel == deviceLevel
    }
    return requiredLevel <= deviceLevel
  }

  private fun chooseCamera(): String? {
    val manager = getSystemService(Context.CAMERA_SERVICE) as CameraManager
    try {
      for (cameraId in manager.cameraIdList) {
        val characteristics = manager.getCameraCharacteristics(cameraId)
        val facing = characteristics.get(CameraCharacteristics.LENS_FACING)
        if (facing != null && facing == CameraCharacteristics.LENS_FACING_FRONT) {
          continue
        }

        characteristics.get(CameraCharacteristics.SCALER_STREAM_CONFIGURATION_MAP) ?: continue
        useCamera2API = facing == CameraCharacteristics.LENS_FACING_EXTERNAL || isHardwareLevelSupported(characteristics,
            CameraCharacteristics.INFO_SUPPORTED_HARDWARE_LEVEL_FULL)
        Timber.i("Camera API lv2: $useCamera2API")
        return cameraId
      }
    } catch (e: CameraAccessException) {
      Timber.e(e, "Not allowed to access camera")
    }
    return null
  }

  private fun setFragment() {
    val cameraId = chooseCamera() ?: return
    val camera2Fragment = CameraConnectionFragment.newInstance(
        object : CameraConnectionFragment.ConnectionCallback {
          override fun onPreviewSizeChosen(size: Size, cameraRotation: Int) {
            previewWidth = size.width
            previewHeight = size.height
            this@CameraActivity.onPreviewSizeChosen(size, cameraRotation)
          }
        },
        this,
        getDesiredPreviewFrameSize(),
        cameraId)
    fragmentManager.beginTransaction()
        .replace(R.id.container, camera2Fragment)
        .commit()
  }

  private fun fillBytes(planes: Array<Image.Plane>, yuvBytes: Array<ByteArray?>) {
    for ((index, plane) in planes.withIndex()) {
      val buffer = plane.buffer
      if (yuvBytes[index] == null) {
        Timber.d("Initializing buffer $index at size ${buffer.capacity()}")
        yuvBytes[index] = ByteArray(buffer.capacity(), { 0 })
      }
      buffer.get(yuvBytes[index])
    }
  }

  fun requestRender() = debug_overlay.postInvalidate()

  fun addCallback(callback: OverlayView.DrawCallback) = debug_overlay.addCallback(callback)

  open fun onSetDebug(debug: Boolean) {}

  override fun onKeyDown(keyCode: Int, event: KeyEvent?): Boolean {
    if (keyCode == KeyEvent.KEYCODE_VOLUME_DOWN || keyCode == KeyEvent.KEYCODE_VOLUME_UP) {
      isDebug = isDebug.not()
      requestRender()
      onSetDebug(isDebug)
      return true
    }
    return super.onKeyDown(keyCode, event)
  }

  protected fun readyForNextImage() {
    postInferenceCallback?.run()
  }

  protected fun getScreenOrientation() = when (windowManager.defaultDisplay.rotation) {
    Surface.ROTATION_270 -> 270
    Surface.ROTATION_180 -> 180
    Surface.ROTATION_90 -> 90
    else -> 0
  }

  protected abstract fun processImage()
  protected abstract fun onPreviewSizeChosen(size: Size, rotation: Int)
  protected abstract fun getDesiredPreviewFrameSize(): Size
}