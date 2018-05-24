package jp.yama07.tensorflowkotlin.ui

import android.graphics.SurfaceTexture
import android.hardware.camera2.CameraCaptureSession
import android.hardware.camera2.CameraCharacteristics
import android.hardware.camera2.CameraDevice
import android.hardware.camera2.CameraManager
import android.os.Bundle
import android.support.v7.app.AppCompatActivity
import android.view.Surface
import android.view.TextureView
import androidx.core.content.systemService
import io.reactivex.disposables.CompositeDisposable
import io.reactivex.subjects.PublishSubject
import jp.yama07.tensorflowkotlin.R
import jp.yama07.tensorflowkotlin.model.RxCamera2Manager
import kotlinx.android.synthetic.main.fragment_camera_connection.*
import timber.log.Timber

class RxCameraTestActivity : AppCompatActivity() {
  private val onSurfaceTextureAvailableSubject = PublishSubject.create<SurfaceTexture>()
  private val cameraDisposable: CompositeDisposable = CompositeDisposable()

  private var device: CameraDevice? = null
  private var session: CameraCaptureSession? = null

  override fun onCreate(savedInstanceState: Bundle?) {
    super.onCreate(savedInstanceState)
    setContentView(R.layout.fragment_camera_connection)

    texture_view.surfaceTextureListener = object : TextureView.SurfaceTextureListener {
      override fun onSurfaceTextureSizeChanged(p0: SurfaceTexture?, p1: Int, p2: Int) {}
      override fun onSurfaceTextureUpdated(p0: SurfaceTexture?) {}
      override fun onSurfaceTextureDestroyed(p0: SurfaceTexture?): Boolean = true
      override fun onSurfaceTextureAvailable(surface: SurfaceTexture, p1: Int, p2: Int) {
        onSurfaceTextureAvailableSubject.onNext(surface)
      }
    }
  }

  override fun onDestroy() {
    onSurfaceTextureAvailableSubject.onComplete()
    super.onDestroy()
  }

  override fun onResume() {
    setupCamera()
    super.onResume()
  }

  override fun onPause() {
    teardownCamera()
    super.onPause()
  }

  private fun setupCamera() {
    val manager = systemService<CameraManager>()
    val cameraId = manager.cameraIdList.first {
      val characteristics = manager.getCameraCharacteristics(it)
      val facing = characteristics.get(CameraCharacteristics.LENS_FACING)
      facing != null && facing == CameraCharacteristics.LENS_FACING_FRONT
    }

    val rxCamera = RxCamera2Manager(manager)

    cameraDisposable.add(
        onSurfaceTextureAvailableSubject
            .firstElement()
            .toObservable()
            .flatMap { rxCamera.openCamera(cameraId) }
            .filter { it.deviceStateEvents == RxCamera2Manager.DeviceStateEvents.ON_OPENED }
            .map {
              device = it.cameraDevice
              it.cameraDevice
            }
            .flatMap { device -> rxCamera.createCaptureSession(device, listOf(Surface(texture_view.surfaceTexture))) }
            .filter { it.captureSessionStateEvents == RxCamera2Manager.CaptureSessionStateEvents.ON_CONFIGURED }
            .map {
              session = it.cameraCaptureSession
              it.cameraCaptureSession
            }
            .flatMap {
              val builder = it.device.createCaptureRequest(CameraDevice.TEMPLATE_PREVIEW)
              builder.addTarget(Surface(texture_view.surfaceTexture))
              rxCamera.fromSetRepeatingRequest(it, builder.build())
            }.doOnDispose {
              session?.close()
              device?.close()
            }.subscribe(
                { sessionData ->
                  Timber.d("Capture session event: ${sessionData.event}")
                },
                { ex ->
                  Timber.e(ex)
                }
            )
    )
  }

  private fun teardownCamera() {
    cameraDisposable.clear()
  }
}