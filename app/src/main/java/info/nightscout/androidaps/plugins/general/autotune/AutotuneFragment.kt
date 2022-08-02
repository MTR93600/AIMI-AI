package info.nightscout.androidaps.plugins.general.autotune

import android.graphics.Paint
import android.graphics.Typeface
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.view.Gravity
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.AdapterView
import android.widget.ArrayAdapter
import android.widget.TableLayout
import android.widget.TableRow
import android.widget.TextView
import dagger.android.HasAndroidInjector
import dagger.android.support.DaggerFragment
import info.nightscout.androidaps.Constants
import info.nightscout.androidaps.R
import info.nightscout.androidaps.data.LocalInsulin
import info.nightscout.androidaps.data.ProfileSealed
import info.nightscout.androidaps.database.entities.UserEntry
import info.nightscout.androidaps.database.entities.ValueWithUnit
import info.nightscout.androidaps.databinding.AutotuneFragmentBinding
import info.nightscout.androidaps.dialogs.ProfileViewerDialog
import info.nightscout.androidaps.extensions.runOnUiThread
import info.nightscout.androidaps.extensions.toVisibility
import info.nightscout.androidaps.interfaces.*
import info.nightscout.androidaps.logging.UserEntryLogger
import info.nightscout.androidaps.plugins.bus.RxBus
import info.nightscout.androidaps.plugins.general.autotune.data.ATProfile
import info.nightscout.androidaps.plugins.general.autotune.events.EventAutotuneUpdateGui
import info.nightscout.androidaps.plugins.profile.local.LocalProfilePlugin
import info.nightscout.androidaps.plugins.profile.local.events.EventLocalProfileChanged
import info.nightscout.androidaps.utils.DateUtil
import info.nightscout.androidaps.utils.FabricPrivacy
import info.nightscout.androidaps.utils.MidnightTime
import info.nightscout.androidaps.utils.Round
import info.nightscout.androidaps.utils.alertDialogs.OKDialog.showConfirmation
import info.nightscout.androidaps.utils.rx.AapsSchedulers
import info.nightscout.shared.SafeParse
import info.nightscout.shared.sharedPreferences.SP
import io.reactivex.rxjava3.disposables.CompositeDisposable
import io.reactivex.rxjava3.kotlin.plusAssign
import org.json.JSONObject
import java.text.DecimalFormat
import javax.inject.Inject

class AutotuneFragment : DaggerFragment() {

    @Inject lateinit var profileFunction: ProfileFunction
    @Inject lateinit var autotunePlugin: AutotunePlugin
    @Inject lateinit var autotuneFS: AutotuneFS
    @Inject lateinit var sp: SP
    @Inject lateinit var dateUtil: DateUtil
    @Inject lateinit var activePlugin: ActivePlugin
    @Inject lateinit var localProfilePlugin: LocalProfilePlugin
    @Inject lateinit var fabricPrivacy: FabricPrivacy
    @Inject lateinit var uel: UserEntryLogger
    @Inject lateinit var rh: ResourceHelper
    @Inject lateinit var rxBus: RxBus
    @Inject lateinit var injector: HasAndroidInjector
    @Inject lateinit var aapsSchedulers: AapsSchedulers

    private var disposable: CompositeDisposable = CompositeDisposable()

    //private val log = LoggerFactory.getLogger(AutotunePlugin::class.java)
    private var _binding: AutotuneFragmentBinding? = null
    private lateinit var profileStore: ProfileStore
    private var profileName = ""
    private var profile: ATProfile? = null

    // This property is only valid between onCreateView and
    // onDestroyView.
    private val binding get() = _binding!!

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = AutotuneFragmentBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        sp.putBoolean(R.string.key_autotune_tune_insulin_curve, false)  // put to false tune insulin curve
        sp.putBoolean(R.string.key_autotune_additional_log, false)      // put to false additional log
        autotunePlugin.loadLastRun()
        if (autotunePlugin.lastNbDays.isEmpty())
            autotunePlugin.lastNbDays = sp.getInt(R.string.key_autotune_default_tune_days, 5).toString()
        val defaultValue = sp.getInt(R.string.key_autotune_default_tune_days, 5).toDouble()
        profileStore = activePlugin.activeProfileSource.profile ?: ProfileStore(injector, JSONObject(), dateUtil)
        profileName = if (binding.profileList.text.toString() == rh.gs(R.string.active)) "" else binding.profileList.text.toString()
        profileFunction.getProfile()?.let { currentProfile ->
            profile = ATProfile(profileStore.getSpecificProfile(profileName)?.let { ProfileSealed.Pure(it) } ?: currentProfile, LocalInsulin(""), injector)
        }

        binding.tuneDays.setParams(
            savedInstanceState?.getDouble("tunedays")
                ?: defaultValue, 1.0, 30.0, 1.0, DecimalFormat("0"), false, null, textWatcher
        )
        binding.autotuneRun.setOnClickListener {
            val daysBack = SafeParse.stringToInt(binding.tuneDays.text)
            autotunePlugin.lastNbDays = daysBack.toString()
            log("Run Autotune $profileName, $daysBack days")
            Thread {
                autotunePlugin.aapsAutotune(daysBack, false, profileName)
            }.start()
            updateGui()
        }
        binding.profileList.onItemClickListener = AdapterView.OnItemClickListener { _, _, _, _ ->
            if (!autotunePlugin.calculationRunning) {
                profileName = if (binding.profileList.text.toString() == rh.gs(R.string.active)) "" else binding.profileList.text.toString()
                profileFunction.getProfile()?.let { currentProfile ->
                    profile = ATProfile(profileStore.getSpecificProfile(profileName)?.let { ProfileSealed.Pure(it) } ?: currentProfile, LocalInsulin(""), injector)
                }
                autotunePlugin.selectedProfile = profileName
                resetParam()
                binding.tuneDays.value = autotunePlugin.lastNbDays.toDouble()
            }
            updateGui()
        }

        binding.autotuneCopylocal.setOnClickListener {
            val localName = rh.gs(R.string.autotune_tunedprofile_name) + " " + dateUtil.dateAndTimeString(autotunePlugin.lastRun)
            val circadian = sp.getBoolean(R.string.key_autotune_circadian_ic_isf, false)
            autotunePlugin.tunedProfile?.let { tunedProfile ->
                showConfirmation(requireContext(),
                                 rh.gs(R.string.autotune_copy_localprofile_button),
                                 rh.gs(R.string.autotune_copy_local_profile_message) + "\n" + localName,
                                 Runnable {
                                     localProfilePlugin.addProfile(localProfilePlugin.copyFrom(tunedProfile.getProfile(circadian), localName))
                                     rxBus.send(EventLocalProfileChanged())
                                     uel.log(
                                         UserEntry.Action.NEW_PROFILE,
                                         UserEntry.Sources.Autotune,
                                         ValueWithUnit.SimpleString(localName)
                                     )
                                     updateGui()
                                 })
            }
        }

        binding.autotuneUpdateProfile.setOnClickListener {
            val localName = autotunePlugin.pumpProfile.profilename
            showConfirmation(requireContext(),
                             rh.gs(R.string.autotune_update_input_profile_button),
                             rh.gs(R.string.autotune_update_local_profile_message, localName),
                             Runnable {
                                 autotunePlugin.tunedProfile?.profilename = localName
                                 autotunePlugin.updateProfile(autotunePlugin.tunedProfile)
                                 autotunePlugin.updateButtonVisibility = View.GONE
                                 autotunePlugin.saveLastRun()
                                 uel.log(
                                     UserEntry.Action.STORE_PROFILE,
                                     UserEntry.Sources.Autotune,
                                     ValueWithUnit.SimpleString(localName)
                                 )
                                 updateGui()
                             }
            )
        }

        binding.autotuneRevertProfile.setOnClickListener {
            val localName = autotunePlugin.pumpProfile.profilename
            showConfirmation(requireContext(),
                             rh.gs(R.string.autotune_revert_input_profile_button),
                             rh.gs(R.string.autotune_revert_local_profile_message, localName),
                             Runnable {
                                 autotunePlugin.tunedProfile?.profilename = ""
                                 autotunePlugin.updateProfile(autotunePlugin.pumpProfile)
                                 autotunePlugin.updateButtonVisibility = View.VISIBLE
                                 autotunePlugin.saveLastRun()
                                 uel.log(
                                     UserEntry.Action.STORE_PROFILE,
                                     UserEntry.Sources.Autotune,
                                     ValueWithUnit.SimpleString(localName)
                                 )
                                 updateGui()
                             }
            )
        }

        binding.autotuneCheckInputProfile.setOnClickListener {
            val pumpProfile = profileFunction.getProfile()?.let { currentProfile ->
                profileStore.getSpecificProfile(profileName)?.let { specificProfile ->
                    ATProfile(ProfileSealed.Pure(specificProfile), LocalInsulin(""), injector).also {
                        it.profilename = profileName
                    }
                }
                    ?: ATProfile(currentProfile, LocalInsulin(""), injector).also {
                        it.profilename = profileFunction.getProfileName()
                    }
            }
            pumpProfile?.let {
                ProfileViewerDialog().also { pvd ->
                    pvd.arguments = Bundle().also {
                        it.putLong("time", dateUtil.now())
                        it.putInt("mode", ProfileViewerDialog.Mode.CUSTOM_PROFILE.ordinal)
                        it.putString("customProfile", pumpProfile.profile.toPureNsJson(dateUtil).toString())
                        it.putString("customProfileUnits", profileFunction.getUnits().asText)
                        it.putString("customProfileName", pumpProfile.profilename)
                    }
                }.show(childFragmentManager, "ProfileViewDialog")
            }
        }

        binding.autotuneCompare.setOnClickListener {
            val pumpProfile = autotunePlugin.pumpProfile
            val circadian = sp.getBoolean(R.string.key_autotune_circadian_ic_isf, false)
            val tunedProfile = if (circadian) autotunePlugin.tunedProfile?.circadianProfile else autotunePlugin.tunedProfile?.profile
            ProfileViewerDialog().also { pvd ->
                pvd.arguments = Bundle().also {
                    it.putLong("time", dateUtil.now())
                    it.putInt("mode", ProfileViewerDialog.Mode.PROFILE_COMPARE.ordinal)
                    it.putString("customProfile", pumpProfile.profile.toPureNsJson(dateUtil).toString())
                    it.putString("customProfile2", tunedProfile?.toPureNsJson(dateUtil).toString())
                    it.putString("customProfileUnits", profileFunction.getUnits().asText)
                    it.putString("customProfileName", pumpProfile.profilename + "\n" + rh.gs(R.string.autotune_tunedprofile_name))
                }
            }.show(childFragmentManager, "ProfileViewDialog")
        }

        binding.autotuneProfileswitch.setOnClickListener {
            val tunedProfile = autotunePlugin.tunedProfile
            autotunePlugin.updateProfile(tunedProfile)
            val circadian = sp.getBoolean(R.string.key_autotune_circadian_ic_isf, false)
            tunedProfile?.let { tunedP ->
                tunedP.profileStore(circadian)?.let {
                    showConfirmation(requireContext(),
                                     rh.gs(R.string.activate_profile) + ": " + tunedP.profilename + " ?",
                                     Runnable {
                                         uel.log(
                                             UserEntry.Action.STORE_PROFILE,
                                             UserEntry.Sources.Autotune,
                                             ValueWithUnit.SimpleString(tunedP.profilename)
                                         )
                                         val now = dateUtil.now()
                                         if (profileFunction.createProfileSwitch(
                                                 it,
                                                 profileName = tunedP.profilename,
                                                 durationInMinutes = 0,
                                                 percentage = 100,
                                                 timeShiftInHours = 0,
                                                 timestamp = now
                                             )
                                         ) {
                                             uel.log(
                                                 UserEntry.Action.PROFILE_SWITCH,
                                                 UserEntry.Sources.Autotune,
                                                 "Autotune AutoSwitch",
                                                 ValueWithUnit.SimpleString(autotunePlugin.tunedProfile!!.profilename)
                                             )
                                         }
                                         rxBus.send(EventLocalProfileChanged())
                                         updateGui()
                                     }
                    )
                }
            }
        }

        binding.tuneLastrun.setOnClickListener {
            if (!autotunePlugin.calculationRunning) {
                autotunePlugin.loadLastRun()
                updateGui()
            }
        }
        binding.tuneLastrun.paintFlags = binding.tuneLastrun.paintFlags or Paint.UNDERLINE_TEXT_FLAG
    }

    @Synchronized
    override fun onResume() {
        super.onResume()
        disposable += rxBus
            .toObservable(EventAutotuneUpdateGui::class.java)
            .observeOn(aapsSchedulers.main)
            .subscribe({ updateGui() }, fabricPrivacy::logException)
        checkNewDay()
        binding.tuneDays.value = autotunePlugin.lastNbDays.toDouble()
        updateGui()
    }

    @Synchronized
    override fun onPause() {
        super.onPause()
        disposable.clear()
    }

    @Synchronized
    private fun updateGui() {
        _binding ?: return
        profileStore = activePlugin.activeProfileSource.profile ?: ProfileStore(injector, JSONObject(), dateUtil)
        profileName = if (binding.profileList.text.toString() == rh.gs(R.string.active)) "" else binding.profileList.text.toString()
        profileFunction.getProfile()?.let { currentProfile ->
            profile = ATProfile(profileStore.getSpecificProfile(profileName)?.let { ProfileSealed.Pure(it) } ?: currentProfile, LocalInsulin(""), injector)
        }
        val profileList: ArrayList<CharSequence> = profileStore.getProfileList()
        profileList.add(0, rh.gs(R.string.active))
        context?.let { context ->
            binding.profileList.setAdapter(ArrayAdapter(context, R.layout.spinner_centered, profileList))
        } ?: return
        // set selected to actual profile
        if (autotunePlugin.selectedProfile.isNotEmpty())
            binding.profileList.setText(autotunePlugin.selectedProfile, false)
        else {
            binding.profileList.setText(profileList[0], false)
        }
        binding.autotuneRun.visibility = View.GONE
        binding.autotuneCheckInputProfile.visibility = View.GONE
        binding.autotuneCopylocal.visibility = View.GONE
        binding.autotuneUpdateProfile.visibility = View.GONE
        binding.autotuneRevertProfile.visibility = View.GONE
        binding.autotuneProfileswitch.visibility = View.GONE
        binding.autotuneCompare.visibility = View.GONE
        when {
            autotunePlugin.calculationRunning -> {
                binding.tuneWarning.text = rh.gs(R.string.autotune_warning_during_run)
            }

            autotunePlugin.lastRunSuccess     -> {
                binding.autotuneCopylocal.visibility = View.VISIBLE
                binding.autotuneUpdateProfile.visibility = autotunePlugin.updateButtonVisibility
                binding.autotuneRevertProfile.visibility = if (autotunePlugin.updateButtonVisibility == View.VISIBLE) View.GONE else View.VISIBLE
                binding.autotuneProfileswitch.visibility = View.VISIBLE
                binding.tuneWarning.text = rh.gs(R.string.autotune_warning_after_run)
                binding.autotuneCompare.visibility = View.VISIBLE
            }

            else                              -> {
                binding.autotuneRun.visibility = (profile?.isValid == true).toVisibility()
                binding.autotuneCheckInputProfile.visibility = View.VISIBLE
            }
        }
        binding.tuneLastrun.text = dateUtil.dateAndTimeString(autotunePlugin.lastRun)
        showResults()
    }

    private fun checkNewDay() {
        val runToday = autotunePlugin.lastRun > MidnightTime.calc(dateUtil.now() - autotunePlugin.autotuneStartHour * 3600 * 1000L) + autotunePlugin.autotuneStartHour * 3600 * 1000L
        if (runToday && autotunePlugin.result != "") {
            binding.tuneWarning.text = rh.gs(R.string.autotune_warning_after_run)
        } else if (!runToday || autotunePlugin.result.isEmpty()) { //if new day re-init result, default days, warning and button's visibility
            resetParam(!runToday)
        }
    }

    private fun addWarnings(): String {
        var warning = ""
        var nl = ""
        if (profileFunction.getProfile() == null) {
            warning = rh.gs(R.string.profileswitch_ismissing)
            return warning
        }
        profileFunction.getProfile()?.let { currentProfile ->
            profile = ATProfile(profileStore.getSpecificProfile(profileName)?.let { ProfileSealed.Pure(it) } ?: currentProfile, LocalInsulin(""), injector).also { profile ->
                if (!profile.isValid) return rh.gs(R.string.autotune_profile_invalid)
                if (profile.icSize > 1) {
                    warning += nl + rh.gs(R.string.autotune_ic_warning, profile.icSize, profile.ic)
                    nl = "\n"
                }
                if (profile.isfSize > 1) {
                    warning += nl + rh.gs(R.string.autotune_isf_warning, profile.isfSize, Profile.fromMgdlToUnits(profile.isf, profileFunction.getUnits()), profileFunction.getUnits().asText)
                }
            }
        }
        return warning
    }

    private fun resetParam(resetDay: Boolean = true) {
        binding.tuneWarning.text = addWarnings()
        if (resetDay)
            autotunePlugin.lastNbDays = sp.getInt(R.string.key_autotune_default_tune_days, 5).toString()
        autotunePlugin.result = ""
        binding.autotuneResults.removeAllViews()
        autotunePlugin.tunedProfile = null
        autotunePlugin.lastRunSuccess = false
        autotunePlugin.updateButtonVisibility = View.GONE
    }

    private val textWatcher = object : TextWatcher {
        override fun afterTextChanged(s: Editable) {
            updateGui()
        }

        override fun beforeTextChanged(s: CharSequence, start: Int, count: Int, after: Int) {}
        override fun onTextChanged(s: CharSequence, start: Int, before: Int, count: Int) {
            if (binding.tuneDays.text.isNotEmpty()) {
                try {
                    if (autotunePlugin.calculationRunning)
                        binding.tuneDays.value = autotunePlugin.lastNbDays.toDouble()
                    if (binding.tuneDays.text != autotunePlugin.lastNbDays) {
                        autotunePlugin.lastNbDays = binding.tuneDays.text
                        resetParam(false)
                    }
                } catch (e: Exception) {
                    fabricPrivacy.logException(e)
                }
            }
        }
    }

    private fun showResults() {
        context?.let { context ->
            runOnUiThread {
                _binding?.let {
                    binding.autotuneResults.removeAllViews()
                    if (autotunePlugin.result.isNotBlank()) {
                        var toMgDl = 1.0
                        if (profileFunction.getUnits() == GlucoseUnit.MMOL) toMgDl = Constants.MMOLL_TO_MGDL
                        val isfFormat = if (profileFunction.getUnits() == GlucoseUnit.MMOL) "%.2f" else "%.1f"
                        binding.autotuneResults.addView(
                            TableLayout(context).also { layout ->
                                layout.addView(
                                    TextView(context).apply {
                                        text = autotunePlugin.result
                                        setTypeface(typeface, Typeface.BOLD)
                                        gravity = Gravity.CENTER_HORIZONTAL
                                        setTextAppearance(android.R.style.TextAppearance_Material_Medium)
                                    })
                                autotunePlugin.tunedProfile?.let { tuned ->
                                    layout.addView(toTableRowHeader())
                                    val tuneInsulin = sp.getBoolean(R.string.key_autotune_tune_insulin_curve, false)
                                    if (tuneInsulin) {
                                        layout.addView(
                                            toTableRowValue(
                                                rh.gs(R.string.insulin_peak),
                                                autotunePlugin.pumpProfile.localInsulin.peak.toDouble(),
                                                tuned.localInsulin.peak.toDouble(),
                                                "%.0f"
                                            )
                                        )
                                        layout.addView(
                                            toTableRowValue(
                                                rh.gs(R.string.dia),
                                                Round.roundTo(autotunePlugin.pumpProfile.localInsulin.dia, 0.1),
                                                Round.roundTo(tuned.localInsulin.dia, 0.1),
                                                "%.1f"
                                            )
                                        )
                                    }
                                    layout.addView(
                                        toTableRowValue(
                                            rh.gs(R.string.isf_short),
                                            Round.roundTo(autotunePlugin.pumpProfile.isf / toMgDl, 0.001),
                                            Round.roundTo(tuned.isf / toMgDl, 0.001),
                                            isfFormat
                                        )
                                    )
                                    layout.addView(toTableRowValue(rh.gs(R.string.ic_short), Round.roundTo(autotunePlugin.pumpProfile.ic, 0.001), Round.roundTo(tuned.ic, 0.001), "%.2f"))
                                    layout.addView(
                                        TextView(context).apply {
                                            text = rh.gs(R.string.basal)
                                            setTypeface(typeface, Typeface.BOLD)
                                            gravity = Gravity.CENTER_HORIZONTAL
                                            setTextAppearance(android.R.style.TextAppearance_Material_Medium)
                                        }
                                    )
                                    layout.addView(toTableRowHeader(true))
                                    var totalPump = 0.0
                                    var totalTuned = 0.0
                                    for (h in 0 until tuned.basal.size) {
                                        val df = DecimalFormat("00")
                                        val time = df.format(h.toLong()) + ":00"
                                        totalPump += autotunePlugin.pumpProfile.basal[h]
                                        totalTuned += tuned.basal[h]
                                        layout.addView(toTableRowValue(time, autotunePlugin.pumpProfile.basal[h], tuned.basal[h], "%.3f", tuned.basalUntuned[h].toString()))
                                    }
                                    layout.addView(toTableRowValue("∑", totalPump, totalTuned, "%.3f", " "))
                                }
                            }
                        )
                    }
                    binding.autotuneResultsCard.visibility = if (autotunePlugin.calculationRunning && autotunePlugin.result.isEmpty()) View.GONE else View.VISIBLE
                }
            }
        }
    }

    private fun toTableRowHeader(basal: Boolean = false): TableRow =
        TableRow(context).also { header ->
            val lp = TableRow.LayoutParams(TableRow.LayoutParams.WRAP_CONTENT, TableRow.LayoutParams.WRAP_CONTENT).apply { weight = 1f }
            header.layoutParams = TableRow.LayoutParams(TableRow.LayoutParams.MATCH_PARENT, TableRow.LayoutParams.WRAP_CONTENT).apply { gravity = Gravity.CENTER_HORIZONTAL }
            header.addView(TextView(context).apply {
                layoutParams = lp.apply { column = 0 }
                textAlignment = TextView.TEXT_ALIGNMENT_CENTER
                text = if (basal) rh.gs(R.string.time) else rh.gs(R.string.autotune_param)
            })
            header.addView(TextView(context).apply {
                layoutParams = lp.apply { column = 1 }
                textAlignment = TextView.TEXT_ALIGNMENT_CENTER
                text = rh.gs(R.string.profile)
            })
            header.addView(TextView(context).apply {
                layoutParams = lp.apply { column = 2 }
                textAlignment = TextView.TEXT_ALIGNMENT_CENTER
                text = rh.gs(R.string.autotune_tunedprofile_name)
            })
            header.addView(TextView(context).apply {
                layoutParams = lp.apply { column = 3 }
                textAlignment = TextView.TEXT_ALIGNMENT_CENTER
                text = rh.gs(R.string.autotune_percent)
            })
            header.addView(TextView(context).apply {
                layoutParams = lp.apply { column = 4 }
                textAlignment = TextView.TEXT_ALIGNMENT_CENTER
                text = if (basal) rh.gs(R.string.autotune_missing) else " "
            })
        }

    private fun toTableRowValue(hour: String, inputValue: Double, tunedValue: Double, format: String = "%.3f", missing: String = ""): TableRow =
        TableRow(context).also { row ->
            val percentValue = Round.roundTo(tunedValue / inputValue * 100 - 100, 1.0).toInt().toString() + "%"
            val lp = TableRow.LayoutParams(TableRow.LayoutParams.WRAP_CONTENT, TableRow.LayoutParams.WRAP_CONTENT).apply { weight = 1f }
            row.layoutParams = TableRow.LayoutParams(TableRow.LayoutParams.MATCH_PARENT, TableRow.LayoutParams.WRAP_CONTENT).apply { gravity = Gravity.CENTER_HORIZONTAL }
            row.addView(TextView(context).apply {
                layoutParams = lp.apply { column = 0 }
                textAlignment = TextView.TEXT_ALIGNMENT_CENTER
                text = hour
            })
            row.addView(TextView(context).apply {
                layoutParams = lp.apply { column = 1 }
                textAlignment = TextView.TEXT_ALIGNMENT_CENTER
                text = String.format(format, inputValue)
            })
            row.addView(TextView(context).apply {
                layoutParams = lp.apply { column = 2 }
                textAlignment = TextView.TEXT_ALIGNMENT_CENTER
                text = String.format(format, tunedValue)
            })
            row.addView(TextView(context).apply {
                layoutParams = lp.apply { column = 3 }
                textAlignment = TextView.TEXT_ALIGNMENT_CENTER
                text = percentValue
            })
            row.addView(TextView(context).apply {
                layoutParams = lp.apply { column = 4 }
                textAlignment = TextView.TEXT_ALIGNMENT_CENTER
                text = missing
            })
        }

    private fun log(message: String) {
        autotuneFS.atLog("[Fragment] $message")
    }
}