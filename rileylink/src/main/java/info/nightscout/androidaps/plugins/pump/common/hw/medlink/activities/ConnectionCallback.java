package info.nightscout.androidaps.plugins.pump.common.hw.medlink.activities;

import java.util.Optional;
import java.util.function.Supplier;
import java.util.stream.Stream;

import info.nightscout.androidaps.plugins.pump.medtronic.comm.PumpResponses;

/**
 * Created by Dirceu on 22/12/20.
 */
public class ConnectionCallback extends BaseStringAggregatorCallback {
    public ConnectionCallback() {
        super();
    }

    @Override public MedLinkStandardReturn<String> apply(Supplier<Stream<String>> answers) {

        if (answers.get().anyMatch(f -> f.toLowerCase().contains("confirmed pump wake-up"))) {
            Optional<String> filtered = answers.get().filter(f -> f.toLowerCase().contains("medtronic")).findFirst();
            if (filtered.isPresent()) {
                return new MedLinkStandardReturn<>(answers, filtered.get());
            }
        }

        return new MedLinkStandardReturn<>(answers, PumpResponses.UnknowAnswer.getAnswer(), MedLinkStandardReturn.ParsingError.ConnectionParsingError);
    }

}

