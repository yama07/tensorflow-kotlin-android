package jp.yama07.tensorflowkotlin.model

import android.hardware.camera2.*
import android.view.Surface
import io.reactivex.Observable

class RxCamera2Manager(private val cameraManager: CameraManager) {
  enum class DeviceStateEvents {
    ON_OPENED, ON_CLOSE, ON_DISCONNECTED
  }

  data class OpenCameraData(
      val deviceStateEvents: DeviceStateEvents,
      val cameraDevice: CameraDevice
  )

  fun openCamera(cameraId: String): Observable<OpenCameraData> =
      Observable.create { emitter ->
        try {
          cameraManager.openCamera(cameraId, object : CameraDevice.StateCallback() {
            override fun onOpened(cameraDevice: CameraDevice) {
              emitter.onNext(OpenCameraData(DeviceStateEvents.ON_OPENED, cameraDevice))
            }

            override fun onClosed(cameraDevice: CameraDevice) {
              super.onClosed(cameraDevice)
              emitter.onNext(OpenCameraData(DeviceStateEvents.ON_CLOSE, cameraDevice))
              emitter.onComplete()
            }

            override fun onDisconnected(cameraDevice: CameraDevice) {
              emitter.onNext(OpenCameraData(DeviceStateEvents.ON_DISCONNECTED, cameraDevice))
              emitter.onComplete()
            }

            override fun onError(cameraDevice: CameraDevice, errorCode: Int) {
              emitter.onError(OpenCameraException(errorCode))
            }
          }, null)
        } catch (ex: SecurityException) {
          emitter.onError(ex)
        }
      }

  enum class CaptureSessionStateEvents {
    ON_CONFIGURED, ON_READY, ON_ACTIVE, ON_CLOSE, ON_SURFACE_PREPARED
  }

  data class CreateCaptureSessionData(
      val captureSessionStateEvents: CaptureSessionStateEvents,
      val cameraCaptureSession: CameraCaptureSession
  )

  fun createCaptureSession(cameraDevice: CameraDevice, surfaceList: List<Surface>)
      : Observable<CreateCaptureSessionData> =
      Observable.create { emitter ->
        cameraDevice.createCaptureSession(surfaceList, object : CameraCaptureSession.StateCallback() {
          override fun onConfigured(session: CameraCaptureSession) {
            emitter.onNext(CreateCaptureSessionData(CaptureSessionStateEvents.ON_CONFIGURED, session))
          }

          override fun onConfigureFailed(session: CameraCaptureSession) {
            cameraDevice.close()
            emitter.onError(CreateCaptureSessionException(session))
          }

          override fun onReady(session: CameraCaptureSession) {
            super.onReady(session)
            emitter.onNext(CreateCaptureSessionData(CaptureSessionStateEvents.ON_READY, session))
          }

          override fun onActive(session: CameraCaptureSession) {
            super.onActive(session)
            emitter.onNext(CreateCaptureSessionData(CaptureSessionStateEvents.ON_ACTIVE, session))
          }

          override fun onClosed(session: CameraCaptureSession) {
            super.onClosed(session)
            cameraDevice.close()
            emitter.onNext(CreateCaptureSessionData(CaptureSessionStateEvents.ON_CLOSE, session))
            emitter.onComplete()
          }

          override fun onSurfacePrepared(session: CameraCaptureSession, surface: Surface?) {
            super.onSurfacePrepared(session, surface)
            emitter.onNext(CreateCaptureSessionData(CaptureSessionStateEvents.ON_SURFACE_PREPARED, session))
          }
        }, null)
      }


  enum class CaptureSessionEvents {
    ON_STARTED, ON_PROGRESSED, ON_COMPLETED, ON_SEQUENCE_COMPLETED, ON_SEQUENCE_ABORTED
  }

  data class CaptureSessionData(
      val event: CaptureSessionEvents,
      val session: CameraCaptureSession,
      val request: CaptureRequest,
      val result: CaptureResult
  )

  fun fromSetRepeatingRequest(captureSession: CameraCaptureSession, request: CaptureRequest)
      : Observable<CaptureSessionData> = Observable.create { emitter ->
    captureSession.setRepeatingRequest(request,
        object : CameraCaptureSession.CaptureCallback() {
          override fun onCaptureCompleted(session: CameraCaptureSession, request: CaptureRequest, result: TotalCaptureResult) {
            super.onCaptureCompleted(session, request, result)
            if (!emitter.isDisposed) {
              emitter.onNext(CaptureSessionData(CaptureSessionEvents.ON_COMPLETED, session, request, result))
            }
          }
        },null)
      }

}