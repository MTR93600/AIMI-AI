package info.nightscout.androidaps.plugins.pump.medtronic.comm.activities;

import info.nightscout.androidaps.data.PumpEnactResult;
import info.nightscout.androidaps.logging.AAPSLogger;
import info.nightscout.androidaps.logging.LTag;
import info.nightscout.androidaps.plugins.pump.common.hw.medlink.activities.BaseResultActivity;
import info.nightscout.androidaps.plugins.pump.medtronic.MedLinkMedtronicPumpPlugin;
import info.nightscout.androidaps.plugins.pump.medtronic.data.dto.BasalProfile;
import info.nightscout.androidaps.plugins.pump.medtronic.data.dto.MedLinkBasalProfileParser;

/**
 * Created by Dirceu on 22/12/20.
 */
public class BasalResultActivity  extends BaseResultActivity<BasalProfile> {
    private final MedLinkMedtronicPumpPlugin medLinkMedtronicPumpPlugin;
    private final MedLinkBasalProfileParser medLinkBasalProfileParser;

    private String errorMessage;

    public BasalResultActivity(AAPSLogger aapsLogger, MedLinkMedtronicPumpPlugin medLinkMedtronicPumpPlugin) {
        super(aapsLogger);
        medLinkBasalProfileParser = new MedLinkBasalProfileParser(aapsLogger);
        this.medLinkMedtronicPumpPlugin = medLinkMedtronicPumpPlugin;

    }

    @Override public BasalProfile apply(String resp) {

            BasalProfile profile = medLinkBasalProfileParser.parseProfile(resp);

            //TODO check this;
            PumpEnactResult result = medLinkMedtronicPumpPlugin.comparePumpBasalProfile(profile);
            if (!result.success) {
                this.errorMessage = resp;
                aapsLogger.warn(LTag.PUMPCOMM, "Error reading profile in max retries.");
            }
            return profile;

    }


    public String getErrorMessage() {
        return errorMessage;
    }

}
