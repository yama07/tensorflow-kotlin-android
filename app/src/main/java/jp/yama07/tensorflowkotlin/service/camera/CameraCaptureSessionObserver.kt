package jp.yama07.tensorflowkotlin.service.camera

import android.arch.lifecycle.Lifecycle
import android.arch.lifecycle.LifecycleObserver
import android.arch.lifecycle.LiveData
import android.arch.lifecycle.MutableLiveData
import android.arch.lifecycle.OnLifecycleEvent
import android.hardware.camera2.CameraCaptureSession
import android.os.Handler
import android.view.Surface
import jp.yama07.tensorflowkotlin.service.camera.CameraCaptureSessionData.CameraCaptureSessionStateEvents
import jp.yama07.tensorflowkotlin.service.camera.CameraDeviceObserver.CameraDeviceData
import timber.log.Timber

class CameraCaptureSessionObserver(
  private val cameraDeviceData: CameraDeviceData,
  private val surfaceList: List<Surface>,
  private val handler: Handler
) : LifecycleObserver {

  private val cameraCaptureSessionLiveData = MutableLiveData<CameraCaptureSessionData>()

  fun open(): LiveData<CameraCaptureSessionData> {
    cameraCaptureSessionLiveData.postValue(
        CameraCaptureSessionData(
            CameraCaptureSessionStateEvents.ON_PREPARED,
            null
        )
    )
    openSession()
    return cameraCaptureSessionLiveData
  }

  @OnLifecycleEvent(Lifecycle.Event.ON_RESUME)
  private fun openSession() {
    val device = cameraDeviceData.cameraDevice
    device?.createCaptureSession(surfaceList, object : CameraCaptureSession.StateCallback() {
      override fun onConfigured(session: CameraCaptureSession) {
        cameraCaptureSessionLiveData.postValue(
            CameraCaptureSessionData(
                CameraCaptureSessionStateEvents.ON_CONFIGURED,
                session
            )
        )
      }

      override fun onConfigureFailed(session: CameraCaptureSession) {
        cameraCaptureSessionLiveData.postValue(
            CameraCaptureSessionData(
                CameraCaptureSessionStateEvents.ON_CONFIGURE_FAILED,
                session
            )
        )
        device.close()

        Timber.e(CreateCaptureSessionException(session), "Exception!")
      }

      override fun onReady(session: CameraCaptureSession) {
        super.onReady(session)
        cameraCaptureSessionLiveData.postValue(
            CameraCaptureSessionData(
                CameraCaptureSessionStateEvents.ON_READY,
                session
            )
        )
      }

      override fun onActive(session: CameraCaptureSession) {
        super.onActive(session)
        cameraCaptureSessionLiveData.postValue(
            CameraCaptureSessionData(
                CameraCaptureSessionStateEvents.ON_ACTIVE,
                session
            )
        )
      }

      override fun onClosed(session: CameraCaptureSession) {
        super.onClosed(session)
        device.close()
        cameraCaptureSessionLiveData.postValue(
            CameraCaptureSessionData(
                CameraCaptureSessionStateEvents.ON_CLOSE,
                session
            )
        )
      }

      override fun onSurfacePrepared(
        session: CameraCaptureSession,
        surface: Surface
      ) {
        super.onSurfacePrepared(session, surface)
        cameraCaptureSessionLiveData.postValue(
            CameraCaptureSessionData(
                CameraCaptureSessionStateEvents.ON_SURFACE_PREPARED,
                session
            )
        )
      }
    }, handler)
  }

  fun close() {
    closeSession()
    cameraCaptureSessionLiveData.postValue(null)
  }

  @OnLifecycleEvent(Lifecycle.Event.ON_PAUSE)
  private fun closeSession() {
    var data = cameraCaptureSessionLiveData.value ?: return
    data.cameraCaptureSession?.close()
  }

}