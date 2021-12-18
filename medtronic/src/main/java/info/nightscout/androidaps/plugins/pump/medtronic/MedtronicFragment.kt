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
import info.nightscout.androidaps.events.EventExtendedBolusChange
import info.nightscout.androidaps.events.EventPumpStatusChanged
import info.nightscout.androidaps.events.EventTempBasalChange
import info.nightscout.androidaps.interfaces.ActivePlugin
import info.nightscout.androidaps.interfaces.CommandQueue
import info.nightscout.androidaps.interfaces.PumpSync
import info.nightscout.shared.logging.AAPSLogger
import info.nightscout.shared.logging.LTag
import info.nightscout.androidaps.plugins.bus.RxBus
import info.nightscout.androidaps.plugins.pump.common.defs.PumpDeviceState
import info.nightscout.androidaps.plugins.pump.common.events.EventRefreshButtonState
import info.nightscout.androidaps.plugins.pump.common.events.EventRileyLinkDeviceStatusChange
import info.nightscout.androidaps.plugins.pump.common.hw.rileylink.RileyLinkUtil
import info.nightscout.androidaps.plugins.pump.common.hw.rileylink.defs.RileyLinkServiceState
import info.nightscout.androidaps.plugins.pump.common.hw.rileylink.defs.RileyLinkTargetDevice
import info.nightscout.androidaps.plugins.pump.common.hw.rileylink.dialog.RileyLinkStatusActivity
import info.nightscout.androidaps.plugins.pump.common.hw.rileylink.service.RileyLinkServiceData
import info.nightscout.androidaps.plugins.pump.medtronic.databinding.MedtronicFragmentBinding
import info.nightscout.androidaps.plugins.pump.medtronic.defs.BatteryType
import info.nightscout.androidaps.plugins.pump.medtronic.defs.MedtronicCommandType
import info.nightscout.androidaps.plugins.pump.medtronic.dialog.MedtronicHistoryActivity
import info.nightscout.androidaps.plugins.pump.medtronic.driver.MedtronicPumpStatus
import info.nightscout.androidaps.plugins.pump.medtronic.events.EventMedtronicPumpConfigurationChanged
import info.nightscout.androidaps.plugins.pump.medtronic.events.EventMedtronicPumpValuesChanged
import info.nightscout.androidaps.plugins.pump.medtronic.util.MedtronicUtil
import info.nightscout.androidaps.queue.Callback
import info.nightscout.androidaps.queue.events.EventQueueChanged
import info.nightscout.androidaps.utils.DateUtil
import info.nightscout.androidaps.utils.FabricPrivacy
import info.nightscout.androidaps.utils.T
import info.nightscout.androidaps.utils.WarnColors
import info.nightscout.androidaps.utils.alertDialogs.OKDialog
import info.nightscout.androidaps.utils.resources.ResourceHelper
import info.nightscout.androidaps.utils.rx.AapsSchedulers
import io.reactivex.disposables.CompositeDisposable
import io.reactivex.rxkotlin.plusAssign
import javax.inject.Inject

class MedtronicFragment : DaggerFragment() {

    @Inject lateinit var aapsLogger: AAPSLogger
    @Inject lateinit var fabricPrivacy: FabricPrivacy
    @Inject lateinit var rh: ResourceHelper
    @Inject lateinit var dateUtil: DateUtil
    @Inject lateinit var rxBus: RxBus
    @Inject lateinit var commandQueue: CommandQueue
    @Inject lateinit var activePlugin: ActivePlugin
    @Inject lateinit var medtronicPumpPlugin: MedtronicPumpPlugin
    @Inject lateinit var warnColors: WarnColors
    @Inject lateinit var rileyLinkUtil: RileyLinkUtil
    @Inject lateinit var medtronicUtil: MedtronicUtil
    @Inject lateinit var medtronicPumpStatus: MedtronicPumpStatus
    @Inject lateinit var rileyLinkServiceData: RileyLinkServiceData
    @Inject lateinit var aapsSchedulers: AapsSchedulers
    @Inject lateinit var pumpSync: PumpSync

    private var disposable: CompositeDisposable = CompositeDisposable()

    private val handler = Handler(HandlerThread(this::class.simpleName + "Handler").also { it.start() }.looper)
    private lateinit var refreshLoop: Runnable

    init {
        refreshLoop = Runnable {
            activity?.runOnUiThread { updateGUI() }
            handler.postDelayed(refreshLoop, T.mins(1).msecs())
        }
    }

    private var _binding: MedtronicFragmentBinding? = null

    // This property is only valid between onCreateView and
    // onDestroyView.
    private val binding get() = _binding!!

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View =
        MedtronicFragmentBinding.inflate(inflater, container, false).also { _binding = it }.root

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        binding.rlStatus.text = rh.gs(RileyLinkServiceState.NotStarted.resourceId)

        binding.pumpStatusIcon.setTextColor(Color.WHITE)
        @SuppressLint("SetTextI18n")
        binding.pumpStatusIcon.text = "{fa-bed}"

        binding.history.setOnClickListener {
            if (medtronicPumpPlugin.rileyLinkService?.verifyConfiguration() == true) {
                startActivity(Intent(context, MedtronicHistoryActivity::class.java))
            } else {
                displayNotConfiguredDialog()
            }
        }

        binding.refresh.setOnClickListener {
            if (medtronicPumpPlugin.rileyLinkService?.verifyConfiguration() != true) {
                displayNotConfiguredDialog()
            } else {
                binding.refresh.isEnabled = false
                medtronicPumpPlugin.resetStatusState()
                commandQueue.readStatus(rh.gs(R.string.clicked_refresh), object : Callback() {
                    override fun run() {
                        activity?.runOnUiThread { if (_binding != null) binding.refresh.isEnabled = true }
                    }
                })
            }
        }

        binding.stats.setOnClickListener {
            if (medtronicPumpPlugin.rileyLinkService?.verifyConfiguration() == true) {
                startActivity(Intent(context, RileyLinkStatusActivity::class.java))
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
            .toObservable(EventRileyLinkDeviceStatusChange::class.java)
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
                medtronicPumpPlugin.rileyLinkService?.verifyConfiguration()
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
    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

    @SuppressLint("SetTextI18n")
    @Synchronized
    private fun setDeviceStatus() {
        val resourceId = rileyLinkServiceData.rileyLinkServiceState.resourceId
        val rileyLinkError = medtronicPumpPlugin.rileyLinkService?.error
        binding.rlStatus.text =
            when {
                rileyLinkServiceData.rileyLinkServiceState == RileyLinkServiceState.NotStarted -> rh.gs(resourceId)
                rileyLinkServiceData.rileyLinkServiceState.isConnecting                        -> "{fa-bluetooth-b spin}   " + rh.gs(resourceId)
                rileyLinkServiceData.rileyLinkServiceState.isError && rileyLinkError == null   -> "{fa-bluetooth-b}   " + rh.gs(resourceId)
                rileyLinkServiceData.rileyLinkServiceState.isError && rileyLinkError != null   -> "{fa-bluetooth-b}   " + rh.gs(rileyLinkError.getResourceId(RileyLinkTargetDevice.MedtronicPump))
                else                                                                           -> "{fa-bluetooth-b}   " + rh.gs(resourceId)
            }
        binding.rlStatus.setTextColor(if (rileyLinkError != null) Color.RED else Color.WHITE)

        binding.errors.text =
            rileyLinkServiceData.rileyLinkError?.let {
                rh.gs(it.getResourceId(RileyLinkTargetDevice.MedtronicPump))
            } ?: "-"

        when (medtronicPumpStatus.pumpDeviceState) {
            PumpDeviceState.Sleeping             ->
                binding.pumpStatusIcon.text = "{fa-bed}   " // + pumpStatus.pumpDeviceState.name());

            PumpDeviceState.NeverContacted,
            PumpDeviceState.WakingUp,
            PumpDeviceState.PumpUnreachable,
            PumpDeviceState.ErrorWhenCommunicating,
            PumpDeviceState.TimeoutWhenCommunicating,
            PumpDeviceState.InvalidConfiguration ->
                binding.pumpStatusIcon.text = " " + rh.gs(medtronicPumpStatus.pumpDeviceState.resourceId)

            PumpDeviceState.Active               -> {
                val cmd = medtronicUtil.getCurrentCommand()
                if (cmd == null)
                    binding.pumpStatusIcon.text = " " + rh.gs(medtronicPumpStatus.pumpDeviceState.resourceId)
                else {
                    aapsLogger.debug(LTag.PUMP, "Command: $cmd")
                    val cmdResourceId = cmd.resourceId //!!
                    if (cmd == MedtronicCommandType.GetHistoryData) {
                        binding.pumpStatusIcon.text = medtronicUtil.frameNumber?.let {
                            rh.gs(cmdResourceId!!, medtronicUtil.pageNumber, medtronicUtil.frameNumber)
                        }
                            ?: rh.gs(R.string.medtronic_cmd_desc_get_history_request, medtronicUtil.pageNumber)
                    } else {
                        binding.pumpStatusIcon.text = " " + (cmdResourceId?.let { rh.gs(it) }
                            ?: cmd.commandDescription)
                    }
                }
            }

            else                                 ->
                aapsLogger.warn(LTag.PUMP, "Unknown pump state: " + medtronicPumpStatus.pumpDeviceState)
        }

        val status = commandQueue.spannedStatus()
        if (status.toString() == "") {
            binding.queue.visibility = View.GONE
        } else {
            binding.queue.visibility = View.VISIBLE
            binding.queue.text = status
        }
    }

    private fun displayNotConfiguredDialog() {
        context?.let {
            OKDialog.show(it, rh.gs(R.string.medtronic_warning),
                rh.gs(R.string.medtronic_error_operation_not_possible_no_configuration), null)
        }
    }

    // GUI functions
    @SuppressLint("SetTextI18n")
    @Synchronized
    fun updateGUI() {
        if (_binding == null) return

        setDeviceStatus()

        // last connection
        if (medtronicPumpStatus.lastConnection != 0L) {
            val minAgo = dateUtil.minAgo(rh, medtronicPumpStatus.lastConnection)
            val min = (System.currentTimeMillis() - medtronicPumpStatus.lastConnection) / 1000 / 60
            if (medtronicPumpStatus.lastConnection + 60 * 1000 > System.currentTimeMillis()) {
                binding.lastConnection.setText(R.string.medtronic_pump_connected_now)
                binding.lastConnection.setTextColor(Color.WHITE)
            } else if (medtronicPumpStatus.lastConnection + 30 * 60 * 1000 < System.currentTimeMillis()) {

                if (min < 60) {
                    binding.lastConnection.text = rh.gs(R.string.minago, min)
                } else if (min < 1440) {
                    val h = (min / 60).toInt()
                    binding.lastConnection.text = (rh.gq(R.plurals.duration_hours, h, h) + " "
                        + rh.gs(R.string.ago))
                } else {
                    val h = (min / 60).toInt()
                    val d = h / 24
                    // h = h - (d * 24);
                    binding.lastConnection.text = (rh.gq(R.plurals.duration_days, d, d) + " "
                        + rh.gs(R.string.ago))
                }
                binding.lastConnection.setTextColor(Color.RED)
            } else {
                binding.lastConnection.text = minAgo
                binding.lastConnection.setTextColor(Color.WHITE)
            }
        }

        // last bolus
        val bolus = medtronicPumpStatus.lastBolusAmount
        val bolusTime = medtronicPumpStatus.lastBolusTime
        if (bolus != null && bolusTime != null) {
            val agoMsc = System.currentTimeMillis() - bolusTime.time
            val bolusMinAgo = agoMsc.toDouble() / 60.0 / 1000.0
            val unit = rh.gs(R.string.insulin_unit_shortname)
            val ago = when {
                agoMsc < 60 * 1000 -> rh.gs(R.string.medtronic_pump_connected_now)
                bolusMinAgo < 60   -> dateUtil.minAgo(rh, bolusTime.time)
                else               -> dateUtil.hourAgo(bolusTime.time, rh)
            }
            binding.lastBolus.text = rh.gs(R.string.mdt_last_bolus, bolus, unit, ago)
        } else {
            binding.lastBolus.text = ""
        }

        // base basal rate
        binding.baseBasalRate.text = ("(" + medtronicPumpStatus.activeProfileName + ")  "
            + rh.gs(R.string.pump_basebasalrate, medtronicPumpPlugin.baseBasalRate))

        // TBR
        var tbrStr = ""
        val tbrRemainingTime: Int? = medtronicPumpStatus.tbrRemainingTime

        if (tbrRemainingTime != null) {
            tbrStr = rh.gs(R.string.mdt_tbr_remaining, medtronicPumpStatus.tempBasalAmount, tbrRemainingTime)
        }
        binding.tempBasal.text = tbrStr

        // battery
        if (medtronicPumpStatus.batteryType == BatteryType.None || medtronicPumpStatus.batteryVoltage == null) {
            binding.pumpStateBattery.text = "{fa-battery-" + medtronicPumpStatus.batteryRemaining / 25 + "}  "
        } else {
            binding.pumpStateBattery.text = "{fa-battery-" + medtronicPumpStatus.batteryRemaining / 25 + "}  " + medtronicPumpStatus.batteryRemaining + "%" + String.format("  (%.2f V)", medtronicPumpStatus.batteryVoltage)
        }
        warnColors.setColorInverse(binding.pumpStateBattery, medtronicPumpStatus.batteryRemaining.toDouble(), 25.0, 10.0)

        // reservoir
        binding.reservoir.text = rh.gs(R.string.reservoirvalue, medtronicPumpStatus.reservoirRemainingUnits, medtronicPumpStatus.reservoirFullUnits)
        warnColors.setColorInverse(binding.reservoir, medtronicPumpStatus.reservoirRemainingUnits, 50.0, 20.0)

        medtronicPumpPlugin.rileyLinkService?.verifyConfiguration()
        binding.errors.text = medtronicPumpStatus.errorInfo

        val showRileyLinkBatteryLevel: Boolean = rileyLinkServiceData.showBatteryLevel

        if (showRileyLinkBatteryLevel) {
            binding.rlBatteryView.visibility = View.VISIBLE
            binding.rlBatteryLabel.visibility = View.VISIBLE
            binding.rlBatteryState.visibility = View.VISIBLE
            binding.rlBatteryLayout.visibility = View.VISIBLE
            binding.rlBatterySemicolon.visibility = View.VISIBLE
            if (rileyLinkServiceData.batteryLevel == null) {
                binding.rlBatteryState.text = " ?"
            } else {
                binding.rlBatteryState.text = "{fa-battery-" + rileyLinkServiceData.batteryLevel / 25 + "}  " + rileyLinkServiceData.batteryLevel + "%"
            }
        } else {
            binding.rlBatteryView.visibility = View.GONE
            binding.rlBatteryLabel.visibility = View.GONE
            binding.rlBatteryState.visibility = View.GONE
            binding.rlBatteryLayout.visibility = View.GONE
            binding.rlBatterySemicolon.visibility = View.GONE
        }

    }
}
