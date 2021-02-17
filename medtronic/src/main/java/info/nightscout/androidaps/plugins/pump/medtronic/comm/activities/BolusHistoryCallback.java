package info.nightscout.androidaps.plugins.pump.medtronic.comm.activities;

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
import java.util.stream.Stream;

import info.nightscout.androidaps.data.DetailedBolusInfo;
import info.nightscout.androidaps.db.Source;
import info.nightscout.androidaps.logging.AAPSLogger;
import info.nightscout.androidaps.logging.LTag;
import info.nightscout.androidaps.plugins.pump.common.hw.medlink.activities.BaseCallback;
import info.nightscout.androidaps.plugins.pump.common.hw.medlink.activities.MedLinkStandardReturn;
import info.nightscout.androidaps.plugins.pump.medtronic.MedLinkMedtronicPumpPlugin;

/**
 * Created by Dirceu on 15/02/21.
 */
public class BolusHistoryCallback extends BaseCallback<Stream<DetailedBolusInfo>> {

    private final AAPSLogger aapsLogger;
    private MedLinkMedtronicPumpPlugin medLinkPumpPlugin;

    public BolusHistoryCallback(AAPSLogger aapsLogger, MedLinkMedtronicPumpPlugin medLinkPumpPlugin) {
        this.aapsLogger = aapsLogger;
        this.medLinkPumpPlugin = medLinkPumpPlugin;
    }

    @Override public MedLinkStandardReturn<Stream<DetailedBolusInfo>> apply(Supplier<Stream<String>> ans) {
        Iterator<String> answers = ans.get().iterator();
        aapsLogger.info(LTag.PUMPBTCOMM, "Bolus history");
        ans.get().forEachOrdered(f -> System.out.println(f));
        while (answers.hasNext() && !answers.next().contains("bolus history:")) {

        }
        //Empty Line
        answers.next();
        Stream<DetailedBolusInfo> bolusHistory;
        try {
            bolusHistory = processBolusHistory(answers).stream().filter(Optional::isPresent).
                    map(Optional::get);
            medLinkPumpPlugin.handleNewTreatmentData(bolusHistory);
            return new MedLinkStandardReturn<>(ans, bolusHistory, Collections.emptyList());
        } catch (ParseException e) {
            e.printStackTrace();
            return new MedLinkStandardReturn<>(ans, Stream.empty(),
                    MedLinkStandardReturn.ParsingError.BolusParsingError);
        }
    }

    private List<Optional<DetailedBolusInfo>> processBolusHistory(Iterator<String> answers) throws ParseException {
        List<Optional<DetailedBolusInfo>> results = new ArrayList<>();
        while (answers.hasNext()) {
            results.add(processBolus(answers));
        }
        return results;
    }

    private Optional<DetailedBolusInfo> processBolus(Iterator<String> answers) throws ParseException {
        while (answers.hasNext() && !answers.next().contains("bolus:")) {

        }
        Pattern bolusDatePattern = Pattern.compile("\\d{2}:\\d{2}\\s+\\d{2}‑\\d{2}‑\\d{4}");
        if (answers.hasNext()) {
            Matcher bolusDateMatcher = bolusDatePattern.matcher(answers.next());
            if (bolusDateMatcher.find()) {
                DetailedBolusInfo bolusInfo = new DetailedBolusInfo();
                String datePattern = "HH:mm dd-MM-yyyy";
                SimpleDateFormat formatter = new SimpleDateFormat(datePattern, Locale.getDefault());
                bolusInfo.date = formatter.parse(bolusDateMatcher.group(0)).getTime();
                bolusInfo.deliverAt = bolusInfo.date;
                bolusInfo.insulin = processBolusData(answers, "given bl:");
                bolusInfo.source = Source.PUMP;
                bolusInfo.pumpId = Long.parseLong(
                        medLinkPumpPlugin.getPumpInfo().getConnectedDeviceSerialNumber());
                Double setBolus = processBolusData(answers, "set bl:");
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
                    if(squareBolusMatcher.find()){
                        squareBolusMatcher.group(0);
                    }
                }
                aapsLogger.info(LTag.DATABASE, bolusInfo.toString());
                return Optional.of(bolusInfo);
            }
        }
        return Optional.empty();
    }
//23:39:17.874 Bolus:
//23:39:17.903 Time:  21:52  15‑02‑2021
//23:39:17.941 Given BL:  1.500 U
//23:39:17.942 Set BL:  1.500 U
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
