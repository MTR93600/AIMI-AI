package info.nightscout.androidaps.plugins.pump.common.hw.medlink.activities;

import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Supplier;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

import info.nightscout.androidaps.plugins.bus.RxBusWrapper;
import info.nightscout.androidaps.plugins.pump.medtronic.comm.PumpResponses;

/**
 * Created by Dirceu on 21/12/20.
 */
public class BolusCallback extends BaseCallback<String> {

//    private final RxBusWrapper rxBus;
    private Pattern deliveredBolusPattern = Pattern.compile(":\\s+\\d{1,2}\\.\\du\\s\\d{1,2}:\\d{1,2}\\s\\d{2}\\S", Pattern.CASE_INSENSITIVE);
    private Pattern deliveringBolusPattern = Pattern.compile(":\\s+\\d{1,2}\\.\\du", Pattern.CASE_INSENSITIVE);


    public BolusCallback(){//RxBusWrapper rxBus) {
        super();
//        this.rxBus = rxBus;
    }

    @Override public MedLinkStandardReturn<String> apply(Supplier<Stream<String>> answers) {

        //TODO fix error response
        AtomicReference<String> pumpResponse = new AtomicReference<>();
        if (answers.get().filter(f -> f.toLowerCase().contains("pump is not delivering a bolus")).findFirst().isPresent()) {
            answers.get().filter(f -> f.toLowerCase().contains("recent bolus bl")).findFirst().map(f -> {
                Matcher matcher = deliveredBolusPattern.matcher(f);
                if (matcher.find()) {
                    pumpResponse.set(PumpResponses.BolusDelivered.getAnswer());
                } else {
                    pumpResponse.set(PumpResponses.UnknowAnswer.getAnswer() + f);
                }
                return pumpResponse.get();
            });
        } else if (answers.get().filter(f -> f.toLowerCase().contains("pump is delivering a bolus")).findFirst().isPresent()) {
            answers.get().filter(f -> f.toLowerCase().contains("recent bolus bl")).findFirst().map(f -> {
                Matcher matcher = deliveringBolusPattern.matcher(f);

                if (matcher.find()) {
                    pumpResponse.set( PumpResponses.DeliveringBolus.getAnswer());
                } else {
                    pumpResponse.set( PumpResponses.UnknowAnswer.getAnswer() + f);
                }
                return pumpResponse.get();
            });
        }
        return  new MedLinkStandardReturn<>(answers,pumpResponse.get());
    }

}
