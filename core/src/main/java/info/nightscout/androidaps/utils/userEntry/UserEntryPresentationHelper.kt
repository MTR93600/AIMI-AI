package info.nightscout.androidaps.utils.userEntry

import android.text.Spanned
import dagger.Reusable
import info.nightscout.androidaps.Constants
import info.nightscout.androidaps.core.R
import info.nightscout.androidaps.database.entities.UserEntry
import info.nightscout.androidaps.database.entities.UserEntry.Action
import info.nightscout.androidaps.database.entities.UserEntry.ColorGroup
import info.nightscout.androidaps.database.entities.UserEntry.Sources
import info.nightscout.androidaps.database.entities.ValueWithUnit
import info.nightscout.androidaps.interfaces.GlucoseUnit
import info.nightscout.androidaps.interfaces.Profile
import info.nightscout.androidaps.interfaces.ProfileFunction
import info.nightscout.androidaps.utils.DateUtil
import info.nightscout.androidaps.utils.DecimalFormatter
import info.nightscout.androidaps.utils.HtmlHelper
import info.nightscout.androidaps.utils.Translator
import info.nightscout.androidaps.utils.resources.ResourceHelper
import javax.inject.Inject

@Reusable
class UserEntryPresentationHelper @Inject constructor(
    private val translator: Translator,
    private val profileFunction: ProfileFunction,
    private val rh: ResourceHelper,
    private val dateUtil: DateUtil
) {

    fun colorId(colorGroup: ColorGroup): Int = when (colorGroup) {
        ColorGroup.InsulinTreatment -> R.color.iob
        ColorGroup.BasalTreatment   -> R.color.basal
        ColorGroup.CarbTreatment    -> R.color.carbs
        ColorGroup.TT               -> R.color.tempTargetConfirmation
        ColorGroup.Profile          -> R.color.white
        ColorGroup.Loop             -> R.color.loopClosed
        ColorGroup.Careportal       -> R.color.high
        ColorGroup.Pump             -> R.color.loopDisconnected
        ColorGroup.Aaps             -> R.color.defaulttext
        else                        -> R.color.defaulttext
    }

    fun iconId(source: Sources): Int = when (source) {
        Sources.TreatmentDialog     -> R.drawable.icon_insulin_carbs
        Sources.InsulinDialog       -> R.drawable.ic_bolus
        Sources.CarbDialog          -> R.drawable.ic_cp_bolus_carbs
        Sources.WizardDialog        -> R.drawable.ic_calculator
        Sources.QuickWizard         -> R.drawable.ic_quick_wizard
        Sources.ExtendedBolusDialog -> R.drawable.ic_actions_startextbolus
        Sources.TTDialog            -> R.drawable.ic_temptarget_high
        Sources.ProfileSwitchDialog -> R.drawable.ic_actions_profileswitch
        Sources.LoopDialog          -> R.drawable.ic_loop_closed
        Sources.TempBasalDialog     -> R.drawable.ic_actions_starttempbasal
        Sources.CalibrationDialog   -> R.drawable.ic_calibration
        Sources.FillDialog          -> R.drawable.ic_cp_pump_canula
        Sources.BgCheck             -> R.drawable.ic_cp_bgcheck
        Sources.SensorInsert        -> R.drawable.ic_cp_cgm_insert
        Sources.BatteryChange       -> R.drawable.ic_cp_pump_battery
        Sources.Note                -> R.drawable.ic_cp_note
        Sources.Exercise            -> R.drawable.ic_cp_exercise
        Sources.Question            -> R.drawable.ic_cp_question
        Sources.Announcement        -> R.drawable.ic_cp_announcement
        Sources.Actions             -> R.drawable.ic_action
        Sources.Automation          -> R.drawable.ic_automation
        Sources.BG                  -> R.drawable.ic_generic_cgm
        Sources.Dexcom              -> R.drawable.ic_dexcom_g6
        Sources.Eversense           -> R.drawable.ic_eversense
        Sources.Glimp               -> R.drawable.ic_glimp
        Sources.MM640g              -> R.drawable.ic_generic_cgm
        Sources.NSClientSource      -> R.drawable.ic_nsclient_bg
        Sources.PocTech             -> R.drawable.ic_poctech
        Sources.Tomato              -> R.drawable.ic_sensor
        Sources.Glunovo             -> R.drawable.ic_glunovo
        Sources.Xdrip               -> R.drawable.ic_blooddrop_48
        Sources.LocalProfile        -> R.drawable.ic_local_profile
        Sources.Loop                -> R.drawable.ic_loop_closed_white
        Sources.Maintenance         -> R.drawable.ic_maintenance
        Sources.NSClient            -> R.drawable.ic_nightscout_syncs
        Sources.NSProfile           -> R.drawable.ic_nightscout_profile
        Sources.Objectives          -> R.drawable.ic_graduation
        Sources.Pump                -> R.drawable.ic_generic_icon
        Sources.Dana                -> R.drawable.ic_danars_128
        Sources.DanaR               -> R.drawable.ic_danars_128
        Sources.DanaRC              -> R.drawable.ic_danars_128
        Sources.DanaRv2             -> R.drawable.ic_danars_128
        Sources.DanaRS              -> R.drawable.ic_danars_128
        Sources.DanaI               -> R.drawable.ic_danai_128
        Sources.DiaconnG8           -> R.drawable.ic_diaconn_g8
        Sources.Insight             -> R.drawable.ic_insight_128
        Sources.Combo               -> R.drawable.ic_combo_128
        Sources.Medtronic           -> R.drawable.ic_veo_128
        Sources.Omnipod             -> R.drawable.ic_pod_128
        Sources.OmnipodEros         -> R.drawable.ic_pod_128
        Sources.OmnipodDash         -> R.drawable.ic_pod_128
        Sources.MDI                 -> R.drawable.ic_ict
        Sources.VirtualPump         -> R.drawable.ic_virtual_pump
        Sources.SMS                 -> R.drawable.ic_sms
        Sources.Treatments          -> R.drawable.ic_treatments
        Sources.Wear                -> R.drawable.ic_watch
        Sources.Food                -> R.drawable.ic_food
        Sources.Stats               -> R.drawable.ic_cp_stats
        Sources.ConfigBuilder       -> R.drawable.ic_cogs
        Sources.Overview            -> R.drawable.ic_home
        Sources.Aaps                -> R.drawable.ic_notif_aaps
        Sources.Unknown             -> R.drawable.ic_generic_icon
    }

    fun actionToColoredString(action: Action): Spanned = when (action) {
        Action.TREATMENT -> HtmlHelper.fromHtml(coloredAction(Action.BOLUS) + " + " + coloredAction(Action.CARBS))
        else             -> HtmlHelper.fromHtml(coloredAction(action))
    }

    private fun coloredAction(action: Action): String = "<font color='${rh.gc(colorId(action.colorGroup))}'>${translator.translate(action)}</font>"

    fun listToPresentationString(list: List<ValueWithUnit?>) =
        list.joinToString(separator = "  ", transform = this::toPresentationString)

    private fun toPresentationString(valueWithUnit: ValueWithUnit?): String = when (valueWithUnit) {
        is ValueWithUnit.Gram                  -> "${valueWithUnit.value}${translator.translate(valueWithUnit)}"
        is ValueWithUnit.Hour                  -> "${valueWithUnit.value}${translator.translate(valueWithUnit)}"
        is ValueWithUnit.Minute                -> "${valueWithUnit.value}${translator.translate(valueWithUnit)}"
        is ValueWithUnit.Percent               -> "${valueWithUnit.value}${translator.translate(valueWithUnit)}"
        is ValueWithUnit.Insulin               -> DecimalFormatter.to2Decimal(valueWithUnit.value) + translator.translate(valueWithUnit)
        is ValueWithUnit.UnitPerHour           -> DecimalFormatter.to2Decimal(valueWithUnit.value) + translator.translate(valueWithUnit)
        is ValueWithUnit.SimpleInt             -> valueWithUnit.value.toString()
        is ValueWithUnit.SimpleString          -> valueWithUnit.value
        is ValueWithUnit.TherapyEventMeterType -> translator.translate(valueWithUnit.value)
        is ValueWithUnit.TherapyEventTTReason  -> translator.translate(valueWithUnit.value)
        is ValueWithUnit.OfflineEventReason    -> translator.translate(valueWithUnit.value)
        is ValueWithUnit.TherapyEventType      -> translator.translate(valueWithUnit.value)
        is ValueWithUnit.Timestamp             -> dateUtil.dateAndTimeAndSecondsString(valueWithUnit.value)

        is ValueWithUnit.Mgdl                  -> {
            if (profileFunction.getUnits() == GlucoseUnit.MGDL) DecimalFormatter.to0Decimal(valueWithUnit.value) + rh.gs(R.string.mgdl)
            else DecimalFormatter.to1Decimal(valueWithUnit.value * Constants.MGDL_TO_MMOLL) + rh.gs(R.string.mmol)
        }

        is ValueWithUnit.Mmoll                 -> {
            if (profileFunction.getUnits() == GlucoseUnit.MMOL) DecimalFormatter.to1Decimal(valueWithUnit.value) + rh.gs(R.string.mmol)
            else DecimalFormatter.to0Decimal(valueWithUnit.value * Constants.MMOLL_TO_MGDL) + rh.gs(R.string.mgdl)
        }

        ValueWithUnit.UNKNOWN                  -> ""
        null                                   -> ""
    }

    fun userEntriesToCsv(userEntries: List<UserEntry>): String {
        return getCsvHeader() + userEntries.joinToString("\n") { entry -> getCsvEntry(entry) }
    }

    private fun getCsvHeader() = rh.gs(R.string.ue_csv_header,
        csvString(R.string.ue_timestamp),
        csvString(R.string.date),
        csvString(R.string.ue_utc_offset),
        csvString(R.string.ue_action),
        csvString(R.string.eventtype),
        csvString(R.string.ue_source),
        csvString(R.string.careportal_note),
        csvString(R.string.ue_string),
        csvString(R.string.event_time_label),
        csvString(if (profileFunction.getUnits() == GlucoseUnit.MGDL) R.string.mgdl else R.string.mmol),
        csvString(R.string.shortgram),
        csvString(R.string.insulin_unit_shortname),
        csvString(R.string.profile_ins_units_per_hour),
        csvString(R.string.shortpercent),
        csvString(R.string.shorthour),
        csvString(R.string.shortminute),
        csvString(R.string.ue_none)
    ) + "\n"

    private fun getCsvEntry(entry: UserEntry): String {
        val fullValueWithUnitList = ArrayList(entry.values)
        val timestampRec = entry.timestamp.toString()
        val dateTimestampRev = dateUtil.dateAndTimeAndSecondsString(entry.timestamp)
        val utcOffset = dateUtil.timeStringFromSeconds((entry.utcOffset / 1000).toInt())
        val action = csvString(entry.action)
        var therapyEvent = ""
        val source = translator.translate(entry.source)
        val note = csvString(entry.note)
        var simpleString = ""
        var timestamp = ""
        var bg = ""
        var gram = ""
        var insulin = ""
        var unitPerHour = ""
        var percent = ""
        var hour = ""
        var minute = ""
        var noUnit = ""

        for (valueWithUnit in fullValueWithUnitList.filterNotNull()) {
            when (valueWithUnit) {
                is ValueWithUnit.Gram                  -> gram = valueWithUnit.value.toString()
                is ValueWithUnit.Hour                  -> hour = valueWithUnit.value.toString()
                is ValueWithUnit.Minute                -> minute = valueWithUnit.value.toString()
                is ValueWithUnit.Percent               -> percent = valueWithUnit.value.toString()
                is ValueWithUnit.Insulin               -> insulin = DecimalFormatter.to2Decimal(valueWithUnit.value)
                is ValueWithUnit.UnitPerHour           -> unitPerHour = DecimalFormatter.to2Decimal(valueWithUnit.value)
                is ValueWithUnit.SimpleInt             -> noUnit = noUnit.addWithSeparator(valueWithUnit.value)
                is ValueWithUnit.SimpleString          -> simpleString = simpleString.addWithSeparator(valueWithUnit.value)
                is ValueWithUnit.TherapyEventMeterType -> therapyEvent = therapyEvent.addWithSeparator(translator.translate(valueWithUnit.value))
                is ValueWithUnit.TherapyEventTTReason  -> therapyEvent = therapyEvent.addWithSeparator(translator.translate(valueWithUnit.value))
                is ValueWithUnit.OfflineEventReason    -> therapyEvent = therapyEvent.addWithSeparator(translator.translate(valueWithUnit.value))
                is ValueWithUnit.TherapyEventType      -> therapyEvent = therapyEvent.addWithSeparator(translator.translate(valueWithUnit.value))
                is ValueWithUnit.Timestamp             -> timestamp = dateUtil.dateAndTimeAndSecondsString(valueWithUnit.value)

                is ValueWithUnit.Mgdl                  ->
                    bg = Profile.toUnitsString(valueWithUnit.value, valueWithUnit.value * Constants.MGDL_TO_MMOLL, profileFunction.getUnits())

                is ValueWithUnit.Mmoll                 ->
                    bg = Profile.toUnitsString(valueWithUnit.value * Constants.MMOLL_TO_MGDL, valueWithUnit.value , profileFunction.getUnits())

                ValueWithUnit.UNKNOWN                  -> Unit
            }
        }

        therapyEvent = csvString(therapyEvent)
        simpleString = csvString(simpleString)
        noUnit = csvString(noUnit)
        return "$timestampRec;$dateTimestampRev;$utcOffset;$action;$therapyEvent;$source;$note;$simpleString;$timestamp;$bg;$gram;$insulin;$unitPerHour;$percent;$hour;$minute;$noUnit"
    }

    private fun csvString(action: Action): String = "\"" + translator.translate(action).replace("\"", "\"\"").replace("\n", " / ") + "\""
    private fun csvString(id: Int): String = if (id != 0) "\"" + rh.gs(id).replace("\"", "\"\"").replace("\n", " / ") + "\"" else ""
    private fun csvString(s: String): String = if (s != "") "\"" + s.replace("\"", "\"\"").replace("\n", " / ") + "\"" else ""

    private fun String.addWithSeparator(add: Any) =
        this + (if (this.isBlank()) "" else " / ") + add.toString()
}