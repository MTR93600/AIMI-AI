package info.nightscout.androidaps.queue.commands

import dagger.android.HasAndroidInjector
import info.nightscout.androidaps.data.PumpEnactResult
import info.nightscout.androidaps.dialogs.BolusProgressDialog
import info.nightscout.androidaps.interfaces.ActivePlugin
import info.nightscout.androidaps.interfaces.Profile
import info.nightscout.androidaps.interfaces.PumpSync
import info.nightscout.androidaps.plugins.pump.common.hw.medlink.defs.MedLinkPumpDevice
import info.nightscout.androidaps.queue.Callback
import info.nightscout.shared.logging.LTag
import javax.inject.Inject

class MedLinkCommandBasalPercent(
    injector: HasAndroidInjector,
    private val percent: Int,
    private val durationInMinutes: Int,
    private val enforceNew: Boolean,
    private val profile: Profile,
    private val tbrType: PumpSync.TemporaryBasalType,
    callback: Callback?
) : Command(injector, CommandType.BASAL_PROFILE, callback) {

    @Inject lateinit var activePlugin: ActivePlugin

    override fun execute() {
        val pump = activePlugin.activePump
        aapsLogger.info(LTag.PUMPQUEUE, "Command bolus plugin: ${pump} ")
        val func = { r: PumpEnactResult ->

            BolusProgressDialog.bolusEnded = true
            aapsLogger.debug(LTag.PUMPQUEUE, "Result percent: $percent durationInMinutes: $durationInMinutes success: ${r.success} enacted: ${r.enacted}")
            callback?.result(r)?.run()
            null
        }

        if (pump is MedLinkPumpDevice) {
            pump.setTempBasalPercent(percent, durationInMinutes, profile, enforceNew, func)
        }
    }

    override fun status(): String = "TEMP BASAL $percent% $durationInMinutes min"
    override fun log(): String = "TEMP BASAL PERCENT"
}