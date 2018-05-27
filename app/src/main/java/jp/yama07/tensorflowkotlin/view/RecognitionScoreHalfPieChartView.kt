package jp.yama07.tensorflowkotlin.view

import android.content.Context
import android.graphics.Color
import android.util.AttributeSet
import com.github.mikephil.charting.data.PieData
import com.github.mikephil.charting.data.PieDataSet
import com.github.mikephil.charting.data.PieEntry
import com.github.mikephil.charting.utils.ColorTemplate
import jp.yama07.tensorflowkotlin.service.Classifier

class RecognitionScoreHalfPieChartView : HalfPieChartView, ResultsView {
  constructor(context: Context) : super(context)
  constructor(context: Context, attrs: AttributeSet) : super(context, attrs)
  constructor(context: Context, attrs: AttributeSet, defStyle: Int) : super(context, attrs, defStyle)

  var ellipsisText = "Others"
  var ellipsisSize = 10
  var noResultsText = "No Results"
  var sliceColors: IntArray = ColorTemplate.MATERIAL_COLORS
  var sliceAlpha = 80

  init {
    chart.also {
      it.isHighlightPerTapEnabled = false
      it.isRotationEnabled = false
      it.setHoleColor(Color.alpha(0))
      it.setUsePercentValues(true)
      it.legend.isEnabled = false
      it.description.isEnabled = false
      it.invalidate()
    }

    setResults(emptyList())
  }

  override fun setResults(results: List<Classifier.Recognition>) {
    var othersValue = 1.0f
    val entry = results
        .take(ellipsisSize)
        .map {
          othersValue -= it.confidence
          PieEntry(it.confidence * 100.0f, it.title)
        }

    val othersEntry = PieEntry(othersValue * 100.0f,
        if (entry.isEmpty()) noResultsText else ellipsisText)
    val dataSet = PieDataSet(entry + othersEntry, "recognition").also {
      it.colors = sliceColors
          .map { Color.argb(sliceAlpha, Color.red(it), Color.green(it), Color.blue(it)) }.toList()
      it.setDrawValues(true)
    }

    chart.also {
      it.data = PieData(dataSet)

      it.notifyDataSetChanged()
      it.invalidate()
    }
  }
}