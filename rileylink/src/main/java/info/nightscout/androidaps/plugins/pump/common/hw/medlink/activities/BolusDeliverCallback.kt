package info.nightscout.androidaps.plugins.pump.common.hw.medlink.activities

import info.nightscout.androidaps.data.DetailedBolusInfo
import info.nightscout.androidaps.logging.AAPSLogger
import info.nightscout.androidaps.logging.LTag
import info.nightscout.androidaps.plugins.pump.common.MedLinkPumpPluginAbstract
import info.nightscout.androidaps.plugins.pump.common.data.MedLinkPumpStatus
import info.nightscout.androidaps.plugins.pump.common.hw.medlink.ble.CommandExecutor
import info.nightscout.androidaps.plugins.pump.common.hw.medlink.service.MedLinkStatusParser
import java.util.function.Supplier
import java.util.stream.Stream

/**
 * Created by Dirceu on 15/09/21.
 */
class BolusDeliverCallback(val pumpStatus: MedLinkPumpStatus, val plugin: MedLinkPumpPluginAbstract,
                           val aapsLogger: AAPSLogger, private val lastBolusInfo: DetailedBolusInfo) : BaseStringAggregatorCallback() {

    override fun apply(answer: Supplier<Stream<String>>): MedLinkStandardReturn<String> {
        var ans = answer.get().iterator()
        aapsLogger.info(LTag.EVENTS, "bolus delivering")
        aapsLogger.info(LTag.EVENTS, pumpStatus.toString())
        MedLinkStatusParser.parseBolusInfo(ans, pumpStatus)
        aapsLogger.info(LTag.EVENTS, "after parse")
        aapsLogger.info(LTag.EVENTS, pumpStatus.toString())

        if (pumpStatus.lastBolusAmount != null) {
            plugin.handleBolusDelivered(lastBolusInfo)
        }
        return super.apply(answer)
    }
}