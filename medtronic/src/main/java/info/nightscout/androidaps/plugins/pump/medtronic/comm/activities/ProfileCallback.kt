package info.nightscout.androidaps.plugins.pump.medtronic.comm.activities

import android.content.Context
import info.nightscout.androidaps.utils.ToastUtils.showToastInUiThread
import dagger.android.HasAndroidInjector
import info.nightscout.androidaps.interfaces.Profile
import info.nightscout.shared.logging.AAPSLogger
import info.nightscout.androidaps.plugins.pump.medtronic.MedLinkMedtronicPumpPlugin
import info.nightscout.androidaps.plugins.pump.common.hw.medlink.activities.MedLinkStandardReturn
import info.nightscout.androidaps.plugins.pump.medtronic.R
import info.nightscout.androidaps.plugins.pump.medtronic.data.dto.MedLinkProfileParser
import info.nightscout.androidaps.plugins.pump.medtronic.driver.MedLinkMedtronicPumpStatus
import info.nightscout.androidaps.plugins.pump.medtronic.defs.BasalProfileStatus
import org.json.JSONException
import java.util.function.Function
import java.util.function.Supplier
import java.util.stream.Stream

/**
 * Created by Dirceu on 01/02/21.
 */
class ProfileCallback(private val injector: HasAndroidInjector, private val aapsLogger: AAPSLogger, private val ctx: Context, private val medLinkMedtronicPumpPlugin: MedLinkMedtronicPumpPlugin) :
    Function<Supplier<Stream<String>>, MedLinkStandardReturn<Profile?>> {

    override fun apply(answer: Supplier<Stream<String>>): MedLinkStandardReturn<Profile?> {
        val parser = MedLinkProfileParser(
            injector, aapsLogger,
            medLinkMedtronicPumpPlugin
        )
        var result: MedLinkStandardReturn<Profile?>
        try {
            val profile = parser.parseProfile(answer)
            result = MedLinkStandardReturn(answer, profile)
            (medLinkMedtronicPumpPlugin.pumpStatusData as MedLinkMedtronicPumpStatus).basalProfileStatus = BasalProfileStatus.ProfileOK
            if (!medLinkMedtronicPumpPlugin.isThisProfileSet(profile)) {
                showToastInUiThread(
                    ctx,
                    medLinkMedtronicPumpPlugin.rh.gs(R.string.medtronic_cmd_basal_profile_could_not_be_set)
                )
            }
        } catch (e: JSONException) {
            e.printStackTrace()
            result = MedLinkStandardReturn(answer, null, MedLinkStandardReturn.ParsingError.BasalParsingError)
        }
        //        errorMessage = new ArrayList<>();
//        aapsLogger.info(LTag.PUMP,"apply command");
//        aapsLogger.info(LTag.PUMP,resp.get().collect(Collectors.joining()));
//        BasalProfile profile = medLinkBasalProfileParser.parseProfile(resp);
        return result
    }
}