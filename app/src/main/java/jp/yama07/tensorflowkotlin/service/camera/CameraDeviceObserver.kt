package jp.yama07.tensorflowkotlin.service.camera

import android.arch.lifecycle.Lifecycle
import android.arch.lifecycle.LifecycleObserver
import android.arch.lifecycle.LiveData
import android.arch.lifecycle.MutableLiveData
import android.arch.lifecycle.OnLifecycleEvent
import android.hardware.camera2.CameraDevice
import android.hardware.camera2.CameraManager
import android.hardware.camera2.CaptureRequest
import android.os.Handler
import android.view.Surface
import jp.yama07.tensorflowkotlin.service.camera.CameraDeviceObserver.DeviceStateEvents.ON_CLOSED
import jp.yama07.tensorflowkotlin.service.camera.CameraDeviceObserver.DeviceStateEvents.ON_DISCONNECTED
import jp.yama07.tensorflowkotlin.service.camera.CameraDeviceObserver.DeviceStateEvents.ON_ERROR
import jp.yama07.tensorflowkotlin.service.camera.CameraDeviceObserver.DeviceStateEvents.ON_OPENED
import jp.yama07.tensorflowkotlin.service.camera.CameraDeviceObserver.DeviceStateEvents.ON_PREPARED
import timber.log.Timber

class CameraDeviceObserver(
  private val cameraManager: CameraManager,
  private val cameraId: String,
  private val handler: Handler
) : LifecycleObserver {

  enum class DeviceStateEvents {
    ON_PREPARED,
    ON_OPENED,
    ON_CLOSED,
    ON_DISCONNECTED,
    ON_ERROR
  }

  class CameraDeviceData(
    val deviceStateEvents: DeviceStateEvents,
    val cameraDevice: CameraDevice?
  ) {
    fun createTemplatePreviewCaptureRequest(surfaceList: List<Surface>): CaptureRequest? {
      cameraDevice ?: return null
      return cameraDevice.createCaptureRequest(CameraDevice.TEMPLATE_PREVIEW)
          .also { builder ->
            surfaceList.forEach {
              builder.addTarget(it)
            }
          }
          ?.build()
    }
  }

  private val cameraDeviceLiveData = MutableLiveData<CameraDeviceData>()

  fun open(): LiveData<CameraDeviceData> {
    cameraDeviceLiveData.postValue(
        CameraDeviceData(
            ON_PREPARED,
            null
        )
    )
    openDevice()
    return cameraDeviceLiveData
  }

  @OnLifecycleEvent(Lifecycle.Event.ON_RESUME)
  private fun openDevice() {
    cameraDeviceLiveData.value ?: return
    try {
      cameraManager.openCamera(cameraId, object : CameraDevice.StateCallback() {
        override fun onOpened(camera: CameraDevice) {
          cameraDeviceLiveData.postValue(
              CameraDeviceData(
                  ON_OPENED,
                  camera
              )
          )
        }

        override fun onClosed(camera: CameraDevice) {
          super.onClosed(camera)
          cameraDeviceLiveData.postValue(
              CameraDeviceData(
                  ON_CLOSED,
                  camera
              )
          )
        }

        override fun onDisconnected(camera: CameraDevice) {
          cameraDeviceLiveData.postValue(
              CameraDeviceData(
                  ON_DISCONNECTED,
                  camera
              )
          )
          camera.close()
        }

        override fun onError(
          camera: CameraDevice,
          error: Int
        ) {
          cameraDeviceLiveData.postValue(
              CameraDeviceData(
                  ON_ERROR,
                  camera
              )
          )
          camera.close()
          Timber.e(OpenCameraException(error), "Exception!")
        }
      }, handler)
    } catch (ex: SecurityException) {
      cameraDeviceLiveData.postValue(
          CameraDeviceData(
              ON_ERROR,
              null
          )
      )
      Timber.e(ex, "Exception!")
    }
  }

  fun close() {
    closeDevice()
    cameraDeviceLiveData.postValue(null)
  }

  @OnLifecycleEvent(Lifecycle.Event.ON_PAUSE)
  private fun closeDevice() {
    var data = cameraDeviceLiveData.value ?: return
    data.cameraDevice?.close()
  }

}