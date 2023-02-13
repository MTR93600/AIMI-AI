package info.nightscout.implementation.queue.commands

import dagger.android.HasAndroidInjector
import info.nightscout.database.impl.AppRepository
import info.nightscout.interfaces.plugin.ActivePlugin
import info.nightscout.interfaces.pump.DetailedBolusInfo
import info.nightscout.interfaces.pump.MedLinkPumpPluginBase
import info.nightscout.interfaces.pump.PumpEnactResult
import info.nightscout.interfaces.queue.Callback
import info.nightscout.interfaces.queue.Command
import info.nightscout.rx.logging.LTag
import info.nightscout.shared.utils.DateUtil
import info.nightscout.shared.utils.T
import javax.inject.Inject

class MedLinkCommandSMBBolus(
    injector: HasAndroidInjector,
    private val detailedBolusInfo: DetailedBolusInfo,
    callback: Callback?
) : Command(injector, CommandType.SMB_BOLUS, callback) {

    @Inject lateinit var repository: AppRepository
    @Inject lateinit var dateUtil: DateUtil
    @Inject lateinit var activePlugin: ActivePlugin

    override fun execute() {
        val r: PumpEnactResult
        val lastBolusTime = repository.getLastBolusRecord()?.timestamp ?: 0L
        if (lastBolusTime != 0L && lastBolusTime + T.mins(3).msecs() > dateUtil.now()) {
            aapsLogger.debug(LTag.PUMPQUEUE, "SMB requested but still in 3 min interval")
            r = PumpEnactResult(injector).enacted(false).success(false).comment("SMB requested but still in 3 min interval")
            callback?.result(r)?.run()
            aapsLogger.debug(LTag.PUMPQUEUE, "Result success: ${r.success} enacted: ${r.enacted}")
        } else if (detailedBolusInfo.deliverAtTheLatest != 0L && detailedBolusInfo.deliverAtTheLatest + T.mins(1).msecs() > System.currentTimeMillis()) {
            val func = { it: PumpEnactResult ->
                callback?.result(it)?.run()
                aapsLogger.debug(LTag.PUMPQUEUE, "Result success: ${it.success} enacted: ${it.enacted}")
            }
            val pump = activePlugin.activePump
            if (pump is MedLinkPumpPluginBase) {
                pump.deliverTreatment(detailedBolusInfo, func)
            }
        } else {
            r = PumpEnactResult(injector).enacted(false).success(false).comment("SMB request too old")
            aapsLogger.debug(LTag.PUMPQUEUE, "SMB bolus canceled. deliverAt: " + dateUtil.dateAndTimeString(detailedBolusInfo.deliverAtTheLatest))
            callback?.result(r)?.run()
            aapsLogger.debug(LTag.PUMPQUEUE, "Result success: ${r.success} enacted: ${r.enacted}")
        }
    }

    override fun status(): String = "SMB BOLUS ${rh.gs(info.nightscout.interfaces.R.string.format_insulin_units, detailedBolusInfo.insulin)}"
    override fun log(): String ="SMB BOLUS"

    override fun cancel() {
        aapsLogger.debug(LTag.PUMPQUEUE, "Result cancel")
        callback?.result(PumpEnactResult(injector).success(false).comment(info.nightscout.core.ui.R.string.connectiontimedout))?.run()
    }
}