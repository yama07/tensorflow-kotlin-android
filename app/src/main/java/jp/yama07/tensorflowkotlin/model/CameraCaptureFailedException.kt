package jp.yama07.tensorflowkotlin.model

import android.hardware.camera2.CaptureFailure

class CameraCaptureFailedException private constructor(message: String?) : Exception(message) {
  constructor(failure: CaptureFailure) : this("Camera Capture Failed.")
}