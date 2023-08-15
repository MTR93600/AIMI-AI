package info.nightscout.pump.medtrum.ui.viewmodel

import androidx.lifecycle.LiveData
import info.nightscout.pump.medtrum.code.EventType
import info.nightscout.pump.medtrum.ui.MedtrumBaseNavigator
import info.nightscout.pump.medtrum.ui.event.SingleLiveEvent
import info.nightscout.pump.medtrum.ui.event.UIEvent
import info.nightscout.interfaces.profile.ProfileFunction
import info.nightscout.interfaces.queue.CommandQueue
import info.nightscout.pump.medtrum.MedtrumPump
import info.nightscout.pump.medtrum.R
import info.nightscout.pump.medtrum.code.ConnectionState
import info.nightscout.pump.medtrum.comm.enums.MedtrumPumpState
import info.nightscout.rx.logging.AAPSLogger
import info.nightscout.rx.logging.LTag
import info.nightscout.shared.interfaces.ResourceHelper
import info.nightscout.shared.utils.DateUtil
import info.nightscout.shared.utils.T
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import javax.inject.Inject

class MedtrumOverviewViewModel @Inject constructor(
    private val aapsLogger: AAPSLogger,
    private val rh: ResourceHelper,
    private val profileFunction: ProfileFunction,
    private val commandQueue: CommandQueue,
    private val dateUtil: DateUtil,
    val medtrumPump: MedtrumPump
) : BaseViewModel<MedtrumBaseNavigator>() {

    private val scope = CoroutineScope(Dispatchers.Default)

    private val _eventHandler = SingleLiveEvent<UIEvent<EventType>>()
    val eventHandler: LiveData<UIEvent<EventType>>
        get() = _eventHandler

    private val _canDoRefresh = SingleLiveEvent<Boolean>()
    val canDoRefresh: LiveData<Boolean>
        get() = _canDoRefresh

    private val _canDoResetAlarms = SingleLiveEvent<Boolean>()
    val canDoResetAlarms: LiveData<Boolean>
        get() = _canDoResetAlarms

    private val _bleStatus = SingleLiveEvent<String>()
    val bleStatus: LiveData<String>
        get() = _bleStatus

    private val _lastConnectionMinAgo = SingleLiveEvent<String>()
    val lastConnectionMinAgo: LiveData<String>
        get() = _lastConnectionMinAgo

    private val _lastBolus = SingleLiveEvent<String>()
    val lastBolus: LiveData<String>
        get() = _lastBolus

    private val _activeAlarms = SingleLiveEvent<String>()
    val activeAlarms: LiveData<String>
        get() = _activeAlarms

    private val _pumpType = SingleLiveEvent<String>()
    val pumpType: LiveData<String>
        get() = _pumpType

    private val _fwVersion = SingleLiveEvent<String>()
    val fwVersion: LiveData<String>
        get() = _fwVersion

    private val _patchNo = SingleLiveEvent<String>()
    val patchNo: LiveData<String>
        get() = _patchNo

    private val _patchExpiry = SingleLiveEvent<String>()
    val patchExpiry: LiveData<String>
        get() = _patchExpiry

    init {
        scope.launch {
            medtrumPump.connectionStateFlow.collect { state ->
                aapsLogger.debug(LTag.PUMP, "MedtrumViewModel connectionStateFlow: $state")
                when (state) {
                    ConnectionState.CONNECTING    -> {
                        _bleStatus.postValue("{fa-bluetooth-b spin}")
                        _canDoRefresh.postValue(false)
                    }

                    ConnectionState.CONNECTED     -> {
                        _bleStatus.postValue("{fa-bluetooth}")
                        _canDoRefresh.postValue(false)
                    }

                    ConnectionState.DISCONNECTED  -> {
                        _bleStatus.postValue("{fa-bluetooth-b}")
                        if (medtrumPump.pumpState > MedtrumPumpState.EJECTED && medtrumPump.pumpState < MedtrumPumpState.STOPPED) {
                            _canDoRefresh.postValue(true)
                        } else {
                            _canDoRefresh.postValue(false)
                        }
                    }

                    ConnectionState.DISCONNECTING -> {
                        _bleStatus.postValue("{fa-bluetooth-b spin}")
                        _canDoRefresh.postValue(false)
                    }
                }
                updateGUI()
            }
        }
        scope.launch {
            medtrumPump.pumpStateFlow.collect { state ->
                aapsLogger.debug(LTag.PUMP, "MedtrumViewModel pumpStateFlow: $state")
                _canDoResetAlarms.postValue(
                    medtrumPump.pumpState in listOf(
                        MedtrumPumpState.PAUSED, MedtrumPumpState.HOURLY_MAX_SUSPENDED, MedtrumPumpState.DAILY_MAX_SUSPENDED
                    )
                )

                updateGUI()
            }
        }
        // Periodically update gui
        scope.launch {
            while (true) {
                updateGUI()
                kotlinx.coroutines.delay(T.mins(1).msecs())
            }
        }
    }

    override fun onCleared() {
        super.onCleared()
        scope.cancel()
    }

    fun onClickRefresh() {
        commandQueue.readStatus(rh.gs(R.string.requested_by_user), null)
    }

    fun onClickResetAlarms() {
        commandQueue.clearAlarms(null)
    }

    fun onClickChangePatch() {
        aapsLogger.debug(LTag.PUMP, "ChangePatch Patch clicked!")
        val profile = profileFunction.getProfile()
        if (profile == null) {
            _eventHandler.postValue(UIEvent(EventType.PROFILE_NOT_SET))
        } else {
            _eventHandler.postValue(UIEvent(EventType.CHANGE_PATCH_CLICKED))
        }
    }

    private fun updateGUI() {
        // Update less dynamic values
        if (medtrumPump.lastConnection != 0L) {
            val agoMilliseconds = System.currentTimeMillis() - medtrumPump.lastConnection
            val agoMinutes = agoMilliseconds / 1000 / 60
            _lastConnectionMinAgo.postValue(rh.gs(info.nightscout.shared.R.string.minago, agoMinutes))
        }
        if (medtrumPump.lastBolusTime != 0L) {
            val agoMilliseconds = System.currentTimeMillis() - medtrumPump.lastBolusTime
            val agoHours = agoMilliseconds.toDouble() / 60.0 / 60.0 / 1000.0
            if (agoHours < 6)
            // max 6h back
                _lastBolus.postValue(
                    dateUtil.timeString(medtrumPump.lastBolusTime) + " " + dateUtil.sinceString(medtrumPump.lastBolusTime, rh) + " " + rh.gs(
                        info.nightscout.interfaces.R.string.format_insulin_units, medtrumPump.lastBolusAmount
                    )
                )
            else _lastBolus.postValue("")
        }

        val activeAlarmStrings = medtrumPump.activeAlarms.map { medtrumPump.alarmStateToString(it) }
        _activeAlarms.postValue(activeAlarmStrings.joinToString("\n"))
        _pumpType.postValue(medtrumPump.deviceType.toString())
        _fwVersion.postValue(medtrumPump.swVersion)
        _patchNo.postValue(medtrumPump.patchId.toString())

        if (medtrumPump.desiredPatchExpiration) {
            val expiry = medtrumPump.patchStartTime + T.hours(72).msecs()
            _patchExpiry.postValue(dateUtil.dateAndTimeString(expiry))
        } else {
            _patchExpiry.postValue(rh.gs(R.string.expiry_not_enabled))
        }
    }
}
