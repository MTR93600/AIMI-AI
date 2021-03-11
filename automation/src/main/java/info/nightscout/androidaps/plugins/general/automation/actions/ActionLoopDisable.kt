package info.nightscout.androidaps.plugins.general.automation.actions

import androidx.annotation.DrawableRes
import dagger.android.HasAndroidInjector
import info.nightscout.androidaps.automation.R
import info.nightscout.androidaps.data.PumpEnactResult
import info.nightscout.androidaps.events.EventRefreshOverview
import info.nightscout.androidaps.interfaces.CommandQueueProvider
import info.nightscout.androidaps.interfaces.ConfigBuilderInterface
import info.nightscout.androidaps.interfaces.LoopInterface
import info.nightscout.androidaps.interfaces.PluginBase
import info.nightscout.androidaps.interfaces.PluginType
import info.nightscout.androidaps.plugins.bus.RxBusWrapper
import info.nightscout.androidaps.queue.Callback
import info.nightscout.androidaps.utils.resources.ResourceHelper
import javax.inject.Inject

class ActionLoopDisable(injector: HasAndroidInjector) : Action(injector) {
    @Inject lateinit var loopPlugin: LoopInterface
    @Inject lateinit var resourceHelper: ResourceHelper
    @Inject lateinit var configBuilderPlugin: ConfigBuilderInterface
    @Inject lateinit var commandQueue: CommandQueueProvider
    @Inject lateinit var rxBus: RxBusWrapper

    override fun friendlyName(): Int = R.string.disableloop
    override fun shortDescription(): String = resourceHelper.gs(R.string.disableloop)
    @DrawableRes override fun icon(): Int = R.drawable.ic_stop_24dp

    override fun doAction(callback: Callback) {
        if ((loopPlugin as PluginBase).isEnabled()) {
            (loopPlugin as PluginBase).setPluginEnabled(PluginType.LOOP, false)
            configBuilderPlugin.storeSettings("ActionLoopDisable")
            commandQueue.cancelTempBasal(true, object : Callback() {
                override fun run() {
                    rxBus.send(EventRefreshOverview("ActionLoopDisable"))
                    callback.result(result).run()
                }
            })
        } else {
            callback.result(PumpEnactResult(injector).success(true).comment(R.string.alreadydisabled)).run()
        }
    }

    override fun isValid(): Boolean = true
}
