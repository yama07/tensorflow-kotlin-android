package jp.yama07.tensorflowkotlin.service

import android.graphics.Bitmap
import android.graphics.RectF

interface Classifier {
  data class Recognition(var id: String, var title: String, var confidence: Float, var location: RectF?)

  var isStatLoggingEnabled: Boolean
  var maxResults: Int
  var threshold: Float

  val labels: List<String>

  fun recognizeImage(bitmap: Bitmap): List<Recognition>
  fun getStatString(): String
  fun close()
}