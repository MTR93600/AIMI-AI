package info.nightscout.androidaps.plugins.pump.medtronic.comm.activities

import info.nightscout.androidaps.interfaces.BgSync.BgHistory
import info.nightscout.androidaps.plugins.pump.common.hw.medlink.activities.BaseStatusCallback
import info.nightscout.androidaps.plugins.pump.common.hw.medlink.activities.MedLinkStandardReturn
import info.nightscout.androidaps.plugins.pump.common.hw.medlink.service.MedLinkStatusParser.Companion.parseStatus
import info.nightscout.androidaps.plugins.pump.medtronic.MedLinkMedtronicPumpPlugin
import info.nightscout.androidaps.plugins.pump.medtronic.driver.MedLinkMedtronicPumpStatus
import info.nightscout.pump.common.data.MedLinkPumpStatus
import info.nightscout.pump.core.defs.PumpDeviceState
import java.util.*
import java.util.function.Supplier
import java.util.regex.Pattern
import java.util.stream.Collectors
import java.util.stream.Stream
import info.nightscout.rx.logging.AAPSLogger
import info.nightscout.rx.logging.LTag

/**
 * Created by Dirceu on 19/01/21.
 */
class StatusCallback(
    private val aapsLogger: AAPSLogger,
    private val medLinkPumpPlugin: MedLinkMedtronicPumpPlugin, private val medLinkPumpStatus: MedLinkMedtronicPumpStatus
) : BaseStatusCallback(medLinkPumpStatus) {

    override fun apply(s: Supplier<Stream<String>>): MedLinkStandardReturn<MedLinkPumpStatus> {
        val f = MedLinkStandardReturn<MedLinkPumpStatus>(s, medLinkPumpStatus)
        val answer = f.getAnswer()
        aapsLogger.info(LTag.PUMPBTCOMM, "BaseResultActivity")
        aapsLogger.info(LTag.PUMPBTCOMM, s.get().collect(Collectors.joining()))
        val messages: Array<String> = answer.toArray().map { it.toString() }.toTypedArray()
        aapsLogger.debug("ready")
        val pattern = Pattern.compile("\\d+")
        val matcher = pattern.matcher(messages[0])
        if (matcher.find()) {
            medLinkPumpStatus.lastConnection = Objects.requireNonNull(matcher.group(0)).toLong()
        }
        val pumpStatus = medLinkPumpPlugin.pumpStatusData
        parseStatus(messages, medLinkPumpStatus, medLinkPumpPlugin.injector)
        aapsLogger.debug("Pumpstatus")
        aapsLogger.debug(pumpStatus.toString())
        medLinkPumpStatus.pumpDeviceState = PumpDeviceState.Active
        medLinkPumpPlugin.alreadyRun()
        medLinkPumpPlugin.setPumpTime(pumpStatus.lastDataTime)
        aapsLogger.info(LTag.PUMPBTCOMM, "statusmessage currentbasal " + pumpStatus.currentBasal)
        aapsLogger.info(LTag.PUMPBTCOMM, "statusmessage currentbasal " + pumpStatus.reservoirRemainingUnits)
        aapsLogger.info(LTag.PUMPBTCOMM, "status " + medLinkPumpStatus.getCurrentBasal())
        medLinkPumpStatus.setLastCommunicationToNow()
        aapsLogger.info(LTag.PUMPBTCOMM, "bgreading " + pumpStatus.bgReading)
        if (pumpStatus.bgReading != null) {
            medLinkPumpPlugin.handleNewSensorData(BgHistory(listOf(pumpStatus.sensorDataReading), emptyList(), null))
            medLinkPumpStatus.lastReadingStatus = MedLinkPumpStatus.BGReadingStatus.SUCCESS
        } else {
            medLinkPumpPlugin.handleNewPumpData()
            medLinkPumpStatus.lastReadingStatus = MedLinkPumpStatus.BGReadingStatus.FAILED
        }
        medLinkPumpPlugin.sendPumpUpdateEvent()
        if (f.getAnswer().anyMatch { m -> m.contains("eomeomeom") || m.contains("ready") }) {
        } else if (messages[messages.size - 1].lowercase(Locale.getDefault()) == "ready") {
            medLinkPumpStatus.pumpDeviceState = PumpDeviceState.Active
        } else {
            aapsLogger.debug("Apply last message" + messages[messages.size - 1])
            medLinkPumpStatus.pumpDeviceState = PumpDeviceState.PumpUnreachable
            //            String errorMessage = PumpDeviceState.PumpUnreachable.name();
            f.addError(MedLinkStandardReturn.ParsingError.Unreachable)
        }
        return f
    }
}