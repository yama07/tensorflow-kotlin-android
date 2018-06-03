package jp.yama07.tensorflowkotlin.view

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.util.AttributeSet
import android.util.TypedValue
import android.view.View
import jp.yama07.tensorflowkotlin.R
import jp.yama07.tensorflowkotlin.service.Classifier

class RecognitionScoreView(context: Context, attrs: AttributeSet) : View(context, attrs), ResultsView {
  companion object {
    private const val TEXT_SIZE_DP = 24f
  }

  private var results: List<Classifier.Recognition>? = null
  private val textSizePx = TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, TEXT_SIZE_DP, resources.displayMetrics)
  private val fgPaint: Paint = Paint().apply { textSize = textSizePx }
  private val bgPaint: Paint = Paint().apply { color = context.getColor(R.color.colorPrimaryLight) }

  override fun setResults(results: List<Classifier.Recognition>) {
    this.results = results
    postInvalidate()
  }

  override fun onDraw(canvas: Canvas?) {
    val x = 10f
    var y = fgPaint.textSize * 1.5f

    canvas?.also { c ->
      c.drawPaint(bgPaint)
      results?.forEach { result ->
        c.drawText("${result.title}: ${result.confidence}", x, y, fgPaint)
        y += fgPaint.textSize * 1.5f
      }
    }
  }
}