package jp.yama07.tensorflowkotlin.view.ui

import android.app.Fragment
import android.content.Context
import android.content.res.Configuration
import android.graphics.ImageFormat
import android.graphics.Matrix
import android.graphics.RectF
import android.graphics.SurfaceTexture
import android.hardware.camera2.*
import android.media.ImageReader
import android.os.Bundle
import android.os.Handler
import android.os.HandlerThread
import android.util.Size
import android.view.*
import android.widget.Toast
import jp.yama07.tensorflowkotlin.R
import kotlinx.android.synthetic.main.fragment_camera_connection.*
import timber.log.Timber
import java.util.concurrent.Semaphore
import java.util.concurrent.TimeUnit

class CameraConnectionFragment : Fragment() {
  private var cameraConnectionCallback: ConnectionCallback? = null
  private var imageListener: ImageReader.OnImageAvailableListener? = null
  private var inputSize: Size? = null
  private var cameraId: String? = null

  companion object {
    private const val MINIMUM_PREVIEW_SIZE = 320

    fun newInstance(connectionCallback: ConnectionCallback,
                    imageListener: ImageReader.OnImageAvailableListener,
                    inputSize: Size,
                    cameraId: String): CameraConnectionFragment = CameraConnectionFragment().also {
      it.cameraConnectionCallback = connectionCallback
      it.imageListener = imageListener
      it.inputSize = inputSize
      it.cameraId = cameraId
    }

    private fun chooseOptionalSize(choices: Array<Size>, width: Int, height: Int): Size {
      val minSize = Math.max(Math.min(width, height), MINIMUM_PREVIEW_SIZE)
      val desiredSize = Size(width, height)

      var exactSizeFound = false
      val bigEnough = mutableListOf<Size>()
      val tooSmall = mutableListOf<Size>()
      choices.forEach {
        if (it == desiredSize) {
          exactSizeFound = true
        }
        if (it.height >= minSize && it.width >= minSize) {
          bigEnough.add(it)
        } else {
          tooSmall.add(it)
        }
      }
      Timber.i("Desired size: $desiredSize, min size: ${minSize}x$minSize")
      Timber.i("Valid preview sizes: [${bigEnough.joinToString()}]")
      Timber.i("Rejected preview sizes: [${tooSmall.joinToString()}]")

      if (exactSizeFound) {
        Timber.i("Exact size match fount.")
        return desiredSize
      }

      return if (0 < bigEnough.size) {
        val chosenSize = bigEnough.minBy { width * height }
        Timber.i("Chosen size: ${chosenSize?.width}x${chosenSize?.height}")
        chosenSize!!
      } else {
        Timber.e("Couldn't find any suitable  preview size")
        choices[0]
      }
    }
  }

  private val surfaceTextureListener = object : TextureView.SurfaceTextureListener {
    override fun onSurfaceTextureAvailable(
        texture: SurfaceTexture, width: Int, height: Int) {
      openCamera(width, height)
    }

    override fun onSurfaceTextureSizeChanged(
        texture: SurfaceTexture, width: Int, height: Int) {
      configureTransform(width, height)
    }

    override fun onSurfaceTextureDestroyed(texture: SurfaceTexture): Boolean {
      return true
    }

    override fun onSurfaceTextureUpdated(texture: SurfaceTexture) {}
  }

  private var captureSession: CameraCaptureSession? = null
  private var cameraDevice: CameraDevice? = null
  private var sensorOrientation: Int? = null
  private lateinit var previewSize: Size

  private val stateCallback = object : CameraDevice.StateCallback() {
    override fun onOpened(cd: CameraDevice) {
      // This method is called when the camera is opened.  We start camera preview here.
      cameraOpenCloseLock.release()
      cameraDevice = cd
      createCameraPreviewSession()
    }

    override fun onDisconnected(cd: CameraDevice) {
      cameraOpenCloseLock.release()
      cd.close()
      cameraDevice = null
    }

    override fun onError(cd: CameraDevice, error: Int) {
      cameraOpenCloseLock.release()
      cd.close()
      cameraDevice = null
      activity?.finish()
    }
  }

  private var backgroundThread: HandlerThread? = null
  private var backgroundHandler: Handler? = null
  private var previewReader: ImageReader? = null
  private var previewRequestBuilder: CaptureRequest.Builder? = null
  private var previewRequest: CaptureRequest? = null
  private val cameraOpenCloseLock = Semaphore(1)

  private fun showToast(text: String) = activity?.runOnUiThread {
    Toast.makeText(activity, text, Toast.LENGTH_SHORT).show()
  }

  override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
    return inflater.inflate(R.layout.fragment_camera_connection, container, false)
  }

  override fun onResume() {
    super.onResume()
    startBackgroundThread()
    if (texture_view.isAvailable) {
      openCamera(texture_view.width, texture_view.height)
    } else {
      texture_view.surfaceTextureListener = surfaceTextureListener
    }
  }

  override fun onPause() {
    closeCamera()
    stopBackgroundThread()
    super.onPause()
  }

  private fun setUpCameraOutputs() {
    val (width, height) = inputSize?.let { Pair(it.width, it.height) } ?: return

    val manager = activity.getSystemService(Context.CAMERA_SERVICE) as CameraManager

    try {
      val characteristics = manager.getCameraCharacteristics(cameraId)
      val map = characteristics.get(CameraCharacteristics.SCALER_STREAM_CONFIGURATION_MAP)
      sensorOrientation = characteristics.get(CameraCharacteristics.SENSOR_ORIENTATION)
      previewSize = chooseOptionalSize(map.getOutputSizes(SurfaceTexture::class.java), width, height)

      val orientation = resources.configuration.orientation
      if (orientation == Configuration.ORIENTATION_LANDSCAPE) {
        texture_view.setAspectRation(previewSize.width, previewSize.height)
      } else {
        texture_view.setAspectRation(previewSize.height, previewSize.width)
      }
    } catch (e: CameraAccessException) {
      Timber.e(e, "Exception!")
    }
    cameraConnectionCallback?.onPreviewSizeChosen(previewSize, sensorOrientation!!)
  }

  private fun openCamera(width: Int, height: Int) {
    setUpCameraOutputs()
    configureTransform(width, height)
    val manager = activity.getSystemService(Context.CAMERA_SERVICE) as CameraManager
    try {
      if (!cameraOpenCloseLock.tryAcquire(2500, TimeUnit.MILLISECONDS)) {
        throw RuntimeException("Time out waiting to lock camera opening.")
      }
      manager.openCamera(cameraId, stateCallback, backgroundHandler)
    } catch (e: CameraAccessException) {
      Timber.e(e, "Exception!")
    } catch (e: InterruptedException) {
      throw RuntimeException("Interrupted while trying to lock camera opening.", e)
    } catch (e: SecurityException) {
      throw RuntimeException("Security Exception.", e)
    }
  }

  private fun closeCamera() {
    try {
      cameraOpenCloseLock.acquire()
      captureSession?.close()
      captureSession = null
      cameraDevice?.close()
      cameraDevice = null
      previewReader?.close()
      previewReader = null
    } catch (e: InterruptedException) {
      throw RuntimeException("Interrupted while trying to lock camera closing.", e)
    } finally {
      cameraOpenCloseLock.release()
    }
  }

  private fun startBackgroundThread() {
    backgroundThread = HandlerThread("ImageListener")
    backgroundThread?.start()
    backgroundHandler = Handler(backgroundThread?.looper)
  }

  private fun stopBackgroundThread() {
    backgroundThread?.quitSafely()
    try {
      backgroundThread?.join()
      backgroundThread = null
      backgroundHandler = null
    } catch (e: InterruptedException) {
      Timber.e(e, "Exception!")
    }
  }

  private val captureCallback = object : CameraCaptureSession.CaptureCallback() {
    override fun onCaptureProgressed(session: CameraCaptureSession?, request: CaptureRequest?, partialResult: CaptureResult?) {}
    override fun onCaptureCompleted(session: CameraCaptureSession?, request: CaptureRequest?, result: TotalCaptureResult?) {}
  }

  private fun createCameraPreviewSession() {
    try {
      val texture = texture_view.surfaceTexture
      texture.setDefaultBufferSize(previewSize.width, previewSize.height)
      val surface = Surface(texture)

      previewRequestBuilder = cameraDevice?.createCaptureRequest(CameraDevice.TEMPLATE_PREVIEW)
      previewRequestBuilder?.addTarget(surface)

      Timber.i("Opening camera preview: ${previewSize.width}x${previewSize.height}")

      previewReader = ImageReader.newInstance(
          previewSize.width, previewSize.height, ImageFormat.YUV_420_888, 2)

      previewReader?.setOnImageAvailableListener(imageListener, backgroundHandler)
      previewRequestBuilder?.addTarget(previewReader?.surface)

      cameraDevice?.createCaptureSession(
          mutableListOf(surface, previewReader?.surface),
          object : CameraCaptureSession.StateCallback() {
            override fun onConfigured(cameraCaptureSession: CameraCaptureSession) {
              if (cameraDevice == null) return
              captureSession = cameraCaptureSession
              try {
                previewRequestBuilder?.set(CaptureRequest.CONTROL_AF_MODE,
                    CaptureRequest.CONTROL_AF_MODE_CONTINUOUS_PICTURE)
                previewRequestBuilder?.set(CaptureRequest.CONTROL_AE_MODE,
                    CaptureRequest.CONTROL_AE_MODE_ON_AUTO_FLASH)

                previewRequest = previewRequestBuilder?.build()
                captureSession?.setRepeatingRequest(previewRequest, captureCallback, backgroundHandler)
              } catch (e: CameraAccessException) {
                Timber.e(e, "Exception!")
              }
            }

            override fun onConfigureFailed(p0: CameraCaptureSession?) {
              showToast("Failed")
            }
          },
          null
      )
    } catch (e: CameraAccessException) {
      Timber.e(e, "Exception!")
    }
  }

  private fun configureTransform(viewWidth: Int, viewHeight: Int) {
    activity ?: return

    val rotation = activity.windowManager.defaultDisplay.rotation
    val matrix = Matrix()
    val viewRect = RectF(0f, 0f, viewWidth.toFloat(), viewHeight.toFloat())
    val bufferRect = RectF(0f, 0f, previewSize.height.toFloat(), previewSize.width.toFloat())
    val centerX = viewRect.centerX()
    val centerY = viewRect.centerY()
    when (rotation) {
      Surface.ROTATION_90, Surface.ROTATION_270 -> {
        bufferRect.offset(centerX - bufferRect.centerX(), centerY - bufferRect.centerY())
        matrix.setRectToRect(viewRect, bufferRect, Matrix.ScaleToFit.FILL)
        val scale = Math.max(viewHeight.toFloat() / previewSize.height, viewWidth.toFloat() / previewSize.width)
        matrix.postScale(scale, scale, centerX, centerY)
        matrix.postRotate(90f * (rotation - 2), centerX, centerY)
      }
      Surface.ROTATION_180 -> {
        matrix.postRotate(180f, centerX, centerY)
      }
    }
    texture_view.setTransform(matrix)
  }

  interface ConnectionCallback {
    fun onPreviewSizeChosen(size: Size, cameraRotation: Int)
  }
}
