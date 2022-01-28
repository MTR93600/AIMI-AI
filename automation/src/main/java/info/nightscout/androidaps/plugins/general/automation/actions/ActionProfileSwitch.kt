package info.nightscout.androidaps.plugins.general.automation.actions

import android.widget.LinearLayout
import androidx.annotation.DrawableRes
import dagger.android.HasAndroidInjector
import info.nightscout.androidaps.automation.R
import info.nightscout.androidaps.data.PumpEnactResult
import info.nightscout.androidaps.database.entities.UserEntry
import info.nightscout.androidaps.database.entities.UserEntry.Sources
import info.nightscout.androidaps.database.entities.ValueWithUnit
import info.nightscout.androidaps.interfaces.ActivePlugin
import info.nightscout.androidaps.interfaces.ProfileFunction
import info.nightscout.shared.logging.LTag
import info.nightscout.androidaps.logging.UserEntryLogger
import info.nightscout.androidaps.plugins.general.automation.elements.InputProfileName
import info.nightscout.androidaps.plugins.general.automation.elements.LabelWithElement
import info.nightscout.androidaps.plugins.general.automation.elements.LayoutBuilder
import info.nightscout.androidaps.queue.Callback
import info.nightscout.androidaps.utils.DateUtil
import info.nightscout.androidaps.utils.JsonHelper
import org.json.JSONObject
import javax.inject.Inject

class ActionProfileSwitch(injector: HasAndroidInjector) : Action(injector) {

    @Inject lateinit var activePlugin: ActivePlugin
    @Inject lateinit var profileFunction: ProfileFunction
    @Inject lateinit var dateUtil: DateUtil
    @Inject lateinit var uel: UserEntryLogger

    var inputProfileName: InputProfileName = InputProfileName(rh, activePlugin, "")

    override fun friendlyName(): Int = R.string.profilename
    override fun shortDescription(): String = rh.gs(R.string.changengetoprofilename, inputProfileName.value)
    @DrawableRes override fun icon(): Int = R.drawable.ic_actions_profileswitch

    override fun doAction(callback: Callback) {
        val activeProfileName = profileFunction.getProfileName()
        //Check for uninitialized profileName
        if (inputProfileName.value == "") {
            aapsLogger.error(LTag.AUTOMATION, "Selected profile not initialized")
            callback.result(PumpEnactResult(injector).success(false).comment(R.string.error_field_must_not_be_empty))?.run()
            return
        }
        if (profileFunction.getProfile() == null) {
            aapsLogger.error(LTag.AUTOMATION, "ProfileFunctions not initialized")
            callback.result(PumpEnactResult(injector).success(false).comment(R.string.noprofile))?.run()
            return
        }
        if (inputProfileName.value == activeProfileName) {
            aapsLogger.debug(LTag.AUTOMATION, "Profile is already switched")
            callback.result(PumpEnactResult(injector).success(true).comment(R.string.alreadyset))?.run()
            return
        }
        val profileStore = activePlugin.activeProfileSource.profile ?: return
        if (profileStore.getSpecificProfile(inputProfileName.value) == null) {
            aapsLogger.error(LTag.AUTOMATION, "Selected profile does not exist! - ${inputProfileName.value}")
            callback.result(PumpEnactResult(injector).success(false).comment(R.string.notexists))?.run()
            return
        }
        uel.log(
            UserEntry.Action.PROFILE_SWITCH, Sources.Automation, title,
            ValueWithUnit.SimpleString(inputProfileName.value),
            ValueWithUnit.Percent(100)
        )
        val result = profileFunction.createProfileSwitch(profileStore, inputProfileName.value, 0, 100, 0, dateUtil.now())
        callback.result(PumpEnactResult(injector).success(result).comment(R.string.ok))?.run()
    }

    override fun generateDialog(root: LinearLayout) {
        LayoutBuilder()
            .add(LabelWithElement(rh, rh.gs(R.string.profilename), "", inputProfileName))
            .build(root)
    }

    override fun hasDialog(): Boolean = true

    override fun toJSON(): String {
        val data = JSONObject().put("profileToSwitchTo", inputProfileName.value)
        return JSONObject()
            .put("type", this.javaClass.name)
            .put("data", data)
            .toString()
    }

    override fun fromJSON(data: String): Action {
        val o = JSONObject(data)
        inputProfileName.value = JsonHelper.safeGetString(o, "profileToSwitchTo", "")
        return this
    }

    override fun isValid(): Boolean = activePlugin.activeProfileSource.profile?.getSpecificProfile(inputProfileName.value) != null
}