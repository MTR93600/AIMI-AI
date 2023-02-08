package info.nightscout.androidaps.plugins.pump.common.hw.medlink.activities;

import java.util.function.Supplier;
import java.util.stream.Stream;

/**
 * Created by Dirceu on 05/12/20.
 */
public class BaseStringAggregatorCallback extends BaseCallback<String,Supplier<Stream<String>>> {
    public BaseStringAggregatorCallback() {
        super();
    }

//    @Override public String apply(String a) {
//        super.aapsLogger.info(LTag.PUMPBTCOMM,"Applying BaseStringAggregator");
//        return functions.stream().reduce(a, (f, element) -> element.apply(f), (left, right) -> left + "\n" + right);
//
//    }

    @Override public MedLinkStandardReturn<String> apply(Supplier<Stream<String>> a) {
        return new MedLinkStandardReturn<>(a,a.toString());

//                reduce(a, (f, element) -> element.apply(f), (left, right) -> left +"\n"+ right);

    }

}
