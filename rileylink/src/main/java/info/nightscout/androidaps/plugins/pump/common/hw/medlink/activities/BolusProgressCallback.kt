package info.nightscout.androidaps.plugins.pump.common.hw.medlink.activities

import android.os.SystemClock
import info.nightscout.androidaps.logging.AAPSLogger
import info.nightscout.androidaps.logging.LTag
import info.nightscout.androidaps.plugins.bus.RxBusWrapper
import info.nightscout.androidaps.plugins.general.overview.events.EventDismissBolusProgressIfRunning
import info.nightscout.androidaps.plugins.general.overview.events.EventOverviewBolusProgress
import info.nightscout.androidaps.plugins.general.overview.events.EventOverviewBolusProgress.t
import info.nightscout.androidaps.plugins.pump.common.R
import info.nightscout.androidaps.plugins.pump.common.data.MedLinkPumpStatus
import info.nightscout.androidaps.plugins.pump.common.hw.medlink.ble.CommandExecutor
import info.nightscout.androidaps.plugins.pump.common.hw.medlink.service.MedLinkStatusParser

import info.nightscout.androidaps.utils.resources.ResourceHelper
import java.util.function.Supplier
import java.util.stream.Stream
import kotlin.math.roundToInt

data class BolusProgressCallback(val pumpStatus: MedLinkPumpStatus,
                                 val resourceHelper: ResourceHelper,
                                 val rxBus: RxBusWrapper,
                                 private val commandExecutor: CommandExecutor?,
                                 val aapsLogger: AAPSLogger) : BaseStringAggregatorCallback() {

    var resend = true;
    fun resend(): Boolean {
        return resend;
    }

    override fun apply(answer: Supplier<Stream<String>>): MedLinkStandardReturn<String> {
        var ans = answer.get().iterator()
        MedLinkStatusParser.parseBolusInfo(ans, pumpStatus)
        aapsLogger.info(LTag.PUMPBTCOMM, ""+pumpStatus.lastBolusAmount)
        aapsLogger.info(LTag.PUMPBTCOMM, ""+pumpStatus.bolusDeliveredAmount)
        aapsLogger.info(LTag.PUMPBTCOMM, ""+pumpStatus.lastBolusInfo)
        if(pumpStatus.lastBolusAmount !=null ) {
            val bolusEvent = EventOverviewBolusProgress
            bolusEvent.t = t
            bolusEvent.status = resourceHelper.gs(R.string.bolusdelivering, pumpStatus.bolusDeliveredAmount, pumpStatus.lastBolusAmount)
            bolusEvent.percent = (pumpStatus.bolusDeliveredAmount / pumpStatus.lastBolusAmount).roundToInt()

            rxBus.send(bolusEvent)
            if (bolusEvent.percent == 100) {
                SystemClock.sleep(200)
                bolusEvent.status = resourceHelper.gs(R.string.bolusdelivering, pumpStatus.lastBolusAmount)
                bolusEvent.percent = 100
                rxBus.send(bolusEvent)
                SystemClock.sleep(1000)

                // rxBus.send(bolusEvent)
                rxBus.send(EventDismissBolusProgressIfRunning(null))
                resend = false
            }else commandExecutor?.clearExecutedCommand()
        }
        return super.apply(answer)
    }

}