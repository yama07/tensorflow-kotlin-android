package jp.yama07.tensorflowkotlin.view

import android.content.Context
import android.util.AttributeSet
import android.view.TextureView

class AutoFitTextureView : TextureView {
  private var ratioWidth = 0
  private var ratioHeight = 0

  constructor(context: Context) : super(context)
  constructor(context: Context, attrs: AttributeSet) : super(context, attrs)
  constructor(context: Context, attrs: AttributeSet, defStyle: Int) : super(context, attrs, defStyle)

  fun setAspectRation(width: Int, height: Int) {
    if (width < 0 || height < 0) {
      throw IllegalArgumentException("Size cannot be negative.")
    }
    ratioWidth = width
    ratioHeight = height
    requestLayout()
  }

  override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
    super.onMeasure(widthMeasureSpec, heightMeasureSpec)
    val width = MeasureSpec.getSize(widthMeasureSpec)
    val height = MeasureSpec.getSize(heightMeasureSpec)
    if (0 == ratioWidth || 0 == ratioHeight) {
      setMeasuredDimension(width, height)
    } else {
      if (width < height * ratioWidth / ratioHeight) {
        setMeasuredDimension(width, width * ratioHeight / ratioWidth)
      } else {
        setMeasuredDimension(height * ratioWidth / ratioHeight, height)
      }
    }
  }
}