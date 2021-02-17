package info.nightscout.androidaps.plugins.pump.medtronic.comm.activities;

import java.util.function.Supplier;
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

    private MedLinkMedtronicPumpPlugin medLinkPumpPlugin;
    private MedLinkMedtronicPumpStatus medLinkPumpStatus;

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
        aapsLogger.info(LTag.PUMPBTCOMM, answer.toString());
        String[] messages = answer.toArray(String[]::new);
        if (f.getAnswer().anyMatch(m -> m.contains("eomeomeom"))) {
            aapsLogger.debug("eomomom");
            medLinkPumpPlugin.setLastCommunicationToNow();
            MedLinkPumpStatus pumpStatus = medLinkPumpPlugin.getPumpStatusData();
            MedLinkStatusParser.parseStatus(messages, pumpStatus, medLinkPumpPlugin.getInjector());
            aapsLogger.debug("Pumpstatus");
            aapsLogger.debug(pumpStatus.toString());

            medLinkPumpStatus.setPumpDeviceState(PumpDeviceState.Active);
            medLinkPumpStatus.batteryRemaining = pumpStatus.batteryRemaining;
            medLinkPumpStatus.reservoirRemainingUnits = pumpStatus.reservoirRemainingUnits;
            medLinkPumpStatus.lastBolusAmount = pumpStatus.lastBolusAmount;
            medLinkPumpStatus.lastBolusTime = pumpStatus.lastBolusTime;
            medLinkPumpStatus.activeProfileName = pumpStatus.activeProfileName;
            medLinkPumpStatus.currentBasal = pumpStatus.currentBasal;
            medLinkPumpStatus.dailyTotalUnits = pumpStatus.dailyTotalUnits;
            medLinkPumpStatus.lastBGTimestamp = pumpStatus.lastBGTimestamp;
            medLinkPumpPlugin.setPumpTime(pumpStatus.lastDateTime);
            medLinkPumpStatus.lastDateTime = pumpStatus.lastDateTime;
            medLinkPumpStatus.tempBasalRatio = pumpStatus.tempBasalRatio;
            medLinkPumpStatus.tempBasalInProgress = pumpStatus.tempBasalInProgress;
            medLinkPumpStatus.tempBasalRemainMin = pumpStatus.tempBasalRemainMin;
            medLinkPumpStatus.tempBasalStart = pumpStatus.tempBasalStart;
            aapsLogger.info(LTag.PUMPBTCOMM, "statusmessage currentbasal " + pumpStatus.currentBasal);
            aapsLogger.info(LTag.PUMPBTCOMM, "statusmessage currentbasal " + pumpStatus.reservoirRemainingUnits);
            aapsLogger.info(LTag.PUMPBTCOMM, "status " + medLinkPumpStatus.currentBasal);
            medLinkPumpStatus.setLastCommunicationToNow();
            medLinkPumpStatus.setPumpDeviceState(PumpDeviceState.Active);
            aapsLogger.info(LTag.PUMPBTCOMM, "bgreading " + pumpStatus.reading);
            if (pumpStatus.reading != null) {
                medLinkPumpPlugin.handleNewBgData(pumpStatus.reading);
                medLinkPumpStatus.lastReadingStatus = MedLinkPumpStatus.BGReadingStatus.SUCCESS;
            }else{
                medLinkPumpStatus.lastReadingStatus = MedLinkPumpStatus.BGReadingStatus.FAILED;
            }
            medLinkPumpPlugin.sendPumpUpdateEvent();
        } else if (messages[messages.length - 1].toLowerCase().equals("ready")) {
            medLinkPumpStatus.setPumpDeviceState(PumpDeviceState.Active);
        } else {
            aapsLogger.debug("Apply last message" + messages[messages.length - 1]);
            medLinkPumpStatus.setPumpDeviceState(PumpDeviceState.PumpUnreachable);
            String errorMessage = PumpDeviceState.PumpUnreachable.name();
            f.addError(MedLinkStandardReturn.ParsingError.Unreachable);
        }
        return f;
    }
}
