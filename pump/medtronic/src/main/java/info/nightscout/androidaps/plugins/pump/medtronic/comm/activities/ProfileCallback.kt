package info.nightscout.androidaps.plugins.pump.medtronic.comm.activities

import android.content.Context
import info.nightscout.androidaps.plugins.pump.common.hw.medlink.activities.MedLinkStandardReturn
import info.nightscout.androidaps.plugins.pump.medtronic.MedLinkMedtronicPumpPlugin
import info.nightscout.androidaps.plugins.pump.medtronic.R
import info.nightscout.androidaps.plugins.pump.medtronic.data.dto.BasalProfile
import info.nightscout.androidaps.plugins.pump.medtronic.defs.BasalProfileStatus
import info.nightscout.androidaps.plugins.pump.medtronic.defs.MedLinkMedtronicDeviceType
import info.nightscout.androidaps.plugins.pump.medtronic.driver.MedLinkMedtronicPumpStatus
import info.nightscout.core.ui.toast.ToastUtils.showToastInUiThread
import info.nightscout.interfaces.plugin.MedLinkProfileParser
import info.nightscout.interfaces.profile.Profile
import info.nightscout.interfaces.sync.DataSyncSelector
import org.json.JSONException
import java.util.function.Function
import java.util.function.Supplier
import java.util.stream.Stream

/**
 * Created by Dirceu on 01/02/21.
 */
class ProfileCallback(private val ctx: Context, private val medLinkMedtronicPumpPlugin: MedLinkMedtronicPumpPlugin,
                      private val parser: MedLinkProfileParser<MedLinkStandardReturn<MedLinkMedtronicDeviceType>, BasalProfile>) :
    Function<Supplier<Stream<String>>, MedLinkStandardReturn<Profile?>> {

    override fun apply(answer: Supplier<Stream<String>>): MedLinkStandardReturn<Profile?> {
        val x:DataSyncSelector.PairProfileSwitch

        var result: MedLinkStandardReturn<Profile?>
        try {
            val profile = parser.parseProfile(answer, medLinkMedtronicPumpPlugin.basalProfile)
            result = MedLinkStandardReturn(answer, profile)
            (medLinkMedtronicPumpPlugin.pumpStatusData as MedLinkMedtronicPumpStatus).basalProfileStatus = BasalProfileStatus.ProfileOK
            if (profile != null &&  !medLinkMedtronicPumpPlugin.isThisProfileSet(profile)) {
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