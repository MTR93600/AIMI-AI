package info.nightscout.androidaps.plugins.pump.common.hw.medlink.activities;

import java.util.Arrays;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

import info.nightscout.androidaps.logging.AAPSLogger;
import info.nightscout.androidaps.plugins.pump.medtronic.comm.PumpResponses;

/**
 * Created by Dirceu on 21/12/20.
 */
public class BolusResultActivity extends BaseResultActivity<String> {

    private Pattern deliveredBolusPattern = Pattern.compile(":\\s+\\d{1,2}\\.\\du\\s\\d{1,2}:\\d{1,2}\\s\\d{2}\\S", Pattern.CASE_INSENSITIVE);
    private Pattern deliveringBolusPattern = Pattern.compile(":\\s+\\d{1,2}\\.\\du", Pattern.CASE_INSENSITIVE);


    public BolusResultActivity(AAPSLogger aapsLogger) {
        super(aapsLogger);
    }

    @Override public String apply(String s) {
        Stream<String> answers = Arrays.stream(s.split("\n"));
        if (answers.filter(f -> f.toLowerCase().contains("pump is not delivering a bolus")).findFirst().isPresent()) {
            answers.filter(f -> f.toLowerCase().contains("recent bolus bl")).findFirst().map(f -> {
                Matcher matcher = deliveredBolusPattern.matcher(f);
                if (matcher.find()) {
                    return PumpResponses.BolusDelivered.getAnswer();
                } else {
                    return PumpResponses.UnknowAnswer.getAnswer() + f;
                }
            });
        } else if (answers.filter(f -> f.toLowerCase().contains("pump is delivering a bolus")).findFirst().isPresent()) {
            answers.filter(f -> f.toLowerCase().contains("recent bolus bl")).findFirst().map(f -> {
                Matcher matcher = deliveringBolusPattern.matcher(f);
                if (matcher.find()) {
                    return PumpResponses.DeliveringBolus.getAnswer();
                } else {
                    return PumpResponses.UnknowAnswer.getAnswer() + f;
                }
            });
        }
        return null;
    }

}
