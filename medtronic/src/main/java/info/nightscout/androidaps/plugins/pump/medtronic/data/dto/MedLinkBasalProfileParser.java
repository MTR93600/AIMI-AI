package info.nightscout.androidaps.plugins.pump.medtronic.data.dto;

import java.util.Arrays;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

import info.nightscout.androidaps.logging.AAPSLogger;

/**
 * Created by Dirceu on 12/12/20.
 */
public class MedLinkBasalProfileParser {
    private final AAPSLogger aapsLogger;
    private String activeBasalName;

    public MedLinkBasalProfileParser(AAPSLogger logger){
        this.aapsLogger = logger;
    }
    public  BasalProfile parseProfile(String profileText){
        Pattern basalName = Pattern.compile("\\s\\w*\\s");
        Pattern timePattern = Pattern.compile("\\d{2}:\\d{2}");

        Pattern ratePattern = Pattern.compile("\\w*Rate[\\s,\\d]\\d:", Pattern.CASE_INSENSITIVE);
        BasalProfile baseProfile = new BasalProfile(aapsLogger);
        Stream<String> profileLines = Arrays.stream(profileText.split("\n"));
        return profileLines.collect(() -> baseProfile, (profile, currentLine) -> {
            if (currentLine.matches("Basal\\s\\w*\\sProfile")) {
                int basalIndex = currentLine.indexOf("Basal");
                Matcher basalNameMatcher = basalName.matcher(currentLine);
                if (basalNameMatcher.find(basalIndex)) {
                    activeBasalName = basalNameMatcher.group();
                }
            }
            Matcher rateMatcher = ratePattern.matcher(currentLine);
            if (rateMatcher.find()) {
                int rateIndex = currentLine.indexOf("Rate");
                double basalAmount = Double.parseDouble(currentLine.substring(rateIndex + 3, rateIndex + 9));
                int startFromIndex = currentLine.indexOf("start from");
                Matcher basalTimeMatcher = timePattern.matcher(currentLine.substring(startFromIndex));
                if (basalTimeMatcher.find()) {
                    String[] basalTimestamp = basalTimeMatcher.group().split(":");
                    baseProfile.addEntry(new BasalProfileEntry(basalAmount, Integer.parseInt(basalTimestamp[0]), Integer.parseInt(basalTimestamp[1])));
                } else {
                    aapsLogger.error("Error parsing basal profile line " + currentLine);
                }
            }
        }, (a, b) -> new BasalProfile(aapsLogger));
    }

    public String getActiveBasalName() {
        return activeBasalName;
    }
}
