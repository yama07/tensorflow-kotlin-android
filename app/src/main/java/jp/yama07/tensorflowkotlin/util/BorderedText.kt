package jp.yama07.tensorflowkotlin.util

import android.graphics.*

class BorderedText(private var interiorColor: Int,
                   private var exteriorColor: Int,
                   private val textSize: Float) {
  private var interiorPaint = Paint().also {
    it.color = interiorColor
    it.style = Paint.Style.FILL
    it.isAntiAlias = false
    it.alpha = 255
    it.textSize = textSize
  }
  private var exteriorPaint: Paint = Paint().also {
    it.color = exteriorColor
    it.style = Paint.Style.FILL_AND_STROKE
    it.strokeWidth = textSize / 8
    it.isAntiAlias = false
    it.alpha = 255
    it.textSize = textSize
  }

  constructor(textSize: Float) : this(Color.WHITE, Color.BLACK, textSize)

  fun setTypeface(typeface: Typeface) {
    interiorPaint.typeface = typeface
    exteriorPaint.typeface = typeface
  }

  fun drawText(canvas: Canvas, posX: Float, posY: Float, text: String) {
    canvas.drawText(text, posX, posY, exteriorPaint)
    canvas.drawText(text, posX, posY, interiorPaint)
  }

  fun drawLines(canvas: Canvas, posX: Float, posY: Float, lines: List<String>) {
    lines.withIndex().forEach {
      drawText(canvas, posX, posY - textSize * (lines.size - it.index - 1), it.value)
    }
  }

  fun setAlpha(alpha: Int) {
    interiorPaint.alpha = alpha
    exteriorPaint.alpha = alpha
  }

  fun getTextBounds(line: String, index: Int, count: Int, lineBounds: Rect) {
    interiorPaint.getTextBounds(line, index, count, lineBounds)
  }

  fun setTextAlign(align: Paint.Align) {
    interiorPaint.textAlign = align
    exteriorPaint.textAlign = align
  }
}