package info.nightscout.androidaps.plugins.pump.medtronic.comm.activities;

import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Optional;
import java.util.function.Supplier;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

import dagger.android.HasAndroidInjector;
import info.nightscout.androidaps.data.EnliteInMemoryGlucoseValue;
import info.nightscout.androidaps.data.InMemoryGlucoseValue;
import info.nightscout.androidaps.interfaces.Pump;
import info.nightscout.androidaps.plugins.pump.common.defs.PumpType;
import info.nightscout.androidaps.plugins.pump.common.hw.medlink.activities.BaseCallback;
import info.nightscout.shared.logging.AAPSLogger;
import info.nightscout.shared.logging.LTag;
import info.nightscout.androidaps.plugins.pump.common.data.MedLinkPumpStatus;
import info.nightscout.androidaps.plugins.pump.common.hw.medlink.activities.MedLinkStandardReturn;
import info.nightscout.androidaps.plugins.pump.medtronic.MedLinkMedtronicPumpPlugin;

/**
 * Created by Dirceu on 24/01/21.
 */
public class BGHistoryCallback extends BaseCallback<Stream<EnliteInMemoryGlucoseValue>,
        Supplier<Stream<String>>> {

    private EnliteInMemoryGlucoseValue[] readings;

    private class InvalidBGHistoryException extends RuntimeException {
        InvalidBGHistoryException(String message) {
            super(message);
        }
    }

    private final AAPSLogger aapsLogger;
    private final boolean handleBG;
    private HasAndroidInjector injector;
    private MedLinkMedtronicPumpPlugin medLinkPumpPlugin;

    public BGHistoryCallback(HasAndroidInjector injector,
                             MedLinkMedtronicPumpPlugin medLinkPumpPlugin,
                             AAPSLogger aapsLogger, boolean handleBG) {
        this.injector = injector;
        this.medLinkPumpPlugin = medLinkPumpPlugin;
        this.aapsLogger = aapsLogger;
        this.handleBG = handleBG;
    }

    private class BGHistoryAccumulator {
        private BGHistory last;
        private List<BGHistory> acc = new ArrayList<>();

        public BGHistoryAccumulator() {
        }

        public BGHistoryAccumulator(BGHistory last) {
            this.last = last;
            acc.add(last);
        }

        public void addBG(BGHistory bg) {
            this.last = bg;
            this.acc.add(bg);
        }

        public List<BGHistory> getAccumulated() {
            return this.acc;
        }
    }

    private class BGHistory {
        private PumpType source;
        private Double currentBG;
        private Double lastBG;
        private Date lastBGDate;
        private Date currentBGDate;

        public BGHistory(Double currentBG, Double lastBG, Date currentBGDate, Date lastBGDate,
                         PumpType source) {
            this.currentBG = currentBG;
            this.lastBG = lastBG;
            this.currentBGDate = currentBGDate;
            this.lastBGDate = lastBGDate;
            this.source = source;
        }

        public long getTimestamp() {
            return currentBGDate.getTime();
        }

    }

    @Override public MedLinkStandardReturn<Stream<EnliteInMemoryGlucoseValue>> apply(Supplier<Stream<String>> ans) {


        EnliteInMemoryGlucoseValue[] readings = parseAnswer(ans);
        if (handleBG) {
            medLinkPumpPlugin.handleNewBgData(readings);
        }
        this.readings = readings;
        return new MedLinkStandardReturn<>(ans, Arrays.stream(readings), Collections.emptyList());
    }

    public EnliteInMemoryGlucoseValue[] parseAnswer(Supplier<Stream<String>> ans) {
        Stream<String> answers = ans.get();
        try {
            Stream<BGHistory> bgs = answers.map(f -> {
                Pattern bgLinePattern = Pattern.compile("[bg|cl]:\\s?\\d{2,3}\\s+\\d{1,2}:\\d{2}\\s+\\d{2}-\\d{2}-\\d{4}");
                Matcher matcher = bgLinePattern.matcher(f);
                //BG: 68 15:35 00‑00‑2000
                Optional<BGHistory> bgHistory = Optional.empty();
                if ((f.length() == 25 || f.length() == 26) && matcher.find()) {
                    String data = matcher.group(0);

//                Double bg = Double.valueOf(data.substring(3, 6).trim());
                    Pattern bgPat = Pattern.compile("\\d{2,3}");

                    assert data != null;
                    Matcher bgMatcher = bgPat.matcher(data);
                    bgMatcher.find();
                    Double bg = Double.valueOf(Objects.requireNonNull(bgMatcher.group(0)));
                    String datePattern = "HH:mm dd-MM-yyyy";
                    Pattern dtPattern = Pattern.compile("\\d{1,2}:\\d{2}\\s+\\d{2}-\\d{2}-\\d{4}");
                    Matcher dtMatcher = dtPattern.matcher(data);
                    dtMatcher.find();
                    SimpleDateFormat formatter = new SimpleDateFormat(datePattern, Locale.getDefault());
                    Date bgDate = null;
                    try {
                        bgDate = formatter.parse(Objects.requireNonNull(dtMatcher.group(0)));
                        Date firstDate = new Date();
                        firstDate.setTime(0l);
                        assert bgDate != null;
                        if (bgDate.getTime() > System.currentTimeMillis()) {
                            throw new InvalidBGHistoryException("TimeInFuture");
                        }
//                    aapsLogger.info(LTag.PUMPBTCOMM, f);
                        if (f.trim().startsWith("cl:")) {
                            bgHistory = Optional.of(new BGHistory(bg, 0d, bgDate, firstDate,
                                    PumpType.USER));
                        } else {
                            if (bgDate.toInstant().isAfter(new Date().toInstant().minus(Duration.ofDays(2))) && bgDate.toInstant().isBefore(new Date().toInstant().plus(Duration.ofMinutes(5)))) {
                                bgHistory = Optional.of(new BGHistory(bg, 0d, bgDate, firstDate,
                                        medLinkPumpPlugin.getPumpType()));
                            }
                        }
                    } catch (ParseException e) {
                        e.printStackTrace();
                        bgHistory = Optional.empty();
                    }
                    return bgHistory;
                }
                return bgHistory;
            }).filter(Optional::isPresent).map(Optional::get);
            Stream<BGHistory> sorted = bgs.sorted((b, a) -> (int) (b.getTimestamp() - a.getTimestamp()));
            BGHistoryAccumulator history = new BGHistoryAccumulator();
            sorted.forEachOrdered(f -> {
                if (history.last != null) {
                    f.lastBG = history.last.currentBG;
                    f.lastBGDate = history.last.lastBGDate;
                }
                history.addBG(f);
            });

            Supplier<Stream<EnliteInMemoryGlucoseValue>> result =
                    () -> history.acc.stream().map(f -> {
                return new EnliteInMemoryGlucoseValue(f.currentBGDate.getTime(), f.currentBG, false,
                        f.lastBGDate.getTime(), f.lastBG);
            });
            if (result.get().findFirst().isPresent()) {
                medLinkPumpPlugin.getPumpStatusData().lastReadingStatus =
                        MedLinkPumpStatus.BGReadingStatus.SUCCESS;
            }
            return result.get().toArray(EnliteInMemoryGlucoseValue[]::new);
        } catch (InvalidBGHistoryException e) {
            aapsLogger.info(LTag.PUMPBTCOMM, "Invalid bg history reading");
            return new EnliteInMemoryGlucoseValue[0];
        }
    }

    public EnliteInMemoryGlucoseValue[] getReadings() {
        return readings;
    }
}
