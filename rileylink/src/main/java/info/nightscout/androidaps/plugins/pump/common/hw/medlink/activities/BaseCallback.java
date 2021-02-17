package info.nightscout.androidaps.plugins.pump.common.hw.medlink.activities;

import java.util.function.Function;
import java.util.function.Supplier;
import java.util.stream.Stream;

/**
 * Created by Dirceu on 26/11/20.
 */
public abstract class BaseCallback<B> implements Function<Supplier<Stream<String>>, MedLinkStandardReturn<B>> {

    public BaseCallback(){
    }

    //    protected List<Function<String, B>> functions = new ArrayList<>();

//    public void addResult(Function<String, B> toBeAdded ) {
//        functions.add(toBeAdded);
//    }

//    public boolean hasFinished() {
//        return false;
//    }


    @Override public MedLinkStandardReturn<B> apply(Supplier<Stream<String>> streamSupplier) {

        return null;
    }
}
