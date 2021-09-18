package info.nightscout.androidaps.queue.commands

import dagger.android.HasAndroidInjector
import info.nightscout.androidaps.interfaces.ActivePluginProvider
import info.nightscout.androidaps.logging.LTag
import info.nightscout.androidaps.plugins.pump.insight.LocalInsightPlugin
import info.nightscout.androidaps.plugins.pump.medtronic.MedLinkMedtronicPumpPlugin
import info.nightscout.androidaps.queue.Callback
import javax.inject.Inject

class CommandStartPump(
    injector: HasAndroidInjector,
    callback: Callback?
) : Command(injector, CommandType.START_PUMP, callback) {

    @Inject lateinit var activePlugin: ActivePluginProvider

    override fun execute() {
        val pump = activePlugin.activePump
        aapsLogger.info(LTag.EVENTS, "starting pump")
        if (pump is MedLinkMedtronicPumpPlugin) {
            pump.startPump(callback);
        } else if (pump is LocalInsightPlugin) {
            val result = pump.startPump()
            callback?.result(result)?.run()
        }
    }

    override fun status(): String = "START PUMP"
}