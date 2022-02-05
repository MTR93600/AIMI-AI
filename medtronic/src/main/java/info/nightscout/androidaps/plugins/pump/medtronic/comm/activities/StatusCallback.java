package info.nightscout.androidaps.plugins.pump.medtronic.comm.activities;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Objects;
import java.util.function.Supplier;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import info.nightscout.androidaps.interfaces.BgSync;
import info.nightscout.shared.logging.AAPSLogger;
import info.nightscout.shared.logging.LTag;
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
        aapsLogger.debug("ready");
        Pattern pattern = Pattern.compile("\\d+");
        Matcher matcher = pattern.matcher(messages[0]);
        if(matcher.find()){
            medLinkPumpStatus.setLastConnection (Long.parseLong(Objects.requireNonNull(matcher.group(0))));
        }
        MedLinkPumpStatus pumpStatus = medLinkPumpPlugin.getPumpStatusData();
        MedLinkStatusParser.parseStatus(messages, medLinkPumpStatus, medLinkPumpPlugin.getInjector());
        aapsLogger.debug("Pumpstatus");
        aapsLogger.debug(pumpStatus.toString());

        medLinkPumpStatus.setPumpDeviceState(PumpDeviceState.Active);
        medLinkPumpPlugin.alreadyRun();
        medLinkPumpPlugin.setPumpTime(pumpStatus.getLastDataTime());
        aapsLogger.info(LTag.PUMPBTCOMM, "statusmessage currentbasal " + pumpStatus.currentBasal);
        aapsLogger.info(LTag.PUMPBTCOMM, "statusmessage currentbasal " + pumpStatus.getReservoirLevel());
        aapsLogger.info(LTag.PUMPBTCOMM, "status " + medLinkPumpStatus.getCurrentBasal());
        medLinkPumpStatus.setLastCommunicationToNow();
        aapsLogger.info(LTag.PUMPBTCOMM, "bgreading " + pumpStatus.bgReading);
        if (pumpStatus.bgReading != null) {
            medLinkPumpPlugin.handleNewSensorData(new BgSync.BgHistory(Collections.singletonList(pumpStatus.sensorDataReading),
                    Collections.emptyList(),null));
            medLinkPumpStatus.lastReadingStatus = MedLinkPumpStatus.BGReadingStatus.SUCCESS;
        } else {
            medLinkPumpPlugin.handleNewPumpData();
            medLinkPumpStatus.lastReadingStatus = MedLinkPumpStatus.BGReadingStatus.FAILED;
        }
        medLinkPumpPlugin.sendPumpUpdateEvent();
        if (f.getAnswer().anyMatch(m -> m.contains("eomeomeom") || m.contains("ready"))) {

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
