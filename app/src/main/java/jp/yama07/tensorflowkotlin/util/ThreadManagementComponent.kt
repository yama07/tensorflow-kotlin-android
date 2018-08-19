package jp.yama07.tensorflowkotlin.util

import android.arch.lifecycle.Lifecycle
import android.arch.lifecycle.LifecycleObserver
import android.arch.lifecycle.OnLifecycleEvent
import android.os.Handler
import android.os.HandlerThread
import timber.log.Timber

class ThreadManagementComponent(val name: String) : LifecycleObserver {
  var handlerThread: HandlerThread = HandlerThread(name).also { it.start() }
  var handler: Handler = Handler(handlerThread.looper)

  @OnLifecycleEvent(Lifecycle.Event.ON_RESUME)
  private fun startThread() {
    Timber.d(handlerThread.state.toString())
    if(handlerThread.state != Thread.State.RUNNABLE) {
      handlerThread.start()
    }
  }

  @OnLifecycleEvent(Lifecycle.Event.ON_PAUSE)
  private fun stopThread() {
    handlerThread.quitSafely()
    try {
      handlerThread.join()
    } catch (e: InterruptedException) {
      Timber.e(e, "Exception!")
    }
  }
}