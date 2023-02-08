package info.nightscout.androidaps.plugins

import dagger.android.HasAndroidInjector
import info.nightscout.androidaps.plugins.pump.medtronic.data.dto.BasalProfile
import info.nightscout.androidaps.plugins.pump.medtronic.data.dto.BasalProfileEntry
import info.nightscout.core.extensions.pureProfileFromJson
import info.nightscout.core.profile.ProfileSealed
import info.nightscout.interfaces.Constants
import info.nightscout.interfaces.db.WizardSettings
import info.nightscout.interfaces.plugin.MedLinkProfileParser
import info.nightscout.interfaces.profile.Profile
import info.nightscout.rx.logging.AAPSLogger
import info.nightscout.rx.logging.LTag
import info.nightscout.shared.utils.DateUtil
import org.json.JSONException
import org.json.JSONObject
import java.util.*
import java.util.function.Supplier
import java.util.regex.Matcher
import java.util.regex.Pattern
import java.util.stream.Stream
import javax.inject.Inject

/**
 * Created by Dirceu on 01/02/21.
 *  used by medlink
 */

class MedLinkProfileParserImpl<T> @Inject constructor(
    private val hasAndroidInjector: HasAndroidInjector, private val aapsLogger: AAPSLogger,
    private val dateUtil: DateUtil
): MedLinkProfileParser<T,BasalProfile> {

    private val hourPattern = Pattern.compile("\\d{2}:\\d{2}")
    private var units: String? = null
    @Throws(JSONException::class) override fun parseProfile(answer: Supplier<Stream<String>>, basalProfile: BasalProfile?): Profile? {
        val profileData = answer.get().map { obj: String -> obj.lowercase(Locale.getDefault()) }.iterator()
        while (profileData.hasNext() && !profileData.next().contains("bolus wizard settings")) {
        }
        var buffer = StringBuffer()
        buffer.append("{")
        buffer = addData(buffer, "dia", true)
        buffer = addData(buffer, Constants.defaultDIA, false)
        parseBolusWizardSettings(profileData)
        buffer = parseCarbRatio(profileData, buffer)
        buffer = parseInsulinSensitivity(profileData, buffer)
        buffer.append(",")
        addData(buffer, "timezone", true)
        addData(buffer, TimeZone.getDefault().id, false)
        buffer = parseBGTargets(profileData, buffer)
        buffer.append(",")
        addData(buffer, "units", true)
        addData(buffer, units, false)
        if (basalProfile != null) {
            buffer = parseBasal(basalProfile!!.getEntries(), buffer)
        }
        buffer.append("}")
        val json = JSONObject(buffer.toString())
        aapsLogger.info(LTag.PUMPBTCOMM, json.toString())
        //        Bolus wizard settings:
//23:37:48.398 Max. bolus: 15.0u
//23:37:48.398 Easy bolus step: 0.1u
//23:37:48.399 Carb ratios:
//23:37:48.623 Rate 1: 12gr/u from 00:00
//23:37:48.657 Rate 2: 09gr/u from 16:00
//23:37:48.694 Rate 3: 10gr/u from 18:00
//23:37:48.696 Insulin sensitivities:
//23:37:48.847 Rate 1:  40mg/dl from 00:00
//23:37:48.995 Rate 2:  30mg/dl from 09:00
//23:37:48.995 Rate 3:  40mg/dl from 18:00
//23:37:48.996 BG targets:
//23:37:49.189 Rate 1: 70‑140 from 00:00
//23:37:49.257 Rate 2: 70‑120 from 06:00
//23:37:49.258 Rate 3: 70‑140 from 10:00
//23:37:49.258 Rate 4: 70‑120 from 15:00
//23:37:49.294 Rate 5: 70‑140 from 21:00
//23:37:49.295 Ready
        return pureProfileFromJson(json, dateUtil,
                                                          units!!.uppercase(Locale.getDefault()))?.let {
            ProfileSealed.Pure(
                it)
        }
    }

    private fun addData(buffer: StringBuffer, dia: Any?, isParameter: Boolean): StringBuffer {
        val result = buffer.append("\"").append(dia).append("\"")
        if (isParameter) {
            result.append(":")
        }
        return result
    }

    private fun parseBGTargets(profileData: Iterator<String>, buffer: StringBuffer): StringBuffer {
        var result = buffer
        if (profileData.hasNext()) {
            val targetHigh = StringBuffer()
            addData(targetHigh, "target_high", true)
            targetHigh.append("[")
            val targetLow = StringBuffer()
            addData(targetLow, "target_low", true)
            targetLow.append("[")
            var ratioText = profileData.next()
            var toAppend = ""
            while (!ratioText.contains("ready")) {
                targetHigh.append(toAppend)
                targetLow.append(toAppend)
                val grIndex = ratioText.indexOf("-")
                val separatorIndex = ratioText.indexOf(":") + 1
                val fromIndex = ratioText.indexOf("from")
                aapsLogger.info(LTag.PUMPBTCOMM, "$ratioText $separatorIndex $grIndex")
                val lowerRate = Integer.valueOf(ratioText.substring(separatorIndex, grIndex).trim { it <= ' ' })
                val higherRate = Integer.valueOf(ratioText.substring(grIndex + 1, fromIndex).trim { it <= ' ' })
                val matcher = hourPattern.matcher(ratioText)
                if (matcher.find()) {
                    val hour = matcher.group(0)
                    addTimeValue(targetHigh, hour, higherRate)
                    addTimeValue(targetLow, hour, lowerRate)
                    toAppend = ","
                } else {
                    aapsLogger.info(LTag.PUMPBTCOMM, "bg target matcher doesn't found hour")
                }
                ratioText = profileData.next()
            }
            targetHigh.append("]")
            targetLow.append("]")
            result = buffer.append(",").append(targetHigh).append(",").append(targetLow)
        }
        return result
    }

    private fun parseInsulinSensitivity(profileData: Iterator<String>, buffer: StringBuffer): StringBuffer {
        if (profileData.hasNext()) {
            var ratioText = profileData.next()
            buffer.append(",")
            addData(buffer, "sens", true)
            buffer.append("[")
            var toAppend = ""
            while (!ratioText.contains("bg targets")) {
                buffer.append(toAppend)
                val separatorIndex = ratioText.indexOf(":") + 1
                units = ratioText.substring(separatorIndex + 4, ratioText.indexOf("from")).trim { it <= ' ' }
                val insulinSensitivity = Integer.valueOf(ratioText.substring(separatorIndex, separatorIndex + 4).trim { it <= ' ' })
                val matcher = hourPattern.matcher(ratioText)
                if (matcher.find()) {
                    val hour = matcher.group(0)
                    addTimeValue(buffer, hour, insulinSensitivity)
                    toAppend = ","
                    //                result.add(new InsulinSensitivity(insulinSensitivity, hour));
                } else {
                    aapsLogger.info(LTag.PUMPBTCOMM, "insulinsensitivity matcher doesn't found hour")
                }
                ratioText = profileData.next()
            }
            buffer.append("]")
        }
        return buffer
    }

    private fun parseBasal(basalProfile: List<BasalProfileEntry>, buffer: StringBuffer): StringBuffer {
        var buffer = buffer
        buffer.append(",")
        buffer = addData(buffer, "basal", true)
        var toAppend = "["
        for (basalEntry in basalProfile) {
            buffer.append(toAppend)
            addTimeValue(buffer, basalEntry.startTime!!.toString("HH:mm"),
                         basalEntry.rate)
            toAppend = ","
        }
        buffer.append("]")
        return buffer
    }

    private fun parseCarbRatio(profileText: Iterator<String>, buffer: StringBuffer): StringBuffer {
        var buffer = buffer
        if (profileText.hasNext()) {
            buffer.append(",")
            buffer = addData(buffer, "carbratio", true)
            val carbRatio = profileText.next()
            if (carbRatio.contains("carb ratios")) {
                var ratioText = profileText.next()
                var toAppend = "["
                //                buffer.append(toAppend);
                while (!ratioText.contains("insulin sensitivities")) {
                    if (ratioText.contains("u\\ex")) {
                        val pattern = Pattern.compile("\\d+\\.\\d+")
                        val carbMatcher = pattern.matcher(ratioText)
                        if (carbMatcher.find()) {
                            carbMatcher(carbMatcher, buffer, ratioText, toAppend)
                        }
                    } else {
                        val pattern = Pattern.compile("\\d{2}")
                        val carbMatcher = pattern.matcher(ratioText)
                        if (carbMatcher.find()) {
                            carbMatcher(carbMatcher, buffer, ratioText, toAppend)
                        }
                    }
                    toAppend = ","
                    ratioText = getNextValidLine(profileText)
                }
                buffer.append("]")
            } else {
                aapsLogger.info(LTag.PUMPBTCOMM, "profile doesn't have carb ratios")
            }
        }
        return buffer
    }

    private fun carbMatcher(carbMatcher: Matcher, buffer: StringBuffer, ratioText: String, toAppend: String) {
        var toAppend: String? = toAppend
        val carbs = carbMatcher.group(0)
        aapsLogger.info(LTag.PUMPBTCOMM, "$ratioText $carbs")
        var ratio = 0
        ratio = if (carbs.contains("\\.")) {
            val rat = java.lang.Double.valueOf(carbs)
            Math.toIntExact(Math.round(rat * 10))
        } else {
            Integer.valueOf(carbs)
        }
        val matcher = hourPattern.matcher(ratioText)
        if (matcher.find()) {
            val hour = matcher.group(0)
            buffer.append(toAppend)
            addTimeValue(buffer, hour, ratio)
            toAppend = ","
        } else {
            aapsLogger.info(LTag.PUMPBTCOMM, "carbratio matcher doesn't found hour")
        }
    }

    private fun getNextValidLine(profileText: Iterator<String>): String {
        val ratioText = profileText.next()
        return if (ratioText.contains("error command")) {
            getNextValidLine(profileText)
        } else {
            ratioText
        }
    }

    private fun addTimeValue(buffer: StringBuffer, hour: String, ratio: Any) {
        buffer.append("{")
        addData(buffer, "time", true)
        addData(buffer, hour, false)
        buffer.append(",")
        addData(buffer, "value", true)
        buffer.append(ratio)
        buffer.append("}")
    }

    private fun parseBolusWizardSettings(profileText: Iterator<String>) {
        if (profileText.hasNext()) {
            val result = WizardSettings()
            val maxBolusText = profileText.next()
            val pattern = Pattern.compile("\\d+\\.\\d")
            if (maxBolusText.contains("max. bolus")) {
                val matcher = pattern.matcher(maxBolusText)
                if (matcher.find()) {
                    result.maxBolus = matcher.group(0).toDouble()
                }
            }
            val easyBolusStepText = profileText.next()
            if (easyBolusStepText.contains("easy bolus")) {
                val matcher = pattern.matcher(easyBolusStepText)
                if (matcher.find()) {
                    result.easyBolusStep = matcher.group(0).toDouble()
                }
            }
            if (result.maxBolus === 0.0 && result.easyBolusStep === 0.0) {
                aapsLogger.info(LTag.PUMPBTCOMM, "Failed to parse max and bolus step " +
                    maxBolusText + " " + easyBolusStepText)
            }
        }
    }
}