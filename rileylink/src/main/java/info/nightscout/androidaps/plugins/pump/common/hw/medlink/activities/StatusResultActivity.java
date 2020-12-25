package info.nightscout.androidaps.plugins.pump.common.hw.medlink.activities;

import java.util.function.Function;

import info.nightscout.androidaps.logging.AAPSLogger;
import info.nightscout.androidaps.plugins.pump.common.data.PumpStatus;
import info.nightscout.androidaps.plugins.pump.common.hw.medlink.service.MedLinkStatusParser;
import info.nightscout.androidaps.plugins.pump.medtronic.comm.PumpResponses;

/**
 * Created by Dirceu on 21/12/20.
 */
public class StatusResultActivity extends BaseResultActivity<PumpStatus> {

    private final PumpStatus pumpStatus;
    private MedLinkStatusParser parser = new MedLinkStatusParser();
    public StatusResultActivity(AAPSLogger aapsLogger, PumpStatus pumpStatus) {
        super(aapsLogger);
        this.pumpStatus = pumpStatus;
    }

    @Override public PumpStatus apply(String s)
        {
            String[] pumpAnswer = s.split("\n");
            return parser.parseStatus(pumpAnswer, pumpStatus);
//            if(parser.fullMatch(stats)){
//                return PumpResponses.StatusFullProcessed.getAnswer();
//            }else if(parser.partialMatch(stats)){
//                return PumpResponses.StatusPartialProcessed.getAnswer();
//            }else{
//                return PumpResponses.StatusProcessFailed.getAnswer() + s;
//            }

        }
}
