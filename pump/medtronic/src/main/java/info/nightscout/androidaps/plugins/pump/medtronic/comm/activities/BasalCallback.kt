package info.nightscout.androidaps.plugins.pump.medtronic.comm.activities

import info.nightscout.androidaps.plugins.pump.common.hw.medlink.activities.BaseCallback
import info.nightscout.androidaps.plugins.pump.common.hw.medlink.activities.MedLinkStandardReturn
import info.nightscout.androidaps.plugins.pump.common.hw.medlink.activities.MedLinkStandardReturn.ParsingError
import info.nightscout.androidaps.plugins.pump.medtronic.MedLinkMedtronicPumpPlugin
import info.nightscout.androidaps.plugins.pump.medtronic.data.dto.BasalProfile
import info.nightscout.androidaps.plugins.pump.medtronic.data.dto.MedLinkBasalProfileParser
import info.nightscout.rx.logging.AAPSLogger
import info.nightscout.rx.logging.LTag
import java.util.function.Supplier
import java.util.stream.Collectors
import java.util.stream.Stream

/**
 * Created by Dirceu on 22/12/20.
 */
class BasalCallback(private val aapsLogger: AAPSLogger, val medLinkMedtronicPumpPlugin: MedLinkMedtronicPumpPlugin) : BaseCallback<BasalProfile, Supplier<Stream<String>>>() {

    private val medLinkBasalProfileParser: MedLinkBasalProfileParser = MedLinkBasalProfileParser(aapsLogger)
    private var errorMessage: MutableList<ParsingError> = mutableListOf()
    override fun apply(resp: Supplier<Stream<String>>): MedLinkStandardReturn<BasalProfile> {
        errorMessage = ArrayList()
        aapsLogger.info(LTag.PUMP, "apply command")
        aapsLogger.info(LTag.PUMP, resp.get()!!.collect(Collectors.joining()))
        val profile = medLinkBasalProfileParser.parseProfile(resp)

        //TODO check this;
        return if (profile != null) {
            val result = medLinkMedtronicPumpPlugin.comparePumpBasalProfile(profile)
            if (!result.success) {
                (errorMessage as ArrayList<ParsingError>).add(ParsingError.BasalComparisonError)
                aapsLogger.warn(LTag.PUMPCOMM, "Error reading profile in max retries.")
                aapsLogger.warn(LTag.PUMPCOMM, "" + profile)
            }
            val errors = checkProfile(profile)
            medLinkMedtronicPumpPlugin.sendPumpUpdateEvent()
            profile.generateRawDataFromEntries()
            medLinkMedtronicPumpPlugin.pumpStatusData.basalsByHour = profile.getProfilesByHour(medLinkMedtronicPumpPlugin.pumpStatusData.pumpType)
            medLinkMedtronicPumpPlugin.basalProfile = profile
            MedLinkStandardReturn<BasalProfile>(resp, profile, errorMessage)
        } else {
            MedLinkStandardReturn(
                resp, BasalProfile(
                    aapsLogger
                ), mutableListOf(ParsingError.BasalParsingError)
            )
        }
    }

    private fun checkProfile(profile: BasalProfile): String {
        return ""
    }

    fun getErrorMessage(): List<ParsingError?>? {
        return errorMessage
    }

}