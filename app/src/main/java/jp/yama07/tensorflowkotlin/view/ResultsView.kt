package jp.yama07.tensorflowkotlin.view

import jp.yama07.tensorflowkotlin.service.Classifier

interface ResultsView {
  fun setResults(results: List<Classifier.Recognition>)
}