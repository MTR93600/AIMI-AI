package info.nightscout.pump.diaconn

import android.annotation.SuppressLint
import android.content.Intent
import android.os.Bundle
import android.os.Handler
import android.os.HandlerThread
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import dagger.android.support.DaggerFragment
import info.nightscout.core.utils.fabric.FabricPrivacy
import info.nightscout.interfaces.plugin.ActivePlugin
import info.nightscout.interfaces.pump.Pump
import info.nightscout.interfaces.pump.WarnColors
import info.nightscout.interfaces.queue.CommandQueue
import info.nightscout.interfaces.ui.UiInteraction
import info.nightscout.pump.diaconn.activities.DiaconnG8HistoryActivity
import info.nightscout.pump.diaconn.activities.DiaconnG8UserOptionsActivity
import info.nightscout.pump.diaconn.databinding.DiaconnG8FragmentBinding
import info.nightscout.pump.diaconn.events.EventDiaconnG8NewStatus
import info.nightscout.rx.AapsSchedulers
import info.nightscout.rx.bus.RxBus
import info.nightscout.rx.events.EventExtendedBolusChange
import info.nightscout.rx.events.EventInitializationChanged
import info.nightscout.rx.events.EventPumpStatusChanged
import info.nightscout.rx.events.EventQueueChanged
import info.nightscout.rx.events.EventTempBasalChange
import info.nightscout.rx.logging.AAPSLogger
import info.nightscout.rx.logging.LTag
import info.nightscout.shared.interfaces.ResourceHelper
import info.nightscout.shared.sharedPreferences.SP
import info.nightscout.shared.utils.DateUtil
import info.nightscout.shared.utils.T
import io.reactivex.rxjava3.disposables.CompositeDisposable
import io.reactivex.rxjava3.kotlin.plusAssign
import javax.inject.Inject

class DiaconnG8Fragment : DaggerFragment() {

    @Inject lateinit var rxBus: RxBus
    @Inject lateinit var aapsLogger: AAPSLogger
    @Inject lateinit var fabricPrivacy: FabricPrivacy
    @Inject lateinit var commandQueue: CommandQueue
    @Inject lateinit var activePlugin: ActivePlugin
    @Inject lateinit var diaconnG8Pump: DiaconnG8Pump
    @Inject lateinit var rh: ResourceHelper
    @Inject lateinit var sp: SP
    @Inject lateinit var warnColors: WarnColors
    @Inject lateinit var dateUtil: DateUtil
    @Inject lateinit var aapsSchedulers: AapsSchedulers
    @Inject lateinit var uiInteraction: UiInteraction

    private var disposable: CompositeDisposable = CompositeDisposable()

    private val handler = Handler(HandlerThread(this::class.simpleName + "Handler").also { it.start() }.looper)
    private lateinit var refreshLoop: Runnable

    private var _binding: DiaconnG8FragmentBinding? = null

    // This property is only valid between onCreateView and
    // onDestroyView.
    private val binding get() = _binding!!

    init {
        refreshLoop = Runnable {
            activity?.runOnUiThread { updateGUI() }
            handler.postDelayed(refreshLoop, T.mins(1).msecs())
        }
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = DiaconnG8FragmentBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        binding.history.setOnClickListener { startActivity(Intent(context, DiaconnG8HistoryActivity::class.java)) }
        binding.stats.setOnClickListener { startActivity(Intent(context, uiInteraction.tddStatsActivity)) }
        binding.userOptions.setOnClickListener { startActivity(Intent(context, DiaconnG8UserOptionsActivity::class.java)) }
        binding.btconnection.setOnClickListener {
            aapsLogger.debug(LTag.PUMP, "Clicked connect to pump")
            diaconnG8Pump.lastConnection = 0
            commandQueue.readStatus(rh.gs(info.nightscout.core.ui.R.string.clicked_connect_to_pump), null)
        }
    }

    @Synchronized
    override fun onResume() {
        super.onResume()
        handler.postDelayed(refreshLoop, T.mins(1).msecs())
        disposable += rxBus
            .toObservable(EventInitializationChanged::class.java)
            .observeOn(aapsSchedulers.main)
            .subscribe({ updateGUI() }, fabricPrivacy::logException)
        disposable += rxBus
            .toObservable(EventDiaconnG8NewStatus::class.java)
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
            .toObservable(EventQueueChanged::class.java)
            .observeOn(aapsSchedulers.main)
            .subscribe({ updateGUI() }, fabricPrivacy::logException)
        disposable += rxBus
            .toObservable(EventPumpStatusChanged::class.java)
            .observeOn(aapsSchedulers.main)
            .subscribe({
                           when (it.status) {
                               EventPumpStatusChanged.Status.CONNECTING   ->
                                   @Suppress("SetTextI18n")
                                   binding.btconnection.text = "{fa-bluetooth-b spin} ${it.secondsElapsed}s"
                               EventPumpStatusChanged.Status.CONNECTED    ->
                                   @Suppress("SetTextI18n")
                                   binding.btconnection.text = "{fa-bluetooth}"
                               EventPumpStatusChanged.Status.DISCONNECTED ->
                                   @Suppress("SetTextI18n")
                                   binding.btconnection.text = "{fa-bluetooth-b}"

                               else                                       -> {}
                           }
                           if (it.getStatus(requireContext()) != "") {
                               binding.diaconnG8Pumpstatus.text = it.getStatus(requireContext())
                               binding.diaconnG8Pumpstatuslayout.visibility = View.VISIBLE
                           } else {
                               binding.diaconnG8Pumpstatuslayout.visibility = View.GONE
                           }
                       }, fabricPrivacy::logException)
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
    fun updateGUI() {
        if (_binding == null) return
        val pump = diaconnG8Pump
        val plugin: Pump = activePlugin.activePump
        if (pump.lastConnection != 0L) {
            val agoMsec = System.currentTimeMillis() - pump.lastConnection
            val agoMin = (agoMsec.toDouble() / 60.0 / 1000.0).toInt()
            binding.lastconnection.text = dateUtil.timeString(pump.lastConnection) + " (" + rh.gs(info.nightscout.shared.R.string.minago, agoMin) + ")"
            warnColors.setColor(binding.lastconnection, agoMin.toDouble(), 16.0, 31.0)
        }
        if (pump.lastBolusTime != 0L) {
            val agoMsec = System.currentTimeMillis() - pump.lastBolusTime
            val agoHours = agoMsec.toDouble() / 60.0 / 60.0 / 1000.0
            if (agoHours < 6)
            // max 6h back
                binding.lastbolus.text = dateUtil.timeString(pump.lastBolusTime) + " " + dateUtil.sinceString(pump.lastBolusTime, rh) + " " + rh.gs(info.nightscout.interfaces.R.string.format_insulin_units, pump.lastBolusAmount)
            else
                binding.lastbolus.text = ""
        }

        val todayInsulinAmount = (pump.todayBaseAmount + pump.todaySnackAmount + pump.todayMealAmount)
        val todayInsulinLimitAmount = (pump.maxBasal.toInt() * 24) + pump.maxBolusePerDay.toInt()
        binding.dailyunits.text = rh.gs(info.nightscout.core.ui.R.string.reservoir_value, todayInsulinAmount, todayInsulinLimitAmount)
        warnColors.setColor(binding.dailyunits, todayInsulinAmount, todayInsulinLimitAmount * 0.75, todayInsulinLimitAmount * 0.9)
        binding.basabasalrate.text = pump.baseInjAmount.toString() + " / " + rh.gs(info.nightscout.core.ui.R.string.pump_base_basal_rate, plugin.baseBasalRate)

        binding.tempbasal.text = diaconnG8Pump.temporaryBasalToString()
        binding.extendedbolus.text = diaconnG8Pump.extendedBolusToString()
        binding.reservoir.text = rh.gs(info.nightscout.core.ui.R.string.reservoir_value, pump.systemRemainInsulin, 307)
        warnColors.setColorInverse(binding.reservoir, pump.systemRemainInsulin, 50.0, 20.0)
        binding.battery.text = "{fa-battery-" + pump.systemRemainBattery / 25 + "}" + " (" + pump.systemRemainBattery + " %)"
        warnColors.setColorInverse(binding.battery, pump.systemRemainBattery.toDouble(), 51.0, 26.0)
        binding.firmware.text = rh.gs(R.string.diaconn_g8_pump) + "\nVersion: " + pump.majorVersion.toString() + "." + pump.minorVersion.toString() + "\nCountry: " + pump.country.toString() + "\nProductType: " + pump.productType.toString() + "\nManufacture: " + pump.makeYear + "." + pump.makeMonth + "." + pump.makeDay
        binding.basalstep.text = pump.basalStep.toString()
        binding.bolusstep.text = pump.bolusStep.toString()
        binding.serialNumber.text = pump.serialNo.toString()
        val status = commandQueue.spannedStatus()
        if (status.toString() == "") {
            binding.queue.visibility = View.GONE
        } else {
            binding.queue.visibility = View.VISIBLE
            binding.queue.text = status
        }

        binding.userOptions.visibility = View.VISIBLE
    }
}
