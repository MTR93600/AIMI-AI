package info.nightscout.androidaps.dialogs

import android.os.Bundle
import android.text.Spanned
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.Window
import android.view.WindowManager
import dagger.android.HasAndroidInjector
import dagger.android.support.DaggerDialogFragment
import info.nightscout.androidaps.Constants
import info.nightscout.androidaps.core.R
import info.nightscout.androidaps.data.Profile
import info.nightscout.androidaps.interfaces.ActivePluginProvider
import info.nightscout.androidaps.interfaces.DatabaseHelperInterface
import info.nightscout.androidaps.utils.DateUtil
import info.nightscout.androidaps.utils.HtmlHelper
import info.nightscout.androidaps.utils.resources.ResourceHelper
import kotlinx.android.synthetic.main.close.*
import kotlinx.android.synthetic.main.dialog_profileviewer.*
import org.json.JSONObject
import java.text.DecimalFormat
import javax.inject.Inject

class ProfileViewerDialog : DaggerDialogFragment() {
    @Inject lateinit var injector: HasAndroidInjector
    @Inject lateinit var resourceHelper: ResourceHelper
    @Inject lateinit var activePlugin: ActivePluginProvider
    @Inject lateinit var dateUtil: DateUtil
    @Inject lateinit var databaseHelper: DatabaseHelperInterface

    private var time: Long = 0

    enum class Mode(val i: Int) {
        RUNNING_PROFILE(1),
        CUSTOM_PROFILE(2),
        DB_PROFILE(3),
        PROFILE_COMPARE(4)
    }

    private var mode: Mode = Mode.RUNNING_PROFILE
    private var customProfileJson: String = ""
    private var customProfileJson2: String = ""
    private var customProfileName: String = ""
    private var customProfileUnits: String = Constants.MGDL

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?,
                              savedInstanceState: Bundle?): View? {
        // load data from bundle
        (savedInstanceState ?: arguments)?.let { bundle ->
            time = bundle.getLong("time", 0)
            mode = Mode.values()[bundle.getInt("mode", Mode.RUNNING_PROFILE.ordinal)]
            customProfileJson = bundle.getString("customProfile", "")
            customProfileUnits = bundle.getString("customProfileUnits", Constants.MGDL)
            customProfileName = bundle.getString("customProfileName", "")
            if (mode == Mode.PROFILE_COMPARE)
                customProfileJson2 = bundle.getString("customProfile2", "")
        }

        dialog?.window?.requestFeature(Window.FEATURE_NO_TITLE)
        dialog?.window?.setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_STATE_HIDDEN)
        isCancelable = true
        dialog?.setCanceledOnTouchOutside(false)

        return inflater.inflate(R.layout.dialog_profileviewer, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        close.setOnClickListener { dismiss() }

        val profile: Profile?
        val profile2: Profile?
        val profileName: String?
        val date: String?
        when (mode) {
            Mode.RUNNING_PROFILE -> {
                profile = activePlugin.activeTreatments.getProfileSwitchFromHistory(time)?.profileObject
                profile2 = null
                profileName = activePlugin.activeTreatments.getProfileSwitchFromHistory(time)?.customizedName
                date = dateUtil.dateAndTimeString(activePlugin.activeTreatments.getProfileSwitchFromHistory(time)?.date
                    ?: 0)
                profileview_datelayout.visibility = View.VISIBLE
            }

            Mode.CUSTOM_PROFILE  -> {
                profile = Profile(injector, JSONObject(customProfileJson), customProfileUnits)
                profile2 = null
                profileName = customProfileName
                date = ""
                profileview_datelayout.visibility = View.GONE
            }

            Mode.PROFILE_COMPARE -> {
                profile = Profile(injector, JSONObject(customProfileJson), customProfileUnits)
                profile2 = Profile(injector, JSONObject(customProfileJson2), customProfileUnits)
                profileName = customProfileName
                header_icon.setImageResource(R.drawable.ic_compare_profiles)
                date = ""
                profileview_datelayout.visibility = View.GONE
            }

            Mode.DB_PROFILE      -> {
                val profileList = databaseHelper.getProfileSwitchData(time, true)
                profile = if (profileList.isNotEmpty()) profileList[0].profileObject else null
                profile2 = null
                profileName = if (profileList.isNotEmpty()) profileList[0].customizedName else null
                date = if (profileList.isNotEmpty()) dateUtil.dateAndTimeString(profileList[0].date) else null
                profileview_datelayout.visibility = View.VISIBLE
            }
        }
        profileview_noprofile.visibility = View.VISIBLE

        if (mode == Mode.PROFILE_COMPARE)
            profile?.let { profile1 ->
                profile2?.let { profile2 ->
                    profileview_units.text = profile1.units
                    profileview_dia.text = HtmlHelper.fromHtml(formatColors("", profile1.dia, profile1.dia, DecimalFormat("0.00"), resourceHelper.gs(R.string.shorthour)))
                    val profileNames =profileName!!.split("\n").toTypedArray()
                    profileview_activeprofile.text = HtmlHelper.fromHtml(formatColors(profileNames[0], profileNames[1]))
                    profileview_date.text = date
                    profileview_ic.text = ics(profile1, profile2)
                    profileview_isf.text = isfs(profile1, profile2)
                    profileview_basal.text = basals(profile1, profile2)
                    profileview_target.text = HtmlHelper.fromHtml(formatColors("", profile1.targetList.replace("\n","<br>") + "<br>", profile2.targetList.replace("\n","<br>"), ""))
                    basal_graph.show(profile1, profile2)
                }

                profileview_noprofile.visibility = View.GONE
                profileview_invalidprofile.visibility = if (profile1.isValid("ProfileViewDialog")) View.GONE else View.VISIBLE
            }
        else
            profile?.let {
                profileview_units.text = it.units
                profileview_dia.text = resourceHelper.gs(R.string.format_hours, it.dia)
                profileview_activeprofile.text = profileName
                profileview_date.text = date
                profileview_ic.text = it.icList
                profileview_isf.text = it.isfList
                profileview_basal.text = it.basalList
                profileview_target.text = it.targetList
                basal_graph.show(it)

                profileview_noprofile.visibility = View.GONE
                profileview_invalidprofile.visibility = if (it.isValid("ProfileViewDialog")) View.GONE else View.VISIBLE
            }
    }

    override fun onStart() {
        super.onStart()
        dialog?.window?.setLayout(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT)
    }

    override fun onSaveInstanceState(bundle: Bundle) {
        super.onSaveInstanceState(bundle)
        bundle.putLong("time", time)
        bundle.putInt("mode", mode.ordinal)
        bundle.putString("customProfile", customProfileJson)
        bundle.putString("customProfileName", customProfileName)
        bundle.putString("customProfileUnits", customProfileUnits)
        if (mode == Mode.PROFILE_COMPARE)
            bundle.putString("customProfile2", customProfileJson2)
    }

    private fun formatColors(label: String, value1: Double, value2: Double, format: DecimalFormat, units: String): String {
        return formatColors(label, format.format(value1), format.format(value2), units)
    }

    private fun formatColors(label: String, text1: String, text2: String, units: String): String {
        var s = "<font color='${resourceHelper.gc(R.color.white)}'>$label</font>"
        s += "    "
        s += "<font color='${resourceHelper.gc(R.color.tempbasal)}'>$text1</font>"
        s += "    "
        s += "<font color='${resourceHelper.gc(R.color.examinedProfile)}'>$text2</font>"
        s += "    "
        s += "<font color='${resourceHelper.gc(R.color.white)}'>$units</font>"
        return s
    }

    private fun formatColors(text1: String, text2: String): String {
        var s = "<font color='${resourceHelper.gc(R.color.tempbasal)}'>$text1</font>"
        s += "<BR/>"
        s += "<font color='${resourceHelper.gc(R.color.examinedProfile)}'>$text2</font>"
        return s
    }

    private fun basals(profile1: Profile, profile2: Profile): Spanned {
        var prev1 = 0.0
        var prev2 = 0.0
        val s = StringBuilder()
        for (hour in 0..23) {
            val val1 = profile1.getBasalTimeFromMidnight(hour * 60 * 60)
            val val2 = profile2.getBasalTimeFromMidnight(hour * 60 * 60)
            if (val1 != prev1 || val2 != prev2) {
                s.append(formatColors(Profile.format_HH_MM(hour * 60 * 60), val1, val2, DecimalFormat("0.00"), resourceHelper.gs(R.string.profile_ins_units_per_hour)))
                s.append("<br>")
            }
            prev1 = val1
            prev2 = val2
        }
        s.append(formatColors(
            "    ∑ ",
            profile1.baseBasalSum(),
            profile2.baseBasalSum(),
            DecimalFormat("0.00"),
            resourceHelper.gs(R.string.insulin_unit_shortname)))
        return HtmlHelper.fromHtml(s.toString())
    }

    private fun ics(profile1: Profile, profile2: Profile): Spanned {
        var prev1 = 0.0
        var prev2 = 0.0
        val s = StringBuilder()
        for (hour in 0..23) {
            val val1 = profile1.getIcTimeFromMidnight(hour * 60 * 60)
            val val2 = profile2.getIcTimeFromMidnight(hour * 60 * 60)
            if (val1 != prev1 || val2 != prev2) {
                s.append(formatColors(Profile.format_HH_MM(hour * 60 * 60), val1, val2, DecimalFormat("0.0"), resourceHelper.gs(R.string.profile_carbs_per_unit)))
                s.append("<br>")
            }
            prev1 = val1
            prev2 = val2
        }
        return HtmlHelper.fromHtml(s.toString())
    }

    private fun isfs(profile1: Profile, profile2: Profile): Spanned {
        var prev1 = 0.0
        var prev2 = 0.0
        val s = StringBuilder()
        for (hour in 0..23) {
            val val1 = Profile.fromMgdlToUnits(profile1.getIsfMgdlTimeFromMidnight(hour * 60 * 60), profile1.units)
            val val2 = Profile.fromMgdlToUnits(profile2.getIsfMgdlTimeFromMidnight(hour * 60 * 60), profile1.units)
            if (val1 != prev1 || val2 != prev2) {
                s.append(formatColors(Profile.format_HH_MM(hour * 60 * 60), val1, val2, DecimalFormat("0.0"), profile1.units + resourceHelper.gs(R.string.profile_per_unit)))
                s.append("<br>")
            }
            prev1 = val1
            prev2 = val2
        }
        return HtmlHelper.fromHtml(s.toString())
    }
}
