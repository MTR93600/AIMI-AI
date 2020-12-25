package info.nightscout.androidaps.plugins.pump.common.hw.medlink.service;

import java.text.ParsePosition;
import java.text.SimpleDateFormat;
import java.util.Arrays;
import java.util.Date;
import java.util.Iterator;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import info.nightscout.androidaps.plugins.pump.common.data.PumpStatus;

/**
 * Created by Dirceu on 13/12/20.
 */
public class MedLinkStatusParser {

    private static Pattern dateTimeFullPattern = Pattern.compile("\\d{2}-\\d{2}-\\d{4}\\s\\d{2}:\\d{2}");
    private static Pattern dateTimePartialPattern = Pattern.compile("\\d{2}-\\d{2}-\\d{2}\\s\\d{2}:\\d{2}");

    public static PumpStatus parseStatus(String[] pumpAnswer, PumpStatus pumpStatus) {

//        13‑12‑2020 18:36  54%
        Iterator<String> messageIterator = Arrays.stream(pumpAnswer).iterator();
        while(messageIterator.hasNext() ){
            String message = messageIterator.next().trim().toLowerCase();
            if(message.equals("ready")){
                break;
            }
        }
        PumpStatus timePumpStatus = parsePumpTimeMedLinkBattery(messageIterator, pumpStatus);
        PumpStatus bgPumpStatus = parseBG(messageIterator, timePumpStatus);

        PumpStatus lastBolusStatus = parseLastBolus(messageIterator, bgPumpStatus);
//                18:36:49.381
//        18:36:49.495 Last bolus: 0.2u 13‑12‑20 18:32
        moveIterator(messageIterator);
//        18:36:49.496 Square bolus: 0.0u delivered: 0.000u
        moveIterator(messageIterator);
//        18:36:49.532 Square bolus time: 0h:00m / 0h:00m
        moveIterator(messageIterator);
//        18:36:49.570 ISIG: 20.62nA
        moveIterator(messageIterator);
//        18:36:49.607 Calibration factor: 6.419
        moveIterator(messageIterator);
//        18:36:49.681 Next calibration time:  5:00
        moveIterator(messageIterator);
//        18:36:49.683 Sensor uptime: 1483min
        moveIterator(messageIterator);
//        18:36:49.719 BG target:  75‑160
        PumpStatus batteryStatus = parseBatteryVoltage(messageIterator, lastBolusStatus);
//        18:36:49.832 Pump battery voltage: 1.43V
        PumpStatus reservoirStatus = parseReservoir(messageIterator, batteryStatus);

//        18:36:49.907 Reservoir:  66.12u
        PumpStatus basalStatus = parseCurrentBasal(messageIterator, reservoirStatus);
//        18:36:49.982 Basal scheme: STD
        moveIterator(messageIterator);
//        18:36:49.983 Basal: 0.600u/h
        PumpStatus tempBasalStatus = parseTempBasal(messageIterator, basalStatus);

//        18:36:50.020 TBR: 100%   0h:00m
        PumpStatus dailyTotal = parseTodayInsulin(messageIterator, tempBasalStatus);
//        18:36:50.058 Insulin today: 37.625u
        moveIterator(messageIterator);
//        18:36:50.095 Insulin yesterday: 48.625u
        moveIterator(messageIterator);

//        18:36:50.132 Max. bolus: 15.0u
        moveIterator(messageIterator);
//        18:36:50.171 Easy bolus step: 0.1J
        moveIterator(messageIterator);
//        18:36:50.244 Max. basal rate: 2.000J/h
        moveIterator(messageIterator);
//        18:36:50.282 Insulin duration time: 3h
        moveIterator(messageIterator);
//        18:36:50.448 Pump status: NORMAL
        moveIterator(messageIterator);
//        18:36:50.471 EomEomEom
        return dailyTotal;
    }

    private static PumpStatus parseTodayInsulin(Iterator<String> messageIterator, PumpStatus pumpStatus) {
        if (messageIterator.hasNext()) {
            String currentLine = messageIterator.next();
            //        Insulin today: 37.625u
            if (currentLine.toLowerCase().contains("insulin today:")) {
                Pattern reservoirPattern = Pattern.compile("\\d+\\.\\d+u");
                Matcher matcher = reservoirPattern.matcher(currentLine);
                if (matcher.find()) {
                    String totalInsulinToday = matcher.group();
                    pumpStatus.dailyTotalUnits = Double.parseDouble(totalInsulinToday.substring(0, totalInsulinToday.length() - 1));
                }
            }
        }
        return pumpStatus;
    }

    private static PumpStatus parseTempBasal(Iterator<String> messageIterator, PumpStatus pumpStatus) {
        if (messageIterator.hasNext()) {
            String currentLine = messageIterator.next();
            //        18:36:50.020 TBR: 100%   0h:00m
            if (currentLine.toLowerCase().contains("tbr:")) {
                Pattern reservoirPattern = Pattern.compile("\\d+%");
                Matcher matcher = reservoirPattern.matcher(currentLine);
                if (matcher.find()) {
                    String reservoirRemaining = matcher.group();
                    pumpStatus.tempBasalRatio = Integer.parseInt(reservoirRemaining.substring(0, reservoirRemaining.length() - 1));
                    Pattern remTempTimePattern = Pattern.compile("\\d+h:\\d+m");
                    Matcher remTempTimeMatcher = remTempTimePattern.matcher(currentLine);
                    if (remTempTimeMatcher.find()) {
                        String remaingTime = remTempTimeMatcher.group();
                        String[] hourMinute = remaingTime.split(":");
                        String hour = hourMinute[0];
                        String minute = hourMinute[1];
                        pumpStatus.tempBasalRemainMin = 60 * Integer.parseInt(hour.substring(0, hour.length() - 1)) + Integer.parseInt(minute.substring(0, minute.length() - 1));
                    }
                }
            }
        }
        return pumpStatus;
    }

    private static PumpStatus parseCurrentBasal(Iterator<String> messageIterator, PumpStatus pumpStatus) {
        if (messageIterator.hasNext()) {
            String currentLine = messageIterator.next();
            //        18:36:49.983 Basal: 0.600u/h
            if (currentLine.toLowerCase().contains("basal scheme:")) {
                String[] basalScheme = currentLine.split(":");
                pumpStatus.activeProfileName = basalScheme[1].trim();
                currentLine = messageIterator.next();
                if(currentLine.toLowerCase().contains("basal:")) {
                    Pattern reservoirPattern = Pattern.compile("\\d+\\.\\d+u/h");
                    Matcher matcher = reservoirPattern.matcher(currentLine);
                    if (matcher.find()) {
                        String currentBasal = matcher.group();
                        pumpStatus.currentBasal = Double.parseDouble(currentBasal.substring(0, currentBasal.length() - 3));
                    }
                }
            }
        }
        return pumpStatus;
    }

    private static PumpStatus parseReservoir(Iterator<String> messageIterator, PumpStatus pumpStatus) {
        if (messageIterator.hasNext()) {
            String currentLine = messageIterator.next();
            //        18:36:49.907 Reservoir:  66.12u
            if (currentLine.toLowerCase().contains("reservoir")) {
                Pattern reservoirPattern = Pattern.compile("\\d+\\.\\d+u");
                Matcher matcher = reservoirPattern.matcher(currentLine);
                if (matcher.find()) {
                    String reservoirRemaining = matcher.group();
                    pumpStatus.reservoirRemainingUnits = Double.parseDouble(reservoirRemaining.substring(0, reservoirRemaining.length() - 1));
                }
            }
        }
        return pumpStatus;
    }

    private static void moveIterator(Iterator<String> messageIterator) {
        if (messageIterator.hasNext()) {
            messageIterator.next();
        }
    }

    private static PumpStatus parseBatteryVoltage(Iterator<String> messageIterator, PumpStatus pumpStatus) {
//        18:36:49.832 Pump battery voltage: 1.43V
        if (messageIterator.hasNext()) {
            String currentLine = messageIterator.next();
            if (currentLine.toLowerCase().contains("pump battery voltage")) {
                Pattern lastBolusPattern = Pattern.compile("\\d\\.\\d{1,2}V");
                Matcher matcher = lastBolusPattern.matcher(currentLine);
                if (matcher.find()) {
                    String batteryVoltage = matcher.group();
                    pumpStatus.batteryVoltage = Double.valueOf(batteryVoltage.substring(0, batteryVoltage.length() - 1));

                }
            }
        }
        return pumpStatus;
    }

    private static PumpStatus parseLastBolus(Iterator<String> messageIterator, PumpStatus pumpStatus) {
        if (messageIterator.hasNext()) {
            String currentLine = messageIterator.next();
//        18:36:49.495 Last bolus: 0.2u 13‑12‑20 18:32
            if (currentLine.toLowerCase().contains("last bolus")) {
                Pattern lastBolusPattern = Pattern.compile("\\d{1,2}\\.\\du");
                Matcher matcher = lastBolusPattern.matcher(currentLine);
                if (matcher.find()) {
                    String lastBolusAmount = matcher.group();
                    pumpStatus.lastBolusAmount = Double.valueOf(lastBolusAmount.substring(0, lastBolusAmount.length() - 1));
                    Date dateTime = parseDateTime(currentLine, dateTimePartialPattern, false);
                    if (dateTime != null) {
                        pumpStatus.lastBolusTime = dateTime;
                    }
                }

            }

        }
        return pumpStatus;
    }

    private static PumpStatus parseBG(Iterator<String> messageIterator, PumpStatus pumpStatus) {
        if (messageIterator.hasNext()) {
            String currentLine = messageIterator.next();
//            BG: 120 13‑12‑20 18:33

            Pattern bgLinePattern = Pattern.compile("BG:\\s+\\d{2,3}");
            Matcher matcher = bgLinePattern.matcher(currentLine);
            if (matcher.find()) {
                String matched = matcher.group(0);
                Pattern bgPattern = Pattern.compile("\\d{2,3}");
                Matcher bgMatcher = bgPattern.matcher(matched);
                if (bgMatcher.find()) {
                    Integer bg = Integer.valueOf(bgMatcher.group());
//                    pumpStatus.b
                }
            }
        }
        return pumpStatus;
    }

    private static PumpStatus parsePumpTimeMedLinkBattery(Iterator<String> messageIterator, PumpStatus pumpStatus) {
        if (messageIterator.hasNext()) {
            String currentLine = messageIterator.next();
            while(currentLine.toLowerCase().startsWith("ready")){
                currentLine = messageIterator.next();
            }
            //        13‑12‑2020 18:36  54%

            Date dateTime = parseDateTime(currentLine,dateTimeFullPattern, true);
            if (dateTime != null) {
                pumpStatus.lastDataTime = dateTime.getTime();
            }
        }
        return pumpStatus;
    }

    private static Date parseDateTime(String currentLine, Pattern pattern, boolean fourDigitYear) {
        Matcher matcher = pattern.matcher(currentLine);
        if (matcher.find()) {
            String datePattern;
            if(fourDigitYear){
                datePattern = "dd-MM-yyyy HH:mm";
            } else {
                datePattern = "dd-MM-yy HH:mm";
            }
            SimpleDateFormat formatter = new SimpleDateFormat(datePattern, Locale.getDefault());
            return formatter.parse(matcher.group(), new ParsePosition(0));
        } else {
            return null;

        }
    }

    public boolean partialMatch(PumpStatus pumpStatus){
        return pumpStatus.lastDataTime != 0 || pumpStatus.lastBolusTime != null ||
                pumpStatus.batteryVoltage != 0d ||
                pumpStatus.reservoirRemainingUnits != 0.0d || pumpStatus.currentBasal != 0.0d ||
                pumpStatus.dailyTotalUnits != null;
    }

    public boolean fullMatch(PumpStatus pumpStatus){
        return pumpStatus.lastDataTime != 0 && pumpStatus.lastBolusTime != null &&
                pumpStatus.batteryVoltage != 0d &&
                pumpStatus.dailyTotalUnits != null;
    }
}
