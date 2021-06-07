package info.nightscout.androidaps.queue.commands

import dagger.android.HasAndroidInjector
import info.nightscout.androidaps.interfaces.ActivePluginProvider
import info.nightscout.androidaps.logging.LTag
import info.nightscout.androidaps.plugins.pump.common.hw.medlink.defs.MedLinkPumpDevice
import info.nightscout.androidaps.queue.Callback
import javax.inject.Inject

class MedLinkCommandCancelTempBasal(
    injector: HasAndroidInjector,
    private val enforceNew: Boolean,
    callback: Callback?
) : Command(injector, CommandType.TEMPBASAL, callback) {

    @Inject lateinit var activePlugin: ActivePluginProvider

    override fun execute() {
        val pump = activePlugin.activePump
        aapsLogger.info(LTag.PUMPQUEUE, "cancelling temp basal: ")
        if(pump is MedLinkPumpDevice){
            pump.cancelTempBasal(enforceNew, callback)
        }else {
            val r = activePlugin.activePump.cancelTempBasal(enforceNew)
            aapsLogger.debug(LTag.PUMPQUEUE, "Result success: ${r.success} enacted: ${r.enacted}")
            callback?.result(r)?.run()
        }
    }

    override fun status(): String = "CANCEL TEMPBASAL"
}