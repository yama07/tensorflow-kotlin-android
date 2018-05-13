package jp.yama07.tensorflowkotlin.model

import android.hardware.camera2.CameraCaptureSession

class CreateCaptureSessionException private constructor(message: String?) : Exception(message) {
  constructor(session: CameraCaptureSession) : this("Camera Capture Session Failed.") {
    session.close()
  }
}