package jp.yama07.tensorflowkotlin.view.custom

import jp.yama07.tensorflowkotlin.service.Classifier

interface ResultsView {
  fun setResults(results: List<Classifier.Recognition>)
}