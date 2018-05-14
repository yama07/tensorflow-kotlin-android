package jp.yama07.tensorflowkotlin.ui

import android.graphics.SurfaceTexture
import android.hardware.camera2.CameraCharacteristics
import android.hardware.camera2.CameraDevice
import android.hardware.camera2.CameraManager
import android.os.Bundle
import android.support.v7.app.AppCompatActivity
import android.view.Surface
import android.view.TextureView
import androidx.core.content.systemService
import io.reactivex.subjects.PublishSubject
import jp.yama07.tensorflowkotlin.R
import jp.yama07.tensorflowkotlin.model.CameraModel
import kotlinx.android.synthetic.main.fragment_camera_connection.*

class RxCameraTestActivity : AppCompatActivity() {
  private val onSurfaceTextureAvailable = PublishSubject.create<SurfaceTexture>()

  override fun onCreate(savedInstanceState: Bundle?) {
    super.onCreate(savedInstanceState)
    setContentView(R.layout.fragment_camera_connection)
    texture_view.surfaceTextureListener = object : TextureView.SurfaceTextureListener {
      override fun onSurfaceTextureSizeChanged(p0: SurfaceTexture?, p1: Int, p2: Int) {}
      override fun onSurfaceTextureUpdated(p0: SurfaceTexture?) {}
      override fun onSurfaceTextureDestroyed(p0: SurfaceTexture?): Boolean = true
      override fun onSurfaceTextureAvailable(surface: SurfaceTexture, p1: Int, p2: Int) {
        onSurfaceTextureAvailable.onNext(surface)
      }
    }

    val manager = systemService<CameraManager>()
    val cameraId = manager.cameraIdList.first {
      val characteristics = manager.getCameraCharacteristics(it)
      val facing = characteristics.get(CameraCharacteristics.LENS_FACING)
      facing != null && facing == CameraCharacteristics.LENS_FACING_FRONT
    }

    onSurfaceTextureAvailable
        .firstElement()
        .toObservable()
        .flatMap { CameraModel(this).openCamera(cameraId, manager) }
        .filter { pair -> pair.first == CameraModel.DeviceStateEvents.ON_OPENED }
        .map { pair -> pair.second }
        .flatMap { device -> CameraModel(this).createCaptureSession(device, listOf(Surface(texture_view.surfaceTexture))) }
        .filter { pair -> pair.first == CameraModel.CaptureSessionStateEvents.ON_CONFIGURED }
        .map { pair -> pair.second }
        .flatMap {
          val builder = it.device.createCaptureRequest(CameraDevice.TEMPLATE_PREVIEW)
          builder.addTarget(Surface(texture_view.surfaceTexture))
          CameraModel(this).fromSetRepeatingRequest(it, builder.build())
        }
        .subscribe()
  }
}