package info.nightscout.androidaps.plugins.general.automation.triggers

import android.widget.LinearLayout
import com.google.common.base.Optional
import dagger.android.HasAndroidInjector
import info.nightscout.androidaps.Constants
import info.nightscout.androidaps.automation.R
import info.nightscout.androidaps.database.ValueWrapper
import info.nightscout.androidaps.interfaces.GlucoseUnit
import info.nightscout.androidaps.interfaces.Profile
import info.nightscout.shared.logging.LTag
import info.nightscout.androidaps.plugins.general.automation.elements.Comparator
import info.nightscout.androidaps.plugins.general.automation.elements.InputBg
import info.nightscout.androidaps.plugins.general.automation.elements.LabelWithElement
import info.nightscout.androidaps.plugins.general.automation.elements.LayoutBuilder
import info.nightscout.androidaps.plugins.general.automation.elements.StaticLabel
import info.nightscout.androidaps.utils.JsonHelper
import org.json.JSONObject

class TriggerTempTargetValue(injector: HasAndroidInjector) : Trigger(injector) {

    var ttValue = InputBg(profileFunction)
    var comparator = Comparator(rh)

    constructor(injector: HasAndroidInjector, value: Double, units: GlucoseUnit, compare: Comparator.Compare) : this(injector) {
        ttValue = InputBg(profileFunction, value, units)
        comparator = Comparator(rh, compare)
    }

    constructor(injector: HasAndroidInjector, triggerTempTarget: TriggerTempTargetValue) : this(injector) {
        ttValue = InputBg(profileFunction, triggerTempTarget.ttValue.value, triggerTempTarget.ttValue.units)
        comparator = Comparator(rh, triggerTempTarget.comparator.value)
    }

    fun comparator(comparator: Comparator.Compare): TriggerTempTargetValue {
        this.comparator.value = comparator
        return this
    }

    fun setUnits(units: GlucoseUnit): TriggerTempTargetValue {
        ttValue.units = units
        return this
    }

    fun setValue(value: Double): TriggerTempTargetValue {
        ttValue.value = value
        return this
    }

    override fun shouldRun(): Boolean {
        val tt = repository.getTemporaryTargetActiveAt(dateUtil.now()).blockingGet()
        if (tt is ValueWrapper.Absent && comparator.value == Comparator.Compare.IS_NOT_AVAILABLE) {
            aapsLogger.debug(LTag.AUTOMATION, "Ready for execution: " + friendlyDescription())
            return true
        }
        if (tt is ValueWrapper.Existing && comparator.value.check(tt.value.lowTarget, Profile.toMgdl(ttValue.value, ttValue.units))) {
            aapsLogger.debug(LTag.AUTOMATION, "Ready for execution: " + friendlyDescription())
            return true
        }
        aapsLogger.debug(LTag.AUTOMATION, "NOT ready for execution: " + friendlyDescription())
        return false
    }

    override fun dataJSON(): JSONObject =
        JSONObject()
            .put("tt", ttValue.value)
            .put("comparator", comparator.value.toString())
            .put("units", ttValue.units.asText)

    override fun fromJSON(data: String): Trigger {
        val d = JSONObject(data)
        ttValue.setUnits(GlucoseUnit.fromText(JsonHelper.safeGetString(d, "units", Constants.MGDL)))
        ttValue.value = JsonHelper.safeGetDouble(d, "tt")
        comparator.setValue(Comparator.Compare.valueOf(JsonHelper.safeGetString(d, "comparator")!!))
        return this
    }

    override fun friendlyName(): Int = R.string.careportal_temporarytargetvalue

    override fun friendlyDescription(): String  {
        return if (comparator.value == Comparator.Compare.IS_NOT_AVAILABLE)
            rh.gs(R.string.notemptarget)
        else
            rh.gs(if (ttValue.units == GlucoseUnit.MGDL) R.string.temptargetcomparedmgdl else R.string.temptargetcomparedmmol, rh.gs(comparator.value.stringRes), ttValue.value, ttValue.units)
    }

    override fun icon(): Optional<Int?> = Optional.of(R.drawable.ic_keyboard_tab)

    override fun duplicate(): Trigger = TriggerTempTargetValue(injector, this)

    override fun generateDialog(root: LinearLayout) {
        LayoutBuilder()
            .add(StaticLabel(rh, R.string.careportal_temporarytargetvalue, this))
            .add(comparator)
            .add(LabelWithElement(rh, rh.gs(R.string.target_u, ttValue.units), "", ttValue))
            .build(root)
    }
}