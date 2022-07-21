package info.nightscout.androidaps.tile.source

import android.content.Context
import android.content.res.Resources
import info.nightscout.androidaps.R
import info.nightscout.androidaps.interaction.actions.BackgroundActionActivity
import info.nightscout.androidaps.interaction.actions.TempTargetActivity
import info.nightscout.shared.logging.AAPSLogger
import info.nightscout.shared.sharedPreferences.SP
import info.nightscout.shared.weardata.EventData
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class TempTargetSource @Inject constructor(context: Context, sp: SP, aapsLogger: AAPSLogger) : StaticTileSource(context, sp, aapsLogger) {

    override val preferencePrefix = "tile_tempt_"

    override fun getActions(resources: Resources): List<StaticAction> {
        val message = resources.getString(R.string.action_tempt_confirmation)
        return listOf(
            StaticAction(
                settingName = "activity",
                buttonText = resources.getString(R.string.temp_target_activity),
                iconRes = R.drawable.ic_target_activity,
                activityClass = BackgroundActionActivity::class.java.name,
                message = message,
                // actionString = "temptarget false 90 8.0 8.0",
                // actionString = "temptarget preset activity",
                action = EventData.ActionTempTargetPreCheck(EventData.ActionTempTargetPreCheck.TempTargetCommand.PRESET_ACTIVITY)
            ),
            StaticAction(
                settingName = "eating_soon",
                buttonText = resources.getString(R.string.temp_target_eating_soon),
                iconRes = R.drawable.ic_target_eatingsoon,
                activityClass = BackgroundActionActivity::class.java.name,
                message = message,
                // actionString = "temptarget false 45 4.5 4.5",
                // actionString = "temptarget preset eating",
                action = EventData.ActionTempTargetPreCheck(EventData.ActionTempTargetPreCheck.TempTargetCommand.PRESET_EATING)
            ),
            StaticAction(
                settingName = "hypo",
                buttonText = resources.getString(R.string.temp_target_hypo),
                iconRes = R.drawable.ic_target_hypo,
                activityClass = BackgroundActionActivity::class.java.name,
                message = message,
                // actionString = "temptarget false 45 7.0 7.0",
                // actionString = "temptarget preset hypo",
                action = EventData.ActionTempTargetPreCheck(EventData.ActionTempTargetPreCheck.TempTargetCommand.PRESET_HYPO)
            ),
            StaticAction(
                settingName = "manual",
                buttonText = resources.getString(R.string.temp_target_manual),
                iconRes = R.drawable.ic_target_manual,
                activityClass = TempTargetActivity::class.java.name,
                action = null
            ),
            StaticAction(
                settingName = "cancel",
                buttonText = resources.getString(R.string.generic_cancel),
                iconRes = R.drawable.ic_target_cancel,
                activityClass = BackgroundActionActivity::class.java.name,
                message = message,
                //actionString = "temptarget cancel",
                action = EventData.ActionTempTargetPreCheck(EventData.ActionTempTargetPreCheck.TempTargetCommand.CANCEL)
            )
        )
    }

    override fun getResourceReferences(resources: Resources): List<Int> {
        return getActions(resources).map { it.iconRes }
    }

    override fun getDefaultConfig(): Map<String, String> {
        return mapOf(
            "tile_tempt_1" to "activity",
            "tile_tempt_2" to "eating_soon",
            "tile_tempt_3" to "hypo",
            "tile_tempt_4" to "manual"
        )
    }
}
