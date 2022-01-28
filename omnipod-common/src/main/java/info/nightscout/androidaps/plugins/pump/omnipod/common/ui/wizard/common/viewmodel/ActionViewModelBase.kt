package info.nightscout.androidaps.plugins.pump.omnipod.common.ui.wizard.common.viewmodel

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import dagger.android.HasAndroidInjector
import info.nightscout.androidaps.data.PumpEnactResult
import info.nightscout.androidaps.utils.rx.AapsSchedulers
import info.nightscout.shared.logging.AAPSLogger
import info.nightscout.shared.logging.LTag
import io.reactivex.Single
import io.reactivex.disposables.CompositeDisposable
import io.reactivex.rxkotlin.plusAssign
import io.reactivex.rxkotlin.subscribeBy

abstract class ActionViewModelBase(
    protected val injector: HasAndroidInjector,
    protected val logger: AAPSLogger,
    private val aapsSchedulers: AapsSchedulers
) : ViewModelBase() {

    protected val disposable = CompositeDisposable()

    private val _isActionExecutingLiveData = MutableLiveData(false)
    val isActionExecutingLiveData: LiveData<Boolean> = _isActionExecutingLiveData

    private val _actionResultLiveData = MutableLiveData<PumpEnactResult?>(null)
    val actionResultLiveData: LiveData<PumpEnactResult?> = _actionResultLiveData

    fun executeAction() {
        _isActionExecutingLiveData.postValue(true)
        disposable += doExecuteAction()
            .subscribeOn(aapsSchedulers.io)
            .observeOn(aapsSchedulers.main)
            .subscribeBy(
                onSuccess = { result ->
                    _isActionExecutingLiveData.postValue(false)
                    _actionResultLiveData.postValue(result)
                },
                onError = { throwable ->
                    logger.error(LTag.PUMP, "Caught exception in while executing action in ActionViewModelBase", throwable)
                    _isActionExecutingLiveData.postValue(false)
                    _actionResultLiveData.postValue(
                        PumpEnactResult(injector).success(false).comment(
                            throwable.message ?: "Caught exception in while executing action in ActionViewModelBase"
                        )
                    )
                })
    }

    override fun onCleared() {
        super.onCleared()
        disposable.clear()
    }

    protected abstract fun doExecuteAction(): Single<PumpEnactResult>
}
