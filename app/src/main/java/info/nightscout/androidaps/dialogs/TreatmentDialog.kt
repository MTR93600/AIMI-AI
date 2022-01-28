package info.nightscout.androidaps.dialogs

import android.content.Context
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import com.google.common.base.Joiner
import info.nightscout.androidaps.interfaces.Config
import info.nightscout.androidaps.R
import info.nightscout.androidaps.activities.ErrorHelperActivity
import info.nightscout.androidaps.data.DetailedBolusInfo
import info.nightscout.androidaps.database.AppRepository
import info.nightscout.androidaps.database.entities.ValueWithUnit
import info.nightscout.androidaps.database.entities.UserEntry.Action
import info.nightscout.androidaps.database.entities.UserEntry.Sources
import info.nightscout.androidaps.databinding.DialogTreatmentBinding
import info.nightscout.androidaps.interfaces.ActivePlugin
import info.nightscout.androidaps.interfaces.CommandQueue
import info.nightscout.androidaps.interfaces.Constraint
import info.nightscout.shared.logging.LTag
import info.nightscout.androidaps.logging.UserEntryLogger
import info.nightscout.androidaps.plugins.configBuilder.ConstraintChecker
import info.nightscout.androidaps.queue.Callback
import info.nightscout.androidaps.utils.DecimalFormatter
import info.nightscout.androidaps.utils.HtmlHelper
import info.nightscout.shared.SafeParse
import info.nightscout.androidaps.utils.ToastUtils
import info.nightscout.androidaps.utils.alertDialogs.OKDialog
import info.nightscout.androidaps.extensions.formatColor
import info.nightscout.androidaps.utils.resources.ResourceHelper
import io.reactivex.disposables.CompositeDisposable
import io.reactivex.rxkotlin.plusAssign
import java.text.DecimalFormat
import java.util.*
import javax.inject.Inject
import kotlin.math.abs

class TreatmentDialog : DialogFragmentWithDate() {

    @Inject lateinit var constraintChecker: ConstraintChecker
    @Inject lateinit var rh: ResourceHelper
    @Inject lateinit var activePlugin: ActivePlugin
    @Inject lateinit var commandQueue: CommandQueue
    @Inject lateinit var ctx: Context
    @Inject lateinit var config: Config
    @Inject lateinit var uel: UserEntryLogger
    @Inject lateinit var repository: AppRepository

    private val disposable = CompositeDisposable()

    private val textWatcher: TextWatcher = object : TextWatcher {
        override fun afterTextChanged(s: Editable) {}
        override fun beforeTextChanged(s: CharSequence, start: Int, count: Int, after: Int) {}
        override fun onTextChanged(s: CharSequence, start: Int, before: Int, count: Int) {
            validateInputs()
        }
    }

    private fun validateInputs() {
        val maxCarbs = constraintChecker.getMaxCarbsAllowed().value().toDouble()
        val maxInsulin = constraintChecker.getMaxBolusAllowed().value()
        if (SafeParse.stringToInt(binding.carbs.text) > maxCarbs) {
            binding.carbs.value = 0.0
            ToastUtils.showToastInUiThread(context, rh.gs(R.string.carbsconstraintapplied))
        }
        if (SafeParse.stringToDouble(binding.insulin.text) > maxInsulin) {
            binding.insulin.value = 0.0
            ToastUtils.showToastInUiThread(context, rh.gs(R.string.bolusconstraintapplied))
        }
    }

    private var _binding: DialogTreatmentBinding? = null

    // This property is only valid between onCreateView and
    // onDestroyView.
    private val binding get() = _binding!!

    override fun onSaveInstanceState(savedInstanceState: Bundle) {
        super.onSaveInstanceState(savedInstanceState)
        savedInstanceState.putDouble("carbs", binding.carbs.value)
        savedInstanceState.putDouble("insulin", binding.insulin.value)
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?,
                              savedInstanceState: Bundle?): View {
        onCreateViewGeneral()
        _binding = DialogTreatmentBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        if (config.NSCLIENT) {
            binding.recordOnly.isChecked = true
            binding.recordOnly.isEnabled = false
        }
        val maxCarbs = constraintChecker.getMaxCarbsAllowed().value().toDouble()
        val maxInsulin = constraintChecker.getMaxBolusAllowed().value()
        val pumpDescription = activePlugin.activePump.pumpDescription
        binding.carbs.setParams(savedInstanceState?.getDouble("carbs")
            ?: 0.0, 0.0, maxCarbs, 1.0, DecimalFormat("0"), false, binding.okcancel.ok, textWatcher)
        binding.insulin.setParams(savedInstanceState?.getDouble("insulin")
            ?: 0.0, 0.0, maxInsulin, pumpDescription.bolusStep, DecimalFormatter.pumpSupportedBolusFormat(activePlugin.activePump), false, binding.okcancel.ok, textWatcher)
        binding.recordOnlyLayout.visibility = View.GONE
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

    override fun submit(): Boolean {
        if (_binding == null) return false
        val pumpDescription = activePlugin.activePump.pumpDescription
        val insulin = SafeParse.stringToDouble(binding.insulin.text)
        val carbs = SafeParse.stringToInt(binding.carbs.text)
        val recordOnlyChecked = binding.recordOnly.isChecked
        val actions: LinkedList<String?> = LinkedList()
        val insulinAfterConstraints = constraintChecker.applyBolusConstraints(Constraint(insulin)).value()
        val carbsAfterConstraints = constraintChecker.applyCarbsConstraints(Constraint(carbs)).value()

        if (insulinAfterConstraints > 0) {
            actions.add(rh.gs(R.string.bolus) + ": " + DecimalFormatter.toPumpSupportedBolus(insulinAfterConstraints, activePlugin.activePump, rh).formatColor(rh, R.color.bolus))
            if (recordOnlyChecked)
                actions.add(rh.gs(R.string.bolusrecordedonly).formatColor(rh, R.color.warning))
            if (abs(insulinAfterConstraints - insulin) > pumpDescription.pumpType.determineCorrectBolusStepSize(insulinAfterConstraints))
                actions.add(rh.gs(R.string.bolusconstraintappliedwarn, insulin, insulinAfterConstraints).formatColor(rh, R.color.warning))
        }
        if (carbsAfterConstraints > 0) {
            actions.add(rh.gs(R.string.carbs) + ": " + rh.gs(R.string.format_carbs, carbsAfterConstraints).formatColor(rh, R.color.carbs))
            if (carbsAfterConstraints != carbs)
                actions.add(rh.gs(R.string.carbsconstraintapplied).formatColor(rh, R.color.warning))
        }
        if (insulinAfterConstraints > 0 || carbsAfterConstraints > 0) {
            activity?.let { activity ->
                OKDialog.showConfirmation(activity, rh.gs(R.string.overview_treatment_label), HtmlHelper.fromHtml(Joiner.on("<br/>").join(actions)), {
                    val action = when {
                        insulinAfterConstraints.equals(0.0) -> Action.CARBS
                        carbsAfterConstraints == 0          -> Action.BOLUS
                        else                                -> Action.TREATMENT
                    }
                    val detailedBolusInfo = DetailedBolusInfo()
                    if (insulinAfterConstraints == 0.0) detailedBolusInfo.eventType = DetailedBolusInfo.EventType.CARBS_CORRECTION
                    if (carbsAfterConstraints == 0) detailedBolusInfo.eventType = DetailedBolusInfo.EventType.CORRECTION_BOLUS
                    detailedBolusInfo.insulin = insulinAfterConstraints
                    detailedBolusInfo.carbs = carbsAfterConstraints.toDouble()
                    detailedBolusInfo.context = context
                    if (recordOnlyChecked) {
                        uel.log(action, Sources.TreatmentDialog, if (insulinAfterConstraints != 0.0) rh.gs(R.string.record) else "",
                            ValueWithUnit.Timestamp(detailedBolusInfo.timestamp).takeIf { eventTimeChanged },
                            ValueWithUnit.SimpleString(rh.gsNotLocalised(R.string.record)).takeIf { insulinAfterConstraints != 0.0 },
                            ValueWithUnit.Insulin(insulinAfterConstraints).takeIf { insulinAfterConstraints != 0.0 },
                            ValueWithUnit.Gram(carbsAfterConstraints).takeIf { carbsAfterConstraints != 0 })
                        if (detailedBolusInfo.insulin > 0)
                            disposable += repository.runTransactionForResult(detailedBolusInfo.insertBolusTransaction())
                                .subscribe(
                                    { result -> result.inserted.forEach { aapsLogger.debug(LTag.DATABASE, "Inserted bolus $it") } },
                                    { aapsLogger.error(LTag.DATABASE, "Error while saving bolus", it) }
                                )
                        if (detailedBolusInfo.carbs > 0)
                            disposable += repository.runTransactionForResult(detailedBolusInfo.insertCarbsTransaction())
                                .subscribe(
                                    { result -> result.inserted.forEach { aapsLogger.debug(LTag.DATABASE, "Inserted carbs $it") } },
                                    { aapsLogger.error(LTag.DATABASE, "Error while saving carbs", it) }
                                )
                    } else {
                        if (detailedBolusInfo.insulin > 0) {
                            uel.log(action, Sources.TreatmentDialog,
                                ValueWithUnit.Insulin(insulinAfterConstraints),
                                ValueWithUnit.Gram(carbsAfterConstraints).takeIf { carbsAfterConstraints != 0 })
                            commandQueue.bolus(detailedBolusInfo, object : Callback() {
                                override fun run() {
                                    if (!result.success) {
                                        ErrorHelperActivity.runAlarm(ctx, result.comment, rh.gs(R.string.treatmentdeliveryerror), R.raw.boluserror)
                                    }
                                }
                            })
                        } else
                            uel.log(action, Sources.TreatmentDialog,
                                ValueWithUnit.Gram(carbsAfterConstraints).takeIf { carbs != 0 })
                    }
                })
            }
        } else
            activity?.let { activity ->
                OKDialog.show(activity, rh.gs(R.string.overview_treatment_label), rh.gs(R.string.no_action_selected))
            }
        return true
    }
}