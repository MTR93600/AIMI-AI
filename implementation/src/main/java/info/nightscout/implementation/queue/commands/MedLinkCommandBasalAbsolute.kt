package info.nightscout.implementation.queue.commands

import dagger.android.HasAndroidInjector
import info.nightscout.interfaces.plugin.ActivePlugin
import info.nightscout.interfaces.profile.Profile
import info.nightscout.interfaces.pump.BolusProgressData.bolusEnded
import info.nightscout.interfaces.pump.MedLinkPumpPluginBase
import info.nightscout.interfaces.pump.PumpEnactResult
import info.nightscout.interfaces.pump.PumpSync
import info.nightscout.interfaces.queue.Callback
import info.nightscout.interfaces.queue.Command
import info.nightscout.rx.logging.LTag
import javax.inject.Inject

class MedLinkCommandBasalAbsolute(
    injector: HasAndroidInjector,
    private val absolute: Double,
    private val durationInMinutes: Int,
    private val enforceNew: Boolean,
    private val profile: Profile,
    tbrType: PumpSync.TemporaryBasalType,
    callback: Callback?
) : Command(injector, CommandType.BASAL_PROFILE, callback) {

    @Inject lateinit var activePlugin: ActivePlugin

    override fun execute() {
        val pump = activePlugin.activePump
        aapsLogger.info(LTag.PUMPQUEUE, "Command bolus plugin: ${pump} ")
        val func = { r: PumpEnactResult ->

            bolusEnded = true
            aapsLogger.debug(LTag.PUMPQUEUE, "Result absolute: $absolute durationInMinutes: $durationInMinutes success: ${r.success} enacted: ${r.enacted}")
            callback?.result(r)?.run()
            null
        }

        if (pump is MedLinkPumpPluginBase) {
            pump.setTempBasalAbsolute(absolute, durationInMinutes, profile, enforceNew, func)
        } else {
            pump.setTempBasalAbsolute(absolute, durationInMinutes, profile, enforceNew, PumpSync.TemporaryBasalType.EMULATED_PUMP_SUSPEND)
        }
    }

    override fun status(): String = "TEMP BASAL $absolute% $durationInMinutes min"
    override fun log(): String = "TEMP BASAL ABSOLUTE"
    override fun cancel() {
        aapsLogger.debug(LTag.PUMPQUEUE, "Result cancel")
        callback?.result(PumpEnactResult(injector).success(false).comment(info.nightscout.core.ui.R.string.connectiontimedout))?.run()
    }
}