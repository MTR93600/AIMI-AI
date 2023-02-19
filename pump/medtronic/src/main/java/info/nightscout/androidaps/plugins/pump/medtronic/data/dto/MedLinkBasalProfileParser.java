package info.nightscout.androidaps.plugins.pump.medtronic.data.dto;

import java.util.function.Supplier;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

import info.nightscout.rx.logging.AAPSLogger;
import info.nightscout.rx.logging.LTag;


/**
 * Created by Dirceu on 12/12/20.
 * used by medlink
 */
public class MedLinkBasalProfileParser {
    private final AAPSLogger aapsLogger;
    private String activeBasalName;

    public MedLinkBasalProfileParser(AAPSLogger logger){
        this.aapsLogger = logger;
    }
    public  BasalProfile parseProfile(Supplier<Stream<String>> profileText){
        Pattern basalName = Pattern.compile("\\s\\w*\\s");
        Pattern timePattern = Pattern.compile("\\d{2}:\\d{2}");

        Pattern ratePattern = Pattern.compile("\\w*rate[\\s,\\d]\\d:", Pattern.CASE_INSENSITIVE);
        BasalProfile baseProfile = new BasalProfile(aapsLogger);
        return profileText.get().collect(() -> baseProfile, (profile, currentLine) -> {
            if (currentLine.matches("basal\\s\\w*\\sprofile:\\r")) {
                int basalIndex = currentLine.indexOf("basal");
                Matcher basalNameMatcher = basalName.matcher(currentLine);
                if (basalNameMatcher.find(basalIndex)) {
                    activeBasalName = basalNameMatcher.group();
                }
            }
            Matcher rateMatcher = ratePattern.matcher(currentLine);
            if (rateMatcher.find()) {
                aapsLogger.info(LTag.PUMPBTCOMM, currentLine);
                int rateIndex = currentLine.indexOf("rate");
                double basalAmount = Double.parseDouble(currentLine.substring(rateIndex + 8, rateIndex + 13));
                int startFromIndex = currentLine.indexOf("start from");
                Matcher basalTimeMatcher = timePattern.matcher(currentLine.substring(startFromIndex));
                if (basalTimeMatcher.find()) {
                    String[] basalTimestamp = basalTimeMatcher.group().split(":");
                    baseProfile.addEntry(new MedLinkBasalProfileEntry(basalAmount, Integer.parseInt(basalTimestamp[0]), Integer.parseInt(basalTimestamp[1])));
                    profile.addEntry(new MedLinkBasalProfileEntry(basalAmount, Integer.parseInt(basalTimestamp[0]), Integer.parseInt(basalTimestamp[1])));
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
