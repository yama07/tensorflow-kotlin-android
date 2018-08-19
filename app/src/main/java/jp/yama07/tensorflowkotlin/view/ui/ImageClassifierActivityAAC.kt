package jp.yama07.tensorflowkotlin.view.ui

import android.arch.lifecycle.LiveData
import android.arch.lifecycle.MediatorLiveData
import android.content.Context
import android.graphics.SurfaceTexture
import android.hardware.camera2.CameraCaptureSession
import android.hardware.camera2.CameraManager
import android.os.Bundle
import android.support.v7.app.AppCompatActivity
import android.view.Surface
import android.view.TextureView
import jp.yama07.tensorflowkotlin.R
import jp.yama07.tensorflowkotlin.service.camera.CameraCaptureSessionData
import jp.yama07.tensorflowkotlin.service.camera.CameraCaptureSessionData.CameraCaptureSessionStateEvents
import jp.yama07.tensorflowkotlin.service.camera.CameraComponent
import jp.yama07.tensorflowkotlin.service.camera.CameraDeviceData.DeviceStateEvents
import jp.yama07.tensorflowkotlin.util.ThreadManagementComponent
import jp.yama07.tensorflowkotlin.util.addSourceNonNullObserve
import jp.yama07.tensorflowkotlin.util.observe
import kotlinx.android.synthetic.main.fragment_camera_connection.*

class ImageClassifierActivityAAC : AppCompatActivity() {

  private lateinit var threadManagementComponent: ThreadManagementComponent
  private lateinit var cameraComponent: CameraComponent
  private val captureManager = MediatorLiveData<Unit>()

  override fun onCreate(savedInstanceState: Bundle?) {
    super.onCreate(savedInstanceState)
    setContentView(R.layout.fragment_camera_connection)


    setupCapture()
  }

  private fun setupCapture() {
    threadManagementComponent = ThreadManagementComponent("ImageCapture")
    val backgroundHandler = threadManagementComponent.handler

    cameraComponent = CameraComponent(
        cameraManager = getSystemService(Context.CAMERA_SERVICE) as CameraManager,
        cameraId = "0", handler = backgroundHandler)

    captureManager.addSourceNonNullObserve(cameraComponent.cameraDeviceLiveData) { cameraDeviceData ->
      var captureSession: CameraCaptureSession? = null
      var captureSessionLiveData: LiveData<CameraCaptureSessionData>? = null
      if (cameraDeviceData.deviceStateEvents == DeviceStateEvents.ON_OPENED) {
        val targetSurfaces = listOf(Surface(texture_view.surfaceTexture))
        val previewCaptureRequest = cameraDeviceData.createPreviewCaptureRequest(targetSurfaces)
            ?: return@addSourceNonNullObserve
        captureSessionLiveData = cameraDeviceData.createCaptureSession(targetSurfaces, backgroundHandler)
        captureManager.addSourceNonNullObserve(captureSessionLiveData) {
          if (it.cameraCaptureSessionStateEvents == CameraCaptureSessionStateEvents.ON_READY) {
            captureSession = it.cameraCaptureSession
            it.setRepeatingRequest(previewCaptureRequest, backgroundHandler)
          }
        }
      } else if (cameraDeviceData.deviceStateEvents == DeviceStateEvents.ON_CLOSED) {
        captureSession?.close()
        captureSessionLiveData?.let { captureManager.removeSource(it) }
      }
    }

    texture_view.surfaceTextureListener = object : TextureView.SurfaceTextureListener {
      override fun onSurfaceTextureSizeChanged(surface: SurfaceTexture?, width: Int, height: Int) {}
      override fun onSurfaceTextureUpdated(surface: SurfaceTexture?) {}
      override fun onSurfaceTextureAvailable(surface: SurfaceTexture?, width: Int, height: Int) {
        lifecycle.addObserver(threadManagementComponent)
        lifecycle.addObserver(cameraComponent)
        captureManager.observe(this@ImageClassifierActivityAAC) {}
      }
      override fun onSurfaceTextureDestroyed(surface: SurfaceTexture?): Boolean {
        captureManager.removeObservers(this@ImageClassifierActivityAAC)
        lifecycle.removeObserver(cameraComponent)
        lifecycle.removeObserver(threadManagementComponent)
        return true
      }
    }
  }

//  override fun onResume() {
//    super.onResume()
//    startBackgroundThread()
//  }
//
//  override fun onPause() {
//    super.onPause()
//    stopBackgroundThread()
//  }
//
//  private var handlerThread: HandlerThread? = null
//  private var handler: Handler? = null
//  private fun startBackgroundThread() {
//    handlerThread = HandlerThread("ImageListener")
//    handlerThread?.start()
//    handler = Handler(handlerThread?.looper)
//  }
//
//  private fun stopBackgroundThread() {
//    handlerThread?.quitSafely()
//    try {
//      handlerThread?.join()
//      handlerThread = null
//      handler = null
//    } catch (e: InterruptedException) {
//      Timber.e(e, "Exception!")
//    }
//  }
}