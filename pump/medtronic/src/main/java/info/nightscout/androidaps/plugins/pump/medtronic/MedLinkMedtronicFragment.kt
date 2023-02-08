package info.nightscout.androidaps.plugins.pump.medtronic

import android.annotation.SuppressLint
import android.content.Intent
import android.graphics.Color
import android.os.Bundle
import android.os.Handler
import android.os.HandlerThread
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import dagger.android.support.DaggerFragment
import info.nightscout.androidaps.plugins.pump.common.events.EventMedLinkDeviceStatusChange
import info.nightscout.androidaps.plugins.pump.common.hw.medlink.MedLinkUtil
import info.nightscout.androidaps.plugins.pump.common.hw.medlink.defs.MedLinkCommandType
import info.nightscout.androidaps.plugins.pump.common.hw.medlink.defs.MedLinkServiceState
import info.nightscout.androidaps.plugins.pump.common.hw.medlink.dialog.MedLinkStatusActivity
import info.nightscout.androidaps.plugins.pump.common.hw.medlink.service.MedLinkServiceData
import info.nightscout.androidaps.plugins.pump.common.hw.rileylink.defs.RileyLinkTargetDevice
import info.nightscout.androidaps.plugins.pump.medtronic.databinding.MedlinkMedtronicFragmentBinding
import info.nightscout.androidaps.plugins.pump.medtronic.defs.BatteryType
import info.nightscout.androidaps.plugins.pump.medtronic.dialog.MedLinkMedtronicHistoryActivity
import info.nightscout.androidaps.plugins.pump.medtronic.driver.MedLinkMedtronicPumpStatus
import info.nightscout.androidaps.plugins.pump.medtronic.events.EventMedtronicPumpConfigurationChanged
import info.nightscout.androidaps.plugins.pump.medtronic.events.EventMedtronicPumpValuesChanged
import info.nightscout.androidaps.plugins.pump.medtronic.util.MedLinkMedtronicUtil
import info.nightscout.core.ui.dialogs.OKDialog
import info.nightscout.core.utils.fabric.FabricPrivacy
import info.nightscout.interfaces.plugin.ActivePlugin
import info.nightscout.interfaces.pump.WarnColors
import info.nightscout.interfaces.queue.Callback
import info.nightscout.interfaces.queue.CommandQueue
import info.nightscout.pump.common.defs.PumpRunningState
import info.nightscout.pump.core.defs.PumpDeviceState
import info.nightscout.rx.AapsSchedulers
import info.nightscout.rx.bus.RxBus
import info.nightscout.rx.events.*
import info.nightscout.rx.logging.AAPSLogger
import info.nightscout.rx.logging.LTag
import info.nightscout.shared.interfaces.ResourceHelper
import info.nightscout.shared.utils.DateUtil
import info.nightscout.shared.utils.T
import io.reactivex.rxjava3.disposables.CompositeDisposable
import io.reactivex.rxjava3.kotlin.plusAssign
import java.util.*
import javax.inject.Inject

class MedLinkMedtronicFragment : DaggerFragment() {

    @Inject lateinit var aapsLogger: AAPSLogger
    @Inject lateinit var fabricPrivacy: FabricPrivacy
    @Inject lateinit var rh: ResourceHelper
    @Inject lateinit var rxBus: RxBus
    @Inject lateinit var commandQueue: CommandQueue
    @Inject lateinit var activePlugin: ActivePlugin
    @Inject lateinit var medLinkMedtronicPumpPlugin: MedLinkMedtronicPumpPlugin
    @Inject lateinit var warnColors: WarnColors
    @Inject lateinit var medLinkUtil: MedLinkUtil
    @Inject lateinit var dateUtil: DateUtil
    @Inject lateinit var medLinkMedtronicUtil: MedLinkMedtronicUtil
    @Inject lateinit var medtronicPumpStatus: MedLinkMedtronicPumpStatus
    @Inject lateinit var medLinkServiceData: MedLinkServiceData
    @Inject lateinit var aapsSchedulers: AapsSchedulers
    
    private var disposable: CompositeDisposable = CompositeDisposable()

    private val handler = Handler(HandlerThread(this::class.simpleName + "Handler").also { it.start() }.looper)
    private lateinit var refreshLoop: Runnable

    init {
        refreshLoop = Runnable {
            activity?.runOnUiThread { updateGUI() }
            handler.postDelayed(refreshLoop, T.mins(1).msecs())
        }
    }

    private var _binding: MedlinkMedtronicFragmentBinding? = null

    // This property is only valid between onCreateView and
    // onDestroyView.
    private val binding get() = _binding!!

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View =
    MedlinkMedtronicFragmentBinding.inflate(inflater, container, false).also { _binding = it }.root


    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        super.onViewCreated(view, savedInstanceState)

        binding.medtronicRlStatus.text = rh.gs(MedLinkServiceState.NotStarted.resourceId)

        binding.medtronicRlStatus.setTextColor(Color.WHITE)
        @SuppressLint("SetTextI18n")
        binding.medtronicRlStatus.text = "{fa-bed}"

        binding.history.setOnClickListener {
            if (medLinkMedtronicPumpPlugin.medLinkService?.verifyConfiguration() == true) {
                startActivity(Intent(context, MedLinkMedtronicHistoryActivity::class.java))
            } else {
                displayNotConfiguredDialog()
            }
        }

        binding.refresh.setOnClickListener {
            val service =medLinkMedtronicPumpPlugin.medLinkService
            if (medLinkMedtronicPumpPlugin.medLinkService?.verifyConfiguration() != true) {
                displayNotConfiguredDialog()
            } else {
                binding.refresh.isEnabled = false
                medLinkMedtronicPumpPlugin.resetStatusState()
                commandQueue.readStatus(rh.gs(R.string.clicked_refresh), object : Callback() {
                    override fun run() {
                        activity?.runOnUiThread { if (_binding != null) binding.refresh.isEnabled = true }
                    }
                })
            }
        }

        binding.stats.setOnClickListener {
            if (medLinkMedtronicPumpPlugin.medLinkService?.verifyConfiguration() == true) {
                startActivity(Intent(context, MedLinkStatusActivity::class.java))
            } else {
                displayNotConfiguredDialog()
            }
        }
    }

    @Synchronized
    override fun onResume() {
        super.onResume()
        handler.postDelayed(refreshLoop, T.mins(1).msecs())
        disposable += rxBus
            .toObservable(EventRefreshButtonState::class.java)
            .observeOn(aapsSchedulers.main)
            .subscribe({ binding.refresh.isEnabled = it.newState }, fabricPrivacy::logException)
        disposable += rxBus
            .toObservable(EventMedLinkDeviceStatusChange::class.java)
            .observeOn(aapsSchedulers.main)
            .subscribe({
                           aapsLogger.debug(LTag.PUMP, "onStatusEvent(EventRileyLinkDeviceStatusChange): $it")
                           setDeviceStatus()
                       }, fabricPrivacy::logException)
        disposable += rxBus
            .toObservable(EventMedtronicPumpValuesChanged::class.java)
            .observeOn(aapsSchedulers.main)
            .subscribe({ updateGUI() }, fabricPrivacy::logException)
        disposable += rxBus
            .toObservable(EventExtendedBolusChange::class.java)
            .observeOn(aapsSchedulers.main)
            .subscribe({ updateGUI() }, fabricPrivacy::logException)
        disposable += rxBus
            .toObservable(EventTempBasalChange::class.java)
            .observeOn(aapsSchedulers.main)
            .subscribe({ updateGUI() }, fabricPrivacy::logException)
        disposable += rxBus
            .toObservable(EventMedtronicPumpConfigurationChanged::class.java)
            .observeOn(aapsSchedulers.main)
            .subscribe({
                           aapsLogger.debug(LTag.PUMP, "EventMedtronicPumpConfigurationChanged triggered")
                           medLinkMedtronicPumpPlugin.medLinkService?.verifyConfiguration()
                           updateGUI()
                       }, fabricPrivacy::logException)
        disposable += rxBus
            .toObservable(EventPumpStatusChanged::class.java)
            .observeOn(aapsSchedulers.main)
            .subscribe({ updateGUI() }, fabricPrivacy::logException)
        disposable += rxBus
            .toObservable(EventQueueChanged::class.java)
            .observeOn(aapsSchedulers.main)
            .subscribe({ updateGUI() }, fabricPrivacy::logException)

        updateGUI()
    }

    @Synchronized
    override fun onPause() {
        super.onPause()
        disposable.clear()
        handler.removeCallbacks(refreshLoop)
    }

    @Synchronized
    private fun setDeviceStatus() {
        val resourceId = medLinkServiceData.medLinkServiceState.resourceId
        val medLinkError = medLinkMedtronicPumpPlugin.medLinkService?.error

        binding.medtronicRlStatus.text =
            when {
                medLinkServiceData.medLinkServiceState == MedLinkServiceState.NotStarted -> rh.gs(resourceId)
                medLinkServiceData.medLinkServiceState.isConnecting                      -> "{fa-bluetooth-b spin}   " + rh.gs(resourceId)
                medLinkServiceData.medLinkServiceState.isError && medLinkError == null   -> "{fa-bluetooth-b}   " + rh.gs(resourceId)
                medLinkServiceData.medLinkServiceState.isError && medLinkError != null   -> "{fa-bluetooth-b}   " + rh.gs(medLinkError.getResourceId(RileyLinkTargetDevice.MedtronicPump))
                else                                                                     -> "{fa-bluetooth-b}   " + rh.gs(resourceId)
            }
        binding.medtronicRlStatus.setTextColor(if (medLinkError != null) Color.RED else Color.WHITE)

        binding.medtronicErrors.text =
            medLinkServiceData.medLinkError?.let {
                rh.gs(it.getResourceId(RileyLinkTargetDevice.MedtronicPump))
            } ?: "-"

        when (medtronicPumpStatus.pumpDeviceState) {
            null,
            PumpDeviceState.Sleeping            -> binding.medtronicPumpStatus.text = "{fa-bed}   " // + pumpStatus.pumpDeviceState.name());
            PumpDeviceState.NeverContacted,
            PumpDeviceState.WakingUp,
            PumpDeviceState.PumpUnreachable,
            PumpDeviceState.ErrorWhenCommunicating,
            PumpDeviceState.TimeoutWhenCommunicating,
            PumpDeviceState.InvalidConfiguration-> binding.medtronicPumpStatus.text =  rh.gs(medtronicPumpStatus.pumpDeviceState.resourceId)

            PumpDeviceState.Active              -> {
                val cmd = medLinkMedtronicUtil.currentCommand
                if (cmd == null)
                    binding.medtronicPumpStatus.text = rh.gs(medtronicPumpStatus.pumpDeviceState.resourceId)
                else {
                    aapsLogger.debug(LTag.PUMP, "Command: " + cmd)
                    val cmdResourceId = cmd.resourceId
                    if (cmd == MedLinkCommandType.GetState) {
                        binding.medtronicPumpStatus.text = medLinkMedtronicUtil.frameNumber?.let {
                            rh.gs(cmdResourceId, medLinkMedtronicUtil.pageNumber, medLinkMedtronicUtil.frameNumber)
                        }
                            ?: rh.gs(R.string.medtronic_cmd_desc_get_settings)
                    } else {
                        binding.medtronicPumpStatus.text = " " + (cmdResourceId?.let { if(it>0){ rh.gs(it) }}
                            ?: cmd.getCommandDescription())
                    }
                }
            }

            else                                 -> aapsLogger.warn(LTag.PUMP, "Unknown pump state: " + medtronicPumpStatus.pumpDeviceState)
        }


        var id  = if(medtronicPumpStatus.pumpRunningState == PumpRunningState.Running){
            R.string.medtronic_pump_state_RUNNING;
        } else {
            R.string.medtronic_pump_state_SUSPENDED;
        }
        binding.medtronicPumpState.text = rh.gs(id)

        val status = commandQueue.spannedStatus()
        if (status.toString() == "") {
            binding.medtronicQueue.visibility = View.GONE
        } else {
            binding.medtronicQueue.visibility = View.VISIBLE
            binding.medtronicQueue.text = status
        }
    }

    private fun displayNotConfiguredDialog() {
        context?.let {
            OKDialog.show(it, rh.gs(R.string.medtronic_warning),
                          rh.gs(R.string.medtronic_error_operation_not_possible_no_configuration) + "fragment", null)
        }
    }

    // GUI functions
    @Synchronized
    fun updateGUI() {
        if (binding.medtronicPumpstatus == null) return

        setDeviceStatus()

        // last connection
        if (medtronicPumpStatus.lastConnection != 0L) {
            val minAgo = dateUtil.minAgo(rh, medtronicPumpStatus.lastConnection)
            val min = (System.currentTimeMillis() - medtronicPumpStatus.lastConnection) / 1000 / 60
            when {
                medtronicPumpStatus.lastConnection + 60 * 1000 > System.currentTimeMillis()      -> {
                    binding.medtronicLastconnection.setText(R.string.medtronic_pump_connected_now)
                    binding.medtronicLastconnection.setTextColor(Color.WHITE)
                }
                medtronicPumpStatus.lastConnection + 30 * 60 * 1000 < System.currentTimeMillis() -> {

                    if (min < 60) {
                        binding.medtronicLastconnection.text = rh.gs(info.nightscout.shared.R.string.minago, min)
                    } else if (min < 1440) {
                        val h = (min / 60).toInt()
                        ("${rh.gq(info.nightscout.androidaps.plugins.pump.common.hw.rileylink.R.plurals.duration_hours, h, h)} ${rh.gs(R.string.ago)}").also { binding.medtronicLastconnection.text = it }
                    } else {
                        val h = (min / 60).toInt()
                        val d = h / 24
                        // h = h - (d * 24);
                        ("${rh.gq(info.nightscout.androidaps.plugins.pump.common.hw.rileylink.R.plurals.duration_days, d, d)} ${rh.gs(R.string.ago)}").also { binding.medtronicLastconnection.text = it }
                    }
                    binding.medtronicLastconnection.setTextColor(Color.RED)
                }
                else                                                                             -> {
                    binding.medtronicLastconnection.text = minAgo
                    binding.medtronicLastconnection.setTextColor(Color.WHITE)
                }
            }
        }

        // last bolus
        val bolus = medtronicPumpStatus.lastBolusAmount
        val bolusTime = medtronicPumpStatus.lastBolusTime
        if (bolus != null && bolusTime != null) {
            val agoMsc = System.currentTimeMillis() - medtronicPumpStatus.lastBolusTime!!.time
            val bolusMinAgo = agoMsc.toDouble() / 60.0 / 1000.0
            val unit = rh.gs(info.nightscout.core.ui.R.string.insulin_unit_shortname)
            val ago = if (agoMsc < 60 * 1000) {
                rh.gs(R.string.medtronic_pump_connected_now)
            } else if (bolusMinAgo < 60) {
                dateUtil.minAgo(rh, medtronicPumpStatus.lastBolusTime!!.time)
            } else {
                dateUtil.hourAgo(medtronicPumpStatus.lastBolusTime!!.time, rh)
            }
            binding.medtronicLastbolus.text = rh.gs(R.string.mdt_last_bolus, bolus, unit, ago)
        } else {
            binding.medtronicLastbolus.text = ""
        }

        // base basal rate
        binding.medtronicBasabasalrate.text = ("(${medtronicPumpStatus.activeProfileName.uppercase(Locale.getDefault())})  "
            + rh.gs(info.nightscout.core.ui.R.string.pump_base_basal_rate, medLinkMedtronicPumpPlugin.baseBasalRate))

        binding.medtronicTempbasal.text = medtronicPumpStatus.tempBasalAmount.toString()
            ?: ""

        // battery
        if (medtronicPumpStatus.batteryType == BatteryType.None || medtronicPumpStatus.batteryVoltage == 0.0) {
            binding.medtronicPumpstateBattery.text = "{fa-battery-${medtronicPumpStatus.batteryRemaining / 25}} "
            // } else if (medtronicPumpStatus.batteryType == BatteryType.LiPo) {
            //     medtronic_pumpstate_battery.text = "{fa-battery-" + medtronicPumpStatus.batteryRemaining / 25 + "}  " + String.format(" %.2f V", medtronicPumpStatus.batteryVoltage)
        } else {
            binding.medtronicPumpstateBattery.text = "{fa-battery-${medtronicPumpStatus.batteryRemaining / 25}} ${String.format("%.2f V", medtronicPumpStatus.batteryVoltage)}"
        }
        warnColors.setColorInverse(binding.medtronicPumpstateBattery, medtronicPumpStatus.batteryRemaining.toDouble(), 25.0, 10.0)

        // reservoir
        binding.medtronicReservoir.text = rh.gs(info.nightscout.core.ui.R.string.reservoir_value, medtronicPumpStatus.reservoirRemainingUnits, medtronicPumpStatus.reservoirFullUnits)
        warnColors.setColorInverse(binding.medtronicReservoir, medtronicPumpStatus.reservoirRemainingUnits, 50.0, 20.0)

        medLinkMedtronicPumpPlugin.medLinkService?.verifyConfiguration()
        binding.medtronicErrors.text = medtronicPumpStatus.errorInfo

        // next command
        val pump = activePlugin.activePump
        if (pump is MedLinkMedtronicPumpPlugin ) {
            if (pump.temporaryBasal != null) {
                ("${pump.tempBasalMicrobolusOperations.operations.peek()?.toStringView()} \n${pump.nextScheduledCommand()}").also { binding.medtronicNextCommand.text = it }
            } else {
                binding.medtronicNextCommand.text = pump.nextScheduledCommand()
            }
        }

        medLinkMedtronicPumpPlugin.medLinkService?.verifyConfiguration()
        binding.medtronicErrors.text = medtronicPumpStatus.errorInfo

        //totalDailyInsulin
        val todayTotalUnits = medtronicPumpStatus.dailyTotalUnits
        val yesterdayTotalUnits = medtronicPumpStatus.yesterdayTotalUnits
        aapsLogger.info(LTag.EVENTS, "today yesterday$todayTotalUnits $yesterdayTotalUnits")
        if (todayTotalUnits != null && yesterdayTotalUnits != null) {
            binding.medtronicTotalInsulin.text = "$todayTotalUnits/$yesterdayTotalUnits"
        } else {
            binding.medtronicLastbolus.text = ""
        }

        //DeviceBattery
        val deviceBatteryRemaining = medtronicPumpStatus.batteryRemaining
        val deviceBatteryVoltage = medtronicPumpStatus.deviceBatteryVoltage
        aapsLogger.info(LTag.EVENTS, "device battery$deviceBatteryRemaining $deviceBatteryVoltage")

        binding.medtronicDevicestateBattery.text = "{fa-battery-${deviceBatteryRemaining/ 25} }  ${deviceBatteryRemaining}%";

        warnColors.setColorInverse(binding.medtronicDevicestateBattery, deviceBatteryRemaining.toDouble(), 25.0, 10.0)

        // next calibration
        if (medtronicPumpStatus.nextCalibration != null) {
            val agoMsc = medtronicPumpStatus.nextCalibration.toInstant().toEpochMilli() - System.currentTimeMillis()
            val calibrationMinAgo = agoMsc.toDouble() / 60.0 / 1000.0

            val ago = if (agoMsc < 60 * 1000) {
                rh.gs(R.string.medtronic_pump_connected_now)
            } else if (calibrationMinAgo < 60) {
                dateUtil.minAfter(rh, medtronicPumpStatus.nextCalibration.toInstant().toEpochMilli()).toString()
            } else {
                dateUtil.hourAfter(medtronicPumpStatus.nextCalibration.toInstant().toEpochMilli(), rh).toString()
            }
            binding.medtronicNextcalibration.text = rh.gs(R.string.mdt_next_calibration, ago, medtronicPumpStatus.nextCalibration.toLocalTime())
            warnColors.setColorInverse(binding.medtronicNextcalibration, calibrationMinAgo, 60.0, 30.0)
        } else {
            binding.medtronicNextcalibration.text = ""
        }

        // isig
        val pumpStatus = medtronicPumpStatus
        if (medtronicPumpStatus.isig != null && medtronicPumpStatus.isig == 0.0) {
            binding.medtronicIsig.text = rh.gs(info.nightscout.androidaps.plugins.pump.common.hw.rileylink.R.string.sensor_lost)
            binding.medtronicIsig.setTextColor(Color.RED)
        } else if (medtronicPumpStatus.isig != null) {
            binding.medtronicIsig.text = String.format("%.2fnA", medtronicPumpStatus.isig)
            warnColors.setColorInverse(binding.medtronicIsig, medtronicPumpStatus.isig, 8.0, 7.0)
            warnColors.setColor(binding.medtronicIsig, medtronicPumpStatus.isig, 26.0, 31.0)
        } else {
            binding.medtronicIsig.text = ""
        }
    }
}
