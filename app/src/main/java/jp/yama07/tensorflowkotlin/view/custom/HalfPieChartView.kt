package jp.yama07.tensorflowkotlin.view.custom

import android.content.Context
import android.util.AttributeSet
import android.widget.RelativeLayout
import com.github.mikephil.charting.charts.PieChart

open class HalfPieChartView : RelativeLayout {
  enum class ChartPosition {
    ON_TOP, ON_RIGHT, ON_BOTTOM, ON_LEFT
  }

  constructor(context: Context) : super(context)
  constructor(context: Context, attrs: AttributeSet) : super(context, attrs)
  constructor(context: Context, attrs: AttributeSet, defStyle: Int) : super(context, attrs, defStyle)

  val chart: PieChart
  var chartPosition: ChartPosition = ChartPosition.ON_BOTTOM

  init {
    chart = PieChart(context).also {
      it.maxAngle = 180f
      it.layoutParams = LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT)
    }

    addView(chart)
  }

  override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
    super.onMeasure(widthMeasureSpec, heightMeasureSpec)

    when (chartPosition) {
      ChartPosition.ON_TOP -> chart.also {
        it.rotationAngle = 0.0f
        (it.layoutParams as RelativeLayout.LayoutParams).topMargin =
            -MeasureSpec.getSize(heightMeasureSpec)
      }
      ChartPosition.ON_RIGHT -> chart.also {
        it.rotationAngle = 90.0f
        (it.layoutParams as RelativeLayout.LayoutParams).rightMargin =
            -MeasureSpec.getSize(widthMeasureSpec)
      }
      ChartPosition.ON_BOTTOM ->
        chart.also {
          it.rotationAngle = 180f
          (it.layoutParams as RelativeLayout.LayoutParams).bottomMargin =
              -MeasureSpec.getSize(heightMeasureSpec)
        }
      ChartPosition.ON_LEFT -> chart.also {
        it.rotationAngle = 270.0f
        (it.layoutParams as RelativeLayout.LayoutParams).leftMargin =
            -MeasureSpec.getSize(widthMeasureSpec)
      }
    }
  }

}