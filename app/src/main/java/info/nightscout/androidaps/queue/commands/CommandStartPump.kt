package info.nightscout.androidaps.queue.commands

import dagger.android.HasAndroidInjector
import info.nightscout.androidaps.R
import info.nightscout.androidaps.interfaces.ActivePlugin
import info.nightscout.androidaps.plugins.pump.insight.LocalInsightPlugin
import info.nightscout.androidaps.queue.Callback
import javax.inject.Inject

class CommandStartPump(
    injector: HasAndroidInjector,
    callback: Callback?
) : Command(injector, CommandType.START_PUMP, callback) {

    @Inject lateinit var activePlugin: ActivePlugin

    override fun execute() {
        val pump = activePlugin.activePump
        if (pump is LocalInsightPlugin) {
            val result = pump.startPump()
            callback?.result(result)?.run()
        }
    }

    override fun status(): String = rh.gs(R.string.start_pump)

    override fun log(): String = "START PUMP"
}