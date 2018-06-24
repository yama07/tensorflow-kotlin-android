package jp.yama07.tensorflowkotlin.view

import android.content.Context
import android.graphics.Color
import android.graphics.drawable.Drawable
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
  var ellipsisSize = 100
  var noResultsText = "No Results"
  var sliceColors: IntArray = ColorTemplate.MATERIAL_COLORS
  var sliceAlpha = 1.0f
  var icons = mutableMapOf<String, Drawable>()

  init {
    chart.also {
      it.isHighlightPerTapEnabled = false
      it.isRotationEnabled = false
      it.setHoleColor(Color.alpha(0))
      it.setUsePercentValues(true)
      it.legend.isEnabled = false
      it.description.isEnabled = false
      it.setNoDataText("")
      it.invalidate()
    }
  }

  override fun setResults(results: List<Classifier.Recognition>) {
    var othersValue = 1.0f
    val resultEntries = results
        .take(ellipsisSize)
        .map { result ->
          othersValue -= result.confidence
          PieEntry(result.confidence * 100.0f, result.title, icons[result.title])
        }

    val entries = when {
      results.isEmpty() -> {
        listOf(PieEntry(100.0f, noResultsText))
      }
      results.size <= ellipsisSize -> {
        resultEntries
      }
      else -> {
        resultEntries + PieEntry(othersValue * 100.0f, ellipsisText)
      }
    }

    val dataSet = PieDataSet(entries, "recognition").also {
      it.colors = sliceColors
          .map { Color.argb((sliceAlpha * 255).toInt(), Color.red(it), Color.green(it), Color.blue(it)) }.toList()
      it.setDrawValues(true)
    }

    chart.also {
      it.data = PieData(dataSet)
      it.notifyDataSetChanged()
      it.postInvalidate()
    }
  }
}