package info.nightscout.androidaps.plugins.pump.eopatch.extension

import info.nightscout.rx.logging.AAPSLogger
import info.nightscout.rx.logging.LTag

import io.reactivex.rxjava3.android.schedulers.AndroidSchedulers
import io.reactivex.rxjava3.core.Single
import io.reactivex.rxjava3.disposables.Disposable
import io.reactivex.rxjava3.schedulers.Schedulers

fun <T: Any> Single<T>.subscribeDefault(aapsLogger: AAPSLogger, onSuccess: (T) -> Unit): Disposable = subscribe(onSuccess) {
    aapsLogger.error(LTag.PUMP, "onError", it)
}

fun <T: Any> Single<T>.with(): Single<T> = subscribeOn(Schedulers.io()).observeOn(AndroidSchedulers.mainThread())
