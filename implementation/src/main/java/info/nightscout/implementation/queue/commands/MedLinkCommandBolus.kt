package info.nightscout.implementation.queue.commands

import dagger.android.HasAndroidInjector
import info.nightscout.interfaces.plugin.ActivePlugin
import info.nightscout.interfaces.pump.BolusProgressData.bolusEnded
import info.nightscout.interfaces.pump.DetailedBolusInfo
import info.nightscout.interfaces.pump.MedLinkPumpPluginBase
import info.nightscout.interfaces.pump.PumpEnactResult
import info.nightscout.interfaces.queue.Callback
import info.nightscout.interfaces.queue.Command
import info.nightscout.rx.bus.RxBus
import info.nightscout.rx.logging.LTag
import javax.inject.Inject

class MedLinkCommandBolus(
    injector: HasAndroidInjector,
    private val detailedBolusInfo: DetailedBolusInfo,
    callback: Callback?,
    type: CommandType
) : Command(injector, type, callback) {

    @Inject lateinit var rxBus: RxBus
    @Inject lateinit var activePlugin: ActivePlugin

    override fun execute() {
        val pump = activePlugin.activePump
        aapsLogger.info(LTag.PUMPQUEUE, "Command bolus plugin: ${pump} ")
        val func = { r: PumpEnactResult ->

                bolusEnded = true
                // rxBus.send(EventDismissBolusProgressIfRunning(r))
                aapsLogger.info(LTag.PUMPQUEUE, "Result success: ${r.success} enacted: ${r.enacted}")
                callback?.result(r)?.run()
                Unit
        }

        if (pump is MedLinkPumpPluginBase) {
            pump.deliverTreatment(detailedBolusInfo, func)
        }

        // val r = activePlugin.activePump

    }

    override fun status(): String {
        return (if (detailedBolusInfo.insulin > 0) "BOLUS " + rh.gs(info.nightscout.interfaces.R.string.format_insulin_units, detailedBolusInfo.insulin) else "") +
            if (detailedBolusInfo.carbs > 0) "CARBS " + rh.gs(info.nightscout.core.ui.R.string.format_carbs, detailedBolusInfo.carbs.toInt()) else ""
    }

    override fun log(): String =  "BOLUS"

    override fun cancel() {
        aapsLogger.debug(LTag.PUMPQUEUE, "Result cancel")
        callback?.result(PumpEnactResult(injector).success(false).comment(info.nightscout.core.ui.R.string.connectiontimedout))?.run()
    }
}