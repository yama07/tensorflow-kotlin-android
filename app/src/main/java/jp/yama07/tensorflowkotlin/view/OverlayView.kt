package jp.yama07.tensorflowkotlin.view

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Canvas
import android.util.AttributeSet
import android.view.View

class OverlayView(context: Context, attrs: AttributeSet) : View(context, attrs) {
  private val callbacks = mutableListOf<DrawCallback>()

  interface DrawCallback {
    fun drawCallback(canvas: Canvas)
  }

  fun addCallback(callback: DrawCallback) = callbacks.add(callback)

  @SuppressLint("MissingSuperCall")
  override fun draw(canvas: Canvas?) {
    canvas?.let { c ->
      callbacks.forEach { it.drawCallback(c) }
    }
  }
}