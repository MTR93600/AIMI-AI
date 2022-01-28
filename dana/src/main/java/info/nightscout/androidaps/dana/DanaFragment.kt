package info.nightscout.androidaps.dana

import android.annotation.SuppressLint
import android.content.Intent
import android.os.Bundle
import android.os.Handler
import android.os.HandlerThread
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.content.ContextCompat
import dagger.android.support.DaggerFragment
import info.nightscout.androidaps.activities.TDDStatsActivity
import info.nightscout.androidaps.dana.activities.DanaHistoryActivity
import info.nightscout.androidaps.dana.activities.DanaUserOptionsActivity
import info.nightscout.androidaps.dana.databinding.DanarFragmentBinding
import info.nightscout.androidaps.dana.events.EventDanaRNewStatus
import info.nightscout.androidaps.dialogs.ProfileViewerDialog
import info.nightscout.androidaps.events.EventExtendedBolusChange
import info.nightscout.androidaps.events.EventInitializationChanged
import info.nightscout.androidaps.events.EventPumpStatusChanged
import info.nightscout.androidaps.events.EventTempBasalChange
import info.nightscout.androidaps.interfaces.ActivePlugin
import info.nightscout.androidaps.interfaces.CommandQueue
import info.nightscout.androidaps.interfaces.Pump
import info.nightscout.shared.logging.AAPSLogger
import info.nightscout.shared.logging.LTag
import info.nightscout.androidaps.logging.UserEntryLogger
import info.nightscout.androidaps.plugins.bus.RxBus
import info.nightscout.androidaps.plugins.pump.common.defs.PumpType
import info.nightscout.androidaps.queue.events.EventQueueChanged
import info.nightscout.androidaps.utils.DateUtil
import info.nightscout.androidaps.utils.FabricPrivacy
import info.nightscout.androidaps.utils.T
import info.nightscout.androidaps.utils.userEntry.UserEntryMapper.Action
import info.nightscout.androidaps.utils.userEntry.UserEntryMapper.Sources
import info.nightscout.androidaps.utils.WarnColors
import info.nightscout.androidaps.utils.alertDialogs.OKDialog
import info.nightscout.androidaps.extensions.toVisibility
import info.nightscout.androidaps.interfaces.Dana
import info.nightscout.androidaps.utils.resources.ResourceHelper
import info.nightscout.androidaps.utils.rx.AapsSchedulers
import info.nightscout.shared.sharedPreferences.SP
import io.reactivex.disposables.CompositeDisposable
import io.reactivex.rxkotlin.plusAssign
import javax.inject.Inject

class DanaFragment : DaggerFragment() {

    @Inject lateinit var rxBus: RxBus
    @Inject lateinit var aapsLogger: AAPSLogger
    @Inject lateinit var fabricPrivacy: FabricPrivacy
    @Inject lateinit var commandQueue: CommandQueue
    @Inject lateinit var activePlugin: ActivePlugin
    @Inject lateinit var danaPump: DanaPump
    @Inject lateinit var rh: ResourceHelper
    @Inject lateinit var sp: SP
    @Inject lateinit var warnColors: WarnColors
    @Inject lateinit var dateUtil: DateUtil
    @Inject lateinit var aapsSchedulers: AapsSchedulers
    @Inject lateinit var uel: UserEntryLogger

    private var disposable: CompositeDisposable = CompositeDisposable()

    private val handler = Handler(HandlerThread(this::class.simpleName + "Handler").also { it.start() }.looper)
    private lateinit var refreshLoop: Runnable
    private var pumpStatus = ""
    private var pumpStatusIcon = "{fa-bluetooth-b}"

    private var _binding: DanarFragmentBinding? = null

    // This property is only valid between onCreateView and
    // onDestroyView.
    private val binding get() = _binding!!

    init {
        refreshLoop = Runnable {
            activity?.runOnUiThread { updateGUI() }
            handler.postDelayed(refreshLoop, T.mins(1).msecs())
        }
    }

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = DanarFragmentBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        binding.history.setOnClickListener { startActivity(Intent(context, DanaHistoryActivity::class.java)) }
        binding.viewProfile.setOnClickListener {
            val profile = danaPump.createConvertedProfile()?.getDefaultProfileJson()
                ?: return@setOnClickListener
            val profileName = danaPump.createConvertedProfile()?.getDefaultProfileName()
                ?: return@setOnClickListener
            ProfileViewerDialog().also { pvd ->
                pvd.arguments = Bundle().also { args ->
                    args.putLong("time", dateUtil.now())
                    args.putInt("mode", ProfileViewerDialog.Mode.CUSTOM_PROFILE.ordinal)
                    args.putString("customProfile", profile.toString())
                    args.putString("customProfileName", profileName)
                }

            }.show(childFragmentManager, "ProfileViewDialog")
        }
        binding.stats.setOnClickListener { startActivity(Intent(context, TDDStatsActivity::class.java)) }
        binding.userOptions.setOnClickListener { startActivity(Intent(context, DanaUserOptionsActivity::class.java)) }
        binding.btConnectionLayout.setOnClickListener {
            aapsLogger.debug(LTag.PUMP, "Clicked connect to pump")
            danaPump.reset()
            commandQueue.readStatus(rh.gs(R.string.clicked_connect_to_pump), null)
        }
        if (activePlugin.activePump.pumpDescription.pumpType == PumpType.DANA_RS ||
            activePlugin.activePump.pumpDescription.pumpType == PumpType.DANA_I
        )
            binding.btConnectionLayout.setOnLongClickListener {
                activity?.let {
                    OKDialog.showConfirmation(it, rh.gs(R.string.resetpairing)) {
                        uel.log(Action.CLEAR_PAIRING_KEYS, Sources.Dana)
                        (activePlugin.activePump as Dana).clearPairing()
                    }
                }
                true
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
            .toObservable(EventDanaRNewStatus::class.java)
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
                           pumpStatusIcon = when (it.status) {
                               EventPumpStatusChanged.Status.CONNECTING   ->
                                   "{fa-bluetooth-b spin} ${it.secondsElapsed}s"
                               EventPumpStatusChanged.Status.CONNECTED    ->
                                   "{fa-bluetooth}"
                               EventPumpStatusChanged.Status.DISCONNECTED ->
                                   "{fa-bluetooth-b}"

                               else                                       ->
                                   "{fa-bluetooth-b}"
                           }
                           binding.btConnection.text = pumpStatusIcon
                           pumpStatus = it.getStatus(rh)
                           binding.pumpStatus.text = pumpStatus
                           binding.pumpStatusLayout.visibility = (pumpStatus != "").toVisibility()
                       }, fabricPrivacy::logException)

        pumpStatus = ""
        pumpStatusIcon = "{fa-bluetooth-b}"
        updateGUI()
    }

    @Synchronized
    override fun onPause() {
        super.onPause()
        disposable.clear()
        handler.removeCallbacks(refreshLoop)
        pumpStatus = ""
        pumpStatusIcon = "{fa-bluetooth-b}"
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
        binding.btConnection.text = pumpStatusIcon
        binding.pumpStatus.text = pumpStatus
        binding.pumpStatusLayout.visibility = (pumpStatus != "").toVisibility()
        binding.queue.text = commandQueue.spannedStatus()
        binding.queueStatusLayout.visibility = (commandQueue.spannedStatus().toString() != "").toVisibility()
        val pump = danaPump
        val plugin: Pump = activePlugin.activePump
        if (pump.lastConnection != 0L) {
            val agoMilliseconds = System.currentTimeMillis() - pump.lastConnection
            val agoMin = (agoMilliseconds.toDouble() / 60.0 / 1000.0).toInt()
            binding.lastconnection.text = dateUtil.timeString(pump.lastConnection) + " (" + rh.gs(R.string.minago, agoMin) + ")"
            warnColors.setColor(binding.lastconnection, agoMin.toDouble(), 16.0, 31.0)
        }
        if (pump.lastBolusTime != 0L) {
            val agoMilliseconds = System.currentTimeMillis() - pump.lastBolusTime
            val agoHours = agoMilliseconds.toDouble() / 60.0 / 60.0 / 1000.0
            if (agoHours < 6)
            // max 6h back
                binding.lastbolus.text = dateUtil.timeString(pump.lastBolusTime) + " " + dateUtil.sinceString(pump.lastBolusTime, rh) + " " + rh.gs(R.string.formatinsulinunits, pump.lastBolusAmount)
            else
                binding.lastbolus.text = ""
        }

        binding.dailyunits.text = rh.gs(R.string.reservoirvalue, pump.dailyTotalUnits, pump.maxDailyTotalUnits)
        warnColors.setColor(binding.dailyunits, pump.dailyTotalUnits, pump.maxDailyTotalUnits * 0.75, pump.maxDailyTotalUnits * 0.9)
        binding.basabasalrate.text = "( " + (pump.activeProfile + 1) + " )  " + rh.gs(R.string.pump_basebasalrate, plugin.baseBasalRate)
        // DanaRPlugin, DanaRKoreanPlugin
        binding.tempbasal.text = danaPump.temporaryBasalToString()
        binding.extendedbolus.text = danaPump.extendedBolusToString()
        binding.reservoir.text = rh.gs(R.string.reservoirvalue, pump.reservoirRemainingUnits, 300)
        warnColors.setColorInverse(binding.reservoir, pump.reservoirRemainingUnits, 50.0, 20.0)
        binding.battery.text = "{fa-battery-" + pump.batteryRemaining / 25 + "}"
        warnColors.setColorInverse(binding.battery, pump.batteryRemaining.toDouble(), 51.0, 26.0)
        binding.firmware.text = rh.gs(R.string.dana_model, pump.modelFriendlyName(), pump.hwModel, pump.protocol, pump.productCode)
        binding.basalBolusStep.text = pump.basalStep.toString() + "/" + pump.bolusStep.toString()
        binding.serialNumber.text = pump.serialNumber
        val icon = if (danaPump.pumpType() == PumpType.DANA_I) R.drawable.ic_dana_i else R.drawable.ic_dana_rs
        binding.danaIcon.setImageDrawable(context?.let { ContextCompat.getDrawable(it, icon) })
        //hide user options button if not an RS pump or old firmware
        // also excludes pump with model 03 because of untested error
        binding.userOptions.visibility = (pump.hwModel != 1 && pump.protocol != 0x00).toVisibility()
    }
}
