package jp.yama07.tensorflowkotlin.util

import android.arch.lifecycle.LifecycleOwner
import android.arch.lifecycle.LiveData
import android.arch.lifecycle.Observer

fun <T> LiveData<T>.observe(owner: LifecycleOwner, observer: (t: T) -> Unit) {
  this.observe(owner, Observer {
    observer
  })
}

fun <T> LiveData<T>.nonNullObserve(owner: LifecycleOwner, observer: (t: T) -> Unit) {
  this.observe(owner, Observer {
    it?.let(observer)
  })
}
