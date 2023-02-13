package info.nightscout.androidaps.plugins.pump.common.hw.medlink.activities;

import java.util.function.Function;
import java.util.function.Supplier;
import java.util.stream.Stream;

/**
 * Created by Dirceu on 26/11/20.
 * For Medlink implementation
 */
public abstract class BaseCallback<B,A> implements Function<A, MedLinkStandardReturn<B>> {

    public BaseCallback(){
    }

    //    protected List<Function<String, B>> functions = new ArrayList<>();

//    public void addResult(Function<String, B> toBeAdded ) {
//        functions.add(toBeAdded);
//    }

//    public boolean hasFinished() {
//        return false;
//    }


    @Override public MedLinkStandardReturn<B> apply(A streamSupplier) {

        return null;
    }
}
