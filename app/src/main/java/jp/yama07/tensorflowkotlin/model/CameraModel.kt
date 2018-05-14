package jp.yama07.tensorflowkotlin.model

import android.annotation.SuppressLint
import android.content.Context
import android.hardware.camera2.*
import android.view.Surface
import io.reactivex.Observable
import io.reactivex.ObservableEmitter

class CameraModel(context: Context) {
  enum class DeviceStateEvents {
    ON_OPENED, ON_CLOSE, ON_DISCONNECTED
  }

  @SuppressLint("MissingPermission")
  fun openCamera(cameraId: String, cameraManager: CameraManager)
      : Observable<Pair<DeviceStateEvents, CameraDevice>> =
      Observable.create { emitter ->
        cameraManager.openCamera(cameraId, object : CameraDevice.StateCallback() {
          override fun onOpened(cameraDevice: CameraDevice) {
            emitter.onNext(Pair(DeviceStateEvents.ON_OPENED, cameraDevice))
          }

          override fun onClosed(cameraDevice: CameraDevice) {
            super.onClosed(cameraDevice)
            emitter.onNext(Pair(DeviceStateEvents.ON_CLOSE, cameraDevice))
            emitter.onComplete()
          }

          override fun onDisconnected(cameraDevice: CameraDevice) {
            emitter.onNext(Pair(DeviceStateEvents.ON_DISCONNECTED, cameraDevice))
            emitter.onComplete()
          }

          override fun onError(cameraDevice: CameraDevice, errorCode: Int) {
            emitter.onError(OpenCameraException(errorCode))
          }
        }, null)
      }

  enum class CaptureSessionStateEvents {
    ON_CONFIGURED, ON_READY, ON_ACTIVE, ON_CLOSE, ON_SURFACE_PREPARED
  }

  fun createCaptureSession(cameraDevice: CameraDevice, surfaceList: List<Surface>)
      : Observable<Pair<CaptureSessionStateEvents, CameraCaptureSession>> =
      Observable.create { emitter ->
        cameraDevice.createCaptureSession(surfaceList, object : CameraCaptureSession.StateCallback() {
          override fun onConfigured(session: CameraCaptureSession) {
            emitter.onNext(Pair(CaptureSessionStateEvents.ON_CONFIGURED, session))
          }

          override fun onConfigureFailed(session: CameraCaptureSession) {
            emitter.onError(CreateCaptureSessionException(session))
          }

          override fun onReady(session: CameraCaptureSession) {
            super.onReady(session)
            emitter.onNext(Pair(CaptureSessionStateEvents.ON_READY, session))
          }

          override fun onActive(session: CameraCaptureSession) {
            super.onActive(session)
            emitter.onNext(Pair(CaptureSessionStateEvents.ON_ACTIVE, session))
          }

          override fun onClosed(session: CameraCaptureSession) {
            super.onClosed(session)
            emitter.onNext(Pair(CaptureSessionStateEvents.ON_CLOSE, session))
            emitter.onComplete()
          }

          override fun onSurfacePrepared(session: CameraCaptureSession, surface: Surface?) {
            super.onSurfacePrepared(session, surface)
            emitter.onNext(Pair(CaptureSessionStateEvents.ON_SURFACE_PREPARED, session))
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
      val result: CaptureResult)

  fun createCaptureCallBack(emitter: ObservableEmitter<CaptureSessionData>)
      : CameraCaptureSession.CaptureCallback = object : CameraCaptureSession.CaptureCallback() {
    override fun onCaptureCompleted(session: CameraCaptureSession, request: CaptureRequest, result: TotalCaptureResult) {
      super.onCaptureCompleted(session, request, result)
      if (!emitter.isDisposed) {
        emitter.onNext(CaptureSessionData(CaptureSessionEvents.ON_COMPLETED, session, request, result))
      }
    }

    override fun onCaptureFailed(session: CameraCaptureSession, request: CaptureRequest, failure: CaptureFailure) {
      super.onCaptureFailed(session, request, failure)
      if (!emitter.isDisposed) {
        emitter.onError(CameraCaptureFailedException(failure))
      }
    }
  }

  fun fromSetRepeatingRequest(captureSession: CameraCaptureSession, request: CaptureRequest)
      : Observable<CaptureSessionData> = Observable.create { emitter ->
    captureSession.setRepeatingRequest(request, createCaptureCallBack(emitter), null)
  }

  fun fromCapture(captureSession: CameraCaptureSession, request: CaptureRequest)
      : Observable<CaptureSessionData> = Observable.create { emitter ->
    captureSession.capture(request, createCaptureCallBack(emitter), null)
  }


}