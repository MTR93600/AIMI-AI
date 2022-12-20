package info.nightscout.androidaps.plugins.pump.medtronic

import android.annotation.SuppressLint
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.os.Bundle
import android.os.IBinder
import android.os.SystemClock
import android.widget.Toast
import androidx.preference.Preference
import dagger.android.HasAndroidInjector
import info.nightscout.androidaps.Constants
import info.nightscout.androidaps.activities.ErrorHelperActivity
import info.nightscout.androidaps.data.DetailedBolusInfo
import info.nightscout.androidaps.data.PumpEnactResult
import info.nightscout.androidaps.events.EventRefreshOverview
import info.nightscout.androidaps.extensions.runOnUiThread
import info.nightscout.androidaps.interfaces.*
import info.nightscout.androidaps.interfaces.Profile.ProfileValue
import info.nightscout.androidaps.interfaces.PumpSync.TemporaryBasalType
import info.nightscout.androidaps.plugins.bus.RxBus
import info.nightscout.androidaps.plugins.common.ManufacturerType
import info.nightscout.androidaps.plugins.general.actions.defs.CustomAction
import info.nightscout.androidaps.plugins.general.actions.defs.CustomActionType
import info.nightscout.androidaps.plugins.general.overview.events.EventNewNotification
import info.nightscout.androidaps.plugins.general.overview.notifications.Notification
import info.nightscout.androidaps.plugins.pump.common.MedLinkPumpPluginAbstract
import info.nightscout.androidaps.plugins.pump.common.data.MedLinkPumpStatus
import info.nightscout.androidaps.plugins.pump.common.defs.PumpDeviceState
import info.nightscout.androidaps.plugins.pump.common.defs.PumpDriverState
import info.nightscout.androidaps.plugins.pump.common.defs.PumpRunningState
import info.nightscout.androidaps.plugins.pump.common.defs.PumpTempBasalType
import info.nightscout.androidaps.plugins.pump.common.defs.PumpType
import info.nightscout.androidaps.plugins.pump.common.events.EventRefreshButtonState
import info.nightscout.androidaps.plugins.pump.common.hw.medlink.MedLinkConst
import info.nightscout.androidaps.plugins.pump.common.hw.medlink.activities.BolusCallback
import info.nightscout.androidaps.plugins.pump.common.hw.medlink.activities.BolusDeliverCallback
import info.nightscout.androidaps.plugins.pump.common.hw.medlink.activities.BolusProgressCallback
import info.nightscout.androidaps.plugins.pump.common.hw.medlink.activities.MedLinkStandardReturn
import info.nightscout.androidaps.plugins.pump.common.hw.medlink.ble.CommandPriority
import info.nightscout.androidaps.plugins.pump.common.hw.medlink.ble.command.*
import info.nightscout.androidaps.plugins.pump.common.hw.medlink.ble.data.*
import info.nightscout.androidaps.plugins.pump.common.hw.medlink.defs.MedLinkCommandType
import info.nightscout.androidaps.plugins.pump.common.hw.medlink.defs.MedLinkPumpDevice
import info.nightscout.androidaps.plugins.pump.common.hw.medlink.defs.MedLinkServiceState
import info.nightscout.androidaps.plugins.pump.common.hw.medlink.service.MedLinkService
import info.nightscout.androidaps.plugins.pump.common.hw.medlink.service.MedLinkServiceData
import info.nightscout.androidaps.plugins.pump.common.hw.medlink.service.MedLinkStatusParser
import info.nightscout.androidaps.plugins.pump.common.hw.medlink.service.tasks.WakeAndTuneTask
import info.nightscout.androidaps.plugins.pump.common.hw.rileylink.defs.RileyLinkPumpInfo
import info.nightscout.androidaps.plugins.pump.common.hw.rileylink.service.tasks.ResetRileyLinkConfigurationTask
import info.nightscout.androidaps.plugins.pump.common.hw.rileylink.service.tasks.ServiceTaskExecutor
import info.nightscout.androidaps.plugins.pump.common.sync.PumpDbEntryTBR
import info.nightscout.androidaps.plugins.pump.common.sync.PumpSyncStorage
import info.nightscout.androidaps.plugins.pump.common.utils.DateTimeUtil
import info.nightscout.androidaps.plugins.pump.medtronic.R.string
import info.nightscout.androidaps.plugins.pump.medtronic.comm.PumpResponses
import info.nightscout.androidaps.plugins.pump.medtronic.comm.activities.*
import info.nightscout.androidaps.plugins.pump.medtronic.comm.history.pump.PumpHistoryEntry
import info.nightscout.androidaps.plugins.pump.medtronic.data.EnliteInterval
import info.nightscout.androidaps.plugins.pump.medtronic.data.MedLinkMedtronicHistoryData
import info.nightscout.androidaps.plugins.pump.medtronic.data.NextStartStop
import info.nightscout.androidaps.plugins.pump.medtronic.data.dto.*
import info.nightscout.androidaps.plugins.pump.medtronic.defs.*
import info.nightscout.androidaps.plugins.pump.medtronic.driver.MedLinkMedtronicPumpStatus
import info.nightscout.androidaps.plugins.pump.medtronic.events.EventMedtronicPumpValuesChanged
import info.nightscout.androidaps.plugins.pump.medtronic.service.MedLinkMedtronicService
import info.nightscout.androidaps.plugins.pump.medtronic.util.MedLinkMedtronicConst
import info.nightscout.androidaps.plugins.pump.medtronic.util.MedLinkMedtronicUtil
import info.nightscout.androidaps.plugins.pump.medtronic.util.MedtronicConst
import info.nightscout.androidaps.plugins.pump.medtronic.util.MedtronicPumpPluginInterface
import info.nightscout.androidaps.queue.Callback
import info.nightscout.androidaps.queue.commands.CustomCommand
import info.nightscout.androidaps.receivers.ReceiverStatusStore
import info.nightscout.androidaps.utils.DateUtil
import info.nightscout.androidaps.utils.FabricPrivacy
import info.nightscout.androidaps.utils.T
import info.nightscout.androidaps.utils.TimeChangeType
import info.nightscout.androidaps.utils.rx.AapsSchedulers
import info.nightscout.shared.logging.AAPSLogger
import info.nightscout.shared.logging.LTag
import info.nightscout.shared.sharedPreferences.SP
import org.joda.time.DateTime
import org.joda.time.DateTimeZone
import org.joda.time.Instant
import org.joda.time.LocalDateTime
import org.joda.time.Seconds
import org.json.JSONException
import org.json.JSONObject
import java.lang.Math.round
import java.math.BigDecimal
import java.math.RoundingMode
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter
import java.time.temporal.ChronoUnit
import java.util.*
import java.util.concurrent.LinkedBlockingDeque
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicReference
import java.util.function.Function
import java.util.function.Supplier
import java.util.stream.Collectors
import java.util.stream.Stream
import java.util.stream.Stream.of
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.math.roundToInt

/**
 * Created by dirceu on 10.07.2020.
 */
@Singleton
open class MedLinkMedtronicPumpPlugin @Inject constructor(
    injector: HasAndroidInjector,
    aapsLogger: AAPSLogger,
    rxBus: RxBus?,
    context: Context?,
    resourceHelper: ResourceHelper?,
    activePlugin: ActivePlugin?,
    sp: SP,
    commandQueue: CommandQueue?,
    fabricPrivacy: FabricPrivacy?,
    private val medtronicUtil: MedLinkMedtronicUtil,
    private val medLinkPumpStatus: MedLinkMedtronicPumpStatus,
    private val medtronicHistoryData: MedLinkMedtronicHistoryData,
    private val medLinkServiceData: MedLinkServiceData,
    private val serviceTaskExecutor: ServiceTaskExecutor,
    private val receiverStatusStore: ReceiverStatusStore,
    dateUtil: DateUtil?,
    aapsSchedulers: AapsSchedulers?,
    pumpSync: PumpSync?,
    pumpSyncStorage: PumpSyncStorage?,
    private val bgSync: BgSync,
) : MedLinkPumpPluginAbstract(
    PluginDescription(

    ) //
        .mainType(PluginType.PUMP) //
        .fragmentClass(MedLinkMedtronicFragment::class.java.name) //
        .pluginIcon(R.drawable.ic_veo_medlink)
        .pluginName(string.medtronic_name)
        .shortName(string.medlink_medtronic_name_short) //
        .preferencesId(R.xml.pref_medtronic_medlink)
        .description(string.description_pump_medtronic_medlink),  //
    PumpType.MEDLINK_MEDTRONIC_554_754_VEO,  // we default to most basic model, correct model from config is loaded later
    injector, resourceHelper!!, aapsLogger, commandQueue!!, rxBus!!, activePlugin!!, sp,
    context!!, fabricPrivacy!!, dateUtil!!,
    aapsSchedulers!!, pumpSync!!, pumpSyncStorage!!
), Pump, MicrobolusPumpInterface, MedLinkPumpDevice, MedtronicPumpPluginInterface {

    private lateinit var remainingBolus: DetailedBolusInfo
    private var bolusHistoryCheck: Boolean = false
    private var hasFirstAlert: Boolean = false
    private var hasSecondAlert: Boolean = false
    var lastCalibration: ZonedDateTime? = null
    private val minimumBatteryLevel = 5

    // private override var dateUtil: DateUtil? = null
    override val reservoirLevel: Double
        get() = medLinkPumpStatus.reservoirRemainingUnits

    override val batteryLevel: Int
        get() = medLinkPumpStatus.batteryRemaining

    override val lastConnectionTimeMillis: Long
        get() = medLinkPumpStatus.lastConnection

    private var batteryLastRead = 0L
    private var lastBatteryLevel = 0
    private var batteryDelta = 0.0
    var medLinkService: MedLinkMedtronicService? = null
    private var missedBGs = 5
    private var firstMissedBGTimestamp = 0L

    // variables for handling statuses and history
    private var firstRun = true
    private var isRefresh = false
    private val statusRefreshMap: MutableMap<MedLinkMedtronicStatusRefreshType?, Long?> = HashMap()
    private var isInitialized = false
    private val lastPumpHistoryEntry: PumpHistoryEntry? = null
    private val busyTimestamps: MutableList<Long> = ArrayList()
    private var hasTimeDateOrTimeZoneChanged = false
    var tempBasalMicrobolusOperations: TempBasalMicrobolusOperations = TempBasalMicrobolusOperations()
        private set
    private var percent: Int? = null
    private var durationInMinutes: Int? = null
    private val profile: Profile? = null
    private var result: PumpEnactResult? = null
    private var bolusDeliveryType = MedtronicPumpPluginInterface.BolusDeliveryType.Idle
    private var customActions: List<CustomAction>? = null
    private var lastStatus = PumpRunningState.Running.status
    protected var lastBGHistoryRead = 0L
    var basalProfile: BasalProfile? = null
    private var lastPreviousHistory = 0L
    private var lastTryToConnect = 0L
    private var lastBgs: Array<Int?>? = null
    private var bgIndex = 0
    private var lastBolusHistoryRead = 0L
    private var lastDeliveredBolus = 0.0
    private val bolusDeliveryTime = 0L
    private var pumpTimeDelta = 0L
    private var lastDetailedBolusInfo: DetailedBolusInfo? = null
    private var late1Min = false
    private var lastProfileRead: Long = 0
    override fun applyBasalConstraints(absoluteRate: Constraint<Double>, profile: Profile): Constraint<Double> {
        return absoluteRate // TODO("Evaluate")
    }

    override fun applyBasalPercentConstraints(percentRate: Constraint<Int>, profile: Profile): Constraint<Int> {
        return super.applyBasalPercentConstraints(percentRate, profile)
    }

    fun stopPump(callback: Callback) {
        aapsLogger.info(LTag.PUMP, "MedtronicPumpPlugin::stopPump - ")
        aapsLogger.info(LTag.PUMP, "batteryDelta $batteryDelta")
        val currentLevel = receiverStatusStore.batteryLevel
        if (currentLevel - batteryDelta * 5 <= minimumBatteryLevel || currentLevel <= minimumBatteryLevel || pumpStatusData.deviceBatteryRemaining != 0 &&
            pumpStatusData.deviceBatteryRemaining <= minimumBatteryLevel
        ) {
            val i = Intent(context, ErrorHelperActivity::class.java)
            i.putExtra("soundid", R.raw.boluserror)
            i.putExtra(
                "status",
                rh.gs(string.medlink_medtronic_cmd_stop_could_not_be_delivered)
            )
            i.putExtra("title", rh.gs(string.medtronic_errors))
            i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            context.startActivity(i)
            return
        }
        if (PumpRunningState.Suspended != medLinkPumpStatus.pumpRunningState) {
            val function = ChangeStatusCallback(
                aapsLogger,
                ChangeStatusCallback.OperationType.STOP, this
            )
            val startStopFunction = function.andThen { f: MedLinkStandardReturn<PumpDriverState> ->
                result = when {
                    f.functionResult === PumpDriverState.Suspended   -> {
                        PumpEnactResult(injector).success(true).enacted(true)
                    }

                    f.functionResult === PumpDriverState.Initialized -> {
                        PumpEnactResult(injector).success(false).enacted(true)
                    }

                    else                                             -> {
                        PumpEnactResult(injector).success(false).enacted(false)
                    }
                }
                sendPumpUpdateEvent()
                callback.result(result!!).run()
                f
            }
            val message = MedLinkPumpMessage<PumpDriverState, Any>(
                MedLinkCommandType.StopStartPump,
                MedLinkCommandType.StopPump,
                startStopFunction,
                btSleepTime, BleStopCommand(
                    aapsLogger,
                    medLinkService!!.medLinkServiceData,
                    this
                ), CommandPriority.NORMAL
            )
            medLinkService!!.medtronicUIComm?.executeCommandCP(message)
        }
    }

    val btSleepTime: Long
        get() {
            val sleepTime = sp.getInt(rh.gs(string.medlink_key_interval_between_bt_connections), 5)
            return sleepTime * 1000L
        }

    override fun storeCancelTempBasal() {
        aapsLogger.info(LTag.DATABASE, "stopping basal")
        val state = pumpSync.expectedPumpState()
        val timestamp = state?.temporaryBasal?.timestamp ?: dateUtil.now()
        pumpSync.syncStopTemporaryBasalWithPumpId(
            timestamp = timestamp,
            endPumpId = dateUtil.now(),
            pumpType = pumpType ?: PumpType.GENERIC_AAPS,
            pumpSerial = serialNumber()
        )
        pumpStatusData.runningTBR = null
        pumpStatusData.tempBasalAmount = profile?.getBasal()
            ?: basalProfile?.getEntryForTime(Instant.now())?.rate

    }

    fun startPump(callback: Callback?) {
        aapsLogger.info(LTag.PUMP, "MedtronicPumpPlugin::startPump - ")
        if (medLinkPumpStatus.pumpRunningState !== PumpRunningState.Running) {
            // buildStartStopCommands()
            val activity: Function<Supplier<Stream<String>>, MedLinkStandardReturn<PumpDriverState>> =
            //     if(tempBasalMicrobolusOperations != null && tempBasalMicrobolusOperations.operations.isNotEmpty() && callback != null){
            //     buildChangeStatusFunction(
            //     tempBasalMicrobolusOperations.operations.first,
            //     callback, this
                // ).first()} else {
                ChangeStatusCallback(aapsLogger, ChangeStatusCallback.OperationType.START, this)
                    // }
                    .andThen { f: MedLinkStandardReturn<PumpDriverState> ->
                        result = when {
                            f.functionResult === PumpDriverState.Initialized -> {
                                PumpEnactResult(injector).success(true).enacted(true)
                            }

                            f.functionResult === PumpDriverState.Suspended   -> {
                                PumpEnactResult(injector).success(false).enacted(true)
                            }

                            else                                             -> {
                                PumpEnactResult(injector).success(false).enacted(false)
                            }
                        }
                        sendPumpUpdateEvent()
                        callback?.result(result!!)?.run()
                        f
                    }

            val message = MedLinkPumpMessage<PumpDriverState, Any>(
                MedLinkCommandType.StopStartPump,
                MedLinkCommandType.StartPump,
                activity,
                btSleepTime,
                BleStartCommand(
                    aapsLogger, medLinkService!!.medLinkServiceData,
                    this
                ),
                CommandPriority.NORMAL
            )
            medLinkService!!.medtronicUIComm?.executeCommandCP(message)
        }
    }

    fun alreadyRun() {
        firstRun = false
    }

    enum class StatusRefreshAction {
        Add,  //
        GetData
    }

    override fun isFakingTempsByMicroBolus(): Boolean {
        return tempBasalMicrobolusOperations.remainingOperations > 0
    }

    override val isFakingTempsByExtendedBoluses: Boolean
        get() = false

    override fun updatePreferenceSummary(pref: Preference) {
        super.updatePreferenceSummary(pref)
        if (pref.key == rh.gs(string.key_medlink_mac_address)) {
            val value = sp.getStringOrNull(string.key_medlink_mac_address, null)
            pref.summary = value ?: rh.gs(string.not_set_short)
        }
    }

    override fun initPumpStatusData() {
        medLinkPumpStatus.lastConnection = sp.getLong(
            MedLinkConst.Prefs.LastGoodDeviceCommunicationTime,
            0L
        )
        medLinkPumpStatus.lastDataTime = medLinkPumpStatus.lastConnection
        medLinkPumpStatus.previousConnection = medLinkPumpStatus.lastConnection

        //if (rileyLinkMedtronicService != null) rileyLinkMedtronicService.verifyConfiguration();
        aapsLogger.debug(LTag.PUMP, "initPumpStatusData: " + medLinkPumpStatus)

        // this is only thing that can change, by being configured
        pumpDescription.maxTempAbsolute = if (medLinkPumpStatus.maxBasal != null) medLinkPumpStatus.maxBasal else 35.0
        pumpDescription.tempBasalStyle = PumpDescription.PERCENT
        pumpDescription.tempPercentStep = 1

        // set first Medtronic Pump Start
        if (!sp.contains(MedtronicConst.Statistics.FirstPumpStart)) {
            sp.putLong(MedtronicConst.Statistics.FirstPumpStart, System.currentTimeMillis())
        }
        migrateSettings()
    }

    private fun migrateSettings() {
        if ("US (916 MHz)" == sp.getString(MedLinkMedtronicConst.Prefs.PumpFrequency, "US (916 MHz)")) {
            sp.putString(MedLinkMedtronicConst.Prefs.PumpFrequency, rh.gs(string.key_medtronic_pump_frequency_us_ca))
        }
        val initC: Any = rh.gs(string.key_medlink_init_command)
        //        if(initC instanceof HashSet) {

        //        }else{
        //            this.initCommands= new HashSet<>();
        //        }

        //        String encoding = sp.getString(MedtronicConst.Prefs.Encoding, "RileyLink 4b6b Encoding");
        //
        //        if ("RileyLink 4b6b Encoding".equals(encoding)) {
        //            sp.putString(MedtronicConst.Prefs.Encoding, getRh().gs(R.string.key_medtronic_pump_encoding_4b6b_rileylink));
        //        }
        //
        //        if ("Local 4b6b Encoding".equals(encoding)) {
        //            sp.putString(MedtronicConst.Prefs.Encoding, getRh().gs(R.string.key_medtronic_pump_encoding_4b6b_local));
        //        }
    }

    override fun onStartScheduledPumpActions() {
        // check status every minute (if any status needs refresh we send readStatus command)
        Thread {
            do {
                SystemClock.sleep(5000)
                if (this.isInitialized) {
                    val statusRefresh = workWithStatusRefresh(
                        StatusRefreshAction.GetData, null, 0L
                    )
                    if (doWeHaveAnyStatusNeededRefreshing(statusRefresh)) {
                        if (!commandQueue.statusInQueue()) {
                            commandQueue.readStatus("Scheduled Status Refresh", null)
                        }
                    }
                    if (System.currentTimeMillis() - pumpStatusData.lastConnection > 590000) {
                        readPumpData(false)
                    }
                    clearBusyQueue()
                }
            } while (serviceRunning)
        }.start()
    }

    @Synchronized
    private fun clearBusyQueue() {
        if (busyTimestamps.size == 0) {
            return
        }
        val deleteFromQueue: MutableSet<Long> = HashSet()
        for (busyTimestamp in busyTimestamps) {
            if (System.currentTimeMillis() > busyTimestamp) {
                deleteFromQueue.add(busyTimestamp)
            }
        }
        if (deleteFromQueue.size == busyTimestamps.size) {
            busyTimestamps.clear()
            setEnableCustomAction(MedtronicCustomActionType.ClearBolusBlock, false)
        }
        if (deleteFromQueue.size > 0) {
            busyTimestamps.removeAll(deleteFromQueue)
        }
    }

    override val serviceClass: Class<*>
        get() = MedLinkMedtronicService::class.java
    override val pumpStatusData: MedLinkPumpStatus
        get() = medLinkPumpStatus
    private val isServiceSet: Boolean
        private get() = medLinkService != null

    override fun isInitialized(): Boolean {
        if (displayConnectionMessages) aapsLogger.debug(LTag.PUMP, "MedtronicPumpPlugin::isInitialized")
        return isServiceSet && isInitialized
    }

    //TODO implement
    override fun isSuspended(): Boolean {
        return pumpStatusData.pumpRunningState === PumpRunningState.Suspended
    }

    override fun isBusy(): Boolean {
        return isBusy
    }

    override fun setBusy(busy: Boolean) {}
    override fun triggerPumpConfigurationChangedEvent() {}
    override fun getRileyLinkService(): MedLinkService {
        return medLinkService!!
    }

    override fun getService(): MedLinkService {
        return medLinkService!!
    }

    //    @Override public MedLinkService getMedLinkService() {
    //        return medLinkService;
    //    }
    override fun isConnected(): Boolean {
        if (displayConnectionMessages) aapsLogger.debug(LTag.PUMP, "MedLinkMedtronicPumpPlugin::isConnected")
        return isServiceSet && medLinkService!!.isInitialized
    }

    override fun isConnecting(): Boolean {
        if (displayConnectionMessages) aapsLogger.debug(LTag.PUMP, "MedtronicPumpPlugin::isConnecting")
        return !isServiceSet || !medLinkService!!.isInitialized
    }

    override fun finishHandshaking() {}
    override fun connect(reason: String) {
        //        medLinkService.getMedLinkRFSpy().e()
    }

    //
    //    @Override
    //    public void disconnect(String reason) {
    //    }
    //
    //    @Override
    //    public void stopConnecting() {
    //    }

    private fun doWeHaveAnyStatusNeededRefreshing(statusRefresh: Map<MedLinkMedtronicStatusRefreshType?, Long?>?): Boolean {
        for ((key, value) in statusRefresh!!) {
            //            aapsLogger.info(
            //                    LTag.PUMP, "Next Command " + round(
            //                    (value!! - System.currentTimeMillis()).toFloat()
            //            )
            //            )
            //            aapsLogger.info(LTag.PUMP, key!!.name)
            if (value!! -
                System.currentTimeMillis() <= 0
            ) {
                return true
            }
        }
        for (oper in tempBasalMicrobolusOperations.operations) {
            //            aapsLogger.info(
            //                    LTag.PUMP, "Next Command " +
            //                    oper.releaseTime.toDateTime()
            //            )
            //            aapsLogger.info(LTag.PUMP, oper.toString())
            if (oper.releaseTime.isAfter(LocalDateTime.now().minus(Seconds.seconds(30)))) {
                return true
            }
        }
        return hasTimeDateOrTimeZoneChanged
    }

    private val lastPumpEntryTime: Long
        private get() {
            val lastPumpEntryTime = sp.getLong(MedtronicConst.Statistics.LastPumpHistoryEntry, 0L)
            return try {
                val localDateTime = DateTimeUtil.toLocalDateTime(lastPumpEntryTime)
                if (localDateTime.year != GregorianCalendar()[Calendar.YEAR]) {
                    aapsLogger.warn(LTag.PUMP, "Saved LastPumpHistoryEntry was invalid. Year was not the same.")
                    return 0L
                }
                lastPumpEntryTime
            } catch (ex: Exception) {
                aapsLogger.warn(LTag.PUMP, "Saved LastPumpHistoryEntry was invalid.")
                0L
            }
        }

    private fun readPumpHistoryLogic() {

        val debugHistory = false
        var targetDate: LocalDateTime? = null

        if (lastPumpHistoryEntry == null) {
            if (debugHistory) aapsLogger.debug(LTag.PUMP, logPrefix + "readPumpHistoryLogic(): lastPumpHistoryEntry: null")
            val lastPumpHistoryEntryTime = lastPumpEntryTime
            var timeMinus36h = LocalDateTime()
            timeMinus36h = timeMinus36h.minusHours(36)
            medtronicHistoryData.setIsInInit(true)
            if (lastPumpHistoryEntryTime == 0L) {
                if (debugHistory) aapsLogger.debug(
                    LTag.PUMP, logPrefix + "readPumpHistoryLogic(): lastPumpHistoryEntryTime: 0L - targetDate: "
                        + targetDate
                )
                targetDate = timeMinus36h
            } else {
                // LocalDateTime lastHistoryRecordTime = DateTimeUtil.toLocalDateTime(lastPumpHistoryEntryTime);
                if (debugHistory) aapsLogger.debug(LTag.PUMP, logPrefix + "readPumpHistoryLogic(): lastPumpHistoryEntryTime: " + lastPumpHistoryEntryTime + " - targetDate: " + targetDate)
                medtronicHistoryData.setLastHistoryRecordTime(lastPumpHistoryEntryTime)
                var lastHistoryRecordTime = DateTimeUtil.toLocalDateTime(lastPumpHistoryEntryTime)
                lastHistoryRecordTime = lastHistoryRecordTime.minusHours(12) // we get last 12 hours of history to
                // determine pump state
                // (we don't process that data), we process only
                if (timeMinus36h.isAfter(lastHistoryRecordTime)) {
                    targetDate = timeMinus36h
                }
                targetDate = if (timeMinus36h.isAfter(lastHistoryRecordTime)) timeMinus36h else lastHistoryRecordTime
                if (debugHistory) aapsLogger.debug(LTag.PUMP, logPrefix + "readPumpHistoryLogic(): targetDate: " + targetDate)
            }
        } else {
            if (debugHistory) aapsLogger.debug(LTag.PUMP, logPrefix + "readPumpHistoryLogic(): lastPumpHistoryEntry: not null - " + medtronicUtil.gsonInstance.toJson(lastPumpHistoryEntry))
            medtronicHistoryData.setIsInInit(false)
            // medtronicHistoryData.setLastHistoryRecordTime(lastPumpHistoryEntry.atechDateTime);

            // targetDate = lastPumpHistoryEntry.atechDateTime;
        }

        //getAapsLogger().debug(LTag.PUMP, "HST: Target Date: " + targetDate);
        val msg = MedLinkPumpMessage<MedLinkPumpStatus, Any>(
            MedLinkCommandType.GetState,
            StatusCallback(
                aapsLogger, this,
                medLinkPumpStatus
            ),
            btSleepTime,
            BlePartialCommand(aapsLogger, medLinkService!!.medLinkServiceData),
            CommandPriority.NORMAL
        )
        medLinkService!!.medtronicUIComm?.executeCommandCP(msg)
        if (debugHistory) aapsLogger.debug(LTag.PUMP, "HST: After task")

        //        PumpHistoryResult historyResult = (PumpHistoryResult) responseTask2.returnData;

        //        if (debugHistory)
        //            getAapsLogger().debug(LTag.PUMP, "HST: History Result: " + historyResult.toString());

        //        PumpHistoryEntry latestEntry = historyResult.getLatestEntry();

        //        if (debugHistory)
        //            getAapsLogger().debug(LTag.PUMP, getLogPrefix() + "Last entry: " + latestEntry);

        //        if (latestEntry == null) // no new history to read
        //            return;

        //        this.lastPumpHistoryEntry = latestEntry;
        //        sp.putLong(MedtronicConst.Statistics.LastPumpHistoryEntry, latestEntry.atechDateTime);

        //        if (debugHistory)
        //            getAapsLogger().debug(LTag.PUMP, "HST: History: valid=" + historyResult.validEntries.size() + ", unprocessed=" + historyResult.unprocessedEntries.size());
        //
        //        this.medtronicHistoryData.addNewHistory(historyResult);
        medtronicHistoryData.filterNewEntries()

        // determine if first run, if yes detrmine how much of update do we need
        // first run:
        // get last hiostory entry, if not there download 1.5 days of data
        // - there: check if last entry is older than 1.5 days
        // - yes: download 1.5 days
        // - no: download with last entry
        // - not there: download 1.5 days
        //
        // upload all new entries to NightScout (TBR, Bolus)
        // determine pump status
        //
        // save last entry
        //
        // not first run:
        // update to last entry
        // - save
        // - determine pump status
    }

    private fun readPumpBGHistory(force: Boolean) {
        if ((activePlugin.activeBgSource as PluginBase).javaClass.canonicalName?.contains("MedLinkPlugin") == false) {
            return
        }
        //TODO fazer ele não ficar lendo o histórico quando o sensor estiver em warmup
        if (firstMissedBGTimestamp > 0L && System.currentTimeMillis() - lastBGHistoryRead > 610000 &&
            System.currentTimeMillis() - firstMissedBGTimestamp > 1800000 || missedBGs > 4 || force
        ) {
            lastBGHistoryRead = System.currentTimeMillis()
            val func = BGHistoryCallback(this, aapsLogger, false, false)
            val isigFunc = IsigHistoryCallback(injector, this, aapsLogger, false, func)
            val msg = MedLinkPumpMessage<BgSync.BgHistory, BgSync.BgHistory>(
                MedLinkCommandType.BGHistory,
                MedLinkCommandType.IsigHistory,  //                        MedLinkCommandType.NoCommand,
                func,
                isigFunc,  //                        null,
                btSleepTime,
                BlePartialCommand(aapsLogger, medLinkService!!.medLinkServiceData)
            )
            medLinkService!!.medtronicUIComm?.executeCommandCP(msg)
        }
    }

    private fun readPumpData(scheduled: Boolean, interval: Long = 300000L) {
        //        if (isLoggingEnabled())
        //            LOG.error(getLogPrefix() + "readPumpHistory WIP.");
        scheduleNextReadState(scheduled, interval)
        aapsLogger.info(LTag.CONFIGBUILDER, "read pump history")
        readPumpHistoryLogic()

        //        if (bgDelta == 1 || bgDelta > 5) {
        //            scheduleNextRefresh(MedLinkMedtronicStatusRefreshType.PumpHistory);
        //        } else if (bgDelta == 0) {
        //            scheduleNextRefresh(MedLinkMedtronicStatusRefreshType.PumpHistory, 1);
        //        } else {
        //            scheduleNextRefresh(MedLinkMedtronicStatusRefreshType.PumpHistory, -1 * bgDelta);
        //        }
        if (medtronicHistoryData.hasRelevantConfigurationChanged()) {
            scheduleNextRefresh(MedLinkMedtronicStatusRefreshType.Configuration, -1)
        }
        if (medtronicHistoryData.hasPumpTimeChanged()) {
            scheduleNextRefresh(MedLinkMedtronicStatusRefreshType.PumpTime, -1)
        }
        if (medLinkPumpStatus.basalProfileStatus !== BasalProfileStatus.NotInitialized
            && medtronicHistoryData.hasBasalProfileChanged()
        ) {
            medtronicHistoryData.processLastBasalProfileChange(
                pumpDescription.pumpType,
                medLinkPumpStatus
            )
        }
        val previousState = pumpState
        if (medtronicHistoryData.isPumpSuspended()) {
            pumpState = PumpDriverState.Suspended
            aapsLogger.debug(LTag.PUMP, logPrefix + "isPumpSuspended: true")
        } else {
            if (previousState === PumpDriverState.Suspended) {
                pumpState = PumpDriverState.Ready
            }
            aapsLogger.debug(LTag.PUMP, logPrefix + "isPumpSuspended: false")
        }
        medtronicHistoryData.processNewHistoryData()
        medtronicHistoryData.finalizeNewHistoryRecords()
        // this.medtronicHistoryData.setLastHistoryRecordTime(this.lastPumpHistoryEntry.atechDateTime);
    }

    private fun scheduleNextReadState(scheduled: Boolean, interval: Long = 300000L): Int {
        if (pumpStatusData.getLastBGTimestamp() != 0L) {
            var bgDelta = Math.toIntExact(
                (pumpStatusData.lastDataTime -
                    pumpStatusData.getLastBGTimestamp()) / 60000
            )
            if (isInitialized && scheduled) {
                if (bgDelta in 7..9) {
                    aapsLogger.info(LTag.PUMPBTCOMM, "reading failed increasing")
                    EnliteInterval.currentFailed()
                } else if (bgDelta < 4) {
                    EnliteInterval.currentSuccess(pumpStatusData.lastConnection)
                }
            }
            pumpTimeDelta = pumpStatusData.lastConnection -
                pumpStatusData.lastDataTime
            var minutesDelta = Math.toIntExact(TimeUnit.MILLISECONDS.toMinutes(pumpTimeDelta))
            //            if (calculateBGDeltaAvg(bgDelta) > 6.0) {
            //                minutesDelta++
            //            }
            if (aapsLogger != null) {
                aapsLogger.info(LTag.CONFIGBUILDER, "Last Connection" + pumpStatusData.lastConnection)
                aapsLogger.info(LTag.CONFIGBUILDER, "Next Delta $minutesDelta")
                aapsLogger.info(LTag.CONFIGBUILDER, "bgdelta  $bgDelta")
                aapsLogger.info(
                    LTag.CONFIGBUILDER,
                    "Last connection " + dateUtil.toISONoZone(pumpStatusData.lastConnection)
                )
                aapsLogger.info(LTag.CONFIGBUILDER, "Last bg " + dateUtil.toISONoZone(pumpStatusData.getLastBGTimestamp()))
                aapsLogger.info(LTag.CONFIGBUILDER, "Last bg " + EnliteInterval.toString())

            }
            //            while (bgDelta >= 5) {
            //                bgDelta -= 5
            //            }
            var lostContactDelta = 0
            if (pumpStatusData.lastConnection - pumpStatusData.getLastBGTimestamp() > 360000 || late1Min) {
                lostContactDelta = 2
            }
            if (hasStarted) {
                scheduleNextRefresh(MedLinkMedtronicStatusRefreshType.PumpHistory, pumpTimeDelta + EnliteInterval.nextInterval())
            } else {
                scheduleNextRefresh(
                    MedLinkMedtronicStatusRefreshType.PumpHistory, pumpTimeDelta + (System.currentTimeMillis() -
                        pumpStatusData.lastConnection) + 300000
                )
                if (pumpStatusData.lastConnection > 0L) {
                    hasStarted = true
                }
            }
        } else {
            aapsLogger.info(LTag.CONFIGBUILDER, "scheduling")
            scheduleNextRefresh(
                MedLinkMedtronicStatusRefreshType.PumpHistory,
                300000
            )
        }
        return 0
    }

    private fun calculateBGDeltaAvg(bgDelta: Int): Double {
        if (lastBgs == null) {
            lastBgs = arrayOfNulls(5)
            Arrays.fill(lastBgs, 0)
        }
        bgIndex++
        if (bgIndex == 5) {
            bgIndex = 0
        }
        lastBgs!![bgIndex] = bgDelta
        var sum = 0.0
        for (value in lastBgs!!) {
            sum += value!!.toDouble()
        }
        return sum / lastBgs!!.size
    }

    protected open fun refreshAnyStatusThatNeedsToBeRefreshed() {
        val statusRefresh = workWithStatusRefresh(
            StatusRefreshAction.GetData, null,
            0L
        )
        if (!doWeHaveAnyStatusNeededRefreshing(statusRefresh)) {
            return
        }
        var resetTime = false
        if (isPumpNotReachable) {
            aapsLogger.error("Pump unreachable.")
            medtronicUtil.sendNotification(MedtronicNotificationType.PumpUnreachable, rh, rxBus)
            return
        }
        medtronicUtil.dismissNotification(MedtronicNotificationType.PumpUnreachable, rxBus)
        if (hasTimeDateOrTimeZoneChanged) {
            checkTimeAndOptionallySetTime()
            // read time if changed, set new time
            hasTimeDateOrTimeZoneChanged = false
        }

        // execute
        val refreshTypesNeededToReschedule: MutableSet<MedLinkMedtronicStatusRefreshType?> = HashSet()
        if (!tempBasalMicrobolusOperations.operations.isEmpty() &&
            tempBasalMicrobolusOperations.operations.first.releaseTime.isBefore(LocalDateTime.now().plus(Seconds.seconds(30))) &&
            !tempBasalMicrobolusOperations.operations.first.isCommandIssued
        ) {
            val oper = tempBasalMicrobolusOperations.operations.peek()
            aapsLogger.info(
                LTag.PUMP, "Next Command " +
                    oper.releaseTime.toDateTime()
            )
            aapsLogger.info(LTag.PUMP, oper.toString())
            if (!oper?.isCommandIssued!!) {

                oper.isCommandIssued = true
                when (oper.operationType!!) {
                    TempBasalMicroBolusPair.OperationType.SUSPEND    -> {
                        tempBasalMicrobolusOperations.setShouldBeSuspended(true)
                        if (PumpRunningState.Suspended != pumpStatusData.pumpRunningState) {
                            stopPump(object : Callback() {
                                override fun run() {
                                    if (medLinkPumpStatus.pumpRunningState ===
                                        PumpRunningState.Suspended
                                    ) {
                                        tempBasalMicrobolusOperations.operations.poll()
                                        oper.isCommandIssued = true
                                    }
                                }
                            })
                        } else {
                            tempBasalMicrobolusOperations.operations.poll()
                        }
                    }

                    TempBasalMicroBolusPair.OperationType.REACTIVATE -> {
                        tempBasalMicrobolusOperations.setShouldBeSuspended(false)
                        if (PumpRunningState.Running != pumpStatusData.pumpRunningState) {
                            startPump(object : Callback() {
                                override fun run() {
                                    if (medLinkPumpStatus.pumpRunningState ===
                                        PumpRunningState.Running
                                    ) {
                                        tempBasalMicrobolusOperations.operations.poll()
                                        oper.isCommandIssued = true
                                    }
                                }
                            })
                        } else {
                            tempBasalMicrobolusOperations.operations.poll()
                        }

                    }

                    TempBasalMicroBolusPair.OperationType.BOLUS      -> {
                        if (PumpRunningState.Suspended == pumpStatusData.pumpRunningState) {
                            reactivatePump(oper) { o: Any? -> o }
                        }
                        val detailedBolusInfo = DetailedBolusInfo()
                        detailedBolusInfo.lastKnownBolusTime =
                            if (pumpStatusData.lastBolusTime != null) pumpStatusData.lastBolusTime!!.time else if (pumpSyncStorage.pumpSyncStorageBolus.isNotEmpty()) {
                                val bolusList = pumpSyncStorage.pumpSyncStorageBolus
                                bolusList.sortBy { it.date }
                                bolusList[0].date
                            } else {
                                System.currentTimeMillis()
                            }
                        detailedBolusInfo.eventType = DetailedBolusInfo.EventType.CORRECTION_BOLUS
                        detailedBolusInfo.insulin = oper.dose
                        detailedBolusInfo.bolusType = DetailedBolusInfo.BolusType.TBR
                        detailedBolusInfo.bolusTimestamp = System.currentTimeMillis()
                        aapsLogger.debug(LTag.APS, "applyAPSRequest: bolus()")
                        val callback = { o: PumpEnactResult? ->
                            aapsLogger.info(LTag.PUMPBTCOMM, "temp basal bolus $o")
                            if (o?.success == true) {
                                tempBasalMicrobolusOperations.operations.poll()
                            } else {
                                oper.isCommandIssued = false
                            }
                            Unit
                        }
                        deliverTreatment(detailedBolusInfo, callback)
                    }
                }
            }
        }

        for ((key, value) in statusRefresh!!) {
            aapsLogger.info(
                LTag.PUMP, "Next Command  ${
                    (value!! - System.currentTimeMillis())
                }"
            )
            aapsLogger.info(LTag.PUMP, key!!.name)
            if (value -
                System.currentTimeMillis() <= 0
            ) {
                //                scheduleNextRefresh(refreshType.getKey());
                when (key) {
                    MedLinkMedtronicStatusRefreshType.PumpHistory -> {
                        aapsLogger.info(LTag.PUMPBTCOMM, "refreshing")
                        readPumpData(true)
                    }

                    MedLinkMedtronicStatusRefreshType.PumpTime    -> {
                        checkTimeAndOptionallySetTime()
                        refreshTypesNeededToReschedule.add(key)
                        resetTime = true
                    }

                    else                                          -> {

                    }
                }
            }
        }
        if (statusRefreshMap.isEmpty() || System.currentTimeMillis() - lastTryToConnect >= 600000) {
            val additionalMillis = if (statusRefreshMap.isEmpty()) {
                300000L
            } else 0L
            lastTryToConnect = System.currentTimeMillis()
            readPumpData(false)
            // scheduleNextRefresh()
            scheduleNextRefresh(MedLinkMedtronicStatusRefreshType.PumpHistory, additionalMillis);
            aapsLogger.info(LTag.PUMPBTCOMM, "posthistory")

        }
        if (resetTime) medLinkPumpStatus.setLastCommunicationToNow()
    }

    private fun getStartStopCallback(
        operation: TempBasalMicroBolusPair?, callback: Function1<PumpEnactResult, *>,
        function: ChangeStatusCallback, isConnect: Boolean,
    ): Function<Supplier<Stream<String>>, MedLinkStandardReturn<PumpDriverState>> {
        return function.andThen(Function { f: MedLinkStandardReturn<PumpDriverState> ->
            when (f.functionResult) {
                PumpDriverState.Busy      -> {
                    aapsLogger.info(LTag.PUMPBTCOMM, "busy $operation")
                    callback.invoke(
                        PumpEnactResult(injector) //
                            .success(false) //
                            .enacted(false) //
                            .comment(rh.gs(string.tempbasaldeliveryerror))
                    )
                }

                PumpDriverState.Connected -> {
                    aapsLogger.info(LTag.PUMPBTCOMM, "connected $operation")
                    callback.invoke(
                        PumpEnactResult(injector) //
                            .success(isConnect) //
                            .enacted(isConnect) //
                            .comment(rh.gs(string.careportal_tempbasalend))
                    )
                    if (isConnect) {
                        lastStatus = PumpRunningState.Running.status
                        tempBasalMicrobolusOperations.operations.peek()
                    } else {
                        operation?.isCommandIssued = false
                    }
                }

                PumpDriverState.Suspended -> {
                    aapsLogger.info(LTag.PUMPBTCOMM, "suspended $operation")
                    callback.invoke(
                        PumpEnactResult(injector) //
                            .success(!isConnect) //
                            .enacted(!isConnect) //
                            .comment(rh.gs(string.careportal_tempbasalstart))
                    )
                    if (!isConnect) {
                        lastStatus = PumpRunningState.Suspended.status
                        tempBasalMicrobolusOperations.operations.peek()
                        //                        createTemporaryBasalData(oper.getDuration(),
                        //                                oper.getDose().setScale(2).doubleValue());
                    } else {
                        operation?.isCommandIssued = false
                    }
                }

                else                      -> {
                    aapsLogger.info(LTag.PUMPBTCOMM, "else $operation")
                    callback.invoke(
                        PumpEnactResult(injector) //
                            .success(!isConnect) //
                            .enacted(!isConnect) //
                            .comment(rh.gs(string.tempbasaldeliveryerror))
                    )
                    if (!isConnect) {
                        lastStatus = PumpRunningState.Suspended.status
                        tempBasalMicrobolusOperations.operations.peek()
                        //                        createTemporaryBasalData(oper.getDuration(),
                        //                                oper.getDose().setScale(2).doubleValue());
                    }
                    operation?.isCommandIssued = false

                }
            }
            f
        })
    }

    fun createTemporaryBasalData(duration: Int, dose: Double) {
        val type: TemporaryBasalType = if (dose == 0.0) TemporaryBasalType.PUMP_SUSPEND else {
            TemporaryBasalType.NORMAL
        }

        val timestamp = dateUtil.now()
        val tempData = PumpDbEntryTBR(
            timestamp,
            timestamp, pumpType,
            serialNumber(), null,
            dose,
            false, duration, type
        )
        pumpStatusData.runningTBR = tempData
        aapsLogger.info(LTag.DATABASE, "syncing temp basal data")
        val result = pumpSync.syncTemporaryBasalWithPumpId(
            timestamp,
            dose,
            T.mins(duration.toLong()).msecs(),
            false,
            tempData.tbrType,
            timestamp,
            pumpType,
            serialNumber()
        )
        pumpStatusData.tempBasalLength = duration
        pumpStatusData.tempBasalAmount = dose
        pumpSyncStorage.addTemporaryBasalRateWithTempId(tempData, true, this)
        incrementStatistics(MedtronicConst.Statistics.TBRsSet)
        finishAction("TBR")
        aapsLogger.info(LTag.EVENTS, "CreateTemporaryData")
    }

    private fun reactivatePump(oper: TempBasalMicroBolusPair, callback: Function1<PumpEnactResult, *>) {
        aapsLogger.debug(LTag.APS, "reactivate pump")
        medLinkService!!.medtronicUIComm?.executeCommandCP(
            MedLinkPumpMessage<PumpDriverState, Any>(
                buildChangeStatusFunction(
                    oper,
                    callback, ChangeStatusCallback.OperationType.START
                ), btSleepTime
            )
        )
    }

    private fun buildChangeStatusFunction(
        oper: TempBasalMicroBolusPair?,
        callback: Function1<PumpEnactResult, *>,
        operationType: ChangeStatusCallback.OperationType,
    ): MutableList<CommandStructure<PumpDriverState, BleCommand>> {
        aapsLogger.info(LTag.PUMPBTCOMM, "changeStatusFunction")
        oper?.let { aapsLogger.info(LTag.PUMPBTCOMM, it.toStringView()) }
        aapsLogger.info(LTag.PUMPBTCOMM, operationType.name)

        val function = ChangeStatusCallback(
            aapsLogger,
            operationType, this
        )
        val commandPriority: CommandPriority
        val command: BleCommand
        val commandType: MedLinkCommandType
        if (operationType == ChangeStatusCallback.OperationType.START) {
            commandType = MedLinkCommandType.StartPump
            command = BleStartCommand(aapsLogger, medLinkService!!.medLinkServiceData, this)
            commandPriority = CommandPriority.HIGH
        } else {
            commandType = MedLinkCommandType.StopPump
            command = BleStopCommand(aapsLogger, medLinkService!!.medLinkServiceData, this)
            commandPriority = CommandPriority.LOWER
        }

        val startStopFunction =
            getStartStopCallback(oper, callback, function, true)
        return mutableListOf(
            CommandStructure(
                MedLinkCommandType.StopStartPump, Optional.of(startStopFunction),
                Optional.of(command), commandPriority = commandPriority
            ), CommandStructure(
                commandType, Optional.of(startStopFunction),
                Optional.of(command), commandPriority = commandPriority
            )
        )

    }

    override fun getPumpStatus(status: String) {
        when {
            status.lowercase(Locale.getDefault()) == "clicked refresh" -> {
                readPumpData(false, 0L)
            }

            firstRun                                                   -> {
                initializePump(!isRefresh)
            }

            else                                                       -> {
                refreshAnyStatusThatNeedsToBeRefreshed()
            }
        }
        rxBus.send(EventMedtronicPumpValuesChanged())
    }

    fun sendChargingEvent() {
        //        rxBus.send(new EventChargingState(false, medLinkPumpStatus.batteryVoltage));
    }

    fun sendPumpUpdateEvent() {
        rxBus.send(EventMedtronicPumpValuesChanged())
    }

    private fun initializePump(b: Boolean) {
        aapsLogger.info(LTag.PUMP, logPrefix + "initializePump - start")
        aapsLogger.info(LTag.PUMP, logPrefix + b)
        if (medLinkService != null) {
            if (!b) {
                medLinkService!!.deviceCommunicationManager.wakeUp(false)
            }
            medLinkService!!.deviceCommunicationManager.setDoWakeUpBeforeCommand(false)
        } else {
            aapsLogger.info(LTag.PUMP, "nullmedlinkservice$medLinkService")
        }
        setRefreshButtonEnabled(false)
        readPumpData(false, 0L)
    }

    @SuppressLint("ResourceType") override fun postInit() {
        if (isRefresh) {
            if (isPumpNotReachable) {
                aapsLogger.error(logPrefix + "initializePump::Pump unreachable.")
                medtronicUtil.sendNotification(MedtronicNotificationType.PumpUnreachable, rh, rxBus)
                setRefreshButtonEnabled(true)
                return
            }
            medtronicUtil.dismissNotification(MedtronicNotificationType.PumpUnreachable, rxBus)
        }

        // model (once)
        if (medtronicUtil.medtronicPumpModel == null) {
            val func = Function<Supplier<Stream<String>>, MedLinkStandardReturn<MedLinkMedtronicDeviceType>> { f: Supplier<Stream<String>> ->
                val res = MedLinkModelParser.parse(f)
                medtronicUtil.medtronicPumpModel = res.functionResult
                res
            }
            val message = MedLinkPumpMessage<MedLinkMedtronicDeviceType, Any>(
                MedLinkCommandType.Connect,
                func,
                btSleepTime,
                BleConnectCommand(aapsLogger, medLinkService!!.medLinkServiceData, this),
                CommandPriority.HIGH
            )
            medLinkService!!.medtronicUIComm?.executeCommandCP(message)
        } else {
            if (medLinkPumpStatus.medtronicDeviceType != medtronicUtil.medtronicPumpModel) {
                //                getAapsLogger().warn(LTag.PUMP, getLogPrefix() + "Configured pump is not the same as one detected.");
                //                medtronicUtil.sendNotification(MedtronicNotificationType.PumpTypeNotSame, getRh(), getRxBus());
            }
        }
        pumpState = PumpDriverState.Connected

        // time (1h)
        //        checkTimeAndOptionallySetTime();

        //        readPumpHistory();

        // remaining insulin (>50 = 4h; 50-20 = 1h; 15m)
        //        medLinkService.getMedtronicUIComm().executeCommand(MedLinkMedtronicCommandType.GetRemainingInsulin);
        //        scheduleNextRefresh(MedLinkMedtronicStatusRefreshType.RemainingInsulin, 10);

        // remaining power (1h)
        //        medLinkService.getMedtronicUIComm().executeCommand(MedLinkMedtronicCommandType.GetBatteryStatus);
        //        scheduleNextRefresh(MedLinkMedtronicStatusRefreshType.BatteryStatus, 20);
        aapsLogger.info(LTag.PUMPBTCOMM, "postinit")
        scheduleNextRefresh()

        // configuration (once and then if history shows config changes)
        //        medLinkService.getMedtronicUIComm().executeCommand(MedLinkMedtronicCommandType.getSettings(medtronicUtil.getMedtronicPumpModel()));

        // read profile (once, later its controlled by isThisProfileSet method)

        //        if (initCommands.contains(getRh().gs(R.string.key_medlink_init_commands_profile))) {
        readPumpProfile()
        //        }

        readPumpBGHistory(true);
        if (sp.getBoolean(R.bool.key_medlink_init_commands_previous_bg_history, false)) {
            previousBGHistory()
        }
        if (sp.getBoolean(R.bool.key_medlink_init_commands_last_bolus_history, false)) {
            aapsLogger.info(LTag.PUMPBTCOMM, "read bolus history")
            readBolusHistory()
            readBolusHistory(true)
        }
        val errorCount = medLinkService!!.medtronicUIComm?.invalidResponsesCount
        if (errorCount!! >= 5) {
            aapsLogger.error("Number of error counts was 5 or more. Starting tunning.")
            setRefreshButtonEnabled(true)
            serviceTaskExecutor.startTask(WakeAndTuneTask(injector))
            return
        }
        medLinkPumpStatus.setLastCommunicationToNow()
        setRefreshButtonEnabled(true)
        rxBus.send(EventMedtronicPumpValuesChanged())
        if (!isRefresh) {
            pumpState = PumpDriverState.Initialized
        }
        isInitialized = true
        pumpState = PumpDriverState.Initialized
        firstRun = false
        aapsLogger.info(LTag.EVENTS, "pump initialized")
    }

    private fun readBolusHistory() {
        this.readBolusHistory(false)
    }

    private fun readBolusHistory(previous: Boolean) {
        medLinkService!!.medtronicUIComm?.executeCommandCP(bolusHistoryMessage(previous))
    }

    private fun bolusHistoryMessage(previous: Boolean): MedLinkPumpMessage<Stream<JSONObject>, Any> {
        aapsLogger.info(LTag.PUMPBTCOMM, "get full bolus history")
        lastBolusHistoryRead = System.currentTimeMillis()
        val func = BolusHistoryCallback(aapsLogger, this)
        val command: MedLinkCommandType = if (previous) {
            MedLinkCommandType.PreviousBolusHistory
        } else {
            MedLinkCommandType.BolusHistory
        }
        return MedLinkPumpMessage(
            command,
            func,
            btSleepTime, BlePartialCommand(aapsLogger, medLinkService!!.medLinkServiceData),
            CommandPriority.NORMAL
        )
    }

    private fun previousBGHistory() {
        if ((activePlugin.activeBgSource as PluginBase).javaClass.canonicalName?.contains("MedLinkPlugin") == true) {
            lastPreviousHistory = System.currentTimeMillis()
            medLinkService!!.medtronicUIComm?.executeCommandCP(previousBGHistory(false))
        }
    }

    private fun previousBGHistory(isCalibration: Boolean): MedLinkPumpMessage<BgSync.BgHistory, Any> {
        aapsLogger.info(LTag.PUMPBTCOMM, "get bolus history")
        val func = BGHistoryCallback(this, aapsLogger, true, isCalibration)
        return MedLinkPumpMessage(
            MedLinkCommandType.PreviousBGHistory,  //                MedLinkCommandType.PreviousIsigHistory,
            MedLinkCommandType.NoCommand,
            func,  //                isigFunc,
            btSleepTime,
            BlePartialCommand(aapsLogger, medLinkService!!.medLinkServiceData),
            CommandPriority.NORMAL
        )
    }

    private fun checkTimeAndOptionallySetTime() {
        aapsLogger.info(LTag.PUMP, "MedtronicPumpPlugin::checkTimeAndOptionallySetTime - Start")
        setRefreshButtonEnabled(false)
        if (isPumpNotReachable) {
            aapsLogger.debug(LTag.PUMP, "MedtronicPumpPlugin::checkTimeAndOptionallySetTime - Pump Unreachable.")
            setRefreshButtonEnabled(true)
            return
        }
        medtronicUtil.dismissNotification(MedtronicNotificationType.PumpUnreachable, rxBus)

        //        medLinkService.getMedtronicUIComm().executeCommand(MedLinkMedtronicCommandType.GetRealTimeClock);
        val clock = medtronicUtil.pumpTime ?: return

        //        if (clock == null) { // retry
        //            medLinkService.getMedtronicUIComm().executeCommand(MedLinkMedtronicCommandType.GetRealTimeClock);
        //
        //            clock = medtronicUtil.getPumpTime();
        //        }
        val timeDiff = Math.abs(clock.timeDifference)
        if (timeDiff > 20) {
            if (clock.localDeviceTime.year <= 2015 || timeDiff <= 24 * 60 * 60) {
                aapsLogger.info(LTag.PUMP, "MedtronicPumpPlugin::checkTimeAndOptionallySetTime - Time difference is {} s. Set time on pump.", timeDiff)

                //                medLinkService.getMedtronicUIComm().executeCommand(MedLinkMedtronicCommandType.SetRealTimeClock);
                if (clock.timeDifference == 0) {
                    val notification = Notification(Notification.INSIGHT_DATE_TIME_UPDATED, rh.gs(string.pump_time_updated), Notification.INFO, 60)
                    rxBus.send(EventNewNotification(notification))
                }
            } else {
                if (clock.localDeviceTime.year > 2015) {
                    aapsLogger.error("MedtronicPumpPlugin::checkTimeAndOptionallySetTime - Time difference over 24h requested [diff={} s]. Doing nothing.", timeDiff)
                    medtronicUtil.sendNotification(MedtronicNotificationType.TimeChangeOver24h, rh, rxBus)
                }
            }
        } else {
            aapsLogger.info(LTag.PUMP, "MedtronicPumpPlugin::checkTimeAndOptionallySetTime - Time difference is {} s. Do nothing.", timeDiff)
        }
        scheduleNextRefresh(MedLinkMedtronicStatusRefreshType.PumpTime, 0)
    }

    fun setPumpTime(currentTime: Long) {
        val clockDTO = ClockDTO(LocalDateTime(), LocalDateTime(currentTime))
        clockDTO.timeDifference = clockDTO.localDeviceTime.compareTo(clockDTO.pumpTime)
        medtronicUtil.pumpTime = clockDTO
    }

    private fun readPumpProfile() {
        lastProfileRead = System.currentTimeMillis()
        aapsLogger.info(LTag.PUMPBTCOMM, "get basal profiles")
        val basalCallback: Function<Supplier<Stream<String>>,
            MedLinkStandardReturn<BasalProfile>> = BasalCallback(aapsLogger, this)
        val profileCallback: Function<Supplier<Stream<String>>,
            MedLinkStandardReturn<Profile?>> = ProfileCallback(injector, aapsLogger, context, this)
        val msg = BasalMedLinkMessage<BasalProfile>(
            basalCallback, profileCallback,
            btSleepTime, BleCommand(aapsLogger, medLinkService!!.medLinkServiceData)
        )
        // val profileMsg: MedLinkPumpMessage<Profile> = MedLinkPumpMessage(
        //     MedLinkCommandType.BaseProfile, profileCallback,
        //     btSleepTime, BleCommand(aapsLogger, medLinkService!!.medLinkServiceData)
        // )

        medLinkService!!.medtronicUIComm?.executeCommandCP(msg)

        //        if (medtronicUITask.getResponseType() == MedtronicUIResponseType.Error) {
        //            getAapsLogger().info(LTag.PUMP, "reprocessing due to error response type");
        //            medLinkService.getMedtronicUIComm().executeCommandCP(msg);
        //        }
    }

    private fun scheduleNextRefresh() {
        scheduleNextRefresh(MedLinkMedtronicStatusRefreshType.PumpHistory, 0)
    }

    private fun scheduleNextRefresh(refreshType: MedLinkMedtronicStatusRefreshType, additionalMillis: Long): Int {
        when (refreshType) {
            MedLinkMedtronicStatusRefreshType.PumpHistory -> {
                aapsLogger.info(
                    LTag.PUMPBTCOMM, "Next refresh will be in " +
                        additionalMillis
                )


                workWithStatusRefresh(
                    StatusRefreshAction.Add, refreshType,
                    additionalMillis
                )
            }

            else                                          -> {
                workWithStatusRefresh(
                    StatusRefreshAction.GetData, refreshType,
                    additionalMillis
                )
            }
        }
        return 0
    }

    fun getTimeInFutureFromMinutes(minutes: Int): Long {
        return currentTime.toDateTime().toInstant().millis + getTimeInMs(minutes)
    }

    fun getTimeInMs(minutes: Int): Long {
        return minutes * 60 * 1000L
    }

    var hasStarted = false

    @Synchronized
    fun workWithStatusRefresh(
        action: StatusRefreshAction?,  //
        statusRefreshType: MedLinkMedtronicStatusRefreshType?,  //
        time: Long,
    ): Map<MedLinkMedtronicStatusRefreshType?, Long?>? {
        return when (action) {
            StatusRefreshAction.Add     -> {
                aapsLogger.info(LTag.PUMPBTCOMM, DateTime(System.currentTimeMillis() + time).toString())
                //                if(!statusRefreshMap.containsKey(statusRefreshType)) {
                statusRefreshMap[statusRefreshType] = pumpStatusData.lastConnection + time
                //                }
                null
            }

            StatusRefreshAction.GetData -> {
                HashMap(statusRefreshMap)
            }

            else                        -> null
        }
    }

    override fun isThisProfileSet(profile: Profile): Boolean {
        aapsLogger.debug(LTag.PUMP, "isThisProfileSet: basalInitalized=" + medLinkPumpStatus.basalProfileStatus)
        if (!isInitialized) return true

        //        if (medtronicPumpStatus.basalProfileStatus == BasalProfileStatus.NotInitialized) {
        //            // this shouldn't happen, but if there was problem we try again
        //            getPumpProfile();
        //            return isProfileSame(profile);
        //        } else
        return if (medLinkPumpStatus.basalProfileStatus === BasalProfileStatus.ProfileChanged) {
            false
        } else medLinkPumpStatus.basalProfileStatus !== BasalProfileStatus.ProfileOK || isProfileSame(profile)
    }

    private fun isProfileSame(profile: Profile): Boolean {
        var invalid = false
        val basalsByHour = medLinkPumpStatus.basalsByHour
        aapsLogger.debug(
            LTag.PUMP, "Current Basals (h):   "
                + if (basalsByHour == null) "null" else basalProfile!!.basalProfileAsString
        )

        // int index = 0;
        if (basalsByHour == null) return true // we don't want to set profile again, unless we are sure
        val stringBuilder = StringBuilder("Requested Basals (h): ")
        for (basalValue in profile.getBasalValues()) {
            val basalValueValue = pumpDescription.pumpType.determineCorrectBasalSize(basalValue.value)
            val hour = basalValue.timeAsSeconds / (60 * 60)
            if (!MedLinkMedtronicUtil.isSame(basalsByHour[hour], basalValueValue)) {
                invalid = true
            }
            stringBuilder.append(String.format(Locale.ENGLISH, "%.3f", basalValueValue))
            stringBuilder.append(" ")
        }
        aapsLogger.debug(LTag.PUMP, stringBuilder.toString())
        if (!invalid) {
            aapsLogger.debug(LTag.PUMP, "Basal profile is same as AAPS one.")
        } else {
            aapsLogger.debug(LTag.PUMP, "Basal profile on Pump is different than the AAPS one.")
        }
        return !invalid
    }

    override fun lastDataTime(): Long {
        return if (medLinkPumpStatus.lastConnection != 0L) {
            medLinkPumpStatus.lastConnection
        } else System.currentTimeMillis()
    }

    override val baseBasalRate: Double
        get() = medLinkPumpStatus.basalProfileForHour

    override fun stopBolusDelivering() {
        bolusDeliveryType = MedtronicPumpPluginInterface.BolusDeliveryType.CancelDelivery
    }

    private fun setRefreshButtonEnabled(enabled: Boolean) {
        rxBus.send(EventRefreshButtonState(enabled))
    }//

    //        return (!rileyLinkMedtronicService.getDeviceCommunicationManager().isDeviceReachable());
    //
    private val isPumpNotReachable: Boolean
        private get() {
            val medLinkServiceState = medLinkServiceData.medLinkServiceState
            if (medLinkServiceState == null) {
                aapsLogger.debug(LTag.PUMP, "MedLink unreachable. MedLinkServiceState is null.")
                return false
            }
            if (medLinkServiceState != MedLinkServiceState.PumpConnectorReady //
                && medLinkServiceState != MedLinkServiceState.MedLinkReady //
                && medLinkServiceState != MedLinkServiceState.TuneUpDevice
            ) {
                aapsLogger.debug(LTag.PUMP, "RileyLink unreachable.")
                return false
            }
            return false
            //        return (!rileyLinkMedtronicService.getDeviceCommunicationManager().isDeviceReachable());
        }
    private val logPrefix: String
        private get() = "MedLinkMedtronicPumpPlugin::"

    private fun readTBR(): TempBasalPair {
        return TempBasalPair(
            medLinkPumpStatus.tempBasalAmount, true,
            medLinkPumpStatus.tempBasalLength
        )
        //        MedtronicUITask responseTask = null;//rileyLinkMedtronicService.getMedtronicUIComm().executeCommand(MedtronicCommandType.ReadTemporaryBasal);
        //
        //        if (responseTask.hasData()) {
        //            TempBasalPair tbr = (TempBasalPair) responseTask.returnData;
        //
        //            // we sometimes get rate returned even if TBR is no longer running
        //            if (tbr.getDurationMinutes() == 0) {
        //                tbr.setInsulinRate(0.0d);
        //            }
        //
        //            return tbr;
        //        } else {
        //            return null;
        //        }
    }

    private fun finishAction(overviewKey: String?) {
        if (overviewKey != null) rxBus.send(EventRefreshOverview(overviewKey, false))
        triggerUIChange()
        setRefreshButtonEnabled(true)
    }

    private fun incrementStatistics(statsKey: String) {
        var currentCount = sp.getLong(statsKey, 0L)
        currentCount++
        sp.putLong(statsKey, currentCount)
    }

    // if enforceNew===true current temp basal is canceled and new TBR set (duration is prolonged),
    // if false and the same rate is requested enacted=false and success=true is returned and TBR is not changed
    override fun setTempBasalAbsolute(
        absoluteRate: Double, durationInMinutes: Int, profile: Profile,
        enforceNew: Boolean, callback: Function1<PumpEnactResult, *>,
    ) {
        val result: PumpEnactResult
        checkPumpNeedToBeStarted(absoluteRate, profile)
        aapsLogger.info(LTag.PUMPBTCOMM, "absolute rate $absoluteRate $durationInMinutes")
        aapsLogger.info(LTag.PUMPBTCOMM, "gettempBasal $temporaryBasal")
        result = if (absoluteRate != 0.0 && (absoluteRate == baseBasalRate ||
                Math.abs(absoluteRate - baseBasalRate) < pumpDescription.bolusStep)
        ) {
            aapsLogger.info(LTag.EVENTS, "cancelling temp basal")
            aapsLogger.info(LTag.EVENTS, "" + baseBasalRate)
            aapsLogger.info(LTag.EVENTS, "" + absoluteRate)
            aapsLogger.info(LTag.EVENTS, "" + pumpDescription.bolusStep)
            tempBasalMicrobolusOperations.operations.clear()
            clearTempBasal()
            PumpEnactResult(injector).enacted(true).success(true)
        } else if (temporaryBasal != null && temporaryBasal!!.desiredRate == absoluteRate && absoluteRate == 0.0) {
            aapsLogger.info(LTag.EVENTS, "extendbasaltreatment")
            extendBasalTreatment(durationInMinutes, callback)
        } else {
            if (absoluteRate == medLinkPumpStatus.currentBasal ||
                Math.abs(absoluteRate - medLinkPumpStatus.currentBasal) < pumpDescription.bolusStep
            ) {
                aapsLogger.info(LTag.EVENTS, "clearing temp basal")
                aapsLogger.info(LTag.EVENTS, "" + baseBasalRate)
                aapsLogger.info(LTag.EVENTS, "" + absoluteRate)
                aapsLogger.info(LTag.EVENTS, "" + pumpDescription.bolusStep)
                clearTempBasal()
            } else if (absoluteRate < medLinkPumpStatus.currentBasal) {
                aapsLogger.info(LTag.EVENTS, "suspending")
                aapsLogger.info(LTag.EVENTS, "" + baseBasalRate)
                aapsLogger.info(LTag.EVENTS, "" + absoluteRate)
                aapsLogger.info(LTag.EVENTS, "" + pumpDescription.bolusStep)
                tempBasalMicrobolusOperations.clearOperations()
                scheduleSuspension(
                    0, durationInMinutes, profile, callback,
                    absoluteRate,
                    PumpTempBasalType.Absolute
                )!!
            } else {
                aapsLogger.info(LTag.EVENTS, "bolusingbasal")
                aapsLogger.info(LTag.EVENTS, "" + baseBasalRate)
                aapsLogger.info(LTag.EVENTS, "" + absoluteRate)
                aapsLogger.info(LTag.EVENTS, "" + pumpDescription.bolusStep)
                clearTempBasal()
                scheduleTempBasalBolus(
                    0, durationInMinutes, profile, callback,
                    absoluteRate, PumpTempBasalType.Absolute
                )
            }
            //        result.success = false;
            //        result.comment = MainApp.gs(R.string.pumperror);
        }

        aapsLogger.info(LTag.EVENTS, "Settings temp basal percent: $result")

    }

    fun setTempBasalAbsolute(
        absoluteRate: Double, durationInMinutes: Int, profile: Profile,
        enforceNew: Boolean,
    ): PumpEnactResult {
        checkPumpNeedToBeStarted(absoluteRate, profile)
        setRefreshButtonEnabled(false)
        if (isPumpNotReachable) {
            setRefreshButtonEnabled(true)
            aapsLogger.info(LTag.PUMP, "pump unreachable")
            return PumpEnactResult(injector) //
                .success(false) //
                .enacted(false) //
                .comment(rh.gs(string.medtronic_pump_status_pump_unreachable))
        }
        medtronicUtil.dismissNotification(MedtronicNotificationType.PumpUnreachable, rxBus)
        aapsLogger.info(LTag.PUMP, logPrefix + "setTempBasalAbsolute: rate: " + absoluteRate + ", duration=" + durationInMinutes)

        // read current TBR
        val tbrCurrent = readTBR()
        aapsLogger.info(LTag.PUMP, logPrefix + "setTempBasalAbsolute: Current Basal: duration: " + tbrCurrent.durationMinutes + " min, rate=" + tbrCurrent.insulinRate)
        if (!enforceNew) {
            if (MedLinkMedtronicUtil.isSame(tbrCurrent.insulinRate, absoluteRate)) {
                var sameRate = true
                if (MedLinkMedtronicUtil.isSame(0.0, absoluteRate) && durationInMinutes > 0) {
                    // if rate is 0.0 and duration>0 then the rate is not the same
                    sameRate = false
                }
                if (sameRate) {
                    aapsLogger.info(LTag.PUMP, logPrefix + "setTempBasalAbsolute - No enforceNew and same rate. Exiting.")
                    finishAction("TBR")
                    return PumpEnactResult(injector).success(true).enacted(false)
                }
            }
            // if not the same rate, we cancel and start new
        }

        // if TBR is running we will cancel it.
        if (tbrCurrent.insulinRate != 0.0 && tbrCurrent.durationMinutes > 0) {
            aapsLogger.info(LTag.PUMP, logPrefix + "setTempBasalAbsolute - TBR running - so canceling it.")

            // CANCEL
            cancelTempBasal(true, object : Callback() {
                override fun run() {
                    aapsLogger.info(LTag.PUMPBTCOMM, "tbr cancelled")
                }
            })
            Toast.makeText(
                context, info.nightscout.androidaps.plugins.pump
                    .common.R
                    .string
                    .tempbasaldeliveryerror, Toast.LENGTH_SHORT
            ).show()
            //            Boolean response = (Boolean) responseTask2.returnData;

            //            if (response) {
            //                getAapsLogger().info(LTag.PUMP, getLogPrefix() + "setTempBasalAbsolute - Current TBR cancelled.");
            //            } else {
            //                getAapsLogger().error(getLogPrefix() + "setTempBasalAbsolute - Cancel TBR failed.");
            //
            //                finishAction("TBR");
            return PumpEnactResult(injector).success(false).enacted(false)
                .comment(rh.gs(string.medtronic_cmd_cant_cancel_tbr_stop_op))
            //            }
        }
        return PumpEnactResult(injector).success(false).enacted(false)
            .comment(rh.gs(string.medtronic_cmd_cant_cancel_tbr_stop_op))
        // now start new TBR
        //        MedtronicUITask responseTask = null;//rileyLinkMedtronicService.getMedtronicUIComm().executeCommand(MedtronicCommandType.SetTemporaryBasal,
        //        //absoluteRate, durationInMinutes);
        //
        //        Boolean response = (Boolean) responseTask.returnData;
        //
        //        getAapsLogger().info(LTag.PUMP, getLogPrefix() + "setTempBasalAbsolute - setTBR. Response: " + response);
        //
        //        if (response) {
        //            // FIXME put this into UIPostProcessor
        //            medtronicPumpStatus.tempBasalStart = new Date();
        //            medtronicPumpStatus.tempBasalAmount = absoluteRate;
        //            medtronicPumpStatus.tempBasalLength = durationInMinutes;
        //
        //            TemporaryBasal tempStart = new TemporaryBasal(getInjector()) //
        //                    .date(System.currentTimeMillis()) //
        //                    .duration(durationInMinutes) //
        //                    .absolute(absoluteRate) //
        //                    .source(Source.USER);
        //
        //            activePlugin.getActiveTreatments().addToHistoryTempBasal(tempStart);
        //
        //            incrementStatistics(MedtronicConst.Statistics.TBRsSet);
        //
        //            finishAction("TBR");
        //
        //            return new PumpEnactResult(getInjector()).success(true).enacted(true) //
        //                    .absolute(absoluteRate).duration(durationInMinutes);
        //
        //        } else {
        //            finishAction("TBR");
        //
        //            return new PumpEnactResult(getInjector()).success(false).enacted(false) //
        //                    .comment(getRh().gs(R.string.medtronic_cmd_tbr_could_not_be_delivered));
        //        }
    }

    private fun checkPumpNeedToBeStarted(absoluteRate: Double, profile: Profile) {
        val previousBasal = temporaryBasal
        if (previousBasal != null) {
            if (absoluteRate > 0.0 && previousBasal.desiredRate!! < profile.getBasal()) {
                aapsLogger.info(LTag.CORE, "starting pump")
                startPump(object : Callback() {
                    override fun run() {}
                })
            }
        }
    }

    protected fun clearTempBasal(): PumpEnactResult {
        aapsLogger.info(LTag.EVENTS, " clearing temp basal")
        val result = buildPumpEnactResult()
        tempBasalMicrobolusOperations.clearOperations()
        result.success = true
        result.comment = rh.gs(string.canceltemp)
        if (pumpStatusData.pumpRunningState == PumpRunningState.Suspended) {
            aapsLogger.info(LTag.EVENTS, "starting pump")

            startPump(object : Callback() {
                override fun run() {
                    aapsLogger.info(LTag.EVENTS, "temp basal cleared")
                }
            })
        }
        //        createTemporaryBasalData(0, 0);
        return result
    }

    private fun buildSuspensionScheduler(
        totalSuspendedMinutes: Int,  //                                                                                  Integer suspensions,
        durationInMinutes: Int,
        callback: Function1<*, *>,
    ): LinkedBlockingDeque<TempBasalMicroBolusPair> {
        var mod = 0
        var mergeOperations = 0
        var operationInterval = 0
        var possibleSuspensions: Int
        var operationDuration = 0
        val durationDouble = durationInMinutes - totalSuspendedMinutes
        if (durationDouble == 0) {
            operationDuration = totalSuspendedMinutes
            possibleSuspensions = 1
        } else {
            possibleSuspensions = (durationDouble / Constants.INTERVAL_BETWEEN_OPERATIONS)
            val neededSuspensions = (totalSuspendedMinutes.toDouble() /
                Constants.INTERVAL_BETWEEN_OPERATIONS).roundToInt()
            if (neededSuspensions < possibleSuspensions) {
                possibleSuspensions = neededSuspensions
            }
            if (possibleSuspensions > 0) {
                operationInterval = durationDouble / possibleSuspensions
                mod = operationInterval % Constants.INTERVAL_BETWEEN_OPERATIONS
                if (mod / Constants.INTERVAL_BETWEEN_OPERATIONS > 0.5) {
                    operationInterval += Constants.INTERVAL_BETWEEN_OPERATIONS - mod
                } else {
                    operationInterval -= mod
                }
                operationDuration = (totalSuspendedMinutes.toDouble() / possibleSuspensions).toInt()
                if (operationDuration < Constants.INTERVAL_BETWEEN_OPERATIONS) {
                    operationDuration = Constants.INTERVAL_BETWEEN_OPERATIONS
                }
                mod = operationDuration % Constants.INTERVAL_BETWEEN_OPERATIONS
                if (mod / Constants.INTERVAL_BETWEEN_OPERATIONS > 0.5) {
                    operationDuration += Constants.INTERVAL_BETWEEN_OPERATIONS - mod
                } else {
                    operationDuration -= mod
                }
                if (totalSuspendedMinutes > operationDuration * possibleSuspensions) {
                    val diff = totalSuspendedMinutes - operationDuration * possibleSuspensions
                    mergeOperations = diff / Constants.INTERVAL_BETWEEN_OPERATIONS
                }
                if ((operationDuration + operationInterval) * possibleSuspensions > java.lang.Double.valueOf(durationInMinutes.toDouble())) {
                    while (operationInterval >= 2 * Constants.INTERVAL_BETWEEN_OPERATIONS &&
                        (operationDuration + operationInterval) * possibleSuspensions >
                        java.lang.Double.valueOf(durationInMinutes.toDouble())
                    ) {
                        operationInterval -= Constants.INTERVAL_BETWEEN_OPERATIONS
                    }
                    while (mergeOperations * operationDuration + (operationDuration + operationInterval) *
                        (possibleSuspensions - mergeOperations) > durationInMinutes && possibleSuspensions > 1
                    ) {
                        mergeOperations++
                    }
                    possibleSuspensions -= mergeOperations
                }
            }
            //        operationDuration = Double.valueOf(totalSuspendedMinutes) / Double.valueOf(suspensions);
            //        if (operationDuration < Constants.INTERVAL_BETWEEN_OPERATIONS) {
            //            operationDuration = (double) Constants.INTERVAL_BETWEEN_OPERATIONS;
            //        }
            //        mod = operationDuration % Constants.INTERVAL_BETWEEN_OPERATIONS;
            //        if ((mod / Constants.INTERVAL_BETWEEN_OPERATIONS) > 0.5) {
            //            operationDuration += Constants.INTERVAL_BETWEEN_OPERATIONS - mod;
            //        } else {
            //            operationDuration -= mod;
            //        }
        }
        val nextStartStop = NextStartStop()
        var startStopDateTime = nextStartStop.getNextStartStop(operationDuration, operationInterval)
        var totalSuspended = 0
        val operations = LinkedBlockingDeque<TempBasalMicroBolusPair>()
        var oper = 0
        while (oper < possibleSuspensions) {
            operations.add(
                TempBasalMicroBolusPair(
                    totalSuspendedMinutes, 0.0,
                    0.0, startStopDateTime.startOperationTime,
                    TempBasalMicroBolusPair.OperationType.SUSPEND, callback
                )
            )
            operations.add(
                TempBasalMicroBolusPair(
                    totalSuspendedMinutes, 0.0,
                    0.0, startStopDateTime.endOperationTime,
                    TempBasalMicroBolusPair.OperationType.REACTIVATE, callback
                )
            )
            if (mergeOperations > 0) {
                startStopDateTime = nextStartStop.getNextStartStop(
                    operationDuration * 2,
                    operationInterval
                )
                mergeOperations--
                totalSuspended += operationDuration * 2
            } else {
                startStopDateTime = nextStartStop.getNextStartStop(
                    operationDuration,
                    operationInterval
                )
                totalSuspended += operationDuration
            }
            oper += 1
        }
        return mergeExtraOperations(operations, mod, possibleSuspensions)
    }

    private fun mergeExtraOperations(
        operations: LinkedBlockingDeque<TempBasalMicroBolusPair>, mod: Int, suspensions: Int,
    ): LinkedBlockingDeque<TempBasalMicroBolusPair> {
        var deltaMod = mod * suspensions
        var previousReleaseTime: LocalDateTime? = null
        val toRemove: MutableList<Int> = ArrayList()
        val newOperations = LinkedBlockingDeque<TempBasalMicroBolusPair>()
        for ((index, operation) in operations.withIndex()) {
            if (deltaMod > Constants.INTERVAL_BETWEEN_OPERATIONS &&
                operation.operationType == TempBasalMicroBolusPair.OperationType.REACTIVATE
            ) {
                operation.delayInMinutes(Constants.INTERVAL_BETWEEN_OPERATIONS)
                previousReleaseTime = operation.releaseTime
                deltaMod -= Constants.INTERVAL_BETWEEN_OPERATIONS
            }
            if (previousReleaseTime != null && operation.operationType ==
                TempBasalMicroBolusPair.OperationType.SUSPEND && operation.releaseTime.compareTo(previousReleaseTime) >= 0
            ) {
                toRemove.add(index)
                toRemove.add(index + 1)
            } else {
                newOperations.add(operation)
            }
        }
        return newOperations
    }

    private fun scheduleSuspension(
        percent: Int,
        durationInMinutes: Int,
        profile: Profile,
        callback: Function1<*, *>,
        absoluteBasalValue: Double,
        pumpTempBasalType: PumpTempBasalType,
    ): PumpEnactResult? {
        this.durationInMinutes = durationInMinutes
        var totalSuspendedMinutes = 0
        var calcPercent: Double
        if (pumpTempBasalType == PumpTempBasalType.Absolute) {
            if (absoluteBasalValue != 0.0) {
                calcPercent = 1 - absoluteBasalValue / profile.getBasal()
                totalSuspendedMinutes = calculateTotalSuspended(calcPercent)
            } else if (percent == 0) {
                totalSuspendedMinutes = durationInMinutes
            }
        } else {
            calcPercent = 1 - percent / 100.0
            totalSuspendedMinutes = calculateTotalSuspended(calcPercent)
        }
        this.percent = percent
        if (totalSuspendedMinutes < Constants.INTERVAL_BETWEEN_OPERATIONS) {
            return result
        } else {
            //            int suspensions;
            //            if (totalSuspendedMinutes < durationInMinutes / 2) {
            //                suspensions = new BigDecimal(totalSuspendedMinutes)
            //                        .divide(new BigDecimal(Constants.INTERVAL_BETWEEN_OPERATIONS), RoundingMode.HALF_UP)
            //                        .setScale(0, RoundingMode.HALF_UP).intValue();
            //            } else {
            ////                int delta = durationInMinutes - ;
            //                suspensions = totalSuspendedMinutes / Constants.INTERVAL_BETWEEN_OPERATIONS;
            //                if (suspensions == 0) {
            //                    suspensions++;
            //                }
            //            }

            //TODO need to reavaluate some cases, used floor here to avoid two possible up rounds
            val operations = buildSuspensionScheduler(
                totalSuspendedMinutes, durationInMinutes, callback
            )
            tempBasalMicrobolusOperations.updateOperations(
                operations.size, 0.0,
                operations,
                totalSuspendedMinutes
            )
            tempBasalMicrobolusOperations.absoluteRate = absoluteBasalValue
            tempBasalMicrobolusOperations.let {
                if (it != null && it.operations.isNotEmpty() &&
                    it.operations.first.operationType == TempBasalMicroBolusPair.OperationType.SUSPEND &&
                    pumpStatusData.pumpRunningState == PumpRunningState.Suspended && it.operations.first.releaseTime.isBefore(LocalDateTime.now())
                ) {
                    operations.removeFirst()
                } else if (it != null && it.operations.isNotEmpty() &&
                    it.operations.first.operationType == TempBasalMicroBolusPair.OperationType.SUSPEND &&
                    pumpStatusData.pumpRunningState == PumpRunningState.Suspended && percent > 0
                ) {
                    aapsLogger.info(LTag.EVENTS, "temp basal starting pump")

                    startPump(object : Callback() {
                        override fun run() {}
                    })
                }
                it
            }
        }


        refreshAnyStatusThatNeedsToBeRefreshed()
        //criar fila de comandos aqui, esta fila deverá ser consumida a cada execução de checagem de status
        return buildPumpEnactResult().enacted(true).success(true).comment(rh.gs(string.medtronic_cmd_desc_set_tbr))
    }

    private fun calculateTotalSuspended(calcPercent: Double): Int {
        val suspended = durationInMinutes!! * calcPercent
        return BigDecimal(java.lang.Double.toString(suspended)).setScale(0, RoundingMode.HALF_UP).toInt()
    }

    private fun buildFirstLevelTempBasalMicroBolusOperations(
        percent: Int, basalProfilesFromTemp: LinkedList<ProfileValue?>,
        durationInMinutes: Int, callback: Function1<*, *>, absoluteBasalValue: Double,
        basalType: PumpTempBasalType,
    ): TempBasalMicrobolusOperations {
        val insulinPeriod: MutableList<TempBasalMicroBolusDTO> = ArrayList()
        var previousProfileValue: ProfileValue? = null
        var spentBasalTimeInSeconds = 0
        var currentStep = 0
        var totalAmount = 0.0
        var durationInSeconds = durationInMinutes * 60
        val time = currentTime
        var startedTime = time.hourOfDay * 3600 + time.minuteOfHour * 60 + time.secondOfMinute
        while (!basalProfilesFromTemp.isEmpty()) {
            val currentProfileValue = basalProfilesFromTemp.pollFirst()
            if (previousProfileValue == null) {
                previousProfileValue = currentProfileValue
            }
            if (currentProfileValue!!.timeAsSeconds == 0) {
                if (currentStep > 0) {
                    startedTime = 0
                }
                durationInSeconds -= spentBasalTimeInSeconds
                spentBasalTimeInSeconds = 0
            }
            val tempBasalPair = calculateTempBasalDosage(
                startedTime + spentBasalTimeInSeconds,
                currentProfileValue, basalProfilesFromTemp, percent, absoluteBasalValue,
                durationInSeconds - spentBasalTimeInSeconds, basalType
            )
            totalAmount += tempBasalPair.insulinRate
            spentBasalTimeInSeconds += tempBasalPair.durationMinutes * 60
            insulinPeriod.add(tempBasalPair)
            currentStep++
        }
        val roundedTotalAmount = BigDecimal(totalAmount).setScale(
            1,
            RoundingMode.HALF_UP
        ).toDouble()
        val operations: TempBasalMicrobolusOperations
        operations = if (roundedTotalAmount == pumpType.bolusSize) {
            val tempBasalList = LinkedBlockingDeque<TempBasalMicroBolusPair>()
            tempBasalList.add(
                TempBasalMicroBolusPair(
                    0, roundedTotalAmount, totalAmount,
                    currentTime.plusMinutes(durationInMinutes / 2),
                    TempBasalMicroBolusPair.OperationType.BOLUS, callback
                )
            )
            TempBasalMicrobolusOperations(
                1, totalAmount,
                durationInMinutes,
                tempBasalList
            )
        } else if (roundedTotalAmount < pumpType.bolusSize) {
            cancelTempBasal(true)
            TempBasalMicrobolusOperations(
                0, 0.0, 0,
                LinkedBlockingDeque()
            )
        } else {
            buildTempBasalSMBOperations(
                roundedTotalAmount, insulinPeriod, callback,
                durationInMinutes, absoluteBasalValue
            )
        }
        if (basalType == PumpTempBasalType.Percent) {
            operations.absoluteRate = totalAmount
        }
        return operations
    }

    private fun calculateTempBasalDosage(
        startedTime: Int,
        currentProfileValue: ProfileValue?,
        basalProfilesFromTemp: LinkedList<ProfileValue?>,
        percent: Int, abs: Double,
        remainingDurationInSeconds: Int,
        basalType: PumpTempBasalType,
    ): TempBasalMicroBolusDTO {
        var delta = 0
        if (basalProfilesFromTemp.isEmpty()) {
            delta = remainingDurationInSeconds
        } else if (basalProfilesFromTemp.peekFirst()!!.timeAsSeconds < startedTime) {
            if (basalProfilesFromTemp.peekFirst()!!.timeAsSeconds == 0) {
                delta = Constants.SECONDS_PER_DAY - startedTime
            }
        } else {
            delta = basalProfilesFromTemp.peekFirst()!!.timeAsSeconds - startedTime
        }
        var profileDosage = 0.0
        profileDosage = if (basalType == PumpTempBasalType.Percent) {
            calcTotalAmountPct(percent, currentProfileValue!!.value, delta / 60)
        } else {
            calcTotalAmountAbs(abs, currentProfileValue!!.value, delta / 60)
        }
        return createTempBasalPair(profileDosage, delta, currentProfileValue.timeAsSeconds, basalType)
    }

    private fun createTempBasalPair(
        totalAmount: Double, durationInSeconds: Int,
        startTime: Int, basalType: PumpTempBasalType,
    ): TempBasalMicroBolusDTO {
        return TempBasalMicroBolusDTO(
            totalAmount, basalType == PumpTempBasalType.Percent, durationInSeconds / 60,
            startTime, startTime + durationInSeconds
        )
    }

    private fun calcTotalAmountPct(
        tempBasal: Int, currentBasalValue: Double,
        durationInMinutes: Int,
    ): Double {
        var tempBasal = tempBasal
        if (tempBasal > 100) {
            tempBasal -= 100
        }
        return tempBasal / 100.0 * currentBasalValue * (durationInMinutes / 60.0)
    }

    private fun calcTotalAmountAbs(
        tempBasal: Double, currentBasalValue: Double,
        durationInMinutes: Int,
    ): Double {
        return (tempBasal - currentBasalValue) * (durationInMinutes / 60.0)
    }

    private fun calcTotalAmount(percent: Int, value: Double, durationInMinutes: Int): Double {
        return (percent.toDouble() / 100 - 1) * value * (durationInMinutes / 60.0)
    }

    open val currentTime: LocalDateTime
        get() = LocalDateTime.now()

    private fun buildTempBasalSMBOperations(
        totalAmount: Double,
        insulinPeriod: List<TempBasalMicroBolusDTO>,
        callback: Function1<*, *>,
        durationInMinutes: Int,
        absoluteRate: Double,
    ): TempBasalMicrobolusOperations {
        val result = TempBasalMicrobolusOperations()
        result.durationInMinutes = durationInMinutes
        result.absoluteRate = absoluteRate
        var operationTime = currentTime
        var minDosage = 0.0
        for (period in insulinPeriod) {
            val periodDose = period.insulinRate // + accumulatedNextPeriodDose;
            val roundedPeriodDose = BigDecimal(periodDose).setScale(1, RoundingMode.HALF_DOWN).toDouble()
            val time = (period.endTimeInSeconds - period.sartTimeInSeconds) / 60
            val periodAvailableOperations: Int = time / Constants.INTERVAL_BETWEEN_OPERATIONS
            aapsLogger.info(LTag.PUMPBTCOMM, "" + time)
            aapsLogger.info(LTag.PUMPBTCOMM, "" + periodAvailableOperations)
            val minBolusDose = pumpType.bolusSize
            if (roundedPeriodDose >= minBolusDose) {
                val doses = BigDecimal(
                    roundedPeriodDose / minBolusDose
                ).setScale(0, RoundingMode.HALF_DOWN).toInt()
                val calculatedDose = BigDecimal(periodDose).divide(BigDecimal(doses), 2, RoundingMode.HALF_DOWN).toDouble()
                aapsLogger.info(LTag.PUMP,periodDose.toString())
                aapsLogger.info(LTag.PUMP,calculatedDose.toString())
                minDosage = BigDecimal(periodDose / doses).setScale(1, RoundingMode.HALF_DOWN).toDouble()
                val list = buildOperations(doses.toDouble(), periodAvailableOperations, emptyList())
                //TODO convert build operations to return list of tempmicroboluspair
                for (dose in list) {
                    if (dose > 0) {
                        val pair = TempBasalMicroBolusPair(
                            0,
                            dose, calculatedDose, operationTime,
                            TempBasalMicroBolusPair.OperationType.BOLUS, callback
                        )
                        aapsLogger.info(LTag.EVENTS, pair.toString())
                        result.operations.add(pair)
                    }
                    operationTime = if (currentTime.isAfter(operationTime)) {
                        val refresh = statusRefreshMap[MedLinkMedtronicStatusRefreshType.PumpHistory]
                        if (refresh!! > currentTime.plusMinutes(5).toDateTime().millis) {
                            operationTime.plusMinutes(Constants.INTERVAL_BETWEEN_OPERATIONS)
                        } else {
                            val instant = Instant.ofEpochMilli(refresh)
                            instant.toDateTime().withZone(DateTimeZone.getDefault()).toLocalDateTime()
                        }
                    } else {
                        operationTime.plusMinutes(Constants.INTERVAL_BETWEEN_OPERATIONS)
                    }
                }
            }
        }
        val totalDose = result.operations.stream().map { obj: TempBasalMicroBolusPair -> obj.dose }.reduce(0.0) { a: Double, b: Double -> java.lang.Double.sum(a, b) }
        val doseDiff = totalDose - totalAmount
        if (totalDose.compareTo(totalAmount) > 0 && Math.abs(doseDiff - minDosage) < 1E-2) {
            result.operations = excludeExtraDose(totalDose, totalAmount, result)
            aapsLogger.info(
                LTag.AUTOMATION,
                "Error in temp basal microbolus calculation, totalDose: $totalDose, totalAmount $totalAmount, profiles $insulinPeriod, absoluteRate: $absoluteRate, insulin period$insulinPeriod"
            )
        } else if (totalAmount.compareTo(totalDose) > 0 && Math.abs(doseDiff) >= minDosage) {
            //TODO need a test to verify if this is reacheable
            aapsLogger.info(
                LTag.AUTOMATION,
                "Error in temp basal microbolus calculation, totalDose: $totalDose, totalAmount $totalAmount, profiles $insulinPeriod, absoluteRate: $absoluteRate, insulin period$insulinPeriod"
            )
            //            throw new RuntimeException("Error in temp basal microbolus calculation, totalDose: " + totalDose + ", totalAmount " + totalAmount + ", profiles " + insulinPeriod);
        }
        return result
    }

    private fun excludeExtraDose(
        totalDose: Double,
        totalAmount: Double,
        result: TempBasalMicrobolusOperations,
    ): LinkedBlockingDeque<TempBasalMicroBolusPair> {
        var dosesToDecrease = round((totalDose - totalAmount) / pumpType.bolusSize).toInt()
        val maxDosage = result.operations.stream().map { obj: TempBasalMicroBolusPair -> obj.dose }
            .max { obj: Double, anotherDouble: Double? -> obj.compareTo(anotherDouble!!) }.orElse(0.0)
        val minDosage = result.operations.stream().map { obj: TempBasalMicroBolusPair -> obj.dose }
            .min { obj: Double, anotherDouble: Double? -> obj.compareTo(anotherDouble!!) }.orElse(0.0)
        var operations = LinkedBlockingDeque<TempBasalMicroBolusPair>()
        if (maxDosage == minDosage) {
            val sortedOperations = result.operations.stream().sorted { prev: TempBasalMicroBolusPair, curr: TempBasalMicroBolusPair -> prev.delta.compareTo(curr.delta) }
            operations = sortedOperations.skip(dosesToDecrease.toLong()).sorted { prev: TempBasalMicroBolusPair, curr: TempBasalMicroBolusPair -> prev.releaseTime.compareTo(curr.releaseTime) }
                .collect(
                    Collectors.toCollection(
                        Supplier<LinkedBlockingDeque<TempBasalMicroBolusPair>> { LinkedBlockingDeque() })
                )
        } else {
            while (!result.operations.isEmpty()) {
                val tmp = result.operations.pollFirst()
                if (tmp.dose == maxDosage && dosesToDecrease > 0) {
                    dosesToDecrease -= 1
                    if (tmp.dose.compareTo(pumpType.bolusSize) > 0) {
                        operations.add(tmp.decreaseDosage(pumpType.bolusSize))
                    }
                } else {
                    operations.add(tmp)
                }
            }
        }
        return operations
    }

    private fun buildOperationsList(doses: Double, operations: Int, dose: Double): List<Double> {
        val result = DoubleArray(operations)
        Arrays.fill(result, dose)
        return buildOperations(doses - operations, operations, result.asList())
    }

    fun buildOperations(doses: Double, operations: Int, list: List<Double>): List<Double> {
        var list: MutableList<Double> = list.toMutableList()
        if (list.isEmpty()) {
            val values = DoubleArray(operations)
            Arrays.fill(values, 0.0)
            list = values.asList().toMutableList()
        }
        return if (doses == 0.0 || operations == 0) {
            list
        } else if (doses > operations) {
            buildOperationsList(doses, operations, list[0] + pumpType.bolusSize)
        } else {
            val step = operations.toDouble() / doses
            if (doses == 1.0) {
                var position = Math.floorDiv(operations, 2) - 1
                if (position < 0) {
                    position = 0
                }
                list[position] = list[position] + pumpType.bolusSize
                list[position] = BigDecimal(list[position]).setScale(
                    1,
                    RoundingMode.HALF_UP
                ).toDouble()
                return list
            }
            if (step < 2.5) {
                buildSmallStepsTempSMBDosage(operations, doses, list, step)
            } else {
                buildBigStepsTempSMBDosage(operations, doses, list, step)
            }
        }
    }

    private fun buildBigStepsTempSMBDosage(operations: Int, doses: Double, list: MutableList<Double>, step: Double): List<Double> {
        var doses = doses
        var nextStep = step
        list[0] = list[0] + pumpType.bolusSize
        doses--
        for (index in 1 until operations) {
            if (doses > 0 && nextStep < index) {
                doses--
                list[index] = BigDecimal(list[index] + pumpType.bolusSize).setScale(
                    1,
                    RoundingMode.HALF_UP
                ).toDouble()
                nextStep = index + step
            }
        }
        return if (doses == 0.0) {
            list
        } else {
            aapsLogger.info(LTag.PUMPBTCOMM, "unreachable?")
            //TODO unreachable code
            buildOperations(doses, operations, list)
        }
    }

    private fun buildSmallStepsTempSMBDosage(operations: Int, doses: Double, list: List<Double>, step: Double): List<Double> {
        val diff = operations - doses
        return when (diff) {
            3.0  -> {
                val newStep = java.lang.Double.valueOf(Math.floor(operations * 0.33)).toInt()
                val secondPosition = 2 * newStep
                val thirdPosition = 3 * newStep
                fillTempSMBWithExclusions(list, newStep, secondPosition, thirdPosition)
            }
            2.0  -> {
                val newStep = java.lang.Double.valueOf(Math.floor(operations * 0.25)).toInt()
                val half = round(operations / diff).toInt()
                val secondPosition = half + newStep
                fillTempSMBWithExclusions(list, newStep, secondPosition)
            }
            1.0  -> {
                fillTempSMBWithExclusions(list, Math.floorDiv(operations, 2))
            }
            else -> {
                fillTempSMBWithStep(list, step, doses)
            }
        }
    }

    private fun fillTempSMBWithStep(list: List<Double>, step: Double, doses: Double): List<Double> {
        var doses = doses
        val result: MutableList<Double> = ArrayList()
        var index = 0
        var stepIndex = 0
        for (value in list) {
            val currentStep = step * stepIndex
            if (doses == 0.0) {
                result.add(value)
            } else if (index == 0) {
                stepIndex++
                result.add(value + pumpType.bolusSize)
                doses--
            } else if (currentStep <= index) {
                stepIndex++
                result.add(value + pumpType.bolusSize)
                doses--
            } else {
                result.add(value)
            }
            index++
        }
        return result
    }

    private fun fillTempSMBWithExclusions(list: List<Double>, vararg exclusions: Int): List<Double> {
        val result: MutableList<Double> = ArrayList()
        var index = 0
        for (value in list) {
            if (!exclusions.contains(index)) {
                result.add(value + pumpType.bolusSize)
            } else {
                result.add(value)
            }
            index++
        }
        return result
    }

    private fun scheduleTempBasalBolus(
        percent: Int, durationInMinutes: Int,
        profile: Profile, callback: Function1<*, *>, absoluteBasalValue: Double, basalType: PumpTempBasalType,
    ): PumpEnactResult {
        val currentTime = currentTime
        val currentTas = currentTime.millisOfDay / 1000
        val endTas = currentTas + durationInMinutes * 60L
        val basalProfilesFromTemp = extractTempProfiles(profile, endTas, currentTas)
        tempBasalMicrobolusOperations = buildFirstLevelTempBasalMicroBolusOperations(
            percent,
            basalProfilesFromTemp, durationInMinutes, callback, absoluteBasalValue, basalType
        )
        refreshAnyStatusThatNeedsToBeRefreshed()
        return buildPumpEnactResult().enacted(true).success(true).comment(rh.gs(string.medtronic_cmd_desc_set_tbr))
    }

    private fun extractTempProfiles(profile: Profile, endTas: Long, currentTas: Int): LinkedList<ProfileValue?> {
        val tempBasalProfiles = LinkedList<ProfileValue?>()
        var previousProfile: ProfileValue? = null
        val basalValues = cloneProfileValue(profile)
        for (basalValue in basalValues) {
            if (endTas < basalValue!!.timeAsSeconds) {
                if (previousProfile == null) {
                    tempBasalProfiles.add(basalValue)
                }
                break
            }
            if (currentTas <= basalValue.timeAsSeconds) {
                if (tempBasalProfiles.isEmpty()) {
                    if (currentTas < basalValue.timeAsSeconds) {
                        tempBasalProfiles.add(previousProfile)
                    }
                    tempBasalProfiles.add(basalValue)
                } else if (previousProfile == null || basalValue.value != previousProfile.value) {
                    tempBasalProfiles.add(basalValue)
                }
            }
            previousProfile = basalValue
        }
        if (tempBasalProfiles.isEmpty()) {
            if (previousProfile!!.timeAsSeconds < endTas && previousProfile.timeAsSeconds < currentTas) {
                tempBasalProfiles.add(previousProfile)
            }
        }
        if (endTas >= Constants.SECONDS_PER_DAY) {
            if (tempBasalProfiles.isEmpty() && previousProfile != null) {
                previousProfile.timeAsSeconds = currentTas
                tempBasalProfiles.add(previousProfile)
            }
            tempBasalProfiles.addAll(extractTempProfiles(profile, endTas - Constants.SECONDS_PER_DAY, 0))
        }
        return tempBasalProfiles
    }

    private fun cloneProfileValue(profile: Profile): Array<ProfileValue?> {
        val basalValues = profile.getBasalValues()
        val cloned = arrayOfNulls<ProfileValue>(basalValues.size)
        for (index in cloned.indices) {
            val profileValue = basalValues[index]
            cloned[index] = ProfileValue(profileValue.timeAsSeconds, profileValue.value)
        }
        return cloned
    }

    protected open fun buildPumpEnactResult(): PumpEnactResult {
        return PumpEnactResult(injector)
    }

    fun setTempBasalPercent(percent: Int?, durationInMinutes: Int?, profile: Profile?, enforceNew: Boolean): PumpEnactResult {
        aapsLogger.debug(LTag.PUMP, "setTempBasalPercent [PumpPluginAbstract] - Not implemented.")
        return PumpEnactResult(injector).success(false).enacted(false).comment(
            rh.gs(
                info.nightscout.androidaps.plugins.pump.common.R.string.pump_operation_not_supported_by_pump_driver
            )
        )
    }

    override fun setTempBasalPercent(
        percent: Int, durationInMinutes: Int,
        profile: Profile, enforceNew: Boolean, callback: Function1<PumpEnactResult, *>,
    ): PumpEnactResult {

        if (temporaryBasal != null) {

            aapsLogger.info(LTag.PUMPBTCOMM,temporaryBasal.toString())

            val previousBasal = temporaryBasal
            if ((pumpStatusData.pumpRunningState == PumpRunningState.Suspended || (previousBasal?.desiredPct != null && previousBasal.desiredPct!! < 100)) && percent >= 100) {
                aapsLogger.info(LTag.EVENTS, "temp basal starting pump")

                startPump(object : Callback() {
                    override fun run() {}
                })
            } else if (percent == 0) {
                createTemporaryBasalData(durationInMinutes, percent.toDouble())
            }
        }
        tempBasalMicrobolusOperations.operations.clear()
        val result: PumpEnactResult = when {
            percent == 100 -> {
                clearTempBasal()
            }

            percent < 100  -> {
                scheduleSuspension(
                    percent, durationInMinutes, profile, callback, 0.0,
                    PumpTempBasalType.Percent
                )!!
            }

            else           -> {
                scheduleTempBasalBolus(
                    percent, durationInMinutes, profile, callback,
                    0.0, PumpTempBasalType.Percent
                )
            }
        }
        //        result.success = false;
        //        result.comment = MainApp.gs(R.string.pumperror);
        aapsLogger.info(LTag.DATATREATMENTS, "Settings temp basal percent: $result")
        return result
    }

    // fun setExtendedBolus(insulin: Double?, durationInMinutes: Int?): PumpEnactResult {
    //     val result = buildPumpEnactResult()
    //     result.success = false
    //     medtronicUtil.sendNotification(MedtronicNotificationType.PumpExtendedBolusNotEnabled, rh, rxBus)
    //     aapsLogger.debug("Setting extended bolus: $result")
    //     return result
    // }

    override fun cancelTempBasal(enforceNew: Boolean, callback: Callback?) {
        aapsLogger.info(LTag.PUMP, logPrefix + "cancelTempBasal - started")
        if (isPumpNotReachable) {
            setRefreshButtonEnabled(true)
            return
        }
        medtronicUtil.dismissNotification(MedtronicNotificationType.PumpUnreachable, rxBus)
        setRefreshButtonEnabled(false)
        val tbrCurrent = readTBR()
        if (tbrCurrent.durationMinutes > 0) {
            if (tbrCurrent.insulinRate > 0.0f && tbrCurrent.durationMinutes == 0) {
                aapsLogger.info(LTag.PUMP, logPrefix + "cancelTempBasal - TBR already canceled.")
                finishAction("TBR")

            }
        } else {
            aapsLogger.warn(LTag.PUMP, logPrefix + "cancelTempBasal - Could not read current TBR, canceling operation.")
            finishAction("TBR")
        }
        if (tempBasalMicrobolusOperations?.operations?.size ?: 0 > 0) {
            var first = tempBasalMicrobolusOperations?.operations?.first
            if ((first?.operationType == TempBasalMicroBolusPair.OperationType.REACTIVATE && !first.isCommandIssued) ||
                (first?.operationType == TempBasalMicroBolusPair.OperationType.SUSPEND && first.isCommandIssued) ||
                medLinkPumpStatus.pumpRunningState == PumpRunningState.Suspended
            ) {
                startPump(callback)
            }
            tempBasalMicrobolusOperations?.operations?.clear()
        }
        // val responseTask2 = medLinkService?.medtronicUIComm?.executeCommand(MedtronicCommandType.CancelTBR)
        // val response = responseTask2?.result as Boolean?
        // finishAction("TBR")
        // return if(tbrCurrent.){
        //     aapsLogger.info(LTag.PUMP, logPrefix + "cancelTempBasal - Cancel TBR failed.")
        //     startPump(callback)
        //     return
        // } else {
        //     aapsLogger.info(LTag.PUMP, logPrefix + "cancelTempBasal - Cancel TBR successful.")
        //
        //
        //     //cancelTBRWithTemporaryId()
        //
        //     return
        // }
    }

    override fun extendBasalTreatment(duration: Int, callback: Function1<PumpEnactResult, *>): PumpEnactResult {
        //TODO implement
        val result = PumpEnactResult(injector).success(true).enacted(true).comment(rh.gs(string.let_temp_basal_run))
        val reactivateOper = tempBasalMicrobolusOperations.operations.stream().filter { f: TempBasalMicroBolusPair ->
            f.operationType ==
                TempBasalMicroBolusPair.OperationType.REACTIVATE
        }.findFirst()
        if (reactivateOper.isPresent) {
            reactivateOper.get().setReleaseTime(duration)
            callback.invoke(result)
        }
        return result
    }

    // override fun cancelExtendedBolus(): PumpEnactResult {
    //     val result = PumpEnactResult(injector)
    //     result.success = false
    //     medtronicUtil.sendNotification(MedtronicNotificationType.No, rh, rxBus)
    //     aapsLogger.debug("Canceling extended bolus: $result")
    //     return result
    // }

    override fun manufacturer(): ManufacturerType {
        return ManufacturerType.Medtronic
    }

    override fun generateTempId(dataObject: Any): Long {
        val timestamp: Long = dataObject as Long
        return DateTimeUtil.toATechDate(timestamp)
    }

    override fun model(): PumpType {
        return pumpDescription.pumpType
    }

    //    @Override public PumpType getPumpType() {
    //        return pumpDescription.pumpType;
    //    }
    override fun serialNumber(): String {
        return medLinkPumpStatus.serialNumber
    }

    // override fun shortStatus(veryShort: Boolean): String {
    //     var ret = ""
    //     if (pumpStatusData.lastConnection != 0L) {
    //         val agoMsec = System.currentTimeMillis() - pumpStatusData.lastConnection
    //         val agoMin = (agoMsec / 60.0 / 1000.0).toInt()
    //         ret += "LastConn: $agoMin min agon"
    //     }
    //     if (pumpStatusData.lastBolusTime != null && pumpStatusData.lastBolusTime!!.time != 0L) {
    //         ret += """
    //             LastBolus: ${to2Decimal(pumpStatusData.lastBolusAmount!!)}U @${DateFormat.format("HH:mm", pumpStatusData.lastBolusTime)}
    //
    //             """.trimIndent()
    //     }
    //     val activeTemp: TemporaryBasal = activePlugin.getActiveTreatments().getRealTempBasalFromHistory(System.currentTimeMillis())
    //     if (activeTemp != null) {
    //         ret += """
    //             Temp: ${activeTemp.toStringFull().toString()}
    //
    //             """.trimIndent()
    //     }
    //     val activeExtendedBolus: ExtendedBolus = activePlugin.getActiveTreatments().getExtendedBolusFromHistory(
    //         System.currentTimeMillis()
    //     )
    //     if (activeExtendedBolus != null) {
    //         ret += """
    //             Extended: ${activeExtendedBolus.toString().toString()}
    //
    //             """.trimIndent()
    //     }
    //     // if (!veryShort) {
    //     // ret += "TDD: " + DecimalFormatter.to0Decimal(pumpStatus.dailyTotalUnits) + " / "
    //     // + pumpStatus.maxDailyTotalUnits + " Un";
    //     // }
    //     ret += """
    //         IOB: ${pumpStatusData.iob}U
    //
    //         """.trimIndent()
    //     ret += """
    //         Reserv: ${to0Decimal(pumpStatusData.reservoirLevel)}U
    //
    //         """.trimIndent()
    //     ret += """
    //         Batt: ${pumpStatusData.batteryVoltage}
    //
    //         """.trimIndent()
    //     return ret
    // }

    override fun getCustomActions(): List<CustomAction>? {
        if (customActions == null) {
            customActions = Arrays.asList(
                customActionClearBolusBlock
            )
        }
        return customActions
    }

    override fun executeCustomAction(customActionType: CustomActionType) {
        val mcat = customActionType as MedtronicCustomActionType
        when (mcat) {
            MedtronicCustomActionType.WakeUpAndTune               -> {
                if (medLinkService!!.verifyConfiguration()) {
                    serviceTaskExecutor.startTask(WakeAndTuneTask(injector))
                } else {
                    aapsLogger.debug("Medronic Pump plugin intent")
                    val i = Intent(context, ErrorHelperActivity::class.java)
                    i.putExtra("soundid", R.raw.boluserror)
                    i.putExtra("status", rh.gs(string.medtronic_error_operation_not_possible_no_configuration))
                    i.putExtra("title", rh.gs(string.medtronic_warning))
                    i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    context.startActivity(i)
                }
            }

            MedtronicCustomActionType.ClearBolusBlock             -> {
                busyTimestamps.clear()

                //                this.customActionClearBolusBlock.setEnabled(false);
                refreshCustomActionsList()
            }

            MedtronicCustomActionType.ResetRileyLinkConfiguration -> {
                serviceTaskExecutor.startTask(ResetRileyLinkConfigurationTask(injector))
            }

            else                                                  -> {}
        }
    }

    override fun executeCustomCommand(customCommand: CustomCommand): PumpEnactResult? {
        return null
    }

    override fun isUnreachableAlertTimeoutExceeded(alertTimeoutMilliseconds: Long): Boolean {
        return false
    }

    override fun setNeutralTempAtFullHour(): Boolean {
        return false
    }

    override fun canHandleDST(): Boolean {
        return true
    }

    fun comparePumpBasalProfile(profile: BasalProfile): PumpEnactResult {
        val validProfile = isProfileValid(profile)
        aapsLogger.info(LTag.PUMPBTCOMM, "valid profile $validProfile")
        if (validProfile != null && !validProfile.isEmpty()) {
            return PumpEnactResult(injector) //
                .success(false) //
                .enacted(false) //
                .comment(rh.gs(string.medtronic_cmd_set_profile_pattern_overflow, validProfile))
        }
        return if (this.profile == null) {

            //            this.setNewBasalProfile(profile);
            PumpEnactResult(injector) //
                .success(false) //
                .enacted(false) //
                .comment(rh.gs(string.medtronic_cmd_set_profile_pattern_overflow, profile.basalProfileToString()))
        } else if (convertProfileToMedtronicProfile(this.profile) != profile) {
            //            getAapsLogger().info(LTag.PUMPBTCOMM,profile.toString());
            aapsLogger.info(LTag.PUMPBTCOMM, this.profile.toString())
            Toast.makeText(
                context,
                rh.gs(info.nightscout.androidaps.plugins.pump.common.R.string.need_manual_profile_set, 40), Toast.LENGTH_LONG
            ).show()

            // showToastInUiThread(
            //     context, rxBus, rh.gs(info.nightscout.androidaps.plugins.pump.common.R.string.need_manual_profile_set, 40),
            //     Toast.LENGTH_LONG
            // )
            PumpEnactResult(injector) //
                .success(false) //
                .enacted(false) //
                .comment(
                    rh.gs(
                        string.medtronic_cmd_set_profile_pattern_overflow,
                        profile.basalProfileToString()
                    )
                )
        } else {
            PumpEnactResult(injector) //
                .success(true) //
                .enacted(true) //
                .comment(rh.gs(string.medtronic_cmd_set_profile_pattern_overflow))
        }
    }

    override fun setNewBasalProfile(profile: Profile): PumpEnactResult {
        aapsLogger.info(LTag.PUMP, logPrefix + "setNewBasalProfile")

        // this shouldn't be needed, but let's do check if profile setting we are setting is same as current one
        if (isThisProfileSet(profile)) {
            return PumpEnactResult(injector) //
                .success(true) //
                .enacted(false) //
                .comment(rh.gs(string.medtronic_cmd_basal_profile_not_set_is_same))
        }
        setRefreshButtonEnabled(false)
        if (isPumpNotReachable) {
            setRefreshButtonEnabled(true)
            aapsLogger.info(LTag.PUMP, "pump unreachable")
            return PumpEnactResult(injector) //
                .success(false) //
                .enacted(false) //
                .comment(rh.gs(string.medtronic_pump_status_pump_unreachable))
        }
        medtronicUtil.dismissNotification(MedtronicNotificationType.PumpUnreachable, rxBus)
        val basalProfile = convertProfileToMedtronicProfile(profile)
        val profileInvalid = isProfileValid(basalProfile)
        if (profileInvalid != null) {
            return PumpEnactResult(injector) //
                .success(false) //
                .enacted(false) //
                .comment(rh.gs(string.medtronic_cmd_set_profile_pattern_overflow, profileInvalid))
        }
        runOnUiThread(Runnable() {
            fun run() {
                Toast.makeText(context, rh.gs(string.medtronic_cmd_basal_profile_could_not_be_set, 40), Toast.LENGTH_LONG).show()
            }
        })
        //        MedtronicUITask responseTask = medLinkService.getMedtronicUIComm().executeCommand(MedLinkMedtronicCommandType.SetBasalProfileSTD,
        //                basalProfile);
        //
        //        Boolean response = (Boolean) responseTask.returnData;
        //
        //        getAapsLogger().info(LTag.PUMP, getLogPrefix() + "Basal Profile was set: " + response);
        //
        //        if (response) {
        return PumpEnactResult(injector).success(false).enacted(false)
        //        } else {
        //            return new PumpEnactResult(getInjector()).success(response).enacted(response) //
        //                    .comment(getRh().gs(R.string.medtronic_cmd_basal_profile_could_not_be_set));
        //        }
    }

    private fun isProfileValid(basalProfile: BasalProfile): String? {
        val stringBuilder = StringBuilder()
        if (medLinkPumpStatus.maxBasal == null) return null
        for (profileEntry in basalProfile.getEntries()) {
            if (profileEntry.rate > medLinkPumpStatus.maxBasal) {
                stringBuilder.append(profileEntry.startTime!!.toString("HH:mm"))
                stringBuilder.append("=")
                stringBuilder.append(profileEntry.rate)
            }
        }
        return if (stringBuilder.length == 0) null else stringBuilder.toString()
    }

    private fun convertProfileToMedtronicProfile(profile: Profile?): BasalProfile {
        val basalProfile = BasalProfile(aapsLogger)
        for (i in 0..23) {
            val rate = profile!!.getBasalTimeFromMidnight(i * 60 * 60)
            val v = pumpDescription.pumpType.determineCorrectBasalSize(rate)
            val basalEntry = BasalProfileEntry(v, i, 0)
            basalProfile.addEntry(basalEntry)
        }
        basalProfile.generateRawDataFromEntries()
        return basalProfile
    }

    private fun setNotReachable(isBolus: Boolean, success: Boolean): PumpEnactResult {
        setRefreshButtonEnabled(true)
        if (isBolus) {
            bolusDeliveryType = MedtronicPumpPluginInterface.BolusDeliveryType.Idle
        }
        return if (success) {
            PumpEnactResult(injector) //
                .success(true) //
                .enacted(false)
        } else {
            aapsLogger.info(LTag.PUMP, "pump unreachable")
            PumpEnactResult(injector) //
                .success(false) //
                .enacted(false) //
                .comment(rh.gs(string.medtronic_pump_status_pump_unreachable))
        }
    }

    private val customActionClearBolusBlock = CustomAction(
        string.medtronic_custom_action_clear_bolus_block, MedtronicCustomActionType.ClearBolusBlock, false
    )

    private fun setEnableCustomAction(customAction: MedtronicCustomActionType, isEnabled: Boolean) {
        if (customAction === MedtronicCustomActionType.ClearBolusBlock) {
            customActionClearBolusBlock.isEnabled = isEnabled
        } else if (customAction === MedtronicCustomActionType.ResetRileyLinkConfiguration) {
            //TODO see if medlink will need this resetconfig
            //            this.customActionResetRLConfig.setEnabled(isEnabled);
        }
        refreshCustomActionsList()
    }

    override fun deliverBolus(detailedBolusInfo: DetailedBolusInfo): PumpEnactResult {
        aapsLogger.info(LTag.PUMP, "MedtronicPumpPlugin::deliverBolus - " + MedtronicPumpPluginInterface.BolusDeliveryType.DeliveryPrepared)
        setRefreshButtonEnabled(false)
        if (detailedBolusInfo.insulin > medLinkPumpStatus.reservoirRemainingUnits) {
            return PumpEnactResult(injector) //
                .success(false) //
                .enacted(false) //
                .comment(
                    rh.gs(
                        string.medtronic_cmd_bolus_could_not_be_delivered_no_insulin,
                        medLinkPumpStatus.reservoirRemainingUnits,
                        detailedBolusInfo.insulin
                    )
                )
        }
        bolusDeliveryType = MedtronicPumpPluginInterface.BolusDeliveryType.DeliveryPrepared
        if (isPumpNotReachable) {
            aapsLogger.debug(LTag.PUMP, "MedtronicPumpPlugin::deliverBolus - Pump Unreachable.")
            return setNotReachable(true, false)
        }
        medtronicUtil.dismissNotification(MedtronicNotificationType.PumpUnreachable, rxBus)
        if (bolusDeliveryType == MedtronicPumpPluginInterface.BolusDeliveryType.CancelDelivery) {
            // LOG.debug("MedtronicPumpPlugin::deliverBolus - Delivery Canceled.");
            return setNotReachable(true, true)
        }

        // LOG.debug("MedtronicPumpPlugin::deliverBolus - Starting wait period.");
        val sleepTime = sp.getInt(MedtronicConst.Prefs.BolusDelay, 10) * 1000

        //        SystemClock.sleep(sleepTime);
        return if (bolusDeliveryType == MedtronicPumpPluginInterface.BolusDeliveryType.CancelDelivery) {
            // LOG.debug("MedtronicPumpPlugin::deliverBolus - Delivery Canceled, before wait period.");
            setNotReachable(true, true)
        } else try {
            bolusDeliveryType = MedtronicPumpPluginInterface.BolusDeliveryType.Delivering

            // LOG.debug("MedtronicPumpPlugin::deliverBolus - Start delivery");
            val response = AtomicReference(false)
            // val bolusTimesstamp = 0L
            val bolusCallback = BolusCallback(aapsLogger, detailedBolusInfo)
            val andThem: Function<Supplier<Stream<String>>, MedLinkStandardReturn<String>> = bolusCallback.andThen { f: MedLinkStandardReturn<BolusAnswer> ->
                val answer = f.functionResult
                if (answer.response == PumpResponses.UnknownAnswer) {
                    aapsLogger.info(LTag.PUMPBTCOMM, "need to check bolus")
                    //                    processDeliveredBolus(answer, detailedBolusInfo);
                    bolusHistoryCheck = true
                }
                MedLinkStandardReturn(f.answer, f.functionResult.answer)
            }
            val bolusCommand = if (detailedBolusInfo.bolusType == DetailedBolusInfo.BolusType.TBR) MedLinkCommandType.TBRBolus
            else if (detailedBolusInfo.bolusType == DetailedBolusInfo.BolusType.SMB) MedLinkCommandType.SMBBolus else
                MedLinkCommandType
                    .Bolus
            val msg = BolusMedLinkMessage(bolusCommand,
                                          detailedBolusInfo,
                                          andThem, buildBolusStatusMessage(detailedBolusInfo.copy()),
                                          btSleepTime,
                                          BleBolusCommand(aapsLogger, medLinkService!!.medLinkServiceData, this),
                                          (tempBasalMicrobolusOperations.shouldBeSuspended() ||
                                              tempBasalMicrobolusOperations.operations.stream().findFirst()
                                                  .map { f: TempBasalMicroBolusPair -> f.operationType == TempBasalMicroBolusPair.OperationType.REACTIVATE }
                                                  .orElse(false)
                                              || tempBasalMicrobolusOperations.absoluteRate == 0.0)
            )
            medLinkService!!.medtronicUIComm?.executeCommandCP(MedLinkPumpMessage<PumpDriverState, Any>(buildStartStopCommands(), btSleepTime), msg)
            setRefreshButtonEnabled(true)

            //            int count = 0;
            //            while ((!isPumpNotReachable() || bolusTimesstamp == 0l) && count < 15) {
            //                SystemClock.sleep(5000);
            //                count++;
            //            }
            //            // LOG.debug("MedtronicPumpPlugin::deliverBolus - Response: {}", response);
            if (response.get() && !isPumpNotReachable) {
                if (bolusDeliveryType == MedtronicPumpPluginInterface.BolusDeliveryType.CancelDelivery) {
                    // LOG.debug("MedtronicPumpPlugin::deliverBolus - Delivery Canceled after Bolus started.");
                    Thread {

                        // Looper.prepare();
                        // LOG.debug("MedtronicPumpPlugin::deliverBolus - Show dialog - before");
                        SystemClock.sleep(20000)
                        // LOG.debug("MedtronicPumpPlugin::deliverBolus - Show dialog. Context: "
                        // + MainApp.instance().getApplicationContext());
                        val i = Intent(context, ErrorHelperActivity::class.java)
                        i.putExtra("soundid", R.raw.boluserror)
                        i.putExtra("status", rh.gs(string.medtronic_cmd_cancel_bolus_not_supported))
                        i.putExtra("title", rh.gs(string.medtronic_warning))
                        i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                        context.startActivity(i)
                    }.start()
                }
                val now = System.currentTimeMillis()
                detailedBolusInfo.deliverAtTheLatest = now

                //                activePlugin.getActiveTreatments().addToHistoryTreatment(detailedBolusInfo, true);

                // we subtract insulin, exact amount will be visible with next remainingInsulin update.
                medLinkPumpStatus.reservoirRemainingUnits -= detailedBolusInfo.insulin
                incrementStatistics(if (detailedBolusInfo.bolusType == DetailedBolusInfo.BolusType.SMB) MedtronicConst.Statistics.SMBBoluses else MedtronicConst.Statistics.StandardBoluses)

                // calculate time for bolus and set driver to busy for that time
                val bolusTime = (detailedBolusInfo.insulin * 42.0).toInt()
                val time = now + bolusTime * 1000
                busyTimestamps.add(time)
                setEnableCustomAction(MedtronicCustomActionType.ClearBolusBlock, true)
                PumpEnactResult(injector).success(true) //
                    .enacted(true) //
                    .bolusDelivered(detailedBolusInfo.insulin) //
                    .carbsDelivered(detailedBolusInfo.carbs)
            } else {
                aapsLogger.info(LTag.PUMPBTCOMM, "bolus not delivered")
                PumpEnactResult(injector) //
                    .success(bolusDeliveryType == MedtronicPumpPluginInterface.BolusDeliveryType.CancelDelivery) //
                    .enacted(false) //
                    .comment(rh.gs(string.medtronic_cmd_bolus_could_not_be_delivered))
            }
        } finally {
            finishAction("Bolus")
            bolusDeliveryType = MedtronicPumpPluginInterface.BolusDeliveryType.Idle
        }

        // LOG.debug("MedtronicPumpPlugin::deliverBolus - End wait period. Start delivery");
        // LOG.debug("MedtronicPumpPlugin::deliverBolus - End wait period. Start delivery");
    }

    private fun buildStartStopCommands(): MutableList<CommandStructure<PumpDriverState, BleCommand>> {
        aapsLogger.info(LTag.DATABASE, "buildStartStopCommands")
        aapsLogger.info(LTag.DATABASE, currentPumpStatus.toString())
        val commands: MutableList<CommandStructure<PumpDriverState, BleCommand>> = ArrayList()
        val callback: Function1<PumpEnactResult, *> = { o: Any? -> null }
        val oper = if (tempBasalMicrobolusOperations.operations.isNotEmpty()) {
            tempBasalMicrobolusOperations.operations.first
        } else {
            null
        }

        if (currentPumpStatus === PumpRunningState.Suspended || !tempBasalMicrobolusOperations.shouldBeSuspended()) {
            commands.addAll(
                buildChangeStatusFunction(
                    oper,
                    callback, ChangeStatusCallback.OperationType.START
                )

            )
        }
        if (tempBasalMicrobolusOperations.shouldBeSuspended() && temporaryBasal != null && temporaryBasal?.rate!! < 100) {
            commands.addAll(
                buildChangeStatusFunction(
                    oper,
                    callback, ChangeStatusCallback.OperationType.STOP
                )
            )
        }

        return commands
    }

    private val currentPumpStatus: Any
        get() = if (!tempBasalMicrobolusOperations.operations.isEmpty()
        ) {
            val firstOperation = tempBasalMicrobolusOperations.operations.first
            if (firstOperation.operationType == TempBasalMicroBolusPair.OperationType.BOLUS ||
                firstOperation.operationType == TempBasalMicroBolusPair.OperationType.SUSPEND
            ) {
                PumpRunningState.Running
            } else {
                PumpRunningState.Suspended
            }
        } else {
            PumpRunningState.Running
        }

    private fun processDeliveredBolus(answer: BolusAnswer) {
        handleNewTreatmentData(of(JSONObject(answer.detailedBolusInfo.toJsonString())))
        // pumpSyncStorage.addBolusWithTempId(detailedBolusInfo, true, this)
        lastDetailedBolusInfo = null
        handleBolusDelivered(answer.detailedBolusInfo)
    }

    //     private fun bolusInProgress(detailedBolusInfo: DetailedBolusInfo, actionTime: Long) {
    //         aapsLogger.info(LTag.EVENTS, "bolus in progress")
    //         val t = Treatment()
    //         t.isSMB = detailedBolusInfo.bolusType == DetailedBolusInfo.BolusType.SMB
    //         t.isTBR = detailedBolusInfo.bolusType == DetailedBolusInfo.BolusType.TBR
    //         val bolusEvent = EventOverviewBolusProgress
    //         bolusEvent.t = t
    //         bolusEvent.status = rh.gs(R.string.bolusdelivering, 0.0, detailedBolusInfo.insulin)
    //         bolusEvent.percent = 0
    //         rxBus.send(bolusEvent)
    //
    // //        Function<Supplier<Stream<String>>, MedLinkStandardReturn> function = new BolusHistoryCallback();
    // //        MedLinkPumpMessage msg = new BolusStatusMedLinkMessage(
    // //                MedLinkCommandType.StopStartPump,function, medLinkServiceData,
    // //                getAapsLogger(), bolusingEvent);
    // //        medLinkService.getMedtronicUIComm().executeCommandCP(msg);
    //         val bolusTime = (detailedBolusInfo.insulin * 42.0).toInt()
    //         val bolusEndTime = actionTime + bolusTime * 1000L
    //         val bolusDelta = System.currentTimeMillis() - actionTime
    //         //        Thread bolusEventThread = new Thread() {
    // //            @Override public void run() {
    // //                super.run();
    //         while (bolusEndTime > System.currentTimeMillis() + 100) {
    //             val remaining = (bolusEndTime - System.currentTimeMillis()) / bolusDelta
    //             val bolusEvt = EventOverviewBolusProgress
    //             bolusEvt.t = t
    //             bolusEvt.percent = remaining.toInt()
    //             bolusEvt.status = rh.gs(R.string.medlink_delivered, remaining * detailedBolusInfo.insulin, detailedBolusInfo.insulin)
    //             rxBus.send(bolusEvt)
    //         }
    //         val bolusEvt = EventOverviewBolusProgress
    //         bolusEvt.percent = 100
    //         bolusEvt.status = rh.gs(R.string.medlink_delivered, detailedBolusInfo.insulin, detailedBolusInfo.insulin)
    //         rxBus.send(bolusEvt)
    //         SystemClock.sleep(200)
    //         scheduleNextRefresh(MedLinkMedtronicStatusRefreshType.PumpHistory, -5)
    //         //                getRxBus().send(new EventDismissBolusProgressIfRunning(new PumpEnactResult(getInjector()).
    // //                        success(true).enacted(true).bolusDelivered(detailedBolusInfo.insulin) //
    // //                        .carbsDelivered(detailedBolusInfo.carbs)));
    // //                getRxBus().send(new ());
    // //                getRxBus().send(new EventDismissBolusProgressIfRunning());
    // //            }
    // //        };
    // //        bolusEventThread.start();
    //     }

    override fun deliverBolus(
        detailedBolusInfo: DetailedBolusInfo,
        func: (PumpEnactResult) -> Unit,
    ) {
        val bolus = detailedBolusInfo.copy()
        aapsLogger.info(LTag.PUMP, "MedtronicPumpPlugin::deliverBolus - " + MedtronicPumpPluginInterface.BolusDeliveryType.DeliveryPrepared)
        aapsLogger.info(LTag.PUMP, detailedBolusInfo.toJsonString())

        setRefreshButtonEnabled(false)
        if (detailedBolusInfo.insulin > medLinkPumpStatus.reservoirRemainingUnits) {
            func.invoke(
                PumpEnactResult(injector) //
                    .success(false) //
                    .enacted(false) //
                    .comment(
                        rh.gs(
                            string.medtronic_cmd_bolus_could_not_be_delivered_no_insulin,
                            medLinkPumpStatus.reservoirRemainingUnits,
                            bolus.insulin
                        )
                    )
            )
            return
        }
        bolusDeliveryType = MedtronicPumpPluginInterface.BolusDeliveryType.DeliveryPrepared
        if (isPumpNotReachable) {
            aapsLogger.debug(LTag.PUMP, "MedtronicPumpPlugin::deliverBolus - Pump Unreachable.")
            func.invoke(setNotReachable(true, false))
        }
        medtronicUtil.dismissNotification(MedtronicNotificationType.PumpUnreachable, rxBus)
        if (bolusDeliveryType == MedtronicPumpPluginInterface.BolusDeliveryType.CancelDelivery) {
            // LOG.debug("MedtronicPumpPlugin::deliverBolus - Delivery Canceled.");
            func.invoke(setNotReachable(true, true))
        }

        try {
            bolusDeliveryType = MedtronicPumpPluginInterface.BolusDeliveryType.Delivering

            val resultFunc = { f: MedLinkStandardReturn<BolusAnswer> ->

                func.invoke(
                    PumpEnactResult(injector) //
                        .success(bolusDeliveryType == MedtronicPumpPluginInterface.BolusDeliveryType.CancelDelivery) //
                        .enacted(false) //
                        .comment(rh.gs(string.medtronic_cmd_bolus_could_not_be_delivered))
                )
                val i = Intent(context, ErrorHelperActivity::class.java)
                i.putExtra("soundid", R.raw.boluserror)
                i.putExtra("status", f.answer.get().collect(Collectors.joining()))
                i.putExtra("title", rh.gs(string.medtronic_cmd_bolus_could_not_be_delivered))
                i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                context.startActivity(i)

            }
            val bolusCallback = BolusCallback(aapsLogger, bolus)
            val andThen: Function<Supplier<Stream<String>>, MedLinkStandardReturn<String>> = bolusCallback.andThen { f: MedLinkStandardReturn<BolusAnswer> ->
                // val answer = Supplier { f.answer }
                aapsLogger.info(LTag.PUMPBTCOMM, f.functionResult.response.name)
                if (PumpResponses.BolusDelivered == f.functionResult.response) {
                    bolusHistoryCheck = false
                    bolusDeliveryType = MedtronicPumpPluginInterface.BolusDeliveryType.Idle
                    processDeliveredBolus(f.functionResult)
                    func.invoke(
                        PumpEnactResult(injector).success(true) //
                            .enacted(true) //
                            .bolusDelivered(bolus.insulin) //
                            .carbsDelivered(bolus.carbs)
                    )
                } else if (PumpResponses.UnknownAnswer == f.functionResult.response) {
                    val bolusAnswer = f.functionResult
                    bolusHistoryCheck = true
                    if (bolusAnswer.delivered == bolus.insulin) {
                        handleNewTreatmentData(of(JSONObject(bolusAnswer.detailedBolusInfo.toJsonString())))
                    } else {
                        //TODO postpone this message to later,  call status logic before to guarantee that the bolus has not been delivered
                        aapsLogger.info(LTag.PUMPBTCOMM, "pump is not deliverying")
                        if (f.functionResult.delivered > 0.0) {
                            processDeliveredBolus(f.functionResult)
                        } else {

                            readBolusData(detailedBolusInfo)
                        }
                    }
                } else if (PumpResponses.DeliveringBolus == f.functionResult.response) {
                    bolusHistoryCheck = true
                    lastDetailedBolusInfo = bolus
                    aapsLogger.info(LTag.PUMPBTCOMM, "and themmmm")
                }
                if (f.functionResult.answer != null) {
                    MedLinkStandardReturn(f.answer, f.functionResult.answer)
                } else {
                    MedLinkStandardReturn(f.answer, "") //TODO check why is happening a nullpointer here
                }
            }
            if (pumpStatusData.pumpRunningState == PumpRunningState.Suspended &&
                bolus.insulin > 0.0
            ) {
                aapsLogger.info(LTag.EVENTS, "bolus starting pump")
                startPump(object : Callback() {
                    override fun run() {
                        aapsLogger.info(LTag.EVENTS, "starting pump for bolus")
                    }
                })
            }
            val bolusCommand =
                if (bolus.bolusType == DetailedBolusInfo.BolusType.TBR) MedLinkCommandType.TBRBolus else if (bolus.bolusType == DetailedBolusInfo.BolusType.SMB) MedLinkCommandType.SMBBolus else MedLinkCommandType.Bolus
            var bolusStatusMessage: MedLinkPumpMessage<String, Any>? = null
            if (bolus.insulin > 0.2) {
                bolusStatusMessage = buildBolusStatusMessage(bolus)
            }

            val msg = BolusMedLinkMessage(bolusCommand, bolus,
                                          andThen, bolusStatusMessage,
                                          btSleepTime,
                                          BleBolusCommand(aapsLogger, medLinkService!!.medLinkServiceData, this),
                                          (tempBasalMicrobolusOperations.shouldBeSuspended() ||
                                              tempBasalMicrobolusOperations.operations.stream().findFirst()
                                                  .map { f: TempBasalMicroBolusPair -> f.operationType == TempBasalMicroBolusPair.OperationType.REACTIVATE }
                                                  .orElse(false)
                                              || tempBasalMicrobolusOperations.absoluteRate == 0.0)
            )
            val startStopMsg = buildStartStopCommands()
            if (startStopMsg.isNotEmpty()) {
                medLinkService!!.medtronicUIComm?.executeCommandCP(MedLinkPumpMessage<PumpDriverState, Any>(startStopMsg, btSleepTime), msg)
            } else {
                medLinkService!!.medtronicUIComm?.executeCommandCP(msg)
            }
            setRefreshButtonEnabled(true)

            //            int count = 0;
            //            while ((!isPumpNotReachable() || bolusTimesstamp == 0l) && count < 15) {
            //                SystemClock.sleep(5000);
            //                count++;
            //            }
            // LOG.debug("MedtronicPumpPlugin::deliverBolus - Response: {}", response);
            if (!isPumpNotReachable) {
                if (bolusDeliveryType == MedtronicPumpPluginInterface.BolusDeliveryType.CancelDelivery) {
                    // LOG.debug("MedtronicPumpPlugin::deliverBolus - Delivery Canceled after Bolus started.");
                    Thread {

                        // Looper.prepare();
                        // LOG.debug("MedtronicPumpPlugin::deliverBolus - Show dialog - before");
                        SystemClock.sleep(2000)
                        // LOG.debug("MedtronicPumpPlugin::deliverBolus - Show dialog. Context: "
                        // + MainApp.instance().getApplicationContext());
                        val i = Intent(context, ErrorHelperActivity::class.java)
                        i.putExtra("soundid", R.raw.boluserror)
                        i.putExtra("status", rh.gs(string.medtronic_cmd_cancel_bolus_not_supported))
                        i.putExtra("title", rh.gs(string.medtronic_warning))
                        i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                        context.startActivity(i)
                    }.start()
                }
                val now = System.currentTimeMillis()
                bolus.deliverAtTheLatest = now
                // detailedBolusInfo.deliverAt = now // not sure about that one

                //                activePlugin.getActiveTreatments().addToHistoryTreatment(detailedBolusInfo, true);

                // we subtract insulin, exact amount will be visible with next remainingInsulin update.
                medLinkPumpStatus.reservoirRemainingUnits -= bolus.insulin
                incrementStatistics(if (bolus.bolusType == DetailedBolusInfo.BolusType.SMB) MedtronicConst.Statistics.SMBBoluses else MedtronicConst.Statistics.StandardBoluses)

                // calculate time for bolus and set driver to busy for that time
                val bolusTime = (bolus.insulin * 42.0).toInt()
                val time = now + bolusTime * 1000L
                busyTimestamps.add(time)
                //                setEnableCustomAction(MedtronicCustomActionType.ClearBolusBlock, true);
            }
        } finally {
            finishAction("Bolus")
            bolusDeliveryType = MedtronicPumpPluginInterface.BolusDeliveryType.Idle
        }
    }

    private fun buildBolusStatusMessage(bolusInfo: DetailedBolusInfo): BolusStatusMedLinkMessage<String> {
        val bolusCallback = BolusProgressCallback(
            medLinkPumpStatus,
            rh, rxBus, aapsLogger,
            this, bolusInfo
        )
        val bolusStatusCommand = BleBolusStatusCommand(aapsLogger, medLinkService!!.medLinkServiceData, this)
        return BolusStatusMedLinkMessage<String>(
            bolusCallback, btSleepTime,
            bolusStatusCommand
        )
    }

    private fun readBolusData(detailedBolusInfo: DetailedBolusInfo) {
        aapsLogger.info(LTag.PUMPBTCOMM, "get full bolus data")
        lastBolusHistoryRead = System.currentTimeMillis()
        val func = BolusDeliverCallback(
            pumpStatusData, this, aapsLogger,
            detailedBolusInfo
        )
        assert(medLinkService != null)
        val msg = BolusStatusMedLinkMessage(
            func,
            btSleepTime,
            BleBolusStatusCommand(aapsLogger, medLinkService!!.medLinkServiceData, this)
        )
        medLinkService!!.medtronicUIComm?.executeCommandCP(msg)
    }

    override fun triggerUIChange() {
        rxBus.send(EventMedtronicPumpValuesChanged())
    }

    override fun timezoneOrDSTChanged(timeChangeType: TimeChangeType) {
        aapsLogger.warn(LTag.PUMP, logPrefix + "Time or TimeZone changed. ")
        hasTimeDateOrTimeZoneChanged = true
    }

    override fun getPumpInfo(): RileyLinkPumpInfo? {
        val frequency = rh.gs(if (medLinkPumpStatus.pumpFrequency == "medtronic_pump_frequency_us_ca") string.medtronic_pump_frequency_us_ca else string.medtronic_pump_frequency_worldwide)
        val model = if (!medtronicUtil.isModelSet) "???" else "Medtronic " + medLinkPumpStatus.medtronicDeviceType.pumpModel
        val serialNumber = medLinkPumpStatus.serialNumber
        return RileyLinkPumpInfo(frequency, model, serialNumber)
    }

    // override fun getLastConnectionTimeMillis(): Long {
    //     return pumpStatusData.lastConnection
    // }

    override fun setLastCommunicationToNow() {
        medLinkPumpStatus.setLastCommunicationToNow()
    }

    fun resetStatusState() {
        firstRun = true
        isRefresh = true
    }

    // private fun buildIntentSensValues(vararg bgs: InMemoryGlucoseValue): Intent {
    //     val intent = Intent()
    //     intent.putExtra("sensorType", "Enlite")
    //     val glucoseValues = Bundle()
    //     val fingerValues = Bundle()
    //     var gvPosition = 0
    //     var meterPosition = 0
    //     for (bg in bgs) {
    //         if (bg ==GlucoseValue.SourceSensor.UNKNOW) {
    //             aapsLogger.info(LTag.BGSOURCE, "User bg source")
    //             val bgBundle = Bundle()
    //             bgBundle.putDouble("meterValue", bg.value)
    //             bgBundle.putLong("timestamp", bg.timestamp)
    //             fingerValues.putBundle("" + meterPosition, bgBundle)
    //             meterPosition++
    //         } else {
    //             val bgBundle = Bundle()
    //             bgBundle.putDouble("value", bg.value)
    //             bgBundle.putLong("date", bg.timestamp)
    //             bgBundle.putString("direction", bg.get)
    //             //            bgBundle.putString("raw", bg.raw);
    //             glucoseValues.putBundle("" + gvPosition, bgBundle)
    //             gvPosition++
    //         }
    //     }
    //     intent.putExtra("glucoseValues", glucoseValues)
    //     intent.putExtra("meters", fingerValues)
    //     intent.putExtra("isigValues", Bundle())
    //     return intent
    // }

    private fun buildIntentSensValues(vararg sensorDataReadings: BgSync.BgHistory): Bundle {
        val baseBundle = Bundle()
        baseBundle.putString("sensorType", "Enlite")
        // val glucoseValues = Bundle()
        val fingerValues = Bundle()
        val isigValues = Bundle()
        // var gvPosition = 0
        var meterPosition = 0
        var isigPosition = 0
        sensorDataReadings.forEach { value ->

            aapsLogger.info(LTag.BGSOURCE, "User bg source")
            value.bgCalibration.forEach {
                val bgBundle = Bundle()
                bgBundle.putDouble("meterValue", it.value)
                bgBundle.putLong("timestamp", it.timestamp)
                fingerValues.putBundle("" + meterPosition, bgBundle)
                meterPosition++
            }
            value.bgValue.forEach {
                //     val bgBundle = Bundle()
                //     bgBundle.putDouble("value", it.value)
                //     bgBundle.putLong("date", it.timestamp)
                //     // bgBundle.putString("direction", sens.trendArrow.text)
                //     //                bgBundle.putString("raw", bg.raw);
                //     glucoseValues.putBundle("" + gvPosition, bgBundle)
                //     gvPosition++
                // }
                val sensBundle = Bundle()
                sensBundle.putDouble("value", it.value)
                sensBundle.putLong("timestamp", it.timestamp)
                // sensBundle.putString("direction", sens.trendArrow.text)
                it.calibrationFactor?.let { it1 -> sensBundle.putDouble("calibration_factor", it1) }
                it.sensorUptime?.let { it1 -> sensBundle.putInt("sensor_uptime", it1) }
                it.isig?.let { it1 -> sensBundle.putDouble("isig", it1) }
                // sensBundle.putDouble("delta", it.de)
                isigValues.putBundle("" + isigPosition, sensBundle)
                aapsLogger.info(LTag.BGSOURCE, sensBundle.toString())
                isigPosition++
            }
        }
        baseBundle.putBundle("glucoseValues", isigValues)
        baseBundle.putBundle("meters", fingerValues)
        // intent.putExtra("isigValues", isigValues)
        return baseBundle
    }

    //    private Bundle buildIntentSensValues(SensorDataReading... sens) {
    //        Bundle isigValues = new Bundle();
    //
    //        int gvPosition = 0;
    //        int meterPosition = 0;
    //        for (SensorDataReading bg : sens) {
    //            Bundle bgBundle = new Bundle();
    //            bgBundle.putDouble("value", bg.value);
    //            bgBundle.putLong("date", bg.date);
    //            bgBundle.putString("direction", bg.direction);
    //            bgBundle.putDouble("calibrationFactor", bg.calibrationFactor);
    //            bgBundle.putInt("sensorUptime", bg.sensorUptime);
    //            bgBundle.putDouble("isig", bg.isig);
    //            bgBundle.putDouble("delta", bg.deltaSinceLastBG);
    //            isigValues.putBundle("" + gvPosition, bgBundle);
    //            gvPosition++;
    //        }
    //        return isigValues;
    //    }
    fun handleNewCareportalEvent(events: Supplier<Stream<JSONObject>>) {
        events.get().forEach { e: JSONObject? ->
            pumpSync.insertTherapyEventIfNewWithTimestamp(
                e!!.getLong("mills"),
                DetailedBolusInfo.EventType.valueOf(e.getString("eventType")),
                pumpSerial = medLinkServiceData.pumpID,
                pumpType = pumpType
            )
        }
    }

    override fun handleNewTreatmentData(bolusInfo: Stream<JSONObject>, history: Boolean) {
        bolusHistoryCheck = false
        var lastProcessedBolusTimestamp = 0L
        var minBolusTimestamp = 0L
        val state = pumpSync.expectedPumpState()
        var count = 0
        bolusInfo.forEachOrdered { bolusJson: JSONObject ->
            count += 1
            val bInfo = DetailedBolusInfo.fromJsonString(bolusJson.toString())
            aapsLogger.info(LTag.PUMPBTCOMM, lastDetailedBolusInfo?.toJsonString() ?: "")
            aapsLogger.info(LTag.PUMPBTCOMM, bInfo.toJsonString())
            if (minBolusTimestamp < (bInfo.bolusTimestamp ?: bInfo.timestamp)) {
                minBolusTimestamp = bInfo.bolusTimestamp ?: bInfo.timestamp
            }
            if (lastDetailedBolusInfo != null && bInfo.insulin == lastDetailedBolusInfo!!.insulin) {
                if (bInfo.bolusType == DetailedBolusInfo.BolusType.NORMAL && lastDetailedBolusInfo?.bolusType != DetailedBolusInfo.BolusType.NORMAL) {
                    bInfo.bolusType = lastDetailedBolusInfo!!.bolusType
                }
                lastDetailedBolusInfo = null
            }
            var mod = (bInfo.bolusTimestamp ?: bInfo.timestamp).mod(60000)
            if (lastProcessedBolusTimestamp == 0L && state.bolus != null && state.bolus?.timestamp == bInfo.bolusTimestamp) {
                remainingBolus = bInfo
                readBolusHistory()
            } else if (bInfo.carbs != 0.0) {
                pumpSyncStorage.addBolusWithTempId(bInfo, true, this)
            } else if (bInfo.insulin > 0) {
                if (mod != 0 && lastProcessedBolusTimestamp == (bInfo.bolusTimestamp?.minus(mod) ?: bInfo.timestamp.mod(mod))) {
                    mod -= 20000
                    if (::remainingBolus.isInitialized && remainingBolus.bolusTimestamp == bInfo.bolusTimestamp?.minus(mod) && bInfo.insulin == remainingBolus.insulin) {
                        bInfo.bolusType = remainingBolus.bolusType
                        remainingBolus = DetailedBolusInfo()
                    }
                }
                lastProcessedBolusTimestamp = bInfo.bolusTimestamp?.minus(mod) ?: bInfo.timestamp
                if(lastProcessedBolusTimestamp < System.currentTimeMillis() + 120000) {
                    pumpSyncStorage.pumpSync.syncBolusWithTempId(
                        lastProcessedBolusTimestamp, bInfo.insulin,
                        generateTempId(bInfo.timestamp),
                        bInfo.bolusType, bInfo.bolusPumpId,
                        this.pumpType, serialNumber()
                    )
                }
            }
        }
        if (state.bolus != null && minBolusTimestamp > state.bolus?.timestamp!! && count > 1) {
            aapsLogger.info(LTag.PUMPBTCOMM, "previous read?")
            aapsLogger.info(LTag.PUMPBTCOMM, "" + state.bolus?.timestamp)
            aapsLogger.info(LTag.PUMPBTCOMM, "" + minBolusTimestamp)
            readBolusHistory(true)
        }
    }

    override fun getBatteryType(): String {
        return medLinkPumpStatus.batteryType.toString()
    }

    private fun handleNewEvent() {
        medtronicUtil.dismissNotification(MedtronicNotificationType.PumpUnreachable, rxBus)
        aapsLogger.info(LTag.EVENTS, " new event ")
        aapsLogger.info(LTag.EVENTS, "" + isInitialized)
        aapsLogger.info(LTag.EVENTS, "" + (medLinkPumpStatus.lastBolusTime?.time ?: ""))
        aapsLogger.info(LTag.EVENTS, "" + pumpTimeDelta)
        // val pumpState = pumpSyncStorage.pumpSync.expectedPumpState()

        if (isInitialized && medLinkPumpStatus.lastBolusTime != null) {
            //TODO possible removal of this first sentence
            // if (pumpState.bolus?.timestamp != medLinkPumpStatus.lastBolusTime?.time
            //     || sp.getBoolean(string.medlink_key_force_bolus_history_read, false) || bolusHistoryCheck
            // ) {
            //     aapsLogger.info(LTag.PUMPBTCOMM, "read bolus history")
            //     readBolusHistory()
            // }
            // if (lastDetailedBolusInfo != null) {
            //     aapsLogger.info(LTag.PUMPBTCOMM, "lastdetailedbolusinfo")
            //     lastDetailedBolusInfo!!.deliverAtTheLatest = pumpStatusData.lastBolusTime!!.time
            //     lastDetailedBolusInfo!!.bolusTimestamp = pumpStatusData.lastBolusTime!!.time
            //     handleNewTreatmentData(of(JSONObject(lastDetailedBolusInfo!!.toJsonString())))
            //     lastDetailedBolusInfo = null
            //
            // }
        }
    }

    fun calibrate(calibrationInfo: Double) {

        val medLinkCalibrationCallback = MedLinkCalibrationArgCallback(
            aapsLogger,
            this,
            medtronicUtil,
            rh,
            rxBus
        )

        val bleCommand = BleCalibrateCommand(aapsLogger, medLinkServiceData, this)
        val calibrationCallback = MedLinkCalibrationCallback(
            aapsLogger,
            this, medtronicUtil, rh, rxBus
        )
        val msg = CalibrateMedLinkMessage.invoke(
            calibrationInfo,
            calibrationCallback,
            medLinkCalibrationCallback,
            btSleepTime = 30000,
            bleCommand,
            currentPumpStatus === PumpRunningState.Suspended
        )

        aapsLogger.info(LTag.DATABASE, msg.toString())
        val startStopMsg = buildStartStopCommands()
        if (startStopMsg.isNotEmpty()) {
            medLinkService!!.medtronicUIComm?.executeCommandCP(MedLinkPumpMessage<PumpDriverState, Any>(startStopMsg, btSleepTime), msg)
        } else {
            medLinkService!!.medtronicUIComm?.executeCommandCP(msg)
        }
    }

    private fun handleCalibrationData() {
        lastCalibration = pumpStatusData.nextCalibration
        if (lastCalibration != null) {
            val firstAlarmMinutes = sp.getString(rh.gs(string.med_link_calibration_first_alarm), "120").toLong()
            val secondAlarmMinutes = sp.getString(rh.gs(string.med_link_calibration_second_alarm), "120").toLong()

            when {
                !hasSecondAlert && pumpStatusData.nextCalibration.minus(secondAlarmMinutes, ChronoUnit.MINUTES).isBefore(ZonedDateTime.now()) -> {
                    medtronicUtil.sendNotification(
                        MedtronicNotificationType.SecondCalibrationAlarm, rh, rxBus,
                        pumpStatusData.nextCalibration.format(DateTimeFormatter.ISO_LOCAL_TIME)
                    )
                    hasSecondAlert = true
                }

                !hasFirstAlert && pumpStatusData.nextCalibration.minus(firstAlarmMinutes, ChronoUnit.MINUTES).isBefore(ZonedDateTime.now())   -> {
                    medtronicUtil.sendNotification(
                        MedtronicNotificationType.FirstCalibrationAlarm, rh, rxBus,
                        pumpStatusData.nextCalibration.format(DateTimeFormatter.ISO_LOCAL_TIME)
                    )
                    hasFirstAlert = true
                }

                lastCalibration?.isBefore(pumpStatusData.nextCalibration) == true                                                             -> {
                    medtronicUtil.dismissNotification(MedtronicNotificationType.SecondCalibrationAlarm, rxBus)
                    medtronicUtil.dismissNotification(MedtronicNotificationType.FirstCalibrationAlarm, rxBus)
                    medtronicUtil.dismissNotification(MedtronicNotificationType.CalibrationSuccess, rxBus)
                    hasFirstAlert = false
                    hasSecondAlert = false
                }
            }
        }
    }

    var lastReservoirLevel = -1.0
    private fun handleDailyDoseUpdate() {
        if (lastReservoirLevel >= 0.0 && lastReservoirLevel - pumpStatusData.reservoirRemainingUnits > 0.3) {
            readBolusHistory()
        }
        lastReservoirLevel = pumpStatusData.reservoirRemainingUnits

        //        pumpSync.
        //        pumpSync.createOrUpdateTotalDailyDose()
    }

    var previousSensorUpTime: Int = -1

    fun sensorReInit() {
        if (sp.getBoolean(R.bool.key_medlink_handle_sensor_change_event, false)) {
            EnliteInterval.clear()
            pumpSync.insertTherapyEventIfNewWithTimestamp(
                pumpStatusData.lastBGTimestamp,
                DetailedBolusInfo.EventType.SENSOR_CHANGE,
                pumpSerial = medLinkServiceData.pumpID,
                pumpType = pumpType
            )
        }
    }

    fun handleNewSensorData(sens: BgSync.BgHistory) {

        if (sens.bgValue.size == 1) {
            if (sens.bgValue.first().sensorUptime != null) {
                if (previousSensorUpTime > sens.bgValue.first().sensorUptime!!) {
                    sensorReInit()
                }
                previousSensorUpTime = sens.bgValue.first().sensorUptime!!
            }
        }
        handleNewPumpData()
        if (sens.bgValue.size == 1 &&
            medLinkPumpStatus.needToGetBGHistory() && isInitialized()
        ) {
            missedBGs++
            if (firstMissedBGTimestamp == 0L && isInitialized()) {
                firstMissedBGTimestamp = lastBGHistoryRead
            }
        } else {
            aapsLogger.info(LTag.PUMPBTCOMM, "" + isInitialized)
            aapsLogger.info(LTag.PUMPBTCOMM, "" + isInitialized())
            // medLinkServiceData.
            pumpSync.expectedPumpState()
            if (isInitialized() && sens.bgValue.size > 1 &&
                sens.bgValue.map { it.timestamp }.min() > firstMissedBGTimestamp &&
                System.currentTimeMillis() - lastPreviousHistory > 1800000 ||
                System.currentTimeMillis() - lastPreviousHistory > 6000000
            ) {
                previousBGHistory()
            }
            missedBGs = 0
            firstMissedBGTimestamp = 0L
        }
        val intent = buildIntentSensValues(sens)

        bgSync.syncBgWithTempId(intent)
        late1Min = if (sens.bgValue.isNotEmpty() && sens.bgValue.first().value != 0.0) {
            readPumpBGHistory(false)
            false
        } else {
            true
        }
    }

    fun handleNewPumpData() {
        medtronicUtil.dismissNotification(MedtronicNotificationType.PumpUnreachable, rxBus)
        handleBatteryData()
        //        if(sens.length>0) {
        // if (lastDetailedBolusInfo != null) {
        //     handleBolusDelivered(lastDetailedBolusInfo)
        // }

        handleDailyDoseUpdate()
        handleProfile()
        handleBolusAlarm()
        handleCalibrationData()
        if (temporaryBasal?.rate == 0.0 && temporaryBasal?.duration!! > 0 && pumpStatusData.pumpRunningState == PumpRunningState.Running) {
            stopPump(object : Callback() {
                override fun run() {
                    aapsLogger.info(LTag.PUMP, "Stopping unstopped pump")
                }
            })
        }
        handleNewEvent()
    }

    private fun handleProfile() {
        if (basalProfile != null) {
            val rate = "%.3f".format(
                basalProfile?.getEntryForTime(
                    Instant(medLinkPumpStatus.lastDataTime)
                )?.rate ?: 0.0
            ).replace(",", ".").toDouble()
            if (lastProfileRead > 0 && System.currentTimeMillis() - lastProfileRead > 600000 && basalProfile != null && rate != medLinkPumpStatus
                    .currentBasal && medLinkPumpStatus.pumpRunningState == PumpRunningState.Running
            ) {
                aapsLogger.info(LTag.PUMPBTCOMM, "reading profile")
                aapsLogger.info(LTag.PUMPBTCOMM, "" + lastProfileRead)
                aapsLogger.info(LTag.PUMPBTCOMM, "" + rate)
                aapsLogger.info(LTag.PUMPBTCOMM, "" + medLinkPumpStatus
                    .currentBasal)
                basalProfile?.toString()?.let { aapsLogger.info(LTag.PUMPBTCOMM, it) }

                readPumpProfile()
            }
        }
    }

    private fun handleBolusAlarm() {
        if (!medLinkPumpStatus.bgAlarmOn) {
            isClosedLoopAllowed(Constraint(false))
        } else {
            isClosedLoopAllowed(Constraint(true))
        }
    }

    override fun handleBolusDelivered(lastBolusInfo: DetailedBolusInfo?) {
        if (bolusHistoryCheck) {
            val state = pumpSync.expectedPumpState()
            if (lastBolusInfo != null &&
                lastBolusInfo.insulin == medLinkPumpStatus.lastBolusAmount &&
                medLinkPumpStatus.lastBolusTime != null &&
                state.bolus != null &&
                medLinkPumpStatus.lastBolusTime!!.time > state.bolus?.timestamp!!
            ) {
                aapsLogger.info(LTag.PUMPBTCOMM,"${medLinkPumpStatus.lastBolusTime!!.time} & ${lastBolusInfo.bolusTimestamp}")
                lastBolusInfo.bolusTimestamp = medLinkPumpStatus.lastBolusTime!!.time
                handleNewTreatmentData(of(JSONObject(lastBolusInfo.toJsonString())))
                bolusHistoryCheck = false
            } else {
                aapsLogger.info(LTag.PUMPBTCOMM, "bolus not delivered")
                aapsLogger.info(LTag.PUMPBTCOMM, lastBolusInfo.toString())
                aapsLogger.info(LTag.PUMPBTCOMM, "" + medLinkPumpStatus.lastBolusAmount)
                val i = Intent(context, ErrorHelperActivity::class.java)
                i.putExtra("soundid", R.raw.boluserror)
                if (result != null) {
                    i.putExtra("status", result!!.comment)
                }
                i.putExtra("title", rh.gs(string.medtronic_cmd_bolus_could_not_be_delivered))
                i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                context.startActivity(i)
            }
        }
    }

    private fun handleBatteryData() {
        val currentBatRead = medLinkPumpStatus.lastDataTime
        val currentLevel = receiverStatusStore.batteryLevel
        if (lastBatteryLevel == 0) {
            lastBatteryLevel = currentLevel
        }
        if ((currentLevel - batteryDelta * 5 <= minimumBatteryLevel ||
                pumpStatusData.batteryRemaining <= minimumBatteryLevel) &&
            medLinkPumpStatus.pumpRunningState === PumpRunningState.Suspended
        ) {
            clearTempBasal()

        }
        if (batteryLastRead == 0L) {
            batteryLastRead = currentBatRead
        } else {

            val batDelta = (currentBatRead - batteryLastRead) / 60000
            var delta = 0.0
            if (batDelta > 0L) {
                delta = ((currentLevel - lastBatteryLevel) / batDelta).toDouble()
            }
            if (delta > batteryDelta) {
                batteryDelta = delta
            }
        }
        if (pumpStatusData.isBatteryChanged) {
            readBolusHistory()
        }
    }

    override val temporaryBasal: PumpSync.PumpState.TemporaryBasal?
        get() {
            if (!tempBasalMicrobolusOperations.operations.isEmpty()) {

                // tempBasal.date(tempBasalMicrobolusOperations.operations.first.releaseTime.toDate().time)
                // tempBasal.duration(
                //     tempBasalMicrobolusOperations.durationInMinutes
                // )
                // tempBasal.desiredRate = tempBasalMicrobolusOperations.absoluteRate
                // if (tempBasalMicrobolusOperations.absoluteRate == 0.0) {
                //     tempBasal.absolute(0.0)
                // } else {
                //     tempBasal.absolute(baseBasalRate)
                // }
                return PumpSync.PumpState.TemporaryBasal(
                    timestamp = tempBasalMicrobolusOperations.operations.first.releaseTime.toDate().time,
                    duration = tempBasalMicrobolusOperations.durationInMinutes * 60 * 1000L,
                    isAbsolute = false,
                    rate = baseBasalRate,
                    type = TemporaryBasalType.NORMAL,
                    desiredRate = tempBasalMicrobolusOperations.absoluteRate,
                    id = 0L,
                    pumpId = 0L
                )
            }
            return null
        }

    override fun nextScheduledCommand(): String? {
        val nextCommandStream: Stream<MutableMap.MutableEntry<MedLinkMedtronicStatusRefreshType?, Long?>>? =
            statusRefreshMap.entries.stream().sorted { f1: Map.Entry<MedLinkMedtronicStatusRefreshType?, Long?>, f2: Map.Entry<MedLinkMedtronicStatusRefreshType?, Long?> ->
                f1.value!!.compareTo(
                    f2.value!!
                )
            }
        val nextCommand = nextCommandStream?.findFirst()
        var result = ""
        if (nextCommand?.isPresent == true) {
            val key = nextCommand.get().key
            val commandType = key!!.getCommandType(medtronicUtil.medtronicPumpModel)
            if (commandType != null) {
                result = commandType.commandDescription
                result = result + " " + LocalDateTime(nextCommand.get().value).toString("HH:mm")
            } else {
                result = key.name
                result = result + " " + LocalDateTime(nextCommand.get().value).toString("HH:mm")
            }
        }
        return result
    }

    override fun getBatteryInfoConfig(): String? {
        return sp.getString(rh.gs(string.key_medlink_battery_info), "Age")
    }

    override fun getJSONStatus(profile: Profile, profileName: String, version: String): JSONObject {
        val result = super.getJSONStatus(profile, profileName, version)
        val battery = JSONObject()
        try {
            if (sp.getString(string.key_medlink_battery_info, "Age") !== "Age") {
                battery.put("voltage", pumpStatusData.batteryVoltage)
                battery.put("percent", batteryLevel)

            } else {
                val dto = BatteryStatusDTO()
                dto.voltage = pumpStatusData.batteryVoltage
                dto.batteryStatusType = BatteryStatusDTO.BatteryStatusType.Unknown
                val type = BatteryType.valueOf(sp.getString(string.key_medtronic_battery_type, BatteryType.None.name))
                pumpStatusData.deviceBatteryRemaining = dto.getCalculatedPercent(type)
                battery.put("percent", pumpStatusData.batteryRemaining)
            }
            result.put("battery", battery)
            result.put("clock", dateUtil.toISOString(pumpStatusData.lastDataTime))
        } catch (e: JSONException) {
            e.printStackTrace()
        }
        return result
    }

    fun glucoseUnit(): BgSync.GlucoseUnit {
        return if (sp.getString(info.nightscout.androidaps.core.R.string.key_units, Constants.MGDL) == Constants.MGDL) BgSync.GlucoseUnit.MGDL
        else BgSync.GlucoseUnit.MMOL
    }

    companion object {

        var isBusy = false
    }

    override fun onStart() {
        displayConnectionMessages = false
        injector?.let { MedLinkStatusParser.parseStatus(emptyArray<String>(), medLinkPumpStatus, it) }
        serviceConnection = object : ServiceConnection {
            override fun onServiceDisconnected(name: ComponentName) {
                aapsLogger.debug(LTag.PUMP, "MedLinkMedtronicService is disconnected")
                medLinkService = null
            }

            override fun onServiceConnected(name: ComponentName, service: IBinder) {
                aapsLogger.debug(LTag.PUMP, "MedLinkMedtronicService is connected")
                val mLocalBinder = service as MedLinkMedtronicService.LocalBinder
                medLinkService = mLocalBinder.serviceInstance
                medLinkService!!.verifyConfiguration()
                Thread {
                    for (i in 0..19) {
                        SystemClock.sleep(5000)
                        aapsLogger.debug(LTag.PUMP, "Starting Medtronic-MedLink service")
                        if (medLinkService!!.setNotInPreInit()) {
                            aapsLogger.debug("MedlinkService setnotinpreinit")
                            break
                        }
                    }
                }.start()
            }
        }
        super.onStart()
    }

    override fun setPumpDeviceState(state: PumpDeviceState) {
        medLinkService?.setPumpDeviceState(state)
    }

    override fun setMedtronicPumpModel(model: String) {
        medtronicUtil.medtronicPumpModel = MedLinkMedtronicDeviceType.getByDescription(model)
    }

    override fun setBatteryLevel(batteryLevel: Int) {
        pumpStatusData.batteryRemaining = batteryLevel
    }
}