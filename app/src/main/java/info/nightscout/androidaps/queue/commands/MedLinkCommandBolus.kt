package info.nightscout.androidaps.queue.commands

import dagger.android.HasAndroidInjector
import info.nightscout.androidaps.R
import info.nightscout.androidaps.data.DetailedBolusInfo
import info.nightscout.androidaps.data.PumpEnactResult
import info.nightscout.androidaps.dialogs.BolusProgressDialog
import info.nightscout.androidaps.interfaces.ActivePluginProvider
import info.nightscout.androidaps.logging.LTag
import info.nightscout.androidaps.plugins.bus.RxBusWrapper
import info.nightscout.androidaps.plugins.general.overview.events.EventDismissBolusProgressIfRunning
import info.nightscout.androidaps.plugins.pump.common.hw.medlink.defs.MedLinkPumpDevice
import info.nightscout.androidaps.queue.Callback
import javax.inject.Inject

class MedLinkCommandBolus(
    injector: HasAndroidInjector,
    private val detailedBolusInfo: DetailedBolusInfo,
    callback: Callback?,
    type: CommandType
) : Command(injector, type, callback) {

    @Inject lateinit var rxBus: RxBusWrapper
    @Inject lateinit var activePlugin: ActivePluginProvider

    override fun execute() {
        val pump = activePlugin.activePump
        aapsLogger.info(LTag.PUMPQUEUE, "Command bolus plugin: ${pump} ")
        val func = { r: PumpEnactResult ->

                BolusProgressDialog.bolusEnded = true
                // rxBus.send(EventDismissBolusProgressIfRunning(r))
                aapsLogger.info(LTag.PUMPQUEUE, "Result success: ${r.success} enacted: ${r.enacted}")
                callback?.result(r)?.run()
                null
        }

        if (pump is MedLinkPumpDevice) {
            pump.deliverTreatment(detailedBolusInfo, func)
        }

        // val r = activePlugin.activePump

    }

    override fun status(): String {
        return (if (detailedBolusInfo.insulin > 0) "BOLUS " + resourceHelper.gs(R.string.formatinsulinunits, detailedBolusInfo.insulin) else "") +
            if (detailedBolusInfo.carbs > 0) "CARBS " + resourceHelper.gs(R.string.format_carbs, detailedBolusInfo.carbs.toInt()) else ""
    }
}