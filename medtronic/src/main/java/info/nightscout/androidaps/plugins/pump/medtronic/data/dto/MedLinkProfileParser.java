package info.nightscout.androidaps.plugins.pump.medtronic.data.dto;

import org.json.JSONException;
import org.json.JSONObject;

import java.util.Iterator;
import java.util.List;
import java.util.TimeZone;
import java.util.function.Supplier;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

import dagger.android.HasAndroidInjector;
import info.nightscout.androidaps.Constants;
import info.nightscout.androidaps.data.Profile;
import info.nightscout.androidaps.db.WizardSettings;
import info.nightscout.androidaps.logging.AAPSLogger;
import info.nightscout.androidaps.logging.LTag;
import info.nightscout.androidaps.plugins.pump.medtronic.MedLinkMedtronicPumpPlugin;

/**
 * Created by Dirceu on 01/02/21.
 */
public class MedLinkProfileParser {

    private final HasAndroidInjector hasAndroidInjector;
    private final MedLinkMedtronicPumpPlugin pumpPlugin;
    private Pattern hourPattern = Pattern.compile("\\d{2}:\\d{2}");
    private final AAPSLogger aapsLogger;
    private String units;

    public MedLinkProfileParser(HasAndroidInjector hasAndroidInjector, AAPSLogger aapsLogger,
                                MedLinkMedtronicPumpPlugin pumpPlugin) {
        this.aapsLogger = aapsLogger;
        this.hasAndroidInjector = hasAndroidInjector;
        this.pumpPlugin = pumpPlugin;
    }

    public Profile parseProfile(Supplier<Stream<String>> answer) throws JSONException {

        Iterator<String> profileData = answer.get().map(String::toLowerCase).iterator();
        while (profileData.hasNext() && !profileData.next().contains("bolus wizard settings")) {

        }
        StringBuffer buffer = new StringBuffer();
        buffer.append("{");
        buffer = addData(buffer, "dia", true);
        buffer = addData(buffer, Constants.defaultDIA, false);
        parseBolusWizardSettings(profileData);

        buffer = parseCarbRatio(profileData, buffer);

        buffer = parseInsulinSensitivity(profileData, buffer);
        buffer.append(",");
        addData(buffer, "timezone", true);
        addData(buffer, TimeZone.getDefault().getID(), false);
        buffer = parseBGTargets(profileData, buffer);
        buffer.append(",");
        addData(buffer, "units", true);
        addData(buffer, units, false);
        if(pumpPlugin.getBasalProfile()!=null) {
            buffer = parseBasal(pumpPlugin.getBasalProfile().listEntries, buffer);
        }
        buffer.append("}");
        JSONObject json = new JSONObject(buffer.toString());
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
        return new Profile(hasAndroidInjector, json);
    }


    private StringBuffer addData(StringBuffer buffer, Object dia, Boolean isParameter) {
        StringBuffer result = buffer.append("\"").append(dia).append("\"");
        if (isParameter) {
            result.append(":");
        }
        return result;
    }


    private StringBuffer parseBGTargets(Iterator<String> profileData, StringBuffer buffer) {
        StringBuffer targetHigh = new StringBuffer();
        addData(targetHigh, "target_high", true);
        targetHigh.append("[");
        StringBuffer targetLow = new StringBuffer();
        addData(targetLow, "target_low", true);
        targetLow.append("[");
        String ratioText = profileData.next();
        String toAppend = "";
        while (!ratioText.contains("ready")) {
            targetHigh.append(toAppend);
            targetLow.append(toAppend);
            int grIndex = ratioText.indexOf("-");
            int separatorIndex = ratioText.indexOf(":") + 1;
            int fromIndex = ratioText.indexOf("from");
            aapsLogger.info(LTag.PUMPBTCOMM, ratioText + " " + separatorIndex + " " + grIndex);
            Integer lowerRate = Integer.valueOf(ratioText.substring(separatorIndex, grIndex).trim());
            Integer higherRate = Integer.valueOf(ratioText.substring(grIndex + 1, fromIndex).trim());

            Matcher matcher = hourPattern.matcher(ratioText);
            if (matcher.find()) {
                String hour = matcher.group(0);
                addTimeValue(targetHigh, hour, higherRate);
                addTimeValue(targetLow, hour, lowerRate);
                toAppend = ",";
            } else {
                aapsLogger.info(LTag.PUMPBTCOMM, "bg target matcher doesn't found hour");
            }
            ratioText = profileData.next();
        }
        targetHigh.append("]");
        targetLow.append("]");
        return buffer.append(",").append(targetHigh).append(",").append(targetLow);
    }

    private StringBuffer parseInsulinSensitivity(Iterator<String> profileData, StringBuffer buffer) {
        String ratioText = profileData.next();
        buffer.append(",");
        addData(buffer, "sens", true);
        buffer.append("[");
        String toAppend = "";
        while (!ratioText.contains("bg targets")) {
            buffer.append(toAppend);
            int separatorIndex = ratioText.indexOf(":") + 1;
            this.units = ratioText.substring(separatorIndex + 4, ratioText.indexOf("from")).trim();
            Integer insulinSensitivity = Integer.valueOf(ratioText.substring(separatorIndex, separatorIndex + 4).trim());
            Matcher matcher = hourPattern.matcher(ratioText);
            if (matcher.find()) {
                String hour = matcher.group(0);
                addTimeValue(buffer, hour, insulinSensitivity);
                toAppend = ",";
//                result.add(new InsulinSensitivity(insulinSensitivity, hour));
            } else {
                aapsLogger.info(LTag.PUMPBTCOMM, "insulinsensitivity matcher doesn't found hour");
            }
            ratioText = profileData.next();
        }
        return buffer.append("]");
    }

    private StringBuffer parseBasal(List<BasalProfileEntry> basalProfile, StringBuffer buffer) {
        buffer.append(",");
        buffer = addData(buffer, "basal", true);
        String toAppend = "[";

        for (BasalProfileEntry basalEntry : basalProfile) {
            buffer.append(toAppend);
            addTimeValue(buffer, basalEntry.startTime.toString("HH:mm"), basalEntry.rate);
            toAppend = ",";
        }
        buffer.append("]");
        return buffer;
    }

    private StringBuffer parseCarbRatio(Iterator<String> profileText, StringBuffer buffer) {
        buffer.append(",");
        buffer = addData(buffer, "carbratio", true);

        String carbRatio = profileText.next();
        if (carbRatio.contains("carb ratios")) {
            String ratioText = profileText.next();
            String toAppend = "";
            buffer.append("[");
            while (!ratioText.contains("insulin sensitivities")) {
                int grIndex = ratioText.indexOf("gr/u");
                int separatorIndex = ratioText.indexOf(":") + 1;
                aapsLogger.info(LTag.PUMPBTCOMM, ratioText + " " + separatorIndex + " " +
                        grIndex);
                Integer ratio = Integer.valueOf(ratioText.substring(separatorIndex, grIndex).trim());
                Matcher matcher = hourPattern.matcher(ratioText);
                if (matcher.find()) {
                    String hour = matcher.group(0);
                    buffer.append(toAppend);
                    addTimeValue(buffer, hour, ratio);
                    toAppend = ",";
                } else {
                    aapsLogger.info(LTag.PUMPBTCOMM, "carbratio matcher doesn't found hour");
                }

                ratioText = getNextValideLine(profileText);
            }
            buffer.append("]");
        } else {
            aapsLogger.info(LTag.PUMPBTCOMM, "profile doesn't have carb ratios");
        }
        return buffer;
    }

    private String getNextValideLine(Iterator<String> profileText){
        String ratioText = profileText.next();
        if(ratioText.contains("error command")){
            return getNextValideLine(profileText);
        }else{
            return ratioText;
        }
    }
    private void addTimeValue(StringBuffer buffer, String hour, Object ratio) {
        buffer.append("{");
        addData(buffer, "time", true);
        addData(buffer, hour, false);
        buffer.append(",");
        addData(buffer, "value", true);
        buffer.append(ratio);
        buffer.append("}");
    }

    private void parseBolusWizardSettings(Iterator<String> profileText) {
        WizardSettings result = new WizardSettings();
        String maxBolusText = profileText.next();
        Pattern pattern = Pattern.compile("\\d+\\.\\d");
        if (maxBolusText.contains("max. bolus")) {
            Matcher matcher = pattern.matcher(maxBolusText);
            if (matcher.find()) {
                result.maxBolus = Double.parseDouble(matcher.group(0));
            }
        }
        String easyBolusStepText = profileText.next();

        if (easyBolusStepText.contains("easy bolus")) {
            Matcher matcher = pattern.matcher(easyBolusStepText);
            if (matcher.find()) {
                result.easyBolusStep = Double.parseDouble(matcher.group(0));
            }
        }

        if (result.maxBolus == 0d && result.easyBolusStep == 0d) {
            aapsLogger.info(LTag.PUMPBTCOMM, "Failed to parse max and bolus step " +
                    maxBolusText + " " + easyBolusStepText);
        }
    }
}
