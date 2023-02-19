package info.nightscout.ui.dialogs

import android.content.Context
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import com.google.common.base.Joiner
import info.nightscout.core.ui.dialogs.OKDialog
import info.nightscout.core.ui.toast.ToastUtils
import info.nightscout.core.utils.extensions.formatColor
import info.nightscout.database.entities.TherapyEvent
import info.nightscout.database.entities.UserEntry
import info.nightscout.database.entities.ValueWithUnit
import info.nightscout.database.impl.AppRepository
import info.nightscout.database.impl.transactions.InsertIfNewByTimestampTherapyEventTransaction
import info.nightscout.interfaces.constraints.Constraint
import info.nightscout.interfaces.constraints.Constraints
import info.nightscout.interfaces.logging.UserEntryLogger
import info.nightscout.interfaces.plugin.ActivePlugin
import info.nightscout.interfaces.protection.ProtectionCheck
import info.nightscout.interfaces.pump.DetailedBolusInfo
import info.nightscout.interfaces.queue.Callback
import info.nightscout.interfaces.queue.CommandQueue
import info.nightscout.interfaces.ui.UiInteraction
import info.nightscout.interfaces.utils.DecimalFormatter
import info.nightscout.interfaces.utils.HtmlHelper
import info.nightscout.rx.logging.LTag
import info.nightscout.shared.SafeParse
import info.nightscout.shared.interfaces.ResourceHelper
import info.nightscout.ui.R
import info.nightscout.ui.databinding.DialogFillBinding
import io.reactivex.rxjava3.disposables.CompositeDisposable
import io.reactivex.rxjava3.kotlin.plusAssign
import java.util.LinkedList
import javax.inject.Inject
import kotlin.math.abs

class FillDialog : DialogFragmentWithDate() {

    @Inject lateinit var constraintChecker: Constraints
    @Inject lateinit var rh: ResourceHelper
    @Inject lateinit var ctx: Context
    @Inject lateinit var commandQueue: CommandQueue
    @Inject lateinit var activePlugin: ActivePlugin
    @Inject lateinit var uel: UserEntryLogger
    @Inject lateinit var repository: AppRepository
    @Inject lateinit var protectionCheck: ProtectionCheck
    @Inject lateinit var uiInteraction: UiInteraction

    private var queryingProtection = false
    private val disposable = CompositeDisposable()
    private var _binding: DialogFillBinding? = null

    // This property is only valid between onCreateView and onDestroyView.
    private val binding get() = _binding!!

    override fun onSaveInstanceState(savedInstanceState: Bundle) {
        super.onSaveInstanceState(savedInstanceState)
        savedInstanceState.putDouble("fill_insulin_amount", binding.fillInsulinAmount.value)
    }

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        onCreateViewGeneral()
        _binding = DialogFillBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        val maxInsulin = constraintChecker.getMaxBolusAllowed().value()
        val bolusStep = activePlugin.activePump.pumpDescription.bolusStep
        binding.fillInsulinAmount.setParams(
            savedInstanceState?.getDouble("fill_insulin_amount")
                ?: 0.0, 0.0, maxInsulin, bolusStep, DecimalFormatter.pumpSupportedBolusFormat(activePlugin.activePump), true, binding.okcancel.ok
        )
        val amount1 = sp.getDouble("fill_button1", 0.3)
        if (amount1 > 0) {
            binding.fillPresetButton1.visibility = View.VISIBLE
            binding.fillPresetButton1.text = DecimalFormatter.toPumpSupportedBolus(amount1, activePlugin.activePump) // + "U");
            binding.fillPresetButton1.setOnClickListener { binding.fillInsulinAmount.value = amount1 }
        } else {
            binding.fillPresetButton1.visibility = View.GONE
        }
        val amount2 = sp.getDouble("fill_button2", 0.0)
        if (amount2 > 0) {
            binding.fillPresetButton2.visibility = View.VISIBLE
            binding.fillPresetButton2.text = DecimalFormatter.toPumpSupportedBolus(amount2, activePlugin.activePump) // + "U");
            binding.fillPresetButton2.setOnClickListener { binding.fillInsulinAmount.value = amount2 }
        } else {
            binding.fillPresetButton2.visibility = View.GONE
        }
        val amount3 = sp.getDouble("fill_button3", 0.0)
        if (amount3 > 0) {
            binding.fillPresetButton3.visibility = View.VISIBLE
            binding.fillPresetButton3.text = DecimalFormatter.toPumpSupportedBolus(amount3, activePlugin.activePump) // + "U");
            binding.fillPresetButton3.setOnClickListener { binding.fillInsulinAmount.value = amount3 }
        } else {
            binding.fillPresetButton3.visibility = View.GONE
        }
        binding.fillLabel.labelFor = binding.fillInsulinAmount.editTextId
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

    override fun submit(): Boolean {
        if (_binding == null) return false
        val insulin = SafeParse.stringToDouble(binding.fillInsulinAmount.text)
        val actions: LinkedList<String?> = LinkedList()

        val insulinAfterConstraints = constraintChecker.applyBolusConstraints(Constraint(insulin)).value()
        if (insulinAfterConstraints > 0) {
            actions.add(rh.gs(R.string.fill_warning))
            actions.add("")
            actions.add(rh.gs(info.nightscout.core.ui.R.string.bolus) + ": " + DecimalFormatter.toPumpSupportedBolus(insulinAfterConstraints, activePlugin.activePump, rh).formatColor(context, rh, info.nightscout.core.ui.R.attr.insulinButtonColor))
            if (abs(insulinAfterConstraints - insulin) > 0.01)
                actions.add(rh.gs(info.nightscout.core.ui.R.string.bolus_constraint_applied_warn, insulin, insulinAfterConstraints).formatColor(context, rh, info.nightscout.core.ui.R.attr.warningColor))
        }
        val siteChange = binding.fillCatheterChange.isChecked
        if (siteChange)
            actions.add(rh.gs(R.string.record_pump_site_change).formatColor(context, rh, info.nightscout.core.ui.R.attr.actionsConfirmColor))
        val insulinChange = binding.fillCartridgeChange.isChecked
        if (insulinChange)
            actions.add(rh.gs(R.string.record_insulin_cartridge_change).formatColor(context, rh, info.nightscout.core.ui.R.attr.actionsConfirmColor))
        val notes: String = binding.notesLayout.notes.text.toString()
        if (notes.isNotEmpty())
            actions.add(rh.gs(info.nightscout.core.ui.R.string.notes_label) + ": " + notes)
        eventTime -= eventTime % 1000

        if (eventTimeChanged)
            actions.add(rh.gs(info.nightscout.core.ui.R.string.time) + ": " + dateUtil.dateAndTimeString(eventTime))

        if (insulinAfterConstraints > 0 || binding.fillCatheterChange.isChecked || binding.fillCartridgeChange.isChecked) {
            activity?.let { activity ->
                OKDialog.showConfirmation(activity, rh.gs(info.nightscout.core.ui.R.string.prime_fill), HtmlHelper.fromHtml(Joiner.on("<br/>").join(actions)), {
                    if (insulinAfterConstraints > 0) {
                        uel.log(
                            UserEntry.Action.PRIME_BOLUS, UserEntry.Sources.FillDialog,
                            notes,
                            ValueWithUnit.Insulin(insulinAfterConstraints)
                        )
                        requestPrimeBolus(insulinAfterConstraints, notes)
                    }
                    if (siteChange) {
                        uel.log(
                            UserEntry.Action.SITE_CHANGE, UserEntry.Sources.FillDialog,
                            notes,
                            ValueWithUnit.Timestamp(eventTime).takeIf { eventTimeChanged },
                            ValueWithUnit.TherapyEventType(TherapyEvent.Type.CANNULA_CHANGE)
                        )
                        disposable += repository.runTransactionForResult(
                            InsertIfNewByTimestampTherapyEventTransaction(
                                timestamp = eventTime,
                                type = TherapyEvent.Type.CANNULA_CHANGE,
                                note = notes,
                                glucoseUnit = TherapyEvent.GlucoseUnit.MGDL
                            )
                        ).subscribe(
                            { result -> result.inserted.forEach { aapsLogger.debug(LTag.DATABASE, "Inserted therapy event $it") } },
                            { aapsLogger.error(LTag.DATABASE, "Error while saving therapy event", it) }
                        )
                    }
                    if (insulinChange) {
                        // add a second for case of both checked
                        uel.log(
                            UserEntry.Action.RESERVOIR_CHANGE, UserEntry.Sources.FillDialog,
                            notes,
                            ValueWithUnit.Timestamp(eventTime).takeIf { eventTimeChanged },
                            ValueWithUnit.TherapyEventType(TherapyEvent.Type.INSULIN_CHANGE)
                        )
                        disposable += repository.runTransactionForResult(
                            InsertIfNewByTimestampTherapyEventTransaction(
                                timestamp = eventTime + 1000,
                                type = TherapyEvent.Type.INSULIN_CHANGE,
                                note = notes,
                                glucoseUnit = TherapyEvent.GlucoseUnit.MGDL
                            )
                        ).subscribe(
                            { result -> result.inserted.forEach { aapsLogger.debug(LTag.DATABASE, "Inserted therapy event $it") } },
                            { aapsLogger.error(LTag.DATABASE, "Error while saving therapy event", it) }
                        )
                    }
                }, null)
            }
        } else {
            activity?.let { activity ->
                OKDialog.show(activity, rh.gs(info.nightscout.core.ui.R.string.prime_fill), rh.gs(info.nightscout.core.ui.R.string.no_action_selected))
            }
        }
        dismiss()
        return true
    }

    private fun requestPrimeBolus(insulin: Double, notes: String) {
        val detailedBolusInfo = DetailedBolusInfo()
        detailedBolusInfo.insulin = insulin
        detailedBolusInfo.context = context
        detailedBolusInfo.bolusType = DetailedBolusInfo.BolusType.PRIMING
        detailedBolusInfo.notes = notes
        aapsLogger.info(LTag.PUMPQUEUE, "bolusing filldialog")
        commandQueue.bolus(detailedBolusInfo, object : Callback() {
            override fun run() {
                if (!result.success) {
                    uiInteraction.runAlarm(result.comment, rh.gs(info.nightscout.core.ui.R.string.treatmentdeliveryerror), info.nightscout.core.ui.R.raw.boluserror)
                }
            }
        })
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