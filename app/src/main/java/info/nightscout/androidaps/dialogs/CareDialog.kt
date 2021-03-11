package info.nightscout.androidaps.dialogs

import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.annotation.StringRes
import com.google.common.base.Joiner
import dagger.android.HasAndroidInjector
import info.nightscout.androidaps.Constants
import info.nightscout.androidaps.MainApp
import info.nightscout.androidaps.R
import info.nightscout.androidaps.data.Profile
import info.nightscout.androidaps.database.AppRepository
import info.nightscout.androidaps.database.entities.TherapyEvent
import info.nightscout.androidaps.database.transactions.InsertTherapyEventIfNewTransaction
import info.nightscout.androidaps.databinding.DialogCareBinding
import info.nightscout.androidaps.interfaces.ProfileFunction
import info.nightscout.androidaps.logging.LTag
import info.nightscout.androidaps.logging.UserEntryLogger
import info.nightscout.androidaps.plugins.general.nsclient.NSUpload
import info.nightscout.androidaps.plugins.iob.iobCobCalculator.GlucoseStatusProvider
import info.nightscout.androidaps.utils.HtmlHelper
import info.nightscout.androidaps.utils.T
import info.nightscout.androidaps.utils.Translator
import info.nightscout.androidaps.utils.alertDialogs.OKDialog
import info.nightscout.androidaps.utils.extensions.fromConstant
import info.nightscout.androidaps.utils.resources.ResourceHelper
import io.reactivex.disposables.CompositeDisposable
import io.reactivex.rxkotlin.plusAssign
import java.text.DecimalFormat
import java.util.*
import javax.inject.Inject

class CareDialog : DialogFragmentWithDate() {

    @Inject lateinit var injector: HasAndroidInjector
    @Inject lateinit var mainApp: MainApp
    @Inject lateinit var resourceHelper: ResourceHelper
    @Inject lateinit var profileFunction: ProfileFunction
    @Inject lateinit var nsUpload: NSUpload
    @Inject lateinit var translator: Translator
    @Inject lateinit var uel: UserEntryLogger
    @Inject lateinit var repository: AppRepository
    @Inject lateinit var glucoseStatusProvider: GlucoseStatusProvider

    private val disposable = CompositeDisposable()

    enum class EventType {
        BGCHECK,
        SENSOR_INSERT,
        BATTERY_CHANGE,
        NOTE,
        EXERCISE,
        QUESTION,
        ANNOUNCEMENT
    }

    private var options: EventType = EventType.BGCHECK

    @StringRes
    private var event: Int = R.string.none

    fun setOptions(options: EventType, @StringRes event: Int): CareDialog {
        this.options = options
        this.event = event
        return this
    }

    private var _binding: DialogCareBinding? = null

    // This property is only valid between onCreateView and
    // onDestroyView.
    private val binding get() = _binding!!

    override fun onSaveInstanceState(savedInstanceState: Bundle) {
        super.onSaveInstanceState(savedInstanceState)
        savedInstanceState.putDouble("bg", binding.bg.value)
        savedInstanceState.putDouble("duration", binding.duration.value)
        savedInstanceState.putInt("event", event)
        savedInstanceState.putInt("options", options.ordinal)
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?,
                              savedInstanceState: Bundle?): View {
        onCreateViewGeneral()
        _binding = DialogCareBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        savedInstanceState?.let {
            event = savedInstanceState.getInt("event", R.string.error)
            options = EventType.values()[savedInstanceState.getInt("options", 0)]
        }

        binding.icon.setImageResource(when (options) {
            EventType.BGCHECK        -> R.drawable.ic_cp_bgcheck
            EventType.SENSOR_INSERT  -> R.drawable.ic_cp_cgm_insert
            EventType.BATTERY_CHANGE -> R.drawable.ic_cp_pump_battery
            EventType.NOTE           -> R.drawable.ic_cp_note
            EventType.EXERCISE       -> R.drawable.ic_cp_exercise
            EventType.QUESTION       -> R.drawable.ic_cp_question
            EventType.ANNOUNCEMENT   -> R.drawable.ic_cp_announcement
        })
        binding.title.text = resourceHelper.gs(when (options) {
            EventType.BGCHECK        -> R.string.careportal_bgcheck
            EventType.SENSOR_INSERT  -> R.string.careportal_cgmsensorinsert
            EventType.BATTERY_CHANGE -> R.string.careportal_pumpbatterychange
            EventType.NOTE           -> R.string.careportal_note
            EventType.EXERCISE       -> R.string.careportal_exercise
            EventType.QUESTION       -> R.string.careportal_question
            EventType.ANNOUNCEMENT   -> R.string.careportal_announcement
        })

        when (options) {
            EventType.QUESTION,
            EventType.ANNOUNCEMENT,
            EventType.BGCHECK        -> {
                binding.durationLayout.visibility = View.GONE
            }

            EventType.SENSOR_INSERT,
            EventType.BATTERY_CHANGE -> {
                binding.bgLayout.visibility = View.GONE
                binding.bgsource.visibility = View.GONE
                binding.durationLayout.visibility = View.GONE
            }

            EventType.NOTE,
            EventType.EXERCISE       -> {
                binding.bgLayout.visibility = View.GONE
                binding.bgsource.visibility = View.GONE
            }
        }

        val bg = Profile.fromMgdlToUnits(glucoseStatusProvider.glucoseStatusData?.glucose
            ?: 0.0, profileFunction.getUnits())
        val bgTextWatcher: TextWatcher = object : TextWatcher {
            override fun afterTextChanged(s: Editable) {}
            override fun beforeTextChanged(s: CharSequence, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence, start: Int, before: Int, count: Int) {
                if (binding.sensor.isChecked) binding.meter.isChecked = true
            }
        }

        if (profileFunction.getUnits() == Constants.MMOL) {
            binding.bgunits.text = resourceHelper.gs(R.string.mmol)
            binding.bg.setParams(savedInstanceState?.getDouble("bg")
                ?: bg, 2.0, 30.0, 0.1, DecimalFormat("0.0"), false, binding.okcancel.ok, bgTextWatcher)
        } else {
            binding.bgunits.text = resourceHelper.gs(R.string.mgdl)
            binding.bg.setParams(savedInstanceState?.getDouble("bg")
                ?: bg, 36.0, 500.0, 1.0, DecimalFormat("0"), false, binding.okcancel.ok, bgTextWatcher)
        }
        binding.duration.setParams(savedInstanceState?.getDouble("duration")
            ?: 0.0, 0.0, Constants.MAX_PROFILE_SWITCH_DURATION, 10.0, DecimalFormat("0"), false, binding.okcancel.ok)
        if (options == EventType.NOTE || options == EventType.QUESTION || options == EventType.ANNOUNCEMENT || options == EventType.EXERCISE)
            binding.notesLayout.root.visibility = View.VISIBLE // independent to preferences
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

    override fun submit(): Boolean {
        val enteredBy = sp.getString("careportal_enteredby", "AndroidAPS")
        val unitResId = if (profileFunction.getUnits() == Constants.MGDL) R.string.mgdl else R.string.mmol

        eventTime -= eventTime % 1000

        val therapyEvent = TherapyEvent(
            timestamp = eventTime,
            type = when (options) {
                EventType.BGCHECK        -> TherapyEvent.Type.FINGER_STICK_BG_VALUE
                EventType.SENSOR_INSERT  -> TherapyEvent.Type.SENSOR_CHANGE
                EventType.BATTERY_CHANGE -> TherapyEvent.Type.PUMP_BATTERY_CHANGE
                EventType.NOTE           -> TherapyEvent.Type.NOTE
                EventType.EXERCISE       -> TherapyEvent.Type.EXERCISE
                EventType.QUESTION       -> TherapyEvent.Type.QUESTION
                EventType.ANNOUNCEMENT   -> TherapyEvent.Type.ANNOUNCEMENT
            },
            glucoseUnit = TherapyEvent.GlucoseUnit.fromConstant(profileFunction.getUnits())
        )

        val actions: LinkedList<String> = LinkedList()
        if (options == EventType.BGCHECK || options == EventType.QUESTION || options == EventType.ANNOUNCEMENT) {
            val meterType =
                when {
                    binding.meter.isChecked  -> TherapyEvent.MeterType.FINGER
                    binding.sensor.isChecked -> TherapyEvent.MeterType.SENSOR
                    else                     -> TherapyEvent.MeterType.MANUAL
                }
            actions.add(resourceHelper.gs(R.string.careportal_newnstreatment_glucosetype) + ": " + translator.translate(meterType.text))
            actions.add(resourceHelper.gs(R.string.treatments_wizard_bg_label) + ": " + Profile.toCurrentUnitsString(profileFunction, binding.bg.value) + " " + resourceHelper.gs(unitResId))
            therapyEvent.glucoseType = meterType
            therapyEvent.glucose = binding.bg.value
        }
        if (options == EventType.NOTE || options == EventType.EXERCISE) {
            actions.add(resourceHelper.gs(R.string.careportal_newnstreatment_duration_label) + ": " + resourceHelper.gs(R.string.format_mins, binding.duration.value.toInt()))
            therapyEvent.duration = T.mins(binding.duration.value.toLong()).msecs()
        }
        val notes = binding.notesLayout.notes.text.toString()
        if (notes.isNotEmpty()) {
            actions.add(resourceHelper.gs(R.string.notes_label) + ": " + notes)
            therapyEvent.note = notes
        }

        if (eventTimeChanged)
            actions.add(resourceHelper.gs(R.string.time) + ": " + dateUtil.dateAndTimeString(eventTime))

        therapyEvent.enteredBy = enteredBy

        activity?.let { activity ->
            OKDialog.showConfirmation(activity, resourceHelper.gs(event), HtmlHelper.fromHtml(Joiner.on("<br/>").join(actions)), {
                disposable += repository.runTransactionForResult(InsertTherapyEventIfNewTransaction(therapyEvent)).subscribe({ result ->
                    result.inserted.forEach { nsUpload.uploadEvent(it) }
                }, {
                    aapsLogger.error(LTag.BGSOURCE, "Error while saving therapy event", it)
                })

                uel.log("CAREPORTAL", therapyEvent.type.text)
            }, null)
        }
        return true
    }
}
