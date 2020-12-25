package info.nightscout.androidaps.plugins.pump.common.hw.medlink.activities;

import java.util.function.Function;

import info.nightscout.androidaps.logging.AAPSLogger;
import info.nightscout.androidaps.logging.LTag;

/**
 * Created by Dirceu on 05/12/20.
 */
public class BaseStringAggregatorResultActivity extends BaseResultActivity<String> {
    public BaseStringAggregatorResultActivity(AAPSLogger aapsLogger) {
        super(aapsLogger);
    }

//    @Override public String apply(String a) {
//        super.aapsLogger.info(LTag.PUMPBTCOMM,"Applying BaseStringAggregator");
//        return functions.stream().reduce(a, (f, element) -> element.apply(f), (left, right) -> left + "\n" + right);
//
//    }

    @Override public String apply(String a) {
        aapsLogger.info(LTag.PUMPBTCOMM, "Applying functions "+a);
        StringBuffer resp = new StringBuffer();
        int count = 0;
        for (Function<String,String> func: functions){
            resp.append(func.apply(a));
            resp.append("\n");
            aapsLogger.info(LTag.PUMPBTCOMM, "Applying functions "+count++);
        }

        return resp.toString();

//                reduce(a, (f, element) -> element.apply(f), (left, right) -> left +"\n"+ right);

    }

}
