package info.nightscout.androidaps.plugins.pump.medtronic.comm.activities;

import android.util.Pair;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Iterator;
import java.util.List;
import java.util.function.Supplier;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

import dagger.android.HasAndroidInjector;
import info.nightscout.androidaps.db.BgReading;
import info.nightscout.androidaps.db.SensorDataReading;
import info.nightscout.androidaps.db.Source;
import info.nightscout.androidaps.logging.AAPSLogger;
import info.nightscout.androidaps.logging.LTag;
import info.nightscout.androidaps.plugins.pump.common.hw.medlink.activities.BaseCallback;
import info.nightscout.androidaps.plugins.pump.common.hw.medlink.activities.MedLinkStandardReturn;
import info.nightscout.androidaps.plugins.pump.medtronic.MedLinkMedtronicPumpPlugin;

/**
 * Created by Dirceu on 15/04/21.
 */
public class IsigHistoryCallback extends BaseCallback<Stream<SensorDataReading>, Pair<Supplier<Stream<String>>, Supplier<Stream<BgReading>>>> {

    private final AAPSLogger aapsLogger;
    private final boolean handleBG;
    private HasAndroidInjector injector;
    private MedLinkMedtronicPumpPlugin medLinkPumpPlugin;

    public IsigHistoryCallback(HasAndroidInjector injector,
                               MedLinkMedtronicPumpPlugin medLinkPumpPlugin,
                               AAPSLogger aapsLogger, boolean handleBG) {
        this.injector = injector;
        this.medLinkPumpPlugin = medLinkPumpPlugin;
        this.aapsLogger = aapsLogger;
        this.handleBG = handleBG;
    }


    @Override public MedLinkStandardReturn<Stream<SensorDataReading>> apply(Pair<Supplier<Stream<String>>, Supplier<Stream<BgReading>>> ans) {
        aapsLogger.info(LTag.PUMPBTCOMM, "isig");
        Stream<String> toParse = ans.first.get();
        aapsLogger.info(LTag.PUMPBTCOMM, "isig2");
        Supplier<Stream<BgReading>> bgReadings = () -> ans.second.get();
        aapsLogger.info(LTag.PUMPBTCOMM, "isig3");
        SensorDataReading[] readings = parseAnswer(() -> toParse, bgReadings);
        medLinkPumpPlugin.handleNewSensorData(readings);
        if (handleBG) {
//            medLinkPumpPlugin.handleNewBgData(readings);
        }
        return new MedLinkStandardReturn<>(() -> toParse, Arrays.stream(readings), Collections.emptyList());
    }

    public SensorDataReading[] parseAnswer(Supplier<Stream<String>> ans, Supplier<Stream<BgReading>> bgReadings) {
        aapsLogger.info(LTag.PUMPBTCOMM, "isig");
        Iterator<String> answers = ans.get().iterator();
        Integer memAddress = 0;
//        answers.iterator();
        List<Double> isigs = new ArrayList<>();
        while (answers.hasNext() && memAddress == 0) {
            String line = answers.next();

            if (line.contains("nr strony")) {
                Pattern memPatter = Pattern.compile("\\d{3}");
                Matcher memMatcher = memPatter.matcher(line);
                if (memMatcher.find()) {
                    memAddress = Integer.valueOf(memMatcher.group(0));
                }
            }
        }

        while (answers.hasNext()) {
            String line = answers.next();

            if (line.contains("end of data")) {
                break;
            }
        }
        while (answers.hasNext()) {
            String line = answers.next();
            Pattern isigLinePattern = Pattern.compile("isig:\\s?\\d{1,3}\\.\\d{1,2}\\sna");
            Matcher matcher = isigLinePattern.matcher(line);
            //BG: 68 15:35 00‑00‑2000

            if (line.length() <= 15 && matcher.find()) {
                String data = matcher.group(0);
//                Double bg = Double.valueOf(data.substring(3, 6).trim());
                Pattern isigPat = Pattern.compile("\\d+\\.\\d+");

                Matcher isigMatcher = isigPat.matcher(data);
                isigMatcher.find();
                isigs.add(Double.valueOf(isigMatcher.group(0)));
            } else if (line.trim().length() > 0 && !line.trim().equals("ready") && !line.contains("end of data") && !line.contains("beginning of data")) {
                aapsLogger.info(LTag.PUMPBTCOMM, "isig failed");
                aapsLogger.info(LTag.PUMPBTCOMM, "" + line.trim().length());
                aapsLogger.info(LTag.PUMPBTCOMM, "" + matcher.find());
                aapsLogger.info(LTag.PUMPBTCOMM, "Invalid isig " + line);
                return null;
            }
        }

        aapsLogger.info(LTag.PUMPBTCOMM, "isig");
        isigs.forEach(f -> aapsLogger.info(LTag.PUMPBTCOMM, f.toString()));
        Collections.reverse(isigs);
        List<BgReading> bgReadingsList = Arrays.asList(bgReadings.get().toArray(BgReading[]::new));
        SensorDataReading[] result = new SensorDataReading[bgReadingsList.size()];
        aapsLogger.info(LTag.PUMPBTCOMM, "isigs s" + isigs.size());
        aapsLogger.info(LTag.PUMPBTCOMM, "readings s" + bgReadingsList.size());
//        if (isigs.size() == bgReadingsList.size()) {
        int delta = 0;
        int count = 0;
        for (; count < isigs.size(); count++) {
            BgReading reading = getReading(bgReadingsList, count, delta);
            if (reading == null) {
                break;
            }
            if (reading.source == Source.USER) {
                result[count + delta] = new SensorDataReading(injector, reading, 0d, 0d);
                delta++;
                reading = bgReadingsList.get(count + delta);
                if (reading == null) {
                    break;
                }
            }
            Double isig = isigs.get(count);
            Double calibrationFactor = 0.0;
            result[count + delta] = new SensorDataReading(injector, reading, isig, calibrationFactor);
            aapsLogger.info(LTag.PUMPBTCOMM, "adding isigs");
        }
        if (result.length > 0 && result[0] != null) {
            aapsLogger.info(LTag.PUMPBTCOMM, "adding isigs");
//            medLinkPumpPlugin.handleNewSensorData(result);
        }
        if (count + delta == result.length) {
            return result;
        } else {
            return null;
        }
    }


//          isigs.reverse
//        BGHistoryCallback.BGHistoryAccumulator history = new BGHistoryCallback.BGHistoryAccumulator();
//        sorted.forEachOrdered(f -> {
//            if (history.last != null) {
//                f.lastBG = history.last.currentBG;
//                f.lastBGDate = history.last.lastBGDate;
//            }
//            history.addBG(f);
//        });
//
//        Supplier<Stream<BgReading>> result = () -> history.acc.stream().map(f -> {
//            return new BgReading(injector, f.currentBGDate.getTime(), f.currentBG, null,
//                    f.lastBGDate.getTime(), f.lastBG, f.source);
//
//        });
//        if (result.get().findFirst().isPresent()) {
//            medLinkPumpPlugin.getPumpStatusData().lastReadingStatus = MedLinkPumpStatus.BGReadingStatus.SUCCESS;

//
//        return result.get().toArray(BgReading[]::new);



    private BgReading getReading(List<BgReading> bgReadingsList, int count, int delta) {
        if (count + delta > bgReadingsList.size()) {
            return null;
        } else return bgReadingsList.get(count + delta);
    }

}
