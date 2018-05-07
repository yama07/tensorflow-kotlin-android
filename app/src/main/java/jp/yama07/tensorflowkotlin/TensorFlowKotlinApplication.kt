package jp.yama07.tensorflowkotlin

import android.app.Application
import timber.log.Timber

class TensorFlowKotlinApplication : Application() {
  override fun onCreate() {
    super.onCreate()

    Timber.plant(Timber.DebugTree())
  }
}