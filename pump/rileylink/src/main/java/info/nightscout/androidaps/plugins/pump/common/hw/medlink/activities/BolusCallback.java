package info.nightscout.androidaps.plugins.pump.common.hw.medlink.activities;

import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Supplier;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import info.nightscout.androidaps.plugins.pump.common.hw.medlink.PumpResponses;
import info.nightscout.androidaps.plugins.pump.common.hw.medlink.ble.data.BolusAnswer;
import info.nightscout.interfaces.pump.DetailedBolusInfo;
import info.nightscout.rx.logging.AAPSLogger;
import info.nightscout.rx.logging.LTag;

/**
 * Created by Dirceu on 21/12/20.
 */
public class BolusCallback extends BaseCallback<BolusAnswer, Supplier<Stream<String>>> {

    private final AAPSLogger aapsLogger;
    private DetailedBolusInfo bolusInfo;
    private final Pattern deliveredBolusPattern = Pattern.compile(":\\s+\\d{1,2}\\.\\du\\s+\\d{1,2}-\\d{2}-\\d{2}\\s+\\d{1,2}:\\d{1,2}", Pattern.CASE_INSENSITIVE);
    private final Pattern deliveringBolusPattern = Pattern.compile(":\\s+\\d{1,2}\\.\\du", Pattern.CASE_INSENSITIVE);

    public BolusCallback(AAPSLogger aapsLogger, DetailedBolusInfo detailedBolusInfo) {
        super();
        this.aapsLogger = aapsLogger;
        this.bolusInfo = detailedBolusInfo;
    }

    @Override public MedLinkStandardReturn<BolusAnswer> apply(Supplier<Stream<String>> answers) {
        aapsLogger.info(LTag.PUMPBTCOMM, "BolusCallback");
        //TODO fix error response
        AtomicReference<BolusAnswer> pumpResponse = new AtomicReference<>();
        if (answers.get().anyMatch(f -> f.toLowerCase().contains("pump is bolusing state")) ||
                answers.get().anyMatch(this::isBolusing)) {
            answers.get().filter(f -> f.toLowerCase().contains("last bolus:")).findFirst().map(f -> {
                pumpResponse.set(deliveringBolus(f));
                return pumpResponse;
            });
        } else if (answers.get().anyMatch(f -> f.toLowerCase().contains("last bolus:"))) {
            answers.get().filter(f -> f.toLowerCase().contains("last bolus:")).findFirst().map(f -> {
                pumpResponse.set(deliveredBolus(f));
                return pumpResponse;
            });
        } else {
            pumpResponse.set(new BolusAnswer(PumpResponses.UnknownAnswer,
                    answers.get().collect(Collectors.joining()), bolusInfo));
        }
        return createPumpResponse(answers, pumpResponse);
    }

    private boolean isBolusing(String toBeMatched) {
        return (!deliveredBolusPattern.matcher(toBeMatched).find() && deliveringBolusPattern.matcher(toBeMatched).find()) || toBeMatched.toLowerCase().contains("pump is bolusing state");

    }

    private MedLinkStandardReturn<BolusAnswer> createPumpResponse(Supplier<Stream<String>> answers, AtomicReference<BolusAnswer> pumpResponse) {
        if (pumpResponse.get() != null) {
            return new MedLinkStandardReturn<>(answers, pumpResponse.get());
        } else {
            return new MedLinkStandardReturn<>(answers, new BolusAnswer(
                    PumpResponses.UnknownAnswer, answers.get().collect(Collectors.joining()),
                    bolusInfo),MedLinkStandardReturn.ParsingError.BolusParsingError);
        } 
    }

    private BolusAnswer deliveringBolus(String input) {
        aapsLogger.info(LTag.PUMPBTCOMM, "match bolus");
        Pattern deliveredBolusPattern = Pattern.compile("\\d{1,2}\\.\\du", Pattern.CASE_INSENSITIVE);
        Matcher matcher = deliveredBolusPattern.matcher(input);
        if (matcher.find()) {
            String units = matcher.group(0);
            assert units != null;
            double delivered = Double.parseDouble(units.substring(0, units.length() - 1));
//                12:09 01-06
            return new BolusAnswer(PumpResponses.DeliveringBolus, delivered, input, bolusInfo.carbs);


        }
        return new BolusAnswer(PumpResponses.UnknownAnswer, input, bolusInfo);
    }


    private BolusAnswer deliveredBolus(String input) {
        aapsLogger.info(LTag.PUMPBTCOMM, "match bolus");
//        Pattern deliveredBolusPattern = Pattern.compile(lastBolusPattern, Pattern.CASE_INSENSITIVE);
        Matcher matcher = deliveredBolusPattern.matcher(input);
        if (matcher.find()) {
            Pattern unitsPattern = Pattern.compile("\\d{1,2}\\.\\du");
            Matcher unitsMatcher = unitsPattern.matcher(input);
            if (unitsMatcher.find()) {
                String units = unitsMatcher.group(0);
                assert units != null;
                double delivered = Double.parseDouble(units.substring(0, units.length() - 1).trim());
//                12:09 01-06
                Pattern deliveredTimePattern = Pattern.compile("\\d{1,2}-\\d{2}-\\d{2}\\s+\\d{1,2}:\\d{1,2}", Pattern.CASE_INSENSITIVE);
                Matcher deliveredTimeMatcher = deliveredTimePattern.matcher(input);
                if (deliveredTimeMatcher.find()) {
                    String deliveredTime = deliveredTimeMatcher.group(0);
                    assert deliveredTime != null;
//                    String[] dateTime = deliveredTime.split(" ");
//                    String[] time = dateTime[0].split(":");
//                    String[] date = dateTime[1].split("-");
                    DateTimeFormatter formatter = DateTimeFormatter.ofPattern("dd-MM-yy HH:mm");
                    ZonedDateTime bolusDeliveredAt = LocalDateTime.parse(deliveredTime, formatter).atZone(ZoneId.systemDefault());
                    bolusInfo.timestamp = bolusDeliveredAt.toInstant().toEpochMilli();
                    return new BolusAnswer(PumpResponses.BolusDelivered, delivered, bolusInfo);
                } else if (bolusInfo != null && unitsMatcher.find()) {
                    units = unitsMatcher.group(1);
                    assert units != null;
                    double lastBolusAmount = Double.parseDouble(units.substring(0, units.length() - 1).trim());
                    if (lastBolusAmount == bolusInfo.insulin) {
                        return new BolusAnswer(PumpResponses.DeliveringBolus, input, bolusInfo);
                    }
                }
            }
        }
        return new BolusAnswer(PumpResponses.UnknownAnswer, input, bolusInfo);
    }

//    private int getYear(String[] date) {
//        ZonedDateTime now = ZonedDateTime.now();
//        if (now.getMonthValue() == 1 && Integer.parseInt(date[1]) == 12) {
//            return now.getYear() - 1;
//        } else {
//            return now.getYear();
//        }
//    }

}
