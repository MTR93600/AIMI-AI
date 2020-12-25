package info.nightscout.androidaps.plugins.pump.common.hw.medlink.activities;

import java.util.Arrays;
import java.util.Optional;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import info.nightscout.androidaps.logging.AAPSLogger;
import info.nightscout.androidaps.plugins.pump.medtronic.comm.PumpResponses;

/**
 * Created by Dirceu on 22/12/20.
 */
public class ConnectionResultActivity extends BaseStringAggregatorResultActivity {
    public ConnectionResultActivity(AAPSLogger aapsLogger) {
        super(aapsLogger);
    }

    @Override public String apply(String s) {
        Stream<String> answers = Arrays.stream(s.split("\n"));
        if (answers.anyMatch(f -> f.toLowerCase().contains("confirmed pump wake-up"))) {
            Optional<String> filtered = answers.filter(f -> f.toLowerCase().contains("medtronic")).findFirst();
            if (filtered.isPresent()) {
                return filtered.get();
            }
        }
        return PumpResponses.UnknowAnswer.getAnswer() + answers.collect(Collectors.joining(","));
    }

}

