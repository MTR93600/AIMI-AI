package info.nightscout.androidaps.plugins.pump.medtronic.comm.activities;

import android.content.Context;

import org.json.JSONException;

import java.util.function.Function;
import java.util.function.Supplier;
import java.util.stream.Stream;

import dagger.android.HasAndroidInjector;
import info.nightscout.androidaps.data.Profile;
import info.nightscout.androidaps.logging.AAPSLogger;
import info.nightscout.androidaps.plugins.pump.common.data.MedLinkPumpStatus;
import info.nightscout.androidaps.plugins.pump.common.hw.medlink.activities.MedLinkStandardReturn;
import info.nightscout.androidaps.plugins.pump.medtronic.MedLinkMedtronicPumpPlugin;
import info.nightscout.androidaps.plugins.pump.medtronic.R;
import info.nightscout.androidaps.plugins.pump.medtronic.data.dto.MedLinkProfileParser;
import info.nightscout.androidaps.plugins.pump.medtronic.defs.BasalProfileStatus;
import info.nightscout.androidaps.plugins.pump.medtronic.driver.MedLinkMedtronicPumpStatus;
import info.nightscout.androidaps.utils.ToastUtils;

/**
 * Created by Dirceu on 01/02/21.
 */
public class ProfileCallback implements Function<Supplier<Stream<String>>,
        MedLinkStandardReturn<Profile>> {


    private final MedLinkMedtronicPumpPlugin medLinkMedtronicPumpPlugin;
    private final Context ctx;
    private HasAndroidInjector injector;
    private AAPSLogger aapsLogger;

    public ProfileCallback(HasAndroidInjector injector, AAPSLogger aapsLogger, Context ctx, MedLinkMedtronicPumpPlugin medLinkMedtronicPumpPlugin) {
        this.injector = injector;
        this.aapsLogger = aapsLogger;
        this.medLinkMedtronicPumpPlugin = medLinkMedtronicPumpPlugin;
        this.ctx = ctx;
    }

    @Override public MedLinkStandardReturn<Profile> apply(Supplier<Stream<String>> answer) {
        MedLinkProfileParser parser = new MedLinkProfileParser(injector, aapsLogger,
                medLinkMedtronicPumpPlugin);
        MedLinkStandardReturn<Profile> result;

        try {
            Profile profile = parser.parseProfile(answer);
            result = new MedLinkStandardReturn<>(answer, profile);
            ((MedLinkMedtronicPumpStatus) medLinkMedtronicPumpPlugin.getPumpStatusData()).basalProfileStatus = BasalProfileStatus.ProfileOK;
            if (!medLinkMedtronicPumpPlugin.isThisProfileSet(profile)) {
                ToastUtils.showToastInUiThread(ctx, medLinkMedtronicPumpPlugin.getResourceHelper().gs(R.string.medtronic_cmd_basal_profile_could_not_be_set));
            }

        } catch (JSONException e) {
            e.printStackTrace();
            result = new MedLinkStandardReturn<>(answer, null, MedLinkStandardReturn.ParsingError.BasalParsingError);
        }
//        errorMessage = new ArrayList<>();
//        aapsLogger.info(LTag.PUMP,"apply command");
//        aapsLogger.info(LTag.PUMP,resp.get().collect(Collectors.joining()));
//        BasalProfile profile = medLinkBasalProfileParser.parseProfile(resp);
        return result;
    }
}
