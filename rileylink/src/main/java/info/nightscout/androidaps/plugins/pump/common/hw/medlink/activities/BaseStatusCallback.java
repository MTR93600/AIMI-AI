package info.nightscout.androidaps.plugins.pump.common.hw.medlink.activities;

import java.util.function.Supplier;
import java.util.stream.Stream;

import info.nightscout.androidaps.plugins.pump.common.data.MedLinkPumpStatus;
import info.nightscout.androidaps.plugins.pump.common.hw.medlink.service.MedLinkStatusParser;

/**
 * Created by Dirceu on 21/12/20.
 */
public abstract class BaseStatusCallback extends BaseCallback<MedLinkPumpStatus, Supplier<Stream<String>>> {

    private final MedLinkPumpStatus pumpStatus;
    private MedLinkStatusParser parser = new MedLinkStatusParser();
    public BaseStatusCallback(MedLinkPumpStatus pumpStatus) {
        super();
        this.pumpStatus = pumpStatus;
    }

//    @Override public MedlinkStandardReturn<MedLinkPumpStatus> apply(Supplier<Stream<String>> s)
//        {
//
//
//            //TODO fix error handling
//            MedLinkPumpStatus resultStatus = parser.parseStatus(s.get().toArray(String[]::new), pumpStatus);
//            return new MedlinkStandardReturn<>(s, resultStatus);
////            if(parser.fullMatch(stats)){
////                return PumpResponses.StatusFullProcessed.getAnswer();
////            }else if(parser.partialMatch(stats)){
////                return PumpResponses.StatusPartialProcessed.getAnswer();
////            }else{
////                return PumpResponses.StatusProcessFailed.getAnswer() + s;
////            }
//
//        }
}
