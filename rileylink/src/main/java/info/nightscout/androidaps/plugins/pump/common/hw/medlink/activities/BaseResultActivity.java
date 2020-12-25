package info.nightscout.androidaps.plugins.pump.common.hw.medlink.activities;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Function;
import java.util.stream.Stream;

import info.nightscout.androidaps.logging.AAPSLogger;
import info.nightscout.androidaps.logging.LTag;

/**
 * Created by Dirceu on 26/11/20.
 */
public abstract class BaseResultActivity<B> implements Function<String,B> {

    protected final AAPSLogger aapsLogger;

    public BaseResultActivity(AAPSLogger aapsLogger){
        this.aapsLogger = aapsLogger;
    }
    protected List<Function<String, B>> functions = new ArrayList<>();

    public void addResult(Function<String, B> toBeAdded ) {
        functions.add(toBeAdded);
    }

    public boolean hasFinished() {
        return false;
    }

//    @Override public B apply(String a) {
//        aapsLogger.info(LTag.PUMPBTCOMM, "Applying functions "+a);
//        StringBuffer resp = new StringBuffer();
//        int count = 0;
//        for (Function<String,B> func: functions){
//            resp.append(func.apply(a));
//            resp.append("\n");
//            aapsLogger.info(LTag.PUMPBTCOMM, "Applying functions "+count++);
//        }
//
//        return resp;
//
////                reduce(a, (f, element) -> element.apply(f), (left, right) -> left +"\n"+ right);
//
//    }
}
