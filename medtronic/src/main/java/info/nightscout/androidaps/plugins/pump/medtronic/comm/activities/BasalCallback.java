package info.nightscout.androidaps.plugins.pump.medtronic.comm.activities;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Supplier;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import info.nightscout.androidaps.data.PumpEnactResult;
import info.nightscout.androidaps.logging.AAPSLogger;
import info.nightscout.androidaps.logging.LTag;
import info.nightscout.androidaps.plugins.pump.common.hw.medlink.activities.BaseCallback;
import info.nightscout.androidaps.plugins.pump.common.hw.medlink.activities.MedLinkStandardReturn;
import info.nightscout.androidaps.plugins.pump.common.hw.medlink.activities.MedLinkStandardReturn.ParsingError;
import info.nightscout.androidaps.plugins.pump.medtronic.MedLinkMedtronicPumpPlugin;
import info.nightscout.androidaps.plugins.pump.medtronic.data.dto.BasalProfile;
import info.nightscout.androidaps.plugins.pump.medtronic.data.dto.MedLinkBasalProfileParser;

/**
 * Created by Dirceu on 22/12/20.
 */
public class BasalCallback extends BaseCallback<BasalProfile> {
    private final MedLinkMedtronicPumpPlugin medLinkMedtronicPumpPlugin;
    private final MedLinkBasalProfileParser medLinkBasalProfileParser;
    private final AAPSLogger aapsLogger;

    private List<ParsingError> errorMessage;

    public BasalCallback(AAPSLogger aapsLogger, MedLinkMedtronicPumpPlugin medLinkMedtronicPumpPlugin) {
        super();
        this.aapsLogger = aapsLogger;
        medLinkBasalProfileParser = new MedLinkBasalProfileParser(aapsLogger);
        this.medLinkMedtronicPumpPlugin = medLinkMedtronicPumpPlugin;
    }

    @Override public MedLinkStandardReturn<BasalProfile> apply(Supplier<Stream<String>> resp) {
            errorMessage = new ArrayList<>();
            aapsLogger.info(LTag.PUMP,"apply command");
            aapsLogger.info(LTag.PUMP,resp.get().collect(Collectors.joining()));
            BasalProfile profile = medLinkBasalProfileParser.parseProfile(resp);

            //TODO check this;
            if(profile!=null) {
                PumpEnactResult result = medLinkMedtronicPumpPlugin.comparePumpBasalProfile(profile);
                if (!result.success) {
                    errorMessage.add(ParsingError.BasalComparisonError);
                    aapsLogger.warn(LTag.PUMPCOMM, "Error reading profile in max retries.");
                    aapsLogger.warn(LTag.PUMPCOMM, ""+profile);
                }
                String errors = checkProfile(profile);
                medLinkMedtronicPumpPlugin.sendPumpUpdateEvent();
                medLinkMedtronicPumpPlugin.getPumpStatusData().basalsByHour  = profile.getProfilesByHour(medLinkMedtronicPumpPlugin.getPumpStatusData().pumpType);
                medLinkMedtronicPumpPlugin.setBasalProfile(profile);
                return new MedLinkStandardReturn<>(resp, profile, errorMessage);
            } else {
                return new MedLinkStandardReturn<>(resp,new BasalProfile(aapsLogger), ParsingError.BasalParsingError);
            }
    }

    private String checkProfile(BasalProfile profile) {
        return "";
    }

    public List<ParsingError> getErrorMessage() {
        return errorMessage;
    }

}
