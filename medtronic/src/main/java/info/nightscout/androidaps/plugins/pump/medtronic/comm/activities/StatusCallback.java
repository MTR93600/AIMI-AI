package info.nightscout.androidaps.plugins.pump.medtronic.comm.activities;

import java.util.function.Supplier;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import info.nightscout.androidaps.logging.AAPSLogger;
import info.nightscout.androidaps.logging.LTag;
import info.nightscout.androidaps.plugins.pump.common.data.MedLinkPumpStatus;
import info.nightscout.androidaps.plugins.pump.common.defs.PumpDeviceState;
import info.nightscout.androidaps.plugins.pump.common.hw.medlink.activities.BaseStatusCallback;
import info.nightscout.androidaps.plugins.pump.common.hw.medlink.activities.MedLinkStandardReturn;
import info.nightscout.androidaps.plugins.pump.common.hw.medlink.service.MedLinkStatusParser;
import info.nightscout.androidaps.plugins.pump.medtronic.MedLinkMedtronicPumpPlugin;
import info.nightscout.androidaps.plugins.pump.medtronic.driver.MedLinkMedtronicPumpStatus;

/**
 * Created by Dirceu on 19/01/21.
 */
public class StatusCallback extends BaseStatusCallback {
    private final AAPSLogger aapsLogger;

    private final  MedLinkMedtronicPumpPlugin medLinkPumpPlugin;
    private final MedLinkMedtronicPumpStatus medLinkPumpStatus;

    public StatusCallback(AAPSLogger aapsLogger,
                          MedLinkMedtronicPumpPlugin medLinkPumpPlugin, MedLinkMedtronicPumpStatus medLinkPumpStatus) {
        super(medLinkPumpStatus);
        this.aapsLogger = aapsLogger;
        this.medLinkPumpPlugin = medLinkPumpPlugin;
        this.medLinkPumpStatus = medLinkPumpStatus;

    }

    @Override public MedLinkStandardReturn<MedLinkPumpStatus> apply(Supplier<Stream<String>> s) {
        MedLinkStandardReturn<MedLinkPumpStatus> f = new MedLinkStandardReturn<>(s, medLinkPumpStatus);
        Stream<String> answer = f.getAnswer();
        aapsLogger.info(LTag.PUMPBTCOMM, "BaseResultActivity");
        aapsLogger.info(LTag.PUMPBTCOMM, s.get().collect(Collectors.joining()));
        String[] messages = answer.toArray(String[]::new);
        if (f.getAnswer().anyMatch(m -> m.contains("eomeomeom") || m.contains("ready"))) {
            aapsLogger.debug("ready");
            medLinkPumpStatus.lastConnection = Long.parseLong(messages[0]);
            MedLinkPumpStatus pumpStatus = medLinkPumpPlugin.getPumpStatusData();
            MedLinkStatusParser.parseStatus(messages, medLinkPumpStatus, medLinkPumpPlugin.getInjector());
            aapsLogger.debug("Pumpstatus");
            aapsLogger.debug(pumpStatus.toString());

            medLinkPumpStatus.setPumpDeviceState(PumpDeviceState.Active);
            medLinkPumpPlugin.alreadyRun();
            medLinkPumpPlugin.setPumpTime(pumpStatus.lastDateTime);
            aapsLogger.info(LTag.PUMPBTCOMM, "statusmessage currentbasal " + pumpStatus.currentBasal);
            aapsLogger.info(LTag.PUMPBTCOMM, "statusmessage currentbasal " + pumpStatus.reservoirRemainingUnits);
            aapsLogger.info(LTag.PUMPBTCOMM, "status " + medLinkPumpStatus.currentBasal);
            medLinkPumpStatus.setLastCommunicationToNow();
            aapsLogger.info(LTag.PUMPBTCOMM, "bgreading " + pumpStatus.bgReading);
            if (pumpStatus.bgReading != null) {
                medLinkPumpPlugin.handleNewSensorData(pumpStatus.sensorDataReading);
                medLinkPumpStatus.lastReadingStatus = MedLinkPumpStatus.BGReadingStatus.SUCCESS;
            } else {
                medLinkPumpPlugin.handleNewPumpData();
                medLinkPumpStatus.lastReadingStatus = MedLinkPumpStatus.BGReadingStatus.FAILED;
            }
            medLinkPumpPlugin.sendPumpUpdateEvent();
        } else if (messages[messages.length - 1].toLowerCase().equals("ready")) {
            medLinkPumpStatus.setPumpDeviceState(PumpDeviceState.Active);
        } else {
            aapsLogger.debug("Apply last message" + messages[messages.length - 1]);
            medLinkPumpStatus.setPumpDeviceState(PumpDeviceState.PumpUnreachable);
//            String errorMessage = PumpDeviceState.PumpUnreachable.name();
            f.addError(MedLinkStandardReturn.ParsingError.Unreachable);
        }
        return f;
    }
}
