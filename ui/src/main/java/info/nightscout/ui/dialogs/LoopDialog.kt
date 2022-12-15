package info.nightscout.ui.dialogs

import android.content.Context
import android.os.Bundle
import android.os.Handler
import android.os.HandlerThread
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.Window
import android.view.WindowManager
import androidx.fragment.app.FragmentManager
import dagger.android.support.DaggerDialogFragment
import info.nightscout.core.ui.dialogs.OKDialog
import info.nightscout.core.ui.toast.ToastUtils
import info.nightscout.core.utils.fabric.FabricPrivacy
import info.nightscout.database.entities.OfflineEvent
import info.nightscout.database.entities.UserEntry
import info.nightscout.database.entities.ValueWithUnit
import info.nightscout.database.impl.AppRepository
import info.nightscout.database.impl.transactions.CancelCurrentOfflineEventIfAnyTransaction
import info.nightscout.database.impl.transactions.InsertAndCancelCurrentOfflineEventTransaction
import info.nightscout.interfaces.ConfigBuilder
import info.nightscout.interfaces.ApsMode
import info.nightscout.interfaces.aps.Loop
import info.nightscout.interfaces.constraints.Constraint
import info.nightscout.interfaces.constraints.Constraints
import info.nightscout.interfaces.constraints.Objectives
import info.nightscout.interfaces.logging.UserEntryLogger
import info.nightscout.interfaces.plugin.ActivePlugin
import info.nightscout.interfaces.plugin.PluginBase
import info.nightscout.interfaces.plugin.PluginType
import info.nightscout.interfaces.profile.ProfileFunction
import info.nightscout.interfaces.protection.ProtectionCheck
import info.nightscout.interfaces.pump.defs.PumpDescription
import info.nightscout.interfaces.queue.Callback
import info.nightscout.interfaces.queue.CommandQueue
import info.nightscout.interfaces.ui.UiInteraction
import info.nightscout.rx.bus.RxBus
import info.nightscout.rx.events.EventPreferenceChange
import info.nightscout.rx.events.EventRefreshOverview
import info.nightscout.rx.logging.AAPSLogger
import info.nightscout.rx.logging.LTag
import info.nightscout.shared.extensions.runOnUiThread
import info.nightscout.shared.extensions.toVisibility
import info.nightscout.shared.interfaces.ResourceHelper
import info.nightscout.shared.sharedPreferences.SP
import info.nightscout.shared.utils.DateUtil
import info.nightscout.shared.utils.T
import info.nightscout.ui.R
import info.nightscout.ui.databinding.DialogLoopBinding
import io.reactivex.rxjava3.disposables.CompositeDisposable
import io.reactivex.rxjava3.kotlin.plusAssign
import javax.inject.Inject

class LoopDialog : DaggerDialogFragment() {

    @Inject lateinit var aapsLogger: AAPSLogger
    @Inject lateinit var ctx: Context
    @Inject lateinit var sp: SP
    @Inject lateinit var rxBus: RxBus
    @Inject lateinit var fabricPrivacy: FabricPrivacy
    @Inject lateinit var rh: ResourceHelper
    @Inject lateinit var profileFunction: ProfileFunction
    @Inject lateinit var loop: Loop
    @Inject lateinit var activePlugin: ActivePlugin
    @Inject lateinit var constraintChecker: Constraints
    @Inject lateinit var commandQueue: CommandQueue
    @Inject lateinit var configBuilder: ConfigBuilder
    @Inject lateinit var uel: UserEntryLogger
    @Inject lateinit var dateUtil: DateUtil
    @Inject lateinit var repository: AppRepository
    @Inject lateinit var protectionCheck: ProtectionCheck
    @Inject lateinit var uiInteraction: UiInteraction

    private var queryingProtection = false
    private var showOkCancel: Boolean = true
    private var _binding: DialogLoopBinding? = null
    private var handler = Handler(HandlerThread(this::class.simpleName + "Handler").also { it.start() }.looper)
    private lateinit var refreshDialog: Runnable

    // This property is only valid between onCreateView and onDestroyView.
    private val binding get() = _binding!!

    val disposable = CompositeDisposable()

    override fun onStart() {
        super.onStart()
        dialog?.window?.setLayout(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.WRAP_CONTENT
        )
    }

    override fun onSaveInstanceState(savedInstanceState: Bundle) {
        super.onSaveInstanceState(savedInstanceState)
        savedInstanceState.putInt("showOkCancel", if (showOkCancel) 1 else 0)
    }

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        // load data from bundle
        (savedInstanceState ?: arguments)?.let { bundle ->
            showOkCancel = bundle.getInt("showOkCancel", 1) == 1
        }
        dialog?.window?.requestFeature(Window.FEATURE_NO_TITLE)
        dialog?.window?.setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_STATE_HIDDEN)
        isCancelable = true
        dialog?.setCanceledOnTouchOutside(false)

        _binding = DialogLoopBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        updateGUI("LoopDialogOnViewCreated")

        binding.overviewCloseloop.setOnClickListener { if (showOkCancel) onClickOkCancelEnabled(it) else onClick(it); dismiss() }
        binding.overviewLgsloop.setOnClickListener { if (showOkCancel) onClickOkCancelEnabled(it) else onClick(it); dismiss() }
        binding.overviewOpenloop.setOnClickListener { if (showOkCancel) onClickOkCancelEnabled(it) else onClick(it); dismiss() }
        binding.overviewDisable.setOnClickListener { if (showOkCancel) onClickOkCancelEnabled(it) else onClick(it); dismiss() }
        binding.overviewEnable.setOnClickListener { if (showOkCancel) onClickOkCancelEnabled(it) else onClick(it); dismiss() }
        binding.overviewResume.setOnClickListener { if (showOkCancel) onClickOkCancelEnabled(it) else onClick(it); dismiss() }
        binding.overviewReconnect.setOnClickListener { if (showOkCancel) onClickOkCancelEnabled(it) else onClick(it); dismiss() }
        binding.overviewSuspend1h.setOnClickListener { if (showOkCancel) onClickOkCancelEnabled(it) else onClick(it); dismiss() }
        binding.overviewSuspend2h.setOnClickListener { if (showOkCancel) onClickOkCancelEnabled(it) else onClick(it); dismiss() }
        binding.overviewSuspend3h.setOnClickListener { if (showOkCancel) onClickOkCancelEnabled(it) else onClick(it); dismiss() }
        binding.overviewSuspend10h.setOnClickListener { if (showOkCancel) onClickOkCancelEnabled(it) else onClick(it); dismiss() }
        binding.overviewDisconnect15m.setOnClickListener { if (showOkCancel) onClickOkCancelEnabled(it) else onClick(it); dismiss() }
        binding.overviewDisconnect30m.setOnClickListener { if (showOkCancel) onClickOkCancelEnabled(it) else onClick(it); dismiss() }
        binding.overviewDisconnect1h.setOnClickListener { if (showOkCancel) onClickOkCancelEnabled(it) else onClick(it); dismiss() }
        binding.overviewDisconnect2h.setOnClickListener { if (showOkCancel) onClickOkCancelEnabled(it) else onClick(it); dismiss() }
        binding.overviewDisconnect3h.setOnClickListener { if (showOkCancel) onClickOkCancelEnabled(it) else onClick(it); dismiss() }

        // cancel button
        binding.cancel.setOnClickListener { dismiss() }

        refreshDialog = Runnable {
            runOnUiThread { updateGUI("refreshDialog") }
            handler.postDelayed(refreshDialog, 15 * 1000L)
        }
        handler.postDelayed(refreshDialog, 15 * 1000L)
    }

    @Synchronized
    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
        handler.removeCallbacksAndMessages(null)
        disposable.clear()
    }

    var task: Runnable? = null

    @Synchronized
    fun updateGUI(from: String) {
        if (_binding == null) return
        aapsLogger.debug("UpdateGUI from $from")
        val pumpDescription: PumpDescription = activePlugin.activePump.pumpDescription
        val closedLoopAllowed = constraintChecker.isClosedLoopAllowed(Constraint(true))
        val closedLoopAllowed2 = activePlugin.activeObjectives?.isAccomplished(Objectives.MAXIOB_OBJECTIVE) ?: false
        val lgsEnabled = constraintChecker.isLgsAllowed(Constraint(true))
        val apsMode = ApsMode.fromString(sp.getString(info.nightscout.core.utils.R.string.key_aps_mode, ApsMode.OPEN.name))
        val pump = activePlugin.activePump

        binding.overviewDisconnect15m.visibility = pumpDescription.tempDurationStep15mAllowed.toVisibility()
        binding.overviewDisconnect30m.visibility = pumpDescription.tempDurationStep30mAllowed.toVisibility()
        when {
            pump.isSuspended()                                     -> {
                binding.overviewLoop.visibility = View.GONE
                binding.overviewSuspend.visibility = View.GONE
                binding.overviewPump.visibility = View.GONE
            }

            !profileFunction.isProfileValid("LoopDialogUpdateGUI") -> {
                binding.overviewLoop.visibility = View.GONE
                binding.overviewSuspend.visibility = View.GONE
                binding.overviewPump.visibility = View.GONE
            }

            loop.isDisconnected                                    -> {
                binding.overviewLoop.visibility = View.GONE
                binding.overviewSuspend.visibility = View.GONE
                binding.overviewPump.visibility = View.VISIBLE
                binding.overviewPumpHeader.text = rh.gs(R.string.reconnect)
                binding.overviewDisconnectButtons.visibility = View.VISIBLE
                binding.overviewReconnect.visibility = View.VISIBLE
            }

            !(loop as PluginBase).isEnabled()                      -> {
                binding.overviewLoop.visibility = View.VISIBLE
                binding.overviewEnable.visibility = View.VISIBLE
                binding.overviewDisable.visibility = View.GONE
                binding.overviewSuspend.visibility = View.GONE
                binding.overviewPump.visibility = View.VISIBLE
                binding.overviewReconnect.visibility = View.GONE
            }

            loop.isSuspended                                       -> {
                binding.overviewLoop.visibility = View.GONE
                binding.overviewSuspend.visibility = View.VISIBLE
                binding.overviewSuspendHeader.text = rh.gs(info.nightscout.core.ui.R.string.resumeloop)
                binding.overviewSuspendButtons.visibility = View.VISIBLE
                binding.overviewResume.visibility = View.VISIBLE
                binding.overviewPump.visibility = View.GONE
                binding.overviewReconnect.visibility = View.GONE
            }

            else                                                   -> {
                binding.overviewLoop.visibility = View.VISIBLE
                binding.overviewEnable.visibility = View.GONE
                when (apsMode) {
                    ApsMode.CLOSED -> {
                        binding.overviewCloseloop.visibility = View.GONE
                        binding.overviewLgsloop.visibility = View.VISIBLE
                        binding.overviewOpenloop.visibility = View.VISIBLE
                    }

                    ApsMode.LGS    -> {
                        binding.overviewCloseloop.visibility = closedLoopAllowed.value().toVisibility()   //show Close loop button only if Close loop allowed
                        binding.overviewLgsloop.visibility = View.GONE
                        binding.overviewOpenloop.visibility = View.VISIBLE
                    }

                    ApsMode.OPEN   -> {
                        binding.overviewCloseloop.visibility =
                            closedLoopAllowed2.toVisibility()          //show CloseLoop button only if Objective 6 is completed (closedLoopAllowed always false in open loop mode)
                        binding.overviewLgsloop.visibility = lgsEnabled.value().toVisibility()
                        binding.overviewOpenloop.visibility = View.GONE
                    }

                    else           -> {
                        binding.overviewCloseloop.visibility = View.GONE
                        binding.overviewLgsloop.visibility = View.GONE
                        binding.overviewOpenloop.visibility = View.GONE
                    }
                }
                binding.overviewSuspend.visibility = View.VISIBLE
                binding.overviewSuspendHeader.text = rh.gs(info.nightscout.core.ui.R.string.suspendloop)
                binding.overviewSuspendButtons.visibility = View.VISIBLE
                binding.overviewResume.visibility = View.GONE

                binding.overviewPump.visibility = View.VISIBLE
                binding.overviewPumpHeader.text = rh.gs(R.string.disconnectpump)
                binding.overviewDisconnectButtons.visibility = View.VISIBLE
                binding.overviewReconnect.visibility = View.GONE

            }
        }
    }

    private fun onClickOkCancelEnabled(v: View): Boolean {
        var description = ""
        when (v.id) {
            R.id.overview_closeloop      -> description = rh.gs(info.nightscout.core.ui.R.string.closedloop)
            R.id.overview_lgsloop        -> description = rh.gs(info.nightscout.core.ui.R.string.lowglucosesuspend)
            R.id.overview_openloop       -> description = rh.gs(info.nightscout.core.ui.R.string.openloop)
            R.id.overview_disable        -> description = rh.gs(info.nightscout.core.ui.R.string.disableloop)
            R.id.overview_enable         -> description = rh.gs(info.nightscout.core.ui.R.string.enableloop)
            R.id.overview_resume         -> description = rh.gs(R.string.resume)
            R.id.overview_reconnect      -> description = rh.gs(R.string.reconnect)
            R.id.overview_suspend_1h     -> description = rh.gs(R.string.suspendloopfor1h)
            R.id.overview_suspend_2h     -> description = rh.gs(R.string.suspendloopfor2h)
            R.id.overview_suspend_3h     -> description = rh.gs(R.string.suspendloopfor3h)
            R.id.overview_suspend_10h    -> description = rh.gs(R.string.suspendloopfor10h)
            R.id.overview_disconnect_15m -> description = rh.gs(R.string.disconnectpumpfor15m)
            R.id.overview_disconnect_30m -> description = rh.gs(R.string.disconnectpumpfor30m)
            R.id.overview_disconnect_1h  -> description = rh.gs(R.string.disconnectpumpfor1h)
            R.id.overview_disconnect_2h  -> description = rh.gs(R.string.disconnectpumpfor2h)
            R.id.overview_disconnect_3h  -> description = rh.gs(R.string.disconnectpumpfor3h)
        }
        activity?.let { activity ->
            OKDialog.showConfirmation(activity, rh.gs(info.nightscout.core.ui.R.string.confirm), description, Runnable {
                onClick(v)
            })
        }
        return true
    }

    fun onClick(v: View): Boolean {
        when (v.id) {
            R.id.overview_closeloop                       -> {
                uel.log(UserEntry.Action.CLOSED_LOOP_MODE, UserEntry.Sources.LoopDialog)
                sp.putString(info.nightscout.core.utils.R.string.key_aps_mode, ApsMode.CLOSED.name)
                rxBus.send(EventPreferenceChange(rh.gs(info.nightscout.core.ui.R.string.closedloop)))
                return true
            }

            R.id.overview_lgsloop                         -> {
                uel.log(UserEntry.Action.LGS_LOOP_MODE, UserEntry.Sources.LoopDialog)
                sp.putString(info.nightscout.core.utils.R.string.key_aps_mode, ApsMode.LGS.name)
                rxBus.send(EventPreferenceChange(rh.gs(info.nightscout.core.ui.R.string.lowglucosesuspend)))
                return true
            }

            R.id.overview_openloop                        -> {
                uel.log(UserEntry.Action.OPEN_LOOP_MODE, UserEntry.Sources.LoopDialog)
                sp.putString(info.nightscout.core.utils.R.string.key_aps_mode, ApsMode.OPEN.name)
                rxBus.send(EventPreferenceChange(rh.gs(info.nightscout.core.ui.R.string.lowglucosesuspend)))
                return true
            }

            R.id.overview_disable                         -> {
                uel.log(UserEntry.Action.LOOP_DISABLED, UserEntry.Sources.LoopDialog)
                (loop as PluginBase).setPluginEnabled(PluginType.LOOP, false)
                (loop as PluginBase).setFragmentVisible(PluginType.LOOP, false)
                configBuilder.storeSettings("DisablingLoop")
                rxBus.send(EventRefreshOverview("suspend_menu"))
                commandQueue.cancelTempBasal(true, object : Callback() {
                    override fun run() {
                        if (!result.success) {
                            ToastUtils.errorToast(ctx, rh.gs(info.nightscout.core.ui.R.string.temp_basal_delivery_error))
                        }
                    }
                })
                disposable += repository.runTransactionForResult(InsertAndCancelCurrentOfflineEventTransaction(dateUtil.now(), T.days(365).msecs(), OfflineEvent.Reason.DISABLE_LOOP))
                    .subscribe({ result ->
                                   result.updated.forEach { aapsLogger.debug(LTag.DATABASE, "Updated OfflineEvent $it") }
                                   result.inserted.forEach { aapsLogger.debug(LTag.DATABASE, "Inserted OfflineEvent $it") }
                               }, {
                                   aapsLogger.error(LTag.DATABASE, "Error while saving OfflineEvent", it)
                               })
                return true
            }

            R.id.overview_enable                          -> {
                uel.log(UserEntry.Action.LOOP_ENABLED, UserEntry.Sources.LoopDialog)
                (loop as PluginBase).setPluginEnabled(PluginType.LOOP, true)
                (loop as PluginBase).setFragmentVisible(PluginType.LOOP, true)
                configBuilder.storeSettings("EnablingLoop")
                rxBus.send(EventRefreshOverview("suspend_menu"))
                disposable += repository.runTransactionForResult(CancelCurrentOfflineEventIfAnyTransaction(dateUtil.now()))
                    .subscribe({ result ->
                                   result.updated.forEach { aapsLogger.debug(LTag.DATABASE, "Updated OfflineEvent $it") }
                               }, {
                                   aapsLogger.error(LTag.DATABASE, "Error while saving OfflineEvent", it)
                               })
                return true
            }

            R.id.overview_resume, R.id.overview_reconnect -> {
                uel.log(if (v.id == R.id.overview_resume) UserEntry.Action.RESUME else UserEntry.Action.RECONNECT, UserEntry.Sources.LoopDialog)
                disposable += repository.runTransactionForResult(CancelCurrentOfflineEventIfAnyTransaction(dateUtil.now()))
                    .subscribe({ result ->
                                   result.updated.forEach { aapsLogger.debug(LTag.DATABASE, "Updated OfflineEvent $it") }
                               }, {
                                   aapsLogger.error(LTag.DATABASE, "Error while saving OfflineEvent", it)
                               })
                rxBus.send(EventRefreshOverview("suspend_menu"))
                commandQueue.cancelTempBasal(true, object : Callback() {
                    override fun run() {
                        if (!result.success) {
                            uiInteraction.runAlarm(result.comment, rh.gs(info.nightscout.core.ui.R.string.temp_basal_delivery_error), info.nightscout.core.ui.R.raw.boluserror)
                        }
                    }
                })
                sp.putBoolean(info.nightscout.core.utils.R.string.key_objectiveusereconnect, true)
                return true
            }

            R.id.overview_suspend_1h                      -> {
                uel.log(UserEntry.Action.SUSPEND, UserEntry.Sources.LoopDialog, ValueWithUnit.Hour(1))
                loop.suspendLoop(T.hours(1).mins().toInt())
                rxBus.send(EventRefreshOverview("suspend_menu"))
                return true
            }

            R.id.overview_suspend_2h                      -> {
                uel.log(UserEntry.Action.SUSPEND, UserEntry.Sources.LoopDialog, ValueWithUnit.Hour(2))
                loop.suspendLoop(T.hours(2).mins().toInt())
                rxBus.send(EventRefreshOverview("suspend_menu"))
                return true
            }

            R.id.overview_suspend_3h                      -> {
                uel.log(UserEntry.Action.SUSPEND, UserEntry.Sources.LoopDialog, ValueWithUnit.Hour(3))
                loop.suspendLoop(T.hours(3).mins().toInt())
                rxBus.send(EventRefreshOverview("suspend_menu"))
                return true
            }

            R.id.overview_suspend_10h                     -> {
                uel.log(UserEntry.Action.SUSPEND, UserEntry.Sources.LoopDialog, ValueWithUnit.Hour(10))
                loop.suspendLoop(T.hours(10).mins().toInt())
                rxBus.send(EventRefreshOverview("suspend_menu"))
                return true
            }

            R.id.overview_disconnect_15m                  -> {
                profileFunction.getProfile()?.let { profile ->
                    uel.log(UserEntry.Action.DISCONNECT, UserEntry.Sources.LoopDialog, ValueWithUnit.Minute(15))
                    loop.goToZeroTemp(T.mins(15).mins().toInt(), profile, OfflineEvent.Reason.DISCONNECT_PUMP)
                    rxBus.send(EventRefreshOverview("suspend_menu"))
                }
                return true
            }

            R.id.overview_disconnect_30m                  -> {
                profileFunction.getProfile()?.let { profile ->
                    uel.log(UserEntry.Action.DISCONNECT, UserEntry.Sources.LoopDialog, ValueWithUnit.Minute(30))
                    loop.goToZeroTemp(T.mins(30).mins().toInt(), profile, OfflineEvent.Reason.DISCONNECT_PUMP)
                    rxBus.send(EventRefreshOverview("suspend_menu"))
                }
                return true
            }

            R.id.overview_disconnect_1h                   -> {
                profileFunction.getProfile()?.let { profile ->
                    uel.log(UserEntry.Action.DISCONNECT, UserEntry.Sources.LoopDialog, ValueWithUnit.Hour(1))
                    loop.goToZeroTemp(T.hours(1).mins().toInt(), profile, OfflineEvent.Reason.DISCONNECT_PUMP)
                    rxBus.send(EventRefreshOverview("suspend_menu"))
                }
                sp.putBoolean(info.nightscout.core.utils.R.string.key_objectiveusedisconnect, true)
                return true
            }

            R.id.overview_disconnect_2h                   -> {
                profileFunction.getProfile()?.let { profile ->
                    uel.log(UserEntry.Action.DISCONNECT, UserEntry.Sources.LoopDialog, ValueWithUnit.Hour(2))
                    loop.goToZeroTemp(T.hours(2).mins().toInt(), profile, OfflineEvent.Reason.DISCONNECT_PUMP)
                    rxBus.send(EventRefreshOverview("suspend_menu"))
                }
                return true
            }

            R.id.overview_disconnect_3h                   -> {
                profileFunction.getProfile()?.let { profile ->
                    uel.log(UserEntry.Action.DISCONNECT, UserEntry.Sources.LoopDialog, ValueWithUnit.Hour(3))
                    loop.goToZeroTemp(T.hours(3).mins().toInt(), profile, OfflineEvent.Reason.DISCONNECT_PUMP)
                    rxBus.send(EventRefreshOverview("suspend_menu"))
                }
                return true
            }
        }
        return false
    }

    override fun show(manager: FragmentManager, tag: String?) {
        try {
            manager.beginTransaction().let {
                it.add(this, tag)
                it.commitAllowingStateLoss()
            }
        } catch (e: IllegalStateException) {
            aapsLogger.debug(e.localizedMessage ?: e.toString())
        }
    }

    override fun onResume() {
        super.onResume()
        if (!queryingProtection) {
            queryingProtection = true
            activity?.let { activity ->
                val cancelFail = {
                    queryingProtection = false
                    aapsLogger.debug(LTag.APS, "Dialog canceled on resume protection: ${this.javaClass.simpleName}")
                    ToastUtils.warnToast(ctx, R.string.dialog_canceled)
                    dismiss()
                }
                protectionCheck.queryProtection(activity, ProtectionCheck.Protection.BOLUS, { queryingProtection = false }, cancelFail, cancelFail)
            }
        }
    }
}