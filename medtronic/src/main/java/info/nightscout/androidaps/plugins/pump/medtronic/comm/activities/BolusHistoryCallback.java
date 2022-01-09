package info.nightscout.androidaps.plugins.pump.medtronic.comm.activities;

import org.json.JSONException;
import org.json.JSONObject;

import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Iterator;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.function.Supplier;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import info.nightscout.androidaps.data.DetailedBolusInfo;
import info.nightscout.androidaps.db.CareportalEvent;
import info.nightscout.androidaps.db.Source;
import info.nightscout.androidaps.logging.AAPSLogger;
import info.nightscout.androidaps.logging.LTag;
import info.nightscout.androidaps.plugins.pump.common.hw.medlink.activities.BaseCallback;
import info.nightscout.androidaps.plugins.pump.common.hw.medlink.activities.MedLinkStandardReturn;
import info.nightscout.androidaps.plugins.pump.medtronic.MedLinkMedtronicPumpPlugin;
import info.nightscout.androidaps.utils.DateUtil;

/**
 * Created by Dirceu on 15/02/21.
 */
public class BolusHistoryCallback extends BaseCallback<Stream<DetailedBolusInfo>,
        Supplier<Stream<String>>> {

    private final AAPSLogger aapsLogger;
    private MedLinkMedtronicPumpPlugin medLinkPumpPlugin;

    public BolusHistoryCallback(AAPSLogger aapsLogger, MedLinkMedtronicPumpPlugin medLinkPumpPlugin) {
        this.aapsLogger = aapsLogger;
        this.medLinkPumpPlugin = medLinkPumpPlugin;
    }

    @Override public MedLinkStandardReturn<Stream<DetailedBolusInfo>> apply(Supplier<Stream<String>> ans) {
        Iterator<String> answers = ans.get().iterator();
        aapsLogger.info(LTag.PUMPBTCOMM, "Bolus history");
        aapsLogger.info(LTag.PUMPBTCOMM, ans.get().collect(Collectors.joining()));
        while (answers.hasNext() && !answers.next().contains("bolus history:")) {

        }
        //Empty Line
        answers.next();
        Supplier<Stream<DetailedBolusInfo>> bolusHistory;
        try {
            Supplier<Stream<Optional<?>>> commandHistory = processBolusHistory(answers);
            Stream<DetailedBolusInfo> bolus =null;


            medLinkPumpPlugin.handleNewCareportalEvent(commandHistory.get().filter(f ->
                    f.isPresent() && f.get() instanceof CareportalEvent).
                    map(f -> (CareportalEvent) f.get()));
            Supplier<Stream<Optional<?>>> resultStream = () -> commandHistory.get().filter(
                    f -> f.isPresent() && f.get() instanceof DetailedBolusInfo);
            if (resultStream.get().count() < 3) {
                medLinkPumpPlugin.readBolusHistory(true);
            }
            medLinkPumpPlugin.handleNewTreatmentData(resultStream.get().map(f -> (DetailedBolusInfo) f.get()));
            return new MedLinkStandardReturn<>(ans, resultStream.get().map(f -> (DetailedBolusInfo) f.get()), Collections.emptyList());
        } catch (ParseException e) {
            e.printStackTrace();
            return new MedLinkStandardReturn<>(ans, Stream.empty(),
                    MedLinkStandardReturn.ParsingError.BolusParsingError);
        }
    }

    private Supplier<Stream<Optional<?>>> processBolusHistory(Iterator<String> answers) throws ParseException {
        List<Optional<? extends Object>> resultList = new ArrayList<>();
        while (answers.hasNext()) {
            resultList.add(processData(answers));
        }
        Supplier<Stream<Optional<?>>> resultStream = resultList::stream;
        return resultStream;
    }

    private Optional<? extends Object> processData(Iterator<String> answers) throws ParseException {
        String answer = answers.next();
        if (answer.contains("bolus:")) {
            return processBolus(answers);
        } else if (answer.contains("battery insert")) {
            return processBattery(answers.next());
        } else if (answer.contains("reservoir change")) {
            return processSite(answers.next());
        } else if (answer.contains("reservoir rewind")) {
            return processReservoir(answers.next());
        } else {
            return Optional.empty();
        }
    }

    private Optional<? extends Object> processSite(String answer) throws ParseException {
            Matcher matcher = parseMatcher(answer);
            if (matcher.find()) {
                JSONObject json = new JSONObject();
                long date = parsetTime(answer, matcher);
                try {
                    json.put("created_at", DateUtil.toISOString(date));
                    json.put("mills", date);
                    json.put("eventType", CareportalEvent.SITECHANGE);
                } catch (JSONException e) {
                    e.printStackTrace();
                }

                CareportalEvent event = new CareportalEvent(medLinkPumpPlugin.getInjector());
                event.date = date;
                event.eventType = CareportalEvent.INSULINCHANGE;
                event.source = Source.PUMP;
                event.json = json.toString();
                aapsLogger.debug("USER ENTRY: CAREPORTAL ${careportalEvent.eventType} json: ${careportalEvent.json}");
                return Optional.of(event);
            }
            return Optional.empty();
        }

    private Optional<? extends Object> processReservoir(String answer) throws ParseException {
        Matcher matcher = parseMatcher(answer);
        if (matcher.find()) {
            JSONObject json = new JSONObject();
            long date = parsetTime(answer, matcher);
            try {
                json.put("created_at", DateUtil.toISOString(date));
                json.put("mills", date);
                json.put("eventType", CareportalEvent.INSULINCHANGE);
            } catch (JSONException e) {
                e.printStackTrace();
            }

            CareportalEvent event = new CareportalEvent(medLinkPumpPlugin.getInjector());
            event.date = date;
            event.eventType = CareportalEvent.INSULINCHANGE;
            event.source = Source.PUMP;
            event.json = json.toString();
            aapsLogger.debug("USER ENTRY: CAREPORTAL ${careportalEvent.eventType} json: ${careportalEvent.json}");
            return Optional.of(event);
        }
        return Optional.empty();
    }

    private Optional<CareportalEvent> processBattery(String answer) throws ParseException {
        Matcher matcher = parseMatcher(answer);
        if (matcher.find()) {
            JSONObject json = new JSONObject();
            long date = parsetTime(answer, matcher);
            try {
                json.put("created_at", DateUtil.toISOString(date));
                json.put("mills", date);
                json.put("eventType", CareportalEvent.PUMPBATTERYCHANGE);
            } catch (JSONException e) {
                e.printStackTrace();
            }

            CareportalEvent event = new CareportalEvent(medLinkPumpPlugin.getInjector());
            event.date = date;
            event.eventType = CareportalEvent.PUMPBATTERYCHANGE;
            event.source = Source.PUMP;
            event.json = json.toString();
            aapsLogger.debug("USER ENTRY: CAREPORTAL ${careportalEvent.eventType} json: ${careportalEvent.json}");
            return Optional.of(event);
        }
        return Optional.empty();
    }

    private Optional<DetailedBolusInfo> processBolus(Iterator<String> answers) throws ParseException {

        if (answers.hasNext()) {
            String answer = answers.next();
            Matcher matcher = parseMatcher(answer);
            if (matcher.find()) {
                DetailedBolusInfo bolusInfo = new DetailedBolusInfo();
                bolusInfo.date = parsetTime(answer, matcher);
                bolusInfo.deliverAt = bolusInfo.date;
                bolusInfo.insulin = processBolusData(answers, "given bl:");
                bolusInfo.source = Source.PUMP;
//                bolusInfo.pumpId = Long.parseLong(
//                        medLinkPumpPlugin.getMedLinkService().getMedLinkServiceData().pumpID);
                Double setBolus = processBolusData(answers, "set bl:");
                if (answers.hasNext()) {
                    String isFromBolusWizard = answers.next();
                    if (isFromBolusWizard.contains("enter from bolus wizard")) {
                        String bg = answers.next();
                        if (bg.contains("glucose")) {
                            Pattern bgPattern = Pattern.compile("\\d{1,3}");
                            Matcher bgMatcher = bgPattern.matcher(bg);
                            if (bgMatcher.find()) {
                                bolusInfo.glucose = Double.parseDouble(bgMatcher.group(0));
                            }
                        }

                        while (answers.hasNext() && !isFromBolusWizard.contains("food")) {
                            isFromBolusWizard = answers.next();
                        }
                        Pattern carbsPattern = Pattern.compile("\\d{1,3}");
                        Matcher carbsMatcher = carbsPattern.matcher(isFromBolusWizard);
                        if (carbsMatcher.find()) {
                            bolusInfo.carbs = Integer.parseInt(carbsMatcher.group(0));

                        }
                    } else if (isFromBolusWizard.contains("pd") && !isFromBolusWizard.contains("0.0h")) {
                        Pattern squareBolusPattern = Pattern.compile("\\d{1,2}\\.\\d{1,2}");
                        Matcher squareBolusMatcher = squareBolusPattern.matcher(isFromBolusWizard);
                        if (squareBolusMatcher.find()) {
                            squareBolusMatcher.group(0);
                        }
                    }
                    return Optional.of(bolusInfo);
                }
            }
        }
        return Optional.empty();
    }

    private Matcher parseMatcher(String answer) {
        Pattern bolusDatePattern = Pattern.compile("\\d{2}:\\d{2}\\s+\\d{2}-\\d{2}-\\d{4}");
        return bolusDatePattern.matcher(answer);
    }

    private long parsetTime(String answer, Matcher matcher) throws ParseException {
        String datePattern = "HH:mm dd-MM-yyyy";
        SimpleDateFormat formatter = new SimpleDateFormat(datePattern, Locale.getDefault());
        return formatter.parse(matcher.group(0)).getTime();
    }
//23:39:17.874 Bolus:
//23:39:17.903 Time:  21:52  15‑02‑2021
//23:39:17.941 Given BL:  1.500 U
//23:39:17.942 Set BL:  1.discoverSer500 U
//23:39:17.978 Time PD:  0.0h  IOB:  5.200 U
//23:39:18.053

    private double processBolusData(Iterator<String> answers, String bolusKey) {
        String bolusData = answers.next();
        if (bolusData.contains(bolusKey)) {
            Pattern bolusPattern = Pattern.compile("\\d{1,2}\\.\\d{3}");
            Matcher bolusMatcher = bolusPattern.matcher(bolusData);
            if (bolusMatcher.find()) {
                return Double.parseDouble(bolusMatcher.group(0));
            }
        }
        return -1;
    }
}
