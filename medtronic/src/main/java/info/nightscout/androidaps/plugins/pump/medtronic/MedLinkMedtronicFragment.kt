package info.nightscout.androidaps.plugins.pump.medtronic

import android.content.Intent
import android.graphics.Color
import android.os.Bundle
import android.os.Handler
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import dagger.android.support.DaggerFragment
import info.nightscout.androidaps.events.EventExtendedBolusChange
import info.nightscout.androidaps.events.EventPumpStatusChanged
import info.nightscout.androidaps.events.EventTempBasalChange
import info.nightscout.androidaps.interfaces.ActivePluginProvider
import info.nightscout.androidaps.interfaces.CommandQueueProvider
import info.nightscout.androidaps.logging.AAPSLogger
import info.nightscout.androidaps.logging.LTag
import info.nightscout.androidaps.plugins.bus.RxBusWrapper
import info.nightscout.androidaps.plugins.pump.common.data.MedLinkPumpStatus
import info.nightscout.androidaps.plugins.pump.common.defs.PumpDeviceState
import info.nightscout.androidaps.plugins.pump.medtronic.defs.BatteryType
import info.nightscout.androidaps.plugins.pump.medtronic.dialog.MedtronicHistoryActivity
import info.nightscout.androidaps.plugins.pump.common.events.EventRileyLinkDeviceStatusChange
import info.nightscout.androidaps.plugins.pump.medtronic.events.EventMedtronicPumpConfigurationChanged
import info.nightscout.androidaps.plugins.pump.medtronic.events.EventMedtronicPumpValuesChanged
import info.nightscout.androidaps.plugins.pump.common.events.EventRefreshButtonState
import info.nightscout.androidaps.plugins.pump.common.hw.medlink.MedLinkUtil
import info.nightscout.androidaps.plugins.pump.common.hw.medlink.defs.MedLinkCommandType
import info.nightscout.androidaps.plugins.pump.common.hw.medlink.defs.MedLinkServiceState
import info.nightscout.androidaps.plugins.pump.common.hw.medlink.dialog.MedLinkStatusActivity
import info.nightscout.androidaps.plugins.pump.common.hw.medlink.service.MedLinkServiceData
import info.nightscout.androidaps.plugins.pump.common.hw.rileylink.defs.RileyLinkServiceState
import info.nightscout.androidaps.plugins.pump.common.hw.rileylink.defs.RileyLinkTargetDevice
import info.nightscout.androidaps.plugins.pump.medtronic.dialog.MedLinkMedtronicHistoryActivity
import info.nightscout.androidaps.plugins.pump.medtronic.driver.MedLinkMedtronicPumpStatus
import info.nightscout.androidaps.plugins.pump.medtronic.util.MedLinkMedtronicUtil
import info.nightscout.androidaps.queue.Callback
import info.nightscout.androidaps.queue.events.EventQueueChanged
import info.nightscout.androidaps.utils.DateUtil
import info.nightscout.androidaps.utils.FabricPrivacy
import info.nightscout.androidaps.utils.T
import info.nightscout.androidaps.utils.WarnColors
import info.nightscout.androidaps.utils.alertDialogs.OKDialog
import info.nightscout.androidaps.utils.extensions.plusAssign
import info.nightscout.androidaps.utils.resources.ResourceHelper
import io.reactivex.android.schedulers.AndroidSchedulers
import io.reactivex.disposables.CompositeDisposable
import kotlinx.android.synthetic.main.medlink_medtronic_fragment.*
import java.time.ZoneOffset
import javax.inject.Inject

class MedLinkMedtronicFragment : DaggerFragment() {

    @Inject lateinit var aapsLogger: AAPSLogger
    @Inject lateinit var fabricPrivacy: FabricPrivacy
    @Inject lateinit var resourceHelper: ResourceHelper
    @Inject lateinit var rxBus: RxBusWrapper
    @Inject lateinit var commandQueue: CommandQueueProvider
    @Inject lateinit var activePlugin: ActivePluginProvider
    @Inject lateinit var medlinkMedtronicPlugin: MedLinkMedtronicPumpPlugin
    @Inject lateinit var warnColors: WarnColors
    @Inject lateinit var medLinkUtil: MedLinkUtil
    @Inject lateinit var medLinkMedtronicUtil: MedLinkMedtronicUtil
    @Inject lateinit var medtronicPumpStatus: MedLinkMedtronicPumpStatus
    @Inject lateinit var medinkServiceData: MedLinkServiceData

    private var disposable: CompositeDisposable = CompositeDisposable()

    private val loopHandler = Handler()
    private lateinit var refreshLoop: Runnable

    init {
        refreshLoop = Runnable {
            activity?.runOnUiThread { updateGUI() }
            loopHandler.postDelayed(refreshLoop, T.mins(1).msecs())
        }
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? {
        return inflater.inflate(R.layout.medlink_medtronic_fragment, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        medtronic_pumpstatus.setBackgroundColor(resourceHelper.gc(R.color.colorInitializingBorder))

        medtronic_rl_status.text = resourceHelper.gs(RileyLinkServiceState.NotStarted.getResourceId())

        medtronic_pump_status.setTextColor(Color.WHITE)
        medtronic_pump_status.text = "{fa-bed}"

        medlink_medtronic_history.setOnClickListener {
            if (medlinkMedtronicPlugin.medLinkService?.verifyConfiguration() == true) {
                startActivity(Intent(context, MedLinkMedtronicHistoryActivity::class.java))
            } else {
                aapsLogger.debug("medtronic history")
                displayNotConfiguredDialog()
            }
        }

        medtronic_refresh.setOnClickListener {
            if (medlinkMedtronicPlugin.medLinkService?.verifyConfiguration() != true) {
                aapsLogger.debug("verifyConfiguration")
                displayNotConfiguredDialog()
            } else {
                medtronic_refresh.isEnabled = false
                medlinkMedtronicPlugin.resetStatusState()
                commandQueue.readStatus("Clicked refresh", object : Callback() {
                    override fun run() {
                        activity?.runOnUiThread { medtronic_refresh?.isEnabled = true }
                    }
                })
            }
        }

        medtronic_stats.setOnClickListener {
            if (medlinkMedtronicPlugin.medLinkService?.verifyConfiguration() == true) {
                aapsLogger.debug("verifyconfiguration")
                startActivity(Intent(context, MedLinkStatusActivity::class.java))
            } else {
                displayNotConfiguredDialog()
            }
        }
    }

    @Synchronized
    override fun onResume() {
        super.onResume()
        loopHandler.postDelayed(refreshLoop, T.mins(1).msecs())
        disposable += rxBus
            .toObservable(EventRefreshButtonState::class.java)
            .observeOn(AndroidSchedulers.mainThread())
            .subscribe({ medtronic_refresh.isEnabled = it.newState }, { fabricPrivacy.logException(it) })
        disposable += rxBus
            .toObservable(EventRileyLinkDeviceStatusChange::class.java)
            .observeOn(AndroidSchedulers.mainThread())
            .subscribe({
                aapsLogger.debug(LTag.PUMP, "onStatusEvent(EventRileyLinkDeviceStatusChange): $it")
                setDeviceStatus()
            }, { fabricPrivacy.logException(it) })
        disposable += rxBus
            .toObservable(EventMedtronicPumpValuesChanged::class.java)
            .observeOn(AndroidSchedulers.mainThread())
            .subscribe({ updateGUI() }, { fabricPrivacy.logException(it) })
        disposable += rxBus
            .toObservable(EventExtendedBolusChange::class.java)
            .observeOn(AndroidSchedulers.mainThread())
            .subscribe({ updateGUI() }, { fabricPrivacy.logException(it) })
        disposable += rxBus
            .toObservable(EventTempBasalChange::class.java)
            .observeOn(AndroidSchedulers.mainThread())
            .subscribe({ updateGUI() }, { fabricPrivacy.logException(it) })
        disposable += rxBus
            .toObservable(EventMedtronicPumpConfigurationChanged::class.java)
            .observeOn(AndroidSchedulers.mainThread())
            .subscribe({
                aapsLogger.debug(LTag.PUMP, "EventMedtronicPumpConfigurationChanged triggered")
                medlinkMedtronicPlugin.medLinkService?.verifyConfiguration()
                updateGUI()
            }, { fabricPrivacy.logException(it) })
        disposable += rxBus
            .toObservable(EventPumpStatusChanged::class.java)
            .observeOn(AndroidSchedulers.mainThread())
            .subscribe({ updateGUI() }, { fabricPrivacy.logException(it) })
        disposable += rxBus
            .toObservable(EventQueueChanged::class.java)
            .observeOn(AndroidSchedulers.mainThread())
            .subscribe({ updateGUI() }, { fabricPrivacy.logException(it) })

        updateGUI()
    }

    @Synchronized
    override fun onPause() {
        super.onPause()
        disposable.clear()
        loopHandler.removeCallbacks(refreshLoop)
    }

    @Synchronized
    private fun setDeviceStatus() {
        val resourceId = medinkServiceData.medLinkServiceState.getResourceId()
        val rileyLinkError = medlinkMedtronicPlugin.medLinkService?.error
        medtronic_rl_status.text =
            when {
                medinkServiceData.medLinkServiceState == MedLinkServiceState.NotStarted -> resourceHelper.gs(resourceId)
                medinkServiceData.medLinkServiceState.isConnecting                      -> "{fa-bluetooth-b spin}   " + resourceHelper.gs(resourceId)
                medinkServiceData.medLinkServiceState.isError && rileyLinkError == null -> "{fa-bluetooth-b}   " + resourceHelper.gs(resourceId)
                medinkServiceData.medLinkServiceState.isError && rileyLinkError != null -> "{fa-bluetooth-b}   " + resourceHelper.gs(rileyLinkError.getResourceId(RileyLinkTargetDevice.MedtronicPump))
                else                                                                    -> "{fa-bluetooth-b}   " + resourceHelper.gs(resourceId)
            }
        medtronic_rl_status.setTextColor(if (rileyLinkError != null) Color.RED else Color.WHITE)

        medtronic_errors.text =
            medinkServiceData.medLinkError?.let {
                resourceHelper.gs(it.getResourceId(RileyLinkTargetDevice.MedtronicPump))
            } ?: "-"

        when (medtronicPumpStatus.pumpDeviceState) {
            null,
            PumpDeviceState.Sleeping -> medtronic_pump_status.text = "{fa-bed}   " // + pumpStatus.pumpDeviceState.name());
            PumpDeviceState.NeverContacted,
            PumpDeviceState.WakingUp,
            PumpDeviceState.PumpUnreachable,
            PumpDeviceState.ErrorWhenCommunicating,
            PumpDeviceState.TimeoutWhenCommunicating,
            PumpDeviceState.InvalidConfiguration -> medtronic_pump_status.text = " " + resourceHelper.gs(medtronicPumpStatus.pumpDeviceState.resourceId)

            PumpDeviceState.Active -> {
                val cmd = medLinkMedtronicUtil.getCurrentCommand()
                if (cmd == null)
                    medtronic_pump_status.text = " " + resourceHelper.gs(medtronicPumpStatus.pumpDeviceState.resourceId)
                else {
                    aapsLogger.debug(LTag.PUMP, "Command: " + cmd)
                    val cmdResourceId = cmd.resourceId
                    if (cmd == MedLinkCommandType.GetState) {
                        medtronic_pump_status.text = medLinkMedtronicUtil.frameNumber?.let {
                            resourceHelper.gs(cmdResourceId, medLinkMedtronicUtil.pageNumber, medLinkMedtronicUtil.frameNumber)
                        }
                            ?: resourceHelper.gs(R.string.medtronic_cmd_desc_get_settings)
                    } else {
                        medtronic_pump_status.text = " " + (cmdResourceId?.let { resourceHelper.gs(it) }
                            ?: cmd.getCommandDescription())
                    }
                }
            }

            else                                 -> aapsLogger.warn(LTag.PUMP, "Unknown pump state: " + medtronicPumpStatus.pumpDeviceState)
        }

        val status = commandQueue.spannedStatus()
        if (status.toString() == "") {
            medtronic_queue.visibility = View.GONE
        } else {
            medtronic_queue.visibility = View.VISIBLE
            medtronic_queue.text = status
        }
    }

    private fun displayNotConfiguredDialog() {
        context?.let {
            OKDialog.show(it, resourceHelper.gs(R.string.medtronic_warning),
                resourceHelper.gs(R.string.medtronic_error_operation_not_possible_no_configuration) + "fragment", null)
        }
    }

    // GUI functions
    @Synchronized
    fun updateGUI() {
        if (medtronic_rl_status == null) return

        setDeviceStatus()

        // last connection
        if (medtronicPumpStatus.lastConnection != 0L) {
            val minAgo = DateUtil.minAgo(resourceHelper, medtronicPumpStatus.lastConnection)
            val min = (System.currentTimeMillis() - medtronicPumpStatus.lastConnection) / 1000 / 60
            if (medtronicPumpStatus.lastConnection + 60 * 1000 > System.currentTimeMillis()) {
                medtronic_lastconnection.setText(R.string.medtronic_pump_connected_now)
                medtronic_lastconnection.setTextColor(Color.WHITE)
            } else if (medtronicPumpStatus.lastConnection + 30 * 60 * 1000 < System.currentTimeMillis()) {

                if (min < 60) {
                    medtronic_lastconnection.text = resourceHelper.gs(R.string.minago, min)
                } else if (min < 1440) {
                    val h = (min / 60).toInt()
                    medtronic_lastconnection.text = (resourceHelper.gq(R.plurals.duration_hours, h, h) + " "
                        + resourceHelper.gs(R.string.ago))
                } else {
                    val h = (min / 60).toInt()
                    val d = h / 24
                    // h = h - (d * 24);
                    medtronic_lastconnection.text = (resourceHelper.gq(R.plurals.duration_days, d, d) + " "
                        + resourceHelper.gs(R.string.ago))
                }
                medtronic_lastconnection.setTextColor(Color.RED)
            } else {
                medtronic_lastconnection.text = minAgo
                medtronic_lastconnection.setTextColor(Color.WHITE)
            }
        }

        // last bolus
        val bolus = medtronicPumpStatus.lastBolusAmount
        val bolusTime = medtronicPumpStatus.lastBolusTime
        if (bolus != null && bolusTime != null) {
            val agoMsc = System.currentTimeMillis() - medtronicPumpStatus.lastBolusTime.time
            val bolusMinAgo = agoMsc.toDouble() / 60.0 / 1000.0
            val unit = resourceHelper.gs(R.string.insulin_unit_shortname)
            val ago: String
            if (agoMsc < 60 * 1000) {
                ago = resourceHelper.gs(R.string.medtronic_pump_connected_now)
            } else if (bolusMinAgo < 60) {
                ago = DateUtil.minAgo(resourceHelper, medtronicPumpStatus.lastBolusTime.time)
            } else {
                ago = DateUtil.hourAgo(medtronicPumpStatus.lastBolusTime.time, resourceHelper)
            }
            medtronic_lastbolus.text = resourceHelper.gs(R.string.mdt_last_bolus, bolus, unit, ago)
        } else {
            medtronic_lastbolus.text = ""
        }

        // base basal rate
        medtronic_basabasalrate.text = ("(${medtronicPumpStatus.activeProfileName.toUpperCase()})  "
            + resourceHelper.gs(R.string.pump_basebasalrate, medlinkMedtronicPlugin.baseBasalRate))

        medtronic_tempbasal.text = activePlugin.activeTreatments.getTempBasalFromHistory(System.currentTimeMillis())?.toStringFull()
            ?: ""

        // battery
        if (medtronicPumpStatus.batteryType == BatteryType.None || medtronicPumpStatus.batteryVoltage == null) {
            medtronic_pumpstate_battery.text = "{fa-battery-${medtronicPumpStatus.batteryRemaining / 25}} "
            // } else if (medtronicPumpStatus.batteryType == BatteryType.LiPo) {
            //     medtronic_pumpstate_battery.text = "{fa-battery-" + medtronicPumpStatus.batteryRemaining / 25 + "}  " + String.format(" %.2f V", medtronicPumpStatus.batteryVoltage)
        } else {
            medtronic_pumpstate_battery.text = "{fa-battery-${medtronicPumpStatus.batteryRemaining / 25}} ${String.format("%.2f V", medtronicPumpStatus.batteryVoltage)}"
        }
        warnColors.setColorInverse(medtronic_pumpstate_battery, medtronicPumpStatus.batteryRemaining.toDouble(), 25.0, 10.0)

        // reservoir
        medtronic_reservoir.text = resourceHelper.gs(R.string.reservoirvalue, medtronicPumpStatus.reservoirRemainingUnits, medtronicPumpStatus.reservoirFullUnits)
        warnColors.setColorInverse(medtronic_reservoir, medtronicPumpStatus.reservoirRemainingUnits, 50.0, 20.0)

        medlinkMedtronicPlugin.medLinkService?.verifyConfiguration()
        medtronic_errors.text = medtronicPumpStatus.errorInfo

        // next command
        val pump = activePlugin.activePump
        if (pump is MedLinkMedtronicPumpPlugin ) {
            if (pump.temporaryBasal != null) {
                ("${pump.tempBasalMicrobolusOperations.operations.peek().toStringView()}" +
                    "\n${pump.nextScheduledCommand()}").also { medtronic_next_command.text = it }
            } else {
                medtronic_next_command.text = pump.nextScheduledCommand()
            }
        }

        medlinkMedtronicPlugin.medLinkService?.verifyConfiguration()
        medtronic_errors.text = medtronicPumpStatus.errorInfo

        //totalDailyInsulin
        val todayTotalUnits = medtronicPumpStatus.todayTotalUnits
        val yesterdayTotalUnits = medtronicPumpStatus.yesterdayTotalUnits
        aapsLogger.info(LTag.EVENTS, "today yesterday$todayTotalUnits $yesterdayTotalUnits")
        if (todayTotalUnits != null && yesterdayTotalUnits != null) {
            medtronic_total_insulin.text = "$todayTotalUnits/$yesterdayTotalUnits"
        } else {
            medtronic_lastbolus.text = ""
        }

        //DeviceBattery
        val deviceBatteryRemaining = medinkServiceData.batteryLevel
        val deviceBatteryVoltage = medtronicPumpStatus.deviceBatteryVoltage
        aapsLogger.info(LTag.EVENTS, "device battery$deviceBatteryRemaining $deviceBatteryVoltage")

        medtronic_devicestate_battery.text = "{fa-battery-${deviceBatteryRemaining/ 25} }  ${deviceBatteryRemaining}%";

        warnColors.setColorInverse(medtronic_pumpstate_battery, deviceBatteryRemaining.toDouble(), 25.0, 10.0)

        // next calibration
        if (medtronicPumpStatus.nextCalibration != null) {
            val agoMsc = medtronicPumpStatus.nextCalibration.toInstant().toEpochMilli() - System.currentTimeMillis()
            val calibrationMinAgo = agoMsc.toDouble() / 60.0 / 1000.0

            val ago: String
            if (agoMsc < 60 * 1000) {
                ago = resourceHelper.gs(R.string.medtronic_pump_connected_now)
            } else if (calibrationMinAgo < 60) {
                ago = DateUtil.minAfter(resourceHelper, medtronicPumpStatus.nextCalibration.toInstant().toEpochMilli())
            } else {
                ago = DateUtil.hourAfter(medtronicPumpStatus.nextCalibration.toInstant().toEpochMilli(), resourceHelper)
            }
            medtronic_nextcalibration.text = resourceHelper.gs(R.string.mdt_next_calibration, ago, medtronicPumpStatus.nextCalibration.toLocalTime())
            warnColors.setColorInverse(medtronic_nextcalibration, calibrationMinAgo, 60.0, 30.0)
        } else {
            medtronic_nextcalibration.text = ""
        }

        // isig
        val pumpStatus = medtronicPumpStatus
        if (medtronicPumpStatus.isig != null && medtronicPumpStatus.isig == 0.0) {
            medtronic_isig.text = resourceHelper.gs(info.nightscout.androidaps.core.R.string.sensor_lost)
            medtronic_isig.setTextColor(Color.RED)
        } else if (medtronicPumpStatus.isig != null) {
            medtronic_isig.text = String.format("%.2fnA", medtronicPumpStatus.isig)
            warnColors.setColorInverse(medtronic_isig, medtronicPumpStatus.isig, 8.0, 7.0)
            warnColors.setColor(medtronic_isig, medtronicPumpStatus.isig, 26.0, 31.0)
        } else {
            medtronic_nextcalibration.text = ""
        }
    }
}
