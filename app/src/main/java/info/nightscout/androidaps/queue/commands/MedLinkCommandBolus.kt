package info.nightscout.androidaps.queue.commands

import dagger.android.HasAndroidInjector
import info.nightscout.androidaps.R
import info.nightscout.androidaps.data.DetailedBolusInfo
import info.nightscout.androidaps.data.PumpEnactResult
import info.nightscout.androidaps.dialogs.BolusProgressDialog
import info.nightscout.androidaps.interfaces.ActivePlugin
import info.nightscout.androidaps.plugins.bus.RxBus
import info.nightscout.androidaps.plugins.pump.common.hw.medlink.defs.MedLinkPumpDevice
import info.nightscout.androidaps.queue.Callback
import info.nightscout.shared.logging.LTag
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

                BolusProgressDialog.bolusEnded = true
                // rxBus.send(EventDismissBolusProgressIfRunning(r))
                aapsLogger.info(LTag.PUMPQUEUE, "Result success: ${r.success} enacted: ${r.enacted}")
                callback?.result(r)?.run()
                Unit
        }

        if (pump is MedLinkPumpDevice) {
            pump.deliverTreatment(detailedBolusInfo, func)
        }

        // val r = activePlugin.activePump

    }

    override fun status(): String {
        return (if (detailedBolusInfo.insulin > 0) "BOLUS " + rh.gs(R.string.formatinsulinunits, detailedBolusInfo.insulin) else "") +
            if (detailedBolusInfo.carbs > 0) "CARBS " + rh.gs(R.string.format_carbs, detailedBolusInfo.carbs.toInt()) else ""
    }

    override fun log(): String =  "BOLUS"
}