package info.nightscout.androidaps.dialogs

import android.content.Context
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.Window
import android.view.WindowManager
import android.widget.AdapterView
import android.widget.AdapterView.OnItemSelectedListener
import android.widget.ArrayAdapter
import android.widget.CompoundButton
import androidx.fragment.app.FragmentManager
import dagger.android.HasAndroidInjector
import dagger.android.support.DaggerDialogFragment
import info.nightscout.androidaps.Constants
import info.nightscout.androidaps.R
import info.nightscout.androidaps.data.ProfileSealed
import info.nightscout.androidaps.database.AppRepository
import info.nightscout.androidaps.database.ValueWrapper
import info.nightscout.androidaps.databinding.DialogWizardBinding
import info.nightscout.androidaps.events.EventAutosensCalculationFinished
import info.nightscout.androidaps.logging.AAPSLogger
import info.nightscout.androidaps.logging.LTag
import info.nightscout.androidaps.plugins.bus.RxBus
import info.nightscout.androidaps.plugins.configBuilder.ConstraintChecker
import info.nightscout.androidaps.utils.DateUtil
import info.nightscout.androidaps.utils.DecimalFormatter
import info.nightscout.androidaps.utils.FabricPrivacy
import info.nightscout.androidaps.utils.SafeParse
import info.nightscout.androidaps.utils.ToastUtils
import info.nightscout.androidaps.extensions.toVisibility
import info.nightscout.androidaps.extensions.valueToUnits
import info.nightscout.androidaps.interfaces.*
import info.nightscout.androidaps.utils.resources.ResourceHelper
import info.nightscout.androidaps.utils.rx.AapsSchedulers
import info.nightscout.androidaps.utils.sharedPreferences.SP
import info.nightscout.androidaps.utils.wizard.BolusWizard
import io.reactivex.disposables.CompositeDisposable
import java.text.DecimalFormat
import java.util.*
import javax.inject.Inject
import kotlin.math.abs

class WizardDialog : DaggerDialogFragment() {

    @Inject lateinit var injector: HasAndroidInjector
    @Inject lateinit var aapsLogger: AAPSLogger
    @Inject lateinit var aapsSchedulers: AapsSchedulers
    @Inject lateinit var constraintChecker: ConstraintChecker
    @Inject lateinit var ctx: Context
    @Inject lateinit var sp: SP
    @Inject lateinit var rxBus: RxBus
    @Inject lateinit var fabricPrivacy: FabricPrivacy
    @Inject lateinit var rh: ResourceHelper
    @Inject lateinit var profileFunction: ProfileFunction
    @Inject lateinit var activePlugin: ActivePlugin
    @Inject lateinit var iobCobCalculator: IobCobCalculator
    @Inject lateinit var repository: AppRepository
    @Inject lateinit var dateUtil: DateUtil

    private var wizard: BolusWizard? = null

    //one shot guards
    private var okClicked: Boolean = false

    private val textWatcher = object : TextWatcher {
        override fun afterTextChanged(s: Editable) {}
        override fun beforeTextChanged(s: CharSequence, start: Int, count: Int, after: Int) {}
        override fun onTextChanged(s: CharSequence, start: Int, before: Int, count: Int) {
            calculateInsulin()
        }
    }

    private val timeTextWatcher = object : TextWatcher {
        override fun afterTextChanged(s: Editable) {}
        override fun beforeTextChanged(s: CharSequence, start: Int, count: Int, after: Int) {}
        override fun onTextChanged(s: CharSequence, start: Int, before: Int, count: Int) {
            calculateInsulin()
            binding.alarm.isChecked = binding.carbTimeInput.value > 0
        }
    }

    private var disposable: CompositeDisposable = CompositeDisposable()

    private var _binding: DialogWizardBinding? = null

    // This property is only valid between onCreateView and
    // onDestroyView.
    private val binding get() = _binding!!

    override fun onStart() {
        super.onStart()
        dialog?.window?.setLayout(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
    }

    override fun onSaveInstanceState(savedInstanceState: Bundle) {
        super.onSaveInstanceState(savedInstanceState)
        savedInstanceState.putDouble("bg_input", binding.bgInput.value)
        savedInstanceState.putDouble("carbs_input", binding.carbsInput.value)
        savedInstanceState.putDouble("correction_input", binding.correctionInput.value)
        savedInstanceState.putDouble("carb_time_input", binding.carbTimeInput.value)
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?,
                              savedInstanceState: Bundle?): View {
        dialog?.window?.requestFeature(Window.FEATURE_NO_TITLE)
        dialog?.window?.setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_STATE_HIDDEN)
        isCancelable = true
        dialog?.setCanceledOnTouchOutside(false)

        _binding = DialogWizardBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        loadCheckedStates()
        processCobCheckBox()
        binding.sbcheckbox.visibility = sp.getBoolean(R.string.key_usesuperbolus, false).toVisibility()
        binding.notesLayout.visibility = sp.getBoolean(R.string.key_show_notes_entry_dialogs, false).toVisibility()

        val maxCarbs = constraintChecker.getMaxCarbsAllowed().value()
        val maxCorrection = constraintChecker.getMaxBolusAllowed().value()

        if (profileFunction.getUnits() == GlucoseUnit.MGDL)
            binding.bgInput.setParams(savedInstanceState?.getDouble("bg_input")
                ?: 0.0, 0.0, 500.0, 1.0, DecimalFormat("0"), false, binding.ok, timeTextWatcher)
        else
            binding.bgInput.setParams(savedInstanceState?.getDouble("bg_input")
                ?: 0.0, 0.0, 30.0, 0.1, DecimalFormat("0.0"), false, binding.ok, textWatcher)
        binding.carbsInput.setParams(savedInstanceState?.getDouble("carbs_input")
            ?: 0.0, 0.0, maxCarbs.toDouble(), 1.0, DecimalFormat("0"), false, binding.ok, textWatcher)
        val bolusStep = activePlugin.activePump.pumpDescription.bolusStep
        binding.correctionInput.setParams(savedInstanceState?.getDouble("correction_input")
            ?: 0.0, -maxCorrection, maxCorrection, bolusStep, DecimalFormatter.pumpSupportedBolusFormat(activePlugin.activePump), false, binding.ok, textWatcher)
        binding.carbTimeInput.setParams(savedInstanceState?.getDouble("carb_time_input")
            ?: 0.0, -60.0, 60.0, 5.0, DecimalFormat("0"), false, binding.ok, timeTextWatcher)
        initDialog()

        binding.percentUsed.text = rh.gs(R.string.format_percent, sp.getInt(R.string.key_boluswizard_percentage, 100))
        // ok button
        binding.ok.setOnClickListener {
            if (okClicked) {
                aapsLogger.debug(LTag.UI, "guarding: ok already clicked")
            } else {
                okClicked = true
                calculateInsulin()
                context?.let { context ->
                    wizard?.confirmAndExecute(context)
                }
            }
            dismiss()
        }
        // cancel button
        binding.cancel.setOnClickListener { dismiss() }
        // checkboxes
        binding.bgcheckbox.setOnCheckedChangeListener(::onCheckedChanged)
        binding.ttcheckbox.setOnCheckedChangeListener(::onCheckedChanged)
        binding.cobcheckbox.setOnCheckedChangeListener(::onCheckedChanged)
        binding.basaliobcheckbox.setOnCheckedChangeListener(::onCheckedChanged)
        binding.bolusiobcheckbox.setOnCheckedChangeListener(::onCheckedChanged)
        binding.bgtrendcheckbox.setOnCheckedChangeListener(::onCheckedChanged)
        binding.sbcheckbox.setOnCheckedChangeListener(::onCheckedChanged)

        val showCalc = sp.getBoolean(R.string.key_wizard_calculation_visible, false)
        binding.delimiter.visibility = showCalc.toVisibility()
        binding.resulttable.visibility = showCalc.toVisibility()
        binding.calculationcheckbox.isChecked = showCalc
        binding.calculationcheckbox.setOnCheckedChangeListener { _, isChecked ->
            run {
                sp.putBoolean(rh.gs(R.string.key_wizard_calculation_visible), isChecked)
                binding.delimiter.visibility = isChecked.toVisibility()
                binding.resulttable.visibility = isChecked.toVisibility()
            }
        }
        // profile spinner
        binding.profile.onItemSelectedListener = object : OnItemSelectedListener {
            override fun onNothingSelected(parent: AdapterView<*>?) {
                ToastUtils.showToastInUiThread(ctx, rh.gs(R.string.noprofileset))
                binding.ok.visibility = View.GONE
            }

            override fun onItemSelected(parent: AdapterView<*>?, view: View?, position: Int, id: Long) {
                calculateInsulin()
                binding.ok.visibility = View.VISIBLE
            }
        }
        // bus
        disposable.add(rxBus
            .toObservable(EventAutosensCalculationFinished::class.java)
            .observeOn(aapsSchedulers.main)
            .subscribe({
                activity?.runOnUiThread { calculateInsulin() }
            }, fabricPrivacy::logException)
        )

    }

    override fun onDestroyView() {
        super.onDestroyView()
        disposable.clear()
        _binding = null
    }

    private fun onCheckedChanged(buttonView: CompoundButton, @Suppress("UNUSED_PARAMETER") state: Boolean) {
        saveCheckedStates()
        binding.ttcheckbox.isEnabled = binding.bgcheckbox.isChecked && repository.getTemporaryTargetActiveAt(dateUtil.now()).blockingGet() is ValueWrapper.Existing
        if (buttonView.id == binding.cobcheckbox.id)
            processCobCheckBox()
        calculateInsulin()
    }

    private fun processCobCheckBox() {
        if (binding.cobcheckbox.isChecked) {
            binding.bolusiobcheckbox.isEnabled = false
            binding.basaliobcheckbox.isEnabled = false
            binding.bolusiobcheckbox.isChecked = true
            binding.basaliobcheckbox.isChecked = true
        } else {
            binding.bolusiobcheckbox.isEnabled = true
            binding.basaliobcheckbox.isEnabled = true
        }
    }

    private fun saveCheckedStates() {
        sp.putBoolean(R.string.key_wizard_include_cob, binding.cobcheckbox.isChecked)
        sp.putBoolean(R.string.key_wizard_include_trend_bg, binding.bgtrendcheckbox.isChecked)
    }

    private fun loadCheckedStates() {
        binding.bgtrendcheckbox.isChecked = sp.getBoolean(R.string.key_wizard_include_trend_bg, false)
        binding.cobcheckbox.isChecked = sp.getBoolean(R.string.key_wizard_include_cob, false)
    }

    private fun valueToUnitsToString(value: Double, units: String): String =
        if (units == Constants.MGDL) DecimalFormatter.to0Decimal(value)
        else DecimalFormatter.to1Decimal(value * Constants.MGDL_TO_MMOLL)

    private fun initDialog() {
        val profile = profileFunction.getProfile()
        val profileStore = activePlugin.activeProfileSource.profile

        if (profile == null || profileStore == null) {
            ToastUtils.showToastInUiThread(ctx, rh.gs(R.string.noprofile))
            dismiss()
            return
        }

        val profileList: ArrayList<CharSequence> = profileStore.getProfileList()
        profileList.add(0, rh.gs(R.string.active))
        context?.let { context ->
            val adapter = ArrayAdapter(context, R.layout.spinner_centered, profileList)
            binding.profile.adapter = adapter
        } ?: return

        val units = profileFunction.getUnits()
        binding.bgunits.text = units.asText
        binding.bgInput.step = if (units == GlucoseUnit.MGDL) 1.0 else 0.1

        // Set BG if not old
        binding.bgInput.value = iobCobCalculator.ads.actualBg()?.valueToUnits(units) ?: 0.0
        binding.ttcheckbox.isEnabled = repository.getTemporaryTargetActiveAt(dateUtil.now()).blockingGet() is ValueWrapper.Existing

        // IOB calculation
        val bolusIob = iobCobCalculator.calculateIobFromBolus().round()
        val basalIob = iobCobCalculator.calculateIobFromTempBasalsIncludingConvertedExtended().round()

        binding.bolusiobinsulin.text = rh.gs(R.string.formatinsulinunits, -bolusIob.iob)
        binding.basaliobinsulin.text = rh.gs(R.string.formatinsulinunits, -basalIob.basaliob)

        calculateInsulin()

        binding.percentUsed.visibility = (sp.getInt(R.string.key_boluswizard_percentage, 100) != 100).toVisibility()
    }

    private fun calculateInsulin() {
        val profileStore = activePlugin.activeProfileSource.profile
        if (binding.profile.selectedItem == null || profileStore == null)
            return  // not initialized yet
        var profileName = binding.profile.selectedItem.toString()
        val specificProfile: Profile?
        if (profileName == rh.gs(R.string.active)) {
            specificProfile = profileFunction.getProfile()
            profileName = profileFunction.getProfileName()
        } else
            specificProfile = profileStore.getSpecificProfile(profileName)?.let { ProfileSealed.Pure(it) }

        if (specificProfile == null) return

        // Entered values
        var bg = SafeParse.stringToDouble(binding.bgInput.text)
        val carbs = SafeParse.stringToInt(binding.carbsInput.text)
        val correction = SafeParse.stringToDouble(binding.correctionInput.text)
        val carbsAfterConstraint = constraintChecker.applyCarbsConstraints(Constraint(carbs)).value()
        if (abs(carbs - carbsAfterConstraint) > 0.01) {
            binding.carbsInput.value = 0.0
            ToastUtils.showToastInUiThread(ctx, rh.gs(R.string.carbsconstraintapplied))
            return
        }

        bg = if (binding.bgcheckbox.isChecked) bg else 0.0
        val dbRecord = repository.getTemporaryTargetActiveAt(dateUtil.now()).blockingGet()
        val tempTarget = if (binding.ttcheckbox.isChecked && dbRecord is ValueWrapper.Existing) dbRecord.value else null

        // COB
        var cob = 0.0
        if (binding.cobcheckbox.isChecked) {
            val cobInfo = iobCobCalculator.getCobInfo(false, "Wizard COB")
            cobInfo.displayCob?.let { cob = it }
        }

        val carbTime = SafeParse.stringToInt(binding.carbTimeInput.text)

        wizard = BolusWizard(injector).doCalc(specificProfile, profileName, tempTarget, carbsAfterConstraint, cob, bg, correction,
            sp.getInt(R.string.key_boluswizard_percentage, 100),
            binding.bgcheckbox.isChecked,
            binding.cobcheckbox.isChecked,
            binding.bolusiobcheckbox.isChecked,
            binding.basaliobcheckbox.isChecked,
            binding.sbcheckbox.isChecked,
            binding.ttcheckbox.isChecked,
            binding.bgtrendcheckbox.isChecked,
            binding.alarm.isChecked,
            binding.notes.text.toString(), carbTime)

        wizard?.let { wizard ->
            binding.bg.text = String.format(rh.gs(R.string.format_bg_isf), valueToUnitsToString(Profile.toMgdl(bg, profileFunction.getUnits()), profileFunction.getUnits().asText), wizard.sens)
            binding.bginsulin.text = rh.gs(R.string.formatinsulinunits, wizard.insulinFromBG)

            binding.carbs.text = String.format(rh.gs(R.string.format_carbs_ic), carbs.toDouble(), wizard.ic)
            binding.carbsinsulin.text = rh.gs(R.string.formatinsulinunits, wizard.insulinFromCarbs)

            binding.bolusiobinsulin.text = rh.gs(R.string.formatinsulinunits, wizard.insulinFromBolusIOB)
            binding.basaliobinsulin.text = rh.gs(R.string.formatinsulinunits, wizard.insulinFromBasalIOB)

            binding.correctioninsulin.text = rh.gs(R.string.formatinsulinunits, wizard.insulinFromCorrection)

            // Superbolus
            binding.sb.text = if (binding.sbcheckbox.isChecked) rh.gs(R.string.twohours) else ""
            binding.sbinsulin.text = rh.gs(R.string.formatinsulinunits, wizard.insulinFromSuperBolus)

            // Trend
            if (binding.bgtrendcheckbox.isChecked && wizard.glucoseStatus != null) {
                binding.bgtrend.text = ((if (wizard.trend > 0) "+" else "")
                    + Profile.toUnitsString(wizard.trend * 3, wizard.trend * 3 / Constants.MMOLL_TO_MGDL, profileFunction.getUnits())
                    + " " + profileFunction.getUnits())
            } else {
                binding.bgtrend.text = ""
            }
            binding.bgtrendinsulin.text = rh.gs(R.string.formatinsulinunits, wizard.insulinFromTrend)

            // COB
            if (binding.cobcheckbox.isChecked) {
                binding.cob.text = String.format(rh.gs(R.string.format_cob_ic), cob, wizard.ic)
                binding.cobinsulin.text = rh.gs(R.string.formatinsulinunits, wizard.insulinFromCOB)
            } else {
                binding.cob.text = ""
                binding.cobinsulin.text = ""
            }

            if (wizard.calculatedTotalInsulin > 0.0 || carbsAfterConstraint > 0.0) {
                val insulinText = if (wizard.calculatedTotalInsulin > 0.0) rh.gs(R.string.formatinsulinunits, wizard.calculatedTotalInsulin) else ""
                val carbsText = if (carbsAfterConstraint > 0.0) rh.gs(R.string.format_carbs, carbsAfterConstraint) else ""
                binding.total.text = rh.gs(R.string.result_insulin_carbs, insulinText, carbsText)
                binding.ok.visibility = View.VISIBLE
            } else {
                binding.total.text = rh.gs(R.string.missing_carbs, wizard.carbsEquivalent.toInt())
                binding.ok.visibility = View.INVISIBLE
            }
        }

    }

    override fun show(manager: FragmentManager, tag: String?) {
        try {
            manager.beginTransaction().let {
                it.add(this, tag)
                it.commitAllowingStateLoss()
            }
        } catch (e: IllegalStateException) {
            aapsLogger.debug(e.localizedMessage ?: "")
        }
    }
}
