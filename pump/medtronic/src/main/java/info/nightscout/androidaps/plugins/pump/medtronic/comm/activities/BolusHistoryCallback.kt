package info.nightscout.androidaps.plugins.pump.medtronic.comm.activities

import info.nightscout.androidaps.plugins.pump.common.hw.medlink.activities.BaseCallback
import info.nightscout.androidaps.plugins.pump.common.hw.medlink.activities.MedLinkStandardReturn
import info.nightscout.androidaps.plugins.pump.medtronic.MedLinkMedtronicPumpPlugin
import info.nightscout.androidaps.plugins.pump.medtronic.R
import info.nightscout.interfaces.pump.DetailedBolusInfo
import info.nightscout.interfaces.utils.JsonHelper.safeGetString
import info.nightscout.rx.logging.AAPSLogger
import info.nightscout.rx.logging.LTag

import org.json.JSONException
import org.json.JSONObject
import java.text.ParseException
import java.text.SimpleDateFormat
import java.util.*
import java.util.function.Supplier
import java.util.regex.Matcher
import java.util.regex.Pattern
import java.util.stream.Collectors
import java.util.stream.Stream

/**
 * Created by Dirceu on 15/02/21.
 */
class BolusHistoryCallback(private val aapsLogger: AAPSLogger, private val medLinkPumpPlugin: MedLinkMedtronicPumpPlugin) : BaseCallback<Stream<JSONObject>, Supplier<Stream<String>>>() {

    override fun apply(ans: Supplier<Stream<String>>): MedLinkStandardReturn<Stream<JSONObject>> {
        val answers = ans.get().iterator()
        aapsLogger.info(LTag.PUMPBTCOMM, "Bolus history")
        aapsLogger.info(LTag.PUMPBTCOMM, ans.get().collect(Collectors.joining()))
        while (answers.hasNext() && !answers.next().contains("bolus history:")) {
        }
        //Empty Line
        if (answers.hasNext()) {
            answers.next()
            return try {
                val commandHistory = processBolusHistory(answers)
                if (medLinkPumpPlugin.sp.getBoolean(R.bool.key_medlink_handle_cannula_change_event, false)) {
                    medLinkPumpPlugin.handleNewCareportalEvent(Supplier { commandHistory.get().filter { f: JSONObject -> !isBolus(f) } })
                }
                val resultStream = Supplier { commandHistory.get().filter { f: JSONObject -> isBolus(f) } }

                medLinkPumpPlugin.handleNewTreatmentData(resultStream.get(), true)
                MedLinkStandardReturn(ans, resultStream.get())
            } catch (e: ParseException) {
                e.printStackTrace()
                MedLinkStandardReturn(
                    ans, Stream.empty(),
                    MedLinkStandardReturn.ParsingError.BolusParsingError
                )
            }
        } else {
            return MedLinkStandardReturn(
                ans, Stream.empty(),
                MedLinkStandardReturn.ParsingError.BolusParsingError
            )
        }
    }

    private fun isBolus(json: JSONObject): Boolean {
        return safeGetString(json, "eventType", "") ==
            DetailedBolusInfo.EventType.BOLUS_WIZARD.name || safeGetString(json, "eventType", "") ==
            DetailedBolusInfo.EventType.MEAL_BOLUS.name || safeGetString(json, "eventType", "") ==
            DetailedBolusInfo.EventType.CORRECTION_BOLUS.name
    }

    @Throws(ParseException::class)
    private fun processBolusHistory(answers: Iterator<String>): Supplier<Stream<JSONObject>> {
        val resultList: MutableList<JSONObject> = ArrayList()
        var cannulaChange: Optional<JSONObject> = Optional.empty()
        var verifyNext = false
        while (answers.hasNext()) {
            var data = processData(answers)
            if (verifyNext && data.isPresent
                && cannulaChange.isPresent
            ) {
                if (data.get().get("eventType") != DetailedBolusInfo.EventType.INSULIN_CHANGE) {
                    resultList.add(cannulaChange.get())
                    cannulaChange = Optional.empty()
                }
                verifyNext = false
            }
            if (!verifyNext && data.isPresent
                && data.get().get("eventType") == DetailedBolusInfo.EventType.CANNULA_CHANGE
                && !medLinkPumpPlugin.sp.getBoolean(R.bool.key_medlink_change_cannula, true)
            ) {
                cannulaChange = data
                data = Optional.empty()
                verifyNext = true
            }
            if (data.isPresent)
                resultList.add(data.get())
        }
        resultList.reverse()
        return Supplier { resultList.stream() }
    }

    @Throws(ParseException::class)
    private fun processData(answers: Iterator<String>): Optional<JSONObject> {
        val answer = answers.next()
        return try {
            if (answer.contains("bolus:")) {
                processBolus(answers)
            } else if (answer.contains("battery insert")) {
                processBattery(answers.next())
            } else if (answer.contains("reservoir change")) {
                aapsLogger.info(LTag.PUMPBTCOMM, "reservoir change")
                processSite(answers.next())
            } else if (answer.contains("reservoir rewind")) {
                aapsLogger.info(LTag.PUMPBTCOMM, "reservoir rewind")
                processReservoir(answers.next())
            } else {
                Optional.empty()
            }
        } catch (e: JSONException) {
            e.printStackTrace()
            Optional.empty()
        }
    }

    @Throws(ParseException::class, JSONException::class)
    private fun processSite(answer: String): Optional<JSONObject> {
        val matcher = parseMatcher(answer)
        if (matcher.find()) {
            val json = JSONObject()
            val date = parsetTime(answer, matcher)
            json.put("mills", date)
            json.put(
                "eventType",
                DetailedBolusInfo.EventType.CANNULA_CHANGE
            )
            json.put("enteredBy", "PUMP")
            aapsLogger.debug("USER ENTRY: CAREPORTAL \${careportalEvent.eventType} json: \${careportalEvent.json}")
            return Optional.of(json)
        }
        return Optional.empty()
    }

    @Throws(ParseException::class, JSONException::class)
    private fun processReservoir(answer: String): Optional<JSONObject> {
        val matcher = parseMatcher(answer)
        if (matcher.find()) {
            val json = JSONObject()
            val date = parsetTime(answer, matcher)
            json.put("mills", date)
            json.put("eventType", DetailedBolusInfo.EventType.INSULIN_CHANGE)
            aapsLogger.debug("USER ENTRY: CAREPORTAL \${careportalEvent.eventType} json: \${careportalEvent.json}")
            return Optional.of(json)
        }
        return Optional.empty()
    }

    @Throws(ParseException::class, JSONException::class)
    private fun processBattery(answer: String): Optional<JSONObject> {
        val matcher = parseMatcher(answer)
        if (matcher.find()) {
            val json = JSONObject()
            val date = parsetTime(answer, matcher)
            json.put("mills", date)
            json.put("eventType", DetailedBolusInfo.EventType.PUMP_BATTERY_CHANGE)
            aapsLogger.debug("USER ENTRY: CAREPORTAL \${careportalEvent.eventType} json: \${careportalEvent.json}")
            return Optional.of(json)
        }
        return Optional.empty()
    }

    @Throws(ParseException::class, JSONException::class)
    private fun processBolus(answers: Iterator<String>): Optional<JSONObject> {
        if (answers.hasNext()) {
            val answer = answers.next()
            val matcher = parseMatcher(answer)
            if (matcher.find()) {
                val bolusInfo = DetailedBolusInfo()
                bolusInfo.bolusTimestamp = parsetTime(answer, matcher)
                bolusInfo.bolusTimestamp?.let { bolusInfo.timestamp = it }
                bolusInfo.deliverAtTheLatest = bolusInfo.bolusTimestamp!!
                bolusInfo.insulin = processBolusData(answers, "given bl:")

//                bolusInfo.pumpId = Long.parseLong(
//                        medLinkPumpPlugin.getMedLinkService().getMedLinkServiceData().pumpID);
                val setBolus = processBolusData(answers, "set bl:")
                if (answers.hasNext()) {
                    var isFromBolusWizard = answers.next()
                    if (isFromBolusWizard.contains("enter from bolus wizard")) {
                        val bg = answers.next()
                        if (bg.contains("glucose")) {
                            val bgPattern = Pattern.compile("\\d{1,3}")
                            val bgMatcher = bgPattern.matcher(bg)
                            if (bgMatcher.find()) {
                                bolusInfo.mgdlGlucose = bgMatcher.group(0).toDouble()
                            }
                        }
                        while (answers.hasNext() && !isFromBolusWizard.contains("food")) {
                            isFromBolusWizard = answers.next()
                        }
                        val carbsPattern = Pattern.compile("\\d{1,3}")
                        val carbsMatcher = carbsPattern.matcher(isFromBolusWizard)
                        if (carbsMatcher.find()) {
                            bolusInfo.carbs = carbsMatcher.group(0).toInt().toDouble()
                        }
                    } else if (isFromBolusWizard.contains("pd") && !isFromBolusWizard.contains("0.0h")) {
                        val squareBolusPattern = Pattern.compile("\\d{1,2}\\.\\d{1,2}")
                        val squareBolusMatcher = squareBolusPattern.matcher(isFromBolusWizard)
                        if (squareBolusMatcher.find()) {
                            squareBolusMatcher.group(0)
                        }
                    }
                    return Optional.of(JSONObject(bolusInfo.toJsonString()))
                }
            }
        }
        return Optional.empty()
    }

    private fun parseMatcher(answer: String): Matcher {
        val bolusDatePattern = Pattern.compile("\\d{2}:\\d{2}\\s+\\d{2}-\\d{2}-\\d{4}")
        return bolusDatePattern.matcher(answer)
    }

    @Throws(ParseException::class)
    private fun parsetTime(answer: String, matcher: Matcher): Long {
        val datePattern = "HH:mm dd-MM-yyyy"
        val formatter = SimpleDateFormat(datePattern, Locale.getDefault())
        return formatter.parse(matcher.group(0)).time
    }

    //23:39:17.874 Bolus:
    //23:39:17.903 Time:  21:52  15‑02‑2021
    //23:39:17.941 Given BL:  1.500 U
    //23:39:17.942 Set BL:  1.discoverSer500 U
    //23:39:17.978 Time PD:  0.0h  IOB:  5.200 U
    //23:39:18.053
    private fun processBolusData(answers: Iterator<String>, bolusKey: String): Double {
        val bolusData = answers.next()
        if (bolusData.contains(bolusKey)) {
            val bolusPattern = Pattern.compile("\\d{1,2}\\.\\d{3}")
            val bolusMatcher = bolusPattern.matcher(bolusData)
            if (bolusMatcher.find()) {
                return bolusMatcher.group(0).toDouble()
            }
        }
        return (-1).toDouble()
    }
}