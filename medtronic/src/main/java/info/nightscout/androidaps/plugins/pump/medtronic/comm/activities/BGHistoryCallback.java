package info.nightscout.androidaps.plugins.pump.medtronic.comm.activities;

import java.text.ParseException;
import java.text.ParsePosition;
import java.text.SimpleDateFormat;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.function.Supplier;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

import dagger.android.HasAndroidInjector;
import info.nightscout.androidaps.db.BgReading;
import info.nightscout.androidaps.plugins.pump.common.data.MedLinkPumpStatus;
import info.nightscout.androidaps.plugins.pump.common.hw.medlink.activities.BaseCallback;
import info.nightscout.androidaps.plugins.pump.common.hw.medlink.activities.MedLinkStandardReturn;
import info.nightscout.androidaps.plugins.pump.medtronic.MedLinkMedtronicPumpPlugin;

/**
 * Created by Dirceu on 24/01/21.
 */
public class BGHistoryCallback extends BaseCallback<Stream<BgReading>> {

    private HasAndroidInjector injector;
    private MedLinkMedtronicPumpPlugin medLinkPumpPlugin;

    public BGHistoryCallback(HasAndroidInjector injector, MedLinkMedtronicPumpPlugin medLinkPumpPlugin) {
        this.injector = injector;
        this.medLinkPumpPlugin = medLinkPumpPlugin;
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
        private Double currentBG;
        private Double lastBG;
        private Date lastBGDate;
        private Date currentBGDate;

        public BGHistory(Double currentBG, Double lastBG, Date currentBGDate, Date lastBGDate) {
            this.currentBG = currentBG;
            this.lastBG = lastBG;
            this.currentBGDate = currentBGDate;
            this.lastBGDate = lastBGDate;
        }

        public long getTimestamp() {
            return currentBGDate.getTime();
        }

    }

    @Override public MedLinkStandardReturn<Stream<BgReading>> apply(Supplier<Stream<String>> ans) {


        Stream<String> answers = ans.get().sorted();
        Stream<BGHistory> bgs = answers.map(f -> {
            Pattern bgLinePattern = Pattern.compile("[bg|cl]:\\s?\\d{2,3}\\s+\\d{1,2}:\\d{2}\\s+\\d{2}-\\d{2}-\\d{4}");
            Matcher matcher = bgLinePattern.matcher(f);
            //BG: 68 15:35 00‑00‑2000
            Optional<BGHistory> bgHistory;
            if (matcher.find()) {
                String data = matcher.group(0);
//                Double bg = Double.valueOf(data.substring(3, 6).trim());
                Pattern bgPat = Pattern.compile("\\d{2,3}");
                Matcher bgMatcher = bgPat.matcher(data);
                bgMatcher.find();
                Double bg = Double.valueOf(bgMatcher.group(0));
                String datePattern = "HH:mm dd-MM-yyyy";
                Pattern dtPattern = Pattern.compile("\\d{1,2}:\\d{2}\\s+\\d{2}-\\d{2}-\\d{4}");
                Matcher dtMatcher = dtPattern.matcher(data);
                dtMatcher.find();
                SimpleDateFormat formatter = new SimpleDateFormat(datePattern, Locale.getDefault());
                Date bgDate = null;
                try {
                    bgDate = formatter.parse(dtMatcher.group(0));
                    Date firstDate = new Date();
                    firstDate.setTime(0l);
                    if (bgDate.toInstant().isBefore(new Date().toInstant().minus(Duration.ofDays(1))) || bgDate.toInstant().isAfter(new Date().toInstant().plus(Duration.ofMinutes(5)))) {
                        bgHistory = Optional.empty();
                    } else {
                        bgHistory = Optional.of(new BGHistory(bg, 0d, bgDate, firstDate));
                    }
                } catch (ParseException e) {
                    e.printStackTrace();
                    bgHistory = Optional.empty();
                }
                return bgHistory;
            }
            bgHistory = Optional.empty();
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

        Supplier<Stream<BgReading>> result = () -> history.acc.stream().map(f -> {
            return new BgReading(injector, f.currentBGDate.getTime(), f.currentBG, null,
                    f.lastBGDate.getTime(), f.lastBG);

        });
        if(result.get().findFirst().isPresent()){
            medLinkPumpPlugin.getPumpStatusData().lastReadingStatus = MedLinkPumpStatus.BGReadingStatus.SUCCESS;
        }
        medLinkPumpPlugin.handleNewBgData(result.get().toArray(BgReading[]::new));
        return new MedLinkStandardReturn<>(ans, result.get(), Collections.emptyList());
    }
}
